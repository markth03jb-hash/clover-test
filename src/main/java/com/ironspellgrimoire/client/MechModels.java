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
 * The Mech Grimoire's GeckoLib models (made by tools/mech_models.py), drawn as hard light - see-through, glowing,
 * full bright:
 *   mech_titan - the war machine standing round a pilot, only the parts he has built (each part is a bone: the rest
 *                are hidden). It's printed in from the feet up, idles, strides when he walks and punches when he swings.
 *   mech_fist  - the Rocket Fist flying out and back, and the Titan Buster's giant fist dropping out of the sky.
 */
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

    /** Hard light: translucent and emissive, and only the built parts shown. No block-centring: we place it ourselves. */
    private static final class Renderer extends GeoObjectRenderer<Anim> {
        Renderer(String name) { super(new Model(name)); }

        @Override
        public RenderType getRenderType(Anim a, ResourceLocation texture, MultiBufferSource buf, float partialTick) {
            return RenderType.entityTranslucentEmissive(texture);
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

    // ================================================================== the titan, round its pilot (GeoFormLayer)
    /**
     * Draw the titan in the player model's space (as GeoFormLayer gives it). {@code started} restarts it (and its
     * assembly) when the pilot climbs in again; {@code alpha} fades it in and out.
     */
    static void drawTitan(PoseStack ps, MultiBufferSource buf, int owner, int mask, double started, String loop, float scale) {
        Anim a = anim("titan#" + owner, "mech_titan", "assemble", loop, started);
        a.mask = mask;
        Renderer r = RENDERERS.computeIfAbsent("mech_titan", Renderer::new);
        ps.pushPose();
        ps.mulPose(Axis.ZP.rotationDegrees(180.0F));   // Blockbench's orientation to the player model's
        ps.translate(0, -1.5, 0);                        // its origin is the player model's (24 px above the feet)
        ps.scale(scale, scale, scale);
        ps.translate(0, 1.5, 0);
        r.render(ps, a, buf, null, null, LightTexture.FULL_BRIGHT);
        ps.popPose();
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
            r.render(ps, a, buf, null, null, LightTexture.FULL_BRIGHT);
            ps.popPose();
        }
        FISTS.clear();
    }

    static void clear() { FISTS.clear(); ANIMS.clear(); }
}
