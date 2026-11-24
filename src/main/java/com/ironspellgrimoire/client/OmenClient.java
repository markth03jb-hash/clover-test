package com.ironspellgrimoire.client;

import com.ironspellgrimoire.network.OmenPackets;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * The Grimoire of Ill Omen's marks: over every creature carrying Misfortune toward you, a small black-blue diamond
 * that fills from the bottom up as its Misfortune rises - so you can see whose luck is about to run out. Only its
 * mage sees them. Small and high, so nothing is hidden behind it.
 */
@Mod.EventBusSubscriber(modid = "iron_spell_grimoire", value = Dist.CLIENT)
public final class OmenClient {
    private OmenClient() {}

    static final ResourceLocation MARK = new ResourceLocation("iron_spell_grimoire", "textures/gui/omen_mark.png");
    /** entity id -> shown fill (eased toward the server's). */
    static final Map<Integer, float[]> FILL = new HashMap<>();

    public static void marks(OmenPackets.Marks m) {
        Map<Integer, float[]> next = new HashMap<>();
        for (int i = 0; i < m.ids.length; i++) {
            float[] prev = FILL.get(m.ids[i]);
            next.put(m.ids[i], new float[]{prev == null ? m.fills[i] : prev[0], m.fills[i]});
        }
        FILL.clear();
        FILL.putAll(next);
    }

    @SubscribeEvent public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) { FILL.clear(); }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onPost(RenderLivingEvent.Post<?, ?> e) {
        if (FILL.isEmpty()) return;
        LivingEntity en = e.getEntity();
        float[] f = FILL.get(en.getId());
        if (f == null || en.isInvisible()) return;
        f[0] += (f[1] - f[0]) * 0.15f;
        Minecraft mc = Minecraft.getInstance();
        PoseStack ps = e.getPoseStack();
        ps.pushPose();
        ps.translate(0, en.getBbHeight() + (en.shouldShowName() ? 0.9F : 0.5F), 0);
        ps.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        float pulse = f[0] > 0.8f ? 1f + 0.12f * (float) Math.sin((en.tickCount + e.getPartialTick()) * 0.6f) : 1f;
        ps.scale(-0.025F * pulse, -0.025F * pulse, 0.025F);
        Matrix4f m = ps.last().pose();
        VertexConsumer vc = e.getMultiBufferSource().getBuffer(RenderType.text(MARK));
        int light = LightTexture.FULL_BRIGHT;
        float s = 16, x = -s / 2, y = -s / 2;
        quad(vc, m, x, y, s, 0f, 1f, 0f, 0.5f, light);                    // the empty mark
        float k = Math.max(0f, Math.min(1f, f[0]));
        if (k > 0.01f) {                                                     // the full one, risen to its Misfortune
            float top = y + s * (1 - k);
            quadPart(vc, m, x, top, s, y + s, 0.5f + 0.5f * (1 - k), 1f, light);
        }
        ps.popPose();
    }

    static void quad(VertexConsumer vc, Matrix4f m, float x, float y, float s, float u0, float u1, float v0, float v1, int light) {
        vc.vertex(m, x, y + s, 0).color(1F, 1F, 1F, 1F).uv(u0, v1).uv2(light).endVertex();
        vc.vertex(m, x + s, y + s, 0).color(1F, 1F, 1F, 1F).uv(u1, v1).uv2(light).endVertex();
        vc.vertex(m, x + s, y, 0).color(1F, 1F, 1F, 1F).uv(u1, v0).uv2(light).endVertex();
        vc.vertex(m, x, y, 0).color(1F, 1F, 1F, 1F).uv(u0, v0).uv2(light).endVertex();
    }

    /** The lower part of the full mark, from y0 down to y1 (texture rows v0..v1), drawn a hair in front. */
    static void quadPart(VertexConsumer vc, Matrix4f m, float x, float y0, float s, float y1, float v0, float v1, int light) {
        vc.vertex(m, x, y1, -0.01f).color(1F, 1F, 1F, 1F).uv(0, v1).uv2(light).endVertex();
        vc.vertex(m, x + s, y1, -0.01f).color(1F, 1F, 1F, 1F).uv(1, v1).uv2(light).endVertex();
        vc.vertex(m, x + s, y0, -0.01f).color(1F, 1F, 1F, 1F).uv(1, v0).uv2(light).endVertex();
        vc.vertex(m, x, y0, -0.01f).color(1F, 1F, 1F, 1F).uv(0, v0).uv2(light).endVertex();
    }
}
