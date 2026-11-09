package com.ironspellgrimoire.registry;

import com.ironspellgrimoire.block.GrimoireAltarBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class AddonBlocks {
    private AddonBlocks() {}

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, "iron_spell_grimoire");

    public static final RegistryObject<Block> GRIMOIRE_ALTAR = BLOCKS.register("grimoire_altar", GrimoireAltarBlock::new);
    public static final RegistryObject<Item> GRIMOIRE_ALTAR_ITEM = AddonItemRegistry.ITEMS.register("grimoire_altar",
            () -> new BlockItem(GRIMOIRE_ALTAR.get(), new Item.Properties()));

    /** Touch the class so its items are added to the item register before it is registered. */
    public static void init() {}
}
