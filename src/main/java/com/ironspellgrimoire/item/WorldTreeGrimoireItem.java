package com.ironspellgrimoire.item;

import com.ironspellgrimoire.registry.AddonSpellRegistry;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.ChatFormatting;

/** One of the newer grimoires (see ThemedGrimoireItem): its eight preset spells are {@code AddonSpellRegistry.WORLD_TREE_SPELLS}. */
public class WorldTreeGrimoireItem extends ThemedGrimoireItem {
    public WorldTreeGrimoireItem() {
        super("world_tree_grimoire", ChatFormatting.GREEN, () -> AddonSpellRegistry.WORLD_TREE_SPELLS, AttributeRegistry.NATURE_SPELL_POWER::get, 900, 0.5, 0.2, 2);
    }
}
