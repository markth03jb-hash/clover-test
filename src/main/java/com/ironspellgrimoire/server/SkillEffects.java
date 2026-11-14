package com.ironspellgrimoire.server;

import com.ironspellgrimoire.IronSpellGrimoire;
import com.ironspellgrimoire.network.FxPacket;
import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.damage.SpellDamageSource;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * What the Mana Tree (server/SkillTree) does in a fight: its stat nodes are curio modifiers on the book; everything
 * else - and every grimoire's crown - is here. A spell hit from a mage is shaped by their equipped grimoire's tree;
 * the extra strikes it adds (lingering, leaps, echoes, bursts) are plain magic, so they don't feed back into it.
 */
@Mod.EventBusSubscriber(modid = IronSpellGrimoire.MODID)
public final class SkillEffects {
    private SkillEffects() {}

    private static final UUID HASTE = UUID.fromString("5e1d7701-0000-4000-8000-000000000001"), SLOW = UUID.fromString("5e1d7701-0000-4000-8000-000000000002"),
            WEAK = UUID.fromString("5e1d7701-0000-4000-8000-000000000003"), JOY = UUID.fromString("5e1d7701-0000-4000-8000-000000000004"),
            MARROW = UUID.fromString("5e1d7701-0000-4000-8000-000000000005"), LIGHT = UUID.fromString("5e1d7701-0000-4000-8000-000000000006");
    /** Per-mage fight state (hit counts, stacks, last target, cooldowns). */
    private static final Map<UUID, CompoundTag> STATE = new HashMap<>();
    /** Each mage's position and health over the last few seconds (Rewind). */
    private static final Map<UUID, double[][]> PAST = new HashMap<>();
    private static final Map<UUID, Long> BURNING = new HashMap<>();

    private static CompoundTag st(ServerPlayer p) { return STATE.computeIfAbsent(p.getUUID(), k -> new CompoundTag()); }
    private static long now(ServerPlayer p) { return p.serverLevel().getGameTime(); }

    private static ItemStack treeBook(ServerPlayer p) {
        ItemStack b = Utils.getPlayerSpellbookStack(p);
        return b != null && b.hasTag() && b.getTag().contains("Tree") ? b : null;
    }

    private static int crownOf(ItemStack book) { return book != null && SkillTree.crown(book) ? SkillTree.bookIndex(book) : -1; }

    private static boolean boss(LivingEntity t) { return TimeStop.isBoss(t) || BossCores.isBoss(t.getType()); }

    private static void extra(ServerPlayer p, LivingEntity t, float dmg) {
        if (t.isAlive() && dmg > 0.05f) { t.invulnerableTime = 0; t.hurt(p.damageSources().indirectMagic(p, p), dmg); }
    }

    // ------------------------------------------------------------------ your spell hits
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onHit(LivingHurtEvent e) {
        if (!(e.getSource() instanceof SpellDamageSource) || !(e.getSource().getEntity() instanceof ServerPlayer p) || e.getEntity() == p) return;
        ItemStack book = treeBook(p);
        if (book == null) return;
        LivingEntity t = e.getEntity();
        ServerLevel sl = p.serverLevel();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        CompoundTag s = st(p);
        long now = now(p);
        float add = 0;
        if (boss(t)) add += SkillTree.sum(book, "boss");
        if (p.getHealth() < p.getMaxHealth() * 0.5f) add += SkillTree.sum(book, "lowhp");
        if (p.getHealth() >= p.getMaxHealth() * 0.95f) add += SkillTree.sum(book, "fullhp");
        if (t.getHealth() >= t.getMaxHealth() * 0.99f) add += SkillTree.sum(book, "first");
        if (t.getHealth() < t.getMaxHealth() * 0.3f) add += SkillTree.sum(book, "execute");
        if (now - s.getLong("stackAt") < 160) add += SkillTree.sum(book, "stacking") * Math.min(5, s.getInt("stacks"));
        float crit = SkillTree.sum(book, "crit");
        boolean crt = crit > 0 && r.nextFloat() < crit;
        if (crt) add += 0.5f;
        // the crowns that change a hit
        int crown = crownOf(book);
        int hits = s.getInt("hits") + 1;
        s.putInt("hits", hits);
        boolean burst = false;
        switch (crown) {
            case 0 -> { if (p.getHealth() < p.getMaxHealth() * 0.4f) add += 0.35f; }
            case 1 -> { if (hits % 8 == 0) { add += 1f; FxSend.slash(sl, Fx.YAMI | FxPacket.KEEP_SHAPE, t.position().add(0, t.getBbHeight() * 0.5, 0), p.getLookAngle(), new Vec3(0, 1, 0), 2f, 0.4f, 8, 1.4f, 0.8f); } }
            case 2 -> { if (!p.onGround()) add += 0.2f; }
            case 4 -> { if (p.getHealth() < p.getMaxHealth() * 0.3f) burst = true; }
            case 5 -> { if (now - s.getLong("joyAt") < 120) add += 0.15f * Math.min(3, s.getInt("joy")); }
            case 6 -> {
                Long b = BURNING.get(t.getUUID());
                if (b != null && now - b < 60) { add += 0.25f; if (now != s.getLong("healAt")) { p.heal(1f); s.putLong("healAt", now); } }
            }
            case 8 -> { if (p.getHealth() >= p.getMaxHealth() * 0.7f) add += 0.25f; }
            case 11 -> {
                if (now - s.getLong("valkAt") >= 400) {
                    s.putLong("valkAt", now); add += 0.8f; p.heal(4f);
                    FxSend.ring(sl, Fx.WATER | FxPacket.KEEP_SHAPE, p.position().add(0, 0.1, 0), 0.3f, 2.2f, 0.06f, 12);
                }
            }
            case 13, 16 -> {   // Unmaking / Ripper's Hunger: the same foe, again and again
                boolean same = t.getUUID().toString().equals(s.getString("last")) && now - s.getLong("lastAt") < (crown == 13 ? 60 : 100);
                int n = same ? Math.min(crown == 13 ? 8 : 12, s.getInt("chainHits") + 1) : 0;
                s.putInt("chainHits", n);
                add += (crown == 13 ? 0.06f : 0.04f) * n;
            }
            case 14 -> { if (hits % 6 == 0) burst = true; }
            case 15 -> { if (now - s.getLong("riseAt") < 160) add += Math.min(0.3f, 0.02f * s.getInt("rise")); }
            case 19 -> { if (s.getBoolean("lightReady")) { add += 0.6f; s.putBoolean("lightReady", false); } }
            case 21 -> {   // Event Horizon: foes off the ground take more, and drag what's round them in
                if (!t.onGround()) {
                    add += 0.3f;
                    Vec3 tm = t.position().add(0, t.getBbHeight() * 0.5, 0);
                    for (LivingEntity o : Kit.near(sl, p, tm, 3)) if (o != t) GravityFx.pull(o, tm, 0.4);
                }
            }
            case 22 -> {   // Perfect Pitch: every 4th hit on the same foe
                boolean same = t.getUUID().toString().equals(s.getString("last")) && now - s.getLong("lastAt") < 100;
                int n = same ? s.getInt("pitch") + 1 : 1;
                s.putInt("pitch", n);
                if (n % 4 == 0) { add += 0.8f; FxSend.ring(sl, Fx.WIND | FxPacket.KEEP_SHAPE, t.position().add(0, t.getBbHeight() * 0.5, 0), 0.3f, 1.6f, 0.05f, 8); }
            }
            case 23 -> { if (hits % 6 == 0) { add += 0.7f; FxSend.ring(sl, 10 | FxPacket.KEEP_SHAPE, t.position().add(0, t.getBbHeight() * 0.5, 0), 0.3f, 1.4f, 0.06f, 8); } }
            case 24 -> {
                if (t.getHealth() < t.getMaxHealth() * 0.5f) add += 0.25f;
                if (r.nextFloat() < 0.1f) add += 2f;
            }
            case 25 -> { if (hits % 5 == 0) { add += 0.6f; FxSend.ring(sl, Fx.GOLD | FxPacket.KEEP_SHAPE, t.position().add(0, t.getBbHeight() * 0.5, 0), 0.3f, 1.2f, 0.05f, 8); } }
            case 26 -> {   // Bound Fate: slowed or held foes
                var ms = t.getAttribute(Attributes.MOVEMENT_SPEED);
                if (ms != null && ms.getValue() < ms.getBaseValue() * 0.95) add += 0.3f;
            }
            case 27 -> {   // Hunter's Dream: three quick hits on the same foe
                boolean same = t.getUUID().toString().equals(s.getString("last")) && now - s.getLong("lastAt") < 30;
                int n = same ? s.getInt("hunt") + 1 : 1;
                s.putInt("hunt", n);
                if (n % 3 == 0) { add += 0.6f; FxSend.ring(sl, Fx.CRIMSON | FxPacket.KEEP_SHAPE, t.position().add(0, t.getBbHeight() * 0.5, 0), 0.3f, 1.3f, 0.05f, 8); }
            }
            case 28 -> { if (t.getArmorValue() <= 1) add += 0.4f; }
            case 29 -> {   // House Edge
                float roll = r.nextFloat();
                if (roll < 0.05f) { add += 2f; FxSend.ring(sl, Fx.GOLD | FxPacket.KEEP_SHAPE, t.position().add(0, t.getBbHeight() * 0.5, 0), 0.3f, 1.8f, 0.07f, 10); }
                else if (roll < 0.25f) add += 1f;
            }
            case 30 -> { if (!t.getUUID().toString().equals(s.getString("last")) && !t.getUUID().toString().equals(s.getString("fated"))) { add += 0.4f; s.putString("fated", t.getUUID().toString()); } }
            case 31 -> { if (p.getY() < 63) add += (float) Math.min(0.4, (63 - p.getY()) * 0.01); }
            case 32 -> { if (hits % 10 == 0) { add += 1f; FxSend.ring(sl, Fx.FIRE | FxPacket.KEEP_SHAPE, t.position().add(0, t.getBbHeight() * 0.5, 0), 0.3f, 1.6f, 0.06f, 10); } }
            case 35 -> { if (DreamFx.asleep(t)) add += 0.4f; else add += 0.3f * DreamFx.drowsy(t) / 100f; }
            case 34 -> { float b = OverdriveFx.bpm(p); if (b >= OverdriveFx.RED) add += 0.3f; else if (b >= OverdriveFx.HOT) add += 0.15f; }
            case 33 -> {
                net.minecraft.core.BlockPos bp = p.blockPosition();
                if (sl.isRainingAt(bp.above())) add += 0.3f;
                else if (sl.canSeeSky(bp.above())) add += 0.1f;
            }
            default -> {}
        }
        s.putString("last", t.getUUID().toString()); s.putLong("lastAt", now);
        float dmg = e.getAmount() * (1f + add);
        e.setAmount(dmg);
        if (crt) FxSend.ring(sl, Fx.GOLD | FxPacket.KEEP_SHAPE, t.position().add(0, t.getBbHeight() + 0.2, 0), 0.4f, 0.6f, 0.03f, 6);

        // what follows the hit
        float burn = SkillTree.sum(book, "burn") + (crown == 6 ? 0.2f : 0);
        if (burn > 0) {
            BURNING.put(t.getUUID(), now);
            if (BURNING.size() > 512) BURNING.clear();
            float each = dmg * burn / 3f;
            for (int i = 1; i <= 3; i++) AstaFx.later(i * 20, () -> { if (t.isAlive()) { extra(p, t, each); BURNING.put(t.getUUID(), p.serverLevel().getGameTime()); } });
        }
        float slow = SkillTree.sum(book, "slow");
        if (slow > 0) TimedModifiers.apply(t, Attributes.MOVEMENT_SPEED, SLOW, "Mana Tree", -Math.min(0.8f, slow), AttributeModifier.Operation.MULTIPLY_TOTAL, 40);
        float weak = SkillTree.sum(book, "weaken");
        if (weak > 0 && t.getAttribute(Attributes.ATTACK_DAMAGE) != null) TimedModifiers.apply(t, Attributes.ATTACK_DAMAGE, WEAK, "Mana Tree", -Math.min(0.8f, weak), AttributeModifier.Operation.MULTIPLY_TOTAL, 60);
        Vec3 away = FxUtil.flat(t.position().subtract(p.position()));
        if (away.lengthSqr() > 1e-4 && !boss(t)) {
            float kb = SkillTree.sum(book, "knock"), pl = SkillTree.sum(book, "pull");
            if (kb > 0) { t.setDeltaMovement(t.getDeltaMovement().add(away.normalize().scale(kb).add(0, 0.2, 0))); t.hurtMarked = true; }
            if (pl > 0 && away.length() > 2.5) { t.setDeltaMovement(t.getDeltaMovement().add(away.normalize().scale(-pl).add(0, 0.1, 0))); t.hurtMarked = true; }
        }
        float ls = SkillTree.sum(book, "lifesteal") + (crown == 0 && p.getHealth() < p.getMaxHealth() * 0.4f ? 0.1f : 0);
        if (ls > 0) p.heal(Math.min(6f, dmg * ls));
        float ms = SkillTree.sum(book, "manasteal");
        if (ms > 0) {
            var md = MagicData.getPlayerMagicData(p);
            md.setMana(Math.min((float) p.getAttributeValue(AttributeRegistry.MAX_MANA.get()), md.getMana() + dmg * ms));
        }
        float chain = SkillTree.sum(book, "chain");
        if (chain > 0 && r.nextFloat() < chain) {
            for (LivingEntity o : sl.getEntitiesOfClass(LivingEntity.class, t.getBoundingBox().inflate(6), x -> x != t && x != p && x.isAlive() && x instanceof Enemy)) {
                FxSend.beam(sl, SkillTree.bookIndex(book) >= 0 ? GrimoireMastery.BOOKS.get(SkillTree.bookIndex(book)).palette() | FxPacket.KEEP_SHAPE : Fx.GOLD,
                        t.position().add(0, t.getBbHeight() * 0.5, 0), o.position().add(0, o.getBbHeight() * 0.5, 0), 0.08f, 6);
                float d = dmg * 0.4f;
                AstaFx.later(3, () -> extra(p, o, d));
                break;
            }
        }
        float echo = SkillTree.sum(book, "echo") + (crown == 12 ? 0.15f : 0);
        if (echo > 0 && r.nextFloat() < echo) {
            float d = dmg * (crown == 12 ? 0.6f : 0.4f);
            AstaFx.later(6, () -> { extra(p, t, d); FxSend.ring(sl, (crown == 12 ? Fx.MIRROR : Fx.SILVER) | FxPacket.KEEP_SHAPE, t.position().add(0, t.getBbHeight() * 0.5, 0), 0.6f, 0.8f, 0.04f, 6); });
        }
        if (burst) {   // Inferno Heart / Spirit Flare
            float d = dmg * (crown == 4 ? 0.5f : 0.6f);
            Vec3 c = t.position();
            FxSend.ring(sl, (crown == 4 ? Fx.FIRE : Fx.FUEGO) | FxPacket.KEEP_SHAPE, c.add(0, 0.1, 0), 0.3f, 3.2f, 0.12f, 10);
            FxSend.beam(sl, (crown == 4 ? Fx.FIRE : Fx.FUEGO) | FxPacket.KEEP_SHAPE, c, c.add(0, 4, 0), 0.7f, 8);
            sl.playSound(null, t.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.6f, 1.4f);
            for (LivingEntity o : sl.getEntitiesOfClass(LivingEntity.class, t.getBoundingBox().inflate(3), x -> x != p && x.isAlive() && (x instanceof Enemy || x == t)))
                AstaFx.later(2, () -> extra(p, o, d));
        }
    }

    // ------------------------------------------------------------------ casting
    @SubscribeEvent
    public static void onCast(SpellOnCastEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        ItemStack book = treeBook(p);
        if (book == null) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        ServerLevel sl = p.serverLevel();
        CompoundTag s = st(p);
        long now = now(p);
        float haste = SkillTree.sum(book, "hastecast");
        if (haste > 0) TimedModifiers.apply(p, Attributes.MOVEMENT_SPEED, HASTE, "Mana Tree", haste, AttributeModifier.Operation.MULTIPLY_BASE, 40);
        float cost = SkillTree.sum(book, "cost");
        if (cost > 0 && r.nextFloat() < cost) { RiftRewards.freeCast(p); p.displayClientMessage(Component.literal("Thrift - that spell cost nothing.").withStyle(ChatFormatting.AQUA), true); }
        float refund = SkillTree.sum(book, "refund");
        if (refund > 0 && r.nextFloat() < refund && e.getSpellId() != null) {
            String id = e.getSpellId();
            AstaFx.later(2, () -> {
                try {
                    var cd = MagicData.getPlayerMagicData(p).getPlayerCooldowns();
                    if (cd.removeCooldown(id)) { cd.syncToPlayer(p); p.displayClientMessage(Component.literal("Mana Tree - that spell is ready again.").withStyle(ChatFormatting.LIGHT_PURPLE), true); }
                } catch (Throwable ignored) {}
            });
        }
        float shield = SkillTree.sum(book, "shield");
        if (shield > 0 && now - s.getLong("shieldAt") >= 200) {
            s.putLong("shieldAt", now);
            p.setAbsorptionAmount(Math.max(p.getAbsorptionAmount(), shield));
            FxSend.ring(sl, Fx.SILVER | FxPacket.KEEP_SHAPE, p.position().add(0, 1, 0), 0.9f, 1.0f, 0.8f, 10);
        }
        if (SkillTree.sum(book, "stacking") > 0) { s.putInt("stacks", now - s.getLong("stackAt") < 160 ? s.getInt("stacks") + 1 : 1); s.putLong("stackAt", now); }
        switch (crownOf(book)) {
            case 2 -> { if (!p.onGround()) { p.setDeltaMovement(p.getDeltaMovement().x, Math.max(p.getDeltaMovement().y, 0.45), p.getDeltaMovement().z); p.hurtMarked = true; p.fallDistance = 0; } }
            case 15 -> { s.putInt("rise", now - s.getLong("riseAt") < 160 ? Math.min(15, s.getInt("rise") + 1) : 1); s.putLong("riseAt", now); }
            case 19 -> {
                if (now - s.getLong("castAt") >= 60) {
                    s.putBoolean("lightReady", true);
                    TimedModifiers.apply(p, Attributes.MOVEMENT_SPEED, LIGHT, "Speed of Light", 0.4, AttributeModifier.Operation.MULTIPLY_BASE, 40);
                    FxSend.beam(sl, Fx.GOLD | FxPacket.KEEP_SHAPE, p.position(), p.position().add(0, 2.4, 0), 0.25f, 8);
                }
            }
            default -> {}
        }
        s.putLong("castAt", now);
    }

    // ------------------------------------------------------------------ being hit
    @SubscribeEvent
    public static void onAttacked(LivingAttackEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !(e.getSource().getEntity() instanceof LivingEntity) || e.getSource().getEntity() == p) return;
        ItemStack book = treeBook(p);
        if (book == null) return;
        float dodge = SkillTree.sum(book, "dodge");
        if (dodge > 0 && ThreadLocalRandom.current().nextFloat() < Math.min(0.4f, dodge)) {
            e.setCanceled(true);
            FxSend.ring(p.serverLevel(), Fx.WIND | FxPacket.KEEP_SHAPE, p.position().add(0, 0.1, 0), 0.4f, 1.4f, 0.04f, 8);
            p.displayClientMessage(Component.literal("Evaded!").withStyle(ChatFormatting.AQUA), true);
        }
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        ItemStack book = treeBook(p);
        if (book == null) return;
        int crown = crownOf(book);
        if (crown == 8 && p.getHealth() >= p.getMaxHealth() * 0.7f) e.setAmount(e.getAmount() * 0.8f);
        if (crown == 17 && p.getDeltaMovement().horizontalDistanceSqr() < 1e-4) e.setAmount(e.getAmount() * 0.75f);
        float thorns = SkillTree.sum(book, "thorns");
        if (thorns > 0 && e.getSource().getEntity() instanceof LivingEntity by && by != p) {
            float d = e.getAmount() * thorns;
            AstaFx.later(1, () -> extra(p, by, d));
        }
        if (crown == 10 && p.getHealth() - e.getAmount() < p.getMaxHealth() * 0.3f && now(p) - st(p).getLong("gateAt") >= 900) {   // Escape Gate
            st(p).putLong("gateAt", now(p));
            Vec3 from = e.getSource().getEntity() != null ? e.getSource().getEntity().position() : p.position().add(p.getLookAngle());
            Vec3 dir = FxUtil.flat(p.position().subtract(from));
            if (dir.lengthSqr() < 1e-4) dir = new Vec3(1, 0, 0);
            Vec3 to = SpaceFx.safe(p.serverLevel(), p.position().add(dir.normalize().scale(8)), p);
            if (to != null) {
                FxSend.ring(p.serverLevel(), 17 | FxPacket.KEEP_SHAPE, p.position().add(0, 1, 0), 1.0f, 1.2f, 1.8f, 10);
                p.teleportTo(to.x, to.y, to.z);
                FxSend.ring(p.serverLevel(), 17 | FxPacket.KEEP_SHAPE, to.add(0, 1, 0), 1.0f, 1.2f, 1.8f, 10);
                p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1f, 1.2f);
            }
        }
    }

    /** The crowns that cheat death: the Red Thread of Fate and Rewind. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(LivingDamageEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || e.getAmount() < p.getHealth()) return;
        ItemStack book = treeBook(p);
        int crown = crownOf(book);
        CompoundTag s = st(p);
        long now = now(p);
        ServerLevel sl = p.serverLevel();
        if (crown == 9 && now - s.getLong("fateAt") >= 1800) {
            s.putLong("fateAt", now);
            e.setAmount(0);
            p.setHealth(p.getMaxHealth() * 0.3f);
            if (e.getSource().getEntity() instanceof LivingEntity by && by != p) {
                TimedModifiers.apply(by, Attributes.MOVEMENT_SPEED, SLOW, "Red Thread", -0.7, AttributeModifier.Operation.MULTIPLY_TOTAL, 80);
                FxSend.beam(sl, 16 | FxPacket.KEEP_SHAPE, p.position().add(0, 1, 0), by.position().add(0, by.getBbHeight() * 0.5, 0), 0.08f, 30);
            }
            p.displayClientMessage(Component.literal("The Red Thread of Fate - that ending is rewritten.").withStyle(ChatFormatting.RED), true);
            sl.playSound(null, p.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.7f, 1.4f);
        } else if (crown == 18 && now - s.getLong("rewindAt") >= 1200) {
            double[][] past = PAST.get(p.getUUID());
            if (past == null) return;
            double[] then = past[(int) ((now / 20 + 1) % past.length)];   // three seconds back
            if (then == null || then[4] <= 0) return;
            s.putLong("rewindAt", now);
            e.setAmount(0);
            FxSend.trail(sl, Fx.TIME | FxPacket.KEEP_SHAPE, p.position().add(0, 1, 0), new Vec3(then[0], then[1] + 1, then[2]), 0.3f, 12);
            p.teleportTo(then[0], then[1], then[2]);
            p.setHealth((float) Math.max(2, Math.min(p.getMaxHealth(), then[3])));
            FxSend.ring(sl, Fx.TIME | FxPacket.KEEP_SHAPE, p.position().add(0, 0.1, 0), 0.3f, 2.6f, 0.08f, 14);
            p.displayClientMessage(Component.literal("Rewind - three seconds taken back.").withStyle(ChatFormatting.AQUA), true);
            sl.playSound(null, p.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1f, 1.6f);
        }
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && crownOf(treeBook(p)) == 2) e.setDistance(0);
    }

    @SubscribeEvent
    public static void onKnock(LivingKnockBackEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && crownOf(treeBook(p)) == 17) e.setCanceled(true);
    }

    // ------------------------------------------------------------------ kills, and the slow ticks
    @SubscribeEvent
    public static void onKill(LivingDeathEvent e) {
        if (!(e.getSource().getEntity() instanceof ServerPlayer p) || e.getEntity() == p) return;
        ItemStack book = treeBook(p);
        if (book == null) return;
        float km = SkillTree.sum(book, "killmana");
        if (km > 0) {
            var md = MagicData.getPlayerMagicData(p);
            float max = (float) p.getAttributeValue(AttributeRegistry.MAX_MANA.get());
            md.setMana(Math.min(max, md.getMana() + max * km));
        }
        float kh = SkillTree.sum(book, "killheal");
        int crown = crownOf(book);
        if (crown == 7) kh += 3;
        if (kh > 0) p.heal(kh);
        CompoundTag s = st(p);
        long now = now(p);
        if (crown == 5) {
            int n = now - s.getLong("joyAt") < 120 ? Math.min(3, s.getInt("joy") + 1) : 1;
            s.putInt("joy", n); s.putLong("joyAt", now);
            TimedModifiers.apply(p, Attributes.MOVEMENT_SPEED, JOY, "Battle Joy", 0.1 * n, AttributeModifier.Operation.MULTIPLY_BASE, 120);
            FxSend.ring(p.serverLevel(), Fx.LIGHTNING | FxPacket.KEEP_SHAPE, p.position().add(0, 0.1, 0), 0.3f, 1.6f + n * 0.4f, 0.05f, 10);
        }
        if (crown == 7) {
            int n = now - s.getLong("marrowAt") < 200 ? Math.min(3, s.getInt("marrow") + 1) : 1;
            s.putInt("marrow", n); s.putLong("marrowAt", now);
            TimedModifiers.apply(p, Attributes.ARMOR, MARROW, "Devil's Marrow", 4.0 * n, AttributeModifier.Operation.ADDITION, 200);
        }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        long now = now(p);
        if (now % 20 != 0) return;
        if (now % 100 == 0) {   // a new leaf grown: say so (once per leaf)
            ItemStack bk = Utils.getPlayerSpellbookStack(p);
            if (SkillTree.bookIndex(bk) >= 0) {
                int free = SkillTree.earned(bk, MemoryPages.chapter(p)) - SkillTree.spent(bk), seen = bk.getOrCreateTag().getInt("TreeSeen");
                if (free > seen) p.displayClientMessage(Component.literal("\u2618 Your grimoire grew a leaf - " + free + " to spend on its Mana Tree (open it, then the Tree ribbon).").withStyle(ChatFormatting.GREEN), false);
                if (free != seen) bk.getOrCreateTag().putInt("TreeSeen", free);
            }
        }
        ItemStack book = treeBook(p);
        if (book == null) { if (now % 200 == 0) STATE.remove(p.getUUID()); return; }
        int crown = crownOf(book);
        if (crown == 18) {   // remember the last few seconds
            double[][] past = PAST.computeIfAbsent(p.getUUID(), k -> new double[4][]);
            past[(int) ((now / 20) % past.length)] = new double[]{p.getX(), p.getY(), p.getZ(), p.getHealth(), 1};
        }
        float hp = SkillTree.sum(book, "regenhp");
        if (hp > 0 && now % 100 == 0 && p.getHealth() < p.getMaxHealth()) p.heal(hp);
        if (crown == 3 && now % 300 == 0 && p.getAbsorptionAmount() < 6) {
            p.setAbsorptionAmount(6);
            FxSend.ring(p.serverLevel(), 11 | FxPacket.KEEP_SHAPE, p.position().add(0, 1, 0), 0.9f, 1.0f, 1.0f, 12);
        }
    }

    @SubscribeEvent
    public static void onLogout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e) { STATE.remove(e.getEntity().getUUID()); PAST.remove(e.getEntity().getUUID()); }
}
