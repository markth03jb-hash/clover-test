package com.ironspellgrimoire.server;

import com.ironspellgrimoire.item.RiftInkItem;
import com.ironspellgrimoire.network.AddonNetwork;
import com.ironspellgrimoire.network.AltarPackets;
import com.ironspellgrimoire.registry.AddonBlocks;
import com.ironspellgrimoire.registry.AddonItemRegistry;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.PacketDistributor;

/**
 * Right-clicking the Grimoire Altar opens its menu: receive a grimoire (the tower ceremony), the Star Forge, forge a
 * Devil Shard into your grimoire, or write a Rift Ink into it - each one also says what it needs.
 */
public final class AltarMenu {
    private AltarMenu() {}

    public static void open(ServerPlayer p, BlockPos pos) {
        ItemStack book = Utils.getPlayerSpellbookStack(p);
        boolean has = book != null && !book.isEmpty() && GrimoireMastery.bookOf(book) != null;
        int shards = 0;
        int[] inks = new int[RiftInkItem.COUNT];
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(AddonItemRegistry.DEVIL_SHARD.get()) || s.getItem() instanceof com.ironspellgrimoire.item.GrimoireUpgradeItem) shards += s.getCount();
            else if (s.is(AddonItemRegistry.RIFT_INK.get())) inks[RiftInkItem.rule(s)] += s.getCount();
        }
        AddonNetwork.send(PacketDistributor.PLAYER.with(() -> p), new AltarPackets.Open(pos, has ? book.getHoverName().getString() : "",
                shards, has ? GrimoireUpgrades.total(book) : 0, Bounties.stars(p), examState(book, has), ascendState(book, has), inks));
    }

    private static int ascendState(ItemStack book, boolean has) {
        if (!has || !Awakening.awakened(book)) return 0;
        if (Ascension.ascended(book)) return 3;
        return Ascension.canAscend(book) ? 2 : 1;
    }

    private static int examState(ItemStack book, boolean has) {
        if (!has) return 0;
        if (GrimoireMastery.ascended(book)) return 3;
        return GrimoireMastery.level(book) >= GrimoireMastery.MAX_LEVEL ? 2 : 1;
    }

    public static void act(ServerPlayer p, BlockPos pos, int action, int arg) {
        if (p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64 || !p.level().getBlockState(pos).is(AddonBlocks.GRIMOIRE_ALTAR.get())) return;
        switch (action) {
            case 0 -> { p.closeContainer(); GrimoireCeremony.begin(p, pos); }
            case 1 -> StarForge.open(p);
            case 2 -> {   // bind everything you carry that the grimoire still has room for
                int bound = 0;
                for (ItemStack s : new java.util.ArrayList<>(p.getInventory().items)) {
                    while (!s.isEmpty() && (s.is(AddonItemRegistry.DEVIL_SHARD.get()) ? ZenonDrops.forge(p, s) : GrimoireUpgrades.bind(p, s))) bound++;
                }
                if (bound == 0) p.displayClientMessage(net.minecraft.network.chat.Component.literal("Nothing you carry fits this grimoire any more.").withStyle(net.minecraft.ChatFormatting.GRAY), true);
                open(p, pos);
            }
            case 3 -> { ItemStack s = find(p, arg); if (s != null) RiftRewards.inscribe(p, s); open(p, pos); }
            case 4 -> {   // the Magic Knight Exam, for a grimoire at mastery 5
                ItemStack book = Utils.getPlayerSpellbookStack(p);
                boolean has = book != null && !book.isEmpty() && GrimoireMastery.bookOf(book) != null;
                if (examState(book, has) == 2) { p.closeContainer(); Rifts.startExam(p); }
            }
            case 5 -> {   // the Ascension Trial, for an awakened grimoire at mastery 8
                ItemStack book = Utils.getPlayerSpellbookStack(p);
                if (Ascension.canAscend(book)) { p.closeContainer(); Rifts.startAscension(p); }
            }
            default -> {}
        }
    }

    /** A Devil Shard (rule -1) or a Rift Ink of {@code rule} from your inventory. */
    private static ItemStack find(ServerPlayer p, int rule) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (rule < 0 ? s.is(AddonItemRegistry.DEVIL_SHARD.get()) : s.is(AddonItemRegistry.RIFT_INK.get()) && RiftInkItem.rule(s) == rule) return s;
        }
        return null;
    }
}
