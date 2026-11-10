package com.ironspellgrimoire.server;

import static com.ironspellgrimoire.server.FxUtil.*;

import com.ironspellgrimoire.network.FxPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.ThreadLocalRandom;

/**
 * The signature look of each of the seven newer grimoires. Moves' shared helpers (burst, bolt, dash, claws, spear) used
 * to draw the same rings-and-orbs in a different colour for every book; here each book gets its own silhouette:
 *
 *   Flame Spirit  slender white-gold lances of fire, a salamander serpent that weaves
 *   Lion's Flame  fanned claw-slashes and a swept-back mane of flame
 *   World Tree    roots and thorns thrusting out of the ground, leaves spinning off
 *   Slash         razor lines that cross: no rings, no orbs - only cuts
 *   Earth         blocks of stone heaving up, thrown boulders
 *   Beast         fangs closing in from every side, three-claw rakes
 *   Cotton        round soft puffs - clouds of cream that bounce and swirl
 *
 * Each method returns true when the book has its own version (the caller then skips the generic one).
 */
final class Sig {
    private Sig() {}
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final int S = Fx.SILVER, G = Fx.GOLD;

    static boolean mine(int pal) { return pal >= Moves.FUEGO && pal <= Moves.COTTON; }

    private static Vec3 perp(Vec3 d) {
        Vec3 r = d.cross(UP);
        return r.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : r.normalize();
    }

    // ================================================================== a burst
    static boolean nova(ServerLevel sl, int pal, Vec3 at, double r) {
        float R = (float) Math.max(1.0, r);
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        switch (pal) {
            case Moves.FUEGO -> {   // a crown of slender lances, one white-gold spear through the middle
                int n = R > 3 ? 10 : 7;
                for (int i = 0; i < n; i++) {
                    double a = i * Math.PI * 2 / n;
                    FxSend.spike(sl, pal, at.add(Math.cos(a) * R * 0.55, -0.1, Math.sin(a) * R * 0.55), new Vec3(Math.cos(a) * 0.3, 1, Math.sin(a) * 0.3), R * 0.8f + 0.9f, 0.1f, 10, 3, 0);
                }
                FxSend.spike(sl, G, at.add(0, -0.1, 0), UP, R * 1.2f + 1.0f, 0.18f, 11, 3, 0);
                FxSend.ring(sl, pal, at.add(0, 0.05, 0), R * 0.3f, R * 0.75f, 0.025f, 6);
            }
            case Moves.LION -> {    // claws raked outward over the ground, a mane of sparks thrown up
                int n = 6;
                for (int i = 0; i < n; i++) {
                    double a = i * Math.PI * 2 / n + 0.3;
                    Vec3 d = new Vec3(Math.cos(a), 0, Math.sin(a)), u = new Vec3(-Math.sin(a) * 0.5, 1, Math.cos(a) * 0.5).normalize();
                    FxSend.slash(sl, pal, at.add(d.scale(R * 0.45)).add(0, 0.5, 0), d, u, R * 0.8f, R * 0.2f, 6, 0.3f);
                    FxSend.slash(sl, G, at.add(d.scale(R * 0.45)).add(0, 0.5, 0), d, u, R * 0.65f, R * 0.06f, 5, 0.3f, 0.5f);
                }
                for (int i = 0; i < 4; i++) Kit.orb(sl, pal, at.add(0, 0.5, 0), new Vec3(rnd.nextDouble(-0.2, 0.2), 0.3, rnd.nextDouble(-0.2, 0.2)), 0.35f, 6);
            }
            case Moves.TREE -> {    // roots and thorns thrust out of the ground, leaves drifting off them
                int n = R > 3 ? 9 : 6;
                for (int i = 0; i < n; i++) {
                    double a = i * Math.PI * 2 / n + rnd.nextDouble(0.4), d = R * (0.3 + 0.55 * rnd.nextDouble());
                    Vec3 base = at.add(Math.cos(a) * d, -0.2, Math.sin(a) * d);
                    FxSend.spike(sl, pal, base, new Vec3(Math.cos(a) * 0.45, 1, Math.sin(a) * 0.45), 1.2f + (float) rnd.nextDouble(1.0), 0.15f, 16, 4, FxPacket.GROUND);
                    Vec3 top = base.add(Math.cos(a) * 0.4, 1.6, Math.sin(a) * 0.4);
                    FxSend.trail(sl, G, top, top.add(Math.cos(a + 1) * 0.8, 0.5, Math.sin(a + 1) * 0.8), 0.06f, 10);
                }
            }
            case Moves.RIPPER -> {  // three cuts crossing on the hit: nothing round, nothing glowing, only lines
                double base = rnd.nextDouble(Math.PI);
                for (int k = 0; k < 3; k++) {
                    double a = base + k * Math.PI / 3;
                    Vec3 d = new Vec3(Math.cos(a), 0, Math.sin(a)), u = new Vec3(-Math.sin(a) * 0.6 * (k - 1), 1, Math.cos(a) * 0.6 * (k - 1)).normalize();
                    FxSend.slash(sl, pal, at.add(0, 0.9, 0), d, u, R * 1.25f, R * 0.12f, 5, 0.4f, 0.5f);
                    FxSend.slash(sl, S, at.add(0, 0.9, 0), d, u, R * 1.1f, R * 0.04f, 4, 0.4f, 0.4f);
                }
            }
            case Moves.EARTH -> {   // blocks of stone heave up out of the ground
                int n = R > 3 ? 8 : 5;
                for (int i = 0; i < n; i++) {
                    double a = i * Math.PI * 2 / n + rnd.nextDouble(0.5), d = R * (0.35 + 0.5 * rnd.nextDouble());
                    float s = 0.3f + (float) rnd.nextDouble(0.35);
                    FxSend.box(sl, pal, at.add(Math.cos(a) * d, s * 0.5, Math.sin(a) * d), new Vec3(Math.cos(a), 0, Math.sin(a)), UP, s, s, s * 1.2f + 0.2f, 18, 3, FxPacket.GROUND);
                }
                FxSend.box(sl, pal, at.add(0, 0.5, 0), new Vec3(1, 0, 0), UP, 0.5f, 0.5f, 0.9f, 20, 3, FxPacket.GROUND);
            }
            case Moves.BEAST -> {   // fangs closing in from every side
                int n = R > 3 ? 10 : 7;
                for (int i = 0; i < n; i++) {
                    double a = i * Math.PI * 2 / n;
                    Vec3 base = at.add(Math.cos(a) * R * 0.95, 1.1, Math.sin(a) * R * 0.95);
                    FxSend.spike(sl, pal, base, new Vec3(-Math.cos(a) * 0.9, -0.55, -Math.sin(a) * 0.9), R * 0.5f + 0.6f, 0.17f, 9, 3, 0);
                }
                FxSend.slash(sl, G, at.add(0, 0.7, 0), new Vec3(1, 0, 0), UP, R * 0.8f, R * 0.08f, 5, 0.2f, 0.5f);
            }
            case Moves.COTTON -> {  // round soft puffs bounce out and swirl
                int n = 10;
                for (int i = 0; i < n; i++) {
                    double a = i * Math.PI * 2 / n + rnd.nextDouble(0.3);
                    Kit.orb(sl, pal, at.add(Math.cos(a) * R * 0.3, 0.3 + rnd.nextDouble(0.8), Math.sin(a) * R * 0.3), new Vec3(Math.cos(a) * 0.14, 0.05, Math.sin(a) * 0.14), 0.4f + (float) rnd.nextDouble(0.25), 10);
                }
                FxLib.halo(sl, pal, at.add(0, 0.5, 0), R * 0.5f);
            }
            default -> { return false; }
        }
        return true;
    }

    // ================================================================== a thrown thing, one tick of its flight
    static boolean bolt(ServerLevel sl, int pal, Vec3 prev, Vec3 pos, Vec3 vel, float size, long t) {
        Vec3 d = vel.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : vel.normalize();
        Vec3 rt = perp(d), up = rt.cross(d).normalize();
        switch (pal) {
            case Moves.FUEGO -> {   // a white-gold core and two flames weaving round it
                Kit.orb(sl, G, pos, vel, size * 0.7f, 2);
                for (int s = 0; s < 2; s++) {
                    double a0 = t * 0.9 + s * Math.PI, a1 = (t + 1) * 0.9 + s * Math.PI, w = size * 1.5;
                    Vec3 p0 = prev.add(rt.scale(Math.cos(a0) * w)).add(up.scale(Math.sin(a0) * w));
                    Vec3 p1 = pos.add(rt.scale(Math.cos(a1) * w)).add(up.scale(Math.sin(a1) * w));
                    FxSend.trail(sl, pal, p0, p1, size * 0.5f, 6);
                }
            }
            case Moves.LION -> {    // a hot head with a mane of flame swept back from it
                Kit.orb(sl, pal, pos, vel, size, 2);
                for (int s = -1; s <= 1; s++)
                    FxSend.trail(sl, s == 0 ? G : pal, pos, pos.subtract(d.scale(size * 4)).add(rt.scale(s * size * 1.3)).add(up.scale(size * 0.6 * (1 - Math.abs(s)))), size * 0.35f, 5);
            }
            case Moves.TREE -> {    // a seed pod tumbling, throwing off leaves
                Kit.orb(sl, pal, pos, vel, size * 0.7f, 2);
                double a = t * 0.8;
                Vec3 u2 = rt.scale(Math.cos(a)).add(up.scale(Math.sin(a)));
                FxSend.slash(sl, pal, pos, d, u2, size * 3.2f, size * 1.0f, 3, 0f);
                FxSend.trail(sl, G, pos, pos.subtract(d.scale(size * 3)).add(u2.scale(size * 1.5)), size * 0.2f, 6);
            }
            case Moves.RIPPER -> {  // a thrown blade, spinning: a single crescent, no orb at all
                double a = t * 1.1;
                Vec3 u2 = rt.scale(Math.cos(a)).add(up.scale(Math.sin(a)));
                FxSend.crescent(sl, pal, pos, d, u2, size * 3.4f, size * 1.1f, 3, 0f);
                FxSend.trail(sl, S, prev, pos, size * 0.12f, 3);
            }
            case Moves.EARTH -> {   // a boulder, turning over as it flies
                FxSend.box(sl, pal, pos, d, up, size * 1.2f, size * 1.1f, size * 1.0f, 2, 0, FxPacket.HOLD, 28f, Vec3.ZERO, 0);
                if (t % 2 == 0) Kit.orb(sl, pal, pos.subtract(d.scale(size)), Vec3.ZERO, size * 0.4f, 3);
            }
            case Moves.BEAST -> {   // a leaping predator: three claw-marks raking ahead of a small body
                Kit.orb(sl, pal, pos, vel, size * 0.55f, 2);
                for (int s = -1; s <= 1; s++)
                    FxSend.slash(sl, pal, pos.add(rt.scale(s * size * 1.1)).add(d.scale(size)), d, up.add(rt.scale(s * 0.25)).normalize(), size * 3.0f, size * 0.55f, 3, 0f, 0.5f);
            }
            case Moves.COTTON -> {  // a big soft puff with four little ones circling it
                Kit.orb(sl, pal, pos, vel, size * 1.15f, 2);
                for (int s = 0; s < 4; s++) {
                    double a = t * 0.35 + s * Math.PI / 2;
                    Kit.orb(sl, pal, pos.add(rt.scale(Math.cos(a) * size * 1.7)).add(up.scale(Math.sin(a) * size * 1.7)), vel, size * 0.42f, 2);
                }
            }
            default -> { return false; }
        }
        return true;
    }

    // ================================================================== a rush
    static boolean dash(ServerLevel sl, int pal, Vec3 from, Vec3 to) {
        Vec3 line = to.subtract(from);
        if (line.lengthSqr() < 1.0E-4) return true;
        Vec3 d = line.normalize(), rt = perp(d);
        switch (pal) {
            case Moves.FUEGO -> {   // the salamander weaves: three bends, white-gold underneath
                Vec3 prev = from;
                for (int i = 1; i <= 3; i++) {
                    Vec3 q = from.add(line.scale(i / 3.0)).add(rt.scale(Math.sin(i * 2.1) * 0.55));
                    FxSend.trail(sl, pal, prev, q, 0.26f, 7);
                    FxSend.trail(sl, G, prev, q, 0.07f, 5);
                    prev = q;
                }
            }
            case Moves.LION -> {    // three claws raking the air
                for (int s = -1; s <= 1; s++) FxSend.trail(sl, s == 0 ? G : pal, from.add(rt.scale(s * 0.4)), to.add(rt.scale(s * 0.4)), s == 0 ? 0.08f : 0.17f, 6);
            }
            case Moves.RIPPER -> {  // a single straight cut, hair-thin
                FxSend.slash(sl, pal, from.add(line.scale(0.5)), d, UP, (float) line.length() * 0.6f + 1f, 0.06f, 4, 0.5f, 0.4f);
                FxSend.trail(sl, S, from, to, 0.04f, 3);
            }
            case Moves.BEAST -> {   // a pounce: a high arc, landing on all four
                Vec3 prev = from;
                for (int i = 1; i <= 3; i++) {
                    double f = i / 3.0;
                    Vec3 q = from.add(line.scale(f)).add(0, Math.sin(f * Math.PI) * 0.9, 0);
                    FxSend.trail(sl, pal, prev, q, 0.2f, 6);
                    prev = q;
                }
                FxSend.slash(sl, pal, to.add(0, 0.3, 0), d, UP, 1.2f, 0.3f, 4, 0.3f, 0.5f);
            }
            default -> { return false; }
        }
        return true;
    }

    // ================================================================== a spear falling from above (Flame Spirit)
    static boolean strike(ServerLevel sl, int pal, Vec3 q0, Vec3 q1) {
        if (pal != Moves.FUEGO) return false;
        Vec3 d = q1.subtract(q0);
        if (d.lengthSqr() < 1.0E-6) return true;
        FxSend.spike(sl, pal, q0, d, 1.9f, 0.13f, 2, 0, FxPacket.HOLD);
        FxSend.trail(sl, G, q0, q1, 0.06f, 3);
        return true;
    }

    // ================================================================== a fan of cuts
    static boolean claw(ServerLevel sl, int pal, Vec3 c, Vec3 dir, Vec3 up, float len, int i) {
        Vec3 rt = perp(dir);
        switch (pal) {
            case Moves.LION -> {    // wide gold-edged arcs, mane sparks flying off them
                FxSend.slash(sl, pal, c, dir, up, len, len * 0.3f, 5, 0.25f);
                FxSend.slash(sl, G, c, dir, up, len * 0.85f, len * 0.07f, 4, 0.25f, 0.5f);
                Kit.orb(sl, pal, c.add(dir.scale(len * 0.4)), dir.scale(0.2), 0.3f, 4);
            }
            case Moves.BEAST -> {   // a rake: three parallel marks, like one paw
                for (int s = -1; s <= 1; s++) FxSend.slash(sl, pal, c.add(rt.scale(s * len * 0.2)), dir, up, len * 0.9f, len * 0.09f, 5, 0.25f, 0.6f);
            }
            default -> { return false; }
        }
        return true;
    }
}
