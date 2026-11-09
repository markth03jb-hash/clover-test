package com.ironspellgrimoire.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Equinox, seen by the player it hits: their own screen is cut in half before the blow lands.
 *   first  a razor-thin line of light flicks across the whole screen at the angle of the cut
 *   then   the picture splits along it - the two halves slide apart, a dark void opening between them
 *   then   the damage lands (the server times it to the end of this) and the screen is whole again
 * The world as rendered this frame is copied and redrawn in two pieces under the HUD; nothing else changes.
 * Fed by FxPacket shape 43 (life = ticks until the blow, sweep = the cut's angle in degrees).
 */
@Mod.EventBusSubscriber(modid = "iron_spell_grimoire", value = Dist.CLIENT)
public final class ScreenCut {
    private ScreenCut() {}

    private static double start = -1, length = 8;
    private static float angle;
    private static TextureTarget copy;

    public static void start(int life, int angleDeg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        start = mc.level.getGameTime();
        length = Math.max(4, life);
        angle = (float) Math.toRadians(angleDeg);
    }

    @SubscribeEvent
    public static void onGui(RenderGuiEvent.Pre e) {
        Minecraft mc = Minecraft.getInstance();
        if (start < 0 || mc.level == null) return;
        double t = (mc.level.getGameTime() + e.getPartialTick() - start) / length;
        if (t >= 1.0 || t < 0.0) { if (t >= 1.0) start = -1; return; }
        try {
            draw(e.getGuiGraphics(), mc, (float) t);
        } catch (Throwable ex) {
            start = -1;   // never leave the screen broken
        }
    }

    private static void draw(GuiGraphics g, Minecraft mc, float t) {
        RenderTarget main = mc.getMainRenderTarget();
        int fw = main.width, fh = main.height;
        if (copy == null || copy.width != fw || copy.height != fh) {
            if (copy != null) copy.destroyBuffers();
            copy = new TextureTarget(fw, fh, false, Minecraft.ON_OSX);
        }
        // copy this frame's world picture so it can be drawn back in two pieces
        GlStateManager._glBindFramebuffer(0x8CA8, main.frameBufferId);   // GL_READ_FRAMEBUFFER
        GlStateManager._glBindFramebuffer(0x8CA9, copy.frameBufferId);   // GL_DRAW_FRAMEBUFFER
        GlStateManager._glBlitFrameBuffer(0, 0, fw, fh, 0, 0, fw, fh, 0x4000, 0x2600);   // colour, nearest
        main.bindWrite(true);

        float W = g.guiWidth(), H = g.guiHeight();
        float cx = W / 2F, cy = H / 2F;
        float dx = Mth.cos(angle), dy = Mth.sin(angle);   // along the cut
        float nx = -dy, ny = dx;                           // across it
        // phase 1 (first 35%): just the line; phase 2: the halves part, easing open
        float open = t < 0.35F ? 0F : smooth((t - 0.35F) / 0.65F);
        float gap = open * Math.min(W, H) * 0.06F;
        Matrix4f m = g.pose().last().pose();

        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        if (open > 0F) {
            // the void between the halves
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            BufferBuilder bb = Tesselator.getInstance().getBuilder();
            bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            bb.vertex(m, 0, 0, 0).color(0.02F, 0.0F, 0.04F, 1F).endVertex();
            bb.vertex(m, 0, H, 0).color(0.02F, 0.0F, 0.04F, 1F).endVertex();
            bb.vertex(m, W, H, 0).color(0.02F, 0.0F, 0.04F, 1F).endVertex();
            bb.vertex(m, W, 0, 0).color(0.02F, 0.0F, 0.04F, 1F).endVertex();
            BufferUploader.drawWithShader(bb.end());
            // the two halves, each slid away from the line
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.setShaderTexture(0, copy.getColorTextureId());
            for (int side = -1; side <= 1; side += 2) {
                List<float[]> poly = half(W, H, cx, cy, nx * side, ny * side);
                if (poly.size() < 3) continue;
                float ox = nx * side * gap * 0.5F, oy = ny * side * gap * 0.5F;
                bb = Tesselator.getInstance().getBuilder();
                bb.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_TEX);
                for (float[] v : poly) bb.vertex(m, v[0] + ox, v[1] + oy, 0).uv(v[0] / W, 1F - v[1] / H).endVertex();
                BufferUploader.drawWithShader(bb.end());
            }
        }
        // the line of the cut: bright, flickering in, thinning as the halves part
        float lw = (1.2F + 2.0F * (1F - open)) * (t < 0.08F ? t / 0.08F : 1F);
        float a = 0.95F * (1F - open * 0.6F);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float L = W + H;
        for (int side = -1; side <= 1; side += 2) {   // one along each edge of the opening
            float ox = nx * side * gap * 0.5F, oy = ny * side * gap * 0.5F;
            float hx = nx * lw * 0.5F, hy = ny * lw * 0.5F;
            float ax = cx - dx * L + ox, ay = cy - dy * L + oy, bx = cx + dx * L + ox, by = cy + dy * L + oy;
            bb.vertex(m, ax - hx, ay - hy, 0).color(0.9F, 0.8F, 1.0F, a).endVertex();
            bb.vertex(m, ax + hx, ay + hy, 0).color(0.9F, 0.8F, 1.0F, a).endVertex();
            bb.vertex(m, bx + hx, by + hy, 0).color(0.9F, 0.8F, 1.0F, a).endVertex();
            bb.vertex(m, bx - hx, by - hy, 0).color(0.9F, 0.8F, 1.0F, a).endVertex();
            if (open <= 0F) break;
        }
        BufferUploader.drawWithShader(bb.end());
        RenderSystem.enableDepthTest();
    }

    /** The part of the screen rectangle on the {@code (nx, ny)} side of the line through (cx, cy). */
    private static List<float[]> half(float W, float H, float cx, float cy, float nx, float ny) {
        float[][] rect = {{0, 0}, {W, 0}, {W, H}, {0, H}};
        List<float[]> out = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            float[] a = rect[i], b = rect[(i + 1) % 4];
            float da = (a[0] - cx) * nx + (a[1] - cy) * ny, db = (b[0] - cx) * nx + (b[1] - cy) * ny;
            if (da >= 0) out.add(a);
            if ((da >= 0) != (db >= 0)) {
                float k = da / (da - db);
                out.add(new float[]{a[0] + (b[0] - a[0]) * k, a[1] + (b[1] - a[1]) * k});
            }
        }
        return out;
    }

    private static float smooth(float x) {
        x = Mth.clamp(x, 0F, 1F);
        return x * x * (3F - 2F * x);
    }
}
