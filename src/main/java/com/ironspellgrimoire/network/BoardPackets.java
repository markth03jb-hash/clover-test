package com.ironspellgrimoire.network;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** The village boards' menus (server/Boards, client/BoardScreen). */
public final class BoardPackets {
    private BoardPackets() {}

    public static class Note {
        public String title = "", twist = "", text = "", where = "", pay = "";
        public int special, icon;
    }

    public static class Open {
        public int kind, stars;
        public String active = "", wait = "";
        public final List<Note> notes = new ArrayList<>();

        public static void encode(Open p, FriendlyByteBuf b) {
            b.writeVarInt(p.kind); b.writeVarInt(p.stars); b.writeUtf(p.active, 200); b.writeUtf(p.wait, 200);
            b.writeVarInt(p.notes.size());
            for (Note n : p.notes) {
                b.writeUtf(n.title, 100); b.writeUtf(n.twist, 200); b.writeUtf(n.text, 400); b.writeUtf(n.where, 100); b.writeUtf(n.pay, 100);
                b.writeVarInt(n.special); b.writeVarInt(n.icon);
            }
        }

        public static Open decode(FriendlyByteBuf b) {
            Open p = new Open();
            p.kind = b.readVarInt(); p.stars = b.readVarInt(); p.active = b.readUtf(200); p.wait = b.readUtf(200);
            int n = Math.min(8, b.readVarInt());
            for (int i = 0; i < n; i++) {
                Note x = new Note();
                x.title = b.readUtf(100); x.twist = b.readUtf(200); x.text = b.readUtf(400); x.where = b.readUtf(100); x.pay = b.readUtf(100);
                x.special = b.readVarInt(); x.icon = b.readVarInt();
                p.notes.add(x);
            }
            return p;
        }

        public static void handle(Open p, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.ironspellgrimoire.client.BoardScreen.show(p)));
            ctx.get().setPacketHandled(true);
        }
    }

    public static class Pick {
        public final int kind, index;
        public Pick(int kind, int index) { this.kind = kind; this.index = index; }
        public static void encode(Pick p, FriendlyByteBuf b) { b.writeVarInt(p.kind); b.writeVarInt(p.index + 1); }
        public static Pick decode(FriendlyByteBuf b) { return new Pick(b.readVarInt(), b.readVarInt() - 1); }
        public static void handle(Pick p, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> { var sp = ctx.get().getSender(); if (sp != null) com.ironspellgrimoire.server.Boards.pick(sp, p.kind, p.index); });
            ctx.get().setPacketHandled(true);
        }
    }
}
