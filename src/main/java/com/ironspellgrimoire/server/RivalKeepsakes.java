package com.ironspellgrimoire.server;

import com.ironspellgrimoire.IronSpellGrimoire;
import com.ironspellgrimoire.registry.AddonItemRegistry;
import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.entity.mobs.IMagicSummon;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The rivals' keepsakes (charm slot), one for the first win over each:
 * <ul>
 *   <li>Prism Ribbon (Seren Vale) - once every 12 s, a blow aimed at you strikes a reflection instead.</li>
 *   <li>Warlord's Standard (Brannoch Tor) - your summons within 8 blocks of you hit 10% harder.</li>
 *   <li>Last Bell (the Bellmars) - fall below half health and you and every ally within 8 blocks gain a 6-point
 *       shield (absorption), once every 30 s.</li>
 *   <li>Ledger Coin (Mira Hollowell) - once a minute, a spell cast below a quarter of your mana is free.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = IronSpellGrimoire.MODID)
public final class RivalKeepsakes {
    private RivalKeepsakes() {}

    static final Map<UUID, Long> RIBBON = new HashMap<>(), BELL = new HashMap<>(), COIN = new HashMap<>();

    static boolean ready(Map<UUID, Long> m, Player p, long gap) {
        long now = p.level().getGameTime();
        Long at = m.get(p.getUUID());
        return at == null || now - at >= gap || now < at;
    }

    @SubscribeEvent
    public static void onAttacked(LivingAttackEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || e.getSource().getEntity() == null || e.getSource().getEntity() == p) return;
        if (!RiftRewards.wears(p, AddonItemRegistry.PRISM_RIBBON.get()) || !ready(RIBBON, p, 240)) return;
        RIBBON.put(p.getUUID(), p.level().getGameTime());
        e.setCanceled(true);
        ServerLevel sl = p.serverLevel();
        BossFx.ring(sl, Fx.MIRROR, p.position().add(0, 1, 0), 0.3f, 1.4f, 0.06f, 8);
        Rivals.snd(sl, p.position(), "rival_glass_step", 0.8f, 1.5f);
        p.displayClientMessage(Component.literal("Prism Ribbon - it struck a reflection.").withStyle(ChatFormatting.AQUA), true);
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent e) {
        Entity a = e.getSource().getEntity();
        if (a == null || a.level().isClientSide || a instanceof Player) return;
        LivingEntity owner = null;
        if (a instanceof IMagicSummon s && s.getSummoner() instanceof LivingEntity l0) owner = l0;
        else if (a instanceof OwnableEntity o) owner = o.getOwner();
        if (owner instanceof ServerPlayer p && p.distanceTo(a) <= 8 && RiftRewards.wears(p, AddonItemRegistry.WARLORDS_STANDARD.get()))
            e.setAmount(e.getAmount() * 1.1f);
    }

    @SubscribeEvent
    public static void onDamage(LivingDamageEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || e.getAmount() <= 0) return;
        if (p.getHealth() - e.getAmount() >= p.getMaxHealth() * 0.5f || p.getHealth() - e.getAmount() <= 0) return;
        if (!RiftRewards.wears(p, AddonItemRegistry.LAST_BELL.get()) || !ready(BELL, p, 600)) return;
        BELL.put(p.getUUID(), p.level().getGameTime());
        ServerLevel sl = p.serverLevel();
        Vec3 c = p.position();
        Rivals.snd(sl, c, "rival_bell", 1.2f, 1f);
        BossFx.ring(sl, Fx.GOLD, c.add(0, 0.1, 0), 0.4f, 8f, 0.05f, 12);
        for (LivingEntity l : sl.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(8), x -> x.isAlive() && ally(p, x))) {
            l.setAbsorptionAmount(Math.max(l.getAbsorptionAmount(), 6f));
            BossFx.ring(sl, Fx.WATER, l.position().add(0, 0.1, 0), 0.6f, 0.8f, 0.05f, 10);
        }
        p.displayClientMessage(Component.literal("The Last Bell rings.").withStyle(ChatFormatting.GOLD), true);
    }

    static boolean ally(Player p, LivingEntity x) {
        if (x == p) return true;
        if (x instanceof Player o) return !o.isSpectator() && (p.getTeam() == null || p.isAlliedTo(o));
        if (x instanceof IMagicSummon s) return s.getSummoner() == p;
        return x instanceof OwnableEntity o && p.getUUID().equals(o.getOwnerUUID());
    }

    @SubscribeEvent
    public static void onCast(SpellOnCastEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        if (!RiftRewards.wears(p, AddonItemRegistry.LEDGER_COIN.get()) || !ready(COIN, p, 1200)) return;
        float max = (float) p.getAttributeValue(AttributeRegistry.MAX_MANA.get());
        if (MagicData.getPlayerMagicData(p).getMana() >= max * 0.25f) return;
        COIN.put(p.getUUID(), p.level().getGameTime());
        RiftRewards.freeNow(p);
        Rivals.snd(p.serverLevel(), p.position(), "rival_coins", 0.7f, 1.4f);
        p.displayClientMessage(Component.literal("Ledger Coin - put it on Mira's account.").withStyle(ChatFormatting.YELLOW), true);
    }
}
