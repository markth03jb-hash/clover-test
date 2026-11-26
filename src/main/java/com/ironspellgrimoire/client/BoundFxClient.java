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
 * The Bound Demon Grimoire, drawn. The demon side is black with a blue sheen and pure crimson - torn air, smoke that
 * curls like fingers, chains, a clawed hand; the Last Seraph is black-winged, with salt - matte white cubes - and gold: black feathers, heaps and pillars of salt.
 * <ul>
 *   <li>0 a demon cut: a black crescent with a crimson edge, wisps of black peeling off it;</li>
 *   <li>1 Cursed Edge: a tear in the air along the line, black and jagged, crimson glowing in it;</li>
 *   <li>2 the demon stirs: black smoke reaching out in curling fingers, a broken crimson ring;</li>
 *   <li>3 Demon's Reach: a black clawed arm shooting out, its claws closing on what it catches;</li>
 *   <li>4 a bond: a crimson chain between two; 5 a Sinner's Mark: a horned crimson sigil over the head;</li>
 *   <li>6 salt: white crystals growing out of a creature; 7 blood drawn: crimson drops streaming to you;</li>
 *   <li>8 possessed: black flames licking up round your feet in a cracked crimson ring (salt-white when fallen);</li>
 *   <li>10 Judgment Feathers: white feathers flying, sticking where they land; 11 Wing Cleave: a great wing of
 *       feather-blades sweeping across; 12 Salt Lance: a lance of crystal, bursting at its end; 13 the Verdict
 *       foretold: a gold circle with the scales on it; 14 the Verdict: a pillar of salt falling from heaven, crystals
 *       bursting from the ground (Fall of Heaven: the same, far bigger).</li>
 * </ul>
 * Fed by FxPacket shape 104 (server/BoundFx, server/SeraphFx): {@code h} = kind, {@code v.x} = the entity (or -1).
 */
public final class BoundFxClient {
    private BoundFxClient() {}

    private static final double TAU = Math.PI * 2;
    private static final Vec3 UP = new Vec3(0, 1, 0);
    static final float[] BLACK = {0.03F, 0.03F, 0.06F}, SHEEN = {0.25F, 0.35F, 0.9F}, CRIMSON = {0.9F, 0.05F, 0.1F}, EMBER = {1F, 0.3F, 0.25F},
            SALT = {0.96F, 0.96F, 0.94F}, SALTSH = {0.75F, 0.82F, 0.9F}, GOLD = {1F, 0.82F, 0.35F}, WHITE = {1F, 1F, 1F};

    private static final class L {
        FxPacket p;
        double born;
        L(FxPacket p, double born) { this.p = p; this.born = born; }
    }

    private static final List<L> LIVE = new ArrayList<>();

    private static boolean keyed(int k) { return k == 4 || k == 5 || k == 8; }

    public static void spawn(FxPacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        int k = (int) p.h;
        double now = mc.level.getGameTime();
        if (k == 98) { LIVE.removeIf(l -> (int) l.p.v.x == (int) p.v.x && (int) l.p.h == (int) p.a0); return; }
        if (keyed(k)) for (L l : LIVE) if ((int) l.p.h == k && (int) l.p.v.x == (int) p.v.x && (int) l.p.v.y == (int) p.v.y) {
            double age = now - l.born, keep = Math.min(age, 12);
            p.a1 = l.p.a1;
            l.p = p;
            l.born = now - keep;
            l.p.life = (int) (keep + p.life);
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
                case 0 -> cut(vc, m, n, cam, p, age);
                case 1 -> tear(vc, m, n, cam, p, age, now);
                case 2 -> stir(vc, m, n, cam, p, age);
                case 3 -> hand(vc, m, n, cam, p, age);
                case 4 -> chain(m, cam, mc, p, age, now, pt);
                case 5 -> sinMark(m, cam, mc, p, age, now, pt);
                case 6 -> salt(vc, m, n, cam, mc, p, age, pt);
                case 7 -> drain(m, cam, p, age);
                case 8 -> possessed(vc, m, n, cam, mc, p, age, now, pt);
                case 10 -> feather(vc, m, n, cam, p, age);
                case 11 -> wing(vc, m, n, cam, p, age);
                case 12 -> lance(vc, m, n, cam, p, age);
                case 13 -> verdictMark(m, cam, p, age, now);
                case 14 -> pillar(vc, m, n, cam, p, age);
                case 15 -> drift(vc, m, n, cam, p, age);
                default -> LIVE.remove(i);
            }
        }
    }

    // ------------------------------------------------------------------ helpers
    private static double hash(double x) { double s = Math.sin(x * 127.1 + 311.7) * 43758.5453; return s - Math.floor(s); }

    private static Vec3 at(Minecraft mc, int id, float pt) {
        Entity e = mc.level.getEntity(id);
        return e == null ? null : new Vec3(Mth.lerp(pt, e.xo, e.getX()), Mth.lerp(pt, e.yo, e.getY()), Mth.lerp(pt, e.zo, e.getZ()));
    }

    static void sseg(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 a, Vec3 b, double w, float aa, float ab, float[] col) {
        VectorFxClient.sseg(vc, m, n, cam, a, b, w, aa, ab, col);
    }

    /** One grain of salt: a cube (salt grows in cubes), matte and off-white, lit from above - its top brightest, its
     *  sides duller, a little of its own tint so a heap never looks like one flat colour. */
    static void cube(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 c, double h, double yaw, double tilt, double seed, float al) {
        Vec3 x = new Vec3(Math.cos(yaw), 0, Math.sin(yaw)), z = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        Vec3 y = UP.scale(Math.cos(tilt)).add(x.scale(Math.sin(tilt)));
        x = x.scale(Math.cos(tilt)).subtract(UP.scale(Math.sin(tilt)));
        float tint = (float) (0.9 + 0.1 * hash(seed));
        float[] base = {SALT[0] * tint, SALT[1] * tint * 0.985F, SALT[2] * tint * 0.95F};
        Vec3 X = x.scale(h), Y = y.scale(h), Z = z.scale(h);
        Vec3[][] faces = {
                {c.add(Y).subtract(X).subtract(Z), c.add(Y).add(X).subtract(Z), c.add(Y).add(X).add(Z), c.add(Y).subtract(X).add(Z)},
                {c.subtract(Y).subtract(X).add(Z), c.subtract(Y).add(X).add(Z), c.subtract(Y).add(X).subtract(Z), c.subtract(Y).subtract(X).subtract(Z)},
                {c.add(X).subtract(Y).subtract(Z), c.add(X).subtract(Y).add(Z), c.add(X).add(Y).add(Z), c.add(X).add(Y).subtract(Z)},
                {c.subtract(X).subtract(Y).add(Z), c.subtract(X).subtract(Y).subtract(Z), c.subtract(X).add(Y).subtract(Z), c.subtract(X).add(Y).add(Z)},
                {c.add(Z).subtract(X).subtract(Y), c.add(Z).add(X).subtract(Y), c.add(Z).add(X).add(Y), c.add(Z).subtract(X).add(Y)},
                {c.subtract(Z).add(X).subtract(Y), c.subtract(Z).subtract(X).subtract(Y), c.subtract(Z).subtract(X).add(Y), c.subtract(Z).add(X).add(Y)}};
        float[] shade = {1F, 0.55F, 0.82F, 0.74F, 0.78F, 0.7F};
        for (int f = 0; f < 6; f++) {
            float[] col = {base[0] * shade[f], base[1] * shade[f], base[2] * shade[f]};
            Vec3[] q = faces[f];
            FxBase.quad(vc, m, n, cam, q[0], q[1], q[2], q[3], col, al, al, al, al);
            FxBase.quad(vc, m, n, cam, q[3], q[2], q[1], q[0], col, al, al, al, al);
        }
    }

    /** A column of salt from a to b, w across at its root: cubes heaped along it, smaller toward b, a few knocked askew
     *  and some grains crumbling off its sides - rough and matte, never glassy. */
    static void crystal(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 a, Vec3 b, double w, float al) {
        Vec3 d = b.subtract(a);
        double len = d.length();
        if (len < 1e-3) return;
        Vec3 f = d.scale(1 / len);
        Vec3 ax = Math.abs(f.y) > 0.9 ? new Vec3(1, 0, 0) : UP;
        Vec3 sx = f.cross(ax).normalize(), sy = sx.cross(f).normalize();
        double yaw0 = Math.atan2(f.z, f.x), tilt0 = Math.acos(Mth.clamp(f.y, -1, 1));
        double step = Math.max(0.05, w * 1.1);
        int N = Math.min(40, Math.max(1, (int) (len / step)));
        double seed = a.x * 3.1 + a.z * 1.7 + b.y;
        for (int i = 0; i < N; i++) {
            double u = (i + 0.5) / N, h = w * (1 - 0.6 * u) * (0.8 + 0.35 * hash(seed + i));
            Vec3 c = a.add(d.scale(u)).add(sx.scale((hash(seed + i * 3) - 0.5) * w * 0.6)).add(sy.scale((hash(seed + i * 5) - 0.5) * w * 0.6));
            cube(vc, m, n, cam, c, h, yaw0 + hash(seed + i * 7) * 1.5, tilt0 * 0.3 + (hash(seed + i * 11) - 0.5) * 0.6, seed + i, al);
            if (hash(seed + i * 13) < 0.4) {   // a grain beside it
                Vec3 g = c.add(sx.scale(h * 1.3 * (hash(seed + i) < 0.5 ? 1 : -1))).add(sy.scale((hash(seed + i * 17) - 0.5) * h));
                cube(vc, m, n, cam, g, h * 0.4, hash(seed + i * 19) * 3, hash(seed + i * 23), seed + i * 29, al);
            }
        }
    }

    /** A drift of salt dust: pale grey puffs, settling. */
    static void dust(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 c, double r, float al, double seed) {
        for (int i = 0; i < 6; i++) {
            Vec3 p = c.add((hash(seed + i) - 0.5) * r * 1.6, hash(seed + i * 3) * r * 0.8, (hash(seed + i * 5) - 0.5) * r * 1.6);
            FuseFxClient.puff(vc, m, n, cam, p, r * (0.35 + 0.3 * hash(seed + i * 7)), SALTSH, al * 0.45F, 0, seed + i);
        }
    }

    // ------------------------------------------------------------------ 0 a demon cut
    /** c = middle, d = facing, u = the plane's up, r = radius, r2 = thickness (1 = normal), a0 = how far round (rad). */
    private static void cut(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age) {
        double grow = Math.min(1, age / 2.5);
        float a = (float) Mth.clamp((p.life - age) / 4, 0, 1);
        Vec3 f = p.d.normalize();
        Vec3 up = p.u.lengthSqr() < 1e-4 ? UP : p.u.normalize();
        Vec3 side0 = f.cross(up).lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : f.cross(up).normalize();
        double roll = (hash(p.a1 * 7) - 0.5) * 1.3;   // each cut at its own slant, so it never reads as a flat line
        Vec3 up0 = side0.cross(f).normalize();
        Vec3 side = side0.scale(Math.cos(roll)).add(up0.scale(Math.sin(roll)));
        double R = p.r > 0 ? p.r : 2, th = (p.r2 > 0 ? p.r2 : 1) * 0.55 * R / 2, sweep = p.a0 != 0 ? p.a0 : 2.4;
        int N = 22;
        Vec3[] outer = new Vec3[N + 1], inner = new Vec3[N + 1];
        double[] w = new double[N + 1];
        float[] al = new float[N + 1];
        for (int i = 0; i <= N; i++) {
            double u = i / (double) N, an = -sweep / 2 + sweep * u * grow, thick = th * Math.sin(Math.PI * u) * (0.8 + 0.4 * hash(p.a1 + i));
            Vec3 dir = f.scale(Math.cos(an)).add(side.scale(Math.sin(an)));
            outer[i] = p.c.add(dir.scale(R));
            inner[i] = p.c.add(dir.scale(R - thick));
            w[i] = 0.1 * Math.sin(Math.PI * u) + 0.015;
            al[i] = a;
        }
        for (int i = 0; i < N; i++) {   // the black body (both faces), jagged on its inside
            FxBase.quad(vc, m, n, cam, inner[i], inner[i + 1], outer[i + 1], outer[i], BLACK, a, a, a, a);
            FxBase.quad(vc, m, n, cam, outer[i], outer[i + 1], inner[i + 1], inner[i], BLACK, a, a, a, a);
        }
        GravityFxClient.gstrip(m, cam, outer, w, al, CRIMSON);   // the crimson edge
        for (int i = 0; i <= N; i++) w[i] *= 0.4;
        GravityFxClient.gstrip(m, cam, outer, w, al, EMBER);
        for (int i = 0; i < N; i += 3) GravityFxClient.gseg(m, cam, inner[i], inner[i].add(outer[i].subtract(inner[i]).scale(0.5)), 0.02, a * 0.6F, 0, SHEEN);   // a blue sheen in the black
        for (int i = 0; i < 6; i++) {   // wisps peeling off behind it
            int j = (int) (hash(p.a1 + i * 3) * N);
            double q = age / p.life;
            Vec3 b = inner[j].add(up.scale(q * 0.6 * (hash(i) - 0.3))).add(f.scale(-q * 0.8));
            sseg(vc, m, n, cam, inner[j], b, 0.05 * (1 - q), a * 0.7F, 0, BLACK);
        }
    }

    // ------------------------------------------------------------------ 1 Cursed Edge
    private static void tear(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age, double t) {
        double open = Math.min(1, age / 2), close = Mth.clamp((p.life - age) / 5, 0, 1);
        float a = (float) close;
        Vec3 ab = p.u.subtract(p.c);
        Vec3 f = ab.normalize();
        Vec3 view = p.c.add(ab.scale(0.5)).subtract(cam).normalize();
        Vec3 side = f.cross(view).normalize();
        int N = 18;
        Vec3 prevT = null, prevB = null, prevC = null;
        for (int i = 0; i <= N; i++) {
            double u = i / (double) N;
            if (u > open) break;
            double wd = (0.35 * Math.sin(Math.PI * u) * (0.6 + 0.6 * hash(p.a1 + i)) + 0.02) * close;
            double jag = (hash(p.a1 * 3 + i) - 0.5) * 0.2;
            Vec3 c = p.c.add(ab.scale(u)).add(side.scale(jag));
            Vec3 tp = c.add(side.scale(wd)), bt = c.subtract(side.scale(wd * (0.7 + 0.5 * hash(i * 7 + p.a1))));
            if (prevT != null) {
                FxBase.quad(vc, m, n, cam, prevT, tp, bt, prevB, BLACK, a, a, a, a);
                FxBase.quad(vc, m, n, cam, prevB, bt, tp, prevT, BLACK, a, a, a, a);
                double fl = 0.7 + 0.3 * Math.sin(t * 1.3 + i);
                GravityFxClient.gseg(m, cam, prevC, c, 0.08 * fl * close, a, a, CRIMSON);
                GravityFxClient.gseg(m, cam, prevT, tp, 0.03, a * 0.8F, a * 0.8F, EMBER);
                GravityFxClient.gseg(m, cam, prevB, bt, 0.03, a * 0.8F, a * 0.8F, EMBER);
            }
            prevT = tp; prevB = bt; prevC = c;
        }
        for (int i = 0; i < 8; i++) {   // black flakes drifting off it
            double u = hash(p.a1 + i * 11), q = (age / p.life + hash(i)) % 1.0;
            Vec3 c = p.c.add(ab.scale(u)).add(side.scale((hash(i * 3) - 0.5) * 0.8)).add(0, q * 0.8, 0);
            sseg(vc, m, n, cam, c, c.add(0.04, 0.06, 0), 0.05, a * (float) (1 - q), a * (float) (1 - q), BLACK);
        }
    }

    // ------------------------------------------------------------------ 2 the demon stirs
    private static void stir(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age) {
        double q = age / p.life, R = p.r > 0 ? p.r : 2;
        float a = (float) (1 - q);
        for (int i = 0; i < 9; i++) {   // fingers of black smoke curling out
            double an = p.a1 + i * TAU / 9, el = (hash(p.a1 + i) - 0.3) * 1.2, curl = (hash(i * 5 + p.a1) - 0.5) * 2.5;
            Vec3 prev = p.c;
            for (int k = 1; k <= 6; k++) {
                double u = k / 6.0 * Math.min(1, q * 2.2);
                double aj = an + curl * u * u;
                Vec3 nx = p.c.add(Math.cos(aj) * Math.cos(el) * R * u, Math.sin(el) * R * u * 0.6 + u * u * 0.4, Math.sin(aj) * Math.cos(el) * R * u);
                sseg(vc, m, n, cam, prev, nx, 0.16 * (1 - u * 0.8) * R / 2, a * 0.85F, a * 0.85F, BLACK);
                prev = nx;
            }
            GravityFxClient.gglow(m, cam, prev, 0.1, a, CRIMSON);
        }
        for (int i = 0; i < 6; i++) {   // the broken crimson ring
            double a0 = p.a1 * 2 + i * TAU / 6, a1 = a0 + TAU / 6 * 0.7, rr = R * (0.5 + 0.6 * Math.min(1, q * 3));
            Vec3 prev = null;
            for (int k = 0; k <= 5; k++) {
                double an = a0 + (a1 - a0) * k / 5;
                Vec3 pt = p.c.add(Math.cos(an) * rr, -0.8, Math.sin(an) * rr);
                if (prev != null) GravityFxClient.gseg(m, cam, prev, pt, 0.07, a, a, CRIMSON);
                prev = pt;
            }
        }
        if (age < 4) GravityFxClient.gglow(m, cam, p.c, R * 0.8, (float) (1 - age / 4) * 0.6F, CRIMSON);
    }

    // ------------------------------------------------------------------ 3 Demon's Reach
    private static void hand(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age) {
        double out = Math.min(1, age / 3), back = Mth.clamp((age - (p.life - 5)) / 5, 0, 1);
        double ext = out * (1 - back);
        float a = (float) (1 - back * 0.7);
        Vec3 ab = p.u.subtract(p.c);
        Vec3 f = ab.normalize();
        Vec3 side = f.cross(UP).lengthSqr() < 1e-3 ? new Vec3(1, 0, 0) : f.cross(UP).normalize();
        Vec3 upv = side.cross(f).normalize();
        Vec3 tip = p.c.add(ab.scale(ext));
        int N = 10;
        Vec3[] pts = new Vec3[N + 1];
        double[] w = new double[N + 1];
        float[] al = new float[N + 1];
        for (int i = 0; i <= N; i++) {   // the arm: thick at your side, sinewy, a little crooked
            double u = i / (double) N;
            pts[i] = p.c.add(tip.subtract(p.c).scale(u)).add(upv.scale(Math.sin(u * Math.PI) * 0.25)).add(side.scale(Math.sin(u * 7 + p.a1) * 0.06));
            w[i] = 0.3 * (1 - u * 0.5);
            al[i] = a;
        }
        VectorFxClient.sstrip(vc, m, n, cam, pts, w, al, BLACK);
        for (int i = 0; i <= N; i++) w[i] = 0.03;
        GravityFxClient.gstrip(m, cam, pts, w, al, CRIMSON);   // a crimson vein along it
        boolean grabbed = p.a0 > 0;
        double closeK = grabbed ? Mth.clamp((age - 3) / 2, 0, 1) : 0;
        for (int k = 0; k < 4; k++) {   // the claws: spread wide, then closing on what it caught
            double spread = (k - 1.5) * 0.45 * (1 - closeK * 0.8);
            Vec3 base = tip.add(side.scale((k - 1.5) * 0.12));
            Vec3 mid = base.add(f.scale(0.35)).add(side.scale(spread * 0.6)).add(upv.scale(0.15));
            Vec3 end = mid.add(f.scale(0.3 * (1 - closeK) + 0.05)).add(side.scale(spread * 0.3 - (k - 1.5) * 0.12 * closeK)).add(upv.scale(-0.25 * (0.4 + closeK)));
            sseg(vc, m, n, cam, base, mid, 0.07, a, a, BLACK);
            sseg(vc, m, n, cam, mid, end, 0.045, a, a, BLACK);
            GravityFxClient.gglow(m, cam, end, 0.06, a, EMBER);
        }
        GravityFxClient.gglow(m, cam, tip, 0.5, a * 0.4F, CRIMSON);
    }

    // ------------------------------------------------------------------ 4 a bond, 5 a Sinner's Mark
    private static void chain(Matrix4f m, Vec3 cam, Minecraft mc, FxPacket p, double age, double t, float pt) {
        Vec3 A = p.v.x >= 0 ? at(mc, (int) p.v.x, pt) : p.c, B = p.v.y >= 0 ? at(mc, (int) p.v.y, pt) : p.u;
        if (A == null || B == null) return;
        if (p.v.x >= 0) A = A.add(0, 1.0, 0);
        if (p.v.y >= 0) B = B.add(0, 1.0, 0);
        float a = (float) (Mth.clamp(age / 3, 0, 1) * Mth.clamp((p.life - age) / 5, 0, 1));
        Vec3 ab = B.subtract(A);
        double len = ab.length();
        if (len < 0.2) return;
        Vec3 f = ab.scale(1 / len);
        Vec3 side = f.cross(UP).lengthSqr() < 1e-3 ? new Vec3(1, 0, 0) : f.cross(UP).normalize();
        Vec3 upv = side.cross(f).normalize();
        int links = Math.max(2, (int) (len / 0.32));
        for (int i = 0; i < links; i++) {   // links, each turned a quarter from the last
            double u = (i + 0.5) / links;
            Vec3 c = A.add(ab.scale(u)).add(0, -Math.sin(u * Math.PI) * Math.min(0.6, len * 0.08), 0);
            Vec3 nrm = i % 2 == 0 ? upv : side;
            GravityFxClient.bring(m, cam, c, new Vec3[]{f, nrm.cross(f).normalize()}, 0.15, 0.035, CRIMSON, a);
        }
        double s = (t * 0.05) % 1.0;
        GravityFxClient.gglow(m, cam, A.add(ab.scale(s)), 0.15, a, EMBER);
    }

    private static void sinMark(Matrix4f m, Vec3 cam, Minecraft mc, FxPacket p, double age, double t, float pt) {
        Vec3 f = at(mc, (int) p.v.x, pt);
        if (f == null) return;
        Entity e = mc.level.getEntity((int) p.v.x);
        float a = (float) (Mth.clamp(age / 4, 0, 1) * Mth.clamp((p.life - age) / 4, 0, 1));
        Vec3 c = f.add(0, (e == null ? 2 : e.getBbHeight()) + 0.6 + Math.sin(t * 0.1) * 0.05, 0);
        Vec3 d = c.subtract(cam).normalize();
        Vec3 x = d.cross(UP).normalize(), y = x.cross(d).normalize().scale(-1);
        double s = 0.32;
        GravityFxClient.ring(m, cam, c, d, s * 0.55, s * 0.08, CRIMSON, a);
        for (int k = -1; k <= 1; k += 2) {   // the horns, curving up and out
            Vec3 prev = c.add(x.scale(k * s * 0.35)).add(y.scale(s * 0.35));
            for (int i = 1; i <= 5; i++) {
                double u = i / 5.0;
                Vec3 nx = c.add(x.scale(k * s * (0.35 + 0.35 * u - 0.1 * u * u))).add(y.scale(s * (0.35 + 0.85 * u)));
                GravityFxClient.gseg(m, cam, prev, nx, s * 0.09 * (1 - u * 0.8), a, a, CRIMSON);
                prev = nx;
            }
        }
        GravityFxClient.gseg(m, cam, c.add(x.scale(-s * 0.2)), c.add(x.scale(s * 0.2)), s * 0.1, a, a, EMBER);   // the slit of an eye
        GravityFxClient.gglow(m, cam, c, s, a * 0.3F, CRIMSON);
    }

    // ------------------------------------------------------------------ 6 salt, 7 blood drawn
    private static void salt(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Minecraft mc, FxPacket p, double age, float pt) {
        Vec3 base = p.c;
        double hgt = 1;
        if (p.v.x >= 0) { Vec3 f = at(mc, (int) p.v.x, pt); if (f == null) return; Entity e = mc.level.getEntity((int) p.v.x); hgt = e.getBbHeight(); base = f; }
        double grow = FxBase.smooth(Math.min(1, age / 5));
        float a = (float) Mth.clamp((p.life - age) / 8, 0, 1);
        int N = 4 + (int) (p.r * 2);
        for (int i = 0; i < N; i++) {   // crystals sprouting out of it at angles
            double s = p.a1 + i * 1.7, an = s * 2.3, el = 0.3 + hash(s) * 1.0;
            Vec3 root = base.add(Math.cos(an) * 0.25, hgt * (0.2 + 0.6 * hash(s * 3)), Math.sin(an) * 0.25);
            Vec3 dir = new Vec3(Math.cos(an) * Math.cos(el), Math.sin(el), Math.sin(an) * Math.cos(el));
            double len = (0.3 + 0.35 * hash(s * 5)) * (0.6 + 0.25 * p.r) * grow;
            crystal(vc, m, n, cam, root, root.add(dir.scale(len)), 0.06 + 0.03 * p.r, a);
        }
    }

    private static void drain(Matrix4f m, Vec3 cam, FxPacket p, double age) {
        Vec3 ab = p.u.subtract(p.c);
        for (int i = 0; i < 6; i++) {
            double u = ((age / p.life) * 1.3 - i * 0.12);
            if (u < 0 || u > 1) continue;
            Vec3 c = p.c.add(ab.scale(u)).add(0, Math.sin(u * Math.PI) * 0.6, 0);
            GravityFxClient.gglow(m, cam, c, 0.1, 1F - (float) u * 0.4F, CRIMSON);
            GravityFxClient.gseg(m, cam, c, c.subtract(ab.normalize().scale(0.25)), 0.05, 1F, 0, CRIMSON);
        }
    }

    // ------------------------------------------------------------------ 8 possessed
    private static void possessed(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Minecraft mc, FxPacket p, double age, double t, float pt) {
        Vec3 f = at(mc, (int) p.v.x, pt);
        if (f == null) return;
        boolean fallen = p.a0 > 0;
        float a = (float) Mth.clamp(age / 4, 0, 1) * (float) Mth.clamp((p.life - age) / 6, 0, 1);
        Vec3 g = f.add(0, 0.05, 0);
        double R = 1.1;
        GravityFxClient.ring(m, cam, g, UP, R, 0.05, fallen ? SALT : CRIMSON, a);
        for (int i = 0; i < 7; i++) {   // cracks
            double an = p.a1 + i * TAU / 7;
            Vec3 o = new Vec3(Math.cos(an), 0, Math.sin(an));
            GravityFxClient.gseg(m, cam, g.add(o.scale(R * 0.4)), g.add(o.scale(R * (1 + 0.2 * hash(i)))).add(new Vec3(-o.z, 0, o.x).scale(0.15 * (hash(i * 3) - 0.5))), 0.03, a, a, fallen ? SALTSH : CRIMSON);
        }
        for (int i = 0; i < 12; i++) {   // flames licking up, black (or salt-white when fallen)
            double an = p.a1 + i * TAU / 12 + t * 0.01, fl = 0.5 + 0.5 * Math.sin(t * (0.5 + hash(i) * 0.4) + i * 1.9);
            Vec3 b = g.add(Math.cos(an) * R, 0, Math.sin(an) * R);
            Vec3 tip = b.add(Math.sin(t * 0.2 + i) * 0.1, 0.3 + 0.5 * fl, Math.cos(t * 0.17 + i) * 0.1);
            if (fallen) GravityFxClient.gseg(m, cam, b, tip, 0.08, a, 0, SALT);
            else { sseg(vc, m, n, cam, b, tip, 0.1 * (0.6 + 0.4 * fl), a * 0.9F, 0, BLACK); GravityFxClient.gseg(m, cam, b, b.add(tip.subtract(b).scale(0.35)), 0.05, a, 0, CRIMSON); }
        }
    }

    // ------------------------------------------------------------------ 10 Judgment Feathers
    /** A feather from c to u over its life (then stuck): a quill and a vane of white, slightly curled. */
    static void featherAt(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 base, Vec3 dir, double len, double roll, float a, boolean gold) {
        Vec3 f = dir.normalize();
        Vec3 ax = Math.abs(f.y) > 0.9 ? new Vec3(1, 0, 0) : UP;
        Vec3 x = f.cross(ax).normalize(), y = x.cross(f).normalize();
        Vec3 side = x.scale(Math.cos(roll)).add(y.scale(Math.sin(roll)));
        Vec3 tip = base.add(f.scale(len));
        int N = 6;
        Vec3 prevL = base, prevR = base, prevC = base;
        for (int i = 1; i <= N; i++) {
            double u = i / (double) N, wd = len * 0.18 * Math.sin(Math.PI * Math.min(1, u * 1.15)) * (u < 0.15 ? 0.3 : 1);
            Vec3 c = base.add(f.scale(len * u));
            Vec3 l = c.add(side.scale(wd)).subtract(f.scale(len * 0.08)), r = c.subtract(side.scale(wd * 0.8)).subtract(f.scale(len * 0.08));
            float[] col = gold && i == N ? GOLD : BLACK;
            FxBase.quad(vc, m, n, cam, prevC, c, l, prevL, col, a, a, a, a);
            FxBase.quad(vc, m, n, cam, prevL, l, c, prevC, col, a, a, a, a);
            FxBase.quad(vc, m, n, cam, prevC, prevR, r, c, BLACK, a, a, a, a);
            FxBase.quad(vc, m, n, cam, c, r, prevR, prevC, BLACK, a, a, a, a);
            GravityFxClient.gseg(m, cam, prevL, l, len * 0.012, a * 0.7F, a * 0.7F, SALT);   // a thin salt-white edge, so black reads on dark ground
            GravityFxClient.gseg(m, cam, prevR, r, len * 0.01, a * 0.5F, a * 0.5F, SALT);
            prevL = l; prevR = r; prevC = c;
        }
        sseg(vc, m, n, cam, base.subtract(f.scale(len * 0.15)), tip, len * 0.03, a, a, gold ? GOLD : SALTSH);   // the quill
    }

    private static void feather(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age) {
        double fly = Math.max(1, p.r2 > 0 ? p.r2 : 4), q = Math.min(1, age / fly);
        Vec3 ab = p.u.subtract(p.c);
        Vec3 pos = p.c.add(ab.scale(q));
        float a = (float) Mth.clamp((p.life - age) / 8, 0, 1);
        boolean gold = p.a0 > 0;
        featherAt(vc, m, n, cam, pos.subtract(ab.normalize().scale(0.6)), ab, 0.7, p.a1 + (q < 1 ? age * 0.4 : 0), a, gold);
        if (q < 1) sseg(vc, m, n, cam, p.c.add(ab.scale(Math.max(0, q - 0.25))), pos, 0.03, 0, a * 0.6F, BLACK);
        else if (age - fly < 6) dust(vc, m, n, cam, p.u, 0.25, (float) (1 - (age - fly) / 6), p.a1);   // salt shaken off where it struck
    }

    // ------------------------------------------------------------------ 11 Wing Cleave
    private static void wing(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age) {
        double sweep = Math.min(1, age / 5);
        float a = (float) Mth.clamp((p.life - age) / 6, 0, 1);
        Vec3 f = new Vec3(p.d.x, 0, p.d.z).normalize();
        Vec3 side = new Vec3(-f.z, 0, f.x);
        double R = p.r > 0 ? p.r : 6;
        int N = 16;
        for (int i = 0; i < N; i++) {   // feather-blades fanned out from the shoulder, sweeping from one side round to the other
            double u = i / (double) (N - 1);
            if (u > sweep + 0.05) continue;
            double an = -1.9 + 3.8 * u;
            Vec3 dir = f.scale(Math.cos(an)).add(side.scale(Math.sin(an))).add(0, 0.15 - 0.3 * u, 0).normalize();
            double len = R * (0.55 + 0.45 * Math.sin(Math.PI * u));
            float fa = a * (float) Mth.clamp(1 - (sweep - u) * 1.2, 0.35, 1);
            featherAt(vc, m, n, cam, p.c.add(dir.scale(R * 0.15)), dir, len, Math.PI / 2, fa, i % 5 == 4);
        }
        if (age < 6) {
            double an = -1.9 + 3.8 * sweep;
            Vec3 lead = p.c.add(f.scale(Math.cos(an) * R)).add(side.scale(Math.sin(an) * R));
            sseg(vc, m, n, cam, p.c, lead, 0.12, 0, (float) (1 - age / 6) * 0.8F, BLACK);
        }
    }

    // ------------------------------------------------------------------ 12 Salt Lance
    private static void lance(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age) {
        double shoot = Math.min(1, age / 2);
        float a = (float) Mth.clamp((p.life - age) / 5, 0, 1);
        Vec3 ab = p.u.subtract(p.c);
        Vec3 end = p.c.add(ab.scale(shoot));
        double w = p.r > 0 ? p.r : 0.3;
        crystal(vc, m, n, cam, p.c, end, w, a);
        if (shoot >= 1) {   // it bursts where it strikes: crystals thrown out in a crown
            double q = (age - 2) / Math.max(1, p.life - 2);
            Vec3 f = ab.normalize();
            for (int i = 0; i < 7; i++) {
                double an = p.a1 + i * TAU / 7;
                Vec3 ax = Math.abs(f.y) > 0.9 ? new Vec3(1, 0, 0) : UP;
                Vec3 x = f.cross(ax).normalize(), y = x.cross(f).normalize();
                Vec3 dir = x.scale(Math.cos(an)).add(y.scale(Math.sin(an))).add(f.scale(-0.6)).normalize();
                crystal(vc, m, n, cam, p.u, p.u.add(dir.scale(0.5 + 0.4 * Math.min(1, q * 4))), w * 0.5, a);
            }
            dust(vc, m, n, cam, p.u, 0.6, a * (float) Mth.clamp(1 - q, 0, 1), p.a1);
        }
    }

    /** 15: a black feather drifting down, rocking side to side as it falls. */
    private static void drift(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age) {
        float a = (float) (Mth.clamp(age / 3, 0, 1) * Mth.clamp((p.life - age) / 6, 0, 1));
        double sway = Math.sin(age * 0.25 + p.a1);
        Vec3 c = p.c.add(sway * 0.35, -age * 0.06, Math.cos(age * 0.2 + p.a1) * 0.15).add(p.u.scale(age));
        featherAt(vc, m, n, cam, c, new Vec3(sway * 0.6, -1, 0.3), 0.45, p.a1 + sway, a, false);
    }

    // ------------------------------------------------------------------ 13 the Verdict foretold, 14 the pillar
    private static void verdictMark(Matrix4f m, Vec3 cam, FxPacket p, double age, double t) {
        double q = Mth.clamp(age / Math.max(1, p.life), 0, 1), R = p.r > 0 ? p.r : 2.4;
        float a = (float) Mth.clamp(age / 3, 0, 1);
        Vec3 g = p.c.add(0, 0.06, 0);
        GravityFxClient.ring(m, cam, g, UP, R, 0.08, GOLD, a);
        GravityFxClient.ring(m, cam, g, UP, R * (1 - q), 0.05, SALT, a);   // closing in to the moment
        double tilt = Math.sin(t * 0.15) * 0.25;   // the scales, swaying
        Vec3 beamL = g.add(-R * 0.45, 0, 0).add(0, tilt * R * 0.3, 0), beamR = g.add(R * 0.45, 0, 0).add(0, -tilt * R * 0.3, 0);
        Vec3 top = g.add(0, 0.02, R * 0.45);
        GravityFxClient.gseg(m, cam, g.add(0, 0.02, -R * 0.5), top, 0.05, a, a, GOLD);
        GravityFxClient.gseg(m, cam, new Vec3(beamL.x, g.y + 0.02, beamL.z + tilt * R * 0.3), new Vec3(beamR.x, g.y + 0.02, beamR.z - tilt * R * 0.3), 0.05, a, a, GOLD);
        for (int k = -1; k <= 1; k += 2) {
            Vec3 pan = g.add(k * R * 0.45, 0.02, -k * tilt * R * 0.3 - R * 0.2);
            GravityFxClient.ring(m, cam, pan, UP, R * 0.15, 0.04, GOLD, a);
        }
        GravityFxClient.gseg(m, cam, g.add(0, 20, 0), g, 0.03 + 0.12 * q, a * 0.1F, a * (float) q * 0.6F, SALT);
    }

    private static void pillar(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age) {
        double R = p.r > 0 ? p.r : 1.3, H = 16 + R * 2;
        double drop = Math.min(1, age / 3);
        float a = (float) Mth.clamp((p.life - age) / 10, 0, 1);
        Vec3 g = p.c.add(0, 0.05, 0);
        Vec3 bottom = g.add(0, H * (1 - drop), 0);
        crystal(vc, m, n, cam, bottom, bottom.add(0, H, 0), R * 0.6, a);   // the pillar of salt, a great heap of cubes
        if (drop >= 1) {
            double q = (age - 3) / Math.max(1, p.life - 3);
            GravityFxClient.ring(m, cam, g, UP, R * (0.5 + 2.2 * Math.min(1, q * 3)), 0.15 * R, SALT, a * (float) (1 - q));
            GravityFxClient.ring(m, cam, g, UP, R * (0.3 + 1.6 * Math.min(1, q * 3)), 0.08 * R, GOLD, a * (float) (1 - q));
            dust(vc, m, n, cam, g.add(0, 0.3, 0), R * 1.2, a * (float) (1 - q), p.a1);   // salt thrown up in a cloud
            int N = 6 + (int) (R * 4);
            for (int i = 0; i < N; i++) {   // crystals bursting up out of the ground round it
                double s = p.a1 + i * 1.3, an = s * 2.1, rr = R * (0.6 + 1.4 * hash(s));
                Vec3 root = g.add(Math.cos(an) * rr, -0.2, Math.sin(an) * rr);
                Vec3 dir = new Vec3(Math.cos(an) * 0.4, 1, Math.sin(an) * 0.4).normalize();
                double len = R * (0.5 + 0.9 * hash(s * 3)) * FxBase.smooth(Math.min(1, (age - 3) / 3));
                crystal(vc, m, n, cam, root, root.add(dir.scale(len)), 0.1 + 0.08 * R * hash(s * 7), a);
            }
        }
    }
}
