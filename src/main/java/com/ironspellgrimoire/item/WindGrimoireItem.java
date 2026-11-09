package com.ironspellgrimoire.item;

import com.ironspellgrimoire.registry.AddonSpellRegistry;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.item.spell_books.SimpleAttributeSpellBook;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraftforge.registries.RegistryObject;

/** Yuno Grinberryall's four-leaf grimoire of Wind Magic: ten preset spells, uses normal mana. */
public class WindGrimoireItem extends SimpleAttributeSpellBook {
    public WindGrimoireItem() {
        super(10, SpellRarity.LEGENDARY, modifiers());
    }

    private static Multimap<Attribute, AttributeModifier> modifiers() {
        ImmutableMultimap.Builder<Attribute, AttributeModifier> b = ImmutableMultimap.builder();
        b.put(AttributeRegistry.MAX_MANA.get(), mod("a91c5e20-7b3d-4c6e-9f10-2b3c4d5e0f01", "Wind Grimoire mana", 700, AttributeModifier.Operation.ADDITION));
        b.put(AttributeRegistry.EVOCATION_SPELL_POWER.get(), mod("a91c5e20-7b3d-4c6e-9f10-2b3c4d5e0f02", "Wind Grimoire wind", 0.15, AttributeModifier.Operation.MULTIPLY_BASE));
        b.put(AttributeRegistry.MANA_REGEN.get(), mod("a91c5e20-7b3d-4c6e-9f10-2b3c4d5e0f03", "Wind Grimoire flow", 0.65, AttributeModifier.Operation.MULTIPLY_BASE));
        b.put(AttributeRegistry.COOLDOWN_REDUCTION.get(), mod("a91c5e20-7b3d-4c6e-9f10-2b3c4d5e0f04", "Wind Grimoire haste", 0.05, AttributeModifier.Operation.MULTIPLY_BASE));
        b.put(Attributes.MOVEMENT_SPEED, mod("a91c5e20-7b3d-4c6e-9f10-2b3c4d5e0f05", "Wind Grimoire tailwind", 0.08, AttributeModifier.Operation.MULTIPLY_BASE));
        return b.build();
    }

    private static AttributeModifier mod(String uuid, String name, double amount, AttributeModifier.Operation op) {
        return new AttributeModifier(UUID.fromString(uuid), name, amount, op);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tip, flag);
        tip.add(Component.translatable("tooltip.iron_spell_grimoire.wind_grimoire.passive").withStyle(ChatFormatting.GREEN));
        tip.add(Component.translatable("tooltip.iron_spell_grimoire.wind_grimoire.desc").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("tooltip.iron_spell_grimoire.wind_grimoire.lore").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }

    @Override
    public void initializeSpellContainer(ItemStack stack) {
        if (stack == null || ISpellContainer.isSpellContainer(stack)) return;
        super.initializeSpellContainer(stack);
        ISpellContainer container = ISpellContainer.get(stack);
        for (RegistryObject<AbstractSpell> spell : AddonSpellRegistry.WIND_SPELLS) {
            container.addSpell(spell.get(), 1, true, stack);
        }
        container.save(stack);
    }
}
