package com.ironspellgrimoire.item;

import com.ironspellgrimoire.registry.AddonSpellRegistry;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.ChatFormatting;

/** The Treasure Grimoire (an original grimoire): ten pages of a vault of weapons ({@code AddonSpellRegistry.TREASURE_SPELLS}). */
public class TreasureGrimoireItem extends ThemedGrimoireItem {
    public TreasureGrimoireItem() {
        super(10, "treasure_grimoire", ChatFormatting.GOLD, () -> AddonSpellRegistry.TREASURE_SPELLS, AttributeRegistry.HOLY_SPELL_POWER::get, 1200, 0.6, 0.2, 2);
    }
}
