package com.ironspellgrimoire.server;

import com.ironspellgrimoire.IronSpellGrimoire;
import com.ironspellgrimoire.item.RiftInkItem;
import com.ironspellgrimoire.registry.AddonItemRegistry;
import io.redspace.ironsspellbooks.api.events.ChangeManaEvent;
import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.damage.SpellDamageSource;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * What the rifts give back, and the rifts' own laws on your magic:
 * <ul>
 *   <li>Laws (inside a rift): Mana Burn - a spell's mana comes out of your health instead; Echo - every spell hit lands
 *       again a second later at half strength, and splashes a third on any other player standing by.</li>
 *   <li>Rift Ink, written into a grimoire at the altar (NBT "RiftInk" = rule + 1): Blood (spells cost 25% less mana and
 *       1 health), Feather (no fall damage, lighter fall), Echo (15% of spell hits echo for 40%), Shade (12% of blows
 *       pass through you, 3 s apart).</li>
 *   <li>Relics (charm slot): Cracked Lens, Rift Heart, Hollow Bell, Shade Lantern.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = IronSpellGrimoire.MODID)
public final class RiftRewards {
    private RiftRewards() {}

    private static final Map<UUID, Long> CAST = new HashMap<>(), FREE = new HashMap<>(), HEART = new HashMap<>(), SHADE = new HashMap<>();
    private static final Map<UUID, Integer> COUNT = new HashMap<>();
    private static final UUID FEATHER = UUID.fromString("a1f70000-0000-4000-8000-0000000000d2");
    private static final UUID FROST = UUID.fromString("a1f70000-0000-4000-8000-0000000000d3");

    static boolean wears(Player p, Item item) {
        return CuriosApi.getCuriosInventory(p).resolve().flatMap(h -> h.findFirstCurio(item)).isPresent();
    }

    /** The ink written into the equipped grimoire: 0-3, or -1. */
    /** Whether the equipped grimoire carries this ink, on either of its ink pages. */
    static boolean hasInk(Player p, int rule) {
        ItemStack b = Utils.getPlayerSpellbookStack(p);
        if (b == null || !b.hasTag()) return false;
        CompoundTag t = b.getTag();
        return t.getInt("RiftInk") - 1 == rule || (t.getBoolean("InkSlot2") && t.getInt("RiftInk2") - 1 == rule)
                || (t.getBoolean("InkSlot3") && t.getInt("RiftInk3") - 1 == rule);
    }

    static int ink(Player p) {
        ItemStack b = Utils.getPlayerSpellbookStack(p);
        return b == null || !b.hasTag() ? -1 : b.getTag().getInt("RiftInk") - 1;
    }

    /** At the altar: write the ink into the grimoire you have equipped. */
    public static void inscribe(ServerPlayer p, ItemStack ink) {
        ItemStack book = Utils.getPlayerSpellbookStack(p);
        if (book == null || book.isEmpty() || GrimoireMastery.bookOf(book) == null) {
            p.displayClientMessage(Component.literal("Equip a grimoire to write the ink into.").withStyle(ChatFormatting.RED), true);
            return;
        }
        int r = RiftInkItem.rule(ink);
        CompoundTag bt = book.getOrCreateTag();
        if (bt.getInt("RiftInk") == r + 1 || bt.getInt("RiftInk2") == r + 1 && bt.getBoolean("InkSlot2") || bt.getInt("RiftInk3") == r + 1 && bt.getBoolean("InkSlot3")) {
            p.displayClientMessage(Component.literal("Your grimoire already carries " + RiftInkItem.NAMES[r] + ".").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        // the first empty page takes it; with every page full, the last page is written over
        if (bt.getInt("RiftInk") == 0) bt.putInt("RiftInk", r + 1);
        else if (bt.getBoolean("InkSlot2") && bt.getInt("RiftInk2") == 0) bt.putInt("RiftInk2", r + 1);
        else if (bt.getBoolean("InkSlot3") && bt.getInt("RiftInk3") == 0) bt.putInt("RiftInk3", r + 1);
        else if (bt.getBoolean("InkSlot3")) bt.putInt("RiftInk3", r + 1);
        else if (bt.getBoolean("InkSlot2")) bt.putInt("RiftInk2", r + 1);
        else bt.putInt("RiftInk", r + 1);
        if (!p.isCreative()) ink.shrink(1);
        ServerLevel sl = p.serverLevel();
        FxSend.ring(sl, Fx.VOID, p.position().add(0, 0.1, 0), 0.3f, 3f, 0.1f, 14);
        FxLib.halo(sl, Fx.VOID, p.position().add(0, p.getBbHeight() + 0.4, 0), 0.8f);
        sl.playSound(null, p.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1f, 0.6f);
        sl.playSound(null, p.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1f, 0.8f);
        p.displayClientMessage(Component.literal(RiftInkItem.NAMES[r] + " written into your grimoire.").withStyle(s -> s.withColor(RiftInkItem.COLORS[r])), true);
    }

    // ------------------------------------------------------------------ mana: Mana Burn, Blood Ink, the Hollow Bell
    @SubscribeEvent
    public static void onCast(SpellOnCastEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        long now = p.level().getGameTime();
        CAST.put(p.getUUID(), now);
        if (wears(p, AddonItemRegistry.HOLLOW_BELL.get())) {
            int n = COUNT.merge(p.getUUID(), 1, Integer::sum);
            if (n % 10 == 0) {
                FREE.put(p.getUUID(), now);
                p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 0.8f, 1.6f);
                p.displayClientMessage(Component.literal("The Hollow Bell rings - that one was free.").withStyle(ChatFormatting.GOLD), true);
            }
        }
    }

    @SubscribeEvent
    public static void onMana(ChangeManaEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || e.getNewMana() >= e.getOldMana()) return;
        long now = p.level().getGameTime();
        Long c = CAST.get(p.getUUID());
        if (c == null || now - c > 1) return;   // only the mana a spell just spent
        float cost = e.getOldMana() - e.getNewMana();
        Long free = FREE.get(p.getUUID());
        if (free != null && now - free <= 1) { e.setNewMana(e.getOldMana()); FREE.remove(p.getUUID()); return; }
        if (Rifts.inRule(p, Rifts.MANA_BURN)) {
            e.setNewMana(e.getOldMana());
            p.hurt(p.damageSources().magic(), Math.max(1f, cost * 0.05f));
            return;
        }
        if (hasInk(p, 0)) {
            e.setNewMana(e.getOldMana() - cost * 0.75f);
            p.hurt(p.damageSources().magic(), 1f);
            cost *= 0.75f;
        }
        if (hasInk(p, 10)) e.setNewMana(e.getOldMana() - cost * 0.85f);   // Elven Ink
    }

    // ------------------------------------------------------------------ spell hits: Echo, the Cracked Lens; blows taken: the Rift Heart
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent e) {
        LivingEntity t = e.getEntity();
        if (t.level().isClientSide) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        if (e.getSource() instanceof SpellDamageSource && e.getSource().getEntity() instanceof ServerPlayer p && t != p) {
            ServerLevel sl = p.serverLevel();
            if (wears(p, AddonItemRegistry.CRACKED_LENS.get()) && r.nextFloat() < 0.15f) {
                e.setAmount(e.getAmount() * 1.5f);
                FxSend.ring(sl, Fx.SILVER, FxUtil.mid(t), 0.2f, 1.4f, 0.06f, 8);
                sl.playSound(null, t.blockPosition(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 1f, 1.6f);
            }
            if (hasInk(p, 6) && sl.isNight()) e.setAmount(e.getAmount() * 1.15f);   // Moon Ink
            if (hasInk(p, 5)) TimedModifiers.apply(t, net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED, FROST, "Frost Ink", -0.25, AttributeModifier.Operation.MULTIPLY_TOTAL, 40);
            if (hasInk(p, 7) && p.getHealth() < p.getMaxHealth()) p.heal(e.getAmount() * 0.03f);   // Vital Ink
            if (hasInk(p, 4) && r.nextFloat() < 0.2f) {   // Storm Ink: the hit arcs to someone else nearby
                LivingEntity next = null; double best = 36;
                for (LivingEntity o : sl.getEntitiesOfClass(LivingEntity.class, t.getBoundingBox().inflate(6), o -> o != t && o != p && o.isAlive() && o instanceof net.minecraft.world.entity.monster.Enemy)) {
                    double d = o.distanceToSqr(t);
                    if (d < best) { best = d; next = o; }
                }
                if (next != null) {
                    final LivingEntity n2 = next;
                    final float amt = e.getAmount() * 0.3f;
                    FxSend.beam(sl, Fx.LIGHTNING | com.ironspellgrimoire.network.FxPacket.KEEP_SHAPE, FxUtil.mid(t), FxUtil.mid(n2), 0.12f, 6);
                    AstaFx.later(2, () -> { if (n2.isAlive()) { n2.invulnerableTime = 0; n2.hurt(p.damageSources().indirectMagic(p, p), amt); } });
                }
            }
            boolean rule = Rifts.inRule(p, Rifts.ECHO);
            boolean inked = hasInk(p, 2) && r.nextFloat() < 0.15f;
            if (rule || inked) {
                float amt = e.getAmount() * (rule ? 0.5f : 0.4f);
                AstaFx.later(20, () -> {
                    if (!t.isAlive()) return;
                    t.invulnerableTime = 0;
                    t.hurt(p.damageSources().indirectMagic(p, p), amt);
                    FxSend.ring(sl, Fx.VOID, FxUtil.mid(t), 0.2f, 1.6f, 0.06f, 8);
                    sl.playSound(null, t.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.8f, 0.8f);
                    if (rule) for (Player o : sl.getEntitiesOfClass(Player.class, t.getBoundingBox().inflate(3), o -> o != p && o != t && o.isAlive()))
                        o.hurt(o.damageSources().indirectMagic(p, p), amt * 0.6f);   // the echo doesn't care whose side you're on
                });
            }
        }
        if (t instanceof ServerPlayer v && e.getAmount() >= v.getMaxHealth() / 3f && wears(v, AddonItemRegistry.RIFT_HEART.get())) {
            long now = v.level().getGameTime();
            Long ready = HEART.get(v.getUUID());
            if (ready == null || now >= ready) {
                HEART.put(v.getUUID(), now + 1200);
                ServerLevel sl = v.serverLevel();
                Vec3 away = e.getSource().getEntity() != null ? v.position().subtract(e.getSource().getEntity().position()) : v.getLookAngle().scale(-1);
                away = new Vec3(away.x, 0, away.z);
                if (away.lengthSqr() < 1e-4) away = new Vec3(1, 0, 0);
                Vec3 dest = SpaceFx.safe(sl, v.position().add(away.normalize().scale(6)), v);
                if (dest != null) {
                    RiftFx.cut(sl, v.position().add(0, 1, 0), new Vec3(0, 1, 0), away.normalize(), 1.2f, 12);
                    SpaceFx.move(v, dest, v.getYRot());
                    RiftFx.cut(sl, dest.add(0, 1, 0), new Vec3(0, 1, 0), away.normalize(), 1.2f, 12);
                    sl.playSound(null, v.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1f, 1.4f);
                    v.displayClientMessage(Component.literal("The Rift Heart tears you clear!").withStyle(ChatFormatting.LIGHT_PURPLE), true);
                }
            }
        }
    }

    /** Thorn Ink: what strikes you up close is hurt back. */
    @SubscribeEvent
    public static void onThorn(LivingDamageEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer v) || !hasInk(v, 8) || !(e.getSource().getDirectEntity() instanceof LivingEntity by) || by == v) return;
        if (by.distanceToSqr(v) > 16) return;
        by.hurt(v.damageSources().thorns(v), Math.max(1f, e.getAmount() * 0.2f));
    }

    /** Gold Ink: the fallen sometimes leave an emerald. */
    @SubscribeEvent
    public static void onGold(net.minecraftforge.event.entity.living.LivingDeathEvent e) {
        if (!(e.getSource().getEntity() instanceof ServerPlayer p) || !hasInk(p, 9) || !(e.getEntity() instanceof net.minecraft.world.entity.monster.Enemy)) return;
        if (ThreadLocalRandom.current().nextInt(5) == 0) e.getEntity().spawnAtLocation(new ItemStack(net.minecraft.world.item.Items.EMERALD));
    }

    /** Shade Ink: now and then a blow passes straight through you. */
    @SubscribeEvent
    public static void onAttack(LivingAttackEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer v) || !hasInk(v, 3)) return;
        long now = v.level().getGameTime();
        Long ready = SHADE.get(v.getUUID());
        if ((ready != null && now < ready) || ThreadLocalRandom.current().nextFloat() >= 0.12f) return;
        if (e.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        SHADE.put(v.getUUID(), now + 60);
        e.setCanceled(true);
        Kit.orb(v.serverLevel(), Fx.DARK, v.position().add(0, 1, 0), new Vec3(0, 0.05, 0), 0.5f, 8);
        v.serverLevel().playSound(null, v.blockPosition(), SoundEvents.PHANTOM_FLAP, SoundSource.PLAYERS, 0.8f, 1.4f);
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && hasInk(p, 1)) e.setDamageMultiplier(0f);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % 20 != 0) return;
        if (hasInk(p, 1)) TimedModifiers.apply(p, ForgeMod.ENTITY_GRAVITY.get(), FEATHER, "Feather Ink", -0.15, AttributeModifier.Operation.MULTIPLY_TOTAL, 30);
        if (p.tickCount % 80 == 0 && p.getHealth() < p.getMaxHealth() && wears(p, AddonItemRegistry.WORLD_TREE_LEAF.get())) p.heal(1f);   // the World Tree Leaf
        if (p.getHealth() < p.getMaxHealth() * 0.5f && wears(p, AddonItemRegistry.SHADE_LANTERN.get()))
            for (LivingEntity t : Kit.near(p.serverLevel(), p, p.position().add(0, 1, 0), 6)) Moves.slow(t, 30, -0.25);
    }
}
