package com.ironspellgrimoire.item;

import com.ironspellgrimoire.server.GrimoireUpgrades;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** A grimoire upgrade (server/GrimoireUpgrades): bind it into your equipped grimoire at a Grimoire Altar. */
public class GrimoireUpgradeItem extends Item {
    public final String id;
    private final boolean trophy;

    public GrimoireUpgradeItem(String id, boolean trophy) {
        super(new Item.Properties().stacksTo(16).rarity(trophy ? Rarity.EPIC : Rarity.RARE).fireResistant());
        this.id = id;
        this.trophy = trophy;
    }

    @Override public boolean isFoil(ItemStack s) { return trophy; }

    @Override
    public void appendHoverText(ItemStack s, Level level, List<Component> tip, TooltipFlag flag) {
        GrimoireUpgrades.Kind k = GrimoireUpgrades.kind(id);
        if (k == null) return;
        tip.add(Component.literal("Grimoire upgrade - each: " + k.each()).withStyle(ChatFormatting.WHITE));
        tip.add(Component.literal("Up to " + k.max() + " per grimoire.").withStyle(ChatFormatting.GRAY));
        tip.add(Component.literal("Bind it at a Grimoire Altar with your grimoire equipped.").withStyle(ChatFormatting.GRAY));
        tip.add(Component.literal("From: " + k.from()).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
