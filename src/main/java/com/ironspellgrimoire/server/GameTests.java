package com.ironspellgrimoire.server;

import com.ironspellgrimoire.entity.BeastOfDespair;
import com.ironspellgrimoire.network.AddonNetwork;
import com.ironspellgrimoire.registry.AddonEntities;
import com.ironspellgrimoire.registry.AddonSpellRegistry;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The automatic game tests - a tool, run only by the "Game tests" workflow (client/UiShots starts them in a window-less
 * game on the build server, with a real player in a flat world). Nothing here runs in normal play.
 * <ul>
 *   <li><b>Every spell</b> is cast by the test player at a target dummy: it must not change any block, must not give a
 *       status effect, and must not throw; how many effect packets it sends is measured (too many = too heavy to see
 *       through), and a spell that does nothing at all is noted.</li>
 *   <li><b>Every boss</b> is spawned beside the player and fought for 40 seconds: it must survive a one-shot, take
 *       damage, not change blocks, and should attack, move and reach its second phase.</li>
 *   <li><b>Systems:</b> the boards (take, give up, new notes), the Magic Knight Exam rift (in and out), an Elf Raid
 *       (four waves, then the spoils chest), and saving and loading (player data, the rifts).</li>
 * </ul>
 * Each result is a line: PASS, FAIL or WARN, the test, and what was seen.
 */
@Mod.EventBusSubscriber(modid = "iron_spell_grimoire")
public final class GameTests {
    private GameTests() {}

    public static volatile boolean running, done;
    public static final List<String> results = new ArrayList<>();
    static ServerPlayer p;
    static Vec3 home;
    static final List<Test> queue = new ArrayList<>();
    static int index, t;
    static net.minecraft.world.entity.monster.Vindicator dummy;
    static int playerHits;
    static final UUID HP = UUID.fromString("a1f70000-0000-4000-8000-00000000e57a");

    abstract static class Test {
        final String name;
        Test(String name) { this.name = name; }
        /** The code this test depends on (its fingerprint, ClassKey); null = always run it. */
        Class<?>[] roots() { return null; }
        String extra() { return name; }
        abstract void start();
        /** Called every tick from 1; returns true when finished. */
        abstract boolean tick(int t);
    }

    /** The Architect's per-move setup: a fresh fortress with every room open, Stone to spare, nothing left from the last move. */
    static void archSetup(ServerPlayer pl) {
        FortressFx.testReset(pl); FortressSpells.testClear();
        FortressFx.testUnlockAll(pl); FortressFx.testStone(pl, 100000); FortressFx.testClearCds(pl);
    }
    static void archMove(ServerPlayer pl, int room) {
        archSetup(pl);
        if (room >= FortressFx.BASE_ROOMS) FortressFx.testStars(pl, FortressFx.MAX_STARS);   // the prestige rooms open with stars
        FortressFx.handle(pl, 1, room * 100);
        FortressFx.castRoom(pl, room);
    }

    static void pass(String test, String what) { results.add("PASS | " + test + " | " + what); }
    static void fail(String test, String what) { results.add("FAIL | " + test + " | " + what); }
    static void warn(String test, String what) { results.add("WARN | " + test + " | " + what); }
    /** A line of detail from inside a spell while the tests run (to explain a failure). */
    static void note(String what) { if (p != null) results.add("NOTE | " + what); }
    static boolean running() { return p != null; }
    static void info(String test, String what) { results.add("INFO | " + test + " | " + what); }

    // ------------------------------------------------------------------ helpers
    static ServerLevel sl() { return p.serverLevel(); }

    /** The blocks in a box round the test spot, to see whether anything changed them. */
    static final int R = 24, RY = 14;
    static BlockState[] snapshot() {
        ServerLevel sl = sl();
        BlockPos c = BlockPos.containing(home);
        BlockState[] s = new BlockState[(2 * R + 1) * (2 * R + 1) * (2 * RY + 1)];
        int i = 0;
        for (int x = -R; x <= R; x++) for (int y = -4; y <= 2 * RY - 4; y++) for (int z = -R; z <= R; z++) s[i++] = sl.getBlockState(c.offset(x, y, z));
        return s;
    }

    /** What changed since the snapshot ("" if nothing), putting it back as it was. */
    static String changes(BlockState[] before) {
        ServerLevel sl = sl();
        BlockPos c = BlockPos.containing(home);
        int i = 0, n = 0;
        StringBuilder sb = new StringBuilder();
        for (int x = -R; x <= R; x++) for (int y = -4; y <= 2 * RY - 4; y++) for (int z = -R; z <= R; z++) {
            BlockPos q = c.offset(x, y, z);
            BlockState now = sl.getBlockState(q), was = before[i++];
            if (now != was) {
                if (n++ < 3) sb.append(was.getBlock().getName().getString()).append(" -> ").append(now.getBlock().getName().getString()).append(" at ").append(x).append(",").append(y).append(",").append(z).append("; ");
                sl.setBlock(q, was, 2);
            }
        }
        return n == 0 ? "" : n + " block(s) changed: " + sb;
    }

    static Set<MobEffect> effects(LivingEntity e) {
        Set<MobEffect> s = new HashSet<>();
        if (e != null) for (var i : e.getActiveEffects()) s.add(i.getEffect());
        return s;
    }

    static void resetPlayer(boolean survival) {
        ServerLevel sl = sl();
        p.setGameMode(survival ? GameType.SURVIVAL : GameType.CREATIVE);
        p.teleportTo(sl, home.x, home.y, home.z, 0f, 0f);   // facing south, towards the dummy
        p.setDeltaMovement(Vec3.ZERO);
        p.setHealth(p.getMaxHealth());
        p.getFoodData().setFoodLevel(20);
        p.clearFire();
        MagicData d = MagicData.getPlayerMagicData(p);
        d.getPlayerCooldowns().clearCooldowns();
        d.setMana((float) p.getAttributeValue(io.redspace.ironsspellbooks.api.registry.AttributeRegistry.MAX_MANA.get()));
    }

    static void ensureDummy() {
        if (dummy != null && dummy.isAlive()) { dummy.setHealth(dummy.getMaxHealth()); dummy.teleportTo(home.x, home.y, home.z + 6); dummy.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO); dummy.hurtMarked = true; return; }   // still, too: a knock-back from the last move mustn't carry it out of the next one
        ServerLevel sl = sl();
        dummy = EntityType.VINDICATOR.create(sl);   // not undead: holy magic hits undead harder, which would skew the damage numbers
        if (dummy == null) return;
        dummy.moveTo(home.x, home.y, home.z + 6, 180f, 0f);
        dummy.setNoAi(true);
        dummy.setPersistenceRequired();
        dummy.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
        dummy.setHealth(1000);
        dummy.setCustomName(net.minecraft.network.chat.Component.literal("Test Dummy"));
        sl.addFreshEntity(dummy);
    }

    /** Everything near the test spot except the player and the dummy goes (summons, projectiles, leftovers). */
    static void clearArea() {
        for (Entity e : sl().getEntities((Entity) null, new AABB(home, home).inflate(80), e -> !(e instanceof Player) && e != dummy)) e.discard();
    }

    static ItemStack testBook = ItemStack.EMPTY;
    static void cast(AbstractSpell s) throws Exception {
        ItemStack book = testBook.isEmpty() ? UiShotsServer.book(p) : testBook;
        Method m = null;
        for (Method x : AbstractSpell.class.getMethods()) if (x.getName().equals("attemptInitiateCast")) { m = x; break; }
        if (m == null) throw new IllegalStateException("Iron's attemptInitiateCast not found");
        Object[] args = new Object[m.getParameterCount()];
        Class<?>[] ts = m.getParameterTypes();
        for (int i = 0; i < ts.length; i++) {
            Class<?> c = ts[i];
            if (c == ItemStack.class) args[i] = book;
            else if (c == int.class) args[i] = s.getMaxLevel();
            else if (Level.class.isAssignableFrom(c)) args[i] = sl();
            else if (Player.class.isAssignableFrom(c) || LivingEntity.class.isAssignableFrom(c)) args[i] = p;
            else if (c.isEnum()) args[i] = Enum.valueOf((Class) c, "SPELLBOOK");
            else if (c == boolean.class) args[i] = false;
            else if (c == String.class) args[i] = "mainhand";
            else args[i] = null;
        }
        Object r = m.invoke(s, args);
        if (!castInfoShown) { castInfoShown = true; info("casting", "using " + m.toGenericString() + " -> " + r); }
        lastCastResult = String.valueOf(r);
    }
    static boolean castInfoShown;
    static String lastCastResult = "";

    static void cancelCast() {
        try {
            Class<?> u = Class.forName("io.redspace.ironsspellbooks.api.util.Utils");
            u.getMethod("serverSideCancelCast", ServerPlayer.class).invoke(null, p);
        } catch (Throwable ignored) {}
    }

    static int phaseOf(Entity e) {
        try { return (int) e.getClass().getMethod("phase").invoke(e); } catch (Throwable x) { return -1; }
    }

    @SubscribeEvent
    public static void onDamage(LivingDamageEvent e) {
        if (running && e.getEntity() == p) { playerHits++; e.setAmount(0); }   // the test player feels nothing, but we count the blows
    }

    // ------------------------------------------------------------------ running them
    public static void start(ServerPlayer player) {
        p = player;
        BlockPos g = sl().getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, BlockPos.containing(player.position()));
        home = new Vec3(g.getX() + 0.5, g.getY(), g.getZ() + 0.5);
        queue.clear(); results.clear(); index = 0; t = 0;
        cache = new ClassKey.Cache(net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("isg_test_cache.txt"));
        p.server.setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);   // Peaceful would delete every boss and elf at once
        sl().setDayTime(6000);
        // a creeper wandering in (or an enderman) would "change the world" in some boss's test: no griefing, always noon
        sl().getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING).set(false, p.server);
        sl().getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING).set(false, p.server);   // no wild slime wandering in front of a spell
        sl().getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false, p.server);
        UiShotsServer.prepare(p);
        TickProfiler.start();   // where the time goes, reported at the end ("PROF" lines)
        GameTestCases.build(queue);   // the tests themselves (their own file: adding one leaves the others' fingerprints alone)
        // a shard ("i/n", -Disg.shard): the CI splits the tests over parallel jobs, each running every n-th one
        String shard = System.getProperty("isg.shard", "");
        if (shard.matches("\\d+/\\d+")) {
            int si = Integer.parseInt(shard.split("/")[0]), sn = Math.max(1, Integer.parseInt(shard.split("/")[1]));
            List<Test> mine = new ArrayList<>();
            for (int i = 0; i < queue.size(); i++) if (i % sn == si) mine.add(queue.get(i));
            queue.clear(); queue.addAll(mine);
        }
        running = true;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent e) {
        if (!running || e.phase != TickEvent.Phase.END) return;
        if (p == null || p.isRemoved() && !p.hasDisconnected()) { /* respawned: find the new player */
            var l = e.getServer().getPlayerList().getPlayers();
            if (!l.isEmpty()) p = l.get(0);
        }
        if (index >= queue.size()) { TickProfiler.stop(); results.addAll(TickProfiler.report()); running = false; done = true; if (dummy != null) dummy.discard(); if (cache != null) cache.save(); return; }
        Test cur = queue.get(index);
        try {
            if (t == 0) {
                Class<?>[] roots = cur.roots();
                curKey = roots == null ? null : ClassKey.of(cur.extra() + "|" + HARNESS + "|" + ClassKey.single(cur.getClass()), roots);
                if (curKey != null && cache != null && cache.unchanged(cur.name, curKey)) {   // passed before, nothing it depends on changed
                    results.add("SKIP | " + cur.name + " | unchanged since it passed");
                    cache.passed(cur.name, curKey);
                    index++;
                    return;
                }
                startAt = results.size();
                cur.start();
            }
            t++;
            if (cur.tick(t)) { finished(cur); results.add("TIME | " + cur.name + " | " + t + " ticks"); index++; t = 0; }
        } catch (Throwable x) {
            fail(cur.name, "the test itself threw: " + x);
            index++; t = 0;
        }
    }

    static ClassKey.Cache cache;
    static String curKey;
    static int startAt;
    static final String HARNESS = ClassKey.single(GameTests.class);

    /** A test is done: if nothing it added failed, its fingerprint is kept, so next time it's skipped unless its code changes. */
    static void finished(Test cur) {
        if (curKey == null || cache == null) return;
        for (int i = startAt; i < results.size(); i++) if (results.get(i).startsWith("FAIL")) return;
        cache.passed(cur.name, curKey);
    }

    /** How far through: "n / total". */
    public static String progress() { return index + " / " + queue.size(); }
}
