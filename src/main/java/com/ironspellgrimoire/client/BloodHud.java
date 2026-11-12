package com.ironspellgrimoire.client;

import com.ironspellgrimoire.network.FxPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The Blood Gauge: a glass vial beside the hotbar (left of the off-hand slot) while the Blood Grimoire is equipped.
 * Blood fills it as you pay for Blood spells and spill your foes' blood - its surface sways, bubbles rise. When it's
 * full it beats red and reads READY: Scarlet Moon can be cast. Under the Moon the blood in it turns to white ice-light
 * and drains away over the minute, with the seconds left above it.
 * <p>Fed by FxPacket shape 81 (server/BloodFx.syncGauge): a0 = gauge (0-100), r = 1 if the grimoire is equipped,
 * sweep = ticks of the Moon left, life = the Moon's full length.
 */
@Mod.EventBusSubscriber(modid = "iron_spell_grimoire", value = net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class BloodHud {
    private BloodHud() {}

    private static float gauge, shown = -1;
    private static boolean equipped;
    private static long moonEnd;
    private static int moonLen = 1;

    public static void set(FxPacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        gauge = Mth.clamp(p.a0, 0F, 100F);
        equipped = p.r > 0.5F;
        moonEnd = p.sweep > 0 ? mc.level.getGameTime() + p.sweep : 0;
        moonLen = Math.max(1, p.life);
    }

    /** Is the Scarlet Moon up on this client (every menu shows the ice version)? */
    public static boolean moon() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.getGameTime() < moonEnd;
    }

    @SubscribeEvent
    public static void onTick(net.minecraftforge.event.TickEvent.ClientTickEvent e) {
        if (e.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        com.ironspellgrimoire.spells.IceView.on = moon();
    }

    /** Is the vial on screen (for anything that would sit in the same place)? */
    public static boolean shown() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && (equipped || mc.level.getGameTime() < moonEnd);
    }

    private static int lerp(int a, int b, float t) {
        int r = (int) Mth.lerp(t, (a >> 16) & 255, (b >> 16) & 255), g = (int) Mth.lerp(t, (a >> 8) & 255, (b >> 8) & 255), bl = (int) Mth.lerp(t, a & 255, b & 255);
        return 0xFF000000 | r << 16 | g << 8 | bl;
    }

    @SubscribeEvent
    public static void onGui(RenderGuiEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui || !shown()) return;
        GuiGraphics g = e.getGuiGraphics();
        double time = mc.level.getGameTime() + e.getPartialTick();
        long now = mc.level.getGameTime();
        boolean moon = now < moonEnd;
        float frac = moon ? (float) Mth.clamp((moonEnd - time) / moonLen, 0, 1) : gauge / 100F;
        shown = shown < 0 ? frac : shown + (frac - shown) * 0.15F;
        boolean full = !moon && gauge >= 99.9F;
        float beat = (float) Math.pow(Math.max(0, Math.sin(time * 0.26)), 6);

        int W = 10, H = 40;
        int x = g.guiWidth() / 2 - 91 - 29 - W - 6, y = g.guiHeight() - H - 4;
        int glass = moon ? 0xFFDDE8FF : 0xFFB9A8AC, dark = 0xFF14090B;

        // the glow behind it when it's ready, or under the Moon
        if (full || moon) {
            int col = moon ? 0xC8E0FF : 0xD0101C;
            int al = (int) ((moon ? 0.28F : 0.25F + 0.35F * beat) * 255);
            for (int i = 3; i >= 1; i--) g.fill(x - i, y + 4 - i, x + W + i, y + H + i, (al / (i + 1)) << 24 | col);
        }
        // neck and stopper
        g.fill(x + 2, y, x + W - 2, y + 4, dark);
        g.fill(x + 3, y + 1, x + W - 3, y + 3, moon ? 0xFF9FB8E0 : 0xFF5A2A20);
        // the glass body: dark outline, rounded bottom
        g.fill(x, y + 4, x + W, y + H - 1, dark);
        g.fill(x + 1, y + H - 1, x + W - 1, y + H, dark);
        g.fill(x + 1, y + 5, x + W - 1, y + H - 1, 0xFF201014);   // empty inside
        int innerTop = y + 5, innerBot = y + H - 1, ih = innerBot - innerTop;
        int level = innerBot - Math.round(ih * Mth.clamp(shown, 0, 1));
        int deep = moon ? 0xFF8FB2F0 : 0xFF5C0008, top = moon ? 0xFFF4F8FF : 0xFFB3121E, surf = moon ? 0xFFFFFFFF : 0xFFE8505A;
        for (int cx = x + 1; cx < x + W - 1; cx++) {   // the liquid, its surface swaying
            int wob = (int) Math.round(Math.sin(time * 0.25 + cx * 0.9) * (shown > 0.02 && shown < 0.98 ? 1.0 : 0));
            int s = Mth.clamp(level + wob, innerTop, innerBot);
            for (int yy = s; yy < innerBot; yy++) {
                float t = (float) (yy - s) / Math.max(1, innerBot - s);
                g.fill(cx, yy, cx + 1, yy + 1, lerp(top, deep, t));
            }
            if (s < innerBot) g.fill(cx, s, cx + 1, s + 1, surf);
        }
        if (shown > 0.05F) for (int b = 0; b < 3; b++) {   // bubbles rising (snowflake glints under the Moon)
            double ph = (time * 0.035 + b * 0.37) % 1.0;
            int by = innerBot - 1 - (int) (ph * (innerBot - level - 1));
            int bx = x + 2 + (b * 3) % (W - 4);
            if (by > level + 1) g.fill(bx, by, bx + 1, by + 1, moon ? 0xFFFFFFFF : 0xFFFF8A90);
        }
        // the glass: a highlight down the left, a rim on the right
        g.fill(x + 1, y + 5, x + 2, y + H - 2, (0x70 << 24) | (glass & 0xFFFFFF));
        g.fill(x + W - 2, y + 6, x + W - 1, y + H - 3, (0x30 << 24) | (glass & 0xFFFFFF));
        for (int m = 1; m < 4; m++) {   // marks at the quarters
            int my = innerBot - ih * m / 4;
            g.fill(x + W - 3, my, x + W - 1, my + 1, 0x90000000 | (glass & 0xFFFFFF));
        }
        // the label above it
        String label = moon ? Math.max(0, (moonEnd - now + 19) / 20) + "s" : full ? "READY" : (int) gauge + "%";
        int lc = moon ? 0xFFEAF2FF : full ? lerp(0xFFB3121E, 0xFFFFD0D4, beat) : 0xFFC9A0A6;
        int lw = mc.font.width(label);
        g.drawString(mc.font, label, x + W / 2 - lw / 2, y - 10, lc, true);
    }
}
