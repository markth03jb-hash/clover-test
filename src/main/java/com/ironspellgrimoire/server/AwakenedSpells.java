package com.ironspellgrimoire.server;

import static com.ironspellgrimoire.server.FxUtil.*;

import com.ironspellgrimoire.IronSpellGrimoire;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The awakened spells - the page each grimoire writes when it awakens (server/Awakening). Each one is that
 * character's magic taken past where the book went before:
 * <ul>
 *   <li><b>Black - Demon-Destroyer: Absolute Zetten.</b> Asta's Zetten as a counter: he settles into a still stance
 *       (a second). Anything that strikes him in it is turned aside and answered at once; enemy projectiles near him are
 *       cut out of the air. Then one cut - a black line through everything in front of him for 18 blocks - and a beat
 *       later the world catches up: cracks along the line, and it all splits.</li>
 *   <li><b>Dark - Dark Cloaked Mana Zone: Moon-Severing Iai.</b> Yami's Black Moon pulled into the blade: a black moon
 *       rises over him and drags at everything nearby, he draws, and one great dark ring cuts round him in every
 *       direction; the moon breaks and its shards rain down.</li>
 *   <li><b>Wind - Star Magic: Never-Never Land.</b> Yuno's Star and Wind magic together: a dome of stars over the
 *       field for ten seconds. Foes inside are slowed, you and your friends inside cast stronger, and the stars in the
 *       dome fire beams of starlight at whoever is inside.</li>
 *   <li><b>Light - Light Magic: Lamp of Avior.</b> Light gathers round you, then is thrown as one star-bright lamp
 *       that bursts where it lands - an explosion of light strong enough to stop a tornado, with a star's cross flaring
 *       over it.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = IronSpellGrimoire.MODID)
public final class AwakenedSpells {
    private AwakenedSpells() {}

    private static final UUID MOON_PULL = UUID.fromString("b7d3c1a0-3f1e-4a7b-9c11-5e0d6a1001c1");
    private static final UUID LAND_SLOW = UUID.fromString("b7d3c1a0-3f1e-4a7b-9c11-5e0d6a1001c2");
    private static final UUID LAND_POWER = UUID.fromString("b7d3c1a0-3f1e-4a7b-9c11-5e0d6a1001c3");
    private static final UUID ZETTEN_HOLD = UUID.fromString("b7d3c1a0-3f1e-4a7b-9c11-5e0d6a1001c4");
    private static final Vec3 UP = new Vec3(0, 1, 0);

    private static void hit(ServerPlayer p, LivingEntity t, float amt, String id) { AstaFx.damage(p, t, amt, id); }

    private static Vec3 fwd(ServerPlayer p) {
        Vec3 f = flat(p.getLookAngle());
        return f.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : f.normalize();
    }

    // ================================================================== Black: Absolute Zetten
    /** Who is in the Zetten stance: until when, and with what power. */
    private static final Map<UUID, Object[]> STANCE = new HashMap<>();
    private static final int STANCE_T = 20;

    public static void absoluteZetten(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        long until = sl.getGameTime() + STANCE_T;
        STANCE.put(p.getUUID(), new Object[]{until, power, false});
        TimedModifiers.apply(p, Attributes.MOVEMENT_SPEED, ZETTEN_HOLD, "Zetten stance", -0.9, AttributeModifier.Operation.MULTIPLY_TOTAL, STANCE_T);
        // stillness: a black ring at his feet, a thin white line of ki round it
        FxSend.ring(sl, Fx.DARK, p.position().add(0, 0.05, 0), 3.2f, 3.4f, 0.05f, STANCE_T + 2);
        FxSend.ring(sl, Fx.SILVER, p.position().add(0, 0.06, 0), 3.0f, 3.05f, 0.03f, STANCE_T + 2);
        sound(sl, p.position(), SoundEvents.WARDEN_HEARTBEAT, 1.0f, 0.6f);
        sound(sl, p.position(), SoundEvents.BEACON_DEACTIVATE, 0.6f, 1.6f);
        for (int t = 2; t < STANCE_T; t += 3) AstaFx.later(t, () -> {   // magic that comes near him is cut out of the air
            if (!alive(p) || !STANCE.containsKey(p.getUUID())) return;
            for (Projectile pr : sl.getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(6), x -> x.getOwner() != p)) {
                FxSend.slash(sl, Fx.DARK, pr.position(), pr.getDeltaMovement().lengthSqr() < 1e-4 ? UP : pr.getDeltaMovement(), UP, 0.6f, 0.9f, 6, 0);
                pr.discard();
            }
        });
        AstaFx.later(STANCE_T, () -> { if (alive(p) && STANCE.remove(p.getUUID()) != null) zettenCut(p, power, false); });
    }

    /** A blow landing on him in the stance is turned aside and answered at once. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttacked(LivingAttackEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        Object[] s = STANCE.get(p.getUUID());
        if (s == null || p.serverLevel().getGameTime() > (long) s[0]) return;
        e.setCanceled(true);
        STANCE.remove(p.getUUID());
        ServerLevel sl = p.serverLevel();
        FxSend.impactFrame(sl, p.position().add(0, 1.2, 0), 2, true, 20);
        sound(sl, p.position(), SoundEvents.ANVIL_LAND, 0.7f, 1.8f);
        if (e.getSource().getEntity() instanceof LivingEntity by) {   // turn to face what struck
            Vec3 d = flat(by.position().subtract(p.position()));
            if (d.lengthSqr() > 1e-4) { float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z)); p.setYRot(yaw); p.setYHeadRot(yaw); }
        }
        AstaFx.later(1, () -> { if (alive(p)) zettenCut(p, (float) s[1], true); });
    }

    private static void zettenCut(ServerPlayer p, float power, boolean counter) {
        ServerLevel sl = p.serverLevel();
        final float pow = power * (counter ? 1.3f : 1f);
        Vec3 f = fwd(p), side = rightOf(f);
        if (counter && p.getLastHurtByMob() != null) { Vec3 d = flat(p.getLastHurtByMob().position().subtract(p.position())); if (d.lengthSqr() > 1e-4) { f = d.normalize(); side = rightOf(f); } }
        final Vec3 F = f, SIDE = side;
        Vec3 a = p.position().add(0, 1.1, 0), b = a.add(F.scale(18));
        // the cut: one black line, a white edge, and the after-image of the swing
        FxSend.slash(sl, Fx.DARK, a.add(F.scale(2)), F, SIDE, 3.5f, 5.5f, 10, 0, 2f);
        FxSend.beam(sl, Fx.DARK, a, b, 0.5f, 14);
        FxSend.beam(sl, Fx.SILVER, a, b, 0.08f, 14);
        FxSend.impactFrame(sl, a, 3, true, 32);
        sound(sl, p.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.4f, 0.5f);
        sound(sl, p.position(), SoundEvents.WITHER_BREAK_BLOCK, 0.5f, 1.8f);
        List<LivingEntity> struck = Kit.along(sl, p, a, b, 2.6);
        for (LivingEntity t : struck) hit(p, t, pow * 0.4f, "absolute_zetten");
        // a beat later the world catches up: cracks along the line, and everything on it splits
        for (int i = 0; i < 6; i++) {
            final Vec3 at = a.add(F.scale(2 + i * 3));
            AstaFx.later(4 + i, () -> FxSend.cracks(sl, Fx.DARK, at, F, UP, 1.6f, 22, false, (int) (at.x * 13 + at.z * 7)));
        }
        AstaFx.later(12, () -> {
            if (!alive(p)) return;
            for (LivingEntity t : Kit.along(sl, p, a, b, 2.8)) { hit(p, t, pow, "absolute_zetten"); Kit.push(t, F, 0.6, 0.25); }
            for (int i = 0; i < 4; i++) FxSend.slash(sl, Fx.SILVER, a.add(F.scale(3 + i * 4)), F, UP.add(SIDE.scale(i % 2 == 0 ? 0.6 : -0.6)), 1.8f, 2.8f, 8, 0);
            FxSend.shake(sl, a.add(F.scale(9)), 0.9f, 14, 32);
            FxSend.impactFrame(sl, a.add(F.scale(9)), 4, FxSend.FRAME_FINRAL, 36);
            sound(sl, a.add(F.scale(9)), SoundEvents.GLASS_BREAK, 1.6f, 0.5f);
            sound(sl, a.add(F.scale(9)), SoundEvents.GENERIC_EXPLODE, 0.8f, 1.5f);
        });
    }

    // ================================================================== Dark: Moon-Severing Iai
    public static void moonSeveringIai(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        final Vec3 c = p.position();
        final Vec3 moon = c.add(0, 9, 0);
        final float R = 16f;
        final int DRAW = 30;
        TimedModifiers.apply(p, Attributes.MOVEMENT_SPEED, ZETTEN_HOLD, "Iai stance", -0.8, AttributeModifier.Operation.MULTIPLY_TOTAL, DRAW);
        sound(sl, c, SoundEvents.WARDEN_SONIC_CHARGE, 1f, 0.5f);
        sound(sl, c, SoundEvents.SOUL_ESCAPE, 1.2f, 0.4f);
        FxSend.ring(sl, Fx.YAMI, c.add(0, 0.05, 0), R - 0.2f, R, 0.4f, DRAW + 10);   // the Mana Zone's edge
        for (int t = 0; t < DRAW; t += 2) {
            final int tt = t;
            AstaFx.later(t, () -> {
                if (!alive(p)) return;
                float k = tt / (float) DRAW;
                float r = 1.2f + 3.2f * k;
                // the black moon swelling in the sky: turning rings of black flame and a pale corona
                for (int i = 0; i < 3; i++) {
                    double a = tt * 0.15 + Math.PI * i / 3;
                    FxSend.wall(sl, Fx.YAMI, moon, new Vec3(Math.cos(a), 0, Math.sin(a)), UP, r * 0.92f, r, -180, 180, r * 0.45f, 3, 0, 0);
                }
                FxSend.ring(sl, Fx.SILVER, moon, r * 1.05f, r * 1.12f, 0.06f, 3);
                if (tt % 6 == 0) for (LivingEntity e : Kit.near(sl, p, c.add(0, 1, 0), R)) {   // it drags at everything in the zone
                    Vec3 in = c.subtract(e.position());
                    if (in.lengthSqr() > 4) Kit.push(e, in, 0.12, 0.02);
                    TimedModifiers.apply(e, Attributes.MOVEMENT_SPEED, MOON_PULL, "Black Moon", -0.5, AttributeModifier.Operation.MULTIPLY_TOTAL, 10);
                    FxSend.trail(sl, Fx.YAMI, mid(e), mid(e).add(moon.subtract(mid(e)).normalize().scale(1.4)), 0.12f, 4);
                }
            });
        }
        // the moon pours down into the blade
        AstaFx.later(DRAW - 6, () -> { if (alive(p)) { FxSend.beam(sl, Fx.YAMI, moon, p.position().add(0, 1.4, 0), 1.2f, 8); FxLib.implode(sl, Fx.YAMI, p.position().add(0, 1.2, 0), 1.4f); } });
        AstaFx.later(DRAW, () -> {
            if (!alive(p)) return;
            Vec3 at = p.position().add(0, 1.1, 0);
            // the draw: one ring of darkness cut round him in every direction, a second just behind it
            FxSend.wall(sl, Fx.YAMI, at, new Vec3(1, 0, 0), new Vec3(0, 0, 1), 1f, R, -180, 180, 0.5f, 12, 3, 0);
            FxSend.wall(sl, Fx.SILVER, at.add(0, 0.05, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1), 1f, R, -180, 180, 0.08f, 10, 3, 0);
            for (int i = 0; i < 8; i++) {
                double a = i * Math.PI / 4;
                Vec3 d = new Vec3(Math.cos(a), 0, Math.sin(a));
                FxSend.slash(sl, Fx.YAMI, at.add(d.scale(R * 0.55)), d, rightOf(d), R * 0.3f, R * 0.45f, 10, 0, 1.6f);
            }
            FxSend.impactFrame(sl, at, 4, true, 40);
            FxSend.shake(sl, at, 1.0f, 18, 40);
            sound(sl, at, SoundEvents.PLAYER_ATTACK_SWEEP, 1.6f, 0.4f);
            sound(sl, at, SoundEvents.WITHER_SHOOT, 0.7f, 0.6f);
            for (LivingEntity e : Kit.near(sl, p, at, R)) { hit(p, e, power, "moon_severing_iai"); Kit.push(e, mid(e).subtract(at), 0.7, 0.3); }
        });
        // the moon breaks: its shards rain down over the zone
        AstaFx.later(DRAW + 6, () -> {
            if (!alive(p)) return;
            FxLib.flash(sl, Fx.YAMI, moon, 2.2f);
            sound(sl, moon, SoundEvents.GLASS_BREAK, 1.5f, 0.4f);
            ThreadLocalRandom rnd = ThreadLocalRandom.current();
            for (int i = 0; i < 14; i++) {
                final Vec3 land = ground(sl, p, c.add(rnd.nextDouble(-R * 0.8, R * 0.8), 0, rnd.nextDouble(-R * 0.8, R * 0.8)));
                final int delay = 4 + i;
                AstaFx.later(delay, () -> {
                    if (!alive(p)) return;
                    FxSend.spike(sl, Fx.YAMI, land.add(0, 7, 0), new Vec3(0, -1, 0), 7f, 0.25f, 8, 3, com.ironspellgrimoire.network.FxPacket.EXTEND);
                    FxSend.ring(sl, Fx.YAMI, land.add(0, 0.05, 0), 0.2f, 1.8f, 0.05f, 8);
                    for (LivingEntity e : Kit.near(sl, p, land.add(0, 1, 0), 2.2)) hit(p, e, power * 0.18f, "moon_severing_iai");
                });
            }
        });
    }

    private static Vec3 ground(ServerLevel sl, ServerPlayer p, Vec3 near) {
        return clip(sl, p, near.add(0, 6, 0), near.add(0, -12, 0));
    }

    // ================================================================== Wind: Never-Never Land
    public static void neverNeverLand(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        final Vec3 c = p.position();
        final float R = 16f;
        final int T = 200;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        // the stars of the dome: fixed points on a half-sphere
        final Vec3[] stars = new Vec3[28];
        for (int i = 0; i < stars.length; i++) {
            double a = i * 2.399963, up = Math.acos(1 - (i + 0.5) / stars.length);   // spread evenly over the dome
            stars[i] = c.add(Math.cos(a) * Math.sin(up) * R, Math.cos(up) * R * 0.75 + 0.5, Math.sin(a) * Math.sin(up) * R);
        }
        sound(sl, c, SoundEvents.AMETHYST_BLOCK_CHIME, 1.6f, 0.6f);
        sound(sl, c, SoundEvents.BEACON_ACTIVATE, 1.2f, 1.4f);
        // the casting: lines of starlight thrown up from his hand, joining star to star into constellations
        for (int i = 0; i < stars.length; i++) {
            final Vec3 s = stars[i], prev = stars[Math.max(0, i - 1)];
            AstaFx.later(i / 2, () -> {
                FxSend.trail(sl, Fx.GOLD, i0(c), s, 0.05f, 6);
                if (s != prev) FxSend.trail(sl, Fx.SILVER, prev, s, 0.04f, 40);
            });
        }
        for (int t = 0; t < T; t += 20) {
            final int tt = t;
            AstaFx.later(t, () -> {
                if (!alive(p)) return;
                FxSend.ring(sl, Fx.WIND, c.add(0, 0.05, 0), R - 0.3f, R, 0.6f, 22);
                for (int i = 0; i < stars.length; i++) {
                    LightFx.glint(sl, i % 3 == 0 ? Fx.WIND : Fx.GOLD, stars[i], 0.55f, 22);
                    if (i % 4 == 0 && i + 4 < stars.length) FxSend.trail(sl, Fx.SILVER, stars[i], stars[i + 4], 0.03f, 22);
                }
                if (tt % 40 == 0) Kit.orb(sl, Fx.WIND, c.add(rnd.nextDouble(-6, 6), rnd.nextDouble(1, 5), rnd.nextDouble(-6, 6)), new Vec3(rnd.nextDouble(-0.1, 0.1), 0.02, rnd.nextDouble(-0.1, 0.1)), 0.25f, 30);   // Sylph's wind
                // inside the dome: foes slowed, friends' magic stronger
                for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(R), x -> x.isAlive() && x.position().distanceTo(c) <= R)) {
                    if (e == p || e.isAlliedTo(p) || e instanceof ServerPlayer) TimedModifiers.apply(e, AttributeRegistry.SPELL_POWER.get(), LAND_POWER, "Never-Never Land", 0.2, AttributeModifier.Operation.MULTIPLY_BASE, 25);
                    else TimedModifiers.apply(e, Attributes.MOVEMENT_SPEED, LAND_SLOW, "Never-Never Land", -0.35, AttributeModifier.Operation.MULTIPLY_TOTAL, 25);
                }
            });
        }
        for (int t = 10; t < T; t += 12) {   // the stars fire beams of starlight at whoever is inside
            AstaFx.later(t, () -> {
                if (!alive(p)) return;
                List<LivingEntity> in = Kit.near(sl, p, c.add(0, 1, 0), R);
                in.removeIf(e -> e instanceof ServerPlayer);
                if (in.isEmpty()) return;
                ThreadLocalRandom r = ThreadLocalRandom.current();
                for (int k = 0; k < 2; k++) {
                    LivingEntity e = in.get(r.nextInt(in.size()));
                    Vec3 s = stars[r.nextInt(stars.length)];
                    FxSend.beam(sl, Fx.GOLD, s, mid(e), 0.22f, 6);
                    FxSend.beam(sl, Fx.SILVER, s, mid(e), 0.06f, 6);
                    LightFx.glint(sl, Fx.GOLD, mid(e), 0.8f, 6);
                    hit(p, e, power * 0.3f, "never_never_land");
                }
                sound(sl, c, SoundEvents.AMETHYST_CLUSTER_HIT, 1.2f, 1.6f);
            });
        }
    }

    private static Vec3 i0(Vec3 c) { return c.add(0, 1.6, 0); }

    // ================================================================== Light: Lamp of Avior
    public static void lampOfAvior(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        final int GATHER = 22;
        TimedModifiers.apply(p, Attributes.MOVEMENT_SPEED, ZETTEN_HOLD, "Lamp of Avior", -0.6, AttributeModifier.Operation.MULTIPLY_TOTAL, GATHER);
        sound(sl, p.position(), SoundEvents.BEACON_POWER_SELECT, 1.2f, 0.7f);
        LightFx.circle(sl, Fx.GOLD, p.position().add(0, 0.05, 0), Vec3.ZERO, 3.2f, 0.1f, GATHER + 6, p, 0.05);
        for (int t = 0; t < GATHER; t += 2) {   // light drawn in round you from every side
            final int tt = t;
            AstaFx.later(t, () -> {
                if (!alive(p)) return;
                Vec3 hand = p.getEyePosition().add(p.getLookAngle().scale(1.2)).add(0, -0.2, 0);
                for (int i = 0; i < 4; i++) {
                    double a = tt * 0.4 + i * Math.PI / 2;
                    Vec3 from = hand.add(Math.cos(a) * 2.6, Math.sin(a * 1.3) * 1.2, Math.sin(a) * 2.6);
                    FxSend.trail(sl, Fx.GOLD, from, from.add(hand.subtract(from).scale(0.5)), 0.08f, 4);
                }
                LightFx.glint(sl, Fx.GOLD, hand, 0.4f + tt / (float) GATHER, 3);
            });
        }
        AstaFx.later(GATHER, () -> {
            if (!alive(p)) return;
            Vec3 from = p.getEyePosition().add(p.getLookAngle().scale(1.2)).add(0, -0.2, 0);
            Vec3 look = p.getLookAngle();
            sound(sl, from, SoundEvents.BEACON_ACTIVATE, 1.4f, 1.6f);
            fly(p, sl, from, look, power, 0);
        });
    }

    /** The lamp flies two blocks a tick until it meets something or goes 40 blocks. */
    private static void fly(ServerPlayer p, ServerLevel sl, Vec3 at, Vec3 dir, float power, int step) {
        if (!alive(p)) return;
        Vec3 next = at.add(dir.scale(2.0));
        Vec3 stop = clip(sl, p, at, next);
        boolean hitBlock = stop.distanceToSqr(next) > 1e-4;
        boolean hitMob = !Kit.along(sl, p, at, stop, 1.2).isEmpty();
        FxSend.trail(sl, Fx.GOLD, at, stop, 0.5f, 8);
        FxSend.trail(sl, Fx.SILVER, at, stop, 0.15f, 8);
        Kit.orb(sl, Fx.GOLD, stop, Vec3.ZERO, 0.6f, 2);
        if (hitBlock || hitMob || step >= 20) { burst(p, sl, stop, power); return; }
        AstaFx.later(1, () -> fly(p, sl, stop, dir, power, step + 1));
    }

    private static void burst(ServerPlayer p, ServerLevel sl, Vec3 c, float power) {
        final float R = 7f;
        LightFx.burst(sl, Fx.GOLD, c, R, 16);
        LightFx.burst(sl, Fx.SILVER, c, R * 0.6f, 12);
        LightFx.pillar(sl, Fx.GOLD, c.add(0, -1, 0), 2.5f, 18f, 0, 18);
        FxSend.ring(sl, Fx.GOLD, c, 0.6f, R * 1.8f, 0.3f, 16);
        FxSend.ring(sl, Fx.SILVER, c.add(0, 0.5, 0), 0.4f, R * 1.4f, 0.12f, 14);
        // a star's cross flaring over the blast
        LightFx.ray(sl, Fx.GOLD, c.add(-R * 1.4, 0, 0), c.add(R * 1.4, 0, 0), 0.4f, 14);
        LightFx.ray(sl, Fx.GOLD, c.add(0, -R * 0.6, 0), c.add(0, R * 1.6, 0), 0.4f, 14);
        LightFx.ray(sl, Fx.GOLD, c.add(0, 0, -R * 1.4), c.add(0, 0, R * 1.4), 0.4f, 14);
        LightFx.glint(sl, Fx.SILVER, c, 3.2f, 16);
        FxSend.impactFrame(sl, c, 5, FxSend.FRAME_LIGHT, 40);
        FxSend.shake(sl, c, 1.1f, 18, 40);
        sound(sl, c, SoundEvents.GENERIC_EXPLODE, 1.4f, 1.1f);
        sound(sl, c, SoundEvents.BELL_RESONATE, 1.2f, 1.6f);
        for (LivingEntity e : Kit.near(sl, p, c, R)) {
            float k = (float) (1 - 0.5 * mid(e).distanceTo(c) / R);
            hit(p, e, power * k, "lamp_of_avior");
            Kit.push(e, mid(e).subtract(c), 1.2, 0.5);
        }
        AstaFx.later(6, () -> { if (alive(p)) for (LivingEntity e : Kit.near(sl, p, c, R * 0.7)) hit(p, e, power * 0.25f, "lamp_of_avior"); });
    }
}
