package com.ironspellgrimoire.client;

import com.ironspellgrimoire.item.RiftInkItem;
import com.ironspellgrimoire.registry.AddonItemRegistry;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Rift Ink's flask holds its own colour: the ink layer (tint 1) is coloured by which ink it is. */
@Mod.EventBusSubscriber(modid = "iron_spell_grimoire", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ItemTints {
    private ItemTints() {}

    @SubscribeEvent
    public static void items(RegisterColorHandlersEvent.Item e) {
        e.register((stack, tint) -> tint == 1 ? 0xFF000000 | RiftInkItem.COLORS[RiftInkItem.rule(stack)] : -1, AddonItemRegistry.RIFT_INK.get());
    }
}
