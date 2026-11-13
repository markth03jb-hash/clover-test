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
 * The Hunter's Grimoire, drawn: the trick cleaver itself - a dark wooden haft, a heavy serrated blade with its edge
 * stained red, folding out on a hinge into the long saw - swung in crimson arcs; silver shots with a muzzle flash;
 * grey mist where you quickstepped; the gold ring of a stagger; blood bursting from a visceral strike; the rune of
 * the hunter's mark; and the blood moon, hanging huge and red in the sky, with the edges of your screen gone red.
 * Fed by FxPacket shape 89 (server/HunterFx), {@code h} = kind: 0 swing, 1 shot, 2 mist, 3 stagger, 4 visceral,
 * 5 mark, 7 blood moon, 8 nightmare arcs, 9 rally, 10 trick.
 */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = "iron_spell_grimoire", value = net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class HunterFxClient {
    private HunterFxClient() {}

    private static final double TAU = Math.PI * 2;
    private static final Vec3 UP = new Vec3(0, 1, 0);
    static final float[] STEEL = {0.68F, 0.7F, 0.74F}, STEEL_SH = {0.4F, 0.42F, 0.46F}, EDGE = {0.92F, 0.94F, 0.98F}, STAIN = {0.45F, 0.03F, 0.04F},
            WOOD = {0.24F, 0.13F, 0.07F}, IRON = {0.2F, 0.2F, 0.22F}, CRIMSON = {0.9F, 0.06F, 0.1F}, SILVER = {0.9F, 0.95F, 1.0F},
            GOLD = {1.0F, 0.72F, 0.25F}, MIST = {0.62F, 0.64F, 0.7F}, MOON = {0.62F, 0.03F, 0.05F}, MOON_HI = {0.95F, 0.25F, 0.2F}, BLOOD = {0.5F, 0.0F, 0.02F};

    private static final class L {
        final FxPacket p;
        final double born;
        L(FxPacket p, double born) { this.p = p; this.born = born; }
    }

    private static final List<L> LIVE = new ArrayList<>();

    public static void spawn(FxPacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (LIVE.size() > 300) LIVE.remove(0);
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
                case 0 -> swing(vc, m, n, cam, mc, p, age, pt);
                case 1 -> shot(m, cam, p, age);
                case 2 -> mist(vc, m, n, cam, p, age);
                case 3 -> stagger(m, cam, mc, p, age, now, pt);
                case 4 -> visceral(vc, m, n, cam, p, age);
                case 5 -> mark(m, cam, mc, p, age, now, pt);
                case 7 -> moon(vc, m, n, cam, mc, p, age, now, pt);
                case 8 -> arcs(m, cam, p, age, now);
                case 9 -> rally(m, cam, mc, p, age, pt);
                case 10 -> trick(vc, m, n, cam, mc, p, age, pt);
                default -> LIVE.remove(l);
            }
        }
    }

    /** The red at the edges of your screen while the blood moon is up over you. */
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onGui(net.minecraftforge.client.event.RenderGuiEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || LIVE.isEmpty()) return;
        double now = mc.level.getGameTime() + e.getPartialTick();
        float a = 0;
        for (L l : LIVE) if ((int) l.p.h == 7 && (int) l.p.v.x == mc.player.getId()) {
            double age = now - l.born;
            a = Math.max(a, (float) (Mth.clamp(age / 20, 0, 1) * Mth.clamp((l.p.life - age) / 20, 0, 1)));
        }
        if (a <= 0.01F) return;
        var g = e.getGuiGraphics();
        int w = g.guiWidth(), h = g.guiHeight();
        for (int i = 0; i < 12; i++) {   // a vignette in bands, thicker toward the edges
            int al = (int) (a * 9 * (12 - i) / 12F);
            int in = i * Math.min(w, h) / 50;
            g.fill(in, in, w - in, in + Math.min(w, h) / 50, al << 24 | 0x5A0006);
            g.fill(in, h - in - Math.min(w, h) / 50, w - in, h - in, al << 24 | 0x5A0006);
            g.fill(in, in, in + Math.min(w, h) / 50, h - in, al << 24 | 0x5A0006);
            g.fill(w - in - Math.min(w, h) / 50, in, w - in, h - in, al << 24 | 0x5A0006);
        }
    }

    // ================================================================== the trick cleaver
    /**
     * The weapon in a hand at {@code hand}, pointing along {@code d}, its flat side facing {@code flat}. SHORT: a haft
     * and a heavy cleaver blade with a saw-toothed back. LONG: the blade folds out on a hinge at the end of an iron arm.
     */
    static void cleaver(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 hand, Vec3 d, Vec3 flat, boolean lng, float a, float unfold) {
        Vec3 w = d.cross(flat);
        w = w.lengthSqr() < 1e-6 ? new Vec3(0, 1, 0) : w.normalize();   // the blade's width runs along w
        Vec3 butt = hand.subtract(d.scale(0.18)), top = hand.add(d.scale(0.32));
        bar(vc, m, n, cam, butt, top, w, flat, 0.045, WOOD, a);   // the haft
        bar(vc, m, n, cam, top, top.add(d.scale(0.05)), w, flat, 0.07, IRON, a);   // a collar
        Vec3 bladeBase = top.add(d.scale(0.05));
        if (lng) {   // the iron arm the blade folds out on
            double arm = 0.85 * unfold;
            bar(vc, m, n, cam, bladeBase, bladeBase.add(d.scale(arm)), w, flat, 0.035, IRON, a);
            bladeBase = bladeBase.add(d.scale(arm));
            GravityFxClient.gglow(m, cam, bladeBase, 0.06, 0.3F * a, SILVER);
        }
        double L = lng ? 1.1 : 0.9, W = lng ? 0.17 : 0.24;
        Vec3 tipEdge = bladeBase.add(d.scale(L)).add(w.scale(-W * 0.2)), tipBack = bladeBase.add(d.scale(L * 0.86)).add(w.scale(W));
        Vec3 baseEdge = bladeBase.add(w.scale(-W * 0.35)), baseBack = bladeBase.add(w.scale(W));
        for (int side = 0; side < 2; side++) {   // the flat of the blade, both faces
            float[] col = side == 0 ? STEEL : STEEL_SH;
            if (side == 0) FxBase.quad(vc, m, n, cam, baseEdge, tipEdge, tipBack, baseBack, col, a, a, a, a);
            else FxBase.quad(vc, m, n, cam, baseBack, tipBack, tipEdge, baseEdge, col, a, a, a, a);
        }
        // the edge, bright, and stained red toward the tip
        GravityFxClient.gseg(m, cam, baseEdge, tipEdge, 0.02, 0.6F * a, 0.4F * a, EDGE);
        FxBase.quad(vc, m, n, cam, baseEdge.add(tipEdge.subtract(baseEdge).scale(0.45)), tipEdge, tipEdge.add(w.scale(W * 0.35)), baseEdge.add(tipEdge.subtract(baseEdge).scale(0.45)).add(w.scale(W * 0.25)), STAIN, 0.85F * a, 0.85F * a, 0.85F * a, 0.85F * a);
        // the saw teeth along the back
        int teeth = lng ? 9 : 7;
        for (int i = 0; i < teeth; i++) {
            double u0 = i / (double) teeth, u1 = (i + 1) / (double) teeth;
            Vec3 b0 = baseBack.add(tipBack.subtract(baseBack).scale(u0)), b1 = baseBack.add(tipBack.subtract(baseBack).scale(u1));
            Vec3 tooth = b0.add(b1.subtract(b0).scale(0.3)).add(w.scale(0.06));
            FxBase.quad(vc, m, n, cam, b0, tooth, b1, b1, STEEL_SH, a, a, a, a);
            FxBase.quad(vc, m, n, cam, b1, tooth, b0, b0, STEEL_SH, a, a, a, a);
        }
    }

    private static void bar(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 a, Vec3 b, Vec3 w, Vec3 flat, double half, float[] col, float al) {
        Vec3 s = w.scale(half), t = flat.scale(half);
        FxBase.quad(vc, m, n, cam, a.add(s), b.add(s), b.subtract(s), a.subtract(s), col, al, al, al, al);
        FxBase.quad(vc, m, n, cam, a.subtract(s), b.subtract(s), b.add(s), a.add(s), col, al, al, al, al);
        FxBase.quad(vc, m, n, cam, a.add(t), b.add(t), b.subtract(t), a.subtract(t), col, al, al, al, al);
        FxBase.quad(vc, m, n, cam, a.subtract(t), b.subtract(t), b.add(t), a.add(t), col, al, al, al, al);
    }

    private static Vec3 feet(Entity e, float pt) { return new Vec3(Mth.lerp(pt, e.xo, e.getX()), Mth.lerp(pt, e.yo, e.getY()), Mth.lerp(pt, e.zo, e.getZ())); }

    // ================================================================== 0 swing
    private static void swing(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Minecraft mc, FxPacket p, double age, float pt) {
        Entity e = mc.level.getEntity((int) p.v.x);
        if (e == null) return;
        boolean first = e == mc.player && mc.options.getCameraType().isFirstPerson();
        double k = Mth.clamp(age / Math.max(1, p.speed), 0, 1);
        double ease = 1 - Math.pow(1 - k, 2.5);
        float a = (float) Mth.clamp((p.life - age) / 4, 0, 1);
        boolean lng = p.r2 > 0.5;
        double yaw = Math.toRadians(Mth.rotLerp(pt, e.yRotO, e.getYRot()));
        double a0 = p.a0, a1 = p.d.x;
        double an = a0 + (a1 - a0) * ease;
        Vec3 f = feet(e, pt);
        Vec3 hand = f.add(0, e.getBbHeight() * (first ? 0.72 : 0.6), 0).add(new Vec3(-Math.sin(yaw), 0, Math.cos(yaw)).scale(0.3));
        double reach = lng ? 2.3 : 1.3;
        Vec3 d = dirAt(yaw, an, p.r, a0 == a1 ? ease : 0);
        if (a0 == a1) hand = hand.add(d.scale(0.6 * Math.sin(ease * Math.PI)));   // a thrust
        Vec3 flat = d.cross(UP);
        flat = flat.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : flat.normalize();
        if (!first || k < 1) cleaver(vc, m, n, cam, hand, d, flat.cross(d).normalize(), lng, a, 1F);
        if (a0 != a1) {   // the crimson arc behind the blade
            int N = 12;
            Vec3[] pts = new Vec3[N];
            double[] w = new double[N];
            float[] al = new float[N];
            for (int i = 0; i < N; i++) {
                double u = i / (double) (N - 1);
                double aa = a0 + (an - a0) * u;
                pts[i] = hand.add(dirAt(yaw, aa, p.r, 0).scale(reach));
                w[i] = 0.25 * u;
                al[i] = (float) (u * u) * 0.8F * a;
            }
            GravityFxClient.gstrip(m, cam, pts, w, al, CRIMSON);
        }
    }

    /** The direction of the blade at yaw offset {@code an}, tilted up or down by {@code tilt}. */
    private static Vec3 dirAt(double yaw, double an, double tilt, double thrust) {
        double y = yaw + an;
        return new Vec3(-Math.sin(y), tilt, Math.cos(y)).normalize();
    }

    // ================================================================== 1 shot
    private static void shot(Matrix4f m, Vec3 cam, FxPacket p, double age) {
        float a = (float) (1 - age / p.life);
        boolean blood = p.a0 > 0.5;
        float[] col = blood ? CRIMSON : SILVER;
        GravityFxClient.gseg(m, cam, p.c, p.u, blood ? 0.07 : 0.04, a, 0.4F * a, col);
        GravityFxClient.gseg(m, cam, p.c, p.u, 0.015, a, a, new float[]{1, 1, 1});
        if (age < 2) GravityFxClient.gglow(m, cam, p.c, 0.35, 0.9F, GOLD);   // the muzzle flash
        GravityFxClient.gglow(m, cam, p.u, 0.25 * (1 + age / 3), 0.7F * a, col);
    }

    // ================================================================== 2 mist
    private static void mist(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age) {
        double k = Mth.clamp(age / p.life, 0, 1);
        float a = (float) (1 - k);
        for (int i = 0; i < 9; i++) {
            double u = i / 8.0;
            Vec3 c = p.c.add(p.u.subtract(p.c).scale(u)).add(0, p.r * (0.3 + 0.4 * FxBase.h2(p.id, i, 3)) + k * 0.5, 0);
            puff(vc, m, n, cam, c, 0.35 + 0.4 * k + 0.1 * FxBase.h2(p.id, i, 4), 0.3F * a * (float) (1 - Math.abs(u - 0.5)));
        }
        puff(vc, m, n, cam, p.c.add(0, p.r * 0.5, 0), 0.6 + k, 0.35F * a);
    }

    private static void puff(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 c, double r, float a) {
        Vec3 view = c.subtract(cam);
        if (view.lengthSqr() < 1e-6) return;
        Vec3[] f = frame(view.normalize());
        for (int i = 0; i < 12; i++) {
            double a0 = i * TAU / 12, a1 = (i + 1) * TAU / 12;
            FxBase.quad(vc, m, n, cam, c, c, c.add(f[0].scale(Math.cos(a1) * r)).add(f[1].scale(Math.sin(a1) * r)), c.add(f[0].scale(Math.cos(a0) * r)).add(f[1].scale(Math.sin(a0) * r)), MIST, a, a, 0, 0);
        }
    }

    // ================================================================== 3 stagger
    private static void stagger(Matrix4f m, Vec3 cam, Minecraft mc, FxPacket p, double age, double now, float pt) {
        Entity e = mc.level.getEntity((int) p.v.x);
        if (e == null) return;
        Vec3 f = feet(e, pt);
        float a = (float) (Mth.clamp(age / 2, 0, 1) * Mth.clamp((p.life - age) / 6, 0, 1));
        float flash = age < 6 ? 1F : 0.6F + 0.4F * (float) Math.sin(now * 0.9);
        Vec3 top = f.add(0, e.getBbHeight() + 0.35, 0);
        GravityFxClient.ring(m, cam, top, UP, 0.4, 0.05, GOLD, 0.9F * a * flash);
        for (int i = 0; i < 3; i++) {   // little stars circling the head
            double an = now * 0.25 + i * TAU / 3;
            GravityFxClient.gglow(m, cam, top.add(Math.cos(an) * 0.4, 0.05 * Math.sin(now * 0.5 + i), Math.sin(an) * 0.4), 0.08, 0.9F * a, GOLD);
        }
        if (age < 6) GravityFxClient.ring(m, cam, f.add(0, e.getBbHeight() * 0.55, 0), cam.subtract(f).normalize(), 0.5 + age * 0.25, 0.08, GOLD, (float) (1 - age / 6) * 0.8F);
    }

    // ================================================================== 4 visceral: blood bursting out
    private static void visceral(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age) {
        double k = Mth.clamp(age / p.life, 0, 1);
        float a = (float) Math.min(1, (1 - k) * 1.6);
        Vec3 d = p.d.lengthSqr() < 1e-6 ? UP : p.d.normalize();
        int N = (int) (10 + 8 * p.r);
        for (int i = 0; i < N; i++) {   // streams of blood thrown out the way the blow went, falling as they go
            Vec3 dir = d.add((FxBase.h2(p.id, i, 5) - 0.5) * 1.6, FxBase.h2(p.id, i, 6) * 1.2, (FxBase.h2(p.id, i, 7) - 0.5) * 1.6).normalize();
            double sp = (0.18 + 0.2 * FxBase.h2(p.id, i, 8)) * p.r;
            double t0 = age, t1 = Math.max(0, age - 2.5);
            Vec3 q0 = p.c.add(dir.scale(sp * t0)).add(0, -0.012 * t0 * t0, 0), q1 = p.c.add(dir.scale(sp * t1)).add(0, -0.012 * t1 * t1, 0);
            InkFxClient.ink(vc, m, n, cam, new Vec3[]{q1, q0}, new double[]{0.02, 0.05 * p.r}, a);
        }
        GravityFxClient.gglow(m, cam, p.c, 0.6 * p.r * (1 + k), 0.6F * a, CRIMSON);
    }

    // ================================================================== 5 the hunter's mark
    private static void mark(Matrix4f m, Vec3 cam, Minecraft mc, FxPacket p, double age, double now, float pt) {
        Entity e = mc.level.getEntity((int) p.v.x);
        if (e == null) return;
        Vec3 f = feet(e, pt);
        float a = (float) (Mth.clamp(age / 4, 0, 1) * Mth.clamp((p.life - age) / 10, 0, 1));
        Vec3 c = f.add(0, e.getBbHeight() + 0.7 + 0.08 * Math.sin(now * 0.15), 0);
        Vec3 view = c.subtract(cam);
        if (view.lengthSqr() < 1e-6) return;
        Vec3[] fr = frame(view.normalize());
        Vec3 up = fr[1].y >= 0 ? fr[1] : fr[1].scale(-1), side = view.normalize().cross(up).normalize();
        GravityFxClient.bring(m, cam, c, fr, 0.32, 0.04, CRIMSON, 0.9F * a);   // the rune: a ring, a stroke through it, a crescent over
        GravityFxClient.gseg(m, cam, c.subtract(up.scale(0.42)), c.add(up.scale(0.42)), 0.03, 0.9F * a, 0.9F * a, CRIMSON);
        for (int i = 0; i < 6; i++) {
            double a0 = Math.PI * (0.15 + 0.7 * i / 6.0), a1 = Math.PI * (0.15 + 0.7 * (i + 1) / 6.0);
            Vec3 p0 = c.add(up.scale(0.2)).add(side.scale(Math.cos(a0) * 0.22)).add(up.scale(Math.sin(a0) * 0.22)), p1 = c.add(up.scale(0.2)).add(side.scale(Math.cos(a1) * 0.22)).add(up.scale(Math.sin(a1) * 0.22));
            GravityFxClient.gseg(m, cam, p0, p1, 0.025, 0.8F * a, 0.8F * a, GOLD);
        }
        GravityFxClient.gglow(m, cam, c, 0.5, 0.25F * a, CRIMSON);
    }

    // ================================================================== 7 the blood moon
    private static void moon(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Minecraft mc, FxPacket p, double age, double now, float pt) {
        Entity e = mc.level.getEntity((int) p.v.x);
        if (e == null) return;
        Vec3 f = feet(e, pt);
        float a = (float) (Mth.clamp(age / 20, 0, 1) * Mth.clamp((p.life - age) / 20, 0, 1));
        double rise = Mth.clamp(age / 30, 0, 1);
        Vec3 c = f.add(9, 18 + 8 * rise, -14);
        double R = 6;
        GravityFxClient.gglow(m, cam, c, R * 2.4, 0.25F * a, MOON);   // its glow over the sky
        Vec3 view = c.subtract(cam);
        if (view.lengthSqr() > 1e-6) {
            Vec3[] fr = frame(view.normalize());
            for (int i = 0; i < 28; i++) {   // the disc, solid and dark red
                double a0 = i * TAU / 28, a1 = (i + 1) * TAU / 28;
                FxBase.quad(vc, m, n, cam, c, c, c.add(fr[0].scale(Math.cos(a1) * R)).add(fr[1].scale(Math.sin(a1) * R)), c.add(fr[0].scale(Math.cos(a0) * R)).add(fr[1].scale(Math.sin(a0) * R)), MOON, a, a, a, a);
            }
            for (int i = 0; i < 7; i++) {   // its seas, a little brighter
                Vec3 q = c.add(fr[0].scale((FxBase.h2(7, i, 9) - 0.5) * R * 1.2)).add(fr[1].scale((FxBase.h2(7, i, 10) - 0.5) * R * 1.2)).subtract(view.normalize().scale(0.05));
                GravityFxClient.gglow(m, cam, q, R * (0.12 + 0.12 * FxBase.h2(7, i, 11)), 0.25F * a, MOON_HI);
            }
            GravityFxClient.bring(m, cam, c, fr, R, R * 0.12, MOON_HI, 0.5F * a);
        }
        GravityFxClient.ring(m, cam, f.add(0, 0.05, 0), UP, 1.4 + 0.2 * Math.sin(now * 0.12), 0.15, CRIMSON, 0.35F * a);
    }

    // ================================================================== 8 nightmare arcs
    private static void arcs(Matrix4f m, Vec3 cam, FxPacket p, double age, double now) {
        Vec3 d = p.d.normalize(), s = new Vec3(-d.z, 0, d.x);
        int N = Math.max(1, (int) p.a0);
        for (int k = 0; k < N; k++) {
            double t0 = 2 + k * 4, q = Mth.clamp((age - t0) / 8, 0, 1);
            if (age < t0) continue;
            float a = (float) (1 - Mth.clamp((age - t0 - 6) / 10, 0, 1));
            Vec3 c = p.c.add(d.scale(p.r * (k + q) / N));
            double R = 2.2 + k * 0.5;
            int M = 16;
            Vec3[] pts = new Vec3[M];
            double[] w = new double[M];
            float[] al = new float[M];
            for (int i = 0; i < M; i++) {   // a great crescent standing up out of the ground, rolling forward
                double an = Math.PI * i / (M - 1);
                pts[i] = c.add(s.scale(Math.cos(an) * R)).add(0, Math.sin(an) * R * 1.1, 0).add(d.scale(-Math.sin(an) * 0.4));
                w[i] = 0.12 + 0.35 * Math.sin(an);
                al[i] = 0.9F * a;
            }
            GravityFxClient.gstrip(m, cam, pts, w, al, CRIMSON);
            for (int i = 0; i < M; i++) w[i] *= 0.25;
            GravityFxClient.gstrip(m, cam, pts, w, al, new float[]{1, 0.85F, 0.8F});
            GravityFxClient.ring(m, cam, c.add(0, 0.05, 0), UP, R * 0.9, 0.2, CRIMSON, 0.4F * a);
        }
    }

    // ================================================================== 9 rally
    private static void rally(Matrix4f m, Vec3 cam, Minecraft mc, FxPacket p, double age, float pt) {
        Entity e = mc.level.getEntity((int) p.v.x);
        if (e == null || (e == mc.player && mc.options.getCameraType().isFirstPerson())) return;
        Vec3 f = feet(e, pt);
        float a = (float) (1 - age / p.life);
        for (int i = 0; i < 6; i++) {
            double an = i * TAU / 6 + age * 0.2;
            Vec3 q = f.add(Math.cos(an) * 0.45, e.getBbHeight() * (0.2 + age / p.life * 0.9), Math.sin(an) * 0.45);
            GravityFxClient.gseg(m, cam, q, q.add(0, 0.3, 0), 0.04, 0.9F * a, 0, GOLD);
        }
    }

    // ================================================================== 10 the trick: the weapon snaps to its other form
    private static void trick(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Minecraft mc, FxPacket p, double age, float pt) {
        Entity e = mc.level.getEntity((int) p.v.x);
        if (e == null || (e == mc.player && mc.options.getCameraType().isFirstPerson())) return;
        Vec3 f = feet(e, pt);
        double yaw = Math.toRadians(Mth.rotLerp(pt, e.yRotO, e.getYRot()));
        Vec3 fwd = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        Vec3 hand = f.add(0, e.getBbHeight() * 0.6, 0).add(fwd.scale(0.4));
        double k = Mth.clamp(age / 8, 0, 1);
        boolean toLong = p.a0 > 0.5;
        double spin = k * TAU;   // it turns once in the hand as it changes
        Vec3 d = new Vec3(-Math.sin(yaw) * Math.cos(spin), Math.sin(spin), Math.cos(yaw) * Math.cos(spin)).normalize();
        Vec3 side = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
        float a = (float) Mth.clamp((p.life - age) / 3, 0, 1);
        cleaver(vc, m, n, cam, hand, d, side, toLong || k < 0.5, a, toLong ? (float) k : (float) (1 - k));
        if (age > 4 && age < 9) {
            GravityFxClient.ring(m, cam, hand, d, 0.2 + (age - 4) * 0.12, 0.04, SILVER, (float) (1 - (age - 4) / 5) * 0.9F);
            GravityFxClient.gglow(m, cam, hand, 0.3, 0.7F, SILVER);
        }
    }

    private static Vec3[] frame(Vec3 axis) {
        Vec3 e1 = Math.abs(axis.y) > 0.9 ? new Vec3(1, 0, 0) : UP;
        e1 = e1.subtract(axis.scale(e1.dot(axis))).normalize();
        return new Vec3[]{e1, axis.cross(e1)};
    }
}
