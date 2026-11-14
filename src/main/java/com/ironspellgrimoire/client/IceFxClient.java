package com.ironspellgrimoire.client;

import com.ironspellgrimoire.network.FxPacket;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * The Scarlet Moon's ice (the Blood Grimoire's mode): under the white moon your blood freezes white. Drawn as clear,
 * faceted crystal - see-through in the middle, bright at the edges like glass, with a hard white highlight - and
 * outlined in the same cold white light as the aura. Frost spreads over the ground in ferns, spires burst up and
 * shatter into shards that skitter and settle, and snow-mist hangs where the cold struck.
 * <p>Fed by FxPacket shape 82, {@code h} = kind:
 * <ul>
 *   <li>0 spire: c = base, d = direction, r = length, r2 = width, sweep = ticks to grow (shatters at the end)</li>
 *   <li>1 shards: c, d = mean velocity, r = spread, sweep = how many, a0 = size</li>
 *   <li>2 crescent: c, d = forward, u = up, r = radius, a0 = distance, speed = ticks of flight</li>
 *   <li>3 lance: c to u, r = length, speed = ticks of flight</li>
 *   <li>4 encase: v.x = entity (shatters at the end, or early by kind 99)</li>
 *   <li>5 frost field: c = ground, r = radius, speed = ticks to spread</li>
 *   <li>6 tether: v.x = caster, v.y = target</li>
 *   <li>7 orbit: v.x = entity, r = radius, sweep = shards</li>
 *   <li>8 dome: v.x = entity, r = radius (kind 99 shatters it)</li>
 *   <li>9 glacier: c = ground, d = forward, r = half width, r2 = height, speed = blocks per tick</li>
 *   <li>10 comet: c = from, u = impact, r = size, speed = ticks of fall</li>
 *   <li>11 mist: c, r</li>
 *   <li>22 Absolute Zero: c = ground at the caster, r = radius, sweep = ticks until everything freezes</li>
 *   <li>23 Moonfall: c = where the moon hangs, u = where it strikes, r = moon radius, sweep = ticks it hangs, speed = ticks to impact</li>
 *   <li>99 end the effect with this id early</li>
 * </ul>
 */
public final class IceFxClient {
    private IceFxClient() {}

    private static final double TAU = Math.PI * 2;
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final float[] BODY = {0.6F, 0.73F, 0.9F}, DEEP = {0.3F, 0.42F, 0.62F};
    static final float[] W_CORE = {1.0F, 1.0F, 1.0F}, W_SOFT = {0.82F, 0.88F, 1.0F}, W_ICE = {0.62F, 0.8F, 1.0F};

    private static final class L {
        final FxPacket p;
        final double born;
        double killAt = -1, last;
        boolean done, burst;
        L(FxPacket p, double born) { this.p = p; this.born = born; this.last = born; }
    }

    private static final class Shard {
        Vec3 pos, vel, axis;
        final float r;
        final double born, life;
        double spin;
        boolean rest;
        Shard(Vec3 pos, Vec3 vel, float r, double born, double life, Vec3 axis) { this.pos = pos; this.vel = vel; this.r = r; this.born = born; this.life = life; this.axis = axis; }
    }

    /** A spire grown by another effect (the glacier, the comet, the crescent's trail). */
    private static final class Spire {
        final Vec3 c, d;
        final float len, w;
        final double born, life, grow;
        final int seed;
        Spire(Vec3 c, Vec3 d, float len, float w, double born, double life, double grow, int seed) { this.c = c; this.d = d; this.len = len; this.w = w; this.born = born; this.life = life; this.grow = grow; this.seed = seed; }
    }

    private static final class Mist {
        Vec3 c;
        final Vec3 vel;
        final float r;
        final double born, life;
        Mist(Vec3 c, Vec3 vel, float r, double born, double life) { this.c = c; this.vel = vel; this.r = r; this.born = born; this.life = life; }
    }

    private static final List<L> LIVE = new ArrayList<>();
    private static final List<Shard> SHARDS = new ArrayList<>();
    private static final List<Spire> SPIRES = new ArrayList<>();
    private static final List<Mist> MISTS = new ArrayList<>();
    private static double lastNow = -1;
    private static int seedN = 1;

    // ================================================================== feed
    public static void spawn(FxPacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        double now = mc.level.getGameTime();
        int k = (int) p.h;
        if (k == 99) { for (L l : LIVE) if (l.p.id == p.id && l.killAt < 0) l.killAt = now; return; }
        if (k == 1) { shards(p.c, p.d, p.r, Math.max(1, p.sweep), p.a0 > 0 ? p.a0 : 0.08F, now); return; }
        if (k == 11) { mist(p.c, Vec3.ZERO, p.r, now, Math.max(10, p.life)); return; }
        if (k == 13) {   // an ice hit: the blow breaks ice off it - shards, a breath of frost - and a frost star flares
            Vec3 d = p.d.lengthSqr() < 1e-6 ? Vec3.ZERO : p.d.normalize();
            float s = p.a0 > 0 ? p.a0 : 1F;
            shards(p.c, d.scale(0.12).add(0, 0.06, 0), 0.1F, (int) (3 + 4 * s), 0.05F + 0.02F * s, now);
            mist(p.c, d.scale(0.02), 0.35F * s, now, 12);
        }
        if (k == 0) { spire(p.c, p.d, p.r, p.r2, now, Math.max(10, p.life), Math.max(1, p.sweep)); mist(p.c.add(0, 0.2, 0), new Vec3(0, 0.02, 0), p.r2 * 2.5F, now, 16); return; }
        if (LIVE.size() > 300) LIVE.remove(0);
        LIVE.add(new L(p, now));
    }

    static void clear() { LIVE.clear(); SHARDS.clear(); SPIRES.clear(); MISTS.clear(); }

    private static void shards(Vec3 c, Vec3 v, float spread, int n, float size, double now) {
        java.util.concurrent.ThreadLocalRandom r = java.util.concurrent.ThreadLocalRandom.current();
        for (int i = 0; i < n; i++) {
            Vec3 jit = new Vec3(r.nextGaussian(), r.nextGaussian() * 0.5 + 0.45, r.nextGaussian()).scale(spread);
            Vec3 ax = new Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian());
            if (SHARDS.size() > 220) SHARDS.remove(0);
            SHARDS.add(new Shard(c.add(r.nextGaussian() * 0.12, r.nextGaussian() * 0.12, r.nextGaussian() * 0.12), v.add(jit), size * (0.5F + r.nextFloat()), now, 40 + r.nextInt(25), ax.lengthSqr() < 1e-6 ? UP : ax.normalize()));
        }
    }

    private static void spire(Vec3 c, Vec3 d, float len, float w, double now, double life, double grow) {
        if (SPIRES.size() > 140) SPIRES.remove(0);
        SPIRES.add(new Spire(c, d.lengthSqr() < 1e-6 ? UP : d.normalize(), len, w, now, life, grow, seedN++ * 7919));
    }

    private static void mist(Vec3 c, Vec3 vel, float r, double now, double life) {
        if (MISTS.size() > 45) MISTS.remove(0);
        MISTS.add(new Mist(c, vel, r, now, life));
    }

    // ================================================================== frame
    static void draw(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, double now, float pt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { clear(); return; }
        double dt = lastNow < 0 ? 0 : Mth.clamp(now - lastNow, 0, 3);
        lastNow = now;
        if (LIVE.isEmpty() && SHARDS.isEmpty() && SPIRES.isEmpty() && MISTS.isEmpty()) return;

        List<L> live = new ArrayList<>(LIVE);   // effects may add to LIVE while drawing
        for (L l : live) {
            double age = now - l.born;
            double end = l.killAt >= 0 ? l.killAt + 2 : l.born + l.p.life;
            if (now > end || l.done) { LIVE.remove(l); continue; }
            boolean tick = Math.floor(now) > Math.floor(l.last);
            if (tick) l.last = now;
            switch ((int) l.p.h) {
                case 2 -> crescent(vc, m, n, cam, mc, l, age, now, tick);
                case 3 -> lance(vc, m, n, cam, l, age, now);
                case 4 -> encase(vc, m, n, cam, mc, l, age, now, pt);
                case 5 -> frost(vc, m, n, cam, l.p, age, now);
                case 6 -> tether(vc, m, n, cam, mc, l, age, now, pt, tick);
                case 7 -> orbit(vc, m, n, cam, mc, l, age, now, pt);
                case 8 -> dome(vc, m, n, cam, mc, l, age, now, pt);
                case 9 -> glacier(vc, m, n, cam, mc, l, age, now, tick);
                case 10 -> comet(vc, m, n, cam, mc, l, age, now, tick);
                case 12 -> throne(vc, m, n, cam, mc, l, age, now, pt);
                case 13 -> iceHit(m, cam, l, age);
                case 14 -> sentry(vc, m, n, cam, l, age, now);
                case 15 -> prison(vc, m, n, cam, l, age, now);
                case 17 -> wall(vc, m, n, cam, l, age, now);
                case 18 -> veil(vc, m, n, cam, mc, l, age, now, pt);
                case 20 -> shock(vc, m, n, cam, l, age, now);
                case 21 -> statue(vc, m, n, cam, mc, l, age, now);
                case 22 -> absoluteZero(vc, m, n, cam, mc, l, age, now, tick);
                case 23 -> moon(vc, m, n, cam, mc, l, age, now, tick);
                default -> l.done = true;
            }
        }
        Iterator<Spire> si = SPIRES.iterator();
        while (si.hasNext()) {
            Spire s = si.next();
            double age = now - s.born;
            if (age < 0) continue;   // a spire queued to rise a moment later (the waves round a crater)
            if (age > s.life) {   // it shatters
                si.remove();
                shards(s.c.add(s.d.scale(s.len * 0.5)), new Vec3(0, 0.05, 0), 0.12F, Math.min(14, 4 + (int) (s.len * 3)), Math.max(0.06F, s.w * 0.4F), now);
                continue;
            }
            double g = FxBase.backOut(age / s.grow);
            double fade = Mth.clamp((s.life - age) / 6.0, 0, 1);
            crystal(vc, m, n, cam, s.c, s.d, s.len * g, s.w * Math.min(1, g), 6, s.seed, (float) (0.35 + 0.65 * fade), true);
        }
        Iterator<Shard> hi = SHARDS.iterator();
        while (hi.hasNext()) {
            Shard s = hi.next();
            double age = now - s.born;
            if (age > s.life) { hi.remove(); continue; }
            if (dt > 0 && !s.rest) {
                s.vel = s.vel.scale(Math.pow(0.98, dt)).add(0, -0.05 * dt, 0);
                s.pos = s.pos.add(s.vel.scale(dt));
                s.spin += dt * 0.5;
                double gy = ground(mc, s.pos);
                if (s.pos.y <= gy + s.r * 0.3) {   // it hits the ground: one skip, then it lies there
                    if (s.vel.y < -0.12) s.vel = new Vec3(s.vel.x * 0.5, -s.vel.y * 0.3, s.vel.z * 0.5);
                    else { s.rest = true; s.vel = Vec3.ZERO; }
                    s.pos = new Vec3(s.pos.x, gy + s.r * 0.3, s.pos.z);
                }
            }
            float a = (float) Mth.clamp((s.life - age) / 10.0, 0, 1);
            Vec3 ax = s.rest ? new Vec3(s.axis.x, s.axis.y * 0.15, s.axis.z).normalize() : rotate(s.axis, s.spin);
            crystal(vc, m, n, cam, s.pos.subtract(ax.scale(s.r)), ax, s.r * 2.2, s.r * 0.55, 4, (int) (s.born * 31), 0.9F * a, false);
        }
        Iterator<Mist> mi = MISTS.iterator();
        while (mi.hasNext()) {
            Mist ms = mi.next();
            double age = now - ms.born;
            if (age > ms.life) { mi.remove(); continue; }
            if (dt > 0) ms.c = ms.c.add(ms.vel.scale(dt * Math.max(0, 1 - age / ms.life)));
            drawMist(vc, m, n, cam, ms, age);
        }
    }

    private static Vec3 rotate(Vec3 a, double t) {
        double c = Math.cos(t), s = Math.sin(t);
        return new Vec3(a.x * c - a.z * s, a.y * c + a.x * s * 0.5, a.z * c + a.x * s).normalize();
    }

    // ================================================================== the look of ice
    /** Clear ice: pale blue, see-through face-on, bright and opaque at grazing angles, a hard white highlight. */
    private static void iv(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 w, Vec3 nrm, float a, float tone) {
        Vec3 view = w.subtract(cam);
        double l = view.length();
        view = l < 1e-6 ? new Vec3(0, 0, 1) : view.scale(1 / l);
        float lam = (float) Math.max(0, nrm.dot(FxBase.LIGHT));
        Vec3 refl = view.subtract(nrm.scale(2 * view.dot(nrm)));
        float spec = (float) Math.pow(Math.max(0, refl.dot(FxBase.LIGHT)), 36);
        float fres = (float) Math.pow(1 - Math.abs(nrm.dot(view)), 2);
        float k = (0.45F + 0.55F * lam) * tone;
        float[] c = new float[3];
        for (int i = 0; i < 3; i++) c[i] = Math.min(1F, DEEP[i] + (BODY[i] - DEEP[i]) * k + spec + fres * 0.45F * W_SOFT[i]);
        FxBase.vtx(vc, m, n, cam, w, c, a * (0.42F + 0.5F * fres + 0.3F * spec));
    }

    private static void q(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 a, Vec3 b, Vec3 c, Vec3 d, Vec3 nrm, float al, float tone) {
        iv(vc, m, n, cam, a, nrm, al, tone); iv(vc, m, n, cam, b, nrm, al, tone); iv(vc, m, n, cam, c, nrm, al, tone); iv(vc, m, n, cam, d, nrm, al, tone);
    }

    private static Vec3[] frame(Vec3 axis) {
        Vec3 e1 = Math.abs(axis.y) > 0.9 ? new Vec3(1, 0, 0) : UP;
        e1 = e1.subtract(axis.scale(e1.dot(axis))).normalize();
        return new Vec3[]{e1, axis.cross(e1)};
    }

    /**
     * One crystal: a faceted prism rising from {@code c} along {@code ax}, swelling a little to a shoulder and then
     * cut to a point - every face flat-shaded so it catches the light like cut ice - with light on its ridges.
     */
    static void crystal(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 c, Vec3 ax, double len, double w, int sides, int seed, float a, boolean glow) {
        if (len < 0.01 || a <= 0.01F || c.distanceToSqr(cam) > 56 * 56) return;
        Vec3[] f = frame(ax);
        Vec3 sh = c.add(ax.scale(len * 0.72)), tip = c.add(ax.scale(len));
        Vec3[] b = new Vec3[sides], s = new Vec3[sides];
        for (int i = 0; i < sides; i++) {
            double an = i * TAU / sides + seed * 0.37;
            Vec3 o = f[0].scale(Math.cos(an)).add(f[1].scale(Math.sin(an)));
            b[i] = c.add(o.scale(w * 0.8));
            s[i] = sh.add(o.scale(w));
        }
        for (int i = 0; i < sides; i++) {
            int j = (i + 1) % sides;
            Vec3 side = s[i].subtract(b[i]).cross(b[j].subtract(b[i])).normalize();
            if (side.dot(b[i].subtract(c)) < 0) side = side.scale(-1);
            q(vc, m, n, cam, b[i], b[j], s[j], s[i], side, a, 1F);
            Vec3 top = tip.subtract(s[i]).cross(s[j].subtract(s[i])).normalize();
            if (top.dot(s[i].subtract(sh)) < 0) top = top.scale(-1);
            q(vc, m, n, cam, s[i], s[j], tip, tip, top, a, 1.1F);
        }
        if (glow) {
            float ga = a * 0.55F;
            for (int i = 0; i < sides; i += 2) gseg(m, cam, b[i], s[i], w * 0.12, ga * 0.6F, ga, W_ICE);
            for (int i = 0; i < sides; i += 2) gseg(m, cam, s[i], tip, w * 0.1, ga, ga * 1.2F, W_SOFT);
            gglow(m, cam, tip, w * 0.9, ga * 0.5F, W_SOFT);
        }
    }

    /** A clear tube (ice threads, the tether). */
    private static void tube(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3[] c, double[] rad, int sides, float a) {
        int k = c.length;
        if (k < 2) return;
        Vec3[][] ring = new Vec3[k][sides], nr = new Vec3[k][sides];
        Vec3 ref = null;
        for (int i = 0; i < k; i++) {
            Vec3 t = c[Math.min(k - 1, i + 1)].subtract(c[Math.max(0, i - 1)]);
            if (t.lengthSqr() < 1e-10) t = new Vec3(0, 0, 1);
            t = t.normalize();
            Vec3 e1 = ref == null ? frame(t)[0] : ref.subtract(t.scale(ref.dot(t)));
            e1 = e1.lengthSqr() < 1e-8 ? frame(t)[0] : e1.normalize();
            ref = e1;
            Vec3 e2 = t.cross(e1);
            for (int s = 0; s < sides; s++) {
                double an = s * TAU / sides;
                Vec3 o = e1.scale(Math.cos(an)).add(e2.scale(Math.sin(an)));
                nr[i][s] = o;
                ring[i][s] = c[i].add(o.scale(rad[i]));
            }
        }
        for (int i = 0; i < k - 1; i++) for (int s = 0; s < sides; s++) {
            int s2 = (s + 1) % sides;
            iv(vc, m, n, cam, ring[i][s], nr[i][s], a, 1F); iv(vc, m, n, cam, ring[i + 1][s], nr[i + 1][s], a, 1F);
            iv(vc, m, n, cam, ring[i + 1][s2], nr[i + 1][s2], a, 1F); iv(vc, m, n, cam, ring[i][s2], nr[i][s2], a, 1F);
        }
    }

    /** Snow-mist: a soft white cloud, never a glow. */
    private static void drawMist(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Mist ms, double age) {
        Vec3 view = ms.c.subtract(cam);
        double dist = view.length();
        if (dist < 1.2 || dist > 48) return;
        double k = age / ms.life;
        float a = (float) (Math.min(1, age / 2.0) * (1 - k) * (1 - k)) * 0.32F;
        double R = ms.r * (0.6 + 1.0 * Math.sqrt(k));
        Vec3[] f = frame(view.scale(1 / dist));
        float[] col = {0.9F, 0.94F, 1.0F};
        int seg = 12;
        for (int lay = 0; lay < 2; lay++) {
            Vec3 c = ms.c.add(f[0].scale(R * 0.25 * (lay == 0 ? 1 : -1))).add(f[1].scale(R * 0.12 * (lay == 0 ? -1 : 1)));
            double rr = R * (lay == 0 ? 1 : 0.7);
            for (int i = 0; i < seg; i++) {
                double a0 = i * TAU / seg, a1 = (i + 1) * TAU / seg;
                Vec3 p0 = c.add(f[0].scale(Math.cos(a0) * rr)).add(f[1].scale(Math.sin(a0) * rr)), p1 = c.add(f[0].scale(Math.cos(a1) * rr)).add(f[1].scale(Math.sin(a1) * rr));
                FxBase.vtx(vc, m, n, cam, c, col, a); FxBase.vtx(vc, m, n, cam, c, col, a);
                FxBase.vtx(vc, m, n, cam, p1, col, 0); FxBase.vtx(vc, m, n, cam, p0, col, 0);
            }
        }
    }

    // ------------------------------------------------------------------ glow (additive, the aura's white)
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
        int seg = 14;
        for (int i = 0; i < seg; i++) {
            double a0 = i * TAU / seg, a1 = (i + 1) * TAU / seg;
            Vec3 p0 = c.add(f[0].scale(Math.cos(a0) * r)).add(f[1].scale(Math.sin(a0) * r)), p1 = c.add(f[0].scale(Math.cos(a1) * r)).add(f[1].scale(Math.sin(a1) * r));
            FxBase.gquad(m, cam, c, c, p1, p0, col, a, a, 0, 0);
        }
    }

    private static void gring(Matrix4f m, Vec3 cam, Vec3 c, double r, double w, float a, float[] col) {
        int seg = 40;
        for (int i = 0; i < seg; i++) {
            double a0 = i * TAU / seg, a1 = (i + 1) * TAU / seg;
            Vec3 d0 = new Vec3(Math.cos(a0), 0, Math.sin(a0)), d1 = new Vec3(Math.cos(a1), 0, Math.sin(a1));
            Vec3 i0 = c.add(d0.scale(Math.max(0, r - w))), i1 = c.add(d1.scale(Math.max(0, r - w))), m0 = c.add(d0.scale(r)), m1 = c.add(d1.scale(r)), o0 = c.add(d0.scale(r + w)), o1 = c.add(d1.scale(r + w));
            FxBase.gquad(m, cam, i0, i1, m1, m0, col, 0, 0, a, a);
            FxBase.gquad(m, cam, m0, m1, o1, o0, col, a, a, 0, 0);
        }
    }

    private static double ground(Minecraft mc, Vec3 at) {
        return mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(at.x), Mth.floor(at.z));
    }

    private static Vec3 feet(Minecraft mc, double id, float pt) {
        Entity e = mc.level.getEntity((int) id);
        if (e == null) return null;
        return new Vec3(Mth.lerp(pt, e.xo, e.getX()), Mth.lerp(pt, e.yo, e.getY()), Mth.lerp(pt, e.zo, e.getZ()));
    }

    private static float env(double age, double life, double in, double fade) {
        return (float) (Mth.clamp(age / in, 0, 1) * Mth.clamp((life - age) / fade, 0, 1));
    }

    private static double hs(int a, int b) { return FxBase.h2(a, b, 433); }

    // ================================================================== 2 crescent
    private static void crescent(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Minecraft mc, L l, double age, double now, boolean tick) {
        FxPacket p = l.p;
        Vec3 d = p.d.normalize(), u = p.u.normalize(), sd = d.cross(u).normalize();
        double fly = Math.max(1, p.speed), k = Mth.clamp(age / fly, 0, 1);
        double travel = p.a0 * (k * (2 - k));
        Vec3 o = p.c.add(d.scale(travel));
        float a = env(age, p.life, 1.5, 4);
        double R = p.r * (0.8 + 0.3 * k);
        int seg = 16;
        Vec3[] pts = new Vec3[seg + 1];
        for (int i = 0; i <= seg; i++) {
            double t = (double) i / seg, an = Math.toRadians(-70 + 140 * t);
            Vec3 out = d.scale(Math.cos(an) * 0.55).add(sd.scale(Math.sin(an)));
            pts[i] = o.add(out.scale(R)).subtract(d.scale(R * 0.45));
            double w = Math.pow(Math.sin(t * Math.PI), 0.7);
            // a row of crystals leaning forward along the arc: a crescent of cut ice
            Vec3 lean = d.add(u.scale(0.25 * (hs(i, p.id) - 0.5))).add(out.scale(0.3)).normalize();
            crystal(vc, m, n, cam, pts[i].subtract(lean.scale(p.r * 0.25 * w)), lean, p.r * 0.55 * w + 0.1, p.r * 0.11 * w + 0.03, 5, i * 13 + p.id, 0.85F * a, i % 2 == 0);
        }
        double[] gw = new double[seg + 1];
        float[] ga = new float[seg + 1];
        for (int i = 0; i <= seg; i++) { gw[i] = 0.18 * Math.sin((double) i / seg * Math.PI) + 0.02; ga[i] = 0.75F * a; }
        gstrip(m, cam, pts, gw, ga, W_SOFT);
        Vec3[] back = new Vec3[]{o.subtract(d.scale(R * 1.8)), o.subtract(d.scale(R * 0.5))};
        gstrip(m, cam, back, new double[]{0.05, R * 0.6}, new float[]{0, 0.18F * a}, W_ICE);   // the cold air streaming behind it
        if (tick && k < 1) {   // it leaves the ground frozen behind it, small spires breaking through
            double gy = ground(mc, o);
            if (o.y - gy < 4) for (int i = 0; i < 2; i++) {
                Vec3 at = new Vec3(o.x + sd.x * (Math.random() * 2 - 1) * R * 0.7, gy, o.z + sd.z * (Math.random() * 2 - 1) * R * 0.7);
                spire(at, new Vec3((Math.random() - 0.5) * 0.6, 1, (Math.random() - 0.5) * 0.6).add(d.scale(0.4)), 0.5F + (float) Math.random() * 0.7F, 0.1F + (float) Math.random() * 0.06F, now, 24 + Math.random() * 10, 2);
            }
            mist(o, d.scale(0.02), 0.7F, now, 14);
        }
        if (k >= 1 && !l.burst) { l.burst = true; shards(o, d.scale(0.12), 0.12F, 22, 0.08F, now); mist(o, Vec3.ZERO, 1.6F, now, 20); }
    }

    // ================================================================== 3 lance
    private static void lance(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, L l, double age, double now) {
        FxPacket p = l.p;
        Vec3 ab = p.u.subtract(p.c);
        if (ab.lengthSqr() < 1e-6) { l.done = true; return; }
        double fly = Math.max(1, p.speed), k = Mth.clamp(age / fly, 0, 1);
        Vec3 dir = ab.normalize(), pos = p.c.add(ab.scale(k));
        if (k >= 1 || l.killAt >= 0) {
            shards(pos, dir.scale(-0.08), 0.1F, 10, 0.07F, now);
            mist(pos, Vec3.ZERO, 0.8F, now, 14);
            l.done = true;
            return;
        }
        double len = p.r;
        crystal(vc, m, n, cam, pos.subtract(dir.scale(len)), dir, len, Math.max(0.09, len * 0.09), 6, p.id, 0.95F, true);
        double tl = Math.min(len * 3, ab.length() * k);
        gseg(m, cam, pos.subtract(dir.scale(len + tl)), pos.subtract(dir.scale(len * 0.6)), 0.06, 0, 0.6F, W_SOFT);
    }

    // ================================================================== 4 encase
    private static void encase(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Minecraft mc, L l, double age, double now, float pt) {
        FxPacket p = l.p;
        Entity e = mc.level.getEntity((int) p.v.x);
        Vec3 f = e == null ? p.c : feet(mc, p.v.x, pt);
        double H = e == null ? 1.8 : e.getBbHeight(), W = e == null ? 0.6 : e.getBbWidth();
        boolean ending = l.killAt >= 0 || age > p.life - 1.5;
        if (ending && !l.burst) {   // it shatters
            l.burst = true;
            shards(f.add(0, H * 0.5, 0), new Vec3(0, 0.12, 0), 0.2F, 28, 0.1F, now);
            mist(f.add(0, H * 0.5, 0), new Vec3(0, 0.02, 0), (float) (H * 0.9), now, 22);
            if (l.killAt < 0) l.killAt = now;
            return;
        }
        if (l.burst) return;
        double g = FxBase.backOut(age / 4.0);
        if (p.a0 > 0) {   // a boss: no prison - a crust of ice cracking across it (Cracked Armour)
            for (int i = 0; i < 6; i++) {
                double an = i * TAU / 6 + hs(i, p.id) * 0.8;
                Vec3 o = new Vec3(Math.cos(an), 0, Math.sin(an));
                Vec3 base = f.add(o.scale(W * 0.45)).add(0, H * (0.15 + 0.7 * hs(i + 9, p.id)), 0);
                crystal(vc, m, n, cam, base, o.add(0, 0.4, 0).normalize(), (0.4 + 0.4 * hs(i, p.id + 3)) * g, 0.12 * Math.min(1, g), 5, i + p.id, 0.8F, true);
            }
            return;
        }
        for (int i = 0; i < 9; i++) {
            double an = i * TAU / 9 + hs(i, p.id) * 0.6;
            double y = H * (0.05 + 0.85 * hs(i + 9, p.id));
            Vec3 o = new Vec3(Math.cos(an), 0, Math.sin(an));
            Vec3 base = f.add(o.scale(W * 0.15)).add(0, y * 0.6, 0);
            Vec3 dir = o.add(0, 0.9 + hs(i + 3, p.id), 0).normalize();
            crystal(vc, m, n, cam, base, dir, (H * 0.45 + W * 0.5) * g * (0.7 + 0.5 * hs(i + 5, p.id)), W * 0.35 * Math.min(1, g), 6, i + p.id, 0.8F, i % 3 == 0);
        }
        crystal(vc, m, n, cam, f.subtract(0, 0.1, 0), UP, (H + 0.3) * g, W * 0.75 * Math.min(1, g), 6, p.id, 0.35F, false);   // the block of ice it stands in
    }

    // ================================================================== 5 frost
    /** Frost spreading over the ground: a pale sheet, ferns of frost growing out across it, small crystals pushing up. */
    private static void frost(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age, double now) {
        double spread = FxBase.smooth(age / Math.max(1, p.speed));
        float a = env(age, p.life, 1, 20);
        double R = p.r * spread;
        Vec3 c = p.c.add(0, 0.03, 0);
        int seg = 28;
        float[] sheet = {0.86F, 0.92F, 1.0F};
        for (int i = 0; i < seg; i++) {   // the sheet of rime
            double a0 = i * TAU / seg, a1 = (i + 1) * TAU / seg;
            double w0 = 0.85 + 0.15 * hs(i, p.id), w1 = 0.85 + 0.15 * hs((i + 1) % seg, p.id);
            Vec3 p0 = c.add(Math.cos(a0) * R * w0, 0, Math.sin(a0) * R * w0), p1 = c.add(Math.cos(a1) * R * w1, 0, Math.sin(a1) * R * w1);
            FxBase.vtx(vc, m, n, cam, c, sheet, 0.38F * a); FxBase.vtx(vc, m, n, cam, c, sheet, 0.38F * a);
            FxBase.vtx(vc, m, n, cam, p1, sheet, 0.22F * a); FxBase.vtx(vc, m, n, cam, p0, sheet, 0.22F * a);
        }
        int arms = 14;
        for (int i = 0; i < arms; i++) {   // ferns of frost
            double an = i * TAU / arms + hs(i, p.id + 1) * 0.3;
            Vec3 dir = new Vec3(Math.cos(an), 0, Math.sin(an)), side = new Vec3(-dir.z, 0, dir.x);
            int k = 8;
            Vec3 prev = c;
            for (int j = 1; j <= k; j++) {
                double t = (double) j / k * R;
                Vec3 q = c.add(dir.scale(t)).add(side.scale((hs(i * 10 + j, p.id) - 0.5) * 0.4)).add(0, 0.01, 0);
                gseg(m, cam, prev, q, 0.035, 0.55F * a, 0.5F * a, W_SOFT);
                if (j % 2 == 0 && j < k) for (int sgn = -1; sgn <= 1; sgn += 2) {
                    Vec3 tip = q.add(dir.scale(0.35 * R / p.r)).add(side.scale(sgn * 0.4 * R / p.r));
                    gseg(m, cam, q, tip, 0.02, 0.4F * a, 0, W_SOFT);
                }
                prev = q;
            }
        }
        gring(m, cam, c, R, 0.25, 0.45F * a * (float) (1 - spread * 0.6), W_CORE);   // the frost front
        int cr = (int) (p.r * 4);
        for (int i = 0; i < cr; i++) {   // crystals pushing up through the frost
            double an = hs(i, p.id + 2) * TAU, rr = p.r * Math.sqrt(hs(i, p.id + 3));
            if (rr > R) continue;
            double g = Mth.clamp((R - rr) / 1.2, 0, 1);
            Vec3 b = c.add(Math.cos(an) * rr, -0.05, Math.sin(an) * rr);
            Vec3 dir = new Vec3((hs(i, p.id + 4) - 0.5) * 0.7, 1, (hs(i, p.id + 5) - 0.5) * 0.7).normalize();
            crystal(vc, m, n, cam, b, dir, (0.25 + 0.45 * hs(i, p.id + 6)) * g, 0.07 + 0.05 * hs(i, p.id + 7), 5, i, 0.85F * a, false);
        }
    }

    // ================================================================== 6 tether
    private static void tether(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Minecraft mc, L l, double age, double now, float pt, boolean tick) {
        FxPacket p = l.p;
        Entity ce = mc.level.getEntity((int) p.v.x), te = mc.level.getEntity((int) p.v.y);
        if (ce == null || te == null) return;
        Vec3 a = feet(mc, p.v.x, pt).add(0, ce.getBbHeight() * 0.65, 0), b = feet(mc, p.v.y, pt).add(0, te.getBbHeight() * 0.55, 0);
        float e = env(age, p.life, 3, 5);
        double reach = Mth.clamp(age / 3.0, 0, 1);
        Vec3 ab = b.subtract(a);
        double len = ab.length();
        if (len < 0.2) return;
        Vec3 dir = ab.scale(1 / len);
        Vec3[] f = frame(dir);
        gseg(m, cam, a, a.add(ab.scale(reach)), 0.16, 0.5F * e, 0.5F * e, W_SOFT);   // the beam of cold
        gseg(m, cam, a, a.add(ab.scale(reach)), 0.05, 0.9F * e, 0.9F * e, W_CORE);
        for (int s = 0; s < 3; s++) {   // three strands of ice winding round it
            int k = 24;
            Vec3[] pts = new Vec3[k + 1];
            double[] r = new double[k + 1];
            for (int i = 0; i <= k; i++) {
                double t = reach * i / k, an = t * len * 2.2 - now * 0.25 + s * TAU / 3;
                pts[i] = a.add(ab.scale(t)).add(f[0].scale(Math.cos(an) * 0.22)).add(f[1].scale(Math.sin(an) * 0.22));
                r[i] = 0.035 * e;
            }
            tube(vc, m, n, cam, pts, r, 4, 0.8F);
        }
        for (int i = 0; i < 5; i++) {   // crystals of frost growing along it
            double t = (i + 0.5) / 5 * reach;
            Vec3 at = a.add(ab.scale(t)), out = f[0].scale(Math.cos(i * 2.4)).add(f[1].scale(Math.sin(i * 2.4)));
            crystal(vc, m, n, cam, at, out.add(dir.scale(0.3)).normalize(), 0.3 * e, 0.06, 5, i + p.id, 0.8F * e, false);
        }
        if (tick && reach >= 1) mist(b, Vec3.ZERO, 0.5F, now, 10);
    }

    // ================================================================== 7 orbit
    private static void orbit(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Minecraft mc, L l, double age, double now, float pt) {
        FxPacket p = l.p;
        Entity e = mc.level.getEntity((int) p.v.x);
        if (e == null) return;
        Vec3 c = feet(mc, p.v.x, pt).add(0, e.getBbHeight() * 0.55, 0);
        float a = env(age, p.life, 5, 8);
        int k = Math.max(3, p.sweep);
        double R = p.r * (0.6 + 0.4 * Math.min(1, age / 5.0));
        boolean self = e == mc.player && mc.options.getCameraType().isFirstPerson();
        for (int i = 0; i < k; i++) {
            double an = now * 0.18 + i * TAU / k;
            double tilt = Math.sin(an * 2 + i) * 0.3;
            Vec3 o = new Vec3(Math.cos(an), tilt, Math.sin(an)).normalize();
            Vec3 tan = new Vec3(-Math.sin(an), 0, Math.cos(an));
            Vec3 at = c.add(o.scale(R));
            if (self && at.distanceTo(cam) < 1.5) continue;
            crystal(vc, m, n, cam, at.subtract(tan.scale(0.35)), tan, 0.75, 0.1, 6, i + p.id, 0.9F * a, true);
            Vec3[] trail = new Vec3[6];
            for (int j = 0; j < 6; j++) {
                double aj = an - j * 0.12;
                trail[j] = c.add(new Vec3(Math.cos(aj), Math.sin(aj * 2 + i) * 0.3, Math.sin(aj)).normalize().scale(R));
            }
            gstrip(m, cam, trail, new double[]{0.06, 0.05, 0.04, 0.03, 0.02, 0.01}, new float[]{0.5F * a, 0.4F * a, 0.3F * a, 0.2F * a, 0.1F * a, 0}, W_SOFT);
        }
    }

    // ================================================================== 8 dome
    private static void dome(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Minecraft mc, L l, double age, double now, float pt) {
        FxPacket p = l.p;
        Entity e = mc.level.getEntity((int) p.v.x);
        Vec3 f = e == null ? p.c : feet(mc, p.v.x, pt);
        double R = p.r * FxBase.backOut(age / 5.0);
        if ((l.killAt >= 0 || age > p.life - 1.5) && !l.burst) {
            l.burst = true;
            for (int i = 0; i < 3; i++) shards(f.add(0, R * 0.5 * (i + 1) / 3, 0), new Vec3(0, 0.08, 0), 0.32F, 16, 0.12F, now);
            mist(f.add(0, 1, 0), Vec3.ZERO, (float) R * 1.2F, now, 22);
            if (l.killAt < 0) l.killAt = now;
            return;
        }
        if (l.burst) return;
        boolean self = e == mc.player && mc.options.getCameraType().isFirstPerson();
        float a = (self ? 0.25F : 0.5F) * env(age, p.life, 3, 3);
        int lat = 5, lon = 12;
        Vec3[][] P = new Vec3[lat + 1][lon];
        for (int i = 0; i <= lat; i++) {
            double th = Math.PI / 2 * i / lat;
            for (int j = 0; j < lon; j++) {
                double an = (j + (i % 2) * 0.5) * TAU / lon;
                P[i][j] = f.add(Math.cos(an) * Math.cos(th) * R, Math.sin(th) * R * 1.05, Math.sin(an) * Math.cos(th) * R);
            }
        }
        for (int i = 0; i < lat; i++) for (int j = 0; j < lon; j++) {   // faceted plates of ice
            int j2 = (j + 1) % lon;
            Vec3 ctr = P[i][j].add(P[i + 1][j]).add(P[i + 1][j2]).add(P[i][j2]).scale(0.25);
            Vec3 nr = ctr.subtract(f).normalize();
            q(vc, m, n, cam, P[i][j], P[i][j2], P[i + 1][j2], P[i + 1][j], nr, a, 1.05F);
        }
        float hb = (float) Math.pow(Math.max(0, Math.sin(now * 0.26)), 6);
        for (int j = 0; j < lon; j += 2) {   // light running up its seams
            Vec3[] v = new Vec3[lat + 1];
            for (int i = 0; i <= lat; i++) v[i] = P[i][j];
            double[] w = new double[lat + 1];
            float[] al = new float[lat + 1];
            for (int i = 0; i <= lat; i++) { w[i] = 0.05; al[i] = (0.25F + 0.35F * hb) * a * 2; }
            gstrip(m, cam, v, w, al, W_SOFT);
        }
        gring(m, cam, f.add(0, 0.05, 0), R, 0.3, 0.5F * a, W_CORE);
    }

    // ================================================================== 9 glacier
    private static void glacier(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Minecraft mc, L l, double age, double now, boolean tick) {
        FxPacket p = l.p;
        Vec3 d = p.d.normalize(), sd = d.cross(UP).normalize();
        double x = age * p.speed;
        Vec3 front = p.c.add(d.scale(x));
        float a = env(age, p.life, 1, 3);
        gseg(m, cam, front.add(sd.scale(p.r)).add(0, 0.05, 0), front.subtract(sd.scale(p.r)).add(0, 0.05, 0), 0.5, 0.5F * a, 0.5F * a, W_SOFT);   // the cold line racing out
        if (tick && age < p.life - 2) {   // a wall of ice bursting up along the front, tallest in the middle
            int cols = Math.max(3, (int) (p.r * 1.6));
            for (int i = 0; i <= cols; i++) {
                double s = (double) i / cols * 2 - 1;
                double h = p.r2 * (1 - 0.55 * s * s) * (0.75 + 0.5 * Math.random());
                Vec3 b = front.add(sd.scale(s * p.r)).add(d.scale((Math.random() - 0.5) * 0.6));
                double gy = ground(mc, b.add(0, 2, 0));
                if (Math.abs(gy - b.y) < 3) b = new Vec3(b.x, gy, b.z);
                Vec3 dir = d.scale(0.45).add(sd.scale(s * 0.35)).add(0, 1, 0).normalize();
                spire(b.subtract(0, 0.2, 0), dir, (float) h, (float) (0.22 + 0.18 * (1 - Math.abs(s))), now, 26 + Math.random() * 8, 2);
            }
            mist(front.add(0, p.r2 * 0.5, 0), d.scale(0.08), (float) p.r * 0.6F, now, 16);
        }
    }

    // ================================================================== 14 a frost sentry: a crystal hanging in the air, turning
    private static void sentry(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, L l, double age, double now) {
        FxPacket p = l.p;
        if (age > p.life - 3) return;
        float a = env(age, p.life, 5, 4);
        double g = FxBase.backOut(Math.min(1, age / 8.0));
        Vec3 c = p.c.add(0, Math.sin(now * 0.12 + p.id) * 0.12, 0);
        double spin = now * 0.08 + p.id;
        double s = p.r * g;
        // a double-pointed crystal (up and down), with three small ones orbiting it
        crystal(vc, m, n, cam, c, UP, s * 0.9, s * 0.22, 6, (int) (spin * 3), 0.9F * a, true);
        crystal(vc, m, n, cam, c, UP.scale(-1), s * 0.6, s * 0.22, 6, (int) (spin * 3) + 1, 0.9F * a, false);
        for (int i = 0; i < 3; i++) {
            double an = spin * 1.6 + i * TAU / 3;
            Vec3 o = new Vec3(Math.cos(an), 0.25 * Math.sin(an * 2), Math.sin(an));
            crystal(vc, m, n, cam, c.add(o.scale(s * 0.75)), o.cross(UP).normalize(), s * 0.3, s * 0.06, 4, i + p.id, 0.8F * a, false);
        }
        gglow(m, cam, c, s * 0.9, 0.3F * a, W_ICE);
        gseg(m, cam, c, new Vec3(c.x, c.y - 2.4, c.z), 0.04, 0.25F * a, 0, W_SOFT);   // a thread of cold light to the ground
    }

    // ================================================================== 15 glacier prison: a ring of pillars, then they crush in
    private static void prison(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, L l, double age, double now) {
        FxPacket p = l.p;
        double crush = Mth.clamp((age - (p.life - 6)) / 5.0, 0, 1);
        if (crush >= 1 && !l.burst) {
            l.burst = true;
            shards(p.c.add(0, 1.5, 0), new Vec3(0, 0.12, 0), 0.3F, 34, 0.12F, now);
            mist(p.c.add(0, 1.2, 0), Vec3.ZERO, 2.5F, now, 24);
            return;
        }
        if (l.burst) return;
        double g = FxBase.backOut(Math.min(1, age / 4.0));
        int pillars = 10;
        double R = p.r * (1 - 0.8 * crush * crush);
        for (int i = 0; i < pillars; i++) {
            double an = i * TAU / pillars;
            Vec3 o = new Vec3(Math.cos(an), 0, Math.sin(an));
            Vec3 lean = o.scale(-0.12 - crush * 0.5).add(0, 1, 0).normalize();   // they lean in, and fall in
            crystal(vc, m, n, cam, p.c.add(o.scale(R)).subtract(0, 0.4, 0), lean, p.r2 * g * (0.8 + 0.3 * hs(i, p.id)), 0.32, 6, i + p.id, 0.85F, i % 2 == 0);
        }
        // bars of frost between the pillars, a band near the top and one near the foot
        for (int band = 0; band < 2; band++) {
            double y = p.r2 * g * (band == 0 ? 0.25 : 0.7);
            Vec3 prev = null;
            for (int i = 0; i <= pillars; i++) {
                double an = i * TAU / pillars;
                Vec3 q = p.c.add(Math.cos(an) * R, y, Math.sin(an) * R);
                if (prev != null) gseg(m, cam, prev, q, 0.05, 0.5F, 0.5F, W_SOFT);
                prev = q;
            }
        }
        gring(m, cam, p.c.add(0, 0.05, 0), R, 0.3, 0.5F, W_CORE);
    }

    // ================================================================== 17 glacier wall
    private static void wall(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, L l, double age, double now) {
        FxPacket p = l.p;
        Vec3 d = p.d.normalize(), side = new Vec3(-d.z, 0, d.x);
        if (age > p.life - 4 && !l.burst) {   // it bursts outward
            l.burst = true;
            for (int i = -3; i <= 3; i++) shards(p.c.add(side.scale(i)).add(0, 1.5, 0), d.scale(0.3).add(0, 0.1, 0), 0.15F, 8, 0.12F, now);
            mist(p.c.add(0, 1.5, 0), d.scale(0.1), 3F, now, 24);
            return;
        }
        if (l.burst) return;
        double g = FxBase.backOut(Math.min(1, age / 6.0));
        int cols = 9;
        for (int i = 0; i < cols; i++) {   // a rank of great crystals, tallest in the middle, packed tight
            double s = (double) i / (cols - 1) * 2 - 1;
            double h = p.r2 * (1 - 0.35 * s * s) * (0.85 + 0.25 * hs(i, p.id)) * g;
            Vec3 b = p.c.add(side.scale(s * p.r)).subtract(0, 0.4, 0);
            crystal(vc, m, n, cam, b, d.scale(-0.08).add(side.scale(s * 0.12)).add(0, 1, 0).normalize(), h, 0.48, 6, i + p.id, 0.8F, true);
            if (i % 2 == 0) crystal(vc, m, n, cam, b.add(d.scale(0.45)), d.scale(0.5).add(0, 1, 0).normalize(), h * 0.45, 0.22, 5, i + 30 + p.id, 0.8F, false);
        }
        float hb = (float) Math.pow(Math.max(0, Math.sin(now * 0.3)), 6);
        gseg(m, cam, p.c.add(side.scale(-p.r)).add(0, p.r2 * 0.5 * g, 0), p.c.add(side.scale(p.r)).add(0, p.r2 * 0.5 * g, 0), 0.08, (0.2F + 0.3F * hb), (0.2F + 0.3F * hb), W_SOFT);
    }

    // ================================================================== 18 snow veil: you are a whirl of snow
    private static void veil(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Minecraft mc, L l, double age, double now, float pt) {
        FxPacket p = l.p;
        Entity e = mc.level.getEntity((int) p.v.x);
        if (e == null) return;
        Vec3 f = feet(mc, p.v.x, pt);
        float a = env(age, p.life, 3, 6);
        boolean self = e == mc.player && mc.options.getCameraType().isFirstPerson();
        double H = e.getBbHeight();
        for (int s = 0; s < 5; s++) {   // bands of snow whirling round and up
            int k = 16;
            Vec3[] pts = new Vec3[k];
            double[] w = new double[k];
            float[] al = new float[k];
            for (int j = 0; j < k; j++) {
                double u = (double) j / (k - 1);
                double an = now * 0.45 + s * TAU / 5 - u * 2.4;
                double rad = 0.9 + 0.5 * Math.sin(s + now * 0.1) + 0.6 * u;
                pts[j] = f.add(Math.cos(an) * rad, H * (0.1 + 0.85 * ((s * 0.21 + u * 0.4 + now * 0.02) % 1.0)), Math.sin(an) * rad);
                w[j] = 0.18 * (1 - u);
                al[j] = (self ? 0.25F : 0.55F) * a * (1 - (float) u);
            }
            gstrip(m, cam, pts, w, al, W_SOFT);
        }
        if ((long) now % 3 == 0 && Math.random() < 0.6) mist(f.add((Math.random() - 0.5) * 2, Math.random() * H, (Math.random() - 0.5) * 2), new Vec3(0, 0.02, 0), 0.6F, now, 14);
    }

    // ================================================================== 20 a ring of cold on the ground (a0 = 1: a shockwave)
    private static void shock(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, L l, double age, double now) {
        FxPacket p = l.p;
        Vec3 c = p.c.add(0, 0.05, 0);
        if (p.a0 > 0) {   // a shockwave of frost rolling out, crystals popping up behind its front
            double k = age / Math.max(1, p.life);
            double R = p.r * Math.sqrt(k);
            float a = (float) (1 - k);
            gring(m, cam, c, R, 0.6, 0.85F * a, W_CORE);
            gring(m, cam, c, R * 0.75, 0.3, 0.5F * a, W_ICE);
            for (int i = 0; i < 24; i++) {
                double an = i * TAU / 24 + p.id;
                Vec3 b = c.add(Math.cos(an) * R * 0.92, -0.1, Math.sin(an) * R * 0.92);
                crystal(vc, m, n, cam, b, new Vec3(Math.cos(an) * 0.6, 1, Math.sin(an) * 0.6).normalize(), 0.5 * a + 0.1, 0.09, 5, i, 0.85F * a, false);
            }
            gglow(m, cam, c.add(0, 1, 0), 2.5 * (1 - k), 0.5F * (float) Math.max(0, 1 - k * 3), W_CORE);
            return;
        }
        // a warning: a ring of cold closing in, frost marks inside it
        float a = env(age, p.life, 3, 4);
        double R = p.r * (1.15 - 0.15 * Math.sin(now * 0.3));
        gring(m, cam, c, R, 0.25, 0.6F * a, W_SOFT);
        for (int i = 0; i < 8; i++) {
            double an = i * TAU / 8 + now * 0.05;
            gseg(m, cam, c.add(Math.cos(an) * R * 0.3, 0, Math.sin(an) * R * 0.3), c.add(Math.cos(an) * R * 0.85, 0, Math.sin(an) * R * 0.85), 0.04, 0.4F * a, 0, W_SOFT);
        }
    }

    // ================================================================== 21 the ice statue Frost Step leaves behind
    private static void statue(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Minecraft mc, L l, double age, double now) {
        FxPacket p = l.p;
        Vec3 f = p.c;
        if (age > p.life - 6 && !l.burst) {   // it shatters
            l.burst = true;
            shards(f.add(0, 1, 0), new Vec3(0, 0.08, 0), 0.22F, 26, 0.09F, now);
            mist(f.add(0, 1, 0), Vec3.ZERO, 1.4F, now, 20);
            return;
        }
        if (l.burst) return;
        Vec3 d = p.d.normalize(), side = new Vec3(-d.z, 0, d.x);
        float a = 0.6F;
        // a figure roughly cut in ice: legs, body, arms flung back, head - each a crystal
        crystal(vc, m, n, cam, f.add(side.scale(0.12)), new Vec3(0, 1, 0).add(d.scale(-0.15)).normalize(), 0.85, 0.12, 5, 1, a, false);
        crystal(vc, m, n, cam, f.add(side.scale(-0.12)), new Vec3(0, 1, 0).add(d.scale(0.2)).normalize(), 0.85, 0.12, 5, 2, a, false);
        crystal(vc, m, n, cam, f.add(0, 0.75, 0), UP, 0.75, 0.24, 6, 3, a, true);
        crystal(vc, m, n, cam, f.add(0, 1.35, 0).add(side.scale(0.28)), side.add(d.scale(-0.8)).add(0, -0.3, 0).normalize(), 0.65, 0.08, 5, 4, a, false);
        crystal(vc, m, n, cam, f.add(0, 1.35, 0).add(side.scale(-0.28)), side.scale(-1).add(d.scale(-0.8)).add(0, -0.3, 0).normalize(), 0.65, 0.08, 5, 5, a, false);
        crystal(vc, m, n, cam, f.add(0, 1.45, 0), UP, 0.45, 0.17, 6, 6, a, true);
        // the cold streak to where you re-formed
        gseg(m, cam, f.add(0, 1, 0), f.add(0, 1, 0).add(d.scale(Math.min(9, age * 3))), 0.25 * (1 - age / p.life), 0.5F, 0, W_SOFT);
    }

    // ================================================================== 13 the frost star of an ice hit (a1 = 1: a Shatter)
    private static void iceHit(Matrix4f m, Vec3 cam, L l, double age) {
        FxPacket p = l.p;
        double k = age / Math.max(1, p.life);
        float a = (float) (1 - k);
        if (a <= 0.01F) return;
        float s = (p.a0 > 0 ? p.a0 : 1F) * (p.a1 > 0 ? 1.8F : 1F);
        Vec3 view = p.c.subtract(cam);
        if (view.lengthSqr() < 1e-6) return;
        Vec3[] f = frame(view.normalize());
        int rays = 6;
        for (int i = 0; i < rays; i++) {   // a six-pointed star of frost, opening and fading
            double an = i * TAU / rays + p.id;
            Vec3 d = f[0].scale(Math.cos(an)).add(f[1].scale(Math.sin(an)));
            double r1 = (0.25 + 0.55 * Math.sqrt(k)) * s;
            gseg(m, cam, p.c.add(d.scale(0.05)), p.c.add(d.scale(r1)), 0.035 * s, 0.9F * a, 0.1F * a, W_CORE);
            Vec3 b = p.c.add(d.scale(r1 * 0.6));   // little branches, like a snowflake's
            gseg(m, cam, b, b.add(d.add(f[0].scale(Math.cos(an + 1.2)).add(f[1].scale(Math.sin(an + 1.2)))).normalize().scale(r1 * 0.25)), 0.02 * s, 0.7F * a, 0, W_SOFT);
            gseg(m, cam, b, b.add(d.add(f[0].scale(Math.cos(an - 1.2)).add(f[1].scale(Math.sin(an - 1.2)))).normalize().scale(r1 * 0.25)), 0.02 * s, 0.7F * a, 0, W_SOFT);
        }
        gglow(m, cam, p.c, 0.45 * s * (1 - k * 0.5), 0.45F * a, W_ICE);
    }

    // ================================================================== frost on your hand (first person, under the Moon)
    private static void frostHands(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Minecraft mc, double now) {
        net.minecraft.client.Camera cm = mc.gameRenderer.getMainCamera();
        org.joml.Vector3f lv = cm.getLeftVector(), uv = cm.getUpVector(), fv = cm.getLookVector();
        Vec3 left = new Vec3(lv.x(), lv.y(), lv.z()), up = new Vec3(uv.x(), uv.y(), uv.z()), fw = new Vec3(fv.x(), fv.y(), fv.z());
        boolean was = FxBase.nearFade;
        FxBase.nearFade = false;
        Vec3 hand = cam.add(fw.scale(0.72)).subtract(left.scale(0.44)).subtract(up.scale(0.46));
        for (int i = 0; i < 6; i++) {   // little crystals of ice grown over the knuckles and wrist
            double an = i * 1.05 + 0.3;
            Vec3 o = left.scale(-Math.cos(an) * 0.06).add(up.scale(Math.sin(an) * 0.05 + 0.04)).add(fw.scale(-0.05 * i));
            Vec3 dir = o.normalize().add(up.scale(0.6)).normalize();
            double len = 0.05 + 0.04 * hs(i, 7) + 0.01 * Math.sin(now * 0.2 + i);
            crystal(vc, m, n, cam, hand.add(o), dir, len, 0.012, 5, i, 0.75F, false);
        }
        gglow(m, cam, hand.add(up.scale(0.05)), 0.12, 0.18F + 0.06F * (float) Math.sin(now * 0.3), W_SOFT);
        if ((long) now % 12 == 0 && Math.random() < 0.35) mist(hand, up.scale(0.01).add(fw.scale(0.01)), 0.08F, now, 10);
        FxBase.nearFade = was;
    }

    // ================================================================== 12 the Ice Throne
    private static void throne(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Minecraft mc, L l, double age, double now, float pt) {
        FxPacket p = l.p;
        Entity e = mc.level.getEntity((int) p.v.x);
        if (e == null) return;
        Vec3 f = feet(mc, p.v.x, pt);
        if (age > p.life - 4 && !l.burst) {   // it shatters, and you drop
            l.burst = true;
            shards(new Vec3(f.x, p.c.y + 1.5, f.z), new Vec3(0, 0.1, 0), 0.3F, 40, 0.13F, now);
            mist(new Vec3(f.x, p.c.y + 1.5, f.z), Vec3.ZERO, 3F, now, 24);
            return;
        }
        if (l.burst) return;
        double g = FxBase.backOut(Math.min(1, age / 10.0));
        Vec3 base = new Vec3(f.x, p.c.y, f.z);
        double seat = Math.max(0.2, f.y - p.c.y);
        double yaw = Math.toRadians(e.getYRot());
        Vec3 face = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw)), rt = new Vec3(-face.z, 0, face.x);
        boolean self = e == mc.player && mc.options.getCameraType().isFirstPerson();
        float a = self ? 0.45F : 0.9F;
        for (int i = 0; i < 8; i++) {   // the pillar of crystals rising under you
            double an = i * TAU / 8 + hs(i, p.id) * 0.4;
            Vec3 o = new Vec3(Math.cos(an), 0, Math.sin(an));
            crystal(vc, m, n, cam, base.add(o.scale(0.35)), o.scale(0.25).add(0, 1, 0).normalize(), seat * g * (0.85 + 0.25 * hs(i, p.id + 1)), 0.22, 6, i + p.id, a, i % 2 == 0);
        }
        crystal(vc, m, n, cam, base, UP, seat * g, 0.45, 8, p.id, a * 0.8F, false);
        Vec3 top = base.add(0, seat * g, 0);
        for (int i = -2; i <= 2; i++) {   // the back of the throne: tall crystals fanning up behind you
            Vec3 b0 = top.subtract(face.scale(0.42)).add(rt.scale(i * 0.16));
            Vec3 dir = new Vec3(0, 1, 0).add(rt.scale(i * 0.22)).subtract(face.scale(0.15)).normalize();
            crystal(vc, m, n, cam, b0, dir, (1.6 - Math.abs(i) * 0.3) * g, 0.12, 6, i + 40 + p.id, a, true);
        }
        for (int s = -1; s <= 1; s += 2)   // the arms
            crystal(vc, m, n, cam, top.add(rt.scale(s * 0.5)).subtract(face.scale(0.2)), face.add(0, 0.35, 0).normalize(), 0.7 * g, 0.09, 5, s + 60 + p.id, a, false);
        gring(m, cam, base.add(0, 0.05, 0), 1.6 + 0.2 * Math.sin(now * 0.2), 0.3, 0.45F, W_SOFT);
    }

    // ================================================================== 10 comet
    private static void comet(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Minecraft mc, L l, double age, double now, boolean tick) {
        FxPacket p = l.p;
        double fall = Math.max(1, p.speed);
        Vec3 ab = p.u.subtract(p.c);
        Vec3 dir = ab.normalize();
        if (age < fall) {
            double k = age / fall;
            k = k * k;
            Vec3 pos = p.c.add(ab.scale(k));
            float s = p.r;
            crystal(vc, m, n, cam, pos.subtract(dir.scale(s * 1.6)), dir, s * 2.2, s * 0.55, 8, p.id, 0.9F, true);
            for (int i = 0; i < 6; i++) {   // a cluster of smaller crystals bristling out of its back
                double an = i * TAU / 6;
                Vec3[] f = frame(dir);
                Vec3 o = f[0].scale(Math.cos(an)).add(f[1].scale(Math.sin(an)));
                crystal(vc, m, n, cam, pos.subtract(dir.scale(s * 0.9)), o.scale(0.8).subtract(dir.scale(0.6)).normalize(), s * 1.1, s * 0.22, 5, i + p.id, 0.85F, false);
            }
            gglow(m, cam, pos, s * 2.2, 0.35F, W_SOFT);
            gseg(m, cam, pos.subtract(dir.scale(s * 14)), pos, s * 0.9, 0, 0.55F, W_SOFT);   // its trail through the sky
            gseg(m, cam, pos.subtract(dir.scale(s * 9)), pos, s * 0.3, 0, 0.9F, W_CORE);
            if (tick) shards(pos.subtract(dir.scale(s)), dir.scale(-0.05), 0.12F, 2, 0.1F, now);
            // the place it will strike: a ring of cold on the ground closing in
            gring(m, cam, p.u.add(0, 0.05, 0), p.r * 4 * (1.2 - 0.6 * k), 0.3, 0.5F * (float) k, W_SOFT);
            return;
        }
        double t = age - fall;
        if (!l.burst) {   // impact
            l.burst = true;
            FxPacket fr = new FxPacket();
            fr.h = 5; fr.c = p.u; fr.r = p.r * 4.5F; fr.speed = 10; fr.life = Math.max(40, p.life - (int) fall); fr.id = p.id + 1;
            LIVE.add(new L(fr, now));
            for (int i = 0; i < 16; i++) {   // a crown of great spires thrown up round the crater
                double an = i * TAU / 16 + hs(i, p.id) * 0.3;
                double rr = p.r * (1.6 + 1.2 * hs(i + 20, p.id));
                Vec3 o = new Vec3(Math.cos(an), 0, Math.sin(an));
                spire(p.u.add(o.scale(rr)).subtract(0, 0.3, 0), o.scale(0.7).add(0, 1, 0), p.r * (1.6F + (float) hs(i + 40, p.id) * 1.6F), p.r * 0.32F, now, 34 + hs(i, 7) * 12, 3);
            }
            spire(p.u.subtract(0, 0.4, 0), UP, p.r * 4.2F, p.r * 0.8F, now, 44, 4);
            shards(p.u.add(0, 0.6, 0), new Vec3(0, 0.25, 0), 0.3F, 48, 0.13F, now);
            mist(p.u.add(0, 1, 0), Vec3.ZERO, p.r * 4, now, 34);
        }
        float a = (float) Mth.clamp(1 - t / 14.0, 0, 1);
        gglow(m, cam, p.u.add(0, 1, 0), p.r * (3 + t * 0.4), 0.7F * (float) Math.max(0, 1 - t / 7.0), W_CORE);
        gring(m, cam, p.u.add(0, 0.06, 0), p.r * (1.5 + t * 1.3), 0.6, 0.8F * a, W_CORE);
        gring(m, cam, p.u.add(0, 0.07, 0), p.r * (1 + t * 0.8), 0.3, 0.5F * a, W_ICE);
        for (int i = 0; i < 18; i++) {   // light thrown up out of the crater
            double an = i * TAU / 18;
            Vec3 b = p.u.add(Math.cos(an) * p.r * (1 + t * 0.3), t * 0.6, Math.sin(an) * p.r * (1 + t * 0.3));
            gseg(m, cam, b, b.add(0, 1.2 + hs(i, 99) * 1.5, 0), 0.05, 0.9F * a, 0, W_CORE);
        }
    }

    // ================================================================== 22 Absolute Zero
    private static final float[] FROZEN = {0.55F, 0.78F, 1.0F};

    /**
     * The cold stops everything. A great snowflake etches itself into the ground out from your feet, pillars of pale
     * light rise from its six points and the air itself freezes - a thousand glints hanging still. Then it lands: a
     * dome of cold sweeps out, a crown of ice spears bursts up round the edge, and the frozen air shatters into snow.
     */
    private static void absoluteZero(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Minecraft mc, L l, double age, double now, boolean tick) {
        FxPacket p = l.p;
        double R = p.r, charge = Math.max(1, p.sweep);
        Vec3 c = p.c.add(0, 0.04, 0);
        float a = env(age, p.life, 2, 18);
        double draw = FxBase.smooth(Mth.clamp(age / charge, 0, 1));
        double after = age - charge;
        float flare = after < 0 ? 0F : (float) Math.max(0, 1 - after / 10.0);
        double rot = now * 0.006 + p.id;
        // ---- the snowflake on the ground
        float sa = a * (0.55F + 0.45F * flare);
        for (int arm = 0; arm < 6; arm++) {
            double an = rot + arm * TAU / 6;
            Vec3 dir = new Vec3(Math.cos(an), 0, Math.sin(an)), side = new Vec3(-dir.z, 0, dir.x);
            double len = R * draw;
            gseg(m, cam, c, c.add(dir.scale(len)), 0.16, sa * 0.5F, sa * 0.35F, FROZEN);
            gseg(m, cam, c, c.add(dir.scale(len)), 0.045, sa, sa * 0.8F, W_CORE);
            double[] at = {0.3, 0.55, 0.8};
            double[] bl = {0.22, 0.18, 0.12};
            for (int b = 0; b < 3; b++) {
                double u = at[b] * R;
                if (u > len) continue;
                double grow = Mth.clamp((len - u) / (R * 0.15), 0, 1);
                Vec3 o = c.add(dir.scale(u));
                for (int sg = -1; sg <= 1; sg += 2) {
                    Vec3 bd = dir.scale(0.5).add(side.scale(sg * 0.866));
                    Vec3 tip = o.add(bd.scale(bl[b] * R * grow));
                    gseg(m, cam, o, tip, 0.03, sa * 0.85F, sa * 0.4F, W_CORE);
                    gseg(m, cam, o, tip, 0.1, sa * 0.35F, 0, FROZEN);
                    if (b == 0) {   // the inner branches fork again
                        Vec3 mid = o.add(bd.scale(bl[b] * R * grow * 0.55));
                        gseg(m, cam, mid, mid.add(dir.scale(bl[b] * R * 0.35 * grow)), 0.02, sa * 0.6F, 0, W_CORE);
                    }
                }
            }
        }
        // hexagons: one inside, one at the rim; and the rim circle
        for (int ring = 0; ring < 2; ring++) {
            double hr = (ring == 0 ? 0.35 : 1.0) * R * draw;
            for (int k = 0; k < 6; k++) {
                double a0 = rot + k * TAU / 6 + TAU / 12, a1 = rot + (k + 1) * TAU / 6 + TAU / 12;
                gseg(m, cam, c.add(Math.cos(a0) * hr, 0, Math.sin(a0) * hr), c.add(Math.cos(a1) * hr, 0, Math.sin(a1) * hr), 0.03, sa * 0.7F, sa * 0.7F, W_SOFT);
            }
        }
        gring(m, cam, c, R * draw * 1.04, 0.22, sa * 0.6F, FROZEN);
        // ---- pillars of light from the six points
        for (int arm = 0; arm < 6; arm++) {
            double an = rot + arm * TAU / 6;
            Vec3 b = c.add(Math.cos(an) * R * draw, 0, Math.sin(an) * R * draw);
            double h = (1.5 + 3.5 * draw) * (1 + flare);
            float pa = a * (float) (0.25 + 0.5 * draw) * (after < 0 ? 1F : Math.max(0.15F, flare));
            gseg(m, cam, b, b.add(0, h, 0), 0.35, pa * 0.45F, 0, FROZEN);
            gseg(m, cam, b, b.add(0, h * 0.8, 0), 0.07, pa, 0, W_CORE);
            gglow(m, cam, b.add(0, 0.2, 0), 0.6 + flare, pa * 0.8F, W_SOFT);
        }
        // ---- the frozen air: glints hanging still, then falling as snow when it lands
        for (int i = 0; i < 70; i++) {
            double born = hs(i, p.id + 300) * charge * 0.9;
            if (age < born) continue;
            double an = hs(i, p.id + 301) * TAU, rr = R * Math.sqrt(hs(i, p.id + 302));
            double y = 0.3 + 3.2 * hs(i, p.id + 303);
            float ga;
            if (after < 0) { y += Math.sin(now * 0.05 + i) * 0.02; ga = (float) Mth.clamp((age - born) / 4, 0, 1) * a; }
            else { y -= after * after * 0.004 * (0.6 + hs(i, p.id + 304)); ga = a * (float) Math.max(0, 1 - after / 40.0); }
            if (y < 0.05) continue;
            Vec3 q = c.add(Math.cos(an) * rr, y, Math.sin(an) * rr);
            float tw = 0.6F + 0.4F * (float) Math.sin(now * 0.7 + i * 2.3);
            gglow(m, cam, q, 0.06 + 0.03 * tw, ga * tw, W_CORE);
            if (after < 0 && i % 3 == 0) {   // a tiny frost cross on some of them
                gseg(m, cam, q.add(-0.09, 0, 0), q.add(0.09, 0, 0), 0.01, ga * 0.8F, ga * 0.8F, W_CORE);
                gseg(m, cam, q.add(0, -0.09, 0), q.add(0, 0.09, 0), 0.01, ga * 0.8F, ga * 0.8F, W_CORE);
            }
        }
        // ---- the moment it freezes
        if (after >= 0) {
            if (!l.burst) {
                l.burst = true;
                for (int i = 0; i < 26; i++) {   // the crown of ice spears round the edge, leaning out
                    double an = rot + i * TAU / 26 + hs(i, p.id + 310) * 0.12;
                    Vec3 o = new Vec3(Math.cos(an), 0, Math.sin(an));
                    double rr = R * (0.97 + 0.08 * hs(i, p.id + 311));
                    float len = (float) (1.6 + 1.8 * hs(i, p.id + 312)) * (i % 2 == 0 ? 1.3F : 1F);
                    SPIRES.add(new Spire(c.add(o.scale(rr)).subtract(0, 0.3, 0), o.scale(0.55).add(0, 1, 0).normalize(), len, len * 0.2F, now + hs(i, p.id + 313) * 3, 40 + hs(i, p.id + 314) * 10, 3, seedN++ * 7919));
                }
                for (int arm = 0; arm < 6; arm++) {   // and a great one at each point of the flake
                    double an = rot + arm * TAU / 6;
                    Vec3 o = new Vec3(Math.cos(an), 0, Math.sin(an));
                    spire(c.add(o.scale(R * 0.82)).subtract(0, 0.3, 0), o.scale(0.25).add(0, 1, 0), 3.6F, 0.7F, now, 46, 4);
                    shards(c.add(o.scale(R * 0.8)).add(0, 0.5, 0), o.scale(0.08).add(0, 0.2, 0), 0.2F, 8, 0.1F, now);
                }
                mist(c.add(0, 0.8, 0), Vec3.ZERO, (float) R * 0.5F, now, 40);
            }
            // the dome of cold sweeping out (thin rims only: you can see through it)
            double sr = R * 1.05 * FxBase.smooth(Mth.clamp(after / 7, 0, 1));
            float da = a * (float) Math.max(0, 1 - after / 12.0);
            for (int lat = 0; lat < 5; lat++) {
                double phi = lat * (Math.PI / 2) / 5;
                gring(m, cam, c.add(0, Math.sin(phi) * sr * 0.55, 0), Math.cos(phi) * sr, 0.18, da * (lat == 0 ? 0.9F : 0.45F), lat == 0 ? W_CORE : FROZEN);
            }
            gglow(m, cam, c.add(0, 1.0, 0), 2.5 + after * 0.3, 0.6F * (float) Math.max(0, 1 - after / 5.0), W_CORE);
        }
    }

    // ================================================================== 23 Moonfall
    /**
     * The Moon comes down. A great pale moon of ice opens in the sky over the place, a sigil of cold turning on the
     * ground under it; cracks of light split its face; then it falls, crystals bristling off it and the air ringing
     * round it, and lands - a white flash, a dome of cold, two waves of spires bursting up round the crater.
     */
    private static void moon(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Minecraft mc, L l, double age, double now, boolean tick) {
        FxPacket p = l.p;
        double hang = Math.max(1, p.sweep), hit = Math.max(hang + 2, p.speed);
        double R = p.r;
        Vec3 ab = p.u.subtract(p.c), dir = ab.normalize();
        if (age < hit) {
            Vec3 pos;
            double fallK = 0;
            if (age < hang) pos = p.c.add(0, Math.sin(now * 0.08) * 0.15, 0);
            else { fallK = (age - hang) / (hit - hang); pos = p.c.add(ab.scale(fallK * fallK)); }
            double grow = FxBase.backOut(Mth.clamp(age / 8, 0, 1));
            double rr = R * grow;
            double crack = Mth.clamp((age - hang * 0.35) / (hang * 0.65), 0, 1);
            // its halo and the shaft of cold light down to the place
            gglow(m, cam, pos, rr * 2.4, 0.3F, W_SOFT);
            gglow(m, cam, pos, rr * 1.4, 0.35F, FROZEN);
            gseg(m, cam, pos, p.u, rr * 0.5, 0.18F * (float) grow, 0.32F * (float) grow, W_SOFT);
            sphere(vc, m, n, cam, pos, rr, now * 0.01 + p.id, 0.95F);
            gglow(m, cam, pos, rr * 0.75, 0.35F + 0.4F * (float) crack, W_CORE);   // the light inside it
            // craters: rings of rime on its face
            Vec3 toCam = cam.subtract(pos).normalize();
            for (int i = 0; i < 7; i++) {
                Vec3 nrm = new Vec3(hs(i, p.id + 400) - 0.5, hs(i, p.id + 401) - 0.5, hs(i, p.id + 402) - 0.5).normalize();
                if (nrm.dot(toCam) < 0.15) nrm = nrm.add(toCam.scale(0.9)).normalize();
                double cr = rr * (0.12 + 0.16 * hs(i, p.id + 403));
                ringOn(m, cam, pos.add(nrm.scale(rr * 1.005)), nrm, cr, 0.05, 0.5F, W_SOFT);
            }
            // cracks of light spreading across it
            if (crack > 0) for (int i = 0; i < 6; i++) {
                Vec3 nrm0 = toCam.add(new Vec3(hs(i, p.id + 410) - 0.5, hs(i, p.id + 411) - 0.5, hs(i, p.id + 412) - 0.5).scale(0.6)).normalize();
                Vec3 prev = pos.add(nrm0.scale(rr * 1.01));
                Vec3 walk = nrm0;
                int steps = (int) Math.ceil(6 * crack);
                for (int j = 1; j <= steps; j++) {
                    walk = walk.add(new Vec3(hs(i * 9 + j, p.id + 413) - 0.5, hs(i * 9 + j, p.id + 414) - 0.5, hs(i * 9 + j, p.id + 415) - 0.5).scale(0.45)).normalize();
                    Vec3 q = pos.add(walk.scale(rr * 1.01));
                    float ca = (float) (0.95 - 0.1 * j) * (0.6F + 0.4F * (float) fallK);
                    gseg(m, cam, prev, q, rr * 0.045, ca, ca, W_CORE);
                    gseg(m, cam, prev, q, rr * 0.12, ca * 0.4F, ca * 0.4F, FROZEN);
                    prev = q;
                }
            }
            if (age >= hang) {   // falling: crystals bristle off its back, the air rings behind it, a long trail
                Vec3[] f = frame(dir);
                for (int i = 0; i < 10; i++) {
                    double an = i * TAU / 10 + p.id;
                    Vec3 o = f[0].scale(Math.cos(an)).add(f[1].scale(Math.sin(an)));
                    crystal(vc, m, n, cam, pos.add(o.scale(rr * 0.7)).subtract(dir.scale(rr * 0.3)), o.scale(0.7).subtract(dir.scale(0.7)).normalize(), rr * (0.7 + 0.4 * hs(i, p.id + 420)), rr * 0.16, 5, i + p.id, 0.9F, true);
                }
                gseg(m, cam, pos.subtract(dir.scale(rr * 9)), pos, rr * 0.9, 0, 0.5F, W_SOFT);
                gseg(m, cam, pos.subtract(dir.scale(rr * 6)), pos, rr * 0.3, 0, 0.9F, W_CORE);
                for (int k = 1; k <= 3; k++) {
                    double back = ((age * 0.9 + k * 1.6) % 5) * rr;
                    ringOn(m, cam, pos.subtract(dir.scale(back + rr)), dir, rr * (1.1 + back / rr * 0.25), 0.12, (float) (0.6 * (1 - back / (5 * rr))), W_SOFT);
                }
                if (tick) shards(pos.subtract(dir.scale(rr)), dir.scale(-0.06), 0.25F, 3, 0.12F, now);
            }
            // the sigil on the ground, turning and closing in
            double sk = Mth.clamp(age / hit, 0, 1);
            Vec3 g = p.u.add(0, 0.05, 0);
            double sr = R * 2.4 * (1.15 - 0.3 * sk);
            gring(m, cam, g, sr, 0.25, 0.55F * (float) sk + 0.15F, FROZEN);
            gring(m, cam, g, sr * 0.72, 0.12, 0.6F * (float) sk + 0.1F, W_CORE);
            double spin = now * 0.04;
            for (int k = 0; k < 12; k++) {
                double an = spin + k * TAU / 12;
                Vec3 o = new Vec3(Math.cos(an), 0, Math.sin(an));
                gseg(m, cam, g.add(o.scale(sr * 0.76)), g.add(o.scale(sr * (k % 3 == 0 ? 0.98 : 0.88))), 0.04, 0.7F * (float) sk + 0.15F, 0.3F, W_CORE);
            }
            for (int k = 0; k < 2; k++) {   // a crescent within, turning the other way
                double an0 = -spin * 1.5 + k * Math.PI;
                Vec3 prev = null;
                for (int j = 0; j <= 10; j++) {
                    double an = an0 + j * Math.PI / 10 * 0.9;
                    Vec3 q = g.add(Math.cos(an) * sr * 0.45, 0, Math.sin(an) * sr * 0.45);
                    if (prev != null) gseg(m, cam, prev, q, 0.05, 0.6F * (float) sk, 0.6F * (float) sk, W_SOFT);
                    prev = q;
                }
            }
            return;
        }
        double t = age - hit;
        if (!l.burst) {   // impact
            l.burst = true;
            FxPacket fr = new FxPacket();
            fr.h = 5; fr.c = p.u; fr.r = (float) R * 3.4F; fr.speed = 10; fr.life = Math.max(40, p.life - (int) hit); fr.id = p.id + 1;
            LIVE.add(new L(fr, now));
            for (int wave = 0; wave < 2; wave++) {   // two rings of great spires thrown up round the crater, the outer one a moment later
                int N = wave == 0 ? 14 : 22;
                for (int i = 0; i < N; i++) {
                    double an = i * TAU / N + hs(i + wave * 50, p.id) * 0.3;
                    double rr = R * (wave == 0 ? 1.3 : 2.6) * (1 + 0.15 * hs(i + 20, p.id));
                    Vec3 o = new Vec3(Math.cos(an), 0, Math.sin(an));
                    float len = (float) (R * (wave == 0 ? 1.05 : 0.7) * (1 + 0.5 * hs(i + 40, p.id)));
                    SPIRES.add(new Spire(p.u.add(o.scale(rr)).subtract(0, 0.3, 0), o.scale(wave == 0 ? 0.6 : 0.8).add(0, 1, 0).normalize(), len, len * 0.22F, now + wave * 5, 34 + hs(i, 7) * 12, 3, seedN++ * 7919));
                }
            }
            spire(p.u.subtract(0, 0.4, 0), UP, (float) R * 2.2F, (float) R * 0.5F, now, 48, 4);
            shards(p.u.add(0, 0.6, 0), new Vec3(0, 0.3, 0), 0.35F, 60, 0.14F, now);
            mist(p.u.add(0, 1, 0), Vec3.ZERO, (float) R * 3.4F, now, 40);
        }
        float a = (float) Mth.clamp(1 - t / 16.0, 0, 1);
        gglow(m, cam, p.u.add(0, 1.5, 0), R * (2.5 + t * 0.6), 0.85F * (float) Math.max(0, 1 - t / 6.0), W_CORE);
        double dome = R * (1 + t * 1.1);
        for (int lat = 0; lat < 4; lat++) {   // the dome of cold rolling out
            double phi = lat * (Math.PI / 2) / 4;
            gring(m, cam, p.u.add(0, 0.06 + Math.sin(phi) * dome * 0.5, 0), Math.cos(phi) * dome, 0.35, a * (lat == 0 ? 0.85F : 0.4F), lat == 0 ? W_CORE : FROZEN);
        }
        gring(m, cam, p.u.add(0, 0.07, 0), R * (0.8 + t * 0.7), 0.3, 0.5F * a, W_ICE);
        for (int i = 0; i < 24; i++) {   // light thrown up out of the crater
            double an = i * TAU / 24;
            Vec3 b = p.u.add(Math.cos(an) * R * (0.6 + t * 0.25), t * 0.7, Math.sin(an) * R * (0.6 + t * 0.25));
            gseg(m, cam, b, b.add(0, 1.6 + hs(i, 99) * 2, 0), 0.06, 0.9F * a, 0, W_CORE);
        }
        // a moon-pale glow lingering over the crater
        float linger = env(t, p.life - hit, 4, 20);
        gring(m, cam, p.u.add(0, 0.08, 0), R * 2.4, 0.4, 0.3F * linger, FROZEN);
    }

    /** A ball of clear ice (the moon): flat-shaded facets lit like cut ice. */
    private static void sphere(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 c, double r, double turn, float a) {
        int lon = 16, lat = 10;
        for (int i = 0; i < lat; i++) {
            double t0 = -Math.PI / 2 + i * Math.PI / lat, t1 = t0 + Math.PI / lat;
            for (int j = 0; j < lon; j++) {
                double p0 = turn + j * TAU / lon, p1 = p0 + TAU / lon;
                Vec3 v00 = sp(t0, p0), v01 = sp(t0, p1), v10 = sp(t1, p0), v11 = sp(t1, p1);
                Vec3 nrm = v00.add(v01).add(v10).add(v11).normalize();
                q(vc, m, n, cam, c.add(v00.scale(r)), c.add(v01.scale(r)), c.add(v11.scale(r)), c.add(v10.scale(r)), nrm, a, 1.15F);
            }
        }
    }

    private static Vec3 sp(double t, double p) { return new Vec3(Math.cos(t) * Math.cos(p), Math.sin(t), Math.cos(t) * Math.sin(p)); }

    /** A glowing ring of radius r round {@code nrm} at c (in any orientation, unlike gring which lies flat). */
    private static void ringOn(Matrix4f m, Vec3 cam, Vec3 c, Vec3 nrm, double r, double w, float a, float[] col) {
        if (a <= 0.01F) return;
        Vec3[] f = frame(nrm);
        int seg = 24;
        Vec3 prev = null;
        for (int i = 0; i <= seg; i++) {
            double an = i * TAU / seg;
            Vec3 q = c.add(f[0].scale(Math.cos(an) * r)).add(f[1].scale(Math.sin(an) * r));
            if (prev != null) gseg(m, cam, prev, q, w, a, a, col);
            prev = q;
        }
    }
}
