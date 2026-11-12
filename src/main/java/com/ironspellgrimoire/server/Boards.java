package com.ironspellgrimoire.server;

import com.ironspellgrimoire.network.AddonNetwork;
import com.ironspellgrimoire.network.BoardPackets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.network.PacketDistributor;

/**
 * The village boards' menus: the Poster Board (bounties) and the Quest Board each show three notes to choose from
 * (client/BoardScreen) instead of handing out one at random. The notes are each mage's own - kept until one is taken, a
 * day passes, or they read a board in another village - and show everything: the target, its twists, roughly where,
 * and what it pays. With a hunt or quest under way, the board shows that instead, with a way to give it up.
 */
public final class Boards {
    private Boards() {}

    public static final int BOUNTY = 0, QUEST = 1;
    private static final String[] KEY = {"isg_bounty_offers", "isg_quest_offers"};

    private static ListTag offers(ServerPlayer p, int kind, BlockPos pos) {
        CompoundTag d = p.getPersistentData();
        CompoundTag box = d.getCompound(KEY[kind]);
        long now = p.serverLevel().getGameTime();
        boolean stale = !box.contains("List") || now - box.getLong("At") > 24000
                || pos.distSqr(new BlockPos(box.getInt("BX"), pos.getY(), box.getInt("BZ"))) > 300 * 300;
        if (!stale && kind == QUEST && Quests.dailyDue(p)) {   // a new day's special replaces the first note
            ListTag l = box.getList("List", Tag.TAG_COMPOUND);
            boolean has = false;
            for (Tag t : l) if (((CompoundTag) t).getBoolean("Daily")) has = true;
            if (!has) stale = true;
        }
        if (stale) {
            box = new CompoundTag();
            box.putLong("At", now); box.putInt("BX", pos.getX()); box.putInt("BZ", pos.getZ());
            box.put("List", kind == BOUNTY ? bountyNotes(p, pos) : questNotes(p, pos));
            d.put(KEY[kind], box);
        }
        return box.getList("List", Tag.TAG_COMPOUND);
    }

    private static CompoundTag at(BlockPos pos, double min, double max) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double a = r.nextDouble(Math.PI * 2), d = r.nextDouble(min, max);
        CompoundTag o = new CompoundTag();
        o.putInt("X", (int) (pos.getX() + Math.cos(a) * d));
        o.putInt("Z", (int) (pos.getZ() + Math.sin(a) * d));
        return o;
    }

    private static ListTag bountyNotes(ServerPlayer p, BlockPos pos) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        List<Integer> bosses = new ArrayList<>();
        for (int i = 0; i < Bounties.SECRET; i++) bosses.add(i);
        java.util.Collections.shuffle(bosses);
        ListTag l = new ListTag();
        for (int i = 0; i < 3; i++) {
            int boss = i == 2 && Bounties.stars(p) >= Bounties.SECRET_AT && r.nextInt(5) == 0 ? Bounties.SECRET : bosses.get(i);   // now and then a poster with no name on it
            CompoundTag o = at(pos, 400, 900);
            o.putInt("Boss", boss);
            o.putInt("Mods", Bounties.rollMods(p, boss));
            l.add(o);
        }
        return l;
    }

    private static ListTag questNotes(ServerPlayer p, BlockPos pos) {
        List<Integer> pool = new ArrayList<>(List.of(Quests.MAGES, Quests.MONSTERS, Quests.RIFT, Quests.PAGE, Quests.BEAST));
        if (Bounties.stars(p) >= 10) pool.add(Quests.BOSS);
        java.util.Collections.shuffle(pool);
        boolean daily = Quests.dailyDue(p);
        ListTag l = new ListTag();
        for (int i = 0; i < 3; i++) {
            CompoundTag o = at(pos, 150, 350);
            o.putInt("Type", pool.get(i));
            if (daily && i == 0) { o.putBoolean("Daily", true); o.putInt("Mods", Quests.rollDaily()); }
            l.add(o);
        }
        return l;
    }

    /** The board is read: its menu opens. */
    public static void open(ServerPlayer p, int kind, BlockPos pos) {
        long now = p.serverLevel().getGameTime();
        BoardPackets.Open o = new BoardPackets.Open();
        o.kind = kind;
        o.stars = Bounties.stars(p);
        if (kind == BOUNTY ? Bounties.hunting(p) : Quests.questing(p)) {
            o.active = kind == BOUNTY ? "Hunting " + Bounties.huntingName(p) : Quests.questTitle(p);
        } else {
            long next = kind == BOUNTY ? Bounties.nextAt(p) : Quests.nextAt(p);
            if (now < next) o.wait = kind == BOUNTY ? "No new posters yet - come back in about " + Math.max(1, (next - now) / 1000) + " h."
                    : "Nothing new on the board yet - come back in a minute or two.";
            for (Tag t : offers(p, kind, pos)) {
                CompoundTag n = (CompoundTag) t;
                double dx = n.getInt("X") - p.getX(), dz = n.getInt("Z") - p.getZ();
                String where = "About " + (int) Math.hypot(dx, dz) + " blocks " + Bounties.direction(dx, dz);
                BoardPackets.Note note = new BoardPackets.Note();
                if (kind == BOUNTY) {
                    int boss = n.getInt("Boss"), mods = n.getInt("Mods");
                    note.title = Bounties.NAMES[Math.min(Bounties.NAMES.length - 1, boss)];
                    note.twist = Bounties.modsText(mods).getString();
                    note.text = boss == Bounties.SECRET ? "No name, no face. Only a price." : "Three days to bring them down.";
                    note.where = where;
                    note.pay = Bounties.starsFor(boss, mods) + "★  emeralds" + (boss == Bounties.SECRET ? "  a relic" : "");
                    note.special = boss == Bounties.SECRET ? 2 : 0;
                    note.icon = boss;
                } else {
                    int type = n.getInt("Type");
                    boolean daily = n.getBoolean("Daily");
                    boolean big = type == Quests.RIFT || type == Quests.BOSS || type == Quests.BEAST || type == Quests.PAGE;
                    note.title = Quests.TITLES[type];
                    note.twist = daily ? "Twists: " + Quests.modsText(n.getInt("Mods")) : "";
                    note.text = Quests.briefOf(type);
                    note.where = where;
                    note.pay = (big ? 3 : 1) * (daily ? 2 : 1) + "★  emeralds" + (daily ? "  Mana Core  Rift Ink" : "");
                    note.special = daily ? 1 : 0;
                    note.icon = type;
                }
                o.notes.add(note);
            }
        }
        AddonNetwork.send(PacketDistributor.PLAYER.with(() -> p), o);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8f, 1.0f);
    }

    /** A note taken (index), or the current one given up (-1). */
    public static void pick(ServerPlayer p, int kind, int index) {
        if (index < 0) {
            if (kind == BOUNTY) Bounties.cancel(p); else Quests.giveUp(p);
            return;
        }
        if (kind == BOUNTY ? Bounties.hunting(p) : Quests.questing(p)) return;
        long now = p.serverLevel().getGameTime();
        if (now < (kind == BOUNTY ? Bounties.nextAt(p) : Quests.nextAt(p))) return;
        CompoundTag box = p.getPersistentData().getCompound(KEY[kind]);
        ListTag l = box.getList("List", Tag.TAG_COMPOUND);
        if (index >= l.size()) return;
        CompoundTag o = l.getCompound(index);
        p.getPersistentData().remove(KEY[kind]);   // fresh notes next time
        if (kind == BOUNTY) Bounties.takeOffer(p, o); else Quests.takeOffer(p, o);
    }
}
