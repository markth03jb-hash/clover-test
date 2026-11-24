package com.ironspellgrimoire.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * Dayreaver, the Meridian Grimoire's great sun-axe (an original design: a half-sun blade with sundial hours, a gnomon
 * fin, a long haft). Called by the Call Dayreaver page; it leaves the moment it isn't in your hand. Heavy and slow; its
 * blows follow the sun (server/MeridianFx: 60% at night to 140%+ at noon), burn while the sun is up, and cleave in Zenith.
 */
public class DayreaverItem extends SwordItem {
    public DayreaverItem() {
        super(Tiers.NETHERITE, 9, -3.2F, new Properties().stacksTo(1).fireResistant().rarity(Rarity.EPIC));
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, level, entity, slot, selected);
        if (!level.isClientSide && (!selected || !(entity instanceof Player p) || p.getMainHandItem() != stack)) stack.setCount(0);
    }

    @Override public boolean onDroppedByPlayer(ItemStack item, Player player) { item.setCount(0); return true; }
    @Override public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) { entity.discard(); return true; }
    @Override public boolean isDamageable(ItemStack stack) { return false; }

    @Override
    public void appendHoverText(ItemStack s, Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.literal("Its blows follow the sun; it burns while the sun is up, and cleaves in Zenith.").withStyle(ChatFormatting.GOLD));
        tip.add(Component.literal("Called from the Meridian Grimoire. It leaves when you let it go.").withStyle(ChatFormatting.GRAY));
    }
}
