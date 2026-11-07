package com.example.arcaneaddon.spells;

import com.example.arcaneaddon.ArcaneAddon;
import com.example.arcaneaddon.item.BlackGrimoireItem;
import com.example.arcaneaddon.registry.AddonItemRegistry;
import com.example.arcaneaddon.server.SummonQueue;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.*;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Exclusive to the Black Grimoire: puts a greatsword in your empty main hand. */
public class SummonGreatswordSpell extends AbstractSpell {
    private final ResourceLocation spellId = ResourceLocation.fromNamespaceAndPath(ArcaneAddon.MODID, "summon_greatsword");

    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.LEGENDARY)
            .setSchoolResource(SchoolRegistry.EVOCATION_RESOURCE)
            .setMaxLevel(1)
            .setCooldownSeconds(1)
            .setAllowCrafting(false)   // can't be made into scrolls / inscribed elsewhere
            .build();

    public SummonGreatswordSpell() {
        this.manaCostPerLevel = 0;
        this.baseSpellPower = 1;
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
    public CastResult canBeCastedBy(int spellLevel, CastSource source, MagicData magicData, Player player) {
        CastResult base = super.canBeCastedBy(spellLevel, source, magicData, player);
        if (!base.isSuccess()) return base;

        // Exclusive: only castable while the Black Grimoire is equipped
        ItemStack equippedBook = Utils.getPlayerSpellbookStack(player);
        if (equippedBook == null || !(equippedBook.getItem() instanceof BlackGrimoireItem)) {
            return new CastResult(CastResult.Type.FAILURE,
                    Component.translatable("ui.arcane_addon.grimoire_only").withStyle(ChatFormatting.RED));
        }
        if (SummonQueue.isPending(player)) return new CastResult(CastResult.Type.FAILURE, Component.empty());
        // Empty main hand -> summon the Demon Slayer Sword. Holding it -> summon the Demon Dweller Sword into the off hand.
        // Holding both -> swap them between the hands.
        if (isPair(player)) return base;
        if (player.getMainHandItem().is(AddonItemRegistry.DEMON_SLAYER_SWORD.get())) {
            if (!player.getOffhandItem().isEmpty()) {
                return new CastResult(CastResult.Type.FAILURE,
                        Component.translatable("ui.arcane_addon.offhand_empty_required").withStyle(ChatFormatting.RED));
            }
        } else if (!player.getMainHandItem().isEmpty()) {
            return new CastResult(CastResult.Type.FAILURE,
                    Component.translatable("ui.arcane_addon.empty_hand_required").withStyle(ChatFormatting.RED));
        }
        return base;
    }

    /** True when one hand holds the Slayer sword and the other the Dweller sword. */
    private static boolean isPair(Player p) {
        ItemStack m = p.getMainHandItem(), o = p.getOffhandItem();
        var slayer = AddonItemRegistry.DEMON_SLAYER_SWORD.get();
        var twin = AddonItemRegistry.DEMON_DWELLER_SWORD.get();
        return (m.is(slayer) && o.is(twin)) || (m.is(twin) && o.is(slayer));
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity caster, CastSource castSource, MagicData playerMagicData) {
        // Sword rises out of the grimoire (client animation) and is placed in your hand a moment later.
        if (caster instanceof ServerPlayer sp && !SummonQueue.isPending(sp)) {
            if (isPair(sp)) SummonQueue.swap(sp);
            else if (sp.getMainHandItem().isEmpty()) SummonQueue.start(sp, false);
            else if (sp.getMainHandItem().is(AddonItemRegistry.DEMON_SLAYER_SWORD.get()) && sp.getOffhandItem().isEmpty()) SummonQueue.start(sp, true);
        }
        super.onCast(level, spellLevel, caster, castSource, playerMagicData);
    }
}
