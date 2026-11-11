package com.ironspellgrimoire.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** The Rift Compass: hold it and its needle points at the nearest open rift, glowing brighter within 64 blocks (server/Rifts, client/ArcaneAnimations). */
public class RiftCompassItem extends Item {
    public RiftCompassItem() { super(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)); }

    @Override
    public void appendHoverText(ItemStack s, Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.literal("Hold it: the needle points at the nearest open rift and glows brighter as you get close.").withStyle(ChatFormatting.GRAY));
    }
}
