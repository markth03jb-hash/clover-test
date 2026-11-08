package com.example.arcaneaddon.server;

import com.example.arcaneaddon.network.AddonNetwork;
import com.example.arcaneaddon.network.FxPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.util.concurrent.atomic.AtomicInteger;

/** Sends 3D slash effects to every player near them. Palettes: 0 slayer (crimson/black), 1 dweller (violet/black), 2 fire, 3 ice, 4 lightning, 5 wind, 6 anti magic (white/black). */
public class FxSend {
    private static final AtomicInteger IDS = new AtomicInteger(1);

    private static void send(ServerLevel sl, Vec3 near, FxPacket p) {
        AddonNetwork.CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(near.x, near.y, near.z, 96, sl.dimension())), p);
    }

    /** Curved wall (arc of a circle in the plane d/u, extruded +-h along d x u). a0..a1 degrees; sweep = ticks to draw the swing. */
    public static int wall(ServerLevel sl, int pal, Vec3 c, Vec3 d, Vec3 u, float r, float r2, float a0, float a1, float h, int life, int sweep, float speed) {
        FxPacket p = new FxPacket();
        p.shape = FxPacket.WALL; p.palette = (byte) pal; p.id = IDS.getAndIncrement(); p.life = life; p.sweep = sweep;
        p.c = c; p.d = d.normalize(); p.u = u.normalize(); p.r = r; p.r2 = r2; p.a0 = a0; p.a1 = a1; p.h = h; p.speed = speed;
        send(sl, c, p);
        return p.id;
    }

    /** Flat expanding ring wall on the ground. */
    public static int ring(ServerLevel sl, int pal, Vec3 c, float r0, float r1, float h, int life) {
        return wall(sl, pal, c, new Vec3(1, 0, 0), new Vec3(0, 0, 1), r0, r1, -180, 180, h, life, 0, 0);
    }

    /** Moving crescent blade facing d, in the plane spanned by d and u. */
    public static int crescent(ServerLevel sl, int pal, Vec3 c, Vec3 d, Vec3 u, float r, float r2, int life, float speed) {
        FxPacket p = new FxPacket();
        p.shape = FxPacket.CRESCENT; p.palette = (byte) pal; p.id = IDS.getAndIncrement(); p.life = life;
        p.c = c; p.d = d.normalize(); p.u = u.normalize(); p.r = r; p.r2 = r2; p.speed = speed;
        send(sl, c, p);
        return p.id;
    }

    /**
     * One clean, solid blade slash: a crisp black crescent with a white-hot cutting edge, drawn in from one tip to the
     * other in two ticks, growing from radius r to r2 and moving along {@code d} at {@code speed}. {@code u} = the
     * direction the blade spans (its tips point that way).
     */
    public static int slash(ServerLevel sl, int pal, Vec3 c, Vec3 d, Vec3 u, float r, float r2, int life, float speed) {
        FxPacket p = new FxPacket();
        p.shape = FxPacket.SLASH; p.palette = (byte) pal; p.id = IDS.getAndIncrement(); p.life = life;
        p.c = c; p.d = d.normalize(); p.u = u.normalize(); p.r = r; p.r2 = r2; p.speed = speed;
        send(sl, c, p);
        return p.id;
    }

    /** A slash with a chosen blade thickness ({@code thick} 1 = normal, 2 = twice as thick). */
    public static int slash(ServerLevel sl, int pal, Vec3 c, Vec3 d, Vec3 u, float r, float r2, int life, float speed, float thick) {
        FxPacket p = new FxPacket();
        p.shape = FxPacket.SLASH; p.palette = (byte) pal; p.id = IDS.getAndIncrement(); p.life = life;
        p.c = c; p.d = d.normalize(); p.u = u.normalize(); p.r = r; p.r2 = r2; p.speed = speed; p.h = thick;
        send(sl, c, p);
        return p.id;
    }

    /** A trail segment: like a beam but the same width end to end, so per-tick segments join into one smooth streak. */
    public static void trail(ServerLevel sl, int pal, Vec3 p0, Vec3 p1, float width, int life) {
        FxPacket p = new FxPacket();
        p.shape = FxPacket.BEAM; p.palette = (byte) pal; p.id = IDS.getAndIncrement(); p.life = life;
        p.c = p0; p.d = p1.subtract(p0); p.r = width; p.h = 1;
        send(sl, p0.add(p1).scale(0.5), p);
    }

    /** Ribbon-cross beam from p0 to p1. */
    public static void beam(ServerLevel sl, int pal, Vec3 p0, Vec3 p1, float width, int life) {
        FxPacket p = new FxPacket();
        p.shape = FxPacket.BEAM; p.palette = (byte) pal; p.id = IDS.getAndIncrement(); p.life = life;
        p.c = p0; p.d = p1.subtract(p0); p.r = width;
        send(sl, p0, p);
    }

    /**
     * A solid, shaded 3D block of metal (or any palette): centre {@code c}, long axis {@code axis} (half length hl),
     * {@code up} (half height hh), and their cross product (half width hw).
     * mode: FxPacket.POP scales in/out, GROUND rises out of the floor along up and sinks back at the end,
     * EXTEND grows along the axis from its back end. rise = ticks to appear, spin = degrees per tick around the axis,
     * vel = blocks per tick it travels, moveTicks = how long it travels (0 = whole life).
     */
    public static int box(ServerLevel sl, int pal, Vec3 c, Vec3 axis, Vec3 up, float hl, float hw, float hh,
                          int life, int rise, float mode, float spin, Vec3 vel, int moveTicks) {
        FxPacket p = new FxPacket();
        if (mode == FxPacket.HOLD && life <= 2) life = 1;   // re-sent every tick: each copy only covers its own tick
        p.shape = FxPacket.BOX; p.palette = (byte) pal; p.id = IDS.getAndIncrement(); p.life = life; p.sweep = rise;
        p.c = c; p.d = axis.normalize(); p.u = up.normalize(); p.r = hl; p.r2 = hw; p.h = hh; p.speed = mode; p.a0 = spin;
        p.v = vel; p.a1 = moveTicks;
        send(sl, c, p);
        return p.id;
    }

    /** Still box (no travel). */
    public static int box(ServerLevel sl, int pal, Vec3 c, Vec3 axis, Vec3 up, float hl, float hw, float hh, int life, int rise, float mode) {
        return box(sl, pal, c, axis, up, hl, hw, hh, life, rise, mode, 0, Vec3.ZERO, 0);
    }

    /**
     * A faceted 3D spike (six-sided, with a collar) from {@code base} toward {@code dir}: length len, base radius rad.
     * Same mode / rise / spin / vel / moveTicks meaning as {@link #box}.
     */
    public static int spike(ServerLevel sl, int pal, Vec3 base, Vec3 dir, float len, float rad,
                            int life, int rise, float mode, float spin, Vec3 vel, int moveTicks) {
        FxPacket p = new FxPacket();
        if (mode == FxPacket.HOLD && life <= 2) life = 1;   // re-sent every tick: each copy only covers its own tick
        p.shape = FxPacket.SPIKE; p.palette = (byte) pal; p.id = IDS.getAndIncrement(); p.life = life; p.sweep = rise;
        p.c = base; p.d = dir.normalize(); p.r = len; p.r2 = rad; p.speed = mode; p.a0 = spin; p.v = vel; p.a1 = moveTicks;
        send(sl, base, p);
        return p.id;
    }

    /** Still spike (no travel). */
    public static int spike(ServerLevel sl, int pal, Vec3 base, Vec3 dir, float len, float rad, int life, int rise, float mode) {
        return spike(sl, pal, base, dir, len, rad, life, rise, mode, 0, Vec3.ZERO, 0);
    }

    public static void kill(ServerLevel sl, int id, Vec3 near) {
        FxPacket p = new FxPacket();
        p.shape = FxPacket.KILL; p.id = id;
        send(sl, near, p);
    }
}
