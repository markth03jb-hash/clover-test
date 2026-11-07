package com.example.arcaneaddon.network;

import com.example.arcaneaddon.client.GrimoireFloatRenderer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client: this player just started casting, so open the grimoire. */
public class GrimoireOpenPacket {
    private final int entityId;

    public GrimoireOpenPacket(int entityId) { this.entityId = entityId; }

    public static void encode(GrimoireOpenPacket p, FriendlyByteBuf buf) { buf.writeVarInt(p.entityId); }

    public static GrimoireOpenPacket decode(FriendlyByteBuf buf) { return new GrimoireOpenPacket(buf.readVarInt()); }

    public static void handle(GrimoireOpenPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> GrimoireFloatRenderer.openBook(p.entityId)));
        ctx.get().setPacketHandled(true);
    }
}
