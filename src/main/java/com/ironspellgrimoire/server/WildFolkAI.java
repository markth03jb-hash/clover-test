package com.ironspellgrimoire.server;

import com.ironspellgrimoire.IronSpellGrimoire;
import com.ironspellgrimoire.entity.WildFolk;
import com.ironspellgrimoire.entity.WildFolk.Family;
import com.ironspellgrimoire.entity.WildFolk.Kind;
import com.ironspellgrimoire.entity.WildFolk.Role;
import com.ironspellgrimoire.registry.AddonEntities;
import com.ironspellgrimoire.registry.AddonItemRegistry;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * What the wild families do (entity/WildFolk). Nothing here is a status effect or touches a block.
 * <ul>
 *   <li><b>Casters</b> mark the ground under you (a ring that fills), then strike there 1 s later: the Mire Hexer's
 *       marsh-light, the Stonecaller's stone spike (it throws you up), the Sandwraith's lash of sand (it pushes).</li>
 *   <li><b>Leaders</b> rally their family every 10 s (within 12 blocks: +20% damage, +15% speed for 6 s) and have a move
 *       of their own: the Fen Matron mends her family, the Peak Warden slams the ground, the Sun Herald calls a flare.
 *       A leader that appears in the wild brings two of its grunts and a caster.</li>
 *   <li><b>The Gloamstalker</b> comes at night for a mage of threat rank 3 or more: it steps behind its quarry every
 *       8 s, takes 40% less from the school of magic that mage casts most, and fades at dawn.</li>
 *   <li><b>Drops:</b> things the Grimoire Altar binds - leaders sometimes leave a Swift Bookmark (the Mirefolk), a Silver
 *       Clasp (the Stonefolk) or a Wind Feather (the Sandwraiths); the Gloamstalker a Mana Crystal Binding or a Bloodstone.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = IronSpellGrimoire.MODID)
public final class WildFolkAI {
    private WildFolkAI() {}

    static final UUID RALLY_DMG = UUID.fromString("f01c0000-0000-4000-8000-000000000001"), RALLY_SPD = UUID.fromString("f01c0000-0000-4000-8000-000000000002");
    static final int[] PAL = {Fx.WIND, Fx.STEEL, Fx.GOLD, Fx.DARK, Fx.ICE, Fx.VOID};
    static final UUID CHILL = UUID.fromString("f01c0000-0000-4000-8000-000000000003");
    public static int testCasts, testRallies, testBlinks;

    static int pal(WildFolk m) { return PAL[m.kind.family.ordinal()]; }

    public static void tick(WildFolk m) {
        if (!(m.level() instanceof ServerLevel sl)) return;
        LivingEntity t = m.getTarget();
        if (m.kind.role == Role.LEADER && --m.cryCd <= 0 && t != null && t.isAlive() && m.distanceToSqr(t) < 20 * 20) { m.cryCd = 200; rally(m, sl, t); }
        if (m.kind.role == Role.HUNTER) hunter(m, sl, t);
    }

    // ------------------------------------------------------------------ casters
    public static void cast(WildFolk m, LivingEntity t) {
        if (!(m.level() instanceof ServerLevel sl)) return;
        testCasts++;
        Vec3 at = t.position();
        int T = m.kind.family == Family.HOLLOW ? 12 : 20;   // the Hollowfolk strike out of the dark: less warning
        ZenonAI.warnCircle(sl, at, 1.8f, T);
        float dmg = (float) m.getAttributeValue(Attributes.ATTACK_DAMAGE) * 2f;
        Family f = m.kind.family;
        FxUtil.play(sl, m.position(), f == Family.MIRE || f == Family.FROST ? "fore_warn" : f == Family.STONE || f == Family.HOLLOW ? "vec_pulse" : "vec_wind", 0.8f, f == Family.MIRE ? 0.7f : f == Family.HOLLOW ? 0.5f : 1f);
        Kit.orb(sl, pal(m), m.position().add(0, m.getBbHeight() + 0.3, 0), Vec3.ZERO, 0.4f, 20);
        AstaFx.later(T, () -> {
            if (!m.isAlive()) return;
            switch (f) {
                case FROST -> { FxSend.spike(sl, Fx.ICE, at, new Vec3(0, 1, 0), 1.8f, 0.35f, 14, 2, com.ironspellgrimoire.network.FxPacket.GROUND); FxSend.ring(sl, Fx.ICE, at.add(0, 0.1, 0), 0.3f, 1.9f, 0.1f, 12); }
                case HOLLOW -> { Kit.orb(sl, Fx.VOID, at.add(0, 1, 0), Vec3.ZERO, 0.9f, 10); FxSend.ring(sl, Fx.VOID, at.add(0, 0.1, 0), 1.9f, 0.3f, 0.1f, 10); }
                case MIRE -> { Kit.orb(sl, Fx.WIND, at.add(0, 0.6, 0), new Vec3(0, 0.05, 0), 0.8f, 14); FxSend.ring(sl, Fx.WIND, at.add(0, 0.1, 0), 0.3f, 1.9f, 0.1f, 12); }
                case STONE -> FxSend.spike(sl, Fx.STEEL, at, new Vec3(0, 1, 0), 2.2f, 0.45f, 12, 2, com.ironspellgrimoire.network.FxPacket.GROUND);
                default -> FxSend.beam(sl, Fx.GOLD, at.add(0, 6, 0), at, 0.4f, 8);
            }
            for (LivingEntity v : sl.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(1.8, 2.2, 1.8), v -> v instanceof ServerPlayer p && !p.isSpectator() && !p.isCreative())) {
                v.hurt(m.damageSources().indirectMagic(m, m), dmg);
                if (f == Family.STONE) { v.setDeltaMovement(v.getDeltaMovement().add(0, 0.55, 0)); v.hurtMarked = true; }
                if (f == Family.FROST) TimedModifiers.apply(v, Attributes.MOVEMENT_SPEED, CHILL, "Chilled", -0.3, AttributeModifier.Operation.MULTIPLY_TOTAL, 60);
                if (f == Family.SAND) { Vec3 push = v.position().subtract(m.position()); push = new Vec3(push.x, 0, push.z); if (push.lengthSqr() > 1e-4) { v.setDeltaMovement(v.getDeltaMovement().add(push.normalize().scale(0.8))); v.hurtMarked = true; } }
            }
        });
    }

    // ------------------------------------------------------------------ leaders
    static void rally(WildFolk m, ServerLevel sl, LivingEntity t) {
        testRallies++;
        for (WildFolk k : sl.getEntitiesOfClass(WildFolk.class, m.getBoundingBox().inflate(12), k -> k.isAlive() && k.kind.family == m.kind.family)) {
            TimedModifiers.apply(k, Attributes.ATTACK_DAMAGE, RALLY_DMG, "Rally", 0.2, AttributeModifier.Operation.MULTIPLY_TOTAL, 120);
            TimedModifiers.apply(k, Attributes.MOVEMENT_SPEED, RALLY_SPD, "Rally", 0.15, AttributeModifier.Operation.MULTIPLY_TOTAL, 120);
            if (m.kind == Kind.FEN_MATRON) k.heal(4f);
        }
        FxSend.ring(sl, pal(m), m.position().add(0, 0.1, 0), 0.4f, 12f, 0.1f, 16);
        sl.playSound(null, m.blockPosition(), SoundEvents.EVOKER_PREPARE_WOLOLO, SoundSource.HOSTILE, 1f, m.kind == Kind.FEN_MATRON ? 0.7f : m.kind == Kind.PEAK_WARDEN ? 0.5f : 1.2f);
        if (m.kind == Kind.DEEPWARDEN) {   // its lantern-heart pulls everyone close to it
            FxSend.ring(sl, Fx.VOID, m.position().add(0, 1, 0), 10f, 0.5f, 0.1f, 16);
            for (ServerPlayer p : sl.getEntitiesOfClass(ServerPlayer.class, m.getBoundingBox().inflate(10))) {
                if (p.isSpectator() || p.isCreative()) continue;
                Vec3 in = m.position().subtract(p.position()); in = new Vec3(in.x, 0, in.z);
                if (in.lengthSqr() > 4) { p.setDeltaMovement(p.getDeltaMovement().add(in.normalize().scale(1.1)).add(0, 0.2, 0)); p.hurtMarked = true; }
            }
        }
        if (m.kind == Kind.PEAK_WARDEN || m.kind == Kind.SUN_HERALD || m.kind == Kind.HOARFROST_JARL) {
            Vec3 at = m.kind == Kind.SUN_HERALD ? t.position() : m.position();
            float r = m.kind == Kind.SUN_HERALD ? 3.2f : 4.5f;
            ZenonAI.warnCircle(sl, at, r, 18);
            AstaFx.later(18, () -> {
                if (!m.isAlive()) return;
                FxSend.ring(sl, pal(m), at.add(0, 0.1, 0), 0.3f, r, 0.15f, 12);
                if (m.kind == Kind.SUN_HERALD) FxSend.beam(sl, Fx.GOLD, at.add(0, 12, 0), at, 1.2f, 10);
                FxSend.shake(sl, at, 0.4f, 8, 16);
                for (ServerPlayer p : sl.getEntitiesOfClass(ServerPlayer.class, new AABB(at, at).inflate(r, 2.5, r))) {
                    if (p.isSpectator() || p.isCreative()) continue;
                    p.hurt(m.damageSources().mobAttack(m), m.kind == Kind.SUN_HERALD ? 5f : 6f);
                    if (m.kind == Kind.HOARFROST_JARL) TimedModifiers.apply(p, Attributes.MOVEMENT_SPEED, CHILL, "Chilled", -0.35, AttributeModifier.Operation.MULTIPLY_TOTAL, 80);
                    Vec3 push = p.position().subtract(at); push = push.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : new Vec3(push.x, 0, push.z).normalize();
                    p.setDeltaMovement(push.scale(0.9).add(0, 0.35, 0)); p.hurtMarked = true;
                }
            });
        }
    }

    /** A leader that appears in the wild brings its family: two grunts and a caster. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide() || e.loadedFromDisk() || !(e.getEntity() instanceof WildFolk m) || m.kind.role != Role.LEADER) return;
        if (m.getPersistentData().getBoolean("isg_folk_band") || !(e.getLevel() instanceof ServerLevel sl)) return;
        bandUp(m, sl);
    }

    /** During a Blood Moon, a grunt that appears in the wild may bring its leader. */
    @SubscribeEvent
    public static void onGruntJoin(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide() || e.loadedFromDisk() || !(e.getEntity() instanceof WildFolk m) || m.kind.role != Role.GRUNT) return;
        if (m.getPersistentData().getBoolean("isg_folk_band") || !(e.getLevel() instanceof ServerLevel sl) || !BloodMoon.on(sl.getServer())) return;
        m.getPersistentData().putBoolean("isg_folk_band", true);
        if (ThreadLocalRandom.current().nextFloat() < 0.15f) {
            Kind lead = Kind.values()[m.kind.ordinal() + 2];
            AstaFx.later(1, () -> { WildFolk l = spawnNear(sl, lead, m.position(), 2); if (l != null) bandUp(l, sl); });
        }
    }

    static void bandUp(WildFolk m, ServerLevel sl) {
        m.getPersistentData().putBoolean("isg_folk_band", true);
        if (m.getPersistentData().getBoolean("isg_folk_banded")) return;
        m.getPersistentData().putBoolean("isg_folk_banded", true);
        Kind grunt = Kind.values()[m.kind.ordinal() - 2], caster = Kind.values()[m.kind.ordinal() - 1];
        AstaFx.later(1, () -> { for (Kind k : new Kind[]{grunt, grunt, caster}) spawnNear(sl, k, m.position(), 3); });
    }

    static WildFolk spawnNear(ServerLevel sl, Kind k, Vec3 c, double r) {
        WildFolk w = type(k).create(sl);
        if (w == null) return null;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        double a = rnd.nextDouble(Math.PI * 2);
        int x = (int) Math.floor(c.x + Math.cos(a) * r), z = (int) Math.floor(c.z + Math.sin(a) * r);
        int y = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (Math.abs(y - c.y) > 6) y = (int) c.y;
        w.moveTo(x + 0.5, y, z + 0.5, rnd.nextFloat() * 360f, 0);
        w.getPersistentData().putBoolean("isg_folk_band", true);
        w.finalizeSpawn(sl, sl.getCurrentDifficultyAt(BlockPos.containing(w.position())), MobSpawnType.REINFORCEMENT, null, null);
        sl.addFreshEntity(w);
        return w;
    }

    @SuppressWarnings("unchecked")
    public static net.minecraft.world.entity.EntityType<WildFolk> type(Kind k) { return (net.minecraft.world.entity.EntityType<WildFolk>) AddonEntities.FOLK.get(k).get(); }

    // ------------------------------------------------------------------ the night hunter
    static void hunter(WildFolk m, ServerLevel sl, LivingEntity t) {
        if (m.tickCount % 40 == 0 && sl.isDay() && sl.canSeeSky(m.blockPosition())) {   // dawn: it fades
            FxSend.ring(sl, Fx.DARK, m.position().add(0, 0.1, 0), 1.5f, 0.2f, 0.06f, 14);
            m.discard();
            return;
        }
        if (t == null || !t.isAlive() || --m.blinkCd > 0 || m.distanceToSqr(t) > 20 * 20) return;
        m.blinkCd = 160;
        Vec3 back = new Vec3(t.getLookAngle().x, 0, t.getLookAngle().z);
        back = back.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : back.normalize().scale(-2.4);
        Vec3 to = t.position().add(back);
        if (!sl.noCollision(m, m.getBoundingBox().move(to.subtract(m.position())))) return;
        testBlinks++;
        FxSend.ring(sl, Fx.DARK, m.position().add(0, 0.1, 0), 1.2f, 0.2f, 0.05f, 10);
        m.teleportTo(to.x, to.y, to.z);
        FxSend.ring(sl, Fx.DARK, to.add(0, 0.1, 0), 0.2f, 1.2f, 0.05f, 10);
        sl.playSound(null, m.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 0.6f, 0.6f);
    }

    /** The Gloamstalker takes 40% less from the school its quarry casts most. */
    public static float taken(WildFolk m, DamageSource src, float amount) {
        if (m.kind.role == Role.HUNTER && src.getEntity() instanceof ServerPlayer p && src instanceof io.redspace.ironsspellbooks.damage.SpellDamageSource sds
                && sds.spell() != null && sds.spell().getSchoolType().getId().getPath().equals(SchoolUse.top(p))) return amount * 0.6f;
        return amount;
    }

    static final Map<UUID, Long> NEXT_HUNT = new HashMap<>();

    /** At night, now and then, a Gloamstalker comes for a mage of threat rank 3 or more (one at a time). */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % 200 != 0 || p.isSpectator() || p.isCreative()) return;
        if (!EliteMobs.AUTO || !com.ironspellgrimoire.AddonConfig.elites()) return;
        ServerLevel sl = p.serverLevel();
        if (sl.dimension() != net.minecraft.world.level.Level.OVERWORLD || sl.isDay() || EliteMobs.threat(p) < 3) return;
        long now = sl.getGameTime();
        if (now < NEXT_HUNT.getOrDefault(p.getUUID(), 0L)) return;
        NEXT_HUNT.put(p.getUUID(), now + 2400);
        if (ThreadLocalRandom.current().nextFloat() > 0.3f) return;
        if (!sl.getEntitiesOfClass(WildFolk.class, p.getBoundingBox().inflate(64), w -> w.kind.role == Role.HUNTER).isEmpty()) return;
        WildFolk w = spawnNear(sl, Kind.GLOAMSTALKER, p.position(), 26);
        if (w == null) return;
        w.setTarget(p);
        p.displayClientMessage(Component.literal("Something in the dark has chosen you...").withStyle(ChatFormatting.DARK_BLUE, ChatFormatting.ITALIC), true);
    }

    // ------------------------------------------------------------------ what they leave
    public static void drops(WildFolk m, DamageSource src, int looting) {
        if (!(src.getEntity() instanceof ServerPlayer)) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Kind k = m.kind;
        switch (k.family) {
            case MIRE -> { if (r.nextFloat() < 0.5f) m.spawnAtLocation(new ItemStack(Items.SLIME_BALL, 1 + r.nextInt(2 + looting))); }
            case STONE -> { if (r.nextFloat() < 0.5f) m.spawnAtLocation(new ItemStack(Items.IRON_NUGGET, 2 + r.nextInt(3 + looting))); }
            case SAND -> { if (r.nextFloat() < 0.5f) m.spawnAtLocation(new ItemStack(Items.GOLD_NUGGET, 2 + r.nextInt(3 + looting))); }
            case FROST -> { if (r.nextFloat() < 0.5f) m.spawnAtLocation(new ItemStack(Items.SNOWBALL, 2 + r.nextInt(3 + looting))); }
            case HOLLOW -> { if (r.nextFloat() < 0.35f) m.spawnAtLocation(new ItemStack(Items.AMETHYST_SHARD, 1 + r.nextInt(2 + looting))); }
            default -> {}
        }
        if (k.role == Role.LEADER) {
            m.spawnAtLocation(new ItemStack(Items.EMERALD, 2 + r.nextInt(3)));
            if (r.nextFloat() < 0.25f + 0.05f * looting)
                m.spawnAtLocation(new ItemStack((k.family == Family.MIRE ? AddonItemRegistry.SWIFT_BOOKMARK : k.family == Family.STONE ? AddonItemRegistry.SILVER_CLASP
                        : k.family == Family.SAND ? AddonItemRegistry.WIND_FEATHER : k.family == Family.FROST ? AddonItemRegistry.SILVER_CLASP : AddonItemRegistry.SWIFT_BOOKMARK).get()));
            if (r.nextFloat() < 0.08f + 0.02f * looting) m.spawnAtLocation(FolkCharms.charm(k.family));   // its charm: rare
        }
        if (k.role == Role.HUNTER) {
            m.spawnAtLocation(new ItemStack(Items.EMERALD, 4 + r.nextInt(4)));
            if (r.nextFloat() < 0.35f + 0.05f * looting) m.spawnAtLocation(new ItemStack((r.nextBoolean() ? AddonItemRegistry.MANA_CRYSTAL_BINDING : AddonItemRegistry.BLOODSTONE).get()));
        }
    }
}
