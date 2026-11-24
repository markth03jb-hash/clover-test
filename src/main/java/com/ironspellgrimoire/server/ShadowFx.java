package com.ironspellgrimoire.server;

import static com.ironspellgrimoire.server.FxUtil.*;

import com.ironspellgrimoire.entity.ShadowSoldier;
import com.ironspellgrimoire.network.AddonNetwork;
import com.ironspellgrimoire.network.FxPacket;
import com.ironspellgrimoire.network.ShadowPackets;
import com.ironspellgrimoire.registry.AddonItemRegistry;
import com.ironspellgrimoire.registry.AddonSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Shadow Grimoire (inspired by the "raise the slain as shadows" idea; every name and look here is original). Black
 * and blue: shadows are living black silhouettes with burning blue eyes, the magic is black smoke and blue fire.
 *
 * Passive - Remnants: whatever you kill while holding the grimoire leaves its remnant where it fell - a shadow of it,
 * crouched in a dark pool - for 60 s. Only you can see it. Rise pulls it up as your shadow: 3 tries, the chance less
 * the stronger it was against your grimoire's mastery. Your army (server/ShadowArmy) levels and ranks up; the menu
 * (its key, V) equips, ranks up, renames and releases them.
 * Drawn by client/ShadowClient (FxPacket shape 98; remnants by ShadowPackets.Remnant, to their maker only).
 *
 *  1 Rise              - raise the remnant you look at as your shadow (3 tries each)
 *  2 Shadow Exchange   - swap places with the shadow nearest where you look
 *  3 Umbral Step       - a dash through everything ahead, shadow cuts left in your wake
 *  4 Unseen Hand       - an invisible grip lifts the foe you aim at, then slams it down
 *  5 Gate of Shade     - sink all your shadows into your own shadow, or call them all out again
 *  6 Command: Hunt     - every shadow turns on the foe you aim at, 40% harder for 8 s
 *  7 Veil              - 5 s: you melt into shadow - foes lose you, and your next blow lands 50% harder
 *    Sovereign's Grip  - lift the foe you aim at, hold it where you look, then fling it (no foe: pull every item near)
 *    Dread Roar        - foes near freeze 2 s, the weak flee, bosses hit softer for 10 s
 *    Mantle of Shades  - your three best shadows at your side become armour for 30 s (client/MantleLayer)
 *    Shadow Step       - step out of the shadow you look at, or one stationed far away (army menu: Station)
 *    Status, Market    - Hunter's Ascension (server/Ascension)
 *  8 Ranks of Shade   - every shadow at your side snaps into a ring round you; for 8 s they take 70% of your harm
 *  9 Rally of the Fallen - every fallen shadow rises at once; those at your side are made whole and fight a rank
 *                       stronger for 20 s
 * Exclusive shadows aren't pages: the Oathbound Knight and the Ravener are won in a Challenge (the army menu) - fight
 * the living form, then Rise on its remnant. Their own instincts (entity/OathboundKnight, entity/ShadowRavener).
 * 10 Sovereign's Domain - 60 s mode: your shadows hit 50% harder and move faster, and a fallen one rises again in 3 s
 * 11 Night Legion      - your whole army (the ones not at your side too) bursts out of the ground and charges ahead,
 *                         then sinks away. Surpassing Limits: a second wave.
 */
@Mod.EventBusSubscriber(modid = "iron_spell_grimoire")
public final class ShadowFx {
    private ShadowFx() {}

    public static final String MODE = "sovereigns_domain", VEIL = "isg_sh_veil";
    private static final java.util.UUID DOMAIN_SPEED = UUID.fromString("e5f3b2c1-7d40-4a3b-8e62-8f1d50000001");
    private static final java.util.UUID HUNT_MARK = UUID.fromString("e5f3b2c1-7d40-4a3b-8e62-8f1d50000002");
    private static final double TAU = Math.PI * 2;

    // ================================================================== the client shapes (ShadowClient, shape 98)
    private static FxPacket pk(int kind, Vec3 c, int life) {
        FxPacket p = new FxPacket();
        p.shape = 98; p.id = FxSend.nextId(); p.h = kind; p.c = c; p.life = life; p.a1 = (float) (Math.random() * 1000);
        return p;
    }
    private static void send(ServerLevel sl, FxPacket p) { FxSend.sendNear(sl, p.c, p); }

    /** 1 a pool of shadow a soldier climbs out of (r = its width). */
    public static void risePool(ServerLevel sl, Vec3 c, float w) { FxPacket p = pk(1, c, 40); p.r = Math.max(0.8f, w); send(sl, p); play(sl, c, "shadow_rise", 0.8f, 1.0f); }
    /** 2 a pool a soldier sinks into. */
    public static void sinkPool(ServerLevel sl, Vec3 c, float w) { FxPacket p = pk(2, c, 24); p.r = Math.max(0.8f, w); send(sl, p); }
    /** 3 a dash of shadow from c to u. */
    static void dash(ServerLevel sl, Vec3 c, Vec3 u) { FxPacket p = pk(3, c, 16); p.u = u; send(sl, p); }
    /** 4 an unseen grip on an entity (v.x), held for life ticks. */
    static void grip(ServerLevel sl, LivingEntity t, int life) { FxPacket p = pk(4, t.position(), life); p.v = new Vec3(t.getId(), 1, 0); p.r = t.getBbHeight(); send(sl, p); }
    /** 5 a hunt mark over an entity. */
    static void huntMark(ServerLevel sl, LivingEntity t, int life) { FxPacket p = pk(5, t.position(), life); p.v = new Vec3(t.getId(), 1, 0); p.r = t.getBbHeight(); send(sl, p); }
    /** 6 the veil on a player. */
    static void veilFx(ServerLevel sl, ServerPlayer pl, int life) { FxPacket p = pk(6, pl.position(), life); p.v = new Vec3(pl.getId(), 1, 0); send(sl, p); }
    /** 7 Sovereign's Domain on a player. */
    static void domainFx(ServerLevel sl, ServerPlayer pl, int life) { FxPacket p = pk(7, pl.position(), life); p.v = new Vec3(pl.getId(), 1, 0); send(sl, p); }
    /** 8 the Night Legion's tide of shadow rolling from c along d, r long. */
    static void legion(ServerLevel sl, Vec3 c, Vec3 d, float r, int life) { FxPacket p = pk(8, c, life); p.d = d; p.r = r; send(sl, p); }
    /** 9 two places swapping: c and u. */
    static void exchange(ServerLevel sl, Vec3 c, Vec3 u) { FxPacket p = pk(9, c, 14); p.u = u; send(sl, p); }
    /** 10 a slam of shadow at c. */
    static void slam(ServerLevel sl, Vec3 c, float r) { FxPacket p = pk(10, c, 18); p.r = r; send(sl, p); }
    /** 11 a shadow's blow landing (r = its rank). */
    public static void shadowHit(ServerLevel sl, LivingEntity t, int rank) { FxPacket p = pk(11, t.position().add(0, t.getBbHeight() * 0.6, 0), 8); p.r = rank; send(sl, p); }
    /** 12 a rank-up flare on a shadow. */
    public static void rankUpFx(ServerLevel sl, ShadowSoldier s) { FxPacket p = pk(12, s.position(), 30); p.r = s.getBbHeight(); send(sl, p); }

    // ---- the exclusive shadows' own instincts (entity/OathboundKnight, entity/ShadowRavener), drawn with the kinds above
    /** Oath Cleave: a ring of shadow thrown out before the knight. */
    public static void knightCleave(ServerLevel sl, ShadowSoldier k, Vec3 f) {
        slam(sl, k.position().add(f.scale(2.0)), 4.2f);
        play(sl, k.position(), "shadow_slash", 1.1f, 0.7f);
        sound(sl, k.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.9f, 0.6f);
    }
    /** Sworn Guard: the knight rises at its master's side wreathed in smoke, blue fire round its feet. */
    public static void knightGuard(ServerLevel sl, ShadowSoldier k, int life) {
        risePool(sl, k.position(), k.getBbWidth());
        rankUpFx(sl, k);
        FxPacket p = pk(6, k.position(), life); p.v = new Vec3(k.getId(), 1, 0); send(sl, p);
        sound(sl, k.position(), SoundEvents.WARDEN_ROAR, 0.25f, 1.6f);
    }
    /** Pounce: a streak of shadow along the leap. */
    public static void ravenerLeap(ServerLevel sl, ShadowSoldier r) {
        Vec3 v = r.getDeltaMovement();
        dash(sl, r.position(), r.position().add(v.x * 6, 0, v.z * 6));
        play(sl, r.position(), "shadow_whisper", 1.0f, 1.6f);
    }
    /** Pounce: it lands - a burst of shadow on the ground. */
    public static void ravenerLand(ServerLevel sl, Vec3 at, ShadowSoldier r) {
        slam(sl, at, 2.4f);
        play(sl, at, "shadow_slash", 0.9f, 1.4f);
    }
    /** Devour: its prey sinks into it, and it flares. */
    public static void ravenerDevour(ServerLevel sl, Vec3 at, ShadowSoldier r) {
        sinkPool(sl, at, 1.0f);
        rankUpFx(sl, r);
        play(sl, r.position(), "shadow_whisper", 1.0f, 0.6f);
    }
    /** Frenzy: a shriek, and smoke boiling off it while it lasts. */
    public static void ravenerFrenzy(ServerLevel sl, ShadowSoldier r, int life) {
        FxPacket p = pk(6, r.position(), life); p.v = new Vec3(r.getId(), 1, 0); send(sl, p);
        play(sl, r.position(), "shadow_rise", 1.2f, 1.8f);
        sound(sl, r.position(), SoundEvents.WARDEN_ROAR, 0.3f, 1.9f);
    }

    public static boolean equipped(ServerPlayer p) {
        var b = io.redspace.ironsspellbooks.api.util.Utils.getPlayerSpellbookStack(p);
        return b != null && b.is(AddonItemRegistry.SHADOW_GRIMOIRE.get());
    }

    /** How much harder the shadows hit in their master's Sovereign's Domain. */
    /** How much harder a player's shadows hit: Sovereign's Domain (x1.5) and the Necromancer's job (x1.25). */
    public static float domainBoost(Player o) {
        if (!(o instanceof ServerPlayer sp)) return 1f;
        return (Moves.inMode(sp, MODE) ? 1.5f : 1f) * (HunterAscension.job(sp) == 1 ? (HunterAscension.advanced(sp) ? 1.25f * 1.15f : 1.25f) : 1f);
    }

    private static Vec3 look(ServerPlayer p) {
        Vec3 l = flat(p.getLookAngle());
        return l.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : l.normalize();
    }

    // ================================================================== remnants
    private record Remnant(int id, UUID owner, String type, Vec3 pos, float hp, long until, int[] tries) {}
    private static final Map<Integer, Remnant> REMNANTS = new HashMap<>();
    private static int nextRemnant = 1;

    /** Leave a remnant of something its maker slew (only they will see it). */
    /** A kill may leave a remnant: half the time for ordinary things (the field doesn't fill with them); always for a
     *  boss - and a boss's only once its corpse has gone (after its death throes), where it fell. */
    public static void leaveRemnant(ServerPlayer p, LivingEntity dead) {
        String type = String.valueOf(ForgeRegistries.ENTITY_TYPES.getKey(dead.getType()));
        if (dead instanceof Player || dead instanceof ShadowSoldier || type.equals("null")) return;
        boolean boss = dead.getMaxHealth() >= 150 || dead.getType().is(net.minecraftforge.common.Tags.EntityTypes.BOSSES);
        if (!boss) {
            if (p.getRandom().nextFloat() >= 0.5f) return;
            remnantAt(p, dead, dead.position());
            return;
        }
        final int[] waited = {0};
        final Runnable[] watch = new Runnable[1];
        watch[0] = () -> {   // wait for the body to go (death animations can run for seconds), then the remnant
            waited[0] += 5;
            if (!dead.isRemoved() && waited[0] < 600) { AstaFx.later(5, watch[0]); return; }
            if (p.isRemoved() || p.level() != dead.level()) return;
            remnantAt(p, dead, dead.position());
        };
        AstaFx.later(5, watch[0]);
    }

    private static void remnantAt(ServerPlayer p, LivingEntity dead, Vec3 at) {
        String type = String.valueOf(ForgeRegistries.ENTITY_TYPES.getKey(dead.getType()));
        int id = nextRemnant++;
        Remnant r = new Remnant(id, p.getUUID(), type, at, dead.getMaxHealth(), p.serverLevel().getGameTime() + 1200, new int[]{3});
        REMNANTS.put(id, r);
        AddonNetwork.send(PacketDistributor.PLAYER.with(() -> p), new ShadowPackets.Remnant(id, type, r.pos(), dead.getYRot(), 1f, 1200, 3));
    }

    private static void dropRemnant(ServerPlayer p, Remnant r) {
        REMNANTS.remove(r.id());
        if (p != null) AddonNetwork.send(PacketDistributor.PLAYER.with(() -> p), new ShadowPackets.Remnant(r.id(), r.type(), r.pos(), 0, 1f, 0, 0));
    }

    /** The chance a remnant rises: easy for weak things, hard for strong ones, better with more mastery. */
    static float chance(ServerPlayer p, float hp) {
        var b = io.redspace.ironsspellbooks.api.util.Utils.getPlayerSpellbookStack(p);
        int lv = b == null ? 1 : GrimoireMastery.level(b);
        if (hp >= 150) return 0.08f + 0.02f * lv;   // bosses
        return (float) Math.max(0.1, Math.min(0.95, 1.15 - hp / (30.0 + 18 * lv)));
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent e) {
        LivingEntity dead = e.getEntity();
        if (dead.level().isClientSide) return;
        if (e.getSource().getEntity() instanceof ServerPlayer p && equipped(p)) leaveRemnant(p, dead);
        if (e.getSource().getEntity() instanceof ShadowSoldier s && s.temporary() <= 0 && s.owner() instanceof ServerPlayer o) {
            ShadowArmy.reward(o, s.armyId(), dead.getMaxHealth());   // the shadow learns from its kill...
            if (equipped(o)) leaveRemnant(o, dead);                   // ...and its master may raise what it slew
        }
    }

    /** Veil: the next blow lands 50% harder (and ends the veil). Ranks of Shade: your shadows take 70% of your harm. */
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent e) {
        if (e.getEntity() instanceof ServerPlayer me && me.serverLevel().getGameTime() < me.getPersistentData().getLong(RANKS)) {
            List<ShadowSoldier> out = ShadowArmy.out(me);
            out.removeIf(s -> s.distanceToSqr(me) > 16 * 16);
            if (!out.isEmpty()) {
                float share = e.getAmount() * 0.7f / out.size();
                for (ShadowSoldier s : out) { s.invulnerableTime = 0; s.hurt(me.damageSources().magic(), share); shadowHit(me.serverLevel(), s, 1); }
                e.setAmount(e.getAmount() * 0.3f);
            }
        }
        if (e.getSource().getEntity() instanceof ServerPlayer p && p.serverLevel().getGameTime() < p.getPersistentData().getLong(VEIL)) {
            e.setAmount(e.getAmount() * 1.5f);
            p.getPersistentData().remove(VEIL);
        }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        if (!equipped(p)) {
            if (p.tickCount % 10 == 0) { ShadowArmy.recallAll(p); if (Moves.inMode(p, MODE)) Moves.ends(p, MODE); }
            return;
        }
        if (p.tickCount % 20 == 0) ShadowArmy.keep(p);
        if (p.tickCount % 20 == 5) {   // remnants fade after their minute
            long now = p.serverLevel().getGameTime();
            for (Iterator<Remnant> it = REMNANTS.values().iterator(); it.hasNext(); ) {
                Remnant r = it.next();
                if (r.owner().equals(p.getUUID()) && now > r.until()) { it.remove(); }
            }
        }
        if (p.serverLevel().getGameTime() < p.getPersistentData().getLong(VEIL) && p.tickCount % 5 == 0) {   // foes lose you
            for (Mob m : p.serverLevel().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(24), m -> m.getTarget() == p)) m.setTarget(null);
        }
    }

    // ================================================================== 1. Rise
    public static void rise(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        Vec3 eye = p.getEyePosition(), lk = p.getLookAngle();
        Remnant best = null;
        double bestScore = 0;
        for (Remnant r : REMNANTS.values()) {   // the remnant nearest where you look, within 14 blocks
            if (!r.owner().equals(p.getUUID())) continue;
            Vec3 to = r.pos().add(0, 0.6, 0).subtract(eye);
            double d = to.length();
            if (d > 14) continue;
            double dot = to.normalize().dot(lk);
            if (dot < 0.8) continue;
            double score = dot - d * 0.01;
            if (score > bestScore) { bestScore = score; best = r; }
        }
        if (best == null) {
            p.displayClientMessage(Component.literal("No remnant there - look at one (only you can see them)").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        Remnant r = best;
        play(sl, r.pos(), "shadow_whisper", 1.0f, 1.0f);
        boolean exclusive = r.hp() < 0;   // a beaten exclusive shadow (a Challenge) - it always answers
        if (exclusive && ShadowArmy.inArmy(p, r.type())) { dropRemnant(p, r); return; }
        if (exclusive || HunterAscension.useLure(p) || p.getRandom().nextFloat() < chance(p, r.hp())) {
            CompoundTag entry = ShadowArmy.add(p, r.type(), exclusive);
            dropRemnant(p, r);
            if (entry == null) return;
            HunterAscension.onRaise(p);
            if (entry.getBoolean("eq")) ShadowArmy.raise(p, entry, r.pos(), 0);
            if (!exclusive && HunterAscension.job(p) == 1 && HunterAscension.advanced(p) && p.getRandom().nextFloat() < 0.35f) {   // the Lich Lord: a second shadow from the same remnant
                CompoundTag twin = ShadowArmy.add(p, r.type(), false);
                if (twin != null) {
                    HunterAscension.onRaise(p);
                    if (twin.getBoolean("eq")) ShadowArmy.raise(p, twin, r.pos().add(1.2, 0, 0), 0); else risePool(sl, r.pos().add(1.2, 0, 0), 1);
                    p.displayClientMessage(Component.literal("Two shadows rise from one remnant").withStyle(ChatFormatting.LIGHT_PURPLE), true);
                }
            }
            else risePool(sl, r.pos(), 1);
            p.displayClientMessage(Component.literal(entry.getString("name") + " rises as your shadow" + (entry.getBoolean("eq") ? "" : " (stored - your slots are full)"))
                    .withStyle(ChatFormatting.AQUA), true);
            sound(sl, r.pos(), SoundEvents.WARDEN_EMERGE, 0.4f, 1.6f);
        } else {
            r.tries()[0]--;
            FxPacket f = pk(13, r.pos(), 16); send(sl, f);   // it shudders and resists
            if (r.tries()[0] <= 0) {
                dropRemnant(p, r);
                p.displayClientMessage(Component.literal("The remnant crumbles away").withStyle(ChatFormatting.DARK_GRAY), true);
            } else {
                AddonNetwork.send(PacketDistributor.PLAYER.with(() -> p), new ShadowPackets.Remnant(r.id(), r.type(), r.pos(), 0, 1f, (int) (r.until() - sl.getGameTime()), r.tries()[0]));
                p.displayClientMessage(Component.literal("It resists - " + r.tries()[0] + " tr" + (r.tries()[0] == 1 ? "y" : "ies") + " left").withStyle(ChatFormatting.GRAY), true);
            }
        }
    }

    /** For the tests and screenshots: a remnant of a zombie in front of the player. */
    public static void testRemnant(ServerPlayer p, Vec3 at) {
        int id = nextRemnant++;
        Remnant r = new Remnant(id, p.getUUID(), "minecraft:zombie", at, 1f, p.serverLevel().getGameTime() + 1200, new int[]{3});
        REMNANTS.put(id, r);
        AddonNetwork.send(PacketDistributor.PLAYER.with(() -> p), new ShadowPackets.Remnant(id, r.type(), at, 180, 1f, 1200, 3));
    }

    // ================================================================== 2. Shadow Exchange
    public static void shadowExchange(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        ShadowSoldier best = null;
        double bestDot = -2;
        for (ShadowSoldier s : ShadowArmy.out(p)) {
            Vec3 to = s.position().subtract(p.position());
            double dot = to.lengthSqr() < 1e-4 ? 0 : to.normalize().dot(p.getLookAngle());
            if (dot > bestDot) { bestDot = dot; best = s; }
        }
        if (best == null) { p.displayClientMessage(Component.literal("No shadow at your side to trade places with").withStyle(ChatFormatting.GRAY), true); return; }
        Vec3 a = p.position(), b = best.position();
        exchange(sl, a, b);
        p.teleportTo(b.x, b.y, b.z);
        best.teleportTo(a.x, a.y, a.z);
        p.hurtMarked = true;
        play(sl, b, "shadow_whisper", 0.8f, 1.5f);
    }

    // ================================================================== 3. Umbral Step
    public static void umbralStep(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        Vec3 d = look(p), from = p.position();
        Vec3 to = clip(sl, p, from.add(0, 0.5, 0), from.add(0, 0.5, 0).add(d.scale(9))).subtract(0, 0.5, 0);
        List<LivingEntity> cut = new ArrayList<>(Kit.along(sl, p, from.add(0, 1, 0), to.add(0, 1, 0), 1.6));
        dash(sl, from, to);
        p.teleportTo(to.x, to.y, to.z);
        p.hurtMarked = true;
        play(sl, from, "shadow_slash", 1.0f, 1.0f);
        AstaFx.later(2, () -> { for (LivingEntity t : cut) if (alive(p) && t.isAlive()) { AstaFx.damage(p, t, power, "umbral_step"); shadowHit(sl, t, 1); } });
    }

    // ================================================================== 4. Unseen Hand
    public static void unseenHand(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        LivingEntity t = aimed(p, 20, 12);
        if (t == null) { p.displayClientMessage(Component.literal("Nothing in your grip").withStyle(ChatFormatting.GRAY), true); return; }
        grip(sl, t, 16);
        play(sl, t.position(), "shadow_whisper", 1.0f, 0.7f);
        final double give = GravityFx.give(t);
        for (int i = 0; i < 8; i++) AstaFx.later(i, () -> { if (t.isAlive()) { t.setDeltaMovement(0, 0.35 * give, 0); t.hurtMarked = true; } });   // lifted...
        AstaFx.later(10, () -> {   // ...and slammed down
            if (!alive(p) || !t.isAlive()) return;
            t.setDeltaMovement(0, -2.2 * give, 0); t.hurtMarked = true;
        });
        AstaFx.later(14, () -> {
            if (!alive(p) || !t.isAlive()) return;
            t.invulnerableTime = 0;
            AstaFx.damage(p, t, power * 1.2f, "unseen_hand");
            slam(sl, t.position(), 2.5f);
            sound(sl, t.position(), SoundEvents.GENERIC_EXPLODE, 0.5f, 1.4f);
            for (LivingEntity e : Kit.near(sl, p, t.position().add(0, 0.5, 0), 2.5)) if (e != t) AstaFx.damage(p, e, power * 0.4f, "unseen_hand");
        });
    }

    // ================================================================== 5. Gate of Shade
    public static void gateOfShade(ServerPlayer p, float power) {
        CompoundTag r = ShadowArmy.root(p);
        boolean stored = !r.getBoolean(ShadowArmy.GATE);
        r.putBoolean(ShadowArmy.GATE, stored);
        ServerLevel sl = p.serverLevel();
        FxPacket f = pk(14, p.position(), 30); f.r = 3.5f; f.a0 = stored ? 1 : 0; send(sl, f);
        play(sl, p.position(), "shadow_gate", 1.0f, stored ? 0.8f : 1.2f);
        if (stored) ShadowArmy.recallAll(p);
        else ShadowArmy.keep(p);
        p.displayClientMessage(Component.literal(stored ? "Your shadows sink into your shadow" : "Your shadows rise").withStyle(ChatFormatting.DARK_AQUA), true);
    }

    // ================================================================== 6. Command: Hunt
    public static void commandHunt(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        LivingEntity t = aimed(p, 28, 12);
        if (t == null) { p.displayClientMessage(Component.literal("Look at what they should hunt").withStyle(ChatFormatting.GRAY), true); return; }
        huntMark(sl, t, 160);
        play(sl, p.position(), "shadow_whisper", 1.1f, 0.6f);
        for (ShadowSoldier s : ShadowArmy.out(p)) { s.setTarget(t); s.hunting = 160; }
        AstaFx.damage(p, t, power * 0.3f, "command_hunt");
        TimedModifiers.apply(t, Attributes.ARMOR, HUNT_MARK, "Hunted", -4, AttributeModifier.Operation.ADDITION, 160);
    }

    // ================================================================== 7. Veil
    public static void veil(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        p.getPersistentData().putLong(VEIL, sl.getGameTime() + 100);
        veilFx(sl, p, 100);
        play(sl, p.position(), "shadow_whisper", 0.9f, 1.2f);
        for (Mob m : sl.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(24), m -> m.getTarget() == p)) m.setTarget(null);
    }

    // ================================================================== 8. Ranks of Shade
    static final String RANKS = "isg_sh_ranks";

    /** Every shadow at your side snaps to you in a guarding ring; for 8 s they take most of the harm meant for you. */
    public static void ranksOfShade(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        List<ShadowSoldier> out = ShadowArmy.out(p);
        if (out.isEmpty()) { p.displayClientMessage(Component.literal("No shadows at your side to close ranks").withStyle(ChatFormatting.GRAY), true); return; }
        int n = out.size();
        for (int i = 0; i < n; i++) {   // they step out of your shadow round you, facing out
            ShadowSoldier s = out.get(i);
            double a = TAU * i / n;
            Vec3 at = p.position().add(Math.cos(a) * 2.2, 0, Math.sin(a) * 2.2);
            sinkPool(sl, s.position(), s.getBbWidth());
            s.teleportTo(at.x, p.getY(), at.z);
            s.setYRot((float) Math.toDegrees(Math.atan2(-Math.cos(a), Math.sin(a))) + 180); s.yBodyRot = s.getYRot();
            s.getNavigation().stop();
            risePool(sl, at, s.getBbWidth());
        }
        p.getPersistentData().putLong(RANKS, sl.getGameTime() + 160);
        FxPacket f = pk(7, p.position(), 160); f.v = new Vec3(p.getId(), 1, 0); send(sl, f);
        play(sl, p.position(), "shadow_gate", 1.0f, 1.3f);
        sound(sl, p.position(), SoundEvents.ARMOR_EQUIP_NETHERITE, 1.0f, 0.7f);
    }

    // ================================================================== 9. Rally of the Fallen
    private static final UUID RALLY_POWER = UUID.fromString("e5f3b2c1-7d40-4a3b-8e62-8f1d50000031");
    private static final UUID RALLY_HEALTH = UUID.fromString("e5f3b2c1-7d40-4a3b-8e62-8f1d50000032");
    private static final UUID RALLY_SPEED = UUID.fromString("e5f3b2c1-7d40-4a3b-8e62-8f1d50000033");

    /** Every fallen shadow rises at once; those at your side are made whole and fight a rank stronger for 20 s. */
    public static void rallyOfTheFallen(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        long now = sl.getGameTime();
        int risen = 0;
        for (var t : ShadowArmy.list(p)) { CompoundTag c = (CompoundTag) t; if (c.getLong("rec") > now) { c.putLong("rec", now); risen++; } }
        ShadowArmy.root(p).putBoolean(ShadowArmy.GATE, false);
        ShadowArmy.keep(p);
        AstaFx.later(4, () -> {
            if (!alive(p)) return;
            for (ShadowSoldier s : ShadowArmy.out(p)) {
                TimedModifiers.apply(s, Attributes.ATTACK_DAMAGE, RALLY_POWER, "Rally of the Fallen", 0.2, AttributeModifier.Operation.MULTIPLY_BASE, 400);
                TimedModifiers.apply(s, Attributes.MAX_HEALTH, RALLY_HEALTH, "Rally of the Fallen", 0.25, AttributeModifier.Operation.MULTIPLY_BASE, 400);
                TimedModifiers.apply(s, Attributes.MOVEMENT_SPEED, RALLY_SPEED, "Rally of the Fallen", 0.1, AttributeModifier.Operation.MULTIPLY_BASE, 400);
                s.setHealth(s.getMaxHealth());
                rankUpFx(sl, s);
            }
        });
        play(sl, p.position(), "shadow_rise", 1.3f, 0.8f);
        sound(sl, p.position(), SoundEvents.WARDEN_EMERGE, 0.5f, 1.3f);
        if (ShadowArmy.count(p) == 0) p.displayClientMessage(Component.literal("Your army is empty - there is no one to rally").withStyle(ChatFormatting.GRAY), true);
        else if (risen > 0) p.displayClientMessage(Component.literal(risen + " fallen shadow" + (risen == 1 ? "" : "s") + " rise again").withStyle(ChatFormatting.AQUA), true);
    }

    // ================================================================== Sovereign's Grip
    /** The foe you aim at is lifted and held where you look for 2.5 s (crouch to throw it early), then flung along your
     *  aim - it takes damage when it lands. With no foe in sight: every dropped item within 12 blocks flies to you. */
    public static void sovereignsGrip(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        LivingEntity t = aimed(p, 20, 12);
        if (t == null || t instanceof ShadowSoldier s && s.ownerUUID() != null && s.ownerUUID().equals(p.getUUID())) {
            int n = 0;
            for (net.minecraft.world.entity.item.ItemEntity it : sl.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, p.getBoundingBox().inflate(12))) {
                it.teleportTo(p.getX(), p.getY() + 0.5, p.getZ()); it.setNoPickUpDelay(); n++;
            }
            play(sl, p.position(), "shadow_whisper", 0.8f, 1.5f);
            if (n == 0) p.displayClientMessage(Component.literal("Nothing in your grip").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        grip(sl, t, 60);
        play(sl, t.position(), "shadow_whisper", 1.0f, 0.8f);
        t.invulnerableTime = 0;
        AstaFx.damage(p, t, power * 0.4f, "sovereigns_grip");   // the grip itself crushes
        final int[] k = {0};
        final Runnable[] hold = new Runnable[1];
        hold[0] = () -> {
            if (!alive(p) || !t.isAlive()) return;
            k[0]++;
            Vec3 at = p.getEyePosition().add(p.getLookAngle().scale(4.5)).subtract(0, t.getBbHeight() / 2, 0);
            Vec3 v = at.subtract(t.position()).scale(0.45);
            t.setDeltaMovement(v); t.hurtMarked = true; t.fallDistance = 0;
            if (t instanceof Mob m) { m.getNavigation().stop(); m.setTarget(null); }
            if (k[0] < 50 && !p.isShiftKeyDown()) { AstaFx.later(1, hold[0]); return; }
            Vec3 d = p.getLookAngle();   // the throw
            t.setDeltaMovement(d.scale(2.2).add(0, 0.25, 0)); t.hurtMarked = true;
            play(sl, t.position(), "shadow_slash", 1.0f, 0.7f);
            final Vec3 from = t.position();
            AstaFx.later(8, () -> {
                if (!alive(p) || !t.isAlive()) return;
                t.invulnerableTime = 0;
                AstaFx.damage(p, t, power * (1 + (float) Math.min(1.5, t.position().distanceTo(from) / 12)), "sovereigns_grip");
                slam(sl, t.position(), 2.2f);
                shadowHit(sl, t, 2);
            });
        };
        AstaFx.later(1, hold[0]);
    }

    // ================================================================== Dread Roar
    private static final UUID DREAD_FREEZE = UUID.fromString("e5f3b2c1-7d40-4a3b-8e62-8f1d50000041");
    private static final UUID DREAD_WEAK = UUID.fromString("e5f3b2c1-7d40-4a3b-8e62-8f1d50000042");

    /** A wave of dread: foes within 10 blocks freeze for 2 s, weak ones flee for 6 s, bosses hit 25% softer for 10 s. */
    public static void dreadRoar(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        slam(sl, p.position(), 10f);
        FxSend.shake(sl, p.position(), 0.5f, 12, 16);
        sound(sl, p.position(), SoundEvents.WARDEN_ROAR, 0.9f, 0.6f);
        play(sl, p.position(), "shadow_gate", 1.2f, 0.5f);
        for (Mob m : sl.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(10), m -> m.isAlive() && !(m instanceof ShadowSoldier s && p.getUUID().equals(s.ownerUUID())))) {
            if (m.distanceToSqr(p) > 100) continue;
            TimedModifiers.apply(m, Attributes.MOVEMENT_SPEED, DREAD_FREEZE, "Dread Roar", -1.0, AttributeModifier.Operation.MULTIPLY_TOTAL, 40);
            m.setTarget(null); m.getNavigation().stop();
            shadowHit(sl, m, 0);
            boolean boss = m.getMaxHealth() >= 150 || m.getType().is(net.minecraftforge.common.Tags.EntityTypes.BOSSES);
            if (boss) TimedModifiers.apply(m, Attributes.ATTACK_DAMAGE, DREAD_WEAK, "Dread Roar", -0.25, AttributeModifier.Operation.MULTIPLY_TOTAL, 200);
            else if (m.getMaxHealth() < 40) {   // the weak run
                for (int k = 0; k < 6; k++) AstaFx.later(40 + k * 20, () -> {
                    if (!m.isAlive() || !alive(p)) return;
                    Vec3 away = m.position().subtract(p.position()).multiply(1, 0, 1);
                    away = away.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : away.normalize();
                    Vec3 to = m.position().add(away.scale(8));
                    m.setTarget(null);
                    m.getNavigation().moveTo(to.x, to.y, to.z, 1.4);
                });
            }
        }
    }

    // ================================================================== Mantle of Shades
    private static final UUID MANTLE_ARMOR = UUID.fromString("e5f3b2c1-7d40-4a3b-8e62-8f1d50000051");
    private static final UUID MANTLE_TOUGH = UUID.fromString("e5f3b2c1-7d40-4a3b-8e62-8f1d50000052");
    private static final UUID MANTLE_POWER = UUID.fromString("e5f3b2c1-7d40-4a3b-8e62-8f1d50000053");

    /** Your three highest-ranked shadows at your side sink into you as armour for 30 s, stronger the higher their ranks. */
    public static void mantleOfShades(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        List<ShadowSoldier> out = ShadowArmy.out(p);
        out.removeIf(s -> s.station != null || s.distanceToSqr(p) > 20 * 20);
        out.sort((a, b2) -> Integer.compare(b2.rank(), a.rank()));
        if (out.isEmpty()) { p.displayClientMessage(Component.literal("No shadows at your side to wear").withStyle(ChatFormatting.GRAY), true); return; }
        List<ShadowSoldier> worn = out.subList(0, Math.min(3, out.size()));
        int sum = 0;
        long back = sl.getGameTime() + 600;
        for (ShadowSoldier s : worn) {
            sum += s.rank() + 1;
            dash(sl, s.position().add(0, 1, 0), p.position().add(0, 1, 0));
            sinkPool(sl, s.position(), s.getBbWidth());
            CompoundTag e = ShadowArmy.find(p, s.armyId());
            if (e != null) e.putLong("rec", back);   // it steps back out of you when the mantle fades
            s.discard();
        }
        TimedModifiers.apply(p, Attributes.ARMOR, MANTLE_ARMOR, "Mantle of Shades", 2.0 * sum, AttributeModifier.Operation.ADDITION, 600);
        TimedModifiers.apply(p, Attributes.ARMOR_TOUGHNESS, MANTLE_TOUGH, "Mantle of Shades", 1.0 * sum, AttributeModifier.Operation.ADDITION, 600);
        TimedModifiers.apply(p, Attributes.ATTACK_DAMAGE, MANTLE_POWER, "Mantle of Shades", 0.03 * sum, AttributeModifier.Operation.MULTIPLY_TOTAL, 600);
        FxPacket f = pk(15, p.position(), 600); f.v = new Vec3(p.getId(), 1, 0); FxSend.sendNear(sl, p.position(), f);   // drawn on you (MantleLayer)
        play(sl, p.position(), "shadow_rise", 1.2f, 0.7f);
        sound(sl, p.position(), SoundEvents.ARMOR_EQUIP_NETHERITE, 1.0f, 0.6f);
    }

    // ================================================================== Shadow Step
    /** Step out of the shadow nearest where you look - any distance, same world; if none is in sight, to one keeping watch. */
    public static void shadowStep(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        Vec3 eye = p.getEyePosition(), lk = p.getLookAngle();
        ShadowSoldier best = null;
        double bestDot = 0.96;
        for (ShadowSoldier s : ShadowArmy.out(p)) {
            if (s.level() != sl) continue;
            Vec3 to = s.position().add(0, 1, 0).subtract(eye);
            if (to.lengthSqr() < 9) continue;
            double dot = to.normalize().dot(lk);
            if (dot > bestDot) { bestDot = dot; best = s; }
        }
        Vec3 to = null;
        if (best != null) to = best.position().add(best.getLookAngle().multiply(1, 0, 1).scale(-1.2));
        else for (var t : ShadowArmy.list(p)) {   // a shadow keeping watch, even where no one is
            CompoundTag e = (CompoundTag) t;
            if (e.contains("st") && e.getString("stdim").equals(sl.dimension().location().toString())) { to = ShadowArmy.stationOf(e); break; }
        }
        if (to == null) { p.displayClientMessage(Component.literal("No shadow in sight - station one in the army menu to step to it from anywhere").withStyle(ChatFormatting.GRAY), true); return; }
        sinkPool(sl, p.position(), 0.8f);
        exchange(sl, p.position(), to);
        p.teleportTo(to.x, to.y, to.z);
        p.fallDistance = 0;
        risePool(sl, to, 0.8f);
        play(sl, to, "shadow_whisper", 1.0f, 1.2f);
    }

    // ================================================================== Thousand Cuts (the Nightfang's own move)
    /** Blink from foe to foe - up to five within 10 blocks, each cut a little deeper than the last - then land back
     *  where you started. Returns false (no cooldown) if there's nothing to cut. */
    public static boolean thousandCuts(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        List<LivingEntity> foes = new ArrayList<>(Kit.near(sl, p, p.position(), 10));
        foes.removeIf(t -> !foe(p, t));
        foes.sort((a, b2) -> Double.compare(a.distanceToSqr(p), b2.distanceToSqr(p)));
        if (foes.isEmpty()) { p.displayClientMessage(Component.literal("Nothing in reach to cut").withStyle(ChatFormatting.GRAY), true); return false; }
        List<LivingEntity> cut = foes.subList(0, Math.min(5, foes.size()));
        final Vec3 home = p.position();
        final float yaw = p.getYRot(), pitch = p.getXRot();
        float base = (float) p.getAttributeValue(Attributes.ATTACK_DAMAGE);
        Vec3 last = home;
        for (int i = 0; i < cut.size(); i++) {
            final LivingEntity t = cut.get(i);
            final int k = i;
            final Vec3 from = last;
            AstaFx.later(1 + i * 3, () -> {
                if (!alive(p) || !t.isAlive()) return;
                Vec3 side = t.position().subtract(from).multiply(1, 0, 1);
                side = side.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : side.normalize();
                Vec3 at = t.position().add(side.scale(t.getBbWidth() * 0.5 + 0.9));   // out the far side of it
                dash(sl, from.add(0, 1, 0), at.add(0, 1, 0));
                p.teleportTo(at.x, t.getY(), at.z);
                p.fallDistance = 0;
                t.invulnerableTime = 0;
                AstaFx.damage(p, t, base * (1 + 0.25f * k), "thousand_cuts");
                shadowHit(sl, t, 2);
                play(sl, t.position(), "shadow_slash", 0.8f, 1.5f + 0.1f * k);
            });
            last = cut.get(i).position();
        }
        final Vec3 end = last;
        AstaFx.later(2 + cut.size() * 3, () -> {   // and back
            if (!alive(p)) return;
            dash(sl, end.add(0, 1, 0), home.add(0, 1, 0));
            p.teleportTo(home.x, home.y, home.z);
            p.setYRot(yaw); p.setXRot(pitch);
            p.fallDistance = 0;
            risePool(sl, home, 0.8f);
        });
        sinkPool(sl, home, 0.8f);
        return true;
    }

    // ================================================================== the other knives' moves (item/ShadowKnifeItem)
    /** Dragon Fang - Wyrmfang Lunge: a dash of up to 8 blocks (stopping at a wall), cutting through every foe in the
     *  way; each wound bursts a second later, and each foe cut heals you 5%. */
    public static boolean wyrmfangLunge(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        Vec3 d = p.getLookAngle().multiply(1, 0, 1);
        d = d.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : d.normalize();
        Vec3 from = p.position(), want = from.add(d.scale(8));
        var hit = sl.clip(new net.minecraft.world.level.ClipContext(from.add(0, 0.6, 0), want.add(0, 0.6, 0),
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, p));
        Vec3 to = hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK ? hit.getLocation().subtract(d.scale(0.6)).subtract(0, 0.6, 0) : want;
        List<LivingEntity> cut = new ArrayList<>(Kit.along(sl, p, from.add(0, 1, 0), to.add(0, 1, 0), 1.1));
        cut.removeIf(t -> !foe(p, t));
        dash(sl, from.add(0, 0.5, 0), to.add(0, 0.5, 0));
        p.teleportTo(to.x, to.y, to.z);
        p.fallDistance = 0;
        play(sl, to, "shadow_slash", 1.1f, 0.6f);
        sound(sl, to, SoundEvents.BLAZE_SHOOT, 0.6f, 0.7f);
        float base = (float) p.getAttributeValue(Attributes.ATTACK_DAMAGE);
        for (LivingEntity t : cut) {
            t.invulnerableTime = 0;
            AstaFx.damage(p, t, base, "wyrmfang_lunge");
            shadowHit(sl, t, 2);
            p.heal(p.getMaxHealth() * 0.05f);
            AstaFx.later(20, () -> {   // the wound bursts
                if (!t.isAlive() || !alive(p)) return;
                t.invulnerableTime = 0;
                AstaFx.damage(p, t, base * 0.8f, "wyrmfang_lunge");
                slam(sl, t.position(), 1.4f);
                sound(sl, t.position(), SoundEvents.FIRECHARGE_USE, 0.5f, 1.4f);
            });
        }
        return true;
    }

    /** Whether t is something p's moves should hit: not a player, and not one of p's own shadows. */
    static boolean foe(ServerPlayer p, LivingEntity t) {
        return t != p && !(t instanceof Player op && !com.ironspellgrimoire.AddonConfig.mayHurt(p, op)) && !(t instanceof ShadowSoldier s && p.getUUID().equals(s.ownerUUID()));
    }

    /** Gilded Thorn - Thornbite: a wide slash cutting everything within 4 blocks in front of you (full damage), and the
     *  thorns stay in the wound - each foe cut bleeds for a quarter of that again every second for 4 s. 12 s. */
    public static boolean thornbite(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        Vec3 d = p.getLookAngle().multiply(1, 0, 1);
        d = d.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : d.normalize();
        final Vec3 dir = d;
        Vec3 eye = p.position().add(0, 1.1, 0);
        List<LivingEntity> cut = new ArrayList<>(Kit.near(sl, p, p.position().add(dir.scale(2.0)), 3.2));
        cut.removeIf(t -> !foe(p, t)
                || t.position().subtract(p.position()).multiply(1, 0, 1).normalize().dot(dir) < 0.25);
        Vec3 side = new Vec3(-dir.z, 0, dir.x);
        dash(sl, eye.add(dir.scale(1.6)).add(side.scale(1.8)), eye.add(dir.scale(1.6)).subtract(side.scale(1.8)));   // the arc of the cut
        play(sl, p.position(), "shadow_slash", 1.0f, 0.9f);
        sound(sl, p.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.9f, 0.8f);
        float base = (float) p.getAttributeValue(Attributes.ATTACK_DAMAGE);
        for (LivingEntity t : cut) {
            t.invulnerableTime = 0;
            AstaFx.damage(p, t, base, "thornbite");
            shadowHit(sl, t, 2);
            for (int k = 1; k <= 4; k++) {   // the thorns bleed it
                AstaFx.later(k * 20, () -> {
                    if (!t.isAlive() || !alive(p)) return;
                    t.invulnerableTime = 0;
                    AstaFx.damage(p, t, base * 0.25f, "thornbite");
                    FxSend.ring(sl, Fx.CRIMSON | FxPacket.KEEP_SHAPE, t.position().add(0, t.getBbHeight() * 0.5, 0), 0.2f, 0.7f, 0.03f, 6);
                });
            }
        }
        return true;
    }

    /** Bloodrend - Crimson Rend: a crescent of blood flies 8 blocks forward (a block a tick, stopped by walls), cutting
     *  through every foe in its path once each. It cuts harder the more hurt you are: +60% at the brink of death. 12 s. */
    public static boolean crimsonRend(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        Vec3 d = p.getLookAngle().multiply(1, 0, 1);
        d = d.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : d.normalize();
        final Vec3 dir = d, side = new Vec3(-d.z, 0, d.x);
        Vec3 start = p.position().add(0, 1.0, 0).add(dir.scale(0.8));
        var hit = sl.clip(new net.minecraft.world.level.ClipContext(start, start.add(dir.scale(8)),
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, p));
        double reach = hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK ? hit.getLocation().distanceTo(start) : 8;
        float missing = 1f - p.getHealth() / Math.max(1f, p.getMaxHealth());
        float dmg = (float) p.getAttributeValue(Attributes.ATTACK_DAMAGE) * 1.2f * (1f + 0.6f * missing);
        play(sl, p.position(), "shadow_slash", 1.1f, 0.7f);
        sound(sl, p.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, 0.6f);
        java.util.Set<java.util.UUID> struck = new java.util.HashSet<>();
        int steps = (int) Math.ceil(reach);
        for (int k = 0; k <= steps; k++) {
            final double at = Math.min(reach, k);
            AstaFx.later(k, () -> {
                if (!alive(p)) return;
                Vec3 c = start.add(dir.scale(at));
                // the crescent: its horns trail behind its middle
                Vec3 hornA = c.add(side.scale(1.6)).subtract(dir.scale(0.7)), hornB = c.subtract(side.scale(1.6)).subtract(dir.scale(0.7));
                dash(sl, hornA, c); dash(sl, c, hornB);
                FxSend.ring(sl, Fx.CRIMSON | FxPacket.KEEP_SHAPE, c, 0.3f, 1.2f, 0.04f, 6);
                for (LivingEntity t : Kit.along(sl, p, hornA, hornB, 0.9)) {
                    if (!foe(p, t) || !struck.add(t.getUUID())) continue;
                    t.invulnerableTime = 0;
                    AstaFx.damage(p, t, dmg, "crimson_rend");
                    shadowHit(sl, t, 2);
                    FxSend.ring(sl, Fx.CRIMSON | FxPacket.KEEP_SHAPE, t.position().add(0, t.getBbHeight() * 0.5, 0), 0.2f, 1.0f, 0.04f, 8);
                }
            });
        }
        return true;
    }

    /** Rosewrath - Crimson Fracture: you drive the blade down and glowing red cracks race out along the ground, 10
     *  blocks the way you face (a block a tick, following the ground, stopped by walls); the ground splits under each
     *  foe they reach (1.3x a blow, a jolt upward), once each. 12 s. */
    public static boolean crimsonFracture(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        Vec3 d = p.getLookAngle().multiply(1, 0, 1);
        d = d.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : d.normalize();
        final Vec3 dir = d, side = new Vec3(-d.z, 0, d.x);
        float dmg = (float) p.getAttributeValue(Attributes.ATTACK_DAMAGE) * 1.3f;
        slam(sl, p.position().add(dir.scale(0.8)), 1.2f);
        play(sl, p.position(), "shadow_slash", 0.9f, 0.5f);
        sound(sl, p.position(), SoundEvents.ANVIL_LAND, 0.4f, 0.6f);
        java.util.Set<java.util.UUID> struck = new java.util.HashSet<>();
        final Vec3[] last = {p.position().add(0, 0.05, 0)};
        final boolean[] stopped = {false};
        for (int k = 1; k <= 10; k++) {
            final int step = k;
            AstaFx.later(k, () -> {
                if (!alive(p) || stopped[0]) return;
                double wob = Math.sin(step * 1.7) * 0.35;   // the crack wanders a little side to side
                Vec3 want = p.position().add(dir.scale(step)).add(side.scale(wob));
                Double gy = groundAt(sl, want, last[0].y);
                if (gy == null) { stopped[0] = true; return; }   // a wall or a drop: the crack stops
                Vec3 at = new Vec3(want.x, gy + 0.05, want.z);
                dash(sl, last[0], at);
                FxSend.ring(sl, Fx.CRIMSON | FxPacket.KEEP_SHAPE, at, 0.2f, 0.9f, 0.03f, 10);
                if (step % 3 == 0) sound(sl, at, SoundEvents.STONE_BREAK, 0.5f, 0.6f);
                last[0] = at;
                for (LivingEntity t : Kit.near(sl, p, at.add(0, 0.6, 0), 1.4)) {
                    if (!foe(p, t) || !struck.add(t.getUUID())) continue;
                    t.invulnerableTime = 0;
                    AstaFx.damage(p, t, dmg, "crimson_fracture");
                    t.push(0, 0.42, 0); t.hurtMarked = true;
                    slam(sl, t.position(), 1.0f);
                    FxSend.ring(sl, Fx.CRIMSON | FxPacket.KEEP_SHAPE, t.position().add(0, 0.1, 0), 0.3f, 1.6f, 0.05f, 12);
                    shadowHit(sl, t, 2);
                }
            });
        }
        return true;
    }

    /** The ground's height under a point, searching a couple of blocks up and down from about y; null if it's a wall or a drop. */
    private static Double groundAt(ServerLevel sl, Vec3 at, double y) {
        net.minecraft.core.BlockPos.MutableBlockPos m = new net.minecraft.core.BlockPos.MutableBlockPos();
        int x = net.minecraft.util.Mth.floor(at.x), z = net.minecraft.util.Mth.floor(at.z), y0 = net.minecraft.util.Mth.floor(y);
        for (int dy = 1; dy >= -3; dy--) {
            m.set(x, y0 + dy - 1, z);
            boolean solidBelow = !sl.getBlockState(m).getCollisionShape(sl, m).isEmpty();
            m.set(x, y0 + dy, z);
            boolean openHere = sl.getBlockState(m).getCollisionShape(sl, m).isEmpty();
            m.set(x, y0 + dy + 1, z);
            boolean openAbove = sl.getBlockState(m).getCollisionShape(sl, m).isEmpty();
            if (solidBelow && openHere && openAbove) return (double) (y0 + dy);
        }
        return null;
    }

    /** Frostwing - Sovereign's Verdict: you raise the greatsword and gather its light for a second - cyan rings draw in
     *  round you, a pillar of light climbs off the blade - then swing it in one full circle: a giant glowing blade
     *  sweeps round you 7 blocks out, rings of light rolling out behind it, and everything within 7 blocks takes 3x a
     *  blow and is thrown outward. 20 s. */
    public static boolean sovereignsVerdict(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        final double R = 7.0;
        final int CHARGE = 20;
        sound(sl, p.position(), SoundEvents.BEACON_ACTIVATE, 1.0f, 1.6f);
        for (int k = 0; k < CHARGE; k += 4) {   // the gathering: rings drawing in, a pillar climbing off the blade
            final int at = k;
            AstaFx.later(k, () -> {
                if (!alive(p)) return;
                Vec3 c = p.position().add(0, 0.05, 0);
                float f = 1f - at / (float) CHARGE;
                FxSend.ring(sl, Fx.ICE | FxPacket.KEEP_SHAPE, c, (float) R * f + 0.6f, (float) R * f + 0.2f, 0.12f, 6);
                FxSend.beam(sl, Fx.ICE | FxPacket.KEEP_SHAPE, p.position().add(0, 1.2, 0), p.position().add(0, 1.2 + 2.5 + at * 0.15, 0), 0.25f + at * 0.01f, 6);
                if (at % 8 == 0) sound(sl, p.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 0.8f + at * 0.04f);
            });
        }
        AstaFx.later(CHARGE, () -> {   // the verdict
            if (!alive(p)) return;
            Vec3 c = p.position();
            float yaw = p.getYRot();
            Vec3 mid = c.add(0, 1.1, 0);
            // the blade itself: glowing walls sweeping a full circle in 5 ticks, from close in out to the edge
            for (double r : new double[]{2.2, 3.8, 5.4, R})
                FxSend.wall(sl, Fx.ICE | FxPacket.KEEP_SHAPE, mid, new Vec3(1, 0, 0), new Vec3(0, 0, 1), (float) r, (float) r, yaw, yaw + 360, r == R ? 0.55f : 0.3f, 14, 5, 0);
            FxSend.wall(sl, Fx.LIGHTNING | FxPacket.KEEP_SHAPE, mid, new Vec3(1, 0, 0), new Vec3(0, 0, 1), (float) R + 0.3f, (float) R + 0.3f, yaw, yaw + 360, 0.15f, 12, 5, 0);
            AstaFx.later(5, () -> {   // the circle closes: rings roll out across the ground, a flash, the ground shakes
                FxSend.ring(sl, Fx.ICE | FxPacket.KEEP_SHAPE, c.add(0, 0.05, 0), 0.5f, (float) R + 1.5f, 0.35f, 16);
                FxSend.ring(sl, Fx.LIGHTNING | FxPacket.KEEP_SHAPE, c.add(0, 0.1, 0), 0.3f, (float) R + 0.5f, 0.15f, 12);
                FxSend.beam(sl, Fx.ICE | FxPacket.KEEP_SHAPE, c, c.add(0, 9, 0), 1.0f, 12);
                FxSend.impactFrame(sl, mid, 4, FxSend.FRAME_LIGHT, 24);
                FxSend.shake(sl, c, 0.6f, 10, 20);
                slam(sl, c, (float) R * 0.5f);
            });
            sound(sl, c, SoundEvents.PLAYER_ATTACK_SWEEP, 1.4f, 0.5f);
            sound(sl, c, SoundEvents.TRIDENT_THUNDER, 0.7f, 1.4f);
            float dmg = (float) p.getAttributeValue(Attributes.ATTACK_DAMAGE) * 3f;
            for (LivingEntity t : Kit.near(sl, p, c.add(0, 1, 0), R)) {
                if (!foe(p, t)) continue;
                t.invulnerableTime = 0;
                AstaFx.damage(p, t, dmg, "sovereigns_verdict");
                Vec3 out = t.position().subtract(c).multiply(1, 0, 1);
                out = out.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : out.normalize();
                t.push(out.x * 0.9, 0.35, out.z * 0.9); t.hurtMarked = true;
                shadowHit(sl, t, 3);
            }
        });
        return true;
    }

    /** Duskglass Kris - Phantom Edges: three ghost knives circle you for 10 s; each strikes a foe that comes within
     *  3 blocks (then needs a breath before it strikes again). Drawn on the client (ShadowClient kind 16). */
    public static boolean phantomEdges(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        FxPacket f = pk(16, p.position(), 200); f.v = new Vec3(p.getId(), 0, 0); FxSend.sendNear(sl, p.position(), f);
        play(sl, p.position(), "shadow_rise", 0.8f, 1.8f);
        float base = (float) p.getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.6f;
        final int[] ready = new int[3];
        for (int tick = 5; tick <= 200; tick += 5) {
            final int now = tick;
            AstaFx.later(tick, () -> {
                if (!alive(p)) return;
                List<LivingEntity> near = new ArrayList<>(Kit.near(sl, p, p.position(), 3.0));
                near.removeIf(t -> !foe(p, t));
                for (int k = 0; k < 3 && !near.isEmpty(); k++) {
                    if (ready[k] > now) continue;
                    LivingEntity t = near.get(k % near.size());
                    ready[k] = now + 15;
                    double an = now * 0.25 + k * TAU / 3;
                    Vec3 at = p.position().add(Math.cos(an) * 1.3, 1.1, Math.sin(an) * 1.3);
                    dash(sl, at, t.position().add(0, t.getBbHeight() * 0.5, 0));
                    t.invulnerableTime = 0;
                    AstaFx.damage(p, t, base, "phantom_edges");
                    shadowHit(sl, t, 1);
                }
            });
        }
        return true;
    }

    // ================================================================== Challenges (the Shadow Army menu)
    /** Its living form steps out to answer the challenge. */
    public static void challengeFx(ServerLevel sl, ShadowSoldier s) {
        slam(sl, s.position(), 3.5f);
        sound(sl, s.position(), SoundEvents.WARDEN_ROAR, 0.4f, s instanceof com.ironspellgrimoire.entity.OathboundKnight ? 0.9f : 1.5f);
        sound(sl, s.position(), SoundEvents.ANVIL_LAND, 0.5f, 0.6f);
    }

    /** A beaten exclusive shadow's remnant: Rise always takes it. */
    public static void exclusiveRemnant(ServerPlayer p, ShadowSoldier s) {
        int id = nextRemnant++;
        Remnant r = new Remnant(id, p.getUUID(), s.kind(), s.position(), -1f, p.serverLevel().getGameTime() + 1200, new int[]{3});
        REMNANTS.put(id, r);
        AddonNetwork.send(PacketDistributor.PLAYER.with(() -> p), new ShadowPackets.Remnant(id, s.kind(), r.pos(), s.getYRot(), 1f, 1200, 3));
    }

    // ================================================================== 9. Sovereign's Domain (mode)
    public static void sovereignsDomain(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        final int T = 1200;   // one minute
        Moves.mode(p, MODE, T, 20, pl -> {
            for (ShadowSoldier s : ShadowArmy.out(pl)) TimedModifiers.apply(s, Attributes.MOVEMENT_SPEED, DOMAIN_SPEED, "Sovereign's Domain", 0.3, AttributeModifier.Operation.MULTIPLY_BASE, 25);
        });
        domainFx(sl, p, T);
        play(sl, p.position(), "shadow_gate", 1.3f, 0.6f);
        sound(sl, p.position(), SoundEvents.WARDEN_ROAR, 0.35f, 1.4f);
        long now = sl.getGameTime();   // the fallen rise again at once
        for (var t : ShadowArmy.list(p)) { CompoundTag c = (CompoundTag) t; if (c.getLong("rec") > now) c.putLong("rec", now); }
        ShadowArmy.keep(p);
    }

    // ================================================================== 10. Night Legion
    public static void nightLegion(ServerPlayer p, float power) {
        boolean limit = LimitBreak.active(p);
        ServerLevel sl = p.serverLevel();
        final Vec3 d = look(p), c = p.position();
        final float L = 18f;
        legion(sl, c, d, L, 60);
        play(sl, c, "shadow_gate", 1.4f, 0.5f);
        sound(sl, c, SoundEvents.WARDEN_EMERGE, 0.6f, 0.9f);
        List<CompoundTag> all = new ArrayList<>();
        for (var t : ShadowArmy.list(p)) all.add((CompoundTag) t);
        int n = Math.min(all.size(), 24);
        Vec3 rt = new Vec3(-d.z, 0, d.x);
        for (int i = 0; i < n; i++) {   // the whole army bursts out of the ground in ranks, then charges
            CompoundTag e = all.get(i);
            if (ShadowArmy.summoned(p, e.getInt("id")) != null) continue;
            Vec3 at = c.add(d.scale(2 + (i / 6) * 1.6)).add(rt.scale(((i % 6) - 2.5) * 1.5));
            final int k = i;
            AstaFx.later(k % 6 * 2, () -> {
                if (!alive(p)) return;
                ShadowSoldier s = ShadowArmy.raise(p, e, at, 240);
                if (s != null) s.hunting = 240;
            });
        }
        final int waves = limit ? 2 : 1;
        for (int w = 0; w < waves; w++) {
            AstaFx.later(10 + w * 30, () -> {   // a tide of shadow rolls ahead with them
                if (!alive(p)) return;
                legion(sl, c, d, L, 40);
                FxSend.shake(sl, c, 0.4f, 10, 24);
                Vec3 side = rt.scale(3.5);
                for (LivingEntity t : Kit.along(sl, p, c.add(0, 1, 0), c.add(0, 1, 0).add(d.scale(L)), 3.5)) {
                    t.invulnerableTime = 0;
                    AstaFx.damage(p, t, power * (limit ? 1.3f : 1f), "night_legion");
                    Kit.push(t, d, 0.6 * GravityFx.give(t), 0.3);
                    shadowHit(sl, t, 3);
                }
            });
        }
        if (n == 0) p.displayClientMessage(Component.literal("Your army is empty - only the tide answers").withStyle(ChatFormatting.GRAY), true);
    }

    /** Surpassing Limits: a short Sovereign's Domain. */
    public static void awaken(ServerPlayer p) {
        Moves.mode(p, MODE, 200, 20, pl -> { });
        domainFx(p.serverLevel(), p, 200);
    }
}
