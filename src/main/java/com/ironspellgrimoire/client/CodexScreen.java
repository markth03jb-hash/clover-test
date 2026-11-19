package com.ironspellgrimoire.client;

import com.ironspellgrimoire.server.GrimoireMastery;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/**
 * The Grimoire Codex (item/CodexItem): the mod's guide book. An index down the left - the chapters, then every
 * grimoire - and the open page on the right, scrolled with the wheel. A grimoire's page is built from its own words
 * (its tooltip lines) and its mastery table, so it never drifts out of date.
 */
public class CodexScreen extends Screen {
    private static final int W = 372, H = 220, IDX_W = 118, ROW = 11;
    private static final int LEATHER = 0xFF15274A, LEATHER_L = 0xFF2B508F, GOLD = 0xFFE0B450, GOLD_L = 0xFFFFE48C, PARCH = 0xFFEEE2C2,
            INK = 0xFF2A1C10, INK_SOFT = 0xFF6A5638, INK_HEAD = 0xFF1F3A6B, IDX_TEXT = 0xFFD8D0B8;

    /** The chapters: a title and its paragraphs ("## " starts a sub-heading). */
    private static final String[][] CHAPTERS = {
        {"Getting Started",
         "Welcome, mage. This Codex is yours to keep - open it whenever you're lost.",
         "## The Tower",
         "Somewhere in the world stands a Grimoire Tower. New mages are handed a compass that points to the nearest one. Climb to the top and find the Grimoire Altar.",
         "## The ceremony",
         "At the altar the grimoires rise and circle above it, faster and faster, until one leaves the ring and comes to you. It has chosen you. In survival a grimoire chooses each mage once, and it is bound to you: only you can cast from it.",
         "## Lost your grimoire?",
         "Go back to any altar. It calls your grimoire back to you if you no longer carry it.",
         "## Casting",
         "Equip your grimoire as your spellbook and cast its spells like any other Iron's Spellbooks spell. Many grimoires also have modes, forms or menus of their own - their pages in this Codex say how."},
        {"Mastery",
         "Every grimoire grows as you fight with its magic, from level 1 to 5. Its stronger spells unlock as it does, and its ultimate waits at level 5.",
         "## Earning mastery",
         "Damage dealt with the book's spells, kills, bosses and time spent in one of its modes all count. Peaceful animals, villagers and armour stands give nothing.",
         "## What each level gives",
         "Every level above 1 adds spell power, cooldown reduction and max mana while the book is equipped. Progress is written on the book itself, so it keeps its level wherever it goes.",
         "## Spell evolution",
         "Cast a spell often enough and it evolves, once and for good: it hits harder, its hits flare in the book's colours and casting it throws a ring of magic from your feet."},
        {"Beyond Mastery",
         "## The Magic Knight Exam",
         "A grimoire at mastery 5 can take the exam, opened from the Grimoire Altar: five floors, each under its own law, then a hall of three boss shades. Pass, and its mastery can go on to 7.",
         "## Awakening",
         "A grimoire that passed the exam and reached mastery 6 may awaken in a real fight, when a boss's blow would leave you nearly dead. Time stops, a new page writes itself and the grimoire gains its awakened spell. Its mastery can then reach 8.",
         "## Ascension",
         "An awakened grimoire at mastery 8 can take the Ascension Trial at the altar and face its Reflection - you, with your own magic. Win, and every spell from it hits harder and burns gold.",
         "## Surpassing Limits",
         "Nearly dead in a hard fight, your grimoire can answer: it heals you to half health, and for a minute its ultimate becomes an evolved version.",
         "## The Star Forge",
         "Sneak and right-click the altar with an empty hand to spend merit stars on your grimoire: empower spells, add ink pages, set a star sigil on the cover, or mutate a spell with a Mana Core."},
        {"The World",
         "## Rifts",
         "Now and then a crack of light tears open near you and stays for a day. Step in to enter a dungeon of five to eight rooms under one or two laws. The relic at the end is found nowhere else.",
         "## Quest Board",
         "Every village gets a Quest Board. Take one quest at a time: hooded mages, monster hunts, rift watches, great beasts and lost grimoire pages. The tracker shows the way.",
         "## Wanted Posters",
         "The Poster Board hands out bounties on the bosses: find the boss within three days and bring it down for merit stars and grimoire upgrades.",
         "## The world boss",
         "About once a real day the Cinder Sovereign rises somewhere in the world and every mage is told where. All who hurt it share the reward.",
         "## The Grimoire Library",
         "Every mage ever chosen gets a copy of their grimoire on the Library's shelves. Read it at the archive in a Grimoire Tower, or from any altar.",
         "## Familiars",
         "Spend a Mana Core at the altar and a familiar hatches. It takes your grimoire's element and grows with its mastery."},
        {"Growing Grimoires",
         "A few grimoires grow far beyond mastery. Each has its own menu (see Controls for its key) and its own way of getting stronger.",
         "## System Grimoire",
         "Raise what you slay as your shadows. They level up and rank up from Soldier to General; open the army menu to equip, rename or release them. The System also gives you levels, stats and a job.",
         "## Parasite Grimoire",
         "A strain that rewrites its host. Evolve through the ranks, grow forms on your arms and choose an apex at the top.",
         "## Hunger Blade Grimoire",
         "A sword that is always hungry. Feed it kills to grow new swords - 29 in all - deepen your Bond with it and earn titles.",
         "## Masked Twins Grimoire",
         "Two masks, two twins, sixteen arts. Swap between Sol and Nyx, level both, set stones in the masks, beat the Twin Wardens and join them in the Eclipse.",
         "## Architect of Ruin Grimoire",
         "A fortress inside a book. Earn Stone from foes, build up to 24 rooms on a floor plan that grows, pair rooms side by side, claim banners for a garrison and raise Siege Mode's walls and towers round you. Its own menu has a guided tour and a full guide."},
        {"Server Rules",
         "Your server can change how the mod plays in its config file (iron_spell_grimoire-common.toml).",
         "## Player versus player",
         "Every grimoire follows one rule: its magic can hurt other players only when grimoire PvP is on in the config, the server allows PvP, and you aren't on the same team.",
         "## Other options",
         "Server admins can scale all grimoire damage, turn rifts and the world boss on or off, set how often rifts open and leave some grimoires out of the ceremony."},
    };

    private final List<Integer> index = new ArrayList<>();   // >= 0: a chapter; <= -2: grimoire -(i + 2); -1: the grimoires heading
    private int sel = 0, idxScroll = 0, pageScroll = 0;
    private List<Line> page = List.of();
    private ItemStack pageIcon = ItemStack.EMPTY;
    private String pageTitle = "";

    private record Line(FormattedCharSequence text, int color, boolean shadow) {}

    /** The page you had open last (this session): the Codex opens there again. The first time, Getting Started. */
    private static int lastSel = 0;

    public CodexScreen() { this(lastSel); }

    /** Open at an index row (0 = the first chapter; CHAPTERS.length + 1 = the first grimoire). */
    public CodexScreen(int at) {
        super(Component.literal("Grimoire Codex"));
        for (int i = 0; i < CHAPTERS.length; i++) index.add(i);
        index.add(-1);
        for (int i = 0; i < GrimoireMastery.BOOKS.size(); i++) index.add(-(i + 2));
        sel = Mth.clamp(at, 0, index.size() - 1);
        if (index.get(sel) == -1) sel++;
    }

    public static int chapters() { return CHAPTERS.length; }

    public static void open() {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new CodexScreen());
        if (mc.player != null) mc.player.playSound(SoundEvents.BOOK_PAGE_TURN, 1.0F, 0.9F);
    }

    @Override public boolean isPauseScreen() { return false; }
    private int x0() { return (width - W) / 2; }
    private int y0() { return (height - H) / 2; }
    private int idxRows() { return (H - 34) / ROW; }
    private int pageW() { return W - IDX_W - 28; }
    private int pageRows() { return (H - 30 - 34) / 10; }

    @Override
    protected void init() {
        if (sel >= idxScroll + idxRows()) idxScroll = sel - idxRows() + 1;
        build();
    }

    private void build() {
        lastSel = sel;
        int w = pageW() - 12;
        List<Line> out = new ArrayList<>();
        int e = index.get(sel);
        pageIcon = ItemStack.EMPTY;
        if (e >= 0) {
            String[] ch = CHAPTERS[e];
            pageTitle = ch[0];
            for (int i = 1; i < ch.length; i++) para(out, ch[i], w);
        } else if (e <= -2) {
            GrimoireMastery.Book b = GrimoireMastery.BOOKS.get(-e - 2);
            pageIcon = new ItemStack(b.item().get());
            pageTitle = pageIcon.getHoverName().getString();
            String key = "tooltip." + net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(pageIcon.getItem()).toString().replace(':', '.');
            for (String part : new String[]{"desc", "passive"}) if (I18n.exists(key + "." + part)) para(out, I18n.get(key + "." + part), w);
            if (!b.unlocks().isEmpty()) {
                para(out, "## Spells by mastery level", w);
                int last = -1;
                for (var en : b.unlocks().entrySet()) {
                    String nm = Component.translatable("spell.iron_spell_grimoire." + en.getKey()).getString();
                    String lv = en.getValue() != last ? "Lv " + en.getValue() + "  " : "        ";
                    last = en.getValue();
                    for (var l : font.split(Component.literal(lv + nm), w)) out.add(new Line(l, INK, false));
                }
                out.add(new Line(FormattedCharSequence.EMPTY, INK, false));
            }
            if (I18n.exists(key + ".lore")) for (var l : font.split(Component.literal(I18n.get(key + ".lore")).withStyle(ChatFormatting.ITALIC), w)) out.add(new Line(l, INK_SOFT, false));
        } else pageTitle = "";
        page = out;
        pageScroll = 0;
    }

    private void para(List<Line> out, String s, int w) {
        if (s.startsWith("## ")) {
            for (var l : font.split(Component.literal(s.substring(3)).withStyle(ChatFormatting.BOLD), w)) out.add(new Line(l, INK_HEAD, false));
            return;
        }
        for (var l : font.split(Component.literal(s), w)) out.add(new Line(l, INK, false));
        out.add(new Line(FormattedCharSequence.EMPTY, INK, false));
    }

    private int rowAt(double mx, double my) {
        int ix = x0() + 8, iy = y0() + 26;
        if (mx < ix || mx >= ix + IDX_W || my < iy) return -1;
        int r = (int) ((my - iy) / ROW);
        if (r >= idxRows()) return -1;
        int i = idxScroll + r;
        return i < index.size() ? i : -1;
    }

    private void select(int i) {
        if (i < 0 || i >= index.size() || index.get(i) == -1 || i == sel) return;
        sel = i;
        if (sel < idxScroll) idxScroll = sel;
        if (sel >= idxScroll + idxRows()) idxScroll = sel - idxRows() + 1;
        build();
        Minecraft.getInstance().player.playSound(SoundEvents.BOOK_PAGE_TURN, 0.7F, 1.1F);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        int i = rowAt(mx, my);
        if (i >= 0) { select(i); return true; }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double d) {
        int step = d < 0 ? 1 : -1;
        if (mx < x0() + 8 + IDX_W) idxScroll = Mth.clamp(idxScroll + step * 2, 0, Math.max(0, index.size() - idxRows()));
        else pageScroll = Mth.clamp(pageScroll + step * 2, 0, Math.max(0, page.size() - pageRows()));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_S) { int n = sel + 1; if (n < index.size() && index.get(n) == -1) n++; select(n); return true; }
        if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_W) { int n = sel - 1; if (n >= 0 && index.get(n) == -1) n--; select(n); return true; }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        int x0 = x0(), y0 = y0();
        g.drawManaged(() -> {
            g.fill(x0 - 4, y0 - 4, x0 + W + 4, y0 + H + 4, 0xFF0E0A06);
            g.fill(x0 - 2, y0 - 2, x0 + W + 2, y0 + H + 2, GOLD);
            g.fill(x0, y0, x0 + W, y0 + H, LEATHER);
            g.fill(x0, y0, x0 + W, y0 + 1, LEATHER_L);
        });
        String t = "GRIMOIRE CODEX";
        g.drawString(font, t, x0 + 8 + (IDX_W - font.width(t)) / 2, y0 + 9, GOLD_L, true);

        // the index
        int ix = x0 + 8, iy = y0 + 26, hover = rowAt(mx, my);
        for (int r = 0; r < idxRows() && idxScroll + r < index.size(); r++) {
            int i = idxScroll + r, e = index.get(i), y = iy + r * ROW;
            if (e == -1) { g.fill(ix + 2, y + 5, ix + IDX_W - 2, y + 6, GOLD); String h = " Grimoires "; int hw = font.width(h); g.fill(ix + (IDX_W - hw) / 2, y + 1, ix + (IDX_W + hw) / 2, y + 10, LEATHER); g.drawString(font, h, ix + (IDX_W - hw) / 2, y + 1, GOLD, false); continue; }
            if (i == sel) g.fill(ix, y, ix + IDX_W, y + ROW, 0xFF35609F);
            else if (i == hover) g.fill(ix, y, ix + IDX_W, y + ROW, 0x40FFFFFF);
            String nm = e >= 0 ? CHAPTERS[e][0] : new ItemStack(GrimoireMastery.BOOKS.get(-e - 2).item().get()).getHoverName().getString();
            if (font.width(nm) > IDX_W - 8) nm = font.plainSubstrByWidth(nm, IDX_W - 12) + "..";
            g.drawString(font, nm, ix + 4, y + 2, i == sel ? GOLD_L : e >= 0 ? 0xFFF0E6C8 : IDX_TEXT, false);
        }
        if (index.size() > idxRows()) {   // the index's scroll bar
            int bh = idxRows() * ROW, kh = Math.max(10, bh * idxRows() / index.size()), ky = iy + (bh - kh) * idxScroll / Math.max(1, index.size() - idxRows());
            g.fill(ix + IDX_W + 2, iy, ix + IDX_W + 4, iy + bh, 0xFF0E1A33);
            g.fill(ix + IDX_W + 2, ky, ix + IDX_W + 4, ky + kh, GOLD);
        }

        // the page
        int px = x0 + IDX_W + 18, py = y0 + 8, pw = pageW(), ph = H - 16;
        g.fill(px - 2, py - 2, px + pw + 2, py + ph + 2, GOLD);
        g.fill(px, py, px + pw, py + ph, PARCH);
        int tx = px + 6;
        if (!pageIcon.isEmpty()) { g.renderItem(pageIcon, px + 6, py + 6); tx = px + 26; }
        String pt2 = pageTitle;
        if (font.width(pt2) > px + pw - 6 - tx) pt2 = font.plainSubstrByWidth(pt2, px + pw - 10 - tx) + "..";
        g.drawString(font, pt2, tx, py + 10, INK_HEAD, false);
        g.fill(px + 6, py + 26, px + pw - 6, py + 27, GOLD);
        int ly = py + 32;
        for (int k = pageScroll; k < page.size() && k < pageScroll + pageRows(); k++) {
            Line l = page.get(k);
            g.drawString(font, l.text(), px + 6, ly, l.color(), l.shadow());
            ly += 10;
        }
        if (page.size() > pageRows()) {
            String more = pageScroll + pageRows() < page.size() ? "v" : "^";
            g.drawString(font, more, px + pw - 10, py + ph - 11, INK_SOFT, false);
        }
        super.render(g, mx, my, pt);
    }
}
