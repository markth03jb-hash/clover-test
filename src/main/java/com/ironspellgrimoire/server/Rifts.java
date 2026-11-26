package com.ironspellgrimoire.server;

import static com.ironspellgrimoire.server.RiftBuilder.*;
import com.ironspellgrimoire.IronSpellGrimoire;
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

/**
 * Dungeon Rifts. Now and then a crack of light tears open near a player and stays for a day. Step into it and you are
 * in the Rift - a pocket dimension of its own (data/iron_spell_grimoire/dimension/rift.json), so your world is never
 * touched - where a dungeon is assembled for that rift alone: 5-8 rooms joined into a path.
 * <ul>
 *   <li><b>Rooms:</b> combat (the doors seal until the waves are down), traps (survive them - sweeping beams and
 *       bursting floors - until the doors open), a rest room (it heals; stand on its circle to leave with what you
 *       have), an elite room off to the side (a boss's shade guarding a chest) and the relic chamber at the end (a
 *       stronger shade, then the relic and the way out).</li>
 *   <li><b>Laws:</b> every rift has one or two that change how you fight: Mana Burn (spells cost health), Low Gravity,
 *       Echo (every spell hit lands again a second later - on friends nearby too) and Hunted (an unkillable shade drifts
 *       after you through the walls).</li>
 *   <li><b>Reward:</b> the relic (a charm-slot trinket found nowhere else), Rift Ink (the rift's law, bottled - write
 *       it into your grimoire at the altar), emeralds, mastery XP and merit stars.</li>
 *   <li><b>Dying</b> throws you back out at the entrance with everything you carry; with nobody left inside, the rift
 *       closes.</li>
 * </ul>
 * Each rift has its own region of the Rift dimension (1024 blocks apart); regions are never reused.
 * <p>
 * <b>The Magic Knight Exam</b> is a rift of its own, opened from the Grimoire Altar for a grimoire at mastery 5: five
 * floors in a straight line, each under its own law (Mana Burn, Low Gravity, Echo with traps, Hunted, then Mana Burn
 * and Echo together against a shade), then the Exam Hall - three boss shades one after another. Pass and the grimoire
 * has passed: its mastery goes on to 7. Die and you are thrown back out to the altar; you can try again.
 */
@Mod.EventBusSubscriber(modid = IronSpellGrimoire.MODID)
public final class Rifts {
    private Rifts() {}

    public static final ResourceKey<Level> RIFT = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(IronSpellGrimoire.MODID, "rift"));
    public static final int MANA_BURN = 1, LOW_GRAVITY = 2, ECHO = 4, HUNTED = 8;
    public static final String[] RULE_NAMES = {"Mana Burn", "Low Gravity", "Echo", "Hunted"};
    static final String[] RULE_TEXT = {"Spells cost health instead of mana.", "You fall slowly and jump high.",
            "Every spell hit lands again a second later - on friends nearby too.", "A shade you cannot kill drifts after you through the walls."};
    static final int START = 0, COMBAT = 1, TRAP = 2, REST = 3, ELITE = 4, RELIC = 5, EXAM = 6, MIRROR = 7, LAIR = 8, GATE = 9;
    static final String[] ROOM_NAMES = {"Entrance", "Combat", "Traps", "Rest", "Elite", "Relic Chamber", "Exam Hall", "Hall of Mirrors", "Lair", "The Gate"};
    /** The exam's rooms in order and the law each floor is under. */
    static final int[] EXAM_ROOMS = {START, COMBAT, COMBAT, TRAP, COMBAT, ELITE, EXAM};
    static final int[] FLOOR_LAWS = {0, MANA_BURN, LOW_GRAVITY, ECHO, HUNTED, MANA_BURN | ECHO, 0};
    static final int CELL = 24, HALF = 8, H = 11, Y = 80, SPACING = 1024;
    static final UUID GRAV = UUID.fromString("a1f70000-0000-4000-8000-0000000000d1");
    static final String RETURN = "isg_rift_return";
    static final int VOIDP = Fx.VOID;

    // ================================================================== saved state
    static final class Room {
        int gx, gz, type;
        boolean cleared, active;
        int wave, timer;
        final Set<Integer> doors = new HashSet<>();   // 0 +x, 1 -x, 2 +z, 3 -z
        Room(int gx, int gz, int type) { this.gx = gx; this.gz = gz; this.type = type; }
    }

    static final class Rift {
        int id, rank, rules;
        String dim;
        double x, y, z;
        long until;
        boolean built, closed, relicTaken, exam, ascension;   // ascension: the Ascension Trial (also counts as an exam: no crack in the world)
        /** A bounty boss's lair (BossLadder): the bounty it belongs to (Boss, Mods, Id, Hardened) and whose it is; or the Gate. */
        boolean lair, gate;
        CompoundTag lairBounty = new CompoundTag();
        String owner = "";
        final List<Room> rooms = new ArrayList<>();
        int floor = -1;   // runtime: the exam floor whose law is in force
        int depth;   // extra rooms, for mages up the boss ladder (only while laying it out)
        // runtime only
        final Map<Integer, List<UUID>> mobs = new HashMap<>();
        Vec3 shade;
        /** The furthest exam floor already announced (walking back to an earlier floor says nothing again). */
        transient int announced;
        final Map<UUID, Integer> padTicks = new HashMap<>();

        Vec3 centre(Room r) { return new Vec3(id * (double) SPACING + r.gx * CELL + 0.5, Y, r.gz * CELL + 0.5); }
    }

    static final class Data extends SavedData {
        final List<Rift> rifts = new ArrayList<>();
        int next = 1;

        static Data load(CompoundTag t) {
            Data d = new Data();
            d.next = t.getInt("Next");
            for (Tag e : t.getList("Rifts", Tag.TAG_COMPOUND)) {
                CompoundTag c = (CompoundTag) e;
                Rift r = new Rift();
                r.id = c.getInt("Id"); r.rank = c.getInt("Rank"); r.rules = c.getInt("Rules"); r.dim = c.getString("Dim");
                r.x = c.getDouble("X"); r.y = c.getDouble("Y"); r.z = c.getDouble("Z"); r.until = c.getLong("Until");
                r.built = c.getBoolean("Built"); r.closed = c.getBoolean("Closed"); r.relicTaken = c.getBoolean("Relic");
                r.exam = c.getBoolean("Exam"); r.owner = c.getString("Owner"); r.ascension = c.getBoolean("Asc");
                r.lair = c.getBoolean("Lair"); r.gate = c.getBoolean("Gate"); r.lairBounty = c.getCompound("LairB");
                for (Tag re : c.getList("Rooms", Tag.TAG_COMPOUND)) {
                    CompoundTag rc = (CompoundTag) re;
                    Room room = new Room(rc.getInt("GX"), rc.getInt("GZ"), rc.getInt("T"));
                    room.cleared = rc.getBoolean("C");
                    for (int dd : rc.getIntArray("D")) room.doors.add(dd);
                    r.rooms.add(room);
                }
                d.rifts.add(r);
            }
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag t) {
            t.putInt("Next", next);
            ListTag l = new ListTag();
            for (Rift r : rifts) {
                if (r.closed) continue;   // closed rifts are forgotten
                CompoundTag c = new CompoundTag();
                c.putInt("Id", r.id); c.putInt("Rank", r.rank); c.putInt("Rules", r.rules); c.putString("Dim", r.dim);
                c.putDouble("X", r.x); c.putDouble("Y", r.y); c.putDouble("Z", r.z); c.putLong("Until", r.until);
                c.putBoolean("Built", r.built); c.putBoolean("Closed", r.closed); c.putBoolean("Relic", r.relicTaken);
                c.putBoolean("Exam", r.exam); c.putString("Owner", r.owner); c.putBoolean("Asc", r.ascension);
                c.putBoolean("Lair", r.lair); c.putBoolean("Gate", r.gate); c.put("LairB", r.lairBounty);
                ListTag rl = new ListTag();
                for (Room room : r.rooms) {
                    CompoundTag rc = new CompoundTag();
                    rc.putInt("GX", room.gx); rc.putInt("GZ", room.gz); rc.putInt("T", room.type); rc.putBoolean("C", room.cleared);
                    rc.putIntArray("D", room.doors.stream().mapToInt(Integer::intValue).toArray());
                    rl.add(rc);
                }
                c.put("Rooms", rl);
                l.add(c);
            }
            t.put("Rifts", l);
            return t;
        }
    }

    // the game tests (GameTests): the rifts written out and read back
    static CompoundTag saveForTest(MinecraftServer s) { return data(s).save(new CompoundTag()); }
    static Data loadForTest(CompoundTag t) { return Data.load(t); }
    static CompoundTag saveForTest(Data d) { return d.save(new CompoundTag()); }

    static Data data(MinecraftServer s) {
        return s.overworld().getDataStorage().computeIfAbsent(Data::load, Data::new, "isg_rifts");
    }

    static Rift riftAt(MinecraftServer s, Vec3 pos) {
        int id = (int) Math.round(pos.x / SPACING);
        for (Rift r : data(s).rifts) if (r.id == id && !r.closed) return r;
        return null;
    }

    /** The rift a player is standing in, or null. */
    public static Rift riftOf(ServerPlayer p) {
        if (!p.level().dimension().equals(RIFT)) return null;
        return riftAt(p.server, p.position());
    }

    public static boolean inRule(ServerPlayer p, int rule) {
        Rift r = riftOf(p);
        return r != null && (r.rules & rule) != 0;
    }

    // ================================================================== opening a rift
    static Rift open(ServerPlayer near, Vec3 at, int rank) {
        Data d = data(near.server);
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        Rift r = new Rift();
        r.id = d.next++;
        r.rank = Math.max(1, Math.min(5, rank));
        int a = 1 << rnd.nextInt(4);
        if (r.rank >= 3) { int b; do b = 1 << rnd.nextInt(4); while (b == a); a |= b; }
        r.rules = a;
        r.dim = near.level().dimension().location().toString();
        r.x = at.x; r.y = at.y; r.z = at.z;
        r.until = near.level().getGameTime() + 24000;
        r.depth = 2 * (BossLadder.tier(near) - 1);   // deeper as you climb the boss tiers
        layout(r, rnd);
        d.rifts.add(r);
        d.setDirty();
        ServerLevel sl = near.serverLevel();
        RiftFx.cut(sl, at.add(0, 1.6, 0), new Vec3(0, 1, 0), new Vec3(1, 0, 0), 1.8f, 30);
        FxSend.shake(sl, at, 0.4f, 10, 30);
        sl.playSound(null, BlockPos.containing(at), SoundEvents.END_PORTAL_SPAWN, SoundSource.AMBIENT, 0.6f, 1.6f);
        near.displayClientMessage(Component.literal("A rift has torn open nearby...").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC), true);
        return r;
    }

    // ================================================================== the Magic Knight Exam
    /** Opens the exam for {@code p}'s equipped grimoire and takes them straight in. The altar checks they may take it. */
    public static void startExam(ServerPlayer p) {
        Data d = data(p.server);
        Rift r = new Rift();
        r.id = d.next++;
        r.rank = 4;
        r.exam = true;
        r.owner = p.getUUID().toString();
        r.dim = p.level().dimension().location().toString();
        r.x = p.getX() - 2.5; r.y = p.getY(); r.z = p.getZ() - 2.5;   // enter() puts the way back at +2.5: right where they stood
        r.until = p.level().getGameTime() + 24000;
        Room prev = null;
        for (int i = 0; i < EXAM_ROOMS.length; i++) {
            Room room = new Room(i, 0, EXAM_ROOMS[i]);
            if (prev != null) link(prev, room);
            r.rooms.add(room);
            prev = room;
        }
        d.rifts.add(r);
        d.setDirty();
        ServerLevel sl = p.serverLevel();
        FxSend.impactFrame(sl, p.position().add(0, 1.2, 0), 4, FxSend.FRAME_GOLD, 16);
        FxSend.ring(sl, Fx.GOLD, p.position().add(0, 0.1, 0), 0.3f, 4f, 0.12f, 16);
        RiftFx.cut(sl, p.position().add(0, 1.6, 0), new Vec3(0, 1, 0), new Vec3(1, 0, 0), 1.8f, 20);
        enter(p, r);
    }

    /** The Ascension Trial: an entrance, then the Hall of Mirrors where the Reflection waits. */
    public static void startAscension(ServerPlayer p) {
        Data d = data(p.server);
        Rift r = new Rift();
        r.id = d.next++;
        r.rank = 5;
        r.exam = true;
        r.ascension = true;
        r.owner = p.getUUID().toString();
        r.dim = p.level().dimension().location().toString();
        r.x = p.getX() - 2.5; r.y = p.getY(); r.z = p.getZ() - 2.5;
        r.until = p.level().getGameTime() + 24000;
        Room a = new Room(0, 0, START), b = new Room(1, 0, MIRROR);
        link(a, b);
        r.rooms.add(a); r.rooms.add(b);
        d.rifts.add(r);
        d.setDirty();
        ServerLevel sl = p.serverLevel();
        FxSend.impactFrame(sl, p.position().add(0, 1.2, 0), 4, FxSend.FRAME_GOLD, 16);
        FxSend.ring(sl, Fx.SILVER, p.position().add(0, 0.1, 0), 0.3f, 4f, 0.12f, 16);
        enter(p, r);
    }

    /** Each floor of the exam puts its own law in force as you reach it. */
    static void examFloor(Rift r, List<ServerPlayer> inside) {
        int furthest = 0;
        for (ServerPlayer p : inside) { Room room = roomOf(r, p.position()); if (room != null) furthest = Math.max(furthest, r.rooms.indexOf(room)); }
        if (furthest == r.floor) return;
        r.floor = furthest;
        int law = FLOOR_LAWS[Math.min(furthest, FLOOR_LAWS.length - 1)];
        r.shade = (law & HUNTED) == 0 ? null : r.centre(r.rooms.get(Math.max(0, furthest - 1))).add(0, 1.2, 0);   // it comes in behind you
        r.rules = law;
        if (furthest <= r.announced) return;   // back on a floor you've already been told about
        r.announced = furthest;
        for (ServerPlayer p : inside) {
            if (furthest == EXAM_ROOMS.length - 1) {
                p.sendSystemMessage(Component.literal("FINAL TRIAL  ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                        .append(Component.literal("Defeat three shades, one after another.").withStyle(ChatFormatting.YELLOW)));
                continue;
            }
            if (furthest == 0) continue;
            p.sendSystemMessage(Component.literal("FLOOR " + furthest + "/5  ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                    .append(Component.literal(law == 0 ? "No law." : lawText(law)).withStyle(ChatFormatting.GRAY)));
        }
    }

    static String lawText(int law) {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < 4; i++) if ((law & (1 << i)) != 0) s.append(s.length() > 0 ? "  " : "").append(RULE_NAMES[i]).append(": ").append(RULE_TEXT[i]);
        return s.toString();
    }

    /** The exam is passed: its mastery goes on to 7 (the book is tagged "Ascended" - not the same as Grimoire Ascension). */
    static void passExam(ServerLevel sl, Rift r, Room room) {
        Vec3 c = r.centre(room);
        FxSend.impactFrame(sl, c.add(0, 1.5, 0), 5, FxSend.FRAME_GOLD, 24);
        FxSend.ring(sl, Fx.GOLD, c.add(0, 0.1, 0), 0.5f, 8f, 0.18f, 20);
        FxSend.beam(sl, Fx.GOLD, c, c.add(0, 30, 0), 0.8f, 40);
        sl.playSound(null, BlockPos.containing(c), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.2f, 0.9f);
        sl.playSound(null, BlockPos.containing(c), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 1.2f, 1.2f);
        for (ServerPlayer p : sl.players()) {
            if (riftAt(sl.getServer(), p.position()) != r) continue;
            boolean owner = p.getUUID().toString().equals(r.owner);
            ItemStack book = io.redspace.ironsspellbooks.api.util.Utils.getPlayerSpellbookStack(p);
            if (owner && book != null && GrimoireMastery.bookOf(book) != null && !GrimoireMastery.ascended(book)) {
                book.getOrCreateTag().putBoolean("Ascended", true);
                Adv.grant(p, "magic_knight");
                p.sendSystemMessage(Component.literal("MAGIC KNIGHT EXAM PASSED  ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                        .append(Component.literal(book.getHoverName().getString() + " has passed - its mastery can now reach 7.").withStyle(ChatFormatting.YELLOW)));
                GrimoireMastery.bountyXp(p, 1);   // nudges the level along if it already had the XP for more
            }
            Bounties.addStars(p, 10);
            p.sendSystemMessage(Component.literal("+10 merit stars. Stand on the circle to leave.").withStyle(ChatFormatting.YELLOW));
        }
        r.relicTaken = true;
    }

    /** The rooms: a random walk on a grid, an elite room hung off the side. */
    static void layout(Rift r, ThreadLocalRandom rnd) {
        int n = Math.min(17, 5 + Math.min(3, r.rank / 2 + rnd.nextInt(2)) + r.depth);
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int attempt = 0; attempt < 50; attempt++) {
            r.rooms.clear();
            Set<Long> used = new HashSet<>();
            int gx = 0, gz = 0;
            r.rooms.add(new Room(0, 0, START));
            used.add(key(0, 0));
            boolean ok = true;
            for (int i = 1; i < n && ok; i++) {
                ok = false;
                for (int tries = 0; tries < 12; tries++) {
                    int[] dd = dirs[rnd.nextInt(4)];
                    if (!used.contains(key(gx + dd[0], gz + dd[1]))) {
                        Room prev = r.rooms.get(r.rooms.size() - 1);
                        gx += dd[0]; gz += dd[1];
                        Room room = new Room(gx, gz, COMBAT);
                        link(prev, room);
                        r.rooms.add(room);
                        used.add(key(gx, gz));
                        ok = true;
                        break;
                    }
                }
            }
            if (!ok) continue;
            // types along the path: rest in the middle, the relic at the end, traps and fights between
            int last = r.rooms.size() - 1;
            r.rooms.get(last).type = RELIC;
            r.rooms.get(last / 2 + 1 == last ? last - 1 : last / 2 + 1).type = REST;
            List<Integer> mids = new ArrayList<>();
            for (int i = 1; i < last; i++) if (r.rooms.get(i).type == COMBAT) mids.add(i);
            if (!mids.isEmpty()) r.rooms.get(mids.get(rnd.nextInt(mids.size()))).type = TRAP;
            if (r.depth > 0) for (int i = 3; i < last; i += 3) if (r.rooms.get(i).type == COMBAT) r.rooms.get(i).type = ELITE;   // deep rifts: a mini-boss every third room
            // the elite room, off to one side of a middle room
            for (int i = 1; i < last && r.rooms.size() == n; i++) {
                Room m = r.rooms.get(rnd.nextInt(1, last));
                for (int[] dd : dirs) {
                    if (used.contains(key(m.gx + dd[0], m.gz + dd[1]))) continue;
                    Room e = new Room(m.gx + dd[0], m.gz + dd[1], ELITE);
                    link(m, e);
                    r.rooms.add(e);
                    used.add(key(e.gx, e.gz));
                    break;
                }
            }
            return;
        }
    }

    static long key(int x, int z) { return ((long) x << 32) ^ (z & 0xffffffffL); }

    static void link(Room a, Room b) {
        int dx = b.gx - a.gx, dz = b.gz - a.gz;
        int da = dx == 1 ? 0 : dx == -1 ? 1 : dz == 1 ? 2 : 3;
        a.doors.add(da);
        b.doors.add(da ^ 1);
    }

    // ================================================================== going in and out
    static void enter(ServerPlayer p, Rift r) {
        ServerLevel rift = p.server.getLevel(RIFT);
        if (rift == null) { p.displayClientMessage(Component.literal("The rift won't open (the Rift dimension is missing).").withStyle(ChatFormatting.RED), true); return; }
        if (!r.built) build(rift, r);
        data(p.server).setDirty();
        CompoundTag ret = new CompoundTag();
        ret.putString("Dim", p.level().dimension().location().toString());
        ret.putDouble("X", r.x + 2.5); ret.putDouble("Y", r.y); ret.putDouble("Z", r.z + 2.5);
        p.getPersistentData().put(RETURN, ret);
        Vec3 c = r.centre(r.rooms.get(0));
        p.teleportTo(rift, c.x, Y, c.z, p.getYRot(), p.getXRot());
        rift.playSound(null, BlockPos.containing(c), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1f, 0.6f);
        if (r.ascension) {
            p.sendSystemMessage(Component.literal("THE ASCENSION TRIAL").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            p.sendSystemMessage(Component.literal("  In the Hall of Mirrors your Reflection waits - your own magic, turned on you.").withStyle(ChatFormatting.GRAY));
            p.sendSystemMessage(Component.literal("  Beat it and your grimoire ascends. Fall and you are sent back to the altar.").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        if (r.gate) {
            p.sendSystemMessage(Component.literal("THE GATE").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
            p.sendSystemMessage(Component.literal("  Two gatekeepers wait: a shade of a tier I boss, then of a tier II one.").withStyle(ChatFormatting.GRAY));
            p.sendSystemMessage(Component.literal("  Pass and tier III opens on your posters. Fall and you can try again from the board.").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        if (r.lair) {
            int b = r.lairBounty.getInt("Boss");
            p.sendSystemMessage(Component.literal((r.lairBounty.getBoolean("Hardened") ? "HARDENED " : "") + "LAIR OF " + (b == Bounties.SECRET ? "LICHT, THE UNBOUND" : Bounties.NAMES[Math.min(Bounties.NAMES.length - 1, b)].toUpperCase())).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
            p.sendSystemMessage(Component.literal("  Tier " + BossLadder.TIER_NAME[BossLadder.TIER[Math.min(7, b)]] + ". It waits in the next room. Fall, and you're thrown back out - the lair stays open until the poster runs out.").withStyle(ChatFormatting.GRAY));
            return;
        }
        if (r.exam) {
            p.sendSystemMessage(Component.literal("THE MAGIC KNIGHT EXAM").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            p.sendSystemMessage(Component.literal("  Five floors, each under its own law, then three shades in the Exam Hall.").withStyle(ChatFormatting.GRAY));
            p.sendSystemMessage(Component.literal("  Fall and you are sent back to the altar. Pass and your grimoire's mastery goes on to 7.").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        p.sendSystemMessage(Component.literal(stars(r.rank) + "  RIFT").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
        for (int i = 0; i < 4; i++) if ((r.rules & (1 << i)) != 0)
            p.sendSystemMessage(Component.literal("  " + RULE_NAMES[i] + ": ").withStyle(ChatFormatting.RED).append(Component.literal(RULE_TEXT[i]).withStyle(ChatFormatting.GRAY)));
        p.sendSystemMessage(Component.literal("  Reach the relic chamber. The rest room's circle takes you out early.").withStyle(ChatFormatting.DARK_GRAY));
    }

    /** Back out to where you came in. */
    public static void leave(ServerPlayer p) {
        CompoundTag ret = p.getPersistentData().getCompound(RETURN);
        ServerLevel to = null;
        if (!ret.isEmpty()) to = p.server.getLevel(ResourceKey.create(Registries.DIMENSION, new ResourceLocation(ret.getString("Dim"))));
        if (to == null) {
            to = p.server.overworld();
            BlockPos s = to.getSharedSpawnPos();
            ret = new CompoundTag();
            ret.putDouble("X", s.getX() + 0.5); ret.putDouble("Y", s.getY()); ret.putDouble("Z", s.getZ() + 0.5);
        }
        p.getAttribute(ForgeMod.ENTITY_GRAVITY.get()).removeModifier(GRAV);
        p.teleportTo(to, ret.getDouble("X"), ret.getDouble("Y"), ret.getDouble("Z"), p.getYRot(), p.getXRot());
        p.fallDistance = 0;
        p.getPersistentData().remove(RETURN);
        to.playSound(null, p.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1f, 1.2f);
    }

    static void close(MinecraftServer s, Rift r) {
        if (r.closed) return;
        r.closed = true;
        ServerLevel rift = s.getLevel(RIFT);
        if (rift != null) {
            double x0 = r.id * (double) SPACING;
            for (Entity e : rift.getEntitiesOfClass(Entity.class, new AABB(x0 - 300, 0, -300, x0 + 300, 256, 300), e -> !(e instanceof ServerPlayer))) e.discard();
        }
        data(s).setDirty();
    }

    // ================================================================== the tick
    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        MinecraftServer s = e.getServer();
        long tick = s.getTickCount();
        Data d = data(s);
        if (d.rifts.isEmpty()) return;
        ServerLevel rift = s.getLevel(RIFT);
        for (Rift r : new ArrayList<>(d.rifts)) {
            if (r.closed) continue;
            List<ServerPlayer> inside = new ArrayList<>();
            if (rift != null) for (ServerPlayer p : rift.players()) if (riftAt(s, p.position()) == r) inside.add(p);
            ServerLevel world = s.getLevel(ResourceKey.create(Registries.DIMENSION, new ResourceLocation(r.dim)));
            long now = s.overworld().getGameTime();
            boolean open = now < r.until && !r.relicTaken && !r.exam;   // the exam has no crack in the world: the altar takes you in
            if (world != null && open) entrance(world, r, tick);
            if (inside.isEmpty()) {
                if (!open || r.relicTaken) close(s, r);
                continue;
            }
            if (rift != null) run(rift, r, inside, tick);
        }
        if (tick % 200 == 0) d.rifts.removeIf(x -> x.closed);
    }

    /** The crack in the world: drawn while anyone is near, entered by walking into it. */
    static void entrance(ServerLevel world, Rift r, long tick) {
        Vec3 at = new Vec3(r.x, r.y, r.z);
        List<ServerPlayer> near = new ArrayList<>();
        for (ServerPlayer p : world.players()) if (p.position().distanceToSqr(at) < 96 * 96) near.add(p);
        if (near.isEmpty()) return;
        if (tick % 40 == 0) {   // a tall crack of light with a second one crossing it, a ring on the ground and a beam up into the sky
            RiftFx.cut(world, at.add(0, 2.2, 0), new Vec3(0, 1, 0), new Vec3(1, 0, 0), 2.6f, 44);
            RiftFx.cut(world, at.add(0, 2.2, 0), new Vec3(0.35, 1, 0).normalize(), new Vec3(0, 0, 1), 2.0f, 44);
            FxSend.ring(world, VOIDP, at.add(0, 0.1, 0), 2.2f, 2.3f, 0.06f, 42);
            FxSend.beam(world, VOIDP, at.add(0, 4.5, 0), at.add(0, 60, 0), 0.6f, 42);
        }
        if (tick % 10 == 0) Kit.orb(world, VOIDP, at.add(ThreadLocalRandom.current().nextDouble(-1.5, 1.5), 0.3, ThreadLocalRandom.current().nextDouble(-1.5, 1.5)), new Vec3(0, 0.08, 0), 0.15f, 20);
        if (tick % 60 == 0) for (ServerPlayer p : near) if (p.position().distanceToSqr(at) < 18 * 18)
            world.playSound(null, BlockPos.containing(at), SoundEvents.BEACON_AMBIENT, SoundSource.AMBIENT, 1.2f, 0.6f);   // its hum
        for (ServerPlayer p : near) {
            double dx = p.getX() - r.x, dz = p.getZ() - r.z, dy = p.getY() - r.y;
            if (tick % 20 == 0 && dx * dx + dz * dz < 100 && r.lair) {
                int b = r.lairBounty.getInt("Boss");
                p.displayClientMessage(Component.literal("Lair of " + (b == Bounties.SECRET ? "Licht, the Unbound" : Bounties.NAMES[Math.min(Bounties.NAMES.length - 1, b)]) + "  -  step in to face it").withStyle(ChatFormatting.DARK_RED), true);
            } else if (tick % 20 == 0 && dx * dx + dz * dz < 100) {
                StringBuilder rules = new StringBuilder();
                for (int i = 0; i < 4; i++) if ((r.rules & (1 << i)) != 0) rules.append(rules.length() > 0 ? ", " : "").append(RULE_NAMES[i]);
                p.displayClientMessage(Component.literal(stars(r.rank) + " Rift  -  " + rules + "  -  step in to enter").withStyle(ChatFormatting.LIGHT_PURPLE), true);
            }
            if (tick % 5 == 0 && dx * dx + dz * dz < 1.3 * 1.3 && dy > -1 && dy < 2.5 && !p.isSpectator()) enter(p, r);
        }
    }

    static String stars(int n) { return "★".repeat(n) + "☆".repeat(5 - n); }

    static Room roomOf(Rift r, Vec3 pos) {
        for (Room room : r.rooms) {
            Vec3 c = r.centre(room);
            if (Math.abs(pos.x - c.x) <= HALF - 0.5 && Math.abs(pos.z - c.z) <= HALF - 0.5) return room;
        }
        return null;
    }

    static void run(ServerLevel sl, Rift r, List<ServerPlayer> inside, long tick) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        if (r.exam && !r.ascension && !r.gate) examFloor(r, inside);
        // laws that act on everyone inside
        if ((r.rules & LOW_GRAVITY) != 0 && tick % 20 == 0) for (ServerPlayer p : inside)
            TimedModifiers.apply(p, ForgeMod.ENTITY_GRAVITY.get(), GRAV, "Rift: low gravity", -0.65, AttributeModifier.Operation.MULTIPLY_TOTAL, 30);
        if ((r.rules & HUNTED) != 0) hunt(sl, r, inside, tick);
        for (ServerPlayer p : inside) if (p.getY() < Y - 20) { p.teleportTo(sl, r.centre(r.rooms.get(0)).x, Y, r.centre(r.rooms.get(0)).z, p.getYRot(), p.getXRot()); p.fallDistance = 0; }
        // rooms
        Map<Room, List<ServerPlayer>> in = new HashMap<>();
        for (ServerPlayer p : inside) { Room room = roomOf(r, p.position()); if (room != null) in.computeIfAbsent(room, k -> new ArrayList<>()).add(p); }
        for (int i = 0; i < r.rooms.size(); i++) {
            Room room = r.rooms.get(i);
            List<ServerPlayer> here = in.getOrDefault(room, List.of());
            if (!room.cleared && !room.active && !here.isEmpty()) activate(sl, r, i, room, inside.size());
            if (room.active) tickRoom(sl, r, i, room, here, inside.size(), tick, rnd);
            if (room.type == REST || ((room.type == RELIC || room.type == EXAM || room.type == MIRROR || room.type == LAIR || room.type == GATE) && room.cleared)) pad(sl, r, room, here, tick);
        }
        if (tick % 20 == 0) for (ServerPlayer p : inside) { Room at = roomOf(r, p.position()); objective(p, r, at); map(p, r, at); }
        if (tick % 15 == 0) for (Room room : in.keySet()) ambience(sl, r, room, rnd);
    }

    /** Motes of the rift's light drifting up through an occupied room, coloured by its law. */
    static void ambience(ServerLevel sl, Rift r, Room room, ThreadLocalRandom rnd) {
        int pal = (r.rules & MANA_BURN) != 0 ? 18 : (r.rules & HUNTED) != 0 ? Fx.DARK : (r.rules & ECHO) != 0 ? Fx.MIRROR : VOIDP;
        Vec3 c = r.centre(room);
        for (int i = 0; i < 3; i++)
            Kit.orb(sl, i == 0 ? VOIDP : pal, c.add(rnd.nextDouble(-6.5, 6.5), rnd.nextDouble(0, 2), rnd.nextDouble(-6.5, 6.5)), new Vec3(rnd.nextDouble(-0.01, 0.01), 0.03, rnd.nextDouble(-0.01, 0.01)), 0.1f, 40);
    }

    static void activate(ServerLevel sl, Rift r, int idx, Room room, int players) {
        switch (room.type) {
            case COMBAT -> { room.active = true; room.wave = 0; seal(sl, r, room, true); wave(sl, r, idx, room, players); }
            case TRAP -> { room.active = true; room.timer = 20 * (18 + r.rank * 2); seal(sl, r, room, true); }
            case ELITE, RELIC -> { room.active = true; seal(sl, r, room, true); guardian(sl, r, idx, room); }
            case EXAM -> { room.active = true; room.wave = 0; seal(sl, r, room, true); examShade(sl, r, idx, room, 0); }
            case MIRROR -> { room.active = true; seal(sl, r, room, true); reflection(sl, r, idx, room); }
            case LAIR -> { room.active = true; room.timer = 30; seal(sl, r, room, true); }   // the boss comes after a breath (and checks it isn't already here)
            case GATE -> { room.active = true; room.wave = 0; seal(sl, r, room, true); gatekeeper(sl, r, idx, room, 0); }
            default -> room.cleared = true;
        }
    }

    static void clear(ServerLevel sl, Rift r, int idx, Room room) {
        room.active = false;
        room.cleared = true;
        seal(sl, r, room, false);
        Vec3 c = r.centre(room);
        FxSend.ring(sl, VOIDP, c.add(0, 0.1, 0), 0.5f, 7f, 0.15f, 14);
        sl.playSound(null, BlockPos.containing(c), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 1.4f);
        data(sl.getServer()).setDirty();
    }

    static void tickRoom(ServerLevel sl, Rift r, int idx, Room room, List<ServerPlayer> here, int players, long tick, ThreadLocalRandom rnd) {
        Vec3 c = r.centre(room);
        switch (room.type) {
            case COMBAT -> {
                if (tick % 10 != 0) return;
                if (alive(sl, r, idx) > 0) return;
                int waves = 2 + r.rank / 2;
                if (++room.wave >= waves) clear(sl, r, idx, room);
                else wave(sl, r, idx, room, players);
            }
            case TRAP -> {
                if (--room.timer <= 0) { clear(sl, r, idx, room); return; }
                double ang = tick * (0.03 + r.rank * 0.004);   // two beams sweeping round the room
                for (int b = 0; b < 2; b++) {
                    double a = ang + b * Math.PI / 2;
                    Vec3 d = new Vec3(Math.cos(a), 0, Math.sin(a)).scale(HALF - 1);
                    Vec3 p0 = c.add(0, 1.0, 0).subtract(d), p1 = c.add(0, 1.0, 0).add(d);
                    if (tick % 2 == 0) FxSend.beam(sl, VOIDP, p0, p1, 0.18f, 3);
                    if (tick % 10 == 0) for (ServerPlayer p : here)
                        if (FxUtil.closestOnSegment(p0, p1, p.position().add(0, 0.6, 0)).distanceTo(p.position().add(0, 0.6, 0)) < 0.8)
                            p.hurt(p.damageSources().magic(), 4 + r.rank);
                }
                if (tick % 30 == 0) for (int k = 0; k < 2 + r.rank / 2; k++) {   // bursting floor: shown, then it goes off
                    Vec3 at = c.add(rnd.nextDouble(-6, 6), 0.05, rnd.nextDouble(-6, 6));
                    ZenonAI.warnCircle(sl, at, 1.6f, 16);
                    AstaFx.later(16, () -> {
                        FxSend.spike(sl, VOIDP, at, new Vec3(0, 1, 0), 2.2f, 0.25f, 10, 2, FxPacket.GROUND);
                        for (ServerPlayer p : sl.getEntitiesOfClass(ServerPlayer.class, new AABB(at, at).inflate(1.6, 2, 1.6)))
                            if (!p.isSpectator()) { p.hurt(p.damageSources().magic(), 5 + r.rank); p.setDeltaMovement(p.getDeltaMovement().add(0, 0.6, 0)); p.hurtMarked = true; }
                    });
                }
            }
            case ELITE, RELIC -> {
                if (tick % 10 != 0 || alive(sl, r, idx) > 0) return;
                clear(sl, r, idx, room);
                if (!r.exam) reward(sl, r, room, here.isEmpty() ? null : here.get(0), room.type == RELIC);
            }
            case MIRROR -> {   // the Reflection falls: the grimoire ascends
                if (tick % 10 != 0 || alive(sl, r, idx) > 0) return;
                clear(sl, r, idx, room);
                r.relicTaken = true;
                for (ServerPlayer p : here) if (p.getUUID().toString().equals(r.owner)) Ascension.ascend(p);
                for (ServerPlayer p : here) p.sendSystemMessage(Component.literal("Stand on the circle to leave.").withStyle(ChatFormatting.GRAY));
            }
            case LAIR -> {   // the bounty's boss: here until the bounty is done
                if (tick % 10 != 0) return;
                int bid = r.lairBounty.getInt("Id");
                java.util.UUID owner = r.owner.isEmpty() ? null : java.util.UUID.fromString(r.owner);
                ServerPlayer op = owner == null ? null : sl.getServer().getPlayerList().getPlayer(owner);
                boolean done = op != null && !Bounties.active(op, bid);
                if (done) { clear(sl, r, idx, room); r.relicTaken = true; return; }
                if (alive(sl, r, idx) > 0) return;
                if (room.timer > 0) { room.timer -= 10; return; }
                // nothing alive here: is it loaded in from before? if not, it rises (full health - you left it)
                Vec3 cc = r.centre(room);
                for (Mob m : sl.getEntitiesOfClass(Mob.class, new AABB(cc, cc).inflate(HALF + 2, 12, HALF + 2), x -> x.isAlive() && x.getPersistentData().getInt("isg_bounty_id") == bid)) {
                    r.mobs.computeIfAbsent(idx, k -> new ArrayList<>()).add(m.getUUID());
                    return;
                }
                if (op == null || here.isEmpty()) return;   // (the poster's owner must be on the server for it to rise)
                Mob m = Bounties.spawnBoss(sl, cc.add(0, 0, 3), r.lairBounty, owner);
                if (m == null) return;
                r.mobs.computeIfAbsent(idx, k -> new ArrayList<>()).add(m.getUUID());
                for (Mob g : Bounties.lastEscort) r.mobs.get(idx).add(g.getUUID());
                RiftFx.cut(sl, m.position().add(0, 1.6, 0), new Vec3(0, 1, 0), new Vec3(1, 0, 0), 2.6f, 24);
                FxSend.impactFrame(sl, m.position().add(0, 1.5, 0), 4, FxSend.FRAME_LIGHT, 24);
                room.timer = 60;
            }
            case GATE -> {   // two gatekeepers, one after the other
                if (tick % 10 != 0) return;
                if (room.timer > 0) { if (--room.timer == 0) gatekeeper(sl, r, idx, room, room.wave); return; }
                if (alive(sl, r, idx) > 0) return;
                if (room.wave >= 1) {
                    clear(sl, r, idx, room);
                    r.relicTaken = true;
                    for (ServerPlayer p : sl.players()) if (riftAt(sl.getServer(), p.position()) == r) {
                        BossLadder.passGate(p);
                        Bounties.addStars(p, 5);
                        p.sendSystemMessage(Component.literal("THE GATE IS PASSED  ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD).append(Component.literal("+5 merit stars. Stand on the circle to leave.").withStyle(ChatFormatting.YELLOW)));
                    }
                    return;
                }
                room.wave++;
                room.timer = 4;
                FxSend.ring(sl, Fx.CRIMSON, c.add(0, 0.1, 0), 0.4f, 6f, 0.12f, 14);
                for (ServerPlayer p : here) p.displayClientMessage(Component.literal("The second gatekeeper comes...").withStyle(ChatFormatting.DARK_RED), true);
            }
            case EXAM -> {   // three shades, one after the other, with a breath between them
                if (tick % 10 != 0) return;
                if (room.timer > 0) { if (--room.timer == 0) examShade(sl, r, idx, room, room.wave); return; }
                if (alive(sl, r, idx) > 0) return;
                if (room.wave >= 2) { clear(sl, r, idx, room); passExam(sl, r, room); return; }
                room.wave++;
                room.timer = 4;
                FxSend.ring(sl, Fx.GOLD, c.add(0, 0.1, 0), 0.4f, 6f, 0.12f, 14);
                for (ServerPlayer p : here) p.displayClientMessage(Component.literal("Shade " + (room.wave + 1) + "/3 approaches...").withStyle(ChatFormatting.GOLD), true);
            }
            default -> {}
        }
    }

    static int alive(ServerLevel sl, Rift r, int idx) {
        List<UUID> l = r.mobs.get(idx);
        if (l == null) return 0;
        l.removeIf(u -> { Entity e = sl.getEntity(u); return e == null || !e.isAlive(); });
        return l.size();
    }

    static void wave(ServerLevel sl, Rift r, int idx, Room room, int players) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        Vec3 c = r.centre(room);
        int n = 2 + r.rank + (players - 1) * 2;
        List<UUID> l = r.mobs.computeIfAbsent(idx, k -> new ArrayList<>());
        for (int i = 0; i < n; i++) {
            EntityType<? extends Mob> t = switch (rnd.nextInt(5)) {
                case 0, 1 -> AddonEntities.MAGE.get();
                case 2 -> EntityType.SKELETON;
                case 3 -> EntityType.ZOMBIE;
                default -> r.rank >= 3 ? EntityType.WITHER_SKELETON : EntityType.HUSK;
            };
            Mob m = t.create(sl);
            if (m == null) continue;
            double a = rnd.nextDouble(Math.PI * 2), d = rnd.nextDouble(3, 6);
            m.moveTo(c.x + Math.cos(a) * d, Y, c.z + Math.sin(a) * d, rnd.nextFloat() * 360, 0);
            m.finalizeSpawn(sl, sl.getCurrentDifficultyAt(m.blockPosition()), MobSpawnType.EVENT, null, null);
            m.setPersistenceRequired();
            var hp = m.getAttribute(Attributes.MAX_HEALTH);
            if (hp != null) { hp.setBaseValue(hp.getBaseValue() * (1 + r.rank * 0.25)); m.setHealth(m.getMaxHealth()); }
            sl.addFreshEntity(m);
            l.add(m.getUUID());
            RiftFx.cut(sl, m.position().add(0, 1, 0), new Vec3(0, 1, 0), new Vec3(1, 0, 0), 1.0f, 12);
        }
        sl.playSound(null, BlockPos.containing(c), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.HOSTILE, 1.2f, 0.8f);
    }

    /** The Reflection: the challenger's own skin and their grimoire's colours. */
    static void reflection(ServerLevel sl, Rift r, int idx, Room room) {
        Vec3 c = r.centre(room);
        var m = AddonEntities.REFLECTION.get().create(sl);
        if (m == null) return;
        m.moveTo(c.x, Y, c.z + 3, 180, 0);
        try {
            UUID o = UUID.fromString(r.owner);
            m.setOwner(o);
            ServerPlayer owner = sl.getServer().getPlayerList().getPlayer(o);
            if (owner != null) {
                GrimoireMastery.Book b = GrimoireMastery.bookOf(io.redspace.ironsspellbooks.api.util.Utils.getPlayerSpellbookStack(owner));
                if (b != null) m.pal = b.palette();
                var bk = io.redspace.ironsspellbooks.api.util.Utils.getPlayerSpellbookStack(owner);   // its family fights its own way
                if (bk != null) m.family = bk.is(com.ironspellgrimoire.registry.AddonItemRegistry.SHADOW_GRIMOIRE.get()) ? 1
                        : bk.is(com.ironspellgrimoire.registry.AddonItemRegistry.PARASITE_GRIMOIRE.get()) ? 2
                        : bk.is(com.ironspellgrimoire.registry.AddonItemRegistry.HUNGER_GRIMOIRE.get()) ? 3 : 0;
                m.setBarName(Component.literal(owner.getGameProfile().getName() + "'s Reflection"));
            }
        } catch (IllegalArgumentException ignored) {}
        m.focus = null;
        sl.addFreshEntity(m);
        r.mobs.computeIfAbsent(idx, k -> new ArrayList<>()).add(m.getUUID());
        FxSend.impactFrame(sl, m.position().add(0, 1.5, 0), 4, FxSend.FRAME_GOLD, 30);
        FxSend.ring(sl, Fx.SILVER | com.ironspellgrimoire.network.FxPacket.KEEP_SHAPE, c.add(0, 0.1, 0), 0.5f, 7f, 0.2f, 18);
        sl.playSound(null, BlockPos.containing(c), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 1.4f, 0.6f);
    }

    /** The Exam Hall's shades: a lesser boss, then a greater, then one of the strongest. */
    static void examShade(ServerLevel sl, Rift r, int idx, Room room, int n) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        EntityType<? extends Mob> t = switch (n) {
            case 0 -> rnd.nextBoolean() ? AddonEntities.REVENANT.get() : AddonEntities.CURSE_WITCH.get();
            case 1 -> rnd.nextBoolean() ? AddonEntities.BEAST_OF_DESPAIR.get() : AddonEntities.RHYA.get();
            default -> rnd.nextBoolean() ? AddonEntities.ZENON.get() : AddonEntities.LICHT.get();
        };
        shade(sl, r, idx, room, t, n == 0 ? 0.45f : n == 1 ? 0.4f : 0.5f, "Examiner");
    }

    /** The Gate's keepers: a shade of a tier I boss, then of a tier II one (60% of their strength). */
    static void gatekeeper(ServerLevel sl, Rift r, int idx, Room room, int n) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        EntityType<? extends Mob> t = n == 0 ? (rnd.nextBoolean() ? AddonEntities.REVENANT.get() : AddonEntities.CURSE_WITCH.get())
                : switch (rnd.nextInt(3)) { case 0 -> AddonEntities.ZENON.get(); case 1 -> AddonEntities.RHYA.get(); default -> AddonEntities.BEAST_OF_DESPAIR.get(); };
        shade(sl, r, idx, room, t, 0.6f, "Gatekeeper");
    }

    /** A bounty boss's lair, opened where it was sighted: an entrance, then the lair. Returns the rift's id. */
    static int openLair(ServerPlayer p, Vec3 at, CompoundTag bounty) {
        Data d = data(p.server);
        Rift r = new Rift();
        r.id = d.next++;
        r.lair = true;
        r.rank = Math.min(5, 1 + BossLadder.TIER[Math.min(7, bounty.getInt("Boss"))] + (bounty.getBoolean("Hardened") ? 1 : 0));
        r.owner = p.getUUID().toString();
        r.lairBounty = bounty.copy();
        r.dim = p.level().dimension().location().toString();
        r.x = at.x; r.y = at.y; r.z = at.z;
        r.until = bounty.getLong("Deadline");
        Room a = new Room(0, 0, START), b = new Room(1, 0, LAIR);
        link(a, b);
        r.rooms.add(a); r.rooms.add(b);
        d.rifts.add(r);
        d.setDirty();
        ServerLevel sl = p.serverLevel();
        RiftFx.cut(sl, at.add(0, 1.6, 0), new Vec3(0, 1, 0), new Vec3(1, 0, 0), 2.2f, 30);
        FxSend.shake(sl, at, 0.6f, 12, 40);
        return r.id;
    }

    /** The Gate (BossLadder): taken from a Poster Board, straight in. */
    public static void startGate(ServerPlayer p) {
        Data d = data(p.server);
        Rift r = new Rift();
        r.id = d.next++;
        r.rank = 4;
        r.exam = true;   // no crack in the world: the board takes you in
        r.gate = true;
        r.owner = p.getUUID().toString();
        r.dim = p.level().dimension().location().toString();
        r.x = p.getX() - 2.5; r.y = p.getY(); r.z = p.getZ() - 2.5;
        r.until = p.level().getGameTime() + 24000;
        Room a = new Room(0, 0, START), b = new Room(1, 0, GATE);
        link(a, b);
        r.rooms.add(a); r.rooms.add(b);
        d.rifts.add(r);
        d.setDirty();
        FxSend.ring(p.serverLevel(), Fx.CRIMSON, p.position().add(0, 0.1, 0), 0.3f, 4f, 0.12f, 16);
        enter(p, r);
    }

    static void closeLair(MinecraftServer s, int id) {
        for (Rift r : data(s).rifts) if (r.id == id && r.lair && !r.closed) {
            ServerLevel rift = s.getLevel(RIFT);
            if (rift != null) for (ServerPlayer p : new ArrayList<>(rift.players())) if (riftAt(s, p.position()) == r) leave(p);
            close(s, r);
        }
    }
    static boolean lairOpen(MinecraftServer s, int id) {
        for (Rift r : data(s).rifts) if (r.id == id && r.lair && !r.closed) return true;
        return false;
    }

    /** A shade of one of the bosses, at a fraction of its strength. */
    static void guardian(ServerLevel sl, Rift r, int idx, Room room) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        Vec3 c = r.centre(room);
        EntityType<? extends Mob> t = room.type == RELIC ? switch (rnd.nextInt(4)) {   // the big ones guard relics; the lesser ones, the elite rooms
            case 0 -> AddonEntities.ZENON.get();
            case 1 -> AddonEntities.BEAST_OF_DESPAIR.get();
            case 2 -> AddonEntities.RHYA.get();
            default -> AddonEntities.LICHT.get();
        } : switch (rnd.nextInt(4)) {
            case 0 -> AddonEntities.REVENANT.get();
            case 1 -> AddonEntities.CURSE_WITCH.get();
            case 2 -> AddonEntities.BEAST_OF_DESPAIR.get();
            default -> AddonEntities.RHYA.get();
        };
        shade(sl, r, idx, room, t, room.type == RELIC ? 0.3f + r.rank * 0.06f : 0.18f + r.rank * 0.03f, "Shade");
    }

    public static final String SHADE_TAG = "isg_rift_shade";

    /** A boss's shade from a rift: it drops nothing - no core, no trophy, no memory page, none of its own loot. */
    public static boolean isShade(net.minecraft.world.entity.Entity e) { return e != null && e.getPersistentData().getBoolean(SHADE_TAG); }

    @SubscribeEvent
    public static void onShadeDrops(net.minecraftforge.event.entity.living.LivingDropsEvent e) {
        if (isShade(e.getEntity())) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onShadeXp(net.minecraftforge.event.entity.living.LivingExperienceDropEvent e) {
        if (isShade(e.getEntity())) e.setDroppedExperience(Math.min(e.getDroppedExperience(), 20));
    }

    static void shade(ServerLevel sl, Rift r, int idx, Room room, EntityType<? extends Mob> t, float frac, String title) {
        Vec3 c = r.centre(room);
        Mob m = t.create(sl);
        if (m == null) return;
        m.moveTo(c.x, Y, c.z + 3, 180, 0);
        m.finalizeSpawn(sl, sl.getCurrentDifficultyAt(m.blockPosition()), MobSpawnType.EVENT, null, null);
        m.getPersistentData().putBoolean(SHADE_TAG, true);   // a shade: it leaves nothing behind (no boss drops)
        sl.addFreshEntity(m);
        m.setHealth(m.getMaxHealth() * Math.min(1f, frac));
        m.setCustomName(Component.literal(title + " of " + m.getType().getDescription().getString()).withStyle(r.exam ? ChatFormatting.GOLD : ChatFormatting.LIGHT_PURPLE));
        r.mobs.computeIfAbsent(idx, k -> new ArrayList<>()).add(m.getUUID());
        RiftFx.cut(sl, m.position().add(0, 1.4, 0), new Vec3(0, 1, 0), new Vec3(1, 0, 0), 2.0f, 20);
        FxSend.impactFrame(sl, m.position().add(0, 1.5, 0), 3, FxSend.FRAME_FINRAL, 24);
    }

    static void reward(ServerLevel sl, Rift r, Room room, ServerPlayer finder, boolean relic) {
        Vec3 c = r.centre(room).add(0, 0.5, 0);
        if (relic) for (ServerPlayer p : sl.players()) if (riftAt(sl.getServer(), p.position()) == r) BookQuests.riftCleared(p);   // the mastery quest
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        List<ItemStack> loot = new ArrayList<>();
        loot.add(new ItemStack(Items.EMERALD, (relic ? 10 : 5) + r.rank * 3));
        if (relic) {
            List<net.minecraftforge.registries.RegistryObject<net.minecraft.world.item.Item>> relics = AddonItemRegistry.RELICS;
            loot.add(new ItemStack(relics.get(rnd.nextInt(relics.size())).get()));
            List<Integer> laws = new ArrayList<>();
            for (int i = 0; i < 4; i++) if ((r.rules & (1 << i)) != 0) laws.add(i);
            ItemStack ink = new ItemStack(AddonItemRegistry.RIFT_INK.get());
            ink.getOrCreateTag().putInt("Rule", laws.get(rnd.nextInt(laws.size())));
            loot.add(ink);
            r.relicTaken = true;
        } else {
            if (rnd.nextInt(3) == 0) loot.add(new ItemStack((rnd.nextBoolean() ? AddonItemRegistry.RUNED_SPINE : AddonItemRegistry.BLOODSTONE).get()));   // the elite's spoils (Devil Shards come only from Zenon)
            if (rnd.nextInt(4) == 0) {
                ItemStack ink = new ItemStack(AddonItemRegistry.RIFT_INK.get());
                ink.getOrCreateTag().putInt("Rule", r.rank >= 3 && rnd.nextBoolean() ? 4 + rnd.nextInt(6) : Integer.numberOfTrailingZeros(r.rules));   // deeper rifts: sometimes a rare ink
                loot.add(ink);
            }
        }
        for (ItemStack s : loot) {
            var it = new net.minecraft.world.entity.item.ItemEntity(sl, c.x, c.y, c.z, s);
            it.setDeltaMovement(rnd.nextDouble(-0.1, 0.1), 0.3, rnd.nextDouble(-0.1, 0.1));
            sl.addFreshEntity(it);
        }
        FxSend.ring(sl, Fx.GOLD, c, 0.3f, 3f, 0.1f, 16);
        sl.playSound(null, BlockPos.containing(c), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1f, 1f);
        if (relic) for (ServerPlayer p : sl.players()) if (riftAt(sl.getServer(), p.position()) == r) {
            Quests.riftCleared(p, r.id);
            FortressFx.riftCleared(p);
            GrimoireMastery.bountyXp(p, 400 + r.rank * 200);
            Bounties.addStars(p, 2 + r.rank * 2);
            p.sendSystemMessage(Component.literal("RIFT CLEARED  ").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD)
                    .append(Component.literal("+" + (2 + r.rank * 2) + " merit stars. Stand on the circle to leave.").withStyle(ChatFormatting.YELLOW)));
        }
    }

    /** The way out: a circle in the rest room (and in the relic chamber once it's won). Stand on it for three seconds. */
    static void pad(ServerLevel sl, Rift r, Room room, List<ServerPlayer> here, long tick) {
        Vec3 c = r.centre(room);
        if (tick % 20 == 0 && !here.isEmpty()) FxSend.ring(sl, VOIDP, c.add(0, 0.05, 0), 1.3f, 1.4f, 0.06f, 22);
        for (ServerPlayer p : here) {
            if (room.type == REST && tick % 20 == 0 && p.getHealth() < p.getMaxHealth()) p.heal(1f);   // the rest room mends
            double dx = p.getX() - c.x, dz = p.getZ() - c.z;
            if (dx * dx + dz * dz < 1.4 * 1.4) {
                int t = r.padTicks.merge(p.getUUID(), 1, Integer::sum);
                if (t % 20 == 0) p.displayClientMessage(Component.literal("Leaving the rift... " + (3 - t / 20)).withStyle(ChatFormatting.LIGHT_PURPLE), true);
                if (t >= 60) { r.padTicks.remove(p.getUUID()); leave(p); }
            } else r.padTicks.remove(p.getUUID());
        }
    }

    /**
     * The rift's map for the HUD (client/RiftMap, shape 79): every room packed into one int - grid x and z, its kind,
     * cleared, and its doors - carried in the packet's 18 number slots. id = how many rooms, sweep = the one you're in + 1.
     */
    static void map(ServerPlayer p, Rift r, Room at) {
        FxPacket k = new FxPacket();
        k.shape = 79; k.life = 40;
        int n = Math.min(18, r.rooms.size());
        k.id = n; k.sweep = at == null ? 0 : r.rooms.indexOf(at) + 1;
        double[] v = new double[18];
        for (int i = 0; i < n; i++) {
            Room room = r.rooms.get(i);
            int doors = 0;
            for (int dd : room.doors) doors |= 1 << dd;
            int shown = room.type == LAIR ? RELIC : room.type == GATE ? EXAM : room.type;   // the map knows eight kinds
            v[i] = ((room.gx + 16) & 31) | ((room.gz + 16) & 31) << 5 | (shown & 7) << 10 | (room.cleared ? 1 : 0) << 13 | (room.active ? 1 : 0) << 14 | doors << 15;
        }
        k.c = new Vec3(v[0], v[1], v[2]); k.d = new Vec3(v[3], v[4], v[5]); k.u = new Vec3(v[6], v[7], v[8]); k.v = new Vec3(v[9], v[10], v[11]);
        k.r = (float) v[12]; k.r2 = (float) v[13]; k.a0 = (float) v[14]; k.a1 = (float) v[15]; k.h = (float) v[16]; k.speed = (float) v[17];
        AddonNetwork.send(PacketDistributor.PLAYER.with(() -> p), k);
    }

    static void objective(ServerPlayer p, Rift r, Room room) {
        if (room == null) return;
        String s = switch (room.type) {
            case COMBAT -> room.cleared ? "Combat - cleared" : room.active ? "Combat - wave " + (room.wave + 1) + "/" + (2 + r.rank / 2) + ", " + r.mobs.getOrDefault(r.rooms.indexOf(room), List.of()).size() + " left" : "Combat";
            case TRAP -> room.cleared ? "Traps - cleared" : "Survive the traps: " + Math.max(0, room.timer / 20) + " s";
            case REST -> "Rest - stand on the circle to leave the rift";
            case ELITE -> room.cleared ? "Elite - cleared" : "Elite - defeat the shade";
            case RELIC -> room.cleared ? "Relic Chamber - stand on the circle to leave" : "Relic Chamber - defeat the guardian";
            case EXAM -> room.cleared ? "Exam passed - stand on the circle to leave" : "Exam Hall - shade " + (room.wave + 1) + "/3";
            case MIRROR -> room.cleared ? "Ascended - stand on the circle to leave" : "Hall of Mirrors - defeat your Reflection";
            case LAIR -> room.cleared ? "Subjugated - stand on the circle to leave" : "The lair - bring it down";
            case GATE -> room.cleared ? "The Gate is passed - stand on the circle to leave" : "The Gate - gatekeeper " + (room.wave + 1) + "/2";
            default -> r.exam ? "The Magic Knight Exam - go on through the floors" : "Find the relic chamber";
        };
        if (r.exam && !r.ascension && !r.gate && room.type != EXAM && room.type != START) s = "Floor " + r.rooms.indexOf(room) + "/5 - " + s;
        p.displayClientMessage(Component.literal(s).withStyle(r.exam ? ChatFormatting.GOLD : ChatFormatting.LIGHT_PURPLE), true);
    }

    /** Hunted: a shade that drifts through the walls after the nearest of you. It can't be hurt; it can hurt you. */
    static void hunt(ServerLevel sl, Rift r, List<ServerPlayer> inside, long tick) {
        if (r.shade == null) {
            r.shade = r.centre(r.rooms.get(0)).add(0, 1.2, 0);
            for (ServerPlayer p : inside) p.sendSystemMessage(Component.literal("Something in the rift has your scent...").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
        }
        ServerPlayer near = null;
        double bd = Double.MAX_VALUE;
        for (ServerPlayer p : inside) { double d = p.position().add(0, 1, 0).distanceToSqr(r.shade); if (d < bd) { bd = d; near = p; } }
        if (near == null) return;
        Vec3 to = near.position().add(0, 1, 0).subtract(r.shade);
        double speed = 0.11 + r.rank * 0.012;
        if (to.length() > speed) r.shade = r.shade.add(to.normalize().scale(speed));
        if (tick % 3 == 0) Kit.orb(sl, Fx.DARK, r.shade, to.lengthSqr() > 1e-4 ? to.normalize().scale(speed) : Vec3.ZERO, 0.55f, 4);
        if (tick % 20 == 0) {
            FxSend.ring(sl, Fx.DARK, r.shade, 0.3f, 1.2f, 0.05f, 10);
            if (Math.sqrt(bd) < 1.6) {
                near.hurt(near.damageSources().magic(), 4 + r.rank);
                Vec3 push = near.position().subtract(r.shade);
                near.setDeltaMovement(push.lengthSqr() > 1e-4 ? push.normalize().scale(0.9).add(0, 0.3, 0) : new Vec3(0, 0.5, 0));
                near.hurtMarked = true;
                sl.playSound(null, near.blockPosition(), SoundEvents.VEX_CHARGE, SoundSource.HOSTILE, 1f, 0.6f);
            }
        }
    }

    // ================================================================== new rifts in the world, dying inside, the compass
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        if (p.tickCount % 10 == 0 && (p.getMainHandItem().is(AddonItemRegistry.RIFT_COMPASS.get()) || p.getOffhandItem().is(AddonItemRegistry.RIFT_COMPASS.get()))) compass(p);
        if (p.tickCount % 6000 != 3000 || !p.level().dimension().equals(Level.OVERWORLD) || p.isSpectator()) return;
        if (!com.ironspellgrimoire.AddonConfig.rifts() || ThreadLocalRandom.current().nextDouble() >= com.ironspellgrimoire.AddonConfig.riftChance()) return;
        for (Rift r : data(p.server).rifts) if (!r.closed && !r.exam && r.dim.equals(p.level().dimension().location().toString()) && new Vec3(r.x, r.y, r.z).distanceToSqr(p.position()) < 400 * 400) return;
        Vec3 at = spot(p.serverLevel(), p.position(), 25, 50);
        int cap = Bounties.stars(p) >= 60 ? 5 : Bounties.stars(p) >= 30 ? 4 : 3;   // rank 4 rifts from 30 merit stars, rank 5 from 60
        if (at != null) open(p, at, Math.min(cap, 1 + Bounties.stars(p) / 15 + ThreadLocalRandom.current().nextInt(3)));
    }

    static Vec3 spot(ServerLevel sl, Vec3 from, double min, double max) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int i = 0; i < 10; i++) {
            double a = rnd.nextDouble(Math.PI * 2), d = rnd.nextDouble(min, max);
            int x = (int) Math.floor(from.x + Math.cos(a) * d), z = (int) Math.floor(from.z + Math.sin(a) * d);
            if (!sl.hasChunk(x >> 4, z >> 4)) continue;
            int y = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (!sl.getBlockState(new BlockPos(x, y - 1, z)).getFluidState().isEmpty()) continue;
            return new Vec3(x + 0.5, y, z + 0.5);
        }
        return null;
    }

    static void compass(ServerPlayer p) {
        Rift best = null;
        double bd = Double.MAX_VALUE;
        String dim = p.level().dimension().location().toString();
        long now = p.server.overworld().getGameTime();
        for (Rift r : data(p.server).rifts) {
            if (r.closed || r.exam || !r.dim.equals(dim) || now >= r.until || r.relicTaken) continue;
            double d = new Vec3(r.x, r.y, r.z).distanceToSqr(p.position());
            if (d < bd) { bd = d; best = r; }
        }
        FxPacket k = new FxPacket();
        k.shape = 68;
        if (best != null) { k.sweep = best.rank; k.c = new Vec3(best.x, best.y, best.z); k.life = 14; }
        AddonNetwork.send(PacketDistributor.PLAYER.with(() -> p), k);
    }

    /** Dying in a rift throws you back out at its entrance, with everything you carry. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onDeath(LivingDeathEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !p.level().dimension().equals(RIFT)) return;
        if (e.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY) && !e.getSource().is(net.minecraft.world.damagesource.DamageTypes.FELL_OUT_OF_WORLD)) return;
        e.setCanceled(true);
        p.setHealth(Math.max(2f, p.getMaxHealth() * 0.3f));
        p.removeAllEffects();
        p.invulnerableTime = 60;
        Rift r = riftOf(p);
        int[] story = StoryStage.where(p);
        leave(p);
        if (story != null) { StoryArc1.onFall(p, story); return; }
        if (r != null && r.ascension) p.sendSystemMessage(Component.literal("Your Reflection won this time. The altar will let you try again.").withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC));
        else if (r != null && r.gate) p.sendSystemMessage(Component.literal("You fell at the Gate. The Poster Board will offer it again.").withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
        else if (r != null && r.lair) p.sendSystemMessage(Component.literal("You were thrown out of the lair. Step back in while the poster lasts.").withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
        else if (r != null && r.exam) p.sendSystemMessage(Component.literal("You fell in the Magic Knight Exam. The altar will let you try again.").withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC));
        else p.sendSystemMessage(Component.literal("The rift threw you out.").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
    }

    // ================================================================== the rift can't be broken
    @SubscribeEvent
    public static void onBreak(net.minecraftforge.event.level.BlockEvent.BreakEvent e) {
        if (e.getLevel() instanceof Level l && l.dimension().equals(RIFT) && !(e.getPlayer() != null && e.getPlayer().isCreative() && e.getPlayer().hasPermissions(2))) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onPlace(net.minecraftforge.event.level.BlockEvent.EntityPlaceEvent e) {
        if (e.getLevel() instanceof Level l && l.dimension().equals(RIFT) && !(e.getEntity() instanceof ServerPlayer p && p.isCreative() && p.hasPermissions(2))) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onExplode(net.minecraftforge.event.level.ExplosionEvent.Detonate e) {
        if (e.getLevel().dimension().equals(RIFT)) e.getAffectedBlocks().clear();
    }

    // ================================================================== /rift
    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("rift").requires(s -> s.hasPermission(2))
                .then(Commands.literal("open").executes(c -> { ServerPlayer p = c.getSource().getPlayerOrException(); open(p, p.position().add(p.getLookAngle().multiply(4, 0, 4)), 2); return 1; })
                        .then(Commands.argument("rank", IntegerArgumentType.integer(1, 5)).executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            open(p, p.position().add(p.getLookAngle().multiply(4, 0, 4)), IntegerArgumentType.getInteger(c, "rank"));
                            return 1;
                        })))
                .then(Commands.literal("leave").executes(c -> { ServerPlayer p = c.getSource().getPlayerOrException(); if (p.level().dimension().equals(RIFT)) leave(p); return 1; }))
                .then(Commands.literal("closeall").executes(c -> { for (Rift r : data(c.getSource().getServer()).rifts) r.until = 0; return 1; })));
    }
}
