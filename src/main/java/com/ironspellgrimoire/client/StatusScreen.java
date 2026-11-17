package com.ironspellgrimoire.client;

import com.ironspellgrimoire.network.ShadowPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * The hunter's Status window (the Shadow Grimoire's "Status" page; server/HunterAscension): your level and XP, your
 * five stats with a + to spend each point, your Shade Marks, what your army gives you (Strength in Numbers), and today's
 * Daily Trial - its three tasks and how long the day has left - and the job (Job Change: begin the trial at hunter
 * level 40, then choose).
 */
public class StatusScreen extends ShadowUiScreen {
    static final String[] NAMES = {"Strength", "Agility", "Vitality", "Focus", "Perception"};
    static final String[] GIVES = {"+%.1f%% melee damage", "+%.1f%% speed", "+%.1f max health", "+%.0f max mana", "+%.1f%% spell power"};
    static final double[] PER = {0.4, 0.3, 0.5, 4, 0.4};
    static final int[] COL = {0xFFE07060, 0xFF70E0A0, 0xFFE0B060, 0xFF70A8FF, 0xFFC090FF};
    static final String[] TASKS = {"Slay %d foes", "Deal %d damage", "Travel %d blocks", "Raise %d shadows"};
    private ShadowPackets.Status s;

    StatusScreen() { super("STATUS"); }

    public static void show(ShadowPackets.Status p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof StatusScreen open) { open.s = p; return; }
        if (!p.open) return;
        StatusScreen n = new StatusScreen();
        n.s = p;
        mc.setScreen(n);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        float t = ticks + pt;
        backdrop(g, t, "STATUS");
        if (s == null) return;
        int x0 = px() + 14, y0 = py() + 42, w = pw() - 28;
        // level
        String lv = "Hunter level " + s.level;
        g.pose().pushPose();
        g.pose().translate(x0, y0, 0); g.pose().scale(1.5f, 1.5f, 1);
        g.drawString(font, lv, 0, 0, TEXT, false);
        g.pose().popPose();
        String mk = s.marks + " Shade Marks";
        g.drawString(font, mk, x0 + w - font.width(mk), y0 + 3, GOLD, false);
        bar(g, x0, y0 + 16, w, 5, s.level >= 100 ? 1 : s.xp / (float) Math.max(1, s.xpNext), 0xFF3C8CFF, 0xFF1A50C0);
        g.drawString(font, s.level >= 100 ? "highest level" : s.xp + " / " + s.xpNext + " xp", x0, y0 + 24, 0xFF5A6A88, false);
        String pts = s.points > 0 ? s.points + " points to spend" : "no points to spend";
        g.drawString(font, pts, x0 + w - font.width(pts), y0 + 24, s.points > 0 ? 0xFF9FE0FF : 0xFF5A6A88, false);
        // stats
        int sy = y0 + 40, colW = w / 2 - 6;
        for (int i = 0; i < NAMES.length; i++) {
            int y = sy + i * 22;
            g.fill(x0, y, x0 + colW, y + 19, PANEL);
            g.fill(x0, y, x0 + 2, y + 19, COL[i]);
            g.drawString(font, NAMES[i], x0 + 8, y + 2, TEXT, false);
            g.drawString(font, String.format(GIVES[i], s.stats[i] * PER[i]), x0 + 8, y + 11, 0xFF7F8EA8, false);
            String v = String.valueOf(s.stats[i]);
            g.drawString(font, v, x0 + colW - 30 - font.width(v), y + 6, COL[i], false);
            final int k = i;
            button(g, x0 + colW - 22, y + 2, 18, 15, "+", s.points > 0, EDGE, mx, my, () -> act(11, k));
        }
        // the army and the lures
        int ay = sy + NAMES.length * 22 + 4;
        int army = Math.min(50, s.army);
        g.drawString(font, "Strength in Numbers: " + s.army + " shadows", x0, ay, 0xFF9AB4D8, false);
        g.drawString(font, String.format("+%d mana, +%.1f%% spell power", army * 2, army * 0.3), x0, ay + 10, 0xFF5A6A88, false);
        if (s.lures > 0) g.drawString(font, s.lures + " Remnant Lure" + (s.lures == 1 ? "" : "s") + " ready", x0, ay + 22, GOLD, false);

        // the Daily Trial
        int dx = x0 + colW + 12, dw = w - colW - 12;
        g.fill(dx, sy, dx + dw, sy + NAMES.length * 22 - 3, PANEL);
        frame(g, dx, sy, dw, NAMES.length * 22 - 3, s.trialDone ? 0xFF3C78E0 : 0x80E8C070);
        g.drawString(font, "DAILY TRIAL", dx + 8, sy + 6, GOLD, false);
        int left = s.dayLeft;
        String tl = s.reckoning ? "the Reckoning!" : left / 60 + "m " + left % 60 + "s left";
        g.drawString(font, tl, dx + dw - 8 - font.width(tl), sy + 6, s.reckoning ? 0xFFE05050 : 0xFF5A6A88, false);
        for (int i = 0; i < s.kinds.length; i++) {
            int y = sy + 22 + i * 24;
            boolean done = s.prog[i] >= s.goal[i];
            g.drawString(font, String.format(TASKS[Math.min(3, s.kinds[i])], s.goal[i]), dx + 8, y, done ? 0xFF9FE0FF : TEXT, false);
            bar(g, dx + 8, y + 11, dw - 16, 4, s.prog[i] / (float) Math.max(1, s.goal[i]), done ? 0xFF7FD8FF : 0xFFE8C070, done ? 0xFF3C8CFF : 0xFFA07830);
        }
        String rw = s.trialDone ? "Complete - come back tomorrow" : "All three: +5 points, XP, 200 Marks";
        g.drawString(font, font.plainSubstrByWidth(rw, dw - 16), dx + 8, sy + NAMES.length * 22 - 16, s.trialDone ? 0xFF9FE0FF : 0xFF7F8EA8, false);
        g.drawString(font, "Left undone when the day turns: the Reckoning", px() + 14, py() + ph() - 16, 0xFF55678A, false);
        // the job (Job Change: a trial at hunter level 40, then a choice for good) - under the Daily Trial
        int jx = dx, jy = sy + NAMES.length * 22 + 2;
        if (s.job > 0) {
            g.drawString(font, "Job: " + JobScreen.NAMES[s.job - 1], jx, jy + 4, 0xFFD8A8FF, false);
        } else if (s.jobReady) {
            g.drawString(font, "Trial passed", jx, jy + 4, 0xFFD8A8FF, false);
            button(g, jx + dw - 84, jy, 84, 15, "CHOOSE JOB", true, 0xFFB070FF, mx, my, () -> JobScreen.open(s));
        } else if (s.level >= 40) {
            g.drawString(font, "Job Change", jx, jy + 4, 0xFFD8A8FF, false);
            button(g, jx + dw - 84, jy, 84, 15, "BEGIN TRIAL", !s.reckoning, 0xFFB070FF, mx, my, () -> act(14, 0));
        } else {
            g.drawString(font, font.plainSubstrByWidth("Job Change at hunter level 40", dw), jx, jy + 4, 0xFF55678A, false);
        }
    }
}
