package com.ironspellgrimoire.item;

import com.ironspellgrimoire.registry.AddonSpellRegistry;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.ChatFormatting;

/** The Chain Grimoire (an original grimoire): ten pages of five chains with five rules ({@code AddonSpellRegistry.CHAIN_SPELLS}). */
public class ChainGrimoireItem extends ThemedGrimoireItem {
    public ChainGrimoireItem() {
        super(10, "chain_grimoire", ChatFormatting.GRAY, () -> AddonSpellRegistry.CHAIN_SPELLS, AttributeRegistry.HOLY_SPELL_POWER::get, 1200, 0.6, 0.2, 2);
    }
}
