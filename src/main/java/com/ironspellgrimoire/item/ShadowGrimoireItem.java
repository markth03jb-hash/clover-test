package com.ironspellgrimoire.item;

import com.ironspellgrimoire.registry.AddonSpellRegistry;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.ChatFormatting;

/** The Shadow Grimoire (raise the slain as your shadows; names and looks original): seventeen pages of black smoke and blue fire ({@code AddonSpellRegistry.SHADOW_SPELLS}). */
public class ShadowGrimoireItem extends ThemedGrimoireItem {
    public ShadowGrimoireItem() {
        super(17, "shadow_grimoire", ChatFormatting.DARK_AQUA, () -> AddonSpellRegistry.SHADOW_SPELLS, AttributeRegistry.ENDER_SPELL_POWER::get, 1400, 0.6, 0.2, 2);
    }
}
