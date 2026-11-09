package com.example.arcaneaddon.client;

import com.example.arcaneaddon.network.FxPacket;
import com.example.arcaneaddon.server.GrimoireMastery;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The grimoire tower ceremony, seen by everyone nearby (FxPacket shape 45: id = the player, c = the altar's top,
 * palette = the grimoire that chooses them, life = length):
 *   rise    every grimoire lifts out of the altar and spreads into a ring above it
 *   circle  the ring turns, faster and faster, the books bobbing and tilting, motes drifting up
 *   choose  one slows, leaves the ring and glides down to the player; the others rise away and fade
 *   bond    it hovers before them, turns once, and goes into their hands
 */
@Mod.EventBusSubscriber(modid = "arcane_addon", value = Dist.CLIENT)
public final class GrimoireCeremonyRenderer {
    private GrimoireCeremonyRenderer() {}

    private static final class Rite { int player, pick; Vec3 top; double start; int life; }
    private static final List<Rite> RITES = new ArrayList<>();

    public static void start(FxPacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Rite r = new Rite();
        r.player = p.id; r.pick = p.palette & 0x1F; r.top = p.c; r.start = mc.level.getGameTime(); r.life = Math.max(20, p.life);
        RITES.add(r);
    }

    private static float ease(float x) { x = Mth.clamp(x, 0F, 1F); return x * x * (3F - 2F * x); }

    /** Where book i is at time t (0..1), and its scale. */
    private static double[] place(Rite r, int i, int n, float t, Vec3 hold) {
        float rise = ease(t / 0.15F);
        float spin = t * t * 14F;                       // accelerating turn
        double ang = i * Math.PI * 2 / n + spin;
        double R = 2.2 * rise, y = 0.6 + 2.0 * rise + Math.sin(t * 30 + i) * 0.12;
        Vec3 ring = r.top.add(Math.cos(ang) * R, y, Math.sin(ang) * R);
        double scale = 0.55;
        if (t > 0.6F) {
            float k = ease((t - 0.6F) / 0.18F);
            if (i == r.pick) {
                Vec3 to = hold;
                if (t > 0.86F) {   // into the hands
                    float h = ease((t - 0.86F) / 0.14F);
                    to = hold.add(0, -0.45 * h, 0);
                    scale = 0.55 * (1.0 - 0.6 * h);
                }
                ring = ring.add(to.subtract(ring).scale(k));
            } else {
                ring = ring.add(0, 3.0 * k, 0);
                scale = 0.55 * (1.0 - k);
            }
        }
        return new double[]{ring.x, ring.y, ring.z, scale, ang};
    }

    private static Vec3 holdPos(Entity e, float pt) {
        Vec3 base = e.getPosition(pt);
        float yaw = e instanceof net.minecraft.world.entity.LivingEntity le ? le.getViewYRot(pt) : e.getYRot();
        double r = Math.toRadians(yaw);
        return base.add(-Math.sin(r) * 0.9, e.getBbHeight() * 0.75, Math.cos(r) * 0.9);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { RITES.clear(); return; }
        double now = mc.level.getGameTime();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (Iterator<Rite> it = RITES.iterator(); it.hasNext(); ) {
            Rite r = it.next();
            float t = (float) ((now - r.start) / r.life);
            if (t > 1F) { it.remove(); continue; }
            Entity pl = mc.level.getEntity(r.player);
            Vec3 hold = pl == null ? r.top.add(0, 1.5, 0) : holdPos(pl, 1F);
            int n = GrimoireMastery.BOOKS.size();
            for (int i = 0; i < n; i++) {   // motes drifting off the books
                if (rnd.nextInt(3) != 0) continue;
                double[] q = place(r, i, n, t, hold);
                if (q[3] < 0.05) continue;
                DarkSmoke.mote(new Vec3(q[0], q[1], q[2]).add(rnd.nextGaussian() * 0.15, rnd.nextGaussian() * 0.15, rnd.nextGaussian() * 0.15),
                        new Vec3(0, 0.02, 0), 0.05F, 10);
            }
            if (rnd.nextInt(2) == 0) {      // a column of light motes rising off the altar
                DarkSmoke.mote(r.top.add(rnd.nextGaussian() * 0.25, 0, rnd.nextGaussian() * 0.25), new Vec3(0, 0.06, 0), 0.06F, 18);
            }
        }
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || RITES.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float pt = e.getPartialTick();
        double now = mc.level.getGameTime() + pt;
        Vec3 cam = e.getCamera().getPosition();
        PoseStack ps = e.getPoseStack();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        int n = GrimoireMastery.BOOKS.size();
        for (Rite r : RITES) {
            float t = (float) Mth.clamp((now - r.start) / r.life, 0.0, 1.0);
            Entity pl = mc.level.getEntity(r.player);
            Vec3 hold = pl == null ? r.top.add(0, 1.5, 0) : holdPos(pl, pt);
            for (int i = 0; i < n; i++) {
                double[] q = place(r, i, n, t, hold);
                if (q[3] < 0.03) continue;
                ItemStack stack = new ItemStack(GrimoireMastery.BOOKS.get(i).item().get());
                ps.pushPose();
                ps.translate(q[0] - cam.x, q[1] - cam.y, q[2] - cam.z);
                boolean chosen = i == r.pick && t > 0.6F;
                // facing out of the ring while it turns; the chosen one turns to face its new owner
                float yaw = chosen ? (float) (now * 8.0) : (float) Math.toDegrees(-q[4]) + 90F;
                ps.mulPose(Axis.YP.rotationDegrees(yaw));
                ps.mulPose(Axis.ZP.rotationDegrees((float) Math.sin(now * 0.2 + i) * 10F));
                float s = (float) q[3];
                ps.scale(s, s, s);
                mc.getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, ps, buf, mc.level, i);
                ps.popPose();
            }
        }
        buf.endBatch();
    }
}
