package com.ironspellgrimoire.item;

import com.ironspellgrimoire.registry.AddonSpellRegistry;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.ChatFormatting;

/** The Blood Grimoire (an original grimoire): ten pages of Blood Magic ({@code AddonSpellRegistry.BLOOD_SPELLS}). */
public class BloodGrimoireItem extends ThemedGrimoireItem {
    public BloodGrimoireItem() {
        super(10, "blood_grimoire", ChatFormatting.DARK_RED, () -> AddonSpellRegistry.BLOOD_SPELLS, AttributeRegistry.BLOOD_SPELL_POWER::get, 1200, 0.6, 0.2, 2);
    }
}
