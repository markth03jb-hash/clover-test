package com.example.arcaneaddon.events;

import com.example.arcaneaddon.ArcaneAddon;
import com.example.arcaneaddon.registry.AddonItemRegistry;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The summoned greatsword may only exist in the player's selected main-hand slot.
 * Every server tick we scan the player's open menu (inventory, chests, ender chests, shulkers, ...)
 * and the cursor, and delete any greatsword that is anywhere else.
 */
@Mod.EventBusSubscriber(modid = ArcaneAddon.MODID)
public class GreatswordCleanup {

    private static boolean isSword(ItemStack s) {
        return !s.isEmpty() && s.is(AddonItemRegistry.SUMMONED_GREATSWORD.get());
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !event.side.isServer()) return;
        Player player = event.player;
        AbstractContainerMenu menu = player.containerMenu;

        for (Slot slot : menu.slots) {
            ItemStack stack = slot.getItem();
            if (!isSword(stack)) continue;
            boolean inSelectedHand = slot.container == player.getInventory()
                    && slot.getContainerSlot() == player.getInventory().selected;
            if (!inSelectedHand) {
                slot.set(ItemStack.EMPTY);   // chest, offhand, armor, other hotbar slot, ...
            }
        }
        if (isSword(menu.getCarried())) {    // held on the mouse cursor
            menu.setCarried(ItemStack.EMPTY);
        }
        // make sure the player's own inventory is also clean when no menu mirrors it
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (i != inv.selected && isSword(inv.getItem(i))) inv.setItem(i, ItemStack.EMPTY);
        }
    }
}
