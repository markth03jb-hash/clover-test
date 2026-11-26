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
 * The Foreseen Grimoire, drawn: what is about to happen, shown a moment early - pale ghosts of the future, mirror
 * glass, clock faces and an all-seeing eye, in silver and a deep electric blue. Its own look:
 * <ul>
 *   <li>0 a held blow: the striker's ghost already swinging at you, its arc drawn in silver, and a clock under you
 *       closing as the blow comes;</li>
 *   <li>1 a dodge: a pane of mirror glass shattering, its shards spinning away;</li>
 *   <li>2 Step Through: a run of your own ghosts from where you were to where you are;</li>
 *   <li>3 an eye over a marked foe: almond lids, a turning iris, the pupil a slit;</li>
 *   <li>4 Punish: a clean silver crescent cut;</li>
 *   <li>5 Snap Back / a rewind: a clock face where you land, its hands running backwards, ghosts streaming back;</li>
 *   <li>6 a foretold strike, waiting: a clock on the ground, its hand sweeping down to the moment; 7 the moment:
 *       a blade of light dropping, glass flying;</li>
 *   <li>8 Seen It Before: panes of mirror turning round you;</li>
 *   <li>9 Rewrite: a great dial under you turning back, and lines of silver to those who'll pay;</li>
 *   <li>10 a fate thread between two foes, glints running along it; 11 a foreseen path: a dashed line running ahead
 *       of an arrow; 12 Glimpse: a foe's ghost lunging out ahead of it.</li>
 * </ul>
 * Fed by FxPacket shape 103 (server/ForeseenFx): {@code h} = kind, {@code v.x} = the entity it rides (or -1),
 * {@code v.y} = a second entity (the thread).
 */
public final class ForeseenFxClient {
    private ForeseenFxClient() {}

    private static final double TAU = Math.PI * 2;
    private static final Vec3 UP = new Vec3(0, 1, 0);
    static final float[] SILVER = {0.86F, 0.92F, 1F}, BLUE = {0.2F, 0.45F, 1F}, DEEP = {0.08F, 0.16F, 0.6F}, WHITE = {1F, 1F, 1F},
            GLASS = {0.7F, 0.85F, 1F}, GHOST = {0.45F, 0.65F, 1F};

    private static final class L {
        FxPacket p;
        double born;
        L(FxPacket p, double born) { this.p = p; this.born = born; }
    }

    private static final List<L> LIVE = new ArrayList<>();

    private static boolean keyed(int k) { return k == 3 || k == 8 || k == 10; }

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
                case 0 -> held(m, cam, mc, p, age, now, pt);
                case 1 -> shatter(vc, m, n, cam, p, age);
                case 2 -> step(m, cam, p, age);
                case 3 -> eye(m, cam, mc, p, age, now, pt);
                case 4 -> cut(vc, m, n, cam, p, age);
                case 5 -> rewind(m, cam, p, age, now);
                case 6 -> foretold(m, cam, p, age);
                case 7 -> moment(vc, m, n, cam, p, age);
                case 8 -> panes(vc, m, n, cam, mc, p, age, now, pt);
                case 9 -> rewrite(m, cam, p, age, now);
                case 10 -> thread(m, cam, mc, p, age, now, pt);
                case 11 -> path(m, cam, p, age, now);
                case 12 -> lunge(m, cam, p, age);
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

    private static boolean firstPersonSelf(Minecraft mc, int id) {
        Entity e = mc.level.getEntity(id);
        return e == mc.player && mc.options.getCameraType().isFirstPerson();
    }

    /** A clock face lying in the plane of 'normal': rim, twelve marks, and one hand at angle 'hand' (0 = twelve). */
    static void clock(Matrix4f m, Vec3 cam, Vec3 c, Vec3 normal, double r, double hand, double hand2, float a) {
        Vec3 ax = Math.abs(normal.y) > 0.9 ? new Vec3(0, 0, 1) : UP;
        Vec3 x = normal.cross(ax).normalize(), y = x.cross(normal).normalize();
        GravityFxClient.ring(m, cam, c, normal, r, r * 0.05, SILVER, a);
        GravityFxClient.ring(m, cam, c, normal, r * 0.9, r * 0.02, BLUE, a * 0.7F);
        for (int i = 0; i < 12; i++) {
            double an = i * TAU / 12;
            Vec3 o = x.scale(Math.sin(an)).add(y.scale(Math.cos(an)));
            GravityFxClient.gseg(m, cam, c.add(o.scale(r * (i % 3 == 0 ? 0.72 : 0.8))), c.add(o.scale(r * 0.88)), r * (i % 3 == 0 ? 0.035 : 0.02), a, a, SILVER);
        }
        Vec3 h = x.scale(Math.sin(hand)).add(y.scale(Math.cos(hand)));
        GravityFxClient.gseg(m, cam, c, c.add(h.scale(r * 0.75)), r * 0.04, a, a, WHITE);
        if (!Double.isNaN(hand2)) {
            Vec3 h2 = x.scale(Math.sin(hand2)).add(y.scale(Math.cos(hand2)));
            GravityFxClient.gseg(m, cam, c, c.add(h2.scale(r * 0.5)), r * 0.05, a, a, BLUE);
        }
        GravityFxClient.gglow(m, cam, c, r * 0.12, a, WHITE);
    }

    /** A ghost of a person: head, body, arms, legs in pale light, standing at 'feet', facing 'fwd', 'lean' forward (0..1),
     *  arms 'reach' forward (0..1). */
    static void ghost(Matrix4f m, Vec3 cam, Vec3 feet, Vec3 fwd, double lean, double reach, float a) {
        Vec3 f = new Vec3(fwd.x, 0, fwd.z).normalize();
        Vec3 side = new Vec3(-f.z, 0, f.x);
        Vec3 up = UP.add(f.scale(lean * 0.6)).normalize();
        Vec3 hip = feet.add(0, 0.8, 0), neck = hip.add(up.scale(0.65)), head = neck.add(up.scale(0.28));
        float w = a * 0.85F;
        GravityFxClient.gseg(m, cam, hip, neck, 0.11, w, w, GHOST);
        GravityFxClient.ring(m, cam, head, head.subtract(cam).normalize(), 0.16, 0.04, SILVER, w);
        GravityFxClient.gglow(m, cam, head, 0.2, w * 0.5F, GHOST);
        for (int s = -1; s <= 1; s += 2) {
            Vec3 sh = neck.add(side.scale(0.2 * s)).add(up.scale(-0.05));
            Vec3 hand = sh.add(up.scale(-0.55 * (1 - reach))).add(f.scale(0.6 * reach)).add(side.scale(0.05 * s));
            GravityFxClient.gseg(m, cam, sh, hand, 0.05, w, w, GHOST);
            Vec3 knee = hip.add(side.scale(0.1 * s)).add(0, -0.4, 0).add(f.scale(0.1 * s * lean));
            GravityFxClient.gseg(m, cam, hip.add(side.scale(0.1 * s)), knee, 0.06, w, w, GHOST);
            GravityFxClient.gseg(m, cam, knee, feet.add(side.scale(0.11 * s)).add(f.scale(-0.15 * s * lean)), 0.05, w, w, GHOST);
        }
        GravityFxClient.gglow(m, cam, hip.add(up.scale(0.3)), 0.6, w * 0.15F, BLUE);
    }

    /** A shard of mirror glass: a thin solid triangle (both faces) with a bright edge. */
    static void shard(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 c, double size, double spin, double seed, float a) {
        Vec3 ax = new Vec3(Math.cos(seed * 3), Math.sin(seed * 5), Math.sin(seed * 7)).normalize();
        Vec3 u = ax.cross(UP).lengthSqr() < 1e-3 ? new Vec3(1, 0, 0) : ax.cross(UP).normalize();
        Vec3 v = ax.cross(u).normalize();
        double cs = Math.cos(spin), sn = Math.sin(spin);
        Vec3 uu = u.scale(cs).add(v.scale(sn)), vv = v.scale(cs).subtract(u.scale(sn));
        Vec3 p0 = c.add(uu.scale(size)), p1 = c.add(uu.scale(-size * 0.5)).add(vv.scale(size * 0.7)), p2 = c.add(uu.scale(-size * 0.4)).add(vv.scale(-size * 0.6));
        FxBase.quad(vc, m, n, cam, p0, p1, p2, p2, GLASS, a * 0.75F, a * 0.75F, a * 0.75F, a * 0.75F);
        FxBase.quad(vc, m, n, cam, p2, p2, p1, p0, GLASS, a * 0.75F, a * 0.75F, a * 0.75F, a * 0.75F);
        GravityFxClient.gseg(m, cam, p0, p1, size * 0.08, a, a, WHITE);
    }

    // ------------------------------------------------------------------ 0 a held blow
    private static void held(Matrix4f m, Vec3 cam, Minecraft mc, FxPacket p, double age, double t, float pt) {
        Vec3 me = at(mc, (int) p.v.x, pt);
        Vec3 by = at(mc, (int) p.v.y, pt);
        if (me == null) return;
        double q = Mth.clamp(age / Math.max(1, p.life), 0, 1);
        float a = (float) Mth.clamp(age / 2, 0, 1);
        boolean fp = firstPersonSelf(mc, (int) p.v.x);
        // the clock under you closing: the hand sweeps round to twelve as the blow comes
        clock(m, cam, me.add(0, 0.06, 0), UP, fp ? 1.1 : 0.9, -TAU * (1 - q), Double.NaN, a * 0.9F);
        if (by == null) return;
        // the striker's ghost, already lunging, and the arc of its swing
        Vec3 to = me.subtract(by);
        Vec3 f = new Vec3(to.x, 0, to.z).normalize();
        double lunge = Math.min(1, q * 1.4);
        ghost(m, cam, by.add(f.scale(0.4 + 0.6 * lunge)), f, 0.3 + 0.4 * lunge, 0.3 + 0.7 * lunge, a * 0.8F);
        Vec3 side = new Vec3(-f.z, 0, f.x);
        Vec3 c = by.add(f.scale(0.9)).add(0, 1.2, 0);
        Vec3 prev = null;
        for (int i = 0; i <= 10; i++) {
            double u = i / 10.0, an = -1.1 + 2.2 * u;
            if (u > lunge + 0.15) break;
            Vec3 pnt = c.add(f.scale(Math.cos(an) * 0.9)).add(side.scale(Math.sin(an) * 0.9)).add(0, (0.5 - u) * 0.6, 0);
            if (prev != null) { GravityFxClient.gseg(m, cam, prev, pnt, 0.05, a, a, SILVER); GravityFxClient.gseg(m, cam, prev, pnt, 0.14, a * 0.4F, a * 0.4F, BLUE); }
            prev = pnt;
        }
    }

    // ------------------------------------------------------------------ 1 a dodge: glass breaking
    private static void shatter(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age) {
        double q = age / p.life, sc = p.r > 0 ? p.r : 1;
        float a = (float) (1 - q);
        if (age < 3) GravityFxClient.gglow(m, cam, p.c, 0.8 * sc, (float) (1 - age / 3), WHITE);
        GravityFxClient.ring(m, cam, p.c, p.c.subtract(cam).normalize(), (0.3 + q * 1.2) * sc, 0.04 * sc, SILVER, a);
        for (int i = 0; i < 12; i++) {
            double s = p.a1 + i;
            Vec3 d = new Vec3(hash(s) - 0.5, hash(s * 2) - 0.3, hash(s * 3) - 0.5).normalize();
            Vec3 c = p.c.add(d.scale((0.2 + q * 1.4) * sc * (0.7 + 0.5 * hash(s * 5)))).add(0, -q * q * 0.6, 0);
            shard(vc, m, n, cam, c, 0.13 * sc * (0.6 + 0.6 * hash(s * 7)), age * 0.6 + s, s, a);
        }
    }

    // ------------------------------------------------------------------ 2 Step Through
    private static void step(Matrix4f m, Vec3 cam, FxPacket p, double age) {
        Vec3 ab = p.u.subtract(p.c);
        Vec3 f = new Vec3(ab.x, 0, ab.z);
        if (f.lengthSqr() < 1e-4) f = new Vec3(0, 0, 1);
        int N = 5;
        for (int i = 0; i < N; i++) {   // ghosts from where you were, each a little later and fainter
            double u = i / (double) (N - 1);
            float a = (float) Mth.clamp(1 - (age - u * 3) / (p.life - 3), 0, 1) * (0.35F + 0.65F * (float) u);
            if (age < u * 3) continue;
            ghost(m, cam, p.c.add(ab.scale(u)), f, 0.5, 0.2, a);
        }
        float a = (float) (1 - age / p.life);
        GravityFxClient.gseg(m, cam, p.c.add(0, 1, 0), p.u.add(0, 1, 0), 0.03, a * 0.3F, a, SILVER);
        GravityFxClient.ring(m, cam, p.u.add(0, 0.06, 0), UP, 0.4 + age * 0.08, 0.04, BLUE, a);
    }

    // ------------------------------------------------------------------ 3 the eye
    static void eyeAt(Matrix4f m, Vec3 cam, Vec3 c, double s, double t, float a, double open) {
        Vec3 d = c.subtract(cam).normalize();
        Vec3 x = d.cross(UP).normalize(), y = x.cross(d).normalize().scale(-1);
        Vec3 prevT = null, prevB = null;
        for (int i = 0; i <= 12; i++) {   // the lids
            double u = -1 + 2 * i / 12.0, h = (1 - u * u) * 0.45 * open;
            Vec3 tp = c.add(x.scale(u * s)).add(y.scale(h * s)), bt = c.add(x.scale(u * s)).add(y.scale(-h * s));
            if (prevT != null) { GravityFxClient.gseg(m, cam, prevT, tp, s * 0.06, a, a, SILVER); GravityFxClient.gseg(m, cam, prevB, bt, s * 0.06, a, a, SILVER); }
            prevT = tp; prevB = bt;
        }
        if (open > 0.2) {
            double ir = s * 0.32 * open;
            GravityFxClient.ring(m, cam, c, d, ir, s * 0.05, BLUE, a);
            for (int i = 0; i < 8; i++) {   // the iris turning
                double an = t * 0.05 + i * TAU / 8;
                Vec3 o = x.scale(Math.cos(an)).add(y.scale(Math.sin(an)));
                GravityFxClient.gseg(m, cam, c.add(o.scale(ir * 0.45)), c.add(o.scale(ir * 0.9)), s * 0.025, a * 0.8F, a * 0.8F, BLUE);
            }
            GravityFxClient.gseg(m, cam, c.add(y.scale(ir * 0.7)), c.add(y.scale(-ir * 0.7)), s * 0.06, a, a, WHITE);   // the pupil, a slit
        }
        GravityFxClient.gglow(m, cam, c, s * 0.7, a * 0.25F, BLUE);
    }

    private static void eye(Matrix4f m, Vec3 cam, Minecraft mc, FxPacket p, double age, double t, float pt) {
        Vec3 f = at(mc, (int) p.v.x, pt);
        if (f == null) return;
        Entity e = mc.level.getEntity((int) p.v.x);
        double open = Mth.clamp(age / 6, 0, 1) * Mth.clamp((p.life - age) / 4, 0, 1);
        float a = (float) Mth.clamp(age / 3, 0, 1);
        eyeAt(m, cam, f.add(0, (e == null ? 2 : e.getBbHeight()) + 0.55, 0), 0.38, t, a, FxBase.smooth(open));
    }

    // ------------------------------------------------------------------ 4 Punish
    private static void cut(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age) {
        double q = Math.min(1, age / 3);
        float a = (float) Mth.clamp((p.life - age) / 4, 0, 1);
        Vec3 f = p.d.normalize();
        Vec3 side = f.cross(UP).lengthSqr() < 1e-3 ? new Vec3(1, 0, 0) : f.cross(UP).normalize();
        Vec3 up = side.cross(f).normalize();
        double tilt = p.a0, R = p.r > 0 ? p.r : 1.8;
        Vec3 sx = side.scale(Math.cos(tilt)).add(up.scale(Math.sin(tilt)));
        int N = 16;
        Vec3[] pts = new Vec3[N + 1];
        double[] w = new double[N + 1];
        float[] al = new float[N + 1];
        for (int i = 0; i <= N; i++) {
            double u = i / (double) N, an = (-1.2 + 2.4 * u * q);
            pts[i] = p.c.add(f.scale(Math.cos(an) * R * 0.5)).add(sx.scale(Math.sin(an) * R));
            w[i] = 0.18 * Math.sin(Math.PI * u) + 0.01;
            al[i] = a * (float) (0.3 + 0.7 * u);
        }
        GravityFxClient.gstrip(m, cam, pts, w, al, BLUE);
        for (int i = 0; i <= N; i++) w[i] *= 0.35;
        GravityFxClient.gstrip(m, cam, pts, w, al, WHITE);
        VectorFxClient.sstrip(vc, m, n, cam, pts, w, al, SILVER);
    }

    // ------------------------------------------------------------------ 5 a rewind
    private static void rewind(Matrix4f m, Vec3 cam, FxPacket p, double age, double t) {
        float a = (float) Mth.clamp((p.life - age) / 6, 0, 1);
        Vec3 c = p.u.add(0, 1.1, 0);
        Vec3 face = cam.subtract(c);
        face = new Vec3(face.x, 0, face.z).normalize();
        clock(m, cam, c, face, 1.1, -t * 0.6, -t * 0.08, a);   // the hands running backwards
        double q = Math.min(1, age / 8);
        Vec3 ab = p.u.subtract(p.c);
        for (int i = 0; i < 4; i++) {   // ghosts streaming back the way you came
            double u = Mth.clamp(q * 1.3 - i * 0.12, 0, 1);
            ghost(m, cam, p.c.add(ab.scale(u)), ab.scale(-1), 0.2, 0, a * (0.6F - i * 0.12F) * (float) (1 - u * 0.5));
        }
        GravityFxClient.ring(m, cam, p.u.add(0, 0.06, 0), UP, 0.3 + q * 0.9, 0.05, SILVER, a);
    }

    // ------------------------------------------------------------------ 6 a foretold strike, 7 the moment
    private static void foretold(Matrix4f m, Vec3 cam, FxPacket p, double age) {
        double q = Mth.clamp(age / Math.max(1, p.life), 0, 1);
        float a = (float) Mth.clamp(age / 3, 0, 1);
        double R = p.r > 0 ? p.r : 1.6;
        clock(m, cam, p.c.add(0, 0.06, 0), UP, R, -TAU * (1 - q), Double.NaN, a);
        GravityFxClient.gseg(m, cam, p.c.add(0, 6, 0), p.c.add(0, 0.1, 0), 0.02 + 0.05 * q, a * 0.2F, a * (float) q, SILVER);   // the line the blade will fall down
    }

    private static void moment(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age) {
        double q = age / p.life, R = p.r > 0 ? p.r : 1.6;
        float a = (float) (1 - q);
        double drop = Math.min(1, age / 2);
        Vec3 top = p.c.add(0, 7, 0), bot = p.c.add(0, 0.05, 0);
        Vec3 end = top.add(bot.subtract(top).scale(drop));
        GravityFxClient.gseg(m, cam, top, end, 0.5 * R / 1.6, a * 0.5F, a, BLUE);
        GravityFxClient.gseg(m, cam, top, end, 0.15 * R / 1.6, a, a, WHITE);
        if (drop >= 1) {
            GravityFxClient.ring(m, cam, bot, UP, R * (0.3 + q * 1.1), 0.12, SILVER, a);
            GravityFxClient.gglow(m, cam, bot.add(0, 0.4, 0), R * (1 - q), a, WHITE);
            for (int i = 0; i < 8; i++) {
                double s = p.a1 + i, an = s * 2.4;
                Vec3 c = bot.add(Math.cos(an) * R * q * 1.2, 0.2 + 1.4 * q - 1.6 * q * q, Math.sin(an) * R * q * 1.2);
                shard(vc, m, n, cam, c, 0.15, age * 0.5 + s, s, a);
            }
        }
    }

    // ------------------------------------------------------------------ 8 Seen It Before
    private static void panes(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Minecraft mc, FxPacket p, double age, double t, float pt) {
        Vec3 f = at(mc, (int) p.v.x, pt);
        if (f == null) return;
        boolean fp = firstPersonSelf(mc, (int) p.v.x);
        float a = (float) (Mth.clamp(age / 6, 0, 1) * Mth.clamp((p.life - age) / 8, 0, 1)) * (fp ? 0.4F : 0.8F);
        Vec3 c = f.add(0, 1.0, 0);
        double R = fp ? 1.6 : 1.2;
        for (int i = 0; i < 6; i++) {   // panes of mirror turning round you, each catching the light in turn
            double an = t * 0.03 + i * TAU / 6;
            Vec3 o = new Vec3(Math.cos(an), 0, Math.sin(an)), tg = new Vec3(-o.z, 0, o.x);
            Vec3 b = c.add(o.scale(R)).add(0, Math.sin(t * 0.05 + i) * 0.15, 0);
            Vec3 p0 = b.add(tg.scale(0.28)).add(0, 0.42, 0), p1 = b.subtract(tg.scale(0.28)).add(0, 0.42, 0), p2 = b.subtract(tg.scale(0.28)).add(0, -0.42, 0), p3 = b.add(tg.scale(0.28)).add(0, -0.42, 0);
            FxBase.quad(vc, m, n, cam, p0, p1, p2, p3, GLASS, a * 0.25F, a * 0.25F, a * 0.35F, a * 0.35F);
            FxBase.quad(vc, m, n, cam, p3, p2, p1, p0, GLASS, a * 0.35F, a * 0.35F, a * 0.25F, a * 0.25F);
            GravityFxClient.gseg(m, cam, p0, p1, 0.02, a, a, SILVER); GravityFxClient.gseg(m, cam, p1, p2, 0.02, a, a, SILVER);
            GravityFxClient.gseg(m, cam, p2, p3, 0.02, a, a, SILVER); GravityFxClient.gseg(m, cam, p3, p0, 0.02, a, a, SILVER);
            double gl = (t * 0.04 + i / 6.0) % 1.0;
            if (gl < 0.25) GravityFxClient.gseg(m, cam, p0.add(p1.subtract(p0).scale(gl * 4)), p3.add(p2.subtract(p3).scale(Math.min(1, gl * 4 + 0.2))), 0.04, a, a, WHITE);
        }
    }

    // ------------------------------------------------------------------ 9 Rewrite
    private static void rewrite(Matrix4f m, Vec3 cam, FxPacket p, double age, double t) {
        float a = (float) Mth.clamp((p.life - age) / 6, 0, 1) * (float) Mth.clamp(age / 3, 0, 1);
        double R = 3 * Math.min(1, age / 5);
        clock(m, cam, p.c.add(0, 0.07, 0), UP, R, -t * 0.5, -t * 0.05, a);
        for (int i = 0; i < 24; i++) {   // marks round the rim turning back
            double an = -t * 0.06 + i * TAU / 24;
            Vec3 o = new Vec3(Math.cos(an), 0, Math.sin(an));
            GravityFxClient.gseg(m, cam, p.c.add(o.scale(R * 1.05)).add(0, 0.07, 0), p.c.add(o.scale(R * 1.15)).add(0, 0.07, 0), 0.04, a, a, i % 2 == 0 ? SILVER : BLUE);
        }
        if (p.u.lengthSqr() > 0 && age > 3) {   // the line to the one who'll pay
            double q = Math.min(1, (age - 3) / 4);
            Vec3 from = p.c.add(0, 1.2, 0), to = from.add(p.u.subtract(from).scale(q));
            GravityFxClient.gseg(m, cam, from, to, 0.18, a * 0.4F, a * 0.6F, BLUE);
            GravityFxClient.gseg(m, cam, from, to, 0.05, a, a, WHITE);
        }
    }

    // ------------------------------------------------------------------ 10 a fate thread, 11 a foreseen path, 12 a Glimpse
    private static void thread(Matrix4f m, Vec3 cam, Minecraft mc, FxPacket p, double age, double t, float pt) {
        Vec3 a0 = at(mc, (int) p.v.x, pt), b0 = at(mc, (int) p.v.y, pt);
        if (a0 == null || b0 == null) return;
        Entity ea = mc.level.getEntity((int) p.v.x), eb = mc.level.getEntity((int) p.v.y);
        Vec3 A = a0.add(0, ea.getBbHeight() * 0.6, 0), B = b0.add(0, eb.getBbHeight() * 0.6, 0);
        float a = (float) (Mth.clamp(age / 4, 0, 1) * Mth.clamp((p.life - age) / 6, 0, 1));
        Vec3 prev = A;
        for (int i = 1; i <= 16; i++) {
            double u = i / 16.0;
            Vec3 q = A.add(B.subtract(A).scale(u)).add(0, -Math.sin(u * Math.PI) * 0.5, 0);
            GravityFxClient.gseg(m, cam, prev, q, 0.025, a, a, SILVER);
            prev = q;
        }
        for (int k = 0; k < 2; k++) {
            double u = ((t * 0.04 + k * 0.5) % 1.0);
            Vec3 g = A.add(B.subtract(A).scale(u)).add(0, -Math.sin(u * Math.PI) * 0.5, 0);
            GravityFxClient.gglow(m, cam, g, 0.15, a, WHITE);
        }
        if (p.r > 0) {   // a blow passing along it
            float ha = (float) Mth.clamp(1 - age / 8, 0, 1);
            GravityFxClient.gseg(m, cam, A, B, 0.15, ha * 0.6F, ha * 0.6F, BLUE);
        }
    }

    private static void path(Matrix4f m, Vec3 cam, FxPacket p, double age, double t) {
        float a = (float) Mth.clamp((p.life - age) / 4, 0, 1);
        Vec3 ab = p.u.subtract(p.c);
        double len = ab.length();
        if (len < 0.1) return;
        Vec3 d = ab.scale(1 / len);
        double off = (t * 0.25) % 0.8;
        for (double s = off; s < len; s += 0.8) GravityFxClient.gseg(m, cam, p.c.add(d.scale(s)), p.c.add(d.scale(Math.min(len, s + 0.4))), 0.03, a, a, SILVER);
        GravityFxClient.ring(m, cam, p.u, d, 0.3, 0.04, BLUE, a);
    }

    private static void lunge(Matrix4f m, Vec3 cam, FxPacket p, double age) {
        double q = Mth.clamp(age / p.life, 0, 1);
        float a = (float) (Math.sin(q * Math.PI)) * 0.8F;
        Vec3 f = p.d;
        ghost(m, cam, p.c.add(f.scale(0.4 + q * 1.4)), f, 0.5, 0.9, a);
    }
}
