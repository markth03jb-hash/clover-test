package com.ironspellgrimoire.client;

import com.ironspellgrimoire.network.ShadowPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Job Change: the choice, once the Job Change Trial is passed (server/HunterAscension) - three paths, chosen for good.
 */
public class JobScreen extends ShadowUiScreen {
    static final String[] NAMES = {"Necromancer", "Assassin", "Warlord"};
    static final String[][] LINES = {
            {"Master of the army", "Shadows hit 25% harder", "+20% max mana"},
            {"Strike from the dark", "Knives hit 25% harder", "+10% speed, +15% attack speed"},
            {"Stand at the front", "Greatswords hit 30% harder", "+4 hearts, +4 armour, steady footing"}};
    static final int[] ACCENT = {0xFF7A60FF, 0xFF40D0A0, 0xFFE0A040};

    JobScreen() { super("JOB CHANGE"); }

    public static void show(ShadowPackets.Status p) { Minecraft.getInstance().setScreen(new JobScreen()); }
    static void open(ShadowPackets.Status p) { Minecraft.getInstance().setScreen(new JobScreen()); }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        float t = ticks + pt;
        backdrop(g, t, "JOB CHANGE");
        int x0 = px() + 14, y0 = py() + 42, w = pw() - 28;
        g.drawString(font, "Choose your path. It is chosen for good.", x0, y0, 0xFF9AB4D8, false);
        int gap = 8, cw = (w - gap * 2) / 3, ch = Math.min(150, ph() - 42 - 40);
        for (int i = 0; i < 3; i++) {
            int x = x0 + i * (cw + gap), y = y0 + 16;
            g.fill(x, y, x + cw, y + ch, PANEL);
            frame(g, x, y, cw, ch, ACCENT[i]);
            g.fill(x, y, x + cw, y + 3, ACCENT[i]);
            g.drawString(font, NAMES[i], x + (cw - font.width(NAMES[i])) / 2, y + 12, TEXT, false);
            int ly = y + 30;
            for (int k = 0; k < LINES[i].length; k++) {   // each line wrapped to the card, the next starting below it
                for (var line : font.split(net.minecraft.network.chat.Component.literal(LINES[i][k]), cw - 12)) {
                    g.drawString(font, line, x + 6, ly, k == 0 ? 0xFF9AB4D8 : 0xFF7F8EA8, false);
                    ly += 10;
                }
                ly += 6;
            }
            final int id = i + 1;
            button(g, x + (cw - 60) / 2, y + ch - 22, 60, 15, "CHOOSE", true, ACCENT[i], mx, my, () -> act(15, id));
        }
    }
}
