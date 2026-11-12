package com.ironspellgrimoire.server;

import com.ironspellgrimoire.IronSpellGrimoire;
import com.ironspellgrimoire.entity.AddonBoss;
import com.ironspellgrimoire.network.AddonNetwork;
import com.ironspellgrimoire.network.FxPacket;
import com.ironspellgrimoire.registry.AddonEntities;
import com.ironspellgrimoire.registry.AddonItemRegistry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * The Elf Raid. Mages with a name for themselves draw the elves: a player with 40 or more merit stars who is in a
 * village as night falls may find the sky tearing open over it (one night in three, at most once every three days).
 * <ul>
 *   <li>A raid horn, ELF RAID across the screen, a red bar at the top: four waves come through rift-cuts round the
 *       village - possessed elves (hooded mages, tougher with each wave), more each time.</li>
 *   <li>The last wave is led by Licht - far stronger than any other: unbound, two and a half times his health, and his
 *       blows land 70% harder.</li>
 *   <li>Win and everyone who stood by the village is paid: merit stars, emeralds, two Elven Blade Shards, a Mana Core
 *       and an upgrade. If dawn comes first, or nobody stays to defend it for a minute, the elves fall back into the rift.</li>
 * </ul>
 * {@code /raid elf} starts one where you stand (ops).
 */
@Mod.EventBusSubscriber(modid = IronSpellGrimoire.MODID)
public final class ElfRaids {
    private ElfRaids() {}

    public static final int STARS_NEEDED = 40, SHAPE = 76;
    private static final int WAVES = 4;
    private static final UUID RAID_HP = UUID.fromString("e1f00001-0000-4000-8000-0000000000a1");
    private static final UUID LEADER_HP = UUID.fromString("e1f00001-0000-4000-8000-0000000000a2");

    static final class Raid {
        Vec3 centre;
        int wave, pause, alone;
        final List<UUID> mobs = new ArrayList<>();
        int waveSize;
        ServerBossEvent bar;
        UUID owner;
        boolean called;   // started by /raid elf: dawn doesn't end it
        int level;        // how many raids its mage has broken before (up to 5): every one comes back harder
    }

    private static final Map<String, Raid> RAIDS = new HashMap<>();   // by dimension + village

    private static String key(Level l, BlockPos c) { return l.dimension().location() + "@" + (c.getX() >> 5) + "," + (c.getZ() >> 5); }

    // ------------------------------------------------------------------ when it comes
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % 100 != 51 || p.isSpectator()) return;
        ServerLevel sl = p.serverLevel();
        if (!sl.dimension().equals(Level.OVERWORLD) || Bounties.stars(p) < STARS_NEEDED) return;
        long time = sl.getDayTime() % 24000;
        if (time < 13000 || time > 14500) return;   // only as night falls
        long day = sl.getDayTime() / 24000;
        if (p.getPersistentData().getLong("isg_raid_roll") == day + 1) return;   // one roll a night
        p.getPersistentData().putLong("isg_raid_roll", day + 1);
        if (sl.getGameTime() < p.getPersistentData().getLong("isg_raid_next")) return;
        StructureStart st = sl.structureManager().getStructureWithPieceAt(p.blockPosition(), StructureTags.VILLAGE);
        if (st == null || !st.isValid() || st.getPieces().isEmpty()) return;
        if (ThreadLocalRandom.current().nextInt(3) != 0) return;
        BoundingBox b = st.getPieces().get(0).getBoundingBox();
        int cx = (b.minX() + b.maxX()) / 2, cz = (b.minZ() + b.maxZ()) / 2;
        start(p, new Vec3(cx + 0.5, sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx, cz), cz + 0.5));
    }

    public static void start(ServerPlayer p, Vec3 centre) { start(p, centre, false); }

    public static void start(ServerPlayer p, Vec3 centre, boolean called) {
        ServerLevel sl = p.serverLevel();
        String k = key(sl, BlockPos.containing(centre));
        if (RAIDS.containsKey(k)) return;
        p.getPersistentData().putLong("isg_raid_next", sl.getGameTime() + 72000);
        Raid r = new Raid();
        r.centre = centre;
        r.owner = p.getUUID();
        r.called = called;
        r.level = Math.min(5, p.getPersistentData().getInt("isg_raids_won"));
        r.pause = 80;
        r.bar = new ServerBossEvent(Component.literal("Elf Raid").withStyle(ChatFormatting.RED), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
        r.bar.setProgress(1f);
        RAIDS.put(k, r);
        for (ServerPlayer o : near(sl, r)) {
            r.bar.addPlayer(o);
            FxPacket pk = new FxPacket();
            pk.shape = SHAPE; pk.life = 70; pk.sweep = 0;
            AddonNetwork.send(PacketDistributor.PLAYER.with(() -> o), pk);
            o.sendSystemMessage(Component.literal("ELF RAID  ").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
                    .append(Component.literal("The sky tears open over the village - the elves have come for its mages. Defend it till the last wave falls.").withStyle(ChatFormatting.GRAY)));
        }
        sl.playSound(null, BlockPos.containing(centre), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 64f, 0.8f);
        FxSend.beam(sl, Fx.CRIMSON | FxPacket.KEEP_SHAPE, centre, centre.add(0, 80, 0), 1.6f, 60);
        RiftFx.cut(sl, centre.add(0, 30, 0), new Vec3(0, 1, 0), new Vec3(1, 0, 0), 8f, 80);
    }

    private static List<ServerPlayer> near(ServerLevel sl, Raid r) {
        List<ServerPlayer> l = new ArrayList<>();
        for (ServerPlayer o : sl.players()) if (o.position().distanceToSqr(r.centre) < 80 * 80 && !o.isSpectator()) l.add(o);
        return l;
    }

    // ------------------------------------------------------------------ the raid
    @SubscribeEvent
    public static void onTick(TickEvent.LevelTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.level instanceof ServerLevel sl) || RAIDS.isEmpty() || sl.getGameTime() % 10 != 0) return;
        for (Map.Entry<String, Raid> en : new ArrayList<>(RAIDS.entrySet())) {
            if (!en.getKey().startsWith(sl.dimension().location() + "@")) continue;
            Raid r = en.getValue();
            List<ServerPlayer> here = near(sl, r);
            if (sl.getGameTime() % 40 < 10) {   // the villagers hear the horn and hide indoors (as from their bell) till it's over
                for (net.minecraft.world.entity.npc.Villager v : sl.getEntitiesOfClass(net.minecraft.world.entity.npc.Villager.class, new net.minecraft.world.phys.AABB(r.centre, r.centre).inflate(64)))
                    v.getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.HEARD_BELL_TIME, sl.getGameTime());
            }
            for (ServerPlayer o : here) if (!r.bar.getPlayers().contains(o)) r.bar.addPlayer(o);
            for (ServerPlayer o : new ArrayList<>(r.bar.getPlayers())) if (!here.contains(o)) r.bar.removePlayer(o);
            long time = sl.getDayTime() % 24000;
            r.alone = here.isEmpty() ? r.alone + 10 : 0;
            if (r.alone > 1200 || (!r.called && (time > 23000 || time < 12000))) { fail(sl, en.getKey(), r); continue; }
            int alive = 0;
            for (UUID u : r.mobs) { Entity m = sl.getEntity(u); if (m != null && m.isAlive()) alive++; }
            if (r.wave > 0 && r.waveSize > 0) r.bar.setProgress(Math.max(0f, alive / (float) r.waveSize));
            if (r.pause > 0) { r.pause -= 10; if (r.pause <= 0) wave(sl, r); continue; }
            if (alive == 0 && r.wave > 0) {
                if (r.wave >= WAVES) { win(sl, en.getKey(), r, here); continue; }
                r.pause = 100;   // a breath between waves
                r.bar.setName(Component.literal("Elf Raid - wave " + (r.wave + 1) + " is coming").withStyle(ChatFormatting.RED));
                r.bar.setProgress(1f);
                for (ServerPlayer o : here) o.displayClientMessage(Component.literal("The wave breaks... the rift opens again.").withStyle(ChatFormatting.RED), true);
            }
        }
    }

    private static void wave(ServerLevel sl, Raid r) {
        r.wave++;
        r.mobs.clear();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int elves = r.wave == WAVES ? 4 : 3 + r.wave * 3;
        double hp = 1.2 + r.wave * 0.35 + r.level * 0.15;
        for (int i = 0; i < elves; i++) {   // through rift-cuts round the village
            double a = (i % 4) * Math.PI / 2 + rnd.nextDouble(-0.4, 0.4), d = rnd.nextDouble(18, 28);
            Mob m = spawn(sl, AddonEntities.MAGE.get().create(sl), r.centre.add(Math.cos(a) * d, 0, Math.sin(a) * d));
            if (m == null) continue;
            m.setCustomName(Component.literal("Possessed Elf").withStyle(ChatFormatting.LIGHT_PURPLE));
            var h = m.getAttribute(Attributes.MAX_HEALTH);
            if (h != null && h.getModifier(RAID_HP) == null) { h.addPermanentModifier(new AttributeModifier(RAID_HP, "Elf Raid", hp - 1, AttributeModifier.Operation.MULTIPLY_TOTAL)); m.setHealth(m.getMaxHealth()); }
            r.mobs.add(m.getUUID());
        }
        if (r.wave == WAVES) {   // the leader
            Mob l = spawn(sl, AddonEntities.LICHT.get().create(sl), r.centre.add(0, 0, 6));
            if (l != null) {
                l.getPersistentData().putBoolean("isg_unbound", true);
                l.getPersistentData().putBoolean("isg_raid_leader", true);
                var h = l.getAttribute(Attributes.MAX_HEALTH);
                if (h != null && h.getModifier(LEADER_HP) == null) h.addPermanentModifier(new AttributeModifier(LEADER_HP, "Raid leader", 1.5 + r.level * 0.2, AttributeModifier.Operation.MULTIPLY_TOTAL));
                l.setHealth(l.getMaxHealth());
                if (l instanceof AddonBoss ab) ab.setBarName(Component.literal("Licht, Leader of the Raid").withStyle(ChatFormatting.DARK_RED));
                r.mobs.add(l.getUUID());
                FxSend.impactFrame(sl, l.position().add(0, 1.5, 0), 5, FxSend.FRAME_LIGHT, 48);
                for (ServerPlayer o : near(sl, r)) o.sendSystemMessage(Component.literal("Licht: ").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD)
                        .append(Component.literal("You've made a name for yourself, mage. My people have come to take it.").withStyle(ChatFormatting.WHITE, ChatFormatting.ITALIC)));
            }
        }
        r.waveSize = r.mobs.size();
        for (ServerPlayer o : near(sl, r)) {   // the wave, across the screen
            FxPacket pk = new FxPacket();
            pk.shape = SHAPE; pk.life = 45; pk.sweep = 2; pk.h = r.wave;
            AddonNetwork.send(PacketDistributor.PLAYER.with(() -> o), pk);
        }
        r.bar.setName(Component.literal("Elf Raid" + (r.level > 0 ? " " + roman(r.level + 1) : "") + " - wave " + r.wave + "/" + WAVES).withStyle(ChatFormatting.RED));
        r.bar.setProgress(1f);
        sl.playSound(null, BlockPos.containing(r.centre), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 48f, 0.9f + r.wave * 0.05f);
    }

    private static Mob spawn(ServerLevel sl, Mob m, Vec3 at) {
        if (m == null) return null;
        int x = (int) Math.floor(at.x), z = (int) Math.floor(at.z);
        if (!sl.hasChunk(x >> 4, z >> 4)) return null;
        Vec3 g = new Vec3(x + 0.5, sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z + 0.5);
        m.moveTo(g.x, g.y, g.z, ThreadLocalRandom.current().nextFloat() * 360f, 0);
        m.finalizeSpawn(sl, sl.getCurrentDifficultyAt(m.blockPosition()), MobSpawnType.EVENT, null, null);
        m.getPersistentData().putBoolean("isg_elf_raid", true);
        sl.addFreshEntity(m);
        RiftFx.cut(sl, g.add(0, 1.4, 0), new Vec3(0, 1, 0), new Vec3(1, 0, 0), 1.6f, 20);
        FxSend.ring(sl, Fx.CRIMSON | FxPacket.KEEP_SHAPE, g.add(0, 0.05, 0), 0.3f, 1.8f, 0.06f, 14);
        return m;
    }

    private static void win(ServerLevel sl, String k, Raid r, List<ServerPlayer> here) {
        RAIDS.remove(k);
        r.bar.removeAllPlayers();
        sl.playSound(null, BlockPos.containing(r.centre), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 2f, 0.9f);
        FxSend.ring(sl, Fx.GOLD | FxPacket.KEEP_SHAPE, r.centre.add(0, 0.2, 0), 1f, 30f, 0.3f, 30);
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        spoils(sl, r, here.size());
        for (ServerPlayer o : here) {
            int won = o.getPersistentData().getInt("isg_raids_won") + 1;
            o.getPersistentData().putInt("isg_raids_won", won);
            int stars = 12 + r.level * 2;
            List<String> got = new ArrayList<>();
            give(o, new ItemStack(Items.EMERALD, 24 + r.level * 8)); got.add((24 + r.level * 8) + " emeralds");
            give(o, new ItemStack(AddonItemRegistry.ELVEN_BLADE_SHARD.get(), 2)); got.add("two Elven Blade Shards");
            give(o, new ItemStack(AddonItemRegistry.MANA_CORE.get(), 1 + r.level / 2)); got.add("Mana Core" + (r.level >= 2 ? "s" : ""));
            var ups = AddonItemRegistry.UPGRADES;
            give(o, new ItemStack(ups.get(rnd.nextInt(ups.size() - 1)).get())); got.add("an upgrade");
            // the raid's own spoils, found nowhere else
            if (won == 1 || rnd.nextInt(100) < 35) { give(o, new ItemStack(AddonItemRegistry.ELF_KINGS_LEAF.get())); got.add("the Elf King's Leaf"); }
            if (rnd.nextInt(100) < 40 + r.level * 10) {
                ItemStack ink = new ItemStack(AddonItemRegistry.RIFT_INK.get()); ink.getOrCreateTag().putInt("Rule", 10);
                give(o, ink); got.add("Elven Ink");
            }
            if (won == 2 || rnd.nextInt(100) < 25) { give(o, new ItemStack(AddonItemRegistry.WORLD_TREE_LEAF.get())); got.add("a World Tree Leaf"); }
            if (won == 3 || rnd.nextInt(100) < 25) { give(o, new ItemStack(AddonItemRegistry.ELVEN_WIND_CHARM.get())); got.add("an Elven Wind Charm"); }
            Bounties.addStars(o, stars);
            GrimoireMastery.bountyXp(o, 1200 + r.level * 300);
            FxPacket pk = new FxPacket();
            pk.shape = SHAPE; pk.life = 60; pk.sweep = 1;
            AddonNetwork.send(PacketDistributor.PLAYER.with(() -> o), pk);
            o.sendSystemMessage(Component.literal("THE RAID IS BROKEN  ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                    .append(Component.literal("+" + stars + " merit stars, " + String.join(", ", got) + ".").withStyle(ChatFormatting.YELLOW)));
            o.sendSystemMessage(Component.literal("  Raids broken: " + won + (won < 6 ? " - the next will come back harder, and pay more." : "")).withStyle(ChatFormatting.GRAY));
        }
    }

    /** The village's thanks: a chest of spoils left at its meeting place for everyone who stood with it. */
    private static void spoils(ServerLevel sl, Raid r, int defenders) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        BlockPos at = null;
        BlockPos c = BlockPos.containing(r.centre);
        search:
        for (int d = 1; d <= 8; d++) for (int k = 0; k < 12; k++) {
            double a = k * Math.PI / 6;
            int x = c.getX() + (int) Math.round(Math.cos(a) * d), z = c.getZ() + (int) Math.round(Math.sin(a) * d);
            if (!sl.hasChunk(x >> 4, z >> 4)) continue;
            BlockPos p = new BlockPos(x, sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            if (sl.getBlockState(p).isAir() && sl.getBlockState(p.below()).isFaceSturdy(sl, p.below(), net.minecraft.core.Direction.UP)
                    && sl.getBlockState(p.below()).getFluidState().isEmpty()) { at = p; break search; }
        }
        if (at == null) return;
        sl.setBlock(at, net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState(), 3);
        if (!(sl.getBlockEntity(at) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chest)) return;
        chest.setCustomName(Component.literal("Raid Spoils").withStyle(ChatFormatting.GOLD));
        List<ItemStack> loot = new ArrayList<>();
        loot.add(new ItemStack(Items.EMERALD, Math.min(64, 12 + defenders * 8 + r.level * 4)));
        loot.add(new ItemStack(Items.GOLDEN_CARROT, 8 + rnd.nextInt(8)));
        loot.add(new ItemStack(Items.BREAD, 12));
        loot.add(new ItemStack(Items.EXPERIENCE_BOTTLE, 4 + r.level * 2));
        loot.add(new ItemStack(AddonItemRegistry.MANA_CORE.get(), 1 + r.level / 2));
        ItemStack ink = new ItemStack(AddonItemRegistry.RIFT_INK.get()); ink.getOrCreateTag().putInt("Rule", 4 + rnd.nextInt(6)); loot.add(ink);
        if (r.level >= 2) loot.add(new ItemStack(AddonItemRegistry.ELVEN_BLADE_SHARD.get(), 2));
        for (int i = 0; i < loot.size(); i++) chest.setItem(i * 3 % 27, loot.get(i));
        Vec3 v = Vec3.atCenterOf(at);
        FxSend.ring(sl, Fx.GOLD | FxPacket.KEEP_SHAPE, v.add(0, -0.4, 0), 0.3f, 2.5f, 0.08f, 20);
        FxSend.beam(sl, Fx.GOLD | FxPacket.KEEP_SHAPE, v, v.add(0, 8, 0), 0.3f, 30);
        for (ServerPlayer o : near(sl, r)) o.sendSystemMessage(Component.literal("  The village leaves its thanks in a chest of Raid Spoils by the meeting place.").withStyle(ChatFormatting.GOLD));
    }

    private static String roman(int n) { return new String[]{"", "I", "II", "III", "IV", "V", "VI"}[Math.max(0, Math.min(6, n))]; }

    private static void fail(ServerLevel sl, String k, Raid r) {
        RAIDS.remove(k);
        r.bar.removeAllPlayers();
        for (UUID u : r.mobs) {   // they fall back into the rift
            Entity m = sl.getEntity(u);
            if (m != null) { RiftFx.cut(sl, m.position().add(0, 1.2, 0), new Vec3(0, 1, 0), new Vec3(1, 0, 0), 1.2f, 14); m.discard(); }
        }
        for (ServerPlayer o : near(sl, r)) o.sendSystemMessage(Component.literal("The elves fall back into the rift... this time.").withStyle(ChatFormatting.GRAY));
    }

    private static void give(ServerPlayer p, ItemStack s) { if (!p.getInventory().add(s)) p.drop(s, false); }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("raid").requires(s -> s.hasPermission(2))
                .then(Commands.literal("elf").executes(c -> { ServerPlayer p = c.getSource().getPlayerOrException(); start(p, p.position(), true); return 1; })));
    }
}
