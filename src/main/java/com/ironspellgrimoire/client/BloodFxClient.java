package com.ironspellgrimoire.client;

import com.ironspellgrimoire.network.FxPacket;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * The Blood Grimoire, drawn as liquid: real, solid geometry in glossy dark red - lit by the world and the key light,
 * with a sharp wet highlight - not glowing light. Blood flows in streams, flies in drops that fall under gravity and
 * splash into pools on the ground, hardens into thorns and needles that melt back into blood, rolls as a curling
 * wave, and soaks away as the pools dry.
 * <p>The Scarlet Moon is the one thing here that glows: a white aura (white flame licking up the body, spiralling
 * streaks, rising sparks, crackling arcs, a ground glow that beats like a heart) and its white awakening.
 * <p>Fed by FxPacket shape 80, {@code h} = kind:
 * <ul>
 *   <li>0 stream: c to u (v.x / v.y = entities the end / start follow), r = thickness, r2 = arc height, speed = ticks to reach the end</li>
 *   <li>1 spray: c, d = mean velocity per tick, r = spread, sweep = drops, a0 = drop size</li>
 *   <li>2 blade: c, d = forward, u = up, r = radius, speed = ticks of the swing, a1 = 1 white edge</li>
 *   <li>3 thorn: c = base, d = direction, r = length, r2 = width, sweep = ticks to grow</li>
 *   <li>4 wave: c = ground, d = forward, r = half width, r2 = height, speed = blocks per tick</li>
 *   <li>5 pool: c, r</li>
 *   <li>6 shell: v.x = entity, r = radius (ended by kind 99 it bursts outward; otherwise it melts)</li>
 *   <li>7 needle: c to u, r = length, speed = ticks of flight</li>
 *   <li>8 thread: v.x = caster, v.y = target - blood drawn along it into the caster</li>
 *   <li>9 white aura: v.x = entity</li>
 *   <li>10 white awakening: c = ground, r = reach, sweep = ticks of gathering light</li>
 *   <li>11 bleeding: v.x = entity - it drips blood</li>
 *   <li>99 end the effect with this id early</li>
 * </ul>
 */
public final class BloodFxClient {
    private BloodFxClient() {}

    private static final double TAU = Math.PI * 2;
    private static final float[] DEEP = {0.16F, 0.0F, 0.01F}, MID = {0.46F, 0.012F, 0.03F}, SPEC = {1.0F, 0.72F, 0.72F};
    private static final float[] W_CORE = {1.0F, 1.0F, 1.0F}, W_SOFT = {0.82F, 0.88F, 1.0F};
    private static final Vec3 UP = new Vec3(0, 1, 0);

    private static final class L {
        final FxPacket p;
        final double born;
        double killAt = -1, lastSpawn;
        boolean done;
        L(FxPacket p, double born) { this.p = p; this.born = born; this.lastSpawn = born; }
    }

    private static final class Drop {
        Vec3 pos, vel;
        final float r;
        final double born;
        Drop(Vec3 pos, Vec3 vel, float r, double born) { this.pos = pos; this.vel = vel; this.r = r; this.born = born; }
    }

    private static final class Pool {
        final Vec3 c;
        final float r;
        final double born, life;
        final int seed;
        Pool(Vec3 c, float r, double born, double life, int seed) { this.c = c; this.r = r; this.born = born; this.life = life; this.seed = seed; }
    }

    /** A splash where a drop lands hard: a crown of blood thrown up round the impact, a ripple, then it falls. */
    private static final class Crown {
        final Vec3 c;
        final float r;
        final double born;
        final int seed;
        Crown(Vec3 c, float r, double born, int seed) { this.c = c; this.r = r; this.born = born; this.seed = seed; }
    }

    /** A puff of fine blood mist hanging in the air after a hit. */
    private static final class Mist {
        Vec3 c;
        final Vec3 vel;
        final float r;
        final double born, life;
        Mist(Vec3 c, Vec3 vel, float r, double born, double life) { this.c = c; this.vel = vel; this.r = r; this.born = born; this.life = life; }
    }

    private static final List<L> LIVE = new ArrayList<>();
    private static final List<Crown> CROWNS = new ArrayList<>();
    private static final List<Mist> MISTS = new ArrayList<>();
    private static final List<Drop> DROPS = new ArrayList<>();
    private static final List<Pool> POOLS = new ArrayList<>();
    private static double lastNow = -1;
    private static int seedN = 1;

    // ================================================================== feed
    public static void spawn(FxPacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        double now = mc.level.getGameTime();
        int k = (int) p.h;
        if (k == 99) { for (L l : LIVE) if (l.p.id == p.id && l.killAt < 0) l.killAt = now; return; }
        if (k == 1) { spray(p.c, p.d, p.r, Math.max(1, p.sweep), p.a0 > 0 ? p.a0 : 0.07F, now); return; }
        if (k == 5) { pool(p.c, p.r, now, Math.max(20, p.life)); return; }
        if (k == 13) { burst(mc, p, now); }
        if (k == 3) {   // a thorn bursts out of the ground: the ground splashes round it
            crown(p.c, p.r2 * 3.2F, now);
            mist(p.c.add(0, 0.3, 0), new Vec3(0, 0.03, 0), 0.5F, now, 18);
        }
        if (LIVE.size() > 400) LIVE.remove(0);
        if (k == 0) System.out.println("ISGDBG stream spawn " + p.c + " " + p.u + " v=" + p.v + " life=" + p.life + " speed=" + p.speed + " r=" + p.r);
        LIVE.add(new L(p, now));
    }

    static void clear() { LIVE.clear(); DROPS.clear(); POOLS.clear(); CROWNS.clear(); MISTS.clear(); }

    private static void spray(Vec3 c, Vec3 v, float spread, int n, float size, double now) {
        java.util.concurrent.ThreadLocalRandom r = java.util.concurrent.ThreadLocalRandom.current();
        for (int i = 0; i < n; i++) {
            Vec3 jit = new Vec3(r.nextGaussian(), r.nextGaussian() * 0.6 + 0.3, r.nextGaussian()).scale(spread);
            drop(c.add(r.nextGaussian() * 0.08, r.nextGaussian() * 0.08, r.nextGaussian() * 0.08), v.add(jit), size * (0.6F + r.nextFloat() * 0.8F), now);
        }
    }

    private static void drop(Vec3 at, Vec3 vel, float r, double now) {
        if (DROPS.size() > 500) DROPS.remove(0);
        DROPS.add(new Drop(at, vel, r, now));
    }

    private static void crown(Vec3 c, float r, double now) {
        if (CROWNS.size() > 60) CROWNS.remove(0);
        CROWNS.add(new Crown(c, r, now, seedN++ * 104729));
    }

    private static void mist(Vec3 c, Vec3 vel, float r, double now, double life) {
        if (MISTS.size() > 80) MISTS.remove(0);
        MISTS.add(new Mist(c, vel, r, now, life));
    }

    /** A hit: blood thrown out of the wound the way the blow went, a puff of mist, and a splash on the ground below. */
    private static void burst(Minecraft mc, FxPacket p, double now) {
        float s = p.a0 > 0 ? p.a0 : 1F;
        Vec3 d = p.d.lengthSqr() < 1e-6 ? Vec3.ZERO : p.d.normalize();
        spray(p.c, d.scale(0.16 * s).add(0, 0.1, 0), 0.07F * s, (int) (8 + 8 * s), 0.055F + 0.02F * s, now);
        spray(p.c, d.scale(0.32 * s).add(0, 0.04, 0), 0.03F, (int) (3 + 3 * s), 0.035F, now);   // a fast jet straight out
        mist(p.c, d.scale(0.03).add(0, 0.01, 0), 0.45F * s, now, 16);
        mist(p.c.add(d.scale(0.4)), d.scale(0.05), 0.3F * s, now, 12);
        double g = ground(mc, p.c);
        if (p.c.y - g < 3) crown(new Vec3(p.c.x + d.x * 0.6, g, p.c.z + d.z * 0.6), 0.35F + 0.25F * s, now);
    }

    private static void pool(Vec3 c, float r, double now, double life) {
        if (POOLS.size() > 180) POOLS.remove(0);
        POOLS.add(new Pool(c, r, now, life, seedN++ * 7919));
    }

    // ================================================================== frame
    static void draw(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, double now, float pt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { clear(); return; }
        double dt = lastNow < 0 ? 0 : Mth.clamp(now - lastNow, 0, 3);
        lastNow = now;
        if (LIVE.isEmpty() && DROPS.isEmpty() && POOLS.isEmpty() && CROWNS.isEmpty() && MISTS.isEmpty()) return;
        sky = mc.level.getSkyDarken(pt);

        // pools first (they lie under everything)
        Iterator<Pool> pi = POOLS.iterator();
        while (pi.hasNext()) {
            Pool q = pi.next();
            double age = now - q.born;
            if (age > q.life) { pi.remove(); continue; }
            drawPool(vc, m, n, cam, q, age);
        }
        // drops fall, and splash where they land
        Iterator<Drop> di = DROPS.iterator();
        while (di.hasNext()) {
            Drop d = di.next();
            if (dt > 0) {
                d.vel = d.vel.scale(Math.pow(0.985, dt)).add(0, -0.045 * dt, 0);
                d.pos = d.pos.add(d.vel.scale(dt));
            }
            double g = ground(mc, d.pos);
            if (d.pos.y <= g || now - d.born > 90) {
                di.remove();
                if (d.pos.y <= g + 0.3) {
                    pool(new Vec3(d.pos.x, g, d.pos.z), d.r * (3.2F + (float) Math.min(1.5, d.vel.length() * 2)), now, 60 + d.r * 300);
                    if (d.vel.y < -0.22 && d.r > 0.045 && Math.random() < 0.55) crown(new Vec3(d.pos.x, g, d.pos.z), d.r * 3.6F, now);   // it lands hard: a little crown
                }
                continue;
            }
            drip(vc, m, n, cam, d.pos, d.vel, d.r);
        }
        Iterator<Crown> ci = CROWNS.iterator();
        while (ci.hasNext()) {
            Crown cr = ci.next();
            double age = now - cr.born;
            if (age > 12) { ci.remove(); continue; }
            drawCrown(vc, m, n, cam, mc, cr, age, now);
        }
        Iterator<Mist> mi = MISTS.iterator();
        while (mi.hasNext()) {
            Mist ms = mi.next();
            double age = now - ms.born;
            if (age > ms.life) { mi.remove(); continue; }
            if (dt > 0) ms.c = ms.c.add(ms.vel.scale(dt * Math.max(0, 1 - age / ms.life)));
            drawMist(vc, m, n, cam, mc, ms, age);
        }
        List<L> add = null;
        Iterator<L> it = LIVE.iterator();
        while (it.hasNext()) {
            L l = it.next();
            double age = now - l.born;
            double end = l.killAt >= 0 ? l.killAt + 2 : l.born + l.p.life;
            if (now > end || l.done) { it.remove(); continue; }
            float out = l.killAt >= 0 ? (float) Mth.clamp((l.killAt + 2 - now) / 2.0, 0, 1) : 1F;
            boolean tick = Math.floor(now) > Math.floor(l.lastSpawn);
            if (tick) l.lastSpawn = now;
            switch ((int) l.p.h) {
                case 0 -> stream(vc, m, n, cam, mc, l, age, now, pt, tick);
                case 2 -> blade(vc, m, n, cam, l, age, now, tick);
                case 3 -> thorn(vc, m, n, cam, l, age, now);
                case 4 -> wave(vc, m, n, cam, mc, l, age, now, tick);
                case 6 -> shell(vc, m, n, cam, mc, l, age, now, pt, out);
                case 7 -> needle(vc, m, n, cam, l, age, now);
                case 8 -> thread(vc, m, n, cam, mc, l, age, now, pt, tick);
                case 9 -> whiteAura(m, cam, mc, l, age, now, pt, out);
                case 10 -> awakening(m, cam, l, age, now);
                case 11 -> bleeding(vc, m, n, cam, mc, l, age, now, pt, tick);
                case 12 -> vein(vc, m, n, cam, mc, l, age, now, tick);
                case 13 -> spatter(m, cam, l, age);
                case 14 -> sigil(vc, m, n, cam, mc, l, age, now, tick);
                default -> l.done = true;
            }
        }
    }

    // ================================================================== the look of blood
    private static float sky = 1F;
    private static float light = 1F;

    /** How lit the blood is where it is: it's matter, not light - dark at night and in caves. */
    private static void lightAt(Minecraft mc, Vec3 at) {
        int pk = LevelRenderer.getLightColor(mc.level, BlockPos.containing(at));
        float bl = ((pk >> 4) & 15) / 15F, sk = ((pk >> 20) & 15) / 15F * sky;
        light = 0.3F + 0.7F * Math.max(bl, sk);
    }

    /** Wet blood: deep red, brighter where the key light falls, a sharp pale highlight where it reflects toward you. */
    private static float[] wet(Vec3 nrm, Vec3 view, float tone) {
        float lam = (float) Math.max(0, nrm.dot(FxBase.LIGHT));
        float k = (0.3F + 0.7F * lam) * tone;
        Vec3 refl = view.subtract(nrm.scale(2 * view.dot(nrm)));
        float spec = (float) Math.pow(Math.max(0, refl.dot(FxBase.LIGHT)), 28) * 0.95F;
        float fres = (float) Math.pow(1 - Math.abs(nrm.dot(view)), 3) * 0.18F;
        float[] c = new float[3];
        for (int i = 0; i < 3; i++) c[i] = Math.min(1F, (DEEP[i] + (MID[i] - DEEP[i]) * k) * light + (SPEC[i] * spec + 0.4F * fres) * (0.35F + 0.65F * light));
        return c;
    }

    private static void v(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 w, Vec3 nrm, float a, float tone) {
        Vec3 view = w.subtract(cam);
        double l = view.length();
        view = l < 1e-6 ? new Vec3(0, 0, 1) : view.scale(1 / l);
        FxBase.vtx(vc, m, n, cam, w, wet(nrm, view, tone), a);
    }

    private static void tri(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 a, Vec3 na, Vec3 b, Vec3 nb, Vec3 c, Vec3 nc, float al, float tone) {
        v(vc, m, n, cam, a, na, al, tone); v(vc, m, n, cam, b, nb, al, tone); v(vc, m, n, cam, c, nc, al, tone); v(vc, m, n, cam, c, nc, al, tone);
    }

    private static void q(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 a, Vec3 na, Vec3 b, Vec3 nb, Vec3 c, Vec3 nc, Vec3 d, Vec3 nd, float al, float tone) {
        v(vc, m, n, cam, a, na, al, tone); v(vc, m, n, cam, b, nb, al, tone); v(vc, m, n, cam, c, nc, al, tone); v(vc, m, n, cam, d, nd, al, tone);
    }

    private static Vec3[] frame(Vec3 axis) {
        Vec3 e1 = Math.abs(axis.y) > 0.9 ? new Vec3(1, 0, 0) : UP;
        e1 = e1.subtract(axis.scale(e1.dot(axis))).normalize();
        return new Vec3[]{e1, axis.cross(e1)};
    }

    /** A smooth liquid tube through the points, radius per point (zero = a pointed end). */
    private static void tube(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3[] c, double[] rad, int sides, float a, float tone) {
        int k = c.length;
        if (k < 2) return;
        Vec3[][] ring = new Vec3[k][sides], nr = new Vec3[k][sides];
        Vec3 ref = null;
        for (int i = 0; i < k; i++) {
            Vec3 t = c[Math.min(k - 1, i + 1)].subtract(c[Math.max(0, i - 1)]);
            if (t.lengthSqr() < 1e-10) t = new Vec3(0, 0, 1);
            t = t.normalize();
            Vec3 e1;
            if (ref == null) e1 = frame(t)[0];
            else { e1 = ref.subtract(t.scale(ref.dot(t))); e1 = e1.lengthSqr() < 1e-8 ? frame(t)[0] : e1.normalize(); }
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
            q(vc, m, n, cam, ring[i][s], nr[i][s], ring[i + 1][s], nr[i + 1][s], ring[i + 1][s2], nr[i + 1][s2], ring[i][s2], nr[i][s2], a, tone);
        }
    }

    /** One drop: a glossy ball pulled into a teardrop along the way it's moving. */
    private static void drip(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 c, Vec3 vel, float r) {
        if (c.distanceToSqr(cam) > 48 * 48) return;
        double sp = vel.length();
        Vec3 ax = sp < 1e-4 ? new Vec3(0, -1, 0) : vel.scale(1 / sp);
        double stretch = 1 + Math.min(2.2, sp * 5);
        Vec3[] f = frame(ax);
        int lat = 4, lon = 6;
        Vec3[][] P = new Vec3[lat + 1][lon], N = new Vec3[lat + 1][lon];
        for (int i = 0; i <= lat; i++) {
            double th = Math.PI * i / lat, ca = Math.cos(th), sa = Math.sin(th);
            double along = ca * r * (ca < 0 ? stretch : 1);           // the tail behind it pulls out
            double wide = sa * r * (ca < 0 ? 1 - 0.35 * (-ca) * (stretch - 1) / 2.2 : 1);
            for (int j = 0; j < lon; j++) {
                double an = j * TAU / lon;
                Vec3 o = f[0].scale(Math.cos(an)).add(f[1].scale(Math.sin(an)));
                P[i][j] = c.add(ax.scale(along)).add(o.scale(wide));
                N[i][j] = ax.scale(ca).add(o.scale(sa)).normalize();
            }
        }
        for (int i = 0; i < lat; i++) for (int j = 0; j < lon; j++) {
            int j2 = (j + 1) % lon;
            q(vc, m, n, cam, P[i][j], N[i][j], P[i + 1][j], N[i + 1][j], P[i + 1][j2], N[i + 1][j2], P[i][j2], N[i][j2], 0.96F, 1F);
        }
    }

    /** A pool on the ground: an irregular glossy sheet with a raised meniscus, spreading, then soaking in and darkening. */
    private static void drawPool(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Pool q, double age) {
        if (q.c.distanceToSqr(cam) > 40 * 40) return;
        float grow = (float) FxBase.smooth(age / 5.0);
        double dry = Mth.clamp((age - q.life * 0.6) / (q.life * 0.4), 0, 1);
        float a = (float) (0.94 * (1 - dry * dry));
        float tone = (float) (1 - 0.55 * dry);
        double R = q.r * grow * (1 + 0.15 * dry);
        int seg = 14;
        double y = q.c.y + 0.015 + (q.seed % 7) * 0.0006;
        Vec3 ctr = new Vec3(q.c.x, y + 0.008, q.c.z);
        Vec3[] o = new Vec3[seg], in = new Vec3[seg];
        for (int i = 0; i < seg; i++) {
            double an = i * TAU / seg;
            double wob = 0.72 + 0.28 * FxBase.h2(q.seed, i, 3) + 0.12 * Math.sin(an * 3 + q.seed);
            o[i] = new Vec3(q.c.x + Math.cos(an) * R * wob, y, q.c.z + Math.sin(an) * R * wob);
            in[i] = new Vec3(q.c.x + Math.cos(an) * R * wob * 0.8, y + 0.012, q.c.z + Math.sin(an) * R * wob * 0.8);
        }
        for (int i = 0; i < seg; i++) {
            int j = (i + 1) % seg;
            tri(vc, m, n, cam, ctr, UP, in[i], UP, in[j], UP, a, tone);
            Vec3 ni = in[i].subtract(ctr).normalize().scale(0.6).add(UP).normalize(), nj = in[j].subtract(ctr).normalize().scale(0.6).add(UP).normalize();
            q(vc, m, n, cam, in[i], ni, o[i], ni, o[j], nj, in[j], nj, a, tone);
        }
    }

    private static double ground(Minecraft mc, Vec3 at) {
        return mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(at.x), Mth.floor(at.z));
    }

    private static Vec3 follow(Minecraft mc, double id, Vec3 fallback, float pt, double up) {
        if (id == 0) return fallback;
        Entity e = mc.level.getEntity((int) id);
        if (e == null) return fallback;
        return new Vec3(Mth.lerp(pt, e.xo, e.getX()), Mth.lerp(pt, e.yo, e.getY()) + e.getBbHeight() * up, Mth.lerp(pt, e.zo, e.getZ()));
    }

    private static float env(double age, double life, double in, double fade) {
        return (float) (Mth.clamp(age / in, 0, 1) * Mth.clamp((life - age) / fade, 0, 1));
    }

    // ================================================================== 0 stream
    private static Vec3 arc(Vec3 a, Vec3 b, double h, double t, double wob, double now, Vec3 side) {
        return a.add(b.subtract(a).scale(t)).add(0, 4 * h * t * (1 - t), 0).add(side.scale(Math.sin(t * 9 - now * 0.7) * wob * Math.sin(t * Math.PI)));
    }

    private static void stream(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Minecraft mc, L l, double age, double now, float pt, boolean tick) {
        FxPacket p = l.p;
        Vec3 b = follow(mc, p.v.x, p.u, pt, 0.55), a = follow(mc, p.v.y, p.c, pt, 0.55);
        lightAt(mc, a.add(b).scale(0.5));
        double fly = Math.max(1, p.speed), life = p.life;
        double head = Mth.clamp(age / fly, 0, 1), tail = Mth.clamp((age - (life - fly)) / fly, 0, 1);
        if (age < 1.5 || (age > 8 && age < 8.3)) System.out.println("ISGDBG stream draw age=" + age + " head=" + head + " tail=" + tail + " a=" + a + " b=" + b);
        if (head - tail < 0.01) return;
        Vec3 side = b.subtract(a).cross(UP);
        side = side.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : side.normalize();
        int k = 18;
        Vec3[] c = new Vec3[k + 1];
        double[] r = new double[k + 1];
        for (int i = 0; i <= k; i++) {
            double t = tail + (head - tail) * i / k;
            c[i] = arc(a, b, p.r2, t, 0.18, now, side);
            double u = (double) i / k;
            r[i] = p.r * (0.55 + 0.45 * Math.sin(u * Math.PI)) * (0.85 + 0.15 * Math.sin(t * 14 - now * 0.9)) * (i == 0 && tail > 0 ? 0.2 : 1);
        }
        r[k] = p.r * 1.35;   // a swelling head
        tube(vc, m, n, cam, c, r, 6, 0.96F, 1F);
        glowAlong(m, cam, c, p.r * 2.6, 0.22F * (0.75F + 0.25F * beat(now)));
        drip(vc, m, n, cam, c[k], c[k].subtract(c[k - 1]), (float) (p.r * 1.3));
        if (tick && Math.random() < 0.6) {   // drops fall off it
            int i = 1 + (int) (Math.random() * (k - 1));
            drop(c[i], new Vec3(0, -0.02, 0), (float) (p.r * 0.7), now);
        }
    }

    // ================================================================== 2 blade
    private static void blade(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, L l, double age, double now, boolean tick) {
        FxPacket p = l.p;
        lightAt(Minecraft.getInstance(), p.c);
        double swing = Math.max(1, p.speed);
        // two thinning afterimages of where it just was, then the blade itself
        if (age < swing + 2) for (int g = 2; g >= 1; g--) if (age - g * 1.1 > 0) bladeSheet(vc, m, n, cam, p, age - g * 1.1, now, g == 1 ? 0.42F : 0.2F);
        Vec3[] c = bladeSheet(vc, m, n, cam, p, age, now, 0.97F);
        Vec3 d = p.d.normalize(), u = p.u.normalize();
        int seg = c.length - 1;
        double collapse = Mth.clamp((age - swing) / Math.max(1, p.life - swing), 0, 1);
        Vec3 o = p.c.add(d.scale(age * 0.12)).add(0, -collapse * collapse * 1.2, 0);
        // a dark crimson glow along the cutting lip
        Vec3[] lip = new Vec3[seg - 3];
        for (int i = 0; i < lip.length; i++) lip[i] = c[i + 2].add(c[i + 2].subtract(o).normalize().scale(0.05));
        glowAlong(m, cam, lip, 0.22, (float) (0.55 * (1 - collapse)));
        if (p.a1 > 0) {   // under the Moon: a white-hot edge on the leading side
            for (int i = seg / 2; i < seg; i++) FxBase.gquad(m, cam, c[i].add(u.scale(0.03)), c[i + 1].add(u.scale(0.03)), c[i + 1].subtract(u.scale(0.03)), c[i].subtract(u.scale(0.03)), W_CORE, 0.6F, 0.6F, 0.6F, 0.6F);
        }
        if (tick && age < swing) {   // drops flung off the leading tip, and mist off the edge
            Vec3 tip = c[seg - 1], fl = c[seg].subtract(c[seg - 2]).normalize();
            for (int i = 0; i < 3; i++) drop(tip, fl.scale(0.18 + Math.random() * 0.12).add(d.scale(0.08)).add(0, 0.05, 0), 0.04F + (float) Math.random() * 0.05F, now);
            mist(c[seg / 2], d.scale(0.04), 0.35F, now, 12);
        }
        if (!l.done && collapse > 0 && l.killAt < 0 && p.a0 == 0) {   // it falls apart into blood
            p.a0 = 1;
            for (int i = 1; i < seg; i++) drop(c[i], c[i].subtract(o).normalize().scale(0.06 + Math.random() * 0.06).add(d.scale(0.06)), 0.05F + (float) Math.random() * 0.06F, now);
        }
    }

    /** The crescent sheet of liquid at one moment of the swing; returns its centre line. */
    private static Vec3[] bladeSheet(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age, double now, float alpha) {
        Vec3 d = p.d.normalize(), u = p.u.normalize(), sd = d.cross(u).normalize();
        double swing = Math.max(1, p.speed), k = FxBase.smooth(age / swing);
        double span = Math.toRadians(150), front = -span / 2 + span * k, back = Math.max(-span / 2, front - Math.toRadians(125));
        double collapse = Mth.clamp((age - swing) / Math.max(1, p.life - swing), 0, 1);
        double R = p.r * (0.55 + 0.3 * k + 0.2 * collapse);
        Vec3 o = p.c.add(d.scale(age * 0.12)).add(0, -collapse * collapse * 1.2, 0);
        int seg = 22;
        Vec3[] c = new Vec3[seg + 1], out = new Vec3[seg + 1];
        double[] r = new double[seg + 1];
        double throb = 1 + 0.08 * beat(now);
        for (int i = 0; i <= seg; i++) {
            double t = (double) i / seg, an = back + (front - back) * t;
            out[i] = d.scale(Math.cos(an)).add(sd.scale(Math.sin(an)));
            c[i] = o.add(out[i].scale(R)).add(u.scale(Math.sin(t * 7 + now * 0.5) * 0.04));
            r[i] = p.r * throb * Math.pow(Math.sin(t * Math.PI), 0.6) * (0.35 + 0.65 * t) * (1 - collapse * 0.85);
        }
        // tall across the swing, thin through it, its outer edge thinned to a cutting lip
        for (int i = 0; i < seg; i++) {
            double h0 = r[i] * 0.16, h1 = r[i + 1] * 0.16, w0 = r[i] * 0.03, w1 = r[i + 1] * 0.03;
            Vec3 a0 = c[i], a1 = c[i + 1], o0 = out[i], o1 = out[i + 1];
            Vec3 t0 = a0.add(u.scale(h0)), t1 = a1.add(u.scale(h1)), b0 = a0.subtract(u.scale(h0)), b1 = a1.subtract(u.scale(h1));
            Vec3 f0 = a0.add(o0.scale(w0 * 2.5)), f1 = a1.add(o1.scale(w1 * 2.5)), k0 = a0.subtract(o0.scale(w0)), k1 = a1.subtract(o1.scale(w1));
            Vec3 nu0 = o0.add(u.scale(0.8)).normalize(), nu1 = o1.add(u.scale(0.8)).normalize(), nd0 = o0.subtract(u.scale(0.8)).normalize(), nd1 = o1.subtract(u.scale(0.8)).normalize();
            Vec3 iu0 = o0.scale(-1).add(u.scale(0.8)).normalize(), iu1 = o1.scale(-1).add(u.scale(0.8)).normalize(), id0 = o0.scale(-1).subtract(u.scale(0.8)).normalize(), id1 = o1.scale(-1).subtract(u.scale(0.8)).normalize();
            q(vc, m, n, cam, t0, nu0, t1, nu1, f1, o1, f0, o0, alpha, 1F);
            q(vc, m, n, cam, f0, o0, f1, o1, b1, nd1, b0, nd0, alpha, 1F);
            q(vc, m, n, cam, t1, iu1, t0, iu0, k0, o0.scale(-1), k1, o1.scale(-1), alpha, 0.85F);
            q(vc, m, n, cam, k1, o1.scale(-1), k0, o0.scale(-1), b0, id0, b1, id1, alpha, 0.85F);
        }
        return c;
    }

    // ================================================================== 3 thorn
    private static void thorn(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, L l, double age, double now) {
        FxPacket p = l.p;
        lightAt(Minecraft.getInstance(), p.c.add(0, 0.5, 0));
        double grow = FxBase.backOut(age / Math.max(1, p.sweep));
        double melt = Mth.clamp((age - p.life * 0.62) / (p.life * 0.38), 0, 1);
        if (melt > 0 && p.a0 == 0) {   // it loses its shape and runs down into a pool
            p.a0 = 1;
            pool(p.c, p.r2 * 2.6F, now, 120);
            for (int i = 0; i < 4; i++) drop(p.c.add(p.d.scale(p.r * (0.3 + 0.15 * i))), new Vec3((Math.random() - 0.5) * 0.06, 0, (Math.random() - 0.5) * 0.06), 0.06F, now);
        }
        double len = p.r * grow * (1 - melt * 0.92) * (1 + 0.04 * beat(now + p.id)), w = p.r2 * Math.min(1, grow) * (1 + melt * 0.6) * (1 - melt * 0.5);
        if (len < 0.02) return;
        Vec3 ax = p.d.normalize();
        Vec3[] f = frame(ax);
        Vec3 tip = p.c.add(ax.scale(len)).add(0, -melt * 0.1, 0);
        int s = 5;
        Vec3[] base = new Vec3[s], midR = new Vec3[s];
        for (int i = 0; i < s; i++) {
            double an = i * TAU / s + p.id;
            Vec3 o = f[0].scale(Math.cos(an)).add(f[1].scale(Math.sin(an)));
            base[i] = p.c.add(o.scale(w)).subtract(ax.scale(0.15));
            midR[i] = p.c.add(ax.scale(len * 0.35)).add(o.scale(w * 0.55)).add(rot(o, ax).scale(w * 0.12));
        }
        float tone = (float) (0.8 - 0.25 * melt);
        for (int i = 0; i < s; i++) {   // faceted, like blood set hard as glass
            int j = (i + 1) % s;
            Vec3 nl = midR[j].subtract(base[i]).cross(base[j].subtract(base[i])).normalize().scale(-1);
            if (nl.dot(base[i].subtract(p.c)) < 0) nl = nl.scale(-1);
            q(vc, m, n, cam, base[i], nl, base[j], nl, midR[j], nl, midR[i], nl, 0.97F, tone);
            Vec3 nt = tip.subtract(midR[i]).cross(midR[j].subtract(midR[i])).normalize();
            if (nt.dot(midR[i].subtract(p.c.add(ax.scale(len * 0.35)))) < 0) nt = nt.scale(-1);
            tri(vc, m, n, cam, midR[i], nt, midR[j], nt, tip, nt, 0.97F, tone);
        }
        float ga = (float) ((1 - melt) * (0.12 + 0.18 * beat(now + p.id)));   // a pulse of dark light running up its ridges
        for (int i = 0; i < s; i += 2) glowAlong(m, cam, new Vec3[]{base[i], midR[i], tip}, 0.07, ga);
        if (age < 3) glowAlong(m, cam, new Vec3[]{p.c, tip}, w * 2.2, (float) (0.5 * (1 - age / 3)));   // the flash as it bursts out
    }

    private static Vec3 rot(Vec3 o, Vec3 ax) { return ax.cross(o); }

    // ================================================================== 4 wave
    private static final double[][] PROFILE = {{-3.2, 0.0}, {-2.2, 0.18}, {-1.3, 0.5}, {-0.55, 0.86}, {0.05, 1.0}, {0.5, 0.92}, {0.78, 0.7}, {0.72, 0.5}, {0.5, 0.42}};

    private static void wave(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Minecraft mc, L l, double age, double now, boolean tick) {
        FxPacket p = l.p;
        Vec3 d = p.d.normalize(), sd = d.cross(UP).normalize();
        double x = age * p.speed;
        Vec3 front = p.c.add(d.scale(x));
        lightAt(mc, front.add(0, 1, 0));
        float e = env(age, p.life, 3, 5);
        double H = p.r2 * e;
        if (H < 0.05) return;
        int ws = 12, ps = PROFILE.length;
        Vec3[][] P = new Vec3[ws + 1][ps];
        for (int i = 0; i <= ws; i++) {
            double s = (double) i / ws * 2 - 1;
            double hk = 1 - Math.pow(Math.abs(s), 3) * 0.75;
            double lag = Math.sin(s * 4 + now * 0.45) * 0.18 + Math.abs(s) * 0.6;
            for (int j = 0; j < ps; j++) {
                double f = PROFILE[j][0] * (0.6 + 0.4 * e) - lag, y = PROFILE[j][1] * H * hk;
                P[i][j] = front.add(d.scale(f)).add(sd.scale(s * p.r)).add(0, y + 0.02, 0);
            }
        }
        for (int i = 0; i < ws; i++) for (int j = 0; j < ps - 1; j++) {
            Vec3 a = P[i][j], b = P[i + 1][j], c = P[i + 1][j + 1], dd = P[i][j + 1];
            Vec3 nr = c.subtract(a).cross(b.subtract(dd));
            nr = nr.lengthSqr() < 1e-8 ? UP : nr.normalize();
            if (nr.y < -0.2 && j < 5) nr = nr.scale(-1);
            q(vc, m, n, cam, a, nr, b, nr, c, nr, dd, nr, 0.95F, 1F);
            q(vc, m, n, cam, dd, nr.scale(-1), c, nr.scale(-1), b, nr.scale(-1), a, nr.scale(-1), 0.95F, 0.6F);   // its underside
        }
        Vec3[] crestLine = new Vec3[ws + 1];
        for (int i = 0; i <= ws; i++) crestLine[i] = P[i][6];
        glowAlong(m, cam, crestLine, 0.3, 0.35F * e);
        if (!l.done && age > p.life - 3 && p.a0 == 0) {   // at the end it crashes down: a wall of spray and a splash all along it
            p.a0 = 1;
            for (int i = 0; i <= ws; i += 2) {
                spray(P[i][5], d.scale(0.12).add(0, 0.15, 0), 0.09F, 4, 0.06F, now);
                crown(new Vec3(P[i][0].x, p.c.y, P[i][0].z).add(d.scale(3)), 0.6F, now);
            }
            mist(front.add(0, H * 0.6, 0), d.scale(0.05), 1.6F, now, 20);
        }
        if (tick) {
            for (int i = 0; i < 4; i++) {   // a curtain of drops falling off the curled lip
                Vec3 lip = P[1 + (int) (Math.random() * (ws - 1))][8];
                drop(lip, d.scale(0.05).add(0, -0.05, 0), 0.04F + (float) Math.random() * 0.04F, now);
            }
            if ((long) now % 2 == 0) mist(P[ws / 2][5].add(sd.scale((Math.random() * 2 - 1) * p.r * 0.6)), d.scale(0.06).add(0, 0.02, 0), 0.6F, now, 14);
            for (int i = 0; i < 3; i++) {   // spray off the crest
                Vec3 crest = P[1 + (int) (Math.random() * (ws - 1))][5];
                drop(crest, d.scale(0.15 + Math.random() * 0.1).add(0, 0.12 + Math.random() * 0.1, 0), 0.05F + (float) Math.random() * 0.05F, now);
            }
            if ((long) now % 2 == 0) for (int i = 0; i < 2; i++) {   // it leaves the ground wet
                Vec3 at = p.c.add(d.scale(Math.max(0, x - 2.5))).add(sd.scale((Math.random() * 2 - 1) * p.r * 0.8));
                pool(new Vec3(at.x, p.c.y, at.z), 0.5F + (float) Math.random() * 0.5F, now, 80);
            }
        }
    }

    // ================================================================== 6 shell
    private static void shell(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Minecraft mc, L l, double age, double now, float pt, float out) {
        FxPacket p = l.p;
        Vec3 c = follow(mc, p.v.x, p.c, pt, 0.5);
        lightAt(mc, c);
        if (l.killAt >= 0 && p.a0 == 0) {   // broken: it bursts outward
            p.a0 = 1;
            java.util.concurrent.ThreadLocalRandom r = java.util.concurrent.ThreadLocalRandom.current();
            for (int i = 0; i < 40; i++) {
                Vec3 o = new Vec3(r.nextGaussian(), r.nextGaussian() * 0.7 + 0.2, r.nextGaussian()).normalize();
                drop(c.add(o.scale(p.r)), o.scale(0.25 + r.nextDouble() * 0.15).add(0, 0.1, 0), 0.05F + r.nextFloat() * 0.06F, now);
            }
            return;
        }
        double melt = Mth.clamp((age - (p.life - 20)) / 20.0, 0, 1);
        if (melt > 0 && tick(now) && Math.random() < 0.8) drop(c.add((Math.random() - 0.5) * p.r, -p.r * 1.1, (Math.random() - 0.5) * p.r), new Vec3(0, -0.03, 0), 0.07F, now);
        float a = 0.42F * env(age, p.life, 4, 20) * out;
        double R = p.r * (0.6 + 0.4 * FxBase.smooth(age / 5.0));
        int lat = 9, lon = 14;
        Vec3[][] P = new Vec3[lat + 1][lon];
        Vec3[][] N = new Vec3[lat + 1][lon];
        for (int i = 0; i <= lat; i++) {
            double th = Math.PI * i / lat;
            for (int j = 0; j < lon; j++) {
                double an = j * TAU / lon;
                Vec3 o = new Vec3(Math.sin(th) * Math.cos(an), Math.cos(th), Math.sin(th) * Math.sin(an));
                double flow = Math.sin(th * 6 - now * 0.35 + j * 1.3) * 0.035 + Math.sin(an * 3 + now * 0.1) * 0.02;   // blood running down it
                double sag = melt * Math.max(0, -o.y) * 0.4;
                P[i][j] = c.add(o.x * R * (1 + flow), o.y * R * 1.2 * (1 + flow) - sag, o.z * R * (1 + flow));
                N[i][j] = o;
            }
        }
        for (int i = 0; i < lat; i++) for (int j = 0; j < lon; j++) {
            int j2 = (j + 1) % lon;
            q(vc, m, n, cam, P[i][j], N[i][j], P[i + 1][j], N[i + 1][j], P[i + 1][j2], N[i + 1][j2], P[i][j2], N[i][j2], a, 1.15F);
        }
        float hb = beat(now);
        for (int j = 0; j < lon; j += 2) {   // veins over its surface, beating
            Vec3[] v = new Vec3[lat + 1];
            for (int i = 0; i <= lat; i++) v[i] = P[i][(j + i / 3) % lon];
            glowAlong(m, cam, v, 0.06, (0.18F + 0.32F * hb) * a / 0.42F);
        }
    }

    private static double lastTickFrame = -1;
    private static boolean tick(double now) {
        if (Math.floor(now) > Math.floor(lastTickFrame)) { lastTickFrame = now; return true; }
        return false;
    }

    // ================================================================== 7 needle
    private static void needle(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, L l, double age, double now) {
        FxPacket p = l.p;
        double fly = Math.max(1, p.speed), k = Mth.clamp(age / fly, 0, 1);
        Vec3 ab = p.u.subtract(p.c);
        if (ab.lengthSqr() < 1e-6) { l.done = true; return; }
        Vec3 dir = ab.normalize(), pos = p.c.add(ab.scale(k));
        lightAt(Minecraft.getInstance(), pos);
        if (k >= 1 || l.killAt >= 0) {   // it bursts back into blood
            spray(pos, dir.scale(-0.06).add(0, 0.06, 0), 0.07F, 7, 0.05F, now);
            l.done = true;
            return;
        }
        double len = p.r;
        Vec3[] c = new Vec3[6];
        double[] r = {0, 0.045, 0.06, 0.05, 0.03, 0};
        for (int i = 0; i < 6; i++) c[i] = pos.subtract(dir.scale(len * (5 - i) / 5.0));
        tube(vc, m, n, cam, c, r, 5, 0.98F, 0.9F);
        glowAlong(m, cam, new Vec3[]{c[0], c[5]}, 0.16, 0.4F);
        // a liquid tail, thinning and wavering behind it
        int t = 8;
        Vec3[] tc = new Vec3[t];
        double[] tr = new double[t];
        Vec3 side = frame(dir)[0];
        double tl = Math.min(len * 2.2, ab.length() * k);
        for (int i = 0; i < t; i++) {
            double u = (double) i / (t - 1);
            tc[i] = pos.subtract(dir.scale(len + tl * (1 - u))).add(side.scale(Math.sin(u * 6 + now) * 0.03 * (1 - u)));
            tr[i] = 0.028 * u;
        }
        tube(vc, m, n, cam, tc, tr, 4, 0.9F, 1F);
    }

    // ================================================================== 8 thread
    private static void thread(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Minecraft mc, L l, double age, double now, float pt, boolean tick) {
        FxPacket p = l.p;
        Vec3 a = follow(mc, p.v.y, p.u, pt, 0.55), b = follow(mc, p.v.x, p.c, pt, 0.6);
        lightAt(mc, a.add(b).scale(0.5));
        float e = env(age, p.life, 4, 5);
        double reach = Mth.clamp(age / 4.0, 0, 1);
        Vec3 side = b.subtract(a).cross(UP);
        side = side.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : side.normalize();
        int k = 20;
        Vec3[] c = new Vec3[k + 1];
        double[] r = new double[k + 1];
        for (int i = 0; i <= k; i++) {
            double t = reach * i / k;
            c[i] = arc(a, b, 0.7, t, 0.12, now, side);
            r[i] = 0.03 * e * (0.7 + 0.3 * Math.sin(t * 20 - now * 1.4));
        }
        tube(vc, m, n, cam, c, r, 5, 0.95F, 1F);
        glowAlong(m, cam, c, 0.12, 0.25F * e * (0.6F + 0.4F * beat(now)));
        if (reach < 1) return;
        float beat = (float) Math.pow(Math.max(0, Math.sin(now * 0.35)), 4);
        for (int i = 0; i < 5; i++) {   // beads of blood drawn along it into the caster
            double t = ((now * 0.045 + i / 5.0) % 1.0);
            Vec3 at = arc(a, b, 0.7, t, 0.12, now, side), nx = arc(a, b, 0.7, Math.min(1, t + 0.02), 0.12, now, side);
            drip(vc, m, n, cam, at, nx.subtract(at).scale(3), (float) (0.065 + 0.03 * beat) * e);
        }
        if (tick && Math.random() < 0.4) drop(a.add((Math.random() - 0.5) * 0.4, 0, (Math.random() - 0.5) * 0.4), new Vec3(0, -0.02, 0), 0.05F, now);
    }

    // ================================================================== 11 bleeding
    private static void bleeding(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Minecraft mc, L l, double age, double now, float pt, boolean tick) {
        FxPacket p = l.p;
        Entity en = mc.level.getEntity((int) p.v.x);
        if (en == null) return;
        Vec3 feet = follow(mc, p.v.x, p.c, pt, 0);
        double h = en.getBbHeight(), w = en.getBbWidth() * 0.5 + 0.03;
        lightAt(mc, feet.add(0, h * 0.5, 0));
        for (int i = 0; i < 3; i++) {   // blood running down its sides
            double an = i * 2.1 + p.id;
            double t = ((age * 0.05 + i * 0.37) % 1.0);
            Vec3 o = new Vec3(Math.cos(an) * w, 0, Math.sin(an) * w);
            Vec3 top = feet.add(o).add(0, h * (0.75 - t * 0.6), 0), bot = top.add(0, -0.25 - t * 0.2, 0);
            tube(vc, m, n, cam, new Vec3[]{top, top.add(bot).scale(0.5).add(o.scale(0.05)), bot}, new double[]{0.0, 0.03, 0.045}, 4, 0.95F, 1F);
        }
        if (tick && Math.random() < 0.55) {
            double an = Math.random() * TAU;
            drop(feet.add(Math.cos(an) * w, h * (0.3 + Math.random() * 0.4), Math.sin(an) * w), new Vec3(Math.cos(an) * 0.02, 0, Math.sin(an) * 0.02), 0.05F + (float) Math.random() * 0.03F, now);
        }
    }

    // ================================================================== crimson glow, crowns, mist
    private static final float[] CRIMSON = {0.62F, 0.0F, 0.05F}, CRIMSON_HOT = {0.95F, 0.12F, 0.12F};

    /** A heartbeat: two quick pulses every 1.3 s, 0..1. */
    private static float beat(double now) {
        double t = now % 26;
        return (float) Math.min(1, Math.exp(-Math.pow(t / 1.4, 2)) + 0.6 * Math.exp(-Math.pow((t - 4.5) / 1.4, 2)));
    }

    /** A soft dark-crimson glow along a line: the life still in the blood. */
    private static void glowAlong(Matrix4f m, Vec3 cam, Vec3[] pts, double w, float a) {
        if (a <= 0.01F || pts.length < 2) return;
        double[] ww = new double[pts.length], wc = new double[pts.length];
        float[] al = new float[pts.length], ac = new float[pts.length];
        for (int i = 0; i < pts.length; i++) {
            double t = pts.length == 1 ? 0 : (double) i / (pts.length - 1);
            double tap = 0.5 + 0.5 * Math.sin(t * Math.PI);
            ww[i] = w * tap; wc[i] = w * 0.25 * tap;
            al[i] = a * 0.55F; ac[i] = a * 0.7F;
        }
        gstrip(m, cam, pts, ww, al, CRIMSON);
        gstrip(m, cam, pts, wc, ac, CRIMSON_HOT);
    }

    private static void drawCrown(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Minecraft mc, Crown cr, double age, double now) {
        if (cr.c.distanceToSqr(cam) > 32 * 32) return;
        lightAt(mc, cr.c.add(0, 0.3, 0));
        double k = age / 12.0;
        double R = cr.r * (0.35 + 0.75 * Math.sqrt(k)), rise = Math.sin(Math.min(1, k * 1.6) * Math.PI);
        float a = (float) (1 - k * k) * 0.96F;
        int spikes = 9;
        Vec3 base = cr.c.add(0, 0.02, 0);
        Vec3[] rim = new Vec3[spikes * 2 + 1];
        double[] rr = new double[rim.length];
        for (int i = 0; i <= spikes * 2; i++) {   // the thin wall of the crown
            double an = i * TAU / (spikes * 2) + cr.seed;
            rim[i] = base.add(Math.cos(an) * R, cr.r * 0.35 * rise, Math.sin(an) * R);
            rr[i] = cr.r * 0.07 * (1 - k);
        }
        tube(vc, m, n, cam, rim, rr, 4, a, 1F);
        for (int i = 0; i < spikes; i++) {   // the points of the crown, each with a bead of blood on top
            double an = i * TAU / spikes + cr.seed + 0.17;
            double hh = cr.r * (0.6 + 0.6 * FxBase.h2(cr.seed, i, 5)) * rise;
            if (hh < 0.01) continue;
            Vec3 o = new Vec3(Math.cos(an), 0, Math.sin(an));
            Vec3 b0 = base.add(o.scale(R)), b1 = b0.add(o.scale(cr.r * 0.25 * k)).add(0, hh, 0);
            tube(vc, m, n, cam, new Vec3[]{b0, b0.add(b1).scale(0.5), b1}, new double[]{cr.r * 0.07, cr.r * 0.05, cr.r * 0.03}, 4, a, 1F);
            drip(vc, m, n, cam, b1.add(0, cr.r * 0.06, 0), new Vec3(o.x * 0.02, 0.04 * (1 - 2 * k), o.z * 0.02), cr.r * 0.09F);
        }
        // a ripple running out across the ground
        double rp = cr.r * (0.5 + 2.2 * k);
        Vec3[] ring = new Vec3[25];
        double[] r2 = new double[25];
        for (int i = 0; i < 25; i++) {
            double an = i * TAU / 24;
            ring[i] = base.add(Math.cos(an) * rp, 0.01, Math.sin(an) * rp);
            r2[i] = 0.02 * (1 - k);
        }
        tube(vc, m, n, cam, ring, r2, 3, a * 0.8F, 0.9F);
    }

    /** Fine mist: a soft, dark, see-through cloud (never a glow), swelling and thinning away. */
    private static void drawMist(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Minecraft mc, Mist ms, double age) {
        Vec3 view = ms.c.subtract(cam);
        double dist = view.length();
        if (dist < 1.2 || dist > 40) return;
        lightAt(mc, ms.c);
        double k = age / ms.life;
        float a = (float) (Math.min(1, age / 2.0) * (1 - k) * (1 - k)) * 0.38F;
        double R = ms.r * (0.6 + 1.1 * Math.sqrt(k));
        Vec3[] f = frame(view.scale(1 / dist));
        float[] col = {0.36F * light, 0.01F * light, 0.03F * light};
        int seg = 12;
        for (int lay = 0; lay < 2; lay++) {   // two offset lobes so it's never a perfect circle
            Vec3 c = ms.c.add(f[0].scale(R * 0.25 * (lay == 0 ? 1 : -1))).add(f[1].scale(R * 0.15 * (lay == 0 ? -1 : 1)));
            double rr = R * (lay == 0 ? 1 : 0.75);
            for (int i = 0; i < seg; i++) {
                double a0 = i * TAU / seg, a1 = (i + 1) * TAU / seg;
                Vec3 p0 = c.add(f[0].scale(Math.cos(a0) * rr)).add(f[1].scale(Math.sin(a0) * rr)), p1 = c.add(f[0].scale(Math.cos(a1) * rr)).add(f[1].scale(Math.sin(a1) * rr));
                FxBase.vtx(vc, m, n, cam, c, col, a);
                FxBase.vtx(vc, m, n, cam, c, col, a);
                FxBase.vtx(vc, m, n, cam, p1, col, 0);
                FxBase.vtx(vc, m, n, cam, p0, col, 0);
            }
        }
    }

    // ================================================================== 12 vein: blood running along the ground
    private static Vec3 veinAt(FxPacket p, Vec3 ab, Vec3 side, double t, double len) {
        double wig = Math.sin(t * len * 1.7 + p.id) * 0.35 + Math.sin(t * len * 4.3 + p.id * 0.7) * 0.12;
        Vec3 q = p.c.add(ab.scale(t)).add(side.scale(wig * Math.min(1, t * 6)));
        return new Vec3(q.x, Mth.lerp(t, p.c.y, p.u.y) + 0.05, q.z);
    }

    private static void vein(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Minecraft mc, L l, double age, double now, boolean tick) {
        FxPacket p = l.p;
        Vec3 ab = p.u.subtract(p.c);
        double len = ab.length();
        if (len < 0.1) { l.done = true; return; }
        Vec3 side = ab.cross(UP).normalize();
        lightAt(mc, p.c.add(ab.scale(0.5)).add(0, 0.5, 0));
        double head = Mth.clamp(age / Math.max(1, p.speed), 0, 1);
        double drain = Mth.clamp((age - (p.life - 14)) / 14.0, 0, 1);
        float hb = beat(now);
        int k = Math.max(4, (int) (40 * head));
        Vec3[] c = new Vec3[k + 1];
        double[] r = new double[k + 1];
        for (int i = 0; i <= k; i++) {
            double t = head * i / k;
            c[i] = veinAt(p, ab, side, t, len);
            r[i] = 0.075 * (1 - drain * 0.8) * (0.8 + 0.25 * Math.sin(t * len * 3 - now * 0.9)) * (i == k ? 0.4 : 1);
        }
        tube(vc, m, n, cam, c, r, 5, 0.97F, (float) (1 - drain * 0.4));
        glowAlong(m, cam, c, 0.35, (float) ((0.22 + 0.3 * hb) * (1 - drain)));
        for (int b = 0; b < 7; b++) {   // branches split off it as the head passes, and grow out across the ground
            double tb = (b + 0.6) / 7.6;
            if (tb > head) break;
            double gl = Mth.clamp((head - tb) * 5, 0, 1) * (1 - drain);
            if (gl < 0.05) continue;
            Vec3 b0 = veinAt(p, ab, side, tb, len);
            double sgn = b % 2 == 0 ? 1 : -1;
            Vec3 dir = side.scale(sgn).add(ab.normalize().scale(0.6)).normalize();
            Vec3[] bc = new Vec3[5];
            double[] br = new double[5];
            for (int i = 0; i < 5; i++) {
                double u = i / 4.0 * gl;
                bc[i] = b0.add(dir.scale(u * 1.3)).add(side.scale(Math.sin(u * 5 + b) * 0.1));
                br[i] = 0.045 * (1 - i / 4.0) + 0.005;
            }
            tube(vc, m, n, cam, bc, br, 4, 0.97F, 0.9F);
        }
        for (int i = 0; i < 4; i++) {   // pulses of blood racing along it
            double t = ((now * 0.06 + i / 4.0) % 1.0) * head;
            Vec3 at = veinAt(p, ab, side, t, len), nx = veinAt(p, ab, side, Math.min(head, t + 0.01), len);
            drip(vc, m, n, cam, at.add(0, 0.04, 0), nx.subtract(at).scale(6), (float) (0.09 * (1 - drain)));
        }
    }

    // ================================================================== 13 spatter: the flash of a wound opening
    private static void spatter(Matrix4f m, Vec3 cam, L l, double age) {
        FxPacket p = l.p;
        double k = age / Math.max(1, p.life);
        float a = (float) (1 - k);
        if (a <= 0.01F) return;
        float s = p.a0 > 0 ? p.a0 : 1F;
        Vec3 d = p.d.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : p.d.normalize();
        Vec3[] f = frame(d);
        for (int i = 0; i < 7; i++) {   // streaks thrown out of the wound, mostly the way the blow went
            double an = i * TAU / 7 + p.id;
            Vec3 dir = d.scale(1.2).add(f[0].scale(Math.cos(an))).add(f[1].scale(Math.sin(an))).normalize();
            double r0 = 0.15 + k * 0.5, r1 = r0 + (0.5 + 0.4 * FxBase.h2(p.id, i, 1)) * s * (0.5 + k);
            glowAlong(m, cam, new Vec3[]{p.c.add(dir.scale(r0)), p.c.add(dir.scale(r1))}, 0.09 * s, a * 0.9F);
        }
        glowAlong(m, cam, new Vec3[]{p.c.subtract(d.scale(0.25)), p.c.add(d.scale(0.25))}, 0.6 * s * (1 - k), a * 0.6F);
    }

    // ================================================================== 14 sigil: a circle drawn on the ground in blood
    private static void sigil(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Minecraft mc, L l, double age, double now, boolean tick) {
        FxPacket p = l.p;
        Vec3 c = p.c.add(0, 0.04, 0);
        lightAt(mc, c.add(0, 0.5, 0));
        double draw = FxBase.smooth(age / Math.max(1, p.speed));
        double drain = Mth.clamp((age - (p.life - 10)) / 10.0, 0, 1);
        float hb = beat(now);
        double R = p.r;
        for (int ring = 0; ring < 2; ring++) {   // two rings of blood running round, drawing themselves
            double rr = R * (ring == 0 ? 1 : 0.8), dirn = ring == 0 ? 1 : -1;
            int seg = 48, k = Math.max(2, (int) (seg * draw));
            Vec3[] pts = new Vec3[k + 1];
            double[] rad = new double[k + 1];
            for (int i = 0; i <= k; i++) {
                double an = dirn * i * TAU / seg + p.id;
                double wob = 1 + 0.02 * Math.sin(an * 7 + now * 0.2);
                pts[i] = c.add(Math.cos(an) * rr * wob, 0, Math.sin(an) * rr * wob);
                rad[i] = (ring == 0 ? 0.08 : 0.055) * (1 - drain * 0.85) * (i == k && draw < 1 ? 0.6 : 1);
            }
            tube(vc, m, n, cam, pts, rad, 5, 0.97F, 1F);
            glowAlong(m, cam, pts, ring == 0 ? 0.5 : 0.35, (float) ((0.18 + 0.32 * hb) * (1 - drain)));
        }
        int spokes = 8;   // channels running in to the middle, the blood flowing along them toward the centre
        for (int i = 0; i < spokes; i++) {
            double an = i * TAU / spokes + p.id + 0.2;
            double g = Mth.clamp((draw - 0.3) / 0.7, 0, 1) * (1 - drain * 0.7);
            if (g <= 0.02) continue;
            Vec3 o = new Vec3(Math.cos(an), 0, Math.sin(an)), sd = new Vec3(-o.z, 0, o.x);
            Vec3[] pts = new Vec3[6];
            double[] rad = new double[6];
            for (int j = 0; j < 6; j++) {
                double t = j / 5.0;
                double rr = R * 0.8 * (1 - t * g * 0.85);
                pts[j] = c.add(o.scale(rr)).add(sd.scale(Math.sin(t * 5 + i) * 0.12 * R / 4));
                rad[j] = 0.04 * (1 - t * 0.5);
            }
            tube(vc, m, n, cam, pts, rad, 4, 0.96F, 0.9F);
            double t = (now * 0.08 + i * 0.37) % 1.0;
            drip(vc, m, n, cam, c.add(o.scale(R * 0.8 * (1 - t * g * 0.85))).add(0, 0.04, 0), o.scale(-0.2), 0.07F * (float) (1 - drain));
        }
        if (draw > 0.5) {   // the centre fills into a pool, beating
            double pr = R * 0.18 * (0.9 + 0.1 * hb) * Mth.clamp((draw - 0.5) * 2, 0, 1) * (1 + drain);
            Pool fake = new Pool(p.c, (float) pr, -1000, 1e9, p.id * 31);
            drawPool(vc, m, n, cam, fake, 1000);
            glowAlong(m, cam, new Vec3[]{c.add(-pr, 0.02, 0), c.add(pr, 0.02, 0)}, pr * 1.6, (0.15F + 0.35F * hb) * (float) (1 - drain));
        }
    }

    // ================================================================== the white of the Scarlet Moon (additive light)
    private static void gstrip(Matrix4f m, Vec3 cam, Vec3[] pts, double[] w, float[] al, float[] col) {
        for (int i = 0; i < pts.length - 1; i++) {
            Vec3 a = pts[i], b = pts[i + 1], ax = b.subtract(a);
            if (ax.lengthSqr() < 1e-10) continue;
            Vec3 s = ax.cross(a.subtract(cam));
            if (s.lengthSqr() < 1e-10) continue;
            s = s.normalize();
            FxBase.gquad(m, cam, a.add(s.scale(w[i])), b.add(s.scale(w[i + 1])), b.subtract(s.scale(w[i + 1])), a.subtract(s.scale(w[i])), col, al[i], al[i + 1], al[i + 1], al[i]);
        }
    }

    private static void gseg(Matrix4f m, Vec3 cam, Vec3 a, Vec3 b, double w, float aa, float ab, float[] col) {
        gstrip(m, cam, new Vec3[]{a, b}, new double[]{w, w}, new float[]{aa, ab}, col);
    }

    /** A soft flat disc of light on the ground. */
    private static void gdisc(Matrix4f m, Vec3 cam, Vec3 c, double r, float a, float[] col) {
        int seg = 24;
        for (int i = 0; i < seg; i++) {
            double a0 = i * TAU / seg, a1 = (i + 1) * TAU / seg;
            Vec3 p0 = c.add(Math.cos(a0) * r, 0, Math.sin(a0) * r), p1 = c.add(Math.cos(a1) * r, 0, Math.sin(a1) * r);
            FxBase.gquad(m, cam, c, c, p1, p0, col, a, a, 0, 0);
        }
    }

    /** A soft flat band (ring) on the ground: bright in its middle, fading both ways. */
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

    /** A soft camera-facing glow (bloom) at a point. */
    private static void gglow(Matrix4f m, Vec3 cam, Vec3 c, double r, float a, float[] col) {
        Vec3 view = c.subtract(cam);
        if (view.lengthSqr() < 1e-6) return;
        Vec3[] f = frame(view.normalize());
        int seg = 16;
        for (int i = 0; i < seg; i++) {
            double a0 = i * TAU / seg, a1 = (i + 1) * TAU / seg;
            Vec3 p0 = c.add(f[0].scale(Math.cos(a0) * r)).add(f[1].scale(Math.sin(a0) * r)), p1 = c.add(f[0].scale(Math.cos(a1) * r)).add(f[1].scale(Math.sin(a1) * r));
            FxBase.gquad(m, cam, c, c, p1, p0, col, a, a, 0, 0);
        }
    }

    private static float heartbeat(double now) {
        double t = now % 26;
        return (float) (Math.exp(-Math.pow(t / 1.6, 2)) + 0.65 * Math.exp(-Math.pow((t - 5) / 1.6, 2)));
    }

    /** Hash in [0, 1). */
    private static double hs(int a, int b) { return FxBase.h2(a, b, 911); }

    private static void whiteAura(Matrix4f m, Vec3 cam, Minecraft mc, L l, double age, double now, float pt, float out) {
        FxPacket p = l.p;
        Entity e = mc.level.getEntity((int) p.v.x);
        if (e == null) return;
        Vec3 f = follow(mc, p.v.x, p.c, pt, 0);
        double H = e.getBbHeight();
        float k = env(age, p.life, 8, 20) * out;
        if (k <= 0.01F) return;
        float hb = heartbeat(now + p.id);
        boolean self = e == mc.player && mc.options.getCameraType().isFirstPerson();
        Vec3 g = new Vec3(f.x, ground(mc, f.add(0, 0.5, 0)) + 0.03, f.z);
        if (g.y > f.y + 0.6 || f.y - g.y > 2.5) g = f.add(0, 0.03, 0);

        // the ground beneath you lit white, beating like a heart, and rings sent out on every beat
        gdisc(m, cam, g, 1.7 + 0.35 * hb, (0.28F + 0.2F * hb) * k, W_SOFT);
        double t = (now + p.id) % 26;
        for (int b = 0; b < 2; b++) {
            double ph = t - b * 5;
            if (ph < 0 || ph > 14) continue;
            double rr = 0.6 + ph * 0.32;
            gring(m, cam, g.add(0, 0.01, 0), rr, 0.18 + ph * 0.02, (float) (1 - ph / 14) * 0.55F * k * (b == 0 ? 1F : 0.7F), W_CORE);
        }
        // rising sparks
        for (int i = 0; i < 26; i++) {
            double ph = ((now / (22 + 10 * hs(i, 1)) + hs(i, 2)) % 1.0);
            double an = hs(i, 3) * TAU + ph * 1.6, rad = 0.35 + 0.85 * hs(i, 4) * (0.6 + ph * 0.6);
            Vec3 s0 = f.add(Math.cos(an) * rad, ph * H * 1.9, Math.sin(an) * rad);
            float sa = (float) Math.sin(ph * Math.PI) * k;
            gseg(m, cam, s0, s0.add(0, 0.16 + 0.12 * hs(i, 5), 0), 0.025, sa, 0, W_CORE);
        }
        if (self) return;   // in first person you see the ground light and the sparks, never a wall of white

        // a soft bloom round the whole body
        gglow(m, cam, f.add(0, H * 0.55, 0), H * 0.85, (0.06F + 0.06F * hb) * k, W_SOFT);
        // white flame licking up the body: tongues born at the feet that rise, stretch and let go
        for (int i = 0; i < 22; i++) {
            double ph = ((now / 15.0 + hs(i, 7)) % 1.0);
            double an = hs(i, 8) * TAU + now * 0.02;
            double rad = 0.36 + 0.12 * ph;
            double y0 = ph * H * 0.85, len = H * (0.25 + 0.2 * hs(i, 9)) * Math.sin(ph * Math.PI) * (1 + 0.3 * hb);
            if (len < 0.03) continue;
            Vec3 b0 = f.add(Math.cos(an) * rad, y0, Math.sin(an) * rad);
            Vec3 outv = new Vec3(Math.cos(an), 0, Math.sin(an));
            Vec3[] pts = new Vec3[5];
            double[] w = new double[5];
            float[] al = new float[5], core = new float[5];
            float fa = (float) Math.sin(ph * Math.PI) * k;
            for (int j = 0; j < 5; j++) {
                double u = j / 4.0;
                double sway = Math.sin(now * 0.6 + i * 1.9 + u * 3) * 0.08 * u;
                pts[j] = b0.add(0, len * u, 0).add(outv.scale(0.1 * u * u + sway)).add(-outv.z * sway, 0, outv.x * sway);
                w[j] = 0.13 * (1 - u) + 0.01;
                al[j] = fa * 0.22F * (float) (1 - u);
                core[j] = fa * 0.6F * (float) (1 - u);
            }
            gstrip(m, cam, pts, w, al, W_SOFT);
            double[] wc = new double[5];
            for (int j = 0; j < 5; j++) wc[j] = w[j] * 0.35;
            gstrip(m, cam, pts, wc, core, W_CORE);
        }
        // three spiralling streaks of light winding up round you
        for (int s = 0; s < 3; s++) {
            double head = ((now / 34.0 + s / 3.0) % 1.0);
            int tr = 14;
            Vec3[] pts = new Vec3[tr];
            double[] w = new double[tr];
            float[] al = new float[tr];
            float fade = (float) Math.sin(Math.min(1, head * 1.15) * Math.PI);
            for (int j = 0; j < tr; j++) {
                double u = Math.max(0, head - j * 0.018);
                double an = u * TAU * 2.2 + s * TAU / 3;
                double rad = 0.85 - 0.35 * u;
                pts[j] = f.add(Math.cos(an) * rad, u * H * 1.35, Math.sin(an) * rad);
                w[j] = 0.06 * (1 - j / (double) tr);
                al[j] = 0.9F * fade * k * (1 - j / (float) tr);
            }
            gstrip(m, cam, pts, w, al, W_CORE);
        }
        // crackling arcs of white power jumping over the body
        long slot = (long) (now / 3);
        for (int i = 0; i < 2; i++) {
            if (hs((int) slot, i + 20) < 0.45 - 0.3 * hb) continue;
            double a0 = hs((int) slot, i + 30) * TAU, a1 = a0 + 0.8 + hs((int) slot, i + 40) * 1.4;
            double y0 = H * (0.2 + 0.6 * hs((int) slot, i + 50)), y1 = y0 + (hs((int) slot, i + 60) - 0.5) * H * 0.6;
            Vec3 pa = f.add(Math.cos(a0) * 0.45, y0, Math.sin(a0) * 0.45), pb = f.add(Math.cos(a1) * 0.45, y1, Math.sin(a1) * 0.45);
            Vec3 prev = pa;
            for (int j = 1; j <= 5; j++) {
                double u = j / 5.0;
                Vec3 q = pa.add(pb.subtract(pa).scale(u));
                if (j < 5) q = q.add((hs((int) slot, i * 10 + j) - 0.5) * 0.3, (hs((int) slot, i * 10 + j + 5) - 0.5) * 0.3, (hs((int) slot, i * 10 + j + 7) - 0.5) * 0.3);
                gseg(m, cam, prev, q, 0.02, 0.95F * k, 0.95F * k, W_CORE);
                gseg(m, cam, prev, q, 0.07, 0.25F * k, 0.25F * k, W_SOFT);
                prev = q;
            }
        }
        // a crown of light over the head
        Vec3 head = f.add(0, H + 0.05, 0);
        gseg(m, cam, head, head.add(0, 0.7 + 0.4 * hb, 0), 0.12, (0.35F + 0.3F * hb) * k, 0, W_SOFT);
        gseg(m, cam, head, head.add(0, 0.5 + 0.3 * hb, 0), 0.035, 0.8F * k, 0, W_CORE);
    }

    private static void awakening(Matrix4f m, Vec3 cam, L l, double age, double now) {
        FxPacket p = l.p;
        Vec3 c = p.c;
        double gather = Math.max(1, p.sweep), R = p.r;
        if (age < gather) {   // white light drawn in from all around, spiralling into you
            double k = age / gather;
            for (int i = 0; i < 36; i++) {
                double st = hs(i, 70) * 0.5, kk = Mth.clamp((k - st) / (1 - st), 0, 1);
                if (kk <= 0) continue;
                double an0 = hs(i, 71) * TAU;
                Vec3 prev = null;
                for (int j = 0; j < 5; j++) {
                    double u = Math.max(0, kk - j * 0.04), e = u * u;
                    double rad = R * (1 - e) + 0.3 * e, an = an0 + e * 2.4;
                    Vec3 q = c.add(Math.cos(an) * rad, 0.15 + e * 1.1 + hs(i, 72) * (1 - e) * 1.5, Math.sin(an) * rad);
                    if (prev != null) gseg(m, cam, prev, q, 0.045, (float) (0.9 * Math.sin(kk * Math.PI)) * (1 - j / 5F), (float) (0.9 * Math.sin(kk * Math.PI)) * (1 - (j - 1) / 5F), W_CORE);
                    prev = q;
                }
            }
            gring(m, cam, c.add(0, 0.03, 0), R * (1 - k * 0.9), 0.15, (float) (0.5 * Math.sin(k * Math.PI)), W_CORE);
            gglow(m, cam, c.add(0, 1.1, 0), 0.6 + k * 1.4, (float) (0.15 + 0.35 * k), W_SOFT);
            return;
        }
        double t = age - gather;
        float a = (float) Mth.clamp(1 - t / 16.0, 0, 1);
        if (a <= 0.01F) return;
        // the pillar of white light, narrowing as it fades
        double w = 1.8 * (1 - t / 18.0) + 0.1;
        Vec3 top = c.add(0, 34, 0);
        gseg(m, cam, c, top, w, 0.4F * a, 0.0F, W_SOFT);
        gseg(m, cam, c, top, w * 0.35, 0.95F * a, 0.1F * a, W_CORE);
        // the flash
        gglow(m, cam, c.add(0, 1.1, 0), 3.2 * (1 - Math.min(1, t / 8.0)) + 0.4, 0.6F * (float) Math.max(0, 1 - t / 8.0), W_CORE);
        // the shockwave rolling out along the ground
        double rr = 1 + t * 0.95;
        gring(m, cam, c.add(0, 0.04, 0), rr, 0.45 + t * 0.04, 0.85F * a, W_CORE);
        gring(m, cam, c.add(0, 0.05, 0), rr * 0.7, 0.25, 0.4F * a, W_SOFT);
        // light thrown upward all round
        for (int i = 0; i < 16; i++) {
            double an = i * TAU / 16 + hs(i, 80);
            double rad = 0.8 + t * 0.35;
            double y = t * (0.5 + hs(i, 81) * 0.6);
            Vec3 b = c.add(Math.cos(an) * rad, y, Math.sin(an) * rad);
            gseg(m, cam, b, b.add(0, 0.8 + hs(i, 82) * 1.2, 0), 0.04, 0.9F * a, 0, W_CORE);
        }
    }
}
