package com.ironspellgrimoire.client;

import com.ironspellgrimoire.entity.Zenon;
import com.ironspellgrimoire.server.ZenonAI;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Zenon on screen: a player-shaped body wearing the skin you gave (textures/entity/zenon.png), posed by the
 * fight (throw, claw, slam, sweep, roar, raise...), with his bone armour and then his devil form drawn over him
 * ({@link ZenonLayer}). He doesn't topple when he dies: he sinks and crumbles.
 */
public class ZenonRenderer extends HumanoidMobRenderer<Zenon, PlayerModel<Zenon>> {
    private static final ResourceLocation TEX = new ResourceLocation("iron_spell_grimoire", "textures/entity/zenon.png");

    public ZenonRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new Model(ctx.bakeLayer(ModelLayers.PLAYER)), 0.5f);
        this.addLayer(new ZenonLayer(this));
    }

    @Override public ResourceLocation getTextureLocation(Zenon e) { return TEX; }

    @Override
    protected void setupRotations(Zenon e, PoseStack ps, float bob, float yaw, float pt) {
        float saved = e.deathTime;
        e.deathTime = 0;   // no tipping over...
        super.setupRotations(e, ps, bob, yaw, pt);
        e.deathTime = (int) saved;
        if (saved > 0) {   // ...he slumps and sinks instead
            float k = Math.min(1F, (saved + pt) / 60F);
            ps.translate(0, -k * 0.9F, 0);
            ps.mulPose(com.mojang.math.Axis.XP.rotationDegrees(k * 25F));
        }
    }

    @Override
    protected void scale(Zenon e, PoseStack ps, float pt) {
        float s = e.phase() == 3 ? 1.12F : 1.0F;   // the devil union stands a little taller
        ps.scale(s, s, s);
    }

    /** The player model, posed by what he is doing. */
    static class Model extends PlayerModel<Zenon> {
        Model(ModelPart root) { super(root, false); }

        @Override
        public void setupAnim(Zenon e, float limbSwing, float limbAmount, float age, float headYaw, float headPitch) {
            super.setupAnim(e, limbSwing, limbAmount, age, headYaw, headPitch);
            float t = (age - e.poseAt()) / 20F;   // seconds into the pose
            int pose = e.transforming() > 0 ? ZenonAI.P_ROAR : e.pose();
            if (e.deathTime > 0) pose = ZenonAI.P_ROAR;
            float in = Mth.clamp(t * 4F, 0F, 1F);
            switch (pose) {
                case ZenonAI.P_CAST -> {   // both hands out, palms toward you
                    arms(-1.4F * in, 0.2F, -0.3F * in, -1.4F * in, -0.2F, 0.3F * in);
                }
                case ZenonAI.P_THROW -> {   // right arm drawn back, then whipped through
                    float back = t < 0.75F ? Mth.clamp(t / 0.5F, 0, 1) : 0F;
                    float rx = t < 0.75F ? -2.6F * back : -1.2F;
                    arms(rx, -0.2F, 0F, -0.6F * in, 0.3F, 0.2F);
                    this.body.yRot = t < 0.75F ? 0.4F * back : -0.3F;
                }
                case ZenonAI.P_CLAW -> {   // arms raking across, one after the other
                    float s = (float) Math.sin(t * 9.0);
                    arms(-1.5F, 0.6F * s, -0.4F, -1.5F, 0.6F * s, 0.4F);
                    this.body.yRot = 0.3F * s;
                }
                case ZenonAI.P_SLAM -> {   // both arms up, then brought down to the ground
                    float up = t < 0.6F ? Mth.clamp(t / 0.4F, 0, 1) : Mth.clamp(1F - (t - 0.6F) / 0.15F, 0, 1);
                    arms(-3.0F * up - 0.4F * (1 - up), 0F, -0.2F, -3.0F * up - 0.4F * (1 - up), 0F, 0.2F);
                    this.body.xRot = (1 - up) * 0.4F * Mth.clamp((t - 0.6F) * 6F, 0, 1);
                }
                case ZenonAI.P_SWEEP -> {   // a wide horizontal sweep
                    float s = Mth.clamp(t / 0.6F, 0, 1);
                    arms(-1.6F, 1.2F - 2.4F * s, -0.2F, -0.4F, 0.2F, 0.4F);
                    this.body.yRot = 0.6F - 1.2F * s;
                }
                case ZenonAI.P_ROAR -> {   // arms flung wide, head thrown back, shaking
                    float shake = (float) Math.sin(age * 2.2) * 0.04F;
                    arms(-0.3F + shake, 0F, 1.3F * in, -0.3F - shake, 0F, -1.3F * in);
                    this.head.xRot = -0.5F * in;
                }
                case ZenonAI.P_RAISE -> {   // both hands raised, calling something up
                    float b = (float) Math.sin(age * 0.3) * 0.05F;
                    arms(-2.9F * in + b, 0F, -0.4F, -2.9F * in - b, 0F, 0.4F);
                    this.head.xRot = -0.35F * in;
                }
                default -> { }
            }
            this.hat.copyFrom(this.head);
            this.jacket.copyFrom(this.body);
            this.rightSleeve.copyFrom(this.rightArm);
            this.leftSleeve.copyFrom(this.leftArm);
        }

        private void arms(float rx, float ry, float rz, float lx, float ly, float lz) {
            this.rightArm.xRot = rx; this.rightArm.yRot = ry; this.rightArm.zRot = rz;
            this.leftArm.xRot = lx; this.leftArm.yRot = ly; this.leftArm.zRot = lz;
        }
    }
}
