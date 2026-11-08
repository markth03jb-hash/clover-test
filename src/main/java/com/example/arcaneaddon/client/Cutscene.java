package com.example.arcaneaddon.client;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * The Black Meteorite cutscene on the caster's screen: letterbox bars, a free cinematic camera (not attached to you)
 * for each phase (dash, blitz, finisher, crater), FOV changes, impact frames and a screen shake on impact. Your camera mode and view angles are put back when it ends.
 * Also plays the impact shake for anyone nearby (no camera takeover for them).
 */
@EventBusSubscriber(modid = "arcane_addon", value = {Dist.CLIENT})
public final class Cutscene {
    private Cutscene() {}

    private static final int AFTER = 26;

    private static boolean running;
    private static int phase;                 // 0 dash, 2 blitz, 3 finisher, 1 after the impact
    private static double start = -1, phaseStart = -1, impact = -1;
    private static Vec3 look = Vec3.ZERO;
    private static CameraType savedCamera;
    private static double shakeUntil = -1, shakeStart, shakeStrength;

    public static boolean running() { return running; }

    /** phase 0 = dash (start), 2 = blitz, 3 = finisher, 1 = impact / end. {@code at} = where the camera should look. */
    public static void handle(int entityId, int ph, Vec3 at) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || p.getId() != entityId) return;
        double now = mc.level.getGameTime();
        if (ph == 0) {
            if (!running) {
                savedCamera = mc.options.getCameraType();
                camEntity = null;
                camPos = null;
            }
            running = true;
            start = now;
            impact = -1;
        } else if (!running) {
            return;
        }
        if (ph == 1) impact = now;
        phase = ph;
        phaseStart = now;
        look = at;
    }

    public static void shake(Vec3 at, float strength, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        double d = mc.player.position().distanceTo(at);
        float s = (float)(strength * Mth.clamp(1.0 - d / 40.0, 0.0, 1.0)) * com.example.arcaneaddon.AddonClientConfig.screenShake();
        if (s <= 0.02F) return;
        shakeStart = mc.level.getGameTime();
        shakeUntil = shakeStart + ticks;
        shakeStrength = s;
    }

    // the free cinematic camera: an invisible marker the view is attached to while the cutscene plays
    private static net.minecraft.world.entity.Marker camEntity;
    private static Vec3 camPos;
    private static double orbit0;

    private static void stop() {
        Minecraft mc = Minecraft.getInstance();
        running = false;
        if (mc.player != null) mc.setCameraEntity(mc.player);
        mc.gameRenderer.setRenderHand(true);
        if (savedCamera != null) mc.options.setCameraType(savedCamera);
        savedCamera = null;
        camEntity = null;
        camPos = null;
    }

    /** Elapsed cutscene time, or -1. */
    private static double time(float pt) {
        Minecraft mc = Minecraft.getInstance();
        if (!running || mc.level == null) return -1;
        return mc.level.getGameTime() + pt - start;
    }

    /**
     * A free camera, not attached to you: it is placed in the world each frame to frame the shot - a chase from the
     * side while you dash in, a slow orbit around them during the blitz, a low angle looking up for the finisher, then a
     * wide shot circling the crater. It never goes through walls (pulled in in front of them).
     */
    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent e) {
        if (e.phase != TickEvent.Phase.START || !running) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) { running = false; return; }
        double now = mc.level.getGameTime() + e.renderTickTime;
        if (impact >= 0 && now - impact > AFTER) { stop(); return; }
        if (now - start > 220) { stop(); return; }   // safety net
        if (camEntity == null || camEntity.level() != mc.level) {
            camEntity = new net.minecraft.world.entity.Marker(net.minecraft.world.entity.EntityType.MARKER, mc.level);
            orbit0 = Math.atan2(p.getZ() - look.z, p.getX() - look.x);
        }
        Vec3 eye = p.getEyePosition(e.renderTickTime);
        Vec3 toT = new Vec3(look.x - eye.x, 0, look.z - eye.z);
        Vec3 d = toT.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : toT.normalize();
        Vec3 side = new Vec3(d.z, 0, -d.x);
        double ph = now - phaseStart;
        Vec3 want, at;
        switch (phase) {
            case 2 -> {                                   // blitz: a slow orbit around them
                double a = orbit0 + ph * 0.05;
                want = look.add(Math.cos(a) * 8.5, 2.2, Math.sin(a) * 8.5);
                at = look;
            }
            case 3 -> {                                   // finisher: low, looking up at you above them
                double a = orbit0 + 0.6;
                want = look.add(Math.cos(a) * 6.5, -2.5, Math.sin(a) * 6.5);
                at = look.add(0, 2.5, 0);
            }
            case 1 -> {                                   // the crater: wide, slowly circling
                double a = orbit0 + 1.2 + ph * 0.03;
                want = look.add(Math.cos(a) * 10, 6.5, Math.sin(a) * 10);
                at = look;
            }
            default -> {                                  // the dash: a chase from the side
                want = eye.subtract(d.scale(2.5)).add(side.scale(3.8)).add(0, 1.0, 0);
                at = eye.add(d.scale(4));
            }
        }
        // keep it out of walls
        var hit = mc.level.clip(new net.minecraft.world.level.ClipContext(at, want, net.minecraft.world.level.ClipContext.Block.VISUAL,
                net.minecraft.world.level.ClipContext.Fluid.NONE, p));
        if (hit.getType() != net.minecraft.world.phys.HitResult.Type.MISS) want = hit.getLocation().add(at.subtract(want).normalize().scale(0.4));
        camPos = camPos == null ? want : camPos.add(want.subtract(camPos).scale(0.18));   // glide between shots
        Vec3 v = at.subtract(camPos);
        float yaw = (float) Math.toDegrees(Math.atan2(-v.x, v.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(v.y, Math.sqrt(v.x * v.x + v.z * v.z)));
        camEntity.setPos(camPos.x, camPos.y, camPos.z);
        camEntity.xo = camPos.x; camEntity.yo = camPos.y; camEntity.zo = camPos.z;
        camEntity.xOld = camPos.x; camEntity.yOld = camPos.y; camEntity.zOld = camPos.z;
        camEntity.setYRot(yaw); camEntity.setXRot(pitch);
        camEntity.yRotO = yaw; camEntity.xRotO = pitch;
        if (mc.options.getCameraType() != CameraType.FIRST_PERSON) mc.options.setCameraType(CameraType.FIRST_PERSON);
        if (mc.getCameraEntity() != camEntity) mc.setCameraEntity(camEntity);
        mc.gameRenderer.setRenderHand(false);
    }

    @SubscribeEvent
    public static void onAngles(ViewportEvent.ComputeCameraAngles e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        double now = mc.level.getGameTime() + e.getPartialTick();
        if (now < shakeUntil) {
            double k = (shakeUntil - now) / Math.max(1.0, shakeUntil - shakeStart);
            double s = shakeStrength * k * k * 3.5;
            e.setRoll((float)(e.getRoll() + Math.sin(now * 2.7) * s));
            e.setPitch((float)(e.getPitch() + Math.sin(now * 3.9 + 1.0) * s * 0.6));
            e.setYaw((float)(e.getYaw() + Math.cos(now * 3.1) * s * 0.6));
        }
    }

    @SubscribeEvent
    public static void onFov(ViewportEvent.ComputeFov e) {
        if (!running) return;
        double f = switch (phase) { case 0 -> 1.25; case 2 -> 1.1; case 3 -> 0.9; default -> 1.12; };
        e.setFOV(e.getFOV() * f);
    }

    // ------------------------------------------------------------------ impact frames
    private static double frameStart = -1, frameLen = 0;
    private static boolean frameSlash;

    /** Anime impact frames: a few ticks of hard white / black flashes with speed lines, for a hit that lands. */
    public static void impactFrame(int ticks, boolean slash) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !com.example.arcaneaddon.AddonClientConfig.impactFrames()) return;
        frameStart = mc.level.getGameTime();
        frameLen = Math.max(1, ticks);
        frameSlash = slash;
    }

    private static void drawImpactFrame(GuiGraphics g, double k) {
        int w = g.guiWidth(), h = g.guiHeight();
        int phase = (int) k;
        boolean white = phase % 2 == 0;
        float fade = (float) Mth.clamp(frameLen - k, 0.0, 1.0);
        int a = (int) (fade * (white ? 200 : 225));
        g.fill(0, 0, w, h, (a << 24) | (white ? 0xFFFFFF : 0x000000));
        // speed lines bursting out from the centre, in the opposite colour, with a red core on black frames
        int line = (int) (fade * 255) << 24 | (white ? 0x000000 : 0xFFFFFF);
        java.util.Random r = new java.util.Random(phase * 31L + 7);
        g.pose().pushPose();
        g.pose().translate(w / 2.0F, h / 2.0F, 0.0F);
        float reach = (float) Math.hypot(w, h) * 0.6F;
        for (int i = 0; i < 36; i++) {
            g.pose().pushPose();
            g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(i * 10.0F + r.nextFloat() * 8.0F));
            int r0 = (int) (reach * (0.25F + r.nextFloat() * 0.25F));
            int thick = 1 + r.nextInt(3);
            g.fill(r0, -thick, (int) reach, thick, line);
            g.pose().popPose();
        }
        g.pose().popPose();
        if (frameSlash) {   // the finisher: one huge white slash cutting across the whole frame, red bleeding off it
            g.pose().pushPose();
            g.pose().translate(w / 2.0F, h / 2.0F, 0.0F);
            g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-28.0F));
            int len = (int) (Math.hypot(w, h) * 0.6 * Math.min(1.0, (k + 0.4) * 1.5));
            int band = Math.max(3, h / 40);
            int red = (int) (fade * 230) << 24 | 0xD01010, core = (int) (fade * 255) << 24 | (white ? 0x000000 : 0xFFFFFF);
            g.fill(-len, -band * 3, len, band * 3, red);
            g.fill(-len, -band, len, band, core);
            g.pose().popPose();
            return;
        }
        if (!white) {
            int c = (int) (fade * 200) << 24 | 0xC01010;
            int s = Math.min(w, h) / 6;
            g.fill(w / 2 - s, h / 2 - 2, w / 2 + s, h / 2 + 2, c);
            g.fill(w / 2 - 2, h / 2 - s, w / 2 + 2, h / 2 + s, c);
        }
    }

    /** Letterbox bars slide in at the start and out at the end; they also cover the hotbar while it plays. */
    @SubscribeEvent
    public static void onGui(RenderGuiEvent.Post e) {
        Minecraft mc0 = Minecraft.getInstance();
        if (frameStart >= 0 && mc0.level != null) {
            double k = mc0.level.getGameTime() + e.getPartialTick() - frameStart;
            if (k >= 0 && k < frameLen) drawImpactFrame(e.getGuiGraphics(), k);
            else if (k >= frameLen) frameStart = -1;
        }
        double t = time(e.getPartialTick());
        if (t < 0) return;
        Minecraft mc = Minecraft.getInstance();
        double in = Mth.clamp(t / 6.0, 0.0, 1.0);
        double out = impact >= 0 ? Mth.clamp((AFTER - (mc.level.getGameTime() + e.getPartialTick() - impact)) / 6.0, 0.0, 1.0) : 1.0;
        GuiGraphics g = e.getGuiGraphics();
        int w = g.guiWidth(), h = g.guiHeight();
        int bar = (int)(h * 0.12 * in * out);
        if (bar <= 0) return;
        g.fill(0, 0, w, bar, 0xFF000000);
        g.fill(0, h - bar, w, h, 0xFF000000);
    }
}
