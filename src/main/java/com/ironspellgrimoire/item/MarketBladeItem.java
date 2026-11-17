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
import java.util.function.Predicate;

/**
 * The Market's blades whose models are meshes built in Blender (tools/blender/) and drawn by Forge's OBJ loader
 * (models/item/NAME.json): a diamond-tier sword with a right-click move on a cooldown, never wearing out.
 * <ul>
 *   <li>Gilded Thorn - 7 damage, speed 1.7. Thornbite (ShadowFx.thornbite): a wide slash; every foe cut bleeds for 4 s. 12 s.</li>
 *   <li>Bloodrend - 8 damage, speed 1.6. Crimson Rend (ShadowFx.crimsonRend): a crescent of blood flies 8 blocks through every
 *       foe, harder the more hurt you are (up to +60%). 12 s.</li>
 *   <li>Rosewrath - 8 damage, speed 1.7. Crimson Fracture (ShadowFx.crimsonFracture): glowing cracks race 10 blocks along the
 *       ground and split open under every foe they reach. 12 s.</li>
 *   <li>Frostwing (a greatsword) - 10 damage, speed 1.0. Sovereign's Verdict (ShadowFx.sovereignsVerdict): a second's charge,
 *       then a giant glowing sweep in a full circle - everything within 7 blocks takes 3x a blow and is thrown back. 20 s.</li>
 * </ul>
 */
public class MarketBladeItem extends SwordItem {
    private final Predicate<ServerPlayer> move;
    private final int cooldown;
    private final String name;
    private final ChatFormatting colour;

    /** bonus = attack damage over the diamond tier's; speed = the attack speed modifier (the base is 4). */
    public MarketBladeItem(String name, int bonus, float speed, int cooldown, ChatFormatting colour, Predicate<ServerPlayer> move) {
        super(Tiers.DIAMOND, bonus, speed, new Properties().stacksTo(1).fireResistant().rarity(Rarity.EPIC));
        this.name = name; this.cooldown = cooldown; this.colour = colour; this.move = move;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack st = p.getItemInHand(hand);
        if (!level.isClientSide && p instanceof ServerPlayer sp && move.test(sp)) p.getCooldowns().addCooldown(this, cooldown);
        p.swing(hand);
        return InteractionResultHolder.sidedSuccess(st, level.isClientSide);
    }

    @Override public boolean isDamageable(ItemStack stack) { return false; }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tip, flag);
        tip.add(Component.translatable("tooltip.iron_spell_grimoire." + name + ".1").withStyle(colour));
        tip.add(Component.translatable("tooltip.iron_spell_grimoire.nightfang.2").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
