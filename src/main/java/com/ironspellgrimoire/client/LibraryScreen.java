package com.ironspellgrimoire.client;

import com.ironspellgrimoire.server.GrimoireLibrary;
import com.ironspellgrimoire.server.GrimoireMastery;
import com.ironspellgrimoire.server.MemoryStory;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/**
 * The Grimoire Library (server/GrimoireLibrary): a tall bookcase of dark wood and gold, one grimoire on its shelves for
 * every mage ever chosen, the greatest first. Each spine is in its grimoire's own colours, taller the higher its
 * mastery; an awakened one wears a gold band, an ascended one a gold crown, yours a red ribbon. Hover or click a spine
 * and its card on the right says whose it is and what they've done with it.
 */
public class LibraryScreen extends Screen {
    private static final int W = 380, H = 214, CASE_W = 232, SHELVES = 4, SHELF_H = 44, SPINE = 11, PER_SHELF = (CASE_W - 16) / SPINE, PER_PAGE = PER_SHELF * SHELVES;
    private static final int WOOD = 0xFF2A1A10, WOOD_L = 0xFF4A3020, WOOD_D = 0xFF180E08, GOLD = 0xFFD8AE50, GOLD_L = 0xFFFFE08A, PARCH = 0xFFE8D8B0, INK = 0xFF2A1C10, INK_SOFT = 0xFF6A5638;

    private final List<GrimoireLibrary.Entry> entries;
    private int page = 0, selected = -1, ticks = 0;

    private LibraryScreen(List<GrimoireLibrary.Entry> e) {
        super(Component.literal("Grimoire Library"));
        this.entries = e;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) for (int i = 0; i < e.size(); i++) if (e.get(i).id().equals(mc.player.getUUID())) { selected = i; page = i / PER_PAGE; }
        if (selected < 0 && !e.isEmpty()) selected = 0;
    }

    public static void show(List<GrimoireLibrary.Entry> e) {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new LibraryScreen(e));
        if (mc.player != null) mc.player.playSound(SoundEvents.BOOK_PAGE_TURN, 1.0F, 0.7F);
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void tick() { ticks++; }
    private int pages() { return Math.max(1, (entries.size() + PER_PAGE - 1) / PER_PAGE); }
    private int x0() { return (width - W) / 2; }
    private int y0() { return (height - H) / 2; }

    /** The spine under the mouse (an index into entries), or -1. */
    private int spineAt(double mx, double my) {
        int cx = x0() + 8, cy = y0() + 24;
        for (int s = 0; s < SHELVES; s++) {
            int base = cy + (s + 1) * SHELF_H - 4;
            for (int k = 0; k < PER_SHELF; k++) {
                int i = page * PER_PAGE + s * PER_SHELF + k;
                if (i >= entries.size()) return -1;
                int h = spineH(entries.get(i)), x = cx + 8 + k * SPINE;
                if (mx >= x && mx < x + SPINE - 1 && my >= base - h && my < base) return i;
            }
        }
        return -1;
    }

    private static int spineH(GrimoireLibrary.Entry e) { return 20 + Math.min(8, e.mastery()) * 2; }

    private void turn(int d) {
        int np = Mth.clamp(page + d, 0, pages() - 1);
        if (np != page) { page = np; Minecraft.getInstance().player.playSound(SoundEvents.BOOK_PAGE_TURN, 0.8F, 1.1F); }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        int i = spineAt(mx, my);
        if (i >= 0) { selected = i; Minecraft.getInstance().player.playSound(SoundEvents.CHISELED_BOOKSHELF_PICKUP, 0.6F, 1.2F); return true; }
        int bx = x0() + 8, by = y0() + H - 16;
        if (mx >= bx && mx < bx + 16 && my >= by && my < by + 12) { turn(-1); return true; }
        if (mx >= bx + CASE_W - 24 && mx < bx + CASE_W && my >= by && my < by + 12) { turn(1); return true; }
        return super.mouseClicked(mx, my, btn);
    }

    @Override public boolean mouseScrolled(double mx, double my, double d) { turn(d < 0 ? 1 : -1); return true; }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_D) { turn(1); return true; }
        if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_A) { turn(-1); return true; }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        int x0 = x0(), y0 = y0();
        // the frame: dark wood, gold trim
        g.fill(x0 - 4, y0 - 4, x0 + W + 4, y0 + H + 4, WOOD_D);
        g.fill(x0 - 2, y0 - 2, x0 + W + 2, y0 + H + 2, GOLD);
        g.fill(x0, y0, x0 + W, y0 + H, WOOD);
        String title = "GRIMOIRE LIBRARY";
        g.drawString(font, title, x0 + 8 + (CASE_W - font.width(title)) / 2, y0 + 7, GOLD_L, true);
        String sub = entries.size() + (entries.size() == 1 ? " mage chosen" : " mages chosen");
        g.drawString(font, sub, x0 + W - 8 - font.width(sub), y0 + 7, 0xFFB09060, false);

        // the bookcase
        int cx = x0 + 8, cy = y0 + 24, ch = SHELVES * SHELF_H;
        g.fill(cx, cy, cx + CASE_W, cy + ch, WOOD_D);
        g.fill(cx, cy, cx + 3, cy + ch, WOOD_L); g.fill(cx + CASE_W - 3, cy, cx + CASE_W, cy + ch, WOOD_L);
        int hover = spineAt(mx, my);
        for (int s = 0; s < SHELVES; s++) {
            int base = cy + (s + 1) * SHELF_H - 4;
            g.fill(cx, base, cx + CASE_W, base + 4, WOOD_L);   // the shelf board, gilded along its edge
            g.fill(cx, base, cx + CASE_W, base + 1, GOLD);
            for (int k = 0; k < PER_SHELF; k++) {
                int i = page * PER_PAGE + s * PER_SHELF + k;
                if (i >= entries.size()) break;
                spine(g, entries.get(i), cx + 8 + k * SPINE, base, i == hover, i == selected);
            }
        }
        if (entries.isEmpty()) {
            var lines = font.split(Component.literal("The shelves are empty. No grimoire has chosen anyone yet."), CASE_W - 24);
            int ly = cy + SHELF_H + (SHELF_H - lines.size() * 10) / 2;   // in the middle of the second shelf, clear of the planks
            for (var l : lines) { g.drawString(font, l, cx + (CASE_W - font.width(l)) / 2, ly, 0xFFB09060, false); ly += 10; }
        }
        // page turns
        int by = y0 + H - 16;
        if (page > 0) g.drawString(font, "<", cx + 4, by + 2, GOLD_L, false);
        if (page < pages() - 1) g.drawString(font, ">", cx + CASE_W - 12, by + 2, GOLD_L, false);
        String pn = (page + 1) + " / " + pages();
        g.drawString(font, pn, cx + (CASE_W - font.width(pn)) / 2, by + 2, 0xFFB09060, false);

        // the card
        int sel = hover >= 0 ? hover : selected;
        card(g, sel, x0 + 8 + CASE_W + 8, cy, W - CASE_W - 24, ch);
        super.render(g, mx, my, pt);
    }

    private void spine(GuiGraphics g, GrimoireLibrary.Entry e, int x, int base, boolean hot, boolean sel) {
        int[] c = GrimoireWheel.themeColors(e.book());
        int h = spineH(e), top = base - h - (hot ? 2 : 0), w = SPINE - 1;
        g.fill(x, top, x + w, base, 0xFF000000 | dark(c[2], 0.4F));
        g.fill(x + 1, top + 1, x + w - 1, base, 0xFF000000 | c[4]);
        g.fill(x + 1, top + 3, x + w - 1, top + 4, 0xFF000000 | c[2]);           // trim bands
        g.fill(x + 1, base - 4, x + w - 1, base - 3, 0xFF000000 | c[2]);
        g.fill(x + 3, top + 7, x + w - 3, top + 9, 0xFF000000 | c[3]);           // the clover mark
        if ((e.flags() & 1) != 0) g.fill(x + 1, top + 11, x + w - 1, top + 13, GOLD);   // awakened: a gold band
        if ((e.flags() & 2) != 0) {   // ascended: a gold crown, glinting
            g.fill(x + 1, top - 2, x + w - 1, top, GOLD);
            g.fill(x + 1, top - 4, x + 2, top - 2, GOLD); g.fill(x + w / 2, top - 4, x + w / 2 + 1, top - 2, GOLD); g.fill(x + w - 2, top - 4, x + w - 1, top - 2, GOLD);
            if ((ticks / 4 + x) % 30 == 0) g.fill(x + w / 2, top - 6, x + w / 2 + 1, top - 5, GOLD_L);
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && e.id().equals(mc.player.getUUID())) g.fill(x + w - 3, base - 10, x + w - 1, base + 3, 0xFFB02020);   // yours: a red ribbon
        if (sel) { g.fill(x - 1, top - 1, x + w + 1, top, GOLD_L); g.fill(x - 1, base, x + w + 1, base + 1, GOLD_L); }
        if (hot) g.fill(x, top, x + w, base, 0x30FFFFFF);
    }

    private void card(GuiGraphics g, int i, int x, int y, int w, int h) {
        g.fill(x - 2, y - 2, x + w + 2, y + h + 2, GOLD);
        g.fill(x, y, x + w, y + h, PARCH);
        if (i < 0 || i >= entries.size()) {
            String s = "Choose a grimoire.";
            g.drawString(font, s, x + (w - font.width(s)) / 2, y + h / 2 - 4, INK_SOFT, false);
            return;
        }
        GrimoireLibrary.Entry e = entries.get(i);
        int[] c = GrimoireWheel.themeColors(e.book());
        ItemStack book = new ItemStack(GrimoireMastery.BOOKS.get(Mth.clamp(e.book(), 0, GrimoireMastery.BOOKS.size() - 1)).item().get());
        g.fill(x, y, x + w, y + 34, 0xFF000000 | c[4]);
        g.fill(x, y + 34, x + w, y + 35, 0xFF000000 | c[2]);
        g.pose().pushPose();
        g.pose().translate(x + 4, y + 2, 0);
        g.pose().scale(1.9F, 1.9F, 1F);
        g.renderItem(book, 0, 0);
        g.pose().popPose();
        String rank = "#" + (i + 1);
        g.drawString(font, rank, x + w - 4 - font.width(rank), y + 4, 0xFF000000 | c[5], true);
        String nm = e.name();
        if (font.width(nm) > w - 44) nm = font.plainSubstrByWidth(nm, w - 46) + "..";
        g.drawString(font, nm, x + 40, y + 16, 0xFF000000 | c[5], true);
        int yy = y + 40;
        String bn = book.getHoverName().getString();
        for (var l : font.split(Component.literal(bn), w - 8)) { g.drawString(font, l, x + 4, yy, INK, false); yy += 10; }
        yy += 2;
        int cap = (e.flags() & 1) != 0 ? GrimoireMastery.AWAKENED_MAX : (e.flags() & 4) != 0 ? GrimoireMastery.ASCENDED_MAX : GrimoireMastery.MAX_LEVEL;
        g.drawString(font, "Mastery " + e.mastery() + " / " + cap, x + 4, yy, INK, false);
        yy += 10;
        for (int k = 0; k < cap; k++) g.fill(x + 4 + k * 9, yy, x + 11 + k * 9, yy + 3, k < e.mastery() ? 0xFF000000 | c[2] : 0x40000000);
        yy += 8;
        if ((e.flags() & 4) != 0) { g.drawString(font, "⚔ Magic Knight", x + 4, yy, 0xFF2A4A7A, false); yy += 10; }
        if ((e.flags() & 1) != 0) { g.drawString(font, "✦ Awakened", x + 4, yy, 0xFF8A5A10, false); yy += 10; }
        if ((e.flags() & 2) != 0) { g.drawString(font, "✧ Ascended", x + 4, yy, 0xFFB08010, false); yy += 10; }
        if (e.evolved() > 0) { g.drawString(font, e.evolved() + " spell" + (e.evolved() == 1 ? "" : "s") + " evolved", x + 4, yy, INK, false); yy += 10; }
        g.drawString(font, "★ " + e.stars() + " merit stars", x + 4, yy, 0xFF8A6A10, false);
        yy += 10;
        String mem = e.memory() >= MemoryStory.CH ? "All memories found" : "Memories " + e.memory() + " / " + MemoryStory.CH;
        g.drawString(font, mem, x + 4, yy, INK_SOFT, false);
        yy += 10;
        g.drawString(font, "Chosen on day " + e.day(), x + 4, y + h - 12, INK_SOFT, false);
    }

    private static int dark(int rgb, float k) {
        return ((int) ((rgb >> 16 & 255) * k) << 16) | ((int) ((rgb >> 8 & 255) * k) << 8) | (int) ((rgb & 255) * k);
    }
}
