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
import net.minecraftforge.common.ForgeMod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The Tarot Grimoire (an original grimoire, its cards drawn in its own style): a deck of the twenty-two major arcana.
 * Drawing a card lays it in the air before you, upright or reversed, and does what the card means. The deck is
 * finite: every card drawn is gone until all twenty-two have been read - then the reading is complete, it pays out,
 * and the deck is shuffled anew. Drawn by client/TarotFxClient (FxPacket shape 92): cards of cream and violet with
 * gold frames, each with its own sign and numeral.
 *
 *  1 Draw              - draw the next card from your deck: one of twenty-two effects, upright or reversed (weaker)
 *  2 The Magician      - four bolts, one of each suit - wand, cup, sword and coin - at your target
 *  3 The Tower         - a tower of light rises under your target, is struck, and falls on everything round it
 *  4 The Star          - stars fall round you for 4 s, mending you and your allies
 *  5 Death             - a sweep of the scythe: foes below a third of their health take it three times over
 *  6 Wheel of Fortune  - the wheel turns: all your other cooldowns run ahead by a random 5 to 15 s
 *  7 The Hanged Man    - the foe you aim at is hung in the air, helpless, for 3 s, then dropped
 *  8 Three-Card Spread - past, present and future: three cards drawn and read in a row
 *  9 The World (mode)  - 20 s: every card you draw is upright and 40% stronger
 * 10 Judgement         - a great card in the sky sounds its trumpet: beams of judgement fall on every foe near you.
 *                        Surpassing Limits: wider, heavier.
 *
 * Passive "The Reading": your deck holds the twenty-two cards; draw them all (with Draw and the Spread) and the
 * reading is complete - for 15 s every spell hits 30% harder - and the deck is shuffled again.
 */
public final class TarotFx {
    private TarotFx() {}

    public static final String MODE = "the_world", READ = "reading_complete";
    public static final String[] ARCANA = {"The Fool", "The Magician", "The High Priestess", "The Empress", "The Emperor", "The Hierophant", "The Lovers",
            "The Chariot", "Strength", "The Hermit", "Wheel of Fortune", "Justice", "The Hanged Man", "Death", "Temperance", "The Devil", "The Tower",
            "The Star", "The Moon", "The Sun", "Judgement", "The World"};
    private static final String DECK_KEY = "isg_tarot_deck";
    private static final UUID HANG = UUID.fromString("f4a0c5b0-90da-4217-a4aa-03b7fe000001");
    private static final UUID SLOW = UUID.fromString("f4a0c5b0-90da-4217-a4aa-03b7fe000002");
    private static final UUID ARMOR = UUID.fromString("f4a0c5b0-90da-4217-a4aa-03b7fe000003");
    private static final double TAU = Math.PI * 2;

    // ================================================================== the client shapes (TarotFxClient, shape 92)
    private static FxPacket pk(int kind, Vec3 c, int life) {
        FxPacket p = new FxPacket();
        p.shape = 92; p.id = FxSend.nextId(); p.h = kind; p.c = c; p.life = life; p.a1 = (float) (Math.random() * 1000);
        return p;
    }
    private static void send(ServerLevel sl, FxPacket p) { FxSend.sendNear(sl, p.c, p); }

    /** 0 a card laid in the air before a player: a0 = arcana, r = 1 reversed, r2 = place in a spread (-1, 0, 1). */
    static void card(ServerLevel sl, ServerPlayer pl, int arcana, boolean reversed, float slot, int life) {
        FxPacket p = pk(0, pl.position(), life); p.v = new Vec3(pl.getId(), 1, 0); p.a0 = arcana; p.r = reversed ? 1 : 0; p.r2 = slot; send(sl, p);
    }
    static void tower(ServerLevel sl, Vec3 c, float r, int strike, int life) { FxPacket p = pk(1, c, life); p.r = r; p.sweep = strike; send(sl, p); }
    static void stars(ServerLevel sl, ServerPlayer pl, float r, int life) { FxPacket p = pk(2, pl.position(), life); p.v = new Vec3(pl.getId(), 1, 0); p.r = r; send(sl, p); }
    static void scythe(ServerLevel sl, ServerPlayer pl, float r, int life) { FxPacket p = pk(3, pl.position(), life); p.v = new Vec3(pl.getId(), 1, 0); p.r = r; send(sl, p); }
    static void hang(ServerLevel sl, LivingEntity t, int life) { FxPacket p = pk(4, t.position(), life); p.v = new Vec3(t.getId(), 1, 0); send(sl, p); }
    static void beam(ServerLevel sl, Vec3 c, int life) { FxPacket p = pk(5, c, life); send(sl, p); }
    static void aura(ServerLevel sl, ServerPlayer pl, int life) { FxPacket p = pk(6, pl.position(), life); p.v = new Vec3(pl.getId(), 1, 0); send(sl, p); }
    static void suit(ServerLevel sl, Vec3 c, Vec3 u, int which, int fly) { FxPacket p = pk(7, c, fly + 4); p.u = u; p.a0 = which; p.speed = fly; send(sl, p); }
    static void sun(ServerLevel sl, Vec3 c, float r, boolean moon) { FxPacket p = pk(8, c, 18); p.r = r; p.a0 = moon ? 1 : 0; send(sl, p); }
    static void complete(ServerLevel sl, ServerPlayer pl) { FxPacket p = pk(10, pl.position(), 50); p.v = new Vec3(pl.getId(), 1, 0); send(sl, p); }
    static void justice(ServerLevel sl, Vec3 c, int fall) { FxPacket p = pk(11, c, fall + 10); p.sweep = fall; send(sl, p); }
    static void link(ServerLevel sl, LivingEntity a, LivingEntity b, int life) { FxPacket p = pk(12, a.position(), life); p.v = new Vec3(a.getId(), b.getId(), 0); send(sl, p); }
    static void judgementSky(ServerLevel sl, Vec3 c, float r, int life) { FxPacket p = pk(13, c, life); p.r = r; send(sl, p); }

    // ================================================================== the deck
    static void play(ServerLevel sl, Vec3 at, String kind, float vol, float pitch) {
        SoundEvent s = AddonSounds.get(kind);
        if (s != null) sl.playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, vol, pitch);
    }

    static boolean world(ServerPlayer p) { return Moves.inMode(p, MODE); }

    /** The cards still in your deck, as a bit mask (bit i set = arcana i still to draw). */
    public static int deck(ServerPlayer p) {
        var t = p.getPersistentData();
        if (!t.contains(DECK_KEY)) t.putInt(DECK_KEY, (1 << 22) - 1);
        return t.getInt(DECK_KEY);
    }

    public static void setDeck(ServerPlayer p, int mask) { p.getPersistentData().putInt(DECK_KEY, mask); }

    /** Draws a card out of the deck; the last one completes the reading. */
    static int drawOne(ServerPlayer p) {
        int mask = deck(p);
        List<Integer> left = new ArrayList<>();
        for (int i = 0; i < 22; i++) if ((mask & (1 << i)) != 0) left.add(i);
        if (left.isEmpty()) { mask = (1 << 22) - 1; for (int i = 0; i < 22; i++) left.add(i); }
        int c = left.get(ThreadLocalRandom.current().nextInt(left.size()));
        mask &= ~(1 << c);
        setDeck(p, mask);
        if (mask == 0) {   // the reading is complete
            setDeck(p, (1 << 22) - 1);
            Moves.mode(p, READ, 300, 20, pl -> {});
            complete(p.serverLevel(), p);
            play(p.serverLevel(), p.position(), "card", 1.4f, 0.6f);
            sound(p.serverLevel(), p.position(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.0f);
            p.displayClientMessage(Component.literal("The reading is complete - your spells hit 30% harder").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD), true);
        } else p.displayClientMessage(Component.literal(ARCANA[c] + " - " + Integer.bitCount(mask) + " cards left in the deck").withStyle(ChatFormatting.LIGHT_PURPLE), true);
        return c;
    }

    static float pw(ServerPlayer p, float power) {
        float k = 1f;
        if (world(p)) k *= 1.4f;
        if (Moves.inMode(p, READ)) k *= 1.3f;
        return power * k;
    }

    static void hit(ServerPlayer p, LivingEntity t, float amt, String id) { AstaFx.damage(p, t, amt, id); }

    private static LivingEntity aimedFoe(ServerPlayer p, double range) {
        LivingEntity t = aimed(p, range, 14);
        if (t != null) return t;
        LivingEntity best = null;
        double bd = range;
        for (LivingEntity e : Kit.near(p.serverLevel(), p, p.getEyePosition(), range)) {
            Vec3 to = mid(e).subtract(p.getEyePosition());
            if (to.normalize().dot(p.getLookAngle()) < 0.5) continue;
            if (to.length() < bd) { bd = to.length(); best = e; }
        }
        return best;
    }

    private static Vec3 look(ServerPlayer p) {
        Vec3 l = flat(p.getLookAngle());
        return l.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : l.normalize();
    }

    /** What a card does (upright, or reversed and weaker). */
    static void read(ServerPlayer p, int arcana, boolean rev, float power) {
        ServerLevel sl = p.serverLevel();
        float pow = pw(p, power) * (rev ? 0.6f : 1f);
        float k = rev ? 0.5f : 1f;
        Vec3 c = p.position().add(0, 1, 0);
        switch (arcana) {
            case 0 -> {   // The Fool: a leap into the unknown
                Vec3 d = look(p).scale(rev ? -1 : 1);
                for (LivingEntity e : Kit.near(sl, p, c, 3)) hit(p, e, pow * 0.5f, "draw_card");
                p.setDeltaMovement(d.x * 1.4, 0.45, d.z * 1.4); p.hurtMarked = true; p.fallDistance = 0;
            }
            case 1 -> magicianBolts(p, pow, rev ? 2 : 4);
            case 2 -> {   // The High Priestess: what is hidden is shown
                for (LivingEntity e : Kit.near(sl, p, c, 24)) { AstaFx.sendKiMark(p, e, 160, !rev); if (!rev) hit(p, e, pow * 0.2f, "draw_card"); }
            }
            case 3 -> { p.heal(6f * k); stars(sl, p, 2f, 30); }   // The Empress
            case 4 -> TimedModifiers.apply(p, Attributes.ARMOR, ARMOR, "The Emperor", 6 * k, AttributeModifier.Operation.ADDITION, 200);
            case 5 -> p.setAbsorptionAmount(Math.max(p.getAbsorptionAmount(), 6f * k));   // The Hierophant
            case 6 -> {   // The Lovers: two foes bound - what hurts one hurts the other
                List<LivingEntity> l = Kit.near(sl, p, c, 14);
                l.sort((x, y) -> Double.compare(x.distanceToSqr(p), y.distanceToSqr(p)));
                if (l.size() >= 2) { link(sl, l.get(0), l.get(1), 30); hit(p, l.get(0), pow * 0.7f, "draw_card"); hit(p, l.get(1), pow * 0.7f, "draw_card"); }
                else if (!l.isEmpty()) { hit(p, l.get(0), pow * 0.7f, "draw_card"); p.heal(2f); }
            }
            case 7 -> {   // The Chariot: a charge
                Vec3 d = look(p);
                p.setDeltaMovement(d.x * (rev ? 1.0 : 1.8), 0.2, d.z * (rev ? 1.0 : 1.8)); p.hurtMarked = true;
                AstaFx.later(4, () -> { if (alive(p)) for (LivingEntity e : Kit.near(sl, p, p.position().add(0, 1, 0), 2.8)) { hit(p, e, pow * 0.9f, "draw_card"); Kit.push(e, d, 0.8, 0.3); } });
            }
            case 8 -> Moves.stance(p, 0, 0, 0, 0.3, 0.3f * k, 160);   // Strength
            case 9 -> {   // The Hermit: a lantern in the dark - foes near you slow
                for (LivingEntity e : Kit.near(sl, p, c, 6)) { TimedModifiers.apply(e, Attributes.MOVEMENT_SPEED, SLOW, "The Hermit's lantern", -0.4 * k, AttributeModifier.Operation.MULTIPLY_TOTAL, 100); hit(p, e, pow * 0.3f, "draw_card"); }
                sun(sl, c, 6f, true);
            }
            case 10 -> wheelOfFortune(p, rev ? 80 : 200);
            case 11 -> {   // Justice: a sword falls on the foe you aim at
                LivingEntity t = aimedFoe(p, 24);
                if (t != null) {
                    justice(sl, mid(t), 8);
                    final float d = Math.min(pow * 3f, t.getMaxHealth() * (rev ? 0.08f : 0.15f)) + pow * 0.4f;
                    AstaFx.later(8, () -> { if (alive(p) && t.isAlive()) hit(p, t, d, "draw_card"); });
                }
            }
            case 12 -> { LivingEntity t = aimedFoe(p, 20); if (t != null) hangFoe(p, t, pow, rev ? 30 : 60); }
            case 13 -> deathSweep(p, pow, !rev);
            case 14 -> { TimedModifiers.clearDebuffs(p); if (!rev) p.heal(4f); stars(sl, p, 1.5f, 20); }   // Temperance
            case 15 -> {   // The Devil: chains on everything near
                for (LivingEntity e : Kit.near(sl, p, c, 5)) { ChainFx.hold(e, rev ? 20 : 40); ChainFx.wrap(sl, e, 0, rev ? 20 : 40); hit(p, e, pow * 0.5f, "draw_card"); }
            }
            case 16 -> towerAt(p, InkFx.target(p, 22), pow);
            case 17 -> starFall(p, rev ? 0.5f : 1f);
            case 18 -> {   // The Moon: illusions - foes near you stumble
                for (LivingEntity e : Kit.near(sl, p, c, 7)) { TimedModifiers.apply(e, Attributes.MOVEMENT_SPEED, SLOW, "Moon-struck", -0.3, AttributeModifier.Operation.MULTIPLY_TOTAL, 80); hit(p, e, pow * 0.6f, "draw_card"); }
                sun(sl, c, 7f, true);
            }
            case 19 -> {   // The Sun: a burst of light; it warms you
                sun(sl, c, 6f, false);
                for (LivingEntity e : Kit.near(sl, p, c, 6)) hit(p, e, pow * 0.9f, "draw_card");
                if (!rev) p.heal(3f);
            }
            case 20 -> judgementBeams(p, pow * 0.7f, rev ? 5f : 8f);
            default -> {   // The World: everything at once
                for (LivingEntity e : Kit.near(sl, p, c, rev ? 6 : 10)) hit(p, e, pow * 1.2f, "draw_card");
                p.heal(3f * k);
                sun(sl, c, rev ? 6f : 10f, false);
            }
        }
    }

    // ================================================================== the cards' own spells
    static void magicianBolts(ServerPlayer p, float pow, int n) {
        ServerLevel sl = p.serverLevel();
        LivingEntity t = aimedFoe(p, 26);
        Vec3 to0 = t != null ? mid(t) : Kit.aim(p, 26);
        for (int i = 0; i < n; i++) {
            final int k = i;
            AstaFx.later(i * 3, () -> {
                if (!alive(p)) return;
                Vec3 from = p.getEyePosition().add(rightOf(look(p)).scale((k - 1.5) * 0.5)).add(0, 0.4, 0);
                Vec3 to = t != null && t.isAlive() ? mid(t) : to0;
                int fly = Math.max(2, (int) Math.ceil(from.distanceTo(to) / 2.8));
                suit(sl, from, to, k % 4, fly);
                AstaFx.later(fly, () -> { if (alive(p)) for (LivingEntity e : Kit.near(sl, p, to, 1.6)) hit(p, e, pow * 0.4f, "magician_card"); });
                play(sl, from, "card", 0.6f, 1.2f + k * 0.1f);
            });
        }
    }

    static void towerAt(ServerPlayer p, Vec3 g, float pow) {
        ServerLevel sl = p.serverLevel();
        final int STRIKE = 16;
        tower(sl, g, 2.8f, STRIKE, STRIKE + 16);
        play(sl, g, "card", 0.8f, 0.8f);
        AstaFx.later(STRIKE, () -> {
            if (!alive(p)) return;
            for (LivingEntity e : Kit.near(sl, p, g.add(0, 1, 0), 4.5)) hit(p, e, pow * 1.5f, "tower_card");
            FxSend.shake(sl, g, 0.5f, 8, 20);
            sound(sl, g, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.8f, 1.2f);
        });
    }

    static void starFall(ServerPlayer p, float k) {
        ServerLevel sl = p.serverLevel();
        stars(sl, p, 4f, 80);
        for (int t = 0; t < 80; t += 10) {
            AstaFx.later(t + 2, () -> {
                if (!alive(p)) return;
                p.heal(1.2f * k);
                for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(5), x -> x.isAlive() && ChainFx.ally(p, x))) e.heal(1.2f * k);
            });
        }
        play(sl, p.position(), "card", 0.8f, 1.5f);
    }

    static void deathSweep(ServerPlayer p, float pow, boolean reap) {
        ServerLevel sl = p.serverLevel();
        scythe(sl, p, 5f, 14);
        AstaFx.later(4, () -> {
            if (!alive(p)) return;
            for (LivingEntity e : Kit.near(sl, p, p.position().add(0, 1, 0), 5.2)) {
                boolean low = e.getHealth() < e.getMaxHealth() / 3f;
                hit(p, e, pow * (reap && low ? 3.0f : 0.9f), "death_card");
            }
            sound(sl, p.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, 0.5f);
        });
    }

    static void wheelOfFortune(ServerPlayer p, int ticks) {
        ServerLevel sl = p.serverLevel();
        try { var cd = io.redspace.ironsspellbooks.api.magic.MagicData.getPlayerMagicData(p).getPlayerCooldowns(); cd.tick(ticks); cd.syncToPlayer(p); } catch (Throwable ignored) {}
        sun(sl, p.position().add(0, 1, 0), 3f, false);
        play(sl, p.position(), "card", 0.9f, 1.0f);
    }

    static void hangFoe(ServerPlayer p, LivingEntity t, float pow, int T) {
        ServerLevel sl = p.serverLevel();
        int time = IceFx.boss(t) ? T / 2 : T;
        hang(sl, t, time + 6);
        TimedModifiers.apply(t, ForgeMod.ENTITY_GRAVITY.get(), HANG, "Hanged", -1.0, AttributeModifier.Operation.MULTIPLY_TOTAL, time);
        TimedModifiers.apply(t, Attributes.MOVEMENT_SPEED, SLOW, "Hanged", -1.0, AttributeModifier.Operation.MULTIPLY_TOTAL, time);
        t.setDeltaMovement(0, 0.5 * GravityFx.give(t) + 0.1, 0);
        t.hurtMarked = true;
        hit(p, t, pow * 0.4f, "hanged_card");
        AstaFx.later(8, () -> { if (t.isAlive()) { t.setDeltaMovement(0, 0, 0); t.hurtMarked = true; } });
        AstaFx.later(time, () -> {
            if (!t.isAlive()) return;
            TimedModifiers.remove(t, ForgeMod.ENTITY_GRAVITY.get(), HANG);
            t.setDeltaMovement(0, -1.4, 0);
            t.hurtMarked = true;
            AstaFx.later(6, () -> { if (alive(p) && t.isAlive()) hit(p, t, pow * 0.8f, "hanged_card"); });
        });
        play(sl, t.position(), "card", 0.8f, 0.7f);
    }

    static void judgementBeams(ServerPlayer p, float pow, float R) {
        ServerLevel sl = p.serverLevel();
        List<LivingEntity> foes = Kit.near(sl, p, p.position().add(0, 1, 0), R);
        int i = 0;
        for (LivingEntity e : foes) {
            final int k = i++;
            AstaFx.later(4 + k * 2, () -> {
                if (!alive(p) || !e.isAlive()) return;
                beam(sl, e.position(), 14);
                hit(p, e, pow, "judgement_card");
                sound(sl, e.position(), SoundEvents.BEACON_POWER_SELECT, 0.5f, 1.6f);
            });
        }
        sound(sl, p.position(), SoundEvents.RAID_HORN.value(), 0.4f, 1.6f);
    }

    /** Draws one card for a spell: in The World it's always upright. */
    private static void drawAndRead(ServerPlayer p, float power, float slot, int delay) {
        ServerLevel sl = p.serverLevel();
        int c = drawOne(p);
        boolean rev = !world(p) && ThreadLocalRandom.current().nextFloat() < 0.3f;
        card(sl, p, c, rev, slot, 40 + delay);
        play(sl, p.position(), "card", 1.0f, 1.0f + slot * 0.1f);
        AstaFx.later(8 + delay, () -> { if (alive(p)) read(p, c, rev, power); });
    }

    // ================================================================== 1. Draw
    public static void drawCard(ServerPlayer p, float power) { drawAndRead(p, power, 0, 0); }

    // ================================================================== 2. The Magician
    public static void magicianCard(ServerPlayer p, float power) {
        card(p.serverLevel(), p, 1, false, 0, 30);
        AstaFx.later(6, () -> { if (alive(p)) magicianBolts(p, pw(p, power), 4); });
    }

    // ================================================================== 3. The Tower
    public static void towerCard(ServerPlayer p, float power) {
        card(p.serverLevel(), p, 16, false, 0, 30);
        Vec3 g = InkFx.target(p, 24);
        AstaFx.later(4, () -> { if (alive(p)) towerAt(p, g, pw(p, power)); });
    }

    // ================================================================== 4. The Star
    public static void starCard(ServerPlayer p, float power) {
        card(p.serverLevel(), p, 17, false, 0, 30);
        starFall(p, 1f);
    }

    // ================================================================== 5. Death
    public static void deathCard(ServerPlayer p, float power) {
        card(p.serverLevel(), p, 13, false, 0, 26);
        AstaFx.later(4, () -> { if (alive(p)) deathSweep(p, pw(p, power), true); });
    }

    // ================================================================== 6. Wheel of Fortune
    public static void wheelCard(ServerPlayer p, float power) {
        card(p.serverLevel(), p, 10, false, 0, 26);
        wheelOfFortune(p, 100 + ThreadLocalRandom.current().nextInt(201));
        for (LivingEntity e : Kit.near(p.serverLevel(), p, p.position().add(0, 1, 0), 5)) hit(p, e, pw(p, power) * 0.4f, "wheel_card");
    }

    // ================================================================== 7. The Hanged Man
    public static void hangedCard(ServerPlayer p, float power) {
        card(p.serverLevel(), p, 12, false, 0, 26);
        LivingEntity t = aimedFoe(p, 22);
        if (t != null) hangFoe(p, t, pw(p, power), 60);
    }

    // ================================================================== 8. Three-Card Spread
    public static void threeCardSpread(ServerPlayer p, float power) {
        for (int i = 0; i < 3; i++) {
            final int k = i;
            AstaFx.later(i * 12, () -> { if (alive(p)) drawAndRead(p, power, k - 1, 0); });
        }
    }

    // ================================================================== 9. The World (mode)
    public static void worldMode(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        final int T = 400;
        card(sl, p, 21, false, 0, 36);
        aura(sl, p, T);
        Moves.mode(p, MODE, T, 20, pl -> {});
        Moves.stance(p, 2, 0, 0.1, 0, 0.1, T);
        FxSend.impactFrame(sl, p.position().add(0, 1, 0), 2, FxSend.FRAME_FINRAL, 16);
        play(sl, p.position(), "card", 1.4f, 0.6f);
    }

    // ================================================================== 10. Judgement
    public static void judgementCard(ServerPlayer p, float power) {
        boolean limit = LimitBreak.active(p);
        ServerLevel sl = p.serverLevel();
        final float pow = pw(p, power) * (limit ? 1.3f : 1f);
        final float R = limit ? 14f : 10f;
        card(sl, p, 20, false, 0, 30);
        judgementSky(sl, p.position(), R, 44);
        play(sl, p.position(), "card", 1.2f, 0.5f);
        sound(sl, p.position(), SoundEvents.RAID_HORN.value(), 0.8f, 1.2f);
        AstaFx.later(14, () -> {
            if (!alive(p)) return;
            judgementBeams(p, pow * 2.0f, R);
            FxSend.impactFrame(sl, p.position().add(0, 1, 0), 3, FxSend.FRAME_LIGHT, 24);
            FxSend.shake(sl, p.position(), 0.6f, 10, 30);
        });
    }

    /** Surpassing Limits: the whole deck fans out round you. */
    public static void awaken(ServerPlayer p) {
        complete(p.serverLevel(), p);
        play(p.serverLevel(), p.position(), "card", 1.2f, 0.7f);
    }
}
