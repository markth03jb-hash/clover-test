package com.ironspellgrimoire.server;

import com.ironspellgrimoire.IronSpellGrimoire;
import com.ironspellgrimoire.network.AddonNetwork;
import com.ironspellgrimoire.network.FxPacket;
import com.ironspellgrimoire.registry.AddonEntities;
import com.ironspellgrimoire.registry.AddonItemRegistry;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * Wanted Posters: bounties on the bosses, run like a quest.
 * <ul>
 *   <li><b>Getting one:</b> ring a village bell. If you have no bounty and your last one ended over a day ago, the bell
 *       hands you a Wanted Poster: a boss, where it was sighted (400-900 blocks away), one or two modifiers, and three
 *       days to find it.</li>
 *   <li><b>Finding it:</b> the quest tracker and the compass bar at the top of the screen lead you there
 *       (client/BountyHud); within a few hundred blocks a pillar of red light marks the spot. When you get close, the
 *       boss is there.</li>
 *   <li><b>Modifiers:</b> Enraged (faster, hits harder), Armoured (heavy armour), Escorted (Hooded Mages with him),
 *       Wounded (starts at 70% health, but the reward is smaller).</li>
 *   <li><b>Reward:</b> merit stars, emeralds, mastery XP for your grimoire, sometimes a Devil Shard, and the poster
 *       comes back stamped SUBJUGATED with your name on it. Miss the deadline and the poster burns up - nothing else.</li>
 * </ul>
 * One bounty at a time, kept on the player (persistent data), so it survives logging out.
 */
@Mod.EventBusSubscriber(modid = IronSpellGrimoire.MODID)
public final class Bounties {
    private Bounties() {}

    public static final String[] BOSSES = {"zenon", "vetto", "rhya", "licht", "rades", "catherine"};
    public static final String[] NAMES = {"Zenon", "Vetto", "Rhya the Disloyal", "Licht", "Rades", "Catherine"};
    public static final int ENRAGED = 1, ARMOURED = 2, ESCORTED = 4, WOUNDED = 8;
    public static final String[] MOD_NAMES = {"Enraged", "Armoured", "Escorted", "Wounded"};
    public static final int STATE_HUNT = 0, STATE_FOUND = 1, STATE_DONE = 2, STATE_EXPIRED = 3;
    private static final String TAG = "isg_bounty", NEXT = "isg_bounty_next", STARS = "isg_merit_stars";
    private static final long DAY = 24000, LIMIT = 3 * DAY;
    private static final UUID ENRAGE = UUID.fromString("b0a7e000-0000-4000-8000-0000000000c1");
    private static final UUID ARMOR = UUID.fromString("b0a7e000-0000-4000-8000-0000000000c2");

    // ================================================================== the bounty itself
    private static CompoundTag bounty(ServerPlayer p) {
        CompoundTag t = p.getPersistentData().getCompound(TAG);
        return t.isEmpty() ? null : t;
    }

    public static int stars(ServerPlayer p) { return p.getPersistentData().getInt(STARS); }

    public static void addStars(ServerPlayer p, int n) { p.getPersistentData().putInt(STARS, stars(p) + n); }

    /** A new bounty for {@code p}, sighted somewhere 400-900 blocks from {@code from}. */
    static CompoundTag create(ServerPlayer p, Vec3 from, int boss) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double a = r.nextDouble(Math.PI * 2), d = r.nextDouble(400, 900);
        int mods = 1 << r.nextInt(4);
        if (r.nextInt(3) == 0) mods |= 1 << r.nextInt(4);
        if ((mods & WOUNDED) != 0 && (mods & ~WOUNDED) != 0 && r.nextBoolean()) mods &= ~WOUNDED;
        CompoundTag t = new CompoundTag();
        t.putInt("Id", r.nextInt(1, Integer.MAX_VALUE));
        t.putInt("Boss", boss);
        t.putInt("Mods", mods);
        t.putInt("X", (int) (from.x + Math.cos(a) * d));
        t.putInt("Z", (int) (from.z + Math.sin(a) * d));
        t.putString("Dim", p.level().dimension().location().toString());
        t.putLong("Deadline", p.level().getGameTime() + LIMIT);
        t.putInt("State", STATE_HUNT);
        p.getPersistentData().put(TAG, t);
        return t;
    }

    public static MutableComponent modsText(int mods) {
        MutableComponent c = Component.empty();
        boolean first = true;
        for (int i = 0; i < MOD_NAMES.length; i++) {
            if ((mods & (1 << i)) == 0) continue;
            if (!first) c.append(Component.literal(", ").withStyle(ChatFormatting.GRAY));
            c.append(Component.literal(MOD_NAMES[i]).withStyle(i == 3 ? ChatFormatting.GREEN : ChatFormatting.RED));
            first = false;
        }
        return c;
    }

    public static String direction(double dx, double dz) {
        String[] dirs = {"south", "south-west", "west", "north-west", "north", "north-east", "east", "south-east"};
        double ang = Math.toDegrees(Math.atan2(-dx, dz));   // Minecraft yaw: 0 = south
        int i = (int) Math.floorMod(Math.round(ang / 45.0), 8);
        return dirs[i];
    }

    /** The poster for a bounty: everything about it is written on the item. */
    static ItemStack poster(CompoundTag b) {
        ItemStack s = new ItemStack(AddonItemRegistry.WANTED_POSTER.get());
        s.getOrCreateTag().put("Bounty", b.copy());
        return s;
    }

    // ================================================================== client sync (FxPacket shape 67 -> client/BountyHud)
    static void sync(ServerPlayer p) {
        CompoundTag b = bounty(p);
        FxPacket k = new FxPacket();
        k.shape = 67;
        if (b != null) {
            k.id = b.getInt("Id");
            k.sweep = b.getInt("Boss") + 1;
            k.h = b.getInt("Mods");
            k.a0 = b.getInt("State");
            k.c = new Vec3(b.getInt("X"), 0, b.getInt("Z"));
            k.life = (int) Math.max(0, b.getLong("Deadline") - p.level().getGameTime());
            k.a1 = stars(p);
        } else k.a1 = stars(p);
        if (p.connection != null) AddonNetwork.send(PacketDistributor.PLAYER.with(() -> p), k);
    }

    // ================================================================== ringing a bell
    @SubscribeEvent
    public static void onUse(PlayerInteractEvent.RightClickBlock e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || e.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) return;
        if (!p.level().getBlockState(e.getPos()).is(Blocks.BELL)) return;
        long now = p.level().getGameTime();
        CompoundTag b = bounty(p);
        if (b != null) {
            p.displayClientMessage(Component.literal("You're already hunting " + NAMES[b.getInt("Boss")] + ".").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        long next = p.getPersistentData().getLong(NEXT);
        if (now < next) {
            long h = (next - now) / 1000;
            p.displayClientMessage(Component.literal("No new posters yet - come back in about " + Math.max(1, h) + " h.").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        give(p, Vec3.atCenterOf(e.getPos()), ThreadLocalRandom.current().nextInt(BOSSES.length));
    }

    static void give(ServerPlayer p, Vec3 from, int boss) {
        CompoundTag b = create(p, from, boss);
        ItemStack poster = poster(b);
        if (!p.getInventory().add(poster)) p.drop(poster, false);
        ServerLevel sl = p.serverLevel();
        sl.playSound(null, p.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1f, 0.8f);
        sl.playSound(null, p.blockPosition(), SoundEvents.VILLAGER_WORK_CARTOGRAPHER, SoundSource.PLAYERS, 1f, 1f);
        double dx = b.getInt("X") - p.getX(), dz = b.getInt("Z") - p.getZ();
        p.sendSystemMessage(Component.literal("WANTED: ").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
                .append(Component.literal(NAMES[boss]).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                .append(Component.literal("  (").withStyle(ChatFormatting.GRAY)).append(modsText(b.getInt("Mods"))).append(Component.literal(")").withStyle(ChatFormatting.GRAY)));
        p.sendSystemMessage(Component.literal("Last sighted about " + (int) Math.hypot(dx, dz) + " blocks " + direction(dx, dz)
                + ". Three days to bring him down - follow the compass at the top of your screen.").withStyle(ChatFormatting.GRAY));
        sync(p);
    }

    // ================================================================== the hunt
    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % 20 != 0) return;
        CompoundTag b = bounty(p);
        if (b == null) return;
        ServerLevel sl = p.serverLevel();
        long now = sl.getGameTime();
        int state = b.getInt("State");
        if (state == STATE_DONE || state == STATE_EXPIRED) return;
        if (now > b.getLong("Deadline")) { expire(p, b); return; }
        if (state == STATE_HUNT && sl.dimension().location().toString().equals(b.getString("Dim"))) {
            double dx = b.getInt("X") + 0.5 - p.getX(), dz = b.getInt("Z") + 0.5 - p.getZ();
            if (dx * dx + dz * dz < 40 * 40) spawn(p, sl, b);
        }
        if (p.tickCount % 200 == 0) sync(p);
    }

    private static void spawn(ServerPlayer p, ServerLevel sl, CompoundTag b) {
        int x = b.getInt("X"), z = b.getInt("Z");
        int y = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        int boss = b.getInt("Boss"), mods = b.getInt("Mods");
        EntityType<? extends Mob> type = switch (boss) {
            case 0 -> AddonEntities.ZENON.get();
            case 1 -> AddonEntities.VETTO.get();
            case 2 -> AddonEntities.RHYA.get();
            case 3 -> AddonEntities.LICHT.get();
            case 4 -> AddonEntities.RADES.get();
            default -> AddonEntities.CATHERINE.get();
        };
        Mob m = type.create(sl);
        if (m == null) return;
        m.moveTo(x + 0.5, y, z + 0.5, ThreadLocalRandom.current().nextFloat() * 360f, 0);
        m.finalizeSpawn(sl, sl.getCurrentDifficultyAt(m.blockPosition()), MobSpawnType.EVENT, null, null);
        m.getPersistentData().putUUID("isg_bounty_owner", p.getUUID());
        m.getPersistentData().putInt("isg_bounty_mods", mods);
        m.getPersistentData().putInt("isg_bounty_id", b.getInt("Id"));
        if ((mods & ENRAGED) != 0) {
            AttributeInstance sp = m.getAttribute(Attributes.MOVEMENT_SPEED);
            if (sp != null && sp.getModifier(ENRAGE) == null) sp.addPermanentModifier(new AttributeModifier(ENRAGE, "Bounty: enraged", 0.3, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
        if ((mods & ARMOURED) != 0) {
            AttributeInstance ar = m.getAttribute(Attributes.ARMOR);
            if (ar != null && ar.getModifier(ARMOR) == null) ar.addPermanentModifier(new AttributeModifier(ARMOR, "Bounty: armoured", 10, AttributeModifier.Operation.ADDITION));
        }
        sl.addFreshEntity(m);
        if ((mods & WOUNDED) != 0) m.setHealth(m.getMaxHealth() * 0.7f);
        if ((mods & ESCORTED) != 0) {
            for (int i = 0; i < 3; i++) {
                Mob g = AddonEntities.MAGE.get().create(sl);
                if (g == null) continue;
                double a = i * Math.PI * 2 / 3;
                int gx = (int) Math.floor(x + Math.cos(a) * 4), gz = (int) Math.floor(z + Math.sin(a) * 4);
                g.moveTo(gx + 0.5, sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, gx, gz), gz + 0.5, 0, 0);
                g.finalizeSpawn(sl, sl.getCurrentDifficultyAt(g.blockPosition()), MobSpawnType.EVENT, null, null);
                g.setPersistenceRequired();
                sl.addFreshEntity(g);
            }
        }
        b.putInt("State", STATE_FOUND);
        b.putUUID("BossUUID", m.getUUID());
        Vec3 at = m.position();
        FxSend.ring(sl, 18, at.add(0, 0.1, 0), 0.5f, 8f, 0.2f, 16);
        FxSend.shake(sl, at, 0.6f, 12, 40);
        sl.playSound(null, m.blockPosition(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 2f, 1f);
        p.displayClientMessage(Component.literal("You found " + NAMES[boss] + "!").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), true);
        sync(p);
    }

    /** Bosses on a poster hit harder when Enraged. */
    public static float damageMult(Entity boss) {
        return (boss.getPersistentData().getInt("isg_bounty_mods") & ENRAGED) != 0 ? 1.3f : 1f;
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent e) {
        LivingEntity m = e.getEntity();
        if (m.level().isClientSide || !m.getPersistentData().hasUUID("isg_bounty_owner")) return;
        var server = m.level().getServer();
        if (server == null) return;
        ServerPlayer p = server.getPlayerList().getPlayer(m.getPersistentData().getUUID("isg_bounty_owner"));
        if (p == null) return;
        CompoundTag b = bounty(p);
        if (b == null || b.getInt("Id") != m.getPersistentData().getInt("isg_bounty_id")) return;
        complete(p, b);
    }

    private static void complete(ServerPlayer p, CompoundTag b) {
        int mods = b.getInt("Mods"), boss = b.getInt("Boss");
        int hard = Integer.bitCount(mods & ~WOUNDED);
        boolean wounded = (mods & WOUNDED) != 0;
        int stars = 3 + hard * 2 - (wounded ? 1 : 0);
        p.getPersistentData().putInt(STARS, stars(p) + stars);
        ItemStack trophy = poster(b);
        CompoundTag pt = trophy.getOrCreateTag().getCompound("Bounty");
        pt.putInt("State", STATE_DONE);
        pt.putString("Hunter", p.getGameProfile().getName());
        trophy.getOrCreateTag().put("Bounty", pt);
        removePoster(p, b.getInt("Id"));
        giveItem(p, trophy);
        giveItem(p, new ItemStack(Items.EMERALD, 8 + hard * 6 - (wounded ? 3 : 0)));
        if (ThreadLocalRandom.current().nextInt(3) == 0 || hard >= 2) giveItem(p, new ItemStack(AddonItemRegistry.DEVIL_SHARD.get()));
        GrimoireMastery.bountyXp(p, 600 + hard * 300);
        p.getPersistentData().remove(TAG);
        p.getPersistentData().putLong(NEXT, p.level().getGameTime() + DAY);
        ServerLevel sl = p.serverLevel();
        sl.playSound(null, p.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1f, 1f);
        p.sendSystemMessage(Component.literal("SUBJUGATED: ").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                .append(Component.literal(NAMES[boss]).withStyle(ChatFormatting.GOLD))
                .append(Component.literal("  +" + stars + " merit stars (" + stars(p) + " total)").withStyle(ChatFormatting.YELLOW)));
        FxPacket k = new FxPacket();   // the tracker shows "Subjugated" for a moment, then clears
        k.shape = 67; k.id = b.getInt("Id"); k.sweep = boss + 1; k.h = mods; k.a0 = STATE_DONE; k.a1 = stars(p); k.life = 0;
        k.c = new Vec3(b.getInt("X"), 0, b.getInt("Z"));
        AddonNetwork.send(PacketDistributor.PLAYER.with(() -> p), k);
    }

    private static void expire(ServerPlayer p, CompoundTag b) {
        ServerLevel sl = p.serverLevel();
        if (b.hasUUID("BossUUID")) {   // he moves on
            Entity m = sl.getEntity(b.getUUID("BossUUID"));
            if (m != null) {
                FxSend.ring(sl, 18, m.position().add(0, 1, 0), 2f, 0.3f, 0.1f, 12);
                m.discard();
            }
        }
        removePoster(p, b.getInt("Id"));
        p.getPersistentData().remove(TAG);
        p.getPersistentData().putLong(NEXT, sl.getGameTime() + DAY / 2);
        p.sendSystemMessage(Component.literal("The poster for " + NAMES[b.getInt("Boss")] + " crumbles to ash - the trail has gone cold.").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        sl.playSound(null, p.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.8f, 1f);
        FxPacket k = new FxPacket();
        k.shape = 67; k.id = b.getInt("Id"); k.sweep = b.getInt("Boss") + 1; k.a0 = STATE_EXPIRED; k.a1 = stars(p);
        AddonNetwork.send(PacketDistributor.PLAYER.with(() -> p), k);
    }

    /** The active poster burns up (or is replaced by its stamped trophy). */
    private static void removePoster(ServerPlayer p, int id) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(AddonItemRegistry.WANTED_POSTER.get()) && s.hasTag() && s.getTag().getCompound("Bounty").getInt("Id") == id
                    && s.getTag().getCompound("Bounty").getInt("State") < STATE_DONE) inv.setItem(i, ItemStack.EMPTY);
        }
    }

    private static void giveItem(ServerPlayer p, ItemStack s) { if (!p.getInventory().add(s)) p.drop(s, false); }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) { if (e.getEntity() instanceof ServerPlayer p) sync(p); }

    @SubscribeEvent
    public static void onChangeDim(PlayerEvent.PlayerChangedDimensionEvent e) { if (e.getEntity() instanceof ServerPlayer p) sync(p); }

    // ================================================================== /bounty (testing and admin)
    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("bounty").requires(s -> s.hasPermission(2))
                .then(Commands.literal("new").then(Commands.argument("boss", IntegerArgumentType.integer(0, 5)).executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    p.getPersistentData().remove(TAG);
                    give(p, p.position(), IntegerArgumentType.getInteger(c, "boss"));
                    return 1;
                })))
                .then(Commands.literal("near").executes(c -> {   // move the sighting next to you (testing)
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    CompoundTag b = bounty(p);
                    if (b == null) return 0;
                    b.putInt("X", (int) p.getX() + 60);
                    b.putInt("Z", (int) p.getZ());
                    sync(p);
                    return 1;
                }))
                .then(Commands.literal("clear").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    p.getPersistentData().remove(TAG);
                    p.getPersistentData().remove(NEXT);
                    FxPacket k = new FxPacket();
                    k.shape = 67; k.a1 = stars(p);
                    AddonNetwork.send(PacketDistributor.PLAYER.with(() -> p), k);
                    return 1;
                })));
    }
}
