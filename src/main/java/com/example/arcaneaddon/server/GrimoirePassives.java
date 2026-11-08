package com.example.arcaneaddon.server;

import com.example.arcaneaddon.ArcaneAddon;
import com.example.arcaneaddon.registry.AddonItemRegistry;
import io.redspace.ironsspellbooks.api.events.SpellCooldownAddedEvent;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.damage.SpellDamageSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Passive abilities of the four themed grimoires. Each only works while that book is equipped in the spellbook slot.
 *
 *  Three-Leaf Grimoire   - HOPE:        below 35% health, +20% spell power.
 *  Four-Leaf Grimoire    - FORTUNE:     15% chance that a spell's cooldown is refunded.
 *  Mana Zone Codex       - MANA ZONE:   stand still for 2 s to claim the mana around you: +25% spell power, +75% mana regen.
 *  Spirit-Bound Grimoire - SPIRIT WARD: the spirit fully blocks one spell hit, then needs 20 s to gather again.
 *
 * Buffs are transient attribute modifiers (TimedModifiers); visuals use the addon's own shapes and are deliberately faint.
 */
@Mod.EventBusSubscriber(modid = ArcaneAddon.MODID)
public class GrimoirePassives {
    private static final float HOPE_THRESHOLD = 0.35f;
    private static final double HOPE_BONUS = 0.20;
    private static final float FORTUNE_CHANCE = 0.15f;
    private static final int ZONE_CHARGE_TICKS = 40;
    private static final double ZONE_POWER_BONUS = 0.25;
    private static final double ZONE_REGEN_BONUS = 0.75;
    private static final int WARD_RECHARGE_TICKS = 20 * 20;

    private static final Map<UUID, Boolean> HOPE_ACTIVE = new HashMap<>();
    private static final Map<UUID, Integer> STILL_TICKS = new HashMap<>();
    private static final Map<UUID, Vec3> LAST_POS = new HashMap<>();
    private static final Map<UUID, Long> WARD_USED = new HashMap<>();

    private static boolean wears(Player p, Item book) {
        ItemStack s = Utils.getPlayerSpellbookStack(p);
        return s != null && s.is(book);
    }

    private static void sound(ServerLevel sl, Vec3 at, net.minecraft.sounds.SoundEvent s, float v, float pitch) {
        sl.playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, v, pitch);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        ServerLevel sl = p.serverLevel();
        long now = sl.getGameTime();
        UUID id = p.getUUID();

        // ---------------------------------------------------------------- HOPE (Three-Leaf)
        if (now % 10 == 0) {
            boolean low = wears(p, AddonItemRegistry.THREE_LEAF_GRIMOIRE.get()) && p.getHealth() < p.getMaxHealth() * HOPE_THRESHOLD;
            boolean was = HOPE_ACTIVE.getOrDefault(id, false);
            if (low) {
                TimedModifiers.apply(p, AttributeRegistry.SPELL_POWER.get(), TimedModifiers.HOPE_POWER, "Hope", HOPE_BONUS, AttributeModifier.Operation.MULTIPLY_BASE, 15);
                if (!was) {   // the moment hope kindles
                    FxSend.ring(sl, Fx.WIND, p.position().add(0, 0.05, 0), 0.2f, 1.8f, 0.12f, 12);
                    sound(sl, p.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 0.9f, 1.4f);
                    p.displayClientMessage(Component.translatable("ui.arcane_addon.hope").withStyle(ChatFormatting.GREEN), true);
                }
            } else if (was) {
                TimedModifiers.remove(p, AttributeRegistry.SPELL_POWER.get(), TimedModifiers.HOPE_POWER);
            }
            HOPE_ACTIVE.put(id, low);
        }

        // ---------------------------------------------------------------- MANA ZONE (Codex)
        if (wears(p, AddonItemRegistry.MANA_ZONE_CODEX.get())) {
            Vec3 pos = p.position();
            Vec3 last = LAST_POS.put(id, pos);
            boolean still = last != null && last.distanceToSqr(pos) < 0.0004 && p.onGround();
            int t = still ? STILL_TICKS.getOrDefault(id, 0) + 1 : 0;
            STILL_TICKS.put(id, t);
            if (t == ZONE_CHARGE_TICKS) {   // the zone forms
                FxSend.ring(sl, Fx.ICE, pos.add(0, 0.05, 0), 0.3f, 3.2f, 0.1f, 14);
                FxSend.ring(sl, Fx.SILVER, pos.add(0, 0.05, 0), 3.0f, 3.2f, 0.05f, 14);
                sound(sl, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0f, 0.8f);
                p.displayClientMessage(Component.translatable("ui.arcane_addon.mana_zone").withStyle(ChatFormatting.AQUA), true);
            }
            if (t >= ZONE_CHARGE_TICKS && now % 10 == 0) {
                TimedModifiers.apply(p, AttributeRegistry.SPELL_POWER.get(), TimedModifiers.ZONE_POWER, "Mana Zone", ZONE_POWER_BONUS, AttributeModifier.Operation.MULTIPLY_BASE, 15);
                TimedModifiers.apply(p, AttributeRegistry.MANA_REGEN.get(), TimedModifiers.ZONE_REGEN, "Mana Zone", ZONE_REGEN_BONUS, AttributeModifier.Operation.MULTIPLY_BASE, 15);
                if (now % 30 == 0) FxSend.ring(sl, Fx.ICE, pos.add(0, 0.05, 0), 2.9f, 3.1f, 0.04f, 20);   // faint boundary of the zone
            }
        } else if (STILL_TICKS.remove(id) != null) {
            LAST_POS.remove(id);
        }

        // ---------------------------------------------------------------- SPIRIT WARD ready cue
        if (now % 40 == 0 && wears(p, AddonItemRegistry.SPIRIT_BOUND_GRIMOIRE.get())) {
            Long used = WARD_USED.get(id);
            if (used != null && now - used >= WARD_RECHARGE_TICKS && now - used < WARD_RECHARGE_TICKS + 40) {
                FxSend.ring(sl, Fx.WIND, p.position().add(0, 1.0, 0), 0.6f, 0.9f, 0.6f, 10);   // the spirit has gathered again
                sound(sl, p.position(), SoundEvents.ALLAY_AMBIENT_WITHOUT_ITEM, 0.5f, 1.4f);
            }
        }
    }

    // ---------------------------------------------------------------- FORTUNE (Four-Leaf)
    @SubscribeEvent
    public static void onCooldown(SpellCooldownAddedEvent.Pre e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !wears(p, AddonItemRegistry.FOUR_LEAF_GRIMOIRE.get())) return;
        if (e.getEffectiveCooldown() <= 0 || ThreadLocalRandom.current().nextFloat() >= FORTUNE_CHANCE) return;
        e.setEffectiveCooldown(0);
        ServerLevel sl = p.serverLevel();
        FxSend.ring(sl, Fx.LIGHTNING, p.position().add(0, 1.9, 0), 0.1f, 0.6f, 0.08f, 10);   // a small golden halo
        sound(sl, p.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 1.9f);
        p.displayClientMessage(Component.translatable("ui.arcane_addon.fortune").withStyle(ChatFormatting.GOLD), true);
    }

    // ---------------------------------------------------------------- SPIRIT WARD (Spirit-Bound)
    @SubscribeEvent
    public static void onAttacked(LivingAttackEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !(e.getSource() instanceof SpellDamageSource)) return;
        if (!wears(p, AddonItemRegistry.SPIRIT_BOUND_GRIMOIRE.get())) return;
        ServerLevel sl = p.serverLevel();
        long now = sl.getGameTime();
        Long used = WARD_USED.get(p.getUUID());
        if (used != null && now - used < WARD_RECHARGE_TICKS) return;
        WARD_USED.put(p.getUUID(), now);
        e.setCanceled(true);
        Vec3 c = p.position().add(0, 1.0, 0);
        FxSend.wall(sl, Fx.WIND, c, new Vec3(1, 0, 0), new Vec3(0, 0, 1), 0.7f, 1.3f, -180, 180, 1.2f, 10, 0, 0);   // a gust wraps around you
        Fx.spark(sl, Fx.WIND, c, 0.9f);
        sound(sl, c, SoundEvents.ENDER_DRAGON_FLAP, 0.7f, 1.6f);
        p.displayClientMessage(Component.translatable("ui.arcane_addon.spirit_ward").withStyle(ChatFormatting.DARK_AQUA), true);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        UUID id = e.getEntity().getUUID();
        HOPE_ACTIVE.remove(id);
        STILL_TICKS.remove(id);
        LAST_POS.remove(id);
        WARD_USED.remove(id);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent e) {
        HOPE_ACTIVE.clear();
        STILL_TICKS.clear();
        LAST_POS.clear();
        WARD_USED.clear();
    }
}
