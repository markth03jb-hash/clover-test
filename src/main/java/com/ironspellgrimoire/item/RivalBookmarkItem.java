package com.ironspellgrimoire.item;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

/** Your rival's bookmark - the keepsake at the end of a rivalry, whichever way it ended. Charm slot: +6% spell power. */
public class RivalBookmarkItem extends Item implements ICurioItem {
    public RivalBookmarkItem() { super(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)); }

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(SlotContext ctx, UUID uuid, ItemStack stack) {
        Multimap<Attribute, AttributeModifier> m = HashMultimap.create();
        m.put(AttributeRegistry.SPELL_POWER.get(), new AttributeModifier(uuid, "Rival's Bookmark", 0.06, AttributeModifier.Operation.MULTIPLY_BASE));
        return m;
    }

    @Override
    public void appendHoverText(ItemStack s, Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.literal("Kept from a rivalry that is over.").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }
}
