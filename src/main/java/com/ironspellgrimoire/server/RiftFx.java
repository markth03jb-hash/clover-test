package com.ironspellgrimoire.server;

import static com.ironspellgrimoire.server.FxUtil.*;

import com.ironspellgrimoire.IronSpellGrimoire;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Langris Vaude's Spatial Magic - the eight spells of the Rift Grimoire. Where Finral opens gates to move things,
 * Langris cuts space itself: a cut that lands where he looks, blades that come out of gates beside the enemy, lines of
 * severed space that snap shut. Magenta-violet cuts and cracks of space, hard straight lines and corners.
 *
 *  1 Spatial Cut          - a cut in space appears on the enemy you aim at, and the space snaps shut on it
 *  2 Rift Blades          - three gates open around an enemy and a blade comes out of each
 *  3 Blink Strike         - step through a gate, come out behind the enemy you aim at and cut as you arrive
 *  4 Severing Line        - one long straight cut through everything ahead; a moment later it snaps shut and drags them in
 *  5 Gate Barrage         - a ring of small gates round you fires blade after blade at everything near
 *  6 Space Collapse       - a cube of space is cut out at the spot you aim and crushed in on itself
 *  7 Convergence          - blades come out of gates all round one enemy and close on it from every direction
 *  8 Dimensional Severance - the sky itself is cut open along a line 24 blocks long: everything in it is severed
 */
@Mod.EventBusSubscriber(modid = IronSpellGrimoire.MODID)
public final class RiftFx {
    private RiftFx() {}
    private static final int V = Fx.VOID, S = Fx.SILVER;

    // ------------------------------------------------------------------ shared
    /** A cut in space: a bright straight line with cracks of space spreading off it. */
    static void cut(ServerLevel sl, Vec3 c, Vec3 along, Vec3 normal, float half, int life) {
        Vec3 a = c.subtract(along.scale(half)), b = c.add(along.scale(half));
        FxSend.beam(sl, V, a, b, 0.2f, life);
        FxSend.beam(sl, S, a, b, 0.06f, Math.max(2, life - 2));
        FxSend.cracks(sl, V, c, along, normal, half * 0.9f, life + 4, true, (int) (c.x * 31 + c.z * 17) & 0xFFFF);
    }

    /** A small gate with a blade coming out of it towards {@code to}. */
    private static void bladeFrom(ServerPlayer p, ServerLevel sl, Vec3 gate, Vec3 to, float dmg, String id, LivingEntity t) {
        Vec3 d = to.subtract(gate);
        if (d.lengthSqr() < 1.0E-4) return;
        SpaceFx.gate(sl, gate, d.normalize(), 0.45f, 0.45f, 8, 0);
        AstaFx.later(3, () -> {
            FxSend.spike(sl, V, gate, d.normalize(), (float) d.length(), 0.14f, 4, 2, 2.0f);
            FxSend.beam(sl, S, gate, to, 0.03f, 3);
            if (t != null && t.isAlive()) { AstaFx.damage(p, t, dmg, id); Fx.spark(sl, V, to, 0.9f); }
        });
    }

    // ================================================================== 1. Spatial Cut
    public static void spatialCut(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        LivingEntity t = aimed(p, 28, 16);
        Vec3 c = t != null ? mid(t) : Kit.aim(p, 22);
        Vec3 look = flat(p.getLookAngle());
        Vec3 rt = rightOf(look);
        double tilt = ThreadLocalRandom.current().nextDouble(-0.5, 0.5);
        Vec3 along = rt.scale(Math.cos(tilt)).add(0, Math.sin(tilt), 0).normalize();
        sound(sl, p.position(), SoundEvents.ENDERMAN_TELEPORT, 0.6f, 1.8f);
        AstaFx.later(6, () -> {
            if (!alive(p)) return;
            Vec3 at = t != null && t.isAlive() ? mid(t) : c;
            cut(sl, at, along, look, 1.8f, 8);
            for (LivingEntity e : Kit.near(sl, p, at, 2.0)) {
                AstaFx.damage(p, e, power * 1.1f, "spatial_cut");
                Fx.impact(sl, V, mid(e), 1.2f);
            }
            sound(sl, at, SoundEvents.GLASS_BREAK, 0.8f, 0.7f);
            sound(sl, at, SoundEvents.PLAYER_ATTACK_SWEEP, 0.8f, 1.6f);
        });
    }

    // ================================================================== 2. Rift Blades
    public static void riftBlades(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        LivingEntity t = aimed(p, 28, 16);
        if (t == null) return;
        sound(sl, p.position(), SoundEvents.ENDERMAN_TELEPORT, 0.6f, 1.5f);
        for (int i = 0; i < 3; i++) {
            final int ii = i;
            AstaFx.later(5 + i * 4, () -> {
                if (!alive(p) || !t.isAlive()) return;
                double a = ii * 2.1 + t.getId();
                Vec3 g = mid(t).add(Math.cos(a) * 2.8, 0.6 + ii * 0.4, Math.sin(a) * 2.8);
                bladeFrom(p, sl, g, mid(t), power * 0.42f, "rift_blades", t);
            });
        }
    }

    // ================================================================== 3. Blink Strike
    public static void blinkStrike(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        LivingEntity t = aimed(p, 22, 18);
        if (t == null) return;
        Vec3 behind = t.position().subtract(flat(t.getLookAngle()).scale(1.5));
        Vec3 dest = SpaceFx.safe(sl, behind, p);
        if (dest == null) return;
        Vec3 d0 = flat(mid(t).subtract(p.position()));
        SpaceFx.gate(sl, p.position().add(0, 1, 0).add(d0.scale(0.7)), d0.scale(-1), 0.8f, 1.2f, 8, 0);
        sound(sl, p.position(), SoundEvents.ENDERMAN_TELEPORT, 0.8f, 1.4f);
        AstaFx.later(4, () -> {
            if (!alive(p) || !t.isAlive()) return;
            Vec3 face = mid(t).subtract(dest);
            float yaw = (float) Math.toDegrees(Math.atan2(-face.x, face.z));
            Vec3 from = p.position().add(0, 1, 0);
            SpaceFx.move(p, dest, yaw);
            FxSend.trail(sl, V, from, dest.add(0, 1, 0), 0.08f, 5);
            SpaceFx.gate(sl, dest.add(0, 1, 0).subtract(flat(face).scale(0.7)), flat(face), 0.8f, 1.2f, 8, 0);
            Vec3 rt = rightOf(flat(face));
            cut(sl, mid(t), rt, flat(face), 1.6f, 7);
            AstaFx.damage(p, t, power * 1.25f, "blink_strike");
            Fx.impact(sl, V, mid(t), 1.6f);
            FxSend.impactFrame(sl, mid(t), 3, FxSend.FRAME_FINRAL, 24);
            sound(sl, dest, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, 1.2f);
        });
    }

    // ================================================================== 4. Severing Line
    public static void severingLine(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        sound(sl, p.position(), SoundEvents.ENDERMAN_TELEPORT, 0.8f, 0.9f);
        AstaFx.later(8, () -> {
            if (!alive(p)) return;
            Vec3 from = p.getEyePosition().add(0, -0.3, 0), dir = p.getLookAngle();
            Vec3 end = clip(sl, p, from, from.add(dir.scale(26)));
            Vec3 axis = end.subtract(from);
            double len = axis.length();
            if (len < 1) return;
            Vec3 n = axis.normalize();
            FxSend.beam(sl, V, from, end, 0.24f, 14);
            FxSend.beam(sl, S, from, end, 0.07f, 12);
            for (double s = 3; s < len; s += 4.5) FxSend.cracks(sl, V, from.add(n.scale(s)), rightOf(n), n.cross(rightOf(n)).normalize(), 1.4f, 16, true, (int) s * 13);
            for (LivingEntity t : Kit.along(sl, p, from, end, 1.0)) {
                AstaFx.damage(p, t, power * 0.9f, "severing_line");
                Fx.impact(sl, V, mid(t), 1.2f);
            }
            sound(sl, from, SoundEvents.GLASS_BREAK, 1.0f, 0.6f);
            sound(sl, from, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, 0.8f);
            FxSend.shake(sl, from, 0.3f, 5, 24);
            AstaFx.later(12, () -> {   // the severed space snaps shut and drags what is near it in
                if (!alive(p)) return;
                for (LivingEntity t : Kit.along(sl, p, from, end, 3.5)) {
                    Vec3 on = closestOnSegment(from, end, mid(t));
                    Kit.push(t, on.subtract(mid(t)), 0.7, 0.1);
                    AstaFx.damage(p, t, power * 0.6f, "severing_line");
                }
                for (double s = 0; s < len; s += 3) FxSend.ring(sl, V, from.add(n.scale(s)), 2.5f, 0.2f, 0.06f, 5);
                sound(sl, end, SoundEvents.GENERIC_EXPLODE, 0.6f, 1.6f);
            });
        });
    }

    // ================================================================== 5. Gate Barrage
    public static void gateBarrage(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        sound(sl, p.position(), SoundEvents.ENDERMAN_TELEPORT, 0.9f, 1.1f);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int v = 0; v < 4; v++) {
            final int vv = v;
            AstaFx.later(6 + v * 6, () -> {
                if (!alive(p)) return;
                List<LivingEntity> foes = new ArrayList<>(Kit.near(sl, p, p.position().add(0, 1, 0), 16));
                for (int g = 0; g < 8; g++) {
                    double a = vv * 0.4 + g * Math.PI / 4;
                    Vec3 pos = p.position().add(0, 1.2 + Math.sin(a * 3) * 0.5, 0).add(Math.cos(a) * 2.6, 0, Math.sin(a) * 2.6);
                    if (foes.isEmpty()) { if (g == 0) SpaceFx.gate(sl, pos, new Vec3(Math.cos(a), 0, Math.sin(a)).scale(-1), 0.4f, 0.4f, 6, 0); continue; }
                    if (g % 2 != vv % 2) continue;
                    LivingEntity t = foes.get(r.nextInt(foes.size()));
                    bladeFrom(p, sl, pos, mid(t), power * 0.26f, "gate_barrage", t);
                }
            });
        }
    }

    // ================================================================== 6. Space Collapse
    public static void spaceCollapse(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        LivingEntity main = aimed(p, 28, 18);
        Vec3 c = main != null ? mid(main) : Kit.aim(p, 22).add(0, 1.5, 0);
        final float H = 2.6f;
        SpaceFx.cube(sl, c, H, 20);
        sound(sl, c, SoundEvents.WARDEN_SONIC_CHARGE, 1.0f, 1.0f);
        for (int t = 0; t < 14; t++) {
            final int tt = t;
            AstaFx.later(t + 1, () -> {
                if (!alive(p)) return;
                for (LivingEntity e : Kit.near(sl, p, c, H * 1.7)) {
                    Vec3 in = c.subtract(mid(e));
                    e.setDeltaMovement(in.lengthSqr() < 0.3 ? Vec3.ZERO : in.normalize().scale(Math.min(0.6, 0.15 + tt * 0.03)));
                    e.hurtMarked = true;
                }
                if (tt >= 8) SpaceFx.cube(sl, c, H * (1.0f - (tt - 8) * 0.14f), 2);
            });
        }
        AstaFx.later(16, () -> {
            if (!alive(p)) return;
            boolean any = false;
            for (LivingEntity e : Kit.near(sl, p, c, H * 1.7)) { AstaFx.damage(p, e, power * 1.5f, "space_collapse"); any = true; }
            if (any) FxSend.impactFrame(sl, c, 3, FxSend.FRAME_FINRAL, 30);
            Fx.flash(sl, c, 2.2f);
            FxSend.ring(sl, V, c, 0.2f, H * 2.4f, 0.2f, 10);
            for (int i = 0; i < 6; i++) {
                double a = i * Math.PI / 3;
                FxSend.cracks(sl, V, c.add(Math.cos(a) * H, 0, Math.sin(a) * H), new Vec3(Math.cos(a), 0, Math.sin(a)), new Vec3(0, 1, 0), 1.6f, 14, true, i * 77);
            }
            FxSend.shake(sl, c, 0.8f, 10, 36);
            sound(sl, c, SoundEvents.GLASS_BREAK, 1.2f, 0.5f);
            sound(sl, c, SoundEvents.GENERIC_EXPLODE, 0.9f, 0.8f);
        });
    }

    // ================================================================== 7. Convergence
    public static void convergence(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        LivingEntity t = aimed(p, 28, 16);
        if (t == null) return;
        sound(sl, p.position(), SoundEvents.ENDERMAN_TELEPORT, 0.8f, 1.3f);
        Set<Integer> opened = new HashSet<>();
        for (int i = 0; i < 12; i++) {
            final int ii = i;
            AstaFx.later(6 + i * 2, () -> {
                if (!alive(p) || !t.isAlive()) return;
                double yaw = ii * 2.399963 + 0.5, pitch = Math.asin(-0.6 + 1.2 * ((ii * 0.618) % 1.0));
                Vec3 n = new Vec3(Math.cos(pitch) * Math.cos(yaw), Math.sin(pitch), Math.cos(pitch) * Math.sin(yaw));
                bladeFrom(p, sl, mid(t).add(n.scale(3.2)), mid(t), power * 0.2f, "convergence", t);
                if (opened.add(ii) && ii % 4 == 0) sound(sl, mid(t), SoundEvents.AMETHYST_BLOCK_BREAK, 0.6f, 1.6f);
            });
        }
        AstaFx.later(6 + 12 * 2 + 5, () -> {
            if (!t.isAlive()) return;
            Fx.impact(sl, V, mid(t), 2.0f);
            FxSend.ring(sl, V, mid(t), 0.2f, 3.2f, 0.1f, 8);
        });
    }

    // ================================================================== 8. Dimensional Severance
    /**
     * Space itself is cut open along a line 24 blocks long where you aim: a great gash of violet light that splits the
     * air, then holds open for a moment while everything along it is severed.
     * Surpassing Limits: Absolute Severance.
     */
    public static void dimensionalSeverance(ServerPlayer p, float power) {
        if (LimitBreak.active(p)) { LimitFx.absoluteSeverance(p, power); return; }
        ServerLevel sl = p.serverLevel();
        Vec3 look = flat(p.getLookAngle());
        Vec3 c = Kit.aim(p, 30).add(0, 2.5, 0);
        Vec3 along = rightOf(look);
        final float half = 12f;
        sound(sl, p.position(), SoundEvents.WARDEN_SONIC_CHARGE, 1.2f, 0.7f);
        sound(sl, c, SoundEvents.ENDERMAN_TELEPORT, 1.0f, 0.5f);
        FxSend.ring(sl, V, p.position().add(0, 0.05, 0), 0.3f, 3.5f, 0.08f, 12);
        for (int k = 0; k < 8; k++) {   // the cut races out from the middle to both ends
            final int kk = k;
            AstaFx.later(8 + k, () -> {
                if (!alive(p)) return;
                double s = (kk + 1) / 8.0 * half;
                for (int side = -1; side <= 1; side += 2) {
                    Vec3 a = c.add(along.scale(side * (s - half / 8.0))), b = c.add(along.scale(side * s));
                    FxSend.beam(sl, V, a, b, 0.4f, 20);
                    FxSend.beam(sl, S, a, b, 0.1f, 16);
                }
                FxSend.cracks(sl, V, c.add(along.scale(s * 0.6)), along, look, 2.2f, 18, true, kk * 53);
                FxSend.cracks(sl, V, c.subtract(along.scale(s * 0.6)), along, look, 2.2f, 18, true, kk * 59);
            });
        }
        AstaFx.later(17, () -> {
            if (!alive(p)) return;
            Vec3 a = c.subtract(along.scale(half)), b = c.add(along.scale(half));
            SpaceFx.gate(sl, c, look, half * 0.9f, 1.3f, 24, 1);    // the gash holds open
            boolean any = false;
            for (LivingEntity t : Kit.along(sl, p, a.subtract(0, 3, 0), b.subtract(0, 3, 0), 7.0)) {
                if (Math.abs(mid(t).y - c.y) > 9) continue;
                AstaFx.damage(p, t, power * 2.2f, "dimensional_severance");
                Kit.push(t, mid(t).subtract(c), 0.5, 0.5);
                any = true;
            }
            if (any) FxSend.impactFrame(sl, c, 4, FxSend.FRAME_FINRAL, 44);
            Fx.flash(sl, c, 3.6f);
            FxSend.shake(sl, c, 1.2f, 14, 48);
            sound(sl, c, SoundEvents.GLASS_BREAK, 1.6f, 0.4f);
            sound(sl, c, SoundEvents.GENERIC_EXPLODE, 1.1f, 0.6f);
        });
    }

    public static void clear(UUID id) {}
    public static void clearAll() {}
}
