package com.ironspellgrimoire.client;

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
    private static final ResourceLocation WHITE = ResourceLocation.fromNamespaceAndPath("iron_spell_grimoire", "textures/effect/white.png");
    /** player id -> {start, end, palette} */
    /** player id -> (palette -> {start, end, palette}): several auras can show at once (e.g. Devil Union + Instinct Guard). */
    private static final Map<Integer, Map<Integer, double[]>> ACTIVE = new HashMap<>();
    private static final float[][] COLORS = {
            {0.55F, 0.18F, 0.95F},   // 0 dark violet (Black Blade)
            {1.0F, 0.08F, 0.06F},    // 1 devil red
            {0.86F, 0.9F, 0.97F},    // 2 liquid silver (Silver Guardian)
            {0.45F, 0.72F, 1.0F},    // 3 blue lightning (Thunder God Mode)
            {1.0F, 0.14F, 0.08F},    // 4 crimson hellfire (Hellfire Incarnate)
            {1.0F, 0.3F, 0.2F},      // 5 mana skin
            {0.93F, 0.89F, 0.8F},    // 6 bone plates (Marrow Regeneration)
            {0.88F, 0.95F, 1.0F},    // 7 ki (Instinct Guard)
            {0.75F, 0.03F, 0.03F},   // 8 faint demonic (Devil Union)
            {1.0F, 0.78F, 0.25F},    // 9 liquid gold (Rien's Golden Shell)
            {0.7F, 0.0F, 0.06F},     // 10 blood (Don Quixote's Vampire Mode)
            {0.05F, 0.05F, 0.07F},   // 11 black slime (Rien's shell)
            {1.0F, 1.0F, 1.0F},      // 12 white (Scarlet Moon, the Blood Grimoire)
    };

    public AuraOutlineLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    public static void set(int id, int palette, int life) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        double now = mc.level.getGameTime();
        if (life > 0) {
            Map<Integer, double[]> m = ACTIVE.computeIfAbsent(id, k -> new HashMap<>());
            double[] f = m.get(palette);
            if (f == null || f[1] < now) m.put(palette, new double[]{now, now + life, palette});
            else { f[1] = now + life; f[2] = palette; }
        } else {
            Map<Integer, double[]> m = ACTIVE.get(id);
            if (m != null) { m.remove(palette); if (m.isEmpty()) ACTIVE.remove(id); }
        }
    }

    @Override
    public void render(PoseStack ps, MultiBufferSource buf, int light, AbstractClientPlayer p, float limbSwing, float limbSwingAmount,
                       float pt, float age, float netHeadYaw, float headPitch) {
        if (!p.isInvisible()) limitShell(ps, buf, p, pt);
        Map<Integer, double[]> all = ACTIVE.get(p.getId());
        if (all == null || p.isInvisible()) return;
        double now0 = p.level().getGameTime() + pt;
        all.values().removeIf(x -> now0 > x[1]);
        if (all.isEmpty()) { ACTIVE.remove(p.getId()); return; }
        for (double[] f : all.values()) drawOne(ps, buf, p, pt, f, now0);
    }

    private void drawOne(PoseStack ps, MultiBufferSource buf, AbstractClientPlayer p, float pt, double[] f, double now) {
        float in = (float) Mth.clamp((now - f[0]) / 6.0, 0.0, 1.0), out = (float) Mth.clamp((f[1] - now) / 10.0, 0.0, 1.0);
        float a = in * out;
        if (a <= 0.01F) return;
        float[] col = COLORS[Mth.clamp((int) f[2], 0, COLORS.length - 1)];
        PlayerModel<AbstractClientPlayer> m = getParentModel();
        VertexConsumer vc = buf.getBuffer(RenderType.entityTranslucentEmissive(WHITE));
        float flick = (float) (Math.sin(now * 0.9 + p.getId()) * 0.012 + Math.sin(now * 2.3) * 0.008);
        if ((int) f[2] == 12) {   // Scarlet Moon: a blazing white aura round a thin blood-red skin - layered, flaring and pulsing
            float beat = (float) Math.pow(Math.max(0, Math.sin(now * 0.32)), 6);   // a heartbeat runs through it
            float flare = 0.85F + 0.15F * (float) Math.sin(now * 1.7 + p.getId()) + 0.25F * beat;
            shell(ps, m, vc, 1.03F, 0.6F, 0.02F, 0.04F, 0.35F * a);                                        // blood-red skin
            shell(ps, m, vc, 1.08F + flick * 0.6F, 1.0F, 1.0F, 1.0F, 0.42F * a * flare);                   // bright white
            shell(ps, m, vc, 1.16F + flick * 1.4F + 0.03F * beat, 1.0F, 0.97F, 0.97F, 0.22F * a * flare);  // white haze
            shell(ps, m, vc, 1.28F + flick * 2.2F + 0.06F * beat, 1.0F, 0.85F, 0.88F, 0.08F * a);          // the wide outer glow
            return;
        }
        if ((int) f[2] == 2) {   // Silver Guardian: a skin of liquid mercury, bright and rippling, hugging the body
            float shine = 0.75F + 0.25F * (float) Math.sin(now * 0.5);
            shell(ps, m, vc, 1.03F + flick * 0.3F, 0.62F * shine, 0.66F * shine, 0.72F * shine, 0.62F * a);
            shell(ps, m, vc, 1.07F + flick * 0.5F, col[0], col[1], col[2], 0.16F * a);
            return;
        }
        if ((int) f[2] == 11) {   // Rien's Black Shell: a skin of glossy black slime like a tailored black coat, a cold sheen sliding over it
            float sheen = (float) Math.pow(0.5 + 0.5 * Math.sin(now * 0.35), 4);
            shell(ps, m, vc, 1.035F, 0.015F, 0.015F, 0.02F, 0.78F * a);
            shell(ps, m, vc, 1.05F + flick * 0.2F, 0.75F, 0.75F, 0.82F, (0.05F + 0.14F * sheen) * a);
            return;
        }
        if ((int) f[2] == 4) {   // Hellfire Incarnate: a body of fire - a white-hot skin inside a wide, pulsing orange glow
            float pulse = 0.8F + 0.2F * (float) Math.sin(now * 0.8);
            shell(ps, m, vc, 1.03F + flick, 1.0F, 0.75F, 0.55F, 0.3F * a * pulse);
            shell(ps, m, vc, 1.1F + flick * 1.5F, col[0], col[1], col[2], 0.22F * a * pulse);
            shell(ps, m, vc, 1.2F + flick * 2F, 0.7F, 0.02F, 0.02F, 0.12F * a);
            return;
        }
        if ((int) f[2] == 7) {   // Instinct Guard: a thin, flickering skin of white ki - reading everything around you
            float flickK = 0.7F + 0.3F * (float) Math.sin(now * 1.3 + p.getId());
            float sweep = 0.5F + 0.5F * (float) Math.sin(now * 0.9);
            shell(ps, m, vc, 1.03F, 0.02F, 0.03F, 0.05F, 0.45F * a);                                   // dark core so the ki reads
            shell(ps, m, vc, 1.06F + flick * 0.4F, 0.85F, 0.95F, 1.0F, 0.6F * a * flickK);              // bright ki skin
            shell(ps, m, vc, 1.12F + flick + 0.03F * sweep, col[0], col[1], col[2], 0.3F * a);          // rippling ring of sensed ki
            shell(ps, m, vc, 1.2F + flick * 1.6F + 0.06F * sweep, 1.0F, 1.0F, 1.0F, 0.12F * a * flickK);
            return;
        }
        if ((int) f[2] == 8) {   // Devil Union: a faint, slow-breathing demonic haze - black inside, a dim red edge
            float breathe = 0.7F + 0.3F * (float) Math.sin(now * 0.25);
            shell(ps, m, vc, 1.05F + flick * 0.5F, 0.02F, 0.0F, 0.0F, 0.18F * a);
            shell(ps, m, vc, 1.13F + flick * 1.4F, col[0], col[1], col[2], 0.09F * a * breathe);
            return;
        }
        if ((int) f[2] == 6) {   // bone plates: a hard ivory shell over the body
            shell(ps, m, vc, 1.06F, 0.9F, 0.86F, 0.76F, 0.5F * a);
            return;
        }
        if ((int) f[2] == 5) {   // Mana Skin: a thin, faint warm sheen
            shell(ps, m, vc, 1.025F, col[0], col[1], col[2], 0.14F * a);
            return;
        }
        if ((int) f[2] == 3) {   // Thunder God Mode: a flickering skin of yellow light, no dark shell
            float zap = ((int) (now * 3) % 4 == 1) ? 0.5F : 1.0F;
            shell(ps, m, vc, 1.05F + flick, col[0], col[1], col[2], 0.22F * a * zap);
            shell(ps, m, vc, 1.12F + flick * 1.5F, 0.85F, 0.95F, 1.0F, 0.08F * a * zap);
            return;
        }
        // black shell hugging the body
        shell(ps, m, vc, 1.045F + flick * 0.5F, 0.02F, 0.0F, 0.03F, 0.55F * a);
        // coloured shell around it, breathing
        shell(ps, m, vc, 1.11F + flick, col[0], col[1], col[2], 0.28F * a);
        shell(ps, m, vc, 1.17F + flick * 1.6F, col[0], col[1], col[2], 0.12F * a);
    }

    private static void shell(PoseStack ps, PlayerModel<AbstractClientPlayer> m, VertexConsumer vc, float scale, float r, float g, float b, float a) {
        a *= com.ironspellgrimoire.AddonClientConfig.effectOpacity();
        ps.pushPose();
        ps.translate(0.0F, 0.75F, 0.0F);   // grow around the middle of the body, not the neck
        ps.scale(scale, scale, scale);
        ps.translate(0.0F, -0.75F, 0.0F);
        m.renderToBuffer(ps, vc, 15728880, OverlayTexture.NO_OVERLAY, r, g, b, a);
        ps.popPose();
    }

    /** Surpassing Limits: a thin white-hot skin and a breathing glow in the grimoire's colour. */
    private void limitShell(PoseStack ps, MultiBufferSource buf, AbstractClientPlayer p, float pt) {
        double[] f = LimitClient.ACTIVE.get(p.getId());
        if (f == null) return;
        double now = p.level().getGameTime() + pt;
        if (now > f[1]) return;
        float in = (float) Mth.clamp((now - f[0]) / 8.0, 0.0, 1.0), out = (float) Mth.clamp((f[1] - now) / 20.0, 0.0, 1.0);
        float a = in * out;
        if (a <= 0.01F) return;
        float[] col = LimitClient.COLORS[Mth.clamp((int) f[2], 0, LimitClient.COLORS.length - 1)];
        PlayerModel<AbstractClientPlayer> m = getParentModel();
        VertexConsumer vc = buf.getBuffer(RenderType.entityTranslucentEmissive(WHITE));
        float breathe = 0.8F + 0.2F * (float) Math.sin(now * 0.4);
        shell(ps, m, vc, 1.035F, 1.0F, 0.97F, 0.9F, 0.16F * a * breathe);
        shell(ps, m, vc, 1.14F + 0.02F * (float) Math.sin(now * 0.7), col[0], col[1], col[2], 0.14F * a * breathe);
    }
}
