package com.example.arcaneaddon.registry;

import com.example.arcaneaddon.server.DarkFx;
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
    public static final RegistryObject<AbstractSpell> DEVIL_INSTINCT_GUARD = registerSpell(new DevilSpell("devil_instinct_guard", 12, 0, Need.NONE, DevilFx::instinct));
    public static final RegistryObject<AbstractSpell> DEVIL_UNION_RELEASE = registerSpell(new DevilSpell("devil_union_release", 5, 30, Need.NONE, DevilFx::release));
    public static final RegistryObject<AbstractSpell> DEVIL_DWELLER_SLASH = registerSpell(new DevilSpell("devil_dweller_slash", 7, 18, Need.DWELLER_ANY, DevilFx::dwellerSlash));
    public static final RegistryObject<AbstractSpell> DEVIL_BORROWED_SLASH = registerSpell(new DevilSpell("devil_borrowed_slash", 12, 18, Need.DWELLER_MAIN, DevilFx::borrowedSlash));

    /** Normal spell -> its Devil Union version, slot for slot. */
    private record DevilPair(RegistryObject<AbstractSpell> normal, RegistryObject<AbstractSpell> devil) {}
    private static final List<DevilPair> DEVIL_PAIRS = List.of(
            new DevilPair(SUMMON_GREATSWORD, DEVIL_SUMMON), new DevilPair(PIERCING_DASH, DEVIL_PIERCING_DASH),
            new DevilPair(ZETTEN, DEVIL_ZETTEN), new DevilPair(SKY_SPLITTER, DEVIL_SKY_SPLITTER),
            new DevilPair(ANTI_MAGIC, DEVIL_ANTI_MAGIC), new DevilPair(KI_SENSE, DEVIL_KI_SENSE),
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
    public static final RegistryObject<AbstractSpell> BLACK_BLADE = registerSpell(new DarkSpell("black_blade", 30, 40, 0, true, DarkSpell.anim("irons_spellbooks:sword_deflect_1"), DarkFx::blackBlade));
    public static final RegistryObject<AbstractSpell> DIMENSION_SLASH = registerSpell(new DarkSpell("dimension_slash", 12, 80, 26, true, DarkSpell.anim("arcane_addon:dark_dimension_slash"), DarkFx::dimensionSlash));
    public static final RegistryObject<AbstractSpell> AVIDYA_SLASH = registerSpell(new DarkSpell("avidya_slash", 8, 50, 18, true, DarkSpell.anim("irons_spellbooks:katana_upslash"), DarkFx::avidyaSlash));
    public static final RegistryObject<AbstractSpell> WILD_SLASH = registerSpell(new DarkSpell("wild_slash", 16, 90, 12, true, DarkSpell.anim("arcane_addon:dark_wild_flurry"), DarkFx::wildSlash));
    public static final RegistryObject<AbstractSpell> DEATH_THRUST = registerSpell(new DarkSpell("death_thrust", 10, 60, 22, true, DarkSpell.anim("irons_spellbooks:simple_sword_lunge_stab"), DarkFx::deathThrust));
    public static final RegistryObject<AbstractSpell> BLACK_MOON = registerSpell(new DarkSpell("black_moon", 20, 70, 16, false, DarkSpell.anim("irons_spellbooks:instant_projectile"), DarkFx::blackMoon));
    public static final RegistryObject<AbstractSpell> BLACK_HOLE = registerSpell(new DarkSpell("black_hole", 24, 100, 10, false, DarkSpell.anim("irons_spellbooks:cast_t_pose"), DarkFx::blackHole));
    public static final RegistryObject<AbstractSpell> MANA_ZONE_DIMENSION_SLASH = registerSpell(new DarkSpell("mana_zone_dimension_slash", 45, 150, 10, false, DarkSpell.anim("irons_spellbooks:cross_arms"), DarkFx::manaZone));
    public static final RegistryObject<AbstractSpell> KI_FORESIGHT = registerSpell(new DarkSpell("ki_foresight", 18, 30, 0, false, SpellAnimations.SELF_CAST_ANIMATION, DarkFx::foresight));
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
    public static final RegistryObject<AbstractSpell> KNIGHTS_ARMOR = registerSpell(new SteelSpell("knights_armor", 40, 60, 0, false, SteelSpell.anim("arcane_addon:klaus_glasses_push"), SteelFx::knightsArmor));

    /** The eight Steel Grimoire spells, in the order they fill the book. */
    public static final List<RegistryObject<AbstractSpell>> STEEL_SPELLS = List.of(
            VIOLENT_ROTATING_LANCE, BLAZING_SPIRAL_LANCE, STEEL_CASTLE_WALL, WHITE_STEEL_HORSE_CART,
            THUNDER_ARROW, ACIER_SILVA, STEEL_PRISON, KNIGHTS_ARMOR);

    /** The ten Dark Grimoire spells, in the order they fill the book. */
    public static final List<RegistryObject<AbstractSpell>> DARK_SPELLS = List.of(
            BLACK_BLADE, DIMENSION_SLASH, AVIDYA_SLASH, WILD_SLASH, DEATH_THRUST,
            BLACK_MOON, BLACK_HOLE, MANA_ZONE_DIMENSION_SLASH, KI_FORESIGHT, EVENT_HORIZON);

    private static RegistryObject<AbstractSpell> registerSpell(AbstractSpell spell) {
        return SPELLS.register(spell.getSpellName(), () -> spell);
    }
}
