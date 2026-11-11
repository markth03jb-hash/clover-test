package com.ironspellgrimoire.client;

import com.ironspellgrimoire.entity.Reflection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;

/** The Reflection wears its challenger's own skin. */
public class ReflectionRenderer extends BossRenderer<Reflection> {
    public ReflectionRenderer(EntityRendererProvider.Context ctx) { super(ctx, "reflection", false, 1.0F); }

    @Override
    public ResourceLocation getTextureLocation(Reflection e) {
        java.util.UUID o = e.owner();
        var conn = Minecraft.getInstance().getConnection();
        if (o != null && conn != null) {
            PlayerInfo pi = conn.getPlayerInfo(o);
            if (pi != null) return pi.getSkinLocation();
        }
        return DefaultPlayerSkin.getDefaultSkin(o != null ? o : e.getUUID());
    }
}
