package com.ironspellgrimoire.server;

import com.ironspellgrimoire.entity.ShadowSoldier;
import com.ironspellgrimoire.network.AddonNetwork;
import com.ironspellgrimoire.network.ShadowPackets;
import com.ironspellgrimoire.registry.AddonItemRegistry;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Hunter's Ascension - the Shadow Grimoire's own growth for its holder (original names and looks).
 *
 *  Level (1-100): kills made while holding the grimoire (or by your shadows) give XP, more for stronger foes. Each level
 *    gives 3 stat points: Strength (melee), Agility (speed), Vitality (health), Focus (mana), Perception (spell power) -
 *    kept as attribute bonuses, applied every second (no status effects). Seen and spent in the Status window
 *    (the "Status" page).
 *  Shade Marks: earned from kills, the Daily Trial and the Reckoning; spent in the Market (the "Market" page).
 *  Daily Trial: three tasks each in-game day (kills, damage dealt, distance travelled, shadows raised). All three: 5 stat
 *    points, XP and Shade Marks. Left undone when the day turns: the Reckoning - a 2-minute hunt, waves of stronger
 *    mobs closing in (no block is ever broken); survive it for a reward.
 *  Strength in Numbers: while you hold the grimoire, every shadow in your army adds 2 max mana and 0.3% spell power
 *    (up to 50 shadows).
 *  Job Change (hunter level 40; from the Status window - no spell slot): the Job Change Trial - three waves and a
 *    trial champion, all of them to be slain within 3 minutes. Pass it and choose a job, for good:
 *      Necromancer - your shadows hit 25% harder, +20% max mana;
 *      Assassin - knives hit 25% harder, +10% speed, +15% attack speed;
 *      Warlord - greatswords hit 30% harder, +4 hearts, +4 armour, steadier footing (knockback resistance).
 *  Level-Up Surge (automatic): every level-up heals you fully, fills your mana and, for 10 s, +20% damage and +15% speed.
 * All saved in the part of the player's data that survives death (ShadowArmy.root).
 */
@Mod.EventBusSubscriber(modid = "iron_spell_grimoire")
public final class HunterAscension {
    private HunterAscension() {}

    public static final int MAX_LEVEL = 100, POINTS_PER_LEVEL = 3;
    public static final String[] STATS = {"str", "agi", "vit", "foc", "per"};
    static final String LVL = "isg_asc_lvl", XP = "isg_asc_xp", PTS = "isg_asc_pts", MARKS = "isg_marks", LURE = "isg_lure",
            DAY = "isg_day", DAILY = "isg_daily", DONE = "isg_daily_done", DUE = "isg_reckon_due", STARTED = "isg_asc_on";
    static final String JOB = "isg_job", JOB_READY = "isg_job_ready";
    public static final int JOB_LEVEL = 40;
    /** The jobs (1-3; 0 none yet). */
    public static final String[] JOBS = {"", "Necromancer", "Assassin", "Warlord"};
    /** Daily Trial kinds: 0 kills, 1 damage dealt, 2 blocks travelled, 3 shadows raised. */
    public static final String[] TASKS = {"Slay %d foes", "Deal %d damage", "Travel %d blocks", "Raise %d shadows"};

    private static final UUID STR_ID = UUID.fromString("e5f3b2c1-7d40-4a3b-8e62-8f1d50000061");
    private static final UUID AGI_ID = UUID.fromString("e5f3b2c1-7d40-4a3b-8e62-8f1d50000062");
    private static final UUID AGI_ATK_ID = UUID.fromString("e5f3b2c1-7d40-4a3b-8e62-8f1d50000063");
    private static final UUID VIT_ID = UUID.fromString("e5f3b2c1-7d40-4a3b-8e62-8f1d50000064");
    private static final UUID FOC_ID = UUID.fromString("e5f3b2c1-7d40-4a3b-8e62-8f1d50000065");
    private static final UUID FOC_REGEN_ID = UUID.fromString("e5f3b2c1-7d40-4a3b-8e62-8f1d50000066");
    private static final UUID PER_ID = UUID.fromString("e5f3b2c1-7d40-4a3b-8e62-8f1d50000067");
    private static final UUID ARMY_MANA_ID = UUID.fromString("e5f3b2c1-7d40-4a3b-8e62-8f1d50000068");
    private static final UUID ARMY_POWER_ID = UUID.fromString("e5f3b2c1-7d40-4a3b-8e62-8f1d50000069");
    private static final UUID JOB_A = UUID.fromString("e5f3b2c1-7d40-4a3b-8e62-8f1d5000006a");
    private static final UUID JOB_B = UUID.fromString("e5f3b2c1-7d40-4a3b-8e62-8f1d5000006b");
    private static final UUID JOB_C = UUID.fromString("e5f3b2c1-7d40-4a3b-8e62-8f1d5000006c");
    private static final UUID JOB_D = UUID.fromString("e5f3b2c1-7d40-4a3b-8e62-8f1d5000006d");
    private static final UUID SURGE_DMG = UUID.fromString("e5f3b2c1-7d40-4a3b-8e62-8f1d5000006e");
    private static final UUID SURGE_SPD = UUID.fromString("e5f3b2c1-7d40-4a3b-8e62-8f1d5000006f");

    static CompoundTag d(Player p) { return ShadowArmy.root(p); }
    public static int level(Player p) { return Math.max(1, d(p).getInt(LVL)); }
    public static int stat(Player p, int i) { return d(p).getInt("isg_asc_" + STATS[i]); }
    public static int marks(Player p) { return d(p).getInt(MARKS); }
    public static int job(Player p) { return d(p).getInt(JOB); }
    public static int xpNext(int lvl) { return (int) Math.round(40 + 10 * Math.pow(lvl, 1.45)); }

    // ================================================================== XP, marks
    public static void gainXp(ServerPlayer p, int xp) {
        CompoundTag t = d(p);
        int lvl = level(p);
        if (lvl >= MAX_LEVEL) return;
        int cur = t.getInt(XP) + xp, ups = 0;
        while (lvl < MAX_LEVEL && cur >= xpNext(lvl)) { cur -= xpNext(lvl); lvl++; ups++; }
        t.putInt(LVL, lvl); t.putInt(XP, lvl >= MAX_LEVEL ? 0 : cur);
        if (ups > 0) {
            t.putInt(PTS, t.getInt(PTS) + ups * POINTS_PER_LEVEL);
            p.displayClientMessage(Component.literal("+" + ups * POINTS_PER_LEVEL + " stat points  -  open Status to spend them").withStyle(ChatFormatting.AQUA), true);
            p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 0.7f);
            ShadowFx.play(p.serverLevel(), p.position(), "shadow_rise", 0.7f, 1.5f);
            surge(p, lvl);
        }
    }

    /** Level-Up Surge: whole again - health and mana - and for 10 s, +20% damage and +15% speed; the System says so. */
    static void surge(ServerPlayer p, int lvl) {
        if (!p.isAlive()) return;
        p.setHealth(p.getMaxHealth());
        io.redspace.ironsspellbooks.api.magic.MagicData.getPlayerMagicData(p).setMana((float) p.getAttributeValue(AttributeRegistry.MAX_MANA.get()));
        TimedModifiers.apply(p, Attributes.ATTACK_DAMAGE, SURGE_DMG, "Level-Up Surge", 0.2, AttributeModifier.Operation.MULTIPLY_TOTAL, 200);
        TimedModifiers.apply(p, Attributes.MOVEMENT_SPEED, SURGE_SPD, "Level-Up Surge", 0.15, AttributeModifier.Operation.MULTIPLY_TOTAL, 200);
        p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(5, 40, 15));
        p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(Component.literal("LEVEL UP").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)));
        p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(Component.literal("Hunter level " + lvl + "  -  Surge: +20% damage, +15% speed for 10 s").withStyle(ChatFormatting.GRAY)));
        ServerLevel sl = p.serverLevel();
        Vec3 c = p.position().add(0, 0.05, 0);
        FxSend.ring(sl, Fx.ICE | com.ironspellgrimoire.network.FxPacket.KEEP_SHAPE, c, 0.4f, 4.5f, 0.2f, 14);
        FxSend.beam(sl, Fx.ICE | com.ironspellgrimoire.network.FxPacket.KEEP_SHAPE, c, c.add(0, 6, 0), 0.6f, 14);
    }

    public static void addMarks(ServerPlayer p, int n) { d(p).putInt(MARKS, Math.max(0, marks(p) + n)); }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent e) {
        LivingEntity dead = e.getEntity();
        if (dead.level().isClientSide || dead instanceof Player || dead.getPersistentData().getBoolean("isg_no_reward")) return;
        ServerPlayer p = e.getSource().getEntity() instanceof ServerPlayer sp ? sp
                : e.getSource().getEntity() instanceof ShadowSoldier s && s.owner() instanceof ServerPlayer o ? o : null;
        if (p == null || !ShadowFx.equipped(p) || dead instanceof ShadowSoldier ss && !ss.living()) return;
        start(p);
        float hp = dead.getMaxHealth();
        boolean boss = hp >= 150 || dead.getType().is(net.minecraftforge.common.Tags.EntityTypes.BOSSES);
        gainXp(p, Math.max(1, Math.round(hp / 2f * (boss ? 3 : 1))));
        addMarks(p, Math.max(1, Math.round(hp / 8f * (boss ? 3 : 1))));
        progress(p, 0, 1);
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent e) {
        if (e.getSource().getEntity() instanceof ServerPlayer p && e.getEntity() != p && ShadowFx.equipped(p)) progress(p, 1, Math.round(e.getAmount()));
    }

    /** The jobs' weapon edge: an Assassin's knives hit 25% harder, a Warlord's greatswords 30%. */
    @SubscribeEvent
    public static void onJobHurt(LivingHurtEvent e) {
        if (!(e.getSource().getEntity() instanceof ServerPlayer p) || e.getEntity() == p) return;
        int job = job(p);
        if (job != 2 && job != 3) return;
        net.minecraft.world.item.Item it = p.getMainHandItem().getItem();
        boolean greatsword = it == AddonItemRegistry.FROSTWING.get();
        boolean knife = !greatsword && (it == AddonItemRegistry.NIGHTFANG.get() || it == AddonItemRegistry.DRAGON_FANG.get() || it == AddonItemRegistry.DUSKGLASS_KRIS.get()
                || it == AddonItemRegistry.GILDED_THORN.get() || it == AddonItemRegistry.BLOODREND.get() || it == AddonItemRegistry.ROSEWRATH.get());
        if (job == 2 && knife) e.setAmount(e.getAmount() * 1.25f);
        if (job == 3 && greatsword) e.setAmount(e.getAmount() * 1.3f);
    }

    /** A shadow rose (ShadowFx.rise). */
    public static void onRaise(ServerPlayer p) { progress(p, 3, 1); }

    /** A Remnant Lure bought in the Market: the next remnant rises on the first try. */
    public static boolean useLure(ServerPlayer p) {
        int n = d(p).getInt(LURE);
        if (n <= 0) return false;
        d(p).putInt(LURE, n - 1);
        return true;
    }

    // ================================================================== stats as attribute bonuses
    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        if (p.tickCount % 20 == 0) {
            int str = stat(p, 0), agi = stat(p, 1), vit = stat(p, 2), foc = stat(p, 3), per = stat(p, 4);
            setMod(p, Attributes.ATTACK_DAMAGE, STR_ID, "Strength", 0.004 * str, AttributeModifier.Operation.MULTIPLY_TOTAL);
            setMod(p, Attributes.MOVEMENT_SPEED, AGI_ID, "Agility", Math.min(0.3, 0.003 * agi), AttributeModifier.Operation.MULTIPLY_TOTAL);
            setMod(p, Attributes.ATTACK_SPEED, AGI_ATK_ID, "Agility", 0.005 * agi, AttributeModifier.Operation.MULTIPLY_TOTAL);
            setMod(p, Attributes.MAX_HEALTH, VIT_ID, "Vitality", 0.5 * vit, AttributeModifier.Operation.ADDITION);
            setMod(p, AttributeRegistry.MAX_MANA.get(), FOC_ID, "Focus", 4.0 * foc, AttributeModifier.Operation.ADDITION);
            setMod(p, AttributeRegistry.MANA_REGEN.get(), FOC_REGEN_ID, "Focus", 0.005 * foc, AttributeModifier.Operation.MULTIPLY_BASE);
            setMod(p, AttributeRegistry.SPELL_POWER.get(), PER_ID, "Perception", 0.004 * per, AttributeModifier.Operation.MULTIPLY_BASE);
            boolean held = ShadowFx.equipped(p);   // Strength in Numbers
            int army = held ? Math.min(50, ShadowArmy.count(p)) : 0;
            setMod(p, AttributeRegistry.MAX_MANA.get(), ARMY_MANA_ID, "Strength in Numbers", 2.0 * army, AttributeModifier.Operation.ADDITION);
            setMod(p, AttributeRegistry.SPELL_POWER.get(), ARMY_POWER_ID, "Strength in Numbers", 0.003 * army, AttributeModifier.Operation.MULTIPLY_BASE);
            int job = job(p);   // the job's perks (Job Change)
            setMod(p, AttributeRegistry.MAX_MANA.get(), JOB_A, "Necromancer", job == 1 ? 0.2 : 0, AttributeModifier.Operation.MULTIPLY_BASE);
            setMod(p, Attributes.MOVEMENT_SPEED, JOB_B, "Assassin", job == 2 ? 0.1 : 0, AttributeModifier.Operation.MULTIPLY_TOTAL);
            setMod(p, Attributes.ATTACK_SPEED, JOB_C, "Assassin", job == 2 ? 0.15 : 0, AttributeModifier.Operation.MULTIPLY_TOTAL);
            setMod(p, Attributes.MAX_HEALTH, JOB_A, "Warlord", job == 3 ? 8 : 0, AttributeModifier.Operation.ADDITION);
            setMod(p, Attributes.ARMOR, JOB_B, "Warlord", job == 3 ? 4 : 0, AttributeModifier.Operation.ADDITION);
            setMod(p, Attributes.KNOCKBACK_RESISTANCE, JOB_D, "Warlord", job == 3 ? 0.25 : 0, AttributeModifier.Operation.ADDITION);
            if (held) start(p);
            if (d(p).getBoolean(STARTED)) { dayTick(p); travelTick(p); }
        }
        Reckoning r = RECKONING.get(p.getUUID());
        if (r != null) r.tick(p);
    }

    private static void setMod(Player p, Attribute a, UUID id, String name, double amount, AttributeModifier.Operation op) {
        AttributeInstance inst = p.getAttribute(a);
        if (inst == null) return;
        AttributeModifier cur = inst.getModifier(id);
        if (amount == 0) { if (cur != null) inst.removeModifier(id); return; }
        if (cur != null && cur.getAmount() == amount) return;
        if (cur != null) inst.removeModifier(id);
        inst.addTransientModifier(new AttributeModifier(id, name, amount, op));
        if (a == Attributes.MAX_HEALTH && p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
    }

    /** The first time the grimoire is held: the hunter's level and the Daily Trial begin. */
    static void start(ServerPlayer p) {
        CompoundTag t = d(p);
        if (t.getBoolean(STARTED)) return;
        t.putBoolean(STARTED, true);
        if (!t.contains(LVL)) t.putInt(LVL, 1);
        t.putLong(DAY, day(p));
        newTrial(p);
    }

    // ================================================================== the Daily Trial (one in-game day each)
    static long day(ServerPlayer p) { return p.server.overworld().getDayTime() / 24000L; }

    static void newTrial(ServerPlayer p) {
        CompoundTag t = d(p);
        ListTag l = new ListTag();
        List<Integer> kinds = new ArrayList<>(List.of(0, 1, 2, 3));
        java.util.Collections.shuffle(kinds, new java.util.Random(p.getRandom().nextLong()));
        int lvl = level(p);
        for (int i = 0; i < 3; i++) {
            int k = kinds.get(i);
            int goal = switch (k) { case 0 -> 12 + lvl / 4; case 1 -> 200 + lvl * 5; case 2 -> 600; default -> 2; };
            CompoundTag c = new CompoundTag();
            c.putInt("k", k); c.putInt("goal", goal); c.putInt("prog", 0);
            l.add(c);
        }
        t.put(DAILY, l);
        t.putBoolean(DONE, false);
    }

    static void progress(ServerPlayer p, int kind, int amount) {
        CompoundTag t = d(p);
        if (!t.getBoolean(STARTED) || t.getBoolean(DONE) || amount <= 0) return;
        ListTag l = t.getList(DAILY, Tag.TAG_COMPOUND);
        boolean all = !l.isEmpty();
        for (Tag x : l) {
            CompoundTag c = (CompoundTag) x;
            if (c.getInt("k") == kind) c.putInt("prog", Math.min(c.getInt("goal"), c.getInt("prog") + amount));
            if (c.getInt("prog") < c.getInt("goal")) all = false;
        }
        if (all) {   // the trial is complete
            t.putBoolean(DONE, true);
            t.putInt(PTS, t.getInt(PTS) + 5);
            gainXp(p, xpNext(level(p)) / 2);
            addMarks(p, 200);
            boolean lure = p.getRandom().nextFloat() < 0.2f;
            if (lure) t.putInt(LURE, t.getInt(LURE) + 1);
            p.displayClientMessage(Component.literal("Daily Trial complete  -  +5 stat points, +200 Shade Marks" + (lure ? ", a Remnant Lure" : "")).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
            p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.6f, 1.0f);
        }
    }

    private static final Map<UUID, Vec3> LAST = new HashMap<>();

    static void travelTick(ServerPlayer p) {
        Vec3 now = p.position(), was = LAST.put(p.getUUID(), now);
        if (was == null || !ShadowFx.equipped(p) || p.isPassenger()) return;
        double dd = Math.sqrt((now.x - was.x) * (now.x - was.x) + (now.z - was.z) * (now.z - was.z));
        if (dd < 40) progress(p, 2, (int) Math.round(dd));   // a teleport isn't travel
    }

    static void dayTick(ServerPlayer p) {
        CompoundTag t = d(p);
        long today = day(p);
        if (t.getLong(DAY) == today) return;
        boolean missed = !t.getBoolean(DONE);
        t.putLong(DAY, today);
        newTrial(p);
        if (missed) {
            t.putBoolean(DUE, true);
            p.displayClientMessage(Component.literal("Yesterday's trial was left undone - the Reckoning comes").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), true);
            AstaFx.later(200, () -> { if (p.isAlive() && !p.isRemoved()) startReckoning(p); });
        } else p.displayClientMessage(Component.literal("A new Daily Trial - cast Status").withStyle(ChatFormatting.AQUA), true);
    }

    // ================================================================== the Reckoning
    private static final Map<UUID, Reckoning> RECKONING = new HashMap<>();
    public static boolean inReckoning(Player p) { return RECKONING.containsKey(p.getUUID()); }

    public static void startReckoning(ServerPlayer p) {
        if (RECKONING.containsKey(p.getUUID())) return;
        d(p).putBoolean(DUE, false);
        Reckoning r = new Reckoning(p, false);
        RECKONING.put(p.getUUID(), r);
        p.displayClientMessage(Component.literal("THE RECKONING  -  survive two minutes").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), true);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.4f, 1.4f);
    }

    /** Job Change: the trial (hunter level 40, no job yet, not already in a hunt). */
    public static void startJobTrial(ServerPlayer p) {
        if (RECKONING.containsKey(p.getUUID()) || level(p) < JOB_LEVEL || job(p) != 0 || d(p).getBoolean(JOB_READY)) return;
        RECKONING.put(p.getUUID(), new Reckoning(p, true));
        p.closeContainer();
        p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(5, 50, 15));
        p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(Component.literal("JOB CHANGE TRIAL").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD)));
        p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(Component.literal("Slay every foe - and the champion - within 3 minutes").withStyle(ChatFormatting.GRAY)));
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.4f, 1.8f);
    }

    static final class Reckoning {
        static final int LENGTH = 2400, WAVE = 400, TRIAL_LENGTH = 3600, TRIAL_WAVE = 300, TRIAL_WAVES = 4;
        final long start;
        final boolean trial;   // the Job Change Trial: three waves and a champion, all to be slain in time
        final List<Mob> mobs = new ArrayList<>();
        final ServerBossEvent bar;
        int wave;

        Reckoning(ServerPlayer p, boolean trial) {
            this.trial = trial;
            start = p.serverLevel().getGameTime();
            bar = trial ? new ServerBossEvent(Component.literal("Job Change Trial"), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10)
                        : new ServerBossEvent(Component.literal("The Reckoning"), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_6);
            bar.addPlayer(p);
        }

        void tick(ServerPlayer p) {
            long age = p.serverLevel().getGameTime() - start;
            int length = trial ? TRIAL_LENGTH : LENGTH;
            bar.setProgress(Math.max(0, 1 - age / (float) length));
            if (!p.isAlive()) { end(p, false); return; }
            if (trial) {
                if (wave >= TRIAL_WAVES && mobs.stream().noneMatch(Mob::isAlive)) { end(p, true); return; }
                if (age >= length) { end(p, false); return; }
                if (age >= (long) wave * TRIAL_WAVE && wave < TRIAL_WAVES) { if (wave == TRIAL_WAVES - 1) champion(p); else spawnWave(p, wave + 2); wave++; }
                if (age % 40 == 0) for (Mob m : mobs) if (m.isAlive() && m.getTarget() != p) m.setTarget(p);
                return;
            }
            if (age >= length) { end(p, true); return; }
            if (age >= (long) wave * WAVE && wave < LENGTH / WAVE) spawnWave(p, wave++);
            if (age % 40 == 0) for (Mob m : mobs) if (m.isAlive() && m.getTarget() != p) m.setTarget(p);
        }

        void spawnWave(ServerPlayer p, int w) {
            ServerLevel sl = p.serverLevel();
            EntityType<?>[] kinds = w < 2 ? new EntityType<?>[]{EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER}
                    : w < 4 ? new EntityType<?>[]{EntityType.HUSK, EntityType.STRAY, EntityType.CAVE_SPIDER, EntityType.ZOMBIE}
                    : new EntityType<?>[]{EntityType.WITHER_SKELETON, EntityType.HUSK, EntityType.STRAY, EntityType.VINDICATOR};
            int n = 2 + w + level(p) / 25;
            double hpMul = 0.5 + 0.08 * w + level(p) * 0.01;
            for (int i = 0; i < n; i++) {
                double a = p.getRandom().nextDouble() * Math.PI * 2, r = 8 + p.getRandom().nextDouble() * 6;
                int x = (int) Math.floor(p.getX() + Math.cos(a) * r), z = (int) Math.floor(p.getZ() + Math.sin(a) * r);
                BlockPos at = sl.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
                if (Math.abs(at.getY() - p.getY()) > 8) at = BlockPos.containing(x, p.getY(), z);
                Mob m = (Mob) kinds[p.getRandom().nextInt(kinds.length)].create(sl);
                if (m == null) continue;
                m.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, p.getRandom().nextFloat() * 360, 0);
                m.finalizeSpawn(sl, sl.getCurrentDifficultyAt(at), MobSpawnType.EVENT, null, null);
                m.getPersistentData().putBoolean("isg_reckoning", true);
                if (trial) m.getPersistentData().putBoolean("isg_no_reward", true);
                AttributeInstance hp = m.getAttribute(Attributes.MAX_HEALTH);
                if (hp != null) { hp.addPermanentModifier(new AttributeModifier("Reckoning", hpMul, AttributeModifier.Operation.MULTIPLY_BASE)); m.setHealth(m.getMaxHealth()); }
                AttributeInstance dmg = m.getAttribute(Attributes.ATTACK_DAMAGE);
                if (dmg != null) dmg.addPermanentModifier(new AttributeModifier("Reckoning", 0.25 + 0.05 * w, AttributeModifier.Operation.MULTIPLY_BASE));
                m.setPersistenceRequired();
                m.setTarget(p);
                sl.addFreshEntity(m);
                mobs.add(m);
                ShadowFx.risePool(sl, m.position(), 0.9f);
            }
            sl.playSound(null, p.blockPosition(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 0.5f, 1.6f);
        }

        /** The trial's champion: a wither skeleton, six times its health, harder blows, on its own bar. */
        void champion(ServerPlayer p) {
            ServerLevel sl = p.serverLevel();
            Vec3 at = p.position().add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(9));
            BlockPos bp = sl.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(at));
            if (Math.abs(bp.getY() - p.getY()) > 8) bp = BlockPos.containing(at.x, p.getY(), at.z);
            Mob m = EntityType.WITHER_SKELETON.create(sl);
            if (m == null) return;
            m.moveTo(bp.getX() + 0.5, bp.getY(), bp.getZ() + 0.5, 0, 0);
            m.finalizeSpawn(sl, sl.getCurrentDifficultyAt(bp), MobSpawnType.EVENT, null, null);
            m.setCustomName(Component.literal("Trial Champion").withStyle(ChatFormatting.LIGHT_PURPLE));
            m.getPersistentData().putBoolean("isg_reckoning", true);
            m.getPersistentData().putBoolean("isg_no_reward", true);
            AttributeInstance hp = m.getAttribute(Attributes.MAX_HEALTH);
            if (hp != null) { hp.addPermanentModifier(new AttributeModifier("Trial", 5.0 + level(p) * 0.03, AttributeModifier.Operation.MULTIPLY_BASE)); m.setHealth(m.getMaxHealth()); }
            AttributeInstance dmg = m.getAttribute(Attributes.ATTACK_DAMAGE);
            if (dmg != null) dmg.addPermanentModifier(new AttributeModifier("Trial", 0.6, AttributeModifier.Operation.MULTIPLY_BASE));
            m.setPersistenceRequired();
            m.setTarget(p);
            sl.addFreshEntity(m);
            mobs.add(m);
            ShadowFx.risePool(sl, m.position(), 1.4f);
            sl.playSound(null, m.blockPosition(), SoundEvents.WITHER_SKELETON_AMBIENT, SoundSource.HOSTILE, 1.2f, 0.6f);
            p.displayClientMessage(Component.literal("The Trial Champion has risen").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD), true);
        }

        void end(ServerPlayer p, boolean survived) {
            RECKONING.remove(p.getUUID());
            bar.removeAllPlayers();
            for (Mob m : mobs) if (m.isAlive()) { ShadowFx.sinkPool(p.serverLevel(), m.position(), 0.9f); m.discard(); }
            if (trial) {
                if (survived) {
                    d(p).putBoolean(JOB_READY, true);
                    p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8f, 1.0f);
                    p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(Component.literal("TRIAL PASSED").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD)));
                    p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(Component.literal("Choose your job").withStyle(ChatFormatting.GRAY)));
                    sendStatus(p, true, true);   // straight to the choice
                } else p.displayClientMessage(Component.literal("Job Change Trial failed  -  try again from Status").withStyle(ChatFormatting.DARK_GRAY), true);
                return;
            }
            if (survived) {
                addMarks(p, 150);
                gainXp(p, xpNext(level(p)) / 3);
                p.displayClientMessage(Component.literal("You survived the Reckoning  -  +150 Shade Marks").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
                p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.6f, 0.8f);
            } else p.displayClientMessage(Component.literal("The Reckoning has ended").withStyle(ChatFormatting.DARK_GRAY), true);
        }
    }

    // ================================================================== Status, the Market
    public static void openStatus(ServerPlayer p, float power) { start(p); sendStatus(p, true); }
    public static void openMarket(ServerPlayer p, float power) { start(p); AddonNetwork.send(PacketDistributor.PLAYER.with(() -> p), new ShadowPackets.Market(marks(p), true)); }

    static void sendStatus(ServerPlayer p, boolean open) { sendStatus(p, open, false); }

    /** openJob: open the job choice instead (the trial was just passed). */
    static void sendStatus(ServerPlayer p, boolean open, boolean openJob) {
        CompoundTag t = d(p);
        int[] st = new int[STATS.length];
        for (int i = 0; i < st.length; i++) st[i] = stat(p, i);
        ListTag l = t.getList(DAILY, Tag.TAG_COMPOUND);
        int[] k = new int[l.size()], prog = new int[l.size()], goal = new int[l.size()];
        for (int i = 0; i < l.size(); i++) { CompoundTag c = l.getCompound(i); k[i] = c.getInt("k"); prog[i] = c.getInt("prog"); goal[i] = c.getInt("goal"); }
        long dayTime = p.server.overworld().getDayTime() % 24000L;
        AddonNetwork.send(PacketDistributor.PLAYER.with(() -> p), new ShadowPackets.Status(open, level(p), t.getInt(XP), xpNext(level(p)), t.getInt(PTS), st,
                marks(p), ShadowArmy.count(p), t.getInt(LURE), k, prog, goal, t.getBoolean(DONE), (int) ((24000L - dayTime) / 20L), inReckoning(p),
                job(p), t.getBoolean(JOB_READY), openJob));
    }

    /** The Market's wares: 0 Nightfang, 1 Mana Tonic, 2 Shade Salve, 3 Remnant Lure, 4 Stat Reset, 5 Reckoning Key,
     *  6 Dragon Fang, 7 Duskglass Kris. */
    public static final int[] PRICES = {500, 40, 60, 120, 800, 300, 1000, 900, 950, 1100, 1050, 1600};

    static void buy(ServerPlayer p, int i) {
        if (i < 0 || i >= PRICES.length) return;
        if (marks(p) < PRICES[i]) { p.displayClientMessage(Component.literal("Not enough Shade Marks").withStyle(ChatFormatting.GRAY), true); return; }
        CompoundTag t = d(p);
        switch (i) {
            case 0 -> give(p, new ItemStack(AddonItemRegistry.NIGHTFANG.get()));
            case 1 -> give(p, new ItemStack(AddonItemRegistry.MANA_TONIC.get()));
            case 2 -> give(p, new ItemStack(AddonItemRegistry.SHADE_SALVE.get()));
            case 6 -> give(p, new ItemStack(AddonItemRegistry.DRAGON_FANG.get()));
            case 7 -> give(p, new ItemStack(AddonItemRegistry.DUSKGLASS_KRIS.get()));
            case 8 -> give(p, new ItemStack(AddonItemRegistry.GILDED_THORN.get()));
            case 9 -> give(p, new ItemStack(AddonItemRegistry.BLOODREND.get()));
            case 10 -> give(p, new ItemStack(AddonItemRegistry.ROSEWRATH.get()));
            case 11 -> give(p, new ItemStack(AddonItemRegistry.FROSTWING.get()));
            case 3 -> t.putInt(LURE, t.getInt(LURE) + 1);
            case 4 -> {
                int spent = 0;
                for (String s : STATS) { spent += t.getInt("isg_asc_" + s); t.putInt("isg_asc_" + s, 0); }
                if (spent == 0) { p.displayClientMessage(Component.literal("No points to refund").withStyle(ChatFormatting.GRAY), true); return; }
                t.putInt(PTS, t.getInt(PTS) + spent);
            }
            case 5 -> {
                if (inReckoning(p)) { p.displayClientMessage(Component.literal("You're already in a Reckoning").withStyle(ChatFormatting.GRAY), true); return; }
                AstaFx.later(40, () -> { if (p.isAlive() && !p.isRemoved()) startReckoning(p); });
            }
            default -> { }
        }
        addMarks(p, -PRICES[i]);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 0.7f);
        if (i == 5) p.closeContainer();
    }

    static void give(ServerPlayer p, ItemStack st) { if (!p.getInventory().add(st)) p.drop(st, false); }

    /** The windows' buttons (ShadowPackets.Act 10+): 10 status, 11 spend a point on stat id, 12 market, 13 buy ware id,
     *  14 start the Job Change Trial, 15 choose job id (1-3) once the trial is passed. */
    static void handle(ServerPlayer p, int action, int id) {
        if (!ShadowFx.equipped(p)) return;
        switch (action) {
            case 10 -> sendStatus(p, true);
            case 11 -> {
                CompoundTag t = d(p);
                if (id >= 0 && id < STATS.length && t.getInt(PTS) > 0) {
                    t.putInt(PTS, t.getInt(PTS) - 1);
                    t.putInt("isg_asc_" + STATS[id], t.getInt("isg_asc_" + STATS[id]) + 1);
                }
                sendStatus(p, false);
            }
            case 12 -> AddonNetwork.send(PacketDistributor.PLAYER.with(() -> p), new ShadowPackets.Market(marks(p), false));
            case 13 -> { buy(p, id); AddonNetwork.send(PacketDistributor.PLAYER.with(() -> p), new ShadowPackets.Market(marks(p), false)); }
            case 14 -> startJobTrial(p);
            case 15 -> {
                CompoundTag t = d(p);
                if (!t.getBoolean(JOB_READY) || job(p) != 0 || id < 1 || id >= JOBS.length) return;
                t.putInt(JOB, id); t.putBoolean(JOB_READY, false);
                p.closeContainer();
                p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(Component.literal(JOBS[id].toUpperCase()).withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD)));
                p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(Component.literal("Your job has changed").withStyle(ChatFormatting.GRAY)));
                p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.7f, 1.2f);
                FxSend.ring(p.serverLevel(), Fx.VOID | com.ironspellgrimoire.network.FxPacket.KEEP_SHAPE, p.position().add(0, 0.05, 0), 0.4f, 5f, 0.25f, 16);
            }
            default -> { }
        }
    }

    /** For the screenshots and tests. */
    public static void testGrant(ServerPlayer p, int levels, int marks) {
        start(p);
        for (int i = 0; i < levels; i++) gainXp(p, xpNext(level(p)));
        addMarks(p, marks);
    }

    /** For the tests: end a running Reckoning now (as survived or not); returns how many mobs it had raised. */
    public static int testEndReckoning(ServerPlayer p, boolean survived) {
        Reckoning r = RECKONING.get(p.getUUID());
        if (r == null) return -1;
        int n = r.mobs.size();
        r.end(p, survived);
        return n;
    }

    /** For the tests: whether p is in the Job Change Trial now. */
    static boolean testInTrial(ServerPlayer p) { Reckoning r = RECKONING.get(p.getUUID()); return r != null && r.trial; }

    /** For the screenshots: no job, the trial not yet passed. */
    public static void testJobReset(ServerPlayer p) { d(p).putInt(JOB, 0); d(p).putBoolean(JOB_READY, false); }

    /** For the screenshots: the trial passed, the choice open. */
    public static void testJobReady(ServerPlayer p) { d(p).putInt(JOB, 0); d(p).putBoolean(JOB_READY, true); sendStatus(p, true, true); }

    /** For the tests: forget a player's hunter data. */
    static void testReset(ServerPlayer p) {
        CompoundTag t = d(p);
        for (String k : new String[]{LVL, XP, PTS, MARKS, LURE, DAY, DAILY, DONE, DUE, STARTED, JOB, JOB_READY}) t.remove(k);
        for (String st : STATS) t.remove("isg_asc_" + st);
    }

    public static void clearAll() {
        for (Reckoning r : RECKONING.values()) { r.bar.removeAllPlayers(); for (Mob m : r.mobs) m.discard(); }
        RECKONING.clear(); LAST.clear();
    }
}
