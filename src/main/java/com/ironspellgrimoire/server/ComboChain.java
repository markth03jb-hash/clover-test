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
            new Combo("sylphs_breath", "tornado_fang", "Gale Fang", Kind.PILLAR, 5, FxSend.FRAME_YUNO, "a column of wind hurls the target skyward"),
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

    // ------------------------------------------------------------------ finishers
    private static void finisher(ServerPlayer p, Combo c, float power) {
        if (!alive(p)) return;
        ServerLevel sl = p.serverLevel();
        LivingEntity t = aimed(p, 32, 30);
        Vec3 at = t != null ? mid(t) : clip(sl, p, p.getEyePosition(), p.getEyePosition().add(p.getLookAngle().scale(12)));
        int K = FxPacket.KEEP_SHAPE;
        FxSend.impactFrame(sl, at, 2, c.frame(), 32);
        sound(sl, at, SoundEvents.PLAYER_ATTACK_CRIT, 1.2f, 0.7f);
        switch (c.kind()) {
            case BURST -> {
                Fx.flash(sl, at, 2.0f);
                FxSend.ring(sl, c.pal(), at, 0.3f, 4.5f, 0.3f, 10);
                FxSend.ring(sl, c.pal(), at.add(0, -0.8, 0), 0.5f, 5.5f, 0.1f, 12);
                FxSend.shake(sl, at, 0.7f, 8, 24);
                for (LivingEntity e : enemies(sl, p, new AABB(at, at).inflate(4.5))) {
                    if (mid(e).distanceTo(at) > 4.5) continue;
                    AstaFx.damage(p, e, power, c.second());
                    Vec3 out = mid(e).subtract(at).normalize().scale(0.6);
                    e.setDeltaMovement(out.x, 0.45, out.z); e.hurtMarked = true;
                }
                sound(sl, at, SoundEvents.GENERIC_EXPLODE, 0.9f, 1.2f);
            }
            case CROSS -> {
                Vec3 look = p.getLookAngle(), rt = rightOf(look), up = rt.cross(look).normalize();
                for (int s = -1; s <= 1; s += 2) {
                    Vec3 d = up.scale(0.7).add(rt.scale(0.7 * s)).scale(2.6);
                    FxSend.beam(sl, c.pal() | K, at.subtract(d), at.add(d), 0.3f, 8);
                    FxSend.beam(sl, 6 | K, at.subtract(d.scale(1.05)), at.add(d.scale(1.05)), 0.05f, 6);
                }
                Fx.impact(sl, c.pal(), at, 1.6f);
                for (LivingEntity e : enemies(sl, p, new AABB(at, at).inflate(3.0))) {
                    if (mid(e).distanceTo(at) > 3.0) continue;
                    AstaFx.damage(p, e, power, c.second());
                }
                sound(sl, at, SoundEvents.PLAYER_ATTACK_SWEEP, 1.3f, 0.6f);
            }
            case PILLAR -> {
                Vec3 g = new Vec3(at.x, (t != null ? t.getY() : at.y - 1), at.z);
                FxSend.beam(sl, c.pal() | K, g, g.add(0, 7, 0), 0.9f, 10);
                FxSend.ring(sl, c.pal(), g.add(0, 0.05, 0), 0.3f, 2.8f, 0.1f, 10);
                for (LivingEntity e : enemies(sl, p, new AABB(g, g.add(0, 4, 0)).inflate(2.0))) {
                    AstaFx.damage(p, e, power, c.second());
                    e.setDeltaMovement(e.getDeltaMovement().x * 0.3, 1.25, e.getDeltaMovement().z * 0.3); e.hurtMarked = true;
                }
                sound(sl, g, SoundEvents.FIREWORK_ROCKET_LAUNCH, 1.2f, 0.6f);
            }
            case STORM -> {
                for (int k = 0; k < 6; k++) {
                    AstaFx.later(k * 5, () -> {
                        if (!alive(p)) return;
                        Vec3 a2 = t != null && t.isAlive() ? mid(t) : at;
                        Vec3 from = a2.add((Math.random() - 0.5) * 6, 4 + Math.random() * 2, (Math.random() - 0.5) * 6);
                        FxSend.beam(sl, c.pal() | K, from, a2, 0.18f, 5);
                        Fx.spark(sl, c.pal(), a2, 0.9f);
                        for (LivingEntity e : enemies(sl, p, new AABB(a2, a2).inflate(1.6))) AstaFx.damage(p, e, power * 0.25f, c.second());
                        sound(sl, a2, SoundEvents.PLAYER_ATTACK_STRONG, 0.8f, 1.2f);
                    });
                }
            }
            case SNARE -> {
                for (LivingEntity e : enemies(sl, p, new AABB(at, at).inflate(4.5))) {
                    if (mid(e).distanceTo(at) > 4.5) continue;
                    ThreadFx.bind(e, 50);
                    FxSend.ring(sl, c.pal() | K, e.position().add(0, 0.2, 0), 0.6f, 0.66f, 0.03f, 50);
                    FxSend.ring(sl, c.pal() | K, e.position().add(0, e.getBbHeight() * 0.7, 0), 0.6f, 0.66f, 0.03f, 50);
                    AstaFx.damage(p, e, power * 0.6f, c.second());
                }
                sound(sl, at, SoundEvents.CHAIN_PLACE, 1.2f, 0.6f);
            }
            default -> {}
        }
    }
}
