package com.ironspellgrimoire.item;

import com.ironspellgrimoire.registry.AddonSpellRegistry;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.ChatFormatting;

/** The Hunger Blade Grimoire (a living sword that grows as it feeds; every name and look original): five pages - Draw,
 *  Gnash, Feast, Unleash and The Blade - and the four carried swords on the G wheel ({@code server/HungerFx}). */
public class HungerGrimoireItem extends ThemedGrimoireItem {
    public HungerGrimoireItem() {
        super(5, "hunger_grimoire", ChatFormatting.RED, () -> AddonSpellRegistry.HUNGER_SPELLS, AttributeRegistry.BLOOD_SPELL_POWER::get, 1000, 0.45, 0.2, 3);
    }
}
