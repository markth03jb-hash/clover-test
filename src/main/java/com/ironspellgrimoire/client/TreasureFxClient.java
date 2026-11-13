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
 * 4 vault breaker, 5 King's Vault, 6 Aegis.
 */
public final class TreasureFxClient {
    private TreasureFxClient() {}

    private static final double TAU = Math.PI * 2;
    private static final Vec3 UP = new Vec3(0, 1, 0);
    static final float[] GOLD = {1.0F, 0.78F, 0.28F}, PALE = {1.0F, 0.95F, 0.75F}, WHITE = {1.0F, 1.0F, 1.0F},
            STEEL = {0.8F, 0.84F, 0.9F}, STEEL_SH = {0.52F, 0.56F, 0.64F}, GILT = {0.92F, 0.7F, 0.2F}, GILT_SH = {0.6F, 0.42F, 0.1F}, GRIP = {0.28F, 0.12F, 0.06F}, WOOD = {0.42F, 0.26F, 0.12F};

    private static final class L {
        final FxPacket p;
        final double born;
        L(FxPacket p, double born) { this.p = p; this.born = born; }
    }

    private static final List<L> LIVE = new ArrayList<>();

    public static void spawn(FxPacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
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
                case 4 -> spiral(m, cam, p, age, now);
                case 5 -> sky(m, cam, mc, p, age, now, pt);
                case 6 -> aegis(m, cam, mc, p, age, now, pt);
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

    // ================================================================== 4 vault breaker
    private static void spiral(Matrix4f m, Vec3 cam, FxPacket p, double age, double now) {
        Vec3 d = p.d.normalize();
        Vec3[] f = frame(d);
        double charge = Math.max(1, p.sweep);
        if (age < charge) {   // the key turning: three rings spinning faster and closing in
            double q = age / charge;
            for (int k = 0; k < 3; k++) {
                Vec3 ax = f[0].scale(Math.cos(now * (0.2 + q) + k * 2.1)).add(d.scale(0.6)).normalize();
                GravityFxClient.ring(m, cam, p.c, ax, 1.4 * (1 - 0.5 * q), 0.07, k == 0 ? PALE : GOLD, 0.8F);
            }
            GravityFxClient.gglow(m, cam, p.c, 0.5 + 1.2 * q, 0.6F, PALE);
            return;
        }
        double k = Mth.clamp((age - charge) / Math.max(1, p.life - charge), 0, 1);
        float a = (float) (1 - k);
        double L = p.r * Math.min(1, (age - charge) / 3.0), W = p.r2;
        for (int s = 0; s < 4; s++) {   // four strands of torn wind, twisting round each other
            int N = Math.max(6, (int) (L * 2));
            Vec3[] pts = new Vec3[N];
            double[] w = new double[N];
            float[] al = new float[N];
            for (int i = 0; i < N; i++) {
                double u = i / (double) (N - 1), x = u * L;
                double an = s * TAU / 4 + x * 0.9 - now * 1.1;
                double rr = W * (0.35 + 0.65 * Math.min(1, u * 4)) * (0.8 + 0.2 * Math.sin(x * 0.5 + s));
                pts[i] = p.c.add(d.scale(x)).add(f[0].scale(Math.cos(an) * rr)).add(f[1].scale(Math.sin(an) * rr));
                w[i] = 0.12 + 0.18 * (1 - u);
                al[i] = a * (float) (0.9 - 0.5 * u);
            }
            GravityFxClient.gstrip(m, cam, pts, w, al, s % 2 == 0 ? GOLD : PALE);
        }
        for (int i = 0; i < (int) (L / 3); i++) GravityFxClient.ring(m, cam, p.c.add(d.scale(i * 3 + (age * 2) % 3)), d, W * 1.1, 0.12, GOLD, 0.5F * a);
        GravityFxClient.gseg(m, cam, p.c, p.c.add(d.scale(L)), W * 0.35, 0.6F * a, 0.2F * a, WHITE);
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
