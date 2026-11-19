package com.ironspellgrimoire.server;

import static com.ironspellgrimoire.server.FxUtil.*;
import static com.ironspellgrimoire.server.ParasiteFx.*;
import static com.ironspellgrimoire.server.ParasiteNest.*;
import com.ironspellgrimoire.entity.AddonBoss;
import com.ironspellgrimoire.entity.HiveNest;
import com.ironspellgrimoire.entity.ShadowSoldier;
import com.ironspellgrimoire.network.AddonNetwork;
import com.ironspellgrimoire.network.FxPacket;
import com.ironspellgrimoire.network.ParasitePackets;
import com.ironspellgrimoire.registry.AddonItemRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Split out of ParasiteFx (same code, own file): the Spine Launcher, Meteor Fall, evolution and strains, the awakened strain's signature move, the Final Form, Tendril Grapple and Burrow, the second strain and organs, Collapse Core, Pack Call, Air Dash, Predator Sight and Molting. */
@Mod.EventBusSubscriber(modid = "iron_spell_grimoire")
public final class ParasiteMoves {
    private ParasiteMoves() {}

    // ------------------------------------------------------------------ the Spine Launcher
    static final Map<UUID, Long> SPINE_CD = new HashMap<>();

    static void fireSpine(ServerPlayer p) {
        boolean spear = form(p) == LANCE && organ(p, GLAND) == 2;   // the Marrow Forge: the lance throws a bone spear
        if (form(p) != SPINE && !spear) return;
        long now = p.serverLevel().getGameTime();
        Long next = SPINE_CD.get(p.getUUID());
        if (next != null && now < next) return;
        SPINE_CD.put(p.getUUID(), now + (spear ? 20 : frenzied(p) ? 5 : 8));
        ServerLevel sl = p.serverLevel();
        float dmg = (spear ? 9f : 6f) * (1f + 0.15f * rank(p, HONED)) * (frenzied(p) ? 1.4f : 1f) * spineBoost(p);
        int n = spear ? 1 : 1 + Math.round(2 * sw(p, HIVE));
        sound(sl, p.position(), SoundEvents.ARROW_SHOOT, 0.9f, 0.6f);
        for (int k = 0; k < n; k++) {
            Vec3 dir = rotY(p.getLookAngle(), Math.toRadians((k - (n - 1) / 2.0) * 6)).normalize();
            Vec3 from = handPos(p);
            Vec3 end = clip(sl, p, from, from.add(dir.scale(24)));
            List<LivingEntity> hit = new ArrayList<>(Kit.along(sl, p, from, end, 0.6));
            hit.removeIf(t -> !foe(p, t));
            hit.sort(Comparator.comparingDouble(t -> t.distanceToSqr(from)));
            if (!hit.isEmpty()) end = mid(hit.get(0));
            spineFly(sl, from, dir, end.distanceTo(from), 3f, 0.9f);
            if (hit.isEmpty()) continue;
            LivingEntity t = hit.get(0);
            int delay = (int) (end.distanceTo(from) / 3);
            AstaFx.later(Math.max(1, delay), () -> {
                if (!t.isAlive() || !alive(p)) return;
                t.invulnerableTime = 0;
                armHit(p, t, dmg);
                spineLanded(p, t);
                Fx.spark(sl, BONE, mid(t), 0.5f);
                feed(p, 1);
            });
        }
    }

    // ------------------------------------------------------------------ Meteor Fall
    static void slamStart(ServerPlayer p, float height) {
        if (p.onGround()) return;
        CompoundTag r = root(p);
        r.putLong("slam", p.serverLevel().getGameTime() + 60);
        r.putDouble("slamY", p.getY());
        FxSend.spike(p.serverLevel(), RED, p.position().add(0, 2.2, 0), new Vec3(0, -1, 0), 2.0f, 0.3f, 6, 0, FxPacket.POP);
        sound(p.serverLevel(), p.position(), SoundEvents.PHANTOM_SWOOP, 0.8f, 0.6f);
    }

    static void slamLand(ServerPlayer p) {
        CompoundTag r = root(p);
        double fell = Math.max(0, r.getDouble("slamY") - p.getY());
        r.remove("slam"); r.remove("slamY");
        ServerLevel sl = p.serverLevel();
        float radius = (3.5f + (float) Math.min(3, fell * 0.15)) * (1f + 0.5f * sw(p, BRUTE));
        float dmg = (4f + (float) Math.min(20, fell * 0.6)) * (1f + 0.4f * rank(p, METEOR)) * (form(p) == TITAN ? 1.5f : 1f) * (frenzied(p) ? 1.4f : 1f);
        Vec3 c = p.position();
        FxSend.ring(sl, RED | FxPacket.KEEP_SHAPE, c.add(0, 0.05, 0), 0.3f, radius, 0.15f, 10);
        groundSpines(sl, c, radius * 0.8f, 12, 10);
        FxSend.shake(sl, c, 0.6f, 8, 14);
        sound(sl, c, SoundEvents.GENERIC_EXPLODE, 0.7f, 0.8f);
        sound(sl, c, SoundEvents.ANVIL_LAND, 0.5f, 0.5f);
        for (LivingEntity t : Kit.near(sl, p, c.add(0, 0.5, 0), radius)) {
            if (!foe(p, t)) continue;
            t.invulnerableTime = 0;
            armHit(p, t, dmg);
            Kit.push(t, t.position().subtract(c), 0.9, 0.45);
        }
    }

    // ------------------------------------------------------------------ evolution and strains
    /** Evolve a node (arg = node; for an Ultimates node, node + 100 x the path, 1 or 2). */
    static void buy(ServerPlayer p, int arg) {
        int node = arg % 100, way = arg / 100;
        if (node < 0 || node >= NODES.length) return;
        int[] rk = ranks(p);
        CompoundTag r = root(p);
        if (rk[node] >= NODES[node][1]) return;
        if (isPath(node) && way != 1 && way != 2) return;
        if (needs(node) >= 0 && rk[needs(node)] <= 0) {
            p.displayClientMessage(Component.literal("Evolve " + NODE_NAMES[needs(node)] + " first").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        int cost = NODES[node][2];
        if (r.getInt("ep") < cost) { p.displayClientMessage(Component.literal("Not enough evolution points").withStyle(ChatFormatting.RED), true); return; }
        r.putInt("ep", r.getInt("ep") - cost);
        r.putInt("spent", r.getInt("spent") + cost);
        boolean wasAwake = awakened(p);
        rk[node] = isPath(node) ? way : rk[node] + 1;
        r.putIntArray("ranks", rk);
        sound(p.serverLevel(), p.position(), SoundEvents.SLIME_SQUISH, 1.0f, 0.5f);
        sound(p.serverLevel(), p.position(), SoundEvents.PLAYER_LEVELUP, 0.5f, 0.6f);
        FxSend.ring(p.serverLevel(), RED | FxPacket.KEEP_SHAPE, p.position().add(0, 1, 0), 0.3f, 1.6f, 0.06f, 8);
        if (!wasAwake && awakened(p)) awaken(p);
        applyStats(p);
        sync(p, false);
    }

    static void awaken(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        FxSend.ring(sl, RED | FxPacket.KEEP_SHAPE, p.position().add(0, 1, 0), 0.5f, 5.0f, 0.15f, 16);
        FxSend.shake(sl, p.position(), 0.5f, 10, 12);
        sound(sl, p.position(), SoundEvents.RAVAGER_ROAR, 1.0f, 0.6f);
        p.displayClientMessage(Component.literal("The " + STRAINS[strain(p)] + " strain AWAKENS - " + ARTS[strain(p)] + " and the Final Form are on your wheel")
                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), false);
    }

    static void chooseStrain(ServerPlayer p, int s) {
        CompoundTag r = root(p);
        if (s < HUNTER || s > HIVE || r.getInt("strain") != 0 || r.getInt("spent") < STRAIN_AT) return;
        r.putInt("strain", s);
        if (awakened(p)) AstaFx.later(20, () -> awaken(p));
        sound(p.serverLevel(), p.position(), SoundEvents.RAVAGER_ROAR, 0.7f, 0.8f);
        FxSend.ring(p.serverLevel(), RED | FxPacket.KEEP_SHAPE, p.position().add(0, 1, 0), 0.4f, 3.0f, 0.1f, 12);
        p.displayClientMessage(Component.literal("The strain settles: " + STRAINS[s]).withStyle(ChatFormatting.RED, ChatFormatting.BOLD), false);
        applyStats(p);
        sync(p, false);
    }

    // ------------------------------------------------------------------ the awakened strain's signature move
    static int left(ServerPlayer p, String key) { return (int) Math.max(0, (root(p).getLong(key) - p.serverLevel().getGameTime()) / 20); }

    public static void strainArt(ServerPlayer p) {
        if (!awakened(p)) { p.displayClientMessage(Component.literal("Evolve every node to awaken your strain").withStyle(ChatFormatting.GRAY), true); return; }
        CompoundTag r = root(p);
        long now = p.serverLevel().getGameTime();
        if (now < r.getLong("artCd")) { p.displayClientMessage(Component.literal(ARTS[strain(p)] + " is ready in " + left(p, "artCd") + " s").withStyle(ChatFormatting.GRAY), true); return; }
        r.putLong("artCd", now + ART_CD);
        ServerLevel sl = p.serverLevel();
        switch (strain(p)) {
            case HUNTER -> apexChain(p);
            case BRUTE -> {   // Colossus: 15 s bigger, harder-hitting, armoured, unstoppable
                r.putLong("colossus", now + 300);
                FxSend.ring(sl, RED | FxPacket.KEEP_SHAPE, p.position().add(0, 0.1, 0), 0.5f, 4.0f, 0.15f, 12);
                FxSend.shake(sl, p.position(), 0.6f, 10, 14);
                sound(sl, p.position(), SoundEvents.RAVAGER_ROAR, 1.0f, 0.5f);
            }
            case HIVE -> {   // Swarm: 20 s of parasite motes darting at every foe round you
                r.putLong("swarm", now + 400);
                sound(sl, p.position(), SoundEvents.BEEHIVE_EXIT, 1.0f, 0.5f);
            }
            default -> { }
        }
        applyStats(p);
        dirty(p);
    }

    /** Apex Chain: you flash from foe to foe - up to five within 12 blocks - cutting each as you pass. */
    static void apexChain(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        List<LivingEntity> prey = new ArrayList<>(Kit.near(sl, p, p.position().add(0, 1, 0), 12));
        prey.removeIf(t -> !foe(p, t));
        prey.sort(Comparator.comparingDouble(t -> t.distanceToSqr(p)));
        if (prey.size() > 5) prey = new ArrayList<>(prey.subList(0, 5));
        if (prey.isEmpty()) { p.displayClientMessage(Component.literal("No prey in reach").withStyle(ChatFormatting.GRAY), true); root(p).putLong("artCd", 0); return; }
        float dmg = (float) p.getAttributeValue(Attributes.ATTACK_DAMAGE) * 1.5f + 6f;
        sound(sl, p.position(), SoundEvents.PHANTOM_SWOOP, 1.0f, 1.2f);
        for (int k = 0; k < prey.size(); k++) {
            final LivingEntity t = prey.get(k);
            AstaFx.later(k * 4, () -> {
                if (!alive(p) || !t.isAlive()) return;
                Vec3 from = p.position();
                Vec3 side = t.position().subtract(from).multiply(1, 0, 1);
                side = side.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : side.normalize();
                Vec3 to = t.position().add(side.scale(t.getBbWidth() * 0.5 + 0.8));
                FxSend.slash(sl, RED, from.add(0, 1, 0).add(to.subtract(from).scale(0.5)), side, new Vec3(0, 1, 0), 0.4f, (float) from.distanceTo(to) * 0.5f + 0.5f, 5, 0);
                p.teleportTo(to.x, t.getY(), to.z);
                p.fallDistance = 0;
                t.invulnerableTime = 0;
                armHit(p, t, dmg);
                Fx.impact(sl, RED, mid(t), 1.0f);
                sound(sl, to, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, 0.8f);
            });
        }
    }

    // ------------------------------------------------------------------ the Final Form
    public static void finalFormStart(ServerPlayer p) {
        if (!awakened(p)) { p.displayClientMessage(Component.literal("The Final Form needs an awakened strain").withStyle(ChatFormatting.GRAY), true); return; }
        CompoundTag r = root(p);
        long now = p.serverLevel().getGameTime();
        if (now < r.getLong("ffCd")) { p.displayClientMessage(Component.literal("The Final Form is ready in " + left(p, "ffCd") + " s").withStyle(ChatFormatting.GRAY), true); return; }
        if (biomass(p) < 100) { p.displayClientMessage(Component.literal("The Final Form needs a full gut (100 biomass)").withStyle(ChatFormatting.RED), true); return; }
        r.putFloat("bio", biomass(p) - 100);
        r.putLong("ffEnd", now + FINAL_LEN);
        r.putLong("ffCd", now + FINAL_CD);
        r.putLong("frenzyEnd", 0); r.putFloat("frenzy", 0);
        if (form(p) == NONE && p.getMainHandItem().isEmpty()) setForm(p, REAPER, true);
        ServerLevel sl = p.serverLevel();
        FxSend.ring(sl, RED | FxPacket.KEEP_SHAPE, p.position().add(0, 0.1, 0), 0.5f, 6.0f, 0.2f, 16);
        groundSpines(sl, p.position(), 4f, 18, 16);
        FxSend.shake(sl, p.position(), 0.8f, 14, 20);
        FxSend.impactFrame(sl, p.position(), 4, false, 24);
        sound(sl, p.position(), SoundEvents.RAVAGER_ROAR, 1.2f, 0.4f);
        sound(sl, p.position(), SoundEvents.WARDEN_SONIC_CHARGE, 0.8f, 0.6f);
        p.displayClientMessage(Component.literal("FINAL FORM").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), true);
        applyStats(p);
        dirty(p);
    }

    /** While in the Final Form: spines burst out of you every 2 s. */
    static void finalPulse(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        Vec3 c = p.position().add(0, 1.2, 0);
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI * 2 / 12 + p.tickCount * 0.1;
            spineFly(sl, c, new Vec3(Math.cos(a), 0.1, Math.sin(a)), 5, 1.8f, 0.9f);
        }
        sound(sl, c, SoundEvents.ARROW_SHOOT, 0.8f, 0.5f);
        for (LivingEntity t : Kit.near(sl, p, c, 5)) {
            if (!foe(p, t)) continue;
            t.invulnerableTime = 0;
            armHit(p, t, 4f * spineBoost(p));
        }
    }

    /** The Hive's Swarm: every half second, motes dart at up to four foes within 8 blocks. */
    static void swarmPulse(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        Vec3 c = p.position().add(0, 1.4, 0);
        double t0 = p.tickCount * 0.25;
        for (int i = 0; i < 6; i++) {   // the motes circling you
            double a = t0 + i * Math.PI / 3;
            Fx.spark(sl, RED, c.add(Math.cos(a) * 1.6, 0.4 * Math.sin(a * 2), Math.sin(a) * 1.6), 0.25f);
        }
        List<LivingEntity> near = new ArrayList<>(Kit.near(sl, p, c, 8));
        near.removeIf(t -> !foe(p, t));
        near.sort(Comparator.comparingDouble(t -> t.distanceToSqr(p)));
        for (int k = 0; k < Math.min(4, near.size()); k++) {
            LivingEntity t = near.get(k);
            Vec3 d = mid(t).subtract(c);
            spineFly(sl, c, d, d.length(), 1.6f, 0.4f);
            AstaFx.later(Math.max(1, (int) (d.length() / 1.6)), () -> {
                if (!t.isAlive() || !alive(p)) return;
                t.invulnerableTime = 0;
                armHit(p, t, 2f);
            });
        }
    }

    // ------------------------------------------------------------------ Tendril Grapple and Burrow
    /** Z: a tendril to what you aim at - light foes are yanked to you, anything else pulls you to it. */
    public static void grapple(ServerPlayer p) {
        if (rank(p, TENDRIL) <= 0) return;
        CompoundTag r = root(p);
        long now = p.serverLevel().getGameTime();
        if (now < r.getLong("grappleCd") || burrowed(p)) return;
        ServerLevel sl = p.serverLevel();
        LivingEntity t = aimed(p, 24, 6);
        Vec3 eye = p.getEyePosition(), hand = handPos(p);
        if (t != null && foe(p, t) && !isBoss(t) && t.getMaxHealth() < 40) {   // a light foe: yanked in
            Vec3 d = p.position().subtract(t.position());
            Kit.push(t, d, Math.min(2.0, d.length() * 0.25), 0.3);
            tendril(sl, hand, mid(t), 8);
            sound(sl, t.position(), SoundEvents.FISHING_BOBBER_RETRIEVE, 1.0f, 0.6f);
        } else {   // anything else: you're pulled to it
            Vec3 to = t != null ? mid(t) : Kit.aim(p, 24);
            if (to.distanceTo(eye) > 23.5 && t == null) { p.displayClientMessage(Component.literal("Nothing in reach").withStyle(ChatFormatting.GRAY), true); return; }
            Vec3 d = to.subtract(p.position());
            p.setDeltaMovement(d.normalize().scale(Math.min(2.2, d.length() * 0.22)).add(0, 0.35, 0));
            p.hurtMarked = true;
            r.putLong("glide", now + 60);   // no fall damage from the swing
            tendril(sl, hand, to, 8);
            sound(sl, p.position(), SoundEvents.FISHING_BOBBER_THROW, 1.0f, 0.5f);
        }
        r.putLong("grappleCd", now + 30);
    }

    /** Sneak + Z: sink out of sight for 4 s (hidden, faster, foes lose you), then burst up in spines. */
    public static void burrow(ServerPlayer p) {
        if (rank(p, BURROW) <= 0) return;
        CompoundTag r = root(p);
        long now = p.serverLevel().getGameTime();
        if (burrowed(p)) { erupt(p); return; }   // pressed again: up now
        if (now < r.getLong("burrowCd") || !p.onGround()) return;
        r.putLong("burrow", now + 80);
        r.putLong("burrowCd", now + 200);
        ServerLevel sl = p.serverLevel();
        groundSpines(sl, p.position(), 1.2f, 6, 8);
        sound(sl, p.position(), SoundEvents.ROOTED_DIRT_BREAK, 1.0f, 0.6f);
        sound(sl, p.position(), SoundEvents.SLIME_SQUISH, 0.8f, 0.5f);
        for (net.minecraft.world.entity.Mob m : sl.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, p.getBoundingBox().inflate(32), m -> m.getTarget() == p)) m.setTarget(null);
        applyStats(p);
        dirty(p);
    }

    static void erupt(ServerPlayer p) {
        CompoundTag r = root(p);
        r.putLong("burrow", 0);
        ServerLevel sl = p.serverLevel();
        Vec3 c = p.position();
        groundSpines(sl, c, 3.0f, 16, 12);
        FxSend.ring(sl, RED | FxPacket.KEEP_SHAPE, c.add(0, 0.1, 0), 0.3f, 3.5f, 0.15f, 10);
        FxSend.shake(sl, c, 0.5f, 6, 12);
        sound(sl, c, SoundEvents.EVOKER_FANGS_ATTACK, 1.2f, 0.6f);
        for (LivingEntity t : Kit.near(sl, p, c.add(0, 0.5, 0), 3.5)) {
            if (!foe(p, t)) continue;
            t.invulnerableTime = 0;
            armHit(p, t, 8f * spineBoost(p));
            spineLanded(p, t);
            if (!isBoss(t)) { t.setDeltaMovement(t.getDeltaMovement().add(0, 0.7, 0)); t.hurtMarked = true; }
        }
        applyStats(p);
        dirty(p);
    }

    /** Foes can't take aim at someone burrowed. */
    @SubscribeEvent
    public static void onTarget(net.minecraftforge.event.entity.living.LivingChangeTargetEvent e) {
        if (e.getNewTarget() instanceof ServerPlayer p && burrowed(p)) e.setCanceled(true);
    }

    // ------------------------------------------------------------------ the second strain and organs
    static void secondStrain(ServerPlayer p, int s) {
        CompoundTag r = root(p);
        if (!awakened(p) || r.getInt("strain2") != 0 || s < HUNTER || s > HIVE || s == strain(p)) return;
        r.putInt("strain2", s);
        FxSend.ring(p.serverLevel(), RED | FxPacket.KEEP_SHAPE, p.position().add(0, 1, 0), 0.4f, 3.0f, 0.1f, 12);
        sound(p.serverLevel(), p.position(), SoundEvents.RAVAGER_ROAR, 0.7f, 1.0f);
        p.displayClientMessage(Component.literal("Grafted: the " + STRAINS[s] + " strain, at half strength").withStyle(ChatFormatting.RED), false);
        applyStats(p);
        sync(p, false);
    }

    /** Grow an organ in a slot (4 EP the first time a slot is grown; swapping after that is free). */
    static void growOrgan(ServerPlayer p, int slot, int choice) {
        if (slot < 0 || slot > 2 || choice < 1 || choice > 3) return;
        CompoundTag r = root(p);
        int[] o = r.getIntArray("organs");
        if (o.length < 3) o = new int[3];
        if (o[slot] == choice) return;
        if (o[slot] == 0) {
            if (r.getInt("ep") < ORGAN_COST) { p.displayClientMessage(Component.literal("Growing an organ takes " + ORGAN_COST + " evolution points").withStyle(ChatFormatting.RED), true); return; }
            r.putInt("ep", r.getInt("ep") - ORGAN_COST);
        }
        o[slot] = choice;
        r.putIntArray("organs", o);
        sound(p.serverLevel(), p.position(), SoundEvents.SLIME_SQUISH, 1.0f, 0.6f);
        sound(p.serverLevel(), p.position(), SoundEvents.WARDEN_HEARTBEAT, 1.0f, 1.0f);
        applyStats(p);
        sync(p, false);
    }

    // ------------------------------------------------------------------ Collapse Core
    /** A dense sphere of biomass where you aim (20 blocks): for 3 s it drags every foe within 9 blocks into its middle,
     *  tearing at them; then it bursts in tendrils. Bosses are slowed, not pulled. */
    public static void collapseCore(ServerPlayer p, float power) {
        if (!spend(p, ultCost(p, 3))) { noBiomass(p, 3); return; }
        ServerLevel sl = p.serverLevel();
        LivingEntity aimedAt = aimed(p, 20, 10);
        Vec3 c = (aimedAt != null && foe(p, aimedAt) ? mid(aimedAt) : Kit.aim(p, 20)).add(0, 0.6, 0);
        float dmg = power * ultBoost(p);
        sound(sl, c, SoundEvents.WARDEN_SONIC_CHARGE, 1.0f, 0.5f);
        for (int k = 0; k < 12; k++) {
            final int tick = k;
            AstaFx.later(k * 5, () -> {
                if (!alive(p)) return;
                Kit.orb(sl, RED, c, Vec3.ZERO, 0.9f + 0.05f * tick, 6);   // the core, swelling
                FxSend.ring(sl, RED | FxPacket.KEEP_SHAPE, c, 9f - tick * 0.7f, 9.2f - tick * 0.7f, 0.06f, 6);   // the pull, closing in
                if (tick % 2 == 0) sound(sl, c, SoundEvents.SLIME_SQUISH, 0.7f, 0.4f + tick * 0.05f);
                for (LivingEntity t : Kit.near(sl, p, c, 9)) {
                    if (!foe(p, t)) continue;
                    Vec3 to = c.subtract(mid(t));
                    if (isBoss(t)) TimedModifiers.apply(t, Attributes.MOVEMENT_SPEED, ROOTED, "Pulled", -0.5, AttributeModifier.Operation.MULTIPLY_TOTAL, 8);
                    else if (to.length() > 0.8) { t.setDeltaMovement(to.normalize().scale(Math.min(0.9, 0.25 + to.length() * 0.08))); t.hurtMarked = true; }
                    if (tick % 3 == 0) { t.invulnerableTime = 0; AstaFx.damage(p, t, dmg * 0.25f, "collapse_core"); tendril(sl, c, mid(t), 4); }
                }
            });
        }
        AstaFx.later(62, () -> {   // the burst
            if (!alive(p)) return;
            FxSend.ring(sl, RED | FxPacket.KEEP_SHAPE, c, 0.5f, 7f, 0.2f, 12);
            FxSend.shake(sl, c, 0.8f, 10, 20);
            FxSend.impactFrame(sl, c, 3, false, 24);
            sound(sl, c, SoundEvents.GENERIC_EXPLODE, 1.0f, 0.6f);
            ThreadLocalRandom rnd = ThreadLocalRandom.current();
            for (int i = 0; i < 18; i++) {
                Vec3 d = new Vec3(rnd.nextDouble(-1, 1), rnd.nextDouble(-0.3, 0.8), rnd.nextDouble(-1, 1)).normalize();
                tendril(sl, c, c.add(d.scale(rnd.nextDouble(3, 6.5))), 10);
            }
            for (LivingEntity t : Kit.near(sl, p, c, 6.5)) {
                if (!foe(p, t)) continue;
                t.invulnerableTime = 0;
                AstaFx.damage(p, t, dmg * 2.2f, "collapse_core");
                if (!isBoss(t)) Kit.push(t, mid(t).subtract(c), 1.2, 0.5);
            }
        });
    }

    // ------------------------------------------------------------------ Pack Call
    /** Three infected brutes burst up beside you, shaped by your Strain, to fight for 30 s. */
    public static void packCall(ServerPlayer p, float power) {
        if (!spend(p, ULT_COST[4])) { p.displayClientMessage(Component.literal("Needs " + ULT_COST[4] + " biomass").withStyle(ChatFormatting.RED), true); return; }
        ServerLevel sl = p.serverLevel();
        for (com.ironspellgrimoire.entity.InfectedBrute old : sl.getEntitiesOfClass(com.ironspellgrimoire.entity.InfectedBrute.class, p.getBoundingBox().inflate(64), b -> p.getUUID().equals(b.owner()))) old.life = 1;
        for (int k = 0; k < 3; k++) {
            double a = Math.toRadians(p.getYRot() + 90) + (k - 1) * 0.9;
            Vec3 at = p.position().add(-Math.sin(a + Math.PI / 2) * 2.2, 0, Math.cos(a + Math.PI / 2) * 2.2);
            at = ground(sl, p, at.add(0, 1.5, 0));
            com.ironspellgrimoire.entity.InfectedBrute b = com.ironspellgrimoire.registry.AddonEntities.INFECTED_BRUTE.get().create(sl);
            if (b == null) continue;
            b.moveTo(at.x, at.y, at.z, p.getYRot(), 0);
            b.setOwner(p.getUUID());
            b.setKind(strain(p));
            sl.addFreshEntity(b);
            groundSpines(sl, at, 1.2f, 6, 10);
        }
        sound(sl, p.position(), SoundEvents.RAVAGER_ROAR, 0.8f, 1.2f);
        sound(sl, p.position(), SoundEvents.ROOTED_DIRT_BREAK, 1.0f, 0.5f);
    }

    /** Every tick, for each brute: wither when its time's up or its master is gone; follow its master; go for what
     *  its master fights; the Hive's spitters shoot spines from range. */
    public static void bruteTick(com.ironspellgrimoire.entity.InfectedBrute b) {
        if (!(b.level() instanceof ServerLevel sl)) return;
        ServerPlayer o = b.getOwnerEntity();
        if (o == null || --b.life <= 0 || o.level() != b.level()) {
            groundSpines(sl, b.position(), 0.8f, 4, 8);
            sound(sl, b.position(), SoundEvents.SLIME_SQUISH, 0.8f, 0.5f);
            b.discard();
            return;
        }
        LivingEntity t = b.getTarget();
        if (t != null && (!t.isAlive() || !b.canAttack(t))) { b.setTarget(null); t = null; }
        if (b.tickCount % 10 == 0) {   // what its master fights, or what fights its master
            LivingEntity want = o.getLastHurtMob() != null && o.getLastHurtMob().isAlive() && o.tickCount - o.getLastHurtMobTimestamp() < 200 ? o.getLastHurtMob() : o.getLastHurtByMob();
            if (want != null && want.isAlive() && b.canAttack(want) && want.distanceTo(b) < 24) b.setTarget(want);
            else if (t == null) {
                List<net.minecraft.world.entity.Mob> near = sl.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, b.getBoundingBox().inflate(12),
                        m -> m instanceof net.minecraft.world.entity.monster.Enemy && m.isAlive() && b.canAttack(m));
                near.sort(Comparator.comparingDouble(m -> m.distanceToSqr(b)));
                if (!near.isEmpty()) b.setTarget(near.get(0));
            }
        }
        t = b.getTarget();
        if (t == null) {   // follow its master
            double d = b.distanceTo(o);
            if (d > 24) b.teleportTo(o.getX(), o.getY(), o.getZ());
            else if (d > 5) b.getNavigation().moveTo(o, 1.2);
        } else if (b.kind() == 3) {   // the spitter keeps its distance and shoots
            double d = b.distanceTo(t);
            if (d > 12) b.getNavigation().moveTo(t, 1.0); else b.getNavigation().stop();
            b.getLookControl().setLookAt(t);
            if (--b.spitCd <= 0 && d < 14 && b.hasLineOfSight(t)) {
                b.spitCd = 25;
                Vec3 from = b.getEyePosition(), to = mid(t);
                spineFly(sl, from, to.subtract(from), from.distanceTo(to), 1.6f, 0.6f);
                sound(sl, from, SoundEvents.LLAMA_SPIT, 0.8f, 0.6f);
                final LivingEntity tt = t;
                AstaFx.later(Math.max(1, (int) (from.distanceTo(to) / 1.6)), () -> {
                    if (!tt.isAlive() || !b.isAlive()) return;
                    tt.invulnerableTime = 0;
                    tt.hurt(b.damageSources().mobAttack(b), 4f);
                });
            }
        }
    }

    // ------------------------------------------------------------------ Air Dash
    /** A dash in mid-air (the client moved; this charges it and keeps the fall harmless). */
    static void airDash(ServerPlayer p) {
        if (p.onGround()) return;
        CompoundTag r = root(p);
        r.putFloat("bio", Math.max(0, r.getFloat("bio") - 2f));
        r.putLong("glide", p.serverLevel().getGameTime() + 40);
        FxSend.ring(p.serverLevel(), RED | FxPacket.KEEP_SHAPE, p.position().add(0, 1, 0), 0.2f, 1.2f, 0.06f, 6);
        sound(p.serverLevel(), p.position(), SoundEvents.PHANTOM_FLAP, 0.7f, 1.3f);
        dirty(p);
    }

    // ------------------------------------------------------------------ Predator Sight and Molting
    static void toggleSight(ServerPlayer p) {
        if (rank(p, SIGHT) <= 0) { p.displayClientMessage(Component.literal("Evolve Predator Sight first").withStyle(ChatFormatting.GRAY), true); return; }
        CompoundTag r = root(p);
        boolean on = !r.getBoolean("sight");
        if (on && r.getFloat("bio") < 1) { p.displayClientMessage(Component.literal("No biomass to see with").withStyle(ChatFormatting.GRAY), true); return; }
        r.putBoolean("sight", on);
        sound(p.serverLevel(), p.position(), on ? SoundEvents.WARDEN_HEARTBEAT : SoundEvents.SLIME_SQUISH_SMALL, 0.6f, on ? 1.4f : 0.8f);
        dirty(p);
    }

    static final UUID MOLT_SPEED = UUID.fromString("5e1f0c3a-7d21-4b8e-9a64-0b2c7a900012");

    /** After a blow lands: under 30% health, shed a husk - mobs turn on it while you get away. 2 min. */
    static void molt(ServerPlayer p) {
        if (!p.isAlive() || p.getHealth() >= p.getMaxHealth() * 0.3f) return;
        CompoundTag r = root(p);
        long now = p.serverLevel().getGameTime();
        if (now < r.getLong("moltCd")) return;
        r.putLong("moltCd", now + 2400);
        ServerLevel sl = p.serverLevel();
        com.ironspellgrimoire.entity.MoltHusk h = com.ironspellgrimoire.registry.AddonEntities.MOLT_HUSK.get().create(sl);
        if (h != null) {
            h.moveTo(p.getX(), p.getY(), p.getZ(), p.getYRot(), 0);
            h.yBodyRot = p.yBodyRot; h.yHeadRot = p.yHeadRot;
            h.setOwner(p.getUUID());
            sl.addFreshEntity(h);
            for (net.minecraft.world.entity.Mob m : sl.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, p.getBoundingBox().inflate(24), m -> m.getTarget() == p)) m.setTarget(h);
        }
        TimedModifiers.apply(p, Attributes.MOVEMENT_SPEED, MOLT_SPEED, "Molted", 0.4, AttributeModifier.Operation.MULTIPLY_BASE, 60);
        FxSend.ring(sl, RED | FxPacket.KEEP_SHAPE, p.position().add(0, 1, 0), 0.3f, 2.0f, 0.1f, 10);
        sound(sl, p.position(), SoundEvents.SLIME_SQUISH, 1.2f, 0.4f);
        sound(sl, p.position(), SoundEvents.ARMOR_EQUIP_LEATHER, 1.0f, 0.5f);
        p.displayClientMessage(Component.literal("You molt - the husk draws them off").withStyle(ChatFormatting.DARK_RED), true);
    }

    /** The husk falls apart in a burst of spines. */
    public static void huskCrumbles(com.ironspellgrimoire.entity.MoltHusk h) {
        if (!(h.level() instanceof ServerLevel sl) || h.isRemoved()) return;
        Vec3 c = h.position().add(0, 1, 0);
        ServerPlayer owner = h.owner() == null ? null : (ServerPlayer) sl.getPlayerByUUID(h.owner());
        for (int i = 0; i < 10; i++) {
            double a = i * Math.PI / 5;
            spineFly(sl, c, new Vec3(Math.cos(a), 0.2, Math.sin(a)), 4, 1.4f, 0.6f);
        }
        sound(sl, c, SoundEvents.SKELETON_HURT, 1.0f, 0.5f);
        if (owner != null) for (LivingEntity t : Kit.near(sl, owner, c, 3.5)) {
            if (!foe(owner, t) || t == h) continue;
            t.invulnerableTime = 0;
            t.hurt(owner.damageSources().playerAttack(owner), 5f);
        }
        h.discard();
    }

}
