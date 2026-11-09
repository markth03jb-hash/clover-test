package com.ironspellgrimoire.server;

import static com.ironspellgrimoire.server.FxUtil.*;

import com.ironspellgrimoire.IronSpellGrimoire;
import com.ironspellgrimoire.entity.MagicBolt;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Noelle Silva's Water Magic - the eight spells of the Water Grimoire (a royal's water: clear blue, white foam).
 *
 *  1 Water Ball         - a sphere of water thrown ahead; it bursts into a splash that soaks everything near
 *  2 Torrent Lance      - a thin, hard jet of water through everything in a straight line
 *  3 Sea Dragon's Nest  - a swirling sphere of water at the spot you aim: it drags enemies in, holds and batters them
 *  4 Aqua Barrage       - five water balls in a fan, one after another
 *  5 Tidal Wave         - a breaking wave rolls out ahead of you and carries everything in it away
 *  6 Sea Serpent        - a serpent of water snakes through the enemies ahead of you
 *  7 Valkyrie Dress     - armour of flowing water for 20 s: tougher, a little faster, and it never stops flowing
 *  8 Sea Dragon's Wrath - a great dragon of water rises, arcs over and crashes down where you aim
 *
 * Passive "Drenched": whatever your water hits is soaked and moves a quarter slower for a few seconds.
 */
@Mod.EventBusSubscriber(modid = IronSpellGrimoire.MODID)
public final class WaterFx {
    private WaterFx() {}
    private static final int W = Fx.WATER, S = Fx.SILVER;
    private static final UUID DRESS_ARMOR = UUID.fromString("c41d2a10-5b7e-4f21-8a90-1f3b6d700011");
    private static final UUID DRESS_SPEED = UUID.fromString("c41d2a10-5b7e-4f21-8a90-1f3b6d700012");
    private static final Map<UUID, Long> DRESS = new HashMap<>();

    // ------------------------------------------------------------------ shared
    private static void splash(ServerPlayer p, ServerLevel sl, Vec3 at, double r, float dmg, String id) {
        for (LivingEntity t : Kit.near(sl, p, at, r)) {
            AstaFx.damage(p, t, dmg, id);
            Kit.push(t, mid(t).subtract(at), 0.5, 0.25);
            Kit.drench(t, 60);
        }
        Fx.impact(sl, W, at, (float) r * 0.45f);
        FxSend.ring(sl, W, at.add(0, 0.05, 0), 0.2f, (float) r, 0.08f, 8);
        FxSend.ring(sl, S, at.add(0, 0.3, 0), 0.1f, (float) r * 0.7f, 0.04f, 6);
        for (int i = 0; i < 6; i++) {   // droplets thrown up and out
            double a = i * Math.PI / 3 + at.x;
            Vec3 d = new Vec3(Math.cos(a), 0.9, Math.sin(a));
            FxSend.trail(sl, W, at, at.add(d.scale(r * 0.55)), 0.05f, 6);
        }
        sound(sl, at, SoundEvents.PLAYER_SPLASH, 1.0f, 0.9f);
        sound(sl, at, SoundEvents.GENERIC_SPLASH, 0.8f, 1.1f);
    }

    private static void ball(ServerPlayer p, Vec3 from, Vec3 dir, float speed, float size, double radius, float dmg, String id) {
        ServerLevel sl = p.serverLevel();
        MagicBolt.launch(p, from, dir.normalize().scale(speed), 0.02F + size * 0.02F, 34,
                (b, at, t) -> splash(p, sl, t == null ? at : mid(t), radius, dmg, id),
                (b, prev) -> {
                    Kit.orb(sl, W, b.position(), b.getDeltaMovement(), size, 2);
                    FxSend.trail(sl, W, prev, b.position(), size * 0.6f, 5);
                    FxSend.trail(sl, S, prev, b.position(), size * 0.15f, 3);
                });
    }

    // ================================================================== 1. Water Ball
    public static void waterBall(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        for (int k = 0; k < 6; k += 2) {   // water gathering into the palm
            final int kk = k;
            AstaFx.later(k + 1, () -> { if (alive(p)) Kit.orb(sl, W, Kit.hand(p), Vec3.ZERO, 0.1f + kk * 0.04f, 3); });
        }
        sound(sl, p.position(), SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, 0.8f, 1.4f);
        AstaFx.later(7, () -> {
            if (!alive(p)) return;
            ball(p, Kit.hand(p), p.getLookAngle(), 1.7f, 0.5f, 3.0, power, "water_ball");
            sound(sl, p.position(), SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 0.8f, 1.2f);
        });
    }

    // ================================================================== 2. Torrent Lance
    public static void torrentLance(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        AstaFx.later(8, () -> {
            if (!alive(p)) return;
            Vec3 from = Kit.hand(p), dir = p.getLookAngle();
            Vec3 end = clip(sl, p, from, from.add(dir.scale(24)));
            FxSend.beam(sl, W, from, end, 0.32f, 6);
            FxSend.beam(sl, S, from, end, 0.1f, 5);
            Vec3 rt = rightOf(dir), up = rt.cross(dir).normalize();
            Vec3 prev = from;
            for (int i = 1; i <= 10; i++) {   // a corkscrew of foam around the jet
                double t = i / 10.0, a = t * 14;
                Vec3 q = from.add(end.subtract(from).scale(t)).add(rt.scale(Math.cos(a) * 0.45)).add(up.scale(Math.sin(a) * 0.45));
                FxSend.trail(sl, S, prev, q, 0.06f, 5);
                prev = q;
            }
            for (LivingEntity t : Kit.along(sl, p, from, end, 0.9)) {
                AstaFx.damage(p, t, power * 1.15f, "torrent_lance");
                Kit.push(t, dir, 0.9, 0.2);
                Kit.drench(t, 60);
                Fx.impact(sl, W, mid(t), 1.0f);
            }
            Fx.impact(sl, W, end, 1.2f);
            FxSend.shake(sl, from, 0.2f, 4, 20);
            sound(sl, from, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 1.0f, 1.5f);
            sound(sl, from, SoundEvents.TRIDENT_RIPTIDE_1, 0.7f, 1.6f);
        });
    }

    // ================================================================== 3. Sea Dragon's Nest
    public static void seaDragonsNest(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        LivingEntity main = aimed(p, 30, 15);
        Vec3 c = (main != null ? mid(main) : Kit.aim(p, 22)).add(0, main == null ? 1.5 : 0, 0);
        final double R = 4.0;
        FxSend.ring(sl, W, c.add(0, -1.4, 0), 0.3f, (float) R, 0.1f, 10);
        sound(sl, c, SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_INSIDE, 1.2f, 0.8f);
        for (int t = 0; t < 40; t++) {
            final int tt = t;
            AstaFx.later(t + 1, () -> {
                if (!alive(p)) return;
                Kit.orb(sl, W, c, Vec3.ZERO, 0.6f + 0.9f * Math.min(1, tt / 10f), 2);   // the sphere of water
                if (tt % 2 == 0) {   // streams curling round it
                    double a0 = tt * 0.5;
                    for (int s = 0; s < 3; s++) {
                        double a = a0 + s * Math.PI * 2 / 3;
                        Vec3 p0 = c.add(Math.cos(a) * R * 0.9, Math.sin(a * 1.7) * 1.2, Math.sin(a) * R * 0.9);
                        Vec3 p1 = c.add(Math.cos(a + 0.7) * R * 0.45, Math.sin((a + 0.7) * 1.7) * 0.7, Math.sin(a + 0.7) * R * 0.45);
                        FxSend.trail(sl, W, p0, p1, 0.07f, 4);
                    }
                }
                for (LivingEntity e : Kit.near(sl, p, c, R)) {   // drawn in and held
                    Vec3 in = c.subtract(mid(e));
                    if (in.length() > 0.8) { e.setDeltaMovement(in.normalize().scale(Math.min(0.45, 0.1 + in.length() * 0.06))); e.hurtMarked = true; }
                    else { e.setDeltaMovement(e.getDeltaMovement().scale(0.3)); e.hurtMarked = true; }
                    if (tt % 10 == 9) { AstaFx.damage(p, e, power * 0.14f, "sea_dragons_nest"); Kit.drench(e, 40); }
                }
            });
        }
        AstaFx.later(42, () -> {   // the nest bursts
            if (!alive(p)) return;
            for (LivingEntity e : Kit.near(sl, p, c, R)) {
                AstaFx.damage(p, e, power * 0.55f, "sea_dragons_nest");
                Kit.push(e, mid(e).subtract(c), 0.9, 0.6);
                Kit.drench(e, 80);
            }
            FxSend.ring(sl, W, c, 0.3f, (float) R * 1.6f, 0.2f, 10);
            FxSend.ring(sl, S, c, 0.2f, (float) R * 1.1f, 0.06f, 8);
            Fx.impact(sl, W, c, 2.4f);
            FxSend.shake(sl, c, 0.5f, 8, 32);
            sound(sl, c, SoundEvents.GENERIC_SPLASH, 1.4f, 0.7f);
            sound(sl, c, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 1.0f, 0.7f);
        });
    }

    // ================================================================== 4. Aqua Barrage
    public static void aquaBarrage(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        sound(sl, p.position(), SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, 0.8f, 1.2f);
        for (int i = 0; i < 5; i++) {
            final int ii = i;
            AstaFx.later(6 + i * 2, () -> {
                if (!alive(p)) return;
                Vec3 look = p.getLookAngle();
                Vec3 dir = rotY(look, (ii - 2) * 0.12);
                ball(p, Kit.hand(p), dir, 1.7f, 0.32f, 2.0, power * 0.42f, "aqua_barrage");
                sound(sl, p.position(), SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 0.5f, 1.4f + ii * 0.05f);
            });
        }
    }

    // ================================================================== 5. Tidal Wave
    public static void tidalWave(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        Vec3 dir = flat(p.getLookAngle());
        Vec3 rt = rightOf(dir), up = new Vec3(0, 1, 0);
        Vec3 start = p.position().add(0, 0.1, 0).add(dir.scale(1.5));
        Set<UUID> hit = new HashSet<>();
        sound(sl, p.position(), SoundEvents.GENERIC_SPLASH, 1.3f, 0.5f);
        for (int k = 0; k < 14; k++) {
            final int kk = k;
            AstaFx.later(6 + k, () -> {
                if (!alive(p)) return;
                Vec3 front = start.add(dir.scale(kk * 1.5));
                float width = 3.4f + kk * 0.12f;
                FxSend.crescent(sl, W, front.add(0, 1.0, 0), dir, up, width, 1.2f + kk * 0.04f, 4, 0.0f);
                FxSend.trail(sl, S, front.add(rt.scale(-width)).add(0, 1.6, 0), front.add(rt.scale(width)).add(0, 1.6, 0), 0.07f, 3);
                if (kk % 2 == 0) FxSend.ring(sl, W, front.add(0, 0.05, 0), 0.2f, width * 0.8f, 0.05f, 5);
                for (LivingEntity t : Kit.near(sl, p, front.add(0, 1, 0), width)) {
                    if (!hit.add(t.getUUID())) continue;
                    AstaFx.damage(p, t, power * 0.9f, "tidal_wave");
                    Kit.push(t, dir, 1.4, 0.45);
                    Kit.drench(t, 80);
                    Fx.impact(sl, W, mid(t), 1.1f);
                }
                if (kk % 3 == 0) sound(sl, front, SoundEvents.GENERIC_SPLASH, 0.7f, 0.8f);
            });
        }
    }

    // ================================================================== 6. Sea Serpent
    public static void seaSerpent(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        Vec3 dir = p.getLookAngle();
        Vec3 rt = rightOf(dir), up = rt.cross(dir).normalize();
        Vec3 from = Kit.hand(p);
        Set<UUID> hit = new HashSet<>();
        sound(sl, p.position(), SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_AMBIENT, 1.0f, 1.2f);
        Vec3[] prev = {from};
        final int T = 16;
        for (int k = 1; k <= T; k++) {
            final int kk = k;
            AstaFx.later(6 + k, () -> {
                if (!alive(p)) return;
                double s = kk * 1.7;
                double wob = Math.sin(kk * 0.9) * 1.1 * Math.min(1, kk / 4.0);
                Vec3 pos = from.add(dir.scale(s)).add(rt.scale(wob)).add(up.scale(Math.cos(kk * 0.9) * 0.5));
                float w = 0.55f - kk * 0.012f;
                FxSend.trail(sl, W, prev[0], pos, w, 8);
                FxSend.trail(sl, S, prev[0], pos, w * 0.25f, 5);
                Kit.orb(sl, W, pos, dir.scale(1.7), 0.5f, 2);   // the head
                for (LivingEntity t : Kit.near(sl, p, pos, 1.6)) {
                    if (!hit.add(t.getUUID())) continue;
                    AstaFx.damage(p, t, power * 1.0f, "sea_serpent");
                    Kit.push(t, dir, 0.7, 0.3);
                    Kit.drench(t, 80);
                    Fx.impact(sl, W, mid(t), 1.1f);
                }
                if (kk == T) { Fx.impact(sl, W, pos, 1.6f); FxSend.ring(sl, W, pos, 0.2f, 2.5f, 0.06f, 6); }
                prev[0] = pos;
            });
        }
    }

    // ================================================================== 7. Valkyrie Dress
    public static void valkyrieDress(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        DRESS.put(p.getUUID(), sl.getGameTime() + 400);
        TimedModifiers.apply(p, Attributes.ARMOR, DRESS_ARMOR, "Valkyrie Dress", 8, AttributeModifier.Operation.ADDITION, 400);
        TimedModifiers.apply(p, Attributes.ARMOR_TOUGHNESS, DRESS_ARMOR, "Valkyrie Dress toughness", 4, AttributeModifier.Operation.ADDITION, 400);
        TimedModifiers.apply(p, Attributes.MOVEMENT_SPEED, DRESS_SPEED, "Valkyrie Dress speed", 0.12, AttributeModifier.Operation.MULTIPLY_BASE, 400);
        ModeHud.show(p, "valkyrie_dress", 400);
        // the water rises from the ground and wraps round you into armour
        for (int k = 0; k < 14; k++) {
            final int kk = k;
            AstaFx.later(k + 1, () -> {
                if (!alive(p)) return;
                Vec3 base = p.position();
                for (int s = 0; s < 2; s++) {
                    double a = kk * 0.55 + s * Math.PI;
                    Vec3 q0 = base.add(Math.cos(a) * 0.9, kk * 0.14, Math.sin(a) * 0.9);
                    Vec3 q1 = base.add(Math.cos(a + 0.6) * 0.75, kk * 0.14 + 0.2, Math.sin(a + 0.6) * 0.75);
                    FxSend.trail(sl, W, q0, q1, 0.09f, 6);
                }
            });
        }
        FxSend.ring(sl, W, p.position().add(0, 0.05, 0), 0.3f, 3.2f, 0.08f, 10);
        AstaFx.later(14, () -> { if (alive(p)) { Fx.flash(sl, p.position().add(0, 1, 0), 1.6f); FxLib.halo(sl, W, p.position().add(0, p.getBbHeight() + 0.4, 0), 0.8f); } });
        sound(sl, p.position(), SoundEvents.PLAYER_SPLASH, 1.0f, 1.2f);
        sound(sl, p.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 0.8f, 1.5f);
    }

    private static void dressTick(ServerPlayer p, long now) {
        Long until = DRESS.get(p.getUUID());
        if (until == null) return;
        if (now > until) { DRESS.remove(p.getUUID()); ModeHud.hide(p, "valkyrie_dress"); return; }
        if (now % 6 != 0) return;
        ServerLevel sl = p.serverLevel();
        double a = now * 0.35;
        Vec3 c = p.position();
        for (int s = 0; s < 2; s++) {   // two ribbons of water coiling up the body
            double b = a + s * Math.PI;
            FxSend.trail(sl, W, c.add(Math.cos(b) * 0.55, 0.2, Math.sin(b) * 0.55), c.add(Math.cos(b + 0.9) * 0.5, 1.7, Math.sin(b + 0.9) * 0.5), 0.045f, 7);
        }
    }

    // ================================================================== 8. Sea Dragon's Wrath
    /**
     * A dragon of water forms from the sea around you, rises, arcs over and plunges onto the spot you aim at (up to 34
     * blocks): everything within 7 blocks takes a colossal blow and is thrown up and away.
     * Surpassing Limits: Sea Dragon's Domain.
     */
    public static void seaDragonsWrath(ServerPlayer p, float power) {
        if (LimitBreak.active(p)) { LimitFx.seaDomain(p, power); return; }
        ServerLevel sl = p.serverLevel();
        Vec3 target = Kit.aim(p, 34);
        Vec3 from = p.position().add(0, 1.5, 0);
        Vec3 flatTo = target.subtract(from);
        final int T = 22;
        sound(sl, from, SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_INSIDE, 1.4f, 0.6f);
        sound(sl, from, SoundEvents.WARDEN_SONIC_CHARGE, 0.8f, 0.7f);
        for (int g = 0; g < 8; g++) {   // water rising out of the ground to feed it
            final int gg = g;
            AstaFx.later(g, () -> { if (alive(p)) FxSend.ring(sl, W, p.position().add(0, 0.05, 0), 0.4f + gg * 0.3f, 1.2f + gg * 0.4f, 0.06f, 5); });
        }
        Vec3[] prev = {from};
        for (int k = 1; k <= T; k++) {
            final int kk = k;
            AstaFx.later(8 + k, () -> {
                if (!alive(p)) return;
                double t = kk / (double) T;
                Vec3 pos = from.add(flatTo.scale(t)).add(0, Math.sin(t * Math.PI) * 7.0, 0);
                Vec3 tan = pos.subtract(prev[0]);
                // the body: a thick chain of water with a coil of foam, and a head of three spikes
                FxSend.trail(sl, W, prev[0], pos, 1.0f - (float) t * 0.25f, 12);
                FxSend.trail(sl, S, prev[0], pos, 0.18f, 8);
                Vec3 d = tan.lengthSqr() < 1.0E-4 ? new Vec3(0, -1, 0) : tan.normalize();
                Vec3 rt = rightOf(d), up = rt.cross(d).normalize();
                double a = kk * 1.1;
                Vec3 coil = pos.add(rt.scale(Math.cos(a) * 1.1)).add(up.scale(Math.sin(a) * 1.1));
                FxSend.trail(sl, S, pos, coil, 0.08f, 6);
                Kit.orb(sl, W, pos, tan, 1.1f, 2);
                if (kk % 3 == 0) FxSend.ring(sl, W, pos, 0.2f, 1.8f, 0.05f, 5);
                prev[0] = pos;
            });
        }
        AstaFx.later(8 + T + 1, () -> {   // the crash
            if (!alive(p)) return;
            boolean any = false;
            for (LivingEntity t : Kit.near(sl, p, target, 7.0)) {
                AstaFx.damage(p, t, power * (float) (1.8 - 0.6 * Math.min(1, mid(t).distanceTo(target) / 7.0)), "sea_dragons_wrath");
                Kit.push(t, mid(t).subtract(target), 1.1, 0.8);
                Kit.drench(t, 100);
                any = true;
            }
            if (any) FxSend.impactFrame(sl, target, 4, FxSend.FRAME_NOZEL, 40);
            Fx.flash(sl, target, 3.0f);
            FxSend.ring(sl, W, target.add(0, 0.1, 0), 0.5f, 11f, 0.3f, 12);
            FxSend.ring(sl, S, target.add(0, 0.2, 0), 0.3f, 8f, 0.08f, 10);
            for (int i = 0; i < 10; i++) {
                double a = i * Math.PI / 5;
                FxSend.trail(sl, W, target, target.add(Math.cos(a) * 5, 4 + (i % 3), Math.sin(a) * 5), 0.25f, 9);
            }
            FxSend.shake(sl, target, 1.1f, 14, 48);
            sound(sl, target, SoundEvents.GENERIC_SPLASH, 1.6f, 0.5f);
            sound(sl, target, SoundEvents.GENERIC_EXPLODE, 1.0f, 0.7f);
        });
    }

    // ================================================================== events
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        dressTick(p, p.serverLevel().getGameTime());
    }

    @SubscribeEvent
    public static void onLogout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e) { clear(e.getEntity().getUUID()); }

    public static void clear(UUID id) { DRESS.remove(id); }
    public static void clearAll() { DRESS.clear(); }
}
