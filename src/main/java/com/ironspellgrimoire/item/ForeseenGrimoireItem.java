package com.ironspellgrimoire.item;

import com.ironspellgrimoire.registry.AddonSpellRegistry;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.ChatFormatting;

/** The Grimoire of the Foreseen (server/ForeseenFx, its own tree in server/BookTree; every name, look and line original). Eight pages. */
public class ForeseenGrimoireItem extends ThemedGrimoireItem {
    public ForeseenGrimoireItem() {
        super(8, "foreseen_grimoire", ChatFormatting.WHITE, () -> AddonSpellRegistry.FORESEEN_SPELLS, AttributeRegistry.HOLY_SPELL_POWER::get, 900, 0.4, 0.2, 2);
    }
}
