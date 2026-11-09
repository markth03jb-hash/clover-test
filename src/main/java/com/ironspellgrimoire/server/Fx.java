package com.ironspellgrimoire.server;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Small effect vocabulary built ONLY from the addon's own 3D shapes (FxSend walls, rings, crescents, beams).
 * No vanilla particles are used anywhere in the addon.
 *
 * Palettes: 0 crimson (Slayer), 1 violet-black (Dweller / devil), 2 fire, 3 ice, 4 lightning, 5 wind, 6 white-silver.
 */
public final class Fx {
    private Fx() {}

    public static final int CRIMSON = 0, DARK = 1, FIRE = 2, ICE = 3, LIGHTNING = 4, WIND = 5, SILVER = 6, STEEL = 7, GOLD = 8, YAMI = 10, WATER = 19, MIRROR = 20, VOID = 21;

    private static Vec3 randomUnit() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Vec3 v;
        do { v = new Vec3(r.nextDouble(-1, 1), r.nextDouble(-1, 1), r.nextDouble(-1, 1)); } while (v.lengthSqr() < 0.05 || v.lengthSqr() > 1);
        return v.normalize();
    }

    private static Vec3 perpendicular(Vec3 v) {
        Vec3 p = v.cross(new Vec3(0, 1, 0));
        if (p.lengthSqr() < 1.0E-4) p = v.cross(new Vec3(1, 0, 0));
        return p.normalize();
    }

    /** The standard "hit" mark: a short X of two thin blades. Small and quick so it never hides the target. */
    public static void spark(ServerLevel sl, int pal, Vec3 c, float size) {
        Vec3 a = randomUnit();
        Vec3 b = perpendicular(a).scale(0.8).add(a.scale(0.2)).normalize();
        FxSend.beam(sl, pal, c.subtract(a.scale(size * 0.8)), c.add(a.scale(size * 0.8)), size * 0.12f, 5);
        FxSend.beam(sl, SILVER, c.subtract(b.scale(size * 0.6)), c.add(b.scale(size * 0.6)), size * 0.07f, 4);
    }

    /** A heavier hit: spark plus a thin tilted shock ring. */
    public static void impact(ServerLevel sl, int pal, Vec3 c, float size) {
        spark(sl, pal, c, size);
        Vec3 n = randomUnit();
        FxSend.wall(sl, pal, c, perpendicular(n), n.cross(perpendicular(n)), size * 0.2f, size * 1.2f, -180, 180, size * 0.08f, 6, 0, 0);
    }

    /** A quick white pop: one thin vertical ring that appears and vanishes. */
    public static void flash(ServerLevel sl, Vec3 c, float size) {
        FxSend.wall(sl, SILVER, c, new Vec3(1, 0, 0), new Vec3(0, 1, 0), 0.05f, size * 0.7f, -180, 180, size * 0.12f, 3, 0, 0);
    }

    /** A dust-free "puff" where something lands: a low wide ring. */
    public static void groundPuff(ServerLevel sl, int pal, Vec3 feet, float radius) {
        FxSend.ring(sl, pal, feet.add(0, 0.05, 0), radius * 0.2f, radius, 0.12f, 8);
    }

    /** A vertical column of energy. */
    public static void pillar(ServerLevel sl, int pal, Vec3 base, float height, float width, int life) {
        FxSend.beam(sl, pal, base, base.add(0, height, 0), width, life);
    }
}
