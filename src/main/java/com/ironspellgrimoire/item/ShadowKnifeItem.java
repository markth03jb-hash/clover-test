package com.ironspellgrimoire.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The Duskglass Kris (an original design; model tools/shadow_items.py): a wavy blade of smoked black glass, light
 * running along its waves. Bought in the Shadow Grimoire's Market.
 * Right-click - Phantom Edges (server/ShadowFx.phantomEdges): three ghost knives circle you for 10 s and strike anything
 * that comes within 3 blocks. 20 s.
 */
public class ShadowKnifeItem extends SwordItem {
    public static final int COOLDOWN = 400;

    public ShadowKnifeItem() {
        super(Tiers.DIAMOND, 2, -1.7F, new Properties().stacksTo(1).fireResistant().rarity(Rarity.EPIC));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack st = p.getItemInHand(hand);
        if (!level.isClientSide && p instanceof ServerPlayer sp && com.ironspellgrimoire.server.ShadowFx.phantomEdges(sp))
            p.getCooldowns().addCooldown(this, COOLDOWN);
        return InteractionResultHolder.sidedSuccess(st, level.isClientSide);
    }

    @Override public boolean isDamageable(ItemStack stack) { return false; }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tip, flag);
        tip.add(Component.translatable("tooltip.iron_spell_grimoire.duskglass_kris.1").withStyle(ChatFormatting.AQUA));
        tip.add(Component.translatable("tooltip.iron_spell_grimoire.nightfang.2").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
