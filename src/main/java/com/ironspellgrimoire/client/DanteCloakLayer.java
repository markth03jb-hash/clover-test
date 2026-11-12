package com.ironspellgrimoire.client;

import com.ironspellgrimoire.entity.Dante;
import com.ironspellgrimoire.server.BossKit;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Dante's cloak - an original design: a long oxblood mantle to his ankles under a short layered capelet, a high
 * stiff collar standing behind his neck, gold thread along every edge, a gold chain across his chest and a torn hem.
 * Drawn as cloth in the body's own space: it trails behind him as he moves, ripples as it hangs, and flares out wide
 * while he calls down the Singularity.
 */
public class DanteCloakLayer extends RenderLayer<Dante, PlayerModel<Dante>> {
    private static final ResourceLocation WHITE = new ResourceLocation("iron_spell_grimoire", "textures/effect/white.png");
    private static final float[] OUT = {0.36F, 0.05F, 0.07F}, IN = {0.13F, 0.03F, 0.04F}, GOLD = {0.86F, 0.66F, 0.26F}, CAPE = {0.30F, 0.04F, 0.06F};
    private static final int C = 12, R = 14;

    private VertexConsumer vc;
    private int light;

    public DanteCloakLayer(RenderLayerParent<Dante, PlayerModel<Dante>> parent) { super(parent); }

    @Override
    public void render(PoseStack ps, MultiBufferSource buf, int light, Dante e, float limbSwing, float limbAmount, float pt, float age,
                       float headYaw, float headPitch) {
        if (e.isInvisible()) return;
        this.light = light;
        this.vc = buf.getBuffer(RenderType.entityCutoutNoCull(WHITE));
        float since = age - e.poseAt();
        float flare = e.pose() == BossKit.P_RAISE ? Mth.clamp(since / 12F, 0F, 1F) * Mth.clamp((90F - since) / 12F, 0F, 1F) : 0F;
        if (e.transforming() > 0) flare = Math.max(flare, 0.6F);
        if (e.deathTime > 0) flare = 0;
        float move = Mth.clamp(limbAmount, 0F, 1F);
        ps.pushPose();
        getParentModel().body.translateAndRotate(ps);
        cloak(ps, age, move, flare);
        capelet(ps, age, move, flare);
        collar(ps);
        chain(ps);
        ps.popPose();
    }

    private static float hash(int i) { float s = (float) Math.sin(i * 12.9898F) * 43758.547F; return s - (float) Math.floor(s); }

    /** A point of the long cloak: u across (0 left .. 1 right), v down (0 shoulders .. 1 hem). */
    private float[] point(float u, float v, float t, float move, float flare, int col) {
        float len = 1.36F * (v < 0.999F ? 1F : 0.9F + 0.1F * hash(col));   // a torn hem
        float x = (-0.34F + 0.68F * u) * (1F + flare * v * 1.1F);
        float y = v * len - flare * v * v * 0.25F;
        float ripple = (float) Math.sin(t * 0.11F + u * 5F + v * 2.5F) * 0.035F * v;
        float trail = (float) Math.pow(v, 1.4) * (0.08F + 0.42F * move + 0.75F * flare);
        float z = 0.15F + 0.05F * (float) Math.sin(Math.PI * u) + trail + ripple;
        return new float[]{x, y, z};
    }

    private void cloak(PoseStack ps, float t, float move, float flare) {
        float[][][] g = new float[C + 1][R + 1][];
        for (int i = 0; i <= C; i++) for (int j = 0; j <= R; j++) g[i][j] = point(i / (float) C, j / (float) R, t, move, flare, i);
        for (int i = 0; i < C; i++) for (int j = 0; j < R; j++) {
            float shade = 0.72F + 0.28F * (float) Math.cos((i + 0.5F) / C * Math.PI * 2F) * 0.3F - j * 0.012F;   // folds, and darker toward the hem
            quad(ps, g[i][j], g[i + 1][j], g[i + 1][j + 1], g[i][j + 1], OUT, shade, 0.004F);
            quad(ps, g[i][j + 1], g[i + 1][j + 1], g[i + 1][j], g[i][j], IN, 1F, -0.004F);
        }
        for (int j = 0; j < R; j++) {   // gold thread down both sides
            edge(ps, g[0][j], g[0][j + 1], 0.022F, true);
            edge(ps, g[C][j], g[C][j + 1], 0.022F, true);
        }
        for (int i = 0; i < C; i++) edge(ps, g[i][R], g[i + 1][R], 0.03F, false);   // and along the torn hem
    }

    /** The short capelet over his shoulders, wrapping forward at the ends. */
    private void capelet(PoseStack ps, float t, float move, float flare) {
        int CC = 10, RR = 3;
        float[][][] g = new float[CC + 1][RR + 1][];
        for (int i = 0; i <= CC; i++) for (int j = 0; j <= RR; j++) {
            float u = i / (float) CC, v = j / (float) RR;
            float side = Math.abs(u - 0.5F) * 2F;
            float x = (-0.37F + 0.74F * u) * (1F + flare * 0.25F * v);
            float y = -0.02F + v * 0.36F;
            float z = 0.18F - 0.30F * side * side * side + v * (0.03F + 0.1F * move + 0.2F * flare) + (float) Math.sin(t * 0.13F + u * 4F) * 0.01F * v;
            g[i][j] = new float[]{x, y, z};
        }
        for (int i = 0; i < CC; i++) for (int j = 0; j < RR; j++) {
            quad(ps, g[i][j], g[i + 1][j], g[i + 1][j + 1], g[i][j + 1], CAPE, 0.85F - j * 0.05F, 0.006F);
            quad(ps, g[i][j + 1], g[i + 1][j + 1], g[i + 1][j], g[i][j], IN, 1F, -0.006F);
        }
        for (int i = 0; i < CC; i++) edge(ps, g[i][RR], g[i + 1][RR], 0.025F, false);
    }

    /** A high, stiff collar standing up behind his neck. */
    private void collar(PoseStack ps) {
        int N = 8;
        float[][] lo = new float[N + 1][], hi = new float[N + 1][];
        for (int i = 0; i <= N; i++) {
            float u = i / (float) N, a = (float) Math.PI * (0.12F + 0.76F * u);
            float x = -(float) Math.cos(a) * 0.24F, z = (float) Math.sin(a) * 0.16F + 0.02F;
            lo[i] = new float[]{x, 0.0F, z};
            hi[i] = new float[]{x * 1.15F, -0.24F - 0.04F * (float) Math.sin(Math.PI * u), z * 1.2F + 0.03F};
        }
        for (int i = 0; i < N; i++) {
            quad(ps, lo[i], lo[i + 1], hi[i + 1], hi[i], CAPE, 0.9F, 0.004F);
            quad(ps, hi[i], hi[i + 1], lo[i + 1], lo[i], IN, 1F, -0.004F);
            edge(ps, hi[i], hi[i + 1], 0.02F, false);
        }
    }

    /** A gold chain slung across his chest, holding the cloak on. */
    private void chain(PoseStack ps) {
        int N = 8;
        for (int i = 0; i < N; i++) {
            float u0 = i / (float) N, u1 = (i + 1) / (float) N;
            float[] a = {-0.22F + 0.44F * u0, 0.03F + 0.06F * (float) Math.sin(Math.PI * u0), -0.135F};
            float[] b = {-0.22F + 0.44F * u1, 0.03F + 0.06F * (float) Math.sin(Math.PI * u1), -0.135F};
            edge(ps, a, b, 0.018F, false);
        }
    }

    // ------------------------------------------------------------------ drawing
    private void quad(PoseStack ps, float[] a, float[] b, float[] c, float[] d, float[] col, float shade, float push) {
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();
        float nx = 0, ny = 0, nz = push >= 0 ? 1 : -1;
        float k = Mth.clamp(shade, 0.2F, 1.2F);
        v(m, n, a[0], a[1], a[2] + push, col, k, nx, ny, nz);
        v(m, n, b[0], b[1], b[2] + push, col, k, nx, ny, nz);
        v(m, n, c[0], c[1], c[2] + push, col, k, nx, ny, nz);
        v(m, n, d[0], d[1], d[2] + push, col, k, nx, ny, nz);
    }

    /** A thin gold band between two points (vertical: widened sideways; else widened downward). */
    private void edge(PoseStack ps, float[] a, float[] b, float w, boolean vertical) {
        float[] a2 = vertical ? new float[]{a[0] + w, a[1], a[2]} : new float[]{a[0], a[1] + w, a[2]};
        float[] b2 = vertical ? new float[]{b[0] + w, b[1], b[2]} : new float[]{b[0], b[1] + w, b[2]};
        quad(ps, a, b, b2, a2, GOLD, 1F, 0.008F);
        quad(ps, a2, b2, b, a, GOLD, 0.8F, -0.008F);
    }

    private void v(Matrix4f m, Matrix3f n, float x, float y, float z, float[] c, float k, float nx, float ny, float nz) {
        vc.vertex(m, x, y, z).color(Math.min(1F, c[0] * k), Math.min(1F, c[1] * k), Math.min(1F, c[2] * k), 1F).uv(0.5F, 0.5F)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, nx, ny, nz).endVertex();
    }
}
