package com.ironspellgrimoire.item;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
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
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The shared body of the newer grimoires (Flame Spirit, Lion's Flame, World Tree, Slash, Earth, Beast, Cotton): eight
 * preset spells, normal mana, and a small bonus to the school the book belongs to.
 */
public abstract class ThemedGrimoireItem extends SimpleAttributeSpellBook {
    private final String key;
    private final ChatFormatting color;
    private final Supplier<List<RegistryObject<AbstractSpell>>> spells;

    protected ThemedGrimoireItem(String key, ChatFormatting color, Supplier<List<RegistryObject<AbstractSpell>>> spells,
                                 Supplier<Attribute> school, double mana, double regen, double power, double armor) {
        this(8, key, color, spells, school, mana, regen, power, armor);
    }

    /** A grimoire with {@code slots} pages (Julius's holds twelve). */
    protected ThemedGrimoireItem(int slots, String key, ChatFormatting color, Supplier<List<RegistryObject<AbstractSpell>>> spells,
                                 Supplier<Attribute> school, double mana, double regen, double power, double armor) {
        super(slots, SpellRarity.LEGENDARY, modifiers(key, school.get(), mana, regen, power, armor));
        this.key = key;
        this.color = color;
        this.spells = spells;
    }

    private static Multimap<Attribute, AttributeModifier> modifiers(String key, Attribute school, double mana, double regen, double power, double armor) {
        ImmutableMultimap.Builder<Attribute, AttributeModifier> b = ImmutableMultimap.builder();
        b.put(AttributeRegistry.MAX_MANA.get(), mod(key, "mana", mana, AttributeModifier.Operation.ADDITION));
        b.put(AttributeRegistry.MANA_REGEN.get(), mod(key, "regen", regen, AttributeModifier.Operation.MULTIPLY_BASE));
        b.put(school, mod(key, "power", power, AttributeModifier.Operation.MULTIPLY_BASE));
        if (armor != 0) b.put(Attributes.ARMOR, mod(key, "armour", armor, AttributeModifier.Operation.ADDITION));
        return b.build();
    }

    private static AttributeModifier mod(String key, String what, double amount, AttributeModifier.Operation op) {
        return new AttributeModifier(UUID.nameUUIDFromBytes((key + ":" + what).getBytes(StandardCharsets.UTF_8)), key + " " + what, amount, op);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tip, flag);
        GrimoireTooltip.add(tip, key, color);
    }

    @Override
    public void initializeSpellContainer(ItemStack stack) {
        if (stack == null || ISpellContainer.isSpellContainer(stack)) return;
        super.initializeSpellContainer(stack);
        ISpellContainer container = ISpellContainer.get(stack);
        for (RegistryObject<AbstractSpell> spell : spells.get()) container.addSpell(spell.get(), 1, true, stack);
        container.save(stack);
    }
}
