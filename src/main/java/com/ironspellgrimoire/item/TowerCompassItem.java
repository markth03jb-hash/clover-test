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
 * The Grimoire Tower Compass: handed to every new player (server/TowerCompass). Its needle points to the nearest
 * Grimoire Tower (kept on the compass: "TowerX", "TowerZ"); it crumbles away once you reach the tower.
 */
public class TowerCompassItem extends Item {
    public TowerCompassItem() { super(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)); }

    @Override
    public void appendHoverText(ItemStack s, Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.literal("It points to the Grimoire Tower, where a grimoire may choose you.").withStyle(ChatFormatting.GRAY));
        if (s.hasTag() && s.getTag().contains("TowerX")) tip.add(Component.literal("Tower at " + s.getTag().getInt("TowerX") + ", " + s.getTag().getInt("TowerZ")).withStyle(ChatFormatting.DARK_GRAY));
        tip.add(Component.literal("It crumbles once you get there.").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
