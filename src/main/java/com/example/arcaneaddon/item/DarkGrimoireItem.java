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

/** Yami Sukehiro's three-leaf grimoire of Dark Magic: ten preset spells, uses normal mana. */
public class DarkGrimoireItem extends SimpleAttributeSpellBook {
    public DarkGrimoireItem() {
        super(10, SpellRarity.LEGENDARY, modifiers());
    }

    private static Multimap<Attribute, AttributeModifier> modifiers() {
        ImmutableMultimap.Builder<Attribute, AttributeModifier> b = ImmutableMultimap.builder();
        b.put(AttributeRegistry.MAX_MANA.get(), mod("d4a7b9c0-1e2f-4a3b-8c4d-5e6f00000d01", "Dark Grimoire mana", 300, AttributeModifier.Operation.ADDITION));
        b.put(AttributeRegistry.ENDER_SPELL_POWER.get(), mod("d4a7b9c0-1e2f-4a3b-8c4d-5e6f00000d02", "Dark Grimoire darkness", 0.15, AttributeModifier.Operation.MULTIPLY_BASE));
        b.put(AttributeRegistry.SPELL_RESIST.get(), mod("d4a7b9c0-1e2f-4a3b-8c4d-5e6f00000d03", "Dark Grimoire ward", 0.10, AttributeModifier.Operation.MULTIPLY_BASE));
        b.put(Attributes.ATTACK_DAMAGE, mod("d4a7b9c0-1e2f-4a3b-8c4d-5e6f00000d04", "Dark Grimoire strength", 2.0, AttributeModifier.Operation.ADDITION));
        b.put(Attributes.MAX_HEALTH, mod("d4a7b9c0-1e2f-4a3b-8c4d-5e6f00000d05", "Dark Grimoire body", 4.0, AttributeModifier.Operation.ADDITION));
        return b.build();
    }

    private static AttributeModifier mod(String uuid, String name, double amount, AttributeModifier.Operation op) {
        return new AttributeModifier(UUID.fromString(uuid), name, amount, op);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tip, flag);
        tip.add(Component.translatable("tooltip.arcane_addon.dark_grimoire.passive").withStyle(ChatFormatting.DARK_PURPLE));
        tip.add(Component.translatable("tooltip.arcane_addon.dark_grimoire.desc").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("tooltip.arcane_addon.dark_grimoire.lore").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }

    @Override
    public void initializeSpellContainer(ItemStack stack) {
        if (stack == null || ISpellContainer.isSpellContainer(stack)) return;
        super.initializeSpellContainer(stack);
        ISpellContainer container = ISpellContainer.get(stack);
        for (RegistryObject<AbstractSpell> spell : AddonSpellRegistry.DARK_SPELLS) {
            container.addSpell(spell.get(), 1, true, stack);
        }
        container.save(stack);
    }
}
