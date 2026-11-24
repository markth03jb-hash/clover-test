package com.ironspellgrimoire.item;

import com.ironspellgrimoire.server.Rivals;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
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
 * A Rival's Token (bought on the Poster Board for merit stars): use it to call a rival to fight you here and now.
 * Sneak and use to choose which one.
 */
public class RivalTokenItem extends Item {
    public RivalTokenItem() { super(new Item.Properties().stacksTo(16).rarity(Rarity.RARE)); }

    public static int chosen(ItemStack s) { return s.hasTag() ? Math.floorMod(s.getTag().getInt("Rival"), Rivals.COUNT) : 0; }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack s = p.getItemInHand(hand);
        if (level.isClientSide || !(p instanceof ServerPlayer sp)) return InteractionResultHolder.sidedSuccess(s, level.isClientSide);
        int id = chosen(s);
        if (p.isShiftKeyDown()) {
            id = (id + 1) % Rivals.COUNT;
            s.getOrCreateTag().putInt("Rival", id);
            sp.displayClientMessage(Component.literal("The token now calls " + Rivals.NAMES[id] + "  (rank " + Rivals.rankFor(sp, id) + ")")
                    .withStyle(Rivals.COLOR[id]), true);
            level.playSound(null, p.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.6f, 1.4f);
            return InteractionResultHolder.success(s);
        }
        if (Rivals.useToken(sp, id) && !p.isCreative()) s.shrink(1);
        return InteractionResultHolder.success(s);
    }

    @Override
    public void appendHoverText(ItemStack s, Level level, List<Component> tip, TooltipFlag flag) {
        int id = chosen(s);
        tip.add(Component.literal("Calls: " + Rivals.NAMES[id] + ", " + Rivals.EPITHETS[id]).withStyle(Rivals.COLOR[id]));
        tip.add(Component.literal("Use to call them here. Sneak and use to choose another.").withStyle(ChatFormatting.GRAY));
    }
}
