package com.example.arcaneaddon.server;

import com.example.arcaneaddon.registry.AddonSounds;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Which kind of move sound a vanilla stand-in sound is: FxUtil.sound plays the addon's CC0 clip of that kind
 * (with the vanilla sound kept quietly underneath for body). Sounds with no kind (armor clanks, buckets...) stay.
 */
final class Sfx {
    private Sfx() {}

    private static final Map<SoundEvent, String> KIND = new IdentityHashMap<>();

    private static void kind(String k, SoundEvent... es) { for (SoundEvent e : es) KIND.put(e, k); }

    static {
        kind("slash", SoundEvents.PLAYER_ATTACK_SWEEP, SoundEvents.PLAYER_ATTACK_STRONG, SoundEvents.PLAYER_ATTACK_CRIT, SoundEvents.TRIDENT_THROW, SoundEvents.PLAYER_ATTACK_KNOCKBACK);
        kind("blast", SoundEvents.GENERIC_EXPLODE, SoundEvents.WITHER_SHOOT, SoundEvents.WARDEN_SONIC_BOOM, SoundEvents.TRIDENT_THUNDER, SoundEvents.DRAGON_FIREBALL_EXPLODE);
        kind("whoosh", SoundEvents.ENDER_DRAGON_FLAP, SoundEvents.TRIDENT_RIPTIDE_1, SoundEvents.TRIDENT_RIPTIDE_2, SoundEvents.TRIDENT_RIPTIDE_3, SoundEvents.ENDERMAN_TELEPORT, SoundEvents.PHANTOM_FLAP);
        kind("impact", SoundEvents.ANVIL_LAND, SoundEvents.ANVIL_PLACE, SoundEvents.TRIDENT_HIT, SoundEvents.GLASS_BREAK, SoundEvents.ANVIL_USE, SoundEvents.IRON_DOOR_CLOSE, SoundEvents.CHAIN_PLACE, SoundEvents.SHIELD_BLOCK);
        kind("charge", SoundEvents.BEACON_POWER_SELECT, SoundEvents.BEACON_ACTIVATE, SoundEvents.WARDEN_SONIC_CHARGE, SoundEvents.ENCHANTMENT_TABLE_USE, SoundEvents.AMETHYST_BLOCK_CHIME, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundEvents.SOUL_ESCAPE, SoundEvents.EVOKER_PREPARE_ATTACK);
        kind("fire", SoundEvents.BLAZE_SHOOT, SoundEvents.FIRECHARGE_USE, SoundEvents.BLAZE_AMBIENT, SoundEvents.GHAST_SHOOT);
        kind("thunder", SoundEvents.LIGHTNING_BOLT_IMPACT, SoundEvents.LIGHTNING_BOLT_THUNDER);
        kind("roar", SoundEvents.RAVAGER_ROAR, SoundEvents.ENDER_DRAGON_GROWL, SoundEvents.WITHER_SPAWN);
    }

    static String kind(SoundEvent vanilla) { return KIND.get(vanilla); }

    static SoundEvent own(String kind) { return kind == null ? null : AddonSounds.get(kind); }
}
