package com.ironspellgrimoire.client;

import com.ironspellgrimoire.entity.Rhya;
import com.ironspellgrimoire.server.RhyaAI;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Rhya on screen: a player-shaped body wearing the skin you gave (textures/entity/rhya.png), posed by the fight. The
 * grimoire he copied floats open at his shoulder,
 * turning slowly; in phase 3 he holds the copied anti-magic sword (the mob's own main hand).
 */
public class RhyaRenderer extends HumanoidMobRenderer<Rhya, PlayerModel<Rhya>> {
    private static final ResourceLocation TEX = new ResourceLocation("iron_spell_grimoire", "textures/entity/rhya.png");

    public RhyaRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new Model(ctx.bakeLayer(ModelLayers.PLAYER)), 0.5f);
        this.addLayer(new BookLayer(this));
    }

    @Override public ResourceLocation getTextureLocation(Rhya e) { return TEX; }

    @Override
    protected void setupRotations(Rhya e, PoseStack ps, float bob, float yaw, float pt) {
        float saved = e.deathTime;
        e.deathTime = 0;
        super.setupRotations(e, ps, bob, yaw, pt);
        e.deathTime = (int) saved;
        if (saved > 0) {   // he sits down, as if it were all too much bother
            float k = Math.min(1F, (saved + pt) / 40F);
            ps.translate(0, -k * 0.55F, 0);
            ps.mulPose(Axis.XP.rotationDegrees(-k * 12F));
        }
    }

    @Override
    protected void scale(Rhya e, PoseStack ps, float pt) { ps.scale(1.05F, 1.05F, 1.05F); }

    static class Model extends PlayerModel<Rhya> {
        Model(ModelPart root) { super(root, false); }

        @Override
        public void setupAnim(Rhya e, float limbSwing, float limbAmount, float age, float headYaw, float headPitch) {
            super.setupAnim(e, limbSwing, limbAmount, age, headYaw, headPitch);
            float t = (age - e.poseAt()) / 20F;
            int pose = e.deathTime > 0 ? RhyaAI.P_LAZY : e.transforming() > 0 ? RhyaAI.P_ROAR : e.pose();
            float in = Mth.clamp(t * 4F, 0F, 1F);
            switch (pose) {
                case RhyaAI.P_CAST -> arms(-1.5F * in, -0.2F, 0F, -0.5F * in, 0.2F, 0.3F);   // one hand out, lazily
                case RhyaAI.P_DASH -> {
                    arms(0.8F * in, 0F, 0.2F, -1.5F * in, 0F, 0F);   // the left hand reaching for your book
                    this.body.xRot = 0.4F * in;
                }
                case RhyaAI.P_SLASH -> {
                    float s = Mth.clamp(t / 0.5F, 0, 1);
                    arms(-2.4F + 1.8F * s, 0.6F - 1.4F * s, 0F, -0.4F, 0F, 0.2F);
                    this.body.yRot = 0.5F - 1.0F * s;
                }
                case RhyaAI.P_RAISE -> arms(-2.9F * in, 0F, -0.2F, -0.3F, 0F, 0.2F);
                case RhyaAI.P_SPIN -> {
                    this.body.yRot = (float) (t * 18.0);
                    arms(-1.6F, 0F, 1.4F * in, -1.6F, 0F, -1.4F * in);
                }
                case RhyaAI.P_ROAR -> {
                    arms(-0.3F, 0F, 1.2F * in, -0.3F, 0F, -1.2F * in);
                    this.head.xRot = -0.5F * in;
                }
                case RhyaAI.P_LAZY -> {   // slumped, arms hanging
                    arms(0.1F, 0F, 0.15F, 0.1F, 0F, -0.15F);
                    this.head.xRot = 0.5F;
                    this.rightLeg.xRot = -1.4F;
                    this.leftLeg.xRot = -1.4F;
                }
                default -> this.leftArm.xRot += 0.1F;   // a slouch
            }
            this.hat.copyFrom(this.head);
            this.jacket.copyFrom(this.body);
            this.rightSleeve.copyFrom(this.rightArm);
            this.leftSleeve.copyFrom(this.leftArm);
            this.rightPants.copyFrom(this.rightLeg);
            this.leftPants.copyFrom(this.leftLeg);
        }

        private void arms(float rx, float ry, float rz, float lx, float ly, float lz) {
            this.rightArm.xRot = rx; this.rightArm.yRot = ry; this.rightArm.zRot = rz;
            this.leftArm.xRot = lx; this.leftArm.yRot = ly; this.leftArm.zRot = lz;
        }
    }

    /** The copied grimoire, open and turning beside his left shoulder. */
    static class BookLayer extends RenderLayer<Rhya, PlayerModel<Rhya>> {
        BookLayer(RenderLayerParent<Rhya, PlayerModel<Rhya>> p) { super(p); }

        @Override
        public void render(PoseStack ps, MultiBufferSource buf, int light, Rhya e, float limbSwing, float limbAmount, float pt, float age,
                           float headYaw, float headPitch) {
            ItemStack book = e.book();
            if (book.isEmpty() || e.isInvisible() || e.deathTime > 20) return;
            ps.pushPose();
            float bob = (float) Math.sin(age * 0.1) * 0.06F;
            ps.translate(0.62F, -0.35F + bob, -0.1F);   // model space: +x is his left, +y down
            ps.mulPose(Axis.YP.rotationDegrees(age * 2.0F));
            ps.mulPose(Axis.ZP.rotationDegrees(180F));
            ps.scale(0.45F, 0.45F, 0.45F);
            Minecraft.getInstance().getItemRenderer().renderStatic(book, ItemDisplayContext.FIXED, 15728880, OverlayTexture.NO_OVERLAY, ps, buf, e.level(), e.getId());
            ps.popPose();
        }
    }
}
