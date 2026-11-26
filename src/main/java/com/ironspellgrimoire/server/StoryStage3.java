package com.ironspellgrimoire.server;

import static com.ironspellgrimoire.server.StoryStage.Y;
import static com.ironspellgrimoire.server.StoryStage.set;
import static com.ironspellgrimoire.server.StoryStage.st;

import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * ASHFALL, Arc III's places (the Rift dimension only): Ashwick - a palisaded farming village, its well, the Mirefolk
 * camp outside its south gate and the pits to the north the Hollowfolk come up from; the tunnels under it with their
 * three hives; and the deep cavern where the Mother waits.
 */
public final class StoryStage3 {
    private StoryStage3() {}

    static final double[] ASHWICK_SPAWN = {0, 0, 38}, WELL = {0, 0, 0}, GATE = {0, 0, 16}, MAREN = {-2, 0, 14}, SEDGE = {3, 0, 27}, CORVIN = {1, 0, 13};
    static final int[][] PITS = {{-12, -30}, {0, -32}, {12, -30}};
    static final int[][] CAMP = {{-8, 26}, {8, 26}, {-5, 32}, {6, 33}};
    static final int[][] HIVES = {{-14, -12}, {14, -26}, {0, -50}};
    static final double[] TUNNEL_PAD = {0, 0, 3}, DEEP_SPAWN = {0, 0, 18}, MOTHER_DEEP = {0, 0, -6}, MOTHER_VILLAGE = {0, 0, -8};
    static final int[][] CAGES = {{-19, -4}, {-19, 4}, {19, 0}};

    // ================================================================== Ashwick
    static void ashwick(ServerLevel sl, int ox, int oz, Random rnd) {
        BlockState air = st(Blocks.AIR);
        int R = 42;
        for (int dx = -R; dx <= R; dx++) for (int dz = -R; dz <= R; dz++) {
            double r = Math.sqrt(dx * dx + dz * dz);
            if (r > R + 0.5) continue;
            int x = ox + dx, z = oz + dz;
            for (int y = 0; y <= 20; y++) set(sl, x, Y + y, z, air);
            boolean road = Math.abs(dx) <= 1 && (dz > 0 || Math.abs(dz) < 16);
            set(sl, x, Y - 1, z, road ? (rnd.nextInt(4) == 0 ? st(Blocks.COARSE_DIRT) : st(Blocks.DIRT_PATH)) : rnd.nextInt(14) == 0 ? st(Blocks.COARSE_DIRT) : st(Blocks.GRASS_BLOCK));
            int depth = 2 + (int) ((R + 1 - r) * 0.35) + rnd.nextInt(2);
            for (int d = 2; d <= depth; d++) set(sl, x, Y - d, z, d < 4 ? st(Blocks.DIRT) : st(Blocks.STONE));
            if (r > R - 1.5) for (int y = 0; y <= 10; y++) set(sl, x, Y + y, z, st(Blocks.BARRIER));
            else if (r > 17.5 && r <= 18.5) {   // the palisade
                boolean gate = Math.abs(dx) <= 2;
                set(sl, x, Y, z, gate ? air : st(Blocks.COBBLESTONE));
                for (int y = 1; y <= 4; y++) set(sl, x, Y + y, z, gate && y <= 3 ? air : st(Blocks.STRIPPED_SPRUCE_LOG));
                if (!gate && (dx + dz) % 2 == 0) set(sl, x, Y + 5, z, st(Blocks.SPRUCE_FENCE));
                if (gate) set(sl, x, Y + 4, z, st(Blocks.SPRUCE_LOG).setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
            }
        }
        // the gate posts, lanterns on them
        for (int sz : new int[]{-1, 1}) for (int sx : new int[]{-3, 3}) {
            int z = oz + sz * 18;
            for (int y = 0; y <= 5; y++) set(sl, ox + sx, Y + y, z, st(Blocks.SPRUCE_LOG));
            set(sl, ox + sx, Y + 6, z, st(Blocks.LANTERN));
        }
        // the cottages
        int[][] houses = {{-10, -8}, {10, -8}, {-11, 6}, {11, 6}, {-4, -12}, {6, -13}, {-6, 11}};
        for (int[] h : houses) cottage(sl, ox + h[0], oz + h[1], h[0] > 0 ? Direction.WEST : Direction.EAST, rnd);
        // the well at the middle: a ring of stone, a dark shaft (it goes down to the tunnels)
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            int ax = Math.abs(dx), az = Math.abs(dz);
            if (ax <= 1 && az <= 1) { for (int y = -1; y >= -5; y--) set(sl, ox + dx, Y + y, oz + dz, ax == 0 && az == 0 || y > -2 ? air : st(Blocks.MOSSY_COBBLESTONE)); }
            else { set(sl, ox + dx, Y, oz + dz, st(Blocks.MOSSY_COBBLESTONE)); set(sl, ox + dx, Y - 1, oz + dz, st(Blocks.COBBLESTONE)); }
        }
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) for (int y = -1; y >= -5; y--)
            set(sl, ox + dx, Y + y, oz + dz, (dx == 0 && dz == 0) ? air : st(Blocks.MOSSY_COBBLESTONE));
        set(sl, ox, Y - 6, oz, st(Blocks.BLACK_CONCRETE));
        for (int s : new int[]{-2, 2}) { set(sl, ox + s, Y + 1, oz, st(Blocks.SPRUCE_FENCE)); set(sl, ox + s, Y + 2, oz, st(Blocks.SPRUCE_FENCE)); }
        for (int dx = -2; dx <= 2; dx++) set(sl, ox + dx, Y + 3, oz, st(Blocks.SPRUCE_SLAB));
        // the pits to the north, where they come up
        for (int[] p : PITS) for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            boolean in = Math.abs(dx) <= 1 && Math.abs(dz) <= 1;
            for (int y = -1; y >= -6; y--) set(sl, ox + p[0] + dx, Y + y, oz + p[1] + dz, in ? (y == -6 ? st(Blocks.BLACK_CONCRETE) : air) : st(Blocks.COBBLED_DEEPSLATE));
            if (!in && rnd.nextInt(3) == 0) set(sl, ox + p[0] + dx, Y, oz + p[1] + dz, st(Blocks.SCULK));
        }
        // the Mirefolk camp outside the south gate: tents of green and brown, lanterns on poles
        for (int[] c : CAMP) tent(sl, ox + c[0], oz + c[1], rnd);
        for (int[] l : new int[][]{{-3, 24}, {3, 30}, {-10, 34}, {10, 22}}) {
            set(sl, ox + l[0], Y, oz + l[1], st(Blocks.SPRUCE_FENCE)); set(sl, ox + l[0], Y + 1, oz + l[1], st(Blocks.SPRUCE_FENCE)); set(sl, ox + l[0], Y + 2, oz + l[1], st(Blocks.LANTERN));
        }
        // hay and barrels in the fields
        for (int i = 0; i < 10; i++) {
            int x = ox + rnd.nextInt(-34, 35), z = oz + rnd.nextInt(-34, 35);
            double r = Math.hypot(x - ox, z - oz);
            if (r < 20 || r > 36 || Math.abs(x - ox) < 3) continue;
            set(sl, x, Y, z, rnd.nextBoolean() ? st(Blocks.HAY_BLOCK) : st(Blocks.BARREL));
        }
    }

    static void cottage(ServerLevel sl, int cx, int cz, Direction door, Random rnd) {
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            boolean edge = Math.abs(dx) == 2 || Math.abs(dz) == 2, corner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
            set(sl, cx + dx, Y - 1, cz + dz, st(Blocks.COBBLESTONE));
            for (int y = 0; y <= 2; y++) {
                BlockState b = !edge ? st(Blocks.AIR) : corner ? st(Blocks.SPRUCE_LOG) : y == 1 && (dx == 0 || dz == 0) ? st(Blocks.GLASS_PANE) : st(Blocks.SPRUCE_PLANKS);
                boolean isDoor = edge && !corner && y <= 1 && (door == Direction.EAST ? dx == 2 && dz == 0 : door == Direction.WEST ? dx == -2 && dz == 0 : false);
                sl.setBlock(new BlockPos(cx + dx, Y + y, cz + dz), isDoor ? st(Blocks.AIR) : b, 3);
            }
            set(sl, cx + dx, Y + 3, cz + dz, st(Blocks.DARK_OAK_SLAB).setValue(SlabBlock.TYPE, SlabType.BOTTOM));
        }
        for (int dx = -1; dx <= 1; dx++) set(sl, cx + dx, Y + 3, cz, st(Blocks.DARK_OAK_PLANKS));
        set(sl, cx, Y + 4, cz, st(Blocks.DARK_OAK_SLAB));
        set(sl, cx - 1, Y, cz - 1, st(Blocks.CRAFTING_TABLE));
        set(sl, cx + 1, Y + 2, cz + 1, st(Blocks.LANTERN).setValue(LanternBlock.HANGING, true));
    }

    static void tent(ServerLevel sl, int cx, int cz, Random rnd) {
        BlockState cloth = rnd.nextBoolean() ? st(Blocks.GREEN_WOOL) : st(Blocks.BROWN_WOOL);
        for (int dz = -2; dz <= 2; dz++) {
            set(sl, cx - 1, Y, cz + dz, cloth); set(sl, cx + 1, Y, cz + dz, cloth);
            set(sl, cx, Y + 1, cz + dz, cloth);
        }
        set(sl, cx, Y, cz - 2, st(Blocks.SPRUCE_FENCE));
        set(sl, cx, Y - 1, cz, st(Blocks.MOSS_BLOCK));
    }

    // ================================================================== the tunnels and their hives
    static void tunnels(ServerLevel sl, int ox, int oz, Random rnd) {
        // the hall where the well comes down
        room(sl, ox, oz, 4, 5, rnd, false);
        for (int y = 5; y <= 9; y++) set(sl, ox, Y + y, oz, st(Blocks.AIR));
        tube(sl, ox, oz - 3, ox, oz - 46, rnd);
        tube(sl, ox, oz - 12, ox - 10, oz - 12, rnd);
        tube(sl, ox, oz - 26, ox + 10, oz - 26, rnd);
        for (int[] h : HIVES) hive(sl, ox + h[0], oz + h[1], rnd);
    }

    /** A round chamber, radius r, height h; a hive's has comb in its walls and cells of light. */
    static void room(ServerLevel sl, int cx, int cz, int r, int h, Random rnd, boolean comb) {
        for (int dx = -r - 1; dx <= r + 1; dx++) for (int dz = -r - 1; dz <= r + 1; dz++) {
            double rr = Math.sqrt(dx * dx + dz * dz);
            if (rr > r + 1.2) continue;
            int x = cx + dx, z = cz + dz;
            int ceil = (int) Math.round(h * Math.sqrt(Math.max(0, 1 - (rr / (r + 1.0)) * (rr / (r + 1.0)))));
            for (int y = -1; y <= h + 1; y++) {
                BlockState b;
                if (y == -1) b = comb && rnd.nextInt(3) == 0 ? st(Blocks.SCULK) : rnd.nextInt(4) == 0 ? st(Blocks.TUFF) : st(Blocks.DEEPSLATE);
                else if (rr > r + 0.2 || y > ceil) b = comb && rnd.nextInt(4) == 0 ? (rnd.nextInt(5) == 0 ? st(Blocks.SEA_LANTERN) : st(Blocks.DARK_PRISMARINE)) : rnd.nextInt(5) == 0 ? st(Blocks.COBBLED_DEEPSLATE) : st(Blocks.DEEPSLATE);
                else b = st(Blocks.AIR);
                if (y > ceil + 1 && rr <= r + 0.2) continue;
                set(sl, x, Y + y, z, b);
            }
        }
    }

    static void hive(ServerLevel sl, int cx, int cz, Random rnd) {
        room(sl, cx, cz, 6, 6, rnd, true);
        hiveCore(sl, cx, cz, false);
    }

    /** The hive's heart: a knot of comb round a core of light - or, burnt out, a knot of ash. */
    static void hiveCore(ServerLevel sl, int cx, int cz, boolean burnt) {
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) for (int y = 0; y <= 2; y++) {
            boolean core = dx == 0 && dz == 0 && y == 1, edge = Math.abs(dx) + Math.abs(dz) == 2 && y != 1;
            BlockState b = edge ? st(Blocks.AIR) : core ? (burnt ? st(Blocks.MAGMA_BLOCK) : st(Blocks.SEA_LANTERN)) : burnt ? (y == 2 ? st(Blocks.BLACKSTONE) : st(Blocks.BASALT)) : (y == 2 ? st(Blocks.PRISMARINE_BRICKS) : st(Blocks.DARK_PRISMARINE));
            set(sl, cx + dx, Y + y, cz + dz, b);
        }
        set(sl, cx, Y + 3, cz, burnt ? st(Blocks.AIR) : st(Blocks.PRISMARINE));
    }

    static void tube(ServerLevel sl, int x0, int z0, int x1, int z1, Random rnd) {
        int n = Math.max(Math.abs(x1 - x0), Math.abs(z1 - z0));
        boolean alongX = x1 != x0;
        for (int i = 0; i <= n; i++) {
            int x = x0 + Integer.signum(x1 - x0) * i, z = z0 + Integer.signum(z1 - z0) * i;
            for (int w = -2; w <= 2; w++) for (int y = -1; y <= 4; y++) {
                int bx = alongX ? x : x + w, bz = alongX ? z + w : z;
                boolean in = Math.abs(w) <= 1 && y >= 0 && y <= 3;
                if (in) set(sl, bx, Y + y, bz, st(Blocks.AIR));
                else if (sl.getBlockState(new BlockPos(bx, Y + y, bz)).isAir())
                    set(sl, bx, Y + y, bz, y == -1 ? (rnd.nextInt(6) == 0 ? st(Blocks.SCULK) : st(Blocks.DEEPSLATE)) : rnd.nextInt(7) == 0 ? st(Blocks.DARK_PRISMARINE) : st(Blocks.DEEPSLATE));
            }
            if (i % 7 == 3) set(sl, x, Y + 3, z, st(Blocks.SEA_LANTERN));
        }
    }

    // ================================================================== the deep cavern
    static void deep(ServerLevel sl, int ox, int oz, Random rnd) {
        int R = 24, H = 18;
        for (int dx = -R - 2; dx <= R + 2; dx++) for (int dz = -R - 2; dz <= R + 2; dz++) {
            double r = Math.sqrt(dx * dx + dz * dz);
            if (r > R + 2.2) continue;
            int x = ox + dx, z = oz + dz;
            int ceil = r > R ? -1 : (int) Math.round(H * Math.sqrt(Math.max(0, 1 - (r / (R + 1.0)) * (r / (R + 1.0)))));
            for (int y = -3; y <= H + 2; y++) {
                BlockState b;
                if (y < -1) b = st(Blocks.DEEPSLATE);
                else if (y == -1) {
                    double n = Math.sin(dx * 0.4) + Math.cos(dz * 0.35);
                    b = r < 3 ? st(Blocks.SCULK) : n > 1.3 ? st(Blocks.SCULK) : n < -1.4 ? st(Blocks.TUFF) : rnd.nextInt(12) == 0 ? st(Blocks.SEA_LANTERN) : st(Blocks.DEEPSLATE_TILES);
                } else if (r > R || y > ceil) {
                    if (y > ceil + 2 && r <= R) continue;
                    b = rnd.nextInt(6) == 0 ? st(Blocks.DARK_PRISMARINE) : rnd.nextInt(14) == 0 ? st(Blocks.SEA_LANTERN) : st(Blocks.DEEPSLATE);
                } else b = st(Blocks.AIR);
                set(sl, x, Y + y, z, b);
            }
        }
        // pillars of stone holding the roof up, comb crawling up them
        for (int i = 0; i < 6; i++) {
            double a = i * Math.PI / 3 + 0.3;
            int px = ox + (int) Math.round(Math.cos(a) * 14), pz = oz + (int) Math.round(Math.sin(a) * 14);
            if (Math.abs(px - ox) <= 2 && pz > oz) continue;   // not across the way in
            for (int y = 0; y <= H; y++) for (int dx = 0; dx <= 1; dx++) for (int dz = 0; dz <= 1; dz++)
                set(sl, px + dx, Y + y, pz + dz, y % 4 == 0 && rnd.nextBoolean() ? st(Blocks.DARK_PRISMARINE) : st(Blocks.POLISHED_DEEPSLATE));
        }
        // crystals of light on the floor
        for (int i = 0; i < 14; i++) {
            int x = ox + rnd.nextInt(-20, 21), z = oz + rnd.nextInt(-20, 21);
            double r = Math.hypot(x - ox, z - oz);
            if (r < 8 || r > 21 || (Math.abs(x - ox) < 3 && z > oz)) continue;
            int h = 1 + rnd.nextInt(3);
            for (int y = 0; y < h; y++) set(sl, x, Y + y, z, y == h - 1 ? st(Blocks.SEA_LANTERN) : st(Blocks.DARK_PRISMARINE));
        }
        // the cages where the Mirefolk are kept
        for (int[] c : CAGES) {
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                set(sl, ox + c[0] + dx, Y - 1, oz + c[1] + dz, st(Blocks.IRON_BLOCK));
                set(sl, ox + c[0] + dx, Y + 3, oz + c[1] + dz, st(Blocks.COBBLED_DEEPSLATE));
                if (dx != 0 || dz != 0) for (int y = 0; y < 3; y++) sl.setBlock(new BlockPos(ox + c[0] + dx, Y + y, oz + c[1] + dz), st(Blocks.IRON_BARS), 3);
            }
        }
        // the way in from the tunnels, south
        for (int dz = R - 2; dz <= R + 1; dz++) for (int dx = -1; dx <= 1; dx++) for (int y = 0; y <= 3; y++) set(sl, ox + dx, Y + y, oz + dz, st(Blocks.AIR));
    }
}
