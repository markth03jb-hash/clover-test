package com.ironspellgrimoire.client;

import com.ironspellgrimoire.entity.FortressDefender;
import com.ironspellgrimoire.entity.FortressTower;
import com.ironspellgrimoire.entity.FortressWall;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

/**
 * The Architect of Ruin's fortress, drawn: the Blender models (tools/blender/architect_ruin.py) for the walls, the
 * gatehouse, the four towers, the Ballista and the bone sentries; each piece rises out of the ground as it appears,
 * and a wall sinks a little and darkens as it's broken. Garrison defenders are the mob they were, in weathered stone.
 */
public final class FortressRenderers {
    private FortressRenderers() {}

    static ResourceLocation model(String name) { return ResourceLocation.fromNamespaceAndPath("iron_spell_grimoire", "item/fortress_" + name); }
    static final ResourceLocation WALL = model("wall"), GATE = model("gatehouse"), SENTRY = model("bone_sentry");
    static final ResourceLocation[] TOWER = {model("watchtower"), model("arcane_spire"), model("forge_tower"), model("siege_platform"), model("ballista")};

    @Mod.EventBusSubscriber(modid = "iron_spell_grimoire", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Register {
        @SubscribeEvent
        public static void models(ModelEvent.RegisterAdditional e) {
            e.register(WALL); e.register(GATE); e.register(SENTRY);
            for (ResourceLocation r : TOWER) e.register(r);
            EliteClient.registerModels(e);   // the elite mobs' models
            RivalRenderer.registerModels(e);   // the rivals' gear
        }
    }

    /** Rising out of the ground over its first 10 ticks: how far still below (0 = up). */
    static float rise(int age, float pt) { float k = Mth.clamp((age + pt) / 10F, 0F, 1F); return (1 - k) * (1 - k); }

    public static class WallRenderer extends EntityRenderer<FortressWall> {
        public WallRenderer(EntityRendererProvider.Context c) { super(c); this.shadowRadius = 0F; }

        @Override
        public void render(FortressWall e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
            ps.pushPose();
            boolean gate = e.kind() == FortressWall.GATE;
            float h = gate ? 2.9F : 2.6F;
            ps.translate(0, -rise(e.tickCount, pt) * h, 0);
            ps.mulPose(Axis.YP.rotationDegrees(-e.getYRot()));
            if (e.grand()) ps.scale(1F, 1.25F, 1F);
            float hurt = e.maxHp <= 0 ? 1F : Mth.clamp(e.hp() / e.maxHp, 0F, 1F);
            if (!gate && hurt < 0.5F) ps.translate(0, -(0.5F - hurt) * 0.4F, 0);   // a battered wall sags
            ParasiteArms.drawPiece(ps, buf, light, gate ? GATE : WALL);
            ps.popPose();
            super.render(e, yaw, pt, ps, buf, light);
        }

        @SuppressWarnings("deprecation")
        @Override public ResourceLocation getTextureLocation(FortressWall e) { return TextureAtlas.LOCATION_BLOCKS; }
    }

    public static class TowerRenderer extends EntityRenderer<FortressTower> {
        public TowerRenderer(EntityRendererProvider.Context c) { super(c); this.shadowRadius = 0.7F; }

        @Override
        public void render(FortressTower e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
            int k = Mth.clamp(e.kind(), 0, TOWER.length - 1);
            ps.pushPose();
            ps.translate(0, -rise(e.tickCount, pt) * 3.6F, 0);
            boolean turns = k == FortressTower.BALLISTA || k == FortressTower.BOLTS;   // the ballista turns to its target
            ps.mulPose(Axis.YP.rotationDegrees(turns ? -e.aim() + 180 : 0));
            ParasiteArms.drawPiece(ps, buf, e.overcharged() ? net.minecraft.client.renderer.LightTexture.FULL_BRIGHT : light, TOWER[k]);   // overcharged: lit from within
            ps.popPose();
            super.render(e, yaw, pt, ps, buf, light);
        }

        @SuppressWarnings("deprecation")
        @Override public ResourceLocation getTextureLocation(FortressTower e) { return TextureAtlas.LOCATION_BLOCKS; }
    }

    public static class DefenderRenderer extends EntityRenderer<FortressDefender> {
        private static final Map<Integer, LivingEntity> PUPPETS = new HashMap<>();
        public DefenderRenderer(EntityRendererProvider.Context c) { super(c); this.shadowRadius = 0.5F; }

        @Override
        public void render(FortressDefender s, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
            ps.pushPose();
            ps.translate(0, -rise(s.tickCount, pt) * s.getBbHeight(), 0);
            if (s.role() == FortressDefender.SENTRY) {   // the Crypt's bone sentry: its own model, turned the way it faces
                ps.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(pt, s.yBodyRotO, s.yBodyRot) + 180));
                    ParasiteArms.drawPiece(ps, buf, light, SENTRY);
                ps.popPose();
                super.render(s, yaw, pt, ps, buf, light);
                return;
            }
            LivingEntity d = ShadowSoldierRenderer.puppet(PUPPETS, s.getId(), s.kind(), s.level());
            d.setPos(s.getX(), s.getY(), s.getZ());
            d.xo = s.xo; d.yo = s.yo; d.zo = s.zo;
            d.yBodyRot = s.yBodyRot; d.yBodyRotO = s.yBodyRotO; d.yHeadRot = s.yHeadRot; d.yHeadRotO = s.yHeadRotO;
            d.setXRot(s.getXRot()); d.xRotO = s.xRotO; d.setYRot(s.getYRot()); d.yRotO = s.yRotO;
            d.attackAnim = s.attackAnim; d.oAttackAnim = s.oAttackAnim; d.swinging = s.swinging; d.swingTime = s.swingTime;
            d.tickCount = s.tickCount; d.hurtTime = s.hurtTime;
            d.setOnGround(s.onGround());
            if (d.tickCount != d.getPersistentData().getInt("isg_last")) {
                d.getPersistentData().putInt("isg_last", d.tickCount);
                d.walkAnimation.update(Math.min(1, s.walkAnimation.speed()), 0.4F);
            }
            float sc = s.rankScale();
            ps.scale(sc, sc, sc);
            var r = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(d);
            r.render(d, yaw, pt, ps, new ShadowSoldierRenderer.Tint(buf, 0.6F, 0.58F, 0.54F, 0), light);   // weathered stone
            ps.popPose();
            crest(ps, buf, s.getBbHeight() * sc + 0.45F, s.tickCount + pt, s.rank());
            super.render(s, yaw, pt, ps, buf, light);
        }

        /** A small brass crest turning over its head: it's yours, not a wild mob (brighter with its rank). */
        static void crest(PoseStack ps, MultiBufferSource buf, float y, float t, int rank) {
            ps.pushPose();
            ps.translate(0, y + 0.05F * Mth.sin(t * 0.1F), 0);
            ps.mulPose(Axis.YP.rotationDegrees(t * 3F));
            com.mojang.blaze3d.vertex.VertexConsumer vc = buf.getBuffer(net.minecraft.client.renderer.RenderType.lightning());
            org.joml.Matrix4f m = ps.last().pose();
            float s = 0.12F + 0.02F * rank, k = 0.55F + 0.12F * rank;
            float r = 0.91F * k, g = 0.72F * k, b = 0.38F * k;
            for (int face = 0; face < 2; face++) {   // a diamond, seen from both sides
                float z = face == 0 ? 0.001F : -0.001F;
                vc.vertex(m, 0, s * 1.4F, z).color(r, g, b, 1F).endVertex();
                vc.vertex(m, face == 0 ? -s : s, 0, z).color(r, g, b, 1F).endVertex();
                vc.vertex(m, 0, -s * 1.4F, z).color(r, g, b, 1F).endVertex();
                vc.vertex(m, face == 0 ? s : -s, 0, z).color(r, g, b, 1F).endVertex();
            }
            ps.popPose();
        }

        @SuppressWarnings("deprecation")
        @Override public ResourceLocation getTextureLocation(FortressDefender e) { return TextureAtlas.LOCATION_BLOCKS; }
    }
}
