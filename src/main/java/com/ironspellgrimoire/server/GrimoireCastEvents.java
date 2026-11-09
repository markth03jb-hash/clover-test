package com.ironspellgrimoire.server;

import com.ironspellgrimoire.IronSpellGrimoire;
import com.ironspellgrimoire.network.AddonNetwork;
import com.ironspellgrimoire.network.GrimoireOpenPacket;
import io.redspace.ironsspellbooks.api.events.SpellPreCastEvent;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/** Whenever a player wearing any of this addon's grimoires starts any spell, tell clients to open the book. */
@Mod.EventBusSubscriber(modid = IronSpellGrimoire.MODID)
public class GrimoireCastEvents {
    @SubscribeEvent
    public static void onPreCast(SpellPreCastEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        ItemStack book = Utils.getPlayerSpellbookStack(sp);
        if (book == null || book.isEmpty() || !IronSpellGrimoire.MODID.equals(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(book.getItem()).getNamespace())) return;
        if (!sp.isCreative() && GrimoireCeremony.boundToOther(book, sp)) {   // a grimoire only answers the one it chose
            e.setCanceled(true);
            sp.displayClientMessage(net.minecraft.network.chat.Component.literal("This grimoire did not choose you.").withStyle(net.minecraft.ChatFormatting.RED), true);
            return;
        }
        AddonNetwork.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> sp), new GrimoireOpenPacket(sp.getId()));
    }
}
