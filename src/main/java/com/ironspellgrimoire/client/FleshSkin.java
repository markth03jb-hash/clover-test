package com.ironspellgrimoire.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

import java.util.Random;

/**
 * The parasite's look (the Evolution page, the Hive Network) and the Hunger Blade's (The Blade): a panel of living
 * tissue. A dark membrane that swells with a double heartbeat, veins creeping in from the edges and brightening on
 * each beat, a ragged rim lined with small teeth, drips hanging from the bottom, and an eye in the corner that
 * follows the mouse and blinks. Drawn with plain fills; no textures.
 */
final class FleshSkin {
    private FleshSkin() {}

    static final int BONE = 0xFFE8DCC4, BONE_SH = 0xFFA89880, GUM = 0xFF6A1018;

    /** The heartbeat: two quick swells, a rest (0..1). */
    static float beat(float t) {
        float ph = (t % 24f) / 24f;
        return Math.max(0, Mth.sin(ph * Mth.PI * 2 * 2)) * (ph < 0.5f ? 1f : 0f);
    }

    /** The membrane, its veins, the ragged toothed rim, the drips and the eye. */
    static void panel(GuiGraphics g, int x0, int y0, int w, int h, float t, int mx, int my, boolean eye) {
        float b = beat(t);
        // the membrane: cells of flesh, each its own shade, all swelling with the beat
        int cell = 8;
        for (int yy = y0; yy < y0 + h; yy += cell)
            for (int xx = x0; xx < x0 + w; xx += cell) {
                float n = 0.5f + 0.25f * Mth.sin(xx * 0.13f + yy * 0.07f) + 0.25f * Mth.sin(xx * 0.05f - yy * 0.17f + t * 0.02f);
                int r = (int) (26 + 22 * n + 14 * b), gg = (int) (5 + 4 * n), bl = (int) (9 + 6 * n);
                g.fill(xx, yy, Math.min(x0 + w, xx + cell), Math.min(y0 + h, yy + cell), 0xF0000000 | r << 16 | gg << 8 | bl);
            }
        // veins creeping in from the edges (the same every time: seeded by the panel's size)
        Random rnd = new Random(w * 31L + h);
        int vc = 0xFF000000 | (int) (110 + 110 * b) << 16 | 0x1018;
        for (int v = 0; v < 10; v++) {
            int side = v % 4;
            float x = side == 0 ? x0 + 2 : side == 1 ? x0 + w - 3 : x0 + rnd.nextInt(w);
            float y = side >= 2 ? (side == 2 ? y0 + 2 : y0 + h - 3) : y0 + rnd.nextInt(h);
            float a = side == 0 ? 0 : side == 1 ? Mth.PI : side == 2 ? Mth.PI / 2 : -Mth.PI / 2;
            int len = 18 + rnd.nextInt(40);
            for (int s = 0; s < len; s++) {
                a += (rnd.nextFloat() - 0.5f) * 0.7f;
                x += Mth.cos(a) * 1.5f; y += Mth.sin(a) * 1.5f;
                if (x < x0 + 1 || x > x0 + w - 2 || y < y0 + 1 || y > y0 + h - 2) break;
                int th = s < len / 3 ? 2 : 1;
                g.fill((int) x, (int) y, (int) x + th, (int) y + th, vc);
                if (rnd.nextInt(14) == 0) { float bx = x, by = y, ba = a + (rnd.nextBoolean() ? 1 : -1); for (int k = 0; k < 8; k++) { bx += Mth.cos(ba) * 1.5f; by += Mth.sin(ba) * 1.5f; g.fill((int) bx, (int) by, (int) bx + 1, (int) by + 1, vc); } }
            }
        }
        // the ragged rim: a dark lip of uneven thickness
        for (int xx = x0; xx < x0 + w; xx += 3) {
            int th = 2 + (int) (2 * (0.5f + 0.5f * Mth.sin(xx * 0.31f)));
            g.fill(xx, y0, xx + 3, y0 + th, 0xFF3A0810);
            g.fill(xx, y0 + h - th, xx + 3, y0 + h, 0xFF3A0810);
        }
        for (int yy = y0; yy < y0 + h; yy += 3) {
            int th = 2 + (int) (2 * (0.5f + 0.5f * Mth.sin(yy * 0.27f)));
            g.fill(x0, yy, x0 + th, yy + 3, 0xFF3A0810);
            g.fill(x0 + w - th, yy, x0 + w, yy + 3, 0xFF3A0810);
        }
        // small teeth along the top and bottom, outside the panel like a jaw round it (never over the text)
        teeth(g, x0 + 6, y0 - 1, w - 12, 9, 4, false);
        teeth(g, x0 + 10, y0 + h + 1, w - 20, 11, 3, true);
        // drips from below the bottom teeth
        for (int d = 0; d < 5; d++) {
            int dx = x0 + 20 + (int) ((w - 40) * ((d * 0.37f + 0.11f) % 1f));
            int dl = 3 + (int) (4 * (0.5f + 0.5f * Mth.sin(t * 0.05f + d * 1.7f)));
            g.fill(dx, y0 + h + 4, dx + 2, y0 + h + 4 + dl, 0xE0701018);
            g.fill(dx, y0 + h + 4 + dl, dx + 2, y0 + h + 6 + dl, 0xF0A01828);
        }
        if (eye) eye(g, x0 + w - 18, y0 - 2, t, mx, my, 0);
    }

    /** A row of teeth: bone triangles (stacked fills) on a gum. down: pointing down (along a top edge). */
    static void teeth(GuiGraphics g, int x, int y, int w, int spacing, int size, boolean down) {
        g.fill(x, down ? y - 1 : y - 1, x + w, down ? y + 1 : y + 1, GUM);
        for (int tx = x + 2, i = 0; tx < x + w - size * 2; tx += spacing, i++) {
            int s = size + (i % 3 == 1 ? 1 : 0);
            for (int k = 0; k < s; k++) {
                int half = s - k, yy = down ? y + 1 + k : y - 2 - k;
                g.fill(tx + (s - half), yy, tx + s + half, yy + 1, k == 0 ? BONE_SH : BONE);
            }
        }
    }

    /** An eye: the white, an iris of its colour that looks toward the mouse, a slit pupil; it blinks. mood: 0 calm,
     *  1 narrowed (hungry), 2 bloodshot (starved), 3 wide (fed). */
    static void eye(GuiGraphics g, int cx, int cy, float t, int mx, int my, int mood) {
        int rx = 9, ry = mood == 1 ? 3 : mood == 3 ? 6 : 5;
        float blink = (t % 110f) > 104f ? 1f - Math.abs(((t % 110f) - 107f) / 3f) : 0f;
        int open = Math.max(0, Math.round(ry * (1 - blink)));
        g.fill(cx - rx - 2, cy - ry - 2, cx + rx + 2, cy + ry + 2, 0xFF2A0608);   // the lids
        for (int k = -open; k <= open; k++) {
            int half = (int) Math.round(rx * Math.sqrt(1 - (k / (double) (ry + 0.5)) * (k / (double) (ry + 0.5))));
            g.fill(cx - half, cy + k, cx + half, cy + k + 1, mood == 2 ? 0xFFE8B0A8 : 0xFFF0E4D8);
        }
        if (open <= 0) { g.fill(cx - rx, cy, cx + rx, cy + 1, 0xFF120204); return; }
        if (mood == 2) for (int k = 0; k < 5; k++) g.fill(cx - rx + 1 + k * 4, cy - 1 + (k % 2), cx - rx + 3 + k * 4, cy + (k % 2), 0xFFC02020);   // bloodshot
        float lx = Mth.clamp((mx - cx) / 60f, -1, 1), ly = Mth.clamp((my - cy) / 60f, -1, 1);
        int ix = cx + Math.round(lx * (rx - 4)), iy = cy + Math.round(ly * Math.max(0, open - 2));
        int ir = Math.min(4, open);
        g.fill(ix - ir, iy - ir + 1, ix + ir, iy + ir - 1, 0xFFE02838);
        g.fill(ix - ir + 1, iy - ir, ix + ir - 1, iy + ir, 0xFFE02838);
        g.fill(ix - 1, iy - ir, ix + 1, iy + ir, 0xFF100204);   // a slit
        g.fill(ix + 1, iy - ir + 1, ix + 2, iy - ir + 2, 0xC0FFFFFF);   // a glint
    }

    /** A fleshy nodule (a tab or a button): a rounded swelling, lit along its top; the chosen one swells. */
    static void nodule(GuiGraphics g, int x, int y, int w, int h, boolean on, boolean hov, float t) {
        int s = on ? 1 : 0;
        x -= s; y -= s; w += 2 * s; h += 2 * s;
        int base = on ? 0xFF7A1424 : hov ? 0xFF5A1018 : 0xFF3A0A10;
        g.fill(x + 1, y, x + w - 1, y + h, base);
        g.fill(x, y + 1, x + w, y + h - 1, base);
        g.fill(x + 2, y + 1, x + w - 2, y + 2, on ? 0xFFC04050 : 0x80A03040);   // lit along the top
        g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, 0xFF200408);             // dark underneath
        if (on) { float b = beat(t); g.fill(x + 1, y + h - 1, x + w - 1, y + h, (int) (0x60 + 0x90 * b) << 24 | 0xFF3040); }
    }
}
