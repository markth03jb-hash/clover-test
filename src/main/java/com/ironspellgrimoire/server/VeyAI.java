package com.ironspellgrimoire.server;

import com.ironspellgrimoire.entity.MagistrateVey;
import com.ironspellgrimoire.entity.Warden;
import com.ironspellgrimoire.registry.AddonEntities;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Magistrate Vey's fight (entity/MagistrateVey), in his vault (ASHFALL, Arc II).
 * <ul>
 *   <li><b>The ledger:</b> he writes down the hardest blow that lands on him (kept 8 s).</li>
 *   <li><b>Recital:</b> he reads it back - a gold line marked to whoever struck it, then 60% of that blow down it
 *       (between 4 and 16).</li>
 *   <li><b>Seal of Office:</b> a gold circle under you; stay and you're sealed - slowed hard for 2 s - and burnt.</li>
 *   <li><b>The staff:</b> close, a blow that throws you back.</li>
 *   <li><b>Stage 2</b> (two thirds): he calls his Wardens (two at a time, three at most).</li>
 *   <li><b>Stage 3</b> (a third): Final Judgment - circles all over the vault and one under each of you; find the gaps.</li>
 * </ul>
 */
public final class VeyAI {
    private VeyAI() {}

    static final UUID SEALED = UUID.fromString("7e400000-0000-4000-8000-000000000001");
    static final ChatFormatting COL = ChatFormatting.GOLD;
    static final String WHO = "Magistrate Vey";

    static Vec3 chest(MagistrateVey b) { return b.position().add(0, b.getBbHeight() * 0.62, 0); }

    /** The ledger: the hardest blow in the last 8 s, and whose it was. */
    public static void write(MagistrateVey b, DamageSource src, float amount) {
        if (!(src.getEntity() instanceof Player p)) return;
        long now = b.level().getGameTime();
        if (now - b.writtenAt > 160 || amount > b.written) { b.written = amount; b.writer = p.getUUID(); b.writtenAt = now; }
    }

    public static void tick(MagistrateVey b) {
        ServerLevel sl = (ServerLevel) b.level();
        Player p = BossKit.target(b);
        if (p == null) { b.getNavigation().stop(); return; }
        b.getLookControl().setLookAt(p, 30f, 30f);
        if (b.busy > 0) { b.busy--; b.getNavigation().stop(); return; }
        BossKit.keep(b, p, 4.5);
        if (--b.strikeCd <= 0 && b.distanceTo(p) < 3.4) { b.strikeCd = 32; staff(b, p, sl); return; }
        if (b.phase() >= 3 && --b.judgeCd <= 0) { b.judgeCd = 240; judgment(b, sl); return; }
        if (--b.recitalCd <= 0) { b.recitalCd = b.phase() >= 2 ? 120 : 160; if (recital(b, sl)) return; }
        if (--b.sealCd <= 0) { b.sealCd = b.phase() >= 2 ? 90 : 120; seal(b, p, sl); return; }
        if (b.phase() >= 2 && --b.callCd <= 0) { b.callCd = 400; call(b, sl); }
    }

    static void staff(MagistrateVey b, Player p, ServerLevel sl) {
        b.swing(net.minecraft.world.InteractionHand.OFF_HAND);
        BossKit.hurt(b, p, 8f);
        Vec3 away = p.position().subtract(b.position()); away = new Vec3(away.x, 0, away.z);
        if (away.lengthSqr() > 1e-4) { p.setDeltaMovement(away.normalize().scale(1.2).add(0, 0.4, 0)); p.hurtMarked = true; }
        BossFx.ring(sl, Fx.GOLD, p.position().add(0, 1, 0), 0.3f, 1.4f, 0.06f, 8);
        sl.playSound(null, b.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 0.6f, 1.6f);
    }

    /** He reads back what you wrote on him. False when there's nothing written (or its writer is gone). */
    static boolean recital(MagistrateVey b, ServerLevel sl) {
        long now = sl.getGameTime();
        if (b.writer == null || now - b.writtenAt > 160 || b.written <= 0) return false;
        Player w = sl.getPlayerByUUID(b.writer);
        if (!BossKit.valid(b, w)) return false;
        float dmg = Math.max(4f, Math.min(16f, b.written * 0.6f));
        b.written = 0;
        Vec3 a = b.position().add(0, 0.1, 0), dir = w.position().subtract(b.position()); dir = new Vec3(dir.x, 0, dir.z);
        if (dir.lengthSqr() < 1e-4) return false;
        Vec3 c = a.add(dir.normalize().scale(20));
        int T = 22;
        BossKit.warnLine(sl, a, c, 1.5f, T);
        BossKit.say(b, WHO, COL, "Let the record show what you did to me. Every line of it.");
        b.busy = T + 4;
        AstaFx.later(T, () -> {
            if (!b.isAlive()) return;
            Vec3 a2 = chest(b), c2 = new Vec3(c.x, a2.y, c.z);
            BossFx.beam(sl, Fx.GOLD, a2, c2, 0.6f, 10);
            for (Player t : BossKit.along(b, a2, c2, 1.5)) BossKit.hurt(b, t, dmg);
            sl.playSound(null, b.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.HOSTILE, 2f, 0.6f);
        });
        return true;
    }

    static void seal(MagistrateVey b, Player p, ServerLevel sl) {
        for (Player h : BossKit.near(b, b.position(), 24)) {
            Vec3 at = BossKit.ground(sl, h.position());
            BossKit.warnCircle(sl, at, 2f, 20);
            AstaFx.later(20, () -> {
                if (!b.isAlive()) return;
                BossFx.ring(sl, Fx.GOLD, at.add(0, 0.1, 0), 0.3f, 2.1f, 0.1f, 14);
                for (Player v : BossKit.near(b, at, 2.1)) {
                    BossKit.hurt(b, v, 6f);
                    TimedModifiers.apply(v, Attributes.MOVEMENT_SPEED, SEALED, "Seal of Office", -0.7, AttributeModifier.Operation.MULTIPLY_TOTAL, 40);
                }
            });
        }
        b.busy = 10;
    }

    static void call(MagistrateVey b, ServerLevel sl) {
        int alive = sl.getEntitiesOfClass(Warden.class, b.getBoundingBox().inflate(40), m -> m.isAlive() && m.getPersistentData().getInt("isg_story") == b.party).size();
        if (alive >= 3) return;
        BossKit.say(b, WHO, COL, "Wardens. Escort our guest to the binding table.");
        Story.Party pt = Story.byId(sl.getServer(), b.party);
        for (int i = 0; i < 2; i++) {
            Warden m = AddonEntities.WARDEN.get().create(sl);
            if (m == null) continue;
            Vec3 at = BossKit.ground(sl, b.position().add((i == 0 ? -5 : 5), 1, -4));
            m.moveTo(at.x, at.y, at.z, 0, 0);
            if (pt != null) StoryArc1.tag(m, pt, StoryStage.VAULT);
            sl.addFreshEntity(m);
            BossFx.ring(sl, Fx.GOLD, at.add(0, 0.1, 0), 0.2f, 1.3f, 0.05f, 10);
        }
    }

    /** Stage 3: circles all over, and one under each of you. */
    static void judgment(MagistrateVey b, ServerLevel sl) {
        BossKit.say(b, WHO, COL, "Final judgment. The Trials find you wanting.");
        b.busy = 34;
        int T = 30;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < 10; i++) {
            Vec3 at = BossKit.ground(sl, b.position().add(r.nextDouble(-6, 6), 1, r.nextDouble(-12, 4)));
            strike(b, sl, at, T + (i % 3) * 6);
        }
        for (Player h : BossKit.near(b, b.position(), 24)) strike(b, sl, BossKit.ground(sl, h.position()), T);
        sl.playSound(null, b.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.HOSTILE, 2f, 0.5f);
    }

    static void strike(MagistrateVey b, ServerLevel sl, Vec3 at, int T) {
        BossKit.warnCircle(sl, at, 2.2f, T);
        AstaFx.later(T, () -> {
            if (!b.isAlive()) return;
            BossFx.beam(sl, Fx.GOLD, at.add(0, 6, 0), at, 0.7f, 8);
            for (Player v : BossKit.near(b, at, 2.2)) BossKit.hurt(b, v, 9f);
        });
    }

    public static void phase(MagistrateVey b, int phase) {
        if (!(b.level() instanceof ServerLevel sl)) return;
        b.setTransforming(30);
        BossFx.ring(sl, Fx.GOLD, b.position().add(0, 0.1, 0), 0.4f, 6f, 0.12f, 14);
        BossKit.say(b, WHO, COL, phase == 2 ? "You think this vault is mine? It's the city's. Every coin in Cindermarch is bound in these books."
                : "Then I'll write YOU down. Name, grimoire, the colour of the light when you go out.");
        if (phase == 2) b.callCd = 20;
    }

    public static void dying(MagistrateVey b, int t) {
        if (t != 1 || !(b.level() instanceof ServerLevel sl)) return;
        BossKit.say(b, WHO, COL, "The ledger... balances...");
        BossFx.ring(sl, Fx.GOLD, b.position().add(0, 0.1, 0), 0.3f, 6f, 0.1f, 20);
        StoryArc2.veyFell(b);
    }
}
