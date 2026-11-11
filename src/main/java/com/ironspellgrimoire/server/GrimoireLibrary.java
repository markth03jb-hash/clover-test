package com.ironspellgrimoire.server;

import com.ironspellgrimoire.IronSpellGrimoire;
import com.ironspellgrimoire.network.AddonNetwork;
import com.ironspellgrimoire.network.LibraryPackets;
import io.redspace.ironsspellbooks.api.util.Utils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * The Grimoire Library: the server's hall of fame. Every mage a grimoire has ever chosen gets a copy of their grimoire
 * on its shelves - who they are, which grimoire, its mastery, whether it awoke or ascended, whether they passed the
 * Magic Knight Exam, how many of its spells evolved, their merit stars, how much of the first binder's story they
 * recovered, and the day they were chosen. Kept with the world (SavedData "isg_grimoire_library"), refreshed from each
 * mage while they're online. Read it at the Grimoire Archive shelves in a Grimoire Tower, or from any Grimoire Altar's
 * Library ribbon (client/LibraryScreen).
 */
@Mod.EventBusSubscriber(modid = IronSpellGrimoire.MODID)
public final class GrimoireLibrary {
    private GrimoireLibrary() {}

    /** One mage's entry. flags: 1 awakened, 2 ascended, 4 passed the Magic Knight Exam. */
    public record Entry(UUID id, String name, int book, int mastery, int flags, int evolved, int stars, int memory, int day) {}

    public static final class Data extends SavedData {
        final Map<UUID, Entry> entries = new LinkedHashMap<>();

        static Data load(CompoundTag t) {
            Data d = new Data();
            for (Tag x : t.getList("Mages", Tag.TAG_COMPOUND)) {
                CompoundTag m = (CompoundTag) x;
                if (!m.hasUUID("Id")) continue;
                d.entries.put(m.getUUID("Id"), new Entry(m.getUUID("Id"), m.getString("Name"), m.getInt("Book"), m.getInt("Mastery"), m.getInt("Flags"),
                        m.getInt("Evolved"), m.getInt("Stars"), m.getInt("Memory"), m.getInt("Day")));
            }
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag t) {
            ListTag l = new ListTag();
            for (Entry e : entries.values()) {
                CompoundTag m = new CompoundTag();
                m.putUUID("Id", e.id()); m.putString("Name", e.name()); m.putInt("Book", e.book()); m.putInt("Mastery", e.mastery());
                m.putInt("Flags", e.flags()); m.putInt("Evolved", e.evolved()); m.putInt("Stars", e.stars()); m.putInt("Memory", e.memory()); m.putInt("Day", e.day());
                l.add(m);
            }
            t.put("Mages", l);
            return t;
        }
    }

    static Data data(MinecraftServer s) {
        return s.overworld().getDataStorage().computeIfAbsent(Data::load, Data::new, "isg_grimoire_library");
    }

    /** Writes (or refreshes) this mage's entry from what they carry now. */
    public static void record(ServerPlayer p) {
        int chosen = GrimoireCeremony.chosen(p);
        if (chosen < 0) return;
        Data d = data(p.server);
        Entry old = d.entries.get(p.getUUID());
        int mastery = old != null && old.book() == chosen ? old.mastery() : 1, flags = old != null && old.book() == chosen ? old.flags() : 0,
                evolved = old != null && old.book() == chosen ? old.evolved() : 0;
        ItemStack book = Utils.getPlayerSpellbookStack(p);
        GrimoireMastery.Book b = GrimoireMastery.bookOf(book);
        if (b != null && GrimoireMastery.BOOKS.indexOf(b) == chosen) {   // their own grimoire, equipped: read it
            mastery = GrimoireMastery.level(book);
            flags = (Awakening.awakened(book) ? 1 : 0) | (Ascension.ascended(book) ? 2 : 0) | (GrimoireMastery.ascended(book) ? 4 : 0);
            evolved = book.hasTag() ? book.getTag().getCompound("Evolved").size() : 0;
        }
        int day = old != null ? old.day() : (int) (p.serverLevel().getDayTime() / 24000L) + 1;
        Entry e = new Entry(p.getUUID(), p.getGameProfile().getName(), chosen, mastery, flags, evolved, Bounties.stars(p), MemoryPages.chapter(p), day);
        if (!e.equals(old)) { d.entries.put(p.getUUID(), e); d.setDirty(); }
    }

    /** A grimoire has just chosen this mage (server/GrimoireCeremony): a new book on the shelves. */
    public static void chosen(ServerPlayer p) {
        Data d = data(p.server);
        d.entries.remove(p.getUUID());
        record(p);
        p.displayClientMessage(net.minecraft.network.chat.Component.literal("A copy of your grimoire appears on the Grimoire Library's shelves.")
                .withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.ITALIC), false);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase == TickEvent.Phase.END && e.player instanceof ServerPlayer p && p.tickCount % 200 == 77) record(p);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) { if (e.getEntity() instanceof ServerPlayer p) record(p); }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) { if (e.getEntity() instanceof ServerPlayer p) record(p); }

    /** Opens the library: the greatest first (ascended, awakened, mastery, merit stars). */
    public static void open(ServerPlayer p) {
        record(p);
        List<Entry> all = new ArrayList<>(data(p.server).entries.values());
        all.sort(Comparator.comparingInt((Entry x) -> -score(x)).thenComparing(Entry::name, String.CASE_INSENSITIVE_ORDER));
        if (all.size() > 240) all = all.subList(0, 240);
        AddonNetwork.send(PacketDistributor.PLAYER.with(() -> p), new LibraryPackets.Open(all));
        ServerLevel sl = p.serverLevel();
        sl.playSound(null, p.blockPosition(), SoundEvents.CHISELED_BOOKSHELF_PICKUP, SoundSource.PLAYERS, 0.8f, 0.9f);
    }

    private static int score(Entry e) {
        return ((e.flags() & 2) != 0 ? 100000 : 0) + ((e.flags() & 1) != 0 ? 50000 : 0) + e.mastery() * 2000 + e.memory() * 500 + Math.min(e.stars(), 499);
    }
}
