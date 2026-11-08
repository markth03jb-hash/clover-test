package com.example.arcaneaddon.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Knight's Armor form: white-steel plate armor with gold trim, a visored helm with a red plume, and a red cape,
 * worn on the player's own model - every plate follows the head, arms and legs as they move, and the cape swings
 * and flows like the vanilla cape does. Fed by FxPacket shape 11 (id = player, life = ticks left, 0 = off).
 */
public class KnightArmorLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final ResourceLocation TEX = ResourceLocation.fromNamespaceAndPath("arcane_addon", "textures/entity/knight_armor.png");
    /** player entity id -> {start, end} game time */
    private static final Map<Integer, double[]> ACTIVE = new HashMap<>();

    // texture regions (u0, v0, u1, v1) on the 64x64 sheet
    private static final float[] STEEL = {0, 0, 0.5F, 0.5F}, GOLD = {0.5F, 0, 0.75F, 0.25F}, VISOR = {0.75F, 0, 1, 0.25F},
            PLUME = {0.5F, 0.25F, 0.75F, 0.5F}, CAPE = {0, 0.5F, 0.5F, 1};

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
                    r.addLayer(new AuraOutlineLayer(r));
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
        // the plates lock on over the first few ticks (they grow onto the body)
        float grow = (float)Mth.clamp((now - f[0]) / 5.0, 0.0, 1.0);
        if (grow <= 0.01F) return;
        float in = 0.4F * (1.0F - grow);   // extra puff-out while locking on
        VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(TEX));
        PlayerModel<AbstractClientPlayer> m = getParentModel();
        boolean slim = isSlim(p);

        // helm: plate shell, a gold band, the dark visor slit and a red plume
        part(ps, m.head, () -> {
            cube(ps, vc, light, -4.6F - in, -8.7F - in, -4.6F - in, 4.6F + in, 0.4F, 4.6F + in, STEEL);
            cube(ps, vc, light, -4.75F, -5.2F, -4.75F, 4.75F, -4.4F, 4.75F, GOLD);
            cube(ps, vc, light, -3.4F, -5.0F, -4.85F, 3.4F, -3.9F, -4.6F, VISOR);
            cube(ps, vc, light, -0.6F, -11.5F, -2.5F, 0.6F, -8.6F, 3.5F, PLUME);
        });
        // breastplate and tassets
        part(ps, m.body, () -> {
            cube(ps, vc, light, -4.7F - in, -0.3F, -2.7F - in, 4.7F + in, 8.6F, 2.7F + in, STEEL);
            cube(ps, vc, light, -4.8F, 8.6F, -2.8F, 4.8F, 9.6F, 2.8F, GOLD);   // belt
            cube(ps, vc, light, -4.6F, 9.6F, -2.75F, 4.6F, 12.6F, 2.75F, STEEL);
            cube(ps, vc, light, -0.5F, 1.0F, -2.9F, 0.5F, 7.5F, -2.7F, GOLD);   // gold line down the chest
        });
        // pauldrons and vambraces
        float aw = slim ? 3.0F : 4.0F;
        part(ps, m.rightArm, () -> {
            cube(ps, vc, light, -3.9F - in, -2.9F, -2.9F, (aw - 3.0F) + 1.6F, 1.9F, 2.9F, STEEL);
            cube(ps, vc, light, -4.0F, 1.9F, -3.0F, (aw - 3.0F) + 1.7F, 2.5F, 3.0F, GOLD);
            cube(ps, vc, light, -3.4F, 5.0F, -2.4F, (aw - 3.0F) + 1.4F, 10.3F, 2.4F, STEEL);
        });
        part(ps, m.leftArm, () -> {
            cube(ps, vc, light, -1.6F, -2.9F, -2.9F, aw - 1.0F + 0.9F + in, 1.9F, 2.9F, STEEL);
            cube(ps, vc, light, -1.7F, 1.9F, -3.0F, aw - 1.0F + 1.0F, 2.5F, 3.0F, GOLD);
            cube(ps, vc, light, -1.4F, 5.0F, -2.4F, aw - 1.0F + 0.4F, 10.3F, 2.4F, STEEL);
        });
        // greaves with gold knee caps
        for (ModelPart leg : new ModelPart[]{m.rightLeg, m.leftLeg}) {
            part(ps, leg, () -> {
                cube(ps, vc, light, -2.45F, 4.5F, -2.45F, 2.45F, 12.3F, 2.45F, STEEL);
                cube(ps, vc, light, -1.4F, 4.0F, -2.7F, 1.4F, 5.8F, -2.4F, GOLD);
            });
        }
        cape(ps, vc, light, p, pt, grow);
    }

    private static boolean isSlim(AbstractClientPlayer p) {
        return "slim".equals(p.getModelName());
    }

    private static void part(PoseStack ps, ModelPart part, Runnable draw) {
        ps.pushPose();
        part.translateAndRotate(ps);
        draw.run();
        ps.popPose();
    }

    /** The red cape: hangs from the shoulders and swings with your movement exactly like the vanilla cape. */
    private static void cape(PoseStack ps, VertexConsumer vc, int light, AbstractClientPlayer p, float pt, float grow) {
        ps.pushPose();
        ps.translate(0.0F, 0.0F, 0.17F);
        double dx = Mth.lerp(pt, p.xCloakO, p.xCloak) - Mth.lerp(pt, p.xo, p.getX());
        double dy = Mth.lerp(pt, p.yCloakO, p.yCloak) - Mth.lerp(pt, p.yo, p.getY());
        double dz = Mth.lerp(pt, p.zCloakO, p.zCloak) - Mth.lerp(pt, p.zo, p.getZ());
        float body = Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot);
        double s = Mth.sin(body * ((float)Math.PI / 180F)), c = -Mth.cos(body * ((float)Math.PI / 180F));
        float lift = Mth.clamp((float)dy * 10.0F, -6.0F, 32.0F);
        float back = Mth.clamp((float)(dx * s + dz * c) * 100.0F, 0.0F, 150.0F);
        float side = Mth.clamp((float)(dx * c - dz * s) * 100.0F, -20.0F, 20.0F);
        float bob = Mth.lerp(pt, p.oBob, p.bob);
        lift += Mth.sin(Mth.lerp(pt, p.walkDistO, p.walkDist) * 6.0F) * 32.0F * bob;
        if (p.isCrouching()) lift += 25.0F;
        // it billows a little even standing still
        lift += Mth.sin((p.tickCount + pt) * 0.12F) * 3.0F;
        ps.mulPose(Axis.XP.rotationDegrees(6.0F + back / 2.0F + lift));
        ps.mulPose(Axis.ZP.rotationDegrees(side / 2.0F));
        float len = 19.0F * grow;
        cube(ps, vc, light, -6.0F, 0.0F, -0.6F, 6.0F, len, 0.0F, CAPE);
        cube(ps, vc, light, -6.2F, -0.4F, -0.8F, 6.2F, 0.8F, 0.2F, GOLD);   // the clasp bar across the shoulders
        ps.popPose();
    }

    /** A textured box in model pixels (1/16 block); every face shows the same texture region. */
    private static void cube(PoseStack ps, VertexConsumer vc, int light, float x0, float y0, float z0, float x1, float y1, float z1, float[] uv) {
        float a = 1.0F / 16.0F;
        x0 *= a; y0 *= a; z0 *= a; x1 *= a; y1 *= a; z1 *= a;
        PoseStack.Pose pose = ps.last();
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        float u0 = uv[0], v0 = uv[1], u1 = uv[2], v1 = uv[3];
        face(vc, m, n, light, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, 0, 0, -1, u0, v0, u1, v1);   // north
        face(vc, m, n, light, x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1, 0, 0, 1, u0, v0, u1, v1);    // south
        face(vc, m, n, light, x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1, -1, 0, 0, u0, v0, u1, v1);   // west
        face(vc, m, n, light, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, 1, 0, 0, u0, v0, u1, v1);    // east
        face(vc, m, n, light, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0, 0, -1, 0, u0, v0, u1, v1);   // top (model y is down)
        face(vc, m, n, light, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, 0, 1, 0, u0, v0, u1, v1);    // bottom
    }

    private static void face(VertexConsumer vc, Matrix4f m, Matrix3f n, int light,
                             float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz,
                             float nx, float ny, float nz, float u0, float v0, float u1, float v1) {
        vtx(vc, m, n, light, ax, ay, az, u0, v0, nx, ny, nz);
        vtx(vc, m, n, light, bx, by, bz, u1, v0, nx, ny, nz);
        vtx(vc, m, n, light, cx, cy, cz, u1, v1, nx, ny, nz);
        vtx(vc, m, n, light, dx, dy, dz, u0, v1, nx, ny, nz);
    }

    private static void vtx(VertexConsumer vc, Matrix4f m, Matrix3f n, int light, float x, float y, float z, float u, float v, float nx, float ny, float nz) {
        vc.vertex(m, x, y, z).color(1.0F, 1.0F, 1.0F, 1.0F).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, nx, ny, nz).endVertex();
    }
}
