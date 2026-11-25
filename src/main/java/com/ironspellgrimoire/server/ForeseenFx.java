package com.ironspellgrimoire.server;

import static com.ironspellgrimoire.server.FxUtil.*;

import com.ironspellgrimoire.IronSpellGrimoire;
import com.ironspellgrimoire.entity.AddonBoss;
import com.ironspellgrimoire.entity.ForeseenDouble;
import com.ironspellgrimoire.registry.AddonEntities;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The Grimoire of the Foreseen: you see a blow before it lands (every name, look and line here is original).
 * <ul>
 *   <li><b>Foresight</b> (passive): a blow aimed at you is seen first - an outline of it, a ring at your feet - and it
 *       waits out your warning (0.3 s, growing with the Sight level up to 1.05 s, and more from the tree). When the
 *       warning runs out, if you are out of the striker's reach, it misses: a dodge. At first only the nearest foe
 *       hunting you is foreseen; mastery 2 or Crowd Reading foresee them all. A creeper about to burst shows its reach.</li>
 *   <li>Pages: Step Through, Read Intent, Punish, False Future, Snap Back, Inevitable, Seen It Before, and the ultimate
 *       Rewrite.</li>
 *   <li>Mastery: 2 every foe hunting you is foreseen; 3 a dodge gives back 10 mana; 5 once a minute a killing blow
 *       simply doesn't happen.</li>
 * </ul>
 * Movement and timing only - no block is touched; the speed boost is an attribute modifier, never a status effect.
 */
@Mod.EventBusSubscriber(modid = IronSpellGrimoire.MODID)
public final class ForeseenFx {
    private ForeseenFx() {}

    static final int PAL = 13, PALE = 20;   // blue lightning's deep blue, mirror silver
    static final UUID DODGE = UUID.fromString("7a1e0000-0000-4000-8000-0000000000f6");

    record Held(LivingEntity by, DamageSource src, float amount, long lands) {}
    record Past(Vec3 at, float hp, long t) {}
    record Taken(long t, float amount, LivingEntity by) {}
    record Strike(Vec3 at, long when) {}

    static final class S {
        final List<Held> held = new ArrayList<>();
        final ArrayDeque<Past> past = new ArrayDeque<>();
        final ArrayDeque<Taken> ledger = new ArrayDeque<>();
        final List<Strike> strikes = new ArrayList<>();
        final Set<String> seen = new HashSet<>();
        int streak, punishes;
        long lastDodge = -1000, untouchableUntil, glimpseUntil, seenUntil, lastSave = -100000, unforeseenAt = -1;
    }
    static final Map<UUID, S> STATE = new HashMap<>();
    static S s(ServerPlayer p) { return STATE.computeIfAbsent(p.getUUID(), k -> new S()); }
    static final Map<LivingEntity, Long> MARK = new WeakHashMap<>(), EXPOSED = new WeakHashMap<>();
    static final Map<LivingEntity, LivingEntity> THREAD = new WeakHashMap<>();
    static final Map<LivingEntity, Long> THREAD_UNTIL = new WeakHashMap<>();
    private static boolean landing, threading;

    public static boolean has(Player p) { return BookTree.kind(p) == BookTree.FORESEEN; }
    static int r(ServerPlayer p, String id) { return BookTree.rank(p, id); }
    static int mastery(ServerPlayer p) { return GrimoireMastery.level(BookTree.book(p)); }
    static boolean boss(LivingEntity t) { return t instanceof AddonBoss || TimeStop.isBoss(t); }

    /** The warning, in ticks. */
    public static int warning(ServerPlayer p) { return 6 + BookTree.level(p) / 2 + 3 * r(p, "s1") + (r(p, "k1") > 0 ? 10 : 0); }
    public static double range(ServerPlayer p) { return 8 + BookTree.level(p) * 0.53 + 6 * r(p, "s2"); }

    /** Is a blow from this attacker foreseen? */
    static boolean foreseen(ServerPlayer p, LivingEntity by) {
        if (r(p, "k2") > 0) return false;
        if (boss(by) && r(p, "s5") <= 0) return false;
        if (by.distanceTo(p) > range(p)) return false;
        long now = p.level().getGameTime();
        if (now < s(p).glimpseUntil || r(p, "s3") > 0 || mastery(p) >= 2) return true;
        Long mk = MARK.get(by);
        if (mk != null && mk > now) return true;
        // only the nearest foe hunting you
        LivingEntity near = null; double bd = 1e9;
        for (Mob m : p.serverLevel().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(range(p)), x -> x.getTarget() == p && x.isAlive())) {
            double d = m.distanceToSqr(p); if (d < bd) { bd = d; near = m; }
        }
        return near == null || near == by;
    }

    // ------------------------------------------------------------------ the held blow
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttack(LivingAttackEvent e) {
        if (landing || !(e.getEntity() instanceof ServerPlayer p) || !has(p)) return;
        testSeen++;
        Entity src = e.getSource().getEntity();
        if (!(src instanceof LivingEntity by) || by == p || e.getSource().getDirectEntity() != by || !BookTree.melee(e.getSource())) return;   // blows only
        S s = s(p);
        long now = p.level().getGameTime();
        if (r(p, "k2") > 0) {   // Closed Eyes: one in four simply misses
            if (p.getRandom().nextInt(4) == 0) { e.setCanceled(true); dodged(p, by); }
            return;
        }
        if (!foreseen(p, by)) { s.unforeseenAt = now; return; }
        e.setCanceled(true);
        if (now < s.untouchableUntil) { dodged(p, by); return; }
        for (Held h : s.held) if (h.by() == by) return;   // one held blow per striker
        Long mk = MARK.get(by);
        int w = warning(p) + (mk != null && mk > now ? 10 : 0);
        s.held.add(new Held(by, e.getSource(), e.getAmount(), now + w));
        ServerLevel sl = p.serverLevel();
        Vec3 to = mid(p).subtract(mid(by)).normalize();
        FxSend.slash(sl, PALE, mid(by).add(to.scale(0.8)), to, new Vec3(0, 1, 0), 0.6f, 1.4f, w, 0);
        FxSend.ring(sl, PAL, p.position().add(0, 0.05, 0), 0.2f, 0.9f, 0.04f, w);
        play(sl, p.position(), "fore_warn", 0.6f, 1.6f);
    }

    static double reach(LivingEntity by, Player p) { return 1.0 + by.getBbWidth() * 2 + p.getBbWidth() * 0.5; }

    static void dodged(ServerPlayer p, LivingEntity by) {
        S s = s(p);
        ServerLevel sl = p.serverLevel();
        long now = sl.getGameTime();
        s.streak++;
        s.lastDodge = now;
        ModeHud.show(p, "punish_ready", 40);
        FxSend.ring(sl, PALE, mid(p), 0.3f, 1.2f, 0.05f, 8);
        play(sl, p.position(), "fore_dodge", 0.9f, 1.2f + 0.05f * Math.min(6, s.streak));
        BookTree.grow(p, BookTree.FORESEEN, 1);
        float mana = (r(p, "e2") > 0 ? 15 : 0) + (mastery(p) >= 3 ? 10 : 0);
        if (mana > 0) {
            var md = io.redspace.ironsspellbooks.api.magic.MagicData.getPlayerMagicData(p);
            float max = (float) p.getAttributeValue(io.redspace.ironsspellbooks.api.registry.AttributeRegistry.MAX_MANA.get());
            md.setMana(Math.min(max, md.getMana() + mana));
        }
        if (r(p, "e2") > 0) TimedModifiers.apply(p, Attributes.MOVEMENT_SPEED, DODGE, "Perfect Dodge", 0.2, AttributeModifier.Operation.MULTIPLY_TOTAL, 30);
        if (r(p, "c2") > 0 && by != null) EXPOSED.put(by, now + 80);
        if (r(p, "e4") > 0 && s.streak >= 3) {
            s.untouchableUntil = now + 80; s.streak = 0;
            ModeHud.show(p, "untouchable", 80);
            p.displayClientMessage(Component.literal("Untouchable").withStyle(ChatFormatting.AQUA), true);
        }
    }

    // ------------------------------------------------------------------ blows that land, and blows you deal
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onHurt(LivingHurtEvent e) {
        LivingEntity victim = e.getEntity();
        Entity src = e.getSource().getEntity();
        if (victim.level().isClientSide) return;
        long now = victim.level().getGameTime();
        // you strike: Exposed, Fate Thread
        if (src instanceof ServerPlayer p && has(p) && victim != p) {
            Long ex = EXPOSED.get(victim);
            if (ex != null && ex > now) e.setAmount(e.getAmount() * 1.15f);
            LivingEntity other = THREAD.get(victim);
            Long tu = THREAD_UNTIL.get(victim);
            if (!threading && other != null && tu != null && tu > now && other.isAlive()) {
                float half = e.getAmount() * 0.5f;
                AstaFx.later(1, () -> { threading = true; try { if (other.isAlive()) AstaFx.damage(p, other, half, "fore_thread"); } finally { threading = false; } });
                FxSend.trail(p.serverLevel(), PALE, mid(victim), mid(other), 0.04f, 6);
            }
        }
        if (!(victim instanceof ServerPlayer p) || !has(p)) return;
        S s = s(p);
        // Seen It Before
        if (now < s.seenUntil) {
            String key = e.getSource().getMsgId() + "|" + (src == null ? "" : src.getType().toString());
            if (!s.seen.add(key)) { e.setCanceled(true); FxSend.ring(p.serverLevel(), PALE, mid(p), 0.2f, 1f, 0.05f, 6); return; }
        }
        // Almighty Eye: what you did not see hurts more
        if (r(p, "k1") > 0 && s.unforeseenAt == now && !landing) e.setAmount(e.getAmount() * 1.15f);
        // mastery 5: once a minute, a killing blow doesn't happen
        if (mastery(p) >= 5 && e.getAmount() >= p.getHealth() && now - s.lastSave > 1200) {
            s.lastSave = now;
            e.setCanceled(true);
            p.displayClientMessage(Component.literal("You saw that one coming.").withStyle(ChatFormatting.AQUA), true);
            FxSend.ring(p.serverLevel(), PALE, mid(p), 0.3f, 2.4f, 0.1f, 12);
            play(p.serverLevel(), p.position(), "fore_rewrite", 1f, 1.4f);
            return;
        }
        s.streak = landing ? 0 : s.streak;
        if (e.getAmount() > 0) {
            s.ledger.addLast(new Taken(now, e.getAmount(), src instanceof LivingEntity le ? le : null));
            while (s.ledger.size() > 12) s.ledger.removeFirst();
        }
    }

    // ------------------------------------------------------------------ the tick
    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || !p.isAlive()) return;
        if (!has(p)) { STATE.remove(p.getUUID()); return; }
        S s = s(p);
        ServerLevel sl = p.serverLevel();
        long now = sl.getGameTime();
        // the held blows: land, or miss
        for (Iterator<Held> it = s.held.iterator(); it.hasNext(); ) {
            Held h = it.next();
            if (now < h.lands()) continue;
            it.remove();
            LivingEntity by = h.by();
            if (by.isAlive() && by.level() == sl && by.distanceTo(p) <= reach(by, p) && now >= s.untouchableUntil) {
                landing = true;
                try { p.invulnerableTime = 0; p.hurt(h.src(), h.amount()); } finally { landing = false; }
                s.streak = 0;
            } else dodged(p, by);
        }
        // the past, for Snap Back and Borrowed Second (every 5 ticks, 6 s of it)
        if (now % 5 == 0) {
            s.past.addLast(new Past(p.position(), p.getHealth(), now));
            while (s.past.size() > 24) s.past.removeFirst();
        }
        double rr = range(p);
        // a creeper about to burst shows how far it reaches
        if (now % 5 == 0) for (Creeper c : sl.getEntitiesOfClass(Creeper.class, p.getBoundingBox().inflate(rr), x -> x.getSwellDir() > 0))
            FxSend.ring(sl, PAL, c.position().add(0, 0.1, 0), 2.6f, 3.0f, 0.04f, 6);
        // Arrow Sight
        if (r(p, "s4") > 0 && now % 2 == 0)
            for (Projectile pr : sl.getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(rr), x -> x.getOwner() != p && x.getDeltaMovement().lengthSqr() > 0.05 && !x.getPersistentData().getBoolean("isg_seen"))) {
                Vec3 to = mid(p).subtract(pr.position());
                if (to.normalize().dot(pr.getDeltaMovement().normalize()) < 0.8) continue;
                pr.getPersistentData().putBoolean("isg_seen", true);
                FxSend.trail(sl, PALE, pr.position(), pr.position().add(pr.getDeltaMovement().scale(Math.min(20, to.length() / pr.getDeltaMovement().length() + 2))), 0.04f, 10);
            }
        // Glimpse: every foe's next blow drawn
        if (now < s.glimpseUntil && now % 10 == 0)
            for (Mob m : sl.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(rr), x -> x.getTarget() == p && x.isAlive())) {
                FxSend.ring(sl, PAL, m.position().add(0, 0.1, 0), 0.6f, 0.9f, 0.04f, 10);
                FxSend.trail(sl, PALE, mid(m), mid(m).add(mid(p).subtract(mid(m)).normalize().scale(Math.min(3, m.distanceTo(p)))), 0.04f, 10);
            }
        // marks
        if (now % 20 == 0) for (Map.Entry<LivingEntity, Long> en : MARK.entrySet())
            if (en.getValue() > now && en.getKey().isAlive() && en.getKey().level() == sl && en.getKey().distanceToSqr(p) < 1600)
                FxSend.ring(sl, PALE, en.getKey().position().add(0, en.getKey().getBbHeight() + 0.4, 0), 0.15f, 0.4f, 0.04f, 20);
        // foretold strikes
        for (Iterator<Strike> it = s.strikes.iterator(); it.hasNext(); ) {
            Strike k = it.next();
            if (now < k.when()) { if ((k.when() - now) % 10 == 0) FxSend.ring(sl, PAL, k.at().add(0, 0.05, 0), 1.7f, 2f, 0.04f, 10); continue; }
            it.remove();
            FxSend.beam(sl, PALE, k.at().add(0, 6, 0), k.at(), 0.4f, 8);
            FxSend.ring(sl, PAL, k.at().add(0, 0.1, 0), 0.2f, 2.2f, 0.12f, 10);
            play(sl, k.at(), "fore_strike", 1.1f, 1f);
            for (LivingEntity t : enemies(sl, p, new AABB(k.at(), k.at()).inflate(2, 2.5, 2))) AstaFx.damage(p, t, 10f, "inevitable");
        }
    }

    // ------------------------------------------------------------------ the pages
    /** Step Through: a quick step along where you look (free if a blow is held against you). */
    public static void stepThrough(ServerPlayer p, float power) {
        S s = s(p);
        ServerLevel sl = p.serverLevel();
        Vec3 from = p.position();
        Vec3 d = new Vec3(p.getLookAngle().x, 0, p.getLookAngle().z).normalize();
        double dist = 5 + r(p, "e1");
        Vec3 to = clip(sl, p, from.add(0, 0.5, 0), from.add(0, 0.5, 0).add(d.scale(dist))).subtract(d.scale(0.5)).add(0, -0.5, 0);
        p.teleportTo(to.x, to.y, to.z);
        p.fallDistance = 0;
        FxSend.trail(sl, PALE, from.add(0, 1, 0), to.add(0, 1, 0), 0.25f, 8);
        play(sl, from, "fore_step", 1f, 1.2f);
        if (!s.held.isEmpty()) {   // free when you're dodging
            var md = io.redspace.ironsspellbooks.api.magic.MagicData.getPlayerMagicData(p);
            float max = (float) p.getAttributeValue(io.redspace.ironsspellbooks.api.registry.AttributeRegistry.MAX_MANA.get());
            md.setMana(Math.min(max, md.getMana() + 25));
        }
        if (r(p, "e3") > 0) spawnDouble(p, from, 60);
    }

    /** Read Intent: mark the foe you look at for 10 s - its blows always foreseen, half a second sooner (2 more with Second Glance). */
    public static void readIntent(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        LivingEntity t = aimed(p, 20, 10);
        play(sl, p.position(), "fore_read", 1f, 1f);
        if (t == null) return;
        long until = sl.getGameTime() + 200;
        MARK.put(t, until);
        ModeHud.show(p, "read_intent", 200);
        FxSend.ring(sl, PALE, mid(t), 0.2f, 1.2f, 0.06f, 10);
        if (r(p, "t2") > 0) {
            int n = 0;
            for (LivingEntity o : enemies(sl, p, t.getBoundingBox().inflate(8))) { if (o == t || n >= 2) continue; MARK.put(o, until); FxSend.ring(sl, PALE, mid(o), 0.2f, 1f, 0.05f, 10); n++; }
        }
    }

    /** Punish: a cut at the foe before you - three times as hard just after a dodge. */
    public static void punish(ServerPlayer p, float power) {
        S s = s(p);
        ServerLevel sl = p.serverLevel();
        LivingEntity t = aimed(p, 6, 25);
        play(sl, p.position(), "fore_punish", 1f, 1f);
        Vec3 look = p.getLookAngle();
        FxSend.slash(sl, PALE, p.getEyePosition().add(look.scale(1.5)), look, new Vec3(0, 1, 0), 1.2f, 2.2f, 6, 0);
        if (t == null) return;
        boolean after = sl.getGameTime() - s.lastDodge <= 40;
        float dmg = 7f * (1 + 0.15f * r(p, "c1")) * (after ? 3f : 1f);
        if (r(p, "c4") > 0 && ++s.punishes % 4 == 0) dmg *= 2f;
        AstaFx.damage(p, t, dmg, "punish");
        if (after) { FxSend.ring(sl, PAL, mid(t), 0.2f, 1.6f, 0.1f, 8); play(sl, t.position(), "fore_strike", 0.9f, 1.4f); }
    }

    /** False Future: a pale double where the future says you'll be; foes near strike at it for 6 s. */
    public static void falseFuture(ServerPlayer p, float power) {
        Vec3 at = p.position().add(new Vec3(p.getLookAngle().x, 0, p.getLookAngle().z).normalize().scale(1.5));
        spawnDouble(p, at, 120);
        play(p.serverLevel(), at, "fore_double", 1f, 1f);
    }

    static void spawnDouble(ServerPlayer p, Vec3 at, int life) {
        ServerLevel sl = p.serverLevel();
        ForeseenDouble d = AddonEntities.FORESEEN_DOUBLE.get().create(sl);
        if (d == null) return;
        d.moveTo(at.x, at.y, at.z, p.getYRot(), 0);
        d.setYHeadRot(p.getYRot()); d.yBodyRot = p.getYRot();
        d.owner = p.getUUID();
        d.life = life;
        sl.addFreshEntity(d);
        for (Mob m : sl.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(16), x -> x.isAlive() && x.getTarget() == p && !boss(x))) m.setTarget(d);
        FxSend.ring(sl, PALE, at.add(0, 0.05, 0), 0.2f, 1.2f, 0.04f, 10);
    }

    /** Snap Back: back to where you stood 3 s ago (more with Deep Memory), with the health you had then if it was more. */
    public static void snapBack(ServerPlayer p, float power) { rewind(p, 60 + 20 * r(p, "t1"), true); }

    static boolean rewind(ServerPlayer p, int ticks, boolean hp) {
        S s = s(p);
        long want = p.level().getGameTime() - ticks;
        Past best = null;
        for (Past x : s.past) if (x.t() <= want) best = x;
        if (best == null) best = s.past.peekFirst();
        if (best == null) return false;
        ServerLevel sl = p.serverLevel();
        FxSend.trail(sl, PALE, p.position().add(0, 1, 0), best.at().add(0, 1, 0), 0.2f, 10);
        p.teleportTo(best.at().x, best.at().y, best.at().z);
        p.fallDistance = 0;
        if (hp && best.hp() > p.getHealth()) p.setHealth(Math.min(p.getMaxHealth(), best.hp()));
        FxSend.ring(sl, PAL, best.at().add(0, 0.05, 0), 0.2f, 1.2f, 0.05f, 10);
        play(sl, best.at(), "fore_rewind", 1f, 1f);
        s.held.clear();   // the blows aimed at where you were miss
        return true;
    }

    /** Inevitable: a strike where the foe you look at is going to be in a second (three points of its path, with Inevitable Chain). */
    public static void inevitable(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        LivingEntity t = aimed(p, 20, 12);
        play(sl, p.position(), "fore_read", 1f, 0.8f);
        if (t == null) return;
        Vec3 v = t.position().subtract(t.xo, t.yo, t.zo);
        double[] when = r(p, "c3") > 0 ? new double[]{10, 20, 30} : new double[]{20};
        for (double w : when) {
            Vec3 at = t.position().add(v.x * w, 0, v.z * w);
            FxSend.ring(sl, PAL, at.add(0, 0.05, 0), 1.2f, 1.5f, 0.04f, 10);
            AstaFx.later(10, () -> {
                FxSend.beam(sl, PALE, at.add(0, 5, 0), at, 0.3f, 8);
                play(sl, at, "fore_strike", 1f, 1.1f);
                for (LivingEntity o : enemies(sl, p, new AABB(at, at).inflate(1.5, 2.5, 1.5))) AstaFx.damage(p, o, 9f, "inevitable");
            });
        }
    }

    /** Seen It Before: for 10 s (15 with Seen Twice), a kind of blow that has struck you once can't strike you again. */
    public static void seenItBefore(ServerPlayer p, float power) {
        S s = s(p);
        s.seen.clear();
        s.seenUntil = p.level().getGameTime() + 200 + 100 * r(p, "t3");
        ModeHud.show(p, "seen_it_before", 200 + 100 * r(p, "t3"));
        FxSend.ring(p.serverLevel(), PALE, mid(p), 0.8f, 1.0f, 0.04f, (int) (s.seenUntil - p.level().getGameTime()));
        play(p.serverLevel(), p.position(), "fore_read", 1f, 1.3f);
    }

    /** Rewrite: the last blow that struck you (in 5 s; 10 with Rewrite Anything) never happened - and its striker takes it. */
    public static void rewrite(ServerPlayer p, float power) {
        S s = s(p);
        ServerLevel sl = p.serverLevel();
        long now = sl.getGameTime(), win = r(p, "k3") > 0 ? 200 : 100;
        int count = r(p, "t4") > 0 ? 2 : 1;
        List<Taken> undo = new ArrayList<>();
        for (Iterator<Taken> it = s.ledger.descendingIterator(); it.hasNext() && undo.size() < count; ) { Taken x = it.next(); if (now - x.t() <= win) undo.add(x); }
        play(sl, p.position(), "fore_rewrite", 1.3f, 1f);
        FxSend.ring(sl, PALE, p.position().add(0, 0.1, 0), 0.3f, 3f, 0.08f, 14);
        if (undo.isEmpty()) { p.displayClientMessage(Component.literal("Nothing to rewrite").withStyle(ChatFormatting.GRAY), true); return; }
        for (Taken x : undo) {
            s.ledger.remove(x);
            p.heal(x.amount() * (r(p, "k4") > 0 ? 1.3f : 1f));
            LivingEntity by = x.by();
            if (by != null && by.isAlive() && by != p) {
                FxSend.trail(sl, PALE, mid(p), mid(by), 0.08f, 8);
                AstaFx.damage(p, by, x.amount(), "rewrite");
                if (r(p, "k4") > 0) AstaFx.later(8, () -> { if (by.isAlive()) AstaFx.damage(p, by, x.amount(), "rewrite"); });
            }
        }
    }

    // ------------------------------------------------------------------ the G wheel
    static boolean wheel(ServerPlayer p, int slot) {
        S s = s(p);
        ServerLevel sl = p.serverLevel();
        switch (slot) {
            case 0 -> {
                s.glimpseUntil = sl.getGameTime() + 100;
                ModeHud.show(p, "fore_glimpse", 100);
                play(sl, p.position(), "fore_read", 1.1f, 1.2f);
                FxSend.ring(sl, PALE, p.position().add(0, 0.1, 0), 0.3f, (float) range(p), 0.06f, 12);
            }
            case 1 -> { return rewind(p, 20, false); }
            case 2 -> {
                Vec3 eye = p.getEyePosition();
                Vec3 at = BossKit.ground(sl, clip(sl, p, eye, eye.add(p.getLookAngle().scale(20))));
                s.strikes.add(new Strike(at, sl.getGameTime() + 40));
                play(sl, at, "fore_warn", 1f, 0.9f);
            }
            default -> {
                LivingEntity a = aimed(p, 20, 12);
                if (a == null) { p.displayClientMessage(Component.literal("Look at a foe to tie").withStyle(ChatFormatting.GRAY), true); return false; }
                LivingEntity b = null; double bd = 64;
                for (LivingEntity o : enemies(sl, p, a.getBoundingBox().inflate(8))) { if (o == a) continue; double d = o.distanceToSqr(a); if (d < bd) { bd = d; b = o; } }
                if (b == null) { p.displayClientMessage(Component.literal("No second foe within 8 blocks").withStyle(ChatFormatting.GRAY), true); return false; }
                long until = sl.getGameTime() + 200;
                THREAD.put(a, b); THREAD.put(b, a); THREAD_UNTIL.put(a, until); THREAD_UNTIL.put(b, until);
                ModeHud.show(p, "fore_thread", 200);
                FxSend.trail(sl, PALE, mid(a), mid(b), 0.05f, 200);
                play(sl, a.position(), "fore_read", 1f, 0.7f);
            }
        }
        return true;
    }

    /** For the wheel's middle: warning ticks, reach, dodge streak. */
    static int[] extra(ServerPlayer p) { return new int[]{warning(p), (int) range(p), s(p).streak}; }

    @SubscribeEvent public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) { STATE.remove(e.getEntity().getUUID()); }

    // test hooks
    public static void testReset(ServerPlayer p) { STATE.remove(p.getUUID()); }
    public static int testSeen;
    public static int testHeld(ServerPlayer p) { return s(p).held.size(); }
    public static long testLastDodge(ServerPlayer p) { return s(p).lastDodge; }
}
