package com.ironspellgrimoire.network;

import com.ironspellgrimoire.client.FxRenderer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client: spawn (or kill) one 3D slash effect. See FxRenderer for the shapes. */
public class FxPacket {
    public static final byte WALL = 0, CRESCENT = 1, BEAM = 2, KILL = 3, BOX = 7, SPIKE = 8, SLASH = 13;
    /** BOX / SPIKE modes (sent in {@code speed}): pop in and out, rise out of / sink into the ground, extend along the axis. */
    public static final float POP = 0, GROUND = 1, EXTEND = 2, HOLD = 3;   // HOLD: full size its whole (short) life - for pieces re-sent every tick

    public byte shape, palette;
    public int id, life, sweep;
    public Vec3 c = Vec3.ZERO, d = Vec3.ZERO, u = Vec3.ZERO, v = Vec3.ZERO;   // v = velocity per tick (BOX / SPIKE)
    public float r, r2, a0, a1, h, speed;
    /** Sent as bit 0x40 of the palette: draw this effect only in the pixel-sprite style (no old solid shape). */
    public static final int PIXEL_ONLY = 0x40;
    /** Sent as bit 0x20 of the palette: keep this effect's solid 3D shape under its sprites (not pixel-only). */
    public static final int KEEP_SHAPE = 0x20;
    /** Client side, decoded from the palette bit (never sent itself). */
    public transient boolean pixelOnly;

    /*
     * Wire format: the header, then a bitmask saying which vectors / floats are non-zero, then only those.
     * Lossless: a value is skipped only when its bits are exactly 0 (so -0.0 is still sent), and a skipped value
     * decodes to exactly 0 - the client gets the very same packet, in far fewer bytes (most packets leave several
     * of the 4 vectors and 6 floats at zero).
     */
    private static boolean zero(Vec3 v) {
        return Double.doubleToRawLongBits(v.x) == 0L && Double.doubleToRawLongBits(v.y) == 0L && Double.doubleToRawLongBits(v.z) == 0L;
    }

    private static boolean zero(float f) { return Float.floatToRawIntBits(f) == 0; }

    public static void encode(FxPacket p, FriendlyByteBuf b) {
        b.writeByte(p.shape); b.writeByte(p.palette); b.writeVarInt(p.id); b.writeVarInt(p.life); b.writeVarInt(p.sweep);
        Vec3[] vs = {p.c, p.d, p.u, p.v};
        float[] fs = {p.r, p.r2, p.a0, p.a1, p.h, p.speed};
        int mask = 0;
        for (int i = 0; i < 4; i++) if (!zero(vs[i])) mask |= 1 << i;
        for (int i = 0; i < 6; i++) if (!zero(fs[i])) mask |= 1 << (4 + i);
        b.writeShort(mask);
        for (int i = 0; i < 4; i++) if ((mask & (1 << i)) != 0) vec(b, vs[i]);
        for (int i = 0; i < 6; i++) if ((mask & (1 << (4 + i))) != 0) b.writeFloat(fs[i]);
    }

    public static FxPacket decode(FriendlyByteBuf b) {
        FxPacket p = new FxPacket();
        p.shape = b.readByte(); p.palette = b.readByte(); p.id = b.readVarInt(); p.life = b.readVarInt(); p.sweep = b.readVarInt();
        int mask = b.readShort() & 0xFFFF;
        Vec3[] vs = new Vec3[4];
        for (int i = 0; i < 4; i++) vs[i] = (mask & (1 << i)) != 0 ? vec(b) : Vec3.ZERO;
        p.c = vs[0]; p.d = vs[1]; p.u = vs[2]; p.v = vs[3];
        float[] fs = new float[6];
        for (int i = 0; i < 6; i++) fs[i] = (mask & (1 << (4 + i))) != 0 ? b.readFloat() : 0f;
        p.r = fs[0]; p.r2 = fs[1]; p.a0 = fs[2]; p.a1 = fs[3]; p.h = fs[4]; p.speed = fs[5];
        return p;
    }

    private static void vec(FriendlyByteBuf b, Vec3 v) { b.writeDouble(v.x); b.writeDouble(v.y); b.writeDouble(v.z); }
    private static Vec3 vec(FriendlyByteBuf b) { return new Vec3(b.readDouble(), b.readDouble(), b.readDouble()); }

    public static void handle(FxPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> FxRenderer.spawn(p)));
        ctx.get().setPacketHandled(true);
    }
}
