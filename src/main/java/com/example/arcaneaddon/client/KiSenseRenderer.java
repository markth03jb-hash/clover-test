package com.example.arcaneaddon.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

/**
 * Ki Sense, client side: a faint flame of ki licking up around every creature the caster sensed, seen through walls.
 *  red    = monsters (hostile)
 *  green  = passive creatures
 *  yellow = neutral creatures (wolves, bees, golems, endermen...)
 *  blue   = other players
 * Drawn additively at low strength so it reads as an aura, not a solid shape.
 */
@Mod.EventBusSubscriber(modid = "arcane_addon", value = Dist.CLIENT)
public class KiSenseRenderer {
    private static final float[][] COLORS = {
            {1.00F, 0.16F, 0.10F},   // hostile
            {0.30F, 1.00F, 0.38F},   // passive
            {1.00F, 0.82F, 0.22F},   // neutral
            {0.36F, 0.62F, 1.00F},   // player
    };
    private static final float STRENGTH = 0.32F;   // overall opacity of the flames
    private static final int TONGUES = 9;           // flame licks around each creature
    private static final int FADE_TICKS = 20;

    private record Mark(int type, long start, long until) {}
    private static final Map<Integer, Mark> MARKS = new HashMap<>();

    /** Called from the network (FxPacket shape 4). */
    public static void mark(int entityId, int type, int life) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        long now = mc.level.getGameTime();
        MARKS.put(entityId, new Mark(Mth.clamp(type, 0, COLORS.length - 1), now, now + life));
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { MARKS.clear(); return; }
        long now = mc.level.getGameTime();
        MARKS.values().removeIf(m -> m.until() < now);
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || MARKS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float pt = e.getPartialTick();
        double now = mc.level.getGameTime() + pt;
        Vec3 cam = e.getCamera().getPosition();
        Matrix4f m = e.getPoseStack().last().pose();

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);   // additive: a glow, never a solid wall
        RenderSystem.disableDepthTest();   // ki is felt through walls
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        for (Map.Entry<Integer, Mark> en : MARKS.entrySet()) {
            Entity ent = mc.level.getEntity(en.getKey());
            if (ent == null || ent.isRemoved() || !ent.isAlive()) continue;
            Mark mk = en.getValue();
            float fadeIn = Mth.clamp((float) (now - mk.start()) / 8F, 0F, 1F);
            float fadeOut = Mth.clamp((float) (mk.until() - now) / FADE_TICKS, 0F, 1F);
            float a = STRENGTH * fadeIn * fadeOut;
            if (a <= 0.01F) continue;
            Vec3 base = new Vec3(Mth.lerp(pt, ent.xo, ent.getX()), Mth.lerp(pt, ent.yo, ent.getY()), Mth.lerp(pt, ent.zo, ent.getZ())).subtract(cam);
            flame(bb, m, base, ent.getBbWidth() * 0.5F + 0.12F, ent.getBbHeight(), COLORS[mk.type()], a, now, ent.getId());
        }

        BufferUploader.drawWithShader(bb.end());
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    /** A thin shell hugging the body plus flickering flame tongues rising off it. */
    private static void flame(BufferBuilder bb, Matrix4f m, Vec3 c, float r, float h, float[] col, float a, double t, int seed) {
        double phase = t * 0.32 + seed * 0.77;
        for (int i = 0; i < TONGUES; i++) {
            double a0 = Math.PI * 2 * i / TONGUES, a1 = Math.PI * 2 * (i + 1) / TONGUES, am = (a0 + a1) * 0.5;
            float flick = (float) (0.22 * Math.sin(phase + i * 1.9) + 0.12 * Math.sin(phase * 2.6 + i * 0.7));
            // body shell: bright at the feet, fading out towards the head
            float shellTop = h * 0.75F;
            vtx(bb, m, c, a0, r, 0, col, a * 0.55F);
            vtx(bb, m, c, a1, r, 0, col, a * 0.55F);
            vtx(bb, m, c, a1, r * 0.9F, shellTop, col, 0F);
            vtx(bb, m, c, a0, r * 0.9F, shellTop, col, 0F);
            // flame tongue: from a band on the body up to a wavering tip above the head
            float tipH = h * (1.02F + flick);
            double sway = 0.08 * Math.sin(phase * 1.7 + i);
            vtx(bb, m, c, a0, r * 0.95F, h * 0.35F, col, a);
            vtx(bb, m, c, a1, r * 0.95F, h * 0.35F, col, a);
            vtx(bb, m, c, am + sway, r * 0.35F, tipH, col, 0F);
            vtx(bb, m, c, am + sway, r * 0.35F, tipH, col, 0F);
        }
    }

    private static void vtx(BufferBuilder bb, Matrix4f m, Vec3 c, double ang, float r, float y, float[] col, float a) {
        bb.vertex(m, (float) (c.x + Math.cos(ang) * r), (float) (c.y + y), (float) (c.z + Math.sin(ang) * r))
                .color(col[0], col[1], col[2], a).endVertex();
    }
}
