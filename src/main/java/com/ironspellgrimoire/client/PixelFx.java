package com.ironspellgrimoire.client;

import com.ironspellgrimoire.network.FxPacket;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A second effect style, built from pixel-art sprites instead of solid shapes: hundreds of flat 2D sprites - long
 * flame streaks, diamond shards, square embers - each turned toward the camera but stretched along its own direction
 * of travel, laid out along a 3D arc. Up close every piece is a flat pixel sprite; together they read as a solid
 * three-dimensional slash, like an anime / pixel-art fire effect.
 *
 * First use (a test): Asta's Sky Splitter with the Demon Slayer - a towering crescent of black anti magic edged with
 * white-hot and ember-orange flame streaks, shedding sparks as it flies.
 *
 * FxPacket shape 41: id (so a kill packet can end it), c = start, d = direction of travel, u = the arc's "up",
 * r -> r2 = radius growing over the flight, speed = blocks per tick, life = ticks, palette = colour scheme.
 */
public final class PixelFx {
    private PixelFx() {}

    /** The pixel-art sprites (grey-scale, tinted per palette). Long ones point up: the top row leads. */
    static final int K_STREAK = 0, K_SHARD = 1, K_EMBER = 2, K_TONGUE = 3, K_BLADE = 4, K_CROSS = 5, K_BOLT = 6, K_FEATHER = 7,
            K_STAR = 8, K_PLATE = 9, K_RING = 10, K_BARB = 11, K_PUFF = 12, K_MOON = 13, K_DROP = 14, K_WISP = 15, K_DIAMOND = 16,
            K_CLAW = 17, K_BONE = 18, K_GLOW = 19, K_SHOCK = 20, K_COTTON = 21, K_TUFT = 22, K_FIBER = 23;
    private static final ResourceLocation[] TEX = {
            tex("streak"), tex("shard"), tex("ember"), tex("tongue"), tex("blade"), tex("cross"), tex("bolt"), tex("feather"),
            tex("star"), tex("plate"), tex("ring"), tex("barb"), tex("puff"), tex("moon"), tex("drop"), tex("wisp"), tex("diamond"),
            tex("claw"), tex("bone"), tex("glow"), tex("shock"), tex("cotton"), tex("tuft"), tex("fiber")};
    private static final ResourceLocation[] TEX_CORE = suffixed("_core"), TEX_RIM = suffixed("_rim");

    private static ResourceLocation[] suffixed(String sfx) {
        ResourceLocation[] out = new ResourceLocation[TEX.length];
        for (int i = 0; i < TEX.length; i++) out[i] = ResourceLocation.fromNamespaceAndPath(TEX[i].getNamespace(), TEX[i].getPath().replace(".png", sfx + ".png"));
        return out;
    }

    private static ResourceLocation tex(String n) { return ResourceLocation.fromNamespaceAndPath("iron_spell_grimoire", "textures/effect/pixel/" + n + ".png"); }

    /** A moving pixel slash emitter. */
    private static final class Slash {
        int id; Vec3 c, d, u, side; double r0, r1, speed; int life, pal; double born; boolean dead;
    }

    /** One sprite. kind: 0 streak, 1 shard, 2 ember, 3 tongue. dark = drawn black (anti magic), else glowing. */
    private static final class Spr {
        boolean see;   // part of a barrier seen from inside: drawn faint
        float tone = 1.0F;   // brightness of a solid sprite (mercury's chrome shading)
        float alpha = 1.0F;  // extra see-through-ness (Equinox's invisible blade)
        Vec3 pos, prev, vel, axis; float len, wid; int life, age, kind; boolean dark; float heat; float drag;
        int pal = 2; Vec3 curl; float curlRate; float darkAlpha = 0.92F; float grav;
        float spin; float grow = 1.0F; float pop; boolean twinkle, flicker; int seed = ThreadLocalRandom.current().nextInt(64);

        /** Size multiplier now: shock rings grow, popped sprites burst in from small. */
        float scaleNow(float pt) {
            float t = age + pt;
            float s = grow == 1.0F ? 1.0F : (float) Math.pow(grow, t);
            if (pop > 0) { float k = Mth.clamp(t / pop, 0.0F, 1.0F); s *= 0.35F + 0.65F * k * k * (3.0F - 2.0F * k); }   // smoothstep
            return s;
        }
    }

    private static final List<Slash> SLASHES = new ArrayList<>();
    private static final List<Spr> SPRITES = new ArrayList<>();
    /** A hard cap that keeps big fights smooth - set by the effect quality. */
    private static int max() { return com.ironspellgrimoire.AddonClientConfig.spriteCap(); }
    private static int sigsThisTick = 0;
    /** True while a slash (a move's main blade) emits: it may go past the cap, so the blade is never dropped. */
    private static boolean priority = false;

    public static void spawn(FxPacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Slash s = new Slash();
        s.id = p.id; s.c = p.c; s.d = p.d.normalize(); s.u = p.u.normalize(); s.side = s.d.cross(s.u).normalize();
        s.r0 = p.r; s.r1 = p.r2 > 0 ? p.r2 : p.r; s.speed = p.speed; s.life = Math.max(1, p.life); s.pal = p.palette;
        s.born = mc.level.getGameTime();
        SLASHES.add(s);
        int first = SPRITES.size();
        priority = true;
        try { emit(s, 0, true); } finally { priority = false; }   // the first frame is already a full blade
        if (s.pal == 0) signature(0, s.c, s.d, s.r1);   // anti magic's star of blade slivers as it's released
        if (s.pal == 0) redOnly(first);
    }

    public static void kill(int id) {
        for (Slash s : SLASHES) if (s.id == id) s.dead = true;
    }

    // ------------------------------------------------------------------ colours (by heat: 1 = white-hot ... 0 = dying)
    /** Each palette's glow colour - the same numbering as FxRenderer (0 anti magic ... 15 bone). */
    private static final float[][] RIM = {
            {1.0F, 0.16F, 0.1F}, {0.67F, 0.24F, 1.0F}, {1.0F, 0.6F, 0.12F}, {0.6F, 0.88F, 1.0F}, {1.0F, 0.96F, 0.44F},
            {0.6F, 1.0F, 0.78F}, {0.95F, 0.95F, 1.0F}, {0.7F, 0.82F, 0.95F}, {1.0F, 0.8F, 0.3F}, {1.0F, 0.07F, 0.05F},
            {0.6F, 0.2F, 1.0F}, {0.86F, 0.92F, 1.0F}, {0.35F, 0.65F, 1.0F}, {0.45F, 0.72F, 1.0F}, {1.0F, 0.16F, 0.1F},
            {1.0F, 0.98F, 0.93F}, {1.0F, 0.55F, 0.78F}, {0.62F, 0.45F, 1.0F}, {1.0F, 0.1F, 0.06F},
            {0.3F, 0.7F, 1.0F}, {0.85F, 0.88F, 1.0F}, {0.9F, 0.25F, 0.85F},
            {1.0F, 0.38F, 0.1F}, {1.0F, 0.72F, 0.18F}, {0.45F, 0.95F, 0.38F}, {0.36F, 1.0F, 0.48F}, {0.86F, 0.64F, 0.36F},
            {0.78F, 0.38F, 1.0F}, {1.0F, 0.97F, 0.99F},
            {0.3F, 0.88F, 1.0F},
            {0.66F, 0.46F, 0.24F},
            {0.55F, 0.55F, 0.62F}
    };
    /** The colour of the solid (non-glowing) sprites: black anti magic and darkness, steel, gold, mercury, bone. */
    private static final float[][] SOLID = {
            {0.02F, 0.0F, 0.01F}, {0.03F, 0.0F, 0.05F}, {0.08F, 0.03F, 0.02F}, {0.05F, 0.1F, 0.16F}, {0.1F, 0.09F, 0.02F},
            {0.05F, 0.12F, 0.08F}, {0.5F, 0.5F, 0.55F}, {0.32F, 0.36F, 0.42F}, {0.55F, 0.38F, 0.08F}, {0.03F, 0.0F, 0.0F},
            {0.015F, 0.0F, 0.035F}, {0.62F, 0.67F, 0.75F}, {0.02F, 0.05F, 0.2F}, {0.02F, 0.06F, 0.18F}, {0.12F, 0.0F, 0.01F},
            {0.95F, 0.93F, 0.88F}, {0.4F, 0.12F, 0.26F}, {0.04F, 0.02F, 0.12F}, {0.2F, 0.0F, 0.01F},
            {0.02F, 0.08F, 0.22F}, {0.55F, 0.58F, 0.7F}, {0.1F, 0.0F, 0.12F},
            {0.12F, 0.02F, 0.01F}, {0.2F, 0.12F, 0.02F}, {0.05F, 0.14F, 0.05F}, {0.0F, 0.08F, 0.02F}, {0.34F, 0.24F, 0.14F},
            {0.12F, 0.04F, 0.2F}, {0.97F, 0.97F, 1.0F},
            {0.02F, 0.1F, 0.2F},
            {0.2F, 0.11F, 0.05F},
            {0.015F, 0.015F, 0.02F}
    };

    static final int DARK = 0, FLAME = 1, BOLT = 2, WIND = 3, METAL = 4, BONE = 5, LIGHT = 6;

    /** Which kind of magic a palette is - it decides what its sprites do. */
    static int element(int pal) {
        return switch (pal) {
            case 0, 1, 9, 10, 31 -> DARK;
            case 2, 12, 14, 22, 23 -> FLAME;
            case 24 -> WIND;
            case 26 -> METAL;
            case 4, 13 -> BOLT;
            case 5 -> WIND;
            case 7, 8, 11 -> METAL;
            case 15 -> BONE;
            default -> LIGHT;
        };
    }

    /** Glow colour of a palette at a given heat: white-hot at the top, the palette's own colour, then dying embers -
     *  blended smoothly between those stops, so a sprite cools without visible colour jumps. */
    private static float[] glow(int pal, float heat) {
        pal = Mth.clamp(pal, 0, RIM.length - 1);
        if (pal == 2) return ramp(FIRE_H, FIRE_C, heat);
        if (pal == 0 || pal == 9 || pal == 14 || pal == 18) return ramp(CRIM_H, CRIM_C, heat);
        if (pal == 13) return ramp(GEN_H, BLUE_C, heat);   // Luck: deep blue to electric blue to a pale blue-white, never yellow
        float[] c = RIM[pal];
        float[][] cs = {
                {c[0] * 0.3F, c[1] * 0.3F, c[2] * 0.3F}, {c[0] * 0.6F, c[1] * 0.6F, c[2] * 0.6F}, c,
                {0.55F + 0.45F * c[0], 0.55F + 0.45F * c[1], 0.55F + 0.45F * c[2]}, {0.92F + 0.08F * c[0], 0.92F + 0.08F * c[1], 0.92F + 0.08F * c[2]}};
        return ramp(GEN_H, cs, heat);
    }

    private static final float[] GEN_H = {0.1F, 0.3F, 0.55F, 0.8F, 0.97F};
    private static final float[][] BLUE_C = {{0.02F, 0.06F, 0.28F}, {0.08F, 0.25F, 0.85F}, {0.25F, 0.55F, 1.0F}, {0.55F, 0.8F, 1.0F}, {0.82F, 0.92F, 1.0F}};
    private static final float[] CRIM_H = {0.15F, 0.42F, 0.68F, 0.86F, 0.97F};
    private static final float[][] CRIM_C = {{0.35F, 0.0F, 0.02F}, {0.75F, 0.04F, 0.04F}, {1.0F, 0.22F, 0.16F}, {1.0F, 0.75F, 0.7F}, {1.0F, 0.93F, 0.88F}};
    private static final float[] FIRE_H = {0.1F, 0.3F, 0.5F, 0.7F, 0.9F};
    private static final float[][] FIRE_C = {{0.45F, 0.06F, 0.03F}, {0.9F, 0.22F, 0.05F}, {1.0F, 0.55F, 0.12F}, {1.0F, 0.86F, 0.35F}, {1.0F, 0.97F, 0.85F}};

    private static float[] ramp(float[] hs, float[][] cs, float h) {
        if (h <= hs[0]) return cs[0];
        for (int i = 1; i < hs.length; i++) {
            if (h <= hs[i]) {
                float t = (h - hs[i - 1]) / (hs[i] - hs[i - 1]);
                float[] a = cs[i - 1], b = cs[i];
                return new float[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t};
            }
        }
        return cs[cs.length - 1];
    }

    // ------------------------------------------------------------------ emitting
    private static Vec3 arcPoint(Slash s, double centre, double a, double R) {
        // a crescent: the outer edge curves back at the tips; a = -1..1 along the arc
        double ang = a * Math.toRadians(78);
        return s.c.add(s.d.scale(centre)).add(s.u.scale(Math.sin(ang) * R)).add(s.d.scale((Math.cos(ang) - 1.0) * R * 0.5));
    }

    private static Vec3 arcTangent(Slash s, double a, double R) {
        double ang = a * Math.toRadians(78);
        return s.u.scale(Math.cos(ang)).add(s.d.scale(-Math.sin(ang) * 0.5)).normalize();
    }

    private static Spr spr(Vec3 at, Vec3 vel, Vec3 axis, int kind, float len, float wid, int life) {
        Spr p = new Spr();
        p.pos = at; p.prev = at; p.vel = vel; p.axis = axis.lengthSqr() > 1.0E-8 ? axis.normalize() : new Vec3(0, 1, 0);
        p.kind = kind; p.len = len; p.wid = wid; p.life = life; p.drag = 0.86F;
        return p;
    }

    /**
     * Asta's anti magic as the anime draws it: a thick crescent of pure black, a thin white-hot cutting edge, a
     * blood-red glow bleeding off it, black lightning crackling out from the blade, black smoke curling in its wake,
     * and red embers and black flakes falling away. The moment it's released, a ring of shards bursts outward.
     */
    private static void emit(Slash s, double age, boolean burst) {
        if (s.pal != 0) { emitFlame(s, age, burst); return; }
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double grow = 1.0 - Math.pow(1.0 - Mth.clamp(age / (s.life * 0.5), 0.0, 1.0), 2.0);
        double R = s.r0 + (s.r1 - s.r0) * grow;
        double centre = s.speed * age;
        Vec3 mid = s.c.add(s.d.scale(centre - R * 0.5));
        // the black blade: thick, chunky streaks packed along the arc
        for (int i = 0; i < (burst ? 170 : 100); i++) {
            double a = r.nextDouble(-1, 1), edge = Math.pow(Math.cos(a * Math.PI * 0.5), 1.2);
            Vec3 tan = arcTangent(s, a, R);
            double depth = R * (0.03 + r.nextDouble() * 0.3) * edge;
            Vec3 at = arcPoint(s, centre, a, R).subtract(s.d.scale(depth)).add(s.side.scale(r.nextGaussian() * 0.04 * R * edge));
            Spr p = spr(at, s.d.scale(s.speed * 0.93), tan.subtract(s.d.scale(0.1)), r.nextFloat() < 0.6F ? K_BLADE : (r.nextBoolean() ? K_STREAK : K_TONGUE),
                    (float) (R * r.nextDouble(0.18, 0.34)), (float) (0.14 + 0.2 * edge), r.nextInt(3, 6));
            p.dark = true; p.pal = 0;
            add(p);
        }
        // the blood-red glow bleeding off it (wide, faint, a little behind and to the sides)
        for (int i = 0; i < (burst ? 50 : 32); i++) {
            double a = r.nextDouble(-1, 1), edge = Math.pow(Math.cos(a * Math.PI * 0.5), 1.2);
            Vec3 tan = arcTangent(s, a, R);
            double depth = R * (0.1 + r.nextDouble() * 0.3) * edge;
            Vec3 at = arcPoint(s, centre, a, R).subtract(s.d.scale(depth)).add(s.side.scale((r.nextBoolean() ? 1 : -1) * R * 0.1 * edge));
            Spr p = spr(at, s.d.scale(s.speed * 0.85), tan.subtract(s.d.scale(0.3)), 0,
                    (float) (R * r.nextDouble(0.2, 0.36)), (float) (0.25 + 0.25 * edge), r.nextInt(2, 5));
            p.pal = 0; p.heat = 0.6F;
            add(p);
        }
        // the white-hot cutting edge: thin and crisp, right on the arc
        for (int i = 0; i < (burst ? 40 : 28); i++) {
            double a = r.nextDouble(-1, 1), edge = Math.pow(Math.cos(a * Math.PI * 0.5), 1.2);
            Vec3 tan = arcTangent(s, a, R);
            Vec3 at = arcPoint(s, centre, a, R).add(s.d.scale(r.nextDouble() * R * 0.02));
            Spr p = spr(at, s.d.scale(s.speed * 0.97), tan, 0, (float) (R * r.nextDouble(0.12, 0.2) * (0.5 + 0.5 * edge)),
                    (float) (0.05 + 0.04 * edge), r.nextInt(2, 4));
            p.heat = 1.0F;
            add(p);
        }
        // black lightning crackling out of the blade, each bolt with a red glow
        for (int b = 0; b < (burst ? 6 : 2); b++) {
            double a = r.nextDouble(-0.9, 0.9);
            Vec3 at = arcPoint(s, centre, a, R).subtract(s.d.scale(R * 0.1));
            Vec3 dir = at.subtract(mid).normalize().add(s.side.scale(r.nextGaussian() * 0.6)).normalize();
            int segs = r.nextInt(4, 8);
            for (int k = 0; k < segs; k++) {
                Vec3 next = at.add(dir.scale(R * r.nextDouble(0.08, 0.16))).add(new Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).scale(R * 0.05));
                Vec3 m0 = at.add(next).scale(0.5), ax = next.subtract(at);
                Spr bolt = spr(m0, s.d.scale(s.speed * 0.9), ax, 0, (float) ax.length() * 1.1F, 0.05F, r.nextInt(2, 4));
                bolt.dark = true; bolt.pal = 0; bolt.darkAlpha = 1.0F;
                add(bolt);
                Spr glow = spr(m0, s.d.scale(s.speed * 0.9), ax, 0, (float) ax.length() * 1.2F, 0.16F, 2);
                glow.pal = 0; glow.heat = 0.7F;
                add(glow);
                at = next;
                if (r.nextInt(4) == 0) dir = dir.add(new Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).scale(0.5)).normalize();
            }
        }
        // crimson cross-sparks twinkling along the edge
        for (int i = 0; i < (burst ? 8 : 3); i++) {
            double a = r.nextDouble(-0.9, 0.9);
            Spr x = spr(arcPoint(s, centre, a, R).add(s.d.scale(R * 0.03)), s.d.scale(s.speed * 0.8), s.u, K_CROSS, (float) (R * 0.16), (float) (R * 0.16), r.nextInt(3, 6));
            x.pal = 0; x.heat = 0.95F; x.twinkle = true; x.pop = 1;
            add(x);
        }
        // black smoke curling round the path behind it
        for (int i = 0; i < (burst ? 14 : 8); i++) {
            double a = r.nextDouble(-1, 1);
            Vec3 at = arcPoint(s, centre, a, R * 0.85).subtract(s.d.scale(R * r.nextDouble(0.2, 0.5)));
            Vec3 radial = at.subtract(mid).normalize();
            Spr p = spr(at, s.d.scale(s.speed * 0.2).add(radial.scale(0.05)), radial.cross(s.d), 3,
                    (float) r.nextDouble(0.5, 1.0), (float) r.nextDouble(0.3, 0.55), r.nextInt(10, 17));
            p.dark = true; p.pal = 0; p.darkAlpha = 0.7F; p.curl = s.d; p.curlRate = (float) r.nextDouble(0.2, 0.4) * (r.nextBoolean() ? 1 : -1); p.drag = 0.9F;
            add(p);
        }
        // red embers and black flakes falling away
        for (int i = 0; i < (burst ? 22 : 12); i++) {
            double a = r.nextDouble(-1, 1);
            Vec3 at = arcPoint(s, centre, a, R);
            Vec3 out = at.subtract(mid).normalize();
            Vec3 vel = out.scale(r.nextDouble(0.06, 0.25)).add(s.d.scale(s.speed * 0.35)).add(0, r.nextDouble(0.0, 0.06), 0);
            float sz = (float) r.nextDouble(0.06, 0.15);
            Spr p = spr(at, vel, vel, r.nextFloat() < 0.6F ? 2 : 1, sz, sz, r.nextInt(8, 16));
            if (r.nextBoolean()) { p.pal = 0; p.heat = 0.9F; } else { p.dark = true; p.pal = 0; p.darkAlpha = 1.0F; }
            p.drag = 0.92F;
            add(p);
        }
        if (burst) {   // released: a ring of shards bursting outward round the blade, and white flash streaks
            for (int i = 0; i < 40; i++) {
                double ang = Math.PI * 2 * i / 40;
                Vec3 out = s.u.scale(Math.cos(ang)).add(s.side.scale(Math.sin(ang)));
                Spr p = spr(s.c.add(out.scale(0.6)), out.scale(r.nextDouble(0.45, 0.7)), out, 0, 0.45F, 0.08F, r.nextInt(4, 7));
                p.heat = 1.0F; p.drag = 0.8F;
                if (i % 2 == 0) { p.pal = 0; p.heat = 0.85F; }
                add(p);
            }
            for (int i = 0; i < 12; i++) {
                Vec3 out = new Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).normalize();
                Spr p = spr(s.c, out.scale(0.25), out, 0, 1.2F, 0.1F, 3);
                p.heat = 1.0F;
                add(p);
            }
        }
    }

    /** The plain flame version (other schemes). */
    private static void emitFlame(Slash s, double age, boolean burst) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double grow = 1.0 - Math.pow(1.0 - Mth.clamp(age / (s.life * 0.5), 0.0, 1.0), 2.0);
        double R = s.r0 + (s.r1 - s.r0) * grow;
        double centre = s.speed * age;
        int n = burst ? 150 : 90;
        for (int i = 0; i < n; i++) {
            double a = r.nextDouble(-1, 1);
            double edge = Math.pow(Math.cos(a * Math.PI * 0.5), 1.3);   // the blade is thickest in the middle
            Vec3 tan = arcTangent(s, a, R);
            double q = r.nextDouble(), depth;
            int sgn = r.nextBoolean() ? 1 : -1;
            Spr p = new Spr();
            if (q < 0.4) {          // the cutting edge: white-hot streaks lying exactly along the arc, moving with it
                depth = r.nextDouble() * R * 0.04;
                p.kind = 0; p.dark = false; p.heat = 1.0F;
                p.axis = tan.subtract(s.d.scale(0.12)).normalize();
                p.vel = s.d.scale(s.speed * 0.95);
                p.len = (float) (R * r.nextDouble(0.12, 0.22) * (0.5 + 0.5 * edge)); p.wid = (float) (0.07 + 0.05 * edge);
                p.life = r.nextInt(3, 5);
            } else if (q < 0.7) {   // the core: black anti magic just behind the edge
                depth = R * (0.06 + r.nextDouble() * 0.16) * edge;
                p.kind = r.nextFloat() < 0.8F ? 0 : 3; p.dark = s.pal == 0; p.heat = 0.55F; if (s.pal == 0) p.pal = 0;
                p.axis = tan.subtract(s.d.scale(0.1)).normalize();
                p.vel = s.d.scale(s.speed * 0.9);
                p.len = (float) (R * r.nextDouble(0.14, 0.26)); p.wid = (float) (0.1 + 0.12 * edge);
                p.life = r.nextInt(3, 6);
            } else {                // flames swept back off the blade, cooling as they fall behind
                depth = R * r.nextDouble() * 0.25 * edge;
                p.kind = r.nextFloat() < 0.6F ? 0 : 3; p.dark = false; p.heat = 0.75F;
                p.axis = tan.scale(sgn * 0.6).subtract(s.d.scale(0.8)).normalize();
                p.vel = s.d.scale(s.speed * r.nextDouble(0.3, 0.55));
                p.len = (float) (R * r.nextDouble(0.1, 0.22) * (0.6 + 0.4 * edge)); p.wid = (float) (0.06 + 0.06 * edge);
                p.life = r.nextInt(6, 11);
            }
            Vec3 at = arcPoint(s, centre, a, R).subtract(s.d.scale(depth)).add(s.side.scale(r.nextGaussian() * 0.05 * R * edge));
            p.pos = at; p.prev = at;
            p.drag = 0.86F;
            add(p);
        }
        // sparks and shards flung off the edge
        for (int i = 0; i < (burst ? 20 : 10); i++) {
            double a = r.nextDouble(-1, 1);
            Vec3 at = arcPoint(s, centre, a, R);
            Vec3 out = at.subtract(s.c.add(s.d.scale(centre - R * 0.5))).normalize();
            Spr p = new Spr();
            p.pos = at; p.prev = at;
            p.kind = r.nextFloat() < 0.6F ? 2 : 1; p.dark = false; p.heat = r.nextFloat() * 0.5F + 0.5F;
            p.vel = out.scale(r.nextDouble(0.08, 0.3)).add(s.d.scale(s.speed * 0.4)).add(0, r.nextDouble(0.0, 0.08), 0);
            p.axis = p.vel.lengthSqr() > 1.0E-6 ? p.vel.normalize() : s.d;
            float sz = (float) r.nextDouble(0.06, 0.16);
            p.len = sz; p.wid = sz;
            p.life = r.nextInt(8, 16);
            p.drag = 0.92F;
            add(p);
        }
    }

    // ================================================================== every other move, in the same style
    /*
     * Every effect FxRenderer draws (slashes, crescents, beams, rings, blocks, spikes, bones, spheres, fireballs,
     * lightning, tornadoes, gusts, space cracks) and every form or aura worn by a player now sheds pixel sprites
     * too, in its own palette. What the sprites do depends on the kind of magic:
     *   DARK   (anti magic, darkness, devil)  black blade streaks, a coloured glow, white edges, flakes, curling smoke
     *   FLAME  (fire, blue fire, crimson)     rising flame tongues, hot streaks, embers (crimson burns clean: no smoke)
     *   BOLT   (lightning)                    white zigzags with a coloured glow, fast sparks
     *   WIND                                  long thin swirling streaks, glinting leaves
     *   METAL  (steel, gold, mercury)         falling solid shards, white glints, sparks
     *   BONE                                  ivory shards and bone dust
     *   LIGHT  (everything else)              glowing streaks and shards
     */

    private static float density() { return Mth.clamp(com.ironspellgrimoire.AddonClientConfig.particleDensity(), 0.0F, 2.0F); }

    /** How many sprites for a wanted (fractional) count, scaled by the density setting. */
    /** Extra density while emitting for an effect that is drawn only in sprites (no solid shape under it). */
    private static double boost = 1.0;

    private static int cnt(double want) {
        double w = want * density() * boost;
        int busy = SPRITES.size();
        int calm = max() * 3 / 8;
        if (busy > calm) w *= (double) calm / busy;   // the busier the screen, the fewer new sprites per emission
        int n = (int) w;
        return n + (ThreadLocalRandom.current().nextDouble() < w - n ? 1 : 0);
    }

    private static Vec3 rnd(ThreadLocalRandom r, double s) { return new Vec3(r.nextGaussian() * s, r.nextGaussian() * s, r.nextGaussian() * s); }

    private static Vec3 nz(Vec3 v, Vec3 fallback) { return v.lengthSqr() > 1.0E-8 ? v.normalize() : fallback; }

    private static final Vec3 UP = new Vec3(0, 1, 0);

    /*
     * Each grimoire's own sprite set: LONG = the streak its magic is drawn in, BIT = the little pieces it sheds,
     * SPEC = its signature piece. (0 Asta's anti magic: jagged black blade slivers and crimson cross-sparks;
     * 5 Yuno's wind: feathers and crescent gusts; 7 Klaus: steel plates; 8 gold: coin rings; 9 devil: black thorns
     * and claws; 10 Yami: dark crescents and smoke puffs; 11 Nozel: mercury drops; 12 blue fire: wisps;
     * 13 Luck: zigzag bolts and diamonds; 14 Mereoleona: curved lion-mane flame claws; 15 Zenon: little bones.)
     */
    private static final int[] LONG = {K_BLADE, K_MOON, K_TONGUE, K_STREAK, K_BOLT, K_STREAK, K_STREAK, K_STREAK, K_STREAK, K_BARB, K_MOON, K_STREAK, K_WISP, K_BOLT, K_CLAW, K_STREAK, K_STREAK, K_STREAK, K_BLADE, K_STREAK, K_STREAK, K_STREAK, K_TONGUE, K_CLAW, K_STREAK, K_BLADE, K_SHARD, K_CLAW, K_PUFF, K_STREAK, K_STREAK, K_TONGUE};
    private static final int[] BIT = {K_SHARD, K_SHARD, K_EMBER, K_DIAMOND, K_CROSS, K_FEATHER, K_STAR, K_PLATE, K_RING, K_CROSS, K_PUFF, K_DROP, K_EMBER, K_DIAMOND, K_EMBER, K_BONE, K_CROSS, K_STAR, K_SHARD, K_DROP, K_DIAMOND, K_RING, K_EMBER, K_EMBER, K_FEATHER, K_SHARD, K_PLATE, K_BONE, K_PUFF, K_STAR, K_FEATHER, K_DROP};
    private static final int[] SPEC = {K_CROSS, K_PUFF, K_CROSS, K_STAR, K_STAR, K_MOON, K_CROSS, K_CROSS, K_STAR, K_CLAW, K_WISP, K_STAR, K_TONGUE, K_STAR, K_TONGUE, K_SHARD, K_RING, K_RING, K_CROSS, K_STAR, K_STAR, K_RING, K_TONGUE, K_TONGUE, K_STAR, K_CROSS, K_CROSS, K_CLAW, K_STAR, K_RING, K_SHARD, K_DROP};

    /** One sprite of a palette's own kind of magic at {@code at}, carried by {@code drift}, lying along {@code axis}. */
    static void one(int pal, Vec3 at, Vec3 drift, Vec3 axis, double size) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        pal = Mth.clamp(pal, 0, RIM.length - 1);
        axis = nz(axis, UP);
        float sz = (float) size;
        pal = Math.min(pal, LONG.length - 1);
        int L = LONG[pal], B = BIT[pal], X = SPEC[pal];
        if (pal == 28) { cottonOne(at, drift, axis, sz); return; }
        double q = r.nextDouble();
        Spr p;
        switch (element(pal)) {
            case DARK -> {
                if (q < 0.42) {        // the black of the blade, in its own shape (blades, crescents, thorns)
                    p = spr(at, drift.scale(0.95), axis.add(rnd(r, 0.12)), r.nextFloat() < 0.75F ? L : K_TONGUE, sz * (float) r.nextDouble(1.0, 1.8), sz * (float) r.nextDouble(0.35, 0.55), r.nextInt(3, 6));
                    p.dark = true;
                } else if (q < 0.62) { // its coloured glow, the same shape
                    p = spr(at.add(rnd(r, size * 0.15)), drift.scale(0.85), axis, L, sz * 1.5F, sz * 0.6F, r.nextInt(2, 5));
                    p.heat = 0.6F;
                } else if (q < 0.72) { // a white-hot edge
                    p = spr(at, drift.scale(0.97), axis, K_STREAK, sz * 1.2F, sz * 0.14F, r.nextInt(2, 4));
                    p.heat = 1.0F;
                } else if (q < 0.86) { // pieces flung away, tumbling
                    Vec3 v = drift.scale(0.3).add(rnd(r, 0.08)).add(0, 0.03, 0);
                    p = spr(at, v, v, B, sz * 0.32F, sz * 0.32F, r.nextInt(8, 15));
                    p.drag = 0.92F; p.spin = (float) r.nextDouble(-0.5, 0.5); p.pop = 2;
                    if (r.nextBoolean() || B == K_PUFF) { p.dark = true; p.darkAlpha = 1.0F; } else p.heat = 0.9F;
                } else if (q < 0.93) { // the signature piece
                    p = spr(at, drift.scale(0.6).add(rnd(r, 0.03)), axis, X, sz * 0.7F, sz * 0.7F, r.nextInt(4, 8));
                    p.heat = 0.95F; p.pop = 1.5F; p.spin = (float) r.nextDouble(-0.25, 0.25); p.twinkle = X == K_CROSS;
                    if (X == K_CLAW || X == K_PUFF) { p.dark = true; p.darkAlpha = 0.85F; p.len = sz * 1.4F; p.wid = sz * 0.9F; }
                } else {               // smoke curling away
                    Vec3 v = drift.scale(0.2).add(rnd(r, 0.03));
                    p = spr(at, v, axis.cross(UP).lengthSqr() > 1.0E-6 ? axis.cross(UP) : axis, pal == 10 ? K_PUFF : K_TONGUE, sz * 2.0F, sz * 1.2F, r.nextInt(10, 17));
                    p.dark = true; p.darkAlpha = 0.6F; p.drag = 0.9F; p.grav = 0.004F; p.grow = 1.03F;
                    p.curl = nz(drift, UP); p.curlRate = (float) r.nextDouble(0.2, 0.4) * (r.nextBoolean() ? 1 : -1);
                }
            }
            case FLAME -> {
                if (q < 0.5) {         // flames in the grimoire's shape (tongues, mane claws, wisps), licking upward
                    p = spr(at, drift.scale(0.5).add(0, 0.05, 0).add(rnd(r, 0.015)), UP.add(axis.scale(0.3)).add(rnd(r, 0.15)), L,
                            sz * 1.7F, sz * (L == K_TONGUE ? 0.8F : 0.6F), r.nextInt(5, 10));
                    p.heat = (float) r.nextDouble(0.75, 1.0); p.grav = 0.006F; p.flicker = true;
                    if (L == K_WISP) { p.curl = UP; p.curlRate = 0.3F * (r.nextBoolean() ? 1 : -1); }
                } else if (q < 0.66) { // hot streaks along the shape
                    p = spr(at, drift.scale(0.9), axis, K_STREAK, sz * 1.6F, sz * 0.25F, r.nextInt(3, 6));
                    p.heat = 0.9F;
                } else if (q < 0.88 || (pal == 14 && q < 0.95)) {   // embers
                    Vec3 v = drift.scale(0.3).add(rnd(r, 0.08)).add(0, 0.06, 0);
                    p = spr(at, v, v, B, sz * 0.25F, sz * 0.25F, r.nextInt(10, 19));
                    p.heat = 1.0F; p.drag = 0.93F; p.twinkle = true;
                } else if (q < 0.95 || pal == 14) {   // the signature (Magna: hot cross-bursts; blue fire / Mereoleona: a big tongue)
                    p = spr(at, drift.scale(0.5).add(0, 0.04, 0), X == K_CROSS ? axis : UP, X, sz * (X == K_CROSS ? 0.8F : 2.4F), sz * (X == K_CROSS ? 0.8F : 1.0F), r.nextInt(4, 8));
                    p.heat = 1.0F; p.pop = 1.5F; p.flicker = X != K_CROSS; p.grav = X == K_CROSS ? 0 : 0.004F;
                } else {               // a curl of smoke (Mereoleona's fire burns clean: never reaches here)
                    p = spr(at, drift.scale(0.2).add(0, 0.03, 0), UP, K_PUFF, sz * 1.4F, sz * 1.4F, r.nextInt(14, 21));
                    p.dark = true; p.darkAlpha = 0.45F; p.drag = 0.9F; p.grav = 0.003F; p.grow = 1.04F;
                }
            }
            case BOLT -> {
                if (q < 0.45) {        // a zigzag bolt sprite with a little forked tail of streaks
                    Spr b0 = spr(at, drift.scale(0.9), axis.add(rnd(r, 0.4)), L, sz * 1.6F, sz * 0.8F, r.nextInt(2, 4));
                    b0.heat = 1.0F; b0.pal = pal; b0.flicker = true; add(b0);
                    Vec3 a0 = at, dir = nz(axis.add(rnd(r, 0.6)), UP);
                    for (int k = 0; k < 2; k++) {
                        Vec3 a1 = a0.add(dir.scale(size * 0.7)).add(rnd(r, size * 0.25));
                        Vec3 mid = a0.add(a1).scale(0.5), ax = a1.subtract(a0);
                        Spr core = spr(mid, drift.scale(0.9), ax, K_STREAK, (float) ax.length() * 1.1F, sz * 0.12F, r.nextInt(2, 4));
                        core.heat = 1.0F; core.pal = pal; add(core);
                        a0 = a1;
                        dir = nz(dir.add(rnd(r, 0.7)), UP);
                    }
                    return;
                }
                if (q < 0.82) {        // fast sparks in the grimoire's shape (crosses, diamonds), spinning
                    Vec3 v = drift.scale(0.5).add(rnd(r, 0.2));
                    p = spr(at, v, v, B, sz * 0.3F, sz * 0.3F, r.nextInt(4, 9));
                    p.heat = 1.0F; p.drag = 0.85F; p.spin = (float) r.nextDouble(-0.8, 0.8);
                } else {               // a twinkling star where the charge hangs in the air
                    p = spr(at, drift.scale(0.2), axis, X, sz * 0.9F, sz * 0.9F, r.nextInt(5, 10));
                    p.heat = 1.0F; p.twinkle = true; p.pop = 1;
                }
            }
            case WIND -> {
                if (q < 0.55) {        // long thin streaks of air, swirling
                    p = spr(at, drift.add(axis.scale(0.15)), axis, K_STREAK, sz * 2.5F, sz * 0.12F, r.nextInt(4, 8));
                    p.heat = 0.75F;
                    if (r.nextBoolean()) { p.curl = UP; p.curlRate = 0.25F * (r.nextBoolean() ? 1 : -1); }
                } else if (q < 0.72) {
                    p = spr(at, drift.add(axis.scale(0.2)), axis, K_STREAK, sz * 1.8F, sz * 0.07F, r.nextInt(3, 6));
                    p.heat = 1.0F;
                } else if (q < 0.92) { // feathers drifting and turning on the wind
                    Vec3 v = drift.scale(0.5).add(rnd(r, 0.05)).add(0, 0.01, 0);
                    p = spr(at, v, axis.add(rnd(r, 0.6)), B, sz * 0.6F, sz * 0.35F, r.nextInt(12, 20));
                    p.heat = 0.8F; p.drag = 0.94F; p.curl = UP; p.curlRate = 0.25F; p.spin = (float) r.nextDouble(-0.2, 0.2);
                } else {               // a crescent gust
                    p = spr(at, drift.add(axis.scale(0.25)), axis.cross(UP).lengthSqr() > 1.0E-6 ? axis.cross(UP) : axis, X, sz * 1.8F, sz * 0.8F, r.nextInt(3, 6));
                    p.heat = 0.9F; p.pop = 1;
                }
            }
            case METAL -> {
                if (pal == 11) { mercury(at, drift, axis, sz, q, r); return; }
                if (q < 0.38) {        // solid pieces (Klaus: plates, Nozel: mercury drops, gold: coins), falling and spinning
                    Vec3 v = drift.scale(0.6).add(rnd(r, 0.08));
                    float s2 = sz * (float) r.nextDouble(0.35, 0.6);
                    p = spr(at, v, B == K_DROP ? new Vec3(0, -1, 0).subtract(v) : v.add(rnd(r, 0.5)), B, s2, B == K_DROP ? s2 * 0.7F : s2, r.nextInt(6, 13));
                    p.dark = B != K_RING; p.darkAlpha = 1.0F; p.drag = 0.9F; p.grav = -0.01F; p.heat = 0.9F;
                    p.spin = B == K_DROP ? 0 : (float) r.nextDouble(-0.6, 0.6); p.pop = 1.5F;
                } else if (q < 0.7) {  // a white glint running along it
                    p = spr(at, drift.scale(0.9), axis, K_STREAK, sz * 0.9F, sz * 0.1F, r.nextInt(2, 4));
                    p.heat = 1.0F;
                } else if (q < 0.86) { // the flash of light off the metal
                    p = spr(at.add(rnd(r, size * 0.2)), drift.scale(0.8), axis, X, sz * 0.7F, sz * 0.7F, r.nextInt(3, 7));
                    p.heat = 1.0F; p.twinkle = true; p.spin = 0.15F;
                } else {
                    Vec3 v = drift.scale(0.4).add(rnd(r, 0.15));
                    p = spr(at, v, v, K_EMBER, sz * 0.15F, sz * 0.15F, r.nextInt(5, 10));
                    p.heat = 0.95F; p.drag = 0.88F;
                }
            }
            case BONE -> {
                if (q < 0.42) {        // little bones, tumbling down
                    Vec3 v = drift.scale(0.6).add(rnd(r, 0.07));
                    float s2 = sz * (float) r.nextDouble(0.45, 0.7);
                    p = spr(at, v, v.add(rnd(r, 0.5)), B, s2, s2 * 0.45F, r.nextInt(8, 15));
                    p.dark = true; p.darkAlpha = 1.0F; p.drag = 0.9F; p.grav = -0.012F; p.spin = (float) r.nextDouble(-0.5, 0.5); p.pop = 1.5F;
                } else if (q < 0.7) {  // bone dust
                    Vec3 v = drift.scale(0.3).add(rnd(r, 0.03));
                    p = spr(at, v, v, K_EMBER, sz * 0.2F, sz * 0.2F, r.nextInt(10, 17));
                    p.dark = true; p.darkAlpha = 0.7F; p.drag = 0.92F; p.grav = -0.004F;
                } else if (q < 0.88) { // splinters
                    Vec3 v = drift.scale(0.5).add(rnd(r, 0.1));
                    p = spr(at, v, v, X, sz * 0.3F, sz * 0.3F, r.nextInt(6, 11));
                    p.dark = true; p.darkAlpha = 1.0F; p.grav = -0.01F; p.spin = 0.4F;
                } else {               // a pale marrow-glow streak
                    p = spr(at, drift.scale(0.9), axis, K_STREAK, sz * 1.2F, sz * 0.14F, r.nextInt(2, 4));
                    p.heat = 0.8F;
                }
            }
            default -> {
                if (q < 0.45) {
                    p = spr(at, drift.scale(0.9), axis, L, sz * 1.5F, sz * 0.3F, r.nextInt(3, 6));
                    p.heat = 0.8F;
                } else if (q < 0.68) {
                    p = spr(at, drift.scale(0.95), axis, K_STREAK, sz * 1.1F, sz * 0.1F, r.nextInt(2, 4));
                    p.heat = 1.0F;
                } else if (q < 0.88) {
                    Vec3 v = drift.scale(0.4).add(rnd(r, 0.08));
                    p = spr(at, v, v, B, sz * 0.3F, sz * 0.3F, r.nextInt(6, 12));
                    p.heat = 0.9F; p.drag = 0.92F; p.spin = 0.3F;
                } else {
                    p = spr(at, drift.scale(0.5), axis, X, sz * 0.8F, sz * 0.8F, r.nextInt(4, 9));
                    p.heat = 1.0F; p.twinkle = true; p.pop = 1;
                }
            }
        }
        p.pal = pal;
        add(p);
    }


    // ------------------------------------------------------------------ real cotton (Charmy): lumpy fluffy bolls, tufts and curling fibres, solid white with soft shading
    private static void cottonOne(Vec3 at, Vec3 drift, Vec3 axis, float sz) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double q = r.nextDouble();
        Spr p;
        if (q < 0.5) {         // a boll of cotton, floating on
            Vec3 v = drift.scale(0.35).add(rnd(r, 0.03)).add(0, 0.01, 0);
            float s = sz * (float) r.nextDouble(0.9, 1.7);
            p = spr(at.add(rnd(r, sz * 0.2)), v, v, K_COTTON, s, s, r.nextInt(10, 18));
            p.tone = (float) r.nextDouble(0.88, 1.0); p.drag = 0.9F; p.grav = -0.003F; p.spin = (float) r.nextDouble(-0.15, 0.15); p.pop = 2; p.grow = 1.015F;
        } else if (q < 0.72) { // a tuft with fine fibres streaming off it
            Vec3 v = drift.scale(0.3).add(rnd(r, 0.04)).add(0, 0.015, 0);
            float s = sz * (float) r.nextDouble(0.8, 1.3);
            p = spr(at.add(rnd(r, sz * 0.2)), v, v, K_TUFT, s, s, r.nextInt(8, 15));
            p.tone = (float) r.nextDouble(0.9, 1.0); p.drag = 0.9F; p.grav = -0.004F; p.spin = (float) r.nextDouble(-0.2, 0.2); p.pop = 1.5F;
        } else if (q < 0.9) {  // a long curling fibre
            Vec3 v = drift.scale(0.5).add(rnd(r, 0.03));
            p = spr(at, v, v.lengthSqr() > 1.0E-6 ? v : axis, K_FIBER, sz * 1.8F, sz * 0.4F, r.nextInt(6, 11));
            p.tone = 0.97F; p.drag = 0.92F; p.grav = -0.002F; p.curl = nz(drift, UP); p.curlRate = (float) r.nextDouble(0.15, 0.35) * (r.nextBoolean() ? 1 : -1);
        } else {               // a speck of fluff
            Vec3 v = drift.scale(0.2).add(rnd(r, 0.03)).add(0, 0.02, 0);
            p = spr(at, v, v, K_EMBER, sz * 0.18F, sz * 0.18F, r.nextInt(10, 18));
            p.drag = 0.93F; p.grav = -0.003F;
        }
        p.dark = true; p.darkAlpha = 1.0F; p.pal = 28;
        add(p);
    }

    /** A ball of cotton: a heap of overlapping lumps with fibres bristling off its surface. */
    private static void cottonBall(Vec3 at, Vec3 v, double R) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int n = cnt(Mth.clamp(R * 9.0, 5.0, 26.0));
        for (int i = 0; i < n; i++) {
            Vec3 dir = nz(rnd(r, 1.0), UP);
            double rr = R * 0.8 * Math.cbrt(r.nextDouble());
            float sz = (float) (R * r.nextDouble(0.7, 1.1));
            Spr s = spr(at.add(dir.scale(rr)), v, dir, K_COTTON, sz, sz, r.nextInt(2, 4));
            s.dark = true; s.darkAlpha = 1.0F; s.tone = 0.84F + 0.16F * r.nextFloat(); s.spin = (r.nextFloat() - 0.5F) * 0.3F; s.pal = 28;
            add(s);
        }
        int f = cnt(Mth.clamp(R * 6.0, 3.0, 14.0));
        for (int i = 0; i < f; i++) {
            Vec3 dir = nz(rnd(r, 1.0), UP);
            Spr s = spr(at.add(dir.scale(R * 0.9)), v.add(dir.scale(0.03)), dir, K_FIBER, (float) R * 1.1F, (float) R * 0.25F, r.nextInt(2, 4));
            s.dark = true; s.darkAlpha = 1.0F; s.tone = 0.97F; s.pal = 28;
            add(s);
        }
    }

    /**
     * The body of a fireball / mercury sphere, all in sprites, moving with it: fire is a churning ball of flame tongues
     * with a white-hot heart; mercury is a cluster of silver drops with glints; anything else, its own pieces.
     */
    private static void ball(int pal, Vec3 at, Vec3 v, double R) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int el = element(pal);
        if (pal == 28) { cottonBall(at, v, R); return; }
        if (R > 1.2) { shell(pal, el, at, v, R, r); return; }
        if (pal == 11) { mercuryBall(at, v, R, r); return; }
        int n = cnt(Mth.clamp(R * 12.0, 6.0, 30.0));
        for (int i = 0; i < n; i++) {
            Vec3 dir = nz(rnd(r, 1.0), UP);
            double rr = R * Math.cbrt(r.nextDouble());
            Vec3 o = at.add(dir.scale(rr));
            float sz = (float) (R * r.nextDouble(0.45, 0.8));
            Spr s;
            if (el == FLAME) {
                s = spr(o, v.add(dir.scale(0.02)), dir, rr < R * 0.5 ? K_EMBER : K_TONGUE, sz * (rr < R * 0.5 ? 0.8F : 1.4F), sz * 0.8F, r.nextInt(2, 4));
                s.heat = (float) (1.0 - 0.45 * rr / R); s.flicker = true;
            } else if (el == METAL) {
                s = spr(o, v, dir, rr > R * 0.7 ? K_STAR : K_DROP, sz, sz * 0.8F, r.nextInt(2, 4));
                if (rr > R * 0.7) { s.heat = 1.0F; s.twinkle = true; } else { s.dark = true; s.darkAlpha = 1.0F; }
            } else if (el == DARK) {
                s = spr(o, v, dir, LONG[pal], sz * 1.2F, sz * 0.6F, r.nextInt(2, 4));
                if (rr < R * 0.75) { s.dark = true; s.darkAlpha = 1.0F; } else s.heat = 0.6F;
            } else {
                s = spr(o, v, dir, BIT[pal], sz, sz, r.nextInt(2, 4));
                s.heat = 0.9F;
            }
            s.pal = pal; s.drag = 1.0F; s.grav = 0.0F; s.spin = (float) r.nextDouble(-0.3, 0.3);
            add(s);
        }
    }

    /**
     * Chrome shading of liquid mercury by which way its surface faces: the bright sky reflected on top, a dark band
     * at the horizon, the dimmer ground below - the look of a polished metal ball.
     */
    private static float chrome(double ny) {
        if (ny > 0.3) return 1.45F;
        if (ny > 0.0) return 0.55F + (float) (ny / 0.3) * 0.9F;
        if (ny > -0.18) return 0.38F;
        return 0.85F;
    }

    /** One piece of liquid mercury: a solid chrome blob, a falling bead or a sharp white glint. */
    private static void mercury(Vec3 at, Vec3 drift, Vec3 axis, float sz, double q, ThreadLocalRandom r) {
        Spr p;
        if (q < 0.62) {
            Vec3 v = drift.scale(0.7).add(rnd(r, 0.03));
            float s2 = sz * (float) r.nextDouble(0.45, 0.85);
            p = spr(at, v, v.lengthSqr() > 1.0E-4 ? v : axis, K_DROP, s2 * 1.25F, s2, r.nextInt(4, 9));
            p.dark = true; p.darkAlpha = 1.0F; p.drag = 0.9F; p.grav = -0.006F;
            p.tone = chrome(r.nextDouble(-1, 1));
        } else if (q < 0.86) {
            Vec3 v = drift.scale(0.4).add(rnd(r, 0.04)).add(0, -0.02, 0);
            float s2 = sz * (float) r.nextDouble(0.16, 0.28);
            p = spr(at, v, new Vec3(0, -1, 0), K_DROP, s2 * 1.4F, s2, r.nextInt(8, 15));
            p.dark = true; p.darkAlpha = 1.0F; p.grav = -0.02F; p.drag = 0.96F;
            p.tone = r.nextBoolean() ? 1.4F : 0.75F;
        } else {
            p = spr(at.add(rnd(r, sz * 0.2)), drift.scale(0.9), axis, K_STAR, sz * 0.32F, sz * 0.32F, r.nextInt(2, 4));
            p.heat = 1.0F; p.twinkle = true;
        }
        p.pal = 11;
        add(p);
    }

    /** A ball of mercury: solid chrome drops packed into a sphere, shaded like a polished metal ball, glints on top. */
    private static void mercuryBall(Vec3 at, Vec3 v, double R, ThreadLocalRandom r) {
        int n = cnt(Mth.clamp(R * 16.0, 8.0, 34.0));
        for (int i = 0; i < n; i++) {
            Vec3 dir = nz(rnd(r, 1.0), UP);
            double rr = R * Math.sqrt(r.nextDouble(0.35, 1.0));
            float sz = (float) (R * r.nextDouble(0.42, 0.62));
            Spr s = spr(at.add(dir.scale(rr * 0.75)), v, dir.cross(UP).lengthSqr() > 1.0E-6 ? dir.cross(UP) : UP, K_DROP, sz, sz * 0.9F, r.nextInt(2, 4));
            s.dark = true; s.darkAlpha = 1.0F; s.drag = 1.0F; s.grav = 0.0F;
            s.tone = chrome(dir.y);
            s.pal = 11;
            add(s);
        }
        if (r.nextInt(3) == 0) {   // the highlight: a hard white star high on the side facing the sky
            Vec3 o = at.add(nz(new Vec3(r.nextGaussian() * 0.4, 1.0, r.nextGaussian() * 0.4), UP).scale(R * 0.7));
            Spr g = spr(o, v, UP, K_STAR, (float) R * 0.35F, (float) R * 0.35F, 2);
            g.heat = 1.0F; g.pal = 11; g.twinkle = true;
            add(g);
        }
    }

    /**
     * A big ball (a barrier like the Mercury Sphere): only its SURFACE is drawn, in small sprites with gaps between
     * them, so you can always see out of it from inside and see in from outside. Seen from inside it is fainter still.
     */
    private static void shell(int pal, int el, Vec3 at, Vec3 v, double R, ThreadLocalRandom r) {
        Minecraft mc = Minecraft.getInstance();
        boolean inside = mc.gameRenderer != null && mc.gameRenderer.getMainCamera().getPosition().distanceTo(at) < R + 0.3;
        int n = cnt(Mth.clamp(R * 3.0, 4.0, 12.0) * (inside ? 0.5 : 1.0));
        for (int i = 0; i < n; i++) {
            Vec3 dir = nz(rnd(r, 1.0), UP);
            Vec3 o = at.add(dir.scale(R * r.nextDouble(0.95, 1.0)));
            Vec3 tan = nz(dir.cross(UP), new Vec3(1, 0, 0));
            float sz = (float) r.nextDouble(0.14, 0.3);
            Spr s = spr(o, v.add(tan.scale(r.nextDouble(-0.02, 0.02))), tan, el == METAL ? (r.nextInt(3) == 0 ? K_STAR : K_DROP) : BIT[pal], sz, sz * 0.8F, r.nextInt(3, 6));
            s.heat = el == METAL ? 0.85F : 0.7F;
            if (el == METAL && s.kind == K_STAR) s.twinkle = true;
            if (pal == 11 && s.kind == K_DROP) { s.dark = true; s.darkAlpha = inside ? 0.45F : 0.9F; s.tone = chrome(dir.y); }
            s.pal = pal; s.drag = 1.0F; s.grav = 0.0F; s.spin = (float) r.nextDouble(-0.2, 0.2);
            s.see = inside;
            add(s);
        }
    }

    /**
     * Equinox's invisible sword (FxPacket shape 44: c = hilt, d = along the blade, u = across it, r = length,
     * r2 = width, life): the blade itself can't be seen - only its outline, picked out by faint pale glints along
     * both edges that taper to the point, a hairline down its spine and a slow sheen sliding along it. Inside it is
     * clear.
     */
    public static void ghostBlade(FxPacket p) {
        if (Minecraft.getInstance().level == null || p.d == null || p.u == null) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Vec3 d = nz(p.d, UP), u = nz(p.u, new Vec3(1, 0, 0));
        double L = Math.max(0.5, p.r), W = Math.max(0.05, p.r2);
        int life = Math.max(2, p.life);
        int n = (int) Mth.clamp(L * 7.0, 10.0, 110.0);
        for (int i = 0; i < n; i++) {
            double t = r.nextDouble();
            double w = W * 0.5 * (t < 0.82 ? 1.0 : (1.0 - t) / 0.18);   // straight edges, then the point
            int side = r.nextBoolean() ? 1 : -1;
            Vec3 at = p.c.add(d.scale(L * t)).add(u.scale(w * side));
            Vec3 along = t < 0.82 ? d : d.scale(0.8).subtract(u.scale(side * 0.6));
            Spr e = spr(at, Vec3.ZERO, along, K_STREAK, (float) Math.min(0.5, L * 0.04 + 0.12), 0.035F, life + r.nextInt(0, 3));
            e.pal = 6; e.heat = 0.62F; e.alpha = 0.55F; e.pop = 1.0F;
            add(e);
        }
        for (int i = 0; i < n / 3; i++) {   // the spine: a hairline down the middle
            Spr e = spr(p.c.add(d.scale(L * r.nextDouble(0.0, 0.95))), Vec3.ZERO, d, K_STREAK, (float) Math.min(0.6, L * 0.05 + 0.12), 0.02F, life);
            e.pal = 10; e.heat = 0.6F; e.alpha = 0.35F;
            add(e);
        }
        for (int i = 0; i < 2; i++) {        // the sheen: a brighter glint sliding from hilt to tip
            Spr g = spr(p.c.add(u.scale(W * 0.5 * (i == 0 ? 1 : -1))), d.scale(L / (double) life), d, K_STREAK, (float) Math.min(1.2, L * 0.12), 0.05F, life);
            g.pal = 6; g.heat = 0.95F; g.alpha = 0.8F; g.drag = 1.0F;
            add(g);
        }
    }

    /** A shock ring: a pixel ring that bursts outward from {@code at} and fades. */
    private static void shockRing(int pal, Vec3 at, double size) {
        Spr p = spr(at, Vec3.ZERO, UP, K_SHOCK, (float) size, (float) size, 6);
        p.pal = pal; p.heat = 0.9F; p.grow = 1.22F; p.seed = 0;   // upright: a ring never spins
        add(p);
    }

    /**
     * The signature moment when an effect appears - a different burst for every grimoire, so each one's moves are
     * recognisable at a glance. {@code d} = its direction, {@code s} = how big it is.
     */
    static void signature(int pal, Vec3 at, Vec3 d, double s) {
        if (density() <= 0.0F) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        pal = Mth.clamp(pal, 0, RIM.length - 1);
        d = nz(d, UP);
        Vec3 s1 = nz(d.cross(UP), new Vec3(1, 0, 0)), s2 = d.cross(s1);
        double sz = Mth.clamp(s * 0.25, 0.25, 1.2);
        shockRing(pal, at, sz * 1.2);
        int n = cnt(Mth.clamp(s * 6.0, 8.0, 28.0));
        for (int i = 0; i < n; i++) {
            double ang = Math.PI * 2 * i / Math.max(1, n) + r.nextDouble(-0.15, 0.15);
            Vec3 out = s1.scale(Math.cos(ang)).add(s2.scale(Math.sin(ang)));
            Spr p;
            switch (pal) {
                case 0 -> {   // Asta: black blade slivers flung out in a star, crimson cross-sparks between them
                    p = spr(at.add(out.scale(sz * 0.4)), out.scale(r.nextDouble(0.3, 0.5)), out, i % 3 == 0 ? K_CROSS : K_BLADE, (float) sz * 1.4F, (float) sz * 0.5F, r.nextInt(4, 7));
                    p.drag = 0.78F;
                    if (i % 3 == 0) { p.heat = 1.0F; p.twinkle = true; p.len = p.wid = (float) sz * 0.9F; } else { p.dark = true; p.darkAlpha = 1.0F; }
                }
                case 1, 10 -> {   // darkness: a ring of black crescents turning outward, violet glow between
                    p = spr(at.add(out.scale(sz * 0.5)), out.scale(0.2), out.cross(d), K_MOON, (float) sz * 1.6F, (float) sz * 0.7F, r.nextInt(6, 10));
                    p.drag = 0.85F; p.spin = 0.25F;
                    if (i % 2 == 0) { p.dark = true; p.darkAlpha = 1.0F; } else p.heat = 0.7F;
                }
                case 9 -> {   // devil: black thorns lashing out
                    p = spr(at, out.scale(r.nextDouble(0.35, 0.6)), out, K_BARB, (float) sz * 1.6F, (float) sz * 0.7F, r.nextInt(5, 8));
                    p.dark = true; p.darkAlpha = 1.0F; p.drag = 0.75F;
                }
                case 2, 12 -> {   // fire: a fountain of embers and tongues
                    Vec3 v = out.scale(r.nextDouble(0.1, 0.25)).add(0, r.nextDouble(0.1, 0.3), 0);
                    p = spr(at, v, v, i % 2 == 0 ? K_TONGUE : K_EMBER, (float) sz * (i % 2 == 0 ? 1.4F : 0.3F), (float) sz * (i % 2 == 0 ? 0.6F : 0.3F), r.nextInt(8, 14));
                    p.heat = 1.0F; p.drag = 0.9F; p.grav = -0.008F; p.flicker = true;
                }
                case 14 -> {   // Mereoleona: a lion's mane of curved flame claws flaring round
                    p = spr(at.add(out.scale(sz * 0.6)), out.scale(0.12).add(0, 0.03, 0), out, K_CLAW, (float) sz * 2.0F, (float) sz * 0.8F, r.nextInt(6, 10));
                    p.heat = 1.0F; p.flicker = true; p.pop = 2;
                }
                case 4, 13 -> {   // lightning: diamonds and crosses shot out, crackling
                    p = spr(at, out.scale(r.nextDouble(0.4, 0.8)), out, i % 2 == 0 ? K_BOLT : BIT[pal], (float) sz * (i % 2 == 0 ? 1.2F : 0.4F), (float) sz * (i % 2 == 0 ? 0.6F : 0.4F), r.nextInt(3, 6));
                    p.heat = 1.0F; p.drag = 0.7F; p.spin = 0.6F; p.flicker = true;
                }
                case 5 -> {   // Yuno: a spiral of feathers swept round by the wind
                    p = spr(at.add(out.scale(sz * 0.5)), out.cross(d).scale(0.25).add(out.scale(0.08)), out.cross(d), K_FEATHER, (float) sz * 0.8F, (float) sz * 0.45F, r.nextInt(12, 20));
                    p.heat = 0.85F; p.drag = 0.93F; p.curl = d; p.curlRate = 0.2F; p.spin = 0.15F;
                }
                case 7 -> {   // Klaus: steel plates bursting out like a shield breaking
                    p = spr(at.add(out.scale(sz * 0.3)), out.scale(r.nextDouble(0.15, 0.3)), out, K_PLATE, (float) sz * 0.5F, (float) sz * 0.5F, r.nextInt(8, 14));
                    p.dark = true; p.darkAlpha = 1.0F; p.drag = 0.88F; p.grav = -0.012F; p.spin = (float) r.nextDouble(-0.5, 0.5);
                }
                case 8 -> {   // gold: spinning coins
                    p = spr(at, out.scale(r.nextDouble(0.12, 0.25)).add(0, 0.12, 0), out, K_RING, (float) sz * 0.4F, (float) sz * 0.4F, r.nextInt(10, 16));
                    p.heat = 1.0F; p.grav = -0.015F; p.spin = 0.5F; p.twinkle = true;
                }
                case 11 -> {   // Nozel: a splash of mercury drops
                    Vec3 v = out.scale(r.nextDouble(0.1, 0.25)).add(0, r.nextDouble(0.08, 0.2), 0);
                    p = spr(at, v, new Vec3(0, -1, 0), K_DROP, (float) sz * 0.5F, (float) sz * 0.35F, r.nextInt(10, 16));
                    p.dark = true; p.darkAlpha = 1.0F; p.grav = -0.02F; p.drag = 0.95F;
                }
                case 15 -> {   // Zenon: a scatter of little bones
                    Vec3 v = out.scale(r.nextDouble(0.12, 0.3)).add(0, r.nextDouble(0.05, 0.18), 0);
                    p = spr(at, v, v, K_BONE, (float) sz * 0.7F, (float) sz * 0.32F, r.nextInt(10, 16));
                    p.dark = true; p.darkAlpha = 1.0F; p.grav = -0.018F; p.spin = (float) r.nextDouble(-0.6, 0.6);
                }
                default -> {   // light: a burst of twinkling stars
                    p = spr(at, out.scale(r.nextDouble(0.2, 0.4)), out, i % 2 == 0 ? K_STAR : K_STREAK, (float) sz * (i % 2 == 0 ? 0.8F : 1.4F), (float) sz * (i % 2 == 0 ? 0.8F : 0.12F), r.nextInt(4, 8));
                    p.heat = 1.0F; p.drag = 0.8F; p.twinkle = true;
                }
            }
            p.pal = pal;
            add(p);
        }
    }

    /**
     * Called by FxRenderer once a tick for every live effect. {@code at} = where it is now (moved along its velocity),
     * {@code perTick} = a copy re-sent every tick (only its first tick sheds sprites, so a held shape isn't doubled).
     */
    public static void fromFx(FxPacket p, Vec3 at, double age, double life, boolean perTick) {
        if (age < 0.0 || age > life) return;
        if (perTick && age >= 1.0) return;
        // hair-thin lines (threads, red threads, hairline cuts) shed a few sprites when they appear and none after:
        // they are drawn as crisp lines, and dozens of them held for seconds must not flood the screen with sprites
        if (!p.pixelOnly && ((p.shape == 2 && p.r <= 0.035F) || (p.shape == 0 && p.h <= 0.03F)) && age >= 1.0) return;
        boost = p.pixelOnly ? 1.35 : 1.0;
        try { fromFx0(p, at, age, life, perTick); } finally { boost = 1.0; }
    }

    private static void fromFx0(FxPacket p, Vec3 at, double age, double life, boolean perTick) {
        if (density() <= 0.0F) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int pal = Mth.clamp(p.palette, 0, RIM.length - 1);
        boolean burst = !perTick && age < 1.0;
        double frac = Mth.clamp(age / Math.max(1.0, life), 0.0, 1.0);
        Vec3 dn = nz(p.d, new Vec3(1, 0, 0)), un = nz(p.u, UP);
        if (burst && sigsThisTick < 6) {   // the moment it appears: this grimoire's own burst (a few a tick at most)
            Vec3 sat = null; Vec3 sd = dn; double ss = 0;
            switch (p.shape) {
                case 13, 1 -> { sat = p.c; ss = Math.max(p.r, p.r2); sd = dn; }
                case 0 -> { if (Math.max(p.r, p.r2) >= 1.5F) { sat = p.c; ss = Math.max(p.r, p.r2) * 0.6; sd = nz(dn.cross(un), UP); } }
                case 7, 8, 30 -> { if (p.r >= 0.8F && !perTick) { sat = at; ss = p.r; } }
                case 19, 20 -> { sat = at; ss = p.h > 0.5F ? p.r2 : p.r * 2.0; sd = nz(p.v, UP); }
                case 23 -> { if (p.r >= 0.08F) { sat = p.c.add(p.d); ss = Mth.clamp(p.r * 12.0, 1.0, 5.0); sd = UP; } }
                case 28 -> { sat = at; ss = p.r; sd = UP; }
                case 29 -> { sat = p.c; ss = p.r * 0.3; }
                case 33 -> { sat = p.c; ss = p.r; sd = nz(dn.cross(un), UP); }
                default -> {}
            }
            if (sat != null && ss > 0.2) { signature(pal, sat, sd, ss); sigsThisTick++; }
        }
        switch (p.shape) {
            case 13, 1 -> {   // slashes and crescents: sprites along the blade's arc, flying with it
                double R = p.shape == 13 ? p.r + (p.r2 - p.r) * (1.0 - Math.pow(1.0 - Mth.clamp(age / (life * 0.45), 0.0, 1.0), 3.0))
                        : p.r + (p.r2 - p.r) * frac;
                if (R < 0.05) return;
                Vec3 c = p.c.add(p.d.scale(p.speed * age));
                double back = p.shape == 13 ? 0.35 : 0.55;
                int n = cnt(Mth.clamp(R * 5.0, 6.0, 36.0) * (burst ? 2.0 : 1.0));
                for (int i = 0; i < n; i++) {
                    double fd = Math.toRadians(r.nextDouble(-70, 70)), cf = Math.cos(fd);
                    Vec3 o = c.add(un.scale(Math.sin(fd) * R)).add(dn.scale((cf - 1.0) * R * back - R * r.nextDouble(0.0, 0.12) * cf));
                    Vec3 tan = un.scale(cf).subtract(dn.scale(Math.sin(fd) * back));
                    one(pal, o, dn.scale(p.speed * 0.9), tan, Mth.clamp(R * 0.18 * Math.pow(cf, 0.8), 0.12, 0.9));
                }
            }
            case 2 -> {       // beams and trails
                double len = p.d.length();
                if (len < 0.05) return;
                // a pixel-only beam or trail keeps being drawn in sprites its whole life (it has no solid line under it)
                int n = cnt(age < 3.0 ? Mth.clamp(len * 1.5, 3.0, 24.0) : (p.pixelOnly ? Mth.clamp(len * 0.9, 2.0, 16.0) : Mth.clamp(len * 0.35, 0.5, 6.0)));
                double sz = Mth.clamp(p.r * 1.5 + 0.15, 0.15, 0.8);
                for (int i = 0; i < n; i++)
                    one(pal, p.c.add(p.d.scale(r.nextDouble())).add(rnd(r, p.r * 0.4)), dn.scale(0.05).add(rnd(r, 0.01)), dn, sz);
            }
            case 0 -> {       // walls and rings
                float R = p.r + (p.r2 - p.r) * (float) frac;
                if (R < 0.05F) return;
                Vec3 c = p.c.add(p.d.scale(p.speed * age));
                double span = p.a1 - p.a0;
                double grow = (p.r2 - p.r) / Math.max(1.0, life);
                Vec3 hx = dn.cross(un);
                hx = nz(hx, UP);
                int n = cnt(Mth.clamp(R * Math.abs(span) / 360.0 * 3.0 * Mth.clamp(p.h * 2.0, 0.3, 1.5), 2.0, 28.0) * (burst ? 1.6 : 1.0));
                for (int i = 0; i < n; i++) {
                    double th = Math.toRadians(p.a0 + span * r.nextDouble());
                    Vec3 out = dn.scale(Math.cos(th)).add(un.scale(Math.sin(th)));
                    Vec3 o = c.add(out.scale(R)).add(hx.scale(r.nextDouble(-1, 1) * Math.max(0.05, p.h)));
                    Vec3 tan = un.scale(Math.cos(th)).subtract(dn.scale(Math.sin(th)));
                    one(pal, o, out.scale(grow * 0.8).add(p.d.scale(p.speed)), tan, Mth.clamp(p.h * 0.8 + 0.2, 0.2, 0.8));
                }
            }
            case 7, 8, 30 -> { // blocks of metal, spikes, bones
                double hl = Math.max(0.1, p.r);
                int n = cnt(perTick ? 0.7 : (burst ? Mth.clamp(hl * 4.0, 3.0, 14.0) : Mth.clamp(hl * 0.6, 0.5, 4.0)));
                double sz = Mth.clamp(p.r2 * 2.5, 0.15, 0.6);
                for (int i = 0; i < n; i++) {
                    double t = p.shape == 8 ? r.nextDouble() : r.nextDouble(-1, 1);
                    one(pal, at.add(dn.scale(hl * t)).add(rnd(r, sz * 0.3)), p.v.scale(0.3), dn, sz);
                }
            }
            case 19, 20 -> {  // spheres and fireballs
                if (p.shape == 20 && p.h > 0.5F) {   // an explosion: one big burst, then it rolls on
                    double R = p.r + (p.r2 - p.r) * frac;
                    int n = cnt(burst ? Mth.clamp(p.r2 * 8.0, 12.0, 60.0) : Mth.clamp(p.r2 * 0.8, 1.0, 6.0));
                    for (int i = 0; i < n; i++) {
                        Vec3 dir = nz(rnd(r, 1.0), UP);
                        one(pal, at.add(dir.scale(R * (burst ? 0.3 : 1.0))), dir.scale(burst ? p.r2 * r.nextDouble(0.08, 0.16) : 0.03), dir, Mth.clamp(p.r2 * 0.12, 0.25, 0.9));
                    }
                    variant(p, at, age, life, burst, perTick, pal, dn, un);
                    return;
                }
                if (p.r < 0.08F) return;
                ball(pal, at, p.v, p.r);
                int n = cnt(p.r > 1.0F ? 5.0 : 3.0);
                Vec3 back = p.v.lengthSqr() > 1.0E-4 ? p.v.scale(-1) : null;
                for (int i = 0; i < n; i++) {
                    Vec3 dir = nz(rnd(r, 1.0), UP);
                    one(pal, at.add(dir.scale(p.r * 0.9)), p.v.scale(0.4).add(dir.scale(0.03)), back != null ? back : dir, Mth.clamp(p.r * 0.5, 0.15, 0.8));
                }
            }
            case 23 -> {      // lightning: sparks all along the channel, a splash where it lands
                double len = p.d.length();
                if (len < 0.05 || (!burst && age % 2 >= 1.0)) return;
                double sz = Mth.clamp(p.r * 6.0, 0.15, 0.6);
                int n = cnt(Mth.clamp(len * (burst ? 1.2 : 0.4), 1.0, 20.0));
                for (int i = 0; i < n; i++) one(pal, p.c.add(p.d.scale(r.nextDouble())), Vec3.ZERO, dn, sz);
                if (burst) for (int i = 0; i < cnt(6.0); i++) one(pal, p.c.add(p.d), rnd(r, 0.15).add(0, 0.12, 0), UP, sz);
            }
            case 28 -> {      // tornadoes: sprites riding the funnel round
                double R = p.r, H = Math.max(0.5, p.h);
                int n = cnt(Mth.clamp(R * H * 0.6, 6.0, 30.0));
                for (int i = 0; i < n; i++) {
                    double y = r.nextDouble() * H, rr = R * (0.18 + 0.82 * Math.pow(y / H, 0.85)), a = r.nextDouble(Math.PI * 2);
                    Vec3 tan = new Vec3(-Math.sin(a), 0.15, Math.cos(a));
                    one(pal == 0 ? 5 : pal, at.add(Math.cos(a) * rr, y, Math.sin(a) * rr), tan.scale(0.35).add(0, 0.05, 0), tan, 0.4);
                }
            }
            case 29 -> {      // gusts: streaks racing out along the cone with its front
                if (age > life * 0.7) return;
                double front = p.r * (1.0 - Math.pow(1.0 - Mth.clamp(age / (life * 0.55), 0.0, 1.0), 2.0));
                Vec3 s1 = nz(dn.cross(UP), new Vec3(1, 0, 0)), s2 = dn.cross(s1);
                double tanH = Math.tan(p.h);
                int n = cnt(burst ? 16.0 : 10.0);
                for (int i = 0; i < n; i++) {
                    double dist = front * r.nextDouble(0.6, 1.0), a = r.nextDouble(Math.PI * 2), rad = dist * tanH * r.nextDouble();
                    Vec3 o = p.c.add(dn.scale(dist)).add(s1.scale(Math.cos(a) * rad)).add(s2.scale(Math.sin(a) * rad));
                    one(pal, o, dn.scale(0.4), dn, 0.35);
                }
            }
            case 33 -> {      // cracks in space: shards bursting out along the fractures
                double R = Math.max(0.3, p.r);
                int n = cnt(burst ? Mth.clamp(R * 6.0, 10.0, 40.0) : 2.0);
                for (int i = 0; i < n; i++) {
                    double ang = r.nextDouble(Math.PI * 2);
                    Vec3 out = dn.scale(Math.cos(ang)).add(un.scale(Math.sin(ang)));
                    one(pal, p.c.add(out.scale(R * r.nextDouble())), out.scale(burst ? 0.06 : 0.01), out, Mth.clamp(R * 0.1, 0.2, 0.6));
                }
            }
            default -> {}
        }
        variant(p, at, age, life, burst, perTick, pal, dn, un);
    }

    /**
     * Effect variants, so moves of the same kind don't all look alike. The variant (0-3) comes from the move's own
     * numbers (shape, size, life, speed, palette), so a given move always gets the same look, but two different
     * slashes - or beams, rings, orbs - usually get different ones:
     *   slashes      0 plain, 1 twin edge, 2 afterimage trail left along its path, 3 shredding spiral
     *   beams        0 plain, 1 double helix wound round it, 2 pulse rings along it, 3 crackling side-sparks
     *   rings/walls  0 plain, 1 pillars of light rising off it, 2 an inner echo ring, 3 spikes thrown outward
     *   orbs         0 plain, 1 comet tail, 2 two sprites orbiting it, 3 a pulsing shell
     *   explosions   0 plain, 1 a second shockwave, 2 a rising column, 3 heavy debris
     *   blocks       0 plain, 1 a dust burst at the base, 2 a glint running along it, 3 shards sprayed off the tip
     *   lightning    0 plain, 1 a scorch ring where it lands, 2 extra forks of sparks, 3 static left hanging
     *   tornadoes    0 plain, 1 debris circling the foot, 2 a crown ring at the top, 3 crackling flecks
     */
    private static int variantOf(FxPacket p) {
        return Math.floorMod(p.shape * 31 + Math.round(p.r * 4) * 7 + Math.round(p.r2 * 4) * 13 + p.life * 3 + Math.round(p.speed * 10) * 5 + p.palette * 17, 4);
    }

    private static void variant(FxPacket p, Vec3 at, double age, double life, boolean burst, boolean perTick, int pal, Vec3 dn, Vec3 un) {
        int var = variantOf(p);
        if (var == 0) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double frac = Mth.clamp(age / Math.max(1.0, life), 0.0, 1.0);
        Vec3 s1 = nz(dn.cross(UP), new Vec3(1, 0, 0)), s2 = dn.cross(s1);
        int L = LONG[pal], B = BIT[pal];
        switch (p.shape) {
            case 13, 1 -> {
                double R = p.shape == 13 ? p.r + (p.r2 - p.r) * (1.0 - Math.pow(1.0 - Mth.clamp(age / (life * 0.45), 0.0, 1.0), 3.0)) : p.r + (p.r2 - p.r) * frac;
                if (R < 0.1) return;
                Vec3 c = p.c.add(p.d.scale(p.speed * age));
                double back = p.shape == 13 ? 0.35 : 0.55;
                int n = cnt(Mth.clamp(R * 2.5, 3.0, 16.0));
                for (int i = 0; i < n; i++) {
                    double fd = Math.toRadians(r.nextDouble(-70, 70)), cf = Math.cos(fd);
                    Vec3 tan = un.scale(cf).subtract(dn.scale(Math.sin(fd) * back));
                    float sz = (float) Mth.clamp(R * 0.16 * Math.pow(cf, 0.8), 0.1, 0.8);
                    if (var == 1) {          // twin edge: a second, thinner blade just behind the first
                        double R2 = R * 0.8;
                        Vec3 o = c.add(un.scale(Math.sin(fd) * R2)).add(dn.scale((cf - 1.0) * R2 * back - R * 0.18));
                        Spr s = spr(o, dn.scale(p.speed * 0.9), tan, L, sz * 1.6F, sz * 0.35F, r.nextInt(2, 4));
                        s.heat = 0.95F; s.pal = pal; add(s);
                    } else if (var == 2) {   // afterimage: ghosts of the blade left hanging where it passed
                        Vec3 o = c.add(un.scale(Math.sin(fd) * R)).add(dn.scale((cf - 1.0) * R * back));
                        Spr s = spr(o, Vec3.ZERO, tan, L, sz * 1.8F, sz * 0.5F, r.nextInt(7, 11));
                        s.pal = pal; s.drag = 0.5F;
                        if (element(pal) == DARK || element(pal) == METAL || element(pal) == BONE) { s.dark = true; s.darkAlpha = 0.55F; } else s.heat = 0.55F;
                        add(s);
                    } else {                 // shredding spiral: slivers torn off the edge, corkscrewing along its path
                        Vec3 o = c.add(un.scale(Math.sin(fd) * R)).add(dn.scale((cf - 1.0) * R * back));
                        Vec3 rad = nz(o.subtract(c), un);
                        Spr s = spr(o, dn.scale(p.speed * 0.5).add(rad.scale(0.12)), rad, K_STREAK, sz * 1.4F, sz * 0.12F, r.nextInt(5, 9));
                        s.heat = 1.0F; s.pal = pal; s.curl = dn; s.curlRate = 0.35F * (r.nextBoolean() ? 1 : -1); s.drag = 0.95F; add(s);
                    }
                }
            }
            case 2 -> {
                double len = p.d.length();
                if (len < 0.3 || (age > 3.0 && var != 1)) return;
                Vec3 bs1 = nz(dn.cross(UP), new Vec3(1, 0, 0)), bs2 = dn.cross(bs1);
                if (var == 1) {              // a double helix wound round the beam
                    int n = cnt(Mth.clamp(len * 0.8, 2.0, 14.0));
                    for (int i = 0; i < n; i++) {
                        double t = r.nextDouble(), ang = t * len * 2.2 + age * 0.8 + (r.nextBoolean() ? Math.PI : 0), rad = p.r + 0.25;
                        Vec3 o = p.c.add(p.d.scale(t)).add(bs1.scale(Math.cos(ang) * rad)).add(bs2.scale(Math.sin(ang) * rad));
                        Spr s = spr(o, Vec3.ZERO, dn, K_STREAK, 0.4F, 0.08F, 3);
                        s.heat = 0.95F; s.pal = pal; add(s);
                    }
                } else if (var == 2) {       // pulse rings strung along it as it fires
                    if (!burst) return;
                    int k = (int) Math.min(6, len / 2.0);
                    for (int i = 1; i <= k; i++) {
                        Spr s = spr(p.c.add(p.d.scale(i / (double) (k + 1))), Vec3.ZERO, dn, K_SHOCK, p.r * 2.5F + 0.4F, p.r * 2.5F + 0.4F, 5);
                        s.pal = pal; s.heat = 0.85F; s.grow = 1.15F; s.seed = 0; add(s);
                    }
                } else {                     // crackling sparks jumping off the sides
                    int n = cnt(Mth.clamp(len * 0.4, 1.0, 6.0));
                    for (int i = 0; i < n; i++) {
                        double ang = r.nextDouble(Math.PI * 2);
                        Vec3 out = bs1.scale(Math.cos(ang)).add(bs2.scale(Math.sin(ang)));
                        Spr s = spr(p.c.add(p.d.scale(r.nextDouble())).add(out.scale(p.r + 0.1)), out.scale(0.08), out, K_STREAK, 0.45F, 0.06F, r.nextInt(2, 4));
                        s.heat = 1.0F; s.pal = pal; add(s);
                    }
                }
            }
            case 0 -> {
                float R = p.r + (p.r2 - p.r) * (float) frac;
                if (R < 0.3F) return;
                Vec3 c = p.c.add(p.d.scale(p.speed * age));
                double span = p.a1 - p.a0;
                int n = cnt(Mth.clamp(R * Math.abs(span) / 360.0 * 1.2, 1.0, 10.0));
                for (int i = 0; i < n; i++) {
                    double th = Math.toRadians(p.a0 + span * r.nextDouble());
                    Vec3 out = dn.scale(Math.cos(th)).add(un.scale(Math.sin(th)));
                    if (var == 1) {          // pillars of light rising off it
                        Spr s = spr(c.add(out.scale(R)), new Vec3(0, r.nextDouble(0.12, 0.25), 0), UP, L, 0.9F, 0.18F, r.nextInt(5, 9));
                        s.heat = 0.9F; s.pal = pal; s.drag = 0.9F; add(s);
                    } else if (var == 2) {   // an inner echo ring
                        one(pal, c.add(out.scale(R * 0.6)), out.scale(0.02), un.scale(Math.cos(th)).subtract(dn.scale(Math.sin(th))), 0.3);
                    } else {                 // spikes thrown outward
                        Spr s = spr(c.add(out.scale(R)), out.scale(r.nextDouble(0.2, 0.35)), out, element(pal) == DARK ? L : K_STREAK, 0.8F, 0.16F, r.nextInt(3, 6));
                        s.pal = pal; s.drag = 0.8F;
                        if (element(pal) == DARK) { s.dark = true; s.darkAlpha = 1.0F; } else s.heat = 1.0F;
                        add(s);
                    }
                }
            }
            case 19, 20 -> {
                if (p.shape == 20 && p.h > 0.5F) {   // explosions
                    if (var == 1 && age >= 3.0 && age < 4.0) shockRing(pal, at, Mth.clamp(p.r2 * 0.4, 0.5, 3.0));
                    else if (var == 2 && age < 8.0) {   // a rising column
                        for (int i = 0; i < cnt(4.0); i++) {
                            Spr s = spr(at.add(rnd(r, p.r2 * 0.15)), new Vec3(0, r.nextDouble(0.15, 0.3), 0), UP, L, (float) (p.r2 * 0.35), (float) (p.r2 * 0.12), r.nextInt(6, 10));
                            s.heat = 0.9F; s.pal = pal; s.flicker = true; s.drag = 0.92F; add(s);
                        }
                    } else if (var == 3 && burst) {     // heavy debris
                        for (int i = 0; i < cnt(Mth.clamp(p.r2 * 3.0, 6.0, 20.0)); i++) {
                            Vec3 dir = nz(rnd(r, 1.0).add(0, 0.6, 0), UP);
                            Spr s = spr(at, dir.scale(r.nextDouble(0.25, 0.5)), dir, element(pal) == FLAME ? K_SHARD : B, 0.35F, 0.35F, r.nextInt(12, 20));
                            s.dark = true; s.darkAlpha = 1.0F; s.pal = pal; s.grav = -0.03F; s.drag = 0.97F; s.spin = 0.4F; add(s);
                        }
                    }
                    return;
                }
                if (p.r < 0.08F) return;
                if (var == 1 && p.v.lengthSqr() > 1.0E-3) {   // comet tail
                    Vec3 back = p.v.scale(-1).normalize();
                    for (int i = 0; i < cnt(2.0); i++) {
                        Spr s = spr(at.add(back.scale(p.r)).add(rnd(r, p.r * 0.3)), p.v.scale(0.2), back, L, p.r * 2.5F, p.r * 0.6F, r.nextInt(5, 9));
                        s.heat = 0.85F; s.pal = pal; s.flicker = true; add(s);
                    }
                } else if (var == 2) {       // two sprites orbiting it
                    for (int k = 0; k < 2; k++) {
                        double ang = age * 0.6 + k * Math.PI;
                        Vec3 off = new Vec3(Math.cos(ang), Math.sin(ang * 0.7) * 0.4, Math.sin(ang)).scale(p.r * 1.4 + 0.2);
                        Spr s = spr(at.add(off), p.v, new Vec3(-Math.sin(ang), 0, Math.cos(ang)), B, 0.22F, 0.22F, 4);
                        s.heat = 1.0F; s.pal = pal; s.drag = 1.0F; add(s);
                    }
                } else if (var == 3 && ((int) age) % 4 == 0 && age - Math.floor(age) < 1.0 && !perTick) {   // a pulsing shell
                    shockRing(pal, at, p.r * 1.6);
                }
            }
            case 7, 8, 30 -> {
                double hl = Math.max(0.1, p.r);
                if (var == 1 && burst) {     // a dust burst where it rises from
                    Vec3 base = p.shape == 8 ? at : at.subtract(dn.scale(hl));
                    for (int i = 0; i < cnt(8.0); i++) {
                        double ang = r.nextDouble(Math.PI * 2);
                        Vec3 out = new Vec3(Math.cos(ang), 0.3, Math.sin(ang));
                        Spr s = spr(base, out.scale(r.nextDouble(0.08, 0.18)), out, K_PUFF, 0.4F, 0.4F, r.nextInt(10, 16));
                        s.dark = true; s.darkAlpha = 0.5F; s.pal = pal; s.grow = 1.05F; s.drag = 0.88F; add(s);
                    }
                } else if (var == 2 && !perTick && age < 6.0) {   // a glint running along it
                    double t = (age / 6.0) * 2.0 - 1.0;
                    Spr s = spr(at.add(dn.scale(hl * (p.shape == 8 ? (t + 1) * 0.5 : t))), Vec3.ZERO, dn, K_STAR, 0.5F, 0.5F, 2);
                    s.heat = 1.0F; s.pal = pal; add(s);
                } else if (var == 3 && burst) {   // shards sprayed off the tip
                    Vec3 tip = at.add(dn.scale(hl));
                    for (int i = 0; i < cnt(6.0); i++) {
                        Vec3 v = dn.scale(0.2).add(rnd(r, 0.12));
                        Spr s = spr(tip, v, v, K_SHARD, 0.25F, 0.25F, r.nextInt(8, 13));
                        s.heat = 0.95F; s.pal = pal; s.grav = -0.012F; s.spin = 0.5F; add(s);
                    }
                }
            }
            case 23 -> {
                if (!burst) return;
                Vec3 land = p.c.add(p.d);
                if (var == 1) shockRing(pal, land.add(0, 0.05, 0), Mth.clamp(p.r * 10.0, 0.6, 3.0));
                else if (var == 2) {
                    for (int i = 0; i < cnt(5.0); i++) {
                        Vec3 a0 = p.c.add(p.d.scale(r.nextDouble(0.2, 0.9))), dir = nz(rnd(r, 1.0), UP);
                        Spr s = spr(a0.add(dir.scale(0.3)), dir.scale(0.05), dir, K_BOLT, 0.7F, 0.35F, 3);
                        s.heat = 1.0F; s.pal = pal; s.flicker = true; add(s);
                    }
                } else {
                    for (int i = 0; i < cnt(5.0); i++) {
                        Spr s = spr(land.add(rnd(r, 0.6)).add(0, 0.3, 0), new Vec3(0, 0.01, 0), UP, K_STAR, 0.3F, 0.3F, r.nextInt(10, 18));
                        s.heat = 1.0F; s.pal = pal; s.twinkle = true; add(s);
                    }
                }
            }
            case 28 -> {
                double R = p.r, H = Math.max(0.5, p.h);
                int tp = pal == 0 ? 5 : pal;
                if (var == 1) {
                    for (int i = 0; i < cnt(2.0); i++) {
                        double ang = r.nextDouble(Math.PI * 2), rr = R * 0.35;
                        Vec3 tan = new Vec3(-Math.sin(ang), 0.1, Math.cos(ang));
                        Spr s = spr(at.add(Math.cos(ang) * rr, 0.2, Math.sin(ang) * rr), tan.scale(0.4), tan, K_SHARD, 0.3F, 0.3F, r.nextInt(8, 12));
                        s.dark = true; s.darkAlpha = 1.0F; s.pal = 15; s.curl = UP; s.curlRate = 0.45F; s.spin = 0.6F; s.drag = 0.97F; add(s);
                    }
                } else if (var == 2) {
                    if (((int) age) % 6 == 0 && age - Math.floor(age) < 1.0) {
                        Spr s = spr(at.add(0, H, 0), Vec3.ZERO, UP, K_SHOCK, (float) R, (float) R, 6);
                        s.pal = tp; s.heat = 0.8F; s.grow = 1.12F; s.seed = 0; add(s);
                    }
                } else {
                    for (int i = 0; i < cnt(1.5); i++) {
                        double y = r.nextDouble() * H, ang = r.nextDouble(Math.PI * 2), rr = R * (0.18 + 0.82 * Math.pow(y / H, 0.85));
                        Spr s = spr(at.add(Math.cos(ang) * rr, y, Math.sin(ang) * rr), Vec3.ZERO, rnd(r, 1.0), K_BOLT, 0.6F, 0.3F, 2);
                        s.heat = 1.0F; s.pal = tp == 5 ? 13 : tp; s.flicker = true; add(s);
                    }
                }
            }
            default -> {}
        }
    }

    // ------------------------------------------------------------------ forms and auras worn by an entity
    private static final int BODY = 0, HANDS = 1, RIGHT_HAND = 2, SHELL = 3, ZONE = 4, ORBIT = 5, RING = 6, GLINT = 7, WING = 8, CROWN = 9, MANTLE = 10;

    private static final class Att { int id, shape, pal, mode; double end; float r, h; float rate; }

    private static final java.util.Map<Long, Att> ATTACH = new java.util.HashMap<>();

    /** Aura-outline colours (AuraOutlineLayer) to palettes. */
    /** Aura outline colour (AuraOutlineLayer) -> sprite palette. Every outline needs its own entry: a missing one fell back to
     *  the last (Devil Union) and the Scarlet Moon shed black-and-red devil sprites. */
    private static final int[] OUTLINE_PAL = {10, 9, 11, 13, 14, 0, 15, 6, 9, 8, 18, 31, 20};
    /** Grimoire (GrimoireMastery.BOOKS order) to palette: Asta, Yami, Yuno, Nozel, Magna, Luck, Mereoleona, Zenon, Klaus. */
    private static final int[] BOOK_PAL = {0, 10, 5, 11, 2, 13, 14, 15, 7, 16, 17, 19, 20, 21, 22, 23, 25, 26, 29, 8, 18, 1, 5, 10, 21, 8, 7, 0, 21, 8, 20, 19, 2, 4};

    /** Ki Sense colours by creature: hostile red, passive green (wind), neutral yellow (lightning), players blue. */
    private static final int[] KI_PAL = {14, 5, 4, 13};

    private static int flamePal(int p) { return p == 12 || p == 14 ? p : 2; }

    /**
     * Called by FxRenderer for every packet: forms, auras and held weapons (which have no shape of their own in the
     * effect list) start or stop shedding sprites here.
     */
    public static void onPacket(FxPacket p) {
        int mode, pal;
        float rate;
        switch (p.shape) {
            case 4 -> { mode = GLINT; pal = KI_PAL[p.palette & 3]; rate = 0.35F; }                 // Ki Sense mark, in the creature's colour
            case 6 -> { mode = BODY; pal = 9; rate = 3.0F; }                                       // Devil Union
            case 11 -> { mode = BODY; pal = 7; rate = 1.2F; }                                      // Knight's Armor
            case 14 -> { mode = BODY; pal = OUTLINE_PAL[Mth.clamp(p.palette, 0, OUTLINE_PAL.length - 1)]; rate = 1.0F; }   // aura outline
            case 15 -> { mode = SHELL; pal = 10; rate = 4.0F; }                                    // Black Cocoon
            case 16 -> { mode = BODY; pal = 10; rate = 2.0F; }                                     // Black Blade
            case 17 -> { mode = ZONE; pal = Mth.clamp(p.palette, 0, RIM.length - 1); rate = 1.0F; }            // Mana Zone
            case 18 -> { mode = BODY; pal = 5; rate = 1.0F; }                                      // Spirit Dive
            case 21 -> { mode = BODY; pal = flamePal(p.palette); rate = 1.2F; }                    // burning
            case 22 -> { mode = HANDS; pal = flamePal(p.palette); rate = 2.0F; }                   // Flame Knuckles
            case 24 -> { mode = BODY; pal = 13; rate = 2.5F; }                                     // Thunder God Mode
            case 25 -> { mode = HANDS; pal = 13; rate = 1.5F; }                                    // thunder gloves
            case 27 -> { mode = BODY; pal = 14; rate = 3.0F; }                                     // Hellfire Incarnate
            case 31 -> { mode = BODY; pal = 15; rate = 2.0F; }                                     // Beelzebub
            case 35 -> { mode = BODY; pal = BOOK_PAL[Mth.clamp(p.palette, 0, BOOK_PAL.length - 1)]; rate = 2.5F; }   // Surpassing Limits
            case 36 -> { mode = ORBIT; pal = p.palette == 1 ? 11 : (p.palette == 0 ? 5 : 7); rate = 3.0F; }        // weapon rings
            case 37 -> { mode = RIGHT_HAND; pal = 15; rate = 1.5F; }                               // bone sword
            case 38 -> { mode = RING; pal = 15; rate = 2.0F; }                                     // bone whips
            case 39 -> { mode = HANDS; pal = 14; rate = 2.5F; }                                    // Ignis guard fists
            default -> { return; }
        }
        long key = (long) p.id << 8 | (p.shape & 0xFF);
        long wingKey = (long) p.id << 8 | 106;   // Devil Union's wing sheds the same sprites as its aura
        long crownKey = (long) p.id << 8 | 107, mantleKey = (long) p.id << 8 | 108;   // Devil Union's crown and cloak
        if (p.shape == 6 && p.life <= 0) { ATTACH.remove(wingKey); ATTACH.remove(crownKey); ATTACH.remove(mantleKey); }
        if (p.life <= 0) { ATTACH.remove(key); return; }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Att a = new Att();
        a.id = p.id; a.shape = p.shape; a.pal = pal; a.mode = mode; a.rate = rate;
        a.end = mc.level.getGameTime() + p.life;
        a.r = p.shape == 36 ? p.r : (p.shape == 37 ? Math.max(0.6F, p.r) : p.r);
        a.h = p.shape == 36 ? p.r2 : p.h;
        ATTACH.put(key, a);
        if (p.shape == 6) {
            Att w = new Att();
            w.id = p.id; w.shape = 106; w.pal = 9; w.mode = WING; w.rate = 3.0F; w.end = a.end;
            ATTACH.put(wingKey, w);
            Att c = new Att();
            c.id = p.id; c.shape = 107; c.pal = 9; c.mode = CROWN; c.rate = 5.0F; c.end = a.end;
            ATTACH.put(crownKey, c);
            Att k = new Att();
            k.id = p.id; k.shape = 108; k.pal = 9; k.mode = MANTLE; k.rate = 6.0F; k.end = a.end;
            ATTACH.put(mantleKey, k);
        }
    }

    private static void tickAttached(Minecraft mc) {
        if (ATTACH.isEmpty()) return;
        double now = mc.level.getGameTime();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (Iterator<Att> it = ATTACH.values().iterator(); it.hasNext(); ) {
            Att a = it.next();
            if (now > a.end) { it.remove(); continue; }
            net.minecraft.world.entity.Entity e = mc.level.getEntity(a.id);
            if (e == null) continue;
            boolean self = e == mc.player && mc.options.getCameraType().isFirstPerson();
            float h = e.getBbHeight(), w = Math.max(0.3F, e.getBbWidth() * 0.6F);
            Vec3 base = e.position(), mv = e.getDeltaMovement();
            int el = element(a.pal);
            Vec3 rise = el == FLAME || el == DARK ? new Vec3(0, 0.04, 0) : (el == METAL || el == BONE ? Vec3.ZERO : new Vec3(0, 0.015, 0));
            int n = cnt(a.rate * (self && (a.mode == BODY || a.mode == GLINT || a.mode == HANDS || a.mode == RIGHT_HAND) ? 0.3 : 1.0));   // lighter in your own view
            if (a.shape == 18) {   // Spirit Dive: its wind only shows while you move
                double sp = Math.sqrt((e.getX() - e.xo) * (e.getX() - e.xo) + (e.getZ() - e.zo) * (e.getZ() - e.zo) + (e.getY() - e.yo) * (e.getY() - e.yo));
                if (sp < 0.06) n = 0;
                else mv = new Vec3(e.getX() - e.xo, e.getY() - e.yo, e.getZ() - e.zo).scale(-0.4);
            }
            for (int i = 0; i < n; i++) {
                switch (a.mode) {
                    case BODY, GLINT -> {
                        Vec3 o = base.add(r.nextDouble(-w, w), r.nextDouble(0.05, h), r.nextDouble(-w, w));
                        one(a.pal, o, mv.scale(0.5).add(rise), el == WIND ? new Vec3(-(o.z - base.z), 0.2, o.x - base.x) : UP, a.mode == GLINT ? 0.18 : 0.3);
                    }
                    case HANDS, RIGHT_HAND -> {
                        double yaw = e instanceof net.minecraft.world.entity.LivingEntity le ? le.yBodyRot : e.getYRot();
                        if (e instanceof net.minecraft.world.entity.player.Player pl) yaw -= FxRenderer.animYawDeg(pl, 1.0F);
                        double by = Math.toRadians(yaw);
                        Vec3 fw = new Vec3(-Math.sin(by), 0.0, Math.cos(by)), lf = new Vec3(Math.cos(by), 0.0, Math.sin(by));
                        int s = a.mode == RIGHT_HAND ? -1 : (r.nextBoolean() ? 1 : -1);
                        Vec3 hand = base.add(0, h * 0.4, 0).add(lf.scale(0.38 * s)).add(fw.scale(0.08));
                        if (a.mode == RIGHT_HAND) hand = hand.add(fw.scale(r.nextDouble(0.2, a.r)));   // along the blade
                        one(a.pal, hand.add(rnd(r, 0.08)), mv.scale(0.6).add(rise), a.mode == RIGHT_HAND ? fw : UP, 0.22);
                    }
                    case SHELL -> {
                        Vec3 nrm = nz(rnd(r, 1.0), UP);
                        one(a.pal, base.add(0, h * 0.5, 0).add(nrm.scale(Math.max(1.0, a.r))), nrm.scale(0.02), nrm.cross(UP), 0.4);
                    }
                    case ZONE -> {
                        double R = Math.max(2.0, a.r), ang = r.nextDouble(Math.PI * 2), rr = R * Math.sqrt(r.nextDouble());
                        int zn = cnt(Mth.clamp(R * 0.3, 2.0, 8.0) / Math.max(1.0F, a.rate));
                        for (int k = 0; k < zn; k++) {
                            Vec3 o = base.add(Math.cos(ang) * rr, r.nextDouble(0.1, 1.6), Math.sin(ang) * rr);
                            one(a.pal, o, new Vec3(0, 0.03, 0), UP, 0.35);
                            ang = r.nextDouble(Math.PI * 2); rr = R * Math.sqrt(r.nextDouble());
                        }
                    }
                    case ORBIT -> {
                        double ang = r.nextDouble(Math.PI * 2), R = Math.max(0.8, a.r);
                        Vec3 tan = new Vec3(-Math.sin(ang), 0, Math.cos(ang));
                        one(a.pal, base.add(Math.cos(ang) * R, a.h > 0 ? a.h : h * 0.6, Math.sin(ang) * R), mv.scale(0.6).add(tan.scale(0.1)), tan, 0.3);
                    }
                    case WING -> {   // across Asta's wing: out to his left and up from the shoulder blade
                        double yaw = e instanceof net.minecraft.world.entity.LivingEntity le ? le.yBodyRot : e.getYRot();
                        if (e instanceof net.minecraft.world.entity.player.Player pl) yaw -= FxRenderer.animYawDeg(pl, 1.0F);
                        double by = Math.toRadians(yaw);
                        Vec3 fw = new Vec3(-Math.sin(by), 0.0, Math.cos(by)), lf = new Vec3(Math.cos(by), 0.0, Math.sin(by));
                        double u = r.nextDouble(), v = r.nextDouble();
                        Vec3 o = base.add(0, h * 0.78, 0).subtract(fw.scale(0.3)).add(lf.scale(0.1 + u * 1.7)).add(0, -0.6 + v * (0.9 + u * 0.9), 0);
                        one(a.pal, o, mv.scale(0.5).add(0, 0.02, 0), lf.scale(0.6).add(0, 1, 0), 0.28);
                    }
                    case CROWN -> {   // a crown of black flame: tongues standing round the head, three taller points
                        if (self) break;   // never in your own view
                        int k = r.nextInt(10);
                        boolean tall = k % 3 == 0 && k < 9;
                        double ang = Math.PI * 2.0 * k / 10.0 + now * 0.025 + r.nextDouble(-0.12, 0.12);
                        Vec3 o = base.add(Math.cos(ang) * 0.27, h + 0.03, Math.sin(ang) * 0.27);
                        double up = tall ? r.nextDouble(0.035, 0.06) : r.nextDouble(0.018, 0.035);
                        Spr f = spr(o, mv.scale(0.6).add(0, up, 0), UP, K_TONGUE, tall ? 0.26F : 0.17F, tall ? 0.09F : 0.07F, r.nextInt(5, 9));
                        f.dark = r.nextFloat() < 0.6F; f.darkAlpha = 1.0F; f.heat = 0.55F; f.drag = 0.86F; f.pal = 9;
                        add(f);
                    }
                    case MANTLE -> {  // a cloak of black flame down the back, streaming out behind as you move
                        double yaw = e instanceof net.minecraft.world.entity.LivingEntity le ? le.yBodyRot : e.getYRot();
                        if (e instanceof net.minecraft.world.entity.player.Player pl) yaw -= FxRenderer.animYawDeg(pl, 1.0F);
                        double by = Math.toRadians(yaw);
                        Vec3 fw = new Vec3(-Math.sin(by), 0.0, Math.cos(by)), lf = new Vec3(Math.cos(by), 0.0, Math.sin(by));
                        double sp = Math.min(0.5, Math.sqrt(mv.x * mv.x + mv.z * mv.z));
                        double u = r.nextDouble(), side = r.nextDouble(-1, 1);
                        // from the shoulders (u = 0) down to the calves (u = 1), widening and pushed back by speed
                        Vec3 o = base.add(0, h * (0.82 - 0.62 * u), 0).subtract(fw.scale(0.2 + u * (0.18 + sp * 1.6)))
                                .add(lf.scale(side * (0.22 + 0.18 * u)));
                        Vec3 vel = fw.scale(-0.015 - sp * 0.4).add(0, 0.006 - 0.012 * u, 0).add(lf.scale(Math.sin(now * 0.2 + u * 4) * 0.01));
                        Spr c = spr(o, vel, new Vec3(0, -1, 0).subtract(fw.scale(0.4 + sp * 2)), r.nextFloat() < 0.7F ? K_TONGUE : K_STREAK,
                                (float) r.nextDouble(0.22, 0.36), (float) r.nextDouble(0.1, 0.16), r.nextInt(4, 8));
                        c.dark = r.nextFloat() < 0.75F; c.darkAlpha = self ? 0.5F : 0.95F; c.heat = 0.5F; c.drag = 0.9F; c.pal = 9;
                        add(c);
                    }
                    case RING -> {
                        double ang = r.nextDouble(Math.PI * 2), R = Math.max(1.0, a.r) * r.nextDouble(0.3, 0.9);
                        one(a.pal, base.add(Math.cos(ang) * R, h * r.nextDouble(0.3, 0.8), Math.sin(ang) * R), mv.scale(0.5), new Vec3(Math.cos(ang), 0, Math.sin(ang)), 0.3);
                    }
                    default -> {}
                }
            }
        }
    }

    /** Sky Splitter is all red: the sprites made since {@code first} lose their black and their white-hot. */
    private static void redOnly(int first) {
        for (int i = first; i < SPRITES.size(); i++) {
            Spr p = SPRITES.get(i);
            p.pal = 0;
            if (p.dark) { p.dark = false; p.heat = 0.45F; }
            p.heat = Math.min(p.heat, 0.5F);   // crimson tops out red, never pink-white
        }
    }

    /** The hottest a palette may glow: darkness never turns white (no white-hot cores or edges on Yami's moves). */
    private static float maxHeat(int pal) { return pal == 1 || pal == 10 ? 0.68F : (pal == 18 ? 0.5F : 1.0F); }

    private static void add(Spr p) {
        if (p.heat > maxHeat(p.pal)) p.heat = maxHeat(p.pal);
        if (p.pop == 0 && p.grow == 1.0F) p.pop = 1.5F;   // every sprite scales in smoothly - nothing pops into being
        if (SPRITES.size() >= (priority ? max() * 5 / 3 : max())) return;   // full: drop the newest (a move's blade gets extra room)
        if (p.age == 0 && p.vel.lengthSqr() > 1.0E-4) {   // spread along the path it moved this tick, so no stepping
            double u = ThreadLocalRandom.current().nextDouble();
            p.pos = p.pos.subtract(p.vel.scale(u));
            p.prev = p.pos;
        }
        SPRITES.add(p);
    }

    // ------------------------------------------------------------------ ticking
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { SLASHES.clear(); SPRITES.clear(); ATTACH.clear(); return; }
        sigsThisTick = 0;
        tickAttached(mc);
        double now = mc.level.getGameTime();
        for (Iterator<Slash> it = SLASHES.iterator(); it.hasNext(); ) {
            Slash s = it.next();
            double age = now - s.born;
            if (s.dead || age > s.life) { it.remove(); continue; }
            int first = SPRITES.size();
            priority = true;
            try { emit(s, age, false); } finally { priority = false; }
            if (s.pal == 0) redOnly(first);
        }
        // compact in place (one pass) instead of removing one at a time - thousands of sprites die every second
        int keep = 0;
        for (int idx = 0, size = SPRITES.size(); idx < size; idx++) {
            Spr p = SPRITES.get(idx);
            if (++p.age > p.life) continue;
            SPRITES.set(keep++, p);
            p.prev = p.pos;
            p.pos = p.pos.add(p.vel);
            p.vel = p.vel.scale(p.drag);
            if ((p.kind == 2 || p.kind == 1) && p.grav == 0) p.vel = p.vel.add(0, -0.012, 0);   // sparks fall a little
            if (p.grav != 0) p.vel = p.vel.add(0, p.grav, 0);   // flames rise, dust and shards settle
            if (p.curl != null) {   // smoke curling round the path: turn its velocity about the travel axis
                double ca = Math.cos(p.curlRate), sa = Math.sin(p.curlRate);
                Vec3 k = p.curl, v = p.vel;
                p.vel = v.scale(ca).add(k.cross(v).scale(sa)).add(k.scale(k.dot(v) * (1 - ca)));
                p.axis = p.vel.lengthSqr() > 1.0E-8 ? p.vel.normalize() : p.axis;
            }
            if (!p.dark) p.heat = Math.max(0.0F, p.heat - 0.9F / p.life);
        }
        if (keep < SPRITES.size()) SPRITES.subList(keep, SPRITES.size()).clear();
    }

    // ------------------------------------------------------------------ drawing
    /*
     * Every sprite texture is a 4-frame strip (top to bottom): whole, then dissolving away in an ordered dither -
     * the classic pixel-art fade. Each kind also has a "core" strip (only its white-hot pixels) and a "rim" strip
     * (a one-pixel outline just outside the shape). Layers, back to front:
     *   bloom      a soft banded halo behind hot glowing sprites
     *   solid      black / steel / bone sprites (normal blending)
     *   rim        a glowing outline round the solid ones in the palette colour - anti magic's black with a red edge
     *   glow       the glowing sprites in their palette colour
     *   core       their white-hot pixels drawn over the top
     *   soft rim   a faint coloured outline round the glowing ones
     */
    private static final int L_SOLID = 0, L_SOLID_RIM = 1, L_GLOW = 2, L_CORE = 3, L_GLOW_RIM = 4;
    @SuppressWarnings("unchecked")
    private static final List<Spr>[] BY_KIND = new List[TEX.length];
    static { for (int i = 0; i < BY_KIND.length; i++) BY_KIND[i] = new ArrayList<>(); }

    public static void render(PoseStack ps, Camera camera, MultiBufferSource.BufferSource buf, float pt) {
        if (SPRITES.isEmpty()) return;
        Vec3 cam = camera.getPosition();
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();
        float opacity = com.ironspellgrimoire.AddonClientConfig.effectOpacity();
        for (List<Spr> l : BY_KIND) l.clear();
        for (Spr p : SPRITES) BY_KIND[Mth.clamp(p.kind, 0, TEX.length - 1)].add(p);
        RenderType bt = RenderType.eyes(TEX[K_GLOW]);
        VertexConsumer bv = null;
        for (Spr p : SPRITES) {
            if (!com.ironspellgrimoire.AddonClientConfig.glowPass()) break;
            if (p.dark || p.heat < 0.45F || p.kind == K_GLOW || p.kind == K_SHOCK) continue;
            if (bv == null) bv = buf.getBuffer(bt);
            bloom(bv, m, n, cam, p, pt, opacity);
        }
        if (bv != null) buf.endBatch(bt);   // (skipped entirely on LOW quality)
        for (int layer = 0; layer <= L_GLOW_RIM; layer++) {
            for (int kind = 0; kind < TEX.length; kind++) {
                List<Spr> list = BY_KIND[kind];
                if (list.isEmpty()) continue;
                ResourceLocation tx = layer == L_CORE ? TEX_CORE[kind] : (layer == L_SOLID_RIM || layer == L_GLOW_RIM ? TEX_RIM[kind] : TEX[kind]);
                RenderType rt = layer == L_SOLID ? RenderType.entityTranslucentEmissive(tx) : RenderType.eyes(tx);
                VertexConsumer vc = null;
                for (Spr p : list) {
                    boolean solidLayer = layer == L_SOLID || layer == L_SOLID_RIM;
                    if (p.dark != solidLayer) continue;
                    if (layer == L_CORE && p.heat < 0.5F) continue;
                    if (vc == null) vc = buf.getBuffer(rt);
                    draw(vc, m, n, cam, p, pt, layer, opacity);
                }
                if (vc != null) buf.endBatch(rt);
            }
        }
    }

    /** Sprites drawn as a square (not stretched along their travel). */
    private static boolean square(int k) {
        return k == K_SHARD || k == K_EMBER || k == K_CROSS || k == K_STAR || k == K_PLATE || k == K_RING || k == K_PUFF || k == K_DIAMOND || k == K_GLOW || k == K_SHOCK || k == K_COTTON || k == K_TUFT;
    }

    private static float fadeOf(Spr p, Vec3 pos, Vec3 cam, float pt, float opacity) {
        float fade = opacity;   // full strength all its life: the dither dissolve takes it away (no fading in or out)
        double dc = pos.distanceTo(cam);
        // never smother the camera: a sprite fades out as it nears you, the bigger it is the earlier
        double big = Math.max(p.len, p.wid) * p.scaleNow(pt);
        double near = 0.8 + big * 1.6, full = near + 1.6 + big;
        if (dc < full) fade *= (float) Mth.clamp((dc - near) / (full - near), 0.0, 1.0);
        if (p.see) fade *= 0.35F;
        fade *= p.alpha;
        if (p.twinkle) fade *= 0.55F + 0.45F * (float) Math.abs(Math.sin((p.age + pt) * 1.3 + p.seed));
        return fade;
    }

    /** Which frame of the dissolve: whole for the first part of its life, then dithering away. */
    private static int frameOf(Spr p, float pt) {
        float life = (p.age + pt) / (float) p.life;
        return life < 0.45F ? 0 : Math.min(3, 1 + (int) ((life - 0.45F) / 0.55F * 3.0F));
    }

    private static void bloom(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Spr p, float pt, float opacity) {
        Vec3 pos = p.prev.add(p.pos.subtract(p.prev).scale(pt));
        if (pos.distanceToSqr(cam) > 48.0 * 48.0) return;   // the soft glow only up close
        float fade = fadeOf(p, pos, cam, pt, opacity) * 0.32F * Mth.clamp((p.heat - 0.35F) * 2.0F, 0.0F, 1.0F);
        if (fade <= 0.01F) return;
        Vec3 toCam = cam.subtract(pos).normalize();
        Vec3 sx = nz(toCam.cross(UP), new Vec3(1, 0, 0)), sy = sx.cross(toCam);
        double s = Math.max(p.len, p.wid) * (square(p.kind) ? 1.4 : 0.8) * p.scaleNow(pt);
        float[] c = glow(p.pal, Math.min(p.heat, 0.7F));
        quad(vc, m, n, cam, pos, sy.scale(s), sx.scale(s), c[0] * fade, c[1] * fade, c[2] * fade, 1.0F, false, 0.0F, 0.25F);
    }

    private static void draw(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Spr p, float pt, int layer, float opacity) {
        Vec3 pos = p.prev.add(p.pos.subtract(p.prev).scale(pt));
        if (pos.distanceToSqr(cam) > 80.0 * 80.0) return;   // too far to see a pixel of it
        float fade = fadeOf(p, pos, cam, pt, opacity);
        if (fade <= 0.01F) return;
        float life = (p.age + pt) / (float) p.life;
        float heat = p.dark ? p.heat : Math.max(0.0F, p.heat - 0.9F / p.life * pt);   // keeps cooling between ticks
        Vec3 toCam = cam.subtract(pos);
        Vec3 ax, side;
        float scale = p.scaleNow(pt);
        float len, wid;
        if (square(p.kind)) {   // squares face the camera and spin about the view direction
            Vec3 tc = toCam.normalize();
            Vec3 sx = nz(tc.cross(UP), new Vec3(1, 0, 0)), sy = sx.cross(tc);
            double ang = p.spin * (p.age + pt) + p.seed;
            ax = sy.scale(Math.cos(ang)).add(sx.scale(Math.sin(ang)));
            side = sx.scale(Math.cos(ang)).subtract(sy.scale(Math.sin(ang)));
            len = p.len * scale; wid = p.wid * scale;
        } else {                // stretched along its own direction, turned to face the camera around it
            ax = p.axis;
            if (p.spin != 0) {   // tumbling: the long axis turns round the view direction
                Vec3 k = toCam.normalize();
                double a0 = p.spin * (p.age + pt), ca = Math.cos(a0), sa = Math.sin(a0);
                ax = ax.scale(ca).add(k.cross(ax).scale(sa)).add(k.scale(k.dot(ax) * (1 - ca)));
            }
            side = ax.cross(toCam);
            if (side.lengthSqr() < 1.0E-8) side = ax.cross(UP);
            if (side.lengthSqr() < 1.0E-8) side = new Vec3(1, 0, 0);
            side = side.normalize();
            float stretch = p.kind == K_STREAK || p.kind == K_TONGUE || p.kind == K_CLAW || p.kind == K_WISP || p.kind == K_FIBER ? 0.7F + 0.6F * (1.0F - life) : 1.0F;
            len = p.len * stretch * scale; wid = p.wid * scale;
        }
        float r, g, bl, al = 1.0F;
        switch (layer) {
            case L_SOLID -> { float[] sc = SOLID[Mth.clamp(p.pal, 0, RIM.length - 1)]; r = Math.min(1F, sc[0] * p.tone); g = Math.min(1F, sc[1] * p.tone); bl = Math.min(1F, sc[2] * p.tone); al = p.darkAlpha * fade; }
            case L_SOLID_RIM -> {   // the glowing edge round a solid sprite: strong on black magic, a faint sheen on metal and bone
                float k = (element(p.pal) == DARK ? 0.95F : (p.pal == 11 ? 0.18F : 0.45F)) * fade * Math.min(1.0F, p.darkAlpha + 0.2F);
                float[] c = glow(p.pal, 0.6F);
                r = c[0] * k; g = c[1] * k; bl = c[2] * k;
            }
            case L_CORE -> { float[] c = glow(p.pal, maxHeat(p.pal)); float k = fade * Mth.clamp((heat - 0.5F) * 2.5F, 0.0F, 1.0F); r = c[0] * k; g = c[1] * k; bl = c[2] * k; }
            case L_GLOW_RIM -> { float[] c = glow(p.pal, Math.min(heat, 0.5F)); float k = 0.4F * fade; r = c[0] * k; g = c[1] * k; bl = c[2] * k; }
            default -> { float[] c = glow(p.pal, Math.min(heat, 0.88F)); r = c[0] * fade; g = c[1] * fade; bl = c[2] * fade; }
        }
        if (r + g + bl < 0.01F && layer != L_SOLID) return;
        int fr = frameOf(p, pt);
        // flames flicker: mirrored every other couple of ticks, like a two-frame flip-book
        boolean flip = p.flicker && (((p.age + p.seed) >> 1) & 1) == 1;
        // the rim layers sit one pixel outside the shape, so they are drawn on the same quad (the strips are padded)
        quad(vc, m, n, cam, pos, ax.scale(len * 0.5), side.scale(wid * 0.5), r, g, bl, al, flip, fr * 0.25F, fr * 0.25F + 0.25F);
    }

    /** A double-faced quad: {@code a} = half its length (texture top = +a), {@code b} = half its width; v0..v1 = the frame.
     *  Done in plain numbers (no temporary vectors): this runs for thousands of sprites every frame. */
    private static void quad(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 pos, Vec3 a, Vec3 b, float r, float g, float bl, float al, boolean flip, float v0, float v1) {
        float u0 = flip ? 1 : 0, u1 = flip ? 0 : 1;
        double px = pos.x - cam.x, py = pos.y - cam.y, pz = pos.z - cam.z;
        float x0 = (float) (px - a.x - b.x), y0 = (float) (py - a.y - b.y), z0 = (float) (pz - a.z - b.z);   // back, left
        float x1 = (float) (px - a.x + b.x), y1 = (float) (py - a.y + b.y), z1 = (float) (pz - a.z + b.z);   // back, right
        float x2 = (float) (px + a.x + b.x), y2 = (float) (py + a.y + b.y), z2 = (float) (pz + a.z + b.z);   // front, right
        float x3 = (float) (px + a.x - b.x), y3 = (float) (py + a.y - b.y), z3 = (float) (pz + a.z - b.z);   // front, left
        vf(vc, m, n, x0, y0, z0, r, g, bl, al, u0, v1); vf(vc, m, n, x1, y1, z1, r, g, bl, al, u1, v1);
        vf(vc, m, n, x2, y2, z2, r, g, bl, al, u1, v0); vf(vc, m, n, x3, y3, z3, r, g, bl, al, u0, v0);
        vf(vc, m, n, x3, y3, z3, r, g, bl, al, u0, v0); vf(vc, m, n, x2, y2, z2, r, g, bl, al, u1, v0);
        vf(vc, m, n, x1, y1, z1, r, g, bl, al, u1, v1); vf(vc, m, n, x0, y0, z0, r, g, bl, al, u0, v1);
    }

    private static void vf(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, float r, float g, float b, float a, float u, float t) {
        vc.vertex(m, x, y, z).color(r, g, b, a).uv(u, t).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(n, 0, 1, 0).endVertex();
    }

    private static void v(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 w, float r, float g, float b, float a, float u, float t) {
        vc.vertex(m, (float) (w.x - cam.x), (float) (w.y - cam.y), (float) (w.z - cam.z)).color(r, g, b, a).uv(u, t)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(n, 0, 1, 0).endVertex();
    }
}
