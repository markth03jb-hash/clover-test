package com.ironspellgrimoire.client;

import com.ironspellgrimoire.entity.ThrownHungerBlade;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;

/** The thrown Hunger Blade (entity/ThrownHungerBlade): its own sword model, spinning flat as it flies. */
public class ThrownBladeRenderer extends EntityRenderer<ThrownHungerBlade> {
    public ThrownBladeRenderer(EntityRendererProvider.Context c) { super(c); }

    @Override
    public void render(ThrownHungerBlade e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        ps.pushPose();
        ps.translate(0, 0.4, 0);
        ps.mulPose(Axis.YP.rotationDegrees(-e.getYRot()));
        ps.mulPose(Axis.XP.rotationDegrees(90));
        ps.mulPose(Axis.ZP.rotationDegrees((e.tickCount + pt) * 40F));
        ps.scale(1.6F, 1.6F, 1.6F);
        Minecraft.getInstance().getItemRenderer().renderStatic(e.stack(), ItemDisplayContext.FIXED, 0xF000F0, OverlayTexture.NO_OVERLAY, ps, buf, e.level(), e.getId());
        ps.popPose();
        super.render(e, yaw, pt, ps, buf, light);
    }

    @Override public ResourceLocation getTextureLocation(ThrownHungerBlade e) { return TextureAtlas.LOCATION_BLOCKS; }
}
