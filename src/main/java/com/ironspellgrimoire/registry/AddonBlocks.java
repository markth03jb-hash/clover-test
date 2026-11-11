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

    /** The Poster Board (3 wide, 2 tall): where bounties come from. */
    public static final RegistryObject<Block> POSTER_BOARD = BLOCKS.register("poster_board", com.ironspellgrimoire.block.PosterBoardBlock::new);
    public static final RegistryObject<Block> POSTER_BOARD_PART = BLOCKS.register("poster_board_part", com.ironspellgrimoire.block.PosterBoardPartBlock::new);
    public static final RegistryObject<Item> POSTER_BOARD_ITEM = AddonItemRegistry.ITEMS.register("poster_board",
            () -> new BlockItem(POSTER_BOARD.get(), new Item.Properties()));
    /** The Quest Board: the Poster Board's frame with villagers' requests pinned to it (server/Quests). */
    public static final RegistryObject<Block> QUEST_BOARD = BLOCKS.register("quest_board", com.ironspellgrimoire.block.QuestBoardBlock::new);
    public static final RegistryObject<Item> QUEST_BOARD_ITEM = AddonItemRegistry.ITEMS.register("quest_board",
            () -> new BlockItem(QUEST_BOARD.get(), new Item.Properties()));

    /** A Grimoire Archive: the Grimoire Library's shelves (server/GrimoireLibrary). */
    public static final RegistryObject<Block> GRIMOIRE_ARCHIVE = BLOCKS.register("grimoire_archive", com.ironspellgrimoire.block.GrimoireArchiveBlock::new);
    public static final RegistryObject<Item> GRIMOIRE_ARCHIVE_ITEM = AddonItemRegistry.ITEMS.register("grimoire_archive",
            () -> new BlockItem(GRIMOIRE_ARCHIVE.get(), new Item.Properties()));

    /** A rift's door seal (server/Rifts). */
    public static final RegistryObject<Block> RIFT_SEAL = BLOCKS.register("rift_seal", com.ironspellgrimoire.block.RiftSealBlock::new);

    /** Touch the class so its items are added to the item register before it is registered. */
    public static void init() {}
}
