package com.ironspellgrimoire.client;

import com.ironspellgrimoire.network.FxPacket;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * The Stone Golem (Sol's Earth Magic), animated every frame so it moves smoothly: it climbs out of the ground, stands
 * with a slow breath, turns to its target and throws its fists - each arm a chain of floating stone chunks that winds
 * back, snaps out to the mark, holds a beat and returns. (FxPacket shape 59 = the golem, 60 = one punch.)
 */
final class GolemFx {
    private GolemFx() {}

    private static final int EARTH = 26, GOLD = 8;
    private static final Vec3 UP = new Vec3(0, 1, 0);

    static final class Golem {
        int id, life;
        Vec3 c, face, cur;
        float scale;
        double born;
        final List<double[]> punches = new ArrayList<>();   // {t0, x, y, z, arm}
    }

    static final List<Golem> GOLEMS = new ArrayList<>();

    static void spawn(FxPacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Golem g = new Golem();
        g.id = p.id; g.c = p.c; g.life = Math.max(20, p.life); g.scale = p.r > 0 ? p.r : 1F; g.born = mc.level.getGameTime();
        Vec3 f = new Vec3(p.d.x, 0, p.d.z);
        g.face = f.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : f.normalize();
        g.cur = g.face;
        GOLEMS.add(g);
    }

    static void punch(FxPacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        for (Golem g : GOLEMS) if (g.id == p.id) g.punches.add(new double[]{mc.level.getGameTime(), p.c.x, p.c.y, p.c.z, p.sweep});
    }

    static void tick(double now) {
        GOLEMS.removeIf(g -> now > g.born + g.life + 1);
    }

    private static double ease(double x) { x = Mth.clamp(x, 0.0, 1.0); return x * x * (3 - 2 * x); }
    private static double out(double x) { x = Mth.clamp(x, 0.0, 1.0); return 1 - (1 - x) * (1 - x) * (1 - x); }

    private static void blk(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 c, Vec3 fwd, Vec3 right, Vec3 up, double hl, double hw, double hh, int pal, float glow) {
        FxShapes.drawBox(vc, m, n, cam, c, fwd, right, up, hl, hw, hh, pal, 1.0F, glow);
    }

    static void draw(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Golem g, double now, float pt) {
        double age = now - g.born;
        if (age < 0 || age > g.life) return;
        double vis = Math.min(ease(age / 14.0), ease((g.life - age) / 12.0));
        if (vis <= 0.01) return;
        double S = g.scale;
        // turn toward whatever it is punching
        Vec3 want = g.face;
        for (double[] pu : g.punches) {
            double u = now - pu[0];
            if (u >= -1 && u < 14) {
                Vec3 d = new Vec3(pu[1] - g.c.x, 0, pu[3] - g.c.z);
                if (d.lengthSqr() > 0.25) want = d.normalize();
            }
        }
        g.cur = g.cur.scale(0.82).add(want.scale(0.18));
        Vec3 fwd = g.cur.lengthSqr() < 1.0E-6 ? g.face : g.cur.normalize();
        Vec3 right = new Vec3(-fwd.z, 0, fwd.x);
        double breath = 0.04 * Math.sin(age * 0.13), sink = (1 - vis) * 4.8 * S;
        Vec3 base = g.c.add(0, -sink, 0);
        float glow = 0.55F;
        // legs and feet
        for (int s = -1; s <= 1; s += 2) {
            blk(vc, m, n, cam, base.add(right.scale(s * 0.55 * S)).add(0, 0.9 * S, 0), fwd, right, UP, 0.42 * S, 0.38 * S, 0.9 * S, EARTH, glow);
            blk(vc, m, n, cam, base.add(right.scale(s * 0.55 * S)).add(fwd.scale(0.22 * S)).add(0, 0.2 * S, 0), fwd, right, UP, 0.62 * S, 0.44 * S, 0.2 * S, EARTH, glow);
        }
        // hips, torso, chest ridge
        blk(vc, m, n, cam, base.add(0, 1.95 * S, 0), fwd, right, UP, 0.55 * S, 0.95 * S, 0.25 * S, EARTH, glow);
        Vec3 tor = base.add(0, (2.95 + breath) * S, 0);
        blk(vc, m, n, cam, tor, fwd, right, UP, 0.65 * S, 1.05 * S, 0.85 * S, EARTH, glow);
        blk(vc, m, n, cam, tor.add(fwd.scale(0.62 * S)).add(0, 0.15 * S, 0), fwd, right, UP, 0.15 * S, 0.6 * S, 0.5 * S, EARTH, glow);
        blk(vc, m, n, cam, tor.add(fwd.scale(-0.6 * S)).add(0, 0.3 * S, 0), fwd, right, UP, 0.2 * S, 0.7 * S, 0.55 * S, EARTH, glow);   // a hunched back
        // shoulders: boulders
        for (int s = -1; s <= 1; s += 2) blk(vc, m, n, cam, base.add(right.scale(s * 1.5 * S)).add(0, (3.55 + breath) * S, 0), fwd, right, UP, 0.5 * S, 0.5 * S, 0.45 * S, EARTH, glow);
        // head with a heavy brow and a glowing slit
        Vec3 head = base.add(fwd.scale(0.12 * S)).add(0, (4.15 + breath * 1.5) * S, 0);
        blk(vc, m, n, cam, head, fwd, right, UP, 0.45 * S, 0.45 * S, 0.42 * S, EARTH, glow);
        blk(vc, m, n, cam, head.add(fwd.scale(0.42 * S)).add(0, 0.16 * S, 0), fwd, right, UP, 0.12 * S, 0.5 * S, 0.1 * S, EARTH, glow);
        blk(vc, m, n, cam, head.add(fwd.scale(0.47 * S)).add(0, 0.0, 0), fwd, right, UP, 0.03 * S, 0.36 * S, 0.06 * S, GOLD, 1.6F);
        // the arms: a chain of floating chunks from the shoulder to the fist
        for (int arm = 0; arm < 2; arm++) {
            int s = arm == 0 ? 1 : -1;
            Vec3 sh = base.add(right.scale(s * 1.5 * S)).add(0, (3.4 + breath) * S, 0);
            Vec3 rest = sh.add(0, -1.45 * S, 0).add(fwd.scale(0.35 * S)).add(right.scale(s * 0.2 * S)).add(0, 0.06 * S * Math.sin(age * 0.11 + arm), 0);
            Vec3 hand = rest;
            for (double[] pu : g.punches) {
                if ((int) pu[4] != arm) continue;
                double u = now + pt * 0 - pu[0];
                if (u < 0 || u > 12) continue;
                Vec3 tgt = new Vec3(pu[1], pu[2] - sink * 0, pu[3]).subtract(g.c.subtract(base));
                Vec3 toT = tgt.subtract(sh);
                double reach = 7.5 * S;
                if (toT.length() > reach) tgt = sh.add(toT.normalize().scale(reach));
                Vec3 wind = sh.add(0, 0.9 * S, 0).subtract(fwd.scale(0.9 * S)).add(right.scale(s * 0.3 * S));
                if (u < 2.5) hand = rest.add(wind.subtract(rest).scale(ease(u / 2.5)));            // wind back and up
                else if (u < 5.5) hand = wind.add(tgt.subtract(wind).scale(out((u - 2.5) / 3.0)));   // snap out to the mark
                else if (u < 8) hand = tgt;                                                            // hold the blow
                else hand = tgt.add(rest.subtract(tgt).scale(ease((u - 8) / 4.0)));                  // draw back
            }
            Vec3 d = hand.subtract(sh);
            double len = d.length();
            Vec3 ax = len < 1.0E-4 ? UP.scale(-1) : d.scale(1 / len);
            Vec3 sd = ax.cross(UP);
            sd = sd.lengthSqr() < 1.0E-6 ? right : sd.normalize();
            Vec3 up2 = sd.cross(ax).normalize();
            for (int k = 1; k <= 3; k++) {   // upper arm, elbow, wrist
                double f = k * 0.24;
                double sz = (0.36 - 0.04 * k) * S;
                blk(vc, m, n, cam, sh.add(d.scale(f)), ax, sd, up2, Math.max(0.2 * S, len * 0.11), sz, sz, EARTH, glow);
            }
            double fs = 0.62 * S;   // the fist
            blk(vc, m, n, cam, hand, ax, sd, up2, fs, fs * 0.95, fs * 0.9, EARTH, 0.9F);
            blk(vc, m, n, cam, hand.add(ax.scale(fs * 0.9)), ax, sd, up2, fs * 0.2, fs * 0.7, fs * 0.6, EARTH, 0.9F);   // knuckles
        }
    }
}
