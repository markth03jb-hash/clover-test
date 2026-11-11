package com.ironspellgrimoire.client;

import com.ironspellgrimoire.network.FxPacket;
import com.ironspellgrimoire.server.SpellEvolution;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.util.Utils;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * A spell evolving (server/SpellEvolution), on your screen for three seconds: a band of the grimoire's colour sweeps
 * across, the spell's icon flares in the middle of it, SPELL EVOLVED over it and the spell's name under - gold and
 * white, with a second flare, on an Ascended grimoire.
 */
@Mod.EventBusSubscriber(modid = "iron_spell_grimoire", value = Dist.CLIENT)
public final class EvolutionToast {
    private EvolutionToast() {}

    private static int age = -1, life;
    private static String name = "";
    private static ResourceLocation icon;
    private static boolean asc;
    private static int[] col = {0, 0, 0xE0B060, 0xFFD27A, 0x2A1A10, 0xF0E0B0};

    public static void start(FxPacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || p.id != mc.player.getId()) return;
        ItemStack book = Utils.getPlayerSpellbookStack(mc.player);
        List<String> ids = SpellEvolution.spells(book);
        int i = (int) p.h;
        if (i < 0 || i >= ids.size()) return;
        String id = ids.get(i);
        name = Component.translatable("spell.iron_spell_grimoire." + id).getString();
        icon = null;
        try {
            AbstractSpell s = SpellRegistry.getSpell(new ResourceLocation("iron_spell_grimoire", id));
            if (s != null) icon = s.getSpellIconResource();
        } catch (Throwable ignored) {}
        asc = p.sweep == 1;
        col = BookUi.colours();
        life = p.life;
        age = 0;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase == TickEvent.Phase.END && age >= 0 && ++age >= life) age = -1;
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
        float in = ease(t / 8f), out = 1f - ease((t - (life - 10)) / 10f), a = in * out;
        if (a <= 0) return;
        int cy = (int) (h * 0.3f), band = 30;
        int accent = asc ? 0xFFD870 : col[3];
        // the band sweeping across
        int x1 = (int) (w * in);
        g.fill(0, cy - band / 2, x1, cy + band / 2, (int) (0xB0 * a) << 24 | (col[4] & 0xFFFFFF));
        g.fill(0, cy - band / 2 - 2, x1, cy - band / 2, (int) (0xFF * a) << 24 | (accent & 0xFFFFFF));
        g.fill(0, cy + band / 2, x1, cy + band / 2 + 2, (int) (0xFF * a) << 24 | (accent & 0xFFFFFF));
        // the icon's flare: rays turning behind it
        float fl = ease((t - 4) / 10f) * out;
        for (int i = 0; i < 12; i++) {
            double ang = i * Math.PI / 6 + t * 0.05;
            for (int k = 10; k < 34; k += 2) {
                int x = w / 2 + (int) (Math.cos(ang) * k), y = cy + (int) (Math.sin(ang) * k);
                int al = (int) (0x90 * fl * (1 - (k - 10) / 24f));
                g.fill(x, y, x + 2, y + 2, al << 24 | (accent & 0xFFFFFF));
            }
        }
        if (icon != null) g.blit(icon, w / 2 - 12, cy - 12, 24, 24, 0, 0, 16, 16, 16, 16);
        int ta = Math.max(4, (int) (255 * a));
        String head = asc ? "✧ SPELL EVOLVED ✧" : "✦ SPELL EVOLVED ✦";
        g.pose().pushPose();
        g.pose().translate(w / 2f, cy - band / 2f - 16, 0);
        g.pose().scale(1.5f, 1.5f, 1);
        g.drawString(f, head, -f.width(head) / 2, 0, ta << 24 | (accent & 0xFFFFFF), true);
        g.pose().popPose();
        g.pose().pushPose();
        g.pose().translate(w / 2f, cy + band / 2f + 6, 0);
        g.pose().scale(1.3f, 1.3f, 1);
        g.drawString(f, name, -f.width(name) / 2, 0, ta << 24 | 0xFFFFFF, true);
        String sub = asc ? "+35% - every hit echoes in gold" : "+20% - every hit flares";
        g.drawString(f, sub, -f.width(sub) / 2, 11, ta << 24 | 0xC0C0C0, true);
        g.pose().popPose();
        if (asc && t > 10 && t < 16) g.fill(0, 0, w, h, (int) (0x60 * (1 - (t - 10) / 6f)) << 24 | 0xFFF4C0);   // the second, gold flash
    }
}
