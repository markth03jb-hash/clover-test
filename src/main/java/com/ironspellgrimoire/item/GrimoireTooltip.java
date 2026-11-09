package com.ironspellgrimoire.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Every grimoire's own lines under Iron's spell list, laid out the same way and in the grimoire's own colour:
 *   Passive - its name             (the grimoire's colour, bold)
 *   what the passive does          (grey)
 *   how to open the grimoire book  (dark aqua)
 *   a line of lore                 (dark grey, italic)
 */
public final class GrimoireTooltip {
    private GrimoireTooltip() {}

    public static void add(List<Component> tip, String book, ChatFormatting colour) {
        String k = "tooltip.iron_spell_grimoire." + book + ".";
        tip.add(Component.translatable(k + "passive").withStyle(colour, ChatFormatting.BOLD));
        tip.add(Component.translatable(k + "desc").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("tooltip.iron_spell_grimoire.open_book").withStyle(ChatFormatting.DARK_AQUA));
        tip.add(Component.translatable(k + "lore").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
