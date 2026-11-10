package com.ironspellgrimoire.spells;

import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * A spell that belongs to one grimoire (the Thread and Space grimoires use this): it costs mana, has one school,
 * and can only be cast from its own grimoire. The effect itself is a method in that grimoire's Fx class.
 */
public class BookSpell extends AbstractSpell {
    @FunctionalInterface
    public interface Effect { void perform(ServerPlayer caster, float power); }

    private final ResourceLocation spellId;
    private final DefaultConfig config;
    private final Effect effect;
    private final boolean showDamage;
    private final AnimationHolder animation;
    private final Class<? extends Item> book;
    private final String onlyKey;

    public static AnimationHolder anim(String id) { return AnimNames.holder(id); }

    public BookSpell(String id, ResourceLocation school, Class<? extends Item> book, String onlyKey,
                     int cooldownSeconds, int mana, float power, AnimationHolder animation, Effect effect) {
        this.spellId = ResourceLocation.fromNamespaceAndPath("iron_spell_grimoire", id);
        this.config = new DefaultConfig()
                .setMinRarity(SpellRarity.LEGENDARY)
                .setSchoolResource(school)
                .setMaxLevel(1)
                .setCooldownSeconds(cooldownSeconds)
                .setAllowCrafting(false)
                .build();
        this.effect = effect;
        this.animation = animation;
        this.book = book;
        this.onlyKey = onlyKey;
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

    /** An extra condition the caster must meet (e.g. Furioso needs a full Fortune), and what to say when they don't. */
    private java.util.function.Predicate<Player> gate;
    private String gateMsg;

    public BookSpell gate(java.util.function.Predicate<Player> gate, String msg) { this.gate = gate; this.gateMsg = msg; return this; }

    @Override
    public CastResult canBeCastedBy(int spellLevel, CastSource source, MagicData magicData, Player player) {
        CastResult base = super.canBeCastedBy(spellLevel, source, magicData, player);
        if (!base.isSuccess()) return base;
        ItemStack held = Utils.getPlayerSpellbookStack(player);
        if (held == null || !book.isInstance(held.getItem())) {
            return new CastResult(CastResult.Type.FAILURE, Component.translatable(onlyKey).withStyle(ChatFormatting.GRAY));
        }
        if (gate != null && !gate.test(player)) return new CastResult(CastResult.Type.FAILURE, Component.literal(gateMsg).withStyle(ChatFormatting.GRAY));
        return base;
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity caster, CastSource castSource, MagicData playerMagicData) {
        if (!level.isClientSide && caster instanceof ServerPlayer sp) {
            com.ironspellgrimoire.server.CastSync.cast(sp, getCastStartAnimation());
            effect.perform(sp, getSpellPower(spellLevel, caster));
        }
        super.onCast(level, spellLevel, caster, castSource, playerMagicData);
    }
}
