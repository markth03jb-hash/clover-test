package com.ironspellgrimoire.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * The Story Journal (ASHFALL): on the left, where the story is and what's next; on the right, three tabs - the story
 * so far, the party (and your Brand), and the pages you've found. Opened by the Journal item, K, or /story.
 */
public class JournalScreen extends Screen {
    static final String[] TABS = {"Story", "Party", "Pages"};
    int tab, page = -1, scroll;

    public JournalScreen(int page) {
        super(Component.literal("Story Journal"));
        if (page >= 0) { tab = 2; this.page = page; }
    }

    void refresh() {}

    static CompoundTag s() { return StoryClient.state; }
    static boolean begun() { return s().getBoolean("has") && s().getInt("ch") >= 0; }

    @Override public boolean isPauseScreen() { return false; }

    int x0() { return BookUi.x0(width); }
    int y0() { return BookUi.y0(height); }
    static final int PW = 126;
    int lx() { return x0() + 12; }
    int rx() { return x0() + BookUi.BW / 2 + 12; }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        BookUi.book(g, font, width, height, BookUi.colours(), Component.literal("Story Journal"), begun() ? "ASHFALL" : null);
        int y0 = y0();
        left(g, mx, my, y0);
        right(g, mx, my, y0);
        super.render(g, mx, my, pt);
    }

    // ---- the left page: where the story is, what's next
    void left(GuiGraphics g, int mx, int my, int y0) {
        int x = lx(), y = y0 + 10;
        if (!begun()) {
            BookUi.heading(g, font, "ASHFALL", x, PW, y);
            y = BookUi.text(g, font, "A star is going to fall on a village called Emberfall, and you are going to be the one who lives.", x, y + 14, PW, BookUi.INK);
            y = BookUi.text(g, font, "Your party shares one story; each of you carries your own Brand.", x, y + 6, PW, BookUi.INK_SOFT);
            boolean can = !s().getBoolean("has") || s().getBoolean("leader");
            BookUi.button(g, font, x + 13, y0 + BookUi.BH - 30, 100, 16, "Begin", BookUi.in(mx, my, x + 13, y0 + BookUi.BH - 30, 100, 16), can);
            if (!can) BookUi.text(g, font, "(the leader begins it)", x, y0 + BookUi.BH - 12, PW, BookUi.INK_FAINT);
            return;
        }
        BookUi.heading(g, font, s().getString("arcTitle"), x, PW, y);
        y = BookUi.text(g, font, s().getString("chTitle"), x, y + 13, PW, BookUi.INK_SOFT);
        BookUi.rule(g, x - 6, PW + 12, y + 2);
        y += 8;
        ListTag goals = s().getList("goals", Tag.TAG_COMPOUND);
        if (goals.isEmpty()) y = BookUi.text(g, font, s().getInt("ch") >= 5 ? "Arc I is done. Rest - for now." : "Read on.", x, y, PW, BookUi.INK_FAINT);
        for (Tag t : goals) {
            CompoundTag gl = (CompoundTag) t;
            int h = gl.getInt("h"), n = gl.getInt("n");
            boolean done = h >= Math.max(1, n);
            BookUi.frame(g, x, y + 1, 7, 7, done ? BookUi.RED_INK : BookUi.INK);
            if (done) g.fill(x + 2, y + 3, x + 5, y + 6, BookUi.RED_INK);
            int yy = BookUi.text(g, font, gl.getString("t"), x + 11, y, PW - 11, done ? BookUi.INK_FAINT : BookUi.INK);
            if (n > 1) {
                int bw = PW - 40, filled = (int) (bw * Math.min(1f, h / (float) n));
                g.fill(x + 11, yy + 1, x + 11 + bw, yy + 4, 0x40000000 | (BookUi.INK & 0xFFFFFF));
                g.fill(x + 11, yy + 1, x + 11 + filled, yy + 4, BookUi.RED_INK);
                String p = h + "/" + n;
                g.drawString(font, p, x + PW - font.width(p), yy - 1, BookUi.INK_SOFT, false);
                yy += 6;
            }
            y = yy + 3;
        }
        String enter = s().getBoolean("next") ? (s().getString("nextName").isEmpty() ? "Begin Arc II" : s().getString("nextName")) : s().getString("enter");
        boolean can = !s().getBoolean("next") || s().getBoolean("leader");
        if (!enter.isEmpty()) BookUi.button(g, font, x + 3, y0 + BookUi.BH - 26, PW - 6, 16, enter, BookUi.in(mx, my, x + 3, y0 + BookUi.BH - 26, PW - 6, 16), can);
    }

    // ---- the right page: tabs
    void right(GuiGraphics g, int mx, int my, int y0) {
        int x = rx(), tw = (PW - 4) / 3;
        for (int i = 0; i < TABS.length; i++) {
            int bx = x + i * (tw + 2);
            BookUi.button(g, font, bx, y0 + 8, tw, 13, TABS[i], tab == i || BookUi.in(mx, my, bx, y0 + 8, tw, 13), true);
            if (tab == i) g.fill(bx + 2, y0 + 19, bx + tw - 2, y0 + 20, BookUi.RED_INK);
        }
        List<Object[]> rows = rows();   // {text, colour} or {null, n}: a gap
        int top = y0 + 28, bottom = y0 + BookUi.BH - 10, y = top;
        int lineIdx = 0;
        for (Object[] r : rows) {
            if (r[0] == null) { if (lineIdx >= scroll) y += (Integer) r[1]; lineIdx++; continue; }
            for (FormattedCharSequence l : font.split(Component.literal((String) r[0]), PW)) {
                if (lineIdx++ < scroll) continue;
                if (y > bottom - 9) break;
                g.drawString(font, l, x, y, (Integer) r[1], false);
                y += 9;
            }
        }
        if (tab == 2 && page >= 0) BookUi.button(g, font, x, y0 + BookUi.BH - 22, 40, 13, "Back", BookUi.in(mx, my, x, y0 + BookUi.BH - 22, 40, 13), true);
    }

    List<Object[]> rows() {
        List<Object[]> r = new ArrayList<>();
        switch (tab) {
            case 0 -> {
                if (!begun()) { r.add(new Object[]{"Nothing written yet.", BookUi.INK_FAINT}); break; }
                r.add(new Object[]{s().getString("chText"), BookUi.INK});
                r.add(new Object[]{null, 6});
                ListTag log = s().getList("log", Tag.TAG_STRING);
                if (!log.isEmpty()) r.add(new Object[]{"So far", BookUi.RED_INK});
                for (int i = log.size() - 1; i >= 0; i--) r.add(new Object[]{"- " + log.getString(i), BookUi.INK_SOFT});
            }
            case 1 -> {
                int b = s().getInt("brand");
                r.add(new Object[]{"Your Brand: " + (b <= 0 ? "asleep" : "Brand " + new String[]{"0", "I", "II", "III", "IV"}[Math.min(4, b)] + (b >= 2 ? " - Cinderheart" : " - Ember Surge")), BookUi.RED_INK});
                if (b == 1) r.add(new Object[]{"Press " + StoryClient.BRAND.getTranslatedKeyMessage().getString() + ": 12 s of fire - spells 20% harder, 10% faster, melee burns. Then it sleeps 90 s.", BookUi.INK_SOFT});
                if (b >= 2) r.add(new Object[]{"Press " + StoryClient.BRAND.getTranslatedKeyMessage().getString() + ": 12 s of fire - spells 30% harder, 15% faster, melee burns, and your spells kindle what they hit. It takes 1 health a second while it burns. Then it sleeps 90 s.", BookUi.INK_SOFT});
                r.add(new Object[]{null, 6});
                r.add(new Object[]{"Party", BookUi.RED_INK});
                ListTag ms = s().getList("members", Tag.TAG_COMPOUND);
                if (ms.isEmpty()) r.add(new Object[]{"Just you.", BookUi.INK_SOFT});
                for (Tag t : ms) {
                    CompoundTag m = (CompoundTag) t;
                    r.add(new Object[]{(m.getBoolean("lead") ? "* " : "  ") + m.getString("n") + (m.getBoolean("on") ? "" : " (away)"), m.getBoolean("on") ? BookUi.INK : BookUi.INK_FAINT});
                }
                r.add(new Object[]{null, 6});
                r.add(new Object[]{"/story party invite <name> - bring someone in (the leader).", BookUi.INK_FAINT});
                r.add(new Object[]{"/story party accept, leave, kick, lead.", BookUi.INK_FAINT});
                r.add(new Object[]{"Everyone's deeds count for the party. Choices are a vote.", BookUi.INK_FAINT});
            }
            default -> {
                ListTag ps = s().getList("pages", Tag.TAG_COMPOUND);
                if (page >= 0) {
                    for (Tag t : ps) {
                        CompoundTag p = (CompoundTag) t;
                        if (p.getInt("i") != page) continue;
                        r.add(new Object[]{p.getString("t"), BookUi.RED_INK});
                        r.add(new Object[]{null, 4});
                        for (String para : p.getString("b").split("\n")) r.add(new Object[]{para.isEmpty() ? " " : para, BookUi.INK});
                    }
                    if (r.isEmpty()) r.add(new Object[]{"The page is too scorched to read here.", BookUi.INK_FAINT});
                    break;
                }
                if (ps.isEmpty()) r.add(new Object[]{"No pages yet. Sallow's people carry them.", BookUi.INK_FAINT});
                for (Tag t : ps) r.add(new Object[]{"> " + ((CompoundTag) t).getString("t"), BookUi.INK});
            }
        }
        return r;
    }

    @Override public boolean mouseClicked(double mx, double my, int b) {
        int y0 = y0(), x = rx(), tw = (PW - 4) / 3;
        for (int i = 0; i < TABS.length; i++) if (BookUi.in(mx, my, x + i * (tw + 2), y0 + 8, tw, 13)) { tab = i; page = -1; scroll = 0; return true; }
        if (!begun() && BookUi.in(mx, my, lx() + 13, y0 + BookUi.BH - 30, 100, 16)) { StoryClient.act("begin"); onClose(); return true; }
        if (begun() && s().getBoolean("next") && s().getBoolean("leader") && BookUi.in(mx, my, lx() + 3, y0 + BookUi.BH - 26, PW - 6, 16)) { StoryClient.act("begin"); onClose(); return true; }
        if (begun() && !s().getBoolean("next") && !s().getString("enter").isEmpty() && BookUi.in(mx, my, lx() + 3, y0 + BookUi.BH - 26, PW - 6, 16)) { StoryClient.act("enter"); onClose(); return true; }
        if (tab == 2 && page >= 0 && BookUi.in(mx, my, x, y0 + BookUi.BH - 22, 40, 13)) { page = -1; scroll = 0; return true; }
        if (tab == 2 && page < 0) {   // a page's title: read it
            ListTag ps = s().getList("pages", Tag.TAG_COMPOUND);
            int y = y0 + 28;
            for (Tag t : ps) {
                if (BookUi.in(mx, my, x, y, PW, 9)) { page = ((CompoundTag) t).getInt("i"); scroll = 0; return true; }
                y += 9 * Math.max(1, font.split(Component.literal("> " + ((CompoundTag) t).getString("t")), PW).size());
            }
        }
        return super.mouseClicked(mx, my, b);
    }

    @Override public boolean mouseScrolled(double mx, double my, double d) { scroll = Mth.clamp(scroll - (int) Math.signum(d), 0, 60); return true; }

    /** For the screenshot run. */
    public void showTab(int t) { tab = t; scroll = 0; }
}
