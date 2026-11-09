package com.example.arcaneaddon.client;

import com.example.arcaneaddon.server.GrimoireMastery;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Sneak + use while holding a grimoire: opens the grimoire book (client side only). */
@Mod.EventBusSubscriber(modid = "arcane_addon", value = Dist.CLIENT)
public final class GrimoireBookOpen {
    private GrimoireBookOpen() {}

    @SubscribeEvent
    public static void onUse(PlayerInteractEvent.RightClickItem e) {
        if (!e.getLevel().isClientSide || !e.getEntity().isShiftKeyDown()) return;
        if (GrimoireMastery.bookOf(e.getItemStack()) == null) return;
        GrimoireBookScreen.open(e.getItemStack());
        e.setCanceled(true);
        e.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
    }
}
