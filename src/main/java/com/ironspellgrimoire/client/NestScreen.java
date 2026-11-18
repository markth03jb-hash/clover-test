package com.ironspellgrimoire.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** The Hive Network's travel menu: your nests - where each is, and whether it can be reached yet. Pick one to sink
 *  into the ground here and burst out there (server/ParasiteFx.nestTravel). */
public class NestScreen extends Screen {
    private final List<String[]> rows = new ArrayList<>();
    private record Hit(int x, int y, int w, int h, Runnable run) { boolean in(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; } }
    private final List<Hit> hits = new ArrayList<>();

    NestScreen(List<String> rows) { super(Component.literal("Hive Network")); for (String r : rows) this.rows.add(r.split("\\|")); }

    public static void show(List<String> rows) { Minecraft.getInstance().setScreen(new NestScreen(rows)); }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        hits.clear();
        int W = Math.min(260, width - 16), rowH = 26, H = 30 + rows.size() * (rowH + 4) + 6;
        int x0 = (width - W) / 2, y0 = (height - H) / 2;
        FleshSkin.panel(g, x0, y0, W, H, (Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime()) + pt, mx, my, true);
        g.drawString(font, "HIVE NETWORK", x0 + 8, y0 + 8, 0xFFFF6070, true);
        int y = y0 + 26;
        for (int i = 0; i < rows.size(); i++) {
            String[] r = rows.get(i);
            if (r.length < 6) continue;
            boolean here = "1".equals(r[5]);
            int cd = Integer.parseInt(r[4]);
            boolean ok = !here && cd == 0, hv = ok && mx >= x0 + 6 && mx < x0 + W - 6 && my >= y && my < y + rowH;
            g.fill(x0 + 6, y, x0 + W - 6, y + rowH, hv ? 0xD0501820 : 0xC0200C10);
            g.renderOutline(x0 + 6, y, W - 12, rowH, here ? 0xFFFF6070 : ok ? EvolutionScreen.EDGE : EvolutionScreen.EDGE_DIM);
            g.drawString(font, "Nest " + (i + 1) + " - " + r[0], x0 + 11, y + 4, EvolutionScreen.TEXT, false);
            g.drawString(font, r[1] + ", " + r[2] + ", " + r[3], x0 + 11, y + 14, EvolutionScreen.MUTED, false);
            String tag = here ? "you are here" : cd > 0 ? "ready in " + cd + " s" : "travel";
            g.drawString(font, tag, x0 + W - 11 - font.width(tag), y + 9, here ? 0xFFFF9090 : ok ? EvolutionScreen.GOLD : EvolutionScreen.MUTED, false);
            if (ok) { final int idx = i; hits.add(new Hit(x0 + 6, y, W - 12, rowH, () -> { ParasiteClient.send(28, idx); onClose(); })); }
            y += rowH + 4;
        }
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) for (Hit h : hits) if (h.in(mx, my)) { h.run().run(); return true; }
        return super.mouseClicked(mx, my, button);
    }
}
