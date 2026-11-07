package com.example.arcaneaddon.network;

import com.example.arcaneaddon.client.FxRenderer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client: spawn (or kill) one 3D slash effect. See FxRenderer for the shapes. */
public class FxPacket {
    public static final byte WALL = 0, CRESCENT = 1, BEAM = 2, KILL = 3;

    public byte shape, palette;
    public int id, life, sweep;
    public Vec3 c = Vec3.ZERO, d = Vec3.ZERO, u = Vec3.ZERO;
    public float r, r2, a0, a1, h, speed;

    public static void encode(FxPacket p, FriendlyByteBuf b) {
        b.writeByte(p.shape); b.writeByte(p.palette); b.writeVarInt(p.id); b.writeVarInt(p.life); b.writeVarInt(p.sweep);
        vec(b, p.c); vec(b, p.d); vec(b, p.u);
        b.writeFloat(p.r); b.writeFloat(p.r2); b.writeFloat(p.a0); b.writeFloat(p.a1); b.writeFloat(p.h); b.writeFloat(p.speed);
    }

    public static FxPacket decode(FriendlyByteBuf b) {
        FxPacket p = new FxPacket();
        p.shape = b.readByte(); p.palette = b.readByte(); p.id = b.readVarInt(); p.life = b.readVarInt(); p.sweep = b.readVarInt();
        p.c = vec(b); p.d = vec(b); p.u = vec(b);
        p.r = b.readFloat(); p.r2 = b.readFloat(); p.a0 = b.readFloat(); p.a1 = b.readFloat(); p.h = b.readFloat(); p.speed = b.readFloat();
        return p;
    }

    private static void vec(FriendlyByteBuf b, Vec3 v) { b.writeDouble(v.x); b.writeDouble(v.y); b.writeDouble(v.z); }
    private static Vec3 vec(FriendlyByteBuf b) { return new Vec3(b.readDouble(), b.readDouble(), b.readDouble()); }

    public static void handle(FxPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> FxRenderer.spawn(p)));
        ctx.get().setPacketHandled(true);
    }
}
