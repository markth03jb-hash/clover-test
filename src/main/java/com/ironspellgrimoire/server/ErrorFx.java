package com.ironspellgrimoire.server;

import static com.ironspellgrimoire.server.FxUtil.*;

import com.ironspellgrimoire.network.FxPacket;
import com.ironspellgrimoire.registry.AddonSounds;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The Error Grimoire (an original grimoire): corrupted magic. Its spells glitch, stutter and tear holes in the
 * world - colours slip apart, frames skip, things lag and snap back. Drawn by client/ErrorFxClient (FxPacket shape
 * 86): split magenta and cyan, black pixel blocks, and white noise.
 *
 *  1 Glitch Bolt        - a bolt that skips toward your target in jagged jumps
 *  2 Frame Skip         - you skip ahead ten blocks in three jumps, glitching through everything in between
 *  3 Corrupted Threads  - glitching threads lash the foe you aim at and hold it in place, hurting it as they cut
 *  4 Lag Spike          - foes in front of you lag: they freeze, then snap back to where they stood and take the hit
 *  5 Copy Paste         - a strike on your target, then the same strike pasted onto up to three other foes
 *  6 Crash Zone         - a zone where everything stutters, slows and takes damage for four seconds
 *  7 Error Windows      - error windows pop up round your target and slam shut on it
 *  8 Blue Screen        - a cube of dead pixels forms round your target and crushes everything in it
 *  9 Fatal Error (mode) - 20 s: your spells may fire twice, your cooldowns skip ahead, and you flicker faster
 * 10 System Crash       - reality tears open round your target and everything in it crashes.
 *                        Surpassing Limits: wider, harder.
 *
 * Passive "Duplicate": one Error hit in five is duplicated - it strikes again for half.
 */
public final class ErrorFx {
    private ErrorFx() {}

    public static final String MODE = "fatal_error";
    static final int PAL = Fx.VOID;   // the generic hit marks: magenta
    private static final UUID HOLD = UUID.fromString("9e4a6f50-3a74-4db1-8e44-ad51f8000001");
    private static final UUID STUTTER = UUID.fromString("9e4a6f50-3a74-4db1-8e44-ad51f8000002");
    private static final double TAU = Math.PI * 2;

    // ================================================================== the client shapes (ErrorFxClient, shape 86)
    private static FxPacket pk(int kind, Vec3 c, int life) {
        FxPacket p = new FxPacket();
        p.shape = 86; p.id = FxSend.nextId(); p.h = kind; p.c = c; p.life = life; p.a1 = (float) (Math.random() * 1000);
        return p;
    }
    private static void send(ServerLevel sl, FxPacket p) { FxSend.sendNear(sl, p.c, p); }

    /** 0 a glitching bolt from c to u (w = width). */
    static void bolt(ServerLevel sl, Vec3 c, Vec3 u, float w, int life) { FxPacket p = pk(0, c, life); p.u = u; p.r = w; send(sl, p); }
    /** 1 a burst of dead pixels at c. */
    static void blocks(ServerLevel sl, Vec3 c, float r, int life) { FxPacket p = pk(1, c, life); p.r = r; send(sl, p); }
    /** 2 threads from c to an entity. */
    static void threads(ServerLevel sl, Vec3 c, LivingEntity t, int life) { FxPacket p = pk(2, c, life); p.v = new Vec3(t.getId(), 1, 0); send(sl, p); }
    /** 3 a frozen frame round an entity (lag). */
    static void frozen(ServerLevel sl, LivingEntity t, int life) { FxPacket p = pk(3, t.position(), life); p.v = new Vec3(t.getId(), 1, 0); send(sl, p); }
    /** 4 a crash zone on the ground. */
    static void zone(ServerLevel sl, Vec3 c, float r, int life) { FxPacket p = pk(4, c, life); p.r = r; send(sl, p); }
    /** 5 an error window at c facing d, slamming onto u after {@code wait} ticks. */
    static void window(ServerLevel sl, Vec3 c, Vec3 u, Vec3 d, float size, int wait, int life) { FxPacket p = pk(5, c, life); p.u = u; p.d = d; p.r = size; p.sweep = wait; send(sl, p); }
    /** 6 the blue screen: a cube of dead pixels closing on c. */
    static void cube(ServerLevel sl, Vec3 c, float r, int life) { FxPacket p = pk(6, c, life); p.r = r; send(sl, p); }
    /** 7 reality tearing round c (r), {@code build} ticks of tearing before it crashes. */
    static void tear(ServerLevel sl, Vec3 c, float r, int build, int life) { FxPacket p = pk(7, c, life); p.r = r; p.sweep = build; send(sl, p); }
    /** 8 Fatal Error: glitches round a player. */
    static void aura(ServerLevel sl, ServerPlayer pl, int life) { FxPacket p = pk(8, pl.position(), life); p.v = new Vec3(pl.getId(), 1, 0); send(sl, p); }
    /** 9 an afterimage of someone at c (r = height). */
    static void ghost(ServerLevel sl, Vec3 c, float h, int life) { FxPacket p = pk(9, c, life); p.r = h; send(sl, p); }

    // ================================================================== helpers
    static void play(ServerLevel sl, Vec3 at, String kind, float vol, float pitch) {
        SoundEvent s = AddonSounds.get(kind);
        if (s != null) sl.playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, vol, pitch);
    }

    static boolean fatal(ServerPlayer p) { return Moves.inMode(p, MODE); }
    private static boolean twice = false;

    /** An Error hit: one in five is duplicated (Fatal Error: one in three). */
    static void hit(ServerPlayer p, LivingEntity t, float amt, String id) {
        AstaFx.damage(p, t, amt, id);
        if (ThreadLocalRandom.current().nextFloat() < (fatal(p) ? 0.33f : 0.2f)) {
            ServerLevel sl = p.serverLevel();
            AstaFx.later(4, () -> {
                if (!alive(p) || !t.isAlive()) return;
                blocks(sl, mid(t), 0.9f, 8);
                AstaFx.damage(p, t, amt * 0.5f, id);
                play(sl, mid(t), "glitch", 0.5f, 1.6f);
            });
        }
    }

    /** Fatal Error: each spell may fire again (at 70%), and cooldowns skip ahead. */
    private static void cast(ServerPlayer p, float power, java.util.function.BiConsumer<ServerPlayer, Float> again) {
        if (!fatal(p)) return;
        try { var cd = MagicData.getPlayerMagicData(p).getPlayerCooldowns(); cd.tick(30); cd.syncToPlayer(p); } catch (Throwable ignored) {}
        if (again == null || twice || ThreadLocalRandom.current().nextFloat() > 0.35f) return;
        AstaFx.later(10, () -> {
            if (!alive(p)) return;
            twice = true;
            try { again.accept(p, power * 0.7f); } finally { twice = false; }
        });
    }

    private static Vec3 target(ServerPlayer p, double range) { return InkFx.target(p, range); }

    /** A little nudge to somewhere close and safe (the stutter). */
    private static void jitter(ServerLevel sl, LivingEntity e, double amt) {
        if (IceFx.boss(e)) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Vec3 to = e.position().add(r.nextDouble(-amt, amt), 0, r.nextDouble(-amt, amt));
        Vec3 safe = SpaceFx.safe(sl, to, e);
        if (safe != null) e.teleportTo(safe.x, safe.y, safe.z);
    }

    // ================================================================== 1. Glitch Bolt
    public static void glitchBolt(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        cast(p, power, ErrorFx::glitchBolt);
        LivingEntity t = aimed(p, 30, 10);
        Vec3 from = Kit.hand(p);
        Vec3 to = t != null ? mid(t) : clip(sl, p, p.getEyePosition(), p.getEyePosition().add(p.getLookAngle().scale(30)));
        bolt(sl, from, to, 0.25f, 9);
        blocks(sl, to, 1.0f, 10);
        for (LivingEntity e : Kit.along(sl, p, from, to, 1.0)) hit(p, e, power * 0.9f, "glitch_bolt");
        play(sl, from, "glitch", 0.9f, 1.0f);
    }

    // ================================================================== 2. Frame Skip
    public static void frameSkip(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        cast(p, power, null);
        Vec3 look = flat(p.getLookAngle());
        if (look.lengthSqr() < 1.0E-4) look = new Vec3(0, 0, 1);
        final Vec3 lk = look.normalize();
        final List<LivingEntity> hitAlready = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            AstaFx.later(1 + i * 3, () -> {
                if (!alive(p)) return;
                Vec3 from = p.position();
                Vec3 dest = clip(sl, p, p.getEyePosition(), p.getEyePosition().add(lk.scale(3.4))).subtract(lk.scale(0.5)).subtract(0, p.getEyeHeight(), 0);
                Vec3 safe = SpaceFx.safe(sl, dest, p);
                if (safe == null) return;
                ghost(sl, from, p.getBbHeight(), 10);
                blocks(sl, from.add(0, 1, 0), 0.8f, 8);
                for (LivingEntity e : Kit.along(sl, p, from.add(0, 1, 0), safe.add(0, 1, 0), 1.3)) {
                    if (hitAlready.contains(e)) continue;
                    hitAlready.add(e);
                    hit(p, e, power * 0.7f, "frame_skip");
                }
                SpaceFx.move(p, safe, p.getYRot());
                play(sl, safe, "glitch", 0.6f, 1.5f);
            });
        }
    }

    // ================================================================== 3. Corrupted Threads
    public static void corruptedThreads(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        cast(p, power, ErrorFx::corruptedThreads);
        LivingEntity t = aimed(p, 22, 12);
        if (t == null) {   // nothing aimed: the nearest foe in front
            double best = 22;
            for (LivingEntity e : Kit.near(sl, p, p.getEyePosition(), 22)) {
                Vec3 to = mid(e).subtract(p.getEyePosition());
                if (to.normalize().dot(p.getLookAngle()) < 0.5) continue;
                if (to.length() < best) { best = to.length(); t = e; }
            }
        }
        if (t == null) { bolt(sl, Kit.hand(p), Kit.aim(p, 22), 0.08f, 6); play(sl, p.position(), "error_beep", 0.6f, 0.8f); return; }
        final LivingEntity tt = t;
        final int T = IceFx.boss(t) ? 30 : 50;
        threads(sl, Kit.hand(p), t, T);
        TimedModifiers.apply(t, Attributes.MOVEMENT_SPEED, HOLD, "Bound by threads", -1.0, AttributeModifier.Operation.MULTIPLY_TOTAL, T);
        for (int k = 0; k < T; k += 10) {
            AstaFx.later(k + 1, () -> { if (alive(p) && tt.isAlive()) hit(p, tt, power * 0.28f, "corrupted_threads"); });
        }
        play(sl, p.position(), "glitch", 0.8f, 0.7f);
    }

    // ================================================================== 4. Lag Spike
    public static void lagSpike(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        cast(p, power, ErrorFx::lagSpike);
        Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
        final List<LivingEntity> foes = new ArrayList<>();
        final List<Vec3> where = new ArrayList<>();
        for (LivingEntity e : Kit.near(sl, p, eye, 12)) {
            Vec3 to = mid(e).subtract(eye);
            if (to.lengthSqr() > 1.0E-4 && to.normalize().dot(look) < 0.55) continue;
            foes.add(e); where.add(e.position());
            frozen(sl, e, 30);
            TimedModifiers.apply(e, Attributes.MOVEMENT_SPEED, HOLD, "Lagging", -1.0, AttributeModifier.Operation.MULTIPLY_TOTAL, 30);
            hit(p, e, power * 0.3f, "lag_spike");
        }
        blocks(sl, eye.add(look.scale(3)), 1.4f, 10);
        play(sl, eye, "error_beep", 0.9f, 1.0f);
        AstaFx.later(30, () -> {   // and they snap back to where they were
            if (!alive(p)) return;
            for (int i = 0; i < foes.size(); i++) {
                LivingEntity e = foes.get(i);
                if (!e.isAlive()) continue;
                Vec3 w = where.get(i);
                blocks(sl, mid(e), 1.0f, 8);
                if (!IceFx.boss(e) && e.position().distanceTo(w) > 0.5 && SpaceFx.safe(sl, w, e) != null) e.teleportTo(w.x, w.y, w.z);
                hit(p, e, power * 0.9f, "lag_spike");
                blocks(sl, w.add(0, 1, 0), 1.0f, 8);
            }
            play(sl, p.position(), "glitch", 1.0f, 0.6f);
        });
    }

    // ================================================================== 5. Copy Paste
    public static void copyPaste(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        cast(p, power, ErrorFx::copyPaste);
        LivingEntity t = aimed(p, 24, 12);
        Vec3 c = t != null ? mid(t) : target(p, 24).add(0, 1, 0);
        window(sl, c.add(0, 1.6, 0), c, new Vec3(0, 0, 0), 1.0f, 2, 8);
        blocks(sl, c, 1.4f, 10);
        for (LivingEntity e : Kit.near(sl, p, c, 2)) hit(p, e, power * 0.8f, "copy_paste");
        play(sl, c, "error_beep", 0.8f, 1.3f);
        List<LivingEntity> others = Kit.near(sl, p, c, 12);
        others.removeIf(e -> mid(e).distanceTo(c) < 2.2);
        for (int i = 0; i < Math.min(3, others.size()); i++) {   // pasted
            final LivingEntity e = others.get(i);
            final int k = i;
            AstaFx.later(6 + i * 4, () -> {
                if (!alive(p) || !e.isAlive()) return;
                window(sl, mid(e).add(0, 1.6, 0), mid(e), Vec3.ZERO, 1.0f, 2, 8);
                blocks(sl, mid(e), 1.4f, 10);
                hit(p, e, power * 0.8f, "copy_paste");
                play(sl, mid(e), "error_beep", 0.6f, 1.4f + k * 0.1f);
            });
        }
    }

    // ================================================================== 6. Crash Zone
    public static void crashZone(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        cast(p, power, null);
        final Vec3 c = target(p, 24);
        final float R = 5f;
        final int T = 80;
        zone(sl, c.add(0, 0.03, 0), R, T);
        play(sl, c, "glitch", 1.0f, 0.5f);
        for (int k = 0; k < T; k += 10) {
            final int kk = k;
            AstaFx.later(k + 1, () -> {
                if (!alive(p)) return;
                for (LivingEntity e : Kit.near(sl, p, c.add(0, 1, 0), R)) {
                    TimedModifiers.apply(e, Attributes.MOVEMENT_SPEED, STUTTER, "Stuttering", -0.45, AttributeModifier.Operation.MULTIPLY_TOTAL, 14);
                    hit(p, e, power * 0.18f, "crash_zone");
                    if (kk % 20 == 0) jitter(sl, e, 0.7);
                }
                if (kk % 20 == 0) play(sl, c, "glitch", 0.5f, 0.7f + kk * 0.005f);
            });
        }
    }

    // ================================================================== 7. Error Windows
    public static void errorWindows(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        cast(p, power, ErrorFx::errorWindows);
        LivingEntity t = aimed(p, 24, 12);
        final Vec3 c = t != null ? mid(t) : target(p, 24).add(0, 1, 0);
        final int N = 6, WAIT = 14;
        for (int i = 0; i < N; i++) {
            double an = i * TAU / N;
            Vec3 at = c.add(Math.cos(an) * 3.2, (i % 2) * 0.9 - 0.2, Math.sin(an) * 3.2);
            Vec3 face = c.subtract(at).normalize();
            final int k = i;
            AstaFx.later(i * 2, () -> { window(sl, at, c, face, 1.1f, WAIT - k * 2, WAIT - k * 2 + 6); play(sl, at, "error_beep", 0.5f, 1.0f + k * 0.08f); });
        }
        AstaFx.later(WAIT, () -> {
            if (!alive(p)) return;
            blocks(sl, c, 2.2f, 12);
            for (LivingEntity e : Kit.near(sl, p, c, 2.6)) hit(p, e, power * 1.3f, "error_windows");
            play(sl, c, "glitch", 1.0f, 0.8f);
        });
    }

    // ================================================================== 8. Blue Screen
    public static void blueScreen(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        cast(p, power, ErrorFx::blueScreen);
        LivingEntity t = aimed(p, 24, 12);
        final Vec3 c = t != null ? mid(t) : target(p, 24).add(0, 1, 0);
        final float R = 2.6f;
        final int T = 18;
        cube(sl, c, R, T + 8);
        for (LivingEntity e : Kit.near(sl, p, c, R)) TimedModifiers.apply(e, Attributes.MOVEMENT_SPEED, HOLD, "Stuck", -1.0, AttributeModifier.Operation.MULTIPLY_TOTAL, T);
        play(sl, c, "error_beep", 1.0f, 0.6f);
        AstaFx.later(T, () -> {
            if (!alive(p)) return;
            blocks(sl, c, 3f, 14);
            for (LivingEntity e : Kit.near(sl, p, c, R + 0.5)) hit(p, e, power * 1.6f, "blue_screen");
            FxSend.shake(sl, c, 0.4f, 6, 16);
            play(sl, c, "glitch", 1.2f, 0.5f);
        });
    }

    // ================================================================== 9. Fatal Error (mode)
    public static void fatalError(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        final int T = 400;
        aura(sl, p, T);
        Moves.mode(p, MODE, T, 20, pl -> {});
        Moves.stance(p, 0, 0, 0.25, 0, 0.2, T);
        blocks(sl, p.position().add(0, 1, 0), 2.5f, 16);
        FxSend.impactFrame(sl, p.position().add(0, 1, 0), 2, FxSend.FRAME_RIPPER_GREEN, 16);
        play(sl, p.position(), "glitch", 1.2f, 0.4f);
        play(sl, p.position(), "error_beep", 1.0f, 0.5f);
    }

    // ================================================================== 10. System Crash
    public static void systemCrash(ServerPlayer p, float power) {
        boolean limit = LimitBreak.active(p);
        ServerLevel sl = p.serverLevel();
        cast(p, power, null);
        final float pow = power * (limit ? 1.3f : 1f);
        final Vec3 g = target(p, 30);
        final float R = limit ? 11f : 8f;
        final int BUILD = 26;
        tear(sl, g, R, BUILD, BUILD + 18);
        play(sl, g, "glitch", 1.4f, 0.4f);
        for (int k = 0; k < BUILD; k += 6) {
            final int kk = k;
            AstaFx.later(k, () -> {
                if (!alive(p)) return;
                for (LivingEntity e : Kit.near(sl, p, g.add(0, 1, 0), R)) {
                    TimedModifiers.apply(e, Attributes.MOVEMENT_SPEED, STUTTER, "Crashing", -0.6, AttributeModifier.Operation.MULTIPLY_TOTAL, 10);
                    if (kk % 12 == 0) jitter(sl, e, 0.5);
                }
                play(sl, g, "error_beep", 0.7f, 0.6f + kk * 0.02f);
            });
        }
        AstaFx.later(BUILD, () -> {
            if (!alive(p)) return;
            for (LivingEntity e : Kit.near(sl, p, g.add(0, 1, 0), R)) hit(p, e, pow * 2.0f, "system_crash");
            FxSend.impactFrame(sl, g.add(0, 1, 0), 3, FxSend.FRAME_RIPPER_GREEN, 24);
            FxSend.shake(sl, g, 0.8f, 12, 34);
            play(sl, g, "glitch", 1.6f, 0.3f);
            sound(sl, g, SoundEvents.GENERIC_EXPLODE, 0.8f, 1.2f);
        });
    }

    /** Surpassing Limits: you glitch out of the world and back in. */
    public static void awaken(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        Vec3 c = p.position();
        for (int i = 0; i < 4; i++) ghost(sl, c.add(Math.cos(i * 1.57) * 1.5, 0, Math.sin(i * 1.57) * 1.5), p.getBbHeight(), 14 + i * 3);
        blocks(sl, c.add(0, 1, 0), 3f, 16);
        play(sl, c, "glitch", 1.2f, 0.5f);
    }
}
