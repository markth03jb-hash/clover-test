package com.ironspellgrimoire.client;

import com.ironspellgrimoire.entity.ShadowSoldier;
import com.ironspellgrimoire.network.AddonNetwork;
import com.ironspellgrimoire.network.ShadowPackets;
import com.ironspellgrimoire.registry.AddonEntities;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Shadow Army menu (key V) - drawn entirely by hand, no Minecraft widgets: black smoke and blue fire.
 *
 *  ARMY        your raised shadows as cards on the left (rank pips, level, a burning eye if it's at your side); the
 *              chosen one stands on the right, risen out of its pool as a live model that follows your mouse, with
 *              its rank, XP, what its rank gives it and its actions - Equip / Rank up / Rename / Release (twice to
 *              confirm). Ranks go Soldier to General; the army holds one General and three Captains.
 *  CHALLENGES  the exclusive shadows (Oathbound Knight, Ravener) in their living colours: challenge one, beat it, and
 *              raise it (Rise on its remnant, or Arise here).
 */
public class ShadowArmyScreen extends Screen {
    public static final KeyMapping KEY = new KeyMapping("key.iron_spell_grimoire.shadow_army", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, "key.categories.iron_spell_grimoire");
    static final String[] RANKS = {"Soldier", "Elite", "Knight", "Captain", "General"};
    static final int[] RANK_COL = {0xFF8FA6C8, 0xFF6FB6FF, 0xFF4FA0FF, 0xFF7FD8FF, 0xFFE8F6FF};
    static final String[] PERK = {"No perk yet", "Elite: heavier armour", "Knight: its blows cleave into what stands beside", "Captain: a war cry - shadows round it hit harder",
            "General: it drinks a little of every blow"};
    static final String[] EXCL = {"Oathbound Knight", "Ravener"};
    static final String[] EXCL_TYPE = {"iron_spell_grimoire:oathbound_knight", "iron_spell_grimoire:ravener"};
    static final String[][] EXCL_TEXT = {
            {"A towering swordmaster in steel and gold.", "Oath Cleave - a wide greatsword sweep every 8 s.", "Sworn Guard - when you fall low it steps", "out at your side and draws every foe onto itself."},
            {"A tall stalker with long ivory talons.", "Pounce - leaps onto foes up to 14 blocks off.", "Devour - every kill heals it by a quarter.", "Frenzy - hurt below half, it runs wild for 8 s."}};

    // colours
    static final int INK = 0xF0070910, PANEL = 0xD00C1220, EDGE = 0xFF3C78E0, EDGE_DIM = 0xFF1C3460, TEXT = 0xFFE6F0FF, MUTED = 0xFF7F8EA8, GOLD = 0xFFE8C070;

    /** The tab a fresh menu opens on. */
    public static int startTab = 0;

    /** Switch the open menu to a tab (the screenshots use it). */
    public static void showTab(int which) {
        if (Minecraft.getInstance().screen instanceof ShadowArmyScreen s) { s.tab = which; s.renaming = false; }
    }

    private List<ShadowPackets.Entry> list = new ArrayList<>();
    private int slots, storage, scroll, tab = startTab, selected = -1, releaseArmed = -1;
    private int[] challenges = new int[0];
    private boolean renaming;
    private String nameBuf = "";
    private int ticks;
    private final List<Hit> hits = new ArrayList<>();
    private final Map<String, ShadowSoldier> models = new HashMap<>();

    private record Hit(int x, int y, int w, int h, Runnable run) {
        boolean in(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }

    ShadowArmyScreen() { super(Component.literal("Shadow Army")); }

    /** The server sent the army: open the menu, or refresh it if it's open. */
    public static void show(ShadowPackets.Open p) {
        Minecraft mc = Minecraft.getInstance();
        ShadowArmyScreen s = mc.screen instanceof ShadowArmyScreen open ? open : new ShadowArmyScreen();
        s.list = p.list; s.slots = p.slots; s.storage = p.storage; s.challenges = p.challenges;
        boolean found = false;
        for (ShadowPackets.Entry e : s.list) if (e.id() == s.selected) found = true;
        if (!found) s.selected = s.list.isEmpty() ? -1 : s.list.get(0).id();
        if (mc.screen != s) mc.setScreen(s);
    }

    private static void act(int action, int id, String name) { AddonNetwork.CHANNEL.sendToServer(new ShadowPackets.Act(action, id, name)); }

    private ShadowPackets.Entry sel() {
        for (ShadowPackets.Entry e : list) if (e.id() == selected) return e;
        return null;
    }

    // ---------------------------------------------------------------------------------------------------- layout
    private int pw() { return Math.min(520, width - 24); }
    private int ph() { return Math.min(330, height - 24); }
    private int px() { return (width - pw()) / 2; }
    private int py() { return (height - ph()) / 2; }
    private static final int CARD = 30, LIST_W = 176;
    private int listTop() { return py() + 58; }
    private int visibleCards() { return Math.max(1, (py() + ph() - 30 - listTop()) / CARD); }

    @Override public void tick() { ticks++; for (ShadowSoldier m : models.values()) m.tickCount++; }

    // ---------------------------------------------------------------------------------------------------- drawing
    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        hits.clear();
        float t = ticks + pt;
        // the dark: a deep vignette over the world, smoke drifting up behind the panel
        g.fillGradient(0, 0, width, height, 0xC0020308, 0xE0050A18);
        for (int k = 0; k < 14; k++) {
            float ph = k * 1.7f;
            int sx = (int) (width * ((k * 0.137f + 0.05f) % 1f) + Mth.sin(t * 0.02f + ph) * 18);
            int sy = (int) (height - ((t * (0.25f + k % 3 * 0.1f) + k * 47) % (height + 80)));
            int a = (int) (40 + 30 * Mth.sin(t * 0.05f + ph));
            g.fillGradient(sx - 10, sy - 60, sx + 10, sy + 20, 0x002050C0, (a << 24) | 0x1A3C90);
        }
        int x0 = px(), y0 = py(), w = pw(), h = ph();
        // the panel: ink, a blue rim that breathes, corner marks
        g.fill(x0, y0, x0 + w, y0 + h, INK);
        int rim = lerpCol(EDGE_DIM, EDGE, 0.5f + 0.5f * Mth.sin(t * 0.08f));
        frame(g, x0, y0, w, h, rim);
        frame(g, x0 + 3, y0 + 3, w - 6, h - 6, 0x402A50A0);
        for (int[] c : new int[][]{{x0, y0}, {x0 + w - 10, y0}, {x0, y0 + h - 10}, {x0 + w - 10, y0 + h - 10}}) g.fill(c[0] + 3, c[1] + 3, c[0] + 7, c[1] + 7, 0xFF8CC8FF);
        // the title, lit from behind
        String title = "SHADOW ARMY";
        int tw = font.width(title) * 2;
        g.pose().pushPose();
        g.pose().translate(x0 + w / 2f - tw / 2f, y0 + 10, 0);
        g.pose().scale(2, 2, 1);
        g.drawString(font, title, 1, 1, 0xFF1A3C90, false);
        g.drawString(font, title, 0, 0, 0xFF9AD0FF, false);
        g.pose().popPose();
        // tabs
        tab(g, x0 + 14, y0 + 34, "ARMY", 0, mx, my);
        tab(g, x0 + 84, y0 + 34, "CHALLENGES", 1, mx, my);
        int gens = 0, caps = 0, eq = 0;
        for (ShadowPackets.Entry e : list) { if (e.rank() == 4) gens++; if (e.rank() == 3) caps++; if (e.equipped() && !e.exclusive()) eq++; }
        String counts = list.size() + "/" + storage + " raised   " + eq + "/" + slots + " at your side";
        g.drawString(font, counts, x0 + w - 14 - font.width(counts), y0 + 38, MUTED, false);
        g.fill(x0 + 10, y0 + 50, x0 + w - 10, y0 + 51, 0x803C78E0);

        if (tab == 0) army(g, mx, my, t);
        else challengesTab(g, mx, my, t);

        // footer
        String foot = tab == 0 ? "Generals " + gens + "/1    Captains " + caps + "/3    scroll the list    V to close" : "Beat its living form, then raise it    V to close";
        g.drawString(font, foot, x0 + 14, y0 + h - 16, 0xFF55678A, false);
    }

    private void army(GuiGraphics g, int mx, int my, float t) {
        int x0 = px() + 10, y0 = listTop(), n = visibleCards();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, list.size() - n)));
        if (list.isEmpty()) {
            g.drawCenteredString(font, "No shadows yet.", px() + pw() / 2, y0 + 40, TEXT);
            g.drawCenteredString(font, "Slay something while holding the Shadow Grimoire,", px() + pw() / 2, y0 + 56, MUTED);
            g.drawCenteredString(font, "then cast Rise on its remnant before it fades.", px() + pw() / 2, y0 + 68, MUTED);
            return;
        }
        // the cards
        for (int i = scroll; i < Math.min(list.size(), scroll + n); i++) {
            ShadowPackets.Entry e = list.get(i);
            int y = y0 + (i - scroll) * CARD;
            boolean on = e.id() == selected, hov = in(mx, my, x0, y, LIST_W, CARD - 3);
            int rc = RANK_COL[Math.min(4, e.rank())];
            g.fill(x0, y, x0 + LIST_W, y + CARD - 3, on ? 0xC0142A58 : hov ? 0x9012203E : 0x700E1628);
            g.fill(x0, y, x0 + 2, y + CARD - 3, rc);
            if (on) frame(g, x0, y, LIST_W, CARD - 3, 0xFF5A9CFF);
            String nm = font.plainSubstrByWidth(e.name(), LIST_W - 46);
            g.drawString(font, nm, x0 + 8, y + 4, e.exclusive() ? GOLD : TEXT, false);
            g.drawString(font, "Lv " + e.level() + "  " + RANKS[Math.min(4, e.rank())], x0 + 8, y + 15, rc, false);
            pips(g, x0 + LIST_W - 8, y + 6, e.rank(), rc);
            if (e.equipped()) eye(g, x0 + LIST_W - 12, y + 16, t);
            if (e.recovering() > 0) g.drawString(font, e.recovering() + "s", x0 + LIST_W - 40, y + 15, 0xFF5A6A88, false);
            if (e.canRankUp()) g.fill(x0 + LIST_W - 4, y + 2, x0 + LIST_W - 2, y + CARD - 5, 0xFF9FE0FF);
            final int id = e.id();
            hits.add(new Hit(x0, y, LIST_W, CARD - 3, () -> { if (selected != id) { selected = id; renaming = false; releaseArmed = -1; } }));
        }
        if (list.size() > n) {   // a scroll rail
            int rx = x0 + LIST_W + 3, rh = n * CARD - 3, bh = Math.max(12, rh * n / list.size()), by = y0 + (rh - bh) * scroll / Math.max(1, list.size() - n);
            g.fill(rx, y0, rx + 2, y0 + rh, 0x401C3460);
            g.fill(rx, by, rx + 2, by + bh, 0xFF3C78E0);
        }
        // the chosen shadow
        ShadowPackets.Entry e = sel();
        if (e == null) return;
        int dx = x0 + LIST_W + 12, dw = px() + pw() - 10 - dx, dy = y0, dh = n * CARD - 3;
        g.fill(dx, dy, dx + dw, dy + dh, PANEL);
        frame(g, dx, dy, dw, dh, 0x603C78E0);
        int stageW = 120, cx = dx + stageW / 2, floor = dy + dh - 24;
        pool(g, cx, floor, t);
        ShadowSoldier m = model(e.type(), e.rank(), false);
        if (m != null) {
            int sc = (int) Math.min(48, 112 / Math.max(1f, m.getBbHeight() * m.rankScale()));
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, cx, floor, sc, cx - mx, floor - 60 - my, m);
        }
        int ix = dx + stageW + 4, iy = dy + 8;
        int rc = RANK_COL[Math.min(4, e.rank())];
        if (renaming) {   // the name, being typed
            g.fill(ix - 2, iy - 2, dx + dw - 8, iy + 11, 0xFF0A1830);
            frame(g, ix - 2, iy - 2, dx + dw - 8 - (ix - 2), 13, 0xFF5A9CFF);
            g.drawString(font, nameBuf + ((ticks / 8) % 2 == 0 ? "_" : ""), ix + 1, iy, TEXT, false);
        } else g.drawString(font, e.name(), ix, iy, e.exclusive() ? GOLD : TEXT, false);
        String what = e.exclusive() ? "Exclusive shadow" : "Shadow of " + typeName(e.type());
        g.drawString(font, what, ix, iy + 13, MUTED, false);
        g.drawString(font, RANKS[Math.min(4, e.rank())] + "  -  Level " + e.level(), ix, iy + 28, rc, false);
        pips(g, dx + dw - 12, iy + 29, e.rank(), rc);
        int bw = dw - stageW - 16;   // XP
        g.fill(ix, iy + 40, ix + bw, iy + 44, 0xFF101828);
        float f = e.xpNext() <= 0 ? 1 : Math.min(1f, e.xp() / (float) e.xpNext());
        g.fillGradient(ix, iy + 40, ix + (int) (bw * f), iy + 44, 0xFF3C8CFF, 0xFF1A50C0);
        g.drawString(font, e.level() >= 30 ? "max level" : e.xp() + " / " + e.xpNext() + " xp", ix, iy + 47, 0xFF5A6A88, false);
        int ty = iy + 62;
        for (var line : font.getSplitter().splitLines(PERK[Math.min(4, e.rank())], bw, net.minecraft.network.chat.Style.EMPTY)) {
            g.drawString(font, line.getString(), ix, ty, 0xFF9AB4D8, false); ty += 10;
        }
        if (e.exclusive()) {
            int k = EXCL_TYPE[0].equals(e.type()) ? 0 : 1;
            for (int i = 1; i < EXCL_TEXT[k].length; i++) { g.drawString(font, font.plainSubstrByWidth(EXCL_TEXT[k][i], bw), ix, ty, 0xFFC8B890, false); ty += 10; }
        }
        String status = e.recovering() > 0 ? "Reforming in its master's shadow - " + e.recovering() + "s"
                : e.rank() >= 4 ? "Highest rank" : e.rankBlock() == 1 ? "Your army already has its General" : e.rankBlock() == 2 ? "All three Captain ranks are taken"
                : e.canRankUp() ? "Ready to rank up!" : "Next rank at level " + nextLevel(e.rank());
        g.drawString(font, font.plainSubstrByWidth(status, bw), ix, dy + dh - 46, e.canRankUp() ? 0xFF9FE0FF : 0xFF6A7A98, false);
        // actions
        int by = dy + dh - 30, gap = 4, bwid = (dx + dw - 8 - ix - gap * 3) / 4;
        int bx = ix;
        button(g, bx, by, bwid, 18, e.equipped() ? "UNEQUIP" : "EQUIP", true, EDGE, mx, my, () -> act(1, e.id(), "")); bx += bwid + gap;
        button(g, bx, by, bwid, 18, "RANK UP", e.canRankUp(), 0xFF7FD8FF, mx, my, () -> act(2, e.id(), "")); bx += bwid + gap;
        button(g, bx, by, bwid, 18, renaming ? "KEEP" : "RENAME", true, EDGE, mx, my, () -> {
            if (renaming) commitName(); else { renaming = true; nameBuf = e.name(); }
        }); bx += bwid + gap;
        boolean armed = releaseArmed == e.id();
        button(g, bx, by, bwid, 18, e.exclusive() ? "SWORN" : armed ? "SURE?" : "RELEASE", !e.exclusive(), armed ? 0xFFE05050 : 0xFF8A5060, mx, my, () -> {
            if (releaseArmed == e.id()) { act(4, e.id(), ""); releaseArmed = -1; } else releaseArmed = e.id();
        });
    }

    private void challengesTab(GuiGraphics g, int mx, int my, float t) {
        int n = challenges.length, x0 = px() + 10, y0 = listTop(), gap = 10;
        int cw = (pw() - 20 - gap * Math.max(0, n - 1)) / Math.max(1, n), chh = ph() - 58 - 30;
        for (int i = 0; i < n; i++) {
            int x = x0 + i * (cw + gap), st = challenges[i];
            boolean won = st == 3;
            g.fill(x, y0, x + cw, y0 + chh, won ? 0xC00E1A34 : 0xC0181208);
            frame(g, x, y0, cw, chh, won ? 0xFF3C78E0 : lerpCol(0xFF6A5020, GOLD, 0.5f + 0.5f * Mth.sin(t * 0.07f + i)));
            g.drawString(font, EXCL[i], x + 10, y0 + 8, won ? 0xFFB8E8FF : GOLD, false);
            g.drawString(font, won ? "Sworn to you" : st == 2 ? "Beaten - raise it" : st == 1 ? "Fighting you now" : "Its living form awaits", x + 10, y0 + 19, MUTED, false);
            int cx = x + cw / 2, floor = y0 + Math.min(150, chh - 80);
            if (won) pool(g, cx, floor, t);
            else g.fillGradient(cx - 40, floor - 4, cx + 40, floor + 2, 0x00000000, 0x80403020);
            ShadowSoldier m = model(EXCL_TYPE[i], 2, !won);
            if (m != null) {
                int sc = (int) Math.min(46, (floor - y0 - 36) / Math.max(1f, m.getBbHeight() * m.rankScale()));
                InventoryScreen.renderEntityInInventoryFollowsMouse(g, cx, floor, sc, cx - mx, floor - 60 - my, m);
            }
            int ty = floor + 8;
            for (String line : EXCL_TEXT[i]) { if (ty < y0 + chh - 32) g.drawString(font, font.plainSubstrByWidth(line, cw - 20), x + 10, ty, 0xFFB0B8C8, false); ty += 10; }
            final int k = i;
            String label = st == 0 ? "CHALLENGE" : st == 1 ? "FIGHTING..." : st == 2 ? "ARISE" : "IN YOUR ARMY";
            button(g, x + 10, y0 + chh - 26, cw - 20, 18, label, st == 0 || st == 2, st == 2 ? EDGE : GOLD, mx, my, () -> {
                if (challenges[k] == 0) { act(5, k, ""); onClose(); } else act(6, k, "");
            });
        }
    }

    // ---------------------------------------------------------------------------------------------------- pieces
    private void tab(GuiGraphics g, int x, int y, String label, int which, int mx, int my) {
        int w = font.width(label) + 14;
        boolean on = tab == which, hov = in(mx, my, x, y - 3, w, 14);
        if (on) g.fill(x, y + 10, x + w, y + 12, EDGE);
        g.drawString(font, label, x + 7, y, on ? 0xFFCDE6FF : hov ? 0xFF9AB4D8 : 0xFF5A6A88, false);
        hits.add(new Hit(x, y - 3, w, 14, () -> { tab = which; renaming = false; releaseArmed = -1; }));
    }

    private void button(GuiGraphics g, int x, int y, int w, int h, String label, boolean enabled, int accent, int mx, int my, Runnable run) {
        boolean hov = enabled && in(mx, my, x, y, w, h);
        g.fill(x, y, x + w, y + h, !enabled ? 0x60101622 : hov ? 0xE0183060 : 0xC00E1830);
        frame(g, x, y, w, h, !enabled ? 0x401C3460 : hov ? lighten(accent) : accent);
        if (hov) g.fill(x + 1, y + 1, x + w - 1, y + 2, 0x60FFFFFF);
        String l = font.plainSubstrByWidth(label, w - 4);
        g.drawString(font, l, x + (w - font.width(l)) / 2, y + (h - 8) / 2, !enabled ? 0xFF3A4660 : hov ? 0xFFFFFFFF : 0xFFCDE6FF, false);
        if (enabled) hits.add(new Hit(x, y, w, h, run));
    }

    private static void frame(GuiGraphics g, int x, int y, int w, int h, int c) {
        g.fill(x, y, x + w, y + 1, c); g.fill(x, y + h - 1, x + w, y + h, c);
        g.fill(x, y, x + 1, y + h, c); g.fill(x + w - 1, y, x + w, y + h, c);
    }

    /** Its rank as little diamonds. */
    private static void pips(GuiGraphics g, int right, int y, int rank, int c) {
        for (int i = 0; i <= Math.min(4, rank); i++) {
            int x = right - i * 6;
            g.fill(x - 1, y, x + 1, y + 1, c); g.fill(x - 2, y + 1, x + 2, y + 3, c); g.fill(x - 1, y + 3, x + 1, y + 4, c);
        }
    }

    /** A burning eye: it's at your side. */
    private static void eye(GuiGraphics g, int x, int y, float t) {
        int a = (int) (180 + 70 * Mth.sin(t * 0.2f));
        g.fill(x - 3, y, x + 3, y + 2, (a << 24) | 0x3C90FF);
        g.fill(x - 1, y, x + 1, y + 2, 0xFFE0F4FF);
    }

    /** The dark pool it stands in, lit blue at the rim, wisps rising off it. */
    private static void pool(GuiGraphics g, int cx, int y, float t) {
        for (int k = 0; k < 6; k++) {
            int rw = 44 - k * 6, a = 60 + k * 30;
            g.fill(cx - rw, y - 2 + k / 2, cx + rw, y + 2 - k / 3, (Math.min(255, a) << 24) | (k == 0 ? 0x3C78E0 : 0x050812));
        }
        for (int k = 0; k < 5; k++) {
            float ph = k * 1.3f;
            int wx = cx - 34 + k * 17 + (int) (Mth.sin(t * 0.07f + ph) * 3);
            int hgt = (int) (10 + 8 * Mth.sin(t * 0.05f + ph * 2));
            g.fillGradient(wx, y - hgt - 6, wx + 2, y, 0x002A60D0, 0x903C90FF);
        }
    }

    private ShadowSoldier model(String type, int rank, boolean living) {
        String key = type + "|" + rank + "|" + living;
        ShadowSoldier m = models.get(key);
        if (m != null || minecraft == null || minecraft.level == null) return m;
        var et = EXCL_TYPE[0].equals(type) ? AddonEntities.OATHBOUND_KNIGHT.get() : EXCL_TYPE[1].equals(type) ? AddonEntities.SHADOW_RAVENER.get() : AddonEntities.SHADOW_SOLDIER.get();
        m = et.create(minecraft.level);
        if (m == null) return null;
        m.preview(type, rank, living);
        models.put(key, m);
        return m;
    }

    private static String typeName(String type) {
        var t = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getValue(net.minecraft.resources.ResourceLocation.tryParse(type));
        return t == null ? type : t.getDescription().getString();
    }

    private static int nextLevel(int rank) { return new int[]{8, 14, 20, 26, 30}[Math.min(4, rank)]; }

    private static boolean in(double mx, double my, int x, int y, int w, int h) { return mx >= x && mx < x + w && my >= y && my < y + h; }

    private static int lerpCol(int a, int b, float f) {
        int r = 0;
        for (int s = 0; s < 32; s += 8) r |= ((int) Mth.lerp(f, (a >>> s) & 255, (b >>> s) & 255) & 255) << s;
        return r;
    }

    private static int lighten(int c) { return lerpCol(c, 0xFFFFFFFF, 0.35f); }

    private void commitName() {
        ShadowPackets.Entry e = sel();
        if (e != null && !nameBuf.isBlank()) act(3, e.id(), nameBuf.strip());
        renaming = false;
    }

    // ---------------------------------------------------------------------------------------------------- input
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return false;
        for (Hit h : List.copyOf(hits)) if (h.in(mx, my)) {
            h.run().run();
            if (minecraft != null) minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_HIT, 0.7f, 0.6f));
            return true;
        }
        if (renaming) commitName();
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double d) {
        if (tab == 0) scroll = Math.max(0, Math.min(Math.max(0, list.size() - visibleCards()), scroll - (int) Math.signum(d)));
        return true;
    }

    @Override
    public boolean charTyped(char c, int mods) {
        if (renaming && nameBuf.length() < 24 && net.minecraft.SharedConstants.isAllowedChatCharacter(c)) { nameBuf += c; return true; }
        return false;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (renaming) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) { commitName(); return true; }
            if (key == GLFW.GLFW_KEY_BACKSPACE) { if (!nameBuf.isEmpty()) nameBuf = nameBuf.substring(0, nameBuf.length() - 1); return true; }
            if (key == GLFW.GLFW_KEY_ESCAPE) { renaming = false; return true; }
            return true;   // typing: no other keys
        }
        if (KEY.matches(key, scan)) { onClose(); return true; }
        if (key == GLFW.GLFW_KEY_TAB) { tab = 1 - tab; return true; }
        if (tab == 0 && (key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_UP) && !list.isEmpty()) {   // walk the list
            int i = 0;
            for (int k = 0; k < list.size(); k++) if (list.get(k).id() == selected) i = k;
            i = Mth.clamp(i + (key == GLFW.GLFW_KEY_DOWN ? 1 : -1), 0, list.size() - 1);
            selected = list.get(i).id(); releaseArmed = -1;
            if (i < scroll) scroll = i; else if (i >= scroll + visibleCards()) scroll = i - visibleCards() + 1;
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override public boolean isPauseScreen() { return false; }

    // ---------------------------------------------------------------------------------------------------- the key
    @Mod.EventBusSubscriber(modid = "iron_spell_grimoire", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Keys {
        @SubscribeEvent public static void register(RegisterKeyMappingsEvent e) { e.register(KEY); }
    }

    @Mod.EventBusSubscriber(modid = "iron_spell_grimoire", value = Dist.CLIENT)
    public static final class Ticks {
        @SubscribeEvent
        public static void tick(TickEvent.ClientTickEvent e) {
            if (e.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getInstance();
            while (KEY.consumeClick()) if (mc.player != null && mc.screen == null) act(0, 0, "");
        }
    }
}
