package com.ironspellgrimoire.server;

import static com.ironspellgrimoire.server.FxUtil.*;

import com.ironspellgrimoire.IronSpellGrimoire;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Gauche Adlai's Mirror Magic - the eight spells of the Mirror Grimoire (silver panes edged in rose, shards of light).
 *
 *  1 Mirror Shard      - three shards of mirror fly out in a fan
 *  2 Mirror Step       - step into a mirror and out of another 10 blocks ahead
 *  3 Reflect Guard     - for 8 s the next four blows on you meet a mirror: they never land and 60% is thrown back
 *  4 Prism Beam        - a beam of light that bounces off mirrors from one enemy to the next
 *  5 Mirror Cage       - six mirrors close round an enemy: it can barely move and every mirror cuts it
 *  6 Mirror Barrage    - mirrors circle you and fire volley after volley of shards at everything near
 *  7 Mirror Double     - slip into a mirror and come out behind the enemy you aim at, cutting as you arrive
 *  8 Hall of Mirrors   - a dome of mirrors closes round the enemies: reflections of light strike them from every pane
 */
@Mod.EventBusSubscriber(modid = IronSpellGrimoire.MODID)
public final class MirrorFx {
    private MirrorFx() {}
    private static final int M = Fx.MIRROR, S = Fx.SILVER;
    private static final UUID CAGE = UUID.fromString("c41d2a10-5b7e-4f21-8a90-1f3b6d700021");
    private static final Map<UUID, int[]> GUARD = new HashMap<>();           // player -> {charges}
    private static final Map<UUID, Long> GUARD_UNTIL = new HashMap<>();

    // ------------------------------------------------------------------ shared
    /** A mirror pane: a rectangle edged in silver with one bright glint across it. */
    static void pane(ServerLevel sl, Vec3 c, Vec3 rt, Vec3 up, float hw, float hh, int life) {
        Vec3 a = c.add(rt.scale(-hw)).add(up.scale(-hh)), b = c.add(rt.scale(hw)).add(up.scale(-hh));
        Vec3 d = c.add(rt.scale(-hw)).add(up.scale(hh)), e = c.add(rt.scale(hw)).add(up.scale(hh));
        FxSend.trail(sl, M, a, b, 0.05f, life); FxSend.trail(sl, M, b, e, 0.05f, life);
        FxSend.trail(sl, M, e, d, 0.05f, life); FxSend.trail(sl, M, d, a, 0.05f, life);
        FxSend.beam(sl, S, a.add(rt.scale(hw * 0.4)), e.subtract(rt.scale(hw * 0.4)), 0.035f, Math.max(2, life / 2));
    }

    /** A pane standing at {@code c} facing {@code n}. */
    static void paneFacing(ServerLevel sl, Vec3 c, Vec3 n, float hw, float hh, int life) {
        Vec3 rt = rightOf(n);
        pane(sl, c, rt, rt.cross(n).normalize(), hw, hh, life);
    }

    private static void shard(ServerPlayer p, ServerLevel sl, Vec3 from, Vec3 to, float dmg, String id, boolean flash) {
        FxSend.beam(sl, M, from, to, 0.1f, 4);
        FxSend.beam(sl, S, from, to, 0.035f, 3);
        for (LivingEntity t : Kit.along(sl, p, from, to, 0.6)) {
            AstaFx.damage(p, t, dmg, id);
            Fx.spark(sl, M, mid(t), 0.9f);
        }
        if (flash) Fx.flash(sl, to, 0.5f);
    }

    // ================================================================== 1. Mirror Shard
    public static void mirrorShard(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        sound(sl, p.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 0.9f, 1.8f);
        AstaFx.later(6, () -> {
            if (!alive(p)) return;
            Vec3 from = Kit.hand(p), look = p.getLookAngle();
            paneFacing(sl, from.add(look.scale(0.9)), look, 0.7f, 0.7f, 6);
            for (int i = -1; i <= 1; i++) {
                Vec3 d = rotY(look, i * 0.13);
                Vec3 end = clip(sl, p, from, from.add(d.scale(20)));
                shard(p, sl, from, end, power * 0.5f, "mirror_shard", true);
            }
            sound(sl, from, SoundEvents.GLASS_BREAK, 0.5f, 1.8f);
        });
    }

    // ================================================================== 2. Mirror Step
    public static void mirrorStep(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        Vec3 look = flat(p.getLookAngle());
        Vec3 here = p.position();
        Vec3 want = here.add(look.scale(10));
        Vec3 dest = null;
        for (double d = 10; d >= 3 && dest == null; d -= 1) {
            Vec3 q = here.add(look.scale(d));
            if (sl.noCollision(p, p.getBoundingBox().move(q.subtract(here)))) dest = q;
        }
        if (dest == null) return;
        paneFacing(sl, here.add(0, 1, 0).add(look.scale(0.7)), look, 0.8f, 1.1f, 8);
        paneFacing(sl, dest.add(0, 1, 0).subtract(look.scale(0.7)), look, 0.8f, 1.1f, 8);
        FxSend.trail(sl, M, here.add(0, 1, 0), dest.add(0, 1, 0), 0.06f, 5);
        Fx.flash(sl, here.add(0, 1, 0), 1.0f);
        final Vec3 to = dest;
        sound(sl, here, SoundEvents.GLASS_BREAK, 0.6f, 1.6f);
        AstaFx.later(2, () -> {
            if (!alive(p)) return;
            SpaceFx.move(p, to, p.getYRot());
            Fx.flash(sl, to.add(0, 1, 0), 1.0f);
            sound(sl, to, SoundEvents.AMETHYST_BLOCK_CHIME, 0.9f, 1.4f);
        });
    }

    // ================================================================== 3. Reflect Guard
    public static void reflectGuard(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        GUARD.put(p.getUUID(), new int[]{4});
        GUARD_UNTIL.put(p.getUUID(), sl.getGameTime() + 160);
        ModeHud.show(p, "reflect_guard", 160, 4);
        Vec3 look = p.getLookAngle();
        paneFacing(sl, p.position().add(0, 1.0, 0).add(flat(look).scale(1.1)), flat(look), 1.2f, 1.5f, 14);
        FxSend.ring(sl, M, p.position().add(0, 0.05, 0), 0.3f, 2.4f, 0.06f, 8);
        sound(sl, p.position(), SoundEvents.AMETHYST_CLUSTER_PLACE, 1.0f, 1.2f);
    }

    @SubscribeEvent
    public static void onAttacked(LivingAttackEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        int[] g = GUARD.get(p.getUUID());
        if (g == null || e.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY) || e.getSource().is(DamageTypeTags.IS_FALL)) return;
        ServerLevel sl = p.serverLevel();
        Long until = GUARD_UNTIL.get(p.getUUID());
        if (until == null || sl.getGameTime() > until || g[0] <= 0) return;
        e.setCanceled(true);
        g[0]--;
        ModeHud.show(p, "reflect_guard", (int) Math.max(1, until - sl.getGameTime()), g[0]);
        Vec3 from = e.getSource().getEntity() instanceof LivingEntity le ? mid(le) : p.position().add(0, 1, 0).add(p.getLookAngle());
        Vec3 dir = from.subtract(p.position().add(0, 1, 0));
        Vec3 n = dir.lengthSqr() < 1.0E-4 ? p.getLookAngle() : dir.normalize();
        paneFacing(sl, p.position().add(0, 1.1, 0).add(n.scale(0.9)), n, 0.9f, 1.2f, 6);
        Fx.impact(sl, M, p.position().add(0, 1.1, 0).add(n.scale(0.9)), 1.2f);
        sound(sl, p.position(), SoundEvents.GLASS_BREAK, 0.8f, 1.5f);
        if (e.getSource().getEntity() instanceof LivingEntity src && src != p) {   // thrown back
            FxSend.beam(sl, M, p.position().add(0, 1.1, 0), mid(src), 0.1f, 4);
            AstaFx.damage(p, src, Math.max(2f, e.getAmount() * 0.6f), "reflect_guard");
        }
    }

    // ================================================================== 4. Prism Beam
    public static void prismBeam(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        AstaFx.later(8, () -> {
            if (!alive(p)) return;
            Vec3 from = Kit.hand(p);
            LivingEntity first = aimed(p, 28, 18);
            Vec3 end = first != null ? mid(first) : clip(sl, p, from, from.add(p.getLookAngle().scale(26)));
            List<LivingEntity> done = new ArrayList<>();
            Vec3 at = from;
            LivingEntity cur = first;
            Vec3 point = end;
            for (int hop = 0; hop < 4; hop++) {
                final Vec3 a = at, b = point;
                final LivingEntity tgt = cur;
                AstaFx.later(hop * 3, () -> {
                    FxSend.beam(sl, M, a, b, 0.22f, 5);
                    FxSend.beam(sl, S, a, b, 0.07f, 4);
                    if (tgt != null && tgt.isAlive()) { AstaFx.damage(p, tgt, power * 0.9f, "prism_beam"); Fx.impact(sl, M, b, 1.1f); }
                    else Fx.flash(sl, b, 0.8f);
                    // a pane where the light bounces
                    Vec3 d = b.subtract(a);
                    if (d.lengthSqr() > 1.0E-4) paneFacing(sl, b, d.normalize().scale(-1), 0.6f, 0.6f, 6);
                    sound(sl, b, SoundEvents.AMETHYST_BLOCK_CHIME, 0.8f, 1.2f + 0.2f * (float) Math.random());
                });
                if (cur != null) done.add(cur);
                final Vec3 pt = point;
                LivingEntity next = null;
                double best = 14;
                for (LivingEntity t : Kit.near(sl, p, pt, 14)) {
                    if (done.contains(t)) continue;
                    double d = mid(t).distanceTo(pt);
                    if (d < best) { best = d; next = t; }
                }
                if (next == null) break;
                at = point; point = mid(next); cur = next;
            }
        });
    }

    // ================================================================== 5. Mirror Cage
    public static void mirrorCage(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        LivingEntity t = aimed(p, 26, 15);
        if (t == null) return;
        Vec3 c = mid(t);
        final int T = 70;
        TimedModifiers.apply(t, Attributes.MOVEMENT_SPEED, CAGE, "Mirror Cage", -0.9, AttributeModifier.Operation.MULTIPLY_TOTAL, T);
        sound(sl, c, SoundEvents.AMETHYST_CLUSTER_BREAK, 1.2f, 0.8f);
        for (int i = 0; i < 6; i++) {   // six panes slide in from outside and close round it
            final int ii = i;
            double a = i * Math.PI / 3;
            Vec3 n = new Vec3(Math.cos(a), 0, Math.sin(a));
            for (int s = 0; s < 4; s++) {
                final int ss = s;
                AstaFx.later(s * 2, () -> paneFacing(sl, c.add(n.scale(3.0 - ss * 0.7)), n.scale(-1), 0.9f, Math.max(1.0f, t.getBbHeight() * 0.7f), 4));
            }
            AstaFx.later(8, () -> paneFacing(sl, c.add(n.scale(0.9 + t.getBbWidth() * 0.5)), n.scale(-1), 0.9f, Math.max(1.0f, t.getBbHeight() * 0.7f), T - 8));
        }
        for (int k = 0; k < 6; k++) {   // every mirror in turn cuts it
            final int kk = k;
            AstaFx.later(14 + k * 9, () -> {
                if (!alive(p) || !t.isAlive()) return;
                double a = kk * Math.PI / 3;
                Vec3 from = mid(t).add(Math.cos(a) * (0.9 + t.getBbWidth() * 0.5), 0, Math.sin(a) * (0.9 + t.getBbWidth() * 0.5));
                FxSend.beam(sl, M, from, mid(t), 0.12f, 4);
                AstaFx.damage(p, t, power * 0.28f, "mirror_cage");
                Fx.spark(sl, M, mid(t), 0.9f);
                sound(sl, mid(t), SoundEvents.GLASS_BREAK, 0.5f, 1.8f);
            });
        }
        AstaFx.later(T, () -> {
            Fx.impact(sl, M, mid(t), 1.6f);
            FxSend.ring(sl, M, c, 0.2f, 3.0f, 0.06f, 8);
            sound(sl, c, SoundEvents.GLASS_BREAK, 1.2f, 0.9f);
        });
    }

    // ================================================================== 6. Mirror Barrage
    public static void mirrorBarrage(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        sound(sl, p.position(), SoundEvents.AMETHYST_CLUSTER_PLACE, 1.0f, 1.0f);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int v = 0; v < 8; v++) {
            final int vv = v;
            AstaFx.later(6 + v * 5, () -> {
                if (!alive(p)) return;
                double spin = vv * 0.5;
                List<LivingEntity> foes = new ArrayList<>(Kit.near(sl, p, p.position().add(0, 1, 0), 16));
                for (int m = 0; m < 6; m++) {
                    double a = spin + m * Math.PI / 3;
                    Vec3 pos = p.position().add(0, 1.4 + Math.sin(a * 2) * 0.3, 0).add(Math.cos(a) * 2.0, 0, Math.sin(a) * 2.0);
                    Vec3 out = new Vec3(Math.cos(a), 0, Math.sin(a));
                    if (vv == 0 || vv % 3 == 0) paneFacing(sl, pos, out, 0.4f, 0.55f, 6);
                    if (foes.isEmpty()) continue;
                    if (m % 2 != vv % 2) continue;   // half the mirrors fire each volley
                    LivingEntity t = foes.get(r.nextInt(foes.size()));
                    FxSend.beam(sl, M, pos, mid(t), 0.07f, 3);
                    AstaFx.damage(p, t, power * 0.22f, "mirror_barrage");
                    Fx.spark(sl, M, mid(t), 0.7f);
                }
                sound(sl, p.position(), SoundEvents.AMETHYST_BLOCK_HIT, 0.4f, 1.6f);
            });
        }
    }

    // ================================================================== 7. Mirror Double
    public static void mirrorDouble(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        LivingEntity t = aimed(p, 22, 18);
        if (t == null) return;
        Vec3 behind = t.position().subtract(flat(t.getLookAngle()).scale(1.4));
        Vec3 dest = SpaceFx.safe(sl, behind, p);
        if (dest == null) return;
        Vec3 look = flat(mid(t).subtract(p.position()));
        paneFacing(sl, p.position().add(0, 1, 0).add(look.scale(0.7)), look, 0.8f, 1.2f, 8);
        Fx.flash(sl, p.position().add(0, 1, 0), 1.0f);
        sound(sl, p.position(), SoundEvents.GLASS_BREAK, 0.7f, 1.5f);
        AstaFx.later(4, () -> {
            if (!alive(p) || !t.isAlive()) return;
            Vec3 face = mid(t).subtract(dest);
            float yaw = (float) (Math.toDegrees(Math.atan2(-face.x, face.z)));
            SpaceFx.move(p, dest, yaw);
            paneFacing(sl, dest.add(0, 1, 0).subtract(flat(face).scale(0.6)), flat(face), 0.8f, 1.2f, 8);
            Vec3 d = flat(face);
            FxLib.crossSlash(sl, M, mid(t), d, 1.6f);
            AstaFx.damage(p, t, power * 1.3f, "mirror_double");
            Fx.impact(sl, M, mid(t), 1.8f);
            FxSend.impactFrame(sl, mid(t), 3, FxSend.FRAME_KLAUS, 24);
            sound(sl, dest, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, 1.4f);
        });
    }

    // ================================================================== 8. Hall of Mirrors
    /**
     * A dome of mirrors closes round the enemies near the spot you aim at (8 blocks): for 3 s light strikes them from
     * every pane, then the whole dome shatters inward.
     * Surpassing Limits: Infinite Reflection.
     */
    public static void hallOfMirrors(ServerPlayer p, float power) {
        if (LimitBreak.active(p)) { LimitFx.infiniteReflection(p, power); return; }
        dome(p, power, 8.0, 60, 0.3f, 1.6f);
    }

    static void dome(ServerPlayer p, float power, double R, int T, float tickDmg, float finalDmg) {
        ServerLevel sl = p.serverLevel();
        Vec3 c = Kit.aim(p, 30);
        LivingEntity main = aimed(p, 30, 20);
        if (main != null) c = mid(main);
        final Vec3 centre = c;
        sound(sl, c, SoundEvents.AMETHYST_CLUSTER_BREAK, 1.4f, 0.6f);
        sound(sl, c, SoundEvents.WARDEN_SONIC_CHARGE, 0.7f, 1.2f);
        FxSend.ring(sl, M, c.add(0, -0.9, 0), (float) R, 0.4f, 0.1f, 12);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        List<Vec3> panes = new ArrayList<>();
        for (int i = 0; i < 14; i++) {   // the dome: panes on a sphere, all facing the centre
            double yaw = i * 2.399963, pitch = Math.asin(-0.2 + 1.1 * ((i * 0.618) % 1.0));
            Vec3 n = new Vec3(Math.cos(pitch) * Math.cos(yaw), Math.sin(pitch), Math.cos(pitch) * Math.sin(yaw));
            panes.add(c.add(n.scale(R)));
        }
        for (int i = 0; i < panes.size(); i++) {
            final Vec3 at = panes.get(i);
            AstaFx.later(i / 2, () -> paneFacing(sl, at, centre.subtract(at).normalize(), 1.6f, 2.0f, T));
        }
        for (int k = 0; k < T; k += 5) {
            final int kk = k;
            AstaFx.later(8 + k, () -> {
                if (!alive(p)) return;
                List<LivingEntity> in = Kit.near(sl, p, centre, R);
                for (int s = 0; s < 3; s++) {
                    Vec3 from = panes.get(r.nextInt(panes.size()));
                    if (in.isEmpty()) { FxSend.beam(sl, M, from, centre, 0.08f, 3); continue; }
                    LivingEntity t = in.get(r.nextInt(in.size()));
                    FxSend.beam(sl, M, from, mid(t), 0.14f, 4);
                    FxSend.beam(sl, S, from, mid(t), 0.05f, 3);
                    AstaFx.damage(p, t, power * tickDmg, "hall_of_mirrors");
                    Fx.spark(sl, M, mid(t), 0.9f);
                }
                if (kk % 10 == 0) sound(sl, centre, SoundEvents.AMETHYST_BLOCK_CHIME, 0.7f, 1.0f + r.nextFloat() * 0.6f);
            });
        }
        AstaFx.later(8 + T, () -> {   // it shatters inward
            if (!alive(p)) return;
            boolean any = false;
            for (LivingEntity t : Kit.near(sl, p, centre, R)) {
                AstaFx.damage(p, t, power * finalDmg, "hall_of_mirrors");
                Kit.push(t, centre.subtract(mid(t)), -0.2, 0.4);
                any = true;
            }
            if (any) FxSend.impactFrame(sl, centre, 4, FxSend.FRAME_KLAUS, 40);
            Fx.flash(sl, centre, 3.0f);
            for (Vec3 at : panes) FxSend.trail(sl, M, at, centre, 0.12f, 5);
            FxSend.ring(sl, M, centre, 0.3f, (float) R * 1.2f, 0.2f, 10);
            FxSend.shake(sl, centre, 1.0f, 12, 40);
            sound(sl, centre, SoundEvents.GLASS_BREAK, 1.6f, 0.6f);
            sound(sl, centre, SoundEvents.GENERIC_EXPLODE, 0.9f, 0.9f);
        });
    }

    // ================================================================== events
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        Long until = GUARD_UNTIL.get(p.getUUID());
        if (until == null) return;
        int[] g = GUARD.get(p.getUUID());
        if (p.serverLevel().getGameTime() > until || g == null || g[0] <= 0) {
            GUARD.remove(p.getUUID()); GUARD_UNTIL.remove(p.getUUID());
            ModeHud.hide(p, "reflect_guard");
        }
    }

    @SubscribeEvent
    public static void onLogout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e) { clear(e.getEntity().getUUID()); }

    public static void clear(UUID id) { GUARD.remove(id); GUARD_UNTIL.remove(id); }
    public static void clearAll() { GUARD.clear(); GUARD_UNTIL.clear(); }
}
