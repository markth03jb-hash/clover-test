package com.ironspellgrimoire.client;

import com.ironspellgrimoire.network.FxPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The rift's room map (server/Rifts, shape 79), at the top right under the trackers while you're inside: every room as
 * a small square joined by its corridors - grey until cleared, green once cleared, the relic chamber in gold, the elite
 * room in red, the rest room in blue - with the room you're in outlined and pulsing.
 */
@Mod.EventBusSubscriber(modid = "iron_spell_grimoire", value = Dist.CLIENT)
public final class RiftMap {
    private RiftMap() {}

    private static int[] rooms = new int[0];
    private static int here = -1, left;

    public static void set(FxPacket p) {
        double[] v = {p.c.x, p.c.y, p.c.z, p.d.x, p.d.y, p.d.z, p.u.x, p.u.y, p.u.z, p.v.x, p.v.y, p.v.z, p.r, p.r2, p.a0, p.a1, p.h, p.speed};
        int n = Math.max(0, Math.min(18, p.id));
        rooms = new int[n];
        for (int i = 0; i < n; i++) rooms[i] = (int) Math.round(v[i]);
        here = p.sweep - 1;
        left = p.life;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent e) { if (e.phase == TickEvent.Phase.END && left > 0) left--; }

    private static int gx(int r) { return (r & 31) - 16; }
    private static int gz(int r) { return ((r >> 5) & 31) - 16; }
    private static int type(int r) { return (r >> 10) & 7; }
    private static boolean cleared(int r) { return ((r >> 13) & 1) != 0; }
    private static boolean active(int r) { return ((r >> 14) & 1) != 0; }
    private static int doors(int r) { return (r >> 15) & 15; }

    // START, COMBAT, TRAP, REST, ELITE, RELIC, EXAM, MIRROR (server/Rifts)
    private static final int[] TINT = {0xFF9A88C8, 0xFFB0A8B8, 0xFFD89040, 0xFF60A8E0, 0xFFE04848, 0xFFF0C040, 0xFFF0C040, 0xFFC8D8F0};
    private static final String[] MARK = {"", "", "!", "+", "☠", "★", "★", "★"};

    @SubscribeEvent
    public static void onGui(RenderGuiEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (left <= 0 || rooms.length == 0 || mc.player == null || mc.options.hideGui || TrackerMode.hidden()) return;
        GuiGraphics g = e.getGuiGraphics();
        Font f = mc.font;
        int minX = 99, maxX = -99, minZ = 99, maxZ = -99;
        for (int r : rooms) { minX = Math.min(minX, gx(r)); maxX = Math.max(maxX, gx(r)); minZ = Math.min(minZ, gz(r)); maxZ = Math.max(maxZ, gz(r)); }
        int cell = TrackerMode.compact() ? 8 : 11, step = cell + (TrackerMode.compact() ? 4 : 6);
        int mw = (maxX - minX) * step + cell, mh = (maxZ - minZ) * step + cell;
        int pad = 6, w = Math.max(mw + pad * 2, 70), h = mh + pad * 2 + 12;
        int x = g.guiWidth() - w - 4, y = TrackerMode.worldY();
        g.fill(x, y, x + w, y + h, 0xA0100818);
        g.renderOutline(x, y, w, h, 0xC07A5AC8);
        int done = 0;
        for (int r : rooms) if (cleared(r) || type(r) == 0 || type(r) == 3) done++;
        g.drawString(f, "RIFT  " + done + "/" + rooms.length, x + 5, y + 3, 0xFFC8A8F8, false);
        int ox = x + (w - mw) / 2, oy = y + 13 + pad - 2;
        // corridors first, under the rooms
        for (int r : rooms) {
            int cx = ox + (gx(r) - minX) * step + cell / 2, cz = oy + (gz(r) - minZ) * step + cell / 2, d = doors(r);
            if ((d & 1) != 0) g.fill(cx, cz - 1, cx + step, cz + 1, 0xFF5A4A78);
            if ((d & 4) != 0) g.fill(cx - 1, cz, cx + 1, cz + step, 0xFF5A4A78);
        }
        long t = mc.level == null ? 0 : mc.level.getGameTime();
        for (int i = 0; i < rooms.length; i++) {
            int r = rooms[i], ty = type(r);
            int rx = ox + (gx(r) - minX) * step, rz = oy + (gz(r) - minZ) * step;
            boolean c = cleared(r) || ty == 0 || ty == 3;
            int fill = c ? (ty == 1 || ty == 2 ? 0xFF3A8A48 : TINT[ty]) : (TINT[ty] & 0x00FFFFFF) | 0x70000000;
            if (active(r) && !cleared(r)) fill = (t / 6) % 2 == 0 ? 0xFFE05030 : 0xFF902818;   // a fight under way
            g.fill(rx, rz, rx + cell, rz + cell, fill);
            if (!MARK[ty].isEmpty() && !TrackerMode.compact())
                g.drawString(f, MARK[ty], rx + (cell - f.width(MARK[ty])) / 2 + 1, rz + 2, c ? 0xFF201010 : 0xFFF0E8FF, false);
            if (i == here) {
                int a = 0xFF000000 | ((t / 5) % 2 == 0 ? 0xFFFFFF : 0xC8A8F8);
                g.renderOutline(rx - 2, rz - 2, cell + 4, cell + 4, a);
            }
        }
    }
}
