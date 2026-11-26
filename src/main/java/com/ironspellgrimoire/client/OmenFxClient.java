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
 * The Grimoire of Ill Omen, drawn: black ink, a cold blue sheen, bone-pale light. Its own look:
 * <ul>
 *   <li>0 a footprint of black ink, rippling now and then; 1 a print trodden on: the ink splashes up;</li>
 *   <li>2 the Laden Token in flight - a black coin, spinning, a pale glint; 3 the token waiting on the ground, turning
 *       slowly, an ill light under it; 4 the token taken: it cracks and the ink bursts out;</li>
 *   <li>5 Needle Rain: long black needles falling, sticking in the ground round their mark, fading;</li>
 *   <li>6 Domino: a black line snapping from one pursuer to the next, a pale spark running down it;</li>
 *   <li>7 Stillness: the air round you turned to glass - a faint shell of pale facets; 8 a blow on it: cracks spread;</li>
 *   <li>9 Karma's Ledger: a great ledger page opens over the one who owes, the debt writes itself in lines of pale
 *       ink, then the page slams down on it;</li>
 *   <li>10 The Law Itself: a great sigil turning in the sky over you - rings, spokes and marks; 11 a shaft of bad
 *       luck falling from it on a pursuer;</li>
 *   <li>12 the path begun: ink spreading out from under your feet;</li>
 *   <li>13 a calamity's sign: a cracked sigil opening on the ground, its marks turning, a shaft from far above.</li>
 * </ul>
 * Fed by FxPacket shape 101 (server/OmenFx): {@code h} = kind, {@code v.x} = the entity it rides (or -1).
 */
public final class OmenFxClient {
    private OmenFxClient() {}

    private static final double TAU = Math.PI * 2;
    private static final Vec3 UP = new Vec3(0, 1, 0);
    static final float[] INK = {0.01F, 0.01F, 0.03F}, BLUE = {0.25F, 0.45F, 1.0F}, PALE = {0.82F, 0.88F, 1.0F}, DEEP = {0.08F, 0.12F, 0.45F},
            PAGE = {0.86F, 0.84F, 0.78F}, GLINT = {1.0F, 1.0F, 1.0F};

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
        if (k == 4) LIVE.removeIf(l -> (int) l.p.h == 3 && l.p.c.distanceToSqr(p.c) < 1.0);   // the token taken: it's gone from the ground
        if (k == 7 || k == 10) for (L l : LIVE) if ((int) l.p.h == k && (int) l.p.v.x == (int) p.v.x) { double age = now - l.born; l.p = p; l.born = now - Math.min(age, 10); return; }
        if (LIVE.size() > 500) LIVE.remove(0);
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
                case 0 -> print(vc, m, n, cam, p, age, now);
                case 1 -> splash(vc, m, n, cam, p, age);
                case 2 -> tokenFlight(vc, m, n, cam, p, age, now);
                case 3 -> tokenWaiting(vc, m, n, cam, p, age, now);
                case 4 -> tokenBurst(vc, m, n, cam, p, age);
                case 5 -> needles(vc, m, n, cam, p, age);
                case 6 -> domino(vc, m, n, cam, p, age);
                case 7 -> glass(m, cam, mc, p, age, now, pt);
                case 8 -> cracks(m, cam, mc, p, age, pt);
                case 9 -> ledger(vc, m, n, cam, p, age, now);
                case 10 -> law(vc, m, n, cam, mc, p, age, now, pt);
                case 11 -> shaft(m, cam, p, age);
                case 12 -> inkSpread(vc, m, n, cam, p, age);
                case 13 -> sign(vc, m, n, cam, p, age, now);
                default -> LIVE.remove(i);
            }
        }
    }

    // ------------------------------------------------------------------ helpers
    private static double hash(double x) { double s = Math.sin(x * 127.1 + 311.7) * 43758.5453; return s - Math.floor(s); }

    private static Vec3 at(Minecraft mc, FxPacket p, float pt) {
        Entity e = mc.level.getEntity((int) p.v.x);
        return e == null ? null : new Vec3(Mth.lerp(pt, e.xo, e.getX()), Mth.lerp(pt, e.yo, e.getY()), Mth.lerp(pt, e.zo, e.getZ()));
    }

    private static Vec3[] frame(Vec3 d) {
        Vec3 a = Math.abs(d.y) > 0.9 ? new Vec3(1, 0, 0) : UP;
        Vec3 x = d.cross(a).normalize();
        return new Vec3[]{x, x.cross(d).normalize()};
    }

    /** A solid filled polygon (both faces) - ink on the ground, a page, a coin. */
    static void fan(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 c, Vec3[] ring, float[] col, float aIn, float aOut) {
        for (int i = 0; i < ring.length; i++) {
            Vec3 p0 = ring[i], p1 = ring[(i + 1) % ring.length];
            FxBase.quad(vc, m, n, cam, c, c, p1, p0, col, aIn, aIn, aOut, aOut);
            FxBase.quad(vc, m, n, cam, p0, p1, c, c, col, aOut, aOut, aIn, aIn);
        }
    }

    /** A solid flat blob on the ground at c: an ellipse (rx, rz) turned by yaw, edges ragged by seed. */
    static void blob(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 c, double rx, double rz, double yaw, double seed, float[] col, float a) {
        int N = 14;
        Vec3[] ring = new Vec3[N];
        double cs = Math.cos(yaw), sn = Math.sin(yaw);
        for (int i = 0; i < N; i++) {
            double an = i * TAU / N, k = 0.82 + 0.3 * hash(seed + i);
            double x = Math.cos(an) * rx * k, z = Math.sin(an) * rz * k;
            ring[i] = c.add(x * cs - z * sn, 0, x * sn + z * cs);
        }
        fan(vc, m, n, cam, c, ring, col, a, a * 0.85F);
    }

    static void sseg(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 a, Vec3 b, double w, float aa, float ab, float[] col) {
        VectorFxClient.sseg(vc, m, n, cam, a, b, w, aa, ab, col);
    }

    // ------------------------------------------------------------------ 0 a footprint, 1 trodden, 12 the path begun
    private static void print(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age, double t) {
        float a = (float) (Mth.clamp(age / 3, 0, 1) * Mth.clamp((p.life - age) / 30, 0, 1));
        double yaw = p.a0, cs = Math.cos(yaw), sn = Math.sin(yaw);
        Vec3 g = p.c.add(0, 0.03, 0);
        Vec3 fwd = new Vec3(-sn, 0, cs);
        blob(vc, m, n, cam, g.add(fwd.scale(0.1)), 0.13, 0.2, yaw, p.a1, INK, a * 0.92F);    // the sole
        blob(vc, m, n, cam, g.subtract(fwd.scale(0.18)), 0.1, 0.11, yaw, p.a1 + 5, INK, a * 0.92F);   // the heel
        double ph = ((t * 0.02 + p.a1) % 1.0);
        if (ph < 0.3) GravityFxClient.ring(m, cam, g, UP, 0.2 + ph * 1.3, 0.03, BLUE, a * (float) (1 - ph / 0.3) * 0.7F);   // a ripple, now and then
    }

    private static void splash(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age) {
        double q = age / p.life;
        float a = (float) (1 - q);
        GravityFxClient.ring(m, cam, p.c.add(0, 0.05, 0), UP, 0.2 + q * 1.2, 0.06, PALE, a);
        for (int i = 0; i < 8; i++) {   // drops of ink thrown up, falling back
            double an = i * TAU / 8 + p.a1, sp = 0.6 + hash(p.a1 + i) * 0.5;
            double x = Math.cos(an) * sp * q, z = Math.sin(an) * sp * q, y = 1.2 * q - 1.6 * q * q;
            Vec3 d = p.c.add(x, 0.05 + Math.max(0, y), z);
            sseg(vc, m, n, cam, d, d.add(0, 0.12, 0), 0.05, a, a, INK);
        }
    }

    private static void inkSpread(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age) {
        double q = Math.min(1, age / 8);
        float a = (float) Mth.clamp((p.life - age) / 6, 0, 1);
        for (int i = 0; i < 7; i++) {   // tendrils of ink running out from under you
            double an = i * TAU / 7 + p.a1, len = 1.6 * q * (0.7 + 0.5 * hash(i + p.a1));
            Vec3 prev = p.c.add(0, 0.03, 0);
            for (int k = 1; k <= 4; k++) {
                double u = k / 4.0;
                Vec3 nx = p.c.add(Math.cos(an + Math.sin(u * 3 + i) * 0.3) * len * u, 0.03, Math.sin(an + Math.sin(u * 3 + i) * 0.3) * len * u);
                sseg(vc, m, n, cam, prev, nx, 0.12 * (1 - u * 0.7), a * 0.9F, a * 0.9F, INK);
                prev = nx;
            }
        }
        blob(vc, m, n, cam, p.c.add(0, 0.03, 0), 0.6 * q, 0.6 * q, 0, p.a1, INK, a * 0.85F);
        GravityFxClient.ring(m, cam, p.c.add(0, 0.04, 0), UP, 0.6 * q + 0.2, 0.05, BLUE, a * 0.6F);
    }

    // ------------------------------------------------------------------ 2-4 the Laden Token
    /** The coin: a black disc with a pale rim and a cracked eye on it, face turned by spin about the axis 'axis'. */
    static void coin(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 c, double spin, double r, float a) {
        Vec3 face = new Vec3(Math.cos(spin), 0, Math.sin(spin));
        Vec3[] f = frame(face);
        int N = 16;
        Vec3[] ring = new Vec3[N];
        for (int i = 0; i < N; i++) { double an = i * TAU / N; ring[i] = c.add(f[0].scale(Math.cos(an) * r)).add(f[1].scale(Math.sin(an) * r)); }
        fan(vc, m, n, cam, c, ring, INK, a, a);
        GravityFxClient.bring(m, cam, c, f, r, r * 0.12, PALE, a * 0.9F);
        GravityFxClient.bring(m, cam, c, f, r * 0.35, r * 0.08, BLUE, a);   // the eye
        double gl = (spin % TAU) / TAU;
        if (gl > 0.45 && gl < 0.55) GravityFxClient.gglow(m, cam, c.add(f[1].scale(r * 0.6)), r * 0.8, a, GLINT);   // a glint as it turns
    }

    private static void tokenFlight(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age, double t) {
        double q = Math.min(1, age / Math.max(1, p.life - 2));
        Vec3 pos = p.c.add(p.u.subtract(p.c).scale(q)).add(0, Math.sin(q * Math.PI) * 1.2, 0);
        coin(vc, m, n, cam, pos, t * 0.9, 0.22, 1F);
        for (int i = 1; i <= 5; i++) {
            double qq = Math.max(0, q - i * 0.04);
            Vec3 b = p.c.add(p.u.subtract(p.c).scale(qq)).add(0, Math.sin(qq * Math.PI) * 1.2, 0);
            GravityFxClient.gglow(m, cam, b, 0.08, 0.6F * (1 - i / 6F), BLUE);
        }
    }

    private static void tokenWaiting(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age, double t) {
        float a = (float) Mth.clamp(age / 4, 0, 1);
        Vec3 c = p.c.add(0, 0.35 + Math.sin(t * 0.08) * 0.08, 0);
        coin(vc, m, n, cam, c, t * 0.06, 0.24, a);
        GravityFxClient.ring(m, cam, p.c.add(0, 0.04, 0), UP, 0.5 + Math.sin(t * 0.1) * 0.06, 0.08, DEEP, a * 0.8F);   // an ill light under it
        GravityFxClient.gglow(m, cam, p.c.add(0, 0.1, 0), 0.4, a * 0.3F, BLUE);
    }

    private static void tokenBurst(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age) {
        double q = age / p.life;
        float a = (float) (1 - q);
        for (int i = 0; i < 2; i++) {   // the coin cracks in two
            Vec3 d = new Vec3(i == 0 ? 1 : -1, 1, 0).normalize();
            Vec3 c = p.c.add(0, 0.4, 0).add(d.scale(q * 1.2)).add(0, -q * q * 1.5, 0);
            sseg(vc, m, n, cam, c.add(0, -0.12, 0), c.add(0, 0.12, 0), 0.12, a, a, INK);
        }
        GravityFxClient.gglow(m, cam, p.c.add(0, 0.4, 0), 0.6 * (1 - q) + 0.2, a, PALE);
        splash(vc, m, n, cam, p, age);
    }

    // ------------------------------------------------------------------ 5 Needle Rain
    private static void needles(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age) {
        for (int i = 0; i < 7; i++) {
            double t0 = hash(p.a1 + i) * 6, fall = (age - t0) / 4.0;
            if (fall < 0) continue;
            double ox = (hash(p.a1 + i * 3) - 0.5) * 1.6, oz = (hash(p.a1 + i * 5) - 0.5) * 1.6;
            Vec3 ground = p.c.add(ox, 0, oz);
            Vec3 dir = new Vec3(-0.15, -1, 0.05).normalize();
            double len = 0.9;
            Vec3 tip;
            float a;
            if (fall < 1) { tip = ground.subtract(dir.scale(5 * (1 - fall))); a = 1F; }   // falling
            else { tip = ground.add(dir.scale(0.25)); a = (float) Mth.clamp(1 - (age - t0 - 4) / 14, 0, 1); }   // stuck in the ground, fading
            Vec3 tail = tip.subtract(dir.scale(len));
            sseg(vc, m, n, cam, tail, tip, 0.035, a, a, INK);
            GravityFxClient.gseg(m, cam, tail, tip, 0.012, a * 0.8F, a, PALE);
            if (fall >= 1 && fall < 1.6) GravityFxClient.ring(m, cam, ground.add(0, 0.04, 0), UP, 0.1 + (fall - 1) * 0.5, 0.03, PALE, (float) (1.6 - fall));
        }
    }

    // ------------------------------------------------------------------ 6 Domino
    private static void domino(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age) {
        double q = Math.min(1, age / 3);
        float a = (float) Mth.clamp((p.life - age) / 5, 0, 1);
        Vec3 ab = p.u.subtract(p.c);
        Vec3[] f = frame(ab.normalize());
        Vec3 prev = p.c;
        int N = 8;
        for (int i = 1; i <= N; i++) {   // a black line, snapping across, kinked
            double u = i / (double) N * q;
            Vec3 nx = p.c.add(ab.scale(u)).add(i < N ? f[0].scale((hash(p.a1 + i) - 0.5) * 0.4) : Vec3.ZERO);
            sseg(vc, m, n, cam, prev, nx, 0.06, a, a, INK);
            GravityFxClient.gseg(m, cam, prev, nx, 0.02, a * 0.7F, a * 0.7F, BLUE);
            prev = nx;
        }
        double s = (age / 5.0) % 1.0;   // a pale spark running down it
        GravityFxClient.gglow(m, cam, p.c.add(ab.scale(s * q)), 0.2, a, PALE);
    }

    // ------------------------------------------------------------------ 7 Stillness, 8 cracks in it
    private static void glass(Matrix4f m, Vec3 cam, Minecraft mc, FxPacket p, double age, double t, float pt) {
        Vec3 f = at(mc, p, pt);
        if (f == null) return;
        Entity e = mc.level.getEntity((int) p.v.x);
        boolean self = e == mc.player && mc.options.getCameraType().isFirstPerson();
        float a = (float) (Mth.clamp(age / 5, 0, 1) * Mth.clamp((p.life - age) / 6, 0, 1)) * (self ? 0.35F : 0.8F);
        Vec3 c = f.add(0, 1.0, 0);
        double R = 1.4;
        for (int lat = -2; lat <= 2; lat++) {   // facets: a faint lattice of pale lines on a shell
            double y = lat * 0.4, rr = Math.sqrt(Math.max(0, R * R - y * y));
            GravityFxClient.ring(m, cam, c.add(0, y, 0), UP, rr, 0.02, PALE, a * 0.6F);
        }
        for (int i = 0; i < 8; i++) {
            double an = i * TAU / 8 + t * 0.004;
            Vec3 prev = null;
            for (int k = 0; k <= 8; k++) {
                double el = -Math.PI / 2 + Math.PI * k / 8;
                Vec3 q = c.add(Math.cos(an) * Math.cos(el) * R, Math.sin(el) * R, Math.sin(an) * Math.cos(el) * R);
                if (prev != null) GravityFxClient.gseg(m, cam, prev, q, 0.015, a * 0.5F, a * 0.5F, PALE);
                prev = q;
            }
        }
        double gl = (t * 0.03) % 1.0;   // a glint sliding over it
        GravityFxClient.gglow(m, cam, c.add(Math.cos(gl * TAU) * R, 0.6, Math.sin(gl * TAU) * R), 0.25, a, GLINT);
    }

    private static void cracks(Matrix4f m, Vec3 cam, Minecraft mc, FxPacket p, double age, float pt) {
        Vec3 f = at(mc, p, pt);
        if (f == null) return;
        float a = (float) (1 - age / p.life);
        Vec3 c = f.add(0, 1.0, 0), hit = c.add(p.d.normalize().scale(1.4));
        double grow = Math.min(1, age / 4);
        for (int i = 0; i < 7; i++) {   // cracks running out over the glass from where it was struck
            Vec3 prev = hit;
            double an = i * TAU / 7 + p.a1;
            for (int k = 1; k <= 4; k++) {
                Vec3 off = new Vec3(Math.cos(an + (hash(i * 9 + k + p.a1) - 0.5)) , Math.sin(an + (hash(i * 7 + k) - 0.5)), Math.sin(an * 1.3)).normalize().scale(0.25 * k * grow);
                Vec3 nx = c.add(hit.add(off).subtract(c).normalize().scale(1.4));
                GravityFxClient.gseg(m, cam, prev, nx, 0.025, a, a, k < 3 ? GLINT : PALE);
                prev = nx;
            }
        }
        GravityFxClient.gglow(m, cam, hit, 0.4 * (1 - age / p.life) + 0.1, a, PALE);
    }

    // ------------------------------------------------------------------ 9 Karma's Ledger
    private static void ledger(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age, double t) {
        double write = 10, slam = 3;
        Vec3 view = p.c.subtract(cam);
        Vec3 side = new Vec3(-view.z, 0, view.x).normalize();
        double hgt = age < write ? 3.2 : 3.2 * Math.max(0, 1 - (age - write) / slam);
        Vec3 c = p.c.add(0, hgt, 0);
        double open = Math.min(1, age / 6);
        float a = (float) Mth.clamp((p.life - age) / 6, 0, 1);
        if (hgt > 0.05) {
            double W = 1.1 * open, H = 1.4;
            Vec3 tilt = UP.add(view.normalize().scale(-0.35)).normalize();
            Vec3 tl = c.add(side.scale(-W)).add(tilt.scale(H / 2)), tr = c.add(side.scale(W)).add(tilt.scale(H / 2));
            Vec3 br = c.add(side.scale(W)).add(tilt.scale(-H / 2)), bl = c.add(side.scale(-W)).add(tilt.scale(-H / 2));
            FxBase.quad(vc, m, n, cam, tl, tr, br, bl, PAGE, a, a, a, a);
            FxBase.quad(vc, m, n, cam, bl, br, tr, tl, PAGE, a, a, a, a);
            sseg(vc, m, n, cam, c.add(tilt.scale(H / 2)), c.add(tilt.scale(-H / 2)), 0.03, a, a, INK);   // the spine
            int lines = 7;
            double written = Mth.clamp(age / write, 0, 1) * lines;
            for (int i = 0; i < lines; i++) {   // the debt, writing itself in
                double fill = Mth.clamp(written - i, 0, 1);
                if (fill <= 0) break;
                double y = H / 2 - 0.2 - i * (H - 0.35) / (lines - 1);
                for (int pageSide = -1; pageSide <= 1; pageSide += 2) {
                    Vec3 l0 = c.add(side.scale(pageSide * 0.12)).add(tilt.scale(y)), l1 = l0.add(side.scale(pageSide * (W - 0.22) * fill));
                    GravityFxClient.gseg(m, cam, l0.add(view.normalize().scale(-0.01)), l1.add(view.normalize().scale(-0.01)), 0.025, a, a, i % 3 == 2 ? BLUE : DEEP);
                }
            }
            GravityFxClient.gglow(m, cam, c, 1.6, a * 0.25F, BLUE);
        }
        if (age >= write + slam - 0.5) {   // the page lands
            double b = age - write - slam;
            float ba = (float) Mth.clamp(1 - b / 10, 0, 1);
            GravityFxClient.ring(m, cam, p.c.add(0, 0.06, 0), UP, 0.5 + b * 0.25, 0.25, BLUE, ba);
            GravityFxClient.ring(m, cam, p.c.add(0, 0.06, 0), UP, 0.3 + b * 0.18, 0.08, PALE, ba);
            if (b < 3) GravityFxClient.gglow(m, cam, p.c.add(0, 0.5, 0), 1.5, (float) (1 - b / 3), PALE);
        }
    }

    // ------------------------------------------------------------------ 10 The Law Itself, 11 a shaft from it
    private static void law(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Minecraft mc, FxPacket p, double age, double t, float pt) {
        Vec3 f = at(mc, p, pt);
        if (f == null) return;
        float a = (float) (Mth.clamp(age / 10, 0, 1) * Mth.clamp((p.life - age) / 15, 0, 1));
        Vec3 c = f.add(0, 9, 0);
        sigil(vc, m, n, cam, c, 5.5, t, a, true);
    }

    /** The sigil: rings, spokes, and marks round its edge, turning (the inner part against the outer). */
    static void sigil(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 c, double R, double t, float a, boolean sky) {
        GravityFxClient.ring(m, cam, c, UP, R, 0.12, BLUE, a);
        GravityFxClient.ring(m, cam, c, UP, R * 0.86, 0.05, PALE, a * 0.8F);
        GravityFxClient.ring(m, cam, c, UP, R * 0.45, 0.08, BLUE, a * 0.9F);
        double outer = t * 0.01, inner = -t * 0.025;
        for (int i = 0; i < 12; i++) {   // the marks: little crooked runes round the rim
            double an = outer + i * TAU / 12;
            Vec3 o = new Vec3(Math.cos(an), 0, Math.sin(an)), tg = new Vec3(-o.z, 0, o.x);
            Vec3 b = c.add(o.scale(R * 0.93));
            double k = hash(i + 3.3);
            GravityFxClient.gseg(m, cam, b.subtract(tg.scale(0.25)), b.add(tg.scale(0.25)).add(o.scale(k * 0.2)), 0.04, a, a, PALE);
            GravityFxClient.gseg(m, cam, b, b.subtract(o.scale(0.3)).add(tg.scale((k - 0.5) * 0.3)), 0.04, a, a, PALE);
        }
        for (int i = 0; i < 6; i++) {   // the spokes and the star in the middle
            double an = inner + i * TAU / 6;
            Vec3 o = new Vec3(Math.cos(an), 0, Math.sin(an));
            GravityFxClient.gseg(m, cam, c.add(o.scale(R * 0.45)), c.add(o.scale(R * 0.86)), 0.05, a * 0.8F, a * 0.8F, BLUE);
            double an2 = inner + (i + 2) * TAU / 6;
            GravityFxClient.gseg(m, cam, c.add(o.scale(R * 0.45)), c.add(new Vec3(Math.cos(an2), 0, Math.sin(an2)).scale(R * 0.45)), 0.04, a, a, PALE);
        }
        if (sky) GravityFxClient.gglow(m, cam, c, R * 0.6, a * 0.2F, DEEP);
    }

    private static void shaft(Matrix4f m, Vec3 cam, FxPacket p, double age) {
        double q = age / p.life;
        float a = (float) (1 - q);
        Vec3 top = p.u, bot = p.c;
        double drop = Math.min(1, age / 2);
        Vec3 end = top.add(bot.subtract(top).scale(drop));
        GravityFxClient.gseg(m, cam, top, end, 0.6, a * 0.5F, a * 0.8F, DEEP);
        GravityFxClient.gseg(m, cam, top, end, 0.15, a, a, PALE);
        if (drop >= 1) GravityFxClient.ring(m, cam, bot.add(0, 0.06, 0), UP, 0.4 + q * 1.6, 0.12, BLUE, a);
    }

    // ------------------------------------------------------------------ 13 a calamity's sign
    private static void sign(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age, double t) {
        double open = FxBase.smooth(Math.min(1, age / 5));
        float a = (float) Mth.clamp((p.life - age) / 8, 0, 1);
        double R = p.r * open;
        Vec3 g = p.c.add(0, 0.05, 0);
        blob(vc, m, n, cam, g, R * 0.95, R * 0.95, 0, p.a1, INK, a * 0.55F);
        sigil(vc, m, n, cam, g.add(0, 0.02, 0), R, t * 2.5, a, false);
        for (int i = 0; i < 9; i++) {   // cracks running out past its rim
            double an = p.a1 + i * TAU / 9 + (hash(i + p.a1) - 0.5) * 0.4;
            Vec3 prev = g.add(Math.cos(an) * R * 0.95, 0.03, Math.sin(an) * R * 0.95);
            for (int k = 1; k <= 3; k++) {
                double rr = R * (0.95 + 0.25 * k * open), aj = an + (hash(i * 5 + k + p.a1) - 0.5) * 0.35;
                Vec3 nx = g.add(Math.cos(aj) * rr, 0.03, Math.sin(aj) * rr);
                sseg(vc, m, n, cam, prev, nx, 0.06, a, a, INK);
                GravityFxClient.gseg(m, cam, prev, nx, 0.02, a, a, BLUE);
                prev = nx;
            }
        }
        if (age < 10) {   // the shaft of ill light from far above
            float sa = (float) (1 - age / 10);
            GravityFxClient.gseg(m, cam, g.add(0, 24, 0), g, 1.0 * p.r / 3.4, sa * 0.5F, sa * 0.9F, DEEP);
            GravityFxClient.gseg(m, cam, g.add(0, 24, 0), g, 0.25 * p.r / 3.4, sa, sa, PALE);
        }
    }
}
