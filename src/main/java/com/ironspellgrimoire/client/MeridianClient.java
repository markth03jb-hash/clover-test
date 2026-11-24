package com.ironspellgrimoire.client;

import com.ironspellgrimoire.network.MeridianPackets;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The Meridian Grimoire on the client (server/MeridianFx): the sundial HUD beside the hotbar - a half-dial whose shadow
 * follows the sun, the Sun Meter under it, Pride as pips, Zenith's countdown - and every Meridian mage drawn larger as
 * the sun climbs (up to 1.3x at noon, 1.35x in Zenith; the model only - the hitbox doesn't change).
 */
@Mod.EventBusSubscriber(modid = "iron_spell_grimoire", value = Dist.CLIENT)
public final class MeridianClient {
    private MeridianClient() {}

    static final Map<Integer, MeridianPackets.Sync> ALL = new HashMap<>();
    static final Map<Integer, Float> SHOWN = new HashMap<>();

    public static void sync(MeridianPackets.Sync m) { if (m.on) ALL.put(m.id, m); else { ALL.remove(m.id); SHOWN.remove(m.id); } }

    /** How large a mage is drawn: eased toward the sun's size so it never jumps. */
    static float scale(int id) {
        MeridianPackets.Sync m = ALL.get(id);
        float want = m == null ? 1f : m.zenith > 0 ? 1.35f : 1f + 0.3f * Math.min(1f, m.meter);
        float cur = SHOWN.getOrDefault(id, 1f);
        cur += (want - cur) * 0.05f;
        if (Math.abs(cur - 1f) < 0.002f && m == null) { SHOWN.remove(id); return 1f; }
        SHOWN.put(id, cur);
        return cur;
    }

    private static final java.util.Set<Integer> PUSHED = new java.util.HashSet<>();

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void grow(RenderPlayerEvent.Pre e) {
        int id = e.getEntity().getId();
        if (!ALL.containsKey(id) && !SHOWN.containsKey(id)) return;
        float k = scale(id);
        e.getPoseStack().pushPose();
        e.getPoseStack().scale(k, k, k);
        PUSHED.add(id);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void grown(RenderPlayerEvent.Post e) {
        if (PUSHED.remove(e.getEntity().getId())) e.getPoseStack().popPose();
    }

    @SubscribeEvent
    public static void onGui(RenderGuiEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        MeridianPackets.Sync m = ALL.get(mc.player.getId());
        if (m == null) return;
        GuiGraphics g = e.getGuiGraphics();
        int x = g.guiWidth() / 2 - 91 - 29 - 52 - 6, y = g.guiHeight() - 32;
        draw(g, mc, m, x, y, mc.level.getDayTime() + e.getPartialTick());
    }

    /** The sundial, 52 x 30 at (x, y). */
    public static void draw(GuiGraphics g, Minecraft mc, MeridianPackets.Sync m, int x, int y, float dayTime) {
        boolean day = m.sun > 0.02f || m.zenith > 0;
        int cx = x + 26, cy = y + 24, R = 22;
        int face = day ? 0xDC281A0E : 0xDC0E0E1A, rim = m.zenith > 0 ? (m.borrowed ? 0xFFFF8A40 : 0xFFFFF4C8) : 0xFFFFDE8C;
        g.drawManaged(() -> {
            // the half-dial, filled row by row
            for (int dy = 0; dy <= R; dy++) {
                int half = (int) Math.sqrt(R * R - dy * dy);
                g.fill(cx - half, cy - dy, cx + half, cy - dy + 1, face);
                int edge = (int) Math.sqrt(Math.max(0, (R - 1) * (R - 1) - dy * dy));
                g.fill(cx - half, cy - dy, cx - edge, cy - dy + 1, rim);
                g.fill(cx + edge, cy - dy, cx + half, cy - dy + 1, rim);
            }
            g.fill(cx - R, cy, cx + R, cy + 1, rim);
            // the hour marks
            for (int k = 1; k < 12; k++) {
                double a = Math.PI + k * Math.PI / 12;
                int hx = cx + (int) Math.round(Math.cos(a) * (R - 4)), hy = cy + (int) Math.round(Math.sin(a) * (R - 4));
                g.fill(hx, hy, hx + 1, hy + 1, 0xA0FFDE8C);
            }
            // the gnomon's shadow: from the hub, opposite the sun (by day)
            if (day) {
                float t = Mth.positiveModulo(dayTime, 24000f);
                double a = Math.PI + Math.PI * Mth.clamp(1f - t / 12000f, 0f, 1f);   // dawn: pointing west (right), dusk: east (left)
                for (int r = 2; r < R - 3; r++) {
                    int sx = cx + (int) Math.round(Math.cos(a) * r), sy = cy + (int) Math.round(Math.sin(a) * r);
                    g.fill(sx, sy, sx + 2, sy + 1, 0xFF0A0604);
                }
            }
            g.fill(cx - 3, cy - 3, cx + 3, cy + 1, day ? 0xFFFFF0B4 : 0xFF7878A0);
            // the Sun Meter: 0-100%, the climb above it in white
            int bx = x + 2, bw = 48, by = y + 26;
            g.fill(bx, by, bx + bw, by + 3, 0xE6140E08);
            g.fill(bx, by, bx + (int) (bw * Mth.clamp(m.meter, 0f, 1f)), by + 3, 0xFFFFD25A);
            if (m.meter > 1f) g.fill(bx, by, bx + (int) (bw * Mth.clamp(m.meter - 1f, 0f, 0.25f) * 4f), by + 1, 0xFFFFFFFF);
            // Pride: ten pips down the right side
            for (int i = 0; i < 10; i++) g.fill(x + 52, y + 26 - i * 3, x + 54, y + 28 - i * 3, i < m.pride ? 0xFFFFB43C : 0x50000000);
            // the night's bank: embers along the left
            int emb = Math.round(m.banked / 0.3f * 5);
            for (int i = 0; i < 5; i++) g.fill(x - 3, y + 26 - i * 3, x - 1, y + 28 - i * 3, i < emb ? 0xFFFF7A2A : 0x40000000);
        });
        String label = m.zenith > 0 ? (m.borrowed ? "BORROWED " : "ZENITH ") + (m.zenith / 20 + 1) + "s" : Math.round(m.meter * 100) + "%";
        g.drawString(mc.font, label, cx - mc.font.width(label) / 2, y - 10, m.zenith > 0 ? 0xFFFFF4C8 : day ? 0xFFFFDE8C : 0xFF9AA0C8, true);
        if (m.borrowUsed && m.zenith <= 0) g.fill(x + 50, y - 2, x + 53, y + 1, 0xFF7A2020);   // today's noon is borrowed
    }

    /** The automatic screenshot run (UiShots): a sample sundial. */
    public static void sample(int id, float meter, float sun, int pride, int zenith, boolean borrowed) {
        ALL.put(id, new MeridianPackets.Sync(id, true, meter, sun, pride, zenith, borrowed, borrowed, 0.15f));
    }
}
