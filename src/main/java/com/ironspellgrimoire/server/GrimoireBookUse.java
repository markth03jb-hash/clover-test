package com.ironspellgrimoire.server;

import com.ironspellgrimoire.IronSpellGrimoire;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Sneak + use on a grimoire only opens it (client): the server must not equip or swap it as a normal use would. */
@Mod.EventBusSubscriber(modid = IronSpellGrimoire.MODID)
public final class GrimoireBookUse {
    private GrimoireBookUse() {}

    @SubscribeEvent
    public static void onUse(PlayerInteractEvent.RightClickItem e) {
        if (e.getLevel().isClientSide || !e.getEntity().isShiftKeyDown()) return;
        if (GrimoireMastery.bookOf(e.getItemStack()) == null) return;
        e.setCanceled(true);
        e.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
    }
}
