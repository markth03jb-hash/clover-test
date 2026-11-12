package com.ironspellgrimoire.server;

import static com.ironspellgrimoire.server.BossKit.*;
import static com.ironspellgrimoire.server.FxUtil.*;

import com.ironspellgrimoire.entity.Dante;
import io.redspace.ironsspellbooks.damage.SpellDamageSource;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;

/**
 * Dante's fight: body magic and gravity.
 * <ul>
 *   <li><b>Body magic - he mends.</b> Every second after he's been hurt, his flesh knits itself back together, paid for
 *       from his body-magic mana (shown on his boss bar). Magic hits drain that mana twice as fast. Run it dry and he's
 *       <b>exhausted</b> for eight seconds: no mending, and he takes half again as much damage. Then it comes back.</li>
 *   <li><b>Phase 1 - Body Magic:</b> a flesh lance shot from his arm along a line; thorns bursting out of his body all
 *       round him; a severed hand that crawls through the air after you and bursts; and, when he's badly hurt, a full
 *       rebuild that you can stop by hitting him hard while he does it.</li>
 *   <li><b>Phase 2 - Gravity:</b> a gravity well that drags you in before it crushes; a heavy field that pins your feet
 *       (slower, falling hard - attribute modifiers, no status effects); rubble that orbits him and is flung at you; and a
 *       lift that throws you up and slams you down.</li>
 *   <li><b>Phase 3 - the devil's power:</b> everything faster, and every half-minute a Singularity: a black point over
 *       the arena pulls everyone towards it, then collapses.</li>
 * </ul>
 * Effects are drawn with the mod's own shapes; nothing changes the world.
 */
public final class DanteAI {
    private DanteAI() {}

    public static final float MANA = 100;
    private static final String WHO = "Dante";
    private static final ChatFormatting COL = ChatFormatting.DARK_RED;
    private static final int FLESH = Fx.CRIMSON, GRAV = Fx.VOID, DARKP = Fx.DARK;
    private static final UUID HEAVY_SPEED = UUID.fromString("da7e0001-0000-4000-8000-000000000001");
    private static final UUID HEAVY_GRAV = UUID.fromString("da7e0001-0000-4000-8000-000000000002");
    private static final UUID SURGE = UUID.fromString("da7e0001-0000-4000-8000-000000000003");

    // ================================================================== the tick
    public static void tick(Dante b) {
        ServerLevel sl = (ServerLevel) b.level();
        long now = sl.getGameTime();
        body(b, sl, now);
        if (b.phase() == 3 && now % 8 == 0) {   // the devil's power on him: dark streaks rising, a ring at his feet
            ThreadLocalRandom r = ThreadLocalRandom.current();
            Vec3 p0 = b.position().add(r.nextDouble(-0.5, 0.5), r.nextDouble(0.2, 1.6), r.nextDouble(-0.5, 0.5));
            BossFx.trail(sl, r.nextBoolean() ? DARKP : FLESH, p0, p0.add(0, 0.8, 0), 0.035f, 10);
            if (now % 32 == 0) BossFx.ring(sl, GRAV, b.position().add(0, 0.06, 0), 1.2f, 1.8f, 0.04f, 30);
        }
        Player t = target(b);
        if (t == null) { b.getNavigation().stop(); return; }
        if (!b.getPersistentData().getBoolean("isg_met")) {
            b.getPersistentData().putBoolean("isg_met", true);
            say(b, WHO, COL, "Cut me, burn me, break me. I'll simply put myself back together.");
        }
        b.getLookControl().setLookAt(t, 30, 30);
        if (b.phase() == 3 && now % 600 == 300 && b.busy <= 0) { b.busy = singularity(b, sl, t) + 20; return; }
        if (b.busy > 0) { b.busy--; return; }
        double dist = b.distanceTo(t);
        keep(b, t, b.phase() == 1 ? 6 : 8);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        List<Move> pool = new ArrayList<>();
        if (b.phase() == 1 || r.nextInt(3) == 0) {
            pool.add(DanteAI::fleshLance);
            if (dist < 6) { pool.add(DanteAI::thorns); pool.add(DanteAI::thorns); }
            pool.add(DanteAI::severedHand);
        }
        if (b.phase() >= 2) {
            pool.add(DanteAI::gravityWell);
            pool.add(DanteAI::heavyField);
            pool.add(DanteAI::rubble);
            pool.add(DanteAI::lift);
        }
        if (b.getHealth() < b.getMaxHealth() * (b.phase() == 1 ? 0.8f : b.phase() == 2 ? 0.45f : 0.15f) && b.mana >= 30 && b.exhausted == 0 && r.nextInt(4) == 0)
            pool = List.of(DanteAI::rebuild);
        int took = pool.get(r.nextInt(pool.size())).run(b, sl, t);
        int rest = b.phase() == 1 ? 30 : b.phase() == 2 ? 24 : 16;
        b.busy = took + rest;
    }

    private interface Move { int run(Dante b, ServerLevel sl, Player t); }

    /** Body magic: the wound closes, paid for in mana. */
    private static void body(Dante b, ServerLevel sl, long now) {
        if (b.exhausted > 0) {
            if (--b.exhausted == 0) {
                b.mana = MANA * 0.5f;
                say(b, WHO, COL, "There. Whole again.");
                BossFx.ring(sl, FLESH, b.position().add(0, 1, 0), 0.3f, 2.2f, 0.06f, 12);
            } else if (now % 10 == 0) BossFx.orb(sl, DARKP, b.position().add(0, 1.9, 0), new Vec3(0, 0.03, 0), 0.18f, 10);   // spent: breath steaming off him
        } else if (now % 20 == 0) {
            if (now - b.lastHurt < 60 && b.getHealth() < b.getMaxHealth()) {
                float heal = b.getMaxHealth() * (b.phase() == 3 ? 0.012f : 0.009f);
                b.heal(heal);
                b.mana -= 4 + b.recent * 0.04f;
                b.recent = 0;
                Vec3 c = b.position();
                for (int i = 0; i < 3; i++) {   // threads of flesh knitting over him
                    double a = now * 0.3 + i * 2.1;
                    BossFx.trail(sl, FLESH, c.add(Math.cos(a) * 0.6, 0.4 + i * 0.5, Math.sin(a) * 0.6), c.add(Math.cos(a + 1.6) * 0.5, 0.7 + i * 0.5, Math.sin(a + 1.6) * 0.5), 0.04f, 8);
                }
            } else b.mana = Math.min(MANA, b.mana + 1.5f);
            if (b.mana <= 0) {
                b.mana = 0;
                b.exhausted = 160;
                say(b, WHO, COL, "...My mana. Gone? No - not yet!");
                FxSend.impactFrame(sl, b.position().add(0, 1.2, 0), 3, FxSend.FRAME_BLOOD, 24);
                sl.playSound(null, b.blockPosition(), SoundEvents.PLAYER_BREATH, SoundSource.HOSTILE, 1.4f, 0.5f);
                for (Player p : near(b, b.position(), 40)) p.displayClientMessage(Component.literal("Dante is exhausted - he can't mend!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
            }
        }
        if (now % 10 == 0) {
            int pct = Math.round(100 * b.mana / MANA);
            String s = b.exhausted > 0 ? "  [EXHAUSTED]" : "  [body " + pct + "%]";
            String ph = b.phase() == 1 ? "Body Magic" : b.phase() == 2 ? "Gravity" : "Devil's Power";
            b.setBarName(Component.literal("Dante - " + ph + s));
        }
    }

    public static void onHurt(Dante b, DamageSource src, float amount) {
        if (src.getEntity() instanceof Player p && valid(b, p)) b.focus = p.getUUID();
        b.recent += amount * (src instanceof SpellDamageSource ? 2f : 1f);   // magic tears at his mana
        if (src instanceof SpellDamageSource && b.exhausted == 0) b.mana -= amount * 0.08f;
        b.mendCheck += Math.round(amount);
    }

    /** Exhausted, he takes half again as much. */
    public static float taken(Dante b, DamageSource src) { return b.exhausted > 0 ? 1.5f : 1f; }

    // ================================================================== phase 1: body magic
    /** His arm stretches into a lance of flesh down a line. */
    static int fleshLance(Dante b, ServerLevel sl, Player t) {
        int T = b.phase() == 3 ? 10 : 14;
        b.setPose(P_CAST);
        b.getNavigation().stop();
        Vec3 d = dirTo(b, t);
        face(b, d);
        Vec3 a = b.position().add(0, 1.3, 0), c = a.add(d.scale(16));
        warnLine(sl, a.add(0, -1.2, 0), c.add(0, -1.2, 0), 1.2f, T);
        charge(b, FLESH, T);
        AstaFx.later(T, () -> {
            if (!ok(b)) return;
            for (int k = 0; k < 8; k++) {
                final int kk = k;
                AstaFx.later(k, () -> {
                    if (!b.isAlive()) return;
                    Vec3 p0 = a.add(d.scale(2 * kk)), p1 = a.add(d.scale(2 * (kk + 1)));
                    BossFx.trail(sl, FLESH, p0, p1, 0.32f, 14 - kk);
                    BossFx.trail(sl, DARKP, p0.add(0, 0.05, 0), p1.add(0, 0.05, 0), 0.12f, 12 - kk);
                    for (Player p : along(b, p0, p1, 1.0)) if (p.invulnerableTime <= 10) hurt(b, p, 9f);
                });
            }
            sl.playSound(null, b.blockPosition(), SoundEvents.SLIME_ATTACK, SoundSource.HOSTILE, 1.4f, 0.4f);
            sl.playSound(null, b.blockPosition(), SoundEvents.TRIDENT_THROW, SoundSource.HOSTILE, 1.0f, 0.5f);
        });
        return T + 10;
    }

    /** Thorns burst out of his body in every direction. */
    static int thorns(Dante b, ServerLevel sl, Player t) {
        int T = b.phase() == 3 ? 10 : 16;
        b.setPose(P_ROAR);
        b.getNavigation().stop();
        Vec3 c = b.position();
        warnCircle(sl, c, 4.5f, T);
        charge(b, FLESH, T);
        AstaFx.later(T, () -> {
            if (!ok(b)) return;
            for (int i = 0; i < 14; i++) {
                double a = i * Math.PI * 2 / 14;
                Vec3 d = new Vec3(Math.cos(a), 0.15 + (i % 3) * 0.12, Math.sin(a)).normalize();
                Vec3 from = c.add(0, 1.0, 0);
                BossFx.trail(sl, FLESH, from, from.add(d.scale(4.2)), 0.18f, 12);
            }
            for (Player p : near(b, c.add(0, 1, 0), 4.5)) {
                hurt(b, p, 8f);
                Vec3 away = flat(p.position().subtract(c));
                if (away.lengthSqr() > 1e-4) { p.setDeltaMovement(away.normalize().scale(0.9).add(0, 0.35, 0)); p.hurtMarked = true; }
            }
            sl.playSound(null, b.blockPosition(), SoundEvents.SLIME_SQUISH, SoundSource.HOSTILE, 1.6f, 0.4f);
            FxSend.shake(sl, c, 0.4f, 6, 14);
        });
        return T + 8;
    }

    /** He tears off a hand and it crawls through the air after you, then bursts. */
    static int severedHand(Dante b, ServerLevel sl, Player t) {
        b.setPose(P_THROW);
        b.getNavigation().stop();
        sl.playSound(null, b.blockPosition(), SoundEvents.SLIME_BLOCK_BREAK, SoundSource.HOSTILE, 1.2f, 0.5f);
        final Vec3[] at = {b.position().add(0, 1.4, 0).add(dirTo(b, t).scale(0.8))};
        int life = 50;
        for (int k = 1; k <= life; k++) {
            final int kk = k;
            AstaFx.later(k, () -> {
                if (!b.isAlive() || at[0] == null) return;
                Player tt = target(b);
                Vec3 goal = tt == null ? at[0] : mid(tt);
                Vec3 d = goal.subtract(at[0]);
                double sp = 0.32 + kk * 0.004;
                Vec3 step = d.lengthSqr() > sp * sp ? d.normalize().scale(sp) : d;
                Vec3 nx = at[0].add(step);
                BossFx.trail(sl, FLESH, at[0], nx, 0.14f, 6);
                if (kk % 2 == 0) BossFx.orb(sl, FLESH, nx, Vec3.ZERO, 0.24f, 3);
                at[0] = nx;
                boolean hit = tt != null && nx.distanceTo(mid(tt)) < 1.1;
                if (hit || kk == life) {
                    BossFx.ring(sl, FLESH, nx, 0.2f, 2.4f, 0.1f, 8);
                    BossFx.orb(sl, DARKP, nx, Vec3.ZERO, 0.7f, 6);
                    for (Player p : near(b, nx, 2.4)) hurt(b, p, 7f);
                    sl.playSound(null, nx.x, nx.y, nx.z, SoundEvents.SLIME_DEATH, SoundSource.HOSTILE, 1.4f, 0.4f);
                    at[0] = null;
                }
            });
        }
        return 14;
    }

    /** Badly hurt, he rebuilds himself: a big heal over two seconds - unless you hit him hard enough to stop it. */
    static int rebuild(Dante b, ServerLevel sl, Player t) {
        int T = 40;
        b.setPose(P_RAISE);
        b.getNavigation().stop();
        b.mendCheck = 0;
        float need = b.getMaxHealth() * 0.06f;
        say(b, WHO, COL, "Hold still. This only takes a moment.");
        for (Player p : near(b, b.position(), 40)) p.displayClientMessage(Component.literal("He's rebuilding himself - hit him hard to stop it!").withStyle(ChatFormatting.RED), true);
        for (int k = 0; k < T; k += 4) {
            final int kk = k;
            AstaFx.later(k, () -> {
                if (!b.isAlive()) return;
                Vec3 c = b.position();
                float r = 2.6f * (1 - kk / (float) T) + 0.6f;
                BossFx.ring(sl, FLESH, c.add(0, 0.08 + kk * 0.04, 0), r - 0.15f, r, 0.05f, 5);
            });
        }
        AstaFx.later(T, () -> {
            if (!ok(b)) return;
            if (b.mendCheck >= need) {
                say(b, WHO, COL, "Tch - you tore it open again.");
                BossFx.ring(sl, DARKP, b.position().add(0, 1, 0), 2f, 0.2f, 0.08f, 8);
                b.mana -= 20;
                return;
            }
            b.heal(b.getMaxHealth() * 0.1f);
            b.mana -= 30;
            BossFx.ring(sl, FLESH, b.position().add(0, 1, 0), 0.3f, 3f, 0.1f, 10);
            sl.playSound(null, b.blockPosition(), SoundEvents.ZOMBIE_VILLAGER_CURE, SoundSource.HOSTILE, 1.0f, 0.6f);
        });
        return T + 6;
    }

    // ================================================================== phase 2: gravity
    /** A well opens under you and drags you to its centre before it crushes. */
    static int gravityWell(Dante b, ServerLevel sl, Player t) {
        int T = b.phase() == 3 ? 26 : 36;
        b.setPose(P_CAST);
        b.getNavigation().stop();
        Vec3 c = ground(sl, t.position());
        warnCircle(sl, c, 3.5f, T);
        sl.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_AMBIENT, SoundSource.HOSTILE, 1.6f, 0.5f);
        for (int k = 0; k < T; k += 2) {
            final int kk = k;
            AstaFx.later(k, () -> {
                if (!ok(b)) return;
                if (kk % 6 == 0) BossFx.ring(sl, GRAV, c.add(0, 0.1, 0), 6f - kk * 0.12f, 6.2f - kk * 0.12f, 0.06f, 6);
                for (Player p : near(b, c.add(0, 1, 0), 9)) {   // dragged in
                    Vec3 in = flat(c.subtract(p.position()));
                    if (in.lengthSqr() < 0.04) continue;
                    p.setDeltaMovement(p.getDeltaMovement().add(in.normalize().scale(0.09)));
                    p.hurtMarked = true;
                }
            });
        }
        AstaFx.later(T, () -> {
            if (!ok(b)) return;
            BossFx.orb(sl, GRAV, c.add(0, 1, 0), Vec3.ZERO, 1.6f, 8);
            BossFx.ring(sl, DARKP, c.add(0, 0.08, 0), 0.4f, 3.5f, 0.2f, 10);
            FxSend.shake(sl, c, 0.5f, 8, 16);
            for (Player p : near(b, c.add(0, 1, 0), 3.5)) { hurt(b, p, 10f); p.setDeltaMovement(0, -1, 0); p.hurtMarked = true; }
            sl.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.2f, 0.5f);
        });
        return T + 8;
    }

    /** The air round him turns heavy: within the field you're slower and fall hard. */
    static int heavyField(Dante b, ServerLevel sl, Player t) {
        int T = 14;
        b.setPose(P_RAISE);
        b.getNavigation().stop();
        Vec3 c = b.position();
        float R = 7f;
        warnCircle(sl, c, R, T);
        charge(b, GRAV, T);
        AstaFx.later(T, () -> {
            if (!ok(b)) return;
            BossFx.ring(sl, GRAV, c.add(0, 0.06, 0), R - 0.3f, R, 0.05f, 80);
            BossFx.wall(sl, GRAV, c.add(0, 0.05, 0), new Vec3(1, 0, 0), new Vec3(0, 1, 0), R, R, -180, 180, 0.6f, 60, 0, 0);
            sl.playSound(null, c.x, c.y, c.z, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.HOSTILE, 1.4f, 0.5f);
            for (int k = 0; k < 80; k += 10) {
                AstaFx.later(k, () -> {
                    if (!b.isAlive()) return;
                    for (Player p : near(b, c.add(0, 1, 0), R)) {
                        TimedModifiers.apply(p, Attributes.MOVEMENT_SPEED, HEAVY_SPEED, "Dante: heavy", -0.45, AttributeModifier.Operation.MULTIPLY_TOTAL, 14);
                        TimedModifiers.apply(p, ForgeMod.ENTITY_GRAVITY.get(), HEAVY_GRAV, "Dante: heavy", 1.0, AttributeModifier.Operation.MULTIPLY_TOTAL, 14);
                        p.setDeltaMovement(p.getDeltaMovement().multiply(1, 0.6, 1).add(0, -0.08, 0));
                        p.hurtMarked = true;
                    }
                });
            }
        });
        return T + 10;
    }

    /** Rubble rises and circles him, then is flung at you, piece by piece. */
    static int rubble(Dante b, ServerLevel sl, Player t) {
        int N = b.phase() == 3 ? 7 : 5, T = 24;
        b.setPose(P_SPIN);
        b.getNavigation().stop();
        sl.playSound(null, b.blockPosition(), SoundEvents.GRAVEL_BREAK, SoundSource.HOSTILE, 1.4f, 0.5f);
        for (int k = 0; k < T; k += 2) {
            final int kk = k;
            AstaFx.later(k, () -> {
                if (!ok(b)) return;
                for (int i = 0; i < N; i++) {
                    double a = kk * 0.25 + i * Math.PI * 2 / N;
                    Vec3 at = b.position().add(Math.cos(a) * 2.4, 1.2 + Math.sin(kk * 0.3 + i) * 0.3 + kk * 0.03, Math.sin(a) * 2.4);
                    FxSend.box(sl, Fx.STEEL | com.ironspellgrimoire.network.FxPacket.KEEP_SHAPE, at, new Vec3(Math.cos(a), 0, Math.sin(a)), new Vec3(0, 1, 0), 0.3f, 0.25f, 0.25f, 3, 0, com.ironspellgrimoire.network.FxPacket.HOLD);
                }
            });
        }
        for (int i = 0; i < N; i++) {
            final int ii = i;
            AstaFx.later(T + i * 4, () -> {
                if (!ok(b)) return;
                Player tt = target(b);
                if (tt == null) return;
                double a = T * 0.25 + ii * Math.PI * 2 / N;
                Vec3 from = b.position().add(Math.cos(a) * 2.4, 1.9, Math.sin(a) * 2.4);
                Vec3 d = mid(tt).add(tt.getDeltaMovement().multiply(5, 0, 5)).subtract(from).normalize();
                warnLine(sl, from, from.add(d.scale(16)), 0.05f, 4);
                AstaFx.later(4, () -> {
                    if (!b.isAlive()) return;
                    for (int k = 0; k < 8; k++) {
                        final int kk = k;
                        AstaFx.later(k, () -> {
                            Vec3 p0 = from.add(d.scale(2 * kk)), p1 = from.add(d.scale(2 * (kk + 1)));
                            FxSend.box(sl, Fx.STEEL | com.ironspellgrimoire.network.FxPacket.KEEP_SHAPE, p1, d, new Vec3(0, 1, 0), 0.32f, 0.26f, 0.26f, 2, 0, com.ironspellgrimoire.network.FxPacket.HOLD);
                            BossFx.trail(sl, GRAV, p0, p1, 0.08f, 6);
                            for (Player p : along(b, p0, p1, 0.9)) if (p.invulnerableTime <= 10) hurt(b, p, 5f);
                        });
                    }
                    sl.playSound(null, from.x, from.y, from.z, SoundEvents.SNOWBALL_THROW, SoundSource.HOSTILE, 1.2f, 0.4f);
                });
            });
        }
        return T + N * 4 + 10;
    }

    /** Everyone near you is thrown up - then gravity turns round and slams you down. */
    static int lift(Dante b, ServerLevel sl, Player t) {
        int T = 16;
        b.setPose(P_RAISE);
        b.getNavigation().stop();
        Vec3 c = ground(sl, t.position());
        warnCircle(sl, c, 3f, T);
        charge(b, GRAV, T);
        AstaFx.later(T, () -> {
            if (!ok(b)) return;
            List<Player> up = near(b, c.add(0, 1, 0), 3);
            for (Player p : up) { p.setDeltaMovement(0, 1.25, 0); p.hurtMarked = true; }
            BossFx.ring(sl, GRAV, c.add(0, 0.1, 0), 0.4f, 3f, 0.4f, 10);
            sl.playSound(null, c.x, c.y, c.z, SoundEvents.SHULKER_SHOOT, SoundSource.HOSTILE, 1.2f, 0.5f);
            AstaFx.later(14, () -> {
                for (Player p : up) {
                    if (!p.isAlive()) continue;
                    p.setDeltaMovement(0, -2.4, 0); p.hurtMarked = true;
                    BossFx.trail(sl, GRAV, mid(p), mid(p).add(0, 3, 0), 0.2f, 8);
                }
                AstaFx.later(8, () -> {
                    for (Player p : up) if (p.isAlive()) { hurt(b, p, 9f); BossFx.ring(sl, DARKP, p.position().add(0, 0.08, 0), 0.3f, 2.2f, 0.1f, 8); }
                    FxSend.shake(sl, c, 0.5f, 6, 14);
                });
            });
        });
        return T + 26;
    }

    // ================================================================== phase 3
    /** A black point opens over the arena and pulls everyone towards it - then collapses. Run. */
    static int singularity(Dante b, ServerLevel sl, Player t) {
        int T = 80;
        b.setPose(P_RAISE);
        b.getNavigation().stop();
        Vec3 c = (b.home != null ? b.home : b.position()).add(0, 3, 0);
        say(b, WHO, COL, "Everything falls. In the end, everything falls to me.");
        FxSend.impactFrame(sl, b.position().add(0, 1.5, 0), 4, FxSend.FRAME_BLOOD, 30);
        warnCircle(sl, c.subtract(0, 3, 0), 5f, T);
        for (int k = 0; k < T; k += 2) {
            final int kk = k;
            AstaFx.later(k, () -> {
                if (!ok(b)) return;
                BossFx.orb(sl, DARKP, c, Vec3.ZERO, 0.6f + kk * 0.012f, 3);
                if (kk % 6 == 0) BossFx.ring(sl, GRAV, c, 7f - kk * 0.07f, 7.3f - kk * 0.07f, 0.05f, 6);
                if (kk % 4 == 0) {   // the air streaming in
                    double a = kk * 0.7;
                    Vec3 e = c.add(Math.cos(a) * 9, -2, Math.sin(a) * 9);
                    BossFx.trail(sl, GRAV, e, c, 0.05f, 6);
                }
                for (Player p : near(b, c, 26)) {
                    Vec3 in = c.subtract(mid(p));
                    double d = in.length();
                    if (d < 0.5) continue;
                    p.setDeltaMovement(p.getDeltaMovement().add(flat(in).normalize().scale(0.05 + 0.25 / Math.max(2, d))));
                    p.hurtMarked = true;
                }
                if (kk % 20 == 0) sl.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 1.4f, 0.4f);
            });
        }
        AstaFx.later(T, () -> {
            if (!ok(b)) return;
            BossFx.orb(sl, DARKP, c, Vec3.ZERO, 3f, 10);
            BossFx.ring(sl, GRAV, c.subtract(0, 2.9, 0), 0.5f, 6f, 0.4f, 14);
            FxSend.impactFrame(sl, c, 4, FxSend.FRAME_BLOOD, 36);
            FxSend.shake(sl, c, 1.0f, 14, 30);
            for (Player p : near(b, c.subtract(0, 2, 0), 5)) hurt(b, p, 14f);
            sl.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 2f, 0.4f);
        });
        return T + 10;
    }

    // ================================================================== phases and death
    public static void phase(Dante b, int phase) {
        ServerLevel sl = (ServerLevel) b.level();
        b.setTransforming(50);
        b.setPose(P_ROAR);
        Vec3 c = b.position();
        if (phase == 2) {
            say(b, WHO, COL, "You've seen what my body can do. Now feel how heavy the world can be.");
            for (int k = 0; k < 10; k++) {
                final int kk = k;
                AstaFx.later(k * 4, () -> BossFx.ring(sl, GRAV, c.add(0, 0.1 + kk * 0.2, 0), 6f - kk * 0.5f, 6.2f - kk * 0.5f, 0.06f, 8));
            }
            AstaFx.later(44, () -> { FxSend.shake(sl, c, 0.7f, 10, 24); sl.playSound(null, c.x, c.y, c.z, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.HOSTILE, 1.6f, 0.5f); });
        } else {
            say(b, WHO, COL, "Enough holding back. Look upon what I borrowed from below.");
            b.mana = MANA;
            TimedModifiers.apply(b, Attributes.MOVEMENT_SPEED, SURGE, "Devil's power", 0.25, AttributeModifier.Operation.MULTIPLY_TOTAL, 20 * 600);
            for (int k = 0; k < 12; k++) {
                final int kk = k;
                AstaFx.later(k * 4, () -> {   // darkness pouring into him from the edge of the arena
                    double a = kk * Math.PI / 6;
                    Vec3 e = c.add(Math.cos(a) * 12, 0.2, Math.sin(a) * 12);
                    BossFx.trail(sl, DARKP, e, c.add(0, 1.2, 0), 0.25f, 10);
                    BossFx.trail(sl, FLESH, e, c.add(0, 1.2, 0), 0.06f, 8);
                    sl.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 1.4f, 0.6f + kk * 0.03f);
                });
            }
            AstaFx.later(46, () -> {
                FxSend.impactFrame(sl, c.add(0, 1.5, 0), 5, FxSend.FRAME_BLOOD, 34);
                BossFx.ring(sl, DARKP, c.add(0, 0.1, 0), 0.5f, 11f, 0.35f, 16);
                FxSend.shake(sl, c, 1.0f, 14, 36);
                sl.playSound(null, c.x, c.y, c.z, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1.2f, 0.5f);
            });
        }
    }

    public static void dying(Dante b, int t) {
        ServerLevel sl = (ServerLevel) b.level();
        if (t == 1) {
            say(b, WHO, COL, "It won't close... why won't it close...");
            sl.playSound(null, b.blockPosition(), SoundEvents.WITHER_DEATH, SoundSource.HOSTILE, 0.8f, 1.2f);
        }
        if (t < 60 && t % 5 == 0) {   // his body tries to mend, and can't
            Vec3 c = b.position();
            double a = t * 0.5;
            BossFx.trail(sl, FLESH, c.add(Math.cos(a) * 0.6, 0.6, Math.sin(a) * 0.6), c.add(Math.cos(a) * 0.9, 1.4, Math.sin(a) * 0.9), 0.04f, 8);
            BossFx.orb(sl, DARKP, c.add(0, 1 + ThreadLocalRandom.current().nextDouble(), 0), new Vec3(0, 0.04, 0), 0.25f, 12);
        }
        if (t == 60) BossFx.ring(sl, DARKP, b.position().add(0, 0.1, 0), 0.3f, 4f, 0.1f, 14);
    }
}
