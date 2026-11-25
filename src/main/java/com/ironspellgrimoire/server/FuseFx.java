package com.ironspellgrimoire.server;

import static com.ironspellgrimoire.server.FxUtil.*;

import com.ironspellgrimoire.IronSpellGrimoire;
import com.ironspellgrimoire.entity.AddonBoss;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The Grimoire of the Quiet Fuse: whatever you touch, you can make go off (every name, look and line here is original).
 * <ul>
 *   <li><b>Primed Touch</b> (passive): your blows arm what they strike; Detonate sets everything armed off. Blasts hurt
 *       creatures only - never a block. How many you can keep armed (1, up to 5) and how hard and wide they blow grow
 *       with the Fuse level (server/BookTree). The G wheel's Fuse Mode chooses when armed targets blow: on Detonate,
 *       3 s after arming, or as soon as another foe comes near.</li>
 *   <li>Pages: Detonate, Prime Item, Heat Seeker, Air Bubble, Contact Charge, Chain Fuse, Hidden Tenant, and the
 *       ultimate Loop of Ruin.</li>
 *   <li>Mastery: 2 the Heat Seeker survives one blast; 3 detonating gives you +25% speed for 2 s; 5 armed foes that run
 *       more than 20 blocks from you go off on their own.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = IronSpellGrimoire.MODID)
public final class FuseFx {
    private FuseFx() {}

    static final int PAL = 16, HOT = 22;   // thread pink, flame-spirit vermilion
    static final UUID CONC = UUID.fromString("7a1e0000-0000-4000-8000-0000000000f4"), RUSH = UUID.fromString("7a1e0000-0000-4000-8000-0000000000f5");
    public static final int REMOTE = 0, TIMER = 1, NEAR = 2;

    record Armed(Entity e, long since, boolean extra) {}
    record Seeker(Vec3[] pos, long until, int[] lives) {}
    record Bubble(Vec3[] pos, long until, boolean orbit) {}
    record Ring(Vec3 at, double r, long until) {}
    record Replay(Vec3 at, double r, float dmg, long when, int left) {}

    static final class F {
        int mode, arrowsArmed;
        long contactUntil, chainUntil, loopUntil, firstBombAt = -1000;
        final List<Armed> armed = new ArrayList<>();
        final List<Seeker> seekers = new ArrayList<>();
        final List<Bubble> bubbles = new ArrayList<>();
        final List<Ring> rings = new ArrayList<>();
        final List<Replay> replays = new ArrayList<>();
        final Map<LivingEntity, Long> tenants = new java.util.WeakHashMap<>();
        Vec3 coin, coinDir; long coinUntil;
    }
    static final Map<UUID, F> STATE = new HashMap<>();
    /** Who carries a Hidden Tenant, and whose it is. */
    static final Map<LivingEntity, UUID> HOSTS = new java.util.WeakHashMap<>();
    static F f(ServerPlayer p) { return STATE.computeIfAbsent(p.getUUID(), k -> new F()); }

    public static boolean has(Player p) { return BookTree.kind(p) == BookTree.FUSE; }
    static int r(ServerPlayer p, String id) { return BookTree.rank(p, id); }
    static int mastery(ServerPlayer p) { return GrimoireMastery.level(BookTree.book(p)); }
    static boolean boss(LivingEntity t) { return t instanceof AddonBoss || TimeStop.isBoss(t); }

    public static int limit(ServerPlayer p) { return Math.max(1, 1 + BookTree.level(p) / 7 + r(p, "p1") - (r(p, "k1") > 0 ? 1 : 0)); }
    static float baseDamage(ServerPlayer p) { return (6f + 0.3f * BookTree.level(p)) * (1f + 0.1f * r(p, "p3")); }
    static double radius(ServerPlayer p) { return (2.5 + 0.05 * BookTree.level(p) + 0.5 * r(p, "b1")) * (r(p, "k2") > 0 ? 1.5 : 1); }

    // ------------------------------------------------------------------ arming
    /** Arm e for p. extra: past the limit (Primed Coin, Chain Fuse with Sympathetic). */
    public static boolean arm(ServerPlayer p, Entity e, boolean extra) {
        F s = f(p);
        if (e == p || !e.isAlive()) return false;
        for (Armed a : s.armed) if (a.e() == e) return false;
        long normal = s.armed.stream().filter(a -> !a.extra()).count();
        if (!extra && normal >= limit(p)) return false;
        s.armed.add(new Armed(e, p.level().getGameTime(), extra));
        ServerLevel sl = p.serverLevel();
        FxSend.ring(sl, PAL, e.position().add(0, e.getBbHeight() * 0.5, 0), 0.1f, 0.7f, 0.05f, 6);
        play(sl, e.position(), "fuse_arm", 0.7f, 1.2f);
        return true;
    }

    // ------------------------------------------------------------------ the blast
    /** A blast at a point: creatures within r take dmg (no block is touched). Returns how many it caught. */
    static int blast(ServerPlayer p, Vec3 at, double rr, float dmg, Entity armedOn, boolean record) {
        ServerLevel sl = p.serverLevel();
        F s = f(p);
        FxSend.ring(sl, PAL, at, 0.2f, (float) rr, 0.4f, 9);
        FxSend.ring(sl, HOT, at, 0.2f, (float) rr * 0.6f, 0.6f, 7);
        Kit.orb(sl, HOT, at, Vec3.ZERO, (float) rr * 0.35f, 5);
        FxSend.shake(sl, at, (float) Math.min(0.5, rr * 0.12), 6, rr * 4);
        play(sl, at, "fuse_blast", 1.2f, 1.1f - (float) Math.min(0.4, rr * 0.05));
        int n = 0;
        Set<LivingEntity> hitSet = new HashSet<>();
        for (LivingEntity t : enemies(sl, p, new AABB(at, at).inflate(rr))) {
            if (t.distanceToSqr(at) > rr * rr * 1.3) continue;
            float d = dmg;
            if (r(p, "s4") > 0 && (t.isOnFire() || t.fireImmune())) d *= 1.25f;
            if (t == armedOn && r(p, "k1") > 0) d *= 1.3f;
            AstaFx.damage(p, t, d, "detonate");
            if (!boss(t)) Kit.push(t, t.position().subtract(at).normalize(), 0.5, 0.25);
            if (r(p, "b2") > 0) TimedModifiers.apply(t, Attributes.MOVEMENT_SPEED, CONC, "Concussion", -0.3, AttributeModifier.Operation.MULTIPLY_TOTAL, 40);
            if (sl.getGameTime() < s.chainUntil && t != armedOn) arm(p, t, r(p, "p5") > 0 && s.armed.stream().filter(Armed::extra).count() < 3);
            hitSet.add(t);
            n++;
        }
        if (r(p, "b3") > 0) {   // Shrapnel
            List<LivingEntity> far = enemies(sl, p, new AABB(at, at).inflate(6));
            far.removeIf(hitSet::contains);
            for (int i = 0; i < Math.min(4, far.size()); i++) {
                LivingEntity t = far.get(i);
                FxSend.trail(sl, HOT, at, mid(t), 0.05f, 5);
                AstaFx.damage(p, t, dmg * 0.3f, "detonate");
            }
        }
        if (r(p, "b4") > 0) s.rings.add(new Ring(at, rr, sl.getGameTime() + 60));
        if (record && sl.getGameTime() < s.loopUntil) s.replays.add(new Replay(at, rr, dmg, sl.getGameTime() + 60, r(p, "k3") > 0 ? 2 : 1));
        BookTree.grow(p, BookTree.FUSE, n);
        return n;
    }

    /** Set off one armed thing. */
    static void blow(ServerPlayer p, Armed a, float mult) {
        Entity e = a.e();
        if (!e.isAlive() && !(e instanceof ItemEntity)) return;
        Vec3 at = e.position().add(0, e.getBbHeight() * 0.5, 0);
        float dmg = baseDamage(p) * mult;
        if (r(p, "p4") > 0 && p.level().getGameTime() - a.since() >= 100) dmg *= 1.4f;
        if (!(e instanceof LivingEntity)) e.discard();   // an armed item or arrow is used up
        blast(p, at, radius(p), dmg, e, true);
    }

    /** Detonate: everything armed goes off. */
    static int detonateAll(ServerPlayer p) {
        F s = f(p);
        ServerLevel sl = p.serverLevel();
        if (s.armed.isEmpty()) { p.displayClientMessage(Component.literal("Nothing is armed").withStyle(ChatFormatting.GRAY), true); return 0; }
        float mult = 1f;
        long now = sl.getGameTime();
        if (r(p, "k4") > 0 && now - s.firstBombAt >= 400) { mult = 2f; s.firstBombAt = now; }
        List<Armed> list = new ArrayList<>(s.armed);
        s.armed.clear();
        int i = 0;
        for (Armed a : list) { final float m = mult; AstaFx.later(i++ * 2, () -> { if (p.isAlive()) blow(p, a, m); }); }
        if (r(p, "k2") > 0 && p.getHealth() > 2) p.setHealth(p.getHealth() - 1);
        if (mastery(p) >= 3) TimedModifiers.apply(p, Attributes.MOVEMENT_SPEED, RUSH, "Fuse Rush", 0.25, AttributeModifier.Operation.MULTIPLY_TOTAL, 40);
        play(sl, p.position(), "fuse_click", 1f, 1f);
        return list.size();
    }

    // ------------------------------------------------------------------ events
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onHurt(LivingHurtEvent e) {
        LivingEntity victim = e.getEntity();
        Entity src = e.getSource().getEntity();
        if (victim.level().isClientSide || !(src instanceof LivingEntity attacker) || attacker == victim) return;
        // Primed Touch: your blows arm what they strike
        if (attacker instanceof ServerPlayer p && has(p) && e.getSource().getDirectEntity() == p && BookTree.melee(e.getSource())) arm(p, victim, false);
        // Contact Charge: the next creature to strike you is armed
        if (victim instanceof ServerPlayer p && has(p) && p.level().getGameTime() < f(p).contactUntil && e.getSource().getDirectEntity() == attacker && BookTree.melee(e.getSource())) {
            f(p).contactUntil = 0;
            ModeHud.hide(p, "contact_charge");
            arm(p, attacker, true);
            e.setAmount(e.getAmount() * 0.5f);
        }
        // Hidden Tenant: whoever hurts its host sets it off
        UUID owner = HOSTS.get(victim);
        if (owner != null && victim.level() instanceof ServerLevel vl && vl.getPlayerByUUID(owner) instanceof ServerPlayer p && has(p)) {
            Long until = f(p).tenants.get(victim);
            if (until == null || until < p.level().getGameTime() || attacker == p) return;
            f(p).tenants.remove(victim);
            HOSTS.remove(victim);
            if (f(p).tenants.isEmpty()) ModeHud.hide(p, "hidden_tenant");
            blast(p, mid(attacker), Math.max(2.5, radius(p)), baseDamage(p) * 1.5f, attacker, true);
            if (r(p, "a4") > 0) victim.heal(4f);
        }
    }

    /** Hot Touch / Booby Trap: your arrows arm what they hit. */
    @SubscribeEvent
    public static void onProjectile(ProjectileImpactEvent e) {
        if (!(e.getRayTraceResult() instanceof EntityHitResult eh) || !(e.getProjectile().getOwner() instanceof ServerPlayer p) || !has(p)) return;
        if (!(e.getProjectile() instanceof AbstractArrow)) return;
        boolean armedArrow = e.getProjectile().getPersistentData().getBoolean("isg_fuse");
        if (r(p, "p2") > 0 || armedArrow) AstaFx.later(1, () -> arm(p, eh.getEntity(), armedArrow));
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide || !(e.getEntity() instanceof AbstractArrow a) || !(a.getOwner() instanceof ServerPlayer p) || !has(p)) return;
        F s = f(p);
        if (s.arrowsArmed > 0) { s.arrowsArmed--; a.getPersistentData().putBoolean("isg_fuse", true); }
    }

    // ------------------------------------------------------------------ the tick
    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || !p.isAlive()) return;
        if (!has(p)) { F old = STATE.remove(p.getUUID()); if (old != null && old.mode != 0) ModeHud.standing(p, new String[]{"fuse_mode_timer", "fuse_mode_near"}, -1); return; }
        F s = f(p);
        ServerLevel sl = p.serverLevel();
        long now = sl.getGameTime();
        // the armed: marks, timers, proximity, running away
        for (Iterator<Armed> it = s.armed.iterator(); it.hasNext(); ) {
            Armed a = it.next();
            Entity en = a.e();
            if (!en.isAlive() || en.level() != sl) {
                if (en instanceof LivingEntity && en.level() == sl && !(en instanceof Player)) { it.remove(); blast(p, en.position().add(0, 0.5, 0), radius(p) * 0.7, baseDamage(p) * 0.6f, null, true); }   // it dies: it goes off where it fell
                else it.remove();
                continue;
            }
            if (now % 10 == 0) FxSend.ring(sl, PAL, en.position().add(0, en.getBbHeight() + 0.3, 0), 0.05f, 0.22f, 0.04f, 10);
            boolean go = (s.mode == TIMER && now - a.since() >= 60)
                    || (s.mode == NEAR && now % 5 == 0 && !sl.getEntitiesOfClass(LivingEntity.class, en.getBoundingBox().inflate(2), x -> x != en && x != p && x.isAlive() && !x.isAlliedTo(p) && x instanceof net.minecraft.world.entity.Mob).isEmpty())
                    || (mastery(p) >= 5 && en.distanceToSqr(p) > 400);
            if (go) { it.remove(); blow(p, a, 1f); }
        }
        // Heat Seekers
        for (Iterator<Seeker> it = s.seekers.iterator(); it.hasNext(); ) {
            Seeker k = it.next();
            if (now >= k.until() || k.lives()[0] <= 0) { it.remove(); continue; }
            LivingEntity t = warmest(p, k.pos()[0], 16);
            if (t != null) {
                Vec3 to = mid(t).subtract(k.pos()[0]);
                double sp = 0.35 * (1 + 0.2 * r(p, "s1"));
                k.pos()[0] = k.pos()[0].add(to.lengthSqr() > sp * sp ? to.normalize().scale(sp) : to);
                if (to.length() < 0.9) {
                    blast(p, k.pos()[0], radius(p), baseDamage(p) * 1.2f, t, true);
                    k.lives()[0]--;
                    k.pos()[0] = k.pos()[0].add(0, 1.2, 0);
                }
            }
            if (now % 2 == 0) Kit.orb(sl, PAL, k.pos()[0], Vec3.ZERO, 0.22f, 3);
        }
        // Air Bubbles
        for (Iterator<Bubble> it = s.bubbles.iterator(); it.hasNext(); ) {
            Bubble b = it.next();
            if (now >= b.until()) { it.remove(); continue; }
            Vec3 at = b.pos()[0];
            LivingEntity t = nearestFoe(p, at, b.orbit() ? 5 : 12);
            if (t != null) at = at.add(mid(t).subtract(at).normalize().scale(b.orbit() ? 0.3 : 0.15));
            else if (b.orbit()) { double a = now * 0.08 + System.identityHashCode(b) % 7; at = at.add(mid(p).add(Math.cos(a) * 2.2, 0.4, Math.sin(a) * 2.2).subtract(at).scale(0.2)); }
            b.pos()[0] = at;
            if (now % 3 == 0) FxSend.ring(sl, PAL, at, 0.25f, 0.35f, 0.04f, 4);
            if (t != null && mid(t).distanceToSqr(at) < 1.0) { it.remove(); blast(p, at, radius(p) * 0.6, baseDamage(p) * 0.6f, t, true); }
        }
        // Afterburn
        for (Iterator<Ring> it = s.rings.iterator(); it.hasNext(); ) {
            Ring g = it.next();
            if (now >= g.until()) { it.remove(); continue; }
            if (now % 10 == 0) {
                FxSend.ring(sl, HOT, g.at().add(0, -0.4, 0), (float) g.r() - 0.4f, (float) g.r(), 0.05f, 10);
                for (LivingEntity t : enemies(sl, p, new AABB(g.at(), g.at()).inflate(g.r()))) AstaFx.damage(p, t, 1f, "detonate");
            }
        }
        // Loop of Ruin
        for (Iterator<Replay> it = s.replays.iterator(); it.hasNext(); ) {
            Replay rp = it.next();
            if (now < rp.when()) continue;
            it.remove();
            blast(p, rp.at(), rp.r(), rp.dmg(), null, false);
            if (rp.left() > 1) s.replays.add(new Replay(rp.at(), rp.r(), rp.dmg(), now + 60, rp.left() - 1));
            break;   // one a tick (the list was changed)
        }
        // the Primed Coin in flight
        if (s.coin != null) {
            if (now >= s.coinUntil) s.coin = null;
            else {
                Vec3 nx = s.coin.add(s.coinDir.scale(1.2));
                FxSend.trail(sl, PAL, s.coin, nx, 0.04f, 4);
                for (LivingEntity t : enemies(sl, p, new AABB(s.coin, nx).inflate(0.5))) { arm(p, t, true); s.coin = null; play(sl, t.position(), "fuse_coin", 1f, 1.2f); break; }
                if (s.coin != null) s.coin = nx;
            }
        }
    }

    static LivingEntity nearestFoe(ServerPlayer p, Vec3 at, double rr) {
        LivingEntity best = null; double bd = rr * rr;
        for (LivingEntity t : enemies(p.serverLevel(), p, new AABB(at, at).inflate(rr))) { double d = mid(t).distanceToSqr(at); if (d < bd) { bd = d; best = t; } }
        return best;
    }

    /** The warmest foe near: burning ones first, then fire-proof ones, then the nearest. */
    static LivingEntity warmest(ServerPlayer p, Vec3 at, double rr) {
        LivingEntity best = null; double bs = -1e9;
        for (LivingEntity t : enemies(p.serverLevel(), p, new AABB(at, at).inflate(rr))) {
            double sc = (t.isOnFire() ? 100 : 0) + (t.fireImmune() ? 50 : 0) - mid(t).distanceTo(at);
            if (sc > bs) { bs = sc; best = t; }
        }
        return best;
    }

    // ------------------------------------------------------------------ the pages
    public static void detonate(ServerPlayer p, float power) { detonateAll(p); }

    /** Prime Item: arm the dropped item where you look (3 with Booby Trap, and your next 5 arrows). */
    public static void primeItem(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        Vec3 eye = p.getEyePosition(), at = clip(sl, p, eye, eye.add(p.getLookAngle().scale(10)));
        int max = r(p, "a3") > 0 ? 3 : 1, n = 0;
        for (ItemEntity it : sl.getEntitiesOfClass(ItemEntity.class, new AABB(at, at).inflate(3))) { if (n >= max) break; if (arm(p, it, true)) n++; }
        if (r(p, "a3") > 0) f(p).arrowsArmed = 5;
        play(sl, at, "fuse_arm", 1f, 0.9f);
        if (n == 0 && r(p, "a3") <= 0) p.displayClientMessage(Component.literal("No item there to arm").withStyle(ChatFormatting.GRAY), true);
    }

    /** Heat Seeker: a small homing charge that hunts the warmest foe near. */
    public static void heatSeeker(ServerPlayer p, float power) {
        F s = f(p);
        ServerLevel sl = p.serverLevel();
        int lives = 1 + (mastery(p) >= 2 ? 1 : 0) + (r(p, "s2") > 0 ? 2 : 0);
        int count = r(p, "s3") > 0 ? 2 : 1;
        for (int i = 0; i < count; i++)
            s.seekers.add(new Seeker(new Vec3[]{p.position().add(rotY(p.getLookAngle(), i == 0 ? -0.6 : 0.6).scale(1.2)).add(0, 0.4, 0)}, sl.getGameTime() + 300, new int[]{lives}));
        play(sl, p.position(), "fuse_seeker", 1f, 1f);
    }

    /** Air Bubble: bubbles drift to foes and pop on them (more, and orbiting you, with the Air nodes). */
    public static void airBubble(ServerPlayer p, float power) {
        F s = f(p);
        ServerLevel sl = p.serverLevel();
        int n = 3 + 2 * r(p, "a1");
        boolean orbit = r(p, "a2") > 0;
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n;
            s.bubbles.add(new Bubble(new Vec3[]{mid(p).add(Math.cos(a) * 1.5, 0.3, Math.sin(a) * 1.5)}, sl.getGameTime() + (orbit ? 360 : 160), orbit));
        }
        play(sl, p.position(), "fuse_bubble", 1f, 1f);
    }

    /** Contact Charge: for 10 s, the next creature to strike you is armed (past your limit), and its blow is halved. */
    public static void contactCharge(ServerPlayer p, float power) {
        f(p).contactUntil = p.level().getGameTime() + 200;
        ModeHud.show(p, "contact_charge", 200);
        FxSend.ring(p.serverLevel(), PAL, mid(p), 0.6f, 0.9f, 0.04f, 200);
        play(p.serverLevel(), p.position(), "fuse_arm", 1f, 0.7f);
    }

    /** Chain Fuse: for 15 s every blast arms what it catches. */
    public static void chainFuse(ServerPlayer p, float power) {
        f(p).chainUntil = p.level().getGameTime() + 300;
        ModeHud.show(p, "chain_fuse", 300);
        FxSend.ring(p.serverLevel(), HOT, p.position().add(0, 0.1, 0), 0.3f, 2.4f, 0.05f, 12);
        play(p.serverLevel(), p.position(), "fuse_click", 1f, 0.7f);
    }

    /** Hidden Tenant: hide a charge in the ally you look at (or yourself): whatever hurts them sets it off. */
    public static void hiddenTenant(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        LivingEntity host = null; double bd = 256;
        Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
        for (LivingEntity t : sl.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(16), x -> x != p && x.isAlive() && (x.isAlliedTo(p) || x instanceof Player
                || (x instanceof net.minecraft.world.entity.TamableAnimal ta && p.getUUID().equals(ta.getOwnerUUID()))))) {
            Vec3 d = mid(t).subtract(eye);
            if (d.normalize().dot(look) < 0.95) continue;
            if (d.lengthSqr() < bd) { bd = d.lengthSqr(); host = t; }
        }
        if (host == null) host = p;
        f(p).tenants.put(host, sl.getGameTime() + (r(p, "a4") > 0 ? 1200 : 600));
        ModeHud.show(p, "hidden_tenant", r(p, "a4") > 0 ? 1200 : 600);
        HOSTS.put(host, p.getUUID());
        FxSend.ring(sl, PAL, mid(host), 0.3f, 1.0f, 0.05f, 10);
        play(sl, host.position(), "fuse_arm", 1f, 0.8f);
        p.displayClientMessage(Component.literal("A tenant hides in " + (host == p ? "you" : host.getDisplayName().getString())).withStyle(ChatFormatting.LIGHT_PURPLE), true);
    }

    /** Loop of Ruin: for 15 s every blast you set off happens again 3 s later, where it was (twice with Endless Loop). */
    public static void loopOfRuin(ServerPlayer p, float power) {
        f(p).loopUntil = p.level().getGameTime() + 300;
        ModeHud.show(p, "loop_of_ruin", 300);
        ServerLevel sl = p.serverLevel();
        FxSend.ring(sl, PAL, p.position().add(0, 0.1, 0), 0.3f, 12f, 0.1f, 16);
        FxSend.ring(sl, HOT, p.position().add(0, 0.1, 0), 11.6f, 12f, 0.05f, 300);
        play(sl, p.position(), "fuse_loop", 1.4f, 1f);
        detonateAll(p);
    }

    // ------------------------------------------------------------------ the G wheel
    static boolean wheel(ServerPlayer p, int slot) {
        F s = f(p);
        ServerLevel sl = p.serverLevel();
        switch (slot) {
            case 0 -> { return detonateAll(p) > 0; }
            case 1 -> {
                s.mode = (s.mode + 1) % 3;
                ModeHud.standing(p, new String[]{"fuse_mode_timer", "fuse_mode_near"}, s.mode - 1);
                p.displayClientMessage(Component.literal(new String[]{"Armed targets blow on Detonate", "Armed targets blow 3 s after arming", "Armed targets blow when a foe comes near"}[s.mode]).withStyle(ChatFormatting.LIGHT_PURPLE), true);
                play(sl, p.position(), "fuse_click", 0.8f, 1.4f);
            }
            case 2 -> {
                s.coin = p.getEyePosition().add(p.getLookAngle());
                s.coinDir = p.getLookAngle();
                s.coinUntil = sl.getGameTime() + 14;
                play(sl, p.position(), "fuse_coin", 1f, 1f);
            }
            default -> {
                int n = s.armed.size();
                if (n == 0) { p.displayClientMessage(Component.literal("Nothing is armed").withStyle(ChatFormatting.GRAY), true); return false; }
                s.armed.clear();
                var md = io.redspace.ironsspellbooks.api.magic.MagicData.getPlayerMagicData(p);
                float max = (float) p.getAttributeValue(io.redspace.ironsspellbooks.api.registry.AttributeRegistry.MAX_MANA.get());
                md.setMana(Math.min(max, md.getMana() + 8 * n));
                play(sl, p.position(), "fuse_click", 0.8f, 0.6f);
                p.displayClientMessage(Component.literal("Disarmed " + n + " - " + 8 * n + " mana back").withStyle(ChatFormatting.LIGHT_PURPLE), true);
            }
        }
        return true;
    }

    /** For the wheel's middle: mode, armed now, limit. */
    static int[] extra(ServerPlayer p) {
        F s = f(p);
        return new int[]{s.mode, s.armed.size(), limit(p)};
    }

    @SubscribeEvent public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) { STATE.remove(e.getEntity().getUUID()); }

    // test hooks
    public static void testReset(ServerPlayer p) { STATE.remove(p.getUUID()); }
    public static int testArmed(ServerPlayer p) { return f(p).armed.size(); }
    public static int testMode(ServerPlayer p) { return f(p).mode; }
    public static boolean testHost(ServerPlayer p, LivingEntity host) { return p.getUUID().equals(HOSTS.get(host)); }
}
