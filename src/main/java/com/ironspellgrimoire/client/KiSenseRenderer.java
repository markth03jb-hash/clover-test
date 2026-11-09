package com.ironspellgrimoire.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
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
 * Ki Sense, client side: pixel-art flames of ki licking up around every creature the caster sensed (the same sprites
 * as the moves), with embers rising off them - seen through walls.
 *  red    = monsters (hostile)
 *  green  = passive creatures
 *  yellow = neutral creatures (wolves, bees, golems, endermen...)
 *  blue   = other players
 * Drawn additively at low strength so it reads as an aura, not a solid shape.
 */
@Mod.EventBusSubscriber(modid = "iron_spell_grimoire", value = Dist.CLIENT)
public class KiSenseRenderer {
    private static final float[][] COLORS = {
            {1.00F, 0.16F, 0.10F},   // hostile
            {0.30F, 1.00F, 0.38F},   // passive
            {1.00F, 0.82F, 0.22F},   // neutral
            {0.36F, 0.62F, 1.00F},   // player
    };
    private static final float STRENGTH = 0.32F;   // overall opacity of the flames
    private static final float STRONG = 0.85F;     // Mana Zone ki: much easier to see
    private static final int TONGUES = 9;           // flame licks around each creature
    private static final int FADE_TICKS = 20;

    private record Mark(int type, long start, long until, boolean strong) {}
    private static final Map<Integer, Mark> MARKS = new HashMap<>();

    /** Called from the network (FxPacket shape 4). */
    public static void mark(int entityId, int type, int life) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        long now = mc.level.getGameTime();
        boolean strong = type >= 8;   // Mana Zone ki: bright, with a foot ring and a beacon
        Mark old = MARKS.get(entityId);
        long start = old != null && old.until() >= now && old.strong() == strong ? old.start() : now;   // refreshing keeps it steady
        MARKS.put(entityId, new Mark(Mth.clamp(type & 7, 0, COLORS.length - 1), start, now + life, strong));
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
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        // the same pixel-art sprites as the moves: flame tongues, embers, streaks and a diamond (one pass per sprite)
        for (int pass = 0; pass < 4; pass++) {
            RenderSystem.setShaderTexture(0, TEX[pass]);
            BufferBuilder bb = Tesselator.getInstance().getBuilder();
            bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            for (Map.Entry<Integer, Mark> en : MARKS.entrySet()) {
                Entity ent = mc.level.getEntity(en.getKey());
                if (ent == null || ent.isRemoved() || !ent.isAlive()) continue;
                Mark mk = en.getValue();
                float fadeOut = Mth.clamp((float) (mk.until() - now) / FADE_TICKS, 0F, 1F);   // only fades as the sense wears off
                float a = (mk.strong() ? STRONG : STRENGTH) * fadeOut;
                if (a <= 0.01F) continue;
                Vec3 base = new Vec3(Mth.lerp(pt, ent.xo, ent.getX()), Mth.lerp(pt, ent.yo, ent.getY()), Mth.lerp(pt, ent.zo, ent.getZ())).subtract(cam);
                float r = ent.getBbWidth() * 0.5F + 0.12F;
                float[] col = COLORS[mk.type()];
                switch (pass) {
                    case 0 -> tongues(bb, m, base, r, ent.getBbHeight(), col, a, now, ent.getId());
                    case 1 -> embers(bb, m, base, r, ent.getBbHeight(), col, a, now, ent.getId(), mk.strong());
                    case 2 -> { if (mk.strong()) pillar(bb, m, base, ent.getBbHeight(), col, a, now); }
                    default -> { if (mk.strong()) diamond(bb, m, base, ent.getBbHeight(), col, a, now, ent.getId()); }
                }
            }
            BufferUploader.drawWithShader(bb.end());
        }
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private static final ResourceLocation[] TEX = {
            ResourceLocation.fromNamespaceAndPath("iron_spell_grimoire", "textures/effect/pixel/tongue.png"),
            ResourceLocation.fromNamespaceAndPath("iron_spell_grimoire", "textures/effect/pixel/ember.png"),
            ResourceLocation.fromNamespaceAndPath("iron_spell_grimoire", "textures/effect/pixel/streak.png"),
            ResourceLocation.fromNamespaceAndPath("iron_spell_grimoire", "textures/effect/pixel/diamond.png")};

    /**
     * A camera-facing pixel sprite standing upright at {@code c} (camera-relative): w wide, h tall from its foot.
     * Uses frame 0 of the sprite strip (the whole sprite); {@code flip} mirrors it (flames flicker by flipping).
     */
    private static void sprite(BufferBuilder bb, Matrix4f m, Vec3 c, float w, float h, float[] col, float a, boolean flip) {
        double len = Math.max(1.0E-4, Math.sqrt(c.x * c.x + c.z * c.z));
        float rx = (float) (-c.z / len * w * 0.5), rz = (float) (c.x / len * w * 0.5);
        float u0 = flip ? 1 : 0, u1 = flip ? 0 : 1;
        float x = (float) c.x, y = (float) c.y, z = (float) c.z;
        bb.vertex(m, x - rx, y, z - rz).uv(u0, 0.25F).color(col[0], col[1], col[2], a).endVertex();
        bb.vertex(m, x + rx, y, z + rz).uv(u1, 0.25F).color(col[0], col[1], col[2], a).endVertex();
        bb.vertex(m, x + rx, y + h, z + rz).uv(u1, 0.0F).color(col[0], col[1], col[2], a).endVertex();
        bb.vertex(m, x - rx, y + h, z - rz).uv(u0, 0.0F).color(col[0], col[1], col[2], a).endVertex();
    }

    /** Pixel flame tongues licking up all round the body, flickering (flip-book) and swaying. */
    private static void tongues(BufferBuilder bb, Matrix4f m, Vec3 c, float r, float h, float[] col, float a, double t, int seed) {
        for (int i = 0; i < TONGUES; i++) {
            double ang = Math.PI * 2 * i / TONGUES + seed;
            double ph = t * 0.32 + seed * 0.77 + i * 1.9;
            float tall = h * (0.55F + 0.25F * (float) Math.abs(Math.sin(ph)));
            float y0 = h * (0.05F + 0.25F * (float) ((i * 0.37) % 1.0));
            Vec3 at = c.add(Math.cos(ang) * r, y0, Math.sin(ang) * r);
            boolean flip = (((int) (t / 2.0) + i) & 1) == 1;
            sprite(bb, m, at, r * 1.1F, tall, col, a, flip);
        }
    }

    /** Embers rising off the body and fading as they go (a looping stream, so no state to keep). */
    private static void embers(BufferBuilder bb, Matrix4f m, Vec3 c, float r, float h, float[] col, float a, double t, int seed, boolean strong) {
        int n = strong ? 10 : 6;
        for (int i = 0; i < n; i++) {
            double cyc = (t * 0.035 + i / (double) n + seed * 0.13) % 1.0;   // 0..1 up the body
            double ang = i * 2.4 + seed;
            Vec3 at = c.add(Math.cos(ang) * r * 0.9, h * (0.1 + cyc * 1.2), Math.sin(ang) * r * 0.9);
            float s = 0.12F;
            sprite(bb, m, at, s, s, col, a * (float) (1.0 - cyc), false);
        }
        if (strong) {   // Mana Zone ki: a ring of embers on the ground at its feet
            for (int i = 0; i < 16; i++) {
                double ang = Math.PI * 2 * i / 16 + t * 0.03;
                sprite(bb, m, c.add(Math.cos(ang) * (r + 0.3), 0.02, Math.sin(ang) * (r + 0.3)), 0.14F, 0.14F, col, a, false);
            }
        }
    }

    /** Mana Zone ki: a pixel pillar of light rising over the head, seen from far away. */
    private static void pillar(BufferBuilder bb, Matrix4f m, Vec3 c, float h, float[] col, float a, double t) {
        for (int k = 0; k < 3; k++) sprite(bb, m, c.add(0, h + 0.2 + k * 0.85, 0), 0.22F, 0.9F, col, a * (0.8F - k * 0.22F), false);
    }

    /** Mana Zone ki: a diamond bobbing above it. */
    private static void diamond(BufferBuilder bb, Matrix4f m, Vec3 c, float h, float[] col, float a, double t, int seed) {
        float bob = (float) Math.sin(t * 0.15 + seed) * 0.08F;
        sprite(bb, m, c.add(0, h + 0.45 + bob, 0), 0.32F, 0.32F, col, a, false);
    }

}
