package com.ironspellgrimoire.client;

import com.ironspellgrimoire.server.Bounties;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The First Steps tracker (server/FirstSteps): a blue box at the top right, under the other trackers, with the three
 * things a new mage does - read the Codex, find the tower (how far, which way, an arrow that turns as you do), be
 * chosen at the altar. Done steps are ticked and greyed. It goes away once a grimoire chooses you.
 */
@Mod.EventBusSubscriber(modid = "iron_spell_grimoire", value = Dist.CLIENT)
public final class FirstStepsHud {
    private FirstStepsHud() {}

    public static final int H = 50;
    private static final int HEAD = 0xFF6FA8FF, DONE = 0xFF7A8A9A;
    private static int flags, tx, tz;

    public static void set(int f, int x, int z) { flags = f; tx = x; tz = z; }

    /** Test hook (UiShots): show it with these flags and the tower this far ahead of the player. */
    public static void force(int f, int dx, int dz) {
        Minecraft mc = Minecraft.getInstance();
        set(f, mc.player == null ? dx : (int) mc.player.getX() + dx, mc.player == null ? dz : (int) mc.player.getZ() + dz);
    }

    public static boolean shown() {
        Minecraft mc = Minecraft.getInstance();
        return (flags & 1) != 0 && mc.player != null && !TrackerMode.hidden();
    }

    @SubscribeEvent
    public static void onGui(RenderGuiEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (!shown() || mc.options.hideGui || mc.level == null) return;
        GuiGraphics g = e.getGuiGraphics();
        Font f = mc.font;
        long now = mc.level.getGameTime();
        float yaw = mc.player.getViewYRot(e.getPartialTick());
        boolean codex = (flags & 2) != 0, tower = (flags & 4) != 0, known = (flags & 8) != 0;
        boolean overworld = mc.level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD);
        double dx = tx + 0.5 - mc.player.getX(), dz = tz + 0.5 - mc.player.getZ(), d = Math.sqrt(dx * dx + dz * dz);
        String towerLine = tower ? "Reached the Grimoire Tower" : !known ? "Find a Grimoire Tower" : !overworld ? "Tower: back in the Overworld" : "Tower: " + (int) d + " m " + Bounties.direction(dx, dz);
        boolean arrow = !tower && known && overworld;
        int y = TrackerMode.stepsY();
        if (TrackerMode.compact()) {
            String t = !codex ? "Read the Codex" : !tower ? (arrow ? (int) d + " m" : "Find a tower") : "Visit the altar";
            TrackerMode.line(g, f, y, HEAD, "FIRST STEPS", t, dx, dz, arrow && codex, now, yaw);
            return;
        }
        int w = 168, x = g.guiWidth() - w - 4;
        g.fill(x, y, x + w, y + H, 0xA0081020);
        g.renderOutline(x, y, w, H, 0xC04A70B0);
        g.fill(x, y, x + 3, y + H, HEAD);
        g.drawString(f, "FIRST STEPS", x + 8, y + 4, HEAD, false);
        row(g, f, x, y + 15, codex, "Read your Grimoire Codex");
        row(g, f, x, y + 26, tower, towerLine);
        row(g, f, x, y + 37, false, "Be chosen at the altar on top");
        if (arrow) {
            float rel = Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(-dx, dz)) - yaw);
            float pulse = 0.75F + 0.25F * (float) Math.sin(now * 0.3);
            int col = 0xFF000000 | ((int) (0x6F * pulse) << 16) | ((int) (0xA8 * pulse) << 8) | (int) (0xFF * pulse);
            g.pose().pushPose();
            g.pose().translate(x + w - 10, y + 29.5, 0);
            g.pose().mulPose(Axis.ZP.rotationDegrees(rel));
            g.fill(-1, -1, 1, 4, col);
            for (int i = 0; i < 3; i++) g.fill(-i, -4 + i, i + 1, -3 + i, col);
            g.pose().popPose();
        }
    }

    private static void row(GuiGraphics g, Font f, int x, int y, boolean done, String text) {
        int room = 168 - 22 - 16;
        String t = f.width(text) > room ? f.plainSubstrByWidth(text, room - 6) + ".." : text;
        if (done) {   // a tick
            g.fill(x + 9, y + 4, x + 10, y + 6, 0xFF60D080); g.fill(x + 10, y + 5, x + 11, y + 7, 0xFF60D080);
            g.fill(x + 11, y + 2, x + 12, y + 6, 0xFF60D080); g.fill(x + 12, y + 1, x + 13, y + 3, 0xFF60D080);
        } else g.fill(x + 10, y + 3, x + 13, y + 6, HEAD);
        g.drawString(f, t, x + 18, y, done ? DONE : 0xFFFFFFFF, false);
    }
}
