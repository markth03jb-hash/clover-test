package com.example.arcaneaddon.server;

import com.example.arcaneaddon.ArcaneAddon;
import com.example.arcaneaddon.network.AddonNetwork;
import com.example.arcaneaddon.network.FxPacket;
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
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Grimoire acquisition - the grimoire tower ceremony. At a Grimoire Altar the grimoires rise and circle above it,
 * faster and faster, until one leaves the ring and comes to you: it has chosen you. Which one is weighted by how you
 * play (your statistics): fighters draw Asta's, Yami's and Mereoleona's; archers and fliers Yuno's; swimmers Nozel's;
 * fire-starters Magna's; runners and jumpers Luck's; skeleton hunters Zenon's; shield users Klaus's. Asta's five-leaf
 * grimoire is the rarest, and favours those with little magic (a low experience level).
 *
 * In survival each player is chosen once; the grimoire is bound to them (only they can cast from it). After that the
 * altar calls your grimoire back if you no longer carry it (a fresh copy). In creative the ceremony can be repeated.
 */
@Mod.EventBusSubscriber(modid = ArcaneAddon.MODID)
public final class GrimoireCeremony {
    private GrimoireCeremony() {}

    public static final int TICKS = 110;          // the whole ceremony
    private static final String KEY = "arcane_addon_grimoire";   // persisted: index of the grimoire that chose you, +1
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

    // ------------------------------------------------------------------ the choice, weighted by how you play
    private static int stat(ServerPlayer p, Stat<?> s) {
        try { return p.getStats().getValue(s); } catch (Throwable t) { return 0; }
    }

    private static int custom(ServerPlayer p, net.minecraft.resources.ResourceLocation s) { return stat(p, Stats.CUSTOM.get(s)); }

    private static double cap(double v) { return Math.min(30.0, v); }

    private static int pick(ServerPlayer p) {
        int n = GrimoireMastery.BOOKS.size();
        double[] w = new double[n];
        java.util.Arrays.fill(w, 10.0);
        if (n > 0) w[0] = 3.0;   // Asta's five-leaf grimoire is the rarest
        double dealt = custom(p, Stats.DAMAGE_DEALT) / 10.0, kills = custom(p, Stats.MOB_KILLS);
        set(w, 0, cap(dealt / 120.0) + (p.experienceLevel < 5 ? 8 : 0));                                           // Asta: a fighter with no magic
        set(w, 1, cap(kills / 25.0) + cap(stat(p, Stats.ENTITY_KILLED.get(EntityType.ENDERMAN)) / 3.0));              // Yami
        set(w, 2, cap(stat(p, Stats.ITEM_USED.get(Items.BOW)) / 15.0) + cap(custom(p, Stats.AVIATE_ONE_CM) / 20000.0)); // Yuno
        set(w, 3, cap(custom(p, Stats.SWIM_ONE_CM) / 15000.0));                                                       // Nozel
        set(w, 4, cap(stat(p, Stats.ITEM_USED.get(Items.FLINT_AND_STEEL)) * 1.5 + stat(p, Stats.ITEM_USED.get(Items.FIRE_CHARGE)) * 2.0)); // Magna
        set(w, 5, cap(custom(p, Stats.SPRINT_ONE_CM) / 60000.0) + cap(custom(p, Stats.JUMP) / 600.0));                // Luck
        set(w, 6, cap(custom(p, Stats.DAMAGE_TAKEN) / 3000.0) + cap(custom(p, Stats.PLAYER_KILLS) * 3.0));            // Mereoleona
        set(w, 7, cap(stat(p, Stats.ENTITY_KILLED.get(EntityType.SKELETON)) / 4.0));                                  // Zenon
        set(w, 8, cap(custom(p, Stats.DAMAGE_BLOCKED_BY_SHIELD) / 300.0) + cap(stat(p, Stats.ITEM_USED.get(Items.SHIELD)) / 20.0)); // Klaus
        double sum = 0;
        for (double v : w) sum += v;
        double r = p.getRandom().nextDouble() * sum;
        for (int i = 0; i < n; i++) { r -= w[i]; if (r <= 0) return i; }
        return n - 1;
    }

    private static void set(double[] w, int i, double bonus) { if (i < w.length) w[i] += bonus; }

    /** Keep the record on respawn (Forge copies the persisted tag, but be sure). */
    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone e) {
        CompoundTag old = e.getOriginal().getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (old.contains(KEY)) persisted(e.getEntity()).putInt(KEY, old.getInt(KEY));
    }
}
