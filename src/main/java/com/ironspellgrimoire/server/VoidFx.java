package com.ironspellgrimoire.server;

import static com.ironspellgrimoire.server.FxUtil.*;

import com.ironspellgrimoire.network.FxPacket;
import com.ironspellgrimoire.registry.AddonSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
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

/**
 * The Void Grimoire (an original grimoire): the magic of taking things away. Its spells don't burn or cut - they
 * erase: a missile in the air, the armour off a foe's back, its strength, a slice of space, and in the end the foe.
 * Everything it erases becomes Nothing, which the book keeps (0-100, shown on your screen) and the last page lets out.
 * Drawn by client/VoidFxClient (FxPacket shape 90): stark black and white - holes of solid black with hard white
 * edges, things flaking away into nothing.
 *
 *  1 Erase            - a wedge of nothing in front of you: missiles in it are gone (each +6 Nothing), foes hurt
 *  2 Null Step        - you stop existing for a moment and start again ten blocks on (nothing can touch you between)
 *  3 Unmake           - the armour of the foe you aim at is erased for 8 s (+10 Nothing)
 *  4 Nullify          - every foe within 6 blocks has its strength taken away: 30% weaker and slower for 6 s
 *                       (+4 Nothing each)
 *  5 Seam             - you cut a line out of space; a moment later it snaps shut, dragging what's on either side in
 *  6 Absence          - 6 s: a sphere round you where nothing exists - missiles that enter it vanish (+4 Nothing),
 *                       foes in it are worn away, and you take 40% less
 *  7 Void Lance       - a beam of nothing straight through everything in its path, unmaking their armour as it goes
 *  8 Delete           - mark the foe you aim at; in two seconds it is deleted if it's below a quarter of its health -
 *                       otherwise it takes a terrible blow (a boss is never deleted outright)
 *  9 Emptiness (mode) - 20 s: you are part nothing yourself - Void spells hit 30% harder and erase twice as much
 * 10 Origin Zero      - let out all the Nothing you've kept: a sphere of white void that grows with it and erases
 *                       everything inside. Surpassing Limits: larger still.
 *
 * Passive "Erasure": every Void hit erases a little of the foe's armour (-6% a hit, up to -42%, for 6 s) and keeps
 * two Nothing.
 */
@Mod.EventBusSubscriber(modid = "iron_spell_grimoire")
public final class VoidFx {
    private VoidFx() {}

    public static final String MODE = "emptiness", ABSENCE = "absence";
    private static final String NOTHING_KEY = "isg_void_nothing";
    private static final UUID UNMADE = UUID.fromString("d28ea390-7eb8-40f5-8288-e195fc000001");
    private static final UUID UNMADE_T = UUID.fromString("d28ea390-7eb8-40f5-8288-e195fc000002");
    private static final UUID NULL_DMG = UUID.fromString("d28ea390-7eb8-40f5-8288-e195fc000003");
    private static final UUID NULL_SPD = UUID.fromString("d28ea390-7eb8-40f5-8288-e195fc000004");
    private static final UUID WORN = UUID.fromString("d28ea390-7eb8-40f5-8288-e195fc000005");
    private static final double TAU = Math.PI * 2;

    // ================================================================== the client shapes (VoidFxClient, shape 90)
    private static FxPacket pk(int kind, Vec3 c, int life) {
        FxPacket p = new FxPacket();
        p.shape = 90; p.id = FxSend.nextId(); p.h = kind; p.c = c; p.life = life; p.a1 = (float) (Math.random() * 1000);
        return p;
    }
    private static void send(ServerLevel sl, FxPacket p) { FxSend.sendNear(sl, p.c, p); }

    static void wedge(ServerLevel sl, Vec3 c, Vec3 d, float r, float half, int life) { FxPacket p = pk(0, c, life); p.d = d; p.r = r; p.a0 = half; send(sl, p); }
    static void blink(ServerLevel sl, Vec3 c, Vec3 u, float h) { FxPacket p = pk(1, c, 14); p.u = u; p.r = h; send(sl, p); }
    static void unmake(ServerLevel sl, LivingEntity t, int life) { FxPacket p = pk(2, t.position(), life); p.v = new Vec3(t.getId(), 1, 0); send(sl, p); }
    static void nullRing(ServerLevel sl, Vec3 c, float r) { FxPacket p = pk(3, c, 14); p.r = r; send(sl, p); }
    static void seam(ServerLevel sl, Vec3 c, Vec3 u, int snap, int life) { FxPacket p = pk(4, c, life); p.u = u; p.sweep = snap; send(sl, p); }
    static void absence(ServerLevel sl, ServerPlayer pl, float r, int life) { FxPacket p = pk(5, pl.position(), life); p.v = new Vec3(pl.getId(), 1, 0); p.r = r; send(sl, p); }
    static void lance(ServerLevel sl, Vec3 c, Vec3 u, float w, int life) { FxPacket p = pk(6, c, life); p.u = u; p.r = w; send(sl, p); }
    static int delete(ServerLevel sl, LivingEntity t, int wait) { FxPacket p = pk(7, t.position(), wait + 12); p.v = new Vec3(t.getId(), 1, 0); p.sweep = wait; send(sl, p); return p.id; }
    static void aura(ServerLevel sl, ServerPlayer pl, int life) { FxPacket p = pk(8, pl.position(), life); p.v = new Vec3(pl.getId(), 1, 0); send(sl, p); }
    static void origin(ServerLevel sl, Vec3 c, float r, int grow, int life) { FxPacket p = pk(9, c, life); p.r = r; p.sweep = grow; send(sl, p); }
    /** 10 motes of nothing drifting from c into a player. */
    static void absorb(ServerLevel sl, Vec3 c, ServerPlayer pl) { FxPacket p = pk(10, c, 16); p.v = new Vec3(pl.getId(), 1, 0); send(sl, p); }
    /** 11 the delete landing: a foe flaking away to nothing. */
    static void erased(ServerLevel sl, LivingEntity t) { FxPacket p = pk(11, t.position(), 24); p.r = t.getBbHeight(); p.r2 = t.getBbWidth(); send(sl, p); }

    // ================================================================== Nothing
    public static float nothing(ServerPlayer p) { return p.getPersistentData().getFloat(NOTHING_KEY); }

    public static void setNothing(ServerPlayer p, float v) {
        p.getPersistentData().putFloat(NOTHING_KEY, Math.max(0f, Math.min(100f, v)));
        sync(p);
    }

    static void keep(ServerPlayer p, float v, Vec3 from) {
        if (v <= 0) return;
        float before = nothing(p);
        setNothing(p, before + v * (emptiness(p) ? 2f : 1f));
        if (from != null) absorb(p.serverLevel(), from, p);
        if (before < 100f && nothing(p) >= 100f) p.displayClientMessage(Component.literal("Nothing is full - Origin Zero is at its strongest").withStyle(ChatFormatting.WHITE), true);
    }

    /** The meter on the player's screen (VoidFxClient, kind 99): a0 = Nothing, r = 1 if the book is equipped. */
    static void sync(ServerPlayer p) {
        if (p.connection == null) return;
        FxPacket h = new FxPacket();
        h.shape = 90; h.h = 99; h.a0 = nothing(p) + 0.001f; h.r = equipped(p) ? 1 : 0; h.life = 1;
        com.ironspellgrimoire.network.AddonNetwork.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> p), h);
    }

    public static boolean equipped(ServerPlayer p) {
        var b = io.redspace.ironsspellbooks.api.util.Utils.getPlayerSpellbookStack(p);
        return b != null && b.is(com.ironspellgrimoire.registry.AddonItemRegistry.VOID_GRIMOIRE.get());
    }

    static void play(ServerLevel sl, Vec3 at, String kind, float vol, float pitch) {
        SoundEvent s = AddonSounds.get(kind);
        if (s != null) sl.playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, vol, pitch);
    }

    static boolean emptiness(ServerPlayer p) { return Moves.inMode(p, MODE); }

    private static final Map<UUID, long[]> WEAR = new HashMap<>();

    /** A Void hit: Erasure wears the armour away and keeps two Nothing. */
    static void hit(ServerPlayer p, LivingEntity t, float amt, String id) {
        if (emptiness(p)) amt *= 1.3f;
        AstaFx.damage(p, t, amt, id);
        long now = p.serverLevel().getGameTime();
        long[] w = WEAR.get(t.getUUID());
        int n = w == null || now > w[1] ? 0 : (int) w[0];
        n = Math.min(7, n + 1);
        WEAR.put(t.getUUID(), new long[]{n, now + 120});
        TimedModifiers.apply(t, Attributes.ARMOR, WORN, "Erased away", -0.06 * n, AttributeModifier.Operation.MULTIPLY_TOTAL, 120);
        keep(p, 2f, null);
        if (WEAR.size() > 300) WEAR.entrySet().removeIf(en -> now > en.getValue()[1]);
    }

    private static int eraseProjectiles(ServerPlayer p, Vec3 c, double r, java.util.function.Predicate<Projectile> also) {
        ServerLevel sl = p.serverLevel();
        int n = 0;
        for (Projectile pr : sl.getEntitiesOfClass(Projectile.class, new AABB(c, c).inflate(r), x -> x.getOwner() != p)) {
            if (pr.position().distanceTo(c) > r || !also.test(pr)) continue;
            erasedAt(sl, pr.position());
            pr.discard();
            n++;
        }
        return n;
    }

    private static void erasedAt(ServerLevel sl, Vec3 at) {
        FxPacket p = pk(11, at.subtract(0, 0.25, 0), 14); p.r = 0.5f; p.r2 = 0.5f; send(sl, p);
        play(sl, at, "erase", 0.5f, 1.6f);
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && Moves.inMode(p, ABSENCE)) e.setAmount(e.getAmount() * 0.6f);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        if (p.tickCount % 40 == 3 && equipped(p)) sync(p);
        if (!Moves.inMode(p, ABSENCE) || p.tickCount % 2 != 0) return;
        ServerLevel sl = p.serverLevel();
        Vec3 c = p.position().add(0, 1, 0);
        int n = eraseProjectiles(p, c, 4.2, x -> true);
        if (n > 0) keep(p, 4f * n, c);
        if (p.tickCount % 20 == 0) for (LivingEntity t : Kit.near(sl, p, c, 4.2)) hit(p, t, 1.5f, "absence");
    }

    private static Vec3 look(ServerPlayer p) {
        Vec3 l = flat(p.getLookAngle());
        return l.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : l.normalize();
    }

    // ================================================================== 1. Erase
    public static void erase(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        Vec3 eye = p.getEyePosition(), lk = p.getLookAngle();
        wedge(sl, eye.add(0, -0.3, 0), lk, 8f, 0.6f, 14);
        int n = eraseProjectiles(p, eye, 8, pr -> { Vec3 to = pr.position().subtract(eye); return to.lengthSqr() < 1.0E-4 || to.normalize().dot(lk) > 0.75; });
        keep(p, 6f * n, eye.add(lk.scale(3)));
        for (LivingEntity t : Kit.near(sl, p, eye, 8)) {
            Vec3 to = mid(t).subtract(eye);
            if (to.lengthSqr() > 1.0E-4 && to.normalize().dot(lk) < 0.75) continue;
            hit(p, t, power * 0.85f, "erase");
        }
        play(sl, eye, "erase", 1.0f, 1.0f);
    }

    // ================================================================== 2. Null Step
    public static void nullStep(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        Vec3 from = p.position(), lk = look(p);
        Vec3 dest = clip(sl, p, p.getEyePosition(), p.getEyePosition().add(lk.scale(10))).subtract(lk.scale(0.5)).subtract(0, p.getEyeHeight(), 0);
        Vec3 safe = SpaceFx.safe(sl, dest, p);
        if (safe == null) safe = from;
        blink(sl, from, safe, p.getBbHeight());
        p.invulnerableTime = Math.max(p.invulnerableTime, 16);
        for (LivingEntity t : Kit.along(sl, p, from.add(0, 1, 0), safe.add(0, 1, 0), 1.2)) hit(p, t, power * 0.5f, "null_step");
        SpaceFx.move(p, safe, p.getYRot());
        play(sl, from, "erase", 0.8f, 0.7f);
        play(sl, safe, "erase", 0.8f, 1.3f);
    }

    // ================================================================== 3. Unmake
    public static void unmakeSpell(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        LivingEntity t = aimed(p, 24, 12);
        if (t == null) { wedge(sl, p.getEyePosition(), p.getLookAngle(), 3f, 0.3f, 8); return; }
        unmake(sl, t, 160);
        TimedModifiers.apply(t, Attributes.ARMOR, UNMADE, "Armour unmade", -1.0, AttributeModifier.Operation.MULTIPLY_TOTAL, 160);
        TimedModifiers.apply(t, Attributes.ARMOR_TOUGHNESS, UNMADE_T, "Armour unmade", -1.0, AttributeModifier.Operation.MULTIPLY_TOTAL, 160);
        hit(p, t, power * 0.7f, "unmake");
        keep(p, 10f, mid(t));
        play(sl, mid(t), "erase", 1.0f, 0.8f);
        sound(sl, mid(t), SoundEvents.ARMOR_EQUIP_IRON, 1.0f, 0.5f);
    }

    // ================================================================== 4. Nullify
    public static void nullify(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        Vec3 c = p.position().add(0, 1, 0);
        nullRing(sl, c, 6f);
        for (LivingEntity t : Kit.near(sl, p, c, 6)) {
            TimedModifiers.apply(t, Attributes.ATTACK_DAMAGE, NULL_DMG, "Nullified", -0.3, AttributeModifier.Operation.MULTIPLY_TOTAL, 120);
            TimedModifiers.apply(t, Attributes.MOVEMENT_SPEED, NULL_SPD, "Nullified", -0.3, AttributeModifier.Operation.MULTIPLY_TOTAL, 120);
            unmake(sl, t, 30);
            hit(p, t, power * 0.45f, "nullify");
            keep(p, 4f, mid(t));
        }
        play(sl, c, "erase", 1.2f, 0.6f);
    }

    // ================================================================== 5. Seam
    public static void seamSpell(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        Vec3 lk = look(p);
        Vec3 c = p.position().add(lk.scale(4)).add(0, 1, 0);
        Vec3 side = rightOf(lk);
        final Vec3 a = c.add(side.scale(-6)), b = c.add(side.scale(6));
        final int SNAP = 12;
        seam(sl, a, b, SNAP, SNAP + 10);
        for (LivingEntity t : Kit.along(sl, p, a, b, 1.0)) hit(p, t, power * 0.6f, "seam");
        play(sl, c, "erase", 1.0f, 1.2f);
        AstaFx.later(SNAP, () -> {   // it snaps shut: everything near the line is dragged into it
            if (!alive(p)) return;
            for (LivingEntity t : Kit.near(sl, p, c, 7)) {
                Vec3 on = closestOnSegment(a, b, mid(t));
                Vec3 to = on.subtract(mid(t));
                if (to.length() > 5) continue;
                t.setDeltaMovement(to.x * 0.35 * GravityFx.give(t), 0.15, to.z * 0.35 * GravityFx.give(t));
                t.hurtMarked = true;
                hit(p, t, power * 0.8f, "seam");
            }
            FxSend.shake(sl, c, 0.35f, 5, 16);
            play(sl, c, "erase", 1.2f, 0.5f);
        });
    }

    // ================================================================== 6. Absence
    public static void absenceSpell(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        final int T = 120;
        absence(sl, p, 4.2f, T);
        Moves.mode(p, ABSENCE, T, 20, pl -> {});
        play(sl, p.position(), "erase", 1.0f, 0.5f);
        sound(sl, p.position(), SoundEvents.BEACON_DEACTIVATE, 0.6f, 1.6f);
    }

    // ================================================================== 7. Void Lance
    public static void voidLance(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        Vec3 from = Kit.hand(p);
        Vec3 to = clip(sl, p, p.getEyePosition(), p.getEyePosition().add(p.getLookAngle().scale(30)));
        lance(sl, from, to, 0.6f, 16);
        int n = eraseProjectiles(p, from.add(to).scale(0.5), from.distanceTo(to) / 2 + 1, pr -> pr.position().distanceTo(closestOnSegment(from, to, pr.position())) < 1.2);
        keep(p, 6f * n, null);
        for (LivingEntity t : Kit.along(sl, p, from, to, 1.1)) {
            TimedModifiers.apply(t, Attributes.ARMOR, UNMADE, "Armour unmade", -1.0, AttributeModifier.Operation.MULTIPLY_TOTAL, 60);
            hit(p, t, power * 1.1f, "void_lance");
        }
        play(sl, from, "erase", 1.2f, 0.9f);
        sound(sl, from, SoundEvents.BEACON_POWER_SELECT, 0.6f, 1.8f);
    }

    // ================================================================== 8. Delete
    public static void deleteSpell(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        LivingEntity t = aimed(p, 26, 10);
        if (t == null) { wedge(sl, p.getEyePosition(), p.getLookAngle(), 3f, 0.3f, 8); return; }
        final LivingEntity tt = t;
        final int W = 40;
        delete(sl, t, W);
        hit(p, t, power * 0.3f, "delete");
        play(sl, mid(t), "erase", 0.7f, 1.8f);
        AstaFx.later(W, () -> {
            if (!alive(p) || !tt.isAlive()) return;
            if (!IceFx.boss(tt) && tt.getHealth() <= tt.getMaxHealth() * 0.25f) {   // deleted
                erased(sl, tt);
                tt.hurt(p.damageSources().playerAttack(p), Float.MAX_VALUE);
                keep(p, 20f, mid(tt));
                play(sl, mid(tt), "erase", 1.4f, 0.4f);
                p.displayClientMessage(Component.literal("Deleted").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD), true);
            } else {
                hit(p, tt, power * (IceFx.boss(tt) ? 2.2f : 1.6f), "delete");
                unmake(sl, tt, 30);
                play(sl, mid(tt), "erase", 1.2f, 0.7f);
            }
        });
    }

    // ================================================================== 9. Emptiness (mode)
    public static void emptinessMode(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        final int T = 400;
        aura(sl, p, T);
        Moves.mode(p, MODE, T, 20, pl -> {});
        Moves.stance(p, 0, 0, 0.15, 0.3, 0, T);
        FxSend.impactFrame(sl, p.position().add(0, 1, 0), 2, FxSend.FRAME_YAMI, 16);
        play(sl, p.position(), "erase", 1.4f, 0.4f);
    }

    // ================================================================== 10. Origin Zero
    public static void originZero(ServerPlayer p, float power) {
        boolean limit = LimitBreak.active(p);
        ServerLevel sl = p.serverLevel();
        float store = nothing(p);
        setNothing(p, 0);
        final float k = 1f + store / 100f;
        final float pow = power * k * (limit ? 1.3f : 1f) * (emptiness(p) ? 1.3f : 1f);
        final Vec3 g = InkFx.target(p, 28).add(0, 1, 0);
        final float R = (4f + store / 12f) * (limit ? 1.35f : 1f);
        final int GROW = 18;
        origin(sl, g, R, GROW, GROW + 20);
        play(sl, g, "erase", 1.6f, 0.3f);
        sound(sl, g, SoundEvents.BEACON_ACTIVATE, 1.0f, 0.4f);
        AstaFx.later(GROW, () -> {
            if (!alive(p)) return;
            eraseProjectiles(p, g, R, x -> true);
            for (LivingEntity t : Kit.near(sl, p, g, R)) {
                AstaFx.damage(p, t, pow * 1.6f, "origin_zero");
                TimedModifiers.apply(t, Attributes.ARMOR, UNMADE, "Armour unmade", -1.0, AttributeModifier.Operation.MULTIPLY_TOTAL, 100);
            }
            FxSend.impactFrame(sl, g, 3, FxSend.FRAME_YAMI, 24);
            FxSend.shake(sl, g, 0.8f, 12, 34);
            play(sl, g, "erase", 1.8f, 0.25f);
        });
    }

    /** Surpassing Limits: the world round you blanks out for a moment. */
    public static void awaken(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        nullRing(sl, p.position().add(0, 1, 0), 7f);
        aura(sl, p, 40);
        keep(p, 30f, null);
        play(sl, p.position(), "erase", 1.2f, 0.5f);
    }
}
