package com.example.arcaneaddon.server;

import com.example.arcaneaddon.ArcaneAddon;
import com.example.arcaneaddon.item.HellfireGrimoireItem;
import com.example.arcaneaddon.network.AddonNetwork;
import com.example.arcaneaddon.network.FxPacket;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.damage.SpellDamageSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Mereoleona Vermillion's Flame Magic - the ten spells of the Hellfire Grimoire and its passive. She fights up close:
 * fists of fire that burn through other magic, her Mana Zone, Mana Skin, and Hellfire Incarnate.
 *
 *  1 Calidos Brachium            - a fist of fire flies out of your punch
 *  2 Calidos Brachium Barrage    - fists of fire hit your target from every side at once
 *  3 Flame Lion Paws             - two great paws of fire seize your target, lift it, and slam it down
 *  4 Lioness Charge              - a dash and a burning punch
 *  5 Mana Skin                   - natural mana hardened over your body: far less damage, no knockback
 *  6 Mana Zone                   - the mana around you serves you: step through the air, read attacks, every move grows
 *  7 Ground-Burning Punch        - punch the ground and it erupts in fire around you
 *  8 Burning Fists Guard         - punch incoming magic and arrows out of the air
 *  9 Hellfire Incarnate          - your body becomes fire: hotter and stronger the longer it lasts, but it costs you
 * 10 Calidos Brachium Purgatory  - a punch of blue flame that engulfs everything in front of you, and all your mana
 *
 * Passive "Wild Lioness": your bare punches burn (+3 damage, 3 s of burning).
 */
@Mod.EventBusSubscriber(modid = ArcaneAddon.MODID)
public class HellfireFx {
    private static final int F = 14, BLUE = 12;   // 14 = crimson flame: hers, not Magna's orange

    private static final Map<UUID, Long> SKIN = new HashMap<>();
    private static final Map<UUID, Long> ZONE = new HashMap<>();
    private static final Map<UUID, Long> ZONE_DODGE = new HashMap<>();
    private static final Map<UUID, Long> GUARD = new HashMap<>();
    private static final Map<UUID, long[]> HELLFIRE = new HashMap<>();   // {start, end}
    private static final Map<UUID, Long> PUNCH_CD = new HashMap<>();
    private static final double ZONE_R = 12;
    private static final int HELL_TICKS = 20 * 20;

    // ------------------------------------------------------------------ helpers
    private static void sound(ServerLevel sl, Vec3 at, SoundEvent s, float v, float pitch) {
        sl.playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, v, pitch);
    }

    private static Vec3 mid(LivingEntity t) { return t.position().add(0, t.getBbHeight() * 0.5, 0); }

    private static Vec3 rightOf(Vec3 dir) {
        Vec3 r = dir.cross(new Vec3(0, 1, 0));
        return r.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : r.normalize();
    }

    private static Vec3 clip(ServerLevel sl, Entity p, Vec3 a, Vec3 b) {
        var hit = sl.clip(new ClipContext(a, b, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        return hit.getType() == HitResult.Type.MISS ? b : hit.getLocation();
    }

    private static List<LivingEntity> enemies(ServerLevel sl, ServerPlayer p, AABB box) {
        return sl.getEntitiesOfClass(LivingEntity.class, box, x -> x != p && x.isAlive() && !x.isAlliedTo(p)
                && !(x instanceof net.minecraft.world.entity.decoration.ArmorStand));
    }

    private static boolean alive(ServerPlayer p) { return p.isAlive() && !p.isRemoved(); }

    private static boolean wearsHellfire(ServerPlayer p) {
        ItemStack s = Utils.getPlayerSpellbookStack(p);
        return s != null && s.getItem() instanceof HellfireGrimoireItem;
    }

    private static Vec3 handPos(ServerPlayer p) {
        double yr = Math.toRadians(p.getYRot());
        double lx = Math.cos(yr), lz = Math.sin(yr), fx = -Math.sin(yr), fz = Math.cos(yr);
        return new Vec3(p.getX() + fx * 0.6 - lx * 0.35, p.getY() + p.getEyeHeight() * 0.8, p.getZ() + fz * 0.6 - lz * 0.35);
    }

    public static boolean inZone(ServerPlayer p) {
        Long z = ZONE.get(p.getUUID());
        return z != null && p.serverLevel().getGameTime() <= z;
    }

    /** Hellfire Incarnate's heat: from x1.3 at the start up to x1.7 at the end. 1 when not active. */
    private static float heat(ServerPlayer p) {
        long[] h = HELLFIRE.get(p.getUUID());
        long now = p.serverLevel().getGameTime();
        if (h == null || now > h[1]) return 1f;
        return 1.3f + 0.4f * (float) Math.min(1.0, (now - h[0]) / (double) HELL_TICKS);
    }

    private static void zoneMsg(ServerPlayer p, String key) {
        p.displayClientMessage(Component.translatable("ui.arcane_addon.lion_zone." + key).withStyle(ChatFormatting.GOLD), true);
    }

    private static LivingEntity aimed(ServerPlayer p, double range, double deg) {
        Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
        LivingEntity best = null;
        double bd = Math.cos(Math.toRadians(deg));
        for (LivingEntity t : enemies(p.serverLevel(), p, p.getBoundingBox().inflate(range))) {
            Vec3 to = mid(t).subtract(eye);
            if (to.length() > range || !p.hasLineOfSight(t)) continue;
            double d = to.normalize().dot(look);
            if (d > bd) { bd = d; best = t; }
        }
        return best;
    }

    private static void hit(ServerPlayer p, LivingEntity t, float dmg, String id) {
        AstaFx.damage(p, t, dmg * heat(p), id);
        FireFx.burn(p, t, 60);
    }

    /** A ball of fire (shape 20) in a palette: 2 = orange fire, 12 = blue flame. */
    private static void ball(ServerLevel sl, int pal, Vec3 c, Vec3 vel, float r, float r2, int life, boolean explode) {
        FxPacket p = new FxPacket();
        if (life <= 3 && life > 0) life = 1;   // per-tick copies: the client moves each along its velocity through its tick
        p.shape = 20; p.palette = (byte) pal; p.life = life; p.c = c; p.v = vel; p.r = r; p.r2 = r2; p.h = explode ? 1 : 0;
        AddonNetwork.CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(c.x, c.y, c.z, 128, sl.dimension())), p);
    }

    private static void shake(ServerLevel sl, Vec3 at, float s, int life) {
        FxPacket p = new FxPacket();
        p.shape = 10; p.c = at; p.r = s; p.life = life;
        AddonNetwork.CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(at.x, at.y, at.z, 24, sl.dimension())), p);
    }

    /** A fist of fire: a burning ball with four knuckle bumps, facing {@code dir}. */
    private static void fist(ServerLevel sl, int pal, Vec3 c, Vec3 dir, float s, Vec3 vel) {
        ball(sl, pal, c, vel, 0.55f * s, 0.55f * s, 2, false);
        Vec3 rt = rightOf(dir), up = rt.cross(dir).normalize();
        for (int k = 0; k < 4; k++) {
            Vec3 kn = c.add(dir.scale(0.4 * s)).add(rt.scale((k - 1.5) * 0.22 * s)).add(up.scale(0.18 * s));
            ball(sl, pal, kn, vel, 0.17f * s, 0.17f * s, 2, false);
        }
    }

    /** Fire gathering on your fist while you wind up. */
    private static void windup(ServerPlayer p, int ticks, int pal) {
        ServerLevel sl = p.serverLevel();
        for (int k = 0; k < ticks; k += 2) {
            final int kk = k;
            AstaFx.later(k, () -> { if (alive(p)) ball(sl, pal, handPos(p), Vec3.ZERO, 0.15f + kk * 0.02f, 0.15f + kk * 0.02f, 3, false); });
        }
        sound(sl, p.position(), SoundEvents.FIRECHARGE_USE, 0.6f, 1.0f);
    }

    // ================================================================== 1. Calidos Brachium
    private static final int BRACHIUM_RELEASE = 5;

    /**
     * Punch: an arm of fire shoots out of your fist, its burning fist flying 16 blocks into the first thing it meets.
     * Mana Zone: three arms, one after another.
     */
    public static void calidosBrachium(ServerPlayer p, float power) {
        windup(p, BRACHIUM_RELEASE, F);
        AstaFx.later(BRACHIUM_RELEASE, () -> {
            if (!alive(p)) return;
            boolean zone = inZone(p);
            if (zone) zoneMsg(p, "calidos_brachium");
            int n = zone ? 3 : 1;
            for (int i = 0; i < n; i++) {
                final int ii = i;
                AstaFx.later(i * 3, () -> {
                    if (!alive(p)) return;
                    Vec3 look = p.getLookAngle();
                    Vec3 side = rightOf(look).scale(n == 1 ? 0 : (ii - 1) * 0.6);
                    Vec3 start = handPos(p).add(side);
                    arm(p, p.serverLevel(), start, start, look, power, 0, new HashSet<>());
                    sound(p.serverLevel(), p.position(), SoundEvents.PLAYER_ATTACK_STRONG, 1f, 0.7f);
                    sound(p.serverLevel(), p.position(), SoundEvents.BLAZE_SHOOT, 1f, 0.8f);
                });
            }
        });
    }

    /** The arm of fire grows from the shoulder 2.6 blocks a tick; the fist leads. */
    private static void arm(ServerPlayer p, ServerLevel sl, Vec3 root, Vec3 pos, Vec3 dir, float dmg, int step, Set<UUID> hit) {
        if (!alive(p) || step > 6) return;
        Vec3 want = pos.add(dir.scale(2.6));
        Vec3 next = clip(sl, p, pos, want);
        FxSend.trail(sl, F, pos, next, 0.45f, 8);   // the arm
        fist(sl, F, next, dir, 1.4f, dir.scale(2.6));
        for (LivingEntity t : enemies(sl, p, new AABB(pos, next).inflate(1.4))) {
            if (!t.getBoundingBox().inflate(0.8).clip(pos, next).isPresent()) continue;
            if (!hit.add(t.getUUID())) continue;
            hit(p, t, dmg, "calidos_brachium");
            t.setDeltaMovement(t.getDeltaMovement().add(dir.scale(1.6)).add(0, 0.35, 0));
            t.hurtMarked = true;
            ball(sl, F, mid(t), Vec3.ZERO, 0.5f, 2.2f, 8, true);
            shake(sl, mid(t), 0.35f, 5);
            sound(sl, t.position(), SoundEvents.GENERIC_EXPLODE, 0.5f, 1.3f);
            return;
        }
        if (next.distanceToSqr(want) > 1.0E-4) { ball(sl, F, next, Vec3.ZERO, 0.5f, 2.0f, 8, true); return; }
        AstaFx.later(1, () -> arm(p, sl, root, next, dir, dmg, step + 1, hit));
    }

    // ================================================================== 2. Calidos Brachium Barrage
    /**
     * Fists of fire appear all around the enemy you aim at (up to 20 blocks) and punch into it from every direction:
     * 8 punches over 1.5 s. Mana Zone: 16, and every enemy inside the zone gets some.
     */
    public static void barrage(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        boolean zone = inZone(p);
        LivingEntity t0 = aimed(p, 20, 20);
        List<LivingEntity> targets = new ArrayList<>();
        if (t0 != null) targets.add(t0);
        if (zone) {
            zoneMsg(p, "barrage");
            for (LivingEntity e : enemies(sl, p, p.getBoundingBox().inflate(ZONE_R))) if (e != t0 && e.distanceTo(p) <= ZONE_R) targets.add(e);
        }
        if (targets.isEmpty()) {
            p.displayClientMessage(Component.translatable("ui.arcane_addon.lion.no_target").withStyle(ChatFormatting.GOLD), true);
            return;
        }
        int n = zone ? 16 : 8;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < n; i++) {
            final LivingEntity t = targets.get(i % targets.size());
            AstaFx.later(3 + i * (zone ? 1 : 2), () -> {
                if (!alive(p) || !t.isAlive()) return;
                Vec3 d = new Vec3(r.nextDouble(-1, 1), r.nextDouble(-0.4, 0.8), r.nextDouble(-1, 1)).normalize();
                Vec3 from = mid(t).add(d.scale(4.5));
                Vec3 in = mid(t).subtract(from).normalize();
                FxSend.trail(sl, F, from, mid(t).subtract(in.scale(0.6)), 0.3f, 6);
                fist(sl, F, mid(t).subtract(in.scale(0.6)), in, 1.1f, Vec3.ZERO);
                hit(p, t, power * 0.15f, "calidos_brachium_barrage");
                ball(sl, F, mid(t), Vec3.ZERO, 0.3f, 1.2f, 6, true);
                sound(sl, t.position(), SoundEvents.PLAYER_ATTACK_STRONG, 0.9f, 0.9f + r.nextFloat() * 0.4f);
            });
        }
        shake(sl, mid(targets.get(0)), 0.25f, 30);
        // the finish: one huge fist of fire from the front, matching the haymaker at the end of the flurry
        final LivingEntity main = targets.get(0);
        AstaFx.later(27, () -> {
            if (!alive(p) || !main.isAlive()) return;
            Vec3 from = p.getEyePosition().add(0, -0.3, 0);
            Vec3 in = mid(main).subtract(from).normalize();
            FxSend.trail(sl, F, from, mid(main), 0.5f, 8);
            fist(sl, F, mid(main).subtract(in.scale(0.8)), in, 2.2f, Vec3.ZERO);
            hit(p, main, power * 0.5f, "calidos_brachium_barrage");
            main.setDeltaMovement(main.getDeltaMovement().add(in.scale(1.4)).add(0, 0.6, 0));
            main.hurtMarked = true;
            ball(sl, F, mid(main), Vec3.ZERO, 0.6f, 3.2f, 10, true);
            shake(sl, mid(main), 0.5f, 6);
            sound(sl, main.position(), SoundEvents.GENERIC_EXPLODE, 0.8f, 1.0f);
        });
    }

    // ================================================================== 3. Flame Lion Paws
    /** Two great paws of fire seize the enemy you aim at, lift it high, and slam it into the ground. */
    public static void lionPaws(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        LivingEntity t = aimed(p, 14, 20);
        if (t == null) {
            p.displayClientMessage(Component.translatable("ui.arcane_addon.lion.no_target").withStyle(ChatFormatting.GOLD), true);
            return;
        }
        boolean zone = inZone(p);
        if (zone) zoneMsg(p, "lion_paws");
        final int GRAB = 4, LIFT = 16, SLAM = 24;
        for (int k = 0; k <= SLAM; k++) {
            final int kk = k;
            AstaFx.later(k, () -> {
                if (!alive(p) || !t.isAlive()) return;
                Vec3 m = mid(t);
                Vec3 side = rightOf(t.position().subtract(p.position()).multiply(1, 0, 1).normalize());
                double close = kk < GRAB ? 1.8 - kk * 0.25 : 0.75;
                for (int s = -1; s <= 1; s += 2) paw(sl, m.add(side.scale(s * close)), side.scale(-s));
                if (kk >= GRAB && kk < LIFT) {   // held and lifted
                    t.setDeltaMovement(0, 0.22, 0);
                    t.hurtMarked = true;
                    t.fallDistance = 0;
                } else if (kk >= LIFT && kk < SLAM) {
                    t.setDeltaMovement(0, 0, 0);
                    t.hurtMarked = true;
                } else if (kk == SLAM) {
                    t.setDeltaMovement(0, -3.0, 0);
                    t.hurtMarked = true;
                }
            });
        }
        sound(sl, t.position(), SoundEvents.RAVAGER_ROAR, 0.6f, 1.4f);
        AstaFx.later(SLAM + 3, () -> {
            if (!alive(p)) return;
            Vec3 g = FxLib.ground(sl, t.position().add(0, 0.5, 0), 12);
            Vec3 at = g != null ? g : t.position();
            hit(p, t, power, "flame_lion_paws");
            for (LivingEntity e : enemies(sl, p, new AABB(at, at).inflate(zone ? 5 : 3))) if (e != t) hit(p, e, power * 0.4f, "flame_lion_paws");
            ball(sl, F, at.add(0, 0.5, 0), Vec3.ZERO, 0.6f, zone ? 4.5f : 3f, 10, true);
            FxLib.shockRing(sl, F, at.add(0, 0.05, 0), zone ? 1.6f : 1.1f);
            shake(sl, at, 0.5f, 8);
            sound(sl, at, SoundEvents.GENERIC_EXPLODE, 1f, 0.8f);
        });
    }

    /** A paw of fire: a palm and four curved claws, reaching along {@code dir}. */
    private static void paw(ServerLevel sl, Vec3 c, Vec3 dir) {
        ball(sl, F, c, Vec3.ZERO, 0.45f, 0.45f, 2, false);
        Vec3 up = new Vec3(0, 1, 0);
        for (int k = 0; k < 4; k++) {
            Vec3 claw = dir.add(up.scale((k - 1.5) * 0.35)).normalize();
            FxSend.spike(sl, F, c.add(claw.scale(0.3)), claw.add(dir.scale(0.5)).normalize(), 0.55f, 0.07f, 2, 0, FxPacket.HOLD);
        }
    }

    // ================================================================== 4. Lioness Charge
    /** Charge 12 blocks in a burst of fire and drive your fist into the first enemy in your way. */
    public static void lionessCharge(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        Set<UUID> done = new HashSet<>();
        boolean[] landed = {false};
        for (int t = 0; t < 7; t++) {
            AstaFx.later(t, () -> {
                if (!alive(p) || landed[0]) return;
                Vec3 look = p.getLookAngle();
                Vec3 v = new Vec3(look.x, Math.max(-0.2, look.y * 0.5), look.z).normalize().scale(1.7);
                p.setDeltaMovement(v);
                p.hurtMarked = true;
                p.fallDistance = 0;
                FxSend.trail(sl, F, p.position().add(0, 1, 0), p.position().add(0, 1, 0).subtract(v.scale(1.2)), 0.5f, 8);
                for (LivingEntity e : enemies(sl, p, p.getBoundingBox().inflate(1.8))) {
                    if (!done.add(e.getUUID())) continue;
                    landed[0] = true;
                    hit(p, e, power, "lioness_charge");
                    e.setDeltaMovement(e.getDeltaMovement().add(v.scale(1.0)).add(0, 0.5, 0));
                    e.hurtMarked = true;
                    ball(sl, F, mid(e), Vec3.ZERO, 0.5f, 2.6f, 9, true);
                    shake(sl, mid(e), 0.4f, 6);
                    p.setDeltaMovement(v.scale(-0.15));
                    sound(sl, e.position(), SoundEvents.GENERIC_EXPLODE, 0.6f, 1.2f);
                    break;
                }
            });
        }
        sound(sl, p.position(), SoundEvents.BLAZE_SHOOT, 1f, 0.6f);
    }

    // ================================================================== 5. Mana Skin
    /** Natural mana hardened over your whole body for 20 s: you take 35% less damage and can't be knocked back. */
    public static void manaSkin(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        SKIN.put(p.getUUID(), sl.getGameTime() + 400);
        sendAura(p, 5, 400);
        FxSend.ring(sl, F, p.position().add(0, 0.05, 0), 0.4f, 2.0f, 0.3f, 8);
        sound(sl, p.position(), SoundEvents.ARMOR_EQUIP_NETHERITE, 1f, 0.8f);
    }

    private static void sendAura(ServerPlayer p, int pal, int life) {
        if (pal == 5) ModeHud.show(p, "mana_skin", life);
        FxPacket pkt = new FxPacket();
        pkt.shape = 14; pkt.id = p.getId(); pkt.palette = (byte) pal; pkt.life = life;
        AddonNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), pkt);
    }

    // ================================================================== 6. Mana Zone
    /**
     * The mana around you (12 blocks, 20 s) serves you: you can step through the air (press Jump in the air, twice
     * before landing), you read attacks and slip one every 5 s, and every other move gets its Mana Zone form.
     */
    public static void manaZone(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        ZONE.put(p.getUUID(), sl.getGameTime() + 400);
        sendZone(p, 400);
        p.displayClientMessage(Component.translatable("ui.arcane_addon.lion_zone.open").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
        sound(sl, p.position(), SoundEvents.BLAZE_AMBIENT, 1f, 0.6f);
        sound(sl, p.position(), SoundEvents.RAVAGER_ROAR, 0.5f, 1.2f);
    }

    private static void sendZone(ServerPlayer p, int life) {
        ModeHud.show(p, "lion_mana_zone", life);
        FxPacket pkt = new FxPacket();
        pkt.shape = 17; pkt.id = p.getId(); pkt.palette = (byte) F; pkt.life = life; pkt.r = (float) ZONE_R;
        AddonNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), pkt);
    }

    // ================================================================== 7. Ground-Burning Punch
    private static final int GROUND_RELEASE = 8;

    /** Drive your fist into the ground: fire erupts in rings around you out to 8 blocks, throwing everything up. Mana Zone: 12. */
    public static void groundPunch(ServerPlayer p, float power) {
        windup(p, GROUND_RELEASE, F);
        AstaFx.later(GROUND_RELEASE, () -> {
            if (!alive(p)) return;
            ServerLevel sl = p.serverLevel();
            boolean zone = inZone(p);
            if (zone) zoneMsg(p, "ground_punch");
            double R = zone ? 12 : 8;
            Vec3 c0 = FxLib.floor(sl, p.position());
            Vec3 c = c0 != null ? c0 : p.position();
            ball(sl, F, c.add(0, 0.3, 0), Vec3.ZERO, 0.6f, 2.5f, 8, true);
            shake(sl, c, 0.6f, 8);
            for (int ring = 1; ring <= (int) (R / 2); ring++) {
                final double rr = ring * 2.0;
                AstaFx.later(ring * 2, () -> {
                    FxSend.wall(sl, F, c.add(0, 0.8, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1), (float) rr, (float) rr + 0.4f, -180, 180, 0.9f, 8, 0, 0);
                    int pillars = (int) (rr * 2.5);
                    for (int k = 0; k < pillars; k++) {
                        double a = Math.PI * 2 * k / pillars + rr;
                        Vec3 g = FxLib.ground(sl, c.add(Math.cos(a) * rr, 1.5, Math.sin(a) * rr), 4);
                        if (g != null) FxSend.spike(sl, F, g, new Vec3(0, 1, 0), 1.6f + (float) (rr * 0.08), 0.25f, 12, 2, FxPacket.GROUND);
                    }
                    for (LivingEntity e : enemies(sl, p, new AABB(c, c).inflate(rr + 1, 3, rr + 1))) {
                        double d = new Vec3(e.getX() - c.x, 0, e.getZ() - c.z).length();
                        if (Math.abs(d - rr) > 1.1) continue;
                        hit(p, e, power * 0.7f, "ground_punch");
                        e.setDeltaMovement(e.getDeltaMovement().add(0, 0.9, 0));
                        e.hurtMarked = true;
                    }
                    sound(sl, c, SoundEvents.GENERIC_EXPLODE, 0.4f, 1.0f + (float) rr * 0.03f);
                });
            }
        });
    }

    // ================================================================== 8. Burning Fists Guard
    /** For 3 s your fists burn through magic: projectiles that come close are punched out of the air, spells from the front are blocked. */
    public static void burningGuard(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        GUARD.put(p.getUUID(), sl.getGameTime() + 60);
        for (int t = 0; t < 60; t += 2) {
            AstaFx.later(t, () -> {
                if (!alive(p)) return;
                for (Projectile pr : sl.getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(3.5))) {
                    if (pr.getOwner() == p) continue;
                    Vec3 at = pr.position();
                    FxSend.trail(sl, F, handPos(p), at, 0.25f, 5);
                    fist(sl, F, at, at.subtract(p.getEyePosition()).normalize(), 0.8f, Vec3.ZERO);
                    ball(sl, F, at, Vec3.ZERO, 0.3f, 1.2f, 6, true);
                    pr.discard();
                    sound(sl, at, SoundEvents.PLAYER_ATTACK_STRONG, 0.8f, 1.2f);
                }
                fist(sl, F, handPos(p), p.getLookAngle(), 0.7f, Vec3.ZERO);
            });
        }
        sound(sl, p.position(), SoundEvents.FIRECHARGE_USE, 1f, 1.2f);
    }

    // ================================================================== 9. Hellfire Incarnate
    /**
     * Your body becomes fire for 20 s, as if it were mana itself: flames pour off you, everything within 3 blocks
     * burns, your fire hits 30% harder - rising to 70% as it burns hotter - and your punches hit like explosions.
     * When it ends your body pays for it: you lose a quarter of your health.
     */
    public static void hellfire(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        for (int k = 0; k < 12; k += 2) {
            final int kk = k;
            AstaFx.later(k, () -> { if (alive(p)) FxSend.ring(sl, F, p.position().add(0, 0.1 + kk * 0.15, 0), 2.5f - kk * 0.15f, 0.4f, 0.3f, 5); });
        }
        AstaFx.later(12, () -> {
            if (!alive(p)) return;
            long now = sl.getGameTime();
            HELLFIRE.put(p.getUUID(), new long[]{now, now + HELL_TICKS});
            sendBody(p, HELL_TICKS);
            sendAura(p, 4, HELL_TICKS);
            ball(sl, F, p.position().add(0, 1, 0), Vec3.ZERO, 0.8f, 4.5f, 12, true);
            FxSend.beam(sl, F, p.position(), p.position().add(0, 12, 0), 0.9f, 10);
            shake(sl, p.position(), 0.6f, 10);
            p.displayClientMessage(Component.translatable("ui.arcane_addon.hellfire.open").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
            sound(sl, p.position(), SoundEvents.BLAZE_AMBIENT, 1.2f, 0.5f);
            sound(sl, p.position(), SoundEvents.GENERIC_EXPLODE, 0.8f, 0.6f);
        });
    }

    private static void sendBody(ServerPlayer p, int life) {
        ModeHud.show(p, "hellfire_incarnate", life);
        FxPacket pkt = new FxPacket();
        pkt.shape = 27; pkt.id = p.getId(); pkt.palette = (byte) F; pkt.life = life;
        AddonNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), pkt);
    }

    // ================================================================== 10. Calidos Brachium Purgatory
    private static final int PURGATORY_RELEASE = 22;

    /**
     * Her strongest spell: blue flame gathers on your fist, then one punch releases a blast of blue fire that engulfs
     * everything in front of you - a 30-block wave that widens as it goes. It takes all of your remaining mana.
     * Mana Zone / Hellfire Incarnate: 45 blocks, wider.
     */
    public static void purgatory(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        for (int k = 0; k < PURGATORY_RELEASE; k += 2) {
            final int kk = k;
            AstaFx.later(k, () -> {
                if (!alive(p)) return;
                Vec3 h = handPos(p);
                ball(sl, BLUE, h, Vec3.ZERO, 0.15f + kk * 0.03f, 0.15f + kk * 0.03f, 3, false);
                ThreadLocalRandom r = ThreadLocalRandom.current();
                double a = r.nextDouble(Math.PI * 2);
                Vec3 from = h.add(Math.cos(a) * 3, r.nextDouble(-1, 2), Math.sin(a) * 3);
                FxSend.trail(sl, F, from, from.add(h.subtract(from).scale(0.7)), 0.12f, 4);
            });
        }
        sound(sl, p.position(), SoundEvents.BEACON_ACTIVATE, 0.8f, 0.6f);
        sound(sl, p.position(), SoundEvents.BLAZE_AMBIENT, 1f, 0.4f);
        AstaFx.later(PURGATORY_RELEASE, () -> {
            if (!alive(p)) return;
            boolean big = inZone(p) || heat(p) > 1f;
            if (big) zoneMsg(p, "purgatory");
            Vec3 dir = p.getLookAngle();
            Vec3 start = handPos(p).add(dir.scale(0.6));
            double reach = big ? 45 : 30;
            float speed = 2.4f;
            Vec3 end = start.add(dir.scale(reach));
            Vec3 rt = rightOf(dir), up = rt.cross(dir).normalize();
            for (LivingEntity t : enemies(sl, p, new AABB(start, end).inflate(big ? 12 : 9))) {
                Vec3 m = mid(t);
                double along = m.subtract(start).dot(dir);
                if (along < 0 || along > reach) continue;
                Vec3 on = start.add(dir.scale(along));
                if (on.distanceTo(m) > 1.5 + along * (big ? 0.3 : 0.24)) continue;
                final LivingEntity tt = t;
                AstaFx.later((int) (along / speed), () -> {
                    if (!tt.isAlive() || !alive(p)) return;
                    hit(p, tt, power, "calidos_brachium_purgatory");
                    tt.setDeltaMovement(tt.getDeltaMovement().add(dir.scale(1.2)).add(0, 0.4, 0));
                    tt.hurtMarked = true;
                });
            }
            // the wave of blue flame: swelling balls of blue fire rolling forward, ringed by fire
            for (int k = 2; k < (int) reach; k += 3) {
                final int kk = k;
                AstaFx.later((int) (k / speed), () -> {
                    float w = (float) (1.2 + kk * (big ? 0.3 : 0.24));
                    Vec3 c = start.add(dir.scale(kk));
                    ball(sl, BLUE, c, dir.scale(0.4), w * 0.6f, w, 12, true);
                    FxSend.wall(sl, F, c, rt, up, w * 0.9f, w * 1.1f, -180, 180, 0.25f, 8, 0, 0);
                });
            }
            ball(sl, BLUE, start, Vec3.ZERO, 0.8f, 2.4f, 8, true);
            shake(sl, p.position(), 0.9f, 14);
            p.setDeltaMovement(p.getDeltaMovement().subtract(dir.scale(0.5)));
            p.hurtMarked = true;
            MagicData.getPlayerMagicData(p).setMana(0);   // it burns through everything she has
            sound(sl, p.position(), SoundEvents.GENERIC_EXPLODE, 1.4f, 0.5f);
            sound(sl, p.position(), SoundEvents.WARDEN_SONIC_BOOM, 0.6f, 0.7f);
            sound(sl, p.position(), SoundEvents.BLAZE_SHOOT, 1.5f, 0.4f);
        });
    }

    // ================================================================== events
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        ServerLevel sl = p.serverLevel();
        long now = sl.getGameTime();
        UUID id = p.getUUID();
        Long s = SKIN.get(id);
        if (s != null) {
            if (now > s) { SKIN.remove(id); if (!HELLFIRE.containsKey(id)) sendAura(p, 5, 0); }
            else if (now % 40 == 0 && !HELLFIRE.containsKey(id)) sendAura(p, 5, (int) (s - now));
        }
        Long z = ZONE.get(id);
        if (z != null) {
            if (now > z) {
                ZONE.remove(id); ZONE_DODGE.remove(id);
                sendZone(p, 0);
                p.displayClientMessage(Component.translatable("ui.arcane_addon.lion_zone.close").withStyle(ChatFormatting.GOLD), true);
            } else {
                if (now % 40 == 0) sendZone(p, (int) (z - now));
                p.fallDistance = 0;
            }
        }
        long[] h = HELLFIRE.get(id);
        if (h != null) {
            if (now > h[1]) {
                HELLFIRE.remove(id);
                sendBody(p, 0);
                sendAura(p, 4, 0);
                p.hurt(p.damageSources().magic(), p.getHealth() * 0.25f);   // the body pays for becoming fire
                p.displayClientMessage(Component.translatable("ui.arcane_addon.hellfire.close").withStyle(ChatFormatting.RED), true);
            } else {
                if (now % 40 == 0) { sendBody(p, (int) (h[1] - now)); sendAura(p, 4, (int) (h[1] - now)); }
                if (now % 20 == 0) {   // everything close to you burns
                    for (LivingEntity t : enemies(sl, p, p.getBoundingBox().inflate(3))) {
                        if (t.distanceTo(p) > 3.2) continue;
                        AstaFx.damage(p, t, 2f * heat(p), "hellfire_incarnate");
                        FireFx.burn(p, t, 40);
                    }
                }
            }
        }
        Long g = GUARD.get(id);
        if (g != null && now > g) GUARD.remove(id);
    }

    @SubscribeEvent
    public static void onAttacked(LivingAttackEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || e.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        ServerLevel sl = p.serverLevel();
        long now = sl.getGameTime();
        Vec3 src = e.getSource().getSourcePosition();
        Long g = GUARD.get(p.getUUID());
        if (g != null && now <= g && src != null && e.getSource() instanceof SpellDamageSource) {
            Vec3 to = src.subtract(p.getEyePosition()).normalize();
            if (to.dot(p.getLookAngle()) > 0.3) {
                e.setCanceled(true);
                ball(sl, F, p.getEyePosition().add(to.scale(1.2)), Vec3.ZERO, 0.3f, 1.4f, 6, true);
                sound(sl, p.position(), SoundEvents.PLAYER_ATTACK_STRONG, 1f, 1.1f);
                return;
            }
        }
        if (inZone(p) && e.getSource().getEntity() != null && e.getSource().getEntity() != p) {   // the zone reads it coming
            Long last = ZONE_DODGE.get(p.getUUID());
            if (last == null || now - last >= 100) {
                ZONE_DODGE.put(p.getUUID(), now);
                e.setCanceled(true);
                Vec3 away = p.position().subtract(e.getSource().getEntity().position()).multiply(1, 0, 1);
                Vec3 side = rightOf(away.lengthSqr() < 1.0E-4 ? p.getLookAngle() : away.normalize());
                Vec3 dest = p.position().add(side.scale(1.6));
                if (sl.noCollision(p, p.getBoundingBox().move(dest.subtract(p.position())))) {
                    FxSend.trail(sl, F, p.position().add(0, 1, 0), dest.add(0, 1, 0), 0.3f, 6);
                    p.connection.teleport(dest.x, dest.y, dest.z, p.getYRot(), p.getXRot());
                }
                sound(sl, p.position(), SoundEvents.BLAZE_SHOOT, 0.4f, 1.8f);
            }
        }
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent e) {
        // Mana Skin
        if (e.getEntity() instanceof ServerPlayer v) {
            Long s = SKIN.get(v.getUUID());
            if (s != null && v.serverLevel().getGameTime() <= s) e.setAmount(e.getAmount() * 0.65f);
        }
        // Wild Lioness / Hellfire Incarnate: punches burn and blow up
        if (!(e.getSource().getEntity() instanceof ServerPlayer p) || e.getSource().getDirectEntity() != p) return;
        if (e.getSource() instanceof SpellDamageSource || !wearsHellfire(p)) return;
        long now = p.serverLevel().getGameTime();
        Long cd = PUNCH_CD.get(p.getUUID());
        if (cd != null && now - cd < 5) return;
        PUNCH_CD.put(p.getUUID(), now);
        boolean hell = HELLFIRE.containsKey(p.getUUID());
        e.setAmount(e.getAmount() + (hell ? 7f : 3f));
        FireFx.burn(p, e.getEntity(), 60);
        if (hell) {
            ball(p.serverLevel(), F, mid(e.getEntity()), Vec3.ZERO, 0.4f, 2.0f, 7, true);
            Vec3 push = e.getEntity().position().subtract(p.position()).multiply(1, 0, 1);
            if (push.lengthSqr() > 1.0E-4) e.getEntity().setDeltaMovement(e.getEntity().getDeltaMovement().add(push.normalize().scale(0.8)).add(0, 0.3, 0));
            sound(p.serverLevel(), e.getEntity().position(), SoundEvents.GENERIC_EXPLODE, 0.4f, 1.4f);
        }
    }

    @SubscribeEvent
    public static void onKnockback(net.minecraftforge.event.entity.living.LivingKnockBackEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            Long s = SKIN.get(p.getUUID());
            if (s != null && p.serverLevel().getGameTime() <= s) e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        UUID id = e.getEntity().getUUID();
        SKIN.remove(id); ZONE.remove(id); ZONE_DODGE.remove(id); GUARD.remove(id); HELLFIRE.remove(id); PUNCH_CD.remove(id);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent e) {
        SKIN.clear(); ZONE.clear(); ZONE_DODGE.clear(); GUARD.clear(); HELLFIRE.clear(); PUNCH_CD.clear();
    }
}
