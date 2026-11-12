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
 * 10 Scarlet Moon (mode) - 30 s: a white aura blazes round you and a red moon hangs over your head. Blood spells hit
 *                       35% harder and heal you for a quarter of what they deal; you move faster and hold your ground.
 *
 * Passive "Blood Price": up to +80% power as your health runs low.
 */
@Mod.EventBusSubscriber(modid = "iron_spell_grimoire")
public final class BloodFx {
    private BloodFx() {}

    public static final String MODE = "scarlet_moon";
    private static final int RED = Fx.CRIMSON, WHITE = Fx.SILVER, DARK = Fx.DARK;
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
            FxSend.ring(p.serverLevel(), RED | FxPacket.KEEP_SHAPE, p.position().add(0, 1, 0), 0.2f, 2.2f, 0.08f, 8);
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

    /** A drop of blood: a small crimson orb, falling a little. */
    private static void drop(ServerLevel sl, Vec3 at, float r, int life) {
        BossFx.orb(sl, RED, at, new Vec3(0, -0.04, 0), r, life);
    }

    // ================================================================== 1. Crimson Edge
    public static void crimsonEdge(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        price(p, 0.02f);
        float pow = pw(p, power);
        Vec3 look = flat(p.getLookAngle());
        if (look.lengthSqr() < 1e-4) look = new Vec3(0, 0, 1);
        look = look.normalize();
        Vec3 c = p.position().add(0, 1.1, 0).add(look.scale(1.6));
        FxSend.slash(sl, RED | FxPacket.KEEP_SHAPE, c, look, new Vec3(0, 1, 0).add(rightOf(look).scale(0.25)).normalize(), 3.4f, 0.5f, 9, 1.4f, 0.35f);
        FxSend.slash(sl, DARK | FxPacket.KEEP_SHAPE, c.add(0, -0.05, 0), look, new Vec3(0, 1, 0), 3.0f, 0.25f, 7, 1.4f, 0.2f);
        if (moon(p)) FxSend.slash(sl, WHITE | FxPacket.KEEP_SHAPE, c.add(0, 0.05, 0), look, new Vec3(0, 1, 0), 3.7f, 0.12f, 8, 1.4f, 0.12f);
        for (LivingEntity e : enemies(sl, p, new AABB(c, c).inflate(3.6))) {
            Vec3 to = flat(mid(e).subtract(p.position()));
            if (to.lengthSqr() < 1e-4 || to.normalize().dot(look) < 0.2) continue;
            hit(p, e, pow, "crimson_edge");
            drop(sl, mid(e), 0.3f, 10);
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
        BossFx.trail(sl, RED, p.getEyePosition().subtract(0, 0.2, 0), mid(t), 0.06f, 8);
        FxSend.ring(sl, RED | FxPacket.KEEP_SHAPE, mid(t), 1.6f, 0.2f, 0.08f, 10);
        sound(sl, mid(t), SoundEvents.HONEY_DRINK, 1.2f, 0.5f);
        for (int k = 20; k <= 160; k += 20) {   // it bleeds: a little every second
            AstaFx.later(k, () -> {
                if (!t.isAlive() || !alive(p)) return;
                hit(p, t, pow * 0.22f, "hemorrhage");
                for (int i = 0; i < 3; i++) drop(sl, mid(t).add(Math.random() - 0.5, Math.random() * 0.6, Math.random() - 0.5), 0.12f, 14);
                FxSend.ring(sl, RED, t.position().add(0, 0.05, 0), 0.3f, 0.8f, 0.03f, 12);
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
            for (int k = 0; k < 9; k++) {
                final int kk = k;
                AstaFx.later(k + i / 3, () -> {
                    if (spent[0] || !alive(p)) return;
                    Vec3 a0 = eye.add(d.scale(2.2 * kk)), a1 = eye.add(d.scale(2.2 * (kk + 1)));
                    BossFx.trail(sl, RED, a0, a1, 0.07f, 5);
                    if (kk % 2 == 0) BossFx.trail(sl, WHITE, a0.add(d.scale(1.6)), a1, 0.025f, 3);
                    for (LivingEntity e : Kit.along(sl, p, a0, a1, 0.6)) {
                        hit(p, e, pow * 0.35f, "sanguine_volley");
                        drop(sl, mid(e), 0.2f, 10);
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
        for (int k = 0; k < 40; k += 2) {
            final int kk = k;
            AstaFx.later(k, () -> {
                if (!alive(p) || !t.isAlive() || t.distanceTo(p) > 20) return;
                Vec3 a = mid(t), b = p.position().add(0, 1.1, 0);
                double w = Math.sin(kk * 0.5) * 0.25;
                Vec3 m = a.add(b).scale(0.5).add(0, 0.6 + w, 0);
                BossFx.trail(sl, RED, a, m, 0.07f, 3);
                BossFx.trail(sl, RED, m, b, 0.07f, 3);
                drop(sl, a.add(b.subtract(a).scale((kk % 10) / 10.0)), 0.14f, 4);
                if (kk % 10 == 0) {
                    float before = t.getHealth();
                    hit(p, t, pow * 0.25f, "transfusion");
                    p.heal(Math.max(0f, before - t.getHealth()) * 0.6f);
                }
            });
        }
    }

    // ================================================================== 5. Blood Pact
    public static void bloodPact(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        price(p, 0.2f);
        PACT.put(p.getUUID(), true);
        Vec3 c = p.position();
        for (int i = 0; i < 10; i++) {   // blood rises off you in a spiral and sinks into the grimoire
            final int ii = i;
            AstaFx.later(i, () -> {
                double a = ii * 0.7;
                Vec3 q = c.add(Math.cos(a) * 0.8, 0.2 + ii * 0.16, Math.sin(a) * 0.8);
                drop(sl, q, 0.18f, 10);
            });
        }
        FxSend.ring(sl, RED | FxPacket.KEEP_SHAPE, c.add(0, 0.05, 0), 0.3f, 2.4f, 0.06f, 14);
        FxSend.impactFrame(sl, c.add(0, 1, 0), 2, FxSend.FRAME_BLOOD, 16);
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
        final Vec3 d = look.normalize(), rt = rightOf(d);
        Vec3 start = p.position();
        java.util.Set<UUID> struck = new java.util.HashSet<>();
        for (int k = 0; k < 12; k++) {
            final int kk = k;
            AstaFx.later(k, () -> {
                if (!alive(p)) return;
                Vec3 c = start.add(d.scale(1.5 + kk * 1.1));
                Vec3 g = ZenonAI.ground(sl, c);
                FxSend.wall(sl, RED | FxPacket.KEEP_SHAPE, g.add(0, 0.05, 0), d, new Vec3(0, 1, 0), 2.6f, 2.6f, -40, 40, 1.6f - kk * 0.04f, 6, 0, 0);
                if (kk % 2 == 0) FxSend.wall(sl, WHITE | FxPacket.KEEP_SHAPE, g.add(d.scale(0.2)).add(0, 1.4, 0), d, new Vec3(0, 1, 0), 2.4f, 2.4f, -35, 35, 0.15f, 4, 0, 0);
                for (LivingEntity e : enemies(sl, p, new AABB(g, g).inflate(2.8, 2, 2.8))) {
                    if (!struck.add(e.getUUID())) continue;
                    hit(p, e, pow, "red_tide");
                    e.setDeltaMovement(d.scale(1.3).add(0, 0.45, 0));
                    e.hurtMarked = true;
                }
                if (kk % 3 == 0) sound(sl, g, SoundEvents.GENERIC_SPLASH, 1.0f, 0.5f);
            });
        }
        for (int s = -1; s <= 1; s += 2) drop(sl, start.add(rt.scale(s)).add(0, 1, 0), 0.2f, 8);
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
                for (int i = 0; i < 3; i++) {   // a cluster of crimson thorns, leaning out
                    Vec3 base = g.add(rt.scale(r.nextDouble(-0.8, 0.8))).add(d.scale(r.nextDouble(-0.5, 0.5)));
                    Vec3 dir = new Vec3(r.nextDouble(-0.35, 0.35), 1, r.nextDouble(-0.35, 0.35)).normalize();
                    FxSend.spike(sl, RED | FxPacket.KEEP_SHAPE, base, dir, 1.6f + r.nextFloat() * 1.2f, 0.22f, 22, 3, FxPacket.GROUND);
                }
                FxSend.cracks(sl, RED, g.add(0, 0.04, 0), d, new Vec3(0, 1, 0), 1.6f, 30, false, kk * 31 + p.getId());
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
        FxSend.ring(sl, RED | FxPacket.KEEP_SHAPE, c.add(0, 1, 0), 1.6f, 1.0f, 1.6f, 14);
        FxSend.ring(sl, DARK, c.add(0, 0.05, 0), 0.4f, 1.6f, 0.05f, 12);
        sound(sl, c, SoundEvents.ARMOR_EQUIP_NETHERITE, 1.0f, 0.7f);
        final float pow = pw(p, power);
        for (int k = 10; k <= 200; k += 10) {   // while it holds, it drips; when it's gone, it breaks outward
            final int kk = k;
            AstaFx.later(k, () -> {
                if (!alive(p)) return;
                if (p.getAbsorptionAmount() > 0.5f) {
                    if (kk % 20 == 0) FxSend.ring(sl, RED, p.position().add(0, 1.0, 0), 0.75f, 0.8f, 1.4f, 10);
                    return;
                }
                if (kk == 200 || p.getPersistentData().getBoolean("isg_bulwark_broke")) return;
                p.getPersistentData().putBoolean("isg_bulwark_broke", true);
                Vec3 at = p.position().add(0, 1, 0);
                FxSend.ring(sl, RED | FxPacket.KEEP_SHAPE, at, 0.5f, 4f, 0.2f, 10);
                for (LivingEntity e : enemies(sl, p, new AABB(at, at).inflate(4))) hit(p, e, pow * 0.6f, "crimson_bulwark");
                sound(sl, at, SoundEvents.GLASS_BREAK, 1.0f, 0.6f);
            });
        }
        p.getPersistentData().remove("isg_bulwark_broke");
    }

    // ================================================================== 9. Exsanguinate
    public static void exsanguinate(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        price(p, 0.05f);
        float pow = pw(p, power);
        Vec3 c = p.position().add(0, 1.1, 0);
        List<LivingEntity> foes = enemies(sl, p, new AABB(c, c).inflate(9));
        FxSend.ring(sl, RED | FxPacket.KEEP_SHAPE, p.position().add(0, 0.05, 0), 9f, 0.5f, 0.08f, 16);
        sound(sl, c, SoundEvents.WARDEN_HEARTBEAT, 1.4f, 0.7f);
        for (int k = 0; k < 20; k += 2) {
            final int kk = k;
            AstaFx.later(k, () -> {
                if (!alive(p)) return;
                for (LivingEntity e : foes) {
                    if (!e.isAlive()) continue;
                    Vec3 a = mid(e), b = p.position().add(0, 1.1, 0);
                    Vec3 m = a.add(b).scale(0.5).add(0, 1.0 + Math.sin(kk * 0.4) * 0.3, 0);
                    BossFx.trail(sl, RED, a, m, 0.06f, 3);
                    BossFx.trail(sl, RED, m, b, 0.06f, 3);
                }
            });
        }
        AstaFx.later(20, () -> {
            if (!alive(p)) return;
            float healed = 0;
            for (LivingEntity e : foes) {
                if (!e.isAlive()) continue;
                float before = e.getHealth();
                hit(p, e, pow, "exsanguinate");
                healed += Math.max(0f, before - e.getHealth()) * 0.5f;
                FxSend.ring(sl, RED, mid(e), 0.2f, 1.2f, 0.06f, 8);
            }
            p.heal(healed);
            FxSend.ring(sl, RED | FxPacket.KEEP_SHAPE, p.position().add(0, 1, 0), 2.2f, 0.2f, 0.1f, 10);
            sound(sl, p.position(), SoundEvents.HONEY_DRINK, 1.4f, 0.6f);
        });
    }

    // ================================================================== 10. Scarlet Moon
    private static final int MOON_T = 600, FORM = 30;

    /**
     * The mode. It forms over a second and a half: blood is drawn up out of the ground to you in streams, a red moon
     * opens in the sky above your head, and a white aura blazes up round you in one shockwave. Then for 30 s: the aura
     * and the moon stay with you, white light licks up your body and blood circles you; your Blood spells hit 35%
     * harder and heal you for a quarter of what they deal (their price is doubled); you're faster and hard to move.
     */
    public static void scarletMoon(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        price(p, 0.1f);
        Vec3 c0 = p.position();
        sound(sl, c0, SoundEvents.WARDEN_HEARTBEAT, 1.6f, 0.8f);
        sound(sl, c0, SoundEvents.BEACON_ACTIVATE, 0.8f, 0.6f);
        for (int k = 0; k < FORM; k += 2) {   // blood drawn up to you from the ground in streams
            final int kk = k;
            AstaFx.later(k, () -> {
                if (!alive(p)) return;
                Vec3 c = p.position().add(0, 1.1, 0);
                for (int i = 0; i < 3; i++) {
                    double a = kk * 0.45 + i * TAU / 3;
                    double r = 5.5 - kk * 0.15;
                    Vec3 from = p.position().add(Math.cos(a) * r, 0.1, Math.sin(a) * r);
                    BossFx.trail(sl, RED, from, c, 0.07f, 5);
                }
                if (kk % 6 == 0) FxSend.ring(sl, RED, p.position().add(0, 0.05, 0), 5.5f - kk * 0.15f, 5.7f - kk * 0.15f, 0.04f, 6);
                if (kk % 10 == 0) sound(sl, c, SoundEvents.WARDEN_HEARTBEAT, 1.2f, 0.9f + kk * 0.02f);
            });
        }
        AstaFx.later(FORM, () -> {
            if (!alive(p)) return;
            Vec3 c = p.position();
            Moves.aura(p, 6, RED, MOON_T);   // the red moon overhead, white wisps and circling blood (ModeAuraFx style 6)
            com.ironspellgrimoire.network.FxPacket a = new com.ironspellgrimoire.network.FxPacket();
            a.shape = 14; a.id = p.getId(); a.palette = 12; a.life = MOON_T;   // the white aura on your body (AuraOutlineLayer 12)
            com.ironspellgrimoire.network.AddonNetwork.send(net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), a);
            Moves.stance(p, 4, 2, 0.25, 0.6, 0, MOON_T);
            Moves.mode(p, MODE, MOON_T, 10, pl -> {
                if (sl.getGameTime() % 40 == 0) FxSend.ring(sl, RED, pl.position().add(0, 0.05, 0), 1.3f, 1.6f, 0.03f, 30);
            });
            // the moment it opens: a white pillar into you, a shockwave of white and red, a crack in the ground
            BossFx.beam(sl, WHITE, c.add(0, 18, 0), c.add(0, 0.5, 0), 0.6f, 10);
            BossFx.beam(sl, RED, c.add(0, 18, 0), c.add(0, 0.5, 0), 0.25f, 12);
            FxSend.ring(sl, WHITE | FxPacket.KEEP_SHAPE, c.add(0, 0.1, 0), 0.5f, 8f, 0.3f, 14);
            FxSend.ring(sl, RED | FxPacket.KEEP_SHAPE, c.add(0, 0.12, 0), 0.5f, 6f, 0.15f, 16);
            FxSend.cracks(sl, RED, c.add(0, 0.04, 0), new Vec3(1, 0, 0), new Vec3(0, 1, 0), 4.5f, 60, false, p.getId() * 7);
            FxSend.impactFrame(sl, c.add(0, 1.2, 0), 4, FxSend.FRAME_BLOOD, 28);
            FxSend.shake(sl, c, 0.6f, 10, 20);
            for (LivingEntity e : enemies(sl, p, new AABB(c, c).inflate(4))) {   // the shockwave throws back anyone close
                Vec3 away = flat(e.position().subtract(c));
                if (away.lengthSqr() > 1e-4) { e.setDeltaMovement(away.normalize().scale(1.2).add(0, 0.4, 0)); e.hurtMarked = true; }
            }
            p.displayClientMessage(Component.literal("SCARLET MOON").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD), true);
            sound(sl, c, SoundEvents.WITHER_SPAWN, 0.7f, 1.4f);
            sound(sl, c, SoundEvents.BEACON_POWER_SELECT, 1.0f, 0.5f);
        });
        AstaFx.later(FORM + MOON_T, () -> { if (alive(p)) Moves.aura(p, 6, RED, 0); });
    }

    /** Marked foes take 25% more from Blood spells - and hits taken by a Scarlet Moon caster are lessened a little. */
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && Moves.inMode(p, MODE)) e.setAmount(e.getAmount() * 0.85f);
        long now = e.getEntity().level().getGameTime();
        MARKED.values().removeIf(t -> t < now - 200);
    }
}
