package com.ironspellgrimoire.client;

import com.mojang.blaze3d.systems.RenderSystem;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.player.ClientMagicData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Method;

/**
 * The grimoire's own mana bar, in place of Iron's while one of this addon's grimoires is equipped: a frame in the
 * grimoire's trim colour, the mana inside in the grimoire's colour with a sheen running
 * through it, and the numbers beside it. The bar eases toward the real value instead of jumping.
 * Asta's grimoire has no mana (anti magic): its bar is pitch black with a flickering blood-red edge.
 */
@Mod.EventBusSubscriber(modid = "iron_spell_grimoire", value = Dist.CLIENT)
public final class ManaBarHud {
    private ManaBarHud() {}

    /** {frame/trim, mana fill, fill highlight, dark inside, text} per grimoire (GrimoireMastery.BOOKS order). */
    private static final int[][] COL = {
            {0x8A7A6E, 0x120608, 0xFF2014, 0x050304, 0xFF6A5A},   // Asta (anti magic)
            {0xB4A9C8, 0x6A2BC8, 0xC89BFF, 0x0B0712, 0xD6B8FF},   // Yami
            {0xE7D9A0, 0x1FA86A, 0x9CFFD0, 0x07140F, 0xC8FFE0},   // Yuno
            {0xDCE6F2, 0x8794AA, 0xFFFFFF, 0x0C1018, 0xEEF4FF},   // Nozel
            {0xD9A94C, 0xE0601A, 0xFFD27A, 0x140805, 0xFFC27A},   // Magna
            {0xEDE7C8, 0x2F7FE0, 0xBFE8FF, 0x060C18, 0xBFE4FF},   // Luck
            {0xE8B652, 0xC4201A, 0xFF9A70, 0x160505, 0xFFB08A},   // Mereoleona
            {0xEDE3CC, 0xBBA98C, 0xFFF8E6, 0x100808, 0xF5EBD5},   // Zenon
            {0xD9A94C, 0x5E7896, 0xD8E6F6, 0x101216, 0xE6EEF8},   // Klaus
            {0xF2DDE8, 0xE0559A, 0xFFC4E2, 0x1C0814, 0xFFD0E8},   // Vanessa
            {0xE7C766, 0x6A48D8, 0xC8B4FF, 0x090620, 0xE6DAFF},   // Finral
            {0xDCEEFF, 0x1E78D8, 0x9CE0FF, 0x061224, 0xB8E4FF},   // Noelle
            {0xE8E8F2, 0x8A86B8, 0xFFD0EA, 0x0E0C18, 0xEEE6FF},   // Gauche
            {0xE0B0F0, 0x9A22B0, 0xFF7AF0, 0x14061A, 0xF2C0FF},   // Langris
            {0xF0CE7A, 0xE0501A, 0xFFB070, 0x140503, 0xFFB890},   // Fuegoleon
            {0xFFD36B, 0xE69010, 0xFFE08A, 0x180C03, 0xFFE0A0},   // Leopold
            {0xE8E2A0, 0x2E9A3A, 0xA0F090, 0x06140B, 0xCFFFC0},   // William
            {0xE8E8F0, 0xB01E34, 0xFF8090, 0x0E0609, 0xFFC0CC},   // Jack
            {0xD8B878, 0x8A6034, 0xD8B070, 0x140E07, 0xEBD0A0},   // Sol
            {0xE6D08A, 0x8A2BD8, 0xD89BFF, 0x100620, 0xE6C4FF},   // Vetto
            {0xFFF0F6, 0xE0709E, 0xFFC4DC, 0x1C0C16, 0xFFD6EA},   // Charmy
            {0xC8F2FF, 0x22B8E8, 0x8CE6FF, 0x04101C, 0xE0FAFF},   // Julius
            {0xD8B060, 0xB0101E, 0xFF5A66, 0x120204, 0xFFB0B8},   // Don Quixote (bloodfiend)
            {0xF2CC5A, 0xD9A020, 0xFFF0B0, 0x0A0A0C, 0xFFE8A8},   // Rien (the Caduceus)
    };

    private static float shown = -1F;
    private static Method getMana;

    /** Iron's own mana bar is hidden while ours is up. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onOverlay(RenderGuiOverlayEvent.Pre e) {
        var id = e.getOverlay().id();
        if (!"irons_spellbooks".equals(id.getNamespace()) || !id.getPath().contains("mana")) return;
        if (GrimoireWheel.grimoireIndex() >= 0) e.setCanceled(true);
    }

    private static float mana(Minecraft mc) {
        try {
            if (getMana == null) getMana = ClientMagicData.class.getMethod("getPlayerMana");
            return ((Number) getMana.invoke(null)).floatValue();
        } catch (Throwable t) {
            return 0F;
        }
    }

    @SubscribeEvent
    public static void onGui(RenderGuiEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.player.isSpectator()) return;
        int book = GrimoireWheel.grimoireIndex();
        if (book < 0) { shown = -1F; return; }
        book = Mth.clamp(book, 0, COL.length - 1);
        try { draw(e.getGuiGraphics(), mc, book, e.getPartialTick()); } catch (Throwable ignored) { }
    }

    private static void draw(GuiGraphics g, Minecraft mc, int book, float pt) {
        int[] c = COL[book];
        boolean anti = book == 0;
        float max = (float) Math.max(1.0, mc.player.getAttributeValue(AttributeRegistry.MAX_MANA.get()));
        float mana = Mth.clamp(mana(mc), 0F, max);
        float frac = mana / max;
        shown = shown < 0 ? frac : shown + (frac - shown) * 0.2F;   // eases toward the real value
        double time = mc.level.getGameTime() + pt;

        int W = g.guiWidth(), H = g.guiHeight();
        // Iron's layout: a long steel pill centred above the experience bar (its own offsets are honoured)
        int bw = 90, bh = 7, ox = 0, oy = 0;
        try {
            ox = io.redspace.ironsspellbooks.config.ClientConfigs.MANA_BAR_X_OFFSET.get();
            oy = io.redspace.ironsspellbooks.config.ClientConfigs.MANA_BAR_Y_OFFSET.get();
        } catch (Throwable ignored) { }
        int x = W / 2 - bw / 2 + ox, y = H - 39 + oy - (mc.player.isCreative() ? 0 : 0);
        int steel = c[0], dk = darker(c[0], 0.40F), lt = mix(c[0], 0xFFFFFF, 0.45F);
        // steel casing with rounded ends (corner pixels cut), a bright top edge and a dark inside - Iron's bar shape
        g.fill(x - 3, y - 2, x + bw + 3, y + bh + 2, 0xFF000000);
        g.fill(x - 2, y - 3, x + bw + 2, y + bh + 3, 0xFF000000);
        g.fill(x - 2, y - 2, x + bw + 2, y + bh + 2, 0xFF000000 | dk);
        g.fill(x - 1, y - 2, x + bw + 1, y - 1, 0xFF000000 | lt);                       // bright upper edge
        g.fill(x - 2, y - 1, x - 1, y + bh + 1, 0xFF000000 | steel);                    // left rim
        g.fill(x + bw + 1, y - 1, x + bw + 2, y + bh + 1, 0xFF000000 | steel);         // right rim
        g.fill(x - 1, y + bh + 1, x + bw + 1, y + bh + 2, 0xFF000000 | steel);          // lower rim
        g.fill(x - 1, y - 1, x + bw + 1, y + bh + 1, 0xFF000000 | steel);
        g.fill(x, y, x + bw, y + bh, 0xFF000000 | c[3]);                                // dark well
        // ten segments, like Iron's mana: gaps between them, each filled in the grimoire's colour
        int segs = 10, sw = bw / segs;
        float fillW = bw * Mth.clamp(shown, 0F, 1F);
        if (anti) {
            // anti magic: nothing to fill - the segments stay black and a blood-red edge flickers along the well
            for (int i = 0; i < segs; i++) g.fill(x + i * sw + 1, y + 1, x + (i + 1) * sw, y + bh - 1, 0xFF000000 | mix(c[3], 0x000000, 0.4F));
            for (int i = 0; i < bw; i += 3) {
                float f = (float) (0.5 + 0.5 * Math.sin(time * 0.35 + i * 0.7) * Math.sin(time * 0.13 + i * 0.31));
                int al = (int) (90 + 140 * f);
                g.fill(x + i, y + bh - 1, Math.min(x + bw, x + i + 3), y + bh, (al << 24) | c[2]);
                if (f > 0.75F) g.fill(x + i, y, Math.min(x + bw, x + i + 2), y + 1, ((int) (al * 0.6F) << 24) | c[2]);
            }
        } else {
            for (int i = 0; i < segs; i++) {
                int sx0 = x + i * sw, sx1 = sx0 + sw - 1;
                int full = (int) Math.min(sx1 - sx0, Math.max(0F, x + fillW - sx0));
                g.fill(sx0, y + 1, sx1, y + bh - 1, 0xFF000000 | mix(c[3], c[1], 0.12F));       // empty segment
                if (full > 0) {
                    g.fill(sx0, y + 1, sx0 + full, y + bh - 1, 0xFF000000 | c[1]);
                    g.fill(sx0, y + 1, sx0 + full, y + 2, 0xFF000000 | mix(c[1], c[2], 0.65F));   // lit top of the segment
                    g.fill(sx0, y + bh - 2, sx0 + full, y + bh - 1, 0xFF000000 | darker(c[1], 0.65F));
                }
            }
            // a sheen sliding through the mana
            int fw = Math.round(fillW);
            int sx = (int) ((time * 1.6) % (bw + 30)) - 15;
            for (int k = 0; k < 8; k++) {
                int px = x + sx + k;
                if (px < x || px >= x + fw || (px - x) % sw == sw - 1) continue;
                int al = (int) (110 * (1F - Math.abs(k - 4) / 4F));
                g.fill(px, y + 1, px + 1, y + bh - 1, (al << 24) | c[2]);
            }
        }
        // the numbers (or ANTI MAGIC) just above the right end
        Font f = mc.font;
        String s = anti ? "ANTI MAGIC" : (int) mana + " / " + (int) max;
        g.pose().pushPose();
        g.pose().translate(x + bw, y - 9, 0);
        g.pose().scale(0.5F, 0.5F, 1F);
        g.drawString(f, Component.literal(s), -f.width(s), 0, 0xFF000000 | c[4], true);
        g.pose().popPose();
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
    }

    private static int darker(int rgb, float k) {
        return ((int) ((rgb >> 16 & 255) * k) << 16) | ((int) ((rgb >> 8 & 255) * k) << 8) | (int) ((rgb & 255) * k);
    }

    private static int mix(int a, int b, float t) {
        int r = (int) Mth.lerp(t, a >> 16 & 255, b >> 16 & 255), gg = (int) Mth.lerp(t, a >> 8 & 255, b >> 8 & 255), bl = (int) Mth.lerp(t, a & 255, b & 255);
        return r << 16 | gg << 8 | bl;
    }
}
