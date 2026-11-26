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
 * The Meridian Grimoire, drawn: a real little sun. Its own look - not rings in gold:
 * <ul>
 *   <li>0 the Hearth Star: a white-hot core in gold and orange, a corona of flame tongues licking out and turning,
 *       prominences arching off its rim, heat pulsing out of it;</li>
 *   <li>1 Haughty Flare: the star swells and cracks with light, then bursts - a flash, shells of light in three
 *       planes, rays flung out, a burning ring on the ground;</li>
 *   <li>2 the Dayreaver's solar arc: a great crescent of sunlight following the swing, embers shed off it, and (a
 *       cleave) the arc's line left burning on the ground;</li>
 *   <li>3 Noonwave: fronts of shimmering heat rolling out in a cone, scorch lines raked across the ground under them;</li>
 *   <li>4 Lamplight: a little lantern-flame flying - a cage of light round a flame, a tail of embers;</li>
 *   <li>5 Warm Hands: embers spiralling in to the chest, a warmth there, motes rising;</li>
 *   <li>6 Last Coal: banked coals circling your waist, brighter the more the shield has grown;</li>
 *   <li>7 Zenith Unbound: a sun-disc halo behind your head, rays turning, shafts of light falling round you and a
 *       sundial of light on the ground under you, its shadow-hand pointing straight up;</li>
 *   <li>8 Zenith's dawn: a pillar of light and a ring of noon rolling out;</li>
 *   <li>9 a sun-flare where a blow lands.</li>
 * </ul>
 * Fed by FxPacket shape 99 (server/MeridianFx): {@code h} = kind, {@code v.x} = the entity it rides (or -1).
 */
public final class MeridianFxClient {
    private MeridianFxClient() {}

    private static final double TAU = Math.PI * 2;
    private static final Vec3 UP = new Vec3(0, 1, 0);
    static final float[] CORE = {1.0F, 0.98F, 0.88F}, GOLD = {1.0F, 0.78F, 0.22F}, ORANGE = {1.0F, 0.45F, 0.08F}, RED = {0.9F, 0.18F, 0.04F},
            PALE = {1.0F, 0.93F, 0.6F}, EMBERC = {1.0F, 0.55F, 0.15F};

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
        if (k == 0 || k == 6 || k == 7) {   // one of these per mage: a re-send moves or refreshes it (without replaying its fade-in)
            for (L l : LIVE) if ((int) l.p.h == k && (int) l.p.v.x == (int) p.v.x) {
                double age = now - l.born;
                l.p = p;
                l.born = k == 0 ? now : now - Math.min(age, 20);
                return;
            }
        }
        if (LIVE.size() > 300) LIVE.remove(0);
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
                case 0 -> star(m, cam, p.c, p.r, now, 1F, p.id);
                case 1 -> flare(m, cam, p, age, now);
                case 2 -> arc(m, cam, p, age, now);
                case 3 -> noonwave(m, cam, p, age, now);
                case 4 -> lamp(m, cam, p, age, now);
                case 5 -> warm(m, cam, mc, p, age, now, pt);
                case 6 -> coals(m, cam, mc, p, age, now, pt);
                case 7 -> zenith(m, cam, mc, p, age, now, pt);
                case 8 -> dawn(m, cam, p, age, now);
                case 9 -> hitFlare(m, cam, p, age);
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

    private static float env(double age, double life, double in, double out) {
        return (float) (Mth.clamp(age / in, 0, 1) * Mth.clamp((life - age) / out, 0, 1));
    }

    /** A smooth wobble in [-1, 1]. */
    private static double wob(double t, double seed) { return Math.sin(t * 0.37 + seed * 3.1) * 0.6 + Math.sin(t * 0.91 + seed * 1.7) * 0.4; }

    // ------------------------------------------------------------------ 0 the sun
    /** A sun of radius r at c: the layered glow, the corona's flame tongues, prominences arching off the rim. */
    static void star(Matrix4f m, Vec3 cam, Vec3 c, double r, double t, float a, int seed) {
        if (a <= 0.01F) return;
        Vec3 view = c.subtract(cam);
        if (view.lengthSqr() < 1e-6) return;
        Vec3[] f = frame(view.normalize());   // the corona lies in the plane facing you, like a sun's always does
        GravityFxClient.gglow(m, cam, c, r * 2.6, a * 0.25F, ORANGE);
        GravityFxClient.gglow(m, cam, c, r * 1.5, a * 0.6F, GOLD);
        GravityFxClient.gglow(m, cam, c, r * 0.95, a, PALE);
        GravityFxClient.gglow(m, cam, c, r * 0.6, a, CORE);
        // the corona: flame tongues licking out, each one flickering and leaning as the sun turns
        int N = 18;
        for (int i = 0; i < N; i++) {
            double base = i * TAU / N + t * 0.015;
            double len = r * (0.45 + 0.35 * (0.5 + 0.5 * wob(t * 1.4, i + seed)));
            double lean = 0.35 * wob(t, i * 2.3 + seed);
            int K = 6;
            Vec3[] pts = new Vec3[K];
            double[] w = new double[K];
            float[] al = new float[K];
            for (int k = 0; k < K; k++) {
                double u = k / (double) (K - 1);
                double an = base + lean * u * u;
                double rr = r * 0.8 + len * u;
                pts[k] = c.add(f[0].scale(Math.cos(an) * rr)).add(f[1].scale(Math.sin(an) * rr));
                w[k] = r * 0.16 * (1 - u * 0.85);
                al[k] = a * (float) (1 - u) * 0.9F;
            }
            GravityFxClient.gstrip(m, cam, pts, w, al, i % 3 == 0 ? GOLD : ORANGE);
        }
        // two prominences: loops of flame arching off the rim
        for (int j = 0; j < 2; j++) {
            double a0 = j * Math.PI + t * 0.01 + seed, span = 0.55 + 0.15 * wob(t * 0.5, j + seed);
            double hgt = r * (0.6 + 0.25 * (0.5 + 0.5 * wob(t * 0.7, j * 5 + seed)));
            int K = 14;
            Vec3[] pts = new Vec3[K];
            double[] w = new double[K];
            float[] al = new float[K];
            for (int k = 0; k < K; k++) {
                double u = k / (double) (K - 1), an = a0 + span * u, rr = r * 0.9 + hgt * Math.sin(u * Math.PI);
                pts[k] = c.add(f[0].scale(Math.cos(an) * rr)).add(f[1].scale(Math.sin(an) * rr));
                w[k] = r * 0.07;
                al[k] = a * 0.85F;
            }
            GravityFxClient.gstrip(m, cam, pts, w, al, RED);
            for (int k = 0; k < K; k++) w[k] *= 0.4;
            GravityFxClient.gstrip(m, cam, pts, w, al, GOLD);
        }
        // the heat breathing out of it
        double ph = (t * 0.05) % 1.0;
        GravityFxClient.ring(m, cam, c, view.normalize(), r * (1.1 + ph * 1.4), r * 0.12, GOLD, a * 0.5F * (float) (1 - ph));
    }

    // ------------------------------------------------------------------ 1 Haughty Flare
    private static void flare(Matrix4f m, Vec3 cam, FxPacket p, double age, double t) {
        double swell = 20, R = p.r, s0 = Math.max(0.4, p.a1);
        if (age < swell) {   // swelling, cracking with light
            double q = age / swell;
            double r = s0 * (1 + 1.2 * q * q);
            star(m, cam, p.c, r, t, 1F, p.id);
            Vec3 view = p.c.subtract(cam).normalize();
            Vec3[] f = frame(view);
            int cracks = 6 + (int) (q * 10);
            for (int i = 0; i < cracks; i++) {   // light splitting out through it
                double an = i * 2.399 + (int) (t / 2) * 0.7;
                if ((int) (t + i) % 3 == 0) continue;
                Vec3 d = f[0].scale(Math.cos(an)).add(f[1].scale(Math.sin(an)));
                GravityFxClient.gseg(m, cam, p.c.add(d.scale(r * 0.3)), p.c.add(d.scale(r * (1.4 + q))), r * 0.05, (float) q, 0, CORE);
            }
            GravityFxClient.ring(m, cam, BossGround(p), UP, R * (1 - q * 0.2), 0.12, RED, (float) q * 0.6F);
            return;
        }
        double b = age - swell, life = p.life - swell;
        double q = Math.min(1, b / 8.0);
        float a = (float) Mth.clamp(1 - b / life, 0, 1);
        if (b < 4) GravityFxClient.gglow(m, cam, p.c, R * (0.6 + b * 0.35), (float) (1 - b / 4), CORE);   // the flash
        // shells of light in three planes
        Vec3[] axes = {UP, new Vec3(1, 0, 0), new Vec3(0, 0, 1)};
        for (int k = 0; k < 3; k++) GravityFxClient.ring(m, cam, p.c, axes[k], R * FxBase.smooth(q), R * 0.08, k == 0 ? GOLD : PALE, a * 0.8F);
        GravityFxClient.ring(m, cam, p.c, UP, R * 1.2 * FxBase.smooth(q), R * 0.25, ORANGE, a * 0.4F);
        // rays flung out
        for (int i = 0; i < 22; i++) {
            double an = i * 2.399 + p.a1 * 3, el = ((i * 0.618) % 1.0 - 0.5) * 1.6;
            Vec3 d = new Vec3(Math.cos(an) * Math.cos(el), Math.sin(el), Math.sin(an) * Math.cos(el));
            double r0 = R * 0.3 * q, r1 = R * (0.7 + 0.6 * q);
            GravityFxClient.gseg(m, cam, p.c.add(d.scale(r0)), p.c.add(d.scale(r1)), 0.08 + R * 0.015, a, 0, i % 2 == 0 ? GOLD : CORE);
        }
        // a burning ring on the ground, and flames licking up along it
        Vec3 g = BossGround(p);
        double gr = (R + 0.5) * FxBase.smooth(q);
        GravityFxClient.ring(m, cam, g, UP, gr, 0.35, ORANGE, a * 0.7F);
        for (int i = 0; i < 20; i++) {
            double an = i * TAU / 20 + p.a1;
            Vec3 bpt = g.add(Math.cos(an) * gr, 0, Math.sin(an) * gr);
            double h = 0.5 + 0.5 * (0.5 + 0.5 * wob(t * 2, i));
            GravityFxClient.gseg(m, cam, bpt, bpt.add(0, h, 0), 0.14, a * 0.8F, 0, i % 2 == 0 ? ORANGE : GOLD);
        }
    }

    private static Vec3 BossGround(FxPacket p) { return p.u != null && p.u.lengthSqr() > 0 ? p.u.add(0, 0.06, 0) : p.c.add(0, -1, 0); }

    // ------------------------------------------------------------------ 2 the Dayreaver's solar arc
    private static void arc(Matrix4f m, Vec3 cam, FxPacket p, double age, double t) {
        Vec3 d = p.d.normalize(), side = new Vec3(-d.z, 0, d.x);
        Vec3 tiltUp = UP.add(side.scale(p.a0)).normalize();   // a0: the swing's tilt
        double R = p.r, sweep = Math.min(1, age / 4.0);
        float a = env(age, p.life, 0.5, p.life * 0.6);
        int N = 26;
        Vec3[] pts = new Vec3[N];
        double[] w = new double[N];
        float[] al = new float[N];
        double from = -1.75, to = -1.75 + 3.5 * sweep;
        for (int i = 0; i < N; i++) {
            double u = i / (double) (N - 1), an = from + (to - from) * u;
            pts[i] = p.c.add(d.scale(Math.cos(an) * R)).add(side.scale(Math.sin(an) * R)).add(tiltUp.scale(-0.25 * Math.sin(an)));
            double edge = Math.sin(u * Math.PI);
            w[i] = R * 0.16 * (0.2 + 0.8 * edge);
            al[i] = a * (float) (0.2 + 0.8 * u);
        }
        GravityFxClient.gstrip(m, cam, pts, w, al, ORANGE);
        double[] w2 = w.clone(); for (int i = 0; i < N; i++) w2[i] *= 0.55;
        GravityFxClient.gstrip(m, cam, pts, w2, al, GOLD);
        for (int i = 0; i < N; i++) w2[i] *= 0.4;
        GravityFxClient.gstrip(m, cam, pts, w2, al, CORE);
        // embers shed off the arc, flung outward and falling
        for (int i = 0; i < 14; i++) {
            double u = (i * 0.618 + p.a1) % 1.0, an = from + (to - from) * u;
            double fly = Math.max(0, age - 1) * 0.12;
            Vec3 b = p.c.add(d.scale(Math.cos(an) * (R + fly))).add(side.scale(Math.sin(an) * (R + fly))).add(0, -fly * fly * 0.3, 0);
            GravityFxClient.gglow(m, cam, b, 0.09, a * (float) Math.max(0, 1 - age / p.life), i % 2 == 0 ? GOLD : EMBERC);
        }
        // a cleave: the arc's line left burning on the ground
        if (p.a1 > 0 && p.u != null) {
            double gy = p.u.y + 0.05;
            float ga = (float) Mth.clamp((p.life - age) / 10.0, 0, 1) * (float) Mth.clamp(age / 3, 0, 1);
            for (int i = 0; i < 16; i++) {
                double an = -1.6 + 3.2 * i / 15;
                Vec3 g = new Vec3(p.c.x + d.x * Math.cos(an) * R + side.x * Math.sin(an) * R, gy, p.c.z + d.z * Math.cos(an) * R + side.z * Math.sin(an) * R);
                double h = 0.25 + 0.35 * (0.5 + 0.5 * wob(t * 2.5, i));
                GravityFxClient.gseg(m, cam, g, g.add(0, h, 0), 0.12, ga * 0.9F, 0, i % 2 == 0 ? ORANGE : GOLD);
                GravityFxClient.gglow(m, cam, g, 0.25, ga * 0.4F, RED);
            }
        }
    }

    // ------------------------------------------------------------------ 3 Noonwave
    private static void noonwave(Matrix4f m, Vec3 cam, FxPacket p, double age, double t) {
        Vec3 d = p.d.normalize(), side = new Vec3(-d.z, 0, d.x);
        double L = p.r, half = Math.toRadians(35);
        // four fronts of heat rolling out, shimmering
        for (int k = 0; k < 4; k++) {
            double q = (age - k * 2) / 10.0;
            if (q < 0 || q > 1) continue;
            double rr = 0.8 + q * L;
            float a = (float) (Math.sin(q * Math.PI)) * (k == 0 ? 1F : 0.75F);
            int N = 22;
            for (int layer = 0; layer < 3; layer++) {
                double y = 0.2 + layer * 0.65;
                Vec3[] pts = new Vec3[N];
                double[] w = new double[N];
                float[] al = new float[N];
                for (int i = 0; i < N; i++) {
                    double u = i / (double) (N - 1), an = -half + 2 * half * u;
                    double shimmer = 0.12 * Math.sin(u * 18 + t * 0.9 + layer * 2 + k);
                    pts[i] = p.c.add(d.scale(Math.cos(an) * rr)).add(side.scale(Math.sin(an) * rr)).add(0, y - 1.0 + shimmer, 0);
                    w[i] = 0.18 + 0.1 * layer;
                    al[i] = a * (float) Math.sin(u * Math.PI) * (layer == 1 ? 0.9F : 0.6F);
                }
                GravityFxClient.gstrip(m, cam, pts, w, al, layer == 1 ? GOLD : ORANGE);
            }
        }
        // scorch lines raked across the ground under them
        float ga = env(age, p.life, 3, 8);
        for (int i = 0; i < 9; i++) {
            double an = -half + 2 * half * i / 8 + Math.sin(i * 7.7) * 0.05;
            Vec3 dir = d.scale(Math.cos(an)).add(side.scale(Math.sin(an)));
            double reach = Math.min(1, age / 8.0) * L * (0.7 + 0.3 * ((i * 0.37) % 1.0));
            Vec3 a0 = p.c.add(0, -0.95, 0).add(dir.scale(0.8)), a1 = p.c.add(0, -0.95, 0).add(dir.scale(reach));
            GravityFxClient.gseg(m, cam, a0, a1, 0.1, ga * 0.9F, ga * 0.2F, RED);
            GravityFxClient.gseg(m, cam, a0, a1, 0.03, ga, 0, GOLD);
        }
    }

    // ------------------------------------------------------------------ 4 Lamplight
    private static void lamp(Matrix4f m, Vec3 cam, FxPacket p, double age, double t) {
        Vec3 d = p.d.normalize();
        double dist = Math.min(age * p.r, p.a0);
        Vec3 c = p.c.add(d.scale(dist));
        boolean landed = age * p.r >= p.a0;
        if (!landed) {
            GravityFxClient.gglow(m, cam, c, 0.35, 0.8F, ORANGE);
            GravityFxClient.gglow(m, cam, c, 0.16, 1F, CORE);
            Vec3[] f = frame(d);
            double spin = t * 0.4;
            for (int i = 0; i < 4; i++) {   // the lantern's cage of light, turning round the flame
                double an = spin + i * TAU / 4;
                Vec3 o = f[0].scale(Math.cos(an) * 0.22).add(f[1].scale(Math.sin(an) * 0.22));
                GravityFxClient.gseg(m, cam, c.add(o).subtract(d.scale(0.25)), c.add(o).add(d.scale(0.25)), 0.025, 0.9F, 0.9F, GOLD);
            }
            GravityFxClient.ring(m, cam, c.add(d.scale(0.25)), d, 0.22, 0.03, GOLD, 0.9F);
            GravityFxClient.ring(m, cam, c.subtract(d.scale(0.25)), d, 0.22, 0.03, GOLD, 0.9F);
            for (int i = 1; i <= 8; i++) {   // the tail of embers
                Vec3 b = c.subtract(d.scale(i * 0.35)).add(0, Math.sin(t * 0.8 + i) * 0.06 + i * 0.02, 0);
                GravityFxClient.gglow(m, cam, b, 0.1 * (1 - i / 9.0), 0.8F * (1 - i / 9F), i % 2 == 0 ? GOLD : EMBERC);
            }
        } else {   // it lands: the cage bursts and the flame flares
            double b = age - p.a0 / p.r;
            float a = (float) Math.max(0, 1 - b / 6);
            GravityFxClient.gglow(m, cam, c, 0.3 + b * 0.15, a, GOLD);
            for (int i = 0; i < 8; i++) {
                double an = i * TAU / 8;
                Vec3 o = new Vec3(Math.cos(an), 0.5, Math.sin(an)).normalize();
                GravityFxClient.gseg(m, cam, c.add(o.scale(0.1 + b * 0.1)), c.add(o.scale(0.3 + b * 0.18)), 0.04, a, 0, ORANGE);
            }
        }
    }

    // ------------------------------------------------------------------ 5 Warm Hands
    private static void warm(Matrix4f m, Vec3 cam, Minecraft mc, FxPacket p, double age, double t, float pt) {
        Vec3 f = at(mc, p, pt);
        if (f == null) return;
        Vec3 chest = f.add(0, 1.1, 0);
        float a = env(age, p.life, 2, 10);
        for (int i = 0; i < 14; i++) {   // embers drawn in, spiralling
            double q = Mth.clamp((age - i * 0.8) / 12.0, 0, 1);
            if (q <= 0 || q >= 1) continue;
            double an = i * 2.399 + q * 4.0, rr = 2.2 * (1 - q);
            Vec3 b = chest.add(Math.cos(an) * rr, (1 - q) * (i % 3 - 1) * 0.6, Math.sin(an) * rr);
            GravityFxClient.gglow(m, cam, b, 0.12, a, i % 2 == 0 ? GOLD : EMBERC);
            Vec3 b2 = chest.add(Math.cos(an - 0.25) * (rr + 0.25), (1 - q) * (i % 3 - 1) * 0.6, Math.sin(an - 0.25) * (rr + 0.25));
            GravityFxClient.gseg(m, cam, b2, b, 0.04, 0, a * 0.8F, ORANGE);
        }
        if (age > 8) GravityFxClient.gglow(m, cam, chest, 0.6, a * 0.5F, GOLD);   // the warmth, gathered
        for (int i = 0; i < 6; i++) {   // and motes rising off it
            double ph = ((age * 0.04 + i / 6.0) % 1.0);
            Vec3 b = chest.add(Math.cos(i * 2.1) * 0.35, ph * 1.4, Math.sin(i * 2.1) * 0.35);
            GravityFxClient.gglow(m, cam, b, 0.07, a * (float) (1 - ph), PALE);
        }
    }

    // ------------------------------------------------------------------ 6 Last Coal
    private static void coals(Matrix4f m, Vec3 cam, Minecraft mc, FxPacket p, double age, double t, float pt) {
        Vec3 f = at(mc, p, pt);
        if (f == null) return;
        Entity e = mc.level.getEntity((int) p.v.x);
        boolean self = e == mc.player && mc.options.getCameraType().isFirstPerson();
        float a = env(age, p.life, 4, 10);
        float g = 0.35F + 0.65F * Mth.clamp(p.a0, 0, 1);   // how much the shield has grown
        double y = self ? 0.5 : 0.9, R = self ? 1.2 : 0.85;
        for (int i = 0; i < 10; i++) {
            double an = i * TAU / 10 + t * 0.03;
            Vec3 c = f.add(Math.cos(an) * R, y + Math.sin(t * 0.1 + i) * 0.06, Math.sin(an) * R);
            double pulse = 0.5 + 0.5 * Math.sin(t * 0.2 + i * 1.3);
            GravityFxClient.gglow(m, cam, c, 0.16 + 0.08 * g, a * g * (0.6F + 0.4F * (float) pulse), ORANGE);
            GravityFxClient.gglow(m, cam, c, 0.07, a * g, CORE);
            Vec3 c2 = f.add(Math.cos(an + 0.3) * R, y, Math.sin(an + 0.3) * R);
            GravityFxClient.gseg(m, cam, c, c2, 0.05, a * g * 0.6F, 0, RED);   // a trail of heat behind each coal
        }
        GravityFxClient.ring(m, cam, f.add(0, 0.05, 0), UP, R + 0.1, 0.25, RED, a * g * 0.35F);
    }

    // ------------------------------------------------------------------ 7 Zenith Unbound
    private static void zenith(Matrix4f m, Vec3 cam, Minecraft mc, FxPacket p, double age, double t, float pt) {
        Entity e = mc.level.getEntity((int) p.v.x);
        Vec3 f = at(mc, p, pt);
        if (f == null || e == null || e.isInvisible()) return;
        boolean borrowed = p.a0 > 0.5F;
        float[] main = borrowed ? ORANGE : GOLD;
        float a = env(age, p.life, 10, 20);
        boolean self = e == mc.player && mc.options.getCameraType().isFirstPerson();
        float yaw = (e instanceof net.minecraft.world.entity.LivingEntity le ? Mth.rotLerp(pt, le.yBodyRotO, le.yBodyRot) : e.getYRot()) * Mth.DEG_TO_RAD;
        Vec3 fwd = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        if (!self) {   // the sun-disc halo behind the head, rays turning
            Vec3 h = f.add(0, e.getBbHeight() * 0.92, 0).subtract(fwd.scale(0.45));
            Vec3[] fr = frame(fwd);
            GravityFxClient.bring(m, cam, h, fr, 0.75, 0.08, CORE, a);
            GravityFxClient.bring(m, cam, h, fr, 0.95, 0.06, main, a * 0.8F);
            GravityFxClient.gglow(m, cam, h, 0.7, a * 0.35F, main);
            for (int i = 0; i < 16; i++) {
                double an = i * TAU / 16 + t * 0.02;
                double len = i % 2 == 0 ? 0.75 : 0.45;
                Vec3 d = fr[0].scale(Math.cos(an)).add(fr[1].scale(Math.sin(an)));
                GravityFxClient.gseg(m, cam, h.add(d.scale(1.0)), h.add(d.scale(1.0 + len)), 0.05, a, 0, i % 2 == 0 ? main : PALE);
            }
        }
        // shafts of light falling round you
        for (int i = 0; i < 5; i++) {
            double an = i * TAU / 5 + t * 0.012, rr = self ? 2.4 : 1.8;
            Vec3 g = f.add(Math.cos(an) * rr, 0, Math.sin(an) * rr);
            double ph = (t * 0.03 + i * 0.2) % 1.0;
            GravityFxClient.gseg(m, cam, g.add(0, 9, 0), g, 0.18, 0, a * 0.5F * (float) Math.sin(ph * Math.PI), PALE);
        }
        // the sundial of light under your feet, its shadow-hand pointing straight up: noon
        Vec3 g = f.add(0, 0.05, 0);
        double R = self ? 2.6 : 2.0;
        GravityFxClient.ring(m, cam, g, UP, R, 0.08, main, a * 0.8F);
        GravityFxClient.ring(m, cam, g, UP, R * 0.55, 0.05, PALE, a * 0.5F);
        for (int i = 0; i < 12; i++) {
            double an = i * TAU / 12;
            Vec3 o = new Vec3(Math.cos(an), 0, Math.sin(an));
            GravityFxClient.gseg(m, cam, g.add(o.scale(R * 0.82)), g.add(o.scale(R)), i % 3 == 0 ? 0.07 : 0.035, a, a, i % 3 == 0 ? CORE : main);
        }
        GravityFxClient.gseg(m, cam, g, g.add(0, 1.8, 0), 0.05, a * 0.8F, 0, CORE);
    }

    // ------------------------------------------------------------------ 8 Zenith's dawn
    private static void dawn(Matrix4f m, Vec3 cam, FxPacket p, double age, double t) {
        double q = age / p.life;
        float a = (float) (1 - q);
        GravityFxClient.gseg(m, cam, p.c, p.c.add(0, 16, 0), 1.2 * (1 - q * 0.6), a * 0.8F, 0, PALE);
        GravityFxClient.gseg(m, cam, p.c, p.c.add(0, 16, 0), 0.35, a, 0, CORE);
        GravityFxClient.ring(m, cam, p.c.add(0, 0.08, 0), UP, 0.5 + 8 * FxBase.smooth(Math.min(1, q * 1.5)), 0.5, GOLD, a * 0.7F);
        GravityFxClient.ring(m, cam, p.c.add(0, 0.08, 0), UP, 0.3 + 6 * FxBase.smooth(Math.min(1, q * 1.5)), 0.15, CORE, a);
        if (age < 5) GravityFxClient.gglow(m, cam, p.c.add(0, 1, 0), 3 + age, (float) (1 - age / 5), CORE);
    }

    // ------------------------------------------------------------------ 9 a sun-flare where a blow lands
    private static void hitFlare(Matrix4f m, Vec3 cam, FxPacket p, double age) {
        double q = age / p.life;
        float a = (float) (1 - q);
        double r = p.r * (0.4 + 0.6 * q);
        GravityFxClient.gglow(m, cam, p.c, r * 0.6, a, CORE);
        GravityFxClient.gglow(m, cam, p.c, r, a * 0.5F, GOLD);
        Vec3[] f = frame(p.c.subtract(cam).normalize());
        for (int i = 0; i < 8; i++) {
            double an = i * TAU / 8 + p.a1;
            Vec3 d = f[0].scale(Math.cos(an)).add(f[1].scale(Math.sin(an)));
            GravityFxClient.gseg(m, cam, p.c.add(d.scale(r * 0.4)), p.c.add(d.scale(r * (i % 2 == 0 ? 1.6 : 1.1))), r * 0.06, a, 0, i % 2 == 0 ? GOLD : ORANGE);
        }
    }
}
