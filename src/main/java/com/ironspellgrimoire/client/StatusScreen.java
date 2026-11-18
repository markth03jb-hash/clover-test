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
    /** 0 Status, 1 Titles, 2 Guide (the screenshots open on a tab). */
    public static int startTab = 0;
    private int tab = startTab;
    static final String[] TITLE_NAMES = {"Shadow Monarch", "Unbroken", "Peak", "Trialbreaker", "Ascendant", "Diligent", "Hoarder", "Lord of Generals"};
    static final String[] TITLE_HOW = {"Raise 100 shadows", "Survive a Reckoning", "Reach hunter level 100", "Pass the Job Change Trial",
            "Advance your job", "Complete 10 Daily Trials", "Earn 10000 Shade Marks", "Rank a shadow up to General"};
    static final String[] TITLE_GIVES = {"+20 max mana", "+2 armour", "+3% damage", "+5% attack speed", "+3% spell power", "+3% speed",
            "+5% mana regeneration", "+2 max health"};
    static final String[] JOBS2 = {"Lich Lord", "Phantom", "Conqueror"};

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
        int tx = px() + 14;
        for (int i = 0; i < 3; i++) {
            String[] names = {"STATUS", "TITLES", "GUIDE"};
            final int k = i;
            button(g, tx, py() + 37, 64, 13, names[i], true, tab == i ? 0xFF9AD0FF : EDGE_DIM, mx, my, () -> tab = k);
            tx += 68;
        }
        if (s.title >= 0) { String tt = "\"" + TITLE_NAMES[s.title] + "\""; g.drawString(font, tt, px() + pw() - 14 - font.width(tt), py() + 40, GOLD, false); }
        if (tab == 1) { titles(g, mx, my); return; }
        if (tab == 2) { guide(g); return; }
        int x0 = px() + 14, y0 = py() + 56, w = pw() - 28;
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
        g.drawString(font, "Left undone when the day turns: the Reckoning", px() + 14, py() + ph() - 14, 0xFF55678A, false);
        // the job (Job Change: a trial at hunter level 40, then a choice for good) - under the Daily Trial
        int jx = dx, jy = sy + NAMES.length * 22 + 2;
        if (s.job > 0 && s.advanced) {
            g.drawString(font, "Job: " + JOBS2[s.job - 1] + " (advanced)", jx, jy + 4, 0xFFD8A8FF, false);
        } else if (s.job > 0 && s.level >= 80) {
            g.drawString(font, "Job: " + JobScreen.NAMES[s.job - 1], jx, jy + 4, 0xFFD8A8FF, false);
            button(g, jx + dw - 84, jy, 84, 15, "ADVANCE", !s.reckoning, 0xFFB070FF, mx, my, () -> act(20, 0));
        } else if (s.job > 0) {
            g.drawString(font, "Job: " + JobScreen.NAMES[s.job - 1], jx, jy + 4, 0xFFD8A8FF, false);
            g.drawString(font, font.plainSubstrByWidth("Advancement at hunter level 80", dw), jx, jy + 15, 0xFF55678A, false);
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

    /** Titles: earned at milestones, one worn at a time. */
    private void titles(GuiGraphics g, int mx, int my) {
        int x0 = px() + 14, y0 = py() + 58, w = pw() - 28, rh = Math.min(26, (ph() - 80) / TITLE_NAMES.length);
        for (int i = 0; i < TITLE_NAMES.length; i++) {
            boolean have = (s.titles & (1 << i)) != 0, worn = s.title == i;
            int y = y0 + i * rh;
            g.fill(x0, y, x0 + w, y + rh - 3, PANEL);
            g.fill(x0, y, x0 + 2, y + rh - 3, have ? GOLD : EDGE_DIM);
            g.drawString(font, TITLE_NAMES[i], x0 + 8, y + 2, have ? (worn ? GOLD : TEXT) : 0xFF55678A, false);
            g.drawString(font, have ? TITLE_GIVES[i] : TITLE_HOW[i], x0 + 8, y + 12, have ? 0xFF9AB4D8 : 0xFF4A5A78, false);
            final int k = i;
            if (have) button(g, x0 + w - 70, y + 3, 64, 14, worn ? "TAKE OFF" : "WEAR", true, worn ? GOLD : EDGE, mx, my, () -> act(19, k));
            else g.drawString(font, "locked", x0 + w - 44, y + 7, 0xFF3A4660, false);
        }
    }

    /** The Guide: every System feature, how to use it, and what opens the ones not yet open. */
    private void guide(GuiGraphics g) {
        int x0 = px() + 14, y0 = py() + 58, w = pw() - 28;
        boolean job = s.level >= 40, adv = s.job > 0 && s.level >= 80;
        Object[][] rows = {
                {"Level & stats", "Kills with the grimoire (or your shadows) give XP; each level, 3 points to spend here.", true, ""},
                {"Daily Trial", "Three tasks a day for points, XP and Marks. Left undone: the Reckoning.", true, ""},
                {"Rise", "Cast Rise on a fallen foe's remnant to raise it as your shadow.", true, ""},
                {"Army (V)", "Equip, rank up, station and rename shadows; Challenges for exclusives.", true, ""},
                {"G wheel", "Hunt, Rally, Stand Down, Sink/Rise, Army, Status, Market, Loadout.", true, ""},
                {"Market", "Spend Shade Marks on weapons, tonics, lures and Reckoning keys.", true, ""},
                {"Bonds", "A shadow at your side for 50 kills gains a trait: Loyal, Ferocious or Cautious.", true, ""},
                {"Loadouts", "Save who stands at your side (Army, Loadouts tab); swap with the wheel.", s.army >= 1, "a shadow in your army"},
                {"Titles", "Earned at milestones; wear one here for its passive.", true, ""},
                {"Job Change", "Pass the trial, choose Necromancer, Assassin or Warlord - for good.", job, "hunter level 40"},
                {"Advancement", "A harder trial; your job takes its second form.", adv, "a job and hunter level 80"}};
        int rh = Math.min(18, (ph() - 76) / rows.length);
        for (int i = 0; i < rows.length; i++) {
            boolean open = (Boolean) rows[i][2];
            int y = y0 + i * rh;
            g.drawString(font, (String) rows[i][0], x0, y, open ? 0xFF9AD0FF : 0xFF55678A, false);
            String txt = open ? (String) rows[i][1] : "Opens with " + rows[i][3] + ".";
            float sc = Math.min(1f, (w - 92) / (float) font.width(txt));
            g.pose().pushPose();
            g.pose().translate(x0 + 92, y, 0);
            g.pose().scale(sc, sc, 1);
            g.drawString(font, txt, 0, 0, open ? TEXT : 0xFF4A5A78, false);
            g.pose().popPose();
        }
    }
}
