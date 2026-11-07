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
    public static boolean isDweller(ServerPlayer p) { return p.getMainHandItem().is(com.example.arcaneaddon.registry.AddonItemRegistry.DEMON_DWELLER_SWORD.get()); }

    static void damage(ServerPlayer p, LivingEntity t, float dmg, String id) {
        var spell = AddonSpellRegistry.SPELLS.getEntries().stream().filter(e -> e.getId().getPath().equals(id)).findFirst().map(e -> e.get()).orElse(null);
        if (spell != null) DamageSources.applyDamage(t, dmg, spell.getDamageSource(p));
        else t.hurt(p.damageSources().playerAttack(p), dmg);
    }

    private static void variantMsg(ServerPlayer p) {
        p.displayClientMessage(Component.translatable("ui.arcane_addon.variant.dweller").withStyle(ChatFormatting.DARK_PURPLE), true);
    }

    // ------------------------------------------------------------------ Zetten: huge downward slash (Slayer) / fast X of two slashes (Dweller)
    public static void zetten(ServerPlayer p, float dmg, boolean dw) {
        ServerLevel sl = p.serverLevel();
        if (dw) { dmg *= 0.85f; variantMsg(p); }
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        Vec3 dir = new Vec3(look.x, 0, look.z).normalize();
        Vec3 center = p.position().add(dir.scale(2.6));
        List<LivingEntity> targets = sl.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(dw ? 3.8 : 3.2, 2.5, dw ? 3.8 : 3.2),
                t -> t != p && t.isAlive() && !t.isAlliedTo(p) && t.position().subtract(p.position()).multiply(1, 0, 1).normalize().dot(dir) > 0.2);
        for (LivingEntity t : targets) {
            damage(p, t, dmg, "zetten");
            t.knockback(1.2, -dir.x, -dir.z);
            Vec3 m = t.position().add(0, t.getBbHeight() * 0.5, 0);
            burst(sl, ParticleTypes.CRIT, m, 18, 0.5, 0.4);
            burst(sl, ParticleTypes.EXPLOSION, m, 1, 0, 0);
        }
        Vec3 rt = dir.cross(new Vec3(0, 1, 0)).normalize();
        if (dw) {   // two diagonal dark blades crossing in an X
            Vec3 cx = eye.add(dir.scale(2.4)).add(0, -0.2, 0);
            for (int dgn = 0; dgn < 2; dgn++) {
                Vec3 u = rt.scale(dgn == 0 ? 0.7 : -0.7).add(0, 0.7, 0).scale(3.0);
                FxSend.beam(sl, 1, cx.subtract(u), cx.add(u), 0.55f, 9);
            }
            FxSend.wall(sl, 1, p.position().add(0, 0.1, 0).add(dir.scale(0.2)), dir, rt, 3.4f, 3.4f, -70, 70, 0.8f, 8, 2, 0);
        } else {    // the Slayer's vertical cleave, from above your head down to the ground
            FxSend.wall(sl, 0, eye.add(0, -0.3, 0), dir, new Vec3(0, 1, 0), 3.4f, 3.4f, 80, -70, 1.5f, 13, 3, 0);
        }
        Vec3 hit = p.position().add(dir.scale(3.2)).add(0, 0.1, 0);
        FxSend.ring(sl, dw ? 1 : 0, hit, 0.4f, 4.5f, 0.45f, 10);
        burst(sl, ParticleTypes.EXPLOSION, hit.add(0, 0.4, 0), 2, 0, 0.5);
        sound(sl, p.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.2f, 0.5f);
        sound(sl, hit, SoundEvents.GENERIC_EXPLODE, 0.8f, 1.2f);
        sound(sl, hit, SoundEvents.PLAYER_ATTACK_CRIT, 1.0f, 0.6f);
    }

    // ------------------------------------------------------------------ Sky Splitter: ranged beam that cleaves everything in a line (Dweller: twin beams)
    public static void skySplitter(ServerPlayer p, float dmg, boolean dw) {
        ServerLevel sl = p.serverLevel();
        if (dw) { dmg *= 0.85f; variantMsg(p); }
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
            Vec3 m = t.position().add(0, t.getBbHeight() * 0.5, 0);
            burst(sl, ParticleTypes.CRIT, m, 24, 0.6, 0.4);
            burst(sl, ParticleTypes.EXPLOSION, m, 1, 0, 0);
            sound(sl, t.position(), SoundEvents.PLAYER_ATTACK_STRONG, 1f, 0.6f);
        }
        Vec3 rt = dir.cross(new Vec3(0, 1, 0));
        rt = rt.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : rt.normalize();
        Vec3 from = start.add(dir.scale(1.0)).add(0, -0.25, 0);
        if (dw) {
            for (int side = -1; side <= 1; side += 2) FxSend.beam(sl, 1, from.add(rt.scale(side * 0.9)), end.add(rt.scale(side * 0.9)), 0.75f, 12);
        } else {
            FxSend.beam(sl, 0, from, end, 1.25f, 12);
            FxSend.beam(sl, 6, from, end, 0.5f, 9);
        }
        FxSend.ring(sl, dw ? 1 : 0, start.add(dir.scale(1.2)), 0.2f, 1.6f, 0.3f, 6);
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
        int pal = el == null ? 1 : switch (el) { case "fire" -> 2; case "ice" -> 3; case "lightning" -> 4; default -> 5; };
        ParticleOptions spark = el == null ? ParticleTypes.SOUL_FIRE_FLAME : switch (el) {
            case "fire" -> ParticleTypes.FLAME;
            case "ice" -> ParticleTypes.SNOWFLAKE;
            case "lightning" -> ParticleTypes.ELECTRIC_SPARK;
            default -> ParticleTypes.CLOUD;
        };
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
            burst(sl, spark, c, 14, 0.15, 0.4);
            burst(sl, ParticleTypes.CRIT, c, 16, 0.5, 0.4);
            sound(sl, c, SoundEvents.PLAYER_ATTACK_CRIT, 1f, 1.3f);
        }
        // the flying crescent blade: a real 3D mesh, tilted 30 degrees like a diagonal cut
        Vec3 right = look.cross(new Vec3(0, 1, 0));
        right = right.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : right.normalize();
        Vec3 up = right.cross(look).normalize();
        double tilt = Math.toRadians(30);
        Vec3 u = right.scale(Math.cos(tilt)).add(up.scale(Math.sin(tilt)));
        FxSend.crescent(sl, pal, start.add(look.scale(1.6)).add(0, -0.2, 0), look, u, 1.3f, 2.6f, 9, 2.4f);
        FxSend.ring(sl, pal, start.add(look.scale(1.2)), 0.2f, 1.4f, 0.3f, 6);
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
        new ArrayList<>(p.getActiveEffects()).stream().filter(e -> !e.getEffect().isBeneficial()).forEach(e -> p.removeEffect(e.getEffect()));
        for (LivingEntity t : sl.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(9), x -> x != p && x.isAlive() && !x.isAlliedTo(p))) {
            if (!t.getActiveEffects().isEmpty()) t.removeAllEffects();
            burst(sl, ParticleTypes.CRIT, t.position().add(0, t.getBbHeight() * 0.5, 0), 10, 0.3, 0.4);
        }
        Vec3 c = p.position().add(0, 0.1, 0);
        FxSend.ring(sl, 6, c, 0.5f, 9.5f, 1.3f, 12);
        FxSend.ring(sl, 1, c.add(0, 0.5, 0), 0.3f, 7f, 0.9f, 10);
        FxSend.ring(sl, 0, c.add(0, 1.0, 0), 0.2f, 5f, 0.6f, 8);
        sound(sl, p.position(), SoundEvents.BEACON_DEACTIVATE, 1.2f, 0.6f);
        sound(sl, p.position(), SoundEvents.ENCHANTMENT_TABLE_USE, 1f, 0.5f);
    }

    // ------------------------------------------------------------------ Ki Sense: everything nearby is revealed
    public static void kiSense(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        for (LivingEntity t : sl.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(32), x -> x != p && x.isAlive())) {
            t.addEffect(new MobEffectInstance(MobEffects.GLOWING, 12 * 20, 0, false, false));
        }
        Vec3 c = p.position().add(0, 0.1, 0);
        FxSend.ring(sl, 0, c, 0.5f, 32f, 0.7f, 16);
        FxSend.ring(sl, 6, c.add(0, 0.4, 0), 0.3f, 22f, 0.5f, 12);
        sound(sl, p.position(), SoundEvents.BEACON_ACTIVATE, 1f, 1.6f);
        sound(sl, p.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 1f, 0.7f);
    }

    // ------------------------------------------------------------------ Instinct Guard: a moment where nothing can touch you
    public static void instinct(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        GUARD.put(p.getUUID(), sl.getGameTime() + GUARD_TICKS);
        FxSend.ring(sl, 6, p.position().add(0, 0.9, 0), 0.4f, 2.4f, 1.0f, 9);
        FxSend.ring(sl, 0, p.position().add(0, 0.2, 0), 0.4f, 2.0f, 0.5f, 8);
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
        FxSend.ring(sl, 1, c.add(0, 0.1, 0), 0.4f, 7f, 1.6f, 16);
        FxSend.ring(sl, 0, c.add(0, 0.6, 0), 0.3f, 5f, 1.2f, 14);
        FxSend.ring(sl, 1, c.add(0, 1.2, 0), 0.2f, 4f, 0.9f, 12);
        burst(sl, ParticleTypes.LARGE_SMOKE, c.add(0, 1, 0), 24, 0.08, 0.6);
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
        }
        Long u = UNION.get(p.getUUID());
        if (u != null) {
            if (now > u) {
                UNION.remove(p.getUUID());
                ring(sl, BLACK, p.position().add(0, 0.2, 0), 30, 0.3);
                sound(sl, p.position(), SoundEvents.BEACON_DEACTIVATE, 1f, 0.8f);
            } else {
                // devil aura: a dark outline cylinder around the body, refreshed every few ticks
                if (now % 6 == 0) FxSend.wall(sl, 1, p.position().add(0, 1.0, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1), 0.85f, 1.05f, -180, 180, 1.0f, 9, 0, 0);
                if (now % 12 == 0) FxSend.wall(sl, 0, p.position().add(0, 0.9, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1), 0.7f, 1.3f, -180, 180, 0.5f, 9, 0, 0);
                if (now % 4 == 0) burst(sl, BLACK, p.position().add(0, 1, 0), 2, 0.01, 0.45);
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
