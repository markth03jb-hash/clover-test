package com.ironspellgrimoire.server;

import com.ironspellgrimoire.IronSpellGrimoire;
import com.ironspellgrimoire.entity.FamiliarEntity;
import com.ironspellgrimoire.network.FxPacket;
import com.ironspellgrimoire.registry.AddonEntities;
import com.ironspellgrimoire.registry.AddonItemRegistry;
import io.redspace.ironsspellbooks.api.util.Utils;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Mana Familiars. Spend a Mana Core at the Grimoire Altar (right-click it holding the core) and a familiar hatches -
 * one per mage, for good ("isg_familiar"). It takes the element of the grimoire you have equipped, and changes when
 * you change books; it grows with that grimoire's mastery (stage 2 at mastery 4, stage 3 at 7: bigger, quicker, a
 * second effect, and a name of its own). In a fight it helps every few seconds:
 * <pre>
 *  Salamander (fire books)   a firebolt                 Wind Sprite (wind)       a gust: damage, and a shove
 *  Light Wisp (light)        heals you                  Shadow Moth (dark, black) a dark bolt that weakens
 *  Pebble Golem (earth)      a hurled stone             Quicksilver Drop (mercury, steel, water)  a silver bolt
 *  Spark Imp (lightning)     a bolt that jumps on       Clockwork Moth (time)    a bolt that slows
 *  Crystal Shard (mirror, rift, space, slash, thread)   Bone Wisp (bone)         a bone dart
 * </pre>
 * Sneak + right-click it to send it home; {@code /familiar} calls it back (or sends it home).
 */
@Mod.EventBusSubscriber(modid = IronSpellGrimoire.MODID)
public final class Familiars {
    private Familiars() {}

    public static final String[] NAMES = {"Salamander", "Wind Sprite", "Light Wisp", "Shadow Moth", "Pebble Golem", "Quicksilver Drop", "Spark Imp", "Clockwork Moth", "Crystal Shard", "Bone Wisp"};
    /** Each kind's colours (Fx palettes) for its bolts. */
    private static final int[] PAL = {Fx.FIRE, Fx.WIND, Fx.GOLD, Fx.YAMI, Fx.EARTH, Fx.SILVER, Fx.LIGHTNING, Fx.TIME, Fx.MIRROR, Fx.SILVER};
    /** Book (GrimoireMastery.BOOKS order) -> kind. */
    private static final int[] BOOK_KIND = {3, 3, 1, 5, 0, 6, 0, 9, 5, 8, 8, 5, 8, 8, 0, 0, 8, 4, 7, 2};
    private static final UUID WEAK = UUID.fromString("f00a0001-0000-4000-8000-0000000000a1");
    private static final UUID SLOW = UUID.fromString("f00a0001-0000-4000-8000-0000000000a2");
    private static final String HAS = "isg_familiar", HOME = "isg_familiar_home";

    /** Each player's familiar while it's out. */
    private static final Map<UUID, Integer> OUT = new HashMap<>();

    public static boolean has(ServerPlayer p) { return p.getPersistentData().getBoolean(HAS); }

    /** At the altar, holding a Mana Core. */
    public static boolean hatch(ServerPlayer p, ItemStack core) {
        if (has(p)) return false;
        ItemStack book = Utils.getPlayerSpellbookStack(p);
        if (book == null || GrimoireMastery.bookOf(book) == null) {
            p.displayClientMessage(Component.literal("Equip a grimoire - the familiar takes its element.").withStyle(ChatFormatting.GRAY), true);
            return true;
        }
        p.getPersistentData().putBoolean(HAS, true);
        p.getPersistentData().putBoolean(HOME, false);
        if (!p.isCreative()) core.shrink(1);
        ServerLevel sl = p.serverLevel();
        Vec3 c = p.position().add(0, 1.2, 0);
        FxSend.ring(sl, Fx.GOLD | FxPacket.KEEP_SHAPE, c, 0.2f, 2.5f, 0.08f, 16);
        FxSend.impactFrame(sl, c, 2, FxSend.FRAME_GOLD, 16);
        sl.playSound(null, p.blockPosition(), SoundEvents.SNIFFER_EGG_HATCH, SoundSource.PLAYERS, 1f, 1.3f);
        sl.playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1f, 1.4f);
        int kind = kindFor(p);
        p.sendSystemMessage(Component.literal("A FAMILIAR HATCHES  ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.literal("The Mana Core cracks open: a " + NAMES[kind] + ", in your grimoire's element. It changes with your book and grows with its mastery.").withStyle(ChatFormatting.YELLOW)));
        p.sendSystemMessage(Component.literal("  Sneak + right-click it to send it home; /familiar calls it back.").withStyle(ChatFormatting.GRAY));
        return true;
    }

    static int kindFor(ServerPlayer p) {
        GrimoireMastery.Book b = GrimoireMastery.bookOf(Utils.getPlayerSpellbookStack(p));
        int i = b == null ? -1 : GrimoireMastery.BOOKS.indexOf(b);
        return i >= 0 && i < BOOK_KIND.length ? BOOK_KIND[i] : 8;
    }

    static int stageFor(ServerPlayer p) {
        ItemStack book = Utils.getPlayerSpellbookStack(p);
        if (book == null || GrimoireMastery.bookOf(book) == null) return 1;
        int l = GrimoireMastery.level(book);
        return l >= 7 ? 3 : l >= 4 ? 2 : 1;
    }

    public static void sendHome(ServerPlayer p) {
        p.getPersistentData().putBoolean(HOME, true);
        FamiliarEntity f = find(p);
        if (f != null) { FxSend.ring(p.serverLevel(), Fx.GOLD | FxPacket.KEEP_SHAPE, f.position(), 0.6f, 0.1f, 0.05f, 10); f.discard(); }
        OUT.remove(p.getUUID());
        p.displayClientMessage(Component.literal("Your familiar goes home. /familiar calls it back.").withStyle(ChatFormatting.GRAY), true);
    }

    private static FamiliarEntity find(ServerPlayer p) {
        Integer id = OUT.get(p.getUUID());
        return id != null && p.serverLevel().getEntity(id) instanceof FamiliarEntity f && f.isAlive() ? f : null;
    }

    // ------------------------------------------------------------------ keeping it by you, and its help in a fight
    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || !has(p)) return;
        if (p.getPersistentData().getBoolean(HOME) || p.isSpectator() || !p.isAlive()) { if (p.isSpectator() || !p.isAlive()) { FamiliarEntity f = find(p); if (f != null) f.discard(); OUT.remove(p.getUUID()); } return; }
        FamiliarEntity f = find(p);
        if (f == null) {   // called (back): it appears at your shoulder
            if (p.tickCount % 20 != 0) return;
            f = AddonEntities.FAMILIAR.get().create(p.serverLevel());
            if (f == null) return;
            f.setOwner(p.getId());
            f.moveTo(p.getX(), p.getY() + p.getBbHeight() + 0.2, p.getZ(), p.getYRot(), 0);
            p.serverLevel().addFreshEntity(f);
            OUT.put(p.getUUID(), f.getId());
            FxSend.ring(p.serverLevel(), Fx.GOLD | FxPacket.KEEP_SHAPE, f.position(), 0.1f, 0.8f, 0.04f, 10);
        }
        if (p.tickCount % 20 == 0) {   // its element follows your book, its stage your mastery
            int kind = kindFor(p), stage = stageFor(p);
            if (f.kind() != kind || f.stage() != stage) {
                boolean grew = stage > f.stage() && f.kind() == kind;
                f.setKind(kind); f.setStage(stage);
                FxSend.ring(p.serverLevel(), PAL[kind] | FxPacket.KEEP_SHAPE, f.position(), 0.2f, 1.6f, 0.06f, 12);
                if (grew) p.displayClientMessage(Component.literal("Your " + NAMES[kind] + " grows stronger! (stage " + stage + ")").withStyle(ChatFormatting.GOLD), true);
            }
            if (stage >= 2) {
                f.setCustomName(Component.literal(p.getGameProfile().getName() + "'s " + NAMES[kind]).withStyle(ChatFormatting.GRAY));
                f.setCustomNameVisible(false);
            }
        }
        int every = f.stage() == 3 ? 100 : f.stage() == 2 ? 130 : 160;
        if (p.tickCount % every == 0) assist(p, f);
    }

    /** Its help: at whatever you last struck or whatever last struck you (a Light Wisp mends you instead). */
    private static void assist(ServerPlayer p, FamiliarEntity f) {
        ServerLevel sl = p.serverLevel();
        int kind = f.kind(), stage = f.stage();
        if (kind == 2) {   // the Light Wisp heals
            if (p.getHealth() >= p.getMaxHealth() * 0.9f) return;
            p.heal(2f + stage);
            FxSend.beam(sl, Fx.GOLD | FxPacket.KEEP_SHAPE, f.position(), p.position().add(0, 1, 0), 0.12f, 8);
            FxSend.ring(sl, Fx.GOLD | FxPacket.KEEP_SHAPE, p.position().add(0, 0.1, 0), 0.2f, 1.4f, 0.05f, 10);
            f.markCast();
            sl.playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.6f, 1.8f);
            return;
        }
        LivingEntity t = null;
        if (p.getLastHurtMob() != null && p.tickCount - p.getLastHurtMobTimestamp() < 120) t = p.getLastHurtMob();
        if ((t == null || !t.isAlive()) && p.getLastHurtByMob() != null) t = p.getLastHurtByMob();
        if (t == null || !t.isAlive() || t.distanceToSqr(p) > 20 * 20 || !(t instanceof Enemy || t == p.getLastHurtByMob())) return;
        final LivingEntity tgt = t;
        int pal = PAL[kind];
        Vec3 from = f.position(), to = t.position().add(0, t.getBbHeight() * 0.55, 0);
        FxSend.beam(sl, pal | FxPacket.KEEP_SHAPE, from, to, 0.14f + stage * 0.04f, 8);
        FxSend.ring(sl, pal | FxPacket.KEEP_SHAPE, to, 0.2f, 0.9f + stage * 0.3f, 0.05f, 10);
        f.markCast();
        float dmg = 3f + stage * 2f;
        t.hurt(p.damageSources().indirectMagic(f, p), dmg);
        sl.playSound(null, f.blockPosition(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 0.5f, 1.8f);
        // each kind's own touch (from stage 2 on, Wind and Lightning and Dark and Time get theirs at once; others at 3)
        boolean extra = stage >= 3 || (stage >= 2 && (kind == 1 || kind == 3 || kind == 6 || kind == 7));
        if (!extra) return;
        switch (kind) {
            case 1 -> { Vec3 away = t.position().subtract(p.position()); if (away.lengthSqr() > 1e-4) { t.setDeltaMovement(t.getDeltaMovement().add(away.normalize().scale(0.8).add(0, 0.3, 0))); t.hurtMarked = true; } }
            case 3 -> TimedModifiers.apply(t, Attributes.ATTACK_DAMAGE, WEAK, "Shadow Moth", -0.2, AttributeModifier.Operation.MULTIPLY_TOTAL, 80);
            case 6 -> {   // the spark jumps on
                for (LivingEntity o : sl.getEntitiesOfClass(LivingEntity.class, t.getBoundingBox().inflate(5), x -> x != tgt && x != p && x.isAlive() && x instanceof Enemy)) {
                    FxSend.beam(sl, pal | FxPacket.KEEP_SHAPE, to, o.position().add(0, o.getBbHeight() * 0.5, 0), 0.1f, 6);
                    o.hurt(p.damageSources().indirectMagic(f, p), dmg * 0.6f);
                    break;
                }
            }
            case 7 -> TimedModifiers.apply(t, Attributes.MOVEMENT_SPEED, SLOW, "Clockwork Moth", -0.35, AttributeModifier.Operation.MULTIPLY_TOTAL, 60);
            default -> t.hurt(p.damageSources().indirectMagic(f, p), dmg * 0.5f);   // a second strike
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) { FamiliarEntity f = find(p); if (f != null) f.discard(); OUT.remove(p.getUUID()); }
    }

    @SubscribeEvent
    public static void onDim(PlayerEvent.PlayerChangedDimensionEvent e) { OUT.remove(e.getEntity().getUUID()); }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("familiar").executes(c -> {
            ServerPlayer p = c.getSource().getPlayerOrException();
            if (!has(p)) { c.getSource().sendFailure(Component.literal("You have no familiar - hatch one from a Mana Core at a Grimoire Altar.")); return 0; }
            if (p.getPersistentData().getBoolean(HOME)) {
                p.getPersistentData().putBoolean(HOME, false);
                p.displayClientMessage(Component.literal("Your familiar comes back to you.").withStyle(ChatFormatting.GOLD), true);
            } else sendHome(p);
            return 1;
        }));
    }
}
