package com.ironspellgrimoire.server;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * ASHFALL's places, each party's own, built in the Rift dimension (never in your world), far from the rifts: Emberfall
 * (the burnt village where the story begins) and Father Sallow's chapel. Going in remembers where you came from, as a
 * rift does; falling there throws you back out (Rifts.onDeath), and the Journal or the chapel's door takes you back.
 */
public final class StoryStage {
    private StoryStage() {}

    public static final int EMBER = 0, CHAPEL = 1, ARENA = 2, VAULT = 3, ASHWICK = 4, TUNNELS = 5, DEEP = 6;
    public static final String[] NAMES = {"Emberfall", "Sallow's Chapel", "the Cindermarch arena", "Vey's vault", "Ashwick", "the tunnels under Ashwick", "the deep cavern"};
    static final int Y = 80, BASE = 8192, STRIDE = 2048;

    static int ox(int party) { return -(BASE + party * STRIDE); }
    static int oz(int place) { return place * 1024; }
    /** A spot in a place, from its origin (x across, z along). */
    public static Vec3 at(int party, int place, double x, double y, double z) { return new Vec3(ox(party) + x + 0.5, Y + y, oz(place) + z + 0.5); }

    /** Where a place's people and scenes gather (for finding what's in it). */
    public static Vec3 centre(int party, int place) { return at(party, place, 0, 0, place == CHAPEL ? 22 : place == VAULT ? 18 : place == ARENA ? 10 : place == TUNNELS ? -20 : 0); }
    /** Where you come in. */
    public static Vec3 spawn(int party, int place) {
        return switch (place) { case EMBER -> at(party, place, 0, 0, 18); case CHAPEL -> at(party, place, 0, 0, 3); case ARENA -> at(party, place, 0, 0, 42); case ASHWICK -> at(party, place, 0, 0, 38); case TUNNELS -> at(party, place, 0, 0, 2); case DEEP -> at(party, place, 0, 0, 18); default -> at(party, place, 0, 0, 2); };
    }

    /** Where a player stands: {party, place}, or null outside every story place. */
    public static int[] where(ServerPlayer p) {
        if (!p.level().dimension().equals(Rifts.RIFT) || p.getX() > -BASE + STRIDE / 2.0) return null;
        int party = (int) Math.round((-p.getX() - BASE) / STRIDE), place = (int) Math.round(p.getZ() / 1024);
        if (place < 0 || place > DEEP || Math.abs(p.getX() - ox(party)) > 200 || Math.abs(p.getZ() - oz(place)) > 200) return null;
        return new int[]{party, place};
    }
    public static boolean inside(ServerPlayer p, Story.Party pt, int place) { int[] w = where(p); return w != null && w[0] == pt.id && w[1] == place; }
    public static boolean inStage(ServerPlayer p) { return where(p) != null; }

    public static List<ServerPlayer> inside(MinecraftServer s, Story.Party pt, int place) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : Story.online(s, pt)) if (inside(p, pt, place)) out.add(p);
        return out;
    }

    static ServerLevel rift(MinecraftServer s) { return s.getLevel(Rifts.RIFT); }

    /** Takes p in (building the place the first time), remembering where they stood. */
    static boolean enter(ServerPlayer p, Story.Party pt, int place, Vec3 spot, float yaw) {
        ServerLevel rift = rift(p.server);
        if (rift == null) { p.displayClientMessage(net.minecraft.network.chat.Component.literal("The way won't open (the Rift dimension is missing)."), true); return false; }
        if (!pt.flag("built" + place)) { build(rift, pt.id, place); pt.set("built" + place); }
        if (!inStage(p) && Rifts.riftOf(p) == null) {
            CompoundTag ret = new CompoundTag();
            ret.putString("Dim", p.level().dimension().location().toString());
            ret.putDouble("X", p.getX()); ret.putDouble("Y", p.getY()); ret.putDouble("Z", p.getZ());
            p.getPersistentData().put(Rifts.RETURN, ret);
        }
        p.teleportTo(rift, spot.x, spot.y, spot.z, yaw, 0);
        p.fallDistance = 0;
        rift.playSound(null, BlockPos.containing(spot), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1f, 0.6f);
        return true;
    }

    /** Back out to where they came in from. */
    static void leave(ServerPlayer p) { Rifts.leave(p); }
    static void leaveIfInside(ServerPlayer p) { if (inStage(p)) leave(p); }

    // ================================================================== building
    static void set(ServerLevel sl, int x, int y, int z, BlockState s) { sl.setBlock(new BlockPos(x, y, z), s, 2); }
    static BlockState st(Block b) { return b.defaultBlockState(); }

    static void build(ServerLevel sl, int party, int place) {
        Random rnd = new Random(party * 31L + place);
        if (place == EMBER) emberfall(sl, ox(party), oz(place), rnd);
        else if (place == CHAPEL) chapel(sl, ox(party), oz(place), rnd);
        else if (place == ARENA) StoryStage2.arena(sl, ox(party), oz(place), rnd);
        else if (place == VAULT) StoryStage2.vault(sl, ox(party), oz(place), rnd);
        else if (place == ASHWICK) StoryStage3.ashwick(sl, ox(party), oz(place), rnd);
        else if (place == TUNNELS) StoryStage3.tunnels(sl, ox(party), oz(place), rnd);
        else StoryStage3.deep(sl, ox(party), oz(place), rnd);
    }

    // ---- Emberfall: a burnt village on an island of ash, a crater where the star came down, a gate to the south
    static final int[][] HOUSES = {{-15, -11}, {11, -15}, {-17, 7}, {13, 4}, {-5, -21}};

    static void emberfall(ServerLevel sl, int ox, int oz, Random rnd) {
        int R = 30;
        BlockState air = st(Blocks.AIR);
        for (int dx = -R - 1; dx <= R + 1; dx++) for (int dz = -R - 1; dz <= R + 1; dz++) {
            double rr = Math.sqrt(dx * dx + dz * dz);
            if (rr > R + 0.5) continue;
            int x = ox + dx, z = oz + dz;
            for (int y = 0; y <= 16; y++) set(sl, x, Y + y, z, air);
            set(sl, x, Y - 1, z, ash(dx, dz, rnd));
            int depth = 2 + (int) ((R + 1 - rr) * 0.45) + rnd.nextInt(2);   // the island's underside tapers away
            for (int d = 2; d <= depth; d++) set(sl, x, Y - d, z, d < 4 ? st(Blocks.TUFF) : rnd.nextInt(3) == 0 ? st(Blocks.BLACKSTONE) : st(Blocks.DEEPSLATE));
            if (rr > R - 1.5) for (int y = 0; y <= 8; y++) set(sl, x, Y + y, z, st(Blocks.BARRIER));   // the edge: nothing to see, nothing to fall off
            else if (rnd.nextInt(90) == 0 && rr > 5) set(sl, x, Y - 1, z, st(Blocks.SHROOMLIGHT));   // embers in the ash
        }
        // the road from the gate in, and the north road the Penitents come down
        for (int dz = -R + 2; dz <= R - 2; dz++) for (int dx = -1; dx <= 1; dx++)
            if (Math.abs(dz) > 6 && rnd.nextInt(3) != 0) set(sl, ox + dx, Y - 1, oz + dz, rnd.nextInt(3) == 0 ? st(Blocks.COARSE_DIRT) : rnd.nextBoolean() ? st(Blocks.GRAVEL) : st(Blocks.PACKED_MUD));
        // the crater
        for (int dx = -6; dx <= 6; dx++) for (int dz = -6; dz <= 6; dz++) {
            double rr = Math.sqrt(dx * dx + (dz + 2) * (dz + 2));
            if (rr > 6) continue;
            int x = ox + dx, z = oz + dz;
            if (rr < 4.5) { set(sl, x, Y - 1, z, air); set(sl, x, Y - 2, z, rr < 2.5 ? st(Blocks.SHROOMLIGHT) : rnd.nextBoolean() ? st(Blocks.BLACKSTONE) : st(Blocks.BASALT)); }
            else set(sl, x, Y - 1, z, rnd.nextBoolean() ? st(Blocks.BLACKSTONE) : st(Blocks.SMOOTH_BASALT));
            if (rr > 4.5 && rr < 6 && rnd.nextInt(3) == 0) set(sl, x, Y, z, rnd.nextBoolean() ? st(Blocks.BLACKSTONE) : st(Blocks.BASALT));   // the thrown-up rim
        }
        // the shard of the star, still glowing
        int sx = ox, sz = oz - 2;
        set(sl, sx, Y - 1, sz, st(Blocks.SHROOMLIGHT));
        for (int y = 0; y < 3; y++) set(sl, sx, Y + y, sz, y == 2 ? st(Blocks.ORANGE_STAINED_GLASS) : st(Blocks.SHROOMLIGHT));
        set(sl, sx + 1, Y, sz, st(Blocks.ORANGE_STAINED_GLASS)); set(sl, sx, Y, sz + 1, st(Blocks.RED_STAINED_GLASS)); set(sl, sx - 1, Y + 1, sz, st(Blocks.ORANGE_STAINED_GLASS));
        set(sl, sx, Y + 3, sz, st(Blocks.RED_STAINED_GLASS));
        for (int[] h : HOUSES) house(sl, ox + h[0], oz + h[1], rnd);
        // the gate, its beam fallen
        for (int s : new int[]{-3, 3}) for (int y = 0; y < 4; y++) set(sl, ox + s, Y + y, oz + 25, st(Blocks.STRIPPED_DARK_OAK_LOG));
        set(sl, ox - 2, Y + 3, oz + 25, st(Blocks.DARK_OAK_SLAB));
        set(sl, ox + 1, Y, oz + 26, st(Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState().getBlock()).setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS, Direction.Axis.X));
        // dead trees round the edge
        for (int i = 0; i < 9; i++) {
            double a = i * Math.PI * 2 / 9 + rnd.nextDouble() * 0.3;
            int x = ox + (int) Math.round(Math.cos(a) * 25), z = oz + (int) Math.round(Math.sin(a) * 25);
            if (Math.abs(x - ox) < 5) continue;   // not across the roads
            int h = 4 + rnd.nextInt(3);
            for (int y = 0; y < h; y++) set(sl, x, Y + y, z, st(Blocks.DARK_OAK_LOG));
            int bx = rnd.nextBoolean() ? 1 : -1;
            set(sl, x + bx, Y + h - 1, z, st(Blocks.DARK_OAK_LOG).setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS, Direction.Axis.X));
            set(sl, x + 2 * bx, Y + h, z, st(Blocks.DARK_OAK_FENCE));
        }
        // a fallen lantern here and there
        for (int[] l : new int[][]{{6, 10}, {-9, -3}, {4, -12}, {-3, 15}}) set(sl, ox + l[0], Y, oz + l[1], st(Blocks.LANTERN));
    }

    static BlockState ash(Random rnd) { return ash(rnd.nextInt(1000), rnd.nextInt(1000), rnd); }

    /** Ash lies in drifts, not a checkerboard: smooth patches of grey (a little noise on top), soot darker in the hollows. */
    static BlockState ash(int x, int z, Random rnd) {
        double n = Math.sin(x * 0.21 + Math.cos(z * 0.13) * 2.1) + Math.sin(z * 0.19 + Math.sin(x * 0.11) * 1.7) + rnd.nextDouble() * 0.7;
        if (n > 1.5) return st(Blocks.LIGHT_GRAY_CONCRETE_POWDER);
        if (n > 0.6) return st(rnd.nextInt(5) == 0 ? Blocks.GRAVEL : Blocks.GRAY_CONCRETE_POWDER);
        if (n > -0.4) return st(rnd.nextInt(4) == 0 ? Blocks.TUFF : Blocks.GRAY_CONCRETE_POWDER);
        if (n > -1.2) return st(rnd.nextInt(3) == 0 ? Blocks.SOUL_SOIL : Blocks.TUFF);
        return st(rnd.nextInt(3) == 0 ? Blocks.BLACK_CONCRETE_POWDER : Blocks.SOUL_SOIL);
    }

    /** A burnt house: its floor, a low stone course, what's left of the walls and posts, the roof fallen in, a chimney. */
    static void house(ServerLevel sl, int x0, int z0, Random rnd) {
        int W = 7, D = 6;
        for (int dx = 0; dx < W; dx++) for (int dz = 0; dz < D; dz++) {
            int x = x0 + dx - W / 2, z = z0 + dz - D / 2;
            boolean edge = dx == 0 || dx == W - 1 || dz == 0 || dz == D - 1;
            set(sl, x, Y - 1, z, rnd.nextInt(3) == 0 ? ash(rnd) : st(Blocks.DARK_OAK_PLANKS));
            if (!edge) { if (rnd.nextInt(5) == 0) set(sl, x, Y, z, rnd.nextBoolean() ? st(Blocks.DARK_OAK_SLAB) : st(Blocks.DARK_OAK_STAIRS).setValue(StairBlock.FACING, Direction.from2DDataValue(rnd.nextInt(4)))); continue; }
            boolean corner = (dx == 0 || dx == W - 1) && (dz == 0 || dz == D - 1);
            boolean door = dz == D - 1 && dx == W / 2;
            if (corner) { int h = 2 + rnd.nextInt(3); for (int y = 0; y < h; y++) set(sl, x, Y + y, z, st(Blocks.STRIPPED_DARK_OAK_LOG)); continue; }
            if (door) continue;
            set(sl, x, Y, z, rnd.nextInt(4) == 0 ? st(Blocks.COBBLED_DEEPSLATE) : st(Blocks.BLACKSTONE));
            int h = rnd.nextInt(3);
            for (int y = 1; y <= h; y++) set(sl, x, Y + y, z, rnd.nextInt(3) == 0 ? st(Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS) : st(Blocks.POLISHED_BLACKSTONE_BRICKS));
        }
        int cx = x0 - W / 2, cz = z0 - D / 2;
        for (int y = 0; y < 5; y++) set(sl, cx, Y + y, cz, y == 4 ? st(Blocks.NETHER_BRICK_WALL) : st(Blocks.NETHER_BRICKS));   // the chimney stands
    }

    // ---- Sallow's chapel: a long nave of black stone and red glass, pews, candles, the altar, the cages behind it
    static void chapel(ServerLevel sl, int ox, int oz, Random rnd) {
        BlockState air = st(Blocks.AIR), wall = st(Blocks.POLISHED_BLACKSTONE_BRICKS), crack = st(Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS), basalt = st(Blocks.POLISHED_BASALT);
        int L = 47;
        for (int dx = -9; dx <= 9; dx++) for (int dz = -4; dz <= L + 1; dz++) {
            int x = ox + dx, z = oz + dz, ax = Math.abs(dx);
            int ceil = ax > 5 ? 11 : ax > 3 ? 12 : 13;
            for (int y = -3; y <= 15; y++) {
                BlockState s;
                boolean outer = ax >= 8 || dz <= -3 || dz >= L;
                if (y < -1) s = st(Blocks.DEEPSLATE);
                else if (y == -1) s = outer ? wall : (ax <= 1 ? st(Blocks.POLISHED_DEEPSLATE) : (dx + dz) % 7 == 0 ? st(Blocks.CRACKED_DEEPSLATE_TILES) : st(Blocks.DEEPSLATE_TILES));
                else if (outer) {
                    boolean window = ax == 8 && dz % 6 == 0 && dz > 0 && dz < L - 1 && y >= 3 && y <= 8;
                    boolean rose = dz >= L && ax <= 2 && y >= 6 && y <= 10 && !(ax == 2 && (y == 6 || y == 10));
                    boolean doorway = dz == -3 && ax <= 1 && y <= 3;
                    if (window) s = st(Blocks.RED_STAINED_GLASS);
                    else if (rose) s = ax == 0 && y == 8 ? st(Blocks.ORANGE_STAINED_GLASS) : st(Blocks.RED_STAINED_GLASS);
                    else if (doorway) s = st(Blocks.BARRIER);
                    else if (ax == 8 && dz % 6 == 3) s = basalt;
                    else s = rnd.nextInt(7) == 0 ? crack : wall;
                } else if (y > ceil) s = wall;
                else if (y == ceil) s = ax == 0 && dz % 4 == 0 ? st(Blocks.CHISELED_POLISHED_BLACKSTONE) : wall;
                else if (ax == 7 && dz % 6 == 3) s = basalt;   // pillars
                else s = air;
                set(sl, x, Y + y, z, s);
            }
        }
        // the aisle's runner
        for (int dz = 0; dz <= 30; dz++) set(sl, ox, Y, oz + dz, st(Blocks.RED_CARPET));
        // pews, facing the altar
        for (int dz = 6; dz <= 21; dz += 3) for (int dx = 2; dx <= 6; dx++) for (int s : new int[]{-1, 1})
            set(sl, ox + s * dx, Y, oz + dz, st(Blocks.DARK_OAK_STAIRS).setValue(StairBlock.FACING, Direction.NORTH));
        // candles along the walls and at the pews' ends
        for (int dz = 1; dz <= 45; dz += 4) for (int s : new int[]{-1, 1})
            if (dz % 6 != 3) set(sl, ox + s * 7, Y, oz + dz, st(Blocks.CANDLE).setValue(CandleBlock.CANDLES, 1 + rnd.nextInt(4)).setValue(CandleBlock.LIT, true));
        // lanterns hung from the vault on chains
        for (int dz = 4; dz <= 44; dz += 8) {
            for (int y = 9; y <= 12; y++) set(sl, ox, Y + y, oz + dz, st(Blocks.CHAIN));
            set(sl, ox, Y + 8, oz + dz, st(Blocks.LANTERN).setValue(LanternBlock.HANGING, true));
        }
        // the altar and its candles
        for (int dx = -2; dx <= 2; dx++) { set(sl, ox + dx, Y, oz + 44, st(Blocks.POLISHED_BLACKSTONE)); set(sl, ox + dx, Y + 1, oz + 44, Math.abs(dx) == 1 ? st(Blocks.CANDLE).setValue(CandleBlock.CANDLES, 3).setValue(CandleBlock.LIT, true) : air); }
        set(sl, ox, Y + 1, oz + 44, st(Blocks.LANTERN));
        for (int dx = -3; dx <= 3; dx++) set(sl, ox + dx, Y - 1, oz + 42, st(Blocks.CHISELED_POLISHED_BLACKSTONE));
        // the cages: iron bars round a cell, chains above; one is Ilsa's
        for (int[] c : CAGES) cage(sl, ox + c[0], oz + c[1]);
        // the door's frame, inside
        for (int y = 0; y <= 4; y++) for (int s : new int[]{-2, 2}) set(sl, ox + s, Y + y, oz - 2, basalt);
        for (int dx = -2; dx <= 2; dx++) set(sl, ox + dx, Y + 4, oz - 2, st(Blocks.CHISELED_POLISHED_BLACKSTONE));
    }

    /** The cages behind the altar (x, z from the origin): the first is Ilsa's. */
    public static final int[][] CAGES = {{5, 39}, {-5, 39}, {-5, 33}, {5, 33}};

    static void cage(ServerLevel sl, int cx, int cz) {
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            boolean in = dx == 0 && dz == 0;
            set(sl, cx + dx, Y - 1, cz + dz, st(Blocks.IRON_BLOCK));
            set(sl, cx + dx, Y + 3, cz + dz, st(Blocks.IRON_BLOCK));
            if (!in) for (int y = 0; y < 3; y++) sl.setBlock(new BlockPos(cx + dx, Y + y, cz + dz), st(Blocks.IRON_BARS), 3);
        }
        for (int y = 4; y <= 10; y++) set(sl, cx, Y + y, cz, st(Blocks.CHAIN));
    }
}
