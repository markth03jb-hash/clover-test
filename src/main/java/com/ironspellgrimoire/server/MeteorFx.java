package com.ironspellgrimoire.server;

import static com.ironspellgrimoire.server.FxUtil.*;
import com.ironspellgrimoire.IronSpellGrimoire;
import com.ironspellgrimoire.network.AddonNetwork;
import com.ironspellgrimoire.network.FxPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Devil Union: Black Meteorite - a cutscene combo.
 *
 *  DASH      you rocket at the enemy you aim at, trailing black flame. Miss and it ends there.
 *  LAUNCH    on contact an uppercut slash throws them high into the air
 *  BLITZ     piercing dashes straight through them, ten times from every side
 *  FINISHER  you stop beside them in mid-air and charge, then one huge slash straight through them, on an impact frame
 *  FINALE    the final blow lands in the air with a burst of black flame and red light
 *
 * The victim cannot die until the finisher lands (the finishing blow can kill). You can't be hurt while it plays.
 * The caster's client plays it as a cutscene - see client/Cutscene. Packet shape 9 = cutscene (palette = phase:
 * 0 dash, 2 blitz, 3 finisher, 1 impact/end; c = where to look), shape 10 = screen shake for people nearby.
 */
@Mod.EventBusSubscriber(modid = IronSpellGrimoire.MODID)
public final class MeteorFx {
    private MeteorFx() {}

    private static final int EMBER = 9, DARK = 1, SILVER = 6;
    private static final int MAX_DASH = 12, LAUNCH = 5, GATHER = 6, MAX_FALL = 12;
    private static final Set<UUID> ACTIVE = new HashSet<>();      // casters in the combo
    private static final Set<UUID> PROTECTED = new HashSet<>();   // victims who can't die yet

    public static boolean active(ServerPlayer p) { return ACTIVE.contains(p.getUUID()); }

    private static void cutscene(ServerPlayer p, Vec3 look, int phase) {
        FxPacket pkt = new FxPacket();
        pkt.shape = 9; pkt.id = p.getId(); pkt.palette = (byte) phase; pkt.c = look;
        AddonNetwork.send(PacketDistributor.PLAYER.with(() -> p), pkt);
    }

    private static void shake(ServerLevel sl, Vec3 at, float strength, int ticks) {
        FxPacket pkt = new FxPacket();
        pkt.shape = 10; pkt.c = at; pkt.r = strength; pkt.life = ticks;
        AddonNetwork.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(at.x, at.y, at.z, 48, sl.dimension())), pkt);
    }

    /** Anime impact frames on the screens of everyone close to {@code at}. */
    private static void impactFrame(ServerLevel sl, Vec3 at, int ticks) { impactFrame(sl, at, ticks, false); }

    /** slash = the finisher's frame: one huge white slash across the screen. */
    private static void impactFrame(ServerLevel sl, Vec3 at, int ticks, boolean slash) {
        FxPacket pkt = new FxPacket();
        pkt.shape = 12; pkt.life = ticks; pkt.c = at; pkt.palette = (byte) (slash ? 1 : 0);
        AddonNetwork.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(at.x, at.y, at.z, 24, sl.dimension())), pkt);
    }

    /** Put the caster at {@code pos}, facing {@code look}, motionless. */
    private static void place(ServerPlayer p, Vec3 pos, Vec3 look) {
        Vec3 d = look.subtract(pos.add(0, 1.5, 0));
        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        p.connection.teleport(pos.x, pos.y, pos.z, yaw, pitch);
        p.setDeltaMovement(Vec3.ZERO);
        p.hurtMarked = true;
        p.resetFallDistance();
    }

    /** Hold the victim with its middle at {@code at}. */
    private static void hold(LivingEntity t, Vec3 at) {
        t.teleportTo(at.x, at.y - t.getBbHeight() * 0.5, at.z);
        t.setDeltaMovement(Vec3.ZERO);
        t.hurtMarked = true;
        t.resetFallDistance();
    }

    private static Vec3 closest(Vec3 a, Vec3 b, Vec3 q) {
        Vec3 ab = b.subtract(a);
        double l = ab.lengthSqr();
        if (l < 1.0E-6) return a;
        double t = Math.max(0, Math.min(1, q.subtract(a).dot(ab) / l));
        return a.add(ab.scale(t));
    }

    private static boolean ok(ServerPlayer p) { return p.isAlive() && !p.isRemoved(); }

    private static void abort(ServerPlayer p, LivingEntity t) {
        ACTIVE.remove(p.getUUID());
        if (t != null) PROTECTED.remove(t.getUUID());
        if (!p.isRemoved()) cutscene(p, p.position(), 1);
    }

    // ------------------------------------------------------------------ cast + dash
    public static void cast(ServerPlayer p, float power) {
        if (!ACTIVE.add(p.getUUID())) return;
        ServerLevel sl = p.serverLevel();
        Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
        LivingEntity target = null;
        double best = Math.cos(Math.toRadians(22));
        for (LivingEntity t : sl.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(28), x -> x != p && x.isAlive() && !x.isAlliedTo(p))) {
            Vec3 to = mid(t).subtract(eye);
            double d = to.length();
            if (d > 28 || !p.hasLineOfSight(t)) continue;
            double dot = to.scale(1 / d).dot(look);
            if (dot > best) { best = dot; target = t; }
        }
        Vec3 aim = target != null ? mid(target) : eye.add(look.scale(14));
        cutscene(p, aim, 0);
        sound(sl, p.position(), net.minecraft.sounds.SoundEvents.ENDER_DRAGON_FLAP, 1f, 0.6f);
        sound(sl, p.position(), net.minecraft.sounds.SoundEvents.TRIDENT_RIPTIDE_3, 1f, 0.7f);
        FxLib.shockRing(sl, EMBER, p.position().add(0, 0.05, 0), 1.0f);
        dash(p, sl, target, aim, power, 0, p.position().add(0, 1, 0));
    }

    private static void dash(ServerPlayer p, ServerLevel sl, LivingEntity t, Vec3 aim, float power, int step, Vec3 last) {
        if (!ok(p) || !AstaFx.inDevilUnion(p)) { abort(p, null); return; }
        Vec3 me = p.position().add(0, 1, 0);
        Vec3 goal = t != null && t.isAlive() ? mid(t) : aim;
        if (t != null && t.isAlive()) {
            // a hit if the target is close, or if the last tick of the dash swept past it (no more passing through)
            double swept = closest(last, me, goal).distanceTo(goal);
            if (me.distanceTo(goal) < 3.0 || swept < 2.4 + t.getBbWidth() * 0.5) { launch(p, sl, t, power); return; }
            // close enough that the next step would overshoot: stop right in front of them instead
            if (me.distanceTo(goal) < 5.6) {
                Vec3 dd = goal.subtract(me).normalize();
                place(p, goal.subtract(dd.scale(1.6)).subtract(0, 1.0, 0), goal);
                FxSend.trail(sl, EMBER, me, goal.subtract(dd.scale(1.6)), 0.9f, 8);
                launch(p, sl, t, power);
                return;
            }
        }
        if (step >= MAX_DASH || (t == null && me.distanceTo(goal) < 2.0) || (step > 1 && p.horizontalCollision)) {
            // missed: skid to a stop
            p.setDeltaMovement(Vec3.ZERO);
            p.hurtMarked = true;
            FxLib.groundPuff(sl, EMBER, p.position(), 1.6f);
            p.displayClientMessage(Component.translatable("ui.iron_spell_grimoire.meteorite_miss").withStyle(ChatFormatting.GRAY), true);
            abort(p, null);
            return;
        }
        Vec3 dir = goal.subtract(me).normalize();
        p.setDeltaMovement(dir.scale(2.6));
        p.hurtMarked = true;
        p.resetFallDistance();
        FxSend.trail(sl, EMBER, last, me, 0.9f, 8);
        FxSend.trail(sl, DARK, last, me, 0.45f, 7);
        FxSend.wall(sl, DARK, me, FxLib.right(dir), FxLib.up(dir), 0.6f, 1.2f, -180, 180, 0.1f, 4, 0, 0);
        AstaFx.later(1, () -> dash(p, sl, t, aim, power, step + 1, me));
    }

    // ------------------------------------------------------------------ launch + blitz
    private static void launch(ServerPlayer p, ServerLevel sl, LivingEntity t, float power) {
        PROTECTED.add(t.getUUID());
        Vec3 base = mid(t);
        // how high there's room for (up to 6 blocks)
        var ceil = sl.clip(new ClipContext(base, base.add(0, 7, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        double room = ceil.getType() == HitResult.Type.MISS ? 6 : Math.max(1.5, ceil.getLocation().y - base.y - 1.5);
        Vec3 apex = base.add(0, room, 0);
        p.setDeltaMovement(Vec3.ZERO);
        p.hurtMarked = true;
        // the uppercut
        Vec3 f = new Vec3(base.x - p.getX(), 0, base.z - p.getZ());
        f = f.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : f.normalize();
        FxSend.crescent(sl, DARK, base, new Vec3(0, 1, 0), FxLib.right(f), 1.6f, 3.2f, 8, 1.2f);
        FxSend.wall(sl, EMBER, base.subtract(f.scale(0.5)), f, new Vec3(0, 1, 0), 2.0f, 2.4f, -60, 90, 0.4f, 7, 2, 0);
        FxLib.impact(sl, EMBER, base, 1.4f);
        AstaFx.damage(p, t, power * 0.3f, "devil_black_meteorite");
        sound(sl, base, net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_STRONG, 1.2f, 0.6f);
        sound(sl, base, net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE, 0.6f, 1.4f);
        impactFrame(sl, base, 2);   // the uppercut connects
        cutscene(p, apex, 2);
        rise(p, sl, t, base, apex, power, 0);
    }

    private static void rise(ServerPlayer p, ServerLevel sl, LivingEntity t, Vec3 from, Vec3 apex, float power, int k) {
        if (!ok(p) || !t.isAlive()) { abort(p, t); return; }
        double u = Math.min(1.0, (k + 1) / (double) LAUNCH);
        u = 1 - (1 - u) * (1 - u);
        Vec3 at = from.add(apex.subtract(from).scale(u));
        hold(t, at);
        FxSend.beam(sl, EMBER, from, at, 0.35f, 6);
        if (k + 1 >= LAUNCH) { blitz(p, sl, t, apex, power, 0, p.position().add(0, 1, 0)); return; }
        AstaFx.later(1, () -> rise(p, sl, t, from, apex, power, k + 1));
    }

    /**
     * The blitz: piercing dashes straight through them, again and again from every side - each dash starts a few
     * blocks out, tears through the middle of them and out the other side in three ticks, leaving a black-flame streak.
     */
    private static final int DASHES = 10, DASH_TICKS = 3;

    private static void blitz(ServerPlayer p, ServerLevel sl, LivingEntity t, Vec3 apex, float power, int k, Vec3 prevPos) {
        if (!ok(p) || !t.isAlive()) { abort(p, t); return; }
        hold(t, apex);
        if (k >= DASHES * DASH_TICKS) { finisher(p, sl, t, apex, power, 0); return; }
        int dash = k / DASH_TICKS, step = k % DASH_TICKS;
        Random r = new Random(apex.hashCode() * 31L + dash);
        Vec3 off = dashDir(dash).scale(4.0);
        Vec3 from = apex.add(off), to = apex.subtract(off);
        Vec3 pos = from.add(to.subtract(from).scale(step / (double) (DASH_TICKS - 1)));
        place(p, pos.subtract(0, 1.0, 0), to.add(to.subtract(from).normalize().scale(3)));
        FxSend.trail(sl, EMBER, prevPos, pos, 0.5f, 6);       // the streak of the dash
        FxSend.trail(sl, DARK, prevPos, pos, 0.25f, 5);
        if (step == 1) {                                     // tearing through them
            Vec3 cd = to.subtract(from).normalize();
            FxSend.crescent(sl, dash % 2 == 0 ? DARK : EMBER, apex, cd, FxLib.up(cd), 1.1f, 1.8f, 4, 0f);
            FxSend.beam(sl, SILVER, from, to, 0.04f, 4);
            AstaFx.damage(p, t, power * 0.12f, "devil_black_meteorite");
            if (dash % 3 == 0) impactFrame(sl, apex, 1);
            sound(sl, apex, net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_SWEEP, 0.9f, 1.2f + r.nextFloat() * 0.6f);
            sound(sl, apex, net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_CRIT, 0.7f, 1.0f + r.nextFloat() * 0.5f);
        }
        if (step == 0) sound(sl, pos, net.minecraft.sounds.SoundEvents.TRIDENT_RIPTIDE_1, 0.5f, 1.6f);
        final Vec3 now = step == DASH_TICKS - 1 ? null : pos;
        AstaFx.later(1, () -> blitz(p, sl, t, apex, power, k + 1, now == null ? apexNext(apex, dash + 1) : now));
    }

    /** Where the next dash will start (so the streak links up as a blink). */
    private static Vec3 apexNext(Vec3 apex, int dash) {
        return apex.add(dashDir(dash).scale(4.0));
    }

    /** Dash directions spread evenly around them (golden-angle spiral): high, low, left, right - never the same twice. */
    private static Vec3 dashDir(int dash) {
        double y = 0.75 - 1.5 * ((dash * 0.618034) % 1.0);       // from steeply above to steeply below
        double a = dash * 2.39996;                               // golden angle around them
        double rr = Math.sqrt(Math.max(0, 1 - y * y));
        return new Vec3(Math.cos(a) * rr, y, Math.sin(a) * rr);
    }

    // ------------------------------------------------------------------ finisher
    private static final int CHARGE = 10;

    /**
     * The finisher: you stop a few blocks from them, still in the air, and charge - black flame and red light pour into
     * the blade - then one huge slash straight through them, frozen on an impact frame, and the sky behind them splits.
     */
    private static void finisher(ServerPlayer p, ServerLevel sl, LivingEntity t, Vec3 apex, float power, int k) {
        if (!ok(p) || !t.isAlive()) { abort(p, t); return; }
        hold(t, apex);
        Vec3 side = new Vec3(p.getX() - apex.x, 0, p.getZ() - apex.z);
        side = side.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : side.normalize();
        Vec3 stand = apex.add(side.scale(5.0)).add(0, 0.6, 0);
        if (k == 0) {
            cutscene(p, apex, 3);
            sound(sl, stand, net.minecraft.sounds.SoundEvents.BEACON_POWER_SELECT, 1f, 0.5f);
        }
        if (k < CHARGE) {                                    // the charge
            place(p, stand.subtract(0, 1.0, 0), apex);
            Vec3 blade = stand.add(0, 0.3, 0);
            FxSend.ring(sl, EMBER, blade, 3.2f - k * 0.3f, 0.3f, 0.08f, 3);
            FxSend.wall(sl, DARK, stand.subtract(0, 0.4, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1), 0.6f + k * 0.06f, 0.7f + k * 0.08f, -180, 180, 1.0f, 3, 0, 0);
            if (k % 3 == 0) {
                Random r = new Random();
                Vec3 from = blade.add(r.nextDouble() * 4 - 2, r.nextDouble() * 3 - 1, r.nextDouble() * 4 - 2);
                FxSend.trail(sl, EMBER, from, blade, 0.12f, 4);
            }
            if (k == CHARGE - 2) FxLib.flash(sl, EMBER, blade, 1.4f);
            AstaFx.later(1, () -> finisher(p, sl, t, apex, power, k + 1));
            return;
        }
        // the slash: you are through them and out the other side in an instant
        Vec3 out = apex.subtract(side.scale(5.0)).add(0, 0.6, 0);
        place(p, out.subtract(0, 1.0, 0), out.subtract(side.scale(3)));
        Vec3 cut = side.scale(-1);
        Vec3 diag = new Vec3(0, 1, 0).add(FxLib.right(cut).scale(0.6)).normalize();
        FxSend.slash(sl, DARK, apex, cut, diag, 3.0f, 6.5f, 14, 0.15f);
        FxSend.trail(sl, EMBER, stand, out, 0.7f, 10);
        FxSend.trail(sl, SILVER, stand, out, 0.06f, 8);
        impactFrame(sl, apex, 6, true);
        finale(p, sl, t, apex, power);
    }

    /** The finisher lands in mid-air: the final blow (this one can kill), a burst of black flame and red light. */
    private static void finale(ServerPlayer p, ServerLevel sl, LivingEntity victim, Vec3 at, float power) {
        ACTIVE.remove(p.getUUID());
        PROTECTED.remove(victim.getUUID());
        p.setDeltaMovement(Vec3.ZERO);
        p.hurtMarked = true;
        p.resetFallDistance();
        cutscene(p, at, 1);
        shake(sl, at, 1.0f, 20);
        AstaFx.damage(p, victim, power * 1.6f, "devil_black_meteorite");
        victim.setDeltaMovement(0, -0.6, 0);
        victim.hurtMarked = true;
        for (LivingEntity t : sl.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(6), x -> x != p && x != victim && x.isAlive() && !x.isAlliedTo(p))) {
            if (t.position().distanceTo(at) > 6) continue;
            AstaFx.damage(p, t, power * 0.6f, "devil_black_meteorite");
        }
        AstaFx.later(2, () -> {
            FxLib.flash(sl, SILVER, at, 2.0f);
            FxSend.ring(sl, EMBER, at, 0.5f, 9f, 0.3f, 12);
            FxSend.wall(sl, DARK, at, new Vec3(0, 1, 0), FxLib.right(new Vec3(0, 1, 0)), 0.5f, 7f, -180, 180, 0.4f, 12, 0, 0);
            FxLib.spikeBurst(sl, DARK, at, 1.8f);
            FxLib.dome(sl, EMBER, at.subtract(0, 1, 0), 1.6f);
            sound(sl, at, net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE, 1.4f, 0.5f);
            sound(sl, at, net.minecraft.sounds.SoundEvents.WARDEN_SONIC_BOOM, 1f, 0.7f);
        });
        sound(sl, at, net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_SWEEP, 1.5f, 0.4f);
        sound(sl, at, net.minecraft.sounds.SoundEvents.TRIDENT_THUNDER, 0.9f, 0.6f);
    }

    private static void impact(ServerPlayer p, ServerLevel sl, LivingEntity victim, Vec3 at, float power) {
        ACTIVE.remove(p.getUUID());
        PROTECTED.remove(victim.getUUID());
        p.setDeltaMovement(Vec3.ZERO);
        p.hurtMarked = true;
        p.resetFallDistance();
        cutscene(p, at, 1);
        impactFrame(sl, at, 6, true);   // the finisher lands: white, black, white, black - and the slash across the screen
        shake(sl, at, 1.4f, 26);
        // the finishing blow - this one can kill
        AstaFx.damage(p, victim, power * 2.0f, "devil_black_meteorite");
        Vec3 gg = FxLib.ground(sl, at, 2.5);
        boolean crater = gg != null;   // no ground close below (it hit in mid-air): no crater
        Vec3 g = crater ? gg : at;
        double R = 8;
        for (LivingEntity t : sl.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(R, 5, R), x -> x != p && x != victim && x.isAlive() && !x.isAlliedTo(p))) {
            double dist = t.position().distanceTo(at);
            if (dist > R) continue;
            AstaFx.damage(p, t, (float) (power * (1.0 - dist / R * 0.6)), "devil_black_meteorite");
            Vec3 away = t.position().subtract(at).multiply(1, 0, 1);
            away = away.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : away.normalize();
            t.setDeltaMovement(away.scale(1.4 - dist / R).add(0, 0.8 - dist / R * 0.4, 0));
            t.hurtMarked = true;
        }
        FxLib.flash(sl, SILVER, at.add(0, 1, 0), 2.0f);
        if (crater) {
            FxSend.ring(sl, EMBER, g, 0.5f, 12f, 0.35f, 14);
            FxSend.ring(sl, DARK, g, 0.5f, 8f, 0.6f, 12);
        }
        AstaFx.later(3, () -> FxSend.ring(sl, SILVER, g, 1f, 16f, 0.06f, 12));
        FxLib.dome(sl, EMBER, g, 2.0f);
        FxSend.beam(sl, EMBER, g, g.add(0, 14, 0), 2.8f, 20);
        FxSend.beam(sl, DARK, g, g.add(0, 10, 0), 1.6f, 18);
        Random rnd = new Random();
        if (crater) for (int k = 0; k < 12; k++) {             // burning fissures
            double a = Math.PI * 2 * k / 12 + rnd.nextDouble() * 0.3;
            Vec3 prev = FxLib.ground(sl, g.add(Math.cos(a) * 2.2, 0, Math.sin(a) * 2.2), 2.5);
            if (prev == null) continue;
            for (int s = 1; s <= 4; s++) {
                double rr = 2.2 + s * 1.5, aa = a + (rnd.nextDouble() - 0.5) * 0.25;
                Vec3 q = FxLib.ground(sl, g.add(Math.cos(aa) * rr, 0, Math.sin(aa) * rr), 2.5);   // the cracks follow the ground
                if (q == null) break;                          // ...and stop at a drop
                final Vec3 pa = prev, pb = q;
                AstaFx.later(s, () -> FxSend.beam(sl, EMBER, pa, pb, 0.16f, 40));
                prev = q;
            }
        }
        if (crater) for (int k = 0; k < 14; k++) {             // crater rim of black shards
            double a = Math.PI * 2 * k / 14 + rnd.nextDouble() * 0.2;
            Vec3 b0 = FxLib.ground(sl, g.add(Math.cos(a) * 3.0, 0, Math.sin(a) * 3.0), 2.5);
            if (b0 == null) continue;
            Vec3 b = b0.add(0, -0.2, 0);
            FxSend.spike(sl, k % 4 == 0 ? EMBER : DARK, b, new Vec3(Math.cos(a) * 0.8, 1, Math.sin(a) * 0.8), 1.3f + rnd.nextFloat() * 1.1f, 0.26f, 50, 2, FxPacket.GROUND);
        }
        FxLib.spikeBurst(sl, DARK, at.add(0, 1, 0), 1.8f);
        // the impact breaks space itself: the ground shatters into void, and a rift tears up through the column
        FxSend.cracks(sl, EMBER, g.add(0, 0.05, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1), crater ? 7.5f : 4f, 70, false, (int) sl.getGameTime());
        Vec3 view = p.getLookAngle();
        Vec3 side = new Vec3(-view.z, 0, view.x);
        side = side.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : side.normalize();
        FxSend.cracks(sl, EMBER, g.add(0, 3.5, 0), new Vec3(0, 1, 0), side, 4.5f, 50, true, (int) sl.getGameTime() + 7);
        AstaFx.later(6, () -> {   // a black-flame aftershock rolls out across the crater
            FxSend.ring(sl, DARK, g.add(0, 0.08, 0), 2f, 10f, 0.9f, 12);
            for (LivingEntity t : sl.getEntitiesOfClass(LivingEntity.class, new AABB(g, g).inflate(10, 4, 10), x -> x != p && x.isAlive() && !x.isAlliedTo(p))) {
                if (t.position().distanceTo(g) > 10) continue;
                AstaFx.damage(p, t, power * 0.4f, "devil_black_meteorite");
            }
            sound(sl, g, net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE, 1f, 0.4f);
        });
        sound(sl, at, net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE, 1.6f, 0.5f);
        sound(sl, at, net.minecraft.sounds.SoundEvents.WARDEN_SONIC_BOOM, 1f, 0.6f);
        sound(sl, at, net.minecraft.sounds.SoundEvents.LIGHTNING_BOLT_THUNDER, 0.8f, 0.6f);
    }

    // ------------------------------------------------------------------ rules while it plays
    /** The victim can't die before the finisher; anything that would kill it leaves it at half a heart. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(LivingDamageEvent e) {
        LivingEntity t = e.getEntity();
        if (PROTECTED.contains(t.getUUID()) && e.getAmount() >= t.getHealth()) e.setAmount(Math.max(0f, t.getHealth() - 1f));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDeath(LivingDeathEvent e) {
        LivingEntity t = e.getEntity();
        if (PROTECTED.contains(t.getUUID())) {
            e.setCanceled(true);
            t.setHealth(1f);
        }
        if (t instanceof ServerPlayer sp) ACTIVE.remove(sp.getUUID());
    }

    /** You can't be hurt while the combo plays. */
    @SubscribeEvent
    public static void onAttacked(LivingAttackEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && ACTIVE.contains(p.getUUID())) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onStop(net.minecraftforge.event.server.ServerStoppingEvent e) {
        ACTIVE.clear();
        PROTECTED.clear();
    }
}
