package com.ironspellgrimoire.client;

import com.ironspellgrimoire.entity.BroomEntity;
import com.ironspellgrimoire.registry.AddonItemRegistry;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** The broom in the world: its 3D item model (models/item/magic_broom.json), lying along the way it faces, bobbing a little. */
public class BroomRenderer extends EntityRenderer<BroomEntity> {
    private ItemStack stack;

    public BroomRenderer(EntityRendererProvider.Context ctx) { super(ctx); this.shadowRadius = 0.3f; }

    @Override
    public void render(BroomEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        if (stack == null) stack = new ItemStack(AddonItemRegistry.MAGIC_BROOM.get());
        int tint = e.book() < 0 ? 0xFFFFFF : GrimoireWheel.themeColors(e.book())[3];
        if (stack.getOrCreateTag().getInt("Tint") != tint) stack.getOrCreateTag().putInt("Tint", tint);
        ps.pushPose();
        float bob = Mth.sin((e.tickCount + pt) * 0.12f) * 0.04f;
        ps.translate(0, 0.35 + bob, 0);
        ps.mulPose(Axis.YP.rotationDegrees(-(Mth.rotLerp(pt, e.yRotO, e.getYRot()) + 90f)));
        Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, ps, buf, e.level(), e.getId());
        ps.popPose();
        super.render(e, yaw, pt, ps, buf, light);
    }

    @Override public ResourceLocation getTextureLocation(BroomEntity e) { return new ResourceLocation("minecraft", "textures/block/oak_planks.png"); }
}
