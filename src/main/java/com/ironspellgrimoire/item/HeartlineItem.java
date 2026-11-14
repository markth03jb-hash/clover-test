package com.ironspellgrimoire.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The Heartline: the Overdrive Grimoire's black katana - a crimson heartbeat down the blade, a charcoal-and-crimson grip -
 * drawn out of your chest by Draw the Heartline. Every other Overdrive page needs it in your main hand.
 * Damage 1 + 4 (netherite) + 6 = 11, speed 4.0 - 2.6 = 1.4: long and heavy, but quicker than a greatsword.
 * Summoned, not made: it fades the moment it leaves your hand.
 */
public class HeartlineItem extends SwordItem {
    public HeartlineItem() {
        super(Tiers.NETHERITE, 6, -2.6F, new Properties().stacksTo(1).fireResistant().rarity(Rarity.EPIC));
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, level, entity, slot, selected);
        if (!selected && !level.isClientSide) stack.setCount(0);
    }

    @Override
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) { entity.discard(); return true; }

    @Override
    public boolean isDamageable(ItemStack stack) { return false; }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tip, flag);
        tip.add(Component.translatable("tooltip.iron_spell_grimoire.heartline.1").withStyle(ChatFormatting.RED));
        tip.add(Component.translatable("tooltip.iron_spell_grimoire.heartline.2").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
