package com.ironspellgrimoire.item;

import com.ironspellgrimoire.registry.AddonSpellRegistry;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.ChatFormatting;

/** The Tarot Grimoire (an original grimoire): ten pages of twenty-two cards and their fortunes ({@code AddonSpellRegistry.TAROT_SPELLS}). */
public class TarotGrimoireItem extends ThemedGrimoireItem {
    public TarotGrimoireItem() {
        super(10, "tarot_grimoire", ChatFormatting.LIGHT_PURPLE, () -> AddonSpellRegistry.TAROT_SPELLS, AttributeRegistry.NATURE_SPELL_POWER::get, 1200, 0.6, 0.2, 2);
    }
}
