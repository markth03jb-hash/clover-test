package com.ironspellgrimoire.item;

import com.ironspellgrimoire.registry.AddonSpellRegistry;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.ChatFormatting;

/** Patolli's Light Grimoire: ten pages of Light Magic ({@code AddonSpellRegistry.LIGHT_SPELLS}). */
public class LightGrimoireItem extends ThemedGrimoireItem {
    public LightGrimoireItem() {
        super(10, "light_grimoire", ChatFormatting.YELLOW, () -> AddonSpellRegistry.LIGHT_SPELLS, AttributeRegistry.HOLY_SPELL_POWER::get, 1200, 0.6, 0.2, 2);
    }
}
