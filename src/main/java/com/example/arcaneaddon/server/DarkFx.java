package com.example.arcaneaddon.server;

import com.example.arcaneaddon.ArcaneAddon;
import com.example.arcaneaddon.item.DarkGrimoireItem;
import com.example.arcaneaddon.registry.AddonItemRegistry;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.damage.SpellDamageSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

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
 * Yami Sukehiro's Dark Magic - the ten spells of the Dark Grimoire and its passive.
 *
 *  1 Dark Cloaked Black Blade        - coat your blade in darkness: heavier melee hits that devour the target's mana
 *  2 Dark Cloaked Dimension Slash    - a cut through space itself: instant, 40 blocks, passes through walls
 *  3 Dark Cloaked Avidya Slash Raijin - a ki-guided slash that bends toward the enemy you're looking at
 *  4 Black Cocoon                    - a sphere of darkness that swallows every blow and carries you, then bursts
 *  5 Dark Cloaked Death Thrust       - a piercing thrust of darkness straight ahead
 *  6 Black Moon                      - a moon of darkness that swallows enemy spells, then hurls them back
 *  7 Black Hole                      - a hole of darkness that drags enemies in and crushes them
 *  8 Mana Zone                       - spread your mana 14 blocks for 20 s: every other move turns into its Mana Zone variant
 *  9 Ki Read: Foresight              - read their ki: the next 3 attacks simply miss
 * 10 Dimension Slash: Event Horizon  - the ultimate cut that splits space open and leaves the horizon behind
 *
 * Passive "Surpass Your Limits": the first time you fall below 25% health (once per 90 s), every spell cooldown resets
 * and you heal 25% of your health.
 * Sword techniques (every slash, the thrust, Black Blade, Mana Zone and Event Horizon) need the Dark Katana in the main hand.
 *
 * All visuals use the addon's own 3D shapes (black body, violet rim) and are kept thin so the screen stays readable.
 */
@Mod.EventBusSubscriber(modid = ArcaneAddon.MODID)
public class DarkFx {
    private static final int D = Fx.DARK, S = Fx.SILVER;

    // ------------------------------------------------------------------ state
    private static final Map<UUID, Long> BLADE = new HashMap<>();          // Black Blade active until
    private static final Map<UUID, Moon> MOON = new HashMap<>();           // Black Moon
    private static final Map<UUID, Long> ZONE = new HashMap<>();           // Mana Zone: Dimension Slash until
    private static final Map<UUID, Foresight> FORESIGHT = new HashMap<>();
    private static final Map<UUID, Long> LIMITS_USED = new HashMap<>();

    private static final class Moon { long until; float absorbed; float power; boolean zone; }
    private static final class Foresight { long until; int charges; boolean counter; }

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
        return sl.getEntitiesOfClass(LivingEntity.class, box, x -> x != p && x.isAlive() && !x.isAlliedTo(p));
    }

    private static boolean alive(ServerPlayer p) { return p.isAlive() && !p.isRemoved(); }

    private static boolean wearsDarkGrimoire(ServerPlayer p) {
        ItemStack s = Utils.getPlayerSpellbookStack(p);
        return s != null && s.getItem() instanceof DarkGrimoireItem;
    }

    /**
     * The mark every dark cut leaves: a white hairline appears first, then space opens along it a tick later - a black
     * blade-shaped tear with a glowing edge and black flame, held open for a moment.
     */
    private static void cutMark(ServerLevel sl, Vec3 at, Vec3 across, float half) {
        Vec3 ac = across.normalize();
        Vec3 face = ac.cross(new Vec3(0, 1, 0));
        if (face.lengthSqr() < 1.0E-4) face = new Vec3(1, 0, 0);
        face = face.normalize();
        FxSend.beam(sl, S, at.subtract(ac.scale(half)), at.add(ac.scale(half)), 0.05f, 4);
        final Vec3 f = face;
        AstaFx.later(1, () -> {
            FxSend.beam(sl, D, at.subtract(ac.scale(half)), at.add(ac.scale(half)), 0.42f, 8);
            FxSend.crescent(sl, D, at, f, ac, half * 0.9f, half * 1.05f, 7, 0f);
            FxSend.beam(sl, S, at.subtract(ac.scale(half * 0.85)), at.add(ac.scale(half * 0.85)), 0.07f, 6);
        });
    }

    // ------------------------------------------------------------------ Mana Zone
    /** Radius of Yami's Mana Zone. While it is up every Dark Grimoire move turns into its Mana Zone variant. */
    private static final double ZONE_R = 14;
    /** How far you read ki while the zone is up. */
    private static final double KI_RANGE = 80;

    public static boolean inZone(ServerPlayer p) {
        Long z = ZONE.get(p.getUUID());
        return z != null && p.serverLevel().getGameTime() <= z;
    }

    /** Enemies inside the zone, nearest first. */
    private static List<LivingEntity> zoneEnemies(ServerPlayer p) {
        List<LivingEntity> l = new ArrayList<>(enemies(p.serverLevel(), p, p.getBoundingBox().inflate(ZONE_R)));
        l.removeIf(t -> t.distanceTo(p) > ZONE_R);
        l.sort(Comparator.comparingDouble(t -> t.distanceToSqr(p)));
        return l;
    }

    /** A point on the zone's edge, on the far side of {@code t} from you (cuts come out of the zone at them). */
    private static Vec3 edgeBehind(ServerPlayer p, LivingEntity t, double spread) {
        Vec3 d = t.position().subtract(p.position()).multiply(1, 0, 1);
        double a = Math.atan2(d.z, d.x) + spread;
        return p.position().add(Math.cos(a) * ZONE_R, 1.2, Math.sin(a) * ZONE_R);
    }

    private static void zoneMsg(ServerPlayer p, String key) {
        p.displayClientMessage(Component.translatable("ui.arcane_addon.zone." + key).withStyle(ChatFormatting.DARK_PURPLE), true);
    }

    /** A pulse of the zone: its edge flares and a ring runs out from you. */
    private static void zonePulse(ServerPlayer p, ServerLevel sl) {
        FxSend.ring(sl, D, p.position().add(0, 0.05, 0), 0.5f, (float) ZONE_R, 0.12f, 10);
        FxSend.ring(sl, D, p.position().add(0, 0.05, 0), (float) ZONE_R - 0.2f, (float) ZONE_R, 0.7f, 10);
    }

    /** Black Blade's aura outline around your body (client AuraOutlineLayer), for everyone who can see you. */
    private static void sendAura(ServerPlayer p, int life) {
        com.example.arcaneaddon.network.FxPacket pkt = new com.example.arcaneaddon.network.FxPacket();
        pkt.shape = 14; pkt.id = p.getId(); pkt.palette = 0; pkt.life = life;
        com.example.arcaneaddon.network.AddonNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), pkt);
    }

    /** Anticipation: thin wisps of darkness drawn into your sword hand while the animation winds up. */
    private static void windup(ServerPlayer p, int ticks, float size) {
        ServerLevel sl = p.serverLevel();
        for (int k = 0; k < ticks; k += 2) {
            final int kk = k;
            AstaFx.later(k, () -> {
                if (!alive(p)) return;
                Vec3 h = handPos(p);
                double a = kk * 2.3 + p.getYRot() * 0.017;
                Vec3 from = h.add(Math.cos(a) * 1.4 * size, 0.5 - kk * 0.12, Math.sin(a) * 1.4 * size);
                FxSend.trail(sl, D, from, from.add(h.subtract(from).scale(0.75)), 0.09f * size, 4);
            });
        }
        sound(sl, p.position(), SoundEvents.SOUL_ESCAPE, 0.5f, 0.8f);
    }

    private static Vec3 handPos(ServerPlayer p) {
        double yr = Math.toRadians(p.getYRot());
        double lx = Math.cos(yr), lz = Math.sin(yr), fx = -Math.sin(yr), fz = Math.cos(yr);
        return new Vec3(p.getX() + fx * 0.55 - lx * 0.4, p.getY() + p.getEyeHeight() * 0.72, p.getZ() + fz * 0.55 - lz * 0.4);
    }

    // ================================================================== 1. Black Blade
    /**
     * Darkness climbs your body in rings and cloaks the blade. Heavier melee hits that swallow mana.
     * Mana Zone: every hit also sends a dimension cut to the next enemy inside the zone.
     */
    public static void blackBlade(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        BLADE.put(p.getUUID(), sl.getGameTime() + 20 * 20);
        sendAura(p, 20 * 20);
        for (int k = 0; k < 6; k++) {
            final int kk = k;
            AstaFx.later(k * 2, () -> {
                if (alive(p)) FxSend.ring(sl, D, p.position().add(0, 0.1 + kk * 0.36, 0), 1.0f - kk * 0.1f, 0.45f, 0.22f, 8);
            });
        }
        AstaFx.later(10, () -> {   // the blade raised high: the coat settles over it
            if (!alive(p)) return;
            Vec3 h = handPos(p).add(0, 0.6, 0), d = p.getLookAngle(), rt = rightOf(d);
            FxLib.flash(sl, D, h, 0.8f);
            FxSend.wall(sl, D, h, rt, rt.cross(d).normalize(), 0.3f, 1.8f, -180, 180, 0.08f, 7, 0, 0);
            FxSend.beam(sl, S, h, h.add(d.scale(0.3)).add(0, 1.2, 0), 0.06f, 4);   // one glint along the edge as the coat settles
            sound(sl, p.position(), SoundEvents.ARMOR_EQUIP_NETHERITE, 0.8f, 0.6f);
        });
        if (inZone(p)) zoneMsg(p, "black_blade");
        sound(sl, p.position(), SoundEvents.SOUL_ESCAPE, 1f, 0.6f);
    }

    // ================================================================== 2. Dimension Slash
    /** Ticks from the cast to the draw-cut in the dark_dimension_slash animation (stance, hold, then the swing). */
    private static final int DIM_RELEASE = 12;

    /**
     * Yami's iaido: he sinks into a draw stance while darkness gathers at the hilt and space goes still... then one
     * horizontal draw-cut. A huge black-flame crescent tears forward 40 blocks, a white hairline runs through space ahead
     * of it (walls don't stop it), and a moment later space itself splits open along the line.
     * Mana Zone: Dark Cloaked Dimension Slash - after the draw, the cut simply appears on every enemy in the zone.
     */
    public static void dimensionSlash(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        // the stance: darkness drawn into the sheath at your left hip, a pulse under your feet, then a glint on the edge
        for (int k = 0; k < 4; k++) {
            final int kk = k;
            AstaFx.later(k * 2, () -> {
                if (!alive(p)) return;
                Vec3 hilt = hipHilt(p);
                double a = kk * 1.7;
                Vec3 from = hilt.add(Math.cos(a) * 1.8, 0.6 - kk * 0.3, Math.sin(a) * 1.8);
                Vec3 d = hilt.subtract(from).normalize();
                FxSend.crescent(sl, D, from, d, rightOf(d).cross(d).normalize(), 0.45f, 0.2f, 4, 0.45f);
                FxSend.ring(sl, D, p.position().add(0, 0.05, 0), 1.6f - kk * 0.3f, 0.3f, 0.08f, 5);
            });
        }
        AstaFx.later(DIM_RELEASE - 3, () -> {
            if (!alive(p)) return;
            Vec3 h = hipHilt(p);
            FxSend.beam(sl, S, h, h.add(rightOf(p.getLookAngle()).scale(-0.6)), 0.05f, 3);   // the glint as the blade leaves the sheath
            sound(sl, p.position(), SoundEvents.ARMOR_EQUIP_NETHERITE, 0.7f, 1.6f);
        });
        sound(sl, p.position(), SoundEvents.SOUL_ESCAPE, 0.8f, 0.5f);
        AstaFx.later(DIM_RELEASE, () -> {
            if (!alive(p)) return;
            if (inZone(p)) { zoneDimension(p, sl, power); return; }
            Vec3 start = p.getEyePosition().add(0, -0.25, 0);
            Vec3 dir = p.getLookAngle();
            Vec3 end = start.add(dir.scale(40));
            Vec3 rt = rightOf(dir), up = rt.cross(dir).normalize();
            Vec3 left = rt.scale(-1);
            final float SPEED = 2.6f;
            // everything the wave of darkness passes through is cut as it arrives (a wide, tall path)
            for (LivingEntity t : enemies(sl, p, new AABB(start, end).inflate(4.5))) {
                Vec3 m = mid(t), on = closestOnSegment(start, end, m);
                Vec3 off = m.subtract(on);
                double side = Math.abs(off.dot(rt)), high = Math.abs(off.dot(up));
                double along = on.distanceTo(start);
                if (side > 2.5 + along * 0.12 || high > 2.4) continue;   // the wave widens as it travels
                final LivingEntity tt = t;
                AstaFx.later((int) (along / SPEED), () -> {
                    if (!tt.isAlive() || !alive(p)) return;
                    AstaFx.damage(p, tt, power, "dimension_slash");
                    FxLib.impact(sl, D, mid(tt), 1.3f);
                    sound(sl, tt.position(), SoundEvents.PLAYER_ATTACK_CRIT, 1f, 0.6f);
                });
            }
            // the draw and the release: one clean, solid slash - a black blade of darkness with a white-hot edge,
            // drawn left to right as you cut, flying forward and growing as it goes
            Vec3 o = start.add(dir.scale(1.2));
            FxSend.slash(sl, D, start.add(dir.scale(0.6)), dir, rt, 2.2f, 2.6f, 5, 0f);      // the swing itself, at your blade
            FxSend.slash(sl, D, o, dir, rt, 3.0f, 8.5f, 16, SPEED);                          // the slash that flies
            // after it passes: a white hairline left through space, and the tears where space was cut
            AstaFx.later(3, () -> {
                FxSend.beam(sl, S, o, end, 0.05f, 10);
                for (int k = 5; k < 40; k += 5) {
                    Vec3 c = start.add(dir.scale(k));
                    double h = 0.8 + k * 0.04;
                    FxSend.beam(sl, D, c.subtract(up.scale(h)).add(rt.scale(0.4)), c.add(up.scale(h)).subtract(rt.scale(0.4)), 0.16f, 10);
                }
                sound(sl, p.position(), SoundEvents.WITHER_SHOOT, 0.5f, 0.4f);
            });
            FxLib.shockRing(sl, D, p.position().add(0, 0.05, 0), 1.2f);
            sound(sl, p.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.3f, 0.4f);
            sound(sl, p.position(), SoundEvents.WARDEN_SONIC_BOOM, 0.5f, 0.7f);
            sound(sl, p.position(), SoundEvents.ENDERMAN_TELEPORT, 0.7f, 0.5f);
        });
    }

    /** Where Yami's sheathed katana's hilt sits: at the left hip, slightly forward. */
    private static Vec3 hipHilt(ServerPlayer p) {
        double yr = Math.toRadians(p.getYRot());
        double lx = Math.cos(yr), lz = Math.sin(yr), fx = -Math.sin(yr), fz = Math.cos(yr);
        return new Vec3(p.getX() + fx * 0.35 + lx * 0.32, p.getY() + 0.95, p.getZ() + fz * 0.35 + lz * 0.32);
    }

    /**
     * Mana Zone: Dark Cloaked Dimension Slash - ONE huge slash: a ring of black flame sweeps out from you through the
     * whole zone, and every enemy inside is cut the moment it reaches them.
     */
    private static void zoneDimension(ServerPlayer p, ServerLevel sl, float power) {
        zoneMsg(p, "dimension_slash");
        Vec3 c = p.position().add(0, 1.0, 0);
        int travel = 7;   // ticks for the slash to reach the zone's edge
        FxSend.wall(sl, D, c, new Vec3(1, 0, 0), new Vec3(0, 0, 1), 1.0f, (float) ZONE_R, -180, 180, 0.9f, travel + 1, 0, 0);
        FxSend.wall(sl, S, c, new Vec3(1, 0, 0), new Vec3(0, 0, 1), 1.0f, (float) ZONE_R, -180, 180, 0.05f, travel + 1, 0, 0);
        for (LivingEntity t : zoneEnemies(p)) {
            final LivingEntity tt = t;
            AstaFx.later((int) Math.round(t.distanceTo(p) / ZONE_R * travel), () -> {
                if (!tt.isAlive() || !alive(p)) return;
                AstaFx.damage(p, tt, power, "mana_zone_dimension_slash");
                FxLib.impact(sl, D, mid(tt), 1.1f);
            });
        }
        sound(sl, p.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.3f, 0.4f);
        sound(sl, p.position(), SoundEvents.ENDERMAN_TELEPORT, 0.7f, 0.4f);
    }

    // ================================================================== 3. Avidya Slash Raijin (ki-guided)
    /**
     * A ki-guided crescent that bends toward the enemy you look at.
     * Mana Zone: Raijin - three crescents tear out of the zone's edge around the target and converge on it.
     */
    /** Ticks from the cast to the upward cut in the dark_avidya animation (low crouch, then the rising draw). */
    private static final int AVIDYA_RELEASE = 5;

    public static void avidyaSlash(ServerPlayer p, float power) {
        windup(p, AVIDYA_RELEASE, 0.9f);
        AstaFx.later(AVIDYA_RELEASE, () -> { if (alive(p)) avidyaRelease(p, power); });
    }

    private static void avidyaRelease(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
        LivingEntity target = null;
        double best = Math.cos(Math.toRadians(25));
        for (LivingEntity t : enemies(sl, p, p.getBoundingBox().inflate(30))) {
            Vec3 to = mid(t).subtract(eye);
            double d = to.length();
            if (d > 30 || !p.hasLineOfSight(t)) continue;
            double dot = to.scale(1 / d).dot(look);
            if (dot > best) { best = dot; target = t; }
        }
        if (inZone(p)) {
            List<LivingEntity> zs = zoneEnemies(p);
            final LivingEntity t = target != null && t0in(p, target) ? target : (zs.isEmpty() ? null : zs.get(0));
            if (t != null) {
                zoneMsg(p, "avidya_slash");
                for (int k = 0; k < 3; k++) {
                    final Vec3 from = edgeBehind(p, t, (k - 1) * 0.7);
                    AstaFx.later(k * 2, () -> {
                        if (!alive(p)) return;
                        FxSend.ring(sl, D, from, 0.1f, 1.4f, 0.2f, 6);   // the zone opens where the cut comes out
                        guidedSlash(p, sl, from, mid(t).subtract(from).normalize(), t, power * 0.7f, 0, new HashSet<>());
                    });
                }
                sound(sl, p.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 1.5f);
                return;
            }
        }
        guidedSlash(p, sl, handPos(p), look, target, power, 0, new HashSet<>());
        Vec3 rt = rightOf(look);
        FxSend.wall(sl, D, handPos(p), look, rt.scale(-0.5).add(0, 0.87, 0), 1.2f, 1.6f, -80, 80, 0.25f, 6, 2, 0);   // the upward draw
        sound(sl, p.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 1.3f);
        sound(sl, p.position(), SoundEvents.TRIDENT_RIPTIDE_1, 0.6f, 1.5f);
    }

    private static boolean t0in(ServerPlayer p, LivingEntity t) { return t.distanceTo(p) <= ZONE_R; }

    // ================================================================== 4. Black Cocoon
    /** Ticks for the shell to close around you (the dark_black_cocoon animation crosses the arms over this time). */
    private static final int COCOON_CLOSE = 8;
    private static final float COCOON_R = 1.6f;
    private static final java.util.UUID COCOON_SPEED_ID = java.util.UUID.fromString("6b1c3a52-9d0e-4f7e-a1c4-2f3b8e7d9c10");
    private static final Map<UUID, Cocoon> COCOON = new HashMap<>();
    private static final class Cocoon { long start, until; float power; boolean zone; float absorbed; }

    /**
     * Yami's Black Cocoon: darkness wraps you in a solid black sphere. Nothing gets through it for 4 s - every hit and
     * every spell is swallowed - and you can drift inside it, faster, without falling (it carries you, like the cocoon
     * he travels in). When it opens it bursts: everything within 4 blocks is cut and thrown back, harder for every blow it
     * swallowed. Mana Zone: it lasts 6 s and when it opens every enemy in the zone is cut.
     */
    public static void blackCocoon(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        long now = sl.getGameTime();
        Cocoon c = new Cocoon();
        c.zone = inZone(p);
        c.start = now;
        c.until = now + COCOON_CLOSE + (c.zone ? 120 : 80);
        c.power = power;
        COCOON.put(p.getUUID(), c);
        sendCocoon(p, (int) (c.until - now));
        // darkness pulled in from around you as the shell closes
        for (int k = 0; k < 4; k++) {
            final int kk = k;
            AstaFx.later(k * 2, () -> {
                if (!alive(p)) return;
                FxSend.ring(sl, D, p.position().add(0, 0.05, 0), 3.2f - kk * 0.6f, 0.35f, 0.1f, 5);
                double a = kk * 1.9;
                Vec3 ctr = p.position().add(0, 1.0, 0);
                Vec3 from = ctr.add(Math.cos(a) * 3, 0.8 - kk * 0.5, Math.sin(a) * 3);
                FxSend.trail(sl, D, from, from.add(ctr.subtract(from).scale(0.7)), 0.12f, 5);
            });
        }
        AstaFx.later(COCOON_CLOSE, () -> {
            if (!alive(p)) return;
            sound(sl, p.position(), SoundEvents.ARMOR_EQUIP_NETHERITE, 0.9f, 0.4f);
            sound(sl, p.position(), SoundEvents.SCULK_SHRIEKER_SHRIEK, 0.25f, 0.5f);
        });
        if (c.zone) zoneMsg(p, "black_cocoon");
        sound(sl, p.position(), SoundEvents.SOUL_ESCAPE, 1f, 0.4f);
    }

    private static void sendCocoon(ServerPlayer p, int life) {
        com.example.arcaneaddon.network.FxPacket pkt = new com.example.arcaneaddon.network.FxPacket();
        pkt.shape = 15; pkt.id = p.getId(); pkt.palette = (byte) D; pkt.life = life; pkt.r = COCOON_R; pkt.sweep = COCOON_CLOSE;
        com.example.arcaneaddon.network.AddonNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), pkt);
    }

    private static void cocoonSpeed(ServerPlayer p, boolean on) {
        var att = p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        if (att == null) return;
        att.removeModifier(COCOON_SPEED_ID);
        if (on) att.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(COCOON_SPEED_ID, "black_cocoon",
                0.6, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_TOTAL));
    }

    private static void cocoonTick(ServerPlayer p, ServerLevel sl, Cocoon c, long now) {
        boolean closed = now >= c.start + COCOON_CLOSE;
        if (closed) {
            cocoonSpeed(p, true);
            Vec3 v = p.getDeltaMovement();
            if (v.y < -0.06) { p.setDeltaMovement(v.x, -0.06, v.z); p.hurtMarked = true; }   // it carries you: no falling
            p.fallDistance = 0;
            if (now % 40 == 0) sendCocoon(p, (int) (c.until - now));
            if (now % 6 == 0) {   // a slow ripple of darkness around its foot
                Vec3 g = FxLib.ground(sl, p.position(), 3);
                if (g != null) FxSend.ring(sl, D, g.add(0, 0.04, 0), COCOON_R * 0.8f, COCOON_R * 1.4f, 0.05f, 8);
            }
        }
    }

    /** The shell bursts open: a sphere of cuts, everything near is thrown back. */
    private static void cocoonBurst(ServerPlayer p, ServerLevel sl, Cocoon c) {
        cocoonSpeed(p, false);
        sendCocoon(p, 0);
        Vec3 ctr = p.position().add(0, 1.0, 0);
        float dmg = c.power + c.absorbed * 0.5f;
        for (LivingEntity t : enemies(sl, p, p.getBoundingBox().inflate(4.5))) {
            if (t.distanceTo(p) > 4.5) continue;
            AstaFx.damage(p, t, dmg, "black_cocoon");
            Vec3 away = t.position().subtract(p.position()).multiply(1, 0, 1);
            if (away.lengthSqr() > 1.0E-4) t.knockback(1.6, -away.x, -away.z);
            cutMark(sl, mid(t), rightOf(away.lengthSqr() > 1.0E-4 ? away.normalize() : p.getLookAngle()).add(0, 0.6, 0), 1.2f);
        }
        // the shell splits along three great circles and flies apart as crescents
        for (int k = 0; k < 6; k++) {
            double a = Math.PI * 2 * k / 6;
            Vec3 d = new Vec3(Math.cos(a), 0.25, Math.sin(a)).normalize();
            FxSend.slash(sl, D, ctr.add(d.scale(COCOON_R)), d, new Vec3(0, 1, 0).add(d.scale(-0.25)), 1.2f, 2.6f, 7, 0.55f);
        }
        FxSend.wall(sl, D, ctr, new Vec3(1, 0, 0), new Vec3(0, 0, 1), COCOON_R, 5.0f, -180, 180, 0.9f, 8, 0, 0);
        FxSend.ring(sl, S, ctr, COCOON_R, 5.5f, 0.04f, 7);
        Vec3 g = FxLib.ground(sl, p.position(), 3);
        if (g != null) FxLib.shockRing(sl, D, g.add(0, 0.05, 0), 1.3f);
        if (c.zone) {
            zonePulse(p, sl);
            int i = 0;
            for (LivingEntity t : zoneEnemies(p)) {
                if (t.distanceTo(p) <= 4.5) continue;
                final LivingEntity tt = t;
                AstaFx.later(2 + (i++ % 8), () -> {
                    if (!tt.isAlive() || !alive(p)) return;
                    AstaFx.damage(p, tt, c.power * 0.8f, "black_cocoon");
                    cutMark(sl, mid(tt), new Vec3(0.4, 1, 0.3), 1.3f);
                });
            }
        }
        sound(sl, p.position(), SoundEvents.GLASS_BREAK, 1f, 0.4f);
        sound(sl, p.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.3f, 0.5f);
        sound(sl, p.position(), SoundEvents.WARDEN_SONIC_BOOM, 0.3f, 1.2f);
    }

    /** One ki-guided slash: a black-flame crescent flying 2.4 blocks a tick, steering toward its target, leaving a dark wake. */
    private static void guidedSlash(ServerPlayer p, ServerLevel sl, Vec3 pos, Vec3 dir, LivingEntity target, float dmg, int step, Set<UUID> hit) {
        if (!alive(p) || step > 18) return;
        if (target != null && target.isAlive()) {
            Vec3 to = mid(target).subtract(pos);
            dir = dir.scale(0.55).add(to.normalize().scale(0.45)).normalize();
        }
        Vec3 next = pos.add(dir.scale(2.4));
        Vec3 rt = rightOf(dir), up = rt.cross(dir).normalize();
        Vec3 blade = rt.scale(0.5).add(up.scale(0.87));   // tilted like a diagonal cut
        FxSend.crescent(sl, D, next, dir, blade, 0.95f, 1.05f, 3, 0f);
        FxSend.trail(sl, D, pos, next, 0.28f, 6);
        FxSend.trail(sl, S, pos, next, 0.04f, 4);
        for (LivingEntity t : enemies(sl, p, new AABB(pos, next).inflate(1.0))) {
            if (!t.getBoundingBox().inflate(0.5).clip(pos, next).isPresent() && t.distanceToSqr(next) > 1.6) continue;
            if (!hit.add(t.getUUID())) continue;
            AstaFx.damage(p, t, dmg, "avidya_slash");
            cutMark(sl, mid(t), blade, 1.3f);
            sound(sl, t.position(), SoundEvents.PLAYER_ATTACK_CRIT, 1f, 0.8f);
            if (t == target) return;
        }
        if (target == null && clip(sl, p, pos, next).distanceToSqr(next) > 1.0E-4) {
            FxLib.impact(sl, D, next, 0.8f);
            return;
        }
        final Vec3 nd = dir;
        AstaFx.later(1, () -> guidedSlash(p, sl, next, nd, target, dmg, step + 1, hit));
    }

    // ================================================================== 5. Death Thrust
    /**
     * A thrust that drives a solid spike of darkness 12 blocks straight through everything, punching rings of air out.
     * Mana Zone: the thrust comes out of the dark behind the three nearest enemies and runs them through.
     */
    /** Ticks from the cast to the thrust in the dark_death_thrust animation (draw back, hold, then lunge). */
    private static final int THRUST_RELEASE = 9;

    public static void deathThrust(ServerPlayer p, float power) {
        windup(p, THRUST_RELEASE, 1.0f);
        AstaFx.later(THRUST_RELEASE, () -> { if (alive(p)) thrustRelease(p, power); });
    }

    private static void thrustRelease(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        if (inZone(p)) {
            List<LivingEntity> zs = zoneEnemies(p);
            if (!zs.isEmpty()) {
                zoneMsg(p, "death_thrust");
                for (int i = 0; i < Math.min(3, zs.size()); i++) {
                    final LivingEntity t = zs.get(i);
                    AstaFx.later(i * 3, () -> {
                        if (!alive(p) || !t.isAlive()) return;
                        Vec3 away = t.position().subtract(p.position()).multiply(1, 0, 1);
                        Vec3 d = away.lengthSqr() < 1.0E-4 ? p.getLookAngle() : away.normalize().scale(-1);   // from behind them, toward you
                        Vec3 portal = mid(t).subtract(d.scale(2.2));
                        Vec3 rt = rightOf(d);
                        FxSend.wall(sl, D, portal, rt, rt.cross(d).normalize(), 0.2f, 1.1f, -180, 180, 0.2f, 9, 0, 0);
                        FxSend.spike(sl, D, portal, d, 4.2f, 0.28f, 10, 2, com.example.arcaneaddon.network.FxPacket.EXTEND);
                        FxSend.beam(sl, S, portal, portal.add(d.scale(4.2)), 0.05f, 5);
                        AstaFx.damage(p, t, power, "death_thrust");
                        t.knockback(1.4, -d.x, -d.z);
                        FxLib.impact(sl, D, mid(t), 1.1f);
                        sound(sl, t.position(), SoundEvents.TRIDENT_RIPTIDE_3, 0.7f, 0.9f);
                    });
                }
                return;
            }
        }
        Vec3 dir = p.getLookAngle();
        Vec3 from = handPos(p);
        Vec3 end = clip(sl, p, p.getEyePosition(), p.getEyePosition().add(dir.scale(12)));
        for (LivingEntity t : enemies(sl, p, new AABB(from, end).inflate(1.5))) {
            if (!t.getBoundingBox().inflate(0.9).clip(p.getEyePosition(), end).isPresent()) continue;
            AstaFx.damage(p, t, power, "death_thrust");
            t.knockback(1.8, -dir.x, -dir.z);
            FxLib.impact(sl, D, mid(t), 1.1f);
        }
        Vec3 rt = rightOf(dir), up = rt.cross(dir).normalize();
        float L = (float) from.distanceTo(end);
        FxSend.spike(sl, D, from, dir, L, 0.34f, 11, 2, com.example.arcaneaddon.network.FxPacket.EXTEND);   // the spike of darkness
        FxSend.beam(sl, S, from, end, 0.07f, 7);
        for (int k = 0; k < (int) L; k += 2) {
            final Vec3 c = from.add(dir.scale(k + 0.6));
            final int kk = k;
            AstaFx.later(kk / 4, () -> FxSend.wall(sl, kk % 4 == 0 ? D : S, c, rt, up, 0.3f, 1.0f + kk * 0.05f, -180, 180, 0.08f, 6, 0, 0));
        }
        AstaFx.later(2, () -> FxLib.spikeBurst(sl, D, end, 0.7f));
        sound(sl, p.position(), SoundEvents.TRIDENT_RIPTIDE_3, 0.8f, 0.8f);
        sound(sl, p.position(), SoundEvents.PLAYER_ATTACK_STRONG, 1.2f, 0.5f);
    }

    // ================================================================== 6. Black Moon
    /**
     * A moon of darkness that swallows every spell aimed at you for 4 s, then flies out carrying half of it.
     * Mana Zone: it lasts 6 s and, when it breaks, a crescent flies at every enemy in the zone.
     */
    public static void blackMoon(ServerPlayer p, float power) {
        Moon m = new Moon();
        boolean z = inZone(p);
        m.until = p.serverLevel().getGameTime() + (z ? 120 : 80);
        m.power = power;
        m.zone = z;
        MOON.put(p.getUUID(), m);
        if (z) zoneMsg(p, "black_moon");
        FxLib.implode(p.serverLevel(), D, moonPos(p), 1.2f);
        FxSend.beam(p.serverLevel(), D, p.getEyePosition(), moonPos(p), 0.25f, 8);   // darkness rises from you into the moon
        sound(p.serverLevel(), p.position(), SoundEvents.SOUL_ESCAPE, 1.0f, 0.5f);
    }

    /** The black moon hangs above you and a little ahead, like an eclipse in the sky. */
    private static Vec3 moonPos(ServerPlayer p) {
        Vec3 f = new Vec3(p.getLookAngle().x, 0, p.getLookAngle().z);
        f = f.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : f.normalize();
        return p.position().add(0, p.getBbHeight() + 1.3, 0).add(f.scale(0.9));
    }

    /**
     * An eclipse of darkness: a sphere of slowly turning black-flame rings, a pale corona around it and a bright
     * crescent rim along one side, with motes of darkness orbiting it.
     */
    private static void drawMoon(ServerLevel sl, Vec3 c, float r) {
        double t = sl.getGameTime() * 0.08;
        for (int k = 0; k < 3; k++) {
            double a = t + Math.PI * k / 3;
            FxSend.wall(sl, D, c, new Vec3(Math.cos(a), 0, Math.sin(a)), new Vec3(0, 1, 0), r * 0.92f, r, -180, 180, r * 0.45f, 6, 0, 0);
        }
        FxSend.ring(sl, D, c, r * 0.92f, r, r * 0.45f, 6);
        FxSend.ring(sl, S, c, r * 1.25f, r * 1.3f, 0.03f, 6);   // corona
        FxSend.wall(sl, S, c, new Vec3(Math.cos(t * 0.5), 0, Math.sin(t * 0.5)), new Vec3(0, 1, 0), r * 1.05f, r * 1.08f, -70, 70, 0.06f, 6, 0, 0);   // crescent rim
        for (int k = 0; k < 3; k++) {   // orbiting motes
            double a = t * 2.2 + k * 2.1;
            Vec3 q = c.add(Math.cos(a) * r * 1.7, Math.sin(a * 1.3) * r * 0.6, Math.sin(a) * r * 1.7);
            Vec3 q2 = c.add(Math.cos(a - 0.35) * r * 1.7, Math.sin((a - 0.35) * 1.3) * r * 0.6, Math.sin(a - 0.35) * r * 1.7);
            FxSend.beam(sl, D, q2, q, 0.14f, 5);
        }
    }

    /** A spell swallowed by the moon: a spiral of darkness from where it came, pulled into the moon. */
    private static void swallow(ServerLevel sl, Vec3 moon, Vec3 from) {
        Vec3 d = moon.subtract(from);
        if (d.lengthSqr() < 1.0E-4) return;
        Vec3 sa = rightOf(d.normalize()), sb = sa.cross(d.normalize()).normalize();
        Vec3 prev = from;
        for (int k = 1; k <= 8; k++) {
            double u = k / 8.0, rad = (1 - u) * 0.8;
            Vec3 q = from.add(d.scale(u)).add(sa.scale(Math.cos(u * 9) * rad)).add(sb.scale(Math.sin(u * 9) * rad));
            FxSend.beam(sl, D, prev, q, 0.12f + 0.1f * (float) u, 6);
            prev = q;
        }
        FxLib.implode(sl, D, moon, 0.7f);
    }

    /**
     * The moon breaks: it collapses into a point for a moment, then dives out as two crossed black crescents (one
     * upright, one level) with a dark wake - bigger the more it swallowed.
     * Mana Zone: it splits into one crescent for every enemy in the zone.
     */
    private static void releaseMoon(ServerPlayer p, Moon m) {
        ServerLevel sl = p.serverLevel();
        float dmg = m.power + m.absorbed * 0.5f;
        float grow = 1.0f + Math.min(1.0f, m.absorbed / 30f);
        Vec3 c = moonPos(p);
        FxSend.ring(sl, D, c, 1.6f * grow, 0.1f, 0.5f, 5);   // collapse
        FxLib.implode(sl, S, c, 0.9f * grow);
        sound(sl, c, SoundEvents.BEACON_DEACTIVATE, 0.8f, 0.6f);
        AstaFx.later(4, () -> {
            if (!alive(p)) return;
            Vec3 dir = p.getLookAngle();
            Vec3 rt = rightOf(dir), up = rt.cross(dir).normalize();
            if (m.zone && inZone(p)) {
                for (LivingEntity t : zoneEnemies(p)) {
                    Vec3 to = mid(t).subtract(c).normalize();
                    Vec3 trt = rightOf(to);
                    FxSend.crescent(sl, D, c, to, trt.cross(to).normalize(), 1.0f, 1.8f, (int) Math.min(12, t.distanceTo(p) / 2.4 + 3), 2.4f);
                    FxSend.beam(sl, D, c, mid(t), 0.08f, 6);
                    AstaFx.later((int) (t.distanceTo(p) / 2.4), () -> {
                        if (!t.isAlive() || !alive(p)) return;
                        AstaFx.damage(p, t, dmg * 0.8f, "black_moon");
                        cutMark(sl, mid(t), trt, 1.3f);
                    });
                }
            } else {
                Vec3 end = clip(sl, p, c, c.add(dir.scale(24)));
                for (LivingEntity t : enemies(sl, p, new AABB(c, end).inflate(2.2 * grow))) {
                    if (!t.getBoundingBox().inflate(1.4 * grow).clip(c, end).isPresent()) continue;
                    final LivingEntity tt = t;
                    AstaFx.later((int) (t.distanceTo(p) / 2.4), () -> {
                        if (!tt.isAlive() || !alive(p)) return;
                        AstaFx.damage(p, tt, dmg, "black_moon");
                        FxLib.impact(sl, D, mid(tt), 1.3f);
                    });
                }
                FxSend.crescent(sl, D, c, dir, up, 1.8f * grow, 3.4f * grow, 11, 2.4f);   // upright
                FxSend.crescent(sl, D, c, dir, rt, 1.6f * grow, 3.0f * grow, 11, 2.4f);   // level - together a cross
                AstaFx.later(2, () -> FxSend.crescent(sl, S, c, dir, up, 1.3f * grow, 2.6f * grow, 8, 2.4f));
                FxSend.beam(sl, D, c, end, 0.5f * grow, 10);                               // the dark wake
            }
            FxLib.shockRing(sl, D, c, 0.9f * grow);
            sound(sl, c, SoundEvents.WITHER_SHOOT, 0.8f, 0.6f);
            sound(sl, c, SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 0.5f);
        });
        if (m.absorbed > 0) {
            p.displayClientMessage(Component.translatable("ui.arcane_addon.black_moon_return", (int) m.absorbed).withStyle(ChatFormatting.DARK_PURPLE), true);
        }
    }

    // ================================================================== 7. Black Hole
    /**
     * A hole of darkness with spiralling arms that drags enemies in for 3 s, then implodes.
     * Mana Zone: it opens in the middle of the enemies inside the zone, pulls harder from further away, and lasts 4 s.
     */
    /** Ticks from the cast to the throw in the dark_black_hole animation. */
    private static final int HOLE_RELEASE = 6;

    public static void blackHole(ServerPlayer p, float power) {
        windup(p, HOLE_RELEASE, 0.7f);
        AstaFx.later(HOLE_RELEASE, () -> { if (alive(p)) holeRelease(p, power); });
    }

    private static void holeRelease(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
        Vec3 c;
        boolean z = false;
        List<LivingEntity> zs = inZone(p) ? zoneEnemies(p) : List.of();
        if (!zs.isEmpty()) {
            Vec3 sum = Vec3.ZERO;
            for (LivingEntity t : zs) sum = sum.add(mid(t));
            c = sum.scale(1.0 / zs.size()).add(0, 0.6, 0);
            z = true;
            zoneMsg(p, "black_hole");
        } else {
            Vec3 hit = clip(sl, p, eye, eye.add(look.scale(18)));
            c = hit.subtract(look.scale(0.8)).add(0, 0.6, 0);
        }
        FxLib.implode(sl, D, c, 1.2f);
        sound(sl, c, SoundEvents.PORTAL_TRIGGER, 0.5f, 1.6f);
        holeTick(p, sl, c, power, 0, z);
    }

    private static void holeTick(ServerPlayer p, ServerLevel sl, Vec3 c, float dmg, int age, boolean zone) {
        if (!alive(p)) return;
        final int LIFE = zone ? 80 : 60;
        final double R = zone ? 10 : 7;
        for (LivingEntity t : enemies(sl, p, new AABB(c, c).inflate(R))) {
            Vec3 pull = c.subtract(t.position().add(0, t.getBbHeight() * 0.5, 0));
            double d = pull.length();
            if (d > R || d < 0.3) continue;
            t.setDeltaMovement(t.getDeltaMovement().scale(0.6).add(pull.scale(0.12 / d * Math.min(1.5, R / d))));
            t.hurtMarked = true;
            if (age % 10 == 0) AstaFx.damage(p, t, dmg * 0.25f, "black_hole");
        }
        if (age % 3 == 0) {   // four spiral arms winding inward, turning
            for (int arm = 0; arm < 4; arm++) {
                Vec3 prev = null;
                for (int s = 0; s <= 6; s++) {
                    double rr = R * 0.75 * (1.0 - s / 6.0) + 0.4;
                    double a = arm * Math.PI / 2 + age * 0.22 + s * 0.45;
                    Vec3 q = c.add(Math.cos(a) * rr, -0.1 * (6 - s), Math.sin(a) * rr);
                    if (prev != null) FxSend.beam(sl, s % 2 == 0 ? D : S, prev, q, s % 2 == 0 ? 0.22f : 0.05f, 4);
                    prev = q;
                }
            }
        }
        if (age % 6 == 0) FxSend.ring(sl, D, c.subtract(0, 0.5, 0), (float) R, 0.4f, 0.12f, 8);
        if (age % 5 == 0) drawMoon(sl, c, 0.7f);
        if (age % 20 == 0) sound(sl, c, SoundEvents.BEACON_AMBIENT, 0.8f, 0.5f);
        if (age >= LIFE) {
            for (LivingEntity t : enemies(sl, p, new AABB(c, c).inflate(4.5))) {
                if (t.position().distanceTo(c) > 4.5) continue;
                AstaFx.damage(p, t, dmg * 1.5f, "black_hole");
                t.push(0, 0.6, 0);
                t.hurtMarked = true;
            }
            FxLib.spikeBurst(sl, D, c, 1.4f);
            FxLib.shockRing(sl, D, c, 1.6f);
            FxSend.ring(sl, S, c, 0.3f, 6f, 0.05f, 9);
            FxLib.flash(sl, S, c, 1.2f);
            sound(sl, c, SoundEvents.GENERIC_EXPLODE, 0.9f, 0.6f);
            return;
        }
        AstaFx.later(1, () -> holeTick(p, sl, c, dmg, age + 1, zone));
    }

    // ================================================================== 8. Mana Zone
    /**
     * Spread your mana 14 blocks around you for 20 s. While the zone is up, every Dark Grimoire move becomes its
     * Mana Zone variant. When it ends, the zone collapses and cuts everyone still inside it.
     */
    public static void manaZone(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        ZONE.put(p.getUUID(), sl.getGameTime() + 400);
        ZONE_POWER.put(p.getUUID(), power);
        for (int k = 0; k < 4; k++) {
            final int kk = k;
            AstaFx.later(k * 2, () -> {
                if (alive(p)) FxSend.ring(sl, D, p.position().add(0, 0.05 + kk * 0.02, 0), 0.5f, (float) ZONE_R, 0.25f - kk * 0.05f, 12);
            });
        }
        FxLib.dome(sl, D, p.position(), 1.4f);
        p.displayClientMessage(Component.translatable("ui.arcane_addon.zone.open").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD), true);
        sound(sl, p.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 1f, 0.5f);
        sound(sl, p.position(), SoundEvents.BEACON_POWER_SELECT, 0.8f, 0.6f);
        sound(sl, p.position(), SoundEvents.WARDEN_HEARTBEAT, 1f, 0.6f);
    }
    private static final Map<UUID, Float> ZONE_POWER = new HashMap<>();

    /** Every 10 ticks while the zone is up: its burning edge and slow inner rings, centred on you. */
    /**
     * Every 10 ticks while the zone is up, centred on you:
     *  - you read the ki of every creature inside it (bright marks, like Asta's Ki Sense but much easier to see)
     *  - its edge burns with black flame, and a faint dome of mana arches over it
     *  - a rune circle turns slowly on the ground, and motes of mana drift in toward you
     */
    private static void zoneTick(ServerPlayer p, ServerLevel sl, long now) {
        Vec3 feet = p.position();
        Vec3 c = FxLib.floor(sl, feet);
        float R = (float) ZONE_R;
        // ki: your mana spreads your senses far beyond the zone itself - every creature within 80 blocks
        for (LivingEntity t : sl.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(KI_RANGE), x -> x != p && x.isAlive()
                && !(x instanceof net.minecraft.world.entity.decoration.ArmorStand))) {
            if (t.distanceTo(p) <= KI_RANGE) AstaFx.sendKiMark(p, t, 30, true);
        }
        // the burning edge
        FxSend.ring(sl, D, c, R - 0.15f, R, 0.45f, 12);
        // the dome of mana: thin rings stacked up into a hemisphere, refreshed slowly
        if (now % 20 == 0) {
            for (int k = 1; k <= 4; k++) {
                double el = Math.PI / 2 * k / 5.0;
                float rr = (float) (R * Math.cos(el));
                FxSend.ring(sl, D, c.add(0, R * Math.sin(el) * 0.55, 0), rr - 0.05f, rr, 0.03f, 22);
            }
        }
        // the rune circle on the ground, slowly turning
        double rot = now * 0.02;
        for (int k = 0; k < 8; k++) {
            double a0 = rot + Math.PI * 2 * k / 8, a1 = a0 + Math.PI * 2 / 8;
            Vec3 q0 = c.add(Math.cos(a0) * R * 0.55, 0.02, Math.sin(a0) * R * 0.55), q1 = c.add(Math.cos(a1) * R * 0.55, 0.02, Math.sin(a1) * R * 0.55);
            FxSend.beam(sl, D, q0, q1, 0.12f, 11);
            Vec3 tick0 = c.add(Math.cos(a0) * R * 0.48, 0.02, Math.sin(a0) * R * 0.48), tick1 = c.add(Math.cos(a0) * R * 0.66, 0.02, Math.sin(a0) * R * 0.66);
            FxSend.beam(sl, S, tick0, tick1, 0.05f, 11);
            double b = -rot * 1.6 + Math.PI * 2 * k / 8, b1 = b + Math.PI * 3 / 8;   // an inner star turning the other way
            FxSend.beam(sl, D, c.add(Math.cos(b) * R * 0.3, 0.02, Math.sin(b) * R * 0.3), c.add(Math.cos(b1) * R * 0.3, 0.02, Math.sin(b1) * R * 0.3), 0.06f, 11);
        }
        // motes of mana drifting in toward you
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int k = 0; k < 3; k++) {
            double a = r.nextDouble(Math.PI * 2), dist = r.nextDouble(R * 0.4, R);
            Vec3 from = feet.add(Math.cos(a) * dist, r.nextDouble(0.3, 3.0), Math.sin(a) * dist);
            Vec3 to = from.add(feet.add(0, 1, 0).subtract(from).normalize().scale(1.4));
            FxSend.beam(sl, D, from, to, 0.08f, 8);
        }
        if (now % 40 == 0) FxSend.ring(sl, D, c, 0.4f, R * 0.95f, 0.05f, 16);   // a slow pulse of mana running outward
    }

    private static void zoneCollapse(ServerPlayer p, ServerLevel sl) {
        float dmg = ZONE_POWER.getOrDefault(p.getUUID(), 10f);
        FxSend.ring(sl, D, p.position().add(0, 0.05, 0), (float) ZONE_R, 0.4f, 0.4f, 10);   // the zone folds back into you
        int i = 0;
        for (LivingEntity t : zoneEnemies(p)) {
            final LivingEntity tt = t;
            AstaFx.later(4 + (i++ % 8), () -> {
                if (!tt.isAlive() || !alive(p)) return;
                AstaFx.damage(p, tt, dmg, "mana_zone_dimension_slash");
                cutMark(sl, mid(tt), new Vec3(1, 0.3, 0.4), 1.4f);
            });
        }
        p.displayClientMessage(Component.translatable("ui.arcane_addon.zone.close").withStyle(ChatFormatting.DARK_PURPLE), true);
        sound(sl, p.position(), SoundEvents.BEACON_DEACTIVATE, 1f, 0.5f);
    }

    // ================================================================== 9. Ki Read: Foresight
    /**
     * Read their ki: the next 3 attacks simply miss as you step aside.
     * Mana Zone: you read everyone in the zone - 5 dodges, and every dodge cuts the attacker.
     */
    public static void foresight(ServerPlayer p, float power) {
        Foresight f = new Foresight();
        boolean z = inZone(p);
        f.until = p.serverLevel().getGameTime() + (z ? 160 : 100);
        f.charges = z ? 5 : 3;
        f.counter = z;
        FORESIGHT.put(p.getUUID(), f);
        ServerLevel sl = p.serverLevel();
        FxSend.ring(sl, S, p.position().add(0, 0.05, 0), 0.3f, z ? (float) ZONE_R : 4f, 0.06f, 10);
        Vec3 eye = p.getEyePosition();
        FxSend.wall(sl, S, eye, p.getLookAngle(), rightOf(p.getLookAngle()), 0.5f, 0.55f, -40, 40, 0.04f, 10, 3, 0);   // the eye opens
        FxLib.halo(sl, D, eye.add(0, 0.5, 0), 0.8f);
        if (z) {
            zoneMsg(p, "foresight");
            for (LivingEntity t : zoneEnemies(p)) FxLib.halo(sl, S, t.position().add(0, t.getBbHeight() + 0.2, 0), 0.6f);   // every ki read
        }
        sound(sl, p.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 1f, 0.6f);
    }

    // ================================================================== 10. Dimension Slash: Event Horizon
    /**
     * Darkness gathers into the blade and swells around you, then one cut releases a colossal wave of darkness: a
     * towering black-flame front with giant crescents inside it, rolling 55 blocks forward and widening as it goes.
     * It leaves the horizon hanging open behind it, pulling enemies back in and cutting them again before it seals.
     * Mana Zone: the wave is 1.7x bigger, faster, and reaches 90 blocks.
     */
    public static void eventHorizon(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        final float dmg = power;
        final boolean zone = inZone(p);
        sound(sl, p.position(), SoundEvents.WARDEN_SONIC_CHARGE, 1f, 0.6f);
        for (int k = 0; k < 5; k++) {   // darkness gathers into the blade: rings closing in, crescents converging on the hand
            final int kk = k;
            AstaFx.later(k * 4, () -> {
                if (!alive(p)) return;
                FxSend.ring(sl, D, p.position().add(0, 0.05 + kk * 0.35, 0), 5f - kk * 0.6f, 0.3f, 0.1f, 6);
                Vec3 h = handPos(p);
                double a = kk * 1.3;
                Vec3 from = h.add(Math.cos(a) * 3, 1.0 - kk * 0.3, Math.sin(a) * 3);
                Vec3 d = h.subtract(from).normalize();
                FxSend.crescent(sl, D, from, d, rightOf(d).cross(d).normalize(), 0.6f, 0.4f, 4, 0.7f);
            });
        }
        if (zone) zoneMsg(p, "event_horizon");
        // the darkness swells around you as it gathers: black flame climbing your body
        for (int k = 0; k < 4; k++) {
            final int kk = k;
            AstaFx.later(4 + k * 4, () -> {
                if (!alive(p)) return;
                FxSend.wall(sl, D, p.position().add(0, 1.0, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1), 0.9f + kk * 0.15f, 1.0f + kk * 0.2f, -180, 180, 1.1f + kk * 0.2f, 5, 0, 0);
            });
        }
        AstaFx.later(20, () -> {
            if (!alive(p)) return;
            // aimed where you look; when you look roughly level it hugs the ground instead, rising out of it
            Vec3 look0 = p.getLookAngle();
            final boolean aimed = Math.abs(look0.y) > 0.25;
            Vec3 flatDir = new Vec3(look0.x, 0, look0.z).lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : new Vec3(look0.x, 0, look0.z).normalize();
            Vec3 dir = aimed ? look0.normalize() : flatDir;
            Vec3 start = aimed ? p.getEyePosition().add(0, -0.3, 0) : FxLib.floor(sl, p.position());
            Vec3 rt = rightOf(dir), up = aimed ? rt.cross(dir).normalize() : new Vec3(0, 1, 0);
            // zone: everything bigger and it reaches much further
            final double reach = zone ? 90 : 55;
            final float scale = zone ? 1.7f : 1.0f;
            final float speed = zone ? 4.2f : 3.2f;
            Vec3 end = start.add(dir.scale(reach));
            // the colossal wave cuts everything in a widening, towering band as it passes
            for (LivingEntity t : enemies(sl, p, new AABB(start, end).inflate(10 * scale))) {
                Vec3 m = mid(t), on = closestOnSegment(start, end, m);
                Vec3 off = m.subtract(on);
                double along = on.distanceTo(start);
                if (Math.abs(off.dot(rt)) > (3.0 + along * 0.14) * scale || Math.abs(off.dot(up)) > 6.0 * scale) continue;
                final LivingEntity tt = t;
                AstaFx.later((int) (along / speed), () -> {
                    if (!tt.isAlive() || !alive(p)) return;
                    AstaFx.damage(p, tt, dmg, "event_horizon");
                    FxLib.impact(sl, D, mid(tt), 1.5f);
                });
            }
            // the cut: a giant vertical arc in front of you
            FxSend.wall(sl, D, aimed ? start : start.add(0, 1.2, 0), dir, up, 4.0f * scale, 4.6f * scale, 85, -85, 1.2f * scale, 10, 3, 0);
            // the release: a towering black wave of darkness - a tall arc front, a colossal vertical crescent inside it,
            // more crescents layered behind it, all growing as they roll forward
            Vec3 o = start.add(dir.scale(1.5));
            int life = (int) (reach / speed) + 2;
            FxSend.wall(sl, D, o, dir, rt, 3.0f * scale, 14f * scale, -60, 60, 5.5f * scale, life, 0, speed);
            FxSend.crescent(sl, D, o, dir, up, 5.0f * scale, 15f * scale, life, speed);
            FxSend.crescent(sl, D, o, dir, rt, 4.0f * scale, 12f * scale, life, speed);
            for (int k = 1; k <= 4; k++) {
                final int kk = k;
                AstaFx.later(k, () -> {
                    Vec3 lift = rt.scale((kk % 2 == 0 ? 1 : -1) * 0.8 * kk * scale);
                    FxSend.crescent(sl, kk == 2 ? S : D, o.add(lift), dir, up, (4.4f - kk * 0.4f) * scale, (13f - kk * 1.5f) * scale, life - kk, speed);
                });
            }
            // in its wake: the horizon - a line through space left hanging open with tall tears along it
            AstaFx.later(4, () -> {
                // the horizon: a burning scar along the ground, with tall tears rising out of it wherever the ground is
                Vec3 prev = start.add(dir);
                for (double k = 4; k < reach; k += 4) {
                    Vec3 c = aimed ? start.add(dir.scale(k)) : FxLib.floor(sl, start.add(dir.scale(k)));
                    FxSend.beam(sl, D, prev, c, 1.0f * scale, 34);
                    FxSend.beam(sl, S, prev, c, 0.08f * scale, 30);
                    double h = (2.0 + k * 0.05) * scale;
                    if (((int) k) % 8 == 0) FxSend.beam(sl, D, c, c.add(up.scale(h * 2)), 0.3f * scale, 24);
                    prev = c;
                }
            });
            FxLib.shockRing(sl, D, p.position().add(0, 0.05, 0), 1.8f * scale);
            sound(sl, p.position(), SoundEvents.WARDEN_SONIC_BOOM, 1f, 0.5f);
            sound(sl, p.position(), SoundEvents.GENERIC_EXPLODE, 0.8f, 0.5f);
            // the horizon keeps pulling and cutting, then seals with a flash
            for (int k = 1; k <= 3; k++) {
                AstaFx.later(10 + k * 8, () -> {
                    if (!alive(p)) return;
                    for (LivingEntity t : enemies(sl, p, new AABB(start, end).inflate(5 * scale))) {
                        Vec3 onLine = closestOnSegment(start, end, mid(t));
                        Vec3 pull = onLine.subtract(mid(t));
                        if (pull.length() > 5 * scale) continue;
                        if (pull.lengthSqr() > 1.0E-4) t.setDeltaMovement(t.getDeltaMovement().add(pull.normalize().scale(0.35)));
                        t.hurtMarked = true;
                        AstaFx.damage(p, t, dmg * 0.25f, "event_horizon");
                    }
                    sound(sl, start.add(dir.scale(10)), SoundEvents.PLAYER_ATTACK_SWEEP, 0.9f, 0.4f);
                });
            }
            AstaFx.later(40, () -> {
                FxSend.beam(sl, S, start.add(dir).add(0, 0.2, 0), end.add(0, 0.2, 0), 0.9f * scale, 4);
                sound(sl, start, SoundEvents.ENDERMAN_TELEPORT, 0.8f, 0.4f);
            });
        });
    }

    private static Vec3 closestOnSegment(Vec3 a, Vec3 b, Vec3 q) {
        Vec3 ab = b.subtract(a);
        double t = Math.max(0, Math.min(1, q.subtract(a).dot(ab) / ab.lengthSqr()));
        return a.add(ab.scale(t));
    }

    // ================================================================== events
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        ServerLevel sl = p.serverLevel();
        long now = sl.getGameTime();
        UUID id = p.getUUID();

        Long blade = BLADE.get(id);
        if (blade != null) {
            if (now > blade) { BLADE.remove(id); sendAura(p, 0); }
            else {
                if (now % 40 == 0) sendAura(p, (int) (blade - now));   // keep it showing for anyone who arrives later
                if (now % 8 == 0) {   // wisps of darkness rising off the aura
                    ThreadLocalRandom r = ThreadLocalRandom.current();
                    double a = r.nextDouble(Math.PI * 2);
                    Vec3 w0 = p.position().add(Math.cos(a) * 0.45, r.nextDouble(0.2, 1.5), Math.sin(a) * 0.45);
                    FxSend.trail(sl, D, w0, w0.add(Math.cos(a) * 0.15, 0.6, Math.sin(a) * 0.15), 0.08f, 6);
                }
            }
        }

        Moon m = MOON.get(id);
        if (m != null) {
            if (now >= m.until) { MOON.remove(id); releaseMoon(p, m); }
            else if (now % 5 == 0) drawMoon(sl, moonPos(p), 0.9f);
        }

        Long z = ZONE.get(id);
        if (z != null) {
            if (now > z) { zoneCollapse(p, sl); ZONE.remove(id); ZONE_POWER.remove(id); }
            else if (now % 10 == 0) zoneTick(p, sl, now);
        }

        Cocoon cc = COCOON.get(id);
        if (cc != null) {
            if (now >= cc.until) { COCOON.remove(id); cocoonBurst(p, sl, cc); }
            else cocoonTick(p, sl, cc, now);
        }

        Foresight f = FORESIGHT.get(id);
        if (f != null && (now > f.until || f.charges <= 0)) FORESIGHT.remove(id);
    }

    /** Black Blade: heavier melee hits that devour the target's mana. */
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent e) {
        if (!(e.getSource().getEntity() instanceof ServerPlayer p) || e.getSource().getDirectEntity() != p) return;
        if (e.getSource() instanceof SpellDamageSource) return;
        Long blade = BLADE.get(p.getUUID());
        if (blade == null || p.serverLevel().getGameTime() > blade) return;
        e.setAmount(e.getAmount() + 6f);
        LivingEntity t = e.getEntity();
        if (t instanceof ServerPlayer victim) {
            MagicData data = MagicData.getPlayerMagicData(victim);
            data.setMana(Math.max(0, data.getMana() - 20));   // darkness swallows magic
        }
        cutMark(p.serverLevel(), mid(t), rightOf(p.getLookAngle()).add(0, 0.5, 0), 0.9f);
        if (inZone(p)) {   // Mana Zone: the cut jumps to the next enemy in the zone
            for (LivingEntity o : zoneEnemies(p)) {
                if (o == t) continue;
                AstaFx.damage(p, o, 6f, "black_blade");
                cutMark(p.serverLevel(), mid(o), new Vec3(1, -0.4, 0.3), 1.1f);
                break;
            }
        }
    }

    /** Black Moon swallows spells; Foresight lets attacks miss. */
    @SubscribeEvent
    public static void onAttacked(LivingAttackEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        if (e.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        ServerLevel sl = p.serverLevel();
        long now = sl.getGameTime();

        Cocoon cc = COCOON.get(p.getUUID());
        if (cc != null && now >= cc.start + COCOON_CLOSE / 2 && now < cc.until && !e.getSource().is(DamageTypeTags.IS_FALL)) {
            e.setCanceled(true);   // the shell swallows it
            cc.absorbed = Math.min(30f, cc.absorbed + e.getAmount());
            Vec3 src0 = e.getSource().getSourcePosition();
            Vec3 ctr = p.position().add(0, 1.0, 0);
            Vec3 hit = src0 == null ? ctr.add(p.getLookAngle().scale(COCOON_R)) : ctr.add(src0.subtract(ctr).normalize().scale(COCOON_R));
            Vec3 n = hit.subtract(ctr).normalize();
            FxSend.wall(sl, D, hit, rightOf(n), rightOf(n).cross(n).normalize(), 0.1f, 0.9f, -180, 180, 0.05f, 6, 0, 0);
            sound(sl, hit, SoundEvents.SCULK_CLICKING, 1f, 0.5f);
            return;
        }

        Moon m = MOON.get(p.getUUID());
        if (m != null && now < m.until && e.getSource() instanceof SpellDamageSource) {
            e.setCanceled(true);
            m.absorbed = Math.min(40f, m.absorbed + e.getAmount());
            Vec3 src0 = e.getSource().getSourcePosition();
            swallow(sl, moonPos(p), src0 != null ? src0 : p.getEyePosition().add(p.getLookAngle().scale(3)));
            sound(sl, p.position(), SoundEvents.SCULK_CLICKING, 1f, 0.6f);
            return;
        }

        Foresight f = FORESIGHT.get(p.getUUID());
        Entity src = e.getSource().getEntity();
        if (f != null && now <= f.until && f.charges > 0 && src != null && src != p) {
            e.setCanceled(true);
            f.charges--;
            Vec3 away = p.position().subtract(src.position()).multiply(1, 0, 1);
            Vec3 side = rightOf(away.lengthSqr() < 1.0E-4 ? p.getLookAngle() : away.normalize());
            for (int s = 1; s >= -1; s -= 2) {   // step left or right, whichever is free
                Vec3 dest = p.position().add(side.scale(1.4 * s));
                if (sl.noCollision(p, p.getBoundingBox().move(dest.subtract(p.position())))) {
                    FxSend.beam(sl, S, p.position().add(0, 1, 0), dest.add(0, 1, 0), 0.2f, 6);   // afterimage
                    p.connection.teleport(dest.x, dest.y, dest.z, p.getYRot(), p.getXRot());
                    break;
                }
            }
            AnimSend.play(p, AnimSend.DEFLECT);   // the blade turns the blow aside as you step
            if (f.counter && src instanceof LivingEntity le && le.isAlive()) {   // Mana Zone: every read attack is answered with a cut
                AstaFx.later(3, () -> {
                    if (!le.isAlive() || !alive(p)) return;
                    AstaFx.damage(p, le, 8f, "ki_foresight");
                    cutMark(sl, mid(le), rightOf(p.getLookAngle()), 1.2f);
                });
            }
            sound(sl, p.position(), SoundEvents.ENDERMAN_TELEPORT, 0.5f, 1.8f);
        }
    }

    /** Passive: Surpass Your Limits - falling below 25% health resets every cooldown (once per 90 s). */
    @SubscribeEvent
    public static void onDamaged(LivingDamageEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !wearsDarkGrimoire(p)) return;
        float after = p.getHealth() - e.getAmount();
        if (after <= 0 || after > p.getMaxHealth() * 0.25f || p.getHealth() <= p.getMaxHealth() * 0.25f) return;
        long now = p.serverLevel().getGameTime();
        Long used = LIMITS_USED.get(p.getUUID());
        if (used != null && now - used < 90 * 20) return;
        LIMITS_USED.put(p.getUUID(), now);
        AstaFx.later(1, () -> { if (p.isAlive()) p.heal(p.getMaxHealth() * 0.25f); });   // and heal a quarter of your health
        MagicData data = MagicData.getPlayerMagicData(p);
        data.getPlayerCooldowns().clearCooldowns();
        data.getPlayerCooldowns().syncToPlayer(p);
        ServerLevel sl = p.serverLevel();
        FxSend.ring(sl, D, p.position().add(0, 0.05, 0), 0.3f, 5f, 0.3f, 12);
        FxSend.wall(sl, D, p.position().add(0, 1, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1), 0.7f, 1.0f, -180, 180, 1.4f, 12, 0, 0);
        sound(sl, p.position(), SoundEvents.TOTEM_USE, 0.4f, 0.6f);
        p.displayClientMessage(Component.translatable("ui.arcane_addon.surpass_limits").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD), true);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        UUID id = e.getEntity().getUUID();
        BLADE.remove(id); MOON.remove(id); COCOON.remove(id); ZONE.remove(id); ZONE_POWER.remove(id); FORESIGHT.remove(id); LIMITS_USED.remove(id);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent e) {
        BLADE.clear(); MOON.clear(); COCOON.clear(); ZONE.clear(); ZONE_POWER.clear(); FORESIGHT.clear(); LIMITS_USED.clear();
    }
}
