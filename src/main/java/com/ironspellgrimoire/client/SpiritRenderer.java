package com.ironspellgrimoire.client;

import com.ironspellgrimoire.entity.BladeSpirit;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;

/** The blade's spirit (entity/BladeSpirit): its wielder's sword, point down, turning slowly, lit by its own light. */
public class SpiritRenderer extends EntityRenderer<BladeSpirit> {
    public SpiritRenderer(EntityRendererProvider.Context c) { super(c); }

    @Override
    public void render(BladeSpirit e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        ps.pushPose();
        float t = e.tickCount + pt;
        ps.translate(0, 0.3 + 0.08 * Mth.sin(t * 0.2F), 0);
        ps.mulPose(Axis.YP.rotationDegrees(t * 6F));
        ps.mulPose(Axis.ZP.rotationDegrees(180F));
        ps.scale(0.42F, 0.42F, 0.42F);
        Minecraft.getInstance().getItemRenderer().renderStatic(e.stack(), ItemDisplayContext.NONE, 0xF000F0, OverlayTexture.NO_OVERLAY, ps, buf, e.level(), e.getId());
        ps.popPose();
        super.render(e, yaw, pt, ps, buf, light);
    }

    @Override public ResourceLocation getTextureLocation(BladeSpirit e) { return TextureAtlas.LOCATION_BLOCKS; }
}
