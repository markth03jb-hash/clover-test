package com.ironspellgrimoire.server;

import static com.ironspellgrimoire.server.StoryStage.Y;
import static com.ironspellgrimoire.server.StoryStage.set;
import static com.ironspellgrimoire.server.StoryStage.st;

import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * ASHFALL, Arc II's places (the Rift dimension only, like the rest): the Cindermarch arena - the sand, its wall, the
 * stands rising round it, Vey's balcony, the gate tunnel and the waiting cells, and under it all the catacomb where
 * the ledger is kept - and Vey's vault, a long hall of shelves of stolen grimoires.
 */
final class StoryStage2 {
    private StoryStage2() {}

    /** Where things are in the arena (x across, y up, z along; the sand's centre at 0). */
    static final double[] ARENA_SPAWN = {0, 0, 42}, ARENA_PAD = {0, 0, 43}, CORVIN_GATE = {2, 0, 40}, TAMSIN_CELL = {5, 0, 34}, BALCONY = {0, 10, -23},
            RIVAL_START = {0, 0, -8}, BOUT_PAD = {0, 0, 0};
    /** The catacomb's three rooms (their middles, at the catacomb's floor) and where each lectern stands. */
    static final int CY = -12;
    static final int[][] ROOMS = {{-14, 0}, {14, 0}, {0, -14}};
    static final double[] VAULT_SPAWN = {0, 0, 2}, VAULT_PAD = {0, 0, 0}, VEY_VAULT = {0, 0, 32};

    static BlockState stairs(net.minecraft.world.level.block.Block b, Direction d) { return st(b).setValue(StairBlock.FACING, d); }

    // ================================================================== the arena
    static void arena(ServerLevel sl, int ox, int oz, Random rnd) {
        BlockState air = st(Blocks.AIR), wall = st(Blocks.DEEPSLATE_BRICKS), cap = st(Blocks.CHISELED_DEEPSLATE), stone = st(Blocks.STONE_BRICKS);
        for (int dx = -31; dx <= 31; dx++) for (int dz = -31; dz <= 31; dz++) {
            double r = Math.sqrt(dx * dx + dz * dz);
            if (r > 30.5) continue;
            int x = ox + dx, z = oz + dz;
            for (int y = 0; y <= 22; y++) set(sl, x, Y + y, z, air);
            if (r <= 16.5) {   // the sand: rings and spokes, the bout's circle in the middle
                BlockState f = r <= 2.2 ? st(Blocks.CHISELED_SANDSTONE) : (r > 7 && r < 8) || (r > 13 && r < 14) ? st(Blocks.CUT_RED_SANDSTONE)
                        : (dx == 0 || dz == 0) ? st(Blocks.CUT_SANDSTONE) : rnd.nextInt(5) == 0 ? st(Blocks.RED_SAND) : st(Blocks.SAND);
                set(sl, x, Y - 1, z, f);
                for (int y = 2; y <= 4; y++) set(sl, x, Y - y, z, st(Blocks.SANDSTONE));
            } else if (r <= 18.5) {   // the arena wall, banded in gold
                boolean gate = dz > 0 && Math.abs(dx) <= 2;
                for (int y = -4; y <= 3; y++) set(sl, x, Y + y, z, gate && y >= 0 && y <= 3 ? air : y == 3 ? cap : y == -1 ? st(Blocks.POLISHED_DEEPSLATE) : wall);
                if (!gate && (int) Math.round(Math.toDegrees(Math.atan2(dz, dx))) % 30 == 0) set(sl, x, Y + 3, z, st(Blocks.GOLD_BLOCK));
                if (!gate && r > 17.5 && (int) Math.round(Math.toDegrees(Math.atan2(dz, dx)) + 360) % 24 == 0) set(sl, x, Y + 4, z, st(Blocks.LANTERN));
            } else if (r <= 27.5) {   // the stands: a seat on every ring, each a step higher
                int h = (int) Math.round(r - 18.5);
                boolean tunnel = dz > 0 && Math.abs(dx) <= 2;
                for (int y = -4; y < h; y++) set(sl, x, Y + y, z, tunnel && y >= 0 && y <= 3 ? air : stone);
                if (!(tunnel && h <= 3)) {
                    Direction toward = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
                    set(sl, x, Y + h, z, stairs(Blocks.STONE_BRICK_STAIRS, toward));
                }
            } else {   // the outer wall, hung with red and black
                int deg = (int) Math.round(Math.toDegrees(Math.atan2(dz, dx)) + 360) % 360;
                boolean banner = deg % 30 < 4;
                for (int y = -4; y <= 14; y++) set(sl, x, Y + y, z, banner && y >= 4 && y <= 12 ? st(deg % 60 < 4 ? Blocks.RED_WOOL : Blocks.BLACK_WOOL) : y == 14 ? cap : wall);
            }
        }
        // Vey's balcony, over the north stands
        for (int dx = -4; dx <= 4; dx++) for (int dz = -27; dz <= -21; dz++) {
            for (int y = 0; y <= 9; y++) set(sl, ox + dx, Y + y, oz + dz, y == 9 ? st(Blocks.POLISHED_BLACKSTONE) : stone);
            for (int y = 10; y <= 14; y++) set(sl, ox + dx, Y + y, oz + dz, st(Blocks.AIR));
            if (dz == -21 || Math.abs(dx) == 4) set(sl, ox + dx, Y + 10, oz + dz, st(Blocks.GOLD_BLOCK));
        }
        set(sl, ox, Y + 10, oz - 21, st(Blocks.AIR));
        // the gate tunnel under the south stands, and the waiting cells past it
        for (int dz = 17; dz <= 46; dz++) for (int dx = -7; dx <= 7; dx++) for (int y = -1; y <= 5; y++) {
            int x = ox + dx, z = oz + dz, ax = Math.abs(dx);
            boolean hall = ax <= 2 && y >= 0 && y <= 3, inCell = dz >= 29 && dz <= 39 && ax >= 4 && ax <= 6 && y >= 0 && y <= 3 && (dz - 29) % 4 != 3;
            BlockState b;
            if (y == -1) b = ax <= 2 ? st(Blocks.POLISHED_DEEPSLATE) : st(Blocks.DEEPSLATE_TILES);
            else if (hall || inCell) b = air;
            else if (ax == 3 && dz >= 29 && dz <= 39 && (dz - 29) % 4 != 3 && y <= 3) b = st(Blocks.IRON_BARS);
            else if (dz > 18 || ax > 2 || y > 3) b = y == 5 ? st(Blocks.DEEPSLATE_BRICKS) : (ax == 7 || dz == 46 || y >= 4 || (dz - 29) % 4 == 3 || dz < 29) ? wall : air;
            else continue;
            if (y == 4 && ax <= 2 && dz > 18 && dz % 4 == 0) b = st(Blocks.LANTERN).setValue(LanternBlock.HANGING, true);
            if (dz >= 19 || y == -1) sl.setBlock(new BlockPos(x, Y + y, z), b, 3);
        }
        for (int dz = 29; dz <= 39; dz += 4) for (int s : new int[]{-1, 1}) set(sl, ox + s * 6, Y, oz + dz + 1, st(Blocks.CANDLE).setValue(CandleBlock.LIT, true));
        catacomb(sl, ox, oz, rnd);
    }

    /** Under the arena: a ramp down from the cells, a hall north, a round hub under the sand and three rooms off it. */
    static void catacomb(ServerLevel sl, int ox, int oz, Random rnd) {
        BlockState air = st(Blocks.AIR), wall = st(Blocks.DEEPSLATE_TILES), crack = st(Blocks.CRACKED_DEEPSLATE_TILES), floor = st(Blocks.POLISHED_DEEPSLATE);
        // the ramp: from the end of the cells hall (z 44) down to the catacomb's floor, going north
        for (int i = 0; i <= 12; i++) {
            int z = oz + 44 - i, fy = Y - 1 - i;
            for (int dx = -2; dx <= 2; dx++) for (int y = fy; y <= fy + 5; y++) {
                boolean in = Math.abs(dx) <= 1 && y > fy && y <= fy + 4;
                set(sl, ox + dx + 5, y, z, y == fy ? floor : in ? air : wall);
            }
        }
        for (int z = 43; z <= 45; z++) for (int y = 0; y <= 3; y++) set(sl, ox + 3, Y + y, oz + z, air);   // the ramp's mouth, off the cells hall
        tube(sl, ox + 5, oz + 32, ox + 5, oz + 8, rnd);   // north, under the stands and the sand
        tube(sl, ox + 5, oz + 8, ox, oz + 8, rnd);
        tube(sl, ox, oz + 8, ox, oz + 4, rnd);
        // the hub
        for (int dx = -6; dx <= 6; dx++) for (int dz = -6; dz <= 6; dz++) {
            double r = Math.sqrt(dx * dx + dz * dz);
            if (r > 6.5) continue;
            for (int y = CY - 1; y <= CY + 5; y++) set(sl, ox + dx, Y + y, oz + dz, y == CY - 1 ? floor : y == CY + 5 || r > 5.5 ? (rnd.nextInt(5) == 0 ? crack : wall) : air);
        }
        set(sl, ox, Y + CY + 4, oz, st(Blocks.LANTERN).setValue(LanternBlock.HANGING, true));
        for (int[] rm : ROOMS) {
            int cx = ox + rm[0], cz = oz + rm[1];
            tube(sl, ox, oz, cx, cz, rnd);
            for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) for (int y = CY - 1; y <= CY + 5; y++) {
                boolean edge = Math.abs(dx) == 4 || Math.abs(dz) == 4;
                boolean door = edge && (rm[0] != 0 ? (dz == 0 || Math.abs(dz) == 1) && Math.signum(dx) == -Math.signum(rm[0]) : (dx == 0 || Math.abs(dx) == 1) && dz == 4) && y >= CY && y <= CY + 3;
                set(sl, cx + dx, Y + y, cz + dz, y == CY - 1 ? floor : door ? air : y == CY + 5 || edge ? (rnd.nextInt(5) == 0 ? crack : wall) : air);
            }
            // shelves of ledgers along the back, the lectern in front of them, candles
            int bx = rm[0] == 0 ? 0 : (int) Math.signum(rm[0]) * 3, bz = rm[0] == 0 ? -3 : 0;
            for (int k = -2; k <= 2; k++) for (int y = 0; y <= 2; y++) {
                int x = rm[0] == 0 ? cx + k : cx + bx, z = rm[0] == 0 ? cz + bz : cz + k;
                set(sl, x, Y + CY + y, z, st(Blocks.BOOKSHELF));
            }
            Direction face = rm[0] == 0 ? Direction.SOUTH : rm[0] > 0 ? Direction.WEST : Direction.EAST;
            set(sl, cx + (rm[0] == 0 ? 0 : -(int) Math.signum(rm[0])), Y + CY, cz + (rm[0] == 0 ? -1 : 0), st(Blocks.LECTERN).setValue(LecternBlock.FACING, face));
            set(sl, cx + 2, Y + CY, cz + 2, st(Blocks.CANDLE).setValue(CandleBlock.CANDLES, 3).setValue(CandleBlock.LIT, true));
            set(sl, cx - 2, Y + CY, cz + 2, st(Blocks.CANDLE).setValue(CandleBlock.CANDLES, 2).setValue(CandleBlock.LIT, true));
        }
    }

    /** A corridor 3 wide and 4 high from (x0, z0) to (x1, z1) along one axis, at the catacomb's floor. */
    static void tube(ServerLevel sl, int x0, int z0, int x1, int z1, Random rnd) {
        int n = Math.max(Math.abs(x1 - x0), Math.abs(z1 - z0));
        for (int i = 0; i <= n; i++) {
            int x = x0 + Integer.signum(x1 - x0) * i, z = z0 + Integer.signum(z1 - z0) * i;
            boolean alongX = x1 != x0;
            for (int w = -2; w <= 2; w++) for (int y = CY - 1; y <= CY + 4; y++) {
                int bx = alongX ? x : x + w, bz = alongX ? z + w : z;
                boolean in = Math.abs(w) <= 1 && y >= CY && y <= CY + 3;
                BlockState cur = sl.getBlockState(new BlockPos(bx, Y + y, bz));
                if (in) set(sl, bx, Y + y, bz, st(Blocks.AIR));
                else if (cur.isAir()) set(sl, bx, Y + y, bz, y == CY - 1 ? st(Blocks.POLISHED_DEEPSLATE) : rnd.nextInt(6) == 0 ? st(Blocks.CRACKED_DEEPSLATE_TILES) : st(Blocks.DEEPSLATE_TILES));
            }
            if (i % 6 == 3) set(sl, x, Y + CY + 3, z, st(Blocks.LANTERN).setValue(LanternBlock.HANGING, true));
        }
    }

    // ================================================================== the vault
    static void vault(ServerLevel sl, int ox, int oz, Random rnd) {
        BlockState air = st(Blocks.AIR), shelf = st(Blocks.BOOKSHELF), basalt = st(Blocks.POLISHED_BASALT), floor = st(Blocks.POLISHED_BLACKSTONE), gold = st(Blocks.GOLD_BLOCK);
        for (int dx = -8; dx <= 8; dx++) for (int dz = -3; dz <= 40; dz++) {
            int x = ox + dx, z = oz + dz, ax = Math.abs(dx);
            boolean outer = ax == 8 || dz == -3 || dz == 40;
            for (int y = -2; y <= 11; y++) {
                BlockState b;
                if (y <= -1) b = y == -1 ? (ax == 0 && dz > 2 && dz < 30 ? gold : (dx + dz) % 2 == 0 ? floor : st(Blocks.POLISHED_BLACKSTONE_BRICKS)) : st(Blocks.BLACKSTONE);
                else if (outer) b = ax == 8 && dz % 5 == 0 ? basalt : y >= 9 ? st(Blocks.POLISHED_BLACKSTONE_BRICKS) : shelf;
                else if (y == 10) b = dz >= 28 && dz <= 36 && ax <= 4 ? st(Blocks.IRON_BARS) : st(Blocks.POLISHED_BLACKSTONE_BRICKS);   // the grate the city looks down through
                else if (y == 11) b = dz >= 28 && dz <= 36 && ax <= 4 ? air : st(Blocks.POLISHED_BLACKSTONE_BRICKS);
                else b = air;
                set(sl, x, Y + y, z, b);
            }
        }
        // rows of shelves with an aisle down the middle; a lectern at each row's end, a stolen grimoire on each
        for (int dz = 6; dz <= 26; dz += 5) for (int s : new int[]{-1, 1}) {
            for (int dx = 3; dx <= 6; dx++) for (int y = 0; y <= 3; y++) set(sl, ox + s * dx, Y + y, oz + dz, shelf);
            set(sl, ox + s * 2, Y, oz + dz, st(Blocks.LECTERN).setValue(LecternBlock.FACING, s > 0 ? Direction.WEST : Direction.EAST));
            set(sl, ox + s * 6, Y + 4, oz + dz, st(Blocks.CANDLE).setValue(CandleBlock.CANDLES, 2).setValue(CandleBlock.LIT, true));
        }
        for (int dz = 4; dz <= 36; dz += 8) {
            for (int y = 7; y <= 9; y++) set(sl, ox, Y + y, oz + dz, st(Blocks.CHAIN));
            set(sl, ox, Y + 6, oz + dz, st(Blocks.LANTERN).setValue(LanternBlock.HANGING, true));
        }
        // the Magistrate's desk at the far end
        for (int dx = -2; dx <= 2; dx++) set(sl, ox + dx, Y, oz + 37, st(Blocks.DARK_OAK_PLANKS));
        set(sl, ox - 1, Y + 1, oz + 37, st(Blocks.CANDLE).setValue(CandleBlock.CANDLES, 3).setValue(CandleBlock.LIT, true));
        set(sl, ox + 1, Y + 1, oz + 37, st(Blocks.LANTERN));
    }
}
