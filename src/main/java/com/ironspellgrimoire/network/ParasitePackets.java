package com.ironspellgrimoire.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** The Parasite Grimoire's messages (server/ParasiteFx, client/ParasiteClient). */
public final class ParasitePackets {
    private ParasitePackets() {}

    /** Server -> its player: the whole strain (the HUD, the wheel, the Evolution page; open = show that page). */
    public static class Sync {
        public final boolean equipped, frenzied, bulwark, hide, open;
        public final float biomass, frenzy;
        public final int ep, spent, devoured, form, strain;
        public final int[] ranks, extra;
        public Sync(boolean equipped, float biomass, float frenzy, boolean frenzied, int ep, int spent, int devoured, int form, boolean bulwark,
                    boolean hide, int strain, int[] ranks, boolean open, int[] extra) {
            this.equipped = equipped; this.biomass = biomass; this.frenzy = frenzy; this.frenzied = frenzied; this.ep = ep; this.spent = spent;
            this.devoured = devoured; this.form = form; this.bulwark = bulwark; this.hide = hide; this.strain = strain; this.ranks = ranks; this.open = open;
            this.extra = extra;
        }
        public static void encode(Sync p, FriendlyByteBuf b) {
            b.writeBoolean(p.equipped); b.writeFloat(p.biomass); b.writeFloat(p.frenzy); b.writeBoolean(p.frenzied);
            b.writeVarInt(p.ep); b.writeVarInt(p.spent); b.writeVarInt(p.devoured); b.writeVarInt(p.form);
            b.writeBoolean(p.bulwark); b.writeBoolean(p.hide); b.writeVarInt(p.strain); b.writeVarIntArray(p.ranks); b.writeBoolean(p.open);
            b.writeVarInt(p.extra.length); for (int v : p.extra) b.writeInt(v);
        }
        public static Sync decode(FriendlyByteBuf b) {
            boolean eq = b.readBoolean(); float bio = b.readFloat(), fr = b.readFloat(); boolean frd = b.readBoolean();
            int ep = b.readVarInt(), spent = b.readVarInt(), dev = b.readVarInt(), form = b.readVarInt();
            boolean bw = b.readBoolean(), hd = b.readBoolean(); int st = b.readVarInt(); int[] rk = b.readVarIntArray(); boolean open = b.readBoolean();
            int[] ex = new int[b.readVarInt()]; for (int i = 0; i < ex.length; i++) ex[i] = b.readInt();
            return new Sync(eq, bio, fr, frd, ep, spent, dev, form, bw, hd, st, rk, open, ex);
        }
        public static void handle(Sync p, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.ironspellgrimoire.client.ParasiteClient.sync(p)));
            ctx.get().setPacketHandled(true);
        }
    }

    /** Server -> everyone watching: what a player's arms and body have become (drawn by client/ParasiteArms). */
    public static class Look {
        public final int id, form, flags;   // flags: 1 Colossus, 2 the Final Form
        public final boolean bulwark, hide, frenzied;
        public Look(int id, int form, boolean bulwark, boolean hide, boolean frenzied, int flags) {
            this.id = id; this.form = form; this.bulwark = bulwark; this.hide = hide; this.frenzied = frenzied; this.flags = flags;
        }
        public static void encode(Look p, FriendlyByteBuf b) { b.writeVarInt(p.id); b.writeVarInt(p.form); b.writeBoolean(p.bulwark); b.writeBoolean(p.hide); b.writeBoolean(p.frenzied); b.writeVarInt(p.flags); }
        public static Look decode(FriendlyByteBuf b) { return new Look(b.readVarInt(), b.readVarInt(), b.readBoolean(), b.readBoolean(), b.readBoolean(), b.readVarInt()); }
        public static void handle(Look p, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.ironspellgrimoire.client.ParasiteArms.look(p)));
            ctx.get().setPacketHandled(true);
        }
    }

    /** Server -> its player: the Hive Network's travel menu (one "dim|x|y|z|cooldown s|here" row a nest). */
    public static class Nests {
        public final java.util.List<String> rows;
        public Nests(java.util.List<String> rows) { this.rows = rows; }
        public static void encode(Nests p, FriendlyByteBuf b) { b.writeVarInt(p.rows.size()); for (String r : p.rows) b.writeUtf(r, 200); }
        public static Nests decode(FriendlyByteBuf b) {
            int n = b.readVarInt(); java.util.List<String> l = new java.util.ArrayList<>();
            for (int i = 0; i < n; i++) l.add(b.readUtf(200));
            return new Nests(l);
        }
        public static void handle(Nests p, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.ironspellgrimoire.client.NestScreen.show(p.rows)));
            ctx.get().setPacketHandled(true);
        }
    }

    /** Client -> server: the wheel, the movement, the Evolution page (server/ParasiteFx.handle). */
    public static class Act {
        public final int action, arg;
        public Act(int action, int arg) { this.action = action; this.arg = arg; }
        public static void encode(Act p, FriendlyByteBuf b) { b.writeVarInt(p.action); b.writeVarInt(p.arg); }
        public static Act decode(FriendlyByteBuf b) { return new Act(b.readVarInt(), b.readVarInt()); }
        public static void handle(Act p, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                var sp = ctx.get().getSender();
                if (sp != null) com.ironspellgrimoire.server.ParasiteFx.handle(sp, p.action, p.arg);
            });
            ctx.get().setPacketHandled(true);
        }
    }
}
