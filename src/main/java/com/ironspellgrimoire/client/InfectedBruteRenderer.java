package com.ironspellgrimoire.client;

import com.ironspellgrimoire.entity.InfectedBrute;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;

/** Pack Call's infected brutes (entity/InfectedBrute): a flesh-skinned figure, sized and grown by its master's strain
 *  with its Blender pieces (tools/blender/parasite_arms.py) on its head, body and both arms. */
public class InfectedBruteRenderer extends HumanoidMobRenderer<InfectedBrute, HumanoidModel<InfectedBrute>> {
    static final ResourceLocation SKIN = ResourceLocation.fromNamespaceAndPath("iron_spell_grimoire", "textures/entity/infected_brute.png");
    static final ResourceLocation BASE = ParasiteArms.rl("item/parasite_brute_base");
    static final ResourceLocation[] BODY = {null, ParasiteArms.rl("item/parasite_brute_hunter"), ParasiteArms.rl("item/parasite_brute_brute"), ParasiteArms.rl("item/parasite_brute_hive")};
    static final ResourceLocation[] ARM = {null, ParasiteArms.rl("item/parasite_brute_hunter_arm"), ParasiteArms.rl("item/parasite_brute_brute_arm"), ParasiteArms.rl("item/parasite_brute_hive_arm")};

    public InfectedBruteRenderer(EntityRendererProvider.Context c) {
        super(c, new HumanoidModel<>(c.bakeLayer(ModelLayers.ZOMBIE)), 0.5F);
        addLayer(new Pieces(this));
    }

    @Override public ResourceLocation getTextureLocation(InfectedBrute e) { return SKIN; }

    @Override
    protected void scale(InfectedBrute e, PoseStack ps, float pt) { float k = e.size(); ps.scale(k, k, k); }

    /** The strain's pieces: the plain brute's spurs and crest, and the stalker's claws, the brawler's plates or the
     *  spitter's sacs and tubes. */
    static final class Pieces extends RenderLayer<InfectedBrute, HumanoidModel<InfectedBrute>> {
        Pieces(RenderLayerParent<InfectedBrute, HumanoidModel<InfectedBrute>> p) { super(p); }

        @Override
        public void render(PoseStack ps, MultiBufferSource buf, int light, InfectedBrute e, float a, float b, float pt, float age, float yaw, float pitch) {
            HumanoidModel<InfectedBrute> m = getParentModel();
            int k = e.kind();
            ps.pushPose(); m.body.translateAndRotate(ps); ParasiteArms.drawPiece(ps, buf, light, BASE); if (k > 0) ParasiteArms.drawPiece(ps, buf, light, BODY[k]); ps.popPose();
            if (k > 0) {
                ps.pushPose(); m.rightArm.translateAndRotate(ps); ParasiteArms.drawForm(ps, buf, light, ARM[k], false); ps.popPose();
                ps.pushPose(); m.leftArm.translateAndRotate(ps); ParasiteArms.drawForm(ps, buf, light, ARM[k], true); ps.popPose();
            }
        }
    }
}
