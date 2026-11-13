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
 * The Gravity Grimoire, drawn: cores of pure black - solid, swallowing the light - with violet light bending round
 * their rims, rings of force rolling in and out, matter streaming down into wells and up out of lifts.
 * <p>Fed by FxPacket shape 83 (server/GravityFx), {@code h} = kind:
 * 0 orb (c to u over speed ticks, r), 1 well (c, r), 2 crushing field (c, r), 3 wave (c, r, a0 = 1 rolling in),
 * 4 lift column (c, r, r2 height), 5 orbiter (v.x entity, a1 place, a0 count), 6 Mass Domain (v.x entity, r),
 * 7 falling star (c to u over speed, r), 8 slam (c, r), 9 weightless wake (v.x entity). Shape 3 with the id ends one.
 */
public final class GravityFxClient {
    private GravityFxClient() {}

    private static final double TAU = Math.PI * 2;
    private static final Vec3 UP = new Vec3(0, 1, 0);
    static final float[] VIO = {0.6F, 0.34F, 1.0F}, PALE = {0.86F, 0.78F, 1.0F}, CORE = {1.0F, 1.0F, 1.0F}, DEEP = {0.25F, 0.08F, 0.6F};
    private static final float[] BLACK = {0.01F, 0.0F, 0.03F};

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

    public static boolean kill(int id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        boolean f = false;
        for (L l : LIVE) if (l.p.id == id && l.killAt < 0) { l.killAt = mc.level.getGameTime(); f = true; }
        return f;
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
            switch ((int) p.h) {
                case 0 -> {
                    double k = p.speed <= 0 ? 0 : Mth.clamp(age / p.speed, 0, 1);
                    Vec3 at = p.c.add(p.u.subtract(p.c).scale(k));
                    Vec3 dir = p.u.subtract(p.c);
                    orb(vc, m, n, cam, at, p.r * env(age, p.life, 2, 3) * out, now + p.id, dir.lengthSqr() > 1e-6 && k < 1 ? dir.normalize() : null);
                }
                case 1 -> well(vc, m, n, cam, p, age, now, out);
                case 2 -> field(vc, m, n, cam, p, age, now, out);
                case 3 -> wave(m, cam, p, age, out);
                case 4 -> column(m, cam, p, age, now, out);
                case 5 -> orbiter(vc, m, n, cam, mc, p, age, now, pt, out);
                case 6 -> domain(vc, m, n, cam, mc, p, age, now, pt, out);
                case 7 -> star(vc, m, n, cam, p, age, now, out);
                case 8 -> slam(m, cam, p, age, now);
                case 9 -> wake(m, cam, mc, p, age, now, pt);
                default -> LIVE.remove(l);
            }
        }
    }

    // ================================================================== the black core
    /**
     * A sphere of pure black (drawn solid, facing you) inside a bright violet rim that fades outward - light bent round
     * something too heavy for it - and a thin ring of matter orbiting it at a tilt.
     */
    static void core(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 c, double r, double t, float a) {
        if (r < 0.02 || a < 0.02) return;
        Vec3 view = c.subtract(cam);
        if (view.lengthSqr() < 1e-6) return;
        Vec3[] f = frame(view.normalize());
        int seg = 20;
        for (int i = 0; i < seg; i++) {   // the black disc
            double a0 = i * TAU / seg, a1 = (i + 1) * TAU / seg;
            Vec3 p0 = c.add(f[0].scale(Math.cos(a0) * r)).add(f[1].scale(Math.sin(a0) * r));
            Vec3 p1 = c.add(f[0].scale(Math.cos(a1) * r)).add(f[1].scale(Math.sin(a1) * r));
            FxBase.quad(vc, m, n, cam, c, c, p1, p0, BLACK, a, a, a, a);
        }
        // the rim: white-hot right at the edge, violet beyond, gone by 1.7 r
        bring(m, cam, c, f, r * 1.02, r * 0.1, CORE, 0.9F * a);
        bring(m, cam, c, f, r * 1.08, r * 0.35, VIO, 0.75F * a);
        bring(m, cam, c, f, r * 1.3, r * 0.45, DEEP, 0.35F * a);
        // the ring of matter, tilted, turning
        Vec3 ax = new Vec3(Math.sin(t * 0.05) * 0.4, 1, Math.cos(t * 0.05) * 0.4).normalize();
        ring(m, cam, c, ax, r * 1.9, r * 0.12, PALE, 0.55F * a);
        ring(m, cam, c, ax, r * 1.9, r * 0.35, VIO, 0.3F * a);
    }

    private static void orb(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 at, double r, double t, Vec3 dir) {
        core(vc, m, n, cam, at, r, t, 1F);
        if (dir != null) {   // light dragged behind it
            gseg(m, cam, at, at.subtract(dir.scale(r * 5)), r * 0.7, 0.5F, 0, VIO);
            gseg(m, cam, at, at.subtract(dir.scale(r * 3)), r * 0.2, 0.7F, 0, PALE);
        }
    }

    // ================================================================== 1 well
    private static void well(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age, double now, float out) {
        double life = p.life, R = p.r;
        double grow = Mth.clamp(age / 8, 0, 1), collapse = Mth.clamp((life - 6 - age) / 6, 0, 1);
        if (out < 1) collapse = Math.min(collapse, out);
        float a = (float) (grow * collapse);
        Vec3 c = p.c;
        core(vc, m, n, cam, c, 0.75 * FxBase.backOut(grow) * (0.4 + 0.6 * collapse), now, 1F);
        // spiral arms winding in
        for (int arm = 0; arm < 5; arm++) {
            int N = 14;
            Vec3[] pts = new Vec3[N];
            double[] w = new double[N];
            float[] al = new float[N];
            for (int j = 0; j < N; j++) {
                double u = j / (double) (N - 1);
                double rr = R * 0.85 * (1 - u) + 0.8 * u;
                double an = arm * TAU / 5 + u * 3.2 + now * 0.11;
                pts[j] = c.add(Math.cos(an) * rr, Math.sin(an * 2 + now * 0.05) * 0.15 * (1 - u), Math.sin(an) * rr);
                w[j] = 0.05 + 0.18 * u;
                al[j] = a * (float) (0.15 + 0.6 * u);
            }
            gstrip(m, cam, pts, w, al, VIO);
        }
        // matter falling in
        for (int i = 0; i < 18; i++) {
            double ph = (now / (18 + 8 * hs(i, 1)) + hs(i, 2)) % 1.0;
            double an = hs(i, 3) * TAU + ph * 2.5, rr = R * (1 - ph) + 0.8 * ph, y = (hs(i, 4) - 0.5) * 2 * (1 - ph);
            Vec3 q = c.add(Math.cos(an) * rr, y, Math.sin(an) * rr);
            Vec3 to = c.subtract(q).normalize().scale(0.35 + 0.4 * ph);
            gseg(m, cam, q, q.add(to), 0.03, a * (float) Math.sin(ph * Math.PI) * 0.9F, 0, PALE);
        }
        ring(m, cam, c, UP, R, 0.12, VIO, 0.35F * a);
        if (age > life - 6) {   // the collapse: a flash of light as it shuts
            float k = (float) Mth.clamp((age - (life - 6)) / 6, 0, 1);
            gglow(m, cam, c, 1.5 + 2.5 * k, (1 - k) * 0.8F, PALE);
        }
    }

    // ================================================================== 2 crushing field
    private static void field(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age, double now, float out) {
        double R = p.r;
        float a = env(age, p.life, 6, 10) * out;
        Vec3 c = p.c.add(0, 0.04, 0);
        disc(vc, m, n, cam, c, R, 0.35F * a);   // the ground darkens under the weight
        ring(m, cam, c, UP, R, 0.18, VIO, 0.8F * a);
        ring(m, cam, c, UP, R, 0.04, CORE, 0.7F * a);
        for (int k = 0; k < 3; k++) {   // rings of force pressing down out of the air
            double ph = (now / 14.0 + k / 3.0) % 1.0;
            ring(m, cam, c.add(0, 3.2 * (1 - ph), 0), UP, R * (0.75 + 0.25 * ph), 0.08, PALE, a * (float) (ph * (1 - ph) * 3));
        }
        for (int i = 0; i < 26; i++) {   // the air itself falling
            double ph = (now / (9 + 4 * hs(i, 5)) + hs(i, 6)) % 1.0;
            double an = hs(i, 7) * TAU, rr = Math.sqrt(hs(i, 8)) * R * 0.95;
            Vec3 q = c.add(Math.cos(an) * rr, 3.5 * (1 - ph), Math.sin(an) * rr);
            gseg(m, cam, q, q.add(0, 0.7, 0), 0.025, 0, a * (float) Math.sin(ph * Math.PI) * 0.8F, VIO);
        }
    }

    // ================================================================== 3 wave
    private static void wave(Matrix4f m, Vec3 cam, FxPacket p, double age, float out) {
        double k = Mth.clamp(age / p.life, 0, 1);
        double e = 1 - Math.pow(1 - k, 2.2);
        double r = p.a0 > 0.5 ? p.r * (1 - e) + 0.3 : p.r * e + 0.2;
        float a = (float) (1 - k) * out;
        Vec3 view = p.c.subtract(cam);
        if (view.lengthSqr() > 1e-6) {
            Vec3[] f = frame(view.normalize());
            bring(m, cam, p.c, f, r, 0.08 + 0.25 * (1 - k), VIO, 0.8F * a);
            bring(m, cam, p.c, f, r, 0.03, CORE, 0.6F * a);
        }
        ring(m, cam, p.c, UP, r, 0.15, VIO, 0.6F * a);
        ring(m, cam, p.c, new Vec3(1, 0, 0), r * 0.98, 0.06, PALE, 0.35F * a);
        ring(m, cam, p.c, new Vec3(0, 0, 1), r * 0.98, 0.06, PALE, 0.35F * a);
    }

    // ================================================================== 4 lift column
    private static void column(Matrix4f m, Vec3 cam, FxPacket p, double age, double now, float out) {
        float a = env(age, p.life, 3, 8) * out;
        double R = p.r, H = p.r2;
        Vec3 c = p.c.add(0, 0.05, 0);
        ring(m, cam, c, UP, R, 0.2, VIO, 0.7F * a);
        ring(m, cam, c.add(0, H, 0), UP, R * 0.8, 0.12, PALE, 0.3F * a);
        for (int i = 0; i < 30; i++) {
            double ph = (now / (8 + 4 * hs(i, 9)) + hs(i, 10)) % 1.0;
            double an = hs(i, 11) * TAU, rr = Math.sqrt(hs(i, 12)) * R;
            Vec3 q = c.add(Math.cos(an) * rr, H * ph, Math.sin(an) * rr);
            gseg(m, cam, q, q.add(0, 0.8, 0), 0.03, a * (float) Math.sin(ph * Math.PI) * 0.9F, 0, PALE);
        }
    }

    // ================================================================== 5 orbiter
    private static void orbiter(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Minecraft mc, FxPacket p, double age, double now, float pt, float out) {
        Entity e = mc.level.getEntity((int) p.v.x);
        if (e == null) return;
        Vec3 f = new Vec3(Mth.lerp(pt, e.xo, e.getX()), Mth.lerp(pt, e.yo, e.getY()), Mth.lerp(pt, e.zo, e.getZ()));
        double an = TAU * p.a1 + now * 0.12;
        Vec3 at = f.add(Math.cos(an) * 1.3, 1.3 + 0.2 * Math.sin(now * 0.2 + p.a1 * 9), Math.sin(an) * 1.3);
        float k = env(age, p.life, 4, 6) * out;
        core(vc, m, n, cam, at, 0.24 * k, now + p.id, 1F);
        Vec3 back = f.add(Math.cos(an - 0.5) * 1.3, 1.3, Math.sin(an - 0.5) * 1.3);
        gseg(m, cam, at, back, 0.08, 0.45F * k, 0, VIO);
    }

    // ================================================================== 6 Mass Domain
    private static void domain(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Minecraft mc, FxPacket p, double age, double now, float pt, float out) {
        Entity e = mc.level.getEntity((int) p.v.x);
        if (e == null) return;
        Vec3 f = new Vec3(Mth.lerp(pt, e.xo, e.getX()), Mth.lerp(pt, e.yo, e.getY()), Mth.lerp(pt, e.zo, e.getZ()));
        float a = env(age, p.life, 10, 20) * out;
        double R = p.r;
        boolean self = e == mc.player && mc.options.getCameraType().isFirstPerson();
        Vec3 g = f.add(0, 0.05, 0);
        disc(vc, m, n, cam, g, R, 0.22F * a);
        ring(m, cam, g, UP, R, 0.15, VIO, 0.55F * a);
        double beat = Math.pow(Math.max(0, Math.sin(now * 0.12)), 8);
        ring(m, cam, g, UP, R * (1 - 0.6 * ((now / 30.0) % 1.0)), 0.06, PALE, 0.5F * a);
        for (int i = 0; i < 16; i++) {   // the ground's dust dragged in toward you
            double ph = (now / (24 + 8 * hs(i, 13)) + hs(i, 14)) % 1.0;
            double an = hs(i, 15) * TAU + ph * 0.8, rr = R * (1 - ph) + 1.0 * ph;
            Vec3 q = g.add(Math.cos(an) * rr, 0.1, Math.sin(an) * rr);
            gseg(m, cam, q, q.add(f.subtract(q).multiply(1, 0, 1).normalize().scale(0.6)), 0.03, a * (float) Math.sin(ph * Math.PI) * 0.8F, 0, PALE);
        }
        if (self) return;
        for (int k = 0; k < 2; k++) {   // two rings of force round the body, tilted, turning against each other
            Vec3 ax = new Vec3(Math.sin(now * 0.04 * (k == 0 ? 1 : -1) + k * 2) * 0.6, 1, Math.cos(now * 0.04 * (k == 0 ? 1 : -1) + k * 2) * 0.6).normalize();
            Vec3 c = f.add(0, e.getBbHeight() * 0.55, 0);
            ring(m, cam, c, ax, 1.05 + 0.08 * beat, 0.05, CORE, 0.55F * a);
            ring(m, cam, c, ax, 1.05 + 0.08 * beat, 0.18, VIO, 0.35F * a);
        }
    }

    // ================================================================== 7 falling star
    private static void star(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age, double now, float out) {
        double fall = Math.max(1, p.speed);
        double k = Mth.clamp(age / fall, 0, 1);
        Vec3 at = p.c.add(p.u.subtract(p.c).scale(k * k));
        double r = p.r;
        if (age > fall) {   // it implodes: the core shrinks to nothing in a flash
            double q = Mth.clamp((age - fall) / 8, 0, 1);
            core(vc, m, n, cam, at, r * (1 - q), now, 1F);
            gglow(m, cam, at, r * (1.5 + 3 * q), (float) (1 - q) * 0.9F, PALE);
            return;
        }
        core(vc, m, n, cam, at, r * env(age, p.life, 4, 4) * out, now, 1F);
        Vec3 ground = p.u.subtract(0, r * 0.6, 0);
        for (int i = 0; i < 22; i++) {   // the ground's light pulled up into it
            double ph = (now / (10 + 5 * hs(i, 16)) + hs(i, 17)) % 1.0;
            double an = hs(i, 18) * TAU, rr = r * 2.5 * (1 - ph);
            Vec3 q = ground.add(Math.cos(an) * rr, 0, Math.sin(an) * rr);
            Vec3 tgt = at.add(Math.cos(an) * r * 0.8, 0, Math.sin(an) * r * 0.8);
            Vec3 pnt = q.add(tgt.subtract(q).scale(ph));
            gseg(m, cam, pnt, pnt.add(tgt.subtract(q).normalize().scale(0.7)), 0.035, (float) Math.sin(ph * Math.PI) * 0.8F, 0, PALE);
        }
        gseg(m, cam, at.add(0, r * 1.05, 0), at.add(0, r * 4.5, 0), r * 0.6, 0.35F, 0, VIO);   // the air it drags down behind it
    }

    // ================================================================== 8 slam
    private static void slam(Matrix4f m, Vec3 cam, FxPacket p, double age, double now) {
        double k = Mth.clamp(age / p.life, 0, 1);
        double e = 1 - Math.pow(1 - k, 3);
        float a = (float) (1 - k);
        Vec3 c = p.c.add(0, 0.06, 0);
        ring(m, cam, c, UP, p.r * e, 0.3 * (1 - k) + 0.05, VIO, 0.9F * a);
        ring(m, cam, c, UP, p.r * e, 0.05, CORE, 0.8F * a);
        ring(m, cam, c, UP, p.r * 0.6 * e, 0.12, PALE, 0.5F * a);
        gglow(m, cam, c.add(0, 0.3, 0), p.r * 0.5, 0.6F * a, VIO);
        for (int i = 0; i < 14; i++) {   // dust of light thrown up round the edge
            double an = i * TAU / 14 + hs(p.id, i) * 0.4;
            Vec3 q = c.add(Math.cos(an) * p.r * e * 0.9, 0, Math.sin(an) * p.r * e * 0.9);
            gseg(m, cam, q, q.add(0, 0.6 + 1.2 * hs(p.id, i + 20), 0).add(Math.cos(an) * 0.3, 0, Math.sin(an) * 0.3), 0.06, 0.7F * a, 0, PALE);
        }
    }

    // ================================================================== 9 weightless wake
    private static void wake(Matrix4f m, Vec3 cam, Minecraft mc, FxPacket p, double age, double now, float pt) {
        Entity e = mc.level.getEntity((int) p.v.x);
        if (e == null) return;
        Vec3 f = new Vec3(Mth.lerp(pt, e.xo, e.getX()), Mth.lerp(pt, e.yo, e.getY()), Mth.lerp(pt, e.zo, e.getZ()));
        float a = env(age, p.life, 2, 12);
        boolean self = e == mc.player && mc.options.getCameraType().isFirstPerson();
        ring(m, cam, f.add(0, 0.05, 0), UP, 0.55 + 0.1 * Math.sin(now * 0.4), 0.08, VIO, 0.6F * a);
        if (self) return;
        for (int i = 0; i < 8; i++) {   // motes drifting up off you, slow as feathers
            double ph = (now / (20 + 6 * hs(i, 21)) + hs(i, 22)) % 1.0;
            double an = hs(i, 23) * TAU;
            Vec3 q = f.add(Math.cos(an) * 0.5, ph * e.getBbHeight() * 1.2, Math.sin(an) * 0.5);
            gseg(m, cam, q, q.add(0, 0.25, 0), 0.03, a * (float) Math.sin(ph * Math.PI) * 0.8F, 0, PALE);
        }
    }

    // ================================================================== drawing helpers
    private static Vec3[] frame(Vec3 axis) {
        Vec3 e1 = Math.abs(axis.y) > 0.9 ? new Vec3(1, 0, 0) : UP;
        e1 = e1.subtract(axis.scale(e1.dot(axis))).normalize();
        return new Vec3[]{e1, axis.cross(e1)};
    }

    /** A glowing ring in the plane of f[0], f[1] (bright at radius r, fading over w both ways). */
    static void bring(Matrix4f m, Vec3 cam, Vec3 c, Vec3[] f, double r, double w, float[] col, float a) {
        if (a <= 0.01F || r <= 0) return;
        int seg = 36;
        for (int i = 0; i < seg; i++) {
            double a0 = i * TAU / seg, a1 = (i + 1) * TAU / seg;
            Vec3 d0 = f[0].scale(Math.cos(a0)).add(f[1].scale(Math.sin(a0))), d1 = f[0].scale(Math.cos(a1)).add(f[1].scale(Math.sin(a1)));
            Vec3 i0 = c.add(d0.scale(Math.max(0, r - w))), i1 = c.add(d1.scale(Math.max(0, r - w))), m0 = c.add(d0.scale(r)), m1 = c.add(d1.scale(r)), o0 = c.add(d0.scale(r + w)), o1 = c.add(d1.scale(r + w));
            FxBase.gquad(m, cam, i0, i1, m1, m0, col, 0, 0, a, a);
            FxBase.gquad(m, cam, m0, m1, o1, o0, col, a, a, 0, 0);
        }
    }

    /** A glowing ring round the axis {@code normal}. */
    static void ring(Matrix4f m, Vec3 cam, Vec3 c, Vec3 normal, double r, double w, float[] col, float a) {
        bring(m, cam, c, frame(normal.normalize()), r, w, col, a);
    }

    static void gstrip(Matrix4f m, Vec3 cam, Vec3[] pts, double[] w, float[] al, float[] col) {
        for (int i = 0; i < pts.length - 1; i++) {
            Vec3 a = pts[i], b = pts[i + 1], ax = b.subtract(a);
            if (ax.lengthSqr() < 1e-10) continue;
            Vec3 s = ax.cross(a.subtract(cam));
            if (s.lengthSqr() < 1e-10) continue;
            s = s.normalize();
            FxBase.gquad(m, cam, a.add(s.scale(w[i])), b.add(s.scale(w[i + 1])), b.subtract(s.scale(w[i + 1])), a.subtract(s.scale(w[i])), col, al[i], al[i + 1], al[i + 1], al[i]);
        }
    }

    static void gseg(Matrix4f m, Vec3 cam, Vec3 a, Vec3 b, double w, float aa, float ab, float[] col) {
        gstrip(m, cam, new Vec3[]{a, b}, new double[]{w, w}, new float[]{aa, ab}, col);
    }

    static void gglow(Matrix4f m, Vec3 cam, Vec3 c, double r, float a, float[] col) {
        Vec3 view = c.subtract(cam);
        if (view.lengthSqr() < 1e-6 || a <= 0.01F) return;
        Vec3[] f = frame(view.normalize());
        int seg = 16;
        for (int i = 0; i < seg; i++) {
            double a0 = i * TAU / seg, a1 = (i + 1) * TAU / seg;
            Vec3 p0 = c.add(f[0].scale(Math.cos(a0) * r)).add(f[1].scale(Math.sin(a0) * r)), p1 = c.add(f[0].scale(Math.cos(a1) * r)).add(f[1].scale(Math.sin(a1) * r));
            FxBase.gquad(m, cam, c, c, p1, p0, col, a, a, 0, 0);
        }
    }

    /** A dark, see-through disc flat on the ground (the weight pressing on it). */
    private static void disc(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 c, double r, float a) {
        if (a <= 0.01F) return;
        int seg = 28;
        for (int i = 0; i < seg; i++) {
            double a0 = i * TAU / seg, a1 = (i + 1) * TAU / seg;
            Vec3 p0 = c.add(Math.cos(a0) * r, 0, Math.sin(a0) * r), p1 = c.add(Math.cos(a1) * r, 0, Math.sin(a1) * r);
            FxBase.quad(vc, m, n, cam, c, c, p1, p0, BLACK, a, a, a * 0.2F, a * 0.2F);
        }
    }

    private static float env(double age, double life, double in, double fade) {
        return (float) (Mth.clamp(age / in, 0, 1) * Mth.clamp((life - age) / fade, 0, 1));
    }

    private static double hs(int a, int b) { return FxBase.h2(a, b, 577); }
}
