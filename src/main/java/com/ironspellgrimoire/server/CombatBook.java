package com.ironspellgrimoire.server;

import com.ironspellgrimoire.IronSpellGrimoire;
import com.ironspellgrimoire.network.AddonNetwork;
import com.ironspellgrimoire.network.GrimoireOpenPacket;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Hitting something or getting hit opens the floating grimoire (it stays open 10 s after the last thing that happened). */
@Mod.EventBusSubscriber(modid = IronSpellGrimoire.MODID)
public final class CombatBook {
    private CombatBook() {}
    private static final Map<UUID, Long> LAST = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent e) {
        poke(e.getSource().getEntity());
        poke(e.getEntity());
    }

    private static void poke(Entity en) {
        if (!(en instanceof ServerPlayer p) || p.connection == null) return;
        long now = p.serverLevel().getGameTime();
        Long last = LAST.get(p.getUUID());
        if (last != null && now - last < 20) return;     // refresh once a second at most
        ItemStack book = Utils.getPlayerSpellbookStack(p);
        if (book == null || book.isEmpty() || !IronSpellGrimoire.MODID.equals(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(book.getItem()).getNamespace())) return;
        LAST.put(p.getUUID(), now);
        AddonNetwork.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), new GrimoireOpenPacket(p.getId(), false));
    }
}
