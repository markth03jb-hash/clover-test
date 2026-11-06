package com.example.arcaneaddon.spells;

import com.example.arcaneaddon.ArcaneAddon;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.*;
import io.redspace.ironsspellbooks.api.util.AnimationHolder;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.damage.DamageSources;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.List;

/** Example addon spell: an instant-cast shockwave that damages everything around the caster. */
public class ArcanePulseSpell extends AbstractSpell {
    private final ResourceLocation spellId = ResourceLocation.fromNamespaceAndPath(ArcaneAddon.MODID, "arcane_pulse");

    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.COMMON)
            .setSchoolResource(SchoolRegistry.EVOCATION_RESOURCE)
            .setMaxLevel(8)
            .setCooldownSeconds(10)
            .build();

    public ArcanePulseSpell() {
        this.manaCostPerLevel = 5;
        this.baseSpellPower = 6;
        this.spellPowerPerLevel = 2;
        this.castTime = 0;
        this.baseManaCost = 25;
    }

    @Override
    public ResourceLocation getSpellResource() {
        return spellId;
    }

    @Override
    public DefaultConfig getDefaultConfig() {
        return defaultConfig;
    }

    @Override
    public CastType getCastType() {
        return CastType.INSTANT;
    }

    @Override
    public AnimationHolder getCastStartAnimation() {
        return SpellAnimations.ANIMATION_INSTANT_CAST;
    }

    private float getRadius(int spellLevel) {
        return 3f + spellLevel * 0.5f;
    }

    // Shown in the spell tooltip
    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.translatable("ui.irons_spellbooks.damage", Utils.stringTruncation(getSpellPower(spellLevel, caster), 1)),
                Component.translatable("ui.irons_spellbooks.radius", Utils.stringTruncation(getRadius(spellLevel), 1)));
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity caster, CastSource castSource, MagicData playerMagicData) {
        float radius = getRadius(spellLevel);
        float damage = getSpellPower(spellLevel, caster);

        var targets = level.getEntitiesOfClass(LivingEntity.class, caster.getBoundingBox().inflate(radius), e ->
                e != caster && e.isAlive() && !e.isAlliedTo(caster) && e.distanceToSqr(caster) <= radius * radius);
        for (LivingEntity target : targets) {
            DamageSources.applyDamage(target, damage, getDamageSource(caster));
        }

        if (level instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.END_ROD, caster.getX(), caster.getY() + 1, caster.getZ(),
                    40, radius / 3, 0.4, radius / 3, 0.05);
        }
        super.onCast(level, spellLevel, caster, castSource, playerMagicData);
    }
}
