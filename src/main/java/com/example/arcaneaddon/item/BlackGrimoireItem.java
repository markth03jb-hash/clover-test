package com.example.arcaneaddon.item;

import com.example.arcaneaddon.registry.AddonSpellRegistry;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.item.spell_books.SimpleAttributeSpellBook;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import java.util.List;

import java.util.UUID;

/**
 * Wearing the Black Grimoire permanently boosts the body itself through attribute modifiers
 * (not potion effects, so they show no status icon and can't be milked/cleared):
 *   +30% spell power, +10 max health (5 hearts), +2 attack damage, +20% movement speed,
 *   +10% attack speed, +6 armor, +2 armor toughness, +50% knockback resistance.
 * Change the numbers in modifiers() below.
 */
public class BlackGrimoireItem extends SimpleAttributeSpellBook {
    public BlackGrimoireItem() {
        super(10, SpellRarity.LEGENDARY, modifiers());
    }

    private static Multimap<Attribute, AttributeModifier> modifiers() {
        ImmutableMultimap.Builder<Attribute, AttributeModifier> b = ImmutableMultimap.builder();
        b.put(AttributeRegistry.SPELL_POWER.get(), mod("a1c2f7d0-5b1e-4c55-9a10-0b1d6a000001", "Grimoire spell power", 0.30, Operation.MULTIPLY_BASE));
        b.put(Attributes.MAX_HEALTH,              mod("a1c2f7d0-5b1e-4c55-9a10-0b1d6a000002", "Grimoire vitality", 10.0, Operation.ADDITION));
        b.put(Attributes.ATTACK_DAMAGE,           mod("a1c2f7d0-5b1e-4c55-9a10-0b1d6a000003", "Grimoire strength", 2.0, Operation.ADDITION));
        b.put(Attributes.MOVEMENT_SPEED,          mod("a1c2f7d0-5b1e-4c55-9a10-0b1d6a000004", "Grimoire swiftness", 0.20, Operation.MULTIPLY_BASE));
        b.put(Attributes.ATTACK_SPEED,            mod("a1c2f7d0-5b1e-4c55-9a10-0b1d6a000005", "Grimoire agility", 0.10, Operation.MULTIPLY_BASE));
        b.put(Attributes.ARMOR,                   mod("a1c2f7d0-5b1e-4c55-9a10-0b1d6a000006", "Grimoire resilience", 6.0, Operation.ADDITION));
        b.put(Attributes.KNOCKBACK_RESISTANCE,    mod("a1c2f7d0-5b1e-4c55-9a10-0b1d6a000007", "Grimoire stability", 0.5, Operation.ADDITION));
        b.put(Attributes.ARMOR_TOUGHNESS,         mod("a1c2f7d0-5b1e-4c55-9a10-0b1d6a000008", "Grimoire fortitude", 2.0, Operation.ADDITION));
        return b.build();
    }

    private static AttributeModifier mod(String uuid, String name, double amount, Operation op) {
        return new AttributeModifier(UUID.fromString(uuid), name, amount, op);
    }

    @Override
    public boolean isFoil(ItemStack stack) { return true; }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tip, flag);
        tip.add(Component.translatable("tooltip.arcane_addon.black_grimoire.1").withStyle(ChatFormatting.DARK_RED));
        tip.add(Component.translatable("tooltip.arcane_addon.black_grimoire.2").withStyle(ChatFormatting.GRAY));
    }

    /** Called by Iron's to fill the book; pre-inscribes the grimoire's exclusive spell (locked). */
    @Override
    public void initializeSpellContainer(ItemStack stack) {
        if (stack == null) return;
        if (!ISpellContainer.isSpellContainer(stack)) {
            super.initializeSpellContainer(stack);
            ISpellContainer container = ISpellContainer.get(stack);
            container.addSpell(AddonSpellRegistry.SUMMON_GREATSWORD.get(), 1, true, stack);
            container.addSpell(AddonSpellRegistry.PIERCING_DASH.get(), 1, true, stack);
            container.addSpell(AddonSpellRegistry.ZETTEN.get(), 1, true, stack);
            container.addSpell(AddonSpellRegistry.SKY_SPLITTER.get(), 1, true, stack);
            container.addSpell(AddonSpellRegistry.ANTI_MAGIC.get(), 1, true, stack);
            container.addSpell(AddonSpellRegistry.KI_SENSE.get(), 1, true, stack);
            container.addSpell(AddonSpellRegistry.INSTINCT_GUARD.get(), 1, true, stack);
            container.addSpell(AddonSpellRegistry.DEVIL_UNION.get(), 1, true, stack);
            container.addSpell(AddonSpellRegistry.DWELLER_SLASH.get(), 1, true, stack);
            container.addSpell(AddonSpellRegistry.BORROWED_SLASH.get(), 1, true, stack);
            container.save(stack);
        }
    }
}
