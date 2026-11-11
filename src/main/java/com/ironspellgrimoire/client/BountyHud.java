package com.ironspellgrimoire.client;

import com.ironspellgrimoire.item.WantedPosterItem;
import com.ironspellgrimoire.network.FxPacket;
import com.ironspellgrimoire.server.Bounties;
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
 * The bounty, shown like a quest:
 * <ul>
 *   <li>a compass bar across the top of the screen - N/E/S/W sliding as you turn, a red mark where the boss was sighted
 *       (an arrow at the edge when it's behind you) and the distance under it;</li>
 *   <li>a tracker in the top-right corner: WANTED, the boss, its modifiers, what to do now, and the time left;</li>
 *   <li>in the world, a pillar of red light standing on the sighting, visible from a few hundred blocks.</li>
 * </ul>
 * Fed by FxPacket shape 67 from server/Bounties (id, sweep = boss + 1 or 0 for none, h = modifiers, a0 = state, c = x/z,
 * life = ticks left, a1 = merit stars).
 */
@Mod.EventBusSubscriber(modid = "iron_spell_grimoire", value = Dist.CLIENT)
public final class BountyHud {
    private BountyHud() {}

    private static int boss = -1, mods, state, stars;
    private static double tx, tz;
    private static long deadline, shownAt;
    private static double rx, rz;
    private static int riftRank;
    private static long riftUntil;
    private static double gx, gz;
    private static long raceUntil;

    /** A rival race's finish (FxPacket shape 69): a gold mark on the compass bar. */
    public static void race(FxPacket p) {
        Minecraft mc = Minecraft.getInstance();
        long now = mc.level == null ? 0 : mc.level.getGameTime();
        gx = p.c.x; gz = p.c.z; raceUntil = p.life > 0 ? now + p.life : 0;
    }

    /** The Rift Compass (FxPacket shape 68): the nearest open rift, while it's held. */
    public static void rift(FxPacket p) {
        Minecraft mc = Minecraft.getInstance();
        long now = mc.level == null ? 0 : mc.level.getGameTime();
        if (p.sweep <= 0) {
            riftUntil = 0;
            if (mc.player != null && now % 40 < 10) mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal("The compass needle spins - no rift is open nearby."), true);
            return;
        }
        rx = p.c.x; rz = p.c.z; riftRank = p.sweep; riftUntil = now + p.life;
    }

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

    private static boolean hunting() { return boss >= 0 && state == Bounties.STATE_HUNT; }

    // ------------------------------------------------------------------ the screen
    @SubscribeEvent
    public static void onGui(RenderGuiEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui) return;
        long now = mc.level.getGameTime();
        GuiGraphics g = e.getGuiGraphics();
        Font f = mc.font;
        int W = g.guiWidth();
        float yaw = mc.player.getViewYRot(e.getPartialTick());
        boolean rifting = now < riftUntil, racing = now < raceUntil;
        if (boss >= 0 && state >= Bounties.STATE_DONE && now - shownAt > 120) boss = -1;
        if (hunting() || rifting || racing) compassBar(g, f, W, yaw, now);
        if (racing) mark(g, f, W, yaw, gx - mc.player.getX(), gz - mc.player.getZ(), now, 0xFFD040, (hunting() ? 9 : 0) + (rifting ? 9 : 0));
        if (hunting()) {
            double dx = tx - mc.player.getX(), dz = tz - mc.player.getZ();
            mark(g, f, W, yaw, dx, dz, now, 0xFF2020, 0);
        }
        if (rifting) {
            double dx = rx - mc.player.getX(), dz = rz - mc.player.getZ();
            mark(g, f, W, yaw, dx, dz, now, 0xB060FF, hunting() ? 9 : 0);
        }
        if (boss < 0) return;
        double dx = tx - mc.player.getX(), dz = tz - mc.player.getZ();
        int dist = (int) Math.sqrt(dx * dx + dz * dz);
        tracker(g, f, W, dist, dx, dz, now);
    }

    private static final int BW = 200, BY = 3, BH = 13;
    private static final double SPAN = 100.0;   // degrees either side shown

    /** The compass bar itself: N/E/S/W sliding as you turn (Minecraft yaw 0 = south, 90 = west, 180 = north). */
    private static void compassBar(GuiGraphics g, Font f, int W, float yaw, long now) {
        int x0 = (W - BW) / 2;
        g.fill(x0 - 1, BY - 1, x0 + BW + 1, BY + BH + 1, 0x90000000);
        g.fill(x0, BY, x0 + BW, BY + 1, 0x60E8B84A);
        g.fill(x0, BY + BH - 1, x0 + BW, BY + BH, 0x60E8B84A);
        int cx = x0 + BW / 2;
        String[] names = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
        for (int deg = 0; deg < 360; deg += 15) {
            double rel = Mth.wrapDegrees(deg - yaw);
            if (Math.abs(rel) > SPAN) continue;
            int x = cx + (int) (rel / SPAN * (BW / 2.0 - 4));
            if (deg % 45 == 0) {
                String s = names[deg / 45];
                g.drawCenteredString(f, s, x, BY + 3, s.equals("N") ? 0xFFFF6060 : s.length() == 1 ? 0xFFFFFFFF : 0xFFB0B0B0);
            } else g.fill(x, BY + 4, x + 1, BY + BH - 4, 0x80FFFFFF);
        }
        g.fill(cx, BY - 2, cx + 1, BY + 1, 0xFFFFFFFF);   // where you face
    }

    /** A target on the bar: a pointer under it (or an arrow at the edge when it's behind you) and the distance. */
    private static void mark(GuiGraphics g, Font f, int W, float yaw, double dx, double dz, long now, int rgb, int lower) {
        int cx = W / 2;
        int dist = (int) Math.sqrt(dx * dx + dz * dz);
        double bearing = Math.toDegrees(Math.atan2(-dx, dz));
        double rel = Mth.wrapDegrees(bearing - yaw);
        boolean behind = Math.abs(rel) > SPAN;
        int mx = cx + (int) (Mth.clamp(rel, -SPAN, SPAN) / SPAN * (BW / 2.0 - 4));
        float pulse = 0.6F + 0.4F * (float) Math.sin(now * 0.3);
        int r = (int) (((rgb >> 16) & 255) * (0.7F + 0.3F * pulse)), gg = (int) (((rgb >> 8) & 255) * (0.7F + 0.3F * pulse)), b = (int) ((rgb & 255) * (0.7F + 0.3F * pulse));
        int col = 0xFF000000 | r << 16 | gg << 8 | b;
        if (behind) g.drawCenteredString(f, rel < 0 ? "<" : ">", mx, BY + 3, col);
        else {
            for (int i = 0; i <= 4; i++) g.fill(mx - (4 - i), BY + BH + 1 + i, mx + (4 - i) + 1, BY + BH + 2 + i, col);
            g.fill(mx - 1, BY + 1, mx + 2, BY + BH - 1, col);
        }
        g.drawCenteredString(f, dist + " m", mx, BY + BH + 7 + lower, col);
    }

    private static void tracker(GuiGraphics g, Font f, int W, int dist, double dx, double dz, long now) {
        int w = 156, x = W - w - 4, y = 4;
        String name = Bounties.NAMES[Math.max(0, Math.min(Bounties.NAMES.length - 1, boss))];
        String line1, line2;
        int head;
        if (state == Bounties.STATE_DONE) { head = 0xFF60FF80; line1 = "SUBJUGATED"; line2 = "+ merit stars (" + stars + " total)"; }
        else if (state == Bounties.STATE_EXPIRED) { head = 0xFF909090; line1 = "The trail went cold"; line2 = "The poster burned up."; }
        else if (state == Bounties.STATE_FOUND) { head = 0xFFFF4040; line1 = "Defeat " + name + "!"; line2 = "Time left: " + WantedPosterItem.time(Math.max(0, deadline - now)); }
        else {
            head = 0xFFFF4040;
            line1 = "Find him: " + dist + " m " + Bounties.direction(dx, dz);
            line2 = "Time left: " + WantedPosterItem.time(Math.max(0, deadline - now));
        }
        int h = 52;
        g.fill(x, y, x + w, y + h, 0xA0100804);
        g.renderOutline(x, y, w, h, 0xC0E8B84A);
        g.fill(x, y, x + 3, y + h, head);
        g.drawString(f, state >= Bounties.STATE_DONE ? "BOUNTY" : "WANTED", x + 8, y + 4, head, false);
        g.drawString(f, "★ " + stars, x + w - 6 - f.width("★ " + stars), y + 4, 0xFFFFD27A, false);
        g.drawString(f, name, x + 8, y + 15, 0xFFFFC040, false);
        StringBuilder m = new StringBuilder();
        for (int i = 0; i < Bounties.MOD_NAMES.length; i++) if ((mods & (1 << i)) != 0) m.append(m.length() > 0 ? ", " : "").append(Bounties.MOD_NAMES[i]);
        if (m.length() > 0) g.drawString(f, m.toString(), x + 8 + f.width(name) + 6, y + 15, 0xFFFF8080, false);
        g.drawString(f, line1, x + 8, y + 27, 0xFFFFFFFF, false);
        g.drawString(f, line2, x + 8, y + 38, state == Bounties.STATE_HUNT && deadline - now < 12000 ? 0xFFFF6060 : 0xFFB0B0B0, false);
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
