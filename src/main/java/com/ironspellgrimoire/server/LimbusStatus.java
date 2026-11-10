package com.ironspellgrimoire.server;

import static com.ironspellgrimoire.server.FxUtil.*;

import com.ironspellgrimoire.IronSpellGrimoire;
import com.ironspellgrimoire.network.AddonNetwork;
import com.ironspellgrimoire.network.FxPacket;
import com.ironspellgrimoire.registry.AddonItemRegistry;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The statuses of the two Limbus-inspired grimoires, all our own (no vanilla effects):
 * <ul>
 *   <li><b>Bleed</b> (on enemies): hurts every time the bleeding body moves or swings, then loses some stacks.</li>
 *   <li><b>Bloodfeast</b> (Don Quixote, 0-999): every point of Bleed damage you cause is stored here, for skills to spend.</li>
 *   <li><b>Hardblood</b> (Don Quixote, 0-30): every 10 makes you faster; 10+ also hits harder, 20+ also takes less.</li>
 *   <li><b>Vampire Mode</b>: drains Bloodfeast; your blows heal you and double-tapping sneak bursts you forward as red mist.</li>
 *   <li><b>Fortuna</b> (on enemies, 0-200): Rien's mark. Each stack makes them take 0.5% more; at 40+ they hit 20% softer.</li>
 *   <li><b>Fortune</b> (Rien, 0-100) and the <b>Prescript</b>: every 20 s an order; carry it out for Fortune and a burst of speed.</li>
 * </ul>
 * The client HUD is fed by FxPacket shape 61 (palette 0 = Don Quixote, 1 = Rien).
 */
@Mod.EventBusSubscriber(modid = IronSpellGrimoire.MODID)
public final class LimbusStatus {
    private LimbusStatus() {}

    static final int BLOOD = 18, GOLD = 31;   // GOLD: Rien's marks are black slime now

    // ================================================================== Bleed
    private static final class Bleed { LivingEntity e; ServerPlayer src; int stacks; Vec3 last; long lastTrig; }
    private static final Map<UUID, Bleed> BLEED = new HashMap<>();

    /** Adds {@code n} Bleed to {@code t} (max 30). */
    public static void bleed(ServerPlayer src, LivingEntity t, int n) {
        if (t == null || !t.isAlive() || t == src) return;
        Bleed b = BLEED.computeIfAbsent(t.getUUID(), k -> { Bleed x = new Bleed(); x.e = t; x.last = t.position(); return x; });
        b.src = src;
        b.stacks = Math.min(30, b.stacks + n);
    }

    public static int bleedOf(LivingEntity t) {
        Bleed b = BLEED.get(t.getUUID());
        return b == null ? 0 : b.stacks;
    }

    /** Sets off all of {@code t}'s Bleed at once (the ultimate's burst). Returns the damage dealt. */
    public static float burstBleed(ServerPlayer src, LivingEntity t, float mult) {
        Bleed b = BLEED.remove(t.getUUID());
        if (b == null || b.stacks <= 0) return 0;
        float dmg = (0.6f + 0.35f * b.stacks) * mult;
        AstaFx.damage(src, t, dmg, "thirst");
        addBloodfeast(src, Math.round(dmg * 12));
        splash((ServerLevel) t.level(), mid(t), 1.2f);
        return dmg;
    }

    private static void trigger(Bleed b, ServerLevel sl, long now) {
        if (now - b.lastTrig < 8 || b.stacks <= 0) return;
        b.lastTrig = now;
        float dmg = 0.5f + 0.12f * b.stacks;
        if (b.src != null && alive(b.src)) {
            AstaFx.damage(b.src, b.e, dmg, "thirst");
            addBloodfeast(b.src, Math.round(dmg * 10));
        } else b.e.hurt(sl.damageSources().magic(), dmg);
        b.stacks -= Math.max(1, b.stacks / 5);
        splash(sl, mid(b.e), 0.5f);
    }

    /** A spurt of blood: a few small crimson spikes thrown out, and a wet sound. */
    static void splash(ServerLevel sl, Vec3 at, float size) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int n = (int) (4 + size * 5);
        for (int k = 0; k < n; k++) {   // a spray of thick drops flung out, already sagging
            Vec3 d = new Vec3(r.nextDouble(-1, 1), r.nextDouble(-0.1, 0.9), r.nextDouble(-1, 1)).normalize();
            FxSend.spike(sl, BLOOD, at, d, 0.22f * size + r.nextFloat() * 0.18f, 0.06f * size, 8, 0, FxPacket.POP, 0, d.scale(0.14).add(0, -0.08, 0), 7);
        }
        int drops = (int) (2 + size * 3);
        for (int k = 0; k < drops; k++) drip(sl, at.add(r.nextDouble(-0.4, 0.4) * size, r.nextDouble(-0.2, 0.2), r.nextDouble(-0.4, 0.4) * size), 2 + r.nextInt(6));
        sound(sl, at, SoundEvents.SLIME_SQUISH_SMALL, 0.5f + size * 0.2f, 0.8f + r.nextFloat() * 0.3f);
    }

    /** One drop of blood falling from {@code from} (after {@code delay} ticks) and spattering where it lands. */
    static void drip(ServerLevel sl, Vec3 from, int delay) {
        AstaFx.later(Math.max(1, delay), () -> {
            double floor = from.y;
            net.minecraft.core.BlockPos.MutableBlockPos bp = new net.minecraft.core.BlockPos.MutableBlockPos();
            for (int i = 0; i < 8; i++) {   // find the ground below (at most 8 blocks)
                bp.set(from.x, from.y - i - 0.01, from.z);
                if (!sl.getBlockState(bp).getCollisionShape(sl, bp).isEmpty()) { floor = bp.getY() + 1.0; break; }
                floor = from.y - i - 1;
            }
            double fall = Math.max(0.2, from.y - floor);
            int ticks = (int) Math.max(2, Math.min(14, Math.sqrt(fall) * 4.5));
            Vec3 v = new Vec3(0, -fall / ticks, 0);
            FxSend.spike(sl, BLOOD, from, new Vec3(0, -1, 0), 0.16f, 0.035f, ticks + 1, 0, FxPacket.POP, 0, v, ticks);
            Vec3 land = new Vec3(from.x, floor + 0.02, from.z);
            AstaFx.later(ticks, () -> {   // the spatter: a small dark pool spreading, two tiny drops jumping off it
                FxSend.ring(sl, BLOOD, land, 0.02f, 0.22f + ThreadLocalRandom.current().nextFloat() * 0.12f, 0.01f, 40);
                for (int k = 0; k < 2; k++) {
                    Vec3 d = new Vec3(ThreadLocalRandom.current().nextDouble(-1, 1), 0.8, ThreadLocalRandom.current().nextDouble(-1, 1)).normalize();
                    FxSend.spike(sl, BLOOD, land, d, 0.08f, 0.02f, 4, 0, FxPacket.POP, 0, d.scale(0.05), 3);
                }
            });
        });
    }

    // ================================================================== Bloodfeast, Hardblood, Vampire Mode (Don Quixote)
    private static final Map<UUID, int[]> DQ = new HashMap<>();   // player -> {bloodfeast, hardblood, last combat tick}
    private static final Map<UUID, Long> VAMPIRE = new HashMap<>();
    private static final Map<UUID, long[]> SNEAK = new HashMap<>(); // player -> {was sneaking, last press tick}
    private static final UUID HB_SPEED = UUID.fromString("e3b61c20-7a4d-4f8e-9b11-0d1a6c900101");
    private static final UUID HB_POWER = UUID.fromString("e3b61c20-7a4d-4f8e-9b11-0d1a6c900102");
    private static final UUID HB_GUARD = UUID.fromString("e3b61c20-7a4d-4f8e-9b11-0d1a6c900103");
    private static final UUID VAMP_SPEED = UUID.fromString("e3b61c20-7a4d-4f8e-9b11-0d1a6c900104");

    private static int[] dq(ServerPlayer p) { return DQ.computeIfAbsent(p.getUUID(), k -> new int[3]); }

    public static int bloodfeast(ServerPlayer p) { return dq(p)[0]; }

    public static void addBloodfeast(ServerPlayer p, int n) {
        int[] d = dq(p);
        d[0] = Math.max(0, Math.min(999, d[0] + n));
    }

    /** Spends up to {@code n} Bloodfeast; returns how much was there to spend. */
    public static int spendBloodfeast(ServerPlayer p, int n) {
        int[] d = dq(p);
        int s = Math.min(d[0], n);
        d[0] -= s;
        return s;
    }

    public static int hardblood(ServerPlayer p) { return dq(p)[1]; }

    public static void addHardblood(ServerPlayer p, int n) {
        int[] d = dq(p);
        int before = d[1] / 10;
        d[1] = Math.max(1, Math.min(30, d[1] + n));
        d[2] = (int) p.serverLevel().getGameTime();
        if (d[1] / 10 > before) {   // a new stage: the blood hardens over you with a ring
            FxSend.ring(p.serverLevel(), BLOOD, p.position().add(0, 0.1, 0), 0.3f, 2.2f, 0.12f, 10);
            sound(p.serverLevel(), p.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 0.8f, 0.6f);
            p.displayClientMessage(Component.literal("Hardblood " + "I".repeat(Math.min(3, d[1] / 10 + 1))).withStyle(ChatFormatting.DARK_RED), true);
        }
    }

    public static boolean vampire(ServerPlayer p) {
        Long u = VAMPIRE.get(p.getUUID());
        return u != null && p.serverLevel().getGameTime() < u;
    }

    public static void startVampire(ServerPlayer p, int ticks) {
        VAMPIRE.put(p.getUUID(), p.serverLevel().getGameTime() + ticks);
        ModeHud.show(p, "vampire_mode", ticks);
        sendAura(p, ticks);
    }

    static void endVampire(ServerPlayer p) {
        if (VAMPIRE.remove(p.getUUID()) == null) return;
        ModeHud.hide(p, "vampire_mode");
        sendAura(p, 0);
        TimedModifiers.remove(p, Attributes.MOVEMENT_SPEED, VAMP_SPEED);
        p.displayClientMessage(Component.literal("The thirst fades.").withStyle(ChatFormatting.GRAY), true);
    }

    /** The red aura outline and the cape (client: AuraOutlineLayer palette 9 via shape 14, and the cape via shape 62). */
    private static void sendAura(ServerPlayer p, int ticks) {
        FxPacket pkt = new FxPacket();
        pkt.shape = 62; pkt.id = p.getId(); pkt.life = ticks; pkt.palette = 0;
        AddonNetwork.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), pkt);
    }

    /** Red mist: burst into blood mist and come out 5 blocks ahead. */
    private static void mistStep(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        Vec3 look = flat(p.getLookAngle());
        Vec3 to = SpaceFx.safe(sl, clip(sl, p, p.position().add(0, 1, 0), p.position().add(0, 1, 0).add(look.scale(5))).subtract(look.scale(0.5)).add(0, -1, 0), p);
        if (to == null) return;
        mist(sl, p.position().add(0, 1, 0));
        SpaceFx.move(p, to, p.getYRot());
        mist(sl, to.add(0, 1, 0));
        sound(sl, to, SoundEvents.PHANTOM_FLAP, 0.8f, 0.7f);
        spendBloodfeast(p, 30);
    }

    static void mist(ServerLevel sl, Vec3 c) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int k = 0; k < 10; k++) {
            Vec3 o = new Vec3(r.nextDouble(-0.6, 0.6), r.nextDouble(-0.9, 0.9), r.nextDouble(-0.6, 0.6));
            Kit.orb(sl, BLOOD, c.add(o), o.scale(0.06), 0.25f + r.nextFloat() * 0.2f, 12 + r.nextInt(6));
        }
    }

    static boolean holdsBloodfiend(ServerPlayer p) {
        ItemStack s = Utils.getPlayerSpellbookStack(p);
        return s != null && s.is(AddonItemRegistry.BLOODFIEND_GRIMOIRE.get());
    }

    static boolean holdsCaduceus(ServerPlayer p) {
        ItemStack s = Utils.getPlayerSpellbookStack(p);
        return s != null && s.is(AddonItemRegistry.CADUCEUS_GRIMOIRE.get());
    }

    // ================================================================== Fortuna (on enemies) and Fortune / Prescript (Rien)
    private static final class Fortuna { LivingEntity e; int stacks; long lastHit; }
    private static final Map<UUID, Fortuna> FORTUNA = new HashMap<>();

    public static void addFortuna(ServerPlayer src, LivingEntity t, int n) {
        if (t == null || !t.isAlive() || t == src) return;
        Fortuna f = FORTUNA.computeIfAbsent(t.getUUID(), k -> { Fortuna x = new Fortuna(); x.e = t; return x; });
        int before = f.stacks / 10;
        f.stacks = Math.min(200, f.stacks + n);
        f.lastHit = t.level().getGameTime();
        if (f.stacks / 10 > before) {   // a golden crack runs over them with every 10 stacks
            ServerLevel sl = (ServerLevel) t.level();
            FxSend.cracks(sl, GOLD, mid(t), new Vec3(0, 1, 0), flat(t.position().subtract(src.position())), Math.max(0.6f, t.getBbHeight() * 0.5f), 10, false, t.getId() * 7 + f.stacks);
        }
    }

    public static int fortunaOf(LivingEntity t) {
        Fortuna f = FORTUNA.get(t.getUUID());
        return f == null ? 0 : f.stacks;
    }

    /** Takes all of {@code t}'s Fortuna; returns how many there were. */
    public static int consumeFortuna(LivingEntity t) {
        Fortuna f = FORTUNA.remove(t.getUUID());
        return f == null ? 0 : f.stacks;
    }

    private static final Map<UUID, int[]> RIEN = new HashMap<>();   // player -> {fortune, prescript type (-1 none), progress, needed, ends at, next at, x}
    private static final Map<UUID, Set<Integer>> PRESCRIPT_HIT = new HashMap<>();
    private static final Map<UUID, Long> RECEIVED = new HashMap<>();
    private static final UUID FORTUNE_SPEED = UUID.fromString("e3b61c20-7a4d-4f8e-9b11-0d1a6c900105");
    private static final UUID FORTUNE_ATTACK = UUID.fromString("e3b61c20-7a4d-4f8e-9b11-0d1a6c900106");
    static final String[] ORDERS = {
            "Strike three different foes within 8 seconds.",
            "Stand perfectly still for 2 seconds.",
            "Land a hit with a thrown or fired form.",
            "Strike the same foe four times.",
            "Take no damage for 6 seconds.",
            "Slay a foe."};

    private static int[] rien(ServerPlayer p) {
        return RIEN.computeIfAbsent(p.getUUID(), k -> new int[]{0, -1, 0, 0, 0, (int) p.serverLevel().getGameTime() + 200, 0});
    }

    public static int fortune(ServerPlayer p) { return rien(p)[0]; }

    public static void addFortune(ServerPlayer p, int n) {
        int[] r = rien(p);
        r[0] = Math.max(0, Math.min(100, r[0] + n));
    }

    public static int spendFortune(ServerPlayer p) {
        int[] r = rien(p);
        int f = r[0];
        r[0] = 0;
        return f;
    }

    public static boolean prescriptReceived(ServerPlayer p) {
        Long u = RECEIVED.get(p.getUUID());
        return u != null && p.serverLevel().getGameTime() < u;
    }

    public static void startReceived(ServerPlayer p, int ticks) {
        RECEIVED.put(p.getUUID(), p.serverLevel().getGameTime() + ticks);
        ModeHud.show(p, "prescript_received", ticks);
        int[] r = rien(p);
        if (r[1] < 0) issue(p, r);   // an order at once
    }

    private static void issue(ServerPlayer p, int[] r) {
        long now = p.serverLevel().getGameTime();
        int type = ThreadLocalRandom.current().nextInt(ORDERS.length);
        r[1] = type; r[2] = 0;
        r[3] = switch (type) { case 0 -> 3; case 1 -> 40; case 3 -> 4; case 4 -> 120; default -> 1; };
        int window = switch (type) { case 0 -> 160; case 1 -> 160; case 4 -> 160; default -> 240; };
        r[4] = (int) (now + window);
        PRESCRIPT_HIT.remove(p.getUUID());
        p.displayClientMessage(Component.literal("Prescript: " + ORDERS[type]).withStyle(ChatFormatting.GOLD), true);
        sound(p.serverLevel(), p.position(), SoundEvents.BOOK_PAGE_TURN, 0.9f, 1.4f);
        sound(p.serverLevel(), p.position(), SoundEvents.EXPERIENCE_ORB_PICKUP, 0.4f, 0.6f);
    }

    private static void progress(ServerPlayer p, int n) {
        int[] r = rien(p);
        if (r[1] < 0) return;
        r[2] += prescriptReceived(p) ? n * 2 : n;
        if (r[2] >= r[3]) fulfil(p, r);
    }

    private static void fulfil(ServerPlayer p, int[] r) {
        long now = p.serverLevel().getGameTime();
        r[1] = -1; r[5] = (int) (now + (prescriptReceived(p) ? 100 : 400));
        addFortune(p, 25);
        TimedModifiers.apply(p, Attributes.MOVEMENT_SPEED, FORTUNE_SPEED, "Prescript fulfilled", 0.25, AttributeModifier.Operation.MULTIPLY_TOTAL, 160);
        TimedModifiers.apply(p, Attributes.ATTACK_DAMAGE, FORTUNE_ATTACK, "Prescript fulfilled", 0.2, AttributeModifier.Operation.MULTIPLY_TOTAL, 160);
        ServerLevel sl = p.serverLevel();
        FxSend.ring(sl, GOLD, p.position().add(0, 0.1, 0), 0.3f, 2.6f, 0.12f, 12);
        FxSend.ring(sl, Fx.SILVER, p.position().add(0, 1.0, 0), 0.2f, 1.6f, 0.06f, 10);
        sound(sl, p.position(), SoundEvents.PLAYER_LEVELUP, 0.6f, 1.6f);
        p.displayClientMessage(Component.literal("Prescript fulfilled.").withStyle(ChatFormatting.YELLOW), true);
    }

    private static void fail(ServerPlayer p, int[] r) {
        r[1] = -1; r[5] = (int) (p.serverLevel().getGameTime() + 300);
        addFortune(p, -10);
        p.displayClientMessage(Component.literal("The Prescript was not carried out.").withStyle(ChatFormatting.DARK_GRAY), true);
        sound(p.serverLevel(), p.position(), SoundEvents.BOOK_PUT, 0.8f, 0.6f);
    }

    /** Called by Rien's skills when one lands: Fortuna on the target, Fortune for you, and the Prescript's count. */
    static void rienHit(ServerPlayer p, LivingEntity t, int fortuna, boolean ranged) {
        addFortuna(p, t, fortuna);
        addFortune(p, 1);
        int[] r = rien(p);
        if (r[1] == 0 && PRESCRIPT_HIT.computeIfAbsent(p.getUUID(), k -> new HashSet<>()).add(t.getId())) progress(p, 1);
        if (r[1] == 2 && ranged) progress(p, 1);
        if (r[1] == 3) {
            Set<Integer> s = PRESCRIPT_HIT.computeIfAbsent(p.getUUID(), k -> new HashSet<>());
            if (s.isEmpty() || s.contains(t.getId())) { s.add(t.getId()); progress(p, 1); }
        }
    }

    // ================================================================== ticking
    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        // bleed: moving hurts
        // a snapshot: the damage below can add or remove Bleed (lifesteal, deaths) while we go through them
        BLEED.values().removeIf(b -> !b.e.isAlive() || b.e.isRemoved() || b.stacks <= 0);
        for (Bleed b : new java.util.ArrayList<>(BLEED.values())) {
            if (!b.e.isAlive() || b.stacks <= 0) continue;
            ServerLevel sl = (ServerLevel) b.e.level();
            long now = sl.getGameTime();
            if (now % 5 != 0) continue;
            Vec3 pos = b.e.position();
            if (pos.distanceToSqr(b.last) > 0.35 * 0.35) trigger(b, sl, now);
            b.last = pos;
            if (now % 20 == 0) {   // a drop running off the wound
                Vec3 at = mid(b.e).add(ThreadLocalRandom.current().nextDouble(-0.3, 0.3), 0, ThreadLocalRandom.current().nextDouble(-0.3, 0.3));
                FxSend.spike(sl, BLOOD, at, new Vec3(0, -1, 0), 0.18f, 0.04f, 10, 0, FxPacket.POP, 0, new Vec3(0, -0.08, 0), 9);
            }
        }
        // fortuna fades if they are left alone
        FORTUNA.values().removeIf(f -> !f.e.isAlive() || f.e.isRemoved() || f.stacks <= 0);
        for (Fortuna f : new java.util.ArrayList<>(FORTUNA.values())) {
            long now = f.e.level().getGameTime();
            if (now - f.lastHit > 100 && now % 40 == 0) f.stacks -= 5;
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        long now = p.serverLevel().getGameTime();
        boolean dqBook = holdsBloodfiend(p), rienBook = holdsCaduceus(p);
        // ---- Don Quixote
        int[] d = DQ.get(p.getUUID());
        if (d != null) {
            if (now % 20 == 0) {
                int stage = d[1] / 10;
                if (d[1] > 0) {
                    TimedModifiers.apply(p, Attributes.MOVEMENT_SPEED, HB_SPEED, "Hardblood", 0.06 * (stage + 1), AttributeModifier.Operation.MULTIPLY_TOTAL, 30);
                    if (stage >= 1) TimedModifiers.apply(p, Attributes.ATTACK_DAMAGE, HB_POWER, "Hardblood", 0.12 * stage, AttributeModifier.Operation.MULTIPLY_TOTAL, 30);
                    if (stage >= 2) TimedModifiers.apply(p, Attributes.ARMOR, HB_GUARD, "Hardblood", 4, AttributeModifier.Operation.ADDITION, 30);
                }
                if (now - d[2] > 200 && d[1] > 0) d[1]--;   // it softens again out of a fight
            }
        }
        if (VAMPIRE.containsKey(p.getUUID())) {
            if (!vampire(p) || !dqBook) endVampire(p);
            else {
                if (now % 4 == 0 && spendBloodfeast(p, 3) <= 0 && now % 20 == 0) endVampire(p);
                if (now % 20 == 0) TimedModifiers.apply(p, Attributes.MOVEMENT_SPEED, VAMP_SPEED, "Vampire", 0.2, AttributeModifier.Operation.MULTIPLY_TOTAL, 30);
                if (now % 10 == 0) {   // now and then a drop falls from the cape's hem
                    Vec3 back = p.position().add(0, 0.3, 0).subtract(flat(p.getLookAngle()).scale(0.5));
                    FxSend.spike(p.serverLevel(), BLOOD, back, new Vec3(0, -1, 0), 0.14f, 0.03f, 8, 0, FxPacket.POP, 0, new Vec3(0, -0.06, 0), 6);
                }
                long[] s = SNEAK.computeIfAbsent(p.getUUID(), k -> new long[]{0, -100});
                boolean sn = p.isShiftKeyDown();
                if (sn && s[0] == 0) {
                    if (now - s[1] < 8) { mistStep(p); s[1] = -100; }
                    else s[1] = now;
                }
                s[0] = sn ? 1 : 0;
            }
        }
        // ---- Rien: the Prescript
        if (rienBook) {
            int[] r = rien(p);
            if (r[1] < 0 && now >= r[5]) issue(p, r);
            else if (r[1] >= 0) {
                if (r[1] == 1) {   // stand still
                    if (p.getDeltaMovement().horizontalDistanceSqr() < 1.0E-3 && !p.isSprinting()) progress(p, 1);
                    else r[2] = 0;
                }
                if (r[1] == 4) progress(p, 1);   // no damage: counts up, reset on hurt
                if (r[1] >= 0 && now > r[4]) fail(p, r);
            }
        }
        // ---- the HUD
        if (now % 5 == 0 && (dqBook || rienBook)) {
            FxPacket pkt = new FxPacket();
            pkt.shape = 61; pkt.id = p.getId();
            if (dqBook) {
                int[] dd = dq(p);
                pkt.palette = 0; pkt.h = dd[0]; pkt.r = dd[1]; pkt.r2 = vampire(p) ? 1 : 0;
            } else {
                int[] r = rien(p);
                pkt.palette = 1; pkt.h = r[0]; pkt.r = r[1]; pkt.r2 = r[3] <= 0 ? 0 : Math.min(1f, r[2] / (float) r[3]);
                pkt.life = r[1] >= 0 ? (int) Math.max(0, r[4] - now) : 0; pkt.a0 = prescriptReceived(p) ? 1 : 0;
            }
            AddonNetwork.send(PacketDistributor.PLAYER.with(() -> p), pkt);
        }
    }

    // ================================================================== hits: bleed on swing, lifesteal, fortuna
    @SubscribeEvent
    public static void onAttack(LivingAttackEvent e) {
        if (!(e.getSource().getEntity() instanceof LivingEntity att)) return;
        Bleed b = BLEED.get(att.getUUID());
        if (b != null && att.level() instanceof ServerLevel sl) trigger(b, sl, sl.getGameTime());   // swinging opens the wound
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent e) {
        LivingEntity t = e.getEntity();
        Fortuna f = FORTUNA.get(t.getUUID());
        if (f != null) e.setAmount(e.getAmount() * (1f + 0.005f * f.stacks));
        if (e.getSource().getEntity() instanceof LivingEntity att) {
            Fortuna fa = FORTUNA.get(att.getUUID());
            if (fa != null && fa.stacks >= 40) e.setAmount(e.getAmount() * 0.8f);
            if (att instanceof ServerPlayer sp && vampire(sp) && t != sp) {
                sp.heal(e.getAmount() * 0.18f);
                if (ThreadLocalRandom.current().nextInt(3) == 0) bleed(sp, t, 1);
            }
            if (att instanceof ServerPlayer sp && holdsBloodfiend(sp)) dq(sp)[2] = (int) sp.serverLevel().getGameTime();
        }
        if (t instanceof ServerPlayer sp) {
            int[] r = RIEN.get(sp.getUUID());
            if (r != null && r[1] == 4) r[2] = 0;   // the "take no damage" order starts again
        }
    }

    @SubscribeEvent
    public static void onDeath(net.minecraftforge.event.entity.living.LivingDeathEvent e) {
        BLEED.remove(e.getEntity().getUUID());
        FORTUNA.remove(e.getEntity().getUUID());
        if (e.getSource().getEntity() instanceof ServerPlayer sp) {
            int[] r = RIEN.get(sp.getUUID());
            if (r != null && r[1] == 5) progress(sp, 1);
        }
        if (e.getEntity() instanceof ServerPlayer sp) endVampire(sp);
    }

    @SubscribeEvent
    public static void onStop(ServerStoppingEvent e) {
        BLEED.clear(); DQ.clear(); VAMPIRE.clear(); SNEAK.clear(); FORTUNA.clear(); RIEN.clear(); PRESCRIPT_HIT.clear(); RECEIVED.clear();
    }
}
