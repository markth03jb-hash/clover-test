package com.ironspellgrimoire.client;

import com.ironspellgrimoire.entity.AddonBoss;
import com.ironspellgrimoire.server.BossKit;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * The later bosses on screen (Licht, Revenant, CurseWitch): a player-shaped body in its own skin
 * (textures/entity/&lt;name&gt;.png), posed by the fight - casting, slashing, dashing, raising, roaring, spinning,
 * cackling - and kneeling as it dies. Weapons are the mob's own hands.
 */
public class BossRenderer<T extends AddonBoss> extends HumanoidMobRenderer<T, PlayerModel<T>> {
    private final ResourceLocation tex;
    private final float size;

    public BossRenderer(EntityRendererProvider.Context ctx, String name, boolean slim, float size) {
        super(ctx, new Model<>(ctx.bakeLayer(slim ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER), slim), 0.5f * size);
        this.tex = new ResourceLocation("iron_spell_grimoire", "textures/entity/" + name + ".png");
        this.size = size;
    }

    @Override public ResourceLocation getTextureLocation(T e) { return tex; }

    @Override protected void scale(T e, PoseStack ps, float pt) { ps.scale(size, size, size); }

    @Override
    protected void setupRotations(T e, PoseStack ps, float bob, float yaw, float pt) {
        float saved = e.deathTime;
        e.deathTime = 0;
        super.setupRotations(e, ps, bob, yaw, pt);
        e.deathTime = (int) saved;
        if (saved > 0) {   // down on one knee, then still
            float k = Math.min(1F, (saved + pt) / 40F);
            ps.translate(0, -k * 0.45F, 0);
            ps.mulPose(Axis.XP.rotationDegrees(k * 20F));
        }
    }

    static class Model<T extends AddonBoss> extends PlayerModel<T> {
        Model(ModelPart root, boolean slim) { super(root, slim); }

        @Override
        public void setupAnim(T e, float limbSwing, float limbAmount, float age, float headYaw, float headPitch) {
            super.setupAnim(e, limbSwing, limbAmount, age, headYaw, headPitch);
            float t = (age - e.poseAt()) / 20F;
            float in = Mth.clamp(t * 4F, 0F, 1F), out = Mth.clamp((1.4F - t) * 3F, 0F, 1F), k = in * out;
            int pose = e.deathTime > 0 ? -1 : e.transforming() > 0 ? BossKit.P_ROAR : e.pose();
            switch (pose) {
                case BossKit.P_CAST -> { this.rightArm.xRot = -1.5F * k; this.leftArm.xRot = -0.5F * k; }
                case BossKit.P_SLASH -> {
                    float s = Mth.clamp(t / 0.45F, 0, 1);
                    this.rightArm.xRot = (-2.6F + 2.0F * s) * out; this.rightArm.yRot = (0.7F - 1.5F * s) * out;
                    this.body.yRot = (0.5F - 1.0F * s) * out;
                }
                case BossKit.P_DASH -> { this.rightArm.xRot = 0.9F * k; this.leftArm.xRot = 0.9F * k; this.body.xRot = 0.45F * k; }
                case BossKit.P_RAISE -> { this.rightArm.xRot = -2.9F * k; this.leftArm.xRot = -2.9F * k; this.head.xRot = -0.3F * k; }
                case BossKit.P_ROAR -> { this.rightArm.zRot = 1.2F * in; this.leftArm.zRot = -1.2F * in; this.head.xRot = -0.5F * in; }
                case BossKit.P_SPIN -> { this.body.yRot = t * 14F * out; this.rightArm.zRot = 1.4F * k; this.leftArm.zRot = -1.4F * k; }
                case BossKit.P_LAUGH -> {   // bent back, shaking with it
                    float sh = (float) Math.sin(age * 1.6) * 0.06F;
                    this.head.xRot = -0.6F * k + sh; this.body.xRot = -0.15F * k;
                    this.rightArm.xRot = -0.4F * k; this.rightArm.zRot = 0.6F * k + sh; this.leftArm.zRot = -0.6F * k - sh;
                }
                case -1 -> { this.head.xRot = 0.5F; this.rightArm.xRot = 0.2F; this.leftArm.xRot = 0.2F; }
                default -> { }
            }
            // bosses never bend or twist at the torso: only arms, legs and head move (the torso stays with the arms)
            this.body.xRot = 0; this.body.yRot = 0; this.body.zRot = 0;
            this.rightArm.x = -5; this.rightArm.z = 0; this.leftArm.x = 5; this.leftArm.z = 0;
            this.hat.copyFrom(this.head);
            this.jacket.copyFrom(this.body);
            this.rightSleeve.copyFrom(this.rightArm);
            this.leftSleeve.copyFrom(this.leftArm);
            this.rightPants.copyFrom(this.rightLeg);
            this.leftPants.copyFrom(this.leftLeg);
        }
    }
}
