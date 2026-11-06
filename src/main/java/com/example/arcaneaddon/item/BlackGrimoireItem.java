package com.example.arcaneaddon.item;

import com.example.arcaneaddon.registry.AddonSpellRegistry;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.item.spell_books.SimpleAttributeSpellBook;
import net.minecraft.world.item.ItemStack;

public class BlackGrimoireItem extends SimpleAttributeSpellBook {
    public BlackGrimoireItem() {
        super(8, SpellRarity.LEGENDARY, AttributeRegistry.SPELL_POWER.get(), 0.15);
    }

    /** Called by Iron's to fill the book; pre-inscribes the grimoire's exclusive spell (locked). */
    @Override
    public void initializeSpellContainer(ItemStack stack) {
        if (stack == null) return;
        if (!ISpellContainer.isSpellContainer(stack)) {
            super.initializeSpellContainer(stack);
            ISpellContainer container = ISpellContainer.get(stack);
            container.addSpell(AddonSpellRegistry.SUMMON_GREATSWORD.get(), 1, true, stack);
            container.save(stack);
        }
    }
}
