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

    /** One spell of the grimoire and how far it has been empowered (0-3). */
    public record Spell(String id, int level) {}

    /** Server -> client: open (or refresh) the forge. */
    public static class Open {
        public final int stars, gem;
        public final boolean slot2;
        public final String book;
        public final List<Spell> spells;
        public Open(int stars, boolean slot2, int gem, String book, List<Spell> spells) { this.stars = stars; this.slot2 = slot2; this.gem = gem; this.book = book; this.spells = spells; }
        public static void encode(Open p, FriendlyByteBuf b) {
            b.writeVarInt(p.stars); b.writeBoolean(p.slot2); b.writeVarInt(p.gem); b.writeUtf(p.book, 120);
            b.writeVarInt(p.spells.size());
            for (Spell s : p.spells) { b.writeUtf(s.id(), 80); b.writeVarInt(s.level()); }
        }
        public static Open decode(FriendlyByteBuf b) {
            int stars = b.readVarInt(); boolean slot2 = b.readBoolean(); int gem = b.readVarInt(); String book = b.readUtf(120);
            int n = b.readVarInt();
            List<Spell> l = new ArrayList<>();
            for (int i = 0; i < n; i++) l.add(new Spell(b.readUtf(80), b.readVarInt()));
            return new Open(stars, slot2, gem, book, l);
        }
        public static void handle(Open p, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.ironspellgrimoire.client.StarForgeScreen.show(p)));
            ctx.get().setPacketHandled(true);
        }
    }

    /** Client -> server: 0 empower spell {@code arg}, 1 buy the second ink slot, 2 set cover gem {@code arg}. */
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
