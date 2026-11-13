package com.ironspellgrimoire.item;

import com.ironspellgrimoire.registry.AddonSpellRegistry;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.ChatFormatting;

/** The Gambler Grimoire (an original grimoire): ten pages of luck, coins and the house ({@code AddonSpellRegistry.GAMBLER_SPELLS}). */
public class GamblerGrimoireItem extends ThemedGrimoireItem {
    public GamblerGrimoireItem() {
        super(10, "gambler_grimoire", ChatFormatting.YELLOW, () -> AddonSpellRegistry.GAMBLER_SPELLS, AttributeRegistry.EVOCATION_SPELL_POWER::get, 1200, 0.6, 0.2, 2);
    }
}
