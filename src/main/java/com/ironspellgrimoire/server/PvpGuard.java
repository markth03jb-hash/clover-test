package com.ironspellgrimoire.server;

import com.ironspellgrimoire.AddonConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * The last word on grimoire PvP (AddonConfig.mayHurt): a blow on a player from another player's grimoire - a spell
 * (an Iron's spell source), or anything the mod made (a summon, a thrown blade, a shadow, a projectile) - is stopped
 * when the rule says no. Plain melee with a sword or a fist is the server's own business and is left alone.
 */
@Mod.EventBusSubscriber(modid = "iron_spell_grimoire")
public final class PvpGuard {
    private PvpGuard() {}

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttack(LivingAttackEvent e) {
        if (!(e.getEntity() instanceof Player victim) || victim.level().isClientSide) return;
        var src = e.getSource();
        Player caster = src.getEntity() instanceof Player c ? c : src.getEntity() instanceof net.minecraft.world.entity.OwnableEntity o && o.getOwner() instanceof Player c2 ? c2 : null;
        if (caster == null || caster == victim) return;
        boolean ours = false;
        var direct = src.getDirectEntity();
        if (direct != null && direct != caster) {
            var key = ForgeRegistries.ENTITY_TYPES.getKey(direct.getType());
            ours = key != null && key.getNamespace().equals("iron_spell_grimoire");
        }
        if (!ours && src.getEntity() != null && src.getEntity() != caster) {
            var key = ForgeRegistries.ENTITY_TYPES.getKey(src.getEntity().getType());
            ours = key != null && key.getNamespace().equals("iron_spell_grimoire");
        }
        if (!ours && caster instanceof ServerPlayer sp && GrimoireMastery.bookOf(io.redspace.ironsspellbooks.api.util.Utils.getPlayerSpellbookStack(sp)) != null && src.typeHolder().unwrapKey()
                .map(k -> k.location().getNamespace().equals("irons_spellbooks")).orElse(false)) ours = true;   // a spell cast from one of the mod's grimoires
        if (ours && !AddonConfig.mayHurt(caster, victim)) e.setCanceled(true);
    }
}
