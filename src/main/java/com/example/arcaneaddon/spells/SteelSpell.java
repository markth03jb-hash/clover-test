package com.example.arcaneaddon.spells;

import com.example.arcaneaddon.item.SteelGrimoireItem;
import com.example.arcaneaddon.registry.AddonItemRegistry;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastResult;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.AnimationHolder;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * One spell of the Steel Grimoire (Klaus Lunettes' Steel Magic). Costs mana, belongs to Iron's Evocation school
 * (creation magic), and can only be cast from the Steel Grimoire. The effect itself is a method in
 * {@link com.example.arcaneaddon.server.SteelFx}.
 */
public class SteelSpell extends AbstractSpell {
    @FunctionalInterface
    public interface Effect { void perform(ServerPlayer caster, float power); }

    private final ResourceLocation spellId;
    private final DefaultConfig config;
    private final Effect effect;
    private final boolean showDamage;
    private final AnimationHolder animation;
    private final boolean needsKatana;

    /** Shorthand for an Iron's/addon player animation id such as "irons_spellbooks:katana_upslash". */
    public static AnimationHolder anim(String id) { return new AnimationHolder(id, true, true); }

    /**
     * @param needsKatana sword techniques (every slash, the thrust, the blade coating) need the Dark Katana in the main hand
     */
    public SteelSpell(String id, int cooldownSeconds, int mana, float power, boolean needsKatana, AnimationHolder animation, Effect effect) {
        this.needsKatana = needsKatana;
        this.spellId = ResourceLocation.fromNamespaceAndPath("arcane_addon", id);
        this.config = new DefaultConfig()
                .setMinRarity(SpellRarity.LEGENDARY)
                .setSchoolResource(SchoolRegistry.EVOCATION_RESOURCE)
                .setMaxLevel(1)
                .setCooldownSeconds(cooldownSeconds)
                .setAllowCrafting(false)
                .build();
        this.effect = effect;
        this.animation = animation;
        this.showDamage = power > 0;
        this.baseManaCost = mana;
        this.manaCostPerLevel = 0;
        this.baseSpellPower = Math.max(1, (int) power);
        this.spellPowerPerLevel = 0;
        this.castTime = 0;
    }

    @Override public ResourceLocation getSpellResource() { return spellId; }
    @Override public DefaultConfig getDefaultConfig() { return config; }
    @Override public CastType getCastType() { return CastType.INSTANT; }
    @Override public AnimationHolder getCastStartAnimation() { return animation; }
    @Override public boolean allowLooting() { return false; }
    @Override public boolean canBeCraftedBy(Player player) { return false; }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return showDamage
                ? List.of(Component.translatable("ui.irons_spellbooks.damage", Utils.stringTruncation(getSpellPower(spellLevel, caster), 1)))
                : List.of();
    }

    @Override
    public CastResult canBeCastedBy(int spellLevel, CastSource source, MagicData magicData, Player player) {
        CastResult base = super.canBeCastedBy(spellLevel, source, magicData, player);
        if (!base.isSuccess()) return base;
        ItemStack book = Utils.getPlayerSpellbookStack(player);
        if (book == null || !(book.getItem() instanceof SteelGrimoireItem)) {
            return new CastResult(CastResult.Type.FAILURE, Component.translatable("ui.arcane_addon.steel_grimoire_only").withStyle(ChatFormatting.GRAY));
        }
        if (needsKatana && !player.getMainHandItem().is(AddonItemRegistry.DARK_KATANA.get())) {
            return new CastResult(CastResult.Type.FAILURE, Component.translatable("ui.arcane_addon.katana_required").withStyle(ChatFormatting.GRAY));
        }
        return base;
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity caster, CastSource castSource, MagicData playerMagicData) {
        if (!level.isClientSide && caster instanceof ServerPlayer sp) effect.perform(sp, getSpellPower(spellLevel, caster));
        super.onCast(level, spellLevel, caster, castSource, playerMagicData);
    }
}
