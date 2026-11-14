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
 * The Treasure Grimoire, drawn: golden portals that ripple like disturbed water, and the weapons that come out of
 * them - swords, spears and axes of polished steel with gold fittings, drawn solid - with chains of gold links, a
 * wall of crossed blades, and the vault breaker's spiral of torn golden wind.
 * Fed by FxPacket shape 87 (server/TreasureFx), {@code h} = kind: 0 portal, 1 weapon, 2 chain, 3 blade wall,
 * 4 Sundering Key's storm, 5 King's Vault, 6 Aegis, 7 the Sundering Key itself.
 */
public final class TreasureFxClient {
    private TreasureFxClient() {}

    private static final double TAU = Math.PI * 2;
    private static final Vec3 UP = new Vec3(0, 1, 0);
    static final float[] GOLD = {1.0F, 0.78F, 0.28F}, PALE = {1.0F, 0.95F, 0.75F}, WHITE = {1.0F, 1.0F, 1.0F},
            STEEL = {0.8F, 0.84F, 0.9F}, STEEL_SH = {0.52F, 0.56F, 0.64F}, GILT = {0.92F, 0.7F, 0.2F}, GILT_SH = {0.6F, 0.42F, 0.1F}, GRIP = {0.28F, 0.12F, 0.06F}, WOOD = {0.42F, 0.26F, 0.12F},
            CRIMSON = {1.0F, 0.14F, 0.1F}, EMBER = {1.0F, 0.45F, 0.18F}, BLOOD = {0.6F, 0.03F, 0.05F}, BLACK = {0.05F, 0.02F, 0.03F},
            DARK = {0.2F, 0.15F, 0.15F}, DARK_SH = {0.11F, 0.08F, 0.08F};

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
        if (LIVE.size() > 500) LIVE.remove(0);
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
                case 0 -> portal(m, cam, p.c, p.d, p.r, age, p.life, now + p.id);
                case 1 -> weapon(vc, m, n, cam, p, age);
                case 2 -> chain(m, cam, mc, p, age, pt);
                case 3 -> wall(vc, m, n, cam, p, age);
                case 4 -> spiral(vc, m, n, cam, p, age, now);
                case 5 -> sky(m, cam, mc, p, age, now, pt);
                case 6 -> aegis(m, cam, mc, p, age, now, pt);
                case 7 -> key(vc, m, n, cam, mc, p, age, now, pt);
                default -> LIVE.remove(l);
            }
        }
    }

    // ================================================================== 0 portal: a golden ripple in the air
    static void portal(Matrix4f m, Vec3 cam, Vec3 c, Vec3 face, double r, double age, double life, double t) {
        double open = FxBase.backOut(Mth.clamp(age / 4, 0, 1));
        float a = (float) Mth.clamp((life - age) / 5, 0, 1);
        Vec3 nrm = face.lengthSqr() < 1e-6 ? c.subtract(cam).normalize() : face.normalize();
        double R = r * open;
        GravityFxClient.gglow(m, cam, c, R * 0.9, 0.55F * a, GOLD);
        GravityFxClient.ring(m, cam, c, nrm, R, 0.06, PALE, 0.95F * a);
        GravityFxClient.ring(m, cam, c, nrm, R, 0.18, GOLD, 0.6F * a);
        for (int k = 0; k < 3; k++) {   // ripples running out, as in disturbed water
            double q = ((t * 0.05 + k / 3.0) % 1.0);
            GravityFxClient.ring(m, cam, c, nrm, R * (0.2 + 0.75 * q), 0.04, GOLD, (float) (1 - q) * 0.7F * a);
        }
        Vec3[] f = frame(nrm);
        for (int k = 0; k < 8; k++) {   // glints round the rim
            double an = k * TAU / 8 + t * 0.03;
            Vec3 q = c.add(f[0].scale(Math.cos(an) * R * 1.05)).add(f[1].scale(Math.sin(an) * R * 1.05));
            GravityFxClient.gglow(m, cam, q, 0.06 + 0.04 * Math.sin(t * 0.3 + k), 0.8F * a, WHITE);
        }
    }

    // ================================================================== 1 weapon
    private static void weapon(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age) {
        Vec3 ax = p.u.subtract(p.c);
        if (ax.lengthSqr() < 1e-6) return;
        Vec3 d = ax.normalize();
        double size = p.r, len = (p.a0 == 1 ? 2.2 : 1.5) * size;
        Vec3 tip;
        boolean flying = age > p.sweep;
        if (!flying) {   // it slides out of the portal, point first
            double q = p.sweep <= 0 ? 1 : Mth.clamp(age / p.sweep, 0, 1);
            tip = p.c.add(d.scale(len * (q - 0.85)));
        } else {
            double q = Mth.clamp((age - p.sweep) / Math.max(1, p.speed), 0, 1);
            tip = p.c.add(d.scale(len * 0.15)).add(ax.subtract(d.scale(len * 0.15)).scale(q));
            if (q < 1) GravityFxClient.gseg(m, cam, tip, tip.subtract(d.scale(len * 2.5)), 0.08 * size, 0.6F, 0, GOLD);
            else { GravityFxClient.gglow(m, cam, tip, 0.6 * size, 0.7F, PALE); return; }
        }
        drawWeapon(vc, m, n, cam, tip, d, (int) p.a0, size, 1F);
    }

    /** A weapon with its point at {@code tip}, pointing along {@code d}: 0 sword, 1 spear, 2 axe. Solid, two crossed planes. */
    static void drawWeapon(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 tip, Vec3 d, int type, double s, float a) {
        Vec3[] f = frame(d);
        for (int pl = 0; pl < 2; pl++) {
            Vec3 w = pl == 0 ? f[0] : f[1];
            float[] steel = pl == 0 ? STEEL : STEEL_SH, gilt = pl == 0 ? GILT : GILT_SH;
            switch (type) {
                case 1 -> {   // spear: a long shaft and a leaf-shaped head, a gold collar
                    Vec3 h0 = tip.subtract(d.scale(0.55 * s)), h1 = tip.subtract(d.scale(0.25 * s));
                    tri(vc, m, n, cam, tip, h1.add(w.scale(0.12 * s)), h1.subtract(w.scale(0.12 * s)), steel, a);
                    tri(vc, m, n, cam, h1.add(w.scale(0.12 * s)), h0, h1.subtract(w.scale(0.12 * s)), steel, a);
                    bar(vc, m, n, cam, h0, h0.subtract(d.scale(0.12 * s)), w, 0.06 * s, gilt, a);
                    bar(vc, m, n, cam, h0.subtract(d.scale(0.12 * s)), tip.subtract(d.scale(2.2 * s)), w, 0.03 * s, WOOD, a);
                }
                case 2 -> {   // axe: a haft and a broad crescent blade off one side
                    Vec3 top = tip, hb = tip.subtract(d.scale(0.15 * s));
                    bar(vc, m, n, cam, top, top.subtract(d.scale(1.5 * s)), w, 0.035 * s, WOOD, a);
                    Vec3 side = f[0];
                    int N = 6;
                    for (int i = 0; i < N; i++) {
                        double a0 = -0.9 + 1.8 * i / N, a1 = -0.9 + 1.8 * (i + 1) / N;
                        Vec3 c = hb.subtract(d.scale(0.25 * s));
                        Vec3 p0 = c.add(side.scale(0.45 * s * Math.cos(a0))).add(d.scale(0.45 * s * Math.sin(a0)));
                        Vec3 p1 = c.add(side.scale(0.45 * s * Math.cos(a1))).add(d.scale(0.45 * s * Math.sin(a1)));
                        tri(vc, m, n, cam, c.subtract(side.scale(0.02 * s)), p0, p1, pl == 0 ? STEEL : STEEL_SH, a);
                    }
                    bar(vc, m, n, cam, hb, hb.subtract(d.scale(0.5 * s)), w, 0.06 * s, gilt, a);
                }
                default -> {   // sword: a tapering blade, a gold crossguard, a dark grip and a gold pommel
                    Vec3 base = tip.subtract(d.scale(1.0 * s));
                    tri(vc, m, n, cam, tip, base.add(w.scale(0.09 * s)), base.subtract(w.scale(0.09 * s)), steel, a);
                    Vec3 g = base;
                    bar(vc, m, n, cam, g.add(w.scale(0.28 * s)), g.subtract(w.scale(0.28 * s)), d, 0.045 * s, gilt, a);
                    bar(vc, m, n, cam, g, g.subtract(d.scale(0.3 * s)), w, 0.035 * s, GRIP, a);
                    bar(vc, m, n, cam, g.subtract(d.scale(0.3 * s)), g.subtract(d.scale(0.38 * s)), w, 0.06 * s, gilt, a);
                }
            }
        }
        GravityFxClient.gseg(m, cam, tip, tip.subtract(d.scale((type == 1 ? 0.55 : 1.0) * s)), 0.03 * s, 0.7F * a, 0.2F * a, WHITE);   // the edge catching the light
    }

    private static void tri(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 a, Vec3 b, Vec3 c, float[] col, float al) {
        FxBase.quad(vc, m, n, cam, a, b, c, c, col, al, al, al, al);
        FxBase.quad(vc, m, n, cam, a, c, b, b, col, al, al, al, al);
    }

    /** A flat bar from a to b, {@code w} half-width along {@code side}. */
    private static void bar(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 a, Vec3 b, Vec3 side, double w, float[] col, float al) {
        Vec3 s = side.scale(w);
        FxBase.quad(vc, m, n, cam, a.add(s), b.add(s), b.subtract(s), a.subtract(s), col, al, al, al, al);
        FxBase.quad(vc, m, n, cam, a.subtract(s), b.subtract(s), b.add(s), a.add(s), col, al, al, al, al);
    }

    // ================================================================== 2 chain of gold links
    private static void chain(Matrix4f m, Vec3 cam, Minecraft mc, FxPacket p, double age, float pt) {
        Entity e = mc.level.getEntity((int) p.v.x);
        if (e == null) return;
        Vec3 t = new Vec3(Mth.lerp(pt, e.xo, e.getX()), Mth.lerp(pt, e.yo, e.getY()) + e.getBbHeight() * 0.5, Mth.lerp(pt, e.zo, e.getZ()));
        float a = (float) Mth.clamp((p.life - age) / 6, 0, 1);
        double reach = Mth.clamp(age / 4, 0, 1);
        Vec3 ax = t.subtract(p.c);
        double L = ax.length() * reach;
        if (L < 0.1) return;
        Vec3 d = ax.normalize();
        Vec3[] f = frame(d);
        int N = (int) (L / 0.32);
        for (int i = 0; i < N; i++) {
            double u = (i + 0.5) * 0.32;
            Vec3 c = p.c.add(d.scale(u)).add(0, -Math.sin(u / Math.max(0.1, L) * Math.PI) * 0.15, 0);
            Vec3 nrm = i % 2 == 0 ? f[0] : f[1];   // each link turned against the last
            Vec3[] lf = {d, nrm.cross(d).normalize()};
            ellipse(m, cam, c, lf, 0.2, 0.09, 0.035, a);
        }
    }

    private static void ellipse(Matrix4f m, Vec3 cam, Vec3 c, Vec3[] f, double rx, double ry, double w, float a) {
        int seg = 12;
        for (int i = 0; i < seg; i++) {
            double a0 = i * TAU / seg, a1 = (i + 1) * TAU / seg;
            Vec3 p0 = c.add(f[0].scale(Math.cos(a0) * rx)).add(f[1].scale(Math.sin(a0) * ry)), p1 = c.add(f[0].scale(Math.cos(a1) * rx)).add(f[1].scale(Math.sin(a1) * ry));
            GravityFxClient.gseg(m, cam, p0, p1, w, a, a, GOLD);
            GravityFxClient.gseg(m, cam, p0, p1, w * 0.35, a, a, PALE);
        }
    }

    // ================================================================== 3 blade wall
    private static void wall(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age) {
        Vec3 d = p.d.normalize(), s = new Vec3(-d.z, 0, d.x).normalize();
        double rise = FxBase.backOut(Mth.clamp(age / 6, 0, 1));
        float a = (float) Mth.clamp((p.life - age) / 8, 0, 1);
        int N = 7;
        for (int i = 0; i < N; i++) {   // pairs of blades planted in the ground, crossed
            double x = (i / (double) (N - 1) - 0.5) * p.r;
            Vec3 b = p.c.add(s.scale(x));
            for (int k = -1; k <= 1; k += 2) {
                Vec3 dir = new Vec3(0, -1, 0).add(s.scale(0.45 * k)).add(d.scale(0.15)).normalize();
                Vec3 tip = b.add(0, -0.1, 0).subtract(s.scale(0.3 * k));
                drawWeapon(vc, m, n, cam, tip, dir, 0, 1.3 * rise, a);
            }
        }
        GravityFxClient.gseg(m, cam, p.c.subtract(s.scale(p.r / 2)).add(0, 0.05, 0), p.c.add(s.scale(p.r / 2)).add(0, 0.05, 0), 0.3, 0.5F * a, 0.5F * a, GOLD);
    }

    // ================================================================== 4 the Sundering Key's storm
    /** c = the key's point, d = the way it tears, r = length, r2 = width, sweep = charge ticks. The storm itself runs to
     *  30 ticks before the end; the last 30 are the cracks it leaves in the air. */
    private static void spiral(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age, double now) {
        Vec3 d = p.d.normalize();
        Vec3[] f = frame(d);
        double charge = Math.max(1, p.sweep), stormEnd = Math.max(charge + 4, p.life - 30);
        double seed = p.a1;
        if (age < charge) {   // the air being pulled into the point: a red whirl closing in
            double q = age / charge;
            for (int k = 0; k < 3; k++) {
                double r = (1.8 - 1.4 * ((q * 2 + k / 3.0) % 1.0));
                GravityFxClient.ring(m, cam, p.c, d, r, 0.05, k == 0 ? EMBER : CRIMSON, 0.55F * (float) q);
            }
            GravityFxClient.gglow(m, cam, p.c, 0.3 + 0.9 * q * q, 0.75F * (float) q, CRIMSON);
            GravityFxClient.gglow(m, cam, p.c, 0.15 + 0.3 * q, 0.9F * (float) q, PALE);
            return;
        }
        double since = age - charge;
        double L = p.r * Math.min(1, since / 3.0), W = p.r2;
        if (age < stormEnd) {
            double k = since / (stormEnd - charge);
            float a = (float) (k < 0.75 ? 1 : (1 - k) / 0.25);
            double swell = 1 + 0.25 * Math.sin(since * 0.9);
            // five strands of torn air twisting round each other: crimson, ember and a thread of gold
            for (int s = 0; s < 5; s++) {
                int N = Math.max(8, (int) (L * 2.5));
                Vec3[] pts = new Vec3[N];
                double[] w = new double[N];
                float[] al = new float[N];
                double dir = s % 2 == 0 ? 1 : -1;   // they turn against each other, like the drums
                for (int i = 0; i < N; i++) {
                    double u = i / (double) (N - 1), x = u * L;
                    double an = s * TAU / 5 + dir * (x * 0.8 - now * 1.3);
                    double rr = W * swell * (0.3 + 0.7 * Math.min(1, u * 5)) * (0.75 + 0.25 * Math.sin(x * 0.6 + s * 1.7));
                    pts[i] = p.c.add(d.scale(x)).add(f[0].scale(Math.cos(an) * rr)).add(f[1].scale(Math.sin(an) * rr));
                    w[i] = (0.16 + 0.2 * (1 - u)) * (s == 4 ? 0.5 : 1);
                    al[i] = a * (float) (0.95 - 0.45 * u);
                }
                GravityFxClient.gstrip(m, cam, pts, w, al, s == 4 ? GOLD : s % 2 == 0 ? CRIMSON : EMBER);
            }
            // two dark ribbons of torn space wound through it: solid, so the storm reads black-and-red even against a bright sky
            for (int s = 0; s < 2; s++) {
                int N = Math.max(8, (int) (L * 2));
                Vec3 prev = null;
                for (int i = 0; i < N; i++) {
                    double u = i / (double) (N - 1), x = u * L;
                    double an = s * Math.PI + (s == 0 ? 1 : -1) * (x * 0.55 - now * 0.9);
                    double rr = W * 0.45 * Math.min(1, u * 5);
                    Vec3 q = p.c.add(d.scale(x)).add(f[0].scale(Math.cos(an) * rr)).add(f[1].scale(Math.sin(an) * rr));
                    if (prev != null) {
                        Vec3 ax = q.subtract(prev), side = ax.cross(prev.subtract(cam));
                        if (side.lengthSqr() > 1e-8) {
                            double near = Mth.clamp((prev.distanceTo(cam) - 2.5) / 4.0, 0, 1);   // never a dark wall in your face
                            float al = a * 0.8F * (float) near * (float) (1 - 0.5 * u);
                            side = side.normalize().scale(W * 0.09 * (1.2 - 0.6 * u));
                            float[] col = s == 0 ? BLOOD : BLACK;
                            FxBase.quad(vc, m, n, cam, prev.add(side), q.add(side), q.subtract(side), prev.subtract(side), col, al, al, al, al);
                            FxBase.quad(vc, m, n, cam, prev.subtract(side), q.subtract(side), q.add(side), prev.add(side), col, al, al, al, al);
                        }
                    }
                    prev = q;
                }
            }
            // the rings of the storm racing ahead
            for (int i = 0; i < (int) (L / 2.5); i++) {
                double x = (i * 2.5 + since * 3.0) % Math.max(2.5, L);
                GravityFxClient.ring(m, cam, p.c.add(d.scale(x)), d, W * (1.0 + 0.35 * x / Math.max(1, L)), 0.14, i % 2 == 0 ? CRIMSON : EMBER, 0.45F * a);
            }
            // the hot core and a flash at the point
            GravityFxClient.gseg(m, cam, p.c, p.c.add(d.scale(L)), W * 0.22, 0.85F * a, 0.3F * a, PALE);
            GravityFxClient.gseg(m, cam, p.c, p.c.add(d.scale(L)), W * 0.55, 0.5F * a, 0.15F * a, CRIMSON);
            GravityFxClient.gglow(m, cam, p.c, W * (0.9 + 0.2 * Math.sin(now)), 0.8F * a, CRIMSON);
            // shards of broken space whirling round it and thrown outwards
            for (int i = 0; i < 18; i++) {
                double x = hs(seed, i, 1) * L, an = hs(seed, i, 2) * TAU + now * (0.15 + 0.1 * hs(seed, i, 3)) * (i % 2 == 0 ? 1 : -1);
                double rr = W * (1.1 + since * 0.05 * (0.5 + hs(seed, i, 4)));
                Vec3 c = p.c.add(d.scale(x)).add(f[0].scale(Math.cos(an) * rr)).add(f[1].scale(Math.sin(an) * rr));
                shard(m, cam, c, d, f, an, 0.25 + 0.25 * hs(seed, i, 5), a);
            }
        }
        // the cracks: the air stays broken along the path for a moment, then heals
        double ck = Mth.clamp((age - (stormEnd - 6)) / (p.life - (stormEnd - 6)), 0, 1);
        if (ck > 0) {
            float a = (float) (ck < 0.2 ? ck / 0.2 : 1 - (ck - 0.2) / 0.8);
            int cracks = 7;
            for (int c = 0; c < cracks; c++) {
                double x0 = (c + 0.3 + 0.4 * hs(seed, c, 7)) * p.r / cracks;
                double an = hs(seed, c, 8) * TAU;
                Vec3 side = f[0].scale(Math.cos(an)).add(f[1].scale(Math.sin(an)));
                Vec3 at = p.c.add(d.scale(x0)).add(side.scale(W * 0.3 * hs(seed, c, 9)));
                crack(m, cam, at, side, d, W * (1.0 + hs(seed, c, 10)), seed + c * 31, a, 0);
            }
        }
    }

    /** A sliver of broken space: a thin dark-cored, red-edged splinter. */
    private static void shard(Matrix4f m, Vec3 cam, Vec3 c, Vec3 d, Vec3[] f, double an, double s, float a) {
        Vec3 out = f[0].scale(Math.cos(an)).add(f[1].scale(Math.sin(an)));
        Vec3 tip = c.add(out.scale(s)).add(d.scale(s * 0.4)), back = c.subtract(out.scale(s * 0.5)).subtract(d.scale(s * 0.3));
        GravityFxClient.gseg(m, cam, back, tip, 0.05, 0.9F * a, 0.2F * a, CRIMSON);
        GravityFxClient.gseg(m, cam, back, tip, 0.015, 0.9F * a, 0.4F * a, PALE);
    }

    /** One jagged crack in the air, forking twice: bright white-red core, crimson glow round it. */
    private static void crack(Matrix4f m, Vec3 cam, Vec3 at, Vec3 side, Vec3 d, double len, double seed, float a, int depth) {
        Vec3 cur = at;
        int N = 6;
        Vec3 bend = side.cross(d).normalize();
        for (int i = 0; i < N; i++) {
            double t = (i + 1) / (double) N;
            Vec3 next = at.add(side.scale(len * t)).add(bend.scale((hs(seed, i, 11) - 0.5) * len * 0.35)).add(d.scale((hs(seed, i, 12) - 0.5) * len * 0.25));
            float aa = a * (float) (1 - 0.6 * t);
            GravityFxClient.gseg(m, cam, cur, next, 0.12 * (1 - 0.5 * t), aa * 0.7F, aa * 0.5F, CRIMSON);
            GravityFxClient.gseg(m, cam, cur, next, 0.03, aa, aa * 0.7F, PALE);
            if (depth < 2 && i == 2 + depth) crack(m, cam, next, side.add(bend.scale(hs(seed, i, 13) > 0.5 ? 0.8 : -0.8)).normalize(), d, len * 0.45, seed * 1.7 + 5, a * 0.8F, depth + 1);
            cur = next;
        }
    }

    // ================================================================== 7 the Sundering Key: three drums turning against each other
    private static void key(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Minecraft mc, FxPacket p, double age, double now, float pt) {
        Entity e = mc.level.getEntity((int) p.v.x);
        Vec3 base = e == null ? p.c : new Vec3(Mth.lerp(pt, e.xo, e.getX()), Mth.lerp(pt, e.yo, e.getY()), Mth.lerp(pt, e.zo, e.getZ())).add(p.u);
        Vec3 d = p.d.normalize();
        Vec3[] f = frame(d);
        double charge = Math.max(1, p.sweep), end = p.life;
        double emerge = FxBase.backOut(Mth.clamp(age / 9, 0, 1));
        float a = (float) Mth.clamp((end - age) / 10, 0, 1);
        double q = Mth.clamp(age / charge, 0, 1);
        double after = Math.max(0, age - charge);
        // how far round the drums have turned: winding up, roaring through the storm, running down
        double spin = age < charge ? 0.08 * age + 0.6 * Math.pow(age / charge, 3) * charge : 0.08 * charge + 0.6 * charge + (after < 22 ? 1.1 * after : 1.1 * 22 + 0.6 * (after - 22) - 0.012 * (after - 22) * (after - 22));
        double big = p.r2 > 0.5 ? 1.2 : 1.0;
        // the dark ripple it comes out of, at its back
        Vec3 gate = base.subtract(d.scale(0.25));
        float ga = (float) (Mth.clamp(age / 4, 0, 1) * Mth.clamp((charge + 8 - age) / 8, 0, 1));
        if (ga > 0.01F) {
            GravityFxClient.ring(m, cam, gate, d, 0.75 * big, 0.06, GOLD, 0.9F * ga);
            GravityFxClient.ring(m, cam, gate, d, 0.6 * big, 0.2, CRIMSON, 0.6F * ga);
            for (int k = 0; k < 3; k++) {
                double rq = ((now * 0.06 + k / 3.0) % 1.0);
                GravityFxClient.ring(m, cam, gate, d, 0.75 * big * (1 - rq), 0.04, BLOOD, (float) rq * 0.8F * ga);
            }
        }
        // it slides out point first
        Vec3 o = base.add(d.scale(-0.4 + 0.4 * emerge));
        // hilt behind the drums: gold pommel, dark grip, a heavy gold guard
        Vec3 pom = o.subtract(d.scale(0.55 * big)), guard = o;
        cyl(vc, m, n, cam, pom, d, f, 0.07 * big, 0.12 * big, 0, GILT, GILT_SH, a);
        cyl(vc, m, n, cam, pom.add(d.scale(0.12 * big)), d, f, 0.045 * big, 0.4 * big, 0, GRIP, GRIP, a);
        cyl(vc, m, n, cam, guard.subtract(d.scale(0.04 * big)), d, f, 0.3 * big, 0.08 * big, spin * 0.2, GILT, GILT_SH, a);
        // the three drums, each narrower, each turning the other way
        double[] R = {0.24, 0.19, 0.14}, LEN = {0.8, 0.72, 0.64};
        Vec3 at = guard.add(d.scale(0.06 * big));
        float glow = (float) (0.25 + 0.75 * Math.min(1, q * q + (after > 0 ? 1 : 0))) * a;
        for (int i = 0; i < 3; i++) {
            double r = R[i] * big, len = LEN[i] * big, rot = (i % 2 == 0 ? 1 : -1) * spin;
            cyl(vc, m, n, cam, at, d, f, r, len, rot, DARK, DARK_SH, a);
            // gold bands at each end of the drum
            GravityFxClient.ring(m, cam, at.add(d.scale(0.02)), d, r * 1.02, 0.025, GOLD, 0.8F * a);
            GravityFxClient.ring(m, cam, at.add(d.scale(len - 0.02)), d, r * 1.02, 0.025, GOLD, 0.8F * a);
            // the red lines cut into it, slanting so they seem to crawl as it turns
            for (int k = 0; k < 4; k++) {
                double an0 = rot + k * TAU / 4, an1 = an0 + 0.7 * (i % 2 == 0 ? 1 : -1);
                Vec3 p0 = at.add(d.scale(0.08)).add(f[0].scale(Math.cos(an0) * r * 1.03)).add(f[1].scale(Math.sin(an0) * r * 1.03));
                Vec3 p1 = at.add(d.scale(len - 0.08)).add(f[0].scale(Math.cos(an1) * r * 1.03)).add(f[1].scale(Math.sin(an1) * r * 1.03));
                GravityFxClient.gseg(m, cam, p0, p1, 0.035, glow, glow, CRIMSON);
                GravityFxClient.gseg(m, cam, p0, p1, 0.012, glow, glow, PALE);
            }
            at = at.add(d.scale(len));
        }
        // the point
        cone(vc, m, n, cam, at, d, f, R[2] * big * 0.9, 0.42 * big, spin, DARK, DARK_SH, a);
        Vec3 tip = at.add(d.scale(0.42 * big));
        GravityFxClient.gglow(m, cam, tip, (0.15 + 0.45 * q) * big, glow, CRIMSON);
        // while it winds up: the wind dragged into the drums
        if (age < charge) {
            for (int i = 0; i < 12; i++) {
                double ph = ((now * 0.07 + hs(p.a1, i, 20)) % 1.0);
                double an = hs(p.a1, i, 21) * TAU + now * 0.1, along = hs(p.a1, i, 22) * 2.2 * big;
                double rr = 0.4 + 2.4 * (1 - ph);
                Vec3 side = f[0].scale(Math.cos(an)).add(f[1].scale(Math.sin(an)));
                Vec3 s0 = guard.add(d.scale(along)).add(side.scale(rr)), s1 = guard.add(d.scale(along)).add(side.scale(rr * 0.7)).add(f[0].scale(Math.cos(an + 0.5) * 0.1));
                GravityFxClient.gseg(m, cam, s0, s1, 0.035, (float) (q * ph) * 0.8F * a, 0.1F * a, i % 3 == 0 ? CRIMSON : PALE);
            }
        }
    }

    /** A solid drum from a along d, {@code len} long, radius r, turned by {@code rot}; faces alternate light and shadow. */
    private static void cyl(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 a, Vec3 d, Vec3[] f, double r, double len, double rot, float[] lit, float[] sh, float al) {
        int seg = 10;
        Vec3 b = a.add(d.scale(len));
        for (int i = 0; i < seg; i++) {
            double a0 = rot + i * TAU / seg, a1 = rot + (i + 1) * TAU / seg;
            Vec3 o0 = f[0].scale(Math.cos(a0) * r).add(f[1].scale(Math.sin(a0) * r)), o1 = f[0].scale(Math.cos(a1) * r).add(f[1].scale(Math.sin(a1) * r));
            float[] col = i % 2 == 0 ? lit : sh;
            FxBase.quad(vc, m, n, cam, a.add(o0), b.add(o0), b.add(o1), a.add(o1), col, al, al, al, al);
            FxBase.quad(vc, m, n, cam, a.add(o1), b.add(o1), b.add(o0), a.add(o0), col, al, al, al, al);
            tri(vc, m, n, cam, a, a.add(o0), a.add(o1), sh, al);
            tri(vc, m, n, cam, b, b.add(o0), b.add(o1), lit, al);
        }
    }

    private static void cone(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 a, Vec3 d, Vec3[] f, double r, double len, double rot, float[] lit, float[] sh, float al) {
        int seg = 10;
        Vec3 tip = a.add(d.scale(len));
        for (int i = 0; i < seg; i++) {
            double a0 = rot + i * TAU / seg, a1 = rot + (i + 1) * TAU / seg;
            Vec3 o0 = a.add(f[0].scale(Math.cos(a0) * r)).add(f[1].scale(Math.sin(a0) * r)), o1 = a.add(f[0].scale(Math.cos(a1) * r)).add(f[1].scale(Math.sin(a1) * r));
            tri(vc, m, n, cam, tip, o0, o1, i % 2 == 0 ? lit : sh, al);
        }
    }

    private static double hs(double seed, int i, int k) {
        double x = Math.sin(seed * 12.9898 + i * 78.233 + k * 37.719) * 43758.5453;
        return x - Math.floor(x);
    }

    // ================================================================== 5 King's Vault: portals in the sky behind you
    private static void sky(Matrix4f m, Vec3 cam, Minecraft mc, FxPacket p, double age, double now, float pt) {
        Entity e = mc.level.getEntity((int) p.v.x);
        if (e == null) return;
        Vec3 f = new Vec3(Mth.lerp(pt, e.xo, e.getX()), Mth.lerp(pt, e.yo, e.getY()), Mth.lerp(pt, e.zo, e.getZ()));
        double yaw = Math.toRadians(e.getYRot());
        Vec3 look = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw)), side = new Vec3(-look.z, 0, look.x);
        for (int i = 0; i < 9; i++) {
            double s = (i / 8.0 - 0.5) * 2;
            Vec3 c = f.add(look.scale(-1.6 - Math.abs(s) * 0.4)).add(side.scale(s * 3.4)).add(0, 2.6 + (i % 3) * 0.9 + Math.abs(s) * 0.5, 0);
            if (age < i) continue;   // they open one after another
            portal(m, cam, c, look, 0.55 + 0.15 * (i % 2), age - i, p.life - i, now + i * 13);
        }
    }

    // ================================================================== 6 Aegis: a shell of gold, small portals turning round you
    private static void aegis(Matrix4f m, Vec3 cam, Minecraft mc, FxPacket p, double age, double now, float pt) {
        Entity e = mc.level.getEntity((int) p.v.x);
        if (e == null) return;
        Vec3 f = new Vec3(Mth.lerp(pt, e.xo, e.getX()), Mth.lerp(pt, e.yo, e.getY()) + e.getBbHeight() * 0.5, Mth.lerp(pt, e.zo, e.getZ()));
        float a = (float) (Mth.clamp(age / 6, 0, 1) * Mth.clamp((p.life - age) / 12, 0, 1));
        boolean self = e == mc.player && mc.options.getCameraType().isFirstPerson();
        for (int i = 1; i < 6; i++) {
            double lat = -Math.PI / 2 + i * Math.PI / 6;
            GravityFxClient.ring(m, cam, f.add(0, Math.sin(lat) * 1.4, 0), UP, Math.cos(lat) * 1.4, 0.04, GOLD, (self ? 0.12F : 0.35F) * a);
        }
        if (self) return;
        for (int k = 0; k < 3; k++) {
            double an = now * 0.08 + k * TAU / 3;
            Vec3 c = f.add(Math.cos(an) * 1.5, 0.3 * Math.sin(now * 0.1 + k), Math.sin(an) * 1.5);
            portal(m, cam, c, new Vec3(Math.cos(an), 0, Math.sin(an)), 0.3, age, p.life, now + k * 9);
        }
    }

    // ================================================================== helpers
    private static Vec3[] frame(Vec3 axis) {
        Vec3 e1 = Math.abs(axis.y) > 0.9 ? new Vec3(1, 0, 0) : UP;
        e1 = e1.subtract(axis.scale(e1.dot(axis))).normalize();
        return new Vec3[]{e1, axis.cross(e1)};
    }
}
