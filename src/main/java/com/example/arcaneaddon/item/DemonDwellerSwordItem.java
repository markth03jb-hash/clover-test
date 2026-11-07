package com.example.arcaneaddon.item;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Demon Dweller Sword: the fast twin of the Demon Slayer Sword. Damage 1+4+4 = 9 (Slayer: 13), attack speed 4.0-1.8 = 2.2 (Slayer: 1.0).
 * Summoned into the off hand; casting Summon again swaps the two swords between hands.
 * In the off hand it adds +2 attack damage and +0.4 attack speed (dual-wield bonus).
 * It only exists in the main/off hand while the Slayer sword is in the other hand (see GreatswordCleanup).
 */
public class DemonDwellerSwordItem extends SwordItem {
    public DemonDwellerSwordItem() {
        super(Tiers.NETHERITE, 4, -1.8F, new Properties().stacksTo(1).fireResistant());
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
        if (slot == EquipmentSlot.OFFHAND) {
            ImmutableMultimap.Builder<Attribute, AttributeModifier> b = ImmutableMultimap.builder();
            b.put(Attributes.ATTACK_DAMAGE, new AttributeModifier(UUID.fromString("a1c2f7d0-5b1e-4c55-9a10-0b1d6a0000d1"), "Dweller twin blade", 2.0, AttributeModifier.Operation.ADDITION));
            b.put(Attributes.ATTACK_SPEED, new AttributeModifier(UUID.fromString("a1c2f7d0-5b1e-4c55-9a10-0b1d6a0000d2"), "Dweller twin speed", 0.4, AttributeModifier.Operation.ADDITION));
            return b.build();
        }
        return super.getDefaultAttributeModifiers(slot);
    }

    @Override public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) { entity.discard(); return true; }
    @Override public boolean isDamageable(ItemStack stack) { return false; }
    @Override public boolean isFoil(ItemStack stack) { return true; }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tip, flag);
        tip.add(Component.translatable("tooltip.arcane_addon.demon_dweller_sword.1").withStyle(ChatFormatting.DARK_RED));
        tip.add(Component.translatable("tooltip.arcane_addon.demon_dweller_sword.2").withStyle(ChatFormatting.GRAY));
    }
}
