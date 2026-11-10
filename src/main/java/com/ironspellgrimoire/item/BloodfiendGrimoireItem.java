package com.ironspellgrimoire.item;

import com.ironspellgrimoire.registry.AddonSpellRegistry;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.ChatFormatting;

/** Don Quixote (La Manchaland, bloodfiend): twelve pages of hardblood ({@code AddonSpellRegistry.BLOODFIEND_SPELLS}). */
public class BloodfiendGrimoireItem extends ThemedGrimoireItem {
    public BloodfiendGrimoireItem() {
        super(12, "bloodfiend_grimoire", ChatFormatting.DARK_RED, () -> AddonSpellRegistry.BLOODFIEND_SPELLS, AttributeRegistry.BLOOD_SPELL_POWER::get, 1100, 0.6, 0.2, 2);
    }
}
