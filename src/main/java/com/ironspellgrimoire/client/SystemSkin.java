package com.ironspellgrimoire.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/**
 * The System's look (the Shadow Grimoire's windows: Status, Market, Job, the Army): a translucent holographic panel -
 * a hex grid and scanlines behind, a scan band drifting down it, glowing rims, bright corner brackets, a
 * "[ SYSTEM ]" chip by a title that flickers as the window boots - which unfolds from a line when it opens.
 * Everything is drawn here; no textures.
 */
public final class SystemSkin {
    private SystemSkin() {}

    /** After a System window draws its contents: the part not yet unfolded is covered. */
    @net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = "iron_spell_grimoire", value = net.minecraftforge.api.distmarker.Dist.CLIENT)
    public static final class Boot {
        @net.minecraftforge.eventbus.api.SubscribeEvent
        public static void after(net.minecraftforge.client.event.ScreenEvent.Render.Post e) {
            if (e.getScreen() instanceof ShadowUiScreen s) s.boot(e.getGuiGraphics(), e.getPartialTick());
            else if (e.getScreen() instanceof ShadowArmyScreen s) s.boot(e.getGuiGraphics(), e.getPartialTick());
        }
    }

    static final int RIM = 0xFF3C8CFF, RIM_HI = 0xFF9AD8FF, CYAN = 0xFF7FE8FF;

    /** The dark round the window, the panel, its grid and lines, the brackets and the header. age: ticks since it opened. */
    static void window(GuiGraphics g, Font font, int x0, int y0, int w, int h, float age, String title) {
        g.drawManaged(() -> windowBody(g, font, x0, y0, w, h, age, title));   // thousands of small fills: one draw, not one each
    }

    private static void windowBody(GuiGraphics g, Font font, int x0, int y0, int w, int h, float age, String title) {
        float t = age;
        int sw = g.guiWidth(), sh = g.guiHeight();
        g.fillGradient(0, 0, sw, sh, 0xC0010308, 0xE0030818);
        // the panel: deep blue glass
        g.fillGradient(x0, y0, x0 + w, y0 + h, 0xE8061428, 0xF0030A18);
        // a hex grid of faint points
        for (int yy = y0 + 6, row = 0; yy < y0 + h - 4; yy += 9, row++)
            for (int xx = x0 + 6 + (row % 2) * 6; xx < x0 + w - 4; xx += 12) g.fill(xx, yy, xx + 1, yy + 1, 0x283C8CFF);
        // scanlines, and a brighter band drifting down
        for (int yy = y0 + 1; yy < y0 + h - 1; yy += 3) g.fill(x0 + 1, yy, x0 + w - 1, yy + 1, 0x0C7FB8FF);
        int band = y0 + (int) ((t * 1.6f) % (h + 30)) - 15;
        for (int k = 0; k < 10; k++) {
            int yy = band + k;
            if (yy > y0 && yy < y0 + h - 1) g.fill(x0 + 1, yy, x0 + w - 1, yy + 1, (Math.max(0, 0x16 - Math.abs(k - 5) * 4) << 24) | 0x7FD0FF);
        }
        // the rims: an outer glow, the line, an inner hairline
        float pulse = 0.5f + 0.5f * Mth.sin(t * 0.09f);
        frame(g, x0 - 2, y0 - 2, w + 4, h + 4, (int) (0x18 + 0x18 * pulse) << 24 | 0x3C8CFF);
        frame(g, x0 - 1, y0 - 1, w + 2, h + 2, (int) (0x30 + 0x30 * pulse) << 24 | 0x3C8CFF);
        frame(g, x0, y0, w, h, lerp(0xFF1C4890, RIM, pulse));
        frame(g, x0 + 3, y0 + 3, w - 6, h - 6, 0x302A70D0);
        // bright corner brackets
        int L = 12;
        for (int[] c : new int[][]{{x0, y0, 1, 1}, {x0 + w, y0, -1, 1}, {x0, y0 + h, 1, -1}, {x0 + w, y0 + h, -1, -1}}) {
            int cx = c[0] - (c[2] < 0 ? 2 : 0), cy = c[1] - (c[3] < 0 ? 2 : 0);
            g.fill(Math.min(cx, cx + c[2] * L), cy, Math.max(cx, cx + c[2] * L) + (c[2] < 0 ? 2 : 0), cy + 2, RIM_HI);
            g.fill(cx, Math.min(cy, cy + c[3] * L), cx + 2, Math.max(cy, cy + c[3] * L) + (c[3] < 0 ? 2 : 0), RIM_HI);
        }
        // the header: the chip, the title (flickering while it boots), a glowing rule under it
        String chip = "[ SYSTEM ]";
        int cw = font.width(chip) + 8;
        g.fill(x0 + 8, y0 + 8, x0 + 8 + cw, y0 + 20, 0xC0102A58);
        frame(g, x0 + 8, y0 + 8, cw, 12, RIM);
        g.drawString(font, chip, x0 + 12, y0 + 10, CYAN, false);
        boolean flick = age < 14 && ((int) (age * 3)) % 3 == 0;
        int tw = font.width(title) * 2;
        g.pose().pushPose();
        g.pose().translate(x0 + w / 2f - tw / 2f + (flick ? 2 : 0), y0 + 10, 0);
        g.pose().scale(2, 2, 1);
        g.drawString(font, title, 0, 0, flick ? 0xFFFFC0E0 : 0xFFB8E4FF, true);   // a pink glitch as it settles
        g.pose().popPose();
        g.fillGradient(x0 + 10, y0 + 32, x0 + w / 2, y0 + 33, 0x003C8CFF, 0xC07FD0FF);
        g.fillGradient(x0 + w / 2, y0 + 32, x0 + w - 10, y0 + 33, 0xC07FD0FF, 0x003C8CFF);
    }

    /** While it boots, the part not yet unfolded is covered (drawn after the window's contents). */
    static void boot(GuiGraphics g, int x0, int y0, int w, int h, float age) {
        if (age >= 9) return;
        float k = Mth.clamp(age / 9f, 0, 1);
        k = 1 - (1 - k) * (1 - k);
        int mid = y0 + h / 2, half = (int) (h / 2f * k);
        g.fill(x0 - 2, y0 - 2, x0 + w + 2, mid - half, 0xFF020610);
        g.fill(x0 - 2, mid + half, x0 + w + 2, y0 + h + 2, 0xFF020610);
        g.fill(x0, mid - half - 1, x0 + w, mid - half + 1, CYAN);
        g.fill(x0, mid + half - 1, x0 + w, mid + half + 1, CYAN);
    }

    /** A System button: glass with bracketed ends, a glowing bar under it when pointed at. */
    static void button(GuiGraphics g, Font font, int x, int y, int w, int h, String label, boolean enabled, int accent, boolean hov) {
        g.fill(x, y, x + w, y + h, !enabled ? 0x50081020 : hov ? 0xD0143066 : 0xB00A1A38);
        int c = !enabled ? 0x401C3460 : hov ? lerp(accent, 0xFFFFFFFF, 0.4f) : accent;
        g.fill(x, y, x + 1, y + h, c); g.fill(x + w - 1, y, x + w, y + h, c);   // the brackets
        g.fill(x, y, x + 4, y + 1, c); g.fill(x, y + h - 1, x + 4, y + h, c);
        g.fill(x + w - 4, y, x + w, y + 1, c); g.fill(x + w - 4, y + h - 1, x + w, y + h, c);
        if (hov) { g.fill(x + 3, y + h, x + w - 3, y + h + 1, 0xC07FE8FF); g.fill(x + 6, y + h + 1, x + w - 6, y + h + 2, 0x507FE8FF); }
        String l = font.plainSubstrByWidth(label, w - 6);
        g.drawString(font, l, x + (w - font.width(l)) / 2, y + (h - 8) / 2, !enabled ? 0xFF3A4660 : hov ? 0xFFFFFFFF : 0xFFCDE6FF, false);
    }

    /** A segmented bar, like a game's HUD. */
    static void bar(GuiGraphics g, int x, int y, int w, int h, float f, int c0, int c1) {
        g.fill(x, y, x + w, y + h, 0xFF081020);
        int seg = 5, filled = (int) (w * Mth.clamp(f, 0, 1));
        for (int sx = 0; sx < w; sx += seg) {
            int ex = Math.min(w, sx + seg - 1);
            if (sx >= filled) { g.fill(x + sx, y, x + ex, y + h, 0xFF101C34); continue; }
            g.fillGradient(x + sx, y, x + Math.min(ex, filled), y + h, c0, c1);
        }
        if (filled > 0 && filled < w) g.fill(x + filled - 1, y - 1, x + filled, y + h + 1, 0xFFE6F6FF);   // the leading edge
    }

    static void frame(GuiGraphics g, int x, int y, int w, int h, int c) {
        g.fill(x, y, x + w, y + 1, c); g.fill(x, y + h - 1, x + w, y + h, c);
        g.fill(x, y, x + 1, y + h, c); g.fill(x + w - 1, y, x + w, y + h, c);
    }

    static int lerp(int a, int b, float f) {
        int r = 0;
        for (int s = 0; s < 32; s += 8) r |= ((int) Mth.lerp(f, (a >>> s) & 255, (b >>> s) & 255) & 255) << s;
        return r;
    }
}
