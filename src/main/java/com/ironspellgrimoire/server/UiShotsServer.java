package com.ironspellgrimoire.server;

import com.ironspellgrimoire.registry.AddonItemRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * The server half of the automatic screenshot run (client/UiShots): sets the test player up with a grimoire and some
 * progress, and opens each of the mod's menus for real, so the screens get real data. Only ever called by that run.
 */
public final class UiShotsServer {
    private UiShotsServer() {}

    /** A grimoire equipped (the one with the most spells), merit stars, Mana Cores, a few past hunts and quests. */
    public static void prepare(ServerPlayer p) {
        GrimoireMastery.Book best = null;
        for (GrimoireMastery.Book b : GrimoireMastery.BOOKS) if (best == null || b.unlocks().size() > best.unlocks().size()) best = b;
        if (best != null) {
            ItemStack book = new ItemStack(best.item().get());
            CuriosApi.getCuriosInventory(p).ifPresent(h -> h.setEquippedCurio("spellbook", 0, book));
            p.getInventory().add(book.copy());
        }
        Bounties.addStars(p, 37);
        p.getInventory().add(new ItemStack(AddonItemRegistry.MANA_CORE.get(), 3));
        p.getInventory().add(new ItemStack(AddonItemRegistry.DEVIL_SHARD.get(), 2));
        Boards.log(p, Boards.BOUNTY, Boards.DONE, Bounties.NAMES[0], 7);
        Boards.log(p, Boards.BOUNTY, Boards.GAVE_UP, Bounties.NAMES[1], 0);
        Boards.log(p, Boards.BOUNTY, Boards.EXPIRED, Bounties.NAMES[Math.min(2, Bounties.NAMES.length - 1)], 0);
        Boards.log(p, Boards.QUEST, Boards.DONE, "Daily: " + Quests.TITLES[0], 2);
        Boards.log(p, Boards.QUEST, Boards.DONE, Quests.TITLES[1], 1);
    }

    public static void board(ServerPlayer p, int kind) { Boards.open(p, kind, p.blockPosition()); }
    /** Takes the first note on a board (so the HUD has a hunt and a quest to track). */
    public static void take(ServerPlayer p, int kind) { Boards.open(p, kind, p.blockPosition()); Boards.pick(p, kind, 0); }
    public static void forge(ServerPlayer p) { StarForge.open(p); }
    public static void altar(ServerPlayer p) { AltarMenu.open(p, p.blockPosition()); }
    public static void library(ServerPlayer p) { GrimoireLibrary.open(p); }
    public static void waypoints(ServerPlayer p) { Waypoints.open(p); }
    public static ItemStack book(ServerPlayer p) {
        for (ItemStack s : p.getInventory().items) if (GrimoireMastery.bookOf(s) != null) return s;
        return ItemStack.EMPTY;
    }
    public static BlockPos at(ServerPlayer p) { return p.blockPosition(); }

    /** The Blood Grimoire's Scarlet Moon, on the test player (for the third-person pictures). */
    public static void scarletMoon(ServerPlayer p) {
        ItemStack book = new ItemStack(AddonItemRegistry.BLOOD_GRIMOIRE.get());
        CuriosApi.getCuriosInventory(p).ifPresent(h -> h.setEquippedCurio("spellbook", 0, book));
        BloodFx.showGauge(p, BloodFx.MAX);
        BloodFx.scarletMoon(p, 10f);
    }
    /** The Gravity or Sound Grimoire equipped, and one of its spells cast where the player looks ("target" puts a still husk 6 ahead). */
    public static void element(ServerPlayer p, String which) {
        Moves.ends(p, BloodFx.MODE);
        String w = which.substring(2);
        ItemStack book = new ItemStack(switch (which.charAt(0)) {
            case 'g' -> AddonItemRegistry.GRAVITY_GRIMOIRE.get();
            case 'i' -> AddonItemRegistry.INK_GRIMOIRE.get();
            case 'e' -> AddonItemRegistry.ERROR_GRIMOIRE.get();
            case 't' -> AddonItemRegistry.TREASURE_GRIMOIRE.get();
            case 'c' -> AddonItemRegistry.CHAIN_GRIMOIRE.get();
            case 'h' -> AddonItemRegistry.HUNTER_GRIMOIRE.get();
            case 'v' -> AddonItemRegistry.VOID_GRIMOIRE.get();
            case 'b' -> AddonItemRegistry.GAMBLER_GRIMOIRE.get();
            case 'r' -> AddonItemRegistry.TAROT_GRIMOIRE.get();
            case 'a' -> AddonItemRegistry.ABYSS_GRIMOIRE.get();
            case 'm' -> AddonItemRegistry.MECH_GRIMOIRE.get();
            case 'w' -> AddonItemRegistry.WEATHER_GRIMOIRE.get();
            default -> AddonItemRegistry.SOUND_GRIMOIRE.get();
        });
        net.minecraft.nbt.CompoundTag mt = new net.minecraft.nbt.CompoundTag(); mt.putInt("xp", 10_000_000);
        book.getOrCreateTag().put(GrimoireMastery.masteryTag(), mt);
        ItemStack cur = io.redspace.ironsspellbooks.api.util.Utils.getPlayerSpellbookStack(p);
        if (cur == null || !cur.is(book.getItem())) CuriosApi.getCuriosInventory(p).ifPresent(h -> h.setEquippedCurio("spellbook", 0, book));
        io.redspace.ironsspellbooks.api.magic.MagicData.getPlayerMagicData(p).setMana(2000);
        switch (w) {
            case "target" -> {
                for (var old : p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.monster.Husk.class, p.getBoundingBox().inflate(40))) old.discard();
                var look = p.getLookAngle().multiply(1, 0, 1).normalize();
                var z = net.minecraft.world.entity.EntityType.HUSK.create(p.serverLevel());
                if (z != null) {
                    var at = p.position().add(look.scale(7));
                    z.moveTo(at.x, p.getY(), at.z, 0, 0); z.setNoAi(true);
                    z.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000); z.setHealth(1000);
                    p.serverLevel().addFreshEntity(z);
                }
            }
            case "gravity_bullet" -> GravityFx.gravityBullet(p, 10f);
            case "crushing_field" -> GravityFx.crushingField(p, 10f);
            case "gravity_well" -> GravityFx.gravityWell(p, 10f);
            case "gravity_lift" -> GravityFx.gravityLift(p, 10f);
            case "orbiting_mass" -> GravityFx.orbitingMass(p, 10f);
            case "repulse" -> GravityFx.repulse(p, 10f);
            case "mass_domain" -> GravityFx.massDomain(p, 10f);
            case "starfall_collapse" -> GravityFx.starfallCollapse(p, 10f);
            case "sonic_pulse" -> SoundFx.sonicPulse(p, 10f);
            case "sonic_lance" -> SoundFx.sonicLance(p, 10f);
            case "sound_barrier" -> SoundFx.soundBarrier(p, 10f);
            case "silence_field" -> SoundFx.silenceField(p, 10f);
            case "echo_chamber" -> SoundFx.echoChamber(p, 10f);
            case "tuning_strike" -> SoundFx.tuningStrike(p, 10f);
            case "thunderclap" -> SoundFx.thunderclap(p, 10f);
            case "crescendo" -> SoundFx.crescendoMode(p, 10f);
            case "final_chord" -> SoundFx.finalChord(p, 10f);
            case "brush_stroke" -> InkFx.brushStroke(p, 10f);
            case "ink_splash" -> InkFx.inkSplash(p, 10f);
            case "ink_serpent" -> InkFx.inkSerpent(p, 10f);
            case "seal_script" -> InkFx.sealScript(p, 10f);
            case "ink_beasts" -> InkFx.inkBeasts(p, 10f);
            case "ink_wave" -> InkFx.inkWave(p, 10f);
            case "masterpiece" -> InkFx.masterpieceMode(p, 10f);
            case "grand_calligraphy" -> InkFx.grandCalligraphy(p, 10f);
            case "glitch_bolt" -> ErrorFx.glitchBolt(p, 10f);
            case "corrupted_threads" -> ErrorFx.corruptedThreads(p, 10f);
            case "lag_spike" -> ErrorFx.lagSpike(p, 10f);
            case "crash_zone" -> ErrorFx.crashZone(p, 10f);
            case "error_windows" -> ErrorFx.errorWindows(p, 10f);
            case "blue_screen" -> ErrorFx.blueScreen(p, 10f);
            case "fatal_error" -> ErrorFx.fatalError(p, 10f);
            case "system_crash" -> ErrorFx.systemCrash(p, 10f);
            case "vault_volley" -> TreasureFx.vaultVolley(p, 10f);
            case "gilded_spear" -> TreasureFx.gildedSpear(p, 10f);
            case "vault_chains" -> TreasureFx.vaultChains(p, 10f);
            case "blade_wall" -> TreasureFx.bladeWall(p, 10f);
            case "blade_rain" -> TreasureFx.bladeRain(p, 10f);
            case "treasure_barrage" -> TreasureFx.treasureBarrage(p, 10f);
            case "kings_vault" -> TreasureFx.kingsVault(p, 10f);
            case "vault_breaker" -> TreasureFx.vaultBreaker(p, 10f);
            case "chain_lash" -> ChainFx.chainLash(p, 10f);
            case "binding_chain" -> ChainFx.bindingChain(p, 10f);
            case "dowsing_chain" -> ChainFx.dowsingChain(p, 10f);
            case "healing_chain" -> ChainFx.healingChain(p, 10f);
            case "judgment_chain" -> ChainFx.judgmentChain(p, 10f);
            case "stealing_chain" -> ChainFx.stealingChain(p, 10f);
            case "chain_net" -> ChainFx.chainNet(p, 10f);
            case "anchor_chain" -> ChainFx.anchorChain(p, 10f);
            case "restriction" -> ChainFx.restriction(p, 10f);
            case "thousand_chains" -> ChainFx.thousandChains(p, 10f);
            case "saw_cleave" -> HunterFx.sawCleave(p, 10f);
            case "trick_switch" -> HunterFx.trickSwitch(p, 10f);
            case "silver_shot" -> HunterFx.silverShot(p, 10f);
            case "quickstep" -> HunterFx.quickstep(p, 10f);
            case "visceral_strike" -> HunterFx.visceralStrike(p, 10f);
            case "hunters_mark" -> HunterFx.huntersMark(p, 10f);
            case "rending_spin" -> HunterFx.rendingSpin(p, 10f);
            case "blood_volley" -> HunterFx.bloodVolley(p, 10f);
            case "blood_moon_hunt" -> HunterFx.bloodMoonHunt(p, 10f);
            case "nightmare_cleave" -> HunterFx.nightmareCleave(p, 10f);
            case "erase" -> VoidFx.erase(p, 10f);
            case "null_step" -> VoidFx.nullStep(p, 10f);
            case "unmake" -> VoidFx.unmakeSpell(p, 10f);
            case "nullify" -> VoidFx.nullify(p, 10f);
            case "seam" -> VoidFx.seamSpell(p, 10f);
            case "absence" -> VoidFx.absenceSpell(p, 10f);
            case "void_lance" -> VoidFx.voidLance(p, 10f);
            case "delete" -> VoidFx.deleteSpell(p, 10f);
            case "emptiness" -> VoidFx.emptinessMode(p, 10f);
            case "origin_zero" -> VoidFx.originZero(p, 10f);
            case "coin_toss" -> GamblerFx.coinToss(p, 10f);
            case "double_or_nothing" -> GamblerFx.doubleOrNothing(p, 10f);
            case "roulette" -> GamblerFx.roulette(p, 10f);
            case "chip_shot" -> GamblerFx.chipShot(p, 10f);
            case "lucky_seven" -> GamblerFx.luckySeven(p, 10f);
            case "slot_machine" -> GamblerFx.slotMachine(p, 10f);
            case "loaded_dice" -> GamblerFx.loadedDice(p, 10f);
            case "insurance" -> GamblerFx.insurance(p, 10f);
            case "all_in" -> GamblerFx.allInMode(p, 10f);
            case "jackpot" -> GamblerFx.jackpot(p, 10f);
            case "draw_card" -> TarotFx.drawCard(p, 10f);
            case "the_magician" -> TarotFx.magicianCard(p, 10f);
            case "the_tower" -> TarotFx.towerCard(p, 10f);
            case "the_star" -> TarotFx.starCard(p, 10f);
            case "death_card" -> TarotFx.deathCard(p, 10f);
            case "wheel_of_fortune" -> TarotFx.wheelCard(p, 10f);
            case "the_hanged_man" -> TarotFx.hangedCard(p, 10f);
            case "three_card_spread" -> TarotFx.threeCardSpread(p, 10f);
            case "the_world" -> TarotFx.worldMode(p, 10f);
            case "judgement" -> TarotFx.judgementCard(p, 10f);
            case "crushing_pressure" -> AbyssFx.crushingPressure(p, 10f);
            case "anglers_lure" -> AbyssFx.anglersLure(p, 10f);
            case "abyssal_grasp" -> AbyssFx.abyssalGrasp(p, 10f);
            case "undertow" -> AbyssFx.undertowSpell(p, 10f);
            case "abyssal_dive" -> AbyssFx.abyssalDive(p, 10f);
            case "depth_charge" -> AbyssFx.depthCharge(p, 10f);
            case "leviathan" -> AbyssFx.leviathanSpell(p, 10f);
            case "hadal_weight" -> AbyssFx.hadalWeight(p, 10f);
            case "bioluminescence" -> AbyssFx.bioluminescence(p, 10f);
            case "the_trench" -> AbyssFx.theTrench(p, 10f);
            case "rivet_barrage" -> MechFx.rivetBarrage(p, 10f);
            case "thruster_dash" -> MechFx.thrusterDash(p, 10f);
            case "rocket_fist" -> MechFx.rocketFist(p, 10f);
            case "piston_stomp" -> MechFx.pistonStomp(p, 10f);
            case "bulwark_plating" -> MechFx.bulwarkPlating(p, 10f);
            case "missile_swarm" -> MechFx.missileSwarm(p, 10f);
            case "overclock" -> MechFx.overclockSpell(p, 10f);
            case "rail_cannon" -> MechFx.railCannon(p, 10f);
            case "pilot" -> MechFx.pilotMode(p, 10f);
            case "titan_buster" -> MechFx.titanBuster(p, 10f);
            case "sunbeam" -> WeatherFx.sunbeamSpell(p, 10f);
            case "downpour" -> WeatherFx.downpour(p, 10f);
            case "thunder_strike" -> WeatherFx.thunderclap(p, 10f);
            case "hailstorm" -> WeatherFx.hailstorm(p, 10f);
            case "gale" -> WeatherFx.galeSpell(p, 10f);
            case "fog_bank" -> WeatherFx.fogBank(p, 10f);
            case "cyclone" -> WeatherFx.cycloneSpell(p, 10f);
            case "rainbow" -> WeatherFx.rainbowSpell(p, 10f);
            case "eye_of_the_storm" -> WeatherFx.eyeOfTheStorm(p, 10f);
            case "cataclysm" -> WeatherFx.cataclysmSpell(p, 10f);
            case "end" -> { Moves.ends(p, GravityFx.MODE); Moves.ends(p, SoundFx.MODE); Moves.ends(p, SoundFx.BARRIER); Moves.ends(p, InkFx.MODE); Moves.ends(p, ErrorFx.MODE); Moves.ends(p, TreasureFx.MODE); Moves.ends(p, ChainFx.MODE); Moves.ends(p, HunterFx.MODE); Moves.ends(p, VoidFx.MODE); Moves.ends(p, VoidFx.ABSENCE); Moves.ends(p, GamblerFx.MODE); Moves.ends(p, GamblerFx.INSURED); Moves.ends(p, TarotFx.MODE); Moves.ends(p, TarotFx.READ); Moves.ends(p, AbyssFx.MODE); Moves.ends(p, MechFx.MODE); Moves.ends(p, MechFx.PLATING); Moves.ends(p, WeatherFx.MODE); Moves.ends(p, WeatherFx.FOG_BANK); }
            default -> {}
        }
    }

    /** One Blood spell, cast where the player looks. */
    public static void bloodSpell(ServerPlayer p, String which) {
        switch (which) {
            case "thorned_veins" -> BloodFx.thornedVeins(p, 10f);
            case "red_tide" -> BloodFx.redTide(p, 10f);
            case "blood_pact" -> BloodFx.bloodPact(p, 10f);
            case "gauge" -> {
                ItemStack book = new ItemStack(AddonItemRegistry.BLOOD_GRIMOIRE.get());
                CuriosApi.getCuriosInventory(p).ifPresent(h -> h.setEquippedCurio("spellbook", 0, book));
                BloodFx.showGauge(p, 68f);
            }
            case "moonfall" -> { p.setXRot(20); BloodFx.scarletMoon(p, 10f); }
            case "overflow" -> BloodFx.showGauge(p, 132f);
            case "throne" -> IceFx.iceThrone(p, 10f);
            case "hemorrhage" -> BloodFx.hemorrhage(p, 10f);
            case "sanguine_volley" -> BloodFx.sanguineVolley(p, 10f);
            case "crimson_bulwark" -> BloodFx.crimsonBulwark(p, 10f);
            case "prison" -> {   // a still zombie straight ahead to hold
                var look = p.getLookAngle().multiply(1, 0, 1).normalize();
                var z = net.minecraft.world.entity.EntityType.HUSK.create(p.serverLevel());
                if (z != null) {
                    var at = p.position().add(look.scale(6));
                    z.moveTo(at.x, p.getY(), at.z, 0, 0); z.setNoAi(true);
                    p.serverLevel().addFreshEntity(z);
                    p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, z.position().add(0, 1, 0));
                }
                BloodFx.transfusion(p, 10f);
            }
            case "master" -> {   // the equipped Blood Grimoire mastered, with a path or two chosen (the book pictures)
                ItemStack bk = io.redspace.ironsspellbooks.api.util.Utils.getPlayerSpellbookStack(p);
                if (bk == null) return;
                net.minecraft.nbt.CompoundTag mt = new net.minecraft.nbt.CompoundTag(); mt.putInt("xp", 10_000_000);
                bk.getOrCreateTag().put(GrimoireMastery.masteryTag(), mt);
                BloodPaths.choose(p, "crimson_edge", 1);
                BloodFx.setMoonCount(p, 25);   // and an awakened Moon for the mode pictures
            }
            case "exsanguinate" -> {   // two still zombies to draw from
                p.server.setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);
                var look = p.getLookAngle().multiply(1, 0, 1).normalize();
                var right = new net.minecraft.world.phys.Vec3(-look.z, 0, look.x);
                for (int i = -1; i <= 1; i += 2) {
                    var z = net.minecraft.world.entity.EntityType.ZOMBIE.create(p.serverLevel());
                    if (z == null) continue;
                    var at = p.position().add(look.scale(3.5)).add(right.scale(i * 1.6));
                    z.moveTo(at.x, p.getY(), at.z, 0, 0);
                    z.setNoAi(true);
                    z.setHealth(z.getMaxHealth());
                    p.serverLevel().addFreshEntity(z);
                }
                BloodFx.exsanguinate(p, 4f);
            }
            default -> BloodFx.crimsonEdge(p, 10f);
        }
    }

    /** Dante, standing four blocks in front of the camera (facing it, or turned away), for a look at his cloak. */
    public static void dante(ServerPlayer p, boolean back, boolean flare) {
        p.server.setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);   // Peaceful would delete him at once
        for (var e : p.serverLevel().getEntitiesOfClass(com.ironspellgrimoire.entity.Dante.class, p.getBoundingBox().inflate(30))) e.discard();
        var d = com.ironspellgrimoire.registry.AddonEntities.DANTE.get().create(p.serverLevel());
        if (d == null) return;
        var look = p.getLookAngle().multiply(1, 0, 1).normalize();
        var at = p.position().add(look.scale(4.2));
        float face = (float) Math.toDegrees(Math.atan2(look.x, -look.z)) + (back ? 180 : 0);
        d.moveTo(at.x, p.getY(), at.z, face, 0);
        d.setYHeadRot(face); d.yBodyRot = face;
        d.setNoAi(true);
        p.serverLevel().addFreshEntity(d);
        if (flare) d.setPose(BossKit.P_RAISE);
    }
}
