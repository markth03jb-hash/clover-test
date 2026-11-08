package com.example.arcaneaddon.spells;

import com.example.arcaneaddon.item.BlackGrimoireItem;
import com.example.arcaneaddon.registry.AddonItemRegistry;
import com.example.arcaneaddon.server.SummonQueue;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastResult;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.spells.CastResult.Type;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class SummonGreatswordSpell extends AbstractSpell {
   private final ResourceLocation spellId;
   private final boolean devil;
   private final DefaultConfig defaultConfig = new DefaultConfig()
      .setMinRarity(SpellRarity.LEGENDARY)
      .setSchoolResource(SchoolRegistry.EVOCATION_RESOURCE)
      .setMaxLevel(1)
      .setCooldownSeconds(1.0)
      .setAllowCrafting(false)
      .build();

   public SummonGreatswordSpell() {
      this("summon_greatsword", false);
   }

   /** devil = the Devil Union version (Devil Twin Swap): every swap strikes with both swords. */
   public SummonGreatswordSpell(String id, boolean devil) {
      this.spellId = ResourceLocation.fromNamespaceAndPath("arcane_addon", id);
      this.devil = devil;
      this.manaCostPerLevel = 0;
      this.baseSpellPower = 1;
      this.spellPowerPerLevel = 0;
      this.castTime = 0;
      this.baseManaCost = 0;
   }

   public ResourceLocation getSpellResource() {
      return this.spellId;
   }

   public DefaultConfig getDefaultConfig() {
      return this.defaultConfig;
   }

   public CastType getCastType() {
      return CastType.INSTANT;
   }

   @Override
   public io.redspace.ironsspellbooks.api.util.AnimationHolder getCastStartAnimation() {
      return new io.redspace.ironsspellbooks.api.util.AnimationHolder("irons_spellbooks:simple_sword_upward_swipe", true, true);   // draw the blade up out of the grimoire
   }

   public boolean allowLooting() {
      return false;
   }

   public boolean canBeCraftedBy(Player player) {
      return false;
   }

   public CastResult canBeCastedBy(int spellLevel, CastSource source, MagicData magicData, Player player) {
      CastResult base = super.canBeCastedBy(spellLevel, source, magicData, player);
      if (!base.isSuccess()) {
         return base;
      } else {
         ItemStack equippedBook = Utils.getPlayerSpellbookStack(player);
         if (equippedBook == null || !(equippedBook.getItem() instanceof BlackGrimoireItem)) {
            return new CastResult(Type.FAILURE, Component.translatable("ui.arcane_addon.grimoire_only").withStyle(ChatFormatting.RED));
         } else if (SummonQueue.isPending(player)) {
            return new CastResult(Type.FAILURE, Component.empty());
         } else if (isPair(player)) {
            return base;
         } else {
            if (player.getMainHandItem().is((Item)AddonItemRegistry.DEMON_SLAYER_SWORD.get())) {
               if (!player.getOffhandItem().isEmpty()) {
                  return new CastResult(Type.FAILURE, Component.translatable("ui.arcane_addon.offhand_empty_required").withStyle(ChatFormatting.RED));
               }
            } else if (!player.getMainHandItem().isEmpty()) {
               return new CastResult(Type.FAILURE, Component.translatable("ui.arcane_addon.empty_hand_required").withStyle(ChatFormatting.RED));
            }

            return base;
         }
      }
   }

   private static boolean isPair(Player p) {
      ItemStack m = p.getMainHandItem();
      ItemStack o = p.getOffhandItem();
      Item slayer = (Item)AddonItemRegistry.DEMON_SLAYER_SWORD.get();
      Item twin = (Item)AddonItemRegistry.DEMON_DWELLER_SWORD.get();
      return m.is(slayer) && o.is(twin) || m.is(twin) && o.is(slayer);
   }

   public void onCast(Level level, int spellLevel, LivingEntity caster, CastSource castSource, MagicData playerMagicData) {
      if (caster instanceof ServerPlayer sp && !SummonQueue.isPending(sp)) {
         if (isPair(sp)) {
            SummonQueue.swap(sp, devil);
         } else if (sp.getMainHandItem().isEmpty()) {
            SummonQueue.start(sp, false);
         } else if (sp.getMainHandItem().is((Item)AddonItemRegistry.DEMON_SLAYER_SWORD.get()) && sp.getOffhandItem().isEmpty()) {
            SummonQueue.start(sp, true);
         }
      }

      super.onCast(level, spellLevel, caster, castSource, playerMagicData);
   }
}
