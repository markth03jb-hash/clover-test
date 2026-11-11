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
 * </ul>
 * Two days to finish it. Done quests pay emeralds, mastery XP for the equipped grimoire and merit stars - sometimes a
 * Rift Ink, a Devil Shard or a Mana Core. Sneak and right-click the board to give a quest up.
 */
@Mod.EventBusSubscriber(modid = IronSpellGrimoire.MODID)
public final class Quests {
    private Quests() {}

    public static final int MAGES = 0, MONSTERS = 1, RIFT = 2, BOSS = 3;
    public static final String[] TITLES = {"Hooded Mages", "Monster Hunt", "Rift Watch", "Great Beast"};
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
        int type = r.nextInt(Bounties.stars(p) >= 10 ? 4 : 3);
        double a = r.nextDouble(Math.PI * 2), d = r.nextDouble(150, 350);
        q = new CompoundTag();
        q.putInt("Type", type);
        q.putInt("State", TRAVEL);
        q.putInt("X", (int) (pos.getX() + Math.cos(a) * d));
        q.putInt("Z", (int) (pos.getZ() + Math.sin(a) * d));
        q.putString("Dim", sl.dimension().location().toString());
        q.putLong("Deadline", now + LIMIT);
        q.putInt("Id", r.nextInt(1, Integer.MAX_VALUE));
        p.getPersistentData().put(TAG, q);
        sl.playSound(null, p.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1f, 0.9f);
        sl.playSound(null, p.blockPosition(), SoundEvents.VILLAGER_WORK_CARTOGRAPHER, SoundSource.PLAYERS, 1f, 1f);
        double dx = q.getInt("X") - p.getX(), dz = q.getInt("Z") - p.getZ();
        p.sendSystemMessage(Component.literal("QUEST: ").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
                .append(Component.literal(TITLES[type]).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD))
                .append(Component.literal("  " + brief(type)).withStyle(ChatFormatting.GRAY)));
        p.sendSystemMessage(Component.literal("About " + (int) Math.hypot(dx, dz) + " blocks " + Bounties.direction(dx, dz)
                + ". Two days - the quest tracker and the blue pillar show the way.").withStyle(ChatFormatting.DARK_GRAY));
        sync(p);
    }

    private static String brief(int type) {
        return switch (type) {
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
            clear(p, q);
            p.sendSystemMessage(Component.literal("Your quest ran out of time: " + TITLES[q.getInt("Type")] + ".").withStyle(ChatFormatting.GRAY));
            return;
        }
        if (q.getInt("State") == TRAVEL && sl.dimension().location().toString().equals(q.getString("Dim"))) {
            double dx = q.getInt("X") + 0.5 - p.getX(), dz = q.getInt("Z") + 0.5 - p.getZ();
            if (dx * dx + dz * dz < 28 * 28) arrive(p, sl, q);
        }
        // keep the count true, whoever (or whatever) brought them down - a friend, a golem, lava, while you were away:
        // near the place (where their chunks are loaded) the quest counts the foes still standing
        if (q.getInt("State") == FIGHT && q.getInt("Type") != RIFT && p.tickCount % 40 == 7 && sl.dimension().location().toString().equals(q.getString("Dim"))) {
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
        switch (type) {
            case MAGES -> {
                int n = 6 + r.nextInt(4);
                for (int i = 0; i < n; i++) spawn(sl, p, q, AddonEntities.MAGE.get(), at, 3 + r.nextDouble(5), mobs, 1.0);
                p.sendSystemMessage(Component.literal("The hooded mages turn on you!").withStyle(ChatFormatting.AQUA));
            }
            case MONSTERS -> {
                int n = 10 + r.nextInt(5);
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
        q.putInt("Need", type == MAGES || type == MONSTERS ? mobs.size() : 1);
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
        boolean big = type == RIFT || type == BOSS;
        int emeralds = big ? 12 + r.nextInt(8) : 6 + r.nextInt(6);
        int stars = big ? 3 : 1;
        give(p, new ItemStack(Items.EMERALD, emeralds));
        GrimoireMastery.bountyXp(p, big ? 500 : 200 + r.nextInt(100));
        Bounties.addStars(p, stars);
        String extra = "";
        if (r.nextInt(big ? 2 : 4) == 0) {
            ItemStack s = switch (r.nextInt(3)) {
                case 0 -> { ItemStack ink = new ItemStack(AddonItemRegistry.RIFT_INK.get()); ink.getOrCreateTag().putInt("Rule", r.nextInt(4)); yield ink; }
                case 1 -> new ItemStack(AddonItemRegistry.DEVIL_SHARD.get());
                default -> new ItemStack(AddonItemRegistry.MANA_CORE.get());
            };
            give(p, s);
            extra = ", " + s.getHoverName().getString();
        }
        p.getPersistentData().remove(TAG);
        p.getPersistentData().putLong(NEXT, p.serverLevel().getGameTime() + 1200);
        ServerLevel sl = p.serverLevel();
        FxSend.ring(sl, Fx.WATER | FxPacket.KEEP_SHAPE, p.position().add(0, 0.05, 0), 0.3f, 2.6f, 0.06f, 14);
        sl.playSound(null, p.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8f, 1.2f);
        p.sendSystemMessage(Component.literal("QUEST COMPLETE  ").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
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
        }
        AddonNetwork.send(PacketDistributor.PLAYER.with(() -> p), k);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) { if (e.getEntity() instanceof ServerPlayer p) sync(p); }
}
