package com.example.arcaneaddon.server;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The addon's effect library: 25 effect recipes x 8 colour palettes = 200 named, reusable effects.
 * Everything is built from the addon's own 3D shapes (FxSend walls, rings, crescents, beams) - never vanilla particles.
 *
 * Use it by name:      FxLib.play("spike_ring_steel", level, pos, dir, 1.0f);
 * or by recipe/colour: FxLib.play(FxLib.Recipe.SPIKE_RING, FxLib.STEEL, level, pos, dir, 1.0f);
 *
 *  pos   = where the effect is centred (for ground effects: the feet / ground point)
 *  dir   = facing, used by directional recipes (slashes, beams, shields, spirals); any vector for the rest
 *  scale = overall size, 1.0 = about player-sized
 *
 * The full list of names is in {@link #CATALOG} (and EFFECTS.txt in the project root).
 */
public final class FxLib {
    private FxLib() {}

    // ------------------------------------------------------------------ palettes (indexes into FxRenderer BODY/RIM)
    public static final int CRIMSON = 0, DARK = 1, FIRE = 2, ICE = 3, LIGHTNING = 4, WIND = 5, SILVER = 6, STEEL = 7, GOLD = 8;
    /** The eight colours every recipe comes in (silver is used as the bright accent inside the others). */
    public static final int[] PALETTES = {CRIMSON, DARK, FIRE, ICE, LIGHTNING, WIND, STEEL, GOLD};
    public static final String[] PALETTE_NAMES = {"crimson", "dark", "fire", "ice", "lightning", "wind", "steel", "gold"};

    public enum Recipe {
        SPARK, IMPACT, FLASH, GROUND_PUFF, PILLAR,
        SHOCK_RING, IMPLODE, DOUBLE_RING, CROSS_SLASH, VERTICAL_SLASH,
        HORIZONTAL_SLASH, SPIN_SLASH, CRESCENT_SHOT, BEAM_LINE, SPIKE_BURST,
        SPIKE_RING, SPIRAL, ORB, CAGE, SHIELD,
        DOME, RAIN, HALO, AURA, STARBURST;

        public String id() { return name().toLowerCase(Locale.ROOT); }
    }

    public record Entry(Recipe recipe, int palette) {}

    /** All 200 effects, "recipe_palette" -> entry, in a stable order. */
    public static final Map<String, Entry> CATALOG;
    static {
        Map<String, Entry> m = new LinkedHashMap<>();
        for (Recipe r : Recipe.values())
            for (int i = 0; i < PALETTES.length; i++)
                m.put(r.id() + "_" + PALETTE_NAMES[i], new Entry(r, PALETTES[i]));
        CATALOG = Collections.unmodifiableMap(m);
    }

    /** Plays a library effect by name, e.g. "shock_ring_gold". Unknown names are ignored. */
    public static void play(String name, ServerLevel sl, Vec3 pos, Vec3 dir, float scale) {
        Entry e = CATALOG.get(name);
        if (e != null) play(e.recipe(), e.palette(), sl, pos, dir, scale);
    }

    public static void play(Recipe r, int pal, ServerLevel sl, Vec3 pos, Vec3 dir, float s) {
        Vec3 d = dir == null || dir.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : dir.normalize();
        switch (r) {
            case SPARK -> spark(sl, pal, pos, s);
            case IMPACT -> impact(sl, pal, pos, s);
            case FLASH -> flash(sl, pal, pos, s);
            case GROUND_PUFF -> groundPuff(sl, pal, pos, s);
            case PILLAR -> pillar(sl, pal, pos, s);
            case SHOCK_RING -> shockRing(sl, pal, pos, s);
            case IMPLODE -> implode(sl, pal, pos, s);
            case DOUBLE_RING -> doubleRing(sl, pal, pos, s);
            case CROSS_SLASH -> crossSlash(sl, pal, pos, d, s);
            case VERTICAL_SLASH -> verticalSlash(sl, pal, pos, d, s);
            case HORIZONTAL_SLASH -> horizontalSlash(sl, pal, pos, d, s);
            case SPIN_SLASH -> spinSlash(sl, pal, pos, d, s);
            case CRESCENT_SHOT -> crescentShot(sl, pal, pos, d, s);
            case BEAM_LINE -> beamLine(sl, pal, pos, d, s);
            case SPIKE_BURST -> spikeBurst(sl, pal, pos, s);
            case SPIKE_RING -> spikeRing(sl, pal, pos, s);
            case SPIRAL -> spiral(sl, pal, pos, d, s, 0);
            case ORB -> orb(sl, pal, pos, s);
            case CAGE -> cage(sl, pal, pos, s);
            case SHIELD -> shield(sl, pal, pos, d, s);
            case DOME -> dome(sl, pal, pos, s);
            case RAIN -> rain(sl, pal, pos, s);
            case HALO -> halo(sl, pal, pos, s);
            case AURA -> aura(sl, pal, pos, s);
            case STARBURST -> starburst(sl, pal, pos, d, s);
        }
    }

    // ------------------------------------------------------------------ geometry helpers
    private static final Vec3 UP = new Vec3(0, 1, 0);

    static Vec3 right(Vec3 d) {
        Vec3 r = d.cross(UP);
        return r.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : r.normalize();
    }

    static Vec3 up(Vec3 d) { return right(d).cross(d).normalize(); }

    /**
     * The floor under (x, z): searches from a little above {@code y} down to 6 below and returns the point on the
     * surface (just above it), so ground effects follow slopes and steps instead of floating or sinking.
     */
    public static Vec3 floor(ServerLevel sl, double x, double y, double z) {
        var hit = sl.clip(new net.minecraft.world.level.ClipContext(new Vec3(x, y + 2.0, z), new Vec3(x, y - 6.0, z),
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, (net.minecraft.world.entity.Entity) null));
        return new Vec3(x, (hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? y : hit.getLocation().y) + 0.04, z);
    }

    public static Vec3 floor(ServerLevel sl, Vec3 near) { return floor(sl, near.x, near.y, near.z); }

    /**
     * The ground under (x, z) within {@code maxDown} blocks below {@code y} (and up to 1.5 above it), or null when
     * there is none that close - so crater pieces, cracks and spikes are skipped instead of hanging in the air.
     */
    public static Vec3 ground(ServerLevel sl, double x, double y, double z, double maxDown) {
        var hit = sl.clip(new net.minecraft.world.level.ClipContext(new Vec3(x, y + 1.5, z), new Vec3(x, y - maxDown, z),
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, (net.minecraft.world.entity.Entity) null));
        return hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? null : hit.getLocation().add(0, 0.04, 0);
    }

    public static Vec3 ground(ServerLevel sl, Vec3 near, double maxDown) { return ground(sl, near.x, near.y, near.z, maxDown); }

    private static Vec3 randomUnit() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Vec3 v;
        do { v = new Vec3(r.nextDouble(-1, 1), r.nextDouble(-1, 1), r.nextDouble(-1, 1)); } while (v.lengthSqr() < 0.05 || v.lengthSqr() > 1);
        return v.normalize();
    }

    // ------------------------------------------------------------------ the 25 recipes
    /** Short X of two thin blades. */
    public static void spark(ServerLevel sl, int pal, Vec3 c, float s) {
        Vec3 a = randomUnit(), b = right(a).scale(0.8).add(a.scale(0.2)).normalize();
        FxSend.beam(sl, pal, c.subtract(a.scale(0.8 * s)), c.add(a.scale(0.8 * s)), 0.12f * s, 5);
        FxSend.beam(sl, SILVER, c.subtract(b.scale(0.6 * s)), c.add(b.scale(0.6 * s)), 0.07f * s, 4);
    }

    /** Spark plus a thin tilted shock ring. */
    public static void impact(ServerLevel sl, int pal, Vec3 c, float s) {
        spark(sl, pal, c, s);
        Vec3 n = randomUnit();
        FxSend.wall(sl, pal, c, right(n), n.cross(right(n)), 0.2f * s, 1.2f * s, -180, 180, 0.08f * s, 6, 0, 0);
    }

    /** Quick vertical pop. */
    public static void flash(ServerLevel sl, int pal, Vec3 c, float s) {
        FxSend.wall(sl, pal, c, new Vec3(1, 0, 0), UP, 0.05f, 0.7f * s, -180, 180, 0.12f * s, 3, 0, 0);
    }

    /** Low wide ring on the ground. */
    public static void groundPuff(ServerLevel sl, int pal, Vec3 feet, float s) {
        FxSend.ring(sl, pal, feet.add(0, 0.05, 0), 0.2f * s, 1.2f * s, 0.1f * s, 8);
    }

    /** Vertical column with a bright core. */
    public static void pillar(ServerLevel sl, int pal, Vec3 base, float s) {
        FxSend.beam(sl, pal, base, base.add(0, 2.5 * s, 0), 0.35f * s, 10);
        FxSend.beam(sl, SILVER, base, base.add(0, 2.6 * s, 0), 0.08f * s, 8);
    }

    /** Expanding horizontal ring. */
    public static void shockRing(ServerLevel sl, int pal, Vec3 c, float s) {
        FxSend.ring(sl, pal, c, 0.3f * s, 3.0f * s, 0.2f * s, 10);
    }

    /** Ring collapsing inward. */
    public static void implode(ServerLevel sl, int pal, Vec3 c, float s) {
        FxSend.ring(sl, pal, c, 3.0f * s, 0.2f * s, 0.15f * s, 8);
    }

    /** Two rings a beat apart. */
    public static void doubleRing(ServerLevel sl, int pal, Vec3 c, float s) {
        FxSend.ring(sl, pal, c, 0.2f * s, 2.0f * s, 0.15f * s, 8);
        AstaFx.later(3, () -> FxSend.ring(sl, SILVER, c, 0.2f * s, 2.6f * s, 0.06f * s, 8));
    }

    /** Two diagonal blades crossing in front of the facing. */
    public static void crossSlash(ServerLevel sl, int pal, Vec3 c, Vec3 d, float s) {
        Vec3 r = right(d), u = up(d);
        for (int k = -1; k <= 1; k += 2) {
            Vec3 a = r.scale(0.7 * k).add(u.scale(0.7)).normalize().scale(1.4 * s);
            FxSend.beam(sl, pal, c.subtract(a), c.add(a), 0.3f * s, 8);
            FxSend.beam(sl, SILVER, c.subtract(a.scale(0.85)), c.add(a.scale(0.85)), 0.06f * s, 6);
        }
    }

    /** Overhead-to-ground arc. */
    public static void verticalSlash(ServerLevel sl, int pal, Vec3 c, Vec3 d, float s) {
        FxSend.wall(sl, pal, c, d, UP, 1.8f * s, 1.9f * s, 80, -70, 0.5f * s, 10, 3, 0);
    }

    /** Wide sideways arc. */
    public static void horizontalSlash(ServerLevel sl, int pal, Vec3 c, Vec3 d, float s) {
        FxSend.wall(sl, pal, c, d, right(d), 2.0f * s, 2.1f * s, -100, 100, 0.5f * s, 10, 3, 0);
    }

    /** Full 360 cut around the centre. */
    public static void spinSlash(ServerLevel sl, int pal, Vec3 c, Vec3 d, float s) {
        Vec3 f = new Vec3(d.x, 0, d.z);
        f = f.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : f.normalize();
        FxSend.wall(sl, pal, c, f, right(f), 2.2f * s, 2.3f * s, -180, 180, 0.6f * s, 12, 5, 0);
    }

    /** A crescent that flies along the facing. */
    public static void crescentShot(ServerLevel sl, int pal, Vec3 c, Vec3 d, float s) {
        FxSend.crescent(sl, pal, c, d, up(d), 0.9f * s, 1.8f * s, 10, 1.8f);
    }

    /** Straight beam 6 blocks along the facing. */
    public static void beamLine(ServerLevel sl, int pal, Vec3 c, Vec3 d, float s) {
        FxSend.beam(sl, pal, c, c.add(d.scale(6 * s)), 0.3f * s, 9);
        FxSend.beam(sl, SILVER, c, c.add(d.scale(6 * s)), 0.06f * s, 7);
    }

    /** Thorns bursting out in every direction. */
    public static void spikeBurst(ServerLevel sl, int pal, Vec3 c, float s) {
        for (int i = 0; i < 8; i++) {   // solid 3D shards, with a glowing streak along each
            Vec3 v = randomUnit();
            FxSend.spike(sl, pal, c.add(v.scale(0.15 * s)), v, 1.2f * s, 0.11f * s, 9, 2, com.example.arcaneaddon.network.FxPacket.POP);
            if (i % 2 == 0) FxSend.beam(sl, pal, c.add(v.scale(0.2 * s)), c.add(v.scale(1.6 * s)), 0.1f * s, 6);
        }
    }

    /** Spikes erupting from the ground in a circle. */
    public static void spikeRing(ServerLevel sl, int pal, Vec3 feet, float s) {
        int n = 10;
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            Vec3 b = feet.add(Math.cos(a) * 1.8 * s, 0, Math.sin(a) * 1.8 * s);
            b = ground(sl, b, 1.5);
            if (b == null) continue;   // no ground there: no spike
            FxSend.spike(sl, pal, b.add(0, -0.12, 0), new Vec3(Math.cos(a) * 0.25, 1.6, Math.sin(a) * 0.25), (1.5f + (i % 2) * 0.35f) * s, 0.2f * s, 14, 3,
                    com.example.arcaneaddon.network.FxPacket.GROUND);   // solid spikes erupting from the floor
        }
    }

    /** A helix wound along the facing. {@code phase} turns it, so successive calls look like it spins. */
    public static void spiral(ServerLevel sl, int pal, Vec3 c, Vec3 d, float s, double phase) {
        Vec3 r = right(d), u = up(d);
        int n = 12;
        Vec3 prev = null;
        for (int i = 0; i <= n; i++) {
            double t = (double) i / n, a = phase + t * Math.PI * 4;
            Vec3 p = c.add(d.scale(t * 5 * s)).add(r.scale(Math.cos(a) * 0.45 * s)).add(u.scale(Math.sin(a) * 0.45 * s));
            if (prev != null) FxSend.beam(sl, pal, prev, p, 0.12f * s, 6);
            prev = p;
        }
    }

    /** A small sphere: three rings at right angles. */
    public static void orb(ServerLevel sl, int pal, Vec3 c, float s) {
        FxSend.wall(sl, pal, c, new Vec3(1, 0, 0), UP, 0.55f * s, 0.6f * s, -180, 180, 0.35f * s, 6, 0, 0);
        FxSend.wall(sl, pal, c, new Vec3(0, 0, 1), UP, 0.55f * s, 0.6f * s, -180, 180, 0.35f * s, 6, 0, 0);
        FxSend.ring(sl, SILVER, c, 0.6f * s, 0.63f * s, 0.04f, 6);
    }

    /** Bars around a point, with rings at top and bottom. */
    public static void cage(ServerLevel sl, int pal, Vec3 feet, float s) {
        int n = 8;
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            Vec3 b = feet.add(Math.cos(a) * 0.9 * s, 0, Math.sin(a) * 0.9 * s);
            FxSend.box(sl, pal, b.add(0, 1.2 * s, 0), new Vec3(Math.cos(a), 0, Math.sin(a)), UP, 0.06f * s, 0.06f * s, 1.2f * s, 14, 3,
                    com.example.arcaneaddon.network.FxPacket.GROUND);   // solid bars
        }
        FxSend.ring(sl, pal, feet.add(0, 0.05, 0), 0.85f * s, 0.95f * s, 0.08f * s, 12);
        FxSend.ring(sl, pal, feet.add(0, 2.4 * s, 0), 0.85f * s, 0.95f * s, 0.08f * s, 12);
    }

    /** A round shield facing {@code d}: three concentric rings. */
    public static void shield(ServerLevel sl, int pal, Vec3 c, Vec3 d, float s) {
        Vec3 r = right(d), u = up(d);
        for (float k : new float[]{0.5f, 1.0f, 1.4f}) {
            FxSend.wall(sl, k > 1.2f ? SILVER : pal, c, r, u, k * s, k * s * 1.04f, -180, 180, 0.12f * s, 10, 0, 0);
        }
    }

    /** A dome of stacked rings. */
    public static void dome(ServerLevel sl, int pal, Vec3 feet, float s) {
        for (int i = 0; i < 5; i++) {
            double t = i / 5.0;
            float r = (float) (2.0 * s * Math.cos(Math.asin(t)));
            FxSend.ring(sl, pal, feet.add(0, t * 2.0 * s, 0), r, r * 1.03f, 0.06f * s, 12);
        }
    }

    /** Streaks falling around a point, staggered. */
    public static void rain(ServerLevel sl, int pal, Vec3 feet, float s) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int i = 0; i < 8; i++) {
            Vec3 g = feet.add(rnd.nextDouble(-2, 2) * s, 0, rnd.nextDouble(-2, 2) * s);
            AstaFx.later(1 + rnd.nextInt(6), () -> FxSend.beam(sl, pal, g.add(0, 4 * s, 0), g, 0.12f * s, 6));
        }
    }

    /** A thin ring hovering (put pos above the head). */
    public static void halo(ServerLevel sl, int pal, Vec3 c, float s) {
        FxSend.ring(sl, pal, c, 0.4f * s, 0.45f * s, 0.08f * s, 14);
    }

    /** A body-hugging cylinder (put pos at the body's middle). */
    public static void aura(ServerLevel sl, int pal, Vec3 c, float s) {
        FxSend.wall(sl, pal, c, new Vec3(1, 0, 0), new Vec3(0, 0, 1), 0.8f * s, 0.9f * s, -180, 180, 1.0f * s, 10, 0, 0);
    }

    /** Six rays in the plane facing {@code d}, plus a ring. */
    public static void starburst(ServerLevel sl, int pal, Vec3 c, Vec3 d, float s) {
        Vec3 r = right(d), u = up(d);
        for (int i = 0; i < 6; i++) {
            double a = Math.PI * 2 * i / 6;
            Vec3 v = r.scale(Math.cos(a)).add(u.scale(Math.sin(a)));
            FxSend.beam(sl, pal, c.add(v.scale(0.2 * s)), c.add(v.scale(1.6 * s)), 0.14f * s, 7);
        }
        FxSend.wall(sl, SILVER, c, r, u, 0.3f * s, 1.0f * s, -180, 180, 0.06f * s, 6, 0, 0);
    }
}
