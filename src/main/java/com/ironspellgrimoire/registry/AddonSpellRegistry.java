package com.ironspellgrimoire.registry;

import com.ironspellgrimoire.server.DarkFx;
import com.ironspellgrimoire.server.WindFx;
import com.ironspellgrimoire.server.MercuryFx;
import com.ironspellgrimoire.server.FireFx;
import com.ironspellgrimoire.server.LightningFx;
import com.ironspellgrimoire.server.HellfireFx;
import com.ironspellgrimoire.server.BoneFx;
import com.ironspellgrimoire.spells.BoneSpell;
import com.ironspellgrimoire.spells.HellfireSpell;
import com.ironspellgrimoire.spells.LightningSpell;
import com.ironspellgrimoire.spells.FireSpell;
import com.ironspellgrimoire.spells.MercurySpell;
import com.ironspellgrimoire.spells.WindSpell;
import com.ironspellgrimoire.server.DevilFx;
import com.ironspellgrimoire.spells.DevilSpell;
import com.ironspellgrimoire.spells.GrimoireSpell.Need;
import com.ironspellgrimoire.server.SteelFx;
import com.ironspellgrimoire.spells.SteelSpell;
import com.ironspellgrimoire.spells.AntiMagicSpell;
import com.ironspellgrimoire.spells.BorrowedSlashSpell;
import com.ironspellgrimoire.spells.DarkSpell;
import com.ironspellgrimoire.spells.DevilUnionSpell;
import com.ironspellgrimoire.spells.DwellerSlashSpell;
import com.ironspellgrimoire.spells.InstinctGuardSpell;
import com.ironspellgrimoire.spells.KiSenseSpell;
import com.ironspellgrimoire.spells.PiercingDashSpell;
import com.ironspellgrimoire.spells.SkySplitterSpell;
import com.ironspellgrimoire.spells.SummonGreatswordSpell;
import com.ironspellgrimoire.spells.ZettenSpell;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.SpellAnimations;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;

public class AddonSpellRegistry {
    public static final DeferredRegister<AbstractSpell> SPELLS = DeferredRegister.create(SpellRegistry.SPELL_REGISTRY_KEY, "iron_spell_grimoire");

    // ------------------------------------------------------------------ Black Grimoire (Asta)
    public static final RegistryObject<AbstractSpell> SUMMON_GREATSWORD = registerSpell(new SummonGreatswordSpell());
    public static final RegistryObject<AbstractSpell> PIERCING_DASH = registerSpell(new PiercingDashSpell());
    public static final RegistryObject<AbstractSpell> ZETTEN = registerSpell(new ZettenSpell());
    public static final RegistryObject<AbstractSpell> SKY_SPLITTER = registerSpell(new SkySplitterSpell());
    public static final RegistryObject<AbstractSpell> ANTI_MAGIC = registerSpell(new AntiMagicSpell());
    public static final RegistryObject<AbstractSpell> KI_SENSE = registerSpell(new KiSenseSpell());
    public static final RegistryObject<AbstractSpell> INSTINCT_GUARD = registerSpell(new InstinctGuardSpell());
    public static final RegistryObject<AbstractSpell> DWELLER_SLASH = registerSpell(new DwellerSlashSpell());
    public static final RegistryObject<AbstractSpell> BORROWED_SLASH = registerSpell(new BorrowedSlashSpell());
    public static final RegistryObject<AbstractSpell> DEVIL_UNION = registerSpell(new DevilUnionSpell());

    // ------------------------------------------------------------------ Black Grimoire: Devil Union versions
    // These replace the normal spells inside the grimoire while Devil Union lasts (server/DevilBook).
    public static final RegistryObject<AbstractSpell> DEVIL_SUMMON = registerSpell(new SummonGreatswordSpell("devil_summon", true));
    public static final RegistryObject<AbstractSpell> DEVIL_PIERCING_DASH = registerSpell(new PiercingDashSpell("devil_piercing_dash", true));
    public static final RegistryObject<AbstractSpell> DEVIL_ZETTEN = registerSpell(new DevilSpell("devil_zetten", 6, 18, Need.ANY_MAIN, DevilFx::zetten));
    public static final RegistryObject<AbstractSpell> DEVIL_SKY_SPLITTER = registerSpell(new DevilSpell("devil_sky_splitter", 14, 26, Need.ANY_MAIN, DevilFx::skySplitter));
    public static final RegistryObject<AbstractSpell> DEVIL_ANTI_MAGIC = registerSpell(new DevilSpell("devil_anti_magic", 20, 14, Need.ANY_MAIN, DevilFx::antiMagic));
    public static final RegistryObject<AbstractSpell> DEVIL_KI_SENSE = registerSpell(new DevilSpell("devil_ki_sense", 25, 0, Need.NONE, DevilFx::kiSense));
    /** Devil Union: Black Meteorite - takes Ki Sense's slot during the union (cutscene move, see MeteorFx). */
    public static final RegistryObject<AbstractSpell> DEVIL_BLACK_METEORITE = registerSpell(new DevilSpell("devil_black_meteorite", 40, 45, Need.ANY_MAIN, com.ironspellgrimoire.server.MeteorFx::cast));
    public static final RegistryObject<AbstractSpell> DEVIL_INSTINCT_GUARD = registerSpell(new DevilSpell("devil_instinct_guard", 12, 0, Need.NONE, DevilFx::instinct));
    /** Black Divider: takes Twin Rift's slot (Sky Splitter) during the union. */
    public static final RegistryObject<AbstractSpell> DEVIL_BLACK_DIVIDER = registerSpell(new DevilSpell("devil_black_divider", 10, 32, Need.ANY_MAIN, DevilFx::blackDivider));
    /** Black Hurricane: takes Devil Instinct's slot (Instinct Guard) during the union. */
    public static final RegistryObject<AbstractSpell> DEVIL_BLACK_HURRICANE = registerSpell(new DevilSpell("devil_black_hurricane", 16, 7, Need.ANY_MAIN, DevilFx::blackHurricane));
    public static final RegistryObject<AbstractSpell> DEVIL_UNION_RELEASE = registerSpell(new DevilSpell("devil_union_release", 5, 30, Need.NONE, DevilFx::release));
    public static final RegistryObject<AbstractSpell> DEVIL_DWELLER_SLASH = registerSpell(new DevilSpell("devil_dweller_slash", 7, 18, Need.DWELLER_ANY, DevilFx::dwellerSlash));
    public static final RegistryObject<AbstractSpell> DEVIL_BORROWED_SLASH = registerSpell(new DevilSpell("devil_borrowed_slash", 12, 18, Need.DWELLER_MAIN, DevilFx::borrowedSlash));

    /** Normal spell -> its Devil Union version, slot for slot. */
    private record DevilPair(RegistryObject<AbstractSpell> normal, RegistryObject<AbstractSpell> devil) {}
    private static final List<DevilPair> DEVIL_PAIRS = List.of(
            new DevilPair(SUMMON_GREATSWORD, DEVIL_SUMMON), new DevilPair(PIERCING_DASH, DEVIL_PIERCING_DASH),
            new DevilPair(ZETTEN, DEVIL_ZETTEN), new DevilPair(SKY_SPLITTER, DEVIL_BLACK_DIVIDER),
            new DevilPair(ANTI_MAGIC, DEVIL_ANTI_MAGIC), new DevilPair(KI_SENSE, DEVIL_BLACK_METEORITE),
            new DevilPair(INSTINCT_GUARD, DEVIL_BLACK_HURRICANE), new DevilPair(DEVIL_UNION, DEVIL_UNION_RELEASE),
            new DevilPair(DWELLER_SLASH, DEVIL_DWELLER_SLASH), new DevilPair(BORROWED_SLASH, DEVIL_BORROWED_SLASH));

    /** The devil version of a Black Grimoire spell, or null if it has none. */
    public static AbstractSpell devilVersionOf(AbstractSpell spell) {
        for (DevilPair pair : DEVIL_PAIRS) if (pair.normal().get() == spell) return pair.devil().get();
        return null;
    }

    /** The normal version of a devil spell, or null if it isn't one. */
    public static AbstractSpell normalVersionOf(AbstractSpell spell) {
        for (DevilPair pair : DEVIL_PAIRS) if (pair.devil().get() == spell) return pair.normal().get();
        // retired devil moves still sitting in a book from before the rework go back to their normal spell too
        if (spell == DEVIL_SKY_SPLITTER.get()) return SKY_SPLITTER.get();
        if (spell == DEVIL_INSTINCT_GUARD.get()) return INSTINCT_GUARD.get();
        return null;
    }

    // ------------------------------------------------------------------ Dark Grimoire (Yami Sukehiro's Dark Magic)
    // katana = needs the Dark Katana in the main hand.  animation = the player pose played when it's cast.
    //                                                                                   id                          cd mana power katana  animation
    public static final RegistryObject<AbstractSpell> BLACK_BLADE = registerSpell(new DarkSpell("black_blade", 30, 25, 0, true, DarkSpell.anim("iron_spell_grimoire:dark_black_blade"), DarkFx::blackBlade));
    public static final RegistryObject<AbstractSpell> DIMENSION_SLASH = registerSpell(new DarkSpell("dimension_slash", 12, 50, 26, true, DarkSpell.anim("iron_spell_grimoire:dark_dimension_slash"), DarkFx::dimensionSlash));
    public static final RegistryObject<AbstractSpell> AVIDYA_SLASH = registerSpell(new DarkSpell("avidya_slash", 8, 30, 18, true, DarkSpell.anim("iron_spell_grimoire:dark_avidya"), DarkFx::avidyaSlash));
    public static final RegistryObject<AbstractSpell> BLACK_COCOON = registerSpell(new DarkSpell("black_cocoon", 20, 50, 14, false, DarkSpell.anim("iron_spell_grimoire:dark_black_cocoon"), DarkFx::blackCocoon));
    public static final RegistryObject<AbstractSpell> DEATH_THRUST = registerSpell(new DarkSpell("death_thrust", 10, 40, 22, true, DarkSpell.anim("iron_spell_grimoire:dark_death_thrust"), DarkFx::deathThrust));
    public static final RegistryObject<AbstractSpell> BLACK_MOON = registerSpell(new DarkSpell("black_moon", 20, 45, 16, false, DarkSpell.anim("iron_spell_grimoire:dark_black_moon"), DarkFx::blackMoon));
    public static final RegistryObject<AbstractSpell> BLACK_HOLE = registerSpell(new DarkSpell("black_hole", 24, 65, 10, false, DarkSpell.anim("iron_spell_grimoire:dark_black_hole"), DarkFx::blackHole));
    public static final RegistryObject<AbstractSpell> MANA_ZONE_DIMENSION_SLASH = registerSpell(new DarkSpell("mana_zone_dimension_slash", 45, 100, 10, false, DarkSpell.anim("iron_spell_grimoire:dark_mana_zone"), DarkFx::manaZone));
    public static final RegistryObject<AbstractSpell> KI_FORESIGHT = registerSpell(new DarkSpell("ki_foresight", 18, 20, 0, false, DarkSpell.anim("iron_spell_grimoire:dark_ki_read"), DarkFx::foresight));
    public static final RegistryObject<AbstractSpell> EVENT_HORIZON = registerSpell(new DarkSpell("event_horizon", 90, 195, 60, true, DarkSpell.anim("iron_spell_grimoire:dark_event_horizon"), DarkFx::eventHorizon));

    // ------------------------------------------------------------------ Steel Grimoire (Klaus Lunettes' Steel Magic)
    //                                                                                          id                       cd mana power  animation
    public static final RegistryObject<AbstractSpell> VIOLENT_ROTATING_LANCE = registerSpell(new SteelSpell("violent_rotating_lance", 8, 30, 16, false, SteelSpell.anim("irons_spellbooks:instant_projectile"), SteelFx::violentLance));
    public static final RegistryObject<AbstractSpell> BLAZING_SPIRAL_LANCE = registerSpell(new SteelSpell("blazing_spiral_lance", 14, 45, 28, false, SteelSpell.anim("iron_spell_grimoire:klaus_lance_charge"), SteelFx::spiralLance));
    public static final RegistryObject<AbstractSpell> STEEL_CASTLE_WALL = registerSpell(new SteelSpell("steel_castle_wall", 16, 45, 14, false, SteelSpell.anim("irons_spellbooks:touch_ground"), SteelFx::armoredWall));
    public static final RegistryObject<AbstractSpell> WHITE_STEEL_HORSE_CART = registerSpell(new SteelSpell("white_steel_horse_cart", 16, 40, 14, false, SteelSpell.anim("iron_spell_grimoire:klaus_cart_ride"), SteelFx::horseCart));
    public static final RegistryObject<AbstractSpell> THUNDER_ARROW = registerSpell(new SteelSpell("thunder_arrow", 30, 80, 34, false, SteelSpell.anim("iron_spell_grimoire:klaus_railgun"), SteelFx::thunderArrow));
    public static final RegistryObject<AbstractSpell> ACIER_SILVA = registerSpell(new SteelSpell("acier_silva", 20, 65, 14, false, SteelSpell.anim("irons_spellbooks:stomp"), SteelFx::acierSilva));
    public static final RegistryObject<AbstractSpell> STEEL_PRISON = registerSpell(new SteelSpell("steel_prison", 22, 45, 18, false, SteelSpell.anim("iron_spell_grimoire:klaus_clench"), SteelFx::steelPrison));
    public static final RegistryObject<AbstractSpell> KNIGHTS_ARMOR = registerSpell(new SteelSpell("knights_armor", 120, 40, 0, false, SteelSpell.anim("iron_spell_grimoire:klaus_glasses_push"), SteelFx::knightsArmor));

    /** The eight Steel Grimoire spells, in the order they fill the book. */
    public static final List<RegistryObject<AbstractSpell>> STEEL_SPELLS = List.of(
            VIOLENT_ROTATING_LANCE, BLAZING_SPIRAL_LANCE, STEEL_CASTLE_WALL, WHITE_STEEL_HORSE_CART,
            THUNDER_ARROW, ACIER_SILVA, STEEL_PRISON, KNIGHTS_ARMOR);

    // ------------------------------------------------------------------ Wind Grimoire (Yuno Grinberryall's Wind Magic)
    //                                                                                id                       cd mana power  animation
    public static final RegistryObject<AbstractSpell> WIND_BLADE_SHOWER = registerSpell(new WindSpell("wind_blade_shower", 6, 25, 9, WindSpell.anim("iron_spell_grimoire:wind_blade_shower"), WindFx::windBladeShower));
    public static final RegistryObject<AbstractSpell> TORNADO_FANG = registerSpell(new WindSpell("tornado_fang", 14, 45, 14, WindSpell.anim("iron_spell_grimoire:wind_tornado_fang"), WindFx::tornadoFang));
    public static final RegistryObject<AbstractSpell> SYLPHS_BREATH = registerSpell(new WindSpell("sylphs_breath", 8, 25, 8, WindSpell.anim("iron_spell_grimoire:wind_sylphs_breath"), WindFx::sylphsBreath));
    public static final RegistryObject<AbstractSpell> GALE_WHITE_BOW = registerSpell(new WindSpell("gale_white_bow", 7, 30, 14, WindSpell.anim("iron_spell_grimoire:wind_gale_bow"), WindFx::galeWhiteBow));
    public static final RegistryObject<AbstractSpell> HUSHED_DANCE = registerSpell(new WindSpell("hushed_dance", 18, 20, 0, WindSpell.anim("iron_spell_grimoire:wind_hushed_dance"), WindFx::hushedDance));
    public static final RegistryObject<AbstractSpell> SPIRIT_OF_ZEPHYR = registerSpell(new WindSpell("spirit_of_zephyr", 10, 40, 20, WindSpell.anim("iron_spell_grimoire:wind_zephyr"), WindFx::spiritOfZephyr));
    public static final RegistryObject<AbstractSpell> SPIRIT_OF_BOREAS = registerSpell(new WindSpell("spirit_of_boreas", 14, 45, 16, WindSpell.anim("iron_spell_grimoire:wind_boreas"), WindFx::spiritOfBoreas));
    public static final RegistryObject<AbstractSpell> SPIRIT_OF_EUROS = registerSpell(new WindSpell("spirit_of_euros", 20, 60, 34, WindSpell.anim("iron_spell_grimoire:wind_euros"), WindFx::spiritOfEuros));
    public static final RegistryObject<AbstractSpell> SPIRIT_DIVE = registerSpell(new WindSpell("spirit_dive", 90, 100, 0, WindSpell.anim("iron_spell_grimoire:wind_spirit_dive"), WindFx::spiritDive));
    public static final RegistryObject<AbstractSpell> MANA_ZONE = registerSpell(new WindSpell("mana_zone", 40, 90, 14, WindSpell.anim("iron_spell_grimoire:dark_mana_zone"), WindFx::manaZone));
    public static final RegistryObject<AbstractSpell> SPIRIT_STORM = registerSpell(new WindSpell("spirit_storm", 60, 130, 40, WindSpell.anim("iron_spell_grimoire:wind_spirit_storm"), WindFx::spiritStorm));

    /** The ten Wind Grimoire spells, in the order they fill the book. */
    public static final List<RegistryObject<AbstractSpell>> WIND_SPELLS = List.of(
            WIND_BLADE_SHOWER, TORNADO_FANG, SYLPHS_BREATH, GALE_WHITE_BOW, HUSHED_DANCE,
            SPIRIT_OF_ZEPHYR, SPIRIT_OF_BOREAS, SPIRIT_OF_EUROS, MANA_ZONE, SPIRIT_DIVE, SPIRIT_STORM);

    // ------------------------------------------------------------------ Mercury Grimoire (Nozel Silva's Mercury Magic)
    //                                                                                id                          cd mana power  animation
    public static final RegistryObject<AbstractSpell> MERCURY_BULLETS = registerSpell(new MercurySpell("mercury_bullets", 5, 20, 10, MercurySpell.anim("iron_spell_grimoire:mercury_bullets"), MercuryFx::mercuryBullets));
    public static final RegistryObject<AbstractSpell> MERCURY_SPEARS = registerSpell(new MercurySpell("mercury_spears", 9, 30, 16, MercurySpell.anim("iron_spell_grimoire:mercury_spears"), MercuryFx::mercurySpears));
    public static final RegistryObject<AbstractSpell> SILVER_RAIN = registerSpell(new MercurySpell("silver_rain", 16, 45, 14, MercurySpell.anim("iron_spell_grimoire:mercury_silver_rain"), MercuryFx::silverRain));
    public static final RegistryObject<AbstractSpell> STORM_OF_BLADES = registerSpell(new MercurySpell("storm_of_blades", 12, 40, 18, MercurySpell.anim("iron_spell_grimoire:mercury_storm_of_blades"), MercuryFx::stormOfBlades));
    public static final RegistryObject<AbstractSpell> MERCURY_EAGLE = registerSpell(new MercurySpell("mercury_eagle", 14, 40, 24, MercurySpell.anim("iron_spell_grimoire:mercury_eagle"), MercuryFx::mercuryEagle));
    public static final RegistryObject<AbstractSpell> SILVER_MIRROR = registerSpell(new MercurySpell("silver_mirror", 16, 25, 0, MercurySpell.anim("iron_spell_grimoire:mercury_mirror"), MercuryFx::silverMirror));
    public static final RegistryObject<AbstractSpell> MERCURY_SPHERE = registerSpell(new MercurySpell("mercury_sphere", 24, 45, 0, MercurySpell.anim("iron_spell_grimoire:mercury_sphere"), MercuryFx::mercurySphere));
    public static final RegistryObject<AbstractSpell> REGAL_SPEAR = registerSpell(new MercurySpell("regal_spear", 22, 65, 34, MercurySpell.anim("iron_spell_grimoire:mercury_regal_spear"), MercuryFx::regalSpear));
    public static final RegistryObject<AbstractSpell> SILVER_GUARDIAN = registerSpell(new MercurySpell("silver_guardian", 60, 60, 0, MercurySpell.anim("iron_spell_grimoire:mercury_guardian"), MercuryFx::silverGuardian));
    public static final RegistryObject<AbstractSpell> MERCURY_CAGE = registerSpell(new MercurySpell("mercury_cage", 40, 90, 30, MercurySpell.anim("iron_spell_grimoire:klaus_clench"), MercuryFx::mercuryCage));

    /** The ten Mercury Grimoire spells, in the order they fill the book. */
    public static final List<RegistryObject<AbstractSpell>> MERCURY_SPELLS = List.of(
            MERCURY_BULLETS, MERCURY_SPEARS, SILVER_RAIN, STORM_OF_BLADES, MERCURY_EAGLE,
            SILVER_MIRROR, MERCURY_SPHERE, REGAL_SPEAR, SILVER_GUARDIAN, MERCURY_CAGE);

    // ------------------------------------------------------------------ Fire Grimoire (Magna Swing's Flame Magic)
    //                                                                                id                        cd mana power  animation
    public static final RegistryObject<AbstractSpell> EXPLODING_FIREBALL = registerSpell(new FireSpell("exploding_fireball", 4, 20, 12, FireSpell.anim("iron_spell_grimoire:fire_fireball"), FireFx::explodingFireball));
    public static final RegistryObject<AbstractSpell> EXPLOSIVE_SCATTERSHOT = registerSpell(new FireSpell("explosive_scattershot", 9, 35, 14, FireSpell.anim("iron_spell_grimoire:fire_scattershot"), FireFx::scattershot));
    public static final RegistryObject<AbstractSpell> MAGNA_SPECIAL = registerSpell(new FireSpell("magna_special", 14, 45, 26, FireSpell.anim("iron_spell_grimoire:fire_magna_special"), FireFx::magnaSpecial));
    public static final RegistryObject<AbstractSpell> FLAMING_BAT = registerSpell(new FireSpell("flaming_bat", 8, 30, 22, FireSpell.anim("iron_spell_grimoire:fire_bat_combo"), FireFx::flamingBat));
    public static final RegistryObject<AbstractSpell> FLAME_KNUCKLES = registerSpell(new FireSpell("flame_knuckles", 30, 30, 0, FireSpell.anim("iron_spell_grimoire:fire_knuckles"), FireFx::flameKnuckles));
    public static final RegistryObject<AbstractSpell> RING_OF_FIRE = registerSpell(new FireSpell("ring_of_fire", 18, 40, 10, FireSpell.anim("irons_spellbooks:stomp"), FireFx::ringOfFire));
    public static final RegistryObject<AbstractSpell> FIREBALL_BARRAGE = registerSpell(new FireSpell("fireball_barrage", 12, 45, 14, FireSpell.anim("iron_spell_grimoire:fire_barrage"), FireFx::fireballBarrage));
    public static final RegistryObject<AbstractSpell> FIREBALL_RAIN = registerSpell(new FireSpell("fireball_rain", 22, 60, 16, FireSpell.anim("iron_spell_grimoire:fire_rain"), FireFx::fireballRain));
    public static final RegistryObject<AbstractSpell> SOUL_CHAIN = registerSpell(new FireSpell("soul_chain", 45, 65, 8, FireSpell.anim("iron_spell_grimoire:fire_soul_chain"), FireFx::soulChain));
    public static final RegistryObject<AbstractSpell> FIRE_CANNON = registerSpell(new FireSpell("fire_cannon", 60, 115, 40, FireSpell.anim("iron_spell_grimoire:fire_cannon"), FireFx::fireCannon));

    /** The ten Fire Grimoire spells, in the order they fill the book. */
    public static final List<RegistryObject<AbstractSpell>> FIRE_SPELLS = List.of(
            EXPLODING_FIREBALL, EXPLOSIVE_SCATTERSHOT, MAGNA_SPECIAL, FLAMING_BAT, FLAME_KNUCKLES,
            RING_OF_FIRE, FIREBALL_BARRAGE, FIREBALL_RAIN, SOUL_CHAIN, FIRE_CANNON);

    // ------------------------------------------------------------------ Lightning Grimoire (Luck Voltia's Lightning Magic)
    //                                                                                id                       cd mana power  animation
    public static final RegistryObject<AbstractSpell> THUNDER_FIST = registerSpell(new LightningSpell("thunder_fist", 5, 25, 14, LightningSpell.anim("iron_spell_grimoire:lightning_fist"), LightningFx::thunderFist));
    public static final RegistryObject<AbstractSpell> THUNDER_KICK = registerSpell(new LightningSpell("thunder_kick", 8, 30, 20, LightningSpell.anim("iron_spell_grimoire:lightning_kicks"), LightningFx::thunderKick));
    public static final RegistryObject<AbstractSpell> THUNDER_ORB = registerSpell(new LightningSpell("thunder_orb", 10, 30, 14, LightningSpell.anim("iron_spell_grimoire:lightning_orb"), LightningFx::thunderOrb));
    public static final RegistryObject<AbstractSpell> LIGHTNING_STEP = registerSpell(new LightningSpell("lightning_step", 4, 15, 8, LightningSpell.anim("irons_spellbooks:instant_self"), LightningFx::lightningStep));
    public static final RegistryObject<AbstractSpell> CHAIN_LIGHTNING = registerSpell(new LightningSpell("chain_lightning", 10, 35, 14, LightningSpell.anim("iron_spell_grimoire:lightning_chain"), LightningFx::chainLightning));
    public static final RegistryObject<AbstractSpell> THUNDER_GLOVES = registerSpell(new LightningSpell("thunder_gloves", 30, 30, 0, LightningSpell.anim("iron_spell_grimoire:fire_knuckles"), LightningFx::thunderGloves));
    public static final RegistryObject<AbstractSpell> MANA_SENSE = registerSpell(new LightningSpell("mana_sense", 20, 15, 0, LightningSpell.anim("iron_spell_grimoire:dark_ki_read"), LightningFx::manaSense));
    public static final RegistryObject<AbstractSpell> THUNDERFALL = registerSpell(new LightningSpell("thunderfall", 20, 60, 16, LightningSpell.anim("iron_spell_grimoire:fire_rain"), LightningFx::thunderfall));
    public static final RegistryObject<AbstractSpell> THUNDER_GOD_MODE = registerSpell(new LightningSpell("thunder_god_mode", 90, 100, 0, LightningSpell.anim("iron_spell_grimoire:lightning_mode"), LightningFx::thunderMode));
    public static final RegistryObject<AbstractSpell> THUNDER_RAMPAGE = registerSpell(new LightningSpell("thunder_rampage", 60, 115, 30, LightningSpell.anim("iron_spell_grimoire:lightning_rampage"), LightningFx::rampage));

    /** The ten Lightning Grimoire spells, in the order they fill the book. */
    public static final List<RegistryObject<AbstractSpell>> LIGHTNING_SPELLS = List.of(
            THUNDER_FIST, THUNDER_KICK, THUNDER_ORB, LIGHTNING_STEP, CHAIN_LIGHTNING,
            THUNDER_GLOVES, MANA_SENSE, THUNDERFALL, THUNDER_GOD_MODE, THUNDER_RAMPAGE);

    // ------------------------------------------------------------------ Hellfire Grimoire (Mereoleona Vermillion's Flame Magic)
    //                                                                                  id                          cd mana power  animation
    public static final RegistryObject<AbstractSpell> CALIDOS_BRACHIUM = registerSpell(new HellfireSpell("calidos_brachium", 5, 25, 16, HellfireSpell.anim("iron_spell_grimoire:lion_punch"), HellfireFx::calidosBrachium));
    public static final RegistryObject<AbstractSpell> BRACHIUM_BARRAGE = registerSpell(new HellfireSpell("calidos_brachium_barrage", 14, 50, 30, HellfireSpell.anim("iron_spell_grimoire:lion_barrage"), HellfireFx::barrage));
    public static final RegistryObject<AbstractSpell> FLAME_LION_PAWS = registerSpell(new HellfireSpell("flame_lion_paws", 16, 45, 24, HellfireSpell.anim("iron_spell_grimoire:klaus_clench"), HellfireFx::lionPaws));
    public static final RegistryObject<AbstractSpell> LIONESS_CHARGE = registerSpell(new HellfireSpell("lioness_charge", 7, 25, 18, HellfireSpell.anim("iron_spell_grimoire:lightning_fist"), HellfireFx::lionessCharge));
    public static final RegistryObject<AbstractSpell> HELL_MANA_SKIN = registerSpell(new HellfireSpell("mana_skin", 40, 40, 0, HellfireSpell.anim("iron_spell_grimoire:mercury_guardian"), HellfireFx::manaSkin));
    public static final RegistryObject<AbstractSpell> HELL_MANA_ZONE = registerSpell(new HellfireSpell("lion_mana_zone", 60, 90, 0, HellfireSpell.anim("iron_spell_grimoire:dark_mana_zone"), HellfireFx::manaZone));
    public static final RegistryObject<AbstractSpell> GROUND_PUNCH = registerSpell(new HellfireSpell("ground_punch", 12, 40, 18, HellfireSpell.anim("iron_spell_grimoire:lion_ground_punch"), HellfireFx::groundPunch));
    public static final RegistryObject<AbstractSpell> BURNING_GUARD = registerSpell(new HellfireSpell("burning_guard", 14, 30, 18, HellfireSpell.anim("iron_spell_grimoire:lion_ignis"), HellfireFx::ignisColumna));
    public static final RegistryObject<AbstractSpell> HELLFIRE_INCARNATE = registerSpell(new HellfireSpell("hellfire_incarnate", 120, 105, 0, HellfireSpell.anim("iron_spell_grimoire:lightning_mode"), HellfireFx::hellfire));
    public static final RegistryObject<AbstractSpell> BRACHIUM_PURGATORY = registerSpell(new HellfireSpell("calidos_brachium_purgatory", 120, 65, 55, HellfireSpell.anim("iron_spell_grimoire:lion_purgatory"), HellfireFx::purgatory));

    /** The ten Hellfire Grimoire spells, in the order they fill the book. */
    public static final List<RegistryObject<AbstractSpell>> HELLFIRE_SPELLS = List.of(
            CALIDOS_BRACHIUM, BRACHIUM_BARRAGE, FLAME_LION_PAWS, LIONESS_CHARGE, HELL_MANA_SKIN,
            HELL_MANA_ZONE, GROUND_PUNCH, BURNING_GUARD, HELLFIRE_INCARNATE, BRACHIUM_PURGATORY);

    // ------------------------------------------------------------------ Bone Grimoire (Zenon Zogratis' Bone Magic)
    //                                                                             id                       cd mana power  animation
    public static final RegistryObject<AbstractSpell> BONE_SPEAR_VOLLEY = registerSpell(new BoneSpell("bone_spear_volley", 6, 25, 14, BoneSpell.anim("iron_spell_grimoire:bone_volley"), BoneFx::spearVolley));
    public static final RegistryObject<AbstractSpell> BONE_SPIKE_ERUPTION = registerSpell(new BoneSpell("bone_spike_eruption", 10, 35, 14, BoneSpell.anim("irons_spellbooks:stomp"), BoneFx::spikeEruption));
    public static final RegistryObject<AbstractSpell> BONE_SWORD = registerSpell(new BoneSpell("bone_sword", 8, 25, 22, BoneSpell.anim("iron_spell_grimoire:bone_claws"), BoneFx::boneSword));
    public static final RegistryObject<AbstractSpell> BONE_WHIPS = registerSpell(new BoneSpell("bone_whips", 14, 45, 22, BoneSpell.anim("iron_spell_grimoire:mana_bone_whips"), BoneFx::boneWhips));
    public static final RegistryObject<AbstractSpell> RIBCAGE_PRISON = registerSpell(new BoneSpell("ribcage_prison", 20, 45, 20, BoneSpell.anim("iron_spell_grimoire:klaus_clench"), BoneFx::ribcage));
    public static final RegistryObject<AbstractSpell> MARROW_REGENERATION = registerSpell(new BoneSpell("marrow_regeneration", 40, 40, 0, BoneSpell.anim("iron_spell_grimoire:mercury_guardian"), BoneFx::regeneration));
    public static final RegistryObject<AbstractSpell> BONE_THICKET = registerSpell(new BoneSpell("bone_thicket", 22, 60, 14, BoneSpell.anim("irons_spellbooks:touch_ground"), BoneFx::thicket));
    public static final RegistryObject<AbstractSpell> SPATIAL_CUBE = registerSpell(new BoneSpell("spatial_cube", 26, 60, 26, BoneSpell.anim("iron_spell_grimoire:lightning_chain"), BoneFx::spatialCube));
    public static final RegistryObject<AbstractSpell> BEELZEBUB_UNION = registerSpell(new BoneSpell("beelzebub_union", 100, 100, 0, BoneSpell.anim("iron_spell_grimoire:devil_union_awaken"), BoneFx::devilUnion));
    public static final RegistryObject<AbstractSpell> BONE_SPEAR_STORM = registerSpell(new BoneSpell("bone_spear_storm", 60, 115, 30, BoneSpell.anim("iron_spell_grimoire:fire_rain"), BoneFx::spearStorm));

    /** The ten Bone Grimoire spells, in the order they fill the book. */
    public static final List<RegistryObject<AbstractSpell>> BONE_SPELLS = List.of(
            BONE_SPEAR_VOLLEY, BONE_SPIKE_ERUPTION, BONE_SWORD, BONE_WHIPS, RIBCAGE_PRISON,
            MARROW_REGENERATION, BONE_THICKET, SPATIAL_CUBE, BEELZEBUB_UNION, BONE_SPEAR_STORM);

    /** The ten Dark Grimoire spells, in the order they fill the book. */
    public static final List<RegistryObject<AbstractSpell>> DARK_SPELLS = List.of(
            BLACK_BLADE, DIMENSION_SLASH, AVIDYA_SLASH, BLACK_COCOON, DEATH_THRUST,
            BLACK_MOON, BLACK_HOLE, MANA_ZONE_DIMENSION_SLASH, KI_FORESIGHT, EVENT_HORIZON);

    // ------------------------------------------------------------------ Thread Grimoire (Vanessa Enoteca's Thread Magic)
    private static com.ironspellgrimoire.spells.BookSpell thread(String id, int cd, int mana, float power, String anim, com.ironspellgrimoire.spells.BookSpell.Effect fx) {
        return new com.ironspellgrimoire.spells.BookSpell(id, io.redspace.ironsspellbooks.api.registry.SchoolRegistry.EVOCATION_RESOURCE,
                com.ironspellgrimoire.item.ThreadGrimoireItem.class, "ui.iron_spell_grimoire.string_grimoire_only", cd, mana, power,
                com.ironspellgrimoire.spells.BookSpell.anim(anim), fx);
    }
    public static final RegistryObject<AbstractSpell> BINDING_THREADS = registerSpell(thread("binding_threads", 8, 30, 8, "iron_spell_grimoire:lightning_chain", com.ironspellgrimoire.server.ThreadFx::bindingThreads));
    public static final RegistryObject<AbstractSpell> PUPPET_STRINGS = registerSpell(thread("puppet_strings", 12, 40, 18, "iron_spell_grimoire:klaus_clench", com.ironspellgrimoire.server.ThreadFx::puppetStrings));
    public static final RegistryObject<AbstractSpell> THREAD_NET = registerSpell(thread("thread_net", 16, 40, 0, "iron_spell_grimoire:mercury_mirror", com.ironspellgrimoire.server.ThreadFx::threadNet));
    public static final RegistryObject<AbstractSpell> THREAD_COCOON = registerSpell(thread("thread_cocoon", 24, 45, 10, "iron_spell_grimoire:mercury_guardian", com.ironspellgrimoire.server.ThreadFx::cocoon));
    public static final RegistryObject<AbstractSpell> STITCHING = registerSpell(thread("stitching", 30, 50, 10, "iron_spell_grimoire:dark_ki_read", com.ironspellgrimoire.server.ThreadFx::stitching));
    public static final RegistryObject<AbstractSpell> BATTLE_DRESS = registerSpell(thread("battle_dress", 45, 50, 0, "iron_spell_grimoire:mercury_guardian", com.ironspellgrimoire.server.ThreadFx::battleDress));
    public static final RegistryObject<AbstractSpell> THREAD_CAGE = registerSpell(thread("thread_cage", 28, 65, 16, "iron_spell_grimoire:fire_rain", com.ironspellgrimoire.server.ThreadFx::threadCage));
    public static final RegistryObject<AbstractSpell> RED_THREAD_OF_FATE = registerSpell(thread("red_thread_of_fate", 90, 110, 0, "iron_spell_grimoire:dark_mana_zone", com.ironspellgrimoire.server.ThreadFx::redThreadOfFate));

    /** The eight Thread Grimoire spells, in the order they fill the book. */
    public static final List<RegistryObject<AbstractSpell>> THREAD_SPELLS = List.of(
            BINDING_THREADS, PUPPET_STRINGS, THREAD_NET, THREAD_COCOON, STITCHING, BATTLE_DRESS, THREAD_CAGE, RED_THREAD_OF_FATE);

    // ------------------------------------------------------------------ Space Grimoire (Finral Roulacase's Spatial Magic)
    private static com.ironspellgrimoire.spells.BookSpell space(String id, int cd, int mana, float power, String anim, com.ironspellgrimoire.spells.BookSpell.Effect fx) {
        return new com.ironspellgrimoire.spells.BookSpell(id, io.redspace.ironsspellbooks.api.registry.SchoolRegistry.ENDER_RESOURCE,
                com.ironspellgrimoire.item.SpaceGrimoireItem.class, "ui.iron_spell_grimoire.spatial_grimoire_only", cd, mana, power,
                com.ironspellgrimoire.spells.BookSpell.anim(anim), fx);
    }
    public static final RegistryObject<AbstractSpell> SPATIAL_GATE = registerSpell(space("spatial_gate", 20, 50, 0, "iron_spell_grimoire:mercury_mirror", com.ironspellgrimoire.server.SpaceFx::spatialGate));
    public static final RegistryObject<AbstractSpell> SHORT_HOP = registerSpell(space("short_hop", 5, 20, 0, "irons_spellbooks:instant_self", com.ironspellgrimoire.server.SpaceFx::shortHop));
    public static final RegistryObject<AbstractSpell> ESCORT_GATE = registerSpell(space("escort_gate", 40, 70, 0, "iron_spell_grimoire:mercury_mirror", com.ironspellgrimoire.server.SpaceFx::escortGate));
    public static final RegistryObject<AbstractSpell> WAYPOINT = registerSpell(space("waypoint", 3, 25, 0, "irons_spellbooks:instant_self", com.ironspellgrimoire.server.SpaceFx::waypoint));
    public static final RegistryObject<AbstractSpell> SKY_DROP = registerSpell(space("sky_drop", 14, 45, 10, "iron_spell_grimoire:klaus_clench", com.ironspellgrimoire.server.SpaceFx::skyDrop));
    public static final RegistryObject<AbstractSpell> REDIRECT_GATE = registerSpell(space("redirect_gate", 18, 40, 0, "iron_spell_grimoire:mercury_mirror", com.ironspellgrimoire.server.SpaceFx::redirectGate));
    public static final RegistryObject<AbstractSpell> EVASION_GATE = registerSpell(space("evasion_gate", 25, 40, 0, "iron_spell_grimoire:dark_ki_read", com.ironspellgrimoire.server.SpaceFx::evasionGate));
    public static final RegistryObject<AbstractSpell> SPACE_DELETION = registerSpell(space("space_deletion", 75, 110, 28, "iron_spell_grimoire:dark_black_hole", com.ironspellgrimoire.server.SpaceFx::spaceDeletion));

    /** The eight Space Grimoire spells, in the order they fill the book. */
    public static final List<RegistryObject<AbstractSpell>> SPACE_SPELLS = List.of(
            SPATIAL_GATE, SHORT_HOP, ESCORT_GATE, WAYPOINT, SKY_DROP, REDIRECT_GATE, EVASION_GATE, SPACE_DELETION);


    // ------------------------------------------------------------------ Water Grimoire (Noelle Silva's Water Magic)
    private static com.ironspellgrimoire.spells.BookSpell water(String id, int cd, int mana, float power, String anim, com.ironspellgrimoire.spells.BookSpell.Effect fx) {
        return new com.ironspellgrimoire.spells.BookSpell(id, io.redspace.ironsspellbooks.api.registry.SchoolRegistry.EVOCATION_RESOURCE,
                com.ironspellgrimoire.item.WaterGrimoireItem.class, "ui.iron_spell_grimoire.water_grimoire_only", cd, mana, power,
                com.ironspellgrimoire.spells.BookSpell.anim(anim), fx);
    }
    public static final RegistryObject<AbstractSpell> WATER_BALL = registerSpell(water("water_ball", 4, 20, 9, "iron_spell_grimoire:fire_fireball", com.ironspellgrimoire.server.WaterFx::waterBall));
    public static final RegistryObject<AbstractSpell> TORRENT_LANCE = registerSpell(water("torrent_lance", 8, 30, 12, "iron_spell_grimoire:mercury_regal_spear", com.ironspellgrimoire.server.WaterFx::torrentLance));
    public static final RegistryObject<AbstractSpell> SEA_DRAGONS_NEST = registerSpell(water("sea_dragons_nest", 18, 55, 14, "iron_spell_grimoire:mercury_sphere", com.ironspellgrimoire.server.WaterFx::seaDragonsNest));
    public static final RegistryObject<AbstractSpell> AQUA_BARRAGE = registerSpell(water("aqua_barrage", 10, 40, 10, "iron_spell_grimoire:fire_barrage", com.ironspellgrimoire.server.WaterFx::aquaBarrage));
    public static final RegistryObject<AbstractSpell> TIDAL_WAVE = registerSpell(water("tidal_wave", 20, 60, 14, "iron_spell_grimoire:klaus_clench", com.ironspellgrimoire.server.WaterFx::tidalWave));
    public static final RegistryObject<AbstractSpell> SEA_SERPENT = registerSpell(water("sea_serpent", 16, 55, 14, "iron_spell_grimoire:lightning_chain", com.ironspellgrimoire.server.WaterFx::seaSerpent));
    public static final RegistryObject<AbstractSpell> VALKYRIE_DRESS = registerSpell(water("valkyrie_dress", 60, 80, 0, "iron_spell_grimoire:mercury_guardian", com.ironspellgrimoire.server.WaterFx::valkyrieDress));
    public static final RegistryObject<AbstractSpell> SEA_DRAGONS_WRATH = registerSpell(water("sea_dragons_wrath", 80, 120, 30, "iron_spell_grimoire:fire_cannon", com.ironspellgrimoire.server.WaterFx::seaDragonsWrath));

    /** The eight Water Grimoire spells, in the order they fill the book. */
    public static final List<RegistryObject<AbstractSpell>> WATER_SPELLS = List.of(
            WATER_BALL, TORRENT_LANCE, SEA_DRAGONS_NEST, AQUA_BARRAGE, TIDAL_WAVE, SEA_SERPENT, VALKYRIE_DRESS, SEA_DRAGONS_WRATH);

    // ------------------------------------------------------------------ Mirror Grimoire (Gauche Adlai's Mirror Magic)
    private static com.ironspellgrimoire.spells.BookSpell mirror(String id, int cd, int mana, float power, String anim, com.ironspellgrimoire.spells.BookSpell.Effect fx) {
        return new com.ironspellgrimoire.spells.BookSpell(id, io.redspace.ironsspellbooks.api.registry.SchoolRegistry.ENDER_RESOURCE,
                com.ironspellgrimoire.item.MirrorGrimoireItem.class, "ui.iron_spell_grimoire.mirror_grimoire_only", cd, mana, power,
                com.ironspellgrimoire.spells.BookSpell.anim(anim), fx);
    }
    public static final RegistryObject<AbstractSpell> MIRROR_SHARD = registerSpell(mirror("mirror_shard", 4, 20, 9, "iron_spell_grimoire:mercury_bullets", com.ironspellgrimoire.server.MirrorFx::mirrorShard));
    public static final RegistryObject<AbstractSpell> MIRROR_STEP = registerSpell(mirror("mirror_step", 6, 25, 0, "irons_spellbooks:instant_self", com.ironspellgrimoire.server.MirrorFx::mirrorStep));
    public static final RegistryObject<AbstractSpell> REFLECT_GUARD = registerSpell(mirror("reflect_guard", 22, 45, 0, "iron_spell_grimoire:mercury_mirror", com.ironspellgrimoire.server.MirrorFx::reflectGuard));
    public static final RegistryObject<AbstractSpell> PRISM_BEAM = registerSpell(mirror("prism_beam", 12, 40, 12, "iron_spell_grimoire:klaus_railgun", com.ironspellgrimoire.server.MirrorFx::prismBeam));
    public static final RegistryObject<AbstractSpell> MIRROR_CAGE = registerSpell(mirror("mirror_cage", 20, 55, 14, "iron_spell_grimoire:mercury_mirror", com.ironspellgrimoire.server.MirrorFx::mirrorCage));
    public static final RegistryObject<AbstractSpell> MIRROR_BARRAGE = registerSpell(mirror("mirror_barrage", 24, 60, 14, "iron_spell_grimoire:mercury_silver_rain", com.ironspellgrimoire.server.MirrorFx::mirrorBarrage));
    public static final RegistryObject<AbstractSpell> MIRROR_DOUBLE = registerSpell(mirror("mirror_double", 14, 45, 14, "iron_spell_grimoire:dark_death_thrust", com.ironspellgrimoire.server.MirrorFx::mirrorDouble));
    public static final RegistryObject<AbstractSpell> HALL_OF_MIRRORS = registerSpell(mirror("hall_of_mirrors", 80, 120, 28, "iron_spell_grimoire:dark_mana_zone", com.ironspellgrimoire.server.MirrorFx::hallOfMirrors));

    /** The eight Mirror Grimoire spells, in the order they fill the book. */
    public static final List<RegistryObject<AbstractSpell>> MIRROR_SPELLS = List.of(
            MIRROR_SHARD, MIRROR_STEP, REFLECT_GUARD, PRISM_BEAM, MIRROR_CAGE, MIRROR_BARRAGE, MIRROR_DOUBLE, HALL_OF_MIRRORS);

    // ------------------------------------------------------------------ Rift Grimoire (Langris Vaude's Spatial Magic)
    private static com.ironspellgrimoire.spells.BookSpell rift(String id, int cd, int mana, float power, String anim, com.ironspellgrimoire.spells.BookSpell.Effect fx) {
        return new com.ironspellgrimoire.spells.BookSpell(id, io.redspace.ironsspellbooks.api.registry.SchoolRegistry.ENDER_RESOURCE,
                com.ironspellgrimoire.item.RiftGrimoireItem.class, "ui.iron_spell_grimoire.rift_grimoire_only", cd, mana, power,
                com.ironspellgrimoire.spells.BookSpell.anim(anim), fx);
    }
    public static final RegistryObject<AbstractSpell> SPATIAL_CUT = registerSpell(rift("spatial_cut", 4, 20, 10, "iron_spell_grimoire:dark_dimension_slash", com.ironspellgrimoire.server.RiftFx::spatialCut));
    public static final RegistryObject<AbstractSpell> RIFT_BLADES = registerSpell(rift("rift_blades", 8, 30, 10, "iron_spell_grimoire:mercury_spears", com.ironspellgrimoire.server.RiftFx::riftBlades));
    public static final RegistryObject<AbstractSpell> BLINK_STRIKE = registerSpell(rift("blink_strike", 12, 40, 14, "iron_spell_grimoire:dark_death_thrust", com.ironspellgrimoire.server.RiftFx::blinkStrike));
    public static final RegistryObject<AbstractSpell> SEVERING_LINE = registerSpell(rift("severing_line", 14, 45, 14, "iron_spell_grimoire:dark_avidya", com.ironspellgrimoire.server.RiftFx::severingLine));
    public static final RegistryObject<AbstractSpell> GATE_BARRAGE = registerSpell(rift("gate_barrage", 18, 55, 12, "iron_spell_grimoire:mercury_mirror", com.ironspellgrimoire.server.RiftFx::gateBarrage));
    public static final RegistryObject<AbstractSpell> SPACE_COLLAPSE = registerSpell(rift("space_collapse", 22, 65, 16, "iron_spell_grimoire:dark_black_hole", com.ironspellgrimoire.server.RiftFx::spaceCollapse));
    public static final RegistryObject<AbstractSpell> CONVERGENCE = registerSpell(rift("convergence", 24, 70, 14, "iron_spell_grimoire:klaus_clench", com.ironspellgrimoire.server.RiftFx::convergence));
    public static final RegistryObject<AbstractSpell> DIMENSIONAL_SEVERANCE = registerSpell(rift("dimensional_severance", 90, 130, 34, "iron_spell_grimoire:rift_severance", com.ironspellgrimoire.server.RiftFx::dimensionalSeverance));

    /** The eight Rift Grimoire spells, in the order they fill the book. */
    public static final List<RegistryObject<AbstractSpell>> RIFT_SPELLS = List.of(
            SPATIAL_CUT, RIFT_BLADES, BLINK_STRIKE, SEVERING_LINE, GATE_BARRAGE, SPACE_COLLAPSE, CONVERGENCE, DIMENSIONAL_SEVERANCE);


    // ------------------------------------------------------------------ Flame Spirit Grimoire (Fuegoleon Vermillion)
    private static com.ironspellgrimoire.spells.BookSpell flame_spirit(String id, int cd, int mana, float power, String anim, com.ironspellgrimoire.spells.BookSpell.Effect fx) {
        return new com.ironspellgrimoire.spells.BookSpell(id, io.redspace.ironsspellbooks.api.registry.SchoolRegistry.FIRE_RESOURCE,
                com.ironspellgrimoire.item.FlameSpiritGrimoireItem.class, "ui.iron_spell_grimoire.flame_spirit_grimoire_only", cd, mana, power,
                com.ironspellgrimoire.spells.BookSpell.anim(anim), fx);
    }
    public static final RegistryObject<AbstractSpell> SALAMANDER_FLARE = registerSpell(flame_spirit("salamander_flare", 4, 20, 9, "iron_spell_grimoire:fire_fireball", com.ironspellgrimoire.server.FlameSpiritFx::salamanderFlare));
    public static final RegistryObject<AbstractSpell> SPIRIT_LANCE = registerSpell(flame_spirit("spirit_lance", 8, 30, 12, "iron_spell_grimoire:mercury_regal_spear", com.ironspellgrimoire.server.FlameSpiritFx::spiritLance));
    public static final RegistryObject<AbstractSpell> FLAME_AEGIS = registerSpell(flame_spirit("flame_aegis", 22, 45, 10, "iron_spell_grimoire:mercury_guardian", com.ironspellgrimoire.server.FlameSpiritFx::flameAegis));
    public static final RegistryObject<AbstractSpell> SALAMANDER_COIL = registerSpell(flame_spirit("salamander_coil", 16, 55, 13, "iron_spell_grimoire:lightning_chain", com.ironspellgrimoire.server.FlameSpiritFx::salamanderCoil));
    public static final RegistryObject<AbstractSpell> FLARE_VOLLEY = registerSpell(flame_spirit("flare_volley", 20, 60, 12, "iron_spell_grimoire:fire_rain", com.ironspellgrimoire.server.FlameSpiritFx::flareVolley));
    public static final RegistryObject<AbstractSpell> SALAMANDER_RUSH = registerSpell(flame_spirit("salamander_rush", 14, 45, 14, "iron_spell_grimoire:klaus_lance_charge", com.ironspellgrimoire.server.FlameSpiritFx::salamanderRush));
    public static final RegistryObject<AbstractSpell> SALAMANDER_SPIRIT = registerSpell(flame_spirit("salamander_spirit", 60, 90, 8, "iron_spell_grimoire:lightning_mode", com.ironspellgrimoire.server.FlameSpiritFx::salamanderSpirit));
    public static final RegistryObject<AbstractSpell> GREAT_SALAMANDER = registerSpell(flame_spirit("great_salamander", 80, 125, 30, "iron_spell_grimoire:fire_cannon", com.ironspellgrimoire.server.FlameSpiritFx::greatSalamander));

    /** The eight Flame Spirit Grimoire spells, in the order they fill the book. */
    public static final List<RegistryObject<AbstractSpell>> FLAME_SPIRIT_SPELLS = List.of(
            SALAMANDER_FLARE, SPIRIT_LANCE, FLAME_AEGIS, SALAMANDER_COIL, FLARE_VOLLEY, SALAMANDER_RUSH, SALAMANDER_SPIRIT, GREAT_SALAMANDER);

    // ------------------------------------------------------------------ Lion's Flame Grimoire (Leopold Vermillion)
    private static com.ironspellgrimoire.spells.BookSpell lion_flame(String id, int cd, int mana, float power, String anim, com.ironspellgrimoire.spells.BookSpell.Effect fx) {
        return new com.ironspellgrimoire.spells.BookSpell(id, io.redspace.ironsspellbooks.api.registry.SchoolRegistry.FIRE_RESOURCE,
                com.ironspellgrimoire.item.LionFlameGrimoireItem.class, "ui.iron_spell_grimoire.lions_flame_grimoire_only", cd, mana, power,
                com.ironspellgrimoire.spells.BookSpell.anim(anim), fx);
    }
    public static final RegistryObject<AbstractSpell> EMBER_FIST = registerSpell(lion_flame("ember_fist", 3, 15, 9, "iron_spell_grimoire:lion_punch", com.ironspellgrimoire.server.LionFlameFx::emberFist));
    public static final RegistryObject<AbstractSpell> LION_CLAW = registerSpell(lion_flame("lion_claw", 6, 25, 10, "iron_spell_grimoire:fire_knuckles", com.ironspellgrimoire.server.LionFlameFx::lionClaw));
    public static final RegistryObject<AbstractSpell> FLAME_LEAP = registerSpell(lion_flame("flame_leap", 12, 40, 14, "iron_spell_grimoire:lion_ground_punch", com.ironspellgrimoire.server.LionFlameFx::flameLeap));
    public static final RegistryObject<AbstractSpell> ROARING_BURST = registerSpell(lion_flame("roaring_burst", 14, 45, 12, "iron_spell_grimoire:lion_ignis", com.ironspellgrimoire.server.LionFlameFx::roaringBurst));
    public static final RegistryObject<AbstractSpell> PRIDE_BARRAGE = registerSpell(lion_flame("pride_barrage", 14, 50, 10, "iron_spell_grimoire:lion_barrage", com.ironspellgrimoire.server.LionFlameFx::prideBarrage));
    public static final RegistryObject<AbstractSpell> PROWL_DASH = registerSpell(lion_flame("prowl_dash", 9, 35, 11, "iron_spell_grimoire:fire_knuckles", com.ironspellgrimoire.server.LionFlameFx::prowlDash));
    public static final RegistryObject<AbstractSpell> MANE_BLAZE = registerSpell(lion_flame("mane_blaze", 60, 90, 8, "iron_spell_grimoire:lightning_mode", com.ironspellgrimoire.server.LionFlameFx::maneBlaze));
    public static final RegistryObject<AbstractSpell> KING_LION_ROAR = registerSpell(lion_flame("king_lion_roar", 80, 125, 30, "iron_spell_grimoire:fire_cannon", com.ironspellgrimoire.server.LionFlameFx::kingLionRoar));

    /** The eight Lion's Flame Grimoire spells, in the order they fill the book. */
    public static final List<RegistryObject<AbstractSpell>> LION_FLAME_SPELLS = List.of(
            EMBER_FIST, LION_CLAW, FLAME_LEAP, ROARING_BURST, PRIDE_BARRAGE, PROWL_DASH, MANE_BLAZE, KING_LION_ROAR);

    // ------------------------------------------------------------------ World Tree Grimoire (William Vangeance)
    private static com.ironspellgrimoire.spells.BookSpell world_tree(String id, int cd, int mana, float power, String anim, com.ironspellgrimoire.spells.BookSpell.Effect fx) {
        return new com.ironspellgrimoire.spells.BookSpell(id, io.redspace.ironsspellbooks.api.registry.SchoolRegistry.NATURE_RESOURCE,
                com.ironspellgrimoire.item.WorldTreeGrimoireItem.class, "ui.iron_spell_grimoire.world_tree_grimoire_only", cd, mana, power,
                com.ironspellgrimoire.spells.BookSpell.anim(anim), fx);
    }
    public static final RegistryObject<AbstractSpell> THORN_BOLT = registerSpell(world_tree("thorn_bolt", 4, 20, 9, "iron_spell_grimoire:wind_gale_bow", com.ironspellgrimoire.server.WorldTreeFx::thornBolt));
    public static final RegistryObject<AbstractSpell> VINE_LASH = registerSpell(world_tree("vine_lash", 7, 28, 11, "iron_spell_grimoire:mana_bone_whips", com.ironspellgrimoire.server.WorldTreeFx::vineLash));
    public static final RegistryObject<AbstractSpell> ROOT_SNARE = registerSpell(world_tree("root_snare", 18, 45, 12, "iron_spell_grimoire:klaus_clench", com.ironspellgrimoire.server.WorldTreeFx::rootSnare));
    public static final RegistryObject<AbstractSpell> LEAF_STORM = registerSpell(world_tree("leaf_storm", 16, 50, 10, "iron_spell_grimoire:wind_blade_shower", com.ironspellgrimoire.server.WorldTreeFx::leafStorm));
    public static final RegistryObject<AbstractSpell> VERDANT_BLOOM = registerSpell(world_tree("verdant_bloom", 24, 55, 10, "iron_spell_grimoire:mercury_guardian", com.ironspellgrimoire.server.WorldTreeFx::verdantBloom));
    public static final RegistryObject<AbstractSpell> TREANT_GUARDIAN = registerSpell(world_tree("treant_guardian", 18, 60, 13, "iron_spell_grimoire:klaus_clench", com.ironspellgrimoire.server.WorldTreeFx::treantGuardian));
    public static final RegistryObject<AbstractSpell> WORLD_TREE_MANTLE = registerSpell(world_tree("world_tree_mantle", 60, 90, 0, "iron_spell_grimoire:wind_sylphs_breath", com.ironspellgrimoire.server.WorldTreeFx::worldTreeMantle));
    public static final RegistryObject<AbstractSpell> WORLD_TREE_JUDGEMENT = registerSpell(world_tree("world_tree_judgement", 85, 125, 30, "iron_spell_grimoire:fire_cannon", com.ironspellgrimoire.server.WorldTreeFx::worldTreeJudgement));

    /** The eight World Tree Grimoire spells, in the order they fill the book. */
    public static final List<RegistryObject<AbstractSpell>> WORLD_TREE_SPELLS = List.of(
            THORN_BOLT, VINE_LASH, ROOT_SNARE, LEAF_STORM, VERDANT_BLOOM, TREANT_GUARDIAN, WORLD_TREE_MANTLE, WORLD_TREE_JUDGEMENT);

    // ------------------------------------------------------------------ Slash Grimoire (Jack the Ripper)
    private static com.ironspellgrimoire.spells.BookSpell slash(String id, int cd, int mana, float power, String anim, com.ironspellgrimoire.spells.BookSpell.Effect fx) {
        return new com.ironspellgrimoire.spells.BookSpell(id, io.redspace.ironsspellbooks.api.registry.SchoolRegistry.BLOOD_RESOURCE,
                com.ironspellgrimoire.item.SlashGrimoireItem.class, "ui.iron_spell_grimoire.slash_grimoire_only", cd, mana, power,
                com.ironspellgrimoire.spells.BookSpell.anim(anim), fx);
    }
    public static final RegistryObject<AbstractSpell> PHANTOM_SLASH = registerSpell(slash("phantom_slash", 4, 20, 10, "iron_spell_grimoire:dark_dimension_slash", com.ironspellgrimoire.server.SlashFx::phantomSlash));
    public static final RegistryObject<AbstractSpell> CRESCENT_CUT = registerSpell(slash("crescent_cut", 8, 30, 10, "iron_spell_grimoire:dark_avidya", com.ironspellgrimoire.server.SlashFx::crescentCut));
    public static final RegistryObject<AbstractSpell> SHADOW_STEP_SLASH = registerSpell(slash("shadow_step_slash", 10, 35, 14, "iron_spell_grimoire:dark_death_thrust", com.ironspellgrimoire.server.SlashFx::shadowStepSlash));
    public static final RegistryObject<AbstractSpell> THOUSAND_CUTS = registerSpell(slash("thousand_cuts", 14, 45, 12, "iron_spell_grimoire:mercury_bullets", com.ironspellgrimoire.server.SlashFx::thousandCuts));
    public static final RegistryObject<AbstractSpell> CROSS_REAPER = registerSpell(slash("cross_reaper", 16, 55, 12, "iron_spell_grimoire:dark_avidya", com.ironspellgrimoire.server.SlashFx::crossReaper));
    public static final RegistryObject<AbstractSpell> BLEEDING_EDGE = registerSpell(slash("bleeding_edge", 18, 55, 12, "iron_spell_grimoire:dark_death_thrust", com.ironspellgrimoire.server.SlashFx::bleedingEdge));
    public static final RegistryObject<AbstractSpell> RIPPER_FRENZY = registerSpell(slash("ripper_frenzy", 55, 85, 8, "iron_spell_grimoire:ripper_frenzy", com.ironspellgrimoire.server.SlashFx::ripperFrenzy));
    public static final RegistryObject<AbstractSpell> BLOODY_MASSACRE = registerSpell(slash("bloody_massacre", 90, 130, 30, "iron_spell_grimoire:dark_event_horizon", com.ironspellgrimoire.server.SlashFx::bloodyMassacre));

    /** The eight Slash Grimoire spells, in the order they fill the book. */
    public static final List<RegistryObject<AbstractSpell>> SLASH_SPELLS = List.of(
            PHANTOM_SLASH, CRESCENT_CUT, SHADOW_STEP_SLASH, THOUSAND_CUTS, CROSS_REAPER, BLEEDING_EDGE, RIPPER_FRENZY, BLOODY_MASSACRE);

    // ------------------------------------------------------------------ Earth Grimoire (Sol Marron)
    private static com.ironspellgrimoire.spells.BookSpell earth(String id, int cd, int mana, float power, String anim, com.ironspellgrimoire.spells.BookSpell.Effect fx) {
        return new com.ironspellgrimoire.spells.BookSpell(id, io.redspace.ironsspellbooks.api.registry.SchoolRegistry.EVOCATION_RESOURCE,
                com.ironspellgrimoire.item.EarthGrimoireItem.class, "ui.iron_spell_grimoire.earth_grimoire_only", cd, mana, power,
                com.ironspellgrimoire.spells.BookSpell.anim(anim), fx);
    }
    public static final RegistryObject<AbstractSpell> STONE_FIST = registerSpell(earth("stone_fist", 4, 20, 11, "iron_spell_grimoire:lion_punch", com.ironspellgrimoire.server.EarthFx::stoneFist));
    public static final RegistryObject<AbstractSpell> ROCK_SPIKE = registerSpell(earth("rock_spike", 8, 30, 10, "iron_spell_grimoire:klaus_clench", com.ironspellgrimoire.server.EarthFx::rockSpike));
    public static final RegistryObject<AbstractSpell> EARTH_WALL = registerSpell(earth("earth_wall", 20, 40, 8, "iron_spell_grimoire:klaus_clench", com.ironspellgrimoire.server.EarthFx::earthWall));
    public static final RegistryObject<AbstractSpell> BOULDER_TOSS = registerSpell(earth("boulder_toss", 12, 45, 14, "iron_spell_grimoire:fire_fireball", com.ironspellgrimoire.server.EarthFx::boulderToss));
    public static final RegistryObject<AbstractSpell> QUAKE_STOMP = registerSpell(earth("quake_stomp", 18, 55, 10, "iron_spell_grimoire:lion_ground_punch", com.ironspellgrimoire.server.EarthFx::quakeStomp));
    public static final RegistryObject<AbstractSpell> STONE_GOLEM = registerSpell(earth("stone_golem", 24, 60, 13, "iron_spell_grimoire:earth_stone_golem", com.ironspellgrimoire.server.EarthFx::stoneGolem));
    public static final RegistryObject<AbstractSpell> BEDROCK_ARMOR = registerSpell(earth("bedrock_armor", 60, 90, 0, "iron_spell_grimoire:mercury_guardian", com.ironspellgrimoire.server.EarthFx::bedrockArmor));
    public static final RegistryObject<AbstractSpell> MOUNTAIN_FALL = registerSpell(earth("mountain_fall", 85, 125, 30, "iron_spell_grimoire:fire_cannon", com.ironspellgrimoire.server.EarthFx::mountainFall));

    /** The eight Earth Grimoire spells, in the order they fill the book. */
    public static final List<RegistryObject<AbstractSpell>> EARTH_SPELLS = List.of(
            STONE_FIST, ROCK_SPIKE, EARTH_WALL, BOULDER_TOSS, QUAKE_STOMP, STONE_GOLEM, BEDROCK_ARMOR, MOUNTAIN_FALL);

    // ------------------------------------------------------------------ Beast Grimoire (Vetto)
    private static com.ironspellgrimoire.spells.BookSpell beast(String id, int cd, int mana, float power, String anim, com.ironspellgrimoire.spells.BookSpell.Effect fx) {
        return new com.ironspellgrimoire.spells.BookSpell(id, io.redspace.ironsspellbooks.api.registry.SchoolRegistry.NATURE_RESOURCE,
                com.ironspellgrimoire.item.BeastGrimoireItem.class, "ui.iron_spell_grimoire.beast_grimoire_only", cd, mana, power,
                com.ironspellgrimoire.spells.BookSpell.anim(anim), fx);
    }
    public static final RegistryObject<AbstractSpell> WOLF_FANG = registerSpell(beast("wolf_fang", 4, 20, 10, "iron_spell_grimoire:lion_punch", com.ironspellgrimoire.server.BeastFx::wolfFang));
    public static final RegistryObject<AbstractSpell> HAWK_DIVE = registerSpell(beast("hawk_dive", 9, 30, 12, "iron_spell_grimoire:mercury_eagle", com.ironspellgrimoire.server.BeastFx::hawkDive));
    public static final RegistryObject<AbstractSpell> BEAR_SWIPE = registerSpell(beast("bear_swipe", 12, 40, 12, "iron_spell_grimoire:lion_barrage", com.ironspellgrimoire.server.BeastFx::bearSwipe));
    public static final RegistryObject<AbstractSpell> PANTHER_POUNCE = registerSpell(beast("panther_pounce", 10, 40, 13, "iron_spell_grimoire:klaus_lance_charge", com.ironspellgrimoire.server.BeastFx::pantherPounce));
    public static final RegistryObject<AbstractSpell> STAMPEDE = registerSpell(beast("stampede", 20, 60, 12, "iron_spell_grimoire:klaus_clench", com.ironspellgrimoire.server.BeastFx::stampede));
    public static final RegistryObject<AbstractSpell> SERPENT_SNARE = registerSpell(beast("serpent_snare", 18, 55, 10, "iron_spell_grimoire:lightning_chain", com.ironspellgrimoire.server.BeastFx::serpentSnare));
    public static final RegistryObject<AbstractSpell> BEAST_INSTINCT = registerSpell(beast("beast_instinct", 60, 90, 8, "iron_spell_grimoire:wind_spirit_dive", com.ironspellgrimoire.server.BeastFx::beastInstinct));
    public static final RegistryObject<AbstractSpell> PRIMAL_KING = registerSpell(beast("primal_king", 85, 125, 30, "iron_spell_grimoire:fire_cannon", com.ironspellgrimoire.server.BeastFx::primalKing));

    /** The eight Beast Grimoire spells, in the order they fill the book. */
    public static final List<RegistryObject<AbstractSpell>> BEAST_SPELLS = List.of(
            WOLF_FANG, HAWK_DIVE, BEAR_SWIPE, PANTHER_POUNCE, STAMPEDE, SERPENT_SNARE, BEAST_INSTINCT, PRIMAL_KING);

    // ------------------------------------------------------------------ Cotton Grimoire (Charmy Pappitson)
    private static com.ironspellgrimoire.spells.BookSpell cotton(String id, int cd, int mana, float power, String anim, com.ironspellgrimoire.spells.BookSpell.Effect fx) {
        return new com.ironspellgrimoire.spells.BookSpell(id, io.redspace.ironsspellbooks.api.registry.SchoolRegistry.HOLY_RESOURCE,
                com.ironspellgrimoire.item.CottonGrimoireItem.class, "ui.iron_spell_grimoire.cotton_grimoire_only", cd, mana, power,
                com.ironspellgrimoire.spells.BookSpell.anim(anim), fx);
    }
    public static final RegistryObject<AbstractSpell> COTTON_BALL = registerSpell(cotton("cotton_ball", 4, 20, 9, "iron_spell_grimoire:fire_fireball", com.ironspellgrimoire.server.CottonFx::cottonBall));
    public static final RegistryObject<AbstractSpell> COTTON_NET = registerSpell(cotton("cotton_net", 10, 30, 8, "iron_spell_grimoire:mercury_mirror", com.ironspellgrimoire.server.CottonFx::cottonNet));
    public static final RegistryObject<AbstractSpell> NEANS_CHARGE = registerSpell(cotton("neans_charge", 14, 45, 13, "iron_spell_grimoire:klaus_lance_charge", com.ironspellgrimoire.server.CottonFx::neansCharge));
    public static final RegistryObject<AbstractSpell> CREAM_PUFF_BARRAGE = registerSpell(cotton("cream_puff_barrage", 10, 40, 10, "iron_spell_grimoire:fire_barrage", com.ironspellgrimoire.server.CottonFx::creamPuffBarrage));
    public static final RegistryObject<AbstractSpell> SWEET_FEAST = registerSpell(cotton("sweet_feast", 30, 60, 8, "iron_spell_grimoire:mercury_guardian", com.ironspellgrimoire.server.CottonFx::sweetFeast));
    public static final RegistryObject<AbstractSpell> COTTON_CLOUD = registerSpell(cotton("cotton_cloud", 20, 55, 10, "iron_spell_grimoire:wind_sylphs_breath", com.ironspellgrimoire.server.CottonFx::cottonCloud));
    public static final RegistryObject<AbstractSpell> NEAN_ARMOR = registerSpell(cotton("nean_armor", 60, 90, 0, "iron_spell_grimoire:mercury_guardian", com.ironspellgrimoire.server.CottonFx::neanArmor));
    public static final RegistryObject<AbstractSpell> GRAND_BANQUET = registerSpell(cotton("grand_banquet", 85, 125, 30, "iron_spell_grimoire:fire_cannon", com.ironspellgrimoire.server.CottonFx::grandBanquet));

    /** The eight Cotton Grimoire spells, in the order they fill the book. */
    public static final List<RegistryObject<AbstractSpell>> COTTON_SPELLS = List.of(
            COTTON_BALL, COTTON_NET, NEANS_CHARGE, CREAM_PUFF_BARRAGE, SWEET_FEAST, COTTON_CLOUD, NEAN_ARMOR, GRAND_BANQUET);

    // ------------------------------------------------------------------ Time Grimoire (Julius Novachrono)
    private static com.ironspellgrimoire.spells.BookSpell time(String id, int cd, int mana, float power, String anim, com.ironspellgrimoire.spells.BookSpell.Effect fx) {
        return new com.ironspellgrimoire.spells.BookSpell(id, io.redspace.ironsspellbooks.api.registry.SchoolRegistry.ENDER_RESOURCE,
                com.ironspellgrimoire.item.TimeGrimoireItem.class, "ui.iron_spell_grimoire.time_grimoire_only", cd, mana, power,
                com.ironspellgrimoire.spells.BookSpell.anim(anim), fx);
    }
    public static final RegistryObject<AbstractSpell> CLOCK_HAND_BOLT = registerSpell(time("clock_hand_bolt", 3, 20, 9, "iron_spell_grimoire:julius_hand_bolt", com.ironspellgrimoire.server.TimeFx::clockHandBolt));
    public static final RegistryObject<AbstractSpell> TIME_STOP_POINT = registerSpell(time("time_stop_point", 14, 45, 6, "iron_spell_grimoire:julius_stop_point", com.ironspellgrimoire.server.TimeFx::timeStopPoint));
    public static final RegistryObject<AbstractSpell> TIME_SKIP = registerSpell(time("time_skip", 6, 25, 0, "iron_spell_grimoire:julius_time_skip", com.ironspellgrimoire.server.TimeFx::timeSkip));
    public static final RegistryObject<AbstractSpell> TIME_RECOVERY = registerSpell(time("time_recovery", 35, 80, 0, "iron_spell_grimoire:julius_time_recovery", com.ironspellgrimoire.server.TimeFx::timeRecovery));
    public static final RegistryObject<AbstractSpell> CHRONO_ACCELERATION = registerSpell(time("chrono_acceleration", 28, 55, 0, "iron_spell_grimoire:julius_acceleration", com.ironspellgrimoire.server.TimeFx::chronoAcceleration));
    public static final RegistryObject<AbstractSpell> TICKING_SNARE = registerSpell(time("ticking_snare", 12, 40, 10, "iron_spell_grimoire:julius_ticking_snare", com.ironspellgrimoire.server.TimeFx::tickingSnare));
    public static final RegistryObject<AbstractSpell> TIME_THEFT = registerSpell(time("time_theft", 16, 55, 10, "iron_spell_grimoire:julius_time_theft", com.ironspellgrimoire.server.TimeFx::timeTheft));
    public static final RegistryObject<AbstractSpell> HOURGLASS_BARRAGE = registerSpell(time("hourglass_barrage", 18, 65, 7, "iron_spell_grimoire:julius_hourglass_barrage", com.ironspellgrimoire.server.TimeFx::hourglassBarrage));
    public static final RegistryObject<AbstractSpell> REWIND_WARD = registerSpell(time("rewind_ward", 24, 50, 0, "iron_spell_grimoire:julius_rewind_ward", com.ironspellgrimoire.server.TimeFx::rewindWard));
    public static final RegistryObject<AbstractSpell> ECHO_OF_THE_PAST = registerSpell(time("echo_of_the_past", 20, 45, 0, "iron_spell_grimoire:julius_echo", com.ironspellgrimoire.server.TimeFx::echoOfThePast));
    public static final RegistryObject<AbstractSpell> CHRONOSTASIS_FIELD = registerSpell(time("chronostasis_field", 60, 100, 6, "iron_spell_grimoire:julius_chronostasis", com.ironspellgrimoire.server.TimeFx::chronostasisField));
    public static final RegistryObject<AbstractSpell> ZENITH_HOUR = registerSpell(time("zenith_hour", 90, 150, 32, "iron_spell_grimoire:julius_zenith_hour", com.ironspellgrimoire.server.TimeFx::zenithHour));

    /** The twelve Time Grimoire spells, in the order they fill the book. */
    public static final List<RegistryObject<AbstractSpell>> TIME_SPELLS = List.of(
            CLOCK_HAND_BOLT, TIME_STOP_POINT, TIME_SKIP, TIME_RECOVERY, CHRONO_ACCELERATION, TICKING_SNARE, TIME_THEFT, HOURGLASS_BARRAGE, REWIND_WARD, ECHO_OF_THE_PAST, CHRONOSTASIS_FIELD, ZENITH_HOUR);

    // ------------------------------------------------------------------ Bloodfiend Grimoire (Don Quixote, La Manchaland)
    private static com.ironspellgrimoire.spells.BookSpell blood(String id, int cd, int mana, float power, String anim, com.ironspellgrimoire.spells.BookSpell.Effect fx) {
        return new com.ironspellgrimoire.spells.BookSpell(id, io.redspace.ironsspellbooks.api.registry.SchoolRegistry.BLOOD_RESOURCE,
                com.ironspellgrimoire.item.BloodfiendGrimoireItem.class, "ui.iron_spell_grimoire.bloodfiend_grimoire_only", cd, mana, power,
                com.ironspellgrimoire.spells.BookSpell.anim(anim), fx);
    }
    public static final RegistryObject<AbstractSpell> HARDBLOOD_LANCE = registerSpell(blood("hardblood_lance", 3, 20, 10, "iron_spell_grimoire:dq_lance_thrust", com.ironspellgrimoire.server.BloodfiendFx::hardbloodLance));
    public static final RegistryObject<AbstractSpell> JUSTICE_CHARGE = registerSpell(blood("justice_charge", 8, 35, 12, "iron_spell_grimoire:dq_charge", com.ironspellgrimoire.server.BloodfiendFx::justiceCharge));
    public static final RegistryObject<AbstractSpell> SANGUINE_CRESCENT = registerSpell(blood("sanguine_crescent", 6, 30, 10, "iron_spell_grimoire:dq_crescent", com.ironspellgrimoire.server.BloodfiendFx::sanguineCrescent));
    public static final RegistryObject<AbstractSpell> CRIMSON_PACT = registerSpell(blood("crimson_pact", 20, 10, 0, "iron_spell_grimoire:dq_pact", com.ironspellgrimoire.server.BloodfiendFx::crimsonPact));
    public static final RegistryObject<AbstractSpell> THIRST = registerSpell(blood("thirst", 14, 40, 6, "iron_spell_grimoire:dq_thirst", com.ironspellgrimoire.server.BloodfiendFx::thirst));
    public static final RegistryObject<AbstractSpell> HARDBLOOD_BARRICADE = registerSpell(blood("hardblood_barricade", 16, 45, 6, "iron_spell_grimoire:dq_barricade", com.ironspellgrimoire.server.BloodfiendFx::barricade));
    public static final RegistryObject<AbstractSpell> HARDBLOOD_BOW = registerSpell(blood("hardblood_bow", 5, 25, 11, "iron_spell_grimoire:dq_bow", com.ironspellgrimoire.server.BloodfiendFx::hardbloodBow));
    public static final RegistryObject<AbstractSpell> TWIN_BLOOD_BLADES = registerSpell(blood("twin_blood_blades", 7, 35, 12, "iron_spell_grimoire:dq_twin_blades", com.ironspellgrimoire.server.BloodfiendFx::twinBlades));
    public static final RegistryObject<AbstractSpell> BLOOD_FIST_BARRAGE = registerSpell(blood("blood_fist_barrage", 9, 40, 12, "iron_spell_grimoire:dq_fist_barrage", com.ironspellgrimoire.server.BloodfiendFx::fistBarrage));
    public static final RegistryObject<AbstractSpell> HARDBLOOD_GREATSWORD = registerSpell(blood("hardblood_greatsword", 10, 45, 16, "iron_spell_grimoire:dq_greatsword", com.ironspellgrimoire.server.BloodfiendFx::greatsword));
    public static final RegistryObject<AbstractSpell> VAMPIRE_MODE = registerSpell(blood("vampire_mode", 40, 60, 0, "iron_spell_grimoire:dq_vampire", com.ironspellgrimoire.server.BloodfiendFx::vampireMode));
    public static final RegistryObject<AbstractSpell> GRAND_FINALE = registerSpell(blood("grand_finale", 80, 140, 30, "iron_spell_grimoire:dq_finale", com.ironspellgrimoire.server.BloodfiendFx::grandFinale));

    /** The twelve Bloodfiend Grimoire spells, in the order they fill the book. */
    public static final List<RegistryObject<AbstractSpell>> BLOODFIEND_SPELLS = List.of(
            HARDBLOOD_LANCE, JUSTICE_CHARGE, SANGUINE_CRESCENT, CRIMSON_PACT, THIRST, HARDBLOOD_BARRICADE, HARDBLOOD_BOW, TWIN_BLOOD_BLADES,
            BLOOD_FIST_BARRAGE, HARDBLOOD_GREATSWORD, VAMPIRE_MODE, GRAND_FINALE);

    // ------------------------------------------------------------------ Caduceus Grimoire (Rien, the Index's Proxy)
    private static com.ironspellgrimoire.spells.BookSpell cad(String id, int cd, int mana, float power, String anim, com.ironspellgrimoire.spells.BookSpell.Effect fx) {
        return new com.ironspellgrimoire.spells.BookSpell(id, io.redspace.ironsspellbooks.api.registry.SchoolRegistry.HOLY_RESOURCE,
                com.ironspellgrimoire.item.CaduceusGrimoireItem.class, "ui.iron_spell_grimoire.caduceus_grimoire_only", cd, mana, power,
                com.ironspellgrimoire.spells.BookSpell.anim(anim), fx);
    }
    public static final RegistryObject<AbstractSpell> CADUCEUS_LASH = registerSpell(cad("caduceus_lash", 3, 20, 9, "iron_spell_grimoire:rien_lash", com.ironspellgrimoire.server.CaduceusFx::lash));
    public static final RegistryObject<AbstractSpell> GILDED_RAPIER = registerSpell(cad("gilded_rapier", 5, 25, 11, "iron_spell_grimoire:rien_rapier", com.ironspellgrimoire.server.CaduceusFx::rapier));
    public static final RegistryObject<AbstractSpell> REAPERS_PRESCRIPT = registerSpell(cad("reapers_prescript", 8, 35, 14, "iron_spell_grimoire:rien_scythe", com.ironspellgrimoire.server.CaduceusFx::scythe));
    public static final RegistryObject<AbstractSpell> MOLTEN_VOLLEY = registerSpell(cad("molten_volley", 6, 30, 9, "iron_spell_grimoire:rien_volley", com.ironspellgrimoire.server.CaduceusFx::volley));
    public static final RegistryObject<AbstractSpell> INDEX_LANCE = registerSpell(cad("index_lance", 7, 30, 13, "iron_spell_grimoire:rien_lance", com.ironspellgrimoire.server.CaduceusFx::lance));
    public static final RegistryObject<AbstractSpell> GOLDEN_SHELL = registerSpell(cad("golden_shell", 18, 40, 0, "iron_spell_grimoire:rien_shell", com.ironspellgrimoire.server.CaduceusFx::shell));
    public static final RegistryObject<AbstractSpell> SEVERING_WIRE = registerSpell(cad("severing_wire", 14, 40, 6, "iron_spell_grimoire:rien_wire", com.ironspellgrimoire.server.CaduceusFx::wire));
    public static final RegistryObject<AbstractSpell> HAMMER_OF_JUDGEMENT = registerSpell(cad("hammer_of_judgement", 12, 50, 16, "iron_spell_grimoire:rien_hammer", com.ironspellgrimoire.server.CaduceusFx::hammer));
    public static final RegistryObject<AbstractSpell> PRESCRIPT_RECEIVED = registerSpell(cad("prescript_received", 40, 60, 0, "iron_spell_grimoire:rien_prescript", com.ironspellgrimoire.server.CaduceusFx::prescriptReceived));
    public static final RegistryObject<AbstractSpell> FURIOSO = registerSpell(cad("furioso", 80, 140, 30, "iron_spell_grimoire:rien_furioso", com.ironspellgrimoire.server.CaduceusFx::furioso));

    /** The ten Caduceus Grimoire spells, in the order they fill the book. */
    public static final List<RegistryObject<AbstractSpell>> CADUCEUS_SPELLS = List.of(
            CADUCEUS_LASH, GILDED_RAPIER, REAPERS_PRESCRIPT, MOLTEN_VOLLEY, INDEX_LANCE, GOLDEN_SHELL, SEVERING_WIRE, HAMMER_OF_JUDGEMENT,
            PRESCRIPT_RECEIVED, FURIOSO);

    private static RegistryObject<AbstractSpell> registerSpell(AbstractSpell spell) {
        return SPELLS.register(spell.getSpellName(), () -> spell);
    }
}
