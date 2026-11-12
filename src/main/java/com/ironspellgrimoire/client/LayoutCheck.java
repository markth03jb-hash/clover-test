package com.ironspellgrimoire.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

/**
 * The layout checker, a tool for finding text that overlaps or runs off its panel in the mod's menus and HUD - with the
 * game's real font, at the real screen size. Off unless switched on with {@code /isglayout on}.
 * <p>
 * While on, the open menu (any of the mod's screens) is drawn a second time, invisibly, into a recording
 * {@link GuiGraphics} that notes where every piece of text and every filled panel lands; the mod's HUD (trackers,
 * rift map, broom, mode HUD, raid banner) likewise. It then looks for:
 * <ul>
 *   <li><b>overlap</b> - two pieces of text drawn over each other;</li>
 *   <li><b>off-panel</b> - text that starts inside a panel but runs past its edge;</li>
 *   <li><b>off-screen</b> - text past the edge of the screen;</li>
 *   <li><b>hud-collision</b> - HUD text over the hotbar or the boss bars.</li>
 * </ul>
 * Problems are outlined in red on screen, said once in chat, and written to {@code isg_layout_report.txt} in the
 * game folder. F9 checks the open menu (or the HUD) at every GUI scale at once.
 */
@Mod.EventBusSubscriber(modid = "iron_spell_grimoire", value = Dist.CLIENT)
public final class LayoutCheck {
    private LayoutCheck() {}

    public static final KeyMapping KEY = new KeyMapping("key.iron_spell_grimoire.layout_check", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F9, "key.categories.iron_spell_grimoire");

    private static boolean on;
    private static int ticks;
    private static boolean screenDue, hudDue, allScales;
    private static final Set<String> reported = new HashSet<>();
    /** The last check's problem rectangles, to outline: [x0, y0, x1, y1] at the current scale. */
    private static final List<float[]> marksScreen = new ArrayList<>(), marksHud = new ArrayList<>();

    // ------------------------------------------------------------------ what gets recorded
    record Text(String s, float x0, float y0, float x1, float y1) {}
    record Panel(float x0, float y0, float x1, float y1) {}

    /** A GuiGraphics that records where text and panels are drawn (and still draws them, with colour writes off). */
    static final class Recorder extends GuiGraphics {
        final List<Text> texts = new ArrayList<>();
        final List<Panel> panels = new ArrayList<>();
        Recorder(Minecraft mc) { super(mc, mc.renderBuffers().bufferSource()); }

        private float[] box(float x0, float y0, float x1, float y1) {
            Matrix4f m = pose().last().pose();
            float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
            for (float[] c : new float[][]{{x0, y0}, {x1, y0}, {x0, y1}, {x1, y1}}) {
                Vector3f v = m.transformPosition(new Vector3f(c[0], c[1], 0));
                minX = Math.min(minX, v.x); minY = Math.min(minY, v.y); maxX = Math.max(maxX, v.x); maxY = Math.max(maxY, v.y);
            }
            return new float[]{minX, minY, maxX, maxY};
        }

        private void note(String s, Font font, int w, float x, float y) {
            if (s == null || s.isBlank() || w <= 0) return;
            float[] b = box(x, y, x + w, y + font.lineHeight - 1);
            texts.add(new Text(s, b[0], b[1], b[2], b[3]));
        }

        @Override
        public int drawString(Font font, @Nullable String text, float x, float y, int color, boolean shadow) {
            if (text != null) note(text, font, font.width(text), x, y);
            return super.drawString(font, text, x, y, color, shadow);
        }

        @Override
        public int drawString(Font font, FormattedCharSequence text, float x, float y, int color, boolean shadow) {
            StringBuilder sb = new StringBuilder();
            text.accept((i, style, cp) -> { sb.appendCodePoint(cp); return true; });
            note(sb.toString(), font, font.width(text), x, y);
            return super.drawString(font, text, x, y, color, shadow);
        }

        @Override
        public void fill(RenderType type, int x0, int y0, int x1, int y1, int z, int color) {
            int a = color >>> 24;
            if (a >= 0xB0 && Math.abs(x1 - x0) >= 30 && Math.abs(y1 - y0) >= 12) {
                float[] b = box(Math.min(x0, x1), Math.min(y0, y1), Math.max(x0, x1), Math.max(y0, y1));
                panels.add(new Panel(b[0], b[1], b[2], b[3]));
            }
            super.fill(type, x0, y0, x1, y1, z, color);
        }
    }

    // ------------------------------------------------------------------ the checks
    private static final class Issue {
        final String kind, detail; final float[] rect;
        Issue(String kind, String detail, float[] rect) { this.kind = kind; this.detail = detail; this.rect = rect; }
    }

    private static String q(Text t) { String s = t.s().length() > 40 ? t.s().substring(0, 38) + ".." : t.s(); return "\"" + s + "\""; }
    private static String at(Text t) { return String.format("(%.0f,%.0f %.0fx%.0f)", t.x0(), t.y0(), t.x1() - t.x0(), t.y1() - t.y0()); }

    private static List<Issue> check(Recorder r, int w, int h, boolean hud) {
        List<Issue> out = new ArrayList<>();
        List<Text> ts = r.texts;
        for (int i = 0; i < ts.size(); i++) {
            Text a = ts.get(i);
            for (int j = i + 1; j < ts.size(); j++) {
                Text b = ts.get(j);
                float ox = Math.min(a.x1(), b.x1()) - Math.max(a.x0(), b.x0()), oy = Math.min(a.y1(), b.y1()) - Math.max(a.y0(), b.y0());
                if (ox < 2 || oy < 2) continue;
                boolean same = a.s().equals(b.s()) && Math.abs(a.x0() - b.x0()) < 1.5 && Math.abs(a.y0() - b.y0()) < 1.5;
                if (same) continue;   // the same text drawn twice in one place (an outline or glow pass)
                out.add(new Issue("overlap", q(a) + " " + at(a) + "  over  " + q(b) + " " + at(b),
                        new float[]{Math.min(a.x0(), b.x0()), Math.min(a.y0(), b.y0()), Math.max(a.x1(), b.x1()), Math.max(a.y1(), b.y1())}));
            }
            if (a.x0() < -0.5 || a.y0() < -0.5 || a.x1() > w + 0.5 || a.y1() > h + 0.5)
                out.add(new Issue("off-screen", q(a) + " " + at(a) + " on a " + w + "x" + h + " screen", new float[]{a.x0(), a.y0(), a.x1(), a.y1()}));
            // the smallest panel its start lies in: does it run past that panel's edge?
            float sx = a.x0() + 1, sy = (a.y0() + a.y1()) / 2;
            Panel best = null;
            for (Panel p : r.panels)
                if (sx >= p.x0() && sx <= p.x1() && sy >= p.y0() && sy <= p.y1()
                        && (best == null || (p.x1() - p.x0()) * (p.y1() - p.y0()) < (best.x1() - best.x0()) * (best.y1() - best.y0()))) best = p;
            if (best != null && (best.x1() - best.x0()) < w - 4 && (a.x1() > best.x1() + 1.5 || a.y1() > best.y1() + 1.5))
                out.add(new Issue("off-panel", q(a) + " " + at(a) + " runs past its panel (" + (int) best.x0() + "," + (int) best.y0() + " to " + (int) best.x1() + "," + (int) best.y1() + ")",
                        new float[]{a.x0(), a.y0(), a.x1(), a.y1()}));
            if (hud) {   // the hotbar, and the boss bars at the top
                boolean hot = a.y1() > h - 23 && a.x1() > w / 2f - 92 && a.x0() < w / 2f + 92;
                int bars = lastBars;
                boolean boss = bars > 0 && a.y0() < 12 + bars * 19 && a.x1() > w / 2f - 92 && a.x0() < w / 2f + 92;
                if (hot || boss) out.add(new Issue("hud-collision", q(a) + " " + at(a) + " is over the " + (hot ? "hotbar" : "boss bar"), new float[]{a.x0(), a.y0(), a.x1(), a.y1()}));
            }
        }
        return out;
    }

    /** How many boss bars were drawn last frame (counted as they draw). */
    private static int bars, lastBars;
    @SubscribeEvent public static void onBossBar(net.minecraftforge.client.event.CustomizeGuiOverlayEvent.BossEventProgress e) { if (on) bars++; }
    @SubscribeEvent public static void onHudPre(RenderGuiEvent.Pre e) { lastBars = bars; bars = 0; }

    // ------------------------------------------------------------------ running it
    /** Draws something into a fresh recorder, invisibly, at GUI scale {@code scale} (0 = as it is now). */
    private static Recorder record(int scale, java.util.function.Consumer<Recorder> draw) {
        Minecraft mc = Minecraft.getInstance();
        Window win = mc.getWindow();
        double was = win.getGuiScale();
        if (scale > 0) win.setGuiScale(scale);
        Recorder r = new Recorder(mc);
        RenderSystem.colorMask(false, false, false, false);
        try { draw.accept(r); r.flush(); } catch (Throwable t) { /* a screen that can't draw twice: skip it */ }
        finally {
            RenderSystem.colorMask(true, true, true, true);
            if (scale > 0) win.setGuiScale(was);
        }
        return r;
    }

    private static int maxScale() { Window w = Minecraft.getInstance().getWindow(); return w.calculateScale(0, Minecraft.getInstance().isEnforceUnicode()); }

    private static void runScreen(Screen s, boolean every, int mx, int my, float pt) {
        Minecraft mc = Minecraft.getInstance();
        int ow = s.width, oh = s.height;
        List<Issue> now = new ArrayList<>();
        int cur = (int) mc.getWindow().getGuiScale();
        for (int sc = every ? 1 : cur; sc <= (every ? maxScale() : cur); sc++) {
            final int scale = sc;
            int w = (int) Math.ceil(mc.getWindow().getWidth() / (double) scale), h = (int) Math.ceil(mc.getWindow().getHeight() / (double) scale);
            Recorder r = record(scale == cur ? 0 : scale, g -> { s.width = w; s.height = h; s.render(g, mx, my, pt); });
            s.width = ow; s.height = oh;
            List<Issue> is = check(r, w, h, false);
            if (scale == cur) { marksScreen.clear(); for (Issue i : is) marksScreen.add(i.rect); }
            for (Issue i : is) now.add(new Issue(i.kind, "[scale " + scale + "] " + i.detail, i.rect));
        }
        report(s.getClass().getSimpleName(), now);
    }

    private static void runHud(boolean every, float pt) {
        Minecraft mc = Minecraft.getInstance();
        List<Issue> now = new ArrayList<>();
        int cur = (int) mc.getWindow().getGuiScale();
        for (int sc = every ? 1 : cur; sc <= (every ? maxScale() : cur); sc++) {
            final int scale = sc;
            Recorder r = record(scale == cur ? 0 : scale, g -> {
                RenderGuiEvent.Post e = new RenderGuiEvent.Post(mc.getWindow(), g, pt);
                BountyHud.onGui(e); QuestHud.onGui(e); WorldBossHud.onGui(e); RiftMap.onGui(e); BroomHud.onGui(e);
                ModeHudOverlay.onGui(e); RaidBanner.onGui(e);
            });
            int w = (int) Math.ceil(mc.getWindow().getWidth() / (double) scale), h = (int) Math.ceil(mc.getWindow().getHeight() / (double) scale);
            List<Issue> is = check(r, w, h, true);
            if (scale == cur) { marksHud.clear(); for (Issue i : is) marksHud.add(i.rect); }
            for (Issue i : is) now.add(new Issue(i.kind, "[scale " + scale + "] " + i.detail, i.rect));
        }
        report("HUD", now);
    }

    private static void report(String where, List<Issue> issues) {
        Minecraft mc = Minecraft.getInstance();
        List<String> fresh = new ArrayList<>();
        for (Issue i : issues) {
            String line = where + " | " + i.kind + " | " + i.detail;
            if (reported.add(where + "|" + i.kind + "|" + i.detail.replaceAll("\\(\\d+,\\d+ ", "("))) fresh.add(line);
        }
        if (fresh.isEmpty()) return;
        Path file = mc.gameDirectory.toPath().resolve("isg_layout_report.txt");
        try {
            StringBuilder sb = new StringBuilder();
            String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            for (String l : fresh) sb.append(stamp).append(" | ").append(l).append('\n');
            Files.writeString(file, sb.toString(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {}
        if (mc.player != null) mc.player.displayClientMessage(Component.literal("Layout check: " + fresh.size() + " new problem(s) in " + where
                + " - outlined in red, listed in isg_layout_report.txt").withStyle(ChatFormatting.RED), false);
    }

    // ------------------------------------------------------------------ hooks
    private static boolean ours(Screen s) { return s != null && s.getClass().getName().startsWith("com.ironspellgrimoire."); }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !on) return;
        ticks++;
        if (ticks % 30 == 0) screenDue = true;
        if (ticks % 40 == 15) hudDue = true;
        Minecraft mc = Minecraft.getInstance();
        while (mc.screen == null && KEY.consumeClick()) { allScales = true; hudDue = true; }
    }

    @SubscribeEvent
    public static void onKeyInScreen(ScreenEvent.KeyPressed.Pre e) {
        if (on && ours(e.getScreen()) && KEY.matches(e.getKeyCode(), e.getScanCode())) { allScales = true; screenDue = true; e.setCanceled(true); }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onScreenRender(ScreenEvent.Render.Post e) {
        if (!on || !ours(e.getScreen())) return;
        if (screenDue) {
            screenDue = false;
            boolean every = allScales; allScales = false;
            runScreen(e.getScreen(), every, e.getMouseX(), e.getMouseY(), e.getPartialTick());
        }
        outline(e.getGuiGraphics(), marksScreen);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onHud(RenderGuiEvent.Post e) {
        if (!on || Minecraft.getInstance().screen != null) return;
        if (hudDue) {
            hudDue = false;
            boolean every = allScales; allScales = false;
            runHud(every, e.getPartialTick());
        }
        outline(e.getGuiGraphics(), marksHud);
    }

    private static void outline(GuiGraphics g, List<float[]> marks) {
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        for (float[] m : marks) g.renderOutline((int) Math.floor(m[0]) - 1, (int) Math.floor(m[1]) - 1, (int) Math.ceil(m[2] - m[0]) + 2, (int) Math.ceil(m[3] - m[1]) + 2, 0xFFFF2020);
        g.pose().popPose();
    }

    @SubscribeEvent
    public static void onCommands(RegisterClientCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("isglayout")
                .then(Commands.literal("on").executes(c -> { on = true; reported.clear(); say("Layout check on: open the mod's menus and pages; F9 checks every GUI size."); return 1; }))
                .then(Commands.literal("off").executes(c -> { on = false; marksScreen.clear(); marksHud.clear(); say("Layout check off."); return 1; })));
    }

    private static void say(String s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.displayClientMessage(Component.literal(s).withStyle(ChatFormatting.GOLD), false);
    }

    /** The F9 key's registration (on the mod bus). */
    @Mod.EventBusSubscriber(modid = "iron_spell_grimoire", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Keys {
        @SubscribeEvent public static void register(RegisterKeyMappingsEvent e) { e.register(KEY); }
    }
}
