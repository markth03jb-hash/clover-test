package com.example.arcaneaddon.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

import java.util.HashMap;
import java.util.Map;

/**
 * Zenon's Bone Sword. Third person: held in the right hand, drawn in the arm's own space so it is one solid piece
 * that follows every frame of the swing. First person: drawn in your view, cutting with the same three swings.
 * The sword is an animated GeckoLib model (GeoForms): a pommel knob, a grip of stacked vertebrae, a guard of two
 * rib-prongs and a blade of bone segments with a tooth on each - it grows out of the hand segment by segment, the
 * ridges then pulse along it, and it shrinks back at the end.
 * Fed by FxPacket shape 37 (id = player, life = ticks, r = blade length, palette 1 = Beelzebub: a violet-black edge).
 */
@EventBusSubscriber(modid = "arcane_addon", value = {Dist.CLIENT})
public class BoneBladeLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    /** player id -> {start, end, length, union} */
    private static final Map<Integer, double[]> ACTIVE = new HashMap<>();

    public BoneBladeLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) { super(parent); }

    public static void set(int id, int life, float length, boolean union) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        double now = mc.level.getGameTime();
        if (life <= 0) ACTIVE.remove(id);
        else ACTIVE.put(id, new double[]{now, now + life, length, union ? 1 : 0});
    }

    private static float grow(double[] f, double now) {
        return (float) Mth.clamp((now - f[0]) / 4.0, 0.0, 1.0) * (float) Mth.clamp((f[1] + 4 - now) / 4.0, 0.0, 1.0);
    }

    // ------------------------------------------------------------------ third person: on the arm
    @Override
    public void render(PoseStack ps, MultiBufferSource buf, int light, AbstractClientPlayer p, float limbSwing, float limbSwingAmount,
                       float pt, float age, float netHeadYaw, float headPitch) {
        double[] f = ACTIVE.get(p.getId());
        if (f == null || p.isInvisible()) return;
        double now = p.level().getGameTime() + pt;
        if (now > f[1] + 4) { ACTIVE.remove(p.getId()); return; }
        float g = grow(f, now);
        if (g <= 0.01F) return;
        ps.pushPose();
        getParentModel().rightArm.translateAndRotate(ps);
        ps.translate(-1.0F / 16.0F, 0.6F, -0.03F);   // in the fist
        drawSword(ps, buf, light, (float) f[2] * g, g, f[3] > 0.5, p.getId(), f[0]);
        ps.popPose();
    }

    // ------------------------------------------------------------------ first person: in your view
    /** {tick, yaw, pitch, roll}: wind up right, sweep left, backhand right, overhead chop - the same three cuts. */
    private static final float[][] FP_KEYS = {{0, 10, 25, 0}, {3, 55, 15, -55}, {5, -60, 0, -70}, {9, -70, 8, 60}, {12, 55, 0, 70},
            {16, 0, 75, 0}, {20, 0, -45, 0}, {26, 10, 25, 0}};

    private static float[] fpAt(double t) {
        if (t <= FP_KEYS[0][0]) return FP_KEYS[0];
        for (int i = 1; i < FP_KEYS.length; i++) {
            if (t <= FP_KEYS[i][0]) {
                float a = (float) ((t - FP_KEYS[i - 1][0]) / (FP_KEYS[i][0] - FP_KEYS[i - 1][0]));
                a = a * a * (3 - 2 * a);
                float[] o = new float[4];
                for (int k = 1; k < 4; k++) o[k] = FP_KEYS[i - 1][k] + (FP_KEYS[i][k] - FP_KEYS[i - 1][k]) * a;
                return o;
            }
        }
        return FP_KEYS[FP_KEYS.length - 1];
    }

    @SubscribeEvent
    public static void onHand(RenderHandEvent e) {
        if (e.getHand() != InteractionHand.MAIN_HAND) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        double[] f = ACTIVE.get(mc.player.getId());
        if (f == null) return;
        double now = mc.level.getGameTime() + e.getPartialTick();
        if (now > f[1] + 4) return;
        float g = grow(f, now);
        if (g <= 0.01F) return;
        float[] k = fpAt(now - f[0]);
        PoseStack ps = e.getPoseStack();
        ps.pushPose();
        ps.translate(0.56F, -0.5F, -0.7F);                // right where your hand is in first person
        ps.mulPose(Axis.YP.rotationDegrees(-k[1]));
        ps.mulPose(Axis.XP.rotationDegrees(-k[2]));
        ps.mulPose(Axis.ZP.rotationDegrees(k[3]));
        ps.mulPose(Axis.XP.rotationDegrees(180.0F));       // arm space: +y down, blade toward -z
        ps.scale(0.85F, 0.85F, 0.85F);
        drawSword(ps, e.getMultiBufferSource(), e.getPackedLight(), (float) f[2] * g, g, f[3] > 0.5, -mc.player.getId() - 1, f[0]);   // its own first-person instance
        ps.popPose();
    }

    // ------------------------------------------------------------------ the sword itself (blade along -z, +y = down the arm)
    private static final ResourceLocation UNION_TEX = ResourceLocation.fromNamespaceAndPath("arcane_addon", "textures/geo/bone_sword_union.png");
    /** The model's own length, pommel to point, in blocks (it is stretched to the blade length the move asks for). */
    private static final float MODEL_LEN = 2.25F;

    /** The animated bone sword (GeoForms "bone_sword"): it grows out vertebra by vertebra, then its ridges pulse. */
    private static void drawSword(PoseStack ps, MultiBufferSource buf, int light, float L, float g, boolean union, int owner, double started) {
        if (L < 0.05F) return;
        ps.pushPose();
        ps.scale(g, g, L / MODEL_LEN);
        GeoForms.draw("bone_sword", owner, started, "form", "idle", union ? UNION_TEX : null, ps, buf, light);
        ps.popPose();
    }
}
