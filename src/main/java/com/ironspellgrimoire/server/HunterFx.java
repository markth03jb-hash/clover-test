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
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The Hunter's Grimoire (an original grimoire): the book of a hunter of the blood moon. It is really a weapon - a
 * serrated trick cleaver that snaps out into a long saw-blade - and a silver pistol. Drawn by client/HunterFxClient
 * (FxPacket shape 89): a solid saw-cleaver with a blood-stained edge, crimson arcs, grey mist and silver shots.
 *
 * Its three ideas:
 *   Trick weapon - the weapon is SHORT (fast, close, wide) or LONG (slow, far-reaching); every strike changes with it,
 *                  and Trick Switch changes it with a strike of its own.
 *   Rally        - damage you take can be won back: hit a foe within 4 s and you heal half of what you deal, up to
 *                  what you lost.
 *   Stagger      - a silver shot to a foe that has just attacked (or a second shot in a row) staggers it: it can't
 *                  move, and a Visceral Strike on it lands four times over.
 *
 *  1 Saw Cleave        - SHORT: two quick cuts in front of you. LONG: one long sweep
 *  2 Trick Switch      - the weapon snaps to its other form with a strike: out to LONG with a lunging thrust, back to
 *                        SHORT with a spinning cut
 *  3 Silver Shot       - a quick silver shot at the foe you aim at (it can stagger)
 *  4 Quickstep         - you dissolve into mist and reappear six blocks away; for a moment nothing can hurt you,
 *                        and your next strike lands 30% harder
 *  5 Visceral Strike   - a thrust into the foe in front of you; a staggered foe takes it four times over and its
 *                        blood heals you
 *  6 Hunter's Mark     - mark a foe: for 10 s it takes 25% more from you, and if it dies marked your cooldowns refresh
 *  7 Rending Spin      - you spin with the weapon three times; SHORT: tighter and more cuts, LONG: wide
 *  8 Blood Volley      - pay a tenth of your health: five bullets of your own blood, each far stronger than silver
 *  9 Blood Moon (mode) - 20 s: a red moon rises over you. Rally wins back everything, every hit builds Frenzy
 *                        (+3% each, up to +30%), and you move faster
 * 10 Nightmare Cleave  - you leap and bring the weapon down: three great crimson arcs roll out under the moon.
 *                        Surpassing Limits: five arcs, heavier.
 */
@Mod.EventBusSubscriber(modid = "iron_spell_grimoire")
public final class HunterFx {
    private HunterFx() {}

    public static final String MODE = "blood_moon_hunt";
    private static final UUID STAGGER = UUID.fromString("c17d9280-6da7-4fe4-b177-d084fb000001");
    private static final String FORM_KEY = "isg_hunter_long";
    private static final double TAU = Math.PI * 2;

    // ================================================================== the client shapes (HunterFxClient, shape 89)
    private static FxPacket pk(int kind, Vec3 c, int life) {
        FxPacket p = new FxPacket();
        p.shape = 89; p.id = FxSend.nextId(); p.h = kind; p.c = c; p.life = life; p.a1 = (float) (Math.random() * 1000);
        return p;
    }
    private static void send(ServerLevel sl, FxPacket p) { FxSend.sendNear(sl, p.c, p); }

    /** 0 a swing of the weapon round a player from yaw-offset a0 to d (radians) over {@code sweep}; r2 = 1 long form; r = tilt. */
    static void swing(ServerLevel sl, ServerPlayer pl, double from, double to, float tilt, int sweep, int life) {
        FxPacket p = pk(0, pl.position(), life);
        p.v = new Vec3(pl.getId(), 1, 0); p.a0 = (float) from; p.d = new Vec3(to, 0, 0); p.r = tilt; p.r2 = isLong(pl) ? 1 : 0; p.speed = sweep;
        send(sl, p);
    }
    /** 1 a shot from c to u (a0 = 1: blood). */
    static void shot(ServerLevel sl, Vec3 c, Vec3 u, boolean blood) { FxPacket p = pk(1, c, 6); p.u = u; p.a0 = blood ? 1 : 0; send(sl, p); }
    /** 2 the mist of a quickstep from c to u. */
    static void mist(ServerLevel sl, Vec3 c, Vec3 u, float h) { FxPacket p = pk(2, c, 18); p.u = u; p.r = h; send(sl, p); }
    /** 3 a staggered foe. */
    static void stagger(ServerLevel sl, LivingEntity t, int life) { FxPacket p = pk(3, t.position(), life); p.v = new Vec3(t.getId(), 1, 0); send(sl, p); }
    /** 4 a visceral burst of blood out of a foe, toward d. */
    static void visceral(ServerLevel sl, LivingEntity t, Vec3 d, float size) { FxPacket p = pk(4, mid(t), 22); p.d = d; p.r = size; p.v = new Vec3(t.getId(), 1, 0); send(sl, p); }
    /** 5 the hunter's mark over a foe. */
    static void mark(ServerLevel sl, LivingEntity t, int life) { FxPacket p = pk(5, t.position(), life); p.v = new Vec3(t.getId(), 1, 0); send(sl, p); }
    /** 7 the blood moon over a player. */
    static void moon(ServerLevel sl, ServerPlayer pl, int life) { FxPacket p = pk(7, pl.position(), life); p.v = new Vec3(pl.getId(), 1, 0); send(sl, p); }
    /** 8 the nightmare arcs: rolling from c along d to r, n of them. */
    static void arcs(ServerLevel sl, Vec3 c, Vec3 d, float r, int n, int life) { FxPacket p = pk(8, c, life); p.d = d; p.r = r; p.a0 = n; send(sl, p); }
    /** 9 rally: health won back, glowing on a player. */
    static void rallyFx(ServerLevel sl, ServerPlayer pl) { FxPacket p = pk(9, pl.position(), 10); p.v = new Vec3(pl.getId(), 1, 0); send(sl, p); }
    /** 10 the trick: the weapon snapping to its other form in a player's hand (a0 = 1: to long). */
    static void trick(ServerLevel sl, ServerPlayer pl, boolean toLong) { FxPacket p = pk(10, pl.position(), 12); p.v = new Vec3(pl.getId(), 1, 0); p.a0 = toLong ? 1 : 0; send(sl, p); }

    // ================================================================== state
    static void play(ServerLevel sl, Vec3 at, String kind, float vol, float pitch) {
        SoundEvent s = AddonSounds.get(kind);
        if (s != null) sl.playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, vol, pitch);
    }

    public static boolean isLong(ServerPlayer p) { return p.getPersistentData().getBoolean(FORM_KEY); }
    static void setLong(ServerPlayer p, boolean v) { p.getPersistentData().putBoolean(FORM_KEY, v); }

    /** Rally per player: {amount that can be won back, until}. */
    private static final Map<UUID, float[]> RALLY = new HashMap<>();
    /** When each foe last attacked something (for the stagger). */
    private static final Map<UUID, Long> ATTACKED = new HashMap<>();
    /** Last foe shot, and when (two shots in a row stagger). */
    private static final Map<UUID, long[]> LAST_SHOT = new HashMap<>();
    /** Staggered foes: until. */
    private static final Map<UUID, Long> STAGGERED = new HashMap<>();
    /** Hunter's marks: foe -> {player id, until}. */
    private static final Map<UUID, long[]> MARKS = new HashMap<>();
    /** Quickstep's bonus, and Frenzy stacks. */
    private static final Map<UUID, Long> STEP = new HashMap<>();
    private static final Map<UUID, Integer> FRENZY = new HashMap<>();

    static boolean bloodMoon(ServerPlayer p) { return Moves.inMode(p, MODE); }

    public static boolean equipped(ServerPlayer p) {
        var b = io.redspace.ironsspellbooks.api.util.Utils.getPlayerSpellbookStack(p);
        return b != null && b.is(com.ironspellgrimoire.registry.AddonItemRegistry.HUNTER_GRIMOIRE.get());
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent e) {
        if (e.getSource().getEntity() instanceof LivingEntity a && a.level() instanceof ServerLevel sl) ATTACKED.put(a.getUUID(), sl.getGameTime());
        if (e.getEntity() instanceof ServerPlayer p && equipped(p)) {   // Rally: what you lose, you can win back
            long now = p.serverLevel().getGameTime();
            float[] r = RALLY.get(p.getUUID());
            float have = r == null || now > r[1] ? 0 : r[0];
            RALLY.put(p.getUUID(), new float[]{Math.min(p.getMaxHealth(), have + e.getAmount()), now + 80});
        }
        if (ATTACKED.size() > 400) ATTACKED.clear();
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent e) {
        long[] mk = MARKS.remove(e.getEntity().getUUID());
        if (mk == null || !(e.getEntity().level() instanceof ServerLevel sl)) return;
        if (sl.getGameTime() <= mk[1] && sl.getEntity((int) mk[0]) instanceof ServerPlayer p) {   // a marked kill: the hunt goes on
            try { var cd = io.redspace.ironsspellbooks.api.magic.MagicData.getPlayerMagicData(p).getPlayerCooldowns(); cd.tick(400); cd.syncToPlayer(p); } catch (Throwable ignored) {}
            p.displayClientMessage(Component.literal("Prey slaughtered - your cooldowns refresh").withStyle(ChatFormatting.DARK_RED), true);
            play(sl, p.position(), "blood_splash", 1.0f, 0.7f);
        }
    }

    /** A hunter's hit: Mark, Quickstep and Frenzy add to it; Rally wins health back from it. */
    static void hit(ServerPlayer p, LivingEntity t, float amt, String id) {
        ServerLevel sl = p.serverLevel();
        long now = sl.getGameTime();
        long[] mk = MARKS.get(t.getUUID());
        if (mk != null && mk[0] == p.getId() && now <= mk[1]) amt *= 1.25f;
        Long st = STEP.remove(p.getUUID());
        if (st != null && now - st < 30) amt *= 1.3f;
        if (bloodMoon(p)) {
            int fz = FRENZY.merge(p.getUUID(), 1, (a, b) -> Math.min(10, a + b));
            amt *= 1f + 0.03f * fz;
        }
        float before = t.getHealth();
        AstaFx.damage(p, t, amt, id);
        float dealt = Math.max(0, before - t.getHealth());
        float[] r = RALLY.get(p.getUUID());
        if (r != null && now <= r[1] && r[0] > 0 && dealt > 0) {
            float back = Math.min(r[0], dealt * (bloodMoon(p) ? 1.0f : 0.5f));
            r[0] -= back;
            p.heal(back);
            rallyFx(sl, p);
        }
    }

    static boolean staggered(LivingEntity t, ServerLevel sl) { Long s = STAGGERED.get(t.getUUID()); return s != null && s > sl.getGameTime(); }

    static void doStagger(ServerLevel sl, LivingEntity t) {
        int T = IceFx.boss(t) ? 24 : 44;
        STAGGERED.put(t.getUUID(), sl.getGameTime() + T);
        TimedModifiers.apply(t, Attributes.MOVEMENT_SPEED, STAGGER, "Staggered", -1.0, AttributeModifier.Operation.MULTIPLY_TOTAL, T);
        stagger(sl, t, T);
        play(sl, mid(t), "gunshot", 0.6f, 0.6f);
        sound(sl, mid(t), SoundEvents.SHIELD_BREAK, 1.0f, 0.8f);
    }

    private static Vec3 look(ServerPlayer p) {
        Vec3 l = flat(p.getLookAngle());
        return l.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : l.normalize();
    }

    /** Foes within reach in an arc of half-angle {@code half} (cosine) in front. */
    private static java.util.List<LivingEntity> arc(ServerPlayer p, double reach, double cosHalf) {
        Vec3 eye = p.getEyePosition(), lk = look(p);
        java.util.List<LivingEntity> l = Kit.near(p.serverLevel(), p, eye, reach);
        l.removeIf(e -> { Vec3 to = flat(mid(e).subtract(eye)); return Math.abs(mid(e).y - eye.y) > 3 || (to.lengthSqr() > 1.0E-4 && to.normalize().dot(lk) < cosHalf); });
        return l;
    }

    private static LivingEntity front(ServerPlayer p, double reach) {
        LivingEntity t = aimed(p, reach, 25);
        if (t != null) return t;
        LivingEntity best = null;
        double bd = reach + 1;
        for (LivingEntity e : arc(p, reach, 0.5)) { double d = e.distanceTo(p); if (d < bd) { bd = d; best = e; } }
        return best;
    }

    // ================================================================== 1. Saw Cleave
    public static void sawCleave(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        if (isLong(p)) {
            swing(sl, p, 1.4, -1.4, 0.15f, 6, 14);
            AstaFx.later(3, () -> {
                if (!alive(p)) return;
                for (LivingEntity e : arc(p, 6.2, 0.0)) { hit(p, e, power * 1.0f, "saw_cleave"); Kit.push(e, look(p), 0.4, 0.15); }
                play(sl, p.position(), "saw", 1.0f, 0.8f);
            });
        } else {
            for (int i = 0; i < 2; i++) {
                final int k = i;
                AstaFx.later(1 + i * 5, () -> {
                    if (!alive(p)) return;
                    swing(sl, p, k == 0 ? 1.0 : -1.0, k == 0 ? -1.0 : 1.0, k == 0 ? 0.3f : -0.3f, 3, 9);
                    for (LivingEntity e : arc(p, 4.0, 0.35)) hit(p, e, power * 0.6f, "saw_cleave");
                    play(sl, p.position(), "saw", 0.8f, 1.1f + k * 0.15f);
                });
            }
        }
    }

    // ================================================================== 2. Trick Switch
    public static void trickSwitch(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        boolean toLong = !isLong(p);
        setLong(p, toLong);
        trick(sl, p, toLong);
        play(sl, p.position(), "trick", 1.0f, toLong ? 0.9f : 1.2f);
        if (toLong) {   // out it snaps, with a lunging thrust
            Vec3 lk = look(p);
            swing(sl, p, 0.05, -0.05, 0f, 3, 10);
            p.setDeltaMovement(lk.x * 0.7, 0.05, lk.z * 0.7);
            p.hurtMarked = true;
            AstaFx.later(3, () -> {
                if (!alive(p)) return;
                Vec3 a = p.getEyePosition().add(0, -0.4, 0), b = a.add(look(p).scale(6.5));
                for (LivingEntity e : Kit.along(sl, p, a, b, 1.2)) hit(p, e, power * 1.0f, "trick_switch");
            });
        } else {        // back it folds, with a spinning cut
            swing(sl, p, 0, -TAU, 0.05f, 7, 14);
            AstaFx.later(3, () -> {
                if (!alive(p)) return;
                for (LivingEntity e : Kit.near(sl, p, p.position().add(0, 1, 0), 3.8)) { hit(p, e, power * 0.8f, "trick_switch"); Kit.push(e, mid(e).subtract(p.position()), 0.6, 0.2); }
            });
        }
    }

    // ================================================================== 3. Silver Shot
    public static void silverShot(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        LivingEntity t = aimed(p, 26, 8);
        Vec3 from = Kit.hand(p);
        Vec3 to = t != null ? mid(t) : clip(sl, p, p.getEyePosition(), p.getEyePosition().add(p.getLookAngle().scale(26)));
        shot(sl, from, to, false);
        play(sl, from, "gunshot", 1.0f, 1.0f);
        if (t == null) return;
        hit(p, t, power * 0.5f, "silver_shot");
        long now = sl.getGameTime();
        Long att = ATTACKED.get(t.getUUID());
        long[] last = LAST_SHOT.get(p.getUUID());
        boolean second = last != null && last[0] == t.getId() && now - last[1] < 60;
        if ((att != null && now - att < 30) || second) {   // caught mid-attack (or shot twice): staggered
            doStagger(sl, t);
            LAST_SHOT.remove(p.getUUID());
            p.displayClientMessage(Component.literal("Staggered - Visceral Strike it!").withStyle(ChatFormatting.GOLD), true);
        } else LAST_SHOT.put(p.getUUID(), new long[]{t.getId(), now});
    }

    // ================================================================== 4. Quickstep
    public static void quickstep(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        Vec3 from = p.position();
        Vec3 lk = look(p);
        Vec3 dest = clip(sl, p, p.getEyePosition(), p.getEyePosition().add(lk.scale(6))).subtract(lk.scale(0.5)).subtract(0, p.getEyeHeight(), 0);
        Vec3 safe = SpaceFx.safe(sl, dest, p);
        if (safe == null) safe = from;
        mist(sl, from, safe, p.getBbHeight());
        SpaceFx.move(p, safe, p.getYRot());
        p.invulnerableTime = Math.max(p.invulnerableTime, 14);
        STEP.put(p.getUUID(), sl.getGameTime());
        play(sl, from, "quickstep", 1.0f, 1.0f);
    }

    // ================================================================== 5. Visceral Strike
    public static void visceralStrike(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        LivingEntity t = front(p, 4.5);
        swing(sl, p, 0.0, 0.0, -0.6f, 4, 10);
        if (t == null) { play(sl, p.position(), "saw", 0.6f, 1.4f); return; }
        final LivingEntity tt = t;
        boolean st = staggered(t, sl);
        AstaFx.later(3, () -> {
            if (!alive(p) || !tt.isAlive()) return;
            Vec3 d = mid(tt).subtract(p.getEyePosition()).normalize();
            if (st) {
                STAGGERED.remove(tt.getUUID());
                float before = tt.getHealth();
                hit(p, tt, power * 3.6f, "visceral_strike");
                p.heal(Math.max(0, before - tt.getHealth()) * 0.2f);
                visceral(sl, tt, d, 1.6f);
                FxSend.impactFrame(sl, mid(tt), 3, FxSend.FRAME_BLOOD, 16);
                FxSend.shake(sl, mid(tt), 0.5f, 6, 14);
                play(sl, mid(tt), "blood_splash", 1.4f, 0.6f);
                sound(sl, mid(tt), SoundEvents.PLAYER_ATTACK_CRIT, 1.0f, 0.6f);
            } else {
                hit(p, tt, power * 0.9f, "visceral_strike");
                visceral(sl, tt, d, 0.6f);
                play(sl, mid(tt), "saw", 0.8f, 0.9f);
            }
        });
    }

    // ================================================================== 6. Hunter's Mark
    public static void huntersMark(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        LivingEntity t = aimed(p, 30, 12);
        if (t == null) t = front(p, 16);
        if (t == null) { play(sl, p.position(), "heartbeat", 0.6f, 1.2f); return; }
        MARKS.put(t.getUUID(), new long[]{p.getId(), sl.getGameTime() + 200});
        mark(sl, t, 200);
        hit(p, t, power * 0.3f, "hunters_mark");
        play(sl, t.position(), "heartbeat", 1.0f, 0.8f);
        sound(sl, t.position(), SoundEvents.BELL_BLOCK, 0.6f, 0.5f);
    }

    // ================================================================== 7. Rending Spin
    public static void rendingSpin(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        final boolean lng = isLong(p);
        final double R = lng ? 5.2 : 3.6;
        final int turns = 3, per = lng ? 7 : 5;
        for (int k = 0; k < turns; k++) {
            final int kk = k;
            AstaFx.later(1 + k * per, () -> {
                if (!alive(p)) return;
                swing(sl, p, 0, -TAU, 0.1f * (kk % 2 == 0 ? 1 : -1), per, per + 4);
                for (LivingEntity e : Kit.near(sl, p, p.position().add(0, 1, 0), R)) hit(p, e, power * (lng ? 0.5f : 0.42f), "rending_spin");
                play(sl, p.position(), "saw", 0.8f, 1.0f + kk * 0.1f);
            });
        }
        if (!lng) AstaFx.later(1 + turns * per, () -> { if (alive(p)) for (LivingEntity e : Kit.near(sl, p, p.position().add(0, 1, 0), R)) hit(p, e, power * 0.3f, "rending_spin"); });
    }

    // ================================================================== 8. Blood Volley
    public static void bloodVolley(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        if (!p.isCreative()) p.setHealth(Math.max(1f, p.getHealth() - p.getMaxHealth() * 0.1f));
        RALLY.merge(p.getUUID(), new float[]{p.getMaxHealth() * 0.1f, sl.getGameTime() + 80}, (a, b) -> new float[]{a[0] + b[0], b[1]});   // what you paid can be rallied back
        LivingEntity t = aimed(p, 26, 10);
        for (int i = 0; i < 5; i++) {
            final int k = i;
            AstaFx.later(1 + i * 2, () -> {
                if (!alive(p)) return;
                Vec3 from = Kit.hand(p);
                Vec3 base = t != null && t.isAlive() ? mid(t) : clip(sl, p, p.getEyePosition(), p.getEyePosition().add(p.getLookAngle().scale(26)));
                Vec3 to = base.add((Math.random() - 0.5) * 0.6, (Math.random() - 0.5) * 0.6, (Math.random() - 0.5) * 0.6);
                shot(sl, from, to, true);
                for (LivingEntity e : Kit.along(sl, p, from, to, 0.8)) hit(p, e, power * 0.45f, "blood_volley");
                play(sl, from, "gunshot", 0.9f, 0.8f + k * 0.05f);
            });
        }
        play(sl, p.position(), "blood_splash", 0.8f, 1.2f);
    }

    // ================================================================== 9. Blood Moon (mode)
    public static void bloodMoonHunt(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        final int T = 400;
        FRENZY.put(p.getUUID(), 0);
        moon(sl, p, T);
        Moves.mode(p, MODE, T, 20, pl -> {});
        Moves.stance(p, 0, 0, 0.2, 0.2, 0.2, T);
        FxSend.impactFrame(sl, p.position().add(0, 1, 0), 2, FxSend.FRAME_BLOOD, 16);
        play(sl, p.position(), "heartbeat", 1.6f, 0.6f);
        sound(sl, p.position(), SoundEvents.WOLF_HOWL, 0.7f, 0.6f);
        p.displayClientMessage(Component.literal("The blood moon rises. The hunt begins.").withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC), true);
    }

    // ================================================================== 10. Nightmare Cleave
    public static void nightmareCleave(ServerPlayer p, float power) {
        boolean limit = LimitBreak.active(p);
        ServerLevel sl = p.serverLevel();
        final float pow = power * (limit ? 1.3f : 1f);
        final Vec3 lk = look(p);
        final int N = limit ? 5 : 3;
        final float L = limit ? 18f : 14f;
        p.setDeltaMovement(lk.x * 0.3, 0.9, lk.z * 0.3);
        p.hurtMarked = true;
        p.fallDistance = 0;
        swing(sl, p, 0.0, 0.0, -1.4f, 10, 16);
        play(sl, p.position(), "trick", 1.2f, 0.6f);
        AstaFx.later(10, () -> {   // and down
            if (!alive(p)) return;
            Vec3 c = p.position();
            p.setDeltaMovement(0, -1.6, 0);
            p.hurtMarked = true;
            p.fallDistance = 0;
            arcs(sl, c.add(lk.scale(0.8)), lk, L, N, 26);
            for (int k = 0; k < N; k++) {
                final int kk = k;
                AstaFx.later(2 + k * 4, () -> {
                    if (!alive(p)) return;
                    double reach = L * (kk + 1) / N;
                    Vec3 a = c.add(lk.scale(reach * 0.6)), b = c.add(lk.scale(reach));
                    for (LivingEntity e : Kit.along(sl, p, a.add(rightOf(lk).scale(-2.5)).add(0, 1, 0), b.add(rightOf(lk).scale(2.5)).add(0, 1, 0), 2.6)) hit(p, e, pow * 0.75f, "nightmare_cleave");
                    play(sl, b, "saw", 1.2f, 0.5f + kk * 0.08f);
                });
            }
            FxSend.impactFrame(sl, c.add(0, 1, 0), 3, FxSend.FRAME_BLOOD, 24);
            FxSend.shake(sl, c, 0.8f, 12, 30);
            sound(sl, c, SoundEvents.GENERIC_EXPLODE, 0.8f, 0.6f);
        });
    }

    /** Surpassing Limits: the blood moon flares red over you for a moment. */
    public static void awaken(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        moon(sl, p, 50);
        swing(sl, p, 0, -TAU, 0.2f, 10, 14);
        play(sl, p.position(), "heartbeat", 1.4f, 0.7f);
    }
}
