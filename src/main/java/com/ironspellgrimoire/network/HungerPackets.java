package com.ironspellgrimoire.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** The Hunger Blade Grimoire's messages (server/HungerFx, client/HungerClient). */
public final class HungerPackets {
    private HungerPackets() {}

    /** Server -> its player: the blade's whole state (the HUD, the wheel, The Blade page; open = show that page). */
    public static class Sync {
        public final boolean equipped, drawn, open;
        public final int growth, unlocked, sel, bond, fed, pact, titles, title, devours;
        public final int[] load;
        public final float hunger;
        public Sync(boolean equipped, int growth, int unlocked, int[] load, int sel, float hunger, int bond, boolean drawn, int fed, int pact, boolean open,
                    int titles, int title, int devours) {
            this.equipped = equipped; this.growth = growth; this.unlocked = unlocked; this.load = load; this.sel = sel; this.hunger = hunger;
            this.bond = bond; this.drawn = drawn; this.fed = fed; this.pact = pact; this.open = open;
            this.titles = titles; this.title = title; this.devours = devours;
        }
        public static void encode(Sync p, FriendlyByteBuf b) {
            b.writeBoolean(p.equipped); b.writeVarInt(p.growth); b.writeInt(p.unlocked); b.writeVarIntArray(p.load); b.writeVarInt(p.sel);
            b.writeFloat(p.hunger); b.writeVarInt(p.bond); b.writeBoolean(p.drawn); b.writeVarInt(p.fed); b.writeVarInt(p.pact); b.writeBoolean(p.open);
            b.writeVarInt(p.titles); b.writeVarInt(p.title + 1); b.writeVarInt(p.devours);
        }
        public static Sync decode(FriendlyByteBuf b) {
            return new Sync(b.readBoolean(), b.readVarInt(), b.readInt(), b.readVarIntArray(), b.readVarInt(), b.readFloat(), b.readVarInt(), b.readBoolean(),
                    b.readVarInt(), b.readVarInt(), b.readBoolean(), b.readVarInt(), b.readVarInt() - 1, b.readVarInt());
        }
        public static void handle(Sync p, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.ironspellgrimoire.client.HungerClient.sync(p)));
            ctx.get().setPacketHandled(true);
        }
    }

    /** Server -> everyone who sees a player: the sword sheathed at their hip (-1 none; client/HungerClient draws it). */
    public static class Look {
        public final int id, form;
        public Look(int id, int form) { this.id = id; this.form = form; }
        public static void encode(Look p, FriendlyByteBuf b) { b.writeVarInt(p.id); b.writeVarInt(p.form + 1); }
        public static Look decode(FriendlyByteBuf b) { return new Look(b.readVarInt(), b.readVarInt() - 1); }
        public static void handle(Look p, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.ironspellgrimoire.client.HungerClient.look(p.id, p.form)));
            ctx.get().setPacketHandled(true);
        }
    }

    /** Client -> server: 1 grow a sword (arg form), 2 switch to a slot, 3 put a sword in a slot (slot * 100 + form, 99 = empty),
     *  4 wear a title (99 = none). */
    public static class Act {
        public final int action, arg;
        public Act(int action, int arg) { this.action = action; this.arg = arg; }
        public static void encode(Act p, FriendlyByteBuf b) { b.writeVarInt(p.action); b.writeVarInt(p.arg); }
        public static Act decode(FriendlyByteBuf b) { return new Act(b.readVarInt(), b.readVarInt()); }
        public static void handle(Act p, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                var sp = ctx.get().getSender();
                if (sp != null) com.ironspellgrimoire.server.HungerFx.handle(sp, p.action, p.arg);
            });
            ctx.get().setPacketHandled(true);
        }
    }
}
