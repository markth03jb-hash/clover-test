package com.ironspellgrimoire.item;

import com.ironspellgrimoire.registry.AddonSpellRegistry;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.ChatFormatting;

/** One of the newer grimoires (see ThemedGrimoireItem): its eight preset spells are {@code AddonSpellRegistry.SLASH_SPELLS}. */
public class SlashGrimoireItem extends ThemedGrimoireItem {
    public SlashGrimoireItem() {
        super("slash_grimoire", ChatFormatting.RED, () -> AddonSpellRegistry.SLASH_SPELLS, AttributeRegistry.BLOOD_SPELL_POWER::get, 700, 0.4, 0.25, 0);
    }
}
