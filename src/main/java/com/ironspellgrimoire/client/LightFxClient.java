package com.ironspellgrimoire.client;

import com.ironspellgrimoire.network.FxPacket;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Patolli's Light Magic, drawn: swords of pure light (a white-hot blade in a gold halo, a crossguard, a star glint
 * on the tip, shimmering while they hang and streaking when they fly), star glints, magic circles, pillars of light,
 * the lashing whip, the healing ray and the blinding burst. Everything is additive glow, so it reads as light.
 * <p>
 * Fed by FxPacket shape 66, {@code h} = kind:
 * <ul>
 *   <li>0 sword: c = start, u = end (same = it only hangs), d = which way it points while it hangs, r = length,
 *       r2 = width scale, sweep = ticks it hangs before flying, speed = ticks of flight, a0 = flags (1 vanish on
 *       arrival, 2 giant), a1 = shimmer seed, life</li>
 *   <li>1 glint: c, r = size, life</li>
 *   <li>2 pillar: c = ground, r = radius, r2 = height, sweep = ticks of warning circle before it strikes, life</li>
 *   <li>3 circle: c, d = facing (zero = flat on the ground), r = radius, speed = spin, life; v.x = entity to follow</li>
 *   <li>4 whip: c = origin, d = start direction, u = end direction, r = length, speed = ticks of the sweep, life;
 *       v.x = entity whose hand it comes from</li>
 *   <li>5 ray: c to u, r = width, life</li>
 *   <li>6 burst: c, r = reach, life</li>
 *   <li>7 blinded: your screen washes white and fades back over {@code life} ticks (sent only to the one blinded)</li>
 * </ul>
 * Shape 3 with the same id ends one early (a sword that struck something bursts).
 */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = "iron_spell_grimoire", value = net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class LightFxClient {
    private LightFxClient() {}

    private static final int GOLD = 8, WHITE = 28, SILVER = 6, DARK = 10;
    private static final double TAU = Math.PI * 2;

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
        if ((int) p.h == 7) { washStart = mc.level.getGameTime(); washLife = Math.max(1, p.life); return; }
        if (LIVE.size() > 900) LIVE.remove(0);
        LIVE.add(new L(p, mc.level.getGameTime()));
    }

    /** Ends one early; a sword bursts where it is. */
    public static boolean kill(int id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        double now = mc.level.getGameTime();
        boolean found = false;
        for (L l : LIVE) if (l.p.id == id && l.killAt < 0) {
            l.killAt = now;
            found = true;
        }
        return found;
    }

    static void clear() { LIVE.clear(); washLife = 0; }

    private static double washStart;
    private static int washLife;

    /** Blinded by the light: white over everything, fading back (never for long). */
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onGui(net.minecraftforge.client.event.RenderGuiEvent.Post e) {
        if (washLife <= 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { washLife = 0; return; }
        double age = mc.level.getGameTime() + e.getPartialTick() - washStart;
        if (age > washLife) { washLife = 0; return; }
        float a = (float) (age < 4 ? 0.82 : 0.82 * (1 - (age - 4) / Math.max(1, washLife - 4)));
        int al = (int) (Mth.clamp(a, 0F, 1F) * 255);
        var g = e.getGuiGraphics();
        g.fill(0, 0, g.guiWidth(), g.guiHeight(), al << 24 | 0xFFFBEA);
    }

    static void draw(Matrix4f m, Vec3 cam, double now, float pt) {
        if (LIVE.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { LIVE.clear(); return; }
        Camera camera = mc.gameRenderer.getMainCamera();
        Vector3f lv = camera.getLeftVector(), uv = camera.getUpVector();
        Vec3 left = new Vec3(lv.x(), lv.y(), lv.z()), up = new Vec3(uv.x(), uv.y(), uv.z());
        List<L> bursts = null;
        Iterator<L> it = LIVE.iterator();
        while (it.hasNext()) {
            L l = it.next();
            double age = now - l.born;
            double end = l.killAt >= 0 ? l.killAt + 3 : l.born + l.p.life;
            if (now > end) {
                it.remove();
                continue;
            }
            float out = l.killAt >= 0 ? (float) Mth.clamp((l.killAt + 3 - now) / 3.0, 0, 1) : 1F;
            switch ((int) l.p.h) {
                case 0 -> {
                    Vec3 at = sword(m, cam, left, up, l.p, age, now, out);
                    if (l.killAt >= 0 && Math.abs(now - l.killAt) < 0.5 && at != null) {
                        if (bursts == null) bursts = new ArrayList<>();
                        FxPacket b = new FxPacket();
                        b.h = 6; b.c = at; b.r = Math.max(0.8F, l.p.r * 0.6F); b.life = 7; b.palette = l.p.palette;
                        bursts.add(new L(b, now));
                    }
                }
                case 1 -> {
                    float k = env(age, l.p.life, 2, 5) * out;
                    float tw = 0.7F + 0.3F * (float) Math.sin(now * 1.3 + l.p.id);
                    star(m, cam, left, up, l.p.c, l.p.r * tw, l.p.palette, k);
                }
                case 2 -> pillar(m, cam, l.p, age, now, out);
                case 3 -> {
                    Vec3 c = l.p.c;
                    if (l.p.v.x != 0 || l.p.v.y != 0) {
                        Entity e = mc.level.getEntity((int) l.p.v.x);
                        if (e != null) c = new Vec3(Mth.lerp(pt, e.xo, e.getX()), Mth.lerp(pt, e.yo, e.getY()), Mth.lerp(pt, e.zo, e.getZ())).add(0, l.p.v.z, 0);
                    }
                    circle(m, cam, c, l.p.d, l.p.r, now * l.p.speed, l.p.palette, env(age, l.p.life, 4, 6) * out);
                }
                case 4 -> whip(m, cam, mc, l.p, age, now, pt, out);
                case 5 -> ray(m, cam, l.p, age, now, out);
                default -> burst(m, cam, left, up, l.p, age, out);
            }
        }
        if (bursts != null) LIVE.addAll(bursts);
    }

    /** 0 → 1 over {@code in} ticks, 1 → 0 over the last {@code fade}. */
    private static float env(double age, double life, double in, double fade) {
        return (float) (Mth.clamp(age / in, 0, 1) * Mth.clamp((life - age) / fade, 0, 1));
    }

    private static double smooth(double x) { x = Mth.clamp(x, 0, 1); return x * x * (3 - 2 * x); }

    // ------------------------------------------------------------------ the light sword
    private static Vec3 sword(Matrix4f m, Vec3 cam, Vec3 left, Vec3 up, FxPacket p, double age, double now, float out) {
        int flags = (int) p.a0;
        boolean vanish = (flags & 1) != 0, giant = (flags & 2) != 0;
        double hang = p.sweep, fly = Math.max(1, p.speed);
        Vec3 travel = p.u.subtract(p.c);
        boolean moves = travel.lengthSqr() > 1.0E-4;
        Vec3 pos, dir;
        double moving = 0;
        if (!moves || age <= hang) {
            double bob = Math.sin(now * 0.25 + p.a1) * 0.04 * p.r;
            pos = p.c.add(0, bob, 0);
            dir = p.d.lengthSqr() < 1.0E-6 ? (moves ? travel.normalize() : new Vec3(0, -1, 0)) : p.d.normalize();
            if (moves) {   // in the last few ticks it turns to face where it will fly
                double turn = smooth(1 - (hang - age) / 4.0);
                dir = dir.scale(1 - turn).add(travel.normalize().scale(turn));
                if (dir.lengthSqr() < 1.0E-6) dir = travel.normalize();
                dir = dir.normalize();
            }
        } else {
            double k = Mth.clamp((age - hang) / fly, 0, 1);
            double e = k * k * (2 - k) * 0.5 + k * 0.5;   // eases in, then streaks
            pos = p.c.add(travel.scale(e));
            dir = travel.normalize();
            moving = k < 1 ? 1 : 0;
            if (k >= 1 && vanish) return pos;
        }
        float len = p.r, ws = p.r2 > 0 ? p.r2 : 1F;
        float a = env(age, p.life, giant ? 6 : 3, giant ? 10 : 5) * out;
        float shimmer = 0.78F + 0.22F * (float) Math.sin(now * 0.9 + p.a1 * 3.1);
        a *= moving > 0 ? 1F : shimmer;
        if (a <= 0.01F) return pos;
        int pal = p.palette;
        boolean demon = pal == DARK;
        Vec3 tip = pos.add(dir.scale(len * 0.62)), guard = pos.subtract(dir.scale(len * 0.16)), pommel = pos.subtract(dir.scale(len * 0.38));
        Vec3 side = dir.cross(pos.subtract(cam));
        side = side.lengthSqr() < 1.0E-6 ? left : side.normalize();
        // the blade: a gold halo round a white-hot core, tapering to the tip
        FxBase.gline(m, cam, guard, tip, len * 0.11F * ws, demon ? DARK : pal, a * 0.8F);
        FxBase.gline(m, cam, guard, guard.add(dir.scale(len * 0.55)), len * 0.05F * ws, demon ? SILVER : WHITE, a);
        FxBase.gline(m, cam, guard.add(dir.scale(len * 0.5)), tip, len * 0.03F * ws, demon ? SILVER : WHITE, a);
        // crossguard and grip
        double gw = len * 0.15;
        FxBase.gline(m, cam, guard.add(side.scale(gw)), guard.subtract(side.scale(gw)), len * 0.05F * ws, pal, a);
        FxBase.gline(m, cam, guard, pommel, len * 0.04F * ws, pal, a * 0.75F);
        if (giant) {   // the giant sword: light pouring off its edges
            for (int i = 0; i < 6; i++) {
                double t = (i + 0.5) / 6.0;
                Vec3 q = guard.add(tip.subtract(guard).scale(t));
                double fl = len * 0.12 * (0.6 + 0.4 * Math.sin(now * 0.7 + i * 1.7));
                FxBase.gline(m, cam, q, q.add(side.scale(fl)), len * 0.015F, pal, a * 0.5F);
                FxBase.gline(m, cam, q, q.subtract(side.scale(fl)), len * 0.015F, pal, a * 0.5F);
            }
        }
        if (moving > 0) FxBase.gline(m, cam, pos.subtract(dir.scale(len * (giant ? 2.5 : 1.8))), guard, len * 0.06F * ws, pal, a * 0.4F);   // the streak behind
        float tw = 0.6F + 0.4F * (float) Math.sin(now * 1.1 + p.a1 * 5.0);
        star(m, cam, left, up, tip, len * 0.2F * tw * (giant ? 0.6F : 1F), demon ? SILVER : pal, a * 0.9F);
        return pos;
    }

    /** A four-pointed star glint, facing the camera, with a softer diagonal cross. */
    static void star(Matrix4f m, Vec3 cam, Vec3 left, Vec3 up, Vec3 c, float s, int pal, float a) {
        if (a <= 0.01F || s <= 0.001F) return;
        FxBase.gline(m, cam, c.add(left.scale(s)), c.subtract(left.scale(s)), s * 0.14F, pal, a);
        FxBase.gline(m, cam, c.add(up.scale(s * 1.25)), c.subtract(up.scale(s * 1.25)), s * 0.14F, pal, a);
        Vec3 d1 = left.add(up).normalize().scale(s * 0.45), d2 = left.subtract(up).normalize().scale(s * 0.45);
        FxBase.gline(m, cam, c.add(d1), c.subtract(d1), s * 0.08F, pal, a * 0.6F);
        FxBase.gline(m, cam, c.add(d2), c.subtract(d2), s * 0.08F, pal, a * 0.6F);
    }

    // ------------------------------------------------------------------ circles and pillars
    /** A magic circle: two rings, a six-pointed star and twelve marks, turning. */
    static void circle(Matrix4f m, Vec3 cam, Vec3 c, Vec3 face, float r, double spin, int pal, float a) {
        if (a <= 0.01F) return;
        Vec3 n = face.lengthSqr() < 1.0E-6 ? new Vec3(0, 1, 0) : face.normalize();
        Vec3 e1 = Math.abs(n.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        e1 = e1.subtract(n.scale(e1.dot(n))).normalize();
        Vec3 e2 = n.cross(e1);
        c = c.add(n.scale(0.04));
        float w = Math.max(0.05F, r * 0.03F);
        int seg = Math.max(24, Math.min(64, (int) (r * 10)));
        ring(m, cam, c, e1, e2, r, seg, spin, w, pal, a);
        ring(m, cam, c, e1, e2, r * 0.82F, seg, -spin, w * 0.7F, pal, a * 0.8F);
        for (int t = 0; t < 2; t++) {   // two triangles: the star
            for (int i = 0; i < 3; i++) {
                double a0 = -spin + t * Math.PI / 3 + i * TAU / 3, a1 = a0 + TAU / 3;
                FxBase.gline(m, cam, on(c, e1, e2, r * 0.82, a0), on(c, e1, e2, r * 0.82, a1), w * 0.7F, pal, a * 0.7F);
            }
        }
        for (int i = 0; i < 12; i++) {   // marks between the rings
            double an = spin + i * TAU / 12;
            FxBase.gline(m, cam, on(c, e1, e2, r * 0.86, an), on(c, e1, e2, r * 0.96, an), w * 0.8F, WHITE, a * 0.8F);
        }
    }

    private static Vec3 on(Vec3 c, Vec3 e1, Vec3 e2, double r, double an) {
        return c.add(e1.scale(Math.cos(an) * r)).add(e2.scale(Math.sin(an) * r));
    }

    private static void ring(Matrix4f m, Vec3 cam, Vec3 c, Vec3 e1, Vec3 e2, float r, int seg, double spin, float w, int pal, float a) {
        Vec3 prev = on(c, e1, e2, r, spin);
        for (int i = 1; i <= seg; i++) {
            Vec3 q = on(c, e1, e2, r, spin + i * TAU / seg);
            FxBase.gline(m, cam, prev, q, w, pal, a);
            prev = q;
        }
    }

    private static void pillar(Matrix4f m, Vec3 cam, FxPacket p, double age, double now, float out) {
        Vec3 c = p.c, upv = new Vec3(0, 1, 0);
        float r = p.r, h = p.r2;
        double warn = p.sweep;
        int pal = p.palette;
        if (age < warn) {   // the warning: a circle drawing itself on the ground, light gathering above it
            double k = age / Math.max(1, warn);
            float a = (float) Mth.clamp(age / 4.0, 0, 1) * out;
            circle(m, cam, c, Vec3.ZERO, r, now * 0.05, pal, a * (0.6F + 0.4F * (float) Math.sin(now * 0.8)));
            for (int i = 0; i < 8; i++) {
                double an = i * TAU / 8 + now * 0.03;
                Vec3 b = c.add(Math.cos(an) * r, 0, Math.sin(an) * r);
                FxBase.gline(m, cam, b, b.add(0, h * 0.25 * k, 0), 0.06F, pal, a * 0.5F);
            }
            return;
        }
        double t = age - warn, life = p.life - warn;
        float grow = (float) smooth(t / 3.0), a = env(t, life, 1, 10) * out;
        if (a <= 0.01F) return;
        Vec3 top = c.add(0, h, 0);
        FxBase.gline(m, cam, c, top, r * 2.2F * grow, pal, a * 0.55F);                    // the shaft
        FxBase.gline(m, cam, c, top, r * 1.1F * grow, WHITE, a);                          // its white heart
        for (int i = 0; i < 10; i++) {   // light streaming up its edge
            double an = i * TAU / 10 + now * 0.08;
            double rise = ((now * 0.9 + i * 3.7) % h);
            Vec3 b = c.add(Math.cos(an) * r * 1.05, rise, Math.sin(an) * r * 1.05);
            FxBase.gline(m, cam, b, b.add(upv.scale(Math.min(h - rise, h * 0.18))), 0.1F, pal, a * 0.7F);
        }
        float rr = r * (1F + (float) t * 0.35F);
        circle(m, cam, c, Vec3.ZERO, Math.min(rr, r * 2.4F), now * 0.08, pal, a * 0.7F);
    }

    // ------------------------------------------------------------------ the whip and the ray
    private static void whip(Matrix4f m, Vec3 cam, Minecraft mc, FxPacket p, double age, double now, float pt, float out) {
        Vec3 o = p.c;
        if (p.v.x != 0) {
            Entity e = mc.level.getEntity((int) p.v.x);
            if (e != null) {
                Vec3 feet = new Vec3(Mth.lerp(pt, e.xo, e.getX()), Mth.lerp(pt, e.yo, e.getY()), Mth.lerp(pt, e.zo, e.getZ()));
                double yr = Math.toRadians(e.getViewYRot(pt));
                Vec3 face = new Vec3(-Math.sin(yr), 0, Math.cos(yr)), rt = new Vec3(-face.z, 0, face.x);
                o = feet.add(0, e.getBbHeight() * 0.7, 0).add(face.scale(0.4)).add(rt.scale(-0.35));
            }
        }
        float a = env(age, p.life, 1, 5) * out;
        double sweep = Math.max(1, p.speed);
        for (int ghost = 2; ghost >= 0; ghost--) {   // afterimages of where it just was
            double k = smooth((age - ghost * 1.2) / sweep);
            if (age - ghost * 1.2 < 0) continue;
            Vec3 d = p.d.scale(1 - k).add(p.u.scale(k));
            if (d.lengthSqr() < 1.0E-6) continue;
            d = d.normalize();
            Vec3 side = d.cross(new Vec3(0, 1, 0));
            side = side.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : side.normalize();
            float ga = a * (ghost == 0 ? 1F : 0.3F / ghost);
            Vec3 prev = o;
            int n = 26;
            for (int i = 1; i <= n; i++) {
                double t = (double) i / n;
                double lag = Math.sin(t * Math.PI) * 0.12 * p.r * (1 - k);                  // the end trails behind the sweep
                double wave = Math.sin(now * 1.4 - t * 9) * 0.12 * t;
                Vec3 q = o.add(d.scale(p.r * t)).add(side.scale(-lag * Math.signum(p.u.subtract(p.d).dot(side)) + wave)).add(0, wave * 0.6, 0);
                FxBase.gline(m, cam, prev, q, (float) (0.22 * (1 - t * 0.6)), p.palette, ga);
                if (ghost == 0) FxBase.gline(m, cam, prev, q, (float) (0.08 * (1 - t * 0.5)), WHITE, ga);
                prev = q;
            }
        }
    }

    private static void ray(Matrix4f m, Vec3 cam, FxPacket p, double age, double now, float out) {
        float a = env(age, p.life, 2, 6) * out;
        if (a <= 0.01F) return;
        Vec3 ab = p.u.subtract(p.c);
        double len = ab.length();
        if (len < 1.0E-3) return;
        Vec3 d = ab.scale(1 / len);
        FxBase.gline(m, cam, p.c, p.u, p.r, p.palette, a * 0.6F);
        FxBase.gline(m, cam, p.c, p.u, p.r * 0.4F, WHITE, a);
        Vec3 e1 = d.cross(new Vec3(0, 1, 0));
        e1 = e1.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : e1.normalize();
        Vec3 e2 = d.cross(e1);
        for (int s = 0; s < 2; s++) {   // two strands winding round it
            Vec3 prev = null;
            int n = Math.max(8, (int) (len * 4));
            for (int i = 0; i <= n; i++) {
                double t = (double) i / n, an = t * len * 2.4 - now * 0.5 + s * Math.PI;
                Vec3 q = p.c.add(d.scale(len * t)).add(e1.scale(Math.cos(an) * p.r * 1.3)).add(e2.scale(Math.sin(an) * p.r * 1.3));
                if (prev != null) FxBase.gline(m, cam, prev, q, p.r * 0.25F, p.palette, a * 0.7F);
                prev = q;
            }
        }
    }

    private static void burst(Matrix4f m, Vec3 cam, Vec3 left, Vec3 up, FxPacket p, double age, float out) {
        double k = Mth.clamp(age / Math.max(1, p.life), 0, 1);
        float a = (float) (1 - k) * out;
        if (a <= 0.01F) return;
        int n = 14;
        for (int i = 0; i < n; i++) {   // rays thrown out in every direction
            double an = i * TAU / n + p.id * 0.37;
            double el = ((i * 7 + p.id) % 5 - 2) * 0.35;
            Vec3 d = new Vec3(Math.cos(an) * Math.cos(el), Math.sin(el), Math.sin(an) * Math.cos(el));
            double r0 = p.r * k * 0.5, r1 = p.r * (0.35 + 0.65 * smooth(k * 2.2)) * (i % 2 == 0 ? 1 : 0.65);
            FxBase.gline(m, cam, p.c.add(d.scale(r0)), p.c.add(d.scale(r1)), (float) (0.09 * p.r * (1 - k)), p.palette, a);
        }
        star(m, cam, left, up, p.c, p.r * 0.7F * (float) (1 - k * 0.5), WHITE, a);
    }
}
