package com.ironspellgrimoire.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** A Mana Core: the condensed magic left when a boss falls. Spent at the Star Forge to mutate a fully empowered spell. */
public class ManaCoreItem extends Item {
    public ManaCoreItem() { super(new Item.Properties().stacksTo(16).rarity(Rarity.EPIC).fireResistant()); }

    @Override public boolean isFoil(ItemStack s) { return true; }

    @Override
    public void appendHoverText(ItemStack s, Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.literal("At the Star Forge, mutate a spell empowered 3/3:").withStyle(ChatFormatting.GRAY));
        tip.add(Component.literal("Echo - each hit lands again for 40%.").withStyle(ChatFormatting.WHITE));
        tip.add(Component.literal("Siphon - 15% of the damage returns as mana.").withStyle(ChatFormatting.WHITE));
        tip.add(Component.literal("Or at a Grimoire Altar: hatch a familiar (once).").withStyle(ChatFormatting.GOLD));
        tip.add(Component.literal("Left behind when a boss falls.").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
