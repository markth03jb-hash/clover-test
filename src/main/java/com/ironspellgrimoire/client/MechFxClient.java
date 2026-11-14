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
 * The Mech Grimoire, drawn: a machine of solid gunmetal boxes with hazard-yellow trim and an orange visor slit,
 * blue thruster flame, rivet tracers and sparks, smoke rings. While Pilot runs the machine stands round you - only
 * the parts you have built (no torso without Plating, no arms without Arms...) - and while the book is equipped your
 * screen shows the blueprint of the whole frame, each part lighting up as it's bolted on and blinking out as it runs
 * down. Fed by FxPacket shape 94 (server/MechFx), {@code h} = kind: 0 rivet, 1 thrust, 2 rocket fist, 3 stomp,
 * 4 plating, 5 missile, 6 overclock, 7 rail cannon, 8 pilot, 9 titan buster, 10 part installed, 11 burst, 99 HUD.
 */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = "iron_spell_grimoire", value = net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class MechFxClient {
    private MechFxClient() {}

    private static final double TAU = Math.PI * 2;
    private static final Vec3 UP = new Vec3(0, 1, 0);
    static final float[] METAL = {0.33F, 0.36F, 0.41F}, METAL_D = {0.2F, 0.22F, 0.26F}, STEEL = {0.6F, 0.64F, 0.7F}, HAZARD = {1.0F, 0.76F, 0.1F},
            VISOR = {1.0F, 0.45F, 0.12F}, FLAME = {0.35F, 0.65F, 1.0F}, FLAME_W = {0.85F, 0.95F, 1.0F}, SPARK = {1.0F, 0.85F, 0.45F},
            SMOKE = {0.55F, 0.55F, 0.58F}, BLUEPRINT = {0.35F, 0.75F, 1.0F};

    private static final class L {
        final FxPacket p;
        final double born;
        L(FxPacket p, double born) { this.p = p; this.born = born; }
    }

    private static final List<L> LIVE = new ArrayList<>();
    private static int mask, lastMask;
    private static boolean equipped, piloting;
    private static final double[] LIT = new double[8];

    public static void spawn(FxPacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if ((int) p.h == 98) { LIVE.removeIf(l -> (int) l.p.v.x == (int) p.v.x); return; }   // a mode was ended early: drop what hangs on that player
        if ((int) p.h == 99) {
            int m = (int) p.a0;
            for (int i = 0; i < 8; i++) if ((m & 1 << i) != 0 && (lastMask & 1 << i) == 0) LIT[i] = mc.level.getGameTime();
            lastMask = mask = m; equipped = p.r > 0.5F; piloting = p.r2 > 0.5F;
            return;
        }
        if (LIVE.size() > 300) LIVE.remove(0);
        LIVE.add(new L(p, mc.level.getGameTime()));
    }

    /** Checked on the client every frame, so the HUD goes the moment you switch to another grimoire (the server's sync can lag up to 2 s). */
    private static boolean held(Minecraft mc) {
        var b = mc.player == null ? null : io.redspace.ironsspellbooks.api.util.Utils.getPlayerSpellbookStack(mc.player);
        return b != null && b.is(com.ironspellgrimoire.registry.AddonItemRegistry.MECH_GRIMOIRE.get());
    }

    static void clear() { LIVE.clear(); }

    // ================================================================== the blueprint on the screen
    /** Each part's rectangle on the blueprint (x, y, w, h in a 24x34 frame): arms, thrusters, fists, legs, plating, launchers, core, cannon. */
    private static final int[][] BP = {
            {1, 9, 4, 11}, {19, 9, 4, 11},          // arms (left, right)
            {7, 6, 10, 3},                          // thrusters (a pack on the shoulders)
            {0, 20, 5, 4}, {19, 20, 5, 4},          // fists
            {7, 21, 4, 12}, {13, 21, 4, 12},        // legs
            {6, 8, 12, 13},                         // plating (torso)
            {2, 4, 5, 4},                           // launchers (on the shoulder)
            {10, 12, 4, 4},                         // core
            {17, 3, 7, 3},                          // cannon (over the shoulder)
    };
    private static final int[] BP_PART = {0, 0, 1, 2, 2, 3, 3, 4, 5, 6, 7};

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onGui(net.minecraftforge.client.event.RenderGuiEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui || !equipped || !held(mc)) return;
        var g = e.getGuiGraphics();
        double t = mc.level.getGameTime() + e.getPartialTick();
        int ox = g.guiWidth() / 2 - 91 - 29 - 30, oy = g.guiHeight() - 38;
        g.fill(ox - 2, oy - 2, ox + 26, oy + 36, 0x88061524);   // the blueprint paper
        for (int x = 0; x <= 24; x += 4) g.fill(ox + x, oy - 2, ox + x + 1, oy + 36, 0x2240A0FF);
        for (int y = 0; y <= 34; y += 4) g.fill(ox - 2, oy + y, ox + 26, oy + y + 1, 0x2240A0FF);
        g.fill(ox + 9, oy, ox + 15, oy + 6, 0xFF2A3440);   // the head, always there
        g.fill(ox + 10, oy + 2, ox + 14, oy + 3, piloting ? 0xFFFF7A20 : 0xFF6A4A30);
        int drawn = 0;
        // draw the torso first so the core sits on top of it
        int[] order = {7, 0, 1, 2, 3, 4, 5, 6, 8, 9, 10};
        for (int k : order) {
            int part = BP_PART[k];
            int[] r = BP[k];
            boolean on = (mask & 1 << part) != 0;
            if (on) {
                double since = t - LIT[part];
                int flash = since < 10 ? (int) (255 * (1 - since / 10)) : 0;
                int base = part == 6 ? 0xFFFF8A2A : 0xFF55606E;
                g.fill(ox + r[0], oy + r[1], ox + r[0] + r[2], oy + r[1] + r[3], base);
                g.fill(ox + r[0], oy + r[1], ox + r[0] + r[2], oy + r[1] + 1, 0xFFFFC21A);   // hazard trim on its top
                if (flash > 0) g.fill(ox + r[0], oy + r[1], ox + r[0] + r[2], oy + r[1] + r[3], flash << 24 | 0xFFFFFF);
                drawn++;
            } else {   // just the dashed outline
                int c = 0x995FB8FF;
                for (int x = r[0]; x < r[0] + r[2]; x += 2) { g.fill(ox + x, oy + r[1], ox + x + 1, oy + r[1] + 1, c); g.fill(ox + x, oy + r[1] + r[3] - 1, ox + x + 1, oy + r[1] + r[3], c); }
                for (int y = r[1]; y < r[1] + r[3]; y += 2) { g.fill(ox + r[0], oy + y, ox + r[0] + 1, oy + y + 1, c); g.fill(ox + r[0] + r[2] - 1, oy + y, ox + r[0] + r[2], oy + y + 1, c); }
            }
        }
        int n = Integer.bitCount(mask);
        boolean full = n == 8;
        String label = full ? "100%" : n + "/8";
        int col = full ? ((int) (t / 5) % 2 == 0 ? 0xFFFFC21A : 0xFFFFFFFF) : 0xFF9FD8FF;
        g.drawString(mc.font, label, ox + 12 - mc.font.width(label) / 2, oy - 12, col, true);
    }

    // ================================================================== the world
    static void draw(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, double now, float pt) {
        if (LIVE.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { LIVE.clear(); return; }
        List<L> live = new ArrayList<>(LIVE);
        for (L l : live) {
            double age = now - l.born;
            if (age > l.p.life) { LIVE.remove(l); continue; }
            FxPacket p = l.p;
            switch ((int) p.h) {
                case 0 -> rivet(m, cam, p, age);
                case 1 -> thrust(m, cam, mc, p, age, now, pt);
                case 2 -> fist(vc, m, n, cam, mc, p, age, now, pt);
                case 3 -> stomp(vc, m, n, cam, p, age);
                case 4 -> plates(vc, m, n, cam, mc, p, age, now, pt);
                case 5 -> missile(vc, m, n, cam, p, age, now);
                case 6 -> overclock(m, cam, mc, p, age, now, pt);
                case 7 -> rail(m, cam, mc, p, age, now, pt);
                case 8 -> pilot(vc, m, n, cam, mc, p, age, now, pt);
                case 9 -> titan(vc, m, n, cam, p, age, now);
                case 10 -> installed(m, cam, mc, p, age, pt);
                case 11 -> burst(m, cam, p, age);
                default -> LIVE.remove(l);
            }
        }
    }

    // ================================================================== solid boxes
    /** A box centred at c with axes x, y, z (unit) and half-sizes hx, hy, hz; the top face takes {@code top} colour. */
    static void box(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 c, Vec3 x, Vec3 y, Vec3 z, double hx, double hy, double hz, float[] col, float[] top, float a) {
        Vec3 X = x.scale(hx), Y = y.scale(hy), Z = z.scale(hz);
        Vec3[] v = new Vec3[8];
        for (int i = 0; i < 8; i++) v[i] = c.add(X.scale((i & 1) == 0 ? -1 : 1)).add(Y.scale((i & 2) == 0 ? -1 : 1)).add(Z.scale((i & 4) == 0 ? -1 : 1));
        int[][] faces = {{0, 1, 3, 2}, {4, 6, 7, 5}, {0, 4, 5, 1}, {2, 3, 7, 6}, {0, 2, 6, 4}, {1, 5, 7, 3}};
        float[][] shade = {col, col, darker(col), top, darker(col), col};
        for (int f = 0; f < 6; f++) {
            int[] q = faces[f];
            FxBase.quad(vc, m, n, cam, v[q[0]], v[q[1]], v[q[2]], v[q[3]], shade[f], a, a, a, a);
            FxBase.quad(vc, m, n, cam, v[q[3]], v[q[2]], v[q[1]], v[q[0]], shade[f], a, a, a, a);
        }
    }

    private static float[] darker(float[] c) { return new float[]{c[0] * 0.7F, c[1] * 0.7F, c[2] * 0.75F}; }

    private static Vec3 pos(Entity e, float pt) { return new Vec3(Mth.lerp(pt, e.xo, e.getX()), Mth.lerp(pt, e.yo, e.getY()), Mth.lerp(pt, e.zo, e.getZ())); }

    // ================================================================== 0 rivet
    private static void rivet(Matrix4f m, Vec3 cam, FxPacket p, double age) {
        double k = Mth.clamp(age / 3, 0, 1);
        Vec3 d = p.u.subtract(p.c);
        Vec3 head = p.c.add(d.scale(k)), tail = p.c.add(d.scale(Math.max(0, k - 0.35)));
        if (k < 1) {
            GravityFxClient.gseg(m, cam, tail, head, 0.05, 0, 1, SPARK);
            GravityFxClient.gglow(m, cam, head, 0.12, 1, FLAME_W);
            GravityFxClient.gglow(m, cam, p.c, 0.3 * (1 - k), 0.8F, SPARK);   // muzzle flash
            return;
        }
        double q = (age - 3) / (p.life - 3);
        for (int i = 0; i < 7; i++) {   // sparks off the hit
            Vec3 sd = new Vec3(FxBase.h2(p.id, i, 1) - 0.5, FxBase.h2(p.id, i, 2) * 0.8, FxBase.h2(p.id, i, 3) - 0.5).normalize();
            Vec3 s = p.u.add(sd.scale(0.9 * q)).add(0, -0.4 * q * q, 0);
            GravityFxClient.gseg(m, cam, s, s.subtract(sd.scale(0.2)), 0.025, (float) (1 - q), 0, SPARK);
        }
    }

    // ================================================================== 1 thrust
    private static void thrust(Matrix4f m, Vec3 cam, Minecraft mc, FxPacket p, double age, double now, float pt) {
        Entity e = mc.level.getEntity((int) p.v.x);
        float a = (float) Mth.clamp((p.life - age) / 5, 0, 1);
        Vec3 d = p.u.subtract(p.c), dir = d.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : d.normalize();
        if (e != null) {   // twin jets from the back
            Vec3 f = pos(e, pt), side = UP.cross(dir).normalize();
            for (int s = -1; s <= 1; s += 2) {
                Vec3 nz = f.add(0, e.getBbHeight() * 0.62, 0).add(side.scale(0.18 * s)).subtract(dir.scale(0.3));
                double len = 1.4 + 0.3 * Math.sin(now * 2 + s);
                GravityFxClient.gseg(m, cam, nz, nz.subtract(dir.scale(len)).add(0, -0.2, 0), 0.16, a, 0, FLAME);
                GravityFxClient.gseg(m, cam, nz, nz.subtract(dir.scale(len * 0.5)), 0.07, a, 0, FLAME_W);
            }
        }
        for (int i = 0; i < 6; i++) {   // smoke rings left along the path
            double u = (i + 0.5) / 6, born = u * 6;
            if (age < born) continue;
            double q = Mth.clamp((age - born) / 10, 0, 1);
            GravityFxClient.ring(m, cam, p.c.add(d.scale(u)).add(0, 0.9, 0), dir, 0.3 + 0.5 * q, 0.08, SMOKE, (float) (1 - q) * 0.6F);
        }
    }

    // ================================================================== 2 the rocket fist
    private static void fist(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Minecraft mc, FxPacket p, double age, double now, float pt) {
        double out = Math.max(1, p.sweep);
        Entity e = mc.level.getEntity((int) p.v.x);
        Vec3 home = e != null ? pos(e, pt).add(0, e.getBbHeight() * 0.6, 0) : p.c;
        Vec3 at;
        boolean back = age > out + 2;
        if (age <= out) at = p.c.add(p.u.subtract(p.c).scale(age / out));
        else if (!back) at = p.u;
        else at = p.u.add(home.subtract(p.u).scale(Mth.clamp((age - out - 2) / (out - 2), 0, 1)));
        Vec3 dir = back ? at.subtract(home) : p.u.subtract(p.c);   // it always faces away from its owner
        dir = dir.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : dir.normalize();
        Vec3 side = Math.abs(dir.y) > 0.95 ? new Vec3(1, 0, 0) : UP.cross(dir).normalize(), up = dir.cross(side).normalize();
        double s = 0.32 * p.r;
        float a = (float) Mth.clamp((p.life - age) / 3, 0, 1);
        box(vc, m, n, cam, at, side, up, dir, s, s * 0.85, s, METAL, STEEL, a);                         // the fist
        box(vc, m, n, cam, at.add(dir.scale(s * 1.02)), side, up, dir, s * 0.95, s * 0.2, 0.02, HAZARD, HAZARD, a);   // knuckle plate
        box(vc, m, n, cam, at.subtract(dir.scale(s * 1.6)), side, up, dir, s * 0.6, s * 0.6, s * 0.7, METAL_D, METAL, a);   // the wrist
        Vec3 nozzle = at.subtract(dir.scale(s * 2.3));
        double fl = 0.7 + 0.25 * Math.sin(now * 3);
        GravityFxClient.gseg(m, cam, nozzle, nozzle.subtract(dir.scale(fl * p.r)), 0.18 * p.r, a, 0, FLAME);
        GravityFxClient.gseg(m, cam, nozzle, nozzle.subtract(dir.scale(fl * 0.5 * p.r)), 0.08 * p.r, a, 0, FLAME_W);
    }

    // ================================================================== 3 the stomp
    private static void stomp(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age) {
        double k = Mth.clamp(age / p.life, 0, 1);
        float a = (float) (1 - k);
        for (int i = 0; i < 4; i++) {   // four piston rams driven into the ground round you
            double an = i * TAU / 4 + 0.785;
            Vec3 b = p.c.add(Math.cos(an) * 0.9, 0, Math.sin(an) * 0.9);
            double ext = age < 3 ? 1.2 * (1 - age / 3) : 0;
            box(vc, m, n, cam, b.add(0, 0.2 + ext + 0.5, 0), new Vec3(1, 0, 0), UP, new Vec3(0, 0, 1), 0.08, 0.5, 0.08, STEEL, STEEL, a);
            box(vc, m, n, cam, b.add(0, 0.12 + ext, 0), new Vec3(1, 0, 0), UP, new Vec3(0, 0, 1), 0.18, 0.1, 0.18, HAZARD, HAZARD, a);
        }
        for (int r = 0; r < 3; r++) {   // the jolt rolling outward
            double q = Mth.clamp((age - r * 2) / (p.life * 0.6), 0, 1);
            if (q <= 0 || q >= 1) continue;
            GravityFxClient.ring(m, cam, p.c.add(0, 0.08, 0), UP, p.r * q, 0.25, r == 0 ? SPARK : SMOKE, (float) (1 - q) * 0.8F);
        }
        for (int i = 0; i < 12; i++) {   // grit kicked up (light only)
            double an = i * TAU / 12, q = Mth.clamp(age / 10, 0, 1);
            Vec3 s = p.c.add(Math.cos(an) * p.r * 0.5 * q, 1.4 * q * (1 - q) * 2, Math.sin(an) * p.r * 0.5 * q);
            GravityFxClient.gglow(m, cam, s, 0.12, (float) (1 - q) * 0.6F, SMOKE);
        }
    }

    // ================================================================== 4 plating
    private static void plates(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Minecraft mc, FxPacket p, double age, double now, float pt) {
        Entity e = mc.level.getEntity((int) p.v.x);
        if (e == null) return;
        Vec3 f = pos(e, pt);
        float a = (float) (Mth.clamp(age / 4, 0, 1) * Mth.clamp((p.life - age) / 8, 0, 1));
        boolean self = e == mc.player && mc.options.getCameraType().isFirstPerson();
        double lock = FxBase.backOut(Mth.clamp(age / 8, 0, 1));
        int N = 6;
        for (int i = 0; i < N; i++) {   // plates slam in from outside and hang round you as a ring
            double an = i * TAU / N + now * 0.02;
            Vec3 out = new Vec3(Math.cos(an), 0, Math.sin(an)), side = UP.cross(out).normalize();
            double R = 0.75 + 1.8 * (1 - lock);
            Vec3 c = f.add(out.scale(R)).add(0, e.getBbHeight() * 0.55, 0);
            if (self && out.dot(mc.player.getLookAngle()) > 0.3) continue;
            box(vc, m, n, cam, c, side, UP, out, 0.32, 0.55, 0.04, METAL, METAL, 0.85F * a);
            box(vc, m, n, cam, c.add(0, 0.45, 0).add(out.scale(0.03)), side, UP, out, 0.3, 0.04, 0.02, HAZARD, HAZARD, a);
            box(vc, m, n, cam, c.add(0, -0.45, 0).add(out.scale(0.03)), side, UP, out, 0.3, 0.04, 0.02, HAZARD, HAZARD, a);
        }
        if (age < 10 && age > 6) GravityFxClient.ring(m, cam, f.add(0, e.getBbHeight() * 0.55, 0), UP, 0.9, 0.2, SPARK, (float) (1 - (age - 6) / 4));
    }

    // ================================================================== 5 a missile
    private static void missile(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age, double now) {
        double fly = Math.max(1, p.sweep);
        if (age >= fly) return;   // the landing is its own burst
        Vec3 d = p.u.subtract(p.c);
        double side0 = (FxBase.h2(p.id, (int) p.a0, 4) - 0.5) * 6;
        Vec3 sd0 = UP.cross(d.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : d.normalize());
        final Vec3 sd = sd0.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : sd0.normalize();
        java.util.function.DoubleFunction<Vec3> path = u -> p.c.add(d.scale(u)).add(0, Math.sin(u * Math.PI) * 3.5, 0).add(sd.scale(Math.sin(u * Math.PI) * side0));
        double k = age / fly;
        Vec3 at = path.apply(k), nx = path.apply(Math.min(1, k + 0.03));
        Vec3 dir = nx.subtract(at);
        dir = dir.lengthSqr() < 1e-8 ? new Vec3(0, 0, 1) : dir.normalize();
        Vec3 s1 = Math.abs(dir.y) > 0.95 ? new Vec3(1, 0, 0) : UP.cross(dir).normalize(), s2 = dir.cross(s1).normalize();
        box(vc, m, n, cam, at, s1, s2, dir, 0.07, 0.07, 0.2, STEEL, STEEL, 1);
        box(vc, m, n, cam, at.add(dir.scale(0.22)), s1, s2, dir, 0.05, 0.05, 0.04, HAZARD, HAZARD, 1);
        GravityFxClient.gseg(m, cam, at.subtract(dir.scale(0.2)), at.subtract(dir.scale(0.6)), 0.08, 1, 0, FLAME_W);
        int T = 10;   // its smoke trail
        Vec3[] pts = new Vec3[T];
        double[] w = new double[T];
        float[] al = new float[T];
        for (int i = 0; i < T; i++) {
            double u = Math.max(0, k - i * 0.025);
            pts[i] = path.apply(u);
            w[i] = 0.05 + i * 0.02;
            al[i] = 0.5F * (1 - i / (float) T);
        }
        GravityFxClient.gstrip(m, cam, pts, w, al, SMOKE);
    }

    // ================================================================== 6 overclock
    private static void overclock(Matrix4f m, Vec3 cam, Minecraft mc, FxPacket p, double age, double now, float pt) {
        Entity e = mc.level.getEntity((int) p.v.x);
        if (e == null) return;
        Vec3 f = pos(e, pt);
        float a = (float) (Mth.clamp(age / 4, 0, 1) * Mth.clamp((p.life - age) / 10, 0, 1));
        boolean self = e == mc.player && mc.options.getCameraType().isFirstPerson();
        for (int g = 0; g < 2; g++) {   // two gear rings spinning opposite ways round you
            double y = e.getBbHeight() * (g == 0 ? 0.35 : 0.75), R = 0.75 + g * 0.1, spin = now * 0.25 * (g == 0 ? 1 : -1);
            Vec3 c = f.add(0, y, 0);
            GravityFxClient.ring(m, cam, c, UP, R, 0.04, VISOR, 0.7F * a);
            for (int t = 0; t < 12; t++) {
                double an = spin + t * TAU / 12;
                Vec3 q = c.add(Math.cos(an) * R, 0, Math.sin(an) * R);
                GravityFxClient.gseg(m, cam, q, q.add(Math.cos(an) * 0.14, 0, Math.sin(an) * 0.14), 0.05, 0.8F * a, 0.8F * a, VISOR);
            }
        }
        if (self) return;
        for (int i = 0; i < 6; i++) {   // heat shimmering up off you
            double ph = ((now * 0.06 + FxBase.h2(p.id, i, 5))) % 1.0, an = FxBase.h2(p.id, i, 6) * TAU;
            Vec3 q = f.add(Math.cos(an) * 0.35, e.getBbHeight() * (0.5 + ph * 0.8), Math.sin(an) * 0.35);
            GravityFxClient.gseg(m, cam, q, q.add(0, 0.35, 0), 0.04, (float) Math.sin(ph * Math.PI) * 0.6F * a, 0, VISOR);
        }
    }

    // ================================================================== 7 rail cannon
    private static void rail(Matrix4f m, Vec3 cam, Minecraft mc, FxPacket p, double age, double now, float pt) {
        double charge = p.sweep;
        if (charge > 0 && age < charge) {   // the charge gathering at the hand: rings of blue tightening
            Entity e = mc.level.getEntity((int) p.v.x);
            Vec3 hand = p.c;
            if (e != null) {
                double yaw = Math.toRadians(Mth.rotLerp(pt, e.yRotO, e.getYRot())), pitch = Math.toRadians(Mth.lerp(pt, e.xRotO, e.getXRot()));
                Vec3 lk = new Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
                Vec3 eye = pos(e, pt).add(0, e.getEyeHeight(), 0);
                hand = eye.add(lk.scale(0.9)).add(new Vec3(Math.cos(yaw), 0, Math.sin(yaw)).scale(0.3)).add(0, -0.3, 0);
                double k = age / charge;
                for (int i = 0; i < 3; i++) GravityFxClient.ring(m, cam, hand.add(lk.scale(i * 0.25)), lk, 0.5 * (1 - k) + 0.1 + i * 0.05, 0.04, FLAME, (float) k);
                GravityFxClient.gglow(m, cam, hand, 0.15 + 0.25 * k, (float) k, FLAME_W);
            }
            return;
        }
        if (charge > 0) return;   // the shot itself comes in its own packet
        float a = (float) (1 - age / p.life);
        Vec3 d = p.u.subtract(p.c), dir = d.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : d.normalize();
        GravityFxClient.gseg(m, cam, p.c, p.u, 0.35 * a + 0.05, 0.6F * a, 0.6F * a, FLAME);
        GravityFxClient.gseg(m, cam, p.c, p.u, 0.08, a, a, FLAME_W);
        double L = d.length();
        for (int i = 0; i < (int) (L / 2); i++) {   // rings the slug leaves along its line
            Vec3 q = p.c.add(dir.scale(i * 2 + 1));
            GravityFxClient.ring(m, cam, q, dir, 0.3 + age * 0.06, 0.05, FLAME, 0.7F * a);
        }
    }

    // ================================================================== 8 the machine round its pilot
    private static void pilot(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Minecraft mc, FxPacket p, double age, double now, float pt) {
        Entity e = mc.level.getEntity((int) p.v.x);
        if (e == null) return;
        int pm = e == mc.player ? mask : (int) p.a0;
        Vec3 f = pos(e, pt);
        float a = (float) (Mth.clamp(age / 6, 0, 1) * Mth.clamp((p.life - age) / 6, 0, 1));
        if (p.life <= 44) a = 1;   // the refresh packets keep it standing without blinking
        GravityFxClient.ring(m, cam, f.add(0, 0.05, 0), UP, 1.0, 0.1, HAZARD, 0.5F * a);
        if (e == mc.player && mc.options.getCameraType().isFirstPerson()) return;
        if (!(e instanceof net.minecraft.world.entity.LivingEntity le)) return;
        double yaw = Math.toRadians(Mth.rotLerp(pt, le.yBodyRotO, le.yBodyRot));
        Vec3 fwd = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw)), right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
        double walk = Math.sin(now * 0.4) * Math.min(1, e.getDeltaMovement().horizontalDistance() * 6);
        double H = e.getBbHeight();
        boolean plating = (pm & 1 << 4) != 0;
        // torso and head (the head is always there - it's where you sit)
        if (plating) {
            box(vc, m, n, cam, f.add(0, H * 0.62, 0), right, UP, fwd, 0.5, 0.42, 0.32, METAL, STEEL, a);
            box(vc, m, n, cam, f.add(0, H * 0.62 + 0.42, 0).add(fwd.scale(0.02)), right, UP, fwd, 0.52, 0.03, 0.34, HAZARD, HAZARD, a);
        }
        Vec3 head = f.add(0, H + 0.12, 0);
        box(vc, m, n, cam, head, right, UP, fwd, 0.3, 0.25, 0.3, METAL_D, METAL, a);
        box(vc, m, n, cam, head.add(fwd.scale(0.31)).add(0, 0.03, 0), right, UP, fwd, 0.22, 0.04, 0.01, VISOR, VISOR, a);
        GravityFxClient.gglow(m, cam, head.add(fwd.scale(0.35)).add(0, 0.03, 0), 0.25, 0.6F * a, VISOR);
        if ((pm & 1 << 6) != 0) {   // core: a glowing heart in the chest
            Vec3 core = f.add(0, H * 0.66, 0).add(fwd.scale(plating ? 0.33 : 0.2));
            GravityFxClient.gglow(m, cam, core, 0.2 + 0.04 * Math.sin(now * 0.5), a, VISOR);
            GravityFxClient.gglow(m, cam, core, 0.5, 0.3F * a, VISOR);
        }
        if ((pm & 1) != 0) for (int s = -1; s <= 1; s += 2) {   // arms (and fists on them)
            double swing = -walk * s * 0.4;
            Vec3 sh = f.add(0, H * 0.82, 0).add(right.scale(0.68 * s));
            Vec3 dn = UP.scale(-Math.cos(swing)).add(fwd.scale(Math.sin(swing))).normalize();
            Vec3 ax = right.cross(dn).normalize();
            box(vc, m, n, cam, sh.add(dn.scale(0.4)), right, dn, ax, 0.17, 0.45, 0.17, METAL, METAL, a);
            box(vc, m, n, cam, sh, right, UP, fwd, 0.24, 0.16, 0.24, METAL_D, HAZARD, a);   // pauldron
            if ((pm & 1 << 2) != 0) box(vc, m, n, cam, sh.add(dn.scale(0.98)), right, dn, ax, 0.22, 0.17, 0.22, STEEL, STEEL, a);
        }
        if ((pm & 1 << 3) != 0) for (int s = -1; s <= 1; s += 2) {   // legs
            double swing = walk * s * 0.45;
            Vec3 hip = f.add(0, H * 0.4, 0).add(right.scale(0.22 * s));
            Vec3 dn = UP.scale(-Math.cos(swing)).add(fwd.scale(Math.sin(swing))).normalize();
            box(vc, m, n, cam, hip.add(dn.scale(H * 0.22)), right, dn, right.cross(dn).normalize(), 0.18, H * 0.22, 0.2, METAL, METAL, a);
            box(vc, m, n, cam, hip.add(dn.scale(H * 0.42)).add(fwd.scale(0.08)), right, UP, fwd, 0.2, 0.06, 0.3, METAL_D, HAZARD, a);
        }
        if ((pm & 1 << 1) != 0) for (int s = -1; s <= 1; s += 2) {   // thrusters on the back, idling
            Vec3 t = f.add(0, H * 0.72, 0).add(right.scale(0.24 * s)).subtract(fwd.scale(0.42));
            box(vc, m, n, cam, t, right, UP, fwd, 0.1, 0.22, 0.1, METAL_D, METAL, a);
            GravityFxClient.gseg(m, cam, t.add(0, -0.22, 0), t.add(0, -0.55 - 0.08 * Math.sin(now + s), 0), 0.08, 0.8F * a, 0, FLAME);
        }
        if ((pm & 1 << 5) != 0) {   // a missile pod on the left shoulder
            Vec3 pod = f.add(0, H * 0.82 + 0.35, 0).add(right.scale(-0.55)).subtract(fwd.scale(0.1));
            box(vc, m, n, cam, pod, right, UP, fwd, 0.2, 0.15, 0.22, METAL_D, HAZARD, a);
            for (int i = 0; i < 4; i++) GravityFxClient.gglow(m, cam, pod.add(fwd.scale(0.23)).add(right.scale((i % 2 - 0.5) * 0.2)).add(0, (i / 2 - 0.5) * 0.14, 0), 0.04, a, VISOR);
        }
        if ((pm & 1 << 7) != 0) {   // a cannon barrel over the right shoulder
            Vec3 b = f.add(0, H * 0.82 + 0.38, 0).add(right.scale(0.55)).add(fwd.scale(0.2));
            box(vc, m, n, cam, b, right, UP, fwd, 0.09, 0.09, 0.55, STEEL, STEEL, a);
            box(vc, m, n, cam, b.subtract(fwd.scale(0.45)), right, UP, fwd, 0.16, 0.14, 0.18, METAL_D, HAZARD, a);
            GravityFxClient.gglow(m, cam, b.add(fwd.scale(0.57)), 0.07, a, FLAME);
        }
    }

    // ================================================================== 9 the titan fist
    private static void titan(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age, double now) {
        double fall = Math.max(1, p.sweep), R = p.r;
        float a = (float) Mth.clamp((p.life - age) / 8, 0, 1);
        double s = R * 0.42;
        if (age < fall + 4) {
            double k = Mth.clamp(age / fall, 0, 1);
            double y = 26 * (1 - k * k) + s;
            Vec3 c = p.c.add(0, y, 0);
            Vec3 x = new Vec3(1, 0, 0), z = new Vec3(0, 0, 1);
            GravityFxClient.ring(m, cam, p.c.add(0, 0.06, 0), UP, R * (0.4 + 0.6 * k), 0.15, HAZARD, 0.8F);   // the target
            for (int i = 0; i < 4; i++) {   // and its hazard stripes
                double an = i * TAU / 4 + now * 0.05;
                GravityFxClient.gseg(m, cam, p.c.add(Math.cos(an) * R * 0.3, 0.06, Math.sin(an) * R * 0.3), p.c.add(Math.cos(an) * R, 0.06, Math.sin(an) * R), 0.1, 0.8F, 0.4F, HAZARD);
            }
            box(vc, m, n, cam, c, x, UP, z, s, s * 0.8, s, METAL, STEEL, 1);   // the fist, knuckles down
            box(vc, m, n, cam, c.add(0, -s * 0.82, 0), x, UP, z, s * 0.95, 0.04 * R, s * 0.95, HAZARD, HAZARD, 1);
            box(vc, m, n, cam, c.add(0, s * 1.6, 0), x, UP, z, s * 0.65, s * 0.8, s * 0.65, METAL_D, METAL, 1);   // the forearm going up out of sight
            box(vc, m, n, cam, c.add(0, s * 3.6, 0), x, UP, z, s * 0.6, s * 1.2, s * 0.6, METAL, METAL, 1);
            for (int i = 0; i < 4; i++) {   // thrusters on the forearm pushing it down
                double an = i * TAU / 4 + 0.785;
                Vec3 t = c.add(Math.cos(an) * s * 0.7, s * 2.4, Math.sin(an) * s * 0.7);
                GravityFxClient.gseg(m, cam, t, t.add(0, 1.6 + 0.3 * Math.sin(now * 2 + i), 0), 0.25, 1, 0, FLAME);
            }
        }
        if (age >= fall) {
            double q = (age - fall) / (p.life - fall);
            GravityFxClient.ring(m, cam, p.c.add(0, 0.1, 0), UP, R * (0.5 + 1.2 * q), 0.6, SPARK, (float) (1 - q));
            GravityFxClient.ring(m, cam, p.c.add(0, 0.1, 0), UP, R * (0.3 + 0.9 * q), 0.4, SMOKE, (float) (1 - q) * 0.7F);
            GravityFxClient.gglow(m, cam, p.c.add(0, 1, 0), R * 1.2 * (1 - q), (float) (1 - q) * 0.6F, SPARK);
            for (int i = 0; i < 16; i++) {
                Vec3 sd = new Vec3(FxBase.h2(p.id, i, 7) - 0.5, FxBase.h2(p.id, i, 8) * 0.9 + 0.2, FxBase.h2(p.id, i, 9) - 0.5).normalize();
                Vec3 sp = p.c.add(sd.scale(R * 0.9 * q)).add(0, -1.5 * q * q, 0);
                GravityFxClient.gseg(m, cam, sp, sp.subtract(sd.scale(0.4)), 0.05, a * (float) (1 - q), 0, SPARK);
            }
        }
    }

    // ================================================================== 10 a part installed
    private static void installed(Matrix4f m, Vec3 cam, Minecraft mc, FxPacket p, double age, float pt) {
        Entity e = mc.level.getEntity((int) p.v.x);
        if (e == null) return;
        Vec3 f = pos(e, pt);
        double k = Mth.clamp(age / p.life, 0, 1);
        float a = (float) Math.sin(k * Math.PI);
        double y = e.getBbHeight() * (0.2 + 0.7 * ((int) p.a0 % 4) / 3.0);
        double R = 1.4 * (1 - FxBase.smooth(k)) + 0.45;
        Vec3 c = f.add(0, y, 0);
        for (int i = 0; i < 4; i++) {   // a square of blueprint lines closing in to bolt on
            double an = i * TAU / 4 + 0.785 + k;
            Vec3 a0 = c.add(Math.cos(an) * R, 0, Math.sin(an) * R), a1 = c.add(Math.cos(an + TAU / 4) * R, 0, Math.sin(an + TAU / 4) * R);
            GravityFxClient.gseg(m, cam, a0, a1, 0.03, a, a, BLUEPRINT);
        }
        if (k > 0.75) GravityFxClient.gglow(m, cam, c, 0.5, (float) (1 - k) * 3, SPARK);
    }

    // ================================================================== 11 sparks and a smoke ring
    private static void burst(Matrix4f m, Vec3 cam, FxPacket p, double age) {
        double q = Mth.clamp(age / p.life, 0, 1);
        GravityFxClient.gglow(m, cam, p.c, p.r * (0.6 + 0.6 * q), (float) (1 - q) * 0.8F, SPARK);
        GravityFxClient.ring(m, cam, p.c, UP, p.r * (0.4 + q), 0.15, SMOKE, (float) (1 - q) * 0.6F);
        for (int i = 0; i < 10; i++) {
            Vec3 sd = new Vec3(FxBase.h2(p.id, i, 10) - 0.5, FxBase.h2(p.id, i, 11) - 0.3, FxBase.h2(p.id, i, 12) - 0.5).normalize();
            Vec3 s = p.c.add(sd.scale(p.r * q)).add(0, -0.6 * q * q, 0);
            GravityFxClient.gseg(m, cam, s, s.subtract(sd.scale(0.25)), 0.03, (float) (1 - q), 0, SPARK);
        }
    }
}
