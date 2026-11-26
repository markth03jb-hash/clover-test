package com.ironspellgrimoire.client;

import com.ironspellgrimoire.network.BoundPackets;
import com.ironspellgrimoire.registry.AddonItemRegistry;
import com.ironspellgrimoire.server.BookTree;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The Grimoire of the Bound Demon on the client (server/BoundFx):
 *   the demon's eye beside the hotbar - Possession fills it from the lid down, it opens wider as the demon rises, shakes
 *   in the warning, burns red in a takeover; under it the mode, and the awakening's time when awake;
 *   on every awakened mage, one huge branched white wing out of the upper left shoulder, swaying slowly - or, with
 *   Black Wings, a ragged black pair.
 * Small and to the side: nothing covers the view.
 */
@Mod.EventBusSubscriber(modid = "iron_spell_grimoire", value = Dist.CLIENT)
public final class BoundClient {
    private BoundClient() {}

    /** entity id -> {awake until (client ticks), possessed, fallen, six} */
    static final Map<Integer, long[]> STATE = new HashMap<>();
    static long ticks() { var l = Minecraft.getInstance().level; return l == null ? 0 : l.getGameTime(); }

    public static void state(BoundPackets.State m) {
        if (m.awake <= 0 && !m.possessed) { STATE.remove(m.id); return; }
        STATE.put(m.id, new long[]{ticks() + m.awake, m.possessed ? 1 : 0, m.fallen ? 1 : 0, m.six ? 1 : 0});
    }

    @SubscribeEvent public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) { STATE.clear(); }

    // ------------------------------------------------------------------ the wing
    static ItemStack wing, black, halo;

    @SubscribeEvent
    public static void onRender(RenderPlayerEvent.Post e) {
        Player pl = e.getEntity();
        long[] st = STATE.get(pl.getId());
        if (st == null || st[0] <= ticks() || pl.isInvisible()) return;
        if (wing == null) { wing = new ItemStack(AddonItemRegistry.BOUND_WING.get()); black = new ItemStack(AddonItemRegistry.BOUND_WING_BLACK.get()); halo = new ItemStack(AddonItemRegistry.SERAPH_HALO.get()); }
        PoseStack ps = e.getPoseStack();
        float pt = e.getPartialTick();
        float body = Mth.rotLerp(pt, pl.yBodyRotO, pl.yBodyRot);
        float t = (pl.tickCount + pt) * 0.08f;
        double y = pl.isCrouching() ? 1.15 : 1.4;
        if (st[3] == 1) {   // the true awakening: a ragged black pair
            for (int side = 0; side < 2; side++) {
                ps.pushPose();
                ps.mulPose(Axis.YP.rotationDegrees(180f - body));
                ps.translate(side == 0 ? -0.12 : 0.12, y, 0.2);
                if (side == 0) ps.scale(-1, 1, 1);
                ps.mulPose(Axis.YP.rotationDegrees(-20 + 14 * Mth.sin(t)));
                ps.scale(1.1f, 1.1f, 1.1f);
                Minecraft.getInstance().getItemRenderer().renderStatic(black, ItemDisplayContext.FIXED, e.getPackedLight(), OverlayTexture.NO_OVERLAY, ps, e.getMultiBufferSource(), pl.level(), pl.getId());
                ps.popPose();
            }
            return;
        }
        // the first awakening: one huge branched wing out of the upper left shoulder
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(180f - body));
        ps.translate(-0.25, y + 0.05, 0.15);
        ps.scale(-1, 1, 1);   // the model reaches +x; the left shoulder is -x
        ps.mulPose(Axis.YP.rotationDegrees(-15 + 8 * Mth.sin(t)));
        ps.mulPose(Axis.ZP.rotationDegrees(4 * Mth.sin(t * 0.7f)));
        ps.scale(2.0f, 2.0f, 2.0f);   // the Last Seraph: the wing is huge
        Minecraft.getInstance().getItemRenderer().renderStatic(wing, ItemDisplayContext.FIXED, 0xF000F0, OverlayTexture.NO_OVERLAY, ps, e.getMultiBufferSource(), pl.level(), pl.getId());
        ps.popPose();
        // and the broken halo behind the head, turning slowly
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(180f - body));
        ps.translate(0, (pl.isCrouching() ? 1.55 : 1.85) + 0.04 * Mth.sin(t), 0.32);
        ps.mulPose(Axis.ZP.rotationDegrees((pl.tickCount + pt) * 1.5f));
        ps.scale(0.85f, 0.85f, 0.85f);
        Minecraft.getInstance().getItemRenderer().renderStatic(halo, ItemDisplayContext.FIXED, 0xF000F0, OverlayTexture.NO_OVERLAY, ps, e.getMultiBufferSource(), pl.level(), pl.getId());
        ps.popPose();
    }

    // ------------------------------------------------------------------ the demon's eye
    @SubscribeEvent
    public static void onHud(RenderGuiOverlayEvent.Post e) {
        if (e.getOverlay() != VanillaGuiOverlay.HOTBAR.type()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || BookTreeClient.book != BookTree.BOUND) return;
        int[] x = BookTreeClient.extra;
        if (x.length < 8) return;
        draw(e.getGuiGraphics(), mc, x, mc.player.tickCount + e.getPartialTick());
    }

    static void draw(GuiGraphics g, Minecraft mc, int[] x, float t) {
        int sw = g.guiWidth(), sh = g.guiHeight();
        int cx = sw / 2 - 91 - 30, cy = sh - 22;
        float poss = Math.min(1f, x[0] / 100f);
        boolean over = x[1] > 0, warn = x[2] > 0, awake = x[3] > 0, sym = x[4] > 0;
        double shake = warn ? Math.sin(t * 2.3) * 1.4 : 0;
        int rim = over ? 0xFFFF3040 : sym ? 0xFFFF8090 : 0xFFB02030, white = 0xFFF0F0F5;
        Shapes.begin(g);
        Shapes.eye(cx + shake, cy, 14, 0.35 + 0.65 * poss, 0xEE140608, rim);
        double iris = 2.5 + 3.5 * poss;
        Shapes.disc(cx + shake, cy, iris, over ? 0xFFFF2030 : 0xFFC02030);
        Shapes.disc(cx + shake, cy, iris * 0.35, 0xFF000000);
        if (awake) Shapes.ring(cx, cy, 16, 17.2, white);
        Shapes.end();
        String pct = over ? "!!" : x[0] + "%";
        g.drawCenteredString(mc.font, pct, cx, cy + 9, over ? 0xFFFF5060 : 0xFFE0B0B0);
        if (awake) g.drawCenteredString(mc.font, (x[3] + 19) / 20 + "s", cx, cy - 20, white);
        else if (x[5] != 0) g.drawCenteredString(mc.font, x[5] == 1 ? "R" : "X", cx - 18, cy - 4, 0xFFE0B0B0);
    }
}
