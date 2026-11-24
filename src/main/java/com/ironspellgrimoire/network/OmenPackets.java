package com.ironspellgrimoire.network;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** The Grimoire of Ill Omen (server/OmenFx): which creatures near its mage carry Misfortune, and how full it is. */
public final class OmenPackets {
    private OmenPackets() {}

    public static class Marks {
        public final int[] ids;
        public final float[] fills;
        public Marks(int[] ids, float[] fills) { this.ids = ids; this.fills = fills; }
        public static void encode(Marks m, FriendlyByteBuf b) {
            b.writeVarInt(m.ids.length);
            for (int i = 0; i < m.ids.length; i++) { b.writeVarInt(m.ids[i]); b.writeByte(Math.round(m.fills[i] * 255) & 0xFF); }
        }
        public static Marks decode(FriendlyByteBuf b) {
            int n = Math.min(256, b.readVarInt());
            int[] ids = new int[n]; float[] f = new float[n];
            for (int i = 0; i < n; i++) { ids[i] = b.readVarInt(); f[i] = (b.readByte() & 0xFF) / 255f; }
            return new Marks(ids, f);
        }
        public static void handle(Marks m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.ironspellgrimoire.client.OmenClient.marks(m)));
            ctx.get().setPacketHandled(true);
        }
    }
}
