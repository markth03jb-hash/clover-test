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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraftforge.registries.RegistryObject;

/** Vanessa Enoteca's three-leaf grimoire of Thread Magic: eight preset spells, uses normal mana. */
public class ThreadGrimoireItem extends SimpleAttributeSpellBook {
    public ThreadGrimoireItem() {
        super(8, SpellRarity.LEGENDARY, modifiers());
    }

    private static Multimap<Attribute, AttributeModifier> modifiers() {
        ImmutableMultimap.Builder<Attribute, AttributeModifier> b = ImmutableMultimap.builder();
        b.put(AttributeRegistry.MAX_MANA.get(), mod("6b7c8d9e-0f1a-4b2c-8d3e-4f5a6b7c0e01", "Thread Grimoire mana", 600, AttributeModifier.Operation.ADDITION));
        b.put(AttributeRegistry.MANA_REGEN.get(), mod("6b7c8d9e-0f1a-4b2c-8d3e-4f5a6b7c0e02", "Thread Grimoire mana flow", 0.6, AttributeModifier.Operation.MULTIPLY_BASE));
        b.put(AttributeRegistry.EVOCATION_SPELL_POWER.get(), mod("6b7c8d9e-0f1a-4b2c-8d3e-4f5a6b7c0e03", "Thread Grimoire weave", 0.2, AttributeModifier.Operation.MULTIPLY_BASE));
        b.put(AttributeRegistry.COOLDOWN_REDUCTION.get(), mod("6b7c8d9e-0f1a-4b2c-8d3e-4f5a6b7c0e04", "Thread Grimoire nimble fingers", 0.1, AttributeModifier.Operation.MULTIPLY_BASE));
        return b.build();
    }

    private static AttributeModifier mod(String uuid, String name, double amount, AttributeModifier.Operation op) {
        return new AttributeModifier(UUID.fromString(uuid), name, amount, op);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tip, flag);
        tip.add(Component.translatable("tooltip.iron_spell_grimoire.string_grimoire.passive").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("tooltip.iron_spell_grimoire.string_grimoire.desc").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("tooltip.iron_spell_grimoire.string_grimoire.lore").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }

    @Override
    public void initializeSpellContainer(ItemStack stack) {
        if (stack == null || ISpellContainer.isSpellContainer(stack)) return;
        super.initializeSpellContainer(stack);
        ISpellContainer container = ISpellContainer.get(stack);
        for (RegistryObject<AbstractSpell> spell : AddonSpellRegistry.THREAD_SPELLS) {
            container.addSpell(spell.get(), 1, true, stack);
        }
        container.save(stack);
    }
}
