package com.example.arcaneaddon.server;

import com.example.arcaneaddon.ArcaneAddon;
import com.example.arcaneaddon.registry.AddonSpellRegistry;
import io.redspace.ironsspellbooks.damage.DamageSources;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.Random;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Effects of the Asta-themed grimoire spells (Zetten, Sky Splitter, Anti Magic, Ki Sense, Instinct Guard, Devil Union). */
@Mod.EventBusSubscriber(modid = ArcaneAddon.MODID)
public class AstaFx {
    private static final DustParticleOptions CRIMSON = new DustParticleOptions(new Vector3f(0.8f, 0.05f, 0.1f), 1.6f);
    private static final DustParticleOptions BLACK = new DustParticleOptions(new Vector3f(0.05f, 0.0f, 0.08f), 1.8f);
    private static final DustColorTransitionOptions EMBER = new DustColorTransitionOptions(new Vector3f(1f, 0.15f, 0.1f), new Vector3f(0.1f, 0f, 0.05f), 1.4f);

    private static final int GUARD_TICKS = 30;          // Instinct Guard: 1.5 s of untouchable
    private static final int UNION_TICKS = 50 * 20;     // Devil Union: 50 s, like the manga
    private static final Map<UUID, Long> GUARD = new HashMap<>();
    private static final Map<UUID, Long> UNION = new HashMap<>();

    private static void sound(ServerLevel sl, Vec3 at, SoundEvent s, float vol, float pitch) {
        sl.playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, vol, pitch);
    }
    private static void burst(ServerLevel sl, ParticleOptions o, Vec3 c, int n, double sp, double spread) {
        sl.sendParticles(o, c.x, c.y, c.z, n, spread, spread, spread, sp);
    }
    private static void ring(ServerLevel sl, ParticleOptions o, Vec3 c, int n, double speed) {
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n;
            sl.sendParticles(o, c.x, c.y, c.z, 0, Math.cos(a), 0.0, Math.sin(a), speed);
        }
    }
    private static void damage(ServerPlayer p, LivingEntity t, float dmg, String id) {
        var spell = AddonSpellRegistry.SPELLS.getEntries().stream().filter(e -> e.getId().getPath().equals(id)).findFirst().map(e -> e.get()).orElse(null);
        if (spell != null) DamageSources.applyDamage(t, dmg, spell.getDamageSource(p));
        else t.hurt(p.damageSources().playerAttack(p), dmg);
    }

    // ------------------------------------------------------------------ Zetten: huge downward slash
    public static void zetten(ServerPlayer p, float dmg) {
        ServerLevel sl = p.serverLevel();
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        Vec3 dir = new Vec3(look.x, 0, look.z).normalize();
        Vec3 center = p.position().add(dir.scale(2.6));
        List<LivingEntity> targets = sl.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(3.2, 2.5, 3.2),
                t -> t != p && t.isAlive() && !t.isAlliedTo(p) && t.position().subtract(p.position()).multiply(1, 0, 1).normalize().dot(dir) > 0.2);
        for (LivingEntity t : targets) {
            damage(p, t, dmg, "zetten");
            t.knockback(1.2, -dir.x, -dir.z);
            burst(sl, ParticleTypes.CRIT, t.position().add(0, t.getBbHeight() * 0.5, 0), 25, 0.5, 0.4);
            burst(sl, CRIMSON, t.position().add(0, t.getBbHeight() * 0.5, 0), 25, 0.12, 0.4);
        }
        // the slash arc: a vertical crescent that sweeps from above your head down to the ground
        for (int i = 0; i <= 28; i++) {
            double th = Math.toRadians(80 - 150.0 * i / 28);
            Vec3 pt = eye.add(dir.scale(Math.cos(th) * 3.4)).add(0, Math.sin(th) * 3.0 - 0.3, 0);
            burst(sl, EMBER, pt, 2, 0.01, 0.08);
            if (i % 4 == 0) burst(sl, ParticleTypes.SWEEP_ATTACK, pt, 1, 0, 0);
            if (i % 3 == 0) burst(sl, ParticleTypes.SOUL_FIRE_FLAME, pt, 1, 0.02, 0.05);
        }
        // impact: shockwave along the ground
        Vec3 hit = p.position().add(dir.scale(3.2)).add(0, 0.1, 0);
        ring(sl, CRIMSON, hit, 32, 0.5);
        burst(sl, ParticleTypes.EXPLOSION, hit.add(0, 0.4, 0), 2, 0, 0.5);
        burst(sl, ParticleTypes.CLOUD, hit, 20, 0.15, 0.4);
        burst(sl, ParticleTypes.FLASH, hit.add(0, 0.5, 0), 1, 0, 0);
        sound(sl, p.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.2f, 0.5f);
        sound(sl, hit, SoundEvents.GENERIC_EXPLODE, 0.8f, 1.2f);
        sound(sl, hit, SoundEvents.PLAYER_ATTACK_CRIT, 1.0f, 0.6f);
    }

    // ------------------------------------------------------------------ Sky Splitter: ranged beam that cleaves everything in a line
    public static void skySplitter(ServerPlayer p, float dmg) {
        ServerLevel sl = p.serverLevel();
        Vec3 start = p.getEyePosition();
        Vec3 dir = p.getLookAngle();
        double len = 26;
        Vec3 end = start.add(dir.scale(len));
        List<LivingEntity> hit = new ArrayList<>();
        for (LivingEntity t : sl.getEntitiesOfClass(LivingEntity.class, new AABB(start, end).inflate(2.5), x -> x != p && x.isAlive() && !x.isAlliedTo(p))) {
            if (t.getBoundingBox().inflate(1.1).clip(start, end).isPresent()) hit.add(t);
        }
        for (LivingEntity t : hit) {
            damage(p, t, dmg, "sky_splitter");
            burst(sl, ParticleTypes.CRIT, t.position().add(0, t.getBbHeight() * 0.5, 0), 30, 0.6, 0.4);
            burst(sl, ParticleTypes.EXPLOSION, t.position().add(0, t.getBbHeight() * 0.5, 0), 1, 0, 0);
            sound(sl, t.position(), SoundEvents.PLAYER_ATTACK_STRONG, 1f, 0.6f);
        }
        for (int i = 0; i <= len; i++) {
            Vec3 c = start.add(dir.scale(i));
            for (int k = -2; k <= 2; k++) burst(sl, k == 0 ? ParticleTypes.END_ROD : EMBER, c.add(0, k * 0.7, 0), 1, 0.01, 0.05);
            if (i % 2 == 0) burst(sl, ParticleTypes.SWEEP_ATTACK, c, 1, 0, 0);
            if (i % 3 == 0) burst(sl, ParticleTypes.SOUL_FIRE_FLAME, c, 2, 0.03, 0.2);
        }
        burst(sl, ParticleTypes.FLASH, start.add(dir), 1, 0, 0);
        burst(sl, ParticleTypes.FLASH, end, 1, 0, 0);
        sound(sl, p.position(), SoundEvents.LIGHTNING_BOLT_THUNDER, 0.6f, 1.6f);
        sound(sl, p.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.2f, 0.4f);
        sound(sl, p.position(), SoundEvents.TRIDENT_THUNDER, 0.8f, 1.3f);
    }

    // ------------------------------------------------------------------ Demon Dweller Sword: projected slash of anti magic
    private static final Random RND = new Random();
    private static final String[] ELEMENTS = {"fire", "ice", "lightning", "wind"};

    /** borrowed=false: pure anti magic crescent that also strips effects. borrowed=true: it takes on a random element. */
    public static void dwellerSlash(ServerPlayer p, float dmg, boolean borrowed) {
        ServerLevel sl = p.serverLevel();
        Vec3 start = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        double len = 20;
        Vec3 end = start.add(look.scale(len));
        String el = borrowed ? ELEMENTS[RND.nextInt(ELEMENTS.length)] : null;
        ParticleOptions col = el == null ? EMBER : switch (el) {
            case "fire" -> new DustParticleOptions(new Vector3f(1f, 0.45f, 0.05f), 1.6f);
            case "ice" -> new DustParticleOptions(new Vector3f(0.55f, 0.85f, 1f), 1.6f);
            case "lightning" -> new DustParticleOptions(new Vector3f(1f, 0.95f, 0.3f), 1.6f);
            default -> new DustParticleOptions(new Vector3f(0.8f, 1f, 0.85f), 1.6f);
        };
        ParticleOptions spark = el == null ? ParticleTypes.SOUL_FIRE_FLAME : switch (el) {
            case "fire" -> ParticleTypes.FLAME;
            case "ice" -> ParticleTypes.SNOWFLAKE;
            case "lightning" -> ParticleTypes.ELECTRIC_SPARK;
            default -> ParticleTypes.CLOUD;
        };
        // hit everything the slash passes through
        for (LivingEntity t : sl.getEntitiesOfClass(LivingEntity.class, new AABB(start, end).inflate(2.5), x -> x != p && x.isAlive() && !x.isAlliedTo(p))) {
            if (!t.getBoundingBox().inflate(1.3).clip(start, end).isPresent()) continue;
            float d = dmg;
            if ("lightning".equals(el)) d += 4;
            damage(p, t, d, borrowed ? "borrowed_slash" : "dweller_slash");
            Vec3 c = t.position().add(0, t.getBbHeight() * 0.5, 0);
            if (el == null) t.removeAllEffects();   // anti magic
            else switch (el) {
                case "fire" -> t.setSecondsOnFire(6);
                case "ice" -> { t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 2)); t.setTicksFrozen(t.getTicksRequiredToFreeze() + 60); }
                case "lightning" -> sound(sl, c, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.5f, 1.8f);
                default -> t.push(0, 0.9, 0);
            }
            burst(sl, spark, c, 20, 0.15, 0.4);
            burst(sl, col, c, 20, 0.12, 0.4);
            burst(sl, ParticleTypes.CRIT, c, 20, 0.5, 0.4);
            sound(sl, c, SoundEvents.PLAYER_ATTACK_CRIT, 1f, 1.3f);
        }
        // the visible slash: crescents (tilted 30 degrees) travelling away, growing as they fly
        Vec3 right = look.cross(new Vec3(0, 1, 0)).normalize();
        Vec3 up = right.cross(look).normalize();
        double tilt = Math.toRadians(30);
        for (int k = 0; k < 6; k++) {
            double dist = 1.5 + k * 3.2, rad = 0.9 + k * 0.25;
            Vec3 c = start.add(look.scale(dist));
            for (int i = 0; i <= 16; i++) {
                double a = Math.toRadians(-70 + 140.0 * i / 16);
                double ox = Math.sin(a) * rad, oy = Math.cos(a) * rad - rad * 0.55;
                Vec3 pt = c.add(right.scale(ox * Math.cos(tilt) - oy * Math.sin(tilt))).add(up.scale(ox * Math.sin(tilt) + oy * Math.cos(tilt)));
                burst(sl, col, pt, 1, 0.01, 0.03);
                if (i % 4 == 0) burst(sl, spark, pt, 1, 0.02, 0.04);
            }
        }
        burst(sl, ParticleTypes.SWEEP_ATTACK, start.add(look.scale(1.6)), 1, 0, 0);
        burst(sl, ParticleTypes.FLASH, start.add(look.scale(1.2)), 1, 0, 0);
        sound(sl, p.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.2f, 0.7f);
        sound(sl, p.position(), SoundEvents.TRIDENT_RIPTIDE_1, 0.7f, 1.4f);
        if (el != null) {
            sound(sl, p.position(), SoundEvents.ENCHANTMENT_TABLE_USE, 0.8f, 1.4f);
            p.displayClientMessage(Component.translatable("ui.arcane_addon.borrowed." + el).withStyle(ChatFormatting.DARK_RED), true);
        }
    }

    // ------------------------------------------------------------------ Anti Magic: strips every magical effect around you
    public static void antiMagic(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        // cleanse harmful effects from yourself (you have no magic to drain)
        new ArrayList<>(p.getActiveEffects()).stream().filter(e -> !e.getEffect().isBeneficial()).forEach(e -> p.removeEffect(e.getEffect()));
        int stripped = 0;
        for (LivingEntity t : sl.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(9), x -> x != p && x.isAlive() && !x.isAlliedTo(p))) {
            if (!t.getActiveEffects().isEmpty()) { t.removeAllEffects(); stripped++; }
            burst(sl, ParticleTypes.ENCHANTED_HIT, t.position().add(0, t.getBbHeight() * 0.5, 0), 12, 0.3, 0.4);
        }
        Vec3 c = p.position().add(0, 0.2, 0);
        for (int r = 0; r < 4; r++) ring(sl, r % 2 == 0 ? BLACK : CRIMSON, c.add(0, r * 0.5, 0), 40, 0.25 + r * 0.1);
        burst(sl, ParticleTypes.FLASH, p.position().add(0, 1, 0), 1, 0, 0);
        burst(sl, ParticleTypes.REVERSE_PORTAL, p.position().add(0, 1, 0), 40, 0.15, 1.0);
        sound(sl, p.position(), SoundEvents.BEACON_DEACTIVATE, 1.2f, 0.6f);
        sound(sl, p.position(), SoundEvents.ENCHANTMENT_TABLE_USE, 1f, 0.5f);
    }

    // ------------------------------------------------------------------ Ki Sense: everything nearby is revealed
    public static void kiSense(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        for (LivingEntity t : sl.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(32), x -> x != p && x.isAlive())) {
            t.addEffect(new MobEffectInstance(MobEffects.GLOWING, 12 * 20, 0, false, false));
            burst(sl, EMBER, t.position().add(0, t.getBbHeight() + 0.3, 0), 6, 0.02, 0.2);
        }
        Vec3 c = p.position().add(0, 0.1, 0);
        for (int r = 0; r < 5; r++) ring(sl, r % 2 == 0 ? CRIMSON : ParticleTypes.END_ROD, c.add(0, 0.3, 0), 36, 0.35 + r * 0.25);
        sound(sl, p.position(), SoundEvents.BEACON_ACTIVATE, 1f, 1.6f);
        sound(sl, p.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 1f, 0.7f);
    }

    // ------------------------------------------------------------------ Instinct Guard: a moment where nothing can touch you
    public static void instinct(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        GUARD.put(p.getUUID(), sl.getGameTime() + GUARD_TICKS);
        burst(sl, ParticleTypes.FLASH, p.position().add(0, 1, 0), 1, 0, 0);
        ring(sl, CRIMSON, p.position().add(0, 1, 0), 30, 0.3);
        sound(sl, p.position(), SoundEvents.ENDERMAN_TELEPORT, 0.8f, 1.6f);
        sound(sl, p.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 1f, 1.8f);
    }

    // ------------------------------------------------------------------ Devil Union: Liebe merges with you for 50 seconds
    public static void devilUnion(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        UNION.put(p.getUUID(), sl.getGameTime() + UNION_TICKS);
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, UNION_TICKS, 2, false, false));
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, UNION_TICKS, 2, false, false));
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, UNION_TICKS, 1, false, false));
        p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, UNION_TICKS, 1, false, false));
        p.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, UNION_TICKS, 1, false, false));
        Vec3 c = p.position();
        for (int r = 0; r < 5; r++) ring(sl, r % 2 == 0 ? BLACK : CRIMSON, c.add(0, 0.1 + r * 0.4, 0), 44, 0.2 + r * 0.12);
        burst(sl, ParticleTypes.FLASH, c.add(0, 1, 0), 2, 0, 0.1);
        burst(sl, ParticleTypes.LARGE_SMOKE, c.add(0, 1, 0), 40, 0.08, 0.6);
        burst(sl, ParticleTypes.SOUL_FIRE_FLAME, c.add(0, 1, 0), 40, 0.1, 0.5);
        burst(sl, ParticleTypes.SCULK_SOUL, c.add(0, 1, 0), 20, 0.1, 0.5);
        sound(sl, c, SoundEvents.WITHER_SPAWN, 0.5f, 1.6f);
        sound(sl, c, SoundEvents.ENDER_DRAGON_GROWL, 0.6f, 0.7f);
        sound(sl, c, SoundEvents.BEACON_POWER_SELECT, 1f, 0.5f);
    }

    // ------------------------------------------------------------------ events
    @SubscribeEvent
    public static void onAttack(LivingAttackEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            Long until = GUARD.get(p.getUUID());
            if (until != null) {
                if (p.level().getGameTime() <= until) {
                    e.setCanceled(true);
                    if (p.level() instanceof ServerLevel sl) {
                        burst(sl, ParticleTypes.CRIT, p.position().add(0, 1, 0), 15, 0.5, 0.4);
                        burst(sl, ParticleTypes.ENCHANTED_HIT, p.position().add(0, 1, 0), 10, 0.3, 0.4);
                        sound(sl, p.position(), SoundEvents.SHIELD_BLOCK, 0.8f, 1.5f);
                    }
                } else GUARD.remove(p.getUUID());
            }
        }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        ServerLevel sl = p.serverLevel();
        long now = sl.getGameTime();
        Long g = GUARD.get(p.getUUID());
        if (g != null) {
            if (now > g) GUARD.remove(p.getUUID());
            else burst(sl, ParticleTypes.ENCHANT, p.position().add(0, 1, 0), 3, 0.4, 0.6);
        }
        Long u = UNION.get(p.getUUID());
        if (u != null) {
            if (now > u) {
                UNION.remove(p.getUUID());
                ring(sl, BLACK, p.position().add(0, 0.2, 0), 30, 0.3);
                sound(sl, p.position(), SoundEvents.BEACON_DEACTIVATE, 1f, 0.8f);
            } else {
                // devil aura: black smoke, crimson sparks, horns of flame
                if (now % 2 == 0) {
                    burst(sl, BLACK, p.position().add(0, 1, 0), 4, 0.01, 0.45);
                    burst(sl, EMBER, p.position().add(0, 1, 0), 3, 0.02, 0.45);
                }
                if (now % 6 == 0) {
                    burst(sl, ParticleTypes.SOUL_FIRE_FLAME, p.position().add(0, p.getBbHeight() + 0.1, 0), 2, 0.02, 0.15);
                    burst(sl, ParticleTypes.LARGE_SMOKE, p.position().add(0, 0.2, 0), 1, 0.01, 0.3);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        GUARD.remove(e.getEntity().getUUID());
        UNION.remove(e.getEntity().getUUID());
    }
}
