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
 * The spell wheel, redrawn as a grimoire when one of the addon's grimoires is equipped (any other spellbook keeps
 * Iron's own wheel). An original design in the spirit of the magic knights' grimoires:
 *   - a dark, worn-leather wheel split into pages, with gold filigree edges and dividing lines
 *   - an outer double gold ring with rune ticks slowly turning between the rings, and black-flame wisps
 *     flickering along it
 *   - a clover emblem in the centre (four leaves; five black leaves for Asta's grimoire) behind the selected
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
            // never leave the player without a wheel: fall back to Iron's next frame
            failed = true;
        }
    }

    private static boolean failed = false;

    private static int grimoireIndex() {
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

        float[] col = book >= 0 && book < LimitClient.COLORS.length ? LimitClient.COLORS[book] : new float[]{1.0F, 0.2F, 0.15F};
        int cx = g.guiWidth() / 2, cy = g.guiHeight() / 2;
        double rIn = 24.0 * ease, rOut = Mth.clamp(60.0 + count * 2.0, 66.0, 84.0) * ease;
        Matrix4f m = g.pose().last().pose();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        float A = open;
        // a dark vignette behind it all
        ring(bb, m, cx, cy, 0, rOut + 26 * ease, 0, TAU, 0.0F, 0.0F, 0.0F, 0.55F * A, 0.0F, 0.0F, 0.0F, 0.0F, 64);
        // pages
        for (int i = 0; i < count; i++) {
            double a0 = i * step - step * 0.5, a1 = a0 + step;
            float p = pop[i];
            double lift = 7.0 * p;
            double ri = rIn + 1.5 + lift * 0.4, ro = rOut + lift;
            // worn leather, darker toward the middle
            ring(bb, m, cx, cy, ri, ro, a0 + 0.012, a1 - 0.012,
                    0.06F + 0.05F * p * col[0], 0.045F + 0.03F * p * col[1], 0.05F + 0.03F * p * col[2], 0.88F * A,
                    0.13F + 0.75F * p * col[0], 0.09F + 0.55F * p * col[1], 0.08F + 0.5F * p * col[2], 0.9F * A, 12);
            if (p > 0.02F) {   // the chosen page's glowing rim
                ring(bb, m, cx, cy, ro - 2.0, ro + 1.5, a0 + 0.012, a1 - 0.012, col[0], col[1], col[2], 0.9F * p * A, 1.0F, 1.0F, 0.9F, 0.7F * p * A, 12);
                ring(bb, m, cx, cy, ro + 1.5, ro + 7.0, a0 + 0.012, a1 - 0.012, col[0], col[1], col[2], 0.35F * p * A, col[0], col[1], col[2], 0.0F, 12);
            }
            // gold divider between pages
            double ad = a0;
            line(bb, m, cx, cy, ad, rIn + 1.0, rOut + 1.0, 1.1, 0.85F, 0.66F, 0.3F, 0.85F * A);
        }
        // gold filigree: inner edge, outer double ring
        ring(bb, m, cx, cy, rIn - 0.5, rIn + 1.2, 0, TAU, 0.85F, 0.66F, 0.3F, 0.95F * A, 0.85F, 0.66F, 0.3F, 0.95F * A, 48);
        ring(bb, m, cx, cy, rOut + 2.0, rOut + 3.2, 0, TAU, 0.85F, 0.66F, 0.3F, 0.95F * A, 0.85F, 0.66F, 0.3F, 0.95F * A, 72);
        ring(bb, m, cx, cy, rOut + 10.0, rOut + 11.0, 0, TAU, 0.7F, 0.52F, 0.22F, 0.85F * A, 0.7F, 0.52F, 0.22F, 0.85F * A, 72);
        // rune ticks turning slowly between the rings
        int ticks = 48;
        for (int t = 0; t < ticks; t++) {
            double a = t * TAU / ticks + time * 0.15;
            double len = t % 4 == 0 ? 5.0 : 2.5;
            line(bb, m, cx, cy, a, rOut + 4.5, rOut + 4.5 + len, t % 4 == 0 ? 1.4 : 0.9, 0.9F, 0.72F, 0.35F, 0.8F * A);
        }
        // black-flame wisps flickering along the outer ring
        for (int f = 0; f < 18; f++) {
            double a = f * TAU / 18 + Math.sin(time * 0.7 + f) * 0.08;
            double h = (5.0 + 4.0 * Math.abs(Math.sin(time * 3.1 + f * 1.7))) * ease;
            double base = rOut + 11.0;
            double w2 = 0.07;
            tri(bb, m, cx, cy, a - w2, base, a + w2, base, a + Math.sin(time * 2 + f) * 0.05, base + h, 0.02F, 0.0F, 0.03F, 0.85F * A);
            tri(bb, m, cx, cy, a - w2 * 0.5, base, a + w2 * 0.5, base, a, base + h * 0.55, col[0] * 0.7F, col[1] * 0.7F, col[2] * 0.7F, 0.5F * A);
        }
        // the clover emblem in the centre
        int leaves = book == 0 ? 5 : 4;
        float[] leafDark = book == 0 ? new float[]{0.03F, 0.02F, 0.03F} : new float[]{0.05F, 0.16F, 0.08F};
        double rl = (rIn - 4.0) * 0.62;
        double spin = time * 0.25;
        for (int pass = 0; pass < 2; pass++) {   // gold outline first, the leaf over it
            double grow = pass == 0 ? 1.6 : 0.0;
            for (int l = 0; l < leaves; l++) {
                double a = spin + l * TAU / leaves;
                if (pass == 0) leaf(bb, m, cx, cy, a, rl, grow, 0.85F, 0.66F, 0.3F, 0.95F * A);
                else leaf(bb, m, cx, cy, a, rl, grow, leafDark[0], leafDark[1], leafDark[2], 0.97F * A);
            }
        }
        BufferUploader.drawWithShader(bb.end());

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
            int s = 14;
            g.blit(icon(chosen.getSpell()), cx - s / 2, cy - s / 2, 0, 0, s, s, s, s);
            infoPage(g, font, pl, sm, chosen, sel, cx, (int) (cy + rOut + 16 * ease), col, A);
        }
        RenderSystem.disableBlend();
    }

    /** The page under the wheel: name, level, mana, cooldown. */
    private static void infoPage(GuiGraphics g, Font font, LocalPlayer pl, SpellSelectionManager sm, SpellData sd, int sel, int cx, int top, float[] col, float A) {
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
        int w = Math.max(font.width(name), Math.max(font.width(mana), cool == null ? 0 : font.width(cool))) + 18;
        int h = cool == null ? 34 : 44;
        int x0 = cx - w / 2, x1 = cx + w / 2;
        int a = (int) (A * 255) & 0xFF;
        g.fill(x0, top, x1, top + h, (int) (A * 0xD0) << 24 | 0x120C10);
        int gold = a << 24 | 0xD9A94C;
        g.fill(x0, top, x1, top + 1, gold); g.fill(x0, top + h - 1, x1, top + h, gold);
        g.fill(x0, top, x0 + 1, top + h, gold); g.fill(x1 - 1, top, x1, top + h, gold);
        int accent = a << 24 | ((int) (col[0] * 255) << 16) | ((int) (col[1] * 255) << 8) | (int) (col[2] * 255);
        g.fill(x0 + 3, top + 12, x1 - 3, top + 13, accent);
        g.drawCenteredString(font, name, cx, top + 3, 0xF4E2B8 | a << 24);
        g.drawCenteredString(font, level, cx, top + 15, 0xBFB49A | a << 24);
        g.drawCenteredString(font, mana, cx, top + 25, 0xFFFFFF | a << 24);
        if (cool != null) g.drawCenteredString(font, cool, cx, top + 35, 0xFFFFFF | a << 24);
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

    /** One heart-shaped clover leaf pointing out at angle a: two lobes and a point at the centre. */
    private static void leaf(BufferBuilder bb, Matrix4f m, int cx, int cy, double a, double rl, double grow, float r, float g, float b, float al) {
        double lobe = rl * 0.42 + grow;
        double off = rl * 0.36;
        double dist = rl * 0.62;
        double fx = Math.sin(a), fy = -Math.cos(a), sx = Math.cos(a), sy = Math.sin(a);
        double lx = cx + fx * dist, ly = cy + fy * dist;
        disc(bb, m, lx + sx * off, ly + sy * off, lobe, r, g, b, al);
        disc(bb, m, lx - sx * off, ly - sy * off, lobe, r, g, b, al);
        double tipX = cx - fx * grow, tipY = cy - fy * grow;
        double w = off + lobe * 0.8;
        v(bb, m, (float) tipX, (float) tipY, r, g, b, al);
        v(bb, m, (float) (lx + sx * w), (float) (ly + sy * w), r, g, b, al);
        v(bb, m, (float) (lx - sx * w), (float) (ly - sy * w), r, g, b, al);
    }
}
