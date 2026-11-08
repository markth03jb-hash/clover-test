package com.example.arcaneaddon.server;

import com.example.arcaneaddon.item.BlackGrimoireItem;
import com.example.arcaneaddon.registry.AddonSpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.ISpellContainerMutable;
import io.redspace.ironsspellbooks.api.spells.SpellSlot;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Swaps the spells inside the equipped Black Grimoire between their normal and devil versions.
 * Iron's spell icon belongs to the spell itself, so swapping the spells is what changes the icons, names and moves
 * on the spell bar / wheel while Devil Union lasts. Slots, levels and locks are kept exactly as they were.
 */
public final class DevilBook {
    private DevilBook() {}

    /** devil = true: normal -> devil versions; false: devil -> normal. Does nothing if the book is already right. */
    public static void apply(ServerPlayer p, boolean devil) {
        ItemStack book = Utils.getPlayerSpellbookStack(p);
        if (book == null || !(book.getItem() instanceof BlackGrimoireItem) || !ISpellContainer.isSpellContainer(book)) return;
        ISpellContainer container = ISpellContainer.get(book);
        ISpellContainerMutable edit = null;
        for (SpellSlot slot : container.getAllSpells()) {
            if (slot == null) continue;
            AbstractSpell from = slot.getSpell();
            AbstractSpell to = devil ? AddonSpellRegistry.devilVersionOf(from) : AddonSpellRegistry.normalVersionOf(from);
            if (to == null || to == from) continue;
            if (edit == null) edit = container.mutableCopy();
            edit.removeSpellAtIndex(slot.index());
            edit.addSpellAtIndex(to, slot.getLevel(), slot.index(), slot.isLocked());
        }
        if (edit != null) ISpellContainer.set(book, edit.toImmutable());
    }
}
