package com.ironspellgrimoire.client;

import com.ironspellgrimoire.network.ParasitePackets;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RenderArmEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Parasite's forms, drawn on the player's own arms at full size (the Blender models of tools/blender/parasite_arms.py,
 * loaded by Forge's OBJ loader as bare models). Each is built in the arm's own space, so it is one piece with the arm
 * and follows every frame of it - vanilla's own punch swing, in first person (RenderArmEvent: the bare arm vanilla
 * draws when the hand is empty) and in third person (a layer on the player). The Bulwark goes on the other arm, and
 * the Chitin Hide is a plated skin over the body.
 */
@Mod.EventBusSubscriber(modid = "iron_spell_grimoire", value = Dist.CLIENT)
public class ParasiteArms extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    static final String[] MODELS = {"", "reaper_edge", "rending_talons", "spine_launcher", "titan_fist", "bone_lance"};
    static final ResourceLocation BULWARK = rl("item/parasite_bulwark");
    static final ResourceLocation HIDE_TEX = ResourceLocation.fromNamespaceAndPath("iron_spell_grimoire", "textures/models/armor/chitin_hide.png");
    static ResourceLocation rl(String p) { return ResourceLocation.fromNamespaceAndPath("iron_spell_grimoire", p); }
    static ResourceLocation formModel(int f) { return rl("item/parasite_" + MODELS[f]); }

    /** player id -> {form, bulwark, hide, frenzied} */
    private static final Map<Integer, int[]> LOOKS = new HashMap<>();

    public static void look(ParasitePackets.Look p) {
        if (p.form == 0 && !p.bulwark && !p.hide) LOOKS.remove(p.id);
        else LOOKS.put(p.id, new int[]{p.form, p.bulwark ? 1 : 0, p.hide ? 1 : 0, p.frenzied ? 1 : 0});
    }

    static int[] lookOf(int id) { return LOOKS.get(id); }

    /** For the screenshots: show a look on a player without the server. */
    public static void testLook(int id, int form, boolean bulwark, boolean hide) { look(new ParasitePackets.Look(id, form, bulwark, hide, false)); }

    private final HumanoidModel<AbstractClientPlayer> hideModel;

    public ParasiteArms(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent, HumanoidModel<AbstractClientPlayer> hideModel) {
        super(parent);
        this.hideModel = hideModel;
    }

    // ------------------------------------------------------------------ the models
    private static final RandomSource RAND = RandomSource.create();

    /** Draw one of the forms in an arm's space (the arm's pivot at the shoulder, +y down it): left = mirrored onto
     *  the left arm. The model's origin is the hand end of the forearm's middle. */
    /** First person only: how the forms sit on the bare arm vanilla draws there (it is held close to the eye, so a
     *  form at full size fills the view): tilted about the wrist, and smaller. */
    public static float fpTilt = -40F, fpScale = 0.75F, bwScale = 0.55F;

    /** In first person, turn and shrink round the hand (where the forearm ends) before drawing. */
    static void firstPerson(PoseStack ps, float tilt, float scale) {
        ps.translate(0F, 10F / 16F, 0F);
        ps.mulPose(Axis.XP.rotationDegrees(tilt));
        ps.scale(scale, scale, scale);
        ps.translate(0F, -10F / 16F, 0F);
    }

    static void drawForm(PoseStack ps, MultiBufferSource buf, int light, ResourceLocation model, boolean left) {
        Minecraft mc = Minecraft.getInstance();
        BakedModel m = mc.getModelManager().getModel(model);
        if (m == null || m == mc.getModelManager().getMissingModel()) return;
        VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));
        ps.pushPose();
        ps.translate((left ? 1F : -1F) / 16F, 10F / 16F, 0F);
        ps.mulPose(Axis.YP.rotationDegrees(180F));
        if (left) ps.scale(-1F, 1F, 1F);
        PoseStack.Pose pose = ps.last();
        RAND.setSeed(42L);
        List<BakedQuad> loose = m.getQuads(null, null, RAND, ModelData.EMPTY, null);
        if (loose.size() < 24) { ps.popPose(); return; }   // not loaded (the missing-model cube): draw nothing rather than a huge box
        for (BakedQuad q : loose) vc.putBulkData(pose, q, 1F, 1F, 1F, 1F, light, OverlayTexture.NO_OVERLAY, true);
        ps.popPose();
    }

    // ------------------------------------------------------------------ third person
    @Override
    public void render(PoseStack ps, MultiBufferSource buf, int light, AbstractClientPlayer p, float limbSwing, float limbSwingAmount,
                       float pt, float age, float netHeadYaw, float headPitch) {
        int[] l = LOOKS.get(p.getId());
        if (l == null || p.isInvisible()) return;
        PlayerModel<AbstractClientPlayer> pm = getParentModel();
        if (l[2] == 1) {   // the Chitin Hide: a plated skin just over the body, arms and legs
            pm.copyPropertiesTo(hideModel);
            hideModel.head.visible = false; hideModel.hat.visible = false;
            float fr = l[3] == 1 ? 0.75F + 0.25F * Mth.sin((p.tickCount + pt) * 0.6F) : 1F;
            hideModel.renderToBuffer(ps, buf.getBuffer(RenderType.armorCutoutNoCull(HIDE_TEX)), light, OverlayTexture.NO_OVERLAY, 1F, fr, fr, 1F);
        }
        if (l[0] > 0 && l[0] < MODELS.length) {
            ps.pushPose();
            pm.rightArm.translateAndRotate(ps);
            drawForm(ps, buf, light, formModel(l[0]), false);
            ps.popPose();
        }
        if (l[1] == 1) {
            ps.pushPose();
            pm.leftArm.translateAndRotate(ps);
            drawForm(ps, buf, light, BULWARK, true);
            ps.popPose();
        }
    }

    // ------------------------------------------------------------------ first person: on the bare arm vanilla draws
    @SubscribeEvent
    public static void onArm(RenderArmEvent e) {
        if (e.getArm() != HumanoidArm.RIGHT) return;
        AbstractClientPlayer p = e.getPlayer();
        int[] l = LOOKS.get(p.getId());
        if (l == null || l[0] <= 0 || l[0] >= MODELS.length) return;
        if (!(Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(p) instanceof PlayerRenderer r)) return;
        PlayerModel<AbstractClientPlayer> m = r.getModel();
        m.attackTime = 0F; m.crouching = false; m.swimAmount = 0F;
        m.setupAnim(p, 0F, 0F, 0F, 0F, 0F);
        m.rightArm.xRot = 0F;
        PoseStack ps = e.getPoseStack();
        ps.pushPose();
        m.rightArm.translateAndRotate(ps);
        firstPerson(ps, fpTilt, fpScale);
        drawForm(ps, e.getMultiBufferSource(), e.getPackedLight(), formModel(l[0]), false);
        ps.popPose();
    }

    /** The Bulwark in first person: vanilla never draws an empty off hand, so the left arm and its shield are drawn
     *  here, posed the way vanilla poses a bare arm, mirrored to the left. */
    @SubscribeEvent
    public static void onHand(RenderHandEvent e) {
        if (e.getHand() != InteractionHand.MAIN_HAND) return;
        Minecraft mc = Minecraft.getInstance();
        AbstractClientPlayer p = mc.player;
        if (p == null || p.isInvisible()) return;
        int[] l = LOOKS.get(p.getId());
        if (l == null || l[1] != 1 || !p.getOffhandItem().isEmpty()) return;
        if (!(mc.getEntityRenderDispatcher().getRenderer(p) instanceof PlayerRenderer r)) return;
        PoseStack ps = e.getPoseStack();
        ps.pushPose();
        float f = -1F;   // the left arm
        ps.translate(f * 0.64000005F, -0.6F - 0.12F, -0.71999997F);
        ps.mulPose(Axis.YP.rotationDegrees(f * 45.0F));
        ps.translate(f * -1.0F, 3.6F, 3.5F);
        ps.mulPose(Axis.ZP.rotationDegrees(f * 120.0F));
        ps.mulPose(Axis.XP.rotationDegrees(200.0F));
        ps.mulPose(Axis.YP.rotationDegrees(f * -135.0F));
        ps.translate(f * 5.6F, 0.0F, 0.0F);
        r.renderLeftHand(ps, e.getMultiBufferSource(), e.getPackedLight(), p);
        PlayerModel<AbstractClientPlayer> m = r.getModel();
        m.leftArm.xRot = 0F;
        m.leftArm.translateAndRotate(ps);
        firstPerson(ps, 0F, bwScale);
        drawForm(ps, e.getMultiBufferSource(), e.getPackedLight(), BULWARK, true);
        ps.popPose();
    }

    // ------------------------------------------------------------------ registration
    @Mod.EventBusSubscriber(modid = "iron_spell_grimoire", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Register {
        @SubscribeEvent
        public static void models(ModelEvent.RegisterAdditional e) {
            for (int f = 1; f < MODELS.length; f++) e.register(formModel(f));
            e.register(BULWARK);
        }

        @SubscribeEvent
        public static void layers(EntityRenderersEvent.AddLayers e) {
            for (String skin : e.getSkins()) {
                PlayerRenderer r = e.getSkin(skin);
                if (r != null) r.addLayer(new ParasiteArms(r, new HumanoidModel<>(e.getEntityModels().bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR))));
            }
        }
    }
}
