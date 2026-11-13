package com.ironspellgrimoire.server;

import static com.ironspellgrimoire.server.FxUtil.*;

import com.ironspellgrimoire.network.FxPacket;
import com.ironspellgrimoire.registry.AddonItemRegistry;
import com.ironspellgrimoire.registry.AddonSounds;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.UUID;

/**
 * The Gravity Grimoire (an original grimoire): ten spells that bend weight itself. Dense black orbs, wells that drag
 * everything in, fields that press foes into the ground, and at the end a falling star of compressed mass.
 * Drawn by client/GravityFxClient (FxPacket shape 83): black cores with violet light bending round them.
 *
 *  1 Gravity Bullet      - three dense orbs, one after another; each pulls the foes round where it lands
 *  2 Weightless Step     - you leap and grow light: you glide for a few seconds, and the air you leave throws foes off
 *  3 Crushing Field      - a circle on the ground where gravity runs five times over: foes inside are pressed down,
 *                          slowed and hurt every half second
 *  4 Repulse             - everything near is thrown away from you, and missiles in the air turn back
 *  5 Gravity Well        - a well opens where you aim and drags foes into it for three seconds, then collapses
 *  6 Gravity Lift        - foes round your target float up helplessly, then are slammed into the ground
 *  7 Orbiting Mass       - four orbs circle you for 10 s, each flinging itself at the nearest foe in turn
 *  8 Heavy Descent       - rise, then fall like a meteor on where you aim: a crater of force where you land
 *  9 Mass Domain (mode)  - 20 s: the air round you grows heavy; foes near you are slowed and dragged, you can't be
 *                          moved, and your Gravity spells hit 30% harder
 * 10 Starfall Collapse   - a sphere of compressed mass falls from the sky on your target, pulling everything under
 *                          it, and implodes. Surpassing Limits: wider and heavier.
 *
 * Passive "Featherweight": no fall damage while it is equipped, and every Gravity hit makes its foe 20% slower for 2 s.
 */
@Mod.EventBusSubscriber(modid = "iron_spell_grimoire")
public final class GravityFx {
    private GravityFx() {}

    public static final String MODE = "mass_domain";
    static final int PAL = Fx.DARK;   // the generic hit marks: violet
    private static final UUID LIGHT = UUID.fromString("6b1f3c20-0d41-4a8e-9b11-7a2e5c900001");
    private static final UUID HEAVY = UUID.fromString("6b1f3c20-0d41-4a8e-9b11-7a2e5c900002");
    private static final UUID HEAVY_SLOW = UUID.fromString("6b1f3c20-0d41-4a8e-9b11-7a2e5c900003");
    private static final UUID MARK_SLOW = UUID.fromString("6b1f3c20-0d41-4a8e-9b11-7a2e5c900004");
    private static final double TAU = Math.PI * 2;

    // ================================================================== the client shapes (GravityFxClient, shape 83)
    private static FxPacket pk(int kind, Vec3 c, int life) {
        FxPacket p = new FxPacket();
        p.shape = 83; p.id = FxSend.nextId(); p.h = kind; p.c = c; p.life = life;
        return p;
    }

    /** 0 an orb flying c -> u over {@code fly} ticks (0 = it only hangs at c), size r. */
    static int orb(ServerLevel sl, Vec3 c, Vec3 u, float r, int fly, int life) {
        FxPacket p = pk(0, c, life);
        p.u = u; p.r = r; p.speed = fly;
        FxSend.sendNear(sl, c, p);
        return p.id;
    }

    /** 1 a well: r = reach, life; it collapses at the end. */
    static int well(ServerLevel sl, Vec3 c, float r, int life) {
        FxPacket p = pk(1, c, life);
        p.r = r;
        FxSend.sendNear(sl, c, p);
        return p.id;
    }

    /** 2 a crushing field on the ground. */
    static void field(ServerLevel sl, Vec3 c, float r, int life) {
        FxPacket p = pk(2, c, life);
        p.r = r;
        FxSend.sendNear(sl, c, p);
    }

    /** 3 a wave of force rolling out from c to radius r (a0 = 1: it rolls in). */
    static void wave(ServerLevel sl, Vec3 c, float r, int life, boolean in) {
        FxPacket p = pk(3, c, life);
        p.r = r; p.a0 = in ? 1 : 0;
        FxSend.sendNear(sl, c, p);
    }

    /** 4 a column of lift: everything in it streams upward. */
    static void column(ServerLevel sl, Vec3 c, float r, float h, int life) {
        FxPacket p = pk(4, c, life);
        p.r = r; p.r2 = h;
        FxSend.sendNear(sl, c, p);
    }

    /** 5 an orb circling an entity: a1 = its place in the ring (0..1), a0 = how many share the ring. */
    static int orbiter(ServerLevel sl, LivingEntity owner, float phase, int count, int life) {
        FxPacket p = pk(5, owner.position(), life);
        p.v = new Vec3(owner.getId(), 1, 0); p.a1 = phase; p.a0 = count;
        FxSend.sendNear(sl, owner.position(), p);
        return p.id;
    }

    /** 6 the Mass Domain round a player. */
    static void domain(ServerLevel sl, ServerPlayer pl, float r, int life) {
        FxPacket p = pk(6, pl.position(), life);
        p.v = new Vec3(pl.getId(), 1, 0); p.r = r;
        FxSend.sendNear(sl, pl.position(), p);
    }

    /** 7 the falling star: from c (high) down to u over {@code fall} ticks, size r. */
    static void star(ServerLevel sl, Vec3 c, Vec3 u, float r, int fall, int life) {
        FxPacket p = pk(7, c, life);
        p.u = u; p.r = r; p.speed = fall;
        FxSend.sendNear(sl, u, p);
    }

    /** 8 a slam on the ground: rings and a crater of light. */
    static void slam(ServerLevel sl, Vec3 c, float r, int life) {
        FxPacket p = pk(8, c, life);
        p.r = r;
        FxSend.sendNear(sl, c, p);
    }

    /** 9 the wake of something weightless: follows the entity. */
    static void wake(ServerLevel sl, LivingEntity e, int life) {
        FxPacket p = pk(9, e.position(), life);
        p.v = new Vec3(e.getId(), 1, 0);
        FxSend.sendNear(sl, e.position(), p);
    }

    static void kill(ServerLevel sl, int id, Vec3 at) { FxSend.kill(sl, id, at); }

    // ================================================================== helpers
    static void play(ServerLevel sl, Vec3 at, String kind, float vol, float pitch) {
        SoundEvent s = AddonSounds.get(kind);
        if (s != null) sl.playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, vol, pitch);
    }

    static boolean domain(ServerPlayer p) { return Moves.inMode(p, MODE); }

    static float pw(ServerPlayer p, float power) { return domain(p) ? power * 1.3f : power; }

    /** A Gravity hit: the damage, and the foe grows heavy (Featherweight's other half). */
    static void hit(ServerPlayer p, LivingEntity t, float amt, String id) {
        AstaFx.damage(p, t, amt, id);
        TimedModifiers.apply(t, Attributes.MOVEMENT_SPEED, MARK_SLOW, "Heavy", -0.2, AttributeModifier.Operation.MULTIPLY_TOTAL, 40);
    }

    /** How much a pull or a throw moves this foe (bosses and the sure-footed barely move). */
    static double give(LivingEntity t) {
        double kb = t.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
        return (IceFx.boss(t) ? 0.25 : 1.0) * Math.max(0.1, 1 - kb);
    }

    /** Drag {@code t} toward {@code c} by up to {@code k} per tick. */
    static void pull(LivingEntity t, Vec3 c, double k) {
        Vec3 d = c.subtract(mid(t));
        double l = d.length();
        if (l < 0.6) { t.setDeltaMovement(t.getDeltaMovement().scale(0.6)); t.hurtMarked = true; return; }
        Vec3 v = d.scale(Math.min(k, l * 0.3) * give(t) / l);
        t.setDeltaMovement(t.getDeltaMovement().scale(0.7).add(v.x, Math.max(-0.2, v.y * 0.5) + 0.02, v.z));
        t.hurtMarked = true;
    }

    static void heavy(LivingEntity t, int ticks) {
        TimedModifiers.apply(t, ForgeMod.ENTITY_GRAVITY.get(), HEAVY, "Crushing gravity", 2.0, AttributeModifier.Operation.MULTIPLY_TOTAL, ticks);
        TimedModifiers.apply(t, Attributes.MOVEMENT_SPEED, HEAVY_SLOW, "Crushing gravity", -0.45, AttributeModifier.Operation.MULTIPLY_TOTAL, ticks);
    }

    private static Vec3 ground(ServerLevel sl, Vec3 near) {
        Vec3 g = FxLib.ground(sl, near.add(0, 2, 0), 10);
        return g == null ? near : g;
    }

    /** Where a spell lands: the foe you aim at, or the ground you look at. */
    private static Vec3 target(ServerPlayer p, double range) {
        LivingEntity t = aimed(p, range, 12);
        return t != null ? t.position() : ground(p.serverLevel(), Kit.aim(p, range));
    }

    public static boolean equipped(ServerPlayer p) {
        ItemStack b = Utils.getPlayerSpellbookStack(p);
        return b != null && b.is(AddonItemRegistry.GRAVITY_GRIMOIRE.get());
    }

    /** Featherweight: no fall damage while the Gravity Grimoire is equipped. */
    @SubscribeEvent
    public static void onFall(LivingFallEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && equipped(p)) e.setDistance(0);
    }

    // ================================================================== 1. Gravity Bullet
    public static void gravityBullet(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        final float pow = pw(p, power);
        for (int i = 0; i < 3; i++) {
            final int k = i;
            AstaFx.later(1 + i * 4, () -> {
                if (!alive(p)) return;
                LivingEntity t = aimed(p, 30, 10);
                Vec3 from = Kit.hand(p);
                Vec3 to = t != null ? mid(t) : clip(sl, p, p.getEyePosition(), p.getEyePosition().add(p.getLookAngle().scale(30)));
                int fly = Math.max(2, (int) Math.ceil(from.distanceTo(to) / 2.4));
                int id = orb(sl, from, to, 0.32f, fly, fly + 4);
                play(sl, from, "gravity_hum", 0.5f, 1.8f + k * 0.1f);
                sound(sl, from, SoundEvents.ENDER_EYE_LAUNCH, 0.7f, 0.6f);
                AstaFx.later(fly, () -> {
                    kill(sl, id, to);
                    wave(sl, to, 3.2f, 9, true);
                    for (LivingEntity e : Kit.near(sl, p, to, 3.2)) {
                        pull(e, to, 0.55);
                        if (mid(e).distanceTo(to) < 1.8 + e.getBbWidth() * 0.5) hit(p, e, pow * 0.45f, "gravity_bullet");
                    }
                    play(sl, to, "gravity_crush", 0.5f, 1.6f);
                });
            });
        }
    }

    // ================================================================== 2. Weightless Step
    public static void weightlessStep(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        final float pow = pw(p, power);
        Vec3 c = p.position();
        Vec3 look = flat(p.getLookAngle());
        if (look.lengthSqr() < 1.0E-4) look = new Vec3(0, 0, 1);
        look = look.normalize();
        for (LivingEntity e : Kit.near(sl, p, c.add(0, 1, 0), 3.5)) {   // the air you leave throws foes off
            hit(p, e, pow * 0.4f, "weightless_step");
            Kit.push(e, mid(e).subtract(c), 1.0 * give(e), 0.35);
        }
        TimedModifiers.apply(p, ForgeMod.ENTITY_GRAVITY.get(), LIGHT, "Weightless", -0.75, AttributeModifier.Operation.MULTIPLY_TOTAL, 60);
        p.setDeltaMovement(look.x * 1.35, 0.85, look.z * 1.35);
        p.hurtMarked = true;
        p.fallDistance = 0;
        slam(sl, c, 2.4f, 12);
        wave(sl, c.add(0, 0.2, 0), 3.5f, 10, false);
        wake(sl, p, 50);
        play(sl, c, "gravity_hum", 0.6f, 1.5f);
        sound(sl, c, SoundEvents.PHANTOM_FLAP, 0.8f, 0.7f);
    }

    // ================================================================== 3. Crushing Field
    public static void crushingField(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        final float pow = pw(p, power);
        final Vec3 c = target(p, 24);
        final float R = domain(p) ? 5.5f : 4.5f;
        field(sl, c, R, 64);
        play(sl, c, "gravity_crush", 0.9f, 0.7f);
        for (int k = 0; k < 6; k++) {
            final int kk = k;
            AstaFx.later(4 + k * 10, () -> {
                if (!alive(p)) return;
                for (LivingEntity e : Kit.near(sl, p, c.add(0, 0.8, 0), R)) {
                    if (Math.abs(e.getY() - c.y) > 4) continue;
                    hit(p, e, pow * 0.22f, "crushing_field");
                    heavy(e, 16);
                    if (!e.onGround()) { e.setDeltaMovement(e.getDeltaMovement().x * 0.5, -1.2 * give(e), e.getDeltaMovement().z * 0.5); e.hurtMarked = true; }
                }
                if (kk % 2 == 0) play(sl, c, "gravity_hum", 0.5f, 0.8f);
            });
        }
    }

    // ================================================================== 4. Repulse
    public static void repulse(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        final float pow = pw(p, power);
        Vec3 c = p.position().add(0, 1, 0);
        final float R = 6f;
        wave(sl, c, R, 12, false);
        slam(sl, p.position(), 2.5f, 10);
        for (LivingEntity e : Kit.near(sl, p, c, R)) {
            hit(p, e, pow * 0.7f, "repulse");
            Kit.push(e, mid(e).subtract(c), 1.7 * give(e), 0.45 * give(e));
        }
        for (Projectile pr : sl.getEntitiesOfClass(Projectile.class, new AABB(c, c).inflate(R), x -> x.getOwner() != p)) {
            Vec3 v = pr.getDeltaMovement();
            Vec3 out = pr.position().subtract(c);
            out = out.lengthSqr() < 1.0E-4 ? v.scale(-1) : out.normalize().scale(Math.max(0.6, v.length()));
            pr.setDeltaMovement(out);
            pr.setOwner(p);
            pr.hurtMarked = true;
        }
        play(sl, c, "gravity_crush", 0.8f, 1.3f);
        sound(sl, c, SoundEvents.WARDEN_SONIC_CHARGE, 0.25f, 1.8f);
    }

    // ================================================================== 5. Gravity Well
    public static void gravityWell(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        final float pow = pw(p, power);
        Vec3 g = target(p, 26);
        final Vec3 c = g.add(0, 1.4, 0);
        final float R = domain(p) ? 9f : 7f;
        final int T = 60;
        int id = well(sl, c, R, T + 6);
        play(sl, c, "gravity_hum", 1.0f, 0.7f);
        for (int k = 0; k < T; k += 2) {
            final int kk = k;
            AstaFx.later(k + 1, () -> {
                if (!alive(p)) return;
                for (LivingEntity e : Kit.near(sl, p, c, R)) pull(e, c, 0.32);
                if (kk % 20 == 0) {
                    for (LivingEntity e : Kit.near(sl, p, c, 2.5)) hit(p, e, pow * 0.15f, "gravity_well");
                    play(sl, c, "gravity_hum", 0.6f, 0.6f + kk * 0.006f);
                }
            });
        }
        AstaFx.later(T, () -> {   // it collapses
            kill(sl, id, c);
            wave(sl, c, 4.5f, 10, false);
            for (LivingEntity e : Kit.near(sl, p, c, 4)) {
                hit(p, e, pow * 1.1f, "gravity_well");
                Kit.push(e, mid(e).subtract(c), 0.5 * give(e), 0.3);
            }
            FxSend.shake(sl, c, 0.35f, 6, 16);
            play(sl, c, "gravity_crush", 1.0f, 0.9f);
        });
    }

    // ================================================================== 6. Gravity Lift
    public static void gravityLift(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        final float pow = pw(p, power);
        final Vec3 c = target(p, 24);
        final float R = 5f;
        final List<LivingEntity> foes = Kit.near(sl, p, c.add(0, 1, 0), R);
        column(sl, c, R, 6f, 30);
        play(sl, c, "gravity_hum", 0.8f, 1.4f);
        for (LivingEntity e : foes) {
            hit(p, e, pow * 0.3f, "gravity_lift");
            TimedModifiers.apply(e, ForgeMod.ENTITY_GRAVITY.get(), LIGHT, "Weightless", -0.9, AttributeModifier.Operation.MULTIPLY_TOTAL, 20);
            e.setDeltaMovement(e.getDeltaMovement().x * 0.2, 1.0 * Math.max(0.4, give(e)), e.getDeltaMovement().z * 0.2);
            e.hurtMarked = true;
        }
        AstaFx.later(20, () -> {   // and down
            for (LivingEntity e : foes) {
                if (!e.isAlive()) continue;
                TimedModifiers.remove(e, ForgeMod.ENTITY_GRAVITY.get(), LIGHT);
                heavy(e, 12);
                e.setDeltaMovement(0, -2.4, 0);
                e.hurtMarked = true;
            }
            play(sl, c, "gravity_crush", 0.7f, 1.2f);
        });
        AstaFx.later(26, () -> {
            if (!alive(p)) return;
            for (LivingEntity e : foes) {
                if (!e.isAlive()) continue;
                hit(p, e, pow * 0.9f, "gravity_lift");
                slam(sl, ground(sl, e.position()), 1.8f, 12);
            }
            FxSend.shake(sl, c, 0.4f, 6, 18);
            play(sl, c, "gravity_crush", 1.0f, 0.8f);
        });
    }

    // ================================================================== 7. Orbiting Mass
    public static void orbitingMass(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        final int N = domain(p) ? 6 : 4, T = 200;
        final int[] ids = new int[N];
        final boolean[] gone = new boolean[N];
        for (int i = 0; i < N; i++) ids[i] = orbiter(sl, p, i / (float) N, N, T);
        play(sl, p.position(), "gravity_hum", 0.7f, 1.2f);
        for (int s = 0; s < N; s++) {
            final int k = s;
            for (int w = 15 + s * 15; w < T; w += 15) {   // each orb waits its turn, then looks for a foe every 3/4 s
                final int when = w;
                AstaFx.later(w, () -> {
                    if (gone[k] || !alive(p)) return;
                    for (int j = 0; j < k; j++) if (!gone[j]) return;   // in order: the first still circling goes first
                    LivingEntity t = null;
                    double best = 12;
                    for (LivingEntity e : Kit.near(sl, p, p.position().add(0, 1, 0), 12)) {
                        double d = e.distanceTo(p);
                        if (d < best) { best = d; t = e; }
                    }
                    if (t == null) return;
                    gone[k] = true;
                    kill(sl, ids[k], p.position());
                    double an = TAU * (k / (double) N) + sl.getGameTime() * 0.12;
                    Vec3 from = p.position().add(Math.cos(an) * 1.3, 1.3, Math.sin(an) * 1.3);
                    Vec3 to = mid(t);
                    int fly = Math.max(2, (int) Math.ceil(from.distanceTo(to) / 2.2));
                    orb(sl, from, to, 0.3f, fly, fly + 2);
                    final LivingEntity tt = t;
                    final float pow = pw(p, power);
                    AstaFx.later(fly, () -> {
                        if (!alive(p)) return;
                        wave(sl, to, 2.4f, 8, true);
                        if (tt.isAlive()) hit(p, tt, pow * 0.55f, "orbiting_mass");
                        for (LivingEntity e : Kit.near(sl, p, to, 2.2)) if (e != tt) hit(p, e, pow * 0.25f, "orbiting_mass");
                        play(sl, to, "gravity_crush", 0.5f, 1.7f);
                    });
                });
            }
        }
    }

    // ================================================================== 8. Heavy Descent
    public static void heavyDescent(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        final float pow = pw(p, power);
        final Vec3 land = target(p, 18);
        Vec3 from = p.position();
        p.setDeltaMovement(land.subtract(from).multiply(0.06, 0, 0.06).add(0, 1.15, 0));
        p.hurtMarked = true;
        p.fallDistance = 0;
        wave(sl, from.add(0, 0.2, 0), 2.6f, 8, false);
        wake(sl, p, 22);
        play(sl, from, "gravity_hum", 0.7f, 1.6f);
        AstaFx.later(9, () -> {   // the top of the rise: fall on the spot like a meteor
            if (!alive(p)) return;
            Vec3 d = land.subtract(p.position());
            double l = d.length();
            p.setDeltaMovement(l < 1.0E-3 ? new Vec3(0, -2.5, 0) : d.scale(Math.min(2.8, l * 0.45) / l).add(0, -0.6, 0));
            p.hurtMarked = true;
            p.fallDistance = 0;
            TimedModifiers.apply(p, ForgeMod.ENTITY_GRAVITY.get(), HEAVY, "Heavy Descent", 2.0, AttributeModifier.Operation.MULTIPLY_TOTAL, 14);
        });
        final boolean[] done = {false};
        for (int k = 10; k < 30; k++) {
            AstaFx.later(k, () -> {
                if (done[0] || !alive(p)) return;
                if (!p.onGround() && p.position().distanceTo(land) > 1.6) return;
                done[0] = true;
                p.fallDistance = 0;
                Vec3 at = p.position();
                slam(sl, at, 5f, 16);
                wave(sl, at.add(0, 0.3, 0), 5.5f, 12, false);
                for (LivingEntity e : Kit.near(sl, p, at.add(0, 0.8, 0), 5)) {
                    hit(p, e, pow * 1.1f, "heavy_descent");
                    Kit.push(e, mid(e).subtract(at), 0.9 * give(e), 0.5 * give(e));
                }
                FxSend.shake(sl, at, 0.55f, 8, 20);
                play(sl, at, "gravity_crush", 1.2f, 0.75f);
            });
        }
    }

    // ================================================================== 9. Mass Domain (mode)
    public static void massDomain(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        final int T = 400;
        domain(sl, p, 7f, T);
        wave(sl, p.position().add(0, 1, 0), 7f, 14, true);
        FxSend.impactFrame(sl, p.position().add(0, 1, 0), 2, FxSend.FRAME_YAMI, 16);
        play(sl, p.position(), "gravity_hum", 1.2f, 0.5f);
        play(sl, p.position(), "gravity_crush", 0.8f, 0.6f);
        Moves.stance(p, 4, 2, 0, 1.0, 0, T);
        Moves.mode(p, MODE, T, 5, pl -> {
            for (LivingEntity e : Kit.near(pl.serverLevel(), pl, pl.position().add(0, 1, 0), 7)) {
                TimedModifiers.apply(e, Attributes.MOVEMENT_SPEED, HEAVY_SLOW, "Mass Domain", -0.35, AttributeModifier.Operation.MULTIPLY_TOTAL, 8);
                if (e.distanceTo(pl) > 2.5) pull(e, pl.position().add(0, 1, 0), 0.08);
            }
        });
    }

    // ================================================================== 10. Starfall Collapse
    public static void starfallCollapse(ServerPlayer p, float power) {
        boolean limit = LimitBreak.active(p);
        ServerLevel sl = p.serverLevel();
        final float pow = pw(p, power) * (limit ? 1.3f : 1f);
        final Vec3 g = target(p, 32);
        final float R = limit ? 10f : 7f, S = limit ? 3.6f : 2.6f;
        final int FALL = 30;
        star(sl, g.add(0, 22, 0), g.add(0, S * 0.6, 0), S, FALL, FALL + 14);
        field(sl, g, R, FALL + 4);
        play(sl, g, "gravity_hum", 1.4f, 0.45f);
        sound(sl, g, SoundEvents.BEACON_ACTIVATE, 1.0f, 0.5f);
        for (int k = 2; k < FALL; k += 2) {   // everything under it is dragged into the middle
            final int kk = k;
            AstaFx.later(k, () -> {
                if (!alive(p)) return;
                for (LivingEntity e : Kit.near(sl, p, g.add(0, 1, 0), R + 2)) pull(e, g.add(0, 0.5, 0), 0.2 + kk * 0.006);
            });
        }
        AstaFx.later(FALL, () -> {
            wave(sl, g.add(0, 1, 0), R, 12, true);
            for (LivingEntity e : Kit.near(sl, p, g.add(0, 1, 0), R)) heavy(e, 20);
            FxSend.shake(sl, g, 0.5f, 6, 30);
            play(sl, g, "gravity_crush", 1.4f, 0.6f);
        });
        AstaFx.later(FALL + 6, () -> {   // the implosion
            if (!alive(p)) return;
            slam(sl, g, R, 18);
            wave(sl, g.add(0, 1, 0), R + 2, 14, false);
            for (LivingEntity e : Kit.near(sl, p, g.add(0, 1, 0), R)) {
                hit(p, e, pow * 2.0f, "starfall_collapse");
                Kit.push(e, mid(e).subtract(g), 0.6 * give(e), 0.4 * give(e));
            }
            FxSend.impactFrame(sl, g.add(0, 1, 0), 3, FxSend.FRAME_YAMI, 24);
            FxSend.shake(sl, g, 0.9f, 14, 36);
            play(sl, g, "gravity_crush", 1.6f, 0.45f);
            sound(sl, g, SoundEvents.GENERIC_EXPLODE, 1.0f, 0.5f);
        });
    }

    /** Surpassing Limits: the ground round you sinks under your weight, and four orbs of mass rise round you. */
    public static void awaken(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        Vec3 c = p.position();
        field(sl, c, 4.5f, 40);
        wave(sl, c.add(0, 1, 0), 6f, 16, true);
        for (int i = 0; i < 4; i++) {
            double an = i * TAU / 4;
            Vec3 at = c.add(Math.cos(an) * 2.4, 0.3, Math.sin(an) * 2.4);
            orb(sl, at, at.add(0, 3.5, 0), 0.45f, 18, 30);
        }
        play(sl, c, "gravity_hum", 1.2f, 0.6f);
    }
}
