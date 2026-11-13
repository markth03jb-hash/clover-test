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
 * The Tarot Grimoire, drawn: cards - cream faces in violet borders with gold frames, each of the twenty-two with its
 * own sign and numeral (its own simple drawings, not any real deck's art), turning over in the air before you, upside
 * down when reversed; and what they do: a tower of light struck and falling, stars raining, a scythe's sweep, the
 * rope of the hanged man, beams of judgement, the four suits flying, a sun and a moon, a sword of justice, the red
 * thread of the lovers, and the whole deck fanned round you when the reading is complete.
 * Fed by FxPacket shape 92 (server/TarotFx), {@code h} = kind: 0 card, 1 tower, 2 stars, 3 scythe, 4 hanged, 5 beam,
 * 6 The World, 7 suit bolt, 8 sun / moon, 10 reading complete, 11 justice, 12 lovers, 13 judgement in the sky.
 */
public final class TarotFxClient {
    private TarotFxClient() {}

    private static final double TAU = Math.PI * 2;
    private static final Vec3 UP = new Vec3(0, 1, 0);
    static final float[] CREAM = {0.96F, 0.91F, 0.8F}, VIOLET = {0.3F, 0.12F, 0.5F}, VIOLET_D = {0.16F, 0.05F, 0.28F}, GOLD = {0.9F, 0.68F, 0.2F},
            GOLD_GLOW = {1.0F, 0.85F, 0.45F}, INK = {0.2F, 0.08F, 0.32F}, WHITE = {1, 1, 1}, PALE = {0.8F, 0.75F, 1.0F}, RED = {0.85F, 0.1F, 0.2F};
    static final float[][] SUITS = {{1.0F, 0.55F, 0.15F}, {0.3F, 0.6F, 1.0F}, {0.85F, 0.9F, 1.0F}, {1.0F, 0.82F, 0.3F}};

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
                case 0 -> drawn(vc, m, n, cam, mc, p, age, now, pt);
                case 1 -> tower(vc, m, n, cam, p, age);
                case 2 -> stars(m, cam, mc, p, age, now, pt);
                case 3 -> scythe(vc, m, n, cam, mc, p, age, pt);
                case 4 -> hanged(m, cam, mc, p, age, pt);
                case 5 -> beam(m, cam, p, age);
                case 6 -> world(vc, m, n, cam, mc, p, age, now, pt);
                case 7 -> suit(m, cam, p, age);
                case 8 -> sunMoon(m, cam, p, age);
                case 10 -> complete(vc, m, n, cam, mc, p, age, now, pt);
                case 11 -> justice(vc, m, n, cam, p, age);
                case 12 -> lovers(m, cam, mc, p, age, now, pt);
                case 13 -> skyCard(vc, m, n, cam, p, age, now);
                default -> LIVE.remove(l);
            }
        }
    }

    // ================================================================== a card
    /**
     * A card at c, its face toward {@code face}, {@code w} wide; {@code turn} 0 = face on, 1 = edge on, 2 = back on.
     * Reversed cards are drawn upside down.
     */
    static void card(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 c, Vec3 face, double w, int arcana, boolean reversed, double turn, float a) {
        Vec3 right = UP.cross(face);
        right = right.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : right.normalize();
        Vec3 up = face.cross(right).normalize();
        double sq = Math.cos(turn * Math.PI / 2 * 2);   // the card turning over: its width squeezes to nothing at edge-on
        boolean back = turn > 0.5;
        Vec3 r = right.scale(w * 0.5 * Math.abs(sq)), u = up.scale(w * 0.85);
        Vec3 bl = c.subtract(r).subtract(u), br = c.add(r).subtract(u), tr = c.add(r).add(u), tl = c.subtract(r).add(u);
        FxBase.quad(vc, m, n, cam, bl, br, tr, tl, back ? VIOLET : VIOLET_D, a, a, a, a);   // border / back
        FxBase.quad(vc, m, n, cam, br, bl, tl, tr, VIOLET, a, a, a, a);
        Vec3 off = face.scale(0.004);
        if (back) {   // the back: violet, a gold star in a ring
            CardPen pen = new CardPen(vc, m, n, cam, c.add(off), r.scale(1 / Math.max(1e-3, w * 0.5)), up, w, a, false);
            pen.ring(0, 0, 0.3, GOLD); pen.star(0, 0, 0.22, 8, GOLD);
            GravityFxClient.bring(m, cam, c, new Vec3[]{right, up}, w * 0.38 * Math.abs(sq), 0.02, GOLD_GLOW, 0.5F * a);
            return;
        }
        Vec3 ir = r.scale(0.86), iu = u.scale(0.9);   // the cream face
        FxBase.quad(vc, m, n, cam, c.subtract(ir).subtract(iu).add(off), c.add(ir).subtract(iu).add(off), c.add(ir).add(iu).add(off), c.subtract(ir).add(iu).add(off), CREAM, a, a, a, a);
        GravityFxClient.gseg(m, cam, c.subtract(ir).subtract(iu).add(off), c.add(ir).subtract(iu).add(off), 0.012 * w, a, a, GOLD_GLOW);
        GravityFxClient.gseg(m, cam, c.subtract(ir).add(iu).add(off), c.add(ir).add(iu).add(off), 0.012 * w, a, a, GOLD_GLOW);
        GravityFxClient.gseg(m, cam, c.subtract(ir).subtract(iu).add(off), c.subtract(ir).add(iu).add(off), 0.012 * w, a, a, GOLD_GLOW);
        GravityFxClient.gseg(m, cam, c.add(ir).subtract(iu).add(off), c.add(ir).add(iu).add(off), 0.012 * w, a, a, GOLD_GLOW);
        CardPen pen = new CardPen(vc, m, n, cam, c.add(off.scale(2)), r.scale(1 / Math.max(1e-3, w * 0.5)).scale(reversed ? -1 : 1), up.scale(reversed ? -1 : 1), w, a, true);
        glyph(pen, arcana);
        numeral(pen, arcana);
        pen.line(-0.25, -0.72, 0.25, -0.72, VIOLET);   // where the name would be
    }

    /** Draws onto a card face in its own coordinates: x across (-0.5..0.5), y up (-0.85..0.85), in units of the card's width. */
    private static final class CardPen {
        final VertexConsumer vc; final Matrix4f m; final Matrix3f n; final Vec3 cam, c, right, up; final double w; final float a; final boolean sq;
        CardPen(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 c, Vec3 right, Vec3 up, double w, float a, boolean sq) {
            this.vc = vc; this.m = m; this.n = n; this.cam = cam; this.c = c; this.right = right; this.up = up; this.w = w; this.a = a; this.sq = sq;
        }
        Vec3 at(double x, double y) { return c.add(right.scale(x * w)).add(up.scale(y * w)); }
        void line(double x0, double y0, double x1, double y1, float[] col) {
            Vec3 p0 = at(x0, y0), p1 = at(x1, y1);
            Vec3 ax = p1.subtract(p0);
            Vec3 s = ax.cross(up.cross(right));
            if (s.lengthSqr() < 1e-10) s = right.cross(up.cross(right));
            if (s.lengthSqr() < 1e-10) return;
            s = s.normalize().scale(0.022 * w);
            FxBase.quad(vc, m, n, cam, p0.add(s), p1.add(s), p1.subtract(s), p0.subtract(s), col, a, a, a, a);
            FxBase.quad(vc, m, n, cam, p0.subtract(s), p1.subtract(s), p1.add(s), p0.add(s), col, a, a, a, a);
        }
        void ring(double x, double y, double r, float[] col) { arc(x, y, r, 0, TAU, col); }
        void arc(double x, double y, double r, double a0, double a1, float[] col) {
            int N = 14;
            for (int i = 0; i < N; i++) {
                double q0 = a0 + (a1 - a0) * i / N, q1 = a0 + (a1 - a0) * (i + 1) / N;
                line(x + Math.cos(q0) * r, y + Math.sin(q0) * r, x + Math.cos(q1) * r, y + Math.sin(q1) * r, col);
            }
        }
        void star(double x, double y, double r, int points, float[] col) {
            for (int i = 0; i < points; i++) {
                double an = i * TAU / points;
                line(x, y, x + Math.cos(an) * r * (i % 2 == 0 ? 1 : 0.55), y + Math.sin(an) * r * (i % 2 == 0 ? 1 : 0.55), col);
            }
        }
        void dot(double x, double y, double r, float[] col) { for (int k = 1; k <= 3; k++) ring(x, y, r * k / 3, col); }
    }

    /** Each arcana's sign (simple drawings of my own). */
    private static void glyph(CardPen p, int k) {
        float[] I = INK, G = GOLD;
        switch (k) {
            case 0 -> { p.line(-0.35, -0.3, 0.1, -0.3, I); p.line(0.1, -0.3, 0.1, -0.55, I); p.ring(-0.1, 0.25, 0.08, I); p.line(-0.1, 0.17, -0.05, -0.15, I); p.line(-0.05, -0.15, 0.12, -0.25, I); p.line(-0.08, 0.05, 0.2, 0.2, I); p.dot(0.24, 0.24, 0.05, G); }
            case 1 -> { p.ring(-0.12, 0.35, 0.1, G); p.ring(0.12, 0.35, 0.1, G); p.line(0, 0.15, 0, -0.45, I); p.dot(0, 0.15, 0.04, G); p.line(-0.25, -0.1, 0.25, -0.1, I); }
            case 2 -> { p.line(-0.3, 0.45, -0.3, -0.45, I); p.line(0.3, 0.45, 0.3, -0.45, I); p.arc(0, 0.05, 0.18, 1.0, 5.3, G); p.arc(0.08, 0.05, 0.15, 1.3, 5.0, G); }
            case 3 -> { p.ring(0, 0.15, 0.18, I); p.line(0, -0.03, 0, -0.4, I); p.line(-0.12, -0.22, 0.12, -0.22, I); p.star(0, 0.15, 0.08, 6, G); }
            case 4 -> { p.line(-0.25, -0.3, 0.25, -0.3, I); p.line(-0.25, -0.3, -0.25, 0.1, I); p.line(0.25, -0.3, 0.25, 0.1, I); p.line(-0.25, 0.1, -0.12, 0.3, G); p.line(-0.12, 0.3, 0, 0.12, G); p.line(0, 0.12, 0.12, 0.3, G); p.line(0.12, 0.3, 0.25, 0.1, G); }
            case 5 -> { p.line(0, 0.5, 0, -0.45, I); p.line(-0.12, 0.38, 0.12, 0.38, G); p.line(-0.18, 0.22, 0.18, 0.22, G); p.line(-0.24, 0.06, 0.24, 0.06, G); }
            case 6 -> { p.ring(-0.1, 0.05, 0.2, I); p.ring(0.1, 0.05, 0.2, I); p.dot(0, 0.05, 0.05, RED); }
            case 7 -> { p.line(-0.25, 0.25, 0.25, 0.25, I); p.line(-0.25, 0.25, -0.25, -0.15, I); p.line(0.25, 0.25, 0.25, -0.15, I); p.line(-0.25, -0.15, 0.25, -0.15, I); p.ring(-0.22, -0.3, 0.12, G); p.ring(0.22, -0.3, 0.12, G); }
            case 8 -> { p.ring(-0.1, 0.42, 0.08, G); p.ring(0.1, 0.42, 0.08, G); p.ring(0, -0.05, 0.25, I); p.star(0, -0.05, 0.18, 12, I); }
            case 9 -> { p.line(0.15, 0.45, 0.15, -0.5, I); p.line(-0.18, 0.15, -0.05, 0.28, G); p.line(-0.05, 0.28, 0.08, 0.15, G); p.line(0.08, 0.15, -0.05, 0.0, G); p.line(-0.05, 0.0, -0.18, 0.15, G); p.dot(-0.05, 0.15, 0.04, G); }
            case 10 -> { p.ring(0, 0.05, 0.3, I); p.ring(0, 0.05, 0.12, G); for (int i = 0; i < 8; i++) { double an = i * TAU / 8; p.line(Math.cos(an) * 0.12, 0.05 + Math.sin(an) * 0.12, Math.cos(an) * 0.3, 0.05 + Math.sin(an) * 0.3, I); } }
            case 11 -> { p.line(0, 0.45, 0, -0.45, I); p.line(-0.3, 0.3, 0.3, 0.3, G); p.line(-0.3, 0.3, -0.38, 0.0, I); p.line(-0.3, 0.3, -0.22, 0.0, I); p.line(-0.38, 0.0, -0.22, 0.0, I); p.line(0.3, 0.3, 0.22, 0.0, I); p.line(0.3, 0.3, 0.38, 0.0, I); p.line(0.22, 0.0, 0.38, 0.0, I); }
            case 12 -> { p.line(-0.3, 0.5, 0.3, 0.5, I); p.line(0, 0.5, 0, 0.3, G); p.line(0, 0.3, 0, -0.15, I); p.ring(0, -0.25, 0.09, I); p.line(-0.15, 0.05, 0.15, 0.05, I); }
            case 13 -> { p.line(0.15, 0.5, -0.1, -0.5, I); p.arc(0.0, 0.35, 0.32, 0.2, 2.6, G); }
            case 14 -> { p.arc(-0.2, 0.25, 0.1, Math.PI, TAU, I); p.arc(0.2, -0.15, 0.1, Math.PI, TAU, I); p.arc(0, 0.05, 0.25, 0.6, 2.3, G); }
            case 15 -> { p.ring(0, -0.05, 0.18, I); p.line(-0.15, 0.1, -0.28, 0.4, I); p.line(0.15, 0.1, 0.28, 0.4, I); p.dot(-0.07, -0.02, 0.03, RED); p.dot(0.07, -0.02, 0.03, RED); }
            case 16 -> { p.line(-0.15, -0.5, -0.12, 0.3, I); p.line(0.15, -0.5, 0.12, 0.3, I); p.line(-0.12, 0.3, 0.12, 0.3, I); p.line(0.3, 0.55, 0.05, 0.3, G); p.line(0.05, 0.3, 0.2, 0.2, G); p.line(0.2, 0.2, -0.05, -0.05, G); }
            case 17 -> { p.star(0, 0.2, 0.28, 16, G); p.star(-0.25, -0.3, 0.08, 8, I); p.star(0.25, -0.3, 0.08, 8, I); }
            case 18 -> { p.arc(0, 0.15, 0.25, 1.0, 5.3, I); p.arc(0.1, 0.15, 0.2, 1.3, 5.0, I); p.dot(-0.2, -0.3, 0.03, G); p.dot(0, -0.38, 0.03, G); p.dot(0.2, -0.3, 0.03, G); }
            case 19 -> { p.ring(0, 0.1, 0.18, G); p.dot(0, 0.1, 0.12, G); for (int i = 0; i < 12; i++) { double an = i * TAU / 12; p.line(Math.cos(an) * 0.24, 0.1 + Math.sin(an) * 0.24, Math.cos(an) * 0.36, 0.1 + Math.sin(an) * 0.36, I); } }
            case 20 -> { p.line(-0.3, 0.35, 0.2, 0.05, I); p.line(-0.3, 0.25, 0.2, 0.05, I); p.ring(0.25, 0.03, 0.07, G); for (int i = 0; i < 4; i++) p.line(0.32, 0.03 + (i - 1.5) * 0.08, 0.45, (i - 1.5) * 0.18, G); }
            default -> { p.arc(0, 0.05, 0.32, 0, TAU, G); p.ring(0, 0.05, 0.36, I); p.line(0, 0.25, 0, -0.15, I); p.ring(0, 0.3, 0.06, I); }
        }
    }

    /** The card's number at its top, in numerals made of strokes. */
    private static void numeral(CardPen p, int k) {
        String s = roman(k);
        double y0 = 0.62, h = 0.1, step = 0.09;
        double x = -(s.length() - 1) * step / 2;
        for (char ch : s.toCharArray()) {
            switch (ch) {
                case 'I' -> p.line(x, y0 - h / 2, x, y0 + h / 2, VIOLET);
                case 'V' -> { p.line(x - 0.035, y0 + h / 2, x, y0 - h / 2, VIOLET); p.line(x, y0 - h / 2, x + 0.035, y0 + h / 2, VIOLET); }
                case 'X' -> { p.line(x - 0.035, y0 + h / 2, x + 0.035, y0 - h / 2, VIOLET); p.line(x - 0.035, y0 - h / 2, x + 0.035, y0 + h / 2, VIOLET); }
                default -> p.ring(x, y0, h / 2, VIOLET);
            }
            x += step;
        }
    }

    private static String roman(int k) {
        if (k == 0) return "0";
        String[] u = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX"};
        return "X".repeat(k / 10) + u[k % 10];
    }

    // ================================================================== 0 a card drawn before you
    private static void drawn(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Minecraft mc, FxPacket p, double age, double now, float pt) {
        Entity e = mc.level.getEntity((int) p.v.x);
        if (e == null) return;
        boolean first = e == mc.player && mc.options.getCameraType().isFirstPerson();
        double yaw = Math.toRadians(Mth.rotLerp(pt, e.yRotO, e.getYRot()));
        Vec3 fwd = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw)), right = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
        Vec3 f = new Vec3(Mth.lerp(pt, e.xo, e.getX()), Mth.lerp(pt, e.yo, e.getY()), Mth.lerp(pt, e.zo, e.getZ()));
        double rise = FxBase.backOut(Mth.clamp(age / 8, 0, 1));
        Vec3 c = f.add(0, e.getBbHeight() * (first ? 0.95 : 0.85) + 0.4 * rise + 0.05 * Math.sin(now * 0.15), 0).add(fwd.scale(first ? 1.6 : 1.1)).add(right.scale(-p.r2 * 0.75));
        double turn = Mth.clamp(1 - (age - 2) / 6, 0, 1) * 2;   // it comes in face down and turns over
        float a = (float) (Mth.clamp(age / 3, 0, 1) * Mth.clamp((p.life - age) / 8, 0, 1));
        Vec3 face = cam.subtract(c).normalize();
        card(vc, m, n, cam, c, face, first ? 0.42 : 0.62, Mth.clamp((int) p.a0, 0, 21), p.r > 0.5, turn, a);
        if (turn < 0.05) GravityFxClient.gglow(m, cam, c, 0.9, (float) Mth.clamp(1 - (age - 8) / 10, 0, 1) * 0.5F * a, p.r > 0.5 ? PALE : GOLD_GLOW);
    }

    // ================================================================== 1 the tower
    private static void tower(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age) {
        double strike = Math.max(1, p.sweep);
        double rise = Mth.clamp(age / (strike * 0.7), 0, 1), H = 7 * FxBase.smooth(rise), w = p.r * 0.4;
        float a = (float) Mth.clamp((p.life - age) / 8, 0, 1);
        Vec3 c = p.c;
        if (age < strike + 2) {
            for (int s = 0; s < 4; s++) {   // the tower: four edges of light and its crown
                double an = s * TAU / 4 + 0.785;
                Vec3 b = c.add(Math.cos(an) * w, 0, Math.sin(an) * w);
                GravityFxClient.gseg(m, cam, b, b.add(0, H, 0), 0.08, a, a, GOLD_GLOW);
            }
            GravityFxClient.ring(m, cam, c.add(0, H, 0), UP, w * 1.5, 0.08, GOLD_GLOW, a);
            GravityFxClient.gseg(m, cam, c, c.add(0, H, 0), w, 0.25F * a, 0.25F * a, PALE);
        }
        if (age >= strike - 2 && age < strike + 3) {   // struck
            Vec3 top = c.add(0, 7, 0), prev = c.add(0, 22, 0);
            for (int i = 1; i <= 6; i++) {
                Vec3 q = prev.add(0, -(22 - 7) / 6.0, 0).add((FxBase.h2(p.id, i, 1) - 0.5) * 1.4, 0, (FxBase.h2(p.id, i, 2) - 0.5) * 1.4);
                if (i == 6) q = top;
                GravityFxClient.gseg(m, cam, prev, q, 0.12, 1, 1, WHITE);
                prev = q;
            }
            GravityFxClient.gglow(m, cam, top, 2.5, 0.8F, WHITE);
        }
        if (age >= strike) {   // falling: its stones coming down
            double q = age - strike;
            for (int i = 0; i < 18; i++) {
                double y = 7 * FxBase.h2(p.id, i, 3) - 0.04 * q * q - 0.1 * q;
                if (y < 0) continue;
                Vec3 at = c.add((FxBase.h2(p.id, i, 4) - 0.5) * w * 3 * (1 + q * 0.1), y, (FxBase.h2(p.id, i, 5) - 0.5) * w * 3 * (1 + q * 0.1));
                VoidFxClient.hole(vc, m, n, cam, at, 0.18, a);
                GravityFxClient.gglow(m, cam, at, 0.25, 0.4F * a, GOLD_GLOW);
            }
            GravityFxClient.ring(m, cam, c.add(0, 0.05, 0), UP, 1 + q * 0.35, 0.2, GOLD_GLOW, a * 0.7F);
        }
    }

    // ================================================================== 2 the star
    private static void stars(Matrix4f m, Vec3 cam, Minecraft mc, FxPacket p, double age, double now, float pt) {
        Entity e = mc.level.getEntity((int) p.v.x);
        if (e == null) return;
        Vec3 f = new Vec3(Mth.lerp(pt, e.xo, e.getX()), Mth.lerp(pt, e.yo, e.getY()), Mth.lerp(pt, e.zo, e.getZ()));
        float a = (float) Mth.clamp((p.life - age) / 10, 0, 1);
        for (int i = 0; i < 14; i++) {
            double ph = (now / (16 + 6 * FxBase.h2(p.id, i, 6)) + FxBase.h2(p.id, i, 7)) % 1.0;
            double an = FxBase.h2(p.id, i, 8) * TAU, rr = p.r * FxBase.h2(p.id, i, 9);
            Vec3 q = f.add(Math.cos(an) * rr, 7 * (1 - ph), Math.sin(an) * rr);
            GravityFxClient.gseg(m, cam, q, q.add(0.3, 0.9, 0.1), 0.06, 0.9F * a, 0, GOLD_GLOW);
            GravityFxClient.gglow(m, cam, q, 0.12, a, WHITE);
        }
        GravityFxClient.ring(m, cam, f.add(0, 0.05, 0), UP, p.r, 0.1, GOLD_GLOW, 0.4F * a);
    }

    // ================================================================== 3 the scythe
    private static void scythe(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Minecraft mc, FxPacket p, double age, float pt) {
        Entity e = mc.level.getEntity((int) p.v.x);
        if (e == null) return;
        Vec3 f = new Vec3(Mth.lerp(pt, e.xo, e.getX()), Mth.lerp(pt, e.yo, e.getY()), Mth.lerp(pt, e.zo, e.getZ()));
        double k = Mth.clamp(age / (p.life * 0.6), 0, 1);
        float a = (float) Mth.clamp((p.life - age) / 5, 0, 1);
        double yaw = Math.toRadians(e.getYRot()) + Math.PI / 2;
        double an = yaw - k * TAU;
        Vec3 c = f.add(0, 1.0, 0);
        int N = 14;
        Vec3[] pts = new Vec3[N];
        double[] w = new double[N];
        float[] al = new float[N];
        for (int i = 0; i < N; i++) {   // the blade's sweep, pale and ghostly
            double u = i / (double) (N - 1), aa = an + u * 1.6;
            pts[i] = c.add(Math.cos(aa) * p.r * (0.6 + 0.4 * u), 0.2 * Math.sin(u * Math.PI), Math.sin(aa) * p.r * (0.6 + 0.4 * u));
            w[i] = 0.35 * Math.sin(u * Math.PI);
            al[i] = 0.75F * a;
        }
        GravityFxClient.gstrip(m, cam, pts, w, al, PALE);
        for (int i = 0; i < N; i++) w[i] *= 0.25;
        GravityFxClient.gstrip(m, cam, pts, w, al, WHITE);
        Vec3 haft = c.add(Math.cos(an + 1.6) * p.r * 0.95, 0, Math.sin(an + 1.6) * p.r * 0.95);
        GravityFxClient.gseg(m, cam, c, haft, 0.05, 0.7F * a, 0.7F * a, VIOLET);
    }

    // ================================================================== 4 the hanged man's rope
    private static void hanged(Matrix4f m, Vec3 cam, Minecraft mc, FxPacket p, double age, float pt) {
        Entity e = mc.level.getEntity((int) p.v.x);
        if (e == null) return;
        Vec3 f = new Vec3(Mth.lerp(pt, e.xo, e.getX()), Mth.lerp(pt, e.yo, e.getY()), Mth.lerp(pt, e.zo, e.getZ()));
        float a = (float) (Mth.clamp(age / 3, 0, 1) * Mth.clamp((p.life - age) / 5, 0, 1));
        Vec3 top = f.add(0, e.getBbHeight() + 3.5, 0);
        GravityFxClient.gseg(m, cam, top, f.add(0, 0.1, 0), 0.04, a, a, GOLD_GLOW);   // the rope comes down past it, to its feet
        GravityFxClient.ring(m, cam, f.add(0, 0.12, 0), UP, Math.max(0.25, e.getBbWidth() * 0.5), 0.05, GOLD_GLOW, a);
        GravityFxClient.gseg(m, cam, top.add(-0.8, 0, 0), top.add(0.8, 0, 0), 0.06, a, a, GOLD_GLOW);
        GravityFxClient.gglow(m, cam, f.add(0, e.getBbHeight() * 0.5, 0), e.getBbHeight() * 0.6, 0.15F * a, PALE);
    }

    // ================================================================== 5 a beam of judgement
    private static void beam(Matrix4f m, Vec3 cam, FxPacket p, double age) {
        float a = (float) (1 - age / p.life);
        double w = 0.7 * (1 - age / p.life) + 0.15;
        GravityFxClient.gseg(m, cam, p.c, p.c.add(0, 24, 0), w, 0.5F * a, 0.1F * a, GOLD_GLOW);
        GravityFxClient.gseg(m, cam, p.c, p.c.add(0, 24, 0), w * 0.3, a, 0.3F * a, WHITE);
        GravityFxClient.ring(m, cam, p.c.add(0, 0.05, 0), UP, 0.6 + age * 0.12, 0.12, GOLD_GLOW, a);
    }

    // ================================================================== 6 The World: cards circling you
    private static void world(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Minecraft mc, FxPacket p, double age, double now, float pt) {
        Entity e = mc.level.getEntity((int) p.v.x);
        if (e == null) return;
        Vec3 f = new Vec3(Mth.lerp(pt, e.xo, e.getX()), Mth.lerp(pt, e.yo, e.getY()), Mth.lerp(pt, e.zo, e.getZ()));
        float a = (float) (Mth.clamp(age / 10, 0, 1) * Mth.clamp((p.life - age) / 20, 0, 1));
        GravityFxClient.ring(m, cam, f.add(0, 0.05, 0), UP, 1.2, 0.08, GOLD_GLOW, 0.6F * a);
        if (e == mc.player && mc.options.getCameraType().isFirstPerson()) return;
        for (int i = 0; i < 5; i++) {
            double an = now * 0.05 + i * TAU / 5;
            Vec3 c = f.add(Math.cos(an) * 1.1, e.getBbHeight() * 0.6 + 0.15 * Math.sin(now * 0.1 + i), Math.sin(an) * 1.1);
            card(vc, m, n, cam, c, new Vec3(Math.cos(an), 0, Math.sin(an)), 0.3, (i * 5) % 22, false, 0, a);
        }
    }

    // ================================================================== 7 a suit bolt
    private static void suit(Matrix4f m, Vec3 cam, FxPacket p, double age) {
        double k = Mth.clamp(age / Math.max(1, p.speed), 0, 1);
        float[] col = SUITS[Mth.clamp((int) p.a0, 0, 3)];
        if (k >= 1) { GravityFxClient.gglow(m, cam, p.u, 0.6 * (1 + age - p.speed), (float) Mth.clamp(1 - (age - p.speed) / 4, 0, 1), col); return; }
        Vec3 at = p.c.add(p.u.subtract(p.c).scale(k));
        GravityFxClient.gglow(m, cam, at, 0.28, 0.9F, col);
        GravityFxClient.gglow(m, cam, at, 0.12, 1F, WHITE);
        GravityFxClient.gseg(m, cam, at, at.subtract(p.u.subtract(p.c).normalize().scale(1.2)), 0.12, 0.6F, 0, col);
    }

    // ================================================================== 8 sun / moon
    private static void sunMoon(Matrix4f m, Vec3 cam, FxPacket p, double age) {
        double k = Mth.clamp(age / p.life, 0, 1);
        float a = (float) (1 - k);
        boolean moon = p.a0 > 0.5;
        float[] col = moon ? PALE : GOLD_GLOW;
        GravityFxClient.ring(m, cam, p.c.add(0, -0.9, 0), UP, p.r * (0.3 + 0.7 * k), 0.25, col, 0.8F * a);
        Vec3 view = p.c.subtract(cam);
        if (view.lengthSqr() < 1e-6) return;
        Vec3 c = p.c.add(0, 2.2, 0);
        if (moon) {
            GravityFxClient.gglow(m, cam, c, 0.9, 0.6F * a, PALE);
            GravityFxClient.gglow(m, cam, c.add(0.35, 0.15, 0), 0.75, 0.4F * a, new float[]{0.1F, 0.1F, 0.25F});
        } else {
            GravityFxClient.gglow(m, cam, c, 1.0, 0.8F * a, GOLD_GLOW);
            for (int i = 0; i < 12; i++) {
                double an = i * TAU / 12 + age * 0.05;
                GravityFxClient.gseg(m, cam, c.add(Math.cos(an) * 1.1, Math.sin(an) * 1.1, 0), c.add(Math.cos(an) * (1.6 + k), Math.sin(an) * (1.6 + k), 0), 0.08, 0.8F * a, 0, GOLD_GLOW);
            }
        }
    }

    // ================================================================== 10 the reading complete: the deck fanned round you
    private static void complete(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Minecraft mc, FxPacket p, double age, double now, float pt) {
        Entity e = mc.level.getEntity((int) p.v.x);
        if (e == null) return;
        Vec3 f = new Vec3(Mth.lerp(pt, e.xo, e.getX()), Mth.lerp(pt, e.yo, e.getY()), Mth.lerp(pt, e.zo, e.getZ()));
        double k = Mth.clamp(age / p.life, 0, 1);
        float a = (float) Math.min(1, (1 - k) * 2.5);
        double R = 1.2 + 1.6 * Math.sin(Math.min(1, k * 2) * Math.PI / 2);
        for (int i = 0; i < 22; i++) {
            double an = i * TAU / 22 + now * 0.03;
            Vec3 c = f.add(Math.cos(an) * R, e.getBbHeight() * 0.6 + 0.4 * Math.sin(an * 2 + now * 0.1), Math.sin(an) * R);
            card(vc, m, n, cam, c, new Vec3(Math.cos(an), 0, Math.sin(an)), 0.36, i, false, k < 0.3 ? 2 - k / 0.15 : 0, a);
        }
        GravityFxClient.gglow(m, cam, f.add(0, 1, 0), 2.5 * (1 - k), 0.5F * a, GOLD_GLOW);
    }

    // ================================================================== 11 the sword of justice
    private static void justice(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age) {
        double fall = Math.max(1, p.sweep);
        double k = Mth.clamp(age / fall, 0, 1);
        float a = (float) Mth.clamp((p.life - age) / 4, 0, 1);
        Vec3 tip = p.c.add(0, 12 * (1 - k * k), 0);
        TreasureFxClient.drawWeapon(vc, m, n, cam, tip, new Vec3(0, -1, 0), 0, 2.6, a);
        GravityFxClient.gseg(m, cam, tip, tip.add(0, 4, 0), 0.3, 0.4F * a, 0, GOLD_GLOW);
        if (age >= fall) GravityFxClient.gglow(m, cam, p.c, 1.5 * (1 + (age - fall) / 3), (float) (1 - (age - fall) / 10) * 0.9F, WHITE);
    }

    // ================================================================== 12 the lovers' thread
    private static void lovers(Matrix4f m, Vec3 cam, Minecraft mc, FxPacket p, double age, double now, float pt) {
        Entity e1 = mc.level.getEntity((int) p.v.x), e2 = mc.level.getEntity((int) p.v.y);
        if (e1 == null || e2 == null) return;
        Vec3 a1 = new Vec3(Mth.lerp(pt, e1.xo, e1.getX()), Mth.lerp(pt, e1.yo, e1.getY()) + e1.getBbHeight() * 0.6, Mth.lerp(pt, e1.zo, e1.getZ()));
        Vec3 a2 = new Vec3(Mth.lerp(pt, e2.xo, e2.getX()), Mth.lerp(pt, e2.yo, e2.getY()) + e2.getBbHeight() * 0.6, Mth.lerp(pt, e2.zo, e2.getZ()));
        float a = (float) Mth.clamp((p.life - age) / 8, 0, 1);
        int N = 16;
        Vec3[] pts = new Vec3[N];
        double[] w = new double[N];
        float[] al = new float[N];
        for (int i = 0; i < N; i++) {
            double u = i / (double) (N - 1);
            pts[i] = a1.add(a2.subtract(a1).scale(u)).add(0, Math.sin(u * Math.PI) * 0.6 + 0.08 * Math.sin(u * 12 - now * 0.5), 0);
            w[i] = 0.035; al[i] = a;
        }
        GravityFxClient.gstrip(m, cam, pts, w, al, RED);
        GravityFxClient.gglow(m, cam, pts[N / 2], 0.25 + 0.05 * Math.sin(now * 0.4), a, RED);
    }

    // ================================================================== 13 Judgement: the great card in the sky
    private static void skyCard(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age, double now) {
        float a = (float) (Mth.clamp(age / 6, 0, 1) * Mth.clamp((p.life - age) / 10, 0, 1));
        Vec3 c = p.c.add(0, 11, 0);
        Vec3 face = cam.subtract(c);
        face = new Vec3(face.x, 0, face.z).lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : new Vec3(face.x, 0, face.z).normalize();
        card(vc, m, n, cam, c, face, 3.2, 20, false, Mth.clamp(1 - age / 8, 0, 1) * 2, a);
        GravityFxClient.gglow(m, cam, c, 5, 0.3F * a, GOLD_GLOW);
        for (int i = 0; i < 10; i++) {   // light pouring down from it
            double an = i * TAU / 10 + now * 0.01;
            Vec3 g = p.c.add(Math.cos(an) * p.r * 0.7, 0, Math.sin(an) * p.r * 0.7);
            GravityFxClient.gseg(m, cam, c, g, 0.15, 0.2F * a, 0.05F * a, GOLD_GLOW);
        }
        GravityFxClient.ring(m, cam, p.c.add(0, 0.05, 0), UP, p.r, 0.2, GOLD_GLOW, 0.5F * a);
    }
}
