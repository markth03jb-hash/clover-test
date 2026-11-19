package com.ironspellgrimoire.network;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** Many effect packets in one message (server/FxBatch): everything a player would get from the effects of one server tick. */
public class FxBatchPacket {
    public final List<FxPacket> fx;

    public FxBatchPacket(List<FxPacket> fx) { this.fx = fx; }

    public static void encode(FxBatchPacket m, FriendlyByteBuf b) {
        b.writeVarInt(m.fx.size());
        for (FxPacket p : m.fx) FxPacket.encode(p, b);
    }

    public static FxBatchPacket decode(FriendlyByteBuf b) {
        int n = b.readVarInt();
        List<FxPacket> l = new ArrayList<>(n);
        for (int i = 0; i < n; i++) l.add(FxPacket.decode(b));
        return new FxBatchPacket(l);
    }

    public static void handle(FxBatchPacket m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> { for (FxPacket p : m.fx) com.ironspellgrimoire.client.FxRenderer.spawn(p); }));
        ctx.get().setPacketHandled(true);
    }
}
