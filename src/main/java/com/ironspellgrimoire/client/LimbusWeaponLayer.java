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

    public static void set(int id, int life, int kind, boolean gold) { set(id, life, kind, gold, 0, 0, 0); }

    /** {@code first}, {@code every}, {@code count}: when the skill's blows land (ticks from now) - the weapon swings / thrusts on them. */
    public static void set(int id, int life, int kind, boolean gold, float first, float every, int count) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        double now = mc.level.getGameTime();
        if (life <= 0) ACTIVE.remove(id);
        else ACTIVE.put(id, new double[]{now, now + life, kind, gold ? 1 : 0, first, every, count});
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

    /** player id -> {game tick checked, 1 if wearing}: the Curios lookup is done once a tick, not once a frame. */
    private static final Map<Integer, long[]> WEARS = new HashMap<>();

    /** True while this player wears the Caduceus (Rien): its rod is always in the main hand. */
    private static boolean wearsCaduceus(net.minecraft.world.entity.player.Player p) {
        long tick = p.level().getGameTime();
        long[] c = WEARS.get(p.getId());
        if (c != null && c[0] == tick) return c[1] == 1;
        boolean w = false;
        try {
            net.minecraft.world.item.ItemStack s = io.redspace.ironsspellbooks.api.util.Utils.getPlayerSpellbookStack(p);
            w = s != null && s.is(com.ironspellgrimoire.registry.AddonItemRegistry.CADUCEUS_GRIMOIRE.get());
        } catch (Throwable t) {
            w = false;
        }
        if (WEARS.size() > 256) WEARS.clear();
        WEARS.put(p.getId(), new long[]{tick, w ? 1 : 0});
        return w;
    }

    /** How far each weapon is turned from square-to-the-fist toward carrying on along the arm (degrees): lances and
     *  spears point almost straight on from the arm, so a thrust goes where the arm points; swords sit a little higher;
     *  hafted weapons (scythe, hammer) and the gun continue the arm's line. */
    private static float angle(int k) {
        return switch (k) {
            case LANCE, SPEAR, RAPIER -> 82F;
            case GREATSWORD, TWIN, DAGGERS -> 70F;
            case SCYTHE, HAMMER, WHIP, GUN -> 88F;
            case VIAL -> 75F;
            default -> 0F;
        };
    }

    // ------------------------------------------------------------------ third person
    @Override
    public void render(PoseStack ps, MultiBufferSource buf, int light, AbstractClientPlayer p, float limbSwing, float limbSwingAmount,
                       float pt, float age, float netHeadYaw, float headPitch) {
        if (p.isInvisible()) return;
        double now = p.level().getGameTime() + pt;
        double[] f = ACTIVE.get(p.getId());
        if (f != null && now > f[1] + 3) { ACTIVE.remove(p.getId()); f = null; }
        boolean rien = wearsCaduceus(p);
        if (f == null && !rien) return;
        VertexConsumer vc = buf.getBuffer(RenderType.entityTranslucentEmissive(WHITE));
        PlayerModel<AbstractClientPlayer> m = getParentModel();
        float g = f == null ? 0F : grow(f, now);
        int kind = f == null ? 0 : kindAt(f, now);
        boolean slime = f != null ? f[3] > 0.5 : rien;
        boolean weapon = f != null && g > 0.01F && kind != VIAL;
        ps.pushPose();
        m.rightArm.translateAndRotate(ps);
        ps.translate(-1.0F / 16.0F, 0.6F, -0.03F);   // in the right fist
        hold(ps, vc, f, now, g, kind, slime, rien, weapon, true, 1F);
        ps.popPose();
        if (weapon && twoHanded(kind)) {   // the second sabre / dagger, or the bow, in the left fist
            ps.pushPose();
            m.leftArm.translateAndRotate(ps);
            ps.translate(1.0F / 16.0F, 0.6F, -0.03F);
            hold(ps, vc, f, now, g, kind, slime, false, true, false, 1F);
            ps.popPose();
        }
    }

    /** What one fist holds. Rien: the Caduceus, turned along the arm like a baton, and the weapon pouring out of its tip. */
    private static void hold(PoseStack ps, VertexConsumer vc, double[] f, double now, float g, int kind, boolean slime, boolean rien,
                             boolean weapon, boolean right, float motionK) {
        if (rien) {
            ps.pushPose();
            ps.mulPose(Axis.XP.rotationDegrees(weapon ? angle(kind) : angle(VIAL)));
            if (weapon) motion(ps, f, now, kind, right, motionK);
            caduceus(ps, vc, now, weapon ? g : 0F);
            if (weapon) {
                ps.translate(0, 0, -0.36F);   // out of the rod's tip
                draw(ps, vc, kind, slime, g, now, right);
            }
            ps.popPose();
            return;
        }
        if (!weapon) return;
        ps.pushPose();
        ps.mulPose(Axis.XP.rotationDegrees(angle(kind)));
        motion(ps, f, now, kind, right, motionK);
        draw(ps, vc, kind, slime, g, now, right);
        ps.popPose();
    }

    private static boolean twoHanded(int k) { return k == TWIN || k == DAGGERS || k == BOW; }

    // ------------------------------------------------------------------ first person
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST, receiveCanceled = true)   // even while a cast hides the hands
    public static void onHand(RenderHandEvent e) {
        if (e.getHand() != InteractionHand.MAIN_HAND) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !mc.options.getCameraType().isFirstPerson()) return;
        double now = mc.level.getGameTime() + e.getPartialTick();
        double[] f = ACTIVE.get(mc.player.getId());
        if (f != null && now > f[1] + 3) f = null;
        boolean rien = wearsCaduceus(mc.player);
        if (f == null && !rien) return;
        float g = f == null ? 0F : grow(f, now);
        int kind = f == null ? 0 : kindAt(f, now);
        boolean slime = f != null ? f[3] > 0.5 : rien;
        boolean weapon = f != null && g > 0.01F && kind != VIAL;
        VertexConsumer vc = e.getMultiBufferSource().getBuffer(RenderType.entityTranslucentEmissive(WHITE));
        PoseStack ps = e.getPoseStack();
        float swing = e.getSwingProgress();
        for (int side = 0; side < (weapon && twoHanded(kind) ? 2 : 1); side++) {
            boolean right = side == 0;
            ps.pushPose();
            // where vanilla holds an item in the hand, low in the corner of the view
            ps.translate(right ? 0.56F : -0.56F, -0.52F, -0.72F);
            ps.mulPose(Axis.YP.rotationDegrees(right ? -4F : 4F));
            ps.mulPose(Axis.XP.rotationDegrees(-swing * 50F));
            ps.scale(0.7F, 0.7F, 0.7F);
            // first person has no arm model to follow: turn the arm frame (+y down the arm, -z out of the fist) so the
            // arm points into the screen - then everything is held exactly as in third person
            ps.mulPose(Axis.XP.rotationDegrees(-90F));
            hold(ps, vc, f, now, g, kind, slime, rien && right, weapon, right, 1.3F);
            ps.popPose();
        }
    }

    /** The Caduceus in the fist, sized to the hand; {@code pour} > 0 while slime is coming out of its tip to make a weapon. */
    private static void caduceus(PoseStack ps, VertexConsumer vc, double now, float pour) {
        ps.pushPose();
        ps.scale(0.5F, 0.5F, 0.5F);
        vial(vc, ps.last().pose(), ps.last().normal(), new Mat(1, now), now);
        if (pour > 0.01F) {   // a swelling bead of black slime at the rod's tip, feeding the weapon
            float r = 0.08F + 0.06F * pour + (float) Math.sin(now * 0.8) * 0.015F;
            sphere(vc, ps.last().pose(), ps.last().normal(), new Mat(4, now), 0, 0, -0.78F, r);
        }
        ps.popPose();
    }

    // ------------------------------------------------------------------ the weapon's own motion, timed to the skill's blows
    /** {strength, which blow}: -0.45 drawing back before a blow, snapping to 1 as it lands, easing back to 0 after. */
    private static float[] strike(double[] f, double now) {
        if (f.length < 7 || f[6] <= 0) return new float[]{0F, 0F};
        double t = now - f[0];
        float best = 0F, idx = 0F;
        for (int i = 0; i < (int) f[6]; i++) {
            double d = t - (f[4] + i * f[5]);
            float v;
            if (d < -6 || d > 7) continue;
            if (d < 0) v = (float) (-0.45 * Math.sin((d + 6) / 6.0 * Math.PI / 2));
            else if (d < 1.5) v = (float) Mth.lerp(d / 1.5, -0.45, 1.0);
            else v = (float) (1.0 - (d - 1.5) / 5.5);
            if (Math.abs(v) > Math.abs(best)) { best = v; idx = i; }
        }
        return new float[]{best, idx};
    }

    private static void motion(PoseStack ps, double[] f, double now, int kind, boolean right, float k) {
        float[] st = strike(f, now);
        float s = st[0] * k;
        int i = (int) st[1];
        float side = (right ? 1F : -1F) * (i % 2 == 0 ? 1F : -1F);
        float idle = (float) Math.sin(now * 0.3) * 0.03F;   // never quite still: a slow breathing sway
        ps.translate(0, idle, 0);
        switch (kind) {
            case LANCE, RAPIER, SPEAR -> {   // thrust: drawn back, then driven out along the blade
                ps.translate(0, 0, -0.6F * s);
                ps.mulPose(Axis.ZP.rotationDegrees(25F * s * (kind == RAPIER ? 1 : 0.4F)));   // a twist as it goes in
            }
            case FISTS, DAGGERS -> {   // jabs, the hands taking turns
                float js = (right == (i % 2 == 0)) ? s : s * 0.3F;
                ps.translate(0, 0, -0.5F * js);
                if (kind == DAGGERS) ps.mulPose(Axis.YP.rotationDegrees(50F * js * side));
            }
            case TWIN, SCYTHE, WHIP -> ps.mulPose(Axis.YP.rotationDegrees(85F * s * side));   // wide horizontal sweeps, back and forth
            case GREATSWORD, HAMMER -> {   // overhead: raised back, then brought down hard
                ps.mulPose(Axis.XP.rotationDegrees(-75F * s));
                ps.translate(0, 0, -0.2F * Math.max(0, s));
            }
            case GUN -> ps.mulPose(Axis.XP.rotationDegrees(30F * Math.max(0, s)));   // the kick of each shot
            case BOW -> { if (right) ps.translate(0, Math.min(0, s) * 0.7F, 0); }   // the arrow drawn back along the arm, then loosed
            case VIAL -> ps.mulPose(Axis.YP.rotationDegrees((float) Math.sin(now * 0.4) * 20F));
            default -> { }
        }
    }

    // ------------------------------------------------------------------ the weapons (forward = -z out of the fist, +y = down the arm, x = across the fist)
    private static void draw(PoseStack ps, VertexConsumer vc, int kind, boolean gold, float g, double now, boolean right) {
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();
        Mat mat = new Mat(gold ? 4 : 0, now);   // 'gold' = Rien's: his weapons are black slime
        float L = g;   // everything grows out of the fist (gold pours out of the vial)
        switch (kind) {
            case LANCE -> {
                // Don Quixote's lance (our own design): a long grip with a spiked pommel and wrapped bands, a thorned cup
                // guard, then the body - a long cone with three ridges winding round it and a collar - to a narrow point
                tube(vc, m, n, mat, 0, 0, 0.42F, 0, 0, 0.5F, 0.04F, 0.04F, 0F, 0F, 6);
                tube(vc, m, n, mat, 0, 0, 0.38F, 0, 0, -0.05F, 0.042F, 0.042F, 0.038F, 0.038F, 8);
                for (int w = 0; w < 4; w++) ring(vc, m, n, mat, 0, 0, 0.32F - w * 0.1F, 0.045F, 0.012F);
                tube(vc, m, n, mat, 0, 0, -0.05F, 0, 0, -0.16F, 0.05F, 0.05F, 0.26F, 0.26F, 12);
                tube(vc, m, n, mat, 0, 0, -0.16F, 0, 0, -0.2F, 0.26F, 0.26F, 0.11F, 0.11F, 12);
                for (int k = 0; k < 6; k++) {   // thorns round the guard's rim, curving back toward the hand
                    double a = Math.PI * 2 * k / 6;
                    float cx = (float) Math.cos(a), cy = (float) Math.sin(a);
                    tube(vc, m, n, mat, cx * 0.24F, cy * 0.24F, -0.15F, cx * 0.36F, cy * 0.36F, -0.02F, 0.025F, 0.025F, 0F, 0F, 4);
                }
                float len = 2.2F * L, z0 = -0.2F;
                tube(vc, m, n, mat, 0, 0, z0, 0, 0, z0 - len, 0.11F, 0.11F, 0.0F, 0.0F, 10);
                for (int r = 0; r < 3; r++) {   // three ridges winding round it, narrowing with the cone
                    float px = 0, py = 0, pz = z0;
                    for (int i = 0; i <= 14; i++) {
                        float t = i / 14F, rad = 0.11F * (1F - t) + 0.008F;
                        double a = r * Math.PI * 2 / 3 + t * Math.PI * 2.2;
                        float x = (float) Math.cos(a) * rad, y = (float) Math.sin(a) * rad, z = z0 - len * t * 0.92F;
                        float w0 = 0.02F * (1F - t * 0.7F);
                        if (i > 0) tube(vc, m, n, mat, px, py, pz, x, y, z, w0, w0, w0 * 0.8F, w0 * 0.8F, 4);
                        px = x; py = y; pz = z;
                    }
                }
                ring(vc, m, n, mat, 0, 0, z0 - len * 0.12F, 0.1F, 0.022F);
                drips(vc, m, n, mat, now, z0, z0 - len * 0.9F, 0.11F);
            }
            case BOW -> {
                // held in the arm's frame: with the arm out in front, +y points ahead and -z points up
                if (!right) {   // the bow: limbs up and down, bowed forward, recurved at the tips, the string behind
                    float R = 0.8F * L;
                    float[] prev = null;
                    for (int i = 0; i <= 16; i++) {
                        double a = -1.25 + 2.5 * i / 16.0;
                        float bz = (float) (Math.sin(a) * R), by = (float) ((Math.cos(a) - Math.cos(1.25)) * R * 0.5);
                        if (Math.abs(i - 8) > 6) by -= 0.05F * (Math.abs(i - 8) - 6);   // the recurve
                        if (prev != null) tube(vc, m, n, mat, 0, prev[0], prev[1], 0, by, bz, 0.04F, 0.055F, 0.04F, 0.055F, 6);
                        prev = new float[]{by, bz};
                    }
                    float ez = (float) (Math.sin(1.25) * R) + 0.02F, ey = -0.08F;
                    tube(vc, m, n, mat, 0, ey, -ez, 0, ey, ez, 0.006F, 0.006F, 0.006F, 0.006F, 4);
                    tube(vc, m, n, mat, 0, -0.06F, -0.05F, 0, 0.12F, -0.05F, 0.06F, 0.06F, 0.06F, 0.06F, 6);   // the grip wrap
                } else {   // the arrow, nocked: shaft forward along the arm, a heavy head, blood fletching
                    tube(vc, m, n, mat, 0, -0.1F, 0, 0, 0.95F * L, 0, 0.018F, 0.018F, 0.018F, 0.018F, 5);
                    tube(vc, m, n, mat, 0, 0.95F * L, 0, 0, 1.15F * L, 0, 0.06F, 0.025F, 0F, 0F, 4);
                    tube(vc, m, n, mat, 0, -0.1F, 0, 0, 0.05F, 0, 0.05F, 0.005F, 0.005F, 0.005F, 3);
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
                blade(vc, m, n, mat, -0.06F, -0.06F - 1.8F * L, 0.17F, 0.09F, 0.035F);
                if (!gold) drips(vc, m, n, mat, now, -0.1F, -0.06F - 2.0F * L, 0.1F);
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
                // a long shaft and a great curved blade at its head, curving the way the reap sweeps so the edge leads
                tube(vc, m, n, mat, 0, 0, 0.9F, 0, 0, -1.4F * L, 0.035F, 0.035F, 0.035F, 0.035F, 6);
                float hz = -1.4F * L;
                float[] prev = {0, hz};
                for (int i = 1; i <= 10; i++) {
                    double a = i / 10.0 * 2.3;
                    float bx = (float) (-Math.sin(a) * 0.75 * L), bz = (float) (hz + (1 - Math.cos(a)) * 0.6 * L);
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
                // a long haft and a great block of a head with blunt spikes on both striking faces
                float H = 1.3F * L;
                tube(vc, m, n, mat, 0, 0, 0.25F, 0, 0, -1.25F * H, 0.05F, 0.05F, 0.045F, 0.045F, 6);
                float hz = -1.35F * H, hw = 0.45F * H, hr = 0.26F * H;
                tube(vc, m, n, mat, -hw, 0, hz, hw, 0, hz, hr, hr, hr, hr, 8);
                ring(vc, m, n, mat, -hw, 0, hz, hr, 0.035F);
                ring(vc, m, n, mat, hw, 0, hz, hr, 0.035F);
                for (int k = -1; k <= 1; k++) {
                    tube(vc, m, n, mat, k * hw * 0.6F, -hr, hz, k * hw * 0.6F, -hr - 0.16F * H, hz, 0.06F, 0.06F, 0F, 0F, 4);
                    tube(vc, m, n, mat, k * hw * 0.6F, hr, hz, k * hw * 0.6F, hr + 0.16F * H, hz, 0.06F, 0.06F, 0F, 0F, 4);
                }
                drips(vc, m, n, mat, now, hz + 0.1F, hz - 0.3F, hr);
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
            if (style == 4) {   // black slime: ink-black, glossy, a cold sheen sliding along it as it moves
                float rip = (float) Math.pow(Math.max(0, Math.sin(now * 0.5 - along * 5.0)), 6.0) * 0.5F;
                float spec = shade > 0.86F ? (shade - 0.86F) * 5F : 0F;
                float v = 0.02F + 0.05F * shade + spec * 0.55F + rip * 0.35F;
                return new float[]{v, v, Math.min(1F, v * 1.12F)};
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

    /** Drops of blood (or slime) creeping down the weapon from base to tip, swelling and falling off the end. */
    private static void drips(VertexConsumer vc, Matrix4f m, Matrix3f n, Mat mat, double now, float z0, float z1, float r0) {
        for (int i = 0; i < 3; i++) {
            double t = ((now * 0.035) + i / 3.0) % 1.0;
            float z = (float) (z0 + (z1 - z0) * t);
            float rad = r0 * (1F - (float) t) + 0.012F;
            float sz = 0.018F + 0.02F * (float) t;
            double a = i * 2.1;
            sphere(vc, m, n, mat, (float) Math.cos(a) * rad, (float) Math.sin(a) * rad + sz * 0.5F, z, sz);
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
     * The Caduceus itself: a black rod banded in white and capped in gold, with a gold chain looping from end to end.
     */
    private static void vial(VertexConsumer vc, Matrix4f m, Matrix3f n, Mat gold, double now) {
        Mat black = new Mat(2, now);
        // the held rod, along -z out of the fist
        rod(vc, m, n, black, gold, 0, 0, 0.25F, 0, 0, -0.75F, now);
        // the gold chain looping from one end of the rod to the other, swaying
        float sw = (float) Math.sin(now * 0.22) * 0.12F;
        float px = 0, py = 0, pz = 0.25F;
        for (int i = 1; i <= 11; i++) {
            float t = i / 11F;
            float z = 0.25F - 1.0F * t, y = (float) Math.sin(t * Math.PI) * 0.35F, x = (float) Math.sin(t * Math.PI) * sw;
            if (i % 2 == 1) ring(vc, m, n, gold, (px + x) * 0.5F, (py + y) * 0.5F, (pz + z) * 0.5F, 0.03F, 0.008F);
            else tube(vc, m, n, gold, px, py, pz, x, y, z, 0.01F, 0.01F, 0.01F, 0.01F, 4);
            px = x; py = y; pz = z;
        }
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
