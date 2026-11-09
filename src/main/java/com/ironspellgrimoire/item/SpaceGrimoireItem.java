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

/** Finral Roulacase's three-leaf grimoire of Spatial Magic: eight preset spells, uses normal mana. */
public class SpaceGrimoireItem extends SimpleAttributeSpellBook {
    public SpaceGrimoireItem() {
        super(8, SpellRarity.LEGENDARY, modifiers());
    }

    private static Multimap<Attribute, AttributeModifier> modifiers() {
        ImmutableMultimap.Builder<Attribute, AttributeModifier> b = ImmutableMultimap.builder();
        b.put(AttributeRegistry.MAX_MANA.get(), mod("7c8d9e0f-1a2b-4c3d-9e4f-5a6b7c8d0e01", "Space Grimoire mana", 800, AttributeModifier.Operation.ADDITION));
        b.put(AttributeRegistry.MANA_REGEN.get(), mod("7c8d9e0f-1a2b-4c3d-9e4f-5a6b7c8d0e02", "Space Grimoire mana flow", 0.5, AttributeModifier.Operation.MULTIPLY_BASE));
        b.put(AttributeRegistry.ENDER_SPELL_POWER.get(), mod("7c8d9e0f-1a2b-4c3d-9e4f-5a6b7c8d0e03", "Space Grimoire reach", 0.2, AttributeModifier.Operation.MULTIPLY_BASE));
        b.put(Attributes.MOVEMENT_SPEED, mod("7c8d9e0f-1a2b-4c3d-9e4f-5a6b7c8d0e04", "Space Grimoire light step", 0.05, AttributeModifier.Operation.MULTIPLY_BASE));
        return b.build();
    }

    private static AttributeModifier mod(String uuid, String name, double amount, AttributeModifier.Operation op) {
        return new AttributeModifier(UUID.fromString(uuid), name, amount, op);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tip, flag);
        GrimoireTooltip.add(tip, "spatial_grimoire", ChatFormatting.BLUE);
    }

    @Override
    public void initializeSpellContainer(ItemStack stack) {
        if (stack == null || ISpellContainer.isSpellContainer(stack)) return;
        super.initializeSpellContainer(stack);
        ISpellContainer container = ISpellContainer.get(stack);
        for (RegistryObject<AbstractSpell> spell : AddonSpellRegistry.SPACE_SPELLS) {
            container.addSpell(spell.get(), 1, true, stack);
        }
        container.save(stack);
    }
}
