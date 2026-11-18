package com.ironspellgrimoire.client;

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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * Predator Sight (the Parasite Grimoire, the B key): every creature within 32 blocks outlined through walls - red for
 * what's hostile, a dim rose for the rest, and pulsing white-red for what's weak enough to devour - and a light red
 * tint round the edges of the screen. Drawn only for its user; no vanilla glowing.
 */
@Mod.EventBusSubscriber(modid = "iron_spell_grimoire", value = Dist.CLIENT)
public final class PredatorSight {
    private PredatorSight() {}

    static boolean active() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && ParasiteClient.equipped && ParasiteClient.sight && ParasiteClient.wheelKind(mc) == 1;
    }

    /** Weak enough to devour, as far as this side can tell (the server decides). */
    static boolean devourable(LivingEntity t) {
        if (t.getType().is(net.minecraftforge.common.Tags.EntityTypes.BOSSES) || t instanceof com.ironspellgrimoire.entity.AddonBoss) return false;
        float pct = t.getHealth() / Math.max(1f, t.getMaxHealth());
        return pct <= 0.30f + 0.08f * ParasiteClient.rank(9) + (ParasiteClient.wears(7) ? 0.1f : 0f) || t.getHealth() <= 6 + 2 * ParasiteClient.rank(9);
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || !active()) return;
        Minecraft mc = Minecraft.getInstance();
        float pt = e.getPartialTick();
        Vec3 cam = e.getCamera().getPosition();
        Matrix4f m = e.getPoseStack().last().pose();
        double t = mc.level.getGameTime() + pt;
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);   // a glow, never a solid wall
        RenderSystem.disableDepthTest();   // seen through walls
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (LivingEntity le : mc.level.getEntitiesOfClass(LivingEntity.class, mc.player.getBoundingBox().inflate(32), x -> x != mc.player && x.isAlive() && !x.isInvisible())) {
            Vec3 at = new Vec3(Mth.lerp(pt, le.xo, le.getX()), Mth.lerp(pt, le.yo, le.getY()), Mth.lerp(pt, le.zo, le.getZ()));
            AABB box = le.getBoundingBox().move(at.subtract(le.position())).inflate(0.05).move(cam.reverse());
            float r, g, b, a;
            if (devourable(le) && !(le instanceof Player)) { float p = 0.7F + 0.3F * Mth.sin((float) t * 0.4F); r = 1F; g = 0.55F * p; b = 0.5F * p; a = 0.9F; }
            else if (le instanceof net.minecraft.world.entity.monster.Enemy) { r = 1F; g = 0.12F; b = 0.12F; a = 0.75F; }
            else { r = 0.75F; g = 0.35F; b = 0.4F; a = 0.4F; }
            outline(bb, m, box, r, g, b, a);
        }
        BufferUploader.drawWithShader(bb.end());
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    /** A box's twelve edges as thin bars. */
    private static void outline(BufferBuilder bb, Matrix4f m, AABB b, float r, float g, float bl, float a) {
        double w = 0.035;
        double[] xs = {b.minX, b.maxX}, ys = {b.minY, b.maxY}, zs = {b.minZ, b.maxZ};
        for (double y : ys) for (double z : zs) bar(bb, m, b.minX, y, z, b.maxX, y, z, w, r, g, bl, a);
        for (double x : xs) for (double z : zs) bar(bb, m, x, b.minY, z, x, b.maxY, z, w, r, g, bl, a);
        for (double x : xs) for (double y : ys) bar(bb, m, x, y, b.minZ, x, y, b.maxZ, w, r, g, bl, a);
    }

    /** A bar from one corner to another: two crossed thin quads, so it reads from any angle. */
    private static void bar(BufferBuilder bb, Matrix4f m, double x0, double y0, double z0, double x1, double y1, double z1, double w,
                            float r, float g, float b, float a) {
        Vec3 d = new Vec3(x1 - x0, y1 - y0, z1 - z0).normalize();
        Vec3 s1 = d.cross(Math.abs(d.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).normalize().scale(w), s2 = d.cross(s1).normalize().scale(w);
        for (Vec3 s : new Vec3[]{s1, s2}) {
            v(bb, m, x0 - s.x, y0 - s.y, z0 - s.z, r, g, b, a); v(bb, m, x0 + s.x, y0 + s.y, z0 + s.z, r, g, b, a);
            v(bb, m, x1 + s.x, y1 + s.y, z1 + s.z, r, g, b, a); v(bb, m, x1 - s.x, y1 - s.y, z1 - s.z, r, g, b, a);
        }
    }

    private static void v(BufferBuilder bb, Matrix4f m, double x, double y, double z, float r, float g, float b, float a) {
        bb.vertex(m, (float) x, (float) y, (float) z).color(r, g, b, a).endVertex();
    }

    /** A light red tint round the screen's edges (never over the middle). */
    @SubscribeEvent
    public static void tint(RenderGuiEvent.Pre e) {
        if (!active()) return;
        GuiGraphics g = e.getGuiGraphics();
        int w = g.guiWidth(), h = g.guiHeight(), band = Math.max(20, h / 6);
        g.fillGradient(0, 0, w, band, 0x40800010, 0x00800010);
        g.fillGradient(0, h - band, w, h, 0x00800010, 0x40800010);
        for (int i = 0; i < band; i += 2) {   // the sides, fading inward
            int al = (int) (0x40 * (1 - i / (float) band));
            g.fill(i, 0, i + 2, h, (al << 24) | 0x800010);
            g.fill(w - i - 2, 0, w - i, h, (al << 24) | 0x800010);
        }
    }
}
