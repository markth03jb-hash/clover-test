package com.example.arcaneaddon.item;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.item.spell_books.SimpleAttributeSpellBook;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * A normal inscribable spell book (like Iron's own books) with fixed stats and one passive ability.
 * The passive itself lives in {@link com.example.arcaneaddon.server.GrimoirePassives}.
 */
public class ThemedGrimoireItem extends SimpleAttributeSpellBook {
    private final String id;

    public ThemedGrimoireItem(String id, int slots, SpellRarity rarity, Multimap<Attribute, AttributeModifier> modifiers) {
        super(slots, rarity, modifiers);
        this.id = id;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tip, flag);
        tip.add(Component.translatable("tooltip.arcane_addon." + id + ".passive").withStyle(ChatFormatting.GOLD));
        tip.add(Component.translatable("tooltip.arcane_addon." + id + ".desc").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("tooltip.arcane_addon." + id + ".lore").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }

    /** Small builder so the registry can describe each book's stats in one line per attribute. */
    public static final class Stats {
        private final ImmutableMultimap.Builder<Attribute, AttributeModifier> b = ImmutableMultimap.builder();
        private final String prefix;

        public Stats(String prefix) { this.prefix = prefix; }

        public Stats add(Attribute attribute, String uuid, double amount, AttributeModifier.Operation op) {
            b.put(attribute, new AttributeModifier(UUID.fromString(uuid), prefix, amount, op));
            return this;
        }

        public Multimap<Attribute, AttributeModifier> build() { return b.build(); }
    }
}
