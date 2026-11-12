package com.ironspellgrimoire.mixin.client;

import com.ironspellgrimoire.spells.IceView;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Under the Scarlet Moon the Blood Grimoire's spells show as ice in every menu Iron's draws them in (the spell bar,
 * tooltips, the wheel...): their icon and name come from {@link IceView} while it's on. Client only.
 */
@Mixin(value = AbstractSpell.class, remap = false)
public abstract class SpellIceViewMixin {
    @Inject(method = "getSpellIconResource", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void iron_spell_grimoire$iceIcon(CallbackInfoReturnable<ResourceLocation> cir) {
        AbstractSpell self = (AbstractSpell) (Object) this;
        if (IceView.applies(self)) cir.setReturnValue(IceView.icon(self));
    }

    @Inject(method = "getDisplayName", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void iron_spell_grimoire$iceName(Player player, CallbackInfoReturnable<MutableComponent> cir) {
        AbstractSpell self = (AbstractSpell) (Object) this;
        if (IceView.applies(self)) cir.setReturnValue(Component.translatable(IceView.nameKey(self)));
    }
}
