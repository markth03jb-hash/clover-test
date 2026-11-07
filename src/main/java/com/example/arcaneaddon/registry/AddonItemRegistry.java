package com.example.arcaneaddon.registry;

import com.example.arcaneaddon.ArcaneAddon;
import com.example.arcaneaddon.item.BlackGrimoireItem;
import com.example.arcaneaddon.item.DemonDwellerSwordItem;
import com.example.arcaneaddon.item.DemonSlayerSwordItem;
import io.redspace.ironsspellbooks.api.spells.IPresetSpellContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class AddonItemRegistry {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ArcaneAddon.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ArcaneAddon.MODID);

    /** 10 spell slots, legendary, +15% spell power, comes with Summon Greatsword. */
    public static final RegistryObject<Item> BLACK_GRIMOIRE = ITEMS.register("black_grimoire",
            BlackGrimoireItem::new);

    /** Only obtainable through the Summon Greatsword spell (not in the creative tab). */
    public static final RegistryObject<Item> DEMON_SLAYER_SWORD = ITEMS.register("demon_slayer_sword", DemonSlayerSwordItem::new);

    /** Off-hand twin blade, summoned by casting Summon Demon Slayer Sword again while holding the first one. */
    public static final RegistryObject<Item> DEMON_DWELLER_SWORD = ITEMS.register("demon_dweller_sword", DemonDwellerSwordItem::new);

    /** Render-only helper: the page sheet that flips inside the floating grimoire. Not in any tab. */
    public static final RegistryObject<Item> GRIMOIRE_PAGE = ITEMS.register("grimoire_page", () -> new Item(new Item.Properties()));

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("arcane_addon", () ->
            CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.arcane_addon"))
                    .icon(() -> BLACK_GRIMOIRE.get().getDefaultInstance())
                    .displayItems((params, out) -> {
                        ItemStack book = new ItemStack(BLACK_GRIMOIRE.get());
                        ((IPresetSpellContainer) BLACK_GRIMOIRE.get()).initializeSpellContainer(book);
                        out.accept(book);
                    })
                    .build());
}
