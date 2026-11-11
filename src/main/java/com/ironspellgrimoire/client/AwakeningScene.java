package com.ironspellgrimoire.client;

import static com.ironspellgrimoire.client.BookUi.*;

import com.ironspellgrimoire.network.FxPacket;
import com.ironspellgrimoire.server.Awakening;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The Grimoire Awakening on the awakening player's screen (server/Awakening), about five seconds:
 * the world goes grey and still behind letterbox bars; the grimoire, in its own colours, flies up to the middle of the
 * screen and swings open; its pages turn over by themselves; then the right-hand page writes itself - a sigil drawn
 * round in red ink and the new spell's name letter by letter - while light pours out from behind the book; a white
 * flash, AWAKENING across the screen, rings bursting out, and it all fades back to the fight.
 */
@Mod.EventBusSubscriber(modid = "iron_spell_grimoire", value = Dist.CLIENT)
public final class AwakeningScene {
    private AwakeningScene() {}

    private static int age = -1, life;
    /** 0: an Awakening; 1: an Ascension (server/Ascension) - the same scene in gold, and the page writes "Ascended". */
    private static int mode;
    private static String spellName = "", bookName = "";
    private static int[] col = {0, 0, 0xE0B060, 0xFFD27A, 0x2A1A10, 0xF0E0B0};

    public static void start(FxPacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || p.id != mc.player.getId()) return;
        ItemStack book = Utils.getPlayerSpellbookStack(mc.player);
        String id = Awakening.spellFor(book);
        mode = p.sweep;
        spellName = mode == 1 ? "Ascended" : id == null ? "" : Component.translatable("spell.iron_spell_grimoire." + id).getString();
        bookName = book == null ? "" : book.getHoverName().getString();
        col = colours().clone();
        if (mode == 1) col[3] = 0xFFD870;
        life = p.life;
        age = 0;
        mc.player.playSound(SoundEvents.BOOK_PAGE_TURN, 1f, 0.6f);
    }

    public static boolean running() { return age >= 0; }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END || age < 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) { age = -1; return; }
        age++;
        if (age >= 26 && age < 50 && (age - 26) % 6 == 0) mc.player.playSound(SoundEvents.BOOK_PAGE_TURN, 1f, 0.9f + (age - 26) * 0.02f);
        if (age == 50) mc.player.playSound(SoundEvents.ENCHANTMENT_TABLE_USE, 1f, 0.7f);
        if (age > 50 && age < 72 && age % 3 == 0) mc.player.playSound(SoundEvents.VILLAGER_WORK_CARTOGRAPHER, 0.35f, 1.4f + (age % 6) * 0.05f);   // the pen
        if (age >= life) age = -1;
    }

    private static float ease(float k) { k = Mth.clamp(k, 0f, 1f); return k * k * (3 - 2 * k); }

    @SubscribeEvent
    public static void onRender(RenderGuiEvent.Post e) {
        if (age < 0) return;
        Minecraft mc = Minecraft.getInstance();
        GuiGraphics g = e.getGuiGraphics();
        Font f = mc.font;
        float t = age + e.getPartialTick();
        int w = g.guiWidth(), h = g.guiHeight();
        float fadeOut = 1f - ease((t - (life - 10)) / 10f);
        // the world stops: grey wash, a darker edge, letterbox bars
        float wash = ease(t / 8f) * fadeOut;
        g.fill(0, 0, w, h, (int) (0x90 * wash) << 24 | 0x1C1C22);
        for (int i = 0; i < 6; i++) {
            int a = (int) (0x18 * wash);
            g.fill(0, 0, w, 8 + i * 6, a << 24); g.fill(0, h - 8 - i * 6, w, h, a << 24);
            g.fill(0, 0, 8 + i * 8, h, a << 24); g.fill(w - 8 - i * 8, 0, w, h, a << 24);
        }
        int bar = (int) (h * 0.11f * ease(t / 10f) * fadeOut);
        g.fill(0, 0, w, bar, 0xFF000000); g.fill(0, h - bar, w, h, 0xFF000000);

        // light pouring out from behind the book while the page writes (and after)
        float glow = ease((t - 46) / 20f) * fadeOut;
        if (glow > 0) rays(g, w / 2, h / 2, Math.max(w, h), t, col[3], glow);

        // the book: flies up to the middle, opens, turns its pages, writes
        float s = ease((t - 4) / 12f);
        if (s > 0 && fadeOut > 0) {
            g.pose().pushPose();
            float sc = 0.9f * (0.4f + 0.6f * s) * (t > 72 ? 1f + 0.04f * ease((t - 72) / 6f) : 1f);
            g.pose().translate(w / 2f, h / 2f + (1 - s) * h * 0.5f, 0);
            g.pose().scale(sc, sc, 1f);
            g.pose().translate(-w / 2f, -h / 2f, 0);
            drawBook(g, f, w, h, t);
            g.pose().popPose();
        }

        // the flash, AWAKENING, rings bursting out
        if (t >= 72) {
            float fl = 1f - ease((t - 72) / 8f);
            if (fl > 0) g.fill(0, 0, w, h, (int) (0xF0 * fl) << 24 | 0xFFFFFF);
            for (int r = 0; r < 3; r++) {
                float k = (t - 72 - r * 4) / 18f;
                if (k > 0 && k < 1) ring(g, w / 2, h / 2, (int) (40 + k * Math.max(w, h) * 0.7f), (int) (0xC0 * (1 - k) * fadeOut), r == 1 ? 0xFFFFFF : col[3]);
            }
            float tk = ease((t - 74) / 6f) * fadeOut;
            if (tk > 0) {
                int a = Math.max(4, (int) (255 * tk));
                g.pose().pushPose();
                g.pose().translate(w / 2f, h * 0.2f, 0);
                g.pose().scale(3.2f, 3.2f, 1);
                String title = mode == 1 ? "A S C E N S I O N" : "A W A K E N I N G";
                g.drawString(f, title, -f.width(title) / 2, 0, a << 24 | (col[3] & 0xFFFFFF), true);
                g.pose().popPose();
                g.pose().pushPose();
                g.pose().translate(w / 2f, h * 0.8f, 0);
                g.pose().scale(1.6f, 1.6f, 1);
                String sub = mode == 1 ? bookName : spellName;
                g.drawString(f, sub, -f.width(sub) / 2, 0, a << 24 | 0xFFFFFF, true);
                g.pose().popPose();
            }
        }
    }

    private static void drawBook(GuiGraphics g, Font f, int w, int h, float t) {
        int x0 = x0(w), y0 = y0(h), mid = x0 + BW / 2;
        float open = ease((t - 14) / 10f);
        if (open < 1f) {   // still closed (or swinging open): the cover over the right half, turning on the spine
            int half = BW / 2;
            g.fill(mid - 4, y0 - 6, mid + 4, y0 + BH + 6, 0xFF000000 | dark(col[2], 0.35F));   // the spine
            if (open < 0.5f) {
                int cw = (int) (half * Mth.cos(open * Mth.PI) + 0.5f);
                cover(g, mid, y0 - 6, cw + 7, BH + 12);
            } else {
                book(g, f, w, h, col, Component.literal(""), null);
                g.fill(x0 - 9, y0 - 8, mid, y0 + BH + 8, 0xFF000000 | dark(col[2], 0.35F));
                int cw = (int) (half * -Mth.cos(open * Mth.PI) + 0.5f);
                cover(g, mid - cw - 7, y0 - 6, cw + 7, BH + 12);
            }
            return;
        }
        book(g, f, w, h, col, Component.literal(bookName), null);
        int lx = x0 + 16, rx = mid + 14, pw = BW / 2 - 30, top = y0 + 14;
        // the pages already written: faint lines of old ink
        for (int i = 0; i < 12; i++) {
            int y = top + 22 + i * 10, len = pw - 10 - (i * 37 % 30);
            g.fill(lx + 4, y, lx + 4 + len, y + 1, 0x50000000 | (INK & 0xFFFFFF));
        }
        heading(g, f, bookName.length() > 22 ? bookName.substring(0, 20) + ".." : bookName, lx, pw, top);
        // the pages turning over by themselves
        if (t >= 26 && t < 50) turning(g, w, h, ((t - 26) % 6) / 6f);
        // the new page writes itself
        float wr = (t - 50) / 22f;
        if (wr <= 0) return;
        wr = Math.min(1f, wr);
        int cx = rx + pw / 2, cy = top + 58, r = 34;
        int steps = (int) (180 * wr);
        for (int i = 0; i < steps; i++) {   // the sigil's circle
            double a = -Math.PI / 2 + i * Math.PI * 2 / 180;
            int x = cx + (int) Math.round(Math.cos(a) * r), y = cy + (int) Math.round(Math.sin(a) * r);
            g.fill(x, y, x + 2, y + 2, mode == 1 ? 0xFFB8860B : RED_INK);
            int x2 = cx + (int) Math.round(Math.cos(a) * (r - 5)), y2 = cy + (int) Math.round(Math.sin(a) * (r - 5));
            if (i % 2 == 0) g.fill(x2, y2, x2 + 1, y2 + 1, RED_INK);
        }
        if (wr > 0.45f) {   // a star inside it, line by line
            float k = (wr - 0.45f) / 0.55f;
            int lines = (int) (5 * k + 0.999f);
            for (int l = 0; l < lines; l++) {
                double a0 = -Math.PI / 2 + l * 4 * Math.PI / 5, a1 = a0 + 4 * Math.PI / 5;
                float lk = l == lines - 1 ? (5 * k - l) : 1f;
                line(g, cx + Math.cos(a0) * (r - 5), cy + Math.sin(a0) * (r - 5), cx + Math.cos(a1) * (r - 5), cy + Math.sin(a1) * (r - 5), Mth.clamp(lk, 0f, 1f), RED_INK);
            }
        }
        int n = (int) (spellName.length() * Mth.clamp((wr - 0.2f) / 0.8f, 0f, 1f));   // the name, letter by letter
        String shown = spellName.substring(0, n);
        int ny = cy + r + 10;
        for (var l : f.split(Component.literal(shown).withStyle(s -> s.withBold(true)), pw)) {
            g.drawString(f, l, cx - f.width(l) / 2, ny, RED_INK, false);
            ny += 10;
        }
        if (n < spellName.length() && (int) (t * 2) % 2 == 0) {   // the pen's nib
            g.fill(cx + f.width(shown) / 2 + 2, ny - 9, cx + f.width(shown) / 2 + 3, ny - 1, INK);
        }
    }

    /** The front cover: the grimoire's own leather, trim and a gem in the middle. */
    private static void cover(GuiGraphics g, int x, int y, int cw, int chh) {
        if (cw <= 1) return;
        g.fill(x, y, x + cw, y + chh, 0xFF000000 | col[4]);
        frame(g, x + 2, y + 2, cw - 4, chh - 4, 0xFF000000 | col[2]);
        frame(g, x + 5, y + 5, Math.max(1, cw - 10), chh - 10, 0xFF000000 | dark(col[2], 0.7F));
        int gx = x + cw / 2, gy = y + chh / 2;
        int gs = Math.max(1, Math.min(10, cw / 6));
        g.fill(gx - gs, gy - gs, gx + gs, gy + gs, 0xFF000000 | col[3]);
        g.fill(gx - gs / 2, gy - gs / 2, gx, gy, 0xC0FFFFFF);
    }

    private static void rays(GuiGraphics g, int cx, int cy, int len, float t, int rgb, float a) {
        for (int i = 0; i < 18; i++) {
            double ang = i * Math.PI * 2 / 18 + t * 0.01;
            int alpha = (int) (0x50 * a * (i % 2 == 0 ? 1f : 0.55f));
            for (int k = 30; k < len; k += 3) {
                double wdt = 1 + k * 0.05;
                int x = cx + (int) (Math.cos(ang) * k), y = cy + (int) (Math.sin(ang) * k);
                int fa = (int) (alpha * (1 - k / (float) len));
                if (fa <= 0) break;
                g.fill(x - (int) wdt / 2, y - (int) wdt / 2, x + (int) Math.ceil(wdt / 2), y + (int) Math.ceil(wdt / 2), fa << 24 | (rgb & 0xFFFFFF));
            }
        }
    }

    private static void ring(GuiGraphics g, int cx, int cy, int r, int a, int rgb) {
        if (a <= 0) return;
        int n = Math.max(60, r);
        for (int i = 0; i < n; i++) {
            double ang = i * Math.PI * 2 / n;
            int x = cx + (int) (Math.cos(ang) * r), y = cy + (int) (Math.sin(ang) * r);
            g.fill(x - 1, y - 1, x + 2, y + 2, a << 24 | (rgb & 0xFFFFFF));
        }
    }

    private static void line(GuiGraphics g, double x0, double y0, double x1, double y1, float k, int c) {
        double ex = x0 + (x1 - x0) * k, ey = y0 + (y1 - y0) * k;
        int n = (int) Math.max(1, Math.hypot(ex - x0, ey - y0));
        for (int i = 0; i <= n; i++) {
            int x = (int) Math.round(x0 + (ex - x0) * i / n), y = (int) Math.round(y0 + (ey - y0) * i / n);
            g.fill(x, y, x + 1, y + 1, c);
        }
    }
}
