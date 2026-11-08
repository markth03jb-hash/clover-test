package com.example.arcaneaddon.client;

import com.example.arcaneaddon.network.FxPacket;
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

    private static final ResourceLocation STREAK = tex("streak"), SHARD = tex("shard"), EMBER = tex("ember"), TONGUE = tex("tongue");

    private static ResourceLocation tex(String n) { return ResourceLocation.fromNamespaceAndPath("arcane_addon", "textures/effect/pixel/" + n + ".png"); }

    /** A moving pixel slash emitter. */
    private static final class Slash {
        int id; Vec3 c, d, u, side; double r0, r1, speed; int life, pal; double born; boolean dead;
    }

    /** One sprite. kind: 0 streak, 1 shard, 2 ember, 3 tongue. dark = drawn black (anti magic), else glowing. */
    private static final class Spr {
        Vec3 pos, prev, vel, axis; float len, wid; int life, age, kind; boolean dark; float heat; float drag;
        int pal = 2; Vec3 curl; float curlRate; float darkAlpha = 0.92F; float grav;
    }

    private static final List<Slash> SLASHES = new ArrayList<>();
    private static final List<Spr> SPRITES = new ArrayList<>();
    private static final int MAX = 6000;

    public static void spawn(FxPacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Slash s = new Slash();
        s.id = p.id; s.c = p.c; s.d = p.d.normalize(); s.u = p.u.normalize(); s.side = s.d.cross(s.u).normalize();
        s.r0 = p.r; s.r1 = p.r2 > 0 ? p.r2 : p.r; s.speed = p.speed; s.life = Math.max(1, p.life); s.pal = p.palette;
        s.born = mc.level.getGameTime();
        SLASHES.add(s);
        emit(s, 0, true);   // the first frame is already a full blade
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
            {1.0F, 0.94F, 0.8F}
    };
    /** The colour of the solid (non-glowing) sprites: black anti magic and darkness, steel, gold, mercury, bone. */
    private static final float[][] SOLID = {
            {0.02F, 0.0F, 0.01F}, {0.03F, 0.0F, 0.05F}, {0.08F, 0.03F, 0.02F}, {0.05F, 0.1F, 0.16F}, {0.1F, 0.09F, 0.02F},
            {0.05F, 0.12F, 0.08F}, {0.5F, 0.5F, 0.55F}, {0.32F, 0.36F, 0.42F}, {0.55F, 0.38F, 0.08F}, {0.03F, 0.0F, 0.0F},
            {0.015F, 0.0F, 0.035F}, {0.62F, 0.67F, 0.75F}, {0.02F, 0.05F, 0.2F}, {0.02F, 0.06F, 0.18F}, {0.12F, 0.0F, 0.01F},
            {0.82F, 0.78F, 0.68F}
    };

    static final int DARK = 0, FLAME = 1, BOLT = 2, WIND = 3, METAL = 4, BONE = 5, LIGHT = 6;

    /** Which kind of magic a palette is - it decides what its sprites do. */
    static int element(int pal) {
        return switch (pal) {
            case 0, 1, 9, 10 -> DARK;
            case 2, 12, 14 -> FLAME;
            case 4, 13 -> BOLT;
            case 5 -> WIND;
            case 7, 8, 11 -> METAL;
            case 15 -> BONE;
            default -> LIGHT;
        };
    }

    /** Glow colour of a palette at a given heat: white-hot at the top, the palette's own colour, then dying embers. */
    private static float[] glow(int pal, float heat) {
        pal = Mth.clamp(pal, 0, 15);
        if (pal == 2) return fire(heat);
        if (pal == 0 || pal == 9 || pal == 14) return crimson(heat);
        float[] c = RIM[pal];
        if (heat > 0.92F) return new float[]{0.92F + 0.08F * c[0], 0.92F + 0.08F * c[1], 0.92F + 0.08F * c[2]};
        if (heat > 0.7F) return new float[]{0.55F + 0.45F * c[0], 0.55F + 0.45F * c[1], 0.55F + 0.45F * c[2]};
        if (heat > 0.4F) return c;
        if (heat > 0.2F) return new float[]{c[0] * 0.6F, c[1] * 0.6F, c[2] * 0.6F};
        return new float[]{c[0] * 0.3F, c[1] * 0.3F, c[2] * 0.3F};
    }

    /** Anti magic's glow: crimson, from a hot pink-white down to a deep blood red. */
    private static float[] crimson(float heat) {
        if (heat > 0.92F) return new float[]{1.0F, 0.93F, 0.88F};
        if (heat > 0.8F) return new float[]{1.0F, 0.75F, 0.7F};
        if (heat > 0.55F) return new float[]{1.0F, 0.22F, 0.16F};
        if (heat > 0.3F) return new float[]{0.75F, 0.04F, 0.04F};
        return new float[]{0.35F, 0.0F, 0.02F};
    }

    private static float[] fire(float heat) {
        if (heat > 0.8F) return new float[]{1.0F, 0.97F, 0.85F};
        if (heat > 0.6F) return new float[]{1.0F, 0.86F, 0.35F};
        if (heat > 0.4F) return new float[]{1.0F, 0.55F, 0.12F};
        if (heat > 0.2F) return new float[]{0.9F, 0.22F, 0.05F};
        return new float[]{0.45F, 0.06F, 0.03F};
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
            Spr p = spr(at, s.d.scale(s.speed * 0.93), tan.subtract(s.d.scale(0.1)), r.nextFloat() < 0.75F ? 0 : 3,
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

    private static float density() { return Mth.clamp(com.example.arcaneaddon.AddonClientConfig.particleDensity(), 0.0F, 2.0F); }

    /** How many sprites for a wanted (fractional) count, scaled by the density setting. */
    private static int cnt(double want) {
        double w = want * density();
        int n = (int) w;
        return n + (ThreadLocalRandom.current().nextDouble() < w - n ? 1 : 0);
    }

    private static Vec3 rnd(ThreadLocalRandom r, double s) { return new Vec3(r.nextGaussian() * s, r.nextGaussian() * s, r.nextGaussian() * s); }

    private static Vec3 nz(Vec3 v, Vec3 fallback) { return v.lengthSqr() > 1.0E-8 ? v.normalize() : fallback; }

    private static final Vec3 UP = new Vec3(0, 1, 0);

    /** One sprite of a palette's kind of magic at {@code at}, carried by {@code drift}, lying along {@code axis}. */
    static void one(int pal, Vec3 at, Vec3 drift, Vec3 axis, double size) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        axis = nz(axis, UP);
        float sz = (float) size;
        double q = r.nextDouble();
        Spr p;
        switch (element(pal)) {
            case DARK -> {
                if (q < 0.45) {        // the black of the blade
                    p = spr(at, drift.scale(0.95), axis.add(rnd(r, 0.12)), r.nextFloat() < 0.75F ? 0 : 3, sz * (float) r.nextDouble(1.0, 1.8), sz * (float) r.nextDouble(0.3, 0.45), r.nextInt(3, 6));
                    p.dark = true;
                } else if (q < 0.7) {  // its coloured glow
                    p = spr(at.add(rnd(r, size * 0.15)), drift.scale(0.85), axis, 0, sz * 1.5F, sz * 0.5F, r.nextInt(2, 5));
                    p.heat = 0.6F;
                } else if (q < 0.8) {  // a white-hot edge
                    p = spr(at, drift.scale(0.97), axis, 0, sz * 1.1F, sz * 0.12F, r.nextInt(2, 4));
                    p.heat = 1.0F;
                } else if (q < 0.92) { // embers and black flakes flung away
                    Vec3 v = drift.scale(0.3).add(rnd(r, 0.08)).add(0, 0.03, 0);
                    p = spr(at, v, v, r.nextFloat() < 0.6F ? 2 : 1, sz * 0.25F, sz * 0.25F, r.nextInt(8, 15));
                    p.drag = 0.92F;
                    if (r.nextBoolean()) p.heat = 0.9F; else { p.dark = true; p.darkAlpha = 1.0F; }
                } else {               // a tongue of smoke curling away
                    Vec3 v = drift.scale(0.2).add(rnd(r, 0.03));
                    p = spr(at, v, axis.cross(UP).lengthSqr() > 1.0E-6 ? axis.cross(UP) : axis, 3, sz * 2.0F, sz * 1.1F, r.nextInt(10, 17));
                    p.dark = true; p.darkAlpha = 0.6F; p.drag = 0.9F; p.grav = 0.004F;
                    p.curl = nz(drift, UP); p.curlRate = (float) r.nextDouble(0.2, 0.4) * (r.nextBoolean() ? 1 : -1);
                }
            }
            case FLAME -> {
                if (q < 0.55) {        // flame tongues licking upward
                    p = spr(at, drift.scale(0.5).add(0, 0.05, 0).add(rnd(r, 0.015)), UP.add(axis.scale(0.3)).add(rnd(r, 0.15)), 3,
                            sz * 1.6F, sz * 0.7F, r.nextInt(5, 10));
                    p.heat = (float) r.nextDouble(0.75, 1.0); p.grav = 0.006F;
                } else if (q < 0.75) { // hot streaks along the shape
                    p = spr(at, drift.scale(0.9), axis, 0, sz * 1.6F, sz * 0.25F, r.nextInt(3, 6));
                    p.heat = 0.9F;
                } else if (q < 0.95 || pal == 14) {   // embers (Mereoleona's fire burns clean: embers, never smoke)
                    Vec3 v = drift.scale(0.3).add(rnd(r, 0.08)).add(0, 0.06, 0);
                    p = spr(at, v, v, 2, sz * 0.25F, sz * 0.25F, r.nextInt(10, 19));
                    p.heat = 1.0F; p.drag = 0.93F;
                } else {               // a curl of smoke
                    p = spr(at, drift.scale(0.2).add(0, 0.03, 0), UP, 3, sz * 1.8F, sz * 1.0F, r.nextInt(14, 21));
                    p.dark = true; p.darkAlpha = 0.5F; p.drag = 0.9F; p.grav = 0.003F;
                }
            }
            case BOLT -> {
                if (q < 0.5) {         // a little zigzag of lightning
                    Vec3 a0 = at, dir = nz(axis.add(rnd(r, 0.6)), UP);
                    for (int k = 0; k < 3; k++) {
                        Vec3 a1 = a0.add(dir.scale(size * 0.8)).add(rnd(r, size * 0.25));
                        Vec3 mid = a0.add(a1).scale(0.5), ax = a1.subtract(a0);
                        Spr core = spr(mid, drift.scale(0.9), ax, 0, (float) ax.length() * 1.1F, sz * 0.12F, r.nextInt(2, 4));
                        core.heat = 1.0F; core.pal = pal; add(core);
                        Spr gl = spr(mid, drift.scale(0.9), ax, 0, (float) ax.length() * 1.2F, sz * 0.4F, 2);
                        gl.heat = 0.6F; gl.pal = pal; add(gl);
                        a0 = a1;
                        dir = nz(dir.add(rnd(r, 0.7)), UP);
                    }
                    return;
                }
                Vec3 v = drift.scale(0.5).add(rnd(r, 0.2));   // fast sparks
                p = spr(at, v, v, 2, sz * 0.2F, sz * 0.2F, r.nextInt(4, 9));
                p.heat = 1.0F; p.drag = 0.85F;
            }
            case WIND -> {
                if (q < 0.7) {         // long thin streaks of air, swirling
                    p = spr(at, drift.add(axis.scale(0.15)), axis, 0, sz * 2.5F, sz * 0.12F, r.nextInt(4, 8));
                    p.heat = 0.75F;
                    if (r.nextBoolean()) { p.curl = UP; p.curlRate = 0.25F * (r.nextBoolean() ? 1 : -1); }
                } else if (q < 0.9) {
                    p = spr(at, drift.add(axis.scale(0.2)), axis, 0, sz * 1.8F, sz * 0.07F, r.nextInt(3, 6));
                    p.heat = 1.0F;
                } else {               // a glinting leaf
                    Vec3 v = drift.scale(0.5).add(rnd(r, 0.06));
                    p = spr(at, v, v, 1, sz * 0.25F, sz * 0.25F, r.nextInt(10, 17));
                    p.heat = 0.7F; p.drag = 0.94F; p.curl = UP; p.curlRate = 0.3F;
                }
            }
            case METAL -> {
                if (q < 0.4) {         // solid shards of metal, falling
                    Vec3 v = drift.scale(0.6).add(rnd(r, 0.08));
                    float s2 = sz * (float) r.nextDouble(0.35, 0.6);
                    p = spr(at, v, v.add(rnd(r, 0.5)), 1, s2, s2, r.nextInt(6, 13));
                    p.dark = true; p.darkAlpha = 1.0F; p.drag = 0.9F; p.grav = -0.01F;
                } else if (q < 0.75) { // a white glint
                    p = spr(at, drift.scale(0.9), axis, 0, sz * 0.9F, sz * 0.1F, r.nextInt(2, 4));
                    p.heat = 1.0F;
                } else {
                    Vec3 v = drift.scale(0.4).add(rnd(r, 0.15));
                    p = spr(at, v, v, 2, sz * 0.15F, sz * 0.15F, r.nextInt(5, 10));
                    p.heat = 0.95F; p.drag = 0.88F;
                }
            }
            case BONE -> {
                if (q < 0.5) {         // ivory splinters
                    Vec3 v = drift.scale(0.6).add(rnd(r, 0.07));
                    float s2 = sz * (float) r.nextDouble(0.3, 0.5);
                    p = spr(at, v, v.add(rnd(r, 0.5)), 1, s2, s2 * 0.6F, r.nextInt(8, 15));
                    p.dark = true; p.darkAlpha = 1.0F; p.drag = 0.9F; p.grav = -0.012F;
                } else if (q < 0.8) {  // bone dust
                    Vec3 v = drift.scale(0.3).add(rnd(r, 0.03));
                    p = spr(at, v, v, 2, sz * 0.2F, sz * 0.2F, r.nextInt(10, 17));
                    p.dark = true; p.darkAlpha = 0.7F; p.drag = 0.92F; p.grav = -0.004F;
                } else {
                    p = spr(at, drift.scale(0.9), axis, 0, sz * 1.2F, sz * 0.12F, r.nextInt(2, 4));
                    p.heat = 0.8F;
                }
            }
            default -> {
                if (q < 0.5) {
                    p = spr(at, drift.scale(0.9), axis, 0, sz * 1.5F, sz * 0.3F, r.nextInt(3, 6));
                    p.heat = 0.8F;
                } else if (q < 0.8) {
                    p = spr(at, drift.scale(0.95), axis, 0, sz * 1.1F, sz * 0.1F, r.nextInt(2, 4));
                    p.heat = 1.0F;
                } else {
                    Vec3 v = drift.scale(0.4).add(rnd(r, 0.08));
                    p = spr(at, v, v, 1, sz * 0.3F, sz * 0.3F, r.nextInt(6, 12));
                    p.heat = 0.9F; p.drag = 0.92F;
                }
            }
        }
        p.pal = pal;
        add(p);
    }

    /**
     * Called by FxRenderer once a tick for every live effect. {@code at} = where it is now (moved along its velocity),
     * {@code perTick} = a copy re-sent every tick (only its first tick sheds sprites, so a held shape isn't doubled).
     */
    public static void fromFx(FxPacket p, Vec3 at, double age, double life, boolean perTick) {
        if (age < 0.0 || age > life) return;
        if (perTick && age >= 1.0) return;
        if (density() <= 0.0F) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int pal = Mth.clamp(p.palette, 0, 15);
        boolean burst = !perTick && age < 1.0;
        double frac = Mth.clamp(age / Math.max(1.0, life), 0.0, 1.0);
        Vec3 dn = nz(p.d, new Vec3(1, 0, 0)), un = nz(p.u, UP);
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
                int n = cnt(age < 3.0 ? Mth.clamp(len * 1.5, 3.0, 24.0) : Mth.clamp(len * 0.35, 0.5, 6.0));
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
                    return;
                }
                if (p.r < 0.08F) return;
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
    }

    // ------------------------------------------------------------------ forms and auras worn by an entity
    private static final int BODY = 0, HANDS = 1, RIGHT_HAND = 2, SHELL = 3, ZONE = 4, ORBIT = 5, RING = 6, GLINT = 7;

    private static final class Att { int id, shape, pal, mode; double end; float r, h; float rate; }

    private static final java.util.Map<Long, Att> ATTACH = new java.util.HashMap<>();

    /** Aura-outline colours (AuraOutlineLayer) to palettes. */
    private static final int[] OUTLINE_PAL = {10, 9, 11, 13, 14, 0, 15, 6, 9};
    /** Grimoire (GrimoireMastery.BOOKS order) to palette: Asta, Yami, Yuno, Nozel, Magna, Luck, Mereoleona, Zenon, Klaus. */
    private static final int[] BOOK_PAL = {0, 10, 5, 11, 2, 13, 14, 15, 7};

    private static int flamePal(int p) { return p == 12 || p == 14 ? p : 2; }

    /**
     * Called by FxRenderer for every packet: forms, auras and held weapons (which have no shape of their own in the
     * effect list) start or stop shedding sprites here.
     */
    public static void onPacket(FxPacket p) {
        int mode, pal;
        float rate;
        switch (p.shape) {
            case 4 -> { mode = GLINT; pal = 6; rate = 0.35F; }                                     // Ki Sense mark
            case 6 -> { mode = BODY; pal = 9; rate = 3.0F; }                                       // Devil Union
            case 11 -> { mode = BODY; pal = 7; rate = 1.2F; }                                      // Knight's Armor
            case 14 -> { mode = BODY; pal = OUTLINE_PAL[Mth.clamp(p.palette, 0, OUTLINE_PAL.length - 1)]; rate = 1.0F; }   // aura outline
            case 15 -> { mode = SHELL; pal = 10; rate = 4.0F; }                                    // Black Cocoon
            case 16 -> { mode = BODY; pal = 10; rate = 2.0F; }                                     // Black Blade
            case 17 -> { mode = ZONE; pal = Mth.clamp(p.palette, 0, 15); rate = 1.0F; }            // Mana Zone
            case 18 -> { mode = BODY; pal = 5; rate = 2.5F; }                                      // Spirit Dive
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
        if (p.life <= 0) { ATTACH.remove(key); return; }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Att a = new Att();
        a.id = p.id; a.shape = p.shape; a.pal = pal; a.mode = mode; a.rate = rate;
        a.end = mc.level.getGameTime() + p.life;
        a.r = p.shape == 36 ? p.r : (p.shape == 37 ? Math.max(0.6F, p.r) : p.r);
        a.h = p.shape == 36 ? p.r2 : p.h;
        ATTACH.put(key, a);
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
            int n = cnt(a.rate * (self && (a.mode == BODY || a.mode == GLINT) ? 0.35 : 1.0));
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
                    case RING -> {
                        double ang = r.nextDouble(Math.PI * 2), R = Math.max(1.0, a.r) * r.nextDouble(0.3, 0.9);
                        one(a.pal, base.add(Math.cos(ang) * R, h * r.nextDouble(0.3, 0.8), Math.sin(ang) * R), mv.scale(0.5), new Vec3(Math.cos(ang), 0, Math.sin(ang)), 0.3);
                    }
                    default -> {}
                }
            }
        }
    }

    private static void add(Spr p) {
        if (SPRITES.size() >= MAX) return;   // full: drop the newest instead of shifting the whole list
        SPRITES.add(p);
    }

    // ------------------------------------------------------------------ ticking
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { SLASHES.clear(); SPRITES.clear(); ATTACH.clear(); return; }
        tickAttached(mc);
        double now = mc.level.getGameTime();
        for (Iterator<Slash> it = SLASHES.iterator(); it.hasNext(); ) {
            Slash s = it.next();
            double age = now - s.born;
            if (s.dead || age > s.life) { it.remove(); continue; }
            emit(s, age, false);
        }
        for (Iterator<Spr> it = SPRITES.iterator(); it.hasNext(); ) {
            Spr p = it.next();
            if (++p.age > p.life) { it.remove(); continue; }
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
    }

    // ------------------------------------------------------------------ drawing
    public static void render(PoseStack ps, Camera camera, MultiBufferSource.BufferSource buf, float pt) {
        if (SPRITES.isEmpty()) return;
        Vec3 cam = camera.getPosition();
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();
        float opacity = com.example.arcaneaddon.AddonClientConfig.effectOpacity();
        // the black core first (normal blending), then the glowing flame on top (additive: eyes ignores alpha,
        // so the fade is done by darkening the colour)
        ResourceLocation[] texs = {STREAK, SHARD, EMBER, TONGUE};
        for (int pass = 0; pass < 2; pass++) {
            for (int kind = 0; kind < 4; kind++) {
                RenderType rt = pass == 0 ? RenderType.entityTranslucentEmissive(texs[kind]) : RenderType.eyes(texs[kind]);
                VertexConsumer vc = null;
                for (Spr p : SPRITES) {
                    if (p.kind != kind || p.dark != (pass == 0)) continue;
                    if (vc == null) vc = buf.getBuffer(rt);
                    draw(vc, m, n, cam, p, pt, pass == 0, opacity);
                }
                if (vc != null) buf.endBatch(rt);
            }
        }
    }

    private static void draw(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Spr p, float pt, boolean dark, float opacity) {
        Vec3 pos = p.prev.add(p.pos.subtract(p.prev).scale(pt));
        float life = (p.age + pt) / (float) p.life;
        float fade = Mth.clamp((1.0F - life) * 2.2F, 0.0F, 1.0F) * opacity;
        double dc = pos.distanceTo(cam);
        if (dc < 2.2) fade *= (float) Mth.clamp((dc - 0.6) / 1.6, 0.0, 1.0);   // never smother the camera
        if (fade <= 0.01F) return;
        // stretched along its own direction, turned to face the camera around that direction
        Vec3 toCam = cam.subtract(pos);
        Vec3 ax = p.axis;
        Vec3 side = ax.cross(toCam);
        if (side.lengthSqr() < 1.0E-8) side = ax.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < 1.0E-8) side = new Vec3(1, 0, 0);
        side = side.normalize();
        float len = p.len * (p.kind == 0 || p.kind == 3 ? (0.7F + 0.6F * (1.0F - life)) : 1.0F);
        Vec3 a = ax.scale(len * 0.5), b = side.scale(p.wid * 0.5);
        float r, g, bl, al;
        if (dark) { float[] sc = SOLID[Mth.clamp(p.pal, 0, 15)]; r = sc[0]; g = sc[1]; bl = sc[2]; al = p.darkAlpha * fade; }
        else {
            float[] c = glow(p.pal, p.heat);
            r = c[0] * fade; g = c[1] * fade; bl = c[2] * fade; al = 1.0F;
        }
        v(vc, m, n, cam, pos.subtract(a).subtract(b), r, g, bl, al, 0, 1);
        v(vc, m, n, cam, pos.subtract(a).add(b), r, g, bl, al, 1, 1);
        v(vc, m, n, cam, pos.add(a).add(b), r, g, bl, al, 1, 0);
        v(vc, m, n, cam, pos.add(a).subtract(b), r, g, bl, al, 0, 0);
        // and the back face, so no render state can cull it away
        v(vc, m, n, cam, pos.add(a).subtract(b), r, g, bl, al, 0, 0);
        v(vc, m, n, cam, pos.add(a).add(b), r, g, bl, al, 1, 0);
        v(vc, m, n, cam, pos.subtract(a).add(b), r, g, bl, al, 1, 1);
        v(vc, m, n, cam, pos.subtract(a).subtract(b), r, g, bl, al, 0, 1);
    }

    private static void v(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 w, float r, float g, float b, float a, float u, float t) {
        vc.vertex(m, (float) (w.x - cam.x), (float) (w.y - cam.y), (float) (w.z - cam.z)).color(r, g, b, a).uv(u, t)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(n, 0, 1, 0).endVertex();
    }
}
