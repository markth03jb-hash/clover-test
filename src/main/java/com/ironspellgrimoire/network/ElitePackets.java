package com.ironspellgrimoire.network;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** Server -> client: elite mobs (server/EliteMobs, client/EliteClient). */
public final class ElitePackets {
    private ElitePackets() {}

    /** One mob: rank, traits, flags (1 a Splitter's copy, 2 a ghost, 4 a twin's shadow), Ironhide plates broken, the
     *  Reflector's phase, and a Tether chain (to that entity until that game time). */
    public static class Sync {
        public final int id, rank, traits, flags, plates, phase, chainTo;
        public final long chainUntil;
        public Sync(int id, int rank, int traits, int flags, int plates, int phase, int chainTo, long chainUntil) {
            this.id = id; this.rank = rank; this.traits = traits; this.flags = flags; this.plates = plates; this.phase = phase; this.chainTo = chainTo; this.chainUntil = chainUntil;
        }
        public static void encode(Sync m, FriendlyByteBuf b) {
            b.writeVarInt(m.id); b.writeByte(m.rank); b.writeVarInt(m.traits); b.writeByte(m.flags); b.writeByte(m.plates); b.writeVarInt(m.phase); b.writeVarInt(m.chainTo + 1); b.writeLong(m.chainUntil);
        }
        public static Sync decode(FriendlyByteBuf b) {
            return new Sync(b.readVarInt(), b.readByte(), b.readVarInt(), b.readByte(), b.readByte(), b.readVarInt(), b.readVarInt() - 1, b.readLong());
        }
        public static void handle(Sync m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.ironspellgrimoire.client.EliteClient.set(m)));
            ctx.get().setPacketHandled(true);
        }
    }

    /** The player: the traits they've met (the Codex) and their own threat rank. */
    public static class Seen {
        public final int mask, threat;
        public Seen(int mask, int threat) { this.mask = mask; this.threat = threat; }
        public static void encode(Seen m, FriendlyByteBuf b) { b.writeVarInt(m.mask); b.writeByte(m.threat); }
        public static Seen decode(FriendlyByteBuf b) { return new Seen(b.readVarInt(), b.readByte()); }
        public static void handle(Seen m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.ironspellgrimoire.client.EliteClient.seen(m.mask, m.threat)));
            ctx.get().setPacketHandled(true);
        }
    }
}
