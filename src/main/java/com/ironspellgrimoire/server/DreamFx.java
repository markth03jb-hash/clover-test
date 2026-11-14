package com.ironspellgrimoire.server;

import static com.ironspellgrimoire.server.FxUtil.*;

import com.ironspellgrimoire.network.FxPacket;
import com.ironspellgrimoire.registry.AddonItemRegistry;
import com.ironspellgrimoire.registry.AddonSounds;
import io.redspace.ironsspellbooks.api.magic.MagicData;
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
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The Dream Grimoire - inspired by a sleepy Black Clover captain whose Dream Magic builds a world where she can make
 * anything (all names and looks here are original). Soft pastel night: coral pink and peacock teal, sheep-shaped
 * clouds, floating clocks, plush toys and a big sleepy moon.
 *
 * Passive - Drowsy: every Dream spell makes its targets drowsy (slower, shown as a little meter over them). A target
 * that's fully drowsy falls Asleep for 3 s - it can't move or hit back - and the first blow wakes it, landing 50%
 * harder. And you: stand still for 2 s and you doze - your mana comes back three times as fast, and you still see
 * everything round you.
 * Drawn by client/DreamFxClient (FxPacket shape 96 + 1 = 97).
 *
 *  1 Lullaby Mote      - a homing star mote; +35 drowsy
 *  2 Counting Sheep    - dream sheep bound through everything in a line, each one +12 drowsy
 *  3 Pillow Fortress   - a soft cloud dome round you for 6 s: anyone inside it is slowed and drowsier by the second
 *  4 Sleepwalk         - you blink ahead and leave a sleeping you behind: it draws foes in, then puffs into sleep dust
 *  5 Dream Clock       - a floating clock: everything round it ticks slow for 5 s
 *  6 Toy Parade        - plush dream toys march out and pummel everything ahead
 *  7 Nightmare Shard   - a dark dream of the foe you aim at strikes it from behind: far harder if it's asleep
 *  8 Rude Awakening    - an alarm rings: every sleeping foe nearby is woken with a crash
 *  9 Half Asleep       - 20 s mode: you float, dozing yet aware - motes fly at nearby foes on their own, blows on you
 *                        knock a toy loose to strike back, and your spells make foes twice as drowsy
 * 10 Dream World       - a pastel dream unfolds round you: everything inside is slowed and shrinks into sleep in waves,
 *                        toys rain down, then a sleepy moon comes down on them all. Surpassing Limits: wider, a second
 *                        wave.
 */
@Mod.EventBusSubscriber(modid = "iron_spell_grimoire")
public final class DreamFx {
    private DreamFx() {}

    public static final String MODE = "half_asleep";
    static final String DROWSY = "isg_dr", DROWSY_T = "isg_dr_t", SLEEP = "isg_dr_sleep", STILL = "isg_dr_still", LASTPOS = "isg_dr_pos";
    private static final UUID DROWSY_SLOW = UUID.fromString("d4e2a1b0-6c3f-4f2a-9d51-7e0c40000001");
    private static final UUID SLEEP_SLOW = UUID.fromString("d4e2a1b0-6c3f-4f2a-9d51-7e0c40000002");
    private static final UUID SLEEP_WEAK = UUID.fromString("d4e2a1b0-6c3f-4f2a-9d51-7e0c40000003");
    private static final UUID CLOCK_SLOW = UUID.fromString("d4e2a1b0-6c3f-4f2a-9d51-7e0c40000004");
    private static final UUID WORLD_SHRINK = UUID.fromString("d4e2a1b0-6c3f-4f2a-9d51-7e0c40000005");
    private static final UUID FLOAT = UUID.fromString("d4e2a1b0-6c3f-4f2a-9d51-7e0c40000006");
    private static final double TAU = Math.PI * 2;

    // ================================================================== the client shapes (DreamFxClient, shape 97)
    private static FxPacket pk(int kind, Vec3 c, int life) {
        FxPacket p = new FxPacket();
        p.shape = 97; p.id = FxSend.nextId(); p.h = kind; p.c = c; p.life = life; p.a1 = (float) (Math.random() * 1000);
        return p;
    }
    private static void send(ServerLevel sl, FxPacket p) { FxSend.sendNear(sl, p.c, p); }

    /** 0 a star mote flying from c to u. */
    static void mote(ServerLevel sl, Vec3 c, Vec3 u, int life) { FxPacket p = pk(0, c, life); p.u = u; send(sl, p); }
    /** 1 sheep bounding from c along d, r long. */
    static void sheep(ServerLevel sl, Vec3 c, Vec3 d, float r, int n, int life) { FxPacket p = pk(1, c, life); p.d = d; p.r = r; p.a0 = n; send(sl, p); }
    /** 2 a pillow-cloud dome at c, radius r. */
    static void pillow(ServerLevel sl, Vec3 c, float r, int life) { FxPacket p = pk(2, c, life); p.r = r; send(sl, p); }
    /** 3 a sleeping double at c (yaw r), and the blink to u. */
    static void double_(ServerLevel sl, Vec3 c, Vec3 u, float yaw, int life) { FxPacket p = pk(3, c, life); p.u = u; p.r = yaw; send(sl, p); }
    /** 4 a floating clock over c, radius r. */
    static void clock(ServerLevel sl, Vec3 c, float r, int life) { FxPacket p = pk(4, c, life); p.r = r; send(sl, p); }
    /** 5 a parade of toys from c along d, r long. */
    static void parade(ServerLevel sl, Vec3 c, Vec3 d, float r, int life) { FxPacket p = pk(5, c, life); p.d = d; p.r = r; send(sl, p); }
    /** 6 a dark dream striking from behind at c, toward u. */
    static void nightmare(ServerLevel sl, Vec3 c, Vec3 u, int life) { FxPacket p = pk(6, c, life); p.u = u; send(sl, p); }
    /** 7 an alarm ring bursting at c, radius r. */
    static void alarm(ServerLevel sl, Vec3 c, float r, int life) { FxPacket p = pk(7, c, life); p.r = r; send(sl, p); }
    /** 8 the sleep marks over a sleeping entity. */
    static void zzz(ServerLevel sl, LivingEntity t, int life) { FxPacket p = pk(8, t.position(), life); p.v = new Vec3(t.getId(), 1, 0); p.r = t.getBbHeight(); send(sl, p); }
    /** 9 Half Asleep on a player. */
    static void halfAsleepAura(ServerLevel sl, ServerPlayer pl, int life) { FxPacket p = pk(9, pl.position(), life); p.v = new Vec3(pl.getId(), 1, 0); send(sl, p); }
    /** 10 the Dream World at c, radius r; sweep = the tick the moon lands. */
    static void world(ServerLevel sl, Vec3 c, float r, int moonAt, int life) { FxPacket p = pk(10, c, life); p.r = r; p.sweep = moonAt; send(sl, p); }
    /** 11 a little pastel burst at c. */
    static void puff(ServerLevel sl, Vec3 c, float size) { FxPacket p = pk(11, c, 10); p.r = size; send(sl, p); }

    // ================================================================== drowsiness
    public static boolean equipped(ServerPlayer p) {
        var b = io.redspace.ironsspellbooks.api.util.Utils.getPlayerSpellbookStack(p);
        return b != null && b.is(AddonItemRegistry.DREAM_GRIMOIRE.get());
    }

    public static float drowsy(LivingEntity t) { return t.getPersistentData().getFloat(DROWSY); }
    public static boolean asleep(LivingEntity t) { return t.level().getGameTime() < t.getPersistentData().getLong(SLEEP); }

    /** Make a target drowsier; full, it falls asleep. Half Asleep doubles it. */
    static void lull(ServerPlayer p, LivingEntity t, float by) {
        if (t == p || !t.isAlive() || asleep(t)) return;
        if (Moves.inMode(p, MODE)) by *= 2;
        float d = Math.min(100, drowsy(t) + by);
        t.getPersistentData().putFloat(DROWSY, d);
        t.getPersistentData().putLong(DROWSY_T, t.level().getGameTime());
        TimedModifiers.apply(t, Attributes.MOVEMENT_SPEED, DROWSY_SLOW, "Drowsy", -0.4 * d / 100.0, AttributeModifier.Operation.MULTIPLY_TOTAL, 60);
        if (d >= 100) fallAsleep((ServerLevel) t.level(), t, 60);
    }

    static void fallAsleep(ServerLevel sl, LivingEntity t, int ticks) {
        t.getPersistentData().putFloat(DROWSY, 0);
        t.getPersistentData().putLong(SLEEP, sl.getGameTime() + ticks);
        TimedModifiers.remove(t, Attributes.MOVEMENT_SPEED, DROWSY_SLOW);
        TimedModifiers.apply(t, Attributes.MOVEMENT_SPEED, SLEEP_SLOW, "Asleep", -0.95, AttributeModifier.Operation.MULTIPLY_TOTAL, ticks);
        TimedModifiers.apply(t, Attributes.ATTACK_DAMAGE, SLEEP_WEAK, "Asleep", -0.9, AttributeModifier.Operation.MULTIPLY_TOTAL, ticks);
        t.setDeltaMovement(0, Math.min(0, t.getDeltaMovement().y), 0);
        t.hurtMarked = true;
        zzz(sl, t, ticks);
        play(sl, t.position(), "dream_chime", 0.7f, 0.8f);
    }

    static void wake(LivingEntity t) {
        t.getPersistentData().remove(SLEEP);
        TimedModifiers.remove(t, Attributes.MOVEMENT_SPEED, SLEEP_SLOW);
        TimedModifiers.remove(t, Attributes.ATTACK_DAMAGE, SLEEP_WEAK);
        FxPacket k = pk(98, t.position(), 1); k.v = new Vec3(t.getId(), 1, 0);   // its sleep marks go
        FxSend.sendNear((ServerLevel) t.level(), t.position(), k);
    }

    /** A Dream hit: damage, and drowsier. */
    static void hit(ServerPlayer p, LivingEntity t, float amt, float lull, String id) {
        AstaFx.damage(p, t, amt, id);
        puff(p.serverLevel(), t.position().add(0, t.getBbHeight() * 0.6, 0), 0.6f);
        if (t.isAlive()) lull(p, t, lull);
    }

    static void play(ServerLevel sl, Vec3 at, String kind, float vol, float pitch) {
        SoundEvent s = AddonSounds.get(kind);
        if (s != null) sl.playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, vol, pitch);
    }

    private static Vec3 look(ServerPlayer p) {
        Vec3 l = flat(p.getLookAngle());
        return l.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : l.normalize();
    }

    /** Sleepers wake when struck - and that first blow lands harder. */
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent e) {
        LivingEntity t = e.getEntity();
        if (asleep(t)) {
            e.setAmount(e.getAmount() * 1.5f);
            wake(t);
        }
        if (t instanceof ServerPlayer p && Moves.inMode(p, MODE) && e.getSource().getEntity() instanceof LivingEntity a && a != p && a.isAlive()) {
            ServerLevel sl = p.serverLevel();   // Half Asleep: a toy tumbles loose and swats back
            parade(sl, p.position(), flat(a.position().subtract(p.position())).normalize(), (float) Math.min(6, a.distanceTo(p) + 1), 18);
            AstaFx.later(6, () -> { if (alive(p) && a.isAlive()) hit(p, a, 4f, 15f, "half_asleep"); });
        }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        if (!equipped(p)) {
            if (p.tickCount % 5 == 0 && Moves.inMode(p, MODE)) Moves.ends(p, MODE);
            if (p.tickCount % 40 == 17 && p.getPersistentData().contains(STILL)) { p.getPersistentData().remove(STILL); sync(p, 0); }
            return;
        }
        // dozing: stand still and you drift off - mana comes back faster, and you still see everything
        Vec3 was = new Vec3(p.getPersistentData().getDouble(LASTPOS + "x"), p.getPersistentData().getDouble(LASTPOS + "y"), p.getPersistentData().getDouble(LASTPOS + "z"));
        boolean still = p.position().distanceToSqr(was) < 0.0025 && !p.swinging;
        p.getPersistentData().putDouble(LASTPOS + "x", p.getX()); p.getPersistentData().putDouble(LASTPOS + "y", p.getY()); p.getPersistentData().putDouble(LASTPOS + "z", p.getZ());
        int s = still ? Math.min(200, p.getPersistentData().getInt(STILL) + 1) : 0;
        p.getPersistentData().putInt(STILL, s);
        if (s >= 40 && p.tickCount % 10 == 0) {
            MagicData data = MagicData.getPlayerMagicData(p);
            double max = p.getAttributeValue(io.redspace.ironsspellbooks.api.registry.AttributeRegistry.MAX_MANA.get());
            data.setMana((float) Math.min(max, data.getMana() + max * 0.02));   // on top of the normal regeneration
        }
        if (p.tickCount % 5 == 0) sync(p, Math.min(1f, s / 40f));
        // Half Asleep: motes find nearby foes by themselves
        if (Moves.inMode(p, MODE) && p.tickCount % 20 == 0) {
            ServerLevel sl = p.serverLevel();
            List<LivingEntity> near = Kit.near(sl, p, p.position().add(0, 1, 0), 10);
            near.sort(java.util.Comparator.comparingDouble(t -> t.distanceToSqr(p)));
            for (int i = 0; i < Math.min(2, near.size()); i++) {
                LivingEntity t = near.get(i);
                Vec3 from = p.position().add(0, 2.2, 0);
                mote(sl, from, t.position().add(0, t.getBbHeight() * 0.6, 0), 8);
                AstaFx.later(8, () -> { if (alive(p) && t.isAlive()) hit(p, t, 2.5f, 18f, "half_asleep"); });
            }
        }
    }

    /** Drowsiness wears off when the spells stop. */
    @SubscribeEvent
    public static void onLivingTick(net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent e) {
        LivingEntity t = e.getEntity();
        if (t.level().isClientSide || t.tickCount % 10 != 0) return;
        float d = t.getPersistentData().getFloat(DROWSY);
        if (d <= 0) return;
        if (t.level().getGameTime() - t.getPersistentData().getLong(DROWSY_T) > 60) t.getPersistentData().putFloat(DROWSY, Math.max(0, d - 5));
    }

    /** The doze meter on the player's screen (DreamFxClient, kind 99): a0 = how dozy, r = 1 if equipped, r2 = 1 in Half Asleep. */
    static void sync(ServerPlayer p, float doze) {
        if (p.connection == null) return;
        FxPacket h = new FxPacket();
        h.shape = 97; h.h = 99; h.a0 = doze; h.r = equipped(p) ? 1 : 0; h.r2 = Moves.inMode(p, MODE) ? 1 : 0; h.life = 1;
        com.ironspellgrimoire.network.AddonNetwork.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> p), h);
    }

    // ================================================================== 1. Lullaby Mote
    public static void lullabyMote(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        LivingEntity t = aimed(p, 24, 14);
        Vec3 from = p.getEyePosition().add(p.getLookAngle().scale(0.6));
        play(sl, from, "dream_chime", 0.9f, 1.3f);
        if (t == null) { mote(sl, from, from.add(p.getLookAngle().scale(14)), 12); return; }
        Vec3 to = t.position().add(0, t.getBbHeight() * 0.6, 0);
        int fly = Math.max(4, (int) (from.distanceTo(to) / 1.6));
        mote(sl, from, to, fly);
        AstaFx.later(fly, () -> { if (alive(p) && t.isAlive()) hit(p, t, power, 35f, "dream_mote"); });
    }

    // ================================================================== 2. Counting Sheep
    public static void countingSheep(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        Vec3 d = look(p), c = p.position().add(d.scale(0.8));
        final float L = 14f;
        final int N = 5;
        sheep(sl, c, d, L, N, 36);
        play(sl, c, "dream_boing", 1.0f, 1.0f);
        for (int k = 0; k < N; k++) {
            final int kk = k;
            AstaFx.later(4 + k * 4, () -> {
                if (!alive(p)) return;
                if (kk % 2 == 0) play(sl, c.add(d.scale(L * 0.4)), "dream_boing", 0.6f, 1.1f + 0.08f * kk);
                for (LivingEntity t : Kit.along(sl, p, c.add(0, 0.6, 0), c.add(0, 0.6, 0).add(d.scale(L)), 1.4)) {
                    t.invulnerableTime = 0;
                    hit(p, t, power * 0.25f, 12f, "counting_sheep");
                }
            });
        }
    }

    // ================================================================== 3. Pillow Fortress
    public static void pillowFortress(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        final Vec3 c = p.position();
        final float R = 4.5f;
        final int T = 120;
        pillow(sl, c, R, T);
        play(sl, c, "dream_hush", 1.0f, 1.0f);
        Moves.stance(p, 6, 2, 0, 0.5, 0, T);
        for (int i = 1; i <= T / 20; i++) {
            AstaFx.later(i * 20, () -> {
                if (!alive(p)) return;
                for (LivingEntity t : Kit.near(sl, p, c.add(0, 1, 0), R)) {
                    TimedModifiers.apply(t, Attributes.MOVEMENT_SPEED, CLOCK_SLOW, "Sunk in pillows", -0.35, AttributeModifier.Operation.MULTIPLY_TOTAL, 25);
                    lull(p, t, 14f);
                }
            });
        }
    }

    // ================================================================== 4. Sleepwalk
    public static void sleepwalk(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        Vec3 d = look(p), from = p.position();
        Vec3 to = clip(sl, p, from.add(0, 0.5, 0), from.add(0, 0.5, 0).add(d.scale(7))).subtract(0, 0.5, 0);
        final int T = 60;
        double_(sl, from, to, p.getYRot(), T);
        play(sl, from, "dream_hush", 0.8f, 1.3f);
        p.teleportTo(to.x, to.y, to.z);
        p.hurtMarked = true;
        // the sleeping double draws foes in
        for (LivingEntity t : Kit.near(sl, p, from.add(0, 1, 0), 10)) if (t instanceof net.minecraft.world.entity.Mob mob) mob.getNavigation().moveTo(from.x, from.y, from.z, 1.0);
        AstaFx.later(T - 4, () -> {   // and puffs into sleep dust
            if (!alive(p)) return;
            pillow(sl, from, 2.6f, 14);
            play(sl, from, "dream_chime", 0.9f, 0.9f);
            for (LivingEntity t : Kit.near(sl, p, from.add(0, 1, 0), 3.2)) hit(p, t, power * 0.6f, 45f, "sleepwalk");
        });
    }

    // ================================================================== 5. Dream Clock
    public static void dreamClock(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        Vec3 at = Kit.aim(p, 14);
        final Vec3 c = at;
        final float R = 5f;
        final int T = 100;
        clock(sl, c, R, T);
        play(sl, c, "dream_tick", 1.0f, 1.0f);
        for (int i = 0; i < T / 10; i++) {
            final int k = i;
            AstaFx.later(i * 10, () -> {
                if (!alive(p)) return;
                if (k % 2 == 0) play(sl, c, "dream_tick", 0.5f, k % 4 == 0 ? 1.0f : 1.2f);
                for (LivingEntity t : Kit.near(sl, p, c.add(0, 1, 0), R)) {
                    TimedModifiers.apply(t, Attributes.MOVEMENT_SPEED, CLOCK_SLOW, "Slow time", -0.55, AttributeModifier.Operation.MULTIPLY_TOTAL, 14);
                    TimedModifiers.apply(t, Attributes.ATTACK_SPEED, CLOCK_SLOW, "Slow time", -0.5, AttributeModifier.Operation.MULTIPLY_TOTAL, 14);
                    t.setDeltaMovement(t.getDeltaMovement().scale(0.4));
                    if (k % 3 == 0) lull(p, t, 10f);
                }
            });
        }
        for (LivingEntity t : Kit.near(sl, p, c.add(0, 1, 0), R)) hit(p, t, power * 0.5f, 10f, "dream_clock");
    }

    // ================================================================== 6. Toy Parade
    public static void toyParade(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        Vec3 d = look(p), c = p.position().add(d.scale(0.8));
        final float L = 10f;
        parade(sl, c, d, L, 40);
        play(sl, c, "dream_boing", 1.0f, 0.8f);
        for (int k = 0; k < 4; k++) {
            final int kk = k;
            AstaFx.later(6 + k * 6, () -> {
                if (!alive(p)) return;
                Vec3 head = c.add(d.scale(L * (0.35 + 0.2 * kk)));
                play(sl, head, "dream_boing", 0.7f, 0.9f + 0.1f * kk);
                for (LivingEntity t : Kit.near(sl, p, head.add(0, 0.8, 0), 3.0)) {
                    t.invulnerableTime = 0;
                    hit(p, t, power * 0.4f, 8f, "toy_parade");
                    Kit.push(t, d, 0.25 * GravityFx.give(t), 0.25);
                }
            });
        }
    }

    // ================================================================== 7. Nightmare Shard
    public static void nightmareShard(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        LivingEntity t = aimed(p, 20, 12);
        if (t == null) {
            Vec3 at = Kit.aim(p, 12);
            nightmare(sl, at.add(look(p).scale(1.2)), at, 18);
            for (LivingEntity e : Kit.near(sl, p, at.add(0, 1, 0), 2.5)) hit(p, e, power * 0.6f, 10f, "nightmare_shard");
            return;
        }
        Vec3 behind = t.position().subtract(flat(p.position().subtract(t.position())).normalize().scale(1.4));
        nightmare(sl, behind, t.position(), 20);
        play(sl, behind, "dream_hush", 1.0f, 0.5f);
        final boolean sleeping = asleep(t);
        AstaFx.later(6, () -> {
            if (!alive(p) || !t.isAlive()) return;
            play(sl, t.position(), "dream_chime", 1.0f, 0.5f);
            hit(p, t, power * (sleeping ? 1.4f : 0.9f), 20f, "nightmare_shard");   // asleep: the wake-up blow lands on top
        });
    }

    // ================================================================== 8. Rude Awakening
    public static void rudeAwakening(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        Vec3 c = p.position();
        final float R = 14f;
        alarm(sl, c, R, 24);
        play(sl, c, "dream_alarm", 1.2f, 1.0f);
        int woke = 0;
        for (LivingEntity t : Kit.near(sl, p, c.add(0, 1, 0), R)) {
            boolean s = asleep(t);
            float d = drowsy(t);
            alarm(sl, t.position(), 1.6f, 14);
            AstaFx.damage(p, t, power * (s ? 1.3f : 0.25f + 0.5f * d / 100f), "rude_awakening");   // asleep: the full crash; drowsy: some of it
            if (s) woke++;
            t.getPersistentData().putFloat(DROWSY, 0);
        }
        if (woke > 0) p.displayClientMessage(Component.literal("Rise and shine! (" + woke + ")").withStyle(ChatFormatting.LIGHT_PURPLE), true);
    }

    // ================================================================== 9. Half Asleep (mode)
    public static void halfAsleep(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        final int T = 400;
        Moves.mode(p, MODE, T, 20, pl -> { });
        halfAsleepAura(sl, p, T);
        TimedModifiers.apply(p, Attributes.MOVEMENT_SPEED, FLOAT, "Drifting", 0.1, AttributeModifier.Operation.MULTIPLY_BASE, T);
        Moves.stance(p, 4, 0, 0, 0.3, 0, T);
        play(sl, p.position(), "dream_hush", 1.2f, 0.8f);
        sound(sl, p.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 0.6f);
        sync(p, 1);
    }

    // ================================================================== 10. Dream World
    public static void dreamWorld(ServerPlayer p, float power) {
        boolean limit = LimitBreak.active(p);
        ServerLevel sl = p.serverLevel();
        final Vec3 c = p.position();
        final float R = limit ? 18f : 13f;
        final int MOON = limit ? 70 : 50;
        world(sl, c, R, MOON, MOON + 30);
        play(sl, c, "dream_hush", 1.4f, 0.7f);
        sound(sl, c, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.2f, 0.6f);
        final int waves = limit ? 3 : 2;
        for (int w = 0; w < waves; w++) {
            AstaFx.later(8 + w * 18, () -> {   // waves of sleep, and toys tumbling down
                if (!alive(p)) return;
                play(sl, c, "dream_chime", 1.2f, 0.9f);
                List<LivingEntity> in = new ArrayList<>(Kit.near(sl, p, c.add(0, 1, 0), R));
                for (LivingEntity t : in) {
                    TimedModifiers.apply(t, Attributes.MOVEMENT_SPEED, WORLD_SHRINK, "Lost in a dream", -0.5, AttributeModifier.Operation.MULTIPLY_TOTAL, 40);
                    t.invulnerableTime = 0;
                    hit(p, t, power * 0.2f, 45f, "dream_world");
                    parade(sl, t.position().add(0, 6, 0), new Vec3(0, -1, 0), 6f, 14);   // a toy drops on it
                }
            });
        }
        AstaFx.later(MOON, () -> {   // the sleepy moon comes down
            if (!alive(p)) return;
            play(sl, c, "dream_alarm", 1.4f, 0.6f);
            sound(sl, c, SoundEvents.GENERIC_EXPLODE, 0.8f, 1.3f);
            FxSend.shake(sl, c, 0.6f, 12, R + 8);
            for (LivingEntity t : Kit.near(sl, p, c.add(0, 1, 0), R)) {
                t.invulnerableTime = 0;
                AstaFx.damage(p, t, power * (asleep(t) ? 1.6f : 1.1f) * (limit ? 1.25f : 1f), "dream_world");
                Kit.push(t, flat(t.position().subtract(c)), 0.6 * GravityFx.give(t), 0.3);
            }
        });
    }

    /** Surpassing Limits: you drift off into the dream - Half Asleep for a few seconds. */
    public static void awaken(ServerPlayer p) {
        Moves.mode(p, MODE, 120, 20, pl -> { });
        halfAsleepAura(p.serverLevel(), p, 120);
    }
}
