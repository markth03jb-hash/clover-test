package com.ironspellgrimoire.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * The auras round the open grimoire for every book that has its own (FxBooks.bookAura draws styles 0-6; these are
 * 7 and up). Each is drawn in the book's plane - viewer roughly along +z, the book about 0.5 across - and kept light
 * enough that the book stays readable. One style per grimoire, each in its own theme:
 *
 *  7 Mercury     - beads of liquid metal sliding round a wobbling quicksilver ring
 *  8 Fire        - tongues of flame licking up all round it, embers rising
 *  9 Lightning   - jagged arcs crackling between points on a ring, re-striking every few ticks
 * 10 Hellfire    - a crown of crimson flame pointing outward, with black smoke between the tongues
 * 11 Bone        - vertebrae above and below it, bones circling it
 * 12 Thread      - three threads weaving across it, a needle running along one
 * 13 Space       - an oval gate behind it: gold rim, a violet swirl turning inside
 * 14 Water       - ripples spreading out from it, bubbles rising
 * 15 Mirror      - four mirror panes turning round it, a glint sweeping across each
 * 16 Lion flame  - a mane of gold-orange flame rays bristling outward
 * 17 Slash       - slash marks flashing across the air round it and fading
 * 18 Earth       - chunks of rock orbiting on a tilted ring, dust motes
 * 19 Time       - a clock face behind it: twelve marks and two hands, the long one ticking
 * 20 Blood       - a ring of blood behind it, drops running down off its lower half
 * 21 Gravity     - streaks spiralling in toward it, pebbles caught in a tight orbit
 * 22 Sound       - sound waves pulsing out to both sides
 * 23 Ink         - a brush-stroke circle drawn round it in black ink, then washed away, ink spatter
 * 24 Error       - glitch blocks flickering round it, scanlines through it
 * 25 Treasure    - golden ripples in an arc above it, blade points easing out of them
 * 26 Chain       - a loop of chain links running round it
 * 27 Hunter      - a blood moon behind it, three claw marks raked across
 * 28 Void        - a black hole behind it with a pale rim, motes drawn in and swallowed
 * 29 Gambler     - playing cards and dice tumbling round it
 * 30 Tarot       - three cards fanned out behind it, each with its own sign
 * 31 Abyss       - glowing jellyfish bobbing round it, bubbles rising
 * 32 Mech        - a toothed gear turning behind it, targeting brackets at the corners and a scan line
 * 33 Weather     - a little cloud above it raining, now and then a flash
 * 34 Overdrive   - a heart-monitor trace running behind it, a pulse ring on every beat
 * 35 Dream       - a sleepy crescent moon in the corner, sheep-shaped clouds drifting round it, stars twinkling
 */
final class FxBookAuras extends FxBase {
   private FxBookAuras() {}

   static final float[] SILVER = {0.78F, 0.82F, 0.9F}, IVORY_C = {0.95F, 0.93F, 0.86F}, ROCK = {0.42F, 0.33F, 0.24F}, ROCK_D = {0.26F, 0.2F, 0.15F},
         INK = {0.03F, 0.03F, 0.05F}, BLOOD = {0.45F, 0.02F, 0.04F}, CARD = {0.96F, 0.95F, 0.92F}, CARD_BACK = {0.22F, 0.08F, 0.34F},
         HOLE = {0.0F, 0.0F, 0.01F}, CLOUD = {0.86F, 0.88F, 0.92F}, CLOUD_D = {0.6F, 0.64F, 0.72F}, RED = {0.8F, 0.06F, 0.08F}, GOLD = {1.0F, 0.82F, 0.3F};

   static Vec3 P(double x, double y, double z) { return new Vec3(x, y, z); }
   static Vec3 polar(double a, double rx, double ry, double z) { return new Vec3(Math.cos(a) * rx, Math.sin(a) * ry, z); }
   /** A repeatable pseudo-random number in [0, 1). */
   static double h(double k) { double v = Math.sin(k * 12.9898 + 78.233) * 43758.5453; return v - Math.floor(v); }

   static void gl(Matrix4f m, Vec3 a, Vec3 b, float w, int pal, float al) { FxBooks.lline(m, a, b, w, pal, al, al); }

   /** A glowing (possibly partial, possibly turning) ellipse. */
   static void ring(Matrix4f m, double rx, double ry, double z, int pal, float al, float w, int seg, double rot) {
      Vec3 prev = null;
      for (int i = 0; i <= seg; i++) {
         Vec3 q = polar(rot + Math.PI * 2.0 * i / seg, rx, ry, z);
         if (prev != null) gl(m, prev, q, w, pal, al);
         prev = q;
      }
   }

   /** A glowing ellipse centred on c. */
   static void ringAt(Matrix4f m, Vec3 c, double rx, double ry, int pal, float al, float w, int seg, double rot) {
      Vec3 prev = null;
      for (int i = 0; i <= seg; i++) {
         Vec3 q = c.add(polar(rot + Math.PI * 2.0 * i / seg, rx, ry, 0));
         if (prev != null) gl(m, prev, q, w, pal, al);
         prev = q;
      }
   }

   /** A solid flat stroke from a to b, w wide, in the book's plane. */
   static void stroke(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 a, Vec3 b, float w0, float w1, float[] col, float al) {
      Vec3 ax = b.subtract(a);
      Vec3 side = ax.cross(new Vec3(0, 0, 1));
      if (side.lengthSqr() < 1.0E-10) return;
      side = side.normalize();
      Vec3 s0 = side.scale(w0 * 0.5), s1 = side.scale(w1 * 0.5);
      quad(vc, m, n, Vec3.ZERO, a.add(s0), b.add(s1), b.subtract(s1), a.subtract(s0), col, al, al, al, al);
   }

   /** A solid flat disc (or ellipse). */
   static void disc(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 c, double rx, double ry, float[] col, float al, int seg) {
      for (int i = 0; i < seg; i++) {
         Vec3 q0 = c.add(polar(Math.PI * 2.0 * i / seg, rx, ry, 0)), q1 = c.add(polar(Math.PI * 2.0 * (i + 1) / seg, rx, ry, 0));
         quad(vc, m, n, Vec3.ZERO, c, c, q1, q0, col, al, al, al, al);
      }
   }

   /** A solid band between two ellipses (a disc with the middle left open, so the book shows through). */
   static void band(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 c, double r0, double r1, double sy, float[] col, float al, int seg) {
      for (int i = 0; i < seg; i++) {
         double a0 = Math.PI * 2.0 * i / seg, a1 = Math.PI * 2.0 * (i + 1) / seg;
         quad(vc, m, n, Vec3.ZERO, c.add(polar(a0, r0, r0 * sy, 0)), c.add(polar(a1, r0, r0 * sy, 0)), c.add(polar(a1, r1, r1 * sy, 0)), c.add(polar(a0, r1, r1 * sy, 0)), col, al, al, al, al);
      }
   }

   /** A soft glowing disc fading to its edge. */
   static void glow(Matrix4f m, Vec3 c, double r, float[] col, float al) {
      for (int i = 0; i < 16; i++) {
         Vec3 q0 = c.add(polar(Math.PI * 2.0 * i / 16, r, r, 0)), q1 = c.add(polar(Math.PI * 2.0 * (i + 1) / 16, r, r, 0));
         gquad(m, Vec3.ZERO, c, c, q1, q0, col, al, al, 0.0F, 0.0F);
      }
   }

   /** A flat rectangle centred on c, turned by ang (radians) in the book's plane. */
   static Vec3[] rect(Vec3 c, double hw, double hh, double ang) {
      double cs = Math.cos(ang), sn = Math.sin(ang);
      double[][] k = {{-hw, -hh}, {hw, -hh}, {hw, hh}, {-hw, hh}};
      Vec3[] r = new Vec3[4];
      for (int i = 0; i < 4; i++) r[i] = c.add(k[i][0] * cs - k[i][1] * sn, k[i][0] * sn + k[i][1] * cs, 0);
      return r;
   }

   static void fill(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3[] r, float[] col, float al) { quad(vc, m, n, Vec3.ZERO, r[0], r[1], r[2], r[3], col, al, al, al, al); }
   static void outline(Matrix4f m, Vec3[] r, float w, int pal, float al) { for (int i = 0; i < 4; i++) gl(m, r[i], r[(i + 1) % 4], w, pal, al); }

   /** A flame tongue standing on base point b, pointing along dir. */
   static void tongue(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 b, Vec3 dir, double len, double wid, int pal, float al, boolean smoke) {
      Vec3 side = dir.cross(new Vec3(0, 0, 1)).normalize().scale(wid);
      Vec3 tip = b.add(dir.scale(len));
      gquad(m, Vec3.ZERO, b.add(side), b.subtract(side), tip, tip, RIM[pal], al * 0.8F, al * 0.8F, 0.0F, 0.0F);
      Vec3 tip2 = b.add(dir.scale(len * 0.55));
      gquad(m, Vec3.ZERO, b.add(side.scale(0.5)), b.subtract(side.scale(0.5)), tip2, tip2, hot(pal), al, al, 0.0F, 0.0F);
      if (smoke) quad(vc, m, n, Vec3.ZERO, b.add(side.scale(1.3)), b.subtract(side.scale(1.3)), tip.add(dir.scale(len * 0.2)), tip.add(dir.scale(len * 0.2)), SMOKE, al * 0.55F, al * 0.55F, 0.0F, 0.0F);
   }

   static void draw(VertexConsumer vc, Matrix4f m, Matrix3f n, FxRenderer.BookDraw b, float HW, float HH, float al) {
      double t = bookTime;
      int pal = b.pal();
      final double Z = -0.09;   // just behind the book
      switch (b.style()) {
         case 7 -> {   // Mercury: liquid metal
            FxBooks.underglow(m, HW, HH, pal, 0.18F * al);
            Vec3 prev = null;
            for (int i = 0; i <= 48; i++) {
               double a = Math.PI * 2.0 * i / 48;
               double r = 0.64 + 0.035 * Math.sin(a * 5 + t * 0.12) + 0.02 * Math.sin(a * 3 - t * 0.07);
               Vec3 q = polar(a, r, r * 0.92, Z);
               if (prev != null) { stroke(vc, m, n, prev, q, 0.028F, 0.028F, SILVER, al * 0.85F); gl(m, prev, q, 0.006F, pal, al * 0.5F); }
               prev = q;
            }
            for (int k = 0; k < 5; k++) {
               double a = t * (0.035 + 0.008 * k) + k * 1.25;
               double r = 0.64 + 0.035 * Math.sin(a * 5 + t * 0.12);
               Vec3 c = polar(a, r, r * 0.92, Z + 0.02);
               double stretch = 1.0 + 0.4 * Math.abs(Math.sin(t * 0.09 + k));
               Vec3 tan = new Vec3(-Math.sin(a), Math.cos(a), 0).scale(0.045 * stretch);
               stroke(vc, m, n, c.subtract(tan), c.add(tan), 0.07F, 0.05F, SILVER, al);
               FxBooks.lstar(m, c.add(-0.012, 0.015, 0.01), 0.03F, pal, al * 0.8F);
            }
         }
         case 8 -> {   // Fire: flames licking up round it
            FxBooks.underglow(m, HW, HH, pal, 0.35F * al);
            for (int k = 0; k < 14; k++) {
               double a = Math.PI * 2.0 * k / 14 + 0.2;
               Vec3 base = polar(a, 0.6, 0.55, Z);
               double fl = 0.5 + 0.5 * Math.sin(t * 0.35 + k * 2.3) * Math.sin(t * 0.21 + k);
               double up = 0.1 + 0.12 * fl + (Math.sin(a) < 0 ? 0.0 : 0.05);
               Vec3 dir = new Vec3(Math.cos(a) * 0.35, 1.0, 0).normalize();
               tongue(vc, m, n, base, dir, up, 0.045, pal, al, false);
            }
            for (int k = 0; k < 7; k++) {
               double ph = (t * 0.02 + k / 7.0) % 1.0;
               Vec3 c = P(Math.sin(k * 3.1 + ph * 3) * 0.5, -0.3 + ph * 1.1, 0.05);
               FxBooks.lstar(m, c, 0.025F, pal, al * (float)Math.sin(Math.PI * ph));
            }
         }
         case 9 -> {   // Lightning: arcs crackling
            FxBooks.underglow(m, HW, HH, pal, 0.22F * al);
            long strike = (long)Math.floor(t / 3.0);
            for (int k = 0; k < 4; k++) {
               double s = strike * 7 + k * 13;
               double a0 = h(s) * Math.PI * 2, a1 = a0 + 0.8 + h(s + 1) * 1.6;
               Vec3 p0 = polar(a0, 0.7, 0.62, Z), p1 = polar(a1, 0.7, 0.62, Z);
               float flick = (float)(0.6 + 0.4 * h(s + Math.floor(t)));
               Vec3 prev = p0;
               for (int i = 1; i <= 7; i++) {
                  double u = i / 7.0;
                  Vec3 q = i == 7 ? p1 : p0.add(p1.subtract(p0).scale(u)).add((h(s + i * 3.3) - 0.5) * 0.14, (h(s + i * 5.7) - 0.5) * 0.14, 0);
                  gl(m, prev, q, 0.018F, pal, al * flick);
                  prev = q;
               }
               FxBooks.lstar(m, p0, 0.05F, pal, al * flick);
            }
            ring(m, 0.7, 0.62, Z, pal, al * 0.18F, 0.01F, 40, 0);
         }
         case 10 -> {   // Hellfire: a crown of crimson flame
            FxBooks.underglow(m, HW, HH, pal, 0.45F * al);
            for (int k = 0; k < 18; k++) {
               double a = Math.PI * 2.0 * k / 18 + t * 0.01;
               Vec3 base = polar(a, 0.56, 0.52, Z);
               double fl = 0.5 + 0.5 * Math.sin(t * 0.4 + k * 1.7);
               tongue(vc, m, n, base, polar(a, 1, 1, 0).add(0, 0.25, 0).normalize(), 0.12 + 0.14 * fl, 0.05, pal, al, true);
            }
         }
         case 11 -> {   // Bone: vertebrae and circling bones
            FxBooks.underglow(m, HW, HH, pal, 0.14F * al);
            double breathe = 1.0 + 0.03 * Math.sin(t * 0.06);
            for (int i = 0; i < 9; i++) {   // the vertebrae - above and below the book, never across it
               if (i >= 2 && i <= 6) continue;
               double y = -0.82 + i * 0.205;
               disc(vc, m, n, P(0, y, Z - 0.02), 0.06, 0.045, IVORY_C, al * 0.9F, 10);
               stroke(vc, m, n, P(-0.08, y, Z - 0.02), P(0.08, y, Z - 0.02), 0.02F, 0.02F, IVORY_C, al * 0.8F);
            }
            for (int k = 0; k < 6; k++) {   // bones circling the book, outside its edge: a shaft with a knob at each end
               double a = t * 0.02 + k * Math.PI / 3;
               Vec3 c = polar(a, 0.76 * breathe, 0.62 * breathe, Z - 0.01);
               Vec3 d = P(-Math.sin(a), Math.cos(a), 0).scale(0.075).add(polar(a + 1.2, 0.02, 0.02, 0));
               stroke(vc, m, n, c.subtract(d), c.add(d), 0.026F, 0.026F, IVORY_C, al * 0.9F);
               for (int e = -1; e <= 1; e += 2) {
                  Vec3 end = c.add(d.scale(e));
                  Vec3 nn = P(-d.y, d.x, 0).normalize().scale(0.018);
                  disc(vc, m, n, end.add(nn), 0.02, 0.02, IVORY_C, al * 0.9F, 8);
                  disc(vc, m, n, end.subtract(nn), 0.02, 0.02, IVORY_C, al * 0.9F, 8);
               }
            }
         }
         case 12 -> {   // Thread: weaving threads and a needle
            FxBooks.underglow(m, HW, HH, pal, 0.18F * al);
            Vec3 needleAt = null, needleDir = null;
            for (int k = 0; k < 3; k++) {
               Vec3 prev = null;
               for (int i = 0; i <= 30; i++) {
                  double u = i / 30.0;
                  double x = -0.85 + u * 1.7;
                  double y = -0.3 + k * 0.3 + 0.12 * Math.sin(u * Math.PI * 3 + t * 0.06 + k * 2.1);
                  double z = 0.1 * Math.cos(u * Math.PI * 6 + k * Math.PI);   // over and under the book
                  Vec3 q = P(x, y, z);
                  if (prev != null) gl(m, prev, q, 0.009F, pal, al * 0.9F);
                  if (k == 1 && i == (int)((t * 0.25) % 30)) { needleAt = q; needleDir = prev == null ? P(1, 0, 0) : q.subtract(prev).normalize(); }
                  prev = q;
               }
            }
            if (needleAt != null) {
               stroke(vc, m, n, needleAt.subtract(needleDir.scale(0.09)), needleAt.add(needleDir.scale(0.07)), 0.018F, 0.004F, SILVER, al);
               FxBooks.lstar(m, needleAt.add(needleDir.scale(0.07)), 0.03F, pal, al);
            }
         }
         case 13 -> {   // Space: an oval gate
            band(vc, m, n, P(0, 0.02, Z - 0.03), 0.56, 0.64, 1.19, new float[]{0.05F, 0.02F, 0.1F}, al * 0.6F, 40);
            for (int k = 0; k < 3; k++) {   // the swirl inside
               Vec3 prev = null;
               for (int i = 0; i <= 16; i++) {
                  double u = i / 16.0;
                  double a = t * 0.05 + k * Math.PI * 2 / 3 + u * 3.2;
                  Vec3 q = polar(a, 0.08 + u * 0.5, (0.08 + u * 0.5) * 1.2, Z - 0.02).add(0, 0.02, 0);
                  if (prev != null) gl(m, prev, q, 0.01F, pal, al * (float)(1 - u) * 0.4F);
                  prev = q;
               }
            }
            ring(m, 0.64, 0.76, Z, 8, al * 0.85F, 0.022F, 48, 0);   // the gold rim
            for (int k = 0; k < 4; k++) FxBooks.lstar(m, polar(t * 0.02 + k * Math.PI / 2, 0.64, 0.76, Z + 0.01), 0.05F, 8, al * 0.8F);
         }
         case 14 -> {   // Water: ripples and bubbles
            FxBooks.underglow(m, HW, HH, pal, 0.22F * al);
            for (int k = 0; k < 3; k++) {
               double ph = (t * 0.012 + k / 3.0) % 1.0;
               double r = 0.35 + 0.55 * ph;
               ring(m, r, r * 0.85, Z, pal, al * (float)(1 - ph) * 0.8F, 0.016F, 40, 0);
            }
            for (int k = 0; k < 6; k++) {
               double ph = (t * 0.018 + k / 6.0) % 1.0;
               double r = 0.025 + 0.015 * h(k);
               Vec3 c = P((h(k + 3) - 0.5) * 1.1 + 0.03 * Math.sin(t * 0.2 + k), -0.45 + ph * 1.1, 0.06);
               ringAt(m, c, r, r, pal, al * (float)Math.sin(Math.PI * ph), 0.006F, 10, 0);
               FxBooks.lstar(m, c.add(-r * 0.4, r * 0.4, 0), 0.012F, pal, al * (float)Math.sin(Math.PI * ph));
            }
         }
         case 15 -> {   // Mirror: four panes turning round it
            FxBooks.underglow(m, HW, HH, pal, 0.14F * al);
            for (int k = 0; k < 4; k++) {
               double a = t * 0.025 + k * Math.PI / 2;
               Vec3 c = polar(a, 0.72, 0.6, Z + 0.05 * Math.sin(a));
               Vec3[] r = rect(c, 0.1, 0.15, a + Math.PI / 2 + 0.3 * Math.sin(t * 0.03 + k));
               fill(vc, m, n, r, new float[]{0.7F, 0.76F, 0.86F}, al * 0.4F);
               outline(m, r, 0.008F, pal, al * 0.85F);
               double g = (t * 0.03 + k * 0.37) % 1.0;   // the glint sweeping across
               if (g < 0.5) {
                  double u = g * 2;
                  Vec3 a0 = r[0].add(r[1].subtract(r[0]).scale(u)), a1 = r[3].add(r[2].subtract(r[3]).scale(Math.max(0, u - 0.3)));
                  gl(m, a0, a1, 0.012F, pal, al * (float)Math.sin(Math.PI * u));
               }
            }
         }
         case 16 -> {   // Lion flame: a bristling mane
            FxBooks.underglow(m, HW, HH, pal, 0.4F * al);
            for (int k = 0; k < 22; k++) {
               double a = Math.PI * 2.0 * k / 22;
               double fl = 0.5 + 0.5 * Math.sin(t * 0.3 + k * 2.7);
               double len = (k % 2 == 0 ? 0.2 : 0.13) + 0.08 * fl;
               tongue(vc, m, n, polar(a, 0.55, 0.55, Z), polar(a + 0.25 * Math.sin(t * 0.05), 1, 1, 0), len, 0.04, pal, al, false);
            }
         }
         case 17 -> {   // Slash: cuts flashing in the air
            FxBooks.underglow(m, HW, HH, pal, 0.15F * al);
            for (int k = 0; k < 3; k++) {
               double ph = (t * 0.03 + k / 3.0) % 1.0;
               long cyc = (long)Math.floor(t * 0.03 + k / 3.0);
               double s = cyc * 3 + k;
               double ang = h(s) * Math.PI;
               Vec3 c = P((h(s + 1) - 0.5) * 0.9, (h(s + 2) - 0.5) * 0.8, 0.05);
               Vec3 d = P(Math.cos(ang), Math.sin(ang), 0).scale(0.38);
               double draw = Math.min(1, ph * 5);   // drawn in a flash...
               float fade = (float)(ph < 0.2 ? 1 : 1 - (ph - 0.2) / 0.8);   // ...then fading
               Vec3 a0 = c.subtract(d), a1 = c.subtract(d).add(d.scale(2 * draw));
               Vec3 bow = P(-d.y, d.x, 0).scale(0.12);
               Vec3 prev = a0;
               for (int i = 1; i <= 8; i++) {
                  double u = i / 8.0;
                  Vec3 q = a0.add(a1.subtract(a0).scale(u)).add(bow.scale(Math.sin(Math.PI * u)));
                  float w = (float)(0.03 * Math.sin(Math.PI * u) + 0.004);
                  gl(m, prev, q, w, pal, al * fade);
                  prev = q;
               }
            }
         }
         case 18 -> {   // Earth: rocks in orbit
            FxBooks.underglow(m, HW, HH, pal, 0.18F * al);
            for (int k = 0; k < 7; k++) {
               double a = t * 0.03 + k * Math.PI * 2 / 7;
               Vec3 c = P(Math.cos(a) * 0.78, Math.sin(a) * 0.62, Math.sin(a) * 0.15);
               double sz = 0.045 + 0.03 * h(k);
               Vec3[] r = rect(c, sz, sz * 0.8, t * 0.04 * (k % 2 == 0 ? 1 : -1) + k);
               fill(vc, m, n, r, ROCK, al);
               fill(vc, m, n, rect(c.add(sz * 0.2, -sz * 0.2, 0.002), sz * 0.6, sz * 0.4, k), ROCK_D, al);
            }
            for (int k = 0; k < 6; k++) {
               double ph = (t * 0.01 + k / 6.0) % 1.0;
               FxBooks.lstar(m, P(Math.cos(k * 1.9) * 0.6, -0.4 + ph * 0.5, 0.08), 0.018F, pal, al * (float)Math.sin(Math.PI * ph) * 0.7F);
            }
         }
         case 19 -> {   // Time: a clock face
            ring(m, 0.68, 0.68, Z, pal, al * 0.7F, 0.016F, 48, 0);
            ring(m, 0.6, 0.6, Z, pal, al * 0.35F, 0.008F, 48, 0);
            for (int k = 0; k < 12; k++) {
               double a = Math.PI * 2 * k / 12;
               double l = k % 3 == 0 ? 0.12 : 0.06;
               gl(m, polar(a, 0.68, 0.68, Z), polar(a, 0.68 - l, 0.68 - l, Z), k % 3 == 0 ? 0.018F : 0.01F, pal, al * 0.8F);
            }
            double tick = Math.floor(t / 20.0);   // the long hand ticks once a second
            double am = Math.PI / 2 - tick * Math.PI * 2 / 60, ah = Math.PI / 2 - t * 0.0015;
            gl(m, P(0, 0, Z), polar(am, 0.55, 0.55, Z), 0.014F, pal, al);
            gl(m, P(0, 0, Z), polar(ah, 0.36, 0.36, Z), 0.022F, pal, al);
            FxBooks.lstar(m, P(0, 0, Z + 0.01), 0.05F, pal, al);
         }
         case 20 -> {   // Blood: a ring of blood, dripping
            FxBooks.underglow(m, HW, HH, pal, 0.3F * al);
            Vec3 prev = null;
            for (int i = 0; i <= 40; i++) {
               Vec3 q = polar(Math.PI * 2 * i / 40 + t * 0.01, 0.63, 0.6, Z);
               if (prev != null) { stroke(vc, m, n, prev, q, 0.04F, 0.04F, BLOOD, al * 0.9F); gl(m, prev, q, 0.006F, pal, al * 0.45F); }
               prev = q;
            }
            for (int k = 0; k < 6; k++) {
               double a = Math.PI * (1.15 + 0.7 * k / 5.0);   // along the lower half
               double ph = (t * 0.008 + h(k)) % 1.0;
               Vec3 top = polar(a, 0.63, 0.6, Z);
               double len = 0.06 + 0.3 * ph;
               Vec3 bot = top.add(0, -len, 0);
               stroke(vc, m, n, top, bot, 0.026F, 0.012F, BLOOD, al * (float)(1 - ph * 0.6));
               disc(vc, m, n, bot, 0.018, 0.024, BLOOD, al * (float)(1 - ph * 0.6), 8);
            }
         }
         case 21 -> {   // Gravity: pulled in
            FxBooks.underglow(m, HW, HH, pal, 0.22F * al);
            for (int k = 0; k < 7; k++) {
               double ph = (t * 0.012 + k / 7.0) % 1.0;
               Vec3 prev = null;
               for (int i = 0; i <= 10; i++) {
                  double u = ph + i * 0.025;
                  if (u > 1) break;
                  double r = 0.95 * (1 - u) + 0.18 * u;
                  double a = k * Math.PI * 2 / 7 + u * 2.6;
                  Vec3 q = polar(a, r, r * 0.85, Z);
                  if (prev != null) gl(m, prev, q, (float)(0.012 * (1 - u) + 0.004), pal, al * (float)(1 - Math.abs(u - 0.5) * 1.6) * (i / 10F));
                  prev = q;
               }
            }
            for (int k = 0; k < 5; k++) {
               double a = t * 0.12 + k * Math.PI * 2 / 5;
               Vec3 c = polar(a, 0.7, 0.56, 0.08 * Math.sin(a));
               fill(vc, m, n, rect(c, 0.025, 0.02, a), ROCK_D, al);
            }
         }
         case 22 -> {   // Sound: waves pulsing out to both sides
            FxBooks.underglow(m, HW, HH, pal, 0.2F * al);
            for (int s = -1; s <= 1; s += 2) {
               for (int k = 0; k < 4; k++) {
                  double ph = (t * 0.025 + k / 4.0) % 1.0;
                  double r = 0.42 + 0.5 * ph;
                  Vec3 prev = null;
                  for (int i = 0; i <= 10; i++) {
                     double a = (s > 0 ? 0 : Math.PI) + (i / 10.0 - 0.5) * 1.3;
                     Vec3 q = polar(a, r, r, Z);
                     if (prev != null) gl(m, prev, q, 0.015F, pal, al * (float)(1 - ph) * (float)Math.sin(Math.PI * i / 10.0));
                     prev = q;
                  }
               }
            }
            for (int k = 0; k < 7; k++) {   // a little level meter under the book
               double hgt = 0.04 + 0.1 * Math.abs(Math.sin(t * 0.2 + k * 1.3) * Math.sin(t * 0.07 + k));
               double x = -0.24 + k * 0.08;
               gl(m, P(x, -0.62, Z), P(x, -0.62 + hgt, Z), 0.02F, pal, al * 0.8F);
            }
         }
         case 23 -> {   // Ink: a brush circle drawn, then washed away
            double cyc = (t * 0.006) % 1.0;
            double drawn = Math.min(1, cyc * 2.5);
            float fade = (float)(cyc < 0.7 ? 1 : 1 - (cyc - 0.7) / 0.3);
            double a0 = Math.PI * 0.6 + Math.floor(t * 0.006) * 1.3;
            int N = 36;
            Vec3 prev = null;
            for (int i = 0; i <= (int)(N * drawn * 0.9); i++) {   // an open circle, as a brush leaves it
               double u = i / (double)N;
               double a = a0 - u * Math.PI * 2 * 0.9;
               double r = 0.64 + 0.03 * Math.sin(u * 9);
               Vec3 q = polar(a, r, r * 0.95, Z);
               float w = (float)(0.09 * (1 - u * 0.75) * (0.8 + 0.2 * Math.sin(u * 23)));
               if (prev != null) { stroke(vc, m, n, prev, q, w, w, INK, al * fade * 0.9F); gl(m, prev, q, w * 0.25F, pal, al * fade * 0.25F); }
               prev = q;
            }
            for (int k = 0; k < 7; k++) {   // spatter
               double a = a0 + h(k) * 0.8 - 0.4;
               disc(vc, m, n, polar(a, 0.64 + 0.08 + 0.1 * h(k + 9), 0.64 + 0.08 + 0.1 * h(k + 9), Z), 0.012 + 0.012 * h(k + 4), 0.012 + 0.012 * h(k + 4), INK, al * fade * (float)Math.min(1, drawn * 3), 8);
            }
         }
         case 24 -> {   // Error: glitches
            FxBooks.underglow(m, HW, HH, pal, 0.18F * al);
            long g = (long)Math.floor(t / 4.0);
            float[] cyan = {0.1F, 1.0F, 0.95F};
            for (int k = 0; k < 9; k++) {
               double s = g * 11 + k;
               if (h(s + 0.5) < 0.3) continue;   // some blink out
               Vec3 c = P((h(s) - 0.5) * 1.5, (h(s + 1) - 0.5) * 1.3, Z + 0.01 * k);
               if (Math.abs(c.x) < 0.55 && Math.abs(c.y) < 0.5) c = c.add(c.x < 0 ? -0.55 : 0.55, 0, 0);   // keep off the pages
               Vec3[] r = rect(c, 0.03 + 0.09 * h(s + 2), 0.012 + 0.03 * h(s + 3), 0);
               gquad(m, Vec3.ZERO, r[0], r[1], r[2], r[3], k % 2 == 0 ? RIM[pal] : cyan, al * 0.6F, al * 0.6F, al * 0.6F, al * 0.6F);
               fill(vc, m, n, rect(c.add(0.015, -0.01, -0.003), 0.03 + 0.09 * h(s + 2), 0.012 + 0.03 * h(s + 3), 0), INK, al * 0.4F);
            }
            for (int k = 0; k < 4; k++) {   // scanlines
               double y = ((t * 0.02 + k * 0.25) % 1.0) * 1.4 - 0.7;
               gl(m, P(-0.75, y, 0.06), P(0.75, y, 0.06), 0.004F, pal, al * 0.35F);
            }
         }
         case 25 -> {   // Treasure: golden ripples with blade points
            FxBooks.underglow(m, HW, HH, pal, 0.25F * al);
            for (int k = 0; k < 5; k++) {
               double a = Math.PI * (0.15 + 0.7 * k / 4.0);
               Vec3 c = polar(a, 0.72, 0.62, Z);
               double rr = 0.08 + 0.01 * Math.sin(t * 0.1 + k);
               ringAt(m, c, rr, rr, pal, al * 0.8F, 0.01F, 18, 0);
               ringAt(m, c, rr * 0.55, rr * 0.55, pal, al * 0.5F, 0.008F, 14, t * 0.05);
               double out = 0.06 + 0.06 * (0.5 + 0.5 * Math.sin(t * 0.04 + k * 1.3));
               Vec3 dir = polar(a, 1, 1, 0).normalize();
               Vec3 tip = c.add(dir.scale(out + 0.05)), side = P(-dir.y, dir.x, 0).scale(0.022);
               quad(vc, m, n, Vec3.ZERO, c.add(side), c.subtract(side), tip, tip, SILVER, al, al, al, al);
               gl(m, c, tip, 0.006F, pal, al * 0.6F);
            }
         }
         case 26 -> {   // Chain: a loop of links running round
            FxBooks.underglow(m, HW, HH, pal, 0.14F * al);
            int L = 18;
            for (int k = 0; k < L; k++) {
               double a = Math.PI * 2 * k / L + t * 0.015;
               Vec3 c = polar(a, 0.7, 0.6, Z);
               Vec3 tan = P(-Math.sin(a) * 0.7, Math.cos(a) * 0.6, 0).normalize();
               if (k % 2 == 0) {   // a link seen flat: a rounded loop
                  Vec3 nn = P(-tan.y, tan.x, 0);
                  Vec3 prev = null;
                  for (int i = 0; i <= 12; i++) {
                     double u = Math.PI * 2 * i / 12;
                     Vec3 q = c.add(tan.scale(Math.cos(u) * 0.075)).add(nn.scale(Math.sin(u) * 0.04));
                     if (prev != null) { stroke(vc, m, n, prev, q, 0.016F, 0.016F, SILVER, al); gl(m, prev, q, 0.004F, pal, al * 0.4F); }
                     prev = q;
                  }
               } else {   // a link seen edge-on: a bar
                  stroke(vc, m, n, c.subtract(tan.scale(0.075)), c.add(tan.scale(0.075)), 0.02F, 0.02F, SILVER, al);
               }
            }
         }
         case 27 -> {   // Hunter: a blood moon and claw marks
            Vec3 mc = P(0.66, 0.6, Z - 0.03);   // up in the corner, clear of the pages
            glow(m, mc, 0.34, RIM[pal], al * 0.25F);
            disc(vc, m, n, mc, 0.2, 0.2, RED, al * 0.85F, 28);
            disc(vc, m, n, mc.add(0.07, 0.035, 0.003), 0.17, 0.17, new float[]{0.25F, 0.0F, 0.02F}, al * 0.5F, 28);   // its dark side
            for (int k = 0; k < 3; k++) {
               double off = (k - 1) * 0.12;
               Vec3 a0 = P(-0.85 + off, 0.25, 0.06), a1 = P(-0.45 + off, -0.6, 0.06);   // raked down the left side
               Vec3 prev = a0;
               for (int i = 1; i <= 8; i++) {
                  double u = i / 8.0;
                  Vec3 q = a0.add(a1.subtract(a0).scale(u)).add(0.05 * Math.sin(Math.PI * u), 0, 0);
                  gl(m, prev, q, (float)(0.025 * Math.sin(Math.PI * u) + 0.003), pal, al * 0.75F);
                  prev = q;
               }
            }
         }
         case 28 -> {   // Void: a black hole
            Vec3 c = P(0, 0, Z - 0.04);
            glow(m, c, 0.9, RIM[pal], al * 0.12F);
            band(vc, m, n, c, 0.56, 0.72, 1.0, HOLE, al * 0.85F, 40);   // the event horizon, round the book
            ring(m, 0.55, 0.55, Z - 0.03, pal, al * 0.9F, 0.016F, 48, 0);
            ring(m, 0.73, 0.73, Z - 0.03, pal, al * 0.4F, 0.01F, 48, 0);
            for (int k = 0; k < 10; k++) {   // motes falling in
               double ph = (t * 0.01 + k / 10.0) % 1.0;
               double r = 1.05 - 0.35 * ph;
               double a = k * 2.4 + ph * 3.5;
               FxBooks.lstar(m, polar(a, r, r, Z - 0.02), (float)(0.03 * (1 - ph) + 0.008), pal, al * (float)(1 - ph));
            }
         }
         case 29 -> {   // Gambler: cards and dice
            FxBooks.underglow(m, HW, HH, pal, 0.18F * al);
            for (int k = 0; k < 4; k++) {
               double a = t * 0.02 + k * Math.PI / 2;
               Vec3 c = polar(a, 0.74, 0.58, Z + 0.06 * Math.sin(a));
               Vec3[] r = rect(c, 0.07, 0.1, a * 0.5 + k);
               fill(vc, m, n, r, CARD, al);
               outline(m, r, 0.005F, pal, al * 0.6F);
               Vec3 pip = c.add(0, 0, 0.003);
               disc(vc, m, n, pip, 0.022, 0.026, k % 2 == 0 ? RED : INK, al, 8);
            }
            for (int k = 0; k < 2; k++) {
               double a = -t * 0.035 + k * Math.PI + 0.8;
               Vec3 c = polar(a, 0.5, 0.75, Z + 0.04);
               double spin = t * 0.12 * (k == 0 ? 1 : -1);
               fill(vc, m, n, rect(c, 0.05, 0.05, spin), CARD, al);
               int face = 1 + (int)(Math.floor(t / 15.0 + k * 3) % 6);
               for (int d = 0; d < face; d++) {
                  double da = d * Math.PI * 2 / Math.max(1, face) + spin;
                  Vec3 dp = face == 1 ? c : c.add(Math.cos(da) * 0.025, Math.sin(da) * 0.025, 0);
                  disc(vc, m, n, dp.add(0, 0, 0.003), 0.008, 0.008, INK, al, 6);
               }
            }
         }
         case 30 -> {   // Tarot: three cards fanned out behind it
            FxBooks.underglow(m, HW, HH, pal, 0.2F * al);
            for (int k = -1; k <= 1; k++) {
               double sway = 0.04 * Math.sin(t * 0.03 + k);
               double ang = -k * 0.38 + sway;
               Vec3 c = P(k * 0.3, 0.78 - Math.abs(k) * 0.06, Z - 0.02 - Math.abs(k) * 0.01);   // fanned above the book
               Vec3[] r = rect(c, 0.11, 0.17, ang);
               fill(vc, m, n, r, CARD_BACK, al * 0.95F);
               outline(m, r, 0.008F, 8, al * 0.9F);
               Vec3 g = c.add(0, 0, 0.004);
               if (k == -1) { // the moon
                  disc(vc, m, n, g, 0.045, 0.045, GOLD, al, 16);
                  disc(vc, m, n, g.add(0.022, 0.011, 0.001), 0.038, 0.038, CARD_BACK, al, 16);
               } else if (k == 0) { // the star
                  for (int s = 0; s < 4; s++) { double sa = s * Math.PI / 4 + ang; gl(m, g.add(Math.cos(sa) * -0.06, Math.sin(sa) * -0.06, 0), g.add(Math.cos(sa) * 0.06, Math.sin(sa) * 0.06, 0), 0.008F, 8, al); }
               } else { // the sun
                  disc(vc, m, n, g, 0.034, 0.034, GOLD, al, 16);
                  for (int s = 0; s < 8; s++) { double sa = s * Math.PI / 4 + t * 0.02; gl(m, g.add(Math.cos(sa) * 0.045, Math.sin(sa) * 0.045, 0), g.add(Math.cos(sa) * 0.07, Math.sin(sa) * 0.07, 0), 0.007F, 8, al); }
               }
            }
         }
         case 31 -> {   // Abyss: jellyfish and bubbles
            FxBooks.underglow(m, HW, HH, pal, 0.22F * al);
            for (int k = 0; k < 3; k++) {
               double a = Math.PI * (0.25 + k * 0.65);
               Vec3 c = polar(a, 0.66, 0.5, Z).add(0, 0.04 * Math.sin(t * 0.06 + k * 2), 0);
               double pulse = 1 + 0.12 * Math.sin(t * 0.12 + k);
               Vec3 prev = null;
               for (int i = 0; i <= 12; i++) {   // the bell
                  double u = Math.PI * i / 12;
                  Vec3 q = c.add(Math.cos(u) * 0.08 * pulse, Math.sin(u) * 0.07, 0);
                  if (prev != null) gl(m, prev, q, 0.012F, pal, al * 0.9F);
                  prev = q;
               }
               glow(m, c.add(0, 0.025, 0), 0.07, RIM[pal], al * 0.35F);
               for (int s = 0; s < 4; s++) {   // trailing tentacles
                  double x0 = (s - 1.5) * 0.04 * pulse;
                  Vec3 tp = c.add(x0, 0, 0);
                  for (int i = 1; i <= 6; i++) {
                     Vec3 q = c.add(x0 + 0.015 * Math.sin(t * 0.15 + i + s), -i * 0.03, 0);
                     gl(m, tp, q, 0.005F, pal, al * (float)(1 - i / 7.0) * 0.8F);
                     tp = q;
                  }
               }
            }
            for (int k = 0; k < 5; k++) {
               double ph = (t * 0.015 + k / 5.0) % 1.0;
               double r = 0.015 + 0.01 * h(k);
               Vec3 b0 = P((h(k + 7) - 0.5) * 0.9, -0.5 + ph * 1.1, 0.05);
               for (int i = 0; i < 8; i++) gl(m, b0.add(polar(i * Math.PI / 4, r, r, 0)), b0.add(polar((i + 1) * Math.PI / 4, r, r, 0)), 0.004F, pal, al * (float)Math.sin(Math.PI * ph));
            }
         }
         case 32 -> {   // Mech: gear, brackets, scan line
            FxBooks.underglow(m, HW, HH, pal, 0.18F * al);
            double rot = t * 0.02;
            ring(m, 0.6, 0.6, Z, pal, al * 0.75F, 0.014F, 48, rot);
            ring(m, 0.48, 0.48, Z, pal, al * 0.3F, 0.008F, 48, rot);
            for (int k = 0; k < 16; k++) {   // the teeth
               double a0 = rot + k * Math.PI * 2 / 16 - 0.07, a1 = a0 + 0.14;
               Vec3 p0 = polar(a0, 0.6, 0.6, Z), p1 = polar(a0, 0.68, 0.68, Z), p2 = polar(a1, 0.68, 0.68, Z), p3 = polar(a1, 0.6, 0.6, Z);
               gl(m, p0, p1, 0.01F, pal, al * 0.75F); gl(m, p1, p2, 0.01F, pal, al * 0.75F); gl(m, p2, p3, 0.01F, pal, al * 0.75F);
            }
            for (int sx = -1; sx <= 1; sx += 2) for (int sy = -1; sy <= 1; sy += 2) {   // targeting brackets
               Vec3 c = P(sx * 0.82, sy * 0.68, 0.04);
               gl(m, c, c.add(-sx * 0.12, 0, 0), 0.012F, pal, al * 0.85F);
               gl(m, c, c.add(0, -sy * 0.12, 0), 0.012F, pal, al * 0.85F);
            }
            double y = Math.sin(t * 0.05) * 0.62;
            gl(m, P(-0.78, y, 0.07), P(0.78, y, 0.07), 0.006F, pal, al * 0.55F);
         }
         case 33 -> {   // Weather: a raincloud
            FxBooks.underglow(m, HW, HH, pal, 0.18F * al);
            Vec3 c = P(0.02 * Math.sin(t * 0.03), 0.8, Z + 0.02);
            disc(vc, m, n, c.add(0, -0.03, 0), 0.34, 0.07, CLOUD_D, al * 0.95F, 20);
            double[][] puff = {{-0.2, 0.0, 0.11}, {-0.06, 0.06, 0.14}, {0.1, 0.04, 0.13}, {0.24, -0.01, 0.09}};
            for (double[] pf : puff) disc(vc, m, n, c.add(pf[0], pf[1], 0.002), pf[2], pf[2] * 0.85, CLOUD, al * 0.95F, 16);
            for (int k = 0; k < 10; k++) {   // rain
               double ph = (t * 0.05 + h(k)) % 1.0;
               double x = (h(k + 2) < 0.5 ? -1 : 1) * (0.5 + 0.25 * h(k + 5)) - c.x;   // the rain falls either side of the book
               Vec3 top = P(x * 0.9, 0.7 - ph * 1.3, c.z + 0.01);
               gl(m, top, top.add(-0.02, -0.08, 0), 0.006F, pal, al * (float)(1 - ph) * 0.9F);
            }
            if ((long)Math.floor(t / 6.0) % 13 == 0) {   // now and then, a flash
               Vec3 p0 = c.add(0.3, -0.08, 0.02), p1 = p0.add(0.1, -0.16, 0), p2 = p1.add(-0.05, -0.02, 0), p3 = p2.add(0.12, -0.22, 0);   // off to the side
               gl(m, p0, p1, 0.014F, 4, al); gl(m, p1, p2, 0.014F, 4, al); gl(m, p2, p3, 0.014F, 4, al);
            }
         }
         case 34 -> {   // Overdrive: a heart-monitor trace and a pulse ring
            FxBooks.underglow(m, HW, HH, pal, 0.25F * al);
            double scroll = (t * 0.012) % 1.0;
            Vec3 prev = null;
            for (int i = 0; i <= 60; i++) {
               double u = i / 60.0;
               double x = -0.85 + 1.7 * u;
               double ph = (u + scroll) % 0.5 / 0.5;   // two beats across
               double y = -0.02;
               if (ph > 0.40 && ph < 0.44) y += 0.06;
               else if (ph > 0.46 && ph < 0.49) y -= 0.08;
               else if (ph > 0.49 && ph < 0.53) y += 0.3 * (1 - Math.abs(ph - 0.51) / 0.02);
               else if (ph > 0.53 && ph < 0.56) y -= 0.1;
               else if (ph > 0.66 && ph < 0.74) y += 0.05 * Math.sin((ph - 0.66) / 0.08 * Math.PI);
               Vec3 q = P(x, y, Z);
               float edge = (float)Math.min(1, Math.min(u, 1 - u) * 6);
               if (prev != null) gl(m, prev, q, 0.013F, pal, al * edge);
               prev = q;
            }
            double beat = (t * 0.024) % 1.0;   // in time with the trace
            double r = 0.3 + 0.6 * beat;
            ring(m, r, r * 0.9, Z - 0.01, pal, al * (float)(1 - beat) * 0.6F, 0.014F, 40, 0);
         }
         case 35 -> {   // Dream: a sleepy moon, drifting sheep-clouds, twinkling stars
            FxBooks.underglow(m, HW, HH, pal, 0.22F * al);
            Vec3 mc = P(-0.66, 0.6, Z - 0.02);   // up in the corner, clear of the pages
            glow(m, mc, 0.3, new float[]{1.0F, 0.95F, 0.78F}, al * 0.3F);
            disc(vc, m, n, mc, 0.17, 0.17, new float[]{1.0F, 0.95F, 0.78F}, al * 0.9F, 24);
            disc(vc, m, n, mc.add(0.07, 0.04, 0.003), 0.145, 0.145, new float[]{0.16F, 0.12F, 0.3F}, al * 0.9F, 24);
            float[] fluff = {0.97F, 0.95F, 0.98F}, face = {0.3F, 0.24F, 0.42F};
            for (int k = 0; k < 3; k++) {   // sheep-clouds drifting round the outside
               double a = t * 0.012 + k * Math.PI * 2 / 3;
               Vec3 c = polar(a, 0.82, 0.66, Z + 0.02).add(0, 0.03 * Math.sin(t * 0.08 + k), 0);
               for (int b2 = 0; b2 < 4; b2++) disc(vc, m, n, c.add(Math.cos(b2 * 1.6) * 0.05, Math.sin(b2 * 2.3) * 0.03, 0.001 * b2), 0.055, 0.045, fluff, al * 0.9F, 10);
               disc(vc, m, n, c.add(0.07, 0.01, 0.006), 0.03, 0.028, face, al * 0.9F, 8);
            }
            for (int k = 0; k < 7; k++) {   // twinkling stars
               double tw = 0.5 + 0.5 * Math.sin(t * 0.12 + k * 2.3);
               Vec3 c = polar(k * 0.9 + 0.3, 0.7 + 0.12 * h(k), 0.58 + 0.12 * h(k + 3), Z + 0.03);
               FxBooks.lstar(m, c, (float) (0.025 + 0.02 * tw), k % 2 == 0 ? pal : 36, al * (float) tw);
            }
         }
         default -> FxBooks.underglow(m, HW, HH, pal, 0.25F * al);
      }
   }
}
