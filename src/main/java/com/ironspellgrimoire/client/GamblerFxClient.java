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
 * The Gambler's Grimoire, drawn: solid gold coins that spin edge-on and flat (a sun on heads, a crescent on tails),
 * a roulette wheel of red and black (and one green) laid on the ground with its ball rattling round, poker chips,
 * slot reels with cherries, bells, sevens, bars and skulls, dice that tumble and land on their faces, and fountains
 * of coins. Fed by FxPacket shape 91 (server/GamblerFx), {@code h} = kind: 0 the flip, 1 coin, 2 roulette, 3 chip,
 * 4 reels, 5 dice, 6 payout, 7 All In, 8 Insurance, 9 the great machine.
 */
public final class GamblerFxClient {
    private GamblerFxClient() {}

    private static final double TAU = Math.PI * 2;
    private static final Vec3 UP = new Vec3(0, 1, 0);
    static final float[] GOLD = {1.0F, 0.78F, 0.22F}, GOLD_SH = {0.66F, 0.46F, 0.08F}, GOLD_GLOW = {1.0F, 0.85F, 0.4F},
            SILVER = {0.82F, 0.84F, 0.9F}, SILVER_SH = {0.5F, 0.52F, 0.58F}, RED = {0.75F, 0.06F, 0.08F}, BLACK = {0.05F, 0.05F, 0.06F},
            GREEN = {0.05F, 0.5F, 0.2F}, FELT = {0.04F, 0.3F, 0.14F}, CREAM = {0.95F, 0.92F, 0.84F}, WHITE = {1, 1, 1}, RED_GLOW = {1.0F, 0.15F, 0.15F};
    static final float[][] CHIPS = {{0.8F, 0.08F, 0.1F}, {0.1F, 0.25F, 0.8F}, {0.08F, 0.55F, 0.2F}, {0.1F, 0.1F, 0.12F}};

    private static final class L {
        final FxPacket p;
        final double born;
        L(FxPacket p, double born) { this.p = p; this.born = born; }
    }

    private static final List<L> LIVE = new ArrayList<>();

    public static void spawn(FxPacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if ((int) p.h == 0) LIVE.removeIf(l -> (int) l.p.h == 0 && l.p.v.x == p.v.x);   // one coin over a head at a time
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
                case 0 -> flip(vc, m, n, cam, mc, p, age, pt);
                case 1 -> {
                    double k = Mth.clamp(age / Math.max(1, p.speed), 0, 1);
                    if (k >= 1) { GravityFxClient.gglow(m, cam, p.u, 0.4 * (1 + age - p.speed), (float) Mth.clamp(1 - (age - p.speed) / 4, 0, 1), GOLD_GLOW); break; }
                    Vec3 at = p.c.add(p.u.subtract(p.c).scale(k)).add(0, Math.sin(k * Math.PI) * 0.4, 0);
                    coin(vc, m, n, cam, at, axisSpin(age * 1.1), p.r, true, 1F);
                    GravityFxClient.gseg(m, cam, at, at.subtract(p.u.subtract(p.c).normalize().scale(0.9)), p.r * 0.5, 0.5F, 0, GOLD_GLOW);
                }
                case 2 -> wheel(vc, m, n, cam, p, age);
                case 3 -> {
                    double k = Mth.clamp(age / Math.max(1, p.speed), 0, 1);
                    Vec3 at = p.c.add(p.u.subtract(p.c).scale(k));
                    chip(vc, m, n, cam, at, axisSpin(age * 0.9 + p.id), 0.16, CHIPS[Mth.clamp((int) p.a0, 0, 3)], 1F);
                }
                case 4 -> reels(vc, m, n, cam, p.c, p, age, p.r, false);
                case 5 -> dice(vc, m, n, cam, p, age);
                case 6 -> payout(vc, m, n, cam, p, age);
                case 7 -> aura(vc, m, n, cam, mc, p, age, now, pt);
                case 8 -> shield(vc, m, n, cam, mc, p, age, now, pt);
                case 9 -> reels(vc, m, n, cam, p.c, p, age, 1.9, true);
                default -> LIVE.remove(l);
            }
        }
    }

    private static Vec3 axisSpin(double t) { return new Vec3(Math.cos(t), 0.25, Math.sin(t)).normalize(); }

    // ================================================================== a coin
    /** A coin of radius r at c, facing {@code nrm}: a gold (or silver) disc, a darker rim, its face stamped. */
    static void coin(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 c, Vec3 nrm, double r, boolean heads, float a) {
        Vec3[] f = frame(nrm);
        float[] face = heads ? GOLD : SILVER, rim = heads ? GOLD_SH : SILVER_SH;
        int seg = 16;
        for (int i = 0; i < seg; i++) {
            double a0 = i * TAU / seg, a1 = (i + 1) * TAU / seg;
            Vec3 p0 = c.add(f[0].scale(Math.cos(a0) * r)).add(f[1].scale(Math.sin(a0) * r)), p1 = c.add(f[0].scale(Math.cos(a1) * r)).add(f[1].scale(Math.sin(a1) * r));
            Vec3 q0 = c.add(f[0].scale(Math.cos(a0) * r * 0.8)).add(f[1].scale(Math.sin(a0) * r * 0.8)), q1 = c.add(f[0].scale(Math.cos(a1) * r * 0.8)).add(f[1].scale(Math.sin(a1) * r * 0.8));
            FxBase.quad(vc, m, n, cam, q0, q1, p1, p0, rim, a, a, a, a);
            FxBase.quad(vc, m, n, cam, p0, p1, q1, q0, rim, a, a, a, a);
            FxBase.quad(vc, m, n, cam, c, c, q1, q0, face, a, a, a, a);
            FxBase.quad(vc, m, n, cam, c, c, q0, q1, face, a, a, a, a);
        }
        if (heads) {   // a sun: a disc and rays
            for (int i = 0; i < 8; i++) {
                double an = i * TAU / 8;
                Vec3 d = f[0].scale(Math.cos(an)).add(f[1].scale(Math.sin(an)));
                GravityFxClient.gseg(m, cam, c.add(d.scale(r * 0.3)), c.add(d.scale(r * 0.62)), r * 0.05, 0.6F * a, 0.2F * a, WHITE);
            }
            GravityFxClient.gglow(m, cam, c, r * 0.28, 0.5F * a, GOLD_GLOW);
        } else {       // a crescent moon
            for (int i = 0; i < 7; i++) {
                double a0 = Math.PI * (0.3 + 1.4 * i / 7), a1 = Math.PI * (0.3 + 1.4 * (i + 1) / 7);
                GravityFxClient.gseg(m, cam, c.add(f[0].scale(Math.cos(a0) * r * 0.45)).add(f[1].scale(Math.sin(a0) * r * 0.45)), c.add(f[0].scale(Math.cos(a1) * r * 0.45)).add(f[1].scale(Math.sin(a1) * r * 0.45)), r * 0.06, 0.6F * a, 0.6F * a, WHITE);
            }
        }
    }

    // ================================================================== 0 the flip over your head
    private static void flip(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Minecraft mc, FxPacket p, double age, float pt) {
        Entity e = mc.level.getEntity((int) p.v.x);
        if (e == null) return;
        if (e == mc.player && mc.options.getCameraType().isFirstPerson()) return;
        Vec3 f = new Vec3(Mth.lerp(pt, e.xo, e.getX()), Mth.lerp(pt, e.yo, e.getY()), Mth.lerp(pt, e.zo, e.getZ()));
        double up = Math.sin(Mth.clamp(age / 14, 0, 1) * Math.PI) * 0.9;
        Vec3 c = f.add(0, e.getBbHeight() + 0.45 + up, 0);
        float a = (float) Mth.clamp((p.life - age) / 6, 0, 1);
        boolean landed = age >= 14, heads = p.a0 > 0.5;
        Vec3 toCam = cam.subtract(c).normalize();
        Vec3 nrm = landed ? toCam : new Vec3(0, Math.cos(age * 1.6), Math.sin(age * 1.6)).normalize();   // tumbling end over end, then shown to you
        coin(vc, m, n, cam, c, nrm, 0.26, !landed || heads, a);
        if (landed) {
            GravityFxClient.gglow(m, cam, c, 0.55, (float) Mth.clamp(1 - (age - 14) / 8, 0, 1) * 0.8F, heads ? GOLD_GLOW : SILVER);
            int luck = Math.min(3, (int) p.r);
            for (int i = 0; i < luck; i++) GravityFxClient.gglow(m, cam, c.add(0, -0.38, 0).add(frame(toCam)[0].scale((i - (luck - 1) / 2.0) * 0.14)), 0.05, 0.9F * a, GREEN);   // luck owed
        }
    }

    // ================================================================== 2 roulette
    private static void wheel(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age) {
        double spin = Math.max(1, p.sweep);
        float a = (float) (Mth.clamp(age / 4, 0, 1) * Mth.clamp((p.life - age) / 8, 0, 1));
        double R = p.r * FxBase.backOut(Mth.clamp(age / 5, 0, 1));
        int pockets = 37, result = Mth.clamp((int) p.a0, 0, 36);
        double k = Mth.clamp(age / spin, 0, 1);
        double turn = (1 - Math.pow(1 - k, 3)) * TAU * 3;   // the wheel slows to a stop
        double wheelRot = turn;
        Vec3 c = p.c;
        for (int i = 0; i < pockets; i++) {
            double a0 = wheelRot + i * TAU / pockets, a1 = wheelRot + (i + 1) * TAU / pockets;
            float[] col = i == 0 ? GREEN : i % 2 == 1 ? RED : BLACK;
            Vec3 o0 = c.add(Math.cos(a0) * R, 0, Math.sin(a0) * R), o1 = c.add(Math.cos(a1) * R, 0, Math.sin(a1) * R);
            Vec3 i0 = c.add(Math.cos(a0) * R * 0.62, 0, Math.sin(a0) * R * 0.62), i1 = c.add(Math.cos(a1) * R * 0.62, 0, Math.sin(a1) * R * 0.62);
            FxBase.quad(vc, m, n, cam, i0, i1, o1, o0, col, 0.95F * a, 0.95F * a, 0.95F * a, 0.95F * a);
            FxBase.quad(vc, m, n, cam, o0, o1, i1, i0, col, 0.95F * a, 0.95F * a, 0.95F * a, 0.95F * a);
            GravityFxClient.gseg(m, cam, i0.add(0, 0.01, 0), o0.add(0, 0.01, 0), 0.015, 0.6F * a, 0.6F * a, GOLD_GLOW);
            if (i == result && age > spin) { float hl = 0.5F * a * (float) (0.6 + 0.4 * Math.sin(age)); FxBase.gquad(m, cam, i0.add(0, 0.02, 0), i1.add(0, 0.02, 0), o1.add(0, 0.02, 0), o0.add(0, 0.02, 0), WHITE, hl, hl, hl, hl); }
        }
        for (int i = 0; i < 24; i++) {   // the felt centre and the gold hub
            double a0 = i * TAU / 24, a1 = (i + 1) * TAU / 24;
            FxBase.quad(vc, m, n, cam, c, c, c.add(Math.cos(a1) * R * 0.62, 0, Math.sin(a1) * R * 0.62), c.add(Math.cos(a0) * R * 0.62, 0, Math.sin(a0) * R * 0.62), FELT, 0.95F * a, 0.95F * a, 0.95F * a, 0.95F * a);
        }
        GravityFxClient.ring(m, cam, c.add(0, 0.02, 0), UP, R, 0.08, GOLD_GLOW, 0.8F * a);
        GravityFxClient.ring(m, cam, c.add(0, 0.02, 0), UP, R * 0.62, 0.05, GOLD_GLOW, 0.7F * a);
        GravityFxClient.gglow(m, cam, c.add(0, 0.2, 0), R * 0.12, 0.8F * a, GOLD_GLOW);
        // the ball: round the rim against the spin, then settling in the pocket
        double target = wheelRot + (result + 0.5) * TAU / pockets;
        double ballAn = age < spin ? target + (1 - k) * (1 - k) * -TAU * 5 : target;
        double ballR = R * (age < spin ? 0.95 - 0.14 * k : 0.81);
        Vec3 ball = c.add(Math.cos(ballAn) * ballR, 0.12 + (age < spin ? 0.06 * Math.abs(Math.sin(age * 2)) * (1 - k) : 0), Math.sin(ballAn) * ballR);
        GravityFxClient.gglow(m, cam, ball, 0.12, a, WHITE);
        GravityFxClient.gglow(m, cam, ball, 0.07, a, WHITE);
    }

    // ================================================================== 3 a chip
    static void chip(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 c, Vec3 nrm, double r, float[] col, float a) {
        Vec3[] f = frame(nrm);
        int seg = 12;
        for (int i = 0; i < seg; i++) {
            double a0 = i * TAU / seg, a1 = (i + 1) * TAU / seg;
            float[] cc = i % 3 == 0 ? CREAM : col;   // the edge stripes
            Vec3 p0 = c.add(f[0].scale(Math.cos(a0) * r)).add(f[1].scale(Math.sin(a0) * r)), p1 = c.add(f[0].scale(Math.cos(a1) * r)).add(f[1].scale(Math.sin(a1) * r));
            FxBase.quad(vc, m, n, cam, c, c, p1, p0, cc, a, a, a, a);
            FxBase.quad(vc, m, n, cam, c, c, p0, p1, cc, a, a, a, a);
        }
        Vec3 q = c.add(nrm.scale(0.005));
        for (int i = 0; i < seg; i++) {
            double a0 = i * TAU / seg, a1 = (i + 1) * TAU / seg;
            FxBase.quad(vc, m, n, cam, q, q, q.add(f[0].scale(Math.cos(a1) * r * 0.6)).add(f[1].scale(Math.sin(a1) * r * 0.6)), q.add(f[0].scale(Math.cos(a0) * r * 0.6)).add(f[1].scale(Math.sin(a0) * r * 0.6)), col, a, a, a, a);
        }
    }

    // ================================================================== 4 / 9 reels
    private static void reels(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 c, FxPacket p, double age, double size, boolean machine) {
        float a = (float) (Mth.clamp(age / 4, 0, 1) * Mth.clamp((p.life - age) / 8, 0, 1));
        int code = (int) p.a0;
        int[] sym = {code % 8, (code / 8) % 8, (code / 64) % 8};
        Vec3 view = c.subtract(cam);
        Vec3 right = new Vec3(-view.z, 0, view.x);
        right = right.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : right.normalize();
        Vec3 toward = right.cross(UP).normalize();
        if (toward.dot(view) > 0) toward = toward.scale(-1);
        double s = size * FxBase.backOut(Mth.clamp(age / 5, 0, 1));
        double win = s * 0.55;
        if (machine) {   // the great machine's body: a gold frame, a crown of lights, a lever
            panel(vc, m, n, cam, c, right, UP, s * 2.3, s * 1.0, RED, a);
            GravityFxClient.gseg(m, cam, c.add(right.scale(-s * 2.3)).add(0, s, 0), c.add(right.scale(s * 2.3)).add(0, s, 0), 0.08, a, a, GOLD_GLOW);
            GravityFxClient.gseg(m, cam, c.add(right.scale(-s * 2.3)).add(0, -s, 0), c.add(right.scale(s * 2.3)).add(0, -s, 0), 0.08, a, a, GOLD_GLOW);
            for (int i = 0; i < 9; i++) GravityFxClient.gglow(m, cam, c.add(right.scale((i - 4) * s * 0.5)).add(0, s * 1.15, 0), 0.12, a * (0.5F + 0.5F * (float) Math.sin(age * 0.8 + i)), i % 2 == 0 ? GOLD_GLOW : RED_GLOW);
            double pull = Mth.clamp(age / 8, 0, 1);
            Vec3 lever = c.add(right.scale(s * 2.5));
            GravityFxClient.gseg(m, cam, lever, lever.add(0, s * (1.2 - 1.6 * Math.sin(pull * Math.PI)), 0), 0.06, a, a, SILVER);
            GravityFxClient.gglow(m, cam, lever.add(0, s * (1.2 - 1.6 * Math.sin(pull * Math.PI)), 0), 0.18, a, RED_GLOW);
        }
        for (int i = 0; i < 3; i++) {
            Vec3 wc = c.add(right.scale((i - 1) * win * 2.15)).add(toward.scale(0.02));
            panel(vc, m, n, cam, wc, right, UP, win, win * 1.25, CREAM, a);
            GravityFxClient.gseg(m, cam, wc.add(right.scale(-win)).add(0, -win * 1.25, 0), wc.add(right.scale(-win)).add(0, win * 1.25, 0), 0.04, a, a, GOLD_GLOW);
            GravityFxClient.gseg(m, cam, wc.add(right.scale(win)).add(0, -win * 1.25, 0), wc.add(right.scale(win)).add(0, win * 1.25, 0), 0.04, a, a, GOLD_GLOW);
            double stop = p.sweep + i * 8;
            Vec3 face = wc.add(toward.scale(0.03));
            if (age < stop) {   // spinning: symbols streaming past, blurred
                double roll = age * 1.4 + i * 1.7;
                for (int k = -1; k <= 1; k++) {
                    double y = ((roll % 1.0) - k) * win * 1.6;
                    if (Math.abs(y) > win) continue;
                    symbol(vc, m, n, cam, face.add(0, -y, 0), right, win * 0.7, ((int) roll + k + 10) % 5, 0.55F * a);
                }
            } else {
                double settle = Mth.clamp((age - stop) / 3, 0, 1);
                symbol(vc, m, n, cam, face.add(0, win * 0.25 * (1 - settle), 0), right, win * 0.75, sym[i], a);
                if (age - stop < 4) GravityFxClient.gglow(m, cam, face, win, (float) (1 - (age - stop) / 4) * 0.5F, WHITE);
            }
        }
        boolean three = sym[0] == sym[1] && sym[1] == sym[2];
        if (three && age > p.sweep + 16) GravityFxClient.gglow(m, cam, c, s * 2.2, 0.35F * a * (0.6F + 0.4F * (float) Math.sin(age * 0.7)), sym[0] == 2 ? RED_GLOW : GOLD_GLOW);
    }

    private static void panel(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 c, Vec3 right, Vec3 up, double hw, double hh, float[] col, float a) {
        Vec3 x = right.scale(hw), y = up.scale(hh);
        FxBase.quad(vc, m, n, cam, c.subtract(x).subtract(y), c.add(x).subtract(y), c.add(x).add(y), c.subtract(x).add(y), col, a, a, a, a);
        FxBase.quad(vc, m, n, cam, c.add(x).subtract(y), c.subtract(x).subtract(y), c.subtract(x).add(y), c.add(x).add(y), col, a, a, a, a);
    }

    /** A reel symbol: 0 cherries, 1 a bell, 2 a seven, 3 a bar, 4 a skull. */
    private static void symbol(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 c, Vec3 right, double s, int which, float a) {
        Vec3 r = right.scale(s), u = UP.scale(s);
        switch (which) {
            case 0 -> {
                GravityFxClient.gglow(m, cam, c.subtract(r.scale(0.3)).subtract(u.scale(0.25)), s * 0.32, a, RED_GLOW);
                GravityFxClient.gglow(m, cam, c.add(r.scale(0.3)).subtract(u.scale(0.3)), s * 0.32, a, RED_GLOW);
                GravityFxClient.gseg(m, cam, c.subtract(r.scale(0.3)).subtract(u.scale(0.1)), c.add(u.scale(0.5)), s * 0.05, a, a, new float[]{0.2F, 0.8F, 0.2F});
                GravityFxClient.gseg(m, cam, c.add(r.scale(0.3)).subtract(u.scale(0.15)), c.add(u.scale(0.5)), s * 0.05, a, a, new float[]{0.2F, 0.8F, 0.2F});
            }
            case 1 -> {
                FxBase.quad(vc, m, n, cam, c.subtract(r.scale(0.45)).subtract(u.scale(0.35)), c.add(r.scale(0.45)).subtract(u.scale(0.35)), c.add(r.scale(0.22)).add(u.scale(0.45)), c.subtract(r.scale(0.22)).add(u.scale(0.45)), GOLD, a, a, a, a);
                GravityFxClient.gglow(m, cam, c.subtract(u.scale(0.45)), s * 0.12, a, GOLD_GLOW);
            }
            case 2 -> {
                GravityFxClient.gseg(m, cam, c.subtract(r.scale(0.4)).add(u.scale(0.5)), c.add(r.scale(0.4)).add(u.scale(0.5)), s * 0.12, a, a, RED_GLOW);
                GravityFxClient.gseg(m, cam, c.add(r.scale(0.4)).add(u.scale(0.5)), c.subtract(r.scale(0.1)).subtract(u.scale(0.55)), s * 0.12, a, a, RED_GLOW);
            }
            case 3 -> {
                FxBase.quad(vc, m, n, cam, c.subtract(r.scale(0.55)).subtract(u.scale(0.2)), c.add(r.scale(0.55)).subtract(u.scale(0.2)), c.add(r.scale(0.55)).add(u.scale(0.2)), c.subtract(r.scale(0.55)).add(u.scale(0.2)), BLACK, a, a, a, a);
                GravityFxClient.gseg(m, cam, c.subtract(r.scale(0.4)), c.add(r.scale(0.4)), s * 0.04, a, a, WHITE);
            }
            default -> {
                GravityFxClient.gglow(m, cam, c.add(u.scale(0.1)), s * 0.45, a, WHITE);
                GravityFxClient.gglow(m, cam, c.add(u.scale(0.1)), s * 0.3, a, WHITE);
                InkFxClient.blob(vc, m, n, cam, c.add(r.scale(-0.17)).add(u.scale(0.15)), s * 0.1, a);
                InkFxClient.blob(vc, m, n, cam, c.add(r.scale(0.17)).add(u.scale(0.15)), s * 0.1, a);
            }
        }
    }

    // ================================================================== 5 dice
    private static void dice(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age) {
        double fly = Math.max(1, p.speed);
        double k = Mth.clamp(age / fly, 0, 1);
        float a = (float) Mth.clamp((p.life - age) / 6, 0, 1);
        int d1 = Mth.clamp((int) p.a0 % 8, 1, 6), d2 = Mth.clamp((int) p.a0 / 8, 1, 6);
        Vec3 side = p.u.subtract(p.c).cross(UP);
        side = side.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : side.normalize();
        for (int i = 0; i < 2; i++) {
            Vec3 off = side.scale(i == 0 ? -0.45 : 0.45);
            Vec3 at = p.c.add(p.u.subtract(p.c).scale(k)).add(off.scale(k)).add(0, Math.abs(Math.sin(k * Math.PI * 2.5)) * 1.0 * (1 - k), 0);
            double tumble = k < 1 ? age * 0.7 + i : 0;
            die(vc, m, n, cam, at.add(0, 0.2, 0), 0.2, tumble, i == 0 ? d1 : d2, a);
        }
    }

    /** A white die of half-size s, turned by {@code rot}, showing {@code top} on its upper face when it lands. */
    private static void die(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 c, double s, double rot, int top, float a) {
        Vec3 x = new Vec3(Math.cos(rot), Math.sin(rot) * 0.5, Math.sin(rot)).normalize(), y = new Vec3(-Math.sin(rot) * 0.4, 1, Math.cos(rot) * 0.4).normalize();
        y = y.subtract(x.scale(y.dot(x))).normalize();
        Vec3 z = x.cross(y);
        Vec3[] axes = {x, y, z};
        int[] faces = {3, top, 2, 7 - top, 4, 5};   // +x, +y, +z, -y, -x, -z
        for (int f = 0; f < 6; f++) {
            Vec3 nrm = axes[f % 3].scale(f < 3 ? 1 : -1);
            Vec3 u = axes[(f + 1) % 3], v = axes[(f + 2) % 3];
            Vec3 fc = c.add(nrm.scale(s));
            Vec3 du = u.scale(s), dv = v.scale(s);
            FxBase.quad(vc, m, n, cam, fc.subtract(du).subtract(dv), fc.add(du).subtract(dv), fc.add(du).add(dv), fc.subtract(du).add(dv), f == 1 ? WHITE : CREAM, a, a, a, a);
            FxBase.quad(vc, m, n, cam, fc.add(du).subtract(dv), fc.subtract(du).subtract(dv), fc.subtract(du).add(dv), fc.add(du).add(dv), CREAM, a, a, a, a);
            int pips = faces[f == 3 ? 3 : f];
            double[][] spots = switch (pips) {
                case 1 -> new double[][]{{0, 0}};
                case 2 -> new double[][]{{-0.5, -0.5}, {0.5, 0.5}};
                case 3 -> new double[][]{{-0.5, -0.5}, {0, 0}, {0.5, 0.5}};
                case 4 -> new double[][]{{-0.5, -0.5}, {0.5, 0.5}, {-0.5, 0.5}, {0.5, -0.5}};
                case 5 -> new double[][]{{-0.5, -0.5}, {0.5, 0.5}, {-0.5, 0.5}, {0.5, -0.5}, {0, 0}};
                default -> new double[][]{{-0.5, -0.5}, {0.5, 0.5}, {-0.5, 0.5}, {0.5, -0.5}, {-0.5, 0}, {0.5, 0}};
            };
            Vec3 pf = fc.add(nrm.scale(0.006));
            for (double[] sp : spots) {
                Vec3 q = pf.add(du.scale(sp[0])).add(dv.scale(sp[1]));
                Vec3 pu = u.scale(s * 0.13), pv = v.scale(s * 0.13);
                FxBase.quad(vc, m, n, cam, q.subtract(pu).subtract(pv), q.add(pu).subtract(pv), q.add(pu).add(pv), q.subtract(pu).add(pv), pips == 1 ? RED : BLACK, a, a, a, a);
            }
        }
    }

    // ================================================================== 6 payout: a fountain of coins
    private static void payout(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age) {
        double k = Mth.clamp(age / p.life, 0, 1);
        float a = (float) Math.min(1, (1 - k) * 2);
        int N = (int) Mth.clamp(8 + p.r * 5, 8, 50);
        for (int i = 0; i < N; i++) {
            double an = FxBase.h2(p.id, i, 1) * TAU, sp = 0.1 + 0.12 * FxBase.h2(p.id, i, 2) * Math.max(1, p.r * 0.4), up = 0.25 + 0.25 * FxBase.h2(p.id, i, 3);
            double t = age;
            Vec3 at = p.c.add(Math.cos(an) * sp * t, up * t - 0.022 * t * t, Math.sin(an) * sp * t);
            coin(vc, m, n, cam, at, axisSpin(age * 0.8 + i), 0.11, FxBase.h2(p.id, i, 4) < 0.85, a);
        }
        GravityFxClient.gglow(m, cam, p.c, p.r * 0.6 * (1 - k) + 0.3, 0.6F * a, GOLD_GLOW);
    }

    // ================================================================== 7 All In: coins orbiting you, felt under your feet
    private static void aura(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Minecraft mc, FxPacket p, double age, double now, float pt) {
        Entity e = mc.level.getEntity((int) p.v.x);
        if (e == null) return;
        Vec3 f = new Vec3(Mth.lerp(pt, e.xo, e.getX()), Mth.lerp(pt, e.yo, e.getY()), Mth.lerp(pt, e.zo, e.getZ()));
        float a = (float) (Mth.clamp(age / 10, 0, 1) * Mth.clamp((p.life - age) / 20, 0, 1));
        for (int i = 0; i < 20; i++) {
            double a0 = i * TAU / 20, a1 = (i + 1) * TAU / 20;
            FxBase.quad(vc, m, n, cam, f.add(0, 0.03, 0), f.add(0, 0.03, 0), f.add(Math.cos(a1) * 1.4, 0.03, Math.sin(a1) * 1.4), f.add(Math.cos(a0) * 1.4, 0.03, Math.sin(a0) * 1.4), FELT, 0.6F * a, 0.6F * a, 0.4F * a, 0.4F * a);
        }
        GravityFxClient.ring(m, cam, f.add(0, 0.04, 0), UP, 1.4, 0.05, GOLD_GLOW, 0.7F * a);
        if (e == mc.player && mc.options.getCameraType().isFirstPerson()) return;
        for (int i = 0; i < 7; i++) {
            double an = now * 0.07 + i * TAU / 7;
            Vec3 c = f.add(Math.cos(an) * 1.0, e.getBbHeight() * 0.55 + 0.25 * Math.sin(now * 0.12 + i), Math.sin(an) * 1.0);
            coin(vc, m, n, cam, c, axisSpin(now * 0.3 + i), 0.13, true, a);
        }
    }

    // ================================================================== 8 Insurance: a shell of coins
    private static void shield(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Minecraft mc, FxPacket p, double age, double now, float pt) {
        Entity e = mc.level.getEntity((int) p.v.x);
        if (e == null) return;
        if (e == mc.player && mc.options.getCameraType().isFirstPerson()) return;
        Vec3 c = new Vec3(Mth.lerp(pt, e.xo, e.getX()), Mth.lerp(pt, e.yo, e.getY()) + e.getBbHeight() * 0.5, Mth.lerp(pt, e.zo, e.getZ()));
        float a = (float) (Mth.clamp(age / 6, 0, 1) * Mth.clamp((p.life - age) / 10, 0, 1));
        for (int i = 0; i < 18; i++) {
            double an = i * TAU / 6 + (i / 6) * 0.5 + now * 0.02, lat = (i / 6 - 1) * 0.6;
            Vec3 d = new Vec3(Math.cos(an) * Math.cos(lat), Math.sin(lat), Math.sin(an) * Math.cos(lat));
            coin(vc, m, n, cam, c.add(d.scale(1.2)), d, 0.2, true, 0.85F * a);
        }
    }

    private static Vec3[] frame(Vec3 axis) {
        Vec3 e1 = Math.abs(axis.y) > 0.9 ? new Vec3(1, 0, 0) : UP;
        e1 = e1.subtract(axis.scale(e1.dot(axis))).normalize();
        return new Vec3[]{e1, axis.cross(e1)};
    }
}
