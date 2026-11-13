package com.ironspellgrimoire.item;

import com.ironspellgrimoire.registry.AddonSpellRegistry;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.ChatFormatting;

/** The Error Grimoire (an original grimoire): ten pages of corrupted magic ({@code AddonSpellRegistry.ERROR_SPELLS}). */
public class ErrorGrimoireItem extends ThemedGrimoireItem {
    public ErrorGrimoireItem() {
        super(10, "error_grimoire", ChatFormatting.LIGHT_PURPLE, () -> AddonSpellRegistry.ERROR_SPELLS, AttributeRegistry.ELDRITCH_SPELL_POWER::get, 1200, 0.6, 0.2, 2);
    }
}
