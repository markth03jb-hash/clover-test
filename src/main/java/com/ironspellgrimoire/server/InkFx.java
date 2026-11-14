package com.ironspellgrimoire.server;

import static com.ironspellgrimoire.server.FxUtil.*;

import com.ironspellgrimoire.network.FxPacket;
import com.ironspellgrimoire.registry.AddonSounds;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Ink Grimoire (an original grimoire): living ink and calligraphy. You paint spells in the air with a brush of
 * mana, and what you paint comes alive. Drawn by client/InkFxClient (FxPacket shape 85): glossy black ink with a
 * faint indigo sheen, splashing, running and drying.
 *
 *  1 Brush Stroke       - one sweeping stroke of ink across everything in front of you
 *  2 Ink Splash         - five globs of ink lobbed onto your target, splattering everything round it
 *  3 Ink Serpent        - a painted serpent slithers along the ground, biting everything on its way
 *  4 Ink Blot           - a great blot spreads under your target: foes in it are stained, slowed and soaked
 *  5 Seal Script        - you write a seal on the ground; a moment later it bursts up in a column of characters
 *  6 Ink Beasts         - three ink wolves are painted at your side and run down the nearest foes
 *  7 Ink Rain           - ink rains on your target's ground for three seconds
 *  8 Ink Wave           - a wave of ink sweeps forward and carries foes with it
 *  9 Masterpiece (mode) - 20 s: everything you paint is painted twice (the copy at 60%)
 * 10 Grand Calligraphy  - a giant character is painted stroke by stroke in the sky, then falls on your target.
 *                        Surpassing Limits: larger, heavier.
 *
 * Passive "Signature": every Ink spell is a stroke; the third stroke completes a seal, which bursts on the nearest
 * foe by itself. Ink also stains: a stained foe takes 4% more from Ink spells per stain (up to five).
 */
public final class InkFx {
    private InkFx() {}

    public static final String MODE = "masterpiece";
    static final int PAL = 10;   // the generic hit marks: inky dark violet
    private static final UUID SOAK = UUID.fromString("8d3f5e40-2f63-4ca0-9d33-9c40e7000001");
    private static final double TAU = Math.PI * 2;

    // ================================================================== the client shapes (InkFxClient, shape 85)
    private static FxPacket pk(int kind, Vec3 c, int life) {
        FxPacket p = new FxPacket();
        p.shape = 85; p.id = FxSend.nextId(); p.h = kind; p.c = c; p.life = life;
        return p;
    }
    private static void send(ServerLevel sl, FxPacket p) { FxSend.sendNear(sl, p.c, p); }

    /** 0 a brush stroke: an arc of radius r round c, from direction d, sweeping a0 radians about u, painted over {@code paint} ticks. */
    static void stroke(ServerLevel sl, Vec3 c, Vec3 d, Vec3 u, float r, float sweep, int paint, int life) {
        FxPacket p = pk(0, c, life);
        p.d = d; p.u = u; p.r = r; p.a0 = sweep; p.speed = paint; p.a1 = (float) Math.random() * 100;
        send(sl, p);
    }
    /** 1 a glob flying from c to u over {@code fly} ticks along an arc of height h; it splats where it lands. */
    static void glob(ServerLevel sl, Vec3 c, Vec3 u, float size, float h, int fly) {
        FxPacket p = pk(1, c, fly + 1);
        p.u = u; p.r = size; p.a0 = h; p.speed = fly;
        send(sl, p);
    }
    /** 2 a splat on the ground, drying away over its life. */
    static void splat(ServerLevel sl, Vec3 c, float r, int life) {
        FxPacket p = pk(2, c, life);
        p.r = r; p.a1 = (float) Math.random() * 100;
        send(sl, p);
    }
    /** 3 a serpent from c along d for {@code len} blocks over its life. */
    static void serpent(ServerLevel sl, Vec3 c, Vec3 d, float len, int life) {
        FxPacket p = pk(3, c, life);
        p.d = d; p.r = len;
        send(sl, p);
    }
    /** 4 a seal written on the ground (radius r), bursting into a column of characters after {@code wait} ticks. */
    static void seal(ServerLevel sl, Vec3 c, float r, int wait, int life) {
        FxPacket p = pk(4, c, life);
        p.r = r; p.sweep = wait; p.a1 = (float) Math.random() * 100;
        send(sl, p);
    }
    /** 5 an ink beast running from c to u over {@code run} ticks. */
    static void beast(ServerLevel sl, Vec3 c, Vec3 u, int run) {
        FxPacket p = pk(5, c, run + 1);
        p.u = u; p.speed = run; p.a1 = (float) Math.random() * 100;
        send(sl, p);
    }
    /** 6 a drop of ink falling from c to u over {@code fall} ticks. */
    static void drop(ServerLevel sl, Vec3 c, Vec3 u, int fall) {
        FxPacket p = pk(6, c, fall + 1);
        p.u = u; p.speed = fall;
        send(sl, p);
    }
    /** 7 a wave of ink rolling from c along d for {@code len} blocks, {@code w} wide, over its life. */
    static void wave(ServerLevel sl, Vec3 c, Vec3 d, float len, float w, int life) {
        FxPacket p = pk(7, c, life);
        p.d = d; p.r = len; p.a0 = w;
        send(sl, p);
    }
    /** 8 the grand character: painted at c (size r) over {@code paint} ticks, then falling onto u over {@code fall}. */
    static void calligraphy(ServerLevel sl, Vec3 c, Vec3 u, float r, int paint, int fall) {
        FxPacket p = pk(8, c, paint + fall + 2);
        p.u = u; p.r = r; p.sweep = paint; p.speed = fall; p.a1 = (float) Math.random() * 100;
        FxSend.sendNear(sl, u, p);
    }
    /** 9 Masterpiece: brush strokes curling round a player. */
    static void aura(ServerLevel sl, ServerPlayer pl, int life) {
        FxPacket p = pk(9, pl.position(), life);
        p.v = new Vec3(pl.getId(), 1, 0);
        send(sl, p);
    }
    /** 10 a small seal flaring at c. */
    static void glyph(ServerLevel sl, Vec3 c, float r, int life) {
        FxPacket p = pk(10, c, life);
        p.r = r; p.a1 = (float) Math.random() * 100;
        send(sl, p);
    }

    // ================================================================== helpers
    static void play(ServerLevel sl, Vec3 at, String kind, float vol, float pitch) {
        SoundEvent s = AddonSounds.get(kind);
        if (s != null) sl.playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, vol, pitch);
    }

    private static final Map<UUID, Integer> STROKES = new HashMap<>();
    private static final Map<UUID, long[]> STAIN = new HashMap<>();
    /** Set while Masterpiece paints its copy (so the copy isn't copied again). */
    private static boolean copying = false;

    static boolean masterpiece(ServerPlayer p) { return Moves.inMode(p, MODE); }

    /** An Ink hit: stains add 4% each (up to five), and the hit leaves another stain. */
    static void hit(ServerPlayer p, LivingEntity t, float amt, String id) {
        long now = p.serverLevel().getGameTime();
        long[] s = STAIN.get(t.getUUID());
        int n = s == null || now > s[1] ? 0 : (int) s[0];
        AstaFx.damage(p, t, amt * (1f + 0.04f * n), id);
        STAIN.put(t.getUUID(), new long[]{Math.min(5, n + 1), now + 160});
        if (STAIN.size() > 300) STAIN.entrySet().removeIf(en -> now > en.getValue()[1]);
    }

    /**
     * Every Ink spell is a stroke: the third completes a seal that bursts on the nearest foe. Under Masterpiece the
     * spell is painted again 8 ticks later at 60%.
     */
    private static void stroke(ServerPlayer p, float power, java.util.function.BiConsumer<ServerPlayer, Float> again) {
        ServerLevel sl = p.serverLevel();
        int n = STROKES.merge(p.getUUID(), 1, Integer::sum);
        if (n >= 3) {
            STROKES.put(p.getUUID(), 0);
            LivingEntity t = null;
            double best = 14;
            for (LivingEntity e : Kit.near(sl, p, p.position().add(0, 1, 0), 14)) { double d = e.distanceTo(p); if (d < best) { best = d; t = e; } }
            if (t != null) {
                final LivingEntity tt = t;
                glyph(sl, tt.position().add(0, 0.05, 0), 1.6f, 16);
                AstaFx.later(8, () -> {
                    if (!alive(p) || !tt.isAlive()) return;
                    glyph(sl, mid(tt), 1.2f, 10);
                    hit(p, tt, power * 0.6f, "ink_seal");
                    play(sl, mid(tt), "ink_splash", 0.8f, 1.3f);
                });
            }
        }
        if (again != null && !copying && masterpiece(p)) {
            AstaFx.later(8, () -> {
                if (!alive(p)) return;
                copying = true;
                try { again.accept(p, power * 0.6f); } finally { copying = false; }
            });
        }
    }

    static Vec3 ground(ServerLevel sl, Vec3 near) {
        Vec3 g = FxLib.ground(sl, near.add(0, 2, 0), 10);
        return g == null ? near : g;
    }

    static Vec3 target(ServerPlayer p, double range) {
        LivingEntity t = aimed(p, range, 12);
        return t != null ? t.position() : ground(p.serverLevel(), Kit.aim(p, range));
    }

    // ================================================================== 1. Brush Stroke
    public static void brushStroke(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        stroke(p, power, InkFx::brushStroke);
        Vec3 look = flat(p.getLookAngle());
        if (look.lengthSqr() < 1.0E-4) look = new Vec3(0, 0, 1);
        look = look.normalize();
        Vec3 c = p.position().add(0, 1.1, 0);
        Vec3 start = rotY(look, 1.2);
        // the arc turns from 70 degrees on one side, across the front, to 70 on the other (a negative sweep went round behind you)
        stroke(sl, c, start, new Vec3(0, 1, 0).add(rightOf(look).scale(0.25)).normalize(), 3.6f, 2.4f, 4, 22);
        final Vec3 lk = look;
        AstaFx.later(2, () -> {
            if (!alive(p)) return;
            for (LivingEntity e : Kit.near(sl, p, c, 4.6)) {
                Vec3 to = flat(mid(e).subtract(c));
                if (to.lengthSqr() > 1.0E-4 && to.normalize().dot(lk) < 0.25) continue;
                hit(p, e, power * 0.7f, "brush_stroke");
                Kit.push(e, lk, 0.4, 0.1);
            }
        });
        play(sl, c, "brush", 1.0f, 1.0f);
    }

    // ================================================================== 2. Ink Splash
    public static void inkSplash(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        stroke(p, power, InkFx::inkSplash);
        Vec3 t = target(p, 24);
        Vec3 from = Kit.hand(p);
        for (int i = 0; i < 5; i++) {
            final int k = i;
            double an = i * TAU / 5;
            Vec3 to = i == 0 ? t : t.add(Math.cos(an) * 1.8, 0, Math.sin(an) * 1.8);
            int fly = 8 + i;
            AstaFx.later(i, () -> glob(sl, from, to.add(0, 0.1, 0), 0.32f, 2.5f, fly));
            AstaFx.later(i + fly, () -> {
                if (!alive(p)) return;
                splat(sl, ground(sl, to).add(0, 0.02, 0), 1.3f, 80);
                for (LivingEntity e : Kit.near(sl, p, to.add(0, 0.6, 0), 1.9)) hit(p, e, power * 0.28f, "ink_splash");
                if (k % 2 == 0) play(sl, to, "ink_splash", 0.7f, 0.9f + k * 0.1f);
            });
        }
        play(sl, from, "brush", 0.7f, 1.4f);
    }

    // ================================================================== 3. Ink Serpent
    public static void inkSerpent(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        stroke(p, power, InkFx::inkSerpent);
        Vec3 d = flat(p.getLookAngle());
        if (d.lengthSqr() < 1.0E-4) d = new Vec3(0, 0, 1);
        d = d.normalize();
        Vec3 c = p.position().add(d.scale(1.2));
        final float L = 16f;
        final int T = 24;
        serpent(sl, c, d, L, T + 14);
        final Vec3 dd = d;
        final List<LivingEntity> bitten = new ArrayList<>();
        for (int k = 1; k <= T; k++) {
            final int kk = k;
            AstaFx.later(k, () -> {
                if (!alive(p)) return;
                Vec3 head = c.add(dd.scale(L * kk / (double) T)).add(0, 0.6, 0);
                for (LivingEntity e : Kit.near(sl, p, head, 1.6)) {
                    if (bitten.contains(e)) continue;
                    bitten.add(e);
                    hit(p, e, power * 0.8f, "ink_serpent");
                    Kit.push(e, dd, 0.3, 0.35);
                    splat(sl, ground(sl, e.position()).add(0, 0.02, 0), 0.9f, 60);
                }
                if (kk % 8 == 0) play(sl, head, "brush", 0.5f, 0.8f);
            });
        }
        play(sl, c, "ink_splash", 0.8f, 0.7f);
    }

    // ================================================================== 4. Ink Blot
    public static void inkBlot(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        stroke(p, power, InkFx::inkBlot);
        final Vec3 c = target(p, 24);
        final float R = 4.5f;
        splat(sl, c.add(0, 0.03, 0), R, 100);
        play(sl, c, "ink_splash", 1.0f, 0.6f);
        for (int k = 0; k < 100; k += 10) {
            final int kk = k;
            AstaFx.later(k + 2, () -> {
                if (!alive(p)) return;
                for (LivingEntity e : Kit.near(sl, p, c.add(0, 0.8, 0), R)) {
                    if (Math.abs(e.getY() - c.y) > 2.5) continue;
                    TimedModifiers.apply(e, Attributes.MOVEMENT_SPEED, SOAK, "Soaked in ink", -0.4, AttributeModifier.Operation.MULTIPLY_TOTAL, 14);
                    if (kk % 20 == 0) hit(p, e, power * 0.2f, "ink_blot");
                }
            });
        }
    }

    // ================================================================== 5. Seal Script
    public static void sealScript(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        stroke(p, power, InkFx::sealScript);
        final Vec3 c = target(p, 24);
        final float R = 3.5f;
        final int W = 26;
        seal(sl, c.add(0, 0.04, 0), R, W, W + 18);
        play(sl, c, "brush", 0.9f, 0.7f);
        AstaFx.later(W, () -> {
            if (!alive(p)) return;
            for (LivingEntity e : Kit.near(sl, p, c.add(0, 1, 0), R)) {
                hit(p, e, power * 1.4f, "seal_script");
                Kit.push(e, new Vec3(0, 1, 0), 0, 0.6);
            }
            FxSend.shake(sl, c, 0.35f, 6, 16);
            play(sl, c, "ink_splash", 1.1f, 0.6f);
            sound(sl, c, SoundEvents.EVOKER_CAST_SPELL, 0.7f, 0.8f);
        });
    }

    // ================================================================== 6. Ink Beasts
    public static void inkBeasts(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        stroke(p, power, InkFx::inkBeasts);
        List<LivingEntity> foes = Kit.near(sl, p, p.position().add(0, 1, 0), 18);
        foes.sort((a, b) -> Double.compare(a.distanceToSqr(p), b.distanceToSqr(p)));
        Vec3 look = flat(p.getLookAngle());
        if (look.lengthSqr() < 1.0E-4) look = new Vec3(0, 0, 1);
        look = look.normalize();
        for (int i = 0; i < 3; i++) {
            final int k = i;
            Vec3 from = p.position().add(rightOf(look).scale((i - 1) * 1.2)).add(look.scale(0.8));
            final LivingEntity t = foes.isEmpty() ? null : foes.get(i % foes.size());
            Vec3 to = t != null ? t.position() : ground(sl, from.add(look.scale(12)));
            int run = Math.max(6, (int) Math.ceil(from.distanceTo(to) / 1.0));
            splat(sl, ground(sl, from).add(0, 0.02, 0), 0.8f, 30);
            AstaFx.later(i * 3, () -> beast(sl, from, to, run));
            AstaFx.later(i * 3 + run, () -> {
                if (!alive(p)) return;
                Vec3 at = t != null && t.isAlive() ? t.position() : to;
                splat(sl, ground(sl, at).add(0, 0.02, 0), 1.4f, 60);
                if (t != null && t.isAlive()) hit(p, t, power * 0.75f, "ink_beasts");
                for (LivingEntity e : Kit.near(sl, p, at.add(0, 0.8, 0), 1.8)) if (e != t) hit(p, e, power * 0.3f, "ink_beasts");
                play(sl, at, "ink_splash", 0.9f, 1.0f + k * 0.15f);
            });
        }
        play(sl, p.position(), "brush", 0.8f, 0.9f);
    }

    // ================================================================== 7. Ink Rain
    public static void inkRain(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        stroke(p, power, InkFx::inkRain);
        final Vec3 c = target(p, 26);
        final float R = 4.5f;
        java.util.Random r = new java.util.Random();
        for (int k = 0; k < 60; k += 2) {
            final int kk = k;
            AstaFx.later(k, () -> {
                if (!alive(p)) return;
                for (int j = 0; j < 3; j++) {
                    double an = r.nextDouble() * TAU, rr = Math.sqrt(r.nextDouble()) * R;
                    Vec3 g = ground(sl, c.add(Math.cos(an) * rr, 0, Math.sin(an) * rr));
                    drop(sl, g.add(0, 9, 0), g, 6);
                }
                if (kk % 10 == 0) {
                    for (LivingEntity e : Kit.near(sl, p, c.add(0, 1, 0), R)) hit(p, e, power * 0.16f, "ink_rain");
                    splat(sl, c.add(r.nextDouble() * 3 - 1.5, 0.03, r.nextDouble() * 3 - 1.5), 1.2f, 50);
                }
            });
        }
        play(sl, c, "ink_splash", 0.6f, 1.6f);
    }

    // ================================================================== 8. Ink Wave
    public static void inkWave(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        stroke(p, power, InkFx::inkWave);
        Vec3 d = flat(p.getLookAngle());
        if (d.lengthSqr() < 1.0E-4) d = new Vec3(0, 0, 1);
        d = d.normalize();
        final Vec3 c = p.position().add(d.scale(1.0)), dd = d;
        final float L = 13f, W = 5f;
        final int T = 18;
        wave(sl, c, d, L, W, T + 8);
        final List<LivingEntity> swept = new ArrayList<>();
        for (int k = 1; k <= T; k++) {
            final int kk = k;
            AstaFx.later(k, () -> {
                if (!alive(p)) return;
                Vec3 front = c.add(dd.scale(L * kk / (double) T));
                for (LivingEntity e : Kit.along(sl, p, front.add(rightOf(dd).scale(-W / 2)).add(0, 0.8, 0), front.add(rightOf(dd).scale(W / 2)).add(0, 0.8, 0), 1.2)) {
                    Kit.push(e, dd, 0.45 * GravityFx.give(e), 0.12);
                    if (!swept.contains(e)) { swept.add(e); hit(p, e, power * 0.9f, "ink_wave"); }
                }
            });
        }
        play(sl, c, "ink_splash", 1.1f, 0.5f);
        play(sl, c, "brush", 0.9f, 0.6f);
    }

    // ================================================================== 9. Masterpiece (mode)
    public static void masterpieceMode(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        final int T = 400;
        aura(sl, p, T);
        Moves.mode(p, MODE, T, 20, pl -> {});
        Moves.stance(p, 0, 0, 0.1, 0, 0.15, T);
        splat(sl, p.position().add(0, 0.03, 0), 3f, 60);
        stroke(sl, p.position().add(0, 1.2, 0), p.getLookAngle(), new Vec3(0, 1, 0), 2.2f, (float) TAU, 10, 24);
        play(sl, p.position(), "brush", 1.0f, 0.6f);
        sound(sl, p.position(), SoundEvents.BOOK_PAGE_TURN, 1.0f, 0.8f);
    }

    // ================================================================== 10. Grand Calligraphy
    public static void grandCalligraphy(ServerPlayer p, float power) {
        boolean limit = LimitBreak.active(p);
        ServerLevel sl = p.serverLevel();
        stroke(p, power, null);
        final float pow = power * (limit ? 1.3f : 1f);
        final Vec3 g = target(p, 30);
        final float S = limit ? 7f : 5f, R = limit ? 9f : 6.5f;
        final int PAINT = 24, FALL = 8;
        calligraphy(sl, g.add(0, 12, 0), g.add(0, 0.1, 0), S, PAINT, FALL);
        seal(sl, g.add(0, 0.04, 0), R, PAINT + FALL, PAINT + FALL + 14);
        play(sl, g, "brush", 1.2f, 0.5f);
        for (int k = 0; k < 4; k++) AstaFx.later(k * 6, () -> play(sl, g.add(0, 12, 0), "brush", 0.9f, 0.7f + 0.1f * (float) Math.random()));
        AstaFx.later(PAINT + FALL, () -> {
            if (!alive(p)) return;
            splat(sl, g.add(0, 0.03, 0), R, 120);
            for (LivingEntity e : Kit.near(sl, p, g.add(0, 1, 0), R)) {
                hit(p, e, pow * 2.0f, "grand_calligraphy");
                Kit.push(e, mid(e).subtract(g), 0.7 * GravityFx.give(e), 0.4);
            }
            FxSend.impactFrame(sl, g.add(0, 1, 0), 3, FxSend.FRAME_YAMI, 24);
            FxSend.shake(sl, g, 0.8f, 12, 32);
            play(sl, g, "ink_splash", 1.5f, 0.45f);
            sound(sl, g, SoundEvents.GENERIC_EXPLODE, 0.7f, 0.7f);
        });
    }

    /** Surpassing Limits: a ring of ink strokes painted round you. */
    public static void awaken(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        Vec3 c = p.position();
        splat(sl, c.add(0, 0.03, 0), 4f, 60);
        for (int i = 0; i < 3; i++) stroke(sl, c.add(0, 0.6 + i * 0.7, 0), new Vec3(Math.cos(i * 2.1), 0, Math.sin(i * 2.1)), new Vec3(0, 1, 0), 2.4f + i * 0.4f, (float) TAU, 12, 30);
        play(sl, c, "brush", 1.0f, 0.5f);
    }
}
