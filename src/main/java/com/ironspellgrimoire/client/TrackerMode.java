package com.ironspellgrimoire.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.math.Axis;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * The trackers at the top right (bounty, quest, world boss) - full, compact (one line each) or hidden, cycled with a
 * key (default H, under Controls - Iron Spell Grimoire). They stack in that order and never overlap: each asks here
 * where to start.
 */
public final class TrackerMode {
    private TrackerMode() {}

    public static final KeyMapping KEY = new KeyMapping("key.iron_spell_grimoire.trackers", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, "key.categories.iron_spell_grimoire");
    private static int mode;   // 0 full, 1 compact, 2 hidden

    public static boolean hidden() { return mode == 2; }
    public static boolean compact() { return mode == 1; }

    /** Where each tracker starts: the bounty at the top, the quest under it, the world boss under both. */
    public static int questY() { return 4 + (BountyHud.shown() ? (compact() ? 16 : 66) : 0); }
    public static int worldY() { return questY() + (QuestHud.shown() ? (compact() ? 16 : QuestHud.height() + 4) : 0); }

    /** One compact line: a coloured edge, a label, a name, and the way to go (an arrow that turns as you do). */
    static void line(GuiGraphics g, Font f, int y, int head, String label, String text, double dx, double dz, boolean arrow, long now, float yaw) {
        int w = 168, x = g.guiWidth() - w - 4;
        g.fill(x, y, x + w, y + 13, 0xA0080808);
        g.fill(x, y, x + 3, y + 13, head);
        g.drawString(f, label, x + 6, y + 3, head, false);
        String t = text;
        int room = w - 10 - f.width(label) - 18;
        if (f.width(t) > room) t = f.plainSubstrByWidth(t, room - 6) + "..";
        g.drawString(f, t, x + w - 6 - f.width(t) - (arrow ? 12 : 0), y + 3, 0xFFFFFFFF, false);
        if (arrow) {
            float rel = Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(-dx, dz)) - yaw);
            g.pose().pushPose();
            g.pose().translate(x + w - 9, y + 6.5, 0);
            g.pose().mulPose(Axis.ZP.rotationDegrees(rel));
            g.fill(-1, -1, 1, 4, head);
            for (int i = 0; i < 3; i++) g.fill(-i, -4 + i, i + 1, -3 + i, head);
            g.pose().popPose();
        }
    }

    @Mod.EventBusSubscriber(modid = "iron_spell_grimoire", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Keys {
        @SubscribeEvent public static void register(RegisterKeyMappingsEvent e) { e.register(KEY); }
    }

    @Mod.EventBusSubscriber(modid = "iron_spell_grimoire", value = Dist.CLIENT)
    public static final class Ticks {
        @SubscribeEvent
        public static void onTick(TickEvent.ClientTickEvent e) {
            if (e.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getInstance();
            while (KEY.consumeClick()) {
                mode = (mode + 1) % 3;
                if (mc.player != null) mc.player.displayClientMessage(Component.literal("Trackers: " + (mode == 0 ? "full" : mode == 1 ? "compact" : "hidden")).withStyle(ChatFormatting.GRAY), true);
            }
        }
    }
}
