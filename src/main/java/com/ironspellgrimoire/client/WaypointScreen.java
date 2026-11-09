package com.ironspellgrimoire.client;

import com.ironspellgrimoire.network.AddonNetwork;
import com.ironspellgrimoire.network.WaypointPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/** The Spatial Grimoire's waypoint menu: name and save where you stand, go to a saved place, or forget one. */
public class WaypointScreen extends Screen {
    private static final int ROWS = 7, ROW_H = 24, W = 260;
    private List<WaypointPackets.Entry> list;
    private int scroll = 0;
    private EditBox name;
    private static String draft = "";

    private WaypointScreen(List<WaypointPackets.Entry> list) {
        super(Component.literal("Waypoints"));
        this.list = list;
    }

    /** Opens the menu, or refreshes it if it is already open. */
    public static void show(List<WaypointPackets.Entry> list) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof WaypointScreen s) { s.list = list; s.scroll = Math.max(0, Math.min(s.scroll, Math.max(0, list.size() - ROWS))); s.rebuild(); }
        else mc.setScreen(new WaypointScreen(list));
    }

    @Override protected void init() { rebuild(); }

    private void rebuild() {
        if (name != null) draft = name.getValue();
        clearWidgets();
        int left = (width - W) / 2, top = height / 2 - 100;
        for (int i = 0; i < ROWS; i++) {
            int idx = scroll + i;
            if (idx >= list.size()) break;
            int y = top + 30 + i * ROW_H;
            addRenderableWidget(Button.builder(Component.literal("Go"), b -> { send(1, idx, ""); onClose(); }).bounds(left + W - 78, y, 36, 20).build());
            addRenderableWidget(Button.builder(Component.literal("X"), b -> send(2, idx, "")).bounds(left + W - 38, y, 22, 20).build());
        }
        addRenderableWidget(Button.builder(Component.literal("^"), b -> { scroll = Math.max(0, scroll - 1); rebuild(); }).bounds(left + W + 4, top + 30, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("v"), b -> { scroll = Math.min(Math.max(0, list.size() - ROWS), scroll + 1); rebuild(); }).bounds(left + W + 4, top + 30 + (ROWS - 1) * ROW_H, 20, 20).build());
        int by = top + 30 + ROWS * ROW_H + 8;
        name = new EditBox(font, left, by, W - 96, 20, Component.literal("Name"));
        name.setMaxLength(24);
        name.setHint(Component.literal("Name this place"));
        name.setValue(draft);
        addRenderableWidget(name);
        addRenderableWidget(Button.builder(Component.literal("Save here"), b -> { send(0, 0, name.getValue()); draft = ""; name.setValue(""); }).bounds(left + W - 92, by, 92, 20).build());
    }

    private void send(int action, int index, String n) {
        AddonNetwork.CHANNEL.sendToServer(new WaypointPackets.Act(action, index, n));
    }

    @Override public boolean mouseScrolled(double mx, double my, double d) {
        int ns = Math.max(0, Math.min(Math.max(0, list.size() - ROWS), scroll - (int) Math.signum(d)));
        if (ns != scroll) { scroll = ns; rebuild(); }
        return true;
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        int left = (width - W) / 2, top = height / 2 - 100;
        g.fill(left - 10, top - 8, left + W + 30, top + 30 + ROWS * ROW_H + 40, 0xE0090620);
        g.renderOutline(left - 10, top - 8, W + 40, 38 + ROWS * ROW_H + 40 - 0, 0xFFE7C766);
        g.drawCenteredString(font, "Spatial Waypoints", width / 2, top + 4, 0xFFE6DAFF);
        if (list.isEmpty()) g.drawCenteredString(font, "Nothing saved yet - name this place and save it.", width / 2, top + 60, 0xFF9C78FF);
        for (int i = 0; i < ROWS; i++) {
            int idx = scroll + i;
            if (idx >= list.size()) break;
            WaypointPackets.Entry e = list.get(idx);
            int y = top + 30 + i * ROW_H;
            g.fill(left, y, left + W - 82, y + 20, 0x80281E6A);
            String dim = e.dim().replace("minecraft:", "");
            g.drawString(font, e.name(), left + 5, y + 2, 0xFFE6DAFF, false);
            g.drawString(font, e.x() + ", " + e.y() + ", " + e.z() + "  " + dim, left + 5, y + 11, 0xFF9C78FF, false);
        }
        super.render(g, mx, my, pt);
    }
}
