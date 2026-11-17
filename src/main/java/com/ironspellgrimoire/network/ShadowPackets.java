package com.ironspellgrimoire.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The Shadow Grimoire's packets: the army menu (server sends the list, the client answers with equip / rank up /
 * rename / release), the remnants a kill leaves (sent only to the grimoire user who made the kill, so only they see
 * them), and the client asking for the menu (its key).
 */
public final class ShadowPackets {
    private ShadowPackets() {}

    /** One shadow in the army, as the menu shows it. */
    public record Entry(int id, String type, String name, int level, int xp, int xpNext, int rank, boolean equipped, boolean exclusive,
                        int recovering, boolean canRankUp, int rankBlock, boolean stationed) {}

    /** Server -> client: open (or refresh) the army menu. */
    public static class Open {
        public final List<Entry> list;
        public final int slots, storage;
        /** Each exclusive shadow's Challenge: 0 can challenge, 1 fighting it, 2 beaten - raise it, 3 in your army. */
        public final int[] challenges;
        public Open(List<Entry> list, int slots, int storage, int[] challenges) { this.list = list; this.slots = slots; this.storage = storage; this.challenges = challenges; }
        public static void encode(Open p, FriendlyByteBuf b) {
            b.writeVarInt(p.slots); b.writeVarInt(p.storage); b.writeVarIntArray(p.challenges); b.writeVarInt(p.list.size());
            for (Entry e : p.list) {
                b.writeVarInt(e.id()); b.writeUtf(e.type(), 100); b.writeUtf(e.name(), 32); b.writeVarInt(e.level()); b.writeVarInt(e.xp()); b.writeVarInt(e.xpNext());
                b.writeVarInt(e.rank()); b.writeBoolean(e.equipped()); b.writeBoolean(e.exclusive()); b.writeVarInt(e.recovering()); b.writeBoolean(e.canRankUp()); b.writeVarInt(e.rankBlock()); b.writeBoolean(e.stationed());
            }
        }
        public static Open decode(FriendlyByteBuf b) {
            int slots = b.readVarInt(), storage = b.readVarInt();
            int[] ch = b.readVarIntArray();
            int n = b.readVarInt();
            List<Entry> l = new ArrayList<>();
            for (int i = 0; i < n; i++) l.add(new Entry(b.readVarInt(), b.readUtf(100), b.readUtf(32), b.readVarInt(), b.readVarInt(), b.readVarInt(),
                    b.readVarInt(), b.readBoolean(), b.readBoolean(), b.readVarInt(), b.readBoolean(), b.readVarInt(), b.readBoolean()));
            return new Open(l, slots, storage, ch);
        }
        public static void handle(Open p, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.ironspellgrimoire.client.ShadowArmyScreen.show(p)));
            ctx.get().setPacketHandled(true);
        }
    }

    /** Client -> server: 0 = open the menu, 1 = equip / unequip id, 2 = rank up id, 3 = rename id (name), 4 = release id,
     *  5 = challenge exclusive shadow id (an index into ShadowArmy.EXCLUSIVES), 6 = raise beaten exclusive shadow id,
     *  7 = station id here / call it back; 10+ the Status and Market windows (HunterAscension.handle). */
    public static class Act {
        public final int action, id;
        public final String name;
        public Act(int action, int id, String name) { this.action = action; this.id = id; this.name = name; }
        public static void encode(Act p, FriendlyByteBuf b) { b.writeVarInt(p.action); b.writeVarInt(p.id); b.writeUtf(p.name, 32); }
        public static Act decode(FriendlyByteBuf b) { return new Act(b.readVarInt(), b.readVarInt(), b.readUtf(32)); }
        public static void handle(Act p, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                var sp = ctx.get().getSender();
                if (sp != null) com.ironspellgrimoire.server.ShadowArmy.handle(sp, p.action, p.id, p.name);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** Server -> client: the hunter's Status (HunterAscension) - open it (open) or refresh it if it's showing. */
    public static class Status {
        public final boolean open, trialDone, reckoning, jobReady, openJob;
        public final int level, xp, xpNext, points, marks, army, lures, dayLeft, job;
        public final int[] stats, kinds, prog, goal;
        public Status(boolean open, int level, int xp, int xpNext, int points, int[] stats, int marks, int army, int lures,
                      int[] kinds, int[] prog, int[] goal, boolean trialDone, int dayLeft, boolean reckoning, int job, boolean jobReady, boolean openJob) {
            this.open = open; this.level = level; this.xp = xp; this.xpNext = xpNext; this.points = points; this.stats = stats; this.marks = marks;
            this.army = army; this.lures = lures; this.kinds = kinds; this.prog = prog; this.goal = goal; this.trialDone = trialDone;
            this.dayLeft = dayLeft; this.reckoning = reckoning; this.job = job; this.jobReady = jobReady; this.openJob = openJob;
        }
        public static void encode(Status p, FriendlyByteBuf b) {
            b.writeBoolean(p.open); b.writeVarInt(p.level); b.writeVarInt(p.xp); b.writeVarInt(p.xpNext); b.writeVarInt(p.points);
            b.writeVarIntArray(p.stats); b.writeVarInt(p.marks); b.writeVarInt(p.army); b.writeVarInt(p.lures);
            b.writeVarIntArray(p.kinds); b.writeVarIntArray(p.prog); b.writeVarIntArray(p.goal);
            b.writeBoolean(p.trialDone); b.writeVarInt(p.dayLeft); b.writeBoolean(p.reckoning);
            b.writeVarInt(p.job); b.writeBoolean(p.jobReady); b.writeBoolean(p.openJob);
        }
        public static Status decode(FriendlyByteBuf b) {
            boolean open = b.readBoolean();
            int level = b.readVarInt(), xp = b.readVarInt(), xpNext = b.readVarInt(), points = b.readVarInt();
            int[] stats = b.readVarIntArray();
            int marks = b.readVarInt(), army = b.readVarInt(), lures = b.readVarInt();
            int[] kinds = b.readVarIntArray(), prog = b.readVarIntArray(), goal = b.readVarIntArray();
            boolean trialDone = b.readBoolean(); int dayLeft = b.readVarInt(); boolean reckoning = b.readBoolean();
            return new Status(open, level, xp, xpNext, points, stats, marks, army, lures, kinds, prog, goal, trialDone, dayLeft, reckoning,
                    b.readVarInt(), b.readBoolean(), b.readBoolean());
        }
        public static void handle(Status p, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                if (p.openJob) com.ironspellgrimoire.client.JobScreen.show(p); else com.ironspellgrimoire.client.StatusScreen.show(p);
            }));
            ctx.get().setPacketHandled(true);
        }
    }

    /** Server -> client: the Market (HunterAscension) - your Shade Marks; open it (open) or refresh it. */
    public static class Market {
        public final int marks;
        public final boolean open;
        public Market(int marks, boolean open) { this.marks = marks; this.open = open; }
        public static void encode(Market p, FriendlyByteBuf b) { b.writeVarInt(p.marks); b.writeBoolean(p.open); }
        public static Market decode(FriendlyByteBuf b) { return new Market(b.readVarInt(), b.readBoolean()); }
        public static void handle(Market p, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.ironspellgrimoire.client.MarketScreen.show(p)));
            ctx.get().setPacketHandled(true);
        }
    }

    /** Server -> one client: a remnant appears (life > 0) or goes (life = 0). Only its maker is sent it. */
    public static class Remnant {
        public final int id, life, tries;
        public final String type;
        public final Vec3 pos;
        public final float yaw, scale;
        public Remnant(int id, String type, Vec3 pos, float yaw, float scale, int life, int tries) {
            this.id = id; this.type = type; this.pos = pos; this.yaw = yaw; this.scale = scale; this.life = life; this.tries = tries;
        }
        public static void encode(Remnant p, FriendlyByteBuf b) {
            b.writeVarInt(p.id); b.writeUtf(p.type, 100); b.writeDouble(p.pos.x); b.writeDouble(p.pos.y); b.writeDouble(p.pos.z);
            b.writeFloat(p.yaw); b.writeFloat(p.scale); b.writeVarInt(p.life); b.writeVarInt(p.tries);
        }
        public static Remnant decode(FriendlyByteBuf b) {
            return new Remnant(b.readVarInt(), b.readUtf(100), new Vec3(b.readDouble(), b.readDouble(), b.readDouble()), b.readFloat(), b.readFloat(), b.readVarInt(), b.readVarInt());
        }
        public static void handle(Remnant p, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.ironspellgrimoire.client.ShadowClient.remnant(p)));
            ctx.get().setPacketHandled(true);
        }
    }
}
