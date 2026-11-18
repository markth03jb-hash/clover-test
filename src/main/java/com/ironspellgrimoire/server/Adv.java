package com.ironspellgrimoire.server;

import com.ironspellgrimoire.IronSpellGrimoire;
import net.minecraft.advancements.Advancement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The mod's advancements (data/iron_spell_grimoire/advancements/main). Most have a single "code" criterion
 * (minecraft:impossible) that only this class grants, at the moment it happens in the game. On login the ones that
 * can be read back from the player or their books are caught up, so worlds from before them aren't left behind.
 */
@Mod.EventBusSubscriber(modid = IronSpellGrimoire.MODID)
public final class Adv {
    private Adv() {}

    /** Grant main/{name} to the player (nothing if it's already done or doesn't exist). */
    public static void grant(ServerPlayer p, String name) {
        if (p == null || p.server == null) return;
        Advancement a = p.server.getAdvancements().getAdvancement(new ResourceLocation(IronSpellGrimoire.MODID, "main/" + name));
        if (a == null || p.getAdvancements().getOrStartProgress(a).isDone()) return;
        for (String c : p.getAdvancements().getOrStartProgress(a).getRemainingCriteria()) p.getAdvancements().award(a, c);
    }

    /** What a grimoire's state earns: its mastery level, the exam, the awakening. */
    public static void book(ServerPlayer p, ItemStack book) {
        if (book == null || book.isEmpty() || GrimoireMastery.bookOf(book) == null) return;
        int lv = GrimoireMastery.level(book);
        if (lv >= 3) grant(p, "mastery_3");
        if (lv >= GrimoireMastery.MAX_LEVEL) grant(p, "mastery_max");
        if (GrimoireMastery.ascended(book)) grant(p, "magic_knight");
        if (Awakening.awakened(book)) grant(p, "awakened");
        if (book.hasTag() && book.getTag().getBoolean("Ascension")) grant(p, "ascended");
        if (book.hasTag() && !book.getTag().getCompound("Evolved").isEmpty()) grant(p, "evolved");
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        if (GrimoireCeremony.chosen(p) >= 0) grant(p, "chosen");
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) book(p, inv.getItem(i));
        book(p, io.redspace.ironsspellbooks.api.util.Utils.getPlayerSpellbookStack(p));
        if (HungerFx.bond(p) >= HungerFx.MAX_BOND) grant(p, "one_appetite");
        if (ParasiteExtras.apex(p) != 0) grant(p, "apex");
    }
}
