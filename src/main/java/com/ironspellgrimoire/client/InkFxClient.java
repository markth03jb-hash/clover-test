package com.ironspellgrimoire.client;

import com.ironspellgrimoire.network.FxPacket;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * The Ink Grimoire, drawn: glossy black ink (solid, not glowing) laid down in brush strokes that thin and split at
 * their ends like a dry brush, globs and splats, painted creatures, seals, and a faint indigo sheen so it reads at
 * night. Fed by FxPacket shape 85 (server/InkFx), {@code h} = kind: 0 stroke, 1 glob, 2 splat, 3 serpent, 4 seal,
 * 5 beast, 6 drop, 7 wave, 8 grand character, 9 Masterpiece, 10 small seal.
 */
public final class InkFxClient {
    private InkFxClient() {}

    private static final double TAU = Math.PI * 2;
    private static final Vec3 UP = new Vec3(0, 1, 0);
    static final float[] INK = {0.02F, 0.02F, 0.05F}, WET = {0.1F, 0.1F, 0.2F}, SHEEN = {0.35F, 0.38F, 1.0F}, PAPER = {1.0F, 0.95F, 0.85F};

    private static final class L {
        final FxPacket p;
        final double born;
        L(FxPacket p, double born) { this.p = p; this.born = born; }
    }

    private static final List<L> LIVE = new ArrayList<>();

    public static void spawn(FxPacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if ((int) p.h == 98) { LIVE.removeIf(l -> (int) l.p.v.x == (int) p.v.x); return; }   // a mode was ended early: drop what hangs on that player
        if (LIVE.size() > 400) LIVE.remove(0);
        LIVE.add(new L(p, mc.level.getGameTime()));
    }

    static void clear() { LIVE.clear(); }

    static void draw(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, double now, float pt) {
        if (LIVE.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { LIVE.clear(); return; }
        List<L> live = new ArrayList<>(LIVE);
        for (L l : live) {
            double age = now - l.born;
            if (age > l.p.life) { LIVE.remove(l); continue; }
            FxPacket p = l.p;
            switch ((int) p.h) {
                case 0 -> stroke(vc, m, n, cam, p, age);
                case 1 -> glob(vc, m, n, cam, p, age);
                case 2 -> splat(vc, m, n, cam, p, age);
                case 3 -> serpent(vc, m, n, cam, p, age, now);
                case 4 -> seal(vc, m, n, cam, p, age, now);
                case 5 -> beast(vc, m, n, cam, p, age, now);
                case 6 -> drop(vc, m, n, cam, p, age);
                case 7 -> wave(vc, m, n, cam, p, age, now);
                case 8 -> grand(vc, m, n, cam, p, age, now);
                case 9 -> aura(vc, m, n, cam, mc, p, age, now, pt);
                case 10 -> glyph(vc, m, n, cam, p, age);
                default -> LIVE.remove(l);
            }
        }
    }

    // ================================================================== the brush
    /** A ribbon of ink through {@code pts}, facing the camera, {@code w} half-widths; a faint sheen along it. */
    static void ink(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3[] pts, double[] w, float a) {
        for (int i = 0; i < pts.length - 1; i++) {
            Vec3 p0 = pts[i], p1 = pts[i + 1], ax = p1.subtract(p0);
            if (ax.lengthSqr() < 1e-10) continue;
            Vec3 s = ax.cross(p0.subtract(cam));
            if (s.lengthSqr() < 1e-10) continue;
            s = s.normalize();
            FxBase.quad(vc, m, n, cam, p0.add(s.scale(w[i])), p1.add(s.scale(w[i + 1])), p1.subtract(s.scale(w[i + 1])), p0.subtract(s.scale(w[i])), INK, a, a, a, a);
        }
        float[] al = new float[pts.length];
        double[] ws = new double[pts.length];
        for (int i = 0; i < pts.length; i++) { al[i] = a * 0.18F; ws[i] = w[i] * 1.5; }
        GravityFxClient.gstrip(m, cam, pts, ws, al, SHEEN);
    }

    /**
     * A brush stroke through {@code pts}: fat in the middle, a pointed start, and an end that splits into dry
     * bristle-lines with gaps in them.
     */
    static void brush(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3[] pts, double W, float a, int seed) {
        int N = pts.length;
        double[] w = new double[N];
        for (int i = 0; i < N; i++) {
            double u = i / (double) (N - 1);
            w[i] = W * (Math.min(1, u * 5) * (1 - 0.55 * u)) + 0.01;
        }
        ink(vc, m, n, cam, pts, w, a);
        if (N < 4) return;
        Vec3 side = pts[N - 1].subtract(pts[N - 2]).cross(pts[N - 1].subtract(cam));
        if (side.lengthSqr() < 1e-10) return;
        side = side.normalize();
        for (int b = 0; b < 4; b++) {   // the dry end: bristles splitting off, broken
            double off = (b - 1.5) * W * 0.55;
            int from = (int) (N * 0.65);
            List<Vec3> seg = new ArrayList<>();
            for (int i = from; i < N; i++) {
                if (FxBase.h2(seed + b, i, 31) < 0.25) { flush(vc, m, n, cam, seg, W * 0.12, a); seg.clear(); continue; }
                double u = (i - from) / (double) Math.max(1, N - 1 - from);
                seg.add(pts[i].add(side.scale(off * (1 + u))));
            }
            flush(vc, m, n, cam, seg, W * 0.12, a);
        }
    }

    private static void flush(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, List<Vec3> seg, double w, float a) {
        if (seg.size() < 2) return;
        double[] ws = new double[seg.size()];
        java.util.Arrays.fill(ws, w);
        ink(vc, m, n, cam, seg.toArray(new Vec3[0]), ws, a);
    }

    /** A round blob of ink facing you (a glob in flight, a beast's head). */
    static void blob(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 c, double r, float a) {
        Vec3 view = c.subtract(cam);
        if (view.lengthSqr() < 1e-6 || r < 0.01) return;
        Vec3[] f = frame(view.normalize());
        int seg = 14;
        for (int i = 0; i < seg; i++) {
            double a0 = i * TAU / seg, a1 = (i + 1) * TAU / seg;
            Vec3 p0 = c.add(f[0].scale(Math.cos(a0) * r)).add(f[1].scale(Math.sin(a0) * r)), p1 = c.add(f[0].scale(Math.cos(a1) * r)).add(f[1].scale(Math.sin(a1) * r));
            FxBase.quad(vc, m, n, cam, c, c, p1, p0, INK, a, a, a, a);
        }
        Vec3 hi = c.add(f[0].scale(-r * 0.35)).add(f[1].scale(r * 0.35));   // the wet glint
        GravityFxClient.gglow(m, cam, hi, r * 0.25, 0.5F * a, PAPER);
        GravityFxClient.bring(m, cam, c, f, r, r * 0.4, SHEEN, 0.2F * a);
    }

    /** Ink spread flat on the ground: a ragged disc with drops round it. */
    static void puddle(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 c, double r, float a, int seed) {
        int seg = 24;
        Vec3[] rim = new Vec3[seg];
        for (int i = 0; i < seg; i++) {
            double an = i * TAU / seg;
            double rr = r * (0.75 + 0.35 * FxBase.h2(seed, i, 7) + 0.12 * Math.sin(an * 5 + seed));
            rim[i] = c.add(Math.cos(an) * rr, 0, Math.sin(an) * rr);
        }
        for (int i = 0; i < seg; i++) FxBase.quad(vc, m, n, cam, c, c, rim[(i + 1) % seg], rim[i], INK, a, a, a, a);
        for (int k = 0; k < 6; k++) {   // flung drops
            double an = FxBase.h2(seed, k + 40, 7) * TAU, rr = r * (1.15 + 0.5 * FxBase.h2(seed, k + 50, 7)), s = r * (0.06 + 0.08 * FxBase.h2(seed, k + 60, 7));
            Vec3 q = c.add(Math.cos(an) * rr, 0, Math.sin(an) * rr);
            for (int i = 0; i < 6; i++) {
                double a0 = i * TAU / 6, a1 = (i + 1) * TAU / 6;
                FxBase.quad(vc, m, n, cam, q, q, q.add(Math.cos(a1) * s, 0, Math.sin(a1) * s), q.add(Math.cos(a0) * s, 0, Math.sin(a0) * s), INK, a, a, a, a);
            }
        }
    }

    // ================================================================== 0 stroke
    private static void stroke(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age) {
        double k = p.speed <= 0 ? 1 : Mth.clamp(age / p.speed, 0, 1);
        float a = (float) Mth.clamp((p.life - age) / 8.0, 0, 1);
        Vec3 d = p.d.normalize(), u = p.u.normalize(), s = u.cross(d).normalize();
        d = s.cross(u).normalize();
        int N = 20;
        Vec3[] pts = new Vec3[N];
        for (int i = 0; i < N; i++) {
            double an = p.a0 * k * i / (N - 1);
            pts[i] = p.c.add(d.scale(Math.cos(an) * p.r)).add(s.scale(Math.sin(an) * p.r)).add(u.scale(0.15 * Math.sin(i * 0.7)));
        }
        brush(vc, m, n, cam, pts, 0.22 + 0.04 * p.r, a, (int) p.a1);
    }

    // ================================================================== 1 glob
    private static void glob(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age) {
        double k = Mth.clamp(age / Math.max(1, p.speed), 0, 1);
        Vec3 at = p.c.add(p.u.subtract(p.c).scale(k)).add(0, 4 * p.a0 * k * (1 - k), 0);
        double k0 = Math.max(0, k - 0.12);
        Vec3 back = p.c.add(p.u.subtract(p.c).scale(k0)).add(0, 4 * p.a0 * k0 * (1 - k0), 0);
        blob(vc, m, n, cam, at, p.r, 1F);
        ink(vc, m, n, cam, new Vec3[]{back, at}, new double[]{0.02, p.r * 0.8}, 0.9F);
    }

    // ================================================================== 2 splat
    private static void splat(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age) {
        double grow = FxBase.backOut(Mth.clamp(age / 4, 0, 1));
        float a = (float) Mth.clamp((p.life - age) / (p.life * 0.35), 0, 1) * 0.92F;
        puddle(vc, m, n, cam, p.c, p.r * grow, a, (int) p.a1);
    }

    // ================================================================== 3 serpent
    private static void serpent(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age, double now) {
        Vec3 d = p.d.normalize(), s = new Vec3(-d.z, 0, d.x);
        double run = Math.max(1, p.life - 14);
        double head = p.r * Mth.clamp(age / run, 0, 1);
        float a = (float) Mth.clamp((p.life - age) / 10.0, 0, 1);
        int N = 22;
        double len = Math.min(head + 0.01, 5.5);
        Vec3[] pts = new Vec3[N];
        double[] w = new double[N];
        for (int i = 0; i < N; i++) {
            double u = i / (double) (N - 1), x = head - len * (1 - u);
            pts[i] = p.c.add(d.scale(x)).add(s.scale(Math.sin(x * 1.6 - now * 0.5) * 0.55 * u)).add(0, 0.35 + 0.25 * Math.sin(x * 1.1) * u, 0);
            w[i] = 0.02 + 0.22 * Math.sin(Math.PI * Math.min(1, u * 1.1));
        }
        ink(vc, m, n, cam, pts, w, a);
        Vec3 hd = pts[N - 1].add(d.scale(0.25));
        blob(vc, m, n, cam, hd.add(0, 0.05, 0), 0.32, a);
        for (int e = -1; e <= 1; e += 2) GravityFxClient.gglow(m, cam, hd.add(s.scale(0.13 * e)).add(0, 0.14, 0).add(d.scale(0.12)), 0.05, 0.9F * a, PAPER);   // two pale eyes
    }

    // ================================================================== 4 seal
    private static void seal(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age, double now) {
        int seed = (int) p.a1;
        double R = p.r, wait = Math.max(1, p.sweep);
        double paint = Mth.clamp(age / (wait * 0.6), 0, 1);
        float a = (float) Mth.clamp((p.life - age) / 8.0, 0, 1);
        Vec3 c = p.c;
        int N = 40, M = Math.max(2, (int) (N * paint));
        Vec3[] ring = new Vec3[M];
        double[] w = new double[M];
        for (int i = 0; i < M; i++) {
            double an = i * TAU / (N - 1);
            ring[i] = c.add(Math.cos(an) * R, 0.02, Math.sin(an) * R);
            w[i] = 0.12;
        }
        flat(vc, m, n, cam, ring, w, a);
        for (int k = 0; k < 6; k++) {   // the characters inside, each written after the last
            double t0 = k / 6.0;
            if (paint < t0) break;
            double an = FxBase.h2(seed, k, 3) * TAU, an2 = an + 1.2 + FxBase.h2(seed, k + 10, 3) * 2;
            double r0 = R * (0.15 + 0.6 * FxBase.h2(seed, k + 20, 3)), r1 = R * (0.2 + 0.65 * FxBase.h2(seed, k + 30, 3));
            double q = Mth.clamp((paint - t0) * 6, 0, 1);
            Vec3 p0 = c.add(Math.cos(an) * r0, 0.025, Math.sin(an) * r0), p1 = c.add(Math.cos(an2) * r1, 0.025, Math.sin(an2) * r1);
            flat(vc, m, n, cam, new Vec3[]{p0, p0.add(p1.subtract(p0).scale(q))}, new double[]{0.1, 0.05}, a);
        }
        if (age >= wait) {   // it bursts: the characters rise up out of the ground in a column
            double q = Mth.clamp((age - wait) / 14, 0, 1);
            for (int k = 0; k < 14; k++) {
                double h = (FxBase.h2(seed, k + 40, 3) * 0.4 + q) * 7, an = FxBase.h2(seed, k + 50, 3) * TAU, rr = R * 0.6 * FxBase.h2(seed, k + 60, 3);
                Vec3 at = c.add(Math.cos(an) * rr, h, Math.sin(an) * rr);
                Vec3 dd = new Vec3(Math.cos(an + 1.5), 0.6 * (FxBase.h2(seed, k + 70, 3) - 0.5), Math.sin(an + 1.5)).scale(0.5);
                brush(vc, m, n, cam, new Vec3[]{at.subtract(dd), at, at.add(dd), at.add(dd.scale(1.5)).add(0, 0.1, 0)}, 0.12, (float) (1 - q) * a, seed + k);
            }
            GravityFxClient.ring(m, cam, c.add(0, 0.1, 0), UP, R * (1 + q * 0.3), 0.3, SHEEN, (float) (1 - q) * 0.6F);
        }
    }

    /** Ink laid flat on the ground through {@code pts}. */
    private static void flat(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3[] pts, double[] w, float a) {
        for (int i = 0; i < pts.length - 1; i++) {
            Vec3 p0 = pts[i], p1 = pts[i + 1], ax = p1.subtract(p0);
            if (ax.lengthSqr() < 1e-10) continue;
            Vec3 s = ax.cross(UP).normalize();
            FxBase.quad(vc, m, n, cam, p0.add(s.scale(w[i])), p1.add(s.scale(w[i + 1])), p1.subtract(s.scale(w[i + 1])), p0.subtract(s.scale(w[i])), INK, a, a, a, a);
        }
    }

    // ================================================================== 5 beast: a wolf of ink, running
    private static void beast(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age, double now) {
        double k = Mth.clamp(age / Math.max(1, p.speed), 0, 1);
        Vec3 dir = p.u.subtract(p.c);
        if (dir.lengthSqr() < 1e-6) return;
        Vec3 d = dir.normalize(), s = new Vec3(-d.z, 0, d.x).normalize();
        double bob = Math.abs(Math.sin(age * 1.4)) * 0.25;
        Vec3 at = p.c.add(dir.scale(k)).add(0, 0.55 + bob, 0);
        ink(vc, m, n, cam, new Vec3[]{at.subtract(d.scale(1.0)).add(0, 0.15, 0), at.subtract(d.scale(0.5)), at}, new double[]{0.05, 0.24, 0.2}, 1F);   // body
        blob(vc, m, n, cam, at.add(d.scale(0.25)).add(0, 0.12, 0), 0.22, 1F);   // head
        for (int e = -1; e <= 1; e += 2) {   // ears, and the pale eyes
            Vec3 ear = at.add(d.scale(0.2)).add(s.scale(0.1 * e)).add(0, 0.28, 0);
            ink(vc, m, n, cam, new Vec3[]{ear, ear.add(0, 0.22, 0).subtract(d.scale(0.05))}, new double[]{0.06, 0.01}, 1F);
            GravityFxClient.gglow(m, cam, at.add(d.scale(0.4)).add(s.scale(0.08 * e)).add(0, 0.18, 0), 0.04, 0.9F, PAPER);
        }
        for (int leg = 0; leg < 4; leg++) {   // legs, galloping
            double ph = age * 1.4 + leg * Math.PI * 0.5;
            Vec3 hip = at.subtract(d.scale(leg < 2 ? 0.0 : 0.75)).add(s.scale(leg % 2 == 0 ? 0.1 : -0.1));
            Vec3 foot = hip.add(d.scale(Math.sin(ph) * 0.3)).add(0, -0.5 - bob + 0.1 * Math.max(0, Math.cos(ph)), 0);
            ink(vc, m, n, cam, new Vec3[]{hip, foot}, new double[]{0.07, 0.03}, 1F);
        }
        Vec3 tail = at.subtract(d.scale(1.0)).add(0, 0.15, 0);   // and a tail of drips streaming off it
        ink(vc, m, n, cam, new Vec3[]{tail, tail.subtract(d.scale(0.5)).add(0, 0.2 + 0.1 * Math.sin(age), 0)}, new double[]{0.06, 0.01}, 1F);
    }

    // ================================================================== 6 drop
    private static void drop(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age) {
        double k = Mth.clamp(age / Math.max(1, p.speed), 0, 1);
        Vec3 at = p.c.add(p.u.subtract(p.c).scale(k * k));
        ink(vc, m, n, cam, new Vec3[]{at.add(0, 0.6, 0), at}, new double[]{0.005, 0.05}, 0.95F);
    }

    // ================================================================== 7 wave
    private static void wave(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age, double now) {
        Vec3 d = p.d.normalize(), s = new Vec3(-d.z, 0, d.x);
        double run = Math.max(1, p.life - 8);
        double k = Mth.clamp(age / run, 0, 1);
        float a = (float) Mth.clamp((p.life - age) / 8.0, 0, 1);
        Vec3 front = p.c.add(d.scale(p.r * k));
        int cols = 14;
        for (int j = 0; j < cols; j++) {   // the wall of ink, each column curling over at the top
            double x = (j / (double) (cols - 1) - 0.5) * p.a0;
            double h = 1.6 + 0.5 * Math.sin(j * 1.7 + now * 0.4) * (1 - Math.abs(x) / p.a0);
            Vec3 b = front.add(s.scale(x));
            Vec3[] pts = {b.subtract(d.scale(0.8)), b.add(0, h * 0.55, 0).subtract(d.scale(0.2)), b.add(0, h, 0).add(d.scale(0.15)), b.add(0, h * 0.82, 0).add(d.scale(0.55))};
            ink(vc, m, n, cam, pts, new double[]{0.3, 0.32, 0.22, 0.04}, a);
        }
        puddle(vc, m, n, cam, front.subtract(d.scale(1.2)).add(0, 0.02, 0), p.a0 * 0.35, a * 0.8F, (int) (p.id * 3));
    }

    // ================================================================== 8 the grand character
    private static void grand(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age, double now) {
        int seed = (int) p.a1;
        double paint = Mth.clamp(age / Math.max(1, p.sweep), 0, 1);
        double fall = age <= p.sweep ? 0 : Mth.clamp((age - p.sweep) / Math.max(1, p.speed), 0, 1);
        Vec3 c = p.c.add(p.u.subtract(p.c).scale(fall * fall));
        double S = p.r * (1 - 0.3 * fall);
        Vec3 view = flatv(c.subtract(cam));
        Vec3 right = new Vec3(-view.z, 0, view.x);
        int strokes = 7;
        for (int k = 0; k < strokes; k++) {   // stroke by stroke, a character of my own (no real script)
            double t0 = k / (double) strokes;
            if (paint < t0) break;
            double q = Mth.clamp((paint - t0) * strokes, 0, 1);
            double x0 = FxBase.h2(seed, k, 5) - 0.5, y0 = FxBase.h2(seed, k + 9, 5) - 0.5;
            boolean horiz = k % 3 == 0, hook = k % 3 == 2;
            double x1 = horiz ? -x0 : x0 + 0.1 * (FxBase.h2(seed, k + 17, 5) - 0.5), y1 = horiz ? y0 + 0.05 : -y0 - 0.2;
            int N = 8;
            Vec3[] pts = new Vec3[N];
            for (int i = 0; i < N; i++) {
                double u = q * i / (N - 1);
                double x = x0 + (x1 - x0) * u + (hook ? 0.15 * Math.sin(u * Math.PI) : 0), y = y0 + (y1 - y0) * u;
                pts[i] = c.add(right.scale(x * S)).add(0, y * S, 0);
            }
            brush(vc, m, n, cam, pts, 0.11 * S, 1F, seed + k);
        }
        if (fall > 0) GravityFxClient.gseg(m, cam, c.add(0, S * 0.5, 0), c.add(0, S * 2.2, 0), S * 0.4, 0.3F, 0, SHEEN);
    }

    // ================================================================== 9 Masterpiece: strokes curling round you
    private static void aura(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Minecraft mc, FxPacket p, double age, double now, float pt) {
        Entity e = mc.level.getEntity((int) p.v.x);
        if (e == null) return;
        if (e == mc.player && mc.options.getCameraType().isFirstPerson()) return;
        Vec3 f = new Vec3(Mth.lerp(pt, e.xo, e.getX()), Mth.lerp(pt, e.yo, e.getY()), Mth.lerp(pt, e.zo, e.getZ()));
        float a = (float) (Mth.clamp(age / 10, 0, 1) * Mth.clamp((p.life - age) / 20, 0, 1));
        for (int k = 0; k < 3; k++) {
            double y = e.getBbHeight() * (0.25 + 0.3 * k), base = now * (0.07 + 0.02 * k) * (k % 2 == 0 ? 1 : -1) + k * 2;
            int N = 12;
            Vec3[] pts = new Vec3[N];
            for (int i = 0; i < N; i++) {
                double an = base + i * 0.22;
                pts[i] = f.add(Math.cos(an) * 0.85, y + 0.12 * Math.sin(an * 2), Math.sin(an) * 0.85);
            }
            brush(vc, m, n, cam, pts, 0.09, 0.9F * a, k * 7 + (int) (now / 20));
        }
        for (int i = 0; i < 4; i++) {   // drips falling off the strokes
            double ph = (now / 14.0 + i / 4.0) % 1.0, an = i * 1.6 + now * 0.05;
            Vec3 q = f.add(Math.cos(an) * 0.85, e.getBbHeight() * (0.25 + 0.3 * (i % 3)) - ph * 1.2, Math.sin(an) * 0.85);
            ink(vc, m, n, cam, new Vec3[]{q.add(0, 0.18, 0), q}, new double[]{0.005, 0.035}, (float) (1 - ph) * a);
        }
        puddle(vc, m, n, cam, f.add(0, 0.02, 0), 0.9, 0.5F * a, 11);
    }

    // ================================================================== 10 a small seal flaring
    private static void glyph(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age) {
        float a = (float) Mth.clamp((p.life - age) / 6.0, 0, 1);
        double r = p.r * FxBase.backOut(Mth.clamp(age / 4, 0, 1));
        Vec3 view = p.c.subtract(cam);
        if (view.lengthSqr() < 1e-6) return;
        Vec3[] f = frame(view.normalize());
        int N = 20;
        Vec3[] pts = new Vec3[N];
        for (int i = 0; i < N; i++) { double an = i * TAU / (N - 1) + 0.3; pts[i] = p.c.add(f[0].scale(Math.cos(an) * r)).add(f[1].scale(Math.sin(an) * r)); }
        brush(vc, m, n, cam, pts, 0.07 * p.r + 0.03, a, (int) p.a1);
        int seed = (int) p.a1;
        for (int k = 0; k < 3; k++) {
            double a0 = FxBase.h2(seed, k, 9) * TAU, a1 = a0 + 2 + FxBase.h2(seed, k + 3, 9);
            brush(vc, m, n, cam, new Vec3[]{p.c.add(f[0].scale(Math.cos(a0) * r * 0.7)).add(f[1].scale(Math.sin(a0) * r * 0.7)), p.c, p.c.add(f[0].scale(Math.cos(a1) * r * 0.6)).add(f[1].scale(Math.sin(a1) * r * 0.6))}, 0.05 * p.r + 0.02, a, seed + k);
        }
        GravityFxClient.gglow(m, cam, p.c, r * 1.3, 0.35F * a, SHEEN);
    }

    // ================================================================== helpers
    private static Vec3[] frame(Vec3 axis) {
        Vec3 e1 = Math.abs(axis.y) > 0.9 ? new Vec3(1, 0, 0) : UP;
        e1 = e1.subtract(axis.scale(e1.dot(axis))).normalize();
        return new Vec3[]{e1, axis.cross(e1)};
    }

    private static Vec3 flatv(Vec3 v) {
        Vec3 f = new Vec3(v.x, 0, v.z);
        return f.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : f.normalize();
    }
}
