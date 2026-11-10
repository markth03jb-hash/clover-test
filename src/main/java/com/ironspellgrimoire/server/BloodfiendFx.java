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
    static void held(ServerPlayer p, int kind, int life) {
        FxPacket pkt = new FxPacket();
        pkt.shape = 63; pkt.id = p.getId(); pkt.life = life; pkt.sweep = kind; pkt.palette = 0;
        AddonNetwork.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), pkt);
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
        held(p, 1, 22);
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
        held(p, 1, 26);
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
        held(p, 1, 18);
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
    /** A recurved bow of blood: one heavy piercing arrow; with 300+ Bloodfeast it splits into five; Vampire Mode makes them seek the bleeding. */
    public static void hardbloodBow(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        held(p, 2, 24);
        sound(sl, p.position(), SoundEvents.CROSSBOW_LOADING_MIDDLE, 1f, 0.7f);
        harden(sl, Kit.hand(p));
        LimbusStatus.addHardblood(p, 1);
        AstaFx.later(14, () -> {
            if (!alive(p)) return;
            boolean fan = LimbusStatus.spendBloodfeast(p, 0) >= 0 && LimbusStatus.bloodfeast(p) >= 300;
            if (fan) LimbusStatus.spendBloodfeast(p, 100);
            Vec3 look = p.getLookAngle();
            int count = fan ? 5 : 1;
            sound(sl, p.position(), SoundEvents.ARROW_SHOOT, 1.2f, 0.6f);
            sound(sl, p.position(), SoundEvents.CROSSBOW_SHOOT, 1f, 0.7f);
            for (int k = 0; k < count; k++) {
                double yaw = count == 1 ? 0 : (k - 2) * 0.12;
                Vec3 d = rotY(look, yaw).normalize();
                arrow(p, sl, p.getEyePosition().add(0, -0.2, 0), d, pow(p, power) * (count == 1 ? 1f : 0.6f), 0);
            }
        });
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
        Vec3 to = clip(sl, p, from, from.add(d.scale(3.2)));
        FxSend.spike(sl, BL, from, d, 1.1f, 0.06f, 3, 0, FxPacket.POP);
        FxSend.trail(sl, BL, from, to, 0.08f, 6);
        boolean stop = to.distanceTo(from) < 3.1;
        for (LivingEntity t : Kit.along(sl, p, from, to, 0.9)) hit(p, t, dmg, "hardblood_bow", 2);
        if (stop) { LimbusStatus.splash(sl, to, 0.6f); return; }
        final Vec3 nd = d;
        AstaFx.later(1, () -> arrow(p, sl, to, nd, dmg, step + 1));
    }

    // ================================================================== 8. Twin Blood Blades
    /** Two curved sabres of blood: a fast six-cut spinning combo, left and right, ending in a cross-cut that bursts Bleed. */
    public static void twinBlades(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        held(p, 3, 34);
        LimbusStatus.addHardblood(p, 2);
        harden(sl, Kit.hand(p));
        int[] at = {6, 10, 14, 18, 22, 28};
        for (int i = 0; i < 6; i++) {
            final int ii = i;
            AstaFx.later(at[i], () -> {
                if (!alive(p)) return;
                Vec3 look = flat(p.getLookAngle()), rt = rightOf(look);
                Vec3 c = p.position().add(0, 1.1, 0).add(look.scale(1.3));
                boolean last = ii == 5;
                if (last) {   // the cross
                    Vec3 u1 = rt.add(0, 0.8, 0).normalize(), u2 = rt.scale(-1).add(0, 0.8, 0).normalize();
                    FxSend.slash(sl, BL, c, look, u1, 1.8f, 3.4f, 8, 0.2f, 1.2f);
                    FxSend.slash(sl, BL, c, look, u2, 1.8f, 3.4f, 8, 0.2f, 1.2f);
                    FxSend.impactFrame(sl, c.add(look.scale(1.5)), 3, FxSend.FRAME_BLOOD, 18);
                    sound(sl, c, SoundEvents.PLAYER_ATTACK_CRIT, 1.2f, 0.7f);
                } else {
                    double tilt = (ii % 2 == 0 ? 1 : -1) * (0.3 + 0.15 * ii);
                    Vec3 u = rt.scale(ii % 2 == 0 ? 1 : -1).add(0, tilt, 0).normalize();
                    FxSend.slash(sl, BL, c, look, u, 1.4f, 2.6f, 6, 0.15f, 0.8f);
                }
                sound(sl, c, SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 1.1f + ii * 0.05f);
                for (LivingEntity t : Kit.near(sl, p, c.add(look.scale(0.8)), last ? 3.2 : 2.6)) {
                    hit(p, t, pow(p, power) * (last ? 0.5f : 0.22f), "twin_blood_blades", last ? 2 : 1);
                    if (last) LimbusStatus.burstBleed(p, t, 0.6f);
                }
            });
        }
    }

    // ================================================================== 9. Blood Fist Barrage
    /** Fists sheathed in hardblood: a 1.5 s barrage of afterimage punches, then a heavy blow that throws them. */
    public static void fistBarrage(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        held(p, 4, 40);
        LimbusStatus.addHardblood(p, 2);
        harden(sl, Kit.hand(p));
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < 15; i++) {
            final int ii = i;
            AstaFx.later(6 + i * 2, () -> {
                if (!alive(p)) return;
                Vec3 look = p.getLookAngle(), rt = rightOf(flat(look));
                Vec3 o = p.getEyePosition().add(0, -0.35, 0).add(rt.scale(r.nextDouble(-0.6, 0.6))).add(0, r.nextDouble(-0.4, 0.4), 0);
                // an afterimage fist: a short thick blood spike punched forward with a ring at its knuckles
                FxSend.spike(sl, BL, o.add(look.scale(0.6)), look, 1.0f, 0.2f, 4, 0, FxPacket.EXTEND);
                FxSend.ring(sl, BL, o.add(look.scale(1.8)), 0.05f, 0.45f, 0.04f, 3);
                if (ii % 2 == 0) sound(sl, o, SoundEvents.PLAYER_ATTACK_WEAK, 0.8f, 1.2f + r.nextFloat() * 0.3f);
                for (LivingEntity t : Kit.near(sl, p, p.getEyePosition().add(look.scale(2.2)), 1.8)) {
                    AstaFx.damage(p, t, pow(p, power) * 0.1f, "blood_fist_barrage");
                    t.invulnerableTime = 0;
                    if (ii % 3 == 0) LimbusStatus.bleed(p, t, 1);
                    if (LimbusStatus.vampire(p)) p.heal(0.25f);
                }
            });
        }
        AstaFx.later(38, () -> {
            if (!alive(p)) return;
            Vec3 look = p.getLookAngle(), c = p.getEyePosition().add(look.scale(2.0));
            FxSend.spike(sl, BL, p.getEyePosition().add(look.scale(0.5)), look, 2.2f, 0.5f, 6, 0, FxPacket.EXTEND);
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
        held(p, 5, 30);
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
    /** Leap; the sky turns crimson; a huge hardblood lance forms above you; you dive and pin the target, and every bleeding foe's Bleed bursts. */
    public static void grandFinale(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        LivingEntity aimed = aimed(p, 28, 25);
        Vec3 land = aimed != null ? aimed.position() : Kit.aim(p, 22);
        int feast = LimbusStatus.spendBloodfeast(p, 999);
        float boost = 1f + feast / 999f;
        held(p, 1, 60);
        LimbusStatus.addHardblood(p, 10);
        // the leap
        p.setDeltaMovement(0, 1.6, 0);
        p.hurtMarked = true;
        sound(sl, p.position(), SoundEvents.ENDER_DRAGON_FLAP, 1.2f, 0.7f);
        sound(sl, p.position(), SoundEvents.HORSE_ANGRY, 1f, 0.8f);
        FxSend.ring(sl, BL, p.position().add(0, 0.1, 0), 0.3f, 4f, 0.15f, 12);
        p.displayClientMessage(Component.literal("The Grand Finale of La Manchaland!").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), true);
        // the sky tints crimson: a vast dim dome of blood over the place she will land
        FxSend.wall(sl, BL, land.add(0, 0.2, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1), 18f, 18f, -180, 180, 14f, 50, 0, 0);
        AstaFx.later(10, () -> {
            if (!alive(p)) return;
            Vec3 top = p.position().add(0, 3.5, 0);
            Vec3 dn = land.add(0, 1, 0).subtract(top).normalize();
            // the lance takes shape overhead: layered spikes and a great gold-rimmed vamplate
            FxSend.spike(sl, BL, top.subtract(dn.scale(1.0)), dn, 9f, 0.9f, 22, 6, FxPacket.EXTEND);
            FxSend.spike(sl, BL, top, dn, 7f, 0.5f, 22, 6, FxPacket.EXTEND);
            FxSend.wall(sl, GD, top.subtract(dn.scale(0.6)), dn.cross(new Vec3(0, 1, 0)).lengthSqr() < 1e-3 ? new Vec3(1, 0, 0) : dn.cross(new Vec3(0, 1, 0)).normalize(),
                    dn.cross(dn.cross(new Vec3(0, 1, 0)).lengthSqr() < 1e-3 ? new Vec3(1, 0, 0) : dn.cross(new Vec3(0, 1, 0)).normalize()).normalize(), 1.8f, 2.2f, -180, 180, 0.3f, 22, 0, 0);
            harden(sl, top);
            sound(sl, top, SoundEvents.TRIDENT_RETURN, 1.4f, 0.5f);
        });
        AstaFx.later(24, () -> {
            if (!alive(p)) return;
            Vec3 from = p.position();
            Vec3 to = SpaceFx.safe(sl, land, p);
            if (to == null) to = land;
            FxSend.trail(sl, BL, from.add(0, 1, 0), to.add(0, 1, 0), 1.2f, 10);
            FxSend.spike(sl, BL, to.add(0, 6, 0), new Vec3(0, -1, 0), 7f, 0.8f, 30, 0, FxPacket.EXTEND);
            SpaceFx.move(p, to, p.getYRot());
            p.fallDistance = 0;
            FxSend.impactFrame(sl, to.add(0, 1, 0), 4, FxSend.FRAME_BLOOD, 30);
            FxSend.shake(sl, to, 1.0f, 14, 30);
            FxSend.ring(sl, BL, to.add(0, 0.05, 0), 0.5f, 9f, 0.25f, 16);
            FxSend.ring(sl, GD, to.add(0, 0.08, 0), 0.5f, 6f, 0.1f, 12);
            FxSend.cracks(sl, BL, to.add(0, 0.05, 0), new Vec3(1, 0, 0), new Vec3(0, 1, 0), 3.5f, 50, false, p.getId() * 3);
            sound(sl, to, SoundEvents.TRIDENT_THUNDER, 1.4f, 0.7f);
            sound(sl, to, SoundEvents.GENERIC_EXPLODE, 1f, 0.8f);
            if (aimed != null && aimed.isAlive()) {
                hit(p, aimed, pow(p, power) * boost, "grand_finale", 6);
                aimed.setDeltaMovement(Vec3.ZERO);
                aimed.hurtMarked = true;
            }
            for (LivingEntity t : Kit.near(sl, p, to, 6)) if (t != aimed) hit(p, t, pow(p, power) * 0.5f * boost, "grand_finale", 3);
            // every bleeding foe nearby: their Bleed bursts as a crown of blood spikes
            final Vec3 tt = to;
            AstaFx.later(8, () -> {
                for (LivingEntity t : Kit.near(sl, p, tt, 14)) {
                    if (LimbusStatus.bleedOf(t) <= 0) continue;
                    Vec3 b = t.position();
                    for (int k = 0; k < 8; k++) {
                        double a = Math.PI * 2 * k / 8;
                        FxSend.spike(sl, BL, b.add(Math.cos(a) * 0.7, 0, Math.sin(a) * 0.7), new Vec3(Math.cos(a) * 0.4, 1, Math.sin(a) * 0.4).normalize(), 1.8f, 0.14f, 24, 3, FxPacket.GROUND);
                    }
                    LimbusStatus.burstBleed(p, t, 1.5f * boost);
                }
                sound(sl, tt, SoundEvents.HONEY_BLOCK_BREAK, 1.4f, 0.5f);
                sound(sl, tt, SoundEvents.WARDEN_HEARTBEAT, 1.4f, 0.7f);
            });
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
