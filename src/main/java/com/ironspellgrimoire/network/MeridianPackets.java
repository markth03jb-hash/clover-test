package com.ironspellgrimoire.network;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** The Meridian Grimoire (server/MeridianFx): a mage's sun, for their sundial HUD and so everyone sees them grow by day. */
public final class MeridianPackets {
    private MeridianPackets() {}

    public static class Sync {
        public final int id, pride, zenith;
        public final boolean on, borrowed, borrowUsed;
        public final float meter, sun, banked;
        public Sync(int id, boolean on, float meter, float sun, int pride, int zenith, boolean borrowed, boolean borrowUsed, float banked) {
            this.id = id; this.on = on; this.meter = meter; this.sun = sun; this.pride = pride; this.zenith = zenith;
            this.borrowed = borrowed; this.borrowUsed = borrowUsed; this.banked = banked;
        }
        public static void encode(Sync m, FriendlyByteBuf b) {
            b.writeVarInt(m.id); b.writeBoolean(m.on); b.writeFloat(m.meter); b.writeFloat(m.sun); b.writeVarInt(m.pride); b.writeVarInt(m.zenith);
            b.writeBoolean(m.borrowed); b.writeBoolean(m.borrowUsed); b.writeFloat(m.banked);
        }
        public static Sync decode(FriendlyByteBuf b) {
            return new Sync(b.readVarInt(), b.readBoolean(), b.readFloat(), b.readFloat(), b.readVarInt(), b.readVarInt(), b.readBoolean(), b.readBoolean(), b.readFloat());
        }
        public static void handle(Sync m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.ironspellgrimoire.client.MeridianClient.sync(m)));
            ctx.get().setPacketHandled(true);
        }
    }
}
