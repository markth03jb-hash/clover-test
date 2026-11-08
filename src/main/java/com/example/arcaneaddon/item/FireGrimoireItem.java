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

/** Magna Swing's grimoire of Flame Magic: ten preset spells, uses normal mana. */
public class FireGrimoireItem extends SimpleAttributeSpellBook {
    public FireGrimoireItem() {
        super(10, SpellRarity.LEGENDARY, modifiers());
    }

    private static Multimap<Attribute, AttributeModifier> modifiers() {
        ImmutableMultimap.Builder<Attribute, AttributeModifier> b = ImmutableMultimap.builder();
        b.put(AttributeRegistry.MAX_MANA.get(), mod("c2f4a6b8-1d3e-4f50-8a1b-2c3d4e5f0b01", "Fire Grimoire mana", 250, AttributeModifier.Operation.ADDITION));
        b.put(AttributeRegistry.FIRE_SPELL_POWER.get(), mod("c2f4a6b8-1d3e-4f50-8a1b-2c3d4e5f0b02", "Fire Grimoire flame", 0.2, AttributeModifier.Operation.MULTIPLY_BASE));
        b.put(AttributeRegistry.FIRE_MAGIC_RESIST.get(), mod("c2f4a6b8-1d3e-4f50-8a1b-2c3d4e5f0b03", "Fire Grimoire heat", 0.25, AttributeModifier.Operation.MULTIPLY_BASE));
        b.put(Attributes.ATTACK_DAMAGE, mod("c2f4a6b8-1d3e-4f50-8a1b-2c3d4e5f0b04", "Fire Grimoire fists", 2.0, AttributeModifier.Operation.ADDITION));
        b.put(Attributes.MAX_HEALTH, mod("c2f4a6b8-1d3e-4f50-8a1b-2c3d4e5f0b05", "Fire Grimoire guts", 4.0, AttributeModifier.Operation.ADDITION));
        return b.build();
    }

    private static AttributeModifier mod(String uuid, String name, double amount, AttributeModifier.Operation op) {
        return new AttributeModifier(UUID.fromString(uuid), name, amount, op);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tip, flag);
        tip.add(Component.translatable("tooltip.arcane_addon.fire_grimoire.passive").withStyle(ChatFormatting.GOLD));
        tip.add(Component.translatable("tooltip.arcane_addon.fire_grimoire.desc").withStyle(ChatFormatting.GOLD));
        tip.add(Component.translatable("tooltip.arcane_addon.fire_grimoire.lore").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }

    @Override
    public void initializeSpellContainer(ItemStack stack) {
        if (stack == null || ISpellContainer.isSpellContainer(stack)) return;
        super.initializeSpellContainer(stack);
        ISpellContainer container = ISpellContainer.get(stack);
        for (RegistryObject<AbstractSpell> spell : AddonSpellRegistry.FIRE_SPELLS) {
            container.addSpell(spell.get(), 1, true, stack);
        }
        container.save(stack);
    }
}
