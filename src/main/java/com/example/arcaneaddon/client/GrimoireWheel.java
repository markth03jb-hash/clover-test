package com.example.arcaneaddon.client;

import com.example.arcaneaddon.server.GrimoireMastery;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import io.redspace.ironsspellbooks.api.magic.SpellSelectionManager;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.gui.overlays.SpellWheelOverlay;
import io.redspace.ironsspellbooks.player.ClientMagicData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import org.joml.Matrix4f;

import java.lang.reflect.Field;

/**
 * The spell wheel, redrawn as a grimoire - each grimoire with its own wheel (see THEMES) when one of the addon's grimoires is equipped (any other spellbook keeps
 * Iron's own wheel). An original design in the spirit of the magic knights' grimoires:
 *   - a dark, worn-leather wheel split into pages, with gold filigree edges and dividing lines
 *   - an outer double gold ring with rune ticks slowly turning between the rings, and black-flame wisps
 *     flickering along it
 *   - a clover emblem in the centre (three leaves; four for Yuno, five black leaves for Asta) behind the selected
 *     spell's icon
 *   - the page under the mouse lifts out and lights up in the grimoire's own colour; it glides there smoothly
 *   - the wheel opens with a quick, eased unfolding
 *   - a gold-bordered page under the wheel with the spell's name, level, mana cost and cooldown
 * Picking works exactly like Iron's wheel: hold the key, point, release. Only the drawing is replaced - the
 * selection is handed back to Iron's wheel (its own fields), so releasing the key selects as usual.
 */
@EventBusSubscriber(modid = "arcane_addon", value = {Dist.CLIENT})
public final class GrimoireWheel {
    private GrimoireWheel() {}

    private static final double TAU = Math.PI * 2;
    private static float[] pop = new float[0];
    private static float open = 0.0F;
    private static long lastNs = 0L;
    private static boolean wasActive = false;
    private static Field selField, smField;

    // ------------------------------------------------------------------ hooking Iron's wheel
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onOverlay(RenderGuiOverlayEvent.Pre e) {
        SpellWheelOverlay w = SpellWheelOverlay.instance;
        if (w == null) return;
        boolean isWheel = e.getOverlay().overlay() == w || e.getOverlay().id().getPath().contains("wheel");
        if (!isWheel) return;
        int book = grimoireIndex();
        if (book < 0) return;   // a normal spellbook: Iron's own wheel
        e.setCanceled(true);
        try {
            render(w, e.getGuiGraphics(), book);
        } catch (Throwable t) {
            // never leave the player without a wheel: fall back to Iron's from the next frame on
            failed = true;
            com.mojang.logging.LogUtils.getLogger().warn("[Iron Spell Grimoire] grimoire spell wheel failed, using Iron's wheel instead", t);
        }
    }

    private static boolean failed = false;
    private static double leafLobe = 0.3;

    static int grimoireIndex() {
        if (failed) return -1;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return -1;
        ItemStack s = Utils.getPlayerSpellbookStack(mc.player);
        if (s == null || s.isEmpty()) return -1;
        GrimoireMastery.Book b = GrimoireMastery.bookOf(s);
        if (b != null) return GrimoireMastery.BOOKS.indexOf(b);
        return "arcane_addon".equals(BuiltInRegistries.ITEM.getKey(s.getItem()).getNamespace()) ? 0 : -1;
    }

    private static void set(SpellWheelOverlay w, String name, Object v) throws Exception {
        Field f = name.equals("wheelSelection") ? selField : smField;
        if (f == null) {
            f = SpellWheelOverlay.class.getDeclaredField(name);
            f.setAccessible(true);
            if (name.equals("wheelSelection")) selField = f; else smField = f;
        }
        f.set(w, v);
    }

    // ------------------------------------------------------------------ the wheel
    private static void render(SpellWheelOverlay w, GuiGraphics g, int book) throws Exception {
        Minecraft mc = Minecraft.getInstance();
        long now = System.nanoTime();
        float dt = lastNs == 0L ? 0.016F : Mth.clamp((now - lastNs) / 1.0E9F, 0.0F, 0.1F);
        lastNs = now;
        if (!w.active) { wasActive = false; open = 0.0F; return; }
        LocalPlayer pl = mc.player;
        if (pl == null || mc.screen != null || mc.mouseHandler.isMouseGrabbed()) { w.close(); return; }
        SpellSelectionManager sm = ClientMagicData.getSpellSelectionManager();
        int count = sm.getSpellCount();
        if (count <= 0) { w.close(); return; }
        set(w, "swsm", sm);
        if (!wasActive) { wasActive = true; open = 0.0F; }
        if (pop.length != count) pop = new float[count];

        // which page is the mouse on (same feel as Iron's: a dead zone in the middle keeps the current spell)
        double rawCx = mc.getWindow().getScreenWidth() * 0.5, rawCy = mc.getWindow().getScreenHeight() * 0.5;
        double mdx = mc.mouseHandler.xpos() - rawCx, mdy = mc.mouseHandler.ypos() - rawCy;
        double step = TAU / count;
        int sel;
        if (mdx * mdx + mdy * mdy < 4225.0) sel = Math.max(0, Math.min(count - 1, sm.getSelectionIndex()));
        else {
            double a = Math.atan2(mdx, -mdy);
            if (a < 0) a += TAU;
            sel = Mth.clamp((int) (((a + step * 0.5) % TAU) / step), 0, count - 1);
        }
        set(w, "wheelSelection", sel);

        // smooth motion: the wheel unfolds, pages glide in and out
        open = Math.min(1.0F, open + dt * 6.0F);
        float k = 1.0F - (float) Math.exp(-dt * 16.0);
        for (int i = 0; i < count; i++) pop[i] += ((i == sel ? 1.0F : 0.0F) - pop[i]) * k;
        float ease = backOut(open);
        double time = now / 1.0E9;

        Theme th = THEMES[book >= 0 && book < THEMES.length ? book : 0];
        float[] col = th.accent;
        int cx = g.guiWidth() / 2, cy = g.guiHeight() / 2;
        double rIn = 24.0 * ease, rOut = Mth.clamp(60.0 + count * 2.0, 66.0, 84.0) * ease;
        Matrix4f m = g.pose().last().pose();
        float A = open;
        float[] tr = th.trim;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();   // shapes are wound both ways
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        // a vignette behind it all, tinted to the grimoire
        ring(bb, m, cx, cy, 0, rOut + 26 * ease, 0, TAU, th.pageIn[0] * 0.3F, th.pageIn[1] * 0.3F, th.pageIn[2] * 0.3F, 0.6F * A, 0.0F, 0.0F, 0.0F, 0.0F, 64);
        // pages
        for (int i = 0; i < count; i++) {
            double a0 = i * step - step * 0.5, a1 = a0 + step;
            float p = pop[i];
            double lift = 7.0 * p;
            double ri = rIn + 1.5 + lift * 0.4, ro = rOut + lift;
            float[] pi = th.pageIn, po = th.pageOut;
            ring(bb, m, cx, cy, ri, ro, a0 + 0.012, a1 - 0.012,
                    pi[0] + 0.08F * p * col[0], pi[1] + 0.08F * p * col[1], pi[2] + 0.08F * p * col[2], 0.9F * A,
                    po[0] + (col[0] * 0.8F - po[0]) * p, po[1] + (col[1] * 0.6F - po[1]) * p, po[2] + (col[2] * 0.6F - po[2]) * p, 0.92F * A, 12);
            if (th.grain) {   // a faint second band across the page: worn leather / metal plate / bone ridge
                double rm = ri + (ro - ri) * 0.62;
                ring(bb, m, cx, cy, rm, rm + 0.8, a0 + 0.05, a1 - 0.05, tr[0], tr[1], tr[2], 0.18F * A, tr[0], tr[1], tr[2], 0.18F * A, 8);
            }
            if (p > 0.02F) {   // the chosen page's glowing rim
                ring(bb, m, cx, cy, ro - 2.0, ro + 1.5, a0 + 0.012, a1 - 0.012, col[0], col[1], col[2], 0.9F * p * A, 1.0F, 1.0F, 0.92F, 0.7F * p * A, 12);
                ring(bb, m, cx, cy, ro + 1.5, ro + 7.0, a0 + 0.012, a1 - 0.012, col[0], col[1], col[2], 0.35F * p * A, col[0], col[1], col[2], 0.0F, 12);
            }
            line(bb, m, cx, cy, a0, rIn + 1.0, rOut + 1.0, 1.1, tr[0], tr[1], tr[2], 0.85F * A);
        }
        // the trim: inner edge and outer double ring
        ring(bb, m, cx, cy, rIn - 0.5, rIn + 1.2, 0, TAU, tr[0], tr[1], tr[2], 0.95F * A, tr[0], tr[1], tr[2], 0.95F * A, 48);
        ring(bb, m, cx, cy, rOut + 2.0, rOut + 3.2, 0, TAU, tr[0], tr[1], tr[2], 0.95F * A, tr[0], tr[1], tr[2], 0.95F * A, 72);
        ring(bb, m, cx, cy, rOut + 10.0, rOut + 11.0, 0, TAU, tr[0] * 0.82F, tr[1] * 0.82F, tr[2] * 0.82F, 0.85F * A, tr[0] * 0.82F, tr[1] * 0.82F, tr[2] * 0.82F, 0.85F * A, 72);
        marks(bb, m, cx, cy, th, rOut, time, A);
        ornaments(bb, m, cx, cy, th, rOut + 11.0, ease, time, A);
        // the clover in the centre (three leaves; Yuno's four; Asta's five black leaves)
        double rl = (rIn - 2.0) * 0.78;
        double spin = time * (book == 0 ? -0.25 : 0.25);
        leafLobe = th.leaves >= 5 ? 0.24 : 0.3;
        for (int pass = 0; pass < 2; pass++) {   // the trim colour as an outline first, the leaf over it
            for (int l = 0; l < th.leaves; l++) {
                double a = spin + l * TAU / th.leaves;
                if (pass == 0) leaf(bb, m, cx, cy, a, rl, 1.6, tr[0], tr[1], tr[2], 0.95F * A);
                else leaf(bb, m, cx, cy, a, rl, 0.0, th.leaf[0], th.leaf[1], th.leaf[2], 0.97F * A);
            }
        }
        BufferUploader.drawWithShader(bb.end());
        RenderSystem.enableCull();

        // spell icons on the pages
        Font font = mc.font;
        for (int i = 0; i < count; i++) {
            SpellData sd = sm.getSpellData(i);
            if (sd == null || sd.getSpell() == null) continue;
            AbstractSpell sp = sd.getSpell();
            double a = i * step;
            double rr = (rIn + rOut) * 0.5 + 4.0 * pop[i];
            int size = (int) Math.round(16 + 4 * pop[i]);
            int ix = (int) Math.round(cx + Math.sin(a) * rr) - size / 2, iy = (int) Math.round(cy - Math.cos(a) * rr) - size / 2;
            RenderSystem.enableBlend();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, A);
            g.blit(icon(sp), ix, iy, 0, 0, size, size, size, size);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            float cd = ClientMagicData.getCooldownPercent(sp);
            if (cd > 0.0F) g.fill(ix, iy + (int) (size * (1.0F - cd)), ix + size, iy + size, 0xAA000000);
        }
        // the chosen spell's icon in the clover's heart
        SpellData chosen = sm.getSpellData(sel);
        if (chosen != null && chosen.getSpell() != null) {
            int s = 10;
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, A);
            g.blit(icon(chosen.getSpell()), cx - s / 2, cy - s / 2, 0, 0, s, s, s, s);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            // text can't fade below ~4% alpha (Minecraft draws that fully opaque), so it waits for the wheel to open
            if (A > 0.15F) infoPage(g, font, pl, sm, chosen, sel, cx, (int) (cy + rOut + 16 * ease), th, A);
        }
        RenderSystem.disableBlend();
    }

    // ------------------------------------------------------------------ each grimoire's own wheel
    private static final int O_BLACK_FLAME = 0, O_SMOKE = 1, O_WIND = 2, O_DROPS = 3, O_FIRE = 4, O_BOLTS = 5, O_MANE = 6, O_BONES = 7, O_RIVETS = 8;
    private static final int M_RUNES = 0, M_DOTS = 1, M_CRESCENTS = 2, M_NOTCHES = 3;

    /** pageIn / pageOut = page colour at the centre and the rim; trim = filigree; leaf = clover; title = name colour. */
    private record Theme(float[] pageIn, float[] pageOut, float[] trim, float[] accent, float[] leaf, int leaves,
                         int ornament, int marks, boolean grain, int panel, int title) {}

    private static float[] c(int rgb) { return new float[]{(rgb >> 16 & 255) / 255F, (rgb >> 8 & 255) / 255F, (rgb & 255) / 255F}; }

    private static final Theme[] THEMES = {
            // Asta: pitch-black pages, blood-red rim, tarnished iron trim, jagged black flames, five black leaves
            new Theme(c(0x060406), c(0x1A0607), c(0x8A7A6E), c(0xFF2014), c(0x080608), 5, O_BLACK_FLAME, M_RUNES, true, 0x0A0607, 0xFF6A5A),
            // Yami: violet-black pages, silver trim, drifting darkness and crescent marks
            new Theme(c(0x07040C), c(0x1C0E2C), c(0xB4A9C8), c(0x9A40FF), c(0x120A1C), 3, O_SMOKE, M_CRESCENTS, false, 0x0B0712, 0xD6B8FF),
            // Yuno: deep jade pages, pale gold trim, wind arcs racing round, four emerald leaves
            new Theme(c(0x05130E), c(0x0F3A2A), c(0xE7D9A0), c(0x8CFFC0), c(0x167A4A), 4, O_WIND, M_DOTS, false, 0x07140F, 0xC8FFE0),
            // Nozel: steel-blue pages, chrome trim, mercury drops hanging off the ring
            new Theme(c(0x0B0F16), c(0x2A3442), c(0xDCE6F2), c(0xE6EEFF), c(0x5C6A80), 3, O_DROPS, M_NOTCHES, true, 0x0C1018, 0xEEF4FF),
            // Magna: scorched brown pages, brass trim, roaring flames
            new Theme(c(0x120604), c(0x3A1206), c(0xD9A94C), c(0xFF8A26), c(0x5A1C06), 3, O_FIRE, M_DOTS, true, 0x140805, 0xFFC27A),
            // Luck: midnight-blue pages, white-gold trim, crackling bolts
            new Theme(c(0x040A16), c(0x0E2244), c(0xEDE7C8), c(0x73BFFF), c(0x123A6A), 3, O_BOLTS, M_RUNES, false, 0x060C18, 0xBFE4FF),
            // Mereoleona: crimson pages, gold trim, a lion's mane of flame claws
            new Theme(c(0x150304), c(0x4A0A08), c(0xE8B652), c(0xFF3A1E), c(0x7A120C), 3, O_MANE, M_NOTCHES, true, 0x160505, 0xFFB08A),
            // Zenon: bone-white trim on dark red-black pages, ribs of bone round the ring
            new Theme(c(0x0E0606), c(0x2C1414), c(0xEDE3CC), c(0xF2E6CC), c(0x3A2622), 3, O_BONES, M_NOTCHES, true, 0x100808, 0xF5EBD5),
            // Klaus: grey steel plates with gold trim, a riveted band
            new Theme(c(0x0E1014), c(0x353C46), c(0xD9A94C), c(0xC7D6EA), c(0x4A5462), 3, O_RIVETS, M_NOTCHES, true, 0x101216, 0xE6EEF8),
    };

    /** {page inside, page edge, trim, accent, panel, title} as RGB ints, for the grimoire book screen and others. */
    static int[] themeColors(int book) {
        Theme t = THEMES[Mth.clamp(book, 0, THEMES.length - 1)];
        return new int[]{rgb(t.pageIn), rgb(t.pageOut), rgb(t.trim), rgb(t.accent), t.panel, t.title};
    }

    private static int rgb(float[] c) { return ((int) (c[0] * 255) << 16) | ((int) (c[1] * 255) << 8) | (int) (c[2] * 255); }

    /** The marks turning between the two outer rings. */
    private static void marks(BufferBuilder bb, Matrix4f m, int cx, int cy, Theme th, double rOut, double time, float A) {
        float[] t = th.trim;
        int n = 48;
        for (int i = 0; i < n; i++) {
            double a = i * TAU / n + time * 0.15;
            boolean big = i % 4 == 0;
            switch (th.marks) {
                case M_DOTS -> { if (big || i % 2 == 0) ring(bb, m, cx, cy, rOut + 5.5, rOut + (big ? 8.0 : 7.0), a - 0.02, a + 0.02, t[0], t[1], t[2], 0.85F * A, t[0], t[1], t[2], 0.85F * A, 2); }
                case M_CRESCENTS -> { if (big) ring(bb, m, cx, cy, rOut + 5.0, rOut + 7.8, a - 0.09, a + 0.09, t[0], t[1], t[2], 0.0F, t[0], t[1], t[2], 0.9F * A, 4); }
                case M_NOTCHES -> line(bb, m, cx, cy, a, rOut + 3.2, rOut + (big ? 7.0 : 5.0), big ? 1.6 : 0.8, t[0], t[1], t[2], 0.75F * A);
                default -> line(bb, m, cx, cy, a, rOut + 4.5, rOut + 4.5 + (big ? 5.0 : 2.5), big ? 1.4 : 0.9, t[0], t[1], t[2], 0.8F * A);
            }
        }
    }

    /** The grimoire's own ornament round the outside of the wheel, always gently moving. */
    private static void ornaments(BufferBuilder bb, Matrix4f m, int cx, int cy, Theme th, double base, float ease, double time, float A) {
        float[] col = th.accent, t = th.trim;
        switch (th.ornament) {
            case O_SMOKE -> {   // darkness drifting round the ring, swelling and thinning
                for (int i = 0; i < 16; i++) {
                    double a = i * TAU / 16 + time * 0.12 + Math.sin(time * 0.8 + i) * 0.06;
                    double r = (3.0 + 2.5 * (0.5 + 0.5 * Math.sin(time * 1.7 + i * 2.1))) * ease;
                    double d = base + 2.0 + r * 0.6;
                    disc(bb, m, cx + Math.sin(a) * d, cy - Math.cos(a) * d, r, 0.02F, 0.0F, 0.04F, 0.8F * A);
                    disc(bb, m, cx + Math.sin(a) * d, cy - Math.cos(a) * d, r * 0.45, col[0] * 0.5F, col[1] * 0.5F, col[2] * 0.5F, 0.35F * A);
                }
            }
            case O_WIND -> {   // wind arcs racing round in two directions
                for (int i = 0; i < 6; i++) {
                    double a0 = i * TAU / 6 + time * 1.1;
                    ring(bb, m, cx, cy, base + 2.0, base + 3.4, a0, a0 + 0.7, col[0], col[1], col[2], 0.0F, 1.0F, 1.0F, 1.0F, 0.85F * A, 10);
                    double b0 = i * TAU / 6 - time * 0.7 + 0.5;
                    ring(bb, m, cx, cy, base + 6.0, base + 7.0, b0, b0 + 0.45, col[0], col[1], col[2], 0.0F, col[0], col[1], col[2], 0.7F * A, 8);
                }
            }
            case O_DROPS -> {   // mercury drops hanging off the ring, swaying
                for (int i = 0; i < 12; i++) {
                    double a = i * TAU / 12 + Math.sin(time * 1.2 + i) * 0.03;
                    double len = (6.0 + 1.5 * Math.sin(time * 2.0 + i * 1.3)) * ease;
                    double tipR = base + len;
                    double x = cx + Math.sin(a) * (tipR - 2.2), y = cy - Math.cos(a) * (tipR - 2.2);
                    tri(bb, m, cx, cy, a - 0.04, base, a + 0.04, base, a, tipR - 2.0, 0.62F, 0.67F, 0.75F, 0.95F * A);
                    disc(bb, m, x, y, 2.4, 0.62F, 0.67F, 0.75F, 0.95F * A);
                    disc(bb, m, x - 0.7, y - 0.7, 0.8, 1.0F, 1.0F, 1.0F, 0.9F * A);
                }
            }
            case O_FIRE -> {   // roaring flames, orange with yellow hearts
                for (int f = 0; f < 22; f++) {
                    double a = f * TAU / 22 + Math.sin(time * 0.9 + f) * 0.05;
                    double h = (6.0 + 5.0 * Math.abs(Math.sin(time * 3.7 + f * 1.3))) * ease;
                    tri(bb, m, cx, cy, a - 0.09, base, a + 0.09, base, a + Math.sin(time * 2.6 + f) * 0.06, base + h, 0.9F, 0.25F, 0.04F, 0.9F * A);
                    tri(bb, m, cx, cy, a - 0.045, base, a + 0.045, base, a, base + h * 0.55, 1.0F, 0.85F, 0.3F, 0.85F * A);
                }
            }
            case O_BOLTS -> {   // lightning crackling off the ring, re-forking several times a second
                java.util.Random rnd = new java.util.Random((long) (time * 9.0));
                for (int b = 0; b < 8; b++) {
                    double a = b * TAU / 8 + rnd.nextDouble() * 0.3, r0 = base;
                    for (int s = 0; s < 3; s++) {
                        double a1 = a + (rnd.nextDouble() - 0.5) * 0.12, r1 = r0 + (3.0 + rnd.nextDouble() * 2.5) * ease;
                        seg(bb, m, cx, cy, a, r0, a1, r1, 2.2, col[0], col[1], col[2], 0.45F * A);
                        seg(bb, m, cx, cy, a, r0, a1, r1, 0.9, 0.92F, 0.97F, 1.0F, 0.95F * A);
                        a = a1; r0 = r1;
                    }
                }
            }
            case O_MANE -> {   // a lion's mane: curved flame claws sweeping round, swaying
                for (int i = 0; i < 20; i++) {
                    double a = i * TAU / 20 + Math.sin(time * 1.1 + i * 0.7) * 0.04;
                    double h = (8.0 + 3.0 * Math.sin(time * 2.3 + i)) * ease;
                    tri(bb, m, cx, cy, a - 0.1, base, a + 0.08, base, a + 0.16, base + h, 0.85F, 0.08F, 0.05F, 0.92F * A);
                    tri(bb, m, cx, cy, a - 0.05, base, a + 0.04, base, a + 0.1, base + h * 0.6, 1.0F, 0.62F, 0.25F, 0.85F * A);
                }
            }
            case O_BONES -> {   // ribs of bone standing round the ring, each with a knuckle at the tip
                for (int i = 0; i < 16; i++) {
                    double a = i * TAU / 16 + time * 0.05;
                    double len = (6.0 + (i % 2) * 2.0) * ease;
                    line(bb, m, cx, cy, a, base, base + len, 1.6, t[0], t[1], t[2], 0.95F * A);
                    double x = cx + Math.sin(a) * (base + len), y = cy - Math.cos(a) * (base + len);
                    double sx = Math.cos(a) * 1.1, sy = Math.sin(a) * 1.1;
                    disc(bb, m, x + sx, y + sy, 1.3, t[0], t[1], t[2], 0.95F * A);
                    disc(bb, m, x - sx, y - sy, 1.3, t[0], t[1], t[2], 0.95F * A);
                }
            }
            case O_RIVETS -> {   // a riveted steel band with seams
                ring(bb, m, cx, cy, base + 1.0, base + 5.0, 0, TAU, 0.3F, 0.34F, 0.4F, 0.95F * A, 0.48F, 0.53F, 0.6F, 0.95F * A, 72);
                for (int i = 0; i < 24; i++) {
                    double a = i * TAU / 24;
                    double x = cx + Math.sin(a) * (base + 3.0), y = cy - Math.cos(a) * (base + 3.0);
                    disc(bb, m, x, y, 1.1, 0.82F, 0.86F, 0.92F, 0.95F * A);
                    if (i % 3 == 0) line(bb, m, cx, cy, a + TAU / 48, base + 1.0, base + 5.0, 0.7, 0.12F, 0.13F, 0.16F, 0.9F * A);
                }
            }
            default -> {   // Asta: jagged black flames with a red heart
                for (int f = 0; f < 18; f++) {
                    double a = f * TAU / 18 + Math.sin(time * 0.7 + f) * 0.08;
                    double h = (5.0 + 4.0 * Math.abs(Math.sin(time * 3.1 + f * 1.7))) * ease;
                    double sway = Math.sin(time * 2 + f) * 0.05;
                    tri(bb, m, cx, cy, a - 0.08, base, a + 0.02, base, a - 0.03 + sway, base + h, 0.02F, 0.0F, 0.02F, 0.9F * A);
                    tri(bb, m, cx, cy, a - 0.01, base, a + 0.08, base, a + 0.05 + sway, base + h * 0.75, 0.02F, 0.0F, 0.02F, 0.9F * A);
                    tri(bb, m, cx, cy, a - 0.035, base, a + 0.035, base, a + sway * 0.5, base + h * 0.5, col[0] * 0.8F, col[1] * 0.8F, col[2] * 0.8F, 0.55F * A);
                }
            }
        }
    }

    /** A straight segment between two polar points, w wide. */
    private static void seg(BufferBuilder bb, Matrix4f m, int cx, int cy, double a0, double r0, double a1, double r1, double w, float r, float g, float b, float al) {
        float x0 = px(cx, a0, r0), y0 = py(cy, a0, r0), x1 = px(cx, a1, r1), y1 = py(cy, a1, r1);
        double dx = x1 - x0, dy = y1 - y0, len = Math.max(1.0E-4, Math.sqrt(dx * dx + dy * dy));
        float nx = (float) (-dy / len * w * 0.5), ny = (float) (dx / len * w * 0.5);
        v(bb, m, x0 - nx, y0 - ny, r, g, b, al); v(bb, m, x1 + nx, y1 + ny, r, g, b, al); v(bb, m, x1 - nx, y1 - ny, r, g, b, al);
        v(bb, m, x0 - nx, y0 - ny, r, g, b, al); v(bb, m, x0 + nx, y0 + ny, r, g, b, al); v(bb, m, x1 + nx, y1 + ny, r, g, b, al);
    }

    /** The page under the wheel: name, level, mana, cooldown - in the grimoire's colours. */
    private static void infoPage(GuiGraphics g, Font font, LocalPlayer pl, SpellSelectionManager sm, SpellData sd, int sel, int cx, int top, Theme th, float A) {
        AbstractSpell sp = sd.getSpell();
        int lvl = sp.getLevelFor(sd.getLevel(), pl);
        Component name = sp.getDisplayName(pl);
        Component level = Component.translatable("ui.irons_spellbooks.level", lvl);
        Component mana = Component.translatable("ui.irons_spellbooks.mana_cost", sp.getManaCost(lvl)).withStyle(net.minecraft.ChatFormatting.AQUA);
        Component cool = null;
        try {
            int cdTicks = io.redspace.ironsspellbooks.capabilities.magic.MagicManager.getEffectiveSpellCooldown(sp, pl, sm.getSpellSlot(sel).getCastSource());
            cool = Component.translatable("tooltip.irons_spellbooks.cooldown_length_seconds", Utils.timeFromTicks(cdTicks, 1)).withStyle(net.minecraft.ChatFormatting.YELLOW);
        } catch (Throwable ignored) {}
        java.util.List<Component> info = new java.util.ArrayList<>();   // damage etc., as the spell itself reports it
        try {
            for (Component c : sp.getUniqueInfo(lvl, pl)) { if (info.size() < 2) info.add(c); }
            if (info.isEmpty()) info.add(Component.translatable("ui.irons_spellbooks.damage", String.format("%.1f", sp.getSpellPower(lvl, pl))));
        } catch (Throwable ignored) {}
        int w = Math.max(font.width(name), Math.max(font.width(mana), cool == null ? 0 : font.width(cool)));
        for (Component c : info) w = Math.max(w, font.width(c));
        w += 18;
        int h = (cool == null ? 34 : 44) + info.size() * 10;
        int x0 = cx - w / 2, x1 = cx + w / 2;
        int a = Mth.clamp((int) (A * 255), 8, 255);
        g.fill(x0, top, x1, top + h, (int) (A * 0xD8) << 24 | th.panel);
        float[] t = th.trim, col = th.accent;
        int trim = a << 24 | ((int) (t[0] * 255) << 16) | ((int) (t[1] * 255) << 8) | (int) (t[2] * 255);
        g.fill(x0, top, x1, top + 1, trim); g.fill(x0, top + h - 1, x1, top + h, trim);
        g.fill(x0, top, x0 + 1, top + h, trim); g.fill(x1 - 1, top, x1, top + h, trim);
        g.fill(x0 + 2, top + 2, x0 + 4, top + 4, trim); g.fill(x1 - 4, top + 2, x1 - 2, top + 4, trim);   // corner studs
        int accent = a << 24 | ((int) (col[0] * 255) << 16) | ((int) (col[1] * 255) << 8) | (int) (col[2] * 255);
        g.fill(x0 + 3, top + 12, x1 - 3, top + 13, accent);
        g.drawCenteredString(font, name, cx, top + 3, th.title | a << 24);
        g.drawCenteredString(font, level, cx, top + 15, 0xBFB49A | a << 24);
        g.drawCenteredString(font, mana, cx, top + 25, 0xFFFFFF | a << 24);
        int ly = top + 35;
        if (cool != null) { g.drawCenteredString(font, cool, cx, ly, 0xFFFFFF | a << 24); ly += 10; }
        for (Component c : info) { g.drawCenteredString(font, c, cx, ly, 0xFF8A70 | a << 24); ly += 10; }
    }

    private static ResourceLocation icon(AbstractSpell sp) {
        ResourceLocation id = sp.getSpellResource();
        return new ResourceLocation(id.getNamespace(), "textures/gui/spell_icons/" + id.getPath() + ".png");
    }

    private static float backOut(float x) {
        float c1 = 1.4F, c3 = c1 + 1.0F;
        float t = x - 1.0F;
        return 1.0F + c3 * t * t * t + c1 * t * t;
    }

    // ------------------------------------------------------------------ shapes (GUI space; angle 0 = up, clockwise)
    private static float px(int cx, double a, double r) { return (float) (cx + Math.sin(a) * r); }
    private static float py(int cy, double a, double r) { return (float) (cy - Math.cos(a) * r); }

    private static void v(BufferBuilder bb, Matrix4f m, float x, float y, float r, float g, float b, float a) {
        bb.vertex(m, x, y, 0.0F).color(r, g, b, a).endVertex();
    }

    /** An annular sector from radius r0 to r1 and angle a0 to a1, coloured inner -> outer. */
    private static void ring(BufferBuilder bb, Matrix4f m, int cx, int cy, double r0, double r1, double a0, double a1,
                             float ir, float ig, float ib, float ia, float or, float og, float ob, float oa, int seg) {
        for (int s = 0; s < seg; s++) {
            double b0 = a0 + (a1 - a0) * s / seg, b1 = a0 + (a1 - a0) * (s + 1) / seg;
            float x00 = px(cx, b0, r0), y00 = py(cy, b0, r0), x01 = px(cx, b0, r1), y01 = py(cy, b0, r1);
            float x10 = px(cx, b1, r0), y10 = py(cy, b1, r0), x11 = px(cx, b1, r1), y11 = py(cy, b1, r1);
            v(bb, m, x00, y00, ir, ig, ib, ia); v(bb, m, x11, y11, or, og, ob, oa); v(bb, m, x01, y01, or, og, ob, oa);
            v(bb, m, x00, y00, ir, ig, ib, ia); v(bb, m, x10, y10, ir, ig, ib, ia); v(bb, m, x11, y11, or, og, ob, oa);
        }
    }

    /** A radial line at angle a from r0 to r1, w wide. */
    private static void line(BufferBuilder bb, Matrix4f m, int cx, int cy, double a, double r0, double r1, double w, float r, float g, float b, float al) {
        double sx = Math.cos(a) * w * 0.5, sy = Math.sin(a) * w * 0.5;
        float x0 = px(cx, a, r0), y0 = py(cy, a, r0), x1 = px(cx, a, r1), y1 = py(cy, a, r1);
        v(bb, m, (float) (x0 - sx), (float) (y0 - sy), r, g, b, al); v(bb, m, (float) (x1 + sx), (float) (y1 + sy), r, g, b, al); v(bb, m, (float) (x1 - sx), (float) (y1 - sy), r, g, b, al);
        v(bb, m, (float) (x0 - sx), (float) (y0 - sy), r, g, b, al); v(bb, m, (float) (x0 + sx), (float) (y0 + sy), r, g, b, al); v(bb, m, (float) (x1 + sx), (float) (y1 + sy), r, g, b, al);
    }

    /** A triangle given in polar points. */
    private static void tri(BufferBuilder bb, Matrix4f m, int cx, int cy, double a0, double r0, double a1, double r1, double a2, double r2, float r, float g, float b, float al) {
        v(bb, m, px(cx, a0, r0), py(cy, a0, r0), r, g, b, al);
        v(bb, m, px(cx, a2, r2), py(cy, a2, r2), r, g, b, al);
        v(bb, m, px(cx, a1, r1), py(cy, a1, r1), r, g, b, al);
    }

    /** A filled disc at (x, y). */
    private static void disc(BufferBuilder bb, Matrix4f m, double x, double y, double rad, float r, float g, float b, float al) {
        int seg = 18;
        for (int s = 0; s < seg; s++) {
            double a0 = s * TAU / seg, a1 = (s + 1) * TAU / seg;
            v(bb, m, (float) x, (float) y, r, g, b, al);
            v(bb, m, (float) (x + Math.sin(a1) * rad), (float) (y - Math.cos(a1) * rad), r, g, b, al);
            v(bb, m, (float) (x + Math.sin(a0) * rad), (float) (y - Math.cos(a0) * rad), r, g, b, al);
        }
    }

    // Asta's five-leaf clover: five broad heart-shaped leaves, each the union of two round lobes seen from the
    // centre, with a shallow notch at the top, deep rounded V's between neighbours and a thin straight slit between
    // them down to the middle.
    private static final double H_D = 0.545, H_OFF = 0.105, H_RAD = 0.248, H_GAP = 0.03;

    /** Distance from the centre to the leaf's edge along local angle phi (0 = the leaf's axis), 0 if past its side. */
    private static double heartEdge(double phi) {
        double dx = Math.cos(phi), dy = Math.sin(phi), best = 0.0;
        for (int s = -1; s <= 1; s += 2) {
            double cxl = H_D, cyl = H_OFF * s;
            double bq = dx * cxl + dy * cyl, cq = cxl * cxl + cyl * cyl - H_RAD * H_RAD, disc = bq * bq - cq;
            if (disc >= 0) best = Math.max(best, bq + Math.sqrt(disc));
        }
        return best;
    }

    /** One heart leaf of the five-leaf clover pointing out at angle a (fan of triangles from just off the centre). */
    private static void heartLeaf(BufferBuilder bb, Matrix4f m, int cx, int cy, double a, double rl, double grow, float r, float g, float b, float al) {
        double R = rl * 1.23;   // the leaf's tip reaches about as far as the old leaves did
        double fx = Math.sin(a), fy = -Math.cos(a), sx = Math.cos(a), sy = Math.sin(a);
        double half = Math.PI / 5.0;
        double tipOff = (H_GAP * R * 0.5 - grow) / Math.sin(half);   // where the two slits meet this leaf
        double tx = cx + fx * tipOff, ty = cy + fy * tipOff;
        int steps = 28;
        double px = 0, py = 0;
        boolean have = false;
        for (int i = 0; i <= steps; i++) {
            double phi = -half + 2.0 * half * i / steps;
            double e = heartEdge(phi);
            if (e <= 0.0) continue;
            double rr = e * R + grow;
            double ux = Math.cos(phi), uy = Math.sin(phi);
            double qx = cx + (fx * ux + sx * uy) * rr, qy = cy + (fy * ux + sy * uy) * rr;
            if (have) {
                v(bb, m, (float) tx, (float) ty, r, g, b, al);
                v(bb, m, (float) px, (float) py, r, g, b, al);
                v(bb, m, (float) qx, (float) qy, r, g, b, al);
            }
            px = qx; py = qy; have = true;
        }
    }

    /** One heart-shaped clover leaf pointing out at angle a: two lobes and a point at the centre. */
    private static void leaf(BufferBuilder bb, Matrix4f m, int cx, int cy, double a, double rl, double grow, float r, float g, float b, float al) {
        if (leafLobe < 0.25) { heartLeaf(bb, m, cx, cy, a, rl, grow, r, g, b, al); return; }   // Asta's five
        double lobe = rl * leafLobe + grow;   // separate heart-shaped leaves, not one blob
        double off = rl * 0.27;
        double dist = rl * 0.74;
        double fx = Math.sin(a), fy = -Math.cos(a), sx = Math.cos(a), sy = Math.sin(a);
        double lx = cx + fx * dist, ly = cy + fy * dist;
        disc(bb, m, lx + sx * off, ly + sy * off, lobe, r, g, b, al);
        disc(bb, m, lx - sx * off, ly - sy * off, lobe, r, g, b, al);
        double tipX = cx - fx * grow, tipY = cy - fy * grow;
        double w = off + lobe * 0.75;
        v(bb, m, (float) tipX, (float) tipY, r, g, b, al);
        v(bb, m, (float) (lx + sx * w), (float) (ly + sy * w), r, g, b, al);
        v(bb, m, (float) (lx - sx * w), (float) (ly - sy * w), r, g, b, al);
    }
}
