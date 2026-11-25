package com.ironspellgrimoire.client;

import com.ironspellgrimoire.entity.FallingStar;
import com.ironspellgrimoire.registry.AddonItemRegistry;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** A fallen star: its Star Core, large, turning slowly and lit from within; a little dimmer while it's still sealed. */
public class FallingStarRenderer extends EntityRenderer<FallingStar> {
    private ItemStack core;

    public FallingStarRenderer(EntityRendererProvider.Context ctx) { super(ctx); this.shadowRadius = 0.6f; }

    @Override
    public void render(FallingStar e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        if (core == null) core = new ItemStack(AddonItemRegistry.STAR_CORE.get());
        ps.pushPose();
        float t = e.tickCount + pt;
        float bob = e.landed() ? (float) Math.sin(t * 0.08) * 0.12f : 0f;
        ps.translate(0, 0.8 + bob, 0);
        ps.mulPose(Axis.YP.rotationDegrees(t * (e.landed() ? 2f : 12f)));
        float k = e.sealed() ? 2.2f : 2.8f;
        ps.scale(k, k, k);
        Minecraft.getInstance().getItemRenderer().renderStatic(core, ItemDisplayContext.FIXED, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, ps, buf, e.level(), e.getId());
        ps.popPose();
        super.render(e, yaw, pt, ps, buf, light);
    }

    @Override public ResourceLocation getTextureLocation(FallingStar e) { return InventoryMenu.BLOCK_ATLAS; }
}
