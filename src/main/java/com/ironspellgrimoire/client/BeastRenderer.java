package com.ironspellgrimoire.client;

import com.ironspellgrimoire.entity.BeastOfDespair;
import com.ironspellgrimoire.server.BeastAI;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * BeastOfDespair on screen: a hulking player-shaped body (textures/entity/beast_of_despair.png, an original beast-man skin) drawn a third
 * bigger, posed by the fight - raking, charging on all fours, roaring, slamming, guarding behind his armour, throwing
 * claws, opening his arms like jaws, gathering, and broken on one knee. His claws, armour, third eye and mane are
 * {@link BeastLayer}.
 */
public class BeastRenderer extends HumanoidMobRenderer<BeastOfDespair, PlayerModel<BeastOfDespair>> {
    private static final ResourceLocation TEX = new ResourceLocation("iron_spell_grimoire", "textures/entity/beast_of_despair.png");

    public BeastRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new Model(ctx.bakeLayer(ModelLayers.PLAYER)), 0.8f);
        this.addLayer(new BeastLayer(this));
    }

    @Override public ResourceLocation getTextureLocation(BeastOfDespair e) { return TEX; }

    @Override
    protected void setupRotations(BeastOfDespair e, PoseStack ps, float bob, float yaw, float pt) {
        float saved = e.deathTime;
        e.deathTime = 0;
        super.setupRotations(e, ps, bob, yaw, pt);
        e.deathTime = (int) saved;
        if (saved > 0) {   // he sinks to his knees, then forward
            float k = Math.min(1F, (saved + pt) / 50F);
            ps.translate(0, -k * 0.5F, 0);
            ps.mulPose(com.mojang.math.Axis.XP.rotationDegrees(k * 35F));
        }
    }

    @Override
    protected void scale(BeastOfDespair e, PoseStack ps, float pt) {
        float s = e.phase() == 3 ? 1.42F : 1.33F;
        ps.scale(s, s, s);
    }

    static class Model extends PlayerModel<BeastOfDespair> {
        Model(ModelPart root) { super(root, false); }

        @Override
        public void setupAnim(BeastOfDespair e, float limbSwing, float limbAmount, float age, float headYaw, float headPitch) {
            super.setupAnim(e, limbSwing, limbAmount, age, headYaw, headPitch);
            float t = (age - e.poseAt()) / 20F;
            int pose = e.transforming() > 0 || e.deathTime > 0 ? (e.deathTime > 0 ? BeastAI.P_BROKEN : BeastAI.P_ROAR) : e.charging() > 0 ? BeastAI.P_GATHER : e.pose();
            float in = Mth.clamp(t * 4F, 0F, 1F);
            this.body.xRot = 0.12F;   // always hunched a little, a beast's stance
            switch (pose) {
                case BeastAI.P_CLAW -> {
                    float s = (float) Math.sin(t * 10.0);
                    arms(-1.7F + 0.3F * s, 0.7F * s, -0.3F, -1.7F - 0.3F * s, 0.7F * s, 0.3F);
                    this.body.yRot = 0.35F * s;
                    this.body.xRot = 0.3F;
                }
                case BeastAI.P_CHARGE -> {   // low, arms swept back, head forward
                    arms(0.9F * in, 0F, 0.2F, 0.9F * in, 0F, -0.2F);
                    this.body.xRot = 0.55F * in;
                    this.head.xRot = -0.4F * in;
                }
                case BeastAI.P_ROAR -> {
                    float shake = (float) Math.sin(age * 2.4) * 0.05F;
                    arms(-0.6F + shake, 0F, 1.1F * in, -0.6F - shake, 0F, -1.1F * in);
                    this.head.xRot = -0.6F * in;
                    this.body.xRot = -0.1F;
                }
                case BeastAI.P_SLAM -> {
                    float up = t < 0.8F ? Mth.clamp(t / 0.5F, 0, 1) : Mth.clamp(1F - (t - 0.8F) / 0.2F, 0, 1);
                    arms(-3.0F * up - 0.5F * (1 - up), 0F, -0.3F, -3.0F * up - 0.5F * (1 - up), 0F, 0.3F);
                    this.body.xRot = 0.1F + (1 - up) * 0.5F * Mth.clamp((t - 0.8F) * 5F, 0, 1);
                }
                case BeastAI.P_GUARD -> {   // forearms crossed before the face
                    arms(-1.6F * in, -0.7F * in, 0F, -1.6F * in, 0.7F * in, 0F);
                    this.body.xRot = 0.25F;
                }
                case BeastAI.P_THROW -> {
                    float back = t < 0.7F ? Mth.clamp(t / 0.5F, 0, 1) : 0F;
                    arms(t < 0.7F ? -2.4F * back : -1.4F, t < 0.7F ? 0.8F * back : -0.6F, 0F, t < 0.7F ? -2.4F * back : -1.4F, t < 0.7F ? -0.8F * back : 0.6F, 0F);
                    this.body.yRot = t < 0.7F ? 0F : 0F;
                }
                case BeastAI.P_BITE -> {   // arms wide like open jaws, then clapped shut
                    float open = t < 0.85F ? Mth.clamp(t / 0.5F, 0, 1) : Mth.clamp(1F - (t - 0.85F) / 0.1F, 0, 1);
                    arms(-1.5F - 0.6F * open, 0F, -0.2F, -1.5F + 0.6F * open, 0F, 0.2F);
                }
                case BeastAI.P_GATHER -> {   // crouched, fists drawn in, shaking with it
                    float shake = (float) Math.sin(age * 3.0) * 0.06F;
                    arms(-0.9F + shake, 0.4F, 0.4F, -0.9F - shake, -0.4F, -0.4F);
                    this.body.xRot = 0.45F;
                    this.head.xRot = -0.3F;
                }
                case BeastAI.P_BROKEN -> {   // on one knee, arms hanging
                    arms(0.2F, 0F, 0.1F, 0.4F, 0F, -0.1F);
                    this.body.xRot = 0.5F;
                    this.rightLeg.xRot = -1.4F;
                    this.leftLeg.xRot = 0.2F;
                    this.head.xRot = 0.4F;
                }
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

        private void arms(float rx, float ry, float rz, float lx, float ly, float lz) {
            this.rightArm.xRot = rx; this.rightArm.yRot = ry; this.rightArm.zRot = rz;
            this.leftArm.xRot = lx; this.leftArm.yRot = ly; this.leftArm.zRot = lz;
        }
    }
}
