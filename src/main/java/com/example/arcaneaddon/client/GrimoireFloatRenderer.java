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
import net.minecraft.core.particles.DustParticleOptions;
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
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Black Grimoire: floats open (tilted like a lectern) far to the front-left of its owner, wrapped in crimson smoke.
 * On Summon Greatsword the 3D sword rises out of the book, turns sideways and flies across to the right hand.
 */
@Mod.EventBusSubscriber(modid = ArcaneAddon.MODID, value = Dist.CLIENT)
public class GrimoireFloatRenderer {
    // ---- tuning knobs ----
    private static final double FORWARD = 1.7;      // blocks in front of the player (bigger = farther)
    private static final double LEFT = 1.35;        // blocks to the left (bigger = more left)
    private static final double HEIGHT = 0.70;      // fraction of body height
    private static final float BOOK_SCALE = 0.8f;
    private static final float TILT = 35f;          // degrees the book leans back (0 = upright, 90 = flat)
    private static final float SWORD_SCALE = 0.9f;
    // sword model: centre sits 0.278 block up/right of the model origin
    private static final float SWORD_CENTER = 0.278f;

    private static final DustParticleOptions CRIMSON = new DustParticleOptions(new Vector3f(0.75f, 0.04f, 0.10f), 1.6f);
    private static final DustParticleOptions DARK_RED = new DustParticleOptions(new Vector3f(0.25f, 0.0f, 0.04f), 2.2f);

    private static final Map<Integer, Long> ANIMS = new HashMap<>();
    private static final Set<Integer> HOLDERS = new HashSet<>();

    public static void startAnim(int entityId) {
        var level = Minecraft.getInstance().level;
        if (level != null) ANIMS.put(entityId, level.getGameTime());
    }

    private static Vec3 bookPosition(Player p, float pt, float yaw) {
        double yawR = Math.toRadians(yaw);
        Vec3 fwd = new Vec3(-Math.sin(yawR), 0, Math.cos(yawR));
        Vec3 left = new Vec3(Math.cos(yawR), 0, Math.sin(yawR));
        Vec3 base = new Vec3(Mth.lerp(pt, p.xo, p.getX()), Mth.lerp(pt, p.yo, p.getY()), Mth.lerp(pt, p.zo, p.getZ()));
        double bob = Math.sin((p.level().getGameTime() + pt) * 0.08 + p.getId()) * 0.05;
        return base.add(0, p.getBbHeight() * HEIGHT + bob, 0).add(fwd.scale(FORWARD)).add(left.scale(LEFT));
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        HOLDERS.clear();
        if (mc.level == null || mc.isPaused()) { if (mc.level == null) ANIMS.clear(); return; }
        for (Player p : mc.level.players()) {
            ItemStack equipped = Utils.getPlayerSpellbookStack(p);   // can be null (e.g. right after joining a world)
            if (equipped != null && equipped.getItem() instanceof BlackGrimoireItem) HOLDERS.add(p.getId());
        }
        long now = mc.level.getGameTime();
        ANIMS.values().removeIf(s -> now - s > SummonQueue.DELAY_TICKS + 2);

        // crimson smoke swirling around each floating grimoire
        var rnd = mc.level.random;
        for (Player p : mc.level.players()) {
            if (!HOLDERS.contains(p.getId()) || p.isSpectator() || p.isInvisible()) continue;
            Vec3 c = bookPosition(p, 1f, p.getViewYRot(1f));
            double t = now * 0.15;
            for (int i = 0; i < 4; i++) {
                double a = rnd.nextDouble() * Math.PI * 2, r = 0.25 + rnd.nextDouble() * 0.45;
                double px = c.x + Math.cos(a) * r, py = c.y + (rnd.nextDouble() - 0.5) * 0.8, pz = c.z + Math.sin(a) * r;
                mc.level.addParticle(i == 0 ? DARK_RED : CRIMSON, px, py, pz,
                        -Math.sin(a + t) * 0.02, 0.01 + rnd.nextDouble() * 0.02, Math.cos(a + t) * 0.02);
            }
            if (now % 3 == 0) mc.level.addParticle(ParticleTypes.LARGE_SMOKE, c.x + (rnd.nextDouble() - 0.5) * 0.6,
                    c.y - 0.3, c.z + (rnd.nextDouble() - 0.5) * 0.6, 0, 0.02, 0);
        }
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

        for (Player p : mc.level.players()) {
            if (!HOLDERS.contains(p.getId()) || p.isSpectator() || p.isInvisible()) continue;

            float yaw = p.getViewYRot(pt);
            double yawR = Math.toRadians(yaw);
            Vec3 fwd = new Vec3(-Math.sin(yawR), 0, Math.cos(yawR));
            Vec3 left = new Vec3(Math.cos(yawR), 0, Math.sin(yawR));
            Vec3 base = new Vec3(Mth.lerp(pt, p.xo, p.getX()), Mth.lerp(pt, p.yo, p.getY()), Mth.lerp(pt, p.zo, p.getZ()));
            Vec3 bookPos = bookPosition(p, pt, yaw);

            // ---- floating open grimoire ----
            ps.pushPose();
            ps.translate(bookPos.x - cam.x, bookPos.y - cam.y, bookPos.z - cam.z);
            ps.mulPose(Axis.YP.rotationDegrees(-yaw));
            ps.mulPose(Axis.YP.rotationDegrees(180f));        // pages face the player
            ps.mulPose(Axis.XP.rotationDegrees(-TILT));       // lean back like a lectern
            ps.scale(BOOK_SCALE, BOOK_SCALE, BOOK_SCALE);
            mc.getItemRenderer().renderStatic(book, ItemDisplayContext.HEAD, LightTexture.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY, ps, buf, mc.level, p.getId());
            ps.popPose();

            // ---- sword: rises from the book, turns sideways, flies to the right hand ----
            Long start = ANIMS.get(p.getId());
            if (start != null) {
                float t = Mth.clamp((mc.level.getGameTime() - start + pt) / SummonQueue.DELAY_TICKS, 0f, 1f);
                Vec3 hand = base.add(0, p.getEyeHeight() * 0.8, 0).add(fwd.scale(0.55)).add(left.scale(-0.4));
                Vec3 top = bookPos.add(0, 0.8, 0);
                Vec3 pos; float scale, roll;               // roll: 45 = blade straight up, 135 = blade pointing to the player's right
                final float RISE = 0.4f;
                if (t < RISE) {
                    float u = t / RISE, eased = 1 - (1 - u) * (1 - u);
                    pos = bookPos.add(0, 0.8 * eased, 0);
                    scale = 0.3f + 0.7f * u; roll = 45f;
                } else {
                    float u = (t - RISE) / (1 - RISE), eased = u * u * (3 - 2 * u);
                    pos = top.add(hand.subtract(top).scale(eased)).add(0, Math.sin(Math.PI * u) * 0.2, 0);
                    scale = 1f; roll = 45f + 90f * Mth.clamp(u * 3f, 0f, 1f);
                }
                if (mc.level.getGameTime() % 2 == 0) mc.level.addParticle(CRIMSON, pos.x, pos.y, pos.z, 0, 0.01, 0);
                ps.pushPose();
                ps.translate(pos.x - cam.x, pos.y - cam.y, pos.z - cam.z);
                ps.mulPose(Axis.YP.rotationDegrees(-yaw));
                ps.mulPose(Axis.ZP.rotationDegrees(roll));
                ps.scale(SWORD_SCALE * scale, SWORD_SCALE * scale, SWORD_SCALE * scale);
                ps.translate(-SWORD_CENTER, -SWORD_CENTER, 0);   // pivot on the sword's own centre
                mc.getItemRenderer().renderStatic(sword, ItemDisplayContext.NONE, LightTexture.FULL_BRIGHT,
                        OverlayTexture.NO_OVERLAY, ps, buf, mc.level, p.getId());
                ps.popPose();
            }
        }
        buf.endBatch();
    }
}
