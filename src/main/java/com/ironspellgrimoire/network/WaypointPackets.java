package com.ironspellgrimoire.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** The Spatial Grimoire's waypoint menu: the server sends the list, the client answers with save / go / delete. */
public final class WaypointPackets {
    private WaypointPackets() {}

    /** One saved place, as the menu shows it. */
    public record Entry(String name, String dim, int x, int y, int z) {}

    /** Server -> client: open (or refresh) the menu with these waypoints. */
    public static class Open {
        public final List<Entry> list;
        public Open(List<Entry> list) { this.list = list; }
        public static void encode(Open p, FriendlyByteBuf b) {
            b.writeVarInt(p.list.size());
            for (Entry e : p.list) { b.writeUtf(e.name(), 40); b.writeUtf(e.dim(), 80); b.writeInt(e.x()); b.writeInt(e.y()); b.writeInt(e.z()); }
        }
        public static Open decode(FriendlyByteBuf b) {
            int n = b.readVarInt();
            List<Entry> l = new ArrayList<>();
            for (int i = 0; i < n; i++) l.add(new Entry(b.readUtf(40), b.readUtf(80), b.readInt(), b.readInt(), b.readInt()));
            return new Open(l);
        }
        public static void handle(Open p, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.ironspellgrimoire.client.WaypointScreen.show(p.list)));
            ctx.get().setPacketHandled(true);
        }
    }

    /** Client -> server: what the player chose. action 0 = save here (named), 1 = go to index, 2 = delete index. */
    public static class Act {
        public final int action, index;
        public final String name;
        public Act(int action, int index, String name) { this.action = action; this.index = index; this.name = name; }
        public static void encode(Act p, FriendlyByteBuf b) { b.writeVarInt(p.action); b.writeVarInt(p.index); b.writeUtf(p.name, 40); }
        public static Act decode(FriendlyByteBuf b) { return new Act(b.readVarInt(), b.readVarInt(), b.readUtf(40)); }
        public static void handle(Act p, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                var sp = ctx.get().getSender();
                if (sp != null) com.ironspellgrimoire.server.Waypoints.handle(sp, p.action, p.index, p.name);
            });
            ctx.get().setPacketHandled(true);
        }
    }
}
