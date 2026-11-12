package com.ironspellgrimoire.client;

import com.ironspellgrimoire.network.FxPacket;
import com.ironspellgrimoire.server.Boards;
import com.ironspellgrimoire.server.UiShotsServer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The automatic screenshot run - a tool, off unless the game is started with {@code -Disg.uishots=true} (the "UI
 * screenshots" workflow does that, in a window-less game on the build server). It makes a flat creative world, sets the
 * test player up (server/UiShotsServer: a grimoire, stars, a past hunt or two), then opens every one of the mod's menus
 * and pages and sets up the HUD trackers, one after another. Each is photographed at GUI scales 2 and 4 and run through
 * the layout checker (client/LayoutCheck) at every scale; the pictures go to screenshots/isg_*.png and the problems to
 * isg_uishots_report.txt. Then the game quits.
 */
@Mod.EventBusSubscriber(modid = "iron_spell_grimoire", value = Dist.CLIENT)
public final class UiShots {
    private UiShots() {}

    static final boolean ON = Boolean.getBoolean("isg.uishots");
    private static final int[] SHOT_SCALES = {2, 4};

    private record Step(String name, Runnable action, int delay, boolean hud) {}
    private static final List<Step> steps = new ArrayList<>();
    private static final List<String> report = new ArrayList<>();
    private static int phase, wait, index, shot = -1, waitShot, ticks;
    private static boolean shotDue;

    // ------------------------------------------------------------------ the run
    private static void onServer(Consumer<ServerPlayer> c) {
        Minecraft mc = Minecraft.getInstance();
        var server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) return;
        var uuid = mc.player.getUUID();
        server.execute(() -> { ServerPlayer p = server.getPlayerList().getPlayer(uuid); if (p != null) c.accept(p); });
    }

    private static void book(String part) {
        Minecraft mc = Minecraft.getInstance();
        var stack = net.minecraft.world.item.ItemStack.EMPTY;
        for (var s : mc.player.getInventory().items) if (com.ironspellgrimoire.server.GrimoireMastery.bookOf(s) != null) { stack = s; break; }
        if (stack.isEmpty()) { report.add("book | no grimoire in the test player's inventory"); return; }
        GrimoireBookScreen b = new GrimoireBookScreen(stack);
        mc.setScreen(b);
        b.showPart(part);
    }

    private static FxPacket fx(int shape) { FxPacket p = new FxPacket(); p.shape = (byte) shape; return p; }

    private static void plan() {
        Minecraft mc = Minecraft.getInstance();
        steps.add(new Step("setup", () -> onServer(UiShotsServer::prepare), 40, false));
        steps.add(new Step("hud_empty", () -> mc.setScreen(null), 20, true));
        for (String part : new String[]{"spells:0", "spells:1", "spells:3", "combos", "path", "upgrades", "tree", "memories:0", "memories:1"})
            steps.add(new Step("book_" + part.replace(':', '_'), () -> book(part), 15, false));
        steps.add(new Step("board_bounty", () -> onServer(p -> UiShotsServer.board(p, Boards.BOUNTY)), 20, false));
        steps.add(new Step("board_bounty_record", () -> { if (mc.screen instanceof BoardScreen b) b.showRecord(true); }, 5, false));
        steps.add(new Step("board_quest", () -> onServer(p -> UiShotsServer.board(p, Boards.QUEST)), 20, false));
        steps.add(new Step("board_quest_record", () -> { if (mc.screen instanceof BoardScreen b) b.showRecord(true); }, 5, false));
        steps.add(new Step("star_forge", () -> onServer(UiShotsServer::forge), 20, false));
        steps.add(new Step("altar", () -> onServer(UiShotsServer::altar), 20, false));
        steps.add(new Step("library", () -> onServer(UiShotsServer::library), 20, false));
        steps.add(new Step("waypoints", () -> onServer(UiShotsServer::waypoints), 20, false));
        // the HUD with everything on it: a hunt, a quest, the world boss, the rift map, a raid's wave banner
        steps.add(new Step("take_hunt_and_quest", () -> { mc.setScreen(null); onServer(p -> { UiShotsServer.take(p, Boards.BOUNTY); UiShotsServer.take(p, Boards.QUEST); }); }, 30, false));
        steps.add(new Step("board_hunting", () -> onServer(p -> UiShotsServer.board(p, Boards.BOUNTY)), 20, false));
        steps.add(new Step("hud_full", () -> {
            mc.setScreen(null);
            TrackerMode.set(0);
            FxPacket w = fx(78); w.life = 1; w.sweep = 0; w.r = 6000; w.c = mc.player.position().add(900, 0, -400); WorldBossHud.set(w);
            FxPacket m = fx(79); m.life = 400; m.id = 7; m.sweep = 3;   // a seven-room rift, standing in the third
            int[][] rooms = {{0, 0, 0, 1, 1}, {1, 0, 1, 1, 1}, {2, 0, 2, 0, 1 | 4}, {2, 1, 1, 0, 4 | 8}, {2, 2, 3, 0, 1}, {3, 2, 4, 0, 0}, {1, 1, 5, 0, 0}};
            double[] v = new double[18];
            for (int i = 0; i < rooms.length; i++) { int[] r = rooms[i]; v[i] = ((r[0] + 16) & 31) | ((r[1] + 16) & 31) << 5 | r[2] << 10 | r[3] << 13 | r[4] << 15; }
            m.c = new Vec3(v[0], v[1], v[2]); m.d = new Vec3(v[3], v[4], v[5]); m.u = new Vec3(v[6], 0, 0);
            RiftMap.set(m);
            FxPacket rb = fx(76); rb.sweep = 2; rb.h = 2; rb.life = 400; RaidBanner.start(rb);
        }, 20, true));
        steps.add(new Step("hud_compact", () -> TrackerMode.set(1), 10, true));
        steps.add(new Step("hud_hidden", () -> TrackerMode.set(2), 10, true));
    }

    private static void finish() {
        Minecraft mc = Minecraft.getInstance();
        Path out = mc.gameDirectory.toPath().resolve("isg_uishots_report.txt");
        List<String> lines = new ArrayList<>();
        lines.add("Iron Spell Grimoire - automatic UI check: " + steps.size() + " steps, " + report.size() + " problem(s)");
        lines.addAll(report);
        try { Files.write(out, lines, StandardCharsets.UTF_8); } catch (IOException ignored) {}
        mc.stop();
    }

    // ------------------------------------------------------------------ the clock
    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent e) {
        if (!ON || e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ticks++;
        if (ticks > 20 * 60 * 15) { report.add("run | gave up after 15 minutes at step " + index); finish(); return; }
        if (phase == 0) {   // wait for the title screen (or the first-launch screens), then make the world
            if (mc.getOverlay() != null || mc.screen == null || mc.level != null) return;
            if (!(mc.screen instanceof TitleScreen) && !mc.screen.getClass().getSimpleName().contains("Onboarding")) return;
            phase = 1;
            mc.options.onboardAccessibility = false;
            LevelSettings ls = new LevelSettings("isg_uishots_" + System.currentTimeMillis(), GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                    new GameRules(), WorldDataConfiguration.DEFAULT);
            mc.createWorldOpenFlows().createFreshLevel(ls.levelName(), ls, new WorldOptions(1234L, false, false),
                    reg -> reg.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
            return;
        }
        if (phase == 1) {   // in the world: let it settle
            if (mc.player == null || mc.level == null || mc.screen != null && !(mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen)) {
                if (mc.player != null && mc.screen != null && ticks % 40 == 0) mc.setScreen(null);   // a leftover "loading" screen
                return;
            }
            if (++wait < 100) return;
            phase = 2; wait = 0; plan();
            return;
        }
        if (phase != 2) return;
        if (shot >= 0) {   // photographing the current step at each scale
            if (shotDue) return;
            if (--waitShot > 0) return;
            shotDue = true;
            return;
        }
        if (index >= steps.size()) { phase = 3; finish(); return; }
        Step s = steps.get(index);
        if (wait == 0) { try { s.action().run(); } catch (Throwable t) { report.add(s.name() + " | failed to set up: " + t); } }
        if (++wait < s.delay()) return;
        wait = 0;
        if (s.name().equals("setup") || s.name().equals("take_hunt_and_quest")) { index++; return; }
        Screen sc = mc.screen;
        if (!s.hud() && sc == null) { report.add(s.name() + " | the menu didn't open"); index++; return; }
        for (String issue : LayoutCheck.audit(s.hud() ? null : sc)) report.add(s.name() + " | " + issue);
        shot = 0; scale(SHOT_SCALES[0]); waitShot = 4;
    }

    private static void scale(int s) {
        Minecraft mc = Minecraft.getInstance();
        mc.options.guiScale().set(s);
        mc.resizeDisplay();
    }

    @SubscribeEvent
    public static void onRender(TickEvent.RenderTickEvent e) {
        if (!ON || e.phase != TickEvent.Phase.END || !shotDue) return;
        shotDue = false;
        Minecraft mc = Minecraft.getInstance();
        Step s = steps.get(index);
        Screenshot.grab(mc.gameDirectory, "isg_" + s.name() + "_x" + SHOT_SCALES[shot] + ".png", mc.getMainRenderTarget(), c -> {});
        shot++;
        if (shot < SHOT_SCALES.length) { scale(SHOT_SCALES[shot]); waitShot = 4; }
        else { shot = -1; scale(2); index++; }
    }
}
