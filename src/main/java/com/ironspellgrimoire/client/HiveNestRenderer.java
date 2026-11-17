package com.ironspellgrimoire.client;

import com.ironspellgrimoire.entity.HiveNest;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** The Hive Nest (entity/HiveNest): its Blender model, breathing slowly, swelling a little as it fills. */
public class HiveNestRenderer extends EntityRenderer<HiveNest> {
    public static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath("iron_spell_grimoire", "item/parasite_hive_nest");

    public HiveNestRenderer(EntityRendererProvider.Context c) { super(c); this.shadowRadius = 1.0F; }

    @Override
    public void render(HiveNest e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        ps.pushPose();
        float t = e.tickCount + pt;
        float breathe = 1F + 0.03F * Mth.sin(t * 0.08F);
        float fill = 0.9F + 0.15F * e.stored() / HiveNest.MAX;
        ps.mulPose(Axis.YP.rotationDegrees(e.getId() * 37 % 360));
        ps.scale(fill, fill * breathe, fill);
        ParasiteArms.drawPiece(ps, buf, light, MODEL);
        ps.popPose();
        super.render(e, yaw, pt, ps, buf, light);
    }

    @SuppressWarnings("deprecation")
    @Override public ResourceLocation getTextureLocation(HiveNest e) { return TextureAtlas.LOCATION_BLOCKS; }
}
