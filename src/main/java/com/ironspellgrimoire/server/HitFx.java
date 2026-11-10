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
final class HitFx {
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
        try {
            ItemStack book = Utils.getPlayerSpellbookStack(p);
            GrimoireMastery.Book b = book == null ? null : GrimoireMastery.bookOf(book);
            if (b != null) pal = b.palette();
        } catch (Throwable ignored) { }

        float s = Math.max(0.5f, Math.min(2.2f, dmg / 9f));
        Vec3 at = mid(t);
        Vec3 dir = at.subtract(p.position().add(0, 1.2, 0));
        dir = dir.lengthSqr() < 1.0E-4 ? p.getLookAngle() : dir.normalize();
        Vec3 side = Math.abs(dir.y) > 0.95 ? new Vec3(1, 0, 0) : dir.cross(new Vec3(0, 1, 0)).normalize();
        Vec3 up = side.cross(dir).normalize();
        ThreadLocalRandom r = ThreadLocalRandom.current();

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
}
