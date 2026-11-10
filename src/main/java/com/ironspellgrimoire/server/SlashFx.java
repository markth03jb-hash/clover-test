package com.ironspellgrimoire.server;

import static com.ironspellgrimoire.server.FxUtil.*;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Jack the Ripper's Slash Magic - the eight spells of the Slash Grimoire. Thin, fast and cruel: lines of blood-red and
 * white that appear a heartbeat before the cut lands, then a blur of Jack himself and nothing but wounds left behind.
 *
 *  1 Phantom Slash     - a long, hair-thin cut that crosses everything ahead in one instant
 *  2 Crescent Cut      - a great crescent of edge flies out and cuts through all in its way
 *  3 Shadow Step Slash - you are behind your mark before it knows, and it is already cut
 *  4 Thousand Cuts     - a storm of tiny cuts at one spot, faster than the eye
 *  5 Cross Reaper      - an X drawn in the air ahead of you, then it tears apart
 *  6 Bleeding Edge     - every foe ahead is marked with a thin cut that opens two seconds later
 *  7 Ripper Frenzy     - for 15 s you are a blur: far faster, and cuts keep opening round you
 *  8 Bloody Massacre   - you cut through every enemy near you in turn, then it all lands at once
 *
 * Passive "Bleed": the cut keeps bleeding, and the nearly dead take 30% more.
 */
public final class SlashFx {
    private SlashFx() {}
    private static final int R = Moves.RIPPER, S = Fx.SILVER, D = Fx.CRIMSON;

    /** One hair-thin line of edge from a to b: red outer, white core. */
    static void line(ServerLevel sl, Vec3 a, Vec3 b, float w, int life) {
        FxSend.trail(sl, R, a, b, w, life);
        FxSend.trail(sl, S, a, b, w * 0.3f, Math.max(2, life - 1));
    }

    /** A cut across a point, along a direction, of the given half-length. */
    static void cut(ServerLevel sl, Vec3 c, Vec3 along, float half, float w, int life) {
        Vec3 a = along.normalize();
        line(sl, c.subtract(a.scale(half)), c.add(a.scale(half)), w, life);
    }

    private static Vec3 tilt(Vec3 rt, double k) { return rt.add(0, k, 0).normalize(); }

    // ================================================================== 1. Phantom Slash
    public static void phantomSlash(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        sound(sl, p.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.6f, 1.7f);
        AstaFx.later(3, () -> {   // the line appears first...
            if (!alive(p)) return;
            Vec3 dir = p.getLookAngle(), rt = rightOf(dir), eye = p.getEyePosition().add(0, -0.3, 0);
            Vec3 end = clip(sl, p, eye, eye.add(dir.scale(22)));
            Vec3 mid = eye.add(end.subtract(eye).scale(0.5));
            cut(sl, eye.add(dir.scale(3)), tilt(rt, 0.5), 1.4f, 0.04f, 3);
            AstaFx.later(3, () -> {   // ...and the cut lands
                if (!alive(p)) return;
                for (int i = 0; i < 4; i++) {
                    Vec3 q = eye.add(end.subtract(eye).scale(0.25 + i * 0.22));
                    cut(sl, q, tilt(rt, 0.5), 2.2f - i * 0.2f, 0.07f, 6);
                }
                FxSend.beam(sl, R, eye, end, 0.12f, 6);
                FxSend.beam(sl, S, eye, end, 0.04f, 5);
                for (LivingEntity t : Kit.along(sl, p, eye, end, 1.3)) {
                    Moves.hit(p, t, power * 1.2f, "phantom_slash", R);
                    Kit.push(t, dir, 0.4, 0.1);
                    cut(sl, mid(t), tilt(rt, 0.5), 1.0f, 0.08f, 6);
                    Fx.impact(sl, R, mid(t), 1.0f);
                }
                Fx.impact(sl, R, end, 1.0f);
                FxSend.shake(sl, mid, 0.15f, 3, 20);
                sound(sl, mid, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, 1.6f);
                sound(sl, mid, SoundEvents.PLAYER_ATTACK_CRIT, 0.6f, 1.8f);
            });
        });
    }

    // ================================================================== 2. Crescent Cut
    public static void crescentCut(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        Vec3 dir = p.getLookAngle(), rt = rightOf(dir), up = new Vec3(0, 1, 0);
        Vec3 start = p.getEyePosition().add(dir.scale(1.5)).add(0, -0.4, 0);
        Set<UUID> hit = new HashSet<>();
        sound(sl, p.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, 0.8f);
        for (int k = 0; k < 11; k++) {
            final int kk = k;
            AstaFx.later(4 + k, () -> {
                if (!alive(p)) return;
                Vec3 c = start.add(dir.scale(kk * 2.3));
                FxSend.crescent(sl, R, c, dir, up, 2.8f, 1.5f, 3, 0.0f);
                FxSend.crescent(sl, S, c, dir, up, 2.5f, 0.5f, 2, 0.0f);
                if (kk % 2 == 0) cut(sl, c, rt, 2.8f, 0.05f, 3);
                for (LivingEntity t : Kit.near(sl, p, c, 2.6)) {
                    if (!hit.add(t.getUUID())) continue;
                    Moves.hit(p, t, power * 1.2f, "crescent_cut", R);
                    Kit.push(t, dir, 0.8, 0.3);
                    Fx.impact(sl, R, mid(t), 1.2f);
                }
            });
        }
    }

    // ================================================================== 3. Shadow Step Slash
    public static void shadowStepSlash(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        LivingEntity t = aimed(p, 20, 20);
        Vec3 from = p.position();
        sound(sl, from, SoundEvents.PLAYER_ATTACK_SWEEP, 0.7f, 1.8f);
        if (t == null) {   // nothing to step to: a short blur forward with a cut at the end
            Moves.dash(p, R, p.getLookAngle(), 8, 3, (at, i) -> {
                if (i == 3) {
                    Vec3 rt = rightOf(flat(p.getLookAngle()));
                    cut(sl, at.add(0, 1.2, 0).add(flat(p.getLookAngle()).scale(1.2)), tilt(rt, 0.7), 1.6f, 0.08f, 6);
                    for (LivingEntity e : Kit.near(sl, p, at.add(0, 1, 0), 3)) Moves.hit(p, e, power, "shadow_step_slash", R);
                }
            });
            return;
        }
        Vec3 behind = SpaceFx.safe(sl, t.position().subtract(flat(t.getLookAngle()).scale(1.4)), p);
        if (behind == null) return;
        AstaFx.later(3, () -> {
            if (!alive(p) || !t.isAlive()) return;
            Vec3 face = mid(t).subtract(behind);
            float yaw = (float) Math.toDegrees(Math.atan2(-face.x, face.z));
            Vec3 a = p.position().add(0, 1, 0);
            SpaceFx.move(p, behind, yaw);
            line(sl, a, behind.add(0, 1, 0), 0.1f, 8);   // the blur between
            Vec3 rt = rightOf(flat(face));
            Vec3 m = mid(t);
            cut(sl, m, tilt(rt, 0.9), 1.6f, 0.09f, 7);
            cut(sl, m, tilt(rt, -0.9), 1.6f, 0.09f, 7);
            Moves.hit(p, t, power * 1.5f, "shadow_step_slash", R);
            Fx.impact(sl, R, m, 1.5f);
            FxSend.impactFrame(sl, m, 2, FxSend.FRAME_JACK, 24);
            sound(sl, behind, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, 1.4f);
            sound(sl, behind, SoundEvents.PLAYER_ATTACK_CRIT, 0.8f, 1.6f);
        });
    }

    // ================================================================== 4. Thousand Cuts
    public static void thousandCuts(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        LivingEntity t = aimed(p, 16, 25);
        Vec3 c = t != null ? mid(t) : Kit.aim(p, 9).add(0, 1, 0);
        Random rnd = new Random(p.getId() * 31L + p.tickCount);
        sound(sl, p.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.7f, 1.9f);
        for (int i = 0; i < 18; i++) {
            final int ii = i;
            AstaFx.later(3 + i, () -> {
                if (!alive(p)) return;
                Vec3 v = new Vec3(rnd.nextDouble() - 0.5, rnd.nextDouble() - 0.5, rnd.nextDouble() - 0.5);
                if (v.lengthSqr() < 0.05) v = new Vec3(1, 0.3, 0);
                Vec3 off = new Vec3(rnd.nextDouble() - 0.5, rnd.nextDouble() - 0.5, rnd.nextDouble() - 0.5).scale(1.4);
                cut(sl, c.add(off), v, 0.9f + rnd.nextFloat() * 0.9f, 0.05f, 4);
                if (ii % 3 == 0) for (LivingEntity e : Kit.near(sl, p, c, 3.2)) {
                    Moves.hit(p, e, power * 0.3f, "thousand_cuts", R);
                    Fx.spark(sl, R, mid(e), 0.7f);
                }
                sound(sl, c, SoundEvents.PLAYER_ATTACK_SWEEP, 0.35f, 1.6f + (ii % 4) * 0.12f);
            });
        }
        AstaFx.later(23, () -> {   // one last, long cut that finishes it
            if (!alive(p)) return;
            Vec3 d = new Vec3(1, 0.9, 0.1);
            cut(sl, c, d, 3.2f, 0.12f, 8);
            for (LivingEntity e : Kit.near(sl, p, c, 3.4)) {
                Moves.hit(p, e, power * 0.9f, "thousand_cuts", R);
                Kit.push(e, mid(e).subtract(c), 0.5, 0.3);
            }
            Fx.impact(sl, R, c, 1.8f);
            sound(sl, c, SoundEvents.PLAYER_ATTACK_CRIT, 1.0f, 1.3f);
        });
    }

    // ================================================================== 5. Cross Reaper
    public static void crossReaper(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        Vec3 dir = flat(p.getLookAngle()), rt = rightOf(dir);
        Vec3 c = p.position().add(dir.scale(5.5)).add(0, 1.6, 0);
        sound(sl, p.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.8f, 1.0f);
        AstaFx.later(3, () -> cut(sl, c, tilt(rt, 1.0), 4.2f, 0.07f, 8));   // the first line
        AstaFx.later(7, () -> cut(sl, c, tilt(rt, -1.0), 4.2f, 0.07f, 8));  // the second crosses it
        AstaFx.later(12, () -> {   // the X tears apart
            if (!alive(p)) return;
            for (int s = -1; s <= 1; s += 2) for (int u = -1; u <= 1; u += 2) {
                Vec3 e = c.add(rt.scale(s * 4.0)).add(0, u * 4.0, 0);
                FxSend.trail(sl, R, c, e, 0.3f, 8);
            }
            FxSend.slash(sl, R, c, dir, tilt(rt, 1.0), 4.5f, 0.8f, 6, 0.3f);
            FxSend.slash(sl, R, c, dir, tilt(rt, -1.0), 4.5f, 0.8f, 6, 0.3f);
            boolean any = false;
            for (LivingEntity t : Kit.near(sl, p, c.add(0, -1.2, 0), 5.2)) {
                Moves.hit(p, t, power * 1.5f, "cross_reaper", R);
                Kit.push(t, dir, 1.0, 0.45);
                any = true;
            }
            if (any) FxSend.impactFrame(sl, c, 3, FxSend.FRAME_JACK, 32);
            Fx.flash(sl, c, 2.4f);
            FxSend.ring(sl, R, c.add(0, -1.5, 0), 0.4f, 6.5f, 0.12f, 9);
            FxSend.shake(sl, c, 0.6f, 8, 30);
            sound(sl, c, SoundEvents.GENERIC_EXPLODE, 0.9f, 1.2f);
            sound(sl, c, SoundEvents.PLAYER_ATTACK_CRIT, 1.0f, 1.0f);
        });
    }

    // ================================================================== 6. Bleeding Edge
    public static void bleedingEdge(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        Vec3 look = p.getLookAngle(), eye = p.getEyePosition();
        List<LivingEntity> marked = new ArrayList<>();
        for (LivingEntity t : Kit.near(sl, p, eye.add(look.scale(7)), 8)) {
            Vec3 to = mid(t).subtract(eye).normalize();
            if (to.dot(look) > 0.5 && marked.size() < 6) marked.add(t);
        }
        sound(sl, p.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.7f, 1.5f);
        if (marked.isEmpty()) { Vec3 c = Kit.aim(p, 8); cut(sl, c, tilt(rightOf(look), 0.4), 1.5f, 0.06f, 6); return; }
        for (int i = 0; i < marked.size(); i++) {
            final LivingEntity t = marked.get(i);
            AstaFx.later(3 + i * 2, () -> {
                if (!t.isAlive()) return;
                Vec3 rt = rightOf(flat(mid(t).subtract(p.getEyePosition())));
                cut(sl, mid(t), tilt(rt, 0.6), 1.2f, 0.07f, 8);
                Moves.hit(p, t, power * 0.4f, "bleeding_edge", R);
                sound(sl, mid(t), SoundEvents.PLAYER_ATTACK_SWEEP, 0.6f, 1.8f);
            });
            for (int w = 1; w <= 3; w++) {   // the wound stays visible as it deepens
                final int ww = w;
                AstaFx.later(8 + w * 9, () -> { if (t.isAlive()) cut(sl, mid(t), tilt(rightOf(flat(mid(t).subtract(p.getEyePosition()))), 0.6), 0.5f + ww * 0.4f, 0.05f, 9); });
            }
            AstaFx.later(8 + 36, () -> {   // ...and opens
                if (!t.isAlive() || !alive(p)) return;
                Vec3 m = mid(t);
                Moves.hit(p, t, power * 1.7f, "bleeding_edge", R);
                for (int k = 0; k < 4; k++) {
                    double a = k * Math.PI / 2 + 0.4;
                    line(sl, m, m.add(Math.cos(a) * 1.6, Math.sin(a * 2) * 0.8, Math.sin(a) * 1.6), 0.1f, 8);
                }
                Fx.impact(sl, R, m, 1.6f);
                FxSend.ring(sl, R, m, 0.2f, 2.2f, 0.06f, 7);
                sound(sl, m, SoundEvents.PLAYER_ATTACK_CRIT, 0.9f, 1.3f);
            });
        }
    }

    // ================================================================== 7. Ripper Frenzy
    public static void ripperFrenzy(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        Moves.stance(p, 0, 0, 0.25, 0, 0.6, 300);
        FxSend.ring(sl, R, p.position().add(0, 0.05, 0), 0.3f, 3.4f, 0.08f, 8);
        for (int k = 0; k < 10; k++) {   // the room is cut to ribbons around you
            final int kk = k;
            AstaFx.later(k + 1, () -> {
                if (!alive(p)) return;
                Vec3 c = p.position().add(0, 1.0, 0);
                double a = kk * 2.4;
                cut(sl, c.add(Math.cos(a) * 2.2, Math.sin(a * 1.3) * 0.8, Math.sin(a) * 2.2), new Vec3(Math.cos(a + 1.2), 0.5, Math.sin(a + 1.2)), 1.5f, 0.06f, 5);
            });
        }
        AstaFx.later(11, () -> { if (alive(p)) Fx.flash(sl, p.position().add(0, 1, 0), 1.6f); });
        sound(sl, p.position(), SoundEvents.PLAYER_ATTACK_CRIT, 1.0f, 1.8f);
        sound(sl, p.position(), SoundEvents.WARDEN_SONIC_CHARGE, 0.6f, 1.6f);
        Moves.mode(p, "ripper_frenzy", 300, 6, pl -> {
            ServerLevel s2 = pl.serverLevel();
            long now = s2.getGameTime();
            Random rnd = new Random(now * 7919L + pl.getId());
            Vec3 c = pl.position().add(0, 1.0, 0);
            double a = rnd.nextDouble() * Math.PI * 2;
            cut(s2, c.add(Math.cos(a) * 1.8, rnd.nextDouble() - 0.5, Math.sin(a) * 1.8), new Vec3(Math.cos(a + 1.5), rnd.nextDouble() - 0.5, Math.sin(a + 1.5)), 1.3f, 0.05f, 5);
            if (now % 12 == 0) for (LivingEntity e : Kit.near(s2, pl, c, 3.0)) {
                Moves.hit(pl, e, power * 0.35f, "ripper_frenzy", R);
                cut(s2, mid(e), new Vec3(1, rnd.nextDouble() - 0.5, rnd.nextDouble() - 0.5), 0.9f, 0.06f, 5);
            }
        });
    }

    // ================================================================== 8. Bloody Massacre
    /**
     * You cut through every enemy within 14 blocks, one after another - a blur, an X, a step - and then return to where you
     * began. Only then does it all land: every cut opens at once.
     * Surpassing Limits: Endless Night of the Ripper.
     */
    public static void bloodyMassacre(ServerPlayer p, float power) {
        if (LimitBreak.active(p)) {
            if (Moves.inMode(p, "ripper_frenzy")) ripperHorizon(p, power);   // Frenzy running + Surpassing Limits: the ultimate
            else AwakenFx.endlessNight(p, power);
            return;
        }
        ServerLevel sl = p.serverLevel();
        Vec3 origin = p.position();
        List<LivingEntity> foes = new ArrayList<>(Kit.near(sl, p, origin, 14));
        if (foes.size() > 8) foes = foes.subList(0, 8);
        final List<LivingEntity> targets = foes;
        sound(sl, origin, SoundEvents.WARDEN_SONIC_CHARGE, 1.0f, 1.4f);
        FxSend.ring(sl, R, origin.add(0, 0.05, 0), 0.3f, 3.2f, 0.1f, 8);
        int n = targets.size();
        if (n == 0) { Moves.dash(p, R, p.getLookAngle(), 14, 5, (at, i) -> cut(sl, at.add(0, 1.2, 0), new Vec3(1, 0.5, 0.3), 1.8f, 0.09f, 6)); return; }
        for (int i = 0; i < n; i++) {
            final LivingEntity t = targets.get(i);
            final int ii = i;
            AstaFx.later(6 + i * 3, () -> {
                if (!alive(p) || !t.isAlive()) return;
                Vec3 dest = SpaceFx.safe(sl, t.position().add(Math.cos(ii * 2.4) * 1.6, 0, Math.sin(ii * 2.4) * 1.6), p);
                if (dest == null) return;
                Vec3 a = p.position().add(0, 1, 0);
                Vec3 face = mid(t).subtract(dest);
                SpaceFx.move(p, dest, (float) Math.toDegrees(Math.atan2(-face.x, face.z)));
                line(sl, a, dest.add(0, 1, 0), 0.12f, 10);
                Vec3 m = mid(t);
                cut(sl, m, new Vec3(1, 0.8, 0.2), 1.5f, 0.08f, 24);   // these stay until the end
                cut(sl, m, new Vec3(1, -0.8, -0.2), 1.5f, 0.08f, 24);
                Moves.hit(p, t, power * 0.6f, "bloody_massacre", R);
                sound(sl, dest, SoundEvents.PLAYER_ATTACK_SWEEP, 0.8f, 1.3f + ii * 0.05f);
            });
        }
        int end = 6 + n * 3 + 4;
        AstaFx.later(end, () -> {
            if (!alive(p)) return;
            Vec3 back = SpaceFx.safe(sl, origin, p);
            if (back != null) SpaceFx.move(p, back, p.getYRot());
            boolean any = false;
            for (LivingEntity t : targets) {
                if (!t.isAlive()) continue;
                Vec3 m = mid(t);
                Moves.hit(p, t, power * 1.7f, "bloody_massacre", R);
                for (int k = 0; k < 5; k++) {
                    double a = k * Math.PI * 2 / 5 + 0.3;
                    line(sl, m, m.add(Math.cos(a) * 2.0, Math.sin(a * 1.7), Math.sin(a) * 2.0), 0.14f, 9);
                }
                Fx.impact(sl, R, m, 1.8f);
                any = true;
            }
            if (any) FxSend.impactFrame(sl, origin.add(0, 1, 0), 5, FxSend.FRAME_JACK, 44);
            Fx.flash(sl, origin.add(0, 1, 0), 3.0f);
            FxSend.ring(sl, R, origin.add(0, 0.1, 0), 0.4f, 12f, 0.25f, 12);
            FxSend.shake(sl, origin, 0.9f, 12, 44);
            sound(sl, origin, SoundEvents.PLAYER_ATTACK_CRIT, 1.4f, 0.8f);
            sound(sl, origin, SoundEvents.GENERIC_EXPLODE, 1.0f, 1.0f);
        });
    }

    // ================================================================== Ultimate: Ripper's Horizon
    /**
     * Bloody Massacre cast while Ripper Frenzy is running AND in Surpassing Limits: Jack's ultimate, staged like Yami's
     * Event Horizon but in green. Both arms go up and HOLD while hair-thin green cuts pour into the raised edge, the
     * ground splits and space starts to fracture; then one cut - a single colossal green slash stands upright, rolls
     * forward and grows, splitting space along its whole path with green rifts left hanging where it passed.
     */
    private static final int HZ_CHARGE = 56, HZ_CUT = 57;

    private static void green(ServerLevel sl, Vec3 a, Vec3 b, float w, int life) {
        FxSend.trail(sl, Fx.TREE, a, b, w, life);
        FxSend.trail(sl, S, a, b, w * 0.3f, Math.max(2, life - 1));
    }

    static void ripperHorizon(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        final int GR = Fx.TREE;
        final float dmg = power * 3.2f;
        final int seed0 = (int) (sl.getGameTime() * 31 + p.getId());
        AnimSend.play(p, AnimSend.RIPPER_HORIZON);
        p.displayClientMessage(net.minecraft.network.chat.Component.literal("Ripper's Horizon")
                .withStyle(net.minecraft.ChatFormatting.GREEN, net.minecraft.ChatFormatting.BOLD), true);
        TimedModifiers.apply(p, net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED, TimedModifiers.HORIZON_CHARGE,
                "Ripper's Horizon charge", -0.8, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_TOTAL, HZ_CUT + 4);
        sound(sl, p.position(), SoundEvents.WARDEN_SONIC_CHARGE, 1f, 0.6f);
        sound(sl, p.position(), SoundEvents.BEACON_ACTIVATE, 0.8f, 0.7f);

        // ---------------------------------------------------------------- the charge
        for (int t = 6; t < HZ_CHARGE; t += 2) {
            final int tt = t;
            AstaFx.later(t, () -> {
                if (!alive(p)) return;
                float k = (tt - 6) / (float) (HZ_CHARGE - 6);
                double yr = Math.toRadians(p.getYRot());
                Vec3 fw = new Vec3(-Math.sin(yr), 0, Math.cos(yr));
                Vec3 g = FxLib.floor(sl, p.position()).add(0, 0.05, 0);
                Vec3 hilt = p.position().add(0, 0.9, 0).subtract(fw.scale(0.25)), tip = g.add(fw.scale(1.8 + 1.2 * k));
                FxSend.trail(sl, GR, hilt, tip, 0.3f + 0.55f * k, 3);                 // the edge held low, wrapped in green, its point on the ground
                FxSend.trail(sl, S, hilt, tip, 0.04f + 0.04f * k, 3);
                if (tt % 4 == 0) {   // hair-thin green cuts drawn in from all round, converging on the point
                    for (int i = 0; i < 2; i++) {
                        double a = tt * 0.7 + i * Math.PI;
                        Vec3 from = tip.add(Math.cos(a) * (4.5 + k * 2), 0.3 + Math.abs(Math.sin(tt + i)) * 1.6, Math.sin(a) * (4.5 + k * 2));
                        green(sl, from, tip.add(from.subtract(tip).scale(0.25)), 0.09f + k * 0.05f, 4);
                    }
                }
                if (tt % 8 == 0) FxSend.ring(sl, GR, p.position().add(0, 0.05, 0), 7f - k * 4f, 0.3f + k * 0.3f, 0.12f, 7);
                if (tt % 6 == 0) FxSend.shake(sl, p.position(), 0.15f + k * 0.3f, 6, 18);
            });
        }

        // ---------------------------------------------------------------- the cut
        AstaFx.later(HZ_CUT, () -> {
            TimedModifiers.remove(p, net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED, TimedModifiers.HORIZON_CHARGE);
            if (!alive(p)) return;
            Vec3 look = p.getLookAngle(), eye = p.getEyePosition();
            final boolean aimed = Math.abs(look.y) > 0.12;
            Vec3 flatDir = new Vec3(look.x, 0, look.z).lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : new Vec3(look.x, 0, look.z).normalize();
            Vec3 dir = aimed ? look.normalize() : flatDir;
            Vec3 start = aimed ? eye.add(0, -0.3, 0) : FxLib.floor(sl, p.position());
            Vec3 rt = rightOf(dir), up = aimed ? rt.cross(dir).normalize() : new Vec3(0, 1, 0);
            final double reach = 80;
            final float speed = 3.6f;
            Vec3 end = start.add(dir.scale(reach));
            final Vec3 o = start.add(dir.scale(1.5)).add(aimed ? Vec3.ZERO : up.scale(1.2));
            FxSend.shake(sl, p.position(), 1.5f, 22, 70);
            Fx.flash(sl, o, 3.0f);
            sound(sl, p.position(), SoundEvents.PLAYER_ATTACK_CRIT, 1.6f, 0.5f);
            sound(sl, p.position(), SoundEvents.GENERIC_EXPLODE, 1.2f, 0.6f);

            // everything in the colossal band is cut as the blade passes
            final boolean[] framed = {false};
            Vec3 midp = start.add(dir.scale(reach * 0.5));
            for (LivingEntity t : Kit.near(sl, p, midp, reach * 0.5 + 14)) {
                Vec3 m = mid(t), seg = end.subtract(start);
                double f = Math.max(0, Math.min(1, m.subtract(start).dot(seg) / seg.lengthSqr()));
                Vec3 on = start.add(seg.scale(f)), off = m.subtract(on);
                double along = on.distanceTo(start);
                if (Math.abs(off.dot(rt)) > 5.5 + along * 0.2 || Math.abs(off.dot(up)) > 11) continue;
                AstaFx.later((int) (along / speed), () -> {
                    if (!t.isAlive() || !alive(p)) return;
                    AstaFx.damage(p, t, dmg, "bloody_massacre");
                    Fx.impact(sl, GR, mid(t), 2.0f);
                    if (!framed[0]) { framed[0] = true; FxSend.impactFrame(sl, mid(t), 6, FxSend.FRAME_RIPPER_GREEN, 48); }
                    t.setDeltaMovement(t.getDeltaMovement().add(dir.add(0, 0.35, 0).normalize().scale(1.7)));
                    t.hurtMarked = true;
                });
            }
            AstaFx.later(10, () -> { if (!framed[0] && alive(p)) { framed[0] = true; FxSend.impactFrame(sl, o.add(dir.scale(10)), 5, FxSend.FRAME_RIPPER_GREEN, 48); } });

            // the upward cut: crescents lying low on the ground tip up one after another until the blade stands tall
            for (int i = 0; i < 6; i++) {
                final int ii = i;
                AstaFx.later(i, () -> {
                    double lean = Math.PI / 2 * ii / 5.0;   // 0 = flat on the ground, 90 deg = upright
                    Vec3 uu = aimed ? up : rt.scale(Math.cos(lean)).add(new Vec3(0, 1, 0).scale(Math.sin(lean)));
                    FxSend.slash(sl, GR, o.add(dir.scale(0.4 * ii)), dir, uu, 4.0f + ii * 0.5f, 14f + ii * 3f, 6, 0f, 2.0f);
                    FxSend.slash(sl, S, o.add(dir.scale(0.4 * ii)), dir, uu, 4.0f + ii * 0.5f, 12f + ii * 3f, 5, 0f, 0.3f);
                });
            }

            // ONE colossal green slash, upright, rolling forward and growing; a thin white-hot core inside it
            int life = (int) (reach / speed) + 2;
            AstaFx.later(5, () -> {
                FxSend.slash(sl, GR, o, dir, up, 8.0f, 42f, life, speed, 2.6f);
                FxSend.slash(sl, S, o, dir, up, 8.0f, 40f, life, speed, 0.4f);
                FxSend.slash(sl, GR, o.add(dir.scale(-1.2)), dir, up, 5.5f, 30f, life - 2, speed, 1.6f);   // an echo of the cut behind it
            });
            FxSend.wall(sl, GR, o.add(dir.scale(2)), dir, up, 3.5f, 4.5f, -180, 180, 0.3f, life, 0, speed);

            // the ground it passed over stays cut: a long green scar laid along the floor behind the blade
            for (double k = 0; k < reach; k += 4) {
                final double kk = k;
                AstaFx.later((int) (k / speed) + 5, () -> {
                    Vec3 a0 = FxLib.floor(sl, start.add(dir.scale(kk))).add(0, 0.06, 0);
                    Vec3 a1 = FxLib.floor(sl, start.add(dir.scale(kk + 4.4))).add(0, 0.06, 0);
                    FxSend.trail(sl, GR, a0, a1, 1.1f, 60);
                    FxSend.trail(sl, S, a0, a1, 0.18f, 50);
                    FxSend.shake(sl, a0, 0.3f, 4, 30);
                });
            }
        });
    }
}
