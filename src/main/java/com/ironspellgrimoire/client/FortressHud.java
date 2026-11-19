package com.ironspellgrimoire.client;

import com.ironspellgrimoire.server.FortressFx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The Architect of Ruin's tracker (top right, under the others while the book is worn): your next goal - always one
 * clear thing to do - the four Room pages with their cooldowns, and Siege Mode's state.
 */
@Mod.EventBusSubscriber(modid = "iron_spell_grimoire", value = Dist.CLIENT)
public final class FortressHud {
    private FortressHud() {}

    public static final int H = 46, W = 176;
    /** Taller by a line while an order is given or a breach is open. */
    public static int h() { return H + (extra() != null ? 11 : 0); }

    /** The extra line: a breach first, else the garrison's order. */
    static String extra() {
        if (FortressClient.breachLeft() > 0) return "BREACH - " + FortressClient.breachSide() + " wall";
        int o = FortressClient.order();
        if (o > 0 && o < com.ironspellgrimoire.server.FortressSiege.ORDER.length && FortressClient.defendersOut() > 0) return "Order: " + com.ironspellgrimoire.server.FortressSiege.ORDER[o];
        return null;
    }

    public static boolean shown() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && FortressClient.equipped() && !TrackerMode.hidden();
    }

    /** The one next thing to do, worked out from the state. */
    static String goal() {
        int stone = FortressClient.stone(), built = FortressClient.builtCount();
        int[] slots = FortressClient.slots();
        if (!FortressClient.toured()) return "Open The Fortress (its book page) for a quick tour";
        if (built == 0) return stone >= FortressFx.COST[0] ? "Build your first room - open The Fortress" : "Defeat foes for Stone (" + stone + " / " + FortressFx.COST[0] + ") to build your first room";
        boolean anySlot = false; for (int s : slots) if (s >= 0) anySlot = true;
        if (!anySlot) return "Put a room's spell on a Room page (Spells tab)";
        if (FortressClient.cd(-2) == 0 && (FortressClient.tag.getInt("hints") & (1 << FortressFx.HINT_SIEGE)) == 0) return "Try Siege Mode from the book";
        if (FortressClient.banners().size() > 0) return "Claim your banner" + (FortressClient.banners().size() > 1 ? "s" : "") + " - Garrison tab";
        int free = 0; for (int i = 0; i < 25; i++) { int n = FortressClient.gridSize(); if (i % 5 < n && i / 5 < n && FortressClient.grid()[i] < 0) free++; }
        int canBuild = 0; for (int r = 0; r < FortressFx.ROOMS; r++) if (FortressClient.unlocked(r) && FortressClient.roomLevel(r) == 0) canBuild++;
        if (free > 0 && canBuild > 0) return stone >= FortressFx.COST[0] ? "Build another room (" + canBuild + " ready)" : "Earn Stone for your next room (" + stone + " / " + FortressFx.COST[0] + ")";
        int cheapest = Integer.MAX_VALUE, which = -1;
        for (int r = 0; r < FortressFx.ROOMS; r++) { int lv = FortressClient.roomLevel(r); if (lv > 0 && lv < FortressFx.ROOM_MAX && FortressFx.COST[lv] < cheapest) { cheapest = FortressFx.COST[lv]; which = r; } }
        if (free == 0 && FortressClient.gridSize() < 5) return "Reach book level " + (FortressClient.gridSize() == 3 ? 4 : 8) + " to grow the floor plan";
        if (canBuild == 0 && built < FortressFx.BASE_ROOMS && free > 0) return "Find blueprints: bosses, rifts, book levels";
        if (which >= 0) return "Upgrade the " + FortressFx.ROOM[which].name() + " (" + Math.min(FortressClient.stone(), cheapest) + " / " + cheapest + " Stone)";
        if (!FortressClient.citadelOk()) return "Reach book level 10 and master the grimoire for the Citadel";
        if (FortressClient.prestigeOk()) return "Prestige for a star (" + FortressClient.stars() + " / " + FortressFx.MAX_STARS + ") - Floor Plan";
        return "The fortress is complete";
    }

    @SubscribeEvent
    public static void onGui(RenderGuiEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (!shown() || mc.options.hideGui || mc.level == null) return;
        GuiGraphics g = e.getGuiGraphics();
        Font f = mc.font;
        int y = TrackerMode.archY();
        String goal = goal();
        if (TrackerMode.compact()) {
            String ex = extra();
            TrackerMode.line(g, f, y, ex != null && FortressClient.breachLeft() > 0 ? 0xFFFF6A50 : 0xFFE8B860, "FORTRESS", ex != null ? ex : goal, 0, 0, false, mc.level.getGameTime(), 0);
            return;
        }
        int x = g.guiWidth() - W - 4, H = h();
        g.fill(x, y, x + W, y + H, 0xA0141008);
        g.renderOutline(x, y, W, H, 0xC09A6A28);
        g.fill(x, y, x + 3, y + H, 0xFFE8B860);
        String ex = extra();
        if (ex != null) {
            boolean breach = FortressClient.breachLeft() > 0;
            int col = breach ? ((mc.level.getGameTime() / 5) % 2 == 0 ? 0xFFFF6A50 : 0xFFE0A090) : 0xFFE8B860;
            g.drawString(f, ex, x + 8, y + 46, col, false);
        }
        String head = "FORTRESS  Lv " + FortressClient.level();
        g.drawString(f, head, x + 8, y + 4, 0xFFE8B860, false);
        String st = FortressClient.stone() + " Stone";
        g.drawString(f, st, x + W - 6 - f.width(st), y + 4, 0xFFC8C4BC, false);
        String gl = f.width(goal) > W - 14 ? f.plainSubstrByWidth(goal, W - 20) + ".." : goal;
        g.drawString(f, gl, x + 8, y + 15, 0xFFFFFFFF, false);
        // the four Room pages: icon and cooldown
        int[] slots = FortressClient.slots();
        for (int s = 0; s < 4; s++) {
            int ix = x + 8 + s * 22, iy = y + 27;
            g.fill(ix, iy, ix + 16, iy + 16, 0x80000000);
            if (slots[s] >= 0 && slots[s] < FortressFx.ROOMS) {
                g.blit(FortressScreen.roomIcon(slots[s]), ix, iy, 16, 16, 0, 0, 64, 64, 64, 64);
                int left = FortressClient.cd(slots[s]), cd = FortressFx.ROOM[slots[s]].cd();
                if (left > 0) g.fill(ix, iy + 16 - Math.min(16, 16 * left / Math.max(1, cd)), ix + 16, iy + 16, 0xA0000000);
            }
        }
        int siege = FortressClient.cd(-2);
        String ss = FortressClient.siegeUp() ? "SIEGE!" : siege > 0 ? "Siege " + (siege + 19) / 20 + "s" : "Siege ready";
        g.drawString(f, ss, x + W - 6 - f.width(ss), y + 32, FortressClient.siegeUp() ? 0xFFFF9A40 : siege > 0 ? 0xFF9A9286 : 0xFF8CE08C, false);
    }
}
