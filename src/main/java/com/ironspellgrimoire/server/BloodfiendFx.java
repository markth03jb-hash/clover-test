package com.ironspellgrimoire.server;

import static com.ironspellgrimoire.server.FxUtil.*;

import com.ironspellgrimoire.network.AddonNetwork;
import com.ironspellgrimoire.network.FxPacket;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

/**
 * The Bloodfiend Grimoire - Don Quixote as the Manager of La Manchaland. Everything is hardblood: blood that sets hard as
 * steel into a lance, a bow, sabres, a greatsword, gauntlets, a barricade of spears. Dark glossy crimson with a wet red
 * sheen; old gold only for the knight's flourish. Hits leave Bleed; Bleed feeds Bloodfeast; every cast hardens Hardblood.
 * Sounds are vanilla ones chosen to be close: the lance's ring is a trident, Rocinante's charge a galloping horse,
 * the wound a wet squelch, the thirst a heartbeat.
 */
public final class BloodfiendFx {
    private BloodfiendFx() {}

    static final int BL = 18, GD = Fx.GOLD;

    /** The weapon of hardblood in the hands for {@code life} ticks (client: LimbusWeaponLayer). */
    static void held(ServerPlayer p, int kind, int life) { held(p, kind, life, 0, 0, 0); }

    /** ... with the ticks its blows land on ({@code first}, then every {@code every}, {@code count} of them): the weapon swings on them. */
    static void held(ServerPlayer p, int kind, int life, int first, int every, int count) {
        FxPacket pkt = new FxPacket();
        pkt.shape = 63; pkt.id = p.getId(); pkt.life = life; pkt.sweep = kind; pkt.palette = 0;
        pkt.a0 = first; pkt.a1 = every; pkt.h = count;
        AddonNetwork.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), pkt);
        for (int t = 5; t < life; t += 7) {   // blood running off the weapon and dripping to the ground
            final int tt = t;
            AstaFx.later(t, () -> { if (alive(p)) LimbusStatus.drip(p.serverLevel(), Kit.hand(p).add(p.getLookAngle().scale(0.6 + (tt % 3) * 0.4)), 1); });
        }
    }

    private static float pow(ServerPlayer p, float base) {
        float k = 1f + 0.05f * (LimbusStatus.hardblood(p) / 10);
        return LimbusStatus.vampire(p) ? base * k * 1.2f : base * k;
    }

    private static void hit(ServerPlayer p, LivingEntity t, float dmg, String id, int bleed) {
        AstaFx.damage(p, t, dmg, id);
        LimbusStatus.bleed(p, t, bleed);
        LimbusStatus.splash(p.serverLevel(), mid(t), 0.6f);
    }

    /** Blood setting hard: a tight crimson ring at the hand and the sound of it. */
    private static void harden(ServerLevel sl, Vec3 at) {
        FxSend.ring(sl, BL, at, 0.05f, 0.7f, 0.05f, 6);
        sound(sl, at, SoundEvents.HONEY_BLOCK_PLACE, 0.8f, 0.6f);
        sound(sl, at, SoundEvents.AMETHYST_BLOCK_CHIME, 0.6f, 0.5f);
    }

    // ================================================================== 1. Hardblood Lance
    /** A long lance of hardblood thrust 6 blocks: one piercing line, Bleed 3. */
    public static void hardbloodLance(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        held(p, 1, 22, 8, 0, 1);
        harden(sl, Kit.hand(p));
        LimbusStatus.addHardblood(p, 1);
        AstaFx.later(8, () -> {
            if (!alive(p)) return;
            Vec3 look = p.getLookAngle(), a = p.getEyePosition().add(0, -0.3, 0), b = a.add(look.scale(LimbusStatus.vampire(p) ? 8.5 : 6.5));
            // the thrust: a lance-shaped spike shooting out, a white-red line of force and a ring where it stops
            FxSend.spike(sl, BL, a.add(look.scale(0.8)), look, 5.6f, 0.22f, 8, 0, FxPacket.EXTEND);
            FxSend.trail(sl, BL, a.add(look.scale(0.5)), b, 0.35f, 6);
            FxSend.ring(sl, BL, b, 0.1f, 1.2f, 0.05f, 6);
            FxSend.crescent(sl, BL, b, look, rightOf(look), 0.4f, 1.0f, 5, 0);
            for (int k = 1; k <= 3; k++) LimbusStatus.drip(sl, a.add(b.subtract(a).scale(k / 4.0)), k);
            sound(sl, a, SoundEvents.TRIDENT_THROW, 1f, 0.8f);
            sound(sl, a, SoundEvents.PLAYER_ATTACK_STRONG, 1f, 0.8f);
            for (LivingEntity t : Kit.along(sl, p, a, b, 1.2)) {
                hit(p, t, pow(p, power), "hardblood_lance", 3);
                Kit.push(t, look, 0.6, 0.15);
            }
        });
    }

    // ================================================================== 2. Justice Charge
    /** Lance levelled, the knight charges 12 blocks; the first foe is carried on the lance and slammed off at the end. */
    public static void justiceCharge(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        held(p, 1, 26, 15, 0, 1);
        LimbusStatus.addHardblood(p, 2);
        Vec3 dir = flat(p.getLookAngle());
        sound(sl, p.position(), SoundEvents.HORSE_GALLOP, 1.2f, 0.9f);
        sound(sl, p.position(), SoundEvents.HORSE_ANGRY, 0.8f, 1.1f);
        p.displayClientMessage(Component.literal("Gallop on!").withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC), true);
        final LivingEntity[] carried = {null};
        final Set<Integer> struck = new HashSet<>();
        for (int i = 0; i < 12; i++) {
            final int ii = i;
            AstaFx.later(4 + i, () -> {
                if (!alive(p)) return;
                Vec3 at = p.position();
                Vec3 to = clip(sl, p, at.add(0, 1, 0), at.add(0, 1, 0).add(dir.scale(1.1))).add(0, -1, 0);
                SpaceFx.move(p, to, yawOf(dir));
                p.fallDistance = 0;
                Vec3 tip = to.add(0, 1.2, 0).add(dir.scale(2.4));
                FxSend.trail(sl, BL, at.add(0, 1.0, 0), to.add(0, 1.0, 0), 0.5f, 8);
                if (ii % 2 == 0) FxSend.crescent(sl, BL, tip, dir, new Vec3(0, 1, 0), 0.6f, 1.3f, 4, 0);
                if (ii % 3 == 0) sound(sl, to, SoundEvents.HORSE_GALLOP, 0.9f, 1.0f);
                for (LivingEntity t : Kit.near(sl, p, tip, 1.6)) {
                    if (!struck.add(t.getId())) continue;
                    hit(p, t, pow(p, power) * 0.6f, "justice_charge", 2);
                    if (carried[0] == null) carried[0] = t;
                }
                if (carried[0] != null && carried[0].isAlive()) {
                    carried[0].teleportTo(tip.x, tip.y - carried[0].getBbHeight() * 0.5, tip.z);
                    carried[0].setDeltaMovement(Vec3.ZERO);
                }
                if (ii == 11) {
                    FxSend.shake(sl, tip, 0.5f, 8, 16);
                    FxSend.ring(sl, BL, to.add(0, 0.1, 0), 0.4f, 3.2f, 0.12f, 10);
                    FxSend.crescent(sl, GD, tip, dir, new Vec3(0, 1, 0), 0.8f, 2.4f, 6, 0);
                    sound(sl, tip, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.2f, 0.6f);
                    if (carried[0] != null && carried[0].isAlive()) {
                        hit(p, carried[0], pow(p, power), "justice_charge", 3);
                        Kit.push(carried[0], dir, 1.6, 0.5);
                    }
                }
            });
        }
    }

    private static float yawOf(Vec3 d) { return (float) Math.toDegrees(Math.atan2(-d.x, d.z)); }

    // ================================================================== 3. Sanguine Crescent
    /** A wide sweep throws a crescent of blood that splits into droplets landing as little blood spikes. */
    public static void sanguineCrescent(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        held(p, 1, 18, 7, 0, 1);
        LimbusStatus.addHardblood(p, 1);
        AstaFx.later(7, () -> {
            if (!alive(p)) return;
            Vec3 look = flat(p.getLookAngle()), rt = rightOf(look);
            Vec3 at = p.position().add(0, 1.1, 0).add(look.scale(1.2));
            FxSend.slash(sl, BL, at, look, rt, 2.4f, 4.2f, 10, 1.1f, 1.4f);
            FxSend.slash(sl, GD, at.add(0, 0.05, 0), look, rt, 2.2f, 3.8f, 6, 1.1f, 0.4f);
            sound(sl, at, SoundEvents.PLAYER_ATTACK_SWEEP, 1.2f, 0.6f);
            sound(sl, at, SoundEvents.HONEY_BLOCK_SLIDE, 1f, 0.7f);
            Vec3 end = at.add(look.scale(11));
            for (LivingEntity t : enemies(sl, p, new AABB(at, end).inflate(4.5, 2, 4.5))) {
                Vec3 on = closestOnSegment(at, end, mid(t));
                if (on.distanceTo(mid(t)) > 4.0) continue;
                hit(p, t, pow(p, power), "sanguine_crescent", 2);
            }
            // the crescent breaks into droplets that rain down as small spikes of blood
            ThreadLocalRandom r = ThreadLocalRandom.current();
            for (int k = 0; k < 14; k++) {
                final double s = 4 + r.nextDouble() * 7, off = r.nextDouble(-3.5, 3.5);
                AstaFx.later(4 + (int) (s * 0.9), () -> {
                    Vec3 g = p.position().add(look.scale(s)).add(rt.scale(off));
                    FxSend.spike(sl, BL, g, new Vec3(r.nextDouble(-0.3, 0.3), 1, r.nextDouble(-0.3, 0.3)), 0.6f + r.nextFloat() * 0.5f, 0.09f, 18, 2, FxPacket.GROUND);
                    for (LivingEntity t : Kit.near(sl, p, g, 1.0)) LimbusStatus.bleed(p, t, 1);
                });
            }
        });
    }

    // ================================================================== 4. Crimson Pact
    /** Drink your own blood: two hearts for 150 Bloodfeast, 5 Hardblood and a burst of speed. */
    public static void crimsonPact(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        p.hurt(sl.damageSources().magic(), 4f);
        LimbusStatus.addBloodfeast(p, 150);
        LimbusStatus.addHardblood(p, 5);
        TimedModifiers.apply(p, net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED, java.util.UUID.fromString("e3b61c20-7a4d-4f8e-9b11-0d1a6c900110"),
                "Crimson Pact", 0.35, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_TOTAL, 100);
        LimbusStatus.splash(sl, Kit.hand(p), 1f);
        for (int i = 0; i < 3; i++) {
            final int ii = i;
            AstaFx.later(2 + i * 4, () -> {
                FxSend.ring(sl, BL, p.position().add(0, 0.1 + ii * 0.6, 0), 0.2f, 1.6f + ii * 0.4f, 0.08f, 8);
                sound(sl, p.position(), SoundEvents.GENERIC_DRINK, 1f, 0.7f);
            });
        }
        sound(sl, p.position(), SoundEvents.WARDEN_HEARTBEAT, 1.2f, 1.0f);
    }

    // ================================================================== 5. Thirst
    /** Every bleeding foe within 10 blocks is bled into you: streams of blood flow to you, healing you and filling Bloodfeast. */
    public static void thirst(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        sound(sl, p.position(), SoundEvents.WARDEN_HEARTBEAT, 1.4f, 0.8f);
        FxSend.ring(sl, BL, p.position().add(0, 0.1, 0), 9.5f, 10f, 0.08f, 20);
        FxSend.ring(sl, BL, p.position().add(0, 0.1, 0), 10f, 0.5f, 0.1f, 18);
        int n = 0;
        for (LivingEntity t : Kit.near(sl, p, p.position(), 10)) {
            int b = LimbusStatus.bleedOf(t);
            if (b <= 0) continue;
            n++;
            final LivingEntity tt = t;
            for (int k = 0; k < 6; k++) {
                final int kk = k;
                AstaFx.later(2 + k * 2, () -> {
                    if (!tt.isAlive() || !alive(p)) return;
                    Vec3 a = mid(tt), b2 = p.position().add(0, 1.1, 0);
                    Vec3 bend = a.add(b2).scale(0.5).add(0, 1.2 + 0.3 * Math.sin(kk), 0);
                    FxSend.trail(sl, BL, a, bend, 0.18f, 8);
                    FxSend.trail(sl, BL, bend, b2, 0.18f, 8);
                });
            }
            float d = pow(p, power) * (0.5f + 0.08f * b);
            AstaFx.damage(p, t, d, "thirst");
            p.heal(1.0f + 0.15f * b);
            LimbusStatus.addBloodfeast(p, 12 * b);
        }
        if (n > 0) {
            sound(sl, p.position(), SoundEvents.GENERIC_DRINK, 1f, 0.6f);
            LimbusStatus.addHardblood(p, 2 + n);
        }
    }

    // ================================================================== 6. Hardblood Barricade
    /** A ring of blood spears rises round you for 4 s; anything that touches it is thrown back and bleeds. */
    public static void barricade(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        Vec3 c = p.position();
        int n = 16;
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            Vec3 out = new Vec3(Math.cos(a), 0, Math.sin(a));
            Vec3 base = c.add(out.scale(2.6));
            FxSend.spike(sl, BL, base, out.scale(0.45).add(0, 1, 0).normalize(), 2.0f + (i % 2) * 0.6f, 0.16f, 80, 4, FxPacket.GROUND);
            FxSend.spike(sl, BL, c.add(out.scale(2.2)), out.scale(0.8).add(0, 1, 0).normalize(), 1.2f, 0.1f, 80, 6, FxPacket.GROUND);
        }
        FxSend.ring(sl, BL, c.add(0, 0.05, 0), 2.0f, 3.1f, 0.06f, 80);
        sound(sl, c, SoundEvents.AMETHYST_CLUSTER_BREAK, 1.2f, 0.5f);
        sound(sl, c, SoundEvents.HONEY_BLOCK_BREAK, 1.2f, 0.6f);
        LimbusStatus.addHardblood(p, 3);
        Moves.mode(p, "hardblood_barricade", 80, 4, pl -> {
            for (LivingEntity t : Kit.near(sl, pl, c, 3.4)) {
                if (t.position().distanceTo(c) < 1.8) continue;
                AstaFx.damage(pl, t, pow(pl, power) * 0.4f, "hardblood_barricade");
                LimbusStatus.bleed(pl, t, 1);
                Kit.push(t, flat(t.position().subtract(c)), 0.9, 0.3);
                LimbusStatus.splash(sl, mid(t), 0.5f);
            }
        });
    }

    // ================================================================== 7. Hardblood Bow
    /** A recurved bow of blood: one heavy arrow at a time, loosed five times in a row. Each one pierces, trails blood and
     *  lands hard - a burst, a ring and a jolt. In Vampire Mode the arrows bend toward bleeding foes. */
    public static void hardbloodBow(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        int shots = 5, first = 10, every = 7;
        held(p, 2, first + every * (shots - 1) + 10, first, every, shots);
        harden(sl, Kit.hand(p));
        LimbusStatus.addHardblood(p, 1);
        for (int i = 0; i < shots; i++) {
            final int ii = i;
            AstaFx.later(first + i * every - 5, () -> { if (alive(p)) sound(sl, p.position(), SoundEvents.CROSSBOW_LOADING_MIDDLE, 0.8f, 0.8f + ii * 0.05f); });
            AstaFx.later(first + i * every, () -> {
                if (!alive(p)) return;
                Vec3 look = p.getLookAngle(), from = p.getEyePosition().add(0, -0.15, 0).add(look.scale(0.6));
                FxSend.ring(sl, BL, from.add(look.scale(0.4)), 0.05f, 0.7f, 0.04f, 5);   // the release
                sound(sl, from, SoundEvents.ARROW_SHOOT, 1.2f, 0.55f);
                sound(sl, from, SoundEvents.CROSSBOW_SHOOT, 1f, 0.6f + ii * 0.05f);
                arrow(p, sl, from, look, pow(p, power) * 0.45f, 0);
            });
        }
    }

    private static void arrow(ServerPlayer p, ServerLevel sl, Vec3 from, Vec3 dir, float dmg, int step) {
        if (step > 12 || !alive(p)) return;
        Vec3 d = dir;
        if (LimbusStatus.vampire(p)) {   // seeks the nearest bleeding foe
            LivingEntity best = null; double bd = 1e9;
            for (LivingEntity t : Kit.near(sl, p, from, 14)) {
                if (LimbusStatus.bleedOf(t) <= 0) continue;
                double dd = t.distanceToSqr(from);
                if (dd < bd) { bd = dd; best = t; }
            }
            if (best != null) d = d.scale(0.6).add(mid(best).subtract(from).normalize().scale(0.4)).normalize();
        }
        Vec3 to = clip(sl, p, from, from.add(d.scale(3.4)));
        // a heavy arrowhead of blood and a thick glowing trail that sags and drips as it goes
        FxSend.spike(sl, BL, from, d, 1.4f, 0.12f, 3, 0, FxPacket.POP);
        FxSend.trail(sl, BL, from, to, 0.18f, 8);
        FxSend.trail(sl, Fx.CRIMSON, from, to, 0.05f, 4);
        if (step % 2 == 0) LimbusStatus.drip(sl, from.add(to).scale(0.5), 1);
        boolean stop = to.distanceTo(from) < 3.3;
        LivingEntity struck = null;
        for (LivingEntity t : Kit.along(sl, p, from, to, 0.9)) { hit(p, t, dmg, "hardblood_bow", 2); if (struck == null) struck = t; }
        if (stop || struck != null) {   // the impact
            Vec3 at = struck != null ? mid(struck) : to;
            LimbusStatus.splash(sl, at, 1.3f);
            FxSend.ring(sl, BL, at, 0.1f, 1.8f, 0.08f, 8);
            FxSend.crescent(sl, BL, at, d, rightOf(d), 0.3f, 1.2f, 5, 0);
            FxSend.shake(sl, at, 0.25f, 4, 12);
            sound(sl, at, SoundEvents.TRIDENT_HIT, 1.1f, 0.6f);
            sound(sl, at, SoundEvents.HONEY_BLOCK_BREAK, 1f, 0.7f);
            if (struck != null) Kit.push(struck, d, 0.5, 0.1);
            if (stop) return;
        }
        final Vec3 nd = d;
        AstaFx.later(1, () -> arrow(p, sl, to, nd, dmg, step + 1));
    }

    // ================================================================== 8. Twin Blood Blades
    /** Two curved sabres of blood: six fast cuts, each a different way - down-right, rising left, flat across, down-left,
     *  straight down - and a cross-cut that bursts the target's Bleed. */
    public static void twinBlades(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        held(p, 3, 34, 6, 4, 6);
        LimbusStatus.addHardblood(p, 2);
        harden(sl, Kit.hand(p));
        int[] at = {6, 10, 14, 18, 22, 28};
        // the blade's span for each cut, as (right, up): its tips point that way
        double[][] dirs = {{1, -0.9}, {-1, -0.6}, {1, 0.05}, {-1, 0.9}, {0.15, 1}, {0, 0}};
        for (int i = 0; i < 6; i++) {
            final int ii = i;
            AstaFx.later(at[i], () -> {
                if (!alive(p)) return;
                Vec3 look = flat(p.getLookAngle()), rt = rightOf(look);
                Vec3 c = p.position().add(0, 1.1, 0).add(look.scale(1.3));
                boolean last = ii == 5;
                if (last) {   // the cross
                    Vec3 u1 = rt.add(0, 0.9, 0).normalize(), u2 = rt.scale(-1).add(0, 0.9, 0).normalize();
                    FxSend.slash(sl, BL, c, look, u1, 1.8f, 3.4f, 8, 0.2f, 1.2f);
                    FxSend.slash(sl, BL, c, look, u2, 1.8f, 3.4f, 8, 0.2f, 1.2f);
                    FxSend.impactFrame(sl, c.add(look.scale(1.5)), 3, FxSend.FRAME_BLOOD, 18);
                    sound(sl, c, SoundEvents.PLAYER_ATTACK_CRIT, 1.2f, 0.7f);
                } else {
                    Vec3 u = rt.scale(dirs[ii][0]).add(0, dirs[ii][1], 0).normalize();
                    FxSend.slash(sl, BL, c.add(0, dirs[ii][1] * 0.2, 0), look, u, 1.4f, 2.6f, 6, 0.15f, 0.8f);
                }
                LimbusStatus.drip(sl, c.add(look.scale(0.8)), 2);
                sound(sl, c, SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 1.1f + ii * 0.05f);
                for (LivingEntity t : Kit.near(sl, p, c.add(look.scale(0.8)), last ? 3.2 : 2.6)) {
                    hit(p, t, pow(p, power) * (last ? 0.5f : 0.22f), "twin_blood_blades", last ? 2 : 1);
                    if (last) LimbusStatus.burstBleed(p, t, 0.6f);
                }
            });
        }
    }

    // ================================================================== 9. Blood Fist Barrage
    /** Bare fists, blood on the knuckles: each arm pulls back and drives in, fifteen times in 1.5 s, then one heavy blow. */
    public static void fistBarrage(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        LimbusStatus.addHardblood(p, 2);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < 15; i++) {
            final int ii = i;
            AstaFx.later(8 + i * 2, () -> {   // each lands as its arm comes forward in the animation
                if (!alive(p)) return;
                Vec3 look = p.getLookAngle(), rt = rightOf(flat(look));
                double side = ii % 2 == 0 ? 0.35 : -0.35;
                Vec3 o = p.getEyePosition().add(0, -0.35, 0).add(rt.scale(side + r.nextDouble(-0.15, 0.15))).add(0, r.nextDouble(-0.25, 0.25), 0);
                Vec3 hitAt = o.add(look.scale(2.0));
                // the force of the punch: a short streak and a ring where it lands, a little blood thrown
                FxSend.trail(sl, BL, o.add(look.scale(0.5)), hitAt, 0.12f, 3);
                FxSend.ring(sl, BL, hitAt, 0.05f, 0.5f, 0.04f, 3);
                if (ii % 2 == 0) sound(sl, o, SoundEvents.PLAYER_ATTACK_WEAK, 0.8f, 1.1f + r.nextFloat() * 0.3f);
                for (LivingEntity t : Kit.near(sl, p, p.getEyePosition().add(look.scale(2.2)), 1.8)) {
                    AstaFx.damage(p, t, pow(p, power) * 0.1f, "blood_fist_barrage");
                    t.invulnerableTime = 0;
                    if (ii % 3 == 0) { LimbusStatus.bleed(p, t, 1); LimbusStatus.splash(sl, mid(t), 0.4f); }
                    if (LimbusStatus.vampire(p)) p.heal(0.25f);
                }
            });
        }
        AstaFx.later(42, () -> {
            if (!alive(p)) return;
            Vec3 look = p.getLookAngle(), c = p.getEyePosition().add(look.scale(2.0));
            FxSend.trail(sl, BL, p.getEyePosition().add(look.scale(0.4)), c, 0.4f, 6);
            FxSend.ring(sl, BL, c, 0.2f, 2.4f, 0.1f, 8);
            FxSend.crescent(sl, GD, c, look, new Vec3(0, 1, 0), 0.4f, 1.4f, 5, 0);
            FxSend.impactFrame(sl, c, 3, FxSend.FRAME_BLOOD, 18);
            FxSend.shake(sl, c, 0.4f, 6, 14);
            sound(sl, c, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.3f, 0.6f);
            sound(sl, c, SoundEvents.GENERIC_EXPLODE, 0.5f, 1.4f);
            for (LivingEntity t : Kit.near(sl, p, c, 2.4)) {
                hit(p, t, pow(p, power) * 0.8f, "blood_fist_barrage", 3);
                Kit.push(t, look, 1.8, 0.5);
            }
        });
    }

    // ================================================================== 10. Hardblood Greatsword
    /** One great blade of blood brought down overhead; a wave runs along the ground and rises into blood spikes. */
    public static void greatsword(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        held(p, 5, 30, 16, 0, 1);
        LimbusStatus.addHardblood(p, 2);
        harden(sl, Kit.hand(p));
        sound(sl, p.position(), SoundEvents.ARMOR_EQUIP_NETHERITE, 1f, 0.6f);
        AstaFx.later(16, () -> {
            if (!alive(p)) return;
            boolean full = LimbusStatus.bloodfeast(p) >= 500;
            if (full) LimbusStatus.spendBloodfeast(p, 200);
            Vec3 look = flat(p.getLookAngle()), rt = rightOf(look);
            Vec3 c = p.position().add(look.scale(1.5)).add(0, 1.4, 0);
            float sz = full ? 2f : 1f;
            FxSend.slash(sl, BL, c, look, new Vec3(0, 1, 0).add(look.scale(0.2)).normalize(), 2.0f * sz, 4.0f * sz, 8, 0.1f, 1.8f);
            FxSend.impactFrame(sl, c.add(look.scale(1.5)), 3, FxSend.FRAME_BLOOD, 20);
            FxSend.shake(sl, c, 0.6f, 8, 20);
            FxSend.cracks(sl, BL, p.position().add(look.scale(2.5)).add(0, 0.05, 0), look, new Vec3(0, 1, 0), 1.5f, 30, false, p.getId());
            sound(sl, c, SoundEvents.PLAYER_ATTACK_STRONG, 1.3f, 0.5f);
            sound(sl, c, SoundEvents.GENERIC_EXPLODE, 0.6f, 1.2f);
            int len = full ? 16 : 10;
            for (int i = 1; i <= len; i++) {
                final int ii = i;
                AstaFx.later(i, () -> {
                    Vec3 g = p.position().add(look.scale(1.5 + ii));
                    for (int s = -1; s <= 1; s++) FxSend.spike(sl, BL, g.add(rt.scale(s * 0.6)), new Vec3(0, 1, 0).add(look.scale(0.3)).add(rt.scale(s * 0.3)).normalize(), 1.2f + (s == 0 ? 0.8f : 0) + ii * 0.04f, 0.18f, 20, 2, FxPacket.GROUND);
                    if (ii % 3 == 0) sound(sl, g, SoundEvents.POINTED_DRIPSTONE_LAND, 0.8f, 0.7f);
                    for (LivingEntity t : Kit.near(sl, p, g.add(0, 0.8, 0), 1.6)) {
                        hit(p, t, pow(p, power) * (full ? 1.2f : 0.9f), "hardblood_greatsword", 3);
                        t.setDeltaMovement(t.getDeltaMovement().add(0, 0.7, 0));
                        t.hurtMarked = true;
                    }
                });
            }
        });
    }

    // ================================================================== 11. Vampire Mode
    /** The bloodfiend wakes: red eyes, a cape of blood, lifesteal, longer reach, and red-mist steps (double-tap sneak). */
    public static void vampireMode(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        LimbusStatus.addBloodfeast(p, 100);
        LimbusStatus.startVampire(p, 600);
        LimbusStatus.addHardblood(p, 5);
        LimbusStatus.mist(sl, p.position().add(0, 1, 0));
        FxSend.ring(sl, BL, p.position().add(0, 0.1, 0), 0.3f, 4.5f, 0.15f, 14);
        FxSend.impactFrame(sl, p.position().add(0, 1.6, 0), 3, FxSend.FRAME_BLOOD, 16);
        sound(sl, p.position(), SoundEvents.BAT_TAKEOFF, 1.2f, 0.6f);
        sound(sl, p.position(), SoundEvents.WARDEN_HEARTBEAT, 1.4f, 0.9f);
        sound(sl, p.position(), SoundEvents.PHANTOM_AMBIENT, 0.6f, 0.6f);
        p.displayClientMessage(Component.literal("Vampire Mode - double-tap sneak to step through red mist.").withStyle(ChatFormatting.DARK_RED), true);
    }

    // ================================================================== 12. Grand Finale of La Manchaland
    /**
     * A staged finale. She leaps and the sky over the target turns to blood; blood is drawn up out of the ground in
     * streams and built, piece by piece, into a huge lance above her; a thin red line marks the target; she dives and
     * drives it down. The blow throws out three shockwaves and a geyser of blood; a field of blood thorns blooms outward
     * in waves; then every bleeding foe nearby bursts, and the blood rains back down. Spends all Bloodfeast for up to
     * double damage.
     */
    public static void grandFinale(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        LivingEntity aimed = aimed(p, 28, 25);
        final Vec3 land = aimed != null ? aimed.position() : Kit.aim(p, 22);
        int feast = LimbusStatus.spendBloodfeast(p, 999);
        float boost = 1f + feast / 999f;
        held(p, 1, 64, 26, 0, 1);
        LimbusStatus.addHardblood(p, 10);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        // 0: the leap, a circle of blood opening under her feet
        p.setDeltaMovement(0, 1.5, 0);
        p.hurtMarked = true;
        FxSend.ring(sl, BL, p.position().add(0, 0.05, 0), 0.3f, 3.2f, 0.12f, 14);
        LimbusStatus.splash(sl, p.position().add(0, 0.3, 0), 1.2f);
        sound(sl, p.position(), SoundEvents.ENDER_DRAGON_FLAP, 1.2f, 0.7f);
        sound(sl, p.position(), SoundEvents.WARDEN_HEARTBEAT, 1.4f, 0.8f);
        p.displayClientMessage(Component.literal("The Grand Finale of La Manchaland!").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), true);
        // 4: the sky over the target turns to blood - a vast dim dome, and a blood moon hanging over it
        AstaFx.later(4, () -> {
            FxSend.wall(sl, BL, land.add(0, 0.2, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1), 16f, 16f, -180, 180, 16f, 34, 0, 0);
            Vec3 moon = land.add(0, 24, 0);
            FxSend.ring(sl, BL, moon, 0.1f, 4.5f, 0.6f, 34);
            FxSend.ring(sl, GD, moon, 4.4f, 4.7f, 0.1f, 34);
            FxSend.ring(sl, BL, land.add(0, 0.06, 0), 13.5f, 14f, 0.05f, 30);
            sound(sl, land, SoundEvents.WARDEN_HEARTBEAT, 1.6f, 0.6f);
        });
        // 6-18: blood drawn up out of the ground in streams, built into the lance above her a section at a time
        for (int k = 0; k < 6; k++) {
            final int kk = k;
            AstaFx.later(6 + k * 2, () -> {
                if (!alive(p)) return;
                Vec3 top = p.position().add(0, 3.5, 0);
                for (int s = 0; s < 3; s++) {
                    double a = (kk * 3 + s) * 0.7;
                    Vec3 g = land.add(Math.cos(a) * 9, 0.1, Math.sin(a) * 9);
                    Vec3 bend = g.add(top).scale(0.5).add(0, 4, 0);
                    FxSend.trail(sl, BL, g, bend, 0.14f, 10);
                    FxSend.trail(sl, BL, bend, top, 0.14f, 10);
                }
                Vec3 dn = land.add(0, 1, 0).subtract(top).normalize();
                float part = 1.5f + kk * 1.3f;   // the lance grows a section with each surge
                FxSend.spike(sl, BL, top.subtract(dn.scale(1.2)), dn, part, 0.35f + kk * 0.1f, 26 - (6 + kk * 2) + 1, 0, FxPacket.HOLD);   // gone the moment she dives
                LimbusStatus.drip(sl, top.add(dn.scale(part * 0.5)), 1);
                sound(sl, top, SoundEvents.HONEY_BLOCK_PLACE, 1.2f, 0.5f + kk * 0.08f);
            });
        }
        // 20: the thorned guard sets in gold, and a thin red line marks the target
        AstaFx.later(20, () -> {
            if (!alive(p)) return;
            Vec3 top = p.position().add(0, 3.5, 0);
            Vec3 dn = land.add(0, 1, 0).subtract(top).normalize();
            Vec3 side = Math.abs(dn.y) > 0.98 ? new Vec3(1, 0, 0) : dn.cross(new Vec3(0, 1, 0)).normalize();
            FxSend.wall(sl, GD, top.subtract(dn.scale(1.2)), side, dn.cross(side).normalize(), 1.4f, 1.8f, -180, 180, 0.25f, 12, 0, 0);
            FxSend.trail(sl, BL, top, land.add(0, 1, 0), 0.04f, 8);
            harden(sl, top);
            sound(sl, top, SoundEvents.TRIDENT_RETURN, 1.4f, 0.5f);
        });
        // 26: the dive - she comes down with it
        AstaFx.later(26, () -> {
            if (!alive(p)) return;
            Vec3 from = p.position();
            Vec3 to = SpaceFx.safe(sl, land, p);
            if (to == null) to = land;
            FxSend.trail(sl, BL, from.add(0, 1, 0), to.add(0, 1, 0), 1.0f, 8);
            FxSend.spike(sl, BL, to.add(0, 9, 0), new Vec3(0, -1, 0), 9.5f, 0.9f, 16, 0, FxPacket.EXTEND);
            SpaceFx.move(p, to, p.getYRot());
            p.fallDistance = 0;
            FxSend.impactFrame(sl, to.add(0, 1, 0), 5, FxSend.FRAME_BLOOD, 32);
            FxSend.shake(sl, to, 1.1f, 16, 32);
            FxSend.cracks(sl, BL, to.add(0, 0.05, 0), new Vec3(1, 0, 0), new Vec3(0, 1, 0), 3.5f, 28, false, p.getId() * 3);
            sound(sl, to, SoundEvents.TRIDENT_THUNDER, 1.4f, 0.7f);
            sound(sl, to, SoundEvents.GENERIC_EXPLODE, 1.2f, 0.7f);
            if (aimed != null && aimed.isAlive()) {
                hit(p, aimed, pow(p, power) * boost, "grand_finale", 6);
                aimed.setDeltaMovement(Vec3.ZERO);
                aimed.hurtMarked = true;
            }
            for (LivingEntity t : Kit.near(sl, p, to, 6)) if (t != aimed) hit(p, t, pow(p, power) * 0.5f * boost, "grand_finale", 3);
            // a geyser of blood bursting up round the lance
            for (int k = 0; k < 10; k++) {
                double a = Math.PI * 2 * k / 10;
                Vec3 d = new Vec3(Math.cos(a) * 0.35, 1, Math.sin(a) * 0.35).normalize();
                FxSend.spike(sl, BL, to.add(Math.cos(a) * 0.8, 0, Math.sin(a) * 0.8), d, 3.5f + r.nextFloat() * 1.5f, 0.2f, 16, 0, FxPacket.EXTEND, 0, d.scale(0.15), 6);
            }
            LimbusStatus.splash(sl, to.add(0, 1.5, 0), 2f);
            // three shockwaves rolling out
            final Vec3 c = to;
            for (int w = 0; w < 3; w++) {
                final int ww = w;
                AstaFx.later(1 + w * 3, () -> {
                    FxSend.ring(sl, BL, c.add(0, 0.06 + ww * 0.02, 0), 0.5f, 6f + ww * 3f, 0.3f - ww * 0.06f, 14);
                    sound(sl, c, SoundEvents.GENERIC_EXPLODE, 0.6f, 0.9f + ww * 0.15f);
                });
            }
        });
        // 32-44: the thorn field blooms outward in waves
        for (int wv = 0; wv < 4; wv++) {
            final int wave = wv;
            AstaFx.later(32 + wv * 4, () -> {
                if (!alive(p)) return;
                double rad = 2.5 + wave * 2.5;
                int n = 8 + wave * 5;
                for (int k = 0; k < n; k++) {
                    double a = Math.PI * 2 * k / n + wave * 0.3;
                    Vec3 b = land.add(Math.cos(a) * rad, 0, Math.sin(a) * rad);
                    Vec3 d = new Vec3(Math.cos(a) * 0.45, 1, Math.sin(a) * 0.45).normalize();   // curving outward, like a briar
                    FxSend.spike(sl, BL, b, d, 1.4f + r.nextFloat() * 1.0f, 0.12f, 18, 3, FxPacket.GROUND);
                }
                sound(sl, land, SoundEvents.POINTED_DRIPSTONE_LAND, 1.2f, 0.6f + wave * 0.1f);
                for (LivingEntity t : Kit.near(sl, p, land, rad + 1.2)) {
                    if (t.position().distanceTo(land) < rad - 1.6) continue;
                    hit(p, t, pow(p, power) * 0.25f * boost, "grand_finale", 2);
                }
            });
        }
        // 50: every bleeding foe bursts, and the blood rains back down
        AstaFx.later(50, () -> {
            for (LivingEntity t : Kit.near(sl, p, land, 14)) {
                if (LimbusStatus.bleedOf(t) <= 0) continue;
                Vec3 b = t.position();
                for (int k = 0; k < 8; k++) {
                    double a = Math.PI * 2 * k / 8;
                    FxSend.spike(sl, BL, b.add(Math.cos(a) * 0.7, 0, Math.sin(a) * 0.7), new Vec3(Math.cos(a) * 0.4, 1, Math.sin(a) * 0.4).normalize(), 1.8f, 0.14f, 16, 3, FxPacket.GROUND);
                }
                LimbusStatus.splash(sl, mid(t), 1.4f);
                LimbusStatus.burstBleed(p, t, 1.5f * boost);
            }
            for (int k = 0; k < 14; k++) LimbusStatus.drip(sl, land.add(r.nextDouble(-8, 8), 5 + r.nextDouble(2), r.nextDouble(-8, 8)), r.nextInt(8));
            sound(sl, land, SoundEvents.HONEY_BLOCK_BREAK, 1.4f, 0.5f);
            sound(sl, land, SoundEvents.WARDEN_HEARTBEAT, 1.4f, 0.7f);
        });
    }

    // ================================================================== awakening (Surpassing Limits)
    static void awaken(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        LimbusStatus.addBloodfeast(p, 999);
        LimbusStatus.addHardblood(p, 30);
        LimbusStatus.mist(sl, p.position().add(0, 1, 0));
        for (int i = 0; i < 12; i++) {
            double a = Math.PI * 2 * i / 12;
            FxSend.spike(sl, BL, p.position().add(Math.cos(a) * 2.5, 0, Math.sin(a) * 2.5), new Vec3(0, 1, 0), 3f, 0.2f, 40, 4, FxPacket.GROUND);
        }
        sound(sl, p.position(), SoundEvents.WARDEN_HEARTBEAT, 1.5f, 0.7f);
    }
}
