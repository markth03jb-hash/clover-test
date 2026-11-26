package com.ironspellgrimoire.server;

import static com.ironspellgrimoire.server.FxUtil.*;

import com.ironspellgrimoire.network.FxPacket;
import io.redspace.ironsspellbooks.api.util.Utils;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * The weight of a hit, shared by every spell (called from AstaFx.damage, which they all land through): a white flash at
 * the point of impact, a shock ring opening square to the blow in the grimoire's colour, sparks thrown on past the
 * target the way the blow was going, and - for the heavy ones - a jolt of the screen and a second, wider ring.
 * It scales with the damage and is rationed (one per target every few ticks, a cap per tick), so a barrage reads as a
 * rhythm of hits instead of a blur.
 */
public final class HitFx {
    private HitFx() {}

    private static final Map<Integer, Long> LAST = new HashMap<>();   // target id -> game time of its last hit effect
    private static long tick = -1;
    private static int thisTick = 0;

    static void land(ServerPlayer p, LivingEntity t, float dmg) {
        if (dmg < 1.5f || !(t.level() instanceof ServerLevel sl)) return;
        long now = sl.getGameTime();
        if (now != tick) { tick = now; thisTick = 0; if (LAST.size() > 400) LAST.clear(); }
        Long last = LAST.get(t.getId());
        boolean heavy = dmg >= 9f;
        if (last != null && now - last < (heavy ? 2 : 4)) return;
        if (thisTick >= 10) return;
        thisTick++;
        LAST.put(t.getId(), now);

        int pal = Fx.SILVER;
        net.minecraft.world.item.Item bookItem = null;
        try {
            ItemStack book = Utils.getPlayerSpellbookStack(p);
            GrimoireMastery.Book b = book == null ? null : GrimoireMastery.bookOf(book);
            if (b != null) { pal = b.palette(); bookItem = book.getItem(); }
            if (b != null && GrimoireMastery.BOOKS.indexOf(b) == 20) return;   // the Blood Grimoire draws its own hits (blood, or ice under the Moon)
            if (Moves.inMode(p, LightFx.MODE)) pal = 31;   // Demon Light: black light with a white sheen
        } catch (Throwable ignored) { }

        float s = Math.max(0.5f, Math.min(2.2f, dmg / 9f));
        Vec3 at = mid(t);
        Vec3 dir = at.subtract(p.position().add(0, 1.2, 0));
        dir = dir.lengthSqr() < 1.0E-4 ? p.getLookAngle() : dir.normalize();
        Vec3 side = Math.abs(dir.y) > 0.95 ? new Vec3(1, 0, 0) : dir.cross(new Vec3(0, 1, 0)).normalize();
        Vec3 up = side.cross(dir).normalize();
        ThreadLocalRandom r = ThreadLocalRandom.current();

        if (bookItem != null && own(sl, bookItem, at, dir, side, up, s, heavy, r)) return;   // the newer grimoires each have their own

        // the flash: a tight white ring that opens and is gone
        FxSend.wall(sl, Fx.SILVER, at, side, up, 0.05f, 0.45f * s, -180, 180, 0.07f * s, 3, 0, 0);
        // the shock ring, square to the blow, in the grimoire's colour
        FxSend.wall(sl, pal, at, side, up, 0.15f, 0.9f * s + 0.2f, -180, 180, 0.05f * s, 5, 0, 0);
        // sparks carried on past the target
        int n = 3 + (int) (s * 2);
        for (int k = 0; k < n; k++) {
            Vec3 d = dir.add(side.scale(r.nextDouble(-0.7, 0.7))).add(up.scale(r.nextDouble(-0.5, 0.8))).normalize();
            FxSend.spike(sl, k == 0 ? Fx.SILVER : pal, at, d, 0.3f * s + r.nextFloat() * 0.3f, 0.035f * s, 5, 0, FxPacket.POP, 0, d.scale(0.22 + 0.1 * s), 4);
        }
        if (heavy) {   // a heavy blow: the screen jolts and a second, wider ring rolls out behind the first
            FxSend.shake(sl, at, Math.min(0.55f, 0.12f + dmg / 60f), 4, 14);
            final int fp = pal;
            final Vec3 fs = side, fu = up;
            AstaFx.later(2, () -> FxSend.wall(sl, fp, at, fs, fu, 0.4f, 1.6f * s, -180, 180, 0.03f * s, 6, 0, 0));
        }
    }

    static final int SHADOW = 47, BLADE_RED = 18;

    /**
     * The newer grimoires' own impacts - each its own shape, none of them the shared ring-and-sparks (which reads as a
     * spray of blood in red): Heartbeat's pulse trace, Dream's slow bubbles, Shadow's tendrils, the Parasite's claw
     * rakes, Hunger's bite, the Architect's stone chips, Meridian's sun-flare, the Bound Demon's black-red cross, Vector's
     * redirection arrows, the Fuse's spark-pop, Foreseen's afterimage ripples, Ill Omen's falling sigil, the Twins' sun
     * and moon. Returns false for every other grimoire (they keep the shared one).
     */
    static boolean own(ServerLevel sl, net.minecraft.world.item.Item book, Vec3 at, Vec3 dir, Vec3 side, Vec3 up, float s, boolean heavy, ThreadLocalRandom r) {
        if (book == com.ironspellgrimoire.registry.AddonItemRegistry.OVERDRIVE_GRIMOIRE.get()) {   // a heartbeat trace across the target
            double w = 0.9 * s;
            Vec3 a = at.add(side.scale(-w));
            double[][] beat = {{0, 0}, {0.35, 0}, {0.45, 0.35}, {0.55, -0.5}, {0.65, 0.2}, {0.72, 0}, {1, 0}};
            Vec3 prev = a;
            for (int k = 1; k < beat.length; k++) {
                Vec3 q = a.add(side.scale(2 * w * beat[k][0])).add(up.scale(w * beat[k][1]));
                FxSend.trail(sl, k == 3 ? Fx.SILVER : Fx.CRIMSON, prev, q, 0.045f * s, 6);
                prev = q;
            }
            if (heavy) FxSend.shake(sl, at, 0.25f, 3, 12);
            return true;
        }
        if (book == com.ironspellgrimoire.registry.AddonItemRegistry.DREAM_GRIMOIRE.get()) {   // bubbles that drift up and pop
            for (int k = 0; k < 3 + (int) s; k++) {
                Vec3 q = at.add(side.scale(r.nextDouble(-0.5, 0.5) * s)).add(up.scale(r.nextDouble(-0.3, 0.5) * s));
                Kit.orb(sl, Fx.VOID, q, new Vec3(0, 0.04, 0), 0.12f + r.nextFloat() * 0.12f * s, 10 + r.nextInt(6));
            }
            FxSend.wall(sl, Fx.VOID, at, side, up, 0.5f * s, 0.6f * s, -180, 180, 0.03f, 9, 0, 0);
            return true;
        }
        if (book == com.ironspellgrimoire.registry.AddonItemRegistry.SHADOW_GRIMOIRE.get()) {   // tendrils of shadow out of the ground, through it
            Vec3 g = at.add(0, -0.9, 0);
            for (int k = 0; k < 3; k++) {
                Vec3 d = new Vec3(r.nextDouble(-0.4, 0.4), 1, r.nextDouble(-0.4, 0.4)).normalize();
                FxSend.spike(sl, SHADOW, g.add(side.scale((k - 1) * 0.4)), d, 1.6f * s, 0.07f * s, 7, 2, FxPacket.EXTEND);
            }
            return true;
        }
        if (book == com.ironspellgrimoire.registry.AddonItemRegistry.PARASITE_GRIMOIRE.get()) {   // three hooked rakes, top to bottom
            for (int k = -1; k <= 1; k++)
                FxSend.slash(sl, Fx.RIPPER, at.add(side.scale(0.28 * k * s)), up.scale(-1), dir, 0.35f * s, 0.75f * s, 5, 0, 0.6f);
            return true;
        }
        if (book == com.ironspellgrimoire.registry.AddonItemRegistry.HUNGER_GRIMOIRE.get()) {   // a bite: two jaws snapping shut
            FxSend.crescent(sl, Fx.CRIMSON, at.add(up.scale(0.35 * s)), up.scale(-1), dir, 0.4f * s, 0.7f * s, 5, 0.5f);
            FxSend.crescent(sl, Fx.CRIMSON, at.add(up.scale(-0.35 * s)), up, dir, 0.4f * s, 0.7f * s, 5, 0.5f);
            for (int k = 0; k < 4; k++) FxSend.spike(sl, Fx.SILVER, at.add(side.scale((k - 1.5) * 0.18 * s)).add(up.scale(0.32 * s)), up.scale(-1), 0.22f * s, 0.04f, 5, 0, FxPacket.POP);
            return true;
        }
        if (book == com.ironspellgrimoire.registry.AddonItemRegistry.ARCHITECT_GRIMOIRE.get()) {   // chips of stone knocked off, a crack
            FxSend.cracks(sl, Fx.EARTH, at, dir, up, 0.7f * s, 8, false, r.nextInt(1000));
            for (int k = 0; k < 4; k++) {
                Vec3 d = dir.add(side.scale(r.nextDouble(-0.8, 0.8))).add(up.scale(r.nextDouble(0, 0.9))).normalize();
                FxSend.box(sl, Fx.EARTH, at, d, up, 0.09f * s, 0.09f * s, 0.09f * s, 7, 0, FxPacket.POP);
            }
            return true;
        }
        if (book == com.ironspellgrimoire.registry.AddonItemRegistry.MERIDIAN_GRIMOIRE.get()) {   // a sun-flare: eight rays round a white core
            FxSend.wall(sl, Fx.SILVER, at, side, up, 0.05f, 0.3f * s, -180, 180, 0.06f * s, 3, 0, 0);
            for (int k = 0; k < 8; k++) {
                double a = Math.PI * 2 * k / 8 + r.nextDouble(0.3);
                Vec3 d = side.scale(Math.cos(a)).add(up.scale(Math.sin(a)));
                FxSend.spike(sl, k % 2 == 0 ? Fx.GOLD : Fx.FIRE, at.add(d.scale(0.2 * s)), d, (k % 2 == 0 ? 0.8f : 0.5f) * s, 0.05f * s, 5, 0, FxPacket.POP);
            }
            return true;
        }
        if (book == com.ironspellgrimoire.registry.AddonItemRegistry.BOUND_GRIMOIRE.get()) {   // a black cross-cut with a red core
            Vec3 d1 = side.add(up).normalize(), d2 = side.subtract(up).normalize();
            FxSend.slash(sl, SHADOW, at, d1, dir, 0.6f * s, 1.0f * s, 5, 0, 0.9f);
            FxSend.slash(sl, BLADE_RED, at, d2, dir, 0.6f * s, 1.0f * s, 5, 0, 0.5f);
            return true;
        }
        if (book == com.ironspellgrimoire.registry.AddonItemRegistry.VECTOR_GRIMOIRE.get()) {   // the blow's vector, turned: white arrows bent away
            Vec3 bent = dir.add(up.scale(0.8)).normalize();
            for (int k = -1; k <= 1; k++) {
                Vec3 o = at.add(side.scale(0.25 * k * s));
                FxSend.spike(sl, Fx.SILVER, o, k == 0 ? bent : bent.add(side.scale(0.35 * k)).normalize(), 0.9f * s, 0.05f * s, 5, 0, FxPacket.POP, 0, bent.scale(0.25), 4);
            }
            FxSend.wall(sl, Fx.TIME, at, side, up, 0.3f * s, 0.45f * s, -180, 180, 0.02f, 4, 0, 0);
            return true;
        }
        if (book == com.ironspellgrimoire.registry.AddonItemRegistry.FUSE_GRIMOIRE.get()) {   // a spark-pop: a little burst of fire and pink sparks
            Kit.orb(sl, Fx.FIRE, at, Vec3.ZERO, 0.35f * s, 4);
            for (int k = 0; k < 6; k++) {
                Vec3 d = new Vec3(r.nextDouble(-1, 1), r.nextDouble(-0.3, 1), r.nextDouble(-1, 1)).normalize();
                FxSend.spike(sl, k % 2 == 0 ? 16 : Fx.FUEGO, at, d, 0.3f * s, 0.03f, 4, 0, FxPacket.POP, 0, d.scale(0.3), 3);
            }
            return true;
        }
        if (book == com.ironspellgrimoire.registry.AddonItemRegistry.FORESEEN_GRIMOIRE.get()) {   // the blow, seen twice: two pale ripples, one late
            FxSend.wall(sl, 13, at, side, up, 0.2f, 0.7f * s, -180, 180, 0.02f, 5, 0, 0);
            AstaFx.later(3, () -> FxSend.wall(sl, Fx.SILVER, at.add(dir.scale(0.3)), side, up, 0.2f, 0.9f * s, -180, 180, 0.015f, 5, 0, 0));
            return true;
        }
        if (book == com.ironspellgrimoire.registry.AddonItemRegistry.OMEN_GRIMOIRE.get()) {   // ill luck falling on it: a shadow-blue sigil underfoot, a line from above
            FxSend.ring(sl, SHADOW, at.add(0, -0.9, 0), 0.4f * s, 0.6f * s, 0.03f, 8);
            FxSend.beam(sl, SHADOW, at.add(0, 2.2 * s, 0), at, 0.07f * s, 4);
            return true;
        }
        if (book == com.ironspellgrimoire.registry.AddonItemRegistry.TWINS_GRIMOIRE.get()) {   // a sun crescent and a moon crescent, back to back
            FxSend.crescent(sl, Fx.GOLD, at, side, dir, 0.4f * s, 0.7f * s, 5, 0.4f);
            FxSend.crescent(sl, Fx.ICE, at, side.scale(-1), dir, 0.4f * s, 0.7f * s, 5, 0.4f);
            return true;
        }
        return false;
    }
}
