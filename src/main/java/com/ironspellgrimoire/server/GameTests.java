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
        /** The code this test depends on (its fingerprint, ClassKey); null = always run it. */
        Class<?>[] roots() { return null; }
        String extra() { return name; }
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
        if (dummy != null && dummy.isAlive()) { dummy.setHealth(dummy.getMaxHealth()); dummy.teleportTo(home.x, home.y, home.z + 6); dummy.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO); dummy.hurtMarked = true; return; }   // still, too: a knock-back from the last move mustn't carry it out of the next one
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
        BlockState[] before; Set<MobEffect> pe, de; int fx0; float mana0, hp0; String castErr;
        SpellTest(AbstractSpell s) { super("spell " + s.getSpellId()); spell = s; }
        @Override Class<?>[] roots() { return new Class<?>[]{spell.getClass()}; }
        boolean skip;
        @Override void start() {
            cancelCast(); clearArea(); resetPlayer(true); ensureDummy();
            // equip the grimoire this spell belongs to, fully mastered (else its lock or another grimoire stops it)
            String path = spell.getSpellResource().getPath();
            GrimoireMastery.Book owner = null;
            for (GrimoireMastery.Book b : GrimoireMastery.BOOKS) if (b.unlocks().containsKey(path)) { owner = b; break; }
            if (owner == null) { skip = true; info(name, "not in any grimoire (a form's or a union's version) - cast through its form, not tested alone"); return; }
            ItemStack book = new ItemStack(owner.item().get());
            CompoundTag mt = new CompoundTag(); mt.putInt("xp", 10_000_000);
            book.getOrCreateTag().put(GrimoireMastery.masteryTag(), mt);
            book.getOrCreateTag().putBoolean("Ascended", true);
            final ItemStack bk = book;
            top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(p).ifPresent(h -> h.setEquippedCurio("spellbook", 0, bk));
            p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            testBook = bk;
            before = snapshot(); pe = effects(p); de = effects(dummy);
            mana0 = MagicData.getPlayerMagicData(p).getMana(); hp0 = dummy == null ? 0 : dummy.getHealth();
            fx0 = AddonNetwork.fxSent;
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
        @Override boolean tick(int t) {
            if (skip) return true;
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
            if (fx <= 1 && spent <= 0 && dealt <= 0) warn(name, "did nothing visible (no effects, no mana, no damage; " + castState + ") - it may need a target, a form or a condition");
            if (ok) pass(name, String.format("%d fx packets, %.0f mana, %.1f damage", fx, spent, dealt));
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
        float hp;
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
                try { moves.get(i).accept(p, 10f); } catch (Throwable x) { bad.add(names[i] + " threw " + x); }
            }
            if ((t - 1) % 50 == 49 && i < names.length) {
                float dealt = hp - (dummy == null ? hp : dummy.getHealth());
                if (!quiet.contains(names[i]) && dealt <= 0) bad.add(names[i] + " did no damage to the dummy");
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
            Moves.ends(p, GravityFx.MODE); Moves.ends(p, SoundFx.MODE); Moves.ends(p, SoundFx.BARRIER); Moves.ends(p, InkFx.MODE); Moves.ends(p, ErrorFx.MODE); Moves.ends(p, TreasureFx.MODE); Moves.ends(p, TreasureFx.AEGIS); Moves.ends(p, ChainFx.MODE); Moves.ends(p, HunterFx.MODE); Moves.ends(p, VoidFx.MODE); Moves.ends(p, VoidFx.ABSENCE); Moves.ends(p, GamblerFx.MODE); Moves.ends(p, GamblerFx.INSURED); Moves.ends(p, TarotFx.MODE); Moves.ends(p, TarotFx.READ); Moves.ends(p, AbyssFx.MODE); Moves.ends(p, MechFx.MODE); Moves.ends(p, MechFx.PLATING); Moves.ends(p, WeatherFx.MODE); Moves.ends(p, WeatherFx.FOG_BANK);
            return true;
        }
    }

    /** The Blood Grimoire's paths: every spell cast both ways (a mastered book) - nothing may throw, change the world or give effects. */
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
        queue.add(new BossTest("Ossuarch", AddonEntities.ZENON));
        queue.add(new BossTest("Beast of Despair", AddonEntities.BEAST_OF_DESPAIR));
        queue.add(new BossTest("Rhya", AddonEntities.RHYA));
        queue.add(new BossTest("Licht", AddonEntities.LICHT));
        queue.add(new BossTest("Revenant", AddonEntities.REVENANT));
        queue.add(new BossTest("Curse Witch", AddonEntities.CURSE_WITCH));
        queue.add(new BossTest("Cinder Sovereign", AddonEntities.CINDER_SOVEREIGN));
        queue.add(new BossTest("Memory Guardian", AddonEntities.MEMORY_GUARDIAN));
        queue.add(new BossTest("Dante", AddonEntities.DANTE));
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
