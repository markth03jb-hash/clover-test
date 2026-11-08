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

/** Mereoleona Vermillion's grimoire of Flame Magic: ten preset spells, uses normal mana. */
public class HellfireGrimoireItem extends SimpleAttributeSpellBook {
    public HellfireGrimoireItem() {
        super(10, SpellRarity.LEGENDARY, modifiers());
    }

    private static Multimap<Attribute, AttributeModifier> modifiers() {
        ImmutableMultimap.Builder<Attribute, AttributeModifier> b = ImmutableMultimap.builder();
        b.put(AttributeRegistry.MAX_MANA.get(), mod("e4b6c8d0-3f5a-4172-8c3d-4e5f6a7b0d01", "Hellfire Grimoire mana", 400, AttributeModifier.Operation.ADDITION));
        b.put(AttributeRegistry.FIRE_SPELL_POWER.get(), mod("e4b6c8d0-3f5a-4172-8c3d-4e5f6a7b0d02", "Hellfire Grimoire flame", 0.25, AttributeModifier.Operation.MULTIPLY_BASE));
        b.put(AttributeRegistry.FIRE_MAGIC_RESIST.get(), mod("e4b6c8d0-3f5a-4172-8c3d-4e5f6a7b0d03", "Hellfire Grimoire heat", 0.35, AttributeModifier.Operation.MULTIPLY_BASE));
        b.put(Attributes.ATTACK_DAMAGE, mod("e4b6c8d0-3f5a-4172-8c3d-4e5f6a7b0d04", "Hellfire Grimoire fists", 4.0, AttributeModifier.Operation.ADDITION));
        b.put(Attributes.MAX_HEALTH, mod("e4b6c8d0-3f5a-4172-8c3d-4e5f6a7b0d05", "Hellfire Grimoire wild", 6.0, AttributeModifier.Operation.ADDITION));
        return b.build();
    }

    private static AttributeModifier mod(String uuid, String name, double amount, AttributeModifier.Operation op) {
        return new AttributeModifier(UUID.fromString(uuid), name, amount, op);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tip, flag);
        tip.add(Component.translatable("tooltip.arcane_addon.hellfire_grimoire.passive").withStyle(ChatFormatting.RED));
        tip.add(Component.translatable("tooltip.arcane_addon.hellfire_grimoire.desc").withStyle(ChatFormatting.RED));
        tip.add(Component.translatable("tooltip.arcane_addon.hellfire_grimoire.lore").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }

    @Override
    public void initializeSpellContainer(ItemStack stack) {
        if (stack == null || ISpellContainer.isSpellContainer(stack)) return;
        super.initializeSpellContainer(stack);
        ISpellContainer container = ISpellContainer.get(stack);
        for (RegistryObject<AbstractSpell> spell : AddonSpellRegistry.HELLFIRE_SPELLS) {
            container.addSpell(spell.get(), 1, true, stack);
        }
        container.save(stack);
    }
}
