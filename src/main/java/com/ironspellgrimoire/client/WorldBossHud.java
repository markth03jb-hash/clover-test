package com.ironspellgrimoire.client;

import com.ironspellgrimoire.network.FxPacket;
import com.ironspellgrimoire.server.Bounties;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * The world boss tracker (server/WorldBoss, shape 78): while the Cinder Sovereign is out, a red box at the top right
 * (under the bounty and quest trackers) - how far and which way, an arrow that turns as you do, and how long it waits -
 * and a red pillar of fire over the place.
 */
@Mod.EventBusSubscriber(modid = "iron_spell_grimoire", value = Dist.CLIENT)
public final class WorldBossHud {
    private WorldBossHud() {}

    private static boolean on, risen;
    private static double tx, tz;
    private static long until;
    /** Closed with its own key (TrackerMode.BOSS_KEY); a new world boss opens it again. */
    static boolean closed;

    public static void set(FxPacket p) {
        Minecraft mc = Minecraft.getInstance();
        boolean was = on;
        on = p.life > 0;
        if (!on) return;
        if (!was) closed = false;   // a new boss: show it again
        tx = p.c.x; tz = p.c.z; risen = p.sweep == 1;
        until = (mc.level == null ? 0 : mc.level.getGameTime()) + (long) p.r;
    }

    private static boolean here() {
        Minecraft mc = Minecraft.getInstance();
        return on && mc.player != null && mc.level != null && mc.level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD);
    }

    /** Whether its box is on screen now (TrackerMode stacks the rift map under it). */
    public static boolean shown() {
        Minecraft mc = Minecraft.getInstance();
        if (!here() || TrackerMode.hidden() || closed) return false;
        double dx = tx - mc.player.getX(), dz = tz - mc.player.getZ();
        return !(risen && dx * dx + dz * dz < 40 * 40);
    }

    @SubscribeEvent
    public static void onGui(RenderGuiEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (!here() || mc.options.hideGui || closed) return;
        long now = mc.level.getGameTime();
        GuiGraphics g = e.getGuiGraphics();
        Font f = mc.font;
        int w = 168, h = 40, x = g.guiWidth() - w - 4, y = TrackerMode.worldY();
        double dx = tx - mc.player.getX(), dz = tz - mc.player.getZ(), d = Math.sqrt(dx * dx + dz * dz);
        if (d < 40 && risen) return;   // you're there: its boss bar says the rest
        if (TrackerMode.hidden()) return;
        if (TrackerMode.compact()) { TrackerMode.line(g, f, y, 0xFFFF5A2A, "WORLD BOSS", (int) d + " m", dx, dz, true, now, mc.player.getViewYRot(e.getPartialTick())); return; }
        int head = 0xFFFF5A2A;
        g.fill(x, y, x + w, y + h, 0xA0180806);
        g.renderOutline(x, y, w, h, 0xC0D84A2A);
        g.fill(x, y, x + 3, y + h, head);
        g.drawString(f, "WORLD BOSS", x + 8, y + 4, head, false);
        String t = "Cinder Sovereign";
        g.drawString(f, t, x + w - 6 - f.width(t), y + 4, 0xFFFFFFFF, false);
        g.drawString(f, (int) d + " m " + Bounties.direction(dx, dz), x + 24, y + 16, 0xFFFFFFFF, false);
        long left = Math.max(0, until - now);
        g.drawString(f, (risen ? "Risen" : "Rising") + " - waits " + Math.max(1, left / 1200) + " min", x + 8, y + 28, 0xFFE0B0A0, false);
        double bearing = Math.toDegrees(Math.atan2(-dx, dz));
        float rel = Mth.wrapDegrees((float) bearing - mc.player.getViewYRot(e.getPartialTick()));
        float pulse = 0.7F + 0.3F * (float) Math.sin(now * 0.3);
        int col = 0xFF000000 | ((int) (255 * pulse) << 16) | ((int) (110 * pulse) << 8) | (int) (50 * pulse);
        g.pose().pushPose();
        g.pose().translate(x + 13, y + 19.5, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(rel));
        g.fill(-1, -2, 1, 5, col);
        for (int i = 0; i < 4; i++) g.fill(-i, -5 + i, i + 1, -4 + i, col);
        g.pose().popPose();
    }

    /** The red pillar over the place (from FxRenderer's glow pass). */
    static void drawBeacon(Matrix4f m, Vec3 cam, double now) {
        Minecraft mc = Minecraft.getInstance();
        if (!here()) return;
        double dx = tx - cam.x, dz = tz - cam.z, d = Math.sqrt(dx * dx + dz * dz);
        if (d > 700 || d < 24) return;
        double ground = mc.player.getY();
        int bx = Mth.floor(tx), bz = Mth.floor(tz);
        if (mc.level.hasChunk(bx >> 4, bz >> 4)) ground = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
        float w = (float) Math.max(0.8, d * 0.012);
        float a = (float) (0.6 + 0.25 * Math.sin(now * 0.12)) * (float) Mth.clamp((d - 24) / 30.0, 0, 1);
        Vec3 bot = new Vec3(tx, ground, tz), top = new Vec3(tx, ground + 160, tz);
        FxBase.gline(m, cam, bot, top, w, 2, a);
        FxBase.gline(m, cam, bot, bot.add(0, 10, 0), w * 2.5F, 22, a * 0.7F);
    }
}
