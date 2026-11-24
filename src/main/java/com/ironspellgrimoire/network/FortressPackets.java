package com.ironspellgrimoire.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** The Architect of Ruin Grimoire's messages (server/FortressFx, client/FortressClient). */
public final class FortressPackets {
    private FortressPackets() {}

    /** Server -> its player: the fortress's whole state (a small NBT tag); open = show The Fortress. */
    public static class Sync {
        public final CompoundTag tag;
        public final boolean open;
        public Sync(CompoundTag tag, boolean open) { this.tag = tag; this.open = open; }
        public static void encode(Sync p, FriendlyByteBuf b) { b.writeNbt(p.tag); b.writeBoolean(p.open); }
        public static Sync decode(FriendlyByteBuf b) { CompoundTag t = b.readNbt(); return new Sync(t == null ? new CompoundTag() : t, b.readBoolean()); }
        public static void handle(Sync p, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.ironspellgrimoire.client.FortressClient.sync(p.tag, p.open)));
            ctx.get().setPacketHandled(true);
        }
    }

    /** Client -> server: a menu action (server/FortressFx.handle). */
    public static class Act {
        public final int action, arg;
        public Act(int action, int arg) { this.action = action; this.arg = arg; }
        public static void encode(Act p, FriendlyByteBuf b) { b.writeVarInt(p.action); b.writeVarInt(p.arg); }
        public static Act decode(FriendlyByteBuf b) { return new Act(b.readVarInt(), b.readVarInt()); }
        public static void handle(Act p, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                var sp = ctx.get().getSender();
                if (sp != null) com.ironspellgrimoire.server.FortressFx.handle(sp, p.action, p.arg);
            });
            ctx.get().setPacketHandled(true);
        }
    }
}
