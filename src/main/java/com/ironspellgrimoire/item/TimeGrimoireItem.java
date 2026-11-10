package com.ironspellgrimoire.item;

import com.ironspellgrimoire.registry.AddonSpellRegistry;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.ChatFormatting;

/** Julius Novachrono's Time Grimoire: twelve pages of Time Magic ({@code AddonSpellRegistry.TIME_SPELLS}). */
public class TimeGrimoireItem extends ThemedGrimoireItem {
    public TimeGrimoireItem() {
        super(12, "time_grimoire", ChatFormatting.AQUA, () -> AddonSpellRegistry.TIME_SPELLS, AttributeRegistry.ENDER_SPELL_POWER::get, 1200, 0.6, 0.2, 2);
    }
}
