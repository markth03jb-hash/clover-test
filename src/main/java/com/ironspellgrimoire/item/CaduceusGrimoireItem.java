package com.ironspellgrimoire.item;

import com.ironspellgrimoire.registry.AddonSpellRegistry;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.ChatFormatting;

/** Rien (the Index's Proxy): ten pages of the Caduceus ({@code AddonSpellRegistry.CADUCEUS_SPELLS}). */
public class CaduceusGrimoireItem extends ThemedGrimoireItem {
    public CaduceusGrimoireItem() {
        super(10, "caduceus_grimoire", ChatFormatting.GOLD, () -> AddonSpellRegistry.CADUCEUS_SPELLS, AttributeRegistry.HOLY_SPELL_POWER::get, 1100, 0.6, 0.2, 2);
    }
}
