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
 * What each live effect sheds every tick, by palette: darkness smoke, fire and embers, crackling lightning, wind streaks, mercury
 * beads, bone dust (FxRenderer.onTick calls these for every effect).
 * (Split out of FxRenderer.)
 */
final class FxEmitters extends FxBase {
   private FxEmitters() {}


   /**
    * Yami's darkness: every effect drawn in palette 10 sheds real smoke while it lives - black puffs curling off the
    * blade edge of slashes and crescents, along beams and trails, around walls and spikes - plus the odd violet mote.
    */
   static void emitDarkness(FxRenderer.Fx f, double age) {
      FxPacket p = f.p;
      double life = f.killAge >= 0.0 ? f.killAge : (double)p.life;
      if (age < 0.0 || age > life) return;
      java.util.concurrent.ThreadLocalRandom r = java.util.concurrent.ThreadLocalRandom.current();
      double frac = Mth.clamp(age / Math.max(1.0, life), 0.0, 1.0);
      int burst = age < 1.0 ? 2 : 1;
      switch (p.shape) {
         case 13: {   // slash: smoke streams off the back of the blade
            double grow = 1.0 - Math.pow(1.0 - Mth.clamp(age / (life * 0.45), 0.0, 1.0), 3.0);
            double R = p.r + (p.r2 - p.r) * grow;
            Vec3 c = p.c.add(p.d.scale(p.speed * age));
            int k = (int)Mth.clamp(R * 0.35, 1, 4) * burst;   // a light veil of darkness, not a smoke-screen
            for (int i = 0; i < k; i++) {
               double fd = Math.toRadians(r.nextDouble(-70, 70));
               Vec3 o = c.add(p.u.scale(Math.sin(fd) * R)).add(p.d.scale((Math.cos(fd) - 1.0) * R * 0.35 - R * 0.08));
               float sz = (float)Mth.clamp(R * 0.14 * Math.cos(fd), 0.25, 1.6);
               DarkSmoke.smoke(o, p.d.scale(p.speed * 0.25).add(jit(r, 0.04)), sz, r.nextInt(12, 22));
            }
            if (r.nextInt(2) == 0) DarkSmoke.mote(c.add(jit(r, R * 0.4)), jit(r, 0.05).add(0, 0.03, 0), 0.09F, 20);
            break;
         }
         case 1: {    // crescent
            float R = p.r + (p.r2 - p.r) * (float)frac;
            Vec3 c = p.c.add(p.d.scale(p.speed * age));
            int k = (int)Mth.clamp(R * 1.5, 2, 7) * burst;
            for (int i = 0; i < k; i++) {
               double fd = Math.toRadians(r.nextDouble(-70, 70));
               Vec3 o = c.add(p.u.scale(Math.sin(fd) * R)).add(p.d.scale((Math.cos(fd) - 1.0) * R * 0.55 - R * 0.2));
               DarkSmoke.smoke(o, p.d.scale(p.speed * 0.2).add(jit(r, 0.03)), (float)Mth.clamp(R * 0.2, 0.2, 1.4), r.nextInt(10, 18));
            }
            break;
         }
         case 2: {    // beam / trail: smoke along it, mostly while it's fresh
            if (age > 3.0 && r.nextInt(3) != 0) break;
            double len = p.d.length();
            int k = (int)Mth.clamp(len * 0.6, 1, 6);
            for (int i = 0; i < k; i++) {
               Vec3 o = p.c.add(p.d.scale(r.nextDouble()));
               DarkSmoke.smoke(o, jit(r, 0.02).add(0, 0.01, 0), (float)Mth.clamp(p.r * 2.2 + 0.15, 0.15, 1.3), r.nextInt(10, 20));
            }
            break;
         }
         case 0: {    // wall / ring: smoke rising off the arc
            float R = p.r + (p.r2 - p.r) * (float)frac;
            Vec3 c = p.c.add(p.d.scale(p.speed * age));
            double span = p.a1 - p.a0;
            double want = Mth.clamp(R * Math.abs(span) / 360.0 * Mth.clamp(p.h * 2.0, 0.1, 1.0), 0.0, 6.0);
            int k = (int)want + (r.nextDouble() < want - (int)want ? 1 : 0);   // thin rings smoke only a little
            for (int i = 0; i < k; i++) {
               double th = Math.toRadians(p.a0 + span * r.nextDouble());
               Vec3 o = c.add(p.d.scale(Math.cos(th) * R)).add(p.u.scale(Math.sin(th) * R));
               DarkSmoke.smoke(o, o.subtract(c).normalize().scale(0.03).add(0, 0.02, 0), (float)Mth.clamp(p.h * 0.9 + 0.2, 0.2, 1.4), r.nextInt(10, 20));
            }
            break;
         }
         case 7:
         case 8: {    // boxes / spikes: smoke off their length
            Vec3 c = p.c.add(p.v.scale(Math.min(age, p.a1 > 0 ? p.a1 : age)));
            Vec3 o = c.add(p.d.scale(p.r * (p.shape == 8 ? r.nextDouble() : r.nextDouble(-1, 1))));
            DarkSmoke.smoke(o, jit(r, 0.03).add(0, 0.015, 0), (float)Mth.clamp(p.r2 * 2.0, 0.2, 1.0), r.nextInt(10, 18));
            break;
         }
         default:
      }
   }

   /** Every lightning-coloured effect crackles: small live arcs jump off beams, slashes, crescents and walls. */
   static void emitLightning(FxRenderer.Fx f, double age) {
      FxPacket p = f.p;
      double life = f.killAge >= 0.0 ? f.killAge : (double)p.life;
      if (age < 0.0 || age > life) return;
      java.util.concurrent.ThreadLocalRandom r = java.util.concurrent.ThreadLocalRandom.current();
      if (r.nextInt(2) != 0) return;
      Vec3 at;
      switch (p.shape) {
         case 2 -> at = p.c.add(p.d.scale(r.nextDouble()));
         case 13, 1 -> {
            double fd = Math.toRadians(r.nextDouble(-70, 70));
            double R = p.r + (p.r2 - p.r) * Mth.clamp(age / Math.max(1.0, life), 0.0, 1.0);
            at = p.c.add(p.d.scale(p.speed * age)).add(p.u.scale(Math.sin(fd) * R));
         }
         case 0 -> {
            double th = Math.toRadians(p.a0 + (p.a1 - p.a0) * r.nextDouble());
            double R = p.r + (p.r2 - p.r) * Mth.clamp(age / Math.max(1.0, life), 0.0, 1.0);
            at = p.c.add(p.d.scale(p.speed * age)).add(p.d.scale(Math.cos(th) * R)).add(p.u.scale(Math.sin(th) * R));
         }
         default -> { return; }
      }
      FxRenderer.localBolt(at, at.add(jit(r, 0.9)), 0.035F, 3, 0);
   }

   /** Real fire: flames pour off fireballs, explosions burst into a ball of flame and smoke, fire walls and trails burn. */
   static void emitFire(FxRenderer.Fx f, double age) {
      FxPacket p = f.p;
      double life = f.killAge >= 0.0 ? f.killAge : (double)p.life;
      if (age < 0.0 || age > life) return;
      java.util.concurrent.ThreadLocalRandom r = java.util.concurrent.ThreadLocalRandom.current();
      switch (p.shape) {
         case 20: {
            Vec3 c = p.c.add(p.v.scale(mv(p, age)));
            if (p.h > 0.5F) {   // explosion: one big burst at the start, then rolling flames and smoke
               if (age < 1.0) {
                  int k = (int)Mth.clamp(p.r2 * 7, 10, 45);
                  for (int i = 0; i < k; i++) {
                     Vec3 d = jit(r, 1.0).normalize();
                     fl(c.add(d.scale(p.r2 * 0.25)), d.scale(p.r2 * r.nextDouble(0.06, 0.13)).add(0, 0.03, 0), (float)Mth.clamp(p.r2 * 0.28, 0.35, 1.8), r.nextInt(12, 22));
                  }
                  for (int i = 0; i < k / 3; i++) {
                     Vec3 d = jit(r, 1.0).normalize();
                     smk(c.add(d.scale(p.r2 * 0.5)), d.scale(p.r2 * 0.04).add(0, 0.05, 0), (float)Mth.clamp(p.r2 * 0.3, 0.4, 2.0), r.nextInt(24, 40));
                  }
               }
               break;
            }
            if (p.r < 0.1F) break;
            int k = p.r > 1.0F ? 5 : (p.r > 0.35F ? 3 : 2);
            for (int i = 0; i < k; i++) {
               Vec3 d = jit(r, 1.0).normalize();
               fl(c.add(d.scale(p.r * 0.8)), p.v.scale(-0.15).add(d.scale(0.02)).add(0, 0.02, 0), p.r * (float)r.nextDouble(0.6, 1.0), r.nextInt(6, 12));
            }
            if (p.v.lengthSqr() > 0.01 && r.nextInt(3) == 0) smk(c.subtract(p.v.scale(0.6)), new Vec3(0, 0.03, 0), p.r * 1.2F, r.nextInt(16, 26));
            break;
         }
         case 2: {
            if (age > 3.0 && r.nextInt(3) != 0) break;
            double len = p.d.length();
            int k = (int)Mth.clamp(len * 0.5, 1, 5);
            for (int i = 0; i < k; i++) {
               fl(p.c.add(p.d.scale(r.nextDouble())).add(jit(r, p.r)), new Vec3(0, 0.03, 0), (float)Mth.clamp(p.r * 1.6 + 0.12, 0.15, 1.0), r.nextInt(8, 14));
            }
            break;
         }
         case 0: {
            double frac = Mth.clamp(age / Math.max(1.0, life), 0.0, 1.0);
            float R = p.r + (p.r2 - p.r) * (float)frac;
            Vec3 c = p.c.add(p.d.scale(p.speed * age));
            double span = p.a1 - p.a0;
            double want = Mth.clamp(R * Math.abs(span) / 360.0 * Mth.clamp(p.h * 2.0, 0.15, 1.6), 0.0, 10.0);
            int k = (int)want + (r.nextDouble() < want - (int)want ? 1 : 0);
            Vec3 hx = p.d.cross(p.u).normalize();
            for (int i = 0; i < k; i++) {
               double th = Math.toRadians(p.a0 + span * r.nextDouble());
               Vec3 o = c.add(p.d.scale(Math.cos(th) * R)).add(p.u.scale(Math.sin(th) * R)).add(hx.scale(r.nextDouble(-p.h, p.h)));
               fl(o, new Vec3(0, 0.035, 0), (float)Mth.clamp(p.h * 0.6 + 0.2, 0.2, 1.0), r.nextInt(8, 14));
            }
            break;
         }
         case 13: {
            double grow = 1.0 - Math.pow(1.0 - Mth.clamp(age / (life * 0.45), 0.0, 1.0), 3.0);
            double R = p.r + (p.r2 - p.r) * grow;
            Vec3 c = p.c.add(p.d.scale(p.speed * age));
            for (int i = 0; i < (int)Mth.clamp(R * 1.5, 2, 7); i++) {
               double fd = Math.toRadians(r.nextDouble(-70, 70));
               Vec3 o = c.add(p.u.scale(Math.sin(fd) * R)).add(p.d.scale((Math.cos(fd) - 1.0) * R * 0.35));
               fl(o, p.d.scale(p.speed * 0.3).add(0, 0.03, 0), (float)Mth.clamp(R * 0.12, 0.2, 0.8), r.nextInt(6, 12));
            }
            break;
         }
         case 7: {   // burning solids (Magna's bat): flames licking along it
            if (r.nextInt(2) == 0) {
               Vec3 o = travelledPos(f, age).add(p.d.scale(p.r * r.nextDouble(-1, 1)));
               fl(o, new Vec3(0, 0.035, 0), (float)Mth.clamp(p.r2 * 2.6, 0.12, 0.45), r.nextInt(5, 9));
            }
            break;
         }
         case 8: {
            if (p.speed != FxPacket.HOLD || r.nextInt(3) == 0) {
               Vec3 o = travelledPos(f, age).add(p.d.scale(p.r * r.nextDouble()));
               fl(o, new Vec3(0, 0.03, 0), (float)Mth.clamp(p.r2 * 2.0, 0.12, 0.6), r.nextInt(6, 10));
            }
            break;
         }
         default:
      }
   }

   /** Smoke off fire - except Mereoleona's, which burns clean: embers fly instead. */
   static void smk(Vec3 at, Vec3 v, float size, int life) {
      if (flamePal == CRIMSON) DarkSmoke.emberSpark(at, v.add(0, 0.08, 0), 0.04F, Math.max(8, life / 2));
      else DarkSmoke.smoke(at, v, size, life);
   }

   static void fl(Vec3 at, Vec3 v, float size, int life) {
      if (flamePal == BLUE_FIRE) DarkSmoke.blueFlame(at, v, size, life);
      else if (flamePal == CRIMSON) DarkSmoke.crimsonFlame(at, v, size, life);
      else DarkSmoke.flame(at, v, size, life);
   }

   static Vec3 travelledPos(FxRenderer.Fx f, double age) { return travelled(f, age); }

   /** Bones bursting out of the ground throw up dust and chips of earth; flying bones shed a little bone dust. */
   static void emitBone(FxRenderer.Fx f, double age) {
      FxPacket p = f.p;
      java.util.concurrent.ThreadLocalRandom r = java.util.concurrent.ThreadLocalRandom.current();
      if (p.speed == FxPacket.GROUND && age < 1.0) {
         Vec3 base = p.c.subtract(p.d.normalize().scale(p.r));
         for (int i = 0; i < 3; i++) DarkSmoke.dust(base.add(jit(r, p.r2 * 2)), new Vec3(r.nextDouble(-0.08, 0.08), r.nextDouble(0.05, 0.15), r.nextDouble(-0.08, 0.08)), (float)Mth.clamp(p.r2 * 3, 0.3, 0.8), r.nextInt(12, 20));
      } else if (p.v.lengthSqr() > 0.25 && r.nextInt(4) == 0) {
         DarkSmoke.dust(travelled(f, age), p.v.scale(-0.05), 0.2F, r.nextInt(8, 12));
      }
   }

   /** Mercury drips: small silver beads fall off fresh mercury trails and moving mercury spheres. */
   static void emitMercury(FxRenderer.Fx f, double age) {
      FxPacket p = f.p;
      java.util.concurrent.ThreadLocalRandom r = java.util.concurrent.ThreadLocalRandom.current();
      if (p.shape == 2 && age < 2.0 && r.nextInt(2) == 0) {
         Vec3 o = p.c.add(p.d.scale(r.nextDouble()));
         DarkSmoke.bead(o, new Vec3(r.nextDouble(-0.03, 0.03), r.nextDouble(0.0, 0.05), r.nextDouble(-0.03, 0.03)), (float)Mth.clamp(p.r * 0.5, 0.03, 0.08), r.nextInt(10, 18));
      } else if (p.shape == 19 && p.r > 0.3F && age < p.life && r.nextInt(p.r > 1.5F ? 2 : 4) == 0) {
         Vec3 c = p.c.add(p.v.scale(mv(p, age)));
         Vec3 nrm = new Vec3(r.nextDouble(-1, 1), r.nextDouble(-1, 0.2), r.nextDouble(-1, 1)).normalize();
         DarkSmoke.bead(c.add(nrm.scale(p.r)), p.v.scale(0.4).add(nrm.scale(0.04)), (float)Mth.clamp(p.r * 0.08, 0.03, 0.1), r.nextInt(10, 20));
      }
   }

   /** Yuno's wind: every effect in the wind palette throws off thin streaks of air that race along with it. */
   static void emitWind(FxRenderer.Fx f, double age) {
      FxPacket p = f.p;
      if (p.shape == 29) {   // the breath: streaks and leaves carried forward in the cone
         java.util.concurrent.ThreadLocalRandom r = java.util.concurrent.ThreadLocalRandom.current();
         Vec3 d = p.d.normalize();
         double front = p.r * Mth.clamp(age / (p.life * 0.55), 0.0, 1.0);
         for (int i = 0; i < 6; i++) {
            double sd = r.nextDouble() * front;
            Vec3 o = p.c.add(d.scale(sd)).add(jit(r, sd * Math.tan(p.h) * 0.7 + 0.1));
            DarkSmoke.streak(o, d.scale(r.nextDouble(0.7, 1.3)), 0.06F, r.nextInt(5, 9));
         }
         if (age < 4.0) for (int i = 0; i < 2; i++) DarkSmoke.dust(p.c.add(d.scale(r.nextDouble() * front)).add(jit(r, 0.8)), d.scale(0.5).add(0, 0.05, 0), (float)r.nextDouble(0.3, 0.6), r.nextInt(10, 16));
         return;
      }
      if (p.shape == 28) {   // tornado: streaks whipping round it, dust and debris thrown about its foot
         if (age > 1.0) return;
         java.util.concurrent.ThreadLocalRandom r = java.util.concurrent.ThreadLocalRandom.current();
         for (int i = 0; i < 4; i++) {
            double y = r.nextDouble(p.h), rr = p.r * (0.18 + 0.82 * Math.pow(y / p.h, 0.85)), a = r.nextDouble(Math.PI * 2);
            Vec3 at = p.c.add(Math.cos(a) * rr, y, Math.sin(a) * rr);
            if (p.palette == 9) {   // Black Hurricane: black flame and embers whipped round instead of air
               DarkSmoke.smoke(at, new Vec3(-Math.sin(a), 0.12, Math.cos(a)).scale(0.5), 0.5F, r.nextInt(10, 16));
               if (i % 2 == 0) DarkSmoke.emberSpark(at, new Vec3(-Math.sin(a), 0.2, Math.cos(a)).scale(0.7), 0.06F, r.nextInt(6, 10));
            } else DarkSmoke.streak(at, new Vec3(-Math.sin(a), 0.15, Math.cos(a)).scale(0.6), 0.07F, r.nextInt(5, 9));
         }
         for (int i = 0; i < 2; i++) {
            double a = r.nextDouble(Math.PI * 2), rr = p.r * r.nextDouble(0.2, 0.6);
            DarkSmoke.dust(p.c.add(Math.cos(a) * rr, r.nextDouble(0.1, 1.2), Math.sin(a) * rr), new Vec3(-Math.sin(a) * 0.3, r.nextDouble(0.05, 0.25), Math.cos(a) * 0.3), (float)r.nextDouble(0.4, 0.9), r.nextInt(14, 24));
         }
         return;
      }
      double life = f.killAge >= 0.0 ? f.killAge : (double)p.life;
      if (age < 0.0 || age > life) return;
      java.util.concurrent.ThreadLocalRandom r = java.util.concurrent.ThreadLocalRandom.current();
      double frac = Mth.clamp(age / Math.max(1.0, life), 0.0, 1.0);
      switch (p.shape) {
         case 13: {
            double grow = 1.0 - Math.pow(1.0 - Mth.clamp(age / (life * 0.45), 0.0, 1.0), 3.0);
            double R = p.r + (p.r2 - p.r) * grow;
            Vec3 c = p.c.add(p.d.scale(p.speed * age));
            int k = (int)Mth.clamp(R, 2, 6);
            for (int i = 0; i < k; i++) {
               double fd = Math.toRadians(r.nextDouble(-70, 70));
               Vec3 o = c.add(p.u.scale(Math.sin(fd) * R)).add(p.d.scale((Math.cos(fd) - 1.0) * R * 0.35));
               DarkSmoke.streak(o, p.d.scale(Math.max(0.3, p.speed * 0.6)).add(jit(r, 0.05)), (float)Mth.clamp(R * 0.04, 0.04, 0.14), r.nextInt(5, 10));
            }
            break;
         }
         case 1: {
            float R = p.r + (p.r2 - p.r) * (float)frac;
            Vec3 c = p.c.add(p.d.scale(p.speed * age));
            for (int i = 0; i < 2; i++) {
               double fd = Math.toRadians(r.nextDouble(-70, 70));
               DarkSmoke.streak(c.add(p.u.scale(Math.sin(fd) * R)), p.d.scale(Math.max(0.3, p.speed * 0.6)), 0.06F, r.nextInt(5, 9));
            }
            break;
         }
         case 2: {
            if (age > 2.0) break;
            double len = p.d.length();
            if (len < 1.0E-3) break;
            Vec3 dir = p.d.scale(1.0 / len);
            int k = (int)Mth.clamp(len * 0.4, 1, 4);
            for (int i = 0; i < k; i++) {
               Vec3 o = p.c.add(p.d.scale(r.nextDouble())).add(jit(r, p.r * 1.5));
               DarkSmoke.streak(o, dir.scale(r.nextDouble(0.3, 0.8)), (float)Mth.clamp(p.r * 0.4 + 0.03, 0.03, 0.14), r.nextInt(5, 10));
            }
            break;
         }
         case 0: {
            float R = p.r + (p.r2 - p.r) * (float)frac;
            Vec3 c = p.c.add(p.d.scale(p.speed * age));
            double span = p.a1 - p.a0;
            double want = Mth.clamp(R * Math.abs(span) / 360.0 * Mth.clamp(p.h * 3.0, 0.15, 1.0), 0.0, 5.0);
            int k = (int)want + (r.nextDouble() < want - (int)want ? 1 : 0);
            for (int i = 0; i < k; i++) {
               double th = Math.toRadians(p.a0 + span * r.nextDouble());
               Vec3 rad = p.d.scale(Math.cos(th)).add(p.u.scale(Math.sin(th)));
               Vec3 tan = p.d.scale(-Math.sin(th)).add(p.u.scale(Math.cos(th)));
               DarkSmoke.streak(c.add(rad.scale(R)), tan.scale(0.35).add(p.d.scale(p.speed * 0.5)), 0.06F, r.nextInt(5, 10));
            }
            break;
         }
         default:
      }
   }
}
