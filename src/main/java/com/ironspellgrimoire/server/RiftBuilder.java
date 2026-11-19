package com.ironspellgrimoire.server;

import com.ironspellgrimoire.IronSpellGrimoire;
import static com.ironspellgrimoire.server.Rifts.*;
import com.ironspellgrimoire.network.AddonNetwork;
import com.ironspellgrimoire.network.FxPacket;
import com.ironspellgrimoire.registry.AddonEntities;
import com.ironspellgrimoire.registry.AddonItemRegistry;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/** Split out of Rifts (same code, own file): building it (in the Rift dimension only), the shape of a room. */
@Mod.EventBusSubscriber(modid = IronSpellGrimoire.MODID)
public final class RiftBuilder {
    private RiftBuilder() {}

    // ================================================================== building it (in the Rift dimension only)
    static void set(ServerLevel sl, int x, int y, int z, BlockState s) { sl.setBlock(new BlockPos(x, y, z), s, 2); }

    static void build(ServerLevel sl, Rift r) {
        BlockState wall = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), crack = Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        BlockState floor = Blocks.DEEPSLATE_TILES.defaultBlockState(), light = Blocks.SHROOMLIGHT.defaultBlockState();
        BlockState amethyst = Blocks.AMETHYST_BLOCK.defaultBlockState(), air = Blocks.AIR.defaultBlockState();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (Room room : r.rooms) {
            Vec3 c = r.centre(room);
            int cx = (int) Math.floor(c.x), cz = (int) Math.floor(c.z);
            int keep = r.rules;
            if (r.exam) r.rules = FLOOR_LAWS[Math.min(r.rooms.indexOf(room), FLOOR_LAWS.length - 1)];   // each exam floor shows its own law
            shell(sl, room, cx, cz, rnd);
            features(sl, r, room, cx, cz, rnd);
            decorate(sl, r, room, cx, cz, rnd);
            r.rules = keep;
            // doorways and the corridors to the next rooms
            for (int dd : room.doors) {
                int ax = dd == 0 ? 1 : dd == 1 ? -1 : 0, az = dd == 2 ? 1 : dd == 3 ? -1 : 0;
                int sx = az != 0 ? 1 : 0, sz = ax != 0 ? 1 : 0;   // sideways
                for (int k = HALF; k <= CELL - HALF; k++) for (int w = -2; w <= 2; w++) for (int dy = -2; dy <= 5; dy++) {
                    int x = cx + ax * k + sx * w, z = cz + az * k + sz * w;
                    boolean inner = Math.abs(w) <= 1 && dy >= 0 && dy <= 3;
                    if (k == HALF || k == CELL - HALF) { if (inner) set(sl, x, Y + dy, z, air); continue; }
                    boolean rib = k % 4 == 0, window = k % 4 == 2 && Math.abs(w) == 2 && (dy == 1 || dy == 2);
                    BlockState s;
                    if (inner) s = air;
                    else if (window) s = air;   // slits onto the rift's sky
                    else if (dy == -2 || dy == 5) s = Math.abs(w) == 2 ? air : Blocks.POLISHED_BLACKSTONE.defaultBlockState();   // a rounded tube, not a box
                    else if (dy == -1) s = Math.abs(w) <= 1 ? (w == 0 ? Blocks.POLISHED_DEEPSLATE.defaultBlockState() : floor) : Blocks.POLISHED_BLACKSTONE.defaultBlockState();
                    else if (dy == 4) s = w == 0 && k % 4 == 2 ? light : rib ? Blocks.POLISHED_BASALT.defaultBlockState() : wall;
                    else s = rib ? Blocks.POLISHED_BASALT.defaultBlockState() : rnd.nextInt(6) == 0 ? crack : wall;
                    set(sl, x, Y + dy, z, s);
                }
                for (int k = HALF + 2; k <= CELL - HALF - 2; k += 4) {   // soul lanterns hung in the corridor
                    int x = cx + ax * k, z = cz + az * k;
                    set(sl, x, Y + 3, z, Blocks.SOUL_LANTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true));
                }
            }
        }
        islands(sl, r, rnd);
        r.built = true;
    }

    // ------------------------------------------------------------------ the shape of a room
    static BlockState st(net.minecraft.world.level.block.Block b) { return b.defaultBlockState(); }

    /**
     * A room is an octagon, not a box: its corners are walled off on the diagonal, the ceiling steps up into a vault
     * (low at the walls, high in the middle), ribs of basalt run up the walls, and the floor is laid in rings and
     * spokes. Under it hangs a jagged spur of rock, so each room is a broken island floating in the rift.
     */
    static void shell(ServerLevel sl, Room room, int cx, int cz, ThreadLocalRandom rnd) {
        BlockState wall = st(Blocks.POLISHED_BLACKSTONE_BRICKS), crack = st(Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS), air = st(Blocks.AIR);
        BlockState light = st(Blocks.SHROOMLIGHT), basalt = st(Blocks.POLISHED_BASALT);
        for (int dx = -HALF; dx <= HALF; dx++) for (int dz = -HALF; dz <= HALF; dz++) {
            int ax = Math.abs(dx), az = Math.abs(dz);
            double rr = Math.sqrt(dx * dx + dz * dz);
            boolean outer = ax == HALF || az == HALF, corner = ax + az >= 13;
            int ceil = rr > 7 ? H - 4 : rr > 5.5 ? H - 2 : rr > 3.5 ? H - 1 : H;   // the stepped vault
            int along = ax == HALF ? dz : dx;   // position along an outer wall
            for (int dy = -1; dy <= H; dy++) {
                BlockState s;
                if (dy == -1) {   // the floor: a centre stone, rings and spokes
                    if (corner) s = wall;
                    else if (rr <= 1.5) s = st(Blocks.CHISELED_DEEPSLATE);
                    else if (rr > 6.2 && rr <= 7.2) s = st(Blocks.POLISHED_BLACKSTONE);
                    else if (rr > 2.5 && rr <= 3.3) s = st(Blocks.DEEPSLATE_BRICKS);
                    else if (dx == 0 || dz == 0 || ax == az) s = st(Blocks.POLISHED_DEEPSLATE);
                    else s = (ax + az) % 5 == 0 ? st(Blocks.CRACKED_DEEPSLATE_TILES) : st(Blocks.DEEPSLATE_TILES);
                } else if (outer || corner) {
                    s = outer && Math.abs(along) % 4 == 0 && Math.abs(along) != HALF ? basalt : rnd.nextInt(6) == 0 ? crack : wall;
                } else if (dy > ceil) s = wall;
                else if (dy == ceil) s = (dx % 4 == 0 && dz % 4 == 0) ? light : rr <= 1.5 ? st(Blocks.CHISELED_POLISHED_BLACKSTONE) : wall;
                else if ((ax == HALF - 1 || az == HALF - 1) && !corner && Math.abs(ax == HALF - 1 ? dz : dx) == 4) s = basalt;   // ribs standing off the walls
                else s = air;
                set(sl, cx + dx, Y + dy, cz + dz, s);
            }
            // the rock spur hanging under the room
            double spur = (HALF + 0.5) - rr * 0.9;
            int depth = (int) Math.max(0, spur + rnd.nextInt(3));
            if (!corner || rnd.nextBoolean()) for (int d = 2; d <= 1 + depth; d++)
                set(sl, cx + dx, Y - d, cz + dz, d > depth - 1 && rnd.nextInt(3) == 0 ? st(Blocks.AMETHYST_BLOCK)
                        : rnd.nextInt(4) == 0 ? st(Blocks.BASALT) : d % 3 == 0 ? st(Blocks.DEEPSLATE) : st(Blocks.BLACKSTONE));
        }
        for (int[] p : new int[][]{{-6, -6}, {6, -6}, {-6, 6}, {6, 6}}) {   // corner pillars up into the vault
            for (int dy = 0; dy < 3; dy++) set(sl, cx + p[0], Y + dy, cz + p[1], st(Blocks.AMETHYST_BLOCK));
            set(sl, cx + p[0], Y + 3, cz + p[1], light);
            for (int dy = 4; dy < H - 4; dy++) set(sl, cx + p[0], Y + dy, cz + p[1], st(Blocks.POLISHED_BLACKSTONE));
        }
        for (int[] p : new int[][]{{-3, -3}, {3, -3}, {-3, 3}, {3, 3}}) {   // soul lanterns on chains from the vault
            for (int dy = H - 2; dy >= H - 4; dy--) set(sl, cx + p[0], Y + dy, cz + p[1], st(Blocks.CHAIN));
            set(sl, cx + p[0], Y + H - 5, cz + p[1], st(Blocks.SOUL_LANTERN).setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true));
        }
    }

    /** The side of the room with no door (for a throne, an arch, a pool), or -1. */
    static int freeSide(Room room) {
        for (int side : new int[]{2, 0, 3, 1}) if (!room.doors.contains(side)) return side;
        return -1;
    }

    /**
     * What each kind of room has in it - kept to the edges, so the middle stays clear to fight in: broken columns in
     * combat rooms, a striped warning ring and floor vents in trap rooms, a pool, moss and benches in the rest room, a
     * stepped throne at the back of elite rooms and relic chambers (gold for relics), and an arch of crying obsidian in
     * the entrance.
     */
    static void features(ServerLevel sl, Rift r, Room room, int cx, int cz, ThreadLocalRandom rnd) {
        int side = freeSide(room);
        int ax = side == 0 ? 1 : side == 1 ? -1 : 0, az = side == 2 ? 1 : side == 3 ? -1 : 0, sx = az != 0 ? 1 : 0, sz = ax != 0 ? 1 : 0;
        switch (room.type) {
            case COMBAT -> {
                for (int i = 0; i < 6; i++) {   // broken columns round the edge
                    double a = rnd.nextDouble(Math.PI * 2);
                    int x = (int) Math.round(Math.cos(a) * 6.6), z = (int) Math.round(Math.sin(a) * 6.6);
                    if (Math.abs(x) <= 2 || Math.abs(z) <= 2 || Math.abs(x) + Math.abs(z) >= 12 || x * x + z * z < 6.5 * 6.5) continue;   // clear of where foes appear
                    int hgt = 1 + rnd.nextInt(3);
                    for (int dy = 0; dy < hgt; dy++) set(sl, cx + x, Y + dy, cz + z, dy == hgt - 1 ? st(Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS) : st(Blocks.POLISHED_BLACKSTONE));
                    if (rnd.nextBoolean()) set(sl, cx + x, Y + hgt, cz + z, st(Blocks.POLISHED_BLACKSTONE_SLAB));
                }
            }
            case TRAP -> {
                for (int dx = -7; dx <= 7; dx++) for (int dz = -7; dz <= 7; dz++) {   // a black and yellow warning ring, vents in the floor
                    double rr = Math.sqrt(dx * dx + dz * dz);
                    if (Math.abs(dx) + Math.abs(dz) >= 13) continue;
                    if (rr > 6.2 && rr <= 7.2) set(sl, cx + dx, Y - 1, cz + dz, ((dx + dz) & 2) == 0 ? st(Blocks.YELLOW_TERRACOTTA) : st(Blocks.BLACK_TERRACOTTA));
                    else if (rr > 1.5 && rr < 6 && rnd.nextInt(14) == 0) set(sl, cx + dx, Y - 1, cz + dz, st(Blocks.CRYING_OBSIDIAN));
                }
                for (int i = 0; i < 8; i++) {   // chains hanging all over the vault
                    int x = rnd.nextInt(-5, 6), z = rnd.nextInt(-5, 6);
                    for (int dy = H - 2; dy >= H - 3 - rnd.nextInt(3); dy--) set(sl, cx + x, Y + dy, cz + z, st(Blocks.CHAIN));
                }
            }
            case REST -> {
                for (int dx = -6; dx <= 6; dx++) for (int dz = -6; dz <= 6; dz++) {   // moss creeping in from the walls
                    double rr = Math.sqrt(dx * dx + dz * dz);
                    if (rr > 4.5 && Math.abs(dx) + Math.abs(dz) < 13 && rnd.nextInt(3) > 0) {
                        set(sl, cx + dx, Y - 1, cz + dz, st(Blocks.MOSS_BLOCK));
                        if (rnd.nextInt(5) == 0) set(sl, cx + dx, Y, cz + dz, rnd.nextBoolean() ? st(Blocks.MOSS_CARPET) : st(Blocks.AZALEA));
                    }
                }
                for (int[] p : new int[][]{{-4, -4}, {4, -4}, {-4, 4}, {4, 4}}) set(sl, cx + p[0], Y + H - 3, cz + p[1], st(Blocks.SPORE_BLOSSOM));
                if (side >= 0) {   // a little pool against the free wall, and benches beside it
                    for (int w = -2; w <= 2; w++) for (int k = 5; k <= 6; k++) {
                        int x = cx + ax * k + sx * w, z = cz + az * k + sz * w;
                        set(sl, x, Y - 2, z, st(Blocks.POLISHED_BLACKSTONE));
                        set(sl, x, Y - 1, z, st(Blocks.WATER));
                        set(sl, x, Y, z, st(Blocks.AIR));
                    }
                    for (int w = -2; w <= 2; w++) set(sl, cx + ax * 7 + sx * w, Y - 1, cz + az * 7 + sz * w, st(Blocks.SEA_LANTERN));
                }
            }
            case ELITE, RELIC -> {
                boolean relic = room.type == RELIC;
                for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++)   // a dais in the middle
                    set(sl, cx + dx, Y - 1, cz + dz, Math.abs(dx) == 2 || Math.abs(dz) == 2 ? st(relic ? Blocks.GILDED_BLACKSTONE : Blocks.POLISHED_BLACKSTONE) : st(Blocks.CRYING_OBSIDIAN));
                if (side >= 0) {   // a stepped throne against the back wall
                    for (int w = -3; w <= 3; w++) {
                        set(sl, cx + ax * 6 + sx * w, Y, cz + az * 6 + sz * w, st(Blocks.POLISHED_BLACKSTONE_SLAB));
                        for (int dy = 0; dy <= 1; dy++) set(sl, cx + ax * 7 + sx * w, Y + dy, cz + az * 7 + sz * w, Math.abs(w) == 3 ? st(relic ? Blocks.GOLD_BLOCK : Blocks.CRYING_OBSIDIAN) : st(Blocks.POLISHED_BLACKSTONE_BRICKS));
                    }
                    for (int dy = 2; dy <= 4; dy++) set(sl, cx + ax * 7, Y + dy, cz + az * 7, st(dy == 4 ? (relic ? Blocks.GOLD_BLOCK : Blocks.AMETHYST_BLOCK) : Blocks.POLISHED_BLACKSTONE));   // its tall back
                    for (int w : new int[]{-2, 2}) set(sl, cx + ax * 7 + sx * w, Y + 2, cz + az * 7 + sz * w, st(Blocks.SOUL_LANTERN));
                }
            }
            case EXAM -> {   // the Exam Hall: a gilded dais, gold on the pillars, and a gold-trimmed throne for the examiners
                for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++)
                    set(sl, cx + dx, Y - 1, cz + dz, Math.abs(dx) == 3 || Math.abs(dz) == 3 ? st(Blocks.GILDED_BLACKSTONE) : (dx + dz) % 2 == 0 ? st(Blocks.POLISHED_BLACKSTONE) : st(Blocks.CHISELED_POLISHED_BLACKSTONE));
                for (int[] p : new int[][]{{-6, -6}, {6, -6}, {-6, 6}, {6, 6}}) { set(sl, cx + p[0], Y + 3, cz + p[1], st(Blocks.GOLD_BLOCK)); set(sl, cx + p[0], Y + 4, cz + p[1], st(Blocks.SHROOMLIGHT)); }
                if (side >= 0) for (int w = -3; w <= 3; w++) for (int dy = 0; dy <= 2; dy++)
                    set(sl, cx + ax * 7 + sx * w, Y + dy, cz + az * 7 + sz * w, dy == 2 || Math.abs(w) == 3 ? st(Blocks.GOLD_BLOCK) : st(Blocks.POLISHED_BLACKSTONE_BRICKS));
            }
            case MIRROR -> {   // the Hall of Mirrors: a pale floor, mirror panes standing round the edge, white light
                for (int dx = -6; dx <= 6; dx++) for (int dz = -6; dz <= 6; dz++) {
                    if (Math.abs(dx) + Math.abs(dz) >= 12) continue;
                    set(sl, cx + dx, Y - 1, cz + dz, (dx + dz) % 2 == 0 ? st(Blocks.CALCITE) : st(Blocks.POLISHED_DIORITE));
                }
                for (int i = 0; i < 8; i++) {
                    double a = i * Math.PI / 4 + Math.PI / 8;
                    int x = (int) Math.round(Math.cos(a) * 6.7), z = (int) Math.round(Math.sin(a) * 6.7);
                    for (int dy = 0; dy < 4; dy++) set(sl, cx + x, Y + dy, cz + z, dy == 3 ? st(Blocks.SEA_LANTERN) : st(Blocks.WHITE_STAINED_GLASS));
                }
                for (int[] p : new int[][]{{-6, -6}, {6, -6}, {-6, 6}, {6, 6}}) set(sl, cx + p[0], Y + 3, cz + p[1], st(Blocks.SEA_LANTERN));
            }
            case START -> {
                if (side >= 0) for (int w = -3; w <= 3; w++) for (int dy = 0; dy <= 5; dy++) {   // the arch you came in by
                    boolean frame = Math.abs(w) == 3 || dy == 5 || (dy == 4 && Math.abs(w) == 2);
                    int x = cx + ax * 7 + sx * w, z = cz + az * 7 + sz * w;
                    set(sl, x, Y + dy, z, frame ? st(Blocks.CRYING_OBSIDIAN) : st(Blocks.PURPLE_STAINED_GLASS));
                }
            }
            default -> {}
        }
    }

    /** Broken islands and crystal spires drifting in the rift's sky around the rooms, seen through the windows. */
    static void islands(ServerLevel sl, Rift r, ThreadLocalRandom rnd) {
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (Room room : r.rooms) {
            Vec3 c = r.centre(room);
            minX = Math.min(minX, (int) c.x); maxX = Math.max(maxX, (int) c.x); minZ = Math.min(minZ, (int) c.z); maxZ = Math.max(maxZ, (int) c.z);
        }
        int n = 10 + r.rooms.size();
        for (int i = 0, tries = 0; i < n && tries < 80; tries++) {
            int x = rnd.nextInt(minX - 34, maxX + 35), z = rnd.nextInt(minZ - 34, maxZ + 35), y = Y + rnd.nextInt(-22, 20);
            boolean clear = true;
            for (Room room : r.rooms) {
                Vec3 c = r.centre(room);
                if (Math.abs(c.x - x) < 17 && Math.abs(c.z - z) < 17) { clear = false; break; }
            }
            if (!clear) continue;
            i++;
            if (rnd.nextInt(3) == 0) {   // a crystal spire
                int len = 5 + rnd.nextInt(7);
                for (int k = 0; k < len; k++) {
                    set(sl, x, y + k, z, k < len - 2 ? st(Blocks.AMETHYST_BLOCK) : st(Blocks.BUDDING_AMETHYST));
                    if (k < len / 2) for (int[] o : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) if (rnd.nextInt(3) == 0) set(sl, x + o[0], y + k, z + o[1], st(Blocks.TINTED_GLASS));
                }
                set(sl, x, y + len, z, st(Blocks.AMETHYST_CLUSTER));
                continue;
            }
            int rad = 2 + rnd.nextInt(3);   // an island: a flat top and a tapering underside
            for (int dx = -rad; dx <= rad; dx++) for (int dz = -rad; dz <= rad; dz++) {
                double rr = Math.sqrt(dx * dx + dz * dz);
                if (rr > rad + 0.3) continue;
                int depth = (int) ((rad + 1 - rr) * 1.6) + rnd.nextInt(2);
                for (int d = 0; d <= depth; d++)
                    set(sl, x + dx, y - d, z + dz, d == 0 ? (rnd.nextInt(4) == 0 ? st(Blocks.CRYING_OBSIDIAN) : st(Blocks.POLISHED_BLACKSTONE)) : rnd.nextInt(5) == 0 ? st(Blocks.BASALT) : st(Blocks.BLACKSTONE));
                if (rr < rad - 0.5 && rnd.nextInt(6) == 0) set(sl, x + dx, y + 1, z + dz, st(Blocks.AMETHYST_CLUSTER));
            }
            if (rnd.nextBoolean()) for (int k = 1; k <= 3; k++) set(sl, x, y + k, z, k == 3 ? st(Blocks.SHROOMLIGHT) : st(Blocks.POLISHED_BLACKSTONE_WALL));   // a ruined lamp post
        }
    }

    /**
     * What makes each room of a rift its own: a ring of glowing runes in the floor, amethyst growing on the pillars,
     * windows broken through the outer walls onto the rift's sky, and the rift's law written into the room -
     * Mana Burn: veins of crimson in the walls; Low Gravity: slabs of stone hanging in the air; Echo: pillars of mirror
     * glass; Hunted: patches of sculk and soul lanterns in the dark.
     */
    static void decorate(ServerLevel sl, Rift r, Room room, int cx, int cz, ThreadLocalRandom rnd) {
        for (int i = 0; i < 16; i++) {   // the rune ring
            double a = i * Math.PI / 8;
            int x = cx + (int) Math.round(Math.cos(a) * 4.5), z = cz + (int) Math.round(Math.sin(a) * 4.5);
            set(sl, x, Y - 1, z, i % 2 == 0 ? Blocks.CRYING_OBSIDIAN.defaultBlockState() : Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState());
        }
        for (int[] p : new int[][]{{-6, -6}, {6, -6}, {-6, 6}, {6, 6}}) {
            set(sl, cx + p[0] + 1, Y, cz + p[1], Blocks.AMETHYST_CLUSTER.defaultBlockState().setValue(net.minecraft.world.level.block.AmethystClusterBlock.FACING, net.minecraft.core.Direction.EAST));
            set(sl, cx + p[0], Y + 4, cz + p[1], Blocks.AMETHYST_CLUSTER.defaultBlockState());
        }
        // windows onto the rift's sky, in walls with no door
        for (int side = 0; side < 4; side++) {
            if (room.doors.contains(side)) continue;
            int ax = side == 0 ? 1 : side == 1 ? -1 : 0, az = side == 2 ? 1 : side == 3 ? -1 : 0;
            int sx = az != 0 ? 1 : 0, sz = ax != 0 ? 1 : 0;
            for (int w = -2; w <= 2; w++) for (int dy = 2; dy <= 5; dy++) {
                if (Math.abs(w) == 2 && (dy == 2 || dy == 5)) continue;   // rounded corners
                set(sl, cx + ax * HALF + sx * w, Y + dy, cz + az * HALF + sz * w, rnd.nextInt(5) == 0 ? Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState() : Blocks.AIR.defaultBlockState());
            }
            for (int w = -3; w <= 3; w++) set(sl, cx + ax * (HALF - 1) + sx * w, Y - 1, cz + az * (HALF - 1) + sz * w, Blocks.POLISHED_BLACKSTONE.defaultBlockState());   // a sill
        }
        // the law
        if ((r.rules & MANA_BURN) != 0) for (int i = 0; i < 18; i++) {
            int side = rnd.nextInt(4), w = rnd.nextInt(-6, 7), dy = rnd.nextInt(0, H);
            int ax = side == 0 ? 1 : side == 1 ? -1 : 0, az = side == 2 ? 1 : side == 3 ? -1 : 0, sx = az != 0 ? 1 : 0, sz = ax != 0 ? 1 : 0;
            BlockPos bp = new BlockPos(cx + ax * HALF + sx * w, Y + dy, cz + az * HALF + sz * w);
            if (!sl.getBlockState(bp).isAir()) set(sl, bp.getX(), bp.getY(), bp.getZ(), (i % 3 == 0 ? Blocks.SHROOMLIGHT : Blocks.CRIMSON_HYPHAE).defaultBlockState());
        }
        if ((r.rules & LOW_GRAVITY) != 0) for (int i = 0; i < 6; i++) {
            int x = cx + rnd.nextInt(-5, 6), z = cz + rnd.nextInt(-5, 6), y = Y + rnd.nextInt(2, 6);
            if (Math.abs(x - cx) < 2 && Math.abs(z - cz) < 2) continue;
            set(sl, x, y, z, Blocks.SMOOTH_STONE_SLAB.defaultBlockState());
            if (rnd.nextBoolean()) set(sl, x + 1, y, z, Blocks.SMOOTH_STONE_SLAB.defaultBlockState());
        }
        if ((r.rules & ECHO) != 0) for (int[] p : new int[][]{{-3, 0}, {3, 0}, {0, -3}, {0, 3}}) {
            if (room.type == RELIC || room.type == ELITE || room.type == REST) break;
            for (int dy = 0; dy < 3; dy++) set(sl, cx + p[0], Y + dy, cz + p[1], Blocks.TINTED_GLASS.defaultBlockState());
            set(sl, cx + p[0], Y + 3, cz + p[1], Blocks.AMETHYST_CLUSTER.defaultBlockState());
        }
        if ((r.rules & HUNTED) != 0) {
            for (int i = 0; i < 10; i++) set(sl, cx + rnd.nextInt(-6, 7), Y - 1, cz + rnd.nextInt(-6, 7), Blocks.SCULK.defaultBlockState());
            for (int[] p : new int[][]{{-6, 0}, {6, 0}}) set(sl, cx + p[0], Y, cz + p[1], Blocks.SOUL_LANTERN.defaultBlockState());
        }
    }

    /** Seals (or opens) a room's doorways with walls of violet glass. */
    static void seal(ServerLevel sl, Rift r, Room room, boolean shut) {
        Vec3 c = r.centre(room);
        int cx = (int) Math.floor(c.x), cz = (int) Math.floor(c.z);
        for (int dd : room.doors) {
            int ax = dd == 0 ? 1 : dd == 1 ? -1 : 0, az = dd == 2 ? 1 : dd == 3 ? -1 : 0;
            int sx = az != 0 ? 1 : 0, sz = ax != 0 ? 1 : 0;
            BlockState s = shut ? com.ironspellgrimoire.registry.AddonBlocks.RIFT_SEAL.get().defaultBlockState()
                    .setValue(com.ironspellgrimoire.block.RiftSealBlock.AXIS, ax != 0 ? net.minecraft.core.Direction.Axis.X : net.minecraft.core.Direction.Axis.Z) : Blocks.AIR.defaultBlockState();
            for (int w = -1; w <= 1; w++) for (int dy = 0; dy <= 3; dy++) set(sl, cx + ax * HALF + sx * w, Y + dy, cz + az * HALF + sz * w, s);
            Vec3 door = new Vec3(cx + ax * HALF + 0.5, Y + 2, cz + az * HALF + 0.5);
            if (shut) FxSend.ring(sl, VOIDP, door, 0.2f, 2.2f, 0.08f, 10);
            else {   // the seal shatters
                FxSend.ring(sl, VOIDP, door, 0.3f, 3f, 0.1f, 12);
                for (int k = 0; k < 4; k++) Kit.orb(sl, VOIDP, door.add(sx * (k - 1.5) * 0.7, (k % 2) - 0.5, sz * (k - 1.5) * 0.7), new Vec3(ax * 0.06, 0.04, az * 0.06), 0.18f, 10);
            }
        }
        sl.playSound(null, BlockPos.containing(c), shut ? SoundEvents.AMETHYST_BLOCK_RESONATE : SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.BLOCKS, 1.6f, shut ? 0.6f : 0.8f);
    }

}
