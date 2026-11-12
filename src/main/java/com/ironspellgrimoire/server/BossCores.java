package com.ironspellgrimoire.server;

import com.ironspellgrimoire.IronSpellGrimoire;
import com.ironspellgrimoire.registry.AddonEntities;
import com.ironspellgrimoire.registry.AddonItemRegistry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Every one of the mod's bosses leaves a Mana Core when a player brings it down (the Star Forge's mutations). */
@Mod.EventBusSubscriber(modid = IronSpellGrimoire.MODID)
public final class BossCores {
    private BossCores() {}

    static boolean isBoss(EntityType<?> t) {
        return t == AddonEntities.ZENON.get() || t == AddonEntities.BEAST_OF_DESPAIR.get() || t == AddonEntities.RHYA.get()
                || t == AddonEntities.LICHT.get() || t == AddonEntities.REVENANT.get() || t == AddonEntities.CURSE_WITCH.get()
                || t == AddonEntities.REFLECTION.get() || t == AddonEntities.MEMORY_GUARDIAN.get() || t == AddonEntities.CINDER_SOVEREIGN.get();
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent e) {
        LivingEntity b = e.getEntity();
        if (!(b.level() instanceof ServerLevel sl) || !isBoss(b.getType()) || !(e.getSource().getEntity() instanceof ServerPlayer killer)) return;
        b.spawnAtLocation(new ItemStack(AddonItemRegistry.MANA_CORE.get()), 1f);
        ItemStack trophy = GrimoireUpgrades.trophy(b.getType());   // and its own grimoire upgrade
        if (!trophy.isEmpty()) b.spawnAtLocation(trophy, 1.4f);
        FxSend.ring(sl, Fx.GOLD, b.position().add(0, 1, 0), 0.2f, 2.4f, 0.08f, 16);
        finisher(sl, b, killer, e.getSource());
    }

    /**
     * The finisher frame (client/FinisherFrame): everyone nearby sees the killing blow frozen - inverted, cracking,
     * shattering, DEFEATED. Gold when the blow was an awakened or evolved spell, or from an Ascended grimoire. The
     * moment holds in the world too: everything nearby stops for half a second, and the boss bursts in shards of light.
     */
    private static void finisher(ServerLevel sl, LivingEntity b, ServerPlayer killer, net.minecraft.world.damagesource.DamageSource src) {
        boolean gold = false;
        if (src instanceof io.redspace.ironsspellbooks.damage.SpellDamageSource sds && sds.spell() != null) {
            String id = sds.spell().getSpellId();
            net.minecraft.world.item.ItemStack book = io.redspace.ironsspellbooks.api.util.Utils.getPlayerSpellbookStack(killer);
            gold = Awakening.isAwakenedSpell(id) || SpellEvolution.evolved(book, id) || Ascension.ascended(book);
        }
        net.minecraft.world.phys.Vec3 c = b.position().add(0, b.getBbHeight() * 0.55, 0);
        com.ironspellgrimoire.network.FxPacket k = new com.ironspellgrimoire.network.FxPacket();
        k.shape = 74; k.id = b.getId(); k.c = c; k.life = 34; k.sweep = gold ? 1 : 0;
        for (ServerPlayer p : sl.players()) if (p.distanceToSqr(c) < 48 * 48)
            com.ironspellgrimoire.network.AddonNetwork.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> p), k);
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, b.getBoundingBox().inflate(16), x -> x != b && x.isAlive() && !(x instanceof ServerPlayer)))
            TimeStop.freeze(killer, e, 10, false, null);   // the hitstop
        final boolean g0 = gold;
        int pal = gold ? Fx.GOLD : Fx.SILVER, K = com.ironspellgrimoire.network.FxPacket.KEEP_SHAPE;
        java.util.concurrent.ThreadLocalRandom r = java.util.concurrent.ThreadLocalRandom.current();
        AstaFx.later(8, () -> {   // after the frozen beat: the shatter
            for (int i = 0; i < 10; i++) {
                net.minecraft.world.phys.Vec3 d = new net.minecraft.world.phys.Vec3(r.nextDouble(-1, 1), r.nextDouble(-0.3, 1), r.nextDouble(-1, 1)).normalize();
                FxSend.slash(sl, pal, c.add(d.scale(0.8)), d, new net.minecraft.world.phys.Vec3(0, 1, 0).cross(d).lengthSqr() < 1e-4 ? new net.minecraft.world.phys.Vec3(1, 0, 0) : new net.minecraft.world.phys.Vec3(0, 1, 0), 0.7f, 1.4f, 10, 0.6f, 1.2f);
            }
            FxSend.ring(sl, pal | K, c, 0.4f, 7f, 0.15f, 14);
            FxSend.beam(sl, pal | K, c.add(0, -1, 0), c.add(0, 30, 0), 0.9f, 16);
            FxSend.impactFrame(sl, c, 4, g0 ? FxSend.FRAME_GOLD : FxSend.FRAME_LIGHT, 40);
            FxSend.shake(sl, c, 1.0f, 14, 40);
            sl.playSound(null, b.blockPosition(), net.minecraft.sounds.SoundEvents.GLASS_BREAK, net.minecraft.sounds.SoundSource.HOSTILE, 1.6f, 0.4f);
            sl.playSound(null, b.blockPosition(), net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE, net.minecraft.sounds.SoundSource.HOSTILE, 1.0f, 1.2f);
        });
    }
}
