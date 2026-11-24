package com.ironspellgrimoire.server;

import static com.ironspellgrimoire.server.FxUtil.*;

import com.ironspellgrimoire.IronSpellGrimoire;
import com.ironspellgrimoire.entity.AddonBoss;
import com.ironspellgrimoire.entity.OmenAfterimage;
import com.ironspellgrimoire.network.AddonNetwork;
import com.ironspellgrimoire.network.OmenPackets;
import com.ironspellgrimoire.registry.AddonEntities;
import com.ironspellgrimoire.registry.AddonItemRegistry;
import io.redspace.ironsspellbooks.api.util.Utils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * The Grimoire of Ill Omen: a law of misfortune around you (every name, look and line here is original).
 * <ul>
 *   <li><b>The Law of Ill Fortune</b> (passive): anything that pursues you - a monster, an animal, a villager, a guard,
 *       another mod's NPC, another player where PvP is allowed - builds Misfortune on itself. Pursuing means targeting
 *       you, closing in on you, standing behind you facing your back, or having struck you in the last 10 s. Misfortune
 *       fades once it gives up. When it fills, a calamity strikes: a falling weight, a stumble, its own blow turned back,
 *       a stray shard, or (in rain) needles. Calamities hurt by karma: more for what has hurt you, for elites and bosses
 *       (bosses keep their caps). Effects and hitboxes only - no blocks touched, no status effects.</li>
 *   <li>Pages: Ill-Fated Path (footprints that trip whoever follows them), Laden Token (a charm that calls a calamity on
 *       whoever touches it, or on the next blow of whoever it strikes), Afterimage (a black-blue silhouette that
 *       pursuers chase, through walls), Needle Rain, Domino (calamities leap to the next pursuer), Stillness (stand still
 *       and blows rebound as calamity), Karma's Ledger (one target pays for everything at once), The Law Itself
 *       (the ultimate: for 20 s calamity hunts every pursuer, worse each time).</li>
 *   <li>Mastery: 2 the omen marks show farther away; 3 Misfortune builds 25% faster; 4 calamities may leap once on their
 *       own; 5 even foes that only mark you as their target, standing still, build Misfortune.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = IronSpellGrimoire.MODID)
public final class OmenFx {
    private OmenFx() {}

    static final int DARK = Fx.YAMI, BLUE = Fx.WATER, PALE = Fx.ICE;
    static final UUID TRIP = UUID.fromString("7a1e0000-0000-4000-8000-0000000000e1");
    public static final float FULL = 100f;

    /** What the law knows about one creature: its Misfortune, its karma, and its brushes with your marks. */
    static final class Fate {
        float misfortune, dealt;
        int calamities, prints;
        long lastStruckYou = -1000, ladenUntil, lastPrint;
        UUID by;   // the Ill Omen mage it is pursuing
    }
    static final Map<LivingEntity, Fate> FATES = new WeakHashMap<>();
    static Fate fate(LivingEntity e) { return FATES.computeIfAbsent(e, k -> new Fate()); }

    static final class Mage {
        long pathUntil, dominoUntil, stillUntil, lawUntil, rainUntil;
        int lawCount;
        Vec3 lastPrintAt;
        final List<Print> prints = new ArrayList<>();
        final List<Token> tokens = new ArrayList<>();
    }
    record Print(Vec3 at, long until, int id) {}
    record Token(Vec3 at, long until) {}
    static final Map<UUID, Mage> MAGES = new HashMap<>();
    static Mage mage(Player p) { return MAGES.computeIfAbsent(p.getUUID(), k -> new Mage()); }
    private static int printIds;

    static ItemStack book(Player p) { return Utils.getPlayerSpellbookStack(p); }
    public static boolean has(Player p) { ItemStack b = book(p); return b != null && b.is(AddonItemRegistry.OMEN_GRIMOIRE.get()); }
    static int level(Player p) { ItemStack b = book(p); return b == null ? 0 : GrimoireMastery.level(b); }

    // ================================================================== who is pursuing
    /** Pursuing p: targets p (or p's Afterimage), struck p in the last 10 s, or closes in on / stands behind p. */
    public static boolean pursues(LivingEntity e, ServerPlayer p) {
        if (e == p || !e.isAlive()) return false;
        if (e instanceof Mob m && m.getTarget() != null && (m.getTarget() == p || (m.getTarget() instanceof OmenAfterimage a && p.getUUID().equals(a.owner)))) return true;
        Fate f = FATES.get(e);
        if (f != null && p.level().getGameTime() - f.lastStruckYou < 200) return true;
        return e instanceof Player o && o != p && com.ironspellgrimoire.AddonConfig.mayHurt(p, o) && closing(o, p) > 0.15;
    }

    /** v laid flat, not normalised (FxUtil.flat normalises, and turns a standstill into a direction). */
    static Vec3 hz(Vec3 v) { return new Vec3(v.x, 0, v.z); }

    /** How fast e is coming at p (blocks a tick, along the line between them). */
    static double closing(LivingEntity e, Player p) {
        Vec3 to = hz(p.position().subtract(e.position()));
        if (to.lengthSqr() < 1e-4) return 0;
        Vec3 v = hz(e.position().subtract(e.xo, e.yo, e.zo));   // how it actually moved, which mobs' deltaMovement doesn't always show
        return v.dot(to.normalize());
    }

    /** e stands within 12 blocks behind p and faces p's back. */
    static boolean behind(LivingEntity e, Player p) {
        Vec3 toE = hz(e.position().subtract(p.position()));
        if (toE.lengthSqr() < 1e-4 || toE.lengthSqr() > 144) return false;
        Vec3 look = hz(p.getLookAngle()), eLook = hz(e.getLookAngle());
        if (look.lengthSqr() < 1e-4 || eLook.lengthSqr() < 1e-4) return false;
        Vec3 d = toE.normalize();
        return d.dot(look.normalize()) < -0.5 && eLook.normalize().dot(d.reverse()) > 0.7;
    }

    // ================================================================== the tick
    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || !p.isAlive()) return;
        if (!has(p)) { if (MAGES.remove(p.getUUID()) != null) AddonNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new OmenPackets.Marks(new int[0], new float[0])); return; }
        Mage m = mage(p);
        ServerLevel sl = p.serverLevel();
        long now = sl.getGameTime();
        if (now < m.pathUntil && now % 4 == 0) leavePrint(p, m, now);
        if (now % 5 == 0) {
            prints(p, m, now);
            tokens(p, m, now);
        }
        if (now % 10 != 0) return;
        int lvl = level(p);
        float rate = lvl >= 3 ? 1.25f : 1f;
        List<Integer> ids = new ArrayList<>();
        List<Float> fills = new ArrayList<>();
        double markRange = lvl >= 2 ? 32 : 16;
        for (LivingEntity t : sl.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(32), x -> x != p && x.isAlive() && !(x instanceof OmenAfterimage))) {
            Fate f = FATES.get(t);
            boolean chasing = pursues(t, p);
            boolean targetsOnly = !chasing && lvl >= 5 && t instanceof Mob mb && mb.getTarget() == p;
            if (chasing || targetsOnly) {
                f = fate(t);
                f.by = p.getUUID();
                float add = targetsOnly ? 0.5f : 1f;          // per half second
                if (chasing) {
                    double c = closing(t, p);
                    if (c > 0.05) add += 2f;                    // closing in
                    if (behind(t, p)) add += 2f;                // at your back
                }
                f.misfortune += add * rate * 2f;
                if (f.misfortune >= FULL) calamity(p, t, false);
            } else if (f != null && f.misfortune > 0) {
                f.misfortune = Math.max(0f, f.misfortune - 3f);   // it gives up: the omen fades
            }
            if (f != null && f.misfortune > 0 && t.distanceTo(p) <= markRange) { ids.add(t.getId()); fills.add(Math.min(1f, f.misfortune / FULL)); }
        }
        // The Law Itself: calamity hunts every pursuer, worse each time
        if (now < m.lawUntil && now % 20 == 0) {
            for (LivingEntity t : sl.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(24), x -> x != p && pursues(x, p))) calamity(p, t, true);
            m.lawCount++;
        }
        // Needle Rain
        if (now < m.rainUntil) needles(p, m, now);
        int[] ia = ids.stream().mapToInt(Integer::intValue).toArray();
        float[] fa = new float[fills.size()];
        for (int i = 0; i < fa.length; i++) fa[i] = fills.get(i);
        AddonNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new OmenPackets.Marks(ia, fa));
    }

    // ================================================================== calamity
    /** How much a creature has earned: what it has done to you, and what it is. */
    static float karma(LivingEntity t) {
        Fate f = FATES.get(t);
        float k = 1f + (f == null ? 0 : Math.min(2f, f.dealt / 10f));
        if (t instanceof AddonBoss || TimeStop.isBoss(t)) k += 0.5f;
        if (EliteMobs.isElite(t)) k += 0.5f;
        return k;
    }

    /** A calamity strikes t. Some kind of accident, scaled by karma; Domino and mastery 4 let it leap on. */
    public static void calamity(ServerPlayer p, LivingEntity t, boolean byLaw) {
        if (!t.isAlive() || t == p) return;
        ServerLevel sl = p.serverLevel();
        Fate f = fate(t);
        f.misfortune = 20f;
        f.calamities++;
        Mage m = mage(p);
        float scale = karma(t) * (byLaw ? 1f + 0.25f * Math.min(8, m.lawCount) : 1f);
        Vec3 c = mid(t);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        boolean rain = sl.isRainingAt(t.blockPosition().above());
        boolean struckLately = sl.getGameTime() - f.lastStruckYou < 60 && f.dealt > 0;
        int kind = rain && r.nextInt(3) == 0 ? 4 : struckLately && r.nextInt(2) == 0 ? 2 : r.nextInt(4) == 2 ? 3 : r.nextInt(4) == 2 ? 1 : r.nextInt(2);
        switch (kind) {
            case 0 -> {   // a weight falls from nowhere
                Vec3 top = t.position().add(0, t.getBbHeight() + 5, 0);
                FxSend.ring(sl, DARK, t.position().add(0, 0.06, 0), 0.2f, 1.1f, 0.05f, 8);
                for (int i = 0; i < 4; i++) {
                    final int k = i;
                    AstaFx.later(i * 2, () -> Kit.orb(sl, BLUE, top.add(0, -k * 1.25, 0), new Vec3(0, -1.25, 0), 0.55f, 3));
                }
                AstaFx.later(8, () -> {
                    if (!t.isAlive()) return;
                    FxSend.ring(sl, BLUE, t.position().add(0, 0.1, 0), 0.2f, 1.6f, 0.1f, 8);
                    FxSend.shake(sl, t.position(), 0.2f, 4, 12);
                    play(sl, t.position(), "omen_fall", 1f, 1f);
                    AstaFx.damage(p, t, 7f * scale, "the_law_itself");
                });
            }
            case 1 -> {   // it stumbles: thrown down, stopped for a moment
                play(sl, t.position(), "omen_trip", 1f, 1f);
                FxSend.ring(sl, DARK, t.position().add(0, 0.05, 0), 0.3f, 1.2f, 0.04f, 10);
                TimedModifiers.apply(t, Attributes.MOVEMENT_SPEED, TRIP, "Stumble", -0.9, AttributeModifier.Operation.MULTIPLY_TOTAL, 30);
                t.setDeltaMovement(t.getDeltaMovement().multiply(0.2, 1, 0.2).add(0, -0.3, 0)); t.hurtMarked = true;
                AstaFx.damage(p, t, 4f * scale, "ill_fated_path");
            }
            case 2 -> {   // its own blow comes back to it
                play(sl, c, "omen_rebound", 1f, 1f);
                FxSend.ring(sl, PALE, c, 0.8f, 0.2f, 0.06f, 8);
                AstaFx.damage(p, t, Math.max(4f, f.dealt) * scale, "stillness");
            }
            case 3 -> {   // a stray shard out of nowhere
                double ang = r.nextDouble(Math.PI * 2);
                Vec3 from = c.add(Math.cos(ang) * 6, 1.5, Math.sin(ang) * 6);
                FxSend.trail(sl, PALE, from, c, 0.08f, 5);
                play(sl, c, "omen_shard", 1f, 1f);
                AstaFx.damage(p, t, 5f * scale, "laden_token");
            }
            default -> {   // the rain turns to needles
                for (int i = 0; i < 6; i++) {
                    Vec3 a = c.add(r.nextDouble(-1, 1), 4, r.nextDouble(-1, 1));
                    FxSend.trail(sl, BLUE, a, a.add(-0.3, -4, 0), 0.04f, 4);
                }
                play(sl, c, "omen_needles", 1f, 1f);
                AstaFx.damage(p, t, 6f * scale, "needle_rain");
            }
        }
        // it leaps on: Domino always, mastery 4 now and then
        boolean leap = sl.getGameTime() < m.dominoUntil || (level(p) >= 4 && r.nextInt(10) < 3);
        if (leap && !chaining) {
            LivingEntity next = null;
            double bd = 64;
            for (LivingEntity o : sl.getEntitiesOfClass(LivingEntity.class, t.getBoundingBox().inflate(8), x -> x != t && x != p && pursues(x, p))) {
                double d = o.distanceToSqr(t);
                if (d < bd) { bd = d; next = o; }
            }
            if (next != null) {
                LivingEntity n = next;
                FxSend.trail(sl, BLUE, c, mid(n), 0.06f, 8);
                play(sl, c, "omen_domino", 0.7f, 1.2f);
                AstaFx.later(6, () -> { chaining = true; try { calamity(p, n, false); } finally { chaining = false; } });
            }
        }
    }
    private static boolean chaining;

    // ================================================================== blows: karma, Laden, Stillness
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onHurt(LivingHurtEvent e) {
        LivingEntity victim = e.getEntity();
        Entity src = e.getSource().getEntity();
        if (victim.level().isClientSide || !(src instanceof LivingEntity attacker) || attacker == victim) return;
        // a laden creature's next blow (at anything) calls its calamity
        Fate lf = FATES.get(attacker);
        if (lf != null && lf.ladenUntil > attacker.level().getGameTime() && lf.by != null && attacker.level() instanceof ServerLevel sl
                && sl.getPlayerByUUID(lf.by) instanceof ServerPlayer owner) {
            lf.ladenUntil = 0;
            AstaFx.later(1, () -> calamity(owner, attacker, false));
        }
        if (!(victim instanceof ServerPlayer p) || !has(p)) return;
        Fate f = fate(attacker);
        f.by = p.getUUID();
        f.lastStruckYou = p.level().getGameTime();
        f.dealt += e.getAmount();
        f.misfortune += 15f;   // striking you is the surest pursuit there is
        // Stillness: standing still, the blow is turned back as calamity
        Mage m = mage(p);
        Vec3 v = p.getDeltaMovement();
        if (p.level().getGameTime() < m.stillUntil && v.x * v.x + v.z * v.z < 0.004) {
            float amt = e.getAmount();
            e.setCanceled(true);
            play(p.serverLevel(), p.position(), "omen_rebound", 1f, 0.8f);
            FxSend.ring(p.serverLevel(), PALE, mid(p), 0.3f, 1.3f, 0.05f, 8);
            f.dealt = Math.max(f.dealt, amt * 1.5f);
            AstaFx.later(1, () -> calamity(p, attacker, false));
        }
    }

    @SubscribeEvent public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) { MAGES.remove(e.getEntity().getUUID()); }

    // ================================================================== 1. Ill-Fated Path
    /** For 10 s you leave footprints; whatever follows in them pays. Three prints, and a calamity. */
    public static void illFatedPath(ServerPlayer p, float power) {
        Mage m = mage(p);
        m.pathUntil = p.level().getGameTime() + 200;
        m.lastPrintAt = null;
        play(p.serverLevel(), p.position(), "omen_step", 1f, 0.8f);
        FxSend.ring(p.serverLevel(), DARK, p.position().add(0, 0.05, 0), 0.3f, 1.4f, 0.04f, 10);
    }

    static void leavePrint(ServerPlayer p, Mage m, long now) {
        if (!p.onGround()) return;
        Vec3 at = p.position();
        if (m.lastPrintAt != null && m.lastPrintAt.distanceToSqr(at) < 0.8 * 0.8) return;
        m.lastPrintAt = at;
        m.prints.add(new Print(at, now + 160, ++printIds));
        FxSend.ring(p.serverLevel(), BLUE, at.add(0, 0.04, 0), 0.12f, 0.32f, 0.02f, 160);
    }

    static void prints(ServerPlayer p, Mage m, long now) {
        m.prints.removeIf(pr -> now >= pr.until);
        if (m.prints.isEmpty()) return;
        ServerLevel sl = p.serverLevel();
        for (LivingEntity t : sl.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(40), x -> x != p && x.isAlive() && pursues(x, p))) {
            Fate f = fate(t);
            for (Print pr : m.prints) {
                if (pr.id <= f.lastPrint || t.position().distanceToSqr(pr.at) > 0.9 * 0.9) continue;
                f.lastPrint = pr.id;
                f.misfortune += 34f;
                play(sl, pr.at, "omen_step", 0.8f, 1f + 0.1f * f.prints);
                FxSend.ring(sl, PALE, pr.at.add(0, 0.05, 0), 0.1f, 0.6f, 0.04f, 6);
                if (++f.prints >= 3) { f.prints = 0; calamity(p, t, false); }
                break;
            }
        }
    }

    // ================================================================== 2. Laden Token
    /** A charm flicked ahead: whatever it strikes carries a calamity on its next blow; where it lands, it waits for a toucher. */
    public static void ladenToken(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        play(sl, p.position(), "omen_token", 1f, 1f);
        Vec3 eye = p.getEyePosition(), dir = p.getLookAngle();
        LivingEntity aimed = aimed(p, 16, 6);
        if (aimed != null) {
            Fate f = fate(aimed);
            f.by = p.getUUID();
            f.ladenUntil = sl.getGameTime() + 600;
            f.misfortune += 25f;
            FxSend.trail(sl, BLUE, eye, mid(aimed), 0.06f, 6);
            FxSend.ring(sl, DARK, mid(aimed), 0.2f, 0.9f, 0.05f, 12);
            return;
        }
        Vec3 land = BossKit.ground(sl, clip(sl, p, eye, eye.add(dir.scale(10))).add(0, 0.5, 0));
        mage(p).tokens.add(new Token(land, sl.getGameTime() + 600));
        FxSend.trail(sl, BLUE, eye, land, 0.05f, 6);
        FxSend.ring(sl, DARK, land.add(0, 0.05, 0), 0.25f, 0.45f, 0.03f, 600);
    }

    static void tokens(ServerPlayer p, Mage m, long now) {
        if (m.tokens.isEmpty()) return;
        ServerLevel sl = p.serverLevel();
        for (Iterator<Token> it = m.tokens.iterator(); it.hasNext(); ) {
            Token tk = it.next();
            if (now >= tk.until) { it.remove(); continue; }
            for (LivingEntity t : enemies(sl, p, new AABB(tk.at, tk.at).inflate(0.9, 1.5, 0.9))) {
                if (t instanceof OmenAfterimage) continue;
                it.remove();
                play(sl, tk.at, "omen_token", 1f, 0.7f);
                calamity(p, t, false);
                break;
            }
        }
    }

    // ================================================================== 3. Afterimage
    /** A black-blue silhouette of you, walking away through walls; everything near pursues it instead - and that counts. */
    public static void afterimage(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        OmenAfterimage a = AddonEntities.OMEN_AFTERIMAGE.get().create(sl);
        if (a == null) return;
        Vec3 dir = flat(p.getLookAngle()).normalize();
        Vec3 at = p.position().add(dir.scale(1.5));
        a.moveTo(at.x, p.getY(), at.z, p.getYRot(), 0);
        a.owner = p.getUUID();
        a.heading = dir;
        sl.addFreshEntity(a);
        for (Mob mob : sl.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(16), x -> x.isAlive() && (x.getTarget() == p || x instanceof net.minecraft.world.entity.monster.Enemy) && !x.isAlliedTo(p))) mob.setTarget(a);
        play(sl, at, "omen_afterimage", 1.2f, 1f);
        FxSend.ring(sl, DARK, at.add(0, 0.05, 0), 0.2f, 1.6f, 0.05f, 10);
    }

    // ================================================================== 4. Needle Rain
    /** For 5 s ill luck falls as needles on everyone pursuing you within 10 blocks - harder in real rain. */
    public static void needleRain(ServerPlayer p, float power) {
        mage(p).rainUntil = p.level().getGameTime() + 100;
        play(p.serverLevel(), p.position(), "omen_needles", 1.3f, 0.9f);
        FxSend.ring(p.serverLevel(), BLUE, p.position().add(0, 0.05, 0), 9.8f, 10f, 0.05f, 100);
    }

    static void needles(ServerPlayer p, Mage m, long now) {
        ServerLevel sl = p.serverLevel();
        boolean rain = sl.isRainingAt(p.blockPosition().above());
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (LivingEntity t : sl.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(10), x -> x != p && x.isAlive() && pursues(x, p))) {
            Vec3 c = mid(t);
            for (int i = 0; i < 3; i++) { Vec3 a = c.add(r.nextDouble(-0.8, 0.8), 3.5, r.nextDouble(-0.8, 0.8)); FxSend.trail(sl, BLUE, a, a.add(-0.25, -3.5, 0), 0.035f, 4); }
            AstaFx.damage(p, t, 3f * (rain ? 1.6f : 1f), "needle_rain");
            Fate f = fate(t);
            f.misfortune += 10f;
            if (f.misfortune >= FULL) calamity(p, t, false);
        }
    }

    // ================================================================== 5-8
    /** Domino: for 15 s every calamity leaps to the nearest other pursuer. */
    public static void domino(ServerPlayer p, float power) {
        mage(p).dominoUntil = p.level().getGameTime() + 300;
        play(p.serverLevel(), p.position(), "omen_domino", 1.2f, 1f);
        FxSend.ring(p.serverLevel(), BLUE, mid(p), 0.3f, 2.2f, 0.05f, 10);
    }

    /** Stillness: for 4 s, while you stand still, blows aimed at you rebound on their senders as calamity. */
    public static void stillness(ServerPlayer p, float power) {
        mage(p).stillUntil = p.level().getGameTime() + 80;
        play(p.serverLevel(), p.position(), "omen_still", 1.2f, 1f);
        FxSend.ring(p.serverLevel(), PALE, p.position().add(0, 0.05, 0), 1.3f, 1.5f, 0.05f, 80);
    }

    /** Karma's Ledger: the one you look at pays for everything at once - its Misfortune and what it has done to you. */
    public static void karmasLedger(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        LivingEntity t = aimed(p, 24, 8);
        play(sl, p.position(), "omen_ledger", 1.3f, 1f);
        if (t == null) return;
        Fate f = fate(t);
        float dmg = (8f + f.misfortune * 0.15f + f.dealt) * karma(t);
        f.misfortune = 0;
        f.dealt = 0;
        Vec3 c = mid(t);
        FxSend.beam(sl, DARK, c.add(0, 6, 0), c, 0.5f, 10);
        FxSend.ring(sl, BLUE, t.position().add(0, 0.1, 0), 0.2f, 2.2f, 0.12f, 12);
        FxSend.shake(sl, c, 0.4f, 6, 16);
        AstaFx.damage(p, t, dmg, "karmas_ledger");
    }

    /** The Law Itself: for 20 s calamity hunts every pursuer within 24 blocks, each blow worse than the last. */
    public static void theLawItself(ServerPlayer p, float power) {
        Mage m = mage(p);
        m.lawUntil = p.level().getGameTime() + 400;
        m.lawCount = 0;
        ServerLevel sl = p.serverLevel();
        play(sl, p.position(), "omen_law", 1.6f, 1f);
        FxSend.ring(sl, DARK, p.position().add(0, 0.1, 0), 0.3f, 24f, 0.15f, 20);
        FxSend.ring(sl, BLUE, p.position().add(0, 0.08, 0), 23.6f, 24f, 0.05f, 400);
    }

    // ================================================================== test hooks
    public static float testMisfortune(LivingEntity e) { Fate f = FATES.get(e); return f == null ? 0 : f.misfortune; }
    public static void testSet(LivingEntity e, float m, float dealt) { Fate f = fate(e); f.misfortune = m; f.dealt = dealt; }
    public static boolean testLaden(LivingEntity e) { Fate f = FATES.get(e); return f != null && f.ladenUntil > e.level().getGameTime(); }
    public static int testCalamities(LivingEntity e) { Fate f = FATES.get(e); return f == null ? 0 : f.calamities; }
    public static void testStruck(LivingEntity e, Player p) { Fate f = fate(e); f.by = p.getUUID(); f.lastStruckYou = e.level().getGameTime(); }
    public static int testPrints(Player p) { return mage(p).prints.size(); }
    public static void testReset(Player p) { MAGES.remove(p.getUUID()); }
}
