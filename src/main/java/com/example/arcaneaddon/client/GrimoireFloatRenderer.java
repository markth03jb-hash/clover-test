package com.example.arcaneaddon.client;

import com.example.arcaneaddon.ArcaneAddon;
import com.example.arcaneaddon.item.BlackGrimoireItem;
import com.example.arcaneaddon.registry.AddonItemRegistry;
import com.example.arcaneaddon.server.SummonQueue;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Draws the Black Grimoire open (pages toward you) floating in front-left of every player who has it equipped,
 * and animates the greatsword rising out of the book into the player's right hand when Summon Greatsword is cast.
 */
@Mod.EventBusSubscriber(modid = ArcaneAddon.MODID, value = Dist.CLIENT)
public class GrimoireFloatRenderer {
    // ---- tuning knobs ----
    private static final double FORWARD = 0.9;      // blocks in front of the player
    private static final double LEFT = 0.55;        // blocks to the left
    private static final double HEIGHT = 0.75;      // fraction of body height
    private static final float BOOK_SCALE = 0.9f;
    private static final float SWORD_SCALE = 1.3f;

    /** model +x (open side) -> toward player, model y (spread) -> horizontal, model z (spine) -> up. */
    private static final Quaternionf BOOK_ROT = new Quaternionf().setFromNormalized(new Matrix3f(0, 0, -1, -1, 0, 0, 0, 1, 0));

    private static final Map<Integer, Long> ANIMS = new HashMap<>();
    private static final Set<Integer> HOLDERS = new HashSet<>();

    public static void startAnim(int entityId) {
        var level = Minecraft.getInstance().level;
        if (level != null) ANIMS.put(entityId, level.getGameTime());
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        HOLDERS.clear();
        if (mc.level == null) { ANIMS.clear(); return; }
        for (Player p : mc.level.players()) {
            if (Utils.getPlayerSpellbookStack(p).getItem() instanceof BlackGrimoireItem) HOLDERS.add(p.getId());
        }
        long now = mc.level.getGameTime();
        ANIMS.values().removeIf(s -> now - s > SummonQueue.DELAY_TICKS + 2);
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || HOLDERS.isEmpty()) return;

        float pt = e.getPartialTick();
        PoseStack ps = e.getPoseStack();
        Vec3 cam = e.getCamera().getPosition();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        ItemStack book = new ItemStack(AddonItemRegistry.BLACK_GRIMOIRE.get());
        ItemStack sword = new ItemStack(AddonItemRegistry.SUMMONED_GREATSWORD.get());
        float time = mc.level.getGameTime() + pt;

        for (Player p : mc.level.players()) {
            if (!HOLDERS.contains(p.getId()) || p.isSpectator() || p.isInvisible()) continue;

            float yaw = p.getViewYRot(pt);
            double yawR = Math.toRadians(yaw);
            Vec3 fwd = new Vec3(-Math.sin(yawR), 0, Math.cos(yawR));
            Vec3 left = new Vec3(Math.cos(yawR), 0, Math.sin(yawR));
            Vec3 base = new Vec3(Mth.lerp(pt, p.xo, p.getX()), Mth.lerp(pt, p.yo, p.getY()), Mth.lerp(pt, p.zo, p.getZ()));
            double bob = Math.sin(time * 0.08 + p.getId()) * 0.04;
            Vec3 bookPos = base.add(0, p.getBbHeight() * HEIGHT + bob, 0).add(fwd.scale(FORWARD)).add(left.scale(LEFT));

            // ---- floating open grimoire ----
            ps.pushPose();
            ps.translate(bookPos.x - cam.x, bookPos.y - cam.y, bookPos.z - cam.z);
            ps.mulPose(Axis.YP.rotationDegrees(-yaw));
            ps.mulPose(BOOK_ROT);
            ps.scale(BOOK_SCALE, BOOK_SCALE, BOOK_SCALE);
            ps.translate(0.03125, 0.375, 0); // re-centre the book's geometry on the pivot
            mc.getItemRenderer().renderStatic(book, ItemDisplayContext.HEAD, LightTexture.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY, ps, buf, mc.level, p.getId());
            ps.popPose();

            // ---- sword rising out of the book, then flying to the hand ----
            Long start = ANIMS.get(p.getId());
            if (start != null) {
                float t = Mth.clamp((mc.level.getGameTime() - start + pt) / SummonQueue.DELAY_TICKS, 0f, 1f);
                Vec3 hand = base.add(0, p.getEyeHeight() * 0.8, 0).add(fwd.scale(0.55)).add(left.scale(-0.4));
                Vec3 top = bookPos.add(0, 0.9, 0);
                Vec3 pos; float scale;
                if (t < 0.6f) {
                    float u = t / 0.6f; float eased = 1 - (1 - u) * (1 - u);
                    pos = bookPos.add(0, 0.9 * eased, 0);
                    scale = 0.3f + 0.7f * u;
                } else {
                    float u = (t - 0.6f) / 0.4f; float eased = u * u * (3 - 2 * u);
                    pos = top.add(hand.subtract(top).scale(eased));
                    scale = 1.0f;
                }
                if (mc.level.getGameTime() % 2 == 0) {
                    mc.level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, pos.x, pos.y, pos.z, 0, 0.02, 0);
                }
                ps.pushPose();
                ps.translate(pos.x - cam.x, pos.y - cam.y, pos.z - cam.z);
                ps.mulPose(Axis.YP.rotationDegrees(-yaw));
                ps.mulPose(Axis.ZP.rotationDegrees(-45f));   // sprite tip is top-right; make the blade point straight up
                ps.scale(SWORD_SCALE * scale, SWORD_SCALE * scale, SWORD_SCALE * scale);
                mc.getItemRenderer().renderStatic(sword, ItemDisplayContext.NONE, LightTexture.FULL_BRIGHT,
                        OverlayTexture.NO_OVERLAY, ps, buf, mc.level, p.getId());
                ps.popPose();
            }
        }
        buf.endBatch();
    }
}
