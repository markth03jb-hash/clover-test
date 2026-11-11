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
        if (inside(mx, my, x0 - 2, y0 + BH - 20, 22, 16)) { turn(-1); return true; }
        if (inside(mx, my, x0 + BW - 20, y0 + BH - 20, 22, 16)) { turn(1); return true; }
        if (inside(mx, my, x0 + BW + 7, y0 + 24, 46, 16)) {   // the ribbon tab on the edge of the book: jump to the combos (or back to the spells)
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
        int[] c = GrimoireWheel.themeColors(book);   // pageIn, pageOut, trim, accent, panel, title
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

        if (ids.isEmpty()) return;
        String num = (page + 1) + " / " + pages();
        g.drawString(f, num, mid - f.width(num) / 2, y0 + BH - 13, INK_SOFT, false);
        if (page > 0) g.drawString(f, "<", x0 + 6, y0 + BH - 15, inside(mx, my, x0 - 2, y0 + BH - 20, 22, 16) ? RED_INK : INK, false);
        if (page < pages() - 1) g.drawString(f, ">", x0 + BW - 12, y0 + BH - 15, inside(mx, my, x0 + BW - 20, y0 + BH - 20, 22, 16) ? RED_INK : INK, false);

        // the ribbon tab on the book's edge: Combos (or back to the Spells)
        boolean tabHot = inside(mx, my, x0 + BW + 7, y0 + 24, 46, 16);
        g.fill(x0 + BW + 5, y0 + 24, x0 + BW + 54, y0 + 40, 0xFF000000 | dark(c[2], 0.35F));
        g.fill(x0 + BW + 6, y0 + 25, x0 + BW + 53, y0 + 39, 0xFF000000 | (tabHot ? c[2] : c[4]));
        String tab = onCombos() ? "Spells" : "Combos";
        g.drawString(f, tab, x0 + BW + 30 - f.width(tab) / 2, y0 + 29, tabHot ? 0xFF000000 | c[4] : 0xFF000000 | c[5], false);
        AbstractSpell sp = onCombos() ? null : spell(page);
        if (onCombos()) {
            hidden = false;
            drawCombos(g, f, x0 + 16, mid + 14, y0 + 14, BW / 2 - 30, BH - 34);
        } else if (sp != null) {
            hidden = !unlocked(page);   // not unlocked yet: the page is written, but scribbled - you can't read it
            drawLeft(g, f, sp, x0 + 16, y0 + 14, BW / 2 - 30, BH - 34, c);
            drawRight(g, f, sp, page, mid + 14, y0 + 14, BW / 2 - 30, BH - 34);
            hidden = false;
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
        String key = "spell.iron_spell_grimoire." + ids.get(page) + ".guide";
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
        int yy = y;
        g.drawString(f, Component.literal("Spell").withStyle(ChatFormatting.BOLD), x, yy, RED_INK, false);
        yy += 12;
        for (Component cmp : info) {
            boolean always = cmp.getString().startsWith("Unlocks at");   // when you'll get it stays readable
            for (FormattedCharSequence l : f.split(always ? cmp : ink(cmp), w)) {
                if (yy > y + h - 8) return;
                g.drawString(f, l, x, yy, INK, false);
                yy += 10;
            }
        }
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
            if (ly > y + h - 12) break;
        }
        hidden = false;
        if (!any) g.drawString(f, "This grimoire has no combos.", lx, ly, INK_SOFT, false);
        String hint = "Cast the second spell within 4 seconds of the first.";
        int hy = y + h - 12;
        for (FormattedCharSequence l : f.split(Component.literal(hint), w)) { g.drawString(f, l, rx, hy, INK_SOFT, false); hy += 9; }
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
