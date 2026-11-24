package com.ironspellgrimoire.item;

import com.ironspellgrimoire.registry.AddonSpellRegistry;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.ChatFormatting;

/** The Grimoire of Ill Omen: a law of misfortune on whatever pursues you ({@code server/OmenFx}; every name, look and line
 *  original). Eight pages. */
public class OmenGrimoireItem extends ThemedGrimoireItem {
    public OmenGrimoireItem() {
        super(8, "omen_grimoire", ChatFormatting.BLUE, () -> AddonSpellRegistry.OMEN_SPELLS, AttributeRegistry.ENDER_SPELL_POWER::get, 900, 0.4, 0.2, 2);
    }
}
