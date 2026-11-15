package com.ironspellgrimoire.client;

import com.ironspellgrimoire.entity.ShadowSoldier;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

/**
 * A shadow soldier, drawn: the very mob it was, rendered through a tint that turns it to living shadow - near-black
 * with a cold blue sheen - with two burning blue eyes. While it rises it climbs up out of the ground.
 * The tint (Tint) is also used for remnants (client/ShadowClient).
 */
public class ShadowSoldierRenderer extends EntityRenderer<ShadowSoldier> {
    private static final Map<Integer, LivingEntity> PUPPETS = new HashMap<>();

    public ShadowSoldierRenderer(EntityRendererProvider.Context c) { super(c); this.shadowRadius = 0.5F; }

    @Override public ResourceLocation getTextureLocation(ShadowSoldier e) { return FxBase.WHITE; }

    /** A client-only copy of the mob a shadow was, kept per shadow, posed like it each frame. */
    static LivingEntity puppet(Map<Integer, LivingEntity> cache, int key, String kind, Level level) {
        LivingEntity d = cache.get(key);
        if (d != null && d.level() == level && kind.equals(String.valueOf(ForgeRegistries.ENTITY_TYPES.getKey(d.getType())))) return d;
        EntityType<?> t = ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.tryParse(kind));
        Entity e = t == null ? null : t.create(level);
        LivingEntity le = e instanceof LivingEntity l ? l : EntityType.ZOMBIE.create(level);
        if (le instanceof net.minecraft.world.entity.Mob m) m.setNoAi(true);
        cache.put(key, le);
        return le;
    }

    @Override
    public void render(ShadowSoldier s, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        LivingEntity d = puppet(PUPPETS, s.getId(), s.kind(), s.level());
        // pose it like the shadow
        d.setPos(s.getX(), s.getY(), s.getZ());
        d.xo = s.xo; d.yo = s.yo; d.zo = s.zo;
        d.yBodyRot = s.yBodyRot; d.yBodyRotO = s.yBodyRotO; d.yHeadRot = s.yHeadRot; d.yHeadRotO = s.yHeadRotO;
        d.setXRot(s.getXRot()); d.xRotO = s.xRotO; d.setYRot(s.getYRot()); d.yRotO = s.yRotO;
        d.attackAnim = s.attackAnim; d.oAttackAnim = s.oAttackAnim; d.swinging = s.swinging; d.swingTime = s.swingTime;
        d.tickCount = s.tickCount; d.hurtTime = s.hurtTime;
        d.setOnGround(s.onGround());
        if (d.tickCount != d.getPersistentData().getInt("isg_last")) {   // keep its legs moving with the shadow's
            d.getPersistentData().putInt("isg_last", d.tickCount);
            d.walkAnimation.update(Math.min(1, s.walkAnimation.speed() * 1.0F), 0.4F);
        }
        ps.pushPose();
        float rise = Mth.clamp((s.tickCount - s.risingAt() + pt) / 30F, 0, 1);
        ps.translate(0, -(1 - rise) * (1 - rise) * s.getBbHeight(), 0);   // climbing up out of its pool
        float sc = s.rankScale();
        ps.scale(sc, sc, sc);
        var r = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(d);
        r.render(d, yaw, pt, ps, new Tint(buf, 0.10F, 0.12F, 0.2F, s.rank()), light);
        // two burning blue eyes, bright with its rank
        float eyeY = d.getEyeHeight() + 0.02F, half = d.getBbWidth() * 0.5F + 0.02F;
        float bodyYaw = Mth.rotLerp(pt, s.yHeadRotO, s.yHeadRot);
        ps.mulPose(Axis.YP.rotationDegrees(-bodyYaw));
        ps.translate(0, eyeY, half);
        eyes(ps, buf, 0.09F + 0.015F * s.rank(), 0.5F + 0.1F * s.rank());
        ps.popPose();
        super.render(s, yaw, pt, ps, buf, light);
    }

    /** Two glowing eye flares facing out of the front of the head. */
    static void eyes(PoseStack ps, MultiBufferSource buf, float spread, float bright) {
        VertexConsumer vc = buf.getBuffer(RenderType.lightning());
        Matrix4f m = ps.last().pose();
        for (int s = -1; s <= 1; s += 2) {
            float x = s * spread, w = 0.05F, h = 0.03F;
            float r = 0.35F * bright, g = 0.75F * bright, b = 1.0F * bright;
            vc.vertex(m, x - w, -h, 0.01F).color(r, g, b, 1F).endVertex();
            vc.vertex(m, x + w, -h, 0.01F).color(r, g, b, 1F).endVertex();
            vc.vertex(m, x + w, h, 0.01F).color(r, g, b, 1F).endVertex();
            vc.vertex(m, x - w, h, 0.01F).color(r, g, b, 1F).endVertex();
            float W = w * 2.4F, H = h * 2.6F;   // a soft halo round each
            vc.vertex(m, x - W, -H, 0.012F).color(r * 0.3F, g * 0.3F, b * 0.3F, 1F).endVertex();
            vc.vertex(m, x + W, -H, 0.012F).color(r * 0.3F, g * 0.3F, b * 0.3F, 1F).endVertex();
            vc.vertex(m, x + W, H, 0.012F).color(r * 0.3F, g * 0.3F, b * 0.3F, 1F).endVertex();
            vc.vertex(m, x - W, H, 0.012F).color(r * 0.3F, g * 0.3F, b * 0.3F, 1F).endVertex();
        }
    }

    /** Wraps a buffer source so everything drawn through it is darkened to shadow with a blue sheen. */
    static final class Tint implements MultiBufferSource {
        final MultiBufferSource base;
        final float r, g, b;
        Tint(MultiBufferSource base, float r, float g, float b, int rank) {
            this.base = base;
            float k = 1 + 0.12F * rank;   // higher ranks carry a little more blue
            this.r = r; this.g = g * k; this.b = Math.min(1, b * k);
        }
        @Override public VertexConsumer getBuffer(RenderType rt) { return new TintConsumer(base.getBuffer(rt), r, g, b); }
    }

    static final class TintConsumer implements VertexConsumer {
        final VertexConsumer d;
        final float r, g, b;
        TintConsumer(VertexConsumer d, float r, float g, float b) { this.d = d; this.r = r; this.g = g; this.b = b; }
        @Override public VertexConsumer vertex(double x, double y, double z) { d.vertex(x, y, z); return this; }
        @Override public VertexConsumer color(int rr, int gg, int bb, int aa) {
            d.color((int) (rr * r), (int) (gg * g), (int) (bb * b), aa); return this;
        }
        @Override public VertexConsumer uv(float u, float v) { d.uv(u, v); return this; }
        @Override public VertexConsumer overlayCoords(int u, int v) { d.overlayCoords(u, v); return this; }
        @Override public VertexConsumer uv2(int u, int v) { d.uv2(u, v); return this; }
        @Override public VertexConsumer normal(float x, float y, float z) { d.normal(x, y, z); return this; }
        @Override public void endVertex() { d.endVertex(); }
        @Override public void defaultColor(int rr, int gg, int bb, int aa) { d.defaultColor((int) (rr * r), (int) (gg * g), (int) (bb * b), aa); }
        @Override public void unsetDefaultColor() { d.unsetDefaultColor(); }
        @Override
        public void vertex(float x, float y, float z, float rr, float gg, float bb, float aa, float u, float v, int overlay, int light, float nx, float ny, float nz) {
            d.vertex(x, y, z, rr * r, gg * g, bb * b, aa, u, v, overlay, light, nx, ny, nz);
        }
    }
}
