package com.ironspellgrimoire.server;

import static com.ironspellgrimoire.server.FxUtil.*;

import com.ironspellgrimoire.network.FxPacket;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Patolli's Light Magic - the ten spells of the Light Grimoire. Light is the fastest magic there is: swords of pure
 * light that streak to their mark before you see them leave, arrows that rise and fall on whoever you are fighting,
 * a whip sixteen blocks long, a pillar of judgment, and - in the sky - rows of swords waiting for the word.
 * Everything is drawn by client/LightFxClient: a white-hot core in a gold halo, star glints on every tip.
 *
 *  1 Light Sword                 - three swords of light form at your side and streak into your target
 *  2 Light Speed Step            - a flash-step twelve blocks; aimed at a foe, you come out behind it and strike
 *  3 Arrows of Judgment          - twelve arrows of light rise over you, hang, and fall on the foes around you
 *  4 Bright Judgment Whip        - a whip of light sixteen blocks long lashes across, then back
 *  5 Healing Ray of Light        - heal the ally you aim at (or yourself), and a little more over two seconds
 *  6 Blinding Flash              - light bursts out of you: foes near are slowed, weakened and lose track of you;
 *                                  players are blinded white for a moment
 *  7 Light Swords of Judgment    - rows of swords open in the sky over the target, shimmer, and rain down in a sweep
 *  8 Light Shaft of Punishment   - a circle draws itself on the ground, then a vast pillar of light comes down on it
 *  9 Demon Light (mode)          - 20 s: your light turns black and white; your Light spells hit harder, every
 *                                  cooldown runs faster and you move faster
 * 10 Imperial Swords of Conviction - a storm of swords circles the target and closes in, then one giant sword falls.
 *                                  Surpassing Limits: Sovereign of Light.
 *
 * Passive "Radiance": every Light spell leaves you moving at light speed for a moment (+30% speed for 2 s).
 */
public final class LightFx {
    private LightFx() {}

    public static final String MODE = "demon_light";
    private static final int GOLD = Fx.GOLD, DARK = Fx.YAMI;
    private static final UUID DIM = UUID.fromString("5a7c2e10-9b44-4c1e-8f20-6c1d3e9a0011");
    private static final double TAU = Math.PI * 2;

    // ================================================================== the client shapes (LightFxClient, shape 66)
    private static FxPacket pk(int kind, int pal, Vec3 c, int life) {
        FxPacket p = new FxPacket();
        p.shape = 66; p.id = FxSend.nextId(); p.h = kind; p.palette = (byte) pal; p.c = c; p.life = life;
        return p;
    }

    /** A sword of light: hangs at {@code c} pointing {@code d} for {@code hang} ticks, then flies to {@code to} in {@code fly}. */
    static int sword(ServerLevel sl, int pal, Vec3 c, Vec3 to, Vec3 d, float len, float width, int hang, int fly, int flags, int life) {
        FxPacket p = pk(0, pal, c, life);
        p.u = to; p.d = d; p.r = len; p.r2 = width; p.sweep = hang; p.speed = fly; p.a0 = flags; p.a1 = (float) (Math.random() * 10);
        FxSend.sendNear(sl, c, p);
        return p.id;
    }

    static void glint(ServerLevel sl, int pal, Vec3 c, float size, int life) {
        FxPacket p = pk(1, pal, c, life);
        p.r = size;
        FxSend.sendNear(sl, c, p);
    }

    static int pillar(ServerLevel sl, int pal, Vec3 c, float r, float h, int warn, int life) {
        FxPacket p = pk(2, pal, c, life);
        p.r = r; p.r2 = h; p.sweep = warn;
        FxSend.sendNear(sl, c, p);
        return p.id;
    }

    static int circle(ServerLevel sl, int pal, Vec3 c, Vec3 face, float r, float spin, int life, Entity follow, double followY) {
        FxPacket p = pk(3, pal, c, life);
        p.d = face; p.r = r; p.speed = spin;
        if (follow != null) p.v = new Vec3(follow.getId(), 1, followY);
        FxSend.sendNear(sl, c, p);
        return p.id;
    }

    static void whip(ServerLevel sl, int pal, Entity from, Vec3 d0, Vec3 d1, float len, int sweep, int life) {
        FxPacket p = pk(4, pal, from.position(), life);
        p.d = d0; p.u = d1; p.r = len; p.speed = sweep; p.v = new Vec3(from.getId(), 1, 0);
        FxSend.sendNear(sl, from.position(), p);
    }

    static void ray(ServerLevel sl, int pal, Vec3 a, Vec3 b, float w, int life) {
        FxPacket p = pk(5, pal, a, life);
        p.u = b; p.r = w;
        FxSend.sendNear(sl, a, p);
    }

    static void burst(ServerLevel sl, int pal, Vec3 c, float r, int life) {
        FxPacket p = pk(6, pal, c, life);
        p.r = r;
        FxSend.sendNear(sl, c, p);
    }

    private static void blind(ServerPlayer pl, int ticks) {
        if (pl.connection == null) return;
        FxPacket p = pk(7, GOLD, pl.position(), ticks);
        com.ironspellgrimoire.network.AddonNetwork.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> pl), p);
    }

    // ================================================================== small helpers
    private static boolean demon(ServerPlayer p) { return Moves.inMode(p, MODE); }
    private static int pal(ServerPlayer p) { return demon(p) ? DARK : GOLD; }
    private static float pw(ServerPlayer p, float power) { return demon(p) ? power * 1.35f : power; }
    private static void hit(ServerPlayer p, LivingEntity t, float amt, String id) { AstaFx.damage(p, t, amt, id); }

    /** Radiance: every Light spell leaves you moving at light speed for a moment. */
    private static void radiance(ServerPlayer p) { Moves.stance(p, 0, 0, 0.3, 0, 0, 40); }

    private static Vec3 ground(ServerLevel sl, Vec3 near) {
        Vec3 g = FxLib.ground(sl, near.add(0, 2, 0), 10);
        return g == null ? near : g;
    }

    /** Where a spell lands: the foe you are aiming at, or the spot you are looking at. */
    private static Vec3 target(ServerPlayer p, double range) {
        LivingEntity t = aimed(p, range, 12);
        return t != null ? t.position() : ground(p.serverLevel(), Kit.aim(p, range));
    }

    private static void hasten(ServerPlayer p, int ticks, boolean sync) {
        try {
            var cd = MagicData.getPlayerMagicData(p).getPlayerCooldowns();
            if (ticks > 0) cd.tick(ticks);
            if (sync) cd.syncToPlayer(p);
        } catch (Throwable ignored) {}
    }

    private static boolean ally(ServerPlayer p, LivingEntity e) {
        if (e == p) return false;
        if (e instanceof Player) return true;
        if (e instanceof TamableAnimal ta && p.getUUID().equals(ta.getOwnerUUID())) return true;
        return e.isAlliedTo(p);
    }

    // ================================================================== 1. Light Sword
    public static void lightSword(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        radiance(p);
        final int pal = pal(p);
        final float pow = pw(p, power);
        final LivingEntity t = aimed(p, 32, 10);
        Vec3 look = p.getLookAngle(), rt = rightOf(look);
        final Vec3 aim = t != null ? mid(t) : Kit.aim(p, 32);
        for (int i = 0; i < 3; i++) {
            final int k = i;
            Vec3 at = p.getEyePosition().add(look.scale(0.6)).add(rt.scale((i - 1) * 0.9)).add(0, 0.35 + (i == 1 ? 0.35 : 0), 0);
            Vec3 to = t != null ? aim.add(rt.scale((i - 1) * 0.25)) : aim;
            int fly = Math.max(2, (int) Math.ceil(at.distanceTo(to) / 3.5));
            int hang = 6 + i * 2;
            int id = sword(sl, pal, at, to, look, 1.6f, 1f, hang, fly, 0, hang + fly + 6);
            AstaFx.later(hang + fly, () -> {
                if (!alive(p)) return;
                FxSend.kill(sl, id, to);
                for (LivingEntity e : Kit.along(sl, p, at, to, 0.8)) hit(p, e, pow * 0.5f, "light_sword");
                glint(sl, pal, to, 0.9f, 6);
                if (k == 2) sound(sl, to, SoundEvents.AMETHYST_BLOCK_BREAK, 1.0f, 1.5f);
            });
        }
        sound(sl, p.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 1.4f);
        AstaFx.later(6, () -> sound(sl, p.position(), SoundEvents.TRIDENT_THROW, 0.8f, 1.7f));
    }

    // ================================================================== 2. Light Speed Step
    public static void lightSpeedStep(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        radiance(p);
        final int pal = pal(p);
        Vec3 from = p.position();
        LivingEntity t = aimed(p, 16, 12);
        Vec3 look = flat(p.getLookAngle());
        if (look.lengthSqr() < 1.0E-4) look = new Vec3(0, 0, 1);
        Vec3 dest;
        float yaw = p.getYRot();
        if (t != null) {   // out behind the foe, facing it
            Vec3 tl = flat(t.position().subtract(from));
            if (tl.lengthSqr() < 1.0E-4) tl = look;
            dest = t.position().add(tl.normalize().scale(1.4 + t.getBbWidth() * 0.5));
            yaw = (float) Math.toDegrees(Math.atan2(tl.x, -tl.z)) + 180f;
        } else {
            dest = clip(sl, p, p.getEyePosition(), p.getEyePosition().add(look.scale(12))).subtract(look.scale(0.6)).subtract(0, p.getEyeHeight(), 0);
        }
        Vec3 safe = SpaceFx.safe(sl, dest, p);
        if (safe == null) safe = from;
        burst(sl, pal, from.add(0, 1, 0), 1.6f, 7);
        ray(sl, pal, from.add(0, 1, 0), safe.add(0, 1, 0), 0.35f, 6);
        SpaceFx.move(p, safe, yaw);
        burst(sl, pal, safe.add(0, 1, 0), 1.8f, 8);
        glint(sl, pal, safe.add(0, 1.2, 0), 1.2f, 8);
        sound(sl, from, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0f, 1.8f);
        sound(sl, safe, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 1.9f);
        if (t != null) {
            final LivingEntity tt = t;
            final float pow = pw(p, power);
            AstaFx.later(2, () -> {
                if (!alive(p) || !tt.isAlive()) return;
                hit(p, tt, pow, "light_speed_step");
                FxSend.slash(sl, pal, mid(tt), mid(tt).subtract(p.getEyePosition()).normalize(), new Vec3(0, 1, 0), 1.8f, 0.4f, 6, 1.4f);
                sound(sl, mid(tt), SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, 1.5f);
            });
        }
    }

    // ================================================================== 3. Arrows of Judgment
    public static void arrowsOfJudgment(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        radiance(p);
        final int pal = pal(p);
        final float pow = pw(p, power);
        final int N = 12;
        Vec3 hand = Kit.hand(p);
        Vec3 c = p.position();
        double yaw0 = Math.toRadians(p.getYRot());
        List<LivingEntity> foes = Kit.near(sl, p, c.add(0, 1, 0), 24);
        foes.sort((a, b) -> Double.compare(a.distanceToSqr(p), b.distanceToSqr(p)));
        Vec3 fallback = ground(sl, Kit.aim(p, 24));
        sound(sl, c, SoundEvents.ILLUSIONER_CAST_SPELL, 0.8f, 1.6f);
        for (int i = 0; i < N; i++) {
            final int k = i;
            double an = yaw0 + Math.PI + (i - (N - 1) / 2.0) * 0.26;
            Vec3 rise = c.add(-Math.sin(an) * 2.6, 4.2 + (i % 2) * 0.6, Math.cos(an) * 2.6);
            sword(sl, pal, hand, rise, new Vec3(0, 1, 0), 1.0f, 0.8f, 0, 7, 1, 8);
            final LivingEntity t = foes.isEmpty() ? null : foes.get(i % foes.size());
            AstaFx.later(7, () -> {
                if (!alive(p)) return;
                Vec3 to = t != null && t.isAlive() ? mid(t) : fallback.add((k % 4 - 1.5) * 1.2, 0.2, (k / 4 - 1) * 1.2);
                int hang = 3 + k;
                int fly = Math.max(2, (int) Math.ceil(rise.distanceTo(to) / 3.0));
                int id = sword(sl, pal, rise, to, new Vec3(0, -1, 0), 1.0f, 0.8f, hang, fly, 0, hang + fly + 5);
                AstaFx.later(hang + fly, () -> {
                    if (!alive(p)) return;
                    FxSend.kill(sl, id, to);
                    boolean struck = false;
                    if (t != null && t.isAlive() && mid(t).distanceTo(to) < 3.5) { hit(p, t, pow * 0.45f, "arrows_of_judgment"); struck = true; }
                    if (!struck) for (LivingEntity e : Kit.near(sl, p, to, 1.4)) hit(p, e, pow * 0.45f, "arrows_of_judgment");
                    sound(sl, to, SoundEvents.AMETHYST_CLUSTER_HIT, 0.8f, 1.6f + k * 0.03f);
                });
            });
        }
        AstaFx.later(7, () -> sound(sl, c, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 1.2f));
    }

    // ================================================================== 4. Bright Judgment Whip
    public static void brightJudgmentWhip(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        radiance(p);
        final int pal = pal(p);
        final float pow = pw(p, power);
        final float L = 16f;
        Vec3 look = p.getLookAngle();
        Vec3 right = rotY(look, -1.05), left = rotY(look, 1.05);
        whip(sl, pal, p, right, left, L, 6, 12);
        sound(sl, p.position(), SoundEvents.TRIDENT_RIPTIDE_1, 0.8f, 1.6f);
        AstaFx.later(3, () -> lash(p, pow, L, 0));
        AstaFx.later(11, () -> {
            if (!alive(p)) return;
            Vec3 lk = p.getLookAngle();
            whip(sl, pal, p, rotY(lk, 1.05), rotY(lk, -1.05), L, 6, 12);
            sound(sl, p.position(), SoundEvents.TRIDENT_RIPTIDE_2, 0.8f, 1.8f);
        });
        AstaFx.later(14, () -> lash(p, pow, L, 1));
    }

    private static void lash(ServerPlayer p, float pow, float L, int n) {
        if (!alive(p)) return;
        ServerLevel sl = p.serverLevel();
        Vec3 eye = p.getEyePosition(), look = flat(p.getLookAngle());
        for (LivingEntity e : Kit.near(sl, p, eye, L)) {
            Vec3 to = flat(mid(e).subtract(eye));
            if (to.lengthSqr() < 1.0E-4 || to.normalize().dot(look) < 0.45) continue;   // inside the arc it swept
            if (Math.abs(mid(e).y - eye.y) > 3.5) continue;
            hit(p, e, pow * 0.6f, "bright_judgment_whip");
            Kit.push(e, rightOf(look).scale(n == 0 ? -1 : 1), 0.4, 0.15);
            glint(sl, pal(p), mid(e), 0.8f, 6);
        }
        sound(sl, p.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, 1.4f);
    }

    // ================================================================== 5. Healing Ray of Light
    public static void healingRay(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        radiance(p);
        final int pal = pal(p);
        LivingEntity who = null;
        double best = Math.cos(Math.toRadians(14));
        Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(20), e -> e.isAlive() && ally(p, e))) {
            Vec3 to = mid(e).subtract(eye);
            if (to.lengthSqr() < 1.0E-4 || to.length() > 20) continue;
            double d = to.normalize().dot(look);
            if (d > best) { best = d; who = e; }
        }
        final LivingEntity t = who == null ? p : who;
        final float amt = Math.max(2f, power);
        if (t != p) ray(sl, pal, Kit.hand(p), mid(t), 0.25f, 14);
        circle(sl, pal, t.position(), Vec3.ZERO, 1.3f, 0.06f, 40, t, 0.05);
        t.heal(amt);
        burst(sl, pal, mid(t), 1.2f, 10);
        for (int i = 0; i < 4; i++) {
            final int k = i;
            AstaFx.later(10 + i * 10, () -> {
                if (!t.isAlive()) return;
                t.heal(amt * 0.15f);
                Random r = new Random(t.getId() * 7L + k);
                glint(sl, pal, t.position().add(r.nextDouble() - 0.5, 0.4 + r.nextDouble() * t.getBbHeight(), r.nextDouble() - 0.5), 0.45f, 10);
            });
        }
        sound(sl, t.position(), SoundEvents.BEACON_POWER_SELECT, 0.8f, 1.6f);
        sound(sl, t.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 1.0f);
    }

    // ================================================================== 6. Blinding Flash
    public static void blindingFlash(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        radiance(p);
        final int pal = pal(p);
        Vec3 c = p.position().add(0, 1.2, 0);
        burst(sl, pal, c, 5.5f, 10);
        glint(sl, pal, c, 3.0f, 10);
        FxSend.ring(sl, pal, p.position().add(0, 0.1, 0), 0.5f, 8f, 0.12f, 10);
        Fx.flash(sl, c, 2.0f);
        sound(sl, c, SoundEvents.BEACON_ACTIVATE, 1.0f, 2.0f);
        sound(sl, c, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0f, 1.6f);
        for (LivingEntity e : Kit.near(sl, p, c, 8)) {
            Moves.slow(e, 60, -0.55);
            TimedModifiers.apply(e, Attributes.ATTACK_DAMAGE, DIM, "Blinded by light", -0.4, AttributeModifier.Operation.MULTIPLY_TOTAL, 60);
            if (e instanceof Mob mob) { mob.setTarget(null); mob.getNavigation().stop(); }
            if (e instanceof ServerPlayer sp) blind(sp, 36);
            hit(p, e, pw(p, power) * 0.3f, "blinding_flash");
        }
    }

    // ================================================================== 7. Light Swords of Judgment
    public static void lightSwordsOfJudgment(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        radiance(p);
        final int pal = pal(p);
        final float pow = pw(p, power);
        final Vec3 c = target(p, 30);
        Vec3 f = flat(c.subtract(p.position()));
        final Vec3 fwd = f.lengthSqr() < 1.0E-4 ? flat(p.getLookAngle()).normalize() : f.normalize();
        final Vec3 side = rightOf(fwd);
        final int ROWS = 4, COLS = 11, HANG = 26;
        Random r = new Random(sl.getGameTime());
        Set<UUID> struckBy = new HashSet<>();
        sound(sl, c, SoundEvents.BEACON_ACTIVATE, 1.0f, 1.4f);
        sound(sl, p.position(), SoundEvents.ILLUSIONER_PREPARE_MIRROR, 0.8f, 1.5f);
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                double x = (col - (COLS - 1) / 2.0) * 1.05 + (row % 2) * 0.5, z = (row - (ROWS - 1) / 2.0) * 1.9;
                Vec3 sky = c.add(side.scale(x)).add(fwd.scale(z)).add(0, 10 + row * 0.9 + r.nextDouble() * 0.5, 0);
                Vec3 land = c.add(side.scale(x * 0.8 + r.nextGaussian() * 0.3)).add(fwd.scale(z * 0.8 + r.nextGaussian() * 0.3)).add(0, 0.3, 0);
                land = ground(sl, land).add(0, 0.4, 0);
                int hang = HANG + row * 3 + Math.abs(col - COLS / 2) * 1;   // they fall in a sweep, middle first
                int fly = 4;
                final Vec3 to = land;
                int id = sword(sl, pal, sky, to, new Vec3(0, -1, 0), 1.7f, 1f, hang, fly, 0, hang + fly + 10);
                if (r.nextInt(3) == 0) glint(sl, pal, sky.add(r.nextGaussian() * 0.6, 0.8, r.nextGaussian() * 0.6), 0.5f + r.nextFloat() * 0.4f, hang);
                AstaFx.later(hang + fly, () -> {
                    if (!alive(p)) return;
                    for (LivingEntity e : Kit.near(sl, p, to, 1.5)) {
                        int n = e.getPersistentData().getInt("isg_light_rain");
                        if (struckBy.add(e.getUUID())) n = 0;
                        if (n >= 6) continue;   // no more than six swords count on one foe
                        e.getPersistentData().putInt("isg_light_rain", n + 1);
                        hit(p, e, pow * 0.28f, "light_swords_of_judgment");
                    }
                });
            }
        }
        for (int i = 0; i < 4; i++) AstaFx.later(HANG + i * 4, () -> sound(sl, c, SoundEvents.AMETHYST_BLOCK_BREAK, 1.2f, 1.3f));
        AstaFx.later(HANG + 6, () -> FxSend.shake(sl, c, 0.4f, 10, 18));
    }

    // ================================================================== 8. Light Shaft of Divine Punishment
    public static void lightShaft(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        radiance(p);
        final int pal = pal(p);
        final float pow = pw(p, power);
        final Vec3 c = target(p, 30);
        final float R = 3.5f;
        final int WARN = 18;
        pillar(sl, pal, c.add(0, 0.05, 0), R, 34f, WARN, WARN + 24);
        sound(sl, c, SoundEvents.BEACON_POWER_SELECT, 1.0f, 0.8f);
        AstaFx.later(WARN, () -> {
            if (!alive(p)) return;
            for (LivingEntity e : Kit.near(sl, p, c.add(0, 1, 0), R + 0.5)) { hit(p, e, pow, "light_shaft"); Kit.push(e, Vec3.ZERO, 0, 0.35); }
            burst(sl, pal, c.add(0, 1, 0), 4.5f, 10);
            FxSend.shake(sl, c, 0.7f, 12, 24);
            if (!Kit.near(sl, p, c.add(0, 1, 0), R + 0.5).isEmpty()) FxSend.impactFrame(sl, c.add(0, 1, 0), 3, FxSend.FRAME_LIGHT, 32);
            sound(sl, c, SoundEvents.BEACON_ACTIVATE, 1.4f, 0.6f);
            sound(sl, c, SoundEvents.GENERIC_EXPLODE, 0.6f, 1.6f);
        });
        for (int i = 1; i <= 3; i++) AstaFx.later(WARN + i * 5, () -> {
            if (!alive(p)) return;
            for (LivingEntity e : Kit.near(sl, p, c.add(0, 1, 0), R)) hit(p, e, pow * 0.2f, "light_shaft");
        });
    }

    // ================================================================== 9. Demon Light (mode)
    public static void demonLight(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        final int T = 400;
        Moves.aura(p, 5, DARK, T);
        Moves.stance(p, 0, 0, 0.3, 0, 0.2, T);
        burst(sl, DARK, p.position().add(0, 1, 0), 3.5f, 12);
        burst(sl, Fx.SILVER, p.position().add(0, 1, 0), 2.5f, 10);
        circle(sl, DARK, p.position(), Vec3.ZERO, 3f, 0.08f, 30, p, 0.05);
        FxSend.impactFrame(sl, p.position().add(0, 1, 0), 2, FxSend.FRAME_LIGHT, 20);
        sound(sl, p.position(), SoundEvents.BEACON_DEACTIVATE, 1.0f, 0.6f);
        sound(sl, p.position(), SoundEvents.WITHER_AMBIENT, 0.4f, 1.8f);
        final long until = sl.getGameTime() + T;
        Moves.mode(p, MODE, T, 1, pl -> {
            long now = sl.getGameTime();
            if (now > until) return;
            hasten(pl, 1, now % 10 == 0);   // every cooldown runs twice as fast
            if (now % 30 == 0) glint(sl, Fx.SILVER, pl.position().add(Math.random() - 0.5, 0.5 + Math.random() * 1.4, Math.random() - 0.5), 0.4f, 8);
        });
        AstaFx.later(T, () -> Moves.aura(p, 5, DARK, 0));
    }

    // ================================================================== 10. Imperial Swords of Conviction
    public static void imperialSwords(ServerPlayer p, float power) {
        if (LimitBreak.active(p)) { conviction(p, power * 1.3f, true); return; }
        conviction(p, power, false);
    }

    private static void conviction(ServerPlayer p, float power, boolean sovereign) {
        ServerLevel sl = p.serverLevel();
        radiance(p);
        final int pal = pal(p);
        final float pow = pw(p, power);
        final Vec3 c = target(p, 34);
        final float R = sovereign ? 13f : 9f;
        final int WAVES = sovereign ? 4 : 3, PER = 12;
        circle(sl, pal, c.add(0, 0.05, 0), Vec3.ZERO, R, 0.03f, 30 + WAVES * 10 + 40, null, 0);
        sound(sl, c, SoundEvents.BEACON_ACTIVATE, 1.2f, 1.0f);
        sound(sl, p.position(), SoundEvents.EVOKER_PREPARE_SUMMON, 0.8f, 1.4f);
        for (int w = 0; w < WAVES; w++) {
            final int wave = w;
            for (int i = 0; i < PER; i++) {
                double an = (i + w * 0.5) * TAU / PER + w * 0.4;
                double rr = R * (0.85 + (w % 2) * 0.15);
                Vec3 at = c.add(Math.cos(an) * rr, 1.6 + w * 1.4 + (i % 3) * 0.5, Math.sin(an) * rr);
                Vec3 to = c.add(Math.cos(an + 0.9) * 0.6, 1.0, Math.sin(an + 0.9) * 0.6);   // they spiral in a little as they come
                Vec3 in = to.subtract(at).normalize();
                int hang = 14 + w * 10 + (i % 4);
                sword(sl, pal, at, to, in.add(0, 0.2, 0), 1.9f, 1f, hang, 5, 1, hang + 5);
            }
            AstaFx.later(19 + w * 10 + 2, () -> {
                if (!alive(p)) return;
                for (LivingEntity e : Kit.near(sl, p, c.add(0, 1, 0), 3.5)) hit(p, e, pow * (sovereign ? 0.35f : 0.3f), "imperial_swords_of_conviction");
                burst(sl, pal, c.add(0, 1.2, 0), 2.2f, 8);
                sound(sl, c, SoundEvents.AMETHYST_BLOCK_BREAK, 1.3f, 1.0f + wave * 0.15f);
            });
        }
        final int GIANT = 19 + WAVES * 10;
        final float GL = sovereign ? 18f : 13f;
        Vec3 sky = c.add(0, GL * 0.62 + 16, 0), land = c.add(0, GL * 0.62 - 0.6, 0);
        sword(sl, pal, sky, land, new Vec3(0, -1, 0), GL, 1.2f, GIANT - 4 + 14, 5, 2, GIANT + 14 + 5 + 22);
        AstaFx.later(GIANT, () -> {
            if (!alive(p)) return;
            glint(sl, pal, sky, 3.5f, 16);
            sound(sl, c, SoundEvents.BEACON_POWER_SELECT, 1.5f, 0.5f);
        });
        AstaFx.later(GIANT + 15, () -> {
            if (!alive(p)) return;
            for (LivingEntity e : Kit.near(sl, p, c.add(0, 1, 0), sovereign ? 9 : 7)) { hit(p, e, pow * (sovereign ? 2.2f : 1.8f), "imperial_swords_of_conviction"); Kit.push(e, mid(e).subtract(c), 0.9, 0.4); }
            burst(sl, pal, c.add(0, 1, 0), sovereign ? 9f : 7f, 14);
            FxSend.ring(sl, pal, c.add(0, 0.1, 0), 1f, sovereign ? 14f : 10f, 0.2f, 14);
            if (sovereign) pillar(sl, pal, c, 5f, 40f, 0, 26);
            FxSend.shake(sl, c, 1.0f, 18, 36);
            FxSend.impactFrame(sl, c.add(0, 1, 0), 5, FxSend.FRAME_LIGHT, 40);
            sound(sl, c, SoundEvents.GENERIC_EXPLODE, 1.2f, 1.2f);
            sound(sl, c, SoundEvents.BELL_RESONATE, 1.0f, 1.4f);
        });
    }

    // ================================================================== the awakening (Surpassing Limits)
    public static void awaken(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        Vec3 c = p.position();
        circle(sl, GOLD, c.add(0, 0.05, 0), Vec3.ZERO, 4.5f, 0.06f, 44, null, 0);
        for (int i = 0; i < 8; i++) {
            double an = i * TAU / 8;
            Vec3 at = c.add(Math.cos(an) * 2.4, 0.4, Math.sin(an) * 2.4);
            sword(sl, GOLD, at, at.add(0, 4.5, 0), new Vec3(0, 1, 0), 1.6f, 1f, 6 + i, 14, 1, 20 + i + 1);
        }
        burst(sl, GOLD, c.add(0, 1.2, 0), 3.5f, 12);
        glint(sl, GOLD, c.add(0, 2.6, 0), 1.8f, 20);
        sound(sl, c, SoundEvents.BEACON_ACTIVATE, 1.2f, 1.2f);
        sound(sl, c, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 0.8f);
    }

    public static void clearAll() {}
}
