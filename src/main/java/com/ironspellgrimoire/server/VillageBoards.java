package com.ironspellgrimoire.server;

import com.ironspellgrimoire.IronSpellGrimoire;
import com.ironspellgrimoire.block.PosterBoardBlock;
import com.ironspellgrimoire.registry.AddonBlocks;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Every village gets a Poster Board and a Quest Board, put up as the village is generated: when the chunk a village
 * starts in is made, the village is noted, and as soon as the ground round its meeting place (the centre piece, where
 * the bell is) is there too - a moment later, long before anyone walks in - the boards go up on open, flat ground near
 * it, facing it, off the paths. Villages from before this (already generated) get theirs the first time a player walks
 * in. Each village is only ever given one of each, remembered by where it starts ("isg_village_boards", per dimension).
 */
@Mod.EventBusSubscriber(modid = IronSpellGrimoire.MODID)
public final class VillageBoards {
    private VillageBoards() {}

    static final class Data extends SavedData {
        final LongOpenHashSet done = new LongOpenHashSet(), doneQuest = new LongOpenHashSet();
        static Data load(CompoundTag t) {
            Data d = new Data();
            for (long l : t.getLongArray("Done")) d.done.add(l);
            for (long l : t.getLongArray("DoneQuest")) d.doneQuest.add(l);
            return d;
        }
        @Override public CompoundTag save(CompoundTag t) { t.putLongArray("Done", done.toLongArray()); t.putLongArray("DoneQuest", doneQuest.toLongArray()); return t; }
    }

    private static Data data(ServerLevel sl) { return sl.getDataStorage().computeIfAbsent(Data::load, Data::new, "isg_village_boards"); }

    /** Villages just generated, waiting for the ground round their meeting place: (dimension, start). */
    private static final java.util.Map<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>, java.util.Map<Long, StructureStart>> PENDING = new java.util.HashMap<>();

    @SubscribeEvent
    public static void onChunk(net.minecraftforge.event.level.ChunkEvent.Load e) {
        if (!e.isNewChunk() || !(e.getLevel() instanceof ServerLevel sl)) return;
        var reg = sl.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE);
        for (var en : e.getChunk().getAllStarts().entrySet()) {
            StructureStart st = en.getValue();
            if (st == null || !st.isValid() || st.getPieces().isEmpty()) continue;
            if (!reg.wrapAsHolder(en.getKey()).is(StructureTags.VILLAGE)) continue;
            PENDING.computeIfAbsent(sl.dimension(), k -> new java.util.HashMap<>()).put(st.getChunkPos().toLong(), st);
        }
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.level instanceof ServerLevel sl) || sl.getGameTime() % 10 != 3) return;
        var m = PENDING.get(sl.dimension());
        if (m == null || m.isEmpty()) return;
        Data d = data(sl);
        var it = m.entrySet().iterator();
        while (it.hasNext()) {
            var en = it.next();
            long key = en.getKey();
            boolean poster = !d.done.contains(key), quest = !d.doneQuest.contains(key);
            if (!poster && !quest) { it.remove(); continue; }
            BoundingBox centre = en.getValue().getPieces().get(0).getBoundingBox();
            int cx = (centre.minX() + centre.maxX()) / 2, cz = (centre.minZ() + centre.maxZ()) / 2;
            int half = Math.max(centre.getXSpan(), centre.getZSpan()) / 2, r = half + 11;
            boolean ready = true;
            for (int x = cx - r; x <= cx + r && ready; x += 16) for (int z = cz - r; z <= cz + r && ready; z += 16) ready = sl.hasChunk(x >> 4, z >> 4);
            if (!ready || !sl.hasChunk((cx + r) >> 4, (cz + r) >> 4)) continue;   // its ground isn't all made yet
            if (poster && place(sl, cx, cz, half, AddonBlocks.POSTER_BOARD.get())) d.done.add(key);
            if (quest && place(sl, cx, cz, half, AddonBlocks.QUEST_BOARD.get())) d.doneQuest.add(key);
            // (if no spot fit, the first player to walk in tries again with the old way)
            d.setDirty();
            it.remove();
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % 100 != 37 || p.isSpectator()) return;
        ServerLevel sl = p.serverLevel();
        StructureStart st = sl.structureManager().getStructureWithPieceAt(p.blockPosition(), StructureTags.VILLAGE);
        if (st == null || !st.isValid() || st.getPieces().isEmpty()) return;
        long key = st.getChunkPos().toLong();
        Data d = data(sl);
        boolean poster = !d.done.contains(key), quest = !d.doneQuest.contains(key);
        if (!poster && !quest) return;
        BoundingBox centre = st.getPieces().get(0).getBoundingBox();   // the meeting place
        int cx = (centre.minX() + centre.maxX()) / 2, cz = (centre.minZ() + centre.maxZ()) / 2;
        if (!sl.hasChunk(cx >> 4, cz >> 4)) return;   // wait until its middle is loaded
        int half = Math.max(centre.getXSpan(), centre.getZSpan()) / 2;
        boolean giveUp = ThreadLocalRandom.current().nextInt(6) == 0;   // nowhere fits: give up on it after a few tries
        if (poster && (place(sl, cx, cz, half, AddonBlocks.POSTER_BOARD.get()) || giveUp)) { d.done.add(key); d.setDirty(); }
        if (quest && (place(sl, cx, cz, half, AddonBlocks.QUEST_BOARD.get()) || giveUp)) { d.doneQuest.add(key); d.setDirty(); }
    }

    /** Looks for a flat, open spot in rings around the meeting place and puts the board there, facing it. */
    private static boolean place(ServerLevel sl, int cx, int cz, int half, net.minecraft.world.level.block.Block board) {
        for (int dist = half + 2; dist <= half + 9; dist++) {
            int steps = 16;
            int off = ThreadLocalRandom.current().nextInt(steps);
            for (int k = 0; k < steps; k++) {
                double a = (k + off) * Math.PI * 2 / steps;
                int x = cx + (int) Math.round(Math.cos(a) * dist), z = cz + (int) Math.round(Math.sin(a) * dist);
                if (!sl.hasChunk(x >> 4, z >> 4)) continue;
                int y = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                BlockPos pos = new BlockPos(x, y, z);
                // face the meeting place
                Direction f = Math.abs(cx - x) > Math.abs(cz - z) ? (cx > x ? Direction.EAST : Direction.WEST) : (cz > z ? Direction.SOUTH : Direction.NORTH);
                if (!fits(sl, pos, f)) continue;
                PosterBoardBlock.placeAt(sl, pos, f, board);
                return true;
            }
        }
        return false;
    }

    private static boolean fits(ServerLevel sl, BlockPos pos, Direction f) {
        for (BlockPos p : PosterBoardBlock.footprint(pos, f)) {
            if (!sl.hasChunk(p.getX() >> 4, p.getZ() >> 4) || !open(sl.getBlockState(p))) return false;
            if (p.getY() == pos.getY()) {   // the bottom row stands on solid, dry ground that isn't a path
                BlockState below = sl.getBlockState(p.below());
                if (!ground(below) || below.is(Blocks.DIRT_PATH)) return false;
                if (!open(sl.getBlockState(p.above(2)))) return false;   // and nothing hangs low over it
            }
        }
        // room in front to stand and read it
        BlockPos front = pos.relative(f);
        return open(sl.getBlockState(front)) && open(sl.getBlockState(front.above())) && sl.getBlockState(front.below()).isFaceSturdy(sl, front.below(), Direction.UP)
                && sl.getBlockState(front.below()).getFluidState().isEmpty();
    }

    /** Natural ground (grass, dirt, sand, snow) - never a roof, a floor or a field. */
    private static boolean ground(BlockState s) {
        return (s.is(BlockTags.DIRT) || s.is(BlockTags.SAND) || s.is(Blocks.SNOW_BLOCK)) && !s.is(Blocks.FARMLAND) && s.getFluidState().isEmpty();
    }

    /** Air or plants that can be grown over. */
    private static boolean open(BlockState s) {
        return s.isAir() || (s.canBeReplaced() && s.getFluidState().isEmpty());
    }
}
