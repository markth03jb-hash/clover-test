package com.example.arcaneaddon.client;

import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * The addon's own animation layer, used to play a second pose part-way through a move (the server sends
 * FxPacket shape 5 with the pose index in {@code palette}). Spell cast poses themselves are played by Iron's
 * Spellbooks from each spell's getCastStartAnimation().
 */
@Mod.EventBusSubscriber(modid = "arcane_addon", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ArcaneAnimations {
    private static final ResourceLocation LAYER = ResourceLocation.fromNamespaceAndPath("arcane_addon", "moves");
    /** Above Iron's cast layer, so a mid-move pose replaces the cast pose while it plays. */
    private static final int PRIORITY = 2000;

    /** Index -> animation id. Must match {@code com.example.arcaneaddon.server.AnimSend}. */
    private static final ResourceLocation[] POSES = {
            ResourceLocation.fromNamespaceAndPath("arcane_addon", "twin_whirlwind"),
            ResourceLocation.fromNamespaceAndPath("arcane_addon", "asta_cross_cut"),
            ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "simple_sword_lunge_stab"),
            ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "sword_deflect_1"),
            ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "horizontal_slash_one_handed"),
            // Surpassing Limits awakenings, one per grimoire (AnimSend.LIMIT_FIRST + book index)
            ResourceLocation.fromNamespaceAndPath("arcane_addon", "limit_black"),
            ResourceLocation.fromNamespaceAndPath("arcane_addon", "limit_dark"),
            ResourceLocation.fromNamespaceAndPath("arcane_addon", "limit_wind"),
            ResourceLocation.fromNamespaceAndPath("arcane_addon", "limit_mercury"),
            ResourceLocation.fromNamespaceAndPath("arcane_addon", "limit_fire"),
            ResourceLocation.fromNamespaceAndPath("arcane_addon", "limit_lightning"),
            ResourceLocation.fromNamespaceAndPath("arcane_addon", "limit_hellfire"),
            ResourceLocation.fromNamespaceAndPath("arcane_addon", "limit_bone"),
            ResourceLocation.fromNamespaceAndPath("arcane_addon", "limit_steel"),
            ResourceLocation.fromNamespaceAndPath("arcane_addon", "fire_magna_special"),   // 14: Magna's swing
            ResourceLocation.fromNamespaceAndPath("arcane_addon", "dark_equinox"),         // 15: Yami's Equinox
    };

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent e) {
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER, PRIORITY, player -> new ModifierLayer<>());
        // idle stances and melee attacks (Yami with his katana, Luck in Thunder God Mode) - below every cast pose
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(StanceAnimations.LAYER, StanceAnimations.PRIORITY, player -> new ModifierLayer<>());
        // Demon Dweller Sword glows in the colour of the element it last borrowed (1 fire, 2 ice, 3 lightning, 4 wind),
        // and "glow" (1 -> 0) lets the glow fade out over its last 1.5 s instead of switching off
        e.enqueueWork(() -> {
            var sword = com.example.arcaneaddon.registry.AddonItemRegistry.DEMON_DWELLER_SWORD.get();
            net.minecraft.client.renderer.item.ItemProperties.register(sword, ResourceLocation.fromNamespaceAndPath("arcane_addon", "element"),
                    (stack, level, entity, seed) -> glowLeft(stack, level, entity) > 0 ? 0.2F * stack.getTag().getInt(com.example.arcaneaddon.item.DemonDwellerSwordItem.GLOW) : 0.0F);   // property values are clamped to 0..1, so element n is 0.2*n
            net.minecraft.client.renderer.item.ItemProperties.register(sword, ResourceLocation.fromNamespaceAndPath("arcane_addon", "glow"),
                    (stack, level, entity, seed) -> Math.min(1.0F, glowLeft(stack, level, entity) / (float) com.example.arcaneaddon.item.DemonDwellerSwordItem.FADE_TICKS));
        });
    }

    /** Ticks of element glow left on a Dweller sword (0 = none). */
    private static long glowLeft(net.minecraft.world.item.ItemStack stack, net.minecraft.client.multiplayer.ClientLevel level, net.minecraft.world.entity.LivingEntity entity) {
        if (!stack.hasTag() || !stack.getTag().contains(com.example.arcaneaddon.item.DemonDwellerSwordItem.GLOW)) return 0;
        net.minecraft.world.level.Level lv = level != null ? level : entity != null ? entity.level() : Minecraft.getInstance().level;
        if (lv == null) return 0;
        return Math.max(0, stack.getTag().getLong(com.example.arcaneaddon.item.DemonDwellerSwordItem.GLOW_UNTIL) - lv.getGameTime());
    }

    @SuppressWarnings("unchecked")
    public static void play(int entityId, int pose) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || pose < 0 || pose >= POSES.length) return;
        Entity ent = mc.level.getEntity(entityId);
        if (!(ent instanceof AbstractClientPlayer player)) return;
        KeyframeAnimation anim = PlayerAnimationRegistry.getAnimation(POSES[pose]);
        if (anim == null) return;
        IAnimation layer = PlayerAnimationAccess.getPlayerAssociatedData(player).get(LAYER);
        if (layer instanceof ModifierLayer<?> ml) ((ModifierLayer<IAnimation>) ml).setAnimation(new KeyframeAnimationPlayer(anim));
    }
}
