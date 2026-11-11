package com.ironspellgrimoire.client;

import com.ironspellgrimoire.network.FxPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** The Elf Raid on your screen: ELF RAID in red across a torn black band as it begins; RAID BROKEN in gold when it's won. */
@Mod.EventBusSubscriber(modid = "iron_spell_grimoire", value = Dist.CLIENT)
public final class RaidBanner {
    private RaidBanner() {}

    private static int age = -1, life;
    private static boolean won;

    public static void start(FxPacket p) { life = p.life; won = p.sweep == 1; age = 0; }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent e) { if (e.phase == TickEvent.Phase.END && age >= 0 && ++age >= life) age = -1; }

    private static float ease(float k) { k = Mth.clamp(k, 0f, 1f); return k * k * (3 - 2 * k); }

    @SubscribeEvent
    public static void onGui(RenderGuiEvent.Post e) {
        if (age < 0) return;
        Minecraft mc = Minecraft.getInstance();
        GuiGraphics g = e.getGuiGraphics();
        float t = age + e.getPartialTick();
        int w = g.guiWidth(), h = g.guiHeight();
        float a = ease(t / 6f) * (1f - ease((t - (life - 12)) / 12f));
        if (a <= 0) return;
        int cy = (int) (h * 0.3f), bh = 34;
        int col = won ? 0xFFD040 : 0xE02020;
        java.util.Random r = new java.util.Random(7);
        for (int x = 0; x < w; x += 4) {   // a torn band: ragged top and bottom
            int up = r.nextInt(6), dn = r.nextInt(6);
            g.fill(x, cy - bh / 2 - up, x + 4, cy + bh / 2 + dn, (int) (0xC8 * a) << 24);
        }
        g.fill(0, cy - bh / 2 - 1, (int) (w * ease(t / 8f)), cy - bh / 2, (int) (0xFF * a) << 24 | col);
        g.fill(0, cy + bh / 2, (int) (w * ease(t / 8f)), cy + bh / 2 + 1, (int) (0xFF * a) << 24 | col);
        String s = won ? "RAID BROKEN" : "ELF RAID";
        float sc = 3.2f * (1f + 0.3f * (1f - ease(t / 6f)));
        g.pose().pushPose();
        g.pose().translate(w / 2f, cy - 4 * sc / 1.0f + 2, 0);
        g.pose().scale(sc, sc, 1);
        g.drawString(mc.font, s, -mc.font.width(s) / 2, 0, Math.max(4, (int) (255 * a)) << 24 | col, true);
        g.pose().popPose();
        String sub = won ? "The village stands." : "Defend the village - four waves, then their leader.";
        g.drawString(mc.font, sub, (w - mc.font.width(sub)) / 2, cy + bh / 2 + 8, Math.max(4, (int) (255 * a)) << 24 | 0xE0E0E0, true);
    }
}
