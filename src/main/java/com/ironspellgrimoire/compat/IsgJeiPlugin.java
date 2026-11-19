package com.ironspellgrimoire.compat;

import com.ironspellgrimoire.IronSpellGrimoire;
import com.ironspellgrimoire.registry.AddonBlocks;
import com.ironspellgrimoire.registry.AddonItemRegistry;
import com.ironspellgrimoire.server.GrimoireMastery;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * JEI info pages: how to get each grimoire and what it is, and what the mod's blocks and key items do. Only JEI loads
 * this class (it finds the @JeiPlugin annotation), so without JEI installed it is never touched.
 */
@JeiPlugin
public class IsgJeiPlugin implements IModPlugin {
    @Override
    public ResourceLocation getPluginUid() { return new ResourceLocation(IronSpellGrimoire.MODID, "info"); }

    @Override
    public void registerRecipes(IRecipeRegistration r) {
        for (GrimoireMastery.Book b : GrimoireMastery.BOOKS) {
            ItemStack s = new ItemStack(b.item().get());
            String key = "tooltip." + ForgeRegistries.ITEMS.getKey(s.getItem()).toString().replace(':', '.');
            List<Component> lines = new ArrayList<>();
            if (I18n.exists(key + ".desc")) lines.add(Component.translatable(key + ".desc"));
            lines.add(Component.translatable("jei.iron_spell_grimoire.grimoire"));
            lines.add(Component.translatable("jei.iron_spell_grimoire.mastery"));
            r.addIngredientInfo(s, VanillaTypes.ITEM_STACK, lines.toArray(Component[]::new));
        }
        info(r, AddonBlocks.GRIMOIRE_ALTAR_ITEM, "altar");
        info(r, AddonBlocks.QUEST_BOARD_ITEM, "quest_board");
        info(r, AddonBlocks.POSTER_BOARD_ITEM, "poster_board");
        info(r, AddonBlocks.GRIMOIRE_ARCHIVE_ITEM, "archive");
        info(r, AddonItemRegistry.GRIMOIRE_CODEX, "codex");
        info(r, AddonItemRegistry.TOWER_COMPASS, "tower_compass");
        info(r, AddonItemRegistry.MANA_CORE, "mana_core");
    }

    private static void info(IRecipeRegistration r, Supplier<? extends Item> item, String key) {
        r.addIngredientInfo(new ItemStack(item.get()), VanillaTypes.ITEM_STACK, Component.translatable("jei.iron_spell_grimoire." + key));
    }
}
