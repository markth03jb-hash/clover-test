package com.ironspellgrimoire.server;

import com.ironspellgrimoire.entity.CursedChest;
import com.ironspellgrimoire.entity.EliteRift;
import com.ironspellgrimoire.registry.AddonEntities;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static com.ironspellgrimoire.server.EliteMobs.*;

/** What each elite trait does (EliteMobs keeps the list and rolls them). Numbers here match the Codex text in EliteMobs.SAYS. */
@Mod.EventBusSubscriber(modid = "iron_spell_grimoire")
public final class EliteTraits {
    private EliteTraits() {}

    /** An elite's moment-to-moment state (not saved: it starts fresh after a reload). */
    public static final class Run {
        int hits, windup, seekerCd = 60, daggerCd = 60, tetherCd = 60, dashCd = 80, dashTele, dashTicks, seenCd;
        boolean dashHit, shell;
        Vec3 dashDir = Vec3.ZERO;
        public int chainTo = -1;
        public long chainUntil;
        UUID ghostFor;
        int ghostLife;
    }

    static final UUID ANCHOR = UUID.fromString("e1173000-0000-4000-8000-000000000010"), HUNT = UUID.fromString("e1173000-0000-4000-8000-000000000011"),
            WIND = UUID.fromString("e1173000-0000-4000-8000-000000000012"), PLANT = UUID.fromString("e1173000-0000-4000-8000-000000000013"),
            HALF = UUID.fromString("e1173000-0000-4000-8000-000000000014");
    public static final int SHELL_CYCLE = 140, SHELL_UP = 40, IRON_PLATES = 3;

    static void play(Mob m, String s, float vol, float pitch) { if (m.level() instanceof ServerLevel sl) OverdriveFx.play(sl, m.position(), s, vol, pitch); }
    static Vec3 mid(Entity e) { return e.position().add(0, e.getBbHeight() * 0.5, 0); }
    static Vec3 flat(Vec3 v) { Vec3 f = new Vec3(v.x, 0, v.z); return f.lengthSqr() < 1e-6 ? Vec3.ZERO : f.normalize(); }
    static float attack(Mob m) { var a = m.getAttribute(Attributes.ATTACK_DAMAGE); return a == null ? 4f : (float) Math.max(2, a.getValue()); }
    static boolean sees(Mob m, Entity t) { return m.hasLineOfSight(t); }
    /** Who an elite's area blows hit: players and anything that isn't a hostile mob (never its own kind). */
    static boolean victim(Mob m, LivingEntity t) { return t != m && t.isAlive() && !(t instanceof Enemy) && !(t instanceof Player pl && (pl.isCreative() || pl.isSpectator())); }

    // ------------------------------------------------------------------ setting up
    static void setup(Mob m, int mask) {
        CompoundTag d = data(m);
        if ((mask & 1 << ANCHORED) != 0) modifier(m, Attributes.KNOCKBACK_RESISTANCE, ANCHOR, "Anchored", 1.0, AttributeModifier.Operation.ADDITION);
        if ((mask & 1 << REFLECTOR) != 0) d.putInt("phase", ThreadLocalRandom.current().nextInt(SHELL_CYCLE));
        if ((mask & 1 << MIRROR_TWIN) != 0 && m.level() instanceof ServerLevel sl) twin(m, sl);
    }

    /** Mirror Twin: a shadow copy beside it (black and blue), the two linked. */
    static Mob twin(Mob m, ServerLevel sl) {
        Vec3 side = flat(Vec3.directionFromRotation(0, m.getYRot() + 90)).scale(1.6);
        Mob t = copy(m, sl, m.position().add(side));
        if (t == null) return null;
        CompoundTag d = new CompoundTag();
        d.putInt("r", rank(m));
        d.putBoolean("shadow", true);
        d.putUUID("tw", m.getUUID());
        put(t, d);
        applyRank(t, rank(m));
        t.setHealth(t.getMaxHealth());
        t.setPersistenceRequired();
        sl.addFreshEntity(t);
        data(m).putUUID("tw", t.getUUID());
        FxSend.ring(sl, Fx.DARK, t.position().add(0, 0.1, 0), 0.3f, 1.4f, 0.1f, 14);
        return t;
    }

    /** A fresh mob of the same kind wearing the same gear (which it never drops). */
    static Mob copy(Mob src, ServerLevel sl, Vec3 at) {
        Entity e = src.getType().create(sl);
        if (!(e instanceof Mob m)) return null;
        m.moveTo(at.x, at.y, at.z, src.getYRot(), 0);
        for (EquipmentSlot s : EquipmentSlot.values()) {
            ItemStack it = src.getItemBySlot(s);
            if (!it.isEmpty()) { m.setItemSlot(s, it.copy()); m.setDropChance(s, 0f); }
        }
        if (src.getTarget() != null) m.setTarget(src.getTarget());
        return m;
    }

    // ------------------------------------------------------------------ every tick
    static void tick(Mob m, Run run) {
        if (!(m.level() instanceof ServerLevel sl)) return;
        if (ghost(m)) { ghostTick(m, run, sl); return; }
        int t = traits(m);
        if (t == 0) return;
        long now = sl.getGameTime();
        if (--run.seenCd <= 0) {
            run.seenCd = 20;
            for (Player p : sl.players()) if (p instanceof ServerPlayer sp && p.distanceToSqr(m) < 16 * 16 && sees(m, p)) seen(sp, t);
        }
        LivingEntity tg = m.getTarget();
        boolean onTarget = tg != null && tg.isAlive() && tg.level() == sl;
        double dist = onTarget ? m.distanceTo(tg) : 99;

        if ((t & 1 << OVERCHARGED) != 0 && run.windup > 0 && --run.windup == 0) slam(m, run, sl);

        if ((t & 1 << SEEKER) != 0 && --run.seekerCd <= 0 && onTarget && tg instanceof Player && dist < 24 && sees(m, tg)) {
            run.seekerCd = 100;
            Vec3 from = m.getEyePosition().add(0, 0.3, 0);
            SHOTS.add(new Shot(sl, m, tg, from, mid(tg).subtract(from).normalize().scale(0.3), true, attack(m) * 0.7f, 160, Fx.VOID));
            play(m, "elite_orb", 0.9f, 1f);
            testOrbs++;
        }
        if ((t & 1 << VOLLEY) != 0 && !(m instanceof RangedAttackMob) && --run.daggerCd <= 0 && onTarget && dist > 4 && dist < 10 && sees(m, tg)) {
            run.daggerCd = 80;
            Vec3 from = m.getEyePosition().add(0, -0.2, 0);
            SHOTS.add(new Shot(sl, m, tg, from, mid(tg).subtract(from).normalize().scale(0.9), false, attack(m) * 0.5f, 30, Fx.STEEL));
            play(m, "elite_dagger", 0.8f, 1.1f);
            testDaggers++;
        }
        if ((t & 1 << HUNTER) != 0 && m.tickCount % 10 == 0)
            modifier(m, Attributes.MOVEMENT_SPEED, HUNT, "Hunter", onTarget ? Math.min(0.6, Math.max(0, (dist - 4) / 20)) : 0, AttributeModifier.Operation.MULTIPLY_TOTAL);
        if ((t & 1 << TETHER) != 0) {
            if (run.tetherCd > 0) run.tetherCd--;
            if (run.tetherCd <= 0 && m.tickCount % 5 == 0 && onTarget && tg instanceof ServerPlayer p && dist > 10 && dist < 24 && sees(m, p)) tether(m, run, p, sl);
            if (run.chainTo >= 0 && now > run.chainUntil) { run.chainTo = -1; sync(m); }
        }
        if ((t & 1 << SPRINTER) != 0) sprint(m, run, sl, tg, onTarget, dist);
        if ((t & 1 << REFLECTOR) != 0) {
            boolean up = shellUp(m);
            if (up && !run.shell) play(m, "elite_reflect", 0.6f, 1.3f);
            run.shell = up;
        }
    }

    /** Anchored: whatever pushed or pulled it, it stays put (checked before it moves each tick). */
    @SubscribeEvent
    public static void onLivingTick(net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent e) {
        if (!(e.getEntity() instanceof Mob m) || m.level().isClientSide()) return;
        Run run = ACTIVE.get(m);
        if (run == null || run.dashTicks > 0 || !has(m, ANCHORED)) return;
        Vec3 v = m.getDeltaMovement();
        if (v.horizontalDistanceSqr() > 0.45 * 0.45 || v.y > 0.45) { m.setDeltaMovement(0, Math.min(v.y, 0.0), 0); m.hurtMarked = true; }
    }

    /** Reflector's shards are up 2 s of every 7. */
    public static boolean shellUp(Mob m) { return (m.level().getGameTime() + data(m).getInt("phase")) % SHELL_CYCLE < SHELL_UP; }

    static void slam(Mob m, Run run, ServerLevel sl) {
        modifier(m, Attributes.MOVEMENT_SPEED, PLANT, "Overcharged", 0, AttributeModifier.Operation.MULTIPLY_TOTAL);
        float dmg = attack(m) * 1.5f;
        for (LivingEntity v : sl.getEntitiesOfClass(LivingEntity.class, m.getBoundingBox().inflate(3, 1.5, 3), x -> victim(m, x) && x.distanceTo(m) <= 3.2))
            v.hurt(m.damageSources().mobAttack(m), dmg);
        FxSend.ring(sl, Fx.CRIMSON, m.position().add(0, 0.1, 0), 0.4f, 3.2f, 0.35f, 10);
        FxSend.shake(sl, m.position(), 0.35f, 8, 10);
        play(m, "elite_slam", 1.1f, 0.9f);
        run.hits = 0;
    }

    static void tether(Mob m, Run run, ServerPlayer p, ServerLevel sl) {
        run.tetherCd = 160;
        Vec3 to = mid(m).subtract(mid(p));
        double d = to.length();
        Vec3 pull = to.normalize().scale(Math.min(1.6, 0.25 + d * 0.06));
        p.setDeltaMovement(p.getDeltaMovement().scale(0.2).add(pull.x, 0.35, pull.z));
        p.hurtMarked = true;
        run.chainTo = p.getId();
        run.chainUntil = sl.getGameTime() + 14;
        sync(m);
        play(m, "elite_chain", 1f, 1f);
    }

    static void sprint(Mob m, Run run, ServerLevel sl, LivingEntity tg, boolean onTarget, double dist) {
        if (run.dashTicks > 0) {
            m.setDeltaMovement(run.dashDir.x * 0.9, m.getDeltaMovement().y, run.dashDir.z * 0.9);
            m.hurtMarked = true;
            if (!run.dashHit) for (LivingEntity v : sl.getEntitiesOfClass(LivingEntity.class, m.getBoundingBox().inflate(0.6), x -> victim(m, x))) {
                v.hurt(m.damageSources().mobAttack(m), attack(m) * 1.2f);
                run.dashHit = true;
            }
            if (--run.dashTicks == 0) run.dashCd = 120;
            return;
        }
        if (run.dashTele > 0) {
            if (--run.dashTele == 0) { run.dashTicks = 8; run.dashHit = false; play(m, "elite_dash", 1f, 1f); }
            return;
        }
        if (run.dashCd > 0) { run.dashCd--; return; }
        if (!onTarget || dist < 4 || dist > 14 || !sees(m, tg)) return;
        run.dashDir = flat(tg.position().subtract(m.position()));
        run.dashTele = 15;
        Vec3 a = m.position().add(0, 0.08, 0);
        FxSend.beam(sl, Fx.CRIMSON, a, a.add(run.dashDir.scale(8)), 0.35f, 15);
        play(m, "elite_windup", 0.8f, 1.4f);
    }

    // ------------------------------------------------------------------ shots: Seeker's orbs and Volley's daggers
    static final class Shot {
        final ServerLevel sl; final Mob owner; final LivingEntity target;
        Vec3 pos, vel; final boolean homing; final float dmg; int life, lost; final int pal;
        Shot(ServerLevel sl, Mob owner, LivingEntity target, Vec3 pos, Vec3 vel, boolean homing, float dmg, int life, int pal) {
            this.sl = sl; this.owner = owner; this.target = target; this.pos = pos; this.vel = vel; this.homing = homing; this.dmg = dmg; this.life = life; this.pal = pal;
        }
    }
    static final List<Shot> SHOTS = new ArrayList<>();

    static void tickShots() {
        for (Iterator<Shot> it = SHOTS.iterator(); it.hasNext(); ) {
            Shot s = it.next();
            if (--s.life <= 0 || !s.target.isAlive() || s.target.level() != s.sl) { it.remove(); continue; }
            if (s.homing) {
                Vec3 want = mid(s.target).subtract(s.pos).normalize().scale(0.3);
                s.vel = s.vel.add(want.subtract(s.vel).scale(0.08)).normalize().scale(0.3);
                if (s.life % 5 == 0) {   // out of sight for a second: it gives up
                    boolean seen = s.sl.clip(new ClipContext(s.pos, s.target.getEyePosition(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, s.owner)).getType() == HitResult.Type.MISS;
                    s.lost = seen ? 0 : s.lost + 5;
                    if (s.lost >= 20) { Fx.spark(s.sl, s.pal, s.pos, 0.6f); it.remove(); continue; }
                }
            }
            Vec3 next = s.pos.add(s.vel);
            if (s.sl.clip(new ClipContext(s.pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, s.owner)).getType() != HitResult.Type.MISS) {
                Fx.spark(s.sl, s.pal, s.pos, 0.6f); it.remove(); continue;
            }
            if (s.target.getBoundingBox().inflate(0.35).contains(next) || mid(s.target).distanceTo(next) < 0.8) {
                s.target.hurt(s.owner.damageSources().mobAttack(s.owner), s.dmg);
                Fx.impact(s.sl, s.pal, next, 0.8f);
                it.remove(); continue;
            }
            if (s.homing) { if (s.life % 3 == 0) Kit.orb(s.sl, s.pal, next, s.vel, 0.28f, 3); }
            else FxSend.trail(s.sl, s.pal, s.pos, next, 0.12f, 4);
            s.pos = next;
        }
    }
    static void clearShots() { SHOTS.clear(); }

    /** Volley: an elite archer's arrow comes with two more, 10 degrees either side. */
    static void onArrow(AbstractArrow ar) {
        if (!(ar.getOwner() instanceof Mob m) || !has(m, VOLLEY) || ar.getPersistentData().getBoolean("isg_volley")) return;
        ar.getPersistentData().putBoolean("isg_volley", true);
        for (int side : new int[]{-1, 1}) {
            Entity e = ar.getType().create(ar.level());
            if (!(e instanceof AbstractArrow c)) continue;
            c.getPersistentData().putBoolean("isg_volley", true);
            c.setOwner(m);
            c.setBaseDamage(ar.getBaseDamage());
            c.pickup = AbstractArrow.Pickup.DISALLOWED;
            Vec3 v = ar.getDeltaMovement();
            double a = Math.toRadians(10 * side), cs = Math.cos(a), sn = Math.sin(a);
            Vec3 r = new Vec3(v.x * cs - v.z * sn, v.y, v.x * sn + v.z * cs);
            c.moveTo(ar.getX(), ar.getY(), ar.getZ(), ar.getYRot(), ar.getXRot());
            c.setDeltaMovement(r);
            ar.level().addFreshEntity(c);
        }
    }

    // ------------------------------------------------------------------ blows given and taken
    private static boolean echoing;
    /** For the tests: how many things an Echo Strike or a Volatile burst has hit. */
    public static int testEchoHits, testBurstHits, testOrbs, testDaggers;

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent e) {
        if (e.getEntity().level().isClientSide()) return;
        DamageSource src = e.getSource();
        if (e.getEntity() instanceof Mob m && marked(m)) {   // an elite is hit
            if (ghost(m)) { e.setCanceled(true); return; }
            boolean real = !src.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
            if (has(m, IRONHIDE) && real && e.getAmount() > 0) {
                CompoundTag d = data(m);
                int ih = d.getInt("ih");
                if (ih < IRON_PLATES) {
                    d.putInt("ih", ih + 1);
                    e.setCanceled(true);
                    if (m.level() instanceof ServerLevel sl) Fx.spark(sl, Fx.STEEL, mid(m), 0.9f);
                    play(m, "elite_crack", 1f, 0.8f + 0.15f * ih);
                    sync(m);
                    return;
                }
            }
            if (has(m, BULWARK) && real) {
                Vec3 from = src.getSourcePosition() != null ? src.getSourcePosition() : src.getEntity() != null ? src.getEntity().position() : null;
                if (from != null) {
                    Vec3 face = flat(Vec3.directionFromRotation(0, m.yBodyRot)), to = flat(from.subtract(m.position()));
                    if (face.dot(to) > 0.5) {   // within 60 degrees of where it faces
                        e.setAmount(e.getAmount() * 0.3f);
                        if (m.level() instanceof ServerLevel sl) Fx.spark(sl, Fx.STEEL, mid(m).add(face.scale(0.6)), 0.7f);
                        play(m, "elite_reflect", 0.7f, 0.7f);
                    }
                }
            }
        }
        if (!(src.getEntity() instanceof Mob a) || !elite(a) || ghost(a)) return;   // an elite lands a blow
        LivingEntity v = e.getEntity();
        Run run = ACTIVE.get(a);
        if (has(a, OVERCHARGED) && run != null && run.windup == 0 && src.getDirectEntity() == a && ++run.hits >= 3) {
            run.windup = 20;   // the 4th: a second's wind-up, then the slam
            modifier(a, Attributes.MOVEMENT_SPEED, PLANT, "Overcharged", -1.0, AttributeModifier.Operation.MULTIPLY_TOTAL);
            if (a.level() instanceof ServerLevel sl) FxSend.ring(sl, Fx.CRIMSON, a.position().add(0, 0.05, 0), 3.0f, 3.2f, 0.05f, 20);
            play(a, "elite_windup", 1f, 0.8f);
        }
        if (has(a, ECHO) && !echoing && a.level() instanceof ServerLevel sl) {
            Vec3 at = v.position();
            float half = e.getAmount() * 0.5f;
            FxSend.ring(sl, Fx.CRIMSON, at.add(0, 0.05, 0), 1.0f, 1.3f, 0.03f, 20);
            AstaFx.later(20, () -> {
                if (!a.isAlive()) return;
                echoing = true;
                try {
                    for (LivingEntity t : sl.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(at, at).inflate(1.3, 1.5, 1.3), x -> victim(a, x)))
                        { t.hurt(a.damageSources().mobAttack(a), half); testEchoHits++; }
                } finally { echoing = false; }
                FxSend.ring(sl, Fx.CRIMSON, at.add(0, 0.1, 0), 0.2f, 1.3f, 0.25f, 8);
                OverdriveFx.play(sl, at, "elite_slam", 0.6f, 1.5f);
            });
        }
        if (v instanceof ServerPlayer p) {
            if (has(a, LEECH)) {
                MagicData md = MagicData.getPlayerMagicData(p);
                float drain = md.getMana() * 0.10f;
                if (drain > 0.5f) {
                    md.setMana(md.getMana() - drain);
                    a.heal(Math.max(1f, Math.min(a.getMaxHealth() * 0.15f, drain * 0.1f)));
                    if (a.level() instanceof ServerLevel sl) FxSend.trail(sl, Fx.VOID, mid(p), mid(a), 0.15f, 8);
                    play(a, "elite_leech", 0.9f, 1f);
                }
            }
            if (has(a, DISRUPTOR) && MagicData.getPlayerMagicData(p).isCasting()) {
                Utils.serverSideCancelCast(p);
                if (a.level() instanceof ServerLevel sl) Fx.spark(sl, Fx.LIGHTNING, mid(p), 0.8f);
                play(a, "elite_disrupt", 1f, 1f);
                p.displayClientMessage(Component.literal("Disrupted!").withStyle(ChatFormatting.RED), true);
            }
        }
    }

    /** Reflector: while its shards are up, projectiles bounce back at whoever threw them. */
    @SubscribeEvent
    public static void onProjectile(ProjectileImpactEvent e) {
        if (!(e.getRayTraceResult() instanceof EntityHitResult eh) || !(eh.getEntity() instanceof Mob m) || !has(m, REFLECTOR) || !shellUp(m)) return;
        var pr = e.getProjectile();
        if (pr.getOwner() == m) return;
        e.setImpactResult(ProjectileImpactEvent.ImpactResult.SKIP_ENTITY);
        Entity shooter = pr.getOwner();
        Vec3 back = shooter != null ? mid(shooter).subtract(pr.position()).normalize() : pr.getDeltaMovement().scale(-1).normalize();
        pr.setDeltaMovement(back.scale(Math.max(1.0, pr.getDeltaMovement().length())));
        pr.setOwner(m);
        pr.hurtMarked = true;
        if (m.level() instanceof ServerLevel sl) Fx.spark(sl, Fx.MIRROR, pr.position(), 0.8f);
        play(m, "elite_reflect", 1f, 1.1f);
    }

    // ------------------------------------------------------------------ death
    /** Second Wind: the first death doesn't take. */
    static boolean cheatDeath(Mob m) {
        if (!has(m, SECOND_WIND) || ghost(m)) return false;
        CompoundTag d = data(m);
        if (d.getBoolean("sw")) return false;
        d.putBoolean("sw", true);
        m.setHealth(m.getMaxHealth() * 0.5f);
        modifier(m, Attributes.MOVEMENT_SPEED, WIND, "Second Wind", 0.2, AttributeModifier.Operation.MULTIPLY_TOTAL);
        if (m.level() instanceof ServerLevel sl) {
            FxSend.ring(sl, Fx.GOLD, m.position().add(0, 0.1, 0), 0.3f, 2.2f, 0.3f, 14);
            Fx.pillar(sl, Fx.GOLD, m.position(), m.getBbHeight() + 1f, 0.6f, 12);
        }
        play(m, "elite_rise", 1.1f, 1f);
        return true;
    }

    static void died(Mob m, ServerLevel sl, Entity killer) {
        CompoundTag d = data(m);
        if (d.hasUUID("tw")) twinDied(m, sl, d.getUUID("tw"), killer);
        if (child(m)) return;
        int t = traits(m);
        int rank = rank(m);
        if ((t & 1 << SPLITTER) != 0) for (int k = 0; k < 2; k++) split(m, sl, k);
        if ((t & 1 << VOLATILE) != 0) volatileRing(m, sl);
        if ((t & 1 << VENGEFUL) != 0 && killer instanceof ServerPlayer p) vengeful(m, sl, p, rank);
        if ((t & 1 << RIFT_BORN) != 0) {
            EliteRift r = AddonEntities.ELITE_RIFT.get().create(sl);
            if (r != null) {
                r.setup(net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(m.getType()).toString(), rank);
                r.moveTo(m.getX(), m.getY() + 0.2, m.getZ(), m.getYRot(), 0);
                sl.addFreshEntity(r);
                OverdriveFx.play(sl, m.position(), "elite_portal", 1.1f, 1f);
            }
        }
    }

    /** Mirror Twin: if the other still stands 5 s later, the fallen one rises again. */
    static void twinDied(Mob m, ServerLevel sl, UUID other, Entity killer) {
        Entity o = sl.getEntity(other);
        if (!(o instanceof Mob partner) || !partner.isAlive()) {
            if (killer instanceof ServerPlayer p) p.displayClientMessage(Component.literal("The twins fall together").withStyle(ChatFormatting.LIGHT_PURPLE), true);
            return;
        }
        final CompoundTag keep = data(m).copy();
        final Vec3 at = m.position();
        FxSend.ring(sl, Fx.DARK, at.add(0, 0.1, 0), 0.3f, 1.2f, 0.2f, 100);
        AstaFx.later(100, () -> {
            if (!partner.isAlive() || partner.level() != sl) return;
            Mob back = copy(partner, sl, at);
            if (back == null) return;
            keep.remove("ih"); keep.remove("sw");
            keep.putUUID("tw", partner.getUUID());
            put(back, keep);
            applyRank(back, keep.getInt("r"));
            if ((keep.getInt("t") & 1 << ANCHORED) != 0) modifier(back, Attributes.KNOCKBACK_RESISTANCE, ANCHOR, "Anchored", 1.0, AttributeModifier.Operation.ADDITION);
            back.setHealth(back.getMaxHealth());
            back.setPersistenceRequired();
            sl.addFreshEntity(back);
            data(partner).putUUID("tw", back.getUUID());
            ACTIVE.computeIfAbsent(back, k -> new Run());
            Fx.pillar(sl, Fx.DARK, at, 2.4f, 0.8f, 14);
            OverdriveFx.play(sl, at, "elite_rise", 1f, 0.7f);
        });
    }

    static void split(Mob m, ServerLevel sl, int k) {
        Vec3 side = flat(Vec3.directionFromRotation(0, m.getYRot() + 90)).scale(k == 0 ? 0.6 : -0.6);
        Mob c = copy(m, sl, m.position().add(side));
        if (c == null) return;
        CompoundTag d = new CompoundTag();
        d.putInt("r", rank(m));
        d.putBoolean("child", true);
        put(c, d);
        applyRank(c, rank(m));
        modifier(c, Attributes.MAX_HEALTH, HALF, "Splitter", -0.5, AttributeModifier.Operation.MULTIPLY_TOTAL);
        c.setHealth(c.getMaxHealth());
        c.refreshDimensions();
        c.setDeltaMovement(side.scale(0.5).add(0, 0.3, 0));
        sl.addFreshEntity(c);
        if (k == 0) { FxSend.ring(sl, Fx.CRIMSON, m.position().add(0, 0.1, 0), 0.2f, 1.2f, 0.2f, 10); OverdriveFx.play(sl, m.position(), "elite_split", 1f, 1f); }
    }

    static void volatileRing(Mob m, ServerLevel sl) {
        final Vec3 at = m.position();
        final float dmg = 6 + 2 * rank(m);
        FxSend.ring(sl, Fx.FIRE, at.add(0, 0.05, 0), 2.8f, 3.0f, 0.05f, 30);
        OverdriveFx.play(sl, at, "elite_tick", 1f, 1f);
        AstaFx.later(30, () -> {
            for (LivingEntity v : sl.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(at, at).inflate(3, 2, 3),
                    x -> x.isAlive() && !(x instanceof Enemy) && x.position().distanceTo(at) <= 3 && !(x instanceof Player pl && (pl.isCreative() || pl.isSpectator()))))
                { v.hurt(sl.damageSources().explosion(null, null), dmg); testBurstHits++; }   // (a burst of force: no block is touched)
            FxSend.ring(sl, Fx.FIRE, at.add(0, 0.1, 0), 0.3f, 3.0f, 0.6f, 10);
            Fx.impact(sl, Fx.FIRE, at.add(0, 0.6, 0), 1.6f);
            FxSend.shake(sl, at, 0.4f, 8, 10);
            OverdriveFx.play(sl, at, "elite_boom", 1.2f, 1f);
        });
    }

    /** Vengeful: its ghost - the same mob, pale and see-through - floats after whoever killed it for 5 s. */
    static void vengeful(Mob m, ServerLevel sl, ServerPlayer p, int rank) {
        Mob g = copy(m, sl, m.position().add(0, 0.3, 0));
        if (g == null) return;
        CompoundTag d = new CompoundTag();
        d.putInt("r", rank);
        d.putBoolean("ghost", true);
        put(g, d);
        g.setNoAi(true); g.setNoGravity(true); g.setInvulnerable(true); g.setSilent(true); g.noPhysics = true;
        for (EquipmentSlot s : EquipmentSlot.values()) g.setDropChance(s, 0f);
        sl.addFreshEntity(g);
        Run r = new Run();
        r.ghostFor = p.getUUID();
        r.ghostLife = 100;
        ACTIVE.put(g, r);
        OverdriveFx.play(sl, g.position(), "elite_wail", 1f, 1f);
    }

    static void ghostTick(Mob g, Run r, ServerLevel sl) {
        Player p = r.ghostFor == null ? null : sl.getPlayerByUUID(r.ghostFor);
        if (p == null || !p.isAlive()) { g.discard(); return; }
        if (--r.ghostLife <= 0) {   // dodged it
            ExperienceOrb.award(sl, p.position(), 6 * (1 + rank(g)));
            if (p instanceof ServerPlayer sp) sp.displayClientMessage(Component.literal("You outran the ghost").withStyle(ChatFormatting.GREEN), true);
            Fx.spark(sl, Fx.SILVER, mid(g), 1f);
            g.discard();
            return;
        }
        Vec3 to = mid(p).subtract(mid(g));
        if (to.length() < 1.1) {
            p.hurt(sl.damageSources().magic(), 3 + rank(g));
            Fx.impact(sl, Fx.SILVER, mid(p), 0.9f);
            OverdriveFx.play(sl, p.position(), "elite_wail", 0.8f, 1.4f);
            g.discard();
            return;
        }
        Vec3 step = to.normalize().scale(0.3);
        g.setPos(g.getX() + step.x, g.getY() + step.y, g.getZ() + step.z);
        float yaw = (float) Math.toDegrees(Math.atan2(-step.x, step.z));
        g.setYRot(yaw); g.yBodyRot = yaw; g.yHeadRot = yaw;
    }

    /** Cursed Loot: a chest holding another share of its drops. */
    static void cursedChest(Mob m, List<ItemStack> loot) {
        if (!(m.level() instanceof ServerLevel sl)) return;
        CursedChest c = AddonEntities.CURSED_CHEST.get().create(sl);
        if (c == null) return;
        c.setup(loot, rank(m));
        c.moveTo(m.getX(), m.getY(), m.getZ(), m.getYRot() + 180, 0);
        sl.addFreshEntity(c);
    }

    /** Opened too soon it bites and gives just its share; left 3 s, it pays out double and some XP. */
    public static void openChest(CursedChest c, Player p) {
        if (!(c.level() instanceof ServerLevel sl) || c.opened()) return;
        boolean early = c.age() < 60;
        c.setOpened(true, early);
        int times = early ? 1 : 2;
        for (int k = 0; k < times; k++)
            for (ItemStack s : c.loot()) {
                var it = new net.minecraft.world.entity.item.ItemEntity(sl, c.getX(), c.getY() + 0.6, c.getZ(), s.copy());
                it.setDefaultPickUpDelay();
                sl.addFreshEntity(it);
            }
        if (early) {
            p.hurt(sl.damageSources().generic(), 4 + c.rank());
            Vec3 back = flat(p.position().subtract(c.position()));
            p.setDeltaMovement(back.x * 0.6, 0.3, back.z * 0.6);
            p.hurtMarked = true;
            OverdriveFx.play(sl, c.position(), "elite_bite", 1.1f, 1f);
            if (p instanceof ServerPlayer sp) sp.displayClientMessage(Component.literal("Too soon - it bites!").withStyle(ChatFormatting.RED), true);
        } else {
            ExperienceOrb.award(sl, c.position().add(0, 0.5, 0), 10 * (1 + c.rank()));
            OverdriveFx.play(sl, c.position(), "elite_chest", 1f, 1f);
            FxSend.ring(sl, Fx.GOLD, c.position().add(0, 0.1, 0), 0.2f, 1.2f, 0.4f, 12);
            if (p instanceof ServerPlayer sp) sp.displayClientMessage(Component.literal("Patience pays: double loot").withStyle(ChatFormatting.GOLD), true);
        }
    }

    /** Rift-Born: a rift left open spits out three elites of the fallen kind (no champions among them). */
    public static void riftOpens(EliteRift r) {
        if (!(r.level() instanceof ServerLevel sl)) return;
        var type = EntityType.byString(r.mobType()).orElse(null);
        for (int k = 0; k < 3 && type != null; k++) {
            Entity e = type.create(sl);
            if (!(e instanceof Mob m)) continue;
            double a = Math.PI * 2 * k / 3;
            m.moveTo(r.getX() + Math.cos(a) * 1.2, r.getY(), r.getZ() + Math.sin(a) * 1.2, (float) Math.toDegrees(a), 0);
            m.finalizeSpawn(sl, sl.getCurrentDifficultyAt(m.blockPosition()), net.minecraft.world.entity.MobSpawnType.EVENT, null, null);
            sl.addFreshEntity(m);
            make(m, r.rank(), pick(m, Math.min(3, r.rank()), 1));
        }
        FxSend.ring(sl, Fx.VOID, r.position().add(0, 0.1, 0), 0.3f, 2.5f, 0.6f, 14);
        OverdriveFx.play(sl, r.position(), "elite_portal", 1.2f, 0.7f);
    }

    public static void riftClosed(EliteRift r, Player by) {
        if (!(r.level() instanceof ServerLevel sl)) return;
        ExperienceOrb.award(sl, r.position(), 8 * (1 + r.rank()));
        Fx.impact(sl, Fx.VOID, r.position().add(0, 1, 0), 1.4f);
        OverdriveFx.play(sl, r.position(), "elite_crack", 1.2f, 0.6f);
        if (by instanceof ServerPlayer sp) sp.displayClientMessage(Component.literal("Rift closed").withStyle(ChatFormatting.GREEN), true);
    }

    // ------------------------------------------------------------------ test hooks
    public static Run testRun(Mob m) { return ACTIVE.get(m); }
    public static int testShots() { return SHOTS.size(); }
    public static void testSlam(Mob m) { Run r = ACTIVE.get(m); if (r != null && m.level() instanceof ServerLevel sl) slam(m, r, sl); }
    public static int testWindup(Mob m) { Run r = ACTIVE.get(m); return r == null ? -1 : r.windup; }
    /** For the screenshots: show a Tether chain to e for a while without pulling anyone. */
    public static void testChain(Mob m, Entity e, int ticks) { Run r = ACTIVE.get(m); if (r != null) { r.chainTo = e.getId(); r.chainUntil = m.level().getGameTime() + ticks; sync(m); } }
    public static void testTether(Mob m, ServerPlayer p) { Run r = ACTIVE.get(m); if (r != null) tether(m, r, p, p.serverLevel()); }
}
