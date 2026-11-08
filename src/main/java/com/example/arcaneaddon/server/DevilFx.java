package com.example.arcaneaddon.server;

import com.example.arcaneaddon.registry.AddonItemRegistry;
import io.redspace.ironsspellbooks.api.entity.IMagicEntity;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;

/**
 * Devil Union versions of the Black Grimoire's moves. While the union lasts every move uses BOTH swords:
 * the sword in your main hand strikes first, then the other sword follows up. Everything is drawn in ember (palette 9)
 * and black. Each method is the effect of one devil spell (see AddonSpellRegistry).
 */
public final class DevilFx {
    private DevilFx() {}

    private static final int EMBER = 9, DARK = 1;

    private static void sound(ServerLevel sl, Vec3 at, SoundEvent s, float v, float pitch) {
        sl.playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, v, pitch);
    }

    private static boolean alive(ServerPlayer p) { return p.isAlive() && !p.isRemoved(); }

    private static boolean holds(ServerPlayer p, Item item) {
        return p.getMainHandItem().is(item) || p.getOffhandItem().is(item);
    }

    /** Devil Zetten: Twin Cleave - the main-hand sword's Zetten, then the other sword's, with an ember crater. */
    public static void zetten(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        boolean dw = AstaFx.isDweller(p);
        AstaFx.zetten(p, power * 1.15f, dw);
        Vec3 dir = new Vec3(p.getLookAngle().x, 0, p.getLookAngle().z).normalize();
        Vec3 hit = p.position().add(dir.scale(3.2));
        FxLib.shockRing(sl, EMBER, hit.add(0, 0.1, 0), 2.2f);
        AstaFx.later(7, () -> { if (alive(p)) AstaFx.zetten(p, power * 0.75f, !dw); });   // the second sword follows
        sound(sl, hit, SoundEvents.GENERIC_EXPLODE, 0.6f, 0.7f);
    }

    /** Devil Sky Splitter: Twin Rift - two splitting cleaves, one per sword. */
    public static void skySplitter(ServerPlayer p, float power) {
        boolean dw = AstaFx.isDweller(p);
        AstaFx.skySplitter(p, power * 1.2f, dw);
        AstaFx.later(12, () -> { if (alive(p)) AstaFx.skySplitter(p, power * 0.6f, !dw); });
    }

    /**
     * Anti-Magic Domain: the anti magic wave, then a second, wider domain (16 blocks) that breaks every cast again and
     * cuts anyone carrying magic. Slayer: a crushing dome. Dweller: eight dark crescents fly out.
     */
    public static void antiMagic(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        AstaFx.antiMagic(p);
        boolean dw = AstaFx.isDweller(p);
        Vec3 c = p.position();
        if (dw) {
            for (int k = 0; k < 8; k++) {
                double a = Math.PI * 2 * k / 8;
                FxLib.crescentShot(sl, DARK, c.add(0, 1, 0), new Vec3(Math.cos(a), 0, Math.sin(a)), 1.2f);
            }
        } else {
            FxLib.dome(sl, EMBER, c, 3.0f);
        }
        AstaFx.later(6, () -> {
            if (!alive(p)) return;
            FxLib.shockRing(sl, EMBER, c.add(0, 0.1, 0), 5.5f);
            for (LivingEntity t : sl.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(16), x -> x != p && x.isAlive() && !x.isAlliedTo(p))) {
                if (t.distanceTo(p) > 16) continue;
                boolean magical = !t.getActiveEffects().isEmpty() || t instanceof IMagicEntity;
                if (t instanceof ServerPlayer sp && MagicData.getPlayerMagicData(sp).isCasting()) { Utils.serverSideCancelCast(sp); magical = true; }
                if (t instanceof IMagicEntity mage && mage.isCasting()) mage.cancelCast();
                if (!t.getActiveEffects().isEmpty()) t.removeAllEffects();
                if (magical) {
                    AstaFx.damage(p, t, power, "devil_anti_magic");
                    FxLib.impact(sl, EMBER, t.position().add(0, t.getBbHeight() * 0.5, 0), 1.0f);
                }
            }
            sound(sl, c, SoundEvents.BEACON_DEACTIVATE, 1f, 0.4f);
        });
    }

    /** Devil's Eye: Ki Sense out to 48 blocks for 20 seconds. */
    public static void kiSense(ServerPlayer p, float power) {
        AstaFx.kiSense(p, 48, 20 * 20);
        FxLib.halo(p.serverLevel(), EMBER, p.position().add(0, p.getBbHeight() + 0.3, 0), 1.4f);
    }

    /** Devil Instinct: a longer stance that counters every attack, harder. */
    public static void instinct(ServerPlayer p, float power) {
        AstaFx.instinct(p, true);
        FxLib.aura(p.serverLevel(), EMBER, p.position().add(0, 1, 0), 1.0f);
    }

    /** Devil Union: Release - see AstaFx.releaseUnion. */
    public static void release(ServerPlayer p, float power) {
        AstaFx.releaseUnion(p, power);
    }

    /** Devil Dweller: Triple Slash - three anti magic crescents in a row; with the Slayer also in hand it adds a sweeping cut. */
    public static void dwellerSlash(ServerPlayer p, float power) {
        for (int k = 0; k < 3; k++) {
            AstaFx.later(1 + k * 3, () -> { if (alive(p)) AstaFx.dwellerSlash(p, power * 0.8f, false); });
        }
        if (holds(p, AddonItemRegistry.DEMON_SLAYER_SWORD.get())) {
            AstaFx.later(10, () -> { if (alive(p)) SlashProjectiles.horizontalSlash(p, power * 0.7f); });
        }
        FxLib.crossSlash(p.serverLevel(), EMBER, p.getEyePosition().add(p.getLookAngle().scale(1.5)), p.getLookAngle(), 1.0f);
    }

    /** Devil Borrowed: Four Elements - fire, ice, lightning and wind slashes back to back, in a random order. */
    public static void borrowedSlash(ServerPlayer p, float power) {
        java.util.List<String> order = new java.util.ArrayList<>(java.util.List.of("fire", "ice", "lightning", "wind"));
        java.util.Collections.shuffle(order, new java.util.Random(p.getRandom().nextLong()));   // all four, in a new order each cast
        String[] els = order.toArray(new String[0]);
        for (int k = 0; k < 4; k++) {
            final String el = els[k];
            AstaFx.later(1 + k * 4, () -> { if (alive(p)) AstaFx.dwellerSlash(p, power * 0.75f, true, el); });
        }
    }
}
