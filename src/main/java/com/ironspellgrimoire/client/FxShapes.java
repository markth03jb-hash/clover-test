package com.ironspellgrimoire.client;

import com.ironspellgrimoire.network.FxPacket;
import com.ironspellgrimoire.registry.AddonItemRegistry;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent.Stage;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * The solid and glowing shapes the effect packets describe: walls and rings, crescents, slashes, steel blocks, spikes, bones,
 * mercury spheres, fireballs, lightning, tornadoes, gusts, space cracks, weapon rings and bone whips.
 * (Split out of FxRenderer.)
 */
final class FxShapes extends FxBase {
   private FxShapes() {}


   /**
    * Real lightning: a jagged channel made by midpoint displacement, with forks splitting off it, drawn as light
    * (a white-hot core in a coloured glow). It re-forks every two ticks and flickers - bright, dim, bright - the way a
    * real strike pulses, the first instant brightest and widest, then fades.
    */
   static void boltFx(Matrix4f m, Vec3 cam, FxPacket p, double age, double life) {
      if (gv == null) return;
      double len = p.d.length();
      if (len < 0.05) return;
      int frame = (int)(age / 2.0);
      float flick = switch ((int)(age * 3.0) % 4) { case 1 -> 0.45F; case 3 -> 0.75F; default -> 1.0F; };
      float fade = (float)Mth.clamp((life - age) / Math.min(4.0, life * 0.6), 0.0, 1.0);
      float first = age < 1.0 ? 1.6F : 1.0F;
      float al = flick * fade;
      if (al <= 0.02F) return;
      java.util.Random rnd = new java.util.Random(p.id * 7919L + frame * 104729L);
      boltBranch(m, cam, p.c, p.c.add(p.d), p.r * first, p.palette, al, rnd, p.sweep, 0);
      // the light where it lands and where it leaves
      float glow = (float)Math.min(3.5, p.r * 12.0 + 0.3);
      Vec3 end = p.c.add(p.d);
      halo(m, cam, end, glow, RIM[p.palette], al * 0.45F * first);
      if (p.r > 0.15F && age < 2.0) halo(m, cam, p.c.add(p.d.scale(0.5)), Math.min(8.0, len * 0.35), new float[]{1.0F, 1.0F, 0.9F}, (float)(1.0 - age / 2.0) * 0.18F);   // the whole sky lights up for an instant
   }

   static void boltBranch(Matrix4f m, Vec3 cam, Vec3 a, Vec3 b, float w, int pal, float al, java.util.Random rnd, int branches, int depth) {
      Vec3 ab = b.subtract(a);
      double len = ab.length();
      int iters = len > 12 ? 6 : (len > 4 ? 5 : (len > 1.5 ? 4 : 3));
      List<Vec3> pts = new ArrayList<>();
      pts.add(a); pts.add(b);
      double amp = len * 0.16;
      Vec3 dir = ab.scale(1.0 / len);
      Vec3 s1 = dir.cross(new Vec3(0, 1, 0));
      if (s1.lengthSqr() < 1.0E-4) s1 = dir.cross(new Vec3(1, 0, 0));
      s1 = s1.normalize();
      Vec3 s2 = dir.cross(s1).normalize();
      for (int it = 0; it < iters; it++) {
         List<Vec3> nx = new ArrayList<>();
         for (int i = 0; i < pts.size() - 1; i++) {
            Vec3 p0 = pts.get(i), p1 = pts.get(i + 1);
            Vec3 midp = p0.add(p1).scale(0.5).add(s1.scale((rnd.nextDouble() - 0.5) * 2 * amp)).add(s2.scale((rnd.nextDouble() - 0.5) * 2 * amp));
            nx.add(p0); nx.add(midp);
         }
         nx.add(pts.get(pts.size() - 1));
         pts = nx;
         amp *= 0.52;
      }
      for (int i = 0; i < pts.size() - 1; i++) {
         Vec3 p0 = pts.get(i), p1 = pts.get(i + 1);
         gline(m, cam, p0, p1, w * 3.2F, pal, al * 0.18F);   // the wide glow of the air around it
         gline(m, cam, p0, p1, w, pal, al);                  // the channel itself, white-hot in the middle
      }
      if (depth < 2) {
         for (int k = 0; k < branches; k++) {
            int i = (int)(pts.size() * (0.15 + rnd.nextDouble() * 0.65));
            Vec3 from = pts.get(Math.min(i, pts.size() - 1));
            Vec3 rest = b.subtract(from);
            double bl = Math.max(0.3, rest.length() * (0.25 + rnd.nextDouble() * 0.35));
            Vec3 bd = rest.normalize().add(s1.scale((rnd.nextDouble() - 0.5) * 1.6)).add(s2.scale((rnd.nextDouble() - 0.5) * 1.6)).normalize();
            boltBranch(m, cam, from, from.add(bd.scale(bl)), w * 0.5F, pal, al * 0.7F, rnd, Math.max(0, branches / 2), depth + 1);
         }
      }
   }

   /**
    * A ball of fire: a white-hot core blooming out to orange and a deep red rim, its surface churning, with a soft
    * glowing halo. Explosions (h = 1) swell from r to r2 and darken as they burn out.
    */
   /**
    * A tornado: a funnel of wind spinning fast - spiral bands of bright, airy wind winding up it, wider at the top,
    * a faint body of air between them, and a ring of dust whirling at its foot. c = base, r = radius at the top,
    * h = height, a0 = how long it has been spinning (keeps the spin continuous between packets).
    */
   static void tornadoFx(Matrix4f m, Vec3 cam, FxPacket p0, double age, double life) {
      if (gv == null) return;
      FxPacket p = new FxPacket();
      p.c = p0.c.add(p0.v.scale(mv(p0, age))); p.r = p0.r; p.h = p0.h; p.a0 = p0.a0;   // glide between server updates
      double R = p.r, H = p.h;
      double spin = (p.a0 + age) * 0.55;
      int tp = p0.palette == 0 ? WIND : p0.palette;   // 0 = old packets: wind
      float[] rim = RIM[tp], white = tp == WIND ? new float[]{0.95F, 1.0F, 0.97F} : hot(tp);
      int BANDS = 7, STEPS = 28;
      for (int b = 0; b < BANDS; b++) {
         Vec3 prev = null;
         for (int k = 0; k <= STEPS; k++) {
            double y = H * k / STEPS;
            double rr = R * (0.18 + 0.82 * Math.pow(y / H, 0.85)) * (1.0 + 0.06 * Math.sin(y * 1.7 + spin * 2 + b));
            double a = y * 0.9 + spin * 2.2 + Math.PI * 2 * b / BANDS;
            Vec3 q = p.c.add(Math.cos(a) * rr, y, Math.sin(a) * rr);
            if (prev != null) {
               float al = (float)(0.55 * Math.sin(Math.PI * k / STEPS) + 0.15);
               gline(m, cam, prev, q, (float)(0.08 + 0.1 * y / H), tp, al * (b % 2 == 0 ? 1.0F : 0.6F));
            }
            prev = q;
         }
      }
      // the faint body of moving air: wide, see-through rings
      for (int k = 0; k < 8; k++) {
         double y = H * (k + 0.5) / 8;
         double rr = R * (0.18 + 0.82 * Math.pow(y / H, 0.85));
         Vec3 prev = null;
         for (int j = 0; j <= 20; j++) {
            double a = Math.PI * 2 * j / 20 + spin * 3 + k;
            Vec3 q = p.c.add(Math.cos(a) * rr, y + 0.15 * Math.sin(a * 2 + spin), Math.sin(a) * rr);
            if (prev != null) gline(m, cam, prev, q, (float)(0.25 + 0.3 * y / H), tp, tp == WIND ? 0.07F : 0.16F);
            prev = q;
         }
      }
      halo(m, cam, p.c.add(0, H * 0.5, 0), R * 1.3, white, 0.08F);
   }

   /**
    * Sylph's Breath: a cone of wind pouring out of the palm. Spiral streamlines race forward along the cone (each one
    * twisting round the axis as it goes), rings of air roll out at the moving front, everything fades as it passes.
    * c = origin, d = direction, r = range, h = half-angle (radians).
    */
   static void gustFx(Matrix4f m, Vec3 cam, FxPacket p, double age, double life) {
      if (gv == null) return;
      Vec3 d = p.d.normalize();
      Vec3 s1 = d.cross(new Vec3(0, 1, 0));
      if (s1.lengthSqr() < 1.0E-4) s1 = new Vec3(1, 0, 0);
      s1 = s1.normalize();
      Vec3 s2 = d.cross(s1).normalize();
      double front = p.r * (1.0 - Math.pow(1.0 - Mth.clamp(age / (life * 0.55), 0.0, 1.0), 2.0));   // the gust's leading edge
      float fade = (float)Mth.clamp((life - age) / (life * 0.45), 0.0, 1.0);
      double tanH = Math.tan(p.h);
      int LINES = 16, STEPS = 14;
      java.util.Random rnd = new java.util.Random(p.id * 31L);
      for (int i = 0; i < LINES; i++) {
         double a0 = Math.PI * 2 * i / LINES + rnd.nextDouble() * 0.4, rf = 0.35 + rnd.nextDouble() * 0.65, tw = 0.18 + rnd.nextDouble() * 0.12;
         Vec3 prev = null;
         for (int k = 0; k <= STEPS; k++) {
            double sd = front * k / STEPS;
            double rad = sd * tanH * rf + 0.15;
            double a = a0 + sd * tw + age * 0.25;
            Vec3 q = p.c.add(d.scale(sd)).add(s1.scale(Math.cos(a) * rad)).add(s2.scale(Math.sin(a) * rad));
            if (prev != null) {
               float tail = (float)k / STEPS;   // brightest near the front
               gline(m, cam, prev, q, (float)(0.035 + 0.05 * tail), WIND, fade * (0.15F + 0.6F * tail * tail));
            }
            prev = q;
         }
      }
      // rings of air rolling out at the front
      for (int j = 0; j < 3; j++) {
         double sd = front - j * 1.6;
         if (sd < 0.5) continue;
         double rad = sd * tanH + 0.2;
         Vec3 cc = p.c.add(d.scale(sd));
         Vec3 prev = null;
         for (int k = 0; k <= 24; k++) {
            double a = Math.PI * 2 * k / 24 + age * 0.3;
            Vec3 q = cc.add(s1.scale(Math.cos(a) * rad)).add(s2.scale(Math.sin(a) * rad));
            if (prev != null) gline(m, cam, prev, q, (float)(0.06 + 0.04 * j), WIND, fade * (0.5F - j * 0.14F));
            prev = q;
         }
      }
   }

   /**
    * BONE: real bone, not a glowing shape. A shaded tube of ivory along a (possibly curved) centre line with a radius
    * profile for its kind, lit by the key light, darker in the creases and toward the ends.
    * kinds (p.h): 0 long bone (knobbed condyles at both ends, slim waist), 1 spear (knobbed butt, sharpened point),
    * 2 spike / horn (thick base, curving to a point), 3 vertebra (short spool with side wings), 4 rib (thin, curved).
    * c = centre, d = axis, u = the way it bends, r = half length, r2 = radius. speed = mode like spikes (0 pop,
    * 1 grows out of the ground from its base, 2 extends along its axis, 3 hold). a0 = spin (deg/tick), v / a1 = travel.
    */
   static void boneFx(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxRenderer.Fx f, double age, double life) {
      FxPacket p = f.p;
      double in = p.sweep > 0 ? Mth.clamp(age / (double)p.sweep, 0.0, 1.0) : 1.0;
      double out = smooth((life - age) / 3.0);
      double k = p.speed == FxPacket.HOLD || p.life <= 1 ? 1.0 : (p.speed == FxPacket.POP ? backOut(in) * out : in * out);
      if (k < 0.01) return;
      int kind = (int)p.h;
      Vec3 ax = p.d.normalize();
      Vec3 bend = p.u.lengthSqr() < 1.0E-4 ? ax.cross(new Vec3(0, 1, 0)) : p.u.subtract(ax.scale(p.u.dot(ax)));
      if (bend.lengthSqr() < 1.0E-4) bend = ax.cross(new Vec3(1, 0, 0));
      bend = bend.normalize();
      double spin = Math.toRadians(p.a0 * age);
      if (spin != 0.0) {   // spin the bend direction (and everything with it) around the axis
         Vec3 b2 = ax.cross(bend);
         bend = bend.scale(Math.cos(spin)).add(b2.scale(Math.sin(spin)));
      }
      double L = p.r * 2.0 * (p.speed == FxPacket.POP ? k : (p.speed == FxPacket.HOLD || p.life <= 1 ? 1.0 : k));
      double R = p.r2 * (p.speed == FxPacket.POP ? k : 1.0);
      Vec3 c = travelled(f, age);
      Vec3 start = (p.speed == FxPacket.GROUND || p.speed == FxPacket.EXTEND) ? c.subtract(ax.scale(p.r)) : c.subtract(ax.scale(L * 0.5));
      int S = kind == 3 ? 6 : 12, SIDES = 8;
      Vec3[] ctr = new Vec3[S + 1];
      double[] rad = new double[S + 1];
      for (int i = 0; i <= S; i++) {
         double t = (double)i / S;
         double curve = kind == 2 ? 0.28 * t * t : (kind == 4 ? 0.35 * Math.sin(Math.PI * t) : 0.0);
         ctr[i] = start.add(ax.scale(L * t)).add(bend.scale(curve * L));
         double w;
         switch (kind) {
            case 1 -> w = t < 0.12 ? 1.25 - t : (t > 0.82 ? Math.max(0.02, (1.0 - t) / 0.18 * 0.85) : 0.62);           // spear: knob butt, sharpened tip
            case 2 -> w = Math.max(0.02, Math.pow(1.0 - t, 1.3));                                                      // horn: thick base to a point
            case 3 -> w = 0.8 + 0.25 * Math.cos(Math.PI * 2 * t);                                                     // vertebra: spool
            case 4 -> w = 0.55 + 0.1 * Math.sin(Math.PI * t);                                                         // rib
            default -> w = 0.55 + 0.75 * Math.pow(Math.abs(2 * t - 1), 6) + 0.08 * Math.cos(Math.PI * 4 * t);         // long bone: waist and flared ends
         }
         rad[i] = R * w;
      }
      tube(vc, m, n, cam, ctr, rad, bend, SIDES, kind != 2 && kind != 1);
      if (kind == 0) {   // the knobbed condyles: two lumps side by side at each end
         Vec3 side = ax.cross(bend).normalize();
         for (int e = 0; e <= 1; e++) {
            Vec3 endC = e == 0 ? ctr[0] : ctr[S];
            for (int s2 = -1; s2 <= 1; s2 += 2) knob(vc, m, n, cam, endC.add(side.scale(R * 0.55 * s2)).add(ax.scale((e == 0 ? -1 : 1) * R * 0.25)), R * 0.72);
         }
      } else if (kind == 1) {
         knob(vc, m, n, cam, ctr[0].subtract(ax.scale(R * 0.3)), R * 0.85);
      } else if (kind == 3) {   // the vertebra's wings and spine
         Vec3 side = ax.cross(bend).normalize();
         Vec3 mid = ctr[S / 2];
         for (int s2 = -1; s2 <= 1; s2 += 2) {
            Vec3[] w = {mid, mid.add(side.scale(R * 2.0 * s2))};
            tube(vc, m, n, cam, w, new double[]{R * 0.35, R * 0.15}, ax, 6, false);
         }
         Vec3[] sp = {mid, mid.add(bend.scale(-R * 1.8)).add(ax.scale(-R * 0.4))};
         tube(vc, m, n, cam, sp, new double[]{R * 0.32, R * 0.08}, ax, 6, false);
      }
   }

   static void fireballFx(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age, double life) {
      boolean boom = p.h > 0.5F;
      double R;
      float al, heat;
      if (boom) {
         double g = 1.0 - Math.pow(1.0 - Mth.clamp(age / (life * 0.45), 0.0, 1.0), 3.0);
         R = p.r + (p.r2 - p.r) * g;
         double t = Mth.clamp(age / life, 0.0, 1.0);
         al = (float)(1.0 - Math.pow(t, 2.0)) * 0.85F;
         heat = (float)(1.0 - t);
      } else {
         R = p.r;
         al = 1.0F;   // a real ball of fire: solid, not see-through
         heat = 1.0F;
      }
      if (R < 0.02 || al <= 0.01F) return;
      Vec3 c = p.c.add(p.v.scale(mv(p, age)));
      boolean inside = cam.distanceTo(c) < R * 1.02;
      if (inside) al *= 0.25F;
      int LAT = R > 1.2 ? 14 : 8, LON = R > 1.2 ? 24 : 12;
      double t = fxTime;
      float[] rim = RIM[p.palette == BLUE_FIRE ? BLUE_FIRE : (p.palette == CRIMSON ? CRIMSON : FIRE)];
      // the corona: the light the fire throws on the air around it (and a white flash when it explodes)
      halo(m, cam, c, R * (boom ? 1.7 : 2.4), rim, al * (boom ? 0.4F : 0.5F) * heat);
      if (boom && age < 3.0) halo(m, cam, c, R * 2.6, new float[]{1.0F, 0.95F, 0.8F}, (float)(1.0 - age / 3.0) * 0.55F);
      if (p.palette == CRIMSON && gv != null) {   // Mereoleona's fire: a mane of sharp flame tongues flaring around it
         Vec3 view = c.subtract(cam).normalize();
         Vec3 u = view.cross(new Vec3(0, 1, 0));
         if (u.lengthSqr() < 1.0E-4) u = new Vec3(1, 0, 0);
         u = u.normalize();
         Vec3 v = view.cross(u).normalize();
         int N = 12;
         for (int k = 0; k < N; k++) {
            double a0 = Math.PI * 2 * k / N + fxTime * 0.15;
            double len = R * (0.55 + 0.35 * Math.sin(fxTime * 1.7 + k * 2.3)) * (boom ? heat : 1.0);
            Vec3 dirk = u.scale(Math.cos(a0)).add(v.scale(Math.sin(a0)));
            Vec3 b1 = c.add(u.scale(Math.cos(a0 - 0.2) * R * 0.9)).add(v.scale(Math.sin(a0 - 0.2) * R * 0.9));
            Vec3 b2 = c.add(u.scale(Math.cos(a0 + 0.2) * R * 0.9)).add(v.scale(Math.sin(a0 + 0.2) * R * 0.9));
            Vec3 tip = c.add(dirk.scale(R * 0.9 + len)).add(0, len * 0.35, 0);
            gquad(m, cam, b1, b2, tip, tip, rim, al * 0.85F, al * 0.85F, 0.0F, 0.0F);
            gquad(m, cam, b1.add(dirk.scale(-0.02)), b2.add(dirk.scale(-0.02)), c.add(dirk.scale(R * 0.9 + len * 0.55)), c.add(dirk.scale(R * 0.9 + len * 0.55)),
                  new float[]{1.0F, 0.75F, 0.45F}, al * 0.6F, al * 0.6F, 0.0F, 0.0F);
         }
      }
      for (int i = 0; i < LAT; i++) {
         for (int j = 0; j < LON; j++) {
            int[][] ij = {{i, j}, {i, j + 1}, {i + 1, j + 1}, {i + 1, j}};
            Vec3[] w = new Vec3[4];
            float[] fr = new float[4];
            boolean front = false;
            for (int k = 0; k < 4; k++) {
               double th = Math.PI * ij[k][0] / LAT, ph = Math.PI * 2 * ij[k][1] / LON;
               Vec3 nv = new Vec3(Math.sin(th) * Math.cos(ph), Math.cos(th), Math.sin(th) * Math.sin(ph));
               double churn = 1.0 + 0.11 * Math.sin(4.0 * th + t * 0.9 + p.id) * Math.sin(3.0 * ph - t * 0.7) + 0.05 * Math.sin(7.0 * ph + t * 1.3);
               w[k] = c.add(nv.scale(R * churn));
               Vec3 view = cam.subtract(w[k]).normalize();
               fr[k] = (float)(1.0 - Math.abs(nv.dot(view)));
               if (nv.dot(view) > 0.0) front = true;
            }
            if (!inside && !front) continue;
            for (int k = 0; k < 4; k++) {
               float e = fr[k];   // 0 = facing you (hot core), 1 = the edge (cooler rim)
               float[] col = p.palette == CRIMSON ? new float[]{
                  Mth.clamp(1.0F * heat + 0.3F * (1 - heat), 0, 1),
                  Mth.clamp((0.75F - 0.7F * e) * heat + 0.03F * (1 - heat), 0, 1),
                  Mth.clamp((0.45F - 0.42F * e) * heat + 0.03F * (1 - heat), 0, 1)} : p.palette == BLUE_FIRE ? new float[]{
                  Mth.clamp((0.85F - 0.75F * e) * heat + 0.02F * (1 - heat), 0, 1),
                  Mth.clamp((0.95F - 0.5F * e) * heat + 0.06F * (1 - heat), 0, 1),
                  Mth.clamp(1.0F * heat + 0.3F * (1 - heat), 0, 1)} : new float[]{
                  Mth.clamp(1.0F * heat + 0.35F * (1 - heat), 0, 1),
                  Mth.clamp((0.92F - 0.62F * e) * heat + 0.08F * (1 - heat), 0, 1),
                  Mth.clamp((0.55F - 0.52F * e) * heat + 0.04F * (1 - heat), 0, 1)};
               vtx(vc, m, n, cam, w[k], col, al * (boom ? 1.0F - 0.35F * e : 1.0F - 0.08F * e));
            }
            if (gv != null) gquad(m, cam, w[0], w[1], w[2], w[3], rim, fr[0] * al * 0.5F * heat, fr[1] * al * 0.5F * heat, fr[2] * al * 0.5F * heat, fr[3] * al * 0.5F * heat);
         }
      }
   }

   /**
    * A liquid mercury sphere: mirror-chrome (the sky reflected above, the ground's dark below, a hard highlight),
    * its surface rippling, growing in over {@code sweep} ticks and moving with {@code v}.
    */
   static void mercurySphere(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age, double life) {
      double in = p.sweep > 0 ? Mth.clamp(age / p.sweep, 0.0, 1.0) : 1.0;
      in = 1.0 - Math.pow(1.0 - in, 3.0);
      double out = life <= 2 ? 1.0 : Mth.clamp((life - age) / 3.0, 0.0, 1.0);
      double R = p.r * in * (0.3 + 0.7 * out);
      if (R < 0.02) return;
      Vec3 c = p.c.add(p.v.scale(mv(p, age)));
      boolean inside = cam.distanceTo(c) < R * 1.02;
      int LAT = R > 1.5 ? 16 : 9, LON = R > 1.5 ? 28 : 14;
      float al = (float)(inside ? 0.25 : 0.98) * (float)Math.min(1.0, out * 1.5);
      double t = fxTime;
      for (int i = 0; i < LAT; i++) {
         for (int j = 0; j < LON; j++) {
            Vec3[] q = new Vec3[4];
            int[][] ij = {{i, j}, {i, j + 1}, {i + 1, j + 1}, {i + 1, j}};
            for (int k = 0; k < 4; k++) {
               double th = Math.PI * ij[k][0] / LAT, ph = Math.PI * 2 * ij[k][1] / LON;
               q[k] = new Vec3(Math.sin(th) * Math.cos(ph), Math.cos(th), Math.sin(th) * Math.sin(ph));
            }
            Vec3 qc = q[0].add(q[2]).scale(0.5);
            if (!inside && qc.dot(cam.subtract(c.add(qc.scale(R)))) < 0.0) continue;
            for (int k = 0; k < 4; k++) {
               double th = Math.PI * ij[k][0] / LAT, ph = Math.PI * 2 * ij[k][1] / LON;
               double wob = 1.0 + 0.05 * Math.sin(3.0 * th + t * 0.55 + p.id) * Math.sin(2.0 * ph + t * 0.4);
               Vec3 nv = q[k];
               Vec3 w = c.add(nv.scale(R * wob));
               Vec3 view = w.subtract(cam).normalize();
               Vec3 refl = view.subtract(nv.scale(2.0 * view.dot(nv)));
               float sky = (float)Math.pow(Mth.clamp(refl.y * 0.5 + 0.5, 0.0, 1.0), 1.4);
               float spec = (float)Math.pow(Math.max(0.0, refl.dot(LIGHT)), 28.0);
               float fres = (float)Math.pow(1.0 - Math.abs(nv.dot(view)), 3.0);
               float v = 0.12F + 0.78F * sky + 0.6F * spec + 0.15F * fres;
               float[] col = {Math.min(1F, v * 0.95F), Math.min(1F, v * 0.97F), Math.min(1F, v * 1.02F)};
               vtx(vc, m, n, cam, w, col, al);
            }
         }
      }
   }

   /**
    * Space itself shattering: jagged fracture lines with white-hot edges and pure black void showing through them,
    * spreading out from c in the plane (d, u). h = 0: a shatter (branches all round, a hole in the middle);
    * h = 1: a rift (a long tear along d with short side fractures). r = size, sweep = seed. They crack open in
    * a few ticks, hang there unmoving, then fade.
    */
   static void spaceCracks(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age, double life) {
      java.util.Random rnd = new java.util.Random(p.sweep * 341873128712L + 132897987541L);
      double grow = smooth(age / 3.0);
      float fade = (float)smooth((life - age) / 3.0);
      if (fade <= 0.01F) return;
      Vec3 d = p.d.normalize(), u = p.u.normalize();
      double R = p.r;
      boolean rift = p.h > 0.5F;
      int branches = rift ? 2 : 6 + rnd.nextInt(3);
      java.util.List<Vec3> inner = new java.util.ArrayList<>();
      for (int b = 0; b < branches; b++) {
         double ang = rift ? (b == 0 ? 0.0 : Math.PI) + rnd.nextGaussian() * 0.05 : b * Math.PI * 2.0 / branches + rnd.nextGaussian() * 0.25;
         double len = R * (rift ? 1.0 : 0.6 + rnd.nextDouble() * 0.5) * grow;
         crackLine(vc, m, n, cam, p.c, d, u, ang, len, R * (rift ? 0.05 : 0.07), rnd, rift ? 9 : 5, p.palette, fade, rift ? 3 : 2, inner);
      }
      if (!rift && inner.size() >= 3) {   // the hole in the middle: black void with a broken white rim
         for (int k = 0; k < inner.size(); k++) {
            Vec3 a = inner.get(k), b2 = inner.get((k + 1) % inner.size());
            vtx(vc, m, n, cam, p.c, VOID, 0.97F * fade); vtx(vc, m, n, cam, a, VOID, 0.97F * fade);
            vtx(vc, m, n, cam, b2, VOID, 0.97F * fade); vtx(vc, m, n, cam, p.c, VOID, 0.97F * fade);
            vtx(vc, m, n, cam, p.c, VOID, 0.97F * fade); vtx(vc, m, n, cam, b2, VOID, 0.97F * fade);
            vtx(vc, m, n, cam, a, VOID, 0.97F * fade); vtx(vc, m, n, cam, p.c, VOID, 0.97F * fade);
            gline(m, cam, a, b2, (float)(R * 0.03), p.palette, 0.9F * fade);
         }
      }
   }

   static void crackLine(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 c, Vec3 d, Vec3 u, double ang, double len,
                                 double w, java.util.Random rnd, int segs, int pal, float fade, int depth, java.util.List<Vec3> inner) {
      if (len < 0.05 || depth <= 0) return;
      Vec3 pos = c;
      double a = ang;
      for (int s = 0; s < segs; s++) {
         a += rnd.nextGaussian() * 0.45;   // jagged: every segment kinks
         double seg = len / segs * (0.7 + rnd.nextDouble() * 0.6);
         Vec3 dirv = d.scale(Math.cos(a)).add(u.scale(Math.sin(a)));
         Vec3 next = pos.add(dirv.scale(seg));
         double t0 = (double)s / segs, t1 = (double)(s + 1) / segs;
         double w0 = w * (1.0 - t0 * 0.85), w1 = w * (1.0 - t1 * 0.85);
         Vec3 side = d.scale(-Math.sin(a)).add(u.scale(Math.cos(a)));
         // the void inside the fracture (both windings, so it reads from either side)
         Vec3 a0 = pos.add(side.scale(w0)), a1 = next.add(side.scale(w1)), b0 = pos.subtract(side.scale(w0)), b1 = next.subtract(side.scale(w1));
         quad(vc, m, n, cam, a0, a1, b1, b0, VOID, 0.95F * fade, 0.95F * fade, 0.95F * fade, 0.95F * fade);
         quad(vc, m, n, cam, b0, b1, a1, a0, VOID, 0.95F * fade, 0.95F * fade, 0.95F * fade, 0.95F * fade);
         // white-hot broken edges
         gline(m, cam, a0, a1, (float)Math.max(0.02, w0 * 0.55), pal, 0.95F * fade);
         gline(m, cam, b0, b1, (float)Math.max(0.02, w0 * 0.55), pal, 0.95F * fade);
         if (s == 0 && inner != null) inner.add(next);
         if (s > 0 && rnd.nextDouble() < 0.45)   // side fractures
            crackLine(vc, m, n, cam, next, d, u, a + (rnd.nextBoolean() ? 1 : -1) * (0.6 + rnd.nextDouble() * 0.6), len * (1.0 - t1) * 0.55, w1 * 0.8, rnd, 3, pal, fade, depth - 1, null);
         pos = next;
      }
   }

   /** BOX: a shaded block that pops in, rises out of the ground, or extends along its axis; can spin and travel. */
   static void boxFx(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxRenderer.Fx f, double age, double life) {
      FxPacket p = f.p;
      Vec3 ax = p.d;
      Vec3 s0 = ax.cross(p.u);
      if (s0.lengthSqr() < 1.0E-6) s0 = ax.cross(new Vec3(1.0, 0.0, 0.0));
      s0 = s0.normalize();
      Vec3 u0 = s0.cross(ax).normalize();
      double th = Math.toRadians((double)p.a0 * age);
      Vec3 side = s0.scale(Math.cos(th)).add(u0.scale(Math.sin(th)));
      Vec3 upv = u0.scale(Math.cos(th)).subtract(s0.scale(Math.sin(th)));
      double in = p.sweep > 0 ? Mth.clamp(age / (double)p.sweep, 0.0, 1.0) : 1.0;
      double out = smooth((life - age) / 3.0);
      double hl = p.r, hw = p.r2, hh = p.h;
      Vec3 c = travelled(f, age);
      if (p.speed == FxPacket.GROUND) {
         c = c.subtract(u0.scale(2.0 * hh * (1.0 - backOut(in)) + 2.0 * hh * (1.0 - out)));
      } else if (p.speed == FxPacket.EXTEND) {
         double L = hl * in;
         c = c.subtract(ax.scale(hl - L));
         hl = Math.max(0.001, L);
         hw *= out;
         hh *= out;
      } else if (p.speed != FxPacket.HOLD) {
         double k = backOut(in) * out;
         hl *= k; hw *= k; hh *= k;
      }
      if (hl < 0.002 || hw < 0.002 || hh < 0.002) return;
      float al = p.speed == FxPacket.HOLD ? 0.96F : 0.96F * (float)Mth.clamp(age * 2.0 + 0.3, 0.0, 1.0);
      drawBox(vc, m, n, cam, c, ax, side, upv, hl, hw, hh, p.palette, al, 1.0F);
   }

   /** One weapon ring: each piece's position and facing worked out for this exact frame, so the circling is smooth. */
   static void orbitFx(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Minecraft mc, double[] o, double now, float pt) {
      net.minecraft.world.entity.Entity e = mc.level.getEntity((int) o[0]);
      if (e == null) return;
      Vec3 ep = e.getPosition(pt);
      int kind = (int) o[1], count = (int) o[2];
      double R = o[3], H = o[4], w = o[5], ph = o[6], bob = o[7], growT = Math.max(1.0, o[8]), stagger = o[9];
      double age = now - o[10];
      for (int i = 0; i < count; i++) {
         if (age > o[11] + stagger * i) continue;   // this one has been launched
         double a = Math.PI * 2 * i / count + ph + w * age;
         float s = (float) Math.min(1.0, (age + 1.0) / growT);
         Vec3 c = ep.add(Math.cos(a) * R, H + bob * Math.sin(age * 0.35 + i), Math.sin(a) * R);
         if (kind == 0) {   // wind halberd: a long shaft, a silver spear point and an axe blade
            Vec3 dir = new Vec3(Math.cos(a) * 0.3, 1.0, Math.sin(a) * 0.3).normalize();
            Vec3 side = dir.cross(new Vec3(0, 1, 0));
            side = side.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : side.normalize();
            drawSpike(vc, m, n, cam, c.subtract(dir.scale(1.4 * s)), dir, 2.8 * s, 0.07, 0.0, 5, 0.96F);
            drawSpike(vc, m, n, cam, c.add(dir.scale(0.9 * s)), dir, 0.7 * s, 0.12, 0.0, 6, 0.96F);
            drawSpike(vc, m, n, cam, c.add(dir.scale(0.7 * s)), side.add(dir.scale(0.3)).normalize(), 0.55 * s, 0.16, 0.0, 5, 0.96F);
         } else {           // mercury sword, point down, flat side along the circle: blade, guard and grip
            Vec3 dir = new Vec3(0, -1, 0), flat = new Vec3(-Math.sin(a), 0, Math.cos(a));
            Vec3 up = dir.cross(flat).normalize();
            orbitBox(vc, m, n, cam, c.add(dir.scale(0.35 * s)), dir, up, 0.75 * s, 0.02, 0.09);
            orbitBox(vc, m, n, cam, c.subtract(dir.scale(0.45 * s)), up, dir, 0.24 * s, 0.04, 0.04);
            orbitBox(vc, m, n, cam, c.subtract(dir.scale(0.65 * s)), dir, up, 0.18 * s, 0.035, 0.035);
         }
      }
   }

   static void orbitBox(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 c, Vec3 ax, Vec3 u, double hl, double hw, double hh) {
      Vec3 s0 = ax.cross(u);
      if (s0.lengthSqr() < 1.0E-6) s0 = ax.cross(new Vec3(1.0, 0.0, 0.0));
      s0 = s0.normalize();
      Vec3 u0 = s0.cross(ax).normalize();
      drawBox(vc, m, n, cam, c, ax, s0, u0, hl, hw, hh, MERCURY, 0.96F, 1.0F);
   }

   /**
    * Zenon's bone whips, worked out every frame so they move smoothly: each whip is a chain of vertebrae growing
    * out of your back - knobbly, thicker at the root - that sways behind you and lashes out at the nearest enemies
    * in turn, cracking at the end of each lash, with a hooked point at the tip. Beelzebub: six of them, longer,
    * faster, with space glowing violet-black round the points.
    */
   static void whipsFx(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Minecraft mc, double[] o, double now, float pt) {
      net.minecraft.world.entity.Entity e = mc.level.getEntity((int) o[0]);
      if (e == null) return;
      double age = now - o[1], life = o[2];
      int count = (int) o[3];
      double reach = o[4], period = Math.max(4.0, o[5]);
      boolean union = o[6] > 0.5;
      double grow = Mth.clamp(age / 5.0, 0.0, 1.0) * Mth.clamp((life + 4.0 - age) / 4.0, 0.0, 1.0);
      if (grow <= 0.01) return;
      Vec3 ep = e.getPosition(pt);
      float yaw = Mth.rotLerp(pt, e instanceof net.minecraft.world.entity.LivingEntity le0 ? le0.yBodyRotO : e.yRotO,
                               e instanceof net.minecraft.world.entity.LivingEntity le1 ? le1.yBodyRot : e.getYRot());
      double yr = Math.toRadians(yaw);
      Vec3 look = new Vec3(-Math.sin(yr), 0, Math.cos(yr)), rt = new Vec3(-Math.cos(yr), 0, -Math.sin(yr));
      Vec3 back = ep.add(0, 1.3, 0).subtract(look.scale(0.3));
      List<net.minecraft.world.entity.LivingEntity> foes = new ArrayList<>(mc.level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
            e.getBoundingBox().inflate(reach), x -> x != e && x.isAlive() && !(x instanceof net.minecraft.world.entity.decoration.ArmorStand) && x.distanceTo(e) <= reach));
      foes.sort(java.util.Comparator.comparingDouble(x -> x.distanceToSqr(e)));
      for (int i = 0; i < count; i++) {
         double side = i - (count - 1) / 2.0;
         Vec3 root = back.add(rt.scale(side * 0.25));
         net.minecraft.world.entity.LivingEntity t0 = foes.isEmpty() ? null : foes.get(i % foes.size());
         double phase = ((age + i * period * 0.4) % period) / period;
         double lash = Math.sin(Math.PI * phase);
         lash = lash * lash * (3 - 2 * lash);   // snap out, hang, snap back
         Vec3 rest = root.add(rt.scale(side * 1.2)).add(0, 1.2 + 0.25 * Math.sin(age * 0.2 + i), 0).subtract(look.scale(1.2));
         Vec3 tgt = t0 != null ? t0.getPosition(pt).add(0, t0.getBbHeight() * 0.5, 0) : rest.add(look.scale(4 + reach * 0.2));
         Vec3 tip = rest.add(tgt.subtract(rest).scale(lash));
         tip = root.add(tip.subtract(root).scale(grow));
         Vec3 d = tip.subtract(root);
         double len = d.length();
         if (len < 0.05) continue;
         int segs = 18;
         Vec3 side0 = d.cross(new Vec3(0, 1, 0));
         side0 = side0.lengthSqr() < 1.0E-6 ? rt : side0.normalize();
         double sway = Math.sin(age * 0.45 + side * 1.7);
         Vec3[] ctr = new Vec3[segs + 1];
         double[] rad = new double[segs + 1];
         double r0 = (union ? 0.11 : 0.09);
         for (int k = 0; k <= segs; k++) {
            double f = (double) k / segs;
            // the chain sags and ripples: a travelling wave that lags behind the lash
            double wave = Math.sin(Math.PI * f) * (0.12 * len * (1.0 - lash * 0.7)) ;
            double ripple = Math.sin(f * 9.0 - age * 0.9 + i) * 0.08 * len * f * (1.0 - lash * 0.5);
            ctr[k] = root.add(d.scale(f)).add(0, -wave, 0).add(side0.scale(sway * wave * 0.8 + ripple));
            double bump = 1.0 + 0.35 * Math.abs(Math.sin(f * segs * Math.PI * 0.5));   // knobbly vertebrae
            rad[k] = r0 * (1.0 - f * 0.55) * bump;
         }
         tube(vc, m, n, cam, ctr, rad, new Vec3(0, 1, 0), 6, true);
         Vec3 tg = ctr[segs].subtract(ctr[segs - 1]).normalize();
         drawSpike(vc, m, n, cam, ctr[segs], tg.add(side0.scale(0.35)).normalize(), 0.35, 0.06, 0.0, 15, 0.96F);   // the hooked point
         if (union) gline(m, cam, ctr[segs].subtract(tg.scale(0.4)), ctr[segs].add(tg.scale(0.4)), 0.12F, 1, 0.5F);
      }
   }

   /** A shaded box with glowing visible edges (brighter along the top). */
   static void drawBox(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 c, Vec3 ax, Vec3 side, Vec3 upv,
                               double hl, double hw, double hh, int pal, float al, float edgeGlow) {
      Vec3[] k = new Vec3[8];
      for (int i = 0; i < 8; i++) {
         k[i] = c.add(ax.scale((i & 1) != 0 ? hl : -hl)).add(side.scale((i & 2) != 0 ? hw : -hw)).add(upv.scale((i & 4) != 0 ? hh : -hh));
      }
      Vec3 wup = new Vec3(0.0, 1.0, 0.0);
      // visible[bit index][sign]
      boolean[][] vis = new boolean[3][2];
      vis[0][1] = face(vc, m, n, cam, k[1], k[3], k[7], k[5], ax, pal, al, wup, c);
      vis[0][0] = face(vc, m, n, cam, k[0], k[4], k[6], k[2], ax.scale(-1.0), pal, al, wup, c);
      vis[1][1] = face(vc, m, n, cam, k[2], k[6], k[7], k[3], side, pal, al, wup, c);
      vis[1][0] = face(vc, m, n, cam, k[0], k[1], k[5], k[4], side.scale(-1.0), pal, al, wup, c);
      vis[2][1] = face(vc, m, n, cam, k[4], k[5], k[7], k[6], upv, pal, al, wup, c);
      vis[2][0] = face(vc, m, n, cam, k[0], k[2], k[3], k[1], upv.scale(-1.0), pal, al, wup, c);
      if (edgeGlow <= 0.0F) return;
      float lw = (float)Mth.clamp(Math.min(hw, hh) * 0.35, 0.012, 0.06);
      for (int i = 0; i < 8; i++) {
         for (int b = 0; b < 3; b++) {
            int bit = 1 << b;
            if ((i & bit) != 0) continue;
            int j = i | bit;
            int b1 = (b + 1) % 3, b2 = (b + 2) % 3;
            boolean v1 = vis[b1][(i >> b1) & 1], v2 = vis[b2][(i >> b2) & 1];
            if (!v1 && !v2) continue;
            boolean top = b != 2 && (i & 4) != 0;
            boolean sil = v1 != v2;   // silhouette edges glow a little stronger
            gline(m, cam, k[i], k[j], lw, pal, al * edgeGlow * (top ? 0.55F : (sil ? 0.32F : 0.18F)));
         }
      }
   }

   /** SPIKE: a faceted six-sided steel spike with a collar; erupts (overshooting), can spin and travel, retracts at the end. */
   static void spikeFx(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxRenderer.Fx f, double age, double life) {
      FxPacket p = f.p;
      double in = p.sweep > 0 ? Mth.clamp(age / (double)p.sweep, 0.0, 1.0) : 1.0;
      double out = smooth((life - age) / 3.0);
      double k = p.speed == FxPacket.HOLD ? 1.0 : (p.speed == FxPacket.EXTEND ? in : backOut(in)) * out;
      double L = p.r * k;
      double R = p.r2 * (0.45 + 0.55 * Math.min(1.0, k));
      if (L < 0.01) return;
      float al = p.speed == FxPacket.HOLD ? 0.96F : 0.96F * (float)Mth.clamp(age * 2.0 + 0.3, 0.0, 1.0);
      drawSpike(vc, m, n, cam, travelled(f, age), p.d, L, R, Math.toRadians((double)p.a0 * age), p.palette, al);
      // a glint off the tip right as it finishes erupting
      double g = age - (double)p.sweep;
      if (g > -1.0 && g < 6.0) {
         Vec3 tip = travelled(f, age).add(p.d.scale(L));
         float ga = al * (float)(1.0 - Math.abs(g - 1.0) / 5.0);
         double gs = Math.max(0.25, R * 2.4);
         Vec3 view = tip.subtract(cam).normalize();
         Vec3 g1 = view.cross(new Vec3(0.0, 1.0, 0.0));
         if (g1.lengthSqr() < 1.0E-4) g1 = new Vec3(1.0, 0.0, 0.0);
         g1 = g1.normalize();
         Vec3 g2 = view.cross(g1).normalize();
         gline(m, cam, tip.subtract(g1.scale(gs)), tip.add(g1.scale(gs)), 0.04F, p.palette, ga);
         gline(m, cam, tip.subtract(g2.scale(gs)), tip.add(g2.scale(gs)), 0.04F, p.palette, ga);
      }
   }

   static void drawSpike(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 base, Vec3 d, double L, double R, double spin, int pal, float al) {
      Vec3 ref = Math.abs(d.y) > 0.95 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0);
      Vec3 e1 = d.cross(ref).normalize(), e2 = d.cross(e1).normalize();
      int N = 6;
      Vec3[] r0 = new Vec3[N], r1 = new Vec3[N];
      Vec3 collar = base.add(d.scale(L * 0.3));
      for (int j = 0; j < N; j++) {
         double a0 = spin + Math.PI * 2.0 * j / N, a1 = a0 + Math.PI / N;
         r0[j] = base.add(e1.scale(Math.cos(a0) * R)).add(e2.scale(Math.sin(a0) * R));
         r1[j] = collar.add(e1.scale(Math.cos(a1) * R * 0.72)).add(e2.scale(Math.sin(a1) * R * 0.72));
      }
      Vec3 tip = base.add(d.scale(L));
      Vec3 ctr = base.add(d.scale(L * 0.4));
      boolean[] tipVis = new boolean[N];
      for (int j = 0; j < N; j++) {
         int jn = (j + 1) % N;
         tri(vc, m, n, cam, r0[j], r0[jn], r1[j], base, d, pal, al, ctr);
         tri(vc, m, n, cam, r1[j], r0[jn], r1[jn], base, d, pal, al, ctr);
         tipVis[j] = tri(vc, m, n, cam, r1[j], r1[jn], tip, base, d, pal, al, ctr);
      }
      float lw = (float)Mth.clamp(R * 0.18, 0.012, 0.05);
      for (int j = 0; j < N; j++) {
         if (tipVis[j] || tipVis[(j + N - 1) % N]) gline(m, cam, r1[j], tip, lw, pal, al * 0.4F);
      }
   }

   static void wall(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age, float env) {
      double lifeFrac = Mth.clamp(age / (double)Math.max(1, p.life), 0.0, 1.0);
      float prog = p.sweep > 0 ? (float)Mth.clamp(age / (double)p.sweep, 0.0, 1.0) : 1.0F;
      prog = prog * prog * (3.0F - 2.0F * prog);
      float R = p.r + (p.r2 - p.r) * (float)lifeFrac;
      Vec3 c = p.c.add(p.d.scale((double)p.speed * age));
      Vec3 h = p.d.cross(p.u).normalize();
      float a0 = p.a0;
      float a1 = p.a0 + (p.a1 - p.a0) * prog;
      int segs = Math.max(4, (int)(Math.abs(a1 - a0) / 7.0F));
      float[] body = BODY[p.palette];
      float[] rim = RIM[p.palette];
      float[] mul = new float[]{0.92F, 1.0F, 1.09F};
      float[] am = new float[]{0.45F, 1.0F, 0.35F};

      for (int layer = 0; layer < 3; layer++) {
         double rl = (double)(R * mul[layer]);

         for (int i = 0; i < segs; i++) {
            double t0 = (double)i / (double)segs;
            double t1 = (double)(i + 1) / (double)segs;
            double th0 = Math.toRadians((double)a0 + (double)(a1 - a0) * t0);
            double th1 = Math.toRadians((double)a0 + (double)(a1 - a0) * t1);
            Vec3 p0 = c.add(p.d.scale(Math.cos(th0) * rl)).add(p.u.scale(Math.sin(th0) * rl));
            Vec3 p1 = c.add(p.d.scale(Math.cos(th1) * rl)).add(p.u.scale(Math.sin(th1) * rl));
            double hh0 = (double)p.h * (0.3 + 0.7 * Math.pow(Math.sin(Math.PI * (0.04 + 0.92 * t0)), 0.6));
            double hh1 = (double)p.h * (0.3 + 0.7 * Math.pow(Math.sin(Math.PI * (0.04 + 0.92 * t1)), 0.6));
            float lead0 = 0.45F + 0.55F * (float)t0;
            float lead1 = 0.45F + 0.55F * (float)t1;
            float A0 = 0.62F * env * am[layer] * lead0;
            float A1 = 0.62F * env * am[layer] * lead1;
            quad(vc, m, n, cam, p0.add(h.scale(hh0)), p1.add(h.scale(hh1)), p1, p0, body, 0.0F, 0.0F, A1, A0);
            quad(vc, m, n, cam, p0, p1, p1.subtract(h.scale(hh1)), p0.subtract(h.scale(hh0)), body, A0, A1, 0.0F, 0.0F);
            if (layer == 1) {
               double lw = (double)p.h * 0.07;
               quad(
                  vc,
                  m,
                  n,
                  cam,
                  p0.add(h.scale(lw)),
                  p1.add(h.scale(lw)),
                  p1.subtract(h.scale(lw)),
                  p0.subtract(h.scale(lw)),
                  rim,
                  0.95F * env * lead0,
                  0.95F * env * lead1,
                  0.95F * env * lead1,
                  0.95F * env * lead0
               );
               // glow: white-hot cutting edge plus a soft bloom either side
               float[] hc = hot(p.palette);
               double bw = (double)p.h * 0.32;
               gquad(m, cam, p0.add(h.scale(lw)), p1.add(h.scale(lw)), p1.subtract(h.scale(lw)), p0.subtract(h.scale(lw)), hc,
                  0.9F * env * lead0, 0.9F * env * lead1, 0.9F * env * lead1, 0.9F * env * lead0);
               gquad(m, cam, p0.add(h.scale(lw)), p1.add(h.scale(lw)), p1.add(h.scale(bw)), p0.add(h.scale(bw)), rim, 0.45F * env * lead0, 0.45F * env * lead1, 0.0F, 0.0F);
               gquad(m, cam, p0.subtract(h.scale(lw)), p1.subtract(h.scale(lw)), p1.subtract(h.scale(bw)), p0.subtract(h.scale(bw)), rim, 0.45F * env * lead0, 0.45F * env * lead1, 0.0F, 0.0F);
            }
         }
      }

      for (int ix = 0; ix < segs; ix++) {
         double t0 = (double)ix / (double)segs;
         double t1 = (double)(ix + 1) / (double)segs;
         double th0 = Math.toRadians((double)a0 + (double)(a1 - a0) * t0);
         double th1 = Math.toRadians((double)a0 + (double)(a1 - a0) * t1);
         Vec3 p0 = c.add(p.d.scale(Math.cos(th0) * (double)R)).add(p.u.scale(Math.sin(th0) * (double)R));
         Vec3 p1 = c.add(p.d.scale(Math.cos(th1) * (double)R)).add(p.u.scale(Math.sin(th1) * (double)R));
         double hh0 = (double)p.h * (0.3 + 0.7 * Math.pow(Math.sin(Math.PI * (0.04 + 0.92 * t0)), 0.6));
         double hh1 = (double)p.h * (0.3 + 0.7 * Math.pow(Math.sin(Math.PI * (0.04 + 0.92 * t1)), 0.6));

         for (int sgn = -1; sgn <= 1; sgn += 2) {
            Vec3 e0 = p0.add(h.scale(hh0 * (double)sgn));
            Vec3 e1 = p1.add(h.scale(hh1 * (double)sgn));
            Vec3 i0 = p0.add(h.scale(hh0 * (double)sgn * 0.9));
            Vec3 i1 = p1.add(h.scale(hh1 * (double)sgn * 0.9));
            quad(vc, m, n, cam, e0, e1, i1, i0, rim, 0.55F * env, 0.55F * env, 0.1F * env, 0.1F * env);
            gquad(m, cam, e0, e1, i1, i0, rim, 0.35F * env, 0.35F * env, 0.0F, 0.0F);
            if (isFlame(p.palette)) flameTongue(vc, m, n, cam, e0, e1, h.scale(sgn), p.h * 0.6 + 0.12, p.palette, env, ix * 2 + sgn + p.id);
         }
      }
   }

   static void crescentFx(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age, float env) {
      double lifeFrac = Mth.clamp(age / (double)Math.max(1, p.life), 0.0, 1.0);
      float R = p.r + (p.r2 - p.r) * (float)lifeFrac;
      Vec3 c = p.c.add(p.d.scale((double)p.speed * age));
      Vec3 h = p.d.cross(p.u).normalize();

      for (int j = 0; j < 5; j++) {
         float a = env * (1.0F - 0.2F * (float)j);
         crescent(vc, m, n, cam, c.subtract(p.d.scale(0.55 * (double)j)), p.d, p.u, (double)(R * (1.0F - 0.09F * (float)j)), p.palette, a);
      }

      crescent(vc, m, n, cam, c.add(p.d.scale(0.05)), p.d, h, (double)(R * 0.5F), p.palette, env * 0.55F);
      if (isFlame(p.palette)) {
         int N = 12;
         for (int i = 0; i < N; i++) {
            double r0 = Math.toRadians(-78.0 + 156.0 * i / N), r1 = Math.toRadians(-78.0 + 156.0 * (i + 1) / N);
            Vec3 o0 = c.add(p.u.scale(Math.sin(r0) * R)).add(p.d.scale((Math.cos(r0) - 1.0) * R * 0.55));
            Vec3 o1 = c.add(p.u.scale(Math.sin(r1) * R)).add(p.d.scale((Math.cos(r1) - 1.0) * R * 0.55));
            double rm = (r0 + r1) * 0.5;
            Vec3 out = p.u.scale(Math.sin(rm)).add(p.d.scale(Math.cos(rm) * 0.55)).subtract(p.d.scale(0.6)).normalize();   // outward, trailing behind
            flameTongue(vc, m, n, cam, o0, o1, out, R * 0.28, p.palette, env, i + p.id * 7);
         }
      }
   }

   /**
    * SLASH: one clean blade slash. A solid black crescent (thickest in the middle, needle-sharp tips) with a white-hot
    * cutting edge and a thin coloured glow, drawn in from one tip to the other over two ticks, then it thins and fades.
    */
   /**
    * A slash: a crescent-moon blade - needle-thin at both tips, thickest in the middle, strongly curved with the tips
    * trailing back - with a white-hot cutting edge along its front, a coloured glow ahead of the edge, and two fading
    * after-images behind it so it reads as something moving fast. It sweeps open over its first 2 ticks, flies
    * along d at {@code speed} and grows from r to r2. h = blade thickness (1 = normal).
    */
   static void slashFx(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age, double life) {
      double reveal = Mth.clamp(age / 2.0, 0.0, 1.0);
      double grow = 1.0 - Math.pow(1.0 - Mth.clamp(age / (life * 0.45), 0.0, 1.0), 3.0);
      double R = p.r + (p.r2 - p.r) * grow;
      float fade = (float)Mth.clamp((life - age) / (life * 0.45), 0.0, 1.0);
      double thin = Math.sqrt(fade);
      Vec3 c = p.c.add(p.d.scale(p.speed * age));
      Vec3 d = p.d, u = p.u;
      Vec3 hx = d.cross(u).normalize();
      float[] black = new float[]{0.01F, 0.0F, 0.015F};
      float[] rim = RIM[p.palette], hc = new float[]{1.0F, 0.97F, 0.95F};
      double thick = p.h > 0.0F ? p.h : 1.0;
      int N = 28;
      double half = 76.0, span = 2.0 * half * reveal, from = -half;
      // two after-images first (behind), then the blade
      for (int ghost = 2; ghost >= 0; ghost--) {
         double back = ghost * R * 0.16;
         float ga = ghost == 0 ? 1.0F : (ghost == 1 ? 0.35F : 0.15F);
         for (int i = 0; i < N; i++) {
            double f0 = from + span * i / N, f1 = from + span * (i + 1) / N;
            double r0 = Math.toRadians(f0), r1 = Math.toRadians(f1);
            // outer (cutting) edge: a strong curve whose tips trail back
            Vec3 o0 = c.add(u.scale(Math.sin(r0) * R)).add(d.scale((Math.cos(r0) - 1.0) * R * 0.55 - back));
            Vec3 o1 = c.add(u.scale(Math.sin(r1) * R)).add(d.scale((Math.cos(r1) - 1.0) * R * 0.55 - back));
            // crescent width: fat in the middle, a needle at the tips
            double w0 = R * 0.2 * thick * thin * Math.pow(Math.max(0.0, Math.cos(Math.toRadians(f0 / half * 90.0))), 1.6);
            double w1 = R * 0.2 * thick * thin * Math.pow(Math.max(0.0, Math.cos(Math.toRadians(f1 / half * 90.0))), 1.6);
            Vec3 i0 = o0.subtract(d.scale(w0)), i1 = o1.subtract(d.scale(w1));
            if (ghost > 0) {   // after-image: a faint smear of the blade's colour
               quad(vc, m, n, cam, o0, o1, i1, i0, black, 0.5F * ga * fade, 0.5F * ga * fade, 0.0F, 0.0F);
               gquad(m, cam, o0, o1, o1.subtract(d.scale(w1 * 0.5)), o0.subtract(d.scale(w0 * 0.5)), rim, 0.5F * ga * fade, 0.5F * ga * fade, 0.0F, 0.0F);
               continue;
            }
            // the solid black crescent
            quad(vc, m, n, cam, o0, o1, i1, i0, black, 0.97F * fade, 0.97F * fade, 0.9F * fade, 0.9F * fade);
            // a little depth across its own plane, so it still reads edge-on - thin, so it never looks like a plank
            double k0 = w0 * 0.22 * thick, k1 = w1 * 0.22 * thick;
            Vec3 m0 = o0.subtract(d.scale(w0 * 0.35)), m1 = o1.subtract(d.scale(w1 * 0.35));
            quad(vc, m, n, cam, m0.add(hx.scale(k0)), m1.add(hx.scale(k1)), m1.subtract(hx.scale(k1)), m0.subtract(hx.scale(k0)), black, 0.9F * fade, 0.9F * fade, 0.9F * fade, 0.9F * fade);
            // the white-hot cutting edge, and the coloured glow it throws ahead
            double e0 = Math.max(0.025, w0 * 0.2 + R * 0.006), e1 = Math.max(0.025, w1 * 0.2 + R * 0.006);
            gquad(m, cam, o0.add(d.scale(e0 * 0.4)), o1.add(d.scale(e1 * 0.4)), o1.subtract(d.scale(e1)), o0.subtract(d.scale(e0)), hc, fade, fade, fade, fade);
            gquad(m, cam, o0.add(d.scale(e0 * 0.4)), o1.add(d.scale(e1 * 0.4)), o1.add(d.scale(e1 * 4.5)), o0.add(d.scale(e0 * 4.5)), rim, 0.6F * fade, 0.6F * fade, 0.0F, 0.0F);
            gquad(m, cam, i0, i1, i1.add(d.scale(w1 * 0.4)), i0.add(d.scale(w0 * 0.4)), rim, 0.0F, 0.0F, 0.25F * fade, 0.25F * fade);
         }
      }
   }
}
