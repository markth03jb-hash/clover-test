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
 *   <li><b>The Law of Ill Fortune</b> (passive): every enemy within 10 blocks of you charges a bar over its head -
 *       the closer, the faster - and so does anything that pursues you - a monster, an animal, a villager, a guard,
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

    static final int DARK = 47, BLUE = 47, PALE = Fx.ICE;   // 47: the shadow palette - black with a blue sheen
    static final UUID TRIP = UUID.fromString("7a1e0000-0000-4000-8000-0000000000e1"), TRIPWIRE = UUID.fromString("7a1e0000-0000-4000-8000-0000000000e5");
    public static final float FULL = 100f;

    /** What the law knows about one creature: its Misfortune, its karma, and its brushes with your marks. */
    static final class Fate {
        float misfortune, dealt;
        int calamities, prints, snow;
        long lastStruckYou = -1000, ladenUntil, lastPrint, lastCalAt = -1000, badCreditUntil;
        UUID by;   // the Ill Omen mage it is pursuing
    }
    static final Map<LivingEntity, Fate> FATES = new WeakHashMap<>();
    static Fate fate(LivingEntity e) { return FATES.computeIfAbsent(e, k -> new Fate()); }

    static final class Mage {
        long pathUntil, dominoUntil, stillUntil, lawUntil, rainUntil;
        int lawCount;
        boolean lawEcho = true;
        Vec3 lastPrintAt;
        final List<Print> prints = new ArrayList<>();
        final List<Token> tokens = new ArrayList<>();
    }
    record Print(Vec3 at, long until, int id) {}
    record Token(Vec3 at, long until) {}
    static final Map<UUID, Mage> MAGES = new HashMap<>();
    static Mage mage(Player p) { return MAGES.computeIfAbsent(p.getUUID(), k -> new Mage()); }
    private static int printIds;

    // ------------------------------------------------------------------ its own look (client/OmenFxClient, shape 101)
    static com.ironspellgrimoire.network.FxPacket fx(int kind, Vec3 c, int life) {
        com.ironspellgrimoire.network.FxPacket q = new com.ironspellgrimoire.network.FxPacket();
        q.shape = 101; q.id = FxSend.nextId(); q.h = kind; q.c = c; q.life = life; q.v = new Vec3(-1, 0, 0); q.a1 = (float) (Math.random() * 6.28);
        return q;
    }
    static com.ironspellgrimoire.network.FxPacket on(int kind, Entity e, int life) { var q = fx(kind, e.position(), life); q.v = new Vec3(e.getId(), 0, 0); return q; }
    static void send(ServerLevel sl, com.ironspellgrimoire.network.FxPacket q) { FxSend.sendNear(sl, q.c, q); }
    /** A black line snapping from a to b. */
    static void line(ServerLevel sl, Vec3 a, Vec3 b) { var q = fx(6, a, 10); q.u = b; send(sl, q); }
    /** Ink splashing up at a point. */
    static void splash(ServerLevel sl, Vec3 at) { send(sl, fx(1, at, 10)); }

    static ItemStack book(Player p) { return Utils.getPlayerSpellbookStack(p); }
    public static boolean has(Player p) { ItemStack b = book(p); return b != null && b.is(AddonItemRegistry.OMEN_GRIMOIRE.get()); }
    static int level(Player p) { ItemStack b = book(p); return b == null ? 0 : GrimoireMastery.level(b); }
    /** A node of the Ledger of Ill Luck (BookTree.OMEN): its rank. */
    static int r(Player p, String id) { return BookTree.rank(p, id); }
    /** Scales the next calamity (a token's, a leap's), then goes back to 1. */
    private static float nextScale = 1f;
    private static int chainDepth;

    // ================================================================== who is pursuing
    /** Pursuing p: targets p (or p's Afterimage), struck p in the last 10 s, or closes in on / stands behind p. */
    public static boolean pursues(LivingEntity e, ServerPlayer p) {
        if (e == p || !e.isAlive()) return false;
        if (e instanceof Mob m && m.getTarget() != null && (m.getTarget() == p || (m.getTarget() instanceof OmenAfterimage a && p.getUUID().equals(a.owner)))) return true;
        Fate f = FATES.get(e);
        if (f != null && p.level().getGameTime() - f.lastStruckYou < 200) return true;
        return e instanceof Player o && o != p && com.ironspellgrimoire.AddonConfig.mayHurt(p, o) && closing(o, p) > 0.15;
    }

    /** How near an enemy must come before its bar starts to charge. */
    static final double NEAR = 10;

    /** An enemy of yours: a monster, or anything pursuing you (never a friend, a pet or a player you can't hurt). */
    static boolean hostile(ServerPlayer p, LivingEntity t) {
        if (t instanceof Player o) return o != p && com.ironspellgrimoire.AddonConfig.mayHurt(p, o) && pursues(t, p);
        if (t.isAlliedTo(p) || (t instanceof net.minecraft.world.entity.TamableAnimal ta && p.getUUID().equals(ta.getOwnerUUID()))) return false;
        return t instanceof net.minecraft.world.entity.monster.Enemy || pursues(t, p);
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
        if (now < m.pathUntil && now % (r(p, "p7") > 0 ? 2 : 4) == 0) leavePrint(p, m, now);
        if (now % 5 == 0) {
            prints(p, m, now);
            tokens(p, m, now);
        }
        if (now % 10 != 0) return;
        int lvl = level(p);
        float rate = (lvl >= 3 ? 1.25f : 1f) * (1f + 0.1f * r(p, "a4")) * (r(p, "K1") > 0 ? 1.2f : 1f) * (r(p, "K2") > 0 ? 0.8f : 1f);
        double nearR = NEAR + 2 * r(p, "a3");
        List<Integer> ids = new ArrayList<>();
        List<Float> fills = new ArrayList<>();
        double markRange = (lvl >= 2 ? 32 : 16) + (r(p, "c4") > 0 ? 16 : 0);
        for (LivingEntity t : sl.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(Math.max(32, markRange)), x -> x != p && x.isAlive() && !(x instanceof OmenAfterimage))) {
            Fate f = FATES.get(t);
            boolean chasing = pursues(t, p);
            boolean targetsOnly = !chasing && lvl >= 5 && t instanceof Mob mb && mb.getTarget() == p;
            double dist = t.distanceTo(p);
            boolean near = dist <= nearR && hostile(p, t);   // an enemy close to you: its bar charges, the closer the faster
            if (chasing || targetsOnly || near) {
                f = fate(t);
                f.by = p.getUUID();
                float add = targetsOnly && !near ? 0.5f : 1f;   // per half second
                if (near) add += (float) (4.0 * (1.0 - dist / nearR));
                if (r(p, "a2") > 0 && t instanceof Mob mb2 && mb2.getTarget() instanceof OmenAfterimage) add *= 2f;   // Lure
                if (r(p, "a5") > 0 && t.getLookAngle().dot(p.getEyePosition().subtract(t.getEyePosition()).normalize()) > 0.9) add *= 1.5f;   // Evil Eye
                if (f.badCreditUntil > now) add *= 2f;   // Bad Credit
                if (chasing) {
                    double c = closing(t, p);
                    if (c > 0.05) add += 2f;                    // closing in
                    if (behind(t, p)) add += 2f;                // at your back
                }
                f.misfortune += add * rate * 2f;
                if (f.misfortune >= FULL) calamity(p, t, false);
            } else if (f != null && f.misfortune > 0) {
                f.misfortune = Math.max(0f, f.misfortune - (r(p, "a7") > 0 ? 1.5f : 3f));   // it gives up: the omen fades
            }
            if (f != null && f.misfortune > 0 && t.distanceTo(p) <= markRange) { ids.add(t.getId()); fills.add(Math.min(1f, f.misfortune / FULL)); }
        }
        // The Law Itself: calamity hunts every pursuer, worse each time
        double lawR = r(p, "l5") > 0 ? 32 : 24;
        if (now < m.lawUntil && now % 20 == 0) {
            for (LivingEntity t : sl.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(lawR), x -> x != p && pursues(x, p))) calamity(p, t, true);
            m.lawCount++;
        }
        // Thirteenth Hour: once more, 13 s after the Law ends
        if (!m.lawEcho && m.lawUntil > 0 && now >= m.lawUntil + 260) {
            m.lawEcho = true;
            for (LivingEntity t : sl.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(lawR), x -> x != p && pursues(x, p))) calamity(p, t, true);
            play(sl, p.position(), "omen_law", 1.2f, 0.7f);
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
        if (EliteMobs.elite(t)) k += 0.5f;
        return k;
    }

    // ------------------------------------------------------------------ the twenty calamities
    public static final String[] KINDS = {
            "Falling Weight", "Stumble", "Stray Shard", "Slip", "Tangled Feet", "Fumble", "Loose Strap", "Wrong Turn",   // common
            "Rebound", "Collision", "Ricochet", "Needles", "Bad Landing", "Last Straw",                                  // when the moment fits
            "Tolling Bell", "Ill Wind", "Toppling Row", "Jinx", "Cracked Luck", "Snowball"};                            // rare
    static final UUID TANGLE = UUID.fromString("7a1e0000-0000-4000-8000-0000000000e2"), FUMBLE = UUID.fromString("7a1e0000-0000-4000-8000-0000000000e3"),
            STRAP = UUID.fromString("7a1e0000-0000-4000-8000-0000000000e4");
    /** Test hooks: force the next calamity's kind (-1 = pick), and the last kind struck. */
    public static int testForce = -1, lastKind = -1;

    /** Another creature within r of t worth hitting with it: a pursuer of p, or a monster. */
    static LivingEntity neighbour(ServerPlayer p, LivingEntity t, double r, boolean pursuerOnly) {
        LivingEntity best = null; double bd = r * r;
        for (LivingEntity o : p.serverLevel().getEntitiesOfClass(LivingEntity.class, t.getBoundingBox().inflate(r), x -> x != t && x != p && x.isAlive() && !(x instanceof OmenAfterimage)
                && (pursues(x, p) || (!pursuerOnly && x instanceof net.minecraft.world.entity.monster.Enemy && !x.isAlliedTo(p))))) {
            double d = o.distanceToSqr(t);
            if (d < bd) { bd = d; best = o; }
        }
        return best;
    }

    /** Which accident: common ones often, the situational ones only when they fit, rare ones now and then (more at mastery 5). */
    static int pick(ServerPlayer p, LivingEntity t, Fate f, boolean nested) {
        if (testForce >= 0) { int k = testForce; testForce = -1; return k; }
        ServerLevel sl = p.serverLevel();
        int[] w = new int[KINDS.length];
        for (int i = 0; i < 8; i++) w[i] = 10;
        if (sl.getGameTime() - f.lastStruckYou < 60 && f.dealt > 0) w[8] = 14;
        if (neighbour(p, t, 4, false) != null) w[9] = 10;
        if (neighbour(p, t, 8, true) != null) w[10] = 10;
        if (sl.isRainingAt(t.blockPosition().above())) w[11] = 8;
        if (!t.onGround() && t.getDeltaMovement().y < 0.1) w[12] = 14;
        if (t.getHealth() < t.getMaxHealth() * 0.3f) w[13] = 14;
        int rare = (level(p) >= 5 ? 6 : 3) * (r(p, "c6") > 0 ? 2 : 1);
        for (int i = 14; i < 20; i++) w[i] = rare;
        if (nested) w[19] = 0;
        int sum = 0; for (int x : w) sum += x;
        int r = ThreadLocalRandom.current().nextInt(sum);
        for (int i = 0; i < w.length; i++) { r -= w[i]; if (r < 0) return i; }
        return 0;
    }

    /** A calamity strikes t: one of twenty accidents, scaled by karma; Domino and mastery 4 let it leap on. */
    public static void calamity(ServerPlayer p, LivingEntity t, boolean byLaw) { calamity(p, t, byLaw, false); }

    static void calamity(ServerPlayer p, LivingEntity t, boolean byLaw, boolean nested) {
        if (!t.isAlive() || t == p) return;
        ServerLevel sl = p.serverLevel();
        Fate f = fate(t);
        long now0 = sl.getGameTime();
        f.misfortune = r(p, "c3") > 0 ? 40f : 20f;
        f.calamities++;
        Mage m = mage(p);
        float scale = karma(t) * (byLaw ? 1f + (r(p, "l6") > 0 ? 0.35f : 0.25f) * Math.min(8, m.lawCount) : 1f) * (nested ? 0.6f : 1f);
        scale *= nextScale; nextScale = 1f;
        scale *= 1f + 0.08f * r(p, "c1");
        if (r(p, "K1") > 0) scale *= 0.85f;
        if (r(p, "K2") > 0) scale *= 1.4f;
        if (r(p, "c7") > 0) { f.snow = now0 - f.lastCalAt <= 200 ? Math.min(4, f.snow + 1) : 0; scale *= 1f + 0.15f * f.snow; }   // Snowballing
        f.lastCalAt = now0;
        BookTree.grow(p, BookTree.OMEN, 1);
        if (r(p, "c5") > 0) { var md = io.redspace.ironsspellbooks.api.magic.MagicData.getPlayerMagicData(p); md.setMana(md.getMana() + 5); }
        int kind = pick(p, t, f, nested);
        if (byLaw) { var sh = fx(11, t.position(), 10); sh.u = p.position().add(0, 6, 0); send(sl, sh); }
        omen(sl, t, nested);
        strike(p, t, f, kind, scale);
        if (r(p, "c2") > 0) for (LivingEntity o : sl.getEntitiesOfClass(LivingEntity.class, t.getBoundingBox().inflate(3), x -> x != t && x != p && x.isAlive() && hostile(p, x)))
            AstaFx.damage(p, o, 5f * scale * 0.3f, "the_law_itself");   // Wide Sign
        if (r(p, "K6") > 0 && t.isAlive() && t.getHealth() < t.getMaxHealth() * 0.3f) { final float sc = scale; final int kk = kind; AstaFx.later(6, () -> { if (t.isAlive()) strike(p, t, fate(t), kk, sc); }); }   // Executioner
        if (!nested) lastKind = kind;
        if (t.distanceTo(p) < 32) p.displayClientMessage(net.minecraft.network.chat.Component.literal("Calamity: " + KINDS[kind]).withStyle(net.minecraft.ChatFormatting.BLUE), true);
        if (nested) return;
        // it leaps on: Domino always, mastery 4 now and then
        boolean domino = sl.getGameTime() < m.dominoUntil || (level(p) >= 4 && ThreadLocalRandom.current().nextInt(10) < 3);
        boolean jinx = r(p, "K3") > 0;
        int maxDepth = r(p, "n5") > 0 ? 2 : 1;
        if ((domino || jinx) && chainDepth < maxDepth) {
            LivingEntity n = neighbour(p, t, 8, !jinx || domino);
            if (n != null) {
                Vec3 c = mid(t);
                line(sl, c, mid(n));
                play(sl, c, "omen_domino", 0.7f, 1.2f);
                float lead = (domino ? 1f : 0.5f) * (r(p, "n6") > 0 ? 1.2f : 1f);
                final int depth = chainDepth + 1;
                AstaFx.later(6, () -> { int was = chainDepth; chainDepth = depth; nextScale = lead; try { calamity(p, n, false); } finally { chainDepth = was; nextScale = 1f; } });
            }
        }
    }

    /** The calamity's sign, before the accident lands: a black-blue sigil opens on the ground under it, cracked and
     *  rayed, a shaft of ill light falls on it from far above, and the world jolts. */
    static void omen(ServerLevel sl, LivingEntity t, boolean small) {
        Vec3 feet = t.position().add(0, 0.06, 0), c = mid(t);
        float k = small ? 0.6f : 1f;
        var sign = fx(13, feet, 34); sign.r = 3.4f * k; send(sl, sign);
        FxSend.shake(sl, feet, small ? 0.2f : 0.45f, 6, 18);
        if (!small) FxSend.impactFrame(sl, c, 3, false, 18);
        play(sl, c, "omen_ledger", 1.2f, 0.5f);
    }

    static Vec3 away(ServerPlayer p, LivingEntity t) {
        Vec3 d = hz(t.position().subtract(p.position()));
        return d.lengthSqr() < 1e-4 ? hz(t.getLookAngle()).normalize() : d.normalize();
    }

    static void strike(ServerPlayer p, LivingEntity t, Fate f, int kind, float scale) {
        ServerLevel sl = p.serverLevel();
        Vec3 c = mid(t), feet = t.position();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        switch (kind) {
            case 0 -> {   // Falling Weight: an iron weight drops out of nowhere
                Vec3 top = feet.add(0, t.getBbHeight() + 5, 0);
                splash(sl, feet.add(0, 0.06, 0));
                for (int i = 0; i < 4; i++) { final int k = i; AstaFx.later(i * 2, () -> { Kit.orb(sl, BLUE, top.add(0, -k * 1.25, 0), new Vec3(0, -1.25, 0), 1.1f, 3); FxSend.box(sl, DARK, top.add(0, -k * 1.25, 0), new Vec3(1, 0, 0), new Vec3(0, 1, 0), 0.6f, 0.6f, 0.5f, 3, 0, com.ironspellgrimoire.network.FxPacket.POP); }); }
                AstaFx.later(8, () -> {
                    if (!t.isAlive()) return;
                    splash(sl, t.position().add(0, 0.1, 0));
                    FxSend.cracks(sl, DARK, t.position().add(0, 0.05, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1), 2f, 20, false, t.getId() + 3);
                    FxSend.shake(sl, t.position(), 0.5f, 6, 16);
                    play(sl, t.position(), "omen_fall", 1.3f, 0.8f);
                    AstaFx.damage(p, t, 7f * scale, "the_law_itself");
                });
            }
            case 1 -> {   // Stumble: down, and pinned a moment
                play(sl, feet, "omen_trip", 1f, 1f);
                splash(sl, feet.add(0, 0.05, 0));
                TimedModifiers.apply(t, Attributes.MOVEMENT_SPEED, TRIP, "Stumble", -0.9, AttributeModifier.Operation.MULTIPLY_TOTAL, 30);
                t.setDeltaMovement(t.getDeltaMovement().multiply(0.2, 1, 0.2).add(0, -0.3, 0)); t.hurtMarked = true;
                AstaFx.damage(p, t, 4f * scale, "ill_fated_path");
            }
            case 2 -> {   // Stray Shard: a sliver from nowhere
                double ang = r.nextDouble(Math.PI * 2);
                Vec3 from = c.add(Math.cos(ang) * 6, 1.5, Math.sin(ang) * 6);
                line(sl, from, c);
                play(sl, c, "omen_shard", 1f, 1f);
                AstaFx.damage(p, t, 5f * scale, "laden_token");
            }
            case 3 -> {   // Slip: its feet go out, it slides off and lands hard
                play(sl, feet, "omen_trip", 1f, 1.3f);
                line(sl, feet.add(0, 0.1, 0), feet.add(away(p, t).scale(2.5)).add(0, 0.1, 0));
                Kit.push(t, away(p, t), 0.9, 0.15);
                AstaFx.later(8, () -> { if (t.isAlive()) { splash(sl, t.position().add(0, 0.05, 0)); AstaFx.damage(p, t, 5f * scale, "ill_fated_path"); } });
            }
            case 4 -> {   // Tangled Feet: slowed for a while
                play(sl, feet, "omen_step", 1f, 0.7f);
                splash(sl, feet.add(0, 0.05, 0));
                TimedModifiers.apply(t, Attributes.MOVEMENT_SPEED, TANGLE, "Tangled Feet", -0.4, AttributeModifier.Operation.MULTIPLY_TOTAL, 100);
                AstaFx.damage(p, t, 2f * scale, "ill_fated_path");
            }
            case 5 -> {   // Fumble: its weapon slips; its blows land half as hard for a while
                play(sl, c, "omen_shard", 0.9f, 0.6f);
                splash(sl, c);
                if (t.getAttribute(Attributes.ATTACK_DAMAGE) != null)
                    TimedModifiers.apply(t, Attributes.ATTACK_DAMAGE, FUMBLE, "Fumble", -0.5, AttributeModifier.Operation.MULTIPLY_TOTAL, 120);
                AstaFx.damage(p, t, 2f * scale, "laden_token");
            }
            case 6 -> {   // Loose Strap: its armour slips
                play(sl, c, "omen_rebound", 0.8f, 1.4f);
                splash(sl, c);
                if (t.getAttribute(Attributes.ARMOR) != null)
                    TimedModifiers.apply(t, Attributes.ARMOR, STRAP, "Loose Strap", -0.4, AttributeModifier.Operation.MULTIPLY_TOTAL, 160);
                AstaFx.damage(p, t, 2f * scale, "laden_token");
            }
            case 7 -> {   // Wrong Turn: it turns its back and wanders off, forgetting you a moment
                play(sl, feet, "omen_still", 0.8f, 1.3f);
                splash(sl, feet.add(0, 0.05, 0));
                if (t instanceof Mob mob && !(t instanceof AddonBoss)) {
                    LivingEntity was = mob.getTarget();
                    mob.setTarget(null);
                    Vec3 to = feet.add(away(p, t).scale(6));
                    mob.getNavigation().moveTo(to.x, to.y, to.z, 1.0);
                    float yaw = mob.getYRot() + 180;
                    mob.setYRot(yaw); mob.setYHeadRot(yaw); mob.yBodyRot = yaw;
                    AstaFx.later(40, () -> { if (mob.isAlive() && mob.getTarget() == null && was != null && was.isAlive()) mob.setTarget(was); });
                } else Kit.push(t, away(p, t), 0.6, 0.1);
                AstaFx.damage(p, t, 3f * scale, "ill_fated_path");
            }
            case 8 -> {   // Rebound: its own blow comes back to it
                play(sl, c, "omen_rebound", 1f, 1f);
                splash(sl, c);
                AstaFx.damage(p, t, Math.max(4f, f.dealt) * scale, "stillness");
            }
            case 9 -> {   // Collision: knocked into its neighbour; both take it
                LivingEntity o = neighbour(p, t, 4, false);
                if (o == null) { strike(p, t, f, 3, scale); return; }
                Vec3 dir = hz(o.position().subtract(feet));
                if (dir.lengthSqr() > 1e-4) Kit.push(t, dir.normalize(), 0.8, 0.1);
                AstaFx.later(5, () -> {
                    if (!t.isAlive()) return;
                    Vec3 hit = mid(t).add(mid(o)).scale(0.5);
                    splash(sl, hit);
                    play(sl, hit, "omen_fall", 0.8f, 1.4f);
                    AstaFx.damage(p, t, 5f * scale, "domino");
                    if (o.isAlive()) AstaFx.damage(p, o, 4f * karma(o), "domino");
                });
            }
            case 10 -> {   // Ricochet: a shard bounces from it to up to three more pursuers
                play(sl, c, "omen_shard", 1f, 1.2f);
                AstaFx.damage(p, t, 4f * scale, "domino");
                LivingEntity from = t;
                java.util.Set<LivingEntity> hit = new java.util.HashSet<>(java.util.List.of(t));
                for (int i = 0; i < 3; i++) {
                    LivingEntity nx = null; double bd = 64;
                    for (LivingEntity o : sl.getEntitiesOfClass(LivingEntity.class, from.getBoundingBox().inflate(8), x -> !hit.contains(x) && x != p && x.isAlive() && pursues(x, p))) {
                        double d = o.distanceToSqr(from); if (d < bd) { bd = d; nx = o; }
                    }
                    if (nx == null) break;
                    hit.add(nx);
                    final LivingEntity a0 = from, b0 = nx; final int k = i;
                    AstaFx.later(3 + 3 * i, () -> {
                        if (!b0.isAlive()) return;
                        line(sl, mid(a0), mid(b0));
                        play(sl, mid(b0), "omen_shard", 0.8f, 1.3f + 0.1f * k);
                        AstaFx.damage(p, b0, 4f * karma(b0), "domino");
                    });
                    from = nx;
                }
            }
            case 11 -> {   // Needles: the rain turns to needles
                send(sl, fx(5, feet, 26));
                play(sl, c, "omen_needles", 1f, 1f);
                AstaFx.damage(p, t, 6f * scale, "needle_rain");
            }
            case 12 -> {   // Bad Landing: whatever it was doing in the air, it comes down wrong
                t.setDeltaMovement(t.getDeltaMovement().multiply(0.3, 0, 0.3).add(0, -1.4, 0)); t.hurtMarked = true;
                AstaFx.later(4, () -> {
                    if (!t.isAlive()) return;
                    splash(sl, t.position().add(0, 0.08, 0));
                    FxSend.shake(sl, t.position(), 0.2f, 4, 10);
                    play(sl, t.position(), "omen_fall", 1f, 0.8f);
                    AstaFx.damage(p, t, 7f * scale, "the_law_itself");
                });
            }
            case 13 -> {   // Last Straw: it was already on its last legs
                { var sh = fx(11, feet, 10); sh.u = c.add(0, 6, 0); send(sl, sh); }
                play(sl, c, "omen_ledger", 0.9f, 1.4f);
                AstaFx.damage(p, t, 10f * scale, "karmas_ledger");
            }
            case 14 -> {   // Tolling Bell: it rings once, and again
                splash(sl, c.add(0, t.getBbHeight() * 0.6, 0));
                play(sl, c, "omen_token", 1.2f, 0.6f);
                AstaFx.damage(p, t, 4f * scale, "the_law_itself");
                AstaFx.later(40, () -> {
                    if (!t.isAlive()) return;
                    splash(sl, mid(t));
                    play(sl, mid(t), "omen_token", 1.4f, 0.5f);
                    AstaFx.damage(p, t, 6f * scale, "the_law_itself");
                });
            }
            case 15 -> {   // Ill Wind: a gust throws it well away from you
                play(sl, c, "omen_afterimage", 1.2f, 1.5f);
                Vec3 d = away(p, t);
                line(sl, c.subtract(d.scale(2)), c.add(d.scale(3)));
                Kit.push(t, d, 2.0, 0.45);
                AstaFx.damage(p, t, 3f * scale, "afterimage");
            }
            case 16 -> {   // Toppling Row: it goes down, and the shock knocks the pursuers round it
                play(sl, feet, "omen_domino", 1.2f, 0.8f);
                splash(sl, feet.add(0, 0.06, 0));
                AstaFx.damage(p, t, 5f * scale, "domino");
                for (LivingEntity o : sl.getEntitiesOfClass(LivingEntity.class, t.getBoundingBox().inflate(3), x -> x != t && x != p && x.isAlive() && pursues(x, p)))
                    AstaFx.damage(p, o, 3f * karma(o), "domino");
            }
            case 17 -> {   // Jinx: its luck stays bad - the next calamity is already half on its way
                play(sl, c, "omen_token", 1f, 0.8f);
                splash(sl, c);
                AstaFx.damage(p, t, 3f * scale, "laden_token");
                AstaFx.later(1, () -> { Fate g = FATES.get(t); if (g != null) g.misfortune = Math.max(g.misfortune, 50f); });
            }
            case 18 -> {   // Cracked Luck: a slice of what it is, gone (bosses keep their caps)
                play(sl, c, "omen_ledger", 1f, 1.2f);
                splash(sl, c);
                line(sl, c.add(-0.6, 0.8, 0), c.add(0.6, -0.8, 0));
                AstaFx.damage(p, t, Math.min(t.getMaxHealth() * 0.1f, 40f), "karmas_ledger");
            }
            default -> {   // Snowball: one accident leads to another
                play(sl, c, "omen_domino", 1f, 1f);
                calamity(p, t, false, true);
                AstaFx.later(8, () -> { if (t.isAlive()) calamity(p, t, false, true); });
            }
        }
    }

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
            AstaFx.later(1, () -> { tokenCalamity(owner, attacker); });
        }
        if (!(victim instanceof ServerPlayer p) || !has(p)) return;
        Fate f = fate(attacker);
        f.by = p.getUUID();
        f.lastStruckYou = p.level().getGameTime();
        if (r(p, "K4") > 0 && f.misfortune >= FULL * 0.5f) e.setAmount(e.getAmount() * 0.9f);   // Curse-Bearer
        f.dealt += e.getAmount();
        f.misfortune += r(p, "K4") > 0 ? 30f : 15f;   // striking you is the surest pursuit there is
        // Stillness: standing still, the blow is turned back as calamity
        Mage m = mage(p);
        Vec3 v = p.getDeltaMovement();
        if (p.level().getGameTime() < m.stillUntil && v.x * v.x + v.z * v.z < (r(p, "s3") > 0 ? 0.02 : 0.004)) {
            float amt = e.getAmount();
            e.setCanceled(true);
            play(p.serverLevel(), p.position(), "omen_rebound", 1f, 0.8f);
            { var cr = on(8, p, 14); cr.d = attacker.position().subtract(p.position()).add(0, 0.01, 0); send(p.serverLevel(), cr); }
            f.dealt = Math.max(f.dealt, amt * 1.5f);
            if (r(p, "s4") > 0) p.heal(amt * 0.5f);   // Turned Fate
            if (r(p, "s5") > 0) m.stillUntil += 20;   // Glass Heart
            if (r(p, "s2") > 0) for (LivingEntity o : p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(4), x -> x != attacker && x != p && x.isAlive() && hostile(p, x))) {
                AstaFx.damage(p, o, 4f, "stillness");   // Broken Mirror
                line(p.serverLevel(), mid(p), mid(o));
            }
            AstaFx.later(1, () -> calamity(p, attacker, false));
        }
    }

    @SubscribeEvent public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) { MAGES.remove(e.getEntity().getUUID()); }

    // ================================================================== 1. Ill-Fated Path
    /** For 10 s you leave footprints; whatever follows in them pays. Three prints, and a calamity. */
    public static void illFatedPath(ServerPlayer p, float power) {
        Mage m = mage(p);
        int dur = r(p, "p1") > 0 ? 300 : 200;
        m.pathUntil = p.level().getGameTime() + dur;
        ModeHud.show(p, "ill_fated_path", dur);
        m.lastPrintAt = null;
        play(p.serverLevel(), p.position(), "omen_step", 1f, 0.8f);
        send(p.serverLevel(), fx(12, p.position(), 26));
    }

    static void leavePrint(ServerPlayer p, Mage m, long now) {
        if (!p.onGround()) return;
        Vec3 at = p.position();
        double gap = r(p, "p7") > 0 ? 0.45 : 0.8;
        if (m.lastPrintAt != null && m.lastPrintAt.distanceToSqr(at) < gap * gap) return;
        print(p, m, at, now, p.getYRot());
    }

    /** One footprint at 'at' (yaw in degrees): it waits for a walker. */
    static void print(ServerPlayer p, Mage m, Vec3 at, long now, float yawDeg) {
        m.lastPrintAt = at;
        int life = r(p, "p3") > 0 ? 240 : 160;
        m.prints.add(new Print(at, now + life, ++printIds));
        if (r(p, "p6") > 0) { var md = io.redspace.ironsspellbooks.api.magic.MagicData.getPlayerMagicData(p); md.setMana(md.getMana() + 2); }
        { double yaw = Math.toRadians(yawDeg); double side = (printIds % 2 == 0 ? 0.13 : -0.13); var pr = fx(0, at.add(Math.cos(yaw) * side, 0, Math.sin(yaw) * side), life); pr.a0 = (float) yaw; send(p.serverLevel(), pr); }
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
                f.misfortune += r(p, "p4") > 0 ? 50f : 34f;
                play(sl, pr.at, "omen_step", 0.8f, 1f + 0.1f * f.prints);
                splash(sl, pr.at);
                if (r(p, "p5") > 0) TimedModifiers.apply(t, Attributes.MOVEMENT_SPEED, TRIPWIRE, "Tripwire", -0.4, AttributeModifier.Operation.MULTIPLY_TOTAL, 30);
                if (++f.prints >= (r(p, "p2") > 0 ? 2 : 3)) { f.prints = 0; calamity(p, t, false); }
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
        if (aimed != null && r(p, "t1") > 0) {   // Cursed Coin: two more, to the enemies nearest it
            int k = 0;
            for (LivingEntity o : sl.getEntitiesOfClass(LivingEntity.class, aimed.getBoundingBox().inflate(6), x -> x != aimed && x != p && x.isAlive() && hostile(p, x))) {
                if (k++ >= 2) break;
                Fate g = fate(o); g.by = p.getUUID(); g.ladenUntil = sl.getGameTime() + 600; g.misfortune += 25f;
                var fl2 = fx(2, eye.add(0, -0.3, 0), 6); fl2.u = mid(o); send(sl, fl2);
            }
        }
        if (aimed != null) {
            Fate f = fate(aimed);
            f.by = p.getUUID();
            f.ladenUntil = sl.getGameTime() + 600;
            f.misfortune += 25f;
            var fl = fx(2, eye.add(0, -0.3, 0), 6); fl.u = mid(aimed); send(sl, fl);
            final Vec3 hitAt = aimed.position();
            AstaFx.later(6, () -> send(sl, fx(4, hitAt, 14)));
            return;
        }
        Vec3 land = BossKit.ground(sl, clip(sl, p, eye, eye.add(dir.scale(10))).add(0, 0.5, 0));
        dropToken(p, land, eye);
        if (r(p, "t1") > 0) {   // Cursed Coin: one to each side
            Vec3 side = new Vec3(-dir.z, 0, dir.x).normalize().scale(2.5);
            dropToken(p, BossKit.ground(sl, land.add(side).add(0, 1, 0)), eye);
            dropToken(p, BossKit.ground(sl, land.subtract(side).add(0, 1, 0)), eye);
        }
    }

    /** A token flicked from 'from' to lie at 'land', waiting for a toucher. */
    static void dropToken(ServerPlayer p, Vec3 land, Vec3 from) {
        ServerLevel sl = p.serverLevel();
        int wait = r(p, "t3") > 0 ? 1200 : 600;
        mage(p).tokens.add(new Token(land, sl.getGameTime() + wait));
        var fl = fx(2, from.add(0, -0.3, 0), 8); fl.u = land.add(0, 0.35, 0); send(sl, fl);
        AstaFx.later(8, () -> send(sl, fx(3, land, wait - 8)));
    }

    /** A calamity a token called: Heavy Coin, Spreading Debt, Lucky Coin. */
    static void tokenCalamity(ServerPlayer p, LivingEntity t) {
        if (!t.isAlive()) return;
        if (r(p, "t2") > 0) nextScale = 1.3f;
        calamity(p, t, false);
        if (r(p, "t6") > 0) p.heal(3f);
        if (r(p, "t5") > 0) {
            ServerLevel sl = p.serverLevel();
            LivingEntity n = null; double bd = 36;
            for (LivingEntity o : sl.getEntitiesOfClass(LivingEntity.class, t.getBoundingBox().inflate(6), x -> x != t && x != p && x.isAlive() && hostile(p, x))) { double d = o.distanceToSqr(t); if (d < bd) { bd = d; n = o; } }
            if (n != null) { Fate g = fate(n); g.by = p.getUUID(); g.ladenUntil = sl.getGameTime() + 600; var fl = fx(2, mid(t), 6); fl.u = mid(n); send(sl, fl); }
        }
    }

    static void tokens(ServerPlayer p, Mage m, long now) {
        if (m.tokens.isEmpty()) return;
        ServerLevel sl = p.serverLevel();
        for (Iterator<Token> it = m.tokens.iterator(); it.hasNext(); ) {
            Token tk = it.next();
            if (now >= tk.until) { it.remove(); continue; }
            double reach = r(p, "t4") > 0 ? 1.8 : 0.9;
            for (LivingEntity t : enemies(sl, p, new AABB(tk.at, tk.at).inflate(reach, 1.5, reach))) {
                if (t instanceof OmenAfterimage) continue;
                it.remove();
                play(sl, tk.at, "omen_token", 1f, 0.7f);
                send(sl, fx(4, tk.at, 14));
                tokenCalamity(p, t);
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
        if (r(p, "a1") > 0) {   // Second Shadow: another, walking off the other way round
            OmenAfterimage b2 = AddonEntities.OMEN_AFTERIMAGE.get().create(sl);
            if (b2 != null) {
                Vec3 d2 = rotY(dir, 1.2);
                Vec3 at2 = p.position().add(d2.scale(1.5));
                b2.moveTo(at2.x, p.getY(), at2.z, p.getYRot() + 70, 0);
                b2.owner = p.getUUID(); b2.heading = d2;
                sl.addFreshEntity(b2);
                send(sl, fx(12, at2, 24));
            }
        }
        for (Mob mob : sl.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(16), x -> x.isAlive() && (x.getTarget() == p || x instanceof net.minecraft.world.entity.monster.Enemy) && !x.isAlliedTo(p))) mob.setTarget(a);
        play(sl, at, "omen_afterimage", 1.2f, 1f);
        send(sl, fx(12, at, 24));
    }

    /** Shadow Burst: an Afterimage of p's fades, and the enemies near it take its dread. */
    public static void afterimageFades(ServerPlayer p, Vec3 at) {
        if (r(p, "a6") <= 0) return;
        ServerLevel sl = p.serverLevel();
        send(sl, fx(12, at, 24));
        play(sl, at, "omen_afterimage", 1f, 0.6f);
        for (LivingEntity t : sl.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(4), x -> x != p && x.isAlive() && hostile(p, x))) {
            Fate f = fate(t); f.by = p.getUUID(); f.misfortune += 40f;
            if (f.misfortune >= FULL) calamity(p, t, false);
        }
    }

    // ================================================================== 4. Needle Rain
    /** For 5 s ill luck falls as needles on everyone pursuing you within 10 blocks - harder in real rain. */
    public static void needleRain(ServerPlayer p, float power) {
        int dur = r(p, "n1") > 0 ? 160 : 100;
        mage(p).rainUntil = p.level().getGameTime() + dur;
        ModeHud.show(p, "needle_rain", dur);
        play(p.serverLevel(), p.position(), "omen_needles", 1.3f, 0.9f);
        send(p.serverLevel(), fx(12, p.position(), 30));
    }

    static void needles(ServerPlayer p, Mage m, long now) {
        ServerLevel sl = p.serverLevel();
        boolean rain = sl.isRainingAt(p.blockPosition().above());
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (LivingEntity t : sl.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(r(p, "n3") > 0 ? 14 : 10), x -> x != p && x.isAlive() && pursues(x, p))) {
            Vec3 c = mid(t);
            send(sl, fx(5, t.position(), 26));
            AstaFx.damage(p, t, 3f * (rain ? 1.6f : 1f), "needle_rain");
            Fate f = fate(t);
            f.misfortune += r(p, "n2") > 0 ? 15f : 10f;
            if (f.misfortune >= FULL) calamity(p, t, false);
        }
    }

    // ================================================================== 5-8
    /** Domino: for 15 s every calamity leaps to the nearest other pursuer. */
    public static void domino(ServerPlayer p, float power) {
        int dur = r(p, "n4") > 0 ? 500 : 300;
        mage(p).dominoUntil = p.level().getGameTime() + dur;
        ModeHud.show(p, "domino", dur);
        play(p.serverLevel(), p.position(), "omen_domino", 1.2f, 1f);
        send(p.serverLevel(), fx(12, p.position(), 24));
    }

    /** Stillness: for 4 s, while you stand still, blows aimed at you rebound on their senders as calamity. */
    public static void stillness(ServerPlayer p, float power) {
        int dur = r(p, "s1") > 0 ? 120 : 80;
        mage(p).stillUntil = p.level().getGameTime() + dur;
        ModeHud.show(p, "stillness", dur);
        play(p.serverLevel(), p.position(), "omen_still", 1.2f, 1f);
        send(p.serverLevel(), on(7, p, dur));
    }

    /** Karma's Ledger: the one you look at pays for everything at once - its Misfortune and what it has done to you. */
    public static void karmasLedger(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        LivingEntity t = aimed(p, 24, 8);
        play(sl, p.position(), "omen_ledger", 1.3f, 1f);
        if (t == null) return;
        Fate f = fate(t);
        float dmg = (8f + f.misfortune * (r(p, "l1") > 0 ? 0.25f : 0.15f) + f.dealt) * karma(t);
        if (r(p, "K5") > 0 && (t instanceof AddonBoss || TimeStop.isBoss(t) || EliteMobs.elite(t))) dmg *= 1.5f;   // Judge
        if (r(p, "l4") > 0) f.badCreditUntil = sl.getGameTime() + 200;   // Bad Credit
        if (r(p, "l3") > 0) {   // Audit: up to two more marked enemies near it, at half
            int k = 0;
            for (LivingEntity o : sl.getEntitiesOfClass(LivingEntity.class, t.getBoundingBox().inflate(8), x -> x != t && x != p && x.isAlive() && hostile(p, x))) {
                Fate g = FATES.get(o);
                if (g == null || g.misfortune <= 0 || k >= 2) continue;
                k++;
                float half = (8f + g.misfortune * 0.15f + g.dealt) * karma(o) * 0.5f;
                g.misfortune = 0; g.dealt = 0;
                send(sl, fx(9, o.position(), 30));
                final LivingEntity oo = o;
                AstaFx.later(13, () -> { if (oo.isAlive() && p.isAlive()) AstaFx.damage(p, oo, half, "karmas_ledger"); });
            }
        }
        f.misfortune = 0;
        f.dealt = 0;
        Vec3 c = mid(t);
        send(sl, fx(9, t.position(), 30));   // the ledger opens over it, the debt writes itself in, and the page slams down
        final LivingEntity tt = t;
        final float dmg0 = dmg;
        AstaFx.later(13, () -> {
            if (!tt.isAlive() || !p.isAlive()) return;
            FxSend.shake(sl, tt.position(), 0.4f, 6, 16);
            play(sl, tt.position(), "omen_fall", 1.2f, 0.6f);
            float over = dmg0 - tt.getHealth();
            AstaFx.damage(p, tt, dmg0, "karmas_ledger");
            if (r(p, "l2") > 0 && over > 0 && !tt.isAlive()) {   // Unpaid Debt
                LivingEntity n = null; double bd = 64;
                for (LivingEntity o : sl.getEntitiesOfClass(LivingEntity.class, tt.getBoundingBox().inflate(8), x -> x != tt && x != p && x.isAlive() && hostile(p, x))) { double d = o.distanceToSqr(tt); if (d < bd) { bd = d; n = o; } }
                if (n != null) { line(sl, mid(tt), mid(n)); AstaFx.damage(p, n, over, "karmas_ledger"); }
            }
        });
    }

    /** The Law Itself: for 20 s calamity hunts every pursuer within 24 blocks, each blow worse than the last. */
    public static void theLawItself(ServerPlayer p, float power) {
        Mage m = mage(p);
        int dur = r(p, "l8") > 0 ? 500 : 400;
        m.lawUntil = p.level().getGameTime() + dur;
        ModeHud.show(p, "the_law_itself", dur);
        m.lawCount = 0;
        m.lawEcho = r(p, "l7") <= 0;
        ServerLevel sl = p.serverLevel();
        play(sl, p.position(), "omen_law", 1.6f, 1f);
        send(sl, on(10, p, dur));   // the sigil over you, for as long as the Law holds
    }

    // ================================================================== the G wheel (the Ledger of Ill Luck)
    static boolean wheel(ServerPlayer p, int slot) {
        ServerLevel sl = p.serverLevel();
        Mage m = mage(p);
        long now = sl.getGameTime();
        switch (slot) {
            case 0 -> {   // Read the Signs
                int n = 0;
                for (LivingEntity t : sl.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(16), x -> x != p && x.isAlive() && hostile(p, x))) {
                    Fate f = fate(t); f.by = p.getUUID(); f.misfortune += 15f; n++;
                    splash(sl, t.position());
                    if (f.misfortune >= FULL) calamity(p, t, false);
                }
                play(sl, p.position(), "omen_still", 1f, 1.3f);
                if (n == 0) { p.displayClientMessage(net.minecraft.network.chat.Component.literal("No enemy near to read").withStyle(net.minecraft.ChatFormatting.GRAY), true); return false; }
            }
            case 1 -> {   // Cross My Path
                Vec3 d = hz(p.getLookAngle()).normalize();
                for (int i = 1; i <= 5; i++) print(p, m, BossKit.ground(sl, p.position().add(d.scale(i * 0.9)).add(0, 1, 0)), now, p.getYRot());
                m.lastPrintAt = null;
                play(sl, p.position(), "omen_step", 1f, 0.7f);
            }
            case 2 -> { dropToken(p, p.position(), p.getEyePosition()); play(sl, p.position(), "omen_token", 1f, 1f); }   // Bad Penny
            default -> {   // Final Notice
                int n = 0;
                for (LivingEntity t : sl.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(16), x -> x != p && x.isAlive() && hostile(p, x))) {
                    Fate f = FATES.get(t);
                    if (f == null || f.misfortune < FULL * 0.5f) continue;
                    final LivingEntity tt = t;
                    AstaFx.later(2 * n++, () -> { if (tt.isAlive()) calamity(p, tt, false); });
                }
                if (n == 0) { p.displayClientMessage(net.minecraft.network.chat.Component.literal("No bar half full yet").withStyle(net.minecraft.ChatFormatting.GRAY), true); return false; }
                play(sl, p.position(), "omen_law", 1.2f, 1.2f);
            }
        }
        return true;
    }

    /** For the wheel: enemies marked, the fullest bar (%), calamities this session, the Law's seconds left. */
    static int[] extra(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        int n = 0; float best = 0;
        for (LivingEntity t : sl.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(32), x -> x != p && x.isAlive())) {
            Fate f = FATES.get(t);
            if (f == null || f.misfortune <= 0 || !p.getUUID().equals(f.by)) continue;
            n++; best = Math.max(best, f.misfortune);
        }
        Mage m = mage(p);
        return new int[]{n, Math.round(Math.min(100f, best)), (int) Math.max(0, m.lawUntil - sl.getGameTime())};
    }

    // ================================================================== the UI showcase (client/UiShots fxs_omen_*)
    public static void showcase(ServerPlayer p, LivingEntity h, String what) {
        ServerLevel sl = p.serverLevel();
        Vec3 look = hz(p.getLookAngle()).normalize();
        switch (what) {
            case "path" -> {
                illFatedPath(p, 0);
                double yaw = Math.toRadians(p.getYRot());
                for (int i = 0; i < 7; i++) {
                    double side = i % 2 == 0 ? 0.13 : -0.13;
                    var pr = fx(0, p.position().add(look.scale(1.2 + i * 0.8)).add(Math.cos(yaw) * side, 0, Math.sin(yaw) * side), 160); pr.a0 = (float) yaw; send(sl, pr);
                }
                splash(sl, p.position().add(look.scale(1.2 + 5 * 0.8)));
            }
            case "token" -> { ladenToken(p, 0); send(sl, fx(3, p.position().add(look.scale(3)).add(look.cross(new Vec3(0, 1, 0)).scale(1.5)), 300)); }
            case "needles" -> { send(sl, fx(5, h.position(), 26)); send(sl, fx(5, h.position().add(look.cross(new Vec3(0, 1, 0)).scale(2.5)), 26)); }
            case "domino" -> { Vec3 b = h.position().add(look.cross(new Vec3(0, 1, 0)).scale(4)).add(0, 1, 0); var q = fx(6, mid(h), 24); q.u = b; send(sl, q); }
            case "still" -> { stillness(p, 0); var cr = on(8, p, 20); cr.d = look; send(sl, cr); }
            case "ledger" -> karmasLedger(p, 0);
            case "law" -> { theLawItself(p, 0); var sh = fx(11, h.position(), 14); sh.u = p.position().add(0, 6, 0); send(sl, sh); }
            case "clear" -> { for (int k : new int[]{7, 10}) { var q = fx(98, p.position(), 1); q.v = new Vec3(p.getId(), 0, 0); q.a0 = k; send(sl, q); } }
            default -> {}
        }
    }

    // ================================================================== test hooks
    public static float testMisfortune(LivingEntity e) { Fate f = FATES.get(e); return f == null ? 0 : f.misfortune; }
    public static void testSet(LivingEntity e, float m, float dealt) { Fate f = fate(e); f.misfortune = m; f.dealt = dealt; }
    public static boolean testLaden(LivingEntity e) { Fate f = FATES.get(e); return f != null && f.ladenUntil > e.level().getGameTime(); }
    public static int testCalamities(LivingEntity e) { Fate f = FATES.get(e); return f == null ? 0 : f.calamities; }
    public static void testStruck(LivingEntity e, Player p) { Fate f = fate(e); f.by = p.getUUID(); f.lastStruckYou = e.level().getGameTime(); }
    public static int testPrints(Player p) { return mage(p).prints.size(); }
    public static int testTokens(Player p) { return mage(p).tokens.size(); }
    public static void testReset(Player p) { MAGES.remove(p.getUUID()); }
}
