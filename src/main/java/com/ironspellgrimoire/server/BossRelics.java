package com.ironspellgrimoire.server;

import com.ironspellgrimoire.IronSpellGrimoire;
import com.ironspellgrimoire.entity.ShadowSoldier;
import com.ironspellgrimoire.item.SignatureItem;
import com.ironspellgrimoire.registry.AddonEntities;
import com.ironspellgrimoire.registry.AddonItemRegistry;
import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.util.Utils;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.theillusivec4.curios.api.CuriosApi;

/** What the bounty bosses' signature drops do (item/SignatureItem; the list in AddonItemRegistry.SIGNATURES). */
@Mod.EventBusSubscriber(modid = IronSpellGrimoire.MODID)
public final class BossRelics {
    private BossRelics() {}

    static final Map<UUID, Long> KNOT = new HashMap<>(), LAST_HIT = new HashMap<>(), SEAL = new HashMap<>();
    static final Map<UUID, String> LAST_SPELL = new HashMap<>();
    static final Map<UUID, Integer> RUN = new HashMap<>();

    /** The charm worn, or empty. */
    static ItemStack worn(ServerPlayer p, Item item) {
        return CuriosApi.getCuriosInventory(p).resolve().flatMap(h -> h.findFirstCurio(item)).map(r -> r.stack()).orElse(ItemStack.EMPTY);
    }

    /** What's written in a grimoire: 0 nothing, 1 the inscription, 2 the Hardened one. */
    public static int written(ItemStack book, String key) {
        return book == null || !book.hasTag() ? 0 : book.getTag().getCompound("Signatures").getInt(key);
    }

    // ------------------------------------------------------------------ the inscriptions
    /** Writes an inscription into the equipped grimoire; says why not if it can't. */
    public static boolean inscribe(ServerPlayer p, ItemStack s, SignatureItem it) {
        ItemStack book = Utils.getPlayerSpellbookStack(p);
        if (book == null || book.isEmpty() || GrimoireMastery.bookOf(book) == null) { no(p, "Equip a grimoire to write it in."); return false; }
        if ("LichPage".equals(it.key) && BookTree.kind(book) < 0) { no(p, "Only a grimoire with its own tree can take Licht's page."); return false; }
        if ("UnboundSeal".equals(it.key) && !Ascension.ascended(book)) { no(p, "Only an Ascended grimoire can hold the Unbound Seal."); return false; }
        int lvl = SignatureItem.hardened(s) ? 2 : 1;
        if (written(book, it.key) >= lvl) { no(p, "Your grimoire already carries it."); return false; }
        CompoundTag sig = book.getOrCreateTag().getCompound("Signatures");
        sig.putInt(it.key, lvl);
        book.getOrCreateTag().put("Signatures", sig);
        ServerLevel sl = p.serverLevel();
        FxSend.ring(sl, Fx.GOLD, p.position().add(0, 1, 0), 1.4f, 0.2f, 0.08f, 14);
        FxSend.impactFrame(sl, p.position().add(0, 1.2, 0), 2, FxSend.FRAME_GOLD, 10);
        sl.playSound(null, p.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1f, 0.7f);
        p.displayClientMessage(Component.literal(s.getHoverName().getString() + " is written into " + book.getHoverName().getString() + ".").withStyle(ChatFormatting.GOLD), true);
        if ("LichPage".equals(it.key)) BookTree.sync(p, false);
        return true;
    }
    static void no(ServerPlayer p, String why) { p.displayClientMessage(Component.literal(why).withStyle(ChatFormatting.RED), true); }

    /** Licht's Page: tree points it adds. */
    public static int treePoints(ItemStack book) { int w = written(book, "LichPage"); return w == 2 ? 5 : w == 1 ? 3 : 0; }

    /** The Hexbound Ink, and the Unbound Seal's second charge. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onCast(SpellOnCastEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        ItemStack book = Utils.getPlayerSpellbookStack(p);
        String id = e.getSpellId();
        int hex = written(book, "Hexbound");
        if (hex > 0) {
            int run = id.equals(LAST_SPELL.get(p.getUUID())) ? RUN.getOrDefault(p.getUUID(), 1) + 1 : 1;
            LAST_SPELL.put(p.getUUID(), id);
            RUN.put(p.getUUID(), run);
            if (run % 3 == 0) e.setManaCost(0);
            else if (hex == 2 && run % 3 == 1 && run > 1) e.setManaCost(e.getManaCost() / 2);
        }
        int seal = written(book, "UnboundSeal");
        if (seal > 0 && Ascension.ascended(book) && GrimoireMastery.isUltimateId(id)) {
            long now = p.serverLevel().getGameTime(), gap = seal == 2 ? 2400 : 3600;
            Long used = SEAL.get(p.getUUID());
            if (used == null || now - used >= gap) {
                SEAL.put(p.getUUID(), now);
                AstaFx.later(2, () -> {   // once it's cast, its cooldown is lifted: the second charge
                    MagicData md = MagicData.getPlayerMagicData(p);
                    if (md.getPlayerCooldowns().removeCooldown(id)) md.getPlayerCooldowns().syncToPlayer(p);
                    p.displayClientMessage(Component.literal("The Unbound Seal holds a second charge.").withStyle(ChatFormatting.GOLD), true);
                });
            }
        }
    }

    // ------------------------------------------------------------------ the charms
    /** The Grave Knot: a killing blow leaves you at 1 health. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onDeath(LivingDeathEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            ItemStack knot = worn(p, AddonItemRegistry.GRAVE_KNOT.get());
            if (knot.isEmpty()) return;
            boolean hard = SignatureItem.hardened(knot);
            long now = p.serverLevel().getGameTime();
            Long at = KNOT.get(p.getUUID());
            if (at != null && now - at < (hard ? 3600 : 6000) && now >= at) return;
            KNOT.put(p.getUUID(), now);
            e.setCanceled(true);
            p.setHealth(1f);
            MagicData md = MagicData.getPlayerMagicData(p);
            float max = (float) p.getAttributeValue(io.redspace.ironsspellbooks.api.registry.AttributeRegistry.MAX_MANA.get());
            md.setMana(Math.min(max, md.getMana() + max * (hard ? 0.5f : 0.3f)));
            p.invulnerableTime = 20;
            ServerLevel sl = p.serverLevel();
            FxSend.ring(sl, Fx.DARK, p.position().add(0, 0.1, 0), 0.3f, 3f, 0.1f, 14);
            sl.playSound(null, p.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.6f, 0.6f);
            p.displayClientMessage(Component.literal("The Grave Knot holds - not yet.").withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.BOLD), true);
            return;
        }
        // the Turncoat Sigil: a fallen elite rises for you
        if (!(e.getEntity() instanceof Mob m) || !EliteMobs.elite(m) || EliteMobs.ghost(m) || !(e.getSource().getEntity() instanceof ServerPlayer p)) return;
        ItemStack sig = worn(p, AddonItemRegistry.TURNCOAT_SIGIL.get());
        if (sig.isEmpty()) return;
        boolean hard = SignatureItem.hardened(sig);
        if (ThreadLocalRandom.current().nextFloat() >= (hard ? 0.35f : 0.2f)) return;
        turn(p, m, hard ? 600 : 400);
    }

    static void turn(ServerPlayer p, Mob m, int ticks) {
        ServerLevel sl = p.serverLevel();
        ShadowSoldier s = AddonEntities.SHADOW_SOLDIER.get().create(sl);
        if (s == null) return;
        var key = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(m.getType());
        s.setup(p.getUUID(), key == null ? "minecraft:zombie" : key.toString(), -1, 3 + EliteMobs.rank(m), 1, "Turncoat " + m.getType().getDescription().getString(), ticks);
        s.moveTo(m.getX(), m.getY(), m.getZ(), m.getYRot(), 0);
        sl.addFreshEntity(s);
        ShadowFx.risePool(sl, m.position(), s.getBbWidth());
        p.displayClientMessage(Component.literal("It turns its coat - and fights for you.").withStyle(ChatFormatting.LIGHT_PURPLE), true);
        testTurned++;
    }

    /** The Marrow Plate and the Hopeless Claw. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onHurt(LivingHurtEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && e.getAmount() > 0) {
            ItemStack plate = worn(p, AddonItemRegistry.MARROW_PLATE.get());
            long now = p.serverLevel().getGameTime();
            Long last = LAST_HIT.put(p.getUUID(), now);
            if (!plate.isEmpty() && (last == null || now - last > 200 || now < last)) {
                e.setAmount(e.getAmount() * (SignatureItem.hardened(plate) ? 0.2f : 0.4f));
                FxSend.ring(p.serverLevel(), Fx.SILVER, p.position().add(0, 1, 0), 0.6f, 1.2f, 0.05f, 8);
            }
        }
        if (e.getSource().getEntity() instanceof ServerPlayer a && e.getEntity().getHealth() < e.getEntity().getMaxHealth() * 0.3f) {
            ItemStack fang = worn(a, AddonItemRegistry.HOPELESS_CLAW.get());
            if (!fang.isEmpty()) e.setAmount(e.getAmount() * (SignatureItem.hardened(fang) ? 1.4f : 1.25f));
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        UUID id = e.getEntity().getUUID();
        LAST_HIT.remove(id); LAST_SPELL.remove(id); RUN.remove(id);
    }

    public static int testTurned;
    public static void testForget(ServerPlayer p) { KNOT.remove(p.getUUID()); LAST_HIT.remove(p.getUUID()); SEAL.remove(p.getUUID()); LAST_SPELL.remove(p.getUUID()); RUN.remove(p.getUUID()); }
    public static void testTurn(ServerPlayer p, Mob m) { turn(p, m, 100); }
}
