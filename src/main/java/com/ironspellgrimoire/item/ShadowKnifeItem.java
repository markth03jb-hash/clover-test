package com.ironspellgrimoire.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The Market's other knives (original designs; models tools/shadow_items.py), each with its own right-click move
 * (server/ShadowFx):
 *  0 Gloamthorn     - a stiletto. Shadow Mark: thrown through up to three foes and back; your shadows hit them 25%
 *                     harder for 8 s. 12 s.
 *  1 Hollowtooth    - a broad hooked serrated blade. Rend: lunge 6 blocks and tear the first foe, three more cuts over
 *                     3 s; if it dies, your shadows heal 10%. 10 s.
 *  2 Duskglass Kris - a wavy blade of smoked glass. Phantom Edges: three ghost knives circle you for 10 s and strike
 *                     anything that comes within 3 blocks. 20 s.
 */
public class ShadowKnifeItem extends SwordItem {
    public static final String[] NAMES = {"gloamthorn", "hollowtooth", "duskglass_kris"};
    public static final int[] COOLDOWNS = {240, 200, 400};
    private final int kind;

    public ShadowKnifeItem(int kind) {
        super(Tiers.DIAMOND, kind == 1 ? 3 : 2, kind == 0 ? -1.4F : kind == 1 ? -2.0F : -1.7F, new Properties().stacksTo(1).fireResistant().rarity(Rarity.EPIC));
        this.kind = kind;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack st = p.getItemInHand(hand);
        if (!level.isClientSide && p instanceof ServerPlayer sp) {
            boolean did = switch (kind) {
                case 0 -> com.ironspellgrimoire.server.ShadowFx.shadowMark(sp);
                case 1 -> com.ironspellgrimoire.server.ShadowFx.rend(sp);
                default -> com.ironspellgrimoire.server.ShadowFx.phantomEdges(sp);
            };
            if (did) p.getCooldowns().addCooldown(this, COOLDOWNS[kind]);
        }
        return InteractionResultHolder.sidedSuccess(st, level.isClientSide);
    }

    @Override public boolean isDamageable(ItemStack stack) { return false; }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tip, flag);
        tip.add(Component.translatable("tooltip.iron_spell_grimoire." + NAMES[kind] + ".1").withStyle(ChatFormatting.AQUA));
        tip.add(Component.translatable("tooltip.iron_spell_grimoire.nightfang.2").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
