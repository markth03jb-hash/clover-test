package com.ironspellgrimoire.item;

import com.ironspellgrimoire.registry.AddonSpellRegistry;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.ChatFormatting;

/** One of the newer grimoires (see ThemedGrimoireItem): its eight preset spells are {@code AddonSpellRegistry.BEAST_SPELLS}. */
public class BeastGrimoireItem extends ThemedGrimoireItem {
    public BeastGrimoireItem() {
        super("beast_grimoire", ChatFormatting.DARK_GREEN, () -> AddonSpellRegistry.BEAST_SPELLS, AttributeRegistry.NATURE_SPELL_POWER::get, 750, 0.4, 0.25, 1);
    }
}
