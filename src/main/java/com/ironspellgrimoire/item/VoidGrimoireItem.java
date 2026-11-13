package com.ironspellgrimoire.item;

import com.ironspellgrimoire.registry.AddonSpellRegistry;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.ChatFormatting;

/** The Void Grimoire (an original grimoire): ten pages of the magic of taking things away ({@code AddonSpellRegistry.VOID_SPELLS}). */
public class VoidGrimoireItem extends ThemedGrimoireItem {
    public VoidGrimoireItem() {
        super(10, "void_grimoire", ChatFormatting.WHITE, () -> AddonSpellRegistry.VOID_SPELLS, AttributeRegistry.ENDER_SPELL_POWER::get, 1200, 0.6, 0.2, 2);
    }
}
