package com.ironspellgrimoire.item;

import com.ironspellgrimoire.registry.AddonSpellRegistry;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.ChatFormatting;

/** The Parasite Grimoire (a living strain that rewrites its host; names and looks original): five pages - Consume,
 *  Evolution and three ultimates - and the arm forms on the G wheel ({@code server/ParasiteFx}). */
public class ParasiteGrimoireItem extends ThemedGrimoireItem {
    public ParasiteGrimoireItem() {
        super(5, "parasite_grimoire", ChatFormatting.DARK_RED, () -> AddonSpellRegistry.PARASITE_SPELLS, AttributeRegistry.BLOOD_SPELL_POWER::get, 1100, 0.5, 0.2, 3);
    }
}
