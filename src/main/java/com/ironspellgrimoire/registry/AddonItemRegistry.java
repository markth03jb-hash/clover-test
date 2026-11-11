package com.ironspellgrimoire.registry;

import com.ironspellgrimoire.item.BlackGrimoireItem;
import com.ironspellgrimoire.item.DarkGrimoireItem;
import com.ironspellgrimoire.item.DarkKatanaItem;
import com.ironspellgrimoire.item.WindGrimoireItem;
import com.ironspellgrimoire.item.MercuryGrimoireItem;
import com.ironspellgrimoire.item.FireGrimoireItem;
import com.ironspellgrimoire.item.LightningGrimoireItem;
import com.ironspellgrimoire.item.HellfireGrimoireItem;
import com.ironspellgrimoire.item.BoneGrimoireItem;
import com.ironspellgrimoire.item.SteelGrimoireItem;
import com.ironspellgrimoire.item.DemonDwellerSwordItem;
import com.ironspellgrimoire.item.DemonSlayerSwordItem;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.api.spells.IPresetSpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class AddonItemRegistry {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, "iron_spell_grimoire");
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, "iron_spell_grimoire");

    public static final RegistryObject<Item> BLACK_GRIMOIRE = ITEMS.register("black_grimoire", BlackGrimoireItem::new);
    public static final RegistryObject<Item> DEMON_SLAYER_SWORD = ITEMS.register("demon_slayer_sword", DemonSlayerSwordItem::new);
    public static final RegistryObject<Item> DEMON_DWELLER_SWORD = ITEMS.register("demon_dweller_sword", DemonDwellerSwordItem::new);
    public static final RegistryObject<Item> GRIMOIRE_PAGE = ITEMS.register("grimoire_page", () -> new Item(new Item.Properties()));

    // ------------------------------------------------------------------ Yami Sukehiro's Dark Magic
    public static final RegistryObject<Item> DARK_GRIMOIRE = ITEMS.register("dark_grimoire", DarkGrimoireItem::new);
    public static final RegistryObject<Item> DARK_KATANA = ITEMS.register("dark_katana", DarkKatanaItem::new);

    // ------------------------------------------------------------------ Yuno Grinberryall's Wind Magic
    public static final RegistryObject<Item> WIND_GRIMOIRE = ITEMS.register("wind_grimoire", WindGrimoireItem::new);

    // ------------------------------------------------------------------ Nozel Silva's Mercury Magic
    public static final RegistryObject<Item> MERCURY_GRIMOIRE = ITEMS.register("mercury_grimoire", MercuryGrimoireItem::new);

    // ------------------------------------------------------------------ Magna Swing's Flame Magic
    public static final RegistryObject<Item> FIRE_GRIMOIRE = ITEMS.register("fire_grimoire", FireGrimoireItem::new);

    // ------------------------------------------------------------------ Luck Voltia's Lightning Magic
    public static final RegistryObject<Item> LIGHTNING_GRIMOIRE = ITEMS.register("lightning_grimoire", LightningGrimoireItem::new);

    // ------------------------------------------------------------------ Mereoleona Vermillion's Flame Magic
    public static final RegistryObject<Item> HELLFIRE_GRIMOIRE = ITEMS.register("hellfire_grimoire", HellfireGrimoireItem::new);

    // ------------------------------------------------------------------ Zenon Zogratis' Bone Magic
    public static final RegistryObject<Item> BONE_GRIMOIRE = ITEMS.register("bone_grimoire", BoneGrimoireItem::new);

    // ------------------------------------------------------------------ Klaus Lunettes' Steel Magic
    public static final RegistryObject<Item> STEEL_GRIMOIRE = ITEMS.register("steel_grimoire", SteelGrimoireItem::new);

    // ------------------------------------------------------------------ Vanessa Enoteca's Thread Magic
    public static final RegistryObject<Item> THREAD_GRIMOIRE = ITEMS.register("string_grimoire", com.ironspellgrimoire.item.ThreadGrimoireItem::new);

    // ------------------------------------------------------------------ Finral Roulacase's Spatial Magic
    public static final RegistryObject<Item> SPACE_GRIMOIRE = ITEMS.register("spatial_grimoire", com.ironspellgrimoire.item.SpaceGrimoireItem::new);

    // ------------------------------------------------------------------ Noelle Silva's Water Magic
    public static final RegistryObject<Item> WATER_GRIMOIRE = ITEMS.register("water_grimoire", com.ironspellgrimoire.item.WaterGrimoireItem::new);

    // ------------------------------------------------------------------ Gauche Adlai's Mirror Magic
    public static final RegistryObject<Item> MIRROR_GRIMOIRE = ITEMS.register("mirror_grimoire", com.ironspellgrimoire.item.MirrorGrimoireItem::new);

    // ------------------------------------------------------------------ Langris Vaude's Rift (offensive spatial) Magic
    public static final RegistryObject<Item> RIFT_GRIMOIRE = ITEMS.register("rift_grimoire", com.ironspellgrimoire.item.RiftGrimoireItem::new);

    // ------------------------------------------------------------------ Fuegoleon Vermillion: Flame Spirit Grimoire
    public static final RegistryObject<Item> FLAME_SPIRIT_GRIMOIRE = ITEMS.register("flame_spirit_grimoire", com.ironspellgrimoire.item.FlameSpiritGrimoireItem::new);

    // ------------------------------------------------------------------ Leopold Vermillion: Lion's Flame Grimoire
    public static final RegistryObject<Item> LION_FLAME_GRIMOIRE = ITEMS.register("lions_flame_grimoire", com.ironspellgrimoire.item.LionFlameGrimoireItem::new);

    // ------------------------------------------------------------------ William Vangeance: World Tree Grimoire

    // ------------------------------------------------------------------ Jack the Ripper: Slash Grimoire
    public static final RegistryObject<Item> SLASH_GRIMOIRE = ITEMS.register("slash_grimoire", com.ironspellgrimoire.item.SlashGrimoireItem::new);

    // ------------------------------------------------------------------ Sol Marron: Earth Grimoire
    public static final RegistryObject<Item> EARTH_GRIMOIRE = ITEMS.register("earth_grimoire", com.ironspellgrimoire.item.EarthGrimoireItem::new);

    // ------------------------------------------------------------------ BeastOfDespair: Beast Grimoire

    // ------------------------------------------------------------------ Charmy Pappitson: Cotton Grimoire

    // ------------------------------------------------------------------ Julius Novachrono: Time Grimoire
    public static final RegistryObject<Item> TIME_GRIMOIRE = ITEMS.register("time_grimoire", com.ironspellgrimoire.item.TimeGrimoireItem::new);

    // ------------------------------------------------------------------ Patolli: Light Grimoire
    public static final RegistryObject<Item> LIGHT_GRIMOIRE = ITEMS.register("light_grimoire", com.ironspellgrimoire.item.LightGrimoireItem::new);

    /** Zenon's drops. */
    public static final RegistryObject<Item> PHYLACTERY = ITEMS.register("zenon_phylactery", com.ironspellgrimoire.item.PhylacteryItem::new);
    public static final RegistryObject<Item> DEVIL_SHARD = ITEMS.register("devil_shard", com.ironspellgrimoire.item.DevilShardItem::new);
    /** Every boss drops one (server/BossCores); the Star Forge spends it to mutate a spell. */
    public static final RegistryObject<Item> MANA_CORE = ITEMS.register("mana_core", com.ironspellgrimoire.item.ManaCoreItem::new);
    /** One per player, given the first time they hold a grimoire (server/BroomFlight). */
    public static final RegistryObject<Item> MAGIC_BROOM = ITEMS.register("magic_broom", com.ironspellgrimoire.item.MagicBroomItem::new);
    /** Grimoire upgrades (server/GrimoireUpgrades): each boss's own, and the crafted and found ones. */
    public static final RegistryObject<Item> DESPAIR_FANG = ITEMS.register("despair_fang", () -> new com.ironspellgrimoire.item.GrimoireUpgradeItem("despair_fang", true));
    public static final RegistryObject<Item> COPIED_SIGIL = ITEMS.register("copied_sigil", () -> new com.ironspellgrimoire.item.GrimoireUpgradeItem("copied_sigil", true));
    public static final RegistryObject<Item> ELVEN_BLADE_SHARD = ITEMS.register("elven_blade_shard", () -> new com.ironspellgrimoire.item.GrimoireUpgradeItem("elven_blade_shard", true));
    public static final RegistryObject<Item> GRAVE_EMBER = ITEMS.register("grave_ember", () -> new com.ironspellgrimoire.item.GrimoireUpgradeItem("grave_ember", true));
    public static final RegistryObject<Item> CURSE_THREAD = ITEMS.register("curse_thread", () -> new com.ironspellgrimoire.item.GrimoireUpgradeItem("curse_thread", true));
    public static final RegistryObject<Item> MIRROR_SHARD = ITEMS.register("mirror_shard", () -> new com.ironspellgrimoire.item.GrimoireUpgradeItem("mirror_shard", true));
    public static final RegistryObject<Item> MANA_CRYSTAL_BINDING = ITEMS.register("mana_crystal_binding", () -> new com.ironspellgrimoire.item.GrimoireUpgradeItem("mana_crystal_binding", false));
    public static final RegistryObject<Item> SILVER_CLASP = ITEMS.register("silver_clasp", () -> new com.ironspellgrimoire.item.GrimoireUpgradeItem("silver_clasp", false));
    public static final RegistryObject<Item> SWIFT_BOOKMARK = ITEMS.register("swift_bookmark", () -> new com.ironspellgrimoire.item.GrimoireUpgradeItem("swift_bookmark", false));
    public static final RegistryObject<Item> RUNED_SPINE = ITEMS.register("runed_spine", () -> new com.ironspellgrimoire.item.GrimoireUpgradeItem("runed_spine", false));
    public static final RegistryObject<Item> PHOENIX_RIBBON = ITEMS.register("phoenix_ribbon", () -> new com.ironspellgrimoire.item.GrimoireUpgradeItem("phoenix_ribbon", false));
    public static final RegistryObject<Item> WIND_FEATHER = ITEMS.register("wind_feather", () -> new com.ironspellgrimoire.item.GrimoireUpgradeItem("wind_feather", false));
    public static final RegistryObject<Item> BLOODSTONE = ITEMS.register("bloodstone", () -> new com.ironspellgrimoire.item.GrimoireUpgradeItem("bloodstone", false));
    public static final RegistryObject<Item> WARDING_GLYPH = ITEMS.register("warding_glyph", () -> new com.ironspellgrimoire.item.GrimoireUpgradeItem("warding_glyph", false));
    public static final RegistryObject<Item> STARLIT_SEAL = ITEMS.register("starlit_seal", () -> new com.ironspellgrimoire.item.GrimoireUpgradeItem("starlit_seal", false));
    public static final java.util.List<RegistryObject<Item>> UPGRADES = java.util.List.of(DESPAIR_FANG, COPIED_SIGIL, ELVEN_BLADE_SHARD, GRAVE_EMBER, CURSE_THREAD, MIRROR_SHARD, MANA_CRYSTAL_BINDING, SILVER_CLASP, SWIFT_BOOKMARK, RUNED_SPINE, PHOENIX_RIBBON, WIND_FEATHER, BLOODSTONE, WARDING_GLYPH, STARLIT_SEAL);

    /** Spawns Zenon (the three-phase bone boss). */
    public static final RegistryObject<Item> ZENON_SPAWN_EGG = ITEMS.register("zenon_spawn_egg",
            () -> new net.minecraftforge.common.ForgeSpawnEggItem(com.ironspellgrimoire.registry.AddonEntities.ZENON, 0x1A1418, 0xE8DCC4, new Item.Properties()));
    /** Spawns BeastOfDespair, Beast of Despair. */
    public static final RegistryObject<Item> BEAST_OF_DESPAIR_SPAWN_EGG = ITEMS.register("beast_of_despair_spawn_egg",
            () -> new net.minecraftforge.common.ForgeSpawnEggItem(com.ironspellgrimoire.registry.AddonEntities.BEAST_OF_DESPAIR, 0x1E3A30, 0x5CE0B0, new Item.Properties()));
    /** A Wanted Poster: a bounty on a boss, handed out by village bells (server/Bounties). */
    public static final RegistryObject<Item> WANTED_POSTER = ITEMS.register("wanted_poster", com.ironspellgrimoire.item.WantedPosterItem::new);
    /** Dungeon Rifts (server/Rifts): the compass, the ink and the relics. */
    public static final RegistryObject<Item> RIFT_COMPASS = ITEMS.register("rift_compass", com.ironspellgrimoire.item.RiftCompassItem::new);
    public static final RegistryObject<Item> RIFT_INK = ITEMS.register("rift_ink", com.ironspellgrimoire.item.RiftInkItem::new);
    public static final RegistryObject<Item> CRACKED_LENS = ITEMS.register("cracked_lens", () -> new com.ironspellgrimoire.item.RelicItem(
            "Your spells sometimes land 50% harder.", "A lens split by the rift: through it, every weak point shows."));
    public static final RegistryObject<Item> RIFT_HEART = ITEMS.register("rift_heart", () -> new com.ironspellgrimoire.item.RelicItem(
            "A blow that takes a third of your health tears a rift and throws you clear (once a minute).", "It still beats with the rift it came from."));
    public static final RegistryObject<Item> HOLLOW_BELL = ITEMS.register("hollow_bell", () -> new com.ironspellgrimoire.item.RelicItem(
            "Every tenth spell you cast costs no mana.", "It rings only for magic."));
    public static final RegistryObject<Item> SHADE_LANTERN = ITEMS.register("shade_lantern", () -> new com.ironspellgrimoire.item.RelicItem(
            "Below half health, foes near you slow down.", "Its light is darker than the dark around it."));
    public static final java.util.List<RegistryObject<Item>> RELICS = java.util.List.of(CRACKED_LENS, RIFT_HEART, HOLLOW_BELL, SHADE_LANTERN);
    public static final RegistryObject<Item> LICHT_SPAWN_EGG = ITEMS.register("licht_spawn_egg",
            () -> new net.minecraftforge.common.ForgeSpawnEggItem(com.ironspellgrimoire.registry.AddonEntities.LICHT, 0xF0EEE0, 0xE8C860, new Item.Properties()));
    public static final RegistryObject<Item> REVENANT_SPAWN_EGG = ITEMS.register("revenant_spawn_egg",
            () -> new net.minecraftforge.common.ForgeSpawnEggItem(com.ironspellgrimoire.registry.AddonEntities.REVENANT, 0x2A3A2A, 0x7AB060, new Item.Properties()));
    public static final RegistryObject<Item> CURSE_WITCH_SPAWN_EGG = ITEMS.register("curse_witch_spawn_egg",
            () -> new net.minecraftforge.common.ForgeSpawnEggItem(com.ironspellgrimoire.registry.AddonEntities.CURSE_WITCH, 0x3A1E4A, 0x8AE060, new Item.Properties()));
    /** Licht's white swords (only he holds them; they look like Asta's, in white). */
    public static final RegistryObject<Item> LICHT_SLAYER_SWORD = ITEMS.register("licht_slayer_sword", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> LICHT_DWELLER_SWORD = ITEMS.register("licht_dweller_sword", () -> new Item(new Item.Properties().stacksTo(1)));
    /** Spawns Rhya the Disloyal. */
    public static final RegistryObject<Item> RHYA_SPAWN_EGG = ITEMS.register("rhya_spawn_egg",
            () -> new net.minecraftforge.common.ForgeSpawnEggItem(com.ironspellgrimoire.registry.AddonEntities.RHYA, 0xD8D0C0, 0x7A5CD0, new Item.Properties()));


    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("iron_spell_grimoire", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.iron_spell_grimoire"))
            .icon(() -> BLACK_GRIMOIRE.get().getDefaultInstance())
            .displayItems((params, out) -> {
                ItemStack book = new ItemStack(BLACK_GRIMOIRE.get());
                ((IPresetSpellContainer) BLACK_GRIMOIRE.get()).initializeSpellContainer(book);
                out.accept(book);
                ItemStack dark = new ItemStack(DARK_GRIMOIRE.get());
                ((IPresetSpellContainer) DARK_GRIMOIRE.get()).initializeSpellContainer(dark);
                out.accept(dark);
                out.accept(DARK_KATANA.get());
                ItemStack wind = new ItemStack(WIND_GRIMOIRE.get());
                ((IPresetSpellContainer) WIND_GRIMOIRE.get()).initializeSpellContainer(wind);
                out.accept(wind);
                ItemStack mercury = new ItemStack(MERCURY_GRIMOIRE.get());
                ((IPresetSpellContainer) MERCURY_GRIMOIRE.get()).initializeSpellContainer(mercury);
                out.accept(mercury);
                ItemStack fire = new ItemStack(FIRE_GRIMOIRE.get());
                ((IPresetSpellContainer) FIRE_GRIMOIRE.get()).initializeSpellContainer(fire);
                out.accept(fire);
                ItemStack lightning = new ItemStack(LIGHTNING_GRIMOIRE.get());
                ((IPresetSpellContainer) LIGHTNING_GRIMOIRE.get()).initializeSpellContainer(lightning);
                out.accept(lightning);
                ItemStack hellfire = new ItemStack(HELLFIRE_GRIMOIRE.get());
                ((IPresetSpellContainer) HELLFIRE_GRIMOIRE.get()).initializeSpellContainer(hellfire);
                out.accept(hellfire);
                ItemStack bone = new ItemStack(BONE_GRIMOIRE.get());
                ((IPresetSpellContainer) BONE_GRIMOIRE.get()).initializeSpellContainer(bone);
                out.accept(bone);
                ItemStack steel = new ItemStack(STEEL_GRIMOIRE.get());
                ((IPresetSpellContainer) STEEL_GRIMOIRE.get()).initializeSpellContainer(steel);
                out.accept(steel);
                ItemStack thread = new ItemStack(THREAD_GRIMOIRE.get());
                ((IPresetSpellContainer) THREAD_GRIMOIRE.get()).initializeSpellContainer(thread);
                out.accept(thread);
                ItemStack space = new ItemStack(SPACE_GRIMOIRE.get());
                ((IPresetSpellContainer) SPACE_GRIMOIRE.get()).initializeSpellContainer(space);
                out.accept(space);
                for (var g : java.util.List.of(WATER_GRIMOIRE, MIRROR_GRIMOIRE, RIFT_GRIMOIRE, FLAME_SPIRIT_GRIMOIRE, LION_FLAME_GRIMOIRE, SLASH_GRIMOIRE, EARTH_GRIMOIRE, TIME_GRIMOIRE, LIGHT_GRIMOIRE)) {
                    ItemStack st = new ItemStack(g.get());
                    ((IPresetSpellContainer) g.get()).initializeSpellContainer(st);
                    out.accept(st);
                }
                out.accept(new ItemStack(ZENON_SPAWN_EGG.get()));
                out.accept(new ItemStack(BEAST_OF_DESPAIR_SPAWN_EGG.get()));
                out.accept(new ItemStack(RHYA_SPAWN_EGG.get()));
                out.accept(new ItemStack(LICHT_SPAWN_EGG.get()));
                out.accept(new ItemStack(REVENANT_SPAWN_EGG.get()));
                out.accept(new ItemStack(CURSE_WITCH_SPAWN_EGG.get()));
                out.accept(new ItemStack(RIFT_COMPASS.get()));
                for (var r : RELICS) out.accept(new ItemStack(r.get()));
                out.accept(new ItemStack(PHYLACTERY.get()));
                out.accept(new ItemStack(DEVIL_SHARD.get()));
                out.accept(new ItemStack(MANA_CORE.get()));
                out.accept(new ItemStack(MAGIC_BROOM.get()));
                for (var u : UPGRADES) out.accept(new ItemStack(u.get()));
                out.accept(AddonBlocks.GRIMOIRE_ALTAR_ITEM.get());
                out.accept(AddonBlocks.POSTER_BOARD_ITEM.get());
                out.accept(AddonBlocks.QUEST_BOARD_ITEM.get());
            })
            .build());
}
