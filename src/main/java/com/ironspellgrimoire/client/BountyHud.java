package com.ironspellgrimoire.client;

import com.ironspellgrimoire.item.WantedPosterItem;
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
 * The bounty, shown like a quest: a tracker in the top-right corner (WANTED, the target, its modifiers, an arrow that
 * turns to point the way, the distance and the time left) and, in the world, a pillar of red light standing on the
 * sighting. Fed by FxPacket shape 67 from server/Bounties. Also keeps the nearest rift for the Rift Compass's needle
 * (shape 68, server/Rifts).
 */
@Mod.EventBusSubscriber(modid = "iron_spell_grimoire", value = Dist.CLIENT)
public final class BountyHud {
    private BountyHud() {}

    private static int boss = -1, mods, state, stars;
    private static double tx, tz;
    private static long deadline, shownAt;
    private static double rx, rz;
    private static long riftUntil;

    public static void set(FxPacket p) {
        Minecraft mc = Minecraft.getInstance();
        long now = mc.level == null ? 0 : mc.level.getGameTime();
        stars = (int) p.a1;
        if (p.sweep <= 0) { boss = -1; return; }
        boss = p.sweep - 1;
        mods = (int) p.h;
        state = (int) p.a0;
        tx = p.c.x + 0.5;
        tz = p.c.z + 0.5;
        deadline = now + p.life;
        shownAt = now;
    }

    public static int stars() { return stars; }

    /** The Rift Compass: where the nearest open rift is, while the compass is held. */
    public static void rift(FxPacket p) {
        Minecraft mc = Minecraft.getInstance();
        long now = mc.level == null ? 0 : mc.level.getGameTime();
        if (p.sweep <= 0) { riftUntil = 0; return; }
        rx = p.c.x; rz = p.c.z; riftUntil = now + p.life;
    }

    /** The nearest rift's position for the compass needle, or null. */
    public static Vec3 riftTarget() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.getGameTime() < riftUntil ? new Vec3(rx, 0, rz) : null;
    }

    private static boolean hunting() { return boss >= 0 && state == Bounties.STATE_HUNT; }

    @SubscribeEvent
    public static void onGui(RenderGuiEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui || boss < 0) return;
        long now = mc.level.getGameTime();
        if (state >= Bounties.STATE_DONE && now - shownAt > 120) { boss = -1; return; }
        double dx = tx - mc.player.getX(), dz = tz - mc.player.getZ();
        tracker(e.getGuiGraphics(), mc.font, e.getGuiGraphics().guiWidth(), (int) Math.sqrt(dx * dx + dz * dz), dx, dz, now, mc.player.getViewYRot(e.getPartialTick()));
    }

    private static void tracker(GuiGraphics g, Font f, int W, int dist, double dx, double dz, long now, float yaw) {
        int w = 168, x = W - w - 4, y = 4;
        String name = Bounties.NAMES[Math.max(0, Math.min(Bounties.NAMES.length - 1, boss))];
        String line1, line2;
        int head;
        boolean hunt = state == Bounties.STATE_HUNT;
        if (state == Bounties.STATE_DONE) { head = 0xFF60FF80; line1 = "SUBJUGATED"; line2 = "★ " + stars + " merit stars"; }
        else if (state == Bounties.STATE_EXPIRED) { head = 0xFF909090; line1 = "The trail went cold"; line2 = "The poster burned up."; }
        else if (state == Bounties.STATE_FOUND) { head = 0xFFFF4040; line1 = "Defeat them!"; line2 = "Time left: " + WantedPosterItem.time(Math.max(0, deadline - now)); }
        else {
            head = 0xFFFF4040;
            line1 = dist + " m " + Bounties.direction(dx, dz);
            line2 = "Time left: " + WantedPosterItem.time(Math.max(0, deadline - now));
        }
        StringBuilder m = new StringBuilder();
        for (int i = 0; i < Bounties.MOD_NAMES.length; i++) if ((mods & (1 << i)) != 0) m.append(m.length() > 0 ? ", " : "").append(Bounties.MOD_NAMES[i]);
        int h = 62;
        g.fill(x, y, x + w, y + h, 0xA0100804);
        g.renderOutline(x, y, w, h, 0xC0E8B84A);
        g.fill(x, y, x + 3, y + h, head);
        g.drawString(f, state >= Bounties.STATE_DONE ? "BOUNTY" : "WANTED", x + 8, y + 4, head, false);
        g.drawString(f, "★ " + stars, x + w - 6 - f.width("★ " + stars), y + 4, 0xFFFFD27A, false);
        g.drawString(f, name, x + 8, y + 15, 0xFFFFC040, false);
        if (m.length() > 0) g.drawString(f, m.toString(), x + 8, y + 26, 0xFFFF8080, false);
        int tx0 = x + 8 + (hunt ? 16 : 0);
        g.drawString(f, line1, tx0, y + 38, 0xFFFFFFFF, false);
        g.drawString(f, line2, x + 8, y + 50, hunt && deadline - now < 12000 ? 0xFFFF6060 : 0xFFB0B0B0, false);
        if (hunt) {   // an arrow that turns to point the way, relative to where you face
            double bearing = Math.toDegrees(Math.atan2(-dx, dz));
            float rel = Mth.wrapDegrees((float) bearing - yaw);
            float pulse = 0.7F + 0.3F * (float) Math.sin(now * 0.3);
            int col = 0xFF000000 | ((int) (255 * pulse) << 16) | ((int) (70 * pulse) << 8) | (int) (60 * pulse);
            g.pose().pushPose();
            g.pose().translate(x + 13, y + 41.5, 0);
            g.pose().mulPose(Axis.ZP.rotationDegrees(rel));
            g.fill(-1, -2, 1, 5, col);                       // the shaft
            for (int i = 0; i < 4; i++) g.fill(-i, -5 + i, i + 1, -4 + i, col);   // the head, pointing up = ahead
            g.pose().popPose();
        }
    }

    // ------------------------------------------------------------------ the pillar in the world (drawn from FxRenderer's glow pass)
    static void drawBeacon(Matrix4f m, Vec3 cam, double now) {
        Minecraft mc = Minecraft.getInstance();
        if (!hunting() || mc.player == null || mc.level == null) return;
        double dx = tx - cam.x, dz = tz - cam.z, d = Math.sqrt(dx * dx + dz * dz);
        if (d > 480 || d < 6) return;
        double ground = mc.player.getY();
        int bx = Mth.floor(tx), bz = Mth.floor(tz);
        if (mc.level.hasChunk(bx >> 4, bz >> 4)) ground = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
        float w = (float) Math.max(0.5, d * 0.01);
        float a = (float) (0.55 + 0.25 * Math.sin(now * 0.15)) * (float) Mth.clamp((d - 6) / 20.0, 0, 1);
        Vec3 bot = new Vec3(tx, ground, tz), top = new Vec3(tx, ground + 120, tz);
        FxBase.gline(m, cam, bot, top, w, 18, a);
        FxBase.gline(m, cam, bot, bot.add(0, 6, 0), w * 2.2F, 18, a * 0.6F);
    }
}
