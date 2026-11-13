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
 * The Lightless Abyss Grimoire, drawn: the deep sea. Solid black-blue shapes - arms, a long body, rings of teeth - with
 * only cold points of light on them, teal and cyan, the way creatures that live without sun light themselves; a
 * lantern on a stalk; heavy dark water pressing down. And on your screen while the book is equipped, how deep you
 * are: a gauge falling from sunlit blue to black, counted in metres, and the edges of your sight darkening with it
 * (only the edges - never enough to hide what's in front of you; Bioluminescence lifts it).
 * Fed by FxPacket shape 93 (server/AbyssFx), {@code h} = kind: 0 pressure, 1 lure, 2 grasp, 3 undertow, 4 dive,
 * 5 depth charge, 6 leviathan, 7 weight, 8 bioluminescence, 9 trench, 10 jaws, 11 feeding light, 99 the gauge.
 */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = "iron_spell_grimoire", value = net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class AbyssFxClient {
    private AbyssFxClient() {}

    private static final double TAU = Math.PI * 2;
    private static final Vec3 UP = new Vec3(0, 1, 0);
    static final float[] ABYSS = {0.02F, 0.035F, 0.08F}, DEEP = {0.04F, 0.1F, 0.2F}, WATER = {0.08F, 0.25F, 0.42F}, TEAL = {0.2F, 0.95F, 0.82F},
            CYAN = {0.45F, 0.85F, 1.0F}, LANTERN = {0.85F, 1.0F, 0.9F}, BONE = {0.86F, 0.88F, 0.8F}, FOAM = {0.75F, 0.92F, 1.0F};

    private static final class L {
        final FxPacket p;
        final double born;
        L(FxPacket p, double born) { this.p = p; this.born = born; }
    }

    private static final List<L> LIVE = new ArrayList<>();
    private static float depth, shown = -1, dark = 0;
    private static boolean equipped, glowing;

    public static void spawn(FxPacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if ((int) p.h == 99) { depth = p.a0; equipped = p.r > 0.5F; glowing = p.r2 > 0.5F; return; }
        if (LIVE.size() > 300) LIVE.remove(0);
        LIVE.add(new L(p, mc.level.getGameTime()));
    }

    static void clear() { LIVE.clear(); }

    // ================================================================== the screen: the dark at the edges and the gauge
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onGui(net.minecraftforge.client.event.RenderGuiEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        var g = e.getGuiGraphics();
        float f = Mth.clamp(depth / 100F, 0, 1);
        shown = shown < 0 ? f : shown + (f - shown) * 0.12F;
        float want = equipped && !glowing ? shown * 0.42F : 0;
        dark += (want - dark) * 0.05F;
        int W = g.guiWidth(), H = g.guiHeight();
        if (dark > 0.01F) {   // the dark closing in from the edges - only the outer fifth of the screen
            int bands = 20, depthPx = Math.min(W, H) / 5;
            for (int i = 0; i < bands; i++) {
                float k = 1 - i / (float) bands;
                int al = (int) (255 * dark * k * k * 0.35F);
                if (al <= 0) continue;
                int col = al << 24 | 0x01040B;
                int s = depthPx * i / bands, s2 = depthPx * (i + 1) / bands;
                g.fill(0, s, W, s2, col);
                g.fill(0, H - s2, W, H - s, col);
                g.fill(s, s2, s2, H - s2, col);
                g.fill(W - s2, s2, W - s, H - s2, col);
            }
        }
        if (mc.options.hideGui || !equipped) return;
        // the gauge: a slim tube left of the hotbar, sunlit at the top, black at the bottom, with a marker sinking in it
        int x = W / 2 - 91 - 29 - 16, top = H - 40, bot = H - 4, w = 6;
        double t = mc.level.getGameTime() + e.getPartialTick();
        for (int y = top; y < bot; y++) {
            float u = (y - top) / (float) (bot - top);
            int r = (int) Mth.lerp(u, 70, 2), gg = (int) Mth.lerp(u, 170, 8), b = (int) Mth.lerp(u, 230, 22);
            g.fill(x, y, x + w, y + 1, 0xFF000000 | r << 16 | gg << 8 | b);
        }
        g.fill(x - 1, top - 1, x + w + 1, top, 0xFF7FD8E6);
        g.fill(x - 1, bot, x + w + 1, bot + 1, 0xFF7FD8E6);
        g.fill(x - 1, top, x, bot, 0xFF7FD8E6);
        g.fill(x + w, top, x + w + 1, bot, 0xFF7FD8E6);
        int my = top + (int) ((bot - top - 3) * shown);
        int pulse = (int) (180 + 75 * Math.sin(t * 0.25));
        g.fill(x - 3, my, x + w + 3, my + 3, pulse << 24 | 0x33FFD6);
        String label = (int) (shown * 11000) + "m";
        g.drawString(mc.font, label, x + w / 2 - mc.font.width(label) / 2, top - 11, 0xFF9FF2E8, true);
    }

    // ================================================================== the world
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
                case 0 -> pressure(vc, m, n, cam, p, age, now);
                case 1 -> lure(vc, m, n, cam, p, age, now);
                case 2 -> grasp(vc, m, n, cam, mc, p, age, now, pt);
                case 3 -> undertow(vc, m, n, cam, p, age, now);
                case 4 -> dive(vc, m, n, cam, p, age);
                case 5 -> charge(vc, m, n, cam, p, age, now);
                case 6 -> leviathan(vc, m, n, cam, p, age, now);
                case 7 -> weight(m, cam, mc, p, age, now, pt);
                case 8 -> glow(vc, m, n, cam, mc, p, age, now, pt);
                case 9 -> trench(vc, m, n, cam, p, age, now);
                case 10 -> jaws(vc, m, n, cam, p.c, p.d, p.r, Mth.clamp(age / 6, 0, 1), (float) Mth.clamp((p.life - age) / 6, 0, 1));
                case 11 -> feed(m, cam, mc, p, age, pt);
                default -> LIVE.remove(l);
            }
        }
    }

    // ================================================================== solid dark shapes
    private static Vec3[] frame(Vec3 axis) {
        Vec3 e1 = Math.abs(axis.y) > 0.9 ? new Vec3(1, 0, 0) : UP;
        e1 = e1.subtract(axis.scale(e1.dot(axis))).normalize();
        return new Vec3[]{e1, axis.cross(e1)};
    }

    private static void tri(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 a, Vec3 b, Vec3 c, float[] col, float al) {
        FxBase.quad(vc, m, n, cam, a, b, c, c, col, al, al, al, al);
        FxBase.quad(vc, m, n, cam, c, b, a, a, col, al, al, al, al);
    }

    /** A solid ribbon through pts, w[i] half-wide, turned to face you. */
    static void strip(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3[] pts, double[] w, float[] col, float a) {
        for (int i = 0; i < pts.length - 1; i++) {
            Vec3 p0 = pts[i], p1 = pts[i + 1], ax = p1.subtract(p0);
            if (ax.lengthSqr() < 1e-10) continue;
            Vec3 s = ax.cross(p0.subtract(cam));
            if (s.lengthSqr() < 1e-10) continue;
            s = s.normalize();
            Vec3 a0 = p0.add(s.scale(w[i])), b0 = p0.subtract(s.scale(w[i])), a1 = p1.add(s.scale(w[i + 1])), b1 = p1.subtract(s.scale(w[i + 1]));
            FxBase.quad(vc, m, n, cam, a0, a1, b1, b0, col, a, a, a, a);
            FxBase.quad(vc, m, n, cam, b0, b1, a1, a0, col, a, a, a, a);
        }
    }

    /** A flat disc lying on the ground (both faces). */
    static void flatDisc(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 c, double r, float[] col, float a) {
        if (r <= 0.01) return;
        int N = 28;
        for (int i = 0; i < N; i++) {
            double a0 = i * TAU / N, a1 = (i + 1) * TAU / N;
            Vec3 p0 = c.add(Math.cos(a0) * r, 0, Math.sin(a0) * r), p1 = c.add(Math.cos(a1) * r, 0, Math.sin(a1) * r);
            FxBase.quad(vc, m, n, cam, c, p0, p1, p1, col, a, a, a, a);
            FxBase.quad(vc, m, n, cam, p1, p0, c, c, col, a, a, a, a);
        }
    }

    /** An arm of the deep: a tapering solid tentacle from base along a curling path, with glowing points down it. */
    static void tentacle(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 base, Vec3 up, Vec3 side, double len, double curl, double phase, double thick, float a) {
        int N = 12;
        Vec3[] pts = new Vec3[N];
        double[] w = new double[N];
        for (int i = 0; i < N; i++) {
            double u = i / (double) (N - 1);
            double bend = curl * u * u + 0.25 * Math.sin(phase + u * 5) * u;
            pts[i] = base.add(up.scale(len * u * Math.cos(bend * 0.8))).add(side.scale(len * u * Math.sin(bend * 0.8)));
            w[i] = thick * (1 - u * 0.85);
        }
        strip(vc, m, n, cam, pts, w, DEEP, a);
        for (int i = 1; i < N - 1; i += 2) GravityFxClient.gglow(m, cam, pts[i], thick * 0.9 * (1 - i / (double) N), 0.7F * a, TEAL);
        GravityFxClient.gglow(m, cam, pts[N - 1], thick * 0.6, 0.9F * a, CYAN);
    }

    /**
     * Jaws: an upper and a lower row of long pale teeth on dark gums, facing {@code face}, closing as {@code k} goes
     * 0 to 1 (open wide, then shut).
     */
    static void jaws(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 c, Vec3 face, double r, double k, float a) {
        if (a <= 0.01F) return;
        Vec3 f = face.lengthSqr() < 1e-6 ? c.subtract(cam) : face;
        f = new Vec3(f.x, 0, f.z).lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : new Vec3(f.x, 0, f.z).normalize();
        Vec3 side = UP.cross(f).normalize();
        double open = r * 0.9 * (1 - FxBase.smooth(k));
        for (int jaw = 0; jaw < 2; jaw++) {
            double sg = jaw == 0 ? 1 : -1;
            Vec3 gum = c.add(0, sg * (open + r * 0.15), 0);
            int N = 9;
            Vec3[] arc = new Vec3[N];
            double[] w = new double[N];
            for (int i = 0; i < N; i++) {
                double u = i / (double) (N - 1) * 2 - 1;
                arc[i] = gum.add(side.scale(u * r)).add(f.scale(-r * 0.5 * u * u)).add(0, sg * r * 0.12 * u * u, 0);
                w[i] = r * 0.1;
            }
            strip(vc, m, n, cam, arc, w, ABYSS, a);
            for (int i = 0; i < N - 1; i++) {   // teeth: long needles pointing at the other jaw
                Vec3 b0 = arc[i], b1 = arc[i + 1], mid = b0.add(b1).scale(0.5);
                double len = r * (0.35 + 0.25 * Math.sin(i * 1.7 + jaw)) ;
                tri(vc, m, n, cam, b0, b1, mid.add(0, -sg * len, 0).add(f.scale(0.05)), BONE, a);
            }
            GravityFxClient.gglow(m, cam, arc[0], r * 0.12, 0.6F * a, TEAL);
            GravityFxClient.gglow(m, cam, arc[N - 1], r * 0.12, 0.6F * a, TEAL);
        }
        if (k >= 1) GravityFxClient.gglow(m, cam, c, r * 1.2, 0.35F * a, CYAN);
    }

    // ================================================================== 0 pressure
    private static void pressure(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age, double now) {
        float a = (float) (Mth.clamp(age / 3, 0, 1) * Mth.clamp((p.life - age) / 8, 0, 1));
        double R = p.r;
        flatDisc(vc, m, n, cam, p.c.add(0, 0.03, 0), R * Mth.clamp(age / 6, 0, 1), WATER, 0.35F * a);
        for (int i = 0; i < 5; i++) {   // rings of the sea's weight sinking onto it
            double ph = ((age + i * 4) / 20.0) % 1.0;
            double y = 6 * (1 - ph * ph);
            GravityFxClient.ring(m, cam, p.c.add(0, y, 0), UP, R * (1 - 0.3 * ph), 0.18, CYAN, a * (float) Math.sin(ph * Math.PI) * 0.8F);
        }
        for (int i = 0; i < 10; i++) {   // the falling dark: streaks coming straight down
            double an = FxBase.h2(p.id, i, 1) * TAU, rr = R * Math.sqrt(FxBase.h2(p.id, i, 2));
            double ph = ((age * 0.08 + FxBase.h2(p.id, i, 3))) % 1.0;
            Vec3 q = p.c.add(Math.cos(an) * rr, 6 * (1 - ph), Math.sin(an) * rr);
            GravityFxClient.gseg(m, cam, q, q.add(0, 1.2, 0), 0.05, 0.5F * a, 0, WATER);
        }
        if (age > 5 && age < 12) GravityFxClient.ring(m, cam, p.c.add(0, 0.1, 0), UP, R * (age - 5) / 7, 0.3, TEAL, (float) (1 - (age - 5) / 7));
    }

    // ================================================================== 1 the angler's lure
    private static void lure(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age, double now) {
        float a = (float) (Mth.clamp(age / 4, 0, 1) * Mth.clamp((p.life - age) / 6, 0, 1));
        double snap = Math.max(1, p.sweep);
        Vec3 c = p.c.add(0, 0.12 * Math.sin(now * 0.2), 0);
        if (age < snap + 2) {
            int N = 12;   // the stalk: from the hand, arching up and drooping to the lantern
            Vec3[] pts = new Vec3[N];
            double[] w = new double[N];
            double reach = Mth.clamp(age / 5, 0, 1);
            Vec3 tip = p.u.add(c.subtract(p.u).scale(reach));
            for (int i = 0; i < N; i++) {
                double u = i / (double) (N - 1);
                pts[i] = p.u.add(tip.subtract(p.u).scale(u)).add(0, Math.sin(u * Math.PI) * 0.9 * reach, 0);
                w[i] = 0.035 * (1 - u * 0.5);
            }
            strip(vc, m, n, cam, pts, w, DEEP, a);
            float pulse = (float) (0.75 + 0.25 * Math.sin(now * 0.5));
            GravityFxClient.gglow(m, cam, tip, 0.5 * reach, pulse * a, LANTERN);
            GravityFxClient.gglow(m, cam, tip, 1.6 * reach, 0.25F * pulse * a, TEAL);
            GravityFxClient.gglow(m, cam, tip, p.r * 0.5 * reach, 0.06F * a, TEAL);
            for (int i = 0; i < 8; i++) {   // drawn toward the light: threads of glow running into it
                double an = FxBase.h2(p.id, i, 4) * TAU, ph = ((now * 0.06 + FxBase.h2(p.id, i, 5))) % 1.0;
                Vec3 q = tip.add(new Vec3(Math.cos(an), 0.3 * (FxBase.h2(p.id, i, 6) - 0.5), Math.sin(an)).scale(p.r * 0.6 * (1 - ph)));
                GravityFxClient.gseg(m, cam, q, q.add(tip.subtract(q).scale(0.25)), 0.03, 0, 0.6F * a, CYAN);
            }
        }
        if (age >= snap - 8) {   // the jaws rise round the lantern from below and snap shut on it
            double k = Mth.clamp((age - (snap - 8)) / 8, 0, 1);
            jaws(vc, m, n, cam, c, cam.subtract(c), 2.2, k * k, (float) Mth.clamp((p.life - age) / 6, 0, 1));
        }
    }

    // ================================================================== 2 the grasp
    private static void grasp(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Minecraft mc, FxPacket p, double age, double now, float pt) {
        Entity e = mc.level.getEntity((int) p.v.x);
        if (e == null) return;
        Vec3 f = new Vec3(Mth.lerp(pt, e.xo, e.getX()), Mth.lerp(pt, e.yo, e.getY()), Mth.lerp(pt, e.zo, e.getZ()));
        float a = (float) (Mth.clamp(age / 3, 0, 1) * Mth.clamp((p.life - age) / 8, 0, 1));
        double rise = FxBase.backOut(Mth.clamp(age / 10, 0, 1));
        double R = Math.max(0.5, p.r * 0.75 + 0.3), H = Math.max(1, p.r2);
        flatDisc(vc, m, n, cam, f.add(0, 0.03, 0), R + 0.5, ABYSS, 0.7F * a);
        GravityFxClient.ring(m, cam, f.add(0, 0.05, 0), UP, R + 0.5, 0.12, TEAL, 0.6F * a);
        for (int i = 0; i < 5; i++) {   // arms from the dark pool, wrapped up round it and squeezing
            double an = i * TAU / 5 + FxBase.h2(p.id, i, 7);
            Vec3 base = f.add(Math.cos(an) * R, 0, Math.sin(an) * R);
            Vec3 in = new Vec3(-Math.cos(an), 0, -Math.sin(an));
            double squeeze = 0.5 + 0.15 * Math.sin(now * 0.3 + i);
            tentacle(vc, m, n, cam, base, UP, in, H * 1.25 * rise, squeeze * 1.6, now * 0.15 + i, 0.16, a);
        }
    }

    // ================================================================== 3 undertow
    private static void undertow(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age, double now) {
        double turn = Math.max(1, p.sweep);
        float a = (float) Mth.clamp((p.life - age) / 6, 0, 1);
        Vec3 d = p.d.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : p.d.normalize(), side = UP.cross(d).normalize();
        double s = age < turn ? FxBase.smooth(age / turn) : 1 - FxBase.smooth((age - turn) / (p.life - turn));   // out, then back
        Vec3 front = p.c.add(d.scale(1 + (p.r - 1) * s));
        boolean back = age >= turn;
        int N = 13;
        Vec3[] crest = new Vec3[N], low = new Vec3[N];
        double[] w = new double[N];
        float[] al = new float[N];
        for (int i = 0; i < N; i++) {   // the wave: a curling dark wall with a foaming crest
            double u = i / (double) (N - 1) * 2 - 1;
            double h = 1.6 * (1 - u * u * 0.6) * (back ? 0.8 : 1);
            Vec3 b = front.add(side.scale(u * 2.4)).add(d.scale(-0.4 * u * u));
            low[i] = b;
            crest[i] = b.add(0, h, 0).add(d.scale((back ? -0.5 : 0.5) * (0.5 + 0.1 * Math.sin(now * 0.4 + u * 3))));
            w[i] = 0.12;
            al[i] = a * (float) (1 - Math.abs(u) * 0.5);
        }
        for (int i = 0; i < N - 1; i++) {
            FxBase.quad(vc, m, n, cam, low[i], low[i + 1], crest[i + 1], crest[i], WATER, 0.65F * a, 0.65F * a, 0.8F * a, 0.8F * a);
            FxBase.quad(vc, m, n, cam, crest[i], crest[i + 1], low[i + 1], low[i], WATER, 0.8F * a, 0.8F * a, 0.65F * a, 0.65F * a);
        }
        GravityFxClient.gstrip(m, cam, crest, w, al, FOAM);
        for (int i = 0; i < 8; i++) {   // the trail of churned water behind it
            double u = FxBase.h2(p.id, i, 8) * 2 - 1, back2 = FxBase.h2(p.id, i, 9) * 3;
            Vec3 q = front.add(side.scale(u * 2.2)).subtract(d.scale(back ? -back2 : back2)).add(0, 0.1, 0);
            GravityFxClient.gglow(m, cam, q, 0.25, 0.4F * a, CYAN);
        }
    }

    // ================================================================== 4 the dive
    private static void dive(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age) {
        double half = Math.max(1, p.sweep);
        double k1 = Mth.clamp(age / half, 0, 1), k2 = Mth.clamp((age - half * 0.4) / half, 0, 1);
        float a1 = (float) (1 - Mth.clamp((age - half) / 6, 0, 1)), a2 = (float) Mth.clamp((p.life - age) / 6, 0, 1);
        double r1 = 1.3 * Math.sin(Math.min(1, k1 * 1.4) * Math.PI / 2);
        flatDisc(vc, m, n, cam, p.c.add(0, 0.03, 0), r1, ABYSS, 0.9F * a1);   // the black water you sink into
        GravityFxClient.ring(m, cam, p.c.add(0, 0.05, 0), UP, r1, 0.1, TEAL, a1);
        if (k2 > 0) {
            double r2 = 1.5 * Math.sin(Math.min(1, k2 * 1.6) * Math.PI / 2);
            flatDisc(vc, m, n, cam, p.u.add(0, 0.03, 0), r2, ABYSS, 0.9F * a2);   // and the water you rise from
            GravityFxClient.ring(m, cam, p.u.add(0, 0.05, 0), UP, r2 + (age - half) * 0.15, 0.12, FOAM, a2 * 0.8F);
            for (int i = 0; i < 10; i++) {   // the splash up round you
                double an = i * TAU / 10;
                double t = Mth.clamp((age - half) / 10, 0, 1);
                Vec3 q = p.u.add(Math.cos(an) * (1 + t), 2.2 * t * (1 - t) * 2, Math.sin(an) * (1 + t));
                GravityFxClient.gglow(m, cam, q, 0.18, a2 * (float) (1 - t), FOAM);
            }
        }
    }

    // ================================================================== 5 depth charge
    private static void charge(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age, double now) {
        double burst = Math.max(1, p.sweep);
        if (age < burst) {
            double k = age / burst;
            Vec3 at = p.c.add(p.u.subtract(p.c).scale(k)).add(0, 1.6 * Math.sin(k * Math.PI) * (1 - k), 0);
            float pulse = (float) (0.5 + 0.5 * Math.sin(age * (0.4 + k * 1.2)));   // its light blinking faster as it sinks
            VoidFxClient.blackDisc(vc, m, n, cam, at, 0.32, 1, 0.02);
            GravityFxClient.gglow(m, cam, at, 0.45, 0.4F + 0.5F * pulse, TEAL);
            GravityFxClient.ring(m, cam, at, UP, 0.42, 0.05, CYAN, 0.8F);
            for (int i = 1; i <= 5; i++) {   // a trail of rising bubbles
                Vec3 b = p.c.add(p.u.subtract(p.c).scale(Math.max(0, k - i * 0.05))).add(0, i * 0.15, 0);
                GravityFxClient.ring(m, cam, b, b.subtract(cam), 0.06, 0.02, FOAM, 0.5F * (1 - i / 6F));
            }
            return;
        }
        double q = (age - burst) / (p.life - burst);
        float a = (float) (1 - q);
        GravityFxClient.ring(m, cam, p.u.add(0, -0.5, 0), UP, p.r * FxBase.smooth(Math.min(1, q * 1.6)), 0.4, CYAN, a);
        GravityFxClient.ring(m, cam, p.u.add(0, -0.5, 0), UP, p.r * 0.7 * FxBase.smooth(Math.min(1, q * 1.6)), 0.25, TEAL, a * 0.6F);
        VoidFxClient.blackDisc(vc, m, n, cam, p.u, p.r * 0.5 * Math.sin(Math.min(1, q * 3) * Math.PI / 2) * (1 - q), 0.7F * a, 0.05);
        GravityFxClient.gglow(m, cam, p.u, p.r * 0.6, 0.4F * a, CYAN);
    }

    // ================================================================== 6 the leviathan
    private static void leviathan(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age, double now) {
        double k = Mth.clamp(age / (p.life * 0.85), 0, 1);
        float a = (float) Mth.clamp((p.life - age) / 5, 0, 1);
        Vec3 d = p.u.subtract(p.c), dir = d.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : d.normalize();
        Vec3 side = UP.cross(dir).normalize();
        double L = d.length();
        // the dark wakes on the ground at both ends
        flatDisc(vc, m, n, cam, p.c.add(0, 0.03, 0), 1.6 * Math.min(1, age / 4), ABYSS, 0.8F * a);
        flatDisc(vc, m, n, cam, p.u.add(0, 0.03, 0), 1.6 * Mth.clamp((age - 4) / 4, 0, 1), ABYSS, 0.8F * a);
        // its body: an arc from one end to the other; we see the part of it that's above ground at this moment
        int N = 30;
        double head = -0.25 + 1.6 * k, tail = head - 0.9;
        List<Vec3> pts = new ArrayList<>();
        List<Double> ws = new ArrayList<>();
        for (int i = 0; i < N; i++) {
            double u = tail + (head - tail) * i / (N - 1);
            if (u < 0 || u > 1) continue;
            double y = 5.0 * Math.sin(u * Math.PI) - 0.2;
            if (y < 0) continue;
            double v = (u - tail) / (head - tail);   // 0 tail .. 1 head
            pts.add(p.c.add(dir.scale(u * L)).add(side.scale(0.5 * Math.sin(u * 7 + now * 0.2))).add(0, y, 0));
            ws.add(0.95 * Math.sin(Math.min(1, v * 1.15) * Math.PI) + 0.12);
        }
        if (pts.size() >= 2) {
            Vec3[] P = pts.toArray(new Vec3[0]);
            double[] W = new double[P.length];
            for (int i = 0; i < W.length; i++) W[i] = ws.get(i);
            strip(vc, m, n, cam, P, W, ABYSS, a);
            for (int i = 0; i < P.length; i++) {   // the line of lights down its flank, and the fin along its back
                GravityFxClient.gglow(m, cam, P[i], 0.18 * W[i] + 0.06, 0.8F * a, i % 3 == 0 ? CYAN : TEAL);
                if (i % 2 == 0 && i < P.length - 1) {
                    Vec3 tg = P[Math.min(P.length - 1, i + 1)].subtract(P[i]);
                    Vec3 nrm = tg.cross(side);
                    if (nrm.lengthSqr() > 1e-6) {
                        nrm = nrm.normalize();
                        if (nrm.y < 0) nrm = nrm.scale(-1);
                        tri(vc, m, n, cam, P[i].add(nrm.scale(W[i] * 0.8)), P[i + 1].add(nrm.scale(W[i + 1] * 0.8)), P[i].add(nrm.scale(W[i] * 0.8 + 0.6)).subtract(tg.scale(0.6)), DEEP, a);
                    }
                }
            }
            Vec3 hd = P[P.length - 1];
            if (head <= 1.0) {   // its eye
                GravityFxClient.gglow(m, cam, hd.add(side.scale(0.4)).add(0, 0.2, 0), 0.22, a, LANTERN);
                GravityFxClient.gglow(m, cam, hd.add(side.scale(-0.4)).add(0, 0.2, 0), 0.22, a, LANTERN);
            }
        }
        for (int i = 0; i < 2; i++) {   // spray where it breaks the surface and where it goes back in
            Vec3 at = i == 0 ? p.c : p.u;
            double t0 = i == 0 ? 2 : p.life * 0.45;
            double t = (age - t0) / 12;
            if (t < 0 || t > 1) continue;
            GravityFxClient.ring(m, cam, at.add(0, 0.08, 0), UP, 1.5 + 2.5 * t, 0.25, FOAM, (float) (1 - t) * a);
        }
    }

    // ================================================================== 7 weight
    private static void weight(Matrix4f m, Vec3 cam, Minecraft mc, FxPacket p, double age, double now, float pt) {
        Entity e = mc.level.getEntity((int) p.v.x);
        if (e == null) return;
        Vec3 f = new Vec3(Mth.lerp(pt, e.xo, e.getX()), Mth.lerp(pt, e.yo, e.getY()), Mth.lerp(pt, e.zo, e.getZ()));
        float a = (float) (Mth.clamp(age / 3, 0, 1) * Mth.clamp((p.life - age) / 10, 0, 1));
        double R = Math.max(0.5, p.r * 0.8 + 0.25), H = e.getBbHeight();
        for (int i = 0; i < 3; i++) {   // rings pressing down onto it, again and again
            double ph = ((now * 0.05 + i / 3.0)) % 1.0;
            GravityFxClient.ring(m, cam, f.add(0, H + 0.6 - (H + 0.5) * ph * ph, 0), UP, R * (1.2 - 0.3 * ph), 0.1, CYAN, a * (float) (0.4 + 0.6 * ph));
        }
        GravityFxClient.ring(m, cam, f.add(0, 0.05, 0), UP, R + 0.2 + 0.05 * Math.sin(now * 0.3), 0.18, TEAL, 0.7F * a);
        for (int i = 0; i < 6; i++) {   // the dark weight hanging on it
            double an = i * TAU / 6 + now * 0.01;
            Vec3 b = f.add(Math.cos(an) * R, 0.05, Math.sin(an) * R);
            GravityFxClient.gseg(m, cam, b.add(0, H + 0.4, 0), b, 0.05, 0, 0.5F * a, WATER);
        }
    }

    // ================================================================== 8 bioluminescence
    private static void glow(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Minecraft mc, FxPacket p, double age, double now, float pt) {
        Entity e = mc.level.getEntity((int) p.v.x);
        if (e == null) return;
        Vec3 f = new Vec3(Mth.lerp(pt, e.xo, e.getX()), Mth.lerp(pt, e.yo, e.getY()), Mth.lerp(pt, e.zo, e.getZ()));
        float a = (float) (Mth.clamp(age / 10, 0, 1) * Mth.clamp((p.life - age) / 20, 0, 1));
        GravityFxClient.ring(m, cam, f.add(0, 0.05, 0), UP, 0.9 + 0.08 * Math.sin(now * 0.15), 0.15, TEAL, 0.5F * a);
        boolean self = e == mc.player && mc.options.getCameraType().isFirstPerson();
        if (self) return;
        double yaw = Math.toRadians(Mth.rotLerp(pt, e.yRotO, e.getYRot()));
        Vec3 fwd = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw)), right = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
        double H = e.getBbHeight();
        for (int i = 0; i < 14; i++) {   // lights along the body that wake and fade in waves, like a deep creature's
            double side = i % 2 == 0 ? 1 : -1, y = H * (0.15 + 0.75 * (i / 2) / 6.0);
            float tw = (float) (0.5 + 0.5 * Math.sin(now * 0.2 - y * 3));
            Vec3 q = f.add(right.scale(side * 0.32)).add(fwd.scale(0.12)).add(0, y, 0);
            GravityFxClient.gglow(m, cam, q, 0.09, a * (0.3F + 0.7F * tw), i % 4 == 0 ? CYAN : TEAL);
        }
        for (int i = 0; i < 6; i++) {   // and small glowing things drifting round you
            double an = now * 0.03 * (i % 2 == 0 ? 1 : -1) + i * TAU / 6, rr = 0.9 + 0.2 * Math.sin(now * 0.07 + i);
            Vec3 q = f.add(Math.cos(an) * rr, H * (0.3 + 0.5 * FxBase.h2(p.id, i, 10)) + 0.15 * Math.sin(now * 0.1 + i), Math.sin(an) * rr);
            GravityFxClient.gglow(m, cam, q, 0.12, 0.7F * a, i % 3 == 0 ? LANTERN : TEAL);
        }
    }

    // ================================================================== 9 the trench and the maw
    private static void trench(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age, double now) {
        double bite = Math.max(1, p.sweep);
        float a = (float) Mth.clamp((p.life - age) / 10, 0, 1);
        double open = FxBase.smooth(Mth.clamp(age / 10, 0, 1)), R = p.r;
        Vec3 c = p.c;
        flatDisc(vc, m, n, cam, c.add(0, 0.04, 0), R * open, ABYSS, 0.95F * a);   // the trench mouth: black
        flatDisc(vc, m, n, cam, c.add(0, 0.05, 0), R * open * 0.6, new float[]{0, 0, 0}, a);
        GravityFxClient.ring(m, cam, c.add(0, 0.06, 0), UP, R * open, 0.25, TEAL, 0.8F * a);
        for (int i = 0; i < 16; i++) {   // the crumbling edge, falling in (only light - nothing is broken)
            double an = FxBase.h2(p.id, i, 11) * TAU, ph = ((now * 0.05 + FxBase.h2(p.id, i, 12))) % 1.0;
            Vec3 q = c.add(Math.cos(an) * R * open * (1 - ph * 0.6), 0.1 - ph * 0.5, Math.sin(an) * R * open * (1 - ph * 0.6));
            GravityFxClient.gglow(m, cam, q, 0.12, a * (float) (1 - ph) * 0.7F, CYAN);
        }
        // the maw rises out of it: a ring of inward teeth round a throat, closing at the bite
        double rise = Mth.clamp((age - bite * 0.45) / (bite * 0.4), 0, 1), shut = Mth.clamp((age - bite + 5) / 5, 0, 1);
        if (rise <= 0) return;
        double rr = R * 0.85 * (1 - 0.8 * FxBase.smooth(shut)), y = 3.0 * FxBase.backOut(rise) - (age > bite ? (age - bite) * 0.15 : 0);
        int T = 18;
        for (int i = 0; i < T; i++) {
            double a0 = i * TAU / T + 0.05 * Math.sin(now * 0.1), a1 = (i + 1) * TAU / T + 0.05 * Math.sin(now * 0.1), am = (a0 + a1) / 2;
            Vec3 b0 = c.add(Math.cos(a0) * rr, 0.1, Math.sin(a0) * rr), b1 = c.add(Math.cos(a1) * rr, 0.1, Math.sin(a1) * rr);
            Vec3 lip0 = b0.add(0, y, 0), lip1 = b1.add(0, y, 0);
            FxBase.quad(vc, m, n, cam, b0, b1, lip1, lip0, ABYSS, a, a, a, a);   // the throat's wall
            FxBase.quad(vc, m, n, cam, lip0, lip1, b1, b0, ABYSS, a, a, a, a);
            Vec3 tip = c.add(Math.cos(am) * rr * 0.35, y + 0.9 * (1 - shut) + 0.3, Math.sin(am) * rr * 0.35);   // a tooth curving inward
            tri(vc, m, n, cam, lip0, lip1, tip, BONE, a);
            if (i % 2 == 0) GravityFxClient.gglow(m, cam, lip0.add(0, -0.4, 0), 0.25, 0.8F * a, TEAL);
        }
        if (shut >= 1) GravityFxClient.gglow(m, cam, c.add(0, y, 0), R * 0.9, (float) Mth.clamp(1 - (age - bite) / 10, 0, 1) * 0.6F, CYAN);
    }

    // ================================================================== 11 feeding light
    private static void feed(Matrix4f m, Vec3 cam, Minecraft mc, FxPacket p, double age, float pt) {
        Entity e = mc.level.getEntity((int) p.v.x);
        if (e == null) return;
        Vec3 to = new Vec3(Mth.lerp(pt, e.xo, e.getX()), Mth.lerp(pt, e.yo, e.getY()) + e.getBbHeight() * 0.55, Mth.lerp(pt, e.zo, e.getZ()));
        double k = FxBase.smooth(Mth.clamp(age / p.life, 0, 1));
        Vec3 at = p.c.add(to.subtract(p.c).scale(k)).add(0, Math.sin(k * Math.PI) * 0.8, 0);
        GravityFxClient.gglow(m, cam, at, 0.18, (float) (1 - k * 0.5), TEAL);
        GravityFxClient.gglow(m, cam, at, 0.07, 1, LANTERN);
    }
}
