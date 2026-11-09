package com.ironspellgrimoire.item;

import com.ironspellgrimoire.registry.AddonSpellRegistry;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.ChatFormatting;

/** One of the newer grimoires (see ThemedGrimoireItem): its eight preset spells are {@code AddonSpellRegistry.FLAME_SPIRIT_SPELLS}. */
public class FlameSpiritGrimoireItem extends ThemedGrimoireItem {
    public FlameSpiritGrimoireItem() {
        super("flame_spirit_grimoire", ChatFormatting.GOLD, () -> AddonSpellRegistry.FLAME_SPIRIT_SPELLS, AttributeRegistry.FIRE_SPELL_POWER::get, 800, 0.45, 0.2, 2);
    }
}
