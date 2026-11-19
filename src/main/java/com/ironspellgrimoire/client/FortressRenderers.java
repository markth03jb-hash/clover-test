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
            ParasiteArms.drawPiece(ps, buf, light, TOWER[k]);
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
            r.render(d, yaw, pt, ps, new ShadowSoldierRenderer.Tint(buf, 0.78F, 0.74F, 0.68F, 0), light);   // weathered stone
            ps.popPose();
            super.render(s, yaw, pt, ps, buf, light);
        }

        @SuppressWarnings("deprecation")
        @Override public ResourceLocation getTextureLocation(FortressDefender e) { return TextureAtlas.LOCATION_BLOCKS; }
    }
}
