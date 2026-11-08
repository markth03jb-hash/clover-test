package com.example.arcaneaddon.server;

import com.example.arcaneaddon.registry.AddonSounds;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Swaps a move's stand-in vanilla sound for its grimoire's own sound of the same kind: Sfx.of(Sfx.ASTA,
 * SoundEvents.PLAYER_ATTACK_SWEEP) plays Asta's anti-magic slash. Sounds with no grimoire version (armor clanks,
 * buckets...) stay as they are.
 */
public final class Sfx {
    private Sfx() {}

    public static final String ASTA = "asta", DEVIL = "devil", YAMI = "yami", YUNO = "yuno", NOZEL = "nozel", MAGNA = "magna",
            LUCK = "luck", MEREOLEONA = "mereoleona", ZENON = "zenon", KLAUS = "klaus";

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

    public static SoundEvent of(String grimoire, SoundEvent vanilla) {
        String k = KIND.get(vanilla);
        if (k == null) return vanilla;
        SoundEvent own = AddonSounds.get(grimoire + "." + k);
        return own != null ? own : vanilla;
    }
}
