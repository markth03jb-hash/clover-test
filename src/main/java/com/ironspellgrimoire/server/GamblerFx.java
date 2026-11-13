package com.ironspellgrimoire.server;

import static com.ironspellgrimoire.server.FxUtil.*;

import com.ironspellgrimoire.network.FxPacket;
import com.ironspellgrimoire.registry.AddonSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The Gambler's Grimoire (an original grimoire): every page is a bet. A gold coin spins over your head each time you
 * cast - heads, the spell comes out lucky; tails, it doesn't (but you're owed). Roulette wheels, slot reels, dice and
 * chips do the rest. Drawn by client/GamblerFxClient (FxPacket shape 91): gold coins, green felt, red and black.
 *
 *  1 Coin Toss          - flick a heavy coin at your target. Heads: it ricochets on to two more foes
 *  2 Double or Nothing  - stake a tenth of your health. Heads: win it back twice over, and your next spell lands
 *                         double. Tails: it's gone - but you're owed two Luck
 *  3 Roulette           - a wheel spins under your target. Red: a burst of fire. Black: a curse - foes there take more
 *                         and move slower. Green (one in eight): the house pays out everything
 *  4 Chip Shot          - a stack of chips fanned out in front of you (heads: eight of them, tails: five)
 *  5 Lucky Seven        - seven coins rain onto your target's ground; each one that lands heads explodes
 *  6 Slot Machine       - three reels spin over your target and stop one by one: three of a kind pays big, three
 *                         sevens is the jackpot, a skull pays the least
 *  7 Loaded Dice        - two dice tumble at your foes: they hit for what they show; doubles roll again
 *  8 Insurance          - 10 s: any blow bigger than a third of your health is paid out by the house - you take a
 *                         quarter of it and get coins back
 *  9 All In (mode)      - 20 s: every flip is double or nothing - heads: the spell lands at 2.2x, tails: at 0.4x but it
 *                         heals you
 * 10 Jackpot            - a great slot machine opens over you and always pays; how much depends on your Luck: with
 *                         three Luck (or Surpassing Limits) it's three sevens and a fountain of gold
 *
 * Passive "Lady Luck": the coin on every spell. Every tails is a Luck owed you; the third makes the next flip heads.
 */
@Mod.EventBusSubscriber(modid = "iron_spell_grimoire")
public final class GamblerFx {
    private GamblerFx() {}

    public static final String MODE = "all_in", INSURED = "insurance";
    private static final UUID CURSED = UUID.fromString("e39fb4a0-8fc9-4106-9399-f2a6fd000001");
    private static final double TAU = Math.PI * 2;
    public static final int CHERRY = 0, BELL = 1, SEVEN = 2, BAR = 3, SKULL = 4;

    // ================================================================== the client shapes (GamblerFxClient, shape 91)
    private static FxPacket pk(int kind, Vec3 c, int life) {
        FxPacket p = new FxPacket();
        p.shape = 91; p.id = FxSend.nextId(); p.h = kind; p.c = c; p.life = life; p.a1 = (float) (Math.random() * 1000);
        return p;
    }
    private static void send(ServerLevel sl, FxPacket p) { FxSend.sendNear(sl, p.c, p); }

    static void flipFx(ServerLevel sl, LivingEntity over, boolean heads, int luck) { FxPacket p = pk(0, over.position(), 30); p.v = new Vec3(over.getId(), 1, 0); p.a0 = heads ? 1 : 0; p.r = luck; send(sl, p); }
    static void coin(ServerLevel sl, Vec3 c, Vec3 u, float size, int fly) { FxPacket p = pk(1, c, fly + 4); p.u = u; p.r = size; p.speed = fly; send(sl, p); }
    static void wheel(ServerLevel sl, Vec3 c, float r, int result, int spin, int life) { FxPacket p = pk(2, c, life); p.r = r; p.a0 = result; p.sweep = spin; send(sl, p); }
    static void chip(ServerLevel sl, Vec3 c, Vec3 u, int colour, int fly) { FxPacket p = pk(3, c, fly + 2); p.u = u; p.a0 = colour; p.speed = fly; send(sl, p); }
    static void reels(ServerLevel sl, Vec3 c, int[] sym, float size, int stop, int life) { FxPacket p = pk(4, c, life); p.a0 = sym[0] + sym[1] * 8 + sym[2] * 64; p.r = size; p.sweep = stop; send(sl, p); }
    static void dice(ServerLevel sl, Vec3 c, Vec3 u, int d1, int d2, int fly) { FxPacket p = pk(5, c, fly + 22); p.u = u; p.a0 = d1 + d2 * 8; p.speed = fly; send(sl, p); }
    static void payout(ServerLevel sl, Vec3 c, float r, int life) { FxPacket p = pk(6, c, life); p.r = r; send(sl, p); }
    static void aura(ServerLevel sl, ServerPlayer pl, int life) { FxPacket p = pk(7, pl.position(), life); p.v = new Vec3(pl.getId(), 1, 0); send(sl, p); }
    static void shield(ServerLevel sl, ServerPlayer pl, int life) { FxPacket p = pk(8, pl.position(), life); p.v = new Vec3(pl.getId(), 1, 0); send(sl, p); }
    static void machine(ServerLevel sl, Vec3 c, int[] sym, int stop, int life) { FxPacket p = pk(9, c, life); p.a0 = sym[0] + sym[1] * 8 + sym[2] * 64; p.sweep = stop; send(sl, p); }

    // ================================================================== luck
    static void play(ServerLevel sl, Vec3 at, String kind, float vol, float pitch) {
        SoundEvent s = AddonSounds.get(kind);
        if (s != null) sl.playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, vol, pitch);
    }

    private static final Map<UUID, Integer> LUCK = new HashMap<>();
    private static final Map<UUID, Long> DOUBLE = new HashMap<>();
    private static final Map<UUID, Long> CURSE = new HashMap<>();

    public static int luck(ServerPlayer p) { return LUCK.getOrDefault(p.getUUID(), 0); }
    public static void setLuck(ServerPlayer p, int v) { LUCK.put(p.getUUID(), v); }
    static boolean allIn(ServerPlayer p) { return Moves.inMode(p, MODE); }

    /** The coin: heads or tails, shown spinning over your head. Three tails owed makes the next one heads. */
    static boolean flip(ServerPlayer p) {
        int l = luck(p);
        boolean heads = l >= 3 || ThreadLocalRandom.current().nextBoolean();
        if (heads) LUCK.put(p.getUUID(), 0); else LUCK.put(p.getUUID(), l + 1);
        flipFx(p.serverLevel(), p, heads, luck(p));
        play(p.serverLevel(), p.position().add(0, 2.2, 0), "coin", 0.8f, heads ? 1.4f : 0.9f);
        return heads;
    }

    /** The spell's power after the flip: All In makes it double or nothing; a Double or Nothing win doubles the next. */
    static float stake(ServerPlayer p, float power, boolean heads) {
        float k = 1f;
        Long d = DOUBLE.remove(p.getUUID());
        if (d != null && p.serverLevel().getGameTime() - d < 400) k *= 2f;
        if (allIn(p)) {
            if (heads) k *= 2.2f;
            else { k *= 0.4f; p.heal(2f); }
        }
        return power * k;
    }

    static void hit(ServerPlayer p, LivingEntity t, float amt, String id) {
        Long c = CURSE.get(t.getUUID());
        if (c != null && c > p.serverLevel().getGameTime()) amt *= 1.3f;
        AstaFx.damage(p, t, amt, id);
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !Moves.inMode(p, INSURED)) return;
        if (e.getAmount() > p.getMaxHealth() / 3f) {   // the house pays
            float paid = e.getAmount() * 0.75f;
            e.setAmount(e.getAmount() * 0.25f);
            Moves.ends(p, INSURED);
            payout(p.serverLevel(), p.position().add(0, 1, 0), 2.5f, 20);
            p.heal(Math.min(4f, paid * 0.2f));
            play(p.serverLevel(), p.position(), "jackpot", 0.8f, 1.4f);
            p.displayClientMessage(Component.literal("Insured - the house pays out").withStyle(ChatFormatting.GOLD), true);
        }
    }

    private static Vec3 aimPoint(ServerPlayer p, double range) {
        LivingEntity t = aimed(p, range, 10);
        return t != null ? mid(t) : clip(p.serverLevel(), p, p.getEyePosition(), p.getEyePosition().add(p.getLookAngle().scale(range)));
    }

    private static Vec3 look(ServerPlayer p) {
        Vec3 l = flat(p.getLookAngle());
        return l.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : l.normalize();
    }

    // ================================================================== 1. Coin Toss
    public static void coinToss(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        boolean heads = flip(p);
        final float pow = stake(p, power, heads);
        LivingEntity first = aimed(p, 28, 10);
        Vec3 from = Kit.hand(p), to = first != null ? mid(first) : aimPoint(p, 28);
        int fly = Math.max(2, (int) Math.ceil(from.distanceTo(to) / 2.6));
        coin(sl, from, to, 0.3f, fly);
        play(sl, from, "coin", 1.0f, 1.2f);
        final LivingEntity f0 = first;
        AstaFx.later(fly, () -> {
            if (!alive(p)) return;
            List<LivingEntity> struck = new ArrayList<>();
            if (f0 != null && f0.isAlive()) { hit(p, f0, pow * 0.9f, "coin_toss"); struck.add(f0); }
            else for (LivingEntity e : Kit.near(sl, p, to, 1.4)) { hit(p, e, pow * 0.9f, "coin_toss"); struck.add(e); }
            play(sl, to, "coin", 1.0f, 1.8f);
            if (!heads || struck.isEmpty()) return;
            Vec3 at = to;   // heads: it ricochets
            for (int b = 0; b < 2; b++) {
                LivingEntity next = null;
                double best = 10;
                for (LivingEntity e : Kit.near(sl, p, at, 10)) if (!struck.contains(e) && mid(e).distanceTo(at) < best) { best = mid(e).distanceTo(at); next = e; }
                if (next == null) break;
                struck.add(next);
                final Vec3 a0 = at, a1 = mid(next);
                final LivingEntity nn = next;
                final int bb = b;
                AstaFx.later(3 + b * 4, () -> {
                    coin(sl, a0, a1, 0.3f, 3);
                    AstaFx.later(3, () -> { if (alive(p) && nn.isAlive()) { hit(p, nn, pow * 0.7f, "coin_toss"); play(sl, a1, "coin", 0.9f, 1.9f + bb * 0.1f); } });
                });
                at = a1;
            }
        });
    }

    // ================================================================== 2. Double or Nothing
    public static void doubleOrNothing(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        float bet = p.getMaxHealth() * 0.1f;
        if (!p.isCreative()) p.setHealth(Math.max(1f, p.getHealth() - bet));
        boolean heads = flip(p);
        AstaFx.later(14, () -> {
            if (!alive(p)) return;
            if (heads) {
                p.heal(bet * 2f);
                DOUBLE.put(p.getUUID(), sl.getGameTime());
                payout(sl, p.position().add(0, 1.2, 0), 1.6f, 18);
                play(sl, p.position(), "jackpot", 0.7f, 1.6f);
                p.displayClientMessage(Component.literal("Doubled - your next spell lands twice as hard").withStyle(ChatFormatting.GOLD), true);
            } else {
                LUCK.merge(p.getUUID(), 2, Integer::sum);
                p.displayClientMessage(Component.literal("Nothing - but the house owes you (Luck " + luck(p) + ")").withStyle(ChatFormatting.GRAY), true);
                sound(sl, p.position(), SoundEvents.VILLAGER_NO, 0.6f, 1.2f);
            }
            for (LivingEntity e : Kit.near(sl, p, p.position().add(0, 1, 0), 4)) hit(p, e, power * (heads ? 0.6f : 0.25f), "double_or_nothing");
        });
    }

    // ================================================================== 3. Roulette
    public static void roulette(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        boolean heads = flip(p);
        final float pow = stake(p, power, heads);
        final Vec3 c = InkFx.target(p, 24);
        final float R = 4f;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int pocket;   // 0 = green; odd = red, even = black (heads loads it toward green and red)
        double roll = r.nextDouble();
        if (roll < (heads ? 0.2 : 0.125)) pocket = 0;
        else pocket = 1 + r.nextInt(36);
        final int pk = pocket;
        final int SPIN = 26;
        wheel(sl, c.add(0, 0.05, 0), R, pocket, SPIN, SPIN + 26);
        play(sl, c, "roulette", 1.0f, 1.0f);
        AstaFx.later(SPIN, () -> {
            if (!alive(p)) return;
            List<LivingEntity> foes = Kit.near(sl, p, c.add(0, 1, 0), R + 0.5);
            if (pk == 0) {   // green: everything
                payout(sl, c.add(0, 1, 0), R + 1, 26);
                for (LivingEntity e : foes) hit(p, e, pow * 2.4f, "roulette");
                play(sl, c, "jackpot", 1.2f, 1.0f);
                p.displayClientMessage(Component.literal("Green! The house pays everything").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), true);
            } else if (pk % 2 == 1) {   // red: fire
                FxSend.ring(sl, Fx.FIRE, c.add(0, 0.2, 0), 0.5f, R, 0.4f, 14);
                FxSend.beam(sl, Fx.FIRE, c, c.add(0, 4, 0), 1.6f, 10);
                for (LivingEntity e : foes) { hit(p, e, pow * 1.2f, "roulette"); }
                sound(sl, c, SoundEvents.BLAZE_SHOOT, 1.0f, 0.8f);
            } else {   // black: a curse
                for (LivingEntity e : foes) {
                    CURSE.put(e.getUUID(), sl.getGameTime() + 120);
                    TimedModifiers.apply(e, Attributes.MOVEMENT_SPEED, CURSED, "Black's curse", -0.35, AttributeModifier.Operation.MULTIPLY_TOTAL, 120);
                    hit(p, e, pow * 0.7f, "roulette");
                }
                FxSend.ring(sl, Fx.YAMI, c.add(0, 0.2, 0), 0.5f, R, 0.4f, 14);
                sound(sl, c, SoundEvents.WITHER_AMBIENT, 0.5f, 1.4f);
            }
        });
    }

    // ================================================================== 4. Chip Shot
    public static void chipShot(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        boolean heads = flip(p);
        final float pow = stake(p, power, heads);
        int N = heads ? 8 : 5;
        Vec3 from = Kit.hand(p), lk = p.getLookAngle();
        for (int i = 0; i < N; i++) {
            double off = (i - (N - 1) / 2.0) * 0.12;
            Vec3 dir = rotY(lk, off).normalize();
            Vec3 to = clip(sl, p, from, from.add(dir.scale(18)));
            final int k = i;
            int fly = Math.max(2, (int) Math.ceil(from.distanceTo(to) / 2.8));
            chip(sl, from, to, i % 4, fly);
            AstaFx.later(fly, () -> { if (alive(p)) for (LivingEntity e : Kit.along(sl, p, from, to, 0.8)) hit(p, e, pow * 0.28f, "chip_shot"); });
            if (k == 0) play(sl, from, "coin", 0.9f, 1.6f);
        }
    }

    // ================================================================== 5. Lucky Seven
    public static void luckySeven(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        boolean heads = flip(p);
        final float pow = stake(p, power, heads);
        final Vec3 c = InkFx.target(p, 24);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int extra = heads ? 2 : 0;
        for (int i = 0; i < 7; i++) {
            double an = i * TAU / 7, rr = i == 0 ? 0 : 2.2;
            Vec3 g = InkFx.ground(sl, c.add(Math.cos(an) * rr, 0, Math.sin(an) * rr));
            final boolean lucky = r.nextInt(2) == 0 || i < extra;
            final int k = i;
            AstaFx.later(i * 2, () -> coin(sl, g.add(0, 7, 0), g.add(0, 0.2, 0), 0.4f, 7));
            AstaFx.later(i * 2 + 7, () -> {
                if (!alive(p)) return;
                if (lucky) {
                    payout(sl, g.add(0, 0.5, 0), 1.8f, 14);
                    for (LivingEntity e : Kit.near(sl, p, g.add(0, 0.8, 0), 2)) hit(p, e, pow * 0.45f, "lucky_seven");
                    play(sl, g, "coin", 1.0f, 1.5f + k * 0.05f);
                } else play(sl, g, "coin", 0.6f, 0.8f);
            });
        }
    }

    // ================================================================== 6. Slot Machine
    public static void slotMachine(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        boolean heads = flip(p);
        final float pow = stake(p, power, heads);
        LivingEntity t = aimed(p, 24, 12);
        final Vec3 c = t != null ? t.position() : InkFx.target(p, 24);
        int[] s = spin(heads, false);
        final int STOP = 18;
        reels(sl, c.add(0, 3.4, 0), s, 1.0f, STOP, STOP + 16 + 20);
        play(sl, c, "slot", 1.0f, 1.0f);
        AstaFx.later(STOP + 16, () -> {
            if (!alive(p)) return;
            float k = payoutFor(s);
            payout(sl, c.add(0, 1, 0), 1.5f + k, 18);
            for (LivingEntity e : Kit.near(sl, p, c.add(0, 1, 0), 2.5 + k)) hit(p, e, pow * k, "slot_machine");
            play(sl, c, k >= 2.5f ? "jackpot" : "coin", 1.0f, k >= 2.5f ? 1.0f : 1.4f);
        });
    }

    /** Three reels: heads leans toward pairs; with {@code sure}, three sevens. */
    static int[] spin(boolean heads, boolean sure) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        if (sure) return new int[]{SEVEN, SEVEN, SEVEN};
        int[] s = {r.nextInt(5), r.nextInt(5), r.nextInt(5)};
        if (heads && r.nextFloat() < 0.6f) s[1] = s[0];
        if (heads && r.nextFloat() < 0.35f) s[2] = s[0];
        return s;
    }

    static float payoutFor(int[] s) {
        boolean three = s[0] == s[1] && s[1] == s[2];
        if (three && s[0] == SEVEN) return 3.2f;
        if (three && s[0] == SKULL) return 0.5f;
        if (three) return 2.2f;
        if (s[0] == s[1] || s[1] == s[2] || s[0] == s[2]) return 1.3f;
        for (int x : s) if (x == SKULL) return 0.5f;
        return 0.8f;
    }

    // ================================================================== 7. Loaded Dice
    public static void loadedDice(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        boolean heads = flip(p);
        final float pow = stake(p, power, heads);
        roll(p, sl, pow, heads, 0);
    }

    private static void roll(ServerPlayer p, ServerLevel sl, float pow, boolean heads, int again) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int d1 = 1 + r.nextInt(6), d2 = 1 + r.nextInt(6);
        if (heads && d1 < 3) d1 += 2;   // loaded
        final int a = d1, b = d2;
        Vec3 from = Kit.hand(p), to = aimPoint(p, 18);
        to = InkFx.ground(sl, to).add(0, 0.15, 0);
        final Vec3 tt = to;
        int fly = Math.max(4, (int) Math.ceil(from.distanceTo(to) / 1.8));
        dice(sl, from, to, a, b, fly);
        play(sl, from, "dice", 1.0f, 1.0f);
        AstaFx.later(fly, () -> {
            if (!alive(p)) return;
            int sum = a + b;
            for (LivingEntity e : Kit.near(sl, p, tt.add(0, 0.8, 0), 2.6)) hit(p, e, pow * 0.12f * sum, "loaded_dice");
            payout(sl, tt.add(0, 0.5, 0), 0.6f + sum * 0.15f, 12);
            play(sl, tt, "dice", 1.0f, 1.4f);
            if (a == b && again < 2) {
                p.displayClientMessage(Component.literal("Doubles - roll again").withStyle(ChatFormatting.GOLD), true);
                AstaFx.later(8, () -> { if (alive(p)) roll(p, sl, pow, heads, again + 1); });
            }
        });
    }

    // ================================================================== 8. Insurance
    public static void insurance(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        flip(p);
        final int T = 200;
        shield(sl, p, T);
        Moves.mode(p, INSURED, T, 20, pl -> {});
        play(sl, p.position(), "coin", 1.0f, 0.7f);
        sound(sl, p.position(), SoundEvents.ARMOR_EQUIP_GOLD, 1.0f, 1.2f);
    }

    // ================================================================== 9. All In (mode)
    public static void allInMode(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        final int T = 400;
        aura(sl, p, T);
        Moves.mode(p, MODE, T, 20, pl -> {});
        Moves.stance(p, 0, 0, 0.15, 0, 0.15, T);
        payout(sl, p.position().add(0, 1, 0), 3f, 24);
        FxSend.impactFrame(sl, p.position().add(0, 1, 0), 2, FxSend.FRAME_GOLD, 16);
        play(sl, p.position(), "jackpot", 1.0f, 0.8f);
        p.displayClientMessage(Component.literal("All in. Every flip is double or nothing.").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
    }

    // ================================================================== 10. Jackpot
    public static void jackpot(ServerPlayer p, float power) {
        boolean limit = LimitBreak.active(p);
        ServerLevel sl = p.serverLevel();
        boolean sure = limit || luck(p) >= 3;
        boolean heads = flip(p);
        final float pow = stake(p, power, heads) * (limit ? 1.3f : 1f);
        int[] s = spin(heads, sure);
        final Vec3 c = p.position().add(look(p).scale(2.5)).add(0, 3.6, 0);
        final int STOP = 24;
        machine(sl, c, s, STOP, STOP + 18 + 14);
        play(sl, c, "slot", 1.4f, 0.8f);
        AstaFx.later(STOP + 18, () -> {
            if (!alive(p)) return;
            float k = Math.max(1.8f, payoutFor(s) * 1.1f);
            boolean big = s[0] == SEVEN && s[1] == SEVEN && s[2] == SEVEN;
            Vec3 g = InkFx.target(p, 26);
            float R = big ? 10f : 6f;
            payout(sl, g.add(0, 1, 0), R, 30);
            for (LivingEntity e : Kit.near(sl, p, g.add(0, 1, 0), R)) hit(p, e, pow * k, "jackpot");
            if (big) {
                FxSend.impactFrame(sl, g.add(0, 1, 0), 3, FxSend.FRAME_GOLD, 26);
                p.displayClientMessage(Component.literal("JACKPOT").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
            }
            FxSend.shake(sl, g, big ? 0.9f : 0.5f, 12, 30);
            play(sl, g, "jackpot", 1.6f, big ? 0.8f : 1.1f);
        });
    }

    /** Surpassing Limits: a shower of coins round you. */
    public static void awaken(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        payout(sl, p.position().add(0, 1.5, 0), 4f, 30);
        LUCK.put(p.getUUID(), 3);
        play(sl, p.position(), "jackpot", 1.2f, 1.0f);
    }
}
