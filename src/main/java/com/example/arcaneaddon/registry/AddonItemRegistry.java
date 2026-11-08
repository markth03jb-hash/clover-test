package com.example.arcaneaddon.registry;

import com.example.arcaneaddon.item.BlackGrimoireItem;
import com.example.arcaneaddon.item.DarkGrimoireItem;
import com.example.arcaneaddon.item.DarkKatanaItem;
import com.example.arcaneaddon.item.WindGrimoireItem;
import com.example.arcaneaddon.item.MercuryGrimoireItem;
import com.example.arcaneaddon.item.FireGrimoireItem;
import com.example.arcaneaddon.item.LightningGrimoireItem;
import com.example.arcaneaddon.item.SteelGrimoireItem;
import com.example.arcaneaddon.item.DemonDwellerSwordItem;
import com.example.arcaneaddon.item.DemonSlayerSwordItem;
import com.example.arcaneaddon.item.ThemedGrimoireItem;
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
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, "arcane_addon");
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, "arcane_addon");

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

    // ------------------------------------------------------------------ Klaus Lunettes' Steel Magic
    public static final RegistryObject<Item> STEEL_GRIMOIRE = ITEMS.register("steel_grimoire", SteelGrimoireItem::new);

    // ------------------------------------------------------------------ the four new grimoires
    /** Faith, Hope and Love: the grimoire most mages receive. Passive "Hope". */
    public static final RegistryObject<Item> THREE_LEAF_GRIMOIRE = ITEMS.register("three_leaf_grimoire", () -> new ThemedGrimoireItem(
            "three_leaf_grimoire", 8, SpellRarity.RARE,
            new ThemedGrimoireItem.Stats("Three-Leaf Grimoire")
                    .add(AttributeRegistry.MAX_MANA.get(), "c3a1e5f2-0b7d-4c1e-9a31-3f1e00000101", 100, Operation.ADDITION)
                    .add(AttributeRegistry.COOLDOWN_REDUCTION.get(), "c3a1e5f2-0b7d-4c1e-9a31-3f1e00000102", 0.05, Operation.MULTIPLY_BASE)
                    .add(AttributeRegistry.SPELL_RESIST.get(), "c3a1e5f2-0b7d-4c1e-9a31-3f1e00000103", 0.05, Operation.MULTIPLY_BASE)
                    .build()));

    /** The rare fourth leaf of luck. Passive "Fortune". */
    public static final RegistryObject<Item> FOUR_LEAF_GRIMOIRE = ITEMS.register("four_leaf_grimoire", () -> new ThemedGrimoireItem(
            "four_leaf_grimoire", 10, SpellRarity.EPIC,
            new ThemedGrimoireItem.Stats("Four-Leaf Grimoire")
                    .add(AttributeRegistry.MAX_MANA.get(), "c3a1e5f2-0b7d-4c1e-9a31-3f1e00000201", 150, Operation.ADDITION)
                    .add(AttributeRegistry.SPELL_POWER.get(), "c3a1e5f2-0b7d-4c1e-9a31-3f1e00000202", 0.10, Operation.MULTIPLY_BASE)
                    .add(Attributes.LUCK, "c3a1e5f2-0b7d-4c1e-9a31-3f1e00000203", 2.0, Operation.ADDITION)
                    .build()));

    /** A codex that teaches control of the mana in the air around you. Passive "Mana Zone". */
    public static final RegistryObject<Item> MANA_ZONE_CODEX = ITEMS.register("mana_zone_codex", () -> new ThemedGrimoireItem(
            "mana_zone_codex", 10, SpellRarity.EPIC,
            new ThemedGrimoireItem.Stats("Mana Zone Codex")
                    .add(AttributeRegistry.MAX_MANA.get(), "c3a1e5f2-0b7d-4c1e-9a31-3f1e00000301", 250, Operation.ADDITION)
                    .add(AttributeRegistry.MANA_REGEN.get(), "c3a1e5f2-0b7d-4c1e-9a31-3f1e00000302", 0.15, Operation.MULTIPLY_BASE)
                    .build()));

    /** A grimoire a wind spirit has chosen to dwell in. Passive "Spirit Ward". */
    public static final RegistryObject<Item> SPIRIT_BOUND_GRIMOIRE = ITEMS.register("spirit_bound_grimoire", () -> new ThemedGrimoireItem(
            "spirit_bound_grimoire", 12, SpellRarity.LEGENDARY,
            new ThemedGrimoireItem.Stats("Spirit-Bound Grimoire")
                    .add(AttributeRegistry.MAX_MANA.get(), "c3a1e5f2-0b7d-4c1e-9a31-3f1e00000401", 200, Operation.ADDITION)
                    .add(AttributeRegistry.CAST_TIME_REDUCTION.get(), "c3a1e5f2-0b7d-4c1e-9a31-3f1e00000402", 0.15, Operation.MULTIPLY_BASE)
                    .add(AttributeRegistry.CASTING_MOVESPEED.get(), "c3a1e5f2-0b7d-4c1e-9a31-3f1e00000403", 0.25, Operation.MULTIPLY_BASE)
                    .add(Attributes.MOVEMENT_SPEED, "c3a1e5f2-0b7d-4c1e-9a31-3f1e00000404", 0.05, Operation.MULTIPLY_BASE)
                    .build()));

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("arcane_addon", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.arcane_addon"))
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
                ItemStack steel = new ItemStack(STEEL_GRIMOIRE.get());
                ((IPresetSpellContainer) STEEL_GRIMOIRE.get()).initializeSpellContainer(steel);
                out.accept(steel);
                out.accept(THREE_LEAF_GRIMOIRE.get());
                out.accept(FOUR_LEAF_GRIMOIRE.get());
                out.accept(MANA_ZONE_CODEX.get());
                out.accept(SPIRIT_BOUND_GRIMOIRE.get());
            })
            .build());
}
