package com.ironspellgrimoire.item;

import com.ironspellgrimoire.registry.AddonSpellRegistry;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.ChatFormatting;

/** The Grimoire of the Bound Demon (server/BoundFx, its Bond Tree in server/BookTree; every name, look and line original). Eight pages. */
public class BoundGrimoireItem extends ThemedGrimoireItem {
    public BoundGrimoireItem() {
        super(8, "bound_grimoire", ChatFormatting.DARK_RED, () -> AddonSpellRegistry.BOUND_SPELLS, AttributeRegistry.BLOOD_SPELL_POWER::get, 900, 0.4, 0.2, 3);
    }
}
