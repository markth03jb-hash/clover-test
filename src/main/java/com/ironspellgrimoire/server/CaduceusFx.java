package com.ironspellgrimoire.server;

import static com.ironspellgrimoire.server.FxUtil.*;

import com.ironspellgrimoire.network.AddonNetwork;
import com.ironspellgrimoire.network.FxPacket;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

/**
 * The Caduceus Grimoire - Rien, the Index's Proxy. One weapon only: the Caduceus, a vial of liquid gold on a chain. For
 * every Prescript it pours out and sets into whatever the order calls for - whip-sword, rapier, scythe, a spray of
 * spikes, a lance, a shell, wires, a hammer - and at the end, all of them at once (Furioso). Every hit leaves Fortuna;
 * carrying out the Prescript fills Fortune. Liquid gold with a white sheen; never block-breaking.
 * Sounds: the pour is lava leaving a bucket, the chain is a chain, the set metal chimes.
 */
public final class CaduceusFx {
    private CaduceusFx() {}

    static final int G = Fx.GOLD, W = Fx.SILVER;

    static void held(ServerPlayer p, int kind, int life) {
        FxPacket pkt = new FxPacket();
        pkt.shape = 63; pkt.id = p.getId(); pkt.life = life; pkt.sweep = kind; pkt.palette = 1;
        AddonNetwork.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), pkt);
    }

    /** The vial pours: a short ribbon of gold falling from the hand and the pour's sound, then the metal sets with a chime. */
    private static void pour(ServerLevel sl, ServerPlayer p) {
        Vec3 h = Kit.hand(p);
        FxSend.trail(sl, G, h, h.add(p.getLookAngle().scale(0.8)).add(0, -0.3, 0), 0.12f, 6);
        FxSend.ring(sl, G, h, 0.05f, 0.6f, 0.04f, 6);
        sound(sl, h, SoundEvents.BUCKET_EMPTY_LAVA, 0.7f, 1.3f);
        sound(sl, h, SoundEvents.CHAIN_PLACE, 0.8f, 1.0f);
        AstaFx.later(3, () -> sound(sl, h, SoundEvents.AMETHYST_BLOCK_CHIME, 0.7f, 1.2f));
    }

    private static float pow(ServerPlayer p, float base) {
        return LimbusStatus.prescriptReceived(p) ? base * 1.25f : base;
    }

    private static void hit(ServerPlayer p, LivingEntity t, float dmg, String id, int fortuna, boolean ranged) {
        AstaFx.damage(p, t, dmg, id);
        LimbusStatus.rienHit(p, t, fortuna, ranged);
        FxSend.ring(p.serverLevel(), G, mid(t), 0.05f, 0.6f, 0.04f, 4);
    }

    // ================================================================== 1. Caduceus Lash
    /** The vial spins on its chain; the gold stretches into a whip-sword and cuts a wide arc. */
    public static void lash(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        held(p, 11, 20);
        pour(sl, p);
        AstaFx.later(8, () -> {
            if (!alive(p)) return;
            Vec3 look = flat(p.getLookAngle()), rt = rightOf(look);
            Vec3 c = p.position().add(0, 1.1, 0);
            // the arc drawn in links: a run of short gold slashes swept from right to left
            for (int i = 0; i < 7; i++) {
                final int ii = i;
                AstaFx.later(i, () -> {
                    double a = -1.3 + ii * 0.43;
                    Vec3 dir = rotY(look, a);
                    Vec3 at = c.add(dir.scale(3.4));
                    FxSend.slash(sl, G, at, dir, rightOf(dir), 0.6f, 1.4f, 5, 0, 0.5f);
                    FxSend.trail(sl, G, c.add(rotY(look, a - 0.43).scale(3.4)), at, 0.1f, 6);
                });
            }
            sound(sl, c, SoundEvents.CHAIN_HIT, 1f, 0.8f);
            sound(sl, c, SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 1.2f);
            for (LivingEntity t : Kit.near(sl, p, c, 5.2)) {
                Vec3 to = flat(t.position().subtract(p.position()));
                if (to.lengthSqr() > 1e-4 && to.normalize().dot(look) < -0.2) continue;
                hit(p, t, pow(p, power), "caduceus_lash", 6, false);
            }
        });
    }

    // ================================================================== 2. Gilded Rapier
    /** A thin sword of gold: three precise thrusts; the third is a sure critical on a foe with 30+ Fortuna. */
    public static void rapier(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        held(p, 12, 30);
        pour(sl, p);
        int[] at = {8, 14, 22};
        for (int i = 0; i < 3; i++) {
            final int ii = i;
            AstaFx.later(at[i], () -> {
                if (!alive(p)) return;
                Vec3 look = p.getLookAngle(), a = p.getEyePosition().add(0, -0.3, 0), b = a.add(look.scale(5.0));
                Vec3 off = rightOf(flat(look)).scale((ii - 1) * 0.25);
                FxSend.spike(sl, G, a.add(look.scale(0.6)).add(off), look, 4.2f, 0.05f, 5, 0, FxPacket.EXTEND);
                FxSend.trail(sl, W, a.add(off), b.add(off), 0.06f, 5);
                FxSend.ring(sl, G, b, 0.05f, ii == 2 ? 1.2f : 0.6f, 0.03f, 5);
                sound(sl, a, SoundEvents.PLAYER_ATTACK_SWEEP, 0.6f, 1.8f);
                sound(sl, a, SoundEvents.TRIDENT_HIT, 0.6f, 1.6f);
                for (LivingEntity t : Kit.along(sl, p, a, b, 1.0)) {
                    boolean crit = ii == 2 && LimbusStatus.fortunaOf(t) >= 30;
                    hit(p, t, pow(p, power) * (crit ? 0.9f : 0.4f), "gilded_rapier", 4, false);
                    t.invulnerableTime = 0;
                    if (crit) {
                        FxSend.impactFrame(sl, mid(t), 2, FxSend.FRAME_GOLD, 16);
                        sound(sl, mid(t), SoundEvents.PLAYER_ATTACK_CRIT, 1.2f, 1.2f);
                    }
                }
            });
        }
    }

    // ================================================================== 3. Reaper's Prescript
    /** A great scythe of gold: one huge reap that pulls everything in front of you inward and always crits. */
    public static void scythe(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        held(p, 13, 30);
        pour(sl, p);
        AstaFx.later(14, () -> {
            if (!alive(p)) return;
            Vec3 look = flat(p.getLookAngle()), rt = rightOf(look);
            Vec3 c = p.position().add(0, 1.0, 0).add(look.scale(1.0));
            FxSend.slash(sl, G, c, look, rt.add(0, -0.25, 0).normalize(), 3.2f, 6.0f, 9, 0.1f, 1.6f);
            FxSend.slash(sl, W, c.add(0, 0.05, 0), look, rt.add(0, -0.25, 0).normalize(), 3.0f, 5.6f, 6, 0.1f, 0.35f);
            FxSend.impactFrame(sl, c.add(look.scale(3)), 3, FxSend.FRAME_GOLD, 20);
            sound(sl, c, SoundEvents.PLAYER_ATTACK_SWEEP, 1.4f, 0.5f);
            sound(sl, c, SoundEvents.PLAYER_ATTACK_CRIT, 1.2f, 0.8f);
            for (LivingEntity t : Kit.near(sl, p, p.position(), 7)) {
                Vec3 to = flat(t.position().subtract(p.position()));
                if (to.lengthSqr() > 1e-4 && to.normalize().dot(look) < 0.0) continue;
                hit(p, t, pow(p, power), "reapers_prescript", 10, false);
                Kit.push(t, p.position().subtract(t.position()).normalize(), 0.9, 0.1);   // reaped inward
            }
        });
    }

    // ================================================================== 4. Molten Volley
    /** The liquid splits into a burst of small spikes fired like shot. Counts as a fired form for the Prescript. */
    public static void volley(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        held(p, 17, 18);
        pour(sl, p);
        AstaFx.later(8, () -> {
            if (!alive(p)) return;
            ThreadLocalRandom r = ThreadLocalRandom.current();
            Vec3 look = p.getLookAngle(), eye = p.getEyePosition().add(0, -0.2, 0);
            sound(sl, eye, SoundEvents.FIREWORK_ROCKET_BLAST, 1.2f, 0.6f);
            sound(sl, eye, SoundEvents.CROSSBOW_SHOOT, 1f, 1.3f);
            FxSend.ring(sl, G, eye.add(look.scale(1.2)), 0.1f, 1.0f, 0.06f, 5);
            for (int k = 0; k < 14; k++) {
                Vec3 d = look.add(r.nextDouble(-0.18, 0.18), r.nextDouble(-0.12, 0.12), r.nextDouble(-0.18, 0.18)).normalize();
                Vec3 end = clip(sl, p, eye, eye.add(d.scale(12)));
                FxSend.spike(sl, G, eye.add(d.scale(1.0)), d, 0.5f, 0.05f, 6, 0, FxPacket.POP, 0, d.scale(1.8), 5);
                FxSend.trail(sl, G, eye.add(d.scale(1.0)), end, 0.03f, 4);
                for (LivingEntity t : Kit.along(sl, p, eye, end, 0.6)) {
                    hit(p, t, pow(p, power) * 0.18f, "molten_volley", 2, true);
                    t.invulnerableTime = 0;
                }
            }
        });
    }

    // ================================================================== 5. Index Lance
    /** A long spear thrown straight and far; it pierces, then melts back into the vial. A fired form. */
    public static void lance(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        held(p, 14, 14);
        pour(sl, p);
        AstaFx.later(12, () -> {
            if (!alive(p)) return;
            Vec3 look = p.getLookAngle(), a = p.getEyePosition();
            sound(sl, a, SoundEvents.TRIDENT_THROW, 1.2f, 1.1f);
            fly(p, sl, a.add(look.scale(1.0)), look, pow(p, power), 0);
        });
    }

    private static void fly(ServerPlayer p, ServerLevel sl, Vec3 from, Vec3 d, float dmg, int step) {
        if (step > 10 || !alive(p)) return;
        Vec3 to = clip(sl, p, from, from.add(d.scale(3.5)));
        FxSend.spike(sl, G, from, d, 2.6f, 0.1f, 3, 0, FxPacket.POP);
        FxSend.trail(sl, W, from, to, 0.08f, 5);
        for (LivingEntity t : Kit.along(sl, p, from, to, 1.0)) hit(p, t, dmg, "index_lance", 8, true);
        if (to.distanceTo(from) < 3.4) {   // stuck: it melts back to you as a thread of gold
            FxSend.ring(sl, G, to, 0.1f, 1.2f, 0.06f, 6);
            FxSend.trail(sl, G, to, Kit.hand(p), 0.06f, 10);
            sound(sl, to, SoundEvents.TRIDENT_HIT, 1f, 1.2f);
            sound(sl, to, SoundEvents.BUCKET_FILL_LAVA, 0.6f, 1.4f);
            return;
        }
        AstaFx.later(1, () -> fly(p, sl, to, d, dmg, step + 1));
    }

    // ================================================================== 6. Golden Shell
    /** The gold wraps you as thin armour for 4 s: hits are greatly softened and the attacker is marked with Fortuna. */
    public static void shell(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        pour(sl, p);
        aura(p, 9, 80);
        FxSend.ring(sl, G, p.position().add(0, 0.1, 0), 0.2f, 1.4f, 0.06f, 10);
        TimedModifiers.apply(p, Attributes.ARMOR, java.util.UUID.fromString("e3b61c20-7a4d-4f8e-9b11-0d1a6c900120"), "Golden Shell", 14, AttributeModifier.Operation.ADDITION, 80);
        TimedModifiers.apply(p, Attributes.ARMOR_TOUGHNESS, java.util.UUID.fromString("e3b61c20-7a4d-4f8e-9b11-0d1a6c900121"), "Golden Shell", 8, AttributeModifier.Operation.ADDITION, 80);
        sound(sl, p.position(), SoundEvents.ARMOR_EQUIP_GOLD, 1.2f, 0.8f);
        Moves.mode(p, "golden_shell", 80, 5, pl -> {
            LivingEntity last = pl.getLastHurtByMob();
            if (last != null && pl.tickCount - pl.getLastHurtByMobTimestamp() < 6) LimbusStatus.addFortuna(pl, last, 5);
        });
    }

    // ================================================================== 7. Severing Wire
    /** Fine gold threads shoot out and bind up to three foes in place for 2 s, cutting a little. */
    public static void wire(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        held(p, 10, 44);
        pour(sl, p);
        List<LivingEntity> foes = new java.util.ArrayList<>(Kit.near(sl, p, p.position(), 12));
        foes.sort(java.util.Comparator.comparingDouble(t -> t.distanceToSqr(p)));
        int n = Math.min(3, foes.size());
        sound(sl, p.position(), SoundEvents.CHAIN_BREAK, 0.8f, 1.6f);
        for (int i = 0; i < n; i++) {
            LivingEntity t = foes.get(i);
            Vec3 pin = t.position();
            hit(p, t, pow(p, power) * 0.5f, "severing_wire", 8, true);
            TimedModifiers.apply(t, Attributes.MOVEMENT_SPEED, java.util.UUID.fromString("e3b61c20-7a4d-4f8e-9b11-0d1a6c900122"), "Severing Wire", -0.95, AttributeModifier.Operation.MULTIPLY_TOTAL, 40);
            for (int k = 0; k < 10; k++) {
                final int kk = k;
                AstaFx.later(k * 4, () -> {
                    if (!t.isAlive() || !alive(p)) return;
                    t.teleportTo(pin.x, t.getY(), pin.z);
                    t.setDeltaMovement(0, Math.min(0, t.getDeltaMovement().y), 0);
                    Vec3 m = mid(t);
                    // threads: one back to your hand, and a cradle of wires round the body
                    FxSend.trail(sl, G, Kit.hand(p), m, 0.025f, 5);
                    for (int w = 0; w < 3; w++) {
                        double a = w * 2.1 + kk * 0.2;
                        Vec3 o = new Vec3(Math.cos(a) * 0.8, 0, Math.sin(a) * 0.8);
                        FxSend.trail(sl, G, m.add(o).add(0, -0.6, 0), m.subtract(o).add(0, 0.6, 0), 0.02f, 5);
                    }
                });
            }
        }
    }

    // ================================================================== 8. Hammer of Judgement
    /** A huge gold hammer forms overhead and comes down; it cashes in all Fortuna on the foes it strikes for extra damage. */
    public static void hammer(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        held(p, 15, 30);
        pour(sl, p);
        Vec3 look = flat(p.getLookAngle()), rt = rightOf(look);
        Vec3 hitAt = p.position().add(look.scale(3.5));
        // the hammer above: a thick bar of gold and its long handle, held up, then brought down
        Vec3 top = hitAt.add(0, 7, 0);
        FxSend.box(sl, G, top, rt, new Vec3(0, 1, 0), 1.6f, 1.1f, 1.1f, 18, 4, FxPacket.POP);
        FxSend.box(sl, W, top.add(0, 1.6, 0), rt, new Vec3(0, 1, 0), 0.15f, 0.15f, 1.8f, 18, 4, FxPacket.POP);
        sound(sl, p.position(), SoundEvents.ANVIL_PLACE, 0.6f, 0.6f);
        AstaFx.later(16, () -> {
            if (!alive(p)) return;
            FxSend.box(sl, G, hitAt.add(0, 1.1, 0), rt, new Vec3(0, 1, 0), 1.6f, 1.1f, 1.1f, 12, 0, FxPacket.POP, 0, new Vec3(0, -0.4, 0), 2);
            FxSend.impactFrame(sl, hitAt.add(0, 1, 0), 4, FxSend.FRAME_GOLD, 24);
            FxSend.shake(sl, hitAt, 0.9f, 10, 24);
            FxSend.ring(sl, G, hitAt.add(0, 0.05, 0), 0.4f, 6.5f, 0.2f, 14);
            FxSend.ring(sl, W, hitAt.add(0, 0.08, 0), 0.3f, 4.0f, 0.08f, 10);
            FxSend.cracks(sl, G, hitAt.add(0, 0.05, 0), look, new Vec3(0, 1, 0), 2.8f, 40, false, p.getId() * 11);
            sound(sl, hitAt, SoundEvents.ANVIL_LAND, 1.4f, 0.6f);
            sound(sl, hitAt, SoundEvents.GENERIC_EXPLODE, 0.8f, 0.9f);
            for (LivingEntity t : Kit.near(sl, p, hitAt, 5)) {
                int f = LimbusStatus.consumeFortuna(t);
                hit(p, t, pow(p, power) * (1f + f / 60f), "hammer_of_judgement", 0, false);
                t.setDeltaMovement(t.getDeltaMovement().add(0, 0.5, 0));
                t.hurtMarked = true;
                if (f > 0) FxSend.ring(sl, G, mid(t), 0.1f, 1.0f + f / 60f, 0.1f, 8);
            }
        });
    }

    // ================================================================== 9. Prescript Received
    /** For 15 s you follow the Prescript perfectly: orders count double, come faster and every skill hits 25% harder. */
    public static void prescriptReceived(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        LimbusStatus.startReceived(p, 300);
        LimbusStatus.addFortune(p, 20);
        FxSend.ring(sl, G, p.position().add(0, 0.1, 0), 0.3f, 3.0f, 0.12f, 14);
        FxSend.ring(sl, W, p.position().add(0, 2.2, 0), 0.3f, 1.2f, 0.05f, 300);
        sound(sl, p.position(), SoundEvents.BOOK_PAGE_TURN, 1.2f, 0.9f);
        sound(sl, p.position(), SoundEvents.BELL_BLOCK, 1f, 1.4f);
        p.displayClientMessage(Component.literal("Prescript received.").withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC), true);
    }

    // ================================================================== 10. Furioso
    /**
     * The vial bursts and every weapon comes at once: rapier, scythe, lance, hammer, twin daggers, the gun, a greatsword
     * and the whip each strike the target in turn, then all of them together. A full Fortune (100) gives the whole
     * nine-strike chain; less gives the first five and the finish.
     */
    public static void furioso(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        LivingEntity t = aimed(p, 20, 30);
        if (t == null) {
            List<LivingEntity> near = Kit.near(sl, p, p.position(), 10);
            if (!near.isEmpty()) t = near.get(0);
        }
        int fortune = LimbusStatus.spendFortune(p);
        boolean full = fortune >= 100;
        final LivingEntity target = t;
        held(p, 18, full ? 60 : 36);
        pour(sl, p);
        FxSend.impactFrame(sl, p.position().add(0, 1.6, 0), 3, FxSend.FRAME_GOLD, 18);
        sound(sl, p.position(), SoundEvents.GLASS_BREAK, 1.2f, 0.7f);   // the vial bursts
        p.displayClientMessage(Component.literal("Furioso.").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
        if (target == null) return;
        int[] forms = full ? new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8} : new int[]{0, 1, 3, 5, 8};
        for (int i = 0; i < forms.length; i++) {
            final int form = forms[i], ii = i;
            AstaFx.later(6 + i * 5, () -> {
                if (!alive(p) || !target.isAlive()) return;
                Vec3 m = mid(target);
                Vec3 from = m.subtract(p.position().add(0, 1, 0)).normalize();
                double side = (ii % 2 == 0 ? 1 : -1);
                Vec3 rt = rightOf(flat(from));
                // step in beside the target for each strike, like a Proxy who is everywhere at once
                Vec3 stand = target.position().subtract(flat(from).scale(1.8)).add(rt.scale(side * 1.2));
                Vec3 safe = SpaceFx.safe(sl, stand, p);
                if (safe != null) SpaceFx.move(p, safe, yawToward(safe, target.position()));
                Vec3 look = flat(m.subtract(p.position())).normalize();
                Vec3 c = p.position().add(0, 1.1, 0).add(look.scale(1.0));
                float dmg = pow(p, power) * 0.28f;
                switch (form) {
                    case 0 -> {   // rapier thrust
                        FxSend.spike(sl, G, c, look, 3f, 0.05f, 5, 0, FxPacket.EXTEND);
                        sound(sl, c, SoundEvents.TRIDENT_HIT, 1f, 1.6f);
                    }
                    case 1 -> {   // scythe reap
                        FxSend.slash(sl, G, c, look, rt.add(0, -0.3, 0).normalize(), 2.0f, 3.6f, 6, 0.1f, 1.2f);
                        sound(sl, c, SoundEvents.PLAYER_ATTACK_SWEEP, 1.2f, 0.6f);
                    }
                    case 2 -> {   // lance
                        FxSend.spike(sl, W, c.subtract(look.scale(0.5)), look, 4.5f, 0.12f, 6, 0, FxPacket.EXTEND);
                        sound(sl, c, SoundEvents.TRIDENT_THROW, 1f, 1.0f);
                    }
                    case 3 -> {   // hammer
                        FxSend.box(sl, G, m.add(0, 2.5, 0), rt, new Vec3(0, 1, 0), 0.9f, 0.6f, 0.6f, 8, 0, FxPacket.POP, 0, new Vec3(0, -0.45, 0), 4);
                        FxSend.ring(sl, G, target.position().add(0, 0.05, 0), 0.3f, 3f, 0.12f, 8);
                        sound(sl, m, SoundEvents.ANVIL_LAND, 0.9f, 0.9f);
                    }
                    case 4 -> {   // twin daggers: an X
                        FxSend.slash(sl, G, c, look, rt.add(0, 0.8, 0).normalize(), 1.2f, 2.2f, 5, 0.1f, 0.7f);
                        FxSend.slash(sl, W, c, look, rt.scale(-1).add(0, 0.8, 0).normalize(), 1.2f, 2.2f, 5, 0.1f, 0.7f);
                        sound(sl, c, SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 1.6f);
                    }
                    case 5 -> {   // the gun: three shots
                        for (int k = 0; k < 3; k++) FxSend.trail(sl, G, c.add(0, k * 0.1 - 0.1, 0), m, 0.05f, 4);
                        FxSend.ring(sl, W, m, 0.1f, 0.9f, 0.05f, 5);
                        sound(sl, c, SoundEvents.FIREWORK_ROCKET_BLAST, 1.2f, 0.7f);
                    }
                    case 6 -> {   // greatsword, overhead
                        FxSend.slash(sl, G, c, look, new Vec3(0, 1, 0), 2.4f, 4.2f, 7, 0.1f, 1.6f);
                        sound(sl, c, SoundEvents.PLAYER_ATTACK_STRONG, 1.2f, 0.6f);
                    }
                    case 7 -> {   // the whip-sword wrapping round
                        for (int k = 0; k < 6; k++) {
                            double a = k * Math.PI / 3;
                            FxSend.trail(sl, G, m.add(Math.cos(a) * 1.1, -0.5 + k * 0.2, Math.sin(a) * 1.1), m.add(Math.cos(a + 1.0) * 1.1, -0.3 + k * 0.2, Math.sin(a + 1.0) * 1.1), 0.06f, 6);
                        }
                        sound(sl, m, SoundEvents.CHAIN_HIT, 1.2f, 0.8f);
                    }
                    default -> {   // the scythe once more, from the other side
                        FxSend.slash(sl, G, c, look, rt.scale(-1).add(0, 0.3, 0).normalize(), 2.2f, 4.0f, 6, 0.1f, 1.2f);
                        sound(sl, c, SoundEvents.PLAYER_ATTACK_SWEEP, 1.2f, 0.8f);
                    }
                }
                hit(p, target, dmg, "furioso", 12, form == 2 || form == 5);
                target.invulnerableTime = 0;
                target.setDeltaMovement(Vec3.ZERO);
                target.hurtMarked = true;
            });
        }
        // the finish: every weapon at once, converging from all round
        AstaFx.later(8 + forms.length * 5, () -> {
            if (!alive(p) || !target.isAlive()) return;
            Vec3 m = mid(target);
            for (int k = 0; k < 9; k++) {
                double a = Math.PI * 2 * k / 9;
                Vec3 from = m.add(Math.cos(a) * 4, 1.5 + Math.sin(k * 1.7), Math.sin(a) * 4);
                FxSend.spike(sl, k % 2 == 0 ? G : W, from, m.subtract(from).normalize(), (float) from.distanceTo(m), 0.12f, 8, 0, FxPacket.EXTEND);
            }
            FxSend.impactFrame(sl, m, 5, FxSend.FRAME_GOLD, 30);
            FxSend.shake(sl, m, 0.9f, 12, 26);
            FxSend.ring(sl, G, target.position().add(0, 0.05, 0), 0.5f, 7f, 0.2f, 14);
            FxSend.ring(sl, W, m, 0.2f, 3.5f, 0.1f, 10);
            sound(sl, m, SoundEvents.ANVIL_LAND, 1.2f, 1.2f);
            sound(sl, m, SoundEvents.GENERIC_EXPLODE, 1f, 1.1f);
            sound(sl, m, SoundEvents.BELL_BLOCK, 1.2f, 0.7f);
            int f = LimbusStatus.consumeFortuna(target);
            hit(p, target, pow(p, power) * (full ? 1.6f : 1.0f) * (1f + f / 100f), "furioso", 0, false);
        });
    }

    /** A skin of colour hugging the body (client: AuraOutlineLayer; 9 = liquid gold). */
    static void aura(ServerPlayer p, int pal, int life) {
        FxPacket pkt = new FxPacket();
        pkt.shape = 14; pkt.id = p.getId(); pkt.palette = (byte) pal; pkt.life = life;
        AddonNetwork.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), pkt);
    }

    private static float yawToward(Vec3 from, Vec3 to) {
        Vec3 d = to.subtract(from);
        return (float) Math.toDegrees(Math.atan2(-d.x, d.z));
    }

    // ================================================================== awakening (Surpassing Limits)
    static void awaken(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        LimbusStatus.addFortune(p, 100);
        LimbusStatus.startReceived(p, 600);
        FxSend.ring(sl, G, p.position().add(0, 0.1, 0), 0.3f, 5f, 0.15f, 20);
        sound(sl, p.position(), SoundEvents.BELL_BLOCK, 1.4f, 0.8f);
    }
}
