package com.example.arcaneaddon.spells;

import com.example.arcaneaddon.item.BlackGrimoireItem;
import com.example.arcaneaddon.registry.AddonItemRegistry;
import com.example.arcaneaddon.server.DashQueue;
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
import java.util.List;
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

public class PiercingDashSpell extends AbstractSpell {
   private final ResourceLocation spellId;
   private final boolean devil;
   private final DefaultConfig defaultConfig = new DefaultConfig()
      .setMinRarity(SpellRarity.LEGENDARY)
      .setSchoolResource(SchoolRegistry.EVOCATION_RESOURCE)
      .setMaxLevel(1)
      .setCooldownSeconds(6.0)
      .setAllowCrafting(false)
      .build();

   public PiercingDashSpell() {
      this("piercing_dash", false);
   }

   /** devil = the Devil Union version (Devil Rush): faster, longer, and the path explodes behind you. */
   public PiercingDashSpell(String id, boolean devil) {
      this.spellId = ResourceLocation.fromNamespaceAndPath("arcane_addon", id);
      this.devil = devil;
      this.manaCostPerLevel = 0;
      this.baseSpellPower = 9;
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
      return new io.redspace.ironsspellbooks.api.util.AnimationHolder("irons_spellbooks:simple_sword_lunge_stab", true, true);   // thrust forward into the dash
   }

   public boolean allowLooting() {
      return false;
   }

   public boolean canBeCraftedBy(Player player) {
      return false;
   }

   public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
      return List.of(Component.translatable("ui.irons_spellbooks.damage", new Object[]{Utils.stringTruncation((double)this.getSpellPower(spellLevel, caster), 1)}));
   }

   public CastResult canBeCastedBy(int spellLevel, CastSource source, MagicData magicData, Player player) {
      CastResult base = super.canBeCastedBy(spellLevel, source, magicData, player);
      if (!base.isSuccess()) {
         return base;
      } else {
         ItemStack equippedBook = Utils.getPlayerSpellbookStack(player);
         if (equippedBook != null && equippedBook.getItem() instanceof BlackGrimoireItem) {
            if (!player.getMainHandItem().is((Item)AddonItemRegistry.DEMON_SLAYER_SWORD.get())
               && !player.getMainHandItem().is((Item)AddonItemRegistry.DEMON_DWELLER_SWORD.get())) {
               return new CastResult(Type.FAILURE, Component.translatable("ui.arcane_addon.any_sword_required").withStyle(ChatFormatting.RED));
            } else {
               return DashQueue.isDashing(player) ? new CastResult(Type.FAILURE, Component.empty()) : base;
            }
         } else {
            return new CastResult(Type.FAILURE, Component.translatable("ui.arcane_addon.grimoire_only").withStyle(ChatFormatting.RED));
         }
      }
   }

   public void onCast(Level level, int spellLevel, LivingEntity caster, CastSource castSource, MagicData playerMagicData) {
      if (caster instanceof ServerPlayer sp && !DashQueue.isDashing(sp)) {
         DashQueue.start(sp, this, this.getSpellPower(spellLevel, caster), sp.getMainHandItem().is((Item)AddonItemRegistry.DEMON_DWELLER_SWORD.get()), devil);
      }

      super.onCast(level, spellLevel, caster, castSource, playerMagicData);
   }
}
