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
    static final String[] MODELS = {"", "reaper_edge", "rending_talons", "spine_launcher", "titan_fist", "bone_lance", "whip_spear", "battering_ram"};
    static final ResourceLocation CREST = rl("item/parasite_final_crest"), CARAPACE = rl("item/parasite_final_carapace");
    static final ResourceLocation[] LOOK_BODY = {null, rl("item/parasite_hide_spiked"), rl("item/parasite_hide_smooth"), rl("item/parasite_hide_plated")};
    static final ResourceLocation[] LOOK_ARM = {null, rl("item/parasite_hide_spiked_arm"), rl("item/parasite_hide_smooth_arm"), rl("item/parasite_hide_plated_arm")};
    static final ResourceLocation BULWARK = rl("item/parasite_bulwark");
    static final ResourceLocation HIDE_TEX = ResourceLocation.fromNamespaceAndPath("iron_spell_grimoire", "textures/models/armor/chitin_hide.png");
    static ResourceLocation rl(String p) { return ResourceLocation.fromNamespaceAndPath("iron_spell_grimoire", p); }
    static ResourceLocation formModel(int f) { return rl("item/parasite_" + MODELS[f]); }

    /** player id -> {form, bulwark, hide, frenzied} */
    private static final Map<Integer, int[]> LOOKS = new HashMap<>();

    public static void look(ParasitePackets.Look p) {
        if (p.form == 0 && !p.bulwark && !p.hide && p.flags == 0) LOOKS.remove(p.id);
        else LOOKS.put(p.id, new int[]{p.form, p.bulwark ? 1 : 0, p.hide ? 1 : 0, p.frenzied ? 1 : 0, p.flags});
    }

    static int[] lookOf(int id) { return LOOKS.get(id); }

    /** For the screenshots: show a look on a player without the server. */
    public static void testLook(int id, int form, boolean bulwark, boolean hide) { look(new ParasitePackets.Look(id, form, bulwark, hide, false, 0)); }

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
    public static float fpTilt = -40F, fpScale = 0.75F, bwScale = 0.8F, bwTurn = 180F;

    /** In first person, turn and shrink round the hand (where the forearm ends) before drawing. */
    static void firstPerson(PoseStack ps, float tilt, float scale) {
        ps.translate(0F, 10F / 16F, 0F);
        ps.mulPose(Axis.XP.rotationDegrees(tilt));
        ps.scale(scale, scale, scale);
        ps.translate(0F, -10F / 16F, 0F);
    }

    /** Draw one of the Final Form's pieces in its part's own space (the head's or the body's: the model is built there). */
    static void drawPiece(PoseStack ps, MultiBufferSource buf, int light, ResourceLocation model) {
        Minecraft mc = Minecraft.getInstance();
        BakedModel m = mc.getModelManager().getModel(model);
        if (m == null) return;
        RAND.setSeed(42L);
        List<BakedQuad> qs = m.getQuads(null, null, RAND, ModelData.EMPTY, null);
        if (qs.size() < 24) return;   // not loaded
        VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));
        PoseStack.Pose pose = ps.last();
        for (BakedQuad q : qs) vc.putBulkData(pose, q, 1F, 1F, 1F, 1F, light, OverlayTexture.NO_OVERLAY, true);
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
            int look = l.length > 4 ? (l[4] >> 3) & 3 : 0;   // the hide's look: its plating over the body and both arms
            if (look > 0) {
                ps.pushPose(); pm.body.translateAndRotate(ps); drawPiece(ps, buf, light, LOOK_BODY[look]); ps.popPose();
                ps.pushPose(); pm.rightArm.translateAndRotate(ps); drawForm(ps, buf, light, LOOK_ARM[look], false); ps.popPose();
                ps.pushPose(); pm.leftArm.translateAndRotate(ps); drawForm(ps, buf, light, LOOK_ARM[look], true); ps.popPose();
            }
        }
        if (l[0] > 0 && l[0] < MODELS.length) {
            ps.pushPose();
            pm.rightArm.translateAndRotate(ps);
            drawForm(ps, buf, light, formModel(l[0]), false);
            ps.popPose();
        }
        boolean fin = l.length > 4 && (l[4] & 2) != 0;
        if (l[1] == 1 || fin) {   // the off hand: the Bulwark, or in the Final Form the talons
            ps.pushPose();
            pm.leftArm.translateAndRotate(ps);
            drawForm(ps, buf, light, l[1] == 1 ? BULWARK : formModel(2), true);
            ps.popPose();
        }
        if (fin) {   // the whole body taken over: the crest on the head, the carapace on the body
            ps.pushPose();
            pm.head.translateAndRotate(ps);
            drawPiece(ps, buf, light, CREST);
            ps.popPose();
            ps.pushPose();
            pm.body.translateAndRotate(ps);
            drawPiece(ps, buf, light, CARAPACE);
            ps.popPose();
        }
    }

    // ------------------------------------------------------------------ burrowed: not drawn at all (only the mound the server shows)
    static boolean burrowed(int id) { int[] l = LOOKS.get(id); return l != null && l.length > 4 && (l[4] & 4) != 0; }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGH)
    public static void hideBurrowed(net.minecraftforge.client.event.RenderPlayerEvent.Pre e) {
        if (burrowed(e.getEntity().getId())) e.setCanceled(true);
    }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGH)
    public static void hideBurrowedHands(RenderHandEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && burrowed(mc.player.getId())) e.setCanceled(true);
    }

    // ------------------------------------------------------------------ bigger: Colossus and the Final Form
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)   // last: nothing can cancel it after the push
    public static void growPre(net.minecraftforge.client.event.RenderPlayerEvent.Pre e) {
        float k = growth(e.getEntity().getId());
        if (k == 1F) return;
        e.getPoseStack().pushPose();
        e.getPoseStack().scale(k, k, k);
    }

    @SubscribeEvent
    public static void growPost(net.minecraftforge.client.event.RenderPlayerEvent.Post e) {
        if (growth(e.getEntity().getId()) != 1F) e.getPoseStack().popPose();
    }

    static float growth(int id) {
        int[] l = LOOKS.get(id);
        if (l == null || l.length < 5) return 1F;
        return (l[4] & 2) != 0 ? 1.3F : (l[4] & 1) != 0 ? 1.4F : 1F;
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
        e.setCanceled(true);   // only the form: the bare arm under it isn't drawn
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
        ps.translate(f * 0.40F, -0.56F, -0.72F);   // a little in from where vanilla puts a bare arm, so the shield sits clear of the meter
        ps.mulPose(Axis.YP.rotationDegrees(f * 45.0F));
        ps.translate(f * -1.0F, 3.6F, 3.5F);
        ps.mulPose(Axis.ZP.rotationDegrees(f * 120.0F));
        ps.mulPose(Axis.XP.rotationDegrees(200.0F));
        ps.mulPose(Axis.YP.rotationDegrees(f * -135.0F));
        ps.translate(f * 5.6F, 0.0F, 0.0F);
        PlayerModel<AbstractClientPlayer> m = r.getModel();
        m.attackTime = 0F; m.crouching = false; m.swimAmount = 0F;   // posed the way vanilla poses a first-person arm
        m.setupAnim(p, 0F, 0F, 0F, 0F, 0F);
        m.leftArm.xRot = 0F;
        m.leftArm.translateAndRotate(ps);
        firstPerson(ps, 0F, bwScale);
        ps.translate(1F / 16F, 0F, 0F);                    // turned round the forearm: its face out ahead, its back to you,
        ps.mulPose(Axis.YP.rotationDegrees(bwTurn));       // the way a shield is really held
        ps.translate(-1F / 16F, 0F, 0F);
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
            e.register(CREST);
            e.register(CARAPACE);
            e.register(HiveNestRenderer.MODEL);
            for (int k = 1; k < LOOK_BODY.length; k++) { e.register(LOOK_BODY[k]); e.register(LOOK_ARM[k]); }
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
