package com.ironspellgrimoire.network;

import com.ironspellgrimoire.server.GrimoireLibrary;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** The Grimoire Library (server/GrimoireLibrary): the server sends its shelves, the client shows them (client/LibraryScreen). */
public final class LibraryPackets {
    private LibraryPackets() {}

    public static class Open {
        public final List<GrimoireLibrary.Entry> entries;
        public Open(List<GrimoireLibrary.Entry> entries) { this.entries = entries; }

        public static void encode(Open p, FriendlyByteBuf b) {
            b.writeVarInt(p.entries.size());
            for (GrimoireLibrary.Entry e : p.entries) {
                b.writeUUID(e.id()); b.writeUtf(e.name(), 40); b.writeVarInt(e.book()); b.writeVarInt(e.mastery()); b.writeVarInt(e.flags());
                b.writeVarInt(e.evolved()); b.writeVarInt(e.stars()); b.writeVarInt(e.memory()); b.writeVarInt(e.day());
            }
        }

        public static Open decode(FriendlyByteBuf b) {
            int n = Math.min(b.readVarInt(), 512);
            List<GrimoireLibrary.Entry> l = new ArrayList<>(n);
            for (int i = 0; i < n; i++)
                l.add(new GrimoireLibrary.Entry(b.readUUID(), b.readUtf(40), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt()));
            return new Open(l);
        }

        public static void handle(Open p, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.ironspellgrimoire.client.LibraryScreen.show(p.entries)));
            ctx.get().setPacketHandled(true);
        }
    }
}
