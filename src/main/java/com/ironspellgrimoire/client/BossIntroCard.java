package com.ironspellgrimoire.client;

import com.ironspellgrimoire.network.FxPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * A boss's title card (server/BossIntro), like a fighting game's. The full card (first meeting): your view turns to
 * the boss, letterbox bars close in, a slanted band of the boss's colour slashes across the screen, its name slams in
 * huge over it with its epithet under, a white edge flashes, and it all wipes away. The quick card (later meetings):
 * the band and name only, smaller, for a second.
 */
@Mod.EventBusSubscriber(modid = "iron_spell_grimoire", value = Dist.CLIENT)
public final class BossIntroCard {
    private BossIntroCard() {}

    /** Per boss (server/BossIntro order): the epithet and the card's colours (band, accent). */
    private static final String[] EPITHET = {
            "Bone Magic  -  host of a devil",
            "Despair, given claws",
            "The mage who copies every grimoire he touches",
            "Swordsman of the elves",
            "He walks out of the dead",
            "Every word a curse",
            "Your own magic, turned against you",
            com.ironspellgrimoire.server.MemoryStory.EPITHETS[0], com.ironspellgrimoire.server.MemoryStory.EPITHETS[1], com.ironspellgrimoire.server.MemoryStory.EPITHETS[2]};
    private static final int[] BAND = {0x2A2420, 0x0E2A24, 0x241A30, 0x2A2614, 0x14102A, 0x22102A, 0x202024, 0x16161C, 0x141C26, 0x2A2010};
    private static final int[] ACCENT = {0xE8DCC4, 0x5CE0B0, 0xC8A0FF, 0xFFE6A0, 0x9A7CFF, 0x8AE060, 0xFFFFFF, 0xD8D8E8, 0xA8C8E8, 0xFFD27A};

    private static int age = -1, life, idx, bossId;
    private static boolean full, unbound;
    private static String name = "";
    private static float yaw0, pitch0;

    public static void start(FxPacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        Entity e = mc.level.getEntity(p.id);
        idx = Mth.clamp(p.sweep, 0, EPITHET.length - 1);
        bossId = p.id;
        full = p.h > 0.5f;
        unbound = p.a0 > 0.5f;
        name = e == null ? "???" : e.getDisplayName().getString();
        life = p.life;
        age = 0;
        yaw0 = mc.player.getYRot(); pitch0 = mc.player.getXRot();
        mc.player.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1f, full ? 0.5f : 0.8f);
    }

    public static boolean running() { return age >= 0; }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END || age < 0) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) { age = -1; return; }
        age++;
        if (full && age <= 16) {   // turn to face it
            Entity b = mc.level.getEntity(bossId);
            if (b != null) {
                Vec3 d = b.position().add(0, b.getBbHeight() * 0.7, 0).subtract(p.getEyePosition());
                float ty = (float) Math.toDegrees(Math.atan2(-d.x, d.z)), tp = (float) -Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z)));
                float k = Mth.clamp(age / 16f, 0f, 1f);
                k = k * k * (3 - 2 * k);
                p.setYRot(yaw0 + Mth.wrapDegrees(ty - yaw0) * k);
                p.setXRot(pitch0 + (tp - pitch0) * k);
                p.yRotO = p.getYRot(); p.xRotO = p.getXRot();
            }
        }
        if (full && age == 18) p.playSound(SoundEvents.GENERIC_EXPLODE, 0.5f, 1.6f);
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
        float lead = full ? 14f : 0f;   // the full card waits for the turn before the band comes in
        float out = 1f - ease((t - (life - 8)) / 8f);
        int band = BAND[idx], acc = unbound ? 0xFF4040 : ACCENT[idx];
        if (full) {   // letterbox bars
            int bar = (int) (h * 0.12f * ease(t / 10f) * out);
            g.fill(0, 0, w, bar, 0xFF000000);
            g.fill(0, h - bar, w, h, 0xFF000000);
        }
        float in = ease((t - lead) / 6f);
        if (in <= 0 || out <= 0) return;
        int cy = full ? (int) (h * 0.62f) : (int) (h * 0.18f);
        int bh = full ? 46 : 26;
        // the slanted band, slashing in from the left
        int reach = (int) ((w + 80) * in);
        g.pose().pushPose();
        g.pose().translate(0, cy, 0);
        for (int y = -bh / 2; y < bh / 2; y++) {
            int x0 = -40 + (y + bh / 2) / 2;   // the slant
            int a = (int) (0xD8 * out);
            g.fill(x0, y, Math.min(x0 + reach, w + 40), y + 1, a << 24 | band);
        }
        g.fill(-40, -bh / 2 - 2, Math.min(-40 + reach, w + 40), -bh / 2, (int) (0xFF * out) << 24 | acc);
        g.fill(-40, bh / 2, Math.min(-40 + reach, w + 40), bh / 2 + 2, (int) (0xFF * out) << 24 | acc);
        g.pose().popPose();
        // the white edge flash as it lands
        float flash = 1f - ease((t - lead - 6) / 5f);
        if (full && t > lead + 4 && flash > 0) g.fill(0, cy - bh / 2 - 10, w, cy + bh / 2 + 10, (int) (0x90 * flash) << 24 | 0xFFFFFF);
        // the name, sliding in from the right, and its epithet
        float slide = 1f - ease((t - lead - 2) / 7f);
        int ta = Math.max(4, (int) (255 * ease((t - lead - 2) / 4f) * out));
        float sc = full ? 3.0f : 1.8f;
        String nm = name.toUpperCase();
        g.pose().pushPose();
        g.pose().translate(w / 2f + slide * w * 0.4f, cy - (full ? 14 : 8), 0);
        g.pose().scale(sc, sc, 1);
        g.drawString(f, nm, -f.width(nm) / 2, 0, ta << 24 | 0xFFFFFF, true);
        g.pose().popPose();
        {
            String ep = unbound ? "Unbound  -  nothing holds him back now" : EPITHET[idx];
            float es = full ? 1.2f : 0.9f;
            g.pose().pushPose();
            g.pose().translate(w / 2f - slide * w * 0.3f, cy + (full ? 13 : 9), 0);
            g.pose().scale(es, es, 1);
            g.drawString(f, ep, -f.width(ep) / 2, 0, ta << 24 | (acc & 0xFFFFFF), true);
            g.pose().popPose();
        }
    }
}
