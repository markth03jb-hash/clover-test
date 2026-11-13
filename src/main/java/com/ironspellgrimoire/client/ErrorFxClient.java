package com.ironspellgrimoire.client;

import com.ironspellgrimoire.network.FxPacket;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * The Error Grimoire, drawn: corrupted magic. Everything is drawn with its colours slipped apart (a magenta copy one
 * way, a cyan copy the other, white where they meet), in squares of dead black pixels, and it flickers - each piece
 * re-rolls its glitch a few times a second. Fed by FxPacket shape 86 (server/ErrorFx), {@code h} = kind:
 * 0 bolt, 1 dead pixels, 2 threads, 3 frozen frame, 4 crash zone, 5 error window, 6 blue screen, 7 tear,
 * 8 Fatal Error, 9 afterimage.
 */
public final class ErrorFxClient {
    private ErrorFxClient() {}

    private static final double TAU = Math.PI * 2;
    private static final Vec3 UP = new Vec3(0, 1, 0);
    static final float[] MAG = {1.0F, 0.15F, 0.75F}, CYA = {0.15F, 0.95F, 1.0F}, WHITE = {1.0F, 1.0F, 1.0F}, RED = {1.0F, 0.12F, 0.2F},
            BLUE = {0.15F, 0.35F, 1.0F}, BLACK = {0.0F, 0.0F, 0.0F}, PANEL = {0.05F, 0.04F, 0.08F}, BAR = {0.75F, 0.05F, 0.35F};

    private static final class L {
        final FxPacket p;
        final double born;
        L(FxPacket p, double born) { this.p = p; this.born = born; }
    }

    private static final List<L> LIVE = new ArrayList<>();

    public static void spawn(FxPacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
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
            int seed = (int) p.a1 + (int) (now / 2) * 7919;   // the glitch re-rolls every other tick
            switch ((int) p.h) {
                case 0 -> bolt(m, cam, p, age, seed);
                case 1 -> pixels(vc, m, n, cam, p.c, p.r * (1 + 0.4 * age / p.life), (float) (1 - age / p.life), seed, 16);
                case 2 -> threads(vc, m, n, cam, mc, p, age, now, pt, seed);
                case 3 -> frozen(m, cam, mc, p, age, now, pt, seed);
                case 4 -> zone(vc, m, n, cam, p, age, now, seed);
                case 5 -> window(vc, m, n, cam, p, age);
                case 6 -> cube(vc, m, n, cam, p, age, seed);
                case 7 -> tear(vc, m, n, cam, p, age, seed);
                case 8 -> aura(vc, m, n, cam, mc, p, age, now, pt, seed);
                case 9 -> ghost(m, cam, p, age, seed);
                default -> LIVE.remove(l);
            }
        }
    }

    // ================================================================== the glitch
    /** A line drawn three times: a magenta copy pushed one way, a cyan copy the other, a thin white core. */
    static void split(Matrix4f m, Vec3 cam, Vec3 a, Vec3 b, double w, float al, double off) {
        Vec3 ax = b.subtract(a);
        if (ax.lengthSqr() < 1e-8) return;
        Vec3 s = ax.cross(a.subtract(cam));
        if (s.lengthSqr() < 1e-8) return;
        s = s.normalize().scale(off);
        GravityFxClient.gseg(m, cam, a.add(s), b.add(s), w, al * 0.8F, al * 0.8F, MAG);
        GravityFxClient.gseg(m, cam, a.subtract(s), b.subtract(s), w, al * 0.8F, al * 0.8F, CYA);
        GravityFxClient.gseg(m, cam, a, b, w * 0.4, al, al, WHITE);
    }

    /** A square facing you, solid. */
    static void square(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 c, double s, float[] col, float a) {
        Vec3 view = c.subtract(cam);
        if (view.lengthSqr() < 1e-6) return;
        Vec3[] f = frame(view.normalize());
        Vec3 x = f[0].scale(s), y = f[1].scale(s);
        FxBase.quad(vc, m, n, cam, c.subtract(x).subtract(y), c.add(x).subtract(y), c.add(x).add(y), c.subtract(x).add(y), col, a, a, a, a);
    }

    /** A scatter of dead pixels: black squares, a few lit magenta or cyan, blinking. */
    static void pixels(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 c, double r, float a, int seed, int count) {
        for (int i = 0; i < count; i++) {
            if (FxBase.h2(seed, i, 1) < 0.25) continue;   // blinking
            Vec3 q = c.add((FxBase.h2(seed, i, 2) - 0.5) * 2 * r, (FxBase.h2(seed, i, 3) - 0.5) * 2 * r, (FxBase.h2(seed, i, 4) - 0.5) * 2 * r);
            double s = 0.05 + 0.12 * FxBase.h2(seed, i, 5);
            double pick = FxBase.h2(seed, i, 6);
            if (pick < 0.6) square(vc, m, n, cam, q, s, BLACK, 0.95F * a);
            else GravityFxClient.gglow(m, cam, q, s * 1.6, 0.9F * a, pick < 0.8 ? MAG : CYA);
        }
    }

    // ================================================================== 0 bolt
    private static void bolt(Matrix4f m, Vec3 cam, FxPacket p, double age, int seed) {
        float a = (float) (1 - age / p.life);
        Vec3 ax = p.u.subtract(p.c);
        Vec3[] f = frame(ax.lengthSqr() < 1e-6 ? UP : ax.normalize());
        int N = 9;
        Vec3 prev = p.c;
        for (int i = 1; i <= N; i++) {
            double u = i / (double) N;
            Vec3 q = p.c.add(ax.scale(u));
            if (i < N) q = q.add(f[0].scale((FxBase.h2(seed, i, 11) - 0.5) * 0.9)).add(f[1].scale((FxBase.h2(seed, i, 12) - 0.5) * 0.9));
            Vec3 knee = new Vec3(q.x, prev.y, prev.z);   // jagged: it moves in right angles, like a broken line on a screen
            split(m, cam, prev, knee, p.r, a, 0.07);
            split(m, cam, knee, q, p.r, a, 0.07);
            prev = q;
        }
    }

    // ================================================================== 2 threads
    private static void threads(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Minecraft mc, FxPacket p, double age, double now, float pt, int seed) {
        Entity e = mc.level.getEntity((int) p.v.x);
        if (e == null) return;
        Vec3 t = new Vec3(Mth.lerp(pt, e.xo, e.getX()), Mth.lerp(pt, e.yo, e.getY()) + e.getBbHeight() * 0.5, Mth.lerp(pt, e.zo, e.getZ()));
        float a = (float) (Mth.clamp(age / 3, 0, 1) * Mth.clamp((p.life - age) / 6, 0, 1));
        Vec3 ax = t.subtract(p.c);
        Vec3[] f = frame(ax.lengthSqr() < 1e-6 ? UP : ax.normalize());
        for (int k = 0; k < 5; k++) {   // the threads, wavering, glitching
            int N = 12;
            Vec3 prev = p.c;
            for (int i = 1; i <= N; i++) {
                double u = i / (double) N;
                double sw = Math.sin(u * Math.PI) * (0.35 + 0.1 * k);
                double an = k * TAU / 5 + now * 0.2 + u * 2;
                Vec3 q = p.c.add(ax.scale(u)).add(f[0].scale(Math.cos(an) * sw)).add(f[1].scale(Math.sin(an) * sw));
                if (FxBase.h2(seed + k, i, 13) < 0.15) q = q.add(f[0].scale(0.25));   // a skipped frame
                GravityFxClient.gseg(m, cam, prev, q, 0.025, a, a, k % 2 == 0 ? CYA : MAG);
                prev = q;
            }
        }
        for (int k = 0; k < 4; k++) {   // wound round the foe
            double y = e.getBbHeight() * (0.2 + 0.2 * k) - e.getBbHeight() * 0.5;
            GravityFxClient.ring(m, cam, t.add(0, y, 0), UP, Math.max(0.35, e.getBbWidth() * 0.7), 0.04, k % 2 == 0 ? CYA : MAG, 0.8F * a);
        }
        pixels(vc, m, n, cam, t, 0.6, a * 0.7F, seed, 6);
    }

    // ================================================================== 3 frozen frame
    private static void frozen(Matrix4f m, Vec3 cam, Minecraft mc, FxPacket p, double age, double now, float pt, int seed) {
        Entity e = mc.level.getEntity((int) p.v.x);
        if (e == null) return;
        AABB b = e.getBoundingBox().inflate(0.15);
        float a = (float) (Mth.clamp(age / 2, 0, 1) * Mth.clamp((p.life - age) / 4, 0, 1)) * (FxBase.h2(seed, 1, 14) < 0.15 ? 0.3F : 1F);
        box(m, cam, b, 0.03, WHITE, a);
        box(m, cam, b.move(0.06, 0, 0), 0.03, MAG, a * 0.7F);
        box(m, cam, b.move(-0.06, 0, 0), 0.03, CYA, a * 0.7F);
        for (int i = 0; i < 6; i++) {   // scanlines
            double y = b.minY + (b.maxY - b.minY) * ((i / 6.0 + now * 0.02) % 1.0);
            GravityFxClient.gseg(m, cam, new Vec3(b.minX, y, b.minZ), new Vec3(b.maxX, y, b.maxZ), 0.015, 0.4F * a, 0.4F * a, WHITE);
        }
    }

    private static void box(Matrix4f m, Vec3 cam, AABB b, double w, float[] col, float a) {
        Vec3[] c = {new Vec3(b.minX, b.minY, b.minZ), new Vec3(b.maxX, b.minY, b.minZ), new Vec3(b.maxX, b.minY, b.maxZ), new Vec3(b.minX, b.minY, b.maxZ),
                new Vec3(b.minX, b.maxY, b.minZ), new Vec3(b.maxX, b.maxY, b.minZ), new Vec3(b.maxX, b.maxY, b.maxZ), new Vec3(b.minX, b.maxY, b.maxZ)};
        int[][] edges = {{0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6}, {6, 7}, {7, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
        for (int[] ed : edges) GravityFxClient.gseg(m, cam, c[ed[0]], c[ed[1]], w, a, a, col);
    }

    // ================================================================== 4 crash zone
    private static void zone(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age, double now, int seed) {
        float a = (float) (Mth.clamp(age / 6, 0, 1) * Mth.clamp((p.life - age) / 10, 0, 1));
        double R = p.r, s = 0.5;
        int g = (int) Math.ceil(R / s);
        for (int i = -g; i <= g; i++) for (int j = -g; j <= g; j++) {   // the ground's pixels going bad
            double x = i * s, z = j * s;
            if (x * x + z * z > R * R) continue;
            double h = FxBase.h2(seed, (i + 50) * 131 + j + 50, 15);
            if (h > 0.22) continue;
            Vec3 q = p.c.add(x, 0.03, z);
            float[] col = h < 0.1 ? BLACK : h < 0.16 ? MAG : CYA;
            Vec3 o = new Vec3(s * 0.48, 0, 0), w = new Vec3(0, 0, s * 0.48);
            if (col == BLACK) FxBase.quad(vc, m, n, cam, q.subtract(o).subtract(w), q.add(o).subtract(w), q.add(o).add(w), q.subtract(o).add(w), col, 0.85F * a, 0.85F * a, 0.85F * a, 0.85F * a);
            else FxBase.gquad(m, cam, q.subtract(o).subtract(w), q.add(o).subtract(w), q.add(o).add(w), q.subtract(o).add(w), col, 0.45F * a, 0.45F * a, 0.45F * a, 0.45F * a);
        }
        GravityFxClient.ring(m, cam, p.c.add(0.08, 0.05, 0), UP, R, 0.06, MAG, 0.7F * a);
        GravityFxClient.ring(m, cam, p.c.add(-0.08, 0.05, 0), UP, R, 0.06, CYA, 0.7F * a);
        GravityFxClient.ring(m, cam, p.c.add(0, 0.05, 0), UP, R, 0.02, WHITE, 0.8F * a);
        pixels(vc, m, n, cam, p.c.add(0, 1, 0), R * 0.6, a * 0.8F, seed, 18);
    }

    // ================================================================== 5 error window
    private static void window(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age) {
        double slam = p.sweep <= 0 ? 1 : Mth.clamp((age - p.sweep) / 4.0, 0, 1);
        double pop = FxBase.backOut(Mth.clamp(age / 3, 0, 1));
        Vec3 c = p.c.add(p.u.subtract(p.c).scale(slam * slam));
        float a = (float) Mth.clamp((p.life - age) / 3.0, 0, 1);
        Vec3 face = p.d.lengthSqr() < 1e-6 ? c.subtract(cam).normalize() : p.d.normalize();
        Vec3 right = face.cross(UP);
        right = right.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : right.normalize();
        Vec3 up = right.cross(face).normalize();
        double W = p.r * 0.8 * pop, H = p.r * 0.5 * pop;
        Vec3 x = right.scale(W), y = up.scale(H);
        Vec3 bl = c.subtract(x).subtract(y), br = c.add(x).subtract(y), tr = c.add(x).add(y), tl = c.subtract(x).add(y);
        FxBase.quad(vc, m, n, cam, bl, br, tr, tl, PANEL, 0.9F * a, 0.9F * a, 0.9F * a, 0.9F * a);
        FxBase.quad(vc, m, n, cam, br, bl, tl, tr, PANEL, 0.9F * a, 0.9F * a, 0.9F * a, 0.9F * a);
        Vec3 barLo = c.add(y.scale(0.62));   // the title bar
        FxBase.gquad(m, cam, barLo.subtract(x), barLo.add(x), tr, tl, BAR, a, a, a, a);
        Vec3 xc = tr.subtract(right.scale(W * 0.12)).subtract(up.scale(H * 0.19)), xs = right.scale(W * 0.06), ys = up.scale(H * 0.1);   // its little X
        GravityFxClient.gseg(m, cam, xc.subtract(xs).subtract(ys), xc.add(xs).add(ys), 0.02, a, a, WHITE);
        GravityFxClient.gseg(m, cam, xc.subtract(xs).add(ys), xc.add(xs).subtract(ys), 0.02, a, a, WHITE);
        Vec3 ic = c.subtract(x.scale(0.55)).subtract(y.scale(0.1));   // a warning mark, and lines of broken text
        GravityFxClient.gglow(m, cam, ic, H * 0.22, a, RED);
        for (int i = 0; i < 3; i++) {
            Vec3 l0 = c.subtract(x.scale(0.25)).add(y.scale(0.25 - i * 0.28));
            GravityFxClient.gseg(m, cam, l0, l0.add(x.scale(0.4 + 0.5 * FxBase.h2(p.id, i, 16))), 0.018, 0.8F * a, 0.8F * a, WHITE);
        }
        box(m, cam, new AABB(bl, tr), 0.02, CYA, 0.6F * a);
    }

    // ================================================================== 6 blue screen: a cube of dead pixels closing
    private static void cube(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age, int seed) {
        double k = Mth.clamp(age / Math.max(1, p.life - 8), 0, 1);
        float a = (float) Mth.clamp((p.life - age) / 4.0, 0, 1);
        double R = p.r * (1.5 - 0.5 * k);
        box(m, cam, new AABB(p.c.subtract(R, R, R), p.c.add(R, R, R)), 0.05, BLUE, 0.9F * a);
        int per = 6;
        double s = 2 * R / per;
        Vec3[][] faces = {{new Vec3(1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, 0, 1)}, {new Vec3(-1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, 0, 1)}, {new Vec3(0, 1, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1)},
                {new Vec3(0, -1, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1)}, {new Vec3(0, 0, 1), new Vec3(1, 0, 0), new Vec3(0, 1, 0)}, {new Vec3(0, 0, -1), new Vec3(1, 0, 0), new Vec3(0, 1, 0)}};
        for (int fi = 0; fi < 6; fi++) {
            Vec3 nrm = faces[fi][0], ua = faces[fi][1], va = faces[fi][2];
            for (int i = 0; i < per; i++) for (int j = 0; j < per; j++) {
                double h = FxBase.h2((int) p.a1 + fi * 97, i * per + j, 17);
                if (h > k) continue;   // the cube fills in, pixel by pixel
                Vec3 q = p.c.add(nrm.scale(R)).add(ua.scale(-R + s * (i + 0.5))).add(va.scale(-R + s * (j + 0.5)));
                Vec3 o = ua.scale(s * 0.46), w = va.scale(s * 0.46);
                float[] col = FxBase.h2(seed, fi * 50 + i * per + j, 18) < 0.1 ? BLUE : BLACK;
                FxBase.quad(vc, m, n, cam, q.subtract(o).subtract(w), q.add(o).subtract(w), q.add(o).add(w), q.subtract(o).add(w), col, 0.92F * a, 0.92F * a, 0.92F * a, 0.92F * a);
                FxBase.quad(vc, m, n, cam, q.subtract(o).add(w), q.add(o).add(w), q.add(o).subtract(w), q.subtract(o).subtract(w), col, 0.92F * a, 0.92F * a, 0.92F * a, 0.92F * a);
            }
        }
        GravityFxClient.gglow(m, cam, p.c, R * 1.4, 0.25F * a, BLUE);
    }

    // ================================================================== 7 tear: reality ripping open in columns
    private static void tear(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age, int seed) {
        double build = Math.max(1, p.sweep);
        double k = Mth.clamp(age / build, 0, 1);
        float a = age < build ? 1F : (float) Mth.clamp((p.life - age) / (p.life - build), 0, 1);
        int cols = 14;
        Vec3 view = p.c.subtract(cam);
        Vec3 right = new Vec3(-view.z, 0, view.x);
        right = right.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : right.normalize();
        for (int i = 0; i < cols; i++) {
            if (FxBase.h2((int) p.a1, i, 19) > k) continue;   // more tear open as it builds
            double an = FxBase.h2((int) p.a1, i + 20, 19) * TAU, rr = Math.sqrt(FxBase.h2((int) p.a1, i + 40, 19)) * p.r;
            Vec3 b = p.c.add(Math.cos(an) * rr, 0, Math.sin(an) * rr);
            double H = 3 + 6 * FxBase.h2((int) p.a1, i + 60, 19), w = 0.12 + 0.3 * FxBase.h2((int) p.a1, i + 80, 19);
            double jit = (FxBase.h2(seed, i, 20) - 0.5) * 0.15;
            Vec3 o = right.scale(w + jit), top = b.add(0, H, 0);
            FxBase.quad(vc, m, n, cam, b.subtract(o), b.add(o), top.add(o), top.subtract(o), BLACK, 0.95F * a, 0.95F * a, 0.95F * a, 0.95F * a);
            FxBase.quad(vc, m, n, cam, b.add(o), b.subtract(o), top.subtract(o), top.add(o), BLACK, 0.95F * a, 0.95F * a, 0.95F * a, 0.95F * a);
            split(m, cam, b.add(o.scale(1.1)), top.add(o.scale(1.1)), 0.04, a, 0.06);
            split(m, cam, b.subtract(o.scale(1.1)), top.subtract(o.scale(1.1)), 0.04, a, 0.06);
        }
        GravityFxClient.ring(m, cam, p.c.add(0, 0.05, 0), UP, p.r, 0.1, MAG, 0.6F * a);
        pixels(vc, m, n, cam, p.c.add(0, 2, 0), p.r * 0.7, a, seed, 24);
        if (age > build) {   // the crash: everything goes white for a moment
            double q = Mth.clamp((age - build) / 6, 0, 1);
            GravityFxClient.gglow(m, cam, p.c.add(0, 2, 0), p.r * (0.6 + q), (float) (1 - q) * 0.8F, WHITE);
        }
    }

    // ================================================================== 8 Fatal Error
    private static void aura(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Minecraft mc, FxPacket p, double age, double now, float pt, int seed) {
        Entity e = mc.level.getEntity((int) p.v.x);
        if (e == null) return;
        if (e == mc.player && mc.options.getCameraType().isFirstPerson()) return;
        Vec3 f = new Vec3(Mth.lerp(pt, e.xo, e.getX()), Mth.lerp(pt, e.yo, e.getY()), Mth.lerp(pt, e.zo, e.getZ()));
        float a = (float) (Mth.clamp(age / 10, 0, 1) * Mth.clamp((p.life - age) / 20, 0, 1));
        AABB b = e.getBoundingBox().move(f.subtract(e.position())).inflate(0.1);
        if (FxBase.h2(seed, 0, 21) < 0.5) {   // the outline slips apart now and then
            box(m, cam, b.move(0.08, 0, 0), 0.02, MAG, 0.6F * a);
            box(m, cam, b.move(-0.08, 0, 0), 0.02, CYA, 0.6F * a);
        }
        pixels(vc, m, n, cam, f.add(0, e.getBbHeight() * 0.5, 0), 0.8, a, seed, 12);
        GravityFxClient.ring(m, cam, f.add(0, 0.05, 0), UP, 1.0, 0.05, FxBase.h2(seed, 1, 21) < 0.5 ? MAG : CYA, 0.6F * a);
    }

    // ================================================================== 9 afterimage
    private static void ghost(Matrix4f m, Vec3 cam, FxPacket p, double age, int seed) {
        float a = (float) (1 - age / p.life);
        double H = Math.max(1, p.r);
        Vec3 view = p.c.subtract(cam);
        Vec3 right = new Vec3(-view.z, 0, view.x);
        right = right.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : right.normalize();
        int N = 12;
        for (int i = 0; i < N; i++) {   // the body in torn scanlines, each slipped sideways a little
            double u = (i + 0.5) / N, y = H * u;
            double w = u > 0.78 ? 0.24 : u > 0.72 ? 0.1 : u > 0.38 ? 0.32 : 0.2;
            double slip = (FxBase.h2(seed, i, 22) - 0.5) * 0.3;
            Vec3 c = p.c.add(0, y, 0).add(right.scale(slip));
            split(m, cam, c.subtract(right.scale(w)), c.add(right.scale(w)), H / N * 0.4, a * 0.8F, 0.05);
        }
    }

    // ================================================================== helpers
    private static Vec3[] frame(Vec3 axis) {
        Vec3 e1 = Math.abs(axis.y) > 0.9 ? new Vec3(1, 0, 0) : UP;
        e1 = e1.subtract(axis.scale(e1.dot(axis))).normalize();
        return new Vec3[]{e1, axis.cross(e1)};
    }
}
