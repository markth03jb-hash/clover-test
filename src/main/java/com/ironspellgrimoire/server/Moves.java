package com.ironspellgrimoire.server;

import static com.ironspellgrimoire.server.FxUtil.*;

import com.ironspellgrimoire.IronSpellGrimoire;
import com.ironspellgrimoire.entity.MagicBolt;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The shared toolkit of the Flame Spirit, Lion's Flame, World Tree, Slash, Earth, Beast and Cotton grimoires: damage with
 * each book's passive, projectiles, lances, dashes, novas, falling strikes and timed modes. Everything is custom-drawn
 * (no particles, no potion effects) and nothing touches the world.
 *
 * Passives (applied in {@link #hit}):
 *  Flame Spirit - Scorch: the hit burns for three more pulses
 *  Lion's Flame - Fury: every fifth hit lands 60% harder
 *  World Tree   - Bloom: each hit mends the caster a little
 *  Slash        - Bleed: the cut keeps bleeding; the nearly dead take 30% more
 *  Earth        - Weighed: what stone hits moves a fifth slower
 *  Beast        - Predator: the wounded (under half health) take 25% more
 *  Cotton       - Sweet Tooth: each hit feeds the caster, and sticks the target in cotton
 */
@Mod.EventBusSubscriber(modid = IronSpellGrimoire.MODID)
public final class Moves {
    private Moves() {}

    public static final int FUEGO = 22, LION = 23, TREE = 24, RIPPER = 25, EARTH = 26, BEAST = 27, COTTON = 28;
    private static final int S = Fx.SILVER;

    private static final UUID SLOW = UUID.fromString("c41d2a10-5b7e-4f21-8a90-1f3b6d700021");
    private static final UUID ARMOR = UUID.fromString("c41d2a10-5b7e-4f21-8a90-1f3b6d700022");
    private static final UUID TOUGH = UUID.fromString("c41d2a10-5b7e-4f21-8a90-1f3b6d700023");
    private static final UUID SPEED = UUID.fromString("c41d2a10-5b7e-4f21-8a90-1f3b6d700024");
    private static final UUID KB = UUID.fromString("c41d2a10-5b7e-4f21-8a90-1f3b6d700025");
    private static final UUID ASPEED = UUID.fromString("c41d2a10-5b7e-4f21-8a90-1f3b6d700026");
    private static final UUID DAMAGE = UUID.fromString("c41d2a10-5b7e-4f21-8a90-1f3b6d700027");

    private static final Map<UUID, Integer> FURY = new HashMap<>();
    private static final Map<UUID, Long> HEALED = new HashMap<>();
    private static final Set<UUID> BURNING = new HashSet<>();

    // ================================================================== damage with the book's passive
    static void hit(ServerPlayer p, LivingEntity t, float amt, String id, int pal) {
        ServerLevel sl = p.serverLevel();
        long now = sl.getGameTime();
        switch (pal) {
            case LION -> {
                int c = FURY.merge(p.getUUID(), 1, Integer::sum);
                if (c >= 5) {
                    FURY.put(p.getUUID(), 0);
                    amt *= 1.6f;
                    FxSend.ring(sl, LION, mid(t), 0.3f, 2.6f, 0.1f, 7);
                    Fx.flash(sl, mid(t), 1.6f);
                }
            }
            case BEAST -> { if (t.getHealth() < t.getMaxHealth() * 0.5f) amt *= 1.25f; }
            case RIPPER -> { if (t.getHealth() < t.getMaxHealth() * 0.3f) amt *= 1.3f; }
            default -> {}
        }
        AstaFx.damage(p, t, amt, id);
        switch (pal) {
            case FUEGO -> dot(p, t, FUEGO, amt * 0.12f, 3, 12, id);
            case RIPPER -> dot(p, t, RIPPER, amt * 0.1f, 3, 14, id);
            case EARTH -> slow(t, 40, -0.2);
            case TREE -> { Long last = HEALED.get(p.getUUID()); if (last == null || now - last >= 6) { HEALED.put(p.getUUID(), now); p.heal(0.8f); } }
            case COTTON -> {
                Long last = HEALED.get(p.getUUID());
                if (last == null || now - last >= 10) { HEALED.put(p.getUUID(), now); p.getFoodData().eat(1, 0.2f); p.heal(0.4f); }
                slow(t, 50, -0.15);
            }
            default -> {}
        }
    }

    /** A slow, as a plain attribute modifier (no potion effect). */
    static void slow(LivingEntity t, int ticks, double amount) {
        TimedModifiers.apply(t, Attributes.MOVEMENT_SPEED, SLOW, "Grimoire slow", amount, AttributeModifier.Operation.MULTIPLY_TOTAL, ticks);
    }

    /** Damage over time: {@code pulses} small hits {@code gap} ticks apart, each marked on the target in the book's colour. */
    static void dot(ServerPlayer p, LivingEntity t, int pal, float each, int pulses, int gap, String id) {
        if (each <= 0.05f || !BURNING.add(t.getUUID())) return;
        ServerLevel sl = p.serverLevel();
        for (int k = 1; k <= pulses; k++) {
            final int kk = k;
            AstaFx.later(k * gap, () -> {
                if (!t.isAlive()) { BURNING.remove(t.getUUID()); return; }
                AstaFx.damage(p, t, each, id);
                Vec3 m = mid(t);
                if (pal == RIPPER) {
                    Vec3 a = new Vec3(Math.cos(kk * 2.1), 0.5, Math.sin(kk * 2.1)).normalize();
                    FxSend.trail(sl, RIPPER, m.subtract(a.scale(0.7)), m.add(a.scale(0.7)), 0.05f, 4);
                } else {
                    FxSend.trail(sl, pal, t.position().add(0, 0.1, 0), t.position().add(0, t.getBbHeight() * 0.9, 0), 0.12f, 5);
                }
                if (kk == pulses) BURNING.remove(t.getUUID());
            });
        }
        AstaFx.later(pulses * gap + 4, () -> BURNING.remove(t.getUUID()));
    }

    // ================================================================== area, projectile, lance
    /** A burst: damage everything within r, throw it out, draw a ring and a hit mark. */
    static void nova(ServerPlayer p, int pal, Vec3 at, double r, float dmg, String id, double push, double up) {
        ServerLevel sl = p.serverLevel();
        for (LivingEntity t : Kit.near(sl, p, at, r)) {
            hit(p, t, dmg, id, pal);
            Kit.push(t, mid(t).subtract(at), push, up);
        }
        Fx.impact(sl, pal, at, (float) r * 0.45f);
        if (!Sig.nova(sl, pal, at, r)) {
            FxSend.ring(sl, pal, at.add(0, 0.05, 0), 0.2f, (float) r, 0.08f, 8);
            FxSend.ring(sl, S, at.add(0, 0.3, 0), 0.1f, (float) r * 0.65f, 0.04f, 6);
        }
    }

    /** A glowing sphere thrown ahead; it bursts where it lands. {@code burst} runs at that spot after the damage. */
    static void bolt(ServerPlayer p, int pal, Vec3 from, Vec3 dir, float speed, float size, int age, double r, float dmg, String id,
                     double push, Consumer<Vec3> burst) {
        ServerLevel sl = p.serverLevel();
        MagicBolt.launch(p, from, dir.normalize().scale(speed), 0.02F + size * 0.02F, age,
                (b, at, t) -> {
                    Vec3 c = t == null ? at : mid(t);
                    nova(p, pal, c, r, dmg, id, push, 0.25);
                    if (burst != null) burst.accept(c);
                },
                (b, prev) -> {
                    if (Sig.bolt(sl, pal, prev, b.position(), b.getDeltaMovement(), size, sl.getGameTime())) return;
                    Kit.orb(sl, pal, b.position(), b.getDeltaMovement(), size, 2);
                    FxSend.trail(sl, pal, prev, b.position(), size * 0.6f, 5);
                    FxSend.trail(sl, S, prev, b.position(), size * 0.15f, 3);
                });
    }

    /** A straight beam through everything on the line. Returns where it ended. */
    static Vec3 lance(ServerPlayer p, int pal, double range, double w, float width, float dmg, String id, double push) {
        ServerLevel sl = p.serverLevel();
        Vec3 from = Kit.hand(p), dir = p.getLookAngle();
        Vec3 end = clip(sl, p, from, from.add(dir.scale(range)));
        FxSend.beam(sl, pal, from, end, width, 6);
        FxSend.beam(sl, S, from, end, width * 0.3f, 5);
        for (LivingEntity t : Kit.along(sl, p, from, end, w)) {
            hit(p, t, dmg, id, pal);
            Kit.push(t, dir, push, 0.2);
            Fx.impact(sl, pal, mid(t), 1.0f);
        }
        Fx.impact(sl, pal, end, 1.2f);
        return end;
    }

    /** A rush: the caster steps forward in {@code steps} quick hops (always to a safe spot), leaving a streak. */
    static void dash(ServerPlayer p, int pal, Vec3 dir, double dist, int steps, java.util.function.BiConsumer<Vec3, Integer> perStep) {
        ServerLevel sl = p.serverLevel();
        Vec3 d = flat(dir), start = p.position();
        for (int i = 1; i <= steps; i++) {
            final int ii = i;
            AstaFx.later(i, () -> {
                if (!alive(p)) return;
                Vec3 want = start.add(d.scale(dist * ii / steps));
                Vec3 dest = SpaceFx.safe(sl, want, p);
                if (dest == null) return;
                Vec3 from = p.position().add(0, 1, 0);
                SpaceFx.move(p, dest, p.getYRot());
                if (!Sig.dash(sl, pal, from, dest.add(0, 1, 0))) {
                    FxSend.trail(sl, pal, from, dest.add(0, 1, 0), 0.22f, 6);
                    FxSend.trail(sl, S, from, dest.add(0, 1, 0), 0.06f, 4);
                }
                if (perStep != null) perStep.accept(dest, ii);
            });
        }
    }

    /** Strikes falling from above onto a circle: each is a beam from the sky, then a small burst. */
    static void rain(ServerPlayer p, int pal, Vec3 c, double r, int count, int gap, int lead, float dmg, String id, double burstR) {
        ServerLevel sl = p.serverLevel();
        Random rnd = new Random(c.hashCode() ^ count);
        for (int i = 0; i < count; i++) {
            final int ii = i;
            double a = rnd.nextDouble() * Math.PI * 2, d = Math.sqrt(rnd.nextDouble()) * r;
            Vec3 aimAt = c.add(Math.cos(a) * d, 0, Math.sin(a) * d);
            Vec3 g = FxLib.ground(sl, aimAt.add(0, 3, 0), 8);
            Vec3 ground = g == null ? aimAt : g;
            AstaFx.later(1 + i * gap, () -> {
                if (!alive(p)) return;
                Vec3 top = ground.add(0.6, 11, 0.3);
                FxSend.ring(sl, pal, ground.add(0, 0.05, 0), 0.1f, (float) burstR * 0.8f, 0.03f, lead + 1);
                for (int s = 0; s < lead; s++) {
                    final int ss = s;
                    AstaFx.later(s + 1, () -> {
                        double t0 = ss / (double) lead, t1 = (ss + 1) / (double) lead;
                        Vec3 q0 = top.add(ground.subtract(top).scale(t0)), q1 = top.add(ground.subtract(top).scale(t1));
                        if (Sig.strike(sl, pal, q0, q1)) return;
                        FxSend.trail(sl, pal, q0, q1, 0.45f, 4);
                        FxSend.trail(sl, S, q0, q1, 0.12f, 3);
                        Kit.orb(sl, pal, q1, q1.subtract(q0), 0.5f, 2);
                    });
                }
                AstaFx.later(lead + 1, () -> {
                    if (!alive(p)) return;
                    nova(p, pal, ground.add(0, 0.3, 0), burstR, dmg, id, 0.5, 0.3);
                    FxSend.shake(sl, ground, 0.15f, 3, 24);
                    sound(sl, ground, SoundEvents.GENERIC_EXPLODE, 0.45f, 1.3f + (ii % 3) * 0.1f);
                });
            });
        }
    }

    /** A rolling front (a wave, a stampede): crescents sweep ahead for {@code steps} ticks and carry what they touch. */
    static void front(ServerPlayer p, int pal, Vec3 dir, int steps, double stepLen, float width, float dmg, String id, double push,
                      Consumer<Vec3> perStep) {
        ServerLevel sl = p.serverLevel();
        Vec3 d = flat(dir), rt = rightOf(d), up = new Vec3(0, 1, 0);
        Vec3 start = p.position().add(0, 0.1, 0).add(d.scale(1.5));
        Set<UUID> hit = new HashSet<>();
        for (int k = 0; k < steps; k++) {
            final int kk = k;
            AstaFx.later(6 + k, () -> {
                if (!alive(p)) return;
                Vec3 f = start.add(d.scale(kk * stepLen));
                float w = width + kk * 0.1f;
                FxSend.crescent(sl, pal, f.add(0, 1.0, 0), d, up, w, 1.1f + kk * 0.03f, 4, 0.0f);
                FxSend.trail(sl, S, f.add(rt.scale(-w)).add(0, 1.5, 0), f.add(rt.scale(w)).add(0, 1.5, 0), 0.06f, 3);
                if (kk % 2 == 0) FxSend.ring(sl, pal, f.add(0, 0.05, 0), 0.2f, w * 0.8f, 0.05f, 5);
                for (LivingEntity t : Kit.near(sl, p, f.add(0, 1, 0), w)) {
                    if (!hit.add(t.getUUID())) continue;
                    hit(p, t, dmg, id, pal);
                    Kit.push(t, d, push, 0.4);
                    Fx.impact(sl, pal, mid(t), 1.1f);
                }
                if (perStep != null) perStep.accept(f);
            });
        }
    }

    /** A fan of crescent slashes ahead of the caster (claws, blades): {@code n} cuts, spread by {@code spread} radians. */
    static void claws(ServerPlayer p, int pal, int n, double spread, float len, double reach, float dmg, String id, double push, int gap) {
        ServerLevel sl = p.serverLevel();
        Set<UUID> hit = new HashSet<>();
        for (int i = 0; i < n; i++) {
            final int ii = i;
            AstaFx.later(1 + i * gap, () -> {
                if (!alive(p)) return;
                Vec3 look = p.getLookAngle(), flatLook = flat(look);
                double off = n == 1 ? 0 : (ii / (double) (n - 1) - 0.5) * 2 * spread;
                Vec3 dir = rotY(flatLook, off);
                Vec3 c = p.getEyePosition().add(dir.scale(reach * 0.55)).add(0, -0.3, 0);
                double tilt = (ii % 2 == 0 ? 1 : -1) * 0.7;
                Vec3 up = new Vec3(rightOf(dir).x * tilt, 1, rightOf(dir).z * tilt).normalize();
                if (!Sig.claw(sl, pal, c, dir, up, len, ii)) {
                    FxSend.slash(sl, pal, c, dir, up, len, len * 0.28f, 5, 0.25f);
                    FxSend.slash(sl, S, c, dir, up, len * 0.8f, len * 0.1f, 4, 0.25f, 0.5f);
                }
                for (LivingEntity t : Kit.near(sl, p, p.getEyePosition().add(dir.scale(reach * 0.6)), reach * 0.6 + 0.8)) {
                    if (to(p, t).dot(dir) < 0.2) continue;
                    if (!hit.add(t.getUUID())) { hit(p, t, dmg * 0.45f, id, pal); Fx.spark(sl, pal, mid(t), 0.9f); continue; }
                    hit(p, t, dmg, id, pal);
                    Kit.push(t, dir, push, 0.2);
                    Fx.impact(sl, pal, mid(t), 1.0f);
                }
                sound(sl, p.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.8f, 1.1f + ii * 0.07f);
            });
        }
    }

    private static Vec3 to(ServerPlayer p, LivingEntity t) {
        Vec3 v = mid(t).subtract(p.getEyePosition());
        return v.lengthSqr() < 1.0E-4 ? Vec3.ZERO : v.normalize();
    }

    // ================================================================== modes
    private record ModeRun(long until, int every, Consumer<ServerPlayer> fx) {}
    private static final Map<UUID, Map<String, ModeRun>> MODES = new HashMap<>();

    /** A timed mode: shown on the HUD, with {@code fx} run every {@code every} ticks for as long as it lasts. */
    static void mode(ServerPlayer p, String id, int ticks, int every, Consumer<ServerPlayer> fx) {
        MODES.computeIfAbsent(p.getUUID(), k -> new HashMap<>()).put(id, new ModeRun(p.serverLevel().getGameTime() + ticks, every, fx));
        ModeHud.show(p, id, ticks);
    }

    /** The mode's trappings on the player, drawn smoothly by the client for {@code life} ticks (ModeAuraFx; 0 ends it). */
    static void aura(ServerPlayer p, int style, int pal, int life) {
        com.ironspellgrimoire.network.FxPacket pkt = new com.ironspellgrimoire.network.FxPacket();
        pkt.shape = 64; pkt.id = p.getId(); pkt.life = life; pkt.sweep = style; pkt.palette = (byte) pal;
        com.ironspellgrimoire.network.AddonNetwork.send(net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), pkt);
    }

    /** Whether {@code id} is running on the player right now. */
    static boolean inMode(ServerPlayer p, String id) {
        Map<String, ModeRun> m = MODES.get(p.getUUID());
        ModeRun r = m == null ? null : m.get(id);
        return r != null && p.serverLevel().getGameTime() < r.until();
    }

    /** Ticks left on {@code id} (0 when it isn't running). */
    static long left(ServerPlayer p, String id) {
        Map<String, ModeRun> m = MODES.get(p.getUUID());
        ModeRun r = m == null ? null : m.get(id);
        return r == null ? 0 : Math.max(0, r.until() - p.serverLevel().getGameTime());
    }

    static void ends(ServerPlayer p, String id) {
        Map<String, ModeRun> m = MODES.get(p.getUUID());
        boolean was = m != null && m.remove(id) != null;
        ModeHud.hide(p, id);
        if (was) {   // the newer grimoires (FxPacket shapes 83-95) draw their modes on the player: clear them now, not when they'd have run out
            for (int shape = 83; shape <= 97; shape++) {
                com.ironspellgrimoire.network.FxPacket k = new com.ironspellgrimoire.network.FxPacket();
                k.shape = (byte) shape; k.h = 98; k.v = new net.minecraft.world.phys.Vec3(p.getId(), 1, 0); k.c = p.position(); k.life = 1;
                FxSend.sendNear(p.serverLevel(), p.position(), k);
            }
        }
    }

    /** Armour, toughness, speed, knockback resistance and attack speed for {@code ticks}. */
    static void stance(ServerPlayer p, double armor, double tough, double speed, double kbRes, double attackSpeed, int ticks) {
        if (armor != 0) TimedModifiers.apply(p, Attributes.ARMOR, ARMOR, "Grimoire armour", armor, AttributeModifier.Operation.ADDITION, ticks);
        if (tough != 0) TimedModifiers.apply(p, Attributes.ARMOR_TOUGHNESS, TOUGH, "Grimoire toughness", tough, AttributeModifier.Operation.ADDITION, ticks);
        if (speed != 0) TimedModifiers.apply(p, Attributes.MOVEMENT_SPEED, SPEED, "Grimoire speed", speed, AttributeModifier.Operation.MULTIPLY_BASE, ticks);
        if (kbRes != 0) TimedModifiers.apply(p, Attributes.KNOCKBACK_RESISTANCE, KB, "Grimoire footing", kbRes, AttributeModifier.Operation.ADDITION, ticks);
        if (attackSpeed != 0) TimedModifiers.apply(p, Attributes.ATTACK_SPEED, ASPEED, "Grimoire tempo", attackSpeed, AttributeModifier.Operation.MULTIPLY_BASE, ticks);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        Map<String, ModeRun> m = MODES.get(p.getUUID());
        if (m == null || m.isEmpty()) return;
        long now = p.serverLevel().getGameTime();
        java.util.List<ModeRun> due = new java.util.ArrayList<>();
        var it = m.entrySet().iterator();
        while (it.hasNext()) {
            var en = it.next();
            ModeRun r = en.getValue();
            if (now > r.until()) { it.remove(); ModeHud.hide(p, en.getKey()); continue; }
            if (now % r.every() == 0) due.add(r);
        }
        // run after the loop: a mode's effect may start or end another mode (or kill something that does), which
        // would change this map while it was being walked
        for (ModeRun r : due) {
            try { r.fx().accept(p); } catch (RuntimeException ex) { org.slf4j.LoggerFactory.getLogger("iron_spell_grimoire").warn("mode effect failed", ex); }
        }
    }

    @SubscribeEvent
    public static void onLogout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e) {
        UUID id = e.getEntity().getUUID();
        MODES.remove(id); FURY.remove(id); HEALED.remove(id);
    }

    public static void clearAll() { MODES.clear(); FURY.clear(); HEALED.clear(); BURNING.clear(); }
}
