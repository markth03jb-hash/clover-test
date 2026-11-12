package com.ironspellgrimoire.server;

import static com.ironspellgrimoire.server.FxUtil.*;

import com.ironspellgrimoire.network.FxPacket;
import com.ironspellgrimoire.registry.AddonSounds;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The Blood Grimoire (an original grimoire): power paid for in your own blood. Every spell costs a little of your
 * health as well as mana (it never takes your last heart), and the more hurt you are, the harder it hits.
 *
 *  1 Crimson Edge      - a wide arc of blood cut in front of you
 *  2 Hemorrhage        - marks the foe you aim at: it bleeds, and takes 25% more from your spells for 8 s
 *  3 Sanguine Volley   - a fan of seven blood needles
 *  4 Transfusion       - a red thread to the foe you aim at, draining it into you for two seconds
 *  5 Blood Pact        - pay a fifth of your health: your next Blood spell is 60% stronger
 *  6 Red Tide          - a wall of blood rolls forward and throws back everything in its way
 *  7 Thorned Veins     - thorns of blood burst out of the ground along a line, flinging foes up
 *  8 Crimson Bulwark   - a shell of hardened blood: absorbs damage, and breaks outward when it's gone
 *  9 Exsanguinate      - blood is torn out of every foe around you and pours into you; it drinks their bleed stacks
 * 10 Scarlet Moon (mode) - needs a full Blood Gauge: a minute (more with overflow) in which every spell turns to ice
 *                       (server/IceFx) - no health cost, much stronger; cast it again for Moonfall.
 *
 * Passive "Blood Price": up to +80% power as your health runs low.
 * Bleed: every Blood hit leaves a stack of bleeding on the foe (up to 5, 8 s) - Exsanguinate drinks them all.
 * Pools: blood spilled by your hits gathers in pools; walk through one to drink it (a little health, a little gauge).
 * Overflow: blood spent with the gauge full builds a darker second layer (up to +50%) - the Moon lasts longer for it.
 * Paths (server/BloodPaths): at mastery 5 every spell takes one of two shapes.
 * Mana Tree (server/SkillTree, book 20): Thirst (gauge fills faster), Hard Freeze, Pale Ward; crown Eternal Night.
 * Everything is drawn as liquid blood by client/BloodFxClient (no shared effect shapes).
 */
@Mod.EventBusSubscriber(modid = "iron_spell_grimoire")
public final class BloodFx {
    private BloodFx() {}

    public static final String MODE = "scarlet_moon";
    private static final double TAU = Math.PI * 2;
    /** Hemorrhage marks: target -> until (game time). */
    private static final Map<UUID, Long> MARKED = new HashMap<>();
    /** Blood Pact: how many Blood spells it still empowers, and by how much. */
    private static final Map<UUID, float[]> PACT = new HashMap<>();
    /** Bleed stacks: target -> {stacks, until, fx id}. */
    private static final Map<UUID, long[]> BLEED = new HashMap<>();
    /** Thorn Shell (Crimson Bulwark, path B): who has it, until when. */
    private static final Map<UUID, Long> THORN_SHELL = new HashMap<>();

    // ================================================================== the price, the power
    static boolean moon(ServerPlayer p) { return Moves.inMode(p, MODE); }

    static ItemStack book(ServerPlayer p) { return io.redspace.ironsspellbooks.api.util.Utils.getPlayerSpellbookStack(p); }

    /** A Blood node of the Mana Tree (SkillTree book 20): its value, 0 if not learned. */
    static float tree(ServerPlayer p, String effect) {
        ItemStack b = book(p);
        return b != null && SkillTree.bookIndex(b) == 20 ? SkillTree.sum(b, effect) : 0f;
    }

    static boolean crown(ServerPlayer p) {
        ItemStack b = book(p);
        return b != null && SkillTree.bookIndex(b) == 20 && SkillTree.crown(b);
    }

    static boolean path(ServerPlayer p, String spell, int which) { return BloodPaths.is(p, spell, which); }

    /** Pays the spell's price in health (a share of max health). Never kills. Nothing under the Moon. */
    private static void price(ServerPlayer p, float share) {
        if (moon(p)) return;
        float cost = p.getMaxHealth() * share;
        addGauge(p, 5f + cost * 3f);
        handDrip(p);
        if (p.isCreative()) return;
        p.setHealth(Math.max(1f, p.getHealth() - cost));
    }

    /** The spell's power: higher the lower your health, higher still with a Pact. */
    private static float pw(ServerPlayer p, float power) {
        float missing = 1f - p.getHealth() / Math.max(1f, p.getMaxHealth());
        float k = 1f + 0.8f * missing;
        float[] pact = PACT.get(p.getUUID());
        if (pact != null) {
            k *= 1f + pact[1];
            if (--pact[0] <= 0) PACT.remove(p.getUUID());
            spray(p.serverLevel(), p.position().add(0, 1.1, 0), new Vec3(0, 0.12, 0), 0.1f, 10, 0.06f);
        }
        return power * k;
    }

    private static void hit(ServerPlayer p, LivingEntity t, float amt, String id) {
        if (MARKED.getOrDefault(t.getUUID(), 0L) > p.serverLevel().getGameTime()) amt *= 1.25f;
        float before = t.getHealth();
        AstaFx.damage(p, t, amt, id);
        FxPacket b = fx(13, mid(t), 6);   // the wound opens: blood thrown out the way the blow went, mist, a splash below
        b.d = mid(t).subtract(p.position().add(0, 1, 0)); b.a0 = Math.max(0.45f, Math.min(1.4f, amt / 8f));
        send(p.serverLevel(), b);
        float dealt = Math.max(0f, before - t.getHealth());
        addGauge(p, dealt * 0.8f);   // blood spilled fills the gauge
        if (t.isAlive()) addBleed(p, t);
        if (dealt > 0.5f) pool(p.serverLevel(), t.position(), 0.55f + Math.min(0.5f, dealt * 0.05f));
        if (ThreadLocalRandom.current().nextFloat() < 0.5f) play(p.serverLevel(), mid(t), "blood_splash", 0.8f, 0.9f + ThreadLocalRandom.current().nextFloat() * 0.3f);
    }

    static void play(ServerLevel sl, Vec3 at, String kind, float vol, float pitch) {
        SoundEvent s = AddonSounds.get(kind);
        if (s != null) sl.playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, vol, pitch);
    }

    // ================================================================== bleed stacks
    static final int BLEED_MAX = 5, BLEED_T = 160;

    static int bleed(LivingEntity t) {
        long[] b = BLEED.get(t.getUUID());
        return b == null || b[1] < t.level().getGameTime() ? 0 : (int) b[0];
    }

    private static void addBleed(ServerPlayer p, LivingEntity t) {
        ServerLevel sl = p.serverLevel();
        long now = sl.getGameTime();
        long[] b = BLEED.get(t.getUUID());
        if (b == null || b[1] < now) b = new long[]{0, 0, 0};
        int stacks = (int) Math.min(BLEED_MAX, b[0] + 1);
        if (b[2] != 0) end(sl, t.position(), (int) b[2]);   // the old drips give way to heavier ones
        FxPacket f = fx(11, t.position(), BLEED_T);
        f.v = new Vec3(t.getId(), 0, 0); f.a0 = stacks;
        int id = send(sl, f);
        BLEED.put(t.getUUID(), new long[]{stacks, now + BLEED_T, id});
    }

    /** Drinks a foe's bleed stacks (Exsanguinate): how many there were. */
    private static int drink(ServerLevel sl, LivingEntity t) {
        long[] b = BLEED.remove(t.getUUID());
        if (b == null || b[1] < sl.getGameTime()) return 0;
        if (b[2] != 0) end(sl, t.position(), (int) b[2]);
        return (int) b[0];
    }

    // ================================================================== pools you can drink
    private record Pool(ServerLevel level, Vec3 at, float r, long until, int id) {}
    private static final List<Pool> POOLS = new ArrayList<>();

    private static void pool(ServerLevel sl, Vec3 near, float r) {
        Vec3 g = ZenonAI.ground(sl, near);
        for (Pool q : POOLS) if (q.level() == sl && q.at().distanceToSqr(g) < 0.8) return;   // one pool per spot
        if (POOLS.size() > 60) POOLS.remove(0);
        FxPacket f = fx(5, g, 600);
        f.r = r;
        POOLS.add(new Pool(sl, g, r, sl.getGameTime() + 600, send(sl, f)));
    }

    /** Walking through a pool of spilled blood drinks it: half a heart and some gauge. */
    private static void drinkPools(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        long now = sl.getGameTime();
        Iterator<Pool> it = POOLS.iterator();
        while (it.hasNext()) {
            Pool q = it.next();
            if (q.until() < now) { it.remove(); continue; }
            if (q.level() != sl || q.at().distanceToSqr(p.position()) > Math.pow(q.r() + 0.5, 2) || Math.abs(q.at().y - p.getY()) > 1.2) continue;
            it.remove();
            end(sl, q.at(), q.id());   // it drains away into you
            stream(sl, q.at().add(0, 0.05, 0), p.position().add(0, 1, 0), 0, p.getId(), 0.05f, 0.6f, 5, 9);
            if (!moon(p)) p.heal(1f);
            addGauge(p, 4f);
            sound(sl, q.at(), SoundEvents.HONEY_DRINK, 0.6f, 1.3f);
        }
    }

    // ================================================================== the look (BloodFxClient, FxPacket shape 80)
    private static FxPacket fx(int kind, Vec3 c, int life) {
        FxPacket p = new FxPacket();
        p.shape = 80; p.id = FxSend.nextId(); p.h = kind; p.c = c; p.life = life;
        return p;
    }

    private static int send(ServerLevel sl, FxPacket p) {
        Vec3 at = p.c;
        com.ironspellgrimoire.network.AddonNetwork.send(net.minecraftforge.network.PacketDistributor.NEAR.with(
                () -> new net.minecraftforge.network.PacketDistributor.TargetPoint(at.x, at.y, at.z, 96, sl.dimension())), p);
        return p.id;
    }

    /** Blood thrown out from a point: drops that fall and splash into pools where they land. */
    private static void spray(ServerLevel sl, Vec3 c, Vec3 vel, float spread, int n, float size) {
        FxPacket p = fx(1, c, 1);
        p.d = vel; p.r = spread; p.sweep = n; p.a0 = size;
        send(sl, p);
    }

    /** The price paid: blood runs off the casting hand (BloodFxClient kind 15 - in first person, off your own hand). */
    private static void handDrip(ServerPlayer p) {
        FxPacket f = fx(15, p.position(), 12);
        f.v = new Vec3(p.getId(), 0, 0);
        com.ironspellgrimoire.network.AddonNetwork.send(net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), f);
    }

    /** A stream of blood flowing from a to b (each following an entity if its id isn't 0). */
    private static int stream(ServerLevel sl, Vec3 a, Vec3 b, int fromId, int toId, float thick, float arc, int flow, int life) {
        FxPacket p = fx(0, a, life);
        p.u = b; p.v = new Vec3(toId, fromId, 0); p.r = thick; p.r2 = arc; p.speed = flow;
        return send(sl, p);
    }

    private static int needle(ServerLevel sl, Vec3 a, Vec3 b, float len, int flight) {
        FxPacket p = fx(7, a, flight + 2);
        p.u = b; p.r = len; p.speed = flight;
        return send(sl, p);
    }

    private static void end(ServerLevel sl, Vec3 near, int id) {
        FxPacket p = fx(99, near, 1);
        p.id = id;
        send(sl, p);
    }

    private static Vec3 flatLook(ServerPlayer p) {
        Vec3 l = flat(p.getLookAngle());
        return l.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : l.normalize();
    }

    // ================================================================== 1. Crimson Edge
    public static void crimsonEdge(ServerPlayer p, float power) {
        if (moon(p)) { IceFx.frostCrescent(p, power); return; }   // under the Scarlet Moon it's ice
        ServerLevel sl = p.serverLevel();
        price(p, 0.02f);
        float pow = pw(p, power);
        Vec3 look = flatLook(p);
        boolean longEdge = path(p, "crimson_edge", 2), twin = path(p, "crimson_edge", 1);
        float reach = longEdge ? 1.5f : 1f;
        edge(p, look, new Vec3(0, 1, 0).add(rightOf(look).scale(0.5)).normalize(), pow, reach);
        if (twin) AstaFx.later(4, () -> { if (alive(p)) edge(p, flatLook(p), new Vec3(0, 1, 0).add(rightOf(flatLook(p)).scale(-0.5)).normalize(), pow * 0.6f, reach); });
        sound(sl, p.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, 0.7f);
        sound(sl, p.position(), SoundEvents.HONEY_BLOCK_BREAK, 0.8f, 0.6f);
    }

    private static void edge(ServerPlayer p, Vec3 look, Vec3 up, float pow, float reach) {
        ServerLevel sl = p.serverLevel();
        Vec3 c = p.position().add(0, 1.1, 0).add(look.scale(0.4));
        FxPacket b = fx(2, c, 16);   // a crescent of liquid blood swung round, flinging drops, then falling apart
        b.d = look; b.u = up; b.r = 3.4f * reach; b.speed = 5;
        send(sl, b);
        for (LivingEntity e : enemies(sl, p, new AABB(c, c).inflate(3.6 * reach))) {
            Vec3 to = flat(mid(e).subtract(p.position()));
            if (to.lengthSqr() < 1e-4 || to.normalize().dot(look) < 0.2) continue;
            hit(p, e, pow, "crimson_edge");
        }
    }

    // ================================================================== 2. Hemorrhage
    public static void hemorrhage(ServerPlayer p, float power) {
        if (moon(p)) { IceFx.rimeSpear(p, power); return; }
        ServerLevel sl = p.serverLevel();
        List<LivingEntity> targets = new ArrayList<>();
        if (path(p, "hemorrhage", 2)) {   // Triple Needle: the three nearest foes in front of you
            Vec3 look = p.getLookAngle();
            List<LivingEntity> all = enemies(sl, p, p.getBoundingBox().inflate(20));
            all.removeIf(e -> mid(e).subtract(p.getEyePosition()).normalize().dot(look) < 0.75);
            all.sort((a, b) -> Double.compare(a.distanceToSqr(p), b.distanceToSqr(p)));
            targets.addAll(all.subList(0, Math.min(3, all.size())));
        } else {
            LivingEntity t = aimed(p, 24, 14);
            if (t != null) targets.add(t);
        }
        if (targets.isEmpty()) { p.displayClientMessage(Component.literal("No one to mark.").withStyle(ChatFormatting.GRAY), true); return; }
        price(p, 0.03f);
        float pow = pw(p, power);
        for (LivingEntity t : targets) mark(p, t, pow, path(p, "hemorrhage", 1));
        sound(sl, mid(targets.get(0)), SoundEvents.HONEY_DRINK, 1.2f, 0.5f);
    }

    private static void mark(ServerPlayer p, LivingEntity t, float pow, boolean spreads) {
        ServerLevel sl = p.serverLevel();
        MARKED.put(t.getUUID(), sl.getGameTime() + 160);
        Vec3 from = p.getEyePosition().subtract(0, 0.2, 0);
        int flight = Math.max(2, (int) (from.distanceTo(mid(t)) / 3.0));
        needle(sl, from, mid(t), 0.9f, flight);   // one needle of hardened blood, and the wound it leaves keeps bleeding
        AstaFx.later(flight, () -> {
            if (!t.isAlive()) return;
            spray(sl, mid(t), p.getLookAngle().scale(0.1), 0.06f, 10, 0.06f);
            if (alive(p)) addBleed(p, t);
            if (spreads) {   // Contagion: the bleeding spreads to two foes near the mark
                int spread = 0;
                for (LivingEntity o : enemies(sl, p, t.getBoundingBox().inflate(5))) {
                    if (o == t) continue;
                    stream(sl, mid(t), mid(o), t.getId(), o.getId(), 0.04f, 0.6f, 6, 14);
                    MARKED.put(o.getUUID(), sl.getGameTime() + 120);
                    if (alive(p)) addBleed(p, o);
                    if (++spread >= 2) break;
                }
            }
        });
        for (int k = 20; k <= 160; k += 20) {   // it bleeds: a little every second
            AstaFx.later(k, () -> { if (t.isAlive() && alive(p)) hit(p, t, pow * 0.22f, "hemorrhage"); });
        }
    }

    // ================================================================== 3. Sanguine Volley
    public static void sanguineVolley(ServerPlayer p, float power) {
        if (moon(p)) { IceFx.glacialVolley(p, power); return; }
        ServerLevel sl = p.serverLevel();
        price(p, 0.03f);
        float pow = pw(p, power);
        Vec3 eye = p.getEyePosition().subtract(0, 0.2, 0), look = p.getLookAngle();
        int n = path(p, "sanguine_volley", 1) ? 11 : 7;
        boolean pierce = path(p, "sanguine_volley", 2);
        for (int i = 0; i < n; i++) {
            double a = (i - (n - 1) / 2.0) * (n > 7 ? 0.1 : 0.12);
            Vec3 d = rotY(look, a).normalize();
            final boolean[] spent = {false};
            final int[] id = {0};
            final int delay = i / 3;
            java.util.Set<UUID> struck = new java.util.HashSet<>();
            AstaFx.later(delay, () -> { if (alive(p)) id[0] = needle(sl, eye, eye.add(d.scale(2.2 * 9)), 0.7f, 9); });
            for (int k = 0; k < 9; k++) {
                final int kk = k;
                AstaFx.later(k + delay, () -> {
                    if (spent[0] || !alive(p)) return;
                    Vec3 a0 = eye.add(d.scale(2.2 * kk)), a1 = eye.add(d.scale(2.2 * (kk + 1)));
                    for (LivingEntity e : Kit.along(sl, p, a0, a1, 0.6)) {
                        if (!struck.add(e.getUUID())) continue;
                        hit(p, e, pow * 0.35f, "sanguine_volley");
                        if (pierce) continue;
                        if (id[0] != 0) end(sl, mid(e), id[0]);
                        spent[0] = true;
                        break;
                    }
                });
            }
        }
        sound(sl, eye, SoundEvents.TRIDENT_THROW, 1.0f, 1.6f);
    }

    // ================================================================== 4. Transfusion
    public static void transfusion(ServerPlayer p, float power) {
        if (moon(p)) { IceFx.frostbind(p, power); return; }
        ServerLevel sl = p.serverLevel();
        LivingEntity t = aimed(p, 16, 16);
        if (t == null) { p.displayClientMessage(Component.literal("No one to draw from.").withStyle(ChatFormatting.GRAY), true); return; }
        price(p, 0.02f);
        float pow = pw(p, power);
        float share = path(p, "transfusion", 1) ? 1.0f : 0.6f;
        sound(sl, p.position(), SoundEvents.HONEY_DRINK, 1.2f, 0.4f);
        drain(p, t, pow, share);
        if (path(p, "transfusion", 2)) {   // Chain: a second foe near the first
            for (LivingEntity o : enemies(sl, p, t.getBoundingBox().inflate(6))) if (o != t) { drain(p, o, pow * 0.7f, share); break; }
        }
    }

    private static void drain(ServerPlayer p, LivingEntity t, float pow, float share) {
        ServerLevel sl = p.serverLevel();
        FxPacket th = fx(8, p.position(), 42);   // a thread of blood from it to you, beads of its blood drawn along it
        th.v = new Vec3(p.getId(), t.getId(), 0); th.u = mid(t);
        send(sl, th);
        for (int k = 0; k < 40; k += 10) {
            AstaFx.later(k, () -> {
                if (!alive(p) || !t.isAlive() || t.distanceTo(p) > 20) return;
                float before = t.getHealth();
                hit(p, t, pow * 0.25f, "transfusion");
                p.heal(Math.max(0f, before - t.getHealth()) * share);
            });
        }
    }

    // ================================================================== 5. Blood Pact
    public static void bloodPact(ServerPlayer p, float power) {
        if (moon(p)) { IceFx.wintersPact(p, power); return; }
        ServerLevel sl = p.serverLevel();
        boolean greater = path(p, "blood_pact", 1), twin = path(p, "blood_pact", 2);
        price(p, greater ? 0.3f : 0.2f);
        PACT.put(p.getUUID(), new float[]{twin ? 2 : 1, greater ? 1.0f : twin ? 0.35f : 0.6f});
        Vec3 c = p.position();
        Vec3 look = flatLook(p);
        Vec3 bookAt = c.add(0, 1.0, 0).add(look.scale(0.6)).add(rightOf(look).scale(0.35));
        for (int i = 0; i < 4; i++) {   // blood drawn out of you in four streams into the grimoire
            double a = i * TAU / 4 + 0.4;
            Vec3 from = c.add(Math.cos(a) * 0.35, 0.7 + i * 0.25, Math.sin(a) * 0.35);
            final int ii = i;
            AstaFx.later(i * 2, () -> stream(sl, from, bookAt, 0, 0, 0.05f, 0.5f, 6, 16 + ii));
        }
        FxPacket pl = fx(14, ZenonAI.ground(sl, c), 34);   // your blood draws a small circle round your feet
        pl.r = 1.5f; pl.speed = 10;
        send(sl, pl);
        String what = greater ? "your next Blood spell is twice as strong" : twin ? "your next two Blood spells are 35% stronger" : "your next Blood spell is 60% stronger";
        p.displayClientMessage(Component.literal("Blood Pact: " + what).withStyle(ChatFormatting.DARK_RED), true);
        play(sl, c, "heartbeat", 1.2f, 1.0f);
    }

    // ================================================================== 6. Red Tide
    public static void redTide(ServerPlayer p, float power) {
        if (moon(p)) { IceFx.whiteAvalanche(p, power); return; }
        ServerLevel sl = p.serverLevel();
        price(p, 0.04f);
        float pow = pw(p, power);
        final Vec3 d = flatLook(p);
        float wide = path(p, "red_tide", 2) ? 1.5f : 1f;
        Vec3 start = p.position();
        FxPacket w = fx(4, ZenonAI.ground(sl, start.add(d.scale(1.5))), 15);   // a curling wave of blood, spraying off its crest
        w.d = d; w.r = 2.6f * wide; w.r2 = 2.4f; w.speed = 1.1f;
        send(sl, w);
        java.util.Set<UUID> struck = new java.util.HashSet<>();
        List<LivingEntity> hitList = new ArrayList<>();
        for (int k = 0; k < 12; k++) {
            final int kk = k;
            AstaFx.later(k, () -> {
                if (!alive(p)) return;
                Vec3 g = ZenonAI.ground(sl, start.add(d.scale(1.5 + kk * 1.1)));
                for (LivingEntity e : enemies(sl, p, new AABB(g, g).inflate(2.8 * wide, 2, 2.8 * wide))) {
                    if (!struck.add(e.getUUID())) continue;
                    hit(p, e, pow, "red_tide");
                    hitList.add(e);
                    e.setDeltaMovement(d.scale(1.3).add(0, 0.45, 0));
                    e.hurtMarked = true;
                }
                if (kk % 3 == 0) sound(sl, g, SoundEvents.GENERIC_SPLASH, 1.0f, 0.5f);
            });
        }
        if (path(p, "red_tide", 1)) AstaFx.later(20, () -> {   // Riptide: the wave draws back, dragging them with it
            if (!alive(p)) return;
            for (LivingEntity e : hitList) {
                if (!e.isAlive()) continue;
                Vec3 back = flat(p.position().subtract(e.position()));
                if (back.lengthSqr() < 1e-4) continue;
                e.setDeltaMovement(back.normalize().scale(1.1).add(0, 0.25, 0));
                e.hurtMarked = true;
                stream(sl, mid(e), p.position().add(0, 1, 0), e.getId(), p.getId(), 0.06f, 0.4f, 6, 12);
            }
            sound(sl, p.position(), SoundEvents.GENERIC_SPLASH, 1.0f, 0.4f);
        });
    }

    // ================================================================== 7. Thorned Veins
    public static void thornedVeins(ServerPlayer p, float power) {
        if (moon(p)) { IceFx.glacierSpires(p, power); return; }
        ServerLevel sl = p.serverLevel();
        price(p, 0.04f);
        float pow = pw(p, power);
        final Vec3 d = flatLook(p), rt = rightOf(d);
        int count = path(p, "thorned_veins", 2) ? 15 : 10;
        boolean briar = path(p, "thorned_veins", 1);
        FxPacket vein = fx(12, ZenonAI.ground(sl, p.position().add(d.scale(0.8))), 52 + (count - 10) * 2);   // a vein of blood racing along the ground
        vein.u = ZenonAI.ground(sl, p.position().add(d.scale(2 + (count - 1) * 1.4 + 0.6))); vein.speed = count * 2 - 1;
        send(sl, vein);
        java.util.Set<UUID> struck = new java.util.HashSet<>();
        for (int k = 0; k < count; k++) {
            final int kk = k;
            AstaFx.later(kk * 2, () -> {
                if (!alive(p)) return;
                Vec3 g = ZenonAI.ground(sl, p.position().add(d.scale(2 + kk * 1.4)));
                ThreadLocalRandom r = ThreadLocalRandom.current();
                for (int i = 0; i < 3; i++) {   // thorns of blood set hard, leaning out - later they melt back into pools
                    Vec3 base = g.add(rt.scale(r.nextDouble(-0.8, 0.8))).add(d.scale(r.nextDouble(-0.5, 0.5)));
                    FxPacket th = fx(3, base, 34 + r.nextInt(10));
                    th.d = new Vec3(r.nextDouble(-0.35, 0.35), 1, r.nextDouble(-0.35, 0.35)).normalize();
                    th.r = 1.5f + r.nextFloat() * 1.2f; th.r2 = 0.2f + r.nextFloat() * 0.08f; th.sweep = 3;
                    send(sl, th);
                }
                spray(sl, g.add(0, 0.2, 0), new Vec3(0, 0.2, 0), 0.09f, 6, 0.06f);
                for (LivingEntity e : enemies(sl, p, new AABB(g, g).inflate(1.6, 2.5, 1.6))) {
                    if (!struck.add(e.getUUID())) continue;
                    hit(p, e, pow, "thorned_veins");
                    e.setDeltaMovement(e.getDeltaMovement().add(0, 0.8, 0));
                    e.hurtMarked = true;
                }
                if (briar) AstaFx.later(20, () -> {   // Briar Field: the thorns strike again
                    if (!alive(p)) return;
                    for (LivingEntity e : enemies(sl, p, new AABB(g, g).inflate(1.6, 2.5, 1.6))) hit(p, e, pow * 0.5f, "thorned_veins");
                });
                if (kk % 2 == 0) sound(sl, g, SoundEvents.POINTED_DRIPSTONE_LAND, 1.0f, 0.6f);
            });
        }
    }

    // ================================================================== 8. Crimson Bulwark
    public static void crimsonBulwark(ServerPlayer p, float power) {
        if (moon(p)) { IceFx.frozenBastion(p, power); return; }
        ServerLevel sl = p.serverLevel();
        price(p, 0.06f);
        float shield = (6f + power * 0.5f) * (path(p, "crimson_bulwark", 1) ? 1.5f : 1f);
        p.setAbsorptionAmount(Math.max(p.getAbsorptionAmount(), shield));
        if (path(p, "crimson_bulwark", 2)) THORN_SHELL.put(p.getUUID(), sl.getGameTime() + 200);
        Vec3 c = p.position();
        FxPacket sh = fx(6, c, 200);   // a shell of blood round you, running over its own surface
        sh.v = new Vec3(p.getId(), 0, 0); sh.r = 1.05f;
        final int shell = send(sl, sh);
        sound(sl, c, SoundEvents.ARMOR_EQUIP_NETHERITE, 1.0f, 0.7f);
        final float pow = pw(p, power);
        p.getPersistentData().remove("isg_bulwark_broke");
        for (int k = 5; k < 200; k += 5) {   // while it holds it stays; when it's gone it bursts outward
            AstaFx.later(k, () -> {
                if (!alive(p) || p.getAbsorptionAmount() > 0.5f || p.getPersistentData().getBoolean("isg_bulwark_broke")) return;
                p.getPersistentData().putBoolean("isg_bulwark_broke", true);
                THORN_SHELL.remove(p.getUUID());
                Vec3 at = p.position().add(0, 1, 0);
                end(sl, at, shell);
                for (LivingEntity e : enemies(sl, p, new AABB(at, at).inflate(4))) hit(p, e, pow * 0.6f, "crimson_bulwark");
                sound(sl, at, SoundEvents.GLASS_BREAK, 1.0f, 0.6f);
                play(sl, at, "blood_splash", 1.2f, 0.7f);
            });
        }
    }

    // ================================================================== 9. Exsanguinate
    public static void exsanguinate(ServerPlayer p, float power) {
        if (moon(p)) { IceFx.absoluteZero(p, power); return; }
        ServerLevel sl = p.serverLevel();
        price(p, 0.05f);
        float pow = pw(p, power);
        double reach = path(p, "exsanguinate", 1) ? 13 : 9;
        float share = path(p, "exsanguinate", 2) ? 1.0f : 0.5f;
        Vec3 c = p.position().add(0, 1.1, 0);
        List<LivingEntity> foes = enemies(sl, p, new AABB(c, c).inflate(reach));
        play(sl, c, "heartbeat", 1.4f, 0.8f);
        FxPacket sg = fx(14, ZenonAI.ground(sl, p.position()), 30);   // a circle of blood drawn round you on the ground
        sg.r = (float) (reach * 0.36); sg.speed = 8;
        send(sl, sg);
        for (LivingEntity e : foes) {   // blood torn out of each of them, arcing into you
            spray(sl, mid(e), new Vec3(0, 0.12, 0), 0.06f, 6 + bleed(e) * 2, 0.06f);
            stream(sl, mid(e), c, e.getId(), p.getId(), 0.075f + bleed(e) * 0.012f, 1.4f, 10, 22);
        }
        AstaFx.later(20, () -> {
            if (!alive(p)) return;
            float healed = 0;
            for (LivingEntity e : foes) {
                if (!e.isAlive()) continue;
                int stacks = drink(sl, e);   // it drinks their bleeding: more damage, more gauge
                float before = e.getHealth();
                hit(p, e, pow * (1f + 0.25f * stacks), "exsanguinate");
                healed += Math.max(0f, before - e.getHealth()) * share;
                addGauge(p, stacks * 4f);
            }
            p.heal(healed);
            sound(sl, p.position(), SoundEvents.HONEY_DRINK, 1.4f, 0.6f);
        });
    }

    // ================================================================== 10. Scarlet Moon
    static final int MOON_T = 1200, FORM = 30;

    /** How long this transformation lasts: a minute, +up to 30 s of overflow, +15 s Long Night, +15 s Eternal Night. */
    private static int moonLength(ServerPlayer p, float overflow) {
        int t = MOON_T + Math.round(overflow / OVER * 600);
        if (path(p, "scarlet_moon", 1)) t += 300;
        if (crown(p)) t += 300;
        return t;
    }

    /**
     * The mode. White light is drawn in from all round you for a second and a half, then a pillar of it strikes you and
     * a white shockwave rolls out, and every spell turns to ice. Twenty transformations awaken it: a greater aura and a
     * crown of frost, and sneak-casting Scarlet Moon under the Moon raises the Ice Throne.
     */
    public static void scarletMoon(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        if (moon(p)) {
            if (p.isShiftKeyDown() && awakened(p)) IceFx.iceThrone(p, power); else IceFx.moonfall(p, power);
            return;
        }
        if (gauge(p) < MAX && !p.isCreative()) {
            p.displayClientMessage(Component.literal("Your Blood Gauge isn't full yet (" + (int) gauge(p) + "%)").withStyle(ChatFormatting.DARK_RED), true);
            return;
        }
        float overflow = Math.max(0, gauge(p) - MAX);
        final int len = moonLength(p, overflow);
        setGauge(p, 0);
        int count = p.getPersistentData().getInt(COUNT_KEY) + 1;
        p.getPersistentData().putInt(COUNT_KEY, count);
        final boolean awake = count >= AWAKEN_AT;
        p.getPersistentData().putLong(MOON_KEY, sl.getGameTime() + FORM + len);
        p.getPersistentData().putInt(LEN_KEY, len);
        syncGauge(p);
        Vec3 c0 = p.position();
        play(sl, c0, "heartbeat", 1.6f, 0.8f);
        sound(sl, c0, SoundEvents.BEACON_ACTIVATE, 0.8f, 0.6f);
        play(sl, c0, "freeze", 1.4f, 0.8f);
        FxPacket aw = fx(10, c0, FORM + 18);
        aw.r = awake ? 8f : 6f; aw.sweep = FORM;
        send(sl, aw);
        for (int k = 10; k < FORM; k += 10) {
            final int kk = k;
            AstaFx.later(k, () -> { if (alive(p)) play(sl, p.position(), "heartbeat", 1.2f, 0.9f + kk * 0.02f); });
        }
        AstaFx.later(FORM, () -> {
            if (!alive(p)) return;
            Vec3 c = p.position();
            FxPacket au = fx(9, c, len);   // the white aura round you (BloodFxClient kind 9); awakened: greater, with a crown of frost
            au.v = new Vec3(p.getId(), 0, 0); au.a0 = awake ? 1 : 0;
            com.ironspellgrimoire.network.AddonNetwork.send(net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), au);
            FxPacket a = new FxPacket();
            a.shape = 14; a.id = p.getId(); a.palette = 12; a.life = len;   // and the white glow on your body (AuraOutlineLayer 12)
            com.ironspellgrimoire.network.AddonNetwork.send(net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), a);
            Moves.stance(p, 4, 2, 0.25, 0.6, 0, len);
            Moves.mode(p, MODE, len, 10, pl -> {});
            p.heal(p.getMaxHealth() * 0.5f);   // the blood you paid comes back as the cold takes you
            syncGauge(p);
            FxSend.shake(sl, c, 0.6f, 10, 20);
            for (LivingEntity e : enemies(sl, p, new AABB(c, c).inflate(4))) {   // the shockwave throws back anyone close
                Vec3 away = flat(e.position().subtract(c));
                if (away.lengthSqr() > 1e-4) { e.setDeltaMovement(away.normalize().scale(1.2).add(0, 0.4, 0)); e.hurtMarked = true; }
            }
            String extra = overflow > 0 ? " (+" + Math.round((len - MOON_T) / 20f) + " s)" : "";
            p.displayClientMessage(Component.literal((awake ? "AWAKENED " : "") + "SCARLET MOON - your blood turns to ice" + extra).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD), true);
            if (count == AWAKEN_AT) p.sendSystemMessage(Component.literal("The Scarlet Moon has awakened: a greater aura and a crown of frost. Sneak and cast Scarlet Moon under the Moon to raise the Ice Throne.").withStyle(ChatFormatting.AQUA));
            sound(sl, c, SoundEvents.WITHER_SPAWN, 0.7f, 1.4f);
            play(sl, c, "ice_crack", 1.4f, 0.7f);
        });
    }

    // ================================================================== the Blood Gauge
    public static final float MAX = 100f, OVER = 50f;
    static final int AWAKEN_AT = 20;
    private static final String GAUGE_KEY = "isg_blood_gauge", MOON_KEY = "isg_moon_until", LEN_KEY = "isg_moon_len", COUNT_KEY = "isg_moon_count";

    public static float gauge(ServerPlayer p) { return p.getPersistentData().getFloat(GAUGE_KEY); }

    public static void setGauge(ServerPlayer p, float v) {
        p.getPersistentData().putFloat(GAUGE_KEY, Math.max(0f, Math.min(MAX + OVER, v)));
    }

    public static boolean awakened(ServerPlayer p) { return p.getPersistentData().getInt(COUNT_KEY) >= AWAKEN_AT; }

    /** Sets the gauge and shows it (UI pictures, tests). */
    public static void showGauge(ServerPlayer p, float v) { setGauge(p, v); syncGauge(p); }

    /** For tests and pictures: how many times this player has transformed. */
    public static void setMoonCount(ServerPlayer p, int n) { p.getPersistentData().putInt(COUNT_KEY, n); }

    /** Fills the gauge (never while the Moon is up); past full it overflows at half the rate. */
    static void addGauge(ServerPlayer p, float v) {
        if (v <= 0 || moon(p)) return;
        v *= 1f + tree(p, "gaugefill");
        float before = gauge(p);
        float after = before >= MAX ? before + v * 0.5f : before + v > MAX ? MAX + (before + v - MAX) * 0.5f : before + v;
        setGauge(p, after);
        if (before < MAX && gauge(p) >= MAX) {
            p.displayClientMessage(Component.literal("The Blood Gauge is full - cast Scarlet Moon").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
            play(p.serverLevel(), p.position(), "heartbeat", 1.4f, 1.3f);
        }
        syncGauge(p);
    }

    /** Sends the gauge (and how long the Moon has left) to the player's HUD (client/BloodHud, FxPacket shape 81). */
    static void syncGauge(ServerPlayer p) {
        FxPacket h = new FxPacket();
        int len = Math.max(MOON_T, p.getPersistentData().getInt(LEN_KEY));
        h.shape = 81; h.a0 = gauge(p) + 0.001f; h.r = holding(p) ? 1 : 0; h.a1 = awakened(p) ? 1 : 0;
        long left = p.getPersistentData().getLong(MOON_KEY) - p.serverLevel().getGameTime();
        h.sweep = (int) Math.max(0, Math.min(len + FORM, left)); h.life = len + FORM;
        com.ironspellgrimoire.network.AddonNetwork.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> p), h);
    }

    private static boolean holding(ServerPlayer p) {
        ItemStack s = book(p);
        return s != null && s.is(com.ironspellgrimoire.registry.AddonItemRegistry.BLOOD_GRIMOIRE.get());
    }

    @SubscribeEvent
    public static void onTick(net.minecraftforge.event.TickEvent.PlayerTickEvent e) {
        if (e.phase != net.minecraftforge.event.TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        if (p.tickCount % 4 == 1 && !POOLS.isEmpty() && holding(p)) drinkPools(p);
        if (p.tickCount % 20 != 7) return;
        long left = p.getPersistentData().getLong(MOON_KEY) - p.serverLevel().getGameTime();
        int len = Math.max(MOON_T, p.getPersistentData().getInt(LEN_KEY));
        if (left > 0 && left < len && !moon(p)) p.getPersistentData().remove(MOON_KEY);   // the mode was lost (a relog): the HUD lets go of it
        if (holding(p) || p.getPersistentData().getLong(MOON_KEY) > p.serverLevel().getGameTime() - 40) syncGauge(p);
        else if (p.getPersistentData().getBoolean("isg_gauge_shown")) syncGauge(p);
        p.getPersistentData().putBoolean("isg_gauge_shown", holding(p));
    }

    /**
     * Marked foes take 25% more from Blood spells; hits taken under the Moon are lessened (15%, more with Pale Ward);
     * Thorn Shell strikes a fifth of a blow back.
     */
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            if (Moves.inMode(p, MODE)) e.setAmount(e.getAmount() * (0.85f - tree(p, "moonguard")));
            Long shell = THORN_SHELL.get(p.getUUID());
            if (shell != null && shell > p.serverLevel().getGameTime() && e.getSource().getEntity() instanceof LivingEntity a && a != p && p.getAbsorptionAmount() > 0) {
                a.hurt(p.damageSources().thorns(p), e.getAmount() * 0.2f);
                spray(p.serverLevel(), mid(a), Vec3.ZERO, 0.06f, 5, 0.05f);
            }
        }
        IceFx.onHurt(e);
        long now = e.getEntity().level().getGameTime();
        MARKED.values().removeIf(t -> t < now - 200);
        BLEED.values().removeIf(b -> b[1] < now - 200);
    }
}
