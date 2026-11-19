package com.ironspellgrimoire.server;

import com.ironspellgrimoire.IronSpellGrimoire;
import com.ironspellgrimoire.entity.FortressDefender;
import com.ironspellgrimoire.entity.FortressTower;
import com.ironspellgrimoire.entity.FortressWall;
import com.ironspellgrimoire.registry.AddonEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The Architect of Ruin's Siege Mode, the Citadel, Rampart and the garrison (server/FortressFx holds the rest).
 *
 * Siege Mode: a square ring of wall blocks round you (half-width 6, 8 or 10 by book level) with one gatehouse on the
 * side you face, towers at the corners and the middle of the two flanks (one per tower room, two Watchtowers to
 * begin with, six at most), and your garrison steps out to hold the middle. 30 s (the Citadel: 20 s, walls twice as
 * tough, every tower, every room's spell once, then the walls fall inward on whoever is beside them).
 *
 * Walls follow the ground (each block finds the floor near your height; a block with no floor is left out) and never
 * touch a block. Every piece is an entity that isn't saved and is cleared when the siege ends, you log out, change
 * world, die or take the book off. Hard limits: 6 towers, 8 defenders, at most 76 wall blocks.
 */
@Mod.EventBusSubscriber(modid = IronSpellGrimoire.MODID)
public final class FortressSiege {
    private FortressSiege() {}

    public static final int SIEGE_LIFE = 600, CITADEL_LIFE = 400, MAX_TOWERS = 6, MAX_DEFENDERS = 8, MAX_BANNERS = 6;
    static final int STONE = Fx.EARTH, BRASS = Fx.GOLD, EMBER = Fx.FIRE, ARCANE = Fx.ICE;

    /** What each player has out: walls, towers, defenders (only ever live entities; cleared with them). */
    static final class Out {
        final List<FortressWall> walls = new ArrayList<>();
        final List<FortressTower> towers = new ArrayList<>();
        final List<FortressDefender> defenders = new ArrayList<>();
        long siegeUntil;
        boolean citadel;
        Vec3 centre;
    }
    static final Map<UUID, Out> OUT = new HashMap<>();
    static Out out(Player p) { return OUT.computeIfAbsent(p.getUUID(), k -> new Out()); }

    public static boolean active(Player p) {
        Out o = OUT.get(p.getUUID());
        return o != null && o.siegeUntil > p.level().getGameTime();
    }

    /** Defenders the garrison can have: 4, +1 per Barracks level, at most 8. */
    public static int cap(Player p) { return Math.min(MAX_DEFENDERS, 4 + FortressFx.roomLevel(p, FortressFx.BARRACKS)); }

    // ================================================================== who's on whose side
    /** Is e on the side of the player with this UUID: the player, their defenders, walls and towers, their teammates. */
    public static boolean friendOf(UUID owner, Entity e) {
        if (owner == null || e == null) return false;
        if (owner.equals(e.getUUID())) return true;
        if (e instanceof FortressDefender d) return owner.equals(d.ownerUUID());
        if (e instanceof FortressWall w) return owner.equals(w.owner);
        if (e instanceof FortressTower t) return owner.equals(t.owner);
        if (e instanceof net.minecraft.world.entity.projectile.Projectile pr && pr.getOwner() != null && pr.getOwner() != e) return friendOf(owner, pr.getOwner());
        Player o = e.level().getPlayerByUUID(owner);
        return o != null && e instanceof Player && o.isAlliedTo(e);
    }

    /** A foe of this player for the fortress's towers and defenders. */
    static boolean foe(UUID owner, ServerPlayer p, LivingEntity t) {
        if (t == null || !t.isAlive() || friendOf(owner, t) || t instanceof net.minecraft.world.entity.decoration.ArmorStand) return false;
        if (t instanceof Player op) return p != null && !op.isCreative() && !op.isSpectator() && com.ironspellgrimoire.AddonConfig.mayHurt(p, op);
        return true;
    }

    /** Is the owner, an ally or one of their defenders standing inside this wall block? Then it gives way. */
    public static boolean someoneInside(FortressWall w) {
        AABB b = w.getBoundingBox().deflate(0.15);
        for (Entity e : w.level().getEntities(w, b, e -> e instanceof LivingEntity && e.isAlive()))
            if (friendOf(w.owner, e)) return true;
        return false;
    }

    // ================================================================== the ground
    /** The floor for a block of wall near y: the first spot (from 3 above to 5 below) with solid ground under it and
     *  room for the wall; null if there isn't one (a cliff, deep water, the inside of a hill). */
    static Vec3 floorAt(ServerLevel sl, int x, int y, int z) {
        for (int dy = 3; dy >= -5; dy--) {
            BlockPos at = new BlockPos(x, y + dy, z);
            BlockState below = sl.getBlockState(at.below());
            if (below.getCollisionShape(sl, at.below()).isEmpty()) continue;
            if (!sl.getBlockState(at).getCollisionShape(sl, at).isEmpty()) continue;
            if (!sl.getBlockState(at.above()).getCollisionShape(sl, at.above()).isEmpty()) continue;
            if (!sl.getFluidState(at).isEmpty() && !sl.getFluidState(at.above()).isEmpty()) continue;   // under water
            return new Vec3(x + 0.5, y + dy, z + 0.5);
        }
        return null;
    }

    // ================================================================== Rampart, Portcullis
    /** Rampart: five blocks of wall across your path, three blocks ahead, for 12 s. */
    static void rampart(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        Vec3 d = FxUtil.flat(p.getLookAngle());
        boolean alongX = Math.abs(d.z) >= Math.abs(d.x);   // the wall runs across the way you face
        Vec3 c = p.position().add(d.scale(3));
        int n = 0;
        for (int k = -2; k <= 2; k++) {
            int x = (int) Math.floor(c.x + (alongX ? k : 0)), z = (int) Math.floor(c.z + (alongX ? 0 : k));
            if (spawnWall(p, sl, x, (int) Math.floor(p.getY()), z, FortressWall.WALL, wallHp(p, false), 240, false, alongX ? 0 : 90) != null) n++;
        }
        if (n == 0) FortressFx.tell(p, "No ground there for a wall");
        OverdriveFx.play(sl, c, "fort_wall", 0.9f, 1.1f);
        FxSend.shake(sl, c, 0.25f, 6, 10);
    }

    /** Portcullis: a three-block gate slams down two blocks ahead, throwing foes back; it stands 6 s. */
    static void portcullis(ServerPlayer p, float m) {
        ServerLevel sl = p.serverLevel();
        Vec3 d = FxUtil.flat(p.getLookAngle());
        boolean alongX = Math.abs(d.z) >= Math.abs(d.x);
        Vec3 c = p.position().add(d.scale(2.5));
        for (int k = -1; k <= 1; k++) {
            int x = (int) Math.floor(c.x + (alongX ? k : 0)), z = (int) Math.floor(c.z + (alongX ? 0 : k));
            spawnWall(p, sl, x, (int) Math.floor(p.getY()), z, FortressWall.WALL, wallHp(p, false), 120, false, alongX ? 0 : 90);
        }
        for (LivingEntity t : Kit.near(sl, p, c.add(0, 1, 0), 3.0)) {
            FortressSpells.hit(p, t, 4 * m, FortressFx.GATEHOUSE);
            Kit.push(t, t.position().subtract(p.position()), 1.1, 0.35);
        }
        FxSend.box(sl, Fx.STEEL, c.add(0, 3.2, 0), alongX ? new Vec3(1, 0, 0) : new Vec3(0, 0, 1), new Vec3(0, 1, 0), 1.6f, 0.1f, 1.4f, 10, 3, com.ironspellgrimoire.network.FxPacket.POP);
        FxSend.shake(sl, c, 0.4f, 6, 12);
        OverdriveFx.play(sl, c, "fort_gate", 1.0f, 1.0f);
    }

    static float wallHp(Player p, boolean grand) {
        return 40f * (1 + 0.2f * FortressFx.roomLevel(p, FortressFx.GATEHOUSE)) * (grand ? 2f : 1f);
    }

    static FortressWall spawnWall(ServerPlayer p, ServerLevel sl, int x, int y, int z, int kind, float hp, int life, boolean grand, float yaw) {
        Vec3 at = floorAt(sl, x, y, z);
        if (at == null) return null;
        if (kind == FortressWall.WALL) {   // never on top of the caster's own side (they'd be shut inside it)
            AABB box = new AABB(at.x - 0.5, at.y, at.z - 0.5, at.x + 0.5, at.y + 2.6, at.z + 0.5);
            for (Entity e : sl.getEntities((Entity) null, box, e -> e instanceof LivingEntity && e.isAlive()))
                if (friendOf(p.getUUID(), e)) return null;
        }
        Out o = out(p);
        if (o.walls.size() > 200) o.walls.removeIf(e -> !e.isAlive());
        FortressWall w = AddonEntities.FORTRESS_WALL.get().create(sl);
        if (w == null) return null;
        w.setup(p.getUUID(), kind, hp, life, grand, yaw);
        w.moveTo(at.x, at.y, at.z, yaw, 0);
        sl.addFreshEntity(w);
        o.walls.add(w);
        return w;
    }

    // ================================================================== Siege Mode and the Citadel
    static int halfWidth(Player p) { int lv = FortressFx.level(p); return lv >= 8 ? 10 : lv >= 4 ? 8 : 6; }

    /** The tower kinds the fortress has: two Watchtowers to begin with, one for each tower room (two at its level 3+). */
    static List<Integer> towerKinds(Player p, boolean citadel) {
        List<Integer> k = new ArrayList<>();
        int[][] rooms = {{FortressFx.WATCHTOWER, FortressTower.ARROWS}, {FortressFx.ARCANUM, FortressTower.BEAM}, {FortressFx.FORGE, FortressTower.FIRE}, {FortressFx.WORKSHOP, FortressTower.BOLTS}};
        for (int[] r : rooms) {
            int lv = FortressFx.roomLevel(p, r[0]);
            if (lv >= 1) k.add(r[1]);
            if (lv >= 3) k.add(r[1]);
        }
        while (k.size() < 2) k.add(0, FortressTower.ARROWS);
        if (citadel) while (k.size() < MAX_TOWERS) k.add(FortressTower.ARROWS);
        return k.subList(0, Math.min(MAX_TOWERS, k.size()));
    }

    static void siege(ServerPlayer p, boolean citadel) {
        endAll(p, false);
        ServerLevel sl = p.serverLevel();
        Out o = out(p);
        int life = citadel ? CITADEL_LIFE : SIEGE_LIFE;
        o.siegeUntil = sl.getGameTime() + life;
        o.citadel = citadel;
        int cx = (int) Math.floor(p.getX()), cz = (int) Math.floor(p.getZ()), y = (int) Math.floor(p.getY());
        o.centre = new Vec3(cx + 0.5, p.getY(), cz + 0.5);
        int R = halfWidth(p);
        Vec3 look = FxUtil.flat(p.getLookAngle());
        // the gate's side: the one you face (0 +z, 1 -z, 2 +x, 3 -x)
        int gate = Math.abs(look.z) >= Math.abs(look.x) ? (look.z >= 0 ? 0 : 1) : (look.x >= 0 ? 2 : 3);
        float hp = wallHp(p, citadel);
        int n = 0;
        for (int k = -R + 1; k <= R - 1; k++) {
            int[][] sides = {{cx + k, cz + R, 0}, {cx + k, cz - R, 1}, {cx + R, cz + k, 2}, {cx - R, cz + k, 3}};
            for (int[] s : sides) {
                boolean gap = s[2] == gate && Math.abs(k) <= 1;
                if (gap) continue;
                if (spawnWall(p, sl, s[0], y, s[1], FortressWall.WALL, hp, life, citadel, s[2] < 2 ? 0 : 90) != null) n++;
            }
        }
        // the gatehouse arch in the gap
        int gx = gate == 2 ? cx + R : gate == 3 ? cx - R : cx, gz = gate == 0 ? cz + R : gate == 1 ? cz - R : cz;
        spawnWall(p, sl, gx, y, gz, FortressWall.GATE, hp, life, citadel, gate < 2 ? 0 : 90);
        // the towers: corners first, then the middle of the two flanks
        List<Integer> kinds = towerKinds(p, citadel);
        int[][] spots = {{cx + R, cz + R}, {cx - R, cz + R}, {cx + R, cz - R}, {cx - R, cz - R},
                gate < 2 ? new int[]{cx + R, cz} : new int[]{cx, cz + R}, gate < 2 ? new int[]{cx - R, cz} : new int[]{cx, cz - R}};
        float range = 16 + (FortressFx.built(p, FortressFx.WATCHTOWER) ? 4 : 0);
        for (int i = 0; i < kinds.size(); i++) spawnTower(p, sl, spots[i][0], y, spots[i][1], kinds.get(i), life, range, citadel);
        // the garrison steps out to hold the middle
        callGarrison(p, o.centre, life, cap(p));
        FortressFx.hint(p, FortressFx.HINT_SIEGE, "Siege! Walls and towers hold for " + life / 20 + " s - fight from the middle, leave by the gate");
        OverdriveFx.play(sl, p.position(), "fort_horn", 1.2f, citadel ? 0.8f : 1f);
        OverdriveFx.play(sl, p.position(), "fort_wall", 1.0f, 0.8f);
        FxSend.shake(sl, p.position(), citadel ? 0.8f : 0.45f, 14, R + 8);
        FxSend.ring(sl, STONE, o.centre.add(0, 0.1, 0), 1, R + 1.5f, 0.2f, 14);
        if (citadel) {
            FxSend.ring(sl, BRASS, o.centre.add(0, 0.2, 0), 1, R + 3f, 0.3f, 18);
            FortressSpells.citadelWave(p);
            AstaFx.later(life - 2, () -> collapse(p));
            Adv.grant(p, "citadel");
        }
        if (n == 0) FortressFx.tell(p, "No ground here for walls");
        FortressFx.sync(p, false);
    }

    static FortressTower spawnTower(ServerPlayer p, ServerLevel sl, int x, int y, int z, int kind, int life, float range, boolean grand) {
        Vec3 at = floorAt(sl, x, y, z);
        if (at == null) return null;
        Out o = out(p);
        o.towers.removeIf(e -> !e.isAlive());   // anything removed some other way doesn't hold a place
        if (o.towers.size() >= MAX_TOWERS) return null;
        FortressTower t = AddonEntities.FORTRESS_TOWER.get().create(sl);
        if (t == null) return null;
        t.setup(p.getUUID(), kind, (kind == FortressTower.BALLISTA ? 40f : 80f) * (grand ? 2f : 1f), life, range, p.getYRot());
        t.moveTo(at.x, at.y, at.z, 0, 0);
        sl.addFreshEntity(t);
        o.towers.add(t);
        FxSend.ring(sl, STONE, at.add(0, 0.1, 0), 0.4f, 1.6f, 0.12f, 10);
        return t;
    }

    /** The Citadel's end: the walls fall inward on whoever stands beside them. */
    static void collapse(ServerPlayer p) {
        Out o = OUT.get(p.getUUID());
        if (o == null || !o.citadel || !p.isAlive()) return;
        ServerLevel sl = p.serverLevel();
        float m = FortressFx.mult(p, -1);
        Set<Integer> hit = new java.util.HashSet<>();
        for (FortressWall w : new ArrayList<>(o.walls)) {
            if (!w.isAlive() || w.kind() != FortressWall.WALL) continue;
            for (LivingEntity t : Kit.near(sl, p, w.position().add(0, 1, 0), 2.5))
                if (hit.add(t.getId())) FortressSpells.hit(p, t, 14 * m, -1);
            if (w.getId() % 3 == 0) FxSend.box(sl, STONE, w.position().add(0, 1.2, 0), new Vec3(1, 0, 0), new Vec3(0, 1, 0), 0.5f, 0.5f, 0.5f, 10, 0, com.ironspellgrimoire.network.FxPacket.POP, 0, new Vec3(0, -0.12, 0), 8);
        }
        FxSend.shake(sl, p.position(), 0.9f, 14, 20);
        OverdriveFx.play(sl, p.position(), "fort_collapse", 1.3f, 0.9f);
        endAll(p, false);
    }

    /** Everything this player has out goes, at once. */
    public static void endAll(ServerPlayer p, boolean quiet) {
        Out o = OUT.remove(p.getUUID());
        if (o == null) return;
        for (FortressWall w : o.walls) if (w.isAlive()) w.discard();
        for (FortressTower t : o.towers) if (t.isAlive()) t.discard();
        for (FortressDefender d : o.defenders) if (d.isAlive()) d.discard();
    }

    public static void wallGone(FortressWall w, boolean broken) {
        Player p = w.ownerPlayer();
        if (p != null) { Out o = OUT.get(p.getUUID()); if (o != null) o.walls.remove(w); }
        if (broken && w.level() instanceof ServerLevel sl) {
            FxSend.box(sl, STONE, w.position().add(0, 0.6, 0), new Vec3(1, 0, 0), new Vec3(0, 1, 0), 0.35f, 0.35f, 0.35f, 8, 0, com.ironspellgrimoire.network.FxPacket.POP, 25, new Vec3(0, -0.08, 0), 8);
            OverdriveFx.play(sl, w.position(), "fort_crumble", 0.7f, 0.9f + ThreadLocalRandom.current().nextFloat() * 0.2f);
        }
    }

    public static void towerGone(FortressTower t, boolean broken) {
        Player p = t.ownerPlayer();
        if (p != null) { Out o = OUT.get(p.getUUID()); if (o != null) o.towers.remove(t); }
        if (broken && t.level() instanceof ServerLevel sl) {
            FxSend.ring(sl, STONE, t.position().add(0, 0.2, 0), 0.5f, 2.2f, 0.2f, 10);
            OverdriveFx.play(sl, t.position(), "fort_crumble", 1.0f, 0.7f);
        }
    }

    public static void defenderGone(FortressDefender d) {
        Player p = d.owner();
        if (p != null) { Out o = OUT.get(p.getUUID()); if (o != null) o.defenders.remove(d); }
        if (d.level() instanceof ServerLevel sl) FxSend.ring(sl, STONE, d.position().add(0, 0.1, 0), 0.3f, 1.0f, 0.1f, 8);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent e) { OUT.clear(); }

    // ================================================================== towers
    public static void towerTick(FortressTower t) {
        if (--t.reload > 0) return;
        if (!(t.ownerPlayer() instanceof ServerPlayer p)) return;
        ServerLevel sl = (ServerLevel) t.level();
        Vec3 top = t.position().add(0, t.kind() == FortressTower.BALLISTA ? 0.8 : t.kind() == FortressTower.BOLTS ? 1.8 : 3.4, 0);
        boolean sure = FortressFx.pair(p, FortressFx.indexOfPair(FortressFx.WATCHTOWER, FortressFx.OBSERVATORY));
        LivingEntity best = null; double bd = t.range * t.range;
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, t.getBoundingBox().inflate(t.range), e -> foe(p.getUUID(), p, e))) {
            if (!(e instanceof net.minecraft.world.entity.monster.Enemy) && e.getLastHurtByMob() == null && !FortressSpells.marked(e) && !(e instanceof Player)) continue;   // leave the peaceful alone
            double dd = e.distanceToSqr(top);
            boolean revealed = FortressSpells.marked(e) || FortressSpells.revealed(p, e);
            if (revealed) dd *= 0.25;   // marked or revealed foes first
            if (dd >= bd) continue;
            if (!(sure && revealed) && !seen(sl, top, FxUtil.mid(e))) continue;
            bd = dd; best = e;
        }
        if (best == null) { t.reload = 5; return; }
        float m = FortressFx.mult(p, -1);
        Vec3 to = FxUtil.mid(best);
        t.setAim((float) Math.toDegrees(Math.atan2(-(to.x - t.getX()), to.z - t.getZ())));
        final LivingEntity tg = best;
        switch (t.kind()) {
            case FortressTower.ARROWS -> {
                t.reload = 15;
                FxSend.trail(sl, STONE, top, to, 0.06f, 4);
                FxSend.beam(sl, BRASS, top, to, 0.03f, 3);
                FortressSpells.towerHit(p, tg, 4 * m);
                OverdriveFx.play(sl, top, "fort_arrow", 0.5f, 1.2f);
            }
            case FortressTower.BEAM -> {
                t.reload = 20;
                FxSend.beam(sl, ARCANE, top, to, 0.14f, 6);
                FxSend.ring(sl, ARCANE, to, 0.2f, 0.9f, 0.05f, 6);
                FortressSpells.towerHit(p, tg, 5 * m);
                OverdriveFx.play(sl, top, "fort_ley", 0.5f, 1.3f);
            }
            case FortressTower.FIRE -> {
                t.reload = 20;
                Kit.orb(sl, EMBER, top, to.subtract(top).scale(1 / 6.0), 0.35f, 6);
                AstaFx.later(6, () -> {
                    if (!tg.isAlive()) return;
                    FortressSpells.towerHit(p, tg, 4 * m);
                    FortressSpells.burnPatch(p, tg.position(), 1.4f, 60, 1.0f * m);
                });
                OverdriveFx.play(sl, top, "fort_fire", 0.5f, 1.1f);
            }
            default -> {   // bolts: the siege platform and the Ballista
                t.reload = t.kind() == FortressTower.BALLISTA ? 20 : 25;
                FxSend.trail(sl, Fx.STEEL, top, to, 0.12f, 5);
                FxSend.box(sl, Fx.STEEL, to, to.subtract(top).normalize(), new Vec3(0, 1, 0), 0.5f, 0.05f, 0.05f, 6, 0, com.ironspellgrimoire.network.FxPacket.POP);
                FortressSpells.towerHit(p, tg, 7 * m);
                Kit.push(tg, to.subtract(top), 0.4, 0.1);
                OverdriveFx.play(sl, top, "fort_bolt", 0.6f, 1.0f);
                if (t.kind() == FortressTower.BALLISTA && t.tickCount % 60 < 20
                        && FortressFx.pair(p, FortressFx.indexOfPair(FortressFx.WORKSHOP, FortressFx.SIEGE_YARD)))   // Workshop + Siege Yard
                    FortressSpells.trebuchet(p, to, 0.6f);
            }
        }
    }

    static boolean seen(ServerLevel sl, Vec3 a, Vec3 b) {
        var hr = sl.clip(new net.minecraft.world.level.ClipContext(a, b, net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, null));
        return hr.getType() == net.minecraft.world.phys.HitResult.Type.MISS;
    }

    // ================================================================== the garrison
    static ListTag garrison(Player p) {
        CompoundTag r = FortressFx.root(p);
        if (!r.contains("garrison", Tag.TAG_LIST)) r.put("garrison", new ListTag());
        return r.getList("garrison", Tag.TAG_COMPOUND);
    }
    static ListTag banners(Player p) {
        CompoundTag r = FortressFx.root(p);
        if (!r.contains("banners", Tag.TAG_LIST)) r.put("banners", new ListTag());
        return r.getList("banners", Tag.TAG_STRING);
    }

    public static final String[] RANK = {"Recruit", "Veteran", "Knight", "Captain"};
    public static final int[] RANK_AT = {0, 25, 75, 200};
    public static final String[] ROLE = {"Footman", "Archer", "Mystic", "Brute", "Bone sentry"};
    static int rankOf(int kills) { int r = 0; for (int i = 0; i < RANK_AT.length; i++) if (kills >= RANK_AT[i]) r = i; return r; }

    /** The role a mob takes in the garrison. */
    public static int roleOf(String type) {
        String id = type.contains(":") ? type.substring(type.indexOf(':') + 1) : type;
        if (Set.of("skeleton", "stray", "pillager", "piglin", "drowned", "wither_skeleton", "bogged").contains(id) && !id.equals("wither_skeleton")) return FortressDefender.ARCHER;
        if (Set.of("witch", "evoker", "illusioner", "blaze", "guardian", "elder_guardian", "shulker").contains(id)) return FortressDefender.MYSTIC;
        EntityType<?> t = ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.tryParse(type));
        if (t != null && DefaultAttributes.hasSupplier(t)) {
            @SuppressWarnings("unchecked")
            var sup = DefaultAttributes.getSupplier((EntityType<? extends LivingEntity>) t);
            if (sup.getBaseValue(Attributes.MAX_HEALTH) >= 40) return FortressDefender.BRUTE;
        }
        return FortressDefender.FOOTMAN;
    }

    public static String typeName(String type) {
        EntityType<?> t = ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.tryParse(type));
        return t == null ? type : t.getDescription().getString();
    }

    /** A defeated hostile mob leaves a banner now and then (a boss always does): claim it in the Garrison tab. */
    static void maybeBanner(ServerPlayer p, LivingEntity t, boolean boss) {
        if (!boss && ThreadLocalRandom.current().nextFloat() >= 0.10f) return;
        String type = String.valueOf(ForgeRegistries.ENTITY_TYPES.getKey(t.getType()));
        if (boss) type = "minecraft:iron_golem";   // a boss's banner brings a brute you can field, not the boss
        ListTag b = banners(p);
        if (b.size() >= MAX_BANNERS) b.remove(0);   // the oldest unclaimed banner is lost
        b.add(net.minecraft.nbt.StringTag.valueOf(type));
        FortressFx.say(p, "A banner from the " + typeName(type) + " - claim it in the Garrison tab.");
        FortressFx.hint(p, FortressFx.HINT_BANNER, "You found a banner - open The Fortress, Garrison tab, to claim it");
        FxSend.ring(p.serverLevel(), BRASS, t.position().add(0, 0.1, 0), 0.3f, 1.2f, 0.12f, 12);
    }

    static void claim(ServerPlayer p, int i) {
        ListTag b = banners(p), g = garrison(p);
        if (i < 0 || i >= b.size()) return;
        if (g.size() >= cap(p)) { FortressFx.tell(p, "Your garrison is full (" + cap(p) + ") - build or upgrade the Barracks, or dismiss someone"); return; }
        String type = b.getString(i);
        b.remove(i);
        CompoundTag r = FortressFx.root(p);
        int id = r.getInt("gid") + 1;
        r.putInt("gid", id);
        CompoundTag e = new CompoundTag();
        e.putInt("id", id);
        e.putString("type", type);
        e.putInt("role", roleOf(type));
        e.putInt("kills", 0);
        e.putInt("post", -1);
        g.add(e);
        FortressFx.say(p, "The " + typeName(type) + " joins your garrison as a " + ROLE[roleOf(type)] + ".");
        if (g.size() >= 8) Adv.grant(p, "full_garrison");
    }

    static void release(ServerPlayer p, int i) {
        ListTag b = banners(p);
        if (i >= 0 && i < b.size()) b.remove(i);
    }

    static void dismiss(ServerPlayer p, int id) {
        ListTag g = garrison(p);
        for (int i = 0; i < g.size(); i++) if (g.getCompound(i).getInt("id") == id) { g.remove(i); return; }
    }

    /** Post a defender in a room (a small bonus for that room's spell); -1 takes it off its post. */
    static void post(ServerPlayer p, int id, int room) {
        if (room >= 0 && !FortressFx.built(p, room)) return;
        ListTag g = garrison(p);
        for (int i = 0; i < g.size(); i++) {
            CompoundTag e = g.getCompound(i);
            if (room >= 0 && e.getInt("post") == room) e.putInt("post", -1);   // one per room
        }
        for (int i = 0; i < g.size(); i++) if (g.getCompound(i).getInt("id") == id) g.getCompound(i).putInt("post", room);
    }

    /** A defender posted in this room (its rank: -1 if none). */
    public static int postedRank(Player p, int room) {
        ListTag g = garrison(p);
        for (int i = 0; i < g.size(); i++) if (g.getCompound(i).getInt("post") == room) return rankOf(g.getCompound(i).getInt("kills"));
        return -1;
    }

    /** Bring defenders out round a spot: the garrison first, plain stone guards if it's short. */
    static List<FortressDefender> callGarrison(ServerPlayer p, Vec3 at, int life, int count) {
        ServerLevel sl = p.serverLevel();
        Out o = out(p);
        List<FortressDefender> made = new ArrayList<>();
        o.defenders.removeIf(e -> !e.isAlive());
        ListTag g = garrison(p);
        java.util.Set<Integer> already = new java.util.HashSet<>();
        for (FortressDefender d : o.defenders) if (d.isAlive()) { already.add(d.gid); d.post = at; d.life = Math.max(d.life, d.tickCount + life); }
        int room = MAX_DEFENDERS - o.defenders.size();
        for (int i = 0; i < g.size() && made.size() < Math.min(count, room); i++) {
            CompoundTag e = g.getCompound(i);
            if (already.contains(e.getInt("id"))) continue;
            FortressDefender d = spawnDefender(p, sl, e.getString("type"), e.getInt("role"), rankOf(e.getInt("kills")), e.getInt("id"), at, life);
            if (d != null) made.add(d);
        }
        return made;
    }

    static FortressDefender spawnDefender(ServerPlayer p, ServerLevel sl, String type, int role, int rank, int gid, Vec3 at, int life) {
        Out o = out(p);
        o.defenders.removeIf(e -> !e.isAlive());
        if (o.defenders.size() >= MAX_DEFENDERS) return null;
        FortressDefender d = AddonEntities.FORTRESS_DEFENDER.get().create(sl);
        if (d == null) return null;
        float base = role == FortressDefender.BRUTE ? 60 : role == FortressDefender.SENTRY ? 30 : role == FortressDefender.FOOTMAN ? 30 : 22;
        float dmg = role == FortressDefender.BRUTE ? 8 : role == FortressDefender.SENTRY ? 5 : role == FortressDefender.FOOTMAN ? 5 : 4;
        float m = FortressFx.mult(p, -1) * (FortressFx.built(p, FortressFx.TRAINING) ? 1.1f : 1f);
        d.setup(p.getUUID(), type, role, rank, gid, at, life, base * (1 + 0.25f * rank), dmg * (1 + 0.2f * rank) * m);
        double a = (gid * 2.39996) % (Math.PI * 2);
        Vec3 spot = at.add(Math.cos(a) * 2, 0, Math.sin(a) * 2);
        Vec3 floor = floorAt(sl, (int) Math.floor(spot.x), (int) Math.floor(at.y), (int) Math.floor(spot.z));
        if (floor == null) floor = at;
        d.moveTo(floor.x, floor.y, floor.z, p.getYRot(), 0);
        sl.addFreshEntity(d);
        o.defenders.add(d);
        FxSend.ring(sl, STONE, floor.add(0, 0.1, 0), 0.2f, 1.1f, 0.12f, 10);
        return d;
    }

    /** A temporary guard that isn't from the garrison (Call Guard or Drill Charge with too few, the Crypt's sentries). */
    static FortressDefender guard(ServerPlayer p, String type, int role, Vec3 at, int life) {
        int gid = 1000 + out(p).defenders.size() + ThreadLocalRandom.current().nextInt(1000);
        return spawnDefender(p, p.serverLevel(), type, role, 0, gid, at, life);
    }

    static List<FortressDefender> defendersOut(Player p) {
        Out o = OUT.get(p.getUUID());
        if (o == null) return new ArrayList<>();   // callers add to it
        o.defenders.removeIf(d -> !d.isAlive());
        return new ArrayList<>(o.defenders);
    }

    public static void defenderTick(FortressDefender d) {
        Player p = d.owner();
        if (p == null) return;
        // Barracks + Chapel: defenders slowly heal
        if (FortressFx.pair(p, FortressFx.indexOfPair(FortressFx.BARRACKS, FortressFx.CHAPEL)) && d.getHealth() < d.getMaxHealth()) d.heal(1.0f);
    }

    public static boolean defenderStrike(FortressDefender d, Entity target) {
        if (!(d.owner() instanceof ServerPlayer p) || !(target instanceof LivingEntity t) || !foe(p.getUUID(), p, t)) return false;
        float dmg = (float) d.getAttributeValue(Attributes.ATTACK_DAMAGE);
        if (d.focus == t && d.drilling && FortressFx.pair(p, FortressFx.indexOfPair(FortressFx.WAR_ROOM, FortressFx.TRAINING)) && FortressSpells.marked(t)) dmg *= 2;
        boolean hit = t.hurt(d.damageSources().indirectMagic(d, p), dmg * FortressSpells.markBonus(p, t));
        if (hit) {
            Kit.push(t, t.position().subtract(d.position()), d.role() == FortressDefender.BRUTE ? 0.6 : 0.25, 0.1);
            if (d.level() instanceof ServerLevel sl) FxSend.slash(sl, STONE, FxUtil.mid(t), d.getLookAngle(), new Vec3(0, 1, 0), 0.6f, 0.9f, 4, 0);
        }
        return hit;
    }

    public static void defenderShot(FortressDefender d, LivingEntity t) {
        if (!(d.owner() instanceof ServerPlayer p) || !foe(p.getUUID(), p, t)) return;
        ServerLevel sl = (ServerLevel) d.level();
        Vec3 from = d.getEyePosition(), to = FxUtil.mid(t);
        float dmg = (float) d.getAttributeValue(Attributes.ATTACK_DAMAGE) * FortressSpells.markBonus(p, t);
        if (d.role() == FortressDefender.MYSTIC) {
            FxSend.beam(sl, ARCANE, from, to, 0.1f, 5);
            FxSend.ring(sl, ARCANE, to, 0.2f, 0.8f, 0.05f, 6);
            t.hurt(d.damageSources().indirectMagic(d, p), dmg * 1.2f);
        } else {
            FxSend.trail(sl, STONE, from, to, 0.05f, 4);
            t.hurt(d.damageSources().indirectMagic(d, p), dmg);
        }
        d.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
    }

    /** A garrison defender's kill: its rank grows with them. */
    static void defenderKill(ServerPlayer p, FortressDefender d) {
        if (d.gid < 0 || d.gid >= 1000) return;
        ListTag g = garrison(p);
        for (int i = 0; i < g.size(); i++) {
            CompoundTag e = g.getCompound(i);
            if (e.getInt("id") != d.gid) continue;
            int before = rankOf(e.getInt("kills"));
            e.putInt("kills", e.getInt("kills") + 1);
            int after = rankOf(e.getInt("kills"));
            if (after > before) {
                FortressFx.say(p, "Your " + typeName(e.getString("type")) + " rose to " + RANK[after] + ".");
                if (after == 3) Adv.grant(p, "captain");
            }
        }
    }

    // ================================================================== test hooks
    public static void testBanner(ServerPlayer p, String type) { banners(p).add(net.minecraft.nbt.StringTag.valueOf(type)); }
    public static int testWalls(ServerPlayer p) { Out o = OUT.get(p.getUUID()); return o == null ? 0 : (int) o.walls.stream().filter(Entity::isAlive).count(); }
    public static int testTowers(ServerPlayer p) { Out o = OUT.get(p.getUUID()); return o == null ? 0 : (int) o.towers.stream().filter(Entity::isAlive).count(); }
    public static int testDefenders(ServerPlayer p) { return defendersOut(p).size(); }
    public static int testGarrison(ServerPlayer p) { return garrison(p).size(); }
    public static List<FortressWall> testWallList(ServerPlayer p) { Out o = OUT.get(p.getUUID()); return o == null ? List.of() : new ArrayList<>(o.walls); }
    public static void testKills(ServerPlayer p, int id, int kills) {
        ListTag g = garrison(p);
        for (int i = 0; i < g.size(); i++) if (g.getCompound(i).getInt("id") == id) g.getCompound(i).putInt("kills", kills);
    }
}
