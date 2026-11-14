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
 *  1 Crimson Edge     -> Frost Step:      break into ice and re-form 9 blocks ahead, cutting through all between; a statue shatters behind
 *  2 Hemorrhage       -> Icicle Rain:     fourteen great icicles fall on where you aim
 *  3 Sanguine Volley  -> Frost Sentries:  three crystals hang round you for 8 s, shooting the nearest foe
 *  4 Transfusion      -> Glacier Prison:  a ring of ice pillars holds the foe you aim at (and its neighbours), then crushes in
 *  5 Blood Pact       -> Snow Veil:       become a whirl of snow for 3 s: untouchable, faster, a blizzard round you
 *  6 Red Tide         -> Frost Nova:      leap on a column of ice and slam down - a ring of frost and spires flings foes
 *  7 Thorned Veins    -> Ice Serpent:     a serpent of ice burrows through the ground, hunting and biting
 *  8 Crimson Bulwark  -> Glacier Wall:    a wall of ice they can't pass; it explodes outward when it ends
 *  9 Exsanguinate     -> Absolute Zero:   frost spreads 10 blocks round you, everything in it is encased, then it all shatters
 * 10 Scarlet Moon     -> Moonfall:        a comet of ice falls where you aim - a crater, a crown of spires, everything frozen
 * Every ice hit chills; the next ice hit on a chilled foe Shatters (+25%).
 */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = "iron_spell_grimoire")
public final class IceFx {
    private IceFx() {}

    /** Frozen ground left by frost fields: foes on it slide and slow, you hit 20% harder standing on it. */
    private record Frozen(net.minecraft.server.level.ServerLevel level, Vec3 c, double r, long until) {}
    private static final java.util.List<Frozen> FROZEN = new java.util.ArrayList<>();
    /** Cracked Armour: bosses can't be encased - the ice cracks their armour instead (+15% damage taken). */
    private static final Map<UUID, Long> CRACKED = new HashMap<>();

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
        life = Math.max(life, spread + 200);   // the ground stays frozen for 10 s
        if (FROZEN.size() > 40) FROZEN.remove(0);
        FROZEN.add(new Frozen(sl, ground, r, sl.getGameTime() + life));
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
    private static float pw(ServerPlayer p, float power) {
        float k = POWER;
        if (BloodFx.path(p, "scarlet_moon", 2)) k *= 1.15f;   // Cold Blood
        if (onFrozen(p)) k *= 1.2f;                           // standing on your own frozen ground
        return power * k;
    }

    static boolean onFrozen(LivingEntity e) {
        long now = e.level().getGameTime();
        for (Frozen f : FROZEN) if (f.level() == e.level() && f.until() > now && f.c().distanceToSqr(e.position()) < f.r() * f.r() && Math.abs(f.c().y - e.getY()) < 1.5) return true;
        return false;
    }

    static boolean boss(LivingEntity t) { return TimeStop.isBoss(t) || BossCores.isBoss(t.getType()) || t.getMaxHealth() >= 200; }

    /** Chilled foes (hit by ice in the last 3 s): the next ice hit on them Shatters - +25%, and the ice bursts off them. */
    private static final Map<UUID, Long> CHILLED = new HashMap<>();

    private static void hit(ServerPlayer p, LivingEntity t, float amt, String id) {
        ServerLevel sl = p.serverLevel();
        long now = sl.getGameTime();
        boolean shatter = CHILLED.getOrDefault(t.getUUID(), 0L) > now;
        if (shatter) amt *= 1.25f;
        AstaFx.damage(p, t, amt, id);
        FxPacket h = fx(13, mid(t), shatter ? 9 : 6);   // the ice hit: shards break off, a frost star flares (bigger for a Shatter)
        h.d = mid(t).subtract(p.position().add(0, 1, 0)); h.a0 = Math.max(0.5f, Math.min(1.4f, amt / 10f)); h.a1 = shatter ? 1 : 0;
        send(sl, h);
        if (shatter) {
            CHILLED.remove(t.getUUID());
            BloodFx.play(sl, mid(t), "ice_shatter", 0.9f, 1.2f + ThreadLocalRandom.current().nextFloat() * 0.2f);
        } else CHILLED.put(t.getUUID(), now + 60);
        if (CHILLED.size() > 300) CHILLED.values().removeIf(u -> u < now);
        chill(t, 60);
    }

    private static void chill(LivingEntity t, int ticks) {
        TimedModifiers.apply(t, Attributes.MOVEMENT_SPEED, TimedModifiers.FROST_SLOW, "Frozen", -0.55, AttributeModifier.Operation.MULTIPLY_TOTAL, ticks);
    }

    /** Encased: held fast in ice for {@code ticks}, then it shatters for {@code burst} damage. */
    private static void encase(ServerPlayer p, LivingEntity t, int ticks, float burst, String id) {
        ServerLevel sl = p.serverLevel();
        ticks += Math.round(BloodFx.tree(p, "encase"));   // Hard Freeze
        boolean big = boss(t);
        FxPacket e = fx(4, t.position(), ticks);
        e.v = new Vec3(t.getId(), 0, 0); e.a0 = big ? 1 : 0;   // a boss: only a crust of ice on it
        send(sl, e);
        if (big) {   // a boss can't be held: its armour cracks instead, and the shatter lands for half
            CRACKED.put(t.getUUID(), sl.getGameTime() + ticks + 60);
            burst *= 0.5f;
            if (p.tickCount % 3 == 0) p.displayClientMessage(Component.literal("Cracked Armour: +15% damage taken").withStyle(ChatFormatting.AQUA), true);
        } else {
            TimedModifiers.apply(t, Attributes.MOVEMENT_SPEED, TimedModifiers.FROST_ROOT, "Encased in ice", -1.0, AttributeModifier.Operation.MULTIPLY_TOTAL, ticks);
            t.setDeltaMovement(0, Math.min(0, t.getDeltaMovement().y), 0);
            t.hurtMarked = true;
        }
        BloodFx.play(sl, t.position(), "freeze", 1.0f, 1.0f);
        final float shatter = burst;
        AstaFx.later(ticks, () -> {
            if (!t.isAlive()) return;
            TimedModifiers.remove(t, Attributes.MOVEMENT_SPEED, TimedModifiers.FROST_ROOT);
            if (alive(p)) hit(p, t, shatter, id);
            BloodFx.play(sl, t.position(), "ice_shatter", 1.3f, 0.9f + ThreadLocalRandom.current().nextFloat() * 0.2f);
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
        BloodFx.play(sl, at, "ice_crack", 1.2f, pitch);
        BloodFx.play(sl, at, "freeze", 0.7f, pitch * 1.1f);
    }

    // ================================================================== frozen ground, cracked armour
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onServerTick(net.minecraftforge.event.TickEvent.ServerTickEvent e) {
        if (e.phase != net.minecraftforge.event.TickEvent.Phase.END || FROZEN.isEmpty()) return;
        Frozen any = FROZEN.get(0);
        long now = any.level().getGameTime();
        FROZEN.removeIf(f -> f.until() < now);
        if (now % 5 != 0) return;
        for (Frozen f : FROZEN) {
            AABB box = new AABB(f.c(), f.c()).inflate(f.r(), 1.5, f.r());
            for (LivingEntity t : f.level().getEntitiesOfClass(LivingEntity.class, box, x -> !(x instanceof net.minecraft.world.entity.player.Player) && x.isAlive()
                    && !(x instanceof net.minecraft.world.entity.decoration.ArmorStand) && x.position().distanceToSqr(f.c()) < f.r() * f.r())) {
                TimedModifiers.apply(t, Attributes.MOVEMENT_SPEED, TimedModifiers.FROST_SLOW, "Frozen ground", -0.4, AttributeModifier.Operation.MULTIPLY_TOTAL, 12);
                Vec3 v = t.getDeltaMovement();   // it slides: the way it was going carries on
                if (v.x * v.x + v.z * v.z > 0.0004 && t.onGround()) { t.setDeltaMovement(v.x * 1.6, v.y, v.z * 1.6); t.hurtMarked = true; }
            }
        }
    }

    /** Cracked Armour: +15% damage taken. */
    static void onHurt(net.minecraftforge.event.entity.living.LivingHurtEvent e) {
        if (CRACKED.isEmpty()) return;
        Long until = CRACKED.get(e.getEntity().getUUID());
        if (until == null) return;
        if (until < e.getEntity().level().getGameTime()) { CRACKED.remove(e.getEntity().getUUID()); return; }
        e.setAmount(e.getAmount() * 1.15f);
    }

    // ================================================================== 1. Frost Step
    /**
     * You break into ice and re-form up to 9 blocks ahead: an ice statue of you stays where you stood and shatters,
     * a line of spires bursts up along the way, and everything you passed through is cut and chilled.
     */
    public static void frostStep(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        float pow = pw(p, power) * 1.1f;
        Vec3 d = flatLook(p), from = p.position();
        Vec3 eye = from.add(0, 1.0, 0);
        net.minecraft.world.phys.HitResult hr = sl.clip(new net.minecraft.world.level.ClipContext(eye, eye.add(d.scale(9)),
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, p));
        double dist = hr.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? 9 : Math.max(0, hr.getLocation().distanceTo(eye) - 0.8);
        Vec3 to = ZenonAI.ground(sl, from.add(d.scale(dist)));
        if (Math.abs(to.y - from.y) > 3) to = from.add(d.scale(dist));
        FxPacket st = fx(21, from, 26);   // the statue you leave behind
        st.d = d; st.v = new Vec3(p.getId(), 0, 0);
        send(sl, st);
        p.teleportTo(to.x, to.y, to.z);
        p.fallDistance = 0;
        Set<UUID> struck = new HashSet<>();
        for (int i = 1; i <= (int) dist; i++) {
            Vec3 at = ZenonAI.ground(sl, from.add(d.scale(i)));
            final int ii = i;
            AstaFx.later(i / 2, () -> spire(sl, at.subtract(0, 0.2, 0), new Vec3((ThreadLocalRandom.current().nextDouble() - 0.5) * 0.5, 1, 0).add(d.scale(-0.3)).normalize(),
                    0.7f + ThreadLocalRandom.current().nextFloat() * 0.8f + (ii % 3 == 0 ? 0.6f : 0), 0.14f, 30, 2));
            for (LivingEntity e : enemies(sl, p, new AABB(at, at).inflate(1.3, 2, 1.3)))
                if (struck.add(e.getUUID())) hit(p, e, pow, "frost_step");
        }
        AstaFx.later(20, () -> BloodFx.play(sl, from, "ice_shatter", 1.0f, 1.1f));
        says(p, "Frost Step");
        BloodFx.play(sl, from, "freeze", 1.0f, 1.5f);
        BloodFx.play(sl, to, "ice_crack", 1.0f, 1.3f);
    }

    // ================================================================== 2. Icicle Rain
    /** Great icicles fall out of a clear sky onto where you aim - fourteen of them in a second and a half. */
    public static void icicleRain(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        float pow = pw(p, power) * 0.5f;
        Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
        LivingEntity aim = aimed(p, 32, 12);
        Vec3 at;
        if (aim != null) at = aim.position();
        else {
            net.minecraft.world.phys.HitResult hr = sl.clip(new net.minecraft.world.level.ClipContext(eye, eye.add(look.scale(28)),
                    net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, p));
            at = ZenonAI.ground(sl, hr.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? eye.add(look.scale(14)) : hr.getLocation());
        }
        final Vec3 c = ZenonAI.ground(sl, at);
        FxPacket mark = fx(20, c, 34);   // a ring of cold on the ground where it will fall
        mark.r = 4f; mark.sweep = 6;
        send(sl, mark);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < 14; i++) {
            double an = r.nextDouble() * Math.PI * 2, rr = Math.sqrt(r.nextDouble()) * 3.8;
            Vec3 land = ZenonAI.ground(sl, c.add(Math.cos(an) * rr, 1, Math.sin(an) * rr));
            Vec3 sky = land.add(r.nextDouble(-1.5, 1.5), 14 + r.nextDouble(4), r.nextDouble(-1.5, 1.5));
            int delay = 4 + i * 2, fall = 7;
            AstaFx.later(delay, () -> {
                if (!alive(p)) return;
                FxPacket ic = fx(3, sky, fall + 2);
                ic.u = land; ic.r = 1.6f + r.nextFloat() * 0.8f; ic.speed = fall;
                send(sl, ic);
            });
            AstaFx.later(delay + fall, () -> {
                if (!alive(p)) return;
                spire(sl, land.subtract(0, 0.6, 0), new Vec3(r.nextDouble(-0.3, 0.3), 1, r.nextDouble(-0.3, 0.3)).normalize(), 0.9f + r.nextFloat() * 0.5f, 0.16f, 40, 1);   // it stands in the ground where it struck
                for (LivingEntity e : enemies(sl, p, new AABB(land, land).inflate(1.5, 2.5, 1.5))) hit(p, e, pow, "icicle_rain");
                if (r.nextInt(3) == 0) BloodFx.play(sl, land, "ice_crack", 0.8f, 1.0f + r.nextFloat() * 0.4f);
            });
        }
        says(p, "Icicle Rain");
        BloodFx.play(sl, c, "freeze", 1.2f, 0.7f);
    }

    // ================================================================== 3. Frost Sentries
    /** Three crystals of ice rise and hang round you for 8 s, each loosing a shard at the nearest foe every second. */
    public static void frostSentries(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        float pow = pw(p, power) * 0.4f;
        Vec3 base = p.position();
        Vec3 d = flatLook(p), rt = rightOf(d);
        Vec3[] spots = {base.add(d.scale(1.5)).add(rt.scale(-2)).add(0, 2.2, 0), base.add(d.scale(-1.6)).add(0, 2.8, 0), base.add(d.scale(1.5)).add(rt.scale(2)).add(0, 2.2, 0)};
        for (int s = 0; s < 3; s++) {
            Vec3 at = spots[s];
            FxPacket se = fx(14, at, 164);
            se.r = 0.9f;
            send(sl, se);
            for (int k = 10 + s * 4; k < 160; k += 20) {
                AstaFx.later(k, () -> {
                    if (!alive(p)) return;
                    LivingEntity t = null;
                    double best = 16 * 16;
                    for (LivingEntity e : enemies(sl, p, new AABB(at, at).inflate(16))) { double dd = e.distanceToSqr(at); if (dd < best) { best = dd; t = e; } }
                    if (t == null) return;
                    final LivingEntity tt = t;
                    int flight = Math.max(2, (int) (at.distanceTo(mid(tt)) / 4.5));
                    FxPacket l = fx(3, at, flight + 2);
                    l.u = mid(tt); l.r = 0.9f; l.speed = flight;
                    send(sl, l);
                    AstaFx.later(flight, () -> { if (tt.isAlive() && alive(p)) hit(p, tt, pow, "frost_sentries"); });
                    BloodFx.play(sl, at, "ice_crack", 0.5f, 1.8f);
                });
            }
            AstaFx.later(162, () -> shards(sl, at, Vec3.ZERO, 0.15f, 14, 0.09f));
        }
        says(p, "Frost Sentries");
        BloodFx.play(sl, base, "freeze", 1.0f, 1.2f);
    }

    // ================================================================== 4. Glacier Prison
    /** Pillars of ice burst up in a ring round the foe you aim at, holding it and everything beside it - then crush inward. */
    public static void glacierPrison(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        LivingEntity t = aimed(p, 22, 16);
        if (t == null) { p.displayClientMessage(Component.literal("No one to imprison.").withStyle(ChatFormatting.GRAY), true); return; }
        float pow = pw(p, power);
        Vec3 c = ZenonAI.ground(sl, t.position());
        FxPacket pr = fx(15, c, 56);
        pr.r = 2.6f; pr.r2 = 4.2f;
        send(sl, pr);
        List<LivingEntity> held = enemies(sl, p, new AABB(c, c).inflate(2.8, 3, 2.8));
        for (LivingEntity e : held) {
            if (boss(e)) { CRACKED.put(e.getUUID(), sl.getGameTime() + 100); continue; }
            TimedModifiers.apply(e, Attributes.MOVEMENT_SPEED, TimedModifiers.FROST_ROOT, "Imprisoned in ice", -1.0, AttributeModifier.Operation.MULTIPLY_TOTAL, 50);
            e.setDeltaMovement(Vec3.ZERO); e.hurtMarked = true;
        }
        for (int k = 10; k <= 40; k += 15) AstaFx.later(k, () -> { for (LivingEntity e : held) if (e.isAlive() && alive(p)) hit(p, e, pow * 0.25f, "glacier_prison"); });
        AstaFx.later(50, () -> {   // the walls crush in
            if (!alive(p)) return;
            for (LivingEntity e : enemies(sl, p, new AABB(c, c).inflate(3, 3, 3))) hit(p, e, pow * 1.3f, "glacier_prison");
            BloodFx.play(sl, c, "ice_shatter", 1.5f, 0.6f);
            FxSend.shake(sl, c, 0.4f, 6, 14);
        });
        says(p, "Glacier Prison");
        BloodFx.play(sl, c, "ice_crack", 1.4f, 0.6f);
    }

    // ================================================================== 5. Snow Veil
    /**
     * You come apart into a whirl of snow for 3 s: blows pass through you, you move half again as fast, and the
     * blizzard you've become cuts everything within 3 blocks. You re-form in a burst of frost.
     */
    public static void snowVeil(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        float pow = pw(p, power == 0 ? 8f : power) * 0.2f;
        FxPacket v = fx(18, p.position(), 62);
        v.v = new Vec3(p.getId(), 0, 0);
        com.ironspellgrimoire.network.AddonNetwork.send(net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), v);
        VEIL.put(p.getUUID(), sl.getGameTime() + 60);
        TimedModifiers.apply(p, Attributes.MOVEMENT_SPEED, TimedModifiers.WINTER_ARMOR, "Snow Veil", 0.5, AttributeModifier.Operation.MULTIPLY_BASE, 60);
        for (int k = 5; k <= 60; k += 5) {
            AstaFx.later(k, () -> {
                if (!alive(p)) return;
                for (LivingEntity e : enemies(sl, p, p.getBoundingBox().inflate(3))) hit(p, e, pow, "snow_veil");
            });
        }
        AstaFx.later(60, () -> {
            if (!alive(p)) return;
            frost(sl, ZenonAI.ground(sl, p.position()), 3.5f, 4, 40);
            shards(sl, p.position().add(0, 1, 0), new Vec3(0, 0.15, 0), 0.25f, 18, 0.08f);
            BloodFx.play(sl, p.position(), "ice_crack", 1.0f, 1.2f);
        });
        says(p, "Snow Veil");
        BloodFx.play(sl, p.position(), "freeze", 1.4f, 0.9f);
    }

    private static final Map<UUID, Long> VEIL = new HashMap<>();

    /** Under the Snow Veil blows pass through you. */
    static boolean veiled(ServerPlayer p) { return VEIL.getOrDefault(p.getUUID(), 0L) > p.serverLevel().getGameTime(); }

    // ================================================================== 6. Frost Nova
    /** You leap up on a column of ice and come down hard: a ring of frost and spires bursts out 7 blocks and flings foes. */
    public static void frostNova(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        float pow = pw(p, power) * 1.4f;
        Vec3 start = p.position();
        spire(sl, start.subtract(0, 0.5, 0), new Vec3(0, 1, 0), 2.6f, 0.45f, 18, 2);
        p.setDeltaMovement(p.getDeltaMovement().x * 0.3, 1.05, p.getDeltaMovement().z * 0.3);
        p.hurtMarked = true;
        final boolean[] done = {false};
        for (int k = 6; k <= 44; k++) {
            final int kk = k;
            AstaFx.later(k, () -> {
                if (done[0] || !alive(p)) return;
                if (kk == 10) { p.setDeltaMovement(p.getDeltaMovement().x, -1.6, p.getDeltaMovement().z); p.hurtMarked = true; }   // driven down
                if (!p.onGround() && kk < 44) return;
                done[0] = true;
                p.fallDistance = 0;
                Vec3 c = ZenonAI.ground(sl, p.position());
                FxPacket w = fx(20, c, 22);   // the shockwave of frost
                w.r = 7f; w.sweep = 0; w.a0 = 1;
                send(sl, w);
                frost(sl, c, 6f, 6, 40);
                for (int i = 0; i < 12; i++) {
                    double an = i * Math.PI * 2 / 12;
                    Vec3 o = new Vec3(Math.cos(an), 0, Math.sin(an));
                    final Vec3 at = ZenonAI.ground(sl, c.add(o.scale(2.5 + (i % 2) * 2)));
                    final int ii = i;
                    AstaFx.later(1 + ii % 3, () -> spire(sl, at.subtract(0, 0.3, 0), o.scale(0.6).add(0, 1, 0).normalize(), 1.4f + (float) (ii % 3) * 0.5f, 0.24f, 30, 2));
                }
                for (LivingEntity e : enemies(sl, p, new AABB(c, c).inflate(7, 3, 7))) {
                    if (e.position().distanceTo(c) > 7.2) continue;
                    hit(p, e, pow, "frost_nova");
                    Vec3 away = flat(e.position().subtract(c));
                    e.setDeltaMovement((away.lengthSqr() < 1e-4 ? Vec3.ZERO : away.normalize().scale(0.8)).add(0, 0.8, 0));
                    e.hurtMarked = true;
                }
                FxSend.shake(sl, c, 0.7f, 10, 20);
                BloodFx.play(sl, c, "ice_shatter", 1.6f, 0.6f);
                BloodFx.play(sl, c, "ice_crack", 1.4f, 0.5f);
            });
        }
        says(p, "Frost Nova");
        BloodFx.play(sl, start, "freeze", 1.0f, 1.4f);
    }

    // ================================================================== 7. Ice Serpent
    /** A serpent of ice crystals burrows forward through the ground, hunting the nearest foe and biting up out of the frost. */
    public static void iceSerpent(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        float pow = pw(p, power) * 0.9f;
        final Vec3[] head = {ZenonAI.ground(sl, p.position().add(flatLook(p).scale(1.5)))};
        final Vec3[] dir = {flatLook(p)};
        Set<UUID> bitten = new HashSet<>();
        for (int k = 1; k <= 36; k++) {
            final int kk = k;
            AstaFx.later(k, () -> {
                if (!alive(p)) return;
                LivingEntity t = null;
                double best = 20 * 20;
                for (LivingEntity e : enemies(sl, p, new AABB(head[0], head[0]).inflate(20))) {
                    if (bitten.contains(e.getUUID())) continue;
                    double dd = e.distanceToSqr(head[0]);
                    if (dd < best) { best = dd; t = e; }
                }
                if (t != null) {   // it turns toward its prey, a little at a time
                    Vec3 want = flat(t.position().subtract(head[0]));
                    if (want.lengthSqr() > 1e-4) dir[0] = dir[0].scale(0.6).add(want.normalize().scale(0.4)).normalize();
                }
                double wig = Math.sin(kk * 0.8) * 0.35;
                Vec3 side = new Vec3(-dir[0].z, 0, dir[0].x);
                head[0] = ZenonAI.ground(sl, head[0].add(dir[0].scale(0.9)).add(side.scale(wig * 0.4)));
                spire(sl, head[0].subtract(0, 0.25, 0), dir[0].scale(0.7).add(0, 1, 0).normalize(), 0.5f + 0.25f * (kk % 3), 0.13f + (kk % 4 == 0 ? 0.06f : 0), 22, 2);
                if (kk % 2 == 0) spire(sl, head[0].add(side.scale(0.4)).subtract(0, 0.25, 0), side.add(0, 1.4, 0).normalize(), 0.35f, 0.08f, 18, 2);
                for (LivingEntity e : enemies(sl, p, new AABB(head[0], head[0]).inflate(1.2, 2, 1.2))) {
                    if (!bitten.add(e.getUUID())) continue;
                    hit(p, e, pow, "ice_serpent");   // the bite: jaws of ice snap up round it
                    spire(sl, e.position().add(dir[0].scale(-0.5)).subtract(0, 0.3, 0), dir[0].add(0, 1.2, 0).normalize(), 2.2f, 0.25f, 26, 2);
                    spire(sl, e.position().add(dir[0].scale(0.5)).subtract(0, 0.3, 0), dir[0].scale(-1).add(0, 1.2, 0).normalize(), 2.2f, 0.25f, 26, 2);
                    e.setDeltaMovement(e.getDeltaMovement().add(0, 0.6, 0)); e.hurtMarked = true;
                    BloodFx.play(sl, e.position(), "ice_shatter", 1.0f, 1.3f);
                }
                if (kk % 4 == 0) BloodFx.play(sl, head[0], "ice_crack", 0.6f, 1.5f);
            });
        }
        says(p, "Ice Serpent");
    }

    // ================================================================== 8. Glacier Wall
    /**
     * A wall of ice 7 wide and 4 high bursts up in front of you for 6 s: foes can't come through it (they're thrown
     * back, chilled), and you take 30% less from the front. When it ends it explodes outward in shards.
     */
    public static void glacierWall(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        float pow = pw(p, power == 0 ? 8f : power);
        Vec3 d = flatLook(p);
        Vec3 c = ZenonAI.ground(sl, p.position().add(d.scale(2.5)));
        FxPacket w = fx(17, c, 124);
        w.d = d; w.r = 3.5f; w.r2 = 4f;
        send(sl, w);
        Vec3 side = new Vec3(-d.z, 0, d.x);
        WALLS.put(p.getUUID(), new Object[]{c, d, sl.getGameTime() + 120});
        for (int k = 2; k < 120; k += 2) {
            AstaFx.later(k, () -> {
                if (!alive(p)) return;
                for (LivingEntity e : enemies(sl, p, new AABB(c, c).inflate(4, 3, 4))) {
                    Vec3 rel = e.position().subtract(c);
                    if (Math.abs(rel.dot(side)) > 3.8 || Math.abs(rel.dot(d)) > 1.0) continue;
                    double front = Math.signum(rel.dot(d));
                    e.setDeltaMovement(d.scale((front == 0 ? 1 : front) * 0.7).add(0, 0.2, 0));   // thrown back off the ice
                    e.hurtMarked = true;
                    chill(e, 30);
                }
            });
        }
        AstaFx.later(120, () -> {   // it bursts outward
            WALLS.remove(p.getUUID());
            if (!alive(p)) return;
            for (LivingEntity e : enemies(sl, p, new AABB(c, c).inflate(5, 3, 5))) {
                if (e.position().subtract(c).dot(d) < -0.5) continue;
                hit(p, e, pow * 0.9f, "glacier_wall");
                e.setDeltaMovement(d.scale(1.0).add(0, 0.4, 0)); e.hurtMarked = true;
            }
            BloodFx.play(sl, c, "ice_shatter", 1.6f, 0.7f);
        });
        says(p, "Glacier Wall");
        BloodFx.play(sl, c, "ice_crack", 1.5f, 0.6f);
    }

    private static final Map<UUID, Object[]> WALLS = new HashMap<>();

    /** Glacier Wall: hits coming from the wall's far side are lessened; Snow Veil: hits pass through. */
    static float guard(ServerPlayer p, net.minecraft.world.damagesource.DamageSource src, float amt) {
        if (veiled(p)) return 0f;
        Object[] w = WALLS.get(p.getUUID());
        if (w == null || (long) w[2] < p.serverLevel().getGameTime()) return amt;
        Vec3 from = src.getSourcePosition();
        if (from == null && src.getEntity() != null) from = src.getEntity().position();
        if (from == null) return amt;
        return from.subtract((Vec3) w[0]).dot((Vec3) w[1]) > 0 ? amt * 0.7f : amt;
    }

    // ================================================================== 9. Absolute Zero
    public static void absoluteZero(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        float pow = pw(p, power);
        Vec3 c = p.position();
        Vec3 g = ZenonAI.ground(sl, c);
        FxPacket az = fx(22, g, 70);   // the snowflake, the frozen air, the dome and the crown of spears (IceFxClient kind 22)
        az.r = 10f; az.sweep = 14;
        send(sl, az);
        AstaFx.later(14, () -> { if (alive(p)) frost(sl, g, 10f, 6, 70); });
        for (int k = 2; k < 14; k += 4) { final float pitch = 1.8f - k * 0.06f; AstaFx.later(k, () -> { if (alive(p)) BloodFx.play(sl, c, "freeze", 0.7f, pitch); }); }   // the cold drawing in
        AstaFx.later(14, () -> {
            if (!alive(p)) return;
            List<LivingEntity> foes = enemies(sl, p, new AABB(c, c).inflate(10, 4, 10));
            for (LivingEntity e : foes) {
                if (e.position().distanceTo(c) > 10.5) continue;
                hit(p, e, pow * 0.4f, "absolute_zero");
                encase(p, e, 30, pow * 2.2f, "absolute_zero");
            }
            BloodFx.play(sl, c, "ice_crack", 1.6f, 0.5f);
            FxSend.shake(sl, c, 0.4f, 8, 16);
        });
        says(p, "Absolute Zero");
        coldSound(sl, c, 0.5f);
        sound(sl, c, SoundEvents.BEACON_DEACTIVATE, 0.8f, 1.6f);
    }

    // ================================================================== Ice Throne (an awakened Moon: sneak + Scarlet Moon)
    /**
     * A throne of ice rises under you and holds you 3 blocks up for 8 s; every 3/4 s it looses a lance of ice at the
     * nearest foe within 18 blocks. Once every 30 s.
     */
    public static void iceThrone(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        long now = sl.getGameTime();
        long ready = THRONE.getOrDefault(p.getUUID(), 0L);
        if (now < ready) {
            p.displayClientMessage(Component.literal("The Ice Throne returns in " + (ready - now + 19) / 20 + " s").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        THRONE.put(p.getUUID(), now + 600);
        float pow = pw(p, Math.max(power, 10f)) * 0.55f;
        Vec3 base = ZenonAI.ground(sl, p.position());
        FxPacket th = fx(12, base, 164);
        th.v = new Vec3(p.getId(), 0, 0);
        send(sl, th);
        frost(sl, base, 4f, 8, 160);
        double topY = base.y + 3.0;
        for (int k = 1; k <= 160; k++) {
            final int kk = k;
            AstaFx.later(k, () -> {
                if (!alive(p)) return;
                double y = p.getY(), want = kk < 10 ? base.y + 3.0 * kk / 10.0 : topY;
                Vec3 v = p.getDeltaMovement();
                p.setDeltaMovement(v.x * 0.6, (want - y) * 0.5, v.z * 0.6);   // held up on the throne
                p.hurtMarked = true;
                p.fallDistance = 0;
                if (kk % 15 == 0) {
                    LivingEntity t = null;
                    double best = 18 * 18;
                    for (LivingEntity e : enemies(sl, p, p.getBoundingBox().inflate(18))) {
                        double d = e.distanceToSqr(p);
                        if (d < best) { best = d; t = e; }
                    }
                    if (t != null) {
                        final LivingEntity tt = t;
                        Vec3 from = p.position().add(0, 2.4, 0);
                        int flight = Math.max(2, (int) (from.distanceTo(mid(tt)) / 4));
                        FxPacket l = fx(3, from, flight + 2);
                        l.u = mid(tt); l.r = 1.3f; l.speed = flight;
                        send(sl, l);
                        AstaFx.later(flight, () -> { if (tt.isAlive() && alive(p)) hit(p, tt, pow, "ice_throne"); });
                        BloodFx.play(sl, from, "ice_crack", 0.8f, 1.5f);
                    }
                }
            });
        }
        AstaFx.later(161, () -> { if (alive(p)) { p.fallDistance = 0; shards(sl, p.position(), new Vec3(0, 0.1, 0), 0.3f, 30, 0.12f); BloodFx.play(sl, p.position(), "ice_shatter", 1.6f, 0.7f); } });
        says(p, "ICE THRONE");
        coldSound(sl, base, 0.5f);
    }

    private static final Map<UUID, Long> THRONE = new HashMap<>();

    // ================================================================== 10. Moonfall (Scarlet Moon cast again under the moon)
    public static void moonfall(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        long now = sl.getGameTime();
        long ready = MOONFALL.getOrDefault(p.getUUID(), 0L);
        if (now < ready) {
            p.displayClientMessage(Component.literal("Moonfall returns in " + (ready - now + 19) / 20 + " s").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        MOONFALL.put(p.getUUID(), now + (BloodFx.crown(p) ? 150 : 300));   // Eternal Night: twice as fast
        float pow = pw(p, Math.max(power, 14f)) * 3f;
        Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
        net.minecraft.world.phys.HitResult hr = sl.clip(new net.minecraft.world.level.ClipContext(eye, eye.add(look.scale(30)),
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, p));
        Vec3 hitAt = hr.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? eye.add(look.scale(18)) : hr.getLocation();
        Vec3 at = ZenonAI.ground(sl, hitAt);
        Vec3 back = flatLook(p).scale(-7);
        final int HANG = 12, IMPACT = 26;
        FxPacket cm = fx(23, at.add(back).add(0, 26, 0), 110);   // the moon itself (IceFxClient kind 23)
        cm.u = at; cm.r = 3.2f; cm.sweep = HANG; cm.speed = IMPACT;
        send(sl, cm);
        sound(sl, at, SoundEvents.BEACON_ACTIVATE, 1.5f, 1.8f);
        AstaFx.later(HANG, () -> { if (alive(p)) { BloodFx.play(sl, at, "ice_crack", 1.6f, 0.4f); sound(sl, at, SoundEvents.ELYTRA_FLYING, 0.8f, 1.4f); } });   // it cracks and starts to fall
        AstaFx.later(IMPACT, () -> {
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
            BloodFx.play(sl, at, "ice_shatter", 2.0f, 0.4f);
            coldSound(sl, at, 0.5f);
        });
        says(p, "MOONFALL");
    }
}
