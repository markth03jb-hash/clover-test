package com.ironspellgrimoire.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** The Masked Twins Grimoire's messages (server/TwinsFx, client/TwinsClient). */
public final class TwinsPackets {
    private TwinsPackets() {}

    /** Server -> its player: the twins' whole state as numbers (TwinsFx.V_*; open = show The Masks). */
    public static class Sync {
        public final int[] v;
        public final boolean open;
        public Sync(int[] v, boolean open) { this.v = v; this.open = open; }
        public static void encode(Sync p, FriendlyByteBuf b) { b.writeVarIntArray(p.v); b.writeBoolean(p.open); }
        public static Sync decode(FriendlyByteBuf b) { return new Sync(b.readVarIntArray(), b.readBoolean()); }
        public static void handle(Sync p, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.ironspellgrimoire.client.TwinsClient.sync(p)));
            ctx.get().setPacketHandled(true);
        }
    }

    /** Server -> everyone who sees a player: the mask on their face and the one at their hip (-1 none; client/TwinsMasks draws them). */
    public static class Look {
        public final int id, face, hip;
        public Look(int id, int face, int hip) { this.id = id; this.face = face; this.hip = hip; }
        public static void encode(Look p, FriendlyByteBuf b) { b.writeVarInt(p.id); b.writeVarInt(p.face + 1); b.writeVarInt(p.hip + 1); }
        public static Look decode(FriendlyByteBuf b) { return new Look(b.readVarInt(), b.readVarInt() - 1, b.readVarInt() - 1); }
        public static void handle(Look p, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.ironspellgrimoire.client.TwinsMasks.look(p.id, p.face, p.hip)));
            ctx.get().setPacketHandled(true);
        }
    }

    /** Client -> server: 1 swap, 2 cast (a wheel slot, or 100 + twin * 8 + art in the eclipse), 3 an art into a slot
     *  (twin * 100 + slot * 10 + art, art 9 = empty), 4 set a stone (twin * 10 + socket), 5 wear a title (99 = none),
     *  6 begin the trial, 7 open The Masks. */
    public static class Act {
        public final int action, arg;
        public Act(int action, int arg) { this.action = action; this.arg = arg; }
        public static void encode(Act p, FriendlyByteBuf b) { b.writeVarInt(p.action); b.writeVarInt(p.arg); }
        public static Act decode(FriendlyByteBuf b) { return new Act(b.readVarInt(), b.readVarInt()); }
        public static void handle(Act p, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                var sp = ctx.get().getSender();
                if (sp != null) com.ironspellgrimoire.server.TwinsFx.handle(sp, p.action, p.arg);
            });
            ctx.get().setPacketHandled(true);
        }
    }
}
