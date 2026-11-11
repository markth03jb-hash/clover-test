package com.ironspellgrimoire.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** The Rift Compass: hold it and the compass bar at the top of the screen marks the nearest open rift (server/Rifts). */
public class RiftCompassItem extends Item {
    public RiftCompassItem() { super(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)); }

    @Override
    public void appendHoverText(ItemStack s, Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.literal("Hold it: the nearest open rift shows on the compass bar.").withStyle(ChatFormatting.GRAY));
    }
}
