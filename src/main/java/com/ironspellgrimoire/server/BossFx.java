package com.ironspellgrimoire.server;

import com.ironspellgrimoire.network.FxPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * How the bosses draw their magic. Plain effects are drawn as quick pixel sprites, which on a boss read as specks
 * popping in and out; a boss's blows and telegraphs are drawn here instead as solid, shaded 3D shapes (KEEP_SHAPE),
 * a little thicker and lasting longer, so every attack has a clear shape you can read - and see coming.
 */
public final class BossFx {
    private BossFx() {}

    private static final int K = FxPacket.KEEP_SHAPE;
    private static int pal(int p) { return (p & 0x1F) | K; }
    private static int life(int l) { return l <= 0 ? l : Math.round(l * 1.5f) + 3; }

    public static int ring(ServerLevel sl, int pal, Vec3 c, float r0, float r1, float h, int life) {
        return FxSend.ring(sl, pal(pal), c, r0, r1, Math.max(0.06f, h * 1.4f), life(life));
    }

    public static void trail(ServerLevel sl, int pal, Vec3 a, Vec3 b, float w, int life) {
        FxSend.trail(sl, pal(pal), a, b, w * 1.3f, life(life));
    }

    public static void beam(ServerLevel sl, int pal, Vec3 a, Vec3 b, float w, int life) {
        FxSend.beam(sl, pal(pal), a, b, w * 1.3f, life(life));
    }

    public static int wall(ServerLevel sl, int pal, Vec3 c, Vec3 d, Vec3 u, float r, float r2, float a0, float a1, float h, int life, int sweep, float speed) {
        return FxSend.wall(sl, pal(pal), c, d, u, r, r2, a0, a1, h * 1.3f, speed == 0 ? life(life) : life, sweep, speed);
    }

    public static int slash(ServerLevel sl, int pal, Vec3 c, Vec3 d, Vec3 u, float r, float r2, int life, float speed) {
        return FxSend.slash(sl, pal, c, d, u, r * 1.15f, r2, speed == 0 ? life(life) : life + 3, speed, 1.4f);
    }

    public static int slash(ServerLevel sl, int pal, Vec3 c, Vec3 d, Vec3 u, float r, float r2, int life, float speed, float thick) {
        return FxSend.slash(sl, pal, c, d, u, r * 1.15f, r2, speed == 0 ? life(life) : life + 3, speed, thick * 1.4f);
    }

    public static void orb(ServerLevel sl, int pal, Vec3 c, Vec3 vel, float r, int life) {
        Kit.orb(sl, pal(pal), c, vel, r * 1.3f, life <= 3 ? life : life + 4);
    }
}
