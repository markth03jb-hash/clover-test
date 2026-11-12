package com.ironspellgrimoire.spells;

import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/**
 * The Scarlet Moon's ice, as the menus show it: while the mode runs on this client, the Blood Grimoire's spells are
 * shown everywhere (the spell wheel, Iron's spell bar, the info page...) with their ice icon and ice name
 * ("ice_" + the spell's id). Only the client ever turns this on (client/BloodHud); a server never looks at it.
 */
public final class IceView {
    private IceView() {}

    public static volatile boolean on;

    private static final Set<String> BLOOD = Set.of("crimson_edge", "hemorrhage", "sanguine_volley", "transfusion", "blood_pact",
            "red_tide", "thorned_veins", "crimson_bulwark", "exsanguinate", "scarlet_moon");

    public static boolean applies(AbstractSpell s) {
        if (!on || s == null) return false;
        ResourceLocation id = s.getSpellResource();
        return id != null && "iron_spell_grimoire".equals(id.getNamespace()) && BLOOD.contains(id.getPath());
    }

    public static ResourceLocation icon(AbstractSpell s) {
        return ResourceLocation.fromNamespaceAndPath("iron_spell_grimoire", "textures/gui/spell_icons/ice_" + s.getSpellResource().getPath() + ".png");
    }

    public static String nameKey(AbstractSpell s) { return "spell.iron_spell_grimoire.ice_" + s.getSpellResource().getPath(); }
}
