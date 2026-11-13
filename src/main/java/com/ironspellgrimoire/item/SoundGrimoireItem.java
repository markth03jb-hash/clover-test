package com.ironspellgrimoire.item;

import com.ironspellgrimoire.registry.AddonSpellRegistry;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.ChatFormatting;

/** The Sound Grimoire (an original grimoire): ten pages of Sound Magic ({@code AddonSpellRegistry.SOUND_SPELLS}). */
public class SoundGrimoireItem extends ThemedGrimoireItem {
    public SoundGrimoireItem() {
        super(10, "sound_grimoire", ChatFormatting.AQUA, () -> AddonSpellRegistry.SOUND_SPELLS, AttributeRegistry.EVOCATION_SPELL_POWER::get, 1200, 0.6, 0.2, 2);
    }
}
