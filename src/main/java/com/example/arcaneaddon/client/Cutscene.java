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
 * The Black Meteorite cutscene on the caster's screen: letterbox bars, cinematic camera angles for each phase
 * (front shot while charging, from below as you launch, slowly circling you at the apex, from above as you dive),
 * a narrowing FOV and a screen shake on impact. Your camera mode and view angles are put back when it ends.
 * Also plays the impact shake for anyone nearby (no camera takeover for them).
 */
@EventBusSubscriber(modid = "arcane_addon", value = {Dist.CLIENT})
public final class Cutscene {
    private Cutscene() {}

    private static final int CHARGE = 10, LAUNCH = 8, APEX = 12, AFTER = 26;

    private static boolean running;
    private static double start = -1, impact = -1;
    private static Vec3 target = Vec3.ZERO;
    private static CameraType savedCamera;
    private static float savedYaw, savedPitch, baseYaw;
    private static double shakeUntil = -1, shakeStart, shakeStrength;

    public static boolean running() { return running; }

    /** phase 0 = start, 1 = impact. */
    public static void handle(int entityId, int phase, Vec3 tgt) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || p.getId() != entityId) return;
        double now = mc.level.getGameTime();
        if (phase == 0) {
            if (!running) {
                savedCamera = mc.options.getCameraType();
                savedYaw = p.getYRot();
                savedPitch = p.getXRot();
            }
            running = true;
            start = now;
            impact = -1;
            target = tgt;
            baseYaw = p.getYRot();
        } else if (running) {
            impact = now;
        }
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

    /** Drives the player's view each frame so the third-person camera lands where the shot needs it. */
    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent e) {
        if (e.phase != TickEvent.Phase.START || !running) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) { running = false; return; }
        double t = time(e.renderTickTime);
        if (impact >= 0 && mc.level.getGameTime() - impact > AFTER) { stop(); return; }
        if (t > 120) { stop(); return; }   // safety net
        float yaw, pitch;
        CameraType cam;
        Vec3 toT = target.subtract(p.position());
        float yawToTarget = (float)(Math.toDegrees(Math.atan2(-toT.x, toT.z)));
        if (impact >= 0) {                                   // after the impact: pulled back, looking down at the crater
            double a = mc.level.getGameTime() + e.renderTickTime - impact;
            cam = CameraType.THIRD_PERSON_BACK;
            yaw = yawToTarget + (float)(a * 2.0);
            pitch = 40.0F;
        } else if (t < CHARGE) {                             // charge: from the front, low
            cam = CameraType.THIRD_PERSON_FRONT;
            yaw = baseYaw + (float)(t * 1.5);
            pitch = 12.0F - (float)t;
        } else if (t < CHARGE + LAUNCH) {                    // launch: from below, looking up as you shoot into the sky
            cam = CameraType.THIRD_PERSON_BACK;
            yaw = baseYaw + 20.0F;
            pitch = -60.0F;
        } else if (t < CHARGE + LAUNCH + APEX) {             // apex: circling you against the sky
            double a = t - CHARGE - LAUNCH;
            cam = CameraType.THIRD_PERSON_FRONT;
            yaw = baseYaw + 160.0F + (float)(a * 9.0);
            pitch = -15.0F;
        } else {                                             // dive: from above and behind, the target rushing up
            cam = CameraType.THIRD_PERSON_BACK;
            yaw = yawToTarget;
            pitch = 65.0F;
        }
        if (mc.options.getCameraType() != cam) mc.options.setCameraType(cam);
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
        double t = time((float)e.getPartialTick());
        if (t < 0) return;
        double f;
        if (impact >= 0) f = 1.12;
        else if (t < CHARGE) f = 1.0 - 0.15 * (t / CHARGE);
        else if (t < CHARGE + LAUNCH) f = 1.15;
        else if (t < CHARGE + LAUNCH + APEX) f = 0.85;
        else f = 1.25;                                       // the rush of the dive
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
