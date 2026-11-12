package com.ironspellgrimoire.server;

import static com.ironspellgrimoire.server.FxUtil.*;

import com.ironspellgrimoire.network.FxPacket;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Scarlet Moon's ice. While the mode runs (a minute) the Blood Grimoire's ten spells turn into these: your blood
 * freezes white, nothing costs you health, and everything hits far harder (x1.6) and freezes what it touches (slowed;
 * encased foes can't move at all until the ice shatters - for a second, big hit).
 *
 *  1 Crimson Edge     -> Frost Crescent:   a crescent of cut ice flies 16 blocks, freezing the ground it passes
 *  2 Hemorrhage       -> Rime Spear:       a lance of ice encases the foe you aim at; the ice shatters for a heavy hit
 *  3 Sanguine Volley  -> Glacial Volley:   a fan of thirteen ice lances
 *  4 Transfusion      -> Frostbind:        a beam of cold binds the foe you aim at, freezing it more and more, then encases it
 *  5 Blood Pact       -> Winter's Pact:    six shards of ice circle you for 10 s, cutting anything close; +6 armour
 *  6 Red Tide         -> White Avalanche:  a wall of ice bursts up and races forward, throwing foes
 *  7 Thorned Veins    -> Glacier Spires:   three ranks of great spires burst out of a frozen field ahead of you
 *  8 Crimson Bulwark  -> Frozen Bastion:   a dome of ice (a big shield); when it breaks it explodes outward
 *  9 Exsanguinate     -> Absolute Zero:    frost spreads 10 blocks round you, everything in it is encased, then all of it shatters
 * 10 Scarlet Moon     -> Moonfall:         a comet of ice falls where you aim - a crater, a crown of spires, everything frozen
 */
public final class IceFx {
    private IceFx() {}

    private static final float POWER = 1.6f;
    private static final Map<UUID, Long> MOONFALL = new HashMap<>();

    // ================================================================== sending
    static FxPacket fx(int kind, Vec3 c, int life) {
        FxPacket p = new FxPacket();
        p.shape = 82; p.id = FxSend.nextId(); p.h = kind; p.c = c; p.life = life;
        return p;
    }

    static int send(ServerLevel sl, FxPacket p) {
        Vec3 at = p.c;
        com.ironspellgrimoire.network.AddonNetwork.send(net.minecraftforge.network.PacketDistributor.NEAR.with(
                () -> new net.minecraftforge.network.PacketDistributor.TargetPoint(at.x, at.y, at.z, 128, sl.dimension())), p);
        return p.id;
    }

    private static void shards(ServerLevel sl, Vec3 c, Vec3 v, float spread, int n, float size) {
        FxPacket p = fx(1, c, 1);
        p.d = v; p.r = spread; p.sweep = n; p.a0 = size;
        send(sl, p);
    }

    private static void spire(ServerLevel sl, Vec3 base, Vec3 dir, float len, float w, int life, int grow) {
        FxPacket p = fx(0, base, life);
        p.d = dir; p.r = len; p.r2 = w; p.sweep = grow;
        send(sl, p);
    }

    private static int frost(ServerLevel sl, Vec3 ground, float r, int spread, int life) {
        FxPacket p = fx(5, ground, life);
        p.r = r; p.speed = spread;
        return send(sl, p);
    }

    private static void end(ServerLevel sl, Vec3 near, int id) {
        FxPacket p = fx(99, near, 1);
        p.id = id;
        send(sl, p);
    }

    // ================================================================== hitting and freezing
    private static float pw(float power) { return power * POWER; }

    private static void hit(ServerPlayer p, LivingEntity t, float amt, String id) {
        AstaFx.damage(p, t, amt, id);
        chill(t, 60);
    }

    private static void chill(LivingEntity t, int ticks) {
        TimedModifiers.apply(t, Attributes.MOVEMENT_SPEED, TimedModifiers.FROST_SLOW, "Frozen", -0.55, AttributeModifier.Operation.MULTIPLY_TOTAL, ticks);
    }

    /** Encased: held fast in ice for {@code ticks}, then it shatters for {@code burst} damage. */
    private static void encase(ServerPlayer p, LivingEntity t, int ticks, float burst, String id) {
        ServerLevel sl = p.serverLevel();
        FxPacket e = fx(4, t.position(), ticks);
        e.v = new Vec3(t.getId(), 0, 0);
        send(sl, e);
        TimedModifiers.apply(t, Attributes.MOVEMENT_SPEED, TimedModifiers.FROST_ROOT, "Encased in ice", -1.0, AttributeModifier.Operation.MULTIPLY_TOTAL, ticks);
        t.setDeltaMovement(0, Math.min(0, t.getDeltaMovement().y), 0);
        t.hurtMarked = true;
        AstaFx.later(ticks, () -> {
            if (!t.isAlive()) return;
            TimedModifiers.remove(t, Attributes.MOVEMENT_SPEED, TimedModifiers.FROST_ROOT);
            if (alive(p)) hit(p, t, burst, id);
            sound(sl, t.position(), SoundEvents.GLASS_BREAK, 1.2f, 0.7f);
        });
    }

    private static void says(ServerPlayer p, String move) {
        p.displayClientMessage(Component.literal(move).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), true);
    }

    private static Vec3 flatLook(ServerPlayer p) {
        Vec3 l = flat(p.getLookAngle());
        return l.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : l.normalize();
    }

    private static void coldSound(ServerLevel sl, Vec3 at, float pitch) {
        sound(sl, at, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.2f, pitch);
        sound(sl, at, SoundEvents.GLASS_PLACE, 0.8f, pitch * 0.8f);
    }

    // ================================================================== 1. Frost Crescent
    public static void frostCrescent(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        float pow = pw(power) * 1.2f;
        Vec3 d = flatLook(p), c = p.position().add(0, 1.1, 0).add(d.scale(0.8));
        FxPacket cr = fx(2, c, 14);
        cr.d = d; cr.u = new Vec3(0, 1, 0); cr.r = 2.4f; cr.a0 = 16; cr.speed = 10;
        send(sl, cr);
        Set<UUID> struck = new HashSet<>();
        for (int k = 0; k <= 10; k++) {
            final double kk = k / 10.0;
            AstaFx.later(k, () -> {
                if (!alive(p)) return;
                Vec3 at = c.add(d.scale(16 * kk * (2 - kk)));
                for (LivingEntity e : enemies(sl, p, new AABB(at, at).inflate(2.6, 1.6, 2.6))) {
                    if (!struck.add(e.getUUID())) continue;
                    hit(p, e, pow, "frost_crescent");
                    shards(sl, mid(e), d.scale(0.1), 0.1f, 8, 0.07f);
                }
            });
        }
        says(p, "Frost Crescent");
        sound(sl, c, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, 1.4f);
        coldSound(sl, c, 1.4f);
    }

    // ================================================================== 2. Rime Spear
    public static void rimeSpear(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        LivingEntity t = aimed(p, 28, 14);
        if (t == null) { p.displayClientMessage(Component.literal("No one to freeze.").withStyle(ChatFormatting.GRAY), true); return; }
        float pow = pw(power);
        Vec3 from = p.getEyePosition().subtract(0, 0.2, 0);
        int flight = Math.max(2, (int) (from.distanceTo(mid(t)) / 3.5));
        FxPacket l = fx(3, from, flight + 2);
        l.u = mid(t); l.r = 1.6f; l.speed = flight;
        send(sl, l);
        AstaFx.later(flight, () -> {
            if (!t.isAlive() || !alive(p)) return;
            hit(p, t, pow * 0.8f, "rime_spear");
            encase(p, t, 50, pow * 1.6f, "rime_spear");
            coldSound(sl, t.position(), 0.8f);
        });
        says(p, "Rime Spear");
        sound(sl, from, SoundEvents.TRIDENT_THROW, 1.0f, 1.3f);
    }

    // ================================================================== 3. Glacial Volley
    public static void glacialVolley(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        float pow = pw(power) * 0.45f;
        Vec3 eye = p.getEyePosition().subtract(0, 0.2, 0), look = p.getLookAngle();
        int n = 13;
        for (int i = 0; i < n; i++) {
            Vec3 d = rotY(look, (i - (n - 1) / 2.0) * 0.09).normalize();
            final int delay = i % 3;
            final boolean[] spent = {false};
            final int[] id = {0};
            AstaFx.later(delay, () -> {
                if (!alive(p)) return;
                FxPacket l = fx(3, eye, 10);
                l.u = eye.add(d.scale(24)); l.r = 1.1f; l.speed = 8;
                id[0] = send(sl, l);
            });
            for (int k = 0; k < 8; k++) {
                final int kk = k;
                AstaFx.later(k + delay, () -> {
                    if (spent[0] || !alive(p)) return;
                    Vec3 a0 = eye.add(d.scale(3 * kk)), a1 = eye.add(d.scale(3 * (kk + 1)));
                    for (LivingEntity e : Kit.along(sl, p, a0, a1, 0.7)) {
                        hit(p, e, pow, "glacial_volley");
                        if (id[0] != 0) end(sl, mid(e), id[0]);
                        spent[0] = true;
                        break;
                    }
                });
            }
        }
        says(p, "Glacial Volley");
        coldSound(sl, eye, 1.6f);
        sound(sl, eye, SoundEvents.TRIDENT_THROW, 1.0f, 1.7f);
    }

    // ================================================================== 4. Frostbind
    public static void frostbind(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        LivingEntity t = aimed(p, 18, 16);
        if (t == null) { p.displayClientMessage(Component.literal("No one to bind.").withStyle(ChatFormatting.GRAY), true); return; }
        float pow = pw(power);
        FxPacket th = fx(6, p.position(), 50);
        th.v = new Vec3(p.getId(), t.getId(), 0);
        send(sl, th);
        for (int k = 10; k <= 40; k += 10) {
            final int kk = k;
            AstaFx.later(k, () -> {
                if (!alive(p) || !t.isAlive() || t.distanceTo(p) > 22) return;
                hit(p, t, pow * 0.3f, "frostbind");
                TimedModifiers.apply(t, Attributes.MOVEMENT_SPEED, TimedModifiers.FROST_SLOW, "Frozen", -0.2 - kk * 0.015, AttributeModifier.Operation.MULTIPLY_TOTAL, 40);
            });
        }
        AstaFx.later(48, () -> {
            if (!alive(p) || !t.isAlive()) return;
            encase(p, t, 30, pow * 0.9f, "frostbind");
        });
        says(p, "Frostbind");
        coldSound(sl, p.position(), 0.7f);
    }

    // ================================================================== 5. Winter's Pact
    public static void wintersPact(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        float pow = pw(power == 0 ? 8f : power) * 0.25f;
        FxPacket o = fx(7, p.position(), 200);
        o.v = new Vec3(p.getId(), 0, 0); o.r = 1.7f; o.sweep = 6;
        send(sl, o);
        TimedModifiers.apply(p, Attributes.ARMOR, TimedModifiers.WINTER_ARMOR, "Winter's Pact", 6, AttributeModifier.Operation.ADDITION, 200);
        Map<UUID, Long> last = new HashMap<>();
        for (int k = 5; k <= 200; k += 5) {
            AstaFx.later(k, () -> {
                if (!alive(p)) return;
                Vec3 c = p.position().add(0, 1, 0);
                long now = sl.getGameTime();
                for (LivingEntity e : enemies(sl, p, new AABB(c, c).inflate(2.4))) {
                    if (now - last.getOrDefault(e.getUUID(), -99L) < 10) continue;
                    last.put(e.getUUID(), now);
                    hit(p, e, pow, "winters_pact");
                    shards(sl, mid(e), Vec3.ZERO, 0.08f, 5, 0.06f);
                }
            });
        }
        says(p, "Winter's Pact");
        coldSound(sl, p.position(), 1.0f);
        sound(sl, p.position(), SoundEvents.ARMOR_EQUIP_DIAMOND, 1.0f, 0.8f);
    }

    // ================================================================== 6. White Avalanche
    public static void whiteAvalanche(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        float pow = pw(power) * 1.3f;
        Vec3 d = flatLook(p), start = p.position();
        FxPacket g = fx(9, ZenonAI.ground(sl, start.add(d.scale(1.2))), 15);
        g.d = d; g.r = 3f; g.r2 = 3.2f; g.speed = 1.1f;
        send(sl, g);
        Set<UUID> struck = new HashSet<>();
        for (int k = 0; k < 13; k++) {
            final int kk = k;
            AstaFx.later(k, () -> {
                if (!alive(p)) return;
                Vec3 at = ZenonAI.ground(sl, start.add(d.scale(1.2 + kk * 1.1)));
                for (LivingEntity e : enemies(sl, p, new AABB(at, at).inflate(3.2, 2.5, 1.6))) {
                    if (!struck.add(e.getUUID())) continue;
                    hit(p, e, pow, "white_avalanche");
                    e.setDeltaMovement(d.scale(1.1).add(0, 0.7, 0));
                    e.hurtMarked = true;
                }
                if (kk % 3 == 0) sound(sl, at, SoundEvents.GLASS_BREAK, 1.0f, 0.5f + kk * 0.04f);
            });
        }
        says(p, "White Avalanche");
        coldSound(sl, start, 0.6f);
    }

    // ================================================================== 7. Glacier Spires
    public static void glacierSpires(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        float pow = pw(power) * 1.1f;
        Vec3 d = flatLook(p), rt = rightOf(d);
        Vec3 start = p.position();
        frost(sl, ZenonAI.ground(sl, start.add(d.scale(6))), 7f, 8, 70);
        Set<UUID> struck = new HashSet<>();
        for (int rank = 0; rank < 3; rank++) {
            final int rk = rank;
            AstaFx.later(3 + rank * 4, () -> {
                if (!alive(p)) return;
                ThreadLocalRandom r = ThreadLocalRandom.current();
                double dist = 3.5 + rk * 3.2;
                int count = 4 + rk * 2;
                for (int i = 0; i < count; i++) {
                    double s = count == 1 ? 0 : (double) i / (count - 1) * 2 - 1;
                    Vec3 b = ZenonAI.ground(sl, start.add(d.scale(dist + r.nextDouble(-0.6, 0.6))).add(rt.scale(s * (1.6 + rk * 1.4))));
                    Vec3 dir = new Vec3(r.nextDouble(-0.25, 0.25), 1, r.nextDouble(-0.25, 0.25)).add(d.scale(0.25)).add(rt.scale(s * 0.3)).normalize();
                    spire(sl, b.subtract(0, 0.3, 0), dir, 2.6f + rk * 0.9f + r.nextFloat() * 1.2f, 0.32f + rk * 0.06f, 36 + r.nextInt(10), 3);
                    for (LivingEntity e : enemies(sl, p, new AABB(b, b).inflate(1.4, 3, 1.4))) {
                        if (!struck.add(e.getUUID())) continue;
                        hit(p, e, pow, "glacier_spires");
                        e.setDeltaMovement(e.getDeltaMovement().add(0, 0.9, 0));
                        e.hurtMarked = true;
                    }
                }
                sound(sl, start.add(d.scale(dist)), SoundEvents.GLASS_BREAK, 1.3f, 0.6f + rk * 0.15f);
                coldSound(sl, start.add(d.scale(dist)), 0.7f + rk * 0.1f);
            });
        }
        says(p, "Glacier Spires");
    }

    // ================================================================== 8. Frozen Bastion
    public static void frozenBastion(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        float pow = pw(power == 0 ? 8f : power);
        p.setAbsorptionAmount(Math.max(p.getAbsorptionAmount(), 12f + power * 0.6f));
        FxPacket dm = fx(8, p.position(), 200);
        dm.v = new Vec3(p.getId(), 0, 0); dm.r = 2.2f;
        final int dome = send(sl, dm);
        frost(sl, ZenonAI.ground(sl, p.position()), 3.2f, 6, 200);
        p.getPersistentData().remove("isg_bastion_broke");
        for (int k = 5; k < 200; k += 5) {
            AstaFx.later(k, () -> {
                if (!alive(p) || p.getAbsorptionAmount() > 0.5f || p.getPersistentData().getBoolean("isg_bastion_broke")) return;
                p.getPersistentData().putBoolean("isg_bastion_broke", true);
                Vec3 at = p.position().add(0, 1, 0);
                end(sl, at, dome);
                for (LivingEntity e : enemies(sl, p, new AABB(at, at).inflate(5))) {
                    hit(p, e, pow * 0.8f, "frozen_bastion");
                    Vec3 away = flat(e.position().subtract(at));
                    if (away.lengthSqr() > 1e-4) { e.setDeltaMovement(away.normalize().scale(1.0).add(0, 0.4, 0)); e.hurtMarked = true; }
                }
                sound(sl, at, SoundEvents.GLASS_BREAK, 1.4f, 0.5f);
            });
        }
        says(p, "Frozen Bastion");
        coldSound(sl, p.position(), 0.8f);
    }

    // ================================================================== 9. Absolute Zero
    public static void absoluteZero(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        float pow = pw(power);
        Vec3 c = p.position();
        frost(sl, ZenonAI.ground(sl, c), 10f, 14, 80);
        FxPacket ms = fx(11, c.add(0, 0.5, 0), 40);
        ms.r = 5f;
        send(sl, ms);
        AstaFx.later(14, () -> {
            if (!alive(p)) return;
            List<LivingEntity> foes = enemies(sl, p, new AABB(c, c).inflate(10, 4, 10));
            for (LivingEntity e : foes) {
                if (e.position().distanceTo(c) > 10.5) continue;
                hit(p, e, pow * 0.4f, "absolute_zero");
                encase(p, e, 30, pow * 2.2f, "absolute_zero");
            }
            sound(sl, c, SoundEvents.GLASS_PLACE, 1.6f, 0.5f);
            FxSend.shake(sl, c, 0.4f, 8, 16);
        });
        says(p, "Absolute Zero");
        coldSound(sl, c, 0.5f);
        sound(sl, c, SoundEvents.BEACON_DEACTIVATE, 0.8f, 1.6f);
    }

    // ================================================================== 10. Moonfall (Scarlet Moon cast again under the moon)
    public static void moonfall(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        long now = sl.getGameTime();
        long ready = MOONFALL.getOrDefault(p.getUUID(), 0L);
        if (now < ready) {
            p.displayClientMessage(Component.literal("Moonfall returns in " + (ready - now + 19) / 20 + " s").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        MOONFALL.put(p.getUUID(), now + 300);
        float pow = pw(Math.max(power, 14f)) * 3f;
        Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
        net.minecraft.world.phys.HitResult hr = sl.clip(new net.minecraft.world.level.ClipContext(eye, eye.add(look.scale(30)),
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, p));
        Vec3 hitAt = hr.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? eye.add(look.scale(18)) : hr.getLocation();
        Vec3 at = ZenonAI.ground(sl, hitAt);
        Vec3 back = flatLook(p).scale(-7);
        FxPacket cm = fx(10, at.add(back).add(0, 28, 0), 90);
        cm.u = at; cm.r = 1.3f; cm.speed = 18;
        send(sl, cm);
        sound(sl, at, SoundEvents.BEACON_ACTIVATE, 1.5f, 1.8f);
        AstaFx.later(18, () -> {
            if (!alive(p)) return;
            for (LivingEntity e : enemies(sl, p, new AABB(at, at).inflate(7, 5, 7))) {
                if (e.position().distanceTo(at) > 7.5) continue;
                hit(p, e, pow, "moonfall");
                e.setDeltaMovement(flat(e.position().subtract(at)).normalize().scale(0.6).add(0, 0.9, 0));
                e.hurtMarked = true;
                AstaFx.later(8, () -> { if (e.isAlive() && alive(p)) encase(p, e, 24, pow * 0.35f, "moonfall"); });
            }
            FxSend.shake(sl, at, 1.0f, 14, 26);
            sound(sl, at, SoundEvents.GENERIC_EXPLODE, 1.6f, 1.3f);
            sound(sl, at, SoundEvents.GLASS_BREAK, 2.0f, 0.4f);
            coldSound(sl, at, 0.5f);
        });
        says(p, "MOONFALL");
    }
}
