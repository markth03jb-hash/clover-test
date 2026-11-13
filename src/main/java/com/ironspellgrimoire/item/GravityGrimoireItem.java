package com.ironspellgrimoire.item;

import com.ironspellgrimoire.registry.AddonSpellRegistry;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.ChatFormatting;

/** The Gravity Grimoire (an original grimoire): ten pages of Gravity Magic ({@code AddonSpellRegistry.GRAVITY_SPELLS}). */
public class GravityGrimoireItem extends ThemedGrimoireItem {
    public GravityGrimoireItem() {
        super(10, "gravity_grimoire", ChatFormatting.DARK_PURPLE, () -> AddonSpellRegistry.GRAVITY_SPELLS, AttributeRegistry.ENDER_SPELL_POWER::get, 1200, 0.6, 0.2, 2);
    }
}
