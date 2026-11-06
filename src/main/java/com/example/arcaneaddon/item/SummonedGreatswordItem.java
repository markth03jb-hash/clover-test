package com.example.arcaneaddon.item;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.level.Level;

/**
 * Greatsword summoned by the Black Grimoire.
 * Damage = 1 (hand) + 4 (netherite tier) + 8 = 13, i.e. netherite sword (8) + 5.
 * Attack speed = 4.0 - 3.0 = 1.0 (netherite sword is 1.6) -> heavy, slow swings.
 */
public class SummonedGreatswordItem extends SwordItem {
    public SummonedGreatswordItem() {
        super(Tiers.NETHERITE, 8, -3.0F, new Properties().stacksTo(1).fireResistant());
    }

    /** Vanishes as soon as it is not the item in the selected hotbar slot. */
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, level, entity, slot, selected);
        if (!selected && !level.isClientSide) {
            stack.setCount(0);
        }
    }

    /** Dropped/tossed copies (Q, death drops) disappear too. */
    @Override
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        entity.discard();
        return true;
    }

    @Override
    public boolean isDamageable(ItemStack stack) {
        return false; // never breaks
    }
}
