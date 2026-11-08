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

/** Zenon Zogratis' grimoire of Bone Magic: ten preset spells, uses normal mana. */
public class BoneGrimoireItem extends SimpleAttributeSpellBook {
    public BoneGrimoireItem() {
        super(10, SpellRarity.LEGENDARY, modifiers());
    }

    private static Multimap<Attribute, AttributeModifier> modifiers() {
        ImmutableMultimap.Builder<Attribute, AttributeModifier> b = ImmutableMultimap.builder();
        b.put(AttributeRegistry.MAX_MANA.get(), mod("a8b9c0d1-e2f3-4a5b-9c6d-7e8f9a0b0e01", "Bone Grimoire mana", 700, AttributeModifier.Operation.ADDITION));
        b.put(AttributeRegistry.MANA_REGEN.get(), mod("a8b9c0d1-e2f3-4a5b-9c6d-7e8f9a0b0e99", "Bone Grimoire mana flow", 0.5, AttributeModifier.Operation.MULTIPLY_BASE));
        b.put(AttributeRegistry.BLOOD_SPELL_POWER.get(), mod("a8b9c0d1-e2f3-4a5b-9c6d-7e8f9a0b0e02", "Bone Grimoire marrow", 0.2, AttributeModifier.Operation.MULTIPLY_BASE));
        b.put(Attributes.ARMOR, mod("a8b9c0d1-e2f3-4a5b-9c6d-7e8f9a0b0e03", "Bone Grimoire plates", 3.0, AttributeModifier.Operation.ADDITION));
        b.put(Attributes.ARMOR_TOUGHNESS, mod("a8b9c0d1-e2f3-4a5b-9c6d-7e8f9a0b0e04", "Bone Grimoire hard bone", 1.0, AttributeModifier.Operation.ADDITION));
        b.put(Attributes.MAX_HEALTH, mod("a8b9c0d1-e2f3-4a5b-9c6d-7e8f9a0b0e05", "Bone Grimoire regrowth", 4.0, AttributeModifier.Operation.ADDITION));
        return b.build();
    }

    private static AttributeModifier mod(String uuid, String name, double amount, AttributeModifier.Operation op) {
        return new AttributeModifier(UUID.fromString(uuid), name, amount, op);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tip, flag);
        tip.add(Component.translatable("tooltip.arcane_addon.bone_grimoire.passive").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("tooltip.arcane_addon.bone_grimoire.desc").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("tooltip.arcane_addon.bone_grimoire.lore").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }

    @Override
    public void initializeSpellContainer(ItemStack stack) {
        if (stack == null || ISpellContainer.isSpellContainer(stack)) return;
        super.initializeSpellContainer(stack);
        ISpellContainer container = ISpellContainer.get(stack);
        for (RegistryObject<AbstractSpell> spell : AddonSpellRegistry.BONE_SPELLS) {
            container.addSpell(spell.get(), 1, true, stack);
        }
        container.save(stack);
    }
}
