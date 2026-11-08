package com.example.arcaneaddon.server;

import com.example.arcaneaddon.ArcaneAddon;
import com.example.arcaneaddon.item.LightningGrimoireItem;
import com.example.arcaneaddon.network.AddonNetwork;
import com.example.arcaneaddon.network.FxPacket;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Luck Voltia's Lightning Magic - the ten spells of the Lightning Grimoire and its passive. Fast, smiling, reckless:
 * lightning boots and gloves, blink-steps, orbs of thunder, and a mode where his whole body is lightning.
 *
 *  1 Thunder Fist             - flash to the enemy you aim at and punch
 *  2 Thunder God's Kick       - three lightning kicks on the move, the last launches them
 *  3 Thunder Crumbling Orb    - orbs of lightning that hunt enemies and burst
 *  4 Lightning Step           - blink 12 blocks in a flash
 *  5 Chain Lightning          - a bolt that jumps from enemy to enemy
 *  6 Thunder God's Gloves     - for 12 s every punch throws lightning into the next enemy too
 *  7 Mana Sense               - Luck's sense for mana: every creature within 64 blocks is marked
 *  8 Thunderfall              - bolts come down from the sky on everything around you
 *  9 Thunder God Mode         - for 25 s your body is lightning: faster, every hit discharges, every move gets stronger
 * 10 Thunder Fiend Rampage    - flash between every enemy around you, again and again, and finish with a bolt from the sky
 *
 * Passive "Battle Maniac": every enemy you hit with these spells makes you faster for 4 s (stacks 3 times).
 * Lightning is drawn as real lightning: branching, flickering bolts with a white-hot core (client shape 23).
 */
@Mod.EventBusSubscriber(modid = ArcaneAddon.MODID)
public class LightningFx {
    private static final int L = Fx.LIGHTNING;

    private static final Map<UUID, Long> MODE = new HashMap<>();
    private static final Map<UUID, Long> GLOVES = new HashMap<>();
    private static final Map<UUID, int[]> MANIAC = new HashMap<>();   // {stacks, until-tick low bits}
    private static final Map<UUID, Long> MANIAC_UNTIL = new HashMap<>();
    private static final Map<UUID, Long> DISCHARGE_CD = new HashMap<>();
    private static final UUID MODE_SPEED = UUID.fromString("d1e2f3a4-b5c6-4d7e-8f90-a1b2c3d4e5f6");
    private static final UUID MANIAC_SPEED = UUID.fromString("e2f3a4b5-c6d7-4e8f-90a1-b2c3d4e5f6a7");
    private static final int MODE_TICKS = 25 * 20;

    // ------------------------------------------------------------------ helpers
    private static void sound(ServerLevel sl, Vec3 at, SoundEvent s, float v, float pitch) {
        sl.playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, v, pitch);
    }

    private static Vec3 mid(LivingEntity t) { return t.position().add(0, t.getBbHeight() * 0.5, 0); }

    private static Vec3 rightOf(Vec3 dir) {
        Vec3 r = dir.cross(new Vec3(0, 1, 0));
        return r.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : r.normalize();
    }

    private static Vec3 clip(ServerLevel sl, Entity p, Vec3 a, Vec3 b) {
        var hit = sl.clip(new ClipContext(a, b, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        return hit.getType() == HitResult.Type.MISS ? b : hit.getLocation();
    }

    private static List<LivingEntity> enemies(ServerLevel sl, ServerPlayer p, AABB box) {
        return sl.getEntitiesOfClass(LivingEntity.class, box, x -> x != p && x.isAlive() && !x.isAlliedTo(p)
                && !(x instanceof net.minecraft.world.entity.decoration.ArmorStand));
    }

    private static boolean alive(ServerPlayer p) { return p.isAlive() && !p.isRemoved(); }

    private static boolean wearsLightningGrimoire(ServerPlayer p) {
        ItemStack s = Utils.getPlayerSpellbookStack(p);
        return s != null && s.getItem() instanceof LightningGrimoireItem;
    }

    public static boolean inMode(ServerPlayer p) {
        Long m = MODE.get(p.getUUID());
        return m != null && p.serverLevel().getGameTime() <= m;
    }

    private static void modeMsg(ServerPlayer p, String key) {
        p.displayClientMessage(Component.translatable("ui.arcane_addon.thunder_mode." + key).withStyle(ChatFormatting.YELLOW), true);
    }

    /** The nearest enemy roughly where you're looking. */
    private static LivingEntity aimed(ServerPlayer p, double range, double deg) {
        Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
        LivingEntity best = null;
        double bd = Math.cos(Math.toRadians(deg));
        for (LivingEntity t : enemies(p.serverLevel(), p, p.getBoundingBox().inflate(range))) {
            Vec3 to = mid(t).subtract(eye);
            if (to.length() > range || !p.hasLineOfSight(t)) continue;
            double d = to.normalize().dot(look);
            if (d > bd) { bd = d; best = t; }
        }
        return best;
    }

    /**
     * Real lightning from a to b: a jagged, branching bolt that flickers and re-forks while it lives (client draws
     * it, shape 23). width = thickness of the channel, branches = how many forks split off it.
     */
    static void bolt(ServerLevel sl, Vec3 a, Vec3 b, float width, int life, int branches) {
        FxPacket p = new FxPacket();
        p.shape = 23; p.palette = (byte) L; p.id = ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE); p.life = life;
        p.c = a; p.d = b.subtract(a); p.r = width; p.sweep = branches;
        AddonNetwork.CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(a.x, a.y, a.z, 128, sl.dimension())), p);
    }

    /** A strike: a flash of light, sparks and a short crackle where lightning lands. */
    private static void strike(ServerLevel sl, Vec3 at, float s) {
        FxLib.flash(sl, L, at, s);
        FxSend.ring(sl, L, at, 0.2f * s, 1.5f * s, 0.04f, 5);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int k = 0; k < 3; k++) {
            Vec3 d = new Vec3(r.nextDouble(-1, 1), r.nextDouble(-0.3, 1), r.nextDouble(-1, 1)).normalize();
            bolt(sl, at, at.add(d.scale(r.nextDouble(0.8, 1.8) * s)), 0.05f, 4, 0);
        }
    }

    /** A lightning hit: damage, a strike, and Battle Maniac speed. */
    private static void hit(ServerPlayer p, LivingEntity t, float dmg, String id) {
        AstaFx.damage(p, t, dmg, id);
        strike(p.serverLevel(), mid(t), 0.8f);
        maniac(p);
    }

    private static void maniac(ServerPlayer p) {
        int[] st = MANIAC.computeIfAbsent(p.getUUID(), k -> new int[]{0});
        st[0] = Math.min(3, st[0] + 1);
        MANIAC_UNTIL.put(p.getUUID(), p.serverLevel().getGameTime() + 80);
        var att = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (att == null) return;
        att.removeModifier(MANIAC_SPEED);
        att.addTransientModifier(new AttributeModifier(MANIAC_SPEED, "battle_maniac", 0.1 * st[0], AttributeModifier.Operation.MULTIPLY_TOTAL));
    }

    /** Move in a flash: blink to {@code to} (stopping short of walls), leaving a bolt where you were. */
    private static Vec3 flashTo(ServerPlayer p, Vec3 to) {
        ServerLevel sl = p.serverLevel();
        Vec3 from = p.position();
        Vec3 eyeFrom = from.add(0, 1.0, 0);
        Vec3 safe = clip(sl, p, eyeFrom, to.add(0, 1.0, 0));
        Vec3 dir = safe.subtract(eyeFrom);
        if (dir.lengthSqr() > 1.0) safe = safe.subtract(dir.normalize().scale(0.6));
        Vec3 dest = safe.subtract(0, 1.0, 0);
        if (!sl.noCollision(p, p.getBoundingBox().move(dest.subtract(from)))) dest = from;
        bolt(sl, eyeFrom, dest.add(0, 1.0, 0), 0.16f, 6, 2);
        p.connection.teleport(dest.x, dest.y, dest.z, p.getYRot(), p.getXRot());
        p.fallDistance = 0;
        sound(sl, from, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.3f, 2.0f);
        return dest;
    }

    // ================================================================== 1. Thunder Fist
    private static final int FIST_RELEASE = 4;

    /** Flash to the enemy you aim at (up to 14 blocks) and hit them with a fist of lightning. Thunder God Mode: two more enemies after it. */
    public static void thunderFist(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        FxSend.ring(sl, L, p.position().add(0, 0.05, 0), 1.4f, 0.2f, 0.05f, FIST_RELEASE + 1);
        AstaFx.later(FIST_RELEASE, () -> {
            if (!alive(p)) return;
            boolean mode = inMode(p);
            if (mode) modeMsg(p, "thunder_fist");
            LivingEntity t = aimed(p, 14, 20);
            List<LivingEntity> chain = new ArrayList<>();
            if (t != null) chain.add(t);
            if (mode && t != null) {
                List<LivingEntity> more = new ArrayList<>(enemies(sl, p, t.getBoundingBox().inflate(10)));
                more.remove(t);
                more.sort(Comparator.comparingDouble(e -> e.distanceToSqr(t)));
                for (int i = 0; i < Math.min(2, more.size()); i++) chain.add(more.get(i));
            }
            if (chain.isEmpty()) {   // nothing there: a flash forward and a punch into the air
                flashTo(p, p.position().add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(6)));
                strike(sl, p.getEyePosition().add(p.getLookAngle().scale(1.2)), 0.8f);
                return;
            }
            for (int i = 0; i < chain.size(); i++) {
                final LivingEntity e = chain.get(i);
                AstaFx.later(i * 3, () -> {
                    if (!alive(p) || !e.isAlive()) return;
                    Vec3 back = e.position().subtract(p.position()).multiply(1, 0, 1);
                    Vec3 stand = e.position().subtract(back.lengthSqr() < 1.0E-4 ? Vec3.ZERO : back.normalize().scale(1.4));
                    flashTo(p, stand);
                    p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, mid(e));
                    hit(p, e, power, "thunder_fist");
                    Vec3 push = back.lengthSqr() < 1.0E-4 ? p.getLookAngle() : back.normalize();
                    e.setDeltaMovement(e.getDeltaMovement().add(push.scale(1.1)).add(0, 0.3, 0));
                    e.hurtMarked = true;
                    sound(sl, e.position(), SoundEvents.PLAYER_ATTACK_STRONG, 1f, 1.3f);
                    sound(sl, e.position(), SoundEvents.LIGHTNING_BOLT_IMPACT, 0.4f, 1.6f);
                });
            }
        });
    }

    // ================================================================== 2. Thunder God's Kick
    private static final int[] KICKS = {3, 8, 13};

    /** Three kicks of the Thunder God's boots, each one a lightning-fast step forward; the third launches them skyward. */
    public static void thunderKick(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        boolean mode = inMode(p);
        if (mode) modeMsg(p, "thunder_kick");
        for (int i = 0; i < 3; i++) {
            final int ii = i;
            AstaFx.later(KICKS[i], () -> {
                if (!alive(p)) return;
                Vec3 look = p.getLookAngle();
                Vec3 flat = new Vec3(look.x, 0, look.z).normalize();
                flashTo(p, p.position().add(flat.scale(mode ? 4.5 : 3.0)));
                Vec3 foot = p.position().add(flat.scale(1.2)).add(0, ii == 2 ? 1.4 : 0.8, 0);
                Vec3 rt = rightOf(flat);
                FxSend.slash(sl, L, foot, flat, ii == 1 ? rt.scale(-1) : (ii == 2 ? new Vec3(0, 1, 0) : rt), 1.2f, 1.8f, 4, 0f, 1.2f);
                for (LivingEntity e : enemies(sl, p, p.getBoundingBox().inflate(3.2))) {
                    Vec3 to = mid(e).subtract(p.position().add(0, 1, 0));
                    if (to.length() > 3.4 || to.normalize().dot(flat) < 0.2) continue;
                    hit(p, e, power * (ii == 2 ? 0.5f : 0.3f), "thunder_kick");
                    e.setDeltaMovement(e.getDeltaMovement().add(flat.scale(ii == 2 ? 0.4 : 0.6)).add(0, ii == 2 ? 1.1 : 0.15, 0));
                    e.hurtMarked = true;
                    if (ii == 2 && mode) bolt(sl, mid(e).add(0, 14, 0), mid(e), 0.3f, 8, 3);   // the mode: lightning answers from the sky
                }
                sound(sl, p.position(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1f, 1.4f);
            });
        }
    }

    // ================================================================== 3. Thunder Crumbling Orb
    private static final int ORB_RELEASE = 6;

    /** Three orbs of crackling lightning leave your hands and hunt the nearest enemies, bursting on contact (2.5 blocks). Mode: five. */
    public static void thunderOrb(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        AstaFx.later(ORB_RELEASE, () -> {
            if (!alive(p)) return;
            boolean mode = inMode(p);
            if (mode) modeMsg(p, "thunder_orb");
            int n = mode ? 5 : 3;
            List<LivingEntity> foes = new ArrayList<>(enemies(sl, p, p.getBoundingBox().inflate(30)));
            foes.removeIf(t -> t.distanceTo(p) > 30 || !p.hasLineOfSight(t));
            foes.sort(Comparator.comparingDouble(t -> t.distanceToSqr(p)));
            Vec3 look = p.getLookAngle(), rt = rightOf(look);
            for (int i = 0; i < n; i++) {
                LivingEntity t = foes.isEmpty() ? null : foes.get(i % foes.size());
                Vec3 start = p.getEyePosition().add(look.scale(0.8)).add(rt.scale((i - (n - 1) / 2.0) * 0.5)).add(0, -0.3, 0);
                Vec3 d = look.add(rt.scale((i - (n - 1) / 2.0) * 0.25)).normalize();
                AstaFx.later(i, () -> orb(p, sl, start, d, t, power * 0.5f, 0));
            }
            sound(sl, p.position(), SoundEvents.LIGHTNING_BOLT_IMPACT, 0.5f, 1.8f);
        });
    }

    private static void orb(ServerPlayer p, ServerLevel sl, Vec3 pos, Vec3 dir, LivingEntity target, float dmg, int step) {
        if (!alive(p) || step > 30) return;
        if (target != null && target.isAlive()) dir = dir.scale(0.7).add(mid(target).subtract(pos).normalize().scale(0.3)).normalize();
        Vec3 want = pos.add(dir.scale(1.4));
        Vec3 next = clip(sl, p, pos, want);
        // the orb: a ball of tangled lightning
        FxLib.flash(sl, L, next, 0.35f);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int k = 0; k < 2; k++) {
            Vec3 a = next.add(r.nextDouble(-0.35, 0.35), r.nextDouble(-0.35, 0.35), r.nextDouble(-0.35, 0.35));
            Vec3 b = next.add(r.nextDouble(-0.35, 0.35), r.nextDouble(-0.35, 0.35), r.nextDouble(-0.35, 0.35));
            bolt(sl, a, b, 0.04f, 2, 0);
        }
        boolean touch = false;
        for (LivingEntity t : enemies(sl, p, new AABB(pos, next).inflate(0.8))) {
            if (t.getBoundingBox().inflate(0.5).clip(pos, next).isPresent() || t.getBoundingBox().inflate(0.5).contains(next)) { touch = true; break; }
        }
        if (touch || next.distanceToSqr(want) > 1.0E-4 || step == 30) {
            for (LivingEntity t : enemies(sl, p, new AABB(next, next).inflate(2.5))) {
                if (mid(t).distanceTo(next) > 2.5 + t.getBbWidth() * 0.5) continue;
                hit(p, t, dmg, "thunder_orb");
            }
            for (int k = 0; k < 5; k++) {
                Vec3 d = new Vec3(r.nextDouble(-1, 1), r.nextDouble(-0.5, 1), r.nextDouble(-1, 1)).normalize();
                bolt(sl, next, next.add(d.scale(r.nextDouble(1.5, 3.0))), 0.07f, 5, 1);
            }
            FxLib.flash(sl, L, next, 1.1f);
            sound(sl, next, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.6f, 1.5f);
            return;
        }
        final Vec3 nd = dir;
        AstaFx.later(1, () -> orb(p, sl, next, nd, target, dmg, step + 1));
    }

    // ================================================================== 4. Lightning Step
    /** Blink 12 blocks where you look in a flash of lightning, hitting everything along the way. Mode: 20 blocks. */
    public static void lightningStep(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        boolean mode = inMode(p);
        if (mode) modeMsg(p, "lightning_step");
        Vec3 from = p.position().add(0, 1, 0);
        Vec3 dest = flashTo(p, p.position().add(p.getLookAngle().scale(mode ? 20 : 12)));
        Vec3 to = dest.add(0, 1, 0);
        Set<UUID> hitSet = new HashSet<>();
        for (LivingEntity t : enemies(sl, p, new AABB(from, to).inflate(1.5))) {
            if (!t.getBoundingBox().inflate(0.8).clip(from, to).isPresent()) continue;
            if (hitSet.add(t.getUUID())) hit(p, t, power, "lightning_step");
        }
        bolt(sl, from, to, 0.22f, 8, 3);
        strike(sl, to, 0.7f);
        sound(sl, to, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.3f, 2.0f);
    }

    // ================================================================== 5. Chain Lightning
    private static final int CHAIN_RELEASE = 5;

    /** A bolt from your hand to the enemy you aim at, then on to the next nearest, and the next - up to 6 (mode: 10). */
    public static void chainLightning(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        AstaFx.later(CHAIN_RELEASE, () -> {
            if (!alive(p)) return;
            boolean mode = inMode(p);
            if (mode) modeMsg(p, "chain_lightning");
            LivingEntity first = aimed(p, 24, 25);
            Vec3 from = p.getEyePosition().add(p.getLookAngle().scale(0.8)).add(0, -0.3, 0);
            if (first == null) {
                Vec3 end = clip(sl, p, from, from.add(p.getLookAngle().scale(24)));
                bolt(sl, from, end, 0.18f, 7, 3);
                strike(sl, end, 0.8f);
                sound(sl, p.position(), SoundEvents.LIGHTNING_BOLT_IMPACT, 0.6f, 1.4f);
                return;
            }
            Set<UUID> done = new HashSet<>();
            LivingEntity cur = first;
            Vec3 at = from;
            int max = mode ? 10 : 6;
            for (int i = 0; i < max && cur != null; i++) {
                done.add(cur.getUUID());
                final LivingEntity t = cur; final Vec3 a = at; final float dmg = power * (float) Math.pow(0.85, i);
                AstaFx.later(i * 2, () -> {
                    if (!alive(p) || !t.isAlive()) return;
                    bolt(sl, a, mid(t), 0.14f, 7, 2);
                    hit(p, t, dmg, "chain_lightning");
                    sound(sl, t.position(), SoundEvents.LIGHTNING_BOLT_IMPACT, 0.4f, 1.6f);
                });
                at = mid(cur);
                LivingEntity next = null;
                double best = 9 * 9;
                for (LivingEntity e : enemies(sl, p, cur.getBoundingBox().inflate(9))) {
                    if (done.contains(e.getUUID())) continue;
                    double d = e.distanceToSqr(cur);
                    if (d < best) { best = d; next = e; }
                }
                cur = next;
            }
        });
    }

    // ================================================================== 6. Thunder God's Gloves
    /** Gloves of lightning for 12 s: every punch discharges into the target and arcs on into the next enemy within 6 blocks. */
    public static void thunderGloves(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        GLOVES.put(p.getUUID(), sl.getGameTime() + 240);
        sendGlow(p, 24, 240);
        Vec3 h = p.position().add(0, 1.1, 0);
        for (int k = 0; k < 4; k++) {
            ThreadLocalRandom r = ThreadLocalRandom.current();
            bolt(sl, h, h.add(r.nextDouble(-1.5, 1.5), r.nextDouble(-0.5, 1.5), r.nextDouble(-1.5, 1.5)), 0.06f, 5, 1);
        }
        sound(sl, p.position(), SoundEvents.LIGHTNING_BOLT_IMPACT, 0.5f, 1.8f);
        sound(sl, p.position(), SoundEvents.ARMOR_EQUIP_CHAIN, 1f, 1.4f);
    }

    /** Client marks for an entity: shape 24 = Thunder God Mode body, shape 25 = glowing gloves. */
    private static void sendGlow(ServerPlayer p, int shape, int life) {
        FxPacket pkt = new FxPacket();
        pkt.shape = (byte) shape; pkt.id = p.getId(); pkt.palette = (byte) L; pkt.life = life;
        AddonNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), pkt);
    }

    // ================================================================== 7. Mana Sense
    /** Luck's sense for mana, sharper than a noble's: every creature within 64 blocks is marked for 15 s. Mode: 128 blocks. */
    public static void manaSense(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        double R = inMode(p) ? 128 : 64;
        for (LivingEntity t : sl.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(R), x -> x != p && x.isAlive()
                && !(x instanceof net.minecraft.world.entity.decoration.ArmorStand))) {
            if (t.distanceTo(p) <= R) AstaFx.sendKiMark(p, t, 300, true);
        }
        FxSend.ring(sl, L, p.position().add(0, 0.05, 0), 0.5f, 12f, 0.04f, 12);
        FxLib.flash(sl, L, p.getEyePosition(), 0.6f);
        sound(sl, p.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 1f, 1.8f);
    }

    // ================================================================== 8. Thunderfall
    /** Raise a hand: for 2 s bolts come down from the sky on every enemy within 12 blocks, one strike after another. */
    public static void thunderfall(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        boolean mode = inMode(p);
        if (mode) modeMsg(p, "thunderfall");
        double R = mode ? 18 : 12;
        int strikes = mode ? 14 : 9;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        bolt(sl, p.getEyePosition().add(0, 0.6, 0), p.getEyePosition().add(0, 16, 0), 0.2f, 6, 2);   // the call, up into the sky
        sound(sl, p.position(), SoundEvents.LIGHTNING_BOLT_THUNDER, 0.6f, 1.2f);
        for (int i = 0; i < strikes; i++) {
            AstaFx.later(6 + i * 3, () -> {
                if (!alive(p)) return;
                List<LivingEntity> foes = new ArrayList<>(enemies(sl, p, p.getBoundingBox().inflate(R)));
                foes.removeIf(t -> t.distanceTo(p) > R);
                Vec3 land;
                LivingEntity t = foes.isEmpty() ? null : foes.get(r.nextInt(foes.size()));
                if (t != null) land = t.position();
                else {
                    double a = r.nextDouble(Math.PI * 2), d = r.nextDouble(3, R);
                    Vec3 g = FxLib.ground(sl, p.position().add(Math.cos(a) * d, 3, Math.sin(a) * d), 8);
                    if (g == null) return;
                    land = g;
                }
                Vec3 sky = land.add(r.nextDouble(-2, 2), 22, r.nextDouble(-2, 2));
                bolt(sl, sky, land, 0.32f, 8, 4);
                FxLib.shockRing(sl, L, land.add(0, 0.05, 0), 0.7f);
                for (LivingEntity e : enemies(sl, p, new AABB(land, land).inflate(2.2))) {
                    if (e.position().distanceTo(land) > 2.4) continue;
                    hit(p, e, power * 0.45f, "thunderfall");
                }
                sound(sl, land, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.9f, 1.0f + r.nextFloat() * 0.4f);
            });
        }
    }

    // ================================================================== 9. Thunder God Mode
    private static final int MODE_FORM = 12;

    /**
     * Luck lets his lightning take over his whole body for 25 s: lightning crackles over every limb, sparks spray from
     * your feet when you run, you move 40% faster, every melee hit discharges a bolt, and every other move of the
     * grimoire gets its Thunder God form.
     */
    public static void thunderMode(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        for (int k = 0; k < MODE_FORM; k += 2) {
            final int kk = k;
            AstaFx.later(k, () -> {
                if (!alive(p)) return;
                ThreadLocalRandom r = ThreadLocalRandom.current();
                Vec3 c = p.position().add(0, 1, 0);
                double a = r.nextDouble(Math.PI * 2);
                Vec3 far = c.add(Math.cos(a) * (4 - kk * 0.25), r.nextDouble(-0.5, 2.5), Math.sin(a) * (4 - kk * 0.25));
                bolt(sl, far, c, 0.08f, 3, 1);   // lightning gathering into you
            });
        }
        sound(sl, p.position(), SoundEvents.BEACON_POWER_SELECT, 0.7f, 2f);
        AstaFx.later(MODE_FORM, () -> {
            if (!alive(p)) return;
            MODE.put(p.getUUID(), sl.getGameTime() + MODE_TICKS);
            sendGlow(p, 24, MODE_TICKS);
            var att = p.getAttribute(Attributes.MOVEMENT_SPEED);
            if (att != null) {
                att.removeModifier(MODE_SPEED);
                att.addTransientModifier(new AttributeModifier(MODE_SPEED, "thunder_god_mode", 0.4, AttributeModifier.Operation.MULTIPLY_TOTAL));
            }
            Vec3 c = p.position();
            bolt(sl, c.add(0, 26, 0), c.add(0, 1, 0), 0.5f, 10, 5);   // the bolt that comes down into you
            FxLib.shockRing(sl, L, c.add(0, 0.05, 0), 1.6f);
            for (int k = 0; k < 8; k++) {
                double a = Math.PI * 2 * k / 8;
                bolt(sl, c.add(0, 1, 0), c.add(Math.cos(a) * 4, 0.1, Math.sin(a) * 4), 0.1f, 7, 1);
            }
            p.displayClientMessage(Component.translatable("ui.arcane_addon.thunder_mode.open").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD), true);
            sound(sl, c, SoundEvents.LIGHTNING_BOLT_THUNDER, 1f, 1.2f);
            sound(sl, c, SoundEvents.LIGHTNING_BOLT_IMPACT, 1f, 1.0f);
        });
    }

    // ================================================================== 10. Thunder Fiend Rampage
    private static final int RAMPAGE_START = 6;

    /**
     * Grinning, you go berserk: flash from enemy to enemy within 16 blocks, hitting each in turn, 10 flashes in all
     * (mode: 16) - then leap up and call down one great bolt on the spot where most of them are.
     */
    public static void rampage(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        boolean mode = inMode(p);
        if (mode) modeMsg(p, "rampage");
        int flashes = mode ? 16 : 10;
        Vec3 home = p.position();
        sound(sl, p.position(), SoundEvents.LIGHTNING_BOLT_THUNDER, 0.5f, 1.6f);
        for (int i = 0; i < flashes; i++) {
            final int ii = i;
            AstaFx.later(RAMPAGE_START + i * 2, () -> {
                if (!alive(p)) return;
                List<LivingEntity> foes = new ArrayList<>(enemies(sl, p, new AABB(home, home).inflate(16)));
                foes.removeIf(t -> t.position().distanceTo(home) > 16);
                if (foes.isEmpty()) {   // no one left: crackling flashes around the spot
                    ThreadLocalRandom r = ThreadLocalRandom.current();
                    double a = r.nextDouble(Math.PI * 2);
                    flashTo(p, home.add(Math.cos(a) * 4, 0, Math.sin(a) * 4));
                    return;
                }
                LivingEntity t = foes.get(ii % foes.size());
                double a = ii * 2.39996;   // a different side every time
                Vec3 stand = t.position().add(Math.cos(a) * 1.6, 0, Math.sin(a) * 1.6);
                flashTo(p, stand);
                p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, mid(t));
                hit(p, t, power * 0.22f, "rampage");
                t.setDeltaMovement(t.getDeltaMovement().scale(0.3));
                t.hurtMarked = true;
                sound(sl, t.position(), SoundEvents.PLAYER_ATTACK_STRONG, 0.8f, 1.5f);
            });
        }
        AstaFx.later(RAMPAGE_START + flashes * 2 + 3, () -> {
            if (!alive(p)) return;
            List<LivingEntity> foes = new ArrayList<>(enemies(sl, p, new AABB(home, home).inflate(16)));
            Vec3 c = home;
            if (!foes.isEmpty()) {
                Vec3 sum = Vec3.ZERO;
                for (LivingEntity t : foes) sum = sum.add(t.position());
                c = sum.scale(1.0 / foes.size());
            }
            Vec3 g = FxLib.ground(sl, c.add(0, 2, 0), 8);
            final Vec3 land = g != null ? g : c;
            bolt(sl, land.add(0, 30, 0), land, 0.9f, 12, 6);
            bolt(sl, land.add(3, 30, -2), land.add(1, 0, 0), 0.4f, 10, 3);
            FxLib.shockRing(sl, L, land.add(0, 0.05, 0), 2.0f);
            for (int k = 0; k < 10; k++) {
                double a = Math.PI * 2 * k / 10;
                bolt(sl, land.add(0, 0.2, 0), land.add(Math.cos(a) * 6, 0.1, Math.sin(a) * 6), 0.12f, 9, 1);
            }
            for (LivingEntity t : enemies(sl, p, new AABB(land, land).inflate(6))) {
                if (t.position().distanceTo(land) > 6) continue;
                hit(p, t, power, "rampage");
                t.setDeltaMovement(t.getDeltaMovement().add(0, 0.8, 0));
                t.hurtMarked = true;
            }
            sound(sl, land, SoundEvents.LIGHTNING_BOLT_THUNDER, 1.5f, 0.9f);
            sound(sl, land, SoundEvents.LIGHTNING_BOLT_IMPACT, 1.5f, 0.8f);
        });
    }

    // ================================================================== events
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        ServerLevel sl = p.serverLevel();
        long now = sl.getGameTime();
        UUID id = p.getUUID();
        Long m = MODE.get(id);
        if (m != null) {
            if (now > m) {
                MODE.remove(id);
                sendGlow(p, 24, 0);
                var att = p.getAttribute(Attributes.MOVEMENT_SPEED);
                if (att != null) att.removeModifier(MODE_SPEED);
                p.displayClientMessage(Component.translatable("ui.arcane_addon.thunder_mode.close").withStyle(ChatFormatting.YELLOW), true);
            } else if (now % 40 == 0) sendGlow(p, 24, (int) (m - now));
        }
        Long g = GLOVES.get(id);
        if (g != null) {
            if (now > g) { GLOVES.remove(id); sendGlow(p, 25, 0); }
            else if (now % 40 == 0) sendGlow(p, 25, (int) (g - now));
        }
        Long mu = MANIAC_UNTIL.get(id);
        if (mu != null && now > mu) {
            MANIAC_UNTIL.remove(id); MANIAC.remove(id);
            var att = p.getAttribute(Attributes.MOVEMENT_SPEED);
            if (att != null) att.removeModifier(MANIAC_SPEED);
        }
    }

    /** Gloves and the mode: melee hits discharge lightning. */
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent e) {
        if (!(e.getSource().getEntity() instanceof ServerPlayer p) || e.getSource().getDirectEntity() != p) return;
        if (e.getSource() instanceof io.redspace.ironsspellbooks.damage.SpellDamageSource) return;
        ServerLevel sl = p.serverLevel();
        long now = sl.getGameTime();
        Long g = GLOVES.get(p.getUUID());
        boolean gloves = g != null && now <= g, mode = inMode(p);
        if (!gloves && !mode) return;
        Long cd = DISCHARGE_CD.get(p.getUUID());
        if (cd != null && now - cd < 6) return;
        DISCHARGE_CD.put(p.getUUID(), now);
        LivingEntity t = e.getEntity();
        e.setAmount(e.getAmount() + (mode ? 4f : 3f));
        strike(sl, mid(t), 0.7f);
        maniac(p);
        if (gloves) {   // arcs on into the next enemy
            LivingEntity next = null; double best = 36;
            for (LivingEntity o : enemies(sl, p, t.getBoundingBox().inflate(6))) {
                if (o == t) continue;
                double d = o.distanceToSqr(t);
                if (d < best) { best = d; next = o; }
            }
            if (next != null) {
                bolt(sl, mid(t), mid(next), 0.1f, 6, 1);
                final LivingEntity nx = next;
                AstaFx.later(1, () -> { if (nx.isAlive() && alive(p)) AstaFx.damage(p, nx, 4f, "thunder_gloves"); });
            }
        }
        if (mode) bolt(sl, mid(t).add(0, 10, 0), mid(t), 0.18f, 6, 2);
        sound(sl, t.position(), SoundEvents.LIGHTNING_BOLT_IMPACT, 0.4f, 1.8f);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        UUID id = e.getEntity().getUUID();
        MODE.remove(id); GLOVES.remove(id); MANIAC.remove(id); MANIAC_UNTIL.remove(id); DISCHARGE_CD.remove(id);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent e) {
        MODE.clear(); GLOVES.clear(); MANIAC.clear(); MANIAC_UNTIL.clear(); DISCHARGE_CD.clear();
    }
}
