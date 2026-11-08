package com.example.arcaneaddon.spells;

import com.example.arcaneaddon.server.Fx;
import com.example.arcaneaddon.server.FxSend;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellAnimations;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.AnimationHolder;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.damage.DamageSources;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Arcane Pulse: a shockwave of raw arcane energy around the caster (normal Evocation spell, any spellbook). */
public class ArcanePulseSpell extends AbstractSpell {
    private final ResourceLocation spellId = ResourceLocation.fromNamespaceAndPath("arcane_addon", "arcane_pulse");
    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.COMMON)
            .setSchoolResource(SchoolRegistry.EVOCATION_RESOURCE)
            .setMaxLevel(8)
            .setCooldownSeconds(10.0)
            .build();

    public ArcanePulseSpell() {
        this.manaCostPerLevel = 5;
        this.baseSpellPower = 6;
        this.spellPowerPerLevel = 2;
        this.castTime = 0;
        this.baseManaCost = 25;
    }

    @Override
    public ResourceLocation getSpellResource() { return spellId; }

    @Override
    public DefaultConfig getDefaultConfig() { return defaultConfig; }

    @Override
    public CastType getCastType() { return CastType.INSTANT; }

    @Override
    public AnimationHolder getCastStartAnimation() { return com.example.arcaneaddon.spells.AnimNames.holder("irons_spellbooks:stomp"); }   // the shockwave comes from a stomp

    private float getRadius(int spellLevel) { return 3.0f + spellLevel * 0.5f; }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.translatable("ui.irons_spellbooks.damage", Utils.stringTruncation(getSpellPower(spellLevel, caster), 1)),
                Component.translatable("ui.irons_spellbooks.radius", Utils.stringTruncation(getRadius(spellLevel), 1))
        );
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity caster, CastSource castSource, MagicData playerMagicData) {
        if (!level.isClientSide && caster instanceof net.minecraft.server.level.ServerPlayer sp0) com.example.arcaneaddon.server.CastSync.cast(sp0, getCastStartAnimation());
        float radius = getRadius(spellLevel);
        float damage = getSpellPower(spellLevel, caster);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, caster.getBoundingBox().inflate(radius),
                e -> e != caster && e.isAlive() && !e.isAlliedTo(caster) && e.distanceToSqr(caster) <= radius * radius)) {
            DamageSources.applyDamage(target, damage, getDamageSource(caster));
            if (level instanceof ServerLevel sl) Fx.spark(sl, Fx.DARK, target.position().add(0, target.getBbHeight() * 0.5, 0), 0.8f);
        }
        if (level instanceof ServerLevel server) {
            Vec3 c = caster.position().add(0, 0.1, 0);
            FxSend.ring(server, Fx.DARK, c, 0.3f, radius, 0.6f, 10);           // the shockwave
            FxSend.ring(server, Fx.SILVER, c.add(0, 0.6, 0), 0.2f, radius * 0.8f, 0.25f, 8);
        }
        super.onCast(level, spellLevel, caster, castSource, playerMagicData);
    }
}
