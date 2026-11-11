package com.ironspellgrimoire.server;

import com.ironspellgrimoire.IronSpellGrimoire;
import com.ironspellgrimoire.network.AddonNetwork;
import com.ironspellgrimoire.network.FxPacket;
import com.ironspellgrimoire.registry.AddonItemRegistry;
import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * Quests from the Quest Board - the village's own requests, one at a time, shown under the bounty tracker
 * (client/QuestHud):
 * <ul>
 *   <li><b>Hooded Mages</b> - drive off the hooded mages troubling the roads (defeat 6-10).</li>
 *   <li><b>Monster Hunt</b> - thin out the monsters (defeat 15-25).</li>
 *   <li><b>Practice</b> - the village's young mages want to see your grimoire at work (cast 30-60 of its spells).</li>
 *   <li><b>Supply Run</b> - bring the board what it asks for (bread, iron, wheat, coal, leather...). Hand it in by
 *       right-clicking any Quest Board with it in your pack.</li>
 *   <li><b>Rift Watch</b> - close a rift (reach its relic chamber).</li>
 *   <li><b>Great Beast</b> - bring down any boss (from 10 merit stars).</li>
 * </ul>
 * Done quests pay emeralds, mastery XP for the equipped grimoire and merit stars - sometimes a Rift Ink, a Devil Shard
 * or a Mana Core. Sneak and right-click the board to give a quest up.
 */
@Mod.EventBusSubscriber(modid = IronSpellGrimoire.MODID)
public final class Quests {
    private Quests() {}

    public static final int MAGES = 0, MONSTERS = 1, PRACTICE = 2, SUPPLY = 3, RIFT = 4, BOSS = 5;
    public static final String[] TITLES = {"Hooded Mages", "Monster Hunt", "Practice", "Supply Run", "Rift Watch", "Great Beast"};
    public static final int SHAPE = 75;
    private static final String TAG = "isg_quest", NEXT = "isg_quest_next";
    /** What a Supply Run can ask for, and how many. */
    private static final Item[] SUPPLY_ITEMS = {Items.BREAD, Items.IRON_INGOT, Items.WHEAT, Items.COAL, Items.LEATHER, Items.COBBLESTONE, Items.OAK_LOG, Items.PAPER};
    private static final int[] SUPPLY_COUNT = {12, 8, 32, 16, 8, 64, 32, 24};

    public static Item supplyItem(int i) { return SUPPLY_ITEMS[Math.max(0, Math.min(SUPPLY_ITEMS.length - 1, i))]; }

    private static CompoundTag quest(ServerPlayer p) {
        CompoundTag t = p.getPersistentData().getCompound(TAG);
        return t.isEmpty() ? null : t;
    }

    public static void fromBoard(ServerPlayer p, BlockPos pos) {
        CompoundTag q = quest(p);
        ServerLevel sl = p.serverLevel();
        if (q != null && p.isShiftKeyDown()) {   // give it up
            p.getPersistentData().remove(TAG);
            p.getPersistentData().putLong(NEXT, sl.getGameTime() + 600);
            sl.playSound(null, p.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1f, 0.6f);
            p.displayClientMessage(Component.literal("You took your note off the board's list: " + TITLES[q.getInt("Type")] + ".").withStyle(ChatFormatting.GRAY), true);
            sync(p);
            return;
        }
        if (q != null) {
            if (q.getInt("Type") == SUPPLY) { handIn(p, q); return; }
            p.displayClientMessage(Component.literal("Your quest: " + text(q) + " (" + q.getInt("Have") + "/" + q.getInt("Need") + "). Sneak + right-click the board to give it up.").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        long now = sl.getGameTime(), next = p.getPersistentData().getLong(NEXT);
        if (now < next) {
            p.displayClientMessage(Component.literal("Nothing new on the board yet - come back in a minute or two.").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int type = r.nextInt(Bounties.stars(p) >= 10 ? 6 : 5);
        q = new CompoundTag();
        q.putInt("Type", type);
        switch (type) {
            case MAGES -> q.putInt("Need", 6 + r.nextInt(5));
            case MONSTERS -> q.putInt("Need", 15 + r.nextInt(11));
            case PRACTICE -> q.putInt("Need", 30 + r.nextInt(31));
            case SUPPLY -> { int i = r.nextInt(SUPPLY_ITEMS.length); q.putInt("Item", i); q.putInt("Need", SUPPLY_COUNT[i]); }
            default -> q.putInt("Need", 1);
        }
        q.putInt("Have", 0);
        p.getPersistentData().put(TAG, q);
        sl.playSound(null, p.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1f, 0.9f);
        sl.playSound(null, p.blockPosition(), SoundEvents.VILLAGER_WORK_LIBRARIAN, SoundSource.PLAYERS, 1f, 1f);
        p.sendSystemMessage(Component.literal("QUEST: ").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
                .append(Component.literal(TITLES[type]).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD))
                .append(Component.literal("  " + text(q)).withStyle(ChatFormatting.GRAY)));
        if (type == SUPPLY) p.sendSystemMessage(Component.literal("Hand it in at any Quest Board.").withStyle(ChatFormatting.DARK_GRAY));
        sync(p);
    }

    /** What the quest asks, in words. */
    public static String text(CompoundTag q) {
        int n = q.getInt("Need");
        return switch (q.getInt("Type")) {
            case MAGES -> "Defeat " + n + " hooded mages.";
            case MONSTERS -> "Defeat " + n + " monsters.";
            case PRACTICE -> "Cast " + n + " spells with your grimoire.";
            case SUPPLY -> "Bring " + n + " " + supplyItem(q.getInt("Item")).getDescription().getString() + ".";
            case RIFT -> "Close a rift: reach its relic chamber.";
            default -> "Bring down any boss.";
        };
    }

    private static void handIn(ServerPlayer p, CompoundTag q) {
        Item want = supplyItem(q.getInt("Item"));
        int need = q.getInt("Need") - q.getInt("Have");
        int took = 0;
        for (ItemStack s : p.getInventory().items) {
            if (took >= need) break;
            if (!s.is(want)) continue;
            int n = Math.min(s.getCount(), need - took);
            s.shrink(n);
            took += n;
        }
        if (took == 0) { p.displayClientMessage(Component.literal("The board still wants " + need + " " + want.getDescription().getString() + ".").withStyle(ChatFormatting.GRAY), true); return; }
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 1f, 0.8f);
        progress(p, SUPPLY, took);
    }

    /** Counts toward the player's quest if it's of this kind. */
    public static void progress(ServerPlayer p, int type, int n) {
        CompoundTag q = quest(p);
        if (q == null || q.getInt("Type") != type) return;
        int have = Math.min(q.getInt("Need"), q.getInt("Have") + n);
        q.putInt("Have", have);
        if (have >= q.getInt("Need")) complete(p, q);
        else sync(p);
    }

    private static void complete(ServerPlayer p, CompoundTag q) {
        int type = q.getInt("Type");
        ThreadLocalRandom r = ThreadLocalRandom.current();
        boolean big = type == RIFT || type == BOSS;
        int emeralds = big ? 12 + r.nextInt(8) : 4 + r.nextInt(6);
        int stars = big ? 3 : 1;
        give(p, new ItemStack(Items.EMERALD, emeralds));
        GrimoireMastery.bountyXp(p, big ? 500 : 150 + r.nextInt(100));
        Bounties.addStars(p, stars);
        String extra = "";
        if (r.nextInt(big ? 2 : 5) == 0) {
            int k = r.nextInt(3);
            ItemStack s = switch (k) {
                case 0 -> { ItemStack ink = new ItemStack(AddonItemRegistry.RIFT_INK.get()); ink.getOrCreateTag().putInt("Rule", r.nextInt(4)); yield ink; }
                case 1 -> new ItemStack(AddonItemRegistry.DEVIL_SHARD.get());
                default -> new ItemStack(AddonItemRegistry.MANA_CORE.get());
            };
            give(p, s);
            extra = ", " + s.getHoverName().getString();
        }
        p.getPersistentData().remove(TAG);
        p.getPersistentData().putLong(NEXT, p.serverLevel().getGameTime() + 1200);
        ServerLevel sl = p.serverLevel();
        FxSend.ring(sl, Fx.WATER | FxPacket.KEEP_SHAPE, p.position().add(0, 0.05, 0), 0.3f, 2.6f, 0.06f, 14);
        sl.playSound(null, p.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8f, 1.2f);
        p.sendSystemMessage(Component.literal("QUEST COMPLETE  ").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
                .append(Component.literal(TITLES[type] + ": " + emeralds + " emeralds, +" + stars + " merit star" + (stars > 1 ? "s" : "") + extra + ".").withStyle(ChatFormatting.YELLOW)));
        FxPacket k = new FxPacket();
        k.shape = SHAPE; k.life = 2; k.sweep = type;   // the tracker shows DONE for a moment
        AddonNetwork.send(PacketDistributor.PLAYER.with(() -> p), k);
    }

    private static void give(ServerPlayer p, ItemStack s) { if (!p.getInventory().add(s)) p.drop(s, false); }

    /** The tracker on the client: type, have/need (life 1 = active, 0 = none, 2 = just done). */
    public static void sync(ServerPlayer p) {
        CompoundTag q = quest(p);
        FxPacket k = new FxPacket();
        k.shape = SHAPE;
        if (q != null) { k.life = 1; k.sweep = q.getInt("Type"); k.a0 = q.getInt("Have"); k.a1 = q.getInt("Need"); k.h = q.getInt("Item"); }
        AddonNetwork.send(PacketDistributor.PLAYER.with(() -> p), k);
    }

    // ------------------------------------------------------------------ progress
    @SubscribeEvent
    public static void onKill(LivingDeathEvent e) {
        if (!(e.getSource().getEntity() instanceof ServerPlayer p) || e.getEntity() == p) return;
        var t = e.getEntity();
        if (t instanceof com.ironspellgrimoire.entity.MageEnemy) progress(p, MAGES, 1);
        if (BossCores.isBoss(t.getType())) progress(p, BOSS, 1);
        else if (t instanceof Enemy) progress(p, MONSTERS, 1);
    }

    @SubscribeEvent
    public static void onCast(SpellOnCastEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && e.getSpellId() != null && e.getSpellId().startsWith(IronSpellGrimoire.MODID + ":")) progress(p, PRACTICE, 1);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) { if (e.getEntity() instanceof ServerPlayer p) sync(p); }
}
