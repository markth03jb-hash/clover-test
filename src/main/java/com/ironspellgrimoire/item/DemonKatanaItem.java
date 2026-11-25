package com.ironspellgrimoire.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * The Demon-Bound Katana of the Grimoire of the Bound Demon (server/BoundFx; an original design: a gently curved
 * near-black blade with a glowing crimson seam, an eight-sided guard with a demon's red eye in each face, a black grip
 * in a red diamond wrap). Drawn by Demon Draw; it leaves the moment it isn't in your hand - except while the demon has
 * taken you over, when your hand won't let it go.
 */
public class DemonKatanaItem extends SwordItem {
    public DemonKatanaItem() {
        super(Tiers.NETHERITE, 6, -2.2F, new Properties().stacksTo(1).fireResistant().rarity(Rarity.EPIC));
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, level, entity, slot, selected);
        if (level.isClientSide) return;
        if (entity instanceof net.minecraft.server.level.ServerPlayer p && com.ironspellgrimoire.server.BoundFx.takenOver(p)) return;   // the demon holds on
        if (!selected || !(entity instanceof Player p) || p.getMainHandItem() != stack) stack.setCount(0);
    }

    @Override public boolean onDroppedByPlayer(ItemStack item, Player player) { item.setCount(0); return true; }
    @Override public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) { entity.discard(); return true; }
    @Override public boolean isDamageable(ItemStack stack) { return false; }

    @Override
    public void appendHoverText(ItemStack s, Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.literal("A demon lives in the blade. Every blow feeds it.").withStyle(ChatFormatting.RED));
        tip.add(Component.literal("Drawn from the Grimoire of the Bound Demon. It leaves when you let it go.").withStyle(ChatFormatting.GRAY));
    }
}
