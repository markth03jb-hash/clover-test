package com.ironspellgrimoire.item;

import com.ironspellgrimoire.registry.AddonSpellRegistry;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.ChatFormatting;

/** The Architect of Ruin Grimoire: a pocket fortress inside a book (every name and look original). Eight pages -
 *  Rampart, Siege Mode, four Room pages (each holds one of your rooms' spells), The Fortress and the Citadel
 *  ({@code server/FortressFx}). */
public class ArchitectGrimoireItem extends ThemedGrimoireItem {
    public ArchitectGrimoireItem() {
        super(8, "architect_grimoire", ChatFormatting.GOLD, () -> AddonSpellRegistry.ARCHITECT_SPELLS, AttributeRegistry.EVOCATION_SPELL_POWER::get, 1000, 0.45, 0.2, 2);
    }
}
