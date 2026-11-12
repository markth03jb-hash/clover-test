package com.ironspellgrimoire.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;

/**
 * Additive glow layer for effect cores and rims: bright light that adds onto whatever is behind it (like the
 * lightning bolt), so slashes and edges read clearly against both dark and bright scenes. Depth tested, but writes no
 * depth so glows never cut holes in each other.
 */
public final class GlowRenderType extends RenderType {
    private GlowRenderType(String name, VertexFormat fmt, VertexFormat.Mode mode, int size, boolean crumbling, boolean sort, Runnable setup, Runnable clear) {
        super(name, fmt, mode, size, crumbling, sort, setup, clear);
    }

    public static final RenderType GLOW = create("iron_spell_grimoire_glow", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 262144, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RENDERTYPE_LIGHTNING_SHADER)
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .createCompositeState(false));

    private static final java.util.Map<String, RenderType> SMOOTH = new java.util.HashMap<>();

    /**
     * A smooth (linear-filtered, not pixelated) full-bright sprite: alpha-blended, or added on as light. Used for the
     * high-resolution painted effects like the Scarlet Moon aura.
     */
    public static RenderType smoothSprite(net.minecraft.resources.ResourceLocation tex, boolean additive) {
        String key = tex + (additive ? "+" : "");
        RenderType rt = SMOOTH.get(key);
        if (rt != null) return rt;
        RenderType.CompositeState state = RenderType.CompositeState.builder()
                .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                .setTextureState(new TextureStateShard(tex, true, false))
                .setTransparencyState(additive ? LIGHTNING_TRANSPARENCY : TRANSLUCENT_TRANSPARENCY)
                .setWriteMaskState(COLOR_WRITE)
                .setCullState(NO_CULL)
                .setOverlayState(OVERLAY)
                .createCompositeState(false);
        rt = create("iron_spell_grimoire_smooth_" + (additive ? "add" : "blend"), DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 4096, false, true, state);
        SMOOTH.put(key, rt);
        return rt;
    }
}
