package com.ironspellgrimoire.item;

import com.ironspellgrimoire.registry.AddonSpellRegistry;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.ChatFormatting;

/** The Overdrive Grimoire (an original grimoire): ten pages of a heart driven past its limit, and the Heartline sword ({@code AddonSpellRegistry.OVERDRIVE_SPELLS}). */
public class OverdriveGrimoireItem extends ThemedGrimoireItem {
    public OverdriveGrimoireItem() {
        super(10, "overdrive_grimoire", ChatFormatting.RED, () -> AddonSpellRegistry.OVERDRIVE_SPELLS, AttributeRegistry.ELDRITCH_SPELL_POWER::get, 1200, 0.6, 0.2, 2);
    }
}
