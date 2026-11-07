package com.example.arcaneaddon.spells;

import com.example.arcaneaddon.ArcaneAddon;
import com.example.arcaneaddon.item.BlackGrimoireItem;
import com.example.arcaneaddon.registry.AddonItemRegistry;
import com.example.arcaneaddon.server.DashQueue;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.*;
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

/** Grimoire-exclusive: dash forward sword-first, piercing every enemy on the way. Needs the Demon Slayer Sword in hand. */
public class PiercingDashSpell extends AbstractSpell {
    private final ResourceLocation spellId = ResourceLocation.fromNamespaceAndPath(ArcaneAddon.MODID, "piercing_dash");

    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.LEGENDARY)
            .setSchoolResource(SchoolRegistry.EVOCATION_RESOURCE)
            .setMaxLevel(1)
            .setCooldownSeconds(6)
            .setAllowCrafting(false)
            .build();

    public PiercingDashSpell() {
        this.manaCostPerLevel = 0;
        this.baseSpellPower = 9;       // damage dealt to every enemy pierced (scaled by spell power)
        this.spellPowerPerLevel = 0;
        this.castTime = 0;
        this.baseManaCost = 0;   // free to cast
    }

    @Override public ResourceLocation getSpellResource() { return spellId; }
    @Override public DefaultConfig getDefaultConfig() { return defaultConfig; }
    @Override public CastType getCastType() { return CastType.INSTANT; }
    @Override public boolean allowLooting() { return false; }
    @Override public boolean canBeCraftedBy(Player player) { return false; }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(Component.translatable("ui.irons_spellbooks.damage", Utils.stringTruncation(getSpellPower(spellLevel, caster), 1)));
    }

    @Override
    public CastResult canBeCastedBy(int spellLevel, CastSource source, MagicData magicData, Player player) {
        CastResult base = super.canBeCastedBy(spellLevel, source, magicData, player);
        if (!base.isSuccess()) return base;
        ItemStack equippedBook = Utils.getPlayerSpellbookStack(player);
        if (equippedBook == null || !(equippedBook.getItem() instanceof BlackGrimoireItem)) {
            return new CastResult(CastResult.Type.FAILURE,
                    Component.translatable("ui.arcane_addon.grimoire_only").withStyle(ChatFormatting.RED));
        }
        if (!player.getMainHandItem().is(AddonItemRegistry.DEMON_SLAYER_SWORD.get()) && !player.getMainHandItem().is(AddonItemRegistry.DEMON_DWELLER_SWORD.get())) {
            return new CastResult(CastResult.Type.FAILURE,
                    Component.translatable("ui.arcane_addon.any_sword_required").withStyle(ChatFormatting.RED));
        }
        if (DashQueue.isDashing(player)) return new CastResult(CastResult.Type.FAILURE, Component.empty());
        return base;
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity caster, CastSource castSource, MagicData playerMagicData) {
        if (caster instanceof ServerPlayer sp && !DashQueue.isDashing(sp)) {
            DashQueue.start(sp, this, getSpellPower(spellLevel, caster), sp.getMainHandItem().is(AddonItemRegistry.DEMON_DWELLER_SWORD.get()));
        }
        super.onCast(level, spellLevel, caster, castSource, playerMagicData);
    }
}
