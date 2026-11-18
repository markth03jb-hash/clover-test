package com.ironspellgrimoire.client;

import com.ironspellgrimoire.entity.TwinWarden;
import com.ironspellgrimoire.item.TwinMaskItem;
import com.ironspellgrimoire.registry.AddonItemRegistry;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

/**
 * The Masked Twins' masks on players (server/TwinsFx.look): the worn twin's mask on the face, the resting twin's
 * hanging at the hip (the fused Eclipse mask alone while it lasts). They belong to the player's model, so they're seen
 * in third person and by others - never in your own first-person view. Also the Twin Wardens' renderer.
 */
public final class TwinsMasks {
    private TwinsMasks() {}

    /** player entity id -> {face form, hip form} (-1 none). */
    static final Map<Integer, int[]> WORN = new HashMap<>();
    public static void look(int id, int face, int hip) { if (face < 0 && hip < 0) WORN.remove(id); else WORN.put(id, new int[]{face, hip}); }

    private static final ItemStack[] STACKS = new ItemStack[9];
    static ItemStack stack(int form) {
        if (STACKS[form] == null) STACKS[form] = TwinMaskItem.of(AddonItemRegistry.TWIN_MASK.get(), form);
        return STACKS[form];
    }

    static void draw(int form, PoseStack ps, MultiBufferSource buf, int light, int seed) {
        Minecraft mc = Minecraft.getInstance();
        mc.getItemRenderer().renderStatic(stack(form), ItemDisplayContext.NONE, light, OverlayTexture.NO_OVERLAY, ps, buf, mc.level, seed);
    }

    public static class Layer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
        public Layer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) { super(parent); }

        @Override
        public void render(PoseStack ps, MultiBufferSource buf, int light, AbstractClientPlayer p, float limbSwing, float limbSwingAmount,
                           float pt, float age, float netHeadYaw, float headPitch) {
            int[] w = WORN.get(p.getId());
            if (w == null || p.isInvisible()) return;
            PlayerModel<AbstractClientPlayer> m = getParentModel();
            if (w[0] >= 0) {   // on the face: its back against the face (out past a helmet), right way up
                ps.pushPose();
                m.head.translateAndRotate(ps);
                boolean helm = !p.getItemBySlot(EquipmentSlot.HEAD).isEmpty();
                ps.translate(0, -4.1F / 16F, -(4.25F + (helm ? 1.1F : 0F)) / 16F);
                ps.mulPose(Axis.XP.rotationDegrees(180));
                float s = helm ? 1.0F : 0.92F;
                ps.scale(s, s, s);
                draw(w[0], ps, buf, light, p.getId());
                ps.popPose();
            }
            if (w[1] >= 0) {   // at the hip: hung on the front of the right thigh, tilted a little
                ps.pushPose();
                m.rightLeg.translateAndRotate(ps);
                ps.translate(0.02F, 3.4F / 16F, -2.35F / 16F);
                ps.mulPose(Axis.XP.rotationDegrees(180));
                ps.mulPose(Axis.ZP.rotationDegrees(10));
                ps.scale(0.5F, 0.5F, 0.5F);
                draw(w[1], ps, buf, light, p.getId() + 1);
                ps.popPose();
            }
        }
    }

    @Mod.EventBusSubscriber(modid = "iron_spell_grimoire", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Register {
        @SubscribeEvent
        public static void addLayers(EntityRenderersEvent.AddLayers e) {
            for (String skin : e.getSkins()) {
                PlayerRenderer r = e.getSkin(skin);
                if (r != null) r.addLayer(new Layer(r));
            }
        }
    }

    /** A Twin Warden: its model (a mask on a floating robe, a halo behind), turned to face its foe, bobbing, lit by its own light. */
    public static class WardenRenderer extends EntityRenderer<TwinWarden> {
        public WardenRenderer(EntityRendererProvider.Context c) { super(c); shadowRadius = 0.6F; }

        @Override
        public void render(TwinWarden e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
            ps.pushPose();
            float t = e.tickCount + pt;
            ps.translate(0, 1.2 + 0.08 * Mth.sin(t * 0.12F), 0);
            ps.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(pt, e.yBodyRotO, e.yBodyRot)));
            float hurt = e.hurtTime > 0 ? 1.04F : 1F;
            ps.scale(1.05F * hurt, 1.05F * hurt, 1.05F * hurt);
            draw(e.dusk() ? TwinMaskItem.DUSK : TwinMaskItem.DAWN, ps, buf, 0xF000F0, e.getId());
            ps.popPose();
            super.render(e, yaw, pt, ps, buf, light);
        }

        @Override public ResourceLocation getTextureLocation(TwinWarden e) { return TextureAtlas.LOCATION_BLOCKS; }
    }
}
