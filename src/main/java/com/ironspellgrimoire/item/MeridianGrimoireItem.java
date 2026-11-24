package com.ironspellgrimoire.item;

import com.ironspellgrimoire.registry.AddonSpellRegistry;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.ChatFormatting;

/** The Meridian Grimoire: power that follows the sun ({@code server/MeridianFx}; every name, look and line original). Eight
 *  pages - four for the day, three for the night, and Zenith Unbound. */
public class MeridianGrimoireItem extends ThemedGrimoireItem {
    public MeridianGrimoireItem() {
        super(8, "meridian_grimoire", ChatFormatting.GOLD, () -> AddonSpellRegistry.MERIDIAN_SPELLS, AttributeRegistry.FIRE_SPELL_POWER::get, 900, 0.4, 0.25, 2);
    }
}
