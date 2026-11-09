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
    public static final RegistryObject<Item> WORLD_TREE_GRIMOIRE = ITEMS.register("world_tree_grimoire", com.ironspellgrimoire.item.WorldTreeGrimoireItem::new);

    // ------------------------------------------------------------------ Jack the Ripper: Slash Grimoire
    public static final RegistryObject<Item> SLASH_GRIMOIRE = ITEMS.register("slash_grimoire", com.ironspellgrimoire.item.SlashGrimoireItem::new);

    // ------------------------------------------------------------------ Sol Marron: Earth Grimoire
    public static final RegistryObject<Item> EARTH_GRIMOIRE = ITEMS.register("earth_grimoire", com.ironspellgrimoire.item.EarthGrimoireItem::new);

    // ------------------------------------------------------------------ Vetto: Beast Grimoire
    public static final RegistryObject<Item> BEAST_GRIMOIRE = ITEMS.register("beast_grimoire", com.ironspellgrimoire.item.BeastGrimoireItem::new);

    // ------------------------------------------------------------------ Charmy Pappitson: Cotton Grimoire
    public static final RegistryObject<Item> COTTON_GRIMOIRE = ITEMS.register("cotton_grimoire", com.ironspellgrimoire.item.CottonGrimoireItem::new);

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
                for (var g : java.util.List.of(WATER_GRIMOIRE, MIRROR_GRIMOIRE, RIFT_GRIMOIRE, FLAME_SPIRIT_GRIMOIRE, LION_FLAME_GRIMOIRE, WORLD_TREE_GRIMOIRE, SLASH_GRIMOIRE, EARTH_GRIMOIRE, BEAST_GRIMOIRE, COTTON_GRIMOIRE)) {
                    ItemStack st = new ItemStack(g.get());
                    ((IPresetSpellContainer) g.get()).initializeSpellContainer(st);
                    out.accept(st);
                }
                out.accept(AddonBlocks.GRIMOIRE_ALTAR_ITEM.get());
            })
            .build());
}
