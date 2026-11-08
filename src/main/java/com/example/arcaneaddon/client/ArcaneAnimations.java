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
    };

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent e) {
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER, PRIORITY, player -> new ModifierLayer<>());
        // Demon Dweller Sword glows in the colour of the element it last borrowed (1 fire, 2 ice, 3 lightning, 4 wind)
        e.enqueueWork(() -> net.minecraft.client.renderer.item.ItemProperties.register(
                com.example.arcaneaddon.registry.AddonItemRegistry.DEMON_DWELLER_SWORD.get(),
                ResourceLocation.fromNamespaceAndPath("arcane_addon", "element"),
                (stack, level, entity, seed) -> stack.hasTag() ? (float) stack.getTag().getInt(com.example.arcaneaddon.item.DemonDwellerSwordItem.GLOW) : 0.0F));
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
