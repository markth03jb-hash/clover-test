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
 * The Black Meteorite cutscene on the caster's screen: letterbox bars, a cinematic third-person camera for each
 * phase (dash, blitz, finisher, crater), FOV changes and a screen shake on impact. Your camera mode and view angles are put back when it ends.
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
    private static float savedYaw, savedPitch;
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
                savedYaw = p.getYRot();
                savedPitch = p.getXRot();
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
        float s = (float)(strength * Mth.clamp(1.0 - d / 40.0, 0.0, 1.0));
        if (s <= 0.02F) return;
        shakeStart = mc.level.getGameTime();
        shakeUntil = shakeStart + ticks;
        shakeStrength = s;
    }

    private static void stop() {
        Minecraft mc = Minecraft.getInstance();
        running = false;
        if (savedCamera != null) mc.options.setCameraType(savedCamera);
        if (mc.player != null) {
            mc.player.setYRot(savedYaw);
            mc.player.setXRot(savedPitch);
            mc.player.yRotO = savedYaw;
            mc.player.xRotO = savedPitch;
        }
        savedCamera = null;
    }

    /** Elapsed cutscene time, or -1. */
    private static double time(float pt) {
        Minecraft mc = Minecraft.getInstance();
        if (!running || mc.level == null) return -1;
        return mc.level.getGameTime() + pt - start;
    }

    /**
     * Drives your view each frame so the third-person camera frames the shot: behind you rushing at them, behind you
     * locked onto them while you blink around (every blink is a new camera cut), high above for the finisher, then
     * pulled back circling the crater.
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
        Vec3 eye = p.getEyePosition(e.renderTickTime);
        Vec3 d = look.subtract(eye);
        float yawTo = (float)Math.toDegrees(Math.atan2(-d.x, d.z));
        float pitchTo = (float)-Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        float yaw, pitch;
        switch (phase) {
            case 2 -> { yaw = yawTo; pitch = Mth.clamp(pitchTo, -70.0F, 70.0F); }          // blitz: locked on them
            case 3 -> { yaw = yawTo + (float)((now - phaseStart) * 3.0); pitch = 75.0F; }  // finisher: from high above
            case 1 -> { yaw = yawTo + (float)((now - phaseStart) * 2.0); pitch = 38.0F; }  // the crater
            default -> { yaw = yawTo; pitch = Mth.clamp(pitchTo + 8.0F, -60.0F, 60.0F); } // the dash
        }
        if (mc.options.getCameraType() != CameraType.THIRD_PERSON_BACK) mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        p.setYRot(yaw);
        p.setXRot(pitch);
        p.yRotO = yaw;
        p.xRotO = pitch;
        p.setYHeadRot(yaw);
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

    /** Letterbox bars slide in at the start and out at the end; they also cover the hotbar while it plays. */
    @SubscribeEvent
    public static void onGui(RenderGuiEvent.Post e) {
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
