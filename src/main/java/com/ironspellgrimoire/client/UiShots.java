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

    static final boolean TESTS = Boolean.getBoolean("isg.gametests");
    static final boolean ON = Boolean.getBoolean("isg.uishots") || TESTS;
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
        // the Blood Grimoire: Scarlet Moon seen from the front and behind (third person), then two spells
        steps.add(new Step("blood_gauge", () -> {
            TrackerMode.set(2); mc.options.hideGui = false; mc.setScreen(null);
            mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
            onServer(p -> UiShotsServer.bloodSpell(p, "gauge"));
        }, 30, true));
        steps.add(new Step("blood_overflow", () -> onServer(p -> UiShotsServer.bloodSpell(p, "overflow")), 16, true));
        steps.add(new Step("blood_master", () -> onServer(p -> UiShotsServer.bloodSpell(p, "master")), 6, true));
        steps.add(new Step("blood_book", () -> {
            var bk = io.redspace.ironsspellbooks.api.util.Utils.getPlayerSpellbookStack(mc.player);
            GrimoireBookScreen b = new GrimoireBookScreen(bk);
            mc.setScreen(b);
            b.showPart("spells:0");
        }, 12, false));
        steps.add(new Step("blood_book_ice", () -> { if (mc.screen instanceof GrimoireBookScreen b) b.iceView(true); }, 12, false));
        steps.add(new Step("blood_book_done", () -> mc.setScreen(null), 4, true));
        steps.add(new Step("blood_edge", () -> {
            mc.options.hideGui = true;
            mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
            mc.player.setXRot(22);
            onServer(p -> { p.setXRot(22); UiShotsServer.bloodSpell(p, "crimson_edge"); });
        }, 4, true));
        steps.add(new Step("blood_thorns", () -> onServer(p -> UiShotsServer.bloodSpell(p, "thorned_veins")), 9, true));
        steps.add(new Step("blood_tide", () -> onServer(p -> UiShotsServer.bloodSpell(p, "red_tide")), 7, true));
        steps.add(new Step("blood_pools", () -> {}, 30, true));
        steps.add(new Step("blood_drain", () -> onServer(p -> UiShotsServer.bloodSpell(p, "exsanguinate")), 9, true));
        steps.add(new Step("blood_drain_hit", () -> {}, 13, true));
        steps.add(new Step("blood_pact", () -> onServer(p -> UiShotsServer.bloodSpell(p, "blood_pact")), 8, true));
        steps.add(new Step("blood_wheel", () -> { mc.options.hideGui = false; GrimoireWheel.forceOpen = true; }, 12, true));
        steps.add(new Step("blood_wheel_done", () -> { GrimoireWheel.forceOpen = false; io.redspace.ironsspellbooks.gui.overlays.SpellWheelOverlay.instance.close(); mc.options.hideGui = true; }, 4, true));
        steps.add(new Step("blood_awaken", () -> {
            mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
            mc.player.setXRot(10);
            onServer(p -> { p.setXRot(10); UiShotsServer.scarletMoon(p); });
        }, 22, true));
        steps.add(new Step("blood_burst", () -> {}, 14, true));
        steps.add(new Step("blood_moon_front", () -> {}, 30, true));
        steps.add(new Step("blood_moon_back", () -> mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK), 12, true));
        // under the Moon every spell is ice
        steps.add(new Step("ice_step", () -> { mc.player.setXRot(22); onServer(p -> { p.setXRot(22); UiShotsServer.bloodSpell(p, "crimson_edge"); }); }, 6, true));
        steps.add(new Step("ice_serpent", () -> onServer(p -> UiShotsServer.bloodSpell(p, "thorned_veins")), 16, true));
        steps.add(new Step("ice_nova", () -> onServer(p -> UiShotsServer.bloodSpell(p, "red_tide")), 22, true));
        steps.add(new Step("ice_wall", () -> onServer(p -> UiShotsServer.bloodSpell(p, "crimson_bulwark")), 14, true));
        steps.add(new Step("ice_sentries", () -> onServer(p -> UiShotsServer.bloodSpell(p, "sanguine_volley")), 24, true));
        steps.add(new Step("ice_veil", () -> onServer(p -> UiShotsServer.bloodSpell(p, "blood_pact")), 12, true));
        steps.add(new Step("ice_prison", () -> onServer(p -> UiShotsServer.bloodSpell(p, "prison")), 16, true));
        steps.add(new Step("ice_icicles", () -> onServer(p -> UiShotsServer.bloodSpell(p, "hemorrhage")), 18, true));
        steps.add(new Step("ice_zero", () -> onServer(p -> UiShotsServer.bloodSpell(p, "exsanguinate")), 18, true));
        steps.add(new Step("ice_shatter", () -> {}, 26, true));
        steps.add(new Step("ice_moonfall", () -> onServer(p -> UiShotsServer.bloodSpell(p, "moonfall")), 12, true));
        steps.add(new Step("ice_impact", () -> {}, 9, true));
        steps.add(new Step("ice_throne", () -> { mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT); onServer(p -> UiShotsServer.bloodSpell(p, "throne")); }, 24, true));
        steps.add(new Step("ice_throne_back", () -> mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK), 6, true));
        steps.add(new Step("ice_hud", () -> { mc.options.hideGui = false; }, 20, true));
        steps.add(new Step("ice_wheel", () -> GrimoireWheel.forceOpen = true, 12, true));
        steps.add(new Step("ice_wheel_done", () -> { GrimoireWheel.forceOpen = false; io.redspace.ironsspellbooks.gui.overlays.SpellWheelOverlay.instance.close(); }, 4, true));
        steps.add(new Step("ice_book", () -> {
            var bk = io.redspace.ironsspellbooks.api.util.Utils.getPlayerSpellbookStack(mc.player);
            GrimoireBookScreen b = new GrimoireBookScreen(bk == null ? new net.minecraft.world.item.ItemStack(com.ironspellgrimoire.registry.AddonItemRegistry.BLOOD_GRIMOIRE.get()) : bk);
            mc.setScreen(b);
            b.showPart("spells:0");
        }, 15, false));
        steps.add(new Step("ice_book_done", () -> mc.setScreen(null), 4, true));
        // the Gravity Grimoire
        steps.add(new Step("grav_bullet", () -> {
            BloodFxClient.clear(); IceFxClient.clear();   // the Scarlet Moon from the shots before
            mc.options.hideGui = true;
            mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
            mc.player.setXRot(14);
            onServer(p -> { p.setXRot(14); UiShotsServer.element(p, "g:target"); UiShotsServer.element(p, "g:gravity_bullet"); });
        }, 7, true));
        steps.add(new Step("grav_well", () -> onServer(p -> UiShotsServer.element(p, "g:gravity_well")), 22, true));
        steps.add(new Step("grav_field", () -> onServer(p -> UiShotsServer.element(p, "g:crushing_field")), 26, true));
        steps.add(new Step("grav_lift", () -> onServer(p -> UiShotsServer.element(p, "g:gravity_lift")), 12, true));
        steps.add(new Step("grav_repulse", () -> onServer(p -> UiShotsServer.element(p, "g:repulse")), 4, true));
        steps.add(new Step("grav_orbit", () -> { mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT); onServer(p -> UiShotsServer.element(p, "g:orbiting_mass")); }, 14, true));
        steps.add(new Step("grav_domain", () -> onServer(p -> UiShotsServer.element(p, "g:mass_domain")), 22, true));
        steps.add(new Step("grav_star", () -> { mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK); onServer(p -> UiShotsServer.element(p, "g:starfall_collapse")); }, 24, true));
        steps.add(new Step("grav_implode", () -> {}, 9, true));
        steps.add(new Step("grav_wheel", () -> { mc.options.hideGui = false; onServer(p -> UiShotsServer.element(p, "g:end")); GrimoireWheel.forceOpen = true; }, 14, true));
        steps.add(new Step("grav_wheel_done", () -> { GrimoireWheel.forceOpen = false; io.redspace.ironsspellbooks.gui.overlays.SpellWheelOverlay.instance.close(); }, 4, true));
        steps.add(new Step("grav_book", () -> {
            GrimoireBookScreen b = new GrimoireBookScreen(new net.minecraft.world.item.ItemStack(com.ironspellgrimoire.registry.AddonItemRegistry.GRAVITY_GRIMOIRE.get()));
            mc.setScreen(b);
            b.showPart("spells:0");
        }, 15, false));
        steps.add(new Step("grav_book_done", () -> mc.setScreen(null), 4, true));
        // the Sound Grimoire
        steps.add(new Step("snd_pulse", () -> { mc.options.hideGui = true; onServer(p -> UiShotsServer.element(p, "s:sonic_pulse")); }, 7, true));
        steps.add(new Step("snd_strike", () -> onServer(p -> UiShotsServer.element(p, "s:tuning_strike")), 6, true));
        steps.add(new Step("snd_lance", () -> onServer(p -> UiShotsServer.element(p, "s:sonic_lance")), 5, true));
        steps.add(new Step("snd_silence", () -> onServer(p -> UiShotsServer.element(p, "s:silence_field")), 16, true));
        steps.add(new Step("snd_chamber", () -> onServer(p -> UiShotsServer.element(p, "s:echo_chamber")), 24, true));
        steps.add(new Step("snd_clap", () -> onServer(p -> UiShotsServer.element(p, "s:thunderclap")), 4, true));
        steps.add(new Step("snd_barrier", () -> { mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT); onServer(p -> UiShotsServer.element(p, "s:sound_barrier")); }, 14, true));
        steps.add(new Step("snd_crescendo", () -> onServer(p -> UiShotsServer.element(p, "s:crescendo")), 18, true));
        steps.add(new Step("snd_chord", () -> { mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK); onServer(p -> UiShotsServer.element(p, "s:final_chord")); }, 16, true));
        steps.add(new Step("snd_blast", () -> {}, 9, true));
        steps.add(new Step("snd_wheel", () -> { mc.options.hideGui = false; onServer(p -> UiShotsServer.element(p, "s:end")); GrimoireWheel.forceOpen = true; }, 14, true));
        steps.add(new Step("snd_wheel_done", () -> { GrimoireWheel.forceOpen = false; io.redspace.ironsspellbooks.gui.overlays.SpellWheelOverlay.instance.close(); }, 4, true));
        steps.add(new Step("snd_book", () -> {
            GrimoireBookScreen b = new GrimoireBookScreen(new net.minecraft.world.item.ItemStack(com.ironspellgrimoire.registry.AddonItemRegistry.SOUND_GRIMOIRE.get()));
            mc.setScreen(b);
            b.showPart("spells:0");
        }, 15, false));
        steps.add(new Step("snd_book_done", () -> mc.setScreen(null), 4, true));
        steps.add(new Step("ink_target", () -> { mc.options.hideGui = true; onServer(p -> UiShotsServer.element(p, "i:target")); }, 3, true));
        steps.add(new Step("ink_stroke", () -> { mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK); onServer(p -> UiShotsServer.element(p, "i:brush_stroke")); }, 5, true));
        steps.add(new Step("ink_splash", () -> { onServer(p -> UiShotsServer.element(p, "i:ink_splash")); }, 14, true));
        steps.add(new Step("ink_serpent", () -> { onServer(p -> UiShotsServer.element(p, "i:ink_serpent")); }, 14, true));
        steps.add(new Step("ink_seal", () -> { onServer(p -> UiShotsServer.element(p, "i:seal_script")); }, 16, true));
        steps.add(new Step("ink_burst", () -> { }, 8, true));
        steps.add(new Step("ink_beasts", () -> { onServer(p -> UiShotsServer.element(p, "i:ink_beasts")); }, 12, true));
        steps.add(new Step("ink_wave", () -> { onServer(p -> UiShotsServer.element(p, "i:ink_wave")); }, 10, true));
        steps.add(new Step("ink_master", () -> { mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT); onServer(p -> UiShotsServer.element(p, "i:masterpiece")); }, 16, true));
        steps.add(new Step("ink_grand", () -> { mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK); onServer(p -> UiShotsServer.element(p, "i:grand_calligraphy")); }, 20, true));
        steps.add(new Step("ink_fall", () -> { }, 13, true));
        steps.add(new Step("ink_wheel", () -> { mc.options.hideGui = false; onServer(p -> UiShotsServer.element(p, "i:end")); GrimoireWheel.forceOpen = true; }, 14, true));
        steps.add(new Step("ink_wheel_done", () -> { GrimoireWheel.forceOpen = false; io.redspace.ironsspellbooks.gui.overlays.SpellWheelOverlay.instance.close(); mc.options.hideGui = true; }, 4, true));
        steps.add(new Step("ink_book", () -> {
            GrimoireBookScreen b = new GrimoireBookScreen(new net.minecraft.world.item.ItemStack(com.ironspellgrimoire.registry.AddonItemRegistry.INK_GRIMOIRE.get()));
            mc.setScreen(b);
            b.showPart("spells:0");
        }, 15, false));
        steps.add(new Step("ink_book_done", () -> mc.setScreen(null), 4, true));
        steps.add(new Step("err_bolt", () -> { mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK); onServer(p -> UiShotsServer.element(p, "e:glitch_bolt")); }, 3, true));
        steps.add(new Step("err_threads", () -> { onServer(p -> UiShotsServer.element(p, "e:corrupted_threads")); }, 10, true));
        steps.add(new Step("err_lag", () -> { onServer(p -> UiShotsServer.element(p, "e:lag_spike")); }, 12, true));
        steps.add(new Step("err_zone", () -> { onServer(p -> UiShotsServer.element(p, "e:crash_zone")); }, 14, true));
        steps.add(new Step("err_windows", () -> { onServer(p -> UiShotsServer.element(p, "e:error_windows")); }, 12, true));
        steps.add(new Step("err_cube", () -> { onServer(p -> UiShotsServer.element(p, "e:blue_screen")); }, 14, true));
        steps.add(new Step("err_fatal", () -> { mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT); onServer(p -> UiShotsServer.element(p, "e:fatal_error")); }, 14, true));
        steps.add(new Step("err_crash", () -> { mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK); onServer(p -> UiShotsServer.element(p, "e:system_crash")); }, 20, true));
        steps.add(new Step("err_crashed", () -> { }, 10, true));
        steps.add(new Step("err_wheel", () -> { mc.options.hideGui = false; onServer(p -> UiShotsServer.element(p, "e:end")); GrimoireWheel.forceOpen = true; }, 14, true));
        steps.add(new Step("err_wheel_done", () -> { GrimoireWheel.forceOpen = false; io.redspace.ironsspellbooks.gui.overlays.SpellWheelOverlay.instance.close(); mc.options.hideGui = true; }, 4, true));
        steps.add(new Step("err_book", () -> {
            GrimoireBookScreen b = new GrimoireBookScreen(new net.minecraft.world.item.ItemStack(com.ironspellgrimoire.registry.AddonItemRegistry.ERROR_GRIMOIRE.get()));
            mc.setScreen(b);
            b.showPart("spells:0");
        }, 15, false));
        steps.add(new Step("err_book_done", () -> mc.setScreen(null), 4, true));
        steps.add(new Step("tre_volley", () -> { mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK); onServer(p -> UiShotsServer.element(p, "t:vault_volley")); }, 8, true));
        steps.add(new Step("tre_spear", () -> { onServer(p -> UiShotsServer.element(p, "t:gilded_spear")); }, 8, true));
        steps.add(new Step("tre_chains", () -> { onServer(p -> UiShotsServer.element(p, "t:vault_chains")); }, 10, true));
        steps.add(new Step("tre_wall", () -> { onServer(p -> UiShotsServer.element(p, "t:blade_wall")); }, 10, true));
        steps.add(new Step("tre_rain", () -> { onServer(p -> UiShotsServer.element(p, "t:blade_rain")); }, 16, true));
        steps.add(new Step("tre_barrage", () -> { onServer(p -> UiShotsServer.element(p, "t:treasure_barrage")); }, 14, true));
        steps.add(new Step("tre_kings", () -> { mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT); onServer(p -> UiShotsServer.element(p, "t:kings_vault")); }, 16, true));
        steps.add(new Step("tre_breaker", () -> { mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK); onServer(p -> UiShotsServer.element(p, "t:vault_breaker")); }, 22, true));
        steps.add(new Step("tre_torn", () -> { }, 8, true));
        steps.add(new Step("tre_wheel", () -> { mc.options.hideGui = false; onServer(p -> UiShotsServer.element(p, "t:end")); GrimoireWheel.forceOpen = true; }, 14, true));
        steps.add(new Step("tre_wheel_done", () -> { GrimoireWheel.forceOpen = false; io.redspace.ironsspellbooks.gui.overlays.SpellWheelOverlay.instance.close(); mc.options.hideGui = true; }, 4, true));
        steps.add(new Step("tre_book", () -> {
            GrimoireBookScreen b = new GrimoireBookScreen(new net.minecraft.world.item.ItemStack(com.ironspellgrimoire.registry.AddonItemRegistry.TREASURE_GRIMOIRE.get()));
            mc.setScreen(b);
            b.showPart("spells:0");
        }, 15, false));
        steps.add(new Step("tre_book_done", () -> mc.setScreen(null), 4, true));
        steps.add(new Step("chn_target", () -> { mc.options.hideGui = true; onServer(p -> { p.setXRot(0); UiShotsServer.element(p, "c:target"); }); }, 3, true));
        steps.add(new Step("chn_chain_lash", () -> { mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK); onServer(p -> UiShotsServer.element(p, "c:chain_lash")); }, 12, true));
        steps.add(new Step("chn_binding_chain", () -> { onServer(p -> UiShotsServer.element(p, "c:binding_chain")); }, 12, true));
        steps.add(new Step("chn_judgment_chain", () -> { onServer(p -> UiShotsServer.element(p, "c:judgment_chain")); }, 12, true));
        steps.add(new Step("chn_chain_net", () -> { onServer(p -> UiShotsServer.element(p, "c:chain_net")); }, 12, true));
        steps.add(new Step("chn_anchor_chain", () -> { onServer(p -> UiShotsServer.element(p, "c:anchor_chain")); }, 12, true));
        steps.add(new Step("chn_restriction", () -> { mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT); onServer(p -> UiShotsServer.element(p, "c:restriction")); }, 16, true));
        steps.add(new Step("chn_thousand_chains", () -> { mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK); onServer(p -> UiShotsServer.element(p, "c:thousand_chains")); }, 22, true));
        steps.add(new Step("chn_after", () -> { }, 10, true));
        steps.add(new Step("chn_wheel", () -> { mc.options.hideGui = false; onServer(p -> UiShotsServer.element(p, "c:end")); GrimoireWheel.forceOpen = true; }, 14, true));
        steps.add(new Step("chn_wheel_done", () -> { GrimoireWheel.forceOpen = false; io.redspace.ironsspellbooks.gui.overlays.SpellWheelOverlay.instance.close(); mc.options.hideGui = true; }, 4, true));
        steps.add(new Step("chn_book", () -> {
            GrimoireBookScreen b = new GrimoireBookScreen(new net.minecraft.world.item.ItemStack(com.ironspellgrimoire.registry.AddonItemRegistry.CHAIN_GRIMOIRE.get()));
            mc.setScreen(b);
            b.showPart("spells:0");
        }, 15, false));
        steps.add(new Step("chn_book_done", () -> mc.setScreen(null), 4, true));
        steps.add(new Step("hun_target", () -> { mc.options.hideGui = true; onServer(p -> { p.setXRot(0); UiShotsServer.element(p, "h:target"); }); }, 3, true));
        steps.add(new Step("hun_saw_cleave", () -> { mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK); onServer(p -> UiShotsServer.element(p, "h:saw_cleave")); }, 12, true));
        steps.add(new Step("hun_silver_shot", () -> { onServer(p -> UiShotsServer.element(p, "h:silver_shot")); }, 12, true));
        steps.add(new Step("hun_rending_spin", () -> { onServer(p -> UiShotsServer.element(p, "h:rending_spin")); }, 12, true));
        steps.add(new Step("hun_blood_volley", () -> { onServer(p -> UiShotsServer.element(p, "h:blood_volley")); }, 12, true));
        steps.add(new Step("hun_blood_moon_hunt", () -> { mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT); onServer(p -> UiShotsServer.element(p, "h:blood_moon_hunt")); }, 16, true));
        steps.add(new Step("hun_nightmare_cleave", () -> { mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK); onServer(p -> UiShotsServer.element(p, "h:nightmare_cleave")); }, 22, true));
        steps.add(new Step("hun_after", () -> { }, 10, true));
        steps.add(new Step("hun_wheel", () -> { mc.options.hideGui = false; onServer(p -> UiShotsServer.element(p, "h:end")); GrimoireWheel.forceOpen = true; }, 14, true));
        steps.add(new Step("hun_wheel_done", () -> { GrimoireWheel.forceOpen = false; io.redspace.ironsspellbooks.gui.overlays.SpellWheelOverlay.instance.close(); mc.options.hideGui = true; }, 4, true));
        steps.add(new Step("hun_book", () -> {
            GrimoireBookScreen b = new GrimoireBookScreen(new net.minecraft.world.item.ItemStack(com.ironspellgrimoire.registry.AddonItemRegistry.HUNTER_GRIMOIRE.get()));
            mc.setScreen(b);
            b.showPart("spells:0");
        }, 15, false));
        steps.add(new Step("hun_book_done", () -> mc.setScreen(null), 4, true));
        steps.add(new Step("voi_target", () -> { mc.options.hideGui = true; onServer(p -> { p.setXRot(0); UiShotsServer.element(p, "v:target"); }); }, 3, true));
        steps.add(new Step("voi_erase", () -> { mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK); onServer(p -> UiShotsServer.element(p, "v:erase")); }, 12, true));
        steps.add(new Step("voi_nullify", () -> { onServer(p -> UiShotsServer.element(p, "v:nullify")); }, 12, true));
        steps.add(new Step("voi_seam", () -> { onServer(p -> UiShotsServer.element(p, "v:seam")); }, 12, true));
        steps.add(new Step("voi_void_lance", () -> { onServer(p -> UiShotsServer.element(p, "v:void_lance")); }, 12, true));
        steps.add(new Step("voi_delete", () -> { onServer(p -> UiShotsServer.element(p, "v:delete")); }, 12, true));
        steps.add(new Step("voi_emptiness", () -> { mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT); onServer(p -> UiShotsServer.element(p, "v:emptiness")); }, 16, true));
        steps.add(new Step("voi_origin_zero", () -> { mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK); onServer(p -> UiShotsServer.element(p, "v:origin_zero")); }, 22, true));
        steps.add(new Step("voi_after", () -> { }, 10, true));
        steps.add(new Step("voi_wheel", () -> { mc.options.hideGui = false; onServer(p -> UiShotsServer.element(p, "v:end")); GrimoireWheel.forceOpen = true; }, 14, true));
        steps.add(new Step("voi_wheel_done", () -> { GrimoireWheel.forceOpen = false; io.redspace.ironsspellbooks.gui.overlays.SpellWheelOverlay.instance.close(); mc.options.hideGui = true; }, 4, true));
        steps.add(new Step("voi_book", () -> {
            GrimoireBookScreen b = new GrimoireBookScreen(new net.minecraft.world.item.ItemStack(com.ironspellgrimoire.registry.AddonItemRegistry.VOID_GRIMOIRE.get()));
            mc.setScreen(b);
            b.showPart("spells:0");
        }, 15, false));
        steps.add(new Step("voi_book_done", () -> mc.setScreen(null), 4, true));
        steps.add(new Step("gam_target", () -> { mc.options.hideGui = true; onServer(p -> { p.setXRot(0); UiShotsServer.element(p, "b:target"); }); }, 3, true));
        steps.add(new Step("gam_coin_toss", () -> { mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK); onServer(p -> UiShotsServer.element(p, "b:coin_toss")); }, 12, true));
        steps.add(new Step("gam_roulette", () -> { onServer(p -> UiShotsServer.element(p, "b:roulette")); }, 12, true));
        steps.add(new Step("gam_slot_machine", () -> { onServer(p -> UiShotsServer.element(p, "b:slot_machine")); }, 12, true));
        steps.add(new Step("gam_loaded_dice", () -> { onServer(p -> UiShotsServer.element(p, "b:loaded_dice")); }, 12, true));
        steps.add(new Step("gam_all_in", () -> { mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT); onServer(p -> UiShotsServer.element(p, "b:all_in")); }, 16, true));
        steps.add(new Step("gam_jackpot", () -> { mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK); onServer(p -> UiShotsServer.element(p, "b:jackpot")); }, 22, true));
        steps.add(new Step("gam_after", () -> { }, 10, true));
        steps.add(new Step("gam_wheel", () -> { mc.options.hideGui = false; onServer(p -> UiShotsServer.element(p, "b:end")); GrimoireWheel.forceOpen = true; }, 14, true));
        steps.add(new Step("gam_wheel_done", () -> { GrimoireWheel.forceOpen = false; io.redspace.ironsspellbooks.gui.overlays.SpellWheelOverlay.instance.close(); mc.options.hideGui = true; }, 4, true));
        steps.add(new Step("gam_book", () -> {
            GrimoireBookScreen b = new GrimoireBookScreen(new net.minecraft.world.item.ItemStack(com.ironspellgrimoire.registry.AddonItemRegistry.GAMBLER_GRIMOIRE.get()));
            mc.setScreen(b);
            b.showPart("spells:0");
        }, 15, false));
        steps.add(new Step("gam_book_done", () -> mc.setScreen(null), 4, true));
        steps.add(new Step("tar_target", () -> { mc.options.hideGui = true; onServer(p -> { p.setXRot(0); UiShotsServer.element(p, "r:target"); }); }, 3, true));
        steps.add(new Step("tar_draw_card", () -> { mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK); onServer(p -> UiShotsServer.element(p, "r:draw_card")); }, 12, true));
        steps.add(new Step("tar_the_magician", () -> { onServer(p -> UiShotsServer.element(p, "r:the_magician")); }, 12, true));
        steps.add(new Step("tar_the_tower", () -> { onServer(p -> UiShotsServer.element(p, "r:the_tower")); }, 12, true));
        steps.add(new Step("tar_death_card", () -> { onServer(p -> UiShotsServer.element(p, "r:death_card")); }, 12, true));
        steps.add(new Step("tar_the_hanged_man", () -> { onServer(p -> UiShotsServer.element(p, "r:the_hanged_man")); }, 12, true));
        steps.add(new Step("tar_the_world", () -> { mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT); onServer(p -> UiShotsServer.element(p, "r:the_world")); }, 16, true));
        steps.add(new Step("tar_judgement", () -> { mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK); onServer(p -> UiShotsServer.element(p, "r:judgement")); }, 22, true));
        steps.add(new Step("tar_after", () -> { }, 10, true));
        steps.add(new Step("tar_wheel", () -> { mc.options.hideGui = false; onServer(p -> UiShotsServer.element(p, "r:end")); GrimoireWheel.forceOpen = true; }, 14, true));
        steps.add(new Step("tar_wheel_done", () -> { GrimoireWheel.forceOpen = false; io.redspace.ironsspellbooks.gui.overlays.SpellWheelOverlay.instance.close(); mc.options.hideGui = true; }, 4, true));
        steps.add(new Step("tar_book", () -> {
            GrimoireBookScreen b = new GrimoireBookScreen(new net.minecraft.world.item.ItemStack(com.ironspellgrimoire.registry.AddonItemRegistry.TAROT_GRIMOIRE.get()));
            mc.setScreen(b);
            b.showPart("spells:0");
        }, 15, false));
        steps.add(new Step("tar_book_done", () -> mc.setScreen(null), 4, true));
        steps.add(new Step("aby_target", () -> { mc.options.hideGui = true; onServer(p -> { p.setXRot(0); UiShotsServer.element(p, "a:target"); }); }, 3, true));
        steps.add(new Step("aby_anglers_lure", () -> { mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK); onServer(p -> UiShotsServer.element(p, "a:anglers_lure")); }, 12, true));
        steps.add(new Step("aby_abyssal_grasp", () -> { onServer(p -> UiShotsServer.element(p, "a:abyssal_grasp")); }, 12, true));
        steps.add(new Step("aby_undertow", () -> { onServer(p -> UiShotsServer.element(p, "a:undertow")); }, 12, true));
        steps.add(new Step("aby_leviathan", () -> { onServer(p -> UiShotsServer.element(p, "a:leviathan")); }, 12, true));
        steps.add(new Step("aby_hadal_weight", () -> { onServer(p -> UiShotsServer.element(p, "a:hadal_weight")); }, 12, true));
        steps.add(new Step("aby_bioluminescence", () -> { mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT); onServer(p -> UiShotsServer.element(p, "a:bioluminescence")); }, 16, true));
        steps.add(new Step("aby_the_trench", () -> { mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK); onServer(p -> UiShotsServer.element(p, "a:the_trench")); }, 22, true));
        steps.add(new Step("aby_after", () -> { }, 10, true));
        steps.add(new Step("aby_wheel", () -> { mc.options.hideGui = false; onServer(p -> UiShotsServer.element(p, "a:end")); GrimoireWheel.forceOpen = true; }, 14, true));
        steps.add(new Step("aby_wheel_done", () -> { GrimoireWheel.forceOpen = false; io.redspace.ironsspellbooks.gui.overlays.SpellWheelOverlay.instance.close(); mc.options.hideGui = true; }, 4, true));
        steps.add(new Step("aby_book", () -> {
            GrimoireBookScreen b = new GrimoireBookScreen(new net.minecraft.world.item.ItemStack(com.ironspellgrimoire.registry.AddonItemRegistry.ABYSS_GRIMOIRE.get()));
            mc.setScreen(b);
            b.showPart("spells:0");
        }, 15, false));
        steps.add(new Step("aby_book_done", () -> mc.setScreen(null), 4, true));
        steps.add(new Step("mec_target", () -> { mc.options.hideGui = true; onServer(p -> { p.setXRot(0); UiShotsServer.element(p, "m:target"); }); }, 3, true));
        steps.add(new Step("mec_rivet_barrage", () -> { mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK); onServer(p -> UiShotsServer.element(p, "m:rivet_barrage")); }, 12, true));
        steps.add(new Step("mec_rocket_fist", () -> { onServer(p -> UiShotsServer.element(p, "m:rocket_fist")); }, 12, true));
        steps.add(new Step("mec_piston_stomp", () -> { onServer(p -> UiShotsServer.element(p, "m:piston_stomp")); }, 12, true));
        steps.add(new Step("mec_missile_swarm", () -> { onServer(p -> UiShotsServer.element(p, "m:missile_swarm")); }, 12, true));
        steps.add(new Step("mec_rail_cannon", () -> { onServer(p -> UiShotsServer.element(p, "m:rail_cannon")); }, 12, true));
        steps.add(new Step("mec_pilot", () -> { mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT); onServer(p -> UiShotsServer.element(p, "m:pilot")); }, 16, true));
        steps.add(new Step("mec_titan_buster", () -> { mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK); onServer(p -> UiShotsServer.element(p, "m:titan_buster")); }, 22, true));
        steps.add(new Step("mec_after", () -> { }, 10, true));
        steps.add(new Step("mec_wheel", () -> { mc.options.hideGui = false; onServer(p -> UiShotsServer.element(p, "m:end")); GrimoireWheel.forceOpen = true; }, 14, true));
        steps.add(new Step("mec_wheel_done", () -> { GrimoireWheel.forceOpen = false; io.redspace.ironsspellbooks.gui.overlays.SpellWheelOverlay.instance.close(); mc.options.hideGui = true; }, 4, true));
        steps.add(new Step("mec_book", () -> {
            GrimoireBookScreen b = new GrimoireBookScreen(new net.minecraft.world.item.ItemStack(com.ironspellgrimoire.registry.AddonItemRegistry.MECH_GRIMOIRE.get()));
            mc.setScreen(b);
            b.showPart("spells:0");
        }, 15, false));
        steps.add(new Step("mec_book_done", () -> mc.setScreen(null), 4, true));
        steps.add(new Step("wea_target", () -> { mc.options.hideGui = true; onServer(p -> { p.setXRot(0); UiShotsServer.element(p, "w:target"); }); }, 3, true));
        steps.add(new Step("wea_sunbeam", () -> { mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK); onServer(p -> UiShotsServer.element(p, "w:sunbeam")); }, 12, true));
        steps.add(new Step("wea_downpour", () -> { onServer(p -> UiShotsServer.element(p, "w:downpour")); }, 12, true));
        steps.add(new Step("wea_thunder_strike", () -> { onServer(p -> UiShotsServer.element(p, "w:thunder_strike")); }, 12, true));
        steps.add(new Step("wea_hailstorm", () -> { onServer(p -> UiShotsServer.element(p, "w:hailstorm")); }, 12, true));
        steps.add(new Step("wea_cyclone", () -> { onServer(p -> UiShotsServer.element(p, "w:cyclone")); }, 12, true));
        steps.add(new Step("wea_rainbow", () -> { onServer(p -> UiShotsServer.element(p, "w:rainbow")); }, 12, true));
        steps.add(new Step("wea_eye_of_the_storm", () -> { mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT); onServer(p -> UiShotsServer.element(p, "w:eye_of_the_storm")); }, 16, true));
        steps.add(new Step("wea_cataclysm", () -> { mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK); onServer(p -> UiShotsServer.element(p, "w:cataclysm")); }, 22, true));
        steps.add(new Step("wea_after", () -> { }, 10, true));
        steps.add(new Step("wea_wheel", () -> { mc.options.hideGui = false; onServer(p -> UiShotsServer.element(p, "w:end")); GrimoireWheel.forceOpen = true; }, 14, true));
        steps.add(new Step("wea_wheel_done", () -> { GrimoireWheel.forceOpen = false; io.redspace.ironsspellbooks.gui.overlays.SpellWheelOverlay.instance.close(); mc.options.hideGui = true; }, 4, true));
        steps.add(new Step("wea_book", () -> {
            GrimoireBookScreen b = new GrimoireBookScreen(new net.minecraft.world.item.ItemStack(com.ironspellgrimoire.registry.AddonItemRegistry.WEATHER_GRIMOIRE.get()));
            mc.setScreen(b);
            b.showPart("spells:0");
        }, 15, false));
        steps.add(new Step("wea_book_done", () -> mc.setScreen(null), 4, true));

        steps.add(new Step("camera_back", () -> mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON), 4, true));
        // Dante's cloak: front, back, and flared (no HUD in these pictures)
        steps.add(new Step("dante_front", () -> { TrackerMode.set(2); mc.options.hideGui = true; mc.player.setXRot(8); onServer(p -> { p.setXRot(8); UiShotsServer.dante(p, false, false); }); }, 30, true));
        steps.add(new Step("dante_back", () -> onServer(p -> UiShotsServer.dante(p, true, false)), 30, true));
        steps.add(new Step("dante_flare", () -> onServer(p -> UiShotsServer.dante(p, true, true)), 26, true));
        steps.add(new Step("hud_hidden", () -> TrackerMode.set(2), 10, true));
    }

    private static void writeTests(String extra) {
        Minecraft mc = Minecraft.getInstance();
        List<String> res = new ArrayList<>(com.ironspellgrimoire.server.GameTests.results);
        if (extra != null) res.add(extra);
        long f = res.stream().filter(l -> l.startsWith("FAIL") || l.startsWith("CRASH")).count(), w = res.stream().filter(l -> l.startsWith("WARN")).count(),
                ps = res.stream().filter(l -> l.startsWith("PASS")).count();
        List<String> lines = new ArrayList<>();
        lines.add("Iron Spell Grimoire - game tests: " + ps + " passed, " + f + " failed, " + w + " warning(s)");
        lines.addAll(res);
        try { Files.write(mc.gameDirectory.toPath().resolve("isg_gametests_report.txt"), lines, StandardCharsets.UTF_8); } catch (IOException ignored) {}
        phase = 6;
        mc.stop();
    }

    // ------------------------------------------------------------------ skipping what hasn't changed (server/ClassKey)
    private static String stepKey;
    private static com.ironspellgrimoire.server.ClassKey.Cache cacheObj;
    private static final String HARNESS = com.ironspellgrimoire.server.ClassKey.single(UiShots.class) + com.ironspellgrimoire.server.ClassKey.single(LayoutCheck.class);
    private static com.ironspellgrimoire.server.ClassKey.Cache cache() {
        if (cacheObj == null) cacheObj = new com.ironspellgrimoire.server.ClassKey.Cache(Minecraft.getInstance().gameDirectory.toPath().resolve("isg_ui_cache.txt"));
        return cacheObj;
    }

    /** The code each picture depends on (null: always taken - the steps that set things up). */
    private static Class<?>[] rootsFor(String step) {
        if (step.startsWith("book_")) return new Class<?>[]{GrimoireBookScreen.class};
        if (step.startsWith("board_")) return new Class<?>[]{BoardScreen.class, com.ironspellgrimoire.server.Boards.class};
        if (step.startsWith("hud_")) return new Class<?>[]{BountyHud.class, QuestHud.class, WorldBossHud.class, RiftMap.class, BroomHud.class, ModeHudOverlay.class, RaidBanner.class, TrackerMode.class};
        if (step.startsWith("dante_")) return new Class<?>[]{DanteCloakLayer.class, BossRenderer.class};
        Class<?>[] shared = {GrimoireWheel.class, GrimoireBookScreen.class, ManaBarHud.class, ModeHudOverlay.class, FxBase.class, GravityFxClient.class};
        String[][] books = {{"grav_", "Gravity"}, {"snd_", "Sound"}, {"ink_", "Ink"}, {"err_", "Error"}, {"tre_", "Treasure"}, {"chn_", "Chain"}, {"hun_", "Hunter"},
                {"voi_", "Void"}, {"gam_", "Gambler"}, {"tar_", "Tarot"}, {"aby_", "Abyss"}, {"mec_", "Mech"}, {"wea_", "Weather"}};
        for (String[] b : books) if (step.startsWith(b[0])) {   // each book's pictures depend on its own two classes and the shared drawing code
            try {
                Class<?>[] r = java.util.Arrays.copyOf(shared, shared.length + 2);
                r[shared.length] = Class.forName("com.ironspellgrimoire.server." + b[1] + "Fx");
                r[shared.length + 1] = Class.forName("com.ironspellgrimoire.client." + b[1] + "FxClient");
                return r;
            } catch (ClassNotFoundException ex) { return null; }
        }
        if (step.startsWith("blood_") || step.startsWith("ice_")) return new Class<?>[]{com.ironspellgrimoire.server.BloodFx.class, BloodFxClient.class, AuraOutlineLayer.class, IceFxClient.class, BloodHud.class, GrimoireWheel.class, GrimoireBookScreen.class, ManaBarHud.class, ModeHudOverlay.class};
        return switch (step) {
            case "star_forge" -> new Class<?>[]{StarForgeScreen.class, com.ironspellgrimoire.server.StarForge.class};
            case "altar" -> new Class<?>[]{AltarScreen.class, com.ironspellgrimoire.server.AltarMenu.class};
            case "library" -> new Class<?>[]{LibraryScreen.class, com.ironspellgrimoire.server.GrimoireLibrary.class};
            case "waypoints" -> new Class<?>[]{WaypointScreen.class, com.ironspellgrimoire.server.Waypoints.class};
            default -> null;
        };
    }

    private static void finish() {
        Minecraft mc = Minecraft.getInstance();
        Path out = mc.gameDirectory.toPath().resolve("isg_uishots_report.txt");
        List<String> lines = new ArrayList<>();
        lines.add("Iron Spell Grimoire - automatic UI check: " + steps.size() + " steps, " + report.size() + " problem(s)");
        lines.addAll(report);
        cache().save();
        try { Files.write(out, lines, StandardCharsets.UTF_8); } catch (IOException ignored) {}
        mc.stop();
    }

    // ------------------------------------------------------------------ the clock
    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent e) {
        if (!ON || e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ticks++;
        if (!TESTS && ticks > 20 * 60 * 15) { report.add("run | gave up after 15 minutes at step " + index); finish(); return; }
        if (TESTS && ticks > 20 * 60 * 40 && phase < 6) { writeTests("CRASH | run | gave up after 40 minutes at test " + com.ironspellgrimoire.server.GameTests.progress()); return; }
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
            wait = 0;
            if (TESTS) { phase = 5; onServer(com.ironspellgrimoire.server.GameTests::start); return; }
            phase = 2; plan();
            return;
        }
        if (phase == 5) {   // the game tests (server/GameTests) run on the server; wait for them
            var server = mc.getSingleplayerServer();
            if (ticks % 400 == 0) System.out.println("[isg game tests] " + com.ironspellgrimoire.server.GameTests.progress());
            if (server == null || !server.isRunning()) { writeTests("CRASH | run | the game's server stopped at test " + com.ironspellgrimoire.server.GameTests.progress() + " - see latest.log"); return; }
            if (com.ironspellgrimoire.server.GameTests.done) writeTests(null);
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
        if (wait == 0) {
            Class<?>[] roots = rootsFor(s.name());   // passed before and nothing it shows has changed: keep the old pictures
            stepKey = roots == null ? null : com.ironspellgrimoire.server.ClassKey.of(s.name() + "|" + HARNESS, roots);
            if (stepKey != null && cache().unchanged(s.name(), stepKey)) {
                report.add(s.name() + " | SKIP | unchanged since it passed");
                cache().passed(s.name(), stepKey);
                index++;
                return;
            }
            try { s.action().run(); } catch (Throwable t) { report.add(s.name() + " | failed to set up: " + t); }
        }
        if (++wait < s.delay()) return;
        wait = 0;
        if (s.name().equals("setup") || s.name().equals("take_hunt_and_quest")) { index++; return; }
        Screen sc = mc.screen;
        if (!s.hud() && sc == null) { report.add(s.name() + " | the menu didn't open"); index++; return; }
        List<String> issues = LayoutCheck.audit(s.hud() ? null : sc);
        for (String issue : issues) report.add(s.name() + " | " + issue);
        if (issues.isEmpty() && stepKey != null) cache().passed(s.name(), stepKey);
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
        boolean worldOnly = mc.options.hideGui && mc.screen == null;   // nothing on screen changes with the GUI scale: one picture is enough
        if (shot < SHOT_SCALES.length && !worldOnly) { scale(SHOT_SCALES[shot]); waitShot = 4; }
        else { shot = -1; scale(2); index++; }
    }
}
