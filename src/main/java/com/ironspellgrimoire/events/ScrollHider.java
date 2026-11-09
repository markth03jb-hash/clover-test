package com.ironspellgrimoire.events;

import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * Grimoire spells live only inside their grimoire: their scrolls are taken out of every creative tab, which also
 * removes them from JEI/REI (they list what the tabs list). Loot and the scroll forge are already shut off per spell.
 */
@Mod.EventBusSubscriber(modid = "iron_spell_grimoire", bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ScrollHider {
    private ScrollHider() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void hide(BuildCreativeModeTabContentsEvent e) {
        List<ItemStack> drop = new ArrayList<>();
        for (var en : e.getEntries()) {
            ItemStack st = en.getKey();
            try {
                if (!ISpellContainer.isSpellContainer(st)) continue;
                ISpellContainer c = ISpellContainer.get(st);
                if (!(st.getItem() instanceof io.redspace.ironsspellbooks.item.Scroll)) continue;   // only scrolls; the grimoires keep their spells
                for (var slot : c.getActiveSpells()) {
                    if ("iron_spell_grimoire".equals(slot.getSpell().getSpellResource().getNamespace())) { drop.add(st); break; }
                }
            } catch (Throwable ignored) { }
        }
        for (ItemStack st : drop) e.getEntries().remove(st);
    }
}
