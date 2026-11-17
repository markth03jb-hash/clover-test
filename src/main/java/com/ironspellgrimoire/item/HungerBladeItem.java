package com.ironspellgrimoire.item;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.ironspellgrimoire.server.HungerFx;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.ForgeMod;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * The Hunger Blade, drawn from the Hunger Blade Grimoire into the main hand. One item for all 23 swords: its "form" tag
 * picks the model (the "iron_spell_grimoire:form" item property, form / 32) and its damage, speed and reach
 * ({@link HungerFx#DMG}, {@link HungerFx#SPEED}, {@link HungerFx#REACH}). It exists only in its wielder's main hand.
 */
public class HungerBladeItem extends Item {
    public static final String FORM = "form";
    private static final UUID REACH_ID = UUID.fromString("5e1f0c3a-7d21-4b8e-9a64-0b2c7a9000a1");

    public HungerBladeItem() { super(new Properties().stacksTo(1).fireResistant()); }

    public static int form(ItemStack s) { return s.hasTag() ? Math.max(0, Math.min(HungerFx.FORMS - 1, s.getTag().getInt(FORM))) : 0; }

    public static ItemStack of(Item item, int form) {
        ItemStack s = new ItemStack(item);
        s.getOrCreateTag().putInt(FORM, form);
        return s;
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        if (slot != EquipmentSlot.MAINHAND) return super.getAttributeModifiers(slot, stack);
        int f = form(stack);
        ImmutableMultimap.Builder<Attribute, AttributeModifier> b = ImmutableMultimap.builder();
        b.put(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_UUID, "Hunger Blade", HungerFx.DMG[f] - 1, AttributeModifier.Operation.ADDITION));
        b.put(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_UUID, "Hunger Blade", HungerFx.SPEED[f], AttributeModifier.Operation.ADDITION));
        if (HungerFx.REACH[f] > 0)
            b.put(ForgeMod.ENTITY_REACH.get(), new AttributeModifier(REACH_ID, "Hunger Blade reach", HungerFx.REACH[f], AttributeModifier.Operation.ADDITION));
        return b.build();
    }

    @Override public Component getName(ItemStack stack) { return Component.literal(HungerFx.NAMES[form(stack)]).withStyle(ChatFormatting.RED); }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        int f = form(stack);
        tip.add(Component.literal("Stage " + HungerFx.STAGE[f] + " - " + HungerFx.LINE_NAMES[HungerFx.line(f)]).withStyle(ChatFormatting.DARK_RED));
        tip.add(Component.literal(HungerFx.PASSIVE[f]).withStyle(ChatFormatting.GRAY));
    }

    /** Dropped or tossed copies vanish: the blade lives in its book. */
    @Override
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        entity.discard();
        return true;
    }

    @Override public boolean isDamageable(ItemStack stack) { return false; }
    @Override public boolean isFoil(ItemStack stack) { return false; }
}
