package com.ironspellgrimoire.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * A Devil Shard, broken off Zenon. Right-click a Grimoire Altar holding one, with a grimoire equipped, to forge
 * it into that grimoire: each shard (up to 5) gives it +5% spell power and +3% cooldown reduction, for good.
 * The forging is in {@link com.ironspellgrimoire.server.ZenonDrops}.
 */
public class DevilShardItem extends Item {
    public DevilShardItem() { super(new Item.Properties().stacksTo(16).rarity(Rarity.RARE).fireResistant()); }

    @Override public boolean isFoil(ItemStack s) { return true; }

    @Override
    public void appendHoverText(ItemStack s, Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.literal("Right-click a Grimoire Altar with your grimoire equipped").withStyle(ChatFormatting.GRAY));
        tip.add(Component.literal("to forge this into it: +5% spell power, +3% cooldown reduction.").withStyle(ChatFormatting.WHITE));
        tip.add(Component.literal("Up to 5 per grimoire.").withStyle(ChatFormatting.GRAY));
        tip.add(Component.literal("Broken off Zenon - only he leaves them behind.").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
