package com.ironspellgrimoire.client;

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
@Mod.EventBusSubscriber(modid = "iron_spell_grimoire", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ArcaneAnimations {
    private static final ResourceLocation LAYER = ResourceLocation.fromNamespaceAndPath("iron_spell_grimoire", "moves");
    /** Above Iron's cast layer, so a mid-move pose replaces the cast pose while it plays. */
    private static final int PRIORITY = 2000;

    /** Index -> animation id. Must match {@code com.ironspellgrimoire.server.AnimSend}. */
    private static final ResourceLocation[] POSES = {
            ResourceLocation.fromNamespaceAndPath("iron_spell_grimoire", "twin_whirlwind"),
            ResourceLocation.fromNamespaceAndPath("iron_spell_grimoire", "asta_cross_cut"),
            ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "simple_sword_lunge_stab"),
            ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "sword_deflect_1"),
            ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "horizontal_slash_one_handed"),
            // Surpassing Limits awakenings, one per grimoire (AnimSend.LIMIT_FIRST + book index)
            ResourceLocation.fromNamespaceAndPath("iron_spell_grimoire", "limit_black"),
            ResourceLocation.fromNamespaceAndPath("iron_spell_grimoire", "limit_dark"),
            ResourceLocation.fromNamespaceAndPath("iron_spell_grimoire", "limit_wind"),
            ResourceLocation.fromNamespaceAndPath("iron_spell_grimoire", "limit_mercury"),
            ResourceLocation.fromNamespaceAndPath("iron_spell_grimoire", "limit_fire"),
            ResourceLocation.fromNamespaceAndPath("iron_spell_grimoire", "limit_lightning"),
            ResourceLocation.fromNamespaceAndPath("iron_spell_grimoire", "limit_hellfire"),
            ResourceLocation.fromNamespaceAndPath("iron_spell_grimoire", "limit_bone"),
            ResourceLocation.fromNamespaceAndPath("iron_spell_grimoire", "limit_steel"),
            ResourceLocation.fromNamespaceAndPath("iron_spell_grimoire", "fire_magna_special"),   // 14: Magna's swing
            ResourceLocation.fromNamespaceAndPath("iron_spell_grimoire", "dark_equinox"),         // 15: Yami's Equinox
            ResourceLocation.fromNamespaceAndPath("iron_spell_grimoire", "limit_time"),           // 16: Julius's awakening
            ResourceLocation.fromNamespaceAndPath("iron_spell_grimoire", "ripper_horizon"),   // 17: low coil then a rising cut (Jack's Ripper's Horizon)
    };

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent e) {
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER, PRIORITY, player -> new ModifierLayer<>());
        // idle stances and melee attacks (Yami with his katana, Luck in Thunder God Mode) - below every cast pose
        // Demon Dweller Sword glows in the colour of the element it last borrowed (1 fire, 2 ice, 3 lightning, 4 wind),
        // and "glow" (1 -> 0) lets the glow fade out over its last 1.5 s instead of switching off
        e.enqueueWork(() -> {
            var sword = com.ironspellgrimoire.registry.AddonItemRegistry.DEMON_DWELLER_SWORD.get();
            net.minecraft.client.renderer.item.ItemProperties.register(sword, ResourceLocation.fromNamespaceAndPath("iron_spell_grimoire", "element"),
                    (stack, level, entity, seed) -> glowLeft(stack, level, entity) > 0 ? 0.2F * stack.getTag().getInt(com.ironspellgrimoire.item.DemonDwellerSwordItem.GLOW) : 0.0F);   // property values are clamped to 0..1, so element n is 0.2*n
            net.minecraft.client.renderer.item.ItemProperties.register(sword, ResourceLocation.fromNamespaceAndPath("iron_spell_grimoire", "glow"),
                    (stack, level, entity, seed) -> Math.min(1.0F, glowLeft(stack, level, entity) / (float) com.ironspellgrimoire.item.DemonDwellerSwordItem.FADE_TICKS));
            // the Rift Compass: its needle points at the nearest open rift (spinning when there is none), and it glows brighter within 64 blocks
            var compass = com.ironspellgrimoire.registry.AddonItemRegistry.RIFT_COMPASS.get();
            net.minecraft.client.renderer.item.ItemProperties.register(compass, ResourceLocation.fromNamespaceAndPath("iron_spell_grimoire", "angle"), (stack, level, entity, seed) -> {
                net.minecraft.world.entity.Entity holder = entity != null ? entity : stack.getEntityRepresentation();
                net.minecraft.world.phys.Vec3 t = BountyHud.riftTarget();
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                if (t == null || holder == null) return mc.level == null ? 0F : (float) ((mc.level.getGameTime() % 40) / 40.0);
                double bearing = Math.toDegrees(Math.atan2(-(t.x - holder.getX()), t.z - holder.getZ()));
                float rel = net.minecraft.util.Mth.wrapDegrees((float) bearing - holder.getYRot());
                return (float) (((rel / 360.0) % 1.0 + 1.0) % 1.0);
            });
            net.minecraft.client.renderer.item.ItemProperties.register(compass, ResourceLocation.fromNamespaceAndPath("iron_spell_grimoire", "near"), (stack, level, entity, seed) -> {
                net.minecraft.world.phys.Vec3 t = BountyHud.riftTarget();
                net.minecraft.world.entity.Entity holder = entity != null ? entity : stack.getEntityRepresentation();
                return t != null && holder != null && Math.hypot(t.x - holder.getX(), t.z - holder.getZ()) < 64 ? 1F : 0F;
            });
        });
    }

    /** Ticks of element glow left on a Dweller sword (0 = none). */
    private static long glowLeft(net.minecraft.world.item.ItemStack stack, net.minecraft.client.multiplayer.ClientLevel level, net.minecraft.world.entity.LivingEntity entity) {
        if (!stack.hasTag() || !stack.getTag().contains(com.ironspellgrimoire.item.DemonDwellerSwordItem.GLOW)) return 0;
        net.minecraft.world.level.Level lv = level != null ? level : entity != null ? entity.level() : Minecraft.getInstance().level;
        if (lv == null) return 0;
        return Math.max(0, stack.getTag().getLong(com.ironspellgrimoire.item.DemonDwellerSwordItem.GLOW_UNTIL) - lv.getGameTime());
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
        KeyframeAnimationPlayer kp = new KeyframeAnimationPlayer(anim);
        if (pose == 17) {   // the big green slash: in first person you see your own arms do it, not the hand at rest
            kp.setFirstPersonMode(dev.kosmx.playerAnim.api.firstPerson.FirstPersonMode.THIRD_PERSON_MODEL);
            kp.setFirstPersonConfiguration(new dev.kosmx.playerAnim.api.firstPerson.FirstPersonConfiguration(true, true, true, true));
        }
        if (layer instanceof ModifierLayer<?> ml) ((ModifierLayer<IAnimation>) ml).setAnimation(kp);
    }
}
