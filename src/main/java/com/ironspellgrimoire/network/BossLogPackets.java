package com.ironspellgrimoire.network;

import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** The boss ladder (server/BossLadder) for the Codex's Bosses chapter: tiers, kills, best times, falls, memory, streak. */
public final class BossLogPackets {
    private BossLogPackets() {}

    public static class Sync {
        public final CompoundTag data;
        public Sync(CompoundTag data) { this.data = data; }
        public static void encode(Sync m, FriendlyByteBuf b) { b.writeNbt(m.data); }
        public static Sync decode(FriendlyByteBuf b) { CompoundTag t = b.readNbt(); return new Sync(t == null ? new CompoundTag() : t); }
        public static void handle(Sync m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.ironspellgrimoire.client.BossLogClient.data = m.data));
            ctx.get().setPacketHandled(true);
        }
    }
}
