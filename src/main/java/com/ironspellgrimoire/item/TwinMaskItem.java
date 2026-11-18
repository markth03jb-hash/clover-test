package com.ironspellgrimoire.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The twins' masks as a model only (never held or kept): its "form" tag picks the model through the
 * "iron_spell_grimoire:form" item property (form / 16) - 0-2 Sol I-III, 3-5 Nyx I-III, 6 Eclipse Union, 7 the Dawn
 * Warden, 8 the Dusk Warden (tools/blender/masked_twins.py). Drawn on the face, at the hip, in the menus and as the wardens.
 */
public class TwinMaskItem extends Item {
    public static final String FORM = "form";
    public static final int SOL = 0, NYX = 3, ECLIPSE = 6, DAWN = 7, DUSK = 8;

    public TwinMaskItem() { super(new Properties().stacksTo(1).fireResistant()); }

    public static int form(ItemStack s) { return s.hasTag() ? Math.max(0, Math.min(8, s.getTag().getInt(FORM))) : 0; }

    public static ItemStack of(Item item, int form) {
        ItemStack s = new ItemStack(item);
        s.getOrCreateTag().putInt(FORM, form);
        return s;
    }
}
