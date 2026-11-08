package com.example.arcaneaddon.spells;

import com.example.arcaneaddon.ArcaneAddon;
import com.example.arcaneaddon.item.BlackGrimoireItem;
import com.example.arcaneaddon.registry.AddonItemRegistry;
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

/** Shared boilerplate for the grimoire-exclusive Asta spells: grimoire-only, free, instant, optional sword requirement. */
public abstract class GrimoireSpell extends AbstractSpell {
    private final ResourceLocation spellId;
    private final DefaultConfig config;
    public enum Need { NONE, SLAYER_MAIN, ANY_MAIN, DWELLER_ANY, DWELLER_MAIN }
    private final Need need;
    private final boolean showDamage;

    protected GrimoireSpell(String id, int cooldownSeconds, float power, boolean needsSlayer) {
        this(id, cooldownSeconds, power, needsSlayer ? Need.SLAYER_MAIN : Need.NONE);
    }

    protected GrimoireSpell(String id, int cooldownSeconds, float power, Need need) {
        this.spellId = ResourceLocation.fromNamespaceAndPath(ArcaneAddon.MODID, id);
        this.config = new DefaultConfig()
                .setMinRarity(SpellRarity.LEGENDARY)
                .setSchoolResource(SchoolRegistry.EVOCATION_RESOURCE)
                .setMaxLevel(1)
                .setCooldownSeconds(cooldownSeconds)
                .setAllowCrafting(false)
                .build();
        this.need = need;
        this.showDamage = power > 0;
        this.manaCostPerLevel = 0;
        this.baseSpellPower = Math.max(1, (int) power);
        this.spellPowerPerLevel = 0;
        this.castTime = 0;
        this.baseManaCost = 0;
    }

    /** Spell effect, server side only. */
    protected abstract void perform(ServerPlayer caster, float power);

    @Override public ResourceLocation getSpellResource() { return spellId; }
    @Override public DefaultConfig getDefaultConfig() { return config; }
    @Override public CastType getCastType() { return CastType.INSTANT; }

    /** The player pose each Black Grimoire spell plays when cast (Iron's animations + the addon's own). */
    private static final java.util.Map<String, io.redspace.ironsspellbooks.api.util.AnimationHolder> POSES = java.util.Map.ofEntries(
            java.util.Map.entry("zetten", pose("arcane_addon:asta_zetten_fast")),                                // huge vertical cleave
            java.util.Map.entry("sky_splitter", pose("arcane_addon:asta_sky_splitter")),               // wind-up, then upward cleave
            java.util.Map.entry("anti_magic", pose("irons_spellbooks:simple_sword_horizontal_cross_swipe")),
            java.util.Map.entry("ki_sense", pose("irons_spellbooks:cross_arms")),                      // stillness, reading ki
            java.util.Map.entry("instinct_guard", pose("irons_spellbooks:sword_deflect_1")),
            java.util.Map.entry("devil_union", pose("arcane_addon:devil_union_awaken")),               // the transformation pose
            java.util.Map.entry("dweller_slash", pose("arcane_addon:dweller_rising_cut")),
            java.util.Map.entry("borrowed_slash", pose("arcane_addon:dweller_borrowed_sweep")),
            // Devil Union versions
            java.util.Map.entry("devil_zetten", pose("arcane_addon:asta_zetten_fast")),
            java.util.Map.entry("devil_sky_splitter", pose("arcane_addon:asta_sky_splitter")),
            java.util.Map.entry("devil_anti_magic", pose("irons_spellbooks:stomp")),
            java.util.Map.entry("devil_ki_sense", pose("irons_spellbooks:cross_arms")),
            java.util.Map.entry("devil_black_meteorite", pose("arcane_addon:asta_sky_splitter")),
            java.util.Map.entry("devil_instinct_guard", pose("irons_spellbooks:sword_deflect_1")),
            java.util.Map.entry("devil_union_release", pose("irons_spellbooks:cast_t_pose")),
            java.util.Map.entry("devil_dweller_slash", pose("arcane_addon:dark_wild_flurry")),
            java.util.Map.entry("devil_borrowed_slash", pose("arcane_addon:dark_wild_flurry")));

    private static io.redspace.ironsspellbooks.api.util.AnimationHolder pose(String id) {
        return new io.redspace.ironsspellbooks.api.util.AnimationHolder(id, true, true);
    }

    @Override
    public io.redspace.ironsspellbooks.api.util.AnimationHolder getCastStartAnimation() {
        return POSES.getOrDefault(getSpellResource().getPath(), super.getCastStartAnimation());
    }
    @Override public boolean allowLooting() { return false; }
    @Override public boolean canBeCraftedBy(Player player) { return false; }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        if (!showDamage) return List.of();
        return List.of(Component.translatable("ui.irons_spellbooks.damage", Utils.stringTruncation(getSpellPower(spellLevel, caster), 1)));
    }

    @Override
    public CastResult canBeCastedBy(int spellLevel, CastSource source, MagicData magicData, Player player) {
        CastResult base = super.canBeCastedBy(spellLevel, source, magicData, player);
        if (!base.isSuccess()) return base;
        ItemStack book = Utils.getPlayerSpellbookStack(player);
        if (book == null || !(book.getItem() instanceof BlackGrimoireItem)) {
            return new CastResult(CastResult.Type.FAILURE, Component.translatable("ui.arcane_addon.grimoire_only").withStyle(ChatFormatting.RED));
        }
        if (need == Need.DWELLER_ANY && !player.getMainHandItem().is(AddonItemRegistry.DEMON_DWELLER_SWORD.get())
                && !player.getOffhandItem().is(AddonItemRegistry.DEMON_DWELLER_SWORD.get())) {
            return new CastResult(CastResult.Type.FAILURE, Component.translatable("ui.arcane_addon.dweller_required").withStyle(ChatFormatting.RED));
        }
        if (need == Need.DWELLER_MAIN && !player.getMainHandItem().is(AddonItemRegistry.DEMON_DWELLER_SWORD.get())) {
            return new CastResult(CastResult.Type.FAILURE, Component.translatable("ui.arcane_addon.dweller_main_required").withStyle(ChatFormatting.RED));
        }
        if (need == Need.ANY_MAIN && !player.getMainHandItem().is(AddonItemRegistry.DEMON_SLAYER_SWORD.get())
                && !player.getMainHandItem().is(AddonItemRegistry.DEMON_DWELLER_SWORD.get())) {
            return new CastResult(CastResult.Type.FAILURE, Component.translatable("ui.arcane_addon.any_sword_required").withStyle(ChatFormatting.RED));
        }
        if (need == Need.SLAYER_MAIN && !player.getMainHandItem().is(AddonItemRegistry.DEMON_SLAYER_SWORD.get())) {
            return new CastResult(CastResult.Type.FAILURE, Component.translatable("ui.arcane_addon.sword_required").withStyle(ChatFormatting.RED));
        }
        return base;
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity caster, CastSource castSource, MagicData playerMagicData) {
        if (!level.isClientSide && caster instanceof ServerPlayer sp) perform(sp, getSpellPower(spellLevel, caster));
        super.onCast(level, spellLevel, caster, castSource, playerMagicData);
    }
}
