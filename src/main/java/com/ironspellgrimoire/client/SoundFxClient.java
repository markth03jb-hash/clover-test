package com.ironspellgrimoire.client;

import com.ironspellgrimoire.network.FxPacket;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * The Sound Grimoire, drawn: sound made visible as pale sea-green light - wavefronts rolling out, rings closing in,
 * a lance threaded with rings, an echo of you made of stacked rings, a shell that ripples when you're struck.
 * <p>Fed by FxPacket shape 84 (server/SoundFx), {@code h} = kind:
 * 0 pulse (c, d, r, a0 = cone half-angle), 1 shockwave (c, r, a0 = 1 closing in), 2 lance (c to u, r width),
 * 3 echo (c, r height, a1 yaw), 4 barrier (v.x entity, r), 5 silence field (c, r), 6 pillar (c, r height),
 * 7 Resonance rings (v.x entity, a0 count), 8 Crescendo (v.x entity, a0 tier; life 1 = only the new tier),
 * 9 Final Chord (c, r, speed = rise ticks), 10 clap (c, d facing, r).
 */
public final class SoundFxClient {
    private SoundFxClient() {}

    private static final double TAU = Math.PI * 2;
    private static final Vec3 UP = new Vec3(0, 1, 0);
    static final float[] SEA = {0.3F, 1.0F, 0.78F}, PALE = {0.8F, 1.0F, 0.93F}, CORE = {1.0F, 1.0F, 1.0F}, DEEP = {0.08F, 0.5F, 0.45F};

    private static final class L {
        final FxPacket p;
        final double born;
        L(FxPacket p, double born) { this.p = p; this.born = born; }
    }

    private static final List<L> LIVE = new ArrayList<>();

    public static void spawn(FxPacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        int k = (int) p.h;
        if (k == 8 && p.life <= 1) {   // Crescendo grew louder: only the tier changes
            for (L l : LIVE) if ((int) l.p.h == 8 && l.p.v.x == p.v.x) l.p.a0 = p.a0;
            return;
        }
        if (k == 7) LIVE.removeIf(l -> (int) l.p.h == 7 && l.p.v.x == p.v.x);   // one set of rings per foe
        if (k == 7 && p.a0 <= 0) return;
        if (LIVE.size() > 300) LIVE.remove(0);
        LIVE.add(new L(p, mc.level.getGameTime()));
    }

    static void clear() { LIVE.clear(); }

    static void draw(Matrix4f m, Vec3 cam, double now, float pt) {
        if (LIVE.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { LIVE.clear(); return; }
        List<L> live = new ArrayList<>(LIVE);
        for (L l : live) {
            double age = now - l.born;
            if (age > l.p.life) { LIVE.remove(l); continue; }
            FxPacket p = l.p;
            switch ((int) p.h) {
                case 0 -> pulse(m, cam, p, age);
                case 1 -> shock(m, cam, p, age);
                case 2 -> lance(m, cam, p, age, now);
                case 3 -> echo(m, cam, p, age, now);
                case 4 -> barrier(m, cam, mc, p, age, now, pt);
                case 5 -> silence(m, cam, p, age, now);
                case 6 -> pillar(m, cam, p, age, now);
                case 7 -> ringing(m, cam, mc, p, age, now, pt);
                case 8 -> loud(m, cam, mc, p, age, now, pt);
                case 9 -> chord(m, cam, p, age, now);
                case 10 -> clap(m, cam, p, age);
                default -> LIVE.remove(l);
            }
        }
    }

    // ================================================================== 0 pulse: wavefronts rolling out in a cone
    private static void pulse(Matrix4f m, Vec3 cam, FxPacket p, double age) {
        Vec3 d = p.d.normalize();
        double k = Mth.clamp(age / p.life, 0, 1), half = Math.max(0.1, p.a0);
        for (int w = 0; w < 3; w++) {
            double kk = k - w * 0.12;
            if (kk <= 0) continue;
            double e = 1 - Math.pow(1 - kk, 2);
            double R = 0.6 + p.r * e;
            float a = (float) ((1 - kk) * (w == 0 ? 1 : 0.55));
            Vec3 c = p.c.add(d.scale(R * Math.cos(half)));
            double rr = R * Math.sin(half);
            ring(m, cam, c, d, rr, 0.1 + 0.25 * (1 - kk), SEA, 0.75F * a);
            ring(m, cam, c, d, rr, 0.03, CORE, 0.7F * a);
        }
    }

    // ================================================================== 1 shockwave
    private static void shock(Matrix4f m, Vec3 cam, FxPacket p, double age) {
        double k = Mth.clamp(age / p.life, 0, 1);
        double e = 1 - Math.pow(1 - k, 2.4);
        boolean in = p.a0 > 0.5;
        for (int w = 0; w < 2; w++) {
            double kk = Math.max(0, e - w * 0.15);
            double r = in ? p.r * (1 - kk) + 0.25 : p.r * kk + 0.15;
            float a = (float) (1 - k) * (w == 0 ? 1F : 0.5F);
            Vec3 view = p.c.subtract(cam);
            if (view.lengthSqr() > 1e-6) {
                Vec3[] f = frame(view.normalize());
                GravityFxClient.bring(m, cam, p.c, f, r, 0.06 + 0.2 * (1 - k), SEA, 0.8F * a);
                GravityFxClient.bring(m, cam, p.c, f, r, 0.025, CORE, 0.7F * a);
            }
            ring(m, cam, p.c, UP, r, 0.12, PALE, 0.5F * a);
        }
    }

    // ================================================================== 2 lance: a beam threaded with rings
    private static void lance(Matrix4f m, Vec3 cam, FxPacket p, double age, double now) {
        Vec3 ax = p.u.subtract(p.c);
        double L = ax.length();
        if (L < 1e-3) return;
        Vec3 d = ax.scale(1 / L);
        double k = Mth.clamp(age / p.life, 0, 1);
        float a = (float) (age < 2 ? 1 : 1 - k);
        double reach = Math.min(1, age / 2.0);
        Vec3 tip = p.c.add(d.scale(L * reach));
        gseg(m, cam, p.c, tip, p.r * 0.25, a, a, CORE);
        gseg(m, cam, p.c, tip, p.r, 0.5F * a, 0.5F * a, SEA);
        Vec3[] f = frame(d);
        for (int s = 0; s < 2; s++) {   // two strands twisting round it
            int N = Math.max(4, (int) (L * 3 * reach));
            Vec3[] pts = new Vec3[N];
            double[] w = new double[N];
            float[] al = new float[N];
            for (int j = 0; j < N; j++) {
                double u = j / (double) (N - 1), x = u * L * reach, an = x * 2.2 - now * 0.9 + s * Math.PI;
                pts[j] = p.c.add(d.scale(x)).add(f[0].scale(Math.cos(an) * p.r * 0.9)).add(f[1].scale(Math.sin(an) * p.r * 0.9));
                w[j] = 0.035;
                al[j] = 0.7F * a;
            }
            gstrip(m, cam, pts, w, al, PALE);
        }
        for (int i = 0; i < (int) (L / 2.2); i++) {   // rings running down it
            double x = ((i * 2.2 + age * 1.6) % L);
            if (x > L * reach) continue;
            ring(m, cam, p.c.add(d.scale(x)), d, p.r * 1.3, 0.08, SEA, 0.6F * a);
        }
    }

    // ================================================================== 3 echo: you, in stacked rings of sound
    private static void echo(Matrix4f m, Vec3 cam, FxPacket p, double age, double now) {
        double H = Math.max(1, p.r);
        float a = env(age, p.life, 2, 3);
        int N = 11;
        for (int i = 0; i < N; i++) {
            double u = i / (double) (N - 1), y = H * u;
            double rr = u > 0.78 ? 0.26 : u > 0.72 ? 0.12 : u > 0.38 ? 0.36 : 0.22;   // head, neck, body, legs
            float fl = (float) (0.6 + 0.4 * Math.sin(now * 1.3 + i * 1.7));
            ring(m, cam, p.c.add(0, y, 0), UP, rr, 0.04, SEA, 0.7F * a * fl);
        }
        gglow(m, cam, p.c.add(0, H * 0.55, 0), H * 0.45, 0.25F * a, SEA);
        if (age > p.life - 3) gglow(m, cam, p.c.add(0, H * 0.5, 0), H * 0.9, 0.7F * (float) ((p.life - age) / 3), PALE);
    }

    // ================================================================== 4 barrier: a shell of rings that ripple
    private static void barrier(Matrix4f m, Vec3 cam, Minecraft mc, FxPacket p, double age, double now, float pt) {
        Entity e = mc.level.getEntity((int) p.v.x);
        if (e == null) return;
        Vec3 c = new Vec3(Mth.lerp(pt, e.xo, e.getX()), Mth.lerp(pt, e.yo, e.getY()) + e.getBbHeight() * 0.5, Mth.lerp(pt, e.zo, e.getZ()));
        float a = env(age, p.life, 6, 12);
        boolean self = e == mc.player && mc.options.getCameraType().isFirstPerson();
        double R = p.r;
        for (int i = 1; i < 8; i++) {   // latitude rings, a ripple running up them
            double lat = -Math.PI / 2 + i * Math.PI / 8;
            double rip = 1 + 0.05 * Math.sin(now * 0.5 - i * 0.9);
            double rr = Math.cos(lat) * R * rip, y = Math.sin(lat) * R * rip;
            ring(m, cam, c.add(0, y, 0), UP, rr, 0.05, i == 4 ? PALE : SEA, (self ? 0.15F : 0.4F) * a);
        }
        if (self) return;
        Vec3 view = c.subtract(cam);
        if (view.lengthSqr() > 1e-6) GravityFxClient.bring(m, cam, c, frame(view.normalize()), R, 0.25, SEA, 0.35F * a);
    }

    // ================================================================== 5 silence field: rings closing in, a flat line where sound died
    private static void silence(Matrix4f m, Vec3 cam, FxPacket p, double age, double now) {
        float a = env(age, p.life, 6, 12);
        Vec3 c = p.c.add(0, 0.05, 0);
        double R = p.r;
        ring(m, cam, c, UP, R, 0.12, DEEP, 0.9F * a);
        ring(m, cam, c, UP, R, 0.03, PALE, 0.7F * a);
        for (int k = 0; k < 3; k++) {
            double ph = (now / 24.0 + k / 3.0) % 1.0;
            ring(m, cam, c, UP, R * (1 - ph), 0.06, SEA, 0.55F * a * (float) Math.sin(ph * Math.PI));
        }
        int N = 64;   // a wave round the edge, its swing dying away to a flat line
        Vec3[] pts = new Vec3[N + 1];
        double[] w = new double[N + 1];
        float[] al = new float[N + 1];
        double amp = 0.45 * Math.max(0, 1 - age / (p.life * 0.6));
        for (int j = 0; j <= N; j++) {
            double an = j * TAU / N;
            pts[j] = c.add(Math.cos(an) * R * 0.85, 0.6 + amp * Math.sin(an * 12 + now * 0.6), Math.sin(an) * R * 0.85);
            w[j] = 0.04;
            al[j] = 0.7F * a;
        }
        gstrip(m, cam, pts, w, al, PALE);
    }

    // ================================================================== 6 pillar: a tuning fork of light
    private static void pillar(Matrix4f m, Vec3 cam, FxPacket p, double age, double now) {
        float a = env(age, p.life, 6, 10);
        double H = p.r;
        Vec3 b = p.c;
        Vec3 view = b.subtract(cam);
        Vec3 side = view.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : new Vec3(-view.z, 0, view.x).normalize();
        double hum = 0.04 * Math.sin(now * 2.6);
        Vec3 fork = b.add(0, H * 0.35, 0);
        gseg(m, cam, b, fork, 0.06, a, a, PALE);   // the stem
        for (int s = -1; s <= 1; s += 2) {   // the two prongs, trembling
            Vec3 p0 = fork.add(side.scale(0.18 * s)), p1 = b.add(0, H, 0).add(side.scale((0.18 + hum) * s));
            gseg(m, cam, p0, p1, 0.06, a, a, PALE);
            gseg(m, cam, p0, p1, 0.2, 0.35F * a, 0.35F * a, SEA);
        }
        gseg(m, cam, fork.add(side.scale(-0.18)), fork.add(side.scale(0.18)), 0.06, a, a, PALE);
        double ph = (age % 15) / 15.0;   // each ring of the fork goes out as a ring of sound
        ring(m, cam, b.add(0, H * 0.7, 0), UP, 0.3 + 1.4 * ph, 0.06, SEA, (float) (1 - ph) * 0.7F * a);
        ring(m, cam, b.add(0, 0.05, 0), UP, 0.6, 0.1, SEA, 0.5F * a);
    }

    // ================================================================== 7 Resonance: rings round the foe, one per ring it carries
    private static void ringing(Matrix4f m, Vec3 cam, Minecraft mc, FxPacket p, double age, double now, float pt) {
        Entity e = mc.level.getEntity((int) p.v.x);
        if (e == null) return;
        Vec3 f = new Vec3(Mth.lerp(pt, e.xo, e.getX()), Mth.lerp(pt, e.yo, e.getY()), Mth.lerp(pt, e.zo, e.getZ()));
        float a = env(age, p.life, 2, 20);
        int n = Math.min(5, (int) p.a0);
        double R = Math.max(0.5, e.getBbWidth() * 0.9);
        for (int i = 0; i < n; i++) {
            double y = e.getBbHeight() * (0.3 + 0.15 * i);
            Vec3 ax = new Vec3(Math.sin(now * 0.1 + i * 1.3) * 0.25, 1, Math.cos(now * 0.1 + i * 1.3) * 0.25).normalize();
            float bright = n >= 4 ? 0.9F + 0.1F * (float) Math.sin(now * 1.5) : 0.7F;
            ring(m, cam, f.add(0, y, 0), ax, R, 0.05, i == n - 1 ? CORE : SEA, bright * a);
        }
    }

    // ================================================================== 8 Crescendo
    private static void loud(Matrix4f m, Vec3 cam, Minecraft mc, FxPacket p, double age, double now, float pt) {
        Entity e = mc.level.getEntity((int) p.v.x);
        if (e == null) return;
        Vec3 f = new Vec3(Mth.lerp(pt, e.xo, e.getX()), Mth.lerp(pt, e.yo, e.getY()), Mth.lerp(pt, e.zo, e.getZ()));
        float a = env(age, p.life, 8, 20);
        int tier = Math.min(5, (int) p.a0);
        boolean self = e == mc.player && mc.options.getCameraType().isFirstPerson();
        double period = 22 - tier * 2.5;
        double ph = (age % period) / period;
        for (int k = 0; k <= tier && k < 3; k++) {   // rings beat out of you, faster and more the louder you get
            double q = (ph + k * 0.18) % 1.0;
            ring(m, cam, f.add(0, 0.06, 0), UP, 0.6 + (2.2 + tier * 0.5) * q, 0.08, SEA, (float) (1 - q) * (0.45F + 0.1F * tier) * a);
        }
        if (self) return;
        for (int k = 0; k < Math.max(1, tier); k++) {
            double y = e.getBbHeight() * (0.15 + 0.85 * ((ph + k / (double) Math.max(1, tier)) % 1.0));
            ring(m, cam, f.add(0, y, 0), UP, 0.55, 0.04, k == 0 ? CORE : PALE, (0.35F + 0.08F * tier) * a);
        }
    }

    // ================================================================== 9 Final Chord
    private static void chord(Matrix4f m, Vec3 cam, FxPacket p, double age, double now) {
        double rise = Math.max(1, p.speed);
        Vec3 c = p.c;
        if (age < rise) {   // three notes rising over the target, one after another
            for (int k = 0; k < 3; k++) {
                double t0 = 4 + k * 6;
                if (age < t0) continue;
                double q = Mth.clamp((age - t0) / 4, 0, 1);
                double y = 1.2 + k * 1.3 * q;
                double pulse = 0.8 + 0.2 * Math.sin(now * 1.4 + k);
                ring(m, cam, c.add(0, y, 0), UP, (1.2 + k * 0.7) * pulse, 0.12, SEA, 0.8F);
                ring(m, cam, c.add(0, y, 0), UP, (1.2 + k * 0.7) * pulse, 0.035, CORE, 0.8F);
            }
            ring(m, cam, c.add(0, 0.05, 0), UP, p.r, 0.15, DEEP, (float) (age / rise));
            return;
        }
        double k = Mth.clamp((age - rise) / (p.life - rise), 0, 1);   // the blast
        double e = 1 - Math.pow(1 - k, 2.4);
        float a = (float) (1 - k);
        Vec3 mid = c.add(0, 1, 0);
        Vec3 view = mid.subtract(cam);
        if (view.lengthSqr() > 1e-6) {
            Vec3[] f = frame(view.normalize());
            GravityFxClient.bring(m, cam, mid, f, p.r * e, 0.3 + 0.5 * (1 - k), SEA, 0.9F * a);
            GravityFxClient.bring(m, cam, mid, f, p.r * e, 0.06, CORE, 0.8F * a);
        }
        ring(m, cam, c.add(0, 0.1, 0), UP, p.r * e, 0.3, PALE, 0.7F * a);
        ring(m, cam, mid, new Vec3(1, 0, 0), p.r * e * 0.97, 0.1, SEA, 0.45F * a);
        ring(m, cam, mid, new Vec3(0, 0, 1), p.r * e * 0.97, 0.1, SEA, 0.45F * a);
        gglow(m, cam, mid, 2 + p.r * 0.5 * (1 - k), 0.7F * a * a, PALE);
    }

    // ================================================================== 10 clap
    private static void clap(Matrix4f m, Vec3 cam, FxPacket p, double age) {
        double k = Mth.clamp(age / p.life, 0, 1);
        double e = 1 - Math.pow(1 - k, 3);
        float a = (float) (1 - k);
        gglow(m, cam, p.c, 0.6 + 0.6 * (1 - k), 0.9F * a * a, CORE);
        Vec3 d = p.d.lengthSqr() < 1e-6 ? UP : p.d.normalize();
        ring(m, cam, p.c, d, 0.3 + p.r * 0.5 * e, 0.12 * (1 - k) + 0.03, SEA, 0.8F * a);
        ring(m, cam, p.c, d, 0.2 + p.r * 0.3 * e, 0.03, CORE, 0.7F * a);
    }

    // ================================================================== helpers
    private static Vec3[] frame(Vec3 axis) {
        Vec3 e1 = Math.abs(axis.y) > 0.9 ? new Vec3(1, 0, 0) : UP;
        e1 = e1.subtract(axis.scale(e1.dot(axis))).normalize();
        return new Vec3[]{e1, axis.cross(e1)};
    }

    private static void ring(Matrix4f m, Vec3 cam, Vec3 c, Vec3 normal, double r, double w, float[] col, float a) {
        GravityFxClient.ring(m, cam, c, normal, r, w, col, a);
    }

    private static void gstrip(Matrix4f m, Vec3 cam, Vec3[] pts, double[] w, float[] al, float[] col) { GravityFxClient.gstrip(m, cam, pts, w, al, col); }

    private static void gseg(Matrix4f m, Vec3 cam, Vec3 a, Vec3 b, double w, float aa, float ab, float[] col) { GravityFxClient.gseg(m, cam, a, b, w, aa, ab, col); }

    private static void gglow(Matrix4f m, Vec3 cam, Vec3 c, double r, float a, float[] col) { GravityFxClient.gglow(m, cam, c, r, a, col); }

    private static float env(double age, double life, double in, double fade) {
        return (float) (Mth.clamp(age / in, 0, 1) * Mth.clamp((life - age) / fade, 0, 1));
    }
}
