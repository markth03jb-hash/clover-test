package com.ironspellgrimoire.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

/**
 * A bounty boss's signature drop (server/BossRules): one per boss, found nowhere else. A charm is worn in the charm
 * slot; an inscription is used (right-click) to write itself into the grimoire you have equipped. From a Hardened
 * kill it comes upgraded ("Hardened" on the item). What each does: server/BossRelics.
 */
public class SignatureItem extends Item implements ICurioItem {
    private final String boss, what, hard, lore;
    public final boolean charm;
    /** For an inscription: the key written on the grimoire. */
    public final String key;

    public SignatureItem(String boss, String what, String hard, String lore, String key) {
        super(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant());
        this.boss = boss; this.what = what; this.hard = hard; this.lore = lore; this.key = key;
        this.charm = key == null;
    }

    public static boolean hardened(ItemStack s) { return s.hasTag() && s.getTag().getBoolean("Hardened"); }

    @Override public boolean isFoil(ItemStack s) { return hardened(s) || super.isFoil(s); }

    @Override
    public void appendHoverText(ItemStack s, Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.literal(boss + (hard.isEmpty() ? "'s charm" : "'s signature") + "  -  " + (charm ? "charm slot" : "use it to write it into your grimoire")).withStyle(ChatFormatting.LIGHT_PURPLE));
        if (hard.isEmpty()) {   // a charm from the wild: no Hardened form
            tip.add(Component.literal(what).withStyle(ChatFormatting.GRAY));
        } else if (hardened(s)) {
            tip.add(Component.literal("Hardened").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            tip.add(Component.literal(hard).withStyle(ChatFormatting.GRAY));
        } else {
            tip.add(Component.literal(what).withStyle(ChatFormatting.GRAY));
            tip.add(Component.literal("Hardened: " + hard).withStyle(ChatFormatting.DARK_GRAY));
        }
        tip.add(Component.literal(lore).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack s = player.getItemInHand(hand);
        if (charm) return InteractionResultHolder.pass(s);
        if (!level.isClientSide && player instanceof ServerPlayer sp && com.ironspellgrimoire.server.BossRelics.inscribe(sp, s, this)) {
            if (!sp.isCreative()) s.shrink(1);
            return InteractionResultHolder.consume(s);
        }
        return InteractionResultHolder.sidedSuccess(s, level.isClientSide);
    }
}
