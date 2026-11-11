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
                || t == AddonEntities.LICHT.get() || t == AddonEntities.REVENANT.get() || t == AddonEntities.CURSE_WITCH.get();
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent e) {
        LivingEntity b = e.getEntity();
        if (!(b.level() instanceof ServerLevel sl) || !isBoss(b.getType()) || !(e.getSource().getEntity() instanceof ServerPlayer)) return;
        b.spawnAtLocation(new ItemStack(AddonItemRegistry.MANA_CORE.get()), 1f);
        FxSend.ring(sl, Fx.GOLD, b.position().add(0, 1, 0), 0.2f, 2.4f, 0.08f, 16);
    }
}
