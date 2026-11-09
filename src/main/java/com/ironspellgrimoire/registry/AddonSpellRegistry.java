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
    public static final RegistryObject<AbstractSpell> SPIRIT_STORM = registerSpell(new WindSpell("spirit_storm", 60, 130, 40, WindSpell.anim("iron_spell_grimoire:wind_spirit_storm"), WindFx::spiritStorm));

    /** The ten Wind Grimoire spells, in the order they fill the book. */
    public static final List<RegistryObject<AbstractSpell>> WIND_SPELLS = List.of(
            WIND_BLADE_SHOWER, TORNADO_FANG, SYLPHS_BREATH, GALE_WHITE_BOW, HUSHED_DANCE,
            SPIRIT_OF_ZEPHYR, SPIRIT_OF_BOREAS, SPIRIT_OF_EUROS, SPIRIT_DIVE, SPIRIT_STORM);

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
    public static final RegistryObject<AbstractSpell> BONE_SWORD = registerSpell(new BoneSpell("bone_sword", 8, 25, 22, BoneSpell.anim("iron_spell_grimoire:bone_sword_combo"), BoneFx::boneSword));
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

    private static RegistryObject<AbstractSpell> registerSpell(AbstractSpell spell) {
        return SPELLS.register(spell.getSpellName(), () -> spell);
    }
}
