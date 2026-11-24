package com.ironspellgrimoire.network;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** Rivals (server/Rivals): your records against them, Mira's deals on screen, and your answer. */
public final class RivalPackets {
    private RivalPackets() {}

    /** Server -> client: per rival, meetings, wins and losses (the Codex). */
    public static class Sync {
        public final int[] met, wins, losses;
        public Sync(int[] met, int[] wins, int[] losses) { this.met = met; this.wins = wins; this.losses = losses; }
        public static void encode(Sync m, FriendlyByteBuf b) {
            b.writeVarInt(m.met.length);
            for (int i = 0; i < m.met.length; i++) { b.writeVarInt(m.met[i]); b.writeVarInt(m.wins[i]); b.writeVarInt(m.losses[i]); }
        }
        public static Sync decode(FriendlyByteBuf b) {
            int n = Math.min(16, b.readVarInt());
            int[] met = new int[n], wins = new int[n], losses = new int[n];
            for (int i = 0; i < n; i++) { met[i] = b.readVarInt(); wins[i] = b.readVarInt(); losses[i] = b.readVarInt(); }
            return new Sync(met, wins, losses);
        }
        public static void handle(Sync m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.ironspellgrimoire.client.RivalClient.records(m)));
            ctx.get().setPacketHandled(true);
        }
    }

    /** Server -> client: Mira offers a deal (deal -1: the offer is gone). */
    public static class Offer {
        public final int deal, seconds;
        public final String name, terms;
        public Offer(int deal, String name, String terms, int seconds) { this.deal = deal; this.name = name; this.terms = terms; this.seconds = seconds; }
        public static void encode(Offer m, FriendlyByteBuf b) { b.writeVarInt(m.deal + 1); b.writeUtf(m.name, 64); b.writeUtf(m.terms, 256); b.writeVarInt(m.seconds); }
        public static Offer decode(FriendlyByteBuf b) { return new Offer(b.readVarInt() - 1, b.readUtf(64), b.readUtf(256), b.readVarInt()); }
        public static void handle(Offer m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.ironspellgrimoire.client.RivalClient.offer(m)));
            ctx.get().setPacketHandled(true);
        }
    }

    /** Client -> server: yes or no to Mira's deal. */
    public static class Answer {
        public final boolean yes;
        public Answer(boolean yes) { this.yes = yes; }
        public static void encode(Answer m, FriendlyByteBuf b) { b.writeBoolean(m.yes); }
        public static Answer decode(FriendlyByteBuf b) { return new Answer(b.readBoolean()); }
        public static void handle(Answer m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            ctx.get().enqueueWork(() -> { if (p != null) com.ironspellgrimoire.server.MiraAI.answer(p, m.yes); });
            ctx.get().setPacketHandled(true);
        }
    }
}
