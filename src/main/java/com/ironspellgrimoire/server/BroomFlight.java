package com.ironspellgrimoire.server;

import com.ironspellgrimoire.IronSpellGrimoire;
import com.ironspellgrimoire.entity.BroomEntity;
import com.ironspellgrimoire.network.FxPacket;
import com.ironspellgrimoire.registry.AddonItemRegistry;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Brooms: every player gets one Magic Broom, once ever ("isg_broom_given"), the first time they hold a grimoire from
 * this mod. While riding, the broom draws on your mana (3 a second flying, 1 hovering) and trails your grimoire's
 * colour; with no mana left it can only glide down.
 */
@Mod.EventBusSubscriber(modid = IronSpellGrimoire.MODID)
public final class BroomFlight {
    private BroomFlight() {}

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        if (p.tickCount % 100 == 13 && !p.getPersistentData().getBoolean("isg_broom_given") && holdsGrimoire(p)) give(p);
        if (!(p.getVehicle() instanceof BroomEntity b)) return;
        double sp = b.getDeltaMovement().length();
        if (p.tickCount % 2 == 0 && sp > 0.3) {   // the trail, in the grimoire's colours
            GrimoireMastery.Book bk = GrimoireMastery.bookOf(Utils.getPlayerSpellbookStack(p));
            int pal = bk == null ? Fx.SILVER : bk.palette();
            Vec3 back = b.position().add(0, 0.3, 0).subtract(b.getDeltaMovement().normalize().scale(1.2));
            FxSend.trail(p.serverLevel(), pal | FxPacket.KEEP_SHAPE, back, back.subtract(b.getDeltaMovement().scale(2)), 0.12f, 8);
        }
        if (p.tickCount % 20 == 0 && !p.isCreative()) {
            MagicData md = MagicData.getPlayerMagicData(p);
            float cost = sp > 0.1 ? 3f : 1f;
            if (md.getMana() >= cost) { md.setMana(md.getMana() - cost); b.setNoMana(false); }
            else {
                if (!b.noMana()) p.displayClientMessage(Component.literal("Your broom is out of mana - it can only glide down.").withStyle(ChatFormatting.GRAY), true);
                b.setNoMana(true);
            }
        }
    }

    private static boolean holdsGrimoire(ServerPlayer p) {
        ItemStack s = Utils.getPlayerSpellbookStack(p);
        if (s != null && !s.isEmpty() && IronSpellGrimoire.MODID.equals(BuiltInRegistries.ITEM.getKey(s.getItem()).getNamespace())) return true;
        for (ItemStack i : p.getInventory().items) if (GrimoireMastery.bookOf(i) != null) return true;
        return false;
    }

    private static void give(ServerPlayer p) {
        p.getPersistentData().putBoolean("isg_broom_given", true);
        ItemStack broom = new ItemStack(AddonItemRegistry.MAGIC_BROOM.get());
        if (!p.getInventory().add(broom)) p.drop(broom, false);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 1.4f);
        p.sendSystemMessage(Component.literal("A Magic Broom for a new mage. ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.literal("Use it to fly - it runs on your mana. You only get one, so keep it safe.").withStyle(ChatFormatting.YELLOW)));
    }
}
