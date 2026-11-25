package com.ironspellgrimoire.server;

import static com.ironspellgrimoire.server.FxUtil.*;

import com.ironspellgrimoire.IronSpellGrimoire;
import com.ironspellgrimoire.entity.AddonBoss;
import com.ironspellgrimoire.network.AddonNetwork;
import com.ironspellgrimoire.network.MeridianPackets;
import com.ironspellgrimoire.registry.AddonItemRegistry;
import io.redspace.ironsspellbooks.api.util.Utils;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * The Meridian Grimoire: power that follows the sun (every name, look and line here is original).
 * <ul>
 *   <li><b>The Sun Meter</b> - 0 at night, climbing through the morning, 100% at noon, falling through the afternoon.
 *       Open sky for full strength (under a roof: half), rain and thunder dim it; the Nether is a dim false sun, the
 *       End has none. Fighting in daylight climbs it further, up to +25% (5% a second).</li>
 *   <li><b>Day pages</b> (work while the meter is 10%+): Hearth Star (a small sun that goes where you look),
 *       Haughty Flare (the star overheats and bursts), Call Dayreaver (the great sun-axe in your hands - its blows
 *       scale with the sun, burn while it's up, and cleave in Zenith), Noonwave (a cone of heat that throws foes back).</li>
 *   <li><b>Night pages</b> (work while the sun is at half or lower): Lamplight (a small lamp-bolt), Warm Hands (a heal
 *       for you and the most hurt friend near), Last Coal (a shield that grows while you stand still). Each night cast
 *       banks heat; at dawn the bank becomes a head start on the meter.</li>
 *   <li><b>Unbowed</b> (Pride, 0-10): holding your ground in a fight builds it, running away loses it. Each stack +3%
 *       damage (and +2% more against bosses and rivals).</li>
 *   <li><b>Heat</b>: fire can't hurt you while the meter is at half or more.</li>
 *   <li><b>Zenith Unbound</b>: around noon (and under open sky), 60 s at the peak - you grow to full size, your blows
 *       land far harder, blows on you land half as hard, the Hearth Star is at its largest and Dayreaver cleaves.
 *       <b>Borrowed Noon</b>: once a day, at any hour, the same for 30 s and stronger still - for 30% of your max
 *       health, for 10 minutes.</li>
 *   <li>Mastery: 2 Dawnbreak (+30% power at dawn and dusk), 3 Sunstride (faster sprinting in daylight), 4 Eclipse
 *       Guard (the night's bank also shields you), 5 Unclouded (no roof or weather penalty).</li>
 *   <li>A voice: humble by night, proud by day.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = IronSpellGrimoire.MODID)
public final class MeridianFx {
    private MeridianFx() {}

    static final int SUN = Fx.GOLD, WHITE = Fx.SILVER, HEAT = Fx.FIRE, EMBER = Fx.FIRE;
    static final UUID ZENITH_DMG = UUID.fromString("7a1e0000-0000-4000-8000-0000000000d1"), BORROW_HP = UUID.fromString("7a1e0000-0000-4000-8000-0000000000d2"),
            STRIDE = UUID.fromString("7a1e0000-0000-4000-8000-0000000000d3");
    public static final int ZENITH_TICKS = 1200, BORROW_TICKS = 600, BORROW_COST_TICKS = 12000;

    static final class State {
        float climb, banked;
        int pride, prideTick;
        long lastCombat = -1000, zenithUntil, borrowDay = -1, coalUntil, starUntil, lastVoice = -10000;
        boolean borrowed, wasDay, init;
        double lastDist = -1;
        Vec3 star;
        float starSize;
    }
    static final Map<UUID, State> S = new HashMap<>();
    static State st(Player p) { return S.computeIfAbsent(p.getUUID(), k -> new State()); }

    // ================================================================== the book and the sun
    static ItemStack book(Player p) { return Utils.getPlayerSpellbookStack(p); }
    public static boolean has(Player p) { ItemStack b = book(p); return b != null && b.is(AddonItemRegistry.MERIDIAN_GRIMOIRE.get()); }
    static int level(Player p) { ItemStack b = book(p); return b == null ? 0 : GrimoireMastery.level(b); }

    /** The sun's height for a time of day: 0 from dusk to dawn, 1 at noon (day time 6000). */
    public static float sunAt(long dayTime) {
        long t = Math.floorMod(dayTime, 24000L);
        return t < 12000 ? (float) Math.sin(Math.PI * t / 12000.0) : 0f;
    }

    static float sun(Level l) { return sunAt(l.getDayTime()); }

    /** The sun's strength where you stand: its height, the sky above you, the weather, the world. */
    public static float base(Player p) {
        Level l = p.level();
        if (l.dimension() == Level.NETHER) return 0.35f;
        if (l.dimension() != Level.OVERWORLD) return 0f;
        float s = sun(l);
        if (level(p) >= 5) return s;   // Unclouded
        BlockPos head = BlockPos.containing(p.getEyePosition());
        if (!l.canSeeSky(head)) s *= 0.5f;
        if (l.isThundering()) s *= 0.5f;
        else if (l.isRainingAt(head.above())) s *= 0.7f;
        return s;
    }

    /** The Sun Meter: 0 to 1.25 (the extra from fighting in daylight). In Zenith, at least full. */
    public static float meter(Player p) {
        State s = st(p);
        float m = Math.min(1.25f, base(p) + s.climb);
        return zenith(p) ? Math.max(1f, m) : m;
    }

    /** The night's strength: how far the sun is below its height (for the night pages). */
    static float night(Player p) { return 1f - sun(p.level()); }

    public static boolean zenith(Player p) { return st(p).zenithUntil > p.level().getGameTime(); }
    static boolean borrowedNow(Player p) { State s = st(p); return s.borrowed && zenith(p); }
    static long today(Level l) { return Math.floorDiv(l.getDayTime(), 24000L); }

    /** Noon's window: an in-game hour either side of noon, with a strong sun where you stand. */
    public static boolean noonWindow(Player p) {
        long t = Math.floorMod(p.level().getDayTime(), 24000L);
        return t >= 5000 && t <= 7000 && base(p) >= 0.5f;
    }

    /** Dawnbreak (mastery 2): +30% power while the sun is low (dawn and dusk). */
    static float dawnbreak(Player p) {
        float s = sun(p.level());
        return level(p) >= 2 && s > 0.02f && s < 0.4f ? 1.3f : 1f;
    }

    // the pages' gates
    public static boolean canDay(Player p) { return meter(p) >= 0.1f || zenith(p); }
    public static boolean canNight(Player p) { return sun(p.level()) <= 0.5f; }
    public static boolean canZenith(Player p) { return !zenith(p) && (noonWindow(p) || st(p).borrowDay != today(p.level())); }

    // ================================================================== the voice: humble by night, proud by day
    static final String[] DAWN = {"...Morning already? Then step aside.", "The sun is up. So am I."};
    static final String[] DUSK = {"Oh - it's getting dark. S-sorry, I'll be quieter now.", "...Is it night? I didn't mean to shout earlier."};
    static final String[] NOON = {"Noon. The sky answers to me.", "Look up. There is only one sun, and it is here."};
    static final String[] BORROW = {"If the sun won't come to me, I'll burn what I have.", "I'll pay for this later. Gladly."};
    static final String[] FADE = {"...That was all the light I had.", "The sun moves on. So must I."};
    static final String[] PROUD = {"Kneel? I don't know the word.", "You may look at me. Briefly."};

    static void say(ServerPlayer p, String[] lines, boolean force) {
        State s = st(p);
        long now = p.level().getGameTime();
        if (!force && now - s.lastVoice < 600) return;
        s.lastVoice = now;
        boolean proud = sun(p.level()) > 0.3f || zenith(p);
        p.displayClientMessage(Component.literal(lines[(int) (now / 20 % lines.length)]).withStyle(proud ? ChatFormatting.GOLD : ChatFormatting.GRAY, ChatFormatting.ITALIC), true);
    }

    // ================================================================== the tick
    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || !p.isAlive()) return;
        if (!has(p)) { if (S.containsKey(p.getUUID()) && p.tickCount % 20 == 0) { star(p, null); sync(p); S.remove(p.getUUID()); } return; }
        State s = st(p);
        if (s.star != null) starTick(p, s);
        if (p.tickCount % 10 != 0) return;
        ServerLevel sl = p.serverLevel();
        long now = sl.getGameTime();
        boolean fighting = now - s.lastCombat < 100;
        float base = base(p);
        // fighting in daylight climbs the meter; peace lets it settle
        if (fighting && base > 0.05f) s.climb = Math.min(0.25f, s.climb + 0.025f);
        else s.climb = Math.max(0f, s.climb - 0.01f);
        // dawn and dusk
        boolean day = sun(sl) > 0.02f && sl.dimension() == Level.OVERWORLD;
        if (!s.init) { s.init = true; s.wasDay = day; }
        if (day && !s.wasDay) {
            s.climb = Math.min(0.25f, s.climb + s.banked); s.banked = 0;
            play(sl, p.position(), "meridian_dawn", 1f, 1f);
            say(p, DAWN, true);
        }
        if (!day && s.wasDay) say(p, DUSK, true);
        s.wasDay = day;
        // Unbowed: hold your ground and Pride grows; run and it's lost
        LivingEntity foe = nearestFoe(p, 12);
        if (fighting && foe != null) {
            double d = foe.distanceTo(p);
            if (s.lastDist >= 0 && d > s.lastDist + 0.8 && p.isSprinting()) s.pride = Math.max(0, s.pride - 3);
            else if (s.lastDist < 0 || d <= s.lastDist + 0.3) {
                if (++s.prideTick >= 4) {
                    s.prideTick = 0;
                    if (s.pride < 10) {
                        s.pride++;
                        play(sl, p.position(), "meridian_pride", 0.5f, 0.9f + s.pride * 0.05f);
                        if (s.pride == 10) say(p, PROUD, false);
                    }
                }
            }
            s.lastDist = d;
        } else {
            s.lastDist = -1;
            if (!fighting && now % 100 == 0 && s.pride > 0) s.pride--;
        }
        // Sunstride (mastery 3): sprinting in daylight is quicker
        if (level(p) >= 3 && p.isSprinting() && meter(p) >= 0.4f)
            TimedModifiers.apply(p, Attributes.MOVEMENT_SPEED, STRIDE, "Sunstride", 0.15, AttributeModifier.Operation.MULTIPLY_TOTAL, 20);
        // Zenith: a heat round you; and its end
        if (zenith(p)) {
            FxSend.ring(sl, s.borrowed ? HEAT : SUN, p.position().add(0, 0.1, 0), 1.4f, 1.6f, 0.05f, 12);
            if (now % 20 == 0) for (LivingEntity t : enemies(sl, p, p.getBoundingBox().inflate(3))) { AstaFx.damage(p, t, 2f, "zenith_unbound"); t.setSecondsOnFire(2); }
        } else if (s.zenithUntil > 0 && now >= s.zenithUntil) {
            s.zenithUntil = 0;
            s.borrowed = false;
            say(p, FADE, true);
        }
        // Last Coal: the shield grows while you stand still
        if (s.coalUntil > now && now % 20 == 0) {
            Vec3 v = p.getDeltaMovement();
            float cap = 6f + 4f * night(p);
            if (v.x * v.x + v.z * v.z < 0.003 && p.getAbsorptionAmount() < cap) {
                p.setAbsorptionAmount(Math.min(cap, p.getAbsorptionAmount() + 1f));
                FxSend.ring(sl, EMBER, p.position().add(0, 0.1, 0), 0.6f, 0.8f, 0.04f, 10);
            }
        }
        // noon's call
        if (noonWindow(p) && !zenith(p) && now % 400 == 0) say(p, NOON, false);
        sync(p);
    }

    static LivingEntity nearestFoe(ServerPlayer p, double r) {
        LivingEntity best = null;
        double bd = r * r;
        for (LivingEntity t : enemies(p.serverLevel(), p, p.getBoundingBox().inflate(r))) {
            if (!(t instanceof net.minecraft.world.entity.monster.Enemy) && !(t instanceof AddonBoss)) continue;
            double d = t.distanceToSqr(p);
            if (d < bd) { bd = d; best = t; }
        }
        return best;
    }

    public static void sync(ServerPlayer p) {
        State s = S.get(p.getUUID());
        boolean on = s != null && has(p);
        long now = p.level().getGameTime();
        AddonNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), new MeridianPackets.Sync(p.getId(), on,
                on ? meter(p) : 0, on ? sun(p.level()) : 0, on ? s.pride : 0, on && zenith(p) ? (int) (s.zenithUntil - now) : 0,
                on && s.borrowed, on && s.borrowDay == today(p.level()), on ? s.banked : 0));
    }

    // ================================================================== damage: Pride, Dayreaver, Zenith, heat
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onHurt(LivingHurtEvent e) {
        LivingEntity t = e.getEntity();
        if (t.level().isClientSide) return;
        // blows on the Meridian mage
        if (t instanceof ServerPlayer p && has(p)) {
            State s = st(p);
            s.lastCombat = p.level().getGameTime();
            if (zenith(p)) e.setAmount(e.getAmount() * (s.borrowed ? 0.4f : 0.5f));
            else if (level(p) >= 4 && sun(p.level()) < 0.05f) e.setAmount(e.getAmount() * (1f - 0.2f * Math.min(1f, s.banked / 0.3f)));   // Eclipse Guard
            Vec3 v = p.getDeltaMovement();
            if (v.x * v.x + v.z * v.z < 0.003 && s.pride < 10) s.pride++;   // taking it standing up
        }
        // blows by the Meridian mage
        if (e.getSource().getEntity() instanceof ServerPlayer p && p != t && has(p)) {
            State s = st(p);
            s.lastCombat = p.level().getGameTime();
            float m = 1f + 0.03f * s.pride + (t instanceof AddonBoss || TimeStop.isBoss(t) ? 0.02f * s.pride : 0f);
            if (zenith(p)) m *= s.borrowed ? 1.95f : 1.5f;
            boolean melee = e.getSource().getDirectEntity() == p && p.getMainHandItem().is(AddonItemRegistry.DAYREAVER.get());
            if (melee) {
                float sunNow = meter(p);
                m *= 0.6f + 0.8f * sunNow;
                ServerLevel sl = p.serverLevel();
                if (sunNow >= 0.3f) {   // Kindled Edge
                    t.setSecondsOnFire(3);
                    FxSend.slash(sl, HEAT, mid(t), p.getLookAngle(), new Vec3(0, 1, 0), 1.1f, 1.6f, 6, 0);
                    play(sl, t.position(), "meridian_bite", 0.9f, 1f);
                }
                if (zenith(p) && !cleaving) cleave(p, t, e.getAmount() * m * 0.6f);
            }
            float out = e.getAmount() * m;
            if (t instanceof AddonBoss) out = Math.min(out, Math.max(e.getAmount(), t.getMaxHealth() * 0.15f));   // never past a boss's per-blow cap
            e.setAmount(out);
        }
    }

    private static boolean cleaving;

    /** In Zenith, Dayreaver's blows cleave: everyone else in a wide arc in front takes a share. */
    static void cleave(ServerPlayer p, LivingEntity hit, float dmg) {
        ServerLevel sl = p.serverLevel();
        Vec3 look = flat(p.getLookAngle()).normalize();
        FxSend.slash(sl, SUN, p.position().add(0, 1.1, 0), look, new Vec3(0, 1, 0), 3.6f, 4.4f, 7, 1.6f);
        cleaving = true;
        try {
            for (LivingEntity o : enemies(sl, p, p.getBoundingBox().inflate(4.5))) {
                if (o == hit) continue;
                Vec3 to = flat(o.position().subtract(p.position()));
                if (to.lengthSqr() > 0.01 && to.normalize().dot(look) > 0.2) AstaFx.damage(p, o, dmg, "call_dayreaver");
            }
        } finally { cleaving = false; }
    }

    @SubscribeEvent
    public static void onAttacked(LivingAttackEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !e.getSource().is(DamageTypeTags.IS_FIRE) || !has(p)) return;
        if (meter(p) >= 0.5f) { e.setCanceled(true); p.clearFire(); }   // heat is home
    }

    @SubscribeEvent public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) { S.remove(e.getEntity().getUUID()); }

    /** Night pages bank heat (Banked Fire): up to 30%, given back at dawn. */
    static void bank(ServerPlayer p) { State s = st(p); s.banked = Math.min(0.3f, s.banked + 0.03f); }

    // ================================================================== day pages
    /** Hearth Star: a small sun before you that goes where you look, burning what it touches. Its size follows the meter. */
    public static void hearthStar(ServerPlayer p, float power) {
        State s = st(p);
        ServerLevel sl = p.serverLevel();
        Vec3 at = s.star != null ? s.star : p.getEyePosition().add(p.getLookAngle().scale(2.5));
        star(p, at);
        s.starUntil = sl.getGameTime() + 400;
        ModeHud.show(p, "hearth_star", 400);
        s.starSize = 0.35f + 1.4f * Math.min(1f, meter(p));
        if (zenith(p)) s.starSize = 2.2f;
        play(sl, at, "meridian_star", 1.1f, 1.2f - s.starSize * 0.2f);
        FxSend.ring(sl, SUN, at, 0.1f, s.starSize + 0.6f, 0.08f, 8);
    }

    static void star(Player p, Vec3 at) { st(p).star = at; }

    static void starTick(ServerPlayer p, State s) {
        ServerLevel sl = p.serverLevel();
        long now = sl.getGameTime();
        if (now >= s.starUntil || p.level() != sl) { s.star = null; return; }
        // drift toward what you look at: a foe in your sights, else the point you look at (14 blocks, stopped by walls)
        Vec3 eye = p.getEyePosition();
        LivingEntity sighted = aimed(p, 14, 8);
        Vec3 aim = sighted != null ? mid(sighted) : clip(sl, p, eye, eye.add(p.getLookAngle().scale(14)));
        Vec3 to = aim.subtract(s.star);
        double step = Math.min(to.length(), 0.45);
        if (step > 0.01) s.star = s.star.add(to.normalize().scale(step));
        if (now % 2 == 0) {
            Kit.orb(sl, SUN, s.star, Vec3.ZERO, s.starSize, 3);
            Kit.orb(sl, WHITE, s.star, Vec3.ZERO, s.starSize * 0.55f, 3);
        }
        if (now % 10 == 0) {
            FxSend.ring(sl, HEAT, s.star, s.starSize * 0.8f, s.starSize + 0.4f, 0.04f, 8);
            float dmg = (2f + 6f * Math.min(1.25f, meter(p))) * dawnbreak(p);
            for (LivingEntity t : enemies(sl, p, new AABB(s.star, s.star).inflate(s.starSize + 0.7))) {
                if (mid(t).distanceTo(s.star) > s.starSize + 1.0) continue;
                AstaFx.damage(p, t, dmg, "hearth_star");
                t.setSecondsOnFire(2);
            }
        }
    }

    /** Haughty Flare: the star swells and bursts. Without a star, a smaller flare where you look. */
    public static void haughtyFlare(ServerPlayer p, float power) {
        State s = st(p);
        ServerLevel sl = p.serverLevel();
        boolean big = s.star != null;
        Vec3 eye = p.getEyePosition();
        Vec3 c = big ? s.star : clip(sl, p, eye, eye.add(p.getLookAngle().scale(10)));
        float m = Math.min(1.25f, meter(p));
        float R = big ? 2.5f + 2.6f * s.starSize : 2.5f;
        float dmg = (big ? 8f + 18f * m : 5f + 8f * m) * dawnbreak(p) * (zenith(p) ? 1.5f : 1f);
        s.star = null;
        Vec3 g = BossKit.ground(sl, c.add(0, 1, 0));
        ZenonAI.warnCircle(sl, g, R, 20);
        play(sl, c, "meridian_flare", 1.6f, 1f);
        for (int k = 0; k < 20; k += 4) {
            final float f = k / 20f;
            AstaFx.later(k, () -> { Kit.orb(sl, SUN, c, Vec3.ZERO, (big ? 1.2f : 0.6f) + f * (big ? 2.2f : 1f), 5); Kit.orb(sl, WHITE, c, Vec3.ZERO, 0.6f + f, 5); });
        }
        AstaFx.later(20, () -> {
            if (!alive(p)) return;
            FxSend.ring(sl, SUN, c, 0.3f, R, 0.2f, 10);
            FxSend.ring(sl, HEAT, g.add(0, 0.1, 0), 0.3f, R + 0.5f, 0.12f, 12);
            FxSend.shake(sl, c, big ? 0.6f : 0.3f, 8, 24);
            for (LivingEntity t : enemies(sl, p, new AABB(c, c).inflate(R))) {
                if (mid(t).distanceTo(c) > R + 0.5) continue;
                AstaFx.damage(p, t, dmg, "haughty_flare");
                t.setSecondsOnFire(4);
                Kit.push(t, mid(t).subtract(c), 1.0, 0.35);
            }
        });
    }

    /** Call Dayreaver: the great sun-axe, in your main hand (what you held goes to your pack). It leaves when you let go. */
    public static void callDayreaver(ServerPlayer p, float power) {
        ItemStack main = p.getMainHandItem();
        if (main.is(AddonItemRegistry.DAYREAVER.get())) return;
        if (!main.isEmpty()) {
            ItemStack moved = main.copy();
            if (!p.getInventory().add(moved)) {
                p.displayClientMessage(Component.literal("No room in your pack for what you're holding.").withStyle(ChatFormatting.GRAY), true);
                return;
            }
        }
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(AddonItemRegistry.DAYREAVER.get()));
        ServerLevel sl = p.serverLevel();
        play(sl, p.position(), "meridian_reaver", 1.2f, 0.8f);
        FxSend.ring(sl, SUN, p.position().add(0, 1.2, 0), 0.2f, 1.6f, 0.08f, 8);
    }

    /** Noonwave: a cone of heat thrown forward - it burns and throws foes back, harder as the sun climbs. */
    public static void noonwave(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        float m = Math.min(1.25f, meter(p));
        float dmg = (4f + 10f * m) * dawnbreak(p) * (zenith(p) ? 1.5f : 1f);
        Vec3 look = flat(p.getLookAngle()).normalize(), at = p.position().add(0, 1.0, 0);
        play(sl, at, "meridian_wave", 1.4f, 1f);
        for (int i = 0; i < 4; i++) {
            final int k = i;
            AstaFx.later(i * 2, () -> FxSend.wall(sl, k % 2 == 0 ? SUN : HEAT, at, look, new Vec3(0, 1, 0), 1.5f + k * 1.8f, 2.2f + k * 1.8f, -35, 35, 1.6f, 8, 0, 0));
        }
        double cos = Math.cos(Math.toRadians(35));
        for (LivingEntity t : enemies(sl, p, p.getBoundingBox().inflate(8.5))) {
            Vec3 to = flat(t.position().subtract(p.position()));
            if (to.length() > 8.5 || to.lengthSqr() < 1e-4 || to.normalize().dot(look) < cos) continue;
            AstaFx.damage(p, t, dmg, "noonwave");
            if (m >= 0.3f) t.setSecondsOnFire(3);
            Kit.push(t, to, 1.2, 0.35);
        }
    }

    // ================================================================== night pages
    /** Lamplight: a small lamp-flame bolt. Humble, cheap. */
    public static void lamplight(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        bank(p);
        play(sl, p.position(), "meridian_lamp", 0.8f, 1f);
        Vec3 from = p.getEyePosition().add(p.getLookAngle().scale(0.6)), dir = p.getLookAngle();
        float dmg = 3f + 4f * night(p);
        lampStep(p, sl, from, dir, 0, dmg);
    }

    static void lampStep(ServerPlayer p, ServerLevel sl, Vec3 at, Vec3 dir, int n, float dmg) {
        if (n > 20 || !alive(p)) return;
        Vec3 next = at.add(dir.scale(1.1));
        Vec3 wall = clip(sl, p, at, next);
        Kit.orb(sl, EMBER, at, dir.scale(1.1), 0.25f, 2);
        for (LivingEntity t : enemies(sl, p, new AABB(at, next).inflate(0.5))) {
            if (mid(t).distanceTo(closestOnSegment(at, next, mid(t))) > 0.9) continue;
            AstaFx.damage(p, t, dmg, "lamplight");
            t.setSecondsOnFire(1);
            FxSend.ring(sl, EMBER, mid(t), 0.1f, 0.6f, 0.04f, 6);
            return;
        }
        if (wall.distanceToSqr(next) > 1e-4) return;
        AstaFx.later(1, () -> lampStep(p, sl, next, dir, n + 1, dmg));
    }

    /** Warm Hands: a gentle heal for you, and for the most hurt friend within 8 blocks. */
    public static void warmHands(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        bank(p);
        float heal = 3f + 3f * night(p);
        p.heal(heal);
        Player friend = null;
        for (Player o : sl.getEntitiesOfClass(Player.class, p.getBoundingBox().inflate(8), o -> o != p && o.isAlive() && !o.isSpectator() && (p.getTeam() == null || p.isAlliedTo(o))))
            if (friend == null || o.getHealth() / o.getMaxHealth() < friend.getHealth() / friend.getMaxHealth()) friend = o;
        if (friend != null) { friend.heal(heal); FxSend.ring(sl, EMBER, friend.position().add(0, 0.1, 0), 0.3f, 1.0f, 0.05f, 12); }
        FxSend.ring(sl, EMBER, p.position().add(0, 0.1, 0), 0.3f, 1.2f, 0.06f, 14);
        play(sl, p.position(), "meridian_warm", 1f, 1f);
    }

    /** Last Coal: a shield that starts small and grows each second you stand still (15 s). */
    public static void lastCoal(ServerPlayer p, float power) {
        bank(p);
        State s = st(p);
        s.coalUntil = p.level().getGameTime() + 300;
        ModeHud.show(p, "last_coal", 300);
        p.setAbsorptionAmount(Math.max(p.getAbsorptionAmount(), 2f));
        play(p.serverLevel(), p.position(), "meridian_coal", 1f, 1f);
        FxSend.ring(p.serverLevel(), EMBER, p.position().add(0, 0.1, 0), 0.9f, 1.1f, 0.05f, 20);
    }

    // ================================================================== Zenith
    /** Zenith Unbound: around noon, 60 s at the peak. Any other time, once a day: Borrowed Noon (30 s, stronger, for 30% max health over 10 minutes). */
    public static void zenithUnbound(ServerPlayer p, float power) {
        State s = st(p);
        ServerLevel sl = p.serverLevel();
        long now = sl.getGameTime();
        if (zenith(p)) return;
        boolean noon = noonWindow(p);
        if (!noon) {
            if (s.borrowDay == today(sl)) { p.displayClientMessage(Component.literal("You've already borrowed today's noon.").withStyle(ChatFormatting.GRAY), true); return; }
            s.borrowDay = today(sl);
            s.borrowed = true;
            TimedModifiers.apply(p, Attributes.MAX_HEALTH, BORROW_HP, "Borrowed Noon", -0.3, AttributeModifier.Operation.MULTIPLY_TOTAL, BORROW_COST_TICKS);
            ModeHud.show(p, "borrowed_noon", BORROW_COST_TICKS);
            if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
            play(sl, p.position(), "meridian_borrow", 1.6f, 1f);
            say(p, BORROW, true);
        } else {
            s.borrowed = false;
            play(sl, p.position(), "meridian_zenith", 1.8f, 1f);
            say(p, NOON, true);
        }
        int len = noon ? ZENITH_TICKS : BORROW_TICKS;
        s.zenithUntil = now + len;
        ModeHud.show(p, "zenith_unbound", len);
        s.climb = 0.25f;
        TimedModifiers.apply(p, Attributes.ATTACK_DAMAGE, ZENITH_DMG, "Zenith", noon ? 0.6 : 0.8, AttributeModifier.Operation.MULTIPLY_TOTAL, len);
        if (s.star != null) s.starSize = 2.2f;
        Vec3 c = p.position();
        FxSend.ring(sl, SUN, c.add(0, 0.1, 0), 0.3f, 7f, 0.2f, 16);
        FxSend.beam(sl, WHITE, c, c.add(0, 12, 0), 0.6f, 16);
        FxSend.shake(sl, c, 0.5f, 10, 24);
        sync(p);
    }

    // ================================================================== test hooks
    public static void testState(ServerPlayer p, float climb, float banked, int pride) { State s = st(p); s.climb = climb; s.banked = banked; s.pride = pride; }
    public static int testPride(Player p) { return st(p).pride; }
    public static float testBanked(Player p) { return st(p).banked; }
    public static boolean testStar(Player p) { return st(p).star != null; }
    public static void testCombat(Player p) { st(p).lastCombat = p.level().getGameTime(); }
    public static void testReset(Player p) { S.remove(p.getUUID()); }
    public static long testBorrowDay(Player p) { return st(p).borrowDay; }
}
