package com.example.arcaneaddon.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
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

import java.util.HashMap;
import java.util.Map;

/**
 * The animated GeckoLib models worn by the forms (assets/arcane_addon/geo, animations, textures/geo):
 *   devil_wings        Devil Union         Asta's single black wing - open, idle, run, fly, glide, fold
 *   beelzebub_wings    Beelzebub           open, idle, fly
 *   beelzebub_horns    Beelzebub           grow, idle
 *   bone_sword         Zenon's bone sword  form, idle
 *   knight_*           Knight's Armor      equip, idle (+ plume sway / running), cape idle, run, air
 *
 * Every model is drawn inside a player model part's own transform (so it follows that part exactly). The models
 * are made in the player model's space; a half turn about Z here lines GeckoLib's Blockbench orientation up with it.
 * Each player gets his own animated instance of each piece, so every one moves on its own; an instance restarts
 * (and plays its intro again) when the form starts again.
 */
public final class GeoForms {
    private GeoForms() {}

    /** One animated piece on one player. */
    public static final class Anim implements GeoAnimatable {
        private final AnimatableInstanceCache cache = new InstancedAnimatableInstanceCache(this);
        final String model;
        final String intro;
        String loop;
        ResourceLocation texture;
        double started;
        private boolean introUsed;
        private String startLoop, currentKey;
        private RawAnimation current;

        Anim(String model, String intro, String loop, double started) {
            this.model = model; this.intro = intro; this.loop = loop; this.started = started; this.startLoop = loop;
        }

        private String name(String anim) { return "animation." + model + "." + anim; }

        private PlayState handle(AnimationState<Anim> state) {
            if (intro != null && !introUsed && !loop.equals(startLoop)) introUsed = true;   // moved on: no replay
            boolean withIntro = intro != null && !introUsed;
            String key = withIntro ? intro + ">" + loop : loop;
            if (!key.equals(currentKey)) {
                currentKey = key;
                current = withIntro ? RawAnimation.begin().thenPlay(name(intro)).thenLoop(name(loop)) : RawAnimation.begin().thenLoop(name(loop));
            }
            return state.setAndContinue(current);
        }

        @Override
        public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
            controllers.add(new AnimationController<>(this, "main", 5, this::handle));   // 5-tick blend between states
        }

        @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }

        @Override public double getTick(Object o) { return RenderUtils.getCurrentTick(); }
    }

    private static final class Model extends GeoModel<Anim> {
        private final ResourceLocation geo, anim, tex;

        Model(String name) {
            geo = ResourceLocation.fromNamespaceAndPath("arcane_addon", "geo/" + name + ".geo.json");
            anim = ResourceLocation.fromNamespaceAndPath("arcane_addon", "animations/" + name + ".animation.json");
            tex = ResourceLocation.fromNamespaceAndPath("arcane_addon", "textures/geo/" + name + ".png");
        }

        @Override public ResourceLocation getModelResource(Anim a) { return geo; }
        @Override public ResourceLocation getTextureResource(Anim a) { return a.texture != null ? a.texture : tex; }
        @Override public ResourceLocation getAnimationResource(Anim a) { return anim; }
    }

    /** GeckoLib's object renderer without its block-centring offset: we place the model ourselves. */
    private static final class Renderer extends GeoObjectRenderer<Anim> {
        Renderer(String name) { super(new Model(name)); }

        @Override
        public void preRender(PoseStack ps, Anim a, BakedGeoModel model, MultiBufferSource bufferSource, VertexConsumer buffer,
                              boolean isReRender, float partialTick, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
            this.objectRenderTranslations = new Matrix4f(ps.last().pose());
        }
    }

    private static final Map<String, Renderer> RENDERERS = new HashMap<>();
    private static final Map<String, Anim> ANIMS = new HashMap<>();

    /**
     * Draw piece {@code model} for {@code owner} at the current pose (already inside the right model part).
     * {@code started} = when the form began (a new value restarts the piece with its intro), {@code loop} = the
     * looping animation it should be in now (it blends there), {@code intro} = played once first, or null.
     */
    public static void draw(String model, int owner, double started, String intro, String loop, ResourceLocation texture,
                            PoseStack ps, MultiBufferSource buf, int light) {
        String key = model + "#" + owner;
        Anim a = ANIMS.get(key);
        if (a == null || a.started != started) {
            a = new Anim(model, intro, loop, started);
            ANIMS.put(key, a);
            if (ANIMS.size() > 256) ANIMS.clear();
        }
        a.loop = loop;
        a.texture = texture;
        Renderer r = RENDERERS.computeIfAbsent(model, Renderer::new);
        ps.pushPose();
        ps.mulPose(Axis.ZP.rotationDegrees(180.0F));
        r.render(ps, a, buf, null, null, light);
        ps.popPose();
    }
}
