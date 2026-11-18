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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Chain Grimoire (an original grimoire): five chains, each bound by its own rule, drawn out of your hand.
 *   Binding  (cold silver, a cross-shaped weight) - holds
 *   Healing  (warm gold, a round cup)             - mends
 *   Judgment (crimson, a blade point)             - punishes whoever breaks its vow
 *   Dowsing  (pale violet, a crystal pendulum)    - reads foes and finds them
 *   Stealing (dark teal, a barbed hook)           - takes
 * Drawn by client/ChainFxClient (FxPacket shape 88): solid links of metal that sag, whip and tighten.
 *
 *  1 Chain Lash         - a whip of chain cracks across everything in front of you
 *  2 Binding Chain      - the Binding chain wraps the foe you aim at and holds it for 3 s (bosses 1.5 s)
 *  3 Dowsing Chain      - the pendulum swings: every foe within 24 blocks is shown to you and Read (Chain spells
 *                         hit it 20% harder for 8 s)
 *  4 Healing Chain      - the Healing chain links you to the ally you aim at (or to yourself) and mends them over
 *                         four seconds, washing out every weakening on them
 *  5 Judgment Chain     - the blade of judgment stakes the heart of a foe with a vow: if it hurts anyone in the next
 *                         10 s, the chain closes and it takes a terrible blow
 *  6 Stealing Chain     - the hook rips into a foe and takes: its health heals you, its speed becomes yours
 *  7 Chain Net          - a net of chains falls on your target's ground and holds everything under it
 *  8 Anchor Chain       - throw the anchor: aimed at a foe, you drag it to you; at the ground, it drags you there
 *  9 Restriction (mode) - 20 s: you bind yourself to a vow - every Chain spell hits 40% harder and comes back twice as
 *                         fast, but the vow takes a little of your health every two seconds
 * 10 Thousand Chains    - chains burst out of the ground round your target, bind every foe there, and crush them.
 *                         Surpassing Limits: wider, heavier.
 *
 * Passive "Five Rules": use all five chains (Binding, Healing, Judgment, Dowsing, Stealing) within 20 s and the next
 * Chain spell lands at double strength.
 */
@Mod.EventBusSubscriber(modid = "iron_spell_grimoire")
public final class ChainFx {
    private ChainFx() {}

    public static final String MODE = "restriction";
    public static final int BIND = 0, HEAL = 1, JUDGE = 2, DOWSE = 3, STEAL = 4, PLAIN = 5;
    private static final UUID HOLD = UUID.fromString("b06c8170-5c96-4fd3-a066-cf73fa000001");
    private static final UUID STOLEN = UUID.fromString("b06c8170-5c96-4fd3-a066-cf73fa000002");
    private static final UUID TAKEN = UUID.fromString("b06c8170-5c96-4fd3-a066-cf73fa000003");
    private static final double TAU = Math.PI * 2;

    // ================================================================== the client shapes (ChainFxClient, shape 88)
    private static FxPacket pk(int kind, Vec3 c, int life) {
        FxPacket p = new FxPacket();
        p.shape = 88; p.id = FxSend.nextId(); p.h = kind; p.c = c; p.life = life; p.a1 = (float) (Math.random() * 1000);
        return p;
    }
    private static void send(ServerLevel sl, FxPacket p) { FxSend.sendNear(sl, p.c, p); }

    /** 0 a chain from an entity's hand to another entity (or to the point u if {@code to} is null), shooting out over {@code reach} ticks. */
    static int chain(ServerLevel sl, LivingEntity from, LivingEntity to, Vec3 u, int type, int reach, int life) {
        FxPacket p = pk(0, from.position(), life);
        p.v = new Vec3(from.getId(), to == null ? 0 : to.getId(), 0); p.u = u == null ? Vec3.ZERO : u; p.a0 = type; p.speed = reach;
        send(sl, p);
        return p.id;
    }
    /** 1 a chain wrapped round an entity. */
    static int wrap(ServerLevel sl, LivingEntity t, int type, int life) { FxPacket p = pk(1, t.position(), life); p.v = new Vec3(t.getId(), 1, 0); p.a0 = type; send(sl, p); return p.id; }
    /** 2 a lash: an arc of chain round c from d0 to d1 (r long) over {@code sweep} ticks. */
    static void lash(ServerLevel sl, LivingEntity from, Vec3 d0, Vec3 d1, float r, int sweep, int life) {
        FxPacket p = pk(2, from.position(), life); p.v = new Vec3(from.getId(), 1, 0); p.d = d0; p.u = d1; p.r = r; p.speed = sweep; send(sl, p);
    }
    /** 3 the dowsing pendulum swinging from a player's hand. */
    static void pendulum(ServerLevel sl, LivingEntity from, int life) { FxPacket p = pk(3, from.position(), life); p.v = new Vec3(from.getId(), 1, 0); send(sl, p); }
    /** 4 a net of chains over c (r). */
    static void net(ServerLevel sl, Vec3 c, float r, int life) { FxPacket p = pk(4, c, life); p.r = r; send(sl, p); }
    /** 5 the judgment stake in a foe; 6 its chain closing. */
    static int stake(ServerLevel sl, LivingEntity t, int life) { FxPacket p = pk(5, t.position(), life); p.v = new Vec3(t.getId(), 1, 0); send(sl, p); return p.id; }
    static void verdict(ServerLevel sl, LivingEntity t) { FxPacket p = pk(6, t.position(), 14); p.v = new Vec3(t.getId(), 1, 0); send(sl, p); }
    /** 7 a chain bursting up out of the ground at c and binding up toward u (rising over {@code rise}). */
    static void erupt(ServerLevel sl, Vec3 c, Vec3 u, int type, int rise, int life) { FxPacket p = pk(7, c, life); p.u = u; p.a0 = type; p.speed = rise; send(sl, p); }
    /** 8 Restriction: the five chains drifting round a player. */
    static void aura(ServerLevel sl, ServerPlayer pl, int life) { FxPacket p = pk(8, pl.position(), life); p.v = new Vec3(pl.getId(), 1, 0); send(sl, p); }
    /** 9 the anchor flying from c to u over {@code fly} ticks. */
    static void anchor(ServerLevel sl, LivingEntity from, Vec3 u, int fly, int life) { FxPacket p = pk(9, from.position(), life); p.v = new Vec3(from.getId(), 1, 0); p.u = u; p.speed = fly; send(sl, p); }

    // ================================================================== helpers
    static void play(ServerLevel sl, Vec3 at, String kind, float vol, float pitch) {
        SoundEvent s = AddonSounds.get(kind);
        if (s != null) sl.playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, vol, pitch);
    }

    /** Five Rules: when each chain was last used, per player; and whether the full set is ready. */
    private static final Map<UUID, long[]> RULES = new HashMap<>();
    private static final Map<UUID, Boolean> FULL = new HashMap<>();
    /** Read foes (Dowsing): until when. */
    private static final Map<UUID, Long> READ = new HashMap<>();
    /** Judgment vows: foe -> {player id (as long), until, stake fx id}. */
    private static final Map<UUID, long[]> VOWS = new HashMap<>();

    static boolean restricted(ServerPlayer p) { return Moves.inMode(p, MODE); }

    /** Every Chain spell: notes which chain was used (Five Rules), and its power with the vow and a full set. */
    private static float use(ServerPlayer p, int type, float power) {
        long now = p.serverLevel().getGameTime();
        float k = restricted(p) ? 1.4f : 1f;
        if (Boolean.TRUE.equals(FULL.remove(p.getUUID()))) {
            k *= 2f;
            play(p.serverLevel(), p.position(), "chain_rattle", 1.0f, 0.6f);
        }
        if (type >= 0 && type < 5) {
            long[] r = RULES.computeIfAbsent(p.getUUID(), x -> new long[]{-999, -999, -999, -999, -999});
            r[type] = now;
            boolean all = true;
            for (long t : r) if (now - t > 400) { all = false; break; }
            if (all) {
                RULES.remove(p.getUUID());
                FULL.put(p.getUUID(), true);
                p.displayClientMessage(Component.literal("Five Rules - all five chains: the next one lands double").withStyle(ChatFormatting.GOLD), true);
                aura(p.serverLevel(), p, 30);
                play(p.serverLevel(), p.position(), "chain_rattle", 1.2f, 1.4f);
            }
        }
        if (restricted(p)) {   // the vow returns cooldowns twice as fast
            try { var cd = io.redspace.ironsspellbooks.api.magic.MagicData.getPlayerMagicData(p).getPlayerCooldowns(); cd.tick(20); cd.syncToPlayer(p); } catch (Throwable ignored) {}
        }
        return power * k;
    }

    static void hit(ServerPlayer p, LivingEntity t, float amt, String id) {
        Long r = READ.get(t.getUUID());
        if (r != null && r > p.serverLevel().getGameTime()) amt *= 1.2f;
        AstaFx.damage(p, t, amt, id);
    }

    static void hold(LivingEntity t, int ticks) {
        TimedModifiers.apply(t, Attributes.MOVEMENT_SPEED, HOLD, "Bound in chains", -1.0, AttributeModifier.Operation.MULTIPLY_TOTAL, IceFx.boss(t) ? ticks / 2 : ticks);
    }

    static boolean ally(ServerPlayer p, LivingEntity e) {
        if (e == p) return false;
        if (e instanceof Player) return true;
        if (e instanceof TamableAnimal ta && p.getUUID().equals(ta.getOwnerUUID())) return true;
        return e.isAlliedTo(p);
    }

    private static LivingEntity aimedFoe(ServerPlayer p, double range) {
        LivingEntity t = aimed(p, range, 12);
        if (t != null) return t;
        double best = range;
        for (LivingEntity e : Kit.near(p.serverLevel(), p, p.getEyePosition(), range)) {
            Vec3 to = mid(e).subtract(p.getEyePosition());
            if (to.normalize().dot(p.getLookAngle()) < 0.6) continue;
            if (to.length() < best) { best = to.length(); t = e; }
        }
        return t;
    }

    /** Judgment: a foe under a vow that hurts anyone has the chain close on it. */
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent e) {
        if (VOWS.isEmpty() || !(e.getSource().getEntity() instanceof LivingEntity attacker)) return;
        long[] v = VOWS.get(attacker.getUUID());
        if (v == null || !(attacker.level() instanceof ServerLevel sl)) return;
        if (sl.getGameTime() > v[1]) { VOWS.remove(attacker.getUUID()); return; }
        if (sl.getEntity((int) v[0]) instanceof ServerPlayer p && e.getEntity() != attacker) {
            VOWS.remove(attacker.getUUID());
            FxSend.kill(sl, (int) v[2], attacker.position());
            verdict(sl, attacker);
            hold(attacker, 40);
            final float dmg = Float.intBitsToFloat((int) v[3]);
            AstaFx.later(3, () -> { if (attacker.isAlive() && alive(p)) hit(p, attacker, dmg, "judgment_chain"); });
            FxSend.shake(sl, attacker.position(), 0.4f, 5, 16);
            play(sl, attacker.position(), "chain_rattle", 1.4f, 0.5f);
            sound(sl, attacker.position(), SoundEvents.ANVIL_LAND, 0.6f, 0.6f);
            p.displayClientMessage(Component.literal("The vow was broken - judgment").withStyle(ChatFormatting.DARK_RED), true);
        }
    }

    // ================================================================== 1. Chain Lash
    public static void chainLash(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        final float pow = use(p, -1, power);
        Vec3 look = flat(p.getLookAngle());
        if (look.lengthSqr() < 1.0E-4) look = new Vec3(0, 0, 1);
        final Vec3 lk = look.normalize();
        lash(sl, p, rotY(lk, 1.1), rotY(lk, -1.1), 7f, 5, 12);
        AstaFx.later(3, () -> {
            if (!alive(p)) return;
            Vec3 eye = p.getEyePosition();
            for (LivingEntity e : Kit.near(sl, p, eye, 7.5)) {
                Vec3 to = flat(mid(e).subtract(eye));
                if (to.lengthSqr() > 1.0E-4 && to.normalize().dot(lk) < 0.35) continue;
                if (Math.abs(mid(e).y - eye.y) > 3) continue;
                hit(p, e, pow * 0.75f, "chain_lash");
                Kit.push(e, rightOf(lk).scale(-1), 0.4, 0.15);
            }
            play(sl, eye, "chain_rattle", 0.9f, 1.3f);
        });
        sound(sl, p.position(), SoundEvents.CHAIN_PLACE, 1.0f, 1.4f);
    }

    // ================================================================== 2. Binding Chain
    public static void bindingChain(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        final float pow = use(p, BIND, power);
        LivingEntity t = aimedFoe(p, 24);
        if (t == null) { chain(sl, p, null, Kit.aim(p, 16), BIND, 4, 10); sound(sl, p.position(), SoundEvents.CHAIN_FALL, 0.8f, 1.3f); return; }
        final LivingEntity tt = t;
        int reach = Math.max(2, (int) Math.ceil(t.distanceTo(p) / 4));
        chain(sl, p, t, null, BIND, reach, reach + 10);
        play(sl, p.position(), "chain_rattle", 0.8f, 1.2f);
        AstaFx.later(reach, () -> {
            if (!alive(p) || !tt.isAlive()) return;
            wrap(sl, tt, BIND, 60);
            hold(tt, 60);
            hit(p, tt, pow * 0.5f, "binding_chain");
            for (int k = 20; k <= 60; k += 20) AstaFx.later(k, () -> { if (alive(p) && tt.isAlive()) hit(p, tt, pow * 0.15f, "binding_chain"); });
            sound(sl, tt.position(), SoundEvents.CHAIN_PLACE, 1.0f, 0.8f);
        });
    }

    // ================================================================== 3. Dowsing Chain
    public static void dowsingChain(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        final float pow = use(p, DOWSE, power);
        pendulum(sl, p, 40);
        long until = sl.getGameTime() + 160;
        int n = 0;
        for (LivingEntity e : Kit.near(sl, p, p.position().add(0, 1, 0), 24)) {
            AstaFx.sendKiMark(p, e, 160, false);
            READ.put(e.getUUID(), until);
            if (n++ < 6) {   // the pendulum points: a faint violet thread to each, and a small sting
                chain(sl, p, e, null, DOWSE, 6, 14);
                final LivingEntity ee = e;
                AstaFx.later(6, () -> { if (alive(p) && ee.isAlive()) hit(p, ee, pow * 0.2f, "dowsing_chain"); });
            }
        }
        READ.entrySet().removeIf(en -> en.getValue() < sl.getGameTime());
        play(sl, p.position(), "chain_rattle", 0.6f, 1.8f);
        sound(sl, p.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 1.2f);
    }

    // ================================================================== 4. Healing Chain
    public static void healingChain(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        use(p, HEAL, power);
        LivingEntity who = null;
        double best = Math.cos(Math.toRadians(14));
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(18), e -> e.isAlive() && ally(p, e))) {
            Vec3 to = mid(e).subtract(p.getEyePosition());
            double d = to.normalize().dot(p.getLookAngle());
            if (d > best && to.length() < 18) { best = d; who = e; }
        }
        final LivingEntity t = who == null ? p : who;
        if (t != p) chain(sl, p, t, null, HEAL, 4, 84);
        wrap(sl, t, HEAL, 80);
        TimedModifiers.clearDebuffs(t);
        final float each = Math.max(1f, power * 0.25f);
        for (int k = 0; k < 80; k += 10) AstaFx.later(k + 4, () -> { if (t.isAlive()) t.heal(each); });
        play(sl, t.position(), "chain_rattle", 0.6f, 1.6f);
        sound(sl, t.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0f, 1.4f);
    }

    // ================================================================== 5. Judgment Chain
    public static void judgmentChain(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        final float pow = use(p, JUDGE, power);
        LivingEntity t = aimedFoe(p, 24);
        if (t == null) { chain(sl, p, null, Kit.aim(p, 16), JUDGE, 4, 10); return; }
        final LivingEntity tt = t;
        int reach = Math.max(2, (int) Math.ceil(t.distanceTo(p) / 4));
        chain(sl, p, t, null, JUDGE, reach, reach + 6);
        play(sl, p.position(), "chain_rattle", 0.9f, 0.9f);
        AstaFx.later(reach, () -> {
            if (!alive(p) || !tt.isAlive()) return;
            hit(p, tt, pow * 0.4f, "judgment_chain");
            int id = stake(sl, tt, 200);
            VOWS.put(tt.getUUID(), new long[]{p.getId(), sl.getGameTime() + 200, id, Float.floatToIntBits(pow * 2.6f)});
            sound(sl, tt.position(), SoundEvents.TRIDENT_HIT, 1.0f, 0.7f);
            AstaFx.later(200, () -> {   // the vow kept: the chain lets go, with a last sting
                if (VOWS.remove(tt.getUUID()) != null && tt.isAlive() && alive(p)) hit(p, tt, pow * 0.6f, "judgment_chain");
            });
        });
    }

    // ================================================================== 6. Stealing Chain
    public static void stealingChain(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        final float pow = use(p, STEAL, power);
        LivingEntity t = aimedFoe(p, 20);
        if (t == null) { chain(sl, p, null, Kit.aim(p, 14), STEAL, 4, 10); return; }
        final LivingEntity tt = t;
        int reach = Math.max(2, (int) Math.ceil(t.distanceTo(p) / 4));
        chain(sl, p, t, null, STEAL, reach, reach + 30);
        AstaFx.later(reach, () -> {
            if (!alive(p) || !tt.isAlive()) return;
            float before = tt.getHealth();
            hit(p, tt, pow * 0.9f, "stealing_chain");
            p.heal(Math.max(0f, before - tt.getHealth()) * 0.5f);
            TimedModifiers.apply(tt, Attributes.MOVEMENT_SPEED, STOLEN, "Its speed taken", -0.3, AttributeModifier.Operation.MULTIPLY_TOTAL, 120);
            TimedModifiers.apply(p, Attributes.MOVEMENT_SPEED, TAKEN, "Speed taken from a foe", 0.2, AttributeModifier.Operation.MULTIPLY_TOTAL, 120);
            Kit.push(tt, p.position().subtract(tt.position()), 0.5 * GravityFx.give(tt), 0.2);
            play(sl, tt.position(), "chain_rattle", 1.0f, 0.8f);
            sound(sl, tt.position(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0f, 0.7f);
        });
    }

    // ================================================================== 7. Chain Net
    public static void chainNet(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        final float pow = use(p, BIND, power);
        final Vec3 c = InkFx.target(p, 22);
        final float R = 4f;
        net(sl, c.add(0, 0.05, 0), R, 60);
        sound(sl, c, SoundEvents.CHAIN_FALL, 1.2f, 0.8f);
        AstaFx.later(6, () -> {
            if (!alive(p)) return;
            for (LivingEntity e : Kit.near(sl, p, c.add(0, 1, 0), R)) {
                hold(e, 50);
                hit(p, e, pow * 0.7f, "chain_net");
                if (!e.onGround()) { e.setDeltaMovement(0, -0.8, 0); e.hurtMarked = true; }
            }
            play(sl, c, "chain_rattle", 1.1f, 0.9f);
        });
    }

    // ================================================================== 8. Anchor Chain
    public static void anchorChain(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        final float pow = use(p, -1, power);
        LivingEntity t = aimed(p, 24, 10);
        if (t != null) {   // the foe is dragged to you
            final LivingEntity tt = t;
            int fly = Math.max(2, (int) Math.ceil(t.distanceTo(p) / 4));
            anchor(sl, p, mid(t), fly, fly + 10);
            AstaFx.later(fly, () -> {
                if (!alive(p) || !tt.isAlive()) return;
                hit(p, tt, pow * 0.6f, "anchor_chain");
                Vec3 to = p.position().subtract(tt.position());
                double g = GravityFx.give(tt);
                tt.setDeltaMovement(to.x * 0.25 * g, 0.35 + to.y * 0.05, to.z * 0.25 * g);
                tt.hurtMarked = true;
                play(sl, tt.position(), "chain_rattle", 1.0f, 0.7f);
            });
        } else {           // it bites the ground and drags you there
            Vec3 at = Kit.aim(p, 24);
            int fly = Math.max(2, (int) Math.ceil(at.distanceTo(p.getEyePosition()) / 4));
            anchor(sl, p, at, fly, fly + 12);
            AstaFx.later(fly, () -> {
                if (!alive(p)) return;
                Vec3 to = at.subtract(p.position());
                p.setDeltaMovement(to.x * 0.22, Math.max(0.4, to.y * 0.12 + 0.45), to.z * 0.22);
                p.hurtMarked = true;
                p.fallDistance = 0;
                sound(sl, at, SoundEvents.ANVIL_LAND, 0.5f, 1.4f);
                play(sl, p.position(), "chain_rattle", 1.0f, 1.0f);
            });
        }
        sound(sl, p.position(), SoundEvents.CHAIN_PLACE, 1.0f, 0.9f);
    }

    // ================================================================== 9. Restriction (mode)
    public static void restriction(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        final int T = 400;
        aura(sl, p, T);
        Moves.mode(p, MODE, T, 40, pl -> { if (pl.getHealth() > 4f && !pl.isCreative()) pl.setHealth(pl.getHealth() - 1f); });
        Moves.stance(p, 2, 1, 0.1, 0.3, 0, T);
        FxSend.impactFrame(sl, p.position().add(0, 1, 0), 2, FxSend.FRAME_CHARMY, 16);
        play(sl, p.position(), "chain_rattle", 1.4f, 0.5f);
        sound(sl, p.position(), SoundEvents.ANVIL_PLACE, 0.6f, 0.5f);
        p.displayClientMessage(Component.literal("Restriction - you bind yourself to a vow").withStyle(ChatFormatting.GRAY), true);
    }

    // ================================================================== 10. Thousand Chains
    public static void thousandChains(ServerPlayer p, float power) {
        boolean limit = LimitBreak.active(p);
        ServerLevel sl = p.serverLevel();
        final float pow = use(p, BIND, power) * (limit ? 1.3f : 1f);
        final Vec3 g = InkFx.target(p, 28);
        final float R = limit ? 12f : 9f;
        final int RISE = 10, HOLDT = 22;
        final List<LivingEntity> foes = Kit.near(sl, p, g.add(0, 1, 0), R);
        int n = 0;
        for (int i = 0; i < 26; i++) {   // chains out of the ground all round, every one reaching for something
            double an = i * TAU / 26 + Math.random() * 0.2, rr = R * (0.4 + 0.6 * Math.random());
            Vec3 base = InkFx.ground(sl, g.add(Math.cos(an) * rr, 0, Math.sin(an) * rr));
            Vec3 top = foes.isEmpty() ? base.add((Math.random() - 0.5) * 2, 3 + Math.random() * 3, (Math.random() - 0.5) * 2) : mid(foes.get(n++ % foes.size()));
            erupt(sl, base, top, i % 5, RISE, RISE + HOLDT + 10);
        }
        play(sl, g, "chain_rattle", 1.4f, 0.6f);
        sound(sl, g, SoundEvents.CHAIN_BREAK, 1.2f, 0.5f);
        AstaFx.later(RISE, () -> {
            if (!alive(p)) return;
            for (LivingEntity e : foes) if (e.isAlive()) { hold(e, HOLDT + 6); wrap(sl, e, BIND, HOLDT + 6); hit(p, e, pow * 0.4f, "thousand_chains"); }
        });
        AstaFx.later(RISE + HOLDT, () -> {   // and they pull taut
            if (!alive(p)) return;
            for (LivingEntity e : foes) if (e.isAlive()) { verdict(sl, e); hit(p, e, pow * 1.7f, "thousand_chains"); }
            FxSend.impactFrame(sl, g.add(0, 1, 0), 3, FxSend.FRAME_CHARMY, 24);
            FxSend.shake(sl, g, 0.7f, 12, 30);
            play(sl, g, "chain_rattle", 1.6f, 0.4f);
            sound(sl, g, SoundEvents.ANVIL_LAND, 1.0f, 0.5f);
        });
    }

    /** Surpassing Limits: all five chains rise round you. */
    public static void awaken(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        Vec3 c = p.position();
        for (int i = 0; i < 5; i++) {
            double an = i * TAU / 5;
            erupt(sl, c.add(Math.cos(an) * 2.2, 0, Math.sin(an) * 2.2), c.add(Math.cos(an) * 0.6, 3.2, Math.sin(an) * 0.6), i, 10, 30);
        }
        play(sl, c, "chain_rattle", 1.2f, 0.8f);
    }
}
