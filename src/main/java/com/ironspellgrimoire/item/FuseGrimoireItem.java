package com.ironspellgrimoire.item;

import com.ironspellgrimoire.registry.AddonSpellRegistry;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.ChatFormatting;

/** The Grimoire of the Quiet Fuse (server/FuseFx, its own tree in server/BookTree; every name, look and line original). Eight pages. */
public class FuseGrimoireItem extends ThemedGrimoireItem {
    public FuseGrimoireItem() {
        super(8, "fuse_grimoire", ChatFormatting.LIGHT_PURPLE, () -> AddonSpellRegistry.FUSE_SPELLS, AttributeRegistry.FIRE_SPELL_POWER::get, 900, 0.4, 0.2, 2);
    }
}
