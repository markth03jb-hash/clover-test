package com.ironspellgrimoire.server;

import static com.ironspellgrimoire.server.FxUtil.*;

import com.ironspellgrimoire.IronSpellGrimoire;
import com.ironspellgrimoire.network.FxPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Vanessa Enoteca's Thread Magic - the eight spells of the Thread Grimoire. Fine, glowing pink-white threads spun
 * from her fingertips: they bind, they pull like puppet strings, they weave nets, cocoons, cages and dresses, they
 * stitch wounds closed - and her strongest, the Red Thread of Fate, rewrites the fate of an attack so it never lands.
 *
 *  1 Binding Threads      - threads shoot from your fingers and wrap the enemy you aim at (and those beside it)
 *  2 Puppet Strings       - strings from above lift the enemy up like a marionette and slam it down
 *  3 Thread Net           - a wide net woven in front of you: stops projectiles and enemies passing through
 *  4 Thread Cocoon        - threads wind round you into a cocoon that soaks up the next hits
 *  5 Stitching            - threads stitch wounds closed: you and your allies nearby heal
 *  6 Battle Dress         - you weave yourself a dress of thread: armour and speed for 30 s
 *  7 Thread Cage          - a great cage of threads closes over an area: nobody inside can leave
 *  8 Red Thread of Fate   - for 12 s fate is rewritten for you and your allies: the first heavy blow each would
 *                           take never lands, and whoever threw it is snared in red thread
 */
@Mod.EventBusSubscriber(modid = IronSpellGrimoire.MODID)
public final class ThreadFx {
    private ThreadFx() {}

    static final int T = 16;                                   // thread pink
    private static final int K = FxPacket.KEEP_SHAPE;          // threads are crisp solid lines
    private static final int FATE = Fx.CRIMSON;                // the red thread

    // ------------------------------------------------------------------ state
    private static final Map<UUID, long[]> BOUND = new HashMap<>();          // entity -> {until}
    private static final Map<UUID, Vec3> BOUND_AT = new HashMap<>();
    private static final Map<UUID, float[]> COCOON = new HashMap<>();        // player -> {shield left, until}
    private static final Map<UUID, long[]> FATE_GUARD = new HashMap<>();     // entity -> {until, used(0/1)}
    private static final UUID DRESS_ARMOR = UUID.fromString("5a1d7e20-61f0-4c7e-9a3b-0d1e2f3a4b01");
    private static final UUID DRESS_TOUGH = UUID.fromString("5a1d7e20-61f0-4c7e-9a3b-0d1e2f3a4b02");
    private static final UUID DRESS_SPEED = UUID.fromString("5a1d7e20-61f0-4c7e-9a3b-0d1e2f3a4b03");

    private static final class Net { ServerPlayer owner; Vec3 c, n, side; double w, h; long until; }
    private static final class Cage { ServerPlayer owner; Vec3 c; double r; long until; float dmg; }
    private static final List<Net> NETS = new ArrayList<>();
    private static final List<Cage> CAGES = new ArrayList<>();

    // ------------------------------------------------------------------ helpers
    static void thread(ServerLevel sl, Vec3 a, Vec3 b, int life) { FxSend.beam(sl, T | K, a, b, 0.022f, life); }
    static void redThread(ServerLevel sl, Vec3 a, Vec3 b, int life) { FxSend.beam(sl, FATE | K, a, b, 0.03f, life); }

    static Vec3 fingers(ServerPlayer p) {
        Vec3 look = p.getLookAngle();
        return p.getEyePosition().add(look.scale(0.6)).add(rightOf(look).scale(0.3)).add(0, -0.35, 0);
    }

    /** Threads wrapped round a body: a few thin rings at different heights. */
    static void wrap(ServerLevel sl, LivingEntity t, int pal, int life) {
        float w = Math.max(0.35f, t.getBbWidth() * 0.65f);
        for (int i = 0; i < 4; i++) {
            Vec3 at = t.position().add(0, t.getBbHeight() * (0.2 + i * 0.22), 0);
            FxSend.ring(sl, pal | K, at, w, w + 0.04f, 0.015f, life);
        }
    }

    static void bind(LivingEntity t, int ticks) {
        long until = t.level().getGameTime() + ticks;
        BOUND.put(t.getUUID(), new long[]{until});
        BOUND_AT.put(t.getUUID(), t.position());
    }

    public static boolean bound(Entity e) {
        long[] b = BOUND.get(e.getUUID());
        return b != null && e.level().getGameTime() <= b[0];
    }

    // ================================================================== 1. Binding Threads
    public static void bindingThreads(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        LivingEntity main = aimed(p, 28, 18);
        Vec3 from = fingers(p);
        sound(sl, p.position(), SoundEvents.CROSSBOW_SHOOT, 0.8f, 1.7f);
        if (main == null) {   // threads fly out and fall slack
            Vec3 end = clip(sl, p, from, from.add(p.getLookAngle().scale(20)));
            for (int i = 0; i < 5; i++) thread(sl, from, end.add((i - 2) * 0.15, (i % 2) * 0.1, 0), 8);
            return;
        }
        List<LivingEntity> hit = new ArrayList<>();
        hit.add(main);
        for (LivingEntity t : enemies(sl, p, main.getBoundingBox().inflate(3.5))) if (t != main && hit.size() < 4) hit.add(t);
        for (LivingEntity t : hit) {
            double d = from.distanceTo(mid(t));
            int delay = (int) Math.min(6, d / 6);   // the threads take a moment to fly there
            AstaFx.later(Math.max(1, delay), () -> {
                if (!t.isAlive() || !alive(p)) return;
                for (int i = 0; i < 6; i++) {
                    Vec3 off = new Vec3((i - 2.5) * 0.06, (i % 3) * 0.25 - 0.25, 0);
                    thread(sl, fingers(p), mid(t).add(off), 60);
                }
                wrap(sl, t, T, 60);
                bind(t, 60);
                AstaFx.damage(p, t, power * (t == main ? 1f : 0.6f), "binding_threads");
                sound(sl, t.position(), SoundEvents.WOOL_PLACE, 1f, 0.7f);
            });
        }
    }

    // ================================================================== 2. Puppet Strings
    public static void puppetStrings(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        LivingEntity t = aimed(p, 24, 16);
        if (t == null) return;
        Vec3 anchor = mid(t).add(0, 7, 0);
        // strings drop from above onto the limbs, one more from your fingers
        for (int i = 0; i < 5; i++) {
            Vec3 limb = t.position().add((i - 2) * 0.18, t.getBbHeight() * (i % 2 == 0 ? 0.85 : 0.35), 0);
            thread(sl, anchor.add((i - 2) * 0.4, 0, 0), limb, 26);
        }
        thread(sl, fingers(p), mid(t), 26);
        bind(t, 26);
        sound(sl, t.position(), SoundEvents.LEASH_KNOT_PLACE, 1f, 1.2f);
        // lifted like a marionette...
        for (int k = 2; k < 14; k++) {
            final int kk = k;
            AstaFx.later(k, () -> {
                if (!t.isAlive()) return;
                BOUND_AT.put(t.getUUID(), t.position().add(0, 0.42, 0));
                t.setDeltaMovement(0, 0.42, 0); t.hurtMarked = true;
                if (kk % 4 == 0) for (int i = 0; i < 3; i++) thread(sl, t.position().add(0, 9 - kk * 0.4, 0).add((i - 1) * 0.5, 0, 0), mid(t), 4);
            });
        }
        // ...and slammed down
        AstaFx.later(15, () -> {
            if (!t.isAlive()) return;
            BOUND.remove(t.getUUID());
            t.setDeltaMovement(0, -3.2, 0); t.hurtMarked = true;
            t.fallDistance = 0;
            sound(sl, t.position(), SoundEvents.LEASH_KNOT_BREAK, 1f, 0.8f);
        });
        AstaFx.later(20, () -> {
            if (!t.isAlive()) return;
            Vec3 g = t.position();
            AstaFx.damage(p, t, power, "puppet_strings");
            Fx.impact(sl, T, g.add(0, 0.3, 0), 1.6f);
            FxSend.ring(sl, T, g.add(0, 0.05, 0), 0.3f, 3.2f, 0.1f, 8);
            FxSend.shake(sl, g, 0.6f, 8, 20);
            sound(sl, g, SoundEvents.GENERIC_EXPLODE, 0.6f, 1.4f);
        });
    }

    // ================================================================== 3. Thread Net
    public static void threadNet(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        Vec3 look = flat(p.getLookAngle());
        Vec3 c = p.position().add(look.scale(3.0)).add(0, 1.8, 0);
        Vec3 side = rightOf(look);
        Net n = new Net();
        n.owner = p; n.c = c; n.n = look; n.side = side; n.w = 4.0; n.h = 2.2; n.until = sl.getGameTime() + 160;
        NETS.add(n);
        int life = 160;
        // the weave: threads across, threads up, and two diagonals - a net, not a wall
        for (int i = -4; i <= 4; i++) {
            Vec3 x0 = c.add(side.scale(i * n.w / 4.0));
            thread(sl, x0.add(0, -n.h, 0), x0.add(0, n.h, 0), life);
        }
        for (int j = -3; j <= 3; j++) {
            Vec3 y0 = c.add(0, j * n.h / 3.0, 0);
            thread(sl, y0.subtract(side.scale(n.w)), y0.add(side.scale(n.w)), life);
        }
        thread(sl, c.subtract(side.scale(n.w)).add(0, -n.h, 0), c.add(side.scale(n.w)).add(0, n.h, 0), life);
        thread(sl, c.subtract(side.scale(n.w)).add(0, n.h, 0), c.add(side.scale(n.w)).add(0, -n.h, 0), life);
        sound(sl, c, SoundEvents.WOOL_PLACE, 1f, 0.6f);
        sound(sl, c, SoundEvents.CROSSBOW_LOADING_END, 0.8f, 1.4f);
    }

    // ================================================================== 4. Thread Cocoon
    public static void cocoon(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        COCOON.put(p.getUUID(), new float[]{8f + power * 0.8f, sl.getGameTime() + 160});
        // threads wind round you, a spiral from the feet up
        for (int k = 0; k < 10; k++) {
            final int kk = k;
            AstaFx.later(k, () -> {
                if (!alive(p)) return;
                double a = kk * 0.9;
                Vec3 base = p.position();
                Vec3 a0 = base.add(Math.cos(a) * 0.7, kk * 0.2, Math.sin(a) * 0.7), a1 = base.add(Math.cos(a + 2.4) * 0.7, kk * 0.2 + 0.3, Math.sin(a + 2.4) * 0.7);
                thread(sl, a0, a1, 150 - kk);
                FxSend.ring(sl, T | K, base.add(0, kk * 0.2, 0), 0.62f + 0.02f * (kk % 3), 0.66f + 0.02f * (kk % 3), 0.02f, 150 - kk);
            });
        }
        sound(sl, p.position(), SoundEvents.WOOL_PLACE, 1f, 1.2f);
    }

    // ================================================================== 5. Stitching
    public static void stitching(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        for (int k = 0; k < 100; k += 10) {
            final int kk = k;
            AstaFx.later(k, () -> {
                if (!alive(p)) return;
                for (LivingEntity a : allies(p, 6)) {
                    a.heal(1.0f + power * 0.08f);
                    Vec3 m = mid(a);
                    // a few stitches across the body: short threads in a zigzag
                    for (int i = 0; i < 3; i++) {
                        double y = -0.3 + i * 0.3;
                        thread(sl, m.add(-0.25, y, 0.0), m.add(0.25, y + 0.15, 0.0), 8);
                    }
                    if (a != p) thread(sl, fingers(p), m, 6);
                }
                if (kk % 20 == 0) sound(sl, p.position(), SoundEvents.WOOL_HIT, 0.7f, 1.5f);
            });
        }
    }

    static List<LivingEntity> allies(ServerPlayer p, double r) {
        List<LivingEntity> out = new ArrayList<>();
        out.add(p);
        for (LivingEntity e : p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(r),
                x -> x != p && x.isAlive() && (x.isAlliedTo(p) || (x instanceof net.minecraft.world.entity.TamableAnimal ta && p.getUUID().equals(ta.getOwnerUUID()))))) out.add(e);
        return out;
    }

    // ================================================================== 6. Battle Dress
    public static void battleDress(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        int dur = 600;
        TimedModifiers.apply(p, Attributes.ARMOR, DRESS_ARMOR, "Battle Dress", 6.0, AttributeModifier.Operation.ADDITION, dur);
        TimedModifiers.apply(p, Attributes.ARMOR_TOUGHNESS, DRESS_TOUGH, "Battle Dress", 3.0, AttributeModifier.Operation.ADDITION, dur);
        TimedModifiers.apply(p, Attributes.MOVEMENT_SPEED, DRESS_SPEED, "Battle Dress", 0.12, AttributeModifier.Operation.MULTIPLY_TOTAL, dur);
        // threads swirl in from all round and weave over you
        for (int k = 0; k < 12; k++) {
            final int kk = k;
            AstaFx.later(k, () -> {
                if (!alive(p)) return;
                double a = kk * 0.52;
                Vec3 out = p.position().add(Math.cos(a) * 2.5, 0.4 + kk * 0.12, Math.sin(a) * 2.5);
                thread(sl, out, mid(p).add(0, (kk % 4) * 0.2 - 0.3, 0), 6);
            });
        }
        AstaFx.later(12, () -> { if (alive(p)) { wrap(sl, p, T, 14); Fx.flash(sl, mid(p), 0.8f); } });
        sound(sl, p.position(), SoundEvents.ARMOR_EQUIP_LEATHER, 1f, 0.8f);
    }

    // ================================================================== 7. Thread Cage
    public static void threadCage(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        LivingEntity t = aimed(p, 30, 20);
        Vec3 c = t != null ? t.position() : clip(sl, p, p.getEyePosition(), p.getEyePosition().add(p.getLookAngle().scale(20)));
        Cage cg = new Cage();
        cg.owner = p; cg.c = c; cg.r = 5.0; cg.until = sl.getGameTime() + 140; cg.dmg = power * 0.35f;
        CAGES.add(cg);
        int life = 140;
        // meridians from the ground ring up over a dome, and rings of latitude
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI / 6;
            Vec3 prev = c.add(Math.cos(a) * cg.r, 0, Math.sin(a) * cg.r);
            for (int k = 1; k <= 4; k++) {
                double el = k * Math.PI / 8;
                Vec3 q = c.add(Math.cos(a) * cg.r * Math.cos(el), cg.r * Math.sin(el), Math.sin(a) * cg.r * Math.cos(el));
                final Vec3 a0 = prev, a1 = q;
                AstaFx.later(k, () -> thread(sl, a0, a1, life - 4));
                prev = q;
            }
        }
        for (int k = 0; k < 3; k++) {
            double el = k * Math.PI / 8, rr = cg.r * Math.cos(el);
            FxSend.ring(sl, T | K, c.add(0, cg.r * Math.sin(el), 0), (float) rr, (float) rr + 0.04f, 0.02f, life);
        }
        sound(sl, c, SoundEvents.WOOL_PLACE, 1f, 0.5f);
        sound(sl, c, SoundEvents.CHAIN_PLACE, 0.8f, 1.4f);
    }

    // ================================================================== 8. Red Thread of Fate
    public static void redThreadOfFate(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        long until = sl.getGameTime() + 240;
        List<LivingEntity> guarded = allies(p, 10);
        for (LivingEntity a : guarded) FATE_GUARD.put(a.getUUID(), new long[]{until, 0});
        // a great wheel of red thread turning overhead, threads running out to everyone it protects
        Vec3 top = p.position().add(0, 4.5, 0);
        for (int i = 0; i < 16; i++) {
            double a0 = i * Math.PI / 8, a1 = (i + 1) * Math.PI / 8;
            redThread(sl, top.add(Math.cos(a0) * 3, 0, Math.sin(a0) * 3), top.add(Math.cos(a1) * 3, 0, Math.sin(a1) * 3), 60);
            if (i % 2 == 0) redThread(sl, top, top.add(Math.cos(a0) * 3, 0, Math.sin(a0) * 3), 60);
        }
        for (LivingEntity a : guarded) redThread(sl, top, mid(a), 60);
        FxSend.ring(sl, FATE, p.position().add(0, 0.05, 0), 0.5f, 10f, 0.1f, 14);
        Fx.flash(sl, top, 1.6f);
        sound(sl, p.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 1.2f, 0.6f);
        sound(sl, p.position(), SoundEvents.BELL_RESONATE, 0.8f, 1.2f);
    }

    /** Fate rewritten: the first heavy blow on a guarded body never lands; the attacker is snared in red thread. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onHurt(LivingHurtEvent e) {
        LivingEntity v = e.getEntity();
        if (v.level().isClientSide) return;
        long now = v.level().getGameTime();
        long[] g = FATE_GUARD.get(v.getUUID());
        if (g != null && now <= g[0] && g[1] == 0 && (e.getAmount() >= 4f || e.getAmount() >= v.getHealth())) {
            g[1] = 1;
            e.setCanceled(true);
            ServerLevel sl = (ServerLevel) v.level();
            Fx.flash(sl, mid(v), 1.0f);
            FxSend.ring(sl, FATE | K, mid(v), 0.4f, 1.6f, 0.03f, 10);
            sound(sl, v.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 1.2f, 0.7f);
            if (e.getSource().getEntity() instanceof LivingEntity atk && atk != v) {
                redThread(sl, mid(v), mid(atk), 40);
                wrap(sl, atk, FATE, 40);
                bind(atk, 40);
            }
            return;
        }
        float[] c = COCOON.get(v.getUUID());
        if (c != null && now <= c[1] && c[0] > 0) {
            float soak = Math.min(c[0], e.getAmount());
            c[0] -= soak;
            e.setAmount(e.getAmount() - soak);
            ServerLevel sl = (ServerLevel) v.level();
            sound(sl, v.position(), SoundEvents.WOOL_HIT, 1f, 0.8f);
            if (c[0] <= 0) { COCOON.remove(v.getUUID()); sound(sl, v.position(), SoundEvents.WOOL_BREAK, 1f, 0.8f); }
            if (e.getAmount() <= 0) e.setCanceled(true);
        }
    }

    // ------------------------------------------------------------------ ticking: bonds, nets, cages
    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        if (!BOUND.isEmpty()) {
            for (Iterator<Map.Entry<UUID, long[]>> it = BOUND.entrySet().iterator(); it.hasNext(); ) {
                var en = it.next();
                Entity ent = null;
                for (ServerLevel sl : server.getAllLevels()) { ent = sl.getEntity(en.getKey()); if (ent != null) break; }
                if (ent == null || !ent.isAlive() || ent.level().getGameTime() > en.getValue()[0]) { it.remove(); BOUND_AT.remove(en.getKey()); continue; }
                Vec3 at = BOUND_AT.get(en.getKey());
                if (at != null && ent.position().distanceToSqr(at) > 0.04) ent.teleportTo(at.x, at.y, at.z);
                ent.setDeltaMovement(0, Math.min(0, ent.getDeltaMovement().y) * 0, 0);
                ent.hurtMarked = true;
            }
        }
        for (Iterator<Net> it = NETS.iterator(); it.hasNext(); ) {
            Net n = it.next();
            ServerLevel sl = n.owner.serverLevel();
            if (!n.owner.isAlive() || sl.getGameTime() > n.until) { it.remove(); continue; }
            AABB box = new AABB(n.c, n.c).inflate(n.w + 1, n.h + 1, n.w + 1);
            for (Projectile pr : sl.getEntitiesOfClass(Projectile.class, box)) {
                if (pr.getOwner() == n.owner) continue;
                if (onNet(n, pr.position(), 1.2)) { Fx.spark(sl, T, pr.position(), 0.6f); pr.discard(); }
            }
            for (LivingEntity t : enemies(sl, n.owner, box)) {
                if (!onNet(n, mid(t), 0.9)) continue;
                double side = mid(t).subtract(n.c).dot(n.n);
                Vec3 push = n.n.scale(side >= 0 ? 0.5 : -0.5);
                t.setDeltaMovement(push.x, 0.1, push.z); t.hurtMarked = true;
            }
        }
        for (Iterator<Cage> it = CAGES.iterator(); it.hasNext(); ) {
            Cage c = it.next();
            ServerLevel sl = c.owner.serverLevel();
            long now = sl.getGameTime();
            if (!c.owner.isAlive() || now > c.until) { it.remove(); continue; }
            for (LivingEntity t : enemies(sl, c.owner, new AABB(c.c, c.c).inflate(c.r + 2))) {
                Vec3 off = t.position().subtract(c.c);
                double d = Math.sqrt(off.x * off.x + off.z * off.z);
                if (d > c.r + 1.5) continue;
                if (d > c.r - 0.8) {   // trying to get out: thrown back in, cut by the threads
                    Vec3 in = new Vec3(-off.x, 0, -off.z).normalize().scale(0.6);
                    t.setDeltaMovement(in.x, 0.15, in.z); t.hurtMarked = true;
                    if (now % 10 == 0) { AstaFx.damage(c.owner, t, c.dmg, "thread_cage"); Fx.spark(sl, T, mid(t), 0.5f); }
                }
            }
        }
    }

    private static boolean onNet(Net n, Vec3 q, double thick) {
        Vec3 o = q.subtract(n.c);
        return Math.abs(o.dot(n.n)) < thick && Math.abs(o.dot(n.side)) < n.w && Math.abs(o.y) < n.h;
    }

    @SubscribeEvent
    public static void onStop(ServerStoppingEvent e) { BOUND.clear(); BOUND_AT.clear(); COCOON.clear(); FATE_GUARD.clear(); NETS.clear(); CAGES.clear(); }
}
