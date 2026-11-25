package com.ironspellgrimoire.server;

import com.ironspellgrimoire.IronSpellGrimoire;
import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Mastery quests: five goals for each grimoire, counted on the book itself (its NBT "BQ") while it's the one you
 * carry - cast its first page 100 times, bring down 25 elites, deal 10,000 damage, bring down a boss, clear a rift.
 * Each one done gives the book 1500 mastery experience; a book with its own tree gets a tree point for each too
 * (BookTree.points). The Codex's "Your Grimoire" page shows how far along you are.
 */
@Mod.EventBusSubscriber(modid = IronSpellGrimoire.MODID)
public final class BookQuests {
    private BookQuests() {}

    public static final String[] KEYS = {"c", "e", "d", "b", "r"};
    public static final int[] GOAL = {100, 25, 10000, 1, 1};
    public static final String[] TEXT = {"Cast its first page", "Bring down elites", "Deal damage with it", "Bring down a boss", "Clear a rift"};
    public static final int XP = 1500;

    static CompoundTag tag(ItemStack book) {
        CompoundTag root = book.getOrCreateTag();
        if (!root.contains("BQ")) root.put("BQ", new CompoundTag());
        return root.getCompound("BQ");
    }
    public static int progress(ItemStack book, int i) { return book != null && book.hasTag() ? book.getTag().getCompound("BQ").getInt(KEYS[i]) : 0; }
    public static int done(ItemStack book) { return book != null && book.hasTag() ? book.getTag().getCompound("BQ").getInt("q") : 0; }
    /** Tree points the quests give a book with its own tree. */
    public static int treePoints(ItemStack book) { return BookTree.kind(book) >= 0 ? Integer.bitCount(done(book)) : 0; }

    static ItemStack book(ServerPlayer p) {
        ItemStack b = Utils.getPlayerSpellbookStack(p);
        return b == null || b.isEmpty() || GrimoireMastery.bookOf(b) == null ? null : b;
    }

    /** Count n towards goal i on the grimoire p carries. */
    public static void add(ServerPlayer p, int i, int n) {
        ItemStack b = book(p);
        if (b == null) return;
        CompoundTag t = tag(b);
        int q = t.getInt("q");
        if ((q & (1 << i)) != 0) return;
        int v = (int) Math.min(Integer.MAX_VALUE, (long) t.getInt(KEYS[i]) + n);
        t.putInt(KEYS[i], v);
        if (v >= GOAL[i]) {
            t.putInt("q", q | (1 << i));
            GrimoireMastery.bountyXp(p, XP);
            boolean tree = BookTree.kind(b) >= 0;
            p.sendSystemMessage(Component.literal("MASTERY QUEST  ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                    .append(Component.literal(b.getHoverName().getString() + ": " + TEXT[i] + " - done. +" + XP + " mastery" + (tree ? ", +1 tree point" : "") + "  (" + Integer.bitCount(q | (1 << i)) + "/5)").withStyle(ChatFormatting.YELLOW)));
            p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 1.3f);
            if (tree) BookTree.sync(p, false);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onCast(SpellOnCastEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        ItemStack b = book(p);
        if (b == null) return;
        var c = ISpellContainer.get(b);
        if (c == null || c.getActiveSpells().isEmpty()) return;
        if (c.getActiveSpells().get(0).getSpell().getSpellId().equals(e.getSpellId())) add(p, 0, 1);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(LivingDamageEvent e) {
        if (e.getSource().getEntity() instanceof ServerPlayer p && e.getEntity() != p && e.getAmount() > 0) add(p, 2, Math.round(e.getAmount()));
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent e) {
        if (!(e.getSource().getEntity() instanceof ServerPlayer p) || !(e.getEntity() instanceof Mob m)) return;
        if (EliteMobs.elite(m) && !EliteMobs.ghost(m)) add(p, 1, 1);
        if (BossRules.tracked(m) || BossCores.isBoss(m.getType())) add(p, 3, 1);
    }

    /** A rift's relic chamber cleared (Rifts.reward): everyone inside counts it. */
    static void riftCleared(ServerPlayer p) { add(p, 4, 1); }
}
