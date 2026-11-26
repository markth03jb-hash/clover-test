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
 * The Vector Grimoire, drawn: force made visible, white and black. Its own look:
 * <ul>
 *   <li>0 a vector: a white line with a black edge and a sharp head - and where something is turned, a hard angle in
 *       it with a star of light at the corner;</li>
 *   <li>1 warped air: thin shells round a point, wobbling, as if the air itself were bent;</li>
 *   <li>2 Pressure Gust: rings of compressed air punching down a lane, streaks running with them;</li>
 *   <li>3 Plasma Point charging: a white-hot point, crackling, rings of air crushed in on it again and again;</li>
 *   <li>4 the plasma bursting: a flash, shells of light, a black shock-ring on the ground, sparks flung out;</li>
 *   <li>5 Wind Vector: a column of air wrung tight - black and white bands spiralling up, dust round its foot,
 *       streaks pulled in;</li>
 *   <li>6 Vector Absolute: two jagged black jets torn out of your back like wings, white at the edges, fluttering,
 *       flicking off shards at the tips;</li>
 *   <li>7 a step on the air: a hexagon of compressed air snapping out under you, vectors kicked down;</li>
 *   <li>8 the turning field: short vectors sliding round you on a sphere;</li>
 *   <li>9 a bundle of vectors.</li>
 * </ul>
 * Fed by FxPacket shape 100 (server/VectorFx): {@code h} = kind, {@code v.x} = the entity it rides (or -1).
 */
public final class VectorFxClient {
    private VectorFxClient() {}

    private static final double TAU = Math.PI * 2;
    private static final Vec3 UP = new Vec3(0, 1, 0);
    static final float[] WHITE = {1, 1, 1}, ICE = {0.75F, 0.9F, 1.0F}, BLACK = {0.02F, 0.02F, 0.03F}, HOT = {1.0F, 0.95F, 0.85F}, CYAN = {0.55F, 0.85F, 1.0F};

    private static final class L {
        FxPacket p;
        double born;
        L(FxPacket p, double born) { this.p = p; this.born = born; }
    }

    private static final List<L> LIVE = new ArrayList<>();

    public static void spawn(FxPacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        int k = (int) p.h;
        double now = mc.level.getGameTime();
        if (k == 98) { LIVE.removeIf(l -> (int) l.p.v.x == (int) p.v.x && (int) l.p.h == (int) p.a0); return; }
        if (k == 6 || k == 8) {   // one per mage: a re-send refreshes it
            for (L l : LIVE) if ((int) l.p.h == k && (int) l.p.v.x == (int) p.v.x) { double age = now - l.born; l.p = p; l.born = now - Math.min(age, 10); return; }
        }
        if (LIVE.size() > 400) LIVE.remove(0);
        LIVE.add(new L(p, now));
    }

    static void clear() { LIVE.clear(); }

    static void draw(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, double now, float pt) {
        if (LIVE.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { LIVE.clear(); return; }
        for (int i = LIVE.size() - 1; i >= 0; i--) {
            L l = LIVE.get(i);
            double age = now - l.born;
            if (age > l.p.life) { LIVE.remove(i); continue; }
            FxPacket p = l.p;
            switch ((int) p.h) {
                case 0 -> vector(vc, m, n, cam, p, age);
                case 1 -> warp(m, cam, p, age, now);
                case 2 -> gust(vc, m, n, cam, p, age, now);
                case 3 -> charge(m, cam, p, age, now);
                case 4 -> burst(vc, m, n, cam, p, age, now);
                case 5 -> column(vc, m, n, cam, p, age, now);
                case 6 -> jets(vc, m, n, cam, mc, p, age, now, pt);
                case 7 -> step(vc, m, n, cam, p, age);
                case 8 -> field(vc, m, n, cam, mc, p, age, now, pt);
                case 9 -> bundle(vc, m, n, cam, p, age);
                default -> LIVE.remove(i);
            }
        }
    }

    // ------------------------------------------------------------------ helpers
    private static Vec3[] frame(Vec3 d) {
        Vec3 a = Math.abs(d.y) > 0.9 ? new Vec3(1, 0, 0) : UP;
        Vec3 x = d.cross(a).normalize();
        return new Vec3[]{x, x.cross(d).normalize()};
    }

    private static Vec3 at(Minecraft mc, FxPacket p, float pt) {
        Entity e = mc.level.getEntity((int) p.v.x);
        return e == null ? null : new Vec3(Mth.lerp(pt, e.xo, e.getX()), Mth.lerp(pt, e.yo, e.getY()), Mth.lerp(pt, e.zo, e.getZ()));
    }

    private static double hash(double x) { double s = Math.sin(x * 127.1 + 311.7) * 43758.5453; return s - Math.floor(s); }

    /** A solid (not glowing) camera-facing strip - the black of the jets, the outline of a vector. */
    static void sstrip(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3[] pts, double[] w, float[] al, float[] col) {
        for (int i = 0; i < pts.length - 1; i++) {
            Vec3 a = pts[i], b = pts[i + 1], ax = b.subtract(a);
            if (ax.lengthSqr() < 1e-10) continue;
            Vec3 s = ax.cross(a.subtract(cam));
            if (s.lengthSqr() < 1e-10) continue;
            s = s.normalize();
            Vec3 p0 = a.add(s.scale(w[i])), p1 = b.add(s.scale(w[i + 1])), p2 = b.subtract(s.scale(w[i + 1])), p3 = a.subtract(s.scale(w[i]));
            FxBase.quad(vc, m, n, cam, p0, p1, p2, p3, col, al[i], al[i + 1], al[i + 1], al[i]);
            FxBase.quad(vc, m, n, cam, p3, p2, p1, p0, col, al[i], al[i + 1], al[i + 1], al[i]);   // both faces: seen from either side
        }
    }

    static void sseg(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 a, Vec3 b, double w, float aa, float ab, float[] col) {
        sstrip(vc, m, n, cam, new Vec3[]{a, b}, new double[]{w, w}, new float[]{aa, ab}, col);
    }

    /** One vector a -> b: a black edge, a white core, a sharp head. */
    static void arrow(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 a, Vec3 b, double w, float al, boolean head) {
        sseg(vc, m, n, cam, a, b, w * 1.9, al * 0.9F, al * 0.9F, BLACK);
        GravityFxClient.gseg(m, cam, a, b, w, al * 0.4F, al, WHITE);
        if (!head) return;
        Vec3 d = b.subtract(a);
        if (d.lengthSqr() < 1e-6) return;
        d = d.normalize();
        Vec3 side = d.cross(b.subtract(cam)).normalize();
        double hl = Math.min(0.55, a.distanceTo(b) * 0.35);
        Vec3 h1 = b.subtract(d.scale(hl)).add(side.scale(hl * 0.55)), h2 = b.subtract(d.scale(hl)).subtract(side.scale(hl * 0.55));
        sseg(vc, m, n, cam, b, h1, w * 1.7, al, al, BLACK); sseg(vc, m, n, cam, b, h2, w * 1.7, al, al, BLACK);
        GravityFxClient.gseg(m, cam, b, h1, w * 0.9, al, al, WHITE); GravityFxClient.gseg(m, cam, b, h2, w * 0.9, al, al, WHITE);
    }

    /** A four-pointed star of light (where a vector turns). */
    static void star(Matrix4f m, Vec3 cam, Vec3 c, double r, float a) {
        Vec3[] f = frame(c.subtract(cam).normalize());
        GravityFxClient.gseg(m, cam, c.subtract(f[0].scale(r)), c.add(f[0].scale(r)), r * 0.08, a, a, WHITE);
        GravityFxClient.gseg(m, cam, c.subtract(f[1].scale(r * 1.4)), c.add(f[1].scale(r * 1.4)), r * 0.08, a, a, WHITE);
        GravityFxClient.gglow(m, cam, c, r * 0.5, a, ICE);
    }

    // ------------------------------------------------------------------ 0 a vector, turned
    private static void vector(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age) {
        float a = (float) Mth.clamp((p.life - age) / 5.0, 0, 1);
        boolean bent = p.u.distanceToSqr(p.d) > 1e-4;
        double grow = Math.min(1, age / 2.5);
        double w = Math.max(0.025, p.r);
        if (!bent) { arrow(vc, m, n, cam, p.c, p.c.add(p.d.subtract(p.c).scale(grow)), w, a, true); return; }
        double l1 = p.c.distanceTo(p.u), l2 = p.u.distanceTo(p.d), u = grow * (l1 + l2);
        if (u <= l1) { arrow(vc, m, n, cam, p.c, p.c.add(p.u.subtract(p.c).scale(u / l1)), w, a * 0.7F, false); return; }
        arrow(vc, m, n, cam, p.c, p.u, w, a * 0.55F, false);   // the way it came, already fading
        arrow(vc, m, n, cam, p.u, p.u.add(p.d.subtract(p.u).scale((u - l1) / l2)), w * 1.3, a, true);   // the way it's going now
        star(m, cam, p.u, 0.45 * (1 - age / p.life) + 0.15, a);
    }

    // ------------------------------------------------------------------ 1 warped air
    private static void warp(Matrix4f m, Vec3 cam, FxPacket p, double age, double t) {
        double q = age / p.life;
        float a = (float) Math.sin(Math.min(1, q) * Math.PI) * 0.8F;
        double r = p.r * (0.85 + 0.25 * q);
        for (int k = 0; k < 4; k++) {   // shells at odd tilts, each wobbling
            double s = p.a1 + k * 1.9;
            Vec3 ax = new Vec3(Math.sin(s + t * 0.04), Math.cos(s * 1.3), Math.sin(s * 0.7 - t * 0.03)).normalize();
            Vec3[] f = frame(ax);
            int N = 40;
            Vec3[] pts = new Vec3[N + 1];
            double[] w = new double[N + 1];
            float[] al = new float[N + 1];
            for (int i = 0; i <= N; i++) {
                double an = i * TAU / N, wob = 1 + 0.06 * Math.sin(an * 5 + t * 0.6 + k);
                pts[i] = p.c.add(f[0].scale(Math.cos(an) * r * wob)).add(f[1].scale(Math.sin(an) * r * wob));
                w[i] = 0.03 + 0.03 * (0.5 + 0.5 * Math.sin(an * 3 + t * 0.3));
                al[i] = a * (0.4F + 0.6F * (float) (0.5 + 0.5 * Math.sin(an * 2 + k)));
            }
            GravityFxClient.gstrip(m, cam, pts, w, al, k % 2 == 0 ? WHITE : ICE);
        }
    }

    // ------------------------------------------------------------------ 2 Pressure Gust
    private static void gust(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age, double t) {
        Vec3 d = p.d.normalize();
        double L = p.r;
        float a = (float) Mth.clamp((p.life - age) / 5.0, 0, 1);
        for (int k = 0; k < 4; k++) {   // rings of compressed air punching down the lane
            double q = (age - k * 1.5) / 6.0;
            if (q < 0 || q > 1) continue;
            Vec3 c = p.c.add(d.scale(q * L));
            double r = 0.5 + 0.9 * q;
            GravityFxClient.ring(m, cam, c, d, r, 0.12, WHITE, a * (float) (1 - q) * 0.9F);
            GravityFxClient.ring(m, cam, c, d, r * 0.6, 0.06, ICE, a * (float) (1 - q) * 0.7F);
        }
        Vec3[] f = frame(d);
        for (int i = 0; i < 9; i++) {   // streaks running with it
            double an = i * 2.399 + p.a1, rr = 0.25 + 0.7 * hash(i + p.a1);
            Vec3 o = f[0].scale(Math.cos(an) * rr).add(f[1].scale(Math.sin(an) * rr));
            double s0 = Math.min(1, age / 6.0) * L, len = 1.5 + 1.5 * hash(i * 3 + p.a1);
            Vec3 a1 = p.c.add(o).add(d.scale(Math.max(0, s0 - len))), a2 = p.c.add(o).add(d.scale(s0));
            arrow(vc, m, n, cam, a1, a2, 0.025, a, i % 3 == 0);
        }
    }

    // ------------------------------------------------------------------ 3 Plasma Point, charging
    private static void charge(Matrix4f m, Vec3 cam, FxPacket p, double age, double t) {
        double q = Math.min(1, age / p.life);
        double r = p.r * (0.25 + 0.75 * q);
        GravityFxClient.gglow(m, cam, p.c, r * 2.2, 0.35F, CYAN);
        GravityFxClient.gglow(m, cam, p.c, r * 1.2, 0.8F, WHITE);
        GravityFxClient.gglow(m, cam, p.c, r * 0.6, 1F, HOT);
        // the air crushed in on it, ring after ring
        for (int k = 0; k < 3; k++) {
            double ph = ((t * 0.08 + k / 3.0) % 1.0);
            double rr = r * (4.0 - 3.0 * ph);
            Vec3 ax = new Vec3(Math.sin(k * 2.1), 1, Math.cos(k * 1.7)).normalize();
            GravityFxClient.ring(m, cam, p.c, ax, rr, 0.06, WHITE, (float) ph * 0.8F);
        }
        // crackle: jagged arcs out of the core, a new shape every tick
        int seed = (int) t;
        for (int b = 0; b < 5; b++) {
            double s = seed * 7.3 + b * 13.1;
            Vec3 dir = new Vec3(hash(s) - 0.5, hash(s + 1) - 0.5, hash(s + 2) - 0.5).normalize();
            Vec3 prev = p.c;
            double len = r * (1.8 + 1.5 * hash(s + 3));
            for (int k = 1; k <= 5; k++) {
                Vec3 jitter = new Vec3(hash(s + k * 4) - 0.5, hash(s + k * 4 + 1) - 0.5, hash(s + k * 4 + 2) - 0.5).scale(r * 0.6);
                Vec3 nx = p.c.add(dir.scale(len * k / 5)).add(jitter);
                GravityFxClient.gseg(m, cam, prev, nx, 0.03, 0.9F, 0.9F, k < 3 ? WHITE : CYAN);
                prev = nx;
            }
        }
    }

    // ------------------------------------------------------------------ 4 the plasma bursting
    private static void burst(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age, double t) {
        double q = Math.min(1, age / 8.0), R = p.r;
        float a = (float) Mth.clamp(1 - age / p.life, 0, 1);
        if (age < 4) GravityFxClient.gglow(m, cam, p.c, R * (0.5 + age * 0.3), (float) (1 - age / 4), HOT);
        Vec3[] axes = {UP, new Vec3(1, 0.3, 0).normalize(), new Vec3(0, 0.3, 1).normalize()};
        for (int k = 0; k < 3; k++) GravityFxClient.ring(m, cam, p.c, axes[k], R * FxBase.smooth(q), 0.12, k == 0 ? WHITE : ICE, a);
        // the black shock-ring on the ground
        Vec3 g = p.u != null && p.u.lengthSqr() > 0 ? p.u.add(0, 0.05, 0) : p.c.add(0, -1, 0);
        double gr = R * 1.2 * FxBase.smooth(q);
        int N = 40;
        for (int i = 0; i < N; i++) {
            double a0 = i * TAU / N, a1 = (i + 1) * TAU / N;
            Vec3 i0 = g.add(Math.cos(a0) * gr * 0.8, 0, Math.sin(a0) * gr * 0.8), i1 = g.add(Math.cos(a1) * gr * 0.8, 0, Math.sin(a1) * gr * 0.8);
            Vec3 o0 = g.add(Math.cos(a0) * gr, 0, Math.sin(a0) * gr), o1 = g.add(Math.cos(a1) * gr, 0, Math.sin(a1) * gr);
            FxBase.quad(vc, m, n, cam, i0, i1, o1, o0, BLACK, a * 0.2F, a * 0.2F, a * 0.85F, a * 0.85F);
            FxBase.quad(vc, m, n, cam, o0, o1, i1, i0, BLACK, a * 0.85F, a * 0.85F, a * 0.2F, a * 0.2F);
        }
        GravityFxClient.ring(m, cam, g, UP, gr, 0.06, WHITE, a);
        // sparks flung out, vectors all
        for (int i = 0; i < 16; i++) {
            double s = p.a1 + i * 3.7;
            Vec3 d = new Vec3(hash(s) - 0.5, hash(s + 1) * 0.8 - 0.2, hash(s + 2) - 0.5).normalize();
            double r0 = R * 0.3 * q, r1 = R * (0.5 + 0.9 * q);
            arrow(vc, m, n, cam, p.c.add(d.scale(r0)), p.c.add(d.scale(r1)), 0.035, a, true);
        }
    }

    // ------------------------------------------------------------------ 5 Wind Vector's column
    private static void column(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age, double t) {
        float a = (float) (Mth.clamp(age / 6, 0, 1) * Mth.clamp((p.life - age) / 10, 0, 1));
        double R = p.r * 0.35, H = 4.5;
        for (int band = 0; band < 4; band++) {   // black and white bands wrung up the column
            int N = 26;
            Vec3[] pts = new Vec3[N];
            double[] w = new double[N];
            float[] al = new float[N];
            for (int i = 0; i < N; i++) {
                double u = i / (double) (N - 1), y = u * H;
                double an = band * TAU / 4 + u * 7 - t * 0.55;
                double rr = R * (0.45 + 0.75 * u);
                pts[i] = p.c.add(Math.cos(an) * rr, y, Math.sin(an) * rr);
                w[i] = 0.09 + 0.12 * u;
                al[i] = a * (float) Math.sin(u * Math.PI * 0.95 + 0.1);
            }
            if (band % 2 == 0) sstrip(vc, m, n, cam, pts, w, al, BLACK);
            else GravityFxClient.gstrip(m, cam, pts, w, al, WHITE);
        }
        // dust round its foot, and streaks pulled in
        GravityFxClient.ring(m, cam, p.c.add(0, 0.08, 0), UP, R * 1.6 + 0.2 * Math.sin(t * 0.3), 0.35, ICE, a * 0.4F);
        for (int i = 0; i < 8; i++) {
            double ph = ((t * 0.05 + i / 8.0) % 1.0), an = i * TAU / 8 + t * 0.05;
            double r0 = p.r * (1 - ph) + R, r1 = r0 - 1.2;
            Vec3 a0 = p.c.add(Math.cos(an) * r0, 0.5 + i % 3 * 0.8, Math.sin(an) * r0), a1 = p.c.add(Math.cos(an + 0.3) * Math.max(R, r1), 0.8 + i % 3 * 0.8, Math.sin(an + 0.3) * Math.max(R, r1));
            arrow(vc, m, n, cam, a0, a1, 0.025, a * (float) Math.sin(ph * Math.PI), true);
        }
    }

    // ------------------------------------------------------------------ 6 Vector Absolute's black jets
    private static void jets(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Minecraft mc, FxPacket p, double age, double t, float pt) {
        Entity e = mc.level.getEntity((int) p.v.x);
        Vec3 f = at(mc, p, pt);
        if (e == null || f == null || e.isInvisible()) return;
        boolean self = e == mc.player && mc.options.getCameraType().isFirstPerson();
        float a = (float) (Mth.clamp(age / 4, 0, 1) * Mth.clamp((p.life - age) / 4, 0, 1));
        float yaw = (e instanceof net.minecraft.world.entity.LivingEntity le ? Mth.rotLerp(pt, le.yBodyRotO, le.yBodyRot) : e.getYRot()) * Mth.DEG_TO_RAD;
        Vec3 fwd = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw)), right = new Vec3(-fwd.z, 0, fwd.x);
        Vec3 root = f.add(0, e.getBbHeight() * 0.75, 0).subtract(fwd.scale(0.3));
        double grow = Math.min(1, age / 6);
        for (int side = -1; side <= 1; side += 2) {
            int N = 9;
            Vec3[] pts = new Vec3[N];
            double[] w = new double[N];
            float[] al = new float[N];
            for (int i = 0; i < N; i++) {
                double u = i / (double) (N - 1) * grow;
                double flutter = Math.sin(t * 0.9 + u * 6 + side) * 0.25 * u;
                double jag = (hash(Math.floor(t / 2) + i * 3.1 + side) - 0.5) * 0.45 * u;   // ragged, re-torn every other tick
                pts[i] = root.add(right.scale(side * (0.2 + 3.2 * u))).subtract(fwd.scale(0.4 + 1.4 * u)).add(0, 0.3 + 2.2 * u - 0.8 * u * u + flutter, 0).add(fwd.scale(jag)).add(0, jag * 0.6, 0);
                w[i] = (0.38 - 0.3 * u) * (self ? 0.6 : 1);
                al[i] = a * (float) (1 - 0.35 * u);
            }
            sstrip(vc, m, n, cam, pts, w, al, BLACK);
            double[] w2 = w.clone(); for (int i = 0; i < N; i++) w2[i] = 0.035;
            GravityFxClient.gstrip(m, cam, pts, w2, al, WHITE);
            for (int s = 0; s < 3; s++) {   // shards flicked off the tip
                double ph = ((t * 0.12 + s / 3.0) % 1.0);
                Vec3 tip = pts[N - 1].add(right.scale(side * ph * 1.2)).add(0, ph * 0.8 - s * 0.3, 0).subtract(fwd.scale(ph * 0.6));
                Vec3 d = right.scale(side).add(0, 0.5, 0).normalize();
                sseg(vc, m, n, cam, tip, tip.add(d.scale(0.35)), 0.08 * (1 - ph), a * (float) (1 - ph), 0, BLACK);
            }
        }
    }

    // ------------------------------------------------------------------ 7 a step on the air
    private static void step(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age) {
        double q = age / p.life;
        float a = (float) (1 - q);
        double r = 0.5 + 1.1 * FxBase.smooth(Math.min(1, q * 2));
        Vec3 prev = null;
        for (int i = 0; i <= 6; i++) {   // a hexagon of compressed air, snapping out
            double an = i * TAU / 6 + 0.5;
            Vec3 c = p.c.add(Math.cos(an) * r, 0.05, Math.sin(an) * r);
            if (prev != null) { GravityFxClient.gseg(m, cam, prev, c, 0.06, a, a, WHITE); sseg(vc, m, n, cam, prev, c, 0.1, a * 0.5F, a * 0.5F, BLACK); }
            prev = c;
        }
        Vec3 d = p.d.normalize();
        for (int i = 0; i < 4; i++) {   // the vectors you kicked down and back
            double an = i * TAU / 4 + 0.8;
            Vec3 o = p.c.add(Math.cos(an) * 0.4, 0.3, Math.sin(an) * 0.4);
            arrow(vc, m, n, cam, o, o.subtract(d.scale(0.8 + q)).add(0, -0.6 - q, 0), 0.03, a, true);
        }
    }

    // ------------------------------------------------------------------ 8 the turning field round you
    private static void field(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Minecraft mc, FxPacket p, double age, double t, float pt) {
        Vec3 f = at(mc, p, pt);
        if (f == null) return;
        Entity e = mc.level.getEntity((int) p.v.x);
        boolean self = e == mc.player && mc.options.getCameraType().isFirstPerson();
        float a = (float) (Mth.clamp(age / 5, 0, 1) * Mth.clamp((p.life - age) / 6, 0, 1)) * (self ? 0.5F : 0.85F);
        Vec3 c = f.add(0, 1.0, 0);
        double R = p.r;
        for (int i = 0; i < 12; i++) {   // vectors sliding over a sphere round you
            double lat = (hash(i * 1.7) - 0.5) * 2.4, lon = i * 2.399 + t * (0.05 + 0.03 * hash(i));
            double lon2 = lon + 0.35;
            Vec3 a0 = c.add(Math.cos(lat) * Math.cos(lon) * R, Math.sin(lat) * R, Math.cos(lat) * Math.sin(lon) * R);
            Vec3 a1 = c.add(Math.cos(lat) * Math.cos(lon2) * R, Math.sin(lat) * R, Math.cos(lat) * Math.sin(lon2) * R);
            arrow(vc, m, n, cam, a0, a1, 0.025, a, true);
        }
    }

    // ------------------------------------------------------------------ 9 a bundle of vectors
    private static void bundle(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age) {
        Vec3 d = p.d.normalize();
        Vec3[] f = frame(d);
        float a = (float) Mth.clamp((p.life - age) / 4.0, 0, 1);
        int k = Math.max(1, (int) p.a0);
        double go = Math.min(1, age / 3.0);
        for (int i = 0; i < k; i++) {
            Vec3 o = p.c.add(f[0].scale((hash(p.a1 + i) - 0.5) * 2 * p.r2)).add(f[1].scale((hash(p.a1 + i + 9) - 0.5) * 2 * p.r2));
            arrow(vc, m, n, cam, o.subtract(d.scale(p.r * 0.4)), o.add(d.scale(p.r * (0.6 * go))), 0.03, a, true);
        }
    }
}
