package com.ironspellgrimoire.item;

import com.ironspellgrimoire.registry.AddonSpellRegistry;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.ChatFormatting;

/** The Hunter Grimoire (an original grimoire): ten pages of a hunter's weapon and a blood moon ({@code AddonSpellRegistry.HUNTER_SPELLS}). */
public class HunterGrimoireItem extends ThemedGrimoireItem {
    public HunterGrimoireItem() {
        super(10, "hunter_grimoire", ChatFormatting.DARK_RED, () -> AddonSpellRegistry.HUNTER_SPELLS, AttributeRegistry.BLOOD_SPELL_POWER::get, 1200, 0.6, 0.2, 2);
    }
}
