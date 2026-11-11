package com.ironspellgrimoire.client;

import com.ironspellgrimoire.entity.Rival;
import com.ironspellgrimoire.server.RivalAI;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.UUID;
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
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Your rival: a player-shaped mage wearing one of the game's default skins (picked from their seed, so they always look
 * the same), their grimoire floating open at their shoulder, posed by what they are casting.
 */
public class RivalRenderer extends HumanoidMobRenderer<Rival, PlayerModel<Rival>> {
    public RivalRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new Model(ctx.bakeLayer(ModelLayers.PLAYER)), 0.5f);
        this.addLayer(new BookLayer(this));
    }

    @Override
    public ResourceLocation getTextureLocation(Rival e) {
        UUID s = e.seed();
        for (int k = 0; k < 32; k++) {   // a wide-armed default skin (the model is the wide one)
            UUID u = new UUID(s.getMostSignificantBits(), s.getLeastSignificantBits() + k);
            if ("default".equals(DefaultPlayerSkin.getSkinModelName(u))) return DefaultPlayerSkin.getDefaultSkin(u);
        }
        return DefaultPlayerSkin.getDefaultSkin();
    }

    static class Model extends PlayerModel<Rival> {
        Model(ModelPart root) { super(root, false); }

        @Override
        public void setupAnim(Rival e, float limbSwing, float limbAmount, float age, float headYaw, float headPitch) {
            super.setupAnim(e, limbSwing, limbAmount, age, headYaw, headPitch);
            float t = (age - e.poseAt()) / 20F;
            float in = Mth.clamp(t * 4F, 0F, 1F), out = Mth.clamp((1.2F - t) * 3F, 0F, 1F), k = in * out;
            switch (e.pose()) {
                case RivalAI.P_CAST -> { this.rightArm.xRot = -1.5F * k; this.leftArm.xRot = -0.6F * k; }
                case RivalAI.P_SLASH -> { float s = Mth.clamp(t / 0.5F, 0, 1); this.rightArm.xRot = (-2.4F + 1.8F * s) * out; this.rightArm.yRot = (0.6F - 1.4F * s) * out; }
                case RivalAI.P_DASH -> { this.rightArm.xRot = 0.8F * k; this.leftArm.xRot = 0.8F * k; this.body.xRot = 0.4F * k; }
                case RivalAI.P_RAISE -> { this.rightArm.xRot = -2.9F * k; this.leftArm.xRot = -2.9F * k; }
                default -> { }
            }
            this.hat.copyFrom(this.head);
            this.jacket.copyFrom(this.body);
            this.rightSleeve.copyFrom(this.rightArm);
            this.leftSleeve.copyFrom(this.leftArm);
            this.rightPants.copyFrom(this.rightLeg);
            this.leftPants.copyFrom(this.leftLeg);
        }
    }

    static class BookLayer extends RenderLayer<Rival, PlayerModel<Rival>> {
        BookLayer(RenderLayerParent<Rival, PlayerModel<Rival>> p) { super(p); }

        @Override
        public void render(PoseStack ps, MultiBufferSource buf, int light, Rival e, float limbSwing, float limbAmount, float pt, float age,
                           float headYaw, float headPitch) {
            ItemStack book = e.book();
            if (book.isEmpty() || e.isInvisible()) return;
            ps.pushPose();
            ps.translate(0.62F, -0.3F + (float) Math.sin(age * 0.1) * 0.05F, -0.1F);
            ps.mulPose(Axis.YP.rotationDegrees(age * 2.0F));
            ps.mulPose(Axis.ZP.rotationDegrees(180F));
            ps.scale(0.42F, 0.42F, 0.42F);
            Minecraft.getInstance().getItemRenderer().renderStatic(book, ItemDisplayContext.FIXED, 15728880, OverlayTexture.NO_OVERLAY, ps, buf, e.level(), e.getId());
            ps.popPose();
        }
    }
}
