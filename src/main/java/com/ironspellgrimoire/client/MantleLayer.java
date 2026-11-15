package com.ironspellgrimoire.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

/**
 * Mantle of Shades (server/ShadowFx.mantleOfShades): three shadows sunk into their master, worn as a hood with a
 * half-mask and eye-lights, cracked black plates over the chest with a tattered shroud behind, shoulder plates with
 * smoke curling off them, bracers and greaves ringed with blue (GeoForms mantle_*; tools/shadow_models.py). The pieces
 * swell out of nothing as it forms, then breathe. Fed by FxPacket shape 98 kind 15 (v.x = player id, life = ticks).
 */
public class MantleLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    /** player entity id -> {start, end} game time */
    private static final Map<Integer, double[]> ACTIVE = new HashMap<>();

    public MantleLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) { super(parent); }

    public static void set(int id, int life) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        double now = mc.level.getGameTime();
        if (life > 0) ACTIVE.put(id, new double[]{now, now + life});
        else ACTIVE.remove(id);
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
        piece(ps, m.head, 0, () -> GeoForms.draw("mantle_helm", id, start, "form", "idle", null, ps, buf, light));
        piece(ps, m.body, 0, () -> GeoForms.draw("mantle_chest", id, start, "form", "idle", null, ps, buf, light));
        float armOff = "slim".equals(p.getModelName()) ? 0.5F : 1.0F;
        piece(ps, m.rightArm, -armOff, () -> GeoForms.draw("mantle_arm", id * 4 + 1, start, "form", "idle", null, ps, buf, light));
        piece(ps, m.leftArm, armOff, () -> GeoForms.draw("mantle_arm", id * 4 + 2, start, "form", "idle", null, ps, buf, light));
        piece(ps, m.rightLeg, 0, () -> GeoForms.draw("mantle_leg", id * 4 + 1, start, "form", "idle", null, ps, buf, light));
        piece(ps, m.leftLeg, 0, () -> GeoForms.draw("mantle_leg", id * 4 + 2, start, "form", "idle", null, ps, buf, light));
    }

    private static void piece(PoseStack ps, ModelPart part, float xOffPx, Runnable draw) {
        ps.pushPose();
        part.translateAndRotate(ps);
        if (xOffPx != 0) ps.translate(xOffPx / 16.0F, 0, 0);
        draw.run();
        ps.popPose();
    }
}
