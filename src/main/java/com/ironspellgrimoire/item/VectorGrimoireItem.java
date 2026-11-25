package com.ironspellgrimoire.item;

import com.ironspellgrimoire.registry.AddonSpellRegistry;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.ChatFormatting;

/** The Vector Grimoire (server/VectorFx, its own tree in server/BookTree; every name, look and line original). Eight pages. */
public class VectorGrimoireItem extends ThemedGrimoireItem {
    public VectorGrimoireItem() {
        super(8, "vector_grimoire", ChatFormatting.AQUA, () -> AddonSpellRegistry.VECTOR_SPELLS, AttributeRegistry.EVOCATION_SPELL_POWER::get, 900, 0.4, 0.2, 2);
    }
}
