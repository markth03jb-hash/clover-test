package com.example.arcaneaddon.server;

import com.example.arcaneaddon.ArcaneAddon;
import io.redspace.ironsspellbooks.api.events.SpellPreCastEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.function.Predicate;

/**
 * Moves that need a target don't start at all without one: no cast animation, no mana, no cooldown - just a short
 * message. (Before, the pose played and the mana went even though the move then found nobody.) The checks are the
 * same ones each move uses to pick its target.
 */
@Mod.EventBusSubscriber(modid = ArcaneAddon.MODID)
public final class SpellTargets {
    private SpellTargets() {}

    private record Need(Predicate<ServerPlayer> ok, String msg, ChatFormatting colour) {}

    private static boolean nearZoneFoe(ServerPlayer p, double r) {
        for (LivingEntity e : FxUtil.enemies(p.serverLevel(), p, p.getBoundingBox().inflate(r))) if (e.distanceTo(p) <= r) return true;
        return false;
    }

    private static final Map<String, Need> NEEDS = Map.of(
            "ribcage_prison", new Need(p -> FxUtil.aimed(p, 20, 18) != null, "ui.arcane_addon.bone.no_target", ChatFormatting.GRAY),
            "spatial_cube", new Need(p -> FxUtil.aimed(p, 30, 18) != null, "ui.arcane_addon.bone.no_target", ChatFormatting.GRAY),
            "soul_chain", new Need(p -> FxUtil.aimed(p, 30, 14) != null, "ui.arcane_addon.soul_chain.miss", ChatFormatting.GOLD),
            "calidos_brachium_barrage", new Need(p -> FxUtil.aimed(p, 20, 20) != null || (HellfireFx.inZone(p) && nearZoneFoe(p, HellfireFx.ZONE_RADIUS)),
                    "ui.arcane_addon.lion.no_target", ChatFormatting.GOLD),
            "flame_lion_paws", new Need(p -> FxUtil.aimed(p, 14, 20) != null, "ui.arcane_addon.lion.no_target", ChatFormatting.GOLD),
            "steel_prison", new Need(p -> FxUtil.aimed(p, 24, 15) != null, "ui.arcane_addon.no_target", ChatFormatting.GRAY));

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPreCast(SpellPreCastEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || e.isCanceled()) return;
        String id = e.getSpellId();
        if (id == null || !id.startsWith("arcane_addon:")) return;
        Need n = NEEDS.get(id.substring("arcane_addon:".length()));
        if (n == null || n.ok().test(p)) return;
        e.setCanceled(true);
        p.displayClientMessage(Component.translatable(n.msg()).withStyle(n.colour()), true);
    }
}
