package com.ironspellgrimoire.server;

import static com.ironspellgrimoire.server.FxUtil.*;

import com.ironspellgrimoire.IronSpellGrimoire;
import com.ironspellgrimoire.network.FxPacket;
import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Combo chains: cast a grimoire's two linked spells one after the other (the second within 4 seconds of the first)
 * and a finisher follows the second - its name flashes up, a short impact frame, and an extra blow on top of both
 * spells. A few combos change the second spell itself instead (marked "boosts").
 */
@Mod.EventBusSubscriber(modid = IronSpellGrimoire.MODID)
public final class ComboChain {
    private ComboChain() {}

    public static final int WINDOW = 80;
    public enum Kind { BURST, CROSS, PILLAR, STORM, SNARE, BOOST }

    /** One combo: first spell, then second, its name, what the finisher does, colour and impact frame style. */
    public record Combo(String first, String second, String name, Kind kind, int pal, int frame, String what) {}

    public static final List<Combo> COMBOS = List.of(
            // Asta
            new Combo("zetten", "sky_splitter", "Cleave and Rift", Kind.CROSS, 0, FxSend.FRAME_ASTA_SLASH, "an X of anti-magic slashes tears through the target"),
            new Combo("piercing_dash", "zetten", "Dash Cleave", Kind.BURST, 0, FxSend.FRAME_ASTA, "the ground bursts where the cleave lands"),
            new Combo("dweller_slash", "borrowed_slash", "Twin Return", Kind.CROSS, 1, FxSend.FRAME_ASTA, "both swords cut again in a cross"),
            // Yami
            new Combo("dimension_slash", "death_thrust", "Severed Thrust", Kind.PILLAR, 10, FxSend.FRAME_YAMI, "a pillar of darkness erupts under the target"),
            new Combo("black_moon", "black_hole", "Eclipse", Kind.BURST, 10, FxSend.FRAME_YAMI, "the darkness collapses in a blast"),
            new Combo("black_blade", "avidya_slash", "Darkness Hunts", Kind.CROSS, 10, FxSend.FRAME_YAMI, "two dark cuts cross on the target"),
            // Yuno
            new Combo("sylphs_breath", "tornado_fang", "Sylph's Verdict", Kind.SNARE, 5, FxSend.FRAME_YUNO, "Sylph circles the target, binds it in a cutting vortex, then drops one great gale from above"),
            new Combo("wind_blade_shower", "gale_white_bow", "Arrow Gale", Kind.STORM, 5, FxSend.FRAME_YUNO, "a storm of wind blades keeps striking the target"),
            // Nozel
            new Combo("mercury_bullets", "mercury_spears", "Silver Barrage", Kind.STORM, 11, FxSend.FRAME_NOZEL, "mercury keeps raining onto the target"),
            new Combo("silver_rain", "storm_of_blades", "Argent Tempest", Kind.BURST, 11, FxSend.FRAME_NOZEL, "the blades burst outward around the target"),
            // Magna
            new Combo("exploding_fireball", "fireball_barrage", "Barrage Finale", Kind.BURST, 2, FxSend.FRAME_MAGNA, "one last great explosion"),
            new Combo("flame_knuckles", "ring_of_fire", "Burning Ring", Kind.BURST, 2, FxSend.FRAME_MAGNA, "the ring erupts in flame"),
            // Luck
            new Combo("thunder_fist", "thunder_kick", "Thunderclap", Kind.PILLAR, 13, FxSend.FRAME_LUCK, "lightning strikes the target from the ground up"),
            new Combo("lightning_step", "chain_lightning", "Flash Chain", Kind.STORM, 13, FxSend.FRAME_LUCK, "the lightning keeps jumping back to the target"),
            // Mereoleona
            new Combo("calidos_brachium", "ground_punch", "Lioness Crash", Kind.BURST, 14, FxSend.FRAME_MEREOLEONA, "crimson flame bursts out of the ground"),
            new Combo("lioness_charge", "flame_lion_paws", "Pride Pounce", Kind.CROSS, 14, FxSend.FRAME_MEREOLEONA, "two claws of flame rake across the target"),
            // Zenon
            new Combo("bone_spear_volley", "bone_spike_eruption", "Marrow Pike", Kind.PILLAR, 15, FxSend.FRAME_ZENON, "a great bone pike bursts up under the target"),
            new Combo("bone_whips", "ribcage_prison", "Bone Snare", Kind.SNARE, 15, FxSend.FRAME_ZENON, "everyone near the target is pinned in place"),
            // Klaus
            new Combo("violent_rotating_lance", "thunder_arrow", "Lance Lightning", Kind.STORM, 7, FxSend.FRAME_KLAUS, "steel keeps striking the target"),
            new Combo("steel_castle_wall", "steel_prison", "Fortress Lock", Kind.SNARE, 7, FxSend.FRAME_KLAUS, "everyone near the target is locked in place"),
            // Vanessa
            new Combo("binding_threads", "puppet_strings", "Marionette Slam", Kind.BURST, 16, FxSend.FRAME_VANESSA, "the slam bursts out in a ring of threads"),
            new Combo("thread_net", "thread_cage", "Inescapable Weave", Kind.SNARE, 16, FxSend.FRAME_VANESSA, "everyone in the cage is bound in threads"),
            // Finral
            new Combo("short_hop", "sky_drop", "Gentleman's Trapdoor", Kind.BOOST, 17, FxSend.FRAME_FINRAL, "boosts Sky Drop: they fall from 30 blocks instead of 18"),
            new Combo("evasion_gate", "space_deletion", "Erase and Vanish", Kind.BOOST, 17, FxSend.FRAME_FINRAL, "boosts Space Deletion: a much larger tear"));

    private static final Map<UUID, Object[]> LAST = new HashMap<>();      // player -> {spell id, game time}
    private static final Map<UUID, Object[]> BOOSTED = new HashMap<>();   // player -> {spell id, game time}

    /** True if this cast of {@code spell} completed a boosting combo (checked by the spell itself). */
    public static boolean boosted(ServerPlayer p, String spell) {
        Object[] b = BOOSTED.get(p.getUUID());
        return b != null && spell.equals(b[0]) && p.serverLevel().getGameTime() - (Long) b[1] <= 2;
    }

    private static String path(String id) { int i = id.indexOf(':'); return i < 0 ? id : id.substring(i + 1); }

    @SubscribeEvent
    public static void onCast(SpellOnCastEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !e.getSpellId().startsWith(IronSpellGrimoire.MODID + ":")) return;
        String id = path(e.getSpellId());
        long now = p.serverLevel().getGameTime();
        Object[] last = LAST.get(p.getUUID());
        LAST.put(p.getUUID(), new Object[]{id, now});
        if (last == null || now - (Long) last[1] > WINDOW) return;
        for (Combo c : COMBOS) {
            if (!c.first().equals(last[0]) || !c.second().equals(id)) continue;
            LAST.remove(p.getUUID());   // a combo is used up
            float power = 8f;
            try {
                var sp = io.redspace.ironsspellbooks.api.registry.SpellRegistry.getSpell(e.getSpellId());
                if (sp != null) power = Math.max(4f, sp.getSpellPower(e.getSpellLevel(), p) * 0.6f);
            } catch (Throwable ignored) {}
            p.displayClientMessage(Component.literal("COMBO: " + c.name()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
            if (c.kind() == Kind.BOOST) {
                BOOSTED.put(p.getUUID(), new Object[]{id, now});
                sound(p.serverLevel(), p.position(), SoundEvents.PLAYER_LEVELUP, 0.6f, 1.6f);
                return;
            }
            final float pw = power;
            AstaFx.later(12, () -> finisher(p, c, pw));
            return;
        }
    }

    // ------------------------------------------------------------------ finishers: every combo its own
    private static void finisher(ServerPlayer p, Combo c, float power) {
        if (!alive(p)) return;
        ServerLevel sl = p.serverLevel();
        LivingEntity t = aimed(p, 32, 30);
        Vec3 at = t != null ? mid(t) : clip(sl, p, p.getEyePosition(), p.getEyePosition().add(p.getLookAngle().scale(12)));
        Vec3 ground = t != null ? t.position() : FxLib.floor(sl, at);
        Vec3 look = p.getLookAngle(), fl = flat(look), rt = rightOf(look), up = rt.cross(look).normalize();
        if (t != null) FxSend.impactFrame(sl, at, 2, c.frame(), 32);   // only when it actually lands on someone
        String id = c.second();
        switch (c.name()) {
            case "Cleave and Rift" -> {   // each sword has its own: the Slayer's red X, the Dweller's stacked flat anti-magic cuts
                if (AstaFx.isDweller(p)) {
                    for (int k = -1; k <= 1; k++) {   // three horizontal crescents sweep through at different heights, the middle one last
                        final int kk = k;
                        AstaFx.later(k == 0 ? 3 : (k + 1) / 2, () -> {
                            Vec3 o = at.subtract(look.scale(3)).add(0, kk * 0.9, 0);
                            FxSend.crescent(sl, Fx.DARK, o, look, rt, 1.5f, 3.8f, 8, 1.3f);
                            FxSend.ring(sl, Fx.SILVER, at.add(0, kk * 0.9, 0), 0.3f, 2.8f, 0.04f, 7);   // the silver edge of anti magic unmaking what it crosses
                        });
                    }
                    FxSend.cracks(sl, Fx.DARK, at, rt, look, 3.4f, 30, true, (int) sl.getGameTime());
                } else {
                    for (int s = -1; s <= 1; s += 2) {
                        Vec3 u = up.scale(0.7071).add(rt.scale(0.7071 * s));
                        FxSend.crescent(sl, 18, at.subtract(look.scale(3)), look, u, 1.4f, 3.6f, 8, 1.2f);
                    }
                    FxSend.cracks(sl, 18, at, up.add(rt).normalize(), look, 3.2f, 30, true, (int) sl.getGameTime());
                    FxSend.cracks(sl, 18, at, up.subtract(rt).normalize(), look, 3.2f, 30, true, (int) sl.getGameTime() + 9);
                }
                hitAround(p, sl, at, 3.2, power, id, 0.3);
                sound(sl, at, SoundEvents.PLAYER_ATTACK_SWEEP, 1.4f, 0.5f);
            }
            case "Dash Cleave" -> {   // the cleave splits the ground: the Slayer's fissure is red spikes, the Dweller's is black and silver
                final boolean dwl = AstaFx.isDweller(p);
                final int fp = dwl ? Fx.DARK : 18;
                Vec3 g0 = p.position().add(fl.scale(1.5));
                for (int i = 0; i < 9; i++) {
                    final int ii = i;
                    AstaFx.later(i, () -> {
                        Vec3 g = FxLib.floor(sl, g0.add(fl.scale(ii * 1.3)).add(0, 1, 0));
                        if (g == null) return;
                        for (int s = -1; s <= 1; s += 2)
                            FxSend.spike(sl, fp, g.add(rt.scale(0.4 * s)), new Vec3(0, 1, 0).add(rt.scale(0.5 * s)), 1.4f + ii * 0.1f, 0.14f, 22, 2, FxPacket.GROUND);
                        FxSend.ring(sl, dwl ? Fx.SILVER : fp, g.add(0, 0.05, 0), 0.2f, 1.6f, 0.08f, 6);
                        for (LivingEntity e : enemies(sl, p, new AABB(g, g).inflate(1.6, 2, 1.6))) {
                            AstaFx.damage(p, e, power * 0.35f, id);
                            e.setDeltaMovement(e.getDeltaMovement().x, 0.7, e.getDeltaMovement().z); e.hurtMarked = true;
                        }
                        if (ii % 3 == 0) sound(sl, g, SoundEvents.STONE_BREAK, 1f, 0.6f);
                    });
                }
                AstaFx.later(9, () -> {   // the end of the line erupts
                    Vec3 end = FxLib.floor(sl, g0.add(fl.scale(11)).add(0, 1, 0));
                    if (end == null) return;
                    FxSend.wall(sl, fp, end.add(0, 0.2, 0), fl, new Vec3(0, 1, 0), 2.2f, 2.8f, -180, 180, 2.4f, 10, 2, 0);
                    FxLib.shockRing(sl, dwl ? Fx.SILVER : fp, end.add(0, 0.05, 0), 2.4f);
                    FxSend.shake(sl, end, 1.0f, 10, 30);
                    hitAround(p, sl, end.add(0, 1, 0), 3.0, power, id, 0.8);
                    sound(sl, end, SoundEvents.GENERIC_EXPLODE, 1f, 0.8f);
                });
            }
            case "Twin Return" -> {   // both blades fly back through the target from the far side, crossing on it
                for (int s = -1; s <= 1; s += 2) {
                    Vec3 from = at.add(look.scale(5)).add(rt.scale(2.5 * s));
                    Vec3 d = at.subtract(from).normalize();
                    FxSend.crescent(sl, s < 0 ? 1 : 18, from, d, rightOf(d).cross(d).normalize(), 0.9f, 1.8f, 8, 1.4f);
                }
                AstaFx.later(4, () -> hitAround(p, sl, at, 2.6, power, id, 0.2));
                sound(sl, at, SoundEvents.TRIDENT_RETURN, 1f, 0.8f);
            }
            case "Severed Thrust" -> {   // space is cut level through them, then darkness erupts from the cut
                FxSend.wall(sl, 10, at, look, new Vec3(0, 1, 0), 0.1f, 4.0f, -90, 90, 0.04f, 10, 0, 0);
                FxSend.cracks(sl, 10, at, rt, new Vec3(0, 1, 0), 4f, 30, true, (int) sl.getGameTime());
                AstaFx.later(5, () -> {
                    FxSend.beam(sl, 10, ground, ground.add(0, 8, 0), 1.1f, 12);
                    FxSend.ring(sl, 10, ground.add(0, 0.05, 0), 0.4f, 3f, 0.2f, 10);
                    hitAround(p, sl, at, 2.5, power, id, 1.1);
                    sound(sl, at, SoundEvents.WITHER_SHOOT, 0.8f, 0.6f);
                });
            }
            case "Eclipse" -> {   // the dark folds in on itself - rings closing - then bursts out as a dome
                for (int k = 0; k < 4; k++) {
                    final int kk = k;
                    AstaFx.later(k * 2, () -> FxSend.ring(sl, 10, at, 6f - kk * 1.4f, 0.3f, 0.6f, 6));
                }
                pull(p, sl, at, 6, 8);
                AstaFx.later(9, () -> {
                    FxSend.wall(sl, 10, at, new Vec3(1, 0, 0), new Vec3(0, 0, 1), 0.5f, 5f, -180, 180, 4.5f, 10, 0, 0);
                    Fx.flash(sl, at, 2.2f);
                    hitAround(p, sl, at, 5, power, id, 0.6);
                    FxSend.shake(sl, at, 0.9f, 10, 30);
                    sound(sl, at, SoundEvents.GENERIC_EXPLODE, 1f, 0.5f);
                });
            }
            case "Darkness Hunts" -> {   // five dark crescents come at them from five sides, one after another
                for (int i = 0; i < 5; i++) {
                    final int ii = i;
                    AstaFx.later(i * 2, () -> {
                        double a0 = ii * Math.PI * 2 / 5;
                        Vec3 from = at.add(Math.cos(a0) * 5, 1.5 * Math.sin(ii * 1.7), Math.sin(a0) * 5);
                        Vec3 d = at.subtract(from).normalize();
                        FxSend.crescent(sl, 10, from, d, rightOf(d).cross(d).normalize(), 0.7f, 1.4f, 5, 1.1f);
                        AstaFx.later(4, () -> hitAround(p, sl, at, 1.8, power * 0.3f, id, 0.1));
                    });
                }
                sound(sl, at, SoundEvents.PHANTOM_SWOOP, 1f, 0.6f);
            }
            case "Sylph's Verdict" -> {   // Sylph circles them once, binds them in a cutting vortex, then drops one great gale from above
                final Vec3 c0 = at;
                for (int k = 0; k < 24; k++) {
                    final int kk = k;
                    AstaFx.later(k, () -> {
                        double r1 = 5.0 - kk * 0.16, r0 = r1 + 0.16;
                        for (int q = 0; q < 2; q++) {   // two ribbons of wind, one spirit's body, spiralling in
                            double a1 = kk * 0.5 + q * Math.PI, a0 = a1 - 0.5;
                            Vec3 p1 = c0.add(Math.cos(a1) * r1, -0.6 + kk * 0.07 + q * 0.5, Math.sin(a1) * r1);
                            Vec3 p0 = c0.add(Math.cos(a0) * r0, -0.6 + (kk - 1) * 0.07 + q * 0.5, Math.sin(a0) * r0);
                            FxSend.trail(sl, 5, p0, p1, 0.16f, 8);
                            if (kk % 3 == 0) FxSend.trail(sl, 6, p0, p1, 0.04f, 6);
                        }
                        if (kk % 4 == 0) WindFx.star(sl, c0.add(Math.cos(kk) * (r1 + 0.4), 0.8 + (kk % 3) * 0.5, Math.sin(kk) * (r1 + 0.4)), look, 0.35f, 8);
                        for (LivingEntity e : enemies(sl, p, new AABB(c0, c0).inflate(4.2))) {   // held in the vortex, rising a little
                            e.setDeltaMovement(0, 0.05, 0); e.hurtMarked = true; e.fallDistance = 0;
                            if (kk % 6 == 5) {
                                AstaFx.damage(p, e, power * 0.15f, id);
                                Vec3 m = mid(e); Vec3 cut = new Vec3(Math.cos(kk), 0.3, Math.sin(kk));
                                FxSend.beam(sl, 6, m.subtract(cut), m.add(cut), 0.08f, 3);
                            }
                        }
                    });
                }
                AstaFx.later(25, () -> {   // the verdict: a column of wind drops out of the sky and slams them down
                    FxSend.beam(sl, 5, c0.add(0, 16, 0), c0, 1.5f, 6);
                    FxSend.beam(sl, 6, c0.add(0, 16, 0), c0, 0.35f, 5);
                    FxLib.shockRing(sl, 5, ground.add(0, 0.05, 0), 3.6f);
                    FxSend.ring(sl, 6, ground.add(0, 0.2, 0), 0.4f, 6f, 0.05f, 8);
                    WindFx.star(sl, c0.add(0, 1, 0), look, 3.0f, 14);
                    for (LivingEntity e : enemies(sl, p, new AABB(c0, c0).inflate(4.2, 5, 4.2))) {
                        AstaFx.damage(p, e, power * 1.1f, id);
                        e.setDeltaMovement(0, -1.8, 0); e.hurtMarked = true;
                    }
                    FxSend.shake(sl, c0, 0.8f, 8, 30);
                    sound(sl, c0, SoundEvents.ENDER_DRAGON_FLAP, 1.2f, 0.5f);
                    sound(sl, c0, SoundEvents.GENERIC_EXPLODE, 0.8f, 1.4f);
                });
                sound(sl, c0, SoundEvents.ELYTRA_FLYING, 1f, 1.4f);
                sound(sl, c0, SoundEvents.AMETHYST_BLOCK_RESONATE, 1f, 1.8f);
            }
            case "Arrow Gale" -> {   // eight arrows of wind appear in a ring round them and fire inward, one by one
                for (int i = 0; i < 8; i++) {
                    final int ii = i;
                    AstaFx.later(i * 2, () -> {
                        double a0 = ii * Math.PI / 4;
                        Vec3 from = at.add(Math.cos(a0) * 6, 1.5, Math.sin(a0) * 6);
                        FxSend.trail(sl, 5, from, at, 0.12f, 6);
                        FxSend.trail(sl, 6, from, at, 0.03f, 5);
                        hitAround(p, sl, at, 1.6, power * 0.18f, id, 0);
                    });
                }
                sound(sl, at, SoundEvents.ARROW_SHOOT, 1f, 1.4f);
            }
            case "Silver Barrage" -> {   // drops of mercury hail down on them from above
                for (int i = 0; i < 10; i++) {
                    final int ii = i;
                    AstaFx.later(i * 2, () -> {
                        Vec3 sky = at.add((Math.random() - 0.5) * 3, 9, (Math.random() - 0.5) * 3);
                        MercuryFx.sphere(sl, sky, new Vec3(0, -1.8, 0), 0.3f, 5, 0);
                        AstaFx.later(5, () -> { Fx.spark(sl, 11, at, 0.7f); hitAround(p, sl, at, 1.8, power * 0.15f, id, 0); });
                    });
                }
                sound(sl, at, SoundEvents.TRIDENT_RIPTIDE_1, 1f, 1.4f);
            }
            case "Argent Tempest" -> {   // mercury blades burst outward from them in a full circle
                for (int i = 0; i < 10; i++) {
                    double a0 = i * Math.PI / 5;
                    Vec3 d = new Vec3(Math.cos(a0), 0, Math.sin(a0));
                    FxSend.crescent(sl, 11, at, d, new Vec3(0, 1, 0), 0.6f, 1.5f, 8, 0.9f);
                }
                MercuryFx.sphere(sl, at, Vec3.ZERO, 1.1f, 6, 1);
                hitAround(p, sl, at, 4.5, power, id, 0.4);
                sound(sl, at, SoundEvents.ANVIL_LAND, 0.8f, 1.6f);
            }
            case "Barrage Finale" -> {   // one enormous fireball falls on them from the sky
                Vec3 sky = at.add(0, 14, 0);
                FireFx.fireball(sl, sky, new Vec3(0, -1.4, 0), 1.6f, 4.5f, 10, true);
                AstaFx.later(10, () -> {
                    FxLib.shockRing(sl, 2, ground.add(0, 0.05, 0), 4f);
                    FxSend.shake(sl, ground, 1.0f, 12, 32);
                    hitAround(p, sl, at, 5.5, power * 1.2f, id, 0.7);
                    sound(sl, ground, SoundEvents.GENERIC_EXPLODE, 1.3f, 0.6f);
                });
            }
            case "Burning Ring" -> {   // pillars of flame burst up in a ring round them, then the ring closes in
                for (int i = 0; i < 8; i++) {
                    double a0 = i * Math.PI / 4;
                    Vec3 g = ground.add(Math.cos(a0) * 3.5, 0, Math.sin(a0) * 3.5);
                    FxSend.beam(sl, 2, g, g.add(0, 4.5, 0), 0.5f, 14);
                }
                AstaFx.later(8, () -> {
                    FxSend.ring(sl, 2, ground.add(0, 0.1, 0), 3.5f, 0.3f, 1.2f, 8);
                    hitAround(p, sl, at, 3.8, power, id, 0.5);
                    sound(sl, ground, SoundEvents.FIRECHARGE_USE, 1.2f, 0.6f);
                });
                sound(sl, ground, SoundEvents.BLAZE_SHOOT, 1f, 0.6f);
            }
            case "Thunderclap" -> {   // lightning strikes down on them and splits back up out of the ground round them
                LightningFx.bolt(sl, at.add(0, 16, 0), ground, 0.35f, 9, 4);
                for (int i = 0; i < 6; i++) {
                    double a0 = i * Math.PI / 3;
                    Vec3 g = ground.add(Math.cos(a0) * 2.2, 0, Math.sin(a0) * 2.2);
                    LightningFx.bolt(sl, g, g.add(0, 3.5, 0), 0.1f, 6, 1);
                }
                FxSend.ring(sl, 13, ground.add(0, 0.05, 0), 0.3f, 3.5f, 0.1f, 8);
                hitAround(p, sl, at, 3, power, id, 0.9);
                sound(sl, ground, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.8f, 1.4f);
            }
            case "Flash Chain" -> {   // he flashes round them six times - a star of lightning drawn round the target
                Vec3[] pts = new Vec3[6];
                for (int i = 0; i < 6; i++) { double a0 = i * Math.PI * 2 / 6; pts[i] = at.add(Math.cos(a0) * 3, 0.3 * Math.sin(i * 2.0), Math.sin(a0) * 3); }
                for (int i = 0; i < 6; i++) {
                    final int ii = i;
                    AstaFx.later(i, () -> {
                        LightningFx.bolt(sl, pts[ii], pts[(ii + 2) % 6], 0.12f, 5, 1);
                        LightningFx.bolt(sl, pts[ii], at, 0.08f, 4, 0);
                        hitAround(p, sl, at, 2.0, power * 0.2f, id, 0);
                    });
                }
                sound(sl, at, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.8f, 1.8f);
            }
            case "Lioness Crash" -> {   // three great claws of crimson flame rake down through them
                for (int i = -1; i <= 1; i++) {
                    Vec3 o = at.add(rt.scale(i * 0.9)).add(0, 2.5, 0);
                    FxSend.crescent(sl, 14, o, new Vec3(0, -1, 0).add(look.scale(0.3)).normalize(), rt, 0.5f, 2.2f, 6, 0.7f);
                }
                AstaFx.later(4, () -> {
                    FxSend.wall(sl, 14, ground.add(0, 0.1, 0), fl, new Vec3(0, 1, 0), 1.5f, 3.5f, -180, 180, 1.8f, 10, 0, 0);
                    hitAround(p, sl, at, 3, power, id, 0.6);
                    sound(sl, ground, SoundEvents.RAVAGER_ROAR, 0.8f, 1.2f);
                });
            }
            case "Pride Pounce" -> {   // a column of crimson flame comes down on them like a pouncing lion
                FxSend.beam(sl, 14, at.add(0, 12, 0), ground, 1.4f, 10);
                AstaFx.later(3, () -> {
                    FxLib.shockRing(sl, 14, ground.add(0, 0.05, 0), 3.2f);
                    FxSend.wall(sl, 14, ground.add(0, 0.1, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1), 0.5f, 4.5f, -180, 180, 1.4f, 10, 0, 0);
                    FxSend.shake(sl, ground, 1.0f, 10, 30);
                    hitAround(p, sl, at, 4, power, id, 0.8);
                    sound(sl, ground, SoundEvents.GENERIC_EXPLODE, 1f, 0.7f);
                });
            }
            case "Marrow Pike" -> {   // a colossal spike of bone bursts up under them, a crown of smaller bones round it
                BoneFx.bone(sl, BoneFx.SPIKE, ground.add(0, 2.4, 0), new Vec3(0, 1, 0), rt, 2.4f, 0.55f, 30, 3, FxPacket.GROUND, 0, Vec3.ZERO, 0);
                for (int i = 0; i < 8; i++) {
                    double a0 = i * Math.PI / 4;
                    Vec3 out = new Vec3(Math.cos(a0), 0, Math.sin(a0));
                    Vec3 dir = new Vec3(0, 1, 0).add(out.scale(0.6)).normalize();
                    BoneFx.bone(sl, BoneFx.SPIKE, ground.add(out.scale(1.6)).add(dir.scale(0.8)), dir, out, 0.8f, 0.2f, 26, 3, FxPacket.GROUND, 0, Vec3.ZERO, 0);
                }
                hitAround(p, sl, at, 2.4, power, id, 1.3);
                sound(sl, ground, SoundEvents.BONE_BLOCK_BREAK, 1.2f, 0.6f);
                sound(sl, ground, SoundEvents.SKELETON_HURT, 0.8f, 0.5f);
            }
            case "Bone Snare" -> {   // ribs of bone burst up round each of them and close like a cage
                for (LivingEntity e : enemies(sl, p, new AABB(at, at).inflate(4.5))) {
                    if (mid(e).distanceTo(at) > 4.5) continue;
                    Vec3 g = e.position();
                    for (int i = 0; i < 6; i++) {
                        double a0 = i * Math.PI / 3;
                        Vec3 out = new Vec3(Math.cos(a0), 0, Math.sin(a0));
                        BoneFx.bone(sl, BoneFx.RIB, g.add(out.scale(0.9)).add(0, 1.0, 0), new Vec3(0, 1, 0), out.scale(-1), 1.0f, 0.1f, 50, 3, FxPacket.GROUND, 0, Vec3.ZERO, 0);
                    }
                    ThreadFx.bind(e, 50);
                    AstaFx.damage(p, e, power * 0.6f, id);
                }
                sound(sl, at, SoundEvents.BONE_BLOCK_PLACE, 1.2f, 0.6f);
            }
            case "Lance Lightning" -> {   // steel lances rain down on them one after another
                for (int i = 0; i < 7; i++) {
                    final int ii = i;
                    AstaFx.later(i * 2, () -> {
                        Vec3 g = ground.add((Math.random() - 0.5) * 2.4, 0, (Math.random() - 0.5) * 2.4);
                        FxSend.spike(sl, 7, g.add(0, 7, 0), new Vec3(0, -1, 0), 2.2f, 0.16f, 14, 0, FxPacket.HOLD);
                        AstaFx.later(2, () -> {
                            FxSend.spike(sl, 7, g, new Vec3(0, 1, 0), 1.6f, 0.16f, 20, 1, FxPacket.GROUND);
                            Fx.spark(sl, 7, g.add(0, 0.3, 0), 0.6f);
                            hitAround(p, sl, g.add(0, 1, 0), 1.6, power * 0.2f, id, 0);
                        });
                        if (ii % 2 == 0) sound(sl, g, SoundEvents.ANVIL_PLACE, 0.6f, 1.4f);
                    });
                }
            }
            case "Fortress Lock" -> {   // walls of steel slam up on four sides of each of them
                for (LivingEntity e : enemies(sl, p, new AABB(at, at).inflate(4.5))) {
                    if (mid(e).distanceTo(at) > 4.5) continue;
                    Vec3 g = e.position();
                    for (int i = 0; i < 4; i++) {
                        double a0 = i * Math.PI / 2;
                        Vec3 out = new Vec3(Math.cos(a0), 0, Math.sin(a0));
                        FxSend.box(sl, 7, g.add(out.scale(1.0)).add(0, 1.0, 0), rightOf(out), new Vec3(0, 1, 0), 0.9f, 0.08f, 1.0f, 50, 3, FxPacket.GROUND);
                    }
                    ThreadFx.bind(e, 50);
                    AstaFx.damage(p, e, power * 0.6f, id);
                }
                sound(sl, at, SoundEvents.IRON_DOOR_CLOSE, 1.2f, 0.6f);
            }
            case "Marionette Slam" -> {   // more strings from all sides yank them back up - and slam them down again
                if (t != null) {
                    for (int i = 0; i < 8; i++) {
                        double a0 = i * Math.PI / 4;
                        ThreadFx.thread(sl, t.position().add(Math.cos(a0) * 5, 6, Math.sin(a0) * 5), mid(t), 14);
                    }
                    t.setDeltaMovement(0, 1.3, 0); t.hurtMarked = true;
                    AstaFx.later(8, () -> { if (t.isAlive()) { t.setDeltaMovement(0, -3.0, 0); t.hurtMarked = true; } });
                    AstaFx.later(12, () -> {
                        if (!t.isAlive()) return;
                        Vec3 g = t.position();
                        for (int i = 0; i < 12; i++) {
                            double a0 = i * Math.PI / 6;
                            ThreadFx.thread(sl, g.add(0, 0.1, 0), g.add(Math.cos(a0) * 3.5, 0.1, Math.sin(a0) * 3.5), 16);
                        }
                        FxSend.shake(sl, g, 0.8f, 8, 24);
                        hitAround(p, sl, g.add(0, 1, 0), 3.5, power, id, 0.5);
                        sound(sl, g, SoundEvents.GENERIC_EXPLODE, 0.7f, 1.3f);
                    });
                }
            }
            case "Inescapable Weave" -> {   // every one of them is spun into a cocoon of thread where they stand
                for (LivingEntity e : enemies(sl, p, new AABB(at, at).inflate(5.5))) {
                    if (mid(e).distanceTo(at) > 5.5) continue;
                    ThreadFx.wrap(sl, e, 16, 70);
                    ThreadFx.wrap(sl, e, 16, 70);
                    ThreadFx.form(e, 0, 70);
                    ThreadFx.bind(e, 70);
                    AstaFx.damage(p, e, power * 0.5f, id);
                }
                sound(sl, at, SoundEvents.WOOL_PLACE, 1.2f, 0.5f);
            }
            default -> hitAround(p, sl, at, 3, power, id, 0.4);
        }
    }

    /** Damage everything within r of c and knock it up a little. */
    private static void hitAround(ServerPlayer p, ServerLevel sl, Vec3 c, double r, float dmg, String id, double lift) {
        for (LivingEntity e : enemies(sl, p, new AABB(c, c).inflate(r))) {
            if (mid(e).distanceTo(c) > r + e.getBbWidth() * 0.5) continue;
            AstaFx.damage(p, e, dmg, id);
            if (lift > 0) { e.setDeltaMovement(e.getDeltaMovement().x, lift, e.getDeltaMovement().z); e.hurtMarked = true; }
        }
    }

    private static void pull(ServerPlayer p, ServerLevel sl, Vec3 c, double r, int ticks) {
        for (int k = 0; k < ticks; k++) {
            AstaFx.later(k, () -> {
                for (LivingEntity e : enemies(sl, p, new AABB(c, c).inflate(r))) {
                    Vec3 in = c.subtract(mid(e));
                    if (in.length() < 0.5) continue;
                    Vec3 v = in.normalize().scale(0.35);
                    e.setDeltaMovement(v.x, v.y * 0.5, v.z); e.hurtMarked = true;
                }
            });
        }
    }
}
