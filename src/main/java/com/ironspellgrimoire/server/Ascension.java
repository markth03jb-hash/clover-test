package com.ironspellgrimoire.server;

import com.ironspellgrimoire.IronSpellGrimoire;
import com.ironspellgrimoire.network.AddonNetwork;
import com.ironspellgrimoire.network.FxPacket;
import io.redspace.ironsspellbooks.api.util.Utils;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * Grimoire Ascension - the step after Awakening. An awakened grimoire at mastery 8 can take the Ascension Trial at the
 * altar: a hall in the Rift where its Reflection waits - you, in your own skin, fighting with your grimoire's magic
 * (entity/Reflection, server/ReflectionAI). Beat it and the grimoire ascends ("Ascension" on the book):
 * <ul>
 *   <li>the Ascension plays on your screen (client/AwakeningScene, in gold);</li>
 *   <li>every spell from it hits 15% harder, and its hits flare gold and white;</li>
 *   <li>its evolved spells go further (server/SpellEvolution): +35% instead of +20%, gold flares that reach up, and a
 *       gold echo of every hit;</li>
 *   <li>its floating aura is crowned with a third ring of gold.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = IronSpellGrimoire.MODID)
public final class Ascension {
    private Ascension() {}

    private static final Map<UUID, Long> FLARE = new HashMap<>();

    public static boolean ascended(ItemStack book) { return book != null && book.hasTag() && book.getTag().getBoolean("Ascension"); }

    public static boolean canAscend(ItemStack book) {
        return book != null && Awakening.awakened(book) && !ascended(book) && GrimoireMastery.level(book) >= GrimoireMastery.AWAKENED_MAX;
    }

    /** +15% for every spell of an Ascended grimoire. */
    public static float boost(ServerPlayer p, String spellId) {
        ItemStack book = Utils.getPlayerSpellbookStack(p);
        return ascended(book) && spellId != null ? 1.15f : 1f;
    }

    /** An Ascended grimoire's hits flare gold and white (evolved spells draw their own, bigger flare). */
    public static void afterHit(ServerPlayer p, LivingEntity t, String spellId) {
        ItemStack book = Utils.getPlayerSpellbookStack(p);
        if (!ascended(book) || SpellEvolution.evolved(book, spellId)) return;
        ServerLevel sl = p.serverLevel();
        long now = sl.getGameTime();
        Long last = FLARE.get(t.getUUID());
        if (last != null && now - last < 6) return;
        FLARE.put(t.getUUID(), now);
        if (FLARE.size() > 512) FLARE.clear();
        Vec3 at = t.position().add(0, t.getBbHeight() * 0.55, 0);
        FxSend.ring(sl, Fx.GOLD | FxPacket.KEEP_SHAPE, at, 0.2f, 1.1f, 0.04f, 7);
        LightFx.glint(sl, Fx.SILVER, at, 0.5f, 7);
    }

    /** {@code /grimoire ascend} (the Ascension Trial on the spot) and {@code /grimoire evolve} (every spell of the book evolves) - ops, for testing. */
    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent e) {
        e.getDispatcher().register(net.minecraft.commands.Commands.literal("grimoire").requires(s -> s.hasPermission(2))
                .then(net.minecraft.commands.Commands.literal("ascend").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    ItemStack book = Utils.getPlayerSpellbookStack(p);
                    if (!canAscend(book)) { c.getSource().sendFailure(Component.literal("Needs an awakened grimoire at mastery 8 that hasn't ascended.")); return 0; }
                    Rifts.startAscension(p);
                    return 1;
                }))
                .then(net.minecraft.commands.Commands.literal("evolve").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    ItemStack book = Utils.getPlayerSpellbookStack(p);
                    for (String id : SpellEvolution.spells(book)) SpellEvolution.evolve(p, book, id);
                    return 1;
                })));
    }

    /** The trial is won: the grimoire ascends. */
    public static void ascend(ServerPlayer p) {
        ItemStack book = Utils.getPlayerSpellbookStack(p);
        if (!canAscend(book)) return;
        book.getOrCreateTag().putBoolean("Ascension", true);
        ServerLevel sl = p.serverLevel();
        Vec3 c = p.position();
        FxPacket k = new FxPacket();
        k.shape = Awakening.SHAPE; k.id = p.getId(); k.life = Awakening.SCENE; k.sweep = 1;   // the scene, in gold
        AddonNetwork.send(PacketDistributor.PLAYER.with(() -> p), k);
        AstaFx.later(Awakening.WRITE_AT, () -> {
            if (!p.isAlive()) return;
            FxSend.impactFrame(sl, p.position().add(0, 1.2, 0), 5, FxSend.FRAME_GOLD, 40);
            FxSend.ring(sl, Fx.GOLD | FxPacket.KEEP_SHAPE, p.position().add(0, 0.1, 0), 0.5f, 14f, 0.25f, 22);
            FxSend.beam(sl, Fx.GOLD | FxPacket.KEEP_SHAPE, p.position(), p.position().add(0, 50, 0), 1.6f, 34);
            FxSend.beam(sl, Fx.SILVER | FxPacket.KEEP_SHAPE, p.position(), p.position().add(0, 50, 0), 0.5f, 34);
            sl.playSound(null, p.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0f, 1.2f);
            sl.playSound(null, p.blockPosition(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 1.4f, 1.0f);
        });
        sl.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.4f, 0.6f);
        p.sendSystemMessage(Component.literal("GRIMOIRE ASCENSION  ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.literal(book.getHoverName().getString() + " has ascended.").withStyle(ChatFormatting.YELLOW)));
        p.sendSystemMessage(Component.literal("  Every spell +15%, gold flares; evolved spells +35% and a gold echo.").withStyle(ChatFormatting.GRAY));
        Bounties.addStars(p, 20);
    }
}
