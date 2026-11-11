package com.ironspellgrimoire.client;

import com.ironspellgrimoire.network.FxPacket;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The killing blow on a boss (server/BossCores), frozen for a moment on everyone's screen nearby: the colours invert,
 * black streaks shoot out from the boss, white cracks run across the frame from it and the frame shatters - shards
 * flying off - and DEFEATED settles over it with the boss's name. When the blow was an awakened or evolved spell, or
 * came from an Ascended grimoire, the frame burns gold.
 */
@Mod.EventBusSubscriber(modid = "iron_spell_grimoire", value = Dist.CLIENT)
public final class FinisherFrame {
    private FinisherFrame() {}

    private static int age = -1, life;
    private static Vec3 at = Vec3.ZERO;
    private static boolean gold;
    private static String name = "";
    private static long seed;

    public static void start(FxPacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        at = p.c;
        gold = p.sweep == 1;
        var e = mc.level.getEntity(p.id);
        name = e == null ? "" : e.getDisplayName().getString();
        life = p.life;
        seed = (long) (at.x * 31 + at.z * 17);
        age = 0;
        mc.player.playSound(SoundEvents.GLASS_BREAK, 1.2f, 0.5f);
        mc.player.playSound(SoundEvents.ANVIL_LAND, 0.6f, 0.6f);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase == TickEvent.Phase.END && age >= 0 && ++age >= life) age = -1;
    }

    private static float ease(float k) { k = Mth.clamp(k, 0f, 1f); return k * k * (3 - 2 * k); }

    /** Where the boss is on screen (the middle if it's behind you). */
    private static float[] screen(int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        Camera cam = mc.gameRenderer.getMainCamera();
        Vec3 d = at.subtract(cam.getPosition());
        Vector3f look = cam.getLookVector(), up = cam.getUpVector(), left = cam.getLeftVector();
        double z = d.x * look.x() + d.y * look.y() + d.z * look.z();
        if (z < 0.5) return new float[]{w / 2f, h / 2f};
        double fov = Math.toRadians(mc.options.fov().get());
        double k = (h / 2.0) / Math.tan(fov / 2);
        double x = w / 2.0 - (d.x * left.x() + d.y * left.y() + d.z * left.z()) / z * k;
        double y = h / 2.0 - (d.x * up.x() + d.y * up.y() + d.z * up.z()) / z * k;
        return new float[]{(float) Mth.clamp(x, 0, w), (float) Mth.clamp(y, 0, h)};
    }

    @SubscribeEvent
    public static void onRender(RenderGuiEvent.Post e) {
        if (age < 0) return;
        Minecraft mc = Minecraft.getInstance();
        GuiGraphics g = e.getGuiGraphics();
        Font f = mc.font;
        float t = age + e.getPartialTick();
        int w = g.guiWidth(), h = g.guiHeight();
        float[] s = screen(w, h);
        int cx = (int) s[0], cy = (int) s[1];
        java.util.Random rnd = new java.util.Random(seed);
        // the frozen frame: inverted
        float inv = t < 9 ? 1f : 1f - ease((t - 9) / 4f);
        if (inv > 0) invert(g, w, h, inv);
        if (gold && t < 13) g.fill(0, 0, w, h, (int) (0x50 * inv) << 24 | 0xFFC830);
        // black streaks shooting out from the boss
        if (t < 10) for (int i = 0; i < 26; i++) {
            double a = rnd.nextDouble() * Math.PI * 2;
            int len = (int) ((60 + rnd.nextInt(Math.max(w, h))) * ease(t / 4f));
            line(g, cx, cy, cx + Math.cos(a) * len, cy + Math.sin(a) * len, 1 + (i % 3 == 0 ? 1 : 0), 0xE0000000);
        }
        // white cracks running across the frame
        float ck = ease((t - 2) / 8f) * (1f - ease((t - 16) / 6f));
        if (ck > 0) for (int i = 0; i < 9; i++) {
            double a = i * Math.PI * 2 / 9 + rnd.nextDouble() * 0.5;
            double x = cx, y = cy;
            int segs = 7;
            for (int sgt = 0; sgt < segs * ck; sgt++) {
                a += (rnd.nextDouble() - 0.5) * 0.9;
                double len = 18 + rnd.nextDouble() * 40;
                double nx = x + Math.cos(a) * len, ny = y + Math.sin(a) * len;
                line(g, x, y, nx, ny, 2, gold ? 0xF0FFE080 : 0xF0FFFFFF);
                x = nx; y = ny;
            }
        }
        // the frame shatters: shards fly off
        float sk = (t - 9) / 14f;
        if (sk > 0 && sk < 1) for (int i = 0; i < 22; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, sp = 80 + rnd.nextDouble() * 260;
            double x = cx + Math.cos(a) * sp * sk, y = cy + Math.sin(a) * sp * sk + 120 * sk * sk;
            int sz = 4 + rnd.nextInt(10);
            int al = (int) (0xE0 * (1 - sk));
            double r = a + sk * (rnd.nextBoolean() ? 6 : -6);
            double x1 = x + Math.cos(r) * sz, y1 = y + Math.sin(r) * sz, x2 = x + Math.cos(r + 2.2) * sz, y2 = y + Math.sin(r + 2.2) * sz;
            int c = al << 24 | (gold ? 0xFFE080 : 0xFFFFFF);
            line(g, x, y, x1, y1, 2, c); line(g, x1, y1, x2, y2, 2, c); line(g, x2, y2, x, y, 2, c);
        }
        // DEFEATED
        float ta = ease((t - 5) / 4f) * (1f - ease((t - (life - 8)) / 8f));
        if (ta > 0) {
            int a = Math.max(4, (int) (255 * ta));
            int col = gold ? 0xFFD040 : 0xFFFFFF;
            float pop = 1f + 0.25f * (1f - ease((t - 5) / 5f));
            g.pose().pushPose();
            g.pose().translate(w / 2f, h * 0.38f, 0);
            g.pose().scale(3.4f * pop, 3.4f * pop, 1);
            String d = "DEFEATED";
            g.drawString(f, d, -f.width(d) / 2, 0, a << 24 | col, true);
            g.pose().popPose();
            g.pose().pushPose();
            g.pose().translate(w / 2f, h * 0.38f + 34, 0);
            g.pose().scale(1.3f, 1.3f, 1);
            g.drawString(f, name, -f.width(name) / 2, 0, a << 24 | 0xE0E0E0, true);
            g.pose().popPose();
        }
    }

    /** Inverts the colours of what's already on screen ({@code a} = how far). */
    private static void invert(GuiGraphics g, int w, int h, float a) {
        g.flush();
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE_MINUS_DST_COLOR, GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR,
                GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f m = g.pose().last().pose();
        int c = (int) (255 * Mth.clamp(a, 0f, 1f));
        bb.vertex(m, 0, h, 0).color(c, c, c, 255).endVertex();
        bb.vertex(m, w, h, 0).color(c, c, c, 255).endVertex();
        bb.vertex(m, w, 0, 0).color(c, c, c, 255).endVertex();
        bb.vertex(m, 0, 0, 0).color(c, c, c, 255).endVertex();
        BufferUploader.drawWithShader(bb.end());
        RenderSystem.defaultBlendFunc();
    }

    private static void line(GuiGraphics g, double x0, double y0, double x1, double y1, int thick, int col) {
        int n = (int) Math.max(1, Math.hypot(x1 - x0, y1 - y0) / 2.5);
        for (int i = 0; i <= n; i++) {
            int x = (int) (x0 + (x1 - x0) * i / n), y = (int) (y0 + (y1 - y0) * i / n);
            g.fill(x, y, x + thick + 1, y + thick + 1, col);
        }
    }
}
