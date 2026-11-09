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
}
