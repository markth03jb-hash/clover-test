package com.ironspellgrimoire.network;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** The exclusive trees of the Vector, Quiet Fuse and Foreseen grimoires (server/BookTree): the state, and what the player asks. */
public final class BookTreePackets {
    private BookTreePackets() {}

    public static class Sync {
        public final int book, points, respec;
        public final float xp;
        public final String ranks;
        public final int[] cd, extra;
        public final boolean open;
        public Sync(int book, float xp, int points, String ranks, int[] cd, int[] extra, int respec, boolean open) {
            this.book = book; this.xp = xp; this.points = points; this.ranks = ranks; this.cd = cd; this.extra = extra; this.respec = respec; this.open = open;
        }
        public static void encode(Sync m, FriendlyByteBuf b) {
            b.writeByte(m.book); b.writeFloat(m.xp); b.writeVarInt(m.points); b.writeUtf(m.ranks, 2048);
            b.writeVarIntArray(m.cd); b.writeVarIntArray(m.extra); b.writeVarInt(m.respec); b.writeBoolean(m.open);
        }
        public static Sync decode(FriendlyByteBuf b) {
            return new Sync(b.readByte(), b.readFloat(), b.readVarInt(), b.readUtf(2048), b.readVarIntArray(8), b.readVarIntArray(64), b.readVarInt(), b.readBoolean());
        }
        public static void handle(Sync m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.ironspellgrimoire.client.BookTreeClient.sync(m)));
            ctx.get().setPacketHandled(true);
        }
    }

    /** 0 open the tree, 1 learn a node (id), 2 unlearn all, 3 use a wheel slot (arg). */
    public static class Act {
        public final int action, arg;
        public final String id;
        public Act(int action, int arg, String id) { this.action = action; this.arg = arg; this.id = id; }
        public static void encode(Act m, FriendlyByteBuf b) { b.writeByte(m.action); b.writeVarInt(m.arg); b.writeUtf(m.id, 16); }
        public static Act decode(FriendlyByteBuf b) { return new Act(b.readByte(), b.readVarInt(), b.readUtf(16)); }
        public static void handle(Act m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> { if (ctx.get().getSender() != null) com.ironspellgrimoire.server.BookTree.act(ctx.get().getSender(), m.action, m.arg, m.id); });
            ctx.get().setPacketHandled(true);
        }
    }
}
