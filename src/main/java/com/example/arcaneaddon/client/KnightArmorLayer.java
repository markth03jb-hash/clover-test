package com.example.arcaneaddon.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * Knight's Armor form: white-steel plate armor with gold trim, a visored helm with a red plume, and a red cape -
 * animated GeckoLib models (GeoForms) worn on the player's own model parts, so every plate follows the head, arms
 * and legs. The pieces slam on with a little pop when the form starts and then breathe gently; the plume sways
 * (harder while running); the cape swings with your movement like the vanilla cape and flutters on top of that -
 * softly standing still, hard while running, wildly in the air.
 * Fed by FxPacket shape 11 (id = player, life = ticks left, 0 = off).
 */
public class KnightArmorLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    /** player entity id -> {start, end} game time */
    private static final Map<Integer, double[]> ACTIVE = new HashMap<>();

    public KnightArmorLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    public static void set(int id, int life) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        double now = mc.level.getGameTime();
        if (life > 0) {
            double[] f = ACTIVE.get(id);
            if (f == null || f[1] < now) ACTIVE.put(id, new double[]{now, now + life});
            else f[1] = now + life;
        } else ACTIVE.remove(id);
    }

    @EventBusSubscriber(modid = "arcane_addon", value = {Dist.CLIENT}, bus = EventBusSubscriber.Bus.MOD)
    public static final class Register {
        @SubscribeEvent
        public static void addLayers(EntityRenderersEvent.AddLayers e) {
            for (String skin : e.getSkins()) {
                PlayerRenderer r = e.getSkin(skin);
                if (r != null) {
                    r.addLayer(new KnightArmorLayer(r));
                    r.addLayer(new GeoFormLayer(r));
                    r.addLayer(new AuraOutlineLayer(r));
                    r.addLayer(new FistFlameLayer(r));
                    r.addLayer(new BoneBladeLayer(r));
                }
            }
        }
    }

    @Override
    public void render(PoseStack ps, MultiBufferSource buf, int light, AbstractClientPlayer p, float limbSwing, float limbSwingAmount,
                       float pt, float age, float netHeadYaw, float headPitch) {
        double[] f = ACTIVE.get(p.getId());
        if (f == null || p.isInvisible()) return;
        double now = p.level().getGameTime() + pt;
        if (now > f[1]) { ACTIVE.remove(p.getId()); return; }
        PlayerModel<AbstractClientPlayer> m = getParentModel();
        double start = f[0];
        int id = p.getId();
        boolean running = Math.abs(p.getX() - p.xo) + Math.abs(p.getZ() - p.zo) > 0.08;
        piece(ps, m.head, 0, () -> GeoForms.draw("knight_helm", id, start, "equip", running ? "run" : "idle", null, ps, buf, light));
        piece(ps, m.body, 0, () -> GeoForms.draw("knight_chest", id, start, "equip", "idle", null, ps, buf, light));
        // the pieces are centred on each limb; slim arms sit half a pixel further in
        float armOff = "slim".equals(p.getModelName()) ? 0.5F : 1.0F;
        piece(ps, m.rightArm, -armOff, () -> GeoForms.draw("knight_arm", id * 4 + 1, start, "equip", "idle", null, ps, buf, light));
        piece(ps, m.leftArm, armOff, () -> GeoForms.draw("knight_arm", id * 4 + 2, start, "equip", "idle", null, ps, buf, light));
        piece(ps, m.rightLeg, 0, () -> GeoForms.draw("knight_leg", id * 4 + 1, start, "equip", "idle", null, ps, buf, light));
        piece(ps, m.leftLeg, 0, () -> GeoForms.draw("knight_leg", id * 4 + 2, start, "equip", "idle", null, ps, buf, light));
        cape(ps, buf, light, p, pt, start);
    }

    private static void piece(PoseStack ps, ModelPart part, float xOffPx, Runnable draw) {
        ps.pushPose();
        part.translateAndRotate(ps);
        if (xOffPx != 0) ps.translate(xOffPx / 16.0F, 0, 0);
        draw.run();
        ps.popPose();
    }

    /** The red cape: swings from the shoulders with your movement like the vanilla cape; the model flutters on top. */
    private static void cape(PoseStack ps, MultiBufferSource buf, int light, AbstractClientPlayer p, float pt, double start) {
        ps.pushPose();
        ps.translate(0.0F, 0.0F, 0.17F);
        double dx = Mth.lerp(pt, p.xCloakO, p.xCloak) - Mth.lerp(pt, p.xo, p.getX());
        double dy = Mth.lerp(pt, p.yCloakO, p.yCloak) - Mth.lerp(pt, p.yo, p.getY());
        double dz = Mth.lerp(pt, p.zCloakO, p.zCloak) - Mth.lerp(pt, p.zo, p.getZ());
        float body = Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot);
        double s = Mth.sin(body * ((float) Math.PI / 180F)), c = -Mth.cos(body * ((float) Math.PI / 180F));
        float lift = Mth.clamp((float) dy * 10.0F, -6.0F, 32.0F);
        float back = Mth.clamp((float) (dx * s + dz * c) * 100.0F, 0.0F, 150.0F);
        float side = Mth.clamp((float) (dx * c - dz * s) * 100.0F, -20.0F, 20.0F);
        float bob = Mth.lerp(pt, p.oBob, p.bob);
        lift += Mth.sin(Mth.lerp(pt, p.walkDistO, p.walkDist) * 6.0F) * 32.0F * bob;
        if (p.isCrouching()) lift += 25.0F;
        ps.mulPose(Axis.XP.rotationDegrees(6.0F + back / 4.0F + lift * 0.6F));   // the model's own flutter adds the rest
        ps.mulPose(Axis.ZP.rotationDegrees(side / 2.0F));
        String state = !p.onGround() ? "air" : (back > 20.0F ? "run" : "idle");
        GeoForms.draw("knight_cape", p.getId(), start, null, state, null, ps, buf, light);
        ps.popPose();
    }
}
