package com.ironspellgrimoire.server;

import com.ironspellgrimoire.IronSpellGrimoire;
import com.ironspellgrimoire.entity.WildFolk;
import com.ironspellgrimoire.item.SignatureItem;
import com.ironspellgrimoire.registry.AddonItemRegistry;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The wild leaders' charms (rare drops - WildFolkAI.drops), and the Ironbound Twins' Vow. Worn in the charm slot.
 * Moss Knot (the Fen Matron): after 8 s unhit, 2 health every 4 s. Slate Ring (the Peak Warden): +2 armour, 15%
 * knockback resistance. Sun Shard (the Sun Herald): spells +8% under the open daytime sky. Rime Pin (the Hoarfrost
 * Jarl): melee hits slow by 20% for 2 s. Lantern Eye (the Deepwarden): +15% damage standing in the dark.
 * Ironbound Vow: with another mage within 8 blocks (12 Hardened), you both take 10% less (15% Hardened).
 * Father Sallow's (ASHFALL - one or the other, as the party chose): Sallow's Hook, +15% on a foe below 30% health;
 * Sallow's Rosary, a killing blow leaves you at 1 health instead, once every 2 minutes.
 */
@Mod.EventBusSubscriber(modid = IronSpellGrimoire.MODID)
public final class FolkCharms {
    private FolkCharms() {}

    static final UUID RING_ARMOR = UUID.fromString("f01c0000-0000-4000-8000-000000000011"), RING_KB = UUID.fromString("f01c0000-0000-4000-8000-000000000012"),
            PIN_SLOW = UUID.fromString("f01c0000-0000-4000-8000-000000000013");
    static final Map<UUID, Long> LAST_HIT = new HashMap<>();

    public static ItemStack charm(WildFolk.Family f) {
        return new ItemStack(switch (f) {
            case MIRE -> AddonItemRegistry.MOSS_KNOT.get();
            case STONE -> AddonItemRegistry.SLATE_RING.get();
            case SAND -> AddonItemRegistry.SUN_SHARD.get();
            case FROST -> AddonItemRegistry.RIME_PIN.get();
            case HOLLOW -> AddonItemRegistry.LANTERN_EYE.get();
            default -> AddonItemRegistry.MOSS_KNOT.get();
        });
    }

    static boolean wears(ServerPlayer p, net.minecraft.world.item.Item i) { return !BossRelics.worn(p, i).isEmpty(); }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % 20 != 0) return;
        if (wears(p, AddonItemRegistry.SLATE_RING.get())) {
            TimedModifiers.apply(p, Attributes.ARMOR, RING_ARMOR, "Slate Ring", 2, AttributeModifier.Operation.ADDITION, 30);
            TimedModifiers.apply(p, Attributes.KNOCKBACK_RESISTANCE, RING_KB, "Slate Ring", 0.15, AttributeModifier.Operation.ADDITION, 30);
        }
        if (p.tickCount % 80 == 0 && p.getHealth() < p.getMaxHealth() && wears(p, AddonItemRegistry.MOSS_KNOT.get())) {
            long now = p.serverLevel().getGameTime();
            if (now - LAST_HIT.getOrDefault(p.getUUID(), 0L) >= 160) p.heal(2f);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onHurt(LivingHurtEvent e) {
        if (e.getEntity() instanceof ServerPlayer v) {
            LAST_HIT.put(v.getUUID(), v.serverLevel().getGameTime());
            if (v.getHealth() < v.getMaxHealth() * 0.4f && wears(v, AddonItemRegistry.CINDER_TOKEN.get())) e.setAmount(e.getAmount() * 0.85f);
            float vow = vow(v);
            if (vow < 1f) e.setAmount(e.getAmount() * vow);
        }
        if (!(e.getSource().getEntity() instanceof ServerPlayer a) || e.getSource().getEntity() == e.getEntity()) return;
        float k = 1f;
        if (e.getSource() instanceof io.redspace.ironsspellbooks.damage.SpellDamageSource && a.level().isDay() && a.level().canSeeSky(a.blockPosition())
                && wears(a, AddonItemRegistry.SUN_SHARD.get())) k *= 1.08f;
        if (a.level().getMaxLocalRawBrightness(a.blockPosition()) < 7 && wears(a, AddonItemRegistry.LANTERN_EYE.get())) k *= 1.15f;
        if (e.getEntity().getHealth() < e.getEntity().getMaxHealth() * 0.3f && wears(a, AddonItemRegistry.SALLOW_HOOK.get())) k *= 1.15f;
        if (wears(a, AddonItemRegistry.VEY_SEAL.get())) {   // the ledger: the same foe, again and again
            long now = a.serverLevel().getGameTime();
            long[] st = SEAL.get(a.getUUID());
            int stacks = st != null && st[0] == e.getEntity().getId() && now - st[2] <= 80 ? (int) st[1] : 0;
            k *= 1f + 0.03f * stacks;
            SEAL.put(a.getUUID(), new long[]{e.getEntity().getId(), Math.min(5, stacks + 1), now});
        }
        if (BookTree.melee(e.getSource()) && wears(a, AddonItemRegistry.RIME_PIN.get()))
            TimedModifiers.apply(e.getEntity(), Attributes.MOVEMENT_SPEED, PIN_SLOW, "Rime Pin", -0.2, AttributeModifier.Operation.MULTIPLY_TOTAL, 40);
        if (k != 1f) e.setAmount(e.getAmount() * k);
    }

    static final Map<UUID, Long> ROSARY = new HashMap<>();
    static final Map<UUID, long[]> SEAL = new HashMap<>();
    public static final int ROSARY_CD = 2400;

    /** Sallow's Rosary: the blow that would have killed you leaves you at 1 health (once every 2 minutes). */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onDeath(net.minecraftforge.event.entity.living.LivingDeathEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || e.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        if (!wears(p, AddonItemRegistry.SALLOW_ROSARY.get())) return;
        long now = p.serverLevel().getGameTime();
        if (now < ROSARY.getOrDefault(p.getUUID(), 0L)) return;
        ROSARY.put(p.getUUID(), now + ROSARY_CD);
        e.setCanceled(true);
        p.setHealth(1f);
        p.invulnerableTime = 40;
        FxSend.ring(p.serverLevel(), Fx.FIRE, p.position().add(0, 1, 0), 0.3f, 2.2f, 0.08f, 14);
        p.serverLevel().playSound(null, p.blockPosition(), net.minecraft.sounds.SoundEvents.BELL_RESONATE, net.minecraft.sounds.SoundSource.PLAYERS, 1f, 0.7f);
        p.displayClientMessage(net.minecraft.network.chat.Component.literal("A bead of the rosary burns out. You're still standing.").withStyle(net.minecraft.ChatFormatting.GOLD), true);
    }

    /** The Ironbound Vow: the wearer, and any mage beside a wearer, take less while they stand together. */
    static float vow(ServerPlayer v) {
        float best = 1f;
        for (ServerPlayer o : v.serverLevel().players()) {
            if (o == v || o.isSpectator() || o.distanceToSqr(v) > 12 * 12) continue;
            for (ServerPlayer w : new ServerPlayer[]{v, o}) {
                ItemStack vow = BossRelics.worn(w, AddonItemRegistry.IRONBOUND_VOW.get());
                if (vow.isEmpty()) continue;
                boolean hard = SignatureItem.hardened(vow);
                if (o.distanceToSqr(v) <= (hard ? 144 : 64)) best = Math.min(best, hard ? 0.85f : 0.9f);
            }
        }
        return best;
    }
}
