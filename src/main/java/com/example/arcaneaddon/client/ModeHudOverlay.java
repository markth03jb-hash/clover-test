package com.example.arcaneaddon.client;

import com.example.arcaneaddon.network.ModeIds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mode timers, like vanilla effect timers: one panel per active mode in the top-right corner (under the vanilla
 * effect icons) with the spell's icon, the mode's name, the time left and a draining bar. Blinks in the last 10 s.
 */
@EventBusSubscriber(modid = "arcane_addon", value = {Dist.CLIENT})
public final class ModeHudOverlay {
    private ModeHudOverlay() {}

    /** mode index -> {start, end, total} in game ticks. */
    private static final Map<Integer, double[]> ACTIVE = new LinkedHashMap<>();

    public static void set(int mode, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mode < 0 || mode >= ModeIds.MODES.length) return;
        double now = mc.level.getGameTime();
        if (ticks <= 0) { ACTIVE.remove(mode); return; }
        double[] f = ACTIVE.get(mode);
        if (f == null || now > f[1]) ACTIVE.put(mode, new double[]{now, now + ticks, ticks});
        else { f[1] = now + ticks; f[2] = Math.max(f[2], f[1] - f[0]); }   // a refresh keeps the bar's full length
    }

    @SubscribeEvent
    public static void onGui(RenderGuiEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) { ACTIVE.clear(); return; }
        if (ACTIVE.isEmpty() || mc.options.hideGui) return;
        double now = mc.level.getGameTime() + e.getPartialTick();
        ACTIVE.values().removeIf(f -> now >= f[1]);
        if (ACTIVE.isEmpty()) return;
        GuiGraphics g = e.getGuiGraphics();
        Font font = mc.font;
        // start below the vanilla effect icons (good row at y 1, bad row at y 27)
        boolean good = false, bad = false;
        for (MobEffectInstance mi : mc.player.getActiveEffects()) {
            if (!mi.showIcon()) continue;
            if (mi.getEffect().isBeneficial()) good = true; else bad = true;
        }
        int y = 2 + (good ? 26 : 0) + (bad ? 26 : 0);
        int right = g.guiWidth() - 2;
        for (Map.Entry<Integer, double[]> en : ACTIVE.entrySet()) {
            String id = ModeIds.MODES[en.getKey()];
            double[] f = en.getValue();
            double left = f[1] - now;
            Component name = Component.translatable("spell.arcane_addon." + id);
            int secs = (int) Math.ceil(left / 20.0);
            String time = String.format("%d:%02d", secs / 60, secs % 60);
            int w = Math.max(96, Math.max(font.width(name), font.width(time)) + 28);
            int x = right - w;
            boolean blink = left < 200 && ((int) (now / 4) % 2 == 0);   // last 10 s, like vanilla
            int accent = accent(id);
            g.fill(x, y, right, y + 24, 0xB0100A16);
            g.fill(x, y, right, y + 1, accent);
            g.fill(x, y + 23, right, y + 24, 0x60000000);
            g.fill(x, y, x + 1, y + 24, accent);
            float frac = (float) Mth.clamp(left / Math.max(1.0, f[2]), 0.0, 1.0);
            g.fill(x + 1, y + 21, x + 1 + (int) ((w - 2) * frac), y + 23, accent);
            if (!blink) {
                ResourceLocation icon = new ResourceLocation("arcane_addon", "textures/gui/spell_icons/" + id + ".png");
                g.blit(icon, x + 4, y + 3, 0, 0, 16, 16, 16, 16);
            }
            g.drawString(font, name, x + 24, y + 3, 0xFFFFFF, true);
            g.drawString(font, time, x + 24, y + 12, blink ? 0xFF5555 : 0xBBBBBB, true);
            y += 26;
        }
    }

    private static int accent(String id) {
        return switch (id) {
            case "devil_union" -> 0xFFB01020;
            case "mana_zone_dimension_slash", "black_blade", "black_cocoon", "ki_foresight" -> 0xFF8A4DE0;
            case "knights_armor" -> 0xFFB8C4D0;
            case "spirit_dive", "hushed_dance" -> 0xFF6BE09A;
            case "silver_guardian", "silver_mirror" -> 0xFFD8E2EE;
            case "flame_knuckles", "soul_chain" -> 0xFFFF8A2A;
            case "thunder_god_mode", "thunder_gloves" -> 0xFF4FB8FF;
            case "mana_skin", "lion_mana_zone", "hellfire_incarnate" -> 0xFFE0302A;
            default -> 0xFFE8DCC0;   // bone
        };
    }
}
