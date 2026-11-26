package com.ironspellgrimoire.server;

import static com.ironspellgrimoire.server.FxUtil.*;

import com.ironspellgrimoire.IronSpellGrimoire;
import com.ironspellgrimoire.entity.AddonBoss;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The Vector Grimoire: the direction of force is yours to choose (every name, look and line here is original).
 * <ul>
 *   <li><b>Redirection</b> (passive): some of every projectile that strikes you flies back at its sender, and some of
 *       every blow's force goes back to the striker. Both grow with the Vector level (server/BookTree): 15% of
 *       projectiles and 10% of a blow at first, up to 75% and 49%. The G wheel's Stance Shift leans it: Reflect
 *       (projectiles), Return (blows) or Flow (no fall damage, faster, a little less of both).</li>
 *   <li>Pages: Redirect, Recoil Step, Return to Sender, Pressure Gust, Pulse Touch, Wind Vector, Plasma Point, and the
 *       ultimate Vector Absolute.</li>
 *   <li>Mastery: 2 reflected shots with no sender seek the nearest foe; 3 reflected shots fly 30% faster; 5 the first
 *       blow of every fight comes back in full.</li>
 * </ul>
 * Force and effects only - no block is ever touched; slows and speed are attribute modifiers, never status effects.
 */
@Mod.EventBusSubscriber(modid = IronSpellGrimoire.MODID)
public final class VectorFx {
    private VectorFx() {}

    static final int PAL = Fx.SILVER, PALE = 11, BLACK = 47, HOT = Fx.LIGHTNING;   // (47: black with a blue sheen)   // white vectors, black jets, a white-hot core

    // ------------------------------------------------------------------ the look (client/VectorFxClient, FxPacket shape 100)
    // White vectors with black edges and sharp heads, hard angles where something is turned, warped air, a white-hot
    // plasma point, a wrung column of air, and in Vector Absolute two jagged black jets out of your back.
    static com.ironspellgrimoire.network.FxPacket fx(int kind, Vec3 c, int life) {
        com.ironspellgrimoire.network.FxPacket p = new com.ironspellgrimoire.network.FxPacket();
        p.shape = 100; p.id = FxSend.nextId(); p.h = kind; p.c = c; p.life = life; p.v = new Vec3(-1, 0, 0); p.a1 = (float) (Math.random() * 100);
        p.u = c; p.d = c;
        return p;
    }
    static com.ironspellgrimoire.network.FxPacket on(int kind, LivingEntity e, int life) { var p = fx(kind, e.position(), life); p.v = new Vec3(e.getId(), 0, 0); return p; }
    static void send(ServerLevel sl, com.ironspellgrimoire.network.FxPacket p) { FxSend.sendNear(sl, p.c, p); }

    /** One vector a -> b. */
    static void arrow(ServerLevel sl, Vec3 a, Vec3 b, float w, int life) {
        var p = fx(0, a, life + 2); p.u = b; p.d = b; p.r = w; send(sl, p);
    }
    /** A bundle of parallel vectors along dir through c. */
    static void arrows(ServerLevel sl, Vec3 c, Vec3 dir, int n, double len, double spread) {
        var p = fx(9, c, 8); p.d = dir.normalize(); p.r = (float) len; p.r2 = (float) spread; p.a0 = n; send(sl, p);
    }
    /** Something turned: the way it came, a hard angle with a star of light, the way it goes now. */
    static void deflect(ServerLevel sl, Vec3 at, Vec3 in, Vec3 out) {
        Vec3 i = in.lengthSqr() < 1e-4 ? out.scale(-1) : in;
        Vec3 o = out.lengthSqr() < 1e-4 ? new Vec3(0, 1, 0) : out.normalize();
        var p = fx(0, at.subtract(i.normalize().scale(1.6)), 10); p.u = at; p.d = at.add(o.scale(2.4)); p.r = 0.05f; send(sl, p);
    }
    /** The air warped round c. */
    static void warp(ServerLevel sl, Vec3 c, float r, int life) { var p = fx(1, c, life + 4); p.r = r; send(sl, p); }
    /** Vector Absolute's black jets (re-sent while it lasts). */
    static void jets(ServerLevel sl, ServerPlayer p, long now) { send(sl, on(6, p, 8)); }

    static final UUID PLATING = UUID.fromString("7a1e0000-0000-4000-8000-0000000000f1"), SLIP = UUID.fromString("7a1e0000-0000-4000-8000-0000000000f2"),
            FLOW = UUID.fromString("7a1e0000-0000-4000-8000-0000000000f3");
    public static final int REFLECT = 0, RETURN = 1, FLOWS = 2;

    static final class V {
        int stance, recoilUsed, plating, casts, slipSecs, redirects;
        long returnUntil, pulseUntil, absoluteUntil, hoverUntil, platingUntil, lastHurt = -1000;
        final Map<LivingEntity, Long> thrown = new WeakHashMap<>();
        final Map<LivingEntity, Long> reversed = new WeakHashMap<>();
        final List<Wind> winds = new ArrayList<>();
        final List<Plasma> plasma = new ArrayList<>();
    }
    record Wind(Vec3 at, long until, double r) {}
    record Plasma(Vec3 at, long burst, boolean twice) {}
    static final Map<UUID, V> STATE = new HashMap<>();
    static V v(ServerPlayer p) { return STATE.computeIfAbsent(p.getUUID(), k -> new V()); }
    static final Map<LivingEntity, Long> ION = new WeakHashMap<>();

    public static boolean has(net.minecraft.world.entity.player.Player p) { return BookTree.kind(p) == BookTree.VECTOR; }
    static int mastery(ServerPlayer p) { return GrimoireMastery.level(BookTree.book(p)); }
    static int r(ServerPlayer p, String id) { return BookTree.rank(p, id); }
    static boolean boss(LivingEntity t) { return t instanceof AddonBoss || TimeStop.isBoss(t); }

    // ------------------------------------------------------------------ the passive's numbers
    public static float reflectChance(ServerPlayer p) {
        V s = v(p);
        float c = 0.15f + 0.02f * BookTree.level(p) + 0.05f * r(p, "r1");
        c *= s.stance == REFLECT ? 1.25f : s.stance == RETURN ? 0.6f : 0.7f;
        if (r(p, "k1") > 0) c = 1f;
        if (r(p, "k2") > 0) c *= 0.5f;
        return Math.min(1f, c);
    }
    public static float returnShare(ServerPlayer p) {
        V s = v(p);
        float f = 0.10f + 0.013f * BookTree.level(p) + 0.05f * r(p, "r2");
        f *= s.stance == RETURN ? 1.5f : s.stance == REFLECT ? 0.5f : 0.7f;
        if (r(p, "k2") > 0) f = 1f;
        if (r(p, "k1") > 0) f *= 0.5f;
        return Math.min(1f, f);
    }

    /** All of this book's damage: Compression and Ionized on top. */
    static void hit(ServerPlayer p, LivingEntity t, float dmg, String id) {
        float m = 1f + 0.06f * r(p, "p1");
        Long ion = ION.get(t);
        if (ion != null && ion > p.level().getGameTime()) m *= 1.2f;
        AstaFx.damage(p, t, dmg * m, id);
    }
    static void ionize(ServerPlayer p, LivingEntity t) { if (r(p, "p3") > 0) ION.put(t, p.level().getGameTime() + 100); }

    static void plate(ServerPlayer p) {
        if (r(p, "r4") <= 0) return;
        V s = v(p);
        long now = p.level().getGameTime();
        s.plating = now < s.platingUntil ? Math.min(5, s.plating + 1) : 1;
        s.platingUntil = now + 120;
        TimedModifiers.apply(p, Attributes.ARMOR, PLATING, "Recoil Plating", s.plating, AttributeModifier.Operation.ADDITION, 120);
        ModeHud.show(p, "recoil_plating", 120, s.plating);
    }

    /** Every page cast: Overpressure counts them. */
    static void cast(ServerPlayer p) {
        if (r(p, "p4") <= 0) return;
        V s = v(p);
        if (++s.casts % 5 == 0) {
            var md = io.redspace.ironsspellbooks.api.magic.MagicData.getPlayerMagicData(p);
            float max = (float) p.getAttributeValue(io.redspace.ironsspellbooks.api.registry.AttributeRegistry.MAX_MANA.get());
            md.setMana(Math.min(max, md.getMana() + 50));
            warp(p.serverLevel(), mid(p), 1.0f, 8);
        }
    }

    // ------------------------------------------------------------------ reflection: projectiles
    /** Turn a projectile round at its sender (or the nearest foe). */
    static void reflect(ServerPlayer p, Projectile pr, float speedMul) {
        ServerLevel sl = p.serverLevel();
        Entity shooter = pr.getOwner();
        Vec3 aim;
        if (shooter != null && shooter.isAlive() && shooter != p) aim = shooter.position().add(0, shooter.getBbHeight() * 0.6, 0);
        else {
            LivingEntity near = mastery(p) >= 2 ? nearestFoe(p, 20) : null;
            aim = near != null ? mid(near) : pr.position().subtract(pr.getDeltaMovement().scale(10));
        }
        Vec3 dir = aim.subtract(pr.position()).normalize();
        Vec3 came = pr.getDeltaMovement();
        double sp = Math.max(1.2, pr.getDeltaMovement().length()) * speedMul * (mastery(p) >= 3 ? 1.3 : 1.0);
        pr.setDeltaMovement(dir.scale(sp));
        pr.setOwner(p);
        pr.hurtMarked = true;
        if (pr instanceof AbstractArrow a) {
            a.setPierceLevel((byte) 0);
            if (r(p, "r3") > 0) a.setBaseDamage(a.getBaseDamage() * 1.3);
        }
        deflect(sl, pr.position(), came, dir);
        play(sl, pr.position(), "vec_reflect", 0.8f, 1.2f + (float) Math.random() * 0.2f);
        plate(p);
        BookTree.grow(p, BookTree.VECTOR, 1);
    }

    static LivingEntity nearestFoe(ServerPlayer p, double r) {
        LivingEntity best = null; double bd = r * r;
        for (LivingEntity t : enemies(p.serverLevel(), p, p.getBoundingBox().inflate(r))) {
            double d = t.distanceToSqr(p);
            if (d < bd) { bd = d; best = t; }
        }
        return best;
    }

    @SubscribeEvent
    public static void onProjectile(ProjectileImpactEvent e) {
        if (!(e.getRayTraceResult() instanceof EntityHitResult eh) || !(eh.getEntity() instanceof ServerPlayer p) || !has(p)) return;
        Projectile pr = e.getProjectile();
        if (pr.getOwner() == p) return;
        V s = v(p);
        long now = p.level().getGameTime();
        boolean field = now < s.returnUntil || now < s.absoluteUntil;
        if (!field && p.getRandom().nextFloat() >= reflectChance(p)) return;
        e.setImpactResult(ProjectileImpactEvent.ImpactResult.SKIP_ENTITY);
        reflect(p, pr, now < s.returnUntil && r(p, "r5") > 0 ? 1.5f : 1.1f);
        if (now < s.absoluteUntil) s.redirects++;
    }

    // ------------------------------------------------------------------ reflection: blows, and Pulse Touch
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onHurt(LivingHurtEvent e) {
        LivingEntity victim = e.getEntity();
        Entity src = e.getSource().getEntity();
        if (victim.level().isClientSide || !(src instanceof LivingEntity attacker) || attacker == victim) return;
        boolean melee = e.getSource().getDirectEntity() == attacker && BookTree.melee(e.getSource());
        // Pulse Touch: the next blow carries through
        if (attacker instanceof ServerPlayer p && melee && has(p) && p.level().getGameTime() < v(p).pulseUntil) {
            v(p).pulseUntil = 0;
            ModeHud.hide(p, "pulse_touch");
            pulse(p, victim);
        }
        if (!(victim instanceof ServerPlayer p) || !has(p) || !melee) return;
        V s = v(p);
        ServerLevel sl = p.serverLevel();
        long now = sl.getGameTime();
        boolean fresh = now - s.lastHurt > 200;
        s.lastHurt = now;
        Vec3 away = flatAway(attacker, p);
        float amt = e.getAmount();
        if (now < s.absoluteUntil || (fresh && mastery(p) >= 5)) {   // the whole blow, back where it came from
            e.setCanceled(true);
            Kit.push(attacker, away, boss(attacker) ? 0.4 : 1.4, 0.35);
            hit(p, attacker, amt, "vector_absolute");
            warp(sl, mid(p), 1.3f, 6);
            deflect(sl, mid(p).add(mid(attacker).subtract(mid(p)).scale(0.4)), mid(p).subtract(mid(attacker)), mid(attacker).subtract(mid(p)));
            play(sl, p.position(), "vec_reflect", 1f, 0.8f);
            if (now < s.absoluteUntil) s.redirects++;
            plate(p);
            BookTree.grow(p, BookTree.VECTOR, 1.5f);
            return;
        }
        float f = returnShare(p);
        if (f <= 0) return;
        Kit.push(attacker, away, (0.4 + f * 1.6) * (boss(attacker) ? 0.3 : 1), 0.2 + f * 0.2);
        float back = amt * f * (r(p, "k2") > 0 ? 0.25f : 0.5f) / (r(p, "k2") > 0 ? 1f : 1f);
        if (back > 0.5f) AstaFx.later(1, () -> { if (attacker.isAlive()) hit(p, attacker, back, "redirect"); });
        arrow(sl, mid(p), mid(attacker), 0.04f, 5);
        play(sl, p.position(), "vec_reflect", 0.6f, 0.7f + f);
        plate(p);
        BookTree.grow(p, BookTree.VECTOR, 1);
    }

    static Vec3 flatAway(LivingEntity t, LivingEntity from) {
        Vec3 d = t.position().subtract(from.position()); d = new Vec3(d.x, 0, d.z);
        return d.lengthSqr() < 1e-4 ? new Vec3(from.getLookAngle().x, 0, from.getLookAngle().z).normalize() : d.normalize();
    }

    // ------------------------------------------------------------------ falls
    @SubscribeEvent
    public static void onFall(LivingFallEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !has(p)) return;
        float d = e.getDistance();
        if (r(p, "m5") > 0 && d >= 4) {   // Terminal Velocity
            e.setCanceled(true);
            ServerLevel sl = p.serverLevel();
            float dmg = Math.min(30f, 1.5f * d);
            { var b = fx(4, p.position().add(0, 0.6, 0), 14); b.r = 4f; b.u = p.position(); send(sl, b); }
            for (int i = 0; i < 8; i++) { double a = i * Math.PI / 4; arrow(sl, p.position().add(Math.cos(a) * 0.8, 0.2, Math.sin(a) * 0.8), p.position().add(Math.cos(a) * 4, 0.2, Math.sin(a) * 4), 0.05f, 7); }
            FxSend.shake(sl, p.position(), 0.4f, 6, 12);
            play(sl, p.position(), "vec_slam", 1.2f, 1f);
            for (LivingEntity t : enemies(sl, p, p.getBoundingBox().inflate(4, 2, 4))) { hit(p, t, dmg, "recoil_step"); Kit.push(t, flatAway(t, p), 0.8, 0.3); }
            return;
        }
        if (r(p, "f2") > 0 || v(p).stance == FLOWS) e.setCanceled(true);
    }

    // ------------------------------------------------------------------ the tick
    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || !p.isAlive()) return;
        if (!has(p)) { V old = STATE.remove(p.getUUID()); if (old != null && old.stance != 0) ModeHud.standing(p, new String[]{"vec_stance_return", "vec_stance_flow"}, -1); return; }
        V s = v(p);
        ServerLevel sl = p.serverLevel();
        long now = sl.getGameTime();
        if (p.onGround()) s.recoilUsed = 0;
        // Return to Sender / Vector Absolute: projectiles coming at you turn round before they arrive
        boolean field = now < s.returnUntil, abs = now < s.absoluteUntil;
        if (field || abs) {
            double rr = abs ? 16 : 6;
            for (Projectile pr : sl.getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(rr), x -> x.getOwner() != p && x.getDeltaMovement().lengthSqr() > 0.04)) {
                Vec3 to = mid(p).subtract(pr.position());
                if (to.normalize().dot(pr.getDeltaMovement().normalize()) < 0.6) continue;
                if (pr instanceof AbstractArrow a && a.isNoPhysics()) continue;
                reflect(p, pr, field && r(p, "r5") > 0 ? 1.5f : 1.1f);
                if (abs) s.redirects++;
            }
            if (now % 10 == 0) { var f = on(8, p, 14); f.r = abs ? 2.6f : 1.8f; send(sl, f); }
        }
        if (abs && now % 4 == 0) jets(sl, p, now);
        if (abs) {   // everything rushing you is turned about
            for (LivingEntity t : enemies(sl, p, p.getBoundingBox().inflate(16))) {
                Vec3 to = new Vec3(p.getX() - t.getX(), 0, p.getZ() - t.getZ());
                Vec3 mv = new Vec3(t.getX() - t.xo, 0, t.getZ() - t.zo);
                if (to.lengthSqr() < 1e-3 || mv.dot(to.normalize()) < 0.08) continue;
                Long last = s.reversed.get(t);
                if (last != null && now - last < 10) continue;
                s.reversed.put(t, now);
                Kit.push(t, flatAway(t, p), boss(t) ? 0.3 : 1.0, 0.25);
                hit(p, t, 2f, "vector_absolute");
                deflect(sl, mid(t), new Vec3(t.getX() - t.xo, 0, t.getZ() - t.zo), flatAway(t, p));
                s.redirects++;
                BookTree.grow(p, BookTree.VECTOR, 0.5f);
            }
        } else if (s.absoluteUntil > 0 && now >= s.absoluteUntil) {   // it ends: Overload
            s.absoluteUntil = 0;
            if (r(p, "k4") > 0 && s.redirects > 0) {
                float dmg = Math.min(40f, 4f * s.redirects);
                { var b = fx(4, mid(p), 18); b.r = 8f; b.u = p.position(); send(sl, b); }
                for (int i = 0; i < 12; i++) { double a = i * Math.PI / 6; arrow(sl, mid(p).add(Math.cos(a), 0, Math.sin(a)), mid(p).add(Math.cos(a) * 8, 0, Math.sin(a) * 8), 0.06f, 8); }
                FxSend.impactFrame(sl, mid(p), 3, false, 20);
                FxSend.shake(sl, p.position(), 0.5f, 8, 16);
                play(sl, p.position(), "vec_absolute", 1.4f, 1.4f);
                for (LivingEntity t : enemies(sl, p, p.getBoundingBox().inflate(8))) { hit(p, t, dmg, "vector_absolute"); Kit.push(t, flatAway(t, p), 1.2, 0.4); }
            }
            s.redirects = 0;
        }
        // Hover Vector
        if (now < s.hoverUntil) { Vec3 d = p.getDeltaMovement(); p.setDeltaMovement(d.x, Math.max(d.y, 0.0) * 0.5 + 0.005, d.z); p.fallDistance = 0; p.hurtMarked = true;
            if (now % 6 == 0) { var st = fx(7, p.position().add(0, -0.1, 0), 10); st.d = new Vec3(0, 1, 0); send(sl, st); } }
        // Slipstream and Flow
        if (now % 20 == 0) {
            if (r(p, "f3") > 0 && p.isSprinting()) {
                s.slipSecs = Math.min(6, s.slipSecs + 1);
                TimedModifiers.apply(p, Attributes.MOVEMENT_SPEED, SLIP, "Slipstream", 0.05 * s.slipSecs, AttributeModifier.Operation.MULTIPLY_TOTAL, 30);
            } else s.slipSecs = 0;
            if (s.stance == FLOWS) TimedModifiers.apply(p, Attributes.MOVEMENT_SPEED, FLOW, "Flow Stance", 0.15, AttributeModifier.Operation.MULTIPLY_TOTAL, 30);
        }
        // Skimming: sprint over water and lava
        if (r(p, "f5") > 0 && p.isSprinting() && (p.isInWater() || p.isInLava()) && !p.isUnderWater()) {
            Vec3 d = p.getDeltaMovement();
            p.setDeltaMovement(d.x * 1.05, Math.max(d.y, 0.12), d.z * 1.05); p.hurtMarked = true;
        }
        // Impact Vector: thrown creatures slam into others
        if (!s.thrown.isEmpty()) {
            for (Iterator<Map.Entry<LivingEntity, Long>> it = s.thrown.entrySet().iterator(); it.hasNext(); ) {
                var en = it.next();
                LivingEntity t = en.getKey();
                if (now > en.getValue() || !t.isAlive()) { it.remove(); continue; }
                for (LivingEntity o : sl.getEntitiesOfClass(LivingEntity.class, t.getBoundingBox().inflate(0.3), x -> x != t && x != p && x.isAlive() && !x.isAlliedTo(p))) {
                    hit(p, t, 6f, "redirect"); hit(p, o, 6f, "redirect");
                    warp(sl, mid(t), 1.2f, 6);
                    FxSend.impactFrame(sl, mid(t), 2, false, 10);
                    play(sl, t.position(), "vec_slam", 0.9f, 1.3f);
                    it.remove();
                    break;
                }
            }
        }
        // Wind Vector: pull in and hold up
        for (Iterator<Wind> it = s.winds.iterator(); it.hasNext(); ) {
            Wind w = it.next();
            if (now >= w.until) { it.remove(); continue; }

            for (LivingEntity t : enemies(sl, p, new AABB(w.at, w.at).inflate(w.r, 4, w.r))) {
                Vec3 to = w.at.add(0, 2, 0).subtract(t.position());
                double k = boss(t) ? 0.03 : 0.12;
                t.setDeltaMovement(t.getDeltaMovement().scale(0.6).add(to.x * k, Math.max(-0.05, to.y * 0.15), to.z * k));
                t.hurtMarked = true; t.fallDistance = 0;
                if (now % 20 == 0) hit(p, t, 3.5f, "wind_vector");
            }
        }
        // Plasma Point
        for (Iterator<Plasma> it = s.plasma.iterator(); it.hasNext(); ) {
            Plasma pl = it.next();
            if (now < pl.burst) {

                continue;
            }
            it.remove();
            burstPlasma(p, pl.at, 1f);
            if (pl.twice) AstaFx.later(10, () -> { if (p.isAlive()) burstPlasma(p, pl.at, 0.6f); });
        }
    }

    static void burstPlasma(ServerPlayer p, Vec3 at, float k) {
        ServerLevel sl = p.serverLevel();
        double rr = 3 + (r(p, "p2") > 0 ? 1 : 0);
        { var b = fx(4, at, 18); b.r = (float) rr + 0.5f; b.u = BossKit.ground(sl, at.add(0, 0.5, 0)); send(sl, b); }
        FxSend.impactFrame(sl, at, 2, false, 18);
        FxSend.shake(sl, at, 0.3f * k, 6, 14);
        play(sl, at, "vec_plasma", 1.3f, k < 1 ? 1.3f : 1f);
        for (LivingEntity t : enemies(sl, p, new AABB(at, at).inflate(rr))) {
            if (t.distanceToSqr(at) > rr * rr * 1.2) continue;
            hit(p, t, 12f * k, "plasma_point");
            ionize(p, t);
            Kit.push(t, t.position().subtract(at).normalize(), 0.6, 0.3);
        }
    }

    // ------------------------------------------------------------------ the pages
    /** Redirect: take hold of the momentum of what you look at and throw it the way you face. */
    public static void redirect(ServerPlayer p, float power) {
        cast(p);
        LivingEntity t = aimed(p, 16, 10);
        ServerLevel sl = p.serverLevel();
        play(sl, p.position(), "vec_redirect", 1f, 1f);
        if (t == null) { arrow(sl, p.getEyePosition().add(p.getLookAngle()), p.getEyePosition().add(p.getLookAngle().scale(6)), 0.05f, 6); return; }
        int m1 = r(p, "m1");
        Vec3 dir = new Vec3(p.getLookAngle().x, 0, p.getLookAngle().z).normalize();
        double strength = (1.6 + 0.24 * m1) * (boss(t) ? 0.25 : 1);
        t.setDeltaMovement(dir.scale(strength).add(0, 0.45, 0)); t.hurtMarked = true;
        hit(p, t, 5f * (1 + 0.1f * m1), "redirect");
        deflect(sl, mid(t), mid(t).subtract(p.getEyePosition()), dir.add(0, 0.25, 0));
        arrows(sl, mid(t).add(dir.scale(2)), dir, 4, 2.5, 0.5);
        if (r(p, "m2") > 0) v(p).thrown.put(t, sl.getGameTime() + 30);
        BookTree.grow(p, BookTree.VECTOR, 1);
    }

    /** Recoil Step: kick off the air - three times before you land (more with Air Purchase). */
    public static void recoilStep(ServerPlayer p, float power) {
        V s = v(p);
        int max = 3 + r(p, "f1");
        if (!p.onGround()) {
            if (s.recoilUsed >= max) { p.displayClientMessage(Component.literal("No more steps until you land").withStyle(ChatFormatting.GRAY), true); return; }
            s.recoilUsed++;
        }
        cast(p);
        Vec3 look = p.getLookAngle();
        p.setDeltaMovement(look.x * 1.1, Math.max(0.45, look.y * 0.9 + 0.35), look.z * 1.1);
        p.hurtMarked = true; p.fallDistance = 0;
        { var st = fx(7, p.position(), 10); st.d = look; send(p.serverLevel(), st); }   // a hexagon of compressed air under you, vectors kicked away
        play(p.serverLevel(), p.position(), "vec_step", 0.9f, 1f + 0.1f * s.recoilUsed);
    }

    /** Return to Sender: for 3 s (5 with Perfect Angle) everything flying at you goes back where it came from. */
    public static void returnToSender(ServerPlayer p, float power) {
        cast(p);
        v(p).returnUntil = p.level().getGameTime() + (r(p, "r5") > 0 ? 100 : 60);
        ModeHud.show(p, "return_to_sender", r(p, "r5") > 0 ? 100 : 60);
        warp(p.serverLevel(), mid(p), 2.2f, 10);
        play(p.serverLevel(), p.position(), "vec_return", 1f, 1f);
    }

    /** Pressure Gust: the air bent into a narrow blast down a line (three, with Pressure Lanes). */
    public static void pressureGust(ServerPlayer p, float power) {
        cast(p);
        ServerLevel sl = p.serverLevel();
        Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
        play(sl, p.position(), "vec_gust", 1.1f, 1f);
        double[] lanes = r(p, "m4") > 0 ? new double[]{-0.35, 0, 0.35} : new double[]{0};
        java.util.Set<LivingEntity> hitSet = new java.util.HashSet<>();
        for (double a : lanes) {
            Vec3 d = rotY(look, a).normalize();
            Vec3 end = clip(sl, p, eye, eye.add(d.scale(12)));
            { var g = fx(2, eye.add(d), 14); g.d = d; g.r = (float) eye.add(d).distanceTo(end); send(sl, g); }
            warp(sl, end, 1.2f, 6);
            for (LivingEntity t : enemies(sl, p, new AABB(eye, end).inflate(1.6))) {
                if (hitSet.contains(t) || closestOnSegment(eye, end, mid(t)).distanceTo(mid(t)) > 1.6) continue;
                hitSet.add(t);
                Kit.push(t, new Vec3(d.x, 0, d.z).normalize(), boss(t) ? 0.3 : 1.3, 0.25);
                hit(p, t, 5f, "pressure_gust");
                ionize(p, t);
            }
        }
        BookTree.grow(p, BookTree.VECTOR, 0.5f * hitSet.size());
    }

    /** Pulse Touch: your next blow (within 6 s) sends its force through the target into everything behind it. */
    public static void pulseTouch(ServerPlayer p, float power) {
        cast(p);
        v(p).pulseUntil = p.level().getGameTime() + 120;
        ModeHud.show(p, "pulse_touch", 120);
        warp(p.serverLevel(), Kit.hand(p), 0.35f, 20);
        play(p.serverLevel(), p.position(), "vec_pulse", 0.8f, 1.4f);
    }

    static void pulse(ServerPlayer p, LivingEntity first) {
        ServerLevel sl = p.serverLevel();
        Vec3 d = new Vec3(first.getX() - p.getX(), 0, first.getZ() - p.getZ()).normalize();
        Vec3 a = mid(first), b = a.add(d.scale(6));
        int count = 2 + (r(p, "m3") > 0 ? 2 : 0);
        float k = r(p, "m3") > 0 ? 1.5f : 1f;
        { var g = fx(2, a, 12); g.d = d; g.r = 6f; send(sl, g); }
        warp(sl, a, 1.0f, 6);
        arrows(sl, a.add(d.scale(3)), d, 6, 3, 0.7);
        play(sl, a, "vec_pulse", 1.2f, 0.9f);
        hit(p, first, 6f * k, "pulse_touch");
        List<LivingEntity> behind = new ArrayList<>(enemies(sl, p, new AABB(a, b).inflate(1.5)));
        behind.remove(first);
        behind.sort((x, y) -> Double.compare(x.distanceToSqr(first), y.distanceToSqr(first)));
        for (int i = 0; i < Math.min(count, behind.size()); i++) {
            LivingEntity t = behind.get(i);
            hit(p, t, 6f * k, "pulse_touch");
            Kit.push(t, d, 0.9 * k, 0.2);
        }
        Kit.push(first, d, (boss(first) ? 0.3 : 1.0) * k, 0.2);
        BookTree.grow(p, BookTree.VECTOR, 1);
    }

    /** Wind Vector: a spinning column that pulls foes in and holds them up for 4 s (6, from 9 blocks, with Wind Lattice). */
    public static void windVector(ServerPlayer p, float power) {
        cast(p);
        ServerLevel sl = p.serverLevel();
        Vec3 eye = p.getEyePosition();
        Vec3 at = BossKit.ground(sl, clip(sl, p, eye, eye.add(p.getLookAngle().scale(8))));
        boolean lat = r(p, "f4") > 0;
        v(p).winds.add(new Wind(at, sl.getGameTime() + (lat ? 120 : 80), lat ? 9 : 6));
        { var c = fx(5, at, lat ? 120 : 80); c.r = lat ? 9 : 6; send(sl, c); }
        play(sl, at, "vec_wind", 1.2f, 1f);
    }

    /** Plasma Point: the air pressed into a bright point that bursts after 2 s. */
    public static void plasmaPoint(ServerPlayer p, float power) {
        cast(p);
        ServerLevel sl = p.serverLevel();
        Vec3 eye = p.getEyePosition();
        Vec3 at = clip(sl, p, eye, eye.add(p.getLookAngle().scale(12)));
        v(p).plasma.add(new Plasma(at, sl.getGameTime() + 40, r(p, "p2") > 0));
        { var c = fx(3, at, 40); c.r = 0.55f; send(sl, c); }   // the point, charging
        play(sl, at, "vec_charge", 1f, 1f);
    }

    /** Vector Absolute: for 12 s (20 with Eye of the Storm) every force round you is yours. */
    public static void vectorAbsolute(ServerPlayer p, float power) {
        cast(p);
        V s = v(p);
        s.absoluteUntil = p.level().getGameTime() + (r(p, "k3") > 0 ? 400 : 240);
        ModeHud.show(p, "vector_absolute", r(p, "k3") > 0 ? 400 : 240);
        s.redirects = 0;
        ServerLevel sl = p.serverLevel();
        { var b = fx(4, mid(p), 18); b.r = 9f; b.u = p.position(); send(sl, b); }   // the world's forces turned, all at once
        warp(sl, mid(p), 3f, 12);
        jets(sl, p, sl.getGameTime());
        FxSend.impactFrame(sl, mid(p), 4, false, 24);
        FxSend.shake(sl, p.position(), 0.3f, 8, 16);
        play(sl, p.position(), "vec_absolute", 1.6f, 1f);
    }

    // ------------------------------------------------------------------ the G wheel
    static boolean wheel(ServerPlayer p, int slot) {
        V s = v(p);
        ServerLevel sl = p.serverLevel();
        switch (slot) {
            case 0 -> {
                s.stance = (s.stance + 1) % 3;
                ModeHud.standing(p, new String[]{"vec_stance_return", "vec_stance_flow"}, s.stance - 1);
                p.displayClientMessage(Component.literal(new String[]{"Reflect stance: projectiles", "Return stance: blows", "Flow stance: no fall damage, faster"}[s.stance]).withStyle(ChatFormatting.AQUA), true);
                warp(sl, mid(p), 1.2f, 8);
                play(sl, p.position(), "vec_step", 0.8f, 1.4f);
            }
            case 1 -> {
                s.hoverUntil = sl.getGameTime() + 60;
                ModeHud.show(p, "vec_hover", 60);
                p.setDeltaMovement(p.getDeltaMovement().x, 0.4, p.getDeltaMovement().z); p.hurtMarked = true;
                play(sl, p.position(), "vec_return", 0.8f, 1.5f);
            }
            case 2 -> {
                int n = 0;
                for (LivingEntity t : enemies(sl, p, p.getBoundingBox().inflate(10))) {
                    Kit.push(t, flatAway(t, p), boss(t) ? 0.4 : 1.5, 0.35);
                    hit(p, t, 3f, "redirect");
                    arrow(sl, mid(t), mid(t).add(flatAway(t, p).scale(3)), 0.05f, 6);
                    n++;
                }
                { var b = fx(4, mid(p), 16); b.r = 10f; b.u = p.position(); send(sl, b); }
                play(sl, p.position(), "vec_gust", 1.2f, 0.8f);
                BookTree.grow(p, BookTree.VECTOR, 0.5f * n);
            }
            default -> {
                Vec3 eye = p.getEyePosition(), end = clip(sl, p, eye, eye.add(p.getLookAngle().scale(20)));
                arrow(sl, eye.add(p.getLookAngle()), end, 0.14f, 8);
                { var g = fx(2, eye.add(p.getLookAngle()), 14); g.d = p.getLookAngle(); g.r = (float) eye.distanceTo(end); send(sl, g); }
                arrows(sl, eye.add(p.getLookAngle().scale(4)), p.getLookAngle(), 6, 3, 0.6);
                play(sl, p.position(), "vec_lance", 1.2f, 1f);
                for (LivingEntity t : enemies(sl, p, new AABB(eye, end).inflate(1.2)))
                    if (closestOnSegment(eye, end, mid(t)).distanceTo(mid(t)) < 1.3) hit(p, t, 10f, "pressure_gust");
            }
        }
        return true;
    }

    /** For the wheel's middle: stance, Recoil Steps used, steps, reflect %, return %. */
    static int[] extra(ServerPlayer p) {
        V s = v(p);
        return new int[]{s.stance, s.recoilUsed, 3 + r(p, "f1"), Math.round(reflectChance(p) * 100), Math.round(returnShare(p) * 100)};
    }

    @SubscribeEvent public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) { STATE.remove(e.getEntity().getUUID()); }

    // test hooks
    public static void testReset(ServerPlayer p) { STATE.remove(p.getUUID()); }
    public static int testStance(ServerPlayer p) { return v(p).stance; }
    public static boolean testAbsolute(ServerPlayer p) { return p.level().getGameTime() < v(p).absoluteUntil; }
}
