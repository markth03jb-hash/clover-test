package com.ironspellgrimoire.item;

import com.ironspellgrimoire.registry.AddonSpellRegistry;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.ChatFormatting;

/** The Masked Twins Grimoire (two masks sharing one soul; every name and look original): seven pages - Swap, Twin Arts,
 *  Twin Strike, Eclipse Union, The Masks, High Noon and Eventide - and each twin's four chosen arts on the G wheel ({@code server/TwinsFx}). */
public class TwinsGrimoireItem extends ThemedGrimoireItem {
    public TwinsGrimoireItem() {
        super(7, "twins_grimoire", ChatFormatting.GOLD, () -> AddonSpellRegistry.TWINS_SPELLS, AttributeRegistry.HOLY_SPELL_POWER::get, 1000, 0.45, 0.2, 2);
    }
}
