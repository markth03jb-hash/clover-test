package com.ironspellgrimoire.server;

import com.ironspellgrimoire.IronSpellGrimoire;
import com.ironspellgrimoire.registry.AddonItemRegistry;
import io.redspace.ironsspellbooks.api.events.SpellPreCastEvent;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The machinery under Julius Novachrono's Time Magic (spells in {@link TimeFx}):
 *
 *  - Stopping time on a target: it is held exactly where it stood - it cannot move, turn, attack, shoot or cast -
 *    until the freeze runs out. No potion effect is used and nothing is saved on the entity, so a frozen mob can
 *    never be left stuck. Bosses are too strong to stop: they are slowed hard instead.
 *  - Held damage (Zenith Hour): while the caster's stopped time holds a target, every blow the caster lands on it is
 *    held back, and it all lands at once when time starts again.
 *  - Stopped arrows and other projectiles hang in the air and fly on when time resumes.
 *  - Stored Time (the book's passive): every hit you land while holding the grimoire banks one second, up to ten.
 *  - Your recent past: where you stood and how hurt you were, every quarter second for the last six seconds
 *    (Time Recovery rewinds to it), and Rewind Ward's banked damage.
 */
@Mod.EventBusSubscriber(modid = IronSpellGrimoire.MODID)
public final class TimeStop {
    private TimeStop() {}

    public static final int MAX_STORED = 10;

    // ================================================================== frozen things
    private static final class Frozen {
        LivingEntity e; ServerPlayer by; Vec3 pos; float yaw, pitch, head, body; long until; boolean hold; float bank; Runnable onRelease;
    }

    private static final class FrozenShot { Projectile pr; Vec3 pos, vel; boolean noGrav; long until; }

    private static final Map<UUID, Frozen> FROZEN = new HashMap<>();
    private static final Map<UUID, FrozenShot> SHOTS = new HashMap<>();

    public static boolean isBoss(LivingEntity t) {
        return t instanceof EnderDragon || t instanceof WitherBoss || t.getMaxHealth() >= 150f || !t.canChangeDimensions();
    }

    public static boolean frozen(Entity e) { return e != null && FROZEN.containsKey(e.getUUID()); }

    /**
     * Stops time on {@code t} for {@code ticks}. {@code hold}: the caster's blows on it are held back until it ends.
     * Returns false (and slows it hard instead) for a boss.
     */
    public static boolean freeze(ServerPlayer by, LivingEntity t, int ticks, boolean hold, Runnable onRelease) {
        if (t == null || !t.isAlive() || t == by) return false;
        ServerLevel sl = by.serverLevel();
        long now = sl.getGameTime();
        if (isBoss(t)) { Moves.slow(t, ticks, -0.75); return false; }
        Frozen f = FROZEN.get(t.getUUID());
        if (f != null) {
            f.until = Math.max(f.until, now + ticks);
            f.hold |= hold;
            if (onRelease != null) { Runnable old = f.onRelease; f.onRelease = old == null ? onRelease : () -> { old.run(); onRelease.run(); }; }
            TimeFx.frozenMark(sl, t, (int) (f.until - now));
            return true;
        }
        f = new Frozen();
        f.e = t; f.by = by; f.pos = t.position(); f.yaw = t.getYRot(); f.pitch = t.getXRot(); f.head = t.getYHeadRot(); f.body = t.yBodyRot;
        f.until = now + ticks; f.hold = hold; f.onRelease = onRelease;
        FROZEN.put(t.getUUID(), f);
        t.setDeltaMovement(Vec3.ZERO);
        t.hurtMarked = true;
        if (t instanceof Mob m) m.getNavigation().stop();
        TimeFx.frozenMark(sl, t, ticks);
        return true;
    }

    /** Ends a freeze early (or extends nothing): the target is let go this tick. */
    public static void release(LivingEntity t) {
        Frozen f = FROZEN.get(t.getUUID());
        if (f != null) f.until = 0;
    }

    /** Arrows, fireballs and other shots within {@code r} of {@code c} hang where they are for {@code ticks}. */
    public static int freezeShots(ServerLevel sl, Vec3 c, double r, int ticks, ServerPlayer owner) {
        long now = sl.getGameTime();
        int n = 0;
        for (Projectile pr : sl.getEntitiesOfClass(Projectile.class, new AABB(c, c).inflate(r))) {
            if (pr.getOwner() == owner || pr.distanceToSqr(c) > r * r) continue;
            FrozenShot s = SHOTS.get(pr.getUUID());
            if (s == null) {
                s = new FrozenShot();
                s.pr = pr; s.pos = pr.position(); s.vel = pr.getDeltaMovement(); s.noGrav = pr.isNoGravity();
                SHOTS.put(pr.getUUID(), s);
                pr.setNoGravity(true);
            }
            s.until = Math.max(s.until, now + ticks);
            n++;
        }
        return n;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        if (!FROZEN.isEmpty()) {
            List<Frozen> done = new ArrayList<>();
            for (Iterator<Frozen> it = FROZEN.values().iterator(); it.hasNext(); ) {
                Frozen f = it.next();
                LivingEntity t = f.e;
                if (!t.isAlive() || t.isRemoved()) {
                    it.remove();
                    if (t.level() instanceof ServerLevel sl) TimeFx.dropHeld(sl, t);
                    continue;
                }
                long now = t.level().getGameTime();
                if (now >= f.until) { it.remove(); done.add(f); continue; }
                pin(f);
            }
            for (Frozen f : done) letGo(f);   // after the map is updated, so held damage is not held again
        }
        if (!SHOTS.isEmpty()) {
            for (Iterator<FrozenShot> it = SHOTS.values().iterator(); it.hasNext(); ) {
                FrozenShot s = it.next();
                if (s.pr.isRemoved()) { it.remove(); continue; }
                if (s.pr.level().getGameTime() >= s.until) {
                    s.pr.setNoGravity(s.noGrav);
                    s.pr.setDeltaMovement(s.vel);
                    s.pr.hurtMarked = true;
                    it.remove();
                    continue;
                }
                s.pr.setDeltaMovement(Vec3.ZERO);
                s.pr.setPos(s.pos.x, s.pos.y, s.pos.z);
                s.pr.hurtMarked = true;
            }
        }
    }

    private static void pin(Frozen f) {
        LivingEntity t = f.e;
        t.setDeltaMovement(Vec3.ZERO);
        t.fallDistance = 0;
        if (t.position().distanceToSqr(f.pos) > 1.0E-4) {
            if (t instanceof ServerPlayer sp) sp.connection.teleport(f.pos.x, f.pos.y, f.pos.z, f.yaw, f.pitch);
            else t.moveTo(f.pos.x, f.pos.y, f.pos.z, f.yaw, f.pitch);
        }
        if (!(t instanceof ServerPlayer)) {
            t.setYRot(f.yaw); t.setXRot(f.pitch); t.setYHeadRot(f.head); t.yBodyRot = f.body;
        }
        if (t instanceof Mob m) m.getNavigation().stop();
        if (t instanceof Creeper c) c.setSwellDir(-1);
        t.hurtMarked = true;
    }

    private static void letGo(Frozen f) {
        TimeFx.frozenMark(f.by.serverLevel(), f.e, 0);
        if (f.bank > 0.05f && f.e.isAlive() && f.by.isAlive()) TimeFx.releaseHeld(f.by, f.e, f.bank);
        if (f.onRelease != null) {
            try { f.onRelease.run(); } catch (Exception ignored) {}
        }
    }

    /** A frozen thing can't hurt anything; and blows on a held target are banked, not dealt. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttack(LivingAttackEvent e) {
        if (FROZEN.isEmpty()) return;
        Entity src = e.getSource().getEntity(), direct = e.getSource().getDirectEntity();
        if (frozen(src) || frozen(direct)) { e.setCanceled(true); return; }
        Frozen f = FROZEN.get(e.getEntity().getUUID());
        if (f != null && f.hold && src == f.by) {
            f.bank += e.getAmount();
            e.setCanceled(true);
            TimeFx.heldBlow(f.by, f.e, f.bank);
        }
    }

    /** A frozen mob can't shoot: anything it fires never appears. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent e) {
        if (FROZEN.isEmpty() || e.getLevel().isClientSide()) return;
        if (e.getEntity() instanceof Projectile pr && frozen(pr.getOwner())) e.setCanceled(true);
    }

    /** A frozen player can't cast. */
    @SubscribeEvent
    public static void onPreCast(SpellPreCastEvent e) {
        if (frozen(e.getEntity())) e.setCanceled(true);
    }

    // ================================================================== Stored Time, Rewind Ward
    private static final Map<UUID, Integer> STORED = new HashMap<>();
    private static final java.util.Set<UUID> SHOWN = new java.util.HashSet<>();
    private static final Map<UUID, Long> LAST_GAIN = new HashMap<>();
    private static final Map<UUID, float[]> WARD = new HashMap<>();   // {until, banked}

    public static boolean holds(ServerPlayer p) {
        ItemStack s = Utils.getPlayerSpellbookStack(p);
        return s != null && !s.isEmpty() && s.is(AddonItemRegistry.TIME_GRIMOIRE.get());
    }

    public static int stored(ServerPlayer p) { return STORED.getOrDefault(p.getUUID(), 0); }

    public static void addStored(ServerPlayer p, int n) {
        int before = stored(p), after = Math.max(0, Math.min(MAX_STORED, before + n));
        if (after == before) return;
        STORED.put(p.getUUID(), after);
        TimeFx.hud(p, after, true);
        if (after == MAX_STORED && before < MAX_STORED) TimeFx.fullStore(p);
    }

    /** Spends {@code n} stored seconds if there are that many. */
    public static boolean spend(ServerPlayer p, int n) {
        int s = stored(p);
        if (s < n) return false;
        addStored(p, -n);
        return true;
    }

    public static void ward(ServerPlayer p, int ticks) {
        WARD.put(p.getUUID(), new float[]{p.serverLevel().getGameTime() + ticks, 0f});
    }

    /** Ends the ward and returns what it banked. */
    public static float endWard(ServerPlayer p) {
        float[] w = WARD.remove(p.getUUID());
        return w == null ? 0f : w[1];
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onHurt(LivingHurtEvent e) {
        if (e.isCanceled()) return;
        if (e.getEntity() instanceof ServerPlayer hurt) {
            float[] w = WARD.get(hurt.getUUID());
            if (w != null && hurt.serverLevel().getGameTime() <= w[0]) {
                w[1] += e.getAmount() * 0.5f;
                TimeFx.wardSoak(hurt);
            }
        }
        if (e.getSource().getEntity() instanceof ServerPlayer p && e.getEntity() != p && holds(p)) {
            long now = p.serverLevel().getGameTime();
            Long last = LAST_GAIN.get(p.getUUID());
            if (last == null || now - last >= 8) {
                LAST_GAIN.put(p.getUUID(), now);
                addStored(p, 1);
            }
        }
    }

    // ================================================================== your recent past
    private static final Map<UUID, ArrayDeque<double[]>> PAST = new HashMap<>();   // {time, health, x, y, z, yaw}

    /** Where you were and how hurt, {@code ticksAgo} ago (the oldest kept if that is further back). */
    public static double[] past(ServerPlayer p, int ticksAgo) {
        ArrayDeque<double[]> q = PAST.get(p.getUUID());
        if (q == null || q.isEmpty()) return null;
        long want = p.serverLevel().getGameTime() - ticksAgo;
        double[] best = q.peekFirst();
        for (double[] s : q) { if (s[0] <= want) best = s; else break; }
        return best;
    }

    /** Every sample from {@code ticksAgo} until now, oldest first. */
    public static List<double[]> path(ServerPlayer p, int ticksAgo) {
        List<double[]> out = new ArrayList<>();
        ArrayDeque<double[]> q = PAST.get(p.getUUID());
        if (q == null) return out;
        long from = p.serverLevel().getGameTime() - ticksAgo;
        for (double[] s : q) if (s[0] >= from) out.add(s);
        return out;
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        long now = p.serverLevel().getGameTime();
        if (now % 5 == 0 && p.isAlive()) {
            ArrayDeque<double[]> q = PAST.computeIfAbsent(p.getUUID(), k -> new ArrayDeque<>());
            q.addLast(new double[]{now, p.getHealth(), p.getX(), p.getY(), p.getZ(), p.getYRot()});
            while (q.size() > 26) q.removeFirst();
        }
        if (now % 5 == 0) {   // the stored seconds belong to the book: put it away or swap grimoires and they go, HUD and all
            boolean h = holds(p);
            boolean shown = SHOWN.contains(p.getUUID());
            if (!h && (shown || stored(p) > 0)) { STORED.remove(p.getUUID()); SHOWN.remove(p.getUUID()); TimeFx.hud(p, 0, false); }
            else if (h && (!shown || now % 100 == 0)) { SHOWN.add(p.getUUID()); TimeFx.hud(p, stored(p), true); }
        }
        float[] w = WARD.get(p.getUUID());
        if (w != null && now > w[0] + 40) WARD.remove(p.getUUID());
    }

    @SubscribeEvent
    public static void onBookChange(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) { SHOWN.remove(p.getUUID()); TimeFx.hud(p, 0, false); }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        UUID id = e.getEntity().getUUID();
        STORED.remove(id); SHOWN.remove(id); LAST_GAIN.remove(id); WARD.remove(id); PAST.remove(id);
        Frozen f = FROZEN.remove(id);
        if (f != null) f.until = 0;
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent e) {
        for (FrozenShot s : SHOTS.values()) if (!s.pr.isRemoved()) { s.pr.setNoGravity(s.noGrav); s.pr.setDeltaMovement(s.vel); }
        SHOTS.clear(); FROZEN.clear(); STORED.clear(); LAST_GAIN.clear(); WARD.clear(); PAST.clear();
    }
}
