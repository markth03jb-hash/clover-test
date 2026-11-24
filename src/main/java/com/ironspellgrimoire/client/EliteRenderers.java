package com.ironspellgrimoire.client;

import com.ironspellgrimoire.entity.CursedChest;
import com.ironspellgrimoire.entity.EliteRift;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** The Rift-Born elite's rift and the Cursed Loot chest (their models: EliteClient). */
public final class EliteRenderers {
    private EliteRenderers() {}

    /** A ring of broken stone around a tear; it spins, and frays (tilts and sinks) with each hit. */
    public static class RiftRenderer extends EntityRenderer<EliteRift> {
        public RiftRenderer(EntityRendererProvider.Context c) { super(c); this.shadowRadius = 0.4F; }
        @Override
        public void render(EliteRift e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
            float t = e.tickCount + pt, grow = Mth.clamp(t / 8F, 0F, 1F);
            ps.pushPose();
            ps.translate(0, 0.15F + 0.05F * Mth.sin(t * 0.2F), 0);
            ps.mulPose(Axis.YP.rotationDegrees(t * 2.5F));
            ps.mulPose(Axis.ZP.rotationDegrees(e.hits() * 7F * Mth.sin(t * 0.6F)));
            float s = grow * (1F - 0.08F * e.hits());
            ps.scale(s, s, s);
            ParasiteArms.drawPiece(ps, buf, LightTexture.FULL_BRIGHT, EliteClient.RIFT);
            ps.popPose();
            super.render(e, yaw, pt, ps, buf, light);
        }
        @SuppressWarnings("deprecation")
        @Override public ResourceLocation getTextureLocation(EliteRift e) { return TextureAtlas.LOCATION_BLOCKS; }
    }

    /** The chest: its lid creaks while it's dangerous, swings open when opened, and snaps if it bit. */
    public static class ChestRenderer extends EntityRenderer<CursedChest> {
        public ChestRenderer(EntityRendererProvider.Context c) { super(c); this.shadowRadius = 0.45F; }
        @Override
        public void render(CursedChest e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
            float t = e.tickCount + pt;
            ps.pushPose();
            ps.mulPose(Axis.YP.rotationDegrees(-e.getYRot()));
            float lid;
            if (!e.opened()) lid = t < 60 ? 6F + 6F * Math.abs(Mth.sin(t * 0.5F)) : 0F;   // chattering while it's still cursed
            else {
                float k = Mth.clamp((e.sinceOpen() + pt) / 6F, 0F, 1F);
                lid = e.bit() ? (k < 0.5F ? 70F * k * 2 : 70F * (1 - k) * 2) : 100F * k;
            }
            ParasiteArms.drawPiece(ps, buf, light, EliteClient.CHEST);
            ps.translate(0, 0.62F, -0.36F);   // the hinge at the back
            ps.mulPose(Axis.XP.rotationDegrees(-lid));
            ps.translate(0, -0.62F, 0.36F);
            ParasiteArms.drawPiece(ps, buf, light, EliteClient.CHEST_LID);
            ps.popPose();
            super.render(e, yaw, pt, ps, buf, light);
        }
        @SuppressWarnings("deprecation")
        @Override public ResourceLocation getTextureLocation(CursedChest e) { return TextureAtlas.LOCATION_BLOCKS; }
    }
}
