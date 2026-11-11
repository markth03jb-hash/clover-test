package com.ironspellgrimoire.server;

import com.ironspellgrimoire.network.FxPacket;
import com.ironspellgrimoire.registry.AddonBlocks;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** The Mana Tree's server side: learning a node (into the equipped grimoire) and resetting the tree at an altar. */
public final class SkillTreeServer {
    private SkillTreeServer() {}

    public static void act(ServerPlayer p, int action, String id) {
        ItemStack book = Utils.getPlayerSpellbookStack(p);
        int b = SkillTree.bookIndex(book);
        if (b < 0) { p.displayClientMessage(Component.literal("Equip the grimoire to grow its tree.").withStyle(ChatFormatting.GRAY), true); return; }
        ServerLevel sl = p.serverLevel();
        int pal = GrimoireMastery.BOOKS.get(b).palette();
        if (action == 0) {
            SkillTree.Node n = SkillTree.node(b, id);
            if (n == null) return;
            String why = SkillTree.blocked(book, n, MemoryPages.chapter(p));
            if (why != null) { p.displayClientMessage(Component.literal(why + ".").withStyle(ChatFormatting.GRAY), true); return; }
            var tree = SkillTree.tree(book);
            tree.putBoolean(n.id(), true);
            book.getOrCreateTag().put("Tree", tree);
            SkillTree.refresh(book);
            Vec3 c = p.position();
            boolean crown = n.branch() == SkillTree.CROWN;
            FxSend.ring(sl, pal | FxPacket.KEEP_SHAPE, c.add(0, 0.1, 0), 0.3f, crown ? 5f : 2.2f, 0.08f, 14);
            FxSend.ring(sl, Fx.GOLD | FxPacket.KEEP_SHAPE, c.add(0, 1.2, 0), 0.2f, crown ? 2.6f : 1.2f, 0.05f, 12);
            sl.playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1f, crown ? 0.6f : 1.2f);
            if (crown) {
                FxSend.beam(sl, pal | FxPacket.KEEP_SHAPE, c, c.add(0, 16, 0), 0.6f, 20);
                FxSend.impactFrame(sl, c.add(0, 1.2, 0), 3, FxSend.FRAME_GOLD, 20);
                sl.playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1f, 0.9f);
                p.sendSystemMessage(Component.literal("CROWN OF THE TREE  ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                        .append(Component.literal(n.name() + " - " + SkillTree.describe(b, n)).withStyle(ChatFormatting.YELLOW)));
            } else p.displayClientMessage(Component.literal(n.name() + ": " + SkillTree.describe(b, n)).withStyle(ChatFormatting.GOLD), true);
        } else if (action == 1) {
            if (!nearAltar(p)) { p.displayClientMessage(Component.literal("Reset your tree at a Grimoire Altar.").withStyle(ChatFormatting.GRAY), true); return; }
            if (SkillTree.spent(book) == 0) return;
            int resets = book.getOrCreateTag().getInt("TreeResets");
            if (resets > 0) {
                if (Bounties.stars(p) < SkillTree.RESPEC_COST) { p.displayClientMessage(Component.literal("A reset costs " + SkillTree.RESPEC_COST + " merit stars.").withStyle(ChatFormatting.GRAY), true); return; }
                Bounties.addStars(p, -SkillTree.RESPEC_COST);
            }
            book.getOrCreateTag().remove("Tree");
            book.getOrCreateTag().putInt("TreeResets", resets + 1);
            SkillTree.refresh(book);
            FxSend.ring(sl, pal | FxPacket.KEEP_SHAPE, p.position().add(0, 0.1, 0), 3f, 0.2f, 0.06f, 14);
            sl.playSound(null, p.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.2f, 0.6f);
            p.displayClientMessage(Component.literal("The tree's leaves fall and gather again - every leaf is yours to spend." + (resets > 0 ? " (-" + SkillTree.RESPEC_COST + " merit stars)" : " (the first reset is free)"))
                    .withStyle(ChatFormatting.GOLD), true);
        }
    }

    private static boolean nearAltar(ServerPlayer p) {
        BlockPos c = p.blockPosition();
        for (BlockPos q : BlockPos.betweenClosed(c.offset(-6, -3, -6), c.offset(6, 3, 6)))
            if (p.level().getBlockState(q).is(AddonBlocks.GRIMOIRE_ALTAR.get())) return true;
        return false;
    }
}
