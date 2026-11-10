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
 * Mana Zones: the field laid over the ground (cached terrain heights), its edge and what rises from it.
 * (Split out of FxRenderer.)
 */
final class FxZones extends FxBase {
   private FxZones() {}

   static final double ZONE_OPEN = 12.0, ZONE_CLOSE = 10.0;
   static final int ZR = 10, ZS = 48;
   /** Terrain heights under each zone's sea: id -> {cx, cz, R, gy, heights...} so it lies on the ground, not over drops. */
   static final java.util.Map<Integer, double[]> ZONE_H = new java.util.HashMap<>();

   /**
    * Ground height of the block column at (x, z) near height gy - one raycast per column, cached, so a moving or
    * opening zone never re-casts hundreds of rays a frame (that was a stutter while walking in a zone).
    */
   static final java.util.Map<Long, Double> COLUMN_TOP = new java.util.HashMap<>();
   static double columnTopAge = -1;

   static double columnTop(net.minecraft.world.entity.Entity e, double x, double gy, double z) {
      int bx = Mth.floor(x), bz = Mth.floor(z), by = Mth.floor(gy);
      long key = ((long) bx & 0x1FFFFFL) << 42 | ((long) bz & 0x1FFFFFL) << 21 | ((long) by & 0x1FFFFFL);
      Double v = COLUMN_TOP.get(key);
      if (v != null) return v;
      Vec3 top = new Vec3(bx + 0.5, gy + 2.5, bz + 0.5);
      net.minecraft.world.phys.BlockHitResult hit = e.level().clip(new net.minecraft.world.level.ClipContext(top, top.subtract(0.0, 6.0, 0.0),
            net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.ANY, e));
      double y = hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? Double.NaN : hit.getLocation().y + 0.04;
      COLUMN_TOP.put(key, y);
      return y;
   }

   static double[] zoneHeights(net.minecraft.world.entity.Entity e, double cx, double gy, double cz, double R) {
      double[] h = ZONE_H.get(e.getId());
      if (h != null && Math.abs(h[0] - cx) < 0.35 && Math.abs(h[1] - cz) < 0.35 && Math.abs(h[2] - R) < 0.35 && Math.abs(h[3] - gy) < 0.2) return h;
      h = new double[4 + (ZR + 1) * ZS];
      h[0] = cx; h[1] = cz; h[2] = R; h[3] = gy;
      for (int i = 0; i <= ZR; i++) {
         double rr = R * i / ZR;
         for (int j = 0; j < ZS; j++) {
            double a = Math.PI * 2 * j / ZS;
            h[4 + i * ZS + j] = columnTop(e, cx + Math.cos(a) * rr, gy, cz + Math.sin(a) * rr);
         }
      }
      ZONE_H.put(e.getId(), h);
      return h;
   }

   // ------------------------------------------------------------------ Mana Zone (Yami)
   /** How far the zone has spread: grows out over ZONE_OPEN ticks, folds back in over ZONE_CLOSE after it ends. */
   static double zoneReach(double[] f, double now) {
      double age = now - f[0];
      if (age < 0.0) return 0.0;
      double open = Mth.clamp(age / ZONE_OPEN, 0.0, 1.0);
      open = 1.0 - Math.pow(1.0 - open, 3.0);
      if (now > f[1]) open *= 1.0 - Mth.clamp((now - f[1]) / ZONE_CLOSE, 0.0, 1.0);
      return f[2] * open;
   }

   /** Ground under the zone's owner: their feet when standing, else the first block below (kept while airborne). */
   static double zoneGround(net.minecraft.world.entity.Entity e, double[] f) {
      if (e.onGround() || Double.isNaN(f[3])) {
         Vec3 a = e.position().add(0.0, 0.2, 0.0);
         net.minecraft.world.phys.BlockHitResult h = e.level().clip(new net.minecraft.world.level.ClipContext(a, a.subtract(0.0, 8.0, 0.0),
               net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, e));
         f[3] = h.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? e.getY() : h.getLocation().y;
      }
      return f[3];
   }

   /**
    * Smoke for the zone, every tick: when it opens, a wall of black smoke rolls outward with the front; while it is up,
    * darkness drifts low over the ground swirling around you, curls up along the edge, and violet motes rise through it.
    */
   static boolean zoneFire(double[] f) { return f.length > 4 && ((int)f[4] == FIRE || (int)f[4] == CRIMSON); }

   /** One puff of the zone: darkness for Yami, a lick of heat-flame for Mereoleona. */
   static void zPuff(double[] f, Vec3 at, Vec3 v, float size, int life) {
      if (zoneFire(f)) DarkSmoke.crimsonFlame(at, v.add(0, 0.02, 0), size * 0.5F, Math.max(8, life / 2));
      else DarkSmoke.smoke(at, v, size, life);
   }

   static void zoneTick(Minecraft mc, net.minecraft.world.entity.Entity e, double[] f, double now) {
      java.util.concurrent.ThreadLocalRandom r = java.util.concurrent.ThreadLocalRandom.current();
      double R = zoneReach(f, now), age = now - f[0];
      if (R < 0.3) return;
      double gy = zoneGround(e, f);
      Vec3 c = new Vec3(e.getX(), gy, e.getZ());
      boolean closing = now > f[1];
      if (age < ZONE_OPEN || closing) {   // the front: a rolling wave of darkness
         for (int k = 0; k < 10; k++) {
            double a = r.nextDouble(Math.PI * 2);
            Vec3 dir = new Vec3(Math.cos(a), 0.0, Math.sin(a));
            Vec3 at = c.add(dir.scale(R)).add(0.0, r.nextDouble(0.2, 1.6), 0.0);
            Vec3 v = closing ? dir.scale(-0.25) : dir.scale(0.22);
            zPuff(f, at, v.add(0, 0.02, 0), (float)r.nextDouble(0.9, 1.6), r.nextInt(14, 24));
         }
         if (closing) return;
      }
      // low darkness flowing over the ground, turning slowly around you
      for (int k = 0; k < 3; k++) {
         double a = r.nextDouble(Math.PI * 2), d = Math.sqrt(r.nextDouble(0.08, 1.0)) * R;
         Vec3 at = c.add(Math.cos(a) * d, r.nextDouble(0.1, 0.5), Math.sin(a) * d);
         Vec3 tan = new Vec3(-Math.sin(a), 0.0, Math.cos(a)).scale(0.05);
         zPuff(f, at, tan.add(0, 0.006, 0), (float)r.nextDouble(0.7, 1.3), r.nextInt(26, 40));
      }
      // darkness curling up the edge of the zone
      for (int k = 0; k < 2; k++) {
         double a = r.nextDouble(Math.PI * 2);
         Vec3 at = c.add(Math.cos(a) * R, r.nextDouble(0.1, 1.2), Math.sin(a) * R);
         zPuff(f, at, new Vec3(-Math.cos(a) * 0.01, 0.05, -Math.sin(a) * 0.01), (float)r.nextDouble(0.6, 1.1), r.nextInt(22, 34));
      }
      if (r.nextInt(2) == 0) {
         double a = r.nextDouble(Math.PI * 2), d = r.nextDouble(R);
         if (zoneFire(f)) DarkSmoke.spark(c.add(Math.cos(a) * d, r.nextDouble(0.1, 1.0), Math.sin(a) * d), new Vec3(0, r.nextDouble(0.05, 0.12), 0), 0.04F, r.nextInt(14, 24));
         else DarkSmoke.mote(c.add(Math.cos(a) * d, r.nextDouble(0.1, 1.0), Math.sin(a) * d), new Vec3(0, r.nextDouble(0.02, 0.05), 0), 0.08F, r.nextInt(30, 50));
      }
   }

   /**
    * Yami's Mana Zone: the ground under the whole zone is drowned in dark mana (a near-black sea, rippling and turning
    * in slow swirls, thinning toward the edge), a faint dome of darkness arches over it with a violet sheen only at its
    * silhouette, and when it opens a violet shock front runs out from you to the edge.
    */
   static void zoneField(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, net.minecraft.world.entity.Entity e, float pt, double now, double[] f) {
      double R = zoneReach(f, now);
      if (R < 0.3) return;
      double age = now - f[0];
      Vec3 ep = e.getPosition(pt);
      double gy = zoneGround(e, f);
      Vec3 c = new Vec3(ep.x, gy + 0.04, ep.z);
      boolean fireZone = zoneFire(f);
      float[] rim = fireZone ? RIM[CRIMSON] : RIM[DARKNESS];
      float[] sea = fireZone ? new float[]{0.4F, 0.02F, 0.02F} : new float[]{0.012F, 0.0F, 0.03F};
      float seaMul = fireZone ? 0.45F : 1.0F;   // her zone: a glowing heat-haze over the ground, not a sea of black
      int RINGS = ZR, SEG = ZS;
      double[] hs = zoneHeights(e, ep.x, gy, ep.z, R);
      // the sea of dark mana on the ground, lying on the terrain (nothing drawn where there's no ground)
      for (int i = 0; i < RINGS; i++) {
         double r0 = R * i / RINGS, r1 = R * (i + 1) / RINGS;
         for (int j = 0; j < SEG; j++) {
            int j1 = (j + 1) % SEG;
            double y00 = hs[4 + i * SEG + j], y01 = hs[4 + i * SEG + j1], y11 = hs[4 + (i + 1) * SEG + j1], y10 = hs[4 + (i + 1) * SEG + j];
            if (Double.isNaN(y00) || Double.isNaN(y01) || Double.isNaN(y11) || Double.isNaN(y10)) continue;
            double yHi = Math.max(Math.max(y00, y01), Math.max(y11, y10)), yLo = Math.min(Math.min(y00, y01), Math.min(y11, y10));
            if (yHi - yLo > 0.52) continue;   // no sheets over blocks/cliffs: they hung in the air beside pedestals as a slab
            if (yHi - yLo > 0.02) { y00 = y01 = y11 = y10 = yHi; }   // a step (slab, path, stairs...): lie flat on the top, never sink into a block
            double a0 = Math.PI * 2 * j / SEG, a1 = Math.PI * 2 * (j + 1) / SEG;
            Vec3 p00 = new Vec3(c.x + Math.cos(a0) * r0, y00, c.z + Math.sin(a0) * r0), p01 = new Vec3(c.x + Math.cos(a1) * r0, y01, c.z + Math.sin(a1) * r0);
            Vec3 p11 = new Vec3(c.x + Math.cos(a1) * r1, y11, c.z + Math.sin(a1) * r1), p10 = new Vec3(c.x + Math.cos(a0) * r1, y10, c.z + Math.sin(a0) * r1);
            vtx(vc, m, n, cam, p00, sea, seaMul * seaA(r0 / R, a0, now));
            vtx(vc, m, n, cam, p01, sea, seaMul * seaA(r0 / R, a1, now));
            vtx(vc, m, n, cam, p11, sea, seaMul * seaA(r1 / R, a1, now));
            vtx(vc, m, n, cam, p10, sea, seaMul * seaA(r1 / R, a0, now));
            if (gv != null) {   // faint violet veins in the swirl
               gquad(m, cam, p00.add(0, 0.01, 0), p01.add(0, 0.01, 0), p11.add(0, 0.01, 0), p10.add(0, 0.01, 0), rim,
                  vein(r0 / R, a0, now), vein(r0 / R, a1, now), vein(r1 / R, a1, now), vein(r1 / R, a0, now));
            }
            if (i == RINGS - 1) {   // the edge: a soft violet band glowing on the ground
               float flick = 0.8F + 0.2F * (float)Math.sin(now * 0.7 + j);
               Vec3 q0 = new Vec3(c.x + Math.cos(a0) * (R - 0.6), y10 + 0.02, c.z + Math.sin(a0) * (R - 0.6));
               Vec3 q1 = new Vec3(c.x + Math.cos(a1) * (R - 0.6), y11 + 0.02, c.z + Math.sin(a1) * (R - 0.6));
               gquad(m, cam, q0, q1, p11.add(0, 0.02, 0), p10.add(0, 0.02, 0), rim, 0.0F, 0.0F, 0.45F * flick, 0.45F * flick);
               // opening: a violet shock front rising off the edge as it races out
               if (age < ZONE_OPEN + 2.0) {
                  float sa = (float)(1.0 - Mth.clamp(age / (ZONE_OPEN + 2.0), 0.0, 1.0));
                  gquad(m, cam, p10.add(0, 0.02, 0), p11.add(0, 0.02, 0), p11.add(0, 1.4 * sa, 0), p10.add(0, 1.4 * sa, 0), rim, sa * 0.7F, sa * 0.7F, 0.0F, 0.0F);
               }
            }
         }
      }
      // the dome: Yami's is a real dome of darkness over the zone like the Black Cocoon - a shaded, see-through shell
      // with a violet rim; from inside it (first person) it is only a faint tint. Mereoleona's stays a heat-haze.
      boolean insideDome = !fireZone && cam.distanceTo(c) < R;
      float[] shell = {0.02F, 0.0F, 0.05F};
      int LAT = 10, LON = 40;
      for (int i = 0; i < LAT; i++) {
         double e0 = Math.PI / 2 * i / LAT, e1 = Math.PI / 2 * (i + 1) / LAT;
         for (int j = 0; j < LON; j++) {
            double a0 = Math.PI * 2 * j / LON, a1 = Math.PI * 2 * (j + 1) / LON;
            Vec3[] q = {dome(a0, e0), dome(a1, e0), dome(a1, e1), dome(a0, e1)};
            double hy = fireZone ? 0.6 : 0.85;
            if (!fireZone && !insideDome) {   // from outside only the near side, so the shell doesn't double up
               Vec3 qc = q[0].add(q[2]).scale(0.5);
               if (qc.dot(cam.subtract(c.add(qc.x * R, qc.y * R * hy, qc.z * R))) < 0.0) continue;
            }
            for (Vec3 nv : q) {
               Vec3 w = c.add(nv.x * R, nv.y * R * hy, nv.z * R);
               float fres = (float)Math.pow(1.0 - Math.abs(nv.dot(cam.subtract(w).normalize())), 3.0);
               if (fireZone) vtx(vc, m, n, cam, w, sea, (0.05F + 0.22F * fres) * seaMul);
               else {
                  float a = insideDome ? 0.05F + 0.08F * fres : 0.26F + 0.38F * fres;
                  float[] col = {shell[0] + rim[0] * fres * 0.4F, shell[1] + rim[1] * fres * 0.4F, shell[2] + rim[2] * fres * 0.4F};
                  vtx(vc, m, n, cam, w, col, a);
               }
            }
            if (gv != null) {
               for (Vec3 nv : q) {
                  Vec3 w = c.add(nv.x * R, nv.y * R * hy, nv.z * R);
                  float fres = (float)Math.pow(1.0 - Math.abs(nv.dot(cam.subtract(w).normalize())), 4.0);
                  gvtx(m, cam, w, rim, fres * (fireZone ? 0.22F : (insideDome ? 0.12F : 0.4F)) * (float)(1.0 - nv.y * 0.5));
               }
            }
         }
      }
   }

   static Vec3 dome(double a, double el) {
      return new Vec3(Math.cos(a) * Math.cos(el), Math.sin(el), Math.sin(a) * Math.cos(el));
   }

   /** Opacity of the dark sea: deep near you, thinning toward the edge, rippling in turning spiral swirls. */
   static float seaA(double t, double a, double now) {
      double swirl = 0.5 + 0.5 * Math.sin(a * 3.0 + t * 9.0 - now * 0.06);
      double base = 0.62 - 0.32 * t;
      return (float)Mth.clamp(base * (0.8 + 0.3 * swirl), 0.0, 0.85) * (t >= 0.999 ? 0.4F : 1.0F);
   }

   static float vein(double t, double a, double now) {
      double s = Math.sin(a * 3.0 + t * 9.0 - now * 0.06);
      return (float)(Math.max(0.0, s - 0.85) / 0.15 * 0.18 * (0.4 + 0.6 * t));
   }
}
