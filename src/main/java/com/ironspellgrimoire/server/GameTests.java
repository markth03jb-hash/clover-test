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
    private static ServerPlayer p;
    private static Vec3 home;
    private static final List<Test> queue = new ArrayList<>();
    private static int index, t;
    private static Husk dummy;
    private static int playerHits;
    private static final UUID HP = UUID.fromString("a1f70000-0000-4000-8000-00000000e57a");

    private abstract static class Test {
        final String name;
        Test(String name) { this.name = name; }
        abstract void start();
        /** Called every tick from 1; returns true when finished. */
        abstract boolean tick(int t);
    }

    private static void pass(String test, String what) { results.add("PASS | " + test + " | " + what); }
    private static void fail(String test, String what) { results.add("FAIL | " + test + " | " + what); }
    private static void warn(String test, String what) { results.add("WARN | " + test + " | " + what); }
    private static void info(String test, String what) { results.add("INFO | " + test + " | " + what); }

    // ------------------------------------------------------------------ helpers
    private static ServerLevel sl() { return p.serverLevel(); }

    /** The blocks in a box round the test spot, to see whether anything changed them. */
    private static final int R = 24, RY = 14;
    private static BlockState[] snapshot() {
        ServerLevel sl = sl();
        BlockPos c = BlockPos.containing(home);
        BlockState[] s = new BlockState[(2 * R + 1) * (2 * R + 1) * (2 * RY + 1)];
        int i = 0;
        for (int x = -R; x <= R; x++) for (int y = -4; y <= 2 * RY - 4; y++) for (int z = -R; z <= R; z++) s[i++] = sl.getBlockState(c.offset(x, y, z));
        return s;
    }

    /** What changed since the snapshot ("" if nothing), putting it back as it was. */
    private static String changes(BlockState[] before) {
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

    private static Set<MobEffect> effects(LivingEntity e) {
        Set<MobEffect> s = new HashSet<>();
        if (e != null) for (var i : e.getActiveEffects()) s.add(i.getEffect());
        return s;
    }

    private static void resetPlayer(boolean survival) {
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

    private static void ensureDummy() {
        if (dummy != null && dummy.isAlive()) { dummy.setHealth(dummy.getMaxHealth()); dummy.teleportTo(home.x, home.y, home.z + 6); return; }
        ServerLevel sl = sl();
        dummy = EntityType.HUSK.create(sl);
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
    private static void clearArea() {
        for (Entity e : sl().getEntities((Entity) null, new AABB(home, home).inflate(80), e -> !(e instanceof Player) && e != dummy)) e.discard();
    }

    private static void cast(AbstractSpell s) throws Exception {
        ItemStack book = UiShotsServer.book(p);
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
        m.invoke(s, args);
    }

    private static void cancelCast() {
        try {
            Class<?> u = Class.forName("io.redspace.ironsspellbooks.api.util.Utils");
            u.getMethod("serverSideCancelCast", ServerPlayer.class).invoke(null, p);
        } catch (Throwable ignored) {}
    }

    private static int phaseOf(Entity e) {
        try { return (int) e.getClass().getMethod("phase").invoke(e); } catch (Throwable x) { return -1; }
    }

    @SubscribeEvent
    public static void onDamage(LivingDamageEvent e) {
        if (running && e.getEntity() == p) { playerHits++; e.setAmount(0); }   // the test player feels nothing, but we count the blows
    }

    // ------------------------------------------------------------------ the tests
    private static final class SpellTest extends Test {
        final AbstractSpell spell;
        BlockState[] before; Set<MobEffect> pe, de; int fx0; float mana0, hp0; String castErr;
        SpellTest(AbstractSpell s) { super("spell " + s.getSpellId()); spell = s; }
        @Override void start() {
            cancelCast(); clearArea(); resetPlayer(true); ensureDummy();
            before = snapshot(); pe = effects(p); de = effects(dummy);
            mana0 = MagicData.getPlayerMagicData(p).getMana(); hp0 = dummy == null ? 0 : dummy.getHealth();
            fx0 = AddonNetwork.fxSent;
            try { cast(spell); } catch (Throwable x) { castErr = (x.getCause() != null ? x.getCause() : x).toString(); }
        }
        @Override boolean tick(int t) {
            p.setHealth(p.getMaxHealth());
            if (t < 60) return false;
            cancelCast();
            int fx = AddonNetwork.fxSent - fx0;
            float spent = mana0 - MagicData.getPlayerMagicData(p).getMana(), dealt = dummy == null ? 0 : hp0 - dummy.getHealth();
            boolean ok = true;
            if (castErr != null) { fail(name, "threw while casting: " + castErr); ok = false; }
            String ch = changes(before);
            if (!ch.isEmpty()) { fail(name, "changed the world: " + ch); ok = false; }
            Set<MobEffect> np = effects(p), nd = effects(dummy);
            np.removeAll(pe); nd.removeAll(de);
            if (!np.isEmpty() || !nd.isEmpty()) { fail(name, "gave status effects: " + np + " " + nd); ok = false; }
            if (fx > 360) warn(name, "heavy: " + fx + " effect packets in 3 s (" + fx / 3 + "/s)");
            if (fx == 0 && spent <= 0 && dealt <= 0) warn(name, "did nothing visible (no effects, no mana, no damage) - it may need a target, a form or a condition");
            if (ok) pass(name, String.format("%d fx packets, %.0f mana, %.1f damage", fx, spent, dealt));
            return true;
        }
    }

    private static final class BossTest extends Test {
        final Supplier<? extends EntityType<?>> type;
        Mob boss; BlockState[] before; int fx0, hits0, maxPhase = -1; double moved; Vec3 last; float maxHp, lowHp; boolean oneShotOk = true; String err;
        BossTest(String n, Supplier<? extends EntityType<?>> type) { super("boss " + n); this.type = type; }
        @Override void start() {
            cancelCast(); clearArea(); if (dummy != null) { dummy.discard(); dummy = null; }
            resetPlayer(true);
            var hp = p.getAttribute(Attributes.MAX_HEALTH);
            if (hp != null && hp.getModifier(HP) == null) hp.addTransientModifier(new AttributeModifier(HP, "Game test", 2000, AttributeModifier.Operation.ADDITION));
            p.setHealth(p.getMaxHealth());
            before = snapshot();
            Entity e = type.get().create(sl());
            if (!(e instanceof Mob m)) { err = "could not be created"; return; }
            boss = m;
            boss.moveTo(home.x, home.y, home.z + 12, 180f, 0f);
            if (boss instanceof com.ironspellgrimoire.entity.MemoryGuardian g) g.setup(0, p.getUUID());
            sl().addFreshEntity(boss);
            boss.setTarget(p);
            maxHp = boss.getMaxHealth(); lowHp = boss.getHealth();
            fx0 = AddonNetwork.fxSent; hits0 = playerHits; last = boss.position();
        }
        @Override boolean tick(int t) {
            p.setHealth(p.getMaxHealth());
            p.teleportTo(home.x, home.y, home.z);   // the player stands their ground
            if (err != null) { fail(name, err); return true; }
            if (boss.isAlive()) {
                moved += boss.position().distanceTo(last); last = boss.position();
                maxPhase = Math.max(maxPhase, phaseOf(boss));
                if (boss.getTarget() == null) boss.setTarget(p);
                if (t == 40) {   // a one-shot: it must not die to it, nor lose much more than a sixth of its health
                    float h = boss.getHealth();
                    boss.hurt(p.damageSources().playerAttack(p), 1.0e7f);
                    if (!boss.isAlive() || boss.getHealth() <= 0 || h - boss.getHealth() > maxHp * 0.16f) oneShotOk = false;
                } else if (t > 40 && t % 25 == 0) boss.hurt(p.damageSources().playerAttack(p), maxHp * 0.12f);
                lowHp = Math.min(lowHp, boss.getHealth());
            }
            if (t < 800) return false;
            int fx = AddonNetwork.fxSent - fx0, attacks = playerHits - hits0;
            boolean ok = true;
            if (!oneShotOk) { fail(name, "a one-shot killed it or took more than 16% of its health"); ok = false; }
            if (lowHp >= maxHp - 0.01f) { fail(name, "never took damage in 40 s"); ok = false; }
            String ch = changes(before);
            if (!ch.isEmpty()) { fail(name, "changed the world: " + ch); ok = false; }
            if (attacks == 0) warn(name, "never hurt the player in 40 s");
            if (moved < 1) warn(name, "never moved in 40 s");
            if (maxPhase >= 0 && maxPhase < 2) warn(name, "didn't reach its second phase (health down to " + Math.round(100 * lowHp / maxHp) + "%)");
            if (fx > 40 * 120) warn(name, "heavy: " + fx / 40 + " effect packets a second");
            if (ok) pass(name, String.format("%d hits on the player, moved %.0f blocks, phase %d, health down to %d%%, %d fx/s", attacks, moved, maxPhase, Math.round(100 * lowHp / maxHp), fx / 40));
            if (boss.isAlive()) boss.discard();
            clearArea();
            var hp = p.getAttribute(Attributes.MAX_HEALTH);
            if (hp != null) hp.removeModifier(HP);
            p.setHealth(p.getMaxHealth());
            return true;
        }
    }

    /** A test that's just a check, run at once. */
    private static Test check(String name, Runnable r) {
        return new Test(name) {
            @Override void start() {}
            @Override boolean tick(int t) {
                try { r.run(); } catch (Throwable x) { fail(name, "threw: " + x); }
                return true;
            }
        };
    }

    private static Test boards() {
        return check("boards", () -> {
            resetPlayer(false);
            BlockPos at = p.blockPosition();
            p.getPersistentData().remove("isg_bounty_offers"); p.getPersistentData().remove("isg_quest_offers");
            Boards.open(p, Boards.BOUNTY, at);
            int stars = Bounties.stars(p);
            if (stars < Boards.REROLL_COST) Bounties.addStars(p, Boards.REROLL_COST);
            stars = Bounties.stars(p);
            Boards.pick(p, Boards.BOUNTY, -2);
            if (Bounties.stars(p) == stars - Boards.REROLL_COST) pass("boards: new notes", "cost " + Boards.REROLL_COST + " stars"); else fail("boards: new notes", "stars " + stars + " -> " + Bounties.stars(p));
            if (Bounties.hunting(p)) Bounties.cancel(p);
            p.getPersistentData().remove("isg_bounty_next");
            Boards.open(p, Boards.BOUNTY, at);
            Boards.pick(p, Boards.BOUNTY, 0);
            if (Bounties.hunting(p)) pass("boards: take a bounty", Bounties.huntingName(p)); else fail("boards: take a bounty", "no hunt after taking a note (maybe the board is waiting: next at " + Bounties.nextAt(p) + ")");
            Boards.pick(p, Boards.BOUNTY, -1);
            if (!Bounties.hunting(p)) pass("boards: give up a bounty", ""); else fail("boards: give up a bounty", "still hunting");
            Boards.open(p, Boards.QUEST, at);
            Boards.pick(p, Boards.QUEST, 0);
            if (Quests.questing(p)) pass("boards: take a quest", Quests.questTitle(p)); else fail("boards: take a quest", "no quest after taking a note");
            Boards.pick(p, Boards.QUEST, -1);
            if (!Quests.questing(p)) pass("boards: give up a quest", ""); else fail("boards: give up a quest", "still on it");
        });
    }

    private static Test exam() {
        return new Test("rift: Magic Knight Exam") {
            @Override void start() { clearArea(); resetPlayer(false); Rifts.startExam(p); }
            @Override boolean tick(int t) {
                if (t == 60) {
                    boolean in = p.level().dimension().equals(Rifts.RIFT);
                    Rifts.Rift r = Rifts.riftOf(p);
                    if (in && r != null) pass(name, "inside, " + r.rooms.size() + " rooms"); else fail(name, "not inside the rift after 3 s (" + p.level().dimension().location() + ")");
                    Rifts.leave(p);
                }
                if (t < 100) return false;
                if (p.level().dimension().equals(Level.OVERWORLD)) pass("rift: leaving", "back in the overworld"); else fail("rift: leaving", "still in " + p.level().dimension().location());
                resetPlayer(false);
                return true;
            }
        };
    }

    private static Test raid() {
        return new Test("elf raid") {
            int waves; boolean elvesSeen;
            @Override void start() { clearArea(); resetPlayer(false); ElfRaids.start(p, home, true); }
            @Override boolean tick(int t) {
                p.setHealth(p.getMaxHealth());
                if (t % 20 == 0) {   // the defenders win every fight at once
                    boolean any = false;
                    for (Entity e : sl().getEntities((Entity) null, new AABB(home, home).inflate(60), e -> e instanceof Mob)) {
                        boolean elf = e.getCustomName() != null && e.getCustomName().getString().contains("Elf");
                        if (elf || e.getPersistentData().getBoolean("isg_raid_leader")) { any = true; e.kill(); }
                    }
                    if (any && !elvesSeen) waves++;
                    elvesSeen = any;
                }
                if (t % 40 == 0) {
                    BlockPos c = BlockPos.containing(home);
                    for (BlockPos q : BlockPos.betweenClosed(c.offset(-12, -3, -12), c.offset(12, 4, 12))) {
                        BlockEntity be = sl().getBlockEntity(q);
                        if (be instanceof ChestBlockEntity ch && ch.getCustomName() != null && ch.getCustomName().getString().contains("Spoils")) {
                            pass(name, waves + " wave(s) beaten, spoils chest at " + q.getX() + "," + q.getY() + "," + q.getZ());
                            sl().removeBlock(q, false);
                            return true;
                        }
                    }
                }
                if (t > 20 * 150) { fail(name, "no spoils chest after 150 s (" + waves + " wave(s) seen)"); return true; }
                return false;
            }
        };
    }

    private static Test saving() {
        return check("saving and loading", () -> {
            CompoundTag a = p.saveWithoutId(new CompoundTag());
            CompoundTag b = roundTrip(a);
            if (a.equals(b)) pass("save: player data", a.getAllKeys().size() + " keys"); else fail("save: player data", "changed by writing and reading it back");
            CompoundTag r1 = Rifts.saveForTest(p.server);
            CompoundTag r2 = Rifts.saveForTest(Rifts.loadForTest(roundTrip(r1)));
            if (r1.equals(r2)) pass("save: rifts", r1.getList("Rifts", 10).size() + " rift(s)"); else fail("save: rifts", "the rifts aren't the same after saving and loading");
        });
    }

    private static CompoundTag roundTrip(CompoundTag t) {
        try {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            NbtIo.write(t, new DataOutputStream(bo));
            return NbtIo.read(new DataInputStream(new ByteArrayInputStream(bo.toByteArray())));
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    // ------------------------------------------------------------------ running them
    public static void start(ServerPlayer player) {
        p = player;
        BlockPos g = sl().getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, BlockPos.containing(player.position()));
        home = new Vec3(g.getX() + 0.5, g.getY(), g.getZ() + 0.5);
        queue.clear(); results.clear(); index = 0; t = 0;
        UiShotsServer.prepare(p);
        queue.add(saving());
        queue.add(boards());
        for (var e : AddonSpellRegistry.SPELLS.getEntries()) queue.add(new SpellTest(e.get()));
        queue.add(new BossTest("Zenon", AddonEntities.ZENON));
        queue.add(new BossTest("Beast of Despair", AddonEntities.BEAST_OF_DESPAIR));
        queue.add(new BossTest("Rhya", AddonEntities.RHYA));
        queue.add(new BossTest("Licht", AddonEntities.LICHT));
        queue.add(new BossTest("Revenant", AddonEntities.REVENANT));
        queue.add(new BossTest("Curse Witch", AddonEntities.CURSE_WITCH));
        queue.add(new BossTest("Cinder Sovereign", AddonEntities.CINDER_SOVEREIGN));
        queue.add(new BossTest("Memory Guardian", AddonEntities.MEMORY_GUARDIAN));
        queue.add(exam());
        queue.add(raid());
        running = true;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent e) {
        if (!running || e.phase != TickEvent.Phase.END) return;
        if (p == null || p.isRemoved() && !p.hasDisconnected()) { /* respawned: find the new player */
            var l = e.getServer().getPlayerList().getPlayers();
            if (!l.isEmpty()) p = l.get(0);
        }
        if (index >= queue.size()) { running = false; done = true; if (dummy != null) dummy.discard(); return; }
        Test cur = queue.get(index);
        try {
            if (t == 0) cur.start();
            t++;
            if (cur.tick(t)) { index++; t = 0; }
        } catch (Throwable x) {
            fail(cur.name, "the test itself threw: " + x);
            index++; t = 0;
        }
    }

    /** How far through: "n / total". */
    public static String progress() { return index + " / " + queue.size(); }
}
