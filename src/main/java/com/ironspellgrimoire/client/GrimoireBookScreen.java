package com.ironspellgrimoire.client;

import com.ironspellgrimoire.AddonClientConfig;
import com.ironspellgrimoire.server.ComboChain;
import com.ironspellgrimoire.server.GrimoireMastery;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The grimoire, opened (sneak + use while holding it): the same parchment pages as the floating 3D book, inside a
 * cover in the grimoire's colours. One spread per spell, in the book's order:
 *   left page   the spell's name and icon, and what it does
 *   right page  its numbers - base damage and the spell's own values, mana cost, cooldown, cast type - the mastery
 *               level it unlocks at, and the combos it belongs to
 * A spell the grimoire has not given you yet is a blank spread. Turn pages with the arrows, arrow keys / A-D or the
 * mouse wheel. The effect quality switch is above the book.
 */
public class GrimoireBookScreen extends Screen {
    private static final int BW = 300, BH = 190;
    private static final ResourceLocation PAGE_L = ResourceLocation.fromNamespaceAndPath("iron_spell_grimoire", "textures/gui/grimoire_page_left.png");
    private static final ResourceLocation PAGE_R = ResourceLocation.fromNamespaceAndPath("iron_spell_grimoire", "textures/gui/grimoire_page_right.png");
    private static final int INK = 0xFF2A1C10, INK_SOFT = 0xFF5A4630, RED_INK = 0xFF7A1A12;

    private final ItemStack stack;
    private final int book;
    private final List<String> ids = new ArrayList<>();
    private final List<Integer> unlockAt = new ArrayList<>();
    private int page = 0;
    private double flipAt = -100;
    private int flipDir = 1;
    private int ticks = 0;
    private boolean hidden = false;
    /** The Memories tab (the Lost Memory Pages, server/MemoryPages): which spread of it is open. */
    private boolean memories = false;
    /** The Blood Grimoire: read its pages as the Scarlet Moon's ice moves without being in the mode. */
    private boolean iceView = false;
    /** The Tree tab (the Mana Tree, server/SkillTree). */
    private boolean treeMode = false;
    /** The Path tab (what's left on the way to Ascension) and the Upgrades tab (everything bound into this grimoire). */
    private boolean pathMode = false, upgMode = false;
    private int mpage = 0;
    private static final int MSPREADS = com.ironspellgrimoire.server.MemoryStory.CH * 3;

    /** Text on a page: readable, or scribbled out when the spell isn't unlocked yet. */
    private Component ink(Component c) {
        return hidden ? c.copy().withStyle(ChatFormatting.OBFUSCATED) : c;
    }

    public GrimoireBookScreen(ItemStack stack) {
        super(stack.getHoverName());
        this.stack = stack.copy();
        GrimoireMastery.Book b = GrimoireMastery.bookOf(stack);
        this.book = b == null ? 0 : Math.max(0, GrimoireMastery.BOOKS.indexOf(b));
        if (b != null) for (Map.Entry<String, Integer> e : b.unlocks().entrySet()) { ids.add(e.getKey()); unlockAt.add(e.getValue()); }
        if (com.ironspellgrimoire.server.Awakening.awakened(stack)) { ids.add(com.ironspellgrimoire.server.Awakening.spellFor(stack)); unlockAt.add(6); }   // the page it wrote when it awakened
    }

    /** The Blood Grimoire's ice view (the automatic screenshot run). */
    public void iceView(boolean on) { iceView = on; }

    /** Turns straight to a part of the book (the automatic screenshot run, UiShots): spells:N, combos, path, upgrades, tree, memories:N. */
    public void showPart(String part) {
        memories = treeMode = pathMode = upgMode = false; flipAt = -100;
        if (part.startsWith("spells:")) page = Math.min(pages() - 1, Integer.parseInt(part.substring(7)));
        else if (part.equals("combos")) page = ids.size();
        else if (part.equals("path")) pathMode = true;
        else if (part.equals("upgrades")) upgMode = true;
        else if (part.equals("tree")) treeMode = true;
        else if (part.startsWith("memories:")) { memories = true; mpage = Math.min(MSPREADS - 1, Integer.parseInt(part.substring(9))); }
    }

    public static void open(ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        if (GrimoireMastery.bookOf(stack) == null) return;
        mc.setScreen(new GrimoireBookScreen(stack));
        if (mc.player != null) mc.player.playSound(SoundEvents.BOOK_PAGE_TURN, 1.0F, 0.8F);
    }

    @Override public boolean isPauseScreen() { return false; }

    private boolean unlocked(int i) {
        if (!MasteryClient.enabled()) return true;
        return GrimoireMastery.level(stack) >= unlockAt.get(i);
    }

    private AbstractSpell spell(int i) {
        try { return SpellRegistry.getSpell(ResourceLocation.fromNamespaceAndPath("iron_spell_grimoire", ids.get(i))); } catch (Throwable t) { return null; }
    }

    /** One page per spell, then the combo page. */
    private int pages() { return ids.size() + 1; }

    private boolean onCombos() { return page == ids.size(); }

    private void turn(int dir) {
        if (memories) {
            int np = Mth.clamp(mpage + dir, 0, MSPREADS - 1);
            if (np == mpage) return;
            mpage = np; flipAt = ticks; flipDir = dir;
            Minecraft.getInstance().player.playSound(SoundEvents.BOOK_PAGE_TURN, 1.0F, 0.9F + 0.2F * (float) Math.random());
            return;
        }
        if (ids.isEmpty()) return;
        int np = Mth.clamp(page + dir, 0, pages() - 1);
        if (np == page) return;
        page = np; flipAt = ticks; flipDir = dir;
        Minecraft.getInstance().player.playSound(SoundEvents.BOOK_PAGE_TURN, 1.0F, 0.9F + 0.2F * (float) Math.random());
    }

    @Override public void tick() { ticks++; }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_D) { turn(1); return true; }
        if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_A) { turn(-1); return true; }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) { turn(delta < 0 ? 1 : -1); return true; }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int x0 = (width - BW) / 2, y0 = (height - BH) / 2;
        if (book == GrimoireWheel.BLOOD && inside(mx, my, x0 + BW + 7, y0 + 134, 54, 16)) {   // the Blood Grimoire's sixth ribbon: Ice / Blood
            iceView = !iceView; flipAt = ticks; flipDir = iceView ? 1 : -1;
            Minecraft.getInstance().player.playSound(iceView ? SoundEvents.AMETHYST_BLOCK_CHIME : SoundEvents.BOOK_PAGE_TURN, 1.0F, iceView ? 1.4F : 0.8F);
            return true;
        }
        if (pathClick(mx, my, x0, y0)) return true;
        if (inside(mx, my, x0 - 2, y0 + BH - 20, 22, 16)) { turn(-1); return true; }
        if (inside(mx, my, x0 + BW - 20, y0 + BH - 20, 22, 16)) { turn(1); return true; }
        if (inside(mx, my, x0 + BW + 7, y0 + 90, 54, 16)) {   // the fourth ribbon: the Path
            pathMode = !pathMode; upgMode = false; treeMode = false; memories = false; flipAt = ticks; flipDir = pathMode ? 1 : -1;
            Minecraft.getInstance().player.playSound(SoundEvents.BOOK_PAGE_TURN, 1.0F, 0.65F);
            return true;
        }
        if (inside(mx, my, x0 + BW + 7, y0 + 112, 54, 16)) {   // the fifth ribbon: the Upgrades
            upgMode = !upgMode; pathMode = false; treeMode = false; memories = false; flipAt = ticks; flipDir = upgMode ? 1 : -1;
            Minecraft.getInstance().player.playSound(SoundEvents.BOOK_PAGE_TURN, 1.0F, 0.75F);
            return true;
        }
        if (inside(mx, my, x0 + BW + 7, y0 + 68, 54, 16)) {   // the third ribbon: the Mana Tree (or back to the spells)
            treeMode = !treeMode; memories = false; pathMode = false; upgMode = false; flipAt = ticks; flipDir = treeMode ? 1 : -1;
            Minecraft.getInstance().player.playSound(SoundEvents.BOOK_PAGE_TURN, 1.0F, 0.6F);
            return true;
        }
        if (treeMode && treeClick(mx, my, x0, y0)) return true;
        if (inside(mx, my, x0 + BW + 7, y0 + 46, 54, 16)) {   // the second ribbon: the Memories (or back to the spells)
            memories = !memories; treeMode = false; pathMode = false; upgMode = false; flipAt = ticks; flipDir = memories ? 1 : -1;
            Minecraft.getInstance().player.playSound(SoundEvents.BOOK_PAGE_TURN, 1.0F, 0.7F);
            return true;
        }
        if (inside(mx, my, x0 + BW + 7, y0 + 24, 46, 16)) {   // the ribbon tab on the edge of the book: jump to the combos (or back to the spells)
            if (memories || treeMode || pathMode || upgMode) { memories = false; treeMode = false; pathMode = false; upgMode = false; page = ids.size(); flipAt = ticks; flipDir = -1; Minecraft.getInstance().player.playSound(SoundEvents.BOOK_PAGE_TURN, 1.0F, 0.8F); return true; }
            int to = onCombos() ? 0 : ids.size();
            if (to != page) { flipDir = to > page ? 1 : -1; page = to; flipAt = ticks; Minecraft.getInstance().player.playSound(SoundEvents.BOOK_PAGE_TURN, 1.0F, 0.8F); }
            return true;
        }
        if (inside(mx, my, x0 + BW - 92, y0 - 16, 92, 12)) {
            AddonClientConfig.Quality[] q = AddonClientConfig.Quality.values();
            AddonClientConfig.setQuality(q[(AddonClientConfig.quality().ordinal() + 1) % q.length]);
            Minecraft.getInstance().player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.6F, 1.0F);
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) { return mx >= x && mx < x + w && my >= y && my < y + h; }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        Font f = font;
        int[] c = GrimoireWheel.themeColors(iceView ? GrimoireWheel.ICE : GrimoireWheel.themeFor(book));   // pageIn, pageOut, trim, accent, panel, title
        int x0 = (width - BW) / 2, y0 = (height - BH) / 2, mid = x0 + BW / 2;
        float flip = (float) Mth.clamp(1.0 - (ticks + pt - flipAt) / 5.0, 0.0, 1.0);

        // the cover, in the grimoire's colours, with its trim
        g.fill(x0 - 9, y0 - 8, x0 + BW + 9, y0 + BH + 8, 0xFF000000 | dark(c[2], 0.35F));
        g.fill(x0 - 7, y0 - 6, x0 + BW + 7, y0 + BH + 6, 0xFF000000 | c[4]);
        frame(g, x0 - 5, y0 - 4, BW + 10, BH + 8, 0xFF000000 | c[2]);
        // the parchment pages of the 3D book
        g.blit(PAGE_L, x0, y0, BW / 2, BH, 0, 0, 256, 320, 256, 320);
        g.blit(PAGE_R, mid, y0, BW / 2, BH, 0, 0, 256, 320, 256, 320);

        String q = "Effects: " + AddonClientConfig.quality().name();
        boolean hq = inside(mx, my, x0 + BW - 92, y0 - 16, 92, 12);
        g.drawString(f, q, x0 + BW - f.width(q), y0 - 15, hq ? 0xFFFFFFFF : 0xFF000000 | c[5], true);
        g.drawString(f, getTitle(), x0, y0 - 15, 0xFF000000 | c[5], true);
        // your merit stars (bounties and rifts), under the book
        String ms = "\u2605 " + BountyHud.stars() + " merit stars";
        g.drawString(f, ms, x0 + (BW - f.width(ms)) / 2, y0 + BH + 6, 0xFFFFD27A, true);

        // the ribbon tab on the book's edge: Combos (or back to the Spells)
        boolean tabHot = inside(mx, my, x0 + BW + 7, y0 + 24, 46, 16);
        g.fill(x0 + BW + 5, y0 + 24, x0 + BW + 54, y0 + 40, 0xFF000000 | dark(c[2], 0.35F));
        g.fill(x0 + BW + 6, y0 + 25, x0 + BW + 53, y0 + 39, 0xFF000000 | (tabHot || (onCombos() && !memories && !treeMode && !pathMode && !upgMode) ? c[2] : c[4]));
        String tab = onCombos() && !memories && !treeMode && !pathMode && !upgMode ? "Spells" : "Combos";
        boolean tabOn = tabHot || (onCombos() && !memories && !treeMode && !pathMode && !upgMode);
        g.drawString(f, tab, x0 + BW + 30 - f.width(tab) / 2, y0 + 29, tabOn ? 0xFF000000 | c[4] : 0xFF000000 | c[5], false);
        // the second ribbon: Memories
        boolean memHot = inside(mx, my, x0 + BW + 7, y0 + 46, 54, 16);
        g.fill(x0 + BW + 5, y0 + 46, x0 + BW + 62, y0 + 62, 0xFF000000 | dark(c[2], 0.35F));
        g.fill(x0 + BW + 6, y0 + 47, x0 + BW + 61, y0 + 61, 0xFF000000 | (memHot || memories ? c[2] : c[4]));
        boolean treeHot = inside(mx, my, x0 + BW + 7, y0 + 68, 54, 16);
        g.fill(x0 + BW + 5, y0 + 68, x0 + BW + 62, y0 + 84, 0xFF000000 | dark(c[2], 0.35F));
        g.fill(x0 + BW + 6, y0 + 69, x0 + BW + 61, y0 + 83, 0xFF000000 | (treeHot || treeMode ? c[2] : c[4]));
        String tt = treeMode ? "Spells" : "Tree";
        g.drawString(f, tt, x0 + BW + 34 - f.width(tt) / 2, y0 + 73, treeHot || treeMode ? 0xFF000000 | c[4] : 0xFF000000 | c[5], false);
        String mt = memories ? "Spells" : "Memories";
        g.drawString(f, mt, x0 + BW + 34 - f.width(mt) / 2, y0 + 51, memHot || memories ? 0xFF000000 | c[4] : 0xFF000000 | c[5], false);
        ribbon(g, f, x0, y0 + 90, pathMode ? "Spells" : "Path", pathMode, mx, my, c);
        ribbon(g, f, x0, y0 + 112, upgMode ? "Spells" : "Upgrades", upgMode, mx, my, c);
        if (book == GrimoireWheel.BLOOD) ribbon(g, f, x0, y0 + 134, iceView ? "Blood" : "\u2744 Ice", iceView, mx, my, c);
        if (treeMode) { drawTree(g, f, x0, y0, mid, mx, my, c); return; }
        if (pathMode) { drawPath(g, f, x0, y0, mid); return; }
        if (upgMode) { drawUpgrades(g, f, x0, y0, mid); return; }
        if (memories) {
            drawMemories(g, f, x0, y0, mid, mx, my);
            if (flip > 0F) {
                float k = 1F - flip;
                int half = BW / 2, w = (int) (half * Math.abs(Mth.cos(k * Mth.PI)));
                boolean firstHalf = k < 0.5F;
                int px = flipDir > 0 ? (firstHalf ? mid : mid - w) : (firstHalf ? mid - w : mid);
                if (w > 0) g.blit((flipDir > 0) == firstHalf ? PAGE_R : PAGE_L, px, y0, w, BH, 0, 0, 256, 320, 256, 320);
            }
            return;
        }
        if (ids.isEmpty()) return;
        String num = (page + 1) + " / " + pages();
        g.drawString(f, num, mid - f.width(num) / 2, y0 + BH - 13, INK_SOFT, false);
        if (page > 0) g.drawString(f, "<", x0 + 6, y0 + BH - 15, inside(mx, my, x0 - 2, y0 + BH - 20, 22, 16) ? RED_INK : INK, false);
        if (page < pages() - 1) g.drawString(f, ">", x0 + BW - 12, y0 + BH - 15, inside(mx, my, x0 + BW - 20, y0 + BH - 20, 22, 16) ? RED_INK : INK, false);

        AbstractSpell sp = onCombos() ? null : spell(page);
        if (onCombos()) {
            hidden = false;
            drawCombos(g, f, x0 + 16, mid + 14, y0 + 14, BW / 2 - 30, BH - 34);
        } else if (sp != null) {
            hidden = !unlocked(page);   // not unlocked yet: the page is written, but scribbled - you can't read it
            boolean was = com.ironspellgrimoire.spells.IceView.on;
            if (iceView) com.ironspellgrimoire.spells.IceView.on = true;   // the ice move's name and picture
            try {
                drawLeft(g, f, sp, x0 + 16, y0 + 14, BW / 2 - 30, BH - 34, c);
                drawRight(g, f, sp, page, mid + 14, y0 + 14, BW / 2 - 30, book == GrimoireWheel.BLOOD ? BH - 84 : BH - 34);   // the Blood pages keep room for the paths
            } finally { com.ironspellgrimoire.spells.IceView.on = was; }
            hidden = false;
            drawPaths(g, f, mid + 14, y0, BW / 2 - 30, mx, my);
        }
        if (flip > 0F) {   // a page turning over
            float k = 1F - flip;
            int half = BW / 2;
            int w = (int) (half * Math.abs(Mth.cos(k * Mth.PI)));
            boolean firstHalf = k < 0.5F;
            int px = flipDir > 0 ? (firstHalf ? mid : mid - w) : (firstHalf ? mid - w : mid);
            ResourceLocation tex = (flipDir > 0) == firstHalf ? PAGE_R : PAGE_L;
            if (w > 0) g.blit(tex, px, y0, w, BH, 0, 0, 256, 320, 256, 320);
        }
    }

    /** Left page: name, icon, what the spell does. */
    private void drawLeft(GuiGraphics g, Font f, AbstractSpell sp, int x, int y, int w, int h, int[] c) {
        Minecraft mc = Minecraft.getInstance();
        int yy = y;
        for (FormattedCharSequence l : f.split(ink(sp.getDisplayName(mc.player).copy().withStyle(ChatFormatting.BOLD)), w)) {
            g.drawString(f, l, x + (w - f.width(l)) / 2, yy, RED_INK, false);
            yy += 10;
        }
        yy += 2;
        if (hidden) {   // no picture yet: a smudge of ink where it will be
            g.drawCenteredString(f, Component.literal("?").withStyle(ChatFormatting.BOLD), x + w / 2, yy + 8, INK_SOFT);
        } else try {   // the spell's icon, like an illustration on the page
            g.blit(sp.getSpellIconResource(), x + w / 2 - 12, yy, 24, 24, 0, 0, 16, 16, 16, 16);
        } catch (Throwable ignored) {}
        yy += 28;
        g.fill(x + 10, yy, x + w - 10, yy + 1, 0x60000000 | (INK & 0xFFFFFF));
        yy += 5;
        String key = "spell.iron_spell_grimoire." + ((iceView || GrimoireWheel.themeFor(book) == GrimoireWheel.ICE) && net.minecraft.client.resources.language.I18n.exists("spell.iron_spell_grimoire.ice_" + ids.get(page) + ".guide") ? "ice_" : "") + ids.get(page) + ".guide";   // under the Scarlet Moon: the ice move
        if (I18n.exists(key)) {
            for (FormattedCharSequence l : f.split(ink(Component.translatable(key)), w)) {
                if (yy > y + h - 8) break;
                g.drawString(f, l, x, yy, INK, false);
                yy += 9;
            }
        }
    }

    /** Right page: every number, the unlock level and the combos. */
    private void drawRight(GuiGraphics g, Font f, AbstractSpell sp, int i, int x, int y, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        int lvl = 1;
        try { lvl = sp.getLevelFor(1, mc.player); } catch (Throwable ignored) {}
        List<Component> info = new ArrayList<>();
        try {
            List<? extends Component> u = sp.getUniqueInfo(lvl, mc.player);
            if (u.isEmpty()) info.add(Component.translatable("ui.irons_spellbooks.damage", String.format("%.1f", sp.getSpellPower(lvl, mc.player))));
            else info.addAll(u);
        } catch (Throwable ignored) {}
        try { info.add(Component.translatable("ui.irons_spellbooks.mana_cost", sp.getManaCost(lvl))); } catch (Throwable ignored) {}
        try { info.add(Component.translatable("tooltip.irons_spellbooks.cooldown_length_seconds", Utils.timeFromTicks(sp.getSpellCooldown(), 1))); } catch (Throwable ignored) {}
        try { info.add(Component.literal("Cast: " + pretty(sp.getCastType().name()))); } catch (Throwable ignored) {}
        info.add(Component.literal(com.ironspellgrimoire.server.Awakening.isAwakenedSpell(ids.get(i)) ? "Unlocks at: Awakening" : "Unlocks at mastery " + unlockAt.get(i)));
        if (com.ironspellgrimoire.server.SpellEvolution.evolved(stack, ids.get(i)))   // its evolution
            info.add(Component.literal("\u2726 Evolved: +" + (com.ironspellgrimoire.server.Ascension.ascended(stack) ? "35% and a gold echo" : "20%")).withStyle(ChatFormatting.DARK_PURPLE));
        else info.add(Component.literal("Evolution: " + com.ironspellgrimoire.server.SpellEvolution.casts(stack, ids.get(i)) + " / " + com.ironspellgrimoire.server.SpellEvolution.EVOLVE_AT + " casts"));
        int yy = y;
        g.drawString(f, Component.literal("Spell").withStyle(ChatFormatting.BOLD), x, yy, RED_INK, false);
        yy += 12;
        for (Component cmp : info) {
            boolean always = cmp.getString().startsWith("Unlocks at") || cmp.getString().startsWith("Evolution");   // when you'll get it stays readable
            for (FormattedCharSequence l : f.split(always ? cmp : ink(cmp), w)) {
                if (yy > y + h - 8) return;
                g.drawString(f, l, x, yy, INK, false);
                yy += 10;
            }
        }
    }

    // ------------------------------------------------------------------ the Blood Grimoire's paths (server/BloodPaths)
    private int chosenPath(String spell) {
        return stack.hasTag() ? stack.getTag().getCompound("BloodPaths").getInt(spell) : 0;
    }

    /** At the foot of a Blood spell's right page: its two paths, the chosen one marked; open at mastery 5. */
    private void drawPaths(GuiGraphics g, Font f, int x, int y0, int w, int mx, int my) {
        if (book != GrimoireWheel.BLOOD || onCombos() || page >= ids.size()) return;
        String[] p = com.ironspellgrimoire.server.BloodPaths.PATHS.get(ids.get(page));
        if (p == null) return;
        boolean open = GrimoireMastery.level(stack) >= com.ironspellgrimoire.server.BloodPaths.NEEDS;
        int y = y0 + BH - 62, chosen = chosenPath(ids.get(page));
        g.fill(x, y - 3, x + w, y - 2, 0x60000000 | (INK & 0xFFFFFF));
        g.drawString(f, open ? "Path - choose one:" : "Paths open at mastery " + com.ironspellgrimoire.server.BloodPaths.NEEDS, x, y, RED_INK, false);
        for (int i = 0; i < 2; i++) {
            int yy = y + 11 + i * 20;
            boolean hot = open && inside(mx, my, x - 2, yy - 2, w + 4, 19), on = chosen == i + 1;
            if (on) g.fill(x - 2, yy - 2, x + w + 2, yy + 17, 0x40A0101E);
            else if (hot) g.fill(x - 2, yy - 2, x + w + 2, yy + 17, 0x20000000);
            g.drawString(f, (on ? "\u25C6 " : "\u25C7 ") + p[i * 2], x, yy, on ? RED_INK : open ? INK : INK_FAINT, false);
            List<FormattedCharSequence> lines = f.split(Component.literal(p[i * 2 + 1]), w - 6);
            if (!lines.isEmpty()) {
                g.pose().pushPose(); g.pose().translate(x + 6, yy + 9, 0); g.pose().scale(0.75F, 0.75F, 1F);
                g.drawString(f, lines.get(0), 0, 0, INK_SOFT, false);
                g.pose().popPose();
            }
        }
    }

    private boolean pathClick(double mx, double my, int x0, int y0) {
        if (book != GrimoireWheel.BLOOD || memories || treeMode || pathMode || upgMode || onCombos() || page >= ids.size()) return false;
        if (!com.ironspellgrimoire.server.BloodPaths.PATHS.containsKey(ids.get(page)) || GrimoireMastery.level(stack) < com.ironspellgrimoire.server.BloodPaths.NEEDS) return false;
        int x = x0 + BW / 2 + 14, w = BW / 2 - 30, y = y0 + BH - 62;
        for (int i = 0; i < 2; i++) {
            int yy = y + 11 + i * 20;
            if (!inside(mx, my, x - 2, yy - 2, w + 4, 19)) continue;
            String id = ids.get(page);
            com.ironspellgrimoire.network.AddonNetwork.CHANNEL.sendToServer(new com.ironspellgrimoire.network.TreePackets.Act(11 + i, id));
            net.minecraft.nbt.CompoundTag t = stack.getOrCreateTag().getCompound("BloodPaths");   // shown at once; the server keeps the real one
            t.putInt(id, i + 1);
            stack.getOrCreateTag().put("BloodPaths", t);
            Minecraft.getInstance().player.playSound(SoundEvents.BOOK_PAGE_TURN, 1.0F, 1.2F);
            return true;
        }
        return false;
    }

    private void ribbon(GuiGraphics g, Font f, int x0, int y, String label, boolean on, int mx, int my, int[] c) {
        boolean hot = inside(mx, my, x0 + BW + 7, y, 54, 16);
        g.fill(x0 + BW + 5, y, x0 + BW + 62, y + 16, 0xFF000000 | dark(c[2], 0.35F));
        g.fill(x0 + BW + 6, y + 1, x0 + BW + 61, y + 15, 0xFF000000 | (hot || on ? c[2] : c[4]));
        g.drawString(f, label, x0 + BW + 34 - f.width(label) / 2, y + 5, hot || on ? 0xFF000000 | c[4] : 0xFF000000 | c[5], false);
    }

    // ------------------------------------------------------------------ the Path: what's done, and what's next
    private void drawPath(GuiGraphics g, Font f, int x0, int y0, int mid) {
        ItemStack bk = treeBook();
        int lv = com.ironspellgrimoire.server.GrimoireMastery.level(bk), cap = com.ironspellgrimoire.server.GrimoireMastery.cap(bk);
        boolean exam = com.ironspellgrimoire.server.GrimoireMastery.ascended(bk), awake = com.ironspellgrimoire.server.Awakening.awakened(bk),
                asc = com.ironspellgrimoire.server.Ascension.ascended(bk);
        java.util.List<String> sp = com.ironspellgrimoire.server.SpellEvolution.spells(bk);
        int evolved = 0, best = 0;
        for (String id : sp) {
            if (com.ironspellgrimoire.server.SpellEvolution.evolved(bk, id)) evolved++;
            else best = Math.max(best, com.ironspellgrimoire.server.SpellEvolution.casts(bk, id));
        }
        boolean crown = com.ironspellgrimoire.server.SkillTree.has(bk, "crown");
        int mem = 0;
        for (int ch = 0; ch < com.ironspellgrimoire.server.MemoryStory.CH; ch++) if (MemoryClient.done(ch)) mem++;
        String[][] steps = {
                {lv >= 5 ? "y" : "n", "Mastery 5", "Cast this grimoire's spells: it grows with use. (" + lv + " / " + cap + ")"},
                {exam ? "y" : "n", "Magic Knight Exam", "At mastery 5, take the Exam at a Grimoire Altar. Pass it and mastery can reach 7."},
                {awake ? "y" : "n", "Awakening", "Pass the Exam and reach mastery 6, then awaken the grimoire at a Grimoire Altar."},
                {lv >= 8 ? "y" : "n", "Mastery 8", "An awakened grimoire grows to mastery 8."},
                {asc ? "y" : "n", "Ascension", "Awakened and at mastery 8: face your Reflection in the Ascension Trial, from the altar."},
                {evolved >= sp.size() && !sp.isEmpty() ? "y" : evolved > 0 ? "h" : "n", "Spell Evolution (" + evolved + " / " + sp.size() + ")",
                        "Each spell evolves after " + com.ironspellgrimoire.server.SpellEvolution.EVOLVE_AT + " casts. Closest: " + best + " / " + com.ironspellgrimoire.server.SpellEvolution.EVOLVE_AT + "."},
                {crown ? "y" : "n", "Crown of the Tree", "Learn five Element nodes on the Mana Tree, then its Crown."},
                {mem >= 3 ? "y" : mem > 0 ? "h" : "n", "Lost Memories (" + mem + " / 3)", "Bosses drop torn Memory Pages; each chapter ends with a Memory Guardian."}};
        int lx = x0 + 16, y = y0 + 12;
        g.drawString(f, Component.literal("The Path").withStyle(ChatFormatting.BOLD), lx, y, RED_INK, false);
        y += 14;
        int next = -1;
        for (int i = 0; i < steps.length; i++) {
            boolean ok = steps[i][0].equals("y"), half = steps[i][0].equals("h");
            if (!ok && next < 0) next = i;
            String mark = ok ? "\u2714" : half ? "\u25D0" : "\u2610";
            g.drawString(f, mark, lx, y, ok ? 0xFF2A6A20 : half ? 0xFF9A6A10 : INK_SOFT, false);
            g.drawString(f, steps[i][1], lx + 12, y, ok ? INK_SOFT : i == next ? RED_INK : INK, false);
            y += 15;
        }
        int rx = mid + 14, ry = y0 + 12, w = BW / 2 - 30;
        g.drawString(f, Component.literal(next < 0 ? "Every step taken" : "Next: " + steps[next][1]).withStyle(ChatFormatting.BOLD), rx, ry, RED_INK, false);
        ry += 16;
        String body = next < 0 ? "This grimoire has gone as far as a grimoire can. Its mage is a legend now." : steps[next][2];
        for (FormattedCharSequence l : f.split(Component.literal(body), w)) { g.drawString(f, l, rx, ry, INK, false); ry += 10; }
        ry += 8;
        for (FormattedCharSequence l : f.split(Component.literal("Mastery " + lv + " of " + cap + (awake ? " (awakened)" : exam ? " (Magic Knight)" : "") + "."), w)) { g.drawString(f, l, rx, ry, INK_SOFT, false); ry += 10; }
    }

    // ------------------------------------------------------------------ the Upgrades: everything bound into the grimoire, and what it adds up to
    private void drawUpgrades(GuiGraphics g, Font f, int x0, int y0, int mid) {
        ItemStack bk = treeBook();
        net.minecraft.nbt.CompoundTag t = bk.hasTag() ? bk.getTag() : new net.minecraft.nbt.CompoundTag();
        int lx = x0 + 16, y = y0 + 12, w = BW / 2 - 30;
        g.drawString(f, Component.literal("Bound Upgrades").withStyle(ChatFormatting.BOLD), lx, y, RED_INK, false);
        y += 14;
        boolean any = false;
        for (var k : com.ironspellgrimoire.server.GrimoireUpgrades.KINDS) {
            int n = com.ironspellgrimoire.server.GrimoireUpgrades.count(bk, k.id());
            if (n <= 0) continue;
            any = true;
            if (y > y0 + BH - 30) { g.drawString(f, "...", lx, y, INK_SOFT, false); break; }
            String head = k.name() + (k.max() > 1 ? "  \u00D7" + n : "");
            g.drawString(f, head, lx, y, INK, false);
            y += 9;
            g.pose().pushPose();
            g.pose().translate(lx + 4, y, 0);
            g.pose().scale(0.75F, 0.75F, 1F);
            String each = k.each();
            int ww = (int) ((w - 4) / 0.75F);
            if (f.width(each) > ww) each = f.plainSubstrByWidth(each, ww - 8) + "..";
            g.drawString(f, each, 0, 0, INK_SOFT, false);
            g.pose().popPose();
            y += 9;
        }
        if (!any) for (FormattedCharSequence l : f.split(Component.literal("Nothing bound yet. Bosses, bounties, quests and rifts give upgrades; bind them at a Grimoire Altar."), w)) { g.drawString(f, l, lx, y, INK_SOFT, false); y += 10; }
        int rx = mid + 14, ry = y0 + 12;
        g.drawString(f, Component.literal("On the Book").withStyle(ChatFormatting.BOLD), rx, ry, RED_INK, false);
        ry += 14;
        int sig = t.getInt("Sigil");
        String[] lines = {
                "Sigil: " + (sig > 0 && sig <= com.ironspellgrimoire.server.StarForge.SIGIL_SHORT.length ? com.ironspellgrimoire.server.StarForge.SIGIL_SHORT[sig - 1] + " (" + com.ironspellgrimoire.server.StarForge.SIGIL_EFFECTS[sig - 1] + ")" : "none"),
                "Ink: " + ink(t, "RiftInk", true) + ink(t, "RiftInk2", t.getBoolean("InkSlot2")) + ink(t, "RiftInk3", t.getBoolean("InkSlot3")),
                "Devil Shards: " + t.getInt("DevilShards"),
                "Empowered spells: " + t.getCompound("StarBoost").size() + ", mutated: " + t.getCompound("StarMut").size(),
                "Evolved spells: " + t.getCompound("Evolved").size(),
                "Mana Tree: " + com.ironspellgrimoire.server.SkillTree.spent(bk) + " leaves spent"};
        for (String ln : lines) for (FormattedCharSequence l : f.split(Component.literal(ln), w)) { g.drawString(f, l, rx, ry, INK, false); ry += 10; }
    }

    private static String ink(net.minecraft.nbt.CompoundTag t, String key, boolean open) {
        if (!open) return "";
        int r = t.getInt(key) - 1;
        String n = r >= 0 && r < com.ironspellgrimoire.item.RiftInkItem.NAMES.length ? com.ironspellgrimoire.item.RiftInkItem.NAMES[r] : "(empty page)";
        return (key.equals("RiftInk") ? "" : ", ") + n;
    }

    // ------------------------------------------------------------------ the Mana Tree
    private ItemStack treeBook() {
        Minecraft mc = Minecraft.getInstance();
        ItemStack live = mc.player == null ? null : Utils.getPlayerSpellbookStack(mc.player);
        return live != null && live.is(stack.getItem()) ? live : stack;
    }

    private boolean equipped() {
        Minecraft mc = Minecraft.getInstance();
        ItemStack live = mc.player == null ? null : Utils.getPlayerSpellbookStack(mc.player);
        return live != null && live.is(stack.getItem());
    }

    private int[] nodeXY(com.ironspellgrimoire.server.SkillTree.Node n, int x0, int y0) {
        int mid = x0 + BW / 2;
        int cx = n.branch() == com.ironspellgrimoire.server.SkillTree.ROOT ? x0 + 66 : n.branch() == com.ironspellgrimoire.server.SkillTree.BODY ? x0 + BW - 66 : mid;
        if (n.branch() == com.ironspellgrimoire.server.SkillTree.CROWN) return new int[]{mid, y0 + 30};
        return new int[]{cx + (n.lane() - 1) * 28, y0 + BH - 38 - (n.tier() - 1) * 31};
    }

    private com.ironspellgrimoire.server.SkillTree.Node nodeAt(double mx, double my, int x0, int y0) {
        for (var n : com.ironspellgrimoire.server.SkillTree.nodes(book)) {
            int[] p = nodeXY(n, x0, y0);
            int r = n.branch() == com.ironspellgrimoire.server.SkillTree.CROWN ? 10 : 8;
            if (Math.abs(mx - p[0]) <= r && Math.abs(my - p[1]) <= r) return n;
        }
        return null;
    }

    private boolean treeClick(double mx, double my, int x0, int y0) {
        Minecraft mc = Minecraft.getInstance();
        var n = nodeAt(mx, my, x0, y0);
        if (n != null) {
            if (equipped() && com.ironspellgrimoire.server.SkillTree.blocked(treeBook(), n, MemoryClient.chapter()) == null) {
                com.ironspellgrimoire.network.AddonNetwork.CHANNEL.sendToServer(new com.ironspellgrimoire.network.TreePackets.Act(0, n.id()));
                mc.player.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.8F, 1.3F);
            } else mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.3F, 0.6F);
            return true;
        }
        String rs = "Reset tree";
        int rw = font.width(rs);
        if (inside(mx, my, x0 + BW / 2 - rw / 2 - 2, y0 + BH - 14, rw + 4, 11)) {
            com.ironspellgrimoire.network.AddonNetwork.CHANNEL.sendToServer(new com.ironspellgrimoire.network.TreePackets.Act(1, ""));
            mc.player.playSound(SoundEvents.BOOK_PAGE_TURN, 0.8F, 0.7F);
            return true;
        }
        return false;
    }

    private static void line(GuiGraphics g, int x1, int y1, int x2, int y2, int col) {
        int n = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        for (int i = 0; i <= n; i++) {
            int x = x1 + (x2 - x1) * i / Math.max(1, n), y = y1 + (y2 - y1) * i / Math.max(1, n);
            g.fill(x, y, x + 2, y + 2, col);
        }
    }

    /** The Mana Tree, across both pages: Root on the left, the grimoire's own Element up the middle to its Crown, Body on the right. */
    private void drawTree(GuiGraphics g, Font f, int x0, int y0, int mid, int mx, int my, int[] c) {
        ItemStack bk = treeBook();
        boolean eq = equipped();
        int mem = MemoryClient.chapter();
        int earned = com.ironspellgrimoire.server.SkillTree.earned(bk, mem), free = earned - com.ironspellgrimoire.server.SkillTree.spent(bk);
        g.drawString(f, Component.literal("Mana Tree").withStyle(ChatFormatting.BOLD), x0 + 16, y0 + 10, RED_INK, false);
        String lv = "\u2618 " + free + " / " + earned + " leaves";
        g.drawString(f, lv, x0 + BW - 26 - f.width(lv), y0 + 10, free > 0 ? 0xFF2A6A20 : INK_SOFT, false);
        String[] lab = {"Root", com.ironspellgrimoire.server.SkillTree.CROWNS.length > 0 ? "Element" : "", "Body"};
        g.drawString(f, lab[0], x0 + 66 - f.width(lab[0]) / 2, y0 + BH - 26, INK_SOFT, false);
        g.drawString(f, lab[1], mid - f.width(lab[1]) / 2, y0 + BH - 26, INK_SOFT, false);
        g.drawString(f, lab[2], x0 + BW - 66 - f.width(lab[2]) / 2, y0 + BH - 26, INK_SOFT, false);
        var nodes = com.ironspellgrimoire.server.SkillTree.nodes(book);
        // the branches first
        for (var n : nodes) {
            int[] p = nodeXY(n, x0, y0);
            if (n.parent() != null) {
                var par = com.ironspellgrimoire.server.SkillTree.node(book, n.parent());
                int[] q = nodeXY(par, x0, y0);
                boolean on = com.ironspellgrimoire.server.SkillTree.has(bk, n.id());
                line(g, q[0] - 1, q[1] - 1, p[0] - 1, p[1] - 1, on ? 0xFF000000 | c[2] : 0x605A4630);
            } else if (n.branch() == com.ironspellgrimoire.server.SkillTree.CROWN) {
                for (String e : new String[]{"e6", "e7"}) {
                    int[] q = nodeXY(com.ironspellgrimoire.server.SkillTree.node(book, e), x0, y0);
                    line(g, q[0] - 1, q[1] - 1, p[0] - 1, p[1] - 1, com.ironspellgrimoire.server.SkillTree.has(bk, "crown") ? 0xFFD8AE50 : 0x405A4630);
                }
            }
        }
        com.ironspellgrimoire.server.SkillTree.Node hover = nodeAt(mx, my, x0, y0);
        for (var n : nodes) {
            int[] p = nodeXY(n, x0, y0);
            boolean crown = n.branch() == com.ironspellgrimoire.server.SkillTree.CROWN;
            int r = crown ? 10 : 7;
            boolean has = com.ironspellgrimoire.server.SkillTree.has(bk, n.id());
            boolean can = eq && !has && com.ironspellgrimoire.server.SkillTree.blocked(bk, n, mem) == null;
            int border = has ? 0xFFD8AE50 : can ? INK : 0x705A4630;
            int fill = has ? 0xFF000000 | c[4] : can ? 0xFFF2E6C8 : 0x30000000;
            if (can) {   // ready to learn: it breathes
                float k = 0.5F + 0.5F * Mth.sin(ticks * 0.25F);
                g.fill(p[0] - r - 2, p[1] - r - 2, p[0] + r + 2, p[1] + r + 2, ((int) (0x60 * k) << 24) | 0x2A6A20);
            }
            g.fill(p[0] - r - 1, p[1] - r - 1, p[0] + r + 1, p[1] + r + 1, border);
            g.fill(p[0] - r, p[1] - r, p[0] + r, p[1] + r, fill);
            if (crown && has) { g.fill(p[0] - r - 3, p[1] - r - 3, p[0] + r + 3, p[1] - r - 2, 0xFFD8AE50); g.fill(p[0] - r - 3, p[1] + r + 2, p[0] + r + 3, p[1] + r + 3, 0xFFD8AE50); }
            String glyph = crown ? "\u2655" : n.name().substring(0, 1);
            int gc = has ? 0xFF000000 | c[5] : can ? INK : 0x905A4630;
            g.drawString(f, glyph, p[0] - f.width(glyph) / 2 + 1, p[1] - 3, gc, false);
            if (n == hover) g.fill(p[0] - r, p[1] - r, p[0] + r, p[1] + r, 0x30FFFFFF);
        }
        String rs = "Reset tree";
        int rw = f.width(rs);
        boolean rh = inside(mx, my, x0 + BW / 2 - rw / 2 - 2, y0 + BH - 14, rw + 4, 11);
        g.drawString(f, rs, x0 + BW / 2 - rw / 2, y0 + BH - 12, rh ? RED_INK : INK_SOFT, false);
        if (!eq) {   // on the left page, clear of the crown in the middle
            int wy = y0 + 22;
            for (FormattedCharSequence l : f.split(Component.literal("Equip this grimoire to grow its tree."), mid - x0 - 50)) { g.drawString(f, l, x0 + 16, wy, RED_INK, false); wy += 9; }
        }
        if (hover != null) {
            java.util.List<Component> tip = new java.util.ArrayList<>();
            tip.add(Component.literal(hover.name()).withStyle(hover.branch() == com.ironspellgrimoire.server.SkillTree.CROWN ? ChatFormatting.GOLD : ChatFormatting.WHITE, ChatFormatting.BOLD));
            for (FormattedCharSequence l : f.split(Component.literal(com.ironspellgrimoire.server.SkillTree.describe(book, hover)), 200))
                tip.add(Component.literal(toStr(l)).withStyle(ChatFormatting.GRAY));
            tip.add(Component.literal("Cost: " + hover.cost() + (hover.cost() == 1 ? " leaf" : " leaves")).withStyle(ChatFormatting.DARK_GREEN));
            String why = com.ironspellgrimoire.server.SkillTree.blocked(bk, hover, mem);
            tip.add(why == null ? Component.literal(eq ? "Click to learn" : "Equip the grimoire to learn").withStyle(ChatFormatting.YELLOW)
                    : Component.literal(why).withStyle(why.equals("Learned") ? ChatFormatting.GREEN : ChatFormatting.RED));
            g.renderComponentTooltip(f, tip, mx, my);
        } else if (inside(mx, my, x0 + BW - 26 - f.width(lv), y0 + 8, f.width(lv), 12)) {
            g.renderComponentTooltip(f, java.util.List.of(Component.literal("Grimoire Leaves").withStyle(ChatFormatting.GREEN),
                    Component.literal("2 per mastery level, 1 per evolved spell (up to 6),").withStyle(ChatFormatting.GRAY),
                    Component.literal("2 each for the Exam, Awakening and Ascension,").withStyle(ChatFormatting.GRAY),
                    Component.literal("1 per Lost Memory chapter recovered.").withStyle(ChatFormatting.GRAY)), mx, my);
        } else if (rh) {
            g.renderComponentTooltip(f, java.util.List.of(Component.literal("Reset the tree").withStyle(ChatFormatting.GOLD),
                    Component.literal("Stand by a Grimoire Altar. The first reset is free;").withStyle(ChatFormatting.GRAY),
                    Component.literal("after that, " + com.ironspellgrimoire.server.SkillTree.RESPEC_COST + " merit stars.").withStyle(ChatFormatting.GRAY)), mx, my);
        }
    }

    private static String toStr(FormattedCharSequence s) {
        StringBuilder b = new StringBuilder();
        s.accept((i, st, cp) -> { b.appendCodePoint(cp); return true; });
        return b.toString();
    }

    /** The Memories: the first binder's story, two pages to a spread, in ink - a lost page is a torn blank. */
    private void drawMemories(GuiGraphics g, Font f, int x0, int y0, int mid, int mx, int my) {
        int ch = mpage / 3, left = (mpage % 3) * 2;
        String num = (mpage + 1) + " / " + MSPREADS;
        g.drawString(f, num, mid - f.width(num) / 2, y0 + BH - 13, INK_SOFT, false);
        if (mpage > 0) g.drawString(f, "<", x0 + 6, y0 + BH - 15, inside(mx, my, x0 - 2, y0 + BH - 20, 22, 16) ? RED_INK : INK, false);
        if (mpage < MSPREADS - 1) g.drawString(f, ">", x0 + BW - 12, y0 + BH - 15, inside(mx, my, x0 + BW - 20, y0 + BH - 20, 22, 16) ? RED_INK : INK, false);
        memoryPage(g, f, ch, left, x0 + 16, y0 + 12, BW / 2 - 30, BH - 32);
        memoryPage(g, f, ch, left + 1, mid + 14, y0 + 12, BW / 2 - 30, BH - 32);
    }

    private void memoryPage(GuiGraphics g, Font f, int ch, int pg, int x, int y, int w, int h) {
        int yy = y;
        if (pg == 0) {   // the chapter's title, in red ink
            g.drawString(f, Component.literal("Chapter " + com.ironspellgrimoire.server.MemoryStory.CHAPTERS[ch]).withStyle(ChatFormatting.BOLD), x, yy, RED_INK, false);
            int n = 0;
            for (int i = 0; i < com.ironspellgrimoire.server.MemoryStory.PER; i++) if (MemoryClient.read(ch, i)) n++;
            yy += 10;
            g.drawString(f, n + " of " + com.ironspellgrimoire.server.MemoryStory.PER + " pages", x, yy, INK_SOFT, false);
            yy += 13;
        } else {
            String hd = "- " + (pg + 1) + " -";
            g.drawString(f, hd, x + (w - f.width(hd)) / 2, yy, INK_SOFT, false);
            yy += 13;
        }
        if (!MemoryClient.read(ch, pg)) {   // still lost: a torn blank, with a ragged edge where it was ripped out
            for (int i = 0; i < w; i += 3) {
                int j = (int) (Math.abs(Math.sin(i * 1.7 + pg * 3.1 + ch)) * 5);
                g.fill(x + i, yy + 2 + j, x + i + 3, yy + 3 + j, 0x405A4630);
            }
            yy += 14;
            for (FormattedCharSequence l : f.split(Component.literal("This page is still lost.").withStyle(ChatFormatting.ITALIC), w)) { g.drawString(f, l, x, yy, INK_SOFT, false); yy += 10; }
            String hint = pg == com.ironspellgrimoire.server.MemoryStory.PER - 1 ? "The chapter's Memory Guardian keeps this one." : "One of the great foes of the world carries it.";
            yy += 4;
            for (FormattedCharSequence l : f.split(Component.literal(hint), w)) { g.drawString(f, l, x, yy, 0x905A4630, false); yy += 10; }
            return;
        }
        float sc = 0.82F;
        int ww = (int) (w / sc);
        g.pose().pushPose();
        g.pose().translate(x, yy, 0);
        g.pose().scale(sc, sc, 1F);
        int ly = 0;
        boolean last = pg == com.ironspellgrimoire.server.MemoryStory.PER - 1;   // the guardian's own page, spoken: a darker ink
        for (FormattedCharSequence l : f.split(Component.literal(com.ironspellgrimoire.server.MemoryStory.PAGES[ch][pg]).withStyle(last ? ChatFormatting.ITALIC : ChatFormatting.RESET), ww)) {
            g.drawString(f, l, 0, ly, last ? RED_INK : INK, false);
            ly += 10;
        }
        g.pose().popPose();
    }

    /** The combo page: this grimoire's combos - the name and the two spells on the left, what it does on the right. */
    private void drawCombos(GuiGraphics g, Font f, int lx, int rx, int y, int w, int h) {
        g.drawString(f, Component.literal("Combos").withStyle(ChatFormatting.BOLD), lx, y, RED_INK, false);
        g.drawString(f, Component.literal("What they do").withStyle(ChatFormatting.BOLD), rx, y, RED_INK, false);
        int ly = y + 14, ry = y + 14;
        int lvl = GrimoireMastery.level(stack);
        boolean any = false;
        for (ComboChain.Combo cb : ComboChain.COMBOS) {
            int a = ids.indexOf(cb.first()), b = ids.indexOf(cb.second());
            if (a < 0 || b < 0) continue;
            any = true;
            int need = Math.max(unlockAt.get(a), unlockAt.get(b));
            hidden = MasteryClient.enabled() && lvl < need;   // a combo whose spells you haven't earned yet stays scribbled
            Component name = Component.literal(cb.name()).withStyle(ChatFormatting.BOLD);
            Component seq = Component.translatable("spell.iron_spell_grimoire." + cb.first()).append(Component.literal("  >  "))
                    .append(Component.translatable("spell.iron_spell_grimoire." + cb.second()));
            int before = ly;
            for (FormattedCharSequence l : f.split(ink(name), w)) { g.drawString(f, l, lx, ly, INK, false); ly += 10; }
            for (FormattedCharSequence l : f.split(ink(seq), w)) { g.drawString(f, l, lx, ly, INK_SOFT, false); ly += 10; }
            if (hidden) { g.drawString(f, "Needs mastery " + need, lx, ly, RED_INK, false); ly += 10; }
            for (FormattedCharSequence l : f.split(ink(Component.literal(cb.what())), w)) { g.drawString(f, l, rx, ry, INK, false); ry += 10; }
            ly += 6; ry += 6;
            int used = Math.max(ly, ry);
            ly = ry = used;   // keep both pages' rows lined up
            if (ly > y + h - 50) break;
        }
        hidden = false;
        if (!any) g.drawString(f, "This grimoire has no combos.", lx, ly, INK_SOFT, false);
        String hint = "Cast the second spell within 4 seconds of the first.";
        var hl = f.split(Component.literal(hint), w);
        int hy = y + h - 18 - hl.size() * 9;   // kept clear of the page number below
        for (FormattedCharSequence l : hl) { g.drawString(f, l, rx, hy, INK_SOFT, false); hy += 9; }
    }

    private static String pretty(String s) {
        s = s.toLowerCase().replace('_', ' ');
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static void frame(GuiGraphics g, int x, int y, int w, int h, int col) {
        g.fill(x, y, x + w, y + 1, col); g.fill(x, y + h - 1, x + w, y + h, col);
        g.fill(x, y, x + 1, y + h, col); g.fill(x + w - 1, y, x + w, y + h, col);
    }

    private static int dark(int rgb, float k) {
        return ((int) ((rgb >> 16 & 255) * k) << 16) | ((int) ((rgb >> 8 & 255) * k) << 8) | (int) ((rgb & 255) * k);
    }
}
