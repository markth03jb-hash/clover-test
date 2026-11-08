package com.example.arcaneaddon.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * An aura outline around a player's own body, like the glowing outline of power in the anime: the player model drawn
 * again slightly larger, twice - a black shell hugging the body and a wider, flickering coloured shell around it - so
 * it follows every movement of the arms, legs and head. Used by Black Blade (dark violet).
 * Fed by FxPacket shape 14 (id = player, palette = colour, life = ticks left, 0 = off).
 */
public class AuraOutlineLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final ResourceLocation WHITE = ResourceLocation.fromNamespaceAndPath("arcane_addon", "textures/effect/white.png");
    /** player id -> {start, end, palette} */
    private static final Map<Integer, double[]> ACTIVE = new HashMap<>();
    private static final float[][] COLORS = {
            {0.55F, 0.18F, 0.95F},   // 0 dark violet (Black Blade)
            {1.0F, 0.08F, 0.06F},    // 1 devil red
            {0.86F, 0.9F, 0.97F},    // 2 liquid silver (Silver Guardian)
    };

    public AuraOutlineLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    public static void set(int id, int palette, int life) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        double now = mc.level.getGameTime();
        if (life > 0) {
            double[] f = ACTIVE.get(id);
            if (f == null || f[1] < now) ACTIVE.put(id, new double[]{now, now + life, palette});
            else { f[1] = now + life; f[2] = palette; }
        } else ACTIVE.remove(id);
    }

    @Override
    public void render(PoseStack ps, MultiBufferSource buf, int light, AbstractClientPlayer p, float limbSwing, float limbSwingAmount,
                       float pt, float age, float netHeadYaw, float headPitch) {
        double[] f = ACTIVE.get(p.getId());
        if (f == null || p.isInvisible()) return;
        double now = p.level().getGameTime() + pt;
        if (now > f[1]) { ACTIVE.remove(p.getId()); return; }
        float in = (float) Mth.clamp((now - f[0]) / 6.0, 0.0, 1.0), out = (float) Mth.clamp((f[1] - now) / 10.0, 0.0, 1.0);
        float a = in * out;
        if (a <= 0.01F) return;
        float[] col = COLORS[Mth.clamp((int) f[2], 0, COLORS.length - 1)];
        PlayerModel<AbstractClientPlayer> m = getParentModel();
        VertexConsumer vc = buf.getBuffer(RenderType.entityTranslucentEmissive(WHITE));
        float flick = (float) (Math.sin(now * 0.9 + p.getId()) * 0.012 + Math.sin(now * 2.3) * 0.008);
        if ((int) f[2] == 2) {   // Silver Guardian: a skin of liquid mercury, bright and rippling, hugging the body
            float shine = 0.75F + 0.25F * (float) Math.sin(now * 0.5);
            shell(ps, m, vc, 1.03F + flick * 0.3F, 0.62F * shine, 0.66F * shine, 0.72F * shine, 0.62F * a);
            shell(ps, m, vc, 1.07F + flick * 0.5F, col[0], col[1], col[2], 0.16F * a);
            return;
        }
        // black shell hugging the body
        shell(ps, m, vc, 1.045F + flick * 0.5F, 0.02F, 0.0F, 0.03F, 0.55F * a);
        // coloured shell around it, breathing
        shell(ps, m, vc, 1.11F + flick, col[0], col[1], col[2], 0.28F * a);
        shell(ps, m, vc, 1.17F + flick * 1.6F, col[0], col[1], col[2], 0.12F * a);
    }

    private static void shell(PoseStack ps, PlayerModel<AbstractClientPlayer> m, VertexConsumer vc, float scale, float r, float g, float b, float a) {
        ps.pushPose();
        ps.translate(0.0F, 0.75F, 0.0F);   // grow around the middle of the body, not the neck
        ps.scale(scale, scale, scale);
        ps.translate(0.0F, -0.75F, 0.0F);
        m.renderToBuffer(ps, vc, 15728880, OverlayTexture.NO_OVERLAY, r, g, b, a);
        ps.popPose();
    }
}
