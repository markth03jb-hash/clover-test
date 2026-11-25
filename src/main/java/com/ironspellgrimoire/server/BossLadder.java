package com.ironspellgrimoire.server;

import com.ironspellgrimoire.IronSpellGrimoire;
import com.ironspellgrimoire.network.AddonNetwork;
import com.ironspellgrimoire.network.BossLogPackets;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * The boss ladder: a player's way up through the bounty bosses, kept on the player (persistent data "isg_ladder").
 * <ul>
 *   <li><b>Tiers:</b> tier I (the Revenant, the Curse Witch) is open from the start; one tier I kill opens tier II (the
 *       Ossuarch, Rhya, the Beast of Despair); tier III (Licht and the poster with no name) opens after the Gate - a
 *       one-time trial on the Poster Board once you've brought down a tier II boss.</li>
 *   <li><b>Hardened:</b> after three kills of a boss, its posters are Hardened - more health, harder hits, the Reckoning
 *       from the start, a shorter fuse before it enrages - and pay twice, with the upgraded signature drop.</li>
 *   <li><b>Streak:</b> every bounty brought down in time adds one (+10% stars each, up to +50%); one that burns up or is
 *       torn up resets it.</li>
 *   <li><b>The log</b> (the Codex's Bosses chapter): kills, best time, falls, and what each boss remembers of you.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = IronSpellGrimoire.MODID)
public final class BossLadder {
    private BossLadder() {}

    public static final String TAG = "isg_ladder";
    public static final int BOSSES = 8, GATE = 99;
    /** Each bounty boss's tier (Bounties.BOSSES order: the Ossuarch, the Beast, Rhya, Licht, the Revenant, the Curse Witch, ???, the Ironbound Twins). */
    public static final int[] TIER = {2, 2, 2, 3, 1, 1, 3, 2};
    public static final String[] TIER_NAME = {"", "I", "II", "III"};
    public static final int HARDENED_AFTER = 3;
    /** How a boss was beaten, for its memory: 1 blades and fists, 2 spells, 3 arrows and thrown things. */
    public static final String[] HOW = {"", "melee", "spells", "projectiles"};

    // ------------------------------------------------------------------ the data
    public static CompoundTag data(ServerPlayer p) {
        CompoundTag t = p.getPersistentData().getCompound(TAG);
        if (!p.getPersistentData().contains(TAG)) p.getPersistentData().put(TAG, t);
        return t;
    }
    static int[] arr(CompoundTag t, String k) {
        int[] a = t.getIntArray(k);
        if (a.length != BOSSES) { int[] b = new int[BOSSES]; System.arraycopy(a, 0, b, 0, Math.min(a.length, BOSSES)); a = b; }
        return a;
    }
    public static int kills(ServerPlayer p, int boss) { return boss < 0 || boss >= BOSSES ? 0 : arr(data(p), "k")[boss]; }
    public static boolean gatePassed(ServerPlayer p) { return data(p).getBoolean("gate"); }
    public static int streak(ServerPlayer p) { return data(p).getInt("streak"); }
    public static int memory(ServerPlayer p, int boss) { return boss < 0 || boss >= BOSSES ? 0 : arr(data(p), "m")[boss]; }

    /** The highest tier of bounty this player may take. */
    public static int tier(ServerPlayer p) {
        int[] k = arr(data(p), "k");
        boolean one = false;
        for (int i = 0; i < BOSSES; i++) if (TIER[i] == 1 && k[i] > 0) one = true;
        if (!one) return 1;
        return gatePassed(p) ? 3 : 2;
    }
    /** Whether the Gate is on offer: a tier II boss down, the Gate not yet passed. */
    public static boolean gateOpen(ServerPlayer p) {
        if (gatePassed(p)) return false;
        int[] k = arr(data(p), "k");
        for (int i = 0; i < BOSSES; i++) if (TIER[i] == 2 && k[i] > 0) return true;
        return false;
    }
    public static boolean allowed(ServerPlayer p, int boss) { return boss >= 0 && boss < BOSSES && TIER[boss] <= tier(p); }
    public static boolean hardenedNext(ServerPlayer p, int boss) { return kills(p, boss) >= HARDENED_AFTER; }

    // ------------------------------------------------------------------ what happens
    /** A boss down, with this player in the fight: the log, and maybe a new tier. */
    static void killed(ServerPlayer p, int boss, long ticks, int how) {
        if (boss < 0 || boss >= BOSSES) return;
        int before = tier(p);
        CompoundTag t = data(p);
        int[] k = arr(t, "k"), b = arr(t, "b"), m = arr(t, "m");
        k[boss]++;
        if (ticks > 0 && (b[boss] == 0 || ticks < b[boss])) b[boss] = (int) Math.min(Integer.MAX_VALUE, ticks);
        if (how > 0) m[boss] = how;
        t.putIntArray("k", k); t.putIntArray("b", b); t.putIntArray("m", m);
        int after = tier(p);
        if (after > before) announceTier(p, after);
        else if (k[boss] == HARDENED_AFTER) p.sendSystemMessage(Component.literal("Word spreads of your hunts: " + Bounties.NAMES[boss] + "'s posters will now be Hardened - harder, and paying twice.").withStyle(ChatFormatting.GOLD));
        if (gateOpen(p) && TIER[boss] == 2 && k[boss] == 1)
            p.sendSystemMessage(Component.literal("THE GATE  ").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
                    .append(Component.literal("A Poster Board now offers the Gate - pass it and tier III opens.").withStyle(ChatFormatting.GRAY)));
        sync(p);
    }

    static void fell(ServerPlayer p, int boss) {
        if (boss < 0 || boss >= BOSSES) return;
        CompoundTag t = data(p);
        int[] d = arr(t, "d");
        d[boss]++;
        t.putIntArray("d", d);
        sync(p);
    }

    static void passGate(ServerPlayer p) {
        if (gatePassed(p)) return;
        data(p).putBoolean("gate", true);
        announceTier(p, 3);
        sync(p);
    }

    static void announceTier(ServerPlayer p, int tier) {
        p.sendSystemMessage(Component.literal("TIER " + TIER_NAME[tier] + " OPEN  ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.literal(tier == 2 ? "The Ossuarch, Rhya, the Beast of Despair and the Ironbound Twins can now be on your posters."
                        : "Licht can now be on your posters - and, with 50 stars, the poster with no name.").withStyle(ChatFormatting.YELLOW)));
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1f, 0.8f);
    }

    /** A bounty done in time: the streak grows; the stars it pays, multiplied. */
    static float streakUp(ServerPlayer p) {
        int s = streak(p) + 1;
        data(p).putInt("streak", s);
        sync(p);
        return streakMult(s);
    }
    public static float streakMult(int s) { return 1f + 0.1f * Math.min(5, Math.max(0, s - 1)); }
    static void streakLost(ServerPlayer p) {
        if (streak(p) > 1) p.sendSystemMessage(Component.literal("Your bounty streak of " + streak(p) + " is broken.").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        data(p).putInt("streak", 0);
        sync(p);
    }

    // ------------------------------------------------------------------ to the client (the Codex)
    public static void sync(ServerPlayer p) {
        if (p.connection == null) return;
        CompoundTag t = data(p).copy();
        t.putInt("tier", tier(p));
        t.putBoolean("gateOpen", gateOpen(p));
        t.putInt("rank", EliteMobs.threat(p));
        t.putInt("danger", EliteMobs.dangerAt(p.serverLevel(), p.position()));
        AddonNetwork.send(PacketDistributor.PLAYER.with(() -> p), new BossLogPackets.Sync(t));
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) { if (e.getEntity() instanceof ServerPlayer p) sync(p); }

    /** Crossing into a more (or less) dangerous band of the land (EliteMobs.dangerAt): you're told, once. */
    @SubscribeEvent
    public static void onPlayerTick(net.minecraftforge.event.TickEvent.PlayerTickEvent e) {
        if (e.phase != net.minecraftforge.event.TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % 40 != 0) return;
        int band = EliteMobs.dangerAt(p.serverLevel(), p.position()), was = p.getPersistentData().getInt("isg_danger");
        if (band == was) return;
        p.getPersistentData().putInt("isg_danger", band);
        p.displayClientMessage(Component.literal(band > was ? "Danger rises: " + EliteMobs.DANGER_TEXT[band] + " - mobs here are at least rank " + band + "."
                : band == 0 ? "Safer ground: " + EliteMobs.DANGER_TEXT[0] + "." : "Safer ground: " + EliteMobs.DANGER_TEXT[band] + ".").withStyle(band > was ? ChatFormatting.RED : ChatFormatting.GREEN), true);
        sync(p);
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone e) {
        CompoundTag old = e.getOriginal().getPersistentData().getCompound(TAG);
        if (!old.isEmpty()) e.getEntity().getPersistentData().put(TAG, old.copy());
    }

    // ------------------------------------------------------------------ /ladder (testing and admin)
    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("ladder").requires(s -> s.hasPermission(2))
                .then(Commands.literal("kill").then(Commands.argument("boss", IntegerArgumentType.integer(0, BOSSES - 1)).executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    killed(p, IntegerArgumentType.getInteger(c, "boss"), 0, 0);
                    return 1;
                })))
                .then(Commands.literal("gate").executes(c -> { passGate(c.getSource().getPlayerOrException()); return 1; }))
                .then(Commands.literal("reset").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    p.getPersistentData().remove(TAG);
                    sync(p);
                    return 1;
                })));
    }

    // the game tests
    public static void testReset(ServerPlayer p) { p.getPersistentData().remove(TAG); }
    public static void testKill(ServerPlayer p, int boss, long ticks, int how) { killed(p, boss, ticks, how); }
    public static void testGate(ServerPlayer p) { passGate(p); }
}
