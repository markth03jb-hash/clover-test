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
    private static net.minecraft.world.entity.monster.Vindicator dummy;
    private static int playerHits;
    private static final UUID HP = UUID.fromString("a1f70000-0000-4000-8000-00000000e57a");

    private abstract static class Test {
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

    private static void pass(String test, String what) { results.add("PASS | " + test + " | " + what); }
    private static void fail(String test, String what) { results.add("FAIL | " + test + " | " + what); }
    private static void warn(String test, String what) { results.add("WARN | " + test + " | " + what); }
    /** A line of detail from inside a spell while the tests run (to explain a failure). */
    static void note(String what) { if (p != null) results.add("NOTE | " + what); }
    static boolean running() { return p != null; }
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
    private static void clearArea() {
        for (Entity e : sl().getEntities((Entity) null, new AABB(home, home).inflate(80), e -> !(e instanceof Player) && e != dummy)) e.discard();
    }

    private static ItemStack testBook = ItemStack.EMPTY;
    private static void cast(AbstractSpell s) throws Exception {
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
    private static boolean castInfoShown;
    private static String lastCastResult = "";

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
        BlockState[] before; Set<MobEffect> pe, de; int fx0, msg0; float mana0, hp0; String castErr, bookId = "?";
        SpellTest(AbstractSpell s) { super("spell " + s.getSpellId()); spell = s; }
        @Override Class<?>[] roots() { return new Class<?>[]{spell.getClass()}; }
        boolean skip;
        @Override void start() {
            // a clean slate: the last spell's staged effects dropped and a fresh dummy (nothing still bound to the old
            // one - a chain, a burn, a mark), so only this spell's damage is counted
            cancelCast(); AstaFx.testDropScheduled(); clearArea(); resetPlayer(true);
            if (dummy != null) { dummy.discard(); dummy = null; }
            ensureDummy();
            // equip the grimoire this spell belongs to, fully mastered (else its lock or another grimoire stops it)
            String path = spell.getSpellResource().getPath();
            GrimoireMastery.Book owner = null;
            for (GrimoireMastery.Book b : GrimoireMastery.BOOKS) if (b.unlocks().containsKey(path)) { owner = b; break; }
            if (owner == null) { skip = true; info(name, "not in any grimoire (a form's or a union's version) - cast through its form, not tested alone"); return; }
            ItemStack book = new ItemStack(owner.item().get());
            bookId = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(book.getItem()).getPath();
            CompoundTag mt = new CompoundTag(); mt.putInt("xp", 10_000_000);
            book.getOrCreateTag().put(GrimoireMastery.masteryTag(), mt);
            book.getOrCreateTag().putBoolean("Ascended", true);
            final ItemStack bk = book;
            top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(p).ifPresent(h -> h.setEquippedCurio("spellbook", 0, bk));
            p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            testBook = bk;
            before = snapshot(); pe = effects(p); de = effects(dummy);
            mana0 = MagicData.getPlayerMagicData(p).getMana(); hp0 = dummy == null ? 0 : dummy.getHealth();
            fx0 = AddonNetwork.fxSent; msg0 = FxBatch.messages;
            try { cast(spell); } catch (Throwable x) { castErr = (x.getCause() != null ? x.getCause() : x).toString(); }
            MagicData d = MagicData.getPlayerMagicData(p);
            castState = "cast returned " + lastCastResult + ", casting=" + d.isCasting() + " " + d.getCastingSpellId();
            if (!"true".equals(lastCastResult)) {
                try {
                    var cr = spell.canBeCastedBy(1, io.redspace.ironsspellbooks.api.spells.CastSource.SPELLBOOK, d, p);
                    castState += "; refused: " + cr.type + " " + (cr.message == null ? "" : cr.message.getString()) + " school=" + spell.getSchoolType().getId();
                } catch (Throwable x) { castState += "; " + x; }
            }
        }
        String castState = "";
        int lastAct, lastFx; float lastHp;
        @Override boolean tick(int t) {
            if (skip) return true;
            p.setHealth(p.getMaxHealth());
            // run at least 3 s, and longer (up to 5 s) while the spell is still drawing or hurting, so a long spell's
            // finisher counts in its damage; the effect count is judged per 3 s either way
            int fxNow = AddonNetwork.fxSent; float hpNow = dummy == null ? 0 : dummy.getHealth();
            if (fxNow != lastFx || hpNow != lastHp) { lastAct = t; lastFx = fxNow; lastHp = hpNow; }
            if (t < 60 || (t < 100 && t - lastAct < 12)) return false;
            cancelCast();
            int fx = (AddonNetwork.fxSent - fx0) * 60 / t;
            float spent = mana0 - MagicData.getPlayerMagicData(p).getMana(), dealt = dummy == null ? 0 : hp0 - dummy.getHealth();
            boolean ok = true;
            if (castErr != null) { fail(name, "threw while casting: " + castErr); ok = false; }
            String ch = changes(before);
            if (!ch.isEmpty()) { fail(name, "changed the world: " + ch); ok = false; }
            Set<MobEffect> np = effects(p), nd = effects(dummy);
            np.removeAll(pe); nd.removeAll(de);
            if (!np.isEmpty() || !nd.isEmpty()) { fail(name, "gave status effects: " + np + " " + nd); ok = false; }
            if (fx > 360) warn(name, "heavy: " + fx + " effect packets in 3 s (" + fx / 3 + "/s)");
            if (fx <= 1 && spent <= 0 && dealt <= 0) warn(name, "did nothing visible (no effects, no mana, no damage; " + castState + ") - it may need a target, a form or a condition");
            if (ok) pass(name, String.format("%d fx packets, %.0f mana, %.1f damage, %d messages, cd %d, book %s", fx, spent, dealt, FxBatch.messages - msg0, spell.getSpellCooldown(), bookId) + ", " + t + " ticks");
            return true;
        }
    }

    /** The Blood Grimoire's mode: fill the gauge, transform, then every spell must come out as its ice move and hurt the dummy. */
    private static final class IceTest extends Test {
        IceTest() { super("blood: Scarlet Moon ice moveset"); }
        @Override Class<?>[] roots() { return new Class<?>[]{BloodFx.class, IceFx.class}; }
        final String[] names = {"crimson_edge", "hemorrhage", "sanguine_volley", "transfusion", "blood_pact", "red_tide", "thorned_veins", "crimson_bulwark", "exsanguinate", "moonfall", "ice_throne"};
        BlockState[] before; Set<MobEffect> pe, de; final java.util.List<String> bad = new java.util.ArrayList<>(), done = new java.util.ArrayList<>();
        float hp; String err;
        @Override void start() {
            cancelCast(); clearArea(); resetPlayer(true); ensureDummy();
            before = snapshot(); pe = effects(p); de = effects(dummy);
            Moves.ends(p, BloodFx.MODE);   // the grimoire's own spell test may have left the Moon up
            BloodFx.setGauge(p, 0);
            BloodFx.scarletMoon(p, 10f);   // not full: must refuse
            if (Moves.inMode(p, BloodFx.MODE)) bad.add("transformed with an empty gauge");
            BloodFx.setGauge(p, BloodFx.MAX);
            try { BloodFx.scarletMoon(p, 10f); } catch (Throwable x) { err = x.toString(); }
        }
        @Override boolean tick(int t) {
            p.setHealth(p.getMaxHealth());
            MagicData.getPlayerMagicData(p).setMana(1000);
            if (t == 40 && !Moves.inMode(p, BloodFx.MODE)) bad.add("the mode didn't start with a full gauge");
            int i = (t - 45) / 45;
            if (t >= 45 && (t - 45) % 45 == 0 && i < names.length) {
                ensureDummy();
                p.teleportTo(sl(), home.x, home.y, home.z, 0f, 0f);
                hp = dummy.getHealth();
                float h0 = p.getHealth();
                try {
                    switch (i) {
                        case 0 -> BloodFx.crimsonEdge(p, 10f);
                        case 1 -> BloodFx.hemorrhage(p, 10f);
                        case 2 -> BloodFx.sanguineVolley(p, 10f);
                        case 3 -> BloodFx.transfusion(p, 10f);
                        case 4 -> BloodFx.bloodPact(p, 10f);
                        case 5 -> BloodFx.redTide(p, 10f);
                        case 6 -> BloodFx.thornedVeins(p, 10f);
                        case 7 -> BloodFx.crimsonBulwark(p, 10f);
                        case 8 -> BloodFx.exsanguinate(p, 10f);
                        case 9 -> { p.setXRot(15); BloodFx.scarletMoon(p, 10f); }
                        default -> IceFx.iceThrone(p, 10f);
                    }
                } catch (Throwable x) { bad.add(names[i] + " threw " + x); }
                if (p.getHealth() < h0) bad.add(names[i] + " cost health under the Moon");
            }
            if (t >= 45 && (t - 45) % 45 == 44 && i < names.length) {
                float dealt = hp - (dummy == null ? hp : dummy.getHealth());
                if (i != 4 && i != 7 && dealt <= 0) bad.add(names[i] + " did no damage to the dummy");
                done.add(names[i] + String.format(" %.0f", dealt));
            }
            if (t < 45 + names.length * 45 + 5) return false;
            if (err != null) bad.add("Scarlet Moon threw " + err);
            String ch = changes(before);
            if (!ch.isEmpty()) bad.add("changed the world: " + ch);
            Set<MobEffect> np = effects(p), nd = effects(dummy);
            np.removeAll(pe); nd.removeAll(de);
            if (!np.isEmpty() || !nd.isEmpty()) bad.add("gave status effects: " + np + " " + nd);
            if (bad.isEmpty()) pass(name, "damage dealt: " + String.join(", ", done));
            else fail(name, String.join("; ", bad));
            Moves.ends(p, BloodFx.MODE);
            return true;
        }
    }

    /** The Gravity and Sound Grimoires: every spell cast straight at the dummy; the attacks must hurt it, none may throw,
     *  change the world or give status effects. Close-range spells get the dummy brought in to 2.5 blocks. */
    private static final class BookMovesTest extends Test {
        final String book; final java.util.function.Supplier<net.minecraft.world.item.Item> item;
        final String[] names; final java.util.List<java.util.function.BiConsumer<ServerPlayer, Float>> moves; final java.util.Set<String> quiet, close;
        BookMovesTest(String book, java.util.function.Supplier<net.minecraft.world.item.Item> item, Class<?> root, Object... spec) {
            super(book + ": every move");
            this.book = book; this.item = item; this.root = root;
            java.util.List<String> n = new java.util.ArrayList<>(); moves = new java.util.ArrayList<>(); quiet = new java.util.HashSet<>(); close = new java.util.HashSet<>();
            for (int i = 0; i < spec.length; i += 3) {
                String nm = (String) spec[i];
                n.add(nm);
                @SuppressWarnings("unchecked") java.util.function.BiConsumer<ServerPlayer, Float> f = (java.util.function.BiConsumer<ServerPlayer, Float>) spec[i + 1];
                moves.add(f);
                String flag = (String) spec[i + 2];
                if (flag.contains("q")) quiet.add(nm);
                if (flag.contains("c")) close.add(nm);
            }
            names = n.toArray(new String[0]);
        }
        final Class<?> root;
        @Override Class<?>[] roots() { return new Class<?>[]{root}; }
        BlockState[] before; Set<MobEffect> pe, de; final java.util.List<String> bad = new java.util.ArrayList<>(), done = new java.util.ArrayList<>();
        float hp; String castNote = "";
        @Override void start() {
            cancelCast(); clearArea(); resetPlayer(true); ensureDummy();
            ItemStack bk = new ItemStack(item.get());
            CompoundTag mt = new CompoundTag(); mt.putInt("xp", 10_000_000);
            bk.getOrCreateTag().put(GrimoireMastery.masteryTag(), mt);
            top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(p).ifPresent(h -> h.setEquippedCurio("spellbook", 0, bk));
            before = snapshot(); pe = effects(p); de = effects(dummy);
        }
        @Override boolean tick(int t) {
            p.setHealth(p.getMaxHealth());
            MagicData.getPlayerMagicData(p).setMana(1000);
            int i = (t - 1) / 50;
            if ((t - 1) % 50 == 0 && i < names.length) {
                ensureDummy();
                p.teleportTo(sl(), home.x, home.y, home.z, 0f, 0f);
                p.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                if (close.contains(names[i])) dummy.teleportTo(home.x, home.y, home.z + 2.5);
                hp = dummy.getHealth();
                net.minecraft.world.entity.LivingEntity seen = FxUtil.aimed(p, 28, 12);
                castNote = String.format("at cast: aimed %s, sees dummy %s, %d near, look %.2f %.2f %.2f", seen == null ? "nothing" : seen == dummy ? "the dummy" : seen.getName().getString(),
                        dummy != null && p.hasLineOfSight(dummy), sl().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, p.getBoundingBox().inflate(28), x -> x != p).size(), p.getLookAngle().x, p.getLookAngle().y, p.getLookAngle().z);
                try { moves.get(i).accept(p, 10f); } catch (Throwable x) { bad.add(names[i] + " threw " + x); }
            }
            if ((t - 1) % 50 == 49 && i < names.length) {
                float dealt = hp - (dummy == null ? hp : dummy.getHealth());
                if (!quiet.contains(names[i]) && dealt <= 0) bad.add(names[i] + " did no damage to the dummy [" + castNote + "]" + (dummy == null ? " (no dummy)" : String.format(" (dummy at %.1f %.1f %.1f, alive %s, hp %.0f, invuln %d; player yaw %.0f pitch %.0f at %.1f %.1f %.1f)", dummy.getX() - home.x, dummy.getY() - home.y, dummy.getZ() - home.z, dummy.isAlive(), dummy.getHealth(), dummy.invulnerableTime, p.getYRot(), p.getXRot(), p.getX() - home.x, p.getY() - home.y, p.getZ() - home.z)));
                done.add(names[i] + String.format(" %.0f", dealt));
            }
            if (t < names.length * 50 + 10) return false;
            String ch = changes(before);
            if (!ch.isEmpty()) bad.add("changed the world: " + ch);
            Set<MobEffect> np = effects(p), nd = effects(dummy);
            np.removeAll(pe); nd.removeAll(de);
            if (!np.isEmpty() || !nd.isEmpty()) bad.add("gave status effects: " + np + " " + nd);
            if (bad.isEmpty()) pass(name, "damage dealt: " + String.join(", ", done));
            else fail(name, String.join("; ", bad));
            Moves.ends(p, GravityFx.MODE); Moves.ends(p, SoundFx.MODE); Moves.ends(p, SoundFx.BARRIER); Moves.ends(p, InkFx.MODE); Moves.ends(p, ErrorFx.MODE); Moves.ends(p, TreasureFx.MODE); Moves.ends(p, TreasureFx.AEGIS); Moves.ends(p, ChainFx.MODE); Moves.ends(p, HunterFx.MODE); Moves.ends(p, VoidFx.MODE); Moves.ends(p, VoidFx.ABSENCE); Moves.ends(p, GamblerFx.MODE); Moves.ends(p, GamblerFx.INSURED); Moves.ends(p, TarotFx.MODE); Moves.ends(p, TarotFx.READ); Moves.ends(p, AbyssFx.MODE); Moves.ends(p, MechFx.MODE); Moves.ends(p, MechFx.PLATING); Moves.ends(p, WeatherFx.MODE); Moves.ends(p, WeatherFx.FOG_BANK); Moves.ends(p, OverdriveFx.MODE); Moves.ends(p, DreamFx.MODE); Moves.ends(p, ShadowFx.MODE); ShadowArmy.recallAll(p);
            return true;
        }
    }

    /** The Blood Grimoire's paths: every spell cast both ways (a mastered book) - nothing may throw, change the world or give effects. */
    /** Hunter's Ascension (the Shadow Grimoire): levels and points, stats as attribute bonuses, the Daily Trial, the
     *  Market, and a Reckoning that spawns its waves, cleans them up and breaks nothing. */
    /** The Hunger Blade: growing swords (in order, for Growth), the four carried and switching, no changing them in a
     *  fight, the blade kept to the main hand, kills feeding it, the moods' modifiers, the starved bite, the throw coming back. */
    private static final class HungerTest extends Test {
        HungerTest() { super("hunger: the blade"); }
        @Override Class<?>[] roots() { return new Class<?>[]{HungerFx.class}; }
        final java.util.List<String> bad = new java.util.ArrayList<>();
        BlockState[] before;
        net.minecraft.world.entity.monster.Zombie prey;
        @Override void start() {
            cancelCast(); clearArea(); resetPlayer(true); ensureDummy();
            ItemStack book = new ItemStack(com.ironspellgrimoire.registry.AddonItemRegistry.HUNGER_GRIMOIRE.get());
            top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(p).ifPresent(h -> h.setEquippedCurio("spellbook", 0, book));
            HungerFx.testReset(p);
            p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            before = snapshot();
        }
        @Override boolean tick(int t) {
            try {
                if (t == 2) {   // drawing it, and its stats
                    HungerFx.draw(p, 10f);
                    if (!HungerFx.drawn(p)) bad.add("Draw didn't put the blade in the hand");
                    if ((HungerFx.testTaught(p) & 1) == 0) bad.add("the first Draw taught nothing");
                    else if (com.ironspellgrimoire.item.HungerBladeItem.form(p.getMainHandItem()) != 0) bad.add("the first blade isn't the Fang");
                    // its damage (the player's attribute only catches up next tick, so the item's own modifier)
                    double add = p.getMainHandItem().getAttributeModifiers(net.minecraft.world.entity.EquipmentSlot.MAINHAND)
                            .get(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE).stream().mapToDouble(m -> m.getAmount()).sum();
                    if (Math.abs(add + 1 - HungerFx.DMG[0]) > 0.01) bad.add("the Fang's damage is " + (add + 1));
                    HungerFx.draw(p, 10f);
                    if (HungerFx.drawn(p)) bad.add("Draw again didn't put it away");
                    p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(net.minecraft.world.item.Items.STICK));
                    HungerFx.draw(p, 10f);
                    if (!p.getMainHandItem().is(net.minecraft.world.item.Items.STICK)) bad.add("Draw replaced what the hand held");
                    p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                }
                if (t == 4) {   // growing: in order, and for Growth
                    HungerFx.handle(p, 1, 1);
                    if (HungerFx.unlocked(p, 1)) bad.add("Gnaw grew with no Growth");
                    HungerFx.testGrowth(p, 1000);
                    HungerFx.handle(p, 1, 3);
                    if (HungerFx.unlocked(p, 3)) bad.add("the Marrow Cleaver grew before the Maw Blade");
                    HungerFx.handle(p, 1, 1); HungerFx.handle(p, 1, 2); HungerFx.handle(p, 1, 3); HungerFx.handle(p, 1, 4);
                    for (int f : new int[]{1, 2, 3, 4}) if (!HungerFx.unlocked(p, f)) bad.add(HungerFx.NAMES[f] + " didn't grow");
                    int spent = 1000 - HungerFx.growth(p), want = HungerFx.COST[2] + HungerFx.COST[3] + HungerFx.COST[4] + HungerFx.COST[5];
                    if (spent != want) bad.add("growing four swords cost " + spent + ", not " + want);
                    HungerFx.handle(p, 1, 9);
                    if (HungerFx.unlocked(p, 9)) bad.add("the Prism Rapier grew without the Shard Blade");
                    // a hybrid: only with an ascended sword of both its paths
                    HungerFx.handle(p, 1, 23);
                    if (HungerFx.unlocked(p, 23)) bad.add("the Fossil Prism grew without an ascended Crystal sword");
                    if (HungerFx.canGrow(1 | (1 << 5), 23)) bad.add("canGrow lets a hybrid grow from one path");
                    if (!HungerFx.canGrow((1 << 5) | (1 << 12), 23)) bad.add("canGrow refuses the Fossil Prism with both ascended swords");
                }
                if (t == 6) {   // four carried; a fifth replaces; switching changes the held sword
                    HungerFx.testCalm(p);
                    HungerFx.handle(p, 1, 5);   // the Ossuary Colossus: the slots are full, it isn't carried yet
                    HungerFx.handle(p, 3, 1 * 100 + 5);
                    HungerFx.testHold(p, 0);
                    HungerFx.handle(p, 2, 1);
                    if (com.ironspellgrimoire.item.HungerBladeItem.form(p.getMainHandItem()) != 5) bad.add("switching to slot 2 didn't give the Ossuary Colossus (got " + com.ironspellgrimoire.item.HungerBladeItem.form(p.getMainHandItem()) + ")");
                    if (HungerFx.current(p) != 5) bad.add("the current sword isn't the Colossus");
                    HungerFx.handle(p, 3, 2 * 100 + 5);   // already carried: swaps
                    int carried = 0;
                    for (int s = 0; s < 4; s++) if (HungerFx.testLoadAt(p, s) == 5) carried++;
                    if (carried != 1) bad.add("one sword carried in " + carried + " slots");
                    HungerFx.handle(p, 3, 0 * 100 + 22);
                    if (HungerFx.testLoadAt(p, 0) == 22) bad.add("a sword not grown went into a slot");
                    // never in a fight
                    p.hurt(p.damageSources().generic(), 1f);
                    int was = HungerFx.testLoadAt(p, 3);
                    HungerFx.handle(p, 3, 3 * 100 + 1);
                    if (HungerFx.testLoadAt(p, 3) != was) bad.add("the swords changed in a fight");
                    p.setHealth(p.getMaxHealth());
                }
                if (t == 8) {   // the blade lives only in the main hand
                    var inv = p.getInventory();
                    inv.setItem(5, com.ironspellgrimoire.item.HungerBladeItem.of(com.ironspellgrimoire.registry.AddonItemRegistry.HUNGER_BLADE.get(), 3));
                }
                if (t == 10) {
                    if (HungerFx.isBlade(p.getInventory().getItem(5)) && p.getInventory().selected != 5) bad.add("a blade stayed in another slot");
                    // a kill with the blade feeds it
                    HungerFx.testGrowth(p, 0); HungerFx.testHunger(p, 20f);
                    p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    HungerFx.testHold(p, 2);
                    p.teleportTo(sl(), home.x, home.y, home.z, 0f, 0f);
                    prey = net.minecraft.world.entity.EntityType.ZOMBIE.create(sl());
                    if (prey != null) {
                        prey.moveTo(home.x, home.y, home.z + 1.5, 180, 0); prey.setNoAi(true);
                        sl().addFreshEntity(prey);
                        prey.setHealth(1);
                        prey.hurt(p.damageSources().playerAttack(p), 50f);
                    }
                }
                if (t == 12) {
                    if (prey != null && prey.isAlive()) { bad.add("the zombie survived"); prey.discard(); }
                    if (HungerFx.growth(p) <= 0) bad.add("a kill gave no Growth");
                    if (HungerFx.hunger(p) <= 20f) bad.add("a kill didn't feed the blade");
                    if (HungerFx.bondXp(p) <= 0) bad.add("a kill built no Bond");
                    // devour: a sneak-strike on a foe near death eats it whole, for double Growth
                    int g0 = HungerFx.growth(p);
                    prey = net.minecraft.world.entity.EntityType.ZOMBIE.create(sl());
                    if (prey != null) {
                        prey.moveTo(home.x, home.y, home.z + 1.5, 180, 0); prey.setNoAi(true);
                        sl().addFreshEntity(prey);
                        prey.setHealth(2.5f);
                        p.setShiftKeyDown(true);
                        prey.hurt(p.damageSources().playerAttack(p), 1f);
                        p.setShiftKeyDown(false);
                        if (prey.isAlive()) { bad.add("devour didn't eat a zombie at 2.5 health"); prey.discard(); }
                        else if (HungerFx.growth(p) - g0 < 2 * Math.max(1, Math.round(prey.getMaxHealth() / 8f))) bad.add("devour gave " + (HungerFx.growth(p) - g0) + " Growth, not double");
                    }
                    // a parry in time takes most of a blow
                    p.setHealth(p.getMaxHealth());
                    HungerFx.testParry(p);
                    float before = p.getHealth();
                    p.invulnerableTime = 0;
                    p.hurt(p.damageSources().mobAttack(dummy), 10f);
                    float took = before - p.getHealth();
                    if (took > 3f) bad.add(String.format("a parry let %.1f of 10 through", took));
                    p.setHealth(p.getMaxHealth());
                    // moods: well fed - faster swings; starved - harder hits and a bite that never takes the last of you
                    HungerFx.testHunger(p, 90f);
                }
                if (t == 36) {   // (over a full second after: the mood is applied once a second)
                    var sp = p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED);
                    if (sp == null || sp.getModifier(HungerFx.FED_SPD) == null) bad.add("well fed gave no faster swings");
                    HungerFx.testHunger(p, 2f);
                    p.setHealth(3.5f);
                }
                if (t == 24) {
                    HungerFx.testBond(p, 140);
                    p.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, ItemStack.EMPTY);
                }
                if (t == 50) {
                    if (!HungerFx.isBlade(p.getOffhandItem())) bad.add("no twin in the off hand at Bond 4");
                    HungerFx.testBond(p, 0);
                }
                if (t == 74) {
                    if (HungerFx.isBlade(p.getOffhandItem())) bad.add("the twin stayed below Bond 4");
                    // sheathed: put away, it hangs at the hip
                    p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                }
                if (t == 78) {
                    if (HungerFx.testLook(p) < 0) bad.add("nothing sheathed at the hip with the blade put away");
                    HungerFx.testHold(p, 2);
                    // sulking: left starved a minute, it won't come at once
                    p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    HungerFx.testStarvedFor(p, 70);
                    HungerFx.draw(p, 10f);
                    if (HungerFx.drawn(p)) bad.add("a sulking blade came at once");
                }
                if (t == 144) {
                    if (!HungerFx.drawn(p)) bad.add("the sulking blade never came");
                    HungerFx.testStarvedFor(p, 0);
                    p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    HungerFx.testHold(p, 2);
                    p.tickCount = (p.tickCount / 20) * 20 + 19;   // the once-a-second mood check comes next tick, not maybe after the look at t 162
                }
                if (t == 162) {
                    var dm = p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
                    if (dm == null || dm.getModifier(HungerFx.HUNGRY_DMG) == null) bad.add("starved gave no harder hits");
                    if (p.getHealth() < 3.4f) bad.add("the starved bite took the player below 4 health");
                    p.setHealth(p.getMaxHealth());
                    HungerFx.testHunger(p, 60f);
                    // the throw: away and back into the hand
                    HungerFx.testBond(p, 140);
                    p.teleportTo(sl(), home.x, home.y, home.z, 0f, 0f);
                    p.setShiftKeyDown(true);
                    HungerFx.gnash(p, 10f);
                    p.setShiftKeyDown(false);
                    if (HungerFx.drawn(p)) bad.add("the throw left the blade in the hand");
                }
                if (t == 204) {   // 42 ticks after the throw: it's long back
                    if (!HungerFx.drawn(p)) bad.add("the thrown blade didn't come back to the hand (main hand: " + p.getMainHandItem() + ")");
                    if (!sl().getEntitiesOfClass(com.ironspellgrimoire.entity.ThrownHungerBlade.class, p.getBoundingBox().inflate(40)).isEmpty()) bad.add("a thrown blade is still out");
                }
                if (t == 206) {   // Bond 10 and the titles
                    HungerFx.testBond(p, 1000);
                    if (HungerFx.bond(p) != 10) bad.add("1000 bond XP gave Bond " + HungerFx.bond(p));
                    HungerFx.testDevours(p, 50);
                    int tt = HungerFx.testTitles(p);
                    if ((tt & 1) == 0) bad.add("50 devours didn't earn Glutton");
                    if ((tt & 8) == 0) bad.add("Bond 10 didn't earn Bloodbound");
                    HungerFx.handle(p, 4, 0);
                    if (HungerFx.testTitle(p) != 0) bad.add("couldn't wear Glutton");
                    HungerFx.handle(p, 4, 99);
                    if (HungerFx.testTitle(p) != -1) bad.add("couldn't take a title off");
                }
                if (t == 210) {
                    if (!HungerFx.drawn(p)) bad.add("Bond 10 or the titles took the blade out of the hand (main hand: " + p.getMainHandItem() + ")");
                    // the book taken off: the blade goes with it
                    top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(p).ifPresent(h -> h.setEquippedCurio("spellbook", 0, ItemStack.EMPTY));
                }
                if (t == 212) {
                    if (HungerFx.drawn(p)) bad.add("the blade stayed after the book was taken off");
                    String ch = changes(before);
                    if (!ch.isEmpty()) bad.add("changed the world: " + ch);
                    if (bad.isEmpty()) pass(name, "drew, grew, carried, switched, fed, moods, threw and caught");
                    else fail(name, String.join("; ", bad));
                    HungerFx.testReset(p);
                    return true;
                }
            } catch (Throwable x) { fail(name, "threw " + x); return true; }
            return false;
        }
    }

    /** The Architect of Ruin: building, neighbours, levels and blueprints, passives, the garrison, Siege Mode's walls
     *  (solid, breakable by foes but not by you, giving way if you're pushed in), the Keepsake and the Ward Sigil,
     *  and that everything is cleared and nothing touches a block. */
    private static final class ArchitectTest extends Test {
        ArchitectTest() { super("architect: the fortress"); }
        @Override Class<?>[] roots() { return new Class<?>[]{FortressFx.class, FortressSiege.class, FortressSpells.class, com.ironspellgrimoire.entity.FortressWall.class,
                com.ironspellgrimoire.entity.FortressTower.class, com.ironspellgrimoire.entity.FortressDefender.class}; }
        final java.util.List<String> bad = new java.util.ArrayList<>();
        BlockState[] before;
        com.ironspellgrimoire.entity.FortressWall probe;
        @Override void start() {
            cancelCast(); clearArea(); resetPlayer(true); ensureDummy();
            ItemStack book = new ItemStack(com.ironspellgrimoire.registry.AddonItemRegistry.ARCHITECT_GRIMOIRE.get());
            CompoundTag mt = new CompoundTag(); mt.putInt("xp", 10_000_000);
            book.getOrCreateTag().put(GrimoireMastery.masteryTag(), mt);
            top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(p).ifPresent(h -> h.setEquippedCurio("spellbook", 0, book));
            FortressFx.testReset(p);
            FortressSpells.testClear();
            before = snapshot();
        }
        @Override boolean tick(int t) {
            try {
                MagicData.getPlayerMagicData(p).setMana(1000);
                p.setHealth(p.getMaxHealth());
                if (t == 2) {   // fresh, building, Stone, locked rooms, the plan's edge
                    if (FortressFx.level(p) != 1 || FortressFx.gridSize(p) != 3 || FortressFx.stone(p) != 0) bad.add("a fresh fortress isn't level 1, 3x3, no Stone");
                    FortressFx.handle(p, 1, FortressFx.BARRACKS * 100 + 0);
                    if (FortressFx.built(p, FortressFx.BARRACKS)) bad.add("built the Barracks with no Stone");
                    FortressFx.testStone(p, 2000);
                    FortressFx.handle(p, 1, FortressFx.BARRACKS * 100 + 0);
                    if (FortressFx.roomLevel(p, FortressFx.BARRACKS) != 1) bad.add("couldn't build the Barracks with Stone");
                    if (FortressFx.stone(p) != 2000 - FortressFx.COST[0]) bad.add("building cost " + (2000 - FortressFx.stone(p)) + " Stone, not " + FortressFx.COST[0]);
                    if (FortressFx.testSlots(p)[0] != FortressFx.BARRACKS) bad.add("the first room didn't fill the first Room page");
                    FortressFx.handle(p, 1, FortressFx.FORGE * 100 + 1);
                    if (FortressFx.built(p, FortressFx.FORGE)) bad.add("built the Forge without its blueprint");
                    FortressFx.handle(p, 1, FortressFx.ARMOURY * 100 + 3);
                    if (FortressFx.built(p, FortressFx.ARMOURY)) bad.add("built outside the 3x3 plan");
                    FortressFx.testUnlockAll(p);
                    FortressFx.handle(p, 1, FortressFx.FORGE * 100 + 1);
                    FortressFx.handle(p, 1, FortressFx.ARMOURY * 100 + 2);
                    if (!FortressFx.paired(p, FortressFx.FORGE, FortressFx.ARMOURY)) bad.add("the Forge and Armoury side by side don't pair");
                    FortressFx.handle(p, 2, 2 * 100 + 6);   // the Armoury moves below the Barracks (cell 6 is row 1, col 1: below the Forge)
                    if (FortressFx.testCell(p, FortressFx.ARMOURY) != 6) bad.add("couldn't move the Armoury");
                    if (!FortressFx.paired(p, FortressFx.FORGE, FortressFx.ARMOURY)) bad.add("the Forge and Armoury one above the other don't pair");
                    FortressFx.handle(p, 2, 6 * 100 + 8);
                    if (!FortressFx.paired(p, FortressFx.FORGE, FortressFx.ARMOURY) || FortressFx.testCell(p, FortressFx.ARMOURY) != 8) {}   // cell 8 is row 1, col 3: off the plan, so it stays
                    if (FortressFx.testCell(p, FortressFx.ARMOURY) == 8) bad.add("moved a room off the plan");
                    int s0 = FortressFx.stone(p);
                    FortressFx.handle(p, 1, FortressFx.BARRACKS * 100 + 0);
                    if (FortressFx.roomLevel(p, FortressFx.BARRACKS) != 2 || s0 - FortressFx.stone(p) != FortressFx.COST[1]) bad.add("upgrading the Barracks didn't make it level 2 for " + FortressFx.COST[1]);
                    if (FortressSiege.cap(p) != 6) bad.add("Barracks 2 gives a garrison of " + FortressSiege.cap(p) + ", not 6");
                    for (int k = 0; k < 6; k++) FortressFx.handle(p, 1, FortressFx.BARRACKS * 100);
                    if (FortressFx.roomLevel(p, FortressFx.BARRACKS) != 5) bad.add("the Barracks went past level 5 or stopped short (" + FortressFx.roomLevel(p, FortressFx.BARRACKS) + ")");
                    FortressFx.handle(p, 3, 1 * 100 + FortressFx.FORGE + 1);
                    if (FortressFx.testSlots(p)[1] != FortressFx.FORGE) bad.add("couldn't put the Molten Bolt on Room page 2");
                    FortressFx.handle(p, 3, 2 * 100 + FortressFx.FORGE + 1);
                    int[] sl = FortressFx.testSlots(p);
                    if (sl[1] == FortressFx.FORGE && sl[2] == FortressFx.FORGE) bad.add("one spell sat on two Room pages");
                    FortressFx.handle(p, 3, 3 * 100 + FortressFx.CHAPEL + 1);
                    if (FortressFx.testSlots(p)[3] == FortressFx.CHAPEL) bad.add("a room not built went on a Room page");
                }
                if (t == 4) {   // levels: a blueprint each, the plan grows
                    FortressFx.testReset(p);
                    int bp0 = Integer.bitCount(FortressFx.testBlueprints(p));
                    FortressFx.addXp(p, FortressFx.need(1) + FortressFx.need(2) + FortressFx.need(3) + 1);
                    if (FortressFx.level(p) != 4) bad.add("XP for three levels gave level " + FortressFx.level(p));
                    if (Integer.bitCount(FortressFx.testBlueprints(p)) != bp0 + 3) bad.add("three levels gave " + (Integer.bitCount(FortressFx.testBlueprints(p)) - bp0) + " blueprints, not 3");
                    if (FortressFx.gridSize(p) != 4) bad.add("level 4's plan isn't 4x4");
                    FortressFx.testLevel(p, 8);
                    if (FortressFx.gridSize(p) != 5) bad.add("level 8's plan isn't 5x5");
                    FortressFx.testRoot(p).putIntArray("grid", new int[]{99, -7, 3, 0, 1000, 11, 11});   // a broken save
                    FortressFx.testRoot(p);
                    if (FortressFx.builtCount(p) != 0) bad.add("a broken saved plan wasn't cleared");
                }
                if (t == 6) {   // passives while worn, gone when the book is off
                    FortressFx.testReset(p); FortressFx.testUnlockAll(p); FortressFx.testStone(p, 1000);
                    FortressFx.handle(p, 1, FortressFx.ARMOURY * 100);
                    FortressFx.handle(p, 1, FortressFx.LIBRARY * 100 + 1);
                    var ad = p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
                    var mm = p.getAttribute(io.redspace.ironsspellbooks.api.registry.AttributeRegistry.MAX_MANA.get());
                    if (ad.getModifiers().stream().noneMatch(m -> m.getName().equals("Armoury"))) bad.add("the Armoury's passive isn't on");
                    if (mm.getModifiers().stream().noneMatch(m -> m.getName().equals("Library"))) bad.add("the Library's passive isn't on");
                    ItemStack held = io.redspace.ironsspellbooks.api.util.Utils.getPlayerSpellbookStack(p);
                    top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(p).ifPresent(h -> h.setEquippedCurio("spellbook", 0, ItemStack.EMPTY));
                    FortressFx.refreshPassives(p);
                    if (ad.getModifiers().stream().anyMatch(m -> m.getName().equals("Armoury"))) bad.add("the Armoury's passive stayed with the book off");
                    top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(p).ifPresent(h -> h.setEquippedCurio("spellbook", 0, held));
                    FortressFx.refreshPassives(p);
                }
                if (t == 8) {   // banners and the garrison's limit
                    FortressFx.testReset(p);
                    for (int k = 0; k < 6; k++) FortressSiege.testBanner(p, k % 2 == 0 ? "minecraft:zombie" : "minecraft:skeleton");
                    for (int k = 0; k < 6; k++) FortressFx.handle(p, 4, 0);
                    if (FortressSiege.testGarrison(p) != 4) bad.add("the garrison took " + FortressSiege.testGarrison(p) + " with room for 4");
                    if (FortressSiege.roleOf("minecraft:skeleton") != com.ironspellgrimoire.entity.FortressDefender.ARCHER) bad.add("a skeleton isn't an Archer");
                    if (FortressSiege.roleOf("minecraft:ravager") != com.ironspellgrimoire.entity.FortressDefender.BRUTE) bad.add("a ravager isn't a Brute");
                    FortressSiege.testKills(p, 1, 200);
                    if (FortressSiege.rankOf(200) != 3) bad.add("200 kills isn't Captain");
                }
                if (t == 10) {   // Siege Mode: walls, a gate, towers, the garrison out
                    FortressFx.testClearCds(p);
                    p.teleportTo(sl(), home.x, home.y, home.z, 0f, 0f);
                    FortressFx.siegePage(p, 10);
                    if (!FortressSiege.active(p)) bad.add("Siege Mode didn't start");
                    int w = FortressSiege.testWalls(p);
                    if (w < 30) bad.add("Siege Mode raised " + w + " wall blocks (a 6-wide ring has 41)");
                    if (FortressSiege.testTowers(p) < 2) bad.add("Siege Mode raised " + FortressSiege.testTowers(p) + " towers, not at least 2");
                    if (FortressSiege.testDefenders(p) != 4) bad.add(FortressSiege.testDefenders(p) + " defenders came out, not the garrison's 4");
                    FortressFx.siegePage(p, 10);
                    if (FortressSiege.testWalls(p) != w) bad.add("a second Siege Mode stacked another ring");
                    long gates = FortressSiege.testWallList(p).stream().filter(x -> x.kind() == com.ironspellgrimoire.entity.FortressWall.GATE).count();
                    if (gates != 1) bad.add(gates + " gatehouses, not 1");
                    for (var x : FortressSiege.testWallList(p)) {
                        if (x.kind() == com.ironspellgrimoire.entity.FortressWall.WALL && probe == null) probe = x;
                        if (x.shouldBeSaved()) { bad.add("a wall would be saved with the world"); break; }
                    }
                    if (probe == null) bad.add("no wall block to test");
                    else {
                        if (!probe.canBeCollidedWith()) bad.add("a wall isn't solid");
                        float h0 = probe.hp();
                        probe.hurt(p.damageSources().playerAttack(p), 10);
                        if (probe.hp() != h0) bad.add("the player knocked down their own wall");
                        probe.hurt(dummy.damageSources().mobAttack(dummy), 10);
                        if (probe.hp() >= h0) bad.add("a foe couldn't damage a wall");
                    }
                }
                if (t == 12 && probe != null && probe.isAlive()) p.teleportTo(sl(), probe.getX(), probe.getY(), probe.getZ(), 0f, 0f);   // pushed into a wall
                if (t == 20) {
                    if (probe != null && probe.isAlive()) bad.add("a wall didn't give way with its owner inside it");
                    p.teleportTo(sl(), home.x, home.y, home.z, 0f, 0f);
                    // the Keepsake and the Ward Sigil
                    FortressFx.testReset(p); FortressFx.testUnlockAll(p); FortressFx.testStone(p, 1000);
                    FortressFx.handle(p, 1, FortressFx.RELIQUARY * 100);
                    FortressFx.castRoom(p, FortressFx.RELIQUARY);
                    // (the harness zeroes every blow to the test player, so the two are asked directly)
                    float kept = FortressSpells.keepsake(p, 500);
                    if (Math.abs(kept - (p.getHealth() - 1)) > 0.01f) bad.add("the Keepsake let a killing blow of 500 through as " + kept);
                    if (FortressFx.testRoot(p).getLong("keep") != 0) bad.add("the Keepsake wasn't used up");
                    if (FortressSpells.keepsake(p, 500) != 500) bad.add("the Keepsake saved twice");
                    p.setHealth(p.getMaxHealth());
                    FortressFx.handle(p, 1, FortressFx.RUNE_VAULT * 100 + 1);
                    FortressFx.castRoom(p, FortressFx.RUNE_VAULT);
                    float took = FortressSpells.ward(p, 10);
                    if (Math.abs(took - 6f) > 0.01f) bad.add("the Ward Sigil turned a hit of 10 into " + took + ", not 6");
                    if (FortressSpells.ward(dummy, 10) != 10) bad.add("the Ward Sigil softened a foe's hit");
                    p.setHealth(p.getMaxHealth());
                }
                if (t == 22) {   // everything goes at once when it ends
                    FortressFx.testClearCds(p);
                    FortressFx.siegePage(p, 10);
                    FortressSiege.endAll(p, false);
                    long left = sl().getEntities((Entity) null, new AABB(home, home).inflate(40), e -> e.isAlive() && (e instanceof com.ironspellgrimoire.entity.FortressWall
                            || e instanceof com.ironspellgrimoire.entity.FortressTower || e instanceof com.ironspellgrimoire.entity.FortressDefender)).size();
                    if (left > 0) bad.add(left + " fortress pieces stayed after the siege ended");
                }
                if (t == 24) {
                    String ch = changes(before);
                    if (!ch.isEmpty()) bad.add("changed the world: " + ch);
                    if (bad.isEmpty()) pass(name, "built, paired, moved, upgraded, levelled, passives, garrison, siege, walls, keepsake, sigil, cleared");
                    else fail(name, String.join("; ", bad));
                    FortressFx.testReset(p);
                    FortressSpells.testClear();
                    return true;
                }
            } catch (Throwable x) { fail(name, "threw " + x); FortressFx.testReset(p); return true; }
            return false;
        }
    }

    /** The Architect of Ruin's siege shapes, the garrison's orders, overcharging a tower, breaches, prestige and the
     *  prestige rooms - and nothing changed in the world. */
    private static final class ArchitectCommandTest extends Test {
        ArchitectCommandTest() { super("architect: shapes, orders, prestige"); }
        @Override Class<?>[] roots() { return new Class<?>[]{FortressFx.class, FortressSiege.class, FortressSpells.class, com.ironspellgrimoire.entity.FortressWall.class,
                com.ironspellgrimoire.entity.FortressTower.class, com.ironspellgrimoire.entity.FortressDefender.class}; }
        final java.util.List<String> bad = new java.util.ArrayList<>();
        BlockState[] before;
        com.ironspellgrimoire.entity.FortressTower charged;
        @Override void start() {
            cancelCast(); clearArea(); resetPlayer(true); ensureDummy();
            ItemStack book = new ItemStack(com.ironspellgrimoire.registry.AddonItemRegistry.ARCHITECT_GRIMOIRE.get());
            CompoundTag mt = new CompoundTag(); mt.putInt("xp", 10_000_000);
            book.getOrCreateTag().put(GrimoireMastery.masteryTag(), mt);
            top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(p).ifPresent(h -> h.setEquippedCurio("spellbook", 0, book));
            FortressFx.testReset(p);
            FortressSpells.testClear();
            before = snapshot();
        }
        void fresh(int garrison) {
            FortressSiege.endAll(p, false);
            FortressFx.testReset(p); FortressFx.testUnlockAll(p); FortressFx.testStone(p, 100000); FortressFx.testClearCds(p);
            for (int k = 0; k < garrison; k++) { FortressSiege.testBanner(p, "minecraft:zombie"); FortressFx.handle(p, 4, 0); }
            p.teleportTo(sl(), home.x, home.y, home.z, 0f, 0f);
            p.setYRot(0); p.setXRot(0);
        }
        int gates() { return (int) FortressSiege.testWallList(p).stream().filter(x -> x.isAlive() && x.kind() == com.ironspellgrimoire.entity.FortressWall.GATE).count(); }
        @Override boolean tick(int t) {
            try {
                MagicData.getPlayerMagicData(p).setMana(1000);
                p.setHealth(p.getMaxHealth());
                if (t == 2) {   // the three shapes, sneak to switch, the Citadel always a ring
                    fresh(0);
                    FortressFx.siegePage(p, 10);
                    int ringWalls = FortressSiege.testWalls(p), ringTowers = FortressSiege.testTowers(p);
                    if (ringWalls < 30 || gates() != 1 || ringTowers < 2) bad.add("Ring: " + ringWalls + " walls, " + gates() + " gates, " + ringTowers + " towers");
                    fresh(0);
                    p.setShiftKeyDown(true);
                    FortressFx.siegePage(p, 10);
                    p.setShiftKeyDown(false);
                    if (FortressSiege.active(p)) bad.add("sneak-casting Siege Mode cast it instead of switching the shape");
                    if (FortressFx.shape(p) != FortressFx.LINE) bad.add("sneak-casting Siege Mode didn't switch Ring to Line (shape " + FortressFx.shape(p) + ")");
                    FortressFx.siegePage(p, 10);
                    int lw = FortressSiege.testWalls(p), lt = FortressSiege.testTowers(p);
                    if (lw < 10 || lw > 13 || gates() != 0 || lt < 2 || lt > 3) bad.add("Line: " + lw + " walls, " + gates() + " gates, " + lt + " towers (want 13, 0, 2-3)");
                    var any = FortressSiege.testWallList(p).stream().filter(x -> x.isAlive()).findFirst();
                    if (any.isPresent() && Math.abs(any.get().hp() - FortressSiege.wallHp(p, false) * 1.25f) > 0.01f) bad.add("the Line's walls aren't 25% tougher");
                    for (var x : FortressSiege.testWallList(p)) if (x.isAlive() && x.getZ() < home.z) { bad.add("the Line's wall stands behind you, not in front"); break; }
                    FortressSiege.endAll(p, false); FortressFx.testClearCds(p);
                    FortressFx.handle(p, 10, FortressFx.HORSESHOE);
                    FortressFx.siegePage(p, 10);
                    int hw = FortressSiege.testWalls(p), ht = FortressSiege.testTowers(p);
                    if (hw < 25 || hw > 33 || gates() != 0 || ht < 3 || ht > 5) bad.add("Horseshoe: " + hw + " walls, " + gates() + " gates, " + ht + " towers (want 33, 0, 3-5)");
                    for (var x : FortressSiege.testWallList(p)) if (x.isAlive() && x.getZ() < home.z - 5) { bad.add("the Horseshoe isn't open behind you"); break; }
                    FortressSiege.endAll(p, false); FortressFx.testClearCds(p);
                    FortressFx.testLevel(p, 10);
                    FortressFx.handle(p, 10, FortressFx.LINE);
                    FortressFx.citadelPage(p, 10);
                    if (!FortressSiege.active(p) || gates() != 1) bad.add("the Citadel wasn't a ring with its gate (" + gates() + " gates)");
                    FortressSiege.endAll(p, false);
                }
                if (t == 4) {   // the orders
                    fresh(4);
                    FortressFx.handle(p, 12, FortressSiege.HOLD);
                    if (FortressSiege.testOrder(p) != FortressSiege.FREE) bad.add("an order was given with no defenders out");
                    FortressFx.siegePage(p, 10);
                    var ds = FortressSiege.testDefenderList(p);
                    if (ds.size() != 4) bad.add(ds.size() + " defenders out, not 4");
                    FortressFx.handle(p, 12, FortressSiege.HOLD);
                    if (FortressSiege.testOrder(p) != FortressSiege.HOLD) bad.add("Hold the Gate didn't take");
                    var foot = ds.stream().filter(d -> !d.ranged()).findFirst().orElse(null);
                    var gate = FortressSiege.testGate(p);
                    if (foot != null && gate != null) {
                        var h = FortressSiege.orderHome(foot);
                        if (h == null || h.distanceTo(gate) > 3.5) bad.add("Hold the Gate didn't send a footman to the gate");
                    }
                    FortressFx.handle(p, 12, FortressSiege.HOLD);
                    if (FortressSiege.testOrder(p) != FortressSiege.FREE) bad.add("giving Hold the Gate again didn't lift it");
                    FortressFx.handle(p, 12, FortressSiege.CHARGE);
                    if (FortressSiege.testOrder(p) != FortressSiege.CHARGE) bad.add("Charge didn't take with a foe near");
                    if (!ds.isEmpty() && FortressSiege.chargeTarget(ds.get(0)) != dummy) bad.add("Charge didn't go for the dummy");
                    if (!ds.isEmpty() && ds.get(0).getAttribute(Attributes.MOVEMENT_SPEED).getModifier(FortressSiege.CHARGE_SPEED) == null) bad.add("charging defenders aren't faster");
                    FortressFx.handle(p, 12, FortressSiege.FALL_BACK);
                    if (FortressSiege.testOrder(p) != FortressSiege.FALL_BACK) bad.add("Fall Back didn't take");
                    if (!ds.isEmpty() && ds.get(0).getAttribute(Attributes.MOVEMENT_SPEED).getModifier(FortressSiege.CHARGE_SPEED) != null) bad.add("the charge's speed stayed after Fall Back");
                    if (!ds.isEmpty() && (FortressSiege.orderHome(ds.get(0)) == null || FortressSiege.orderHome(ds.get(0)).distanceTo(p.position()) > 0.01)) bad.add("Fall Back doesn't send defenders to the player");
                    dummy.teleportTo(home.x, home.y, home.z + 12);
                    if (!ds.isEmpty() && FortressSiege.mayChase(ds.get(0), dummy)) bad.add("on Fall Back a defender would chase a foe 12 blocks away");
                    dummy.teleportTo(home.x, home.y, home.z + 6);
                    // a breach: an enemy breaks a wall; Hold the Gate sends defenders to fill it
                    FortressFx.handle(p, 12, FortressSiege.HOLD);
                    var w = FortressSiege.testWallList(p).stream().filter(x -> x.isAlive() && x.kind() == com.ironspellgrimoire.entity.FortressWall.WALL).findFirst().orElse(null);
                    if (w == null) bad.add("no wall to breach");
                    else {
                        w.hurt(dummy.damageSources().mobAttack(dummy), 100000);
                        var b = FortressSiege.testBreach(p);
                        if (b == null) bad.add("a wall broken by a foe made no breach");
                        else if (foot != null && (FortressSiege.orderHome(foot) == null || FortressSiege.orderHome(foot).distanceTo(b) > 0.01)) bad.add("Hold the Gate didn't send defenders to the breach");
                    }
                }
                if (t == 6) {   // overcharging the tower you look at
                    var towers = FortressSiege.testTowerList(p);
                    if (towers.isEmpty()) bad.add("no tower to overcharge");
                    else {
                        charged = towers.get(0);
                        p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, charged.position().add(0, charged.getBbHeight() * 0.5, 0));
                        MagicData.getPlayerMagicData(p).setMana(100);
                        FortressSiege.testOvercharge(p);
                        if (!charged.overcharged()) bad.add("looking at a tower and overcharging didn't overcharge it");
                        if (Math.abs(MagicData.getPlayerMagicData(p).getMana() - (100 - FortressSiege.OVER_MANA)) > 0.5f) bad.add("overcharging cost " + (100 - MagicData.getPlayerMagicData(p).getMana()) + " mana, not " + FortressSiege.OVER_MANA);
                        float m0 = MagicData.getPlayerMagicData(p).getMana();
                        FortressSiege.testOvercharge(p);
                        if (MagicData.getPlayerMagicData(p).getMana() != m0) bad.add("overcharged the same tower again straight away");
                        p.setXRot(-80);   // looking at the sky: nothing to overcharge
                    }
                }
                if (t == 6 + FortressSiege.OVER_TICKS + 4) {
                    if (charged != null && charged.isAlive() && charged.overcharged()) bad.add("the overcharge didn't wear off after 5 s");
                }
                if (t == 112) {   // prestige
                    fresh(2);
                    FortressFx.handle(p, 11, 0);
                    if (FortressFx.stars(p) != 0) bad.add("prestiged at book level 1");
                    FortressFx.testLevel(p, 10);
                    FortressFx.handle(p, 1, FortressFx.BARRACKS * 100);
                    FortressFx.handle(p, 1, FortressFx.FORGE * 100 + 1);
                    int stone = FortressFx.stone(p), bp = FortressFx.testBlueprints(p);
                    float m0 = FortressFx.mult(p, -1) / FortressFx.mult(p, 0);
                    FortressFx.handle(p, 11, 0);
                    if (FortressFx.stars(p) != 1) bad.add("prestige at level 10, mastered, gave " + FortressFx.stars(p) + " stars");
                    if (FortressFx.level(p) != 1 || FortressFx.builtCount(p) != 0) bad.add("prestige didn't reset the level and the plan");
                    if (FortressSiege.testGarrison(p) != 2) bad.add("prestige lost the garrison");
                    if (FortressFx.stone(p) != stone || FortressFx.testBlueprints(p) != bp) bad.add("prestige lost Stone or blueprints");
                    float m1 = FortressFx.mult(p, -1) / FortressFx.mult(p, 0);
                    if (Math.abs(m1 / m0 - 1.05f) > 0.001f) bad.add("one star made towers " + m1 / m0 + "x, not 1.05x");
                    if (!FortressFx.unlocked(p, FortressFx.HERALD) || FortressFx.unlocked(p, FortressFx.MAP_ROOM)) bad.add("star 1 didn't open the Herald's Gate alone");
                    FortressSiege.banners(p).clear();
                    for (int k = 0; k < 10; k++) FortressSiege.maybeBanner(p, dummy, true);
                    if (FortressSiege.banners(p).size() != FortressSiege.MAX_BANNERS + 1) bad.add(FortressSiege.banners(p).size() + " banners wait with one star, not " + (FortressSiege.MAX_BANNERS + 1));
                    FortressFx.testStars(p, FortressFx.MAX_STARS);
                    FortressFx.testLevel(p, 10);
                    FortressFx.handle(p, 11, 0);
                    if (FortressFx.stars(p) != FortressFx.MAX_STARS || FortressFx.level(p) != 10) bad.add("prestiged past " + FortressFx.MAX_STARS + " stars");
                }
                if (t == 114) {   // the prestige rooms
                    fresh(1);
                    FortressFx.testStars(p, FortressFx.MAX_STARS);
                    FortressFx.handle(p, 1, FortressFx.THRONE * 100);
                    FortressFx.handle(p, 1, FortressFx.MAP_ROOM * 100 + 1);
                    FortressFx.handle(p, 1, FortressFx.HERALD * 100 + 2);
                    if (!FortressFx.built(p, FortressFx.THRONE) || !FortressFx.built(p, FortressFx.MAP_ROOM) || !FortressFx.built(p, FortressFx.HERALD)) bad.add("couldn't build the prestige rooms with five stars");
                    FortressFx.siegePage(p, 10);
                    var ds = FortressSiege.testDefenderList(p);
                    if (ds.isEmpty() || ds.get(0).rank() != 1) bad.add("with the Throne Room a recruit didn't fight a rank higher (" + (ds.isEmpty() ? "none out" : "rank " + ds.get(0).rank()) + ")");
                    FortressFx.castRoom(p, FortressFx.MAP_ROOM);
                    if (!FortressSpells.revealed(p, dummy)) bad.add("Survey didn't reveal the dummy");
                    FortressFx.castRoom(p, FortressFx.THRONE);
                    if (!ds.isEmpty() && ds.get(0).getAttribute(Attributes.ATTACK_DAMAGE).getModifier(FortressSpells.DECREE) == null) bad.add("Royal Decree didn't strengthen the defenders");
                    dummy.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                    p.setXRot(0); p.setYRot(180);   // look away from the dummy: the horn drags it toward that spot
                    FortressFx.castRoom(p, FortressFx.HERALD);
                    if (dummy.getDeltaMovement().horizontalDistanceSqr() < 1e-4) bad.add("the Lure Horn didn't drag the dummy");
                    FortressSiege.endAll(p, false);
                    FortressFx.testStars(p, 0);
                    if (FortressFx.unlocked(p, FortressFx.THRONE)) bad.add("the Throne Room stayed open with no stars");
                }
                if (t == 118) {
                    ensureDummy();
                    String ch = changes(before);
                    if (!ch.isEmpty()) bad.add("changed the world: " + ch);
                    if (bad.isEmpty()) pass(name, "ring, line, horseshoe, sneak to switch, citadel ring, hold, charge, fall back, breach, overcharge, prestige, stars, prestige rooms");
                    else fail(name, String.join("; ", bad));
                    FortressFx.testReset(p);
                    FortressSpells.testClear();
                    return true;
                }
            } catch (Throwable x) { fail(name, "threw " + x); FortressFx.testReset(p); return true; }
            return false;
        }
    }

    /** Elite mobs: threat ranks, who may be one, what rolls, and every trait doing what the Codex says - and nothing changed in the world. */
    /** The rivals: calling, the ring, records, yielding and rewards, rematch ranks, the twins' grief, the Legion, Mira's deals, the token. */
    private static final class RivalTest extends Test {
        RivalTest() { super("rivals: ring, yield, rewards, twins, legion, deals, token"); }
        @Override Class<?>[] roots() { return new Class<?>[]{Rivals.class, SerenAI.class, BrannochAI.class, TwinsAI.class, MiraAI.class, RivalKeepsakes.class,
                com.ironspellgrimoire.entity.Rival.class, com.ironspellgrimoire.entity.TorLegionnaire.class, Boards.class}; }
        final java.util.List<String> bad = new java.util.ArrayList<>();
        com.ironspellgrimoire.entity.Rival seren, alder, wren, bran, mira;
        int stars0;

        void yieldIt(com.ironspellgrimoire.entity.Rival r) {
            r.setPhase(3);
            r.setHealth(r.getMaxHealth() * 0.05f);
            r.invulnerableTime = 0;
            r.hurt(p.damageSources().playerAttack(p), r.getMaxHealth() * 0.1f);
        }
        void clearRivals() {
            for (var r : sl().getEntitiesOfClass(com.ironspellgrimoire.entity.Rival.class, new AABB(home, home).inflate(60))) r.discard();
            for (var l : sl().getEntitiesOfClass(com.ironspellgrimoire.entity.TorLegionnaire.class, new AABB(home, home).inflate(60))) l.discard();
        }

        @Override void start() {
            cancelCast(); clearArea(); resetPlayer(true); clearRivals();
            p.getPersistentData().remove(Rivals.KEY);
            Bounties.addStars(p, 10 - Bounties.stars(p));
            stars0 = Bounties.stars(p);
            p.teleportTo(sl(), home.x, home.y, home.z, 0, 0);
        }

        @Override boolean tick(int t) {
            try {
                if (t != 3 && t < 200) { p.teleportTo(home.x, home.y, home.z); p.setHealth(p.getMaxHealth()); }
                if (t == 1) {
                    seren = Rivals.call(p, Rivals.SEREN, 0);
                    if (seren == null) { fail(name, "Seren Vale couldn't be called"); return true; }
                    if (seren.arena == null || !p.getUUID().equals(seren.challenger)) bad.add("a called rival has no ring or no challenger");
                }
                if (t == 3) {   // from outside the ring, blows don't land
                    Vec3 out = seren.arena.add(seren.arena.subtract(home).normalize().scale(Rivals.RING + 6));
                    p.teleportTo(out.x, home.y, out.z);
                    float h = seren.getHealth();
                    seren.invulnerableTime = 0;
                    seren.hurt(p.damageSources().playerAttack(p), 10);
                    if (seren.getHealth() < h) bad.add("a blow from outside the ring landed");
                }
                if (t == 6) {
                    if (Rivals.met(p, Rivals.SEREN) != 1) bad.add("meeting Seren wasn't recorded (" + Rivals.met(p, Rivals.SEREN) + ")");
                    seren.dodgeAt = Long.MAX_VALUE; seren.parryUntil = 0;   // no sidestep or parry for the test's blow
                    yieldIt(seren);
                    if (!seren.yielded || !seren.over || !seren.isAlive()) bad.add("Seren didn't yield at her last line (yielded " + seren.yielded + ", alive " + seren.isAlive() + ")");
                    if (Rivals.wins(p, Rivals.SEREN) != 1) bad.add("the win wasn't recorded");
                    if (Bounties.stars(p) != stars0 + 2) bad.add("a rank-0 win paid " + (Bounties.stars(p) - stars0) + " stars, not 2");
                    if (!p.getInventory().contains(new ItemStack(com.ironspellgrimoire.registry.AddonItemRegistry.PRISM_RIBBON.get()))) bad.add("the first win gave no Prism Ribbon");
                }
                if (t == 110) {
                    if (seren.isAlive() && !seren.isRemoved()) bad.add("a yielded rival never left");
                    if (Rivals.rankFor(p, Rivals.SEREN) != 1) bad.add("the rematch isn't rank 1");
                    var again = Rivals.call(p, Rivals.SEREN);
                    if (again == null || Math.abs(again.getMaxHealth() - 300 * 1.3f) > 1) bad.add("a rank-1 rematch has " + (again == null ? "nothing" : again.getMaxHealth()) + " health, not 390");
                    if (again != null) again.discard();
                }
                if (t == 112) {   // the twins
                    clearRivals();
                    Rivals.call(p, Rivals.TWINS);
                    var as = sl().getEntitiesOfClass(com.ironspellgrimoire.entity.AlderBellmar.class, new AABB(home, home).inflate(40));
                    var ws = sl().getEntitiesOfClass(com.ironspellgrimoire.entity.WrenBellmar.class, new AABB(home, home).inflate(40));
                    if (as.size() != 1 || ws.size() != 1) { bad.add("the twins came " + as.size() + " + " + ws.size()); }
                    else {
                        alder = as.get(0); wren = ws.get(0);
                        if (!wren.getUUID().equals(alder.partner) || !alder.getUUID().equals(wren.partner)) bad.add("the twins aren't linked");
                    }
                }
                if (t == 116 && alder != null) {
                    yieldIt(alder);
                    if (!alder.yielded || alder.over) bad.add("one twin down ended the fight");
                    if (wren.griefUntil <= sl().getGameTime()) bad.add("Wren didn't grieve");
                    alder.yieldT = 598;
                }
                if (t == 120 && alder != null) {
                    if (alder.yielded) bad.add("Alder didn't get back up after 30 s of Wren standing");
                    else if (alder.getHealth() > alder.getMaxHealth() * 0.31f) bad.add("Alder got up with " + alder.getHealth() + " health");
                    yieldIt(alder);
                    yieldIt(wren);
                    if (!alder.over || !wren.over) bad.add("both twins down didn't end the fight");
                    if (Rivals.wins(p, Rivals.TWINS) != 1) bad.add("the twins' win wasn't recorded");
                }
                if (t == 124) {   // Brannoch and his Legion
                    clearRivals();
                    bran = Rivals.call(p, Rivals.BRANNOCH);
                    if (bran instanceof com.ironspellgrimoire.entity.BrannochTor b) BrannochAI.shieldWall(b, p, 3);
                }
                if (t == 140 && bran instanceof com.ironspellgrimoire.entity.BrannochTor b) {
                    var legion = BrannochAI.legion(b);
                    if (legion.size() < 3) bad.add("Shield Wall raised " + legion.size() + " soldiers, not 3");
                    if (legion.size() >= 2) {
                        var front = legion.get(0); var back = legion.get(1);
                        front.setNoAi(true); back.setNoAi(true);
                        front.moveTo(home.x + 3, home.y, home.z, 0, 0); back.moveTo(home.x - 3, home.y, home.z, 0, 0);
                        Vec3 tf = home.subtract(front.position()), tb = home.subtract(back.position());
                        front.setYRot((float) Math.toDegrees(Math.atan2(-tf.x, tf.z))); front.yHeadRot = front.getYRot(); front.yBodyRot = front.getYRot();
                        back.setYRot((float) Math.toDegrees(Math.atan2(tb.x, -tb.z))); back.yHeadRot = back.getYRot(); back.yBodyRot = back.getYRot();
                        float a = BrannochAI.shield(front, p.damageSources().playerAttack(p), 10), c = BrannochAI.shield(back, p.damageSources().playerAttack(p), 10);
                        if (a > 3.5f || c < 9.9f) bad.add("the shield took " + a + " of 10 from the front and " + c + " from behind");
                    }
                    BrannochAI.standard(b, p);
                    if (!BrannochAI.bearerUp(b) || Math.abs(BrannochAI.guard(b, p.damageSources().playerAttack(p)) - 0.5f) > 0.01f) bad.add("the standard didn't halve his wounds");
                    Rivals.leave(b, false);
                    if (!BrannochAI.legion(b).isEmpty()) bad.add("the Legion stayed after he left");
                }
                if (t == 142) {   // Mira
                    clearRivals();
                    mira = Rivals.call(p, Rivals.MIRA);
                    if (mira instanceof com.ironspellgrimoire.entity.MiraHollowell m) {
                        MiraAI.testOffer(m, p);
                        if (!MiraAI.offering(p)) bad.add("Mira's deal wasn't offered");
                        MiraAI.answer(p, true);
                        if (MiraAI.offering(p) || (MiraAI.deal(p) == null && !MiraAI.LOAN_DUE.containsKey(p.getUUID()))) bad.add("accepting a deal did nothing");
                        var md = io.redspace.ironsspellbooks.api.magic.MagicData.getPlayerMagicData(p);
                        float max = (float) p.getAttributeValue(io.redspace.ironsspellbooks.api.registry.AttributeRegistry.MAX_MANA.get());
                        md.setMana(max);
                        MiraAI.testCollateralize(m, p);
                        if (!MiraAI.testCollateral(p) || md.getMana() > max * 0.75f) bad.add("Collateral took no mana");
                        for (int k = 0; k < 3; k++) MiraAI.struck(m, p);
                        if (MiraAI.testCollateral(p) || md.getMana() < max * 0.99f) bad.add("three hits didn't get the collateral back (" + md.getMana() + "/" + max + ")");
                        Rivals.leave(m, false);
                    } else bad.add("Mira couldn't be called");
                }
                if (t == 146) {   // the Rival's Token, from the Poster Board
                    clearRivals();
                    int before = Bounties.stars(p);
                    Boards.pick(p, Boards.BOUNTY, -3);
                    if (Bounties.stars(p) != before - Boards.TOKEN_COST) bad.add("the token cost " + (before - Bounties.stars(p)) + " stars");
                    boolean has = false;
                    for (ItemStack st : p.getInventory().items) if (st.is(com.ironspellgrimoire.registry.AddonItemRegistry.RIVAL_TOKEN.get())) has = true;
                    if (!has) bad.add("no token was given");
                    if (!Rivals.useToken(p, Rivals.MIRA) || sl().getEntitiesOfClass(com.ironspellgrimoire.entity.MiraHollowell.class, new AABB(home, home).inflate(40)).isEmpty()) bad.add("the token didn't call Mira");
                    if (Rivals.useToken(p, Rivals.SEREN)) bad.add("a second rival came while one was here");
                    clearRivals();
                    // the Prism Ribbon: a blow strikes a reflection
                    ItemStack rib = new ItemStack(com.ironspellgrimoire.registry.AddonItemRegistry.PRISM_RIBBON.get());
                    top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(p).ifPresent(h -> h.setEquippedCurio("charm", 0, rib));
                    RivalKeepsakes.RIBBON.remove(p.getUUID());
                    var z = net.minecraft.world.entity.EntityType.ZOMBIE.create(sl());
                    z.moveTo(home.x + 2, home.y, home.z); sl().addFreshEntity(z);
                    int hits = playerHits;
                    p.invulnerableTime = 0;
                    p.hurt(z.damageSources().mobAttack(z), 4);
                    if (playerHits != hits || !RivalKeepsakes.RIBBON.containsKey(p.getUUID())) bad.add("the Prism Ribbon let the blow through");
                    z.discard();
                    top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(p).ifPresent(h -> h.setEquippedCurio("charm", 0, ItemStack.EMPTY));
                    if (bad.isEmpty()) pass(name, "ring, records, yield, keepsake, rank 1 rematch, twins' grief and rising, shields, standard, deals, collateral, token, Prism Ribbon");
                    else fail(name, String.join("; ", bad));
                    clearRivals(); clearArea();
                    p.getPersistentData().remove(Rivals.KEY);
                    return true;
                }
                return false;
            } catch (Throwable x) { fail(name, "threw " + x + " at tick " + t); clearRivals(); clearArea(); return true; }
        }
    }

    private static final class EliteTest extends Test {
        EliteTest() { super("elites: threat and every trait"); }
        @Override Class<?>[] roots() { return new Class<?>[]{EliteMobs.class, EliteTraits.class, com.ironspellgrimoire.entity.CursedChest.class, com.ironspellgrimoire.entity.EliteRift.class}; }
        final java.util.List<String> bad = new java.util.ArrayList<>();
        BlockState[] before;
        net.minecraft.world.entity.Mob over, echo, seeker, dagger, hunter, sprinter, anchored, reflector, pillager, twin0, ghostFrom;
        Vec3 anchorAt, riftOpen, riftShut, chestBite, chestWait, twinAt, echoAt;
        java.util.UUID shadowId;
        net.minecraft.world.entity.projectile.Arrow reflected;
        boolean dashed;

        net.minecraft.world.entity.Mob spawn(net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.Mob> type, double dx, double dz, int rank, int mask) {
            var m = type.create(sl());
            m.moveTo(home.x + dx, home.y, home.z + dz, 180, 0);
            m.setYBodyRot(180); m.setYHeadRot(180);
            m.setNoAi(true);
            sl().addFreshEntity(m);
            EliteMobs.testMake(m, rank, mask);
            return m;
        }
        static int bit(int t) { return 1 << t; }
        void hitPlayer(net.minecraft.world.entity.Mob m, float amount) { p.invulnerableTime = 0; p.hurt(m.damageSources().mobAttack(m), amount); }
        void hitMob(net.minecraft.world.entity.Mob m, float amount) { m.invulnerableTime = 0; m.hurt(p.damageSources().playerAttack(p), amount); }
        java.util.List<net.minecraft.world.entity.Mob> marked(Vec3 c, double r) {
            return sl().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, new AABB(c, c).inflate(r), x -> x.isAlive() && EliteMobs.marked(x) && x.position().distanceTo(c) <= r);
        }

        @Override void start() {
            cancelCast(); clearArea(); resetPlayer(true); ensureDummy();
            EliteTraits.testEchoHits = 0; EliteTraits.testBurstHits = 0; EliteTraits.testOrbs = 0; EliteTraits.testDaggers = 0;
            EliteTraits.clearShots();
            before = snapshot();
        }

        @Override boolean tick(int t) {
            try {
                var V = net.minecraft.world.entity.EntityType.VINDICATOR;
                if (t == 2) {   // threat, who may be one, what rolls, what a rank does
                    ItemStack book = new ItemStack(com.ironspellgrimoire.registry.AddonItemRegistry.ARCHITECT_GRIMOIRE.get());
                    CompoundTag mt = new CompoundTag(); mt.putInt("xp", 10_000_000);
                    book.getOrCreateTag().put(GrimoireMastery.masteryTag(), mt);
                    top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(p).ifPresent(h -> h.setEquippedCurio("spellbook", 0, book));
                    EliteMobs.testForgetThreat(p);
                    int th = EliteMobs.threat(p);
                    if (th != 4) bad.add("a mastered grimoire gave threat " + th + ", not 4");
                    top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(p).ifPresent(h -> h.setEquippedCurio("spellbook", 0, ItemStack.EMPTY));
                    EliteMobs.testForgetThreat(p);
                    if (EliteMobs.threat(p) != 0) bad.add("no grimoire still gave threat " + EliteMobs.threat(p));
                    var plain = V.create(sl()); plain.moveTo(home.x + 20, home.y, home.z + 20);
                    sl().addFreshEntity(plain);
                    if (!EliteMobs.eligible(plain)) bad.add("a wild vindicator can't be an elite");
                    plain.getPersistentData().putUUID("isg_bounty_owner", p.getUUID());
                    if (EliteMobs.eligible(plain)) bad.add("a bounty target could be an elite");
                    if (EliteMobs.eligible(dummy)) bad.add("a mob with no AI could be an elite");
                    var golem = net.minecraft.world.entity.EntityType.IRON_GOLEM.create(sl()); golem.moveTo(home.x + 20, home.y, home.z - 20);
                    sl().addFreshEntity(golem);
                    if (EliteMobs.eligible(golem)) bad.add("an iron golem could be an elite");
                    golem.discard(); plain.discard();
                    int champs = 0, models = 0, tooMany = 0;
                    for (int k = 0; k < 300; k++) {
                        int m = EliteMobs.pick(plain, 2, 3);
                        if ((m & EliteMobs.CHAMPION) != 0) champs++;
                        if ((m & EliteMobs.MODEL_TRAITS) != 0 && !EliteMobs.modelsApproved) models++;
                        if (Integer.bitCount(m) > 3) tooMany++;
                        int m5 = EliteMobs.pick(plain, 5, 3);
                        if (Integer.bitCount(m5 & EliteMobs.CHAMPION) > 1) tooMany++;
                    }
                    if (champs > 0) bad.add("champion traits rolled at threat 2");
                    if (models > 0) bad.add("traits whose models aren't approved yet rolled");
                    if (tooMany > 0) bad.add("too many traits (or two champion traits) rolled");
                    var r3 = spawn(V, 20, -10, 3, 0);
                    double hp = r3.getAttribute(Attributes.MAX_HEALTH).getValue() / r3.getAttribute(Attributes.MAX_HEALTH).getBaseValue();
                    double dm = r3.getAttribute(Attributes.ATTACK_DAMAGE).getValue() / r3.getAttribute(Attributes.ATTACK_DAMAGE).getBaseValue();
                    if (Math.abs(hp - 1.6) > 0.01 || Math.abs(dm - 1.3) > 0.01) bad.add("rank 3 gave " + hp + "x health and " + dm + "x damage, not 1.6 and 1.3");
                    if (r3.getHealth() < r3.getMaxHealth() - 0.01) bad.add("a ranked mob didn't start at full health");
                    if (EliteMobs.testPacket(r3) == null) bad.add("no sync packet");
                    r3.discard();
                }
                if (t == 4) {   // Bulwark, Ironhide, Second Wind
                    var b = spawn(V, 5, 3, 0, bit(EliteMobs.BULWARK));   // faces -z: toward the player at home
                    p.teleportTo(sl(), home.x + 5, home.y, home.z, 0, 0);
                    float h0 = b.getHealth(); hitMob(b, 10);
                    float front = h0 - b.getHealth();
                    p.teleportTo(sl(), home.x + 5, home.y, home.z + 6, 180, 0);
                    h0 = b.getHealth(); hitMob(b, 10);
                    float back = h0 - b.getHealth();
                    p.teleportTo(sl(), home.x, home.y, home.z, 0, 0);
                    if (back <= 0 || Math.abs(front / back - 0.3f) > 0.03f) bad.add("Bulwark took " + front + " from the front and " + back + " from behind (want 30% from the front)");
                    b.discard();
                    var ih = spawn(V, -5, 3, 0, bit(EliteMobs.IRONHIDE));
                    for (int k = 0; k < 3; k++) hitMob(ih, 5);
                    if (ih.getHealth() < ih.getMaxHealth()) bad.add("Ironhide's plates didn't soak the first 3 hits");
                    if (EliteMobs.data(ih).getInt("ih") != 3) bad.add("Ironhide counted " + EliteMobs.data(ih).getInt("ih") + " plates broken, not 3");
                    hitMob(ih, 5);
                    if (ih.getHealth() >= ih.getMaxHealth()) bad.add("the 4th hit on Ironhide didn't land");
                    ih.discard();
                    var sw = spawn(V, 8, 0, 0, bit(EliteMobs.SECOND_WIND));
                    hitMob(sw, 1000);
                    if (!sw.isAlive() || Math.abs(sw.getHealth() - sw.getMaxHealth() * 0.5f) > 0.1f) bad.add("Second Wind didn't bring it back at half health");
                    hitMob(sw, 1000);
                    if (sw.isAlive()) bad.add("Second Wind worked twice");
                }
                if (t == 6) {   // everything that takes a while
                    over = spawn(V, -8, 0, 0, bit(EliteMobs.OVERCHARGED));
                    for (int k = 0; k < 3; k++) hitPlayer(over, 2);
                    if (EliteTraits.testWindup(over) != 20) bad.add("three blows didn't start Overcharged's wind-up (" + EliteTraits.testWindup(over) + ")");
                    echo = spawn(V, 2, -2, 0, bit(EliteMobs.ECHO));
                    echoAt = p.position();
                    hitPlayer(echo, 4);
                    var leech = spawn(V, -2, -2, 0, bit(EliteMobs.LEECH));
                    MagicData.getPlayerMagicData(p).setMana(100);
                    hitPlayer(leech, 2);
                    float mana = MagicData.getPlayerMagicData(p).getMana();
                    if (Math.abs(mana - 90f) > 0.5f) bad.add("Mana Leech left " + mana + " mana of 100, not 90");
                    leech.discard();
                    var dis = spawn(V, -2, 2, 0, bit(EliteMobs.DISRUPTOR));
                    hitPlayer(dis, 1);   // (not casting: nothing to cut off, and nothing breaks)
                    dis.discard();
                    var teth = spawn(V, 0, -14, 0, bit(EliteMobs.TETHER));
                    p.setDeltaMovement(Vec3.ZERO);
                    EliteTraits.testTether(teth, p);
                    if (p.getDeltaMovement().z >= 0) bad.add("Tether didn't drag the player toward it");
                    if (EliteTraits.testRun(teth) == null || EliteTraits.testRun(teth).chainTo != p.getId()) bad.add("Tether's chain isn't shown");
                    teth.discard();
                    p.setDeltaMovement(Vec3.ZERO);
                    seeker = spawn(V, -4, -12, 0, bit(EliteMobs.SEEKER)); seeker.setTarget(p);
                    dagger = spawn(V, 0, -7, 0, bit(EliteMobs.VOLLEY)); dagger.setTarget(p);
                    hunter = spawn(V, 15, -12, 0, bit(EliteMobs.HUNTER)); hunter.setTarget(p);
                    sprinter = spawn(V, -8, -4, 0, bit(EliteMobs.SPRINTER)); sprinter.setTarget(p);
                    anchored = spawn(V, 6, -6, 0, bit(EliteMobs.ANCHORED));
                    anchorAt = anchored.position();
                    anchored.setDeltaMovement(2, 0.6, 0); anchored.hurtMarked = true;
                    pillager = spawn(net.minecraft.world.entity.EntityType.PILLAGER, 8, 12, 0, bit(EliteMobs.VOLLEY));
                    sl().addFreshEntity(new net.minecraft.world.entity.projectile.Arrow(sl(), pillager));
                    reflector = spawn(V, -6, 3, 0, bit(EliteMobs.REFLECTOR));
                    long g = sl().getGameTime();
                    EliteMobs.data(reflector).putInt("phase", (int) ((EliteTraits.SHELL_CYCLE - g % EliteTraits.SHELL_CYCLE) % EliteTraits.SHELL_CYCLE));
                    reflected = new net.minecraft.world.entity.projectile.Arrow(sl(), p);
                    reflected.moveTo(reflector.getX(), reflector.getY() + 1, reflector.getZ() - 2.5, 0, 0);
                    reflected.setDeltaMovement(0, 0, 1.2);
                    sl().addFreshEntity(reflected);
                    var split = spawn(V, 10, 0, 0, bit(EliteMobs.SPLITTER));
                    hitMob(split, 1000);
                    var children = marked(new Vec3(home.x + 10, home.y, home.z), 2.5);
                    children.removeIf(x -> !EliteMobs.child(x));
                    if (children.size() != 2) bad.add("Splitter left " + children.size() + " copies, not 2");
                    else {
                        var c = children.get(0);
                        if (Math.abs(c.getMaxHealth() - 12f) > 0.1f) bad.add("a Splitter copy has " + c.getMaxHealth() + " max health, not 12");
                        if (Math.abs(c.getBbHeight() - V.getHeight() * 0.6f) > 0.05f) bad.add("a Splitter copy is " + c.getBbHeight() + " tall, not 60%");
                        if (EliteMobs.traits(c) != 0) bad.add("a Splitter copy has traits of its own");
                    }
                    var vol = spawn(V, 12, 4, 0, bit(EliteMobs.VOLATILE));
                    var golem = net.minecraft.world.entity.EntityType.IRON_GOLEM.create(sl());
                    golem.moveTo(home.x + 12, home.y, home.z + 5.5); golem.setNoAi(true); sl().addFreshEntity(golem);
                    hitMob(vol, 1000);
                    var c1 = spawn(V, 10, 8, 0, bit(EliteMobs.CURSED_LOOT)); chestBite = c1.position(); hitMob(c1, 1000);
                    var c2 = spawn(V, -10, 8, 0, bit(EliteMobs.CURSED_LOOT)); chestWait = c2.position(); hitMob(c2, 1000);
                    ghostFrom = spawn(V, 3, -3, 0, bit(EliteMobs.VENGEFUL)); hitMob(ghostFrom, 1000);
                    var r1 = spawn(V, -12, -8, 4, bit(EliteMobs.RIFT_BORN)); riftShut = r1.position(); hitMob(r1, 1000);
                    var r2 = spawn(V, 12, -8, 4, bit(EliteMobs.RIFT_BORN)); riftOpen = r2.position(); hitMob(r2, 1000);
                    twin0 = spawn(V, -3, -10, 4, bit(EliteMobs.MIRROR_TWIN));
                    if (!EliteMobs.data(twin0).hasUUID("tw")) bad.add("Mirror Twin made no shadow");
                    else {
                        shadowId = EliteMobs.data(twin0).getUUID("tw");
                        var sh = sl().getEntity(shadowId);
                        if (!(sh instanceof net.minecraft.world.entity.Mob sm) || !EliteMobs.shadow(sm)) bad.add("Mirror Twin's copy isn't a shadow");
                    }
                }
                if (sprinter != null && EliteTraits.testRun(sprinter) != null && EliteTraits.testRun(sprinter).dashTicks > 0) dashed = true;
                if (t == 7) {
                    var ghosts = sl().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, new AABB(home, home).inflate(12), x -> EliteMobs.ghost(x));
                    if (ghosts.isEmpty()) bad.add("Vengeful left no ghost");
                }
                if (t == 9) {
                    if (anchored.position().subtract(anchorAt).horizontalDistance() > 0.5) bad.add("Anchored was pushed " + anchored.position().subtract(anchorAt).horizontalDistance() + " blocks");
                    long arrows = sl().getEntitiesOfClass(net.minecraft.world.entity.projectile.AbstractArrow.class, new AABB(home, home).inflate(30), a -> a.getOwner() == pillager).size();
                    if (arrows != 3) bad.add("Volley's archer loosed " + arrows + " arrows, not 3");
                    var chests = sl().getEntitiesOfClass(com.ironspellgrimoire.entity.CursedChest.class, new AABB(chestBite, chestBite).inflate(2));
                    if (chests.isEmpty()) bad.add("Cursed Loot left no chest");
                    else { EliteTraits.openChest(chests.get(0), p); if (!chests.get(0).bit()) bad.add("a cursed chest opened at once didn't bite"); }
                    var rifts = sl().getEntitiesOfClass(com.ironspellgrimoire.entity.EliteRift.class, new AABB(riftShut, riftShut).inflate(2));
                    if (rifts.isEmpty()) bad.add("Rift-Born left no rift");
                    else for (int k = 0; k < 4; k++) rifts.get(0).hurt(p.damageSources().playerAttack(p), 1);
                    if (!rifts.isEmpty() && rifts.get(0).isAlive()) bad.add("four hits didn't close the rift");
                    var tw = sl().getEntity(twin0.getUUID());
                    if (tw instanceof net.minecraft.world.entity.Mob m) { twinAt = m.position(); hitMob(m, 1000); }
                }
                if (t == 23) {   // blows and the chest's bite have knocked the player about: back onto the echo's spot
                    p.teleportTo(sl(), echoAt.x, echoAt.y, echoAt.z, 0, 0);
                    p.setDeltaMovement(Vec3.ZERO);
                }
                if (t == 12) {
                    if (reflected.getOwner() != reflector) bad.add("Reflector's shards didn't send the arrow back");
                }
                if (t == 40) {
                    if (EliteTraits.testEchoHits == 0) bad.add("Echo Strike's second blow never landed");
                    if (EliteTraits.testWindup(over) != 0 || EliteTraits.testRun(over).hits != 0) bad.add("Overcharged never slammed");
                    if (EliteTraits.testBurstHits == 0) bad.add("Volatile's ring never burst");
                    var mod = hunter.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(UUID.fromString("e1173000-0000-4000-8000-000000000011"));
                    if (mod == null || Math.abs(mod.getAmount() - 0.6) > 0.01) bad.add("Hunter 19 blocks off isn't 60% faster (" + (mod == null ? "none" : mod.getAmount()) + ")");
                    var ghosts = sl().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, new AABB(home, home).inflate(20), x -> EliteMobs.ghost(x));
                    if (!ghosts.isEmpty()) bad.add("the Vengeful ghost was still about after 34 ticks");
                }
                if (t == 70) {
                    if (EliteTraits.testOrbs == 0) bad.add("Seeker sent no orb");
                    if (EliteTraits.testDaggers == 0) bad.add("Volley's melee elite threw no dagger");
                    var chests = sl().getEntitiesOfClass(com.ironspellgrimoire.entity.CursedChest.class, new AABB(chestWait, chestWait).inflate(2));
                    if (chests.isEmpty()) bad.add("the second cursed chest is gone");
                    else { EliteTraits.openChest(chests.get(0), p); if (!chests.get(0).opened() || chests.get(0).bit()) bad.add("a cursed chest left 3 s still bit"); }
                }
                if (t == 112) {
                    var back = marked(twinAt, 3);
                    back.removeIf(x -> !EliteMobs.has(x, EliteMobs.MIRROR_TWIN));
                    if (back.isEmpty()) bad.add("the fallen Mirror Twin didn't rise while its shadow stood");
                    var news = marked(riftOpen, 4);
                    news.removeIf(x -> !EliteMobs.elite(x));
                    if (news.size() != 3) bad.add("an unclosed rift let out " + news.size() + " elites, not 3");
                    for (var x : news) if (EliteMobs.champion(x)) bad.add("a rift let out a champion");
                    if (marked(riftShut, 4).stream().anyMatch(EliteMobs::elite)) bad.add("a closed rift still let elites out");
                    if (!dashed) bad.add("Sprinter never dashed");
                    // both twins together: neither rises
                    for (var x : back) hitMob(x, 1000);
                    var sh = sl().getEntity(shadowId);
                    if (sh instanceof net.minecraft.world.entity.Mob m) hitMob(m, 1000);
                }
                if (t == 220) {
                    if (marked(twinAt, 6).stream().anyMatch(x -> EliteMobs.data(x).hasUUID("tw"))) bad.add("a twin rose though both fell together");
                    clearArea();
                    ensureDummy();
                    String ch = changes(before);
                    if (!ch.isEmpty()) bad.add("changed the world: " + ch);
                    if (bad.isEmpty()) pass(name, "threat, eligibility, rolls, ranks, all 20 traits");
                    else fail(name, String.join("; ", bad));
                    EliteTraits.clearShots();
                    return true;
                }
            } catch (Throwable x) { fail(name, "threw " + x + " at tick " + t); clearArea(); return true; }
            return false;
        }
    }

    /** The Parasite Grimoire's strain: devouring, the arm forms (and the item they put away and give back), locked
     *  forms, evolving, a Strain, the Bulwark, the frenzy and its end, and everything dropped when the book is. */
    /** The Masked Twins: levels and the balance rule, choosing arts for the wheel, swapping (Resonance spent, the
     *  cooldown), what may be cast, stones and sockets growing the mask, titles, Mirror Double, Lunar Veil, the trial
     *  won by beating both wardens - and nothing changed in the world, no status effects. */
    private static final class TwinsTest extends Test {
        TwinsTest() { super("twins: the masks"); }
        @Override Class<?>[] roots() { return new Class<?>[]{TwinsFx.class, com.ironspellgrimoire.entity.TwinWarden.class}; }
        final java.util.List<String> bad = new java.util.ArrayList<>();
        BlockState[] before; Set<MobEffect> pe, de;
        float hp;
        @Override void start() {
            cancelCast(); clearArea(); resetPlayer(true); ensureDummy();
            ItemStack book = new ItemStack(com.ironspellgrimoire.registry.AddonItemRegistry.TWINS_GRIMOIRE.get());
            top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(p).ifPresent(h -> h.setEquippedCurio("spellbook", 0, book));
            TwinsFx.testReset(p);
            before = snapshot(); pe = effects(p); de = effects(dummy);
        }
        @Override boolean tick(int t) {
            try {
                MagicData.getPlayerMagicData(p).setMana(1000);
                if (t == 2) {   // fresh: Sol worn, both at level 1; levelling; the balance rule
                    if (TwinsFx.active(p) != TwinsFx.SOL) bad.add("a fresh pair doesn't start with Sol worn");
                    if (TwinsFx.level(p, 0) != 1 || TwinsFx.level(p, 1) != 1) bad.add("a fresh pair isn't level 1/1");
                    TwinsFx.testXp(p, 0, TwinsFx.need(1) + TwinsFx.need(2) + 1);
                    if (TwinsFx.level(p, 0) != 3) bad.add("XP for two levels gave level " + TwinsFx.level(p, 0));
                    if (TwinsFx.level(p, 1) != 1) bad.add("Sol's XP raised Nyx");
                    TwinsFx.testLevels(p, 30, 10);
                    if (TwinsFx.weigh(p, 0) > TwinsFx.weigh(p, 1) * 0.65f) bad.add("a twin 20 levels ahead isn't slowed (" + TwinsFx.weigh(p, 0) + " vs " + TwinsFx.weigh(p, 1) + ")");
                }
                if (t == 4) {   // choosing arts: only known ones; one art in one slot
                    TwinsFx.testLevels(p, 1, 1);
                    TwinsFx.handle(p, 3, 0 * 100 + 1 * 10 + 5);
                    if (TwinsFx.testSlot(p, 0, 1) == 5) bad.add("Zenith Leap went on the wheel at level 1");
                    TwinsFx.testLevels(p, 50, 50);
                    TwinsFx.handle(p, 3, 0 * 100 + 1 * 10 + 5);
                    if (TwinsFx.testSlot(p, 0, 1) != 5) bad.add("Zenith Leap didn't go into slot 2");
                    TwinsFx.handle(p, 3, 0 * 100 + 2 * 10 + 5);
                    int n = 0;
                    for (int s = 0; s < 4; s++) if (TwinsFx.testSlot(p, 0, s) == 5) n++;
                    if (n != 1) bad.add("one art sits in " + n + " slots");
                    TwinsFx.handle(p, 3, 0 * 100 + 2 * 10 + 9);
                    if (TwinsFx.testSlot(p, 0, 2) != -1) bad.add("a slot didn't empty");
                }
                if (t == 6) {   // swapping: Resonance spent, then a cooldown
                    TwinsFx.testCooldowns(p); TwinsFx.testRes(p, 80);
                    TwinsFx.swap(p);
                    if (TwinsFx.active(p) != TwinsFx.NYX) bad.add("swapping didn't put Nyx on");
                    if (TwinsFx.resonance(p) != 0) bad.add("the burst didn't spend the Resonance");
                    if (TwinsFx.testSwapCd(p) <= 0) bad.add("no cooldown after a swap");
                    TwinsFx.swap(p);
                    if (TwinsFx.active(p) != TwinsFx.NYX) bad.add("swapped again during the cooldown");
                    int code = TwinsFx.testLookCode(p);
                    if (code / 16 - 2 != TwinsFx.maskForm(p, TwinsFx.NYX) || code % 16 - 2 != TwinsFx.maskForm(p, TwinsFx.SOL)) bad.add("the masks others see are wrong (" + code + ")");
                }
                if (t == 8) {   // what may be cast: the worn twin's chosen arts, off cooldown
                    TwinsFx.testCooldowns(p);
                    if (TwinsArts.cast(p, TwinsFx.SOL, 0)) bad.add("cast Sol's art while wearing Nyx");
                    TwinsFx.testLoad(p, 1, 0, 1, 2, 3);
                    if (TwinsArts.cast(p, TwinsFx.NYX, 7)) bad.add("cast an art that isn't on the wheel");
                    TwinsFx.testLoad(p, 1, 7, 1, 2, 3);
                    if (!TwinsArts.cast(p, TwinsFx.NYX, 7)) bad.add("couldn't cast Void Rift from the wheel");
                    if (TwinsArts.cast(p, TwinsFx.NYX, 7)) bad.add("cast Void Rift again during its cooldown");
                }
                if (t == 10) {   // stones and sockets; the mask grows
                    TwinsFx.testStones(p, 1, 0);
                    TwinsFx.handle(p, 4, 0);
                    if ((TwinsFx.testSocketMask(p, 0) & 1) == 0) bad.add("a Sunstone didn't set");
                    if (TwinsFx.testStone(p, 0) != 0) bad.add("setting a stone didn't use it");
                    TwinsFx.handle(p, 4, 1);
                    if ((TwinsFx.testSocketMask(p, 0) & 2) != 0) bad.add("a socket filled with no stone");
                    if (TwinsFx.maskForm(p, TwinsFx.SOL) != 1) bad.add("Sol's mask didn't grow to stage 2");
                    TwinsFx.testSockets(p, 7, 7);
                    if (TwinsFx.maskForm(p, TwinsFx.NYX) != 5) bad.add("Nyx's mask with three stones isn't stage 3");
                }
                if (t == 12) {   // titles: earned on the level, worn one at a time
                    TwinsFx.testLevels(p, 24, 1);
                    TwinsFx.testXp(p, 0, TwinsFx.need(24));
                    TwinsFx.handle(p, 5, 0);
                    TwinsFx.handle(p, 5, 2);
                    if (TwinsFx.root(p).getInt("title") != 0) bad.add("wore a title not earned, or couldn't wear Dawnchild");
                    if (TwinsFx.canEclipse(p)) bad.add("Eclipse Union open with no trial");
                }
                if (t == 14) {   // Mirror Double: a blow strikes twice
                    ensureDummy();
                    TwinsFx.testLevels(p, 50, 50); TwinsFx.testCooldowns(p); TwinsFx.testActive(p, TwinsFx.NYX); TwinsFx.testLoad(p, 1, 3, 4, 1, 2);
                    TwinsArts.cast(p, TwinsFx.NYX, 3);
                    if (!TwinsFx.testDoubleUp(p)) bad.add("Mirror Double didn't start");
                    hp = dummy.getHealth();
                    dummy.invulnerableTime = 0;
                    dummy.hurt(p.damageSources().playerAttack(p), 4f);
                }
                if (t == 26) {
                    float lost = hp - dummy.getHealth();
                    if (lost < 5f) bad.add("Mirror Double's echo didn't land (lost " + lost + ")");
                    TwinsArts.cast(p, TwinsFx.NYX, 4);
                    if (!TwinsFx.testVeilUp(p)) bad.add("Lunar Veil didn't start");
                    p.hurt(p.damageSources().mobAttack(dummy), 5f);
                    if (TwinsFx.testVeilUp(p)) bad.add("Lunar Veil didn't take the blow");
                    p.teleportTo(sl(), home.x, home.y, home.z, 0f, 0f);
                }
                if (t == 28) {   // the trial: both wardens beaten
                    TwinsFx.testLevels(p, 40, 40);
                    TwinsFx.handle(p, 6, 0);
                    if (!TwinsFx.testTrialRunning(p)) bad.add("the trial didn't begin");
                    var ws = sl().getEntitiesOfClass(com.ironspellgrimoire.entity.TwinWarden.class, p.getBoundingBox().inflate(20));
                    if (ws.size() != 2) bad.add(ws.size() + " wardens came, not 2");
                    for (var w : ws) w.hurt(p.damageSources().playerAttack(p), 100000f);
                }
                if (t == 32) {
                    if (!TwinsFx.trialWon(p)) bad.add("beating both wardens didn't win the trial");
                    if (!TwinsFx.canEclipse(p)) bad.add("Eclipse Union not open after the trial");
                    if ((TwinsFx.root(p).getInt("titles") & 8) == 0) bad.add("no Eclipse-Touched title");
                    TwinsArts.eclipseUnion(p, 10f);
                    if (!TwinsFx.eclipse(p)) bad.add("Eclipse Union didn't start");
                    if (!TwinsArts.cast(p, TwinsFx.SOL, 1) || !TwinsArts.cast(p, TwinsFx.SOL, 1)) bad.add("in the eclipse, an art had a cooldown");
                    TwinsFx.testEndEclipse(p);
                }
                if (t == 40) {   // a socket re-cut for a stone; mastery counts up
                    TwinsFx.testLevels(p, 50, 50); TwinsFx.testSockets(p, 0, 0); TwinsFx.testStones(p, 2, 0);
                    TwinsFx.handle(p, 4, 0 * 100 + 1 * 10 + 2);
                    if (!TwinsFx.testPerk(p, 0, 1, 2)) bad.add("Pyre Heart didn't set");
                    TwinsFx.handle(p, 4, 0 * 100 + 1 * 10 + 1);
                    if (!TwinsFx.testPerk(p, 0, 1, 1) || TwinsFx.testStone(p, 0) != 0) bad.add("re-cutting to Blazing Rush didn't take a stone and change the cut");
                    TwinsFx.handle(p, 4, 0 * 100 + 1 * 10 + 0);
                    if (TwinsFx.testCut(p, 0, 1) != 1) bad.add("re-cut with no stone left");
                    TwinsFx.testCooldowns(p); TwinsFx.testActive(p, 0); TwinsFx.testLoad(p, 0, 1, -1, -1, -1); TwinsFx.testUses(p, 0, 1, 29);
                    TwinsArts.cast(p, 0, 1);
                    if (TwinsFx.testUsesOf(p, 0, 1) != 30 || TwinsFx.mastery(30) != 1) bad.add("casting didn't count toward mastery");
                }
                if (t == 44) {   // the Echo comes out in a fight, and the hip empties
                    TwinsFx.testActive(p, 0); TwinsFx.testCombat(p);
                    TwinsFx.testEcho(p);
                    if (!TwinsFx.testEchoOut(p)) bad.add("the Echo didn't come out in a fight");
                    if (sl().getEntitiesOfClass(com.ironspellgrimoire.entity.TwinEcho.class, p.getBoundingBox().inflate(10)).size() != 1) bad.add("no Echo beside the player");
                    if (TwinsFx.testLookCode(p) % 16 - 2 != -1) bad.add("the resting mask still shows at the hip while its Echo fights");
                }
                if (t == 48) {   // the ultimates
                    TwinsFx.testCooldowns(p); TwinsFx.testActive(p, 0);
                    TwinsArts.cast(p, 0, 1);
                    TwinsFx.highNoon(p, 10f);
                    if (!TwinsFx.testNoon(p)) bad.add("High Noon didn't start");
                    if (!TwinsArts.cast(p, 0, 1)) bad.add("High Noon didn't clear Sol's cooldowns");
                    TwinsFx.testActive(p, 1);
                    TwinsFx.eventide(p, 10f);
                    if (!TwinsFx.testEventide(p)) bad.add("Eventide didn't start");
                    for (int k = 0; k < 3; k++) p.hurt(p.damageSources().mobAttack(dummy), 4f);
                    if (TwinsFx.testVeilUp(p)) bad.add("Eventide's three misses weren't used up by three blows");
                    p.teleportTo(sl(), home.x, home.y, home.z, 0f, 0f);
                }
                if (t == 120) {
                    String ch = changes(before);
                    if (!ch.isEmpty()) bad.add("changed the world: " + ch);
                    Set<MobEffect> np = effects(p), nd = effects(dummy);
                    np.removeAll(pe); nd.removeAll(de);
                    if (!np.isEmpty() || !nd.isEmpty()) bad.add("gave status effects: " + np + " " + nd);
                    TwinsFx.testReset(p);
                    if (bad.isEmpty()) pass(name, "levels, the wheel, swaps, casting rules, stones, titles, Mirror Double, Lunar Veil and the trial");
                    else fail(name, String.join("; ", bad));
                    return true;
                }
            } catch (Throwable x) { fail(name, "threw " + x); TwinsFx.testReset(p); return true; }
            return false;
        }
    }

    private static final class ParasiteTest extends Test {
        ParasiteTest() { super("parasite: the strain"); }
        @Override Class<?>[] roots() { return new Class<?>[]{ParasiteFx.class}; }
        final java.util.List<String> bad = new java.util.ArrayList<>();
        BlockState[] before;
        net.minecraft.world.entity.monster.Zombie prey;
        double baseHp;
        @Override void start() {
            cancelCast(); clearArea(); resetPlayer(true); ensureDummy();
            ItemStack book = new ItemStack(com.ironspellgrimoire.registry.AddonItemRegistry.PARASITE_GRIMOIRE.get());
            top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(p).ifPresent(h -> h.setEquippedCurio("spellbook", 0, book));
            ParasiteFx.testReset(p);
            before = snapshot();
        }
        @Override boolean tick(int t) {
            try {
                if (t == 2) {   // a weakened zombie in front: devoured whole
                    p.teleportTo(sl(), home.x, home.y, home.z, 0f, 0f);
                    prey = net.minecraft.world.entity.EntityType.ZOMBIE.create(sl());
                    if (prey != null) {
                        prey.moveTo(home.x, home.y, home.z + 2.5, 180, 0); prey.setNoAi(true);
                        sl().addFreshEntity(prey);
                        prey.setHealth(3);
                    }
                    ParasiteFx.consume(p, 8f);
                }
                if (t == 14) {
                    if (prey != null && prey.isAlive()) { bad.add("Consume didn't devour a zombie at 3 health"); prey.discard(); }
                    if (ParasiteFx.devoured(p) != 1) bad.add("devoured count is " + ParasiteFx.devoured(p));
                    if (ParasiteFx.points(p) < 1) bad.add("no evolution point for devouring");
                    if (ParasiteFx.biomass(p) <= 0) bad.add("no biomass for devouring");
                }
                if (t == 16) {   // an arm form puts the held item away, and gives it back
                    p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(net.minecraft.world.item.Items.STICK));
                    ParasiteFx.setForm(p, ParasiteFx.REAPER, false);
                    if (ParasiteFx.form(p) != ParasiteFx.REAPER) bad.add("Reaper Edge didn't take");
                    if (!p.getMainHandItem().isEmpty()) bad.add("the hand still holds " + p.getMainHandItem());
                    if (!p.getInventory().contains(new ItemStack(net.minecraft.world.item.Items.STICK))) bad.add("the held stick went missing");
                    var dmg = p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
                    if (dmg == null || dmg.getModifiers().stream().noneMatch(m -> m.getName().equals("Parasite arm"))) bad.add("no arm damage while a form is out");
                    ParasiteFx.setForm(p, ParasiteFx.NONE, false);
                    if (!p.getMainHandItem().is(net.minecraft.world.item.Items.STICK)) bad.add("the stick didn't come back to the hand (" + p.getMainHandItem() + ")");
                    p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    ParasiteFx.setForm(p, ParasiteFx.SPINE, false);
                    if (ParasiteFx.form(p) == ParasiteFx.SPINE) bad.add("the Spine Launcher came out before it was evolved");
                }
                if (t == 18) {   // evolving it
                    ParasiteFx.testFill(p);
                    ParasiteFx.handle(p, 7, ParasiteFx.F_SPINE);
                    if (ParasiteFx.rank(p, ParasiteFx.F_SPINE) != 1) bad.add("buying the Spine Launcher didn't take");
                    ParasiteFx.setForm(p, ParasiteFx.SPINE, false);
                    if (ParasiteFx.form(p) != ParasiteFx.SPINE) bad.add("the evolved Spine Launcher didn't come out");
                    ParasiteFx.setForm(p, ParasiteFx.NONE, false);
                    for (int k = 0; k < 6; k++) ParasiteFx.handle(p, 7, ParasiteFx.LEAP + (k % 3));
                    baseHp = p.getMaxHealth();
                    ParasiteFx.handle(p, 8, ParasiteFx.BRUTE);
                    if (ParasiteFx.strain(p) != ParasiteFx.BRUTE) bad.add("the Brute strain didn't settle (spent " + ParasiteFx.spent(p) + ")");
                }
                if (t == 20) {
                    if (p.getMaxHealth() < baseHp + 5.9) bad.add(String.format("Brute didn't raise max health (%.1f -> %.1f)", baseHp, p.getMaxHealth()));
                    ParasiteFx.handle(p, 8, ParasiteFx.HIVE);
                    if (ParasiteFx.strain(p) != ParasiteFx.BRUTE) bad.add("a second Strain replaced the first");
                    // the Bulwark: a blow from in front loses most of its weight
                    ParasiteFx.handle(p, 7, ParasiteFx.F_BULWARK);
                    ParasiteFx.toggleBulwark(p);
                    if (!ParasiteFx.bulwark(p)) bad.add("the Bulwark didn't come out");
                    p.teleportTo(sl(), home.x, home.y, home.z, 0f, 0f);
                    net.minecraft.world.phys.Vec3 front = new net.minecraft.world.phys.Vec3(home.x, home.y, home.z + 2), back = new net.minecraft.world.phys.Vec3(home.x, home.y, home.z - 2);
                    float ff = ParasiteFx.bulwarkFactor(p, front), fb = ParasiteFx.bulwarkFactor(p, back);
                    if (ff > 0.5f) bad.add("the Bulwark lets " + ff + " of a blow from the front through (Brute)");
                    if (fb < 0.8f || fb >= 1f) bad.add("the Bulwark lets " + fb + " of a blow from behind through");
                    ParasiteFx.toggleBulwark(p);
                    p.setHealth(p.getMaxHealth());
                    ParasiteFx.testFrenzy(p);
                    if (!ParasiteFx.frenzied(p)) bad.add("a full Frenzy didn't start");
                }
                if (t == 22) {   // the hybrids need their parents; the Whip-Spear comes out once evolved
                    ParasiteFx.testReset(p); ParasiteFx.testFill(p);
                    ParasiteFx.handle(p, 7, ParasiteFx.F_WHIP);
                    if (ParasiteFx.rank(p, ParasiteFx.F_WHIP) != 0) bad.add("the Whip-Spear evolved without the Bone Lance");
                    ParasiteFx.handle(p, 7, ParasiteFx.F_LANCE);
                    ParasiteFx.handle(p, 7, ParasiteFx.F_WHIP);
                    if (ParasiteFx.rank(p, ParasiteFx.F_WHIP) != 1) bad.add("the Whip-Spear didn't evolve after the Bone Lance");
                    ParasiteFx.setForm(p, ParasiteFx.WHIP, false);
                    if (ParasiteFx.form(p) != ParasiteFx.WHIP) bad.add("the Whip-Spear didn't come out");
                    ParasiteFx.setForm(p, ParasiteFx.NONE, false);
                    // an ultimate's path, for good
                    ParasiteFx.handle(p, 7, ParasiteFx.U_STORM + 200);
                    ParasiteFx.handle(p, 7, ParasiteFx.U_STORM + 100);
                    if (ParasiteFx.path(p, ParasiteFx.U_STORM) != 2) bad.add("Spine Storm's path is " + ParasiteFx.path(p, ParasiteFx.U_STORM) + ", not Impaler");
                    // mutations: wearing Thick Gut lets biomass past 100; a fourth won't fit
                    ParasiteFx.testMutations(p);
                    for (int m : new int[]{ParasiteFx.THICK, ParasiteFx.ACID, ParasiteFx.ECHO, ParasiteFx.KEEN}) ParasiteFx.handle(p, 10, m);
                    if (!ParasiteFx.wears(p, ParasiteFx.THICK) || ParasiteFx.wears(p, ParasiteFx.KEEN)) bad.add("mutation slots wrong: " + java.util.Arrays.toString(ParasiteFx.slots(p)));
                    ParasiteFx.feed(p, 40);
                    if (ParasiteFx.biomass(p) <= 100.5f) bad.add("Thick Gut didn't hold more than 100 (" + ParasiteFx.biomass(p) + ")");
                    // the awakening: everything evolved and a strain settled
                    ParasiteFx.testUnlockAll(p); ParasiteFx.testStrain(p, ParasiteFx.BRUTE); ParasiteFx.testCooldowns(p);
                    if (!ParasiteFx.awakened(p)) bad.add("not awakened with every node evolved");
                    ParasiteMoves.strainArt(p);
                    if (!ParasiteFx.colossus(p)) bad.add("Colossus didn't start");
                    ParasiteFx.testFill(p);
                    ParasiteMoves.finalFormStart(p);
                    if (!ParasiteFx.finalForm(p)) bad.add("the Final Form didn't start");
                    if (ParasiteFx.biomass(p) > 50.5f) bad.add("the Final Form cost only " + (150 - ParasiteFx.biomass(p)) + " biomass");
                    ParasiteFx.feed(p, 500);
                    if (ParasiteFx.frenzied(p)) bad.add("the Final Form let a Frenzy start");
                    ParasiteFx.testReset(p);
                    // starving weakens
                    ParasiteFx.testStarve(p);
                    var dmg = p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
                    if (dmg == null || dmg.getModifiers().stream().noneMatch(m -> m.getName().equals("Hunger") && m.getAmount() < 0)) bad.add("starving didn't weaken");
                    // organs and the second strain: an Iron Heart and a grafted Brute add health; a Double Stomach feeds more
                    ParasiteFx.testReset(p);
                    double hp0 = p.getMaxHealth();
                    ParasiteFx.testFill(p); ParasiteFx.handle(p, 17, 0 * 10 + 1);   // Iron Heart (4 EP)
                    if (ParasiteFx.organ(p, ParasiteFx.HEART) != 1) bad.add("the Iron Heart didn't grow");
                    if (p.getMaxHealth() < hp0 + 3.9) bad.add(String.format("the Iron Heart didn't add health (%.1f -> %.1f)", hp0, p.getMaxHealth()));
                    ParasiteFx.testUnlockAll(p); ParasiteFx.testStrain(p, ParasiteFx.HUNTER);
                    ParasiteFx.handle(p, 16, ParasiteFx.BRUTE);
                    if (ParasiteFx.strain2(p) != ParasiteFx.BRUTE) bad.add("the Brute strain didn't graft");
                    if (p.getMaxHealth() < hp0 + 6.9) bad.add(String.format("a grafted Brute didn't add half its health (%.1f -> %.1f)", hp0, p.getMaxHealth()));
                    ParasiteFx.root(p).putFloat("bio", 0);
                    ParasiteFx.handle(p, 17, 1 * 10 + 1);   // Double Stomach
                    ParasiteFx.feed(p, 10);
                    if (ParasiteFx.biomass(p) < 13.9f) bad.add("the Double Stomach didn't feed more (10 -> " + ParasiteFx.biomass(p) + ")");
                    // adaptation: fire hurts less the more of it you've taken
                    ParasiteFx.testReset(p); ParasiteFx.testUnlockAll(p);
                    p.setHealth(p.getMaxHealth());
                    float h0 = p.getHealth(); p.invulnerableTime = 0; p.hurt(p.damageSources().onFire(), 10f); float first = h0 - p.getHealth();
                    for (int k = 0; k < 4; k++) { p.setHealth(p.getMaxHealth()); p.invulnerableTime = 0; p.hurt(p.damageSources().onFire(), 10f); }
                    p.setHealth(p.getMaxHealth()); h0 = p.getHealth(); p.invulnerableTime = 0; p.hurt(p.damageSources().onFire(), 10f); float later = h0 - p.getHealth();
                    if (ParasiteFx.adapted(p, 0) < 30) bad.add("adaptation to fire is only " + ParasiteFx.adapted(p, 0) + "%");
                    else if (first > 0 && later >= first * 0.8f) bad.add(String.format("fire didn't hurt less after adapting (%.1f -> %.1f)", first, later));
                    p.clearFire();
                    // the tendril yanks a light foe in; burrowing hides and then erupts
                    var light = net.minecraft.world.entity.EntityType.ZOMBIE.create(sl());
                    p.teleportTo(sl(), home.x, home.y, home.z, 0f, 0f);
                    if (light != null) {
                        light.moveTo(home.x, home.y, home.z + 8, 180, 0); light.setNoAi(true); sl().addFreshEntity(light);
                        ParasiteMoves.grapple(p);
                        if (light.getDeltaMovement().z > -0.3) bad.add(String.format("the tendril didn't yank the zombie in (dz %.2f)", light.getDeltaMovement().z));
                        light.discard();
                    }
                    ParasiteMoves.burrow(p);
                    if (!ParasiteFx.burrowed(p)) bad.add("Burrow didn't start (on the ground: " + p.onGround() + ")");
                    ParasiteMoves.burrow(p);
                    if (ParasiteFx.burrowed(p)) bad.add("Burrow didn't end when pressed again");
                    // ranks: by everything eaten - a rank adds health
                    ParasiteFx.testReset(p);
                    double hpR = p.getMaxHealth();
                    ParasiteFx.testEaten(p, 1600);
                    if (ParasiteFx.evoRank(p) != 2) bad.add("1600 eaten should be Predator, is rank " + ParasiteFx.evoRank(p));
                    if (p.getMaxHealth() < hpR + 1.9) bad.add(String.format("ranks didn't add health (%.1f -> %.1f)", hpR, p.getMaxHealth()));
                    // regenerating limbs: the Bulwark worn through tears off, then regrows
                    ParasiteFx.testReset(p); ParasiteFx.testUnlockAll(p); ParasiteFx.testFill(p);
                    p.teleportTo(sl(), home.x, home.y, home.z, 0f, 0f);
                    ParasiteFx.toggleBulwark(p);
                    ParasiteFx.wear(p, 31f);
                    if (ParasiteFx.bulwark(p) || !ParasiteFx.limbGone(p)) bad.add("a worn-through Bulwark didn't tear off");
                    // the nest: grows where you stand, takes the spill past a full gut
                    ParasiteFx.testReset(p); ParasiteFx.testFill(p);
                    ParasiteFx.handle(p, 19, 0);
                    var nest = ParasiteNest.nestOf(p);
                    if (nest == null) bad.add("no Hive Nest grew (on the ground: " + p.onGround() + ")");
                    else {
                        ParasiteFx.root(p).putFloat("bio", 100);
                        ParasiteFx.feed(p, 20);
                        if (nest.stored() <= 0) bad.add("the nest didn't keep the spill past a full gut");
                        if (ParasiteFx.frenzy(p) > 0) bad.add("the spill went into Frenzy with a nest out");
                        nest.discard();
                    }
                    // Predator Sight toggles (with the node); the looks change the hide's armour
                    ParasiteFx.testReset(p); ParasiteFx.testUnlockAll(p); ParasiteFx.testFill(p);
                    ParasiteFx.handle(p, 27, 0);
                    if (!ParasiteFx.sight(p)) bad.add("Predator Sight didn't switch on");
                    ParasiteFx.handle(p, 27, 0);
                    ParasiteFx.toggleHide(p);
                    var armor = p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR);
                    double a0 = armor == null ? 0 : armor.getValue();
                    ParasiteFx.handle(p, 29, 3);   // Plated
                    if (armor != null && armor.getValue() < a0 + 3.9) bad.add(String.format("the Plated hide didn't add armour (%.1f -> %.1f)", a0, armor.getValue()));
                    // Molting: a blow to under 30% sheds a husk
                    ParasiteFx.testReset(p); ParasiteFx.testUnlockAll(p);
                    p.setHealth(p.getMaxHealth() * 0.2f);
                    ParasiteMoves.molt(p);
                    var husks = sl().getEntitiesOfClass(com.ironspellgrimoire.entity.MoltHusk.class, p.getBoundingBox().inflate(4));
                    if (husks.isEmpty()) bad.add("Molting shed no husk");
                    for (var h : husks) h.discard();
                    p.setHealth(p.getMaxHealth());
                    // Pack Call: three brutes in the Brute strain's shape, none of them after their master
                    ParasiteFx.testReset(p); ParasiteFx.testStrain(p, ParasiteFx.BRUTE); ParasiteFx.testFill(p);
                    ParasiteMoves.packCall(p, 0f);
                    var pack = sl().getEntitiesOfClass(com.ironspellgrimoire.entity.InfectedBrute.class, p.getBoundingBox().inflate(8));
                    if (pack.size() != 3) bad.add("Pack Call raised " + pack.size() + " brutes, not 3");
                    for (var b : pack) {
                        if (b.kind() != ParasiteFx.BRUTE) bad.add("a brute wasn't shaped by the Brute strain (" + b.kind() + ")");
                        if (b.canAttack(p)) bad.add("a brute could attack its master");
                        b.discard();
                    }
                    // Hive Network: three nests at once
                    ParasiteFx.testReset(p); ParasiteFx.testUnlockAll(p);
                    for (int k = 0; k < 3; k++) { ParasiteFx.testFill(p); ParasiteFx.handle(p, 19, 0); }
                    if (ParasiteNest.nests(p).size() != 3) bad.add("Hive Network holds " + ParasiteNest.nests(p).size() + " nests, not 3");
                    for (var n : sl().getEntitiesOfClass(com.ironspellgrimoire.entity.HiveNest.class, p.getBoundingBox().inflate(8))) n.discard();
                    ParasiteFx.testReset(p);
                    ParasiteFx.testFrenzy(p);   // back into the frenzy the next checks wait out
                }
                if (t > 20 && t < 290) p.setHealth(p.getMaxHealth());
                if (t == 286) {   // the quick ring's pins; the Apex Mutation; the lessons
                    ParasiteFx.handle(p, 32, 2 * 10 + ParasiteFx.LANCE);
                    if (ParasiteExtras.pins(p)[2] != ParasiteFx.LANCE) bad.add("pinning the Bone Lance didn't take");
                    ParasiteFx.handle(p, 32, 0 * 10 + ParasiteFx.LANCE);
                    int lances = 0;
                    for (int f : ParasiteExtras.pins(p)) if (f == ParasiteFx.LANCE) lances++;
                    if (lances != 1) bad.add("one form pinned " + lances + " times");
                    ParasiteExtras.testApex(p, 0);
                    ParasiteFx.testEaten(p, 0);
                    ParasiteFx.handle(p, 31, 1);
                    if (ParasiteExtras.apex(p) != 0) bad.add("an Apex Mutation was chosen below the Sovereign rank");
                    ParasiteFx.testEaten(p, ParasiteFx.RANK_AT[ParasiteFx.RANK_AT.length - 1]);
                    ParasiteFx.handle(p, 31, 1);
                    if (ParasiteExtras.apex(p) != 1) bad.add("couldn't choose Hydra Heart at Sovereign");
                    ParasiteFx.handle(p, 31, 2);
                    if (ParasiteExtras.apex(p) != 1) bad.add("the Apex Mutation could be changed");
                    // Hydra Heart: a killing blow leaves you standing
                    p.setHealth(p.getMaxHealth());
                    p.invulnerableTime = 0;
                    p.hurt(p.damageSources().genericKill(), 1000f);
                    if (!p.isAlive() || p.getHealth() < p.getMaxHealth() * 0.3f) bad.add("Hydra Heart didn't cheat death (health " + p.getHealth() + ")");
                    p.setHealth(p.getMaxHealth());
                    // Living Armory: the last form moves to the other arm
                    ParasiteExtras.testApex(p, 2);
                    ParasiteFx.setForm(p, ParasiteFx.REAPER, true);
                    ParasiteFx.setForm(p, ParasiteFx.TITAN, true);
                    if (ParasiteExtras.form2(p) != ParasiteFx.REAPER) bad.add("Living Armory put form " + ParasiteExtras.form2(p) + " on the other arm, not the Reaper Edge");
                    ParasiteFx.setForm(p, ParasiteFx.NONE, true);
                    if (ParasiteExtras.form2(p) != ParasiteFx.NONE) bad.add("retracting left a form on the other arm");
                    ParasiteExtras.testApex(p, 0);
                    int taught = ParasiteExtras.testTaught(p);
                    if ((taught & 1) == 0) bad.add("the parasite taught nothing");
                }
                if (t == 290) {
                    if (ParasiteFx.frenzied(p)) bad.add("the frenzy never ended");
                    if (ParasiteFx.biomass(p) > 50.5f) bad.add("biomass after the frenzy is " + ParasiteFx.biomass(p));
                    ParasiteFx.setForm(p, ParasiteFx.TITAN, false);
                    top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(p).ifPresent(h -> h.setEquippedCurio("spellbook", 0, ItemStack.EMPTY));
                }
                if (t == 294) {
                    if (ParasiteFx.form(p) != ParasiteFx.NONE) bad.add("the arm form stayed out after the book was put away");
                    var dmg = p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
                    if (dmg != null && dmg.getModifiers().stream().anyMatch(m -> m.getName().equals("Parasite arm"))) bad.add("the arm's damage stayed after the book was put away");
                    if (p.getMaxHealth() > baseHp + 0.1) bad.add("Brute's health stayed after the book was put away");
                }
            } catch (Throwable x) { bad.add("threw " + x); }
            if (t < 296) return false;
            String ch = changes(before);
            if (!ch.isEmpty()) bad.add("changed the world: " + ch);
            ParasiteFx.testReset(p);
            if (bad.isEmpty()) pass(name, "devour, forms, evolution, strain, bulwark, frenzy");
            else fail(name, String.join("; ", bad));
            return true;
        }
    }

    private static final class HunterTest extends Test {
        HunterTest() { super("shadow: hunter's ascension"); }
        @Override Class<?>[] roots() { return new Class<?>[]{HunterAscension.class, ShadowArmy.class}; }
        final java.util.List<String> bad = new java.util.ArrayList<>();
        BlockState[] before;
        double baseHp;
        final java.util.Map<String, Float> knifeHp = new java.util.HashMap<>();
        @Override void start() {
            cancelCast(); clearArea(); resetPlayer(true); ensureDummy();
            ItemStack book = new ItemStack(com.ironspellgrimoire.registry.AddonItemRegistry.SHADOW_GRIMOIRE.get());
            top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(p).ifPresent(h -> h.setEquippedCurio("spellbook", 0, book));
            HunterAscension.testReset(p);
            before = snapshot();
        }
        @Override boolean tick(int t) {
            p.setHealth(p.getMaxHealth());
            try {
                if (t == 2) {
                    HunterAscension.start(p);
                    baseHp = p.getMaxHealth();
                    io.redspace.ironsspellbooks.api.magic.MagicData.getPlayerMagicData(p).setMana(0);
                    HunterAscension.gainXp(p, HunterAscension.xpNext(1) + HunterAscension.xpNext(2));
                    if (HunterAscension.level(p) != 3) bad.add("two levels of XP gave level " + HunterAscension.level(p));
                    if (io.redspace.ironsspellbooks.api.magic.MagicData.getPlayerMagicData(p).getMana() < 50) bad.add("the Level-Up Surge didn't fill mana");
                    var surge = p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
                    if (surge == null || surge.getModifiers().stream().noneMatch(m -> m.getName().equals("Level-Up Surge"))) bad.add("no Level-Up Surge damage boost");
                    int pts = HunterAscension.d(p).getInt(HunterAscension.PTS);
                    if (pts != 6) bad.add("expected 6 points, had " + pts);
                    for (int i = 0; i < 4; i++) HunterAscension.handle(p, 11, 2);   // four into Vitality
                    if (HunterAscension.stat(p, 2) != 4) bad.add("Vitality is " + HunterAscension.stat(p, 2) + " after spending 4");
                }
                if (t == 30 && p.getMaxHealth() < baseHp + 1.9) bad.add(String.format("Vitality didn't raise max health (%.1f -> %.1f)", baseHp, p.getMaxHealth()));
                if (t == 32) {
                    for (int k = 0; k < 4; k++) HunterAscension.progress(p, k, 100000);
                    if (!HunterAscension.d(p).getBoolean(HunterAscension.DONE)) bad.add("the Daily Trial didn't complete");
                    int m = HunterAscension.marks(p);
                    HunterAscension.handle(p, 13, 1);   // a Mana Tonic
                    if (HunterAscension.marks(p) != m - 40) bad.add("buying a tonic took " + (m - HunterAscension.marks(p)) + " marks");
                    if (!p.getInventory().contains(new ItemStack(com.ironspellgrimoire.registry.AddonItemRegistry.MANA_TONIC.get()))) bad.add("no Mana Tonic in the inventory");
                    HunterAscension.startReckoning(p);
                }
                if (t == 100) {   // the knives' moves: each must hurt the dummy
                    for (String k : new String[]{"thousand_cuts", "wyrmfang_lunge", "phantom_edges", "thornbite", "crimson_rend", "crimson_fracture", "sovereigns_verdict"}) {
                        ensureDummy();
                        p.teleportTo(sl(), home.x, home.y, home.z, 0f, 0f);
                        float hp0 = dummy.getHealth();
                        boolean did = switch (k) {
                            case "thousand_cuts" -> ShadowFx.thousandCuts(p);
                            case "wyrmfang_lunge" -> ShadowFx.wyrmfangLunge(p);
                            case "thornbite" -> ShadowFx.thornbite(p);
                            case "crimson_rend" -> ShadowFx.crimsonRend(p);
                            case "crimson_fracture" -> ShadowFx.crimsonFracture(p);
                            case "sovereigns_verdict" -> ShadowFx.sovereignsVerdict(p);
                            default -> ShadowFx.phantomEdges(p);
                        };
                        if (!did) bad.add(k + " did nothing");
                        knifeHp.put(k, hp0);
                    }
                }
                if (t == 140) {
                    if (dummy != null && dummy.getHealth() >= 1000) bad.add("the knives' moves did no damage");
                    int n = HunterAscension.testEndReckoning(p, true);
                    if (n <= 0) bad.add("the Reckoning raised no mobs (" + n + ")");
                    else if (!sl().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, p.getBoundingBox().inflate(32), m -> m.getPersistentData().getBoolean("isg_reckoning")).isEmpty())
                        bad.add("the Reckoning's mobs were left behind");
                }
                if (t == 142) {   // Job Change: to level 40, the trial, passing it, choosing the Warlord
                    baseHp = p.getMaxHealth();
                    HunterAscension.testGrant(p, Math.max(0, HunterAscension.JOB_LEVEL - HunterAscension.level(p)), 0);
                    if (HunterAscension.level(p) < HunterAscension.JOB_LEVEL) bad.add("couldn't reach level " + HunterAscension.JOB_LEVEL);
                    HunterAscension.handle(p, 15, 3);
                    if (HunterAscension.job(p) != 0) bad.add("chose a job without passing the trial");
                    HunterAscension.handle(p, 14, 0);
                    if (!HunterAscension.testInTrial(p)) bad.add("the Job Change Trial didn't start");
                    HunterAscension.testEndReckoning(p, true);
                    if (!HunterAscension.d(p).getBoolean(HunterAscension.JOB_READY)) bad.add("passing the trial didn't let you choose");
                    HunterAscension.handle(p, 15, 3);
                    if (HunterAscension.job(p) != 3) bad.add("choosing the Warlord gave job " + HunterAscension.job(p));
                    HunterAscension.handle(p, 15, 1);
                    if (HunterAscension.job(p) != 3) bad.add("the job could be changed again");
                }
                if (t == 146 && p.getMaxHealth() < baseHp + 7.9) bad.add(String.format("the Warlord's +4 hearts weren't applied (%.1f -> %.1f)", baseHp, p.getMaxHealth()));
                if (t == 148) {   // Job Advancement: to level 80, the trial, the Conqueror
                    baseHp = p.getMaxHealth();
                    HunterAscension.handle(p, 20, 0);
                    if (HunterAscension.testInTrial(p)) bad.add("the Advancement Trial started below level 80");
                    HunterAscension.testGrant(p, Math.max(0, HunterAscension.ADV_LEVEL - HunterAscension.level(p)), 0);
                    HunterAscension.handle(p, 20, 0);
                    if (!HunterAscension.testInTrial(p)) bad.add("the Advancement Trial didn't start at level 80");
                    HunterAscension.testEndAdv(p, true);
                    if (!HunterAscension.advanced(p)) bad.add("passing the Advancement Trial didn't advance the job");
                    if ((HunterAscension.titles(p) & (1 << 4)) == 0) bad.add("advancing didn't earn the Ascendant title");
                    if ((HunterAscension.titles(p) & (1 << 3)) == 0) bad.add("passing the Job Change Trial didn't earn Trialbreaker");
                }
                if (t == 152) {
                    if (p.getMaxHealth() < baseHp + 3.9) bad.add(String.format("the Conqueror's +2 hearts weren't applied (%.1f -> %.1f)", baseHp, p.getMaxHealth()));
                    // a title worn: its passive on, and off again
                    var arm = p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR);
                    double a0 = arm.getValue();
                    HunterAscension.testTitles(p, 1 << 1, -1);
                    HunterAscension.handle(p, 19, 1);
                    if (arm.getValue() < a0 + 1.9) bad.add(String.format("wearing Unbroken gave %.1f armour, not +2", arm.getValue() - a0));
                    HunterAscension.handle(p, 19, 1);
                    if (arm.getValue() > a0 + 0.1) bad.add("taking Unbroken off left its armour");
                    HunterAscension.handle(p, 19, 2);
                    if (HunterAscension.title(p) == 2) bad.add("wore a title not earned");
                    // the System's lessons
                    int taught = HunterAscension.testTaught(p);
                    for (int bit : new int[]{0, 1, 2}) if ((taught & (1 << bit)) == 0) bad.add("the System never taught lesson " + bit);
                    // loadouts: save one, change who's equipped, apply it back
                    ShadowArmy.list(p).clear();
                    for (int k = 0; k < 3; k++) ShadowArmy.add(p, "minecraft:zombie", false);
                    ShadowArmy.handle(p, 16, 0, "");
                    for (net.minecraft.nbt.Tag tg : ShadowArmy.list(p)) ((net.minecraft.nbt.CompoundTag) tg).putBoolean("eq", false);
                    ShadowArmy.handle(p, 17, 0, "");
                    int eq = 0;
                    for (net.minecraft.nbt.Tag tg : ShadowArmy.list(p)) if (((net.minecraft.nbt.CompoundTag) tg).getBoolean("eq")) eq++;
                    if (eq != 3) bad.add("applying a saved loadout equipped " + eq + " of 3");
                    ShadowArmy.handle(p, 18, 0, "Vanguard");
                    if (!"Vanguard".equals(ShadowArmy.loadouts(p).getCompound(0).getString("name"))) bad.add("renaming a loadout didn't take");
                }
                if (t == 156) {   // a bond: 50 kills with a shadow at your side
                    var out = ShadowArmy.out(p);
                    if (out.isEmpty()) bad.add("no shadow out to bond");
                    else {
                        var e0 = ShadowArmy.find(p, out.get(0).armyId());
                        for (int k = 0; k < ShadowArmy.BOND_KILLS; k++) ShadowArmy.bondKill(p);
                        if (e0 == null || e0.getInt("trait") < 1) bad.add("50 kills side by side gave no trait");
                    }
                }
            } catch (Throwable x) { bad.add("threw " + x); }
            if (t < 160) return false;
            String ch = changes(before);
            if (!ch.isEmpty()) bad.add("changed the world: " + ch);
            ShadowArmy.recallAll(p); ShadowArmy.list(p).clear();
            if (bad.isEmpty()) pass(name, "level " + HunterAscension.level(p) + ", stats applied, trial done, market and Reckoning work, level-up surge, job change, advancement, titles, lessons, loadouts, bonds");
            else fail(name, String.join("; ", bad));
            HunterAscension.testReset(p);
            clearArea();
            return true;
        }
    }

    private static final class PathTest extends Test {
        PathTest() { super("blood: evolution paths"); }
        @Override Class<?>[] roots() { return new Class<?>[]{BloodFx.class, BloodPaths.class}; }
        final String[] spells = {"crimson_edge", "hemorrhage", "sanguine_volley", "transfusion", "blood_pact", "red_tide", "thorned_veins", "crimson_bulwark", "exsanguinate"};
        BlockState[] before; final java.util.List<String> bad = new java.util.ArrayList<>();
        float dealt;
        @Override void start() {
            cancelCast(); clearArea(); resetPlayer(true); ensureDummy();
            ItemStack book = new ItemStack(com.ironspellgrimoire.registry.AddonItemRegistry.BLOOD_GRIMOIRE.get());
            CompoundTag mt = new CompoundTag(); mt.putInt("xp", 10_000_000);
            book.getOrCreateTag().put(GrimoireMastery.masteryTag(), mt);
            top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(p).ifPresent(h -> h.setEquippedCurio("spellbook", 0, book));
            Moves.ends(p, BloodFx.MODE);
            BloodFx.setGauge(p, 0);
            before = snapshot();
        }
        float last = -1;
        @Override boolean tick(int t) {
            p.setHealth(p.getMaxHealth());
            if (t % 22 == 1) {
                int i = t / 22;
                if (i < spells.length * 2) {
                    String sp = spells[i % spells.length];
                    BloodPaths.choose(p, sp, i < spells.length ? 1 : 2);
                    if (BloodPaths.path(p, sp) != (i < spells.length ? 1 : 2)) bad.add(sp + ": the path didn't take");
                    if (dummy != null && last > 0) dealt += last - dummy.getHealth();   // what the last cast did, all of it
                    ensureDummy();
                    p.teleportTo(sl(), home.x, home.y, home.z, 0f, 0f);
                    last = dummy.getHealth();
                    try {
                        switch (sp) {
                            case "crimson_edge" -> BloodFx.crimsonEdge(p, 10f);
                            case "hemorrhage" -> BloodFx.hemorrhage(p, 10f);
                            case "sanguine_volley" -> BloodFx.sanguineVolley(p, 10f);
                            case "transfusion" -> BloodFx.transfusion(p, 10f);
                            case "blood_pact" -> BloodFx.bloodPact(p, 10f);
                            case "red_tide" -> BloodFx.redTide(p, 10f);
                            case "thorned_veins" -> BloodFx.thornedVeins(p, 10f);
                            case "crimson_bulwark" -> BloodFx.crimsonBulwark(p, 10f);
                            default -> BloodFx.exsanguinate(p, 10f);
                        }
                    } catch (Throwable x) { bad.add(sp + " (path " + (i < spells.length ? 1 : 2) + ") threw " + x); }
                }
            }
            if (t < spells.length * 2 * 22 + 30) return false;
            if (dummy != null && last > 0) dealt += last - dummy.getHealth();
            if (dealt <= 0) bad.add("no path did any damage");
            String ch = changes(before);
            if (!ch.isEmpty()) bad.add("changed the world: " + ch);
            if (BloodFx.gauge(p) <= 0) bad.add("the Blood Gauge never filled");
            if (bad.isEmpty()) pass(name, String.format("18 casts, %.0f damage, gauge %.0f", dealt, BloodFx.gauge(p)));
            else fail(name, String.join("; ", bad));
            BloodFx.setGauge(p, 0);
            return true;
        }
    }

    private static final class BossTest extends Test {
        final Supplier<? extends EntityType<?>> type;
        Mob boss; BlockState[] before; int fx0, hits0, maxPhase = -1; double moved; Vec3 last; float maxHp, lowHp; boolean oneShotOk = true; String err;
        BossTest(String n, Supplier<? extends EntityType<?>> type) { super("boss " + n); this.type = type; }
        @Override Class<?>[] roots() {
            Entity e = type.get().create(sl());
            if (e == null) return null;
            Class<?> c = e.getClass();
            e.discard();
            return new Class<?>[]{c};
        }
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
    private static Test check(String name, Runnable r, Class<?>... roots) {
        return new Test(name) {
            @Override Class<?>[] roots() { return roots.length == 0 ? null : roots; }
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
        }, Boards.class, Bounties.class, Quests.class);
    }

    private static Test exam() {
        return new Test("rift: Magic Knight Exam") {
            @Override Class<?>[] roots() { return new Class<?>[]{Rifts.class}; }
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
            @Override Class<?>[] roots() { return new Class<?>[]{ElfRaids.class}; }
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
                    p.teleportTo(home.x, home.y, home.z);
                    elvesSeen = any;
                }
                if (t % 40 == 0) {
                    BlockPos c = BlockPos.containing(home);
                    for (BlockPos q : BlockPos.betweenClosed(c.offset(-12, -3, -12), c.offset(12, 4, 12))) {
                        BlockEntity be = sl().getBlockEntity(q);
                        if (be instanceof ChestBlockEntity ch && ch.getCustomName() != null && ch.getCustomName().getString().contains("Spoils")) {
                            if (waves >= 4) pass(name, waves + " wave(s) beaten, spoils chest at " + q.getX() + "," + q.getY() + "," + q.getZ());
                            else fail(name, "the spoils chest came after only " + waves + " wave(s) of elves were seen (did they spawn?)");
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
        }, Rifts.class, GrimoireMastery.class, Bounties.class, Quests.class);
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
        cache = new ClassKey.Cache(net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("isg_test_cache.txt"));
        p.server.setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);   // Peaceful would delete every boss and elf at once
        sl().setDayTime(6000);
        // a creeper wandering in (or an enderman) would "change the world" in some boss's test: no griefing, always noon
        sl().getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING).set(false, p.server);
        sl().getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING).set(false, p.server);   // no wild slime wandering in front of a spell
        sl().getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false, p.server);
        UiShotsServer.prepare(p);
        queue.add(saving());
        queue.add(boards());
        for (var e : AddonSpellRegistry.SPELLS.getEntries()) queue.add(new SpellTest(e.get()));
        queue.add(new IceTest());
        queue.add(new PathTest());
        queue.add(new BookMovesTest("gravity", com.ironspellgrimoire.registry.AddonItemRegistry.GRAVITY_GRIMOIRE::get, GravityFx.class,
                "gravity_bullet", (java.util.function.BiConsumer<ServerPlayer, Float>) GravityFx::gravityBullet, "",
                "weightless_step", (java.util.function.BiConsumer<ServerPlayer, Float>) GravityFx::weightlessStep, "c",
                "crushing_field", (java.util.function.BiConsumer<ServerPlayer, Float>) GravityFx::crushingField, "",
                "repulse", (java.util.function.BiConsumer<ServerPlayer, Float>) GravityFx::repulse, "c",
                "gravity_well", (java.util.function.BiConsumer<ServerPlayer, Float>) GravityFx::gravityWell, "",
                "gravity_lift", (java.util.function.BiConsumer<ServerPlayer, Float>) GravityFx::gravityLift, "",
                "orbiting_mass", (java.util.function.BiConsumer<ServerPlayer, Float>) GravityFx::orbitingMass, "",
                "heavy_descent", (java.util.function.BiConsumer<ServerPlayer, Float>) GravityFx::heavyDescent, "",
                "mass_domain", (java.util.function.BiConsumer<ServerPlayer, Float>) GravityFx::massDomain, "q",
                "starfall_collapse", (java.util.function.BiConsumer<ServerPlayer, Float>) GravityFx::starfallCollapse, ""));
        queue.add(new BookMovesTest("sound", com.ironspellgrimoire.registry.AddonItemRegistry.SOUND_GRIMOIRE::get, SoundFx.class,
                "sonic_pulse", (java.util.function.BiConsumer<ServerPlayer, Float>) SoundFx::sonicPulse, "",
                "echo_step", (java.util.function.BiConsumer<ServerPlayer, Float>) SoundFx::echoStep, "",
                "tuning_strike", (java.util.function.BiConsumer<ServerPlayer, Float>) SoundFx::tuningStrike, "",
                "sound_barrier", (java.util.function.BiConsumer<ServerPlayer, Float>) SoundFx::soundBarrier, "c",
                "silence_field", (java.util.function.BiConsumer<ServerPlayer, Float>) SoundFx::silenceField, "",
                "thunderclap", (java.util.function.BiConsumer<ServerPlayer, Float>) SoundFx::thunderclap, "c",
                "sonic_lance", (java.util.function.BiConsumer<ServerPlayer, Float>) SoundFx::sonicLance, "",
                "echo_chamber", (java.util.function.BiConsumer<ServerPlayer, Float>) SoundFx::echoChamber, "",
                "crescendo", (java.util.function.BiConsumer<ServerPlayer, Float>) SoundFx::crescendoMode, "q",
                "final_chord", (java.util.function.BiConsumer<ServerPlayer, Float>) SoundFx::finalChord, ""));
        queue.add(new BookMovesTest("ink", com.ironspellgrimoire.registry.AddonItemRegistry.INK_GRIMOIRE::get, InkFx.class,
                "brush_stroke", (java.util.function.BiConsumer<ServerPlayer, Float>) InkFx::brushStroke, "c",
                "ink_splash", (java.util.function.BiConsumer<ServerPlayer, Float>) InkFx::inkSplash, "",
                "ink_serpent", (java.util.function.BiConsumer<ServerPlayer, Float>) InkFx::inkSerpent, "",
                "ink_blot", (java.util.function.BiConsumer<ServerPlayer, Float>) InkFx::inkBlot, "",
                "seal_script", (java.util.function.BiConsumer<ServerPlayer, Float>) InkFx::sealScript, "",
                "ink_beasts", (java.util.function.BiConsumer<ServerPlayer, Float>) InkFx::inkBeasts, "",
                "ink_rain", (java.util.function.BiConsumer<ServerPlayer, Float>) InkFx::inkRain, "",
                "ink_wave", (java.util.function.BiConsumer<ServerPlayer, Float>) InkFx::inkWave, "",
                "masterpiece", (java.util.function.BiConsumer<ServerPlayer, Float>) InkFx::masterpieceMode, "q",
                "grand_calligraphy", (java.util.function.BiConsumer<ServerPlayer, Float>) InkFx::grandCalligraphy, ""));
        queue.add(new BookMovesTest("error", com.ironspellgrimoire.registry.AddonItemRegistry.ERROR_GRIMOIRE::get, ErrorFx.class,
                "glitch_bolt", (java.util.function.BiConsumer<ServerPlayer, Float>) ErrorFx::glitchBolt, "",
                "frame_skip", (java.util.function.BiConsumer<ServerPlayer, Float>) ErrorFx::frameSkip, "",
                "corrupted_threads", (java.util.function.BiConsumer<ServerPlayer, Float>) ErrorFx::corruptedThreads, "",
                "lag_spike", (java.util.function.BiConsumer<ServerPlayer, Float>) ErrorFx::lagSpike, "",
                "copy_paste", (java.util.function.BiConsumer<ServerPlayer, Float>) ErrorFx::copyPaste, "",
                "crash_zone", (java.util.function.BiConsumer<ServerPlayer, Float>) ErrorFx::crashZone, "",
                "error_windows", (java.util.function.BiConsumer<ServerPlayer, Float>) ErrorFx::errorWindows, "",
                "blue_screen", (java.util.function.BiConsumer<ServerPlayer, Float>) ErrorFx::blueScreen, "",
                "fatal_error", (java.util.function.BiConsumer<ServerPlayer, Float>) ErrorFx::fatalError, "q",
                "system_crash", (java.util.function.BiConsumer<ServerPlayer, Float>) ErrorFx::systemCrash, ""));
        queue.add(new BookMovesTest("treasure", com.ironspellgrimoire.registry.AddonItemRegistry.TREASURE_GRIMOIRE::get, TreasureFx.class,
                "vault_volley", (java.util.function.BiConsumer<ServerPlayer, Float>) TreasureFx::vaultVolley, "",
                "gilded_spear", (java.util.function.BiConsumer<ServerPlayer, Float>) TreasureFx::gildedSpear, "",
                "vault_chains", (java.util.function.BiConsumer<ServerPlayer, Float>) TreasureFx::vaultChains, "",
                "blade_wall", (java.util.function.BiConsumer<ServerPlayer, Float>) TreasureFx::bladeWall, "c",
                "blade_rain", (java.util.function.BiConsumer<ServerPlayer, Float>) TreasureFx::bladeRain, "",
                "seeking_blades", (java.util.function.BiConsumer<ServerPlayer, Float>) TreasureFx::seekingBlades, "",
                "treasure_barrage", (java.util.function.BiConsumer<ServerPlayer, Float>) TreasureFx::treasureBarrage, "",
                "vault_aegis", (java.util.function.BiConsumer<ServerPlayer, Float>) TreasureFx::vaultAegis, "q",
                "kings_vault", (java.util.function.BiConsumer<ServerPlayer, Float>) TreasureFx::kingsVault, "",
                "vault_breaker", (java.util.function.BiConsumer<ServerPlayer, Float>) TreasureFx::vaultBreaker, ""));
        queue.add(new BookMovesTest("chain", com.ironspellgrimoire.registry.AddonItemRegistry.CHAIN_GRIMOIRE::get, ChainFx.class,
                "chain_lash", (java.util.function.BiConsumer<ServerPlayer, Float>) ChainFx::chainLash, "",
                "binding_chain", (java.util.function.BiConsumer<ServerPlayer, Float>) ChainFx::bindingChain, "",
                "dowsing_chain", (java.util.function.BiConsumer<ServerPlayer, Float>) ChainFx::dowsingChain, "",
                "healing_chain", (java.util.function.BiConsumer<ServerPlayer, Float>) ChainFx::healingChain, "q",
                "judgment_chain", (java.util.function.BiConsumer<ServerPlayer, Float>) ChainFx::judgmentChain, "",
                "stealing_chain", (java.util.function.BiConsumer<ServerPlayer, Float>) ChainFx::stealingChain, "",
                "chain_net", (java.util.function.BiConsumer<ServerPlayer, Float>) ChainFx::chainNet, "",
                "anchor_chain", (java.util.function.BiConsumer<ServerPlayer, Float>) ChainFx::anchorChain, "",
                "restriction", (java.util.function.BiConsumer<ServerPlayer, Float>) ChainFx::restriction, "q",
                "thousand_chains", (java.util.function.BiConsumer<ServerPlayer, Float>) ChainFx::thousandChains, ""));
        queue.add(new BookMovesTest("hunter", com.ironspellgrimoire.registry.AddonItemRegistry.HUNTER_GRIMOIRE::get, HunterFx.class,
                "saw_cleave", (java.util.function.BiConsumer<ServerPlayer, Float>) HunterFx::sawCleave, "c",
                "trick_switch", (java.util.function.BiConsumer<ServerPlayer, Float>) HunterFx::trickSwitch, "c",
                "silver_shot", (java.util.function.BiConsumer<ServerPlayer, Float>) HunterFx::silverShot, "",
                "quickstep", (java.util.function.BiConsumer<ServerPlayer, Float>) HunterFx::quickstep, "q",
                "visceral_strike", (java.util.function.BiConsumer<ServerPlayer, Float>) HunterFx::visceralStrike, "c",
                "hunters_mark", (java.util.function.BiConsumer<ServerPlayer, Float>) HunterFx::huntersMark, "",
                "rending_spin", (java.util.function.BiConsumer<ServerPlayer, Float>) HunterFx::rendingSpin, "c",
                "blood_volley", (java.util.function.BiConsumer<ServerPlayer, Float>) HunterFx::bloodVolley, "",
                "blood_moon_hunt", (java.util.function.BiConsumer<ServerPlayer, Float>) HunterFx::bloodMoonHunt, "q",
                "nightmare_cleave", (java.util.function.BiConsumer<ServerPlayer, Float>) HunterFx::nightmareCleave, ""));
        queue.add(new BookMovesTest("void", com.ironspellgrimoire.registry.AddonItemRegistry.VOID_GRIMOIRE::get, VoidFx.class,
                "erase", (java.util.function.BiConsumer<ServerPlayer, Float>) VoidFx::erase, "",
                "null_step", (java.util.function.BiConsumer<ServerPlayer, Float>) VoidFx::nullStep, "",
                "unmake", (java.util.function.BiConsumer<ServerPlayer, Float>) VoidFx::unmakeSpell, "",
                "nullify", (java.util.function.BiConsumer<ServerPlayer, Float>) VoidFx::nullify, "c",
                "seam", (java.util.function.BiConsumer<ServerPlayer, Float>) VoidFx::seamSpell, "",
                "absence", (java.util.function.BiConsumer<ServerPlayer, Float>) VoidFx::absenceSpell, "q",
                "void_lance", (java.util.function.BiConsumer<ServerPlayer, Float>) VoidFx::voidLance, "",
                "delete", (java.util.function.BiConsumer<ServerPlayer, Float>) VoidFx::deleteSpell, "",
                "emptiness", (java.util.function.BiConsumer<ServerPlayer, Float>) VoidFx::emptinessMode, "q",
                "origin_zero", (java.util.function.BiConsumer<ServerPlayer, Float>) VoidFx::originZero, ""));
        queue.add(new BookMovesTest("gambler", com.ironspellgrimoire.registry.AddonItemRegistry.GAMBLER_GRIMOIRE::get, GamblerFx.class,
                "coin_toss", (java.util.function.BiConsumer<ServerPlayer, Float>) GamblerFx::coinToss, "",
                "double_or_nothing", (java.util.function.BiConsumer<ServerPlayer, Float>) GamblerFx::doubleOrNothing, "q",
                "roulette", (java.util.function.BiConsumer<ServerPlayer, Float>) GamblerFx::roulette, "",
                "chip_shot", (java.util.function.BiConsumer<ServerPlayer, Float>) GamblerFx::chipShot, "",
                "lucky_seven", (java.util.function.BiConsumer<ServerPlayer, Float>) GamblerFx::luckySeven, "",
                "slot_machine", (java.util.function.BiConsumer<ServerPlayer, Float>) GamblerFx::slotMachine, "",
                "loaded_dice", (java.util.function.BiConsumer<ServerPlayer, Float>) GamblerFx::loadedDice, "",
                "insurance", (java.util.function.BiConsumer<ServerPlayer, Float>) GamblerFx::insurance, "q",
                "all_in", (java.util.function.BiConsumer<ServerPlayer, Float>) GamblerFx::allInMode, "q",
                "jackpot", (java.util.function.BiConsumer<ServerPlayer, Float>) GamblerFx::jackpot, ""));
        queue.add(new BookMovesTest("tarot", com.ironspellgrimoire.registry.AddonItemRegistry.TAROT_GRIMOIRE::get, TarotFx.class,
                "draw_card", (java.util.function.BiConsumer<ServerPlayer, Float>) TarotFx::drawCard, "q",
                "the_magician", (java.util.function.BiConsumer<ServerPlayer, Float>) TarotFx::magicianCard, "",
                "the_tower", (java.util.function.BiConsumer<ServerPlayer, Float>) TarotFx::towerCard, "",
                "the_star", (java.util.function.BiConsumer<ServerPlayer, Float>) TarotFx::starCard, "q",
                "death_card", (java.util.function.BiConsumer<ServerPlayer, Float>) TarotFx::deathCard, "c",
                "wheel_of_fortune", (java.util.function.BiConsumer<ServerPlayer, Float>) TarotFx::wheelCard, "cq",
                "the_hanged_man", (java.util.function.BiConsumer<ServerPlayer, Float>) TarotFx::hangedCard, "",
                "three_card_spread", (java.util.function.BiConsumer<ServerPlayer, Float>) TarotFx::threeCardSpread, "q",
                "the_world", (java.util.function.BiConsumer<ServerPlayer, Float>) TarotFx::worldMode, "q",
                "judgement", (java.util.function.BiConsumer<ServerPlayer, Float>) TarotFx::judgementCard, ""));
        queue.add(new BookMovesTest("abyss", com.ironspellgrimoire.registry.AddonItemRegistry.ABYSS_GRIMOIRE::get, AbyssFx.class,
                "crushing_pressure", (java.util.function.BiConsumer<ServerPlayer, Float>) AbyssFx::crushingPressure, "",
                "anglers_lure", (java.util.function.BiConsumer<ServerPlayer, Float>) AbyssFx::anglersLure, "",
                "abyssal_grasp", (java.util.function.BiConsumer<ServerPlayer, Float>) AbyssFx::abyssalGrasp, "",
                "undertow", (java.util.function.BiConsumer<ServerPlayer, Float>) AbyssFx::undertowSpell, "",
                "abyssal_dive", (java.util.function.BiConsumer<ServerPlayer, Float>) AbyssFx::abyssalDive, "",
                "depth_charge", (java.util.function.BiConsumer<ServerPlayer, Float>) AbyssFx::depthCharge, "",
                "leviathan", (java.util.function.BiConsumer<ServerPlayer, Float>) AbyssFx::leviathanSpell, "",
                "hadal_weight", (java.util.function.BiConsumer<ServerPlayer, Float>) AbyssFx::hadalWeight, "",
                "bioluminescence", (java.util.function.BiConsumer<ServerPlayer, Float>) AbyssFx::bioluminescence, "q",
                "the_trench", (java.util.function.BiConsumer<ServerPlayer, Float>) AbyssFx::theTrench, ""));
        queue.add(new BookMovesTest("mech", com.ironspellgrimoire.registry.AddonItemRegistry.MECH_GRIMOIRE::get, MechFx.class,
                "rivet_barrage", (java.util.function.BiConsumer<ServerPlayer, Float>) MechFx::rivetBarrage, "",
                "thruster_dash", (java.util.function.BiConsumer<ServerPlayer, Float>) MechFx::thrusterDash, "",
                "rocket_fist", (java.util.function.BiConsumer<ServerPlayer, Float>) MechFx::rocketFist, "",
                "piston_stomp", (java.util.function.BiConsumer<ServerPlayer, Float>) MechFx::pistonStomp, "c",
                "bulwark_plating", (java.util.function.BiConsumer<ServerPlayer, Float>) MechFx::bulwarkPlating, "q",
                "missile_swarm", (java.util.function.BiConsumer<ServerPlayer, Float>) MechFx::missileSwarm, "",
                "overclock", (java.util.function.BiConsumer<ServerPlayer, Float>) MechFx::overclockSpell, "q",
                "rail_cannon", (java.util.function.BiConsumer<ServerPlayer, Float>) MechFx::railCannon, "",
                "pilot", (java.util.function.BiConsumer<ServerPlayer, Float>) MechFx::pilotMode, "q",
                "titan_buster", (java.util.function.BiConsumer<ServerPlayer, Float>) MechFx::titanBuster, ""));
        queue.add(new BookMovesTest("weather", com.ironspellgrimoire.registry.AddonItemRegistry.WEATHER_GRIMOIRE::get, WeatherFx.class,
                "sunbeam", (java.util.function.BiConsumer<ServerPlayer, Float>) WeatherFx::sunbeamSpell, "",
                "downpour", (java.util.function.BiConsumer<ServerPlayer, Float>) WeatherFx::downpour, "",
                "thunder_strike", (java.util.function.BiConsumer<ServerPlayer, Float>) WeatherFx::thunderclap, "",
                "hailstorm", (java.util.function.BiConsumer<ServerPlayer, Float>) WeatherFx::hailstorm, "",
                "gale", (java.util.function.BiConsumer<ServerPlayer, Float>) WeatherFx::galeSpell, "",
                "fog_bank", (java.util.function.BiConsumer<ServerPlayer, Float>) WeatherFx::fogBank, "q",
                "cyclone", (java.util.function.BiConsumer<ServerPlayer, Float>) WeatherFx::cycloneSpell, "",
                "rainbow", (java.util.function.BiConsumer<ServerPlayer, Float>) WeatherFx::rainbowSpell, "",
                "eye_of_the_storm", (java.util.function.BiConsumer<ServerPlayer, Float>) WeatherFx::eyeOfTheStorm, "q",
                "cataclysm", (java.util.function.BiConsumer<ServerPlayer, Float>) WeatherFx::cataclysmSpell, ""));
        queue.add(new BookMovesTest("overdrive", com.ironspellgrimoire.registry.AddonItemRegistry.OVERDRIVE_GRIMOIRE::get, OverdriveFx.class,
                "draw_heartline", (java.util.function.BiConsumer<ServerPlayer, Float>) OverdriveFx::drawHeartline, "q",
                "pulse_cut", (java.util.function.BiConsumer<ServerPlayer, Float>) OverdriveFx::thunderDraw, "",
                "adrenaline_rush", (java.util.function.BiConsumer<ServerPlayer, Float>) OverdriveFx::adrenalineRush, "",
                "heartbeat", (java.util.function.BiConsumer<ServerPlayer, Float>) OverdriveFx::readPulse, "q",
                "flatline", (java.util.function.BiConsumer<ServerPlayer, Float>) OverdriveFx::flatlineThrust, "",
                "tachycardia", (java.util.function.BiConsumer<ServerPlayer, Float>) OverdriveFx::tachycardia, "",
                "second_wind", (java.util.function.BiConsumer<ServerPlayer, Float>) OverdriveFx::secondWind, "q",
                "ribbon_bind", (java.util.function.BiConsumer<ServerPlayer, Float>) OverdriveFx::ribbonBind, "",
                "redline", (java.util.function.BiConsumer<ServerPlayer, Float>) OverdriveFx::lastMinute, "q",
                "final_beat", (java.util.function.BiConsumer<ServerPlayer, Float>) OverdriveFx::finalBeat, ""));
        queue.add(new BookMovesTest("dream", com.ironspellgrimoire.registry.AddonItemRegistry.DREAM_GRIMOIRE::get, DreamFx.class,
                "dream_mote", (java.util.function.BiConsumer<ServerPlayer, Float>) DreamFx::lullabyMote, "",
                "counting_sheep", (java.util.function.BiConsumer<ServerPlayer, Float>) DreamFx::countingSheep, "",
                "pillow_fortress", (java.util.function.BiConsumer<ServerPlayer, Float>) DreamFx::pillowFortress, "q",
                "sleepwalk", (java.util.function.BiConsumer<ServerPlayer, Float>) DreamFx::sleepwalk, "q",
                "dream_clock", (java.util.function.BiConsumer<ServerPlayer, Float>) DreamFx::dreamClock, "",
                "toy_parade", (java.util.function.BiConsumer<ServerPlayer, Float>) DreamFx::toyParade, "",
                "nightmare_shard", (java.util.function.BiConsumer<ServerPlayer, Float>) DreamFx::nightmareShard, "",
                "rude_awakening", (java.util.function.BiConsumer<ServerPlayer, Float>) DreamFx::rudeAwakening, "c",
                "half_asleep", (java.util.function.BiConsumer<ServerPlayer, Float>) DreamFx::halfAsleep, "q",
                "dream_world", (java.util.function.BiConsumer<ServerPlayer, Float>) DreamFx::dreamWorld, ""));
        queue.add(new BookMovesTest("shadow", com.ironspellgrimoire.registry.AddonItemRegistry.SHADOW_GRIMOIRE::get, ShadowFx.class,
                "rise", (java.util.function.BiConsumer<ServerPlayer, Float>) (pl, pw) -> { ShadowFx.testRemnant(pl, pl.position().add(0, 0, 3)); ShadowFx.rise(pl, pw); }, "q",
                "umbral_step", (java.util.function.BiConsumer<ServerPlayer, Float>) ShadowFx::umbralStep, "",
                "unseen_hand", (java.util.function.BiConsumer<ServerPlayer, Float>) ShadowFx::unseenHand, "",
                "shadow_exchange", (java.util.function.BiConsumer<ServerPlayer, Float>) ShadowFx::shadowExchange, "q",
                "gate_of_shade", (java.util.function.BiConsumer<ServerPlayer, Float>) ShadowFx::gateOfShade, "q",
                "command_hunt", (java.util.function.BiConsumer<ServerPlayer, Float>) ShadowFx::commandHunt, "",
                "veil", (java.util.function.BiConsumer<ServerPlayer, Float>) ShadowFx::veil, "q",
                "ranks_of_shade", (java.util.function.BiConsumer<ServerPlayer, Float>) ShadowFx::ranksOfShade, "q",
                "status", (java.util.function.BiConsumer<ServerPlayer, Float>) HunterAscension::openStatus, "q",
                "market", (java.util.function.BiConsumer<ServerPlayer, Float>) HunterAscension::openMarket, "q",
                "sovereigns_grip", (java.util.function.BiConsumer<ServerPlayer, Float>) ShadowFx::sovereignsGrip, "",
                "shadow_step", (java.util.function.BiConsumer<ServerPlayer, Float>) ShadowFx::shadowStep, "q",
                "dread_roar", (java.util.function.BiConsumer<ServerPlayer, Float>) ShadowFx::dreadRoar, "q",
                "mantle_of_shades", (java.util.function.BiConsumer<ServerPlayer, Float>) ShadowFx::mantleOfShades, "q",
                "rally_of_the_fallen", (java.util.function.BiConsumer<ServerPlayer, Float>) ShadowFx::rallyOfTheFallen, "q",
                "sovereigns_domain", (java.util.function.BiConsumer<ServerPlayer, Float>) ShadowFx::sovereignsDomain, "q",
                "night_legion", (java.util.function.BiConsumer<ServerPlayer, Float>) ShadowFx::nightLegion, ""));
        queue.add(new HunterTest());
        queue.add(new BookMovesTest("parasite", com.ironspellgrimoire.registry.AddonItemRegistry.PARASITE_GRIMOIRE::get, ParasiteFx.class,
                "consume", (java.util.function.BiConsumer<ServerPlayer, Float>) ParasiteFx::consume, "c",
                "evolution", (java.util.function.BiConsumer<ServerPlayer, Float>) ParasiteFx::evolution, "q",
                "marrow_barrage", (java.util.function.BiConsumer<ServerPlayer, Float>) (pl, pw) -> { ParasiteFx.testFill(pl); ParasiteFx.marrowBarrage(pl, pw); }, "",
                "grasping_field", (java.util.function.BiConsumer<ServerPlayer, Float>) (pl, pw) -> { ParasiteFx.testFill(pl); ParasiteFx.graspingField(pl, pw); }, "",
                "spine_storm", (java.util.function.BiConsumer<ServerPlayer, Float>) (pl, pw) -> { ParasiteFx.testFill(pl); ParasiteFx.spineStorm(pl, pw); }, "c",
                "spine_storm: tempest", (java.util.function.BiConsumer<ServerPlayer, Float>) (pl, pw) -> { ParasiteFx.testPath(pl, ParasiteFx.U_STORM, 1); ParasiteFx.testFill(pl); ParasiteFx.spineStorm(pl, pw); }, "c",
                "spine_storm: impaler", (java.util.function.BiConsumer<ServerPlayer, Float>) (pl, pw) -> { ParasiteFx.testPath(pl, ParasiteFx.U_STORM, 2); ParasiteFx.testFill(pl); ParasiteFx.spineStorm(pl, pw); }, "",
                "grasping_field: maw", (java.util.function.BiConsumer<ServerPlayer, Float>) (pl, pw) -> { ParasiteFx.testPath(pl, ParasiteFx.U_FIELD, 1); ParasiteFx.testFill(pl); ParasiteFx.graspingField(pl, pw); }, "",
                "grasping_field: vortex", (java.util.function.BiConsumer<ServerPlayer, Float>) (pl, pw) -> { ParasiteFx.testPath(pl, ParasiteFx.U_FIELD, 2); ParasiteFx.testFill(pl); ParasiteFx.graspingField(pl, pw); }, "",
                "marrow_barrage: seeker", (java.util.function.BiConsumer<ServerPlayer, Float>) (pl, pw) -> { ParasiteFx.testPath(pl, ParasiteFx.U_BARRAGE, 1); ParasiteFx.testFill(pl); ParasiteFx.marrowBarrage(pl, pw); }, "",
                "marrow_barrage: bomb", (java.util.function.BiConsumer<ServerPlayer, Float>) (pl, pw) -> { ParasiteFx.testPath(pl, ParasiteFx.U_BARRAGE, 2); ParasiteFx.testFill(pl); ParasiteFx.marrowBarrage(pl, pw); }, "",
                "collapse_core", (java.util.function.BiConsumer<ServerPlayer, Float>) (pl, pw) -> { ParasiteFx.testFill(pl); ParasiteMoves.collapseCore(pl, pw); }, "",
                "pack_call", (java.util.function.BiConsumer<ServerPlayer, Float>) (pl, pw) -> { ParasiteFx.testFill(pl); ParasiteMoves.packCall(pl, pw); }, "q",
                "final form consume", (java.util.function.BiConsumer<ServerPlayer, Float>) (pl, pw) -> {
                    ParasiteFx.testUnlockAll(pl); ParasiteFx.testStrain(pl, ParasiteFx.BRUTE); ParasiteFx.testFill(pl); ParasiteFx.testCooldowns(pl);
                    ParasiteMoves.finalFormStart(pl); ParasiteFx.consume(pl, pw); }, "c"));
        queue.add(new ParasiteTest());
        queue.add(new BookMovesTest("hunger", com.ironspellgrimoire.registry.AddonItemRegistry.HUNGER_GRIMOIRE::get, HungerFx.class,
                "draw_blade", (java.util.function.BiConsumer<ServerPlayer, Float>) (pl, pw) -> { HungerFx.testReset(pl); pl.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY); HungerFx.draw(pl, pw); }, "q",
                "gnash", (java.util.function.BiConsumer<ServerPlayer, Float>) (pl, pw) -> { HungerFx.testReset(pl); HungerFx.testUnlockAll(pl); pl.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY); HungerFx.testHold(pl, 2); HungerFx.gnash(pl, pw); }, "c",
                "feast", (java.util.function.BiConsumer<ServerPlayer, Float>) (pl, pw) -> { HungerFx.testReset(pl); HungerFx.testUnlockAll(pl); pl.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY); HungerFx.testHold(pl, 4); HungerFx.feast(pl, pw); }, "c",
                "unleash: devouring bite", (java.util.function.BiConsumer<ServerPlayer, Float>) (pl, pw) -> { HungerFx.testReset(pl); HungerFx.testUnlockAll(pl); pl.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY); HungerFx.testHold(pl, 2); HungerFx.unleash(pl, pw); }, "c",
                "unleash: ossuary eruption", (java.util.function.BiConsumer<ServerPlayer, Float>) (pl, pw) -> { HungerFx.testReset(pl); HungerFx.testUnlockAll(pl); pl.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY); HungerFx.testHold(pl, 5); HungerFx.unleash(pl, pw); }, "c",
                "unleash: shard nova", (java.util.function.BiConsumer<ServerPlayer, Float>) (pl, pw) -> { HungerFx.testReset(pl); HungerFx.testUnlockAll(pl); pl.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY); HungerFx.testHold(pl, 10); HungerFx.unleash(pl, pw); }, "c",
                "unleash: umbral rend", (java.util.function.BiConsumer<ServerPlayer, Float>) (pl, pw) -> { HungerFx.testReset(pl); HungerFx.testUnlockAll(pl); pl.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY); HungerFx.testHold(pl, 15); HungerFx.unleash(pl, pw); }, "c",
                "unleash: pyre sweep", (java.util.function.BiConsumer<ServerPlayer, Float>) (pl, pw) -> { HungerFx.testReset(pl); HungerFx.testUnlockAll(pl); pl.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY); HungerFx.testHold(pl, 22); HungerFx.unleash(pl, pw); }, "c",
                "gnash: thrown", (java.util.function.BiConsumer<ServerPlayer, Float>) (pl, pw) -> { HungerFx.testReset(pl); HungerFx.testUnlockAll(pl); pl.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY); HungerFx.testHold(pl, 17); HungerFx.testBond(pl, 140); pl.setShiftKeyDown(true); HungerFx.gnash(pl, pw); pl.setShiftKeyDown(false); }, "c",
                "unleash: awakened pact", (java.util.function.BiConsumer<ServerPlayer, Float>) (pl, pw) -> { HungerFx.testReset(pl); HungerFx.testUnlockAll(pl); pl.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY); HungerFx.testHold(pl, 7); HungerFx.testBond(pl, 230); HungerFx.unleash(pl, pw); }, "c",
                "charge: bite", (java.util.function.BiConsumer<ServerPlayer, Float>) (pl, pw) -> { HungerFx.testReset(pl); HungerFx.testUnlockAll(pl); pl.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY); HungerFx.testHold(pl, 2); HungerFx.testCharge(pl); }, "c",
                "charge: bone walls", (java.util.function.BiConsumer<ServerPlayer, Float>) (pl, pw) -> { HungerFx.testReset(pl); HungerFx.testUnlockAll(pl); pl.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY); HungerFx.testHold(pl, 7); HungerFx.testCharge(pl); }, "c",
                "charge: crystal lunge", (java.util.function.BiConsumer<ServerPlayer, Float>) (pl, pw) -> { HungerFx.testReset(pl); HungerFx.testUnlockAll(pl); pl.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY); HungerFx.testHold(pl, 9); HungerFx.testCharge(pl); }, "c",
                "charge: shadow blink", (java.util.function.BiConsumer<ServerPlayer, Float>) (pl, pw) -> { HungerFx.testReset(pl); HungerFx.testUnlockAll(pl); pl.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY); HungerFx.testHold(pl, 16); HungerFx.testCharge(pl); }, "c",
                "charge: fire wheel", (java.util.function.BiConsumer<ServerPlayer, Float>) (pl, pw) -> { HungerFx.testReset(pl); HungerFx.testUnlockAll(pl); pl.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY); HungerFx.testHold(pl, 21); HungerFx.testCharge(pl); }, "c",
                "unleash: fossil prism (two paths)", (java.util.function.BiConsumer<ServerPlayer, Float>) (pl, pw) -> { HungerFx.testReset(pl); HungerFx.testUnlockAll(pl); pl.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY); HungerFx.testHold(pl, 23); HungerFx.unleash(pl, pw); }, "c",
                "unleash: ashen eclipse (two paths)", (java.util.function.BiConsumer<ServerPlayer, Float>) (pl, pw) -> { HungerFx.testReset(pl); HungerFx.testUnlockAll(pl); pl.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY); HungerFx.testHold(pl, 28); HungerFx.unleash(pl, pw); }, "c",
                "hunger_domain", (java.util.function.BiConsumer<ServerPlayer, Float>) (pl, pw) -> { HungerFx.testReset(pl); HungerFx.testUnlockAll(pl); pl.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY); HungerFx.testHold(pl, 5); HungerFx.hungerDomain(pl, pw); HungerFx.feast(pl, pw); }, "c",
                "the_blade", (java.util.function.BiConsumer<ServerPlayer, Float>) (pl, pw) -> HungerFx.theBlade(pl, pw), "q"));
        queue.add(new HungerTest());
        queue.add(new BookMovesTest("twins", com.ironspellgrimoire.registry.AddonItemRegistry.TWINS_GRIMOIRE::get, TwinsFx.class,
                "sol: sunstrike", (java.util.function.BiConsumer<ServerPlayer, Float>) (pl, pw) -> { TwinsFx.testReset(pl); TwinsFx.testLevels(pl, 50, 50); TwinsFx.testCooldowns(pl); TwinsFx.testActive(pl, 0); TwinsFx.testLoad(pl, 0, 0, -1, -1, -1); TwinsArts.cast(pl, 0, 0); }, "c",
                "sol: flare shot", (java.util.function.BiConsumer<ServerPlayer, Float>) (pl, pw) -> { TwinsFx.testReset(pl); TwinsFx.testLevels(pl, 50, 50); TwinsFx.testCooldowns(pl); TwinsFx.testActive(pl, 0); TwinsFx.testLoad(pl, 0, 1, -1, -1, -1); TwinsArts.cast(pl, 0, 1); }, "",
                "sol: solar lance", (java.util.function.BiConsumer<ServerPlayer, Float>) (pl, pw) -> { TwinsFx.testReset(pl); TwinsFx.testLevels(pl, 50, 50); TwinsFx.testCooldowns(pl); TwinsFx.testActive(pl, 0); TwinsFx.testLoad(pl, 0, 2, -1, -1, -1); TwinsArts.cast(pl, 0, 2); }, "",
                "sol: helios wheel", (java.util.function.BiConsumer<ServerPlayer, Float>) (pl, pw) -> { TwinsFx.testReset(pl); TwinsFx.testLevels(pl, 50, 50); TwinsFx.testCooldowns(pl); TwinsFx.testActive(pl, 0); TwinsFx.testLoad(pl, 0, 3, -1, -1, -1); TwinsArts.cast(pl, 0, 3); }, "",
                "sol: corona", (java.util.function.BiConsumer<ServerPlayer, Float>) (pl, pw) -> { TwinsFx.testReset(pl); TwinsFx.testLevels(pl, 50, 50); TwinsFx.testCooldowns(pl); TwinsFx.testActive(pl, 0); TwinsFx.testLoad(pl, 0, 4, -1, -1, -1); TwinsArts.cast(pl, 0, 4); }, "c",
                "sol: zenith leap", (java.util.function.BiConsumer<ServerPlayer, Float>) (pl, pw) -> { TwinsFx.testReset(pl); TwinsFx.testLevels(pl, 50, 50); TwinsFx.testCooldowns(pl); TwinsFx.testActive(pl, 0); TwinsFx.testLoad(pl, 0, 5, -1, -1, -1); TwinsArts.cast(pl, 0, 5); }, "c",
                "sol: dawnbreaker", (java.util.function.BiConsumer<ServerPlayer, Float>) (pl, pw) -> { TwinsFx.testReset(pl); TwinsFx.testLevels(pl, 50, 50); TwinsFx.testCooldowns(pl); TwinsFx.testActive(pl, 0); TwinsFx.testLoad(pl, 0, 6, -1, -1, -1); TwinsArts.cast(pl, 0, 6); }, "c",
                "sol: sunspear barrage", (java.util.function.BiConsumer<ServerPlayer, Float>) (pl, pw) -> { TwinsFx.testReset(pl); TwinsFx.testLevels(pl, 50, 50); TwinsFx.testCooldowns(pl); TwinsFx.testActive(pl, 0); TwinsFx.testLoad(pl, 0, 7, -1, -1, -1); TwinsArts.cast(pl, 0, 7); }, "",
                "nyx: moonstep", (java.util.function.BiConsumer<ServerPlayer, Float>) (pl, pw) -> { TwinsFx.testReset(pl); TwinsFx.testLevels(pl, 50, 50); TwinsFx.testCooldowns(pl); TwinsFx.testActive(pl, 1); TwinsFx.testLoad(pl, 1, 0, -1, -1, -1); TwinsArts.cast(pl, 1, 0); }, "q",
                "nyx: frost needle", (java.util.function.BiConsumer<ServerPlayer, Float>) (pl, pw) -> { TwinsFx.testReset(pl); TwinsFx.testLevels(pl, 50, 50); TwinsFx.testCooldowns(pl); TwinsFx.testActive(pl, 1); TwinsFx.testLoad(pl, 1, 1, -1, -1, -1); TwinsArts.cast(pl, 1, 1); }, "",
                "nyx: crescent fan", (java.util.function.BiConsumer<ServerPlayer, Float>) (pl, pw) -> { TwinsFx.testReset(pl); TwinsFx.testLevels(pl, 50, 50); TwinsFx.testCooldowns(pl); TwinsFx.testActive(pl, 1); TwinsFx.testLoad(pl, 1, 2, -1, -1, -1); TwinsArts.cast(pl, 1, 2); }, "",
                "nyx: mirror double", (java.util.function.BiConsumer<ServerPlayer, Float>) (pl, pw) -> { TwinsFx.testReset(pl); TwinsFx.testLevels(pl, 50, 50); TwinsFx.testCooldowns(pl); TwinsFx.testActive(pl, 1); TwinsFx.testLoad(pl, 1, 3, -1, -1, -1); TwinsArts.cast(pl, 1, 3); }, "q",
                "nyx: lunar veil", (java.util.function.BiConsumer<ServerPlayer, Float>) (pl, pw) -> { TwinsFx.testReset(pl); TwinsFx.testLevels(pl, 50, 50); TwinsFx.testCooldowns(pl); TwinsFx.testActive(pl, 1); TwinsFx.testLoad(pl, 1, 4, -1, -1, -1); TwinsArts.cast(pl, 1, 4); }, "q",
                "nyx: starfall rain", (java.util.function.BiConsumer<ServerPlayer, Float>) (pl, pw) -> { TwinsFx.testReset(pl); TwinsFx.testLevels(pl, 50, 50); TwinsFx.testCooldowns(pl); TwinsFx.testActive(pl, 1); TwinsFx.testLoad(pl, 1, 5, -1, -1, -1); TwinsArts.cast(pl, 1, 5); }, "",
                "nyx: tidelock", (java.util.function.BiConsumer<ServerPlayer, Float>) (pl, pw) -> { TwinsFx.testReset(pl); TwinsFx.testLevels(pl, 50, 50); TwinsFx.testCooldowns(pl); TwinsFx.testActive(pl, 1); TwinsFx.testLoad(pl, 1, 6, -1, -1, -1); TwinsArts.cast(pl, 1, 6); }, "",
                "nyx: void rift", (java.util.function.BiConsumer<ServerPlayer, Float>) (pl, pw) -> { TwinsFx.testReset(pl); TwinsFx.testLevels(pl, 50, 50); TwinsFx.testCooldowns(pl); TwinsFx.testActive(pl, 1); TwinsFx.testLoad(pl, 1, 7, -1, -1, -1); TwinsArts.cast(pl, 1, 7); }, "",
                "swap: sun flare", (java.util.function.BiConsumer<ServerPlayer, Float>) (pl, pw) -> { TwinsFx.testReset(pl); TwinsFx.testLevels(pl, 50, 50); TwinsFx.testCooldowns(pl); TwinsFx.testActive(pl, 1); TwinsFx.testRes(pl, 100); TwinsFx.swap(pl); }, "c",
                "swap: crescent wave", (java.util.function.BiConsumer<ServerPlayer, Float>) (pl, pw) -> { TwinsFx.testReset(pl); TwinsFx.testLevels(pl, 50, 50); TwinsFx.testCooldowns(pl); TwinsFx.testActive(pl, 0); TwinsFx.testRes(pl, 100); TwinsFx.swap(pl); }, "",
                "twin_strike", (java.util.function.BiConsumer<ServerPlayer, Float>) (pl, pw) -> { TwinsFx.testReset(pl); TwinsFx.testLevels(pl, 50, 50); TwinsFx.testCooldowns(pl); TwinsArts.twinStrike(pl, pw); }, "c",
                "eclipse_union", (java.util.function.BiConsumer<ServerPlayer, Float>) (pl, pw) -> { TwinsFx.testReset(pl); TwinsFx.testLevels(pl, 50, 50); TwinsFx.testCooldowns(pl); TwinsFx.testTrialWon(pl); TwinsArts.eclipseUnion(pl, pw); TwinsArts.cast(pl, 0, 6); TwinsArts.cast(pl, 1, 1); TwinsFx.testEndEclipse(pl); }, "c",
                "the_masks", (java.util.function.BiConsumer<ServerPlayer, Float>) (pl, pw) -> { TwinsFx.theMasks(pl, pw); }, "q",
                "high_noon", (java.util.function.BiConsumer<ServerPlayer, Float>) (pl, pw) -> { TwinsFx.testReset(pl); TwinsFx.testLevels(pl, 50, 50); TwinsFx.testActive(pl, 0); TwinsFx.highNoon(pl, pw); }, "c",
                "eventide", (java.util.function.BiConsumer<ServerPlayer, Float>) (pl, pw) -> { TwinsFx.testReset(pl); TwinsFx.testLevels(pl, 50, 50); TwinsFx.testActive(pl, 1); TwinsFx.eventide(pl, pw); }, "q",
                "sol: helios wheel (perfected)", (java.util.function.BiConsumer<ServerPlayer, Float>) (pl, pw) -> { TwinsFx.testReset(pl); TwinsFx.testLevels(pl, 50, 50); TwinsFx.testActive(pl, 0); TwinsFx.testLoad(pl, 0, 3, -1, -1, -1); TwinsFx.testUses(pl, 0, 3, 250); TwinsArts.cast(pl, 0, 3); }, "",
                "nyx: void rift (perfected)", (java.util.function.BiConsumer<ServerPlayer, Float>) (pl, pw) -> { TwinsFx.testReset(pl); TwinsFx.testLevels(pl, 50, 50); TwinsFx.testActive(pl, 1); TwinsFx.testLoad(pl, 1, 7, -1, -1, -1); TwinsFx.testUses(pl, 1, 7, 250); TwinsArts.cast(pl, 1, 7); }, "",
                "echo shot", (java.util.function.BiConsumer<ServerPlayer, Float>) (pl, pw) -> { TwinsFx.testReset(pl); TwinsFx.testLevels(pl, 50, 50); TwinsFx.testActive(pl, 0); TwinsFx.testCombat(pl); TwinsFx.testEcho(pl); }, "c"));
        queue.add(new TwinsTest());
        queue.add(new BookMovesTest("architect", com.ironspellgrimoire.registry.AddonItemRegistry.ARCHITECT_GRIMOIRE::get, FortressFx.class,
                "barracks", (java.util.function.BiConsumer<ServerPlayer, Float>) (pl, pw) -> archMove(pl, 0), "",
                "armoury", (java.util.function.BiConsumer<ServerPlayer, Float>) (pl, pw) -> archMove(pl, 1), "",
                "training yard", (java.util.function.BiConsumer<ServerPlayer, Float>) (pl, pw) -> archMove(pl, 2), "",
                "watchtower", (java.util.function.BiConsumer<ServerPlayer, Float>) (pl, pw) -> archMove(pl, 3), "",
                "gatehouse", (java.util.function.BiConsumer<ServerPlayer, Float>) (pl, pw) -> archMove(pl, 4), "c",
                "war room", (java.util.function.BiConsumer<ServerPlayer, Float>) (pl, pw) -> archMove(pl, 5), "q",
                "observatory", (java.util.function.BiConsumer<ServerPlayer, Float>) (pl, pw) -> archMove(pl, 6), "q",
                "library", (java.util.function.BiConsumer<ServerPlayer, Float>) (pl, pw) -> archMove(pl, 7), "q",
                "arcanum", (java.util.function.BiConsumer<ServerPlayer, Float>) (pl, pw) -> archMove(pl, 8), "",
                "scrying pool", (java.util.function.BiConsumer<ServerPlayer, Float>) (pl, pw) -> archMove(pl, 9), "q",
                "alchemy lab", (java.util.function.BiConsumer<ServerPlayer, Float>) (pl, pw) -> archMove(pl, 10), "",
                "rune vault", (java.util.function.BiConsumer<ServerPlayer, Float>) (pl, pw) -> archMove(pl, 11), "q",
                "chapel", (java.util.function.BiConsumer<ServerPlayer, Float>) (pl, pw) -> archMove(pl, 12), "q",
                "reliquary", (java.util.function.BiConsumer<ServerPlayer, Float>) (pl, pw) -> archMove(pl, 13), "q",
                "crypt", (java.util.function.BiConsumer<ServerPlayer, Float>) (pl, pw) -> archMove(pl, 14), "q",
                "bell tower", (java.util.function.BiConsumer<ServerPlayer, Float>) (pl, pw) -> archMove(pl, 15), "c",
                "garden", (java.util.function.BiConsumer<ServerPlayer, Float>) (pl, pw) -> archMove(pl, 16), "q",
                "hall of banners", (java.util.function.BiConsumer<ServerPlayer, Float>) (pl, pw) -> archMove(pl, 17), "q",
                "forge", (java.util.function.BiConsumer<ServerPlayer, Float>) (pl, pw) -> archMove(pl, 18), "",
                "foundry", (java.util.function.BiConsumer<ServerPlayer, Float>) (pl, pw) -> archMove(pl, 19), "",
                "workshop", (java.util.function.BiConsumer<ServerPlayer, Float>) (pl, pw) -> archMove(pl, 20), "",
                "siege yard", (java.util.function.BiConsumer<ServerPlayer, Float>) (pl, pw) -> archMove(pl, 21), "",
                "cistern", (java.util.function.BiConsumer<ServerPlayer, Float>) (pl, pw) -> archMove(pl, 22), "",
                "treasury", (java.util.function.BiConsumer<ServerPlayer, Float>) (pl, pw) -> archMove(pl, 23), "q",
                "herald's gate", (java.util.function.BiConsumer<ServerPlayer, Float>) (pl, pw) -> archMove(pl, 24), "q",
                "map room", (java.util.function.BiConsumer<ServerPlayer, Float>) (pl, pw) -> archMove(pl, 25), "q",
                "throne room", (java.util.function.BiConsumer<ServerPlayer, Float>) (pl, pw) -> { archMove(pl, 0); FortressFx.testStars(pl, 5); FortressFx.handle(pl, 1, 26 * 100 + 1); FortressFx.castRoom(pl, 26); }, "q",
                "rampart", (java.util.function.BiConsumer<ServerPlayer, Float>) (pl, pw) -> { archSetup(pl); FortressFx.rampartPage(pl, pw); }, "q",
                "siege mode", (java.util.function.BiConsumer<ServerPlayer, Float>) (pl, pw) -> { archSetup(pl); FortressFx.handle(pl, 1, FortressFx.WATCHTOWER * 100); FortressFx.siegePage(pl, pw); }, "",
                "citadel", (java.util.function.BiConsumer<ServerPlayer, Float>) (pl, pw) -> { archSetup(pl); FortressFx.testLevel(pl, 10); for (int r = 0; r < 24; r++) FortressFx.handle(pl, 1, r * 100 + r); FortressFx.citadelPage(pl, pw); }, "",
                "the fortress", (java.util.function.BiConsumer<ServerPlayer, Float>) (pl, pw) -> FortressFx.fortressPage(pl, pw), "q"));
        queue.add(new ArchitectTest());
        queue.add(new ArchitectCommandTest());
        queue.add(new EliteTest());
        queue.add(new BossTest("Ossuarch", AddonEntities.ZENON));
        queue.add(new BossTest("Beast of Despair", AddonEntities.BEAST_OF_DESPAIR));
        queue.add(new BossTest("Rhya", AddonEntities.RHYA));
        queue.add(new BossTest("Licht", AddonEntities.LICHT));
        queue.add(new BossTest("Revenant", AddonEntities.REVENANT));
        queue.add(new BossTest("Curse Witch", AddonEntities.CURSE_WITCH));
        queue.add(new BossTest("Cinder Sovereign", AddonEntities.CINDER_SOVEREIGN));
        queue.add(new BossTest("Memory Guardian", AddonEntities.MEMORY_GUARDIAN));
        queue.add(new BossTest("Dante", AddonEntities.DANTE));
        queue.add(new RivalTest());
        queue.add(new BossTest("Seren Vale", AddonEntities.SEREN_VALE));
        queue.add(new BossTest("Brannoch Tor", AddonEntities.BRANNOCH_TOR));
        queue.add(new BossTest("Alder Bellmar", AddonEntities.ALDER_BELLMAR));
        queue.add(new BossTest("Wren Bellmar", AddonEntities.WREN_BELLMAR));
        queue.add(new BossTest("Mira Hollowell", AddonEntities.MIRA_HOLLOWELL));
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
        if (index >= queue.size()) { running = false; done = true; if (dummy != null) dummy.discard(); if (cache != null) cache.save(); return; }
        Test cur = queue.get(index);
        try {
            if (t == 0) {
                Class<?>[] roots = cur.roots();
                curKey = roots == null ? null : ClassKey.of(cur.extra() + "|" + HARNESS, roots);
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
            if (cur.tick(t)) { finished(cur); index++; t = 0; }
        } catch (Throwable x) {
            fail(cur.name, "the test itself threw: " + x);
            index++; t = 0;
        }
    }

    private static ClassKey.Cache cache;
    private static String curKey;
    private static int startAt;
    private static final String HARNESS = ClassKey.single(GameTests.class);

    /** A test is done: if nothing it added failed, its fingerprint is kept, so next time it's skipped unless its code changes. */
    private static void finished(Test cur) {
        if (curKey == null || cache == null) return;
        for (int i = startAt; i < results.size(); i++) if (results.get(i).startsWith("FAIL")) return;
        cache.passed(cur.name, curKey);
    }

    /** How far through: "n / total". */
    public static String progress() { return index + " / " + queue.size(); }
}
