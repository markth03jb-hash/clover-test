package com.ironspellgrimoire.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.util.Mth;

/**
 * The animated models of the two transformations, attached to the player's own model parts:
 *   Devil Union  - Asta's single black wing on the left of his back, built like a real wing: an arm (upper arm,
 *                  elbow, forearm, wrist with a hooked thumb claw) and four finger bones fanning to the point, with a
 *                  thin membrane between them whose trailing edge is scalloped and torn. It unfurls,
 *                  beats slowly at rest, sweeps back while running, flaps hard in the air, spreads to glide when
 *                  falling and folds in when crouching (bending along its length as it moves)
 *   Beelzebub    - skeletal wings (open, idle, flap in the air) and horns that grow from the temples
 *   Pilot        - the Mech Grimoire's war machine round its pilot (MechModels)
 * The crown, mantle and burst of the Devil Union stay as effects (FxRenderer.devilForm).
 */
public class GeoFormLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    public GeoFormLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) { super(parent); }

    /** How the body is moving, as one of the wing states. */
    static String motion(AbstractClientPlayer p, boolean canGlide) {
        double vy = p.getY() - p.yo;
        double speed = Math.sqrt((p.getX() - p.xo) * (p.getX() - p.xo) + (p.getZ() - p.zo) * (p.getZ() - p.zo));
        if (!p.onGround()) return canGlide && vy < -0.08 ? "glide" : "fly";
        if (p.isCrouching()) return canGlide ? "fold" : "idle";
        return speed > 0.05 && canGlide ? "run" : "idle";
    }

    @Override
    public void render(PoseStack ps, MultiBufferSource buf, int light, AbstractClientPlayer p, float limbSwing, float limbSwingAmount,
                       float pt, float age, float netHeadYaw, float headPitch) {
        if (p.isInvisible()) return;
        double now = p.level().getGameTime() + pt;
        double[] dv = FxRenderer.devilFormTimes(p.getId());
        if (dv != null && now <= dv[1] + 20.0) {
            float fade = (float) Mth.clamp((dv[1] + 20.0 - now) / 20.0, 0.0, 1.0);   // shrinks away as the union ends
            ps.pushPose();
            getParentModel().body.translateAndRotate(ps);
            ps.scale(fade, fade, fade);
            GeoForms.draw("devil_wings", p.getId(), dv[0], "open", motion(p, true), null, ps, buf, LightTexture.FULL_BRIGHT);
            ps.popPose();
        }
        double[] mech = MechFxClient.pilot(p.getId());
        if (mech != null) {   // Pilot: the hard-light machine standing round you (only the parts you've built)
            float out = (float) Mth.clamp((mech[1] + 8.0 - now) / 8.0, 0.0, 1.0);   // it shrinks away when the mode ends
            if (out > 0) {
                double speed = Math.sqrt((p.getX() - p.xo) * (p.getX() - p.xo) + (p.getZ() - p.zo) * (p.getZ() - p.zo));
                String loop = p.swinging ? "punch" : speed > 0.04 ? "walk" : "idle";
                ps.pushPose();
                MechModels.drawTitan(ps, buf, p.getId(), (int) mech[2], mech[0], loop, out, light);
                ps.popPose();
            }
        }
        double[] bf = FxRenderer.boneFormTimes(p.getId());
        if (bf != null && now <= bf[1]) {
            ps.pushPose();
            getParentModel().body.translateAndRotate(ps);
            GeoForms.draw("beelzebub_wings", p.getId(), bf[0], "open", p.onGround() ? "idle" : "fly", null, ps, buf, LightTexture.FULL_BRIGHT);
            ps.popPose();
            ps.pushPose();
            getParentModel().head.translateAndRotate(ps);
            GeoForms.draw("beelzebub_horns", p.getId(), bf[0], "grow", "idle", null, ps, buf, LightTexture.FULL_BRIGHT);
            ps.popPose();
        }
    }
}
