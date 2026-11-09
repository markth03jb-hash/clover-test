package com.ironspellgrimoire;

import com.ironspellgrimoire.network.AddonNetwork;
import com.ironspellgrimoire.registry.AddonItemRegistry;
import com.ironspellgrimoire.registry.AddonSpellRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.eventbus.api.IEventBus;

@Mod(IronSpellGrimoire.MODID)
public class IronSpellGrimoire {
    public static final String MODID = "iron_spell_grimoire";

    public IronSpellGrimoire() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        AddonSpellRegistry.SPELLS.register(modBus);
        com.ironspellgrimoire.registry.AddonEntities.ENTITIES.register(modBus);
        com.ironspellgrimoire.registry.AddonBlocks.init();
        com.ironspellgrimoire.registry.AddonBlocks.BLOCKS.register(modBus);
        AddonItemRegistry.ITEMS.register(modBus);
        com.ironspellgrimoire.registry.AddonSounds.SOUNDS.register(modBus);
        AddonItemRegistry.TABS.register(modBus);
        AddonNetwork.init();
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.COMMON, AddonConfig.SPEC, "iron_spell_grimoire-common.toml");
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.CLIENT, AddonClientConfig.SPEC, "iron_spell_grimoire-client.toml");
        modBus.addListener((net.minecraftforge.fml.event.config.ModConfigEvent.Loading ev) -> { if (ev.getConfig().getSpec() == AddonClientConfig.SPEC) AddonClientConfig.refresh(); });
        modBus.addListener((net.minecraftforge.fml.event.config.ModConfigEvent.Reloading ev) -> { if (ev.getConfig().getSpec() == AddonClientConfig.SPEC) AddonClientConfig.refresh(); });
    }
}
