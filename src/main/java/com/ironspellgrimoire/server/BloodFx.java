package com.ironspellgrimoire.server;

import static com.ironspellgrimoire.server.FxUtil.*;

import com.ironspellgrimoire.network.FxPacket;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
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
 *  9 Exsanguinate      - blood is torn out of every foe around you and pours into you
 * 10 Scarlet Moon (mode) - 30 s: a white aura blazes round you. Blood spells hit
 *                       35% harder and heal you for a quarter of what they deal; you move faster and hold your ground.
 *
 * Passive "Blood Price": up to +80% power as your health runs low.
 * Everything is drawn as liquid blood by client/BloodFxClient (no shared effect shapes).
 */
@Mod.EventBusSubscriber(modid = "iron_spell_grimoire")
public final class BloodFx {
    private BloodFx() {}

    public static final String MODE = "scarlet_moon";
    private static final double TAU = Math.PI * 2;
    /** Hemorrhage marks: target -> until (game time). */
    private static final Map<UUID, Long> MARKED = new HashMap<>();
    private static final Map<UUID, Boolean> PACT = new HashMap<>();

    // ================================================================== the price, the power
    private static boolean moon(ServerPlayer p) { return Moves.inMode(p, MODE); }

    /** Pays the spell's price in health (a share of max health; double under the Scarlet Moon). Never kills. */
    private static void price(ServerPlayer p, float share) {
        float cost = p.getMaxHealth() * share * (moon(p) ? 2f : 1f);
        if (p.isCreative()) return;
        p.setHealth(Math.max(1f, p.getHealth() - cost));
    }

    /** The spell's power: higher the lower your health, higher still with a Pact or the Moon. */
    private static float pw(ServerPlayer p, float power) {
        float missing = 1f - p.getHealth() / Math.max(1f, p.getMaxHealth());
        float k = 1f + 0.8f * missing;
        if (moon(p)) k *= 1.35f;
        if (PACT.remove(p.getUUID()) != null) {
            k *= 1.6f;
            spray(p.serverLevel(), p.position().add(0, 1.1, 0), new Vec3(0, 0.12, 0), 0.1f, 10, 0.06f);
        }
        return power * k;
    }

    private static void hit(ServerPlayer p, LivingEntity t, float amt, String id) {
        if (MARKED.getOrDefault(t.getUUID(), 0L) > p.serverLevel().getGameTime()) amt *= 1.25f;
        float before = t.getHealth();
        AstaFx.damage(p, t, amt, id);
        if (moon(p)) {   // under the Moon, blood spilled comes back to you
            float dealt = Math.max(0f, before - t.getHealth());
            if (dealt > 0) p.heal(dealt * 0.25f);
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

    private static void bleed(ServerLevel sl, LivingEntity t, int life) {
        FxPacket p = fx(11, t.position(), life);
        p.v = new Vec3(t.getId(), 0, 0);
        send(sl, p);
    }

    // ================================================================== 1. Crimson Edge
    public static void crimsonEdge(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        price(p, 0.02f);
        float pow = pw(p, power);
        Vec3 look = flat(p.getLookAngle());
        if (look.lengthSqr() < 1e-4) look = new Vec3(0, 0, 1);
        look = look.normalize();
        Vec3 c = p.position().add(0, 1.1, 0).add(look.scale(0.4));
        FxPacket b = fx(2, c, 16);   // a crescent of liquid blood swung round, flinging drops, then falling apart
        b.d = look; b.u = new Vec3(0, 1, 0).add(rightOf(look).scale(0.25)).normalize(); b.r = 3.0f; b.speed = 5; b.a1 = moon(p) ? 1 : 0;
        send(sl, b);
        for (LivingEntity e : enemies(sl, p, new AABB(c, c).inflate(3.6))) {
            Vec3 to = flat(mid(e).subtract(p.position()));
            if (to.lengthSqr() < 1e-4 || to.normalize().dot(look) < 0.2) continue;
            hit(p, e, pow, "crimson_edge");
            spray(sl, mid(e), look.scale(0.12).add(0, 0.08, 0), 0.07f, 8, 0.07f);
        }
        sound(sl, c, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, 0.7f);
        sound(sl, c, SoundEvents.HONEY_BLOCK_BREAK, 0.8f, 0.6f);
    }

    // ================================================================== 2. Hemorrhage
    public static void hemorrhage(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        LivingEntity t = aimed(p, 24, 14);
        if (t == null) { p.displayClientMessage(Component.literal("No one to mark.").withStyle(ChatFormatting.GRAY), true); return; }
        price(p, 0.03f);
        float pow = pw(p, power);
        long until = sl.getGameTime() + 160;
        MARKED.put(t.getUUID(), until);
        Vec3 from = p.getEyePosition().subtract(0, 0.2, 0);
        int flight = Math.max(2, (int) (from.distanceTo(mid(t)) / 3.0));
        needle(sl, from, mid(t), 0.9f, flight);   // one needle of hardened blood, and the wound it leaves keeps bleeding
        AstaFx.later(flight, () -> {
            if (!t.isAlive()) return;
            bleed(sl, t, 160);
            spray(sl, mid(t), p.getLookAngle().scale(0.1), 0.06f, 10, 0.06f);
        });
        sound(sl, mid(t), SoundEvents.HONEY_DRINK, 1.2f, 0.5f);
        for (int k = 20; k <= 160; k += 20) {   // it bleeds: a little every second
            AstaFx.later(k, () -> {
                if (!t.isAlive() || !alive(p)) return;
                hit(p, t, pow * 0.22f, "hemorrhage");
                spray(sl, mid(t), Vec3.ZERO, 0.05f, 3, 0.05f);
            });
        }
    }

    // ================================================================== 3. Sanguine Volley
    public static void sanguineVolley(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        price(p, 0.03f);
        float pow = pw(p, power);
        Vec3 eye = p.getEyePosition().subtract(0, 0.2, 0), look = p.getLookAngle();
        int n = moon(p) ? 11 : 7;
        for (int i = 0; i < n; i++) {
            double a = (i - (n - 1) / 2.0) * 0.12;
            Vec3 d = rotY(look, a).normalize();
            final boolean[] spent = {false};
            final int[] id = {0};
            final int delay = i / 3;
            AstaFx.later(delay, () -> { if (alive(p)) id[0] = needle(sl, eye, eye.add(d.scale(2.2 * 9)), 0.7f, 9); });
            for (int k = 0; k < 9; k++) {
                final int kk = k;
                AstaFx.later(k + delay, () -> {
                    if (spent[0] || !alive(p)) return;
                    Vec3 a0 = eye.add(d.scale(2.2 * kk)), a1 = eye.add(d.scale(2.2 * (kk + 1)));
                    for (LivingEntity e : Kit.along(sl, p, a0, a1, 0.6)) {
                        hit(p, e, pow * 0.35f, "sanguine_volley");
                        spray(sl, mid(e), d.scale(0.1), 0.05f, 4, 0.05f);
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
        ServerLevel sl = p.serverLevel();
        LivingEntity t = aimed(p, 16, 16);
        if (t == null) { p.displayClientMessage(Component.literal("No one to draw from.").withStyle(ChatFormatting.GRAY), true); return; }
        price(p, 0.02f);
        float pow = pw(p, power);
        sound(sl, p.position(), SoundEvents.HONEY_DRINK, 1.2f, 0.4f);
        FxPacket th = fx(8, p.position(), 42);   // a thread of blood from it to you, beads of its blood drawn along it
        th.v = new Vec3(p.getId(), t.getId(), 0); th.u = mid(t);
        send(sl, th);
        for (int k = 0; k < 40; k += 10) {
            AstaFx.later(k, () -> {
                if (!alive(p) || !t.isAlive() || t.distanceTo(p) > 20) return;
                float before = t.getHealth();
                hit(p, t, pow * 0.25f, "transfusion");
                p.heal(Math.max(0f, before - t.getHealth()) * 0.6f);
            });
        }
    }

    // ================================================================== 5. Blood Pact
    public static void bloodPact(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        price(p, 0.2f);
        PACT.put(p.getUUID(), true);
        Vec3 c = p.position();
        Vec3 look = flat(p.getLookAngle());
        if (look.lengthSqr() < 1e-4) look = new Vec3(0, 0, 1);
        Vec3 book = c.add(0, 1.0, 0).add(look.normalize().scale(0.6)).add(rightOf(look.normalize()).scale(0.35));
        for (int i = 0; i < 4; i++) {   // blood drawn out of you in four streams into the grimoire
            double a = i * TAU / 4 + 0.4;
            Vec3 from = c.add(Math.cos(a) * 0.35, 0.7 + i * 0.25, Math.sin(a) * 0.35);
            final int ii = i;
            AstaFx.later(i * 2, () -> stream(sl, from, book, 0, 0, 0.05f, 0.5f, 6, 16 + ii));
        }
        FxPacket pl = fx(5, ZenonAI.ground(sl, c), 140);
        pl.r = 1.1f;
        send(sl, pl);
        p.displayClientMessage(Component.literal("Blood Pact: your next Blood spell is 60% stronger").withStyle(ChatFormatting.DARK_RED), true);
        sound(sl, c, SoundEvents.WARDEN_HEARTBEAT, 1.2f, 1.0f);
    }

    // ================================================================== 6. Red Tide
    public static void redTide(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        price(p, 0.04f);
        float pow = pw(p, power);
        Vec3 look = flat(p.getLookAngle());
        if (look.lengthSqr() < 1e-4) look = new Vec3(0, 0, 1);
        final Vec3 d = look.normalize();
        Vec3 start = p.position();
        FxPacket w = fx(4, ZenonAI.ground(sl, start.add(d.scale(1.5))), 15);   // a curling wave of blood, spraying off its crest
        w.d = d; w.r = 2.6f; w.r2 = 2.4f; w.speed = 1.1f;
        send(sl, w);
        java.util.Set<UUID> struck = new java.util.HashSet<>();
        for (int k = 0; k < 12; k++) {
            final int kk = k;
            AstaFx.later(k, () -> {
                if (!alive(p)) return;
                Vec3 g = ZenonAI.ground(sl, start.add(d.scale(1.5 + kk * 1.1)));
                for (LivingEntity e : enemies(sl, p, new AABB(g, g).inflate(2.8, 2, 2.8))) {
                    if (!struck.add(e.getUUID())) continue;
                    hit(p, e, pow, "red_tide");
                    e.setDeltaMovement(d.scale(1.3).add(0, 0.45, 0));
                    e.hurtMarked = true;
                }
                if (kk % 3 == 0) sound(sl, g, SoundEvents.GENERIC_SPLASH, 1.0f, 0.5f);
            });
        }
    }

    // ================================================================== 7. Thorned Veins
    public static void thornedVeins(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        price(p, 0.04f);
        float pow = pw(p, power);
        Vec3 look = flat(p.getLookAngle());
        if (look.lengthSqr() < 1e-4) look = new Vec3(0, 0, 1);
        final Vec3 d = look.normalize(), rt = rightOf(d);
        java.util.Set<UUID> struck = new java.util.HashSet<>();
        for (int k = 0; k < 10; k++) {
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
                if (kk % 2 == 0) sound(sl, g, SoundEvents.POINTED_DRIPSTONE_LAND, 1.0f, 0.6f);
            });
        }
    }

    // ================================================================== 8. Crimson Bulwark
    public static void crimsonBulwark(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        price(p, 0.06f);
        float shield = 6f + power * 0.5f + (moon(p) ? 4f : 0f);
        p.setAbsorptionAmount(Math.max(p.getAbsorptionAmount(), shield));
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
                Vec3 at = p.position().add(0, 1, 0);
                end(sl, at, shell);
                for (LivingEntity e : enemies(sl, p, new AABB(at, at).inflate(4))) hit(p, e, pow * 0.6f, "crimson_bulwark");
                sound(sl, at, SoundEvents.GLASS_BREAK, 1.0f, 0.6f);
                sound(sl, at, SoundEvents.GENERIC_SPLASH, 1.0f, 0.6f);
            });
        }
    }

    // ================================================================== 9. Exsanguinate
    public static void exsanguinate(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        price(p, 0.05f);
        float pow = pw(p, power);
        Vec3 c = p.position().add(0, 1.1, 0);
        List<LivingEntity> foes = enemies(sl, p, new AABB(c, c).inflate(9));
        sound(sl, c, SoundEvents.WARDEN_HEARTBEAT, 1.4f, 0.7f);
        for (LivingEntity e : foes) {   // blood torn out of each of them, arcing into you
            spray(sl, mid(e), new Vec3(0, 0.12, 0), 0.06f, 6, 0.06f);
            stream(sl, mid(e), c, e.getId(), p.getId(), 0.075f, 1.4f, 10, 22);
        }
        AstaFx.later(20, () -> {
            if (!alive(p)) return;
            float healed = 0;
            for (LivingEntity e : foes) {
                if (!e.isAlive()) continue;
                float before = e.getHealth();
                hit(p, e, pow, "exsanguinate");
                healed += Math.max(0f, before - e.getHealth()) * 0.5f;
            }
            p.heal(healed);
            sound(sl, p.position(), SoundEvents.HONEY_DRINK, 1.4f, 0.6f);
        });
    }

    // ================================================================== 10. Scarlet Moon
    private static final int MOON_T = 600, FORM = 30;

    /**
     * The mode. White light is drawn in from all round you for a second and a half, then a pillar of it strikes you and
     * a white shockwave rolls out. For 30 s a white aura blazes round you: white flame licking up your body, streaks
     * spiralling round you, rising sparks, crackling arcs and a ground glow beating like a heart. Your Blood spells hit
     * 35% harder and heal you for a quarter of what they deal (their price is doubled); you're faster and hard to move.
     */
    public static void scarletMoon(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        price(p, 0.1f);
        Vec3 c0 = p.position();
        sound(sl, c0, SoundEvents.WARDEN_HEARTBEAT, 1.6f, 0.8f);
        sound(sl, c0, SoundEvents.BEACON_ACTIVATE, 0.8f, 0.6f);
        FxPacket aw = fx(10, c0, FORM + 18);
        aw.r = 6f; aw.sweep = FORM;
        send(sl, aw);
        for (int k = 10; k < FORM; k += 10) {
            final int kk = k;
            AstaFx.later(k, () -> { if (alive(p)) sound(sl, p.position(), SoundEvents.WARDEN_HEARTBEAT, 1.2f, 0.9f + kk * 0.02f); });
        }
        AstaFx.later(FORM, () -> {
            if (!alive(p)) return;
            Vec3 c = p.position();
            FxPacket au = fx(9, c, MOON_T);   // the white aura round you (BloodFxClient kind 9)
            au.v = new Vec3(p.getId(), 0, 0);
            com.ironspellgrimoire.network.AddonNetwork.send(net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), au);
            FxPacket a = new FxPacket();
            a.shape = 14; a.id = p.getId(); a.palette = 12; a.life = MOON_T;   // and the white glow on your body (AuraOutlineLayer 12)
            com.ironspellgrimoire.network.AddonNetwork.send(net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), a);
            Moves.stance(p, 4, 2, 0.25, 0.6, 0, MOON_T);
            Moves.mode(p, MODE, MOON_T, 10, pl -> {});
            FxSend.shake(sl, c, 0.6f, 10, 20);
            for (LivingEntity e : enemies(sl, p, new AABB(c, c).inflate(4))) {   // the shockwave throws back anyone close
                Vec3 away = flat(e.position().subtract(c));
                if (away.lengthSqr() > 1e-4) { e.setDeltaMovement(away.normalize().scale(1.2).add(0, 0.4, 0)); e.hurtMarked = true; }
            }
            p.displayClientMessage(Component.literal("SCARLET MOON").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD), true);
            sound(sl, c, SoundEvents.WITHER_SPAWN, 0.7f, 1.4f);
            sound(sl, c, SoundEvents.BEACON_POWER_SELECT, 1.0f, 0.5f);
        });
    }

    /** Marked foes take 25% more from Blood spells - and hits taken by a Scarlet Moon caster are lessened a little. */
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && Moves.inMode(p, MODE)) e.setAmount(e.getAmount() * 0.85f);
        long now = e.getEntity().level().getGameTime();
        MARKED.values().removeIf(t -> t < now - 200);
    }
}
