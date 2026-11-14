package com.ironspellgrimoire.server;

import static com.ironspellgrimoire.server.FxUtil.*;

import com.ironspellgrimoire.network.FxPacket;
import com.ironspellgrimoire.registry.AddonSounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The Treasure Grimoire (an original grimoire): the key to a vault of weapons. Golden ripples open in the air behind
 * you and swords, spears and axes come out of them at speed. Drawn by client/TreasureFxClient (FxPacket shape 87):
 * rippling gold portals and blades of polished steel with gold fittings.
 *
 *  1 Vault Volley       - four portals open behind you and each fires a weapon at your target
 *  2 Gilded Spear       - one heavy spear from one portal, straight through everything in a line
 *  3 Vault Chains       - chains shoot from portals round your target and hold it fast
 *  4 Blade Wall         - a line of ripples opens in front of you, weapons half out and pointing forward: missiles
 *                         break on it, foes are cut; when it closes every blade fires straight ahead
 *  5 Blade Rain         - portals open in the sky over your target and blades rain down
 *  6 Seeking Blades     - six weapons fly out, each hunting a different foe
 *  7 Treasure Barrage   - ten portals behind you fire as fast as they can at your target for two seconds
 *  8 Vault Aegis        - 10 s: a golden shield; every blow you take is answered by a weapon from the vault
 *  9 King's Vault (mode)- 20 s: portals fill the sky behind you and fire on their own at foes near you;
 *                         Treasure spells hit 25% harder
 * 10 Sundering Key      - (id vault_breaker) a drill-blade of three counter-turning drums slides out of a dark ripple,
 *                         spins up, and lets go a black-and-crimson storm that tears through everything ahead and leaves
 *                         the air cracked. Surpassing Limits: wider and longer.
 *
 * Passive "Open Vault": every Treasure spell leaves one more portal open (up to five, for 8 s); each makes the
 * weapons hit 5% harder.
 */
@Mod.EventBusSubscriber(modid = "iron_spell_grimoire")
public final class TreasureFx {
    private TreasureFx() {}

    public static final String MODE = "kings_vault", AEGIS = "vault_aegis";
    static final int PAL = Fx.GOLD;
    private static final UUID BOUND = UUID.fromString("af5b7060-4b85-4ec2-9f55-be62f9000001");
    private static final double TAU = Math.PI * 2;
    public static final int SWORD = 0, SPEAR = 1, AXE = 2;

    // ================================================================== the client shapes (TreasureFxClient, shape 87)
    private static FxPacket pk(int kind, Vec3 c, int life) {
        FxPacket p = new FxPacket();
        p.shape = 87; p.id = FxSend.nextId(); p.h = kind; p.c = c; p.life = life; p.a1 = (float) (Math.random() * 1000);
        return p;
    }
    private static void send(ServerLevel sl, FxPacket p) { FxSend.sendNear(sl, p.c, p); }

    /** 0 a golden portal at c facing d (r = size). */
    static void portal(ServerLevel sl, Vec3 c, Vec3 d, float r, int life) { FxPacket p = pk(0, c, life); p.d = d; p.r = r; send(sl, p); }
    /** 1 a weapon: comes out of c pointing at u, waits {@code wait} ticks, then flies to u in {@code fly}. */
    static void weapon(ServerLevel sl, Vec3 c, Vec3 u, int type, float size, int wait, int fly) {
        FxPacket p = pk(1, c, wait + fly + 2);
        p.u = u; p.a0 = type; p.r = size; p.sweep = wait; p.speed = fly;
        send(sl, p);
    }
    /** 2 a chain from c to an entity. */
    static void chain(ServerLevel sl, Vec3 c, LivingEntity t, int life) { FxPacket p = pk(2, c, life); p.v = new Vec3(t.getId(), 1, 0); send(sl, p); }
    /** 3 a wall of crossed blades at c facing d, r wide. */
    static void wall(ServerLevel sl, Vec3 c, Vec3 d, float w, int life) { FxPacket p = pk(3, c, life); p.d = d; p.r = w; send(sl, p); }
    /** 4 the vault breaker's spiral from c along d, r long, r2 wide, {@code charge} ticks before it tears out. */
    static void spiral(ServerLevel sl, Vec3 c, Vec3 d, float len, float w, int charge, int life) { FxPacket p = pk(4, c, life); p.d = d; p.r = len; p.r2 = w; p.sweep = charge; send(sl, p); }
    /** 5 King's Vault: portals in the sky behind a player. */
    static void sky(ServerLevel sl, ServerPlayer pl, int life) { FxPacket p = pk(5, pl.position(), life); p.v = new Vec3(pl.getId(), 1, 0); send(sl, p); }
    /** 6 the Aegis round a player. */
    static void aegis(ServerLevel sl, ServerPlayer pl, int life) { FxPacket p = pk(6, pl.position(), life); p.v = new Vec3(pl.getId(), 1, 0); send(sl, p); }

    // ================================================================== helpers
    static void play(ServerLevel sl, Vec3 at, String kind, float vol, float pitch) {
        SoundEvent s = AddonSounds.get(kind);
        if (s != null) sl.playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, vol, pitch);
    }

    private static final Map<UUID, long[]> OPEN = new HashMap<>();

    static boolean kings(ServerPlayer p) { return Moves.inMode(p, MODE); }

    /** Open Vault: one more portal open; the weapons hit harder for each (and King's Vault: +25%). */
    private static float open(ServerPlayer p, float power) {
        long now = p.serverLevel().getGameTime();
        long[] o = OPEN.get(p.getUUID());
        int n = o == null || now > o[1] ? 0 : (int) o[0];
        OPEN.put(p.getUUID(), new long[]{Math.min(5, n + 1), now + 160});
        return power * (1f + 0.05f * n) * (kings(p) ? 1.25f : 1f);
    }

    static void hit(ServerPlayer p, LivingEntity t, float amt, String id) { AstaFx.damage(p, t, amt, id); }

    /** A place behind the player for a portal: {@code i} of {@code n}, in a fan behind the shoulders. */
    static Vec3 behind(ServerPlayer p, int i, int n, double spread, double up) {
        Vec3 look = flat(p.getLookAngle());
        if (look.lengthSqr() < 1.0E-4) look = new Vec3(0, 0, 1);
        look = look.normalize();
        double s = n <= 1 ? 0 : (i / (double) (n - 1) - 0.5) * 2;
        return p.position().add(0, 1.6 + up + Math.abs(s) * -0.3 + (i % 2) * 0.5, 0).add(look.scale(-0.9)).add(rightOf(look).scale(s * spread));
    }

    /** One weapon out of a new portal at {@code from}, at {@code to}; it lands after {@code wait + fly} ticks. */
    static int fire(ServerLevel sl, Vec3 from, Vec3 to, int type, float size, int wait) {
        int fly = Math.max(2, (int) Math.ceil(from.distanceTo(to) / 3.2));
        portal(sl, from, to.subtract(from).normalize(), 0.6f + size * 0.3f, wait + fly + 4);
        weapon(sl, from, to, type, size, wait, fly);
        return wait + fly;
    }

    private static Vec3 target(ServerPlayer p, double range) { return InkFx.target(p, range); }

    private static Vec3 aimPoint(ServerPlayer p, double range) {
        LivingEntity t = aimed(p, range, 10);
        return t != null ? mid(t) : clip(p.serverLevel(), p, p.getEyePosition(), p.getEyePosition().add(p.getLookAngle().scale(range)));
    }

    /** The Aegis: 30% less damage, and every blow is answered by a weapon. */
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !Moves.inMode(p, AEGIS)) return;
        e.setAmount(e.getAmount() * 0.7f);
        if (e.getSource().getEntity() instanceof LivingEntity a && a != p && a.isAlive()) {
            ServerLevel sl = p.serverLevel();
            Vec3 from = behind(p, ThreadLocalRandom.current().nextInt(3), 3, 1.2, 0.3);
            int land = fire(sl, from, mid(a), SWORD, 1f, 2);
            final float pow = 6f;
            AstaFx.later(land, () -> { if (alive(p) && a.isAlive()) hit(p, a, pow, "vault_aegis"); });
            play(sl, from, "portal_open", 0.5f, 1.4f);
        }
    }

    /** King's Vault: the sky's portals fire on their own. */
    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || !kings(p) || p.tickCount % 10 != 0) return;
        ServerLevel sl = p.serverLevel();
        LivingEntity t = null;
        double best = 20;
        for (LivingEntity f : Kit.near(sl, p, p.position().add(0, 1, 0), 20)) { double d = f.distanceTo(p); if (d < best) { best = d; t = f; } }
        if (t == null) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Vec3 from = behind(p, r.nextInt(5), 5, 2.6, 1.6 + r.nextDouble());
        final LivingEntity tt = t;
        Vec3 to = mid(t);
        int land = fire(sl, from, to, r.nextInt(3), 1f, 3);
        AstaFx.later(land, () -> { if (alive(p) && tt.isAlive()) hit(p, tt, 4f, "kings_vault"); });
        play(sl, from, "blade_fly", 0.4f, 1.2f + r.nextFloat() * 0.3f);
    }

    // ================================================================== 1. Vault Volley
    public static void vaultVolley(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        final float pow = open(p, power);
        final Vec3 to = aimPoint(p, 32);
        for (int i = 0; i < 4; i++) {
            final int k = i;
            Vec3 from = behind(p, i, 4, 1.8, 0.2);
            int land = fire(sl, from, to, i % 3, 1f, 4 + i * 2);
            AstaFx.later(land, () -> {
                if (!alive(p)) return;
                for (LivingEntity e : Kit.near(sl, p, to, 1.6)) hit(p, e, pow * 0.4f, "vault_volley");
                if (k % 2 == 0) play(sl, to, "blade_fly", 0.6f, 0.9f);
            });
        }
        play(sl, p.position(), "portal_open", 0.9f, 1.0f);
    }

    // ================================================================== 2. Gilded Spear
    public static void gildedSpear(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        final float pow = open(p, power);
        Vec3 from = behind(p, 0, 1, 0, 0.5);
        Vec3 dir = p.getLookAngle();
        Vec3 to = clip(sl, p, from, from.add(dir.scale(32)));
        int land = fire(sl, from, to, SPEAR, 1.6f, 6);
        AstaFx.later(6, () -> {
            if (!alive(p)) return;
            for (LivingEntity e : Kit.along(sl, p, from, to, 1.1)) {
                hit(p, e, pow * 1.2f, "gilded_spear");
                Kit.push(e, dir, 0.6 * GravityFx.give(e), 0.15);
            }
            play(sl, from, "blade_fly", 1.0f, 0.6f);
        });
        AstaFx.later(land, () -> FxSend.shake(sl, to, 0.3f, 5, 14));
        play(sl, from, "portal_open", 1.0f, 0.8f);
    }

    // ================================================================== 3. Vault Chains
    public static void vaultChains(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        final float pow = open(p, power);
        LivingEntity t = aimed(p, 24, 12);
        if (t == null) {
            List<LivingEntity> l = Kit.near(sl, p, Kit.aim(p, 24), 5);
            t = l.isEmpty() ? null : l.get(0);
        }
        if (t == null) { portal(sl, Kit.aim(p, 12), p.getLookAngle().scale(-1), 0.9f, 12); play(sl, p.position(), "portal_open", 0.6f, 0.7f); return; }
        final LivingEntity tt = t;
        final int T = IceFx.boss(t) ? 40 : 70;
        for (int i = 0; i < 4; i++) {
            double an = i * TAU / 4 + 0.6;
            Vec3 at = t.position().add(Math.cos(an) * 3, 2.2 + (i % 2) * 0.8, Math.sin(an) * 3);
            portal(sl, at, mid(t).subtract(at).normalize(), 0.7f, T);
            chain(sl, at, t, T);
        }
        TimedModifiers.apply(t, Attributes.MOVEMENT_SPEED, BOUND, "Chained", -1.0, AttributeModifier.Operation.MULTIPLY_TOTAL, T);
        hit(p, t, pow * 0.6f, "vault_chains");
        for (int k = 20; k < T; k += 20) AstaFx.later(k, () -> { if (alive(p) && tt.isAlive()) hit(p, tt, pow * 0.2f, "vault_chains"); });
        play(sl, mid(t), "chain_rattle", 1.0f, 1.0f);
    }

    // ================================================================== 4. Blade Wall
    /** A row of golden ripples opens in front of you and the vault's weapons come half out of them, points forward:
     *  a bristling line that cuts anything that walks into it and breaks every missile on it. When it closes, every
     *  weapon in the line fires straight ahead. */
    public static void bladeWall(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        final float pow = open(p, power);
        Vec3 d = flat(p.getLookAngle());
        if (d.lengthSqr() < 1.0E-4) d = new Vec3(0, 0, 1);
        final Vec3 dd = d.normalize(), rt = rightOf(dd);
        final Vec3 c = p.position().add(dd.scale(2.2));
        final float W = 5.2f;
        final int HOLD = 60, N = 9;
        final double RANGE = 14;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < N; i++) {   // two staggered rows: low and high, like a phalanx of blades
            double s = (i / (double) (N - 1) - 0.5) * W;
            double y = 0.6 + (i % 2) * 1.1 + r.nextDouble() * 0.2;
            Vec3 at = c.add(rt.scale(s)).add(0, y, 0);
            Vec3 to = at.add(dd.scale(RANGE)).add(0, -0.25, 0);
            int open = i * 2 > N ? (N - i) : i;   // they open from the middle outwards
            int type = i % 3 == 1 ? SPEAR : i % 3 == 2 ? AXE : SWORD;
            final int wait = HOLD - open;
            AstaFx.later(open, () -> {
                if (!alive(p)) return;
                portal(sl, at, dd, 0.75f, wait + 12);
                weapon(sl, at.subtract(dd.scale(0.15)), to, type, 1.15f, wait, 5);
            });
        }
        play(sl, c, "portal_open", 1.0f, 0.9f);
        play(sl, c, "blade_fly", 0.8f, 0.7f);
        for (int k = 0; k < HOLD; k += 2) {
            final int kk = k;
            AstaFx.later(k + 1, () -> {
                if (!alive(p)) return;
                Vec3 a = c.add(rt.scale(-W / 2)).add(0, 1, 0), b = c.add(rt.scale(W / 2)).add(0, 1, 0);
                for (Projectile pr : sl.getEntitiesOfClass(Projectile.class, new AABB(a, b).inflate(1.2, 1.5, 1.2), x -> x.getOwner() != p)) {
                    blade(sl, pr.position());
                    pr.discard();
                }
                if (kk % 10 == 0) for (LivingEntity e : Kit.along(sl, p, a, b, 1.3)) {
                    hit(p, e, pow * 0.25f, "blade_wall");
                    Kit.push(e, dd, 0.5 * GravityFx.give(e), 0.15);
                }
            });
        }
        AstaFx.later(HOLD, () -> {   // the line lets go: every blade flies forward at once
            if (!alive(p)) return;
            play(sl, c, "blade_fly", 1.2f, 0.9f);
            sound(sl, c, SoundEvents.TRIDENT_THROW, 1.0f, 0.8f);
            java.util.Set<LivingEntity> struck = new java.util.HashSet<>();
            for (int j = -2; j <= 2; j++) {
                Vec3 a = c.add(rt.scale(j * W / 4)).add(0, 1, 0);
                for (LivingEntity e : Kit.along(sl, p, a, a.add(dd.scale(RANGE)), 1.3)) struck.add(e);
            }
            AstaFx.later(4, () -> { for (LivingEntity e : struck) if (alive(p) && e.isAlive()) { hit(p, e, pow * 0.9f, "blade_wall"); Kit.push(e, dd, 0.7 * GravityFx.give(e), 0.2); } });
        });
    }

    private static void blade(ServerLevel sl, Vec3 at) {
        FxSend.ring(sl, Fx.GOLD | FxPacket.KEEP_SHAPE, at, 0.1f, 0.7f, 0.04f, 5);
        sound(sl, at, SoundEvents.ANVIL_LAND, 0.3f, 1.8f);
    }

    // ================================================================== 5. Blade Rain
    public static void bladeRain(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        final float pow = open(p, power);
        final Vec3 c = target(p, 28);
        final float R = 4.5f;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < 6; i++) {
            double an = i * TAU / 6;
            portal(sl, c.add(Math.cos(an) * 2.4, 9, Math.sin(an) * 2.4), new Vec3(0, -1, 0), 1.1f, 50);
        }
        play(sl, c.add(0, 9, 0), "portal_open", 1.2f, 0.7f);
        for (int k = 4; k < 44; k += 2) {
            final int kk = k;
            AstaFx.later(k, () -> {
                if (!alive(p)) return;
                double an = r.nextDouble() * TAU, rr = Math.sqrt(r.nextDouble()) * R;
                Vec3 g = InkFx.ground(sl, c.add(Math.cos(an) * rr, 0, Math.sin(an) * rr));
                double pa = r.nextInt(6) * TAU / 6;
                Vec3 from = c.add(Math.cos(pa) * 2.4, 9, Math.sin(pa) * 2.4);
                weapon(sl, from, g.add(0, 0.3, 0), r.nextInt(3), 0.9f, 0, 4);
                if (kk % 6 == 0) for (LivingEntity e : Kit.near(sl, p, c.add(0, 1, 0), R)) hit(p, e, pow * 0.22f, "blade_rain");
                if (kk % 8 == 0) play(sl, g, "blade_fly", 0.5f, 1.1f);
            });
        }
    }

    // ================================================================== 6. Seeking Blades
    public static void seekingBlades(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        final float pow = open(p, power);
        List<LivingEntity> foes = Kit.near(sl, p, p.position().add(0, 1, 0), 22);
        foes.sort((a, b) -> Double.compare(a.distanceToSqr(p), b.distanceToSqr(p)));
        Vec3 fallback = aimPoint(p, 24);
        for (int i = 0; i < 6; i++) {
            final LivingEntity t = foes.isEmpty() ? null : foes.get(i % foes.size());
            Vec3 from = behind(p, i, 6, 2.2, 0.4);
            Vec3 to = t != null ? mid(t) : fallback;
            int land = fire(sl, from, to, i % 3, 1f, 3 + i * 2);
            final Vec3 tf = to;
            AstaFx.later(land, () -> {
                if (!alive(p)) return;
                if (t != null && t.isAlive()) hit(p, t, pow * 0.45f, "seeking_blades");
                else for (LivingEntity e : Kit.near(sl, p, tf, 1.5)) hit(p, e, pow * 0.45f, "seeking_blades");
            });
        }
        play(sl, p.position(), "portal_open", 1.0f, 1.1f);
    }

    // ================================================================== 7. Treasure Barrage
    public static void treasureBarrage(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        final float pow = open(p, power);
        for (int i = 0; i < 10; i++) portal(sl, behind(p, i, 10, 3.2, 0.6), p.getLookAngle(), 0.85f, 46);
        play(sl, p.position(), "portal_open", 1.2f, 0.8f);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int k = 4; k < 44; k += 2) {
            final int kk = k;
            AstaFx.later(k, () -> {
                if (!alive(p)) return;
                Vec3 to = aimPoint(p, 32).add(r.nextDouble(-0.6, 0.6), r.nextDouble(-0.4, 0.4), r.nextDouble(-0.6, 0.6));
                Vec3 from = behind(p, r.nextInt(10), 10, 3.2, 0.6);
                int fly = Math.max(2, (int) Math.ceil(from.distanceTo(to) / 3.2));
                weapon(sl, from, to, r.nextInt(3), 0.9f, 0, fly);
                AstaFx.later(fly, () -> {
                    if (!alive(p)) return;
                    for (LivingEntity e : Kit.near(sl, p, to, 1.4)) hit(p, e, pow * 0.14f, "treasure_barrage");
                });
                if (kk % 6 == 0) play(sl, from, "blade_fly", 0.5f, 1.0f + r.nextFloat() * 0.4f);
            });
        }
    }

    // ================================================================== 8. Vault Aegis
    public static void vaultAegis(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        open(p, power);
        final int T = 200;
        aegis(sl, p, T);
        Moves.mode(p, AEGIS, T, 20, pl -> {});
        p.setAbsorptionAmount(Math.max(p.getAbsorptionAmount(), 6f));
        play(sl, p.position(), "portal_open", 1.0f, 0.6f);
        sound(sl, p.position(), SoundEvents.ARMOR_EQUIP_GOLD, 1.0f, 0.8f);
    }

    // ================================================================== 9. King's Vault (mode)
    public static void kingsVault(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        final int T = 400;
        sky(sl, p, T);
        Moves.mode(p, MODE, T, 20, pl -> {});
        FxSend.impactFrame(sl, p.position().add(0, 1, 0), 2, FxSend.FRAME_GOLD, 16);
        play(sl, p.position(), "portal_open", 1.4f, 0.5f);
        sound(sl, p.position(), SoundEvents.BEACON_ACTIVATE, 0.8f, 1.2f);
    }

    // ================================================================== 10. Sundering Key (id vault_breaker)
    /** The deepest thing in the vault: a key that is also a blade. A dark ripple opens at your side and it slides out -
     *  a long drill of three stacked drums turning against each other, scored with red lines. The drums spin faster,
     *  the air is pulled into them, and then it lets go: a twisting storm of black and crimson that tears a line
     *  through everything ahead and leaves the air itself cracked for a moment after. */
    public static void vaultBreaker(ServerPlayer p, float power) {
        boolean limit = LimitBreak.active(p);
        ServerLevel sl = p.serverLevel();
        final float pow = open(p, power) * (limit ? 1.3f : 1f);
        final Vec3 d = p.getLookAngle().normalize();
        Vec3 flatD = flat(d);
        if (flatD.lengthSqr() < 1.0E-4) flatD = new Vec3(0, 0, 1);
        final Vec3 rt = rightOf(flatD.normalize());
        final Vec3 hand = p.getEyePosition().add(rt.scale(0.55)).add(0, -0.45, 0).add(d.scale(0.9));
        final Vec3 from = hand.add(d.scale(2.6));   // the storm leaves from the key's point
        final float L = limit ? 42f : 32f, W = limit ? 4f : 3f;
        final int CHARGE = 30, BLAST = 22;
        Vec3 end = clip(sl, p, from, from.add(d.scale(L)));
        final float len = (float) from.distanceTo(end);
        key(sl, p, hand, d, CHARGE, CHARGE + BLAST + 16, limit);
        spiral(sl, from, d, len, W, CHARGE, CHARGE + BLAST + 30);
        play(sl, hand, "portal_open", 1.4f, 0.35f);
        sound(sl, hand, SoundEvents.BEACON_POWER_SELECT, 1.0f, 0.4f);
        for (int k = 0; k < 3; k++) { final float pitch = 0.5f + 0.3f * k; AstaFx.later(8 + k * 7, () -> { if (alive(p)) sound(sl, p.position(), SoundEvents.GRINDSTONE_USE, 0.6f, pitch); }); }   // the drums winding up
        for (int k = 0; k < 10; k++) {   // the storm lasts and drags along everything in it
            final int kk = k;
            AstaFx.later(CHARGE + k * 2, () -> {
                if (!alive(p)) return;
                for (LivingEntity e : Kit.along(sl, p, from.subtract(d.scale(2)), from.add(d.scale(len)), W)) {
                    hit(p, e, pow * (kk == 0 ? 1.4f : 0.12f), "vault_breaker");
                    Kit.push(e, d, 0.35 * GravityFx.give(e), 0.08);
                }
            });
        }
        AstaFx.later(CHARGE, () -> {
            FxSend.impactFrame(sl, from.add(d.scale(6)), 3, FxSend.FRAME_GOLD, 26);
            FxSend.shake(sl, from, 0.9f, 16, 30);
            play(sl, from, "blade_fly", 1.4f, 0.35f);
            sound(sl, from, SoundEvents.GENERIC_EXPLODE, 0.9f, 0.6f);
            sound(sl, from, SoundEvents.WITHER_SHOOT, 0.6f, 0.5f);
        });
        AstaFx.later(CHARGE + BLAST, () -> sound(sl, from.add(d.scale(len * 0.5)), SoundEvents.GLASS_BREAK, 0.9f, 0.5f));
    }

    /** 7 the Sundering Key itself, at a player's side: hand = where it sits, d = where it points. sweep = charge ticks, r2 = 1 when Surpassing Limits. */
    static void key(ServerLevel sl, ServerPlayer pl, Vec3 hand, Vec3 d, int charge, int life, boolean limit) {
        FxPacket p = pk(7, hand, life);
        p.d = d; p.sweep = charge; p.r = 1; p.r2 = limit ? 1 : 0;
        p.v = new Vec3(pl.getId(), 1, 0); p.u = hand.subtract(pl.position());
        send(sl, p);
    }

    /** Surpassing Limits: the vault opens wide behind you. */
    public static void awaken(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        for (int i = 0; i < 7; i++) portal(sl, behind(p, i, 7, 3, 1.2), p.getLookAngle(), 1.0f, 30);
        play(sl, p.position(), "portal_open", 1.2f, 0.6f);
    }
}
