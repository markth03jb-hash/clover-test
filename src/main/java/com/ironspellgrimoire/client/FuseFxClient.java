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
 * The Grimoire of the Quiet Fuse, drawn: soot, a fizzing wick, a blast that is a real blast. Its own look:
 * <ul>
 *   <li>0 the blast: a white flash, a fireball swelling and going dark, smoke rolling up, a shock ring on the
 *       ground, burning bits thrown out and falling, sparks;</li>
 *   <li>1 the armed: a lit wick fizzing over the head, spitting sparks, and a pink stitched charge on the chest;</li>
 *   <li>2 a Heat Seeker: a little round bomb with its wick lit, hunting, smoke behind it;</li>
 *   <li>3 an Air Bubble: a wobbling bubble of air with a spark of a charge in it;</li>
 *   <li>4 Contact Charge: a ticking dial on your chest, its marks lighting round, while it waits;</li>
 *   <li>5 Chain Fuse: a burning fuse coiling out over the ground from your feet; 6 a fuse line from a blast to
 *       what it arms, the flame running along it, burnt black behind;</li>
 *   <li>7 Hidden Tenant: a charge glowing inside its host, beating like a heart;</li>
 *   <li>8 Loop of Ruin: a ring of bombs going round you, a clock-hand sweeping; 9 a blast's ghost waiting where it
 *       was, its fuse burning down to the replay;</li>
 *   <li>10 Afterburn: a ring of flames left on the ground; 11 the Primed Coin, spinning out;
 *       12 shrapnel: hot bits streaking to the far ones; 13 a thing armed: a pop of pink stitches.</li>
 * </ul>
 * Fed by FxPacket shape 102 (server/FuseFx): {@code h} = kind, {@code v.x} = the entity (or a key) it belongs to.
 */
public final class FuseFxClient {
    private FuseFxClient() {}

    private static final double TAU = Math.PI * 2;
    private static final Vec3 UP = new Vec3(0, 1, 0);
    static final float[] WHITE = {1F, 0.97F, 0.88F}, YEL = {1F, 0.82F, 0.3F}, ORA = {1F, 0.45F, 0.1F}, RED = {0.8F, 0.14F, 0.05F},
            SOOT = {0.07F, 0.06F, 0.06F}, SMOKE = {0.22F, 0.2F, 0.2F}, PINK = {1F, 0.35F, 0.7F}, BOMB = {0.12F, 0.1F, 0.12F},
            AIR = {0.75F, 0.9F, 1F}, WICK = {0.45F, 0.33F, 0.2F};

    private static final class L {
        FxPacket p;
        double born, moved;
        final List<Vec3> hist = new ArrayList<>();
        L(FxPacket p, double born) { this.p = p; this.born = born; }
    }

    private static final List<L> LIVE = new ArrayList<>();

    /** Kinds that follow something: a new packet for the same key moves it on rather than adding another. */
    private static boolean keyed(int k) { return k == 1 || k == 2 || k == 3 || k == 4 || k == 11; }

    public static void spawn(FxPacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        int k = (int) p.h;
        double now = mc.level.getGameTime();
        if (k == 98) { LIVE.removeIf(l -> (int) l.p.v.x == (int) p.v.x && (int) l.p.h == (int) p.a0); return; }
        if (keyed(k)) for (L l : LIVE) if ((int) l.p.h == k && (int) l.p.v.x == (int) p.v.x) {
            l.hist.add(0, pos(l, now));
            l.moved = now;
            if (l.hist.size() > 10) l.hist.remove(l.hist.size() - 1);
            double age = now - l.born;
            p.a1 = l.p.a1;
            l.p = p;
            l.born = now - Math.min(age, 200);
            l.p.life = (int) (Math.min(age, 200) + p.life);
            return;
        }
        if (LIVE.size() > 600) LIVE.remove(0);
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
                case 0 -> blast(vc, m, n, cam, p, age);
                case 1 -> armed(vc, m, n, cam, mc, p, age, now, pt);
                case 2 -> seeker(vc, m, n, cam, l, age, now);
                case 3 -> bubble(vc, m, n, cam, l, age, now);
                case 4 -> dial(m, cam, mc, p, age, now, pt);
                case 5 -> coil(vc, m, n, cam, p, age, now);
                case 6 -> fuseLine(vc, m, n, cam, p, age, now);
                case 7 -> tenant(m, cam, mc, p, age, now, pt);
                case 8 -> loop(vc, m, n, cam, mc, p, age, now, pt);
                case 9 -> ghost(m, cam, p, age, now);
                case 10 -> afterburn(vc, m, n, cam, p, age, now);
                case 11 -> coin(vc, m, n, cam, l, age, now);
                case 12 -> shrapnel(vc, m, n, cam, p, age);
                case 13 -> pop(m, cam, p, age);
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

    private static double height(Minecraft mc, FxPacket p) { Entity e = mc.level.getEntity((int) p.v.x); return e == null ? 1.8 : e.getBbHeight(); }

    /** A solid disc facing the camera (both faces) - a puff of smoke, a bomb, a bit of debris. */
    static void puff(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 c, double r, float[] col, float aIn, float aOut, double seed) {
        Vec3 d = c.subtract(cam).normalize();
        Vec3 a = Math.abs(d.y) > 0.9 ? new Vec3(1, 0, 0) : UP;
        Vec3 x = d.cross(a).normalize(), y = x.cross(d).normalize();
        int N = 10;
        Vec3[] ring = new Vec3[N];
        for (int i = 0; i < N; i++) {
            double an = i * TAU / N, k = 0.8 + 0.35 * hash(seed + i);
            ring[i] = c.add(x.scale(Math.cos(an) * r * k)).add(y.scale(Math.sin(an) * r * k));
        }
        OmenFxClient.fan(vc, m, n, cam, c, ring, col, aIn, aOut);
    }

    static void sseg(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 a, Vec3 b, double w, float aa, float ab, float[] col) {
        VectorFxClient.sseg(vc, m, n, cam, a, b, w, aa, ab, col);
    }

    private static float[] mix(float[] a, float[] b, double t) {
        float k = (float) Mth.clamp(t, 0, 1);
        return new float[]{a[0] + (b[0] - a[0]) * k, a[1] + (b[1] - a[1]) * k, a[2] + (b[2] - a[2]) * k};
    }

    /** A lit wick: a short curl of cord from 'base' with a fizzing spark at its tip, spitting. */
    static void wick(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 base, double len, double t, double seed, float a) {
        Vec3 prev = base;
        for (int k = 1; k <= 4; k++) {
            double u = k / 4.0;
            Vec3 nx = base.add(Math.sin(u * 2.5 + seed) * 0.08 * u, len * u, Math.cos(u * 2 + seed) * 0.06 * u);
            sseg(vc, m, n, cam, prev, nx, 0.035, a, a, WICK);
            prev = nx;
        }
        double fl = 0.75 + 0.25 * Math.sin(t * 2.3 + seed * 7);
        GravityFxClient.gglow(m, cam, prev, 0.22 * fl, a, YEL);
        GravityFxClient.gglow(m, cam, prev, 0.09, a, WHITE);
        for (int i = 0; i < 5; i++) {   // the fizz: sparks spat out every way, short-lived
            double ph = (t * 0.35 + hash(seed + i)) % 1.0, an = hash(seed * 3 + i + Math.floor(t * 0.35 + hash(seed + i))) * TAU;
            Vec3 dir = new Vec3(Math.cos(an), 0.6 + hash(i + seed), Math.sin(an)).normalize();
            Vec3 s0 = prev.add(dir.scale(ph * 0.35)), s1 = prev.add(dir.scale(ph * 0.35 + 0.08)).add(0, -ph * ph * 0.1, 0);
            GravityFxClient.gseg(m, cam, s0, s1, 0.02, a * (float) (1 - ph), a * (float) (1 - ph), ph < 0.4 ? WHITE : YEL);
        }
    }

    // ------------------------------------------------------------------ 0 the blast
    private static void blast(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age) {
        double R = Math.max(1, p.r), q = age / p.life, s = p.a1;
        Vec3 c = p.c;
        Vec3 g = p.u == null ? c.add(0, -1, 0) : p.u;   // the ground under it
        // the flash
        if (age < 3) { float fa = (float) (1 - age / 3); GravityFxClient.gglow(m, cam, c, R * 1.6, fa, WHITE); GravityFxClient.gglow(m, cam, c, R * 0.7, fa, WHITE); }
        // the fireball: swells fast, then cools and darkens as it rises
        double fb = Math.min(1, age / 4), cool = Mth.clamp((age - 3) / 10, 0, 1);
        if (age < 14) {
            Vec3 fc = c.add(0, cool * R * 0.4, 0);
            float fa = (float) (1 - cool);
            GravityFxClient.gglow(m, cam, fc, R * 0.9 * fb, fa, mix(YEL, ORA, cool));
            for (int i = 0; i < 7; i++) {   // lobes of fire round it
                double an = s + i * TAU / 7, el = (hash(s + i) - 0.3) * 1.2;
                Vec3 o = new Vec3(Math.cos(an) * Math.cos(el), Math.sin(el), Math.sin(an) * Math.cos(el)).scale(R * 0.5 * fb);
                GravityFxClient.gglow(m, cam, fc.add(o), R * 0.45 * fb, fa * 0.9F, mix(ORA, RED, cool));
            }
            GravityFxClient.gglow(m, cam, fc, R * 0.35 * fb, fa, WHITE);
        }
        // smoke: dark puffs rolling up and out, thinning
        if (age > 2) {
            double sq = Mth.clamp((age - 2) / (p.life - 2), 0, 1);
            for (int i = 0; i < 9; i++) {
                double an = s * 2 + i * TAU / 9, out = R * (0.3 + 0.5 * hash(i + s)) * (0.4 + sq), up = R * (0.2 + 1.3 * sq * (0.6 + 0.6 * hash(i * 3 + s)));
                Vec3 pc = c.add(Math.cos(an) * out, up, Math.sin(an) * out);
                double pr = R * (0.35 + 0.4 * sq) * (0.7 + 0.5 * hash(i * 7 + s));
                float pa = (float) ((1 - sq) * Mth.clamp((age - 2) / 3, 0, 1)) * 0.75F;
                puff(vc, m, n, cam, pc, pr, i % 3 == 0 ? SOOT : SMOKE, pa, pa * 0.2F, s + i);
                if (age < 10) GravityFxClient.gglow(m, cam, pc, pr * 0.6, pa * (float) (1 - age / 10) * 0.6F, ORA);   // lit from inside, at first
            }
        }
        // the shock ring along the ground, and a scorch
        if (age < 10) {
            double rq = age / 10;
            GravityFxClient.ring(m, cam, g.add(0, 0.06, 0), UP, R * (0.4 + 1.4 * rq), 0.25 * (1 - rq) + 0.05, WHITE, (float) (1 - rq));
            GravityFxClient.ring(m, cam, g.add(0, 0.05, 0), UP, R * (0.3 + 1.1 * rq), 0.5 * (1 - rq), ORA, (float) (1 - rq) * 0.7F);
        }
        OmenFxClient.blob(vc, m, n, cam, g.add(0, 0.03, 0), R * 0.8, R * 0.8, s, s, SOOT, (float) (1 - q) * 0.6F);
        // burning bits thrown out, falling, trailing smoke
        for (int i = 0; i < 10; i++) {
            double an = s * 3 + i * TAU / 10 + hash(i) * 0.5, sp = R * (0.45 + 0.35 * hash(i * 5 + s)), vy = R * (0.35 + 0.4 * hash(i * 11 + s));
            double t0 = age * 0.08, t1 = Math.max(0, t0 - 0.06);
            Vec3 b0 = c.add(Math.cos(an) * sp * t0 * 2, vy * t0 * 2 - 2.5 * t0 * t0 * 2, Math.sin(an) * sp * t0 * 2);
            Vec3 b1 = c.add(Math.cos(an) * sp * t1 * 2, vy * t1 * 2 - 2.5 * t1 * t1 * 2, Math.sin(an) * sp * t1 * 2);
            if (b0.y < g.y) continue;
            float ba = (float) Mth.clamp(1 - q * 1.2, 0, 1);
            GravityFxClient.gseg(m, cam, b1, b0, 0.06, ba * 0.3F, ba, ORA);
            GravityFxClient.gglow(m, cam, b0, 0.12, ba, YEL);
        }
        // sparks: fast, thin, bright
        if (age < 8) for (int i = 0; i < 14; i++) {
            double an = s * 5 + i * TAU / 14, el = (hash(i * 13 + s) - 0.2) * 1.4, len = R * (0.8 + hash(i + s * 2)) * (age / 8 + 0.1);
            Vec3 d = new Vec3(Math.cos(an) * Math.cos(el), Math.sin(el), Math.sin(an) * Math.cos(el));
            float sa = (float) (1 - age / 8);
            GravityFxClient.gseg(m, cam, c.add(d.scale(len * 0.7)), c.add(d.scale(len)), 0.03, 0, sa, WHITE);
        }
    }

    // ------------------------------------------------------------------ 1 the armed, 13 the arming pop
    private static void armed(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Minecraft mc, FxPacket p, double age, double t, float pt) {
        Vec3 f = at(mc, p, pt);
        if (f == null) return;
        double h = height(mc, p);
        float a = (float) Mth.clamp(age / 3, 0, 1);
        wick(vc, m, n, cam, f.add(0, h + 0.1, 0), 0.4, t + pt, p.a1, a);
        // the charge: a pink stitched ring on the chest, pulsing faster the longer it's armed
        Vec3 c = f.add(0, h * 0.55, 0);
        double rate = 0.12 + Math.min(0.4, age * 0.002), pulse = 0.5 + 0.5 * Math.sin(t * rate * TAU / 2);
        Vec3 d = c.subtract(cam).normalize();
        Vec3 face = new Vec3(d.x, 0, d.z).normalize();
        GravityFxClient.ring(m, cam, c.subtract(face.scale(0.35)), face, 0.22, 0.03, PINK, a * (0.5F + 0.5F * (float) pulse));
        Vec3 side = new Vec3(-face.z, 0, face.x);
        for (int i = 0; i < 4; i++) {   // stitches across it
            double an = i * TAU / 4 + 0.4;
            Vec3 o = side.scale(Math.cos(an) * 0.22).add(UP.scale(Math.sin(an) * 0.22));
            Vec3 b = c.subtract(face.scale(0.36));
            GravityFxClient.gseg(m, cam, b.add(o.scale(0.7)), b.add(o.scale(1.3)), 0.025, a, a, PINK);
        }
        GravityFxClient.gglow(m, cam, c.subtract(face.scale(0.36)), 0.12 + 0.06 * pulse, a * (float) pulse, ORA);
    }

    private static void pop(Matrix4f m, Vec3 cam, FxPacket p, double age) {
        double q = age / p.life;
        float a = (float) (1 - q);
        for (int i = 0; i < 8; i++) {
            double an = p.a1 + i * TAU / 8;
            Vec3 d = new Vec3(Math.cos(an), Math.sin(an * 2) * 0.4, Math.sin(an));
            GravityFxClient.gseg(m, cam, p.c.add(d.scale(0.2 + q * 0.6)), p.c.add(d.scale(0.35 + q * 0.8)), 0.03, a, a, PINK);
        }
        GravityFxClient.gglow(m, cam, p.c, 0.35 * (1 - q), a, PINK);
    }

    // ------------------------------------------------------------------ 2 Heat Seeker, 3 Air Bubble, 11 the coin
    /** Where a moving thing is now: refreshed every couple of ticks, it slides from its last place to the new one. */
    private static Vec3 pos(L l, double now) {
        Vec3 c = l.p.c;
        if (l.hist.isEmpty()) return c;
        double k = Mth.clamp((now - l.moved) / 2.0, 0, 1);
        return l.hist.get(0).add(c.subtract(l.hist.get(0)).scale(k));
    }

    private static void seeker(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, L l, double age, double t) {
        FxPacket p = l.p;
        Vec3 c = pos(l, t);
        float a = (float) Mth.clamp(age / 3, 0, 1);
        for (int i = 0; i < l.hist.size(); i++) {   // smoke behind it
            float sa = a * 0.5F * (1 - i / (float) l.hist.size());
            puff(vc, m, n, cam, l.hist.get(i).add(0, i * 0.03, 0), 0.12 + i * 0.03, SMOKE, sa, 0, p.a1 + i);
        }
        puff(vc, m, n, cam, c, 0.22, BOMB, a, a, p.a1);   // the bomb
        GravityFxClient.ring(m, cam, c, c.subtract(cam).normalize(), 0.22, 0.025, RED, a * 0.8F);   // a hot rim
        GravityFxClient.gglow(m, cam, c.add(0.07, 0.08, 0), 0.06, a, WHITE);
        wick(vc, m, n, cam, c.add(0, 0.2, 0), 0.22, t, p.a1, a);
        GravityFxClient.gglow(m, cam, c, 0.6, a * 0.25F, ORA);
    }

    private static void bubble(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, L l, double age, double t) {
        FxPacket p = l.p;
        Vec3 c = pos(l, t);
        float a = (float) Mth.clamp(age / 4, 0, 1);
        Vec3 d = c.subtract(cam).normalize();
        Vec3 ax = Math.abs(d.y) > 0.9 ? new Vec3(1, 0, 0) : UP;
        Vec3 x = d.cross(ax).normalize(), y = x.cross(d).normalize();
        double R = 0.38;
        Vec3 prev = null, first = null;
        for (int i = 0; i <= 20; i++) {   // the skin, wobbling
            double an = i * TAU / 20, w = 1 + 0.08 * Math.sin(an * 3 + t * 0.4 + p.a1) + 0.05 * Math.sin(an * 2 - t * 0.27);
            Vec3 q = c.add(x.scale(Math.cos(an) * R * w)).add(y.scale(Math.sin(an) * R * w));
            if (prev != null) GravityFxClient.gseg(m, cam, prev, q, 0.03, a * 0.8F, a * 0.8F, AIR);
            prev = q;
        }
        puff(vc, m, n, cam, c, R * 0.95, AIR, a * 0.06F, a * 0.18F, p.a1);   // a faint body
        GravityFxClient.gglow(m, cam, c.add(x.scale(-R * 0.4)).add(y.scale(R * 0.45)), 0.08, a, WHITE);   // the shine
        double pul = 0.6 + 0.4 * Math.sin(t * 0.5 + p.a1);
        GravityFxClient.gglow(m, cam, c, 0.1 * pul, a, PINK);   // the charge inside it
        GravityFxClient.gglow(m, cam, c, 0.04, a, WHITE);
    }

    private static void coin(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, L l, double age, double t) {
        Vec3 c = pos(l, t);
        float a = 1F;
        for (int i = 0; i < l.hist.size(); i++) GravityFxClient.gglow(m, cam, l.hist.get(i), 0.1, a * 0.6F * (1 - i / (float) l.hist.size()), PINK);
        Vec3 face = new Vec3(Math.cos(t * 1.1), 0, Math.sin(t * 1.1));
        Vec3 ax = face.cross(UP).normalize();
        Vec3[] ring = new Vec3[12];
        for (int i = 0; i < 12; i++) { double an = i * TAU / 12; ring[i] = c.add(ax.scale(Math.cos(an) * 0.16)).add(UP.scale(Math.sin(an) * 0.16)); }
        OmenFxClient.fan(vc, m, n, cam, c, ring, YEL, a, a * 0.8F);
        GravityFxClient.bring(m, cam, c, new Vec3[]{ax, UP}, 0.16, 0.025, PINK, a);
        GravityFxClient.gglow(m, cam, c, 0.25, a * 0.4F, PINK);
    }

    // ------------------------------------------------------------------ 4 Contact Charge
    private static void dial(Matrix4f m, Vec3 cam, Minecraft mc, FxPacket p, double age, double t, float pt) {
        Vec3 f = at(mc, p, pt);
        if (f == null) return;
        Entity e = mc.level.getEntity((int) p.v.x);
        boolean self = e == mc.player && mc.options.getCameraType().isFirstPerson();
        float a = (float) Mth.clamp(age / 3, 0, 1) * (self ? 0.45F : 1F);
        Vec3 c = f.add(0, (e == null ? 1.8 : e.getBbHeight()) * 0.55, 0);
        Vec3 fwd = e == null ? new Vec3(0, 0, 1) : Vec3.directionFromRotation(0, Mth.lerp(pt, e.yRotO, e.getYRot()));
        if (self) { c = f.add(0, 0.2, 0); fwd = UP; }   // in first person: on the ground under you, where you can see it
        Vec3 b = c.add(fwd.scale(self ? 0 : 0.32));
        double R = self ? 0.9 : 0.36;
        GravityFxClient.ring(m, cam, b, fwd, R, 0.03, PINK, a);
        Vec3[] fr = new Vec3[2];
        Vec3 ax = Math.abs(fwd.y) > 0.9 ? new Vec3(1, 0, 0) : UP;
        fr[0] = fwd.cross(ax).normalize(); fr[1] = fr[0].cross(fwd).normalize();
        int lit = (int) ((t * 0.25) % 12);
        for (int i = 0; i < 12; i++) {   // the marks round the dial, one lighting after another
            double an = -i * TAU / 12 + Math.PI / 2;
            Vec3 o = fr[0].scale(Math.cos(an)).add(fr[1].scale(Math.sin(an)));
            boolean on = i == lit;
            GravityFxClient.gseg(m, cam, b.add(o.scale(R * 0.78)), b.add(o.scale(R * 0.95)), on ? 0.05 : 0.025, a, a, on ? YEL : PINK);
        }
        double an = -t * 0.25 / 12 * TAU + Math.PI / 2;   // the hand
        Vec3 o = fr[0].scale(Math.cos(an)).add(fr[1].scale(Math.sin(an)));
        GravityFxClient.gseg(m, cam, b, b.add(o.scale(R * 0.7)), 0.03, a, a, WHITE);
        GravityFxClient.gglow(m, cam, b, R * 0.25, a * 0.6F, ORA);
    }

    // ------------------------------------------------------------------ 5 Chain Fuse, 6 a fuse line
    private static void coil(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age, double t) {
        float a = (float) Mth.clamp((p.life - age) / 8, 0, 1);
        double burn = Math.min(1, age / 18);
        int N = 60;
        Vec3 prev = null;
        for (int i = 0; i <= N; i++) {   // a spiral of cord out from your feet: burnt black behind the flame
            double u = i / (double) N;
            if (u > burn) break;
            double an = p.a1 + u * TAU * 2.5, rr = 0.4 + u * 2.2;
            Vec3 q = p.c.add(Math.cos(an) * rr, 0.05, Math.sin(an) * rr);
            if (prev != null) sseg(vc, m, n, cam, prev, q, 0.06, a, a, burn - u < 0.08 ? WICK : SOOT);
            prev = q;
        }
        if (prev != null && burn < 1) wick(vc, m, n, cam, prev, 0.05, t, p.a1, a);
        if (prev != null && burn >= 1) GravityFxClient.gglow(m, cam, prev, 0.5 * a, a, YEL);
    }

    private static void fuseLine(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age, double t) {
        double burn = Math.min(1, age / Math.max(1, p.life * 0.6));
        float a = (float) Mth.clamp((p.life - age) / 4, 0, 1);
        Vec3 ab = p.u.subtract(p.c);
        int N = 12;
        Vec3 prev = p.c, flame = p.c;
        for (int i = 1; i <= N; i++) {
            double u = i / (double) N;
            Vec3 q = p.c.add(ab.scale(u)).add(0, -Math.sin(u * Math.PI) * 0.4, 0);   // the cord sags
            sseg(vc, m, n, cam, prev, q, 0.035, a, a, u <= burn ? SOOT : WICK);
            if (u <= burn) flame = q;
            prev = q;
        }
        if (burn < 1) wick(vc, m, n, cam, flame, 0.04, t, p.a1, a);
        else GravityFxClient.gglow(m, cam, p.u, 0.4 * a, a, PINK);
    }

    // ------------------------------------------------------------------ 7 Hidden Tenant
    private static void tenant(Matrix4f m, Vec3 cam, Minecraft mc, FxPacket p, double age, double t, float pt) {
        Vec3 f = at(mc, p, pt);
        if (f == null) return;
        Entity e = mc.level.getEntity((int) p.v.x);
        boolean self = e == mc.player && mc.options.getCameraType().isFirstPerson();
        if (self) return;   // you can't see inside yourself (the HUD says it's there)
        float a = (float) (Mth.clamp(age / 6, 0, 1) * Mth.clamp((p.life - age) / 10, 0, 1));
        Vec3 c = f.add(0, height(mc, p) * 0.6, 0);
        double ph = (t % 24) / 24.0, beat = Math.max(Math.exp(-ph * 18), 0.7 * Math.exp(-Math.abs(ph - 0.22) * 22));   // lub-dub
        GravityFxClient.gglow(m, cam, c, 0.3 + 0.25 * beat, a * (0.35F + 0.65F * (float) beat), PINK);
        GravityFxClient.gglow(m, cam, c, 0.1 + 0.08 * beat, a, ORA);
        if (beat > 0.5) GravityFxClient.ring(m, cam, c, c.subtract(cam).normalize(), 0.35 + (1 - beat) * 0.6, 0.02, PINK, a * (float) beat);
    }

    // ------------------------------------------------------------------ 8 Loop of Ruin, 9 a blast's ghost
    private static void loop(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Minecraft mc, FxPacket p, double age, double t, float pt) {
        Vec3 f = p.v.x >= 0 ? at(mc, p, pt) : p.c;
        if (f == null) return;
        float a = (float) (Mth.clamp(age / 8, 0, 1) * Mth.clamp((p.life - age) / 10, 0, 1));
        Vec3 g = f.add(0, 0.08, 0);
        double R = p.r;
        GravityFxClient.ring(m, cam, g, UP, R, 0.08, PINK, a * 0.7F);
        GravityFxClient.ring(m, cam, g, UP, R - 0.5, 0.03, ORA, a * 0.5F);
        for (int i = 0; i < 12; i++) {   // the ring of bombs, going round
            double an = p.a1 + t * 0.02 + i * TAU / 12;
            Vec3 b = g.add(Math.cos(an) * R, 0.6 + 0.15 * Math.sin(t * 0.2 + i), Math.sin(an) * R);
            puff(vc, m, n, cam, b, 0.2, BOMB, a, a, i);
            wick(vc, m, n, cam, b.add(0, 0.18, 0), 0.15, t + i * 3, i, a);
        }
        double an = -(age / p.life) * TAU;   // the clock-hand: once round in the whole of it
        GravityFxClient.gseg(m, cam, g, g.add(Math.sin(an) * R * 0.9, 0, Math.cos(an) * R * 0.9), 0.06, a, a * 0.4F, PINK);
    }

    private static void ghost(Matrix4f m, Vec3 cam, FxPacket p, double age, double t) {
        double q = age / p.life;
        float a = (float) Mth.clamp(age / 4, 0, 1) * 0.8F;
        double R = Math.max(1, p.r);
        Vec3 c = p.c;
        GravityFxClient.ring(m, cam, c, c.subtract(cam).normalize(), R * 0.5, 0.03, PINK, a * 0.6F);
        int N = 24, lit = (int) (N * (1 - q));
        Vec3 d = c.subtract(cam).normalize();
        Vec3 ax = Math.abs(d.y) > 0.9 ? new Vec3(1, 0, 0) : UP;
        Vec3 x = d.cross(ax).normalize(), y = x.cross(d).normalize();
        Vec3 prev = null;
        for (int i = 0; i <= lit; i++) {   // the fuse round it, burning down to the replay
            double an = Math.PI / 2 - i * TAU / N;
            Vec3 o = c.add(x.scale(Math.cos(an) * R * 0.62)).add(y.scale(Math.sin(an) * R * 0.62));
            if (prev != null) GravityFxClient.gseg(m, cam, prev, o, 0.03, a, a, ORA);
            prev = o;
        }
        if (prev != null) GravityFxClient.gglow(m, cam, prev, 0.15, a, YEL);
        GravityFxClient.gglow(m, cam, c, R * 0.3 * (0.6 + 0.4 * Math.sin(t * 0.6)), a * 0.3F, PINK);
    }

    // ------------------------------------------------------------------ 10 Afterburn, 12 shrapnel
    private static void afterburn(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age, double t) {
        float a = (float) (Mth.clamp(age / 4, 0, 1) * Mth.clamp((p.life - age) / 12, 0, 1));
        Vec3 g = p.c;
        double R = p.r;
        int N = Math.max(10, (int) (R * 7));
        for (int i = 0; i < N; i++) {   // tongues of flame round the ring, flickering
            double an = p.a1 + i * TAU / N;
            double fl = 0.5 + 0.5 * Math.sin(t * (0.6 + hash(i) * 0.5) + i * 2.1), h = (0.35 + 0.45 * fl) * a;
            Vec3 b = g.add(Math.cos(an) * R, 0.05, Math.sin(an) * R);
            Vec3 tip = b.add(Math.sin(t * 0.3 + i) * 0.08, h, Math.cos(t * 0.27 + i) * 0.08);
            GravityFxClient.gseg(m, cam, b, tip, 0.14 * (0.7 + 0.3 * fl), a, 0, ORA);
            GravityFxClient.gseg(m, cam, b, b.add(tip.subtract(b).scale(0.55)), 0.07, a, 0, YEL);
        }
        GravityFxClient.ring(m, cam, g.add(0, 0.04, 0), UP, R, 0.18, RED, a * 0.6F);
        OmenFxClient.blob(vc, m, n, cam, g.add(0, 0.025, 0), R + 0.2, R + 0.2, 0, p.a1, SOOT, a * 0.25F);
    }

    private static void shrapnel(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age) {
        double q = Math.min(1, age / 3);
        float a = (float) (1 - age / p.life);
        Vec3 ab = p.u.subtract(p.c);
        for (int i = 0; i < 3; i++) {
            Vec3 off = new Vec3(hash(p.a1 + i) - 0.5, hash(p.a1 + i * 2) - 0.5, hash(p.a1 + i * 3) - 0.5).scale(0.5);
            Vec3 tip = p.c.add(ab.scale(q)).add(off.scale(q)), tail = p.c.add(ab.scale(Math.max(0, q - 0.3))).add(off.scale(q));
            GravityFxClient.gseg(m, cam, tail, tip, 0.05, 0, a, ORA);
            sseg(vc, m, n, cam, tip.subtract(ab.normalize().scale(0.1)), tip, 0.06, a, a, SOOT);
        }
        if (q >= 1) GravityFxClient.gglow(m, cam, p.u, 0.4, a, YEL);
    }
}
