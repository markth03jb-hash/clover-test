package com.ironspellgrimoire.network;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/** The Mana Tree (server/SkillTree): client -> server, 0 learn node {@code id}, 1 reset the tree (at a Grimoire Altar). */
public final class TreePackets {
    private TreePackets() {}

    public static class Act {
        public final int action;
        public final String id;
        public Act(int action, String id) { this.action = action; this.id = id; }
        public static void encode(Act p, FriendlyByteBuf b) { b.writeVarInt(p.action); b.writeUtf(p.id, 48); }
        public static Act decode(FriendlyByteBuf b) { return new Act(b.readVarInt(), b.readUtf(48)); }
        public static void handle(Act p, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                var sp = ctx.get().getSender();
                if (sp != null) com.ironspellgrimoire.server.SkillTreeServer.act(sp, p.action, p.id);
            });
            ctx.get().setPacketHandled(true);
        }
    }
}
