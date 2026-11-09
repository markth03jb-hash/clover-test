package com.ironspellgrimoire.spells;

import com.ironspellgrimoire.server.AstaFx;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.CastResult;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * A Black Grimoire spell's Devil Union version. These only live in the grimoire while the union lasts
 * (see server/DevilBook) and can only be cast during it. The effect is a method in server/DevilFx.
 */
public class DevilSpell extends GrimoireSpell {
    @FunctionalInterface
    public interface Effect { void perform(ServerPlayer caster, float power); }

    private final Effect effect;

    public DevilSpell(String id, int cooldownSeconds, float power, Need need, Effect effect) {
        super(id, cooldownSeconds, power, need);
        this.effect = effect;
    }

    @Override
    protected void perform(ServerPlayer caster, float power) { effect.perform(caster, power); }

    @Override
    public CastResult canBeCastedBy(int spellLevel, CastSource source, MagicData magicData, Player player) {
        if (player instanceof ServerPlayer sp && !AstaFx.inDevilUnion(sp)) {
            return new CastResult(CastResult.Type.FAILURE, Component.translatable("ui.iron_spell_grimoire.union_over").withStyle(ChatFormatting.DARK_RED));
        }
        return super.canBeCastedBy(spellLevel, source, magicData, player);
    }
}
