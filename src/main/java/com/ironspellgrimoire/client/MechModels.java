package com.ironspellgrimoire.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animatable.instance.InstancedAnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoObjectRenderer;
import software.bernie.geckolib.util.RenderUtils;

/**
 * The Mech Grimoire's GeckoLib models (made by tools/mech_models.py): an original heavy war machine in painted
 * sand-and-slate armour, lit by the world, its lenses, reactor and thruster throats glowing (the _glowmask texture):
 *   mech_titan - the war machine standing round a pilot, only the parts he has built (each part is a bone: the rest
 *                are hidden). It's printed in from the feet up, idles, strides when he walks and punches when he swings.
 *   mech_fist  - the Rocket Fist flying out and back, and the Titan Buster's giant fist dropping out of the sky.
 */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = "iron_spell_grimoire", value = net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class MechModels {
    private MechModels() {}

    /** The bones of each buildable part, in MechFx's part order: arms, thrusters, fists, legs, plating, launchers, core, cannon. */
    static final String[][] PART_BONES = {
            {"arm_l", "arm_r"}, {"thrusters"}, {"fist_l", "fist_r"}, {"leg_l", "leg_r"}, {"plating"}, {"launcher"}, {"core"}, {"cannon"}};

    /** One animated model instance. */
    static final class Anim implements GeoAnimatable {
        private final AnimatableInstanceCache cache = new InstancedAnimatableInstanceCache(this);
        final String model, intro;
        String loop;
        final double started;
        int mask = 0xFF;
        boolean forearm = true;
        private String currentKey;
        private RawAnimation current;
        private boolean introDone;

        Anim(String model, String intro, String loop, double started) { this.model = model; this.intro = intro; this.loop = loop; this.started = started; }

        private PlayState handle(AnimationState<Anim> state) {
            String key = (intro != null && !introDone ? intro + ">" : "") + loop;
            if (!key.equals(currentKey)) {
                if (currentKey != null && intro != null) introDone = true;   // it has moved on: never replay the intro
                key = (intro != null && !introDone ? intro + ">" : "") + loop;
                currentKey = key;
                String base = "animation." + model + ".";
                current = intro != null && !introDone ? RawAnimation.begin().thenPlay(base + intro).thenLoop(base + loop) : RawAnimation.begin().thenLoop(base + loop);
            }
            return state.setAndContinue(current);
        }

        @Override public void registerControllers(AnimatableManager.ControllerRegistrar c) { c.add(new AnimationController<>(this, "main", 4, this::handle)); }
        @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
        @Override public double getTick(Object o) { return RenderUtils.getCurrentTick(); }
    }

    private static final class Model extends GeoModel<Anim> {
        private final ResourceLocation geo, anim, tex;
        Model(String name) {
            geo = ResourceLocation.fromNamespaceAndPath("iron_spell_grimoire", "geo/" + name + ".geo.json");
            anim = ResourceLocation.fromNamespaceAndPath("iron_spell_grimoire", "animations/" + name + ".animation.json");
            tex = ResourceLocation.fromNamespaceAndPath("iron_spell_grimoire", "textures/geo/" + name + ".png");
        }
        @Override public ResourceLocation getModelResource(Anim a) { return geo; }
        @Override public ResourceLocation getTextureResource(Anim a) { return tex; }
        @Override public ResourceLocation getAnimationResource(Anim a) { return anim; }
    }

    /** Solid painted armour lit by the world, the glowing bits on top (AutoGlowingGeoLayer reads the _glowmask), and
     *  only the built parts shown. No block-centring: we place it ourselves. */
    private static final class Renderer extends GeoObjectRenderer<Anim> {
        Renderer(String name) {
            super(new Model(name));
            addRenderLayer(new software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer<>(this));
        }

        @Override
        public RenderType getRenderType(Anim a, ResourceLocation texture, MultiBufferSource buf, float partialTick) {
            return RenderType.entityCutoutNoCull(texture);
        }

        @Override
        public void preRender(PoseStack ps, Anim a, BakedGeoModel model, MultiBufferSource bufferSource, VertexConsumer buffer,
                              boolean isReRender, float partialTick, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
            this.objectRenderTranslations = new Matrix4f(ps.last().pose());
            if (a.model.equals("mech_titan")) {
                for (int part = 0; part < PART_BONES.length; part++) {
                    boolean on = (a.mask & 1 << part) != 0;
                    for (String b : PART_BONES[part]) model.getBone(b).ifPresent(bone -> bone.setHidden(!on));
                }
            } else {
                model.getBone("forearm").ifPresent(bone -> bone.setHidden(!a.forearm));
            }
        }
    }

    private static final Map<String, Renderer> RENDERERS = new HashMap<>();
    private static final Map<String, Anim> ANIMS = new HashMap<>();

    private static Anim anim(String key, String model, String intro, String loop, double started) {
        Anim a = ANIMS.get(key);
        if (a == null || a.started != started) {
            a = new Anim(model, intro, loop, started);
            ANIMS.put(key, a);
            if (ANIMS.size() > 128) ANIMS.clear();
        }
        a.loop = loop;
        return a;
    }

    // ================================================================== the titan: the pilot is inside it
    /**
     * The machine, drawn in the world where its pilot stands (the pilot himself is hidden - he's in the cockpit; see
     * onRenderPlayer). Turned with his body, striding when he walks, punching when he swings. {@code started}
     * restarts it (and its assembly) when he climbs in again; {@code scale} shrinks it away when the mode ends.
     */
    static void drawTitan(PoseStack ps, MultiBufferSource buf, Vec3 cam, net.minecraft.world.entity.player.Player p, int mask, double started, String loop, float scale, float pt) {
        Anim a = anim("titan#" + p.getId(), "mech_titan", "assemble", loop, started);
        a.mask = mask;
        Renderer r = RENDERERS.computeIfAbsent("mech_titan", Renderer::new);
        double x = net.minecraft.util.Mth.lerp(pt, p.xo, p.getX()), y = net.minecraft.util.Mth.lerp(pt, p.yo, p.getY()), z = net.minecraft.util.Mth.lerp(pt, p.zo, p.getZ());
        float yaw = net.minecraft.util.Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot);
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        int light = mc.getEntityRenderDispatcher().getPackedLightCoords(p, pt);
        ps.pushPose();
        ps.translate(x - cam.x, y - cam.y, z - cam.z);
        ps.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));   // as GeckoLib turns its entities: the model's front (north) where he faces
        ps.scale(scale, scale, scale);
        ps.translate(0, 1.5, 0);                              // the model's feet are 24 px below its origin
        r.render(ps, a, buf, null, null, light);
        ps.popPose();
    }

    /** The pilot is inside his machine: don't draw him (the machine is drawn in his place by FxRenderer's level pass). */
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onRenderPlayer(net.minecraftforge.client.event.RenderPlayerEvent.Pre e) {
        if (MechFxClient.pilot(e.getEntity().getId()) != null) e.setCanceled(true);
    }

    /** Every pilot's machine this frame (from FxRenderer, after its own effects). */
    static void drawPilots(PoseStack ps, MultiBufferSource buf, Vec3 cam, float pt) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level == null) return;
        double now = mc.level.getGameTime() + pt;
        for (net.minecraft.world.entity.player.Player p : mc.level.players()) {
            double[] mech = MechFxClient.pilot(p.getId());
            if (mech == null || p.isInvisible() || p.isSpectator()) continue;
            if (p == mc.player && mc.options.getCameraType().isFirstPerson()) continue;   // you're in the cockpit: the HUD frame shows instead
            float out = (float) net.minecraft.util.Mth.clamp((mech[1] + 8.0 - now) / 8.0, 0.0, 1.0);
            if (out <= 0) continue;
            double speed = Math.sqrt((p.getX() - p.xo) * (p.getX() - p.xo) + (p.getZ() - p.zo) * (p.getZ() - p.zo));
            String loop = p.swinging ? "punch" : speed > 0.04 ? "walk" : "idle";
            drawTitan(ps, buf, cam, p, (int) mech[2], mech[0], loop, out, pt);
        }
    }

    // ================================================================== fists out in the world (FxRenderer's level pass)
    private record FistDraw(String key, Vec3 at, Vec3 dir, float scale, boolean forearm, double started) {}
    private static final List<FistDraw> FISTS = new ArrayList<>();

    /** Queue a fist for this frame: its knuckles at {@code at}, pointing along {@code dir}, {@code scale} blocks per 16 px. */
    static void fist(String key, Vec3 at, Vec3 dir, float scale, boolean forearm, double started) {
        if (FISTS.size() < 32) FISTS.add(new FistDraw(key, at, dir, scale, forearm, started));
    }

    /** Draw the queued fists (called from FxRenderer with the level's pose stack and buffers), then forget them. */
    static void drawWorld(PoseStack ps, MultiBufferSource buf, Vec3 cam) {
        if (FISTS.isEmpty()) return;
        Renderer r = RENDERERS.computeIfAbsent("mech_fist", Renderer::new);
        for (FistDraw f : FISTS) {
            Anim a = anim("fist#" + f.key, "mech_fist", "clench", "idle", f.started);
            a.forearm = f.forearm;
            Vec3 d = f.dir.lengthSqr() < 1e-8 ? new Vec3(0, 0, 1) : f.dir.normalize();
            ps.pushPose();
            ps.translate(f.at.x - cam.x, f.at.y - cam.y, f.at.z - cam.z);
            // the model points north (-Z): turn -Z onto the fist's direction
            ps.mulPose(new Quaternionf().rotationTo(new Vector3f(0, 0, -1), new Vector3f((float) d.x, (float) d.y, (float) d.z)));
            ps.scale(f.scale, f.scale, f.scale);
            ps.translate(0, 0, 0.6);   // the knuckles (z -9 px) sit at the point given
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            int light = mc.level == null ? LightTexture.FULL_BRIGHT : net.minecraft.client.renderer.LevelRenderer.getLightColor(mc.level, net.minecraft.core.BlockPos.containing(f.at));
            r.render(ps, a, buf, null, null, light);
            ps.popPose();
        }
        FISTS.clear();
    }

    static void clear() { FISTS.clear(); ANIMS.clear(); }
}
