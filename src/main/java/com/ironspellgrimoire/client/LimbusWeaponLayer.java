package com.ironspellgrimoire.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/**
 * The weapons of the two Limbus-inspired grimoires, held in the hands and drawn in the arms' own space so they follow
 * every frame of the animation: Don Quixote's hardblood (glossy dark blood with a wet red sheen) and Rien's Caduceus
 * (liquid gold that pours out of the vial and sets into the weapon, a ripple of light running along it).
 * Fed by FxPacket shape 63: id = player, life = ticks, sweep = weapon kind, palette 0 = blood, 1 = gold.
 */
@EventBusSubscriber(modid = "iron_spell_grimoire", value = {Dist.CLIENT})
public class LimbusWeaponLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    public static final int LANCE = 1, BOW = 2, TWIN = 3, FISTS = 4, GREATSWORD = 5, VIAL = 10, WHIP = 11, RAPIER = 12, SCYTHE = 13,
            SPEAR = 14, HAMMER = 15, DAGGERS = 16, GUN = 17, FURIOSO = 18;
    private static final ResourceLocation WHITE = new ResourceLocation("iron_spell_grimoire", "textures/effect/white.png");
    /** player id -> {start, end, kind, gold} */
    private static final Map<Integer, double[]> ACTIVE = new HashMap<>();

    public LimbusWeaponLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) { super(parent); }

    public static void set(int id, int life, int kind, boolean gold) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        double now = mc.level.getGameTime();
        if (life <= 0) ACTIVE.remove(id);
        else ACTIVE.put(id, new double[]{now, now + life, kind, gold ? 1 : 0});
    }

    private static float grow(double[] f, double now) {
        return (float) Mth.clamp((now - f[0]) / 4.0, 0.0, 1.0) * (float) Mth.clamp((f[1] + 3 - now) / 3.0, 0.0, 1.0);
    }

    /** Furioso: a new weapon every few ticks - every form the Caduceus knows, one after another. */
    private static int kindAt(double[] f, double now) {
        int k = (int) f[2];
        if (k != FURIOSO) return k;
        int[] seq = {RAPIER, SCYTHE, SPEAR, HAMMER, DAGGERS, GUN, GREATSWORD, WHIP, LANCE};
        return seq[Math.floorMod((int) ((now - f[0]) / 5.0), seq.length)];
    }

    // ------------------------------------------------------------------ third person
    @Override
    public void render(PoseStack ps, MultiBufferSource buf, int light, AbstractClientPlayer p, float limbSwing, float limbSwingAmount,
                       float pt, float age, float netHeadYaw, float headPitch) {
        double[] f = ACTIVE.get(p.getId());
        if (f == null || p.isInvisible()) return;
        double now = p.level().getGameTime() + pt;
        if (now > f[1] + 3) { ACTIVE.remove(p.getId()); return; }
        float g = grow(f, now);
        if (g <= 0.01F) return;
        int kind = kindAt(f, now);
        boolean gold = f[3] > 0.5;
        VertexConsumer vc = buf.getBuffer(RenderType.entityTranslucentEmissive(WHITE));
        PlayerModel<AbstractClientPlayer> m = getParentModel();
        hand(ps, vc, m.rightArm, -1.0F / 16.0F, kind, gold, g, now, true);
        if (twoHanded(kind)) hand(ps, vc, m.leftArm, 1.0F / 16.0F, kind, gold, g, now, false);
        else if (gold && kind != VIAL) hand(ps, vc, m.leftArm, 1.0F / 16.0F, VIAL, true, 1F, now, false);   // Rien keeps the Caduceus rods in the other hand
    }

    private static boolean twoHanded(int k) { return k == TWIN || k == FISTS || k == DAGGERS || k == BOW; }

    private static void hand(PoseStack ps, VertexConsumer vc, ModelPart arm, float x, int kind, boolean gold, float g, double now, boolean right) {
        ps.pushPose();
        arm.translateAndRotate(ps);
        ps.translate(x, 0.6F, -0.03F);   // in the fist
        draw(ps, vc, kind, gold, g, now, right);
        ps.popPose();
    }

    // ------------------------------------------------------------------ first person
    @SubscribeEvent
    public static void onHand(RenderHandEvent e) {
        if (e.getHand() != InteractionHand.MAIN_HAND) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        double[] f = ACTIVE.get(mc.player.getId());
        if (f == null) return;
        double now = mc.level.getGameTime() + e.getPartialTick();
        if (now > f[1] + 3) return;
        float g = grow(f, now);
        if (g <= 0.01F) return;
        int kind = kindAt(f, now);
        boolean gold = f[3] > 0.5;
        double t = now - f[0];
        float thrust = (float) Math.max(0, Math.sin(t * 0.9)) * 0.12F;   // a little forward jab in time with the swings
        VertexConsumer vc = e.getMultiBufferSource().getBuffer(RenderType.entityTranslucentEmissive(WHITE));
        PoseStack ps = e.getPoseStack();
        for (int side = 0; side < (twoHanded(kind) ? 2 : 1); side++) {
            ps.pushPose();
            ps.translate(side == 0 ? 0.5F : -0.5F, -0.48F, -0.72F - thrust);
            ps.mulPose(Axis.YP.rotationDegrees(side == 0 ? -8F : 8F));
            ps.mulPose(Axis.XP.rotationDegrees(180.0F + (kind == BOW ? 90F : 12F)));   // arm space: +y down, blade toward -z
            ps.scale(0.8F, 0.8F, 0.8F);
            draw(ps, vc, kind, gold, g, now, side == 0);
            ps.popPose();
        }
    }

    // ------------------------------------------------------------------ the weapons (forward = -z out of the fist, +y = down the arm, x = across the fist)
    private static void draw(PoseStack ps, VertexConsumer vc, int kind, boolean gold, float g, double now, boolean right) {
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();
        Mat mat = new Mat(gold ? 1 : 0, now);
        float L = g;   // everything grows out of the fist (gold pours out of the vial)
        switch (kind) {
            case LANCE -> {
                // Don Quixote's lance: a long fluted cone with a round vamplate guarding the hand, the grip behind it
                tube(vc, m, n, mat, 0, 0, 0.28F, 0, 0, -0.05F, 0.05F, 0.05F, 0.05F, 0.05F, 6);                      // grip (behind the fist)
                tube(vc, m, n, mat, 0, 0, -0.05F, 0, 0, -0.18F, 0.06F, 0.06F, 0.34F, 0.34F, 12);                   // vamplate, flaring
                tube(vc, m, n, mat, 0, 0, -0.18F, 0, 0, -0.22F, 0.34F, 0.34F, 0.12F, 0.12F, 12);
                tube(vc, m, n, mat, 0, 0, -0.22F, 0, 0, -0.22F - 2.6F * L, 0.12F, 0.12F, 0.0F, 0.0F, 8);           // the cone
                ring(vc, m, n, mat, 0, 0, -0.22F - 0.8F * L, 0.085F, 0.02F);
                ring(vc, m, n, mat, 0, 0, -0.22F - 1.5F * L, 0.055F, 0.015F);
            }
            case BOW -> {
                // a recurved bow of hardblood held across the fist, the string a thin red line
                if (!right) {
                    float R = 0.75F * L;
                    float[] prev = null;
                    for (int i = 0; i <= 16; i++) {
                        double a = -1.25 + 2.5 * i / 16.0;
                        float bx = (float) (Math.sin(a) * R), bz = (float) (-0.35F + (1 - Math.cos(a)) * R * 0.55F);
                        float rec = Math.abs(i - 8) > 6 ? (float) (0.06 * Math.signum(i - 8)) : 0F;
                        if (prev != null) tube(vc, m, n, mat, prev[0], 0, prev[1], bx + rec, 0, bz - Math.abs(rec), 0.035F, 0.05F, 0.035F, 0.05F, 6);
                        prev = new float[]{bx + rec, bz - Math.abs(rec)};
                    }
                    float ex = (float) (Math.sin(1.25) * R) + 0.06F, ez = (float) (-0.35F + (1 - Math.cos(1.25)) * R * 0.55F) - 0.06F;
                    tube(vc, m, n, mat, -ex, 0, ez, ex, 0, ez, 0.006F, 0.006F, 0.006F, 0.006F, 4);
                } else {
                    tube(vc, m, n, mat, 0, 0, 0.05F, 0, 0, -0.9F * L, 0.02F, 0.02F, 0.02F, 0.02F, 5);   // the nocked arrow
                    tube(vc, m, n, mat, 0, 0, -0.9F * L, 0, 0, -1.05F * L, 0.05F, 0.02F, 0F, 0F, 4);
                }
            }
            case TWIN -> {
                // two curved blood sabres, edge out
                curvedBlade(vc, m, n, mat, 1.1F * L, 0.09F, right ? 0.25F : -0.25F);
                tube(vc, m, n, mat, -0.12F, 0, -0.04F, 0.12F, 0, -0.04F, 0.03F, 0.03F, 0.03F, 0.03F, 6);
            }
            case FISTS -> {
                // the fist sheathed in hardblood: a heavy knuckle-guard with spikes along the knuckles
                tube(vc, m, n, mat, 0, -0.15F, 0, 0, 0.08F, 0, 0.16F * L, 0.14F * L, 0.16F * L, 0.14F * L, 8);
                for (int k = 0; k < 4; k++) {
                    float kx = -0.1F + k * 0.066F;
                    tube(vc, m, n, mat, kx, 0.05F, -0.12F, kx, 0.12F, -0.12F - 0.2F * L, 0.035F, 0.035F, 0F, 0F, 5);
                }
            }
            case GREATSWORD -> {
                // a broad two-handed blade with a crossguard and a pommel
                tube(vc, m, n, mat, 0, 0, 0.32F, 0, 0, -0.02F, 0.04F, 0.04F, 0.04F, 0.04F, 6);
                sphere(vc, m, n, mat, 0, 0, 0.34F, 0.07F);
                tube(vc, m, n, mat, -0.34F, 0, -0.04F, 0.34F, 0, -0.04F, 0.05F, 0.05F, 0.05F, 0.05F, 6);
                blade(vc, m, n, mat, -0.06F, -0.06F - 2.2F * L, 0.18F, 0.1F, 0.035F);
            }
            case VIAL -> vial(vc, m, n, mat, now);
            case WHIP -> {
                // a whip-sword: blade segments on a chain, swaying and lashing out in a wave
                float px = 0, py = 0, pz = 0;
                for (int i = 1; i <= 10; i++) {
                    double s = i / 10.0;
                    float x = (float) (Math.sin(now * 0.8 - s * 4.0) * 0.25 * s);
                    float y = (float) (Math.cos(now * 0.6 - s * 3.0) * 0.12 * s);
                    float z = (float) (-s * 2.4 * L);
                    tube(vc, m, n, mat, px, py, pz, x, y, z, 0.07F * (float) (1.1 - s), 0.015F, 0.06F * (float) (1.1 - s), 0.015F, 4);
                    px = x; py = y; pz = z;
                }
            }
            case RAPIER -> {
                // a thin straight blade with a swept ring guard round the hand
                tube(vc, m, n, mat, 0, 0, 0.2F, 0, 0, -0.04F, 0.035F, 0.035F, 0.035F, 0.035F, 6);
                ring(vc, m, n, mat, 0, 0, -0.06F, 0.16F, 0.02F);
                tube(vc, m, n, mat, 0, 0, -0.06F, 0, 0, -0.08F - 1.7F * L, 0.03F, 0.01F, 0.004F, 0.004F, 4);
            }
            case SCYTHE -> {
                // a long shaft and a great curved blade at its head, edge inward
                tube(vc, m, n, mat, 0, 0, 0.9F, 0, 0, -1.4F * L, 0.035F, 0.035F, 0.035F, 0.035F, 6);
                float hz = -1.4F * L;
                float[] prev = {0, hz};
                for (int i = 1; i <= 10; i++) {
                    double a = i / 10.0 * 2.3;
                    float bx = (float) (Math.sin(a) * 0.75 * L), bz = (float) (hz + (1 - Math.cos(a)) * 0.6 * L);
                    float w = 0.16F * (float) (1.0 - i / 11.0);
                    tube(vc, m, n, mat, prev[0], 0, prev[1], bx, 0, bz, 0.02F, w, 0.02F, w * 0.9F, 4);
                    prev = new float[]{bx, bz};
                }
            }
            case SPEAR -> {
                tube(vc, m, n, mat, 0, 0, 0.7F, 0, 0, -1.6F * L, 0.035F, 0.035F, 0.035F, 0.035F, 6);
                blade(vc, m, n, mat, -1.6F * L, -2.1F * L, 0.12F, 0.0F, 0.03F);
                ring(vc, m, n, mat, 0, 0, -1.6F * L, 0.06F, 0.02F);
            }
            case HAMMER -> {
                tube(vc, m, n, mat, 0, 0, 0.3F, 0, 0, -1.2F * L, 0.04F, 0.04F, 0.04F, 0.04F, 6);
                tube(vc, m, n, mat, -0.36F * L, 0, -1.25F * L, 0.36F * L, 0, -1.25F * L, 0.2F * L, 0.24F * L, 0.2F * L, 0.24F * L, 8);
                ring(vc, m, n, mat, -0.36F * L, 0, -1.25F * L, 0.2F * L, 0.03F);
            }
            case DAGGERS -> {
                tube(vc, m, n, mat, 0, 0, 0.12F, 0, 0, -0.03F, 0.03F, 0.03F, 0.03F, 0.03F, 5);
                tube(vc, m, n, mat, -0.1F, 0, -0.04F, 0.1F, 0, -0.04F, 0.025F, 0.025F, 0.025F, 0.025F, 5);
                blade(vc, m, n, mat, -0.05F, -0.05F - 0.6F * L, 0.07F, 0.0F, 0.02F);
            }
            case GUN -> {
                // a long-barrelled pistol cast in gold
                tube(vc, m, n, mat, 0, -0.04F, 0.05F, 0, 0.18F, 0.12F, 0.05F, 0.05F, 0.05F, 0.05F, 4);            // grip
                tube(vc, m, n, mat, 0, -0.08F, 0.12F, 0, -0.08F, -0.75F * L, 0.06F, 0.07F, 0.05F, 0.06F, 6);    // barrel
                ring(vc, m, n, mat, 0, -0.08F, -0.75F * L, 0.06F, 0.02F);
            }
            default -> { }
        }
    }

    // ------------------------------------------------------------------ materials
    /** Blood: glossy near-black red with a wet highlight. Gold: liquid gold with a ripple of light along it. */
    private record Mat(int style, double now) {
        float[] col(float shade, float along) {
            if (style == 1) {
                float rip = (float) Math.max(0, Math.sin(now * 0.45 - along * 6.0)) * 0.35F;
                float k = 0.45F + 0.55F * shade + rip;
                return new float[]{Math.min(1F, 0.98F * k), Math.min(1F, 0.72F * k + rip * 0.3F), Math.min(1F, 0.2F * k + rip * 0.5F)};
            }
            if (style == 2 || style == 3) {   // the Caduceus rods: black lacquer, and white enamel
                float spec = shade > 0.88F ? (shade - 0.88F) * 6F : 0F;
                float base = style == 2 ? 0.1F : 0.78F, k = 0.45F + 0.55F * shade;
                float v = Math.min(1F, base * k + spec * 0.5F);
                return new float[]{v, v, Math.min(1F, v * 1.02F)};
            }
            float spec = shade > 0.9F ? (shade - 0.9F) * 8F : 0F;
            float k = 0.25F + 0.75F * shade;
            return new float[]{Math.min(1F, 0.62F * k + spec * 0.4F), Math.min(1F, 0.02F * k + spec * 0.3F), Math.min(1F, 0.06F * k + spec * 0.3F)};
        }
    }

    private static final Vector3f LIGHT = new Vector3f(0.3F, -0.8F, -0.5F).normalize();

    private static void v(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, float[] c, float nx, float ny, float nz) {
        vc.vertex(m, x, y, z).color(c[0], c[1], c[2], 1F).uv(0.5F, 0.5F).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(15728880).normal(n, nx, ny, nz).endVertex();
    }

    /** A tapered tube from a to b, its cross-section an ellipse (ra x rb at a, ra2 x rb2 at b). */
    private static void tube(VertexConsumer vc, Matrix4f m, Matrix3f n, Mat mat, float ax, float ay, float az, float bx, float by, float bz,
                             float ra, float rb, float ra2, float rb2, int sides) {
        Vector3f d = new Vector3f(bx - ax, by - ay, bz - az);
        float len = d.length();
        if (len < 1.0E-4F) return;
        d.div(len);
        Vector3f ref = Math.abs(d.y) > 0.9F ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0);
        Vector3f e1 = new Vector3f(d).cross(ref).normalize(), e2 = new Vector3f(d).cross(e1).normalize();
        for (int i = 0; i < sides; i++) {
            double t0 = Math.PI * 2 * i / sides, t1 = Math.PI * 2 * (i + 1) / sides, tm = (t0 + t1) * 0.5;
            Vector3f nm = new Vector3f(e1).mul((float) Math.cos(tm)).add(new Vector3f(e2).mul((float) Math.sin(tm)));
            float shade = Mth.clamp(0.5F + 0.5F * -nm.dot(LIGHT), 0F, 1F);
            float[] c0 = mat.col(shade, 0F), c1 = mat.col(shade, len);
            float c0x = (float) Math.cos(t0), s0 = (float) Math.sin(t0), c1x = (float) Math.cos(t1), s1 = (float) Math.sin(t1);
            v(vc, m, n, ax + e1.x * ra * c0x + e2.x * rb * s0, ay + e1.y * ra * c0x + e2.y * rb * s0, az + e1.z * ra * c0x + e2.z * rb * s0, c0, nm.x, nm.y, nm.z);
            v(vc, m, n, ax + e1.x * ra * c1x + e2.x * rb * s1, ay + e1.y * ra * c1x + e2.y * rb * s1, az + e1.z * ra * c1x + e2.z * rb * s1, c0, nm.x, nm.y, nm.z);
            v(vc, m, n, bx + e1.x * ra2 * c1x + e2.x * rb2 * s1, by + e1.y * ra2 * c1x + e2.y * rb2 * s1, bz + e1.z * ra2 * c1x + e2.z * rb2 * s1, c1, nm.x, nm.y, nm.z);
            v(vc, m, n, bx + e1.x * ra2 * c0x + e2.x * rb2 * s0, by + e1.y * ra2 * c0x + e2.y * rb2 * s0, bz + e1.z * ra2 * c0x + e2.z * rb2 * s0, c1, nm.x, nm.y, nm.z);
        }
    }

    /** A flat blade along -z from z0 to z1, w0 wide at the base narrowing to w1 (0 = a point), t thick, with a raised spine. */
    private static void blade(VertexConsumer vc, Matrix4f m, Matrix3f n, Mat mat, float z0, float z1, float w0, float w1, float t) {
        tube(vc, m, n, mat, 0, 0, z0, 0, 0, z1, w0, t, w1, t * 0.3F, 4);
        tube(vc, m, n, mat, 0, 0, z0, 0, 0, z1 * 0.85F, w0 * 0.25F, t * 1.4F, 0.005F, t * 0.6F, 4);
        if (w1 <= 0.001F) tube(vc, m, n, mat, 0, 0, z1, 0, 0, z1 - w0 * 0.9F, w0 * 0.25F, t * 0.3F, 0F, 0F, 4);
    }

    /** A sabre curving off to one side ({@code bend} sideways at the tip). */
    private static void curvedBlade(VertexConsumer vc, Matrix4f m, Matrix3f n, Mat mat, float len, float w, float bend) {
        float px = 0, pz = -0.04F;
        for (int i = 1; i <= 8; i++) {
            float s = i / 8F;
            float x = bend * s * s, z = -0.04F - len * s;
            float wa = w * (1F - (i - 1) / 8F * 0.7F), wb = w * (1F - s * 0.7F);
            if (i == 8) wb = 0.004F;
            tube(vc, m, n, mat, px, 0, pz, x, 0, z, wa, 0.02F, wb, 0.012F, 4);
            px = x; pz = z;
        }
    }

    private static void ring(VertexConsumer vc, Matrix4f m, Matrix3f n, Mat mat, float x, float y, float z, float r, float th) {
        int seg = 14;
        for (int i = 0; i < seg; i++) {
            double a0 = Math.PI * 2 * i / seg, a1 = Math.PI * 2 * (i + 1) / seg;
            tube(vc, m, n, mat, x + (float) Math.cos(a0) * r, y + (float) Math.sin(a0) * r, z, x + (float) Math.cos(a1) * r, y + (float) Math.sin(a1) * r, z, th, th, th, th, 4);
        }
    }

    private static void sphere(VertexConsumer vc, Matrix4f m, Matrix3f n, Mat mat, float x, float y, float z, float r) {
        tube(vc, m, n, mat, x, y, z - r, x, y, z, 0F, 0F, r, r, 8);
        tube(vc, m, n, mat, x, y, z, x, y, z + r, r, r, 0F, 0F, 8);
    }

    /**
     * The Caduceus itself: two rods, one black and one white, each banded and capped in gold, joined end to end by a
     * gold chain. The black rod is held; the white one hangs off the chain and swings.
     */
    private static void vial(VertexConsumer vc, Matrix4f m, Matrix3f n, Mat gold, double now) {
        Mat black = new Mat(2, now), white = new Mat(3, now);
        // the held rod, along -z out of the fist
        rod(vc, m, n, black, gold, 0, 0, 0.25F, 0, 0, -0.75F, now);
        // the chain from the far end of the held rod, hanging and swinging, to the white rod
        float sw = (float) Math.sin(now * 0.22) * 0.35F, sw2 = (float) Math.cos(now * 0.17) * 0.15F;
        float px = 0, py = 0, pz = -0.75F;
        float ex = sw, ey = 0.65F, ez = -0.55F + sw2;
        for (int i = 1; i <= 9; i++) {
            float t = i / 9F;
            float x = ex * t, y = ey * t - (float) Math.sin(t * Math.PI) * 0.12F, z = -0.75F + (ez + 0.75F) * t;
            if (i % 2 == 1) ring(vc, m, n, gold, (px + x) * 0.5F, (py + y) * 0.5F, (pz + z) * 0.5F, 0.03F, 0.008F);
            else tube(vc, m, n, gold, px, py, pz, x, y, z, 0.01F, 0.01F, 0.01F, 0.01F, 4);
            px = x; py = y; pz = z;
        }
        // the white rod hanging from the chain's end, roughly level and swinging with it
        rod(vc, m, n, white, gold, ex, ey, ez, ex + 0.1F + sw * 0.4F, ey + 0.08F, ez + 0.95F, now);
    }

    private static void rod(VertexConsumer vc, Matrix4f m, Matrix3f n, Mat body, Mat gold, float ax, float ay, float az, float bx, float by, float bz, double now) {
        tube(vc, m, n, body, ax, ay, az, bx, by, bz, 0.045F, 0.045F, 0.045F, 0.045F, 8);
        for (float t : new float[]{0.1F, 0.18F, 0.82F, 0.9F}) {   // bands near each end
            float x = ax + (bx - ax) * t, y = ay + (by - ay) * t, z = az + (bz - az) * t;
            float x2 = ax + (bx - ax) * (t + 0.025F), y2 = ay + (by - ay) * (t + 0.025F), z2 = az + (bz - az) * (t + 0.025F);
            tube(vc, m, n, body.style() == 2 ? new Mat(3, now) : new Mat(2, now), x, y, z, x2, y2, z2, 0.05F, 0.05F, 0.05F, 0.05F, 8);
        }
        sphere(vc, m, n, gold, ax, ay, az, 0.06F);   // gold caps
        sphere(vc, m, n, gold, bx, by, bz, 0.06F);
    }
}
