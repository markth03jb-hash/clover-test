package com.example.arcaneaddon.server;

import static com.example.arcaneaddon.server.FxUtil.*;
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
    private static final int D = Fx.YAMI, S = 1;   // accents in dark violet (they used to be white)

    // ------------------------------------------------------------------ state
    private static final Map<UUID, Long> BLADE = new HashMap<>();          // Black Blade active until
    private static final Map<UUID, Moon> MOON = new HashMap<>();           // Black Moon
    private static final Map<UUID, Long> ZONE = new HashMap<>();           // Mana Zone: Dimension Slash until
    private static final Map<UUID, Foresight> FORESIGHT = new HashMap<>();
    private static final Map<UUID, Long> LIMITS_USED = new HashMap<>();

    private static final class Moon { long until; float absorbed; float power; boolean zone; }
    private static final class Foresight { long until; int charges; boolean counter; }

    // ------------------------------------------------------------------ helpers

    private static List<LivingEntity> enemies(ServerLevel sl, ServerPlayer p, AABB box) {
        return sl.getEntitiesOfClass(LivingEntity.class, box, x -> x != p && x.isAlive() && !x.isAlliedTo(p));
    }

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
        return z != null && p.serverLevel().getGameTime() <= z && !ZoneClash.suppressed(p);   // a stronger zone over you smothers yours
    }

    /** When this player's Mana Zone ends (game time), or -1 if they have none. Ignores clashes. */
    public static long zoneEnd(ServerPlayer p) {
        Long z = ZONE.get(p.getUUID());
        return z == null || p.serverLevel().getGameTime() > z ? -1 : z;
    }

    public static final double ZONE_RADIUS = ZONE_R;

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

    /** Black Blade's aura: small violet motes rising off your body (client DarkSmoke), for everyone who can see you. */
    private static void sendAura(ServerPlayer p, int life) {
        ModeHud.show(p, "black_blade", life);
        com.example.arcaneaddon.network.FxPacket pkt = new com.example.arcaneaddon.network.FxPacket();
        pkt.shape = 16; pkt.id = p.getId(); pkt.palette = 0; pkt.life = life;
        com.example.arcaneaddon.network.AddonNetwork.send(net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), pkt);
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
        sound(sl, p.position(), net.minecraft.sounds.SoundEvents.SOUL_ESCAPE, 0.5f, 0.8f);
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
        AstaFx.later(10, () -> {   // the blade raised high: darkness coats it, and violet motes start rising off you
            if (!alive(p)) return;
            sendAura(p, 20 * 20 - 10);
            Vec3 h = handPos(p).add(0, 0.6, 0), d = p.getLookAngle();
            FxSend.beam(sl, D, h, h.add(d.scale(0.3)).add(0, 1.2, 0), 0.12f, 6);   // a breath of darkness along the blade
            FxSend.beam(sl, S, h, h.add(d.scale(0.3)).add(0, 1.2, 0), 0.03f, 4);   // one glint along the edge as the coat settles
            sound(sl, p.position(), net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_NETHERITE, 0.8f, 0.6f);
        });
        if (inZone(p)) zoneMsg(p, "black_blade");
        sound(sl, p.position(), net.minecraft.sounds.SoundEvents.SOUL_ESCAPE, 1f, 0.6f);
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
            sound(sl, p.position(), net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_NETHERITE, 0.7f, 1.6f);
        });
        sound(sl, p.position(), net.minecraft.sounds.SoundEvents.SOUL_ESCAPE, 0.8f, 0.5f);
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
                    sound(sl, tt.position(), net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_CRIT, 1f, 0.6f);
                });
            }
            // the draw and the release: one clean, solid slash - a black blade of darkness with a white-hot edge,
            // drawn left to right as you cut, flying forward and growing as it goes
            Vec3 o = start.add(dir.scale(1.2));
            FxSend.slash(sl, D, start.add(dir.scale(0.8)), dir, rt, 2.8f, 3.4f, 6, 0f, 1.0f);   // the swing itself, at your blade
            FxSend.slash(sl, D, o, dir, rt, 4.5f, 13.0f, 18, SPEED, 1.15f);                      // the slash that flies: big, blade-thin
            // after it passes: one clean white hairline left through space, fading
            AstaFx.later(3, () -> {
                FxSend.trail(sl, S, o, end, 0.03f, 14);
                sound(sl, p.position(), net.minecraft.sounds.SoundEvents.WITHER_SHOOT, 0.5f, 0.4f);
            });
            FxLib.shockRing(sl, D, p.position().add(0, 0.05, 0), 1.2f);
            sound(sl, p.position(), net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_SWEEP, 1.3f, 0.4f);
            sound(sl, p.position(), net.minecraft.sounds.SoundEvents.WARDEN_SONIC_BOOM, 0.5f, 0.7f);
            sound(sl, p.position(), net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT, 0.7f, 0.5f);
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
        sound(sl, p.position(), net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_SWEEP, 1.3f, 0.4f);
        sound(sl, p.position(), net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT, 0.7f, 0.4f);
    }

    // ================================================================== 3. Avidya Slash Raijin (ki-guided)
    /**
     * A ki-guided crescent that bends toward the enemy you look at.
     * Mana Zone: Raijin - three crescents tear out of the zone's edge around the target and converge on it.
     */
    /** Ticks from the cast to the upward cut in the dark_avidya animation (low crouch, then the rising draw). */
    private static final int AVIDYA_RELEASE = 7;

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
                sound(sl, p.position(), net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 1.5f);
                return;
            }
        }
        guidedSlash(p, sl, handPos(p), look, target, power, 0, new HashSet<>());
        Vec3 rt = rightOf(look);
        FxSend.wall(sl, D, handPos(p), look, rt.scale(-0.5).add(0, 0.87, 0), 1.2f, 1.6f, -80, 80, 0.25f, 6, 2, 0);   // the upward draw
        sound(sl, p.position(), net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 1.3f);
        sound(sl, p.position(), net.minecraft.sounds.SoundEvents.TRIDENT_RIPTIDE_1, 0.6f, 1.5f);
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
            sound(sl, p.position(), net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_NETHERITE, 0.9f, 0.4f);
            sound(sl, p.position(), net.minecraft.sounds.SoundEvents.SCULK_SHRIEKER_SHRIEK, 0.25f, 0.5f);
        });
        if (c.zone) zoneMsg(p, "black_cocoon");
        sound(sl, p.position(), net.minecraft.sounds.SoundEvents.SOUL_ESCAPE, 1f, 0.4f);
    }

    private static void sendCocoon(ServerPlayer p, int life) {
        ModeHud.show(p, "black_cocoon", life);
        com.example.arcaneaddon.network.FxPacket pkt = new com.example.arcaneaddon.network.FxPacket();
        pkt.shape = 15; pkt.id = p.getId(); pkt.palette = (byte) D; pkt.life = life; pkt.r = COCOON_R; pkt.sweep = COCOON_CLOSE;
        com.example.arcaneaddon.network.AddonNetwork.send(net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), pkt);
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
        sound(sl, p.position(), net.minecraft.sounds.SoundEvents.GLASS_BREAK, 1f, 0.4f);
        sound(sl, p.position(), net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_SWEEP, 1.3f, 0.5f);
        sound(sl, p.position(), net.minecraft.sounds.SoundEvents.WARDEN_SONIC_BOOM, 0.3f, 1.2f);
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
            sound(sl, t.position(), net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_CRIT, 1f, 0.8f);
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
    private static final int THRUST_RELEASE = 10;

    /**
     * Equinox on a player: their screen is cut in half (FxPacket shape 43 to that player only: life = ticks until the
     * blow lands, sweep = the angle of the cut in degrees) - a thin line across it, then the two halves slide apart.
     */
    private static void screenCut(ServerPlayer victim, int ticks) {
        com.example.arcaneaddon.network.FxPacket pkt = new com.example.arcaneaddon.network.FxPacket();
        pkt.shape = 43; pkt.life = ticks;
        pkt.sweep = (java.util.concurrent.ThreadLocalRandom.current().nextBoolean() ? 1 : -1) * java.util.concurrent.ThreadLocalRandom.current().nextInt(20, 60);
        com.example.arcaneaddon.network.AddonNetwork.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> victim), pkt);
    }

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
                        sound(sl, t.position(), net.minecraft.sounds.SoundEvents.TRIDENT_RIPTIDE_3, 0.7f, 0.9f);
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
        sound(sl, p.position(), net.minecraft.sounds.SoundEvents.TRIDENT_RIPTIDE_3, 0.8f, 0.8f);
        sound(sl, p.position(), net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_STRONG, 1.2f, 0.5f);
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
        sound(p.serverLevel(), p.position(), net.minecraft.sounds.SoundEvents.SOUL_ESCAPE, 1.0f, 0.5f);
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
        sound(sl, c, net.minecraft.sounds.SoundEvents.BEACON_DEACTIVATE, 0.8f, 0.6f);
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
            sound(sl, c, net.minecraft.sounds.SoundEvents.WITHER_SHOOT, 0.8f, 0.6f);
            sound(sl, c, net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 0.5f);
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
    private static final int HOLE_RELEASE = 8;

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
        sound(sl, c, net.minecraft.sounds.SoundEvents.PORTAL_TRIGGER, 0.5f, 1.6f);
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
        if (age % 20 == 0) sound(sl, c, net.minecraft.sounds.SoundEvents.BEACON_AMBIENT, 0.8f, 0.5f);
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
            sound(sl, c, net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE, 0.9f, 0.6f);
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
        sendZone(p, 400);   // the dark mana pours out of you and fills the zone (drawn by the client: FxRenderer zone field)
        p.displayClientMessage(Component.translatable("ui.arcane_addon.zone.open").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD), true);
        sound(sl, p.position(), net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_RESONATE, 1f, 0.5f);
        sound(sl, p.position(), net.minecraft.sounds.SoundEvents.BEACON_POWER_SELECT, 0.8f, 0.6f);
        sound(sl, p.position(), net.minecraft.sounds.SoundEvents.WARDEN_HEARTBEAT, 1f, 0.6f);
    }
    private static final Map<UUID, Float> ZONE_POWER = new HashMap<>();

    // ------------------------------------------------------------------ Event Horizon in the Mana Zone: Equinox
    private static final int EQUINOX_CUT = 61;   // the cut lands with the cast animation, like Event Horizon's

    /** Cinematic for everyone close (FxPacket shape 40): letterbox bars, a slow push-in, the split frame at the cut. */
    private static void equinoxCinematic(ServerPlayer p, ServerLevel sl, int life) {
        com.example.arcaneaddon.network.FxPacket pkt = new com.example.arcaneaddon.network.FxPacket();
        pkt.shape = 40; pkt.id = p.getId(); pkt.life = life; pkt.sweep = EQUINOX_CUT; pkt.c = p.position();
        Vec3 c = p.position();
        com.example.arcaneaddon.network.AddonNetwork.send(net.minecraftforge.network.PacketDistributor.NEAR.with(
                () -> new net.minecraftforge.network.PacketDistributor.TargetPoint(c.x, c.y, c.z, ZONE_R + 16, sl.dimension())), pkt);
    }

    /**
     * Mana Zone: Dark Cloaked Dimension Slash - Equinox: Event Horizon cast inside the Mana Zone. Played like a scene
     * from the anime: black bars close in, the view pushes in slowly, and the moment stretches - Yami draws the
     * katana level across himself while everyone the zone has read is held still, the darkness of the zone drawn
     * into the blade. Then the world splits into light and dark and one cut divides the zone: a colossal slash stands
     * across it, every enemy inside is cut at the same instant, a ring of darkness sweeps the edge and a pale
     * dividing line hangs across the ground. (No cracks in space here - it is one clean cut.)
     */
    private static void equinox(ServerPlayer p, ServerLevel sl, float power) {
        zoneMsg(p, "equinox");
        AnimSend.play(p, AnimSend.EQUINOX);
        equinoxCinematic(p, sl, EQUINOX_CUT + 26);
        TimedModifiers.apply(p, net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED, TimedModifiers.HORIZON_CHARGE,
                "Equinox", -0.9, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_TOTAL, EQUINOX_CUT + 2);
        final Vec3 c0 = p.position();
        final float dmg = power * 1.8f;
        for (int t = 0; t < EQUINOX_CUT; t++) {
            final int tt = t;
            AstaFx.later(t, () -> {
                if (!alive(p)) return;
                // the moment stretches: everyone the zone holds is frozen where they stand
                if (tt > 8) for (LivingEntity e : zoneEnemies(p)) { e.setDeltaMovement(Vec3.ZERO); e.hurtMarked = true; }
                if (tt % 2 != 0) return;
                double yr = Math.toRadians(p.getYRot());
                Vec3 fw = new Vec3(-Math.sin(yr), 0, Math.cos(yr)), rt = new Vec3(-Math.cos(yr), 0, -Math.sin(yr));
                float k = tt / (float) EQUINOX_CUT;
                // the level blade: darkness gathering on it, a white edge growing
                Vec3 hilt = p.position().add(0, 1.3, 0).add(fw.scale(0.5)).subtract(rt.scale(0.45));
                Vec3 tip = hilt.add(rt.scale(1.3 + k * 0.9));
                FxSend.trail(sl, D, hilt, tip, 0.25f + k * 0.45f, 3);
                FxSend.trail(sl, S, hilt, tip, 0.02f + k * 0.03f, 3);
                // the zone's darkness drawn in from its edge toward the blade
                if (tt % 4 == 0) {
                    double a = tt * 0.7;
                    Vec3 from = p.position().add(Math.cos(a) * ZONE_R * 0.9, 1.2 + Math.sin(tt) * 1.5, Math.sin(a) * ZONE_R * 0.9);
                    Vec3 d = tip.subtract(from).normalize();
                    FxSend.crescent(sl, D, from, d, rightOf(d).cross(d).normalize(), 0.7f, 0.4f, 6, (float) (from.distanceTo(tip) / 6));
                }
                if (tt % 10 == 0) FxSend.ring(sl, D, p.position().add(0, 0.06, 0), (float) ZONE_R * (1 - k), 0.3f, 0.06f, 8);
                if (tt % 20 == 0) sound(sl, p.position(), net.minecraft.sounds.SoundEvents.WARDEN_HEARTBEAT, 1.2f, 0.5f);
            });
        }
        sound(sl, c0, net.minecraft.sounds.SoundEvents.WARDEN_SONIC_CHARGE, 1f, 0.4f);
        AstaFx.later(EQUINOX_CUT - 2, () -> {   // light and dark, split in two - when there is someone in the zone to cut
            if (alive(p) && !zoneEnemies(p).isEmpty()) FxSend.impactFrame(sl, p.position(), 6, FxSend.FRAME_EQUINOX, ZONE_R + 20);
        });
        AstaFx.later(EQUINOX_CUT, () -> {
            TimedModifiers.remove(p, net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED, TimedModifiers.HORIZON_CHARGE);
            if (!alive(p)) return;
            // aimed: straight along your view (at the enemy under the crosshair if there is one), and it goes far -
            // 140 blocks, further than Event Horizon ever reaches
            final double reach = 140, band = 3.2;
            Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
            net.minecraft.world.phys.EntityHitResult ehr = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(
                    p, eye, eye.add(look.scale(reach)), p.getBoundingBox().expandTowards(look.scale(reach)).inflate(1.5),
                    x -> x instanceof LivingEntity le && le.isAlive() && !x.isAlliedTo(p) && x != p, reach * reach);
            if (ehr != null && ehr.getEntity() instanceof LivingEntity tg && p.hasLineOfSight(tg)) look = mid(tg).subtract(eye).normalize();
            final Vec3 dir = look, start = eye, end = eye.add(look.scale(reach));
            final Vec3 rt = rightOf(dir), up = rt.cross(dir).normalize();
            FxSend.shake(sl, p.position(), 1.0f, 16, 48);
            // the cut itself is invisible: only a hairline of light along the line and a faint ripple at the blade
            FxSend.trail(sl, S, start.add(dir.scale(1.5)), end, 0.015f, 6);
            FxSend.ring(sl, S, start.add(dir.scale(1.6)), 0.2f, 1.6f, 0.03f, 5);
            // ...and a faint trace of it that lingers a moment: a thin drift of black and violet along the whole line
            FxSend.beam(sl, D, start.add(dir.scale(1.5)), end, 0.12f, 14);
            sound(sl, p.position(), net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_SWEEP, 1.4f, 1.8f);
            // everything on the line is cut at the same instant - and a moment later it comes apart
            for (LivingEntity e : enemies(sl, p, new AABB(start, end).inflate(band + 2))) {
                Vec3 m = mid(e), on = closestOnSegment(start, end, m);
                if (on.distanceTo(m) > band + e.getBbWidth() * 0.5) continue;
                final LivingEntity tt = e;
                // the instant: a razor-thin white line straight through the body, nothing else
                FxSend.trail(sl, S, m.subtract(rt.scale(1.4)), m.add(rt.scale(1.4)), 0.02f, 8);
                tt.setDeltaMovement(Vec3.ZERO);
                tt.hurtMarked = true;
                if (tt instanceof ServerPlayer victim) screenCut(victim, 8);   // a player sees their own screen cut in two first
                AstaFx.later(8, () -> {   // ...then it is cut in half: the line opens, darkness spills out both ways
                    if (!tt.isAlive() || !alive(p)) return;
                    Vec3 mm = mid(tt);
                    float w = Math.max(0.8f, tt.getBbWidth() * 1.3f);
                    FxSend.trail(sl, S, mm.subtract(rt.scale(w * 1.3)), mm.add(rt.scale(w * 1.3)), 0.05f, 6);
                    FxSend.wall(sl, D, mm, up, rt, 0.1f, w * 1.4f, -180, 180, 0.05f, 8, 0, 0);   // the dividing plane
                    FxLib.flash(sl, D, mm.add(up.scale(0.4)), 0.6f);
                    FxLib.flash(sl, D, mm.subtract(up.scale(0.4)), 0.6f);
                    AstaFx.damage(p, tt, dmg, "event_horizon");
                    tt.setDeltaMovement(up.scale(0.25));
                    tt.hurtMarked = true;
                    sound(sl, mm, net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_CRIT, 1f, 0.5f);
                });
            }
            sound(sl, p.position(), net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT, 0.8f, 0.4f);
            AstaFx.later(8, () -> sound(sl, p.position(), net.minecraft.sounds.SoundEvents.WARDEN_SONIC_BOOM, 1.0f, 0.5f));
        });
    }


    /**
     * Every 10 ticks while the zone is up, centred on you:
     *  - you read the ki of every creature inside it (bright marks, like Asta's Ki Sense but much easier to see)
     *  - the field itself (a sea of dark mana over the ground, a faint dome, smoke swirling around you) is drawn by
     *    the client from one packet, so it moves smoothly with you
     */
    private static void zoneTick(ServerPlayer p, ServerLevel sl, long now) {
        // ki: your mana spreads your senses far beyond the zone itself - every creature within 80 blocks
        for (LivingEntity t : sl.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(KI_RANGE), x -> x != p && x.isAlive()
                && !(x instanceof net.minecraft.world.entity.decoration.ArmorStand))) {
            if (t.distanceTo(p) <= KI_RANGE) AstaFx.sendKiMark(p, t, 30, true);
        }
        // keep the field showing for anyone who walks up later
        Long z = ZONE.get(p.getUUID());
        if (now % 40 == 0 && z != null) sendZone(p, (int) (z - now));
    }

    /** Mana Zone field (FxPacket shape 17): id = player, life = ticks left (0 = collapse), r = radius. */
    private static void sendZone(ServerPlayer p, int life) {
        ModeHud.show(p, "mana_zone_dimension_slash", life);
        com.example.arcaneaddon.network.FxPacket pkt = new com.example.arcaneaddon.network.FxPacket();
        pkt.shape = 17; pkt.id = p.getId(); pkt.palette = (byte) D; pkt.life = life; pkt.r = (float) ZoneClash.radius(p, ZONE_R);
        com.example.arcaneaddon.network.AddonNetwork.send(net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), pkt);
    }

    private static void zoneCollapse(ServerPlayer p, ServerLevel sl) {
        float dmg = ZONE_POWER.getOrDefault(p.getUUID(), 10f);
        sendZone(p, 0);   // the darkness folds back into you
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
        sound(sl, p.position(), net.minecraft.sounds.SoundEvents.BEACON_DEACTIVATE, 1f, 0.5f);
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
        ModeHud.show(p, "ki_foresight", z ? 160 : 100);
        ServerLevel sl = p.serverLevel();
        FxSend.ring(sl, S, p.position().add(0, 0.05, 0), 0.3f, z ? (float) ZONE_R : 4f, 0.06f, 10);
        Vec3 eye = p.getEyePosition();
        FxSend.wall(sl, S, eye, p.getLookAngle(), rightOf(p.getLookAngle()), 0.5f, 0.55f, -40, 40, 0.04f, 10, 3, 0);   // the eye opens
        FxLib.halo(sl, D, eye.add(0, 0.5, 0), 0.8f);
        if (z) {
            zoneMsg(p, "foresight");
            for (LivingEntity t : zoneEnemies(p)) FxLib.halo(sl, S, t.position().add(0, t.getBbHeight() + 0.2, 0), 0.6f);   // every ki read
        }
        sound(sl, p.position(), net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME, 1f, 0.6f);
    }

    // ================================================================== 10. Dimension Slash: Event Horizon
    /**
     * Yami's strongest cut, done like the anime: a full 3-second charge. He raises the katana straight overhead with
     * both hands and HOLDS the pose (you are slowed to a crawl) while darkness pours into the blade - a black-flame
     * blade with a white edge that grows longer and wilder, rings of darkness closing in, crescents converging on the
     * tip, the ground cracking under his feet and space itself starting to fracture around the sword. Then one cut:
     * an impact frame, the screen shakes, and a single colossal blade of darkness rolls forward, splitting space open
     * along its whole path - jagged white rifts with the void showing through them hang in the air where it passed.
     * The horizon keeps pulling and cutting before it seals. Fully charged: 1.5x damage and heavy knockback.
     * Mana Zone: the wave is 1.7x bigger, faster, and reaches 90 blocks.
     */
    private static final int HORIZON_CHARGE = 60, HORIZON_CUT = 61;

    /** Where the blade is while it is held overhead (hilt, tip): straight up, leaning back a little. */
    private static Vec3[] overheadBlade(ServerPlayer p) {
        double yr = Math.toRadians(p.getYRot());
        Vec3 fw = new Vec3(-Math.sin(yr), 0, Math.cos(yr));
        Vec3 hilt = p.position().add(0, 2.05, 0).add(fw.scale(0.12));
        Vec3 tip = hilt.add(0, 1.55, 0).subtract(fw.scale(0.45));
        return new Vec3[]{hilt, tip};
    }

    public static void eventHorizon(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        if (inZone(p) && LimitBreak.active(p)) { equinox(p, sl, power); return; }   // Equinox only while Surpassing Limits AND in the Mana Zone
        final boolean eclipse = LimitBreak.active(p);   // Surpassing Limits: Lunar Eclipse - two horizons cut in an X
        final float dmg = power * (eclipse ? 2.2f : 1.5f);   // the full charge
        final boolean zone = inZone(p);
        final int seed0 = (int) (sl.getGameTime() * 31 + p.getId());
        if (zone) zoneMsg(p, "event_horizon");
        // held in the pose: barely able to move while the darkness gathers
        TimedModifiers.apply(p, net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED, TimedModifiers.HORIZON_CHARGE,
                "Event Horizon charge", -0.8, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_TOTAL, HORIZON_CUT + 4);
        sound(sl, p.position(), net.minecraft.sounds.SoundEvents.WARDEN_SONIC_CHARGE, 1f, 0.5f);
        sound(sl, p.position(), net.minecraft.sounds.SoundEvents.BEACON_ACTIVATE, 0.8f, 0.5f);

        // ---------------------------------------------------------------- the charge: darkness pours into the raised blade
        for (int t = 6; t < HORIZON_CHARGE; t += 2) {
            final int tt = t;
            AstaFx.later(t, () -> {
                if (!alive(p)) return;
                float k = (tt - 6) / (float) (HORIZON_CHARGE - 6);   // 0 -> 1 as the charge fills
                Vec3[] bl = overheadBlade(p);
                Vec3 ax = bl[1].subtract(bl[0]);
                Vec3 tip = bl[1].add(ax.normalize().scale(0.8 * k));   // the darkness runs past the tip as it builds
                // the blade wrapped in black flame, a white-hot edge along it
                FxSend.trail(sl, D, bl[0], tip, 0.28f + 0.5f * k, 3);
                FxSend.trail(sl, S, bl[0].add(ax.scale(0.05)), tip, 0.03f + 0.03f * k, 3);
                if (tt % 6 == 0) {   // darkness drawn in from all around, converging on the blade
                    double a = tt * 0.9;
                    Vec3 from = tip.add(Math.cos(a) * (3.5 + k * 2), -1.5 + Math.sin(tt) * 1.2, Math.sin(a) * (3.5 + k * 2));
                    Vec3 d = tip.subtract(from).normalize();
                    FxSend.crescent(sl, D, from, d, rightOf(d).cross(d).normalize(), 0.5f + k * 0.4f, 0.35f, 5, 0.8f);
                    FxSend.ring(sl, D, p.position().add(0, 0.05, 0), 6f - k * 3f, 0.25f + k * 0.2f, 0.08f, 6);
                }
                if (tt % 10 == 0) {   // black flame climbing your body, taller and taller
                    FxSend.wall(sl, D, p.position().add(0, 1.0, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1), 0.85f + k * 0.4f, 0.95f + k * 0.5f, -180, 180, 1.0f + k * 0.9f, 7, 0, 0);
                    sound(sl, p.position(), net.minecraft.sounds.SoundEvents.SOUL_ESCAPE, 0.8f, 0.4f + k * 0.3f);
                }
                if (tt == 20 || tt == 40 || tt == 54) {   // the ground under you gives way
                    Vec3 g = FxLib.floor(sl, p.position()).add(0, 0.04, 0);
                    FxSend.cracks(sl, D, g, new Vec3(1, 0, 0), new Vec3(0, 0, 1), 1.8f + k * 2.6f, 80 - tt, false, seed0 + tt);
                    FxSend.shake(sl, p.position(), 0.15f + k * 0.3f, 8, 16);
                    sound(sl, p.position(), net.minecraft.sounds.SoundEvents.DEEPSLATE_BREAK, 1f, 0.5f);
                }
                if (tt == 46 || tt == 56) {   // space around the blade starts to fracture
                    Vec3 fw = new Vec3(-Math.sin(Math.toRadians(p.getYRot())), 0, Math.cos(Math.toRadians(p.getYRot())));
                    FxSend.cracks(sl, D, tip.add(fw.scale(-0.2)), rightOf(fw), new Vec3(0, 1, 0), 0.9f + k * 0.6f, 14, false, seed0 + tt * 7);
                    sound(sl, tip, net.minecraft.sounds.SoundEvents.GLASS_BREAK, 0.7f, 0.5f);
                }
            });
        }

        // ---------------------------------------------------------------- the cut
        AstaFx.later(HORIZON_CUT, () -> {
            TimedModifiers.remove(p, net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED, TimedModifiers.HORIZON_CHARGE);
            if (!alive(p)) return;
            // aimed exactly where you look: at the enemy under your crosshair if there is one, otherwise along your
            // view; only when you look (almost) level does it hug the ground instead, rising out of it
            Vec3 look0 = p.getLookAngle();
            Vec3 eye0 = p.getEyePosition();
            final double reach0 = zone ? 90 : 55;
            net.minecraft.world.phys.EntityHitResult ehr = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(
                    p, eye0, eye0.add(look0.scale(reach0)), p.getBoundingBox().expandTowards(look0.scale(reach0)).inflate(1.5),
                    x -> x instanceof LivingEntity le && le.isAlive() && !x.isAlliedTo(p) && x != p, reach0 * reach0);
            if (ehr != null && ehr.getEntity() instanceof LivingEntity tgt && p.hasLineOfSight(tgt)) look0 = mid(tgt).subtract(eye0.add(0, -0.3, 0)).normalize();
            final boolean aimed = Math.abs(look0.y) > 0.12;
            Vec3 flatDir = new Vec3(look0.x, 0, look0.z).lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : new Vec3(look0.x, 0, look0.z).normalize();
            Vec3 dir = aimed ? look0.normalize() : flatDir;
            Vec3 start = aimed ? p.getEyePosition().add(0, -0.3, 0) : FxLib.floor(sl, p.position());
            Vec3 rt = rightOf(dir), up = aimed ? rt.cross(dir).normalize() : new Vec3(0, 1, 0);
            final double reach = zone ? 90 : 55;
            final float scale = zone ? 1.7f : 1.0f;
            final float speed = zone ? 4.2f : 3.2f;
            Vec3 end = start.add(dir.scale(reach));

            // the shake on everyone close enough to feel it (the impact frame comes only when the cut actually lands)
            FxSend.shake(sl, p.position(), 1.3f * scale, 20, 64);
            final boolean[] framed = {false};
            // space shatters right in front of the blade the instant it cuts
            Vec3 o = start.add(dir.scale(1.5)).add(aimed ? Vec3.ZERO : up.scale(1.2));
            FxSend.cracks(sl, D, o.add(dir.scale(1.0)).add(up.scale(0.6)), rt, up, 4.5f * scale, 70, false, seed0 + 999);

            // the colossal wave cuts everything in a widening, towering band as it passes
            for (LivingEntity t : enemies(sl, p, new AABB(start, end).inflate(10 * scale))) {
                Vec3 m = mid(t), on = closestOnSegment(start, end, m);
                Vec3 off = m.subtract(on);
                double along = on.distanceTo(start);
                if (Math.abs(off.dot(rt)) > (3.0 + along * 0.14 + (eclipse ? 3 : 0)) * scale || Math.abs(off.dot(up)) > 6.0 * scale) continue;
                final LivingEntity tt = t;
                AstaFx.later((int) (along / speed), () -> {
                    if (!tt.isAlive() || !alive(p)) return;
                    AstaFx.damage(p, tt, dmg, "event_horizon");
                    FxLib.impact(sl, D, mid(tt), 1.8f);
                    if (!framed[0]) { framed[0] = true; FxSend.impactFrame(sl, mid(tt), 6, FxSend.FRAME_YAMI, 48); }   // the first thing it cuts
                    Vec3 kb = dir.add(0, 0.35, 0).normalize().scale(1.6);
                    tt.setDeltaMovement(tt.getDeltaMovement().add(kb));
                    tt.hurtMarked = true;
                });
            }
            // ONE colossal blade of darkness, standing upright, rolling forward and growing as it goes
            int life = (int) (reach / speed) + 2;
            if (!eclipse) FxSend.slash(sl, D, o, dir, up, 4.0f * scale, 16f * scale, life, speed, 1.4f);
            else {
                // Lunar Eclipse: two colossal blades crossing in an X, a pale eclipse ring burning where they meet
                Vec3 upA = up.scale(0.7071).add(rt.scale(0.7071)), upB = up.scale(0.7071).subtract(rt.scale(0.7071));
                FxSend.slash(sl, D, o, dir, upA, 4.4f * scale, 17f * scale, life, speed, 1.4f);
                FxSend.slash(sl, D, o, dir, upB, 4.4f * scale, 17f * scale, life, speed, 1.4f);
                FxSend.wall(sl, S, o.add(dir.scale(2)), dir, up, 3.0f * scale, 3.3f * scale, -180, 180, 0.08f, life, 0, speed);
                FxSend.wall(sl, D, o.add(dir.scale(2)), dir, up, 3.3f * scale, 4.2f * scale, -180, 180, 0.3f, life, 0, speed);
                for (double k = 8; k < reach; k += 10) {
                    final double kk = k;
                    AstaFx.later((int) (k / speed), () -> {
                        // the rifts ride on the two blades themselves, out along each arm of the X, not in the middle
                        Vec3 cc = start.add(dir.scale(kk)).add(aimed ? Vec3.ZERO : up.scale(2.4 * scale));
                        double arm = (4.4 + (17 - 4.4) * Math.min(1.0, kk / reach * 2.2)) * scale * 0.55;
                        int q = 0;
                        for (Vec3 off : new Vec3[]{upA.scale(arm), upA.scale(-arm), upB.scale(arm), upB.scale(-arm)}) {
                            Vec3 along = off.normalize();
                            FxSend.cracks(sl, D, cc.add(off), along, along.cross(dir).normalize(), 1.8f * scale, 50, true, seed0 + (int) kk * 7 + q++);
                        }
                    });
                }
            }
            // space cut open along the whole path: rifts hang in the air where the wave passed
            int n = 0;
            for (double k = 5; k < reach; k += 6.5, n++) {
                final double kk = k;
                final int nn = n;
                AstaFx.later((int) (k / speed), () -> {
                    Vec3 base = aimed ? start.add(dir.scale(kk)) : FxLib.floor(sl, start.add(dir.scale(kk)));
                    float grow = (float) (1.0 + kk / reach * 0.8);
                    Vec3 c = base.add(up.scale((aimed ? 0 : 2.2) * scale * grow));
                    // the long tear runs along the cut, leaning a little each way so it reads as broken space
                    Vec3 along = dir.add(up.scale((nn % 2 == 0 ? 0.25 : -0.25))).normalize();
                    Vec3 across = along.cross(rt).normalize();
                    FxSend.cracks(sl, D, c, along, across, 3.4f * scale * grow, 56, true, seed0 + nn * 13);
                    if (nn % 2 == 0) FxSend.cracks(sl, D, c.add(up.scale(1.4 * scale)), rt, up, 1.4f * scale * grow, 44, false, seed0 + nn * 29);
                    if (nn % 3 == 0) sound(sl, c, net.minecraft.sounds.SoundEvents.GLASS_BREAK, 0.9f, 0.4f);
                });
            }
            if (eclipse) AstaFx.later(4, () -> {   // Lunar Eclipse: the two crossing blades leave an X of scars along the whole path
                Vec3 mid0 = start.add(aimed ? Vec3.ZERO : up.scale(2.4 * scale));
                Vec3 xa = up.scale(0.7071).add(rt.scale(0.7071)), xb = up.scale(0.7071).subtract(rt.scale(0.7071));
                double arm = 2.2 * scale;
                for (Vec3 off : new Vec3[]{xa.scale(arm), xa.scale(-arm), xb.scale(arm), xb.scale(-arm)}) {
                    Vec3 prev = mid0.add(dir).add(off.scale(0.2));
                    for (double k = 4; k < reach; k += 4) {
                        double spread = Math.min(1.0, k / 12.0);   // the X opens out as it leaves you
                        Vec3 c = mid0.add(dir.scale(k)).add(off.scale(spread));
                        FxSend.trail(sl, D, prev, c, 0.4f * scale, 40);
                        FxSend.trail(sl, S, prev, c, 0.04f * scale, 36);
                        prev = c;
                    }
                }
            });
            FxLib.shockRing(sl, D, p.position().add(0, 0.05, 0), 2.2f * scale);
            FxSend.ring(sl, S, p.position().add(0, 0.06, 0), 0.5f, 7f * scale, 0.05f, 10);
            sound(sl, p.position(), net.minecraft.sounds.SoundEvents.WARDEN_SONIC_BOOM, 1f, 0.45f);
            sound(sl, p.position(), net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE, 1f, 0.5f);
            sound(sl, p.position(), net.minecraft.sounds.SoundEvents.GLASS_BREAK, 1f, 0.3f);
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
                    sound(sl, start.add(dir.scale(10)), net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_SWEEP, 0.9f, 0.4f);
                });
            }
            AstaFx.later(44, () -> {
                FxSend.beam(sl, S, start.add(dir).add(0, 0.2, 0), end.add(0, 0.2, 0), 0.9f * scale, 4);
                sound(sl, start, net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT, 0.8f, 0.4f);
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
            else if (now % 10 == 0 && !ZoneClash.suppressed(p)) zoneTick(p, sl, now);
        }

        Cocoon cc = COCOON.get(id);
        if (cc != null) {
            if (now >= cc.until) { COCOON.remove(id); cocoonBurst(p, sl, cc); }
            else cocoonTick(p, sl, cc, now);
        }

        Foresight f = FORESIGHT.get(id);
        if (f != null && (now > f.until || f.charges <= 0)) { FORESIGHT.remove(id); ModeHud.hide(p, "ki_foresight"); }
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
            sound(sl, hit, net.minecraft.sounds.SoundEvents.SCULK_CLICKING, 1f, 0.5f);
            return;
        }

        Moon m = MOON.get(p.getUUID());
        if (m != null && now < m.until && e.getSource() instanceof SpellDamageSource) {
            e.setCanceled(true);
            m.absorbed = Math.min(40f, m.absorbed + e.getAmount());
            Vec3 src0 = e.getSource().getSourcePosition();
            swallow(sl, moonPos(p), src0 != null ? src0 : p.getEyePosition().add(p.getLookAngle().scale(3)));
            sound(sl, p.position(), net.minecraft.sounds.SoundEvents.SCULK_CLICKING, 1f, 0.6f);
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
            sound(sl, p.position(), net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT, 0.5f, 1.8f);
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
        sound(sl, p.position(), net.minecraft.sounds.SoundEvents.TOTEM_USE, 0.4f, 0.6f);
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
