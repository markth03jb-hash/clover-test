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

    // ------------------------------------------------------------------ short cinematics (Equinox)
    private static double cineStart = -1, cineEnd = -1, cineCut = -1;

    /** A cinematic for {@code life} ticks with its key moment {@code cut} ticks in: bars, a slow push-in, a snap out. */
    public static void cinematic(int life, int cut) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        cineStart = mc.level.getGameTime();
        cineEnd = cineStart + life;
        cineCut = cineStart + cut;
    }

    private static double cineTime(float pt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || cineStart < 0) return -1;
        double now = mc.level.getGameTime() + pt;
        if (now > cineEnd) { cineStart = -1; return -1; }
        return now;
    }

    /** No zoom from being slowed while charging Event Horizon / Equinox - the view stays steady. */
    @SubscribeEvent
    public static void onFovModifier(net.minecraftforge.client.event.ComputeFovModifierEvent e) {
        var att = e.getPlayer().getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        if (att != null && att.getModifier(com.example.arcaneaddon.server.TimedModifiers.HORIZON_CHARGE) != null) e.setNewFovModifier(1.0F);
    }

    @SubscribeEvent
    public static void onFov(ViewportEvent.ComputeFov e) {
        double ct = cineTime((float) e.getPartialTick());
        if (ct >= 0) {   // push in slowly through the held moment, snap back out on the cut, settle
            double f;
            if (ct < cineCut) f = 1.0 - 0.16 * Mth.clamp((ct - cineStart) / Math.max(1.0, cineCut - cineStart), 0.0, 1.0);
            else f = 1.06 - 0.06 * Mth.clamp((ct - cineCut) / 12.0, 0.0, 1.0);
            e.setFOV(e.getFOV() * f);
        }
        if (!running) return;
        double f = switch (phase) { case 0 -> 1.25; case 2 -> 1.1; case 3 -> 0.9; default -> 1.12; };
        e.setFOV(e.getFOV() * f);
    }

    // ------------------------------------------------------------------ impact frames
    private static double frameStart = -1, frameLen = 0;
    private static boolean frameSlash;
    private static int frameStyle;

    /** Anime impact frames: a few ticks of hard white / black flashes with speed lines, for a hit that lands. */
    public static void impactFrame(int ticks, boolean slash) { impactFrame(ticks, slash ? 1 : 0); }

    /** style 0 = Asta (white / black, speed lines), 1 = Asta with the huge slash, 2 = Luck, 3 = Equinox, 4..10 = Yami, Yuno, Nozel, Magna, Mereoleona, Zenon, Klaus. */
    public static void impactFrame(int ticks, int style) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !com.example.arcaneaddon.AddonClientConfig.impactFrames()) return;
        frameStart = mc.level.getGameTime();
        frameLen = Math.max(1, ticks);
        frameSlash = style == 1;
        frameStyle = style;
    }

    /** One straight piece of a bolt drawn on the screen. */
    private static void seg(GuiGraphics g, float x0, float y0, float x1, float y1, int thick, int col) {
        float dx = x1 - x0, dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.5F) return;
        g.pose().pushPose();
        g.pose().translate(x0, y0, 0.0F);
        g.pose().mulPose(com.mojang.math.Axis.ZP.rotation((float) Math.atan2(dy, dx)));
        g.fill(0, -thick, (int) Math.ceil(len), thick, col);
        g.pose().popPose();
    }

    /**
     * Luck's impact frame: no black and white - the screen flashes electric blue and deep navy in turn, and jagged
     * bolts of lightning crack across it from the edges toward the middle, white-hot cores in a blue glow.
     */
    private static void drawLightningFrame(GuiGraphics g, double k) {
        int w = g.guiWidth(), h = g.guiHeight();
        int phase = (int) k;
        boolean bright = phase % 2 == 0;
        float fade = (float) Mth.clamp(frameLen - k, 0.0, 1.0);
        g.fill(0, 0, w, h, ((int) (fade * (bright ? 170 : 215)) << 24) | (bright ? 0x9FD8FF : 0x040A2A));
        java.util.Random r = new java.util.Random(phase * 977L + 13);
        int glow = ((int) (fade * 200) << 24) | (bright ? 0x1E50C8 : 0x4FB8FF);
        int core = ((int) (fade * 255) << 24) | (bright ? 0x0A1440 : 0xFFFFFF);
        for (int b = 0; b < 6; b++) {
            // from a random point on the edge toward somewhere near the middle, in jagged steps, with a fork or two
            float x, y;
            switch (r.nextInt(4)) {
                case 0 -> { x = r.nextFloat() * w; y = 0; }
                case 1 -> { x = r.nextFloat() * w; y = h; }
                case 2 -> { x = 0; y = r.nextFloat() * h; }
                default -> { x = w; y = r.nextFloat() * h; }
            }
            float tx = w * (0.35F + r.nextFloat() * 0.3F), ty = h * (0.35F + r.nextFloat() * 0.3F);
            int steps = 9;
            for (int s = 0; s < steps; s++) {
                float f = (s + 1) / (float) steps;
                float nx = x + (tx - x) / (steps - s) + (r.nextFloat() - 0.5F) * w * 0.07F;
                float ny = y + (ty - y) / (steps - s) + (r.nextFloat() - 0.5F) * h * 0.07F;
                int th = Math.max(1, (int) (4 * (1.2F - f)));
                seg(g, x, y, nx, ny, th + 3, glow);
                seg(g, x, y, nx, ny, th, core);
                if (r.nextInt(4) == 0) {   // a fork
                    float fx = nx + (r.nextFloat() - 0.5F) * w * 0.12F, fy = ny + (r.nextFloat() - 0.5F) * h * 0.12F;
                    seg(g, nx, ny, fx, fy, 3, glow);
                    seg(g, nx, ny, fx, fy, 1, core);
                }
                x = nx; y = ny;
            }
        }
    }

    /** Yami's Equinox: the screen split down the middle into light and dark, swapping each tick, one line dividing it. */
    private static void drawEquinoxFrame(GuiGraphics g, double k) {
        int w = g.guiWidth(), h = g.guiHeight();
        boolean swap = ((int) k) % 2 == 1;
        float fade = (float) Mth.clamp(frameLen - k, 0.0, 1.0);
        int a = (int) (fade * 225);
        int light = (a << 24) | 0xF4F0FF, dark = (a << 24) | 0x07000F;
        g.fill(0, 0, w / 2, h, swap ? dark : light);
        g.fill(w / 2, 0, w, h, swap ? light : dark);
        int line = ((int) (fade * 255) << 24) | 0x9A5CFF;
        g.fill(w / 2 - 2, 0, w / 2 + 2, h, line);
        g.fill(0, h / 2 - 1, w, h / 2 + 1, ((int) (fade * 120) << 24) | 0x9A5CFF);   // the horizon
    }

    /**
     * Each grimoire's own impact frame: its colours flashing light / dark tick by tick, and its own pattern across
     * the screen - Yami's diagonal cuts, Yuno's spiralling wind, Nozel's mercury spikes, Magna's flames,
     * Mereoleona's roaring crimson rays, Zenon's cracking bone, Klaus's steel plates.
     */
    private static void drawThemedFrame(GuiGraphics g, double k, int style) {
        int w = g.guiWidth(), h = g.guiHeight();
        int phase = (int) k;
        boolean bright = phase % 2 == 0;
        float fade = (float) Mth.clamp(frameLen - k, 0.0, 1.0);
        int[][] pal = {   // light, dark, lines, accent
                {0xD8C8FF, 0x07000F, 0x9A5CFF, 0xFFFFFF},   // 4 Yami
                {0xE8FFF2, 0x02261C, 0x6BE09A, 0xFFFFFF},   // 5 Yuno
                {0xF0F4FA, 0x10141C, 0xB8C4D8, 0x5A6A80},   // 6 Nozel
                {0xFFE2B0, 0x2A0800, 0xFF8A2A, 0xFFF0C0},   // 7 Magna
                {0xFFC8C0, 0x200000, 0xE0302A, 0xFFE0D0},   // 8 Mereoleona
                {0xF4EEDC, 0x0A0610, 0xE8DCC0, 0x7A3CC8},   // 9 Zenon
                {0xE6ECF2, 0x0E1420, 0x9AA8B8, 0xFFFFFF}};  // 10 Klaus
        int[] c = pal[Mth.clamp(style - 4, 0, pal.length - 1)];
        g.fill(0, 0, w, h, ((int) (fade * (bright ? 190 : 220)) << 24) | (bright ? c[0] : c[1]));
        int line = ((int) (fade * 255) << 24) | (bright ? c[1] : c[2]);
        int acc = ((int) (fade * 230) << 24) | (bright ? c[2] : c[3]);
        java.util.Random r = new java.util.Random(phase * 131L + style * 17L);
        float cx = w / 2f, cy = h / 2f, reach = (float) Math.hypot(w, h) * 0.6F;
        switch (style) {
            case 4 -> {   // Yami: long diagonal cuts across the frame, one bright one through the middle
                for (int i = 0; i < 7; i++) {
                    float off = (i - 3) * h * 0.14F + (r.nextFloat() - 0.5F) * 10;
                    seg(g, -20, cy + off - h * 0.5F, w + 20, cy + off + h * 0.5F, 1 + r.nextInt(2), line);
                }
                seg(g, -20, cy - h * 0.55F, w + 20, cy + h * 0.55F, Math.max(3, h / 60), acc);
            }
            case 5 -> {   // Yuno: wind spiralling in to the middle
                for (int a = 0; a < 5; a++) {
                    double a0 = a * Math.PI * 2 / 5 + r.nextDouble() * 0.3;
                    float px = cx + (float) Math.cos(a0) * reach, py = cy + (float) Math.sin(a0) * reach;
                    for (int s = 1; s <= 14; s++) {
                        double rr = reach * (1 - s / 15.0), aa = a0 + s * 0.22;
                        float nx = cx + (float) (Math.cos(aa) * rr), ny = cy + (float) (Math.sin(aa) * rr);
                        seg(g, px, py, nx, ny, Math.max(1, 3 - s / 6), s % 3 == 0 ? acc : line);
                        px = nx; py = ny;
                    }
                }
            }
            case 6 -> {   // Nozel: sharp silver spikes stabbing in from every edge
                for (int i = 0; i < 18; i++) {
                    double a = i * Math.PI * 2 / 18 + r.nextDouble() * 0.2;
                    float ex = cx + (float) Math.cos(a) * reach, ey = cy + (float) Math.sin(a) * reach;
                    float len = reach * (0.45F + r.nextFloat() * 0.25F);
                    for (int t = 0; t < 4; t++) {   // a tapering spike: thick at the edge, a needle at the tip
                        float f0 = t / 4f, f1 = (t + 1) / 4f;
                        seg(g, ex + (cx - ex) * f0 * len / reach, ey + (cy - ey) * f0 * len / reach,
                               ex + (cx - ex) * f1 * len / reach, ey + (cy - ey) * f1 * len / reach, 4 - t, t == 3 ? acc : line);
                    }
                }
            }
            case 7, 8 -> {   // Magna / Mereoleona: tongues of flame roaring out from the middle
                int n = style == 7 ? 22 : 30;
                for (int i = 0; i < n; i++) {
                    double a = i * Math.PI * 2 / n + r.nextDouble() * 0.15;
                    float px = cx + (float) Math.cos(a) * reach * 0.15F, py = cy + (float) Math.sin(a) * reach * 0.15F;
                    for (int s = 1; s <= 6; s++) {
                        double rr = reach * (0.15 + s * 0.14), wob = (r.nextDouble() - 0.5) * 0.18;
                        float nx = cx + (float) (Math.cos(a + wob) * rr), ny = cy + (float) (Math.sin(a + wob) * rr);
                        seg(g, px, py, nx, ny, Math.max(1, (style == 8 ? 5 : 4) - s / 2), s < 3 ? acc : line);
                        px = nx; py = ny;
                    }
                }
            }
            case 9 -> {   // Zenon: the frame cracks like bone from the middle out, a violet edge in the cracks
                for (int b = 0; b < 8; b++) {
                    double a = b * Math.PI * 2 / 8 + r.nextDouble() * 0.4;
                    float px = cx, py = cy;
                    for (int s = 0; s < 8; s++) {
                        a += (r.nextDouble() - 0.5) * 0.7;
                        float nx = px + (float) Math.cos(a) * reach * 0.12F, ny = py + (float) Math.sin(a) * reach * 0.12F;
                        seg(g, px, py, nx, ny, 3, acc);
                        seg(g, px, py, nx, ny, 1, line);
                        if (r.nextInt(3) == 0) {
                            double fa = a + (r.nextBoolean() ? 0.8 : -0.8);
                            seg(g, nx, ny, nx + (float) Math.cos(fa) * reach * 0.08F, ny + (float) Math.sin(fa) * reach * 0.08F, 1, line);
                        }
                        px = nx; py = ny;
                    }
                }
            }
            default -> {  // Klaus: steel plates - a grid of heavy bars slamming across the frame
                for (int i = 1; i < 6; i++) {
                    int x = w * i / 6 + r.nextInt(9) - 4, y = h * i / 6 + r.nextInt(9) - 4;
                    g.fill(x - 2, 0, x + 2, h, line);
                    g.fill(0, y - 2, w, y + 2, line);
                }
                g.fill(0, (int) cy - 4, w, (int) cy + 4, acc);
            }
        }
    }

    private static void drawImpactFrame(GuiGraphics g, double k) {
        if (frameStyle == 2) { drawLightningFrame(g, k); return; }
        if (frameStyle == 3) { drawEquinoxFrame(g, k); return; }
        if (frameStyle >= 4) { drawThemedFrame(g, k, frameStyle); return; }
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
        double ct = cineTime(e.getPartialTick());
        if (ct >= 0) {   // letterbox bars and a darkening at the edges while the moment stretches
            GuiGraphics g = e.getGuiGraphics();
            int w = g.guiWidth(), h = g.guiHeight();
            double in = Mth.clamp((ct - cineStart) / 8.0, 0.0, 1.0), out = Mth.clamp((cineEnd - ct) / 8.0, 0.0, 1.0);
            int bar = (int) (h * 0.12 * Math.min(in, out));
            g.fill(0, 0, w, bar, 0xFF000000);
            g.fill(0, h - bar, w, h, 0xFF000000);
            double hold = ct < cineCut ? Mth.clamp((ct - cineStart) / Math.max(1.0, cineCut - cineStart), 0.0, 1.0) : Math.max(0.0, 1.0 - (ct - cineCut) / 6.0);
            int edge = (int) (hold * 110 * Math.min(in, out));
            for (int k = 0; k < 6; k++) {   // a soft vignette: bands growing fainter toward the middle
                int a = (int) (edge * (1.0 - k / 6.0)) << 24;
                int bw = w / 28;
                g.fill(k * bw, 0, (k + 1) * bw, h, a);
                g.fill(w - (k + 1) * bw, 0, w - k * bw, h, a);
            }
        }
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
