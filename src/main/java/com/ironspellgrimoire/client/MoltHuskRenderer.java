package com.ironspellgrimoire.client;

import com.ironspellgrimoire.entity.MoltHusk;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/** The molted husk (entity/MoltHusk): its owner's shape and skin, darkened to dried red, with the Chitin Hide's plates
 *  half peeled off it; it fades out as it crumbles. */
public class MoltHuskRenderer extends LivingEntityRenderer<MoltHusk, PlayerModel<MoltHusk>> {
    public MoltHuskRenderer(EntityRendererProvider.Context c) { super(c, new PlayerModel<>(c.bakeLayer(ModelLayers.PLAYER), false), 0.4F); }

    @Override
    public ResourceLocation getTextureLocation(MoltHusk e) {
        var mc = Minecraft.getInstance();
        Player owner = e.owner() == null || mc.level == null ? null : mc.level.getPlayerByUUID(e.owner());
        if (owner instanceof net.minecraft.client.player.AbstractClientPlayer ac) return ac.getSkinTextureLocation();
        return DefaultPlayerSkin.getDefaultSkin(e.owner() == null ? e.getUUID() : e.owner());
    }

    @Override
    public void render(MoltHusk e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        ps.pushPose();
        // drawn by hand: darkened red and fading, never the full bright skin
        float fade = Mth.clamp(e.life / 20F, 0.25F, 1F);
        ps.scale(-1F, -1F, 1F);
        ps.translate(0, -1.501F, 0);
        ps.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180F - Mth.rotLerp(pt, e.yBodyRotO, e.yBodyRot)));
        getModel().young = false;
        getModel().setupAnim(e, 0, 0, e.tickCount + pt, 0, 0);
        getModel().renderToBuffer(ps, buf.getBuffer(RenderType.entityTranslucent(getTextureLocation(e))), light, OverlayTexture.NO_OVERLAY, 0.55F, 0.12F, 0.12F, 0.9F * fade);
        getModel().renderToBuffer(ps, buf.getBuffer(RenderType.armorCutoutNoCull(ParasiteArms.HIDE_TEX)), light, OverlayTexture.NO_OVERLAY, 0.7F, 0.3F, 0.3F, 1F);
        ps.popPose();
    }
}
