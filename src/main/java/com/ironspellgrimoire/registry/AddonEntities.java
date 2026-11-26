package com.ironspellgrimoire.registry;

import com.ironspellgrimoire.entity.MagicBolt;
import com.ironspellgrimoire.entity.MageEnemy;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class AddonEntities {
    private AddonEntities() {}

    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES, "iron_spell_grimoire");

    public static final RegistryObject<EntityType<MagicBolt>> MAGIC_BOLT = ENTITIES.register("magic_bolt",
            () -> EntityType.Builder.<MagicBolt>of(MagicBolt::new, MobCategory.MISC).sized(0.5F, 0.5F)
                    .clientTrackingRange(6).updateInterval(1).noSave().fireImmune().build("magic_bolt"));

    public static final RegistryObject<EntityType<MageEnemy>> MAGE = ENTITIES.register("hooded_mage",
            () -> EntityType.Builder.<MageEnemy>of(MageEnemy::new, MobCategory.MONSTER).sized(0.6F, 1.95F)
                    .clientTrackingRange(8).build("hooded_mage"));

    /** Zenon: a three-phase boss of bone, space and devil power (spawn egg or /summon only). */
    public static final RegistryObject<EntityType<com.ironspellgrimoire.entity.Zenon>> ZENON = ENTITIES.register("zenon",
            () -> EntityType.Builder.<com.ironspellgrimoire.entity.Zenon>of(com.ironspellgrimoire.entity.Zenon::new, MobCategory.MONSTER).sized(0.6F, 1.95F)
                    .fireImmune().clientTrackingRange(10).build("zenon"));

    /** BeastOfDespair, Beast of Despair: a three-phase Beast Magic boss (spawn egg or /summon only). */
    public static final RegistryObject<EntityType<com.ironspellgrimoire.entity.BeastOfDespair>> BEAST_OF_DESPAIR = ENTITIES.register("beast_of_despair",
            () -> EntityType.Builder.<com.ironspellgrimoire.entity.BeastOfDespair>of(com.ironspellgrimoire.entity.BeastOfDespair::new, MobCategory.MONSTER).sized(0.9F, 2.6F)
                    .fireImmune().clientTrackingRange(10).build("beast_of_despair"));

    /** Rhya the Disloyal: a three-phase Copy Magic boss that fights with your own grimoire (spawn egg or /summon only). */
    public static final RegistryObject<EntityType<com.ironspellgrimoire.entity.Rhya>> RHYA = ENTITIES.register("rhya",
            () -> EntityType.Builder.<com.ironspellgrimoire.entity.Rhya>of(com.ironspellgrimoire.entity.Rhya::new, MobCategory.MONSTER).sized(0.6F, 2.0F)
                    .fireImmune().clientTrackingRange(10).build("rhya"));


    /** Licht, Revenant and CurseWitch (entity/AddonBoss). */
    public static final RegistryObject<EntityType<com.ironspellgrimoire.entity.Licht>> LICHT = ENTITIES.register("licht",
            () -> EntityType.Builder.<com.ironspellgrimoire.entity.Licht>of(com.ironspellgrimoire.entity.Licht::new, MobCategory.MONSTER).sized(0.6F, 1.95F)
                    .fireImmune().clientTrackingRange(10).build("licht"));

    public static final RegistryObject<EntityType<com.ironspellgrimoire.entity.Revenant>> REVENANT = ENTITIES.register("revenant",
            () -> EntityType.Builder.<com.ironspellgrimoire.entity.Revenant>of(com.ironspellgrimoire.entity.Revenant::new, MobCategory.MONSTER).sized(0.6F, 1.95F)
                    .fireImmune().clientTrackingRange(10).build("revenant"));
    public static final RegistryObject<EntityType<com.ironspellgrimoire.entity.Dante>> DANTE = ENTITIES.register("dante",
            () -> EntityType.Builder.<com.ironspellgrimoire.entity.Dante>of(com.ironspellgrimoire.entity.Dante::new, MobCategory.MONSTER).sized(0.6F, 1.95F)
                    .fireImmune().clientTrackingRange(10).build("dante"));

    public static final RegistryObject<EntityType<com.ironspellgrimoire.entity.CurseWitch>> CURSE_WITCH = ENTITIES.register("curse_witch",
            () -> EntityType.Builder.<com.ironspellgrimoire.entity.CurseWitch>of(com.ironspellgrimoire.entity.CurseWitch::new, MobCategory.MONSTER).sized(0.6F, 1.9F)
                    .fireImmune().clientTrackingRange(10).build("curse_witch"));

    /** The rivals (entity/Rival, server/Rivals) and Brannoch Tor's soldiers. */
    public static final RegistryObject<EntityType<com.ironspellgrimoire.entity.SerenVale>> SEREN_VALE = ENTITIES.register("seren_vale",
            () -> EntityType.Builder.<com.ironspellgrimoire.entity.SerenVale>of(com.ironspellgrimoire.entity.SerenVale::new, MobCategory.MONSTER).sized(0.6F, 1.95F)
                    .fireImmune().clientTrackingRange(10).build("seren_vale"));
    public static final RegistryObject<EntityType<com.ironspellgrimoire.entity.BrannochTor>> BRANNOCH_TOR = ENTITIES.register("brannoch_tor",
            () -> EntityType.Builder.<com.ironspellgrimoire.entity.BrannochTor>of(com.ironspellgrimoire.entity.BrannochTor::new, MobCategory.MONSTER).sized(0.75F, 2.35F)
                    .fireImmune().clientTrackingRange(10).build("brannoch_tor"));
    public static final RegistryObject<EntityType<com.ironspellgrimoire.entity.AlderBellmar>> ALDER_BELLMAR = ENTITIES.register("alder_bellmar",
            () -> EntityType.Builder.<com.ironspellgrimoire.entity.AlderBellmar>of(com.ironspellgrimoire.entity.AlderBellmar::new, MobCategory.MONSTER).sized(0.6F, 1.95F)
                    .fireImmune().clientTrackingRange(10).build("alder_bellmar"));
    public static final RegistryObject<EntityType<com.ironspellgrimoire.entity.WrenBellmar>> WREN_BELLMAR = ENTITIES.register("wren_bellmar",
            () -> EntityType.Builder.<com.ironspellgrimoire.entity.WrenBellmar>of(com.ironspellgrimoire.entity.WrenBellmar::new, MobCategory.MONSTER).sized(0.6F, 1.95F)
                    .fireImmune().clientTrackingRange(10).build("wren_bellmar"));
    public static final RegistryObject<EntityType<com.ironspellgrimoire.entity.MiraHollowell>> MIRA_HOLLOWELL = ENTITIES.register("mira_hollowell",
            () -> EntityType.Builder.<com.ironspellgrimoire.entity.MiraHollowell>of(com.ironspellgrimoire.entity.MiraHollowell::new, MobCategory.MONSTER).sized(0.6F, 1.95F)
                    .fireImmune().clientTrackingRange(10).build("mira_hollowell"));
    public static final RegistryObject<EntityType<com.ironspellgrimoire.entity.TorLegionnaire>> TOR_LEGIONNAIRE = ENTITIES.register("tor_legionnaire",
            () -> EntityType.Builder.<com.ironspellgrimoire.entity.TorLegionnaire>of(com.ironspellgrimoire.entity.TorLegionnaire::new, MobCategory.MONSTER).sized(0.6F, 1.95F)
                    .clientTrackingRange(10).build("tor_legionnaire"));

    /** The Cinder Sovereign, the world boss (entity/CinderSovereign, server/WorldBoss). */
    public static final RegistryObject<EntityType<com.ironspellgrimoire.entity.CinderSovereign>> CINDER_SOVEREIGN = ENTITIES.register("cinder_sovereign",
            () -> EntityType.Builder.<com.ironspellgrimoire.entity.CinderSovereign>of(com.ironspellgrimoire.entity.CinderSovereign::new, MobCategory.MONSTER).sized(4.5F, 12.5F)
                    .fireImmune().clientTrackingRange(16).build("cinder_sovereign"));

    /** A Memory Guardian (entity/MemoryGuardian, server/MemoryPages). */
    public static final RegistryObject<EntityType<com.ironspellgrimoire.entity.MemoryGuardian>> MEMORY_GUARDIAN = ENTITIES.register("memory_guardian",
            () -> EntityType.Builder.<com.ironspellgrimoire.entity.MemoryGuardian>of(com.ironspellgrimoire.entity.MemoryGuardian::new, MobCategory.MONSTER).sized(0.6F, 1.8F)
                    .fireImmune().clientTrackingRange(10).build("memory_guardian"));

    /** A mana familiar (entity/FamiliarEntity, server/Familiars). */
    public static final RegistryObject<EntityType<com.ironspellgrimoire.entity.FamiliarEntity>> FAMILIAR = ENTITIES.register("familiar",
            () -> EntityType.Builder.<com.ironspellgrimoire.entity.FamiliarEntity>of(com.ironspellgrimoire.entity.FamiliarEntity::new, MobCategory.MISC).sized(0.5F, 0.5F)
                    .fireImmune().clientTrackingRange(8).updateInterval(2).build("familiar"));

    /** Pack Call's infected brutes (entity/InfectedBrute). */
    public static final RegistryObject<EntityType<com.ironspellgrimoire.entity.InfectedBrute>> INFECTED_BRUTE = ENTITIES.register("infected_brute",
            () -> EntityType.Builder.<com.ironspellgrimoire.entity.InfectedBrute>of(com.ironspellgrimoire.entity.InfectedBrute::new, MobCategory.MISC).sized(0.6F, 1.95F)
                    .clientTrackingRange(10).build("infected_brute"));

    /** The Hunger Blade thrown (entity/ThrownHungerBlade). */
    public static final RegistryObject<EntityType<com.ironspellgrimoire.entity.ThrownHungerBlade>> THROWN_HUNGER_BLADE = ENTITIES.register("thrown_hunger_blade",
            () -> EntityType.Builder.<com.ironspellgrimoire.entity.ThrownHungerBlade>of(com.ironspellgrimoire.entity.ThrownHungerBlade::new, MobCategory.MISC).sized(0.8F, 0.8F)
                    .fireImmune().clientTrackingRange(8).updateInterval(1).build("thrown_hunger_blade"));

    /** The Hunger Blade's spirit (entity/BladeSpirit). */
    public static final RegistryObject<EntityType<com.ironspellgrimoire.entity.BladeSpirit>> BLADE_SPIRIT = ENTITIES.register("blade_spirit",
            () -> EntityType.Builder.<com.ironspellgrimoire.entity.BladeSpirit>of(com.ironspellgrimoire.entity.BladeSpirit::new, MobCategory.MISC).sized(0.8F, 0.8F)
                    .fireImmune().clientTrackingRange(8).updateInterval(1).build("blade_spirit"));

    /** The Masked Twins' trial: the Dawn Warden and the Dusk Warden (entity/TwinWarden). */
    public static final RegistryObject<EntityType<com.ironspellgrimoire.entity.TwinWarden>> TWIN_WARDEN = ENTITIES.register("twin_warden",
            () -> EntityType.Builder.<com.ironspellgrimoire.entity.TwinWarden>of(com.ironspellgrimoire.entity.TwinWarden::new, MobCategory.MONSTER).sized(1.0F, 2.6F)
                    .fireImmune().clientTrackingRange(10).build("twin_warden"));

    /** The Masked Twins' Echo: the resting twin's mask, fighting beside you (entity/TwinEcho). */
    public static final RegistryObject<EntityType<com.ironspellgrimoire.entity.TwinEcho>> TWIN_ECHO = ENTITIES.register("twin_echo",
            () -> EntityType.Builder.<com.ironspellgrimoire.entity.TwinEcho>of(com.ironspellgrimoire.entity.TwinEcho::new, MobCategory.MISC).sized(0.6F, 0.6F)
                    .fireImmune().clientTrackingRange(8).updateInterval(1).build("twin_echo"));

    /** The Architect of Ruin's fortress: a block of wall or the gatehouse (entity/FortressWall), a tower (entity/FortressTower),
     *  a defender (entity/FortressDefender). None is ever saved. */
    public static final RegistryObject<EntityType<com.ironspellgrimoire.entity.EliteRift>> ELITE_RIFT = ENTITIES.register("elite_rift",
            () -> EntityType.Builder.<com.ironspellgrimoire.entity.EliteRift>of(com.ironspellgrimoire.entity.EliteRift::new, MobCategory.MISC).sized(1.2F, 2.2F)
                    .fireImmune().noSave().clientTrackingRange(8).updateInterval(10).build("elite_rift"));
    public static final RegistryObject<EntityType<com.ironspellgrimoire.entity.CursedChest>> CURSED_CHEST = ENTITIES.register("cursed_chest",
            () -> EntityType.Builder.<com.ironspellgrimoire.entity.CursedChest>of(com.ironspellgrimoire.entity.CursedChest::new, MobCategory.MISC).sized(0.9F, 0.9F)
                    .fireImmune().noSave().clientTrackingRange(8).updateInterval(5).build("cursed_chest"));
    public static final RegistryObject<EntityType<com.ironspellgrimoire.entity.FortressWall>> FORTRESS_WALL = ENTITIES.register("fortress_wall",
            () -> EntityType.Builder.<com.ironspellgrimoire.entity.FortressWall>of(com.ironspellgrimoire.entity.FortressWall::new, MobCategory.MISC).sized(1.0F, 2.6F)
                    .fireImmune().noSave().clientTrackingRange(10).updateInterval(20).build("fortress_wall"));
    public static final RegistryObject<EntityType<com.ironspellgrimoire.entity.FortressTower>> FORTRESS_TOWER = ENTITIES.register("fortress_tower",
            () -> EntityType.Builder.<com.ironspellgrimoire.entity.FortressTower>of(com.ironspellgrimoire.entity.FortressTower::new, MobCategory.MISC).sized(1.4F, 3.6F)
                    .fireImmune().noSave().clientTrackingRange(10).updateInterval(10).build("fortress_tower"));
    public static final RegistryObject<EntityType<com.ironspellgrimoire.entity.FortressDefender>> FORTRESS_DEFENDER = ENTITIES.register("fortress_defender",
            () -> EntityType.Builder.<com.ironspellgrimoire.entity.FortressDefender>of(com.ironspellgrimoire.entity.FortressDefender::new, MobCategory.MISC).sized(0.7F, 1.95F)
                    .noSave().clientTrackingRange(10).build("fortress_defender"));

    /** The Parasite Grimoire's molted husk (entity/MoltHusk). */
    public static final RegistryObject<EntityType<com.ironspellgrimoire.entity.MoltHusk>> MOLT_HUSK = ENTITIES.register("molt_husk",
            () -> EntityType.Builder.<com.ironspellgrimoire.entity.MoltHusk>of(com.ironspellgrimoire.entity.MoltHusk::new, MobCategory.MISC).sized(0.6F, 1.8F)
                    .clientTrackingRange(10).build("molt_husk"));

    /** The Parasite Grimoire's Hive Nest (entity/HiveNest). */
    public static final RegistryObject<EntityType<com.ironspellgrimoire.entity.HiveNest>> HIVE_NEST = ENTITIES.register("hive_nest",
            () -> EntityType.Builder.<com.ironspellgrimoire.entity.HiveNest>of(com.ironspellgrimoire.entity.HiveNest::new, MobCategory.MISC).sized(1.6F, 0.8F)
                    .fireImmune().clientTrackingRange(10).updateInterval(10).build("hive_nest"));

    /** A Magic Broom, ridden (entity/BroomEntity). */
    public static final RegistryObject<EntityType<com.ironspellgrimoire.entity.BroomEntity>> BROOM = ENTITIES.register("broom",
            () -> EntityType.Builder.<com.ironspellgrimoire.entity.BroomEntity>of(com.ironspellgrimoire.entity.BroomEntity::new, MobCategory.MISC).sized(0.9F, 0.4F)
                    .fireImmune().clientTrackingRange(10).updateInterval(1).build("broom"));

    /** The Ascension Trial's examiner: you, in your own skin (server/Ascension). */
    public static final RegistryObject<EntityType<com.ironspellgrimoire.entity.Reflection>> REFLECTION = ENTITIES.register("reflection",
            () -> EntityType.Builder.<com.ironspellgrimoire.entity.Reflection>of(com.ironspellgrimoire.entity.Reflection::new, MobCategory.MONSTER).sized(0.6F, 1.8F)
                    .fireImmune().clientTrackingRange(10).build("reflection"));

    /** The Grimoire of the Foreseen's False Future double (server/ForeseenFx). */
    public static final RegistryObject<EntityType<com.ironspellgrimoire.entity.ForeseenDouble>> FORESEEN_DOUBLE = ENTITIES.register("foreseen_double",
            () -> EntityType.Builder.<com.ironspellgrimoire.entity.ForeseenDouble>of(com.ironspellgrimoire.entity.ForeseenDouble::new, MobCategory.MISC).sized(0.6F, 1.8F)
                    .fireImmune().clientTrackingRange(10).updateInterval(2).build("foreseen_double"));

    /** The Grimoire of Ill Omen's Afterimage: a black-blue silhouette its mage's pursuers chase instead (server/OmenFx). */
    public static final RegistryObject<EntityType<com.ironspellgrimoire.entity.OmenAfterimage>> OMEN_AFTERIMAGE = ENTITIES.register("omen_afterimage",
            () -> EntityType.Builder.<com.ironspellgrimoire.entity.OmenAfterimage>of(com.ironspellgrimoire.entity.OmenAfterimage::new, MobCategory.MISC).sized(0.6F, 1.8F)
                    .fireImmune().clientTrackingRange(10).updateInterval(1).build("omen_afterimage"));

    /** A shadow soldier of the Shadow Grimoire: the shadow of a mob its master slew and raised (server/ShadowArmy). */
    public static final RegistryObject<EntityType<com.ironspellgrimoire.entity.ShadowSoldier>> SHADOW_SOLDIER = ENTITIES.register("shadow_soldier",
            () -> EntityType.Builder.<com.ironspellgrimoire.entity.ShadowSoldier>of(com.ironspellgrimoire.entity.ShadowSoldier::new, MobCategory.MISC).sized(0.6F, 1.9F)
                    .fireImmune().clientTrackingRange(10).build("shadow_soldier"));

    /** The Shadow Grimoire's exclusive shadow, the Oathbound Knight (an original design). */
    public static final RegistryObject<EntityType<com.ironspellgrimoire.entity.OathboundKnight>> OATHBOUND_KNIGHT = ENTITIES.register("oathbound_knight",
            () -> EntityType.Builder.<com.ironspellgrimoire.entity.OathboundKnight>of(com.ironspellgrimoire.entity.OathboundKnight::new, MobCategory.MISC).sized(0.9F, 2.9F)
                    .fireImmune().clientTrackingRange(10).build("oathbound_knight"));
    /** The Shadow Grimoire's second exclusive shadow, the Ravener (an original design). */
    public static final RegistryObject<EntityType<com.ironspellgrimoire.entity.ShadowRavener>> SHADOW_RAVENER = ENTITIES.register("ravener",
            () -> EntityType.Builder.<com.ironspellgrimoire.entity.ShadowRavener>of(com.ironspellgrimoire.entity.ShadowRavener::new, MobCategory.MISC).sized(0.9F, 2.8F)
                    .fireImmune().clientTrackingRange(10).build("ravener"));

    /** The Ironbound Twins (entity/IronboundTwin), a tier II bounty: Haldric carries the shared health. */
    public static final RegistryObject<EntityType<com.ironspellgrimoire.entity.IronboundTwin>> TWIN_HALDRIC = ENTITIES.register("twin_haldric",
            () -> EntityType.Builder.<com.ironspellgrimoire.entity.IronboundTwin>of(com.ironspellgrimoire.entity.IronboundTwin::new, MobCategory.MONSTER).sized(0.78F, 2.5F).clientTrackingRange(10).build("twin_haldric"));
    public static final RegistryObject<EntityType<com.ironspellgrimoire.entity.IronboundTwin>> TWIN_OSWIN = ENTITIES.register("twin_oswin",
            () -> EntityType.Builder.<com.ironspellgrimoire.entity.IronboundTwin>of(com.ironspellgrimoire.entity.IronboundTwin::new, MobCategory.MONSTER).sized(0.78F, 2.5F).clientTrackingRange(10).build("twin_oswin"));
    /** A fallen star (server/FallingStars): its heart, guarded until the guardians fall. */
    public static final RegistryObject<EntityType<com.ironspellgrimoire.entity.FallingStar>> FALLING_STAR = ENTITIES.register("falling_star",
            () -> EntityType.Builder.<com.ironspellgrimoire.entity.FallingStar>of(com.ironspellgrimoire.entity.FallingStar::new, MobCategory.MISC).sized(1.2F, 1.6F).clientTrackingRange(16).updateInterval(2).build("falling_star"));

    /** The wild families and the night hunter (entity/WildFolk, server/WildFolkAI): one entity type each. */
    public static final java.util.Map<com.ironspellgrimoire.entity.WildFolk.Kind, RegistryObject<EntityType<com.ironspellgrimoire.entity.WildFolk>>> FOLK = new java.util.EnumMap<>(com.ironspellgrimoire.entity.WildFolk.Kind.class);
    static {
        for (com.ironspellgrimoire.entity.WildFolk.Kind k : com.ironspellgrimoire.entity.WildFolk.Kind.values())
            FOLK.put(k, ENTITIES.register(k.id, () -> EntityType.Builder.<com.ironspellgrimoire.entity.WildFolk>of((t, l) -> new com.ironspellgrimoire.entity.WildFolk(t, l, k), MobCategory.MONSTER)
                    .sized(0.6F * k.size, 1.95F * k.size).clientTrackingRange(8).build(k.id)));
    }

    /** ASHFALL, Arc I (server/Story, StoryArc1): Corvin Hale and Ilsa Marr, Sallow's Penitents, Father Sallow. */
    public static final RegistryObject<EntityType<com.ironspellgrimoire.entity.StoryNpc>> STORY_CORVIN = ENTITIES.register("story_corvin",
            () -> EntityType.Builder.<com.ironspellgrimoire.entity.StoryNpc>of((t, l) -> new com.ironspellgrimoire.entity.StoryNpc(t, l, "corvin"), MobCategory.MISC).sized(0.66F, 2.1F).clientTrackingRange(10).build("story_corvin"));
    public static final RegistryObject<EntityType<com.ironspellgrimoire.entity.StoryNpc>> STORY_ILSA = ENTITIES.register("story_ilsa",
            () -> EntityType.Builder.<com.ironspellgrimoire.entity.StoryNpc>of((t, l) -> new com.ironspellgrimoire.entity.StoryNpc(t, l, "ilsa"), MobCategory.MISC).sized(0.57F, 1.85F).clientTrackingRange(10).build("story_ilsa"));
    public static final RegistryObject<EntityType<com.ironspellgrimoire.entity.Penitent>> PENITENT = ENTITIES.register("penitent",
            () -> EntityType.Builder.<com.ironspellgrimoire.entity.Penitent>of(com.ironspellgrimoire.entity.Penitent::new, MobCategory.MONSTER).sized(0.6F, 1.95F).clientTrackingRange(8).build("penitent"));
    public static final RegistryObject<EntityType<com.ironspellgrimoire.entity.FatherSallow>> FATHER_SALLOW = ENTITIES.register("father_sallow",
            () -> EntityType.Builder.<com.ironspellgrimoire.entity.FatherSallow>of(com.ironspellgrimoire.entity.FatherSallow::new, MobCategory.MONSTER).sized(0.78F, 2.5F).clientTrackingRange(10).build("father_sallow"));
}
