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

/** Gauche Adlai's grimoire of Mirror Magic: eight preset spells, uses normal mana. */
public class MirrorGrimoireItem extends SimpleAttributeSpellBook {
    public MirrorGrimoireItem() {
        super(8, SpellRarity.LEGENDARY, modifiers());
    }

    private static Multimap<Attribute, AttributeModifier> modifiers() {
        ImmutableMultimap.Builder<Attribute, AttributeModifier> b = ImmutableMultimap.builder();
        b.put(AttributeRegistry.MAX_MANA.get(), mod("9d1e2f30-4a5b-4c6d-8e7f-0b1b2c3d4e01", "Mirror Grimoire mana", 750, AttributeModifier.Operation.ADDITION));
        b.put(AttributeRegistry.MANA_REGEN.get(), mod("9d1e2f30-4a5b-4c6d-8e7f-0b1b2c3d4e02", "Mirror Grimoire flow", 0.5, AttributeModifier.Operation.MULTIPLY_BASE));
        b.put(AttributeRegistry.ENDER_SPELL_POWER.get(), mod("9d1e2f30-4a5b-4c6d-8e7f-0b1b2c3d4e03", "Mirror Grimoire gleam", 0.2, AttributeModifier.Operation.MULTIPLY_BASE));
        b.put(AttributeRegistry.COOLDOWN_REDUCTION.get(), mod("9d1e2f30-4a5b-4c6d-8e7f-0b1b2c3d4e04", "Mirror Grimoire quickness", 0.06, AttributeModifier.Operation.MULTIPLY_BASE));
        return b.build();
    }

    private static AttributeModifier mod(String uuid, String name, double amount, AttributeModifier.Operation op) {
        return new AttributeModifier(UUID.fromString(uuid), name, amount, op);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tip, flag);
        GrimoireTooltip.add(tip, "mirror_grimoire", ChatFormatting.LIGHT_PURPLE);
    }

    @Override
    public void initializeSpellContainer(ItemStack stack) {
        if (stack == null || ISpellContainer.isSpellContainer(stack)) return;
        super.initializeSpellContainer(stack);
        ISpellContainer container = ISpellContainer.get(stack);
        for (RegistryObject<AbstractSpell> spell : AddonSpellRegistry.MIRROR_SPELLS) {
            container.addSpell(spell.get(), 1, true, stack);
        }
        container.save(stack);
    }
}
