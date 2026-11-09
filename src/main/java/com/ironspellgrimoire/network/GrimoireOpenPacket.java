package com.ironspellgrimoire.network;

import com.ironspellgrimoire.client.GrimoireFloatRenderer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client: this player just started casting, so open the grimoire. */
public class GrimoireOpenPacket {
    private final int entityId;
    /** true: a cast (flip through the pages); false: combat only (just keep the book open for 10 seconds). */
    private final boolean flip;

    public GrimoireOpenPacket(int entityId) { this(entityId, true); }
    public GrimoireOpenPacket(int entityId, boolean flip) { this.entityId = entityId; this.flip = flip; }

    public static void encode(GrimoireOpenPacket p, FriendlyByteBuf buf) { buf.writeVarInt(p.entityId); buf.writeBoolean(p.flip); }

    public static GrimoireOpenPacket decode(FriendlyByteBuf buf) { return new GrimoireOpenPacket(buf.readVarInt(), buf.readBoolean()); }

    public static void handle(GrimoireOpenPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> { if (p.flip) GrimoireFloatRenderer.castFlip(p.entityId); else GrimoireFloatRenderer.openBook(p.entityId); }));
        ctx.get().setPacketHandled(true);
    }
}
