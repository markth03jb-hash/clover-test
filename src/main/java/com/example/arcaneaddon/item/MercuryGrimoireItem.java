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

/** Nozel Silva's three-leaf grimoire of Mercury Magic: ten preset spells, uses normal mana. */
public class MercuryGrimoireItem extends SimpleAttributeSpellBook {
    public MercuryGrimoireItem() {
        super(10, SpellRarity.LEGENDARY, modifiers());
    }

    private static Multimap<Attribute, AttributeModifier> modifiers() {
        ImmutableMultimap.Builder<Attribute, AttributeModifier> b = ImmutableMultimap.builder();
        b.put(AttributeRegistry.MAX_MANA.get(), mod("b7e3d1a4-5c2f-4e8d-a6b9-1c0d2e3f0a01", "Mercury Grimoire mana", 750, AttributeModifier.Operation.ADDITION));
        b.put(AttributeRegistry.MANA_REGEN.get(), mod("b7e3d1a4-5c2f-4e8d-a6b9-1c0d2e3f0a99", "Mercury Grimoire mana flow", 0.5, AttributeModifier.Operation.MULTIPLY_BASE));
        b.put(AttributeRegistry.EVOCATION_SPELL_POWER.get(), mod("b7e3d1a4-5c2f-4e8d-a6b9-1c0d2e3f0a02", "Mercury Grimoire mercury", 0.15, AttributeModifier.Operation.MULTIPLY_BASE));
        b.put(AttributeRegistry.SPELL_RESIST.get(), mod("b7e3d1a4-5c2f-4e8d-a6b9-1c0d2e3f0a03", "Mercury Grimoire royal ward", 0.12, AttributeModifier.Operation.MULTIPLY_BASE));
        b.put(Attributes.ARMOR, mod("b7e3d1a4-5c2f-4e8d-a6b9-1c0d2e3f0a04", "Mercury Grimoire silver", 4.0, AttributeModifier.Operation.ADDITION));
        b.put(Attributes.MAX_HEALTH, mod("b7e3d1a4-5c2f-4e8d-a6b9-1c0d2e3f0a05", "Mercury Grimoire body", 4.0, AttributeModifier.Operation.ADDITION));
        return b.build();
    }

    private static AttributeModifier mod(String uuid, String name, double amount, AttributeModifier.Operation op) {
        return new AttributeModifier(UUID.fromString(uuid), name, amount, op);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tip, flag);
        tip.add(Component.translatable("tooltip.arcane_addon.mercury_grimoire.passive").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("tooltip.arcane_addon.mercury_grimoire.desc").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("tooltip.arcane_addon.mercury_grimoire.lore").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }

    @Override
    public void initializeSpellContainer(ItemStack stack) {
        if (stack == null || ISpellContainer.isSpellContainer(stack)) return;
        super.initializeSpellContainer(stack);
        ISpellContainer container = ISpellContainer.get(stack);
        for (RegistryObject<AbstractSpell> spell : AddonSpellRegistry.MERCURY_SPELLS) {
            container.addSpell(spell.get(), 1, true, stack);
        }
        container.save(stack);
    }
}
