package com.example.arcaneaddon.client;

import com.example.arcaneaddon.registry.AddonItemRegistry;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier;
import dev.kosmx.playerAnim.core.util.Ease;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

/**
 * Character stances, on their own animation layer under every cast pose:
 *  - Yami with the Dark Katana in his main hand stands like a samurai (two-handed chudan guard, slow breathing), and
 *    his attacks are samurai cuts in turn - a diagonal kesa-giri, a horizontal cut, a rising cut.
 *  - Luck in Thunder God Mode bounces on his toes in a boxer's guard, and attacks with a jab, a hook, and a high kick.
 * The idle loops while nothing else plays; an attack (any arm swing) cuts in and then flows back into the stance.
 * Legs are left to the normal walk so stances work while moving (only Luck's kick uses a leg).
 */
@Mod.EventBusSubscriber(modid = "arcane_addon", value = Dist.CLIENT)
public final class StanceAnimations {
    private StanceAnimations() {}

    public static final ResourceLocation LAYER = ResourceLocation.fromNamespaceAndPath("arcane_addon", "stance");
    public static final int PRIORITY = 10;    // UNDER Iron's cast layer (42) and the addon's move layer, so spell poses always win

    private enum Style { NONE, SAMURAI, THUNDER }

    private static final class State { Style style = Style.NONE; boolean wasSwinging; int combo; boolean attacking; boolean walking; int still; }
    private static final Map<Integer, State> STATES = new HashMap<>();

    private static ResourceLocation id(String n) { return ResourceLocation.fromNamespaceAndPath("arcane_addon", n); }

    private static Style styleOf(Player p, double now) {
        if (FxRenderer.inThunderMode(p.getId(), now)) return Style.THUNDER;
        if (p.getMainHandItem().is(AddonItemRegistry.DARK_KATANA.get())) return Style.SAMURAI;
        return Style.NONE;
    }

    @SubscribeEvent
    @SuppressWarnings("unchecked")
    public static void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { STATES.clear(); return; }
        double now = mc.level.getGameTime();
        for (Player pl : mc.level.players()) {
            if (!(pl instanceof AbstractClientPlayer p)) continue;
            State st = STATES.computeIfAbsent(p.getId(), k -> new State());
            Style style = styleOf(p, now);
            IAnimation raw = PlayerAnimationAccess.getPlayerAssociatedData(p).get(LAYER);
            if (!(raw instanceof ModifierLayer<?> ml0)) continue;
            ModifierLayer<IAnimation> layer = (ModifierLayer<IAnimation>) ml0;
            boolean swingStart = p.swinging && !st.wasSwinging;
            st.wasSwinging = p.swinging;
            if (style == Style.NONE) {
                if (st.style != Style.NONE) {   // ease back to the normal pose
                    layer.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(5, Ease.INOUTSINE), null);
                    st.style = Style.NONE; st.attacking = false;
                }
                continue;
            }
            boolean changed = style != st.style;
            st.style = style;
            IAnimation cur = layer.getAnimation();
            boolean active = cur != null && cur.isActive();
            // moving: the stance steps aside so the arms swing with the normal walk (no sword glued in place),
            // and comes back once you have stood still for a moment
            double dx = p.getX() - p.xo, dz = p.getZ() - p.zo;
            boolean moving = dx * dx + dz * dz > 0.0004;
            st.still = moving ? 0 : st.still + 1;
            if (!swingStart && !st.attacking) {
                if (moving && !st.walking) {
                    st.walking = true;
                    layer.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(4, Ease.INOUTSINE), null);
                    continue;
                }
                if (st.walking) {
                    if (st.still < 4) continue;
                    st.walking = false;
                    play(layer, id(style == Style.SAMURAI ? "samurai_idle" : "thunder_idle"), 5);
                    continue;
                }
            }
            if (swingStart) {   // an attack: the next cut / blow in the combo
                String[] combo = style == Style.SAMURAI
                        ? new String[]{"samurai_kesa", "samurai_yoko", "samurai_kiriage"}
                        : new String[]{"thunder_jab", "thunder_hook", "thunder_high_kick"};
                if (!st.attacking || !active) st.combo = 0;
                play(layer, id(combo[st.combo % combo.length]), 2);
                st.combo++;
                st.attacking = true;
                st.walking = false;
            } else if (changed || !active) {   // into the stance (it loops by itself from then on)
                st.attacking = false;
                play(layer, id(style == Style.SAMURAI ? "samurai_idle" : "thunder_idle"), changed ? 5 : 3);
            } else if (st.attacking && cur instanceof KeyframeAnimationPlayer kp && kp.getTick() >= kp.getData().endTick - 3) {
                // an attack is finishing: cross-fade into the stance before it ends, so there is never a frame without a pose
                st.attacking = false;
                play(layer, id(style == Style.SAMURAI ? "samurai_idle" : "thunder_idle"), 3);
            }
        }
        STATES.keySet().removeIf(k -> mc.level.getEntity(k) == null);
    }

    /** Play an animation, blending in from whatever was showing over {@code fade} ticks (0 = cut straight in). */
    private static void play(ModifierLayer<IAnimation> layer, ResourceLocation anim, int fade) {
        KeyframeAnimation a = PlayerAnimationRegistry.getAnimation(anim);
        if (a == null) return;
        if (fade > 0) layer.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(fade, Ease.INOUTSINE), new KeyframeAnimationPlayer(a));
        else layer.setAnimation(new KeyframeAnimationPlayer(a));
    }
}
