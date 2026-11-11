package com.ironspellgrimoire.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

/**
 * A rift relic: a trinket worn in the charm slot, found only at the end of a Dungeon Rift. Each has one odd effect
 * (they work in server/RiftRewards):
 * <ul>
 *   <li>Cracked Lens - your spells sometimes land 50% harder.</li>
 *   <li>Rift Heart - a blow that takes over a third of your health tears a rift and throws you clear (60 s).</li>
 *   <li>Hollow Bell - every tenth spell you cast costs no mana.</li>
 *   <li>Shade Lantern - below half health, foes near you slow down.</li>
 * </ul>
 */
public class RelicItem extends Item implements ICurioItem {
    private final String what, how;

    public RelicItem(String what, String how) {
        super(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant());
        this.what = what;
        this.how = how;
    }

    @Override
    public void appendHoverText(ItemStack s, Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.literal("Rift Relic  -  charm slot").withStyle(ChatFormatting.LIGHT_PURPLE));
        tip.add(Component.literal(what).withStyle(ChatFormatting.GRAY));
        tip.add(Component.literal(how).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
