package com.example.arcaneaddon.registry;

import com.example.arcaneaddon.item.BlackGrimoireItem;
import com.example.arcaneaddon.item.DarkGrimoireItem;
import com.example.arcaneaddon.item.DarkKatanaItem;
import com.example.arcaneaddon.item.WindGrimoireItem;
import com.example.arcaneaddon.item.MercuryGrimoireItem;
import com.example.arcaneaddon.item.FireGrimoireItem;
import com.example.arcaneaddon.item.LightningGrimoireItem;
import com.example.arcaneaddon.item.HellfireGrimoireItem;
import com.example.arcaneaddon.item.BoneGrimoireItem;
import com.example.arcaneaddon.item.SteelGrimoireItem;
import com.example.arcaneaddon.item.DemonDwellerSwordItem;
import com.example.arcaneaddon.item.DemonSlayerSwordItem;
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

    // ------------------------------------------------------------------ Mereoleona Vermillion's Flame Magic
    public static final RegistryObject<Item> HELLFIRE_GRIMOIRE = ITEMS.register("hellfire_grimoire", HellfireGrimoireItem::new);

    // ------------------------------------------------------------------ Zenon Zogratis' Bone Magic
    public static final RegistryObject<Item> BONE_GRIMOIRE = ITEMS.register("bone_grimoire", BoneGrimoireItem::new);

    // ------------------------------------------------------------------ Klaus Lunettes' Steel Magic
    public static final RegistryObject<Item> STEEL_GRIMOIRE = ITEMS.register("steel_grimoire", SteelGrimoireItem::new);

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
                ItemStack hellfire = new ItemStack(HELLFIRE_GRIMOIRE.get());
                ((IPresetSpellContainer) HELLFIRE_GRIMOIRE.get()).initializeSpellContainer(hellfire);
                out.accept(hellfire);
                ItemStack bone = new ItemStack(BONE_GRIMOIRE.get());
                ((IPresetSpellContainer) BONE_GRIMOIRE.get()).initializeSpellContainer(bone);
                out.accept(bone);
                ItemStack steel = new ItemStack(STEEL_GRIMOIRE.get());
                ((IPresetSpellContainer) STEEL_GRIMOIRE.get()).initializeSpellContainer(steel);
                out.accept(steel);
                out.accept(AddonBlocks.GRIMOIRE_ALTAR_ITEM.get());
            })
            .build());
}
