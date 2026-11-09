package com.ironspellgrimoire.item;

import com.ironspellgrimoire.registry.AddonSpellRegistry;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.ChatFormatting;

/** One of the newer grimoires (see ThemedGrimoireItem): its eight preset spells are {@code AddonSpellRegistry.LION_FLAME_SPELLS}. */
public class LionFlameGrimoireItem extends ThemedGrimoireItem {
    public LionFlameGrimoireItem() {
        super("lions_flame_grimoire", ChatFormatting.YELLOW, () -> AddonSpellRegistry.LION_FLAME_SPELLS, AttributeRegistry.FIRE_SPELL_POWER::get, 800, 0.4, 0.25, 1);
    }
}
