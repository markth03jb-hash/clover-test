package com.ironspellgrimoire.client;

import com.ironspellgrimoire.entity.Rival;
import com.ironspellgrimoire.entity.TorLegionnaire;
import com.ironspellgrimoire.server.BossKit;
import com.ironspellgrimoire.server.MiraAI;
import com.ironspellgrimoire.server.SerenAI;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.List;
import java.util.Map;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.client.event.ModelEvent;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * The rivals on screen: a player-shaped body in their own skin (textures/entity/rival_&lt;name&gt;.png) with their gear
 * (tools/blender/rival_models.py: models/item/rival_*.obj) set on the bones exactly where the approved model sheet put
 * it - a sword's grip in the fist, a hat on the head, coins circling the waist. They kneel when they yield.
 * Placements are in the sheet's own numbers (Blender pixels, feet at 0, front -Y) and converted here.
 */
public class RivalRenderer<T extends Mob> extends HumanoidMobRenderer<T, PlayerModel<T>> {
    public enum Bone { HEAD, BODY, RARM, LARM }

    /**
     * One piece: its model, the bone it rides, the sheet's arm angle (for arm pieces), where the sheet put it
     * (pixels), its rotation (degrees, Blender XYZ), scale, and a spin (degrees per tick about its own up).
     */
    public record Piece(String model, Bone bone, float armDeg, float[] at, float[] rot, float scale, float spin) {
        Piece(String model, Bone bone, float armDeg, float[] at, float[] rot, float scale) { this(model, bone, armDeg, at, rot, scale, 0); }
        ResourceLocation rl() { return ResourceLocation.fromNamespaceAndPath("iron_spell_grimoire", "item/rival_" + model); }
    }

    static float[] v(double x, double y, double z) { return new float[]{(float) x, (float) y, (float) z}; }
    static final float S35 = Mth.sin(35 * Mth.DEG_TO_RAD), C35 = Mth.cos(35 * Mth.DEG_TO_RAD);
    static final float S40 = Mth.sin(40 * Mth.DEG_TO_RAD), C40 = Mth.cos(40 * Mth.DEG_TO_RAD);
    static final float S30 = Mth.sin(30 * Mth.DEG_TO_RAD), C30 = Mth.cos(30 * Mth.DEG_TO_RAD);

    /** Each skin's gear, in the sheet's numbers (tools/blender/rival_models.py, rival()). */
    public static final Map<String, List<Piece>> GEAR = new java.util.HashMap<>(Map.of(
            "rival_seren", List.of(
                    new Piece("seren_blade", Bone.RARM, -35, v(-5.5, -10 * S35, 22 - 10 * C35), v(75, 0, 0), 1),
                    new Piece("seren_tail", Bone.HEAD, 0, v(0, 0, 23.5), v(0, 0, 0), 1),
                    new Piece("seren_coattails", Bone.BODY, 0, v(0, 0, 13), v(0, 0, 0), 1),
                    new Piece("seren_halo", Bone.BODY, 0, v(0, 5, 19.5), v(0, 0, 0), 0.85f)),
            "rival_brannoch", List.of(
                    new Piece("brannoch_sword", Bone.RARM, -30, v(-6, -10 * S30, 22 - 10 * C30), v(70, 0, 0), 1),
                    new Piece("brannoch_banner", Bone.BODY, 0, v(1.5, 3.2, 8), v(0, 0, 0), 1),
                    new Piece("brannoch_pauldron", Bone.RARM, 0, v(-6.2, 0, 21.8), v(0, 0, 0), 1),
                    new Piece("brannoch_tower", Bone.LARM, 0, v(6.4, 0, 23.6), v(0, 0, 0), 1),
                    new Piece("brannoch_mantle", Bone.BODY, 0, v(0, 0, 23.6), v(0, 0, 0), 1)),
            "rival_legion", List.of(
                    new Piece("legion_spear", Bone.RARM, -20, v(-6, -3, 11), v(-20, 0, 0), 1),
                    new Piece("legion_shield", Bone.LARM, -30, v(7.5, -4.5, 15), v(0, 0, 15), 1),
                    new Piece("legion_plume", Bone.HEAD, 0, v(0, 0, 32.5), v(0, 0, 0), 1)),
            "rival_alder", List.of(
                    new Piece("alder_sword", Bone.RARM, -40, v(-6, -10 * S40, 22 - 10 * C40), v(80, 0, 0), 1),
                    new Piece("alder_bell", Bone.BODY, 0, v(3.2, -2.6, 11.2), v(0, 0, 0), 0.9f),
                    new Piece("alder_scarf", Bone.BODY, 0, v(0, 0, 23.5), v(0, 0, 0), 1)),
            "rival_wren", List.of(
                    new Piece("wren_staff", Bone.LARM, -20, v(6, -2.5, 11), v(-8, 0, 0), 1),
                    new Piece("wren_braid", Bone.HEAD, 0, v(-3.2, -2.2, 26), v(0, 0, 0), 1),
                    new Piece("wren_bells", Bone.BODY, 0, v(0, 0, 20), v(0, 0, 0), 1, 1.2f)),
            "rival_mira", List.of(
                    new Piece("mira_hat", Bone.HEAD, 0, v(0, 0, 32.2), v(0, 0, -10), 1),
                    new Piece("mira_book", Bone.LARM, -55, v(8, -7, 20), v(60, 0, 20), 1),
                    new Piece("mira_cat", Bone.BODY, 0, v(5.4, -0.6, 24.2), v(0, 0, 30), 0.7f),
                    new Piece("mira_coins", Bone.BODY, 0, v(0, 0, 16), v(0, 0, 0), 1, 2f),
                    new Piece("mira_scales", Bone.BODY, 0, v(-4.8, -2.4, 11.5), v(0, 0, 0), 0.8f))));
    /** The wild families, the night hunter and ASHFALL's people (tools/blender/family_models.py, FAMILY - printed by its "java" mode). */
    static final Map<String, List<Piece>> FAMILY_GEAR = Map.ofEntries(
            Map.entry("family_bog_crawler", List.of(
                    new Piece("bog_club", Bone.RARM, -35, v(-6.000, -5.736, 13.808), v(75, 0, 0), 1.000f),
                    new Piece("reed_crown", Bone.HEAD, 0, v(0.000, 0.000, 31.500), v(0, 0, 0), 1.000f))),
            Map.entry("family_mire_hexer", List.of(
                    new Piece("hexer_staff", Bone.RARM, -20, v(-5.500, -3.420, 12.603), v(-8, 0, 0), 1.000f))),
            Map.entry("family_fen_matron", List.of(
                    new Piece("root_crown", Bone.HEAD, 0, v(0.000, 0.000, 32.000), v(0, 0, 0), 1.000f),
                    new Piece("bone_rattle", Bone.LARM, -40, v(5.500, -6.428, 14.340), v(80, 0, 0), 1.000f),
                    new Piece("moss_train", Bone.BODY, 0, v(0.000, 0.000, 15.000), v(0, 0, 0), 1.000f))),
            Map.entry("family_scree_brute", List.of(
                    new Piece("stone_maul", Bone.RARM, -30, v(-6.000, -5.000, 13.340), v(70, 0, 0), 1.000f),
                    new Piece("rock_plates", Bone.BODY, 0, v(0.000, 0.000, 0.000), v(0, 0, 0), 1.000f))),
            Map.entry("family_stonecaller", List.of(
                    new Piece("caller_staff", Bone.LARM, -20, v(6.000, -3.420, 12.603), v(-8, 0, 0), 1.000f))),
            Map.entry("family_peak_warden", List.of(
                    new Piece("horned_helm", Bone.HEAD, 0, v(0.000, 0.000, 27.500), v(0, 0, 0), 1.000f),
                    new Piece("slab_shield", Bone.LARM, -20, v(7.600, -5.500, 13.000), v(0, 0, 10), 1.000f),
                    new Piece("warden_pick", Bone.RARM, -30, v(-6.000, -5.000, 13.340), v(70, 0, 0), 1.000f))),
            Map.entry("family_dune_stalker", List.of(
                    new Piece("curved_blade", Bone.RARM, -40, v(-6.000, -6.428, 14.340), v(80, 0, 0), 1.300f),
                    new Piece("curved_blade", Bone.LARM, -40, v(6.000, -6.428, 14.340), v(80, 0, 0), 1.300f),
                    new Piece("head_wrap", Bone.HEAD, 0, v(0.000, 0.000, 29.000), v(0, 0, 0), 1.000f))),
            Map.entry("family_sandwraith", List.of(
                    new Piece("censer", Bone.LARM, -30, v(5.500, -5.000, 13.340), v(-30, 0, 0), 1.300f),
                    new Piece("sand_mantle", Bone.BODY, 0, v(0.000, 0.000, 21.000), v(0, 0, 0), 1.000f))),
            Map.entry("family_sun_herald", List.of(
                    new Piece("sun_disc", Bone.BODY, 0, v(0.000, 5.000, 29.000), v(0, 0, 0), 1.000f),
                    new Piece("glaive", Bone.RARM, -20, v(-6.000, -3.420, 12.603), v(-8, 0, 0), 1.000f))),
            Map.entry("family_gloamstalker", List.of(
                    new Piece("hooked_blade", Bone.RARM, -50, v(-5.500, -7.660, 15.572), v(75, 0, 0), 1.100f),
                    new Piece("gloam_hood", Bone.HEAD, 0, v(0.000, 0.000, 27.000), v(0, 0, 0), 1.000f),
                    new Piece("coat_tails", Bone.BODY, 0, v(0.000, 0.000, 13.000), v(0, 0, 0), 1.000f))),
            Map.entry("family_rime_brute", List.of(
                    new Piece("ice_axe", Bone.RARM, -30, v(-6.000, -5.000, 13.340), v(70, 0, 0), 1.400f),
                    new Piece("fur_mantle", Bone.BODY, 0, v(0.000, 0.000, 0.000), v(0, 0, 0), 1.000f))),
            Map.entry("family_frost_chanter", List.of(
                    new Piece("ice_staff", Bone.LARM, -20, v(6.000, -3.420, 12.603), v(-8, 0, 0), 1.000f))),
            Map.entry("family_hoarfrost_jarl", List.of(
                    new Piece("ice_crown", Bone.HEAD, 0, v(0.000, 0.000, 30.800), v(0, 0, 0), 1.000f),
                    new Piece("frost_greatsword", Bone.RARM, -35, v(-6.000, -5.736, 13.808), v(75, 0, 0), 1.000f),
                    new Piece("fur_cloak", Bone.BODY, 0, v(0.000, 0.000, 0.000), v(0, 0, 0), 1.000f))),
            Map.entry("family_hollow_crawler", List.of(
                    new Piece("hook_claw", Bone.RARM, -45, v(-5.500, -7.071, 14.929), v(85, 0, 0), 1.000f),
                    new Piece("hook_claw", Bone.LARM, -45, v(5.500, -7.071, 14.929), v(85, 0, 0), 1.000f))),
            Map.entry("family_hollow_singer", List.of(
                    new Piece("fork_staff", Bone.RARM, -20, v(-5.500, -3.420, 12.603), v(-8, 0, 0), 1.000f))),
            Map.entry("family_deepwarden", List.of(
                    new Piece("stone_shell", Bone.BODY, 0, v(0.000, 0.000, 0.000), v(0, 0, 0), 1.000f),
                    new Piece("heart_lantern", Bone.BODY, 0, v(0.000, 0.000, 0.000), v(0, 0, 0), 1.000f))),
            Map.entry("twin_haldric", List.of(
                    new Piece("tower_shield", Bone.LARM, -25, v(7.400, -5.800, 13.000), v(0, 0, 8), 1.000f),
                    new Piece("flanged_mace", Bone.RARM, -30, v(-6.000, -5.000, 13.340), v(70, 0, 0), 1.000f),
                    new Piece("wrist_chain", Bone.RARM, -30, v(-6.000, -4.000, 15.072), v(60, 0, 0), 1.000f))),
            Map.entry("twin_oswin", List.of(
                    new Piece("iron_greatsword", Bone.RARM, -35, v(-6.000, -5.736, 13.808), v(75, 0, 0), 1.000f),
                    new Piece("wrist_chain", Bone.LARM, -35, v(6.000, -4.589, 15.447), v(60, 0, 0), 1.000f))),
            // ASHFALL, Arc 1
            Map.entry("story_corvin", List.of(
                    new Piece("knight_longsword", Bone.RARM, -30, v(-6.000, -5.000, 13.340), v(70, 0, 0), 1.000f),
                    new Piece("half_cloak", Bone.BODY, 0, v(0.000, 0.000, 0.000), v(0, 0, 0), 1.000f))),
            Map.entry("story_sallow", List.of(
                    new Piece("sallow_mitre", Bone.HEAD, 0, v(0.000, 0.000, 32.000), v(0, 0, 0), 1.000f),
                    new Piece("brand_knife", Bone.RARM, -45, v(-6.000, -7.071, 14.929), v(85, 0, 0), 1.308f),
                    new Piece("reliquary", Bone.LARM, -15, v(6.000, -2.588, 12.341), v(-15, 0, 0), 1.000f))),
            Map.entry("story_penitent", List.of(
                    new Piece("rust_sickle", Bone.RARM, -40, v(-6.000, -6.428, 14.340), v(80, 0, 0), 1.400f))),
            Map.entry("story_ilsa", List.<Piece>of()),
            // ASHFALL, Arc II
            Map.entry("story_vey", List.of(
                    new Piece("vey_mantle", Bone.BODY, 0, v(0.000, 0.000, 0.000), v(0, 0, 0), 1.000f),
                    new Piece("chain_of_office", Bone.BODY, 0, v(0.000, 0.000, 0.000), v(0, 0, 0), 1.000f),
                    new Piece("ledger_staff", Bone.LARM, -25, v(6.000, -4.226, 12.937), v(0, 0, 0), 1.000f))),
            Map.entry("story_warden", List.of(
                    new Piece("warden_halberd", Bone.RARM, -20, v(-6.000, -3.420, 12.603), v(0, 0, 0), 1.000f),
                    new Piece("warden_crest", Bone.HEAD, 0, v(0.000, 0.000, 32.000), v(0, 0, 0), 1.000f))),
            Map.entry("story_tamsin", List.of(
                    new Piece("tamsin_wand", Bone.RARM, -40, v(-5.500, -6.428, 14.340), v(80, 0, 0), 1.600f),
                    new Piece("tamsin_satchel", Bone.BODY, 0, v(0.000, 0.000, 0.000), v(0, 0, 0), 1.000f))),
            // ASHFALL, Arc III
            Map.entry("story_mother", List.of(
                    new Piece("mother_crown", Bone.HEAD, 0, v(0.000, 0.000, 32.000), v(0, 0, 0), 1.000f),
                    new Piece("mother_heart", Bone.BODY, 0, v(0.000, 0.000, 0.000), v(0, 0, 0), 1.000f),
                    new Piece("mother_limbs", Bone.BODY, 0, v(0.000, 0.000, 0.000), v(0, 0, 0), 1.000f),
                    new Piece("mother_pauldrons", Bone.BODY, 0, v(0.000, 0.000, 0.000), v(0, 0, 0), 1.000f),
                    new Piece("mother_skirt", Bone.BODY, 0, v(0.000, 0.000, 0.000), v(0, 0, 0), 1.000f),
                    new Piece("mother_claw", Bone.RARM, -35, v(-6.000, -5.736, 13.808), v(75, 0, 0), 1.000f),
                    new Piece("mother_claw", Bone.LARM, -35, v(6.000, -5.736, 13.808), v(75, 0, 0), 1.000f))),
            Map.entry("story_maren", List.of(
                    new Piece("boar_spear", Bone.RARM, -15, v(-5.500, -2.588, 12.341), v(0, 0, 0), 1.000f),
                    new Piece("round_shield", Bone.LARM, -25, v(7.200, -5.200, 13.000), v(0, 0, 10), 1.000f))),
            Map.entry("story_sedge", List.of(
                    new Piece("sedge_cane", Bone.LARM, -20, v(6.000, -3.420, 12.603), v(0, 0, 0), 1.000f))),
            Map.entry("story_militia", List.of(
                    new Piece("pitchfork", Bone.RARM, -15, v(-6.000, -2.588, 12.341), v(0, 0, 0), 1.000f))));
    static { GEAR.putAll(FAMILY_GEAR); }

    /** The legionnaire who carries Brannoch's standard: the banner instead of the spear. */
    static final List<Piece> BEARER = List.of(
            new Piece("brannoch_banner", Bone.BODY, 0, v(1.5, 3.2, 8), v(0, 0, 0), 1),
            new Piece("legion_shield", Bone.LARM, -30, v(7.5, -4.5, 15), v(0, 0, 15), 1),
            new Piece("legion_plume", Bone.HEAD, 0, v(0, 0, 32.5), v(0, 0, 0), 1));

    public static void registerModels(ModelEvent.RegisterAdditional e) {
        for (List<Piece> l : GEAR.values()) for (Piece p : l) e.register(p.rl());
    }

    private final ResourceLocation tex;
    private final float size;
    private final boolean slim;
    private final String skin;

    public RivalRenderer(EntityRendererProvider.Context ctx, String skin, boolean slim, float size) {
        super(ctx, new Model<>(ctx.bakeLayer(slim ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER), slim, skin), 0.5f * size);
        this.tex = ResourceLocation.fromNamespaceAndPath("iron_spell_grimoire", "textures/entity/" + skin + ".png");
        this.size = size;
        this.slim = slim;
        this.skin = skin;
        addLayer(new GearLayer<>(this));
    }

    @Override public ResourceLocation getTextureLocation(T e) { return tex; }
    public String skinName() { return skin; }
    @Override protected void scale(T e, PoseStack ps, float pt) { ps.scale(size, size, size); }

    static boolean kneeling(Mob e) { return e instanceof Rival r && (r.flag() & Rival.YIELDED) != 0 || e instanceof com.ironspellgrimoire.entity.YieldingBoss s && s.yielded(); }

    @Override
    protected void setupRotations(T e, PoseStack ps, float bob, float yaw, float pt) {
        super.setupRotations(e, ps, bob, yaw, pt);
        if (kneeling(e)) { ps.translate(0, -0.42F, 0); ps.mulPose(Axis.XP.rotationDegrees(12F)); }
    }

    static class Model<T extends Mob> extends PlayerModel<T> {
        final String skin;
        Model(ModelPart root, boolean slim, String skin) { super(root, slim); this.skin = skin; }

        @Override
        public void setupAnim(T e, float limbSwing, float limbAmount, float age, float headYaw, float headPitch) {
            super.setupAnim(e, limbSwing, limbAmount, age, headYaw, headPitch);
            // the hands that hold something sit a little forward, like a player's holding an item
            boolean sword = skin.equals("rival_seren") || skin.equals("rival_brannoch") || skin.equals("rival_alder") || skin.equals("rival_legion");
            if (sword) this.rightArm.xRot = this.rightArm.xRot * 0.5F - 0.31F;
            if (skin.equals("rival_wren") || skin.equals("rival_legion")) this.leftArm.xRot = this.leftArm.xRot * 0.5F - 0.31F;
            if (skin.equals("rival_mira")) this.leftArm.xRot = -0.96F;   // her ledger held up to read
            if (skin.startsWith("family_") || skin.startsWith("twin_") || skin.startsWith("story_")) {   // a hand that holds something sits forward, as on the model sheet
                List<Piece> g = FAMILY_GEAR.get(skin);
                if (g != null) for (Piece p : g) {
                    if (p.bone() == Bone.RARM) this.rightArm.xRot = this.rightArm.xRot * 0.5F + p.armDeg() * Mth.DEG_TO_RAD;
                    if (p.bone() == Bone.LARM) this.leftArm.xRot = this.leftArm.xRot * 0.5F + p.armDeg() * Mth.DEG_TO_RAD;
                }
            }
            if (e instanceof Rival r) {
                float t = (age - r.poseAt()) / 20F;
                float in = Mth.clamp(t * 4F, 0F, 1F), out = Mth.clamp((1.4F - t) * 3F, 0F, 1F), k = in * out;
                boolean parry = r instanceof com.ironspellgrimoire.entity.SerenVale && (r.flag() & SerenAI.PARRY) != 0;
                boolean noon = r instanceof com.ironspellgrimoire.entity.SerenVale && (r.flag() & SerenAI.NOON) != 0;
                boolean fore = r instanceof com.ironspellgrimoire.entity.MiraHollowell && (r.flag() & MiraAI.FORECLOSE) != 0;
                if (kneeling(r)) {
                    this.head.xRot = 0.55F; this.rightArm.xRot = -0.2F; this.leftArm.xRot = -0.2F;
                    this.rightLeg.xRot = -1.4F; this.leftLeg.xRot = 0.1F;
                } else if (parry) {   // the blade raised across her, edge out
                    this.rightArm.xRot = -1.7F; this.rightArm.yRot = -0.5F;
                } else if (noon || fore) {
                    this.rightArm.xRot = -2.9F; this.leftArm.xRot = -2.9F; this.head.xRot = -0.3F;
                } else switch (r.pose()) {
                    case BossKit.P_CAST -> { this.rightArm.xRot = -1.5F * k + this.rightArm.xRot * (1 - k); this.leftArm.xRot = -0.5F * k + this.leftArm.xRot * (1 - k); }
                    case BossKit.P_SLASH -> {
                        float s = Mth.clamp(t / 0.45F, 0, 1);
                        this.rightArm.xRot = (-2.6F + 2.0F * s) * out; this.rightArm.yRot = (0.7F - 1.5F * s) * out;
                    }
                    case BossKit.P_DASH -> { this.rightArm.xRot = 0.9F * k - 0.31F; this.leftArm.xRot = 0.9F * k; }
                    case BossKit.P_RAISE -> { this.rightArm.xRot = -2.9F * k; this.leftArm.xRot = -2.9F * k; this.head.xRot = -0.3F * k; }
                    case BossKit.P_THROW -> { this.leftArm.xRot = -2.2F * k + this.leftArm.xRot * (1 - k); }
                    default -> { }
                }
            }
            this.body.xRot = 0; this.body.yRot = 0; this.body.zRot = 0;
            this.rightArm.x = -5; this.rightArm.z = 0; this.leftArm.x = 5; this.leftArm.z = 0;
            this.hat.copyFrom(this.head);
            this.jacket.copyFrom(this.body);
            this.rightSleeve.copyFrom(this.rightArm);
            this.leftSleeve.copyFrom(this.leftArm);
            this.rightPants.copyFrom(this.rightLeg);
            this.leftPants.copyFrom(this.leftLeg);
        }
    }

    /** Draws the gear on the bones. */
    static class GearLayer<T extends Mob> extends RenderLayer<T, PlayerModel<T>> {
        private final RivalRenderer<T> r;
        GearLayer(RivalRenderer<T> r) { super((RenderLayerParent<T, PlayerModel<T>>) r); this.r = r; }

        @Override
        public void render(PoseStack ps, MultiBufferSource buf, int light, T e, float limbSwing, float limbAmount, float pt, float age, float headYaw, float headPitch) {
            List<Piece> gear = e instanceof TorLegionnaire l && l.bearer() ? BEARER : GEAR.get(r.skin);
            if (gear == null) return;
            PlayerModel<T> m = getParentModel();
            boolean glow = e instanceof com.ironspellgrimoire.entity.SerenVale s && (s.flag() & SerenAI.PARRY) != 0;
            for (Piece p : gear) {
                ps.pushPose();
                ModelPart part = switch (p.bone()) { case HEAD -> m.head; case BODY -> m.body; case RARM -> m.rightArm; case LARM -> m.leftArm; };
                part.translateAndRotate(ps);
                place(ps, p, r.slim, p.spin() * age);
                ParasiteArms.drawPiece(ps, buf, glow && p.model().endsWith("blade") ? LightTexture.FULL_BRIGHT : light, p.rl());
                ps.popPose();
            }
        }
    }

    /**
     * Moves the pose from a bone's pivot to where the sheet put the piece. In the sheet (Blender pixels, feet at 0,
     * front -Y) an arm hangs from (centre, 0, 22) rotated {@code armDeg} about X, and the head and body turn about
     * (0, 0, 24); in game a bone's pivot is in model space (pixels/16, y down, front -Z), and an arm's pivot sits a
     * pixel (half for slim arms) inside its centre line.
     */
    static boolean blade(Piece p) { return p.model().endsWith("_blade") || p.model().endsWith("_sword"); }

    static void place(PoseStack ps, Piece p, boolean slim, float spin) {
        boolean arm = p.bone() == Bone.RARM || p.bone() == Bone.LARM;
        float side = p.bone() == Bone.RARM ? -1 : 1;
        float centre = arm ? side * (4 + (slim ? 1.5F : 2F)) : 0;
        float pivotZ = arm ? 22 : 24;
        float inset = arm ? side * (slim ? 0.5F : 1F) : 0;   // the arm's centre line, from its pivot (model pixels)
        float d = Mth.DEG_TO_RAD;
        // in the sheet's frame: undo the arm's angle, move from the pivot to the piece, then the piece's own turn
        Matrix4f m = new Matrix4f().rotateX(-p.armDeg() * d)
                .translate(p.at()[0] - centre, p.at()[1], p.at()[2] - pivotZ)
                .rotateZ((p.rot()[2] + spin) * d).rotateY(p.rot()[1] * d).rotateX(p.rot()[0] * d)
                .rotateZ(blade(p) ? 90 * d : 0)   // a sword turned on its own length: edge up, the flat of the blade upright, not lying flat
                .scale(p.scale());
        Matrix4f P = new Matrix4f(1, 0, 0, 0, 0, 0, 1, 0, 0, -1, 0, 0, 0, 0, 0, 1);   // (x, y, z) -> (x, -z, y)  (columns)
        Matrix4f f = new Matrix4f().translate(inset / 16F, 0, 0).scale(1 / 16F).mul(P).mul(m).scale(16F).mul(P);
        ps.last().pose().mul(f);
        ps.last().normal().mul(new Matrix3f(new Matrix4f(f).normalize3x3()));
    }
}
