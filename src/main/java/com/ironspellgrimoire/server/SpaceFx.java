package com.ironspellgrimoire.server;

import static com.ironspellgrimoire.server.FxUtil.*;

import com.ironspellgrimoire.IronSpellGrimoire;
import com.ironspellgrimoire.network.AddonNetwork;
import com.ironspellgrimoire.network.FxPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Finral Roulacase's Spatial Magic - the eight spells of the Space Grimoire. He opens gates in space - ovals rimmed
 * in gold-white light with deep violet space inside - and anyone who steps through one comes out of the other.
 *
 *  1 Spatial Gate         - a pair of gates: one before you, one where you look (up to 40 blocks) - for 12 s
 *                           anything that walks into one comes out of the other
 *  2 Short Hop            - a quick gate at you and one 12 blocks ahead: you step through
 *  3 Escort Gate          - you and every ally beside you go through a gate to where you look (up to 50 blocks)
 *  4 Waypoint             - mark this spot with a small gate; cast again within a minute to come back to it
 *  5 Sky Drop             - a gate opens under the enemies you aim at and drops them out of one high in the sky
 *  6 Redirect Gate        - a gate before you for 5 s swallows enemy projectiles and fires them at your enemies
 *                           out of gates beside them
 *  7 Evasion Gate         - for 8 s the next three blows on you meet a gate: you slip through it aside unhurt
 *  8 Space Deletion       - space itself is torn out where you aim: everything there is drawn in and crushed
 */
@Mod.EventBusSubscriber(modid = IronSpellGrimoire.MODID)
public final class SpaceFx {
    private SpaceFx() {}

    static final int S = 17;   // space violet
    private static final AtomicInteger IDS = new AtomicInteger(1_500_000_000);

    // ------------------------------------------------------------------ gates (the client draws them: shape 46)
    private static final class Gate { ServerPlayer owner; Vec3 c, n; float rw, rh; long until; int id; Gate other; }
    private static final List<Gate> PAIRS = new ArrayList<>();
    private static final Map<UUID, Long> PASSED = new HashMap<>();
    private static final class Redirect { ServerPlayer owner; Vec3 c, n; long until; }
    private static final List<Redirect> REDIRECTS = new ArrayList<>();
    private static final Map<UUID, int[]> EVADE = new HashMap<>();        // player -> {charges}
    private static final Map<UUID, Long> EVADE_UNTIL = new HashMap<>();
    private static final class Mark { ResourceKey<Level> dim; Vec3 pos; long until; int id; }
    private static final Map<UUID, Mark> MARKS = new HashMap<>();

    /** Show a gate. h: 0 gate, 1 Space Deletion's tear. Returns its id (kill it with FxSend.kill to close early). */
    static int gate(ServerLevel sl, Vec3 c, Vec3 facing, float rw, float rh, int life, int style) {
        FxPacket p = new FxPacket();
        p.shape = 46; p.id = IDS.getAndIncrement(); p.c = c; p.d = facing.normalize(); p.u = new Vec3(0, 1, 0);
        if (Math.abs(p.d.y) > 0.9) p.u = new Vec3(0, 0, 1);
        p.r = rw; p.r2 = rh; p.life = life; p.h = style;
        AddonNetwork.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(c.x, c.y, c.z, 128, sl.dimension())), p);
        return p.id;
    }

    private static void whoosh(ServerLevel sl, Vec3 at) {
        sound(sl, at, SoundEvents.ENDERMAN_TELEPORT, 0.7f, 1.3f);
        sound(sl, at, SoundEvents.AMETHYST_BLOCK_CHIME, 0.8f, 0.6f);
    }

    /** Where a body can stand near {@code at}: the closest spot (up or down a few blocks) with room for it. */
    static Vec3 safe(ServerLevel sl, Vec3 at, Entity e) {
        BlockPos base = BlockPos.containing(at);
        for (int dy = 0; dy <= 6; dy++) {
            for (int s = -1; s <= 1; s += 2) {
                BlockPos b = base.offset(0, dy * s, 0);
                AABB box = e.getDimensions(e.getPose()).makeBoundingBox(b.getX() + 0.5, b.getY(), b.getZ() + 0.5);
                if (sl.noCollision(e, box) && !sl.getBlockState(b.below()).getCollisionShape(sl, b.below()).isEmpty())
                    return new Vec3(b.getX() + 0.5, b.getY(), b.getZ() + 0.5);
                if (dy == 0) break;
            }
        }
        for (int dy = 0; dy <= 6; dy++) {   // nothing to stand on: at least somewhere with room
            BlockPos b = base.above(dy);
            AABB box = e.getDimensions(e.getPose()).makeBoundingBox(b.getX() + 0.5, b.getY(), b.getZ() + 0.5);
            if (sl.noCollision(e, box)) return new Vec3(b.getX() + 0.5, b.getY(), b.getZ() + 0.5);
        }
        return null;
    }

    static void move(Entity e, Vec3 to, float yaw) {
        if (e instanceof ServerPlayer sp) sp.teleportTo(sp.serverLevel(), to.x, to.y, to.z, yaw, sp.getXRot());
        else { e.teleportTo(to.x, to.y, to.z); e.setYRot(yaw); }
        e.fallDistance = 0;
        e.hurtMarked = true;
    }

    private static Vec3 lookPoint(ServerPlayer p, double range) {
        Vec3 eye = p.getEyePosition();
        return clip(p.serverLevel(), p, eye, eye.add(p.getLookAngle().scale(range)));
    }

    private static float yawOf(Vec3 d) { return (float) Math.toDegrees(Math.atan2(-d.x, d.z)); }

    // ================================================================== 1. Spatial Gate
    public static void spatialGate(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        Vec3 look = flat(p.getLookAngle());
        Vec3 a = p.position().add(look.scale(2.2)).add(0, 1.0, 0);
        Vec3 hit = lookPoint(p, 40);
        Vec3 standB = safe(sl, hit.subtract(p.getLookAngle().scale(0.8)), p);
        if (standB == null) { p.displayClientMessage(Component.literal("No room to open a gate there.").withStyle(ChatFormatting.GRAY), true); return; }
        Vec3 b = standB.add(0, 1.0, 0);
        int life = 240;
        Gate ga = new Gate(), gb = new Gate();
        ga.owner = gb.owner = p; ga.c = a; gb.c = b; ga.n = look.scale(-1); gb.n = look;
        ga.rw = gb.rw = 0.85f; ga.rh = gb.rh = 1.25f;
        ga.until = gb.until = sl.getGameTime() + life;
        ga.id = gate(sl, a, look.scale(-1), 0.85f, 1.25f, life, 0);
        gb.id = gate(sl, b, look, 0.85f, 1.25f, life, 0);
        ga.other = gb; gb.other = ga;
        PAIRS.add(ga); PAIRS.add(gb);
        ModeHud.show(p, "spatial_gate", life);
        whoosh(sl, a); whoosh(sl, b);
    }

    // ================================================================== 2. Short Hop
    public static void shortHop(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        Vec3 look = flat(p.getLookAngle());
        Vec3 to = safe(sl, clip(sl, p, p.position().add(0, 1, 0), p.position().add(0, 1, 0).add(look.scale(12))).subtract(look.scale(0.6)).add(0, -1, 0), p);
        if (to == null) return;
        gate(sl, p.position().add(0, 1, 0).add(look.scale(0.6)), look.scale(-1), 0.7f, 1.1f, 12, 0);
        gate(sl, to.add(0, 1, 0), look, 0.7f, 1.1f, 12, 0);
        whoosh(sl, p.position());
        AstaFx.later(3, () -> { if (alive(p)) { move(p, to, yawOf(look)); whoosh(sl, to); } });
    }

    // ================================================================== 3. Escort Gate
    public static void escortGate(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        Vec3 look = flat(p.getLookAngle());
        Vec3 hit = lookPoint(p, 50);
        Vec3 to = safe(sl, hit.subtract(p.getLookAngle().scale(1.0)), p);
        if (to == null) return;
        List<LivingEntity> group = ThreadFx.allies(p, 6);
        // a wide gate opens before the group, its twin at the far end
        gate(sl, p.position().add(look.scale(1.5)).add(0, 1.3, 0), look.scale(-1), 1.6f, 1.6f, 30, 0);
        gate(sl, to.add(0, 1.3, 0), look, 1.6f, 1.6f, 30, 0);
        whoosh(sl, p.position());
        AstaFx.later(8, () -> {
            int i = 0;
            for (LivingEntity e : group) {
                if (!e.isAlive()) continue;
                double a = i++ * 2.4;
                Vec3 spot = i == 1 ? to : safe(sl, to.add(Math.cos(a) * 1.4, 0, Math.sin(a) * 1.4), e);
                if (spot != null) move(e, spot, yawOf(look));
            }
            whoosh(sl, to);
        });
    }

    // ================================================================== 4. Waypoint
    public static void waypoint(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        Mark m = MARKS.get(p.getUUID());
        long now = sl.getGameTime();
        if (m != null && now <= m.until && m.dim == sl.dimension()) {
            // back through a gate to the mark
            Vec3 look = flat(p.getLookAngle());
            gate(sl, p.position().add(look.scale(0.8)).add(0, 1, 0), look.scale(-1), 0.7f, 1.1f, 12, 0);
            FxSend.kill(sl, m.id, m.pos);
            gate(sl, m.pos.add(0, 1, 0), look, 0.7f, 1.1f, 12, 0);
            MARKS.remove(p.getUUID());
            ModeHud.hide(p, "waypoint");
            whoosh(sl, p.position());
            AstaFx.later(3, () -> { if (alive(p)) { Vec3 s = safe(sl, m.pos, p); if (s != null) { move(p, s, p.getYRot()); whoosh(sl, s); } } });
            return;
        }
        Mark nm = new Mark();
        nm.dim = sl.dimension(); nm.pos = p.position(); nm.until = now + 1200;
        // a gate lying flat on the ground where you stand, big enough to step on
        double groundY = p.onGround() ? p.getY() : sl.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(p.getX()), (int) Math.floor(p.getZ()));
        nm.pos = new Vec3(p.getX(), Math.min(p.getY(), groundY), p.getZ());
        nm.id = gate(sl, nm.pos.add(0, 0.06, 0), new Vec3(0, 1, 0), 1.15f, 1.15f, 1200, 0);
        FxSend.ring(sl, S, nm.pos.add(0, 0.05, 0), 0.2f, 1.6f, 0.05f, 10);
        ModeHud.show(p, "waypoint", 1200);
        MARKS.put(p.getUUID(), nm);
        p.displayClientMessage(Component.literal("Waypoint set - cast again within a minute to return.").withStyle(ChatFormatting.LIGHT_PURPLE), true);
        sound(sl, p.position(), SoundEvents.AMETHYST_BLOCK_PLACE, 1f, 1.2f);
    }

    // ================================================================== 5. Sky Drop
    public static void skyDrop(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        LivingEntity main = aimed(p, 30, 18);
        if (main == null) return;
        List<LivingEntity> hit = new ArrayList<>();
        hit.add(main);
        for (LivingEntity t : enemies(sl, p, main.getBoundingBox().inflate(2.5))) if (t != main && hit.size() < 4) hit.add(t);
        int height = ComboChain.boosted(p, "sky_drop") ? 30 : 18;
        for (LivingEntity t : hit) {
            Vec3 floor = t.position().add(0, 0.05, 0);
            gate(sl, floor, new Vec3(0, 1, 0), Math.max(0.9f, t.getBbWidth()), Math.max(0.9f, t.getBbWidth()), 14, 0);
            whoosh(sl, floor);
            AstaFx.later(6, () -> {
                if (!t.isAlive()) return;
                Vec3 sky = t.position().add(0, height, 0);
                gate(sl, sky.add(0, t.getBbHeight() + 0.3, 0), new Vec3(0, -1, 0), Math.max(0.9f, t.getBbWidth()), Math.max(0.9f, t.getBbWidth()), 14, 0);
                Vec3 s = sl.noCollision(t, t.getBoundingBox().move(0, height, 0)) ? sky : null;
                if (s != null) { move(t, s, t.getYRot()); t.setDeltaMovement(0, -0.6, 0); }
                AstaFx.damage(p, t, power * 0.5f, "sky_drop");
                whoosh(sl, sky);
            });
        }
    }

    // ================================================================== 6. Redirect Gate
    public static void redirectGate(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        Vec3 look = p.getLookAngle();
        Redirect r = new Redirect();
        r.owner = p; r.c = p.getEyePosition().add(look.scale(2.0)); r.n = look; r.until = sl.getGameTime() + 100;
        REDIRECTS.add(r);
        gate(sl, r.c, look, 1.3f, 1.3f, 100, 0);
        whoosh(sl, r.c);
    }

    // ================================================================== 7. Evasion Gate
    public static void evasionGate(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        EVADE.put(p.getUUID(), new int[]{3});
        EVADE_UNTIL.put(p.getUUID(), sl.getGameTime() + 160);
        ModeHud.show(p, "evasion_gate", 160, 3);
        FxSend.ring(sl, S, p.position().add(0, 0.05, 0), 0.3f, 1.6f, 0.05f, 10);
        sound(sl, p.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 1f, 1.4f);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttack(LivingAttackEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        int[] c = EVADE.get(p.getUUID());
        Long until = EVADE_UNTIL.get(p.getUUID());
        if (c == null || until == null || c[0] <= 0 || p.serverLevel().getGameTime() > until) return;
        if (e.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        ServerLevel sl = p.serverLevel();
        Vec3 side = rightOf(p.getLookAngle()).scale(p.getRandom().nextBoolean() ? 3.5 : -3.5);
        Vec3 to = safe(sl, p.position().add(side), p);
        if (to == null) return;
        e.setCanceled(true);
        c[0]--;
        gate(sl, p.position().add(0, 1, 0), side.normalize(), 0.7f, 1.1f, 10, 0);
        gate(sl, to.add(0, 1, 0), side.normalize().scale(-1), 0.7f, 1.1f, 10, 0);
        move(p, to, p.getYRot());
        whoosh(sl, to);
        ModeHud.show(p, "evasion_gate", (int) Math.max(1, until - sl.getGameTime()), c[0]);
        if (c[0] <= 0) { EVADE.remove(p.getUUID()); EVADE_UNTIL.remove(p.getUUID()); ModeHud.hide(p, "evasion_gate"); }
    }

    // ================================================================== 8. Space Deletion
    public static void spaceDeletion(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        LivingEntity t = aimed(p, 36, 14);
        Vec3 c = t != null ? mid(t) : lookPoint(p, 30);
        boolean big = ComboChain.boosted(p, "space_deletion");
        double R = big ? 6.0 : 4.5;
        Vec3 face = p.getEyePosition().subtract(c).normalize();
        Vec3 rt = rightOf(face), up = rt.cross(face).normalize();
        int seed = (int) (sl.getGameTime() & 0xFFFF);
        // 1. space starts to crack round the point: gold cracks spreading through the air, a gate-ring framing it
        int frame = gate(sl, c, face, (float) R, (float) R, 26, 0);
        for (int i = 0; i < 6; i++) {
            double a0 = i * Math.PI / 3;
            Vec3 along = rt.scale(Math.cos(a0)).add(up.scale(Math.sin(a0)));
            final Vec3 al = along; final int ii = i;
            AstaFx.later(i, () -> FxSend.cracks(sl, 8, c.add(al.scale(R * 0.45)), al, face, (float) R * 0.6f, 26, true, seed + ii * 31));
        }
        // 2. the black tear opens in the middle and grows, drawing everything in
        int tear = gate(sl, c, face, 0.3f, 0.3f, 30, 1);
        for (int k = 0; k < 18; k++) {
            final int kk = k;
            AstaFx.later(k, () -> {
                for (LivingEntity e : enemies(sl, p, new AABB(c, c).inflate(R + 2))) {
                    Vec3 in = c.subtract(mid(e));
                    double d = in.length();
                    if (d > R + 2 || d < 0.3) continue;
                    Vec3 v = in.normalize().scale(0.25 + 0.02 * kk);
                    e.setDeltaMovement(v.x, v.y * 0.6, v.z); e.hurtMarked = true;
                }
                if (kk % 4 == 0) gate(sl, c, face, (float) (R * (0.15 + kk / 22.0)), (float) (R * (0.15 + kk / 22.0)), 6, 1);
                if (kk % 6 == 0) FxSend.ring(sl, S, c, (float) R + 1.5f, 0.4f, 0.12f, 6);   // space pulled inward
            });
        }
        sound(sl, c, SoundEvents.WARDEN_SONIC_CHARGE, 1f, 0.6f);
        sound(sl, c, SoundEvents.PORTAL_TRIGGER, 0.6f, 1.6f);
        // 3. deleted: the tear snaps shut on everything inside it, the space shatters outward, then is gone
        AstaFx.later(18, () -> {
            FxSend.kill(sl, tear, c);
            FxSend.kill(sl, frame, c);
            for (LivingEntity e : enemies(sl, p, new AABB(c, c).inflate(R))) {
                if (mid(e).distanceTo(c) > R + e.getBbWidth()) continue;
                AstaFx.damage(p, e, power, "space_deletion");
                e.setDeltaMovement(Vec3.ZERO); e.hurtMarked = true;
            }
            FxSend.impactFrame(sl, c, 3, FxSend.FRAME_FINRAL, 40);
            Fx.flash(sl, c, 2.8f);
            for (int i = 0; i < 10; i++) {   // shards of space flung out
                double a0 = i * Math.PI / 5;
                Vec3 along = rt.scale(Math.cos(a0)).add(up.scale(Math.sin(a0)));
                FxSend.cracks(sl, i % 2 == 0 ? 8 : S, c.add(along.scale(R * 0.8)), along, face, (float) R * 0.5f, 18, false, seed + 500 + i * 17);
            }
            FxSend.ring(sl, S, c, 0.2f, (float) R + 2f, 0.3f, 10);
            FxSend.ring(sl, 8, c, 0.4f, (float) R + 4f, 0.12f, 12);
            FxSend.shake(sl, c, 1.1f, 14, 40);
            sound(sl, c, SoundEvents.GLASS_BREAK, 1.4f, 0.5f);
            sound(sl, c, SoundEvents.GENERIC_EXPLODE, 1.0f, 0.7f);
            // a hollow left behind for a moment: a small dark gate where the space was
            gate(sl, c, face, 0.6f, 0.6f, 14, 1);
        });
    }

    // ------------------------------------------------------------------ ticking: gates carry what walks in, redirects
    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        for (Iterator<Gate> it = PAIRS.iterator(); it.hasNext(); ) {
            Gate g = it.next();
            ServerLevel sl = g.owner.serverLevel();
            long now = sl.getGameTime();
            if (now > g.until || !g.owner.isAlive()) { it.remove(); continue; }
            for (Entity ent : sl.getEntities((Entity) null, new AABB(g.c, g.c).inflate(g.rw + 0.6, g.rh + 0.6, g.rw + 0.6),
                    x -> x instanceof LivingEntity || x instanceof Projectile || x instanceof net.minecraft.world.entity.item.ItemEntity)) {
                Long last = PASSED.get(ent.getUUID());
                if (last != null && now - last < 20) continue;
                Vec3 o = ent.position().add(0, ent.getBbHeight() * 0.5, 0).subtract(g.c);
                if (Math.abs(o.dot(g.n)) > 0.7 || o.subtract(g.n.scale(o.dot(g.n))).length() > Math.max(g.rw, g.rh)) continue;
                Gate out = g.other;
                Vec3 dest = out.c.add(out.n.scale(1.0)).add(0, -ent.getBbHeight() * 0.5, 0);
                Vec3 s = ent instanceof LivingEntity ? safe(sl, dest, ent) : dest;
                if (s == null) continue;
                Vec3 vel = ent.getDeltaMovement();
                PASSED.put(ent.getUUID(), now);
                move(ent, s, yawOf(out.n));
                ent.setDeltaMovement(out.n.scale(Math.max(0.2, vel.length())));
                whoosh(sl, s);
            }
        }
        for (Iterator<Redirect> it = REDIRECTS.iterator(); it.hasNext(); ) {
            Redirect r = it.next();
            ServerLevel sl = r.owner.serverLevel();
            if (sl.getGameTime() > r.until || !r.owner.isAlive()) { it.remove(); continue; }
            for (Projectile pr : sl.getEntitiesOfClass(Projectile.class, new AABB(r.c, r.c).inflate(1.8))) {
                if (pr.getOwner() == r.owner) continue;
                LivingEntity foe = null;
                double best = 30 * 30;
                for (LivingEntity t : enemies(sl, r.owner, r.owner.getBoundingBox().inflate(30))) {
                    double d = t.distanceToSqr(r.owner);
                    if (d < best) { best = d; foe = t; }
                }
                if (foe == null) { Fx.spark(sl, S, pr.position(), 0.6f); pr.discard(); continue; }
                Vec3 side = rightOf(mid(foe).subtract(r.owner.position()).normalize()).scale(3.0);
                Vec3 exit = mid(foe).add(side).add(0, 1.0, 0);
                Vec3 dir = mid(foe).subtract(exit).normalize();
                gate(sl, exit, dir, 0.7f, 0.7f, 10, 0);
                double sp = Math.max(1.2, pr.getDeltaMovement().length());
                pr.teleportTo(exit.x, exit.y, exit.z);
                pr.setOwner(r.owner);
                pr.setDeltaMovement(dir.scale(sp));
                pr.hurtMarked = true;
                whoosh(sl, exit);
            }
        }
        if (PASSED.size() > 512) PASSED.clear();
    }

    @SubscribeEvent
    public static void onStop(ServerStoppingEvent e) { PAIRS.clear(); PASSED.clear(); REDIRECTS.clear(); EVADE.clear(); EVADE_UNTIL.clear(); MARKS.clear(); }
}
