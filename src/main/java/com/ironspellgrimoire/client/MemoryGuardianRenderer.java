package com.ironspellgrimoire.client;

import com.ironspellgrimoire.entity.MemoryGuardian;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/** The Memory Guardians: the Copyist's Echo, Taddeus and Elowen, each in their own skin (textures/entity/memory_guardian_N.png); Taddeus a size bigger. */
public class MemoryGuardianRenderer extends BossRenderer<MemoryGuardian> {
    private static final ResourceLocation[] TEX = {
            new ResourceLocation("iron_spell_grimoire", "textures/entity/memory_guardian_0.png"),
            new ResourceLocation("iron_spell_grimoire", "textures/entity/memory_guardian_1.png"),
            new ResourceLocation("iron_spell_grimoire", "textures/entity/memory_guardian_2.png")};

    public MemoryGuardianRenderer(EntityRendererProvider.Context ctx) { super(ctx, "memory_guardian_0", false, 1.0F); }

    @Override public ResourceLocation getTextureLocation(MemoryGuardian e) { return TEX[e.chapter()]; }

    @Override protected void scale(MemoryGuardian e, PoseStack ps, float pt) {
        float s = e.chapter() == 1 ? 1.12F : e.chapter() == 0 ? 0.94F : 1.0F;
        ps.scale(s, s, s);
    }
}
