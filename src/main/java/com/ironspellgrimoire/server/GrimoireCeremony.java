package com.ironspellgrimoire.server;

import com.ironspellgrimoire.IronSpellGrimoire;
import com.ironspellgrimoire.network.AddonNetwork;
import com.ironspellgrimoire.network.FxPacket;
import io.redspace.ironsspellbooks.api.spells.IPresetSpellContainer;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Grimoire acquisition - the grimoire tower ceremony. At a Grimoire Altar (at the top of every Grimoire Tower) the
 * grimoires rise and circle above it, faster and faster, until one leaves the ring and comes to you: it has chosen
 * you. Every grimoire has the same chance.
 *
 * In survival each player is chosen once; the grimoire is bound to them (only they can cast from it). After that the
 * altar calls your grimoire back if you no longer carry it (a fresh copy). In creative the ceremony can be repeated.
 */
@Mod.EventBusSubscriber(modid = IronSpellGrimoire.MODID)
public final class GrimoireCeremony {
    private GrimoireCeremony() {}

    public static final int TICKS = 110;          // the whole ceremony
    private static final String KEY = "iron_spell_grimoire_grimoire";   // persisted: index of the grimoire that chose you, +1
    public static final String OWNER = "GrimoireOwner";          // on the stack: who it chose
    private static final Set<UUID> RUNNING = new HashSet<>();

    private static CompoundTag persisted(Player p) {
        CompoundTag all = p.getPersistentData();
        if (!all.contains(Player.PERSISTED_NBT_TAG)) all.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        return all.getCompound(Player.PERSISTED_NBT_TAG);
    }

    /** The grimoire that chose this player (index into GrimoireMastery.BOOKS), or -1. */
    public static int chosen(Player p) { return persisted(p).getInt(KEY) - 1; }

    public static void begin(ServerPlayer p, BlockPos altar) {
        if (RUNNING.contains(p.getUUID())) return;
        boolean creative = p.isCreative();
        int had = chosen(p);
        if (had >= 0 && !creative) {
            if (carries(p, had)) {
                p.displayClientMessage(Component.literal("Your grimoire has already chosen you.").withStyle(ChatFormatting.GRAY), true);
                return;
            }
            // lost it: the altar calls it back
            give(p, had);
            ServerLevel sl = p.serverLevel();
            Vec3 c = Vec3.atCenterOf(altar).add(0, 0.6, 0);
            Fx.flash(sl, c, 1.4f);
            FxSend.ring(sl, GrimoireMastery.BOOKS.get(had).palette(), c, 0.2f, 1.8f, 0.2f, 10);
            sl.playSound(null, altar, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1f, 0.8f);
            p.displayClientMessage(Component.literal("Your grimoire returns to you.").withStyle(ChatFormatting.GOLD), true);
            return;
        }
        int pick = pick(p);
        RUNNING.add(p.getUUID());
        ServerLevel sl = p.serverLevel();
        Vec3 top = Vec3.atCenterOf(altar).add(0, 0.5, 0);
        FxPacket pk = new FxPacket();
        pk.shape = 45; pk.id = p.getId(); pk.c = top; pk.palette = (byte) pick; pk.life = TICKS;
        AddonNetwork.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(top.x, top.y, top.z, 64, sl.dimension())), pk);
        sl.playSound(null, altar, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1f, 0.7f);
        sl.playSound(null, altar, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1f, 0.6f);
        for (int t = 10; t < 66; t += 8) {
            final int tt = t;
            AstaFx.later(t, () -> sl.playSound(null, altar, SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8f, 0.7f + tt * 0.008f));
        }
        AstaFx.later(66, () -> {   // one leaves the ring
            sl.playSound(null, altar, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.2f, 0.8f);
            FxSend.ring(sl, GrimoireMastery.BOOKS.get(pick).palette(), top.add(0, 2.4, 0), 0.2f, 2.6f, 0.15f, 10);
        });
        AstaFx.later(TICKS, () -> {
            RUNNING.remove(p.getUUID());
            if (!p.isAlive() || p.isRemoved()) return;
            give(p, pick);
            persisted(p).putInt(KEY, pick + 1);
            GrimoireLibrary.chosen(p);
            sl.playSound(null, p.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8f, 1f);
            sl.playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1f, 0.8f);
            Fx.flash(sl, p.position().add(0, 1.2, 0), 1.6f);
            Item item = GrimoireMastery.BOOKS.get(pick).item().get();
            p.connection.send(new ClientboundSetTitlesAnimationPacket(8, 50, 14));
            p.connection.send(new ClientboundSetTitleTextPacket(Component.literal("A grimoire has chosen you").withStyle(ChatFormatting.GOLD)));
            p.connection.send(new ClientboundSetSubtitleTextPacket(new ItemStack(item).getHoverName().copy().withStyle(ChatFormatting.WHITE)));
        });
    }

    private static boolean carries(ServerPlayer p, int book) {
        Item item = GrimoireMastery.BOOKS.get(book).item().get();
        ItemStack worn = Utils.getPlayerSpellbookStack(p);
        if (worn != null && worn.is(item)) return true;
        for (ItemStack s : p.getInventory().items) if (s.is(item)) return true;
        return p.getOffhandItem().is(item);
    }

    private static void give(ServerPlayer p, int book) {
        Item item = GrimoireMastery.BOOKS.get(book).item().get();
        ItemStack s = new ItemStack(item);
        if (item instanceof IPresetSpellContainer c) c.initializeSpellContainer(s);
        s.getOrCreateTag().putUUID(OWNER, p.getUUID());
        s.getOrCreateTag().putString(OWNER + "Name", p.getGameProfile().getName());
        if (!p.getInventory().add(s)) p.drop(s, false);
    }

    /** True if this stack was bound to someone else (only its chosen one may cast from it). */
    public static boolean boundToOther(ItemStack s, Player p) {
        if (s == null || !s.hasTag() || !s.getTag().hasUUID(OWNER)) return false;
        return !s.getTag().getUUID(OWNER).equals(p.getUUID());
    }

    // ------------------------------------------------------------------ the choice: every grimoire has the same chance
    private static int pick(ServerPlayer p) {
        return p.getRandom().nextInt(Math.max(1, GrimoireMastery.BOOKS.size()));
    }

    /** Keep the record on respawn (Forge copies the persisted tag, but be sure). */
    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone e) {
        CompoundTag old = e.getOriginal().getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (old.contains(KEY)) persisted(e.getEntity()).putInt(KEY, old.getInt(KEY));
    }
}
