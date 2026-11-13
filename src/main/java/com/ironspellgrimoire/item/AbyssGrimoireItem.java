package com.ironspellgrimoire.item;

import com.ironspellgrimoire.registry.AddonSpellRegistry;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.ChatFormatting;

/** The Abyss Grimoire (an original grimoire): ten pages of the deep where no light reaches ({@code AddonSpellRegistry.ABYSS_SPELLS}). */
public class AbyssGrimoireItem extends ThemedGrimoireItem {
    public AbyssGrimoireItem() {
        super(10, "abyss_grimoire", ChatFormatting.DARK_AQUA, () -> AddonSpellRegistry.ABYSS_SPELLS, AttributeRegistry.ICE_SPELL_POWER::get, 1200, 0.6, 0.2, 2);
    }
}
