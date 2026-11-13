package com.ironspellgrimoire.item;

import com.ironspellgrimoire.registry.AddonSpellRegistry;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.ChatFormatting;

/** The Weather Grimoire (an original grimoire): ten pages of a sky of your own ({@code AddonSpellRegistry.WEATHER_SPELLS}). */
public class WeatherGrimoireItem extends ThemedGrimoireItem {
    public WeatherGrimoireItem() {
        super(10, "weather_grimoire", ChatFormatting.AQUA, () -> AddonSpellRegistry.WEATHER_SPELLS, AttributeRegistry.LIGHTNING_SPELL_POWER::get, 1200, 0.6, 0.2, 2);
    }
}
