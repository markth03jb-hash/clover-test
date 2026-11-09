package com.ironspellgrimoire.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** A plain, well-worn katana. Required in the main hand for the Dark Grimoire's sword techniques. */
public class DarkKatanaItem extends SwordItem {
    public DarkKatanaItem() {
        super(Tiers.NETHERITE, 5, -2.2F, new Item.Properties().fireResistant().rarity(Rarity.EPIC));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tip, flag);
        tip.add(Component.translatable("tooltip.iron_spell_grimoire.dark_katana.1").withStyle(ChatFormatting.DARK_PURPLE));
        tip.add(Component.translatable("tooltip.iron_spell_grimoire.dark_katana.2").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
