package com.ironspellgrimoire.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.Random;

/**
 * Anime impact frames, drawn like the real thing:
 *   NEGATIVE frames - the world itself turned to a photo negative and tinted with the grimoire's colour, so you
 *                     still see everything (who got hit, where), just inverted for that instant
 *   INK frames      - the screen drops to the grimoire's dark ink (not quite opaque) with its pattern blazing on it
 *   the two alternate tick by tick; a one-tick frame is a single negative.
 * Everything is aimed at the HIT: the point the server sent is projected onto the screen and the speed lines,
 * spikes, bolts, flames and cracks all converge on it (or burst out of it), rushing inward as the frame plays.
 * Lines are tapered wedges, not flat bars. The frame snaps in (it is a cut) and lets go over its last tick.
 * Drawn under the HUD, so the hotbar and health stay readable.
 *
 * style 0 = Asta, 1 = Asta with the huge slash, 2 = Luck, 3 = Equinox, 4..10 = Yami, Yuno, Nozel, Magna,
 * Mereoleona, Zenon, Klaus.
 */
@Mod.EventBusSubscriber(modid = "iron_spell_grimoire", value = Dist.CLIENT)
public final class ImpactFrames {
    private ImpactFrames() {}

    private static double start = -1, len = 0;
    private static int style;
    private static Vec3 at;

    // the last world view, to put the hit on the screen
    private static final Matrix4f VIEW = new Matrix4f(), PROJ = new Matrix4f();
    private static Vec3 cam = Vec3.ZERO;
    private static boolean haveView = false;

    /** {light tint for negatives, dark ink, pattern colour, accent} per style */
    private static final int[][] PAL = {
            {0xFFFFFF, 0x050505, 0xFFFFFF, 0xD01010},   // 0 Asta
            {0xFFFFFF, 0x050505, 0xFFFFFF, 0xD01010},   // 1 Asta slash
            {0xA8DCFF, 0x040A2A, 0x4FB8FF, 0xFFFFFF},   // 2 Luck
            {0xE4DAFF, 0x07000F, 0x9A5CFF, 0xFFFFFF},   // 3 Equinox
            {0xD8C8FF, 0x07000F, 0x9A5CFF, 0xFFFFFF},   // 4 Yami
            {0xD8FFE8, 0x02261C, 0x6BE09A, 0xFFFFFF},   // 5 Yuno
            {0xEAF0FA, 0x10141C, 0xB8C4D8, 0xFFFFFF},   // 6 Nozel
            {0xFFDCA8, 0x2A0800, 0xFF8A2A, 0xFFF0C0},   // 7 Magna
            {0xFFC4BC, 0x200000, 0xE0302A, 0xFFE0D0},   // 8 Mereoleona
            {0xF4EEDC, 0x0A0610, 0xE8DCC0, 0x9A5CFF},   // 9 Zenon
            {0xE6ECF2, 0x0E1420, 0x9AA8B8, 0xFFFFFF},   // 10 Klaus
            {0xFFD6EA, 0x24081A, 0xFF8CC6, 0xFFFFFF},   // 11 Vanessa
            {0xE0D4FF, 0x0A0420, 0x9C78FF, 0xFFE6A0},   // 12 Finral
            {0xFFD2B0, 0x1E0600, 0xFF5A1E, 0xFFF0D0},   // 13 Fuegoleon: flame tongues
            {0xFFE6A8, 0x241000, 0xFFB02E, 0xFFFFFF},   // 14 Leopold: a lion's flame
            {0xD6FFC8, 0x03200A, 0x62E050, 0xFFF4B0},   // 15 William: spiralling leaves
            {0xFFD0D6, 0x14000A, 0xFF4458, 0xFFFFFF},   // 16 Jack: cuts
            {0xF2DFC0, 0x1C1208, 0xB88A4E, 0xFFFFFF},   // 17 Sol: slabs
            {0xCFFFF0, 0x021C16, 0x3CD4A8, 0xFFE7A0},   // 18 Vetto: fangs
            {0xFFE4F0, 0x2A0E1E, 0xFF8CC6, 0xFFFFFF},   // 19 Charmy: threads of cream
            {0xE6FAFF, 0x040E1C, 0x8CDFF5, 0x3CC8F0},   // 20 Julius
            {0xE2FFE6, 0x02140A, 0x44F06C, 0xFFFFFF}};  // 21 Jack's green horizon: the same long cuts, in green  // 20 Julius: clock marks closing in, a clock round the hit

    public static void start(int ticks, int st, Vec3 where) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !com.ironspellgrimoire.AddonClientConfig.impactFrames()) return;
        start = mc.level.getGameTime();
        len = Mth.clamp(ticks, 1, com.ironspellgrimoire.AddonClientConfig.maxFrameTicks());   // never long enough to blind you
        style = Mth.clamp(st, 0, PAL.length - 1);
        at = where == null || where.equals(Vec3.ZERO) ? null : where;
    }

    @SubscribeEvent
    public static void onLevel(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        VIEW.set(e.getPoseStack().last().pose());
        PROJ.set(e.getProjectionMatrix());
        cam = e.getCamera().getPosition();
        haveView = true;
    }

    @SubscribeEvent
    public static void onGui(RenderGuiEvent.Pre e) {
        Minecraft mc = Minecraft.getInstance();
        if (start < 0 || mc.level == null) return;
        double k = mc.level.getGameTime() + e.getPartialTick() - start;
        if (k >= len) { start = -1; return; }
        if (k < 0) return;
        GuiGraphics g = e.getGuiGraphics();
        try {
            g.flush();
            draw(g, k);
        } catch (Throwable ex) {
            start = -1;   // never leave the screen broken
        } finally {
            RenderSystem.defaultBlendFunc();
            RenderSystem.enableCull();
            RenderSystem.enableDepthTest();
        }
    }

    /** Where the hit is on the screen (gui units); the middle if it is behind you or unknown. */
    private static float[] focus(float W, float H) {
        float[] mid = {W / 2F, H / 2F};
        if (at == null || !haveView) return mid;
        Vector4f v = new Vector4f((float) (at.x - cam.x), (float) (at.y - cam.y), (float) (at.z - cam.z), 1F);
        VIEW.transform(v);
        PROJ.transform(v);
        if (v.w <= 0.05F) return mid;
        float x = (v.x / v.w + 1F) * 0.5F * W, y = (1F - v.y / v.w) * 0.5F * H;
        return new float[]{Mth.clamp(x, W * 0.12F, W * 0.88F), Mth.clamp(y, H * 0.12F, H * 0.88F)};
    }

    private static void draw(GuiGraphics g, double k) {
        float W = g.guiWidth(), H = g.guiHeight();
        int frame = (int) k;
        boolean negative = frame % 2 == 0;
        float fade = (float) Mth.clamp((len - k) / Math.min(1.0, len), 0.0, 1.0);   // lets go over the last tick
        fade = fade * fade * (3F - 2F * fade);
        float in = (float) (k - frame);                                               // 0..1 through this frame
        int[] c = PAL[style];
        float[] f = focus(W, H);
        float fx = f[0], fy = f[1];
        float reach = (float) Math.hypot(Math.max(fx, W - fx), Math.max(fy, H - fy)) + 8F;
        Matrix4f m = g.pose().last().pose();

        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        if (style == 3) { equinox(m, W, H, fx, fy, negative, fade, c); return; }

        // ---- the background
        if (negative) negative(m, 0, 0, W, H, fade, c[0]);
        else quad(m, 0, 0, W, H, c[1], 0.6F * fade);   // ink you can still see through

        // ---- the pattern, converging on the hit
        int line = negative ? c[1] : c[2], acc = negative ? c[1] : c[3];
        float la = (negative ? 0.85F : 1F) * fade;
        Random r = new Random(frame * 977L + style * 131L + 7);
        BufferBuilder bb = begin();
        float rush = 1F - 0.35F * smooth(in);   // inner ends rush toward the hit through the frame
        switch (style) {
            case 2 -> {   // Luck: jagged bolts cracking in from the edges to the hit, white cores in blue glow
                speedLines(bb, m, r, fx, fy, reach, 18, 0.55F * rush, 1.6F, line, la * 0.5F);
                for (int b = 0; b < 6; b++) {
                    double a = r.nextDouble() * Math.PI * 2;
                    float x = fx + (float) Math.cos(a) * reach, y = fy + (float) Math.sin(a) * reach;
                    float tx = fx + (r.nextFloat() - 0.5F) * 24F, ty = fy + (r.nextFloat() - 0.5F) * 24F;
                    int steps = 9;
                    for (int s = 0; s < steps; s++) {
                        float nx = x + (tx - x) / (steps - s) + (r.nextFloat() - 0.5F) * W * 0.06F;
                        float ny = y + (ty - y) / (steps - s) + (r.nextFloat() - 0.5F) * H * 0.06F;
                        float th = 3.2F * (1.15F - (s + 1) / (float) steps);
                        taper(bb, m, x, y, th + 3F, nx, ny, th + 2.4F, negative ? c[1] : c[2], la * 0.7F);
                        taper(bb, m, x, y, th, nx, ny, th * 0.8F, negative ? c[1] : c[3], la);
                        if (r.nextInt(4) == 0) {
                            float gx = nx + (r.nextFloat() - 0.5F) * W * 0.12F, gy = ny + (r.nextFloat() - 0.5F) * H * 0.12F;
                            taper(bb, m, nx, ny, 1.6F, gx, gy, 0.2F, negative ? c[1] : c[3], la * 0.9F);
                        }
                        x = nx; y = ny;
                    }
                }
            }
            case 4, 16, 21 -> {   // Yami / Jack: long diagonal cuts through the hit, one bright one right through it
                speedLines(bb, m, r, fx, fy, reach, 22, 0.5F * rush, 2.0F, line, la * 0.45F);
                float dx = 0.86F, dy = 0.5F, nx = -dy, ny = dx;
                for (int i = -3; i <= 3; i++) {
                    float off = i * H * 0.13F + (r.nextFloat() - 0.5F) * 8F;
                    float cx = fx + nx * off, cy = fy + ny * off;
                    blade(bb, m, cx, cy, dx, dy, reach * (0.7F + r.nextFloat() * 0.5F), 1F + r.nextFloat() * 1.4F, line, la * 0.8F);
                }
                blade(bb, m, fx, fy, dx, dy, reach * 1.3F, Math.max(3F, H / 55F), acc, la);
            }
            case 5, 15 -> {   // Yuno / William: wind spiralling in on the hit
                for (int a = 0; a < 6; a++) {
                    double a0 = a * Math.PI * 2 / 6 + r.nextDouble() * 0.3 + in * 0.5;
                    float px = fx + (float) Math.cos(a0) * reach, py = fy + (float) Math.sin(a0) * reach;
                    for (int s = 1; s <= 16; s++) {
                        double rr = reach * (1 - s / 17.0) * rush, aa = a0 + s * 0.22;
                        float qx = fx + (float) (Math.cos(aa) * rr), qy = fy + (float) (Math.sin(aa) * rr);
                        float w0 = 3.4F * (1F - (s - 1) / 17F), w1 = 3.4F * (1F - s / 17F);
                        taper(bb, m, px, py, w0, qx, qy, w1, s % 4 == 0 ? acc : line, la);
                        px = qx; py = qy;
                    }
                }
            }
            case 6, 18 -> {   // Nozel / Vetto: silver spikes stabbing in from every edge, needle tips at the hit
                for (int i = 0; i < 22; i++) {
                    double a = i * Math.PI * 2 / 22 + r.nextDouble() * 0.18;
                    float ex = fx + (float) Math.cos(a) * reach, ey = fy + (float) Math.sin(a) * reach;
                    float tip = (0.18F + r.nextFloat() * 0.3F) * rush;
                    float tx = fx + (ex - fx) * tip, ty = fy + (ey - fy) * tip;
                    float wd = 7F + r.nextFloat() * 6F;
                    taper(bb, m, ex, ey, wd, tx, ty, 0F, line, la);
                    taper(bb, m, ex, ey, wd * 0.3F, tx, ty, 0F, acc, la * 0.9F);
                }
            }
            case 7, 8, 13, 14 -> {   // Magna / Mereoleona / Fuegoleon / Leopold / Mereoleona: tongues of flame roaring out of the hit
                int n = style == 7 ? 16 : 20;
                float grow = 0.75F + 0.35F * smooth(in);
                for (int i = 0; i < n; i++) {
                    double a = i * Math.PI * 2 / n + r.nextDouble() * 0.15;
                    float px = fx + (float) Math.cos(a) * reach * 0.06F, py = fy + (float) Math.sin(a) * reach * 0.06F;
                    float base = (style == 8 ? 7F : 5.5F) * (0.7F + r.nextFloat() * 0.6F);
                    int segs = 7;
                    for (int s = 1; s <= segs; s++) {
                        double rr = reach * (0.05 + s * 0.09) * grow, wob = Math.sin(s * 1.7 + i) * 0.09 + (r.nextDouble() - 0.5) * 0.08;
                        float qx = fx + (float) (Math.cos(a + wob) * rr), qy = fy + (float) (Math.sin(a + wob) * rr);
                        float w0 = base * (1F - (s - 1) / (float) segs), w1 = base * (1F - s / (float) segs);
                        taper(bb, m, px, py, w0, qx, qy, w1, s < 3 ? acc : line, la);
                        px = qx; py = qy;
                    }
                }
            }
            case 9 -> {   // Zenon: the frame cracks like bone out of the hit, violet in the cracks
                for (int b = 0; b < 9; b++) {
                    double a = b * Math.PI * 2 / 9 + r.nextDouble() * 0.4;
                    float px = fx, py = fy;
                    for (int s = 0; s < 9; s++) {
                        a += (r.nextDouble() - 0.5) * 0.7;
                        float step = reach * 0.11F;
                        float qx = px + (float) Math.cos(a) * step, qy = py + (float) Math.sin(a) * step;
                        float w0 = 4.2F * (1F - s / 10F), w1 = 4.2F * (1F - (s + 1) / 10F);
                        taper(bb, m, px, py, w0 + 2F, qx, qy, w1 + 1.6F, acc, la * 0.8F);
                        taper(bb, m, px, py, w0 * 0.5F, qx, qy, w1 * 0.5F, line, la);
                        if (r.nextInt(3) == 0) {
                            double fa = a + (r.nextBoolean() ? 0.8 : -0.8);
                            taper(bb, m, qx, qy, 1.4F, qx + (float) Math.cos(fa) * step * 0.7F, qy + (float) Math.sin(fa) * step * 0.7F, 0F, line, la);
                        }
                        px = qx; py = qy;
                    }
                }
            }
            case 10, 17 -> {  // Klaus / Sol: heavy steel plates slamming in, their seams meeting on the hit
                float slam = 1F - smooth(Math.min(1F, in * 2F));
                for (int i = -3; i <= 3; i++) {
                    if (i == 0) continue;
                    float x = fx + i * W / 7F + (r.nextFloat() - 0.5F) * 6F, y = fy + i * H / 7F + (r.nextFloat() - 0.5F) * 6F;
                    quadTo(bb, m, x - 2.5F, -10, x + 2.5F, H + 10, line, la);
                    quadTo(bb, m, -10, y - 2.5F, W + 10, y + 2.5F, line, la);
                }
                float bw = 5F + slam * 10F;
                quadTo(bb, m, -10, fy - bw, W + 10, fy + bw, acc, la);
                quadTo(bb, m, fx - bw, -10, fx + bw, H + 10, acc, la * 0.8F);
            }
            case 11, 19 -> {  // Vanessa / Charmy: threads drawn taut across the frame, all crossing at the hit
                for (int i = 0; i < 26; i++) {
                    double a = r.nextDouble() * Math.PI;
                    float dx = (float) Math.cos(a) * reach, dy = (float) Math.sin(a) * reach;
                    float ox = (r.nextFloat() - 0.5F) * 10F, oy = (r.nextFloat() - 0.5F) * 10F;
                    taper(bb, m, fx - dx + ox, fy - dy + oy, 0.4F, fx + dx + ox, fy + dy + oy, 0.4F, i % 5 == 0 ? acc : line, la);
                }
                star(bb, m, fx, fy, Math.min(W, H) * 0.08F, 2F, acc, la);
            }
            case 20 -> {  // Julius: the minute marks of a giant clock closing in on the hit, a clock ring round it, two hands stopped
                for (int i = 0; i < 60; i++) {
                    double a0 = i * Math.PI * 2 / 60;
                    boolean five = i % 5 == 0;
                    float r0 = reach * (five ? 0.38F : 0.5F) * (1F - 0.2F * rush), r1 = reach * (1F - 0.15F * rush);
                    taper(bb, m, fx + (float) Math.cos(a0) * r0, fy + (float) Math.sin(a0) * r0, five ? 1.2F : 0.6F,
                            fx + (float) Math.cos(a0) * r1, fy + (float) Math.sin(a0) * r1, five ? 4.5F : 2.2F, five ? acc : line, la);
                }
                float cr = Math.min(W, H) * 0.2F;
                for (int j = 0; j < 48; j++) {
                    double a0 = j * Math.PI * 2 / 48, a1 = (j + 1) * Math.PI * 2 / 48;
                    taper(bb, m, fx + (float) Math.cos(a0) * cr, fy + (float) Math.sin(a0) * cr, 3F, fx + (float) Math.cos(a1) * cr, fy + (float) Math.sin(a1) * cr, 3F, acc, la);
                }
                for (int j = 0; j < 12; j++) {
                    double a = j * Math.PI / 6;
                    taper(bb, m, fx + (float) Math.cos(a) * cr * 0.82F, fy + (float) Math.sin(a) * cr * 0.82F, j % 3 == 0 ? 3F : 1.6F,
                            fx + (float) Math.cos(a) * cr * 0.96F, fy + (float) Math.sin(a) * cr * 0.96F, j % 3 == 0 ? 3F : 1.6F, acc, la);
                }
                taper(bb, m, fx, fy, 3F, fx + cr * 0.15F, fy - cr * 0.55F, 0.5F, acc, la);
                taper(bb, m, fx, fy, 2F, fx + cr * 0.72F, fy + cr * 0.12F, 0.5F, acc, la);
            }
            case 12 -> {  // Finral: rings of space closing in on the hit, a gold rim on the innermost
                for (int q = 0; q < 7; q++) {
                    float rr = reach * (0.12F + q * 0.13F) * (1F - 0.25F * smooth(in));
                    int seg = 40;
                    for (int j = 0; j < seg; j++) {
                        double a0 = j * Math.PI * 2 / seg, a1 = (j + 1) * Math.PI * 2 / seg;
                        taper(bb, m, fx + (float) Math.cos(a0) * rr, fy + (float) Math.sin(a0) * rr * 0.7F, q == 0 ? 3F : 1.4F,
                                fx + (float) Math.cos(a1) * rr, fy + (float) Math.sin(a1) * rr * 0.7F, q == 0 ? 3F : 1.4F, q == 0 ? acc : line, la * (1F - q * 0.1F));
                    }
                }
            }
            default -> {  // Asta: speed lines rushing in on the hit; the black frames get a red star on it
                speedLines(bb, m, r, fx, fy, reach, 46, 0.42F * rush, 4.5F, line, la);
                if (!negative) star(bb, m, fx, fy, Math.min(W, H) * (0.16F + 0.05F * smooth(in)), 4F, c[3], 0.9F * fade);
                if (style == 1) {   // the finisher: one huge slash cutting through the hit, red bleeding off it
                    float grow = (float) Math.min(1.0, (k + 0.4) * 1.5);
                    float dx = Mth.cos((float) Math.toRadians(-28)), dy = Mth.sin((float) Math.toRadians(-28));
                    float band = Math.max(3F, H / 40F);
                    blade(bb, m, fx, fy, dx, dy, reach * 1.3F * grow, band * 3F, 0xD01010, 0.9F * fade);
                    blade(bb, m, fx, fy, dx, dy, reach * 1.3F * grow, band, negative ? 0x050505 : 0xFFFFFF, fade);
                }
            }
        }
        BufferUploader.drawWithShader(bb.end());
    }

    /** Equinox: the screen split at the hit into negative and ink halves that swap each tick, one violet line between. */
    private static void equinox(Matrix4f m, float W, float H, float fx, float fy, boolean swap, float fade, int[] c) {
        if (!swap) { negative(m, 0, 0, fx, H, fade, c[0]); quad(m, fx, 0, W, H, c[1], 0.86F * fade); }
        else { quad(m, 0, 0, fx, H, c[1], 0.86F * fade); negative(m, fx, 0, W, H, fade, c[0]); }
        BufferBuilder bb = begin();
        taper(bb, m, fx, -10, 4.5F, fx, H + 10, 4.5F, c[2], fade);
        taper(bb, m, fx, -10, 1.4F, fx, H + 10, 1.4F, c[3], fade);
        taper(bb, m, -10, fy, 0.2F, fx, fy, 2.2F, c[2], 0.5F * fade);   // the horizon, thickest at the cut
        taper(bb, m, fx, fy, 2.2F, W + 10, fy, 0.2F, c[2], 0.5F * fade);
        BufferUploader.drawWithShader(bb.end());
    }

    // ------------------------------------------------------------------ drawing
    private static BufferBuilder begin() {
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        return bb;
    }

    /** The world under this rectangle turned to a negative (fading in by {@code a}), then tinted. */
    private static void negative(Matrix4f m, float x0, float y0, float x1, float y1, float a, int tint) {
        // result = src * (1 - dst) + dst * (1 - srcAlpha), src = a: a blend from the picture to its negative
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE_MINUS_DST_COLOR, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE);
        quadRaw(m, x0, y0, x1, y1, a, a, a, a);
        if (tint != 0xFFFFFF) {   // multiply by the grimoire's tint
            RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.DST_COLOR, GlStateManager.DestFactor.ZERO,
                    GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE);
            float tr = ((tint >> 16) & 255) / 255F, tg = ((tint >> 8) & 255) / 255F, tb = (tint & 255) / 255F;
            quadRaw(m, x0, y0, x1, y1, 1 + (tr - 1) * a, 1 + (tg - 1) * a, 1 + (tb - 1) * a, 1F);
        }
        RenderSystem.defaultBlendFunc();
    }

    private static void quad(Matrix4f m, float x0, float y0, float x1, float y1, int col, float a) {
        RenderSystem.defaultBlendFunc();
        quadRaw(m, x0, y0, x1, y1, ((col >> 16) & 255) / 255F, ((col >> 8) & 255) / 255F, (col & 255) / 255F, a);
    }

    private static void quadRaw(Matrix4f m, float x0, float y0, float x1, float y1, float r, float g, float b, float a) {
        BufferBuilder bb = begin();
        bb.vertex(m, x0, y0, 0).color(r, g, b, a).endVertex();
        bb.vertex(m, x0, y1, 0).color(r, g, b, a).endVertex();
        bb.vertex(m, x1, y1, 0).color(r, g, b, a).endVertex();
        bb.vertex(m, x1, y0, 0).color(r, g, b, a).endVertex();
        BufferUploader.drawWithShader(bb.end());
    }

    private static void quadTo(BufferBuilder bb, Matrix4f m, float x0, float y0, float x1, float y1, int col, float a) {
        float r = ((col >> 16) & 255) / 255F, g = ((col >> 8) & 255) / 255F, b = (col & 255) / 255F;
        bb.vertex(m, x0, y0, 0).color(r, g, b, a).endVertex();
        bb.vertex(m, x0, y1, 0).color(r, g, b, a).endVertex();
        bb.vertex(m, x1, y1, 0).color(r, g, b, a).endVertex();
        bb.vertex(m, x1, y0, 0).color(r, g, b, a).endVertex();
    }

    /** A strip from (x0, y0) to (x1, y1), {@code w0} wide at the start and {@code w1} at the end (0 = a point). */
    private static void taper(BufferBuilder bb, Matrix4f m, float x0, float y0, float w0, float x1, float y1, float w1, int col, float a) {
        float dx = x1 - x0, dy = y1 - y0, l = (float) Math.sqrt(dx * dx + dy * dy);
        if (l < 0.3F || a <= 0.01F) return;
        float nx = -dy / l * 0.5F, ny = dx / l * 0.5F;
        float r = ((col >> 16) & 255) / 255F, g = ((col >> 8) & 255) / 255F, b = (col & 255) / 255F;
        bb.vertex(m, x0 + nx * w0, y0 + ny * w0, 0).color(r, g, b, a).endVertex();
        bb.vertex(m, x0 - nx * w0, y0 - ny * w0, 0).color(r, g, b, a).endVertex();
        bb.vertex(m, x1 - nx * w1, y1 - ny * w1, 0).color(r, g, b, a).endVertex();
        bb.vertex(m, x1 + nx * w1, y1 + ny * w1, 0).color(r, g, b, a).endVertex();
    }

    /** Wedges from beyond the screen edge to a point near the hit: thick outside, a needle at the inner end. */
    private static void speedLines(BufferBuilder bb, Matrix4f m, Random r, float fx, float fy, float reach, int n,
                                   float inner, float maxW, int col, float a) {
        for (int i = 0; i < n; i++) {
            double ang = (i + r.nextFloat() * 0.8F) * Math.PI * 2 / n;
            float cs = (float) Math.cos(ang), sn = (float) Math.sin(ang);
            float r0 = reach * (inner + r.nextFloat() * 0.22F);
            float w = 1F + r.nextFloat() * maxW;
            taper(bb, m, fx + cs * reach, fy + sn * reach, w, fx + cs * r0, fy + sn * r0, 0F, col, a);
        }
    }

    /** A blade-shaped cut through (cx, cy): a point at both ends, {@code w} wide in the middle. */
    private static void blade(BufferBuilder bb, Matrix4f m, float cx, float cy, float dx, float dy, float half, float w, int col, float a) {
        taper(bb, m, cx - dx * half, cy - dy * half, 0F, cx, cy, w, col, a);
        taper(bb, m, cx, cy, w, cx + dx * half, cy + dy * half, 0F, col, a);
    }

    /** A four-point flash star on the hit, with four thinner points between. */
    private static void star(BufferBuilder bb, Matrix4f m, float cx, float cy, float size, float w, int col, float a) {
        for (int i = 0; i < 8; i++) {
            double ang = i * Math.PI / 4 + Math.PI / 8;
            float s = i % 2 == 0 ? size : size * 0.45F, ww = i % 2 == 0 ? w : w * 0.6F;
            taper(bb, m, cx, cy, ww, cx + (float) Math.cos(ang) * s, cy + (float) Math.sin(ang) * s, 0F, col, a);
        }
    }

    private static float smooth(float x) {
        x = Mth.clamp(x, 0F, 1F);
        return x * x * (3F - 2F * x);
    }
}
