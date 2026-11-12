package com.ironspellgrimoire.server;

import com.ironspellgrimoire.IronSpellGrimoire;
import com.ironspellgrimoire.network.AddonNetwork;
import com.ironspellgrimoire.network.FxPacket;
import com.ironspellgrimoire.registry.AddonEntities;
import com.ironspellgrimoire.registry.AddonItemRegistry;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * Quests from the Quest Board - the village's own requests, one at a time. Every quest is a place: the board marks
 * where, 150-350 blocks away, and the quest tracker (client/QuestHud) points the way with a blue pillar of light over
 * it. When you get there, the trouble shows itself:
 * <ul>
 *   <li><b>Hooded Mages</b> - a band of 6-9 hooded mages camped there. Defeat them all.</li>
 *   <li><b>Monster Hunt</b> - a pack of 10-14 monsters (zombies, skeletons, spiders, husks, a witch). Defeat them all.</li>
 *   <li><b>Rift Watch</b> - a rift tears open there. Go in and reach its relic chamber.</li>
 *   <li><b>Great Beast</b> - one of the bosses is waiting (from 10 merit stars). Bring it down.</li>
 *   <li><b>Lost Grimoire Page</b> - a glowing page, guarded by elite mages. Take it, then hold the ground round it for
 *       thirty seconds while mages come for it (the clock only runs while you stay near). Pays the most mastery XP.</li>
 *   <li><b>Wandering Beast</b> - a great beast that keeps moving; the tracker follows it. One day. Fall too far behind
 *       it once found and it gets away to roam again.</li>
 *   <li><b>Daily Special</b> - once a day the first quest you take is the gold-pinned one: two twists (armoured,
 *       enraged, twice as many, no healing), double emeralds, stars and XP, and a Mana Core and a Rift Ink for sure.</li>
 * </ul>
 * Two days to finish it. Done quests pay emeralds, mastery XP for the equipped grimoire and merit stars - sometimes a
 * Rift Ink, a Devil Shard or a Mana Core. Sneak and right-click the board to give a quest up.
 */
@Mod.EventBusSubscriber(modid = IronSpellGrimoire.MODID)
public final class Quests {
    private Quests() {}

    public static final int MAGES = 0, MONSTERS = 1, RIFT = 2, BOSS = 3, PAGE = 4, BEAST = 5;
    public static final String[] TITLES = {"Hooded Mages", "Monster Hunt", "Rift Watch", "Great Beast", "Lost Grimoire Page", "Wandering Beast"};
    /** The Daily Special's twists (two of them, every day a new one). */
    public static final int D_ARMOURED = 1, D_ENRAGED = 2, D_DOUBLE = 4, D_NOHEAL = 8;
    public static final String[] DAILY_NAMES = {"Armoured", "Enraged", "Twice as many", "No healing"};
    private static final int HOLD = 30;   // seconds to hold the ground round a Lost Grimoire Page
    private static final java.util.UUID ARMOUR = java.util.UUID.fromString("b7d3c1a0-3f1e-4a7b-9c11-5e0d6a1001d1");
    private static final java.util.UUID RAGE = java.util.UUID.fromString("b7d3c1a0-3f1e-4a7b-9c11-5e0d6a1001d2");
    /** state: travelling to the place, fighting there, done. */
    public static final int TRAVEL = 1, FIGHT = 3, DONE = 2;
    public static final int SHAPE = 75;
    private static final String TAG = "isg_quest", NEXT = "isg_quest_next", MARK = "isg_quest_owner";
    private static final long LIMIT = 48000;

    private static CompoundTag quest(ServerPlayer p) {
        CompoundTag t = p.getPersistentData().getCompound(TAG);
        return t.isEmpty() ? null : t;
    }

    // ------------------------------------------------------------------ the board
    public static void fromBoard(ServerPlayer p, BlockPos pos) {
        CompoundTag q = quest(p);
        ServerLevel sl = p.serverLevel();
        if (q != null && p.isShiftKeyDown()) {   // give it up
            clear(p, q);
            p.getPersistentData().putLong(NEXT, sl.getGameTime() + 600);
            sl.playSound(null, p.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1f, 0.6f);
            p.displayClientMessage(Component.literal("You gave up the quest: " + TITLES[q.getInt("Type")] + ".").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (q != null) {
            p.displayClientMessage(Component.literal("Your quest: " + TITLES[q.getInt("Type")] + " - follow the tracker. Sneak + right-click the board to give it up.").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        long now = sl.getGameTime(), next = p.getPersistentData().getLong(NEXT);
        if (now < next) {
            p.displayClientMessage(Component.literal("Nothing new on the board yet - come back in a minute or two.").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int[] pool = Bounties.stars(p) >= 10 ? new int[]{MAGES, MONSTERS, RIFT, PAGE, BEAST, BOSS} : new int[]{MAGES, MONSTERS, RIFT, PAGE, BEAST};
        double a = r.nextDouble(Math.PI * 2), d = r.nextDouble(150, 350);
        CompoundTag o = new CompoundTag();
        o.putInt("Type", pool[r.nextInt(pool.length)]);
        if (dailyDue(p)) { o.putBoolean("Daily", true); o.putInt("Mods", rollDaily()); }
        o.putInt("X", (int) (pos.getX() + Math.cos(a) * d));
        o.putInt("Z", (int) (pos.getZ() + Math.sin(a) * d));
        takeOffer(p, o);
    }

    static boolean dailyDue(ServerPlayer p) { return p.getPersistentData().getLong("isg_quest_daily") != p.serverLevel().getGameTime() / 24000 + 1; }
    static int rollDaily() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int m1 = r.nextInt(4), m2;
        do m2 = r.nextInt(4); while (m2 == m1);
        return (1 << m1) | (1 << m2);
    }
    static boolean questing(ServerPlayer p) { return quest(p) != null; }
    static String questTitle(ServerPlayer p) { CompoundTag q = quest(p); return q == null ? "" : TITLES[q.getInt("Type")]; }
    static long nextAt(ServerPlayer p) { return p.getPersistentData().getLong(NEXT); }
    static String briefOf(int type) { return brief(type); }
    /** Gives up the current quest (from the board's menu). */
    static void giveUp(ServerPlayer p) {
        CompoundTag q = quest(p);
        if (q == null) return;
        ServerLevel sl = p.serverLevel();
        Boards.log(p, Boards.QUEST, Boards.GAVE_UP, TITLES[q.getInt("Type")], 0);
        clear(p, q);
        p.getPersistentData().putLong(NEXT, sl.getGameTime() + 600);
        sl.playSound(null, p.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1f, 0.6f);
        p.displayClientMessage(Component.literal("You gave up the quest: " + TITLES[q.getInt("Type")] + ".").withStyle(ChatFormatting.GRAY), true);
    }

    /** A quest chosen from the board (server/Boards): exactly the note it showed. */
    static void takeOffer(ServerPlayer p, CompoundTag o) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        ServerLevel sl = p.serverLevel();
        long now = sl.getGameTime();
        int type = o.getInt("Type");
        CompoundTag q = new CompoundTag();
        q.putInt("Type", type);
        q.putInt("State", TRAVEL);
        boolean daily = o.getBoolean("Daily") && dailyDue(p);   // the gold-pinned note: once a day
        if (daily) {
            p.getPersistentData().putLong("isg_quest_daily", now / 24000 + 1);
            q.putBoolean("Daily", true);
            q.putInt("Mods", o.getInt("Mods"));
        }
        q.putInt("X", o.getInt("X"));
        q.putInt("Z", o.getInt("Z"));
        q.putString("Dim", sl.dimension().location().toString());
        q.putLong("Deadline", now + (type == BEAST ? LIMIT / 2 : LIMIT));
        q.putFloat("Head", (float) r.nextDouble(Math.PI * 2));
        q.putInt("Id", r.nextInt(1, Integer.MAX_VALUE));
        p.getPersistentData().put(TAG, q);
        sl.playSound(null, p.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1f, 0.9f);
        sl.playSound(null, p.blockPosition(), SoundEvents.VILLAGER_WORK_CARTOGRAPHER, SoundSource.PLAYERS, 1f, 1f);
        double dx = q.getInt("X") - p.getX(), dz = q.getInt("Z") - p.getZ();
        p.sendSystemMessage(Component.literal(daily ? "DAILY SPECIAL: " : "QUEST: ").withStyle(daily ? ChatFormatting.GOLD : ChatFormatting.AQUA, ChatFormatting.BOLD)
                .append(Component.literal(TITLES[type]).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD))
                .append(Component.literal("  " + brief(type)).withStyle(ChatFormatting.GRAY)));
        if (daily) p.sendSystemMessage(Component.literal("  Twists: " + modsText(q.getInt("Mods")) + ". Double reward, and a Mana Core and a Rift Ink for sure.").withStyle(ChatFormatting.GOLD));
        p.sendSystemMessage(Component.literal("About " + (int) Math.hypot(dx, dz) + " blocks " + Bounties.direction(dx, dz)
                + (type == BEAST ? ". It won't wait - one day, and it keeps moving. The tracker follows it." : ". Two days - the quest tracker and the blue pillar show the way.")).withStyle(ChatFormatting.DARK_GRAY));
        sync(p);
    }

    public static String modsText(int mods) {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < DAILY_NAMES.length; i++) if ((mods & (1 << i)) != 0) s.append(s.length() > 0 ? ", " : "").append(DAILY_NAMES[i]);
        return s.toString();
    }

    private static String brief(int type) {
        return switch (type) {
            case PAGE -> "A page torn from a grimoire glows out there, and mages guard it. Take it, and hold the ground.";
            case BEAST -> "A great beast roams the wilds out there. Run it down before it gets away.";
            case MAGES -> "Hooded mages have made camp out there. Drive them off.";
            case MONSTERS -> "Monsters are gathering out there. Thin them out.";
            case RIFT -> "Something is about to tear open out there. Close the rift.";
            default -> "Something big has been seen out there. Bring it down.";
        };
    }

    // ------------------------------------------------------------------ getting there
    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % 20 != 7) return;
        CompoundTag q = quest(p);
        if (q == null) return;
        ServerLevel sl = p.serverLevel();
        long now = sl.getGameTime();
        if (q.getInt("State") == 0) { p.getPersistentData().remove(TAG); sync(p); return; }   // a quest from before quests had places
        if (now > q.getLong("Deadline")) {
            Boards.log(p, Boards.QUEST, Boards.EXPIRED, TITLES[q.getInt("Type")], 0);
            clear(p, q);
            p.sendSystemMessage(Component.literal("Your quest ran out of time: " + TITLES[q.getInt("Type")] + ".").withStyle(ChatFormatting.GRAY));
            return;
        }
        int type = q.getInt("Type");
        if (type == BEAST && q.getInt("State") == TRAVEL && p.tickCount % 60 == 7) wander(q);   // it keeps moving
        if (q.getInt("State") == TRAVEL && sl.dimension().location().toString().equals(q.getString("Dim"))) {
            double dx = q.getInt("X") + 0.5 - p.getX(), dz = q.getInt("Z") + 0.5 - p.getZ();
            double near = type == BEAST ? 40 : 28;
            if (dx * dx + dz * dz < near * near) arrive(p, sl, q);
            else if (type == BEAST && p.tickCount % 60 == 7) sync(p);
        }
        if (q.getInt("State") == FIGHT && type == PAGE) { page(p, sl, q); if (quest(p) == null) return; }
        if (q.getInt("State") == FIGHT && type == BEAST) { chase(p, sl, q); if (quest(p) == null) return; }
        // keep the count true, whoever (or whatever) brought them down - a friend, a golem, lava, while you were away:
        // near the place (where their chunks are loaded) the quest counts the foes still standing
        if (q.getInt("State") == FIGHT && (type == MAGES || type == MONSTERS || type == BOSS) && p.tickCount % 40 == 7 && sl.dimension().location().toString().equals(q.getString("Dim"))) {
            double dx = q.getInt("X") + 0.5 - p.getX(), dz = q.getInt("Z") + 0.5 - p.getZ();
            if (dx * dx + dz * dz < 64 * 64) {
                int alive = 0;
                for (Tag t : q.getList("Mobs", Tag.TAG_INT_ARRAY)) { Entity m = sl.getEntity(net.minecraft.nbt.NbtUtils.loadUUID(t)); if (m != null && m.isAlive()) alive++; }
                int have = Math.max(q.getInt("Have"), q.getInt("Need") - alive);
                if (have != q.getInt("Have")) { q.putInt("Have", have); sync(p); }
                if (alive == 0) { complete(p, q); return; }
            }
        }
        if (p.tickCount % 200 == 7) sync(p);
    }

    /** You've reached the place: the trouble shows itself. */
    private static void arrive(ServerPlayer p, ServerLevel sl, CompoundTag q) {
        int x = q.getInt("X"), z = q.getInt("Z");
        if (!sl.hasChunk(x >> 4, z >> 4)) return;
        int y = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        Vec3 at = new Vec3(x + 0.5, y, z + 0.5);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int type = q.getInt("Type");
        ListTag mobs = new ListTag();
        q.putInt("State", FIGHT);
        int mul = (q.getInt("Mods") & D_DOUBLE) != 0 ? 2 : 1;
        switch (type) {
            case PAGE -> {   // the page, hanging over the ground, and its guards
                q.putDouble("PX", at.x); q.putDouble("PY", at.y + 1.2); q.putDouble("PZ", at.z);
                for (int i = 0; i < 3 * mul; i++) spawn(sl, p, q, AddonEntities.MAGE.get(), at, 3 + r.nextDouble(2), mobs, 2.0);
                p.sendSystemMessage(Component.literal("Its guards rise to meet you. Take the page - then hold the ground.").withStyle(ChatFormatting.AQUA));
            }
            case BEAST -> {
                spawn(sl, p, q, EntityType.RAVAGER, at, 0, mobs, 2.2);
                p.sendSystemMessage(Component.literal("There it is - bring it down before it gets away!").withStyle(ChatFormatting.AQUA));
            }
            case MAGES -> {
                int n = (6 + r.nextInt(4)) * mul;
                for (int i = 0; i < n; i++) spawn(sl, p, q, AddonEntities.MAGE.get(), at, 3 + r.nextDouble(5), mobs, 1.0);
                p.sendSystemMessage(Component.literal("The hooded mages turn on you!").withStyle(ChatFormatting.AQUA));
            }
            case MONSTERS -> {
                int n = (10 + r.nextInt(5)) * mul;
                List<EntityType<? extends Mob>> kinds = List.of(EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.HUSK, EntityType.ZOMBIE, EntityType.SKELETON);
                for (int i = 0; i < n; i++) spawn(sl, p, q, kinds.get(r.nextInt(kinds.size())), at, 3 + r.nextDouble(8), mobs, 1.5);
                spawn(sl, p, q, EntityType.WITCH, at, 2, mobs, 1.5);
                p.sendSystemMessage(Component.literal("The pack has your scent!").withStyle(ChatFormatting.AQUA));
            }
            case RIFT -> {
                Rifts.Rift rift = Rifts.open(p, at, Math.max(2, Math.min(4, 1 + Bounties.stars(p) / 15)));
                q.putInt("Rift", rift.id);
                p.sendSystemMessage(Component.literal("A rift tears open! Step in and reach its relic chamber.").withStyle(ChatFormatting.AQUA));
            }
            default -> {
                EntityType<? extends Mob> t = switch (r.nextInt(6)) {
                    case 0 -> AddonEntities.ZENON.get();
                    case 1 -> AddonEntities.BEAST_OF_DESPAIR.get();
                    case 2 -> AddonEntities.RHYA.get();
                    case 3 -> AddonEntities.LICHT.get();
                    case 4 -> AddonEntities.REVENANT.get();
                    default -> AddonEntities.CURSE_WITCH.get();
                };
                spawn(sl, p, q, t, at, 0, mobs, 1.0);
            }
        }
        q.put("Mobs", mobs);
        q.putInt("Need", type == MAGES || type == MONSTERS ? mobs.size() : type == PAGE ? HOLD : 1);
        q.putInt("Have", 0);
        FxSend.ring(sl, Fx.WATER | FxPacket.KEEP_SHAPE, at.add(0, 0.1, 0), 0.5f, 8f, 0.2f, 18);
        sl.playSound(null, BlockPos.containing(at), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.HOSTILE, 1.4f, 0.8f);
        sync(p);
    }

    private static void spawn(ServerLevel sl, ServerPlayer p, CompoundTag q, EntityType<? extends Mob> type, Vec3 at, double rad, ListTag mobs, double hp) {
        Mob m = type.create(sl);
        if (m == null) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double a = r.nextDouble(Math.PI * 2);
        int x = (int) Math.floor(at.x + Math.cos(a) * rad), z = (int) Math.floor(at.z + Math.sin(a) * rad);
        m.moveTo(x + 0.5, sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z + 0.5, r.nextFloat() * 360f, 0);
        m.finalizeSpawn(sl, sl.getCurrentDifficultyAt(m.blockPosition()), MobSpawnType.EVENT, null, null);
        m.setPersistenceRequired();
        m.getPersistentData().putUUID(MARK, p.getUUID());
        m.getPersistentData().putInt("isg_quest_id", q.getInt("Id"));
        var h = m.getAttribute(Attributes.MAX_HEALTH);
        if (h != null && hp != 1.0) { h.setBaseValue(h.getBaseValue() * hp); m.setHealth(m.getMaxHealth()); }
        int mods = q.getInt("Mods");
        if ((mods & D_ARMOURED) != 0) { var ar = m.getAttribute(Attributes.ARMOR); if (ar != null && ar.getModifier(ARMOUR) == null) ar.addPermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(ARMOUR, "Daily: armoured", 10, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION)); }
        if ((mods & D_ENRAGED) != 0) { var sp = m.getAttribute(Attributes.MOVEMENT_SPEED); if (sp != null && sp.getModifier(RAGE) == null) sp.addPermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(RAGE, "Daily: enraged", 0.3, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_TOTAL)); }
        if (type == EntityType.RAVAGER) m.setCustomName(Component.literal("Wandering Beast").withStyle(ChatFormatting.GOLD));
        m.setTarget(p);
        sl.addFreshEntity(m);
        mobs.add(net.minecraft.nbt.NbtUtils.createUUID(m.getUUID()));
        FxSend.ring(sl, Fx.WATER | FxPacket.KEEP_SHAPE, m.position().add(0, 0.05, 0), 0.2f, 1.4f, 0.06f, 12);
    }

    /** Gone: the quest's foes vanish, the record is cleared. */
    private static void clear(ServerPlayer p, CompoundTag q) {
        ServerLevel sl = p.serverLevel();
        for (Tag t : q.getList("Mobs", Tag.TAG_INT_ARRAY)) {
            Entity m = sl.getEntity(net.minecraft.nbt.NbtUtils.loadUUID(t));
            if (m != null) m.discard();
        }
        p.getPersistentData().remove(TAG);
        sync(p);
    }

    // ------------------------------------------------------------------ the Wandering Beast
    /** Out of sight, it roams: a few blocks every three seconds, turning a little as it goes. */
    private static void wander(CompoundTag q) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        float head = q.getFloat("Head") + (float) r.nextDouble(-0.6, 0.6);
        q.putFloat("Head", head);
        q.putInt("X", (int) (q.getInt("X") + Math.cos(head) * 5));
        q.putInt("Z", (int) (q.getInt("Z") + Math.sin(head) * 5));
    }

    /** In sight: if you fall too far behind it for half a minute, it gets away - back to roaming, from where it was. */
    private static void chase(ServerPlayer p, ServerLevel sl, CompoundTag q) {
        ListTag l = q.getList("Mobs", Tag.TAG_INT_ARRAY);
        Entity b = l.isEmpty() ? null : sl.getEntity(net.minecraft.nbt.NbtUtils.loadUUID(l.get(0)));
        if (b == null || !b.isAlive()) return;
        q.putInt("X", (int) b.getX()); q.putInt("Z", (int) b.getZ());
        if (b.distanceToSqr(p) > 72 * 72) {
            int far = q.getInt("Far") + 1;
            q.putInt("Far", far);
            if (far >= 30) {
                b.discard();
                q.remove("Mobs");
                q.putInt("State", TRAVEL);
                q.putInt("Far", 0);
                p.sendSystemMessage(Component.literal("The Wandering Beast got away. It's roaming again - the tracker still follows it.").withStyle(ChatFormatting.GRAY));
                sync(p);
            }
        } else q.putInt("Far", 0);
    }

    // ------------------------------------------------------------------ the Lost Grimoire Page
    /** Each second: the page glows; touch it to take it; then hold the ground round it while mages come for it. */
    private static void page(ServerPlayer p, ServerLevel sl, CompoundTag q) {
        Vec3 pg = new Vec3(q.getDouble("PX"), q.getDouble("PY"), q.getDouble("PZ"));
        if (!sl.dimension().location().toString().equals(q.getString("Dim"))) return;
        boolean taken = q.getBoolean("Taken");
        // the page: a glint of gold, turning rings under it, a column of light while it's held
        LightFx.glint(sl, Fx.GOLD, pg, taken ? 1.0f : 0.7f, 22);
        FxSend.ring(sl, Fx.GOLD | FxPacket.KEEP_SHAPE, pg.add(0, -1.1, 0), 0.4f, 1.2f, 0.05f, 22);
        if (taken) {
            FxSend.ring(sl, Fx.WATER | FxPacket.KEEP_SHAPE, pg.add(0, -1.15, 0), 7.8f, 8f, 0.08f, 22);
            FxSend.beam(sl, Fx.GOLD | FxPacket.KEEP_SHAPE, pg.add(0, -1, 0), pg.add(0, 18, 0), 0.25f, 22);
        }
        double d2 = p.position().add(0, 1, 0).distanceToSqr(pg);
        if (!taken) {
            if (d2 < 2.5 * 2.5) {
                q.putBoolean("Taken", true);
                q.putInt("Have", 0);
                sl.playSound(null, BlockPos.containing(pg), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.4f, 0.7f);
                sl.playSound(null, BlockPos.containing(pg), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0f, 1.4f);
                p.sendSystemMessage(Component.literal("You have the page! Hold the ground round it for " + HOLD + " seconds.").withStyle(ChatFormatting.GOLD));
                wave(p, sl, q, pg);
                sync(p);
            }
            return;
        }
        if (d2 < 8 * 8) {   // the clock only runs while you hold the ground
            int have = q.getInt("Have") + 1;
            q.putInt("Have", have);
            if (have % 8 == 0) wave(p, sl, q, pg);
            if (have >= HOLD) { complete(p, q); return; }
            sync(p);
        } else if (p.tickCount % 60 == 7) p.displayClientMessage(Component.literal("Get back to the page - the clock stops while you're away!").withStyle(ChatFormatting.RED), true);
    }

    /** Mages come for the page from the edge of the ground. */
    private static void wave(ServerPlayer p, ServerLevel sl, CompoundTag q, Vec3 pg) {
        ListTag mobs = q.getList("Mobs", Tag.TAG_INT_ARRAY);
        int n = 2 * ((q.getInt("Mods") & D_DOUBLE) != 0 ? 2 : 1);
        Vec3 g = pg.add(0, -1.2, 0);
        for (int i = 0; i < n; i++) spawn(sl, p, q, AddonEntities.MAGE.get(), g, 10, mobs, 1.4);
        q.put("Mobs", mobs);
        sl.playSound(null, BlockPos.containing(pg), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.HOSTILE, 1.2f, 1.0f);
    }

    /** The Daily Special's No Healing: no healing while its fight is on. */
    @SubscribeEvent
    public static void onHeal(net.minecraftforge.event.entity.living.LivingHealEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        CompoundTag q = quest(p);
        if (q != null && q.getInt("State") == FIGHT && (q.getInt("Mods") & D_NOHEAL) != 0) e.setCanceled(true);
    }

    // ------------------------------------------------------------------ progress
    /** Any death of a quest's foe counts for the quest - it doesn't matter who or what landed the blow. */
    @SubscribeEvent
    public static void onKill(LivingDeathEvent e) {
        var t = e.getEntity();
        if (!t.getPersistentData().hasUUID(MARK) || !(t.level() instanceof ServerLevel sl)) return;
        ServerPlayer p = sl.getServer().getPlayerList().getPlayer(t.getPersistentData().getUUID(MARK));
        if (p == null) return;
        CompoundTag q = quest(p);
        if (q == null || q.getInt("Id") != t.getPersistentData().getInt("isg_quest_id") || q.getInt("State") != FIGHT) return;
        if (q.getInt("Type") == PAGE) return;   // the page's foes don't count: holding the ground does
        int have = q.getInt("Have") + 1;
        q.putInt("Have", have);
        if (have >= q.getInt("Need")) complete(p, q);
        else sync(p);
    }

    /** A rift's relic chamber was won (server/Rifts): if it's the quest's rift, the quest is done. */
    static void riftCleared(ServerPlayer p, int riftId) {
        CompoundTag q = quest(p);
        if (q == null || q.getInt("Type") != RIFT || q.getInt("State") != FIGHT || q.getInt("Rift") != riftId) return;
        q.putInt("Have", 1);
        complete(p, q);
    }

    private static void complete(ServerPlayer p, CompoundTag q) {
        int type = q.getInt("Type");
        ThreadLocalRandom r = ThreadLocalRandom.current();
        boolean big = type == RIFT || type == BOSS || type == BEAST || type == PAGE;
        boolean daily = q.getBoolean("Daily");
        int emeralds = (big ? 12 + r.nextInt(8) : 6 + r.nextInt(6)) * (daily ? 2 : 1);
        int stars = (big ? 3 : 1) * (daily ? 2 : 1) + GrimoireUpgrades.bonusStars(p);
        give(p, new ItemStack(Items.EMERALD, emeralds));
        GrimoireMastery.bountyXp(p, (type == PAGE ? 900 : big ? 500 : 200 + r.nextInt(100)) * (daily ? 2 : 1));   // a lost page teaches your grimoire most
        Bounties.addStars(p, stars);
        String extra = "";
        if (daily) {   // the Daily Special: a Mana Core and a Rift Ink for sure
            give(p, new ItemStack(AddonItemRegistry.MANA_CORE.get()));
            ItemStack ink = new ItemStack(AddonItemRegistry.RIFT_INK.get()); ink.getOrCreateTag().putInt("Rule", r.nextInt(10));
            give(p, ink);
            extra = ", a Mana Core, a Rift Ink";
            if (r.nextInt(3) == 0) { give(p, new ItemStack(AddonItemRegistry.PHOENIX_RIBBON.get())); extra += ", a Phoenix Ribbon"; }
            if (r.nextInt(4) == 0) { give(p, new ItemStack(AddonItemRegistry.STARLIT_SEAL.get())); extra += ", a Starlit Seal"; }
        }
        for (Tag t : q.getList("Mobs", Tag.TAG_INT_ARRAY)) {   // whatever is left of it fades away
            Entity m = p.serverLevel().getEntity(net.minecraft.nbt.NbtUtils.loadUUID(t));
            if (m != null && m.isAlive()) m.discard();
        }
        if (r.nextInt(big ? 3 : 4) != 0) {   // most quests leave something for your grimoire
            ItemStack s = switch (r.nextInt(big ? 11 : 10)) {
                case 0 -> { ItemStack ink = new ItemStack(AddonItemRegistry.RIFT_INK.get()); ink.getOrCreateTag().putInt("Rule", r.nextInt(10)); yield ink; }
                case 1 -> new ItemStack(AddonItemRegistry.RUNED_SPINE.get());
                case 2 -> new ItemStack(AddonItemRegistry.MANA_CORE.get());
                case 3 -> new ItemStack(AddonItemRegistry.MANA_CRYSTAL_BINDING.get());
                case 4 -> new ItemStack(AddonItemRegistry.SILVER_CLASP.get());
                case 5 -> new ItemStack(AddonItemRegistry.SWIFT_BOOKMARK.get());
                case 7 -> new ItemStack(AddonItemRegistry.WIND_FEATHER.get());
                case 8 -> new ItemStack(AddonItemRegistry.BLOODSTONE.get());
                case 9 -> new ItemStack(AddonItemRegistry.WARDING_GLYPH.get());
                case 10 -> new ItemStack(AddonItemRegistry.PHOENIX_RIBBON.get());   // rare: only from the big quests
                default -> { ItemStack ink = new ItemStack(AddonItemRegistry.RIFT_INK.get()); ink.getOrCreateTag().putInt("Rule", 4 + r.nextInt(6)); yield ink; }   // a rare ink
            };
            give(p, s);
            extra = ", " + s.getHoverName().getString();
        }
        Boards.log(p, Boards.QUEST, Boards.DONE, (daily ? "Daily: " : "") + TITLES[type], stars);
        p.getPersistentData().remove(TAG);
        p.getPersistentData().putLong(NEXT, p.serverLevel().getGameTime() + 1200);
        ServerLevel sl = p.serverLevel();
        FxSend.ring(sl, Fx.WATER | FxPacket.KEEP_SHAPE, p.position().add(0, 0.05, 0), 0.3f, 2.6f, 0.06f, 14);
        sl.playSound(null, p.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8f, 1.2f);
        p.sendSystemMessage(Component.literal(daily ? "DAILY SPECIAL COMPLETE  " : "QUEST COMPLETE  ").withStyle(daily ? ChatFormatting.GOLD : ChatFormatting.AQUA, ChatFormatting.BOLD)
                .append(Component.literal(TITLES[type] + ": " + emeralds + " emeralds, +" + stars + " merit star" + (stars > 1 ? "s" : "") + extra + ".").withStyle(ChatFormatting.YELLOW)));
        FxPacket k = new FxPacket();
        k.shape = SHAPE; k.life = DONE; k.sweep = type;
        AddonNetwork.send(PacketDistributor.PLAYER.with(() -> p), k);
    }

    private static void give(ServerPlayer p, ItemStack s) { if (!p.getInventory().add(s)) p.drop(s, false); }

    /** The tracker on the client: life = state (0 none), sweep = type, c = the place, a0/a1 = defeated/total. */
    public static void sync(ServerPlayer p) {
        CompoundTag q = quest(p);
        FxPacket k = new FxPacket();
        k.shape = SHAPE;
        if (q != null) {
            k.life = q.getInt("State"); k.sweep = q.getInt("Type"); k.a0 = q.getInt("Have"); k.a1 = q.getInt("Need");
            k.c = new Vec3(q.getInt("X") + 0.5, 0, q.getInt("Z") + 0.5);
            k.r = Math.max(0, q.getLong("Deadline") - p.serverLevel().getGameTime());
            k.h = q.getInt("Mods") | (q.getBoolean("Daily") ? 16 : 0) | (q.getBoolean("Taken") ? 32 : 0);
        }
        AddonNetwork.send(PacketDistributor.PLAYER.with(() -> p), k);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) { if (e.getEntity() instanceof ServerPlayer p) sync(p); }
}
