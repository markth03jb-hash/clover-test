package com.ironspellgrimoire.item;

import com.ironspellgrimoire.registry.AddonSpellRegistry;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.ChatFormatting;

/** The Ink Grimoire (an original grimoire): ten pages of Ink Magic ({@code AddonSpellRegistry.INK_SPELLS}). */
public class InkGrimoireItem extends ThemedGrimoireItem {
    public InkGrimoireItem() {
        super(10, "ink_grimoire", ChatFormatting.DARK_BLUE, () -> AddonSpellRegistry.INK_SPELLS, AttributeRegistry.EVOCATION_SPELL_POWER::get, 1200, 0.6, 0.2, 2);
    }
}
