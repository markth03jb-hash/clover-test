package com.ironspellgrimoire.client;

import com.ironspellgrimoire.network.AddonNetwork;
import com.ironspellgrimoire.network.WaypointPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.List;

/**
 * The Spatial Grimoire's waypoint menu, drawn entirely in the grimoire's own style (Finral's violet and gold): a dark
 * panel with a gilded frame and drifting motes, rows you click to open a gate, and no vanilla buttons anywhere.
 */
public class WaypointScreen extends Screen {
    private static final int ROWS = 7, ROW_H = 26, W = 270;
    private static final int GOLD = 0xFFE7C766, VIOLET = 0xFF6A48D8, LILAC = 0xFFC8B4FF, INK = 0xF0090620, TEXT = 0xFFE6DAFF, DIM = 0xFF9C78FF;
    private List<WaypointPackets.Entry> list;
    private int scroll = 0;
    private EditBox name;
    private static String draft = "";
    private long opened = -1;

    private WaypointScreen(List<WaypointPackets.Entry> list) {
        super(Component.literal("Waypoints"));
        this.list = list;
    }

    /** Opens the menu, or refreshes it if it is already open. */
    public static void show(List<WaypointPackets.Entry> list) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof WaypointScreen s) { s.list = list; s.scroll = Math.max(0, Math.min(s.scroll, Math.max(0, list.size() - ROWS))); }
        else mc.setScreen(new WaypointScreen(list));
    }

    private int left() { return (width - W) / 2; }
    private int top() { return height / 2 - 110; }
    private int listTop() { return top() + 34; }
    private int saveY() { return listTop() + ROWS * ROW_H + 10; }

    @Override protected void init() {
        int by = saveY();
        name = new EditBox(font, left() + 8, by + 6, W - 120, 12, Component.literal("Name"));
        name.setBordered(false);
        name.setMaxLength(24);
        name.setHint(Component.literal("Name this place"));
        name.setTextColor(TEXT);
        name.setValue(draft);
        addRenderableWidget(name);
    }

    private void send(int action, int index, String n) {
        AddonNetwork.CHANNEL.sendToServer(new WaypointPackets.Act(action, index, n));
    }

    private static boolean in(double mx, double my, int x, int y, int w, int h) { return mx >= x && mx < x + w && my >= y && my < y + h; }

    @Override public boolean mouseClicked(double mx, double my, int btn) {
        if (btn == 0) {
            int l = left(), lt = listTop();
            for (int i = 0; i < ROWS; i++) {
                int idx = scroll + i;
                if (idx >= list.size()) break;
                int y = lt + i * ROW_H;
                if (in(mx, my, l + W - 30, y, 24, ROW_H - 4)) { send(2, idx, ""); return true; }       // forget
                if (in(mx, my, l + 4, y, W - 40, ROW_H - 4)) { send(1, idx, ""); onClose(); return true; }   // open a gate
            }
            int by = saveY();
            if (in(mx, my, l + W - 104, by, 96, 24)) { draft = ""; send(0, 0, name.getValue()); name.setValue(""); return true; }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override public boolean mouseScrolled(double mx, double my, double d) {
        scroll = Math.max(0, Math.min(Math.max(0, list.size() - ROWS), scroll - (int) Math.signum(d)));
        return true;
    }

    @Override public boolean keyPressed(int key, int sc, int mods) {
        if (name != null && name.isFocused() && (key == 257 || key == 335)) { draft = ""; send(0, 0, name.getValue()); name.setValue(""); return true; }
        return super.keyPressed(key, sc, mods);
    }

    @Override public void removed() { if (name != null) draft = name.getValue(); }

    @Override public boolean isPauseScreen() { return false; }

    /** A gilded frame: dark inside, a gold line, a violet inner line and diamond studs at the corners. */
    private static void frame(GuiGraphics g, int x, int y, int w, int h, int fill, int edge, int inner) {
        g.fill(x, y, x + w, y + h, fill);
        g.renderOutline(x, y, w, h, edge);
        g.renderOutline(x + 2, y + 2, w - 4, h - 4, inner);
        for (int[] c : new int[][]{{x, y}, {x + w, y}, {x, y + h}, {x + w, y + h}}) {
            g.fill(c[0] - 2, c[1] - 1, c[0] + 3, c[1] + 2, edge);
            g.fill(c[0] - 1, c[1] - 2, c[0] + 2, c[1] + 3, edge);
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        if (opened < 0 && minecraft != null && minecraft.level != null) opened = minecraft.level.getGameTime();
        double time = minecraft != null && minecraft.level != null ? minecraft.level.getGameTime() + pt : 0;
        float open = Mth.clamp((float) ((time - opened) / 6.0), 0F, 1F);
        g.fill(0, 0, width, height, (int) (0x90 * open) << 24 | 0x04020E);   // dim the world, deep violet
        int l = left(), t = top();
        int pw = W + 16, ph = 34 + ROWS * ROW_H + 48;
        int px = l - 8, py = t - 4;
        // drifting motes of space behind the panel
        for (int i = 0; i < 26; i++) {
            double a = i * 2.399 + time * 0.01 * (1 + i % 3 * 0.3);
            int x = px + (int) ((Math.sin(a * 1.3 + i) * 0.5 + 0.5) * pw), y = py + (int) (((time * (0.12 + i % 4 * 0.05) + i * 37) % ph));
            int al = (int) (60 + 60 * Math.sin(time * 0.1 + i));
            g.fill(x, y, x + 1, y + 1, al << 24 | 0xC8B4FF);
        }
        frame(g, px, py, pw, ph, INK, GOLD, VIOLET);
        g.fill(px + 4, py + 4, px + pw - 4, py + 22, 0x40_6A48D8);
        // title with a gold rule and a small diamond either side
        g.drawCenteredString(font, "S P A T I A L   W A Y P O I N T S", width / 2, py + 9, GOLD);
        g.fill(px + 14, py + 14, px + 24, py + 15, GOLD); g.fill(px + pw - 24, py + 14, px + pw - 14, py + 15, GOLD);

        int lt = listTop();
        if (list.isEmpty()) {
            g.drawCenteredString(font, "No places saved yet.", width / 2, lt + 40, DIM);
            g.drawCenteredString(font, "Name where you stand below and save it.", width / 2, lt + 54, 0xFF7A5CD0);
        }
        for (int i = 0; i < ROWS; i++) {
            int idx = scroll + i;
            if (idx >= list.size()) break;
            WaypointPackets.Entry e = list.get(idx);
            int y = lt + i * ROW_H;
            boolean hot = in(mx, my, l + 4, y, W - 40, ROW_H - 4), del = in(mx, my, l + W - 30, y, 24, ROW_H - 4);
            double pulse = 0.5 + 0.5 * Math.sin(time * 0.2);
            g.fill(l + 4, y, l + W - 34, y + ROW_H - 4, hot ? 0xC03A2A8A : 0x90281E6A);
            g.fill(l + 4, y, l + 7, y + ROW_H - 4, hot ? GOLD : VIOLET);                 // the stripe lights up gold
            if (hot) g.renderOutline(l + 4, y, W - 38, ROW_H - 4, (int) (140 + 100 * pulse) << 24 | 0xE7C766);
            String dim = e.dim().replace("minecraft:", "");
            g.drawString(font, e.name(), l + 14, y + 3, hot ? 0xFFFFFFFF : TEXT, false);
            g.drawString(font, e.x() + ", " + e.y() + ", " + e.z() + "   " + dim, l + 14, y + 13, DIM, false);
            if (hot) g.drawString(font, "OPEN GATE >", l + W - 100, y + 8, GOLD, false);
            g.fill(l + W - 30, y, l + W - 6, y + ROW_H - 4, del ? 0xD0A01E34 : 0x80400A18);   // forget
            g.renderOutline(l + W - 30, y, 24, ROW_H - 4, del ? 0xFFFF8090 : 0xFF8A2A3A);
            g.drawCenteredString(font, "x", l + W - 18, y + 7, del ? 0xFFFFFFFF : 0xFFFF8090);
        }
        // a slim scroll rail
        if (list.size() > ROWS) {
            int rx = l + W + 1, rh = ROWS * ROW_H - 4;
            g.fill(rx, lt, rx + 3, lt + rh, 0x80281E6A);
            int kh = Math.max(12, rh * ROWS / list.size());
            int ky = lt + (rh - kh) * scroll / Math.max(1, list.size() - ROWS);
            g.fill(rx, ky, rx + 3, ky + kh, GOLD);
        }
        // the name field and the save plate
        int by = saveY();
        g.fill(l, by, l + W - 112, by + 24, 0xB0100620);
        g.renderOutline(l, by, W - 112, 24, name != null && name.isFocused() ? GOLD : VIOLET);
        boolean sv = in(mx, my, l + W - 104, by, 96, 24);
        g.fill(l + W - 104, by, l + W - 8, by + 24, sv ? 0xE06A48D8 : 0xA04A32B0);
        g.renderOutline(l + W - 104, by, 96, 24, sv ? 0xFFFFFFFF : GOLD);
        g.drawCenteredString(font, "SAVE HERE", l + W - 56, by + 8, sv ? 0xFFFFFFFF : LILAC);
        g.drawCenteredString(font, "Click a place to open a gate to it - it stays open, and anyone can walk through.", width / 2, by + 32, 0xFF7A5CD0);
        super.render(g, mx, my, pt);
    }
}
