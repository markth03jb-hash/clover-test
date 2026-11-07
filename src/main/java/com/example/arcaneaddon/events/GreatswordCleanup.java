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
        return !s.isEmpty() && s.is(AddonItemRegistry.DEMON_SLAYER_SWORD.get());
    }

    private static boolean isTwin(ItemStack s) {
        return !s.isEmpty() && s.is(AddonItemRegistry.DEMON_DWELLER_SWORD.get());
    }

    private static final int OFFHAND_SLOT = 40;   // Inventory container index of the off hand

    /**
     * Rules: either sword may sit in the selected main-hand slot. In the OFF hand a sword is only valid while the
     * OTHER sword is in the main hand (so swapping works, and the pair vanishes as soon as the main one is gone).
     */
    private static boolean allowed(ItemStack stack, boolean inMain, boolean inOff, ItemStack mainItem) {
        if (inMain) return true;
        if (!inOff) return false;
        return isSword(stack) ? isTwin(mainItem) : isSword(mainItem);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !event.side.isServer()) return;
        Player player = event.player;
        var inv = player.getInventory();
        AbstractContainerMenu menu = player.containerMenu;
        ItemStack mainItem = player.getMainHandItem();

        for (Slot slot : menu.slots) {
            ItemStack stack = slot.getItem();
            if (!isSword(stack) && !isTwin(stack)) continue;
            boolean mine = slot.container == inv;
            boolean inMain = mine && slot.getContainerSlot() == inv.selected;
            boolean inOff = mine && slot.getContainerSlot() == OFFHAND_SLOT;
            if (!allowed(stack, inMain, inOff, mainItem)) slot.set(ItemStack.EMPTY);   // chest, armor, other hotbar slot, ...
        }
        if (isSword(menu.getCarried()) || isTwin(menu.getCarried())) menu.setCarried(ItemStack.EMPTY);   // mouse cursor
        // make sure the player's own inventory is also clean when no menu mirrors it
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if ((isSword(st) || isTwin(st)) && !allowed(st, i == inv.selected, i == OFFHAND_SLOT, mainItem)) inv.setItem(i, ItemStack.EMPTY);
        }
    }
}
