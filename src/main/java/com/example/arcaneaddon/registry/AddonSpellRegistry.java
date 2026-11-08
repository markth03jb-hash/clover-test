package com.example.arcaneaddon.registry;

import com.example.arcaneaddon.server.DarkFx;
import com.example.arcaneaddon.server.WindFx;
import com.example.arcaneaddon.server.MercuryFx;
import com.example.arcaneaddon.spells.MercurySpell;
import com.example.arcaneaddon.spells.WindSpell;
import com.example.arcaneaddon.server.DevilFx;
import com.example.arcaneaddon.spells.DevilSpell;
import com.example.arcaneaddon.spells.GrimoireSpell.Need;
import com.example.arcaneaddon.server.SteelFx;
import com.example.arcaneaddon.spells.SteelSpell;
import com.example.arcaneaddon.spells.AntiMagicSpell;
import com.example.arcaneaddon.spells.ArcanePulseSpell;
import com.example.arcaneaddon.spells.BorrowedSlashSpell;
import com.example.arcaneaddon.spells.DarkSpell;
import com.example.arcaneaddon.spells.DevilUnionSpell;
import com.example.arcaneaddon.spells.DwellerSlashSpell;
import com.example.arcaneaddon.spells.InstinctGuardSpell;
import com.example.arcaneaddon.spells.KiSenseSpell;
import com.example.arcaneaddon.spells.PiercingDashSpell;
import com.example.arcaneaddon.spells.SkySplitterSpell;
import com.example.arcaneaddon.spells.SummonGreatswordSpell;
import com.example.arcaneaddon.spells.ZettenSpell;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.SpellAnimations;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;

public class AddonSpellRegistry {
    public static final DeferredRegister<AbstractSpell> SPELLS = DeferredRegister.create(SpellRegistry.SPELL_REGISTRY_KEY, "arcane_addon");

    public static final RegistryObject<AbstractSpell> ARCANE_PULSE = registerSpell(new ArcanePulseSpell());
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
    public static final RegistryObject<AbstractSpell> DEVIL_BLACK_METEORITE = registerSpell(new DevilSpell("devil_black_meteorite", 40, 45, Need.ANY_MAIN, com.example.arcaneaddon.server.MeteorFx::cast));
    public static final RegistryObject<AbstractSpell> DEVIL_INSTINCT_GUARD = registerSpell(new DevilSpell("devil_instinct_guard", 12, 0, Need.NONE, DevilFx::instinct));
    public static final RegistryObject<AbstractSpell> DEVIL_UNION_RELEASE = registerSpell(new DevilSpell("devil_union_release", 5, 30, Need.NONE, DevilFx::release));
    public static final RegistryObject<AbstractSpell> DEVIL_DWELLER_SLASH = registerSpell(new DevilSpell("devil_dweller_slash", 7, 18, Need.DWELLER_ANY, DevilFx::dwellerSlash));
    public static final RegistryObject<AbstractSpell> DEVIL_BORROWED_SLASH = registerSpell(new DevilSpell("devil_borrowed_slash", 12, 18, Need.DWELLER_MAIN, DevilFx::borrowedSlash));

    /** Normal spell -> its Devil Union version, slot for slot. */
    private record DevilPair(RegistryObject<AbstractSpell> normal, RegistryObject<AbstractSpell> devil) {}
    private static final List<DevilPair> DEVIL_PAIRS = List.of(
            new DevilPair(SUMMON_GREATSWORD, DEVIL_SUMMON), new DevilPair(PIERCING_DASH, DEVIL_PIERCING_DASH),
            new DevilPair(ZETTEN, DEVIL_ZETTEN), new DevilPair(SKY_SPLITTER, DEVIL_SKY_SPLITTER),
            new DevilPair(ANTI_MAGIC, DEVIL_ANTI_MAGIC), new DevilPair(KI_SENSE, DEVIL_BLACK_METEORITE),
            new DevilPair(INSTINCT_GUARD, DEVIL_INSTINCT_GUARD), new DevilPair(DEVIL_UNION, DEVIL_UNION_RELEASE),
            new DevilPair(DWELLER_SLASH, DEVIL_DWELLER_SLASH), new DevilPair(BORROWED_SLASH, DEVIL_BORROWED_SLASH));

    /** The devil version of a Black Grimoire spell, or null if it has none. */
    public static AbstractSpell devilVersionOf(AbstractSpell spell) {
        for (DevilPair pair : DEVIL_PAIRS) if (pair.normal().get() == spell) return pair.devil().get();
        return null;
    }

    /** The normal version of a devil spell, or null if it isn't one. */
    public static AbstractSpell normalVersionOf(AbstractSpell spell) {
        for (DevilPair pair : DEVIL_PAIRS) if (pair.devil().get() == spell) return pair.normal().get();
        return null;
    }

    // ------------------------------------------------------------------ Dark Grimoire (Yami Sukehiro's Dark Magic)
    // katana = needs the Dark Katana in the main hand.  animation = the player pose played when it's cast.
    //                                                                                   id                          cd mana power katana  animation
    public static final RegistryObject<AbstractSpell> BLACK_BLADE = registerSpell(new DarkSpell("black_blade", 30, 40, 0, true, DarkSpell.anim("arcane_addon:dark_black_blade"), DarkFx::blackBlade));
    public static final RegistryObject<AbstractSpell> DIMENSION_SLASH = registerSpell(new DarkSpell("dimension_slash", 12, 80, 26, true, DarkSpell.anim("arcane_addon:dark_dimension_slash"), DarkFx::dimensionSlash));
    public static final RegistryObject<AbstractSpell> AVIDYA_SLASH = registerSpell(new DarkSpell("avidya_slash", 8, 50, 18, true, DarkSpell.anim("arcane_addon:dark_avidya"), DarkFx::avidyaSlash));
    public static final RegistryObject<AbstractSpell> BLACK_COCOON = registerSpell(new DarkSpell("black_cocoon", 20, 80, 14, false, DarkSpell.anim("arcane_addon:dark_black_cocoon"), DarkFx::blackCocoon));
    public static final RegistryObject<AbstractSpell> DEATH_THRUST = registerSpell(new DarkSpell("death_thrust", 10, 60, 22, true, DarkSpell.anim("arcane_addon:dark_death_thrust"), DarkFx::deathThrust));
    public static final RegistryObject<AbstractSpell> BLACK_MOON = registerSpell(new DarkSpell("black_moon", 20, 70, 16, false, DarkSpell.anim("arcane_addon:dark_black_moon"), DarkFx::blackMoon));
    public static final RegistryObject<AbstractSpell> BLACK_HOLE = registerSpell(new DarkSpell("black_hole", 24, 100, 10, false, DarkSpell.anim("arcane_addon:dark_black_hole"), DarkFx::blackHole));
    public static final RegistryObject<AbstractSpell> MANA_ZONE_DIMENSION_SLASH = registerSpell(new DarkSpell("mana_zone_dimension_slash", 45, 150, 10, false, DarkSpell.anim("arcane_addon:dark_mana_zone"), DarkFx::manaZone));
    public static final RegistryObject<AbstractSpell> KI_FORESIGHT = registerSpell(new DarkSpell("ki_foresight", 18, 30, 0, false, DarkSpell.anim("arcane_addon:dark_ki_read"), DarkFx::foresight));
    public static final RegistryObject<AbstractSpell> EVENT_HORIZON = registerSpell(new DarkSpell("event_horizon", 90, 300, 60, true, DarkSpell.anim("arcane_addon:dark_event_horizon"), DarkFx::eventHorizon));

    // ------------------------------------------------------------------ Steel Grimoire (Klaus Lunettes' Steel Magic)
    //                                                                                          id                       cd mana power  animation
    public static final RegistryObject<AbstractSpell> VIOLENT_ROTATING_LANCE = registerSpell(new SteelSpell("violent_rotating_lance", 8, 45, 16, false, SteelSpell.anim("irons_spellbooks:instant_projectile"), SteelFx::violentLance));
    public static final RegistryObject<AbstractSpell> BLAZING_SPIRAL_LANCE = registerSpell(new SteelSpell("blazing_spiral_lance", 14, 70, 28, false, SteelSpell.anim("arcane_addon:klaus_lance_charge"), SteelFx::spiralLance));
    public static final RegistryObject<AbstractSpell> STEEL_CASTLE_WALL = registerSpell(new SteelSpell("steel_castle_wall", 24, 80, 0, false, SteelSpell.anim("irons_spellbooks:touch_ground"), SteelFx::armoredWall));
    public static final RegistryObject<AbstractSpell> WHITE_STEEL_HORSE_CART = registerSpell(new SteelSpell("white_steel_horse_cart", 16, 60, 14, false, SteelSpell.anim("arcane_addon:klaus_cart_ride"), SteelFx::horseCart));
    public static final RegistryObject<AbstractSpell> THUNDER_ARROW = registerSpell(new SteelSpell("thunder_arrow", 30, 120, 34, false, SteelSpell.anim("arcane_addon:klaus_railgun"), SteelFx::thunderArrow));
    public static final RegistryObject<AbstractSpell> ACIER_SILVA = registerSpell(new SteelSpell("acier_silva", 20, 100, 14, false, SteelSpell.anim("irons_spellbooks:stomp"), SteelFx::acierSilva));
    public static final RegistryObject<AbstractSpell> STEEL_PRISON = registerSpell(new SteelSpell("steel_prison", 22, 70, 18, false, SteelSpell.anim("arcane_addon:klaus_clench"), SteelFx::steelPrison));
    public static final RegistryObject<AbstractSpell> KNIGHTS_ARMOR = registerSpell(new SteelSpell("knights_armor", 120, 60, 0, false, SteelSpell.anim("arcane_addon:klaus_glasses_push"), SteelFx::knightsArmor));

    /** The eight Steel Grimoire spells, in the order they fill the book. */
    public static final List<RegistryObject<AbstractSpell>> STEEL_SPELLS = List.of(
            VIOLENT_ROTATING_LANCE, BLAZING_SPIRAL_LANCE, STEEL_CASTLE_WALL, WHITE_STEEL_HORSE_CART,
            THUNDER_ARROW, ACIER_SILVA, STEEL_PRISON, KNIGHTS_ARMOR);

    // ------------------------------------------------------------------ Wind Grimoire (Yuno Grinberryall's Wind Magic)
    //                                                                                id                       cd mana power  animation
    public static final RegistryObject<AbstractSpell> WIND_BLADE_SHOWER = registerSpell(new WindSpell("wind_blade_shower", 6, 35, 9, WindSpell.anim("arcane_addon:wind_blade_shower"), WindFx::windBladeShower));
    public static final RegistryObject<AbstractSpell> TORNADO_FANG = registerSpell(new WindSpell("tornado_fang", 14, 70, 14, WindSpell.anim("arcane_addon:wind_tornado_fang"), WindFx::tornadoFang));
    public static final RegistryObject<AbstractSpell> SYLPHS_BREATH = registerSpell(new WindSpell("sylphs_breath", 8, 40, 8, WindSpell.anim("arcane_addon:wind_sylphs_breath"), WindFx::sylphsBreath));
    public static final RegistryObject<AbstractSpell> GALE_WHITE_BOW = registerSpell(new WindSpell("gale_white_bow", 7, 45, 14, WindSpell.anim("arcane_addon:wind_gale_bow"), WindFx::galeWhiteBow));
    public static final RegistryObject<AbstractSpell> HUSHED_DANCE = registerSpell(new WindSpell("hushed_dance", 18, 30, 0, WindSpell.anim("arcane_addon:wind_hushed_dance"), WindFx::hushedDance));
    public static final RegistryObject<AbstractSpell> SPIRIT_OF_ZEPHYR = registerSpell(new WindSpell("spirit_of_zephyr", 10, 60, 20, WindSpell.anim("arcane_addon:wind_zephyr"), WindFx::spiritOfZephyr));
    public static final RegistryObject<AbstractSpell> SPIRIT_OF_BOREAS = registerSpell(new WindSpell("spirit_of_boreas", 14, 70, 16, WindSpell.anim("arcane_addon:wind_boreas"), WindFx::spiritOfBoreas));
    public static final RegistryObject<AbstractSpell> SPIRIT_OF_EUROS = registerSpell(new WindSpell("spirit_of_euros", 20, 90, 34, WindSpell.anim("arcane_addon:wind_euros"), WindFx::spiritOfEuros));
    public static final RegistryObject<AbstractSpell> SPIRIT_DIVE = registerSpell(new WindSpell("spirit_dive", 90, 150, 0, WindSpell.anim("arcane_addon:wind_spirit_dive"), WindFx::spiritDive));
    public static final RegistryObject<AbstractSpell> SPIRIT_STORM = registerSpell(new WindSpell("spirit_storm", 60, 200, 40, WindSpell.anim("arcane_addon:wind_spirit_storm"), WindFx::spiritStorm));

    /** The ten Wind Grimoire spells, in the order they fill the book. */
    public static final List<RegistryObject<AbstractSpell>> WIND_SPELLS = List.of(
            WIND_BLADE_SHOWER, TORNADO_FANG, SYLPHS_BREATH, GALE_WHITE_BOW, HUSHED_DANCE,
            SPIRIT_OF_ZEPHYR, SPIRIT_OF_BOREAS, SPIRIT_OF_EUROS, SPIRIT_DIVE, SPIRIT_STORM);

    // ------------------------------------------------------------------ Mercury Grimoire (Nozel Silva's Mercury Magic)
    //                                                                                id                          cd mana power  animation
    public static final RegistryObject<AbstractSpell> MERCURY_BULLETS = registerSpell(new MercurySpell("mercury_bullets", 5, 30, 10, MercurySpell.anim("arcane_addon:mercury_bullets"), MercuryFx::mercuryBullets));
    public static final RegistryObject<AbstractSpell> MERCURY_SPEARS = registerSpell(new MercurySpell("mercury_spears", 9, 50, 16, MercurySpell.anim("arcane_addon:mercury_spears"), MercuryFx::mercurySpears));
    public static final RegistryObject<AbstractSpell> SILVER_RAIN = registerSpell(new MercurySpell("silver_rain", 16, 70, 14, MercurySpell.anim("arcane_addon:mercury_silver_rain"), MercuryFx::silverRain));
    public static final RegistryObject<AbstractSpell> STORM_OF_BLADES = registerSpell(new MercurySpell("storm_of_blades", 12, 60, 18, MercurySpell.anim("arcane_addon:mercury_storm_of_blades"), MercuryFx::stormOfBlades));
    public static final RegistryObject<AbstractSpell> MERCURY_EAGLE = registerSpell(new MercurySpell("mercury_eagle", 14, 65, 24, MercurySpell.anim("arcane_addon:mercury_eagle"), MercuryFx::mercuryEagle));
    public static final RegistryObject<AbstractSpell> SILVER_MIRROR = registerSpell(new MercurySpell("silver_mirror", 16, 40, 0, MercurySpell.anim("arcane_addon:mercury_mirror"), MercuryFx::silverMirror));
    public static final RegistryObject<AbstractSpell> MERCURY_SPHERE = registerSpell(new MercurySpell("mercury_sphere", 24, 70, 0, MercurySpell.anim("arcane_addon:mercury_sphere"), MercuryFx::mercurySphere));
    public static final RegistryObject<AbstractSpell> REGAL_SPEAR = registerSpell(new MercurySpell("regal_spear", 22, 100, 34, MercurySpell.anim("arcane_addon:mercury_regal_spear"), MercuryFx::regalSpear));
    public static final RegistryObject<AbstractSpell> SILVER_GUARDIAN = registerSpell(new MercurySpell("silver_guardian", 60, 90, 0, MercurySpell.anim("arcane_addon:mercury_guardian"), MercuryFx::silverGuardian));
    public static final RegistryObject<AbstractSpell> MERCURY_CAGE = registerSpell(new MercurySpell("mercury_cage", 40, 140, 30, MercurySpell.anim("arcane_addon:klaus_clench"), MercuryFx::mercuryCage));

    /** The ten Mercury Grimoire spells, in the order they fill the book. */
    public static final List<RegistryObject<AbstractSpell>> MERCURY_SPELLS = List.of(
            MERCURY_BULLETS, MERCURY_SPEARS, SILVER_RAIN, STORM_OF_BLADES, MERCURY_EAGLE,
            SILVER_MIRROR, MERCURY_SPHERE, REGAL_SPEAR, SILVER_GUARDIAN, MERCURY_CAGE);

    /** The ten Dark Grimoire spells, in the order they fill the book. */
    public static final List<RegistryObject<AbstractSpell>> DARK_SPELLS = List.of(
            BLACK_BLADE, DIMENSION_SLASH, AVIDYA_SLASH, BLACK_COCOON, DEATH_THRUST,
            BLACK_MOON, BLACK_HOLE, MANA_ZONE_DIMENSION_SLASH, KI_FORESIGHT, EVENT_HORIZON);

    private static RegistryObject<AbstractSpell> registerSpell(AbstractSpell spell) {
        return SPELLS.register(spell.getSpellName(), () -> spell);
    }
}
