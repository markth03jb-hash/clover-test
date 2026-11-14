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
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Weather Grimoire (an original grimoire): you carry your own sky. Every spell changes your personal forecast -
 * Sun, Rain, Storm, Hail, Wind or Fog - and a little cloud of it hangs over your head for everyone to see; the
 * forecast gives its own gift, and the last page brings down a catastrophe of whatever weather you're in. None of it
 * touches the world's real weather or its blocks: it's your sky, drawn round you.
 * Drawn by client/WeatherFxClient (FxPacket shape 95).
 *
 *  1 Sunbeam          (Sun)   - a ray of hard sunlight burns down from the sky on what you aim at, following your aim
 *  2 Downpour         (Rain)  - a raincloud over where you aim: foes under it are soaked and slowed, you're refreshed
 *  3 Thunderclap      (Storm) - lightning on the foe you aim at - and on every soaked foe near it (soaked: +50%)
 *  4 Hailstorm        (Hail)  - hail hammers an area for three seconds, slowing more with each stone
 *  5 Gale             (Wind)  - a wall of wind blows foes back and turns their arrows and fireballs round
 *  6 Fog Bank         (Fog)   - 6 s: a fog rolls out round you; foes in it lose sight of you, and you take 25% less
 *  7 Cyclone          (Wind)  - a whirlwind crawls forward, sucking foes in and throwing them up
 *  8 Rainbow          (Sun)   - you and your allies near you are healed and cleansed (more, after rain); foes under
 *                               its arc are dazzled, hitting 20% weaker
 *  9 Eye of the Storm (Storm) - 20 s mode: a ring of storm turns round you, striking foes at its edge with lightning,
 *    (mode)                     while it's calm in the middle - you're steadier and Weather spells hit 20% harder
 * 10 Cataclysm        - whatever your forecast is, at its worst: Sun - a heatwave; Rain - a deluge; Storm - a
 *                       thunderstorm; Hail - a blizzard; Wind - a great tornado; Fog - a whiteout.
 *                       Surpassing Limits: larger.
 *
 * Passive "Forecast": Sun - Weather spells hit 15% harder; Rain - every hit refreshes you a little and soaks the foe;
 * Storm - a hit may call down a spare bolt; Hail - hits chill (slow); Wind - hits knock back and you move quicker;
 * Fog - you take 15% less.
 */
@Mod.EventBusSubscriber(modid = "iron_spell_grimoire")
public final class WeatherFx {
    private WeatherFx() {}

    public static final int SUN = 0, RAIN = 1, STORM = 2, HAIL = 3, WIND = 4, FOG = 5;
    static final String[] NAMES = {"Sun", "Rain", "Storm", "Hail", "Wind", "Fog"};
    private static final ChatFormatting[] COLS = {ChatFormatting.GOLD, ChatFormatting.BLUE, ChatFormatting.YELLOW, ChatFormatting.AQUA, ChatFormatting.GREEN, ChatFormatting.GRAY};
    public static final String MODE = "eye_of_the_storm", FOG_BANK = "fog_bank";
    private static final String KEY = "isg_weather_forecast", PREV = "isg_weather_prev";
    private static final UUID SOAK = UUID.fromString("b9f0c7a2-14e3-4a55-9d61-3e7a20000001");
    private static final UUID CHILL = UUID.fromString("b9f0c7a2-14e3-4a55-9d61-3e7a20000002");
    private static final UUID DAZZLE = UUID.fromString("b9f0c7a2-14e3-4a55-9d61-3e7a20000003");
    private static final UUID TAILWIND = UUID.fromString("b9f0c7a2-14e3-4a55-9d61-3e7a20000004");
    private static final UUID BLIZ = UUID.fromString("b9f0c7a2-14e3-4a55-9d61-3e7a20000005");
    private static final UUID BLIZ_A = UUID.fromString("b9f0c7a2-14e3-4a55-9d61-3e7a20000007");
    private static final UUID WHITE = UUID.fromString("b9f0c7a2-14e3-4a55-9d61-3e7a20000006");
    private static final double TAU = Math.PI * 2;
    private static final Map<UUID, Long> WET = new HashMap<>();
    private static final Map<UUID, Integer> HAIL_N = new HashMap<>();

    // ================================================================== the client shapes (WeatherFxClient, shape 95)
    private static FxPacket pk(int kind, Vec3 c, int life) {
        FxPacket p = new FxPacket();
        p.shape = 95; p.id = FxSend.nextId(); p.h = kind; p.c = c; p.life = life; p.a1 = (float) (Math.random() * 1000);
        return p;
    }
    private static void send(ServerLevel sl, FxPacket p) { FxSend.sendNear(sl, p.c, p); }

    /** 0 a sunbeam onto c (radius r). */
    static void sunbeam(ServerLevel sl, Vec3 c, float r, int life) { FxPacket p = pk(0, c, life); p.r = r; send(sl, p); }
    /** 1 a raincloud over c (radius r). */
    static void raincloud(ServerLevel sl, Vec3 c, float r, int life) { FxPacket p = pk(1, c, life); p.r = r; send(sl, p); }
    /** 2 a lightning bolt from the sky onto c. */
    static void bolt(ServerLevel sl, Vec3 c, float size) { FxPacket p = pk(2, c, 12); p.r = size; send(sl, p); }
    /** 3 hail on c (radius r). */
    static void hail(ServerLevel sl, Vec3 c, float r, int life) { FxPacket p = pk(3, c, life); p.r = r; send(sl, p); }
    /** 4 a gale from c along d. */
    static void gale(ServerLevel sl, Vec3 c, Vec3 d, float len, int life) { FxPacket p = pk(4, c, life); p.d = d; p.r = len; send(sl, p); }
    /** 5 a fog bank round a player. */
    static void fog(ServerLevel sl, ServerPlayer pl, float r, int life) { FxPacket p = pk(5, pl.position(), life); p.v = new Vec3(pl.getId(), 1, 0); p.r = r; send(sl, p); }
    /** 6 a cyclone from c to u. */
    static void cyclone(ServerLevel sl, Vec3 c, Vec3 u, float r, int life) { FxPacket p = pk(6, c, life); p.u = u; p.r = r; send(sl, p); }
    /** 7 a rainbow arched over c along d. */
    static void rainbow(ServerLevel sl, Vec3 c, Vec3 d, float r, int life) { FxPacket p = pk(7, c, life); p.d = d; p.r = r; send(sl, p); }
    /** 8 the eye of the storm round a player. */
    static void eye(ServerLevel sl, ServerPlayer pl, float r, int life) { FxPacket p = pk(8, pl.position(), life); p.v = new Vec3(pl.getId(), 1, 0); p.r = r; send(sl, p); }
    /** 9 the cataclysm of forecast {@code f} at c. */
    static void cataclysm(ServerLevel sl, Vec3 c, int f, float r, int life) { FxPacket p = pk(9, c, life); p.a0 = f; p.r = r; send(sl, p); }
    /** 10 the little personal cloud over a player; a0 = forecast. */
    static void personal(ServerLevel sl, ServerPlayer pl, int f, int life) { FxPacket p = pk(10, pl.position(), life); p.v = new Vec3(pl.getId(), 1, 0); p.a0 = f; send(sl, p); }
    /** 11 a splash / soak mark on c. */
    static void splash(ServerLevel sl, Vec3 c, float r) { FxPacket p = pk(11, c, 12); p.r = r; send(sl, p); }

    // ================================================================== the forecast
    public static int forecast(ServerPlayer p) { return Math.max(0, Math.min(5, p.getPersistentData().getInt(KEY))); }

    static void setForecast(ServerPlayer p, int f) {
        int was = forecast(p);
        if (was != f) p.getPersistentData().putInt(PREV, was);
        p.getPersistentData().putInt(KEY, f);
        if (was != f) {
            p.displayClientMessage(Component.literal("Forecast: " + NAMES[f]).withStyle(COLS[f]), true);
            personal(p.serverLevel(), p, f, 44);
        }
        sync(p);
    }

    static int previous(ServerPlayer p) { return p.getPersistentData().getInt(PREV); }

    /** The forecast on the player's screen (WeatherFxClient, kind 99): a0 = forecast, r = 1 if equipped. */
    static void sync(ServerPlayer p) {
        if (p.connection == null) return;
        FxPacket h = new FxPacket();
        h.shape = 95; h.h = 99; h.a0 = forecast(p) + 0.001f; h.r = equipped(p) ? 1 : 0; h.life = 1;
        com.ironspellgrimoire.network.AddonNetwork.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> p), h);
    }

    public static boolean equipped(ServerPlayer p) {
        var b = io.redspace.ironsspellbooks.api.util.Utils.getPlayerSpellbookStack(p);
        return b != null && b.is(com.ironspellgrimoire.registry.AddonItemRegistry.WEATHER_GRIMOIRE.get());
    }

    static boolean eyeOfStorm(ServerPlayer p) { return Moves.inMode(p, MODE); }

    static void play(ServerLevel sl, Vec3 at, String kind, float vol, float pitch) {
        SoundEvent s = AddonSounds.get(kind);
        if (s != null) sl.playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, vol, pitch);
    }

    static boolean wet(LivingEntity t) { Long w = WET.get(t.getUUID()); return w != null && w > t.level().getGameTime(); }

    static void soak(LivingEntity t, int ticks) {
        WET.put(t.getUUID(), t.level().getGameTime() + ticks);
        TimedModifiers.apply(t, Attributes.MOVEMENT_SPEED, SOAK, "Soaked", -0.2, AttributeModifier.Operation.MULTIPLY_TOTAL, ticks);
        if (WET.size() > 300) { long now = t.level().getGameTime(); WET.entrySet().removeIf(en -> en.getValue() < now); }
    }

    /** A Weather hit, with the forecast's gift. */
    static void hit(ServerPlayer p, LivingEntity t, float amt, String id) {
        int f = forecast(p);
        if (f == SUN) amt *= 1.15f;
        if (eyeOfStorm(p)) amt *= 1.2f;
        AstaFx.damage(p, t, amt, id);
        ServerLevel sl = p.serverLevel();
        switch (f) {
            case RAIN -> { p.heal(0.5f); soak(t, 100); }
            case STORM -> {
                if (p.getRandom().nextFloat() < 0.2f) {
                    final LivingEntity tt = t;
                    final float extra = amt * 0.3f;
                    AstaFx.later(6, () -> {
                        if (!alive(p) || !tt.isAlive()) return;
                        bolt(sl, tt.position(), 0.6f);
                        AstaFx.damage(p, tt, extra, "forecast_bolt");
                        tt.invulnerableTime = 0;
                        play(sl, tt.position(), "thunder", 0.6f, 1.4f);
                    });
                }
            }
            case HAIL -> TimedModifiers.apply(t, Attributes.MOVEMENT_SPEED, CHILL, "Chilled by hail", -0.15, AttributeModifier.Operation.MULTIPLY_TOTAL, 30);
            case WIND -> Kit.push(t, flat(t.position().subtract(p.position())), 0.35, 0.1);
            default -> {}
        }
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        if (Moves.inMode(p, FOG_BANK)) e.setAmount(e.getAmount() * 0.75f);
        if (equipped(p) && forecast(p) == FOG) e.setAmount(e.getAmount() * 0.85f);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        if (p.tickCount % 40 == 9 && equipped(p)) {
            sync(p);
            personal(p.serverLevel(), p, forecast(p), 44);   // your little sky, for everyone to see
            if (forecast(p) == WIND) TimedModifiers.apply(p, Attributes.MOVEMENT_SPEED, TAILWIND, "Tailwind", 0.1, AttributeModifier.Operation.MULTIPLY_TOTAL, 45);
        }
        if (p.tickCount % 5 == 0 && !equipped(p)) {   // switched to another grimoire: its weather goes with the book
            if (Moves.inMode(p, MODE)) Moves.ends(p, MODE);
            if (Moves.inMode(p, FOG_BANK)) Moves.ends(p, FOG_BANK);
        }
        if (Moves.inMode(p, FOG_BANK) && p.tickCount % 10 == 0) {   // lost in the fog
            for (LivingEntity t : Kit.near(p.serverLevel(), p, p.position().add(0, 1, 0), 7)) if (t instanceof Mob mob && mob.getTarget() == p) mob.setTarget(null);
        }
    }

    private static Vec3 look(ServerPlayer p) {
        Vec3 l = flat(p.getLookAngle());
        return l.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : l.normalize();
    }

    // ================================================================== 1. Sunbeam
    public static void sunbeamSpell(ServerPlayer p, float power) {
        setForecast(p, SUN);
        ServerLevel sl = p.serverLevel();
        play(sl, p.position(), "sun_chime", 1.0f, 1.0f);
        for (int i = 0; i < 6; i++) {   // the ray follows your aim for a second and a half
            final int k = i;
            AstaFx.later(i * 5, () -> {
                if (!alive(p)) return;
                Vec3 g = InkFx.target(p, 26);
                sunbeam(sl, g, 1.6f, 7);
                for (LivingEntity t : Kit.near(sl, p, g.add(0, 1, 0), 1.9)) { hit(p, t, power * 0.3f, "sunbeam"); t.invulnerableTime = 0; }
                if (k % 2 == 0) play(sl, g, "sun_chime", 0.5f, 1.4f);
            });
        }
    }

    // ================================================================== 2. Downpour
    public static void downpour(ServerPlayer p, float power) {
        setForecast(p, RAIN);
        ServerLevel sl = p.serverLevel();
        final Vec3 g = InkFx.target(p, 20);
        final float R = 4.5f;
        final int T = 100;
        raincloud(sl, g, R, T);
        play(sl, g, "rain", 1.2f, 1.0f);
        for (int i = 0; i < T / 20; i++) {
            AstaFx.later(i * 20 + 4, () -> {
                if (!alive(p)) return;
                for (LivingEntity t : Kit.near(sl, p, g.add(0, 1, 0), R)) { soak(t, 120); hit(p, t, power * 0.18f, "downpour"); }
                if (p.position().distanceTo(g) < R + 0.5) p.heal(1f);
            });
        }
    }

    // ================================================================== 3. Thunderclap
    public static void thunderclap(ServerPlayer p, float power) {
        setForecast(p, STORM);
        ServerLevel sl = p.serverLevel();
        LivingEntity t = aimed(p, 28, 10);
        Vec3 g = t != null ? t.position() : InkFx.target(p, 24);
        bolt(sl, g, 1.2f);
        play(sl, g, "thunder", 1.4f, 1.0f);
        if (t != null) hit(p, t, power * (wet(t) ? 1.5f : 1f) * 1.15f, "thunderclap");
        for (LivingEntity o : Kit.near(sl, p, g.add(0, 1, 0), 2.2)) if (o != t) hit(p, o, power * 0.6f, "thunderclap");
        int n = 0;
        for (LivingEntity o : Kit.near(sl, p, g.add(0, 1, 0), 12)) {   // the lightning finds everything that's soaked
            if (o == t || !wet(o) || n >= 6) continue;
            final LivingEntity oo = o;
            AstaFx.later(3 + n * 2, () -> {
                if (!alive(p) || !oo.isAlive()) return;
                bolt(sl, oo.position(), 0.8f);
                hit(p, oo, power * 0.9f * 1.5f, "thunderclap");
                oo.invulnerableTime = 0;
                play(sl, oo.position(), "thunder", 0.7f, 1.3f);
            });
            n++;
        }
        FxSend.shake(sl, g, 0.3f, 4, 16);
    }

    // ================================================================== 4. Hailstorm
    public static void hailstorm(ServerPlayer p, float power) {
        setForecast(p, HAIL);
        ServerLevel sl = p.serverLevel();
        final Vec3 g = InkFx.target(p, 20);
        final float R = 4.5f;
        hail(sl, g, R, 70);
        play(sl, g, "hail", 1.2f, 1.0f);
        for (int i = 0; i < 6; i++) {
            AstaFx.later(8 + i * 10, () -> {
                if (!alive(p)) return;
                for (LivingEntity t : Kit.near(sl, p, g.add(0, 1, 0), R)) {
                    int n = Math.min(5, HAIL_N.merge(t.getUUID(), 1, Integer::sum));
                    TimedModifiers.apply(t, Attributes.MOVEMENT_SPEED, CHILL, "Hail-battered", -0.1 * n, AttributeModifier.Operation.MULTIPLY_TOTAL, 40);
                    hit(p, t, power * 0.25f, "hailstorm");
                    t.invulnerableTime = 0;
                }
            });
        }
        AstaFx.later(80, () -> HAIL_N.clear());
    }

    // ================================================================== 5. Gale
    public static void galeSpell(ServerPlayer p, float power) {
        setForecast(p, WIND);
        ServerLevel sl = p.serverLevel();
        Vec3 lk = look(p), c = p.position().add(0, 1, 0);
        final float LEN = 10f;
        gale(sl, p.position(), lk, LEN, 22);
        play(sl, c, "wind", 1.3f, 1.0f);
        for (LivingEntity t : Kit.along(sl, p, c, c.add(lk.scale(LEN)), 2.6)) {
            Kit.push(t, lk, 1.6 * GravityFx.give(t), 0.35);
            hit(p, t, power * 0.55f, "gale");
        }
        for (Projectile pr : sl.getEntitiesOfClass(Projectile.class, new AABB(c, c.add(lk.scale(LEN))).inflate(2.6), x -> x.getOwner() != p)) {
            Vec3 v = pr.getDeltaMovement();
            pr.setDeltaMovement(lk.scale(Math.max(0.8, v.length())));   // turned round on the wind
            pr.setOwner(p);
            pr.hurtMarked = true;
        }
    }

    // ================================================================== 6. Fog Bank
    public static void fogBank(ServerPlayer p, float power) {
        setForecast(p, FOG);
        ServerLevel sl = p.serverLevel();
        final int T = 120;
        fog(sl, p, 7f, T);
        Moves.mode(p, FOG_BANK, T, 20, pl -> {});
        for (LivingEntity t : Kit.near(sl, p, p.position().add(0, 1, 0), 7)) if (t instanceof Mob mob && mob.getTarget() == p) mob.setTarget(null);
        play(sl, p.position(), "wind", 0.8f, 0.5f);
    }

    // ================================================================== 7. Cyclone
    public static void cycloneSpell(ServerPlayer p, float power) {
        setForecast(p, WIND);
        ServerLevel sl = p.serverLevel();
        Vec3 lk = look(p);
        final Vec3 a = p.position().add(lk.scale(2)), b = p.position().add(lk.scale(14));
        final int T = 44;
        cyclone(sl, a, b, 2.4f, T);
        play(sl, a, "wind", 1.4f, 0.7f);
        for (int i = 0; i <= T; i += 4) {
            final double u = i / (double) T;
            final int k = i;
            AstaFx.later(i, () -> {
                if (!alive(p)) return;
                Vec3 c = a.add(b.subtract(a).scale(u));
                for (LivingEntity t : Kit.near(sl, p, c.add(0, 1, 0), 3.6)) {
                    Vec3 to = flat(c.subtract(t.position()));
                    Vec3 sw = new Vec3(-to.z, 0, to.x);
                    double g = GravityFx.give(t);
                    t.setDeltaMovement(to.x * 0.25 * g + sw.x * 0.2 * g, 0.28 * g, to.z * 0.25 * g + sw.z * 0.2 * g);
                    t.hurtMarked = true;
                    if (k % 12 == 0) hit(p, t, power * 0.35f, "cyclone");
                }
            });
        }
    }

    // ================================================================== 8. Rainbow
    public static void rainbowSpell(ServerPlayer p, float power) {
        boolean afterRain = forecast(p) == RAIN || forecast(p) == STORM;
        setForecast(p, SUN);
        ServerLevel sl = p.serverLevel();
        Vec3 lk = look(p), c = p.position();
        rainbow(sl, c.add(lk.scale(3)), lk, 7f, 50);
        float heal = afterRain ? 8f : 4f;
        p.heal(heal);
        TimedModifiers.clearDebuffs(p);
        for (LivingEntity o : sl.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(8), x -> ChainFx.ally(p, x))) {
            o.heal(heal);
            TimedModifiers.clearDebuffs(o);
            splash(sl, o.position(), 0.8f);
        }
        for (LivingEntity t : Kit.near(sl, p, c.add(lk.scale(3)).add(0, 1, 0), 7)) {
            TimedModifiers.apply(t, Attributes.ATTACK_DAMAGE, DAZZLE, "Dazzled by the rainbow", -0.2, AttributeModifier.Operation.MULTIPLY_TOTAL, 100);
            hit(p, t, power * 0.35f, "rainbow");
        }
        play(sl, c, "sun_chime", 1.2f, 1.2f);
        if (afterRain) p.displayClientMessage(Component.literal("After the rain - the rainbow is brighter").withStyle(ChatFormatting.LIGHT_PURPLE), true);
    }

    // ================================================================== 9. Eye of the Storm (mode)
    public static void eyeOfTheStorm(ServerPlayer p, float power) {
        setForecast(p, STORM);
        ServerLevel sl = p.serverLevel();
        final int T = 400;
        final float R = 6.5f;
        eye(sl, p, R, T);
        Moves.mode(p, MODE, T, 20, pl -> {
            ServerLevel l = pl.serverLevel();
            List<LivingEntity> ring = Kit.near(l, pl, pl.position().add(0, 1, 0), R + 2.5);
            ring.removeIf(t -> t.position().distanceTo(pl.position()) < R - 2.5);
            if (ring.isEmpty()) return;
            LivingEntity t = ring.get(pl.getRandom().nextInt(ring.size()));
            bolt(l, t.position(), 0.9f);
            hit(pl, t, power * 0.55f * (wet(t) ? 1.5f : 1f), "eye_of_the_storm");
            play(l, t.position(), "thunder", 0.8f, 1.2f);
        });
        Moves.stance(p, 0.15, 0, 0, 0.5, 0, T);
        play(sl, p.position(), "wind", 1.2f, 0.6f);
        play(sl, p.position(), "thunder", 1.0f, 0.7f);
    }

    // ================================================================== 10. Cataclysm
    public static void cataclysmSpell(ServerPlayer p, float power) {
        boolean limit = LimitBreak.active(p);
        ServerLevel sl = p.serverLevel();
        final int f = forecast(p);
        final float pow = power * (limit ? 1.3f : 1f) * (eyeOfStorm(p) ? 1.2f : 1f) * (f == SUN ? 1.15f : 1f);
        final Vec3 g = InkFx.target(p, 26);
        final float R = 7f * (limit ? 1.35f : 1f);
        final int T = 60;
        cataclysm(sl, g, f, R, T);
        p.displayClientMessage(Component.literal(new String[]{"HEATWAVE", "DELUGE", "THUNDERSTORM", "BLIZZARD", "GREAT TORNADO", "WHITEOUT"}[f]).withStyle(COLS[f], ChatFormatting.BOLD), true);
        FxSend.impactFrame(sl, g.add(0, 1, 0), 3, FxSend.FRAME_YUNO, 20);
        switch (f) {
            case SUN -> {   // three pulses of killing heat
                play(sl, g, "sun_chime", 1.6f, 0.6f);
                for (int i = 0; i < 3; i++) AstaFx.later(12 + i * 14, () -> {
                    if (!alive(p)) return;
                    for (LivingEntity t : Kit.near(sl, p, g.add(0, 1, 0), R)) { AstaFx.damage(p, t, pow * 0.6f, "cataclysm"); t.invulnerableTime = 0; }
                    FxSend.shake(sl, g, 0.3f, 4, 12);
                });
            }
            case RAIN -> {   // a wall of water crashing out from the middle
                play(sl, g, "rain", 1.8f, 0.6f);
                AstaFx.later(18, () -> {
                    if (!alive(p)) return;
                    for (LivingEntity t : Kit.near(sl, p, g.add(0, 1, 0), R)) {
                        Kit.push(t, flat(t.position().subtract(g)), 1.6 * GravityFx.give(t), 0.5);
                        soak(t, 200);
                        AstaFx.damage(p, t, pow * 1.6f, "cataclysm");
                    }
                    FxSend.shake(sl, g, 0.7f, 10, 28);
                });
            }
            case STORM -> {   // a dozen bolts all over it
                play(sl, g, "thunder", 1.8f, 0.6f);
                for (int i = 0; i < 12; i++) {
                    final int k = i;
                    AstaFx.later(6 + i * 4, () -> {
                        if (!alive(p)) return;
                        List<LivingEntity> in = Kit.near(sl, p, g.add(0, 1, 0), R);
                        Vec3 at = !in.isEmpty() && k % 2 == 0 ? in.get(p.getRandom().nextInt(in.size())).position()
                                : g.add((p.getRandom().nextDouble() * 2 - 1) * R * 0.8, 0, (p.getRandom().nextDouble() * 2 - 1) * R * 0.8);
                        bolt(sl, at, 1.1f);
                        for (LivingEntity t : Kit.near(sl, p, at.add(0, 1, 0), 2.5)) { AstaFx.damage(p, t, pow * 0.32f * (wet(t) ? 1.5f : 1f), "cataclysm"); t.invulnerableTime = 0; }
                        play(sl, at, "thunder", 1.0f, 0.8f + k * 0.04f);
                    });
                }
            }
            case HAIL -> {   // a blizzard: everything inside nearly frozen in place
                play(sl, g, "hail", 1.6f, 0.6f);
                play(sl, g, "wind", 1.4f, 0.5f);
                for (int i = 0; i < 4; i++) AstaFx.later(8 + i * 12, () -> {
                    if (!alive(p)) return;
                    for (LivingEntity t : Kit.near(sl, p, g.add(0, 1, 0), R)) {
                        TimedModifiers.apply(t, Attributes.MOVEMENT_SPEED, BLIZ, "Blizzard", -0.6, AttributeModifier.Operation.MULTIPLY_TOTAL, 100);
                        TimedModifiers.apply(t, Attributes.ATTACK_SPEED, BLIZ_A, "Blizzard", -0.4, AttributeModifier.Operation.MULTIPLY_TOTAL, 100);
                        AstaFx.damage(p, t, pow * 0.42f, "cataclysm");
                        t.invulnerableTime = 0;
                    }
                });
            }
            case WIND -> {   // a great tornado: everything pulled in, up and round, then flung
                play(sl, g, "wind", 1.8f, 0.4f);
                for (int i = 0; i < 12; i++) {
                    final int k = i;
                    AstaFx.later(i * 4, () -> {
                        if (!alive(p)) return;
                        for (LivingEntity t : Kit.near(sl, p, g.add(0, 2, 0), R + 2)) {
                            Vec3 to = flat(g.subtract(t.position()));
                            Vec3 sw = new Vec3(-to.z, 0, to.x).normalize();
                            double gg = GravityFx.give(t);
                            t.setDeltaMovement(to.x * 0.15 * gg + sw.x * 0.5 * gg, (k < 10 ? 0.35 : -0.2) * gg, to.z * 0.15 * gg + sw.z * 0.5 * gg);
                            t.hurtMarked = true;
                            if (k % 3 == 0) { AstaFx.damage(p, t, pow * 0.4f, "cataclysm"); t.invulnerableTime = 0; }
                        }
                    });
                }
            }
            default -> {   // a whiteout: nothing can see you, and everything in it is laid bare
                play(sl, g, "wind", 1.6f, 0.3f);
                AstaFx.later(16, () -> {
                    if (!alive(p)) return;
                    for (LivingEntity t : Kit.near(sl, p, g.add(0, 1, 0), R)) {
                        if (t instanceof Mob mob) mob.setTarget(null);
                        TimedModifiers.apply(t, Attributes.ARMOR, WHITE, "Lost in the whiteout", -0.4, AttributeModifier.Operation.MULTIPLY_TOTAL, 120);
                        AstaFx.damage(p, t, pow * 1.5f, "cataclysm");
                    }
                });
            }
        }
    }

    /** Surpassing Limits: the sky round you turns over every weather at once. */
    public static void awaken(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        bolt(sl, p.position().add(3, 0, 0), 1f);
        bolt(sl, p.position().add(-3, 0, 0), 1f);
        raincloud(sl, p.position(), 3f, 30);
        personal(sl, p, forecast(p), 44);
        play(sl, p.position(), "thunder", 1.2f, 0.7f);
    }
}
