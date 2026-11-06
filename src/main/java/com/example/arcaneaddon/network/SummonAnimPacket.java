package com.example.arcaneaddon.network;

import com.example.arcaneaddon.client.GrimoireFloatRenderer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client: "play the sword-emerges-from-grimoire animation for this player". */
public class SummonAnimPacket {
    private final int entityId;

    public SummonAnimPacket(int entityId) { this.entityId = entityId; }

    public static void encode(SummonAnimPacket p, FriendlyByteBuf buf) { buf.writeVarInt(p.entityId); }

    public static SummonAnimPacket decode(FriendlyByteBuf buf) { return new SummonAnimPacket(buf.readVarInt()); }

    public static void handle(SummonAnimPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> GrimoireFloatRenderer.startAnim(p.entityId)));
        ctx.get().setPacketHandled(true);
    }
}
