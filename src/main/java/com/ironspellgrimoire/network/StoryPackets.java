package com.ironspellgrimoire.network;

import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * ASHFALL (server/Story): the journal and tracker (Sync), a scene of dialogue to play (Talk), and what the player does
 * back - a scene read to its end, a vote, the Brand, a request (Act).
 */
public final class StoryPackets {
    private StoryPackets() {}

    public static class Sync {
        public final CompoundTag data;
        public Sync(CompoundTag data) { this.data = data; }
        public static void encode(Sync m, FriendlyByteBuf b) { b.writeNbt(m.data); }
        public static Sync decode(FriendlyByteBuf b) { CompoundTag t = b.readNbt(); return new Sync(t == null ? new CompoundTag() : t); }
        public static void handle(Sync m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.ironspellgrimoire.client.StoryClient.sync(m.data)));
            ctx.get().setPacketHandled(true);
        }
    }

    public static class Talk {
        public final CompoundTag data;
        public Talk(CompoundTag data) { this.data = data; }
        public static void encode(Talk m, FriendlyByteBuf b) { b.writeNbt(m.data); }
        public static Talk decode(FriendlyByteBuf b) { CompoundTag t = b.readNbt(); return new Talk(t == null ? new CompoundTag() : t); }
        public static void handle(Talk m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.ironspellgrimoire.client.StoryClient.talk(m.data)));
            ctx.get().setPacketHandled(true);
        }
    }

    public static class Act {
        public final CompoundTag data;
        public Act(CompoundTag data) { this.data = data; }
        public static void encode(Act m, FriendlyByteBuf b) { b.writeNbt(m.data); }
        public static Act decode(FriendlyByteBuf b) { CompoundTag t = b.readNbt(); return new Act(t == null ? new CompoundTag() : t); }
        public static void handle(Act m, Supplier<NetworkEvent.Context> ctx) {
            var p = ctx.get().getSender();
            ctx.get().enqueueWork(() -> { if (p != null) com.ironspellgrimoire.server.Story.act(p, m.data); });
            ctx.get().setPacketHandled(true);
        }
    }
}
