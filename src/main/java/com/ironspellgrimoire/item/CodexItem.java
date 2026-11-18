package com.ironspellgrimoire.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/** The Grimoire Codex: the mod's guide (client/CodexScreen). Every new mage is given one (config "starter_codex"). */
public class CodexItem extends Item {
    public CodexItem() { super(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        if (level.isClientSide) DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> com.ironspellgrimoire.client.CodexScreen::open);
        return InteractionResultHolder.sidedSuccess(p.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack s, Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("tooltip.iron_spell_grimoire.grimoire_codex").withStyle(ChatFormatting.GRAY));
    }
}
