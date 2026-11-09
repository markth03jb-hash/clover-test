package com.ironspellgrimoire.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.Nullable;
import java.util.List;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.level.Level;

/**
 * Demon Slayer Sword, summoned by the Black Grimoire (3D model from the supplied OBJ).
 * Damage = 1 (hand) + 4 (netherite tier) + 8 = 13, i.e. netherite sword (8) + 5.
 * Attack speed = 4.0 - 3.0 = 1.0 (netherite sword is 1.6) -> heavy, slow swings.
 */
public class DemonSlayerSwordItem extends SwordItem {
    public DemonSlayerSwordItem() {
        super(Tiers.NETHERITE, 8, -3.0F, new Properties().stacksTo(1).fireResistant());
    }

    /** Dropped/tossed copies (Q, death drops) disappear too. */
    @Override
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        entity.discard();
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tip, flag);
        tip.add(Component.translatable("tooltip.iron_spell_grimoire.demon_slayer_sword.1").withStyle(ChatFormatting.DARK_RED));
        tip.add(Component.translatable("tooltip.iron_spell_grimoire.demon_slayer_sword.2").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public boolean isDamageable(ItemStack stack) {
        return false; // never breaks
    }
}
