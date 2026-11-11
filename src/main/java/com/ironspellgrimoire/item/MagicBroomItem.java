package com.ironspellgrimoire.item;

import com.ironspellgrimoire.entity.BroomEntity;
import com.ironspellgrimoire.registry.AddonEntities;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * The Magic Broom: every mage gets one, once - when they first hold a grimoire (server/BroomFlight). Use it to summon
 * your broom under you and fly; get off and it vanishes back into the item. It isn't used up.
 */
public class MagicBroomItem extends Item {
    public MagicBroomItem() { super(new Item.Properties().stacksTo(1).rarity(Rarity.RARE).fireResistant()); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack s = p.getItemInHand(hand);
        if (p.isPassenger()) return InteractionResultHolder.pass(s);
        if (!level.isClientSide) {
            BroomEntity b = AddonEntities.BROOM.get().create(level);
            if (b == null) return InteractionResultHolder.fail(s);
            b.moveTo(p.getX(), p.getY() + 0.2, p.getZ(), p.getYRot(), 0);
            level.addFreshEntity(b);
            p.startRiding(b);
            level.playSound(null, p.blockPosition(), SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 0.3f, 1.6f);
            p.getCooldowns().addCooldown(this, 20);
        }
        return InteractionResultHolder.sidedSuccess(s, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack s, Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.literal("Use to summon it under you and fly.").withStyle(ChatFormatting.GRAY));
        tip.add(Component.literal("Forward flies where you look; back brakes; let go to hover.").withStyle(ChatFormatting.GRAY));
        tip.add(Component.literal("Runs on your mana. Sneak to get off.").withStyle(ChatFormatting.DARK_GRAY));
    }
}
