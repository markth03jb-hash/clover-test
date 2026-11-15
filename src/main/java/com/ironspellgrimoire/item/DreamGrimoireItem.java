package com.ironspellgrimoire.item;

import com.ironspellgrimoire.registry.AddonSpellRegistry;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.ChatFormatting;

/** The Dream Grimoire (inspired by a sleepy captain's Dream Magic; names and looks original): ten pages of a pastel dream ({@code AddonSpellRegistry.DREAM_SPELLS}). */
public class DreamGrimoireItem extends ThemedGrimoireItem {
    public DreamGrimoireItem() {
        super(10, "dream_grimoire", ChatFormatting.LIGHT_PURPLE, () -> AddonSpellRegistry.DREAM_SPELLS, AttributeRegistry.ENDER_SPELL_POWER::get, 1400, 0.6, 0.2, 2);
    }
}
