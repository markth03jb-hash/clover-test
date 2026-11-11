package com.ironspellgrimoire.server;

import com.ironspellgrimoire.IronSpellGrimoire;
import com.ironspellgrimoire.network.AddonNetwork;
import com.ironspellgrimoire.network.FxPacket;
import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import io.redspace.ironsspellbooks.api.util.Utils;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * Spell Evolution. Every grimoire counts how many times each of its spells has been cast (on the book: "SpellCasts").
 * At {@link #EVOLVE_AT} casts a spell evolves - once, for good ("Evolved"): a moment on your screen (the spell's name
 * and EVOLVED, client/EvolutionToast), and from then on
 * <ul>
 *   <li>it hits 20% harder, and every hit flares in the grimoire's colours;</li>
 *   <li>casting it throws a ring of the book's magic out from your feet.</li>
 * </ul>
 * On an Ascended grimoire (server/Ascension) evolution goes further: +35% instead, the flares burn gold and white and
 * reach higher, and every hit strikes again a moment later for a quarter of its damage (the evolved echo).
 * The grimoire screen shows each spell's count and its evolution.
 */
@Mod.EventBusSubscriber(modid = IronSpellGrimoire.MODID)
public final class SpellEvolution {
    private SpellEvolution() {}

    public static final int EVOLVE_AT = 500;
    public static final int SHAPE = 71;
    private static final Map<UUID, Long> FLARE = new HashMap<>();   // a target's last flare (so a flurry of hits doesn't bury it in light)

    private static String path(String id) { int i = id.indexOf(':'); return i < 0 ? id : id.substring(i + 1); }

    /** Does this spell belong to this book (one of its pages, or its awakened page)? */
    private static boolean ofBook(ItemStack book, String spell) {
        GrimoireMastery.Book b = GrimoireMastery.bookOf(book);
        if (b == null) return false;
        return b.unlocks().containsKey(spell) || spell.equals(Awakening.spellFor(book));
    }

    public static int casts(ItemStack book, String spellId) {
        return book == null || !book.hasTag() ? 0 : book.getTag().getCompound("SpellCasts").getInt(path(spellId));
    }

    public static boolean evolved(ItemStack book, String spellId) {
        return book != null && book.hasTag() && book.getTag().getCompound("Evolved").getBoolean(path(spellId));
    }

    @SubscribeEvent
    public static void onCast(SpellOnCastEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || e.getSpellId() == null || !e.getSpellId().startsWith(IronSpellGrimoire.MODID + ":")) return;
        ItemStack book = Utils.getPlayerSpellbookStack(p);
        String id = path(e.getSpellId());
        if (book == null || !ofBook(book, id)) return;
        CompoundTag tag = book.getOrCreateTag();
        CompoundTag casts = tag.getCompound("SpellCasts");
        int n = casts.getInt(id) + 1;
        casts.putInt(id, n);
        tag.put("SpellCasts", casts);
        ServerLevel sl = p.serverLevel();
        int pal = palette(book);
        boolean asc = Ascension.ascended(book);
        if (evolved(book, id)) {   // an evolved spell throws its ring out as it's cast
            FxSend.ring(sl, (asc ? Fx.GOLD : pal) | FxPacket.KEEP_SHAPE, p.position().add(0, 0.05, 0), 0.4f, asc ? 3.6f : 2.6f, 0.06f, 12);
            if (asc) FxSend.ring(sl, Fx.SILVER | FxPacket.KEEP_SHAPE, p.position().add(0, 0.08, 0), 0.3f, 2.2f, 0.04f, 10);
        } else if (n >= EVOLVE_AT) evolve(p, book, id);
    }

    private static int palette(ItemStack book) {
        GrimoireMastery.Book b = GrimoireMastery.bookOf(book);
        return b == null ? Fx.GOLD : b.palette();
    }

    /** The spell evolves: written into the book, and shown on your screen. */
    public static void evolve(ServerPlayer p, ItemStack book, String id) {
        CompoundTag tag = book.getOrCreateTag();
        CompoundTag ev = tag.getCompound("Evolved");
        if (ev.getBoolean(id)) return;
        ev.putBoolean(id, true);
        tag.put("Evolved", ev);
        ServerLevel sl = p.serverLevel();
        int pal = palette(book);
        Vec3 c = p.position();
        FxSend.ring(sl, pal | FxPacket.KEEP_SHAPE, c.add(0, 0.05, 0), 0.4f, 6f, 0.14f, 18);
        FxSend.beam(sl, pal | FxPacket.KEEP_SHAPE, c, c.add(0, 18, 0), 0.6f, 20);
        FxSend.impactFrame(sl, c.add(0, 1.2, 0), 3, FxSend.FRAME_GOLD, 24);
        sl.playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1f, 1.3f);
        sl.playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.2f, 0.8f);
        Component name = Component.translatable("spell.iron_spell_grimoire." + id);
        p.sendSystemMessage(Component.literal("SPELL EVOLVED  ").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD)
                .append(name.copy().withStyle(ChatFormatting.GOLD))
                .append(Component.literal(Ascension.ascended(book) ? "  (+35%, gold echo)" : "  (+20%)").withStyle(ChatFormatting.GRAY)));
        FxPacket k = new FxPacket();
        k.shape = SHAPE; k.id = p.getId(); k.life = 60; k.sweep = Ascension.ascended(book) ? 1 : 0;
        k.h = spellIndex(book, id);   // the client finds the name from the book's own list
        AddonNetwork.send(PacketDistributor.PLAYER.with(() -> p), k);
    }

    /** The book's spells in order (its pages, then its awakened page) - shared with the client. */
    public static java.util.List<String> spells(ItemStack book) {
        java.util.List<String> l = new java.util.ArrayList<>();
        GrimoireMastery.Book b = GrimoireMastery.bookOf(book);
        if (b == null) return l;
        l.addAll(b.unlocks().keySet());
        String aw = Awakening.spellFor(book);
        if (aw != null) l.add(aw);
        return l;
    }

    private static int spellIndex(ItemStack book, String id) { return spells(book).indexOf(id); }

    /** How much harder a hit from this spell lands for its evolution (1 when it hasn't evolved). */
    public static float boost(ServerPlayer p, String spellId) {
        ItemStack book = Utils.getPlayerSpellbookStack(p);
        if (!evolved(book, spellId)) return 1f;
        return Ascension.ascended(book) ? 1.35f : 1.2f;
    }

    /** After a hit lands: the evolved flare, and on an ascended book the evolved echo. Returns true if an echo is due. */
    public static boolean afterHit(ServerPlayer p, LivingEntity t, String spellId) {
        ItemStack book = Utils.getPlayerSpellbookStack(p);
        if (!evolved(book, spellId)) return false;
        boolean asc = Ascension.ascended(book);
        ServerLevel sl = p.serverLevel();
        long now = sl.getGameTime();
        Long last = FLARE.get(t.getUUID());
        if (last == null || now - last >= 5) {
            FLARE.put(t.getUUID(), now);
            Vec3 at = t.position().add(0, t.getBbHeight() * 0.55, 0);
            int pal = asc ? Fx.GOLD : palette(book);
            FxSend.ring(sl, pal | FxPacket.KEEP_SHAPE, at, 0.2f, asc ? 1.8f : 1.2f, 0.05f, 8);
            LightFx.glint(sl, asc ? Fx.SILVER : pal, at, asc ? 0.9f : 0.6f, 8);
            if (asc) FxSend.beam(sl, Fx.GOLD | FxPacket.KEEP_SHAPE, at.add(0, -0.8, 0), at.add(0, 2.4, 0), 0.18f, 8);
            if (FLARE.size() > 512) FLARE.clear();
        }
        return asc;
    }
}
