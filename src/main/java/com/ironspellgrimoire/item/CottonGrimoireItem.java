package com.ironspellgrimoire.item;

import com.ironspellgrimoire.registry.AddonSpellRegistry;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.ChatFormatting;

/** One of the newer grimoires (see ThemedGrimoireItem): its eight preset spells are {@code AddonSpellRegistry.COTTON_SPELLS}. */
public class CottonGrimoireItem extends ThemedGrimoireItem {
    public CottonGrimoireItem() {
        super("cotton_grimoire", ChatFormatting.LIGHT_PURPLE, () -> AddonSpellRegistry.COTTON_SPELLS, AttributeRegistry.HOLY_SPELL_POWER::get, 1000, 0.55, 0.15, 2);
    }
}
