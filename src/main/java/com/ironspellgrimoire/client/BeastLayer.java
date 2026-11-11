package com.ironspellgrimoire.client;

import com.ironspellgrimoire.entity.BeastOfDespair;
import com.ironspellgrimoire.server.BeastAI;
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
 * What BeastOfDespair's Beast Magic puts on him (original designs, drawn in the body's own space so they move with every pose):
 * <ul>
 *   <li>Always: three long ivory claws on each hand, glowing at the roots with beast mana.</li>
 *   <li>Rhinoceros Armor: heavy grey plates close over his shoulders, chest and back, and a horn over his brow.</li>
 *   <li>Phase 2 (Mythical Beast): a third eye opens on his forehead; a mane of beast mana streams off his head and back,
 *       and the claws grow.</li>
 *   <li>Phase 3 (Despair): the mana turns red at the edges and veins of it run over him; gathering the Final Despair,
 *       his whole body cracks with light.</li>
 * </ul>
 */
public class BeastLayer extends RenderLayer<BeastOfDespair, PlayerModel<BeastOfDespair>> {
    private static final ResourceLocation WHITE = new ResourceLocation("iron_spell_grimoire", "textures/effect/white.png");
    private static final Vector3f LIGHT = new Vector3f(0.3F, -0.8F, -0.5F).normalize();

    public BeastLayer(RenderLayerParent<BeastOfDespair, PlayerModel<BeastOfDespair>> parent) { super(parent); }

    private record Mat(float r, float g, float b, float a, boolean glow) {
        float[] col(float shade) {
            float k = glow ? 1F : 0.45F + 0.55F * shade;
            float hi = !glow && shade > 0.9F ? (shade - 0.9F) * 3F : 0F;
            return new float[]{Math.min(1F, r * k + hi), Math.min(1F, g * k + hi), Math.min(1F, b * k + hi)};
        }
    }

    private static final Mat CLAW = new Mat(0.93F, 0.9F, 0.8F, 1F, false);
    private static final Mat PLATE = new Mat(0.42F, 0.44F, 0.46F, 1F, false);
    private static final Mat FUR = new Mat(0.1F, 0.55F, 0.45F, 0.55F, false);
    private static final Mat MANA = new Mat(0.35F, 1.0F, 0.75F, 0.85F, true);
    private static final Mat RAGE = new Mat(1.0F, 0.2F, 0.15F, 0.9F, true);
    private static final Mat EYE = new Mat(0.7F, 1.0F, 0.85F, 1F, true);

    private VertexConsumer lit, glow;
    private int light;

    @Override
    public void render(PoseStack ps, MultiBufferSource buf, int light, BeastOfDespair e, float limbSwing, float limbAmount, float pt, float age,
                       float headYaw, float headPitch) {
        if (e.isInvisible()) return;
        this.light = light;
        this.lit = buf.getBuffer(RenderType.entityTranslucent(WHITE));
        this.glow = buf.getBuffer(RenderType.entityTranslucentEmissive(WHITE));
        PlayerModel<BeastOfDespair> m = getParentModel();
        int phase = e.phase();
        float fade = e.deathTime > 0 ? Math.max(0F, 1F - (e.deathTime + pt) / 50F) : 1F;
        float grow = e.transforming() > 0 ? Mth.clamp((60 - e.transforming() + pt) / 45F, 0F, 1F) : 1F;
        double t = age;
        claws(ps, m, phase >= 2 ? 1F + 0.35F * grow : 1F, phase, t, fade);
        if (e.armored() > 0) armor(ps, m, Mth.clamp((100 - e.armored() + pt) / 6F, 0F, 1F) * Mth.clamp((e.armored() - pt) / 6F, 0F, 1F));
        if (phase >= 2) mythic(ps, m, (phase == 2 ? grow : 1F) * fade, phase == 3, t);
        if (e.charging() > 0) cracks(ps, m, Mth.clamp((e.charging() + pt) / 120F, 0F, 1F), t);
    }

    private void claws(PoseStack ps, PlayerModel<BeastOfDespair> m, float size, int phase, double t, float fade) {
        if (fade <= 0.01F) return;
        Mat root = phase == 3 ? RAGE : MANA;
        for (ModelPart arm : new ModelPart[]{m.rightArm, m.leftArm}) {
            float side = arm == m.rightArm ? -1F : 1F;
            part(ps, arm, () -> {
                // out of the knuckles: rooted in the front of the fist, reaching past the fingers and curling forward
                float cx = side * 0.0625F;
                for (int f = 0; f < 3; f++) {
                    float x = cx + (f - 1) * 0.06F;
                    float len = (0.32F + (f == 1 ? 0.06F : 0F)) * size * fade;
                    tube(ps, root, x, 0.5F, -0.13F, x, 0.6F, -0.135F, 0.03F * fade, 0.026F * fade, 5);   // the glowing root on the knuckle
                    curve(ps, CLAW, x, 0.52F, -0.13F, new float[]{(f - 1) * 0.015F, len * 0.55F, -0.05F}, new float[]{0, len * 0.4F, -len * 0.45F}, 0.032F, 0.003F, 1F);
                }
            });
        }
    }

    private void armor(PoseStack ps, PlayerModel<BeastOfDespair> m, float k) {
        if (k <= 0.01F) return;
        part(ps, m.head, () -> {   // a horn over the brow
            curve(ps, PLATE, 0, -0.42F, -0.27F, new float[]{0, -0.12F * k, -0.08F}, new float[]{0, -0.12F * k, 0.05F}, 0.07F * k, 0.004F, 1F);
            tube(ps, PLATE, -0.26F, -0.3F, -0.27F, 0.26F, -0.3F, -0.27F, 0.04F * k, 0.04F * k, 6);
        });
        part(ps, m.body, () -> {   // overlapping plates down the chest and back
            for (int r = 0; r < 4; r++) {
                float y = 0.06F + r * 0.17F;
                tube(ps, PLATE, -0.27F, y, -0.16F, 0.27F, y, -0.16F, 0.075F * k, 0.075F * k, 6);
                tube(ps, PLATE, -0.27F, y, 0.16F, 0.27F, y, 0.16F, 0.08F * k, 0.08F * k, 6);
            }
        });
        for (ModelPart arm : new ModelPart[]{m.rightArm, m.leftArm}) {
            part(ps, arm, () -> {
                tube(ps, PLATE, -0.18F, -0.06F, 0, 0.18F, -0.06F, 0, 0.19F * k, 0.15F * k, 7);
                tube(ps, PLATE, -0.14F, 0.42F, 0, 0.14F, 0.42F, 0, 0.15F * k, 0.15F * k, 6);
            });
        }
    }

    private void mythic(PoseStack ps, PlayerModel<BeastOfDespair> m, float k, boolean rage, double t) {
        if (k <= 0.01F) return;
        Mat edge = rage ? RAGE : MANA;
        float pulse = 0.75F + 0.25F * (float) Math.sin(t * 0.35);
        part(ps, m.head, () -> {
            quad(ps, rage ? RAGE : EYE, 0, -0.38F, -0.256F, 0.03F * k, 0.06F * k, pulse);   // the third eye
            quad(ps, EYE, 0, -0.38F, -0.258F, 0.012F * k, 0.04F * k, 1F);
            for (int i = 0; i < 9; i++) {   // the mane: streaming back off the crown
                float x = -0.24F + i * 0.06F;
                float sway = (float) Math.sin(t * 0.2 + i) * 0.06F;
                curve(ps, edge, x, -0.5F, 0.0F, new float[]{x * 0.4F, -0.15F * k, 0.2F}, new float[]{sway, 0.25F * k, 0.4F * k}, 0.035F, 0.003F, k);
            }
        });
        part(ps, m.body, () -> {
            for (int i = 0; i < 7; i++) {   // and down the back
                float x = -0.2F + i * 0.066F;
                float sway = (float) Math.sin(t * 0.18 + i * 1.3) * 0.08F;
                curve(ps, edge, x, 0.0F, 0.14F, new float[]{x * 0.3F, 0.1F, 0.18F * k}, new float[]{sway, 0.35F * k, 0.25F * k}, 0.03F, 0.003F, k);
            }
            if (rage) {   // veins of red mana
                line(ps, RAGE, -0.18F, 0.05F, -0.13F, -0.05F, 0.4F, -0.13F, pulse);
                line(ps, RAGE, 0.18F, 0.05F, -0.13F, 0.06F, 0.45F, -0.13F, pulse);
                line(ps, RAGE, -0.05F, 0.4F, -0.13F, 0.04F, 0.7F, -0.13F, pulse);
            }
        });
    }

    private void cracks(PoseStack ps, PlayerModel<BeastOfDespair> m, float k, double t) {
        float fl = (0.6F + 0.4F * (float) Math.sin(t * (0.5 + k))) * (0.4F + 0.6F * k);
        part(ps, m.body, () -> {
            for (int i = 0; i < 6; i++) {
                float x0 = -0.2F + i * 0.08F, y0 = 0.1F + (i % 3) * 0.2F;
                line(ps, MANA, x0, y0, -0.13F, x0 + 0.06F, y0 + 0.15F, -0.13F, fl);
                line(ps, RAGE, x0, y0, 0.13F, x0 - 0.05F, y0 + 0.18F, 0.13F, fl);
            }
        });
        part(ps, m.head, () -> {
            line(ps, MANA, -0.2F, -0.1F, -0.255F, -0.05F, -0.3F, -0.255F, fl);
            line(ps, MANA, 0.2F, -0.1F, -0.255F, 0.06F, -0.28F, -0.255F, fl);
        });
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
        float[] c = {FUR.r(), FUR.g(), FUR.b()};
        Vector3f nm = new Vector3f(0, 0, 1);
        v(lit, m, n, ax, ay, az, c, a, nm); v(lit, m, n, bx, by, bz, c, a, nm); v(lit, m, n, cx, cy, cz, c, a, nm); v(lit, m, n, dx, dy, dz, c, a, nm);
        v(lit, m, n, dx, dy, dz, c, a, nm); v(lit, m, n, cx, cy, cz, c, a, nm); v(lit, m, n, bx, by, bz, c, a, nm); v(lit, m, n, ax, ay, az, c, a, nm);
    }

    private void v(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, float[] c, float a, Vector3f nm) {
        vc.vertex(m, x, y, z).color(c[0], c[1], c[2], a).uv(0.5F, 0.5F).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(vc == glow ? 15728880 : light).normal(n, nm.x, nm.y, nm.z).endVertex();
    }
}
