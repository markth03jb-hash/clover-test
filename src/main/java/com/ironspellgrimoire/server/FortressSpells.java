package com.ironspellgrimoire.server;

import com.ironspellgrimoire.IronSpellGrimoire;
import com.ironspellgrimoire.entity.FortressDefender;
import com.ironspellgrimoire.entity.FortressTower;
import com.ironspellgrimoire.network.FxPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static com.ironspellgrimoire.server.FortressFx.*;

/**
 * The Architect of Ruin's 24 room spells (FortressFx.ROOM) and what they leave behind: burning patches, the Ward
 * Sigil, the Quiet Grove, Battle Plan's mark, Foresight's sight, Mirror Ward and the Keepsake. Each spell is aimed at
 * a spot - where you look, or (the Citadel's wave) at the nearest foe - so the same code serves both.
 */
@Mod.EventBusSubscriber(modid = IronSpellGrimoire.MODID)
public final class FortressSpells {
    private FortressSpells() {}

    static final int STONE = Fx.EARTH, BRASS = Fx.GOLD, EMBER = Fx.FIRE, ARCANE = Fx.ICE, WATER = Fx.WATER, BONE = Fx.SILVER;
    static final String SPELL = "siege_mode";   // the damage's spell (for its school and the Star Forge)

    // ================================================================== hits
    /** A room spell's hit (room -1: a tower or the walls): foes only, never eaten by hurt-frames. */
    static void hit(ServerPlayer p, LivingEntity t, float dmg, int room) {
        if (!FortressSiege.foe(p.getUUID(), p, t)) return;
        float k = markBonus(p, t);
        if (room >= 0) { int r = FortressSiege.postedRank(p, room); if (r >= 0) k *= 1.05f + 0.05f * r; }   // a defender posted in the room
        t.invulnerableTime = 0;
        AstaFx.damage(p, t, dmg * k, SPELL);
        addXp(p, Math.min(4f, dmg * 0.05f));
    }
    static void towerHit(ServerPlayer p, LivingEntity t, float dmg) { hit(p, t, dmg, -1); }

    // ================================================================== marks: Battle Plan and Foresight
    record Mark(UUID by, long until) {}
    static final Map<UUID, Mark> MARKS = new HashMap<>();   // target -> who marked it, until when
    static final Map<UUID, Map<UUID, Long>> SEEN = new HashMap<>();   // caster -> revealed targets -> until

    public static boolean marked(LivingEntity t) {
        Mark m = MARKS.get(t.getUUID());
        return m != null && m.until > t.level().getGameTime();
    }
    static boolean revealed(Player p, LivingEntity t) {
        Map<UUID, Long> s = SEEN.get(p.getUUID());
        Long u = s == null ? null : s.get(t.getUUID());
        return u != null && u > t.level().getGameTime();
    }
    /** Marked by you: +25%. Revealed to you: +10%. */
    static float markBonus(Player p, LivingEntity t) {
        Mark m = MARKS.get(t.getUUID());
        if (m != null && m.until > t.level().getGameTime() && m.by.equals(p.getUUID())) return 1.25f;
        return revealed(p, t) ? 1.10f : 1f;
    }

    // ================================================================== lingering things
    record Patch(UUID owner, Vec3 at, float r, long until, float dps, int kind) {}   // kind 0 a burning patch, 1 Ward Sigil, 2 Quiet Grove
    static final List<Patch> PATCHES = new ArrayList<>();

    static void burnPatch(ServerPlayer p, Vec3 at, float r, int ticks, float dps) {
        if (PATCHES.size() > 64) PATCHES.remove(0);
        PATCHES.add(new Patch(p.getUUID(), at, r, p.level().getGameTime() + ticks, dps, 0));
        FxSend.ring(p.serverLevel(), EMBER, at.add(0, 0.05, 0), r * 0.5f, r, 0.08f, Math.min(ticks, 40));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || PATCHES.isEmpty() && MARKS.isEmpty()) return;
        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        long now = server.overworld().getGameTime();
        if (now % 10 != 0) return;
        Iterator<Patch> it = PATCHES.iterator();
        while (it.hasNext()) {
            Patch pa = it.next();
            ServerPlayer p = server.getPlayerList().getPlayer(pa.owner);
            if (p == null || pa.until < p.level().getGameTime()) { it.remove(); continue; }
            ServerLevel sl = p.serverLevel();
            if (pa.at.distanceToSqr(p.position()) > 96 * 96) continue;
            switch (pa.kind) {
                case 0 -> {
                    for (LivingEntity t : Kit.near(sl, p, pa.at.add(0, 0.5, 0), pa.r)) hit(p, t, pa.dps * 0.5f, -1);
                    if (now % 20 == 0) FxSend.ring(sl, EMBER, pa.at.add(0, 0.05, 0), pa.r * 0.3f, pa.r, 0.08f, 12);
                }
                case 1 -> {   // Ward Sigil: drawn; its protection is in onHurt; with the Chapel beside the Rune Vault it heals too
                    if (now % 20 == 0) FxSend.ring(sl, BRASS, pa.at.add(0, 0.05, 0), pa.r - 0.2f, pa.r, 0.05f, 22);
                    if (pa.dps > 0 && now % 20 == 0) for (LivingEntity a : allies(p, pa.at, pa.r)) a.heal(pa.dps);
                }
                case 2 -> {   // Quiet Grove: health and mana back to everyone on your side standing in it
                    if (now % 20 != 0) break;
                    FxSend.ring(sl, Fx.TREE, pa.at.add(0, 0.05, 0), pa.r - 0.3f, pa.r, 0.06f, 22);
                    for (LivingEntity a : allies(p, pa.at, pa.r)) {
                        a.heal(1.5f);
                        if (a instanceof ServerPlayer ap) {
                            var md = io.redspace.ironsspellbooks.api.magic.MagicData.getPlayerMagicData(ap);
                            float max = (float) ap.getAttributeValue(io.redspace.ironsspellbooks.api.registry.AttributeRegistry.MAX_MANA.get());
                            md.setMana(Math.min(max, md.getMana() + 6));
                        }
                    }
                }
            }
        }
        MARKS.entrySet().removeIf(en -> en.getValue().until < now - 200);
        if (now % 200 == 0) SEEN.values().forEach(m -> m.entrySet().removeIf(en -> en.getValue() < now - 200));
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent e) { PATCHES.clear(); MARKS.clear(); SEEN.clear(); }

    /** You, your teammates and your defenders within r of a spot. */
    static List<LivingEntity> allies(ServerPlayer p, Vec3 c, double r) {
        return p.serverLevel().getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r, 3, r),
                e -> e.isAlive() && FortressSiege.friendOf(p.getUUID(), e) && e.distanceToSqr(c) <= (r + 0.5) * (r + 0.5) + 9);
    }

    // ================================================================== protections (Ward Sigil, Keepsake) and Mirror Ward
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onHurt(LivingHurtEvent e) {
        LivingEntity v = e.getEntity();
        if (v.level().isClientSide || PATCHES.isEmpty() && !(v instanceof ServerPlayer)) return;
        for (Patch pa : PATCHES) {   // Ward Sigil: 40% less for anyone on its side standing in it
            if (pa.kind != 1 || pa.until < v.level().getGameTime() || v.distanceToSqr(pa.at) > (pa.r + 0.5) * (pa.r + 0.5) + 4) continue;
            if (FortressSiege.friendOf(pa.owner, v)) { e.setAmount(e.getAmount() * 0.6f); break; }
        }
    }

    /** Saint's Keepsake: the killing blow leaves you at 1 health instead (runs last, after every other change to the damage). */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onKeepsake(net.minecraftforge.event.entity.living.LivingDamageEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || e.getAmount() < p.getHealth()) return;   // (absorption is already taken off by now)
        CompoundTag r = FortressFx.root(p);
        if (r.getLong("keep") <= p.level().getGameTime()) return;
        r.putLong("keep", 0);
        e.setAmount(Math.max(0, p.getHealth() - 1));
        ServerLevel sl = p.serverLevel();
        FxSend.ring(sl, BRASS, p.position().add(0, 1, 0), 0.3f, 2.6f, 0.12f, 14);
        Fx.flash(sl, p.position().add(0, 1, 0), 1.2f);
        OverdriveFx.play(sl, p.position(), "fort_bell", 1.0f, 1.4f);
        FortressFx.tell(p, "The Keepsake held");
        if (FortressFx.pair(p, FortressFx.indexOfPair(FortressFx.CRYPT, FortressFx.RELIQUARY)))   // Crypt + Reliquary: sentries rise round you
            for (int i = 0; i < 3; i++) FortressSiege.guard(p, "iron_spell_grimoire:bone_sentry", FortressDefender.SENTRY, p.position(), 200);
    }

    @SubscribeEvent
    public static void onProjectile(ProjectileImpactEvent e) {
        if (!(e.getRayTraceResult() instanceof EntityHitResult eh) || !(eh.getEntity() instanceof ServerPlayer p)) return;
        Projectile pr = e.getProjectile();
        if (pr.getOwner() != null && FortressSiege.friendOf(p.getUUID(), pr.getOwner())) return;
        CompoundTag r = FortressFx.root(p);
        if (r.getLong("mirror") <= p.level().getGameTime()) return;
        r.putLong("mirror", 0);
        e.setImpactResult(ProjectileImpactEvent.ImpactResult.SKIP_ENTITY);
        Entity shooter = pr.getOwner();
        Vec3 back = shooter != null ? shooter.position().add(0, shooter.getBbHeight() * 0.5, 0).subtract(pr.position()).normalize() : pr.getDeltaMovement().scale(-1).normalize();
        pr.setDeltaMovement(back.scale(Math.max(1.2, pr.getDeltaMovement().length() * 1.2)));
        pr.setOwner(p);
        pr.hurtMarked = true;
        ServerLevel sl = p.serverLevel();
        FxSend.ring(sl, ARCANE, pr.position(), 0.2f, 1.2f, 0.06f, 8);
        OverdriveFx.play(sl, p.position(), "fort_ley", 0.8f, 1.8f);
    }

    // ================================================================== the spells
    /** Cast a room's spell where the player looks. */
    static void cast(ServerPlayer p, int room) {
        double reach = room == SIEGE_YARD ? 40 : room == ARCANUM || room == FORGE ? 26 : 20;
        Vec3 at = aimAt(p, reach);
        cast(p, room, at, false);
        Adv.grant(p, "first_room_spell");
    }

    /** The Citadel's wave: every damaging room fires once, each at the foe nearest the middle. */
    static void citadelWave(ServerPlayer p) {
        int[] order = {WATCHTOWER, ARMOURY, ARCANUM, ALCHEMY, FORGE, BELL_TOWER, FOUNDRY, SIEGE_YARD, CISTERN, CRYPT};
        int k = 0;
        for (int room : order) {
            if (!FortressFx.built(p, room)) continue;
            final int rm = room;
            AstaFx.later(10 + 6 * k++, () -> {
                if (!p.isAlive() || !FortressSiege.active(p)) return;
                LivingEntity t = nearestFoe(p, p.position(), 22);
                cast(p, rm, t != null ? FxUtil.mid(t) : p.position().add(FxUtil.flat(p.getLookAngle()).scale(6)), true);
            });
        }
    }

    static LivingEntity nearestFoe(ServerPlayer p, Vec3 c, double r) {
        LivingEntity best = null; double bd = r * r;
        for (LivingEntity t : Kit.near(p.serverLevel(), p, c, r)) {
            if (!(t instanceof net.minecraft.world.entity.monster.Enemy) && !(t instanceof Player)) continue;
            double d = t.distanceToSqr(c);
            if (d < bd) { bd = d; best = t; }
        }
        return best;
    }

    /** Where a spell lands: at the feet of the first foe along your line of sight, else the first block in the way. */
    static Vec3 aimAt(ServerPlayer p, double reach) {
        Vec3 eye = p.getEyePosition(), block = Kit.aim(p, reach);
        LivingEntity best = null; double bd = eye.distanceToSqr(block);
        for (LivingEntity t : Kit.along(p.serverLevel(), p, eye, block, 1.0)) {
            double d = t.distanceToSqr(eye);
            if (d < bd) { bd = d; best = t; }
        }
        return best != null ? best.position().add(0, 0.1, 0) : block;
    }

    /** The one foe the player points at (or the nearest to where they look). */
    static LivingEntity pointed(ServerPlayer p, double range) {
        Vec3 eye = p.getEyePosition(), end = eye.add(p.getLookAngle().scale(range));
        LivingEntity best = null; double bd = Double.MAX_VALUE;
        for (LivingEntity t : Kit.along(p.serverLevel(), p, eye, end, 1.2)) {
            double d = t.distanceToSqr(p);
            if (d < bd && FortressSiege.seen(p.serverLevel(), eye, FxUtil.mid(t))) { bd = d; best = t; }
        }
        return best != null ? best : nearestFoe(p, Kit.aim(p, range), 5);
    }

    static void cast(ServerPlayer p, int room, Vec3 at, boolean wave) {
        ServerLevel sl = p.serverLevel();
        float m = FortressFx.mult(p, room);
        Vec3 eye = p.getEyePosition();
        Vec3 dir = at.subtract(eye).lengthSqr() < 1e-4 ? p.getLookAngle() : at.subtract(eye).normalize();
        Vec3 flat = FxUtil.flat(dir);
        long now = sl.getGameTime();
        CompoundTag r = FortressFx.root(p);
        switch (room) {
            case BARRACKS -> {   // Call Guard: two from the garrison (or plain stone guards) at your side for 15 s
                List<FortressDefender> d = FortressSiege.callGarrison(p, p.position(), 300, 2);
                while (d.size() < 2) { FortressDefender g = FortressSiege.guard(p, "minecraft:vindicator", FortressDefender.FOOTMAN, p.position(), 300); if (g == null) break; d.add(g); }
                for (FortressDefender g : d) g.post = null;   // they follow you
                OverdriveFx.play(sl, p.position(), "fort_horn", 0.8f, 1.2f);
            }
            case ARMOURY -> {   // Blade Rack: six phantom weapons rain on the spot
                boolean burn = FortressFx.pair(p, FortressFx.indexOfPair(FORGE, ARMOURY));
                for (int i = 0; i < 6; i++) {
                    double a = i * Math.PI / 3 + ThreadLocalRandom.current().nextDouble(0.4), rr = i == 0 ? 0 : 1.3;
                    Vec3 hitAt = at.add(Math.cos(a) * rr, 0, Math.sin(a) * rr);
                    int delay = 3 + i * 2;
                    FxSend.spike(sl, i % 2 == 0 ? Fx.STEEL : BRASS, hitAt.add(0, 2.2 + 0.75 * delay, 0), new Vec3(0, -1, 0), 2.2f, 0.18f, delay + 8, 0, FxPacket.POP, 0, new Vec3(0, -0.75, 0), delay);   // the tip reaches the ground as it lands
                    AstaFx.later(delay, () -> {
                        for (LivingEntity t : Kit.near(sl, p, hitAt.add(0, 0.8, 0), 1.6)) hit(p, t, 3 * m, ARMOURY);
                        FxSend.ring(sl, Fx.STEEL, hitAt.add(0, 0.05, 0), 0.2f, 1.2f, 0.05f, 6);
                        if (burn) burnPatch(p, hitAt, 1.0f, 40, 1.0f * m);
                    });
                }
                OverdriveFx.play(sl, at, "fort_blades", 0.9f, 1.0f);
            }
            case TRAINING -> {   // Drill Charge: your defenders dash at the target
                LivingEntity t = wave ? nearestFoe(p, at, 6) : pointed(p, 20);
                if (t == null) { tell(p, "No foe to charge"); return; }
                List<FortressDefender> ds = FortressSiege.defendersOut(p);
                if (ds.isEmpty()) for (int i = 0; i < 2; i++) { FortressDefender g = FortressSiege.guard(p, "minecraft:vindicator", FortressDefender.FOOTMAN, p.position(), 160); if (g != null) ds.add(g); }
                boolean twice = FortressFx.pair(p, FortressFx.indexOfPair(WAR_ROOM, TRAINING)) && marked(t);
                for (FortressDefender d : ds) {
                    d.focus = t; d.drilling = true; d.setTarget(t);
                    Vec3 go = t.position().subtract(d.position());
                    d.setDeltaMovement(FxUtil.flat(go).scale(Math.min(1.6, go.length() * 0.25)).add(0, 0.25, 0));
                    d.hurtMarked = true;
                    FxSend.trail(sl, STONE, d.position().add(0, 1, 0), t.position().add(0, 1, 0), 0.15f, 6);
                    AstaFx.later(8, () -> {
                        if (!d.isAlive() || !t.isAlive()) return;
                        if (d.distanceToSqr(t) < 16) { hit(p, t, 6 * m, TRAINING); if (twice) hit(p, t, 6 * m, TRAINING); }
                        d.drilling = false;
                    });
                }
                OverdriveFx.play(sl, p.position(), "fort_horn", 0.6f, 1.5f);
            }
            case WATCHTOWER -> {   // Arrow Slits: a fan of twelve bolts
                Set<Integer> got = new HashSet<>();
                for (int i = 0; i < 12; i++) {
                    double yaw = Math.toRadians(-40 + i * (80.0 / 11));
                    Vec3 d2 = rotY(flat, yaw).add(0, dir.y, 0).normalize();
                    Vec3 end = Kit.aimFrom(sl, p, eye, d2, 18);
                    FxSend.trail(sl, STONE, eye.add(d2), end, 0.05f, 5);
                    for (LivingEntity t : Kit.along(sl, p, eye, end, 0.5)) if (got.add(t.getId() * 16 + i / 3)) { hit(p, t, 2.5f * m, WATCHTOWER); break; }
                }
                OverdriveFx.play(sl, p.position(), "fort_arrow", 1.0f, 0.9f);
            }
            case GATEHOUSE -> {   // Portcullis (and with the Bell Tower beside it, the toll)
                FortressSiege.portcullis(p, m);
                if (FortressFx.pair(p, FortressFx.indexOfPair(GATEHOUSE, BELL_TOWER))) toll(p, p.position().add(flat.scale(2.5)), m * 0.6f);
            }
            case WAR_ROOM -> {   // Battle Plan: mark a foe for 10 s
                LivingEntity t = wave ? nearestFoe(p, at, 6) : pointed(p, 24);
                if (t == null) { tell(p, "No foe to mark"); return; }
                MARKS.put(t.getUUID(), new Mark(p.getUUID(), now + 200));
                for (FortressDefender d : FortressSiege.defendersOut(p)) { d.focus = t; d.setTarget(t); }
                FxSend.ring(sl, BRASS, t.position().add(0, t.getBbHeight() + 0.4, 0), 0.2f, 0.7f, 0.06f, 200);
                FxSend.ring(sl, Fx.CRIMSON, t.position().add(0, 0.05, 0), 0.6f, 1.2f, 0.05f, 30);
                AstaFx.sendKiMark(p, t, 200, true);
                OverdriveFx.play(sl, p.position(), "fort_horn", 0.5f, 1.8f);
            }
            case OBSERVATORY -> {   // Foresight: every foe near you shows through walls for 20 s, and takes 10% more from you
                Map<UUID, Long> s = SEEN.computeIfAbsent(p.getUUID(), k -> new HashMap<>());
                int n = 0;
                for (LivingEntity t : Kit.near(sl, p, p.position().add(0, 1, 0), 24)) {
                    if (!(t instanceof net.minecraft.world.entity.monster.Enemy) && !(t instanceof Player)) continue;
                    s.put(t.getUUID(), now + 400);
                    AstaFx.sendKiMark(p, t, 400, false);
                    n++;
                }
                FxSend.ring(sl, ARCANE, p.position().add(0, 0.1, 0), 1, 24, 0.1f, 16);
                OverdriveFx.play(sl, p.position(), "fort_ley", 0.8f, 0.7f);
                tell(p, n == 0 ? "Nothing stirs nearby" : n + " foe" + (n == 1 ? "" : "s") + " revealed");
            }
            case LIBRARY -> {   // Annotated Page: the next three spells give back 30% of their mana
                r.putInt("notes", 3);
                FxSend.ring(sl, BRASS, p.position().add(0, 1.2, 0), 0.4f, 1.2f, 0.06f, 14);
                OverdriveFx.play(sl, p.position(), "fort_page", 0.8f, 1.0f);
                tell(p, "Annotated: your next three spells give back 30% of their mana");
            }
            case ARCANUM -> {   // Ley Lance: a piercing beam
                Vec3 end = Kit.aimFrom(sl, p, eye, dir, 26);
                FxSend.beam(sl, ARCANE, eye.add(dir.scale(0.6)), end, 0.22f, 8);
                FxSend.beam(sl, Fx.SILVER, eye.add(dir.scale(0.6)), end, 0.08f, 6);
                for (LivingEntity t : Kit.along(sl, p, eye, end, 0.8)) hit(p, t, 10 * m, ARCANUM);
                FxSend.ring(sl, ARCANE, end, 0.2f, 1.4f, 0.06f, 8);
                OverdriveFx.play(sl, p.position(), "fort_ley", 1.0f, 1.0f);
            }
            case SCRYING -> {   // Mirror Ward
                r.putLong("mirror", now + 100);
                FxSend.ring(sl, ARCANE, p.position().add(0, 1, 0), 1.2f, 1.3f, 0.6f, 100);
                OverdriveFx.play(sl, p.position(), "fort_ley", 0.7f, 1.6f);
            }
            case ALCHEMY -> {   // Volatile Flask: lobbed, then a ring
                Vec3 land = at.distanceTo(eye) > 16 ? eye.add(dir.scale(16)) : at;
                Vec3 floor = FortressSiege.floorAt(sl, (int) Math.floor(land.x), (int) Math.floor(land.y), (int) Math.floor(land.z));
                final Vec3 hitAt = floor != null ? floor : land;
                Kit.orb(sl, Fx.TREE, eye.add(dir), hitAt.subtract(eye).scale(1 / 10.0), 0.25f, 10);
                boolean burn = FortressFx.pair(p, FortressFx.indexOfPair(ALCHEMY, FORGE));
                AstaFx.later(10, () -> {
                    for (LivingEntity t : Kit.near(sl, p, hitAt.add(0, 0.6, 0), 3.5)) hit(p, t, 8 * m, ALCHEMY);
                    FxSend.ring(sl, Fx.TREE, hitAt.add(0, 0.1, 0), 0.3f, 3.5f, 0.12f, 10);
                    FxSend.ring(sl, EMBER, hitAt.add(0, 0.2, 0), 0.2f, 2.6f, 0.08f, 8);
                    if (burn) burnPatch(p, hitAt, 2.2f, 60, 1.2f * m);
                    OverdriveFx.play(sl, hitAt, "fort_flask", 1.0f, 1.0f);
                });
            }
            case RUNE_VAULT -> {   // Ward Sigil
                Vec3 c = FortressSiege.floorAt(sl, (int) Math.floor(p.getX()), (int) Math.floor(p.getY()), (int) Math.floor(p.getZ()));
                if (c == null) c = p.position();
                float heal = FortressFx.pair(p, FortressFx.indexOfPair(RUNE_VAULT, CHAPEL)) ? 1.0f : 0f;
                PATCHES.add(new Patch(p.getUUID(), c, 3.5f, now + 160, heal, 1));
                FxSend.ring(sl, BRASS, c.add(0, 0.05, 0), 0.4f, 3.5f, 0.06f, 20);
                FxSend.ring(sl, BRASS, c.add(0, 0.05, 0), 2.6f, 2.9f, 0.04f, 160);
                OverdriveFx.play(sl, c, "fort_page", 0.9f, 0.7f);
            }
            case CHAPEL -> {   // Sanctuary Bell: heal over 4 s
                float amt = 2f * Math.min(2f, m);
                for (int s = 0; s < 4; s++) AstaFx.later(s * 20, () -> {
                    for (LivingEntity a : allies(p, p.position(), 8)) a.heal(amt);
                    FxSend.ring(sl, BRASS, p.position().add(0, 0.1, 0), 0.5f, 8f, 0.08f, 12);
                });
                OverdriveFx.play(sl, p.position(), "fort_bell", 1.0f, 1.2f);
            }
            case RELIQUARY -> {   // Saint's Keepsake
                r.putLong("keep", now + 600);
                FxSend.ring(sl, BRASS, p.position().add(0, 2.2, 0), 0.2f, 0.5f, 0.05f, 600);
                OverdriveFx.play(sl, p.position(), "fort_bell", 0.7f, 1.6f);
                tell(p, "For 30 s, a killing blow leaves you at 1 health instead");
            }
            case CRYPT -> {   // Ossuary Sentries: three bone sentries guard the spot for 20 s
                Vec3 c = FortressSiege.floorAt(sl, (int) Math.floor(at.x), (int) Math.floor(at.y), (int) Math.floor(at.z));
                if (c == null) c = p.position();
                int made = 0;
                for (int i = 0; i < 3; i++) if (FortressSiege.guard(p, "iron_spell_grimoire:bone_sentry", FortressDefender.SENTRY, c, 400) != null) made++;
                if (made == 0) tell(p, "Too many defenders are out already");
                OverdriveFx.play(sl, c, "fort_crumble", 0.8f, 0.6f);
            }
            case BELL_TOWER -> toll(p, wave ? at : p.position(), m);
            case GARDEN -> {   // Quiet Grove
                Vec3 c = FortressSiege.floorAt(sl, (int) Math.floor(p.getX()), (int) Math.floor(p.getY()), (int) Math.floor(p.getZ()));
                if (c == null) c = p.position();
                PATCHES.add(new Patch(p.getUUID(), c, 4f, now + 200, 0, 2));
                FxSend.ring(sl, Fx.TREE, c.add(0, 0.05, 0), 0.4f, 4f, 0.08f, 20);
                OverdriveFx.play(sl, c, "fort_page", 0.8f, 1.3f);
            }
            case HALL -> {   // Rally
                for (LivingEntity a : allies(p, p.position(), 10))
                    TimedModifiers.apply(a, Attributes.ATTACK_SPEED, RALLY, "Rally", 0.2, AttributeModifier.Operation.MULTIPLY_TOTAL, 160);
                if (FortressFx.pair(p, FortressFx.indexOfPair(TREASURY, HALL))) r.putLong("rallyStone", now + 160);
                FxSend.ring(sl, Fx.CRIMSON, p.position().add(0, 0.1, 0), 0.4f, 10f, 0.08f, 14);
                OverdriveFx.play(sl, p.position(), "fort_horn", 1.0f, 1.0f);
            }
            case FORGE -> {   // Molten Bolt
                Vec3 end = Kit.aimFrom(sl, p, eye, dir, 26);
                LivingEntity first = null; double fd = Double.MAX_VALUE;
                for (LivingEntity t : Kit.along(sl, p, eye, end, 0.6)) { double d = t.distanceToSqr(eye); if (d < fd) { fd = d; first = t; } }
                Vec3 hitAt = first != null ? FxUtil.mid(first) : end;
                double len = hitAt.distanceTo(eye);
                int ticks = Math.max(2, (int) Math.ceil(len / 2.5));
                Kit.orb(sl, EMBER, eye.add(dir), dir.scale(len / ticks), 0.3f, ticks);
                final LivingEntity tg = first;
                AstaFx.later(ticks, () -> {
                    if (tg != null && tg.isAlive()) hit(p, tg, 9 * m, FORGE);
                    Vec3 floor = FortressSiege.floorAt(sl, (int) Math.floor(hitAt.x), (int) Math.floor(hitAt.y), (int) Math.floor(hitAt.z));
                    burnPatch(p, floor != null ? floor : hitAt, 1.8f, 80, 1.5f * m);
                    FxSend.ring(sl, EMBER, hitAt, 0.2f, 1.4f, 0.08f, 8);
                    OverdriveFx.play(sl, hitAt, "fort_fire", 0.9f, 1.0f);
                });
            }
            case FOUNDRY -> {   // Hammerfall
                Vec3 c0 = FortressSiege.floorAt(sl, (int) Math.floor(at.x), (int) Math.floor(at.y), (int) Math.floor(at.z));
                final Vec3 c = c0 != null ? c0 : at;
                FxSend.ring(sl, Fx.CRIMSON, c.add(0, 0.05, 0), 2.6f, 3f, 0.04f, 12);
                FxSend.box(sl, Fx.STEEL, c.add(0, 0.9 + 0.55 * 12, 0), new Vec3(1, 0, 0), new Vec3(0, 1, 0), 1.6f, 0.9f, 0.9f, 14, 0, FxPacket.POP, 0, new Vec3(0, -0.55, 0), 12);
                FxSend.box(sl, Fx.BARK, c.add(0, 0.9 + 0.55 * 12 + 2.9, 0), new Vec3(0, 1, 0), new Vec3(1, 0, 0), 2.0f, 0.18f, 0.18f, 14, 0, FxPacket.POP, 0, new Vec3(0, -0.55, 0), 12);
                AstaFx.later(12, () -> {
                    for (LivingEntity t : Kit.near(sl, p, c.add(0, 0.8, 0), 3)) { hit(p, t, 16 * m, FOUNDRY); Kit.push(t, t.position().subtract(c), 0.5, 0.45); }
                    FxSend.ring(sl, STONE, c.add(0, 0.1, 0), 0.4f, 3.6f, 0.2f, 10);
                    FxSend.shake(sl, c, 0.6f, 8, 14);
                    OverdriveFx.play(sl, c, "fort_hammer", 1.2f, 0.9f);
                });
            }
            case WORKSHOP -> {   // Ballista
                Vec3 spot = p.position().add(flat.scale(2));
                FortressTower t = FortressSiege.spawnTower(p, sl, (int) Math.floor(spot.x), (int) Math.floor(p.getY()), (int) Math.floor(spot.z), FortressTower.BALLISTA, 400, 20, false);
                if (t == null) { tell(p, "No room for the ballista there"); return; }
                OverdriveFx.play(sl, spot, "fort_build", 1.0f, 1.1f);
            }
            case SIEGE_YARD -> trebuchet(p, at, m);
            case CISTERN -> {   // Floodgate: a wall of water sweeps forward
                boolean heals = FortressFx.pair(p, FortressFx.indexOfPair(CISTERN, GARDEN));
                Vec3 start = p.position().add(flat.scale(1.0));
                FxSend.wall(sl, WATER, start.add(0, 0.2, 0), flat, new Vec3(0, 1, 0), 3.0f, 3.2f, -60, 60, 1.8f, 14, 0, 0.75f);
                Set<Integer> got = new HashSet<>();
                for (int s = 0; s < 4; s++) {
                    final int ss = s;
                    AstaFx.later(s * 3, () -> {
                        Vec3 c = start.add(flat.scale(2.5 * ss + 1.5));
                        for (LivingEntity t : Kit.near(sl, p, c.add(0, 0.8, 0), 2.6)) if (got.add(t.getId())) { hit(p, t, 5 * m, CISTERN); Kit.push(t, flat, 1.6, 0.3); Kit.drench(t, 60); }
                        if (heals) for (LivingEntity a : allies(p, c, 2.6)) if (got.add(-a.getId() - 1)) a.heal(3f);
                    });
                }
                OverdriveFx.play(sl, p.position(), "fort_flood", 1.0f, 1.0f);
            }
            case TREASURY -> {   // Tribute
                r.putLong("tribute", now + 600);
                FxSend.ring(sl, BRASS, p.position().add(0, 0.1, 0), 0.4f, 2.4f, 0.1f, 14);
                OverdriveFx.play(sl, p.position(), "fort_coins", 1.0f, 1.0f);
                tell(p, "Tribute: three times the Stone for 30 s");
            }
            default -> {}
        }
        FortressFx.sync(p, false);
    }

    static final UUID RALLY = UUID.fromString("a7c1f001-0b3e-4c2a-9a01-5f0e1d2c0010");

    /** Great Toll: a shockwave from a spot. */
    static void toll(ServerPlayer p, Vec3 c, float m) {
        ServerLevel sl = p.serverLevel();
        for (LivingEntity t : Kit.near(sl, p, c.add(0, 1, 0), 6)) { hit(p, t, 6 * m, BELL_TOWER); Kit.push(t, t.position().subtract(c), 1.4, 0.35); }
        FxSend.ring(sl, BRASS, c.add(0, 0.6, 0), 0.5f, 6f, 0.3f, 10);
        FxSend.ring(sl, Fx.SILVER, c.add(0, 1.2, 0), 0.3f, 5f, 0.15f, 8);
        FxSend.shake(sl, c, 0.4f, 8, 12);
        OverdriveFx.play(sl, c, "fort_bell", 1.3f, 0.7f);
    }

    /** Trebuchet Shot: a boulder on a high arc, crashing down on an area. */
    static void trebuchet(ServerPlayer p, Vec3 at, float m) {
        ServerLevel sl = p.serverLevel();
        Vec3 c0 = FortressSiege.floorAt(sl, (int) Math.floor(at.x), (int) Math.floor(at.y), (int) Math.floor(at.z));
        final Vec3 c = c0 != null ? c0 : at;
        FxSend.ring(sl, Fx.CRIMSON, c.add(0, 0.05, 0), 3.2f, 3.5f, 0.04f, 20);
        FxSend.box(sl, STONE, c.add(0, 0.7 + 0.68 * 20, 0), new Vec3(1, 0, 0), new Vec3(0, 1, 0), 0.7f, 0.7f, 0.7f, 20, 0, FxPacket.POP, 18, new Vec3(0, -0.68, 0), 20);
        AstaFx.later(20, () -> {
            for (LivingEntity t : Kit.near(sl, p, c.add(0, 0.8, 0), 3.5)) { hit(p, t, 14 * m, SIEGE_YARD); Kit.push(t, t.position().subtract(c), 0.6, 0.4); }
            FxSend.ring(sl, STONE, c.add(0, 0.1, 0), 0.4f, 4f, 0.25f, 10);
            FxSend.shake(sl, c, 0.7f, 8, 16);
            OverdriveFx.play(sl, c, "fort_hammer", 1.4f, 0.7f);
        });
        OverdriveFx.play(sl, p.position(), "fort_launch", 1.0f, 1.0f);
    }

    static Vec3 rotY(Vec3 v, double a) {
        double c = Math.cos(a), s = Math.sin(a);
        return new Vec3(v.x * c - v.z * s, 0, v.x * s + v.z * c);
    }

    /** Per player, once a second (FortressFx.onTick): nothing yet that isn't driven by its own clock. */
    static void tick(ServerPlayer p) {}

    // ================================================================== test hooks
    public static void testCast(ServerPlayer p, int room) { cast(p, room); }
    public static void testCastAt(ServerPlayer p, int room, Vec3 at) { cast(p, room, at, true); }
    public static void testClear() { PATCHES.clear(); MARKS.clear(); SEEN.clear(); }
}
