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
    private final boolean offhand;

    public SummonAnimPacket(int entityId, boolean offhand) { this.entityId = entityId; this.offhand = offhand; }

    public static void encode(SummonAnimPacket p, FriendlyByteBuf buf) { buf.writeVarInt(p.entityId); buf.writeBoolean(p.offhand); }

    public static SummonAnimPacket decode(FriendlyByteBuf buf) { return new SummonAnimPacket(buf.readVarInt(), buf.readBoolean()); }

    public static void handle(SummonAnimPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> GrimoireFloatRenderer.startAnim(p.entityId, p.offhand)));
        ctx.get().setPacketHandled(true);
    }
}
