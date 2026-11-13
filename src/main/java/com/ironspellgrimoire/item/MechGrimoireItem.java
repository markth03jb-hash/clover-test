package com.ironspellgrimoire.item;

import com.ironspellgrimoire.registry.AddonSpellRegistry;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.ChatFormatting;

/** The Mech Grimoire (an original grimoire): ten pages of a war machine built part by part ({@code AddonSpellRegistry.MECH_SPELLS}). */
public class MechGrimoireItem extends ThemedGrimoireItem {
    public MechGrimoireItem() {
        super(10, "mech_grimoire", ChatFormatting.GOLD, () -> AddonSpellRegistry.MECH_SPELLS, AttributeRegistry.FIRE_SPELL_POWER::get, 1200, 0.6, 0.2, 2);
    }
}
