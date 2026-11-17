package com.ironspellgrimoire.item;

import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
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
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;
import java.util.function.Consumer;

/**
 * The Dragon Fang (an original design; model tools/dragon_fang.py, drawn by client/DragonFangRenderer): a single curved
 * fang, ivory at the root deepening to crimson, ember veins glowing through it, its root held in the jaws of a gold
 * dragon's head; a grip of red scaled hide; a claw clutching an ember orb. Bought in the Shadow Grimoire's Market.
 * Damage 1 + 3 (diamond) + 3 = 7, speed 4.0 - 2.2 = 1.8.
 * Right-click - Wyrmfang Lunge (server/ShadowFx.wyrmfangLunge): dash up to 8 blocks, cutting through everything in the
 * way; each foe's wound bursts a second later, and each one heals you 5%. 14 s.
 */
public class DragonFangItem extends SwordItem implements GeoItem {
    public static final int COOLDOWN = 280;
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.dragon_fang.idle");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public DragonFangItem() {
        super(Tiers.DIAMOND, 3, -2.2F, new Properties().stacksTo(1).fireResistant().rarity(Rarity.EPIC));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack st = p.getItemInHand(hand);
        if (!level.isClientSide && p instanceof ServerPlayer sp && com.ironspellgrimoire.server.ShadowFx.wyrmfangLunge(sp))
            p.getCooldowns().addCooldown(this, COOLDOWN);
        return InteractionResultHolder.sidedSuccess(st, level.isClientSide);
    }

    @Override public boolean isDamageable(ItemStack stack) { return false; }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tip, flag);
        tip.add(Component.translatable("tooltip.iron_spell_grimoire.dragon_fang.1").withStyle(ChatFormatting.RED));
        tip.add(Component.translatable("tooltip.iron_spell_grimoire.nightfang.2").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }

    // ------------------------------------------------------------------ GeckoLib
    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private BlockEntityWithoutLevelRenderer renderer;
            @Override public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) renderer = new com.ironspellgrimoire.client.DragonFangRenderer();
                return renderer;
            }
        });
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar c) {
        c.add(new AnimationController<>(this, "idle", 0, s -> s.setAndContinue(IDLE)));
    }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
