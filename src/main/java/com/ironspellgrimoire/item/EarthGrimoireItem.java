package com.ironspellgrimoire.item;

import com.ironspellgrimoire.registry.AddonSpellRegistry;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.ChatFormatting;

/** One of the newer grimoires (see ThemedGrimoireItem): its eight preset spells are {@code AddonSpellRegistry.EARTH_SPELLS}. */
public class EarthGrimoireItem extends ThemedGrimoireItem {
    public EarthGrimoireItem() {
        super("earth_grimoire", ChatFormatting.GOLD, () -> AddonSpellRegistry.EARTH_SPELLS, AttributeRegistry.EVOCATION_SPELL_POWER::get, 800, 0.35, 0.2, 4);
    }
}
