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

    /** Ribbon-cross beam from p0 to p1. */
    public static void beam(ServerLevel sl, int pal, Vec3 p0, Vec3 p1, float width, int life) {
        FxPacket p = new FxPacket();
        p.shape = FxPacket.BEAM; p.palette = (byte) pal; p.id = IDS.getAndIncrement(); p.life = life;
        p.c = p0; p.d = p1.subtract(p0); p.r = width;
        send(sl, p0, p);
    }

    public static void kill(ServerLevel sl, int id, Vec3 near) {
        FxPacket p = new FxPacket();
        p.shape = FxPacket.KILL; p.id = id;
        send(sl, near, p);
    }
}
