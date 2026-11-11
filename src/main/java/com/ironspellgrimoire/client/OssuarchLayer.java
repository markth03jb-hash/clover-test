package com.ironspellgrimoire.client;

import com.ironspellgrimoire.entity.Ossuarch;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * What grows over the Ossuarch as the fight goes on (all original designs, drawn in the body's own space so it moves
 * with every pose):
 * <ul>
 *   <li>Phase 2, Bone Mode: a crown of short back-swept horns, a ribcage plate over the chest, heavy bone pauldrons, a
 *       spine ridge, and a pair of skeletal wings of long finger-bones.</li>
 *   <li>Phase 3, Devil Union: his bone goes dark; a halo of jagged spikes stands behind his head; a long segmented tail;
 *       oversized bone hands over his own; a torn black shroud drifting off his shoulders like smoke; glowing eyes and
 *       red cracks of devil power running over him.</li>
 * </ul>
 * Each grows in over the transformation.
 */
public class OssuarchLayer extends RenderLayer<Ossuarch, PlayerModel<Ossuarch>> {
    private static final ResourceLocation WHITE = new ResourceLocation("iron_spell_grimoire", "textures/effect/white.png");
    private static final Vector3f LIGHT = new Vector3f(0.3F, -0.8F, -0.5F).normalize();

    public OssuarchLayer(RenderLayerParent<Ossuarch, PlayerModel<Ossuarch>> parent) { super(parent); }

    /** A surface: base colour, how dark its shade side goes, a highlight, alpha, and whether it glows. */
    private record Mat(float r, float g, float b, float a, boolean glow) {
        float[] col(float shade) {
            float k = glow ? 1F : 0.45F + 0.55F * shade;
            float hi = !glow && shade > 0.9F ? (shade - 0.9F) * 3F : 0F;
            return new float[]{Math.min(1F, r * k + hi), Math.min(1F, g * k + hi), Math.min(1F, b * k + hi)};
        }
    }

    private static final Mat BONE = new Mat(0.92F, 0.88F, 0.78F, 1F, false);
    private static final Mat DARK_BONE = new Mat(0.16F, 0.13F, 0.14F, 1F, false);
    private static final Mat SHROUD = new Mat(0.03F, 0.02F, 0.03F, 0.88F, false);
    private static final Mat RED = new Mat(1.0F, 0.12F, 0.1F, 1F, true);
    private static final Mat EYE = new Mat(1.0F, 0.25F, 0.2F, 1F, true);

    private VertexConsumer lit, glow;
    private int light;

    @Override
    public void render(PoseStack ps, MultiBufferSource buf, int light, Ossuarch e, float limbSwing, float limbAmount, float pt, float age,
                       float headYaw, float headPitch) {
        int phase = e.phase();
        if (phase < 2 || e.isInvisible()) return;
        this.light = light;
        this.lit = buf.getBuffer(RenderType.entityTranslucent(WHITE));
        this.glow = buf.getBuffer(RenderType.entityTranslucentEmissive(WHITE));
        // grows in over the transformation (70 ticks), and falls away as he dies
        float grow = e.transforming() > 0 ? Mth.clamp((70 - e.transforming() + pt) / 50F, 0F, 1F) : 1F;
        if (e.deathTime > 0) grow *= Math.max(0F, 1F - (e.deathTime + pt) / 50F);
        if (grow <= 0.01F) return;
        PlayerModel<Ossuarch> m = getParentModel();
        double t = age;
        if (phase == 2) boneMode(ps, m, grow, t);
        else devilUnion(ps, m, grow, t);
    }

    // ------------------------------------------------------------------ phase 2: Bone Mode
    private void boneMode(PoseStack ps, PlayerModel<Ossuarch> m, float k, double t) {
        // horns: three per side, sweeping back from the brow
        part(ps, m.head, () -> {
            for (int s = -1; s <= 1; s += 2) {
                for (int h = 0; h < 3; h++) {
                    float x = s * (0.12F + h * 0.07F), y = -0.47F + h * 0.03F;
                    curve(ps, BONE, x, y, -0.15F, new float[]{s * 0.08F, -0.28F + h * 0.06F, 0.18F}, new float[]{s * 0.05F, -0.12F, 0.28F}, 0.04F * k, 0.003F, 0.9F * k - h * 0.15F);
                }
            }
        });
        part(ps, m.body, () -> {
            // the ribcage plate: a sternum and curved ribs across the chest
            tube(ps, BONE, 0, 0.08F, -0.15F, 0, 0.6F * k, -0.15F, 0.035F, 0.035F, 6);
            for (int r = 0; r < 5; r++) {
                float y = 0.12F + r * 0.1F, w = (0.26F - r * 0.02F) * k;
                for (int s = -1; s <= 1; s += 2) curve(ps, BONE, 0, y, -0.15F, new float[]{s * w * 0.7F, 0.02F, -0.03F}, new float[]{s * w, 0.05F, 0.12F}, 0.022F, 0.016F, 1F);
            }
            // a ridge of spines down the back
            for (int v = 0; v < 6; v++) {
                float y = 0.05F + v * 0.11F;
                tube(ps, BONE, 0, y, 0.13F, 0, y - 0.03F, 0.13F + 0.12F * k * (1F - v * 0.1F), 0.035F, 0.004F, 5);
            }
            // wings of long finger-bones, a membrane of nothing between them, slowly breathing
            float flap = (float) Math.sin(t * 0.12) * 0.12F;
            for (int s = -1; s <= 1; s += 2) {
                float[] root = {s * 0.12F, 0.1F, 0.14F};
                float[] arm = {s * (0.55F * k), -0.3F * k - flap, 0.45F * k};
                tube(ps, BONE, root[0], root[1], root[2], arm[0], arm[1], arm[2], 0.04F, 0.03F, 6);
                for (int f = 0; f < 4; f++) {
                    float sp = 0.25F + f * 0.22F;
                    tube(ps, BONE, arm[0], arm[1], arm[2], arm[0] + s * sp * 0.6F * k, arm[1] + (0.2F + f * 0.32F) * k, arm[2] + (0.15F + f * 0.05F) * k, 0.025F, 0.006F, 5);
                }
            }
        });
        // pauldrons: three stacked bone plates on each shoulder
        for (ModelPart arm : new ModelPart[]{m.rightArm, m.leftArm}) {
            float side = arm == m.rightArm ? -1F : 1F;
            part(ps, arm, () -> {
                for (int p = 0; p < 3; p++) {
                    float y = -0.08F + p * 0.07F;
                    tube(ps, BONE, -0.17F, y, 0, 0.17F, y, 0, (0.17F - p * 0.02F) * k, 0.07F * k, 6);
                    tube(ps, BONE, side * 0.12F, y - 0.05F, 0, side * 0.2F, y - 0.2F * k, 0, 0.035F, 0.004F, 5);
                }
            });
        }
    }

    // ------------------------------------------------------------------ phase 3: Devil Union (original form)
    private void devilUnion(PoseStack ps, PlayerModel<Ossuarch> m, float k, double t) {
        part(ps, m.head, () -> {
            // a halo of jagged dark spikes standing in a ring behind the head, slowly turning
            float[] c = {0, -0.28F, 0.32F};
            for (int i = 0; i < 11; i++) {
                double a = i * Math.PI * 2 / 11 + t * 0.01;
                float len = (0.32F + (i % 3) * 0.12F) * k;
                float dx = (float) Math.cos(a), dy = (float) Math.sin(a);
                float bx = c[0] + dx * 0.26F, by = c[1] + dy * 0.26F;
                tube(ps, DARK_BONE, bx, by, c[2], bx + dx * len, by + dy * len, c[2] + 0.04F, 0.04F, 0.003F, 5);
                if (i % 3 == 0) tube(ps, RED, bx + dx * len * 0.2F, by + dy * len * 0.2F, c[2] - 0.005F, bx + dx * len * 0.7F, by + dy * len * 0.7F, c[2] + 0.02F, 0.008F, 0.004F, 4);
            }
            ring(ps, DARK_BONE, c[0], c[1], c[2], 0.26F * k, 0.025F);
            // glowing eyes, cracks of red under them
            float pulse = 0.75F + 0.25F * (float) Math.sin(t * 0.3);
            for (int s = -1; s <= 1; s += 2) {
                quad(ps, EYE, s * 0.15F, -0.27F, -0.255F, 0.055F, 0.022F, pulse * k);
                tube(ps, RED, s * 0.15F, -0.24F, -0.253F, s * 0.17F, -0.08F, -0.253F, 0.008F, 0.003F, 4);
            }
        });
        part(ps, m.body, () -> {
            // cracks of devil power running over the chest
            float fl = 0.7F + 0.3F * (float) Math.sin(t * 0.4);
            line(ps, RED, 0.02F, 0.05F, -0.13F, -0.1F, 0.3F, -0.13F, fl);
            line(ps, RED, -0.1F, 0.3F, -0.13F, 0.05F, 0.55F, -0.13F, fl);
            line(ps, RED, -0.1F, 0.3F, -0.13F, -0.22F, 0.42F, -0.13F, fl);
            line(ps, RED, 0.12F, 0.12F, -0.13F, 0.2F, 0.35F, -0.13F, fl);
            // a long segmented tail, swaying, its tip a hooked blade
            float px = 0, py = 0.66F, pz = 0.15F;
            for (int i = 1; i <= 12; i++) {
                float u = i / 12F;
                float x = (float) Math.sin(t * 0.12 - u * 3.0) * 0.35F * u;
                float y = 0.66F + 0.25F * u - 0.35F * u * u;
                float z = 0.15F + 1.4F * u * k;
                float r = 0.06F * (1F - u * 0.7F);
                tube(ps, DARK_BONE, px, py, pz, x, y, z, r, r * 0.85F, 6);
                if (i % 2 == 0) tube(ps, DARK_BONE, x, y, z, x, y - 0.1F * (1F - u), z, r * 0.6F, 0.003F, 4);
                px = x; py = y; pz = z;
            }
            curve(ps, DARK_BONE, px, py, pz, new float[]{0.05F, -0.15F, 0.1F}, new float[]{0.0F, -0.3F, -0.05F}, 0.05F, 0.003F, k);
            // the torn shroud: long ragged strips off the shoulders, drifting and fading at the ends like smoke
            for (int s = 0; s < 9; s++) {
                float u0 = s / 9F, x0 = -0.3F + u0 * 0.6F;
                float len = (0.9F + 0.35F * (float) Math.sin(s * 2.3)) * k;
                float sway = (float) Math.sin(t * 0.15 + s) * 0.08F;
                for (int r = 0; r < 6; r++) {
                    float v0 = r / 6F, v1 = (r + 1) / 6F;
                    float w = 0.034F * (1F - v1 * 0.6F);
                    float y0 = 0.02F + v0 * len, y1 = 0.02F + v1 * len;
                    float z0 = 0.14F + v0 * v0 * 0.35F + sway * v0, z1 = 0.14F + v1 * v1 * 0.35F + sway * v1;
                    float a = SHROUD.a() * (1F - v1 * 0.75F);
                    strip(ps, x0 - w, y0, z0, x0 + w, y0, z0, x0 + w * 0.8F, y1, z1, x0 - w * 0.8F, y1, z1, a);
                }
            }
        });
        // oversized bone hands over his own: a broad palm and four long hooked fingers, a thumb
        for (ModelPart arm : new ModelPart[]{m.rightArm, m.leftArm}) {
            float side = arm == m.rightArm ? -1F : 1F;
            part(ps, arm, () -> {
                tube(ps, DARK_BONE, side * 0.06F, 0.45F, 0, side * 0.06F, 0.7F, 0, 0.11F * k, 0.13F * k, 6);
                for (int f = 0; f < 4; f++) {
                    float fx = side * 0.06F + (f - 1.5F) * 0.055F;
                    curve(ps, DARK_BONE, fx, 0.7F, -0.02F, new float[]{(f - 1.5F) * 0.03F, 0.22F * k, -0.05F}, new float[]{0, 0.12F * k, -0.12F * k}, 0.03F, 0.004F, 1F);
                }
                curve(ps, DARK_BONE, side * 0.06F - side * 0.1F, 0.6F, -0.06F, new float[]{-side * 0.1F, 0.08F, -0.06F}, new float[]{-side * 0.02F, 0.1F, -0.08F}, 0.03F, 0.004F, k);
                line(ps, RED, side * 0.06F, 0.48F, -0.12F, side * 0.06F, 0.66F, -0.13F, 0.8F);
            });
        }
    }

    // ------------------------------------------------------------------ drawing helpers (in the model part's own space: +y down, -z front)
    private void part(PoseStack ps, ModelPart p, Runnable r) {
        ps.pushPose();
        p.translateAndRotate(ps);
        r.run();
        ps.popPose();
    }

    private void tube(PoseStack ps, Mat mat, float ax, float ay, float az, float bx, float by, float bz, float ra, float rb, int sides) {
        Vector3f d = new Vector3f(bx - ax, by - ay, bz - az);
        float len = d.length();
        if (len < 1.0E-4F || (ra <= 0 && rb <= 0)) return;
        d.div(len);
        Vector3f ref = Math.abs(d.y) > 0.9F ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0);
        Vector3f e1 = new Vector3f(d).cross(ref).normalize(), e2 = new Vector3f(d).cross(e1).normalize();
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();
        VertexConsumer vc = mat.glow() ? glow : lit;
        for (int i = 0; i < sides; i++) {
            double t0 = Math.PI * 2 * i / sides, t1 = Math.PI * 2 * (i + 1) / sides, tm = (t0 + t1) * 0.5;
            Vector3f nm = new Vector3f(e1).mul((float) Math.cos(tm)).add(new Vector3f(e2).mul((float) Math.sin(tm)));
            float[] c = mat.col(Mth.clamp(0.5F + 0.5F * -nm.dot(LIGHT), 0F, 1F));
            float c0 = (float) Math.cos(t0), s0 = (float) Math.sin(t0), c1 = (float) Math.cos(t1), s1 = (float) Math.sin(t1);
            v(vc, m, n, ax + (e1.x * c0 + e2.x * s0) * ra, ay + (e1.y * c0 + e2.y * s0) * ra, az + (e1.z * c0 + e2.z * s0) * ra, c, mat.a(), nm);
            v(vc, m, n, ax + (e1.x * c1 + e2.x * s1) * ra, ay + (e1.y * c1 + e2.y * s1) * ra, az + (e1.z * c1 + e2.z * s1) * ra, c, mat.a(), nm);
            v(vc, m, n, bx + (e1.x * c1 + e2.x * s1) * rb, by + (e1.y * c1 + e2.y * s1) * rb, bz + (e1.z * c1 + e2.z * s1) * rb, c, mat.a(), nm);
            v(vc, m, n, bx + (e1.x * c0 + e2.x * s0) * rb, by + (e1.y * c0 + e2.y * s0) * rb, bz + (e1.z * c0 + e2.z * s0) * rb, c, mat.a(), nm);
        }
    }

    /** A tapering curved bone from (x,y,z) through two offsets (a quadratic bend), {@code k} of the way grown. */
    private void curve(PoseStack ps, Mat mat, float x, float y, float z, float[] mid, float[] end, float r0, float r1, float k) {
        if (k <= 0.01F) return;
        float px = x, py = y, pz = z;
        for (int i = 1; i <= 6; i++) {
            float u = i / 6F * k;
            float qx = x + 2 * u * (1 - u) * mid[0] + u * u * (mid[0] + end[0]);
            float qy = y + 2 * u * (1 - u) * mid[1] + u * u * (mid[1] + end[1]);
            float qz = z + 2 * u * (1 - u) * mid[2] + u * u * (mid[2] + end[2]);
            float ra = r0 + (r1 - r0) * ((i - 1) / 6F), rb = r0 + (r1 - r0) * (i / 6F);
            tube(ps, mat, px, py, pz, qx, qy, qz, ra, rb, 5);
            px = qx; py = qy; pz = qz;
        }
    }

    private void ring(PoseStack ps, Mat mat, float x, float y, float z, float r, float th) {
        for (int i = 0; i < 16; i++) {
            double a0 = Math.PI * 2 * i / 16, a1 = Math.PI * 2 * (i + 1) / 16;
            tube(ps, mat, x + (float) Math.cos(a0) * r, y + (float) Math.sin(a0) * r, z, x + (float) Math.cos(a1) * r, y + (float) Math.sin(a1) * r, z, th, th, 4);
        }
    }

    private void line(PoseStack ps, Mat mat, float ax, float ay, float az, float bx, float by, float bz, float k) {
        tube(ps, mat, ax, ay, az, ax + (bx - ax) * k, ay + (by - ay) * k, az + (bz - az) * k, 0.008F, 0.006F, 4);
    }

    private void quad(PoseStack ps, Mat mat, float cx, float cy, float z, float w, float h, float a) {
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();
        float[] c = mat.col(1F);
        Vector3f nm = new Vector3f(0, 0, -1);
        v(glow, m, n, cx - w, cy - h, z, c, a, nm);
        v(glow, m, n, cx - w, cy + h, z, c, a, nm);
        v(glow, m, n, cx + w, cy + h, z, c, a, nm);
        v(glow, m, n, cx + w, cy - h, z, c, a, nm);
    }

    private void strip(PoseStack ps, float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz, float a) {
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();
        float[] c = {SHROUD.r(), SHROUD.g(), SHROUD.b()};
        Vector3f nm = new Vector3f(0, 0, 1);
        v(lit, m, n, ax, ay, az, c, a, nm); v(lit, m, n, bx, by, bz, c, a, nm); v(lit, m, n, cx, cy, cz, c, a, nm); v(lit, m, n, dx, dy, dz, c, a, nm);
        v(lit, m, n, dx, dy, dz, c, a, nm); v(lit, m, n, cx, cy, cz, c, a, nm); v(lit, m, n, bx, by, bz, c, a, nm); v(lit, m, n, ax, ay, az, c, a, nm);
    }

    private void v(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, float[] c, float a, Vector3f nm) {
        vc.vertex(m, x, y, z).color(c[0], c[1], c[2], a).uv(0.5F, 0.5F).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(vc == glow ? 15728880 : light).normal(n, nm.x, nm.y, nm.z).endVertex();
    }
}
