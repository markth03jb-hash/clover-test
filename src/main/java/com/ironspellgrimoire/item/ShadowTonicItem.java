package com.ironspellgrimoire.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The Market's consumables (Shadow Grimoire): Mana Tonic - 150 mana at once; Shade Salve - 16 health over 4 s.
 * Right-click to use; no status effects.
 */
public class ShadowTonicItem extends Item {
    private final boolean salve;

    public ShadowTonicItem(boolean salve) { super(new Properties().stacksTo(16)); this.salve = salve; }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack st = p.getItemInHand(hand);
        if (!level.isClientSide && p instanceof ServerPlayer sp) {
            if (salve) {
                for (int i = 0; i < 4; i++) com.ironspellgrimoire.server.AstaFx.later(i * 20, () -> { if (sp.isAlive()) sp.heal(4f); });
                level.playSound(null, p.blockPosition(), SoundEvents.HONEY_DRINK, SoundSource.PLAYERS, 0.8f, 0.7f);
            } else {
                var data = io.redspace.ironsspellbooks.api.magic.MagicData.getPlayerMagicData(sp);
                double max = sp.getAttributeValue(io.redspace.ironsspellbooks.api.registry.AttributeRegistry.MAX_MANA.get());
                data.setMana((float) Math.min(max, data.getMana() + 150));
                level.playSound(null, p.blockPosition(), SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 0.8f, 1.2f);
            }
            com.ironspellgrimoire.server.FxUtil.play(sp.serverLevel(), sp.position(), "shadow_whisper", 0.6f, 1.6f);
            if (!p.getAbilities().instabuild) st.shrink(1);
            p.getCooldowns().addCooldown(this, 20);
        }
        return InteractionResultHolder.sidedSuccess(st, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("tooltip.iron_spell_grimoire." + (salve ? "shade_salve" : "mana_tonic")).withStyle(ChatFormatting.GRAY));
    }
}
