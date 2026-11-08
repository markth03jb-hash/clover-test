package com.example.arcaneaddon.item;

import com.example.arcaneaddon.registry.AddonSpellRegistry;
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

/** Luck Voltia's grimoire of Lightning Magic: ten preset spells, uses normal mana. */
public class LightningGrimoireItem extends SimpleAttributeSpellBook {
    public LightningGrimoireItem() {
        super(10, SpellRarity.LEGENDARY, modifiers());
    }

    private static Multimap<Attribute, AttributeModifier> modifiers() {
        ImmutableMultimap.Builder<Attribute, AttributeModifier> b = ImmutableMultimap.builder();
        b.put(AttributeRegistry.MAX_MANA.get(), mod("d3a5b7c9-2e4f-4061-9b2c-3d4e5f6a0c01", "Lightning Grimoire mana", 250, AttributeModifier.Operation.ADDITION));
        b.put(AttributeRegistry.LIGHTNING_SPELL_POWER.get(), mod("d3a5b7c9-2e4f-4061-9b2c-3d4e5f6a0c02", "Lightning Grimoire thunder", 0.2, AttributeModifier.Operation.MULTIPLY_BASE));
        b.put(AttributeRegistry.CAST_TIME_REDUCTION.get(), mod("d3a5b7c9-2e4f-4061-9b2c-3d4e5f6a0c03", "Lightning Grimoire speed", 0.15, AttributeModifier.Operation.MULTIPLY_BASE));
        b.put(Attributes.MOVEMENT_SPEED, mod("d3a5b7c9-2e4f-4061-9b2c-3d4e5f6a0c04", "Lightning Grimoire boots", 0.1, AttributeModifier.Operation.MULTIPLY_BASE));
        b.put(Attributes.ATTACK_SPEED, mod("d3a5b7c9-2e4f-4061-9b2c-3d4e5f6a0c05", "Lightning Grimoire fists", 0.2, AttributeModifier.Operation.MULTIPLY_BASE));
        return b.build();
    }

    private static AttributeModifier mod(String uuid, String name, double amount, AttributeModifier.Operation op) {
        return new AttributeModifier(UUID.fromString(uuid), name, amount, op);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tip, flag);
        tip.add(Component.translatable("tooltip.arcane_addon.lightning_grimoire.passive").withStyle(ChatFormatting.YELLOW));
        tip.add(Component.translatable("tooltip.arcane_addon.lightning_grimoire.desc").withStyle(ChatFormatting.YELLOW));
        tip.add(Component.translatable("tooltip.arcane_addon.lightning_grimoire.lore").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }

    @Override
    public void initializeSpellContainer(ItemStack stack) {
        if (stack == null || ISpellContainer.isSpellContainer(stack)) return;
        super.initializeSpellContainer(stack);
        ISpellContainer container = ISpellContainer.get(stack);
        for (RegistryObject<AbstractSpell> spell : AddonSpellRegistry.LIGHTNING_SPELLS) {
            container.addSpell(spell.get(), 1, true, stack);
        }
        container.save(stack);
    }
}
