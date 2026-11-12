package com.ironspellgrimoire.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * The trappings of the long "mode" spells, drawn here every frame so they stay on the player however fast they move
 * (they used to be re-sent from the server every few ticks at a fixed spot, and trailed behind in steps):
 * 0 Beast Instinct - spirit ears, a swaying tail, glowing eyes; 1 Nean Armor - fluffs of fleece circling, curled horns;
 * 2 Salamander Spirit - two ribbons of flame and the little spirit looping round; 3 Mane Blaze - a mane of flame
 * streaming back; 4 World Tree Mantle - leaves drifting round and a vine winding up.
 * Fed by FxPacket shape 64: id = player, life, sweep = style, palette.
 */
final class ModeAuraFx {
    private ModeAuraFx() {}

    private static final double TAU = Math.PI * 2;
    private static final List<double[]> ACTIVE = new ArrayList<>();   // {id, style, palette, start, end}

    static void set(int id, int style, int pal, int life) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        double now = mc.level.getGameTime();
        ACTIVE.removeIf(a -> (int) a[0] == id && (int) a[1] == style);
        if (life > 0) ACTIVE.add(new double[]{id, style, pal, now, now + life});
    }

    static void clear() { ACTIVE.clear(); }

    static void draw(Matrix4f m, Vec3 cam, double now, float pt) {
        if (ACTIVE.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        ACTIVE.removeIf(a -> now > a[4] + 8);
        for (double[] a : ACTIVE) {
            Entity en = mc.level.getEntity((int) a[0]);
            if (en == null || en.isInvisible()) continue;
            boolean self = en == mc.player && mc.options.getCameraType().isFirstPerson();
            float k = (float) (Mth.clamp((now - a[3]) / 8.0, 0, 1) * Mth.clamp((a[4] + 8 - now) / 8.0, 0, 1));
            if (k <= 0.01F) continue;
            Vec3 c = new Vec3(Mth.lerp(pt, en.xo, en.getX()), Mth.lerp(pt, en.yo, en.getY()), Mth.lerp(pt, en.zo, en.getZ()));
            float yaw = en instanceof LivingEntity le ? Mth.rotLerp(pt, le.yBodyRotO, le.yBodyRot) : en.getYRot();
            double yr = Math.toRadians(yaw);
            Vec3 face = new Vec3(-Math.sin(yr), 0, Math.cos(yr)), rt = new Vec3(-face.z, 0, face.x);
            double h = en.getBbHeight();
            int pal = (int) a[2];
            switch ((int) a[1]) {
                case 0 -> beast(m, cam, c, face, rt, h, now, pal, k, self);
                case 1 -> nean(m, cam, c, face, rt, h, now, pal, k, self);
                case 2 -> salamander(m, cam, c, h, now, pal, k);
                case 3 -> mane(m, cam, c, face, rt, h, now, pal, k, self);
                case 5 -> demonLight(m, cam, c, face, rt, h, now, k, self);
                case 6 -> scarletMoon(m, cam, c, face, rt, h, now, k, self);
                default -> mantle(m, cam, c, h, now, pal, k);
            }
        }
    }

    private static void beast(Matrix4f m, Vec3 cam, Vec3 c, Vec3 face, Vec3 rt, double h, double now, int pal, float k, boolean self) {
        if (!self) {
            for (int s = -1; s <= 1; s += 2) {   // pointed ears that twitch
                double tw = Math.sin(now * 0.25 + s) * 0.04;
                Vec3 base = c.add(0, h - 0.08, 0).add(rt.scale(s * 0.2));
                Vec3 tip = base.add(rt.scale(s * (0.1 + tw))).add(0, 0.5, 0);
                FxBase.gline(m, cam, base.add(rt.scale(-s * 0.1)), tip, 0.07F, pal, 0.9F * k);
                FxBase.gline(m, cam, base.add(rt.scale(s * 0.12)), tip, 0.07F, pal, 0.9F * k);
                Vec3 eye = c.add(0, h - 0.33, 0).add(face.scale(0.27)).add(rt.scale(s * 0.12));
                FxBase.gline(m, cam, eye.add(rt.scale(-0.05)), eye.add(rt.scale(0.05)), 0.05F, 8, k);
            }
        }
        Vec3 p = c.add(0, h * 0.5, 0).subtract(face.scale(0.25));   // the tail: a long curve swaying from side to side
        for (int i = 1; i <= 7; i++) {
            double t = i / 7.0;
            Vec3 q = c.add(0, h * 0.5 + t * t * 0.7, 0).subtract(face.scale(0.25 + t * 0.95)).add(rt.scale(Math.sin(now * 0.3 - t * 2.5) * 0.4 * t));
            FxBase.gline(m, cam, p, q, 0.14F * (float) (1.0 - t * 0.6), pal, 0.85F * k);
            p = q;
        }
    }

    private static void nean(Matrix4f m, Vec3 cam, Vec3 c, Vec3 face, Vec3 rt, double h, double now, int pal, float k, boolean self) {
        for (int i = 0; i < 4; i++) {   // fluffs of fleece bobbing round the body
            double a = now * 0.1 + i * Math.PI / 2;
            Vec3 f = c.add(Math.cos(a) * 0.8, 0.6 + 0.5 * Math.sin(now * 0.12 + i), Math.sin(a) * 0.8);
            for (int j = 0; j < 4; j++) {
                double b = j * Math.PI / 4 + i;
                Vec3 d = new Vec3(Math.cos(b) * 0.16, Math.sin(b * 1.7) * 0.12, Math.sin(b) * 0.16);
                FxBase.gline(m, cam, f.subtract(d), f.add(d), 0.16F, pal, 0.8F * k);
            }
        }
        if (self) return;
        for (int s = -1; s <= 1; s += 2) {   // a pair of curled horns
            Vec3 p = c.add(0, h + 0.02, 0).add(rt.scale(s * 0.2));
            for (int i = 1; i <= 7; i++) {
                double t = i / 7.0, ang = t * 4.2;
                Vec3 q = c.add(0, h + 0.02 + Math.sin(ang) * 0.2, 0).add(rt.scale(s * (0.2 + (1 - Math.cos(ang)) * 0.15))).subtract(face.scale(t * 0.12));
                FxBase.gline(m, cam, p, q, 0.07F * (float) (1.0 - t * 0.5), 8, 0.95F * k);
                p = q;
            }
        }
    }

    private static void salamander(Matrix4f m, Vec3 cam, Vec3 c, double h, double now, int pal, float k) {
        for (int s = 0; s < 2; s++) {   // two ribbons of flame winding up round the body
            Vec3 p = null;
            for (int i = 0; i <= 12; i++) {
                double t = i / 12.0, a = now * 0.3 + s * Math.PI + t * 3.2;
                Vec3 q = c.add(Math.cos(a) * 0.6, 0.15 + t * h, Math.sin(a) * 0.6);
                if (p != null) FxBase.gline(m, cam, p, q, 0.07F * (float) Math.sin(t * Math.PI + 0.2), pal, 0.8F * k);
                p = q;
            }
        }
        Vec3 p = null;   // the little spirit: a bright head with a tail, looping round
        for (int i = 0; i <= 8; i++) {
            double a = now * 0.45 - i * 0.14;
            Vec3 q = c.add(Math.cos(a) * 1.1, h * 0.55 + Math.sin(a * 1.4) * 0.5, Math.sin(a) * 1.1);
            if (p != null) FxBase.gline(m, cam, p, q, 0.2F * (1F - i / 9F), i < 2 ? 8 : pal, k * (1F - i / 9F));
            p = q;
        }
    }

    private static void mane(Matrix4f m, Vec3 cam, Vec3 c, Vec3 face, Vec3 rt, double h, double now, int pal, float k, boolean self) {
        if (self) return;
        for (int i = 0; i < 9; i++) {   // strands of flame streaming back from the head and shoulders, flickering
            double a = (i / 8.0 - 0.5) * 2.6;
            Vec3 p = c.add(0, h - 0.18, 0).add(rt.scale(Math.sin(a) * 0.3)).subtract(face.scale(0.12 + Math.cos(a) * 0.1));
            double len = 0.75 + 0.25 * Math.sin(now * 0.4 + i * 1.3);
            for (int j = 1; j <= 5; j++) {
                double t = j / 5.0;
                Vec3 q = c.add(0, h - 0.18 + t * 0.45 + Math.sin(now * 0.5 + i + t * 3) * 0.06, 0)
                        .add(rt.scale(Math.sin(a) * (0.3 + t * 0.35) + Math.sin(now * 0.35 + i * 2 + t * 4) * 0.08 * t))
                        .subtract(face.scale(0.12 + Math.cos(a) * 0.1 + t * len));
                FxBase.gline(m, cam, p, q, 0.12F * (float) (1.0 - t * 0.75), j < 2 ? 8 : pal, 0.85F * k * (float) (1.0 - t * 0.5));
                p = q;
            }
        }
    }

    private static void mantle(Matrix4f m, Vec3 cam, Vec3 c, double h, double now, int pal, float k) {
        for (int i = 0; i < 4; i++) {   // leaves drifting round: a small pointed outline with a midrib
            double b = now * 0.06 + i * TAU / 4;
            Vec3 q = c.add(Math.cos(b) * 0.95, 0.5 + 0.5 * Math.sin(now * 0.07 + i * 1.7), Math.sin(b) * 0.95);
            Vec3 along = new Vec3(-Math.sin(b), 0.3 * Math.sin(now * 0.1 + i), Math.cos(b)).normalize().scale(0.2);
            Vec3 side = new Vec3(0, 0.07, 0);
            FxBase.gline(m, cam, q.subtract(along), q.add(side), 0.05F, pal, 0.9F * k);
            FxBase.gline(m, cam, q.add(side), q.add(along), 0.05F, pal, 0.9F * k);
            FxBase.gline(m, cam, q.subtract(along), q.subtract(side), 0.05F, pal, 0.9F * k);
            FxBase.gline(m, cam, q.subtract(side), q.add(along), 0.05F, pal, 0.9F * k);
        }
        Vec3 p = null;   // a vine winding up, a bud of light at its tip
        for (int i = 0; i <= 14; i++) {
            double t = i / 14.0, a = now * 0.05 + t * 5.0;
            Vec3 q = c.add(Math.cos(a) * 0.55, 0.1 + t * (h + 0.1), Math.sin(a) * 0.55);
            if (p != null) FxBase.gline(m, cam, p, q, 0.045F, pal, 0.8F * k);
            p = q;
        }
        if (p != null) FxBase.gline(m, cam, p, p.add(0, 0.12, 0), 0.1F, 8, k);
    }

    /**
     * 5 Demon Light (Patolli): a black halo over a white one above the head, six small blades of light circling,
     * and flickers of black light running up the body.
     */
    private static void demonLight(Matrix4f m, Vec3 cam, Vec3 c, Vec3 face, Vec3 rt, double h, double now, float k, boolean self) {
        Vec3 top = c.add(0, h + 0.32, 0).subtract(face.scale(0.08));
        for (int ring = 0; ring < 2; ring++) {
            double r = ring == 0 ? 0.3 : 0.38, tilt = ring == 0 ? 0.0 : 0.06;
            int pal = ring == 0 ? 6 : 31;
            if (self && ring == 0) continue;
            Vec3 prev = null;
            for (int i = 0; i <= 20; i++) {
                double a = i * TAU / 20 + now * (ring == 0 ? 0.05 : -0.04);
                Vec3 q = top.add(rt.scale(Math.cos(a) * r)).add(face.scale(Math.sin(a) * r)).add(0, Math.sin(a) * tilt, 0);
                if (prev != null) FxBase.gline(m, cam, prev, q, ring == 0 ? 0.05F : 0.08F, pal, (ring == 0 ? 0.95F : 0.8F) * k);
                prev = q;
            }
        }
        for (int i = 0; i < 6; i++) {   // the circling blades, point up, rising and falling
            double a = i * TAU / 6 + now * 0.09;
            Vec3 b = c.add(Math.cos(a) * 1.05, h * 0.45 + Math.sin(now * 0.2 + i) * 0.25, Math.sin(a) * 1.05);
            Vec3 tip = b.add(0, 0.55, 0), guard = b.add(0, 0.08, 0), side = new Vec3(-Math.sin(a), 0, Math.cos(a)).scale(0.11);
            FxBase.gline(m, cam, guard, tip, 0.07F, i % 2 == 0 ? 31 : 6, 0.85F * k);
            FxBase.gline(m, cam, guard.add(side), guard.subtract(side), 0.04F, 6, 0.8F * k);
            FxBase.gline(m, cam, b.subtract(0, 0.06, 0), guard, 0.03F, 6, 0.6F * k);
        }
        if (!self) for (int i = 0; i < 4; i++) {   // black light licking up the body
            double ph = (now * 0.12 + i * 0.25) % 1.0, a = i * TAU / 4 + now * 0.03;
            Vec3 b = c.add(Math.cos(a) * 0.36, ph * h, Math.sin(a) * 0.36);
            FxBase.gline(m, cam, b, b.add(0, 0.35, 0), 0.06F, 31, (float) (Math.sin(ph * Math.PI) * 0.7) * k);
        }
    }

    /**
     * Scarlet Moon (Blood Grimoire): a red moon hangs high over your head - a disc of crimson rings round a white-hot
     * core, slowly turning - white flames lick up your body, and drops of blood circle you, rising and falling.
     */
    private static void scarletMoon(Matrix4f m, Vec3 cam, Vec3 c, Vec3 face, Vec3 rt, double h, double now, float k, boolean self) {
        Vec3 moon = c.add(0, h + 1.9 + Math.sin(now * 0.05) * 0.08, 0).subtract(face.scale(0.15));
        Vec3 up = new Vec3(0, 1, 0);
        for (int ring = 0; ring < 6; ring++) {   // the disc, facing out to the side so it reads from any angle
            double r = 0.12 + ring * 0.1;
            int pal = ring <= 1 ? 6 : 0;
            Vec3 prev = null;
            for (int i = 0; i <= 24; i++) {
                double a = i * TAU / 24 + now * 0.02 * (ring % 2 == 0 ? 1 : -1);
                Vec3 q = moon.add(rt.scale(Math.cos(a) * r)).add(up.scale(Math.sin(a) * r));
                if (prev != null) FxBase.gline(m, cam, prev, q, ring <= 1 ? 0.1F : 0.075F, pal, (ring == 5 ? 0.55F : 0.9F) * k);
                prev = q;
            }
        }
        Vec3 prev = null;
        for (int i = 0; i <= 32; i++) {   // a thin halo ring round the moon
            double a = i * TAU / 32;
            Vec3 q = moon.add(rt.scale(Math.cos(a) * 0.82)).add(up.scale(Math.sin(a) * 0.82));
            if (prev != null) FxBase.gline(m, cam, prev, q, 0.03F, 6, 0.5F * k);
            prev = q;
        }
        if (!self) for (int i = 0; i < 6; i++) {   // white flames licking up the body
            double ph = (now * 0.1 + i / 6.0) % 1.0, a = i * TAU / 6 + now * 0.04;
            Vec3 b = c.add(Math.cos(a) * 0.38, ph * h * 1.1, Math.sin(a) * 0.38);
            FxBase.gline(m, cam, b, b.add(0, 0.42, 0), 0.07F, 6, (float) (Math.sin(ph * Math.PI) * 0.8) * k);
        }
        for (int i = 0; i < 8; i++) {   // drops of blood circling, rising and falling
            double a = i * TAU / 8 + now * 0.07;
            Vec3 b = c.add(Math.cos(a) * 1.1, h * 0.5 + Math.sin(now * 0.15 + i * 0.8) * 0.45, Math.sin(a) * 1.1);
            FxBase.gline(m, cam, b, b.add(0, -0.14, 0), 0.07F, 0, 0.9F * k);
        }
    }
}
