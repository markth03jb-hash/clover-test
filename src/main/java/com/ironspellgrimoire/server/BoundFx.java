package com.ironspellgrimoire.server;

import static com.ironspellgrimoire.server.FxUtil.*;

import com.ironspellgrimoire.IronSpellGrimoire;
import com.ironspellgrimoire.entity.AddonBoss;
import com.ironspellgrimoire.network.AddonNetwork;
import com.ironspellgrimoire.network.BoundPackets;
import com.ironspellgrimoire.registry.AddonItemRegistry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * The Grimoire of the Bound Demon: a demon in your katana, a seraph asleep in you, and the people you fight for (every
 * name, look and line here is original).
 * <ul>
 *   <li><b>Possession</b> (0-100%): fighting with the katana fills it; its power is yours (attribute modifiers - harder,
 *       faster blows). At 100% the demon takes over for 8 s: it turns you on the nearest creature - your pets and
 *       villagers too - swings for you, holds the blade in your hand and keeps you from casting; you take 25% more.
 *       Then you are yourself again at 40%.</li>
 *   <li><b>Family</b>: allies near you (players, your tamed beasts, villagers, golems) calm the demon; when one is
 *       hurt, the demon's anger rises with your next blow; when one would die near you, the seraph wakes (Never Again,
 *       every 10 minutes).</li>
 *   <li>The undead take 25% more from the katana.</li>
 *   <li>Pages: Demon Draw, Cursed Edge, Demon's Whisper, Bond Guard, Demon Flurry, Sinner's Mark, Salt Lance, and the
 *       ultimate, the Last Seraph - one great white wing, flight, judgment and salt.</li>
 *   <li>The Bond Tree (server/BookTree) changes almost all of it.</li>
 * </ul>
 * No block is touched; slows and boosts are attribute modifiers, never status effects; salt statues are effects only.
 */
@Mod.EventBusSubscriber(modid = IronSpellGrimoire.MODID)
public final class BoundFx {
    private BoundFx() {}

    static final int DARK = 47, RED = 18, SALT = 28;   // black with a blue sheen, pure crimson, cotton white
    static final UUID POSS_DMG = UUID.fromString("b0d0000a-0000-4000-8000-000000000001"), POSS_SPD = UUID.fromString("b0d0000a-0000-4000-8000-000000000002"),
            POSS_MOVE = UUID.fromString("b0d0000a-0000-4000-8000-000000000003"), WHISPER = UUID.fromString("b0d0000a-0000-4000-8000-000000000004"),
            SKIN = UUID.fromString("b0d0000a-0000-4000-8000-000000000005"), AWAKE_DMG = UUID.fromString("b0d0000a-0000-4000-8000-000000000006"),
            AWAKE_MOVE = UUID.fromString("b0d0000a-0000-4000-8000-000000000007"), STAGGER = UUID.fromString("b0d0000a-0000-4000-8000-000000000008"),
            SALTED = UUID.fromString("b0d0000a-0000-4000-8000-000000000009"), CALL = UUID.fromString("b0d0000a-0000-4000-8000-00000000000a");
    public static final int BALANCED = 0, RESTRAIN = 1, RELEASE = 2;

    record Trail(Vec3 a, Vec3 b, long until) {}
    record Wall(Vec3 c, Vec3 along, long until) {}

    static final class S {
        float poss;
        int mode, hitCount, blood, wingGuards;
        long warnUntil, takeoverUntil, symUntil, afterUntil, awakeUntil, whisperUntil, flurryUntil, ghostUntil, angerUntil;
        long lastEdge = -1000, sneakAt = -1000, stillSince, neverAgainAt = -1000000, elderAt = -1000, lastStandAt = -1000000, untouchUntil, allyHurtAt = -1000;
        boolean sneakWas, fallen, flyGranted, sentAwake;
        Vec3 edgeFrom, lastPos, flurryAt;
        LivingEntity allyHurtBy, target;
        final Map<LivingEntity, Long> links = new java.util.LinkedHashMap<>();
        final Map<LivingEntity, Long> offenders = new WeakHashMap<>();
        final Map<LivingEntity, long[]> sinners = new WeakHashMap<>();   // {until, grudge 0/1}
        final List<Trail> trails = new ArrayList<>();
        final List<Wall> walls = new ArrayList<>();
    }
    static final Map<UUID, S> STATE = new HashMap<>();
    static S s(ServerPlayer p) { return STATE.computeIfAbsent(p.getUUID(), k -> new S()); }
    /** Salt on a creature: {stacks, crusted until}. */
    static final Map<LivingEntity, long[]> SALTS = new WeakHashMap<>();
    /** Allies made untouchable by Last Stand Together. */
    static final Map<LivingEntity, Long> UNTOUCH = new WeakHashMap<>();
    private static boolean echo;

    public static boolean has(Player p) { return BookTree.kind(p) == BookTree.BOUND; }
    static int r(ServerPlayer p, String id) { return BookTree.rank(p, id); }
    static boolean boss(LivingEntity t) { return t instanceof AddonBoss || TimeStop.isBoss(t); }
    static boolean holding(ServerPlayer p) { return p.getMainHandItem().is(AddonItemRegistry.DEMON_KATANA.get()); }
    public static boolean takenOver(ServerPlayer p) { S s = STATE.get(p.getUUID()); return s != null && p.level().getGameTime() < s.takeoverUntil; }
    static boolean awake(ServerPlayer p) { S s = STATE.get(p.getUUID()); return s != null && p.level().getGameTime() < s.awakeUntil; }
    static int awakenings(ServerPlayer p) { ItemStack b = BookTree.book(p); return b.hasTag() ? b.getTag().getCompound("BT").getInt("aw") : 0; }

    /** Family: other players, your own tamed beasts, villagers and golems, and anything on your side. */
    public static boolean ally(ServerPlayer p, LivingEntity e) {
        if (e == p || !e.isAlive()) return false;
        if (e instanceof Player) return true;
        if (e instanceof TamableAnimal ta && p.getUUID().equals(ta.getOwnerUUID())) return true;
        if (e instanceof AbstractVillager || e instanceof IronGolem) return true;
        return e.isAlliedTo(p);
    }
    static List<LivingEntity> allies(ServerPlayer p, double r) {
        return p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(r), x -> ally(p, x));
    }

    // ------------------------------------------------------------------ Possession
    static float gainMul(ServerPlayer p) {
        S s = s(p);
        float m = s.mode == RESTRAIN ? 0.5f : s.mode == RELEASE ? 1.5f : 1f;
        if (r(p, "b5") > 0) m *= 1f - 0.1f * Math.min(4, allies(p, 10).size());
        return m;
    }

    static void feed(ServerPlayer p, float amount) {
        S s = s(p);
        long now = p.level().getGameTime();
        if (now < s.takeoverUntil || now < s.symUntil) return;
        float cap = r(p, "K1") > 0 ? 90f : 100f;
        s.poss = Math.max(0f, Math.min(cap, s.poss + amount));
        if (s.poss >= 100f && s.warnUntil == 0) {
            if (r(p, "d3") > 0) {
                s.warnUntil = now + 60;
                ModeHud.show(p, "bd_warning", 60);
                p.displayClientMessage(Component.literal("The demon is at the door...").withStyle(ChatFormatting.DARK_RED), true);
                play(p.serverLevel(), p.position(), "bd_whisper", 1f, 0.7f);
            } else takeover(p);
        }
    }

    static void takeover(ServerPlayer p) {
        S s = s(p);
        ServerLevel sl = p.serverLevel();
        long now = sl.getGameTime();
        s.warnUntil = 0;
        ModeHud.hide(p, "bd_warning");
        s.takeoverUntil = now + (r(p, "K2") > 0 ? 240 : 160);
        ModeHud.show(p, "bd_takeover", (int) (s.takeoverUntil - now));
        s.poss = 100f;
        draw(p, false);
        p.displayClientMessage(Component.literal("The demon has your hands.").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), true);
        FxSend.ring(sl, RED, mid(p), 0.3f, 2.4f, 0.12f, 12);
        FxSend.shake(sl, p.position(), 0.3f, 6, 10);
        play(sl, p.position(), "bd_takeover", 1.3f, 0.8f);
        if (r(p, "d7") > 0) TimedModifiers.apply(p, Attributes.KNOCKBACK_RESISTANCE, SKIN, "Demon Skin", 1.0, AttributeModifier.Operation.ADDITION, (int) (s.takeoverUntil - now));
        broadcast(p);
    }

    static void endTakeover(ServerPlayer p) {
        S s = s(p);
        s.takeoverUntil = 0;
        ModeHud.hide(p, "bd_takeover");
        s.poss = 40f;
        s.target = null;
        s.fallen = false;
        if (r(p, "d9") > 0) { s.afterUntil = p.level().getGameTime() + 120; ModeHud.show(p, "bd_afterglow", 120); }
        p.displayClientMessage(Component.literal("You are yourself again.").withStyle(ChatFormatting.GRAY), true);
        play(p.serverLevel(), p.position(), "bd_whisper", 0.8f, 1.3f);
        BookTree.grow(p, BookTree.BOUND, 3);
        broadcast(p);
    }

    /** The katana into your hand (what you held goes to your pack). */
    static boolean draw(ServerPlayer p, boolean fx) {
        if (holding(p)) return true;
        ItemStack main = p.getMainHandItem();
        if (!main.isEmpty()) {
            if (!p.getInventory().add(main.copy())) { p.displayClientMessage(Component.literal("No room in your pack for what you're holding.").withStyle(ChatFormatting.GRAY), true); return false; }
        }
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AddonItemRegistry.DEMON_KATANA.get()));
        if (fx) {
            play(p.serverLevel(), p.position(), "bd_draw", 1f, 1f);
            FxSend.ring(p.serverLevel(), RED, mid(p), 0.2f, 1.2f, 0.06f, 8);
        }
        return true;
    }

    // ------------------------------------------------------------------ salt and sinners
    static void salt(ServerPlayer p, LivingEntity t, int n) {
        if (r(p, "s2") <= 0) return;
        long now = p.level().getGameTime();
        long[] v = SALTS.computeIfAbsent(t, k -> new long[2]);
        v[0] += n;
        if (v[0] >= 3) {
            v[0] = 0; v[1] = now + 60;
            TimedModifiers.apply(t, Attributes.MOVEMENT_SPEED, SALTED, "Salt Crust", -0.6, AttributeModifier.Operation.MULTIPLY_TOTAL, 60);
            FxSend.ring(p.serverLevel(), SALT, mid(t), 0.2f, 1.1f, 0.08f, 60);
            play(p.serverLevel(), t.position(), "bd_salt", 0.9f, 1.2f);
        }
    }
    static boolean crusted(LivingEntity t, long now) { long[] v = SALTS.get(t); return v != null && v[1] > now; }

    static void mark(ServerPlayer p, LivingEntity t, long ticks, boolean grudge) {
        if (t == null || !t.isAlive() || ally(p, t)) return;
        S s = s(p);
        long until = r(p, "K5") > 0 ? Long.MAX_VALUE : p.level().getGameTime() + ticks;
        long[] old = s.sinners.get(t);
        s.sinners.put(t, new long[]{Math.max(until, old == null ? 0 : old[0]), grudge || (old != null && old[1] == 1) ? 1 : 0});
    }
    static boolean sinner(ServerPlayer p, LivingEntity t) { long[] v = s(p).sinners.get(t); return v != null && v[0] > p.level().getGameTime(); }

    /** A salt statue: where a creature died, its shape in white, crumbling (effects only - no block). */
    static void statue(ServerLevel sl, LivingEntity t) {
        Vec3 c = t.position().add(0, t.getBbHeight() / 2, 0);
        FxSend.box(sl, SALT, c, new Vec3(1, 0, 0), new Vec3(0, 1, 0), t.getBbWidth() * 0.5f, t.getBbWidth() * 0.5f, t.getBbHeight() * 0.5f, 30, 0, com.ironspellgrimoire.network.FxPacket.GROUND);
        for (int i = 0; i < 6; i++) Kit.orb(sl, SALT, c.add((i % 3 - 1) * 0.3, (i / 3) * 0.4 - 0.2, 0), new Vec3(0, -0.05, 0), 0.12f, 16);
        play(sl, c, "bd_salt", 1f, 0.8f);
    }

    // ------------------------------------------------------------------ the awakening
    static void awaken(ServerPlayer p, boolean saving) {
        S s = s(p);
        ServerLevel sl = p.serverLevel();
        long now = sl.getGameTime();
        s.awakeUntil = now + (r(p, "s8") > 0 ? 600 : 400);
        s.fallen = takenOver(p) && r(p, "s10") > 0;
        if (s.fallen) s.takeoverUntil = s.awakeUntil;
        ItemStack b = BookTree.book(p);
        BookTree.data(b).putInt("aw", BookTree.data(b).getInt("aw") + 1);
        if (r(p, "K3") <= 0 && !p.getAbilities().mayfly) { p.getAbilities().mayfly = true; p.onUpdateAbilities(); s.flyGranted = true; }
        s.wingGuards = r(p, "K4") > 0 ? 6 : 0;
        ModeHud.show(p, "last_seraph", (int) (s.awakeUntil - now), s.wingGuards > 0 ? s.wingGuards : -1);
        if (s.fallen) ModeHud.show(p, "bd_takeover", (int) (s.takeoverUntil - now));
        FxSend.ring(sl, SALT, p.position().add(0, 0.1, 0), 0.4f, 6f, 0.2f, 14);
        FxSend.beam(sl, SALT, p.position().add(0, 12, 0), p.position(), 0.8f, 12);
        FxSend.shake(sl, p.position(), 0.4f, 8, 16);
        play(sl, p.position(), "bd_seraph", 1.6f, 1f);
        p.displayClientMessage(Component.literal(s.fallen ? "The fallen seraph wakes." : saving ? "Never again." : "The seraph wakes.").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD), true);
        if (r(p, "s4") > 0) {   // Trumpet Call
            play(sl, p.position(), "bd_trumpet", 1.6f, 1f);
            for (LivingEntity t : enemies(sl, p, p.getBoundingBox().inflate(10))) {
                if (ally(p, t)) continue;
                Kit.push(t, flatAway(t, p), boss(t) ? 0.3 : 1.4, 0.4);
                salt(p, t, 3);
            }
        }
        SeraphFx.apply(p, true);   // the seraph's own moves on the wheel while it lasts
        FxSend.impactFrame(sl, p.position().add(0, 1, 0), 5, false, 32);
        FxSend.cracks(sl, SALT, p.position().add(0, 0.05, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1), 4.5f, 30, false, 11);
        BookTree.grow(p, BookTree.BOUND, 5);
        broadcast(p);
    }

    static void endAwake(ServerPlayer p) {
        S s = s(p);
        s.awakeUntil = 0;
        SeraphFx.apply(p, false);
        ModeHud.hide(p, "last_seraph");
        if (s.flyGranted && !p.isCreative() && !p.isSpectator()) { p.getAbilities().mayfly = false; p.getAbilities().flying = false; p.onUpdateAbilities(); }
        s.flyGranted = false;
        p.fallDistance = 0;
        broadcast(p);
    }

    static Vec3 flatAway(LivingEntity t, LivingEntity from) {
        Vec3 d = new Vec3(t.getX() - from.getX(), 0, t.getZ() - from.getZ());
        return d.lengthSqr() < 1e-4 ? new Vec3(from.getLookAngle().x, 0, from.getLookAngle().z).normalize() : d.normalize();
    }

    /** Wings and the demon's eye for everyone who can see this mage. */
    static void broadcast(ServerPlayer p) {
        S s = s(p);
        long now = p.level().getGameTime();
        var m = new BoundPackets.State(p.getId(), (int) Math.max(0, s.awakeUntil - now), now < s.takeoverUntil, s.fallen, r(p, "K4") > 0);
        AddonNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), m);
    }

    // ------------------------------------------------------------------ blows
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onHurt(LivingHurtEvent e) {
        LivingEntity victim = e.getEntity();
        if (victim.level().isClientSide || !(victim.level() instanceof ServerLevel sl)) return;
        Entity src = e.getSource().getEntity();
        long now = sl.getGameTime();
        Long ut = UNTOUCH.get(victim);
        if (ut != null && ut > now) { e.setCanceled(true); return; }
        // ---- a Bound mage strikes
        if (src instanceof ServerPlayer p && has(p) && victim != p && !echo) {
            S s = s(p);
            boolean katana = holding(p) && e.getSource().getDirectEntity() == p && BookTree.melee(e.getSource());
            float m = 1f;
            long[] sv = s.sinners.get(victim);
            if (sv != null && sv[0] > now) m *= (r(p, "K5") > 0 ? 1.5f : 1.2f) * (sv[1] == 1 ? 2f : 1f);
            if (now < s.takeoverUntil) {
                if (r(p, "d6") > 0) { m *= 1.5f; }
                if (r(p, "K2") > 0) m *= 2f;
                if (s.fallen) m *= 2f;
            }
            if (katana) {
                if (victim.isInvertedHealAndHarm()) m *= 1.25f;   // the undead
                if (r(p, "b5") > 0) m *= 1f + 0.1f * Math.min(4, allies(p, 10).size());
                if (now < s.angerUntil) { m *= 1.3f; s.angerUntil = 0; }
                feed(p, 4f * gainMul(p));
                s.hitCount++;
                if (r(p, "k9") > 0) s.blood = Math.min(10, s.blood + 1);
                if (crusted(victim, now)) {   // the crust shatters
                    SALTS.get(victim)[1] = 0;
                    AstaFx.later(1, () -> { if (victim.isAlive()) { echo = true; try { AstaFx.damage(p, victim, 10f, "salt_lance"); } finally { echo = false; } } });
                    FxSend.ring(sl, SALT, mid(victim), 0.2f, 1.6f, 0.1f, 8);
                }
            }
            float amt = e.getAmount() * m;
            e.setAmount(amt);
            if (now < s.takeoverUntil && r(p, "d6") > 0) p.heal(amt * 0.1f);
            if (katana && r(p, "d2") > 0 && s.poss >= 50f) AstaFx.later(1, () -> { if (victim.isAlive()) { echo = true; try { AstaFx.damage(p, victim, amt * 0.25f, "cursed_edge"); } finally { echo = false; } } });
            if (katana && r(p, "d4") > 0 && s.poss >= 75f && s.hitCount % 4 == 0) {   // Shadow Limbs
                Vec3 look = p.getLookAngle();
                FxSend.slash(sl, DARK, mid(p).add(look.scale(1.5)), look, new Vec3(0, 1, 0), 1.5f, 3.2f, 6, 0);
                int n = 0;
                for (LivingEntity t : enemies(sl, p, p.getBoundingBox().inflate(4))) {
                    if (t == victim || n >= 3 || ally(p, t) || mid(t).subtract(p.getEyePosition()).normalize().dot(look) < 0.3) continue;
                    final LivingEntity tt = t;
                    AstaFx.later(2, () -> { echo = true; try { AstaFx.damage(p, tt, amt * 0.6f, "demon_flurry"); } finally { echo = false; } });
                    n++;
                }
            }
            if (katana && r(p, "k8") > 0 && victim.getHealth() < victim.getMaxHealth() * 0.25f) {
                float extra = Math.min(20f, 0.4f * (victim.getMaxHealth() - victim.getHealth()));
                AstaFx.later(1, () -> { if (victim.isAlive()) { echo = true; try { AstaFx.damage(p, victim, extra, "cursed_edge"); } finally { echo = false; } } });
            }
        }
        // ---- a bound ally strikes: Carried Hope
        if (src instanceof LivingEntity att && !(src instanceof ServerPlayer sp && has(sp)))
            for (S s : STATE.values()) {
                Long lk = s.links.get(att);
                if (lk != null && lk > now) { ServerPlayer owner = ownerOf(sl, s); if (owner != null && r(owner, "b8") > 0) e.setAmount(e.getAmount() * 1.2f); break; }
            }
        // ---- the Bound mage is struck
        if (victim instanceof ServerPlayer p && has(p)) {
            S s = s(p);
            if (now < s.untouchUntil) { e.setCanceled(true); return; }
            LivingEntity att = src instanceof LivingEntity le ? le : null;
            if (att != null && att != p) {
                s.offenders.put(att, now);
                if (now < s.awakeUntil && r(p, "s5") > 0) mark(p, att, 200, false);
            }
            if (now < s.awakeUntil && att != null) {
                if (r(p, "s6") > 0 && mid(att).subtract(mid(p)).normalize().dot(p.getLookAngle()) < -0.3) { e.setCanceled(true); FxSend.ring(sl, SALT, mid(p), 0.3f, 1.4f, 0.06f, 6); return; }
                if (s.wingGuards > 0) { s.wingGuards--; ModeHud.show(p, "last_seraph", (int) (s.awakeUntil - now), s.wingGuards); e.setCanceled(true); FxSend.ring(sl, SALT, mid(p), 0.3f, 1.4f, 0.06f, 6); play(sl, p.position(), "bd_salt", 0.8f, 1.4f); return; }
            }
            if (att != null && r(p, "k4") > 0 && holding(p) && BookTree.melee(e.getSource()) && now - s.sneakAt <= 6) {   // Parry
                e.setCanceled(true);
                TimedModifiers.apply(att, Attributes.MOVEMENT_SPEED, STAGGER, "Staggered", -0.5, AttributeModifier.Operation.MULTIPLY_TOTAL, 20);
                if (!boss(att)) Kit.push(att, flatAway(att, p), 0.6, 0.2);
                FxSend.slash(sl, RED, mid(p).add(p.getLookAngle()), p.getLookAngle(), new Vec3(0, 1, 0), 0.8f, 1.6f, 5, 0);
                play(sl, p.position(), "bd_parry", 1f, 1.2f);
                ModeHud.show(p, "bd_parry", 20);
                BookTree.grow(p, BookTree.BOUND, 1);
                return;
            }
            if (now < s.takeoverUntil) e.setAmount(e.getAmount() * (r(p, "d7") > 0 ? 0.7f : 1.25f));
            if (r(p, "b10") > 0 && e.getAmount() >= p.getHealth() && now - s.lastStandAt > 3600) {   // Last Stand Together
                LivingEntity a = allies(p, 8).stream().findFirst().orElse(null);
                if (a != null) {
                    s.lastStandAt = now;
                    e.setAmount(Math.max(0f, p.getHealth() - 1f));
                    s.untouchUntil = now + 60; UNTOUCH.put(a, now + 60);
                    ModeHud.show(p, "bd_last_stand", 60);
                    FxSend.ring(sl, SALT, mid(p), 0.3f, 2f, 0.1f, 60); FxSend.ring(sl, SALT, mid(a), 0.3f, 2f, 0.1f, 60);
                    p.displayClientMessage(Component.literal("Not yet - not while they're here.").withStyle(ChatFormatting.WHITE), true);
                }
            }
        }
        // ---- an ally of a Bound mage is struck
        if (!(victim instanceof ServerPlayer vp && has(vp)))
            for (Map.Entry<UUID, S> en : STATE.entrySet()) {
                if (!(sl.getPlayerByUUID(en.getKey()) instanceof ServerPlayer p) || !has(p) || p == victim || !ally(p, victim) || p.distanceToSqr(victim) > 256) continue;
                S s = en.getValue();
                LivingEntity att = src instanceof LivingEntity le && le != p ? le : null;
                if (att != null) {
                    s.allyHurtAt = now; s.allyHurtBy = att;
                    s.offenders.put(att, now);
                    s.angerUntil = now + 100;   // the demon wakes for them
                    feed(p, 3f);
                    if (now < s.awakeUntil && r(p, "s5") > 0) mark(p, att, 200, false);
                }
                Long lk = s.links.get(victim);
                if (lk != null && lk > now) {   // Bond Guard: you take part of it
                    float share = r(p, "b1") > 0 ? 0.3f : 0.5f;
                    float amt = e.getAmount();
                    e.setAmount(amt * (1 - share));
                    AstaFx.later(1, () -> { if (p.isAlive()) p.hurt(p.damageSources().magic(), amt * share); });
                    FxSend.trail(sl, RED, mid(victim), mid(p), 0.05f, 6);
                    BookTree.grow(p, BookTree.BOUND, 0.5f);
                }
                if (e.getAmount() >= victim.getHealth() && now - s.neverAgainAt >= (r(p, "b9") > 0 ? 6000 : 12000) && !awake(p)) {   // Never Again
                    s.neverAgainAt = now;
                    e.setCanceled(true);
                    if (r(p, "b9") > 0) victim.setHealth(Math.max(victim.getHealth(), victim.getMaxHealth() * 0.5f));
                    FxSend.ring(sl, SALT, mid(victim), 0.3f, 2f, 0.12f, 12);
                    awaken(p, true);
                    BookTree.grow(p, BookTree.BOUND, 3);
                    return;
                }
                if (r(p, "b4") > 0 && victim.getHealth() - e.getAmount() < victim.getMaxHealth() * 0.3f && now - s.elderAt >= 600 && !s.links.containsKey(victim)) {
                    s.elderAt = now; link(p, victim);
                }
            }
    }

    static ServerPlayer ownerOf(ServerLevel sl, S s) {
        for (Map.Entry<UUID, S> en : STATE.entrySet()) if (en.getValue() == s && sl.getPlayerByUUID(en.getKey()) instanceof ServerPlayer p) return p;
        return null;
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent e) {
        LivingEntity dead = e.getEntity();
        if (!(dead.level() instanceof ServerLevel sl)) return;
        long now = sl.getGameTime();
        Entity src = e.getSource().getEntity();
        if (src instanceof ServerPlayer p && has(p) && dead != p) {
            S s = s(p);
            if (holding(p)) {
                if (r(p, "d1") > 0) { feed(p, 8f * gainMul(p)); p.heal(2f); } else feed(p, 3f * gainMul(p));
            }
            if (now < s.takeoverUntil && r(p, "d8") > 0) { s.takeoverUntil += 40; p.heal(4f); if (s.fallen) s.awakeUntil = Math.max(s.awakeUntil, s.takeoverUntil); }
            if (now < s.awakeUntil || s.fallen) statue(sl, dead);
            if (r(p, "s3") > 0 && crusted(dead, now)) {
                Vec3 at = mid(dead);
                AstaFx.later(20, () -> {
                    FxSend.ring(sl, SALT, at, 0.2f, 3f, 0.3f, 10);
                    play(sl, at, "bd_salt", 1.2f, 0.7f);
                    for (LivingEntity t : enemies(sl, p, new AABB(at, at).inflate(3))) if (!ally(p, t)) AstaFx.damage(p, t, 6f, "salt_lance");
                });
            }
            if (sinner(p, dead) && r(p, "b7") > 0) for (LivingEntity a : allies(p, 8)) { a.heal(4f); FxSend.ring(sl, SALT, mid(a), 0.2f, 0.9f, 0.05f, 8); }
            s.sinners.remove(dead);
            BookTree.grow(p, BookTree.BOUND, 1);
        }
        // an ally falls: Grudge
        if (src instanceof LivingEntity killer)
            for (Map.Entry<UUID, S> en : STATE.entrySet())
                if (sl.getPlayerByUUID(en.getKey()) instanceof ServerPlayer p && has(p) && p != dead && ally(p, dead) && p.distanceToSqr(dead) < 256 && r(p, "b6") > 0) {
                    mark(p, killer, 1200, true);
                    p.displayClientMessage(Component.literal(killer.getDisplayName().getString() + " will pay for that.").withStyle(ChatFormatting.DARK_RED), true);
                }
    }

    /** No casting while the demon has your hands (the Last Seraph alone, with Fallen Seraph). */
    @SubscribeEvent
    public static void onPreCast(io.redspace.ironsspellbooks.api.events.SpellPreCastEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !takenOver(p)) return;
        if (r(p, "s10") > 0 && e.getSpellId() != null && e.getSpellId().endsWith("last_seraph")) return;
        e.setCanceled(true);
        p.displayClientMessage(Component.literal("The demon has your hands - you can't cast.").withStyle(ChatFormatting.DARK_RED), true);
    }

    // ------------------------------------------------------------------ the tick
    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || !p.isAlive()) return;
        if (!has(p)) {
            S old = STATE.remove(p.getUUID());
            if (old != null && old.flyGranted && !p.isCreative() && !p.isSpectator()) { p.getAbilities().mayfly = false; p.getAbilities().flying = false; p.onUpdateAbilities(); }
            if (old != null && old.mode != 0) ModeHud.standing(p, new String[]{"bd_restrain", "bd_release"}, -1);
            if (old != null) AddonNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), new BoundPackets.State(p.getId(), 0, false, false, false));
            return;
        }
        S s = s(p);
        ServerLevel sl = p.serverLevel();
        long now = sl.getGameTime();
        // sneak taps (Parry) and standing still (Iai Stance)
        boolean sh = p.isShiftKeyDown();
        if (sh && !s.sneakWas) s.sneakAt = now;
        s.sneakWas = sh;
        if (s.lastPos == null || s.lastPos.distanceToSqr(p.position()) > 0.0004) s.stillSince = now;
        s.lastPos = p.position();
        // the warning runs out
        if (s.warnUntil > 0 && now >= s.warnUntil) { if (s.poss >= 100f) takeover(p); else s.warnUntil = 0; }
        boolean over = now < s.takeoverUntil;
        if (s.takeoverUntil > 0 && !over) endTakeover(p);
        if (over) possessed(p, s, now);
        // Possession fades out of the fight, faster among family
        if (!over && now % 20 == 0 && now >= s.symUntil) {
            int near = allies(p, 8).size();
            float fade = (holding(p) ? (s.mode == RESTRAIN ? 2f : 0f) : 3f) + (near > 0 ? 1f : 0f) * (s.mode == RELEASE ? 0.5f : 1f);
            s.poss = Math.max(0f, s.poss - fade);
            if (holding(p) && s.mode == RELEASE) feed(p, 1f);
        }
        // the power: attribute modifiers, refreshed
        if (now % 10 == 0) {
            float f = over || now < s.symUntil || now < s.afterUntil ? 1f : s.poss / 100f;
            if (f > 0.01f) {
                TimedModifiers.apply(p, Attributes.ATTACK_DAMAGE, POSS_DMG, "Possession", 0.6 * f, AttributeModifier.Operation.MULTIPLY_TOTAL, 30);
                TimedModifiers.apply(p, Attributes.ATTACK_SPEED, POSS_SPD, "Possession", 0.3 * f, AttributeModifier.Operation.MULTIPLY_TOTAL, 30);
                TimedModifiers.apply(p, Attributes.MOVEMENT_SPEED, POSS_MOVE, "Possession", 0.15 * f, AttributeModifier.Operation.MULTIPLY_TOTAL, 30);
            }
            if (now < s.awakeUntil) {
                TimedModifiers.apply(p, Attributes.ATTACK_DAMAGE, AWAKE_DMG, "The Last Seraph", r(p, "K3") > 0 ? 0.9 : 0.5, AttributeModifier.Operation.MULTIPLY_TOTAL, 30);
                TimedModifiers.apply(p, Attributes.MOVEMENT_SPEED, AWAKE_MOVE, "The Last Seraph", 0.3, AttributeModifier.Operation.MULTIPLY_TOTAL, 30);
            }
        }
        // the awakening
        if (s.awakeUntil > 0 && now >= s.awakeUntil) endAwake(p);
        else if (now % 40 == 0 && now >= s.awakeUntil) SeraphFx.apply(p, false);   // never left with the seraph's pages (a crash, a relog)
        if (now < s.awakeUntil) {
            p.fallDistance = 0;
            SeraphFx.aura(p, sl, now);
            int every = r(p, "s7") > 0 ? 10 : 20;
            if (now % every == 0) for (Map.Entry<LivingEntity, long[]> en : new ArrayList<>(s.sinners.entrySet())) {
                LivingEntity t = en.getKey();
                if (en.getValue()[0] <= now || !t.isAlive() || t.level() != sl || t.distanceToSqr(p) > 576) continue;
                Vec3 c = mid(t);
                FxSend.beam(sl, SALT, c.add(0, 7, 0), c, 0.2f, 6);
                AstaFx.damage(p, t, 4f, "salt_lance");
                salt(p, t, 1);
            }
            if (r(p, "s9") > 0 && now % 40 == 0) for (LivingEntity a : allies(p, 8)) a.heal(1f);
            if (now % 4 == 0) Kit.orb(sl, SALT, p.position().add((Math.random() - 0.5) * 1.2, 1.8 + Math.random(), (Math.random() - 0.5) * 1.2), new Vec3(0, -0.04, 0), 0.06f, 14);
        }
        // the links
        s.links.entrySet().removeIf(en -> en.getValue() <= now || !en.getKey().isAlive());
        if (now % 10 == 0) for (LivingEntity a : s.links.keySet()) if (a.level() == sl) FxSend.trail(sl, RED, mid(p), mid(a), 0.02f, 10);
        // Sinners glow
        if (now % 20 == 0) for (Map.Entry<LivingEntity, long[]> en : s.sinners.entrySet())
            if (en.getValue()[0] > now && en.getKey().isAlive() && en.getKey().level() == sl && en.getKey().distanceToSqr(p) < 1600)
                FxSend.ring(sl, RED, en.getKey().position().add(0, en.getKey().getBbHeight() + 0.3, 0), 0.15f, 0.45f, 0.05f, 20);
        // Smoke Trail
        for (Iterator<Trail> it = s.trails.iterator(); it.hasNext(); ) {
            Trail t = it.next();
            if (now >= t.until()) { it.remove(); continue; }
            if (now % 10 == 0) {
                FxSend.trail(sl, DARK, t.a(), t.b(), 0.12f, 10);
                for (LivingEntity x : enemies(sl, p, new AABB(t.a(), t.b()).inflate(1))) if (!ally(p, x) && closestOnSegment(t.a(), t.b(), mid(x)).distanceTo(mid(x)) < 1.1) AstaFx.damage(p, x, 2f, "cursed_edge");
            }
        }
        // Salt Wall
        for (Iterator<Wall> it = s.walls.iterator(); it.hasNext(); ) {
            Wall w = it.next();
            if (now >= w.until()) { it.remove(); continue; }
            Vec3 a = w.c().subtract(w.along().scale(3)), b = w.c().add(w.along().scale(3));
            if (now % 5 == 0) for (int k = 0; k < 3; k++) FxSend.trail(sl, SALT, a.add(0, k, 0), b.add(0, k, 0), 0.15f, 5);
            AABB box = new AABB(a, b.add(0, 3, 0)).inflate(0.6, 0, 0.6);
            for (Projectile pr : sl.getEntitiesOfClass(Projectile.class, box, x -> x.getOwner() != p)) { pr.discard(); FxSend.ring(sl, SALT, pr.position(), 0.1f, 0.5f, 0.04f, 5); }
            if (now % 10 == 0) for (LivingEntity x : enemies(sl, p, box)) if (!ally(p, x)) { AstaFx.damage(p, x, 2f, "salt_lance"); salt(p, x, 1); }
        }
        // Demon Flurry and its ghosts: a storm of black and red cuts at every angle, the blade's afterimages flickering
        // through it, and a closing cross-cut (the damage is the same: every 4 ticks)
        if (now < s.flurryUntil) {
            Vec3 c = r(p, "k6") > 0 ? mid(p) : s.flurryAt;
            if (c != null) {
                java.util.concurrent.ThreadLocalRandom rr = java.util.concurrent.ThreadLocalRandom.current();
                if (now % 2 == 0) {
                    double yaw = rr.nextDouble(Math.PI * 2), tilt = rr.nextDouble(-1.2, 1.2);
                    Vec3 d = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
                    Vec3 u = new Vec3(0, Math.cos(tilt), 0).add(new Vec3(-d.z, 0, d.x).scale(Math.sin(tilt))).normalize();
                    Vec3 at = c.add(rr.nextDouble(-1.2, 1.2), rr.nextDouble(-0.6, 0.9), rr.nextDouble(-1.2, 1.2));
                    FxSend.slash(sl, now % 4 == 0 ? RED : DARK, at, d, u, 1.6f + rr.nextFloat(), 2.8f + rr.nextFloat() * 1.4f, 4, 0, now % 4 == 0 ? 0.45f : 0.9f);
                    // the blade itself, an afterimage across the storm
                    Vec3 bd = new Vec3(rr.nextDouble(-1, 1), rr.nextDouble(-0.6, 0.6), rr.nextDouble(-1, 1)).normalize();
                    FxSend.spike(sl, DARK, at.subtract(bd.scale(1.4)), bd, 2.8f, 0.07f, 3, 0, com.ironspellgrimoire.network.FxPacket.POP);
                    FxSend.spike(sl, Fx.SILVER, at.subtract(bd.scale(1.4)).add(0, 0.05, 0), bd, 2.6f, 0.025f, 3, 0, com.ironspellgrimoire.network.FxPacket.POP);
                }
                if (now % 4 == 0) {
                    float dmg = 3f * (1f + s.poss / 100f);
                    for (LivingEntity x : enemies(sl, p, new AABB(c, c).inflate(3.5))) if (!ally(p, x)) AstaFx.damage(p, x, dmg, "demon_flurry");
                    play(sl, c, "bd_cut", 0.7f, 1.0f + (now % 3) * 0.15f);
                    FxSend.ring(sl, RED, c.add(0, -0.9, 0), 2.6f, 3.2f, 0.02f, 4);
                }
                if (now == s.flurryUntil - 1) {   // the last cut: a great black-and-red cross, and the screen jolts
                    Vec3 look = new Vec3(p.getLookAngle().x, 0, p.getLookAngle().z).normalize();
                    Vec3 side = new Vec3(-look.z, 0, look.x);
                    Vec3 d1 = side.add(0, 1, 0).normalize(), d2 = side.add(0, -1, 0).normalize();
                    FxSend.slash(sl, DARK, c, d1, look, 3.6f, 5.2f, 8, 0, 1.2f);
                    FxSend.slash(sl, RED, c, d2, look, 3.6f, 5.2f, 8, 0, 0.7f);
                    FxSend.impactFrame(sl, c, 3, true, 20);
                    FxSend.shake(sl, c, 0.45f, 6, 18);
                    play(sl, c, "bd_cut", 1.4f, 0.6f);
                }
            }
        }
        if (now < s.ghostUntil && now >= s.flurryUntil && now % 5 == 0) {
            for (int k = 0; k < 3; k++) {
                double a = now * 0.3 + k * Math.PI * 2 / 3;
                Vec3 at = mid(p).add(Math.cos(a) * 2, 0, Math.sin(a) * 2);
                FxSend.slash(sl, DARK, at, new Vec3(-Math.sin(a), 0, Math.cos(a)), new Vec3(0, 1, 0), 0.6f, 1.2f, 5, 0);
            }
            for (LivingEntity x : enemies(sl, p, p.getBoundingBox().inflate(3))) if (!ally(p, x)) AstaFx.damage(p, x, 2f, "demon_flurry");
        }
        if (now % 20 == 0 && !s.sentAwake) { broadcast(p); s.sentAwake = true; }
    }

    /** The demon at the wheel: it picks a creature, turns you to it, runs you at it and swings. */
    static void possessed(ServerPlayer p, S s, long now) {
        ServerLevel sl = p.serverLevel();
        if (!holding(p)) {   // the hand won't let go
            int slot = -1;
            for (int i = 0; i < 9; i++) if (p.getInventory().getItem(i).is(AddonItemRegistry.DEMON_KATANA.get())) slot = i;
            if (slot >= 0) { p.getInventory().selected = slot; p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(slot)); }
            else draw(p, false);
        }
        boolean calm = r(p, "b2") > 0 && !allies(p, 4).isEmpty();
        if (calm) s.takeoverUntil--;   // a voice it knows: it lets go twice as fast
        if (now % 2 == 0) s.target = pick(p, s);
        LivingEntity t = s.target;
        if (t != null && t.isAlive()) {
            if (r(p, "K2") <= 0) p.lookAt(EntityAnchorArgument.Anchor.EYES, mid(t));
            double d = p.distanceTo(t);
            if (d > 2.6) {
                Vec3 dir = new Vec3(t.getX() - p.getX(), 0, t.getZ() - p.getZ()).normalize();
                p.setDeltaMovement(dir.x * 0.38, p.getDeltaMovement().y, dir.z * 0.38);
                p.hurtMarked = true;
            }
            if (d < 3.4 && p.getAttackStrengthScale(0.5f) >= 0.9f) {
                p.attack(t);
                p.swing(InteractionHand.MAIN_HAND, true);
                p.resetAttackStrengthTicker();
            }
        }
        if (now % 10 == 0) FxSend.ring(sl, s.fallen ? SALT : RED, p.position().add(0, 0.05, 0), 0.3f, 1.2f, 0.04f, 10);
        if (now % 40 == 0) play(sl, p.position(), "bd_whisper", 0.6f, 0.6f);
    }

    /** What the demon goes for: what you look at (Surrender), else the nearest creature - family too, unless a voice it
     *  knows or the fallen seraph spares them; never a player the PvP rule protects. */
    static LivingEntity pick(ServerPlayer p, S s) {
        if (r(p, "K2") > 0) return aimed(p, 12, 35);
        boolean spareFamily = r(p, "b2") > 0 || s.fallen;
        LivingEntity t = null; double bd = 100;
        for (LivingEntity x : p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(10), x -> x != p && x.isAlive() && !(x instanceof ArmorStand)
                && !(x instanceof Player op && !com.ironspellgrimoire.AddonConfig.mayHurt(p, op)) && !(spareFamily && ally(p, x)))) {
            double d = x.distanceToSqr(p); if (d < bd) { bd = d; t = x; }
        }
        return t;
    }

    // ------------------------------------------------------------------ the pages
    static boolean free(ServerPlayer p) {
        if (!takenOver(p)) return true;
        p.displayClientMessage(Component.literal("The demon has your hands.").withStyle(ChatFormatting.DARK_RED), true);
        return false;
    }

    /** Demon Draw: the katana into your hand, the demon's eye open. */
    public static void demonDraw(ServerPlayer p, float power) {
        if (!free(p) || !draw(p, true)) return;
        S s = s(p);
        ServerLevel sl = p.serverLevel();
        if (s.warnUntil > 0 && r(p, "d3") > 0) {   // pushed back
            s.poss = Math.max(0f, s.poss - 25f);
            s.warnUntil = 0;
            p.displayClientMessage(Component.literal("You push the demon back.").withStyle(ChatFormatting.GRAY), true);
        } else feed(p, 5f * gainMul(p));
        if (r(p, "k1") > 0) {   // Draw Cut
            Vec3 look = p.getLookAngle();
            FxSend.slash(sl, RED, mid(p).add(look.scale(1.6)), look, new Vec3(0, 1, 0), 2f, 4f, 6, 0);
            for (LivingEntity t : enemies(sl, p, p.getBoundingBox().inflate(4))) if (!ally(p, t) && mid(t).subtract(p.getEyePosition()).normalize().dot(look) > 0.2) AstaFx.damage(p, t, 6f, "demon_draw");
        }
    }

    /** Cursed Edge: a dash along where you look, cutting everything on the way. */
    public static void cursedEdge(ServerPlayer p, float power) {
        if (!free(p)) return;
        if (!holding(p)) { p.displayClientMessage(Component.literal("Cursed Edge needs the katana in your hand - draw it first.").withStyle(ChatFormatting.GRAY), true); return; }
        S s = s(p);
        ServerLevel sl = p.serverLevel();
        long now = sl.getGameTime();
        Vec3 from = p.position();
        Vec3 to;
        boolean back = r(p, "k3") > 0 && now - s.lastEdge <= 40 && s.edgeFrom != null;
        if (back) {
            to = s.edgeFrom; s.lastEdge = -1000;
            var md = io.redspace.ironsspellbooks.api.magic.MagicData.getPlayerMagicData(p);
            md.setMana(md.getMana() + 35);
        } else if (r(p, "b3") > 0 && now - s.allyHurtAt <= 60 && s.allyHurtBy != null && s.allyHurtBy.isAlive() && s.allyHurtBy.distanceToSqr(p) < 256) {
            Vec3 at = s.allyHurtBy.position();
            to = at.subtract(new Vec3(at.x - from.x, 0, at.z - from.z).normalize().scale(1.2));
        } else {
            Vec3 d = new Vec3(p.getLookAngle().x, 0, p.getLookAngle().z).normalize();
            to = clip(sl, p, from.add(0, 0.5, 0), from.add(0, 0.5, 0).add(d.scale(7))).subtract(d.scale(0.5)).add(0, -0.5, 0);
        }
        float k = 1f;
        if (r(p, "k5") > 0 && holding(p) && now - s.stillSince >= 40) { k *= 3f; FxSend.ring(sl, RED, mid(p), 0.2f, 1.6f, 0.08f, 8); }
        if (r(p, "k9") > 0 && s.blood > 0) { k *= 1f + 0.1f * s.blood; s.blood = 0; }
        p.teleportTo(to.x, to.y, to.z);
        p.fallDistance = 0;
        Vec3 a = from.add(0, 1, 0), b = to.add(0, 1, 0);
        FxSend.trail(sl, DARK, a, b, 0.3f, 10);
        FxSend.trail(sl, RED, a, b, 0.06f, 10);
        play(sl, from, "bd_cut", 1.2f, 0.9f);
        for (LivingEntity t : enemies(sl, p, new AABB(a, b).inflate(1.5))) {
            if (ally(p, t) || closestOnSegment(a, b, mid(t)).distanceTo(mid(t)) > 1.5) continue;
            AstaFx.damage(p, t, 8f * k, "cursed_edge");
        }
        if (r(p, "k2") > 0) s.trails.add(new Trail(a, b, now + 60));
        if (r(p, "k10") > 0) {   // Severing Wind
            Vec3 dir = b.subtract(a).lengthSqr() < 1e-3 ? p.getLookAngle() : b.subtract(a).normalize();
            Vec3 end = clip(sl, p, b, b.add(dir.scale(10)));
            FxSend.slash(sl, RED, b.add(dir), dir, new Vec3(0, 1, 0), 1.2f, 2.4f, 8, 1.2f);
            for (LivingEntity t : enemies(sl, p, new AABB(b, end).inflate(1.2))) if (!ally(p, t) && closestOnSegment(b, end, mid(t)).distanceTo(mid(t)) < 1.3) AstaFx.damage(p, t, 6f, "cursed_edge");
        }
        if (!back) { s.lastEdge = now; s.edgeFrom = from; }
    }

    /** Demon's Whisper: take what the demon offers - Possession, and a burst of strength. */
    public static void demonsWhisper(ServerPlayer p, float power) {
        if (!free(p)) return;
        S s = s(p);
        ServerLevel sl = p.serverLevel();
        long now = sl.getGameTime();
        play(sl, p.position(), "bd_whisper", 1.2f, 0.9f);
        if (s.warnUntil > 0 && r(p, "d5") > 0) {   // Inner Duel
            s.warnUntil = 0; s.symUntil = now + 200; s.poss = 70f;
            ModeHud.hide(p, "bd_warning");
            ModeHud.show(p, "bd_symbiosis", 200);
            p.displayClientMessage(Component.literal("You and the demon want the same thing - for now.").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
            FxSend.ring(sl, RED, mid(p), 0.3f, 2.6f, 0.12f, 14);
            BookTree.grow(p, BookTree.BOUND, 3);
            return;
        }
        feed(p, (r(p, "K1") > 0 ? 15f : 30f) * gainMul(p));
        TimedModifiers.apply(p, Attributes.ATTACK_DAMAGE, WHISPER, "Demon's Whisper", 0.5, AttributeModifier.Operation.MULTIPLY_TOTAL, 160);
        ModeHud.show(p, "demons_whisper", 160);
        FxSend.ring(sl, DARK, mid(p), 0.2f, 1.8f, 0.1f, 10);
        if (r(p, "d10") > 0) for (LivingEntity t : enemies(sl, p, p.getBoundingBox().inflate(6))) {
            if (ally(p, t) || boss(t)) continue;
            Vec3 to = p.position().subtract(t.position());
            t.setDeltaMovement(to.normalize().scale(Math.min(1.2, to.length() * 0.25)).add(0, 0.2, 0)); t.hurtMarked = true;
            FxSend.trail(sl, DARK, mid(t), mid(p), 0.05f, 6);
        }
    }

    /** Bond Guard: bind the ally you look at - part of what hurts them comes to you. */
    public static void bondGuard(ServerPlayer p, float power) {
        if (!free(p)) return;
        ServerLevel sl = p.serverLevel();
        Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
        LivingEntity best = null; double bd = 0.9;
        for (LivingEntity a : allies(p, 16)) {
            double d = mid(a).subtract(eye).normalize().dot(look);
            if (d > bd) { bd = d; best = a; }
        }
        if (best == null) { List<LivingEntity> near = allies(p, 8); if (!near.isEmpty()) best = near.get(0); }
        if (best == null) { p.displayClientMessage(Component.literal("No one near to stand for").withStyle(ChatFormatting.GRAY), true); return; }
        link(p, best);
    }

    static void link(ServerPlayer p, LivingEntity a) {
        S s = s(p);
        ServerLevel sl = p.serverLevel();
        int max = r(p, "K6") > 0 ? 3 : 1;
        while (s.links.size() >= max) { Iterator<LivingEntity> it = s.links.keySet().iterator(); it.next(); it.remove(); }
        s.links.put(a, sl.getGameTime() + (r(p, "b1") > 0 ? 400 : 200));
        ModeHud.show(p, "bond_guard", r(p, "b1") > 0 ? 400 : 200, s.links.size());
        FxSend.trail(sl, RED, mid(p), mid(a), 0.08f, 12);
        FxSend.ring(sl, RED, mid(a), 0.2f, 1.2f, 0.06f, 10);
        play(sl, a.position(), "bd_bond", 1f, 1f);
    }

    /** Demon Flurry: a storm of cuts round you for 1 s (2, following you, with Flowing Flurry) - harder the deeper the demon. */
    public static void demonFlurry(ServerPlayer p, float power) {
        if (!free(p)) return;
        S s = s(p);
        long now = p.level().getGameTime();
        s.flurryUntil = now + (r(p, "k6") > 0 ? 40 : 20);
        ModeHud.show(p, "demon_flurry", (int) (s.flurryUntil - now) + (r(p, "k7") > 0 ? 60 : 0));
        s.flurryAt = mid(p);
        if (r(p, "k7") > 0) s.ghostUntil = s.flurryUntil + 60;
        play(p.serverLevel(), p.position(), "bd_cut", 1.2f, 0.8f);
    }

    /** Sinner's Mark: everything that hurt you or yours in the last 10 s is marked - it glows for you, and takes 20% more. */
    public static void sinnersMark(ServerPlayer p, float power) {
        if (!free(p)) return;
        S s = s(p);
        long now = p.level().getGameTime();
        int n = 0;
        for (Map.Entry<LivingEntity, Long> en : s.offenders.entrySet())
            if (now - en.getValue() <= 200 && en.getKey().isAlive()) { mark(p, en.getKey(), 200, false); n++; }
        play(p.serverLevel(), p.position(), "bd_mark", 1f, 1f);
        p.displayClientMessage(Component.literal(n == 0 ? "No one has sinned against you - yet." : n + (n == 1 ? " sinner marked" : " sinners marked")).withStyle(ChatFormatting.DARK_RED), true);
    }

    /** Demon's Reach: the demon's shadow lunges out of you along your look, takes the first foe it finds and drags it onto
     *  your blade - the cut lands as it arrives. (Salt in the Veins: it leaves salt in it, too.) */
    public static void demonsReach(ServerPlayer p, float power) {
        if (!free(p)) return;
        ServerLevel sl = p.serverLevel();
        Vec3 eye = p.getEyePosition(), d = p.getLookAngle();
        Vec3 end = clip(sl, p, eye, eye.add(d.scale(10)));
        LivingEntity got = null; double bd = 1e9;
        for (LivingEntity t : enemies(sl, p, new AABB(eye, end).inflate(1.2)))
            if (!ally(p, t) && closestOnSegment(eye, end, mid(t)).distanceTo(mid(t)) < 1.3 && t.distanceToSqr(p) < bd) { bd = t.distanceToSqr(p); got = t; }
        Vec3 reach = got == null ? end : mid(got);
        play(sl, p.position(), "bd_whisper", 1.1f, 0.6f);
        // the shadow arm: it grows out over three ticks, a red claw at its end
        for (int k = 1; k <= 3; k++) {
            final Vec3 tip = eye.add(reach.subtract(eye).scale(k / 3.0));
            AstaFx.later(k - 1, () -> { FxSend.trail(sl, DARK, eye.add(d.scale(0.6)), tip, 0.22f, 8); FxSend.trail(sl, RED, eye.add(d.scale(0.6)), tip, 0.05f, 8); });
        }
        if (got == null) { AstaFx.later(3, () -> FxSend.slash(sl, RED, reach, d, new Vec3(0, 1, 0), 0.6f, 1.2f, 5, 0)); return; }
        final LivingEntity t = got;
        AstaFx.later(3, () -> {
            if (!t.isAlive()) return;
            FxSend.slash(sl, RED, mid(t), new Vec3(0, -1, 0), d, 0.6f, 1.2f, 5, 0, 0.7f);
            Vec3 to = p.position().add(new Vec3(d.x, 0, d.z).normalize().scale(1.6));
            if (!boss(t)) { t.teleportTo(to.x, p.getY(), to.z); t.setDeltaMovement(Vec3.ZERO); t.hurtMarked = true; }
            FxSend.trail(sl, DARK, reach, mid(t), 0.3f, 8);
            FxSend.slash(sl, DARK, mid(t), rotY(d, 0.6), new Vec3(0, 1, 0), 1.6f, 2.6f, 6, 0, 0.9f);
            FxSend.slash(sl, RED, mid(t), rotY(d, -0.6), new Vec3(0, 1, 0), 1.4f, 2.4f, 6, 0, 0.5f);
            play(sl, t.position(), "bd_cut", 1.2f, 0.8f);
            AstaFx.damage(p, t, 7f, "demons_reach");
            if (r(p, "s1") > 0) salt(p, t, 1);
            feed(p, 3f * gainMul(p));
        });
    }

    /** The Last Seraph: wake the seraph - one great wing, flight, judgment. */
    public static void lastSeraph(ServerPlayer p, float power) {
        if (takenOver(p) && r(p, "s10") <= 0) { free(p); return; }
        awaken(p, false);
    }

    // ------------------------------------------------------------------ the G wheel
    static boolean wheel(ServerPlayer p, int slot) {
        S s = s(p);
        ServerLevel sl = p.serverLevel();
        switch (slot) {
            case 0 -> {
                s.mode = (s.mode + 1) % 3;
                ModeHud.standing(p, new String[]{"bd_restrain", "bd_release"}, s.mode - 1);
                p.displayClientMessage(Component.literal(new String[]{"Balanced: the demon as it is", "Restrain: it fills slowly, and fades even with the blade drawn", "Release: it fills fast, and gives more"}[s.mode]).withStyle(ChatFormatting.DARK_RED), true);
                play(sl, p.position(), "bd_whisper", 0.7f, 1.2f + 0.2f * s.mode);
            }
            case 1 -> {
                for (TamableAnimal ta : sl.getEntitiesOfClass(TamableAnimal.class, p.getBoundingBox().inflate(48), x -> p.getUUID().equals(x.getOwnerUUID()) && !x.isOrderedToSit()))
                    ta.teleportTo(p.getX() + (Math.random() - 0.5) * 3, p.getY(), p.getZ() + (Math.random() - 0.5) * 3);
                for (LivingEntity a : allies(p, 16)) {
                    TimedModifiers.apply(a, Attributes.MOVEMENT_SPEED, CALL, "Call to Family", 0.2, AttributeModifier.Operation.MULTIPLY_TOTAL, 200);
                    a.setAbsorptionAmount(Math.max(a.getAbsorptionAmount(), 4f));
                    FxSend.ring(sl, RED, mid(a), 0.2f, 1f, 0.05f, 10);
                }
                play(sl, p.position(), "bd_bond", 1.2f, 0.8f);
                ModeHud.show(p, "bd_family", 200);
            }
            case 2 -> {
                Vec3 look = new Vec3(p.getLookAngle().x, 0, p.getLookAngle().z).normalize();
                s.walls.add(new Wall(p.position().add(look.scale(3)), new Vec3(-look.z, 0, look.x), sl.getGameTime() + 80));
                ModeHud.show(p, "bd_saltwall", 80);
                play(sl, p.position(), "bd_salt", 1.2f, 0.7f);
            }
            default -> {
                if (awakenings(p) <= 0) { p.displayClientMessage(Component.literal("You have never woken - the wing isn't yours yet.").withStyle(ChatFormatting.GRAY), true); return false; }
                Vec3 from = p.position(), d = p.getLookAngle();
                Vec3 to = clip(sl, p, from.add(0, 1, 0), from.add(0, 1, 0).add(d.scale(10))).subtract(d.scale(0.6)).add(0, -1, 0);
                p.teleportTo(to.x, to.y, to.z);
                p.fallDistance = 0;
                FxSend.trail(sl, SALT, from.add(0, 1.2, 0), to.add(0, 1.2, 0), 0.5f, 10);
                play(sl, from, "bd_seraph", 0.9f, 1.6f);
                for (LivingEntity t : enemies(sl, p, new AABB(from, to).inflate(1.5))) if (!ally(p, t)) { AstaFx.damage(p, t, 6f, "last_seraph"); if (!boss(t)) Kit.push(t, flatAway(t, p), 0.8, 0.3); }
            }
        }
        return true;
    }

    /** For the wheel and the HUD: possession %, takeover, warning, awake, symbiosis (ticks left), mode, awakenings, wing guards. */
    static int[] extra(ServerPlayer p) {
        S s = s(p);
        long now = p.level().getGameTime();
        return new int[]{Math.round(s.poss), (int) Math.max(0, s.takeoverUntil - now), (int) Math.max(0, s.warnUntil - now), (int) Math.max(0, s.awakeUntil - now),
                (int) Math.max(0, s.symUntil - now), s.mode, awakenings(p), s.wingGuards};
    }

    @SubscribeEvent public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        S s = STATE.remove(e.getEntity().getUUID());
        if (s != null && s.flyGranted && e.getEntity() instanceof ServerPlayer p && !p.isCreative() && !p.isSpectator()) { p.getAbilities().mayfly = false; p.getAbilities().flying = false; p.onUpdateAbilities(); }
    }

    // test hooks
    public static void testReset(ServerPlayer p) { S s = STATE.remove(p.getUUID()); if (s != null && s.flyGranted && !p.isCreative()) { p.getAbilities().mayfly = false; p.onUpdateAbilities(); } }
    public static float testPoss(ServerPlayer p) { return s(p).poss; }
    public static void testFeed(ServerPlayer p, float v) { feed(p, v); }
    public static boolean testAwake(ServerPlayer p) { return awake(p); }
    public static int testMode(ServerPlayer p) { return s(p).mode; }
    public static int testLinks(ServerPlayer p) { return s(p).links.size(); }
    public static LivingEntity testPick(ServerPlayer p) { return pick(p, s(p)); }
}
