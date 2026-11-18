package com.ironspellgrimoire.server;

import static com.ironspellgrimoire.server.FxUtil.*;
import static com.ironspellgrimoire.server.TwinsFx.*;
import static com.ironspellgrimoire.server.TwinsTrial.*;
import com.ironspellgrimoire.entity.AddonBoss;
import com.ironspellgrimoire.entity.TwinWarden;
import com.ironspellgrimoire.network.AddonNetwork;
import com.ironspellgrimoire.network.FxPacket;
import com.ironspellgrimoire.network.TwinsPackets;
import com.ironspellgrimoire.registry.AddonItemRegistry;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Split out of TwinsFx (same code, own file): the arts. */
@Mod.EventBusSubscriber(modid = "iron_spell_grimoire")
public final class TwinsArts {
    private TwinsArts() {}

    // ------------------------------------------------------------------ the arts
    /** Casts an art of a twin (the wheel, or the Twin Arts page): known, chosen (or the eclipse), off cooldown, paid for. */
    public static boolean cast(ServerPlayer p, int twin, int art) {
        if (!equipped(p) || twin < 0 || twin > 1 || art < 0 || art > 7) return false;
        CompoundTag r = root(p);
        boolean ecl = eclipse(p);
        if (!knows(p, twin, art)) { tell(p, ARTS[twin][art] + " is learned at " + TWIN[twin] + " level " + ART_LV[art]); return false; }
        if (!ecl && twin != active(p)) { tell(p, "Wear " + TWIN[twin] + "'s mask to use its arts"); return false; }
        if (!ecl) {
            boolean chosen = false;
            for (int a : load(p, twin)) if (a == art) chosen = true;
            if (!chosen) { tell(p, ARTS[twin][art] + " isn't on your wheel"); return false; }
        }
        long t = now(p);
        long[] cd = cds(p);
        int i = twin * 8 + art;
        if (!ecl && cd[i] > t) { tell(p, ARTS[twin][art] + " ready in " + (int) Math.ceil((cd[i] - t) / 20.0) + " s"); return false; }
        MagicData md = MagicData.getPlayerMagicData(p);
        float cost = ART_MANA[twin][art] * (twin == SOL && perk(p, SOL, 0, 2) ? 0.75f : 1f);
        if (!p.isCreative() && md.getMana() < cost) { tell(p, "Not enough mana"); return false; }
        if (!p.isCreative()) md.setMana(md.getMana() - cost);
        int c = ART_CD[twin][art];
        int mast = mastery(uses(p, twin, art));
        if (twin == NYX && perk(p, NYX, 0, 0)) c = Math.round(c * 0.85f);
        if (mast >= 2) c = Math.round(c * 0.8f);
        if (twin == SOL && noon(p)) c /= 2;
        cd[i] = t + c;
        r.putLongArray("cds", cd);
        if (twin == active(p)) for (int s = 0; s < 4; s++) if (load(p, twin)[s] == art) r.putInt("sel", s);
        r.putLong("combat", t);
        int[] u = root(p).getIntArray("uses");
        if (u.length != 16) u = new int[16];
        int before = mastery(u[i]);
        u[i]++;
        r.putIntArray("uses", u);
        if (mastery(u[i]) > before) {
            int nm = mastery(u[i]);
            say(p, twin, ARTS[twin][art] + " reached Mastery " + "I".repeat(nm) + ". " + (nm == 1 ? "+15% damage." : nm == 2 ? "20% shorter cooldown." : ART_PERF[twin][art]));
            OverdriveFx.play(p.serverLevel(), p.position(), twin == SOL ? "twin_rise" : "twin_fall", 0.7f, 1.6f);
        }
        boost = mast >= 1 ? 1.15f : 1f;
        perfect = mast >= 3;
        try { if (twin == SOL) solArt(p, art); else nyxArt(p, art); } finally { boost = 1f; perfect = false; }
        sync(p, false);
        return true;
    }

    /** Set while an art is cast: its mastery's damage boost, and whether it's Perfected. */
    static float boost = 1f;
    static boolean perfect;

    static void solArt(ServerPlayer p, int art) {
        ServerLevel sl = p.serverLevel();
        final float m = mult(p, SOL) * boost;
        final boolean perf = perfect;
        Vec3 d = flat(p.getLookAngle()), eye = p.getEyePosition(), look = p.getLookAngle();
        switch (art) {
            case 0 -> {   // Sunstrike: a dash that cuts and burns
                p.setDeltaMovement(d.scale(1.8).add(0, 0.15, 0));
                p.hurtMarked = true;
                Set<Integer> got = new HashSet<>();
                Vec3 start = p.position();
                for (int k = 1; k <= 7; k++) {
                    final int kk = k;
                    AstaFx.later(k, () -> {
                        if (!alive(p)) return;
                        for (LivingEntity e : enemies(sl, p, p.getBoundingBox().inflate(1.6))) {
                            if (!got.add(e.getId())) continue;
                            hit(p, e, 8 * m, SOL);
                            burn(p, e, perf ? 5 : 3, 1.5f * m);
                            FxSend.slash(sl, PAL_SOL, mid(e), d, new Vec3(0, 1, 0), 1.0f, 1.6f, 5, 0);
                        }
                        if (kk % 2 == 0) FxSend.ring(sl, PAL_SOL2, p.position().add(0, 0.05, 0), 0.2f, 0.7f, 0.05f, 30);   // the burning marks left behind
                    });
                }
                FxSend.trail(sl, PAL_SOL, start.add(0, 1, 0), start.add(0, 1, 0).add(d.scale(6)), 0.5f, 8);
                OverdriveFx.play(sl, p.position(), "rush", 0.9f, 1.2f);
            }
            case 1 -> {   // Flare Shot: three bolts
                for (int k = 0; k < (perf ? 5 : 3); k++) AstaFx.later(1 + k * 4, () -> {
                    if (!alive(p)) return;
                    Vec3 e0 = p.getEyePosition(), l = p.getLookAngle();
                    LivingEntity tg = aimed(p, 24, 10);
                    Vec3 end = tg != null ? mid(tg) : clip(sl, p, e0, e0.add(l.scale(24)));
                    if (tg == null) { LivingEntity first = firstOnLine(p, e0, end, 0.9); if (first != null) { tg = first; end = mid(first); } }
                    FxSend.beam(sl, PAL_SOL2, e0.add(l.scale(0.6)).add(0, -0.15, 0), end, 0.16f, 5);
                    Fx.spark(sl, PAL_SOL, end, 0.6f);
                    if (tg != null) { hit(p, tg, 5 * m, SOL); }
                    OverdriveFx.play(sl, e0, "sun_flare", 0.4f, 1.8f);
                });
            }
            case 2 -> {   // Solar Lance: through every foe in a line
                Vec3 end = clip(sl, p, eye, eye.add(look.scale(24)));
                FxSend.beam(sl, PAL_SOL, eye.add(look.scale(0.6)).add(0, -0.2, 0), end, perf ? 1.0f : 0.55f, 10);
                FxSend.beam(sl, PAL_SOL2, eye.add(look.scale(0.6)).add(0, -0.2, 0), end, 0.2f, 8);
                Fx.flash(sl, end, 1f);
                for (LivingEntity e : onLine(p, eye, end, perf ? 2.0 : 1.2)) { hit(p, e, 14 * m, SOL); if (perf) burn(p, e, 3, 2f * m); }
                OverdriveFx.play(sl, eye, "sun_flare", 1f, 1.3f);
            }
            case 3 -> {   // Helios Wheel: a disc bouncing between foes
                LivingEntity first = aimed(p, 16, 25);
                if (first == null) {
                    Vec3 end = clip(sl, p, eye, eye.add(d.scale(12)));
                    FxSend.trail(sl, PAL_SOL, eye.add(0, -0.4, 0), end, 0.5f, 10);
                    return;
                }
                List<LivingEntity> chain = new ArrayList<>();
                chain.add(first);
                Set<Integer> got = new HashSet<>(); got.add(first.getId());
                while (chain.size() < (perf ? 8 : 5)) {
                    LivingEntity last = chain.get(chain.size() - 1), next = null; double best = 36;
                    for (LivingEntity e : enemies(sl, p, last.getBoundingBox().inflate(6))) {
                        if (got.contains(e.getId())) continue;
                        double dd = e.distanceToSqr(last);
                        if (dd < best) { best = dd; next = e; }
                    }
                    if (next == null) break;
                    chain.add(next); got.add(next.getId());
                }
                Vec3[] from = {eye.add(0, -0.4, 0)};
                for (int k = 0; k < chain.size(); k++) {
                    LivingEntity e = chain.get(k);
                    AstaFx.later(1 + k * 5, () -> {
                        if (!alive(p) || !e.isAlive()) return;
                        Vec3 to = mid(e);
                        FxSend.trail(sl, PAL_SOL, from[0], to, 0.45f, 8);
                        FxSend.ring(sl, PAL_SOL | FxPacket.KEEP_SHAPE, to, 0.2f, 0.9f, 0.05f, 6);
                        hit(p, e, 9 * m, SOL);
                        from[0] = to;
                    });
                }
                OverdriveFx.play(sl, eye, "blade_fly", 0.8f, 1.3f);
            }
            case 4 -> {   // Corona: 8 s of a burning aura
                root(p).putLong("corona", now(p) + (perf ? 240 : 160));
                root(p).putBoolean("coronaWide", perf);
                FxSend.ring(sl, PAL_SOL, p.position().add(0, 0.1, 0), 0.5f, 4f, 0.4f, 14);
                OverdriveFx.play(sl, p.position(), "sun_flare", 0.9f, 0.8f);
            }
            case 5 -> {   // Zenith Leap: up, then down in fire
                p.setDeltaMovement(d.scale(0.5).add(0, 1.25, 0));
                p.hurtMarked = true;
                root(p).putLong("noFall", now(p) + 80);
                FxSend.ring(sl, PAL_SOL, p.position().add(0, 0.1, 0), 0.3f, 1.8f, 0.2f, 10);
                AstaFx.later(12, () -> {
                    if (!alive(p)) return;
                    p.setDeltaMovement(p.getDeltaMovement().multiply(0.3, 0, 0.3).add(0, -2.6, 0));
                    p.hurtMarked = true;
                    final boolean[] landed = {false};
                    for (int k = 1; k <= 30; k++) {
                        final int kk = k;
                        AstaFx.later(k, () -> {
                            if (landed[0] || !alive(p)) return;
                            if (!p.onGround() && kk < 30) return;
                            landed[0] = true;
                            Vec3 at = p.position();
                            FxSend.ring(sl, PAL_SOL, at.add(0, 0.1, 0), 0.4f, 4.5f, 0.6f, 12);
                            FxSend.ring(sl, PAL_SOL2, at.add(0, 0.2, 0), 0.2f, 3.5f, 0.3f, 10);
                            Fx.pillar(sl, PAL_SOL, at, 3f, 1.4f, 10);
                            FxSend.shake(sl, at, 0.5f, 6, 10);
                            for (LivingEntity e : enemies(sl, p, p.getBoundingBox().inflate(4))) if (e.distanceTo(p) <= 4.5) {
                                hit(p, e, 16 * m, SOL);
                                e.setDeltaMovement(e.getDeltaMovement().add(0, 0.4, 0)); e.hurtMarked = true;
                            }
                            OverdriveFx.play(sl, at, "stomp", 1f, 0.9f);
                            if (perf) AstaFx.later(10, () -> {   // Perfected: a second blast
                                if (!alive(p)) return;
                                FxSend.ring(sl, PAL_SOL2, at.add(0, 0.1, 0), 0.5f, 6f, 0.5f, 12);
                                for (LivingEntity e : enemies(sl, p, new AABB(at, at).inflate(6, 3, 6))) if (e.position().distanceTo(at) <= 6) hit(p, e, 10 * m, SOL);
                            });
                        });
                    }
                });
            }
            case 6 -> {   // Dawnbreaker: a shockwave of light rolling out
                Vec3 c = p.position().add(d.scale(2.0));
                Fx.pillar(sl, PAL_SOL, c, 4f, 1.6f, 12);
                FxSend.ring(sl, PAL_SOL, c.add(0, 0.1, 0), 0.5f, 7f, 0.8f, 14);
                FxSend.ring(sl, PAL_SOL2, c.add(0, 0.3, 0), 0.3f, 6f, 0.4f, 12);
                FxSend.shake(sl, c, 0.7f, 8, 14);
                Set<Integer> got = new HashSet<>();
                for (int k = 0; k <= 6; k++) {
                    final double rr = 1 + k;
                    AstaFx.later(1 + k, () -> {
                        if (!alive(p)) return;
                        for (LivingEntity e : enemies(sl, p, new AABB(c, c).inflate(rr + 1, 3, rr + 1))) {
                            double dd = Math.hypot(e.getX() - c.x, e.getZ() - c.z);
                            if (dd > rr || !got.add(e.getId())) continue;
                            hit(p, e, 20 * m, SOL);
                            Vec3 away = flat(e.position().subtract(c));
                            e.setDeltaMovement(e.getDeltaMovement().add(away.scale(1.1)).add(0, 0.35, 0)); e.hurtMarked = true;
                        }
                    });
                }
                OverdriveFx.play(sl, c, "sun_flare", 1.3f, 0.7f);
                if (perf) AstaFx.later(12, () -> {   // Perfected: a second, wider wave
                    if (!alive(p)) return;
                    FxSend.ring(sl, PAL_SOL, c.add(0, 0.1, 0), 1f, 10f, 0.6f, 14);
                    for (LivingEntity e : enemies(sl, p, new AABB(c, c).inflate(10, 3, 10))) if (Math.hypot(e.getX() - c.x, e.getZ() - c.z) <= 10) hit(p, e, 12 * m, SOL);
                });
            }
            case 7 -> {   // Sunspear Barrage: eight spears in a fan
                int n = perf ? 12 : 8;
                for (int i = 0; i < n; i++) {
                    final double a = Math.toRadians(-30 + i * 60.0 / (n - 1));
                    AstaFx.later(1 + i, () -> {
                        if (!alive(p)) return;
                        Vec3 e0 = p.getEyePosition().add(0, -0.2, 0), dir = rotY(flat(p.getLookAngle()), a).add(0, p.getLookAngle().y, 0).normalize();
                        Vec3 end = clip(sl, p, e0, e0.add(dir.scale(18)));
                        LivingEntity first = firstOnLine(p, e0, end, 0.9);
                        if (first != null) end = mid(first);
                        FxSend.spike(sl, PAL_SOL, e0.add(dir.scale(0.8)), dir, 1.6f, 0.12f, 6, 0, FxPacket.HOLD, 0, dir.scale(Math.max(1, e0.distanceTo(end) / 5)), 5);
                        FxSend.beam(sl, PAL_SOL2, e0, end, 0.06f, 4);
                        if (first != null) { hit(p, first, 12 * m, SOL); Fx.spark(sl, PAL_SOL, end, 0.6f); }
                    });
                }
                OverdriveFx.play(sl, eye, "blade_fly", 1f, 1.1f);
            }
            default -> {}
        }
    }

    static void nyxArt(ServerPlayer p, int art) {
        ServerLevel sl = p.serverLevel();
        final float m = mult(p, NYX) * boost;
        final boolean perf = perfect;
        Vec3 d = flat(p.getLookAngle()), eye = p.getEyePosition(), look = p.getLookAngle();
        switch (art) {
            case 0 -> {   // Moonstep: blink ahead; foes turn on the afterimage
                Vec3 from = p.position();
                Vec3 to = clip(sl, p, from.add(0, 0.6, 0), from.add(0, 0.6, 0).add(d.scale(perk(p, NYX, 1, 1) ? 10 : 7)));
                to = to.subtract(d.scale(0.6)).add(0, -0.6, 0);
                Fx.flash(sl, from.add(0, 1, 0), 0.8f);
                Fx.pillar(sl, PAL_NYX, from, 2.2f, 0.7f, 30);
                FxSend.trail(sl, PAL_NYX, from.add(0, 1, 0), to.add(0, 1, 0), 0.35f, 8);
                p.teleportTo(to.x, to.y, to.z);
                p.fallDistance = 0;
                for (Mob mob : sl.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(12), x -> x.getTarget() == p)) {
                    mob.setTarget(null);
                    mob.getNavigation().moveTo(from.x, from.y, from.z, 1.0);
                }
                if (perk(p, NYX, 1, 0)) p.heal(4f);
                if (perk(p, NYX, 1, 2) && root(p).getLong("veil") <= now(p)) { root(p).putLong("veil", now(p) + 60); root(p).putInt("veilHits", 1); }
                if (perf) for (Vec3 at : new Vec3[]{from, to}) {   // Perfected: frost bursts where you leave and land
                    FxSend.ring(sl, PAL_NYX2, at.add(0, 0.1, 0), 0.3f, 3f, 0.3f, 10);
                    for (LivingEntity e : enemies(sl, p, new AABB(at, at).inflate(3))) if (e.position().distanceTo(at) <= 3.2) { hit(p, e, 6 * m, NYX); slow(p, e, -0.3, 60); }
                }
                OverdriveFx.play(sl, to, "moon_shimmer", 0.8f, 1.3f);
            }
            case 1 -> {   // Frost Needle: through a line; slows
                Vec3 end = clip(sl, p, eye, eye.add(look.scale(20)));
                FxSend.beam(sl, PAL_NYX2, eye.add(look.scale(0.6)).add(0, -0.15, 0), end, 0.12f, 6);
                FxSend.spike(sl, PAL_NYX, eye.add(look.scale(1.0)), look, 1.2f, 0.08f, 5, 0, FxPacket.HOLD, 0, look.scale(4), 4);
                for (LivingEntity e : onLine(p, eye, end, 0.8)) { hit(p, e, 6 * m, NYX); slow(p, e, perf ? -0.5 : -0.3, 60); }
                OverdriveFx.play(sl, eye, "moon_shimmer", 0.6f, 1.8f);
            }
            case 2 -> {   // Crescent Fan: five crescents
                Set<Integer> got = new HashSet<>();
                int n = perf ? 7 : 5;
                for (int i = 0; i < n; i++) {
                    Vec3 dir = rotY(d, Math.toRadians(-30 + i * 60.0 / (n - 1)));
                    Vec3 c0 = p.position().add(0, 1.1, 0).add(dir.scale(1.0));
                    FxSend.crescent(sl, PAL_NYX, c0, dir, rightOf(dir), 0.8f, 1.2f, 10, 1.2f);
                    Vec3 end = clip(sl, p, c0, c0.add(dir.scale(12)));
                    LivingEntity first = firstOnLine(p, c0, end, 1.2);
                    if (first != null && got.add(first.getId())) {
                        final LivingEntity f = first;
                        AstaFx.later(Math.max(1, (int) (f.distanceTo(p) / 1.2)), () -> { if (alive(p)) hit(p, f, 7 * m, NYX); });
                    }
                }
                OverdriveFx.play(sl, eye, "moon_shimmer", 0.8f, 1.1f);
            }
            case 3 -> {   // Mirror Double: the next three blows strike twice
                CompoundTag r = root(p);
                r.putLong("double", now(p) + 160);
                r.putInt("doubleLeft", perf ? 5 : 3);
                Fx.pillar(sl, PAL_NYX, p.position(), 2.4f, 0.8f, 12);
                FxSend.ring(sl, PAL_NYX, p.position().add(0, 1.0, 0), 0.3f, 1.4f, 0.1f, 10);
                OverdriveFx.play(sl, p.position(), "moon_shimmer", 0.8f, 0.9f);
            }
            case 4 -> {   // Lunar Veil: the next blow misses
                root(p).putLong("veil", now(p) + 120);
                root(p).putInt("veilHits", perf ? 2 : 1);
                FxSend.ring(sl, PAL_NYX, p.position().add(0, 1.0, 0), 1.2f, 1.3f, 1.2f, 16);
                OverdriveFx.play(sl, p.position(), "moon_shimmer", 0.9f, 0.7f);
            }
            case 5 -> {   // Starfall Rain: stars falling where you aim
                Vec3 c = aimPoint(p, 20);
                int n = perf ? 18 : 12;
                for (int k = 0; k < n; k++) {
                    final int kk = k;
                    AstaFx.later(2 + k * (perf ? 3 : 5), () -> {
                        if (!alive(p)) return;
                        ThreadLocalRandom rnd = ThreadLocalRandom.current();
                        Vec3 at = kk % 6 == 0 ? c : c.add(rnd.nextDouble(-4, 4), 0, rnd.nextDouble(-4, 4));   // the first lands right where you aimed
                        Vec3 top = at.add(0, 8, 0);
                        FxSend.spike(sl, kk % 2 == 0 ? PAL_NYX : PAL_NYX2, top, new Vec3(0, -1, 0), 1.2f, 0.18f, 6, 0, FxPacket.HOLD, 0, new Vec3(0, -1.6, 0), 5);
                        AstaFx.later(5, () -> {
                            if (!alive(p)) return;
                            FxSend.ring(sl, PAL_NYX, at.add(0, 0.1, 0), 0.2f, 1.6f, 0.15f, 8);
                            for (LivingEntity e : enemies(sl, p, new AABB(at, at).inflate(1.6, 2.5, 1.6))) hit(p, e, 5 * m, NYX);
                        });
                    });
                }
                OverdriveFx.play(sl, c, "moon_shimmer", 1f, 0.8f);
            }
            case 6 -> {   // Tidelock: an orb that pulls and slows
                Vec3 c = aimPoint(p, 18).add(0, 1, 0);
                for (int k = 0; k < (perf ? 32 : 20); k++) {
                    final int kk = k;
                    AstaFx.later(1 + k * 5, () -> {
                        if (!alive(p)) return;
                        if (kk % 2 == 0) FxSend.ring(sl, PAL_NYX2, c.add(0, -0.9, 0), 6f, 0.5f, 0.2f, 10);
                        FxSend.spike(sl, PAL_NYX, c.add(0, -0.4, 0), new Vec3(0, 1, 0), 0.8f, 0.4f, 6, 0, FxPacket.HOLD);
                        for (LivingEntity e : enemies(sl, p, new AABB(c, c).inflate(6))) {
                            if (e.position().distanceTo(c) > 6.5) continue;
                            Vec3 pull = c.subtract(e.position()).normalize().scale(0.18);
                            e.setDeltaMovement(e.getDeltaMovement().add(pull.x, 0, pull.z)); e.hurtMarked = true;
                            slow(p, e, -0.4, 20);
                            if (kk % 4 == 0) hit(p, e, 2 * m, NYX);
                        }
                    });
                }
                OverdriveFx.play(sl, c, "moon_shimmer", 1f, 0.6f);
            }
            case 7 -> {   // Void Rift: a tear that cuts all inside it
                Vec3 c = aimPoint(p, 18).add(0, 1, 0);
                FxSend.cracks(sl, Fx.VOID, c, d, new Vec3(0, 1, 0), 2.2f, 80, true, ThreadLocalRandom.current().nextInt());
                for (int k = 0; k < 16; k++) {
                    final int kk = k;
                    AstaFx.later(1 + k * 5, () -> {
                        if (!alive(p)) return;
                        if (kk % 3 == 0) FxSend.ring(sl, Fx.VOID, c.add(0, -0.9, 0), 0.4f, 3.5f, 0.3f, 12);
                        FxSend.slash(sl, PAL_NYX, c.add(ThreadLocalRandom.current().nextDouble(-1.5, 1.5), ThreadLocalRandom.current().nextDouble(-0.8, 0.8), ThreadLocalRandom.current().nextDouble(-1.5, 1.5)),
                                rotY(d, kk * 1.3), new Vec3(0, 1, 0), 0.8f, 1.4f, 4, 0);
                        double rr = perf ? 5.5 : 4;
                        for (LivingEntity e : enemies(sl, p, new AABB(c, c).inflate(rr))) if (e.position().distanceTo(c) <= rr) hit(p, e, 3 * m, NYX);
                    });
                }
                OverdriveFx.play(sl, c, "eclipse_toll", 0.6f, 1.6f);
            }
            default -> {}
        }
    }

    /** Twin Strike: the gold mask and the silver mask cut at once. */
    public static void twinStrike(ServerPlayer p, float power) {
        if (!canStrike(p)) { tell(p, "Both twins must reach level " + BOND_STRIKE); return; }
        ServerLevel sl = p.serverLevel();
        Vec3 d = flat(p.getLookAngle()), c = p.position().add(0, 1.1, 0).add(d.scale(1.6));
        float m = (mult(p, SOL) + mult(p, NYX)) / 2f;
        FxSend.slash(sl, PAL_SOL, c, d, new Vec3(1, 1, 0).normalize(), 2.2f, 3.2f, 6, 0);
        AstaFx.later(4, () -> FxSend.slash(sl, PAL_NYX, c, d, new Vec3(-1, 1, 0).normalize(), 2.2f, 3.2f, 6, 0));
        for (int k = 0; k < 2; k++) {
            final int twin = k == 0 ? SOL : NYX;
            AstaFx.later(1 + k * 4, () -> {
                if (!alive(p)) return;
                for (LivingEntity e : enemies(sl, p, p.getBoundingBox().inflate(4.5))) {
                    Vec3 to = e.position().subtract(p.position());
                    if (to.length() <= 4.5 && flat(to).dot(d) > 0.2) hit(p, e, 9 * m, twin);
                }
            });
        }
        CompoundTag r = root(p);
        r.putFloat("res", Math.min(100f, r.getFloat("res") + 30f));
        OverdriveFx.play(sl, c, "slash", 1f, 1.1f);
        sync(p, false);
    }

    /** Eclipse Union: 20 s fused. */
    public static void eclipseUnion(ServerPlayer p, float power) {
        if (!canEclipse(p)) { tell(p, "Both twins at level " + BOND_ECLIPSE + ", and the Twin Wardens beaten"); return; }
        ServerLevel sl = p.serverLevel();
        root(p).putLong("eclipse", now(p) + ECLIPSE);
        Adv.grant(p, "eclipse");
        Vec3 at = p.position();
        FxSend.ring(sl, Fx.VOID, at.add(0, 0.1, 0), 0.4f, 6f, 0.8f, 20);
        FxSend.ring(sl, PAL_SOL, at.add(0, 0.2, 0), 0.3f, 5f, 0.3f, 16);
        FxSend.ring(sl, PAL_NYX, at.add(0, 0.3, 0), 0.3f, 5.5f, 0.3f, 18);
        Fx.pillar(sl, Fx.VOID, at, 5f, 1.6f, 16);
        FxSend.shake(sl, at, 0.5f, 10, 14);
        OverdriveFx.play(sl, at, "eclipse_toll", 1.4f, 1f);
        say(p, SOL, "Together, then.");
        look(p, true);
        sync(p, false);
    }

}
