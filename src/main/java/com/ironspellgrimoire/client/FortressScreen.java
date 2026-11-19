package com.ironspellgrimoire.client;

import com.ironspellgrimoire.server.FortressFx;
import com.ironspellgrimoire.server.FortressSiege;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * The Fortress (the Architect of Ruin Grimoire's menu): a stone window with brass trim. Tabs:
 *   Floor Plan  the grid of rooms (3x3, 4x4, 5x5 by book level): click an empty space to build, a room to see it,
 *               upgrade or move it; rooms that pair are joined by a brass bar, and where a room would pair lights up
 *   Garrison    banners to claim, your defenders (role, rank, kills), where each is posted
 *   Spells      the four Room pages and the spells of every room you've built; Rampart, Siege Mode and the Citadel
 *   Guide       every system explained, and an encyclopedia of all 24 rooms
 * The first time it opens, a short walkthrough points at each part in turn (it can be skipped, and replayed from
 * the Guide).
 */
public class FortressScreen extends Screen {
    public static int startTab = -1;
    /** For the screenshots: start the walkthrough at this step (-1: as the save says). */
    public static int startTour = -1;
    /** For the screenshots: a cell already selected on the Floor Plan (-1 none). */
    public static int startCell = -1;
    /** For the screenshots: the Guide's topic to open on. */
    public static int startTopic = 0;
    private int tab, ticks, sel = -1, moving = -1, slotSel = 0, listScroll, guideTopic, guideRoom, guideScroll, tour = -1;
    private final List<int[]> hits = new ArrayList<>();   // {x0, y0, x1, y1, action, arg}
    static final String[] TABS = {"FLOOR PLAN", "GARRISON", "SPELLS", "GUIDE"};
    static final int BRASS = 0xFFE8B860, BRASS_D = 0xFF9A6A28, STONE_L = 0xFFC8C4BC, TEXT = 0xFFEDE6D8, DIM = 0xFF9A9286, EMBER = 0xFFFF9A40,
            GOOD = 0xFF8CE08C, BAD = 0xFFE07A6A;
    static final int[] WING_COL = {0xFFD0604C, 0xFF5CC8C0, 0xFFE8C860, 0xFFFF9A40};

    public FortressScreen() {
        super(Component.literal("The Fortress"));
        tab = startTab >= 0 ? startTab : 0;
        sel = startCell;
        guideTopic = Mth.clamp(startTopic, 0, TOPICS.length - 1);
        tour = startTour >= 0 ? startTour : FortressClient.toured() ? -1 : 0;
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void tick() { ticks++; }

    private int W() { return Math.min(420, width - 16); }
    private int H() { return Math.min(252, height - 12); }

    static ResourceLocation icon(String key) { return ResourceLocation.fromNamespaceAndPath("iron_spell_grimoire", "textures/gui/spell_icons/" + key + ".png"); }
    static ResourceLocation roomIcon(int room) { return icon("fortress_" + FortressClient.key(room)); }
    void blitIcon(GuiGraphics g, ResourceLocation r, int x, int y, int s) { g.blit(r, x, y, s, s, 0, 0, 64, 64, 64, 64); }

    // ================================================================== render
    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        hits.clear();
        int W = W(), H = H(), x0 = (width - W) / 2, y0 = (height - H) / 2;
        g.fillGradient(0, 0, width, height, 0xB0060504, 0xD00A0806);
        g.fill(x0, y0, x0 + W, y0 + H, 0xF01A1714);
        final int fx = x0, fy = y0, fw = W, fh = H;
        g.drawManaged(() -> masonry(g, fx, fy, fw, fh));
        frame(g, x0, y0, W, H);
        g.drawString(font, "ARCHITECT OF RUIN", x0 + 10, y0 + 7, BRASS, false);
        String right = "Lv " + FortressClient.level() + "    Stone " + FortressClient.stone();
        g.drawString(font, right, x0 + W - 10 - font.width(right), y0 + 7, STONE_L, false);
        if (FortressClient.level() < FortressFx.MAX_LV) {   // the XP bar under the title
            float k = Mth.clamp(FortressClient.xp() / FortressFx.need(FortressClient.level()), 0, 1);
            g.fill(x0 + W - 110, y0 + 17, x0 + W - 10, y0 + 19, 0xFF2A2420);
            g.fill(x0 + W - 110, y0 + 17, x0 + W - 110 + (int) (100 * k), y0 + 19, BRASS);
        }
        int tw = 84, tx0 = x0 + (W - tw * 4 - 3 * 4) / 2;
        for (int i = 0; i < 4; i++) {
            int tx = tx0 + i * (tw + 4), ty = y0 + 22;
            boolean on = i == tab, hov = in(mx, my, tx, ty, tw, 13);
            g.fill(tx, ty, tx + tw, ty + 13, on ? 0xE03A3228 : hov ? 0xC02A241E : 0xB0201C18);
            g.fill(tx, ty + 12, tx + tw, ty + 13, on ? BRASS : 0x60E8B860);
            g.drawCenteredString(font, TABS[i], tx + tw / 2, ty + 3, on ? BRASS : DIM);
            hits.add(new int[]{tx, ty, tx + tw, ty + 13, 0, i});
        }
        int top = y0 + 40;
        if (!FortressClient.equipped()) {
            String s = "Wear the Architect of Ruin Grimoire to build and fight with your fortress.";
            g.drawCenteredString(font, s, x0 + W / 2, y0 + H - 12, BAD);
        }
        switch (tab) {
            case 0 -> floorPlan(g, x0, top, W, H - 40, mx, my);
            case 1 -> garrison(g, x0, top, W, H - 40, mx, my);
            case 2 -> spells(g, x0, top, W, H - 40, mx, my);
            default -> guide(g, x0, top, W, H - 40, mx, my);
        }
        if (tour >= 0) tourOverlay(g, x0, y0, W, H, mx, my);
    }

    static boolean in(double mx, double my, int x, int y, int w, int h) { return mx >= x && mx < x + w && my >= y && my < y + h; }

    /** Faint courses of stone behind everything (one batched draw). */
    private static void masonry(GuiGraphics g, int x0, int y0, int W, int H) {
        for (int row = 0, y = y0 + 3; y < y0 + H - 3; row++, y += 12) {
            int off = row % 2 == 0 ? 0 : 14;
            for (int x = x0 + 3 - off; x < x0 + W - 3; x += 28) {
                int a = Math.max(x, x0 + 3), b = Math.min(x + 27, x0 + W - 3);
                if (b - a < 2) continue;
                int shade = ((x * 31 + y * 17) & 7) * 2;
                g.fill(a, y, b, Math.min(y + 11, y0 + H - 3), 0x18000000 | (0x30 + shade) << 16 | (0x2C + shade) << 8 | (0x26 + shade));
            }
        }
    }

    private static void frame(GuiGraphics g, int x0, int y0, int W, int H) {
        g.fill(x0, y0, x0 + W, y0 + 1, BRASS); g.fill(x0, y0 + H - 1, x0 + W, y0 + H, BRASS_D);
        g.fill(x0, y0, x0 + 1, y0 + H, BRASS); g.fill(x0 + W - 1, y0, x0 + W, y0 + H, BRASS_D);
        for (int[] c : new int[][]{{x0, y0}, {x0 + W - 6, y0}, {x0, y0 + H - 6}, {x0 + W - 6, y0 + H - 6}}) g.fill(c[0], c[1], c[0] + 6, c[1] + 6, BRASS);
    }

    /** A sub-panel (opaque: the layout check keeps text inside it). */
    private static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xE0141210);
        g.fill(x, y, x + w, y + 1, 0x80E8B860);
        g.fill(x, y + h - 1, x + w, y + h, 0x409A6A28);
    }

    private void button(GuiGraphics g, int x, int y, int w, String s, boolean can, int mx, int my, int action, int arg) {
        boolean hov = can && in(mx, my, x, y, w, 13);
        g.fill(x, y, x + w, y + 13, !can ? 0xA0201C18 : hov ? 0xE05A4628 : 0xD03A3022);
        g.fill(x, y + 12, x + w, y + 13, can ? BRASS : 0x40E8B860);
        String t = font.width(s) > w - 4 ? font.plainSubstrByWidth(s, w - 8) + ".." : s;
        g.drawCenteredString(font, t, x + w / 2, y + 3, can ? TEXT : DIM);
        if (can) hits.add(new int[]{x, y, x + w, y + 13, action, arg});
    }

    /** Wrapped text inside a width; returns the y after it. Stops at maxY. */
    private int text(GuiGraphics g, String s, int x, int y, int w, int col, int maxY) {
        for (FormattedCharSequence l : font.split(Component.literal(s), w)) {
            if (y + 9 > maxY) break;
            g.drawString(font, l, x, y, col, false);
            y += 10;
        }
        return y;
    }

    private String fit(String s, int w) { return font.width(s) <= w ? s : font.plainSubstrByWidth(s, w - 6) + ".."; }

    // ================================================================== Floor Plan
    static final int CELL = 30, GAP = 3;

    int cellX(int gx, int c) { return gx + (c % 5) * (CELL + GAP); }
    int cellY(int gy, int c) { return gy + (c / 5) * (CELL + GAP); }

    private void floorPlan(GuiGraphics g, int x0, int top, int W, int H, int mx, int my) {
        int gx = x0 + 12, gy = top + 6, n = FortressClient.gridSize();
        int[] grid = FortressClient.grid();
        panel(g, gx - 5, gy - 5, 5 * (CELL + GAP) + 7, 5 * (CELL + GAP) + 7);
        // where the room being placed or moved would pair up
        boolean[] glow = new boolean[25];
        int placing = moving >= 0 ? grid[moving] / 10 : -1;
        if (placing >= 0) for (FortressFx.Pair pr : FortressFx.PAIRS) {
            int other = pr.a() == placing ? pr.b() : pr.b() == placing ? pr.a() : -1;
            int oc = other >= 0 ? FortressClient.cellOf(other) : -1;
            if (oc < 0) continue;
            for (int d : new int[]{-5, 5, -1, 1}) {
                int c = oc + d;
                if (c < 0 || c >= 25 || (d == -1 || d == 1) && c / 5 != oc / 5) continue;
                glow[c] = true;
            }
        }
        int hover = -1;
        for (int c = 0; c < 25; c++) {
            int cx = cellX(gx, c), cy = cellY(gy, c);
            boolean inside = c % 5 < n && c / 5 < n;
            boolean hov = in(mx, my, cx, cy, CELL, CELL);
            if (hov && inside) hover = c;
            if (!inside) {   // beyond the plan for now
                g.fill(cx, cy, cx + CELL, cy + CELL, 0x80100E0C);
                g.fill(cx + 13, cy + 13, cx + 17, cy + 17, 0x40E8B860);
                continue;
            }
            int v = grid[c];
            boolean picked = c == sel || c == moving;
            int bg = v >= 0 ? 0xFF2E2822 : glow[c] ? 0xFF3A3020 : 0xFF221E1A;
            g.fill(cx, cy, cx + CELL, cy + CELL, bg);
            if (glow[c] && v < 0) { int a = (int) (0x50 + 0x30 * Mth.sin((ticks + c) * 0.25F)); g.fill(cx, cy, cx + CELL, cy + CELL, a << 24 | 0xE8B860); }
            if (v >= 0) {
                int room = v / 10, lv = v % 10;
                int wc = WING_COL[FortressFx.ROOM[room].wing()];
                g.fill(cx, cy, cx + CELL, cy + 1, wc); g.fill(cx, cy, cx + 1, cy + CELL, wc);
                blitIcon(g, roomIcon(room), cx + 5, cy + 3, 20);
                for (int i = 0; i < 5; i++) g.fill(cx + 4 + i * 5, cy + CELL - 4, cx + 7 + i * 5, cy + CELL - 2, i < lv ? BRASS : 0x60FFFFFF);
            }
            int edge = picked ? BRASS : hov ? 0xFFB0A890 : 0x60605848;
            g.renderOutline(cx, cy, CELL, CELL, edge);
            hits.add(new int[]{cx, cy, cx + CELL, cy + CELL, 1, c});
        }
        // the brass bars between rooms that pair
        for (FortressFx.Pair pr : FortressFx.PAIRS) {
            if (!FortressClient.paired(pr.a(), pr.b())) continue;
            int a = FortressClient.cellOf(pr.a()), b = FortressClient.cellOf(pr.b());
            int ax = cellX(gx, a) + CELL / 2, ay = cellY(gy, a) + CELL / 2, bx = cellX(gx, b) + CELL / 2, by = cellY(gy, b) + CELL / 2;
            int col = (int) (0xC0 + 0x3F * Mth.sin(ticks * 0.2F)) << 24 | 0xE8B860;
            if (ay == by) g.fill(Math.min(ax, bx) + CELL / 2 - 1, ay - 2, Math.max(ax, bx) - CELL / 2 + 1, ay + 2, col);
            else g.fill(ax - 2, Math.min(ay, by) + CELL / 2 - 1, ax + 2, Math.max(ay, by) - CELL / 2 + 1, col);
        }
        // the side panel
        int px = gx + 5 * (CELL + GAP) + 10, pw = x0 + W - 10 - px, py = top + 1, ph = H - 6;
        panel(g, px, py, pw, ph);
        int ix = px + 6, iw = pw - 12, iy = py + 6, maxY = py + ph - 4;
        if (moving >= 0) {
            int room = grid[moving] / 10;
            g.drawString(font, fit("MOVING: " + FortressFx.ROOM[room].name().toUpperCase(), iw), ix, iy, BRASS, false);
            iy = text(g, "Click any space on the plan to move it there (a room already there swaps places). Glowing spaces sit beside a room it pairs with.", ix, iy + 14, iw, TEXT, maxY - 20);
            button(g, ix, maxY - 16, 80, "Cancel", true, mx, my, 17, 0);
        } else if (sel < 0 || sel >= 25 || !(sel % 5 < n && sel / 5 < n)) {
            g.drawString(font, "YOUR FORTRESS", ix, iy, BRASS, false);
            iy = text(g, "Click an empty space to build a room, or a room to see it.", ix, iy + 14, iw, TEXT, maxY);
            iy += 4;
            int pairs = 0; for (FortressFx.Pair pr : FortressFx.PAIRS) if (FortressClient.paired(pr.a(), pr.b())) pairs++;
            int open = 0; for (int r = 0; r < FortressFx.ROOMS; r++) if (FortressClient.unlocked(r)) open++;
            iy = line(g, "Rooms built", FortressClient.builtCount() + " / 24", ix, iy, iw, maxY);
            iy = line(g, "Blueprints", open + " / 24", ix, iy, iw, maxY);
            iy = line(g, "Neighbours", pairs + " / " + FortressFx.PAIRS.length, ix, iy, iw, maxY);
            iy = line(g, "Floor plan", n + " x " + n + (n < 5 ? "  (grows at Lv " + (n == 3 ? 4 : 8) + ")" : ""), ix, iy, iw, maxY);
            iy += 6;
            g.drawString(font, "NEXT", ix, iy, BRASS, false);
            text(g, FortressHud.goal(), ix, iy + 11, iw, TEXT, maxY);
        } else if (grid[sel] < 0) {
            buildList(g, ix, iy, iw, maxY, mx, my);
        } else roomInfo(g, grid[sel] / 10, grid[sel] % 10, ix, iy, iw, maxY, mx, my);
        if (hover >= 0 && grid[hover] >= 0 && tour < 0) {
            int room = grid[hover] / 10;
            List<Component> tip = new ArrayList<>();
            tip.add(Component.literal(FortressFx.ROOM[room].name() + "  Lv " + grid[hover] % 10).withStyle(s -> s.withColor(BRASS & 0xFFFFFF)));
            tip.add(Component.literal(FortressFx.ROOM[room].spell() + " - " + FortressFx.ROOM[room].does()).withStyle(s -> s.withColor(TEXT & 0xFFFFFF)));
            if (!FortressFx.ROOM[room].passive().equals("-")) tip.add(Component.literal("Passive: " + FortressFx.ROOM[room].passive()).withStyle(s -> s.withColor(DIM & 0xFFFFFF)));
            g.renderComponentTooltip(font, tip, mx, my);
        }
    }

    private int line(GuiGraphics g, String k, String v, int x, int y, int w, int maxY) {
        if (y + 9 > maxY) return y;
        g.drawString(font, k, x, y, DIM, false);
        g.drawString(font, v, x + w - font.width(v), y, TEXT, false);
        return y + 11;
    }

    /** An empty space chosen: every room you can build, with its cost. */
    private void buildList(GuiGraphics g, int ix, int iy, int iw, int maxY, int mx, int my) {
        g.drawString(font, "BUILD HERE", ix, iy, BRASS, false);
        List<Integer> can = new ArrayList<>();
        int locked = 0;
        for (int r = 0; r < FortressFx.ROOMS; r++) {
            if (FortressClient.roomLevel(r) > 0) continue;
            if (FortressClient.unlocked(r)) can.add(r); else locked++;
        }
        int y = iy + 13, rowH = 15;
        int rows = Math.max(1, (maxY - 14 - y) / rowH);
        listScroll = Mth.clamp(listScroll, 0, Math.max(0, can.size() - rows));
        if (can.isEmpty()) y = text(g, "Every room you have a blueprint for is built. Bosses, rifts and book levels give new blueprints.", ix, y, iw, TEXT, maxY);
        for (int i = listScroll; i < can.size() && i < listScroll + rows; i++) {
            int r = can.get(i), cost = FortressFx.COST[0];
            boolean afford = FortressClient.stone() >= cost;
            boolean hov = in(mx, my, ix, y, iw, rowH - 1);
            g.fill(ix, y, ix + iw, y + rowH - 1, hov ? 0xC03A3022 : 0x80241E1A);
            blitIcon(g, roomIcon(r), ix + 1, y + 1, 12);
            String c = cost + " Stone";
            g.drawString(font, fit(FortressFx.ROOM[r].name(), iw - 22 - font.width(c) - 4), ix + 16, y + 3, afford ? TEXT : DIM, false);
            g.drawString(font, c, ix + iw - 3 - font.width(c), y + 3, afford ? BRASS : BAD, false);
            hits.add(new int[]{ix, y, ix + iw, y + rowH - 1, 2, r});
            y += rowH;
        }
        if (can.size() > rows) g.drawString(font, (listScroll + rows < can.size() ? "v " : "^ ") + "scroll for more", ix, maxY - 11, DIM, false);
        else if (locked > 0) g.drawString(font, fit(locked + " more need a blueprint", iw), ix, maxY - 11, DIM, false);
    }

    /** A built room chosen: what it does, its level, what it pairs with, upgrade and move. */
    private void roomInfo(GuiGraphics g, int room, int lv, int ix, int iy, int iw, int maxY, int mx, int my) {
        FortressFx.Room r = FortressFx.ROOM[room];
        blitIcon(g, roomIcon(room), ix, iy, 16);
        g.drawString(font, fit(r.name().toUpperCase(), iw - 20), ix + 20, iy, WING_COL[r.wing()], false);
        g.drawString(font, fit(FortressFx.WING[r.wing()] + " wing - Lv " + lv + " / 5", iw - 20), ix + 20, iy + 9, DIM, false);
        int y = iy + 22;
        g.drawString(font, fit(r.spell(), iw), ix, y, BRASS, false);
        y = text(g, r.does(), ix, y + 10, iw, TEXT, maxY - 40);
        y = text(g, (r.cd() / 20) + " s cooldown, " + r.mana() + " mana. +15% per level.", ix, y, iw, DIM, maxY - 40);
        if (!r.passive().equals("-")) y = text(g, "Passive: " + r.passive(), ix, y + 2, iw, DIM, maxY - 40);
        y += 3;
        for (FortressFx.Pair pr : FortressFx.PAIRS) {
            int other = pr.a() == room ? pr.b() : pr.b() == room ? pr.a() : -1;
            if (other < 0 || y + 9 > maxY - 34) continue;
            boolean on = FortressClient.paired(room, other);
            String s = (on ? "+ " : "- ") + FortressFx.ROOM[other].name() + ": " + pr.gives();
            y = text(g, s, ix, y, iw, on ? GOOD : DIM, maxY - 34);
        }
        int by = maxY - 16;
        boolean max = lv >= FortressFx.ROOM_MAX;
        int cost = max ? 0 : FortressFx.COST[lv];
        button(g, ix, by, iw / 2 - 2, max ? "Highest level" : "Upgrade " + cost, !max && FortressClient.stone() >= cost, mx, my, 3, room);
        button(g, ix + iw / 2 + 2, by, iw / 2 - 2, "Move", true, mx, my, 4, sel);
    }

    // ================================================================== Garrison
    private void garrison(GuiGraphics g, int x0, int top, int W, int H, int mx, int my) {
        int lx = x0 + 10, lw = 150, ly = top + 1, lh = H - 6;
        panel(g, lx, ly, lw, lh);
        g.drawString(font, "BANNERS", lx + 6, ly + 6, BRASS, false);
        var banners = FortressClient.banners();
        int y = ly + 19;
        if (banners.isEmpty()) y = text(g, "Hostile mobs you defeat now and then leave a banner (bosses always do). Claim one here and that mob joins your garrison.", lx + 6, y, lw - 12, DIM, ly + lh - 4);
        for (int i = 0; i < banners.size() && y + 30 < ly + lh; i++) {
            String type = banners.getString(i);
            g.drawString(font, fit(FortressSiege.typeName(type), lw - 12), lx + 6, y, TEXT, false);
            g.drawString(font, fit("as a " + FortressSiege.ROLE[FortressSiege.roleOf(type)], lw - 12), lx + 6, y + 9, DIM, false);
            button(g, lx + 6, y + 19, 66, "Claim", FortressClient.garrison().size() < FortressClient.cap(), mx, my, 5, i);
            button(g, lx + 76, y + 19, 66, "Release", true, mx, my, 6, i);
            y += 38;
        }
        int rx = lx + lw + 8, rw = x0 + W - 10 - rx;
        panel(g, rx, ly, rw, lh);
        var gar = FortressClient.garrison();
        String head = "GARRISON  " + gar.size() + " / " + FortressClient.cap();
        g.drawString(font, head, rx + 6, ly + 6, BRASS, false);
        String sub = "Barracks: +1 room per level (8 at most)";
        g.drawString(font, fit(sub, rw - 18 - font.width(head)), rx + 12 + font.width(head), ly + 6, DIM, false);
        int cy = ly + 19, cw = (rw - 18) / 2, ch = 44;
        if (gar.isEmpty()) text(g, "No defenders yet. Claim a banner, or cast Call Guard: until then plain stone guards answer.", rx + 6, cy, rw - 12, DIM, ly + lh - 4);
        for (int i = 0; i < gar.size(); i++) {
            CompoundTag e = gar.getCompound(i);
            int col = i % 2, row = i / 2;
            int cx = rx + 6 + col * (cw + 6), yy = cy + row * (ch + 4);
            if (yy + ch > ly + lh - 2) break;
            g.fill(cx, yy, cx + cw, yy + ch, 0xC0241E1A);
            int kills = e.getInt("kills"), rank = 0;
            for (int k = 0; k < FortressSiege.RANK_AT.length; k++) if (kills >= FortressSiege.RANK_AT[k]) rank = k;
            String role = FortressSiege.ROLE[Mth.clamp(e.getInt("role"), 0, 4)];
            g.drawString(font, fit(FortressSiege.typeName(e.getString("type")), cw - 8), cx + 4, yy + 3, TEXT, false);
            g.drawString(font, fit(FortressSiege.RANK[rank] + " " + role + " - " + kills + " kills", cw - 8), cx + 4, yy + 12, rank >= 2 ? BRASS : DIM, false);
            int post = e.getInt("post");
            String ps = post < 0 ? "Post: none" : "Post: " + FortressFx.ROOM[post].name();
            button(g, cx + 4, yy + 26, cw - 34, ps, true, mx, my, 7, i);
            button(g, cx + cw - 28, yy + 26, 24, "X", true, mx, my, 8, e.getInt("id"));
        }
    }

    // ================================================================== Spells
    private void spells(GuiGraphics g, int x0, int top, int W, int H, int mx, int my) {
        int lx = x0 + 10, lw = W - 20 - 128, ly = top + 1, lh = H - 6;
        panel(g, lx, ly, lw, lh);
        g.drawString(font, "ROOM PAGES", lx + 6, ly + 6, BRASS, false);
        String hint = "choose a page, then a spell";
        g.drawString(font, fit(hint, lw - 80), lx + lw - 6 - font.width(fit(hint, lw - 80)), ly + 6, DIM, false);
        int[] slots = FortressClient.slots();
        int sw = (lw - 12 - 3 * 4) / 4;
        for (int s = 0; s < 4; s++) {
            int sx = lx + 6 + s * (sw + 4), sy = ly + 18;
            boolean on = s == slotSel, hov = in(mx, my, sx, sy, sw, 34);
            g.fill(sx, sy, sx + sw, sy + 34, on ? 0xE03A3022 : hov ? 0xC02E2620 : 0xB0201C18);
            g.renderOutline(sx, sy, sw, 34, on ? BRASS : 0x60E8B860);
            g.drawString(font, "Room " + (s + 1), sx + 4, sy + 3, on ? BRASS : DIM, false);
            int room = slots[s];
            if (room >= 0 && room < FortressFx.ROOMS) {
                blitIcon(g, roomIcon(room), sx + 4, sy + 14, 16);
                g.drawString(font, fit(FortressFx.ROOM[room].spell(), sw - 26), sx + 22, sy + 18, TEXT, false);
            } else g.drawString(font, "empty", sx + 4, sy + 18, DIM, false);
            hits.add(new int[]{sx, sy, sx + sw, sy + 34, 9, s});
        }
        button(g, lx + 6, ly + 56, 90, "Clear page " + (slotSel + 1), slots[slotSel] >= 0, mx, my, 11, slotSel);
        // the spells of every built room
        int y = ly + 74, cols = 2, cw = (lw - 12 - 4) / cols, rh = 18;
        List<Integer> built = new ArrayList<>();
        for (int r = 0; r < FortressFx.ROOMS; r++) if (FortressClient.roomLevel(r) > 0) built.add(r);
        if (built.isEmpty()) text(g, "Build rooms on the Floor Plan: each gives a spell to put on these pages.", lx + 6, y, lw - 12, DIM, ly + lh - 4);
        int rows = Math.max(1, (ly + lh - 4 - y) / rh);
        listScroll = Mth.clamp(listScroll, 0, Math.max(0, (built.size() + 1) / 2 - rows));
        for (int i = listScroll * 2; i < built.size(); i++) {
            int col = i % 2, row = i / 2 - listScroll;
            if (row >= rows) break;
            int r = built.get(i), cx = lx + 6 + col * (cw + 4), yy = y + row * rh;
            boolean onPage = false; for (int s : slots) if (s == r) onPage = true;
            boolean hov = in(mx, my, cx, yy, cw, rh - 2);
            g.fill(cx, yy, cx + cw, yy + rh - 2, hov ? 0xC03A3022 : onPage ? 0x9030281C : 0x80241E1A);
            blitIcon(g, roomIcon(r), cx + 1, yy + 1, 14);
            int left = FortressClient.cd(r);
            String right = left > 0 ? (left + 19) / 20 + "s" : FortressFx.ROOM[r].mana() + "m";
            g.drawString(font, fit(FortressFx.ROOM[r].spell(), cw - 22 - font.width(right) - 4), cx + 18, yy + 4, onPage ? BRASS : TEXT, false);
            g.drawString(font, right, cx + cw - 3 - font.width(right), yy + 4, left > 0 ? EMBER : DIM, false);
            hits.add(new int[]{cx, yy, cx + cw, yy + rh - 2, 10, r});
        }
        // the always-there pages
        int rx = lx + lw + 6, rw = x0 + W - 10 - rx;
        panel(g, rx, ly, rw, lh);
        g.drawString(font, "ALWAYS", rx + 6, ly + 6, BRASS, false);
        int yy = ly + 20;
        yy = always(g, "Rampart", -1, FortressFx.RAMPART_MANA, rx + 6, yy, rw - 12, true);
        yy = always(g, "Siege Mode", -2, FortressFx.SIEGE_MANA, rx + 6, yy, rw - 12, true);
        yy = always(g, "Citadel", -3, FortressFx.CITADEL_MANA, rx + 6, yy, rw - 12, FortressClient.citadelOk());
        if (!FortressClient.citadelOk()) yy = text(g, "The Citadel: book level 10 and the grimoire mastered.", rx + 6, yy + 2, rw - 12, DIM, ly + lh - 4);
        if (FortressClient.siegeUp()) text(g, "A siege is up now.", rx + 6, yy + 4, rw - 12, GOOD, ly + lh - 4);
    }

    private int always(GuiGraphics g, String name, int key, int mana, int x, int y, int w, boolean can) {
        int left = FortressClient.cd(key);
        String r = !can ? "locked" : left > 0 ? (left + 19) / 20 + " s" : "ready";
        g.drawString(font, name, x, y, can ? TEXT : DIM, false);
        g.drawString(font, r, x + w - font.width(r), y, !can ? DIM : left > 0 ? EMBER : GOOD, false);
        g.drawString(font, mana + " mana", x, y + 9, DIM, false);
        return y + 22;
    }

    // ================================================================== Guide
    static final String[] TOPICS = {"Getting started", "Stone and levels", "Rooms", "Neighbours", "Room pages", "Siege Mode", "Garrison", "Blueprints", "The Citadel", "All 24 rooms"};
    static final String[][] GUIDE = {
        {"The Architect of Ruin keeps a fortress inside its pages. You build it room by room, and it fights for you.",
         "1. Defeat foes to earn Stone.", "2. Open The Fortress (its page in the book) and build a room on the Floor Plan.",
         "3. Each room gives a spell: put it on one of the four Room pages (Spells tab), then cast it from the book like any spell.",
         "4. Cast Siege Mode to raise walls and towers round you, with your garrison holding the middle.",
         "The tracker at the top right always shows your next goal."},
        {"Stone builds and upgrades rooms. Every foe you defeat gives some (bigger foes more, bosses 50+), rifts give 30 and the world boss 80. The Treasury adds a quarter, and its Tribute triples it for 30 s.",
         "Rooms cost 20 Stone to build, then 40, 80, 160 and 320 for levels 2 to 5. Moving a room is free.",
         "The book has its own level, 1 to 10, from the foes you defeat. Every level gives a blueprint, makes your spells 4% stronger and, at 4 and 8, grows the floor plan (3x3, then 4x4, then 5x5)."},
        {"There are 24 rooms in four wings: Martial (red), Arcane (teal), Sacred (gold) and Works (orange).",
         "Each room gives one spell and some give a passive while the book is worn (more damage, mana, armour...).",
         "A room's level (1-5) makes its spell 15% stronger each time. Click a room on the Floor Plan to see it, upgrade it or move it."},
        {"Rooms placed side by side - up, down, left or right, not corners - can pair for a neighbour bonus: the Forge beside the Armoury makes Blade Rack's weapons burn, the Barracks beside the Chapel heals your defenders, and so on.",
         "Paired rooms are joined by a brass bar on the plan. When you move a room, the spaces where it would pair glow.",
         "There are 12 pairs. The room encyclopedia lists each room's partners."},
        {"The book has eight pages: Rampart, Siege Mode, four Room pages, The Fortress and the Citadel.",
         "A Room page casts whichever room spell you put on it (Spells tab). A newly built room fills the first empty page by itself.",
         "Room spells keep their own cooldowns and mana costs, shown on the Spells tab."},
        {"Siege Mode raises a square of walls round you for 30 s: 6 blocks out, 8 at book level 4, 10 at level 8. The gate is on the side you face.",
         "Towers stand at the corners: two Watchtowers to begin with, and one for each tower room you build - Watchtower (arrows), Arcanum (beams), Forge (fire) and Workshop (bolts), two at room level 3. Six at most.",
         "Your garrison steps out to hold the middle. Walls block mobs and arrows and can be broken; the Gatehouse makes them tougher.",
         "You can never be shut in: a wall gives way if you're pushed into it, and everything goes when the siege ends, or if you leave, log out or take the book off.",
         "Rampart, always on its page, raises five blocks of wall in front of you for 12 s."},
        {"Hostile mobs you defeat sometimes leave a banner (one in ten; bosses always). Claim it on the Garrison tab and that mob joins your garrison.",
         "Its role comes from what it was: Footman, Archer, Mystic or Brute. Defenders rank up with their kills: Veteran at 25, Knight at 75, Captain at 200.",
         "You can keep 4, plus one per Barracks level (8 at most). Post a defender in a room and that room's spell hits a little harder.",
         "Defenders only appear in Siege Mode or when a spell calls them (Call Guard, Drill Charge), so they never crowd you."},
        {"Rooms 1-8 (Barracks to Library) are open from the start. The other 16 need a blueprint first.",
         "You get a blueprint at every book level, from every boss you defeat, from the Cinder Sovereign, and half the time from a cleared rift. Each one opens a random room you don't have yet."},
        {"At book level 10 with the grimoire mastered, the Citadel opens: a full fortress rises for 20 s - walls twice as tough, every tower, the whole garrison.",
         "Every room you've built fires its spell once, in a wave, at the foes nearest you; then the walls fall inward on whoever is beside them."},
        {},
    };

    private void guide(GuiGraphics g, int x0, int top, int W, int H, int mx, int my) {
        int lx = x0 + 10, lw = 112, ly = top + 1, lh = H - 6;
        panel(g, lx, ly, lw, lh);
        int y = ly + 6;
        for (int i = 0; i < TOPICS.length; i++) {
            boolean on = i == guideTopic, hov = in(mx, my, lx + 3, y - 2, lw - 6, 12);
            if (on || hov) g.fill(lx + 3, y - 2, lx + lw - 3, y + 10, on ? 0xE03A3022 : 0xA02A241E);
            g.drawString(font, fit(TOPICS[i], lw - 12), lx + 6, y, on ? BRASS : TEXT, false);
            hits.add(new int[]{lx + 3, y - 2, lx + lw - 3, y + 10, 12, i});
            y += 13;
        }
        button(g, lx + 6, ly + lh - 18, lw - 12, "Replay walkthrough", true, mx, my, 16, 0);
        int rx = lx + lw + 6, rw = x0 + W - 10 - rx;
        panel(g, rx, ly, rw, lh);
        int ix = rx + 7, iw = rw - 14, maxY = ly + lh - 4;
        g.drawString(font, TOPICS[guideTopic].toUpperCase(), ix, ly + 6, BRASS, false);
        if (guideTopic == TOPICS.length - 1) { encyclopedia(g, ix, ly + 19, iw, maxY, mx, my); return; }
        List<FormattedCharSequence> lines = new ArrayList<>();
        for (String p : GUIDE[guideTopic]) { lines.addAll(font.split(Component.literal(p), iw)); lines.add(FormattedCharSequence.EMPTY); }
        int rows = (maxY - (ly + 19)) / 10;
        guideScroll = Mth.clamp(guideScroll, 0, Math.max(0, lines.size() - rows));
        int yy = ly + 19;
        for (int i = guideScroll; i < lines.size() && i < guideScroll + rows; i++) { g.drawString(font, lines.get(i), ix, yy, TEXT, false); yy += 10; }
        if (lines.size() > rows) g.drawString(font, guideScroll + rows < lines.size() ? "v" : "^", rx + rw - 10, maxY - 9, DIM, false);
    }

    private void encyclopedia(GuiGraphics g, int ix, int iy, int iw, int maxY, int mx, int my) {
        int cols = 3, cw = (iw - 8) / cols;
        for (int r = 0; r < FortressFx.ROOMS; r++) {
            int cx = ix + (r % cols) * (cw + 4), cy = iy + (r / cols) * 10;
            boolean on = r == guideRoom, hov = in(mx, my, cx, cy, cw, 10);
            if (on || hov) g.fill(cx - 1, cy - 1, cx + cw, cy + 9, on ? 0xE03A3022 : 0xA02A241E);
            g.drawString(font, fit(FortressFx.ROOM[r].name(), cw - 2), cx, cy, on ? BRASS : FortressClient.unlocked(r) ? TEXT : DIM, false);
            hits.add(new int[]{cx, cy - 1, cx + cw, cy + 9, 13, r});
        }
        int y = iy + 8 * 10 + 6;
        FortressFx.Room r = FortressFx.ROOM[guideRoom];
        blitIcon(g, roomIcon(guideRoom), ix, y, 16);
        g.drawString(font, fit(r.name() + " - " + FortressFx.WING[r.wing()], iw - 20), ix + 20, y + 4, WING_COL[r.wing()], false);
        y += 20;
        y = text(g, r.spell() + ": " + r.does() + " (" + r.cd() / 20 + " s, " + r.mana() + " mana)", ix, y, iw, TEXT, maxY);
        if (!r.passive().equals("-")) y = text(g, "Passive: " + r.passive(), ix, y, iw, DIM, maxY);
        y = text(g, guideRoom < FortressFx.OPEN ? "Open from the start." : FortressClient.unlocked(guideRoom) ? "Blueprint found." : "Needs a blueprint.", ix, y, iw, DIM, maxY);
        for (FortressFx.Pair pr : FortressFx.PAIRS) {
            int other = pr.a() == guideRoom ? pr.b() : pr.b() == guideRoom ? pr.a() : -1;
            if (other >= 0) y = text(g, "Beside the " + FortressFx.ROOM[other].name() + ": " + pr.gives(), ix, y, iw, GOOD, maxY);
        }
    }

    // ================================================================== the walkthrough
    static final String[][] TOUR = {
        {"Welcome, Architect", "This book holds a fortress. Here's a quick tour - it only takes a moment."},
        {"The Floor Plan", "Your rooms go on this grid. Click an empty space, then pick a room to build. It costs Stone, which you earn by defeating foes."},
        {"Rooms and their spells", "Every room gives a spell. Built rooms fill the four Room pages of your book - change them on the Spells tab."},
        {"Neighbours", "Some rooms work together when placed side by side. Paired rooms are joined by a brass bar. The Guide lists every pair."},
        {"Siege Mode", "Cast Siege Mode from the book to raise walls and towers round you. Defeated foes give Stone, banners for your garrison and blueprints for new rooms."},
        {"You're ready", "The tracker at the top right shows your next goal. The Guide tab explains everything, and can replay this tour."},
    };

    private void tourOverlay(GuiGraphics g, int x0, int y0, int W, int H, int mx, int my) {
        hits.clear();   // only the walkthrough's buttons while it's up
        int step = Mth.clamp(tour, 0, TOUR.length - 1);
        int[] hl = switch (step) {   // what it points at
            case 1 -> new int[]{x0 + 7, y0 + 41, 5 * (CELL + GAP) + 7, 5 * (CELL + GAP) + 7};
            case 2 -> new int[]{x0 + (W - 84 * 4 - 12) / 2 + 2 * 88, y0 + 22, 84, 13};
            case 3 -> new int[]{x0 + (W - 84 * 4 - 12) / 2 + 3 * 88, y0 + 22, 84, 13};
            default -> null;
        };
        g.fill(x0, y0, x0 + W, y0 + H, 0x90000000);
        if (hl != null) {
            int a = (int) (0xA0 + 0x5F * Mth.sin(ticks * 0.3F));
            g.renderOutline(hl[0] - 2, hl[1] - 2, hl[2] + 4, hl[3] + 4, a << 24 | 0xE8B860);
            g.renderOutline(hl[0] - 3, hl[1] - 3, hl[2] + 6, hl[3] + 6, (a / 2) << 24 | 0xE8B860);
        }
        int bw = 220, bh = 78, bx = x0 + W - bw - 14, by = y0 + H - bh - 14;
        if (step == 0 || step == 4 || step == 5) { bx = x0 + (W - bw) / 2; by = y0 + (H - bh) / 2; }
        g.fill(bx, by, bx + bw, by + bh, 0xF0201A14);
        g.renderOutline(bx, by, bw, bh, BRASS);
        g.drawString(font, TOUR[step][0], bx + 8, by + 7, BRASS, false);
        String n = (step + 1) + " / " + TOUR.length;
        g.drawString(font, n, bx + bw - 8 - font.width(n), by + 7, DIM, false);
        text(g, TOUR[step][1], bx + 8, by + 20, bw - 16, TEXT, by + bh - 18);
        button(g, bx + bw - 68, by + bh - 17, 60, step == TOUR.length - 1 ? "Done" : "Next", true, mx, my, 14, 0);
        if (step < TOUR.length - 1) button(g, bx + 8, by + bh - 17, 60, "Skip", true, mx, my, 15, 0);
    }

    // ================================================================== input
    @Override
    public boolean mouseClicked(double mx, double my, int b) {
        for (int i = hits.size() - 1; i >= 0; i--) {
            int[] h = hits.get(i);
            if (mx < h[0] || mx >= h[2] || my < h[1] || my >= h[3]) continue;
            click(h[4], h[5]);
            return true;
        }
        return super.mouseClicked(mx, my, b);
    }

    private void click(int action, int arg) {
        int[] grid = FortressClient.grid();
        switch (action) {
            case 0 -> { tab = arg; listScroll = 0; }
            case 1 -> {
                if (moving >= 0) { if (arg != moving) FortressClient.send(2, moving * 100 + arg); sel = arg; moving = -1; }
                else { sel = sel == arg ? -1 : arg; listScroll = 0; }
            }
            case 2 -> FortressClient.send(1, arg * 100 + Math.max(0, sel));
            case 3 -> FortressClient.send(1, arg * 100 + Math.max(0, FortressClient.cellOf(arg)));
            case 4 -> { if (arg >= 0 && grid[arg] >= 0) moving = arg; }
            case 5 -> FortressClient.send(4, arg);
            case 6 -> FortressClient.send(9, arg);
            case 7 -> {   // cycle a defender's post through your built rooms
                var gar = FortressClient.garrison();
                if (arg < 0 || arg >= gar.size()) return;
                CompoundTag e = gar.getCompound(arg);
                int cur = e.getInt("post"), next = -1;
                for (int r = cur + 1; r < FortressFx.ROOMS; r++) if (FortressClient.roomLevel(r) > 0) { next = r; break; }
                FortressClient.send(6, e.getInt("id") * 100 + next + 1);
            }
            case 8 -> FortressClient.send(5, arg);
            case 9 -> slotSel = arg;
            case 10 -> FortressClient.send(3, slotSel * 100 + arg + 1);
            case 11 -> FortressClient.send(3, arg * 100);
            case 12 -> { guideTopic = arg; guideScroll = 0; }
            case 13 -> guideRoom = arg;
            case 14 -> { tour++; if (tour >= TOUR.length) { tour = -1; FortressClient.send(7, 0); FortressClient.tag.putBoolean("toured", true); } }
            case 15 -> { tour = -1; FortressClient.send(7, 0); FortressClient.tag.putBoolean("toured", true); }
            case 16 -> { tour = 0; tab = 0; }
            case 17 -> moving = -1;
            default -> {}
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double d) {
        int step = d < 0 ? 1 : -1;
        if (tab == 3) guideScroll = Math.max(0, guideScroll + step);
        else listScroll = Math.max(0, listScroll + step);
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE && moving >= 0) { moving = -1; return true; }
        return super.keyPressed(key, scan, mods);
    }
}
