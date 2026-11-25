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

    public static int tier(ItemStack s) { return s.hasTag() ? Math.max(1, Math.min(3, s.getTag().getInt("Tier"))) : 1; }

    @Override
    public Component getName(ItemStack s) {
        int t = tier(s);
        return t <= 1 ? super.getName(s) : super.getName(s).copy().append(t == 2 ? " II" : " III");
    }

    @Override
    public void appendHoverText(ItemStack s, Level level, List<Component> tip, TooltipFlag flag) {
        int t = tier(s);
        tip.add(Component.literal("Tier " + (t == 1 ? "I" : t == 2 ? "II" : "III") + " - " + (t == 1 ? "Siphon, Frost, Leech, Weaken" : t == 2 ? "also Blast and Execute" : "every mutation, Echo and Chain too")).withStyle(t == 3 ? ChatFormatting.GOLD : ChatFormatting.AQUA));
        tip.add(Component.literal("At the Star Forge, mutate a spell empowered 3/3:").withStyle(ChatFormatting.GRAY));
        tip.add(Component.literal("Echo - each hit lands again for 40%.").withStyle(ChatFormatting.WHITE));
        tip.add(Component.literal("Siphon - 15% of the damage returns as mana.").withStyle(ChatFormatting.WHITE));
        tip.add(Component.literal("Or at a Grimoire Altar: hatch a familiar (once).").withStyle(ChatFormatting.GOLD));
        tip.add(Component.literal("Left behind when a boss falls.").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
