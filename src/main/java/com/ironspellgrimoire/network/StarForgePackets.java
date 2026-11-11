package com.ironspellgrimoire.network;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** The Star Forge at the Grimoire Altar: the server sends what the equipped grimoire has, the client answers with a purchase. */
public final class StarForgePackets {
    private StarForgePackets() {}

    /** One spell of the grimoire, how far it has been empowered (0-3) and its mutation (0 none, 1 Echo, 2 Siphon). */
    public record Spell(String id, int level, int mut) {}

    /** Server -> client: open (or refresh) the forge. */
    public static class Open {
        public final int stars, sigil, cores;
        public final boolean slot2, slot3;
        public final String book;
        public final List<Spell> spells;
        public Open(int stars, boolean slot2, boolean slot3, int sigil, int cores, String book, List<Spell> spells) { this.stars = stars; this.slot2 = slot2; this.slot3 = slot3; this.sigil = sigil; this.cores = cores; this.book = book; this.spells = spells; }
        public static void encode(Open p, FriendlyByteBuf b) {
            b.writeVarInt(p.stars); b.writeBoolean(p.slot2); b.writeBoolean(p.slot3); b.writeVarInt(p.sigil); b.writeVarInt(p.cores); b.writeUtf(p.book, 120);
            b.writeVarInt(p.spells.size());
            for (Spell s : p.spells) { b.writeUtf(s.id(), 80); b.writeVarInt(s.level()); b.writeVarInt(s.mut()); }
        }
        public static Open decode(FriendlyByteBuf b) {
            int stars = b.readVarInt(); boolean slot2 = b.readBoolean(); boolean slot3 = b.readBoolean(); int sigil = b.readVarInt(); int cores = b.readVarInt(); String book = b.readUtf(120);
            int n = b.readVarInt();
            List<Spell> l = new ArrayList<>();
            for (int i = 0; i < n; i++) l.add(new Spell(b.readUtf(80), b.readVarInt(), b.readVarInt()));
            return new Open(stars, slot2, slot3, sigil, cores, book, l);
        }
        public static void handle(Open p, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.ironspellgrimoire.client.StarForgeScreen.show(p)));
            ctx.get().setPacketHandled(true);
        }
    }

    /** Client -> server: 0 empower spell {@code arg}, 1 buy the second ink slot, 2 set cover sigil {@code arg}, 3 mutate: {@code arg} = "spellId|1 or 2". */
    public static class Act {
        public final int action;
        public final String arg;
        public Act(int action, String arg) { this.action = action; this.arg = arg; }
        public static void encode(Act p, FriendlyByteBuf b) { b.writeVarInt(p.action); b.writeUtf(p.arg, 80); }
        public static Act decode(FriendlyByteBuf b) { return new Act(b.readVarInt(), b.readUtf(80)); }
        public static void handle(Act p, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                var sp = ctx.get().getSender();
                if (sp != null) com.ironspellgrimoire.server.StarForge.act(sp, p.action, p.arg);
            });
            ctx.get().setPacketHandled(true);
        }
    }
}
