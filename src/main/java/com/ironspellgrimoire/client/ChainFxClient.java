package com.ironspellgrimoire.client;

import com.ironspellgrimoire.network.FxPacket;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * The Chain Grimoire, drawn: chains of solid metal links that shoot out, whip, sag and pull tight, each of the five
 * in its own metal with its own end - Binding (cold silver, a cross-shaped weight), Healing (warm gold, a round cup),
 * Judgment (crimson, a blade point), Dowsing (pale violet, a crystal pendulum), Stealing (dark teal, a barbed hook);
 * plain steel for the lash and the anchor. Fed by FxPacket shape 88 (server/ChainFx), {@code h} = kind:
 * 0 chain, 1 wrap, 2 lash, 3 pendulum, 4 net, 5 judgment stake, 6 verdict, 7 eruption, 8 Restriction, 9 anchor.
 */
public final class ChainFxClient {
    private ChainFxClient() {}

    private static final double TAU = Math.PI * 2;
    private static final Vec3 UP = new Vec3(0, 1, 0);
    /** Metal per chain: {body, shadow, glow}. */
    static final float[][][] METAL = {
            {{0.74F, 0.8F, 0.9F}, {0.42F, 0.46F, 0.56F}, {0.6F, 0.8F, 1.0F}},     // Binding
            {{0.98F, 0.82F, 0.42F}, {0.6F, 0.44F, 0.14F}, {1.0F, 0.85F, 0.4F}},    // Healing
            {{0.78F, 0.1F, 0.12F}, {0.38F, 0.02F, 0.04F}, {1.0F, 0.2F, 0.15F}},    // Judgment
            {{0.78F, 0.68F, 0.98F}, {0.42F, 0.34F, 0.62F}, {0.75F, 0.55F, 1.0F}},  // Dowsing
            {{0.16F, 0.5F, 0.5F}, {0.04F, 0.22F, 0.24F}, {0.2F, 0.95F, 0.85F}},    // Stealing
            {{0.62F, 0.64F, 0.7F}, {0.32F, 0.34F, 0.4F}, {0.85F, 0.88F, 1.0F}}};   // plain steel
    static final float[] WHITE = {1, 1, 1};

    private static final class L {
        final FxPacket p;
        final double born;
        double killAt = -1;
        L(FxPacket p, double born) { this.p = p; this.born = born; }
    }

    private static final List<L> LIVE = new ArrayList<>();

    public static void spawn(FxPacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if ((int) p.h == 98) { LIVE.removeIf(l -> (int) l.p.v.x == (int) p.v.x); return; }   // a mode was ended early: drop what hangs on that player
        if (LIVE.size() > 300) LIVE.remove(0);
        LIVE.add(new L(p, mc.level.getGameTime()));
    }

    public static void kill(int id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        for (L l : LIVE) if (l.p.id == id && l.killAt < 0) l.killAt = mc.level.getGameTime();
    }

    static void clear() { LIVE.clear(); }

    static void draw(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, double now, float pt) {
        if (LIVE.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { LIVE.clear(); return; }
        List<L> live = new ArrayList<>(LIVE);
        for (L l : live) {
            double age = now - l.born;
            double end = l.killAt >= 0 ? l.killAt + 3 : l.born + l.p.life;
            if (now > end) { LIVE.remove(l); continue; }
            float out = l.killAt >= 0 ? (float) Mth.clamp((l.killAt + 3 - now) / 3.0, 0, 1) : 1F;
            FxPacket p = l.p;
            int type = Mth.clamp((int) p.a0, 0, 5);
            switch ((int) p.h) {
                case 0 -> chain(vc, m, n, cam, mc, p, age, now, pt, type, out);
                case 1 -> wrap(vc, m, n, cam, mc, p, age, now, pt, type, out);
                case 2 -> lash(vc, m, n, cam, mc, p, age, pt);
                case 3 -> pendulum(vc, m, n, cam, mc, p, age, now, pt);
                case 4 -> net(vc, m, n, cam, p, age);
                case 5 -> stake(vc, m, n, cam, mc, p, age, now, pt, out);
                case 6 -> verdict(m, cam, mc, p, age, pt);
                case 7 -> erupt(vc, m, n, cam, p, age, now, type);
                case 8 -> aura(vc, m, n, cam, mc, p, age, now, pt);
                case 9 -> anchor(vc, m, n, cam, mc, p, age, now, pt);
                default -> LIVE.remove(l);
            }
        }
    }

    // ================================================================== links
    /**
     * A chain of solid links along {@code pts}: each link an oval loop of metal, turned at right angles to the last,
     * lit on its upper side, with a glint running along the chain.
     */
    static void links(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, List<Vec3> pts, int type, double s, float a, double shine) {
        if (pts.size() < 2) return;
        float[][] mt = METAL[type];
        double len = 0.22 * s, gap = 0.17 * s;
        double walked = 0;
        int idx = 0;
        for (int i = 0; i < pts.size() - 1; i++) {
            Vec3 p0 = pts.get(i), p1 = pts.get(i + 1);
            Vec3 ax = p1.subtract(p0);
            double segL = ax.length();
            if (segL < 1e-6) continue;
            Vec3 d = ax.scale(1 / segL);
            Vec3[] f = frame(d);
            double t = (idx == 0 ? 0 : (gap - (walked % gap)) % gap);
            while (t < segL) {
                Vec3 c = p0.add(d.scale(t));
                Vec3 side = (idx % 2 == 0) ? f[0] : f[1];
                double glint = Math.max(0, Math.cos((walked + t) * 3 - shine)) ;
                loop(vc, m, n, cam, c, d, side, len * 0.5, 0.06 * s, 0.022 * s, idx % 2 == 0 ? mt[0] : mt[1], a);
                if (glint > 0.92) GravityFxClient.gglow(m, cam, c, 0.07 * s, (float) (glint - 0.92) * 9F * a, WHITE);
                idx++;
                t += gap;
            }
            walked += segL;
        }
    }

    /** One link: an oval of wire round c, long axis d, in the plane (d, side). */
    private static void loop(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 c, Vec3 d, Vec3 side, double hl, double hw, double wire, float[] col, float a) {
        int seg = 8;
        Vec3 prev = null;
        for (int i = 0; i <= seg; i++) {
            double an = i * TAU / seg;
            Vec3 q = c.add(d.scale(Math.cos(an) * hl)).add(side.scale(Math.sin(an) * hw));
            if (prev != null) wireSeg(vc, m, n, cam, prev, q, wire, col, a);
            prev = q;
        }
    }

    /** A short bar of wire facing the camera, shaded lighter on its top edge. */
    private static void wireSeg(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 a, Vec3 b, double w, float[] col, float al) {
        Vec3 ax = b.subtract(a);
        Vec3 s = ax.cross(a.subtract(cam));
        if (s.lengthSqr() < 1e-12) return;
        s = s.normalize().scale(w);
        float[] hi = {Math.min(1, col[0] * 1.3F + 0.12F), Math.min(1, col[1] * 1.3F + 0.12F), Math.min(1, col[2] * 1.3F + 0.12F)};
        FxBase.quad(vc, m, n, cam, a, b, b.add(s), a.add(s), hi, al, al, al, al);
        FxBase.quad(vc, m, n, cam, a.subtract(s), b.subtract(s), b, a, col, al, al, al, al);
    }

    /** The end of a chain, at {@code at}, pointing along {@code d}. */
    static void endPiece(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 at, Vec3 d, int type, double s, float a, double now) {
        float[][] mt = METAL[type];
        Vec3[] f = frame(d);
        switch (type) {
            case 0 -> {   // Binding: a cross-shaped weight
                Vec3 c = at.add(d.scale(0.12 * s));
                bar(vc, m, n, cam, c.subtract(d.scale(0.14 * s)), c.add(d.scale(0.2 * s)), f[0], 0.05 * s, mt[0], a);
                bar(vc, m, n, cam, c.add(f[0].scale(0.13 * s)), c.subtract(f[0].scale(0.13 * s)), d, 0.05 * s, mt[1], a);
                GravityFxClient.gglow(m, cam, c, 0.2 * s, 0.35F * a, mt[2]);
            }
            case 1 -> {   // Healing: a round cup, glowing warm
                Vec3 c = at.add(d.scale(0.1 * s));
                disc(vc, m, n, cam, c, 0.11 * s, mt[0], a);
                GravityFxClient.gglow(m, cam, c, 0.32 * s * (0.85 + 0.15 * Math.sin(now * 0.4)), 0.5F * a, mt[2]);
            }
            case 2 -> {   // Judgment: a blade point
                Vec3 tip = at.add(d.scale(0.42 * s));
                for (int pl = 0; pl < 2; pl++) {
                    Vec3 w = pl == 0 ? f[0] : f[1];
                    FxBase.quad(vc, m, n, cam, tip, at.add(w.scale(0.09 * s)), at.subtract(w.scale(0.09 * s)), at.subtract(w.scale(0.09 * s)), pl == 0 ? mt[0] : mt[1], a, a, a, a);
                    FxBase.quad(vc, m, n, cam, tip, at.subtract(w.scale(0.09 * s)), at.add(w.scale(0.09 * s)), at.add(w.scale(0.09 * s)), pl == 0 ? mt[0] : mt[1], a, a, a, a);
                }
                GravityFxClient.gseg(m, cam, at, tip, 0.04 * s, 0.7F * a, 0.9F * a, mt[2]);
            }
            case 3 -> {   // Dowsing: a crystal pendulum, a point either end
                Vec3 c = at.add(d.scale(0.16 * s));
                Vec3 top = c.subtract(d.scale(0.16 * s)), bot = c.add(d.scale(0.22 * s));
                for (int k = 0; k < 4; k++) {
                    Vec3 w0 = f[0].scale(Math.cos(k * TAU / 4) * 0.08 * s).add(f[1].scale(Math.sin(k * TAU / 4) * 0.08 * s));
                    Vec3 w1 = f[0].scale(Math.cos((k + 1) * TAU / 4) * 0.08 * s).add(f[1].scale(Math.sin((k + 1) * TAU / 4) * 0.08 * s));
                    float[] col = k % 2 == 0 ? mt[0] : mt[1];
                    FxBase.quad(vc, m, n, cam, top, c.add(w0), c.add(w1), c.add(w1), col, 0.85F * a, 0.85F * a, 0.85F * a, 0.85F * a);
                    FxBase.quad(vc, m, n, cam, bot, c.add(w1), c.add(w0), c.add(w0), col, 0.85F * a, 0.85F * a, 0.85F * a, 0.85F * a);
                }
                GravityFxClient.gglow(m, cam, c, 0.25 * s, (0.4F + 0.2F * (float) Math.sin(now * 0.5)) * a, mt[2]);
            }
            case 4 -> {   // Stealing: a barbed hook
                Vec3 prev = at;
                for (int i = 1; i <= 6; i++) {
                    double an = i / 6.0 * Math.PI * 1.1;
                    Vec3 q = at.add(d.scale(0.12 * s + Math.sin(an) * 0.16 * s)).add(f[0].scale((1 - Math.cos(an)) * 0.13 * s));
                    wireSeg(vc, m, n, cam, prev, q, 0.035 * s, mt[0], a);
                    prev = q;
                }
                wireSeg(vc, m, n, cam, prev, prev.add(f[0].scale(-0.08 * s)).add(d.scale(0.08 * s)), 0.03 * s, mt[1], a);   // the barb
                GravityFxClient.gglow(m, cam, prev, 0.12 * s, 0.4F * a, mt[2]);
            }
            default -> {  // plain: an anchor
                Vec3 sh = at.add(d.scale(0.5 * s));
                bar(vc, m, n, cam, at, sh, f[0], 0.04 * s, mt[0], a);
                bar(vc, m, n, cam, at.add(d.scale(0.1 * s)).add(f[0].scale(0.15 * s)), at.add(d.scale(0.1 * s)).subtract(f[0].scale(0.15 * s)), d, 0.03 * s, mt[1], a);
                Vec3 prev = sh.add(f[0].scale(0.3 * s)).subtract(d.scale(0.12 * s));
                for (int i = 1; i <= 8; i++) {
                    double an = Math.PI * i / 8;
                    Vec3 q = sh.add(f[0].scale(Math.cos(an) * 0.3 * s)).add(d.scale(Math.sin(an) * 0.12 * s - 0.12 * s));
                    wireSeg(vc, m, n, cam, prev, q, 0.04 * s, mt[0], a);
                    prev = q;
                }
            }
        }
    }

    private static void bar(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 a, Vec3 b, Vec3 side, double w, float[] col, float al) {
        Vec3 s = side.scale(w);
        FxBase.quad(vc, m, n, cam, a.add(s), b.add(s), b.subtract(s), a.subtract(s), col, al, al, al, al);
        FxBase.quad(vc, m, n, cam, a.subtract(s), b.subtract(s), b.add(s), a.add(s), col, al, al, al, al);
    }

    private static void disc(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 c, double r, float[] col, float a) {
        Vec3 view = c.subtract(cam);
        if (view.lengthSqr() < 1e-6) return;
        Vec3[] f = frame(view.normalize());
        for (int i = 0; i < 10; i++) {
            double a0 = i * TAU / 10, a1 = (i + 1) * TAU / 10;
            FxBase.quad(vc, m, n, cam, c, c, c.add(f[0].scale(Math.cos(a1) * r)).add(f[1].scale(Math.sin(a1) * r)), c.add(f[0].scale(Math.cos(a0) * r)).add(f[1].scale(Math.sin(a0) * r)), col, a, a, a, a);
        }
    }

    /** Points along a chain from a to b: it sags, and a wave runs down it while it is still flying. */
    static List<Vec3> path(Vec3 a, Vec3 b, double sag, double wave, double now) {
        List<Vec3> l = new ArrayList<>();
        Vec3 ax = b.subtract(a);
        double L = ax.length();
        int N = Math.max(4, (int) (L * 3));
        Vec3 side = ax.cross(UP);
        side = side.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : side.normalize();
        for (int i = 0; i <= N; i++) {
            double u = i / (double) N;
            double s = Math.sin(u * Math.PI);
            l.add(a.add(ax.scale(u)).add(0, -sag * L * s, 0).add(side.scale(Math.sin(u * 9 - now * 1.2) * wave * s)));
        }
        return l;
    }

    static Vec3 hand(Minecraft mc, Entity e, float pt) {
        double yaw = Math.toRadians(Mth.rotLerp(pt, e.yRotO, e.getYRot()));
        Vec3 fwd = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw)), right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
        Vec3 f = feet(e, pt);
        if (e == mc.player && mc.options.getCameraType().isFirstPerson()) return f.add(0, e.getEyeHeight() - 0.45, 0).add(fwd.scale(0.5)).add(right.scale(0.35));
        return f.add(0, e.getBbHeight() * 0.55, 0).add(fwd.scale(0.35)).add(right.scale(0.38));
    }

    static Vec3 feet(Entity e, float pt) { return new Vec3(Mth.lerp(pt, e.xo, e.getX()), Mth.lerp(pt, e.yo, e.getY()), Mth.lerp(pt, e.zo, e.getZ())); }

    // ================================================================== 0 chain
    private static void chain(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Minecraft mc, FxPacket p, double age, double now, float pt, int type, float out) {
        Entity from = mc.level.getEntity((int) p.v.x);
        if (from == null) return;
        Vec3 a = hand(mc, from, pt);
        Vec3 b = p.u;
        if (p.v.y != 0) {
            Entity t = mc.level.getEntity((int) p.v.y);
            if (t != null) b = feet(t, pt).add(0, t.getBbHeight() * 0.55, 0);
        }
        double k = p.speed <= 0 ? 1 : Mth.clamp(age / p.speed, 0, 1);
        double back = Mth.clamp((age - (p.life - 6)) / 6, 0, 1);   // at the end it whips back into the hand
        double reach = Math.min(k, 1 - back);
        Vec3 tip = a.add(b.subtract(a).scale(reach));
        float al = out;
        List<Vec3> pts = path(a, tip, k < 1 ? 0.02 : 0.04, 0.25 * (1 - k), now);
        links(vc, m, n, cam, pts, type, 1, al, now * 0.4);
        Vec3 d = tip.subtract(pts.get(Math.max(0, pts.size() - 2)));
        endPiece(vc, m, n, cam, tip, d.lengthSqr() < 1e-6 ? b.subtract(a).normalize() : d.normalize(), type, 1, al, now);
    }

    // ================================================================== 1 wrap
    private static void wrap(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Minecraft mc, FxPacket p, double age, double now, float pt, int type, float out) {
        Entity e = mc.level.getEntity((int) p.v.x);
        if (e == null) return;
        Vec3 f = feet(e, pt);
        double tight = Mth.clamp(age / 5, 0, 1);
        float a = (float) Mth.clamp((p.life - age) / 6, 0, 1) * out;
        double R = e.getBbWidth() * 0.62 + 0.08 + 0.4 * (1 - tight), H = e.getBbHeight();
        List<Vec3> pts = new ArrayList<>();
        int N = 60;
        for (int i = 0; i <= N; i++) {   // three turns, wound from the knees to the shoulders
            double u = i / (double) N, an = u * TAU * 3 + (int) p.a1;
            pts.add(f.add(Math.cos(an) * R, H * (0.2 + 0.65 * u), Math.sin(an) * R));
        }
        links(vc, m, n, cam, pts, type, 0.85, a, now * 0.4);
        Vec3 last = pts.get(pts.size() - 1);
        endPiece(vc, m, n, cam, last, last.subtract(pts.get(pts.size() - 2)).normalize(), type, 0.85, a, now);
    }

    // ================================================================== 2 lash
    private static void lash(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Minecraft mc, FxPacket p, double age, float pt) {
        Entity e = mc.level.getEntity((int) p.v.x);
        if (e == null) return;
        Vec3 h = hand(mc, e, pt);
        double k = Mth.clamp(age / Math.max(1, p.speed), 0, 1);
        float a = (float) Mth.clamp((p.life - age) / 5, 0, 1);
        double a0 = Math.atan2(p.d.z, p.d.x), a1 = Math.atan2(p.u.z, p.u.x);
        double da = a1 - a0;
        while (da > Math.PI) da -= TAU;
        while (da < -Math.PI) da += TAU;
        double sw = a0 + da * (k * k * (3 - 2 * k));
        List<Vec3> pts = new ArrayList<>();
        int N = 24;
        for (int i = 0; i <= N; i++) {   // the far end lags behind the sweep, so the chain curves like a whip
            double u = i / (double) N;
            double an = sw - da * 0.35 * u * u * (1 - k * 0.6);
            pts.add(h.add(Math.cos(an) * p.r * u, -0.25 * u * u + 0.15 * Math.sin(u * Math.PI), Math.sin(an) * p.r * u));
        }
        links(vc, m, n, cam, pts, 5, 1.1, a, age);
        Vec3 last = pts.get(N);
        endPiece(vc, m, n, cam, last, last.subtract(pts.get(N - 1)).normalize(), 0, 1.1, a, age);
        if (k < 1) GravityFxClient.gseg(m, cam, last, pts.get(N - 4), 0.15, 0.5F * a, 0, METAL[5][2]);
    }

    // ================================================================== 3 dowsing pendulum
    private static void pendulum(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Minecraft mc, FxPacket p, double age, double now, float pt) {
        Entity e = mc.level.getEntity((int) p.v.x);
        if (e == null) return;
        Vec3 h = hand(mc, e, pt);
        float a = (float) (Mth.clamp(age / 4, 0, 1) * Mth.clamp((p.life - age) / 6, 0, 1));
        double yaw = Math.toRadians(Mth.rotLerp(pt, e.yRotO, e.getYRot()));
        Vec3 fwd = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        double sw = Math.sin(age * 0.45) * 0.7;
        Vec3 bob = h.add(0, -Math.cos(sw) * 0.8, 0).add(fwd.scale(Math.sin(sw) * 0.8));
        links(vc, m, n, cam, path(h, bob, 0, 0, now), 3, 0.8, a, now * 0.4);
        endPiece(vc, m, n, cam, bob, bob.subtract(h).normalize(), 3, 1.0, a, now);
        Vec3 g = feet(e, pt).add(0, 0.05, 0);
        for (int k = 0; k < 2; k++) {   // it sounds out the ground round you
            double q = ((age / 16.0) + k * 0.5) % 1.0;
            GravityFxClient.ring(m, cam, g, UP, 1 + 22 * q, 0.12, METAL[3][2], (float) (1 - q) * 0.5F * a);
        }
    }

    // ================================================================== 4 net
    private static void net(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age) {
        double drop = Mth.clamp(age / 6, 0, 1);
        float a = (float) Mth.clamp((p.life - age) / 8, 0, 1);
        double R = p.r, y = 4.5 * (1 - drop * drop) + 0.1;
        int lines = 6;
        for (int dir = 0; dir < 2; dir++) for (int i = -lines; i <= lines; i++) {
            double x = R * i / (double) lines;
            double h = Math.sqrt(Math.max(0, R * R - x * x));
            if (h < 0.3) continue;
            Vec3 s0 = dir == 0 ? new Vec3(x, 0, -h) : new Vec3(-h, 0, x), s1 = dir == 0 ? new Vec3(x, 0, h) : new Vec3(h, 0, x);
            List<Vec3> pts = new ArrayList<>();
            for (int k = 0; k <= 8; k++) {
                Vec3 q = s0.add(s1.subtract(s0).scale(k / 8.0));
                pts.add(p.c.add(q).add(0, y + 0.25 * Math.sin(k / 8.0 * Math.PI) * (1 - drop), 0));
            }
            links(vc, m, n, cam, pts, 0, 0.8, a, age * 0.3);
        }
        if (drop >= 1) GravityFxClient.ring(m, cam, p.c.add(0, 0.05, 0), UP, R, 0.15, METAL[0][2], 0.4F * a);
    }

    // ================================================================== 5 the judgment stake
    private static void stake(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Minecraft mc, FxPacket p, double age, double now, float pt, float out) {
        Entity e = mc.level.getEntity((int) p.v.x);
        if (e == null) return;
        Vec3 f = feet(e, pt);
        float a = (float) Mth.clamp((p.life - age) / 10, 0, 1) * out;
        double yaw = Math.toRadians(e instanceof LivingEntity le ? Mth.rotLerp(pt, le.yBodyRotO, le.yBodyRot) : e.getYRot());
        Vec3 fwd = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        Vec3 heart = f.add(0, e.getBbHeight() * 0.68, 0);
        Vec3 in = heart.add(fwd.scale(e.getBbWidth() * 0.5 + 0.05));
        endPiece(vc, m, n, cam, in, fwd.scale(-1), 2, 0.9, a, now);   // the blade sunk in its chest
        List<Vec3> trail = new ArrayList<>();   // and the chain hanging off it
        for (int i = 0; i <= 10; i++) {
            double u = i / 10.0;
            trail.add(in.add(fwd.scale(0.9 * u)).add(0, -0.7 * u * u + 0.05 * Math.sin(now * 0.2 + u * 6), 0));
        }
        links(vc, m, n, cam, trail, 2, 0.8, a, now * 0.4);
        float beat = (float) Math.pow(Math.max(0, Math.sin(now * 0.25)), 6);
        GravityFxClient.gglow(m, cam, heart, 0.25 + 0.2 * beat, (0.4F + 0.5F * beat) * a, METAL[2][2]);
    }

    // ================================================================== 6 verdict: the chain closes
    private static void verdict(Matrix4f m, Vec3 cam, Minecraft mc, FxPacket p, double age, float pt) {
        Entity e = mc.level.getEntity((int) p.v.x);
        if (e == null) return;
        Vec3 c = feet(e, pt).add(0, e.getBbHeight() * 0.55, 0);
        double k = Mth.clamp(age / p.life, 0, 1);
        float a = (float) (1 - k);
        for (int i = 0; i < 4; i++) GravityFxClient.ring(m, cam, c.add(0, (i - 1.5) * 0.35, 0), UP, (1.4 - i * 0.1) * (1 - k * 0.7), 0.06, METAL[2][2], 0.9F * a);
        GravityFxClient.gglow(m, cam, c, 0.8 + 1.2 * (1 - k), 0.8F * a * a, METAL[2][2]);
    }

    // ================================================================== 7 eruption: a chain out of the ground
    private static void erupt(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age, double now, int type) {
        double k = Mth.clamp(age / Math.max(1, p.speed), 0, 1);
        float a = (float) Mth.clamp((p.life - age) / 8, 0, 1);
        Vec3 ax = p.u.subtract(p.c);
        Vec3 tip = p.c.add(ax.scale(FxBase.backOut(k) * 0.98));
        Vec3 side = ax.cross(UP);
        side = side.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : side.normalize();
        List<Vec3> pts = new ArrayList<>();
        int N = 14;
        for (int i = 0; i <= N; i++) {
            double u = i / (double) N;
            pts.add(p.c.add(tip.subtract(p.c).scale(u)).add(side.scale(Math.sin(u * 6 + (int) p.a1) * 0.3 * (1 - k * 0.6) * Math.sin(u * Math.PI))));
        }
        links(vc, m, n, cam, pts, type, 1.1, a, now * 0.4);
        endPiece(vc, m, n, cam, tip, tip.subtract(pts.get(N - 1)).normalize(), type, 1.1, a, now);
        GravityFxClient.ring(m, cam, p.c.add(0, 0.04, 0), UP, 0.5 + 0.3 * k, 0.12, METAL[type][2], (float) (1 - k) * 0.7F);
    }

    // ================================================================== 8 Restriction: the five chains round you
    private static void aura(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Minecraft mc, FxPacket p, double age, double now, float pt) {
        Entity e = mc.level.getEntity((int) p.v.x);
        if (e == null) return;
        if (e == mc.player && mc.options.getCameraType().isFirstPerson()) return;
        Vec3 f = feet(e, pt);
        float a = (float) (Mth.clamp(age / 8, 0, 1) * Mth.clamp((p.life - age) / 12, 0, 1));
        for (int t = 0; t < 5; t++) {
            double an = now * 0.05 + t * TAU / 5;
            Vec3 top = f.add(Math.cos(an) * 1.0, e.getBbHeight() * 0.95, Math.sin(an) * 1.0);
            Vec3 bot = f.add(Math.cos(an + 0.5) * 1.25, e.getBbHeight() * 0.3 + 0.15 * Math.sin(now * 0.1 + t), Math.sin(an + 0.5) * 1.25);
            links(vc, m, n, cam, path(top, bot, 0.05, 0.08, now + t), t, 0.75, a, now * 0.4);
            endPiece(vc, m, n, cam, bot, bot.subtract(top).normalize(), t, 0.8, a, now);
        }
    }

    // ================================================================== 9 anchor
    private static void anchor(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Minecraft mc, FxPacket p, double age, double now, float pt) {
        Entity e = mc.level.getEntity((int) p.v.x);
        if (e == null) return;
        Vec3 h = hand(mc, e, pt);
        double k = Mth.clamp(age / Math.max(1, p.speed), 0, 1);
        float a = (float) Mth.clamp((p.life - age) / 5, 0, 1);
        Vec3 tip = h.add(p.u.subtract(h).scale(k));
        List<Vec3> pts = path(h, tip, 0.03, 0.2 * (1 - k), now);
        links(vc, m, n, cam, pts, 5, 1.1, a, now * 0.4);
        Vec3 d = tip.subtract(h);
        endPiece(vc, m, n, cam, tip, d.lengthSqr() < 1e-6 ? UP : d.normalize(), 5, 1.6, a, now);
    }

    private static Vec3[] frame(Vec3 axis) {
        Vec3 e1 = Math.abs(axis.y) > 0.9 ? new Vec3(1, 0, 0) : UP;
        e1 = e1.subtract(axis.scale(e1.dot(axis))).normalize();
        return new Vec3[]{e1, axis.cross(e1)};
    }
}
