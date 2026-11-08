package com.example.arcaneaddon;

import com.example.arcaneaddon.network.AddonNetwork;
import com.example.arcaneaddon.registry.AddonItemRegistry;
import com.example.arcaneaddon.registry.AddonSpellRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.eventbus.api.IEventBus;

@Mod(ArcaneAddon.MODID)
public class ArcaneAddon {
    public static final String MODID = "arcane_addon";

    public ArcaneAddon() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        AddonSpellRegistry.SPELLS.register(modBus);
        AddonItemRegistry.ITEMS.register(modBus);
        AddonItemRegistry.TABS.register(modBus);
        AddonNetwork.init();
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.COMMON, AddonConfig.SPEC);
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.CLIENT, AddonClientConfig.SPEC);
        modBus.addListener((net.minecraftforge.fml.event.config.ModConfigEvent.Loading ev) -> { if (ev.getConfig().getSpec() == AddonClientConfig.SPEC) AddonClientConfig.refresh(); });
        modBus.addListener((net.minecraftforge.fml.event.config.ModConfigEvent.Reloading ev) -> { if (ev.getConfig().getSpec() == AddonClientConfig.SPEC) AddonClientConfig.refresh(); });
    }
}
