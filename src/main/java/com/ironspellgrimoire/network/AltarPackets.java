package com.ironspellgrimoire.network;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** The Grimoire Altar's menu: the server says what you can do there, the client answers with a choice. */
public final class AltarPackets {
    private AltarPackets() {}

    /** Server -> client: open the menu. inks[i] = how many Rift Inks of rule i you carry. */
    public static class Open {
        public final BlockPos pos;
        public final String book;
        /** exam: 0 no grimoire, 1 below mastery 5, 2 ready to take the Magic Knight Exam, 3 already passed. */
        public final int shards, bookShards, stars, exam;
        public final int[] inks;
        public Open(BlockPos pos, String book, int shards, int bookShards, int stars, int exam, int[] inks) {
            this.pos = pos; this.book = book; this.shards = shards; this.bookShards = bookShards; this.stars = stars; this.exam = exam; this.inks = inks;
        }
        public static void encode(Open p, FriendlyByteBuf b) {
            b.writeBlockPos(p.pos); b.writeUtf(p.book, 120); b.writeVarInt(p.shards); b.writeVarInt(p.bookShards); b.writeVarInt(p.stars); b.writeVarInt(p.exam);
            b.writeVarIntArray(p.inks);
        }
        public static Open decode(FriendlyByteBuf b) {
            return new Open(b.readBlockPos(), b.readUtf(120), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarIntArray());
        }
        public static void handle(Open p, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.ironspellgrimoire.client.AltarScreen.show(p)));
            ctx.get().setPacketHandled(true);
        }
    }

    /** Client -> server: 0 receive a grimoire, 1 the Star Forge, 2 forge a Devil Shard, 3 inscribe Rift Ink {@code arg}, 4 the Magic Knight Exam. */
    public static class Act {
        public final BlockPos pos;
        public final int action, arg;
        public Act(BlockPos pos, int action, int arg) { this.pos = pos; this.action = action; this.arg = arg; }
        public static void encode(Act p, FriendlyByteBuf b) { b.writeBlockPos(p.pos); b.writeVarInt(p.action); b.writeVarInt(p.arg); }
        public static Act decode(FriendlyByteBuf b) { return new Act(b.readBlockPos(), b.readVarInt(), b.readVarInt()); }
        public static void handle(Act p, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                var sp = ctx.get().getSender();
                if (sp != null) com.ironspellgrimoire.server.AltarMenu.act(sp, p.pos, p.action, p.arg);
            });
            ctx.get().setPacketHandled(true);
        }
    }
}
