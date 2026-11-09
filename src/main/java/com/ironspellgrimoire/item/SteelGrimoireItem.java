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

/** Klaus Lunettes' three-leaf grimoire of Steel Magic: eight preset spells, uses normal mana. */
public class SteelGrimoireItem extends SimpleAttributeSpellBook {
    public SteelGrimoireItem() {
        super(8, SpellRarity.EPIC, modifiers());
    }

    private static Multimap<Attribute, AttributeModifier> modifiers() {
        ImmutableMultimap.Builder<Attribute, AttributeModifier> b = ImmutableMultimap.builder();
        b.put(AttributeRegistry.MAX_MANA.get(), mod("e5b8c0d1-2f3a-4b4c-9d5e-6f7a00000e01", "Steel Grimoire mana", 550, AttributeModifier.Operation.ADDITION));
        b.put(AttributeRegistry.MANA_REGEN.get(), mod("e5b8c0d1-2f3a-4b4c-9d5e-6f7a00000e99", "Steel Grimoire mana flow", 0.5, AttributeModifier.Operation.MULTIPLY_BASE));
        b.put(AttributeRegistry.EVOCATION_SPELL_POWER.get(), mod("e5b8c0d1-2f3a-4b4c-9d5e-6f7a00000e02", "Steel Grimoire forging", 0.15, AttributeModifier.Operation.MULTIPLY_BASE));
        b.put(AttributeRegistry.SPELL_RESIST.get(), mod("e5b8c0d1-2f3a-4b4c-9d5e-6f7a00000e03", "Steel Grimoire plating", 0.05, AttributeModifier.Operation.MULTIPLY_BASE));
        b.put(Attributes.ARMOR, mod("e5b8c0d1-2f3a-4b4c-9d5e-6f7a00000e04", "Steel Grimoire armor", 4.0, AttributeModifier.Operation.ADDITION));
        b.put(Attributes.KNOCKBACK_RESISTANCE, mod("e5b8c0d1-2f3a-4b4c-9d5e-6f7a00000e05", "Steel Grimoire footing", 0.2, AttributeModifier.Operation.ADDITION));
        return b.build();
    }

    private static AttributeModifier mod(String uuid, String name, double amount, AttributeModifier.Operation op) {
        return new AttributeModifier(UUID.fromString(uuid), name, amount, op);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tip, flag);
        tip.add(Component.translatable("tooltip.iron_spell_grimoire.steel_grimoire.passive").withStyle(ChatFormatting.GOLD));
        tip.add(Component.translatable("tooltip.iron_spell_grimoire.steel_grimoire.desc").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("tooltip.iron_spell_grimoire.steel_grimoire.lore").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }

    @Override
    public void initializeSpellContainer(ItemStack stack) {
        if (stack == null || ISpellContainer.isSpellContainer(stack)) return;
        super.initializeSpellContainer(stack);
        ISpellContainer container = ISpellContainer.get(stack);
        for (RegistryObject<AbstractSpell> spell : AddonSpellRegistry.STEEL_SPELLS) {
            container.addSpell(spell.get(), 1, true, stack);
        }
        container.save(stack);
    }
}
