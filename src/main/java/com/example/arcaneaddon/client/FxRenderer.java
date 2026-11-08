package com.example.arcaneaddon.client;

import com.example.arcaneaddon.network.FxPacket;
import com.example.arcaneaddon.registry.AddonItemRegistry;
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

@EventBusSubscriber(
   modid = "arcane_addon",
   value = {Dist.CLIENT}
)
public class FxRenderer {
   private static final ResourceLocation WHITE = ResourceLocation.fromNamespaceAndPath("arcane_addon", "textures/effect/white.png");
   private static final float[][] BODY = new float[][]{
      {0.06F, 0.0F, 0.04F},
      {0.03F, 0.0F, 0.08F},
      {0.22F, 0.05F, 0.0F},
      {0.02F, 0.1F, 0.16F},
      {0.12F, 0.11F, 0.0F},
      {0.04F, 0.13F, 0.09F},
      {0.07F, 0.07F, 0.09F},
      {0.09F, 0.10F, 0.12F},   // 7 steel
      {0.16F, 0.10F, 0.02F},   // 8 gold
      {0.07F, 0.0F, 0.0F},     // 9 devil red (devil form)
      {0.015F, 0.0F, 0.035F},  // 10 Yami's darkness
      {0.42F, 0.45F, 0.5F},    // 11 mercury (liquid chrome)
      {0.0F, 0.06F, 0.3F},     // 12 blue flame
      {0.02F, 0.07F, 0.22F},   // 13 blue lightning (Luck)
      {0.28F, 0.0F, 0.03F}     // 14 crimson flame (Mereoleona)
   };
   private static final float[][] RIM = new float[][]{
      {1.0F, 0.1F, 0.06F}, {0.67F, 0.24F, 1.0F}, {1.0F, 0.6F, 0.12F}, {0.6F, 0.88F, 1.0F}, {1.0F, 0.96F, 0.44F}, {0.75F, 1.0F, 0.84F}, {0.95F, 0.95F, 1.0F},
      {0.78F, 0.85F, 0.94F},   // 7 steel
      {1.0F, 0.84F, 0.36F},    // 8 gold
      {1.0F, 0.07F, 0.05F},    // 9 devil red (devil form)
      {0.6F, 0.2F, 1.0F},      // 10 Yami's darkness (smoke comes off everything drawn in it)
      {0.86F, 0.92F, 1.0F},    // 11 mercury
      {0.35F, 0.65F, 1.0F},    // 12 blue flame (Calidos Brachium Purgatory)
      {0.45F, 0.72F, 1.0F},    // 13 blue lightning (Luck Voltia)
      {1.0F, 0.16F, 0.1F}      // 14 crimson flame (Mereoleona Vermillion)
   };
   /** Overall strength of every world effect. Lower = subtler. */
   private static final float FX_ALPHA = 1.0F;
   /** Effects closer than this to the camera fade out so they never cover the screen (first person). */
   private static final double NEAR_FADE_START = 0.8;
   private static final double NEAR_FADE_RANGE = 2.4;
   private static final float NEAR_FADE_MIN = 0.15F;
   private static boolean nearFade = false;
   private static double bookTime = 0.0;
   private static final List<FxRenderer.Fx> FX = new ArrayList<>();
   /** Players in Devil Union: entity id -> {start tick, end tick}. Fed by FxPacket shape 6. */
   private static final java.util.Map<Integer, double[]> DEVIL = new java.util.HashMap<>();
   /** Black Cocoon shells: entity id -> {start, end, radius, close ticks}. Fed by FxPacket shape 15. */
   private static final java.util.Map<Integer, double[]> COCOON = new java.util.HashMap<>();
   /** Spirit Dive: entity id -> {start, end}. Fed by FxPacket shape 18. */
   private static final java.util.Map<Integer, double[]> SPIRIT = new java.util.HashMap<>();

   /** True while the player with this entity id is in Thunder God Mode (as far as this client knows). */
   public static boolean inThunderMode(int entityId, double now) {
      double[] f = THUNDER.get(entityId);
      return f != null && now >= f[0] && now <= f[1];
   }

   /** True while the player with this entity id is in Spirit Dive (as far as this client knows). */
   public static boolean inSpiritDive(int entityId, double now) {
      double[] f = SPIRIT.get(entityId);
      return f != null && now >= f[0] && now <= f[1];
   }
   /** The Mana Zone palette (2 = Mereoleona's fire zone, 10 = Yami's) of this entity, or -1 when none is up. */
   public static int zonePalette(int entityId, double now) {
      double[] f = ZONES.get(entityId);
      return f != null && now >= f[0] && now <= f[1] && f.length > 4 ? (int)f[4] : -1;
   }
   /** Mana Zones: entity id -> {start, end, radius, ground y (NaN = unknown)}. Fed by FxPacket shape 17. */
   private static final java.util.Map<Integer, double[]> ZONES = new java.util.HashMap<>();
   private static final double ZONE_OPEN = 12.0, ZONE_CLOSE = 10.0;
   private static final int ZR = 10, ZS = 48;
   /** Terrain heights under each zone's sea: id -> {cx, cz, R, gy, heights...} so it lies on the ground, not over drops. */
   private static final java.util.Map<Integer, double[]> ZONE_H = new java.util.HashMap<>();

   private static double[] zoneHeights(net.minecraft.world.entity.Entity e, double cx, double gy, double cz, double R) {
      double[] h = ZONE_H.get(e.getId());
      if (h != null && Math.abs(h[0] - cx) < 0.35 && Math.abs(h[1] - cz) < 0.35 && Math.abs(h[2] - R) < 0.35 && Math.abs(h[3] - gy) < 0.2) return h;
      h = new double[4 + (ZR + 1) * ZS];
      h[0] = cx; h[1] = cz; h[2] = R; h[3] = gy;
      for (int i = 0; i <= ZR; i++) {
         double rr = R * i / ZR;
         for (int j = 0; j < ZS; j++) {
            double a = Math.PI * 2 * j / ZS;
            Vec3 top = new Vec3(cx + Math.cos(a) * rr, gy + 2.5, cz + Math.sin(a) * rr);
            net.minecraft.world.phys.BlockHitResult hit = e.level().clip(new net.minecraft.world.level.ClipContext(top, top.subtract(0.0, 6.0, 0.0),
                  net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.ANY, e));
            h[4 + i * ZS + j] = hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? Double.NaN : hit.getLocation().y + 0.04;
         }
      }
      ZONE_H.put(e.getId(), h);
      return h;
   }
   private static final int EMBER = 9;
   private static final List<FxRenderer.BookDraw> BOOKS = new ArrayList<>();
   private static final List<FxRenderer.AuraDraw> AURAS = new ArrayList<>();
   /** Separate buffer for the additive glow layer (cores, rims, metal highlights). */
   private static final MultiBufferSource.BufferSource GLOW_SRC = MultiBufferSource.immediate(new BufferBuilder(262144));
   private static VertexConsumer gv;
   /** Key light for the shaded 3D solids. */
   private static final Vec3 LIGHT = new Vec3(0.35, 1.0, 0.25).normalize();

   /**
    * Black-flame outline and turning pages for an open grimoire drawn at {@code pos} with the same transform as its
    * 3D model. hw/hh = page width / half height and vDeg = how far the boards open from flat, in model blocks.
    */
   public static void queueBook(Vec3 pos, float yawS, float tiltDeg, float scale, float alpha, float flipCycle, float hw, float hh, float vDeg, int pal, int style, int light) {
      BOOKS.add(new FxRenderer.BookDraw(pos, yawS, tiltDeg, scale, alpha, flipCycle, hw, hh, vDeg, pal, style, light));
   }

   public static void queueAura(Vec3 a, Vec3 b, float width, int pal, float alpha) {
      AURAS.add(new FxRenderer.AuraDraw(a, b, width, pal, alpha));
   }

   public static void spawn(FxPacket p) {
      if (p.shape == 4) {   // Ki Sense mark: id = entity, palette = creature type, life = duration
         KiSenseRenderer.mark(p.id, p.palette, p.life);
         return;
      }
      if (p.shape == 6) {   // Devil Union form: id = player, life = ticks left (0 = ending now)
         Minecraft mc0 = Minecraft.getInstance();
         if (mc0.level != null) {
            double now0 = mc0.level.getGameTime();
            double[] f = DEVIL.get(p.id);
            if (p.life > 0) {
               if (f == null || f[1] < now0) DEVIL.put(p.id, new double[]{now0, now0 + p.life});   // a new transformation
               else f[1] = now0 + p.life;                                                           // just a refresh
            } else if (f != null) {
               f[1] = Math.min(f[1], now0);                                                         // start fading out
            }
         }
         return;
      }
      if (p.shape == 24 || p.shape == 25) {   // Thunder God Mode (24) / gloves (25): id = player, life = ticks left (0 = off)
         Minecraft mc0 = Minecraft.getInstance();
         if (mc0.level != null) {
            java.util.Map<Integer, double[]> map = p.shape == 24 ? THUNDER : GLOVES;
            double now0 = mc0.level.getGameTime();
            if (p.life > 0) map.put(p.id, new double[]{now0, now0 + p.life});
            else map.remove(p.id);
            if (p.shape == 24) AuraOutlineLayer.set(p.id, 3, p.life);
         }
         return;
      }
      if (p.shape == 27) { DarkSmoke.hellfire(p.id, p.life); return; }   // Hellfire Incarnate body: id = player, life = ticks
      if (p.shape == 21) { DarkSmoke.burning(p.id, p.life); return; }    // burning: id = entity, life = ticks
      if (p.shape == 22) { DarkSmoke.knuckles(p.id, p.life); return; }   // Flame Knuckles: id = player, life = ticks
      if (p.shape == 18) {  // Spirit Dive form (Yuno + Sylph): id = player, life = ticks left (0 = end)
         Minecraft mc0 = Minecraft.getInstance();
         if (mc0.level != null) {
            double now0 = mc0.level.getGameTime();
            double[] f = SPIRIT.get(p.id);
            if (p.life > 0) {
               if (f == null || f[1] < now0) SPIRIT.put(p.id, new double[]{now0, now0 + p.life});
               else f[1] = now0 + p.life;
            } else if (f != null) f[1] = Math.min(f[1], now0);
         }
         return;
      }
      if (p.shape == 17) {  // Mana Zone field: id = player, life = ticks left (0 = collapse), r = radius
         Minecraft mc0 = Minecraft.getInstance();
         if (mc0.level != null) {
            double now0 = mc0.level.getGameTime();
            double[] f = ZONES.get(p.id);
            if (p.life > 0) {
               if (f == null || f[1] < now0) ZONES.put(p.id, new double[]{now0, now0 + p.life, p.r, Double.NaN, p.palette});
               else f[1] = now0 + p.life;
            } else if (f != null) f[1] = Math.min(f[1], now0);
         }
         return;
      }
      if (p.shape == 16) {  // Black Blade: violet motes off the body. id = player, life = ticks left (0 = off)
         DarkSmoke.body(p.id, p.life);
         return;
      }
      if (p.shape == 15) {  // Black Cocoon: id = player, life = ticks left (0 = burst now), r = radius, sweep = ticks to close
         Minecraft mc0 = Minecraft.getInstance();
         if (mc0.level != null) {
            double now0 = mc0.level.getGameTime();
            double[] f = COCOON.get(p.id);
            if (p.life > 0) {
               if (f == null || f[1] < now0) COCOON.put(p.id, new double[]{now0, now0 + p.life, p.r, Math.max(1, p.sweep)});
               else f[1] = now0 + p.life;
            } else if (f != null) f[1] = Math.min(f[1], now0);
         }
         return;
      }
      if (p.shape == 14) {  // aura outline: id = player, palette = colour, life = ticks left (0 = off)
         AuraOutlineLayer.set(p.id, p.palette, p.life);
         return;
      }
      if (p.shape == 12) {  // impact frame: life = ticks
         Cutscene.impactFrame(p.life, p.palette == 1);
         return;
      }
      if (p.shape == 11) {  // Knight's Armor form: id = player, life = ticks left (0 = off)
         KnightArmorLayer.set(p.id, p.life);
         return;
      }
      if (p.shape == 9) {   // Black Meteorite cutscene: id = player, palette = phase (0 start, 1 impact), c = target
         Cutscene.handle(p.id, p.palette, p.c);
         return;
      }
      if (p.shape == 10) {  // screen shake: c = where, r = strength, life = ticks
         Cutscene.shake(p.c, p.r, p.life);
         return;
      }
      if (p.shape == 5) {   // mid-move pose: id = player, palette = pose index
         ArcaneAnimations.play(p.id, p.palette);
         return;
      }
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null) {
         if (p.shape == 3) {
            for (FxRenderer.Fx f : FX) {
               if (f.p.id == p.id && f.killAge < 0.0) {
                  f.killAge = (double)mc.level.getGameTime() - f.born;
               }
            }
         } else {
            FxRenderer.Fx fx = new FxRenderer.Fx();
            fx.p = p;
            fx.born = (double)mc.level.getGameTime();
            if (p.life <= 1) replaceTrack(fx);
            FX.add(fx);
         }
      }
   }

   /** True while the player with this entity id is in Devil Union (as far as this client knows). */
   public static boolean inDevilForm(int entityId, double now) {
      double[] f = DEVIL.get(entityId);
      return f != null && now >= f[0] && now <= f[1];
   }

   public static void localRing(Vec3 c, int pal, float r0, float r1, float h, int life) {
      FxPacket p = new FxPacket();
      p.shape = 0;
      p.palette = (byte)pal;
      p.life = life;
      p.c = c;
      p.d = new Vec3(1.0, 0.0, 0.0);
      p.u = new Vec3(0.0, 0.0, 1.0);
      p.r = r0;
      p.r2 = r1;
      p.a0 = -180.0F;
      p.a1 = 180.0F;
      p.h = h;
      spawn(p);
   }

   @SubscribeEvent
   public static void onTick(ClientTickEvent e) {
      if (e.phase == Phase.END) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.level == null) {
            FX.clear();
         } else {
            double now = (double)mc.level.getGameTime();
            Iterator<FxRenderer.Fx> it = FX.iterator();
            DarkSmoke.tick();
            for (java.util.Map.Entry<Integer, double[]> ze : ZONES.entrySet()) {
               net.minecraft.world.entity.Entity ent = mc.level.getEntity(ze.getKey());
               if (ent != null) zoneTick(mc, ent, ze.getValue(), now);
            }
            for (java.util.Map.Entry<Integer, double[]> se : SPIRIT.entrySet()) {   // wind streaming around Spirit Dive
               net.minecraft.world.entity.Entity ent = mc.level.getEntity(se.getKey());
               if (ent == null || now > se.getValue()[1]) continue;
               java.util.concurrent.ThreadLocalRandom rr = java.util.concurrent.ThreadLocalRandom.current();
               boolean self = ent == mc.player && mc.options.getCameraType().isFirstPerson();
               for (int k = 0; k < (self ? 1 : 2); k++) {
                  double a = rr.nextDouble(Math.PI * 2), y = rr.nextDouble(0.1, ent.getBbHeight());
                  Vec3 at = ent.position().add(Math.cos(a) * 0.7, y, Math.sin(a) * 0.7);
                  Vec3 tan = new Vec3(-Math.sin(a), 0.04, Math.cos(a)).scale(0.22);
                  DarkSmoke.streak(at, tan.add(ent.getDeltaMovement().scale(0.8)), 0.06F, rr.nextInt(8, 14));
               }
            }
            SPIRIT.values().removeIf(sf -> now > sf[1] + 20.0);
            ZONES.values().removeIf(zf -> now > zf[1] + ZONE_CLOSE + 2.0);
            ZONE_H.keySet().removeIf(id -> !ZONES.containsKey(id));
            for (java.util.Map.Entry<Integer, double[]> ce : COCOON.entrySet()) {   // darkness seeping off the cocoon
               double[] cf = ce.getValue();
               net.minecraft.world.entity.Entity ent = mc.level.getEntity(ce.getKey());
               if (ent == null || now > cf[1] || now < cf[0] + cf[3]) continue;
               java.util.concurrent.ThreadLocalRandom rr = java.util.concurrent.ThreadLocalRandom.current();
               Vec3 cc = ent.position().add(0.0, ent.getBbHeight() * 0.5, 0.0);
               for (int k = 0; k < 2; k++) {
                  Vec3 nrm = jit(rr, 1.0).normalize();
                  DarkSmoke.smoke(cc.add(nrm.scale(cf[2])), nrm.scale(0.03).add(0, 0.015, 0), 0.45F, rr.nextInt(14, 24));
               }
            }

            while (it.hasNext()) {
               FxRenderer.Fx f = it.next();
               if (f.p.palette == DARKNESS) emitDarkness(f, now - f.born);
               else if (f.p.palette == WIND) emitWind(f, now - f.born);
               else if (f.p.palette == MERCURY) emitMercury(f, now - f.born);
               else if (f.p.palette == FIRE || f.p.palette == BLUE_FIRE || f.p.palette == CRIMSON) { flamePal = f.p.palette; emitFire(f, now - f.born); flamePal = FIRE; }
               else if ((f.p.palette == LIGHTNING || f.p.palette == BLUE_LIGHTNING) && f.p.shape != 23) { boltPal = f.p.palette; emitLightning(f, now - f.born); }
               double life = f.killAge >= 0.0 ? f.killAge + 4.0 : (double)f.p.life;
               if (now - f.born > life + 1.0) {
                  it.remove();
               }
            }
            if (!PENDING.isEmpty()) { for (FxPacket q : PENDING) spawn(q); PENDING.clear(); }
            // Thunder God Mode / gloves: sparks off the feet when running, crackle around the hands
            java.util.concurrent.ThreadLocalRandom tr = java.util.concurrent.ThreadLocalRandom.current();
            for (java.util.Map.Entry<Integer, double[]> te : THUNDER.entrySet()) {
               net.minecraft.world.entity.Entity ent = mc.level.getEntity(te.getKey());
               if (ent == null || now > te.getValue()[1]) continue;
               Vec3 mv = ent.getDeltaMovement();
               boolean self = ent == mc.player && mc.options.getCameraType().isFirstPerson();
               if (ent.onGround() && mv.horizontalDistanceSqr() > 0.004) {
                  for (int k = 0; k < 2; k++) {
                     DarkSmoke.blueSpark(ent.position().add(tr.nextDouble(-0.3, 0.3), 0.05, tr.nextDouble(-0.3, 0.3)),
                        new Vec3(tr.nextDouble(-0.12, 0.12) - mv.x * 0.4, tr.nextDouble(0.08, 0.22), tr.nextDouble(-0.12, 0.12) - mv.z * 0.4), 0.035F, tr.nextInt(8, 14));
                  }
               }
               if (tr.nextInt(40) == 0) {   // now and then the sky answers: a bolt crashes down close by
                  Vec3 land = ent.position().add(tr.nextDouble(-4, 4), 0, tr.nextDouble(-4, 4));
                  boltPal = BLUE_LIGHTNING;
                  localBolt(land.add(tr.nextDouble(-2, 2), 18, tr.nextDouble(-2, 2)), land, 0.16F, 7, 3);
               }
               if (ent.onGround() && mv.horizontalDistanceSqr() > 0.004 && tr.nextInt(3) == 0) {   // arcs crawling along the ground from your feet
                  Vec3 f0 = ent.position().add(0, 0.05, 0);
                  double a = tr.nextDouble(Math.PI * 2);
                  boltPal = BLUE_LIGHTNING;
                  localBolt(f0, f0.add(Math.cos(a) * 1.6, 0.02, Math.sin(a) * 1.6), 0.03F, 3, 1);
               }
               if (!self && mv.horizontalDistanceSqr() > 0.01) {   // an afterimage of lightning behind you when you move fast
                  DarkSmoke.streakTinted(ent.position().add(tr.nextDouble(-0.3, 0.3), tr.nextDouble(0.2, ent.getBbHeight()), tr.nextDouble(-0.3, 0.3)),
                     mv.scale(-0.4), 0.05F, tr.nextInt(5, 9), 0.55F, 0.8F, 1.0F);
               }
            }
         }
      }
   }

   private static final int DARKNESS = 10;

   /**
    * Yami's darkness: every effect drawn in palette 10 sheds real smoke while it lives - black puffs curling off the
    * blade edge of slashes and crescents, along beams and trails, around walls and spikes - plus the odd violet mote.
    */
   private static void emitDarkness(FxRenderer.Fx f, double age) {
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

   private static final int WIND = 5, MERCURY = 11, FIRE = 2, LIGHTNING = 4, BLUE_FIRE = 12, BLUE_LIGHTNING = 13, CRIMSON = 14;
   private static int flamePal = 2, boltPal = 4;
   /** Packets made on this client while the effect list is being walked; spawned right after. */
   private static final List<FxPacket> PENDING = new ArrayList<>();
   /** Thunder God Mode: entity id -> {start, end}; gloves: entity id -> {start, end}. Fed by shapes 24 and 25. */
   private static final java.util.Map<Integer, double[]> THUNDER = new java.util.HashMap<>();
   private static final java.util.Map<Integer, double[]> GLOVES = new java.util.HashMap<>();

   private static void localBolt(Vec3 a, Vec3 b, float w, int life, int branches) {
      FxPacket q = new FxPacket();
      q.shape = 23; q.palette = (byte)boltPal; q.life = life; q.c = a; q.d = b.subtract(a); q.r = w; q.sweep = branches;
      q.id = java.util.concurrent.ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE);
      PENDING.add(q);
   }

   /** Every lightning-coloured effect crackles: small live arcs jump off beams, slashes, crescents and walls. */
   private static void emitLightning(FxRenderer.Fx f, double age) {
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
      localBolt(at, at.add(jit(r, 0.9)), 0.035F, 3, 0);
   }

   /**
    * Real lightning: a jagged channel made by midpoint displacement, with forks splitting off it, drawn as light
    * (a white-hot core in a coloured glow). It re-forks every two ticks and flickers - bright, dim, bright - the way a
    * real strike pulses, the first instant brightest and widest, then fades.
    */
   private static void boltFx(Matrix4f m, Vec3 cam, FxPacket p, double age, double life) {
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

   private static void boltBranch(Matrix4f m, Vec3 cam, Vec3 a, Vec3 b, float w, int pal, float al, java.util.Random rnd, int branches, int depth) {
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

   /** Thunder God Mode: lightning crackling over the whole body. Gloves: crackling around the fists. */
   private static void thunderBody(Matrix4f m, Vec3 cam, Player p, float pt, double now, boolean mode, boolean gloves) {
      if (gv == null) return;
      Vec3 base = new Vec3(Mth.lerp((double)pt, p.xo, p.getX()), Mth.lerp((double)pt, p.yo, p.getY()), Mth.lerp((double)pt, p.zo, p.getZ()));
      float h = p.getBbHeight();
      long frame = (long)(now * 1.5);
      java.util.Random rnd = new java.util.Random(p.getId() * 31L + frame * 977L);
      if (mode) {
         // the eyes burn with lightning, and lightning stands up off the head like hair
         double hy = Math.toRadians(Mth.rotLerp(pt, p.yHeadRotO, p.yHeadRot));
         Vec3 face = new Vec3(-Math.sin(hy), 0, Math.cos(hy)), side = new Vec3(Math.cos(hy), 0, Math.sin(hy));
         Vec3 eyes = base.add(0, p.getEyeHeight(), 0).add(face.scale(0.27));
         if (p != Minecraft.getInstance().player || !Minecraft.getInstance().options.getCameraType().isFirstPerson()) {
            for (int s = -1; s <= 1; s += 2) halo(m, cam, eyes.add(side.scale(0.11 * s)), 0.16, RIM[BLUE_LIGHTNING], 0.9F);
            for (int k = 0; k < 3; k++) {
               Vec3 a = base.add((rnd.nextDouble() - 0.5) * 0.4, h * 0.95, (rnd.nextDouble() - 0.5) * 0.4);
               boltBranch(m, cam, a, a.add((rnd.nextDouble() - 0.5) * 0.4, 0.4 + rnd.nextDouble() * 0.4, (rnd.nextDouble() - 0.5) * 0.4), 0.025F, BLUE_LIGHTNING, 0.85F, rnd, 0, 2);
            }
         }
         // a faint field of charge around the whole body
         halo(m, cam, base.add(0, h * 0.5, 0), 1.6, RIM[BLUE_LIGHTNING], 0.07F + 0.05F * (float)Math.abs(Math.sin(now * 2.3)));
         for (int k = 0; k < 5; k++) {
            Vec3 a = base.add((rnd.nextDouble() - 0.5) * 0.7, rnd.nextDouble() * h, (rnd.nextDouble() - 0.5) * 0.7);
            Vec3 b = a.add((rnd.nextDouble() - 0.5) * 0.9, (rnd.nextDouble() - 0.3) * 0.9, (rnd.nextDouble() - 0.5) * 0.9);
            boltBranch(m, cam, a, b, 0.03F, BLUE_LIGHTNING, 0.8F, rnd, 1, 1);
         }
         if (rnd.nextInt(5) == 0) {   // now and then a long arc to the ground
            Vec3 a = base.add(0, h * 0.5, 0);
            Vec3 b = base.add((rnd.nextDouble() - 0.5) * 3.0, 0.05, (rnd.nextDouble() - 0.5) * 3.0);
            boltBranch(m, cam, a, b, 0.04F, BLUE_LIGHTNING, 0.9F, rnd, 1, 1);
         }
      }
      if (gloves || mode) {
         double by = Math.toRadians((double)Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot));
         Vec3 fw = new Vec3(-Math.sin(by), 0.0, Math.cos(by)), lf = new Vec3(Math.cos(by), 0.0, Math.sin(by));
         for (int s = -1; s <= 1; s += 2) {
            Vec3 hand = base.add(0, h * 0.4, 0).add(lf.scale(0.38 * s)).add(fw.scale(0.08));
            for (int k = 0; k < 2; k++) {
               Vec3 b = hand.add((rnd.nextDouble() - 0.5) * 0.5, (rnd.nextDouble() - 0.5) * 0.5, (rnd.nextDouble() - 0.5) * 0.5);
               boltBranch(m, cam, hand, b, 0.025F, BLUE_LIGHTNING, 0.85F, rnd, 0, 2);
            }
         }
      }
   }

   /** Real fire: flames pour off fireballs, explosions burst into a ball of flame and smoke, fire walls and trails burn. */
   private static void emitFire(FxRenderer.Fx f, double age) {
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
   private static void smk(Vec3 at, Vec3 v, float size, int life) {
      if (flamePal == CRIMSON) DarkSmoke.emberSpark(at, v.add(0, 0.08, 0), 0.04F, Math.max(8, life / 2));
      else DarkSmoke.smoke(at, v, size, life);
   }

   private static void fl(Vec3 at, Vec3 v, float size, int life) {
      if (flamePal == BLUE_FIRE) DarkSmoke.blueFlame(at, v, size, life);
      else if (flamePal == CRIMSON) DarkSmoke.crimsonFlame(at, v, size, life);
      else DarkSmoke.flame(at, v, size, life);
   }

   private static Vec3 travelledPos(FxRenderer.Fx f, double age) { return travelled(f, age); }

   /**
    * A ball of fire: a white-hot core blooming out to orange and a deep red rim, its surface churning, with a soft
    * glowing halo. Explosions (h = 1) swell from r to r2 and darken as they burn out.
    */
   /**
    * A tornado: a funnel of wind spinning fast - spiral bands of bright, airy wind winding up it, wider at the top,
    * a faint body of air between them, and a ring of dust whirling at its foot. c = base, r = radius at the top,
    * h = height, a0 = how long it has been spinning (keeps the spin continuous between packets).
    */
   private static void tornadoFx(Matrix4f m, Vec3 cam, FxPacket p0, double age, double life) {
      if (gv == null) return;
      FxPacket p = new FxPacket();
      p.c = p0.c.add(p0.v.scale(mv(p0, age))); p.r = p0.r; p.h = p0.h; p.a0 = p0.a0;   // glide between server updates
      double R = p.r, H = p.h;
      double spin = (p.a0 + age) * 0.55;
      float[] rim = RIM[WIND], white = {0.95F, 1.0F, 0.97F};
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
               gline(m, cam, prev, q, (float)(0.08 + 0.1 * y / H), WIND, al * (b % 2 == 0 ? 1.0F : 0.6F));
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
            if (prev != null) gline(m, cam, prev, q, (float)(0.25 + 0.3 * y / H), WIND, 0.07F);
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
   private static void gustFx(Matrix4f m, Vec3 cam, FxPacket p, double age, double life) {
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

   /** A soft round glow facing the camera: bright in the middle, fading to nothing at radius R (additive). */
   private static void halo(Matrix4f m, Vec3 cam, Vec3 c, double R, float[] col, float a) {
      if (gv == null || a <= 0.01F) return;
      Vec3 view = c.subtract(cam);
      if (view.lengthSqr() < 1.0E-6) return;
      view = view.normalize();
      Vec3 u = view.cross(new Vec3(0, 1, 0));
      if (u.lengthSqr() < 1.0E-4) u = new Vec3(1, 0, 0);
      u = u.normalize();
      Vec3 v = view.cross(u).normalize();
      int N = 18;
      for (int i = 0; i < N; i++) {
         double a0 = Math.PI * 2 * i / N, a1 = Math.PI * 2 * (i + 1) / N;
         Vec3 e0 = c.add(u.scale(Math.cos(a0) * R)).add(v.scale(Math.sin(a0) * R));
         Vec3 e1 = c.add(u.scale(Math.cos(a1) * R)).add(v.scale(Math.sin(a1) * R));
         Vec3 m0 = c.add(e0.subtract(c).scale(0.35)), m1 = c.add(e1.subtract(c).scale(0.35));
         gquad(m, cam, c, m0, m1, c, col, a, a * 0.55F, a * 0.55F, a);
         gquad(m, cam, m0, e0, e1, m1, col, a * 0.55F, 0.0F, 0.0F, a * 0.55F);
      }
   }

   private static void fireballFx(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age, double life) {
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

   /** Mercury drips: small silver beads fall off fresh mercury trails and moving mercury spheres. */
   private static void emitMercury(FxRenderer.Fx f, double age) {
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

   /**
    * A liquid mercury sphere: mirror-chrome (the sky reflected above, the ground's dark below, a hard highlight),
    * its surface rippling, growing in over {@code sweep} ticks and moving with {@code v}.
    */
   private static void mercurySphere(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age, double life) {
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

   /** Yuno's wind: every effect in the wind palette throws off thin streaks of air that race along with it. */
   private static void emitWind(FxRenderer.Fx f, double age) {
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
            DarkSmoke.streak(at, new Vec3(-Math.sin(a), 0.15, Math.cos(a)).scale(0.6), 0.07F, r.nextInt(5, 9));
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

   private static Vec3 jit(java.util.concurrent.ThreadLocalRandom r, double s) {
      return new Vec3(r.nextDouble(-s, s), r.nextDouble(-s, s), r.nextDouble(-s, s));
   }

   /**
    * Energy palettes (fire, lightning, wind, blue flame) are made of light, not matter: while one of their slashes,
    * crescents, walls or beams is drawn, its "body" goes into the additive glow layer, tinted toward the palette's
    * colour - so wind is a bright, airy shimmer and fire a luminous blaze instead of dark see-through ribbons.
    */
   private static int lightPal = -1;

   private static boolean isLight(int pal) { return pal == 2 || pal == 4 || pal == 5 || pal == 6 || pal == 12 || pal == 13 || pal == 14; }

   private static void vtx(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 w, float[] col, float a) {
      if (lightPal >= 0 && gv != null) {
         float[] rc = RIM[lightPal];
         float k = lightPal == 5 ? 0.85F : 0.75F;
         float[] lc = {col[0] + (rc[0] - col[0]) * k, col[1] + (rc[1] - col[1]) * k, col[2] + (rc[2] - col[2]) * k};
         gvtx(m, cam, w, lc, a * (lightPal == 5 ? 0.28F : 0.6F));
         return;
      }
      if (nearFade) {
         double d = w.distanceTo(cam);
         a *= Mth.clamp((float)((d - NEAR_FADE_START) / NEAR_FADE_RANGE), NEAR_FADE_MIN, 1.0F) * FX_ALPHA;
      }
      vc.vertex(m, (float)(w.x - cam.x), (float)(w.y - cam.y), (float)(w.z - cam.z))
         .color(col[0], col[1], col[2], Mth.clamp(a, 0.0F, 1.0F))
         .uv(0.5F, 0.5F)
         .overlayCoords(OverlayTexture.NO_OVERLAY)
         .uv2(15728880)
         .normal(n, 0.0F, 1.0F, 0.0F)
         .endVertex();
   }

   private static void quad(
      VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 a, Vec3 b, Vec3 c, Vec3 d, float[] col, float aa, float ab, float ac, float ad
   ) {
      vtx(vc, m, n, cam, a, col, aa);
      vtx(vc, m, n, cam, b, col, ab);
      vtx(vc, m, n, cam, c, col, ac);
      vtx(vc, m, n, cam, d, col, ad);
   }

   // ------------------------------------------------------------------ additive glow layer
   private static void gvtx(Matrix4f m, Vec3 cam, Vec3 w, float[] col, float a) {
      if (gv == null) return;
      if (nearFade) {
         double d = w.distanceTo(cam);
         a *= Mth.clamp((float)((d - NEAR_FADE_START) / NEAR_FADE_RANGE), NEAR_FADE_MIN, 1.0F) * FX_ALPHA;
      }
      gv.vertex(m, (float)(w.x - cam.x), (float)(w.y - cam.y), (float)(w.z - cam.z)).color(col[0], col[1], col[2], Mth.clamp(a, 0.0F, 1.0F)).endVertex();
   }

   private static void gquad(Matrix4f m, Vec3 cam, Vec3 a, Vec3 b, Vec3 c, Vec3 d, float[] col, float aa, float ab, float ac, float ad) {
      gvtx(m, cam, a, col, aa);
      gvtx(m, cam, b, col, ab);
      gvtx(m, cam, c, col, ac);
      gvtx(m, cam, d, col, ad);
   }

   /** A camera-facing glowing line: white-hot core with a soft coloured falloff. */
   private static void gline(Matrix4f m, Vec3 cam, Vec3 p0, Vec3 p1, float w, int pal, float a) {
      Vec3 ax = p1.subtract(p0);
      if (ax.lengthSqr() < 1.0E-8) return;
      Vec3 side = ax.cross(p0.add(p1).scale(0.5).subtract(cam));
      if (side.lengthSqr() < 1.0E-8) return;
      side = side.normalize();
      float[] rim = RIM[pal], hot = hot(pal);
      Vec3 c0 = side.scale(w * 0.35), o0 = side.scale(w);
      gquad(m, cam, p0.add(c0), p1.add(c0), p1.subtract(c0), p0.subtract(c0), hot, a, a, a, a);
      gquad(m, cam, p0.add(c0), p1.add(c0), p1.add(o0), p0.add(o0), rim, a * 0.6F, a * 0.6F, 0.0F, 0.0F);
      gquad(m, cam, p0.subtract(c0), p1.subtract(c0), p1.subtract(o0), p0.subtract(o0), rim, a * 0.6F, a * 0.6F, 0.0F, 0.0F);
   }

   /**
    * Black Cocoon: a solid, shaded sphere of darkness around the entity. Near-black body, a violet fresnel rim that
    * glows at the silhouette, and three slow bands of light winding over it. It closes in over the first ticks and,
    * when it bursts, swells and fades in a few ticks. Seen from inside (first person) it is only a faint tint.
    */
   private static void cocoon(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, net.minecraft.world.entity.Entity e, float pt, double now, double[] f) {
      double age = now - f[0];
      if (age < 0.0) return;
      float R = (float)f[2];
      double grow = Mth.clamp(age / f[3], 0.0, 1.0);
      grow = 1.0 - Math.pow(1.0 - grow, 3.0);
      float a = 1.0F;
      double rr = R * grow;
      if (now > f[1]) {   // bursting
         double b = (now - f[1]) / 6.0;
         rr = R * (1.0 + b * 1.6);
         a = (float)Math.max(0.0, 1.0 - b);
      }
      if (a <= 0.01F || rr < 0.05) return;
      rr *= 1.0 + 0.015 * Math.sin(now * 0.6);   // breathing
      Vec3 c = e.getPosition(pt).add(0.0, e.getBbHeight() * 0.5, 0.0);
      boolean inside = cam.distanceTo(c) < rr * 1.02;
      float bodyA = (inside ? 0.16F : 0.94F) * a, glowA = (inside ? 0.22F : 0.85F) * a;
      int LAT = 14, LON = 24;
      Vec3[][] pts = new Vec3[LAT + 1][LON + 1];
      for (int i = 0; i <= LAT; i++) {
         double th = Math.PI * i / LAT;
         for (int j = 0; j <= LON; j++) {
            double ph = Math.PI * 2 * j / LON;
            pts[i][j] = new Vec3(Math.sin(th) * Math.cos(ph), Math.cos(th), Math.sin(th) * Math.sin(ph));
         }
      }
      float[] body = BODY[1], rim = RIM[1];
      for (int i = 0; i < LAT; i++) {
         for (int j = 0; j < LON; j++) {
            Vec3[] q = {pts[i][j], pts[i][j + 1], pts[i + 1][j + 1], pts[i + 1][j]};
            Vec3 qc = q[0].add(q[2]).scale(0.5);
            Vec3 toCam = cam.subtract(c.add(qc.scale(rr)));
            if (!inside && qc.dot(toCam) < 0.0) continue;   // only the side facing you (keeps the translucent shell clean)
            for (Vec3 nv : q) {
               Vec3 w = c.add(nv.scale(rr));
               Vec3 v = cam.subtract(w).normalize();
               float fres = (float)Math.pow(1.0 - Math.abs(nv.dot(v)), 3.0);
               float lit = (float)Math.max(0.0, nv.dot(LIGHT)) * 0.08F;
               float[] col = {body[0] + rim[0] * fres * 0.5F + lit, body[1] + rim[1] * fres * 0.5F + lit, body[2] + rim[2] * fres * 0.5F + lit};
               vtx(vc, m, n, cam, w, col, bodyA);
            }
            if (gv != null) {
               for (Vec3 nv : q) {
                  Vec3 w = c.add(nv.scale(rr * 1.01));
                  float fres = (float)Math.pow(1.0 - Math.abs(nv.dot(cam.subtract(w).normalize())), 2.5);
                  gvtx(m, cam, w, rim, fres * glowA * (inside ? 1.0F : 0.9F));
               }
            }
         }
      }
      // three slow bands of light winding over the shell
      for (int b = 0; b < 3; b++) {
         double tilt = b * Math.PI / 3 + now * 0.012, spin = now * 0.05 * (b % 2 == 0 ? 1 : -1) + b * 2.1;
         Vec3 ax = new Vec3(Math.cos(tilt), 0.6, Math.sin(tilt)).normalize();
         Vec3 u0 = ax.cross(new Vec3(0, 1, 0)).normalize(), u1 = ax.cross(u0).normalize();
         Vec3 prev = null;
         for (int k = 0; k <= 10; k++) {
            double t = spin + k * 0.16;
            Vec3 w = c.add(u0.scale(Math.cos(t) * rr * 1.015).add(u1.scale(Math.sin(t) * rr * 1.015)));
            if (prev != null) {
               boolean front = inside || w.subtract(c).dot(cam.subtract(w)) > 0.0;
               if (front) gline(m, cam, prev, w, 0.05F, 1, glowA * 0.7F * (float)Math.sin(Math.PI * k / 10.0));
            }
            prev = w;
         }
      }
   }

   // ------------------------------------------------------------------ Mana Zone (Yami)
   /** How far the zone has spread: grows out over ZONE_OPEN ticks, folds back in over ZONE_CLOSE after it ends. */
   private static double zoneReach(double[] f, double now) {
      double age = now - f[0];
      if (age < 0.0) return 0.0;
      double open = Mth.clamp(age / ZONE_OPEN, 0.0, 1.0);
      open = 1.0 - Math.pow(1.0 - open, 3.0);
      if (now > f[1]) open *= 1.0 - Mth.clamp((now - f[1]) / ZONE_CLOSE, 0.0, 1.0);
      return f[2] * open;
   }

   /** Ground under the zone's owner: their feet when standing, else the first block below (kept while airborne). */
   private static double zoneGround(net.minecraft.world.entity.Entity e, double[] f) {
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
   private static boolean zoneFire(double[] f) { return f.length > 4 && ((int)f[4] == FIRE || (int)f[4] == CRIMSON); }

   /** One puff of the zone: darkness for Yami, a lick of heat-flame for Mereoleona. */
   private static void zPuff(double[] f, Vec3 at, Vec3 v, float size, int life) {
      if (zoneFire(f)) DarkSmoke.crimsonFlame(at, v.add(0, 0.02, 0), size * 0.5F, Math.max(8, life / 2));
      else DarkSmoke.smoke(at, v, size, life);
   }

   private static void zoneTick(Minecraft mc, net.minecraft.world.entity.Entity e, double[] f, double now) {
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
   private static void zoneField(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, net.minecraft.world.entity.Entity e, float pt, double now, double[] f) {
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
            if (Math.max(Math.max(y00, y01), Math.max(y11, y10)) - Math.min(Math.min(y00, y01), Math.min(y11, y10)) > 1.6) continue;   // no sheets over cliffs
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

   private static Vec3 dome(double a, double el) {
      return new Vec3(Math.cos(a) * Math.cos(el), Math.sin(el), Math.sin(a) * Math.cos(el));
   }

   /** Opacity of the dark sea: deep near you, thinning toward the edge, rippling in turning spiral swirls. */
   private static float seaA(double t, double a, double now) {
      double swirl = 0.5 + 0.5 * Math.sin(a * 3.0 + t * 9.0 - now * 0.06);
      double base = 0.62 - 0.32 * t;
      return (float)Mth.clamp(base * (0.8 + 0.3 * swirl), 0.0, 0.85) * (t >= 0.999 ? 0.4F : 1.0F);
   }

   private static float vein(double t, double a, double now) {
      double s = Math.sin(a * 3.0 + t * 9.0 - now * 0.06);
      return (float)(Math.max(0.0, s - 0.85) / 0.15 * 0.18 * (0.4 + 0.6 * t));
   }

   private static float[] hot(int pal) {
      float[] r = RIM[pal];
      float w = isFlame(pal) ? 0.3F : 0.55F;   // black-flame palettes keep a red-orange core instead of white
      return new float[]{r[0] + (1.0F - r[0]) * w, r[1] + (1.0F - r[1]) * w, r[2] + (1.0F - r[2]) * w};
   }

   // ------------------------------------------------------------------ black flame (Asta's anti magic look)
   private static double fxTime;
   private static final float[] SMOKE = new float[]{0.015F, 0.0F, 0.008F};

   /** Asta's palettes (slayer crimson, dweller dark, devil ember) burn with black flame and a red underglow. */
   private static boolean isFlame(int pal) {
      return pal == 0 || pal == 1 || pal == 9;
   }

   /**
    * One licking tongue of black flame on the edge a-b, leaning along {@code out} and rising: a black smoke tongue in
    * front of a larger red glow tongue, flickering over time.
    */
   private static void flameTongue(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 a, Vec3 b, Vec3 out, double height, int pal, float al, int seed) {
      if (al <= 0.01F || height <= 0.0) return;
      Vec3 mid = a.add(b).scale(0.5), along = b.subtract(a);
      double t = fxTime;
      double hg = height * (0.5 + 0.5 * Math.abs(Math.sin(t * 0.45 + seed * 1.71)) + 0.15 * Math.sin(t * 1.3 + seed * 0.37));
      Vec3 tip = mid.add(out.scale(hg)).add(0.0, hg * 0.45, 0.0).add(along.scale(0.35 * Math.sin(t * 0.33 + seed * 2.3)));
      Vec3 tipG = mid.add(tip.subtract(mid).scale(1.35));
      float[] rim = RIM[pal];
      gquad(m, cam, a.subtract(along.scale(0.2)), b.add(along.scale(0.2)), tipG, tipG, rim, al * 0.6F, al * 0.6F, 0.0F, 0.0F);
      quad(vc, m, n, cam, a, b, tip, tip, SMOKE, al * 0.85F, al * 0.85F, 0.0F, 0.0F);
   }

   @SubscribeEvent
   public static void onRender(RenderLevelStageEvent e) {
      if (e.getStage() == Stage.AFTER_PARTICLES) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.level == null) {
            BOOKS.clear();
            AURAS.clear();
         } else {
            float pt = e.getPartialTick();
            PoseStack ps = e.getPoseStack();
            Vec3 cam = e.getCamera().getPosition();
            BufferSource buf = mc.renderBuffers().bufferSource();
            RenderType rt = RenderType.entityTranslucentEmissive(WHITE);
            VertexConsumer vc = buf.getBuffer(rt);
            gv = GLOW_SRC.getBuffer(GlowRenderType.GLOW);
            double now = (double)((float)mc.level.getGameTime() + pt);
            Matrix4f m = ps.last().pose();
            Matrix3f n = ps.last().normal();
            nearFade = true;
            bookTime = now;
            fxTime = now;

            for (FxRenderer.Fx f : FX) {
               double age = now - f.born;
               double life = f.killAge >= 0.0 ? f.killAge + 4.0 : (double)f.p.life;
               // per-tick copies (HOLD solids, moving spheres, the tornado) get a quarter tick of slack so a late
               // packet never leaves a one-frame gap; they move along their velocity, so the overlap lines up
               boolean perTick = f.p.life <= 1 && f.killAge < 0.0;
               if (!(age < 0.0) && !(age > life + (perTick ? 1.5 : 0.0))) {
                  float env = (float)(smooth(age / 1.5) * smooth((life - age) / Math.max(2.0, life * 0.45)));   // eased in and out, no hard edges
                  int sh = f.p.shape;
                  lightPal = (sh == 0 || sh == 1 || sh == 2 || sh == 13) && isLight(f.p.palette) ? f.p.palette : -1;
                  switch (f.p.shape) {
                     case 0:
                        wall(vc, m, n, cam, f.p, age, env);
                        break;
                     case 1:
                        crescentFx(vc, m, n, cam, f.p, age, env);
                        break;
                     case 2:
                        beam(vc, m, n, cam, f.p.c, f.p.c.add(f.p.d), f.p.r * (1.0F - 0.55F * (float)(age / life)), f.p.palette, env, f.p.h > 0.5F ? 0.0 : 0.35);
                        break;
                     case 13:
                        slashFx(vc, m, n, cam, f.p, age, life);
                        break;
                     case 19:
                        mercurySphere(vc, m, n, cam, f.p, age, life);
                        break;
                     case 20:
                        fireballFx(vc, m, n, cam, f.p, age, life);
                        break;
                     case 23:
                        boltFx(m, cam, f.p, age, life);
                        break;
                     case 7:
                        boxFx(vc, m, n, cam, f, age, life);
                        break;
                     case 8:
                        spikeFx(vc, m, n, cam, f, age, life);
                        break;
                     case 28:
                        tornadoFx(m, cam, f.p, age, life);
                        break;
                     case 29:
                        gustFx(m, cam, f.p, age, life);
                  }
                  lightPal = -1;
               }
            }

            float pulse = 0.75F + 0.25F * (float)Math.sin(now * 0.35);

            for (Player p : mc.level.players()) {
               if (!p.isSpectator() && !p.isInvisible() && (p != mc.player || !mc.options.getCameraType().isFirstPerson())) {
                  heldAura(vc, m, n, cam, p, pt, pulse, p.getMainHandItem().getItem(), true);
                  heldAura(vc, m, n, cam, p, pt, pulse, p.getOffhandItem().getItem(), false);
                  double[] form = DEVIL.get(p.getId());
                  if (form != null) devilForm(vc, m, n, cam, p, pt, now, form);
                  double[] sp = SPIRIT.get(p.getId());
                  if (sp != null) spiritForm(vc, m, n, cam, p, pt, now, sp);
                  double[] th = THUNDER.get(p.getId()), gl = GLOVES.get(p.getId());
                  boolean thOn = th != null && now <= th[1], glOn = gl != null && now <= gl[1];
                  if (thOn || glOn) thunderBody(m, cam, p, pt, now, thOn, glOn);
               }
            }
            DEVIL.values().removeIf(f -> now > f[1] + 20.0);
            nearFade = false;
            for (java.util.Map.Entry<Integer, double[]> ce : COCOON.entrySet()) {
               net.minecraft.world.entity.Entity ent = mc.level.getEntity(ce.getKey());
               if (ent != null) cocoon(vc, m, n, cam, ent, pt, now, ce.getValue());
            }
            COCOON.values().removeIf(f -> now > f[1] + 6.0);
            for (java.util.Map.Entry<Integer, double[]> ze : ZONES.entrySet()) {
               net.minecraft.world.entity.Entity ent = mc.level.getEntity(ze.getKey());
               if (ent != null) zoneField(vc, m, n, cam, ent, pt, now, ze.getValue());
            }
            nearFade = true;

            for (FxRenderer.AuraDraw a : AURAS) {
               beam(vc, m, n, cam, a.a, a.b, a.width, a.pal, a.alpha * pulse, 0.5);
            }

            AURAS.clear();
            nearFade = false;
            // open grimoires: black-flame aura (smoke in the translucent pass, red underglow in the glow pass)
            for (FxRenderer.BookDraw b : BOOKS) {
               book(ps, vc, cam, b);
            }
            BOOKS.clear();
            buf.endBatch(rt);
            GLOW_SRC.endBatch(GlowRenderType.GLOW);
            gv = null;
            DarkSmoke.render(ps, e.getCamera(), buf, pt);
            if (!PAGES.isEmpty()) {   // turning pages, drawn with the book's own page art (front = right page, back = left page)
               for (int side = 0; side < 2; side++) {
                  RenderType prt = RenderType.entityCutout(side == 0 ? PAGE_R : PAGE_L);
                  VertexConsumer pv = buf.getBuffer(prt);
                  for (PageDraw pd : PAGES) pageQuad(pv, pd, side == 1);
                  buf.endBatch(prt);
               }
               PAGES.clear();
            }
         }
      }
   }

   /**
    * Devil Union form - an original design drawn over the player's own skin.
    *
    *  WINGS   five blades per side, each bent in three joints. The flap is a wave that travels from shoulder to tip
    *          (tips lag behind), and the wings react to how you move: a slow beat standing still, swept back when
    *          running, hard flaps in the air, spread wide to glide when falling, folded in when crouching.
    *          They burst open with an overshoot when the form starts; ember trails follow the tips and a glow pulse
    *          runs along every blade.
    *  CROWN   black flame tongues above the head, with three taller ones that pulse.
    *  MANTLE  three shadow streams down the back that ripple and get blown back as you move.
    *  SIGIL   a slowly turning ember circle and star under your feet while you stand on the ground.
    *  BURST   an ember shockwave the moment the form begins.
    * Everything fades out over 20 ticks when the union ends.
    */
   private static void devilForm(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Player p, float pt, double now, double[] f) {
      double age = now - f[0];
      float fade = (float)Mth.clamp((f[1] + 20.0 - now) / 20.0, 0.0, 1.0);
      // unfold with an overshoot (ease-out-back)
      double g = Mth.clamp(age / 14.0, 0.0, 1.0) - 1.0;
      float grow = (float)(1.0 + 2.70158 * g * g * g + 1.70158 * g * g);
      float a = fade * Mth.clamp(0.35F + 0.65F * (float)Mth.clamp(age / 14.0, 0.0, 1.0), 0.0F, 1.0F);
      if (a <= 0.01F) return;

      double by = Math.toRadians((double)Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot));
      Vec3 fw = new Vec3(-Math.sin(by), 0.0, Math.cos(by));
      Vec3 lf = new Vec3(Math.cos(by), 0.0, Math.sin(by));
      Vec3 base = new Vec3(Mth.lerp((double)pt, p.xo, p.getX()), Mth.lerp((double)pt, p.yo, p.getY()), Mth.lerp((double)pt, p.zo, p.getZ()));
      float h = p.getBbHeight();
      float[] dark = BODY[EMBER];

      // ---- how the body is moving drives the wings
      double speed = Math.sqrt((p.getX() - p.xo) * (p.getX() - p.xo) + (p.getZ() - p.zo) * (p.getZ() - p.zo));
      double vy = p.getY() - p.yo;
      boolean air = !p.onGround();
      boolean falling = air && vy < -0.08;
      boolean crouch = p.isCrouching();
      double opening = Mth.clamp(1.0 - age / 18.0, 0.0, 1.0);               // a few big beats as the wings burst open
      double freq = air && !falling ? 0.34 : (speed > 0.05 ? 0.18 : 0.09);
      double amp = falling ? 0.06 : (air ? 0.5 : (speed > 0.05 ? 0.2 : 0.11)) + opening * 0.45;
      double sweep = Mth.clamp(speed * 3.0, 0.0, 0.7);
      double fold = crouch ? 0.65 : 0.0;
      double glide = falling ? 1.0 : 0.0;

      Vec3 back = base.add(0.0, h * 0.76, 0.0).subtract(fw.scale(0.2));
      for (int side = -1; side <= 1; side += 2) {
         Vec3 root = back.add(lf.scale(0.16 * side));
         Vec3[] w = demonWing(root, fw, lf, side, now, grow, freq, amp, sweep, fold, glide);
         Vec3[] wPrev = demonWing(root, fw, lf, side, now - 2.5, grow, freq, amp, sweep, fold, glide);
         drawDemonWing(vc, m, n, cam, w, wPrev, a, now, dark);
      }

      // ---- crown of black flame
      Vec3 crown = base.add(0.0, h + 0.02, 0.0);
      for (int k = 0; k < 10; k++) {
         boolean tallOne = k % 3 == 0 && k < 9;
         double ang = Math.PI * 2.0 * k / 10.0 + now * 0.025;
         double r0 = 0.26, span = tallOne ? 0.14 : 0.2;
         Vec3 b0 = crown.add(Math.cos(ang - span) * r0, 0.0, Math.sin(ang - span) * r0);
         Vec3 b1 = crown.add(Math.cos(ang + span) * r0, 0.0, Math.sin(ang + span) * r0);
         double tall = ((tallOne ? 0.42 : 0.22) + 0.12 * Math.sin(now * (tallOne ? 0.5 : 0.4) + k * 1.7)) * Math.min(1.0F, grow);
         Vec3 tip = crown.add(Math.cos(ang) * (tallOne ? 0.3 : 0.34), tall, Math.sin(ang) * (tallOne ? 0.3 : 0.34))
                         .subtract(fw.scale(sweep * 0.25 * tall));                // flames lean back when you run
         quad(vc, m, n, cam, b0, b1, tip, tip, dark, a * 0.9F, a * 0.9F, 0.0F, 0.0F);
         beam(vc, m, n, cam, crown.add(Math.cos(ang) * r0, 0.0, Math.sin(ang) * r0), tip, tallOne ? 0.045F : 0.03F, EMBER, a * 0.75F, 0.5);
      }

      // ---- mantle of shadow: rippling, blown back by movement
      for (int s = -1; s <= 1; s++) {
         Vec3 start = back.add(lf.scale(0.12 * s));
         Vec3 end = base.add(0.0, 0.2, 0.0).subtract(fw.scale(0.42 + 0.06 * Math.abs(s) + speed * 2.2)).add(lf.scale(0.28 * s)).add(0.0, speed * 1.2, 0.0);
         Vec3 prev = start;
         for (int k = 1; k <= 3; k++) {
            double t = k / 3.0 * Math.min(1.0F, grow);
            Vec3 q = start.add(end.subtract(start).scale(t)).add(lf.scale(Math.sin(now * 0.2 + k * 1.1 + s * 2.0) * 0.07 * k));
            beam(vc, m, n, cam, prev, q, 0.2F - k * 0.03F, EMBER, a * 0.5F, 0.7);
            prev = q;
         }
      }

   }

   /**
    * Spirit Dive (Yuno merged with Sylph): four wings of wind on the back - two large upper wings and two smaller
    * lower ones - made of long, glowing, half-transparent feathers of air that flutter, plus two rings of wind turning
    * around the body.
    */
   private static void spiritForm(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Player p, float pt, double now, double[] f) {
      double age = now - f[0];
      float fade = (float)Mth.clamp((f[1] + 12.0 - now) / 12.0, 0.0, 1.0);
      float grow = (float)(1.0 - Math.pow(1.0 - Mth.clamp(age / 12.0, 0.0, 1.0), 3.0));
      float a = fade * grow;
      if (a <= 0.01F) return;
      double by = Math.toRadians((double)Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot));
      Vec3 fw = new Vec3(-Math.sin(by), 0.0, Math.cos(by));
      Vec3 lf = new Vec3(Math.cos(by), 0.0, Math.sin(by));
      Vec3 base = new Vec3(Mth.lerp((double)pt, p.xo, p.getX()), Mth.lerp((double)pt, p.yo, p.getY()), Mth.lerp((double)pt, p.zo, p.getZ()));
      float h = p.getBbHeight();
      boolean air = !p.onGround();
      double flap = Math.sin(now * (air ? 0.45 : 0.18)) * (air ? 0.35 : 0.12);
      float[] rim = RIM[WIND], white = {0.92F, 1.0F, 0.96F};
      Vec3 back = base.add(0.0, h * 0.74, 0.0).subtract(fw.scale(0.22));
      Vec3 up = new Vec3(0, 1, 0);
      // {root height offset, base angle up from horizontal (deg), length, feathers}
      double[][] wings = {{0.08, 38, 1.9, 6}, {-0.35, -22, 1.1, 4}};
      for (double[] wg : wings) {
         for (int side = -1; side <= 1; side += 2) {
            Vec3 root = back.add(0, wg[0], 0).add(lf.scale(0.12 * side));
            for (int k = 0; k < (int)wg[3]; k++) {
               double spread = Math.toRadians(wg[1] - k * (wg[1] > 0 ? 14 : 12) + Math.toDegrees(flap) * (1.0 - k * 0.1));
               double sweepBack = 0.35 + k * 0.08;
               Vec3 dir = lf.scale(side * Math.cos(spread)).add(up.scale(Math.sin(spread))).subtract(fw.scale(sweepBack)).normalize();
               double len = wg[2] * (1.0 - k * 0.09) * grow;
               Vec3 tip = root.add(dir.scale(len));
               Vec3 wdt = dir.cross(fw).normalize().scale(0.09 + 0.02 * (wg[3] - k));
               Vec3 midp = root.add(dir.scale(len * 0.55));
               quad(vc, m, n, cam, root, midp.add(wdt), tip, midp.subtract(wdt), white, a * 0.32F, a * 0.22F, 0.0F, a * 0.22F);
               gquad(m, cam, root, midp.add(wdt), tip, midp.subtract(wdt), rim, a * 0.45F, a * 0.3F, a * 0.05F, a * 0.3F);
               gline(m, cam, root, tip, 0.025F, WIND, a * 0.6F);
            }
         }
      }
      // the crown of wind: a thin circlet of light around the brow with five tall feathers of air rising from it,
      // the middle one tallest, all swaying slightly
      double hyr = Math.toRadians(Mth.rotLerp(pt, p.yHeadRotO, p.yHeadRot));
      Vec3 hf = new Vec3(-Math.sin(hyr), 0, Math.cos(hyr)), hl = new Vec3(Math.cos(hyr), 0, Math.sin(hyr));
      Vec3 crown = base.add(0, h * 0.97, 0);
      Vec3 prevC = null;
      for (int k = 0; k <= 16; k++) {
         double t = Math.PI * 2 * k / 16;
         Vec3 q = crown.add(hf.scale(Math.cos(t) * 0.3)).add(hl.scale(Math.sin(t) * 0.3));
         if (prevC != null) gline(m, cam, prevC, q, 0.025F, WIND, a * 0.85F);
         prevC = q;
      }
      for (int k = -2; k <= 2; k++) {
         double t = k * 0.42;   // spread across the front of the circlet
         Vec3 root = crown.add(hf.scale(Math.cos(t) * 0.3)).add(hl.scale(Math.sin(t) * 0.3));
         double tall = (0.42 - Math.abs(k) * 0.09) * grow;
         double sway = Math.sin(now * 0.25 + k) * 0.04;
         Vec3 tip = root.add(0, tall, 0).add(hf.scale(-0.05 + sway)).add(hl.scale(Math.sin(t) * 0.08));
         Vec3 w = hl.scale(0.045);
         Vec3 midp = root.add(tip.subtract(root).scale(0.5));
         quad(vc, m, n, cam, root, midp.add(w), tip, midp.subtract(w), white, a * 0.5F, a * 0.35F, 0.0F, a * 0.35F);
         gquad(m, cam, root, midp.add(w), tip, midp.subtract(w), rim, a * 0.6F, a * 0.4F, a * 0.1F, a * 0.4F);
         gline(m, cam, root, tip, 0.018F, WIND, a * 0.8F);
      }
      halo(m, cam, crown.add(hf.scale(0.3)).add(0, 0.05, 0), 0.12, white, a * 0.8F);   // a jewel of light at the front
      // two rings of wind turning around the body
      for (int ring = 0; ring < 2; ring++) {
         double tilt = ring == 0 ? 0.25 : -0.35, spin = now * (ring == 0 ? 0.22 : -0.17);
         double yc = h * (ring == 0 ? 0.35 : 0.7), R = 0.75 + ring * 0.12;
         Vec3 prev = null;
         for (int k = 0; k <= 14; k++) {
            double t = spin + k * 0.32;
            Vec3 q = base.add(Math.cos(t) * R, yc + Math.sin(t) * R * tilt, Math.sin(t) * R);
            if (prev != null) gline(m, cam, prev, q, 0.03F, WIND, a * 0.55F * (float)Math.sin(Math.PI * k / 14.0));
            prev = q;
         }
      }
   }

   // indexes into the demon wing skeleton
   private static final int WS = 0, WE = 1, WW = 2, WF = 3, WC0 = 11, WC1 = 12, WWAIST = 13;

   /**
    * Skeleton of one bat-like demon wing at time {@code t}: shoulder, elbow, wrist (with a hooked claw), four finger
    * bones of two joints each, and the waist anchor of the membrane. The arm swings from the shoulder, the elbow flexes a
    * beat later and the fingers spread on the downstroke / close on the upstroke; {@code fold} tucks the fingers down
    * along the arm, {@code glide} spreads everything flat and wide.
    * Result: [S, E, W, M0, T0, M1, T1, M2, T2, M3, T3, claw0, claw1, waist].
    */
   private static Vec3[] demonWing(Vec3 root, Vec3 fw, Vec3 lf, int side, double t, float grow,
                                   double freq, double amp, double sweep, double fold, double glide) {
      Vec3 out = lf.scale(side), up = new Vec3(0.0, 1.0, 0.0), back = fw.scale(-1.0);
      double ph = freq * t;
      double beat = Math.sin(ph);
      double elev = 1.12 * (1.0 - glide) + 0.55 * glide + amp * beat;
      elev = elev * (1.0 - fold) + 1.35 * fold;
      double tilt = 0.3 + sweep + fold * 0.6;
      double s = Math.max(0.05F, grow) * 1.25;
      Vec3[] r = new Vec3[14];
      r[WS] = root;
      r[WE] = root.add(wdir(out, up, back, elev, tilt).scale(0.55 * s));
      double bend = (0.6 + 0.25 * Math.sin(ph - 0.6)) * (1.0 - glide) + 0.35 * glide;
      bend = bend * (1.0 - fold) + 2.3 * fold;
      double fore = elev - bend;
      r[WW] = r[WE].add(wdir(out, up, back, fore, tilt + 0.1).scale(0.75 * s));
      double spread = (0.9 + 0.12 * Math.sin(ph + 1.2)) * (1.0 - glide) + 1.1 * glide;
      spread = spread * (1.0 - fold) + 0.25 * fold;
      double[] design = {0.35, -0.3, -0.85, -1.3};   // finger angles when fully open: outward-up ... down
      double[] lens = {1.1, 1.5, 1.45, 1.15};
      for (int j = 0; j < 4; j++) {
         double a1 = fore + (design[j] - 0.4) * spread;
         double len = lens[j] * s * (0.6 + 0.4 * (1.0 - fold));
         double b = tilt + 0.2 + j * 0.1;
         Vec3 mid = r[WW].add(wdir(out, up, back, a1, b).scale(len * 0.55));
         double curl = -0.15 + amp * 0.5 * Math.sin(ph - 0.9 - j * 0.25);   // finger tips lag the beat
         r[WF + j * 2] = mid;
         r[WF + j * 2 + 1] = mid.add(wdir(out, up, back, a1 + curl, b + 0.1).scale(len * 0.45));
      }
      r[WC0] = r[WW].add(wdir(out, up, back, fore + 1.3, tilt - 0.2).scale(0.18 * s));
      r[WC1] = r[WC0].add(wdir(out, up, back, fore + 2.3, tilt - 0.4).scale(0.1 * s));
      r[WWAIST] = root.add(0.0, -0.55, 0.0).add(out.scale(0.1));
      return r;
   }

   private static Vec3 wdir(Vec3 out, Vec3 up, Vec3 back, double ang, double tilt) {
      return out.scale(Math.cos(ang)).add(up.scale(Math.sin(ang))).add(back.scale(tilt)).normalize();
   }

   private static Vec3 sag(Vec3 a, Vec3 b, Vec3 toward, double k) {
      Vec3 midp = a.add(b).scale(0.5);
      return midp.add(toward.subtract(midp).scale(k));
   }

   /** Leathery membrane with a scalloped trailing edge, faint veins, glowing bones, a bone claw and tip trails. */
   private static void drawDemonWing(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3[] w, Vec3[] wPrev, float a, double now, float[] dark) {
      Vec3 S = w[WS], E = w[WE], W = w[WW], waist = w[WWAIST];
      float in = a * 0.62F, edge = a * 0.38F;
      // membrane: leading edge (shoulder-elbow-wrist-first finger)
      quad(vc, m, n, cam, S, E, W, w[WF], dark, in, in, in, edge);
      // membrane between fingers, sagging inward between the tips
      for (int j = 0; j < 3; j++) {
         Vec3 m0 = w[WF + j * 2], t0 = w[WF + j * 2 + 1], m1 = w[WF + j * 2 + 2], t1 = w[WF + j * 2 + 3];
         Vec3 sg = sag(t0, t1, W, 0.22);
         quad(vc, m, n, cam, W, m0, m1, W, dark, in, in, in, in);
         quad(vc, m, n, cam, m0, t0, sg, m1, dark, in, edge, edge, in);
         quad(vc, m, n, cam, sg, t1, m1, sg, dark, edge, edge, in, edge);
         beam(vc, m, n, cam, W, sg, 0.02F, EMBER, a * 0.25F, 0.5);   // faint vein
      }
      // membrane from the last finger down to the waist
      Vec3 m3 = w[WF + 6], t3 = w[WF + 7];
      Vec3 sgw = sag(t3, waist, W, 0.15);
      quad(vc, m, n, cam, m3, t3, sgw, waist, dark, in, edge, edge, in);
      quad(vc, m, n, cam, W, m3, waist, S, dark, in, in, in, in);
      quad(vc, m, n, cam, S, E, W, S, dark, in, in, in, in);
      // glowing trailing edge
      Vec3 prev = w[WF + 1];
      for (int j = 0; j < 3; j++) {
         Vec3 t0 = w[WF + j * 2 + 1], t1 = w[WF + j * 2 + 3], sg = sag(t0, t1, W, 0.22);
         beam(vc, m, n, cam, prev, sg, 0.035F, EMBER, a * 0.6F, 0.2);
         beam(vc, m, n, cam, sg, t1, 0.035F, EMBER, a * 0.6F, 0.2);
         prev = t1;
      }
      beam(vc, m, n, cam, t3, sgw, 0.035F, EMBER, a * 0.6F, 0.2);
      beam(vc, m, n, cam, sgw, waist, 0.035F, EMBER, a * 0.5F, 0.2);
      // bones: arm, then fingers, with a glow pulse running outward
      float pulse = 1.0F + 0.35F * (float)Math.sin(now * 0.3);
      beam(vc, m, n, cam, S, E, 0.13F * pulse, EMBER, a * 0.95F, 0.4);
      beam(vc, m, n, cam, E, W, 0.11F * pulse, EMBER, a * 0.95F, 0.4);
      for (int j = 0; j < 4; j++) {
         float fp = 1.0F + 0.4F * (float)Math.sin(now * 0.3 - 1.0 - j * 0.4);
         beam(vc, m, n, cam, W, w[WF + j * 2], 0.07F * fp, EMBER, a * 0.9F, 0.5);
         beam(vc, m, n, cam, w[WF + j * 2], w[WF + j * 2 + 1], 0.05F * fp, EMBER, a * 0.85F, 0.6);
      }
      // bone-white hooked claw at the wrist
      beam(vc, m, n, cam, W, w[WC0], 0.05F, 6, a, 0.3);
      beam(vc, m, n, cam, w[WC0], w[WC1], 0.035F, 6, a, 0.8);
      // ember trails off the two longest finger tips
      for (int j = 1; j <= 2; j++) beam(vc, m, n, cam, wPrev[WF + j * 2 + 1], w[WF + j * 2 + 1], 0.04F, EMBER, a * 0.4F, 0.8);
   }

   private static void heldAura(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Player p, float pt, float pulse, Item item, boolean main) {
      int pal;
      float len;
      float width;
      if (item == AddonItemRegistry.DEMON_SLAYER_SWORD.get()) {
         pal = 0;
         len = 3.0F;
         width = 0.34F;
      } else {
         if (item != AddonItemRegistry.DEMON_DWELLER_SWORD.get()) {
            return;
         }

         pal = 1;
         len = 2.1F;
         width = 0.24F;
      }

      double by = Math.toRadians((double)Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot));
      Vec3 fw = new Vec3(-Math.sin(by), 0.0, Math.cos(by));
      Vec3 lf = new Vec3(Math.cos(by), 0.0, Math.sin(by));
      boolean rightHand = p.getMainArm() == HumanoidArm.RIGHT == main;
      Vec3 base = new Vec3(
         Mth.lerp((double)pt, p.xo, p.getX()),
         Mth.lerp((double)pt, p.yo, p.getY()),
         Mth.lerp((double)pt, p.zo, p.getZ())
      );
      Vec3 hand = base.add(0.0, (double)p.getEyeHeight() * 0.62, 0.0).add(fw.scale(0.35)).add(lf.scale(rightHand ? -0.42 : 0.42));
      boolean dash = p.isAutoSpinAttack();
      if (!dash) return;   // no glow stuck on the sword at rest - only the dash streak
      Vec3 dir = dash ? p.getViewVector(pt).normalize() : fw.scale(0.5).add(0.0, 0.85, 0.0).normalize();
      // idle: a faint edge-glow along the blade; dashing: a stronger streak
      float a = (dash ? 0.9F : 0.4F) * pulse;
      beam(vc, m, n, cam, hand.subtract(dir.scale(0.25)), hand.add(dir.scale((double)len)), width * (dash ? 1.4F : 0.75F), pal, a, 0.5);
   }

   // ------------------------------------------------------------------ 3D solids (steel plates, bars, spikes...)
   private static double backOut(double x) {
      x = Mth.clamp(x, 0.0, 1.0) - 1.0;
      return 1.0 + 2.2 * x * x * x + 1.2 * x * x;
   }

   private static double smooth(double x) {
      x = Mth.clamp(x, 0.0, 1.0);
      return x * x * (3.0 - 2.0 * x);
   }

   /** How far a moving solid has travelled: stops when its move time is up, or where it was killed. */
   /**
    * Things re-sent every tick (projectiles, orbiting weapons, the tornado...) are one object to the eye: a new copy
    * replaces the copy it continues (same kind, same size, right where the old one has got to), so there is never a
    * gap (the old copy waits in place until the next arrives) and never a doubled ghost.
    */
   private static void replaceTrack(FxRenderer.Fx nf) {
      FxPacket np = nf.p;
      FxRenderer.Fx best = null;
      double bestD = Double.MAX_VALUE;
      for (FxRenderer.Fx f : FX) {
         FxPacket p = f.p;
         if (p.life > 1 || f.killAge >= 0.0 || p.shape != np.shape || p.palette != np.palette) continue;
         if (Math.abs(p.r - np.r) > Math.max(0.05, np.r * 0.25) || Math.abs(p.r2 - np.r2) > Math.max(0.05, Math.abs(np.r2) * 0.25)) continue;
         double age = nf.born - f.born;
         if (age < 0.5 || age > 3.0) continue;
         Vec3 at = (p.shape == 7 || p.shape == 8) ? travelled(f, age) : p.c.add(p.v.scale(mv(p, age)));
         double d = at.distanceTo(np.c);
         double tol = 0.6 + np.v.length() * 0.6;
         if (d < tol && d < bestD) { bestD = d; best = f; }
      }
      if (best != null) best.killAge = -2.0;   // marked: dropped below
      FX.removeIf(f -> f.killAge == -2.0);
   }

   /** How long a packet has moved: per-tick copies (life 1) stop at the end of their own tick and wait for the next. */
   private static double mv(FxPacket p, double age) { return p.life <= 1 ? Math.min(age, 1.0) : age; }

   private static Vec3 travelled(FxRenderer.Fx f, double age) {
      double mt = f.p.a1 > 0.0F ? Math.min(age, (double)f.p.a1) : age;
      if (f.p.life <= 1) mt = Math.min(mt, 1.0);
      if (f.killAge >= 0.0) mt = Math.min(mt, f.killAge);
      return f.p.c.add(f.p.v.scale(mt));
   }

   private static float[] solidLo(int pal) {
      float[] b = BODY[pal];
      return new float[]{b[0] * 1.6F + 0.06F, b[1] * 1.6F + 0.06F, b[2] * 1.6F + 0.07F};
   }

   /** Lit colour of a face: lambert from the key light plus a fresnel rim toward the camera. */
   private static float[] shade(int pal, Vec3 nrm, Vec3 view, float mul) {
      if (pal == 11) {   // mercury: a mirror - the sky reflected on top, dark below, a hard highlight
         Vec3 refl = view.subtract(nrm.scale(2.0 * view.dot(nrm)));
         float sky = (float)Math.pow(Mth.clamp(refl.y * 0.5 + 0.5, 0.0, 1.0), 1.4);
         float spec = (float)Math.pow(Math.max(0.0, refl.dot(LIGHT)), 24.0);
         float v = (0.12F + 0.78F * sky + 0.6F * spec) * (0.85F + 0.15F * mul);
         return new float[]{Math.min(1F, v * 0.95F), Math.min(1F, v * 0.97F), Math.min(1F, v * 1.03F)};
      }
      float lam = (float)Math.max(0.0, nrm.dot(LIGHT));
      float fres = 1.0F - (float)Math.abs(nrm.dot(view));
      float k = Mth.clamp(0.16F + 0.62F * lam + 0.3F * fres * fres, 0.0F, 1.0F) * mul;
      float[] lo = solidLo(pal), hi = RIM[pal];
      return new float[]{lo[0] + (hi[0] - lo[0]) * k, lo[1] + (hi[1] - lo[1]) * k, lo[2] + (hi[2] - lo[2]) * k};
   }

   /** One flat-shaded face (quad, or triangle with d == c). Returns false when it faces away and was skipped. */
   private static boolean face(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 a, Vec3 b, Vec3 c, Vec3 d, Vec3 nrm, int pal, float al, Vec3 up, Vec3 ctr) {
      Vec3 mid = a.add(b).add(c).add(d).scale(0.25);
      Vec3 view = mid.subtract(cam);
      double vl = view.length();
      if (vl < 1.0E-6 || nrm.dot(view) > 0.0) return false;
      view = view.scale(1.0 / vl);
      float[] ca = shade(pal, nrm, view, a.subtract(ctr).dot(up) > 0.0 ? 1.0F : 0.78F);
      float[] cb = shade(pal, nrm, view, b.subtract(ctr).dot(up) > 0.0 ? 1.0F : 0.78F);
      float[] cc = shade(pal, nrm, view, c.subtract(ctr).dot(up) > 0.0 ? 1.0F : 0.78F);
      float[] cd = shade(pal, nrm, view, d.subtract(ctr).dot(up) > 0.0 ? 1.0F : 0.78F);
      vtx(vc, m, n, cam, a, ca, al);
      vtx(vc, m, n, cam, b, cb, al);
      vtx(vc, m, n, cam, c, cc, al);
      vtx(vc, m, n, cam, d, cd, al);
      return true;
   }

   /** BOX: a shaded block that pops in, rises out of the ground, or extends along its axis; can spin and travel. */
   private static void boxFx(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxRenderer.Fx f, double age, double life) {
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
      double out = smooth((life - age) / Math.min(6.0, Math.max(2.0, life * 0.3)));
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

   /** A shaded box with glowing visible edges (brighter along the top). */
   private static void drawBox(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 c, Vec3 ax, Vec3 side, Vec3 upv,
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
   private static void spikeFx(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxRenderer.Fx f, double age, double life) {
      FxPacket p = f.p;
      double in = p.sweep > 0 ? Mth.clamp(age / (double)p.sweep, 0.0, 1.0) : 1.0;
      double out = smooth((life - age) / Math.min(6.0, Math.max(2.0, life * 0.3)));
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

   private static void drawSpike(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 base, Vec3 d, double L, double R, double spin, int pal, float al) {
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

   private static boolean tri(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 a, Vec3 b, Vec3 c, Vec3 base, Vec3 d, int pal, float al, Vec3 ctr) {
      Vec3 nrm = b.subtract(a).cross(c.subtract(a));
      if (nrm.lengthSqr() < 1.0E-10) return false;
      nrm = nrm.normalize();
      Vec3 mid = a.add(b).add(c).scale(1.0 / 3.0);
      Vec3 axisPt = base.add(d.scale(mid.subtract(base).dot(d)));
      if (nrm.dot(mid.subtract(axisPt)) < 0.0) nrm = nrm.scale(-1.0);
      return face(vc, m, n, cam, a, b, c, c, nrm, pal, al, d, ctr);
   }

   private static void wall(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age, float env) {
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

   private static void crescentFx(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age, float env) {
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
   private static void slashFx(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age, double life) {
      double reveal = Mth.clamp(age / 2.0, 0.0, 1.0);
      double grow = 1.0 - Math.pow(1.0 - Mth.clamp(age / (life * 0.45), 0.0, 1.0), 3.0);
      double R = p.r + (p.r2 - p.r) * grow;
      float fade = (float)Mth.clamp((life - age) / (life * 0.45), 0.0, 1.0);
      double thin = Math.sqrt(fade);
      Vec3 c = p.c.add(p.d.scale(p.speed * age));
      Vec3 d = p.d, u = p.u;
      float[] black = new float[]{0.01F, 0.0F, 0.015F};
      float[] rim = RIM[p.palette], hc = new float[]{1.0F, 0.97F, 0.95F};
      double thick = p.h > 0.0F ? p.h : 1.0;   // h = how thick the blade is (1 = normal)
      int N = 24;
      double a0deg = -80.0, span = 160.0 * reveal;
      for (int i = 0; i < N; i++) {
         double f0 = a0deg + span * i / N, f1 = a0deg + span * (i + 1) / N;
         double r0 = Math.toRadians(f0), r1 = Math.toRadians(f1);
         Vec3 o0 = c.add(u.scale(Math.sin(r0) * R)).add(d.scale((Math.cos(r0) - 1.0) * R * 0.35));
         Vec3 o1 = c.add(u.scale(Math.sin(r1) * R)).add(d.scale((Math.cos(r1) - 1.0) * R * 0.35));
         double w0 = R * 0.17 * thin * Math.pow(Math.cos(Math.toRadians(f0 / 80.0 * 88.0)), 1.4);
         double w1 = R * 0.17 * thin * Math.pow(Math.cos(Math.toRadians(f1 / 80.0 * 88.0)), 1.4);
         Vec3 i0 = o0.subtract(d.scale(w0)), i1 = o1.subtract(d.scale(w1));
         // the solid black blade of the slash
         quad(vc, m, n, cam, o0, o1, i1, i0, black, 0.97F * fade, 0.97F * fade, 0.97F * fade, 0.97F * fade);
         // a short smoky wake behind it
         quad(vc, m, n, cam, i0, i1, i1.subtract(d.scale(w1 * 1.4)), i0.subtract(d.scale(w0 * 1.4)), black, 0.45F * fade, 0.45F * fade, 0.0F, 0.0F);
         // depth: the blade is also wide across its own plane, so it reads as a thick cut from behind, not a hairline
         Vec3 hx = d.cross(u).normalize();
         double k0 = w0 * 0.55 * thick, k1 = w1 * 0.55 * thick;
         Vec3 m0 = o0.subtract(d.scale(w0 * 0.3)), m1 = o1.subtract(d.scale(w1 * 0.3));
         quad(vc, m, n, cam, m0.add(hx.scale(k0)), m1.add(hx.scale(k1)), m1.subtract(hx.scale(k1)), m0.subtract(hx.scale(k0)), black, 0.92F * fade, 0.92F * fade, 0.92F * fade, 0.92F * fade);
         gquad(m, cam, m0.add(hx.scale(k0)), m1.add(hx.scale(k1)), m1.add(hx.scale(k0 * 1.6)), m0.add(hx.scale(k0 * 1.6)), rim, 0.4F * fade, 0.4F * fade, 0.0F, 0.0F);
         gquad(m, cam, m0.subtract(hx.scale(k0)), m1.subtract(hx.scale(k1)), m1.subtract(hx.scale(k0 * 1.6)), m0.subtract(hx.scale(k0 * 1.6)), rim, 0.4F * fade, 0.4F * fade, 0.0F, 0.0F);
         // the white-hot cutting edge and a thin coloured glow ahead of it
         double e0 = Math.max(0.02, w0 * 0.16), e1 = Math.max(0.02, w1 * 0.16);
         gquad(m, cam, o0.add(d.scale(e0 * 0.5)), o1.add(d.scale(e1 * 0.5)), o1.subtract(d.scale(e1)), o0.subtract(d.scale(e0)), hc, fade, fade, fade, fade);
         gquad(m, cam, o0.add(d.scale(e0 * 0.5)), o1.add(d.scale(e1 * 0.5)), o1.add(d.scale(e1 * 4.0)), o0.add(d.scale(e0 * 4.0)), rim, 0.55F * fade, 0.55F * fade, 0.0F, 0.0F);
      }
   }

   private static void crescent(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 c, Vec3 d, Vec3 u, double R, int pal, float env) {
      float[] body = BODY[pal];
      float[] rim = RIM[pal];
      int N = 16;

      for (int i = 0; i < N; i++) {
         double f0 = -78.0 + 156.0 * (double)i / (double)N;
         double f1 = -78.0 + 156.0 * (double)(i + 1) / (double)N;
         double r0 = Math.toRadians(f0);
         double r1 = Math.toRadians(f1);
         Vec3 o0 = c.add(u.scale(Math.sin(r0) * R)).add(d.scale((Math.cos(r0) - 1.0) * R * 0.55));
         Vec3 o1 = c.add(u.scale(Math.sin(r1) * R)).add(d.scale((Math.cos(r1) - 1.0) * R * 0.55));
         double t0 = 0.5 * R * Math.cos(Math.toRadians(f0 / 78.0 * 90.0));
         double t1 = 0.5 * R * Math.cos(Math.toRadians(f1 / 78.0 * 90.0));
         Vec3 i0 = o0.subtract(d.scale(t0));
         Vec3 i1 = o1.subtract(d.scale(t1));
         quad(vc, m, n, cam, o0, o1, i1, i0, body, 0.85F * env, 0.85F * env, 0.15F * env, 0.15F * env);
         double w0 = 0.09 * R * Math.cos(Math.toRadians(f0 / 78.0 * 90.0));
         double w1 = 0.09 * R * Math.cos(Math.toRadians(f1 / 78.0 * 90.0));
         quad(
            vc,
            m,
            n,
            cam,
            o0.add(d.scale(w0 * 0.3)),
            o1.add(d.scale(w1 * 0.3)),
            o1.subtract(d.scale(w1)),
            o0.subtract(d.scale(w0)),
            rim,
            0.98F * env,
            0.98F * env,
            0.7F * env,
            0.7F * env
         );
         // glow: white-hot blade edge and a bloom ahead of it, so the slash reads from far away
         float[] hc = hot(pal);
         gquad(m, cam, o0.add(d.scale(w0 * 0.3)), o1.add(d.scale(w1 * 0.3)), o1.subtract(d.scale(w1 * 0.6)), o0.subtract(d.scale(w0 * 0.6)), hc, env, env, 0.8F * env, 0.8F * env);
         gquad(m, cam, o0.add(d.scale(w0 * 0.3)), o1.add(d.scale(w1 * 0.3)), o1.add(d.scale(w1 * 2.6)), o0.add(d.scale(w0 * 2.6)), rim, 0.5F * env, 0.5F * env, 0.0F, 0.0F);
         gquad(m, cam, o0.subtract(d.scale(w0 * 0.6)), o1.subtract(d.scale(w1 * 0.6)), i1, i0, rim, 0.45F * env, 0.45F * env, 0.0F, 0.0F);
      }
   }

   private static void beam(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 p0, Vec3 p1, float width, int pal, float env, double endTaper) {
      Vec3 axis = p1.subtract(p0);
      double len = axis.length();
      if (!(len < 1.0E-4)) {
         Vec3 d = axis.scale(1.0 / len);
         Vec3 ref = Math.abs(d.y) > 0.95 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0);
         Vec3 n1 = d.cross(ref).normalize();
         Vec3 n2 = d.cross(n1).normalize();
         float[] body = BODY[pal];
         float[] rim = RIM[pal];
         int K = Math.max(4, Math.min(24, (int)(len * 2.0)));
         if (isFlame(pal) && width > 0.15F) {
            Vec3 rise = new Vec3(0.0, 1.0, 0.0).subtract(d.scale(d.y));
            rise = rise.lengthSqr() < 1.0E-4 ? n1 : rise.normalize();
            for (int k = 0; k < K; k++) {
               double t0 = (double)k / K, t1 = (double)(k + 1) / K;
               double wf = width * Math.pow(Math.sin(Math.PI * (0.03 + 0.94 * (t0 + t1) * 0.5)), endTaper);
               flameTongue(vc, m, n, cam, p0.add(axis.scale(t0)).add(rise.scale(wf * 0.3)), p0.add(axis.scale(t1)).add(rise.scale(wf * 0.3)), rise, wf * 1.3, pal, env * 0.9F, k * 3 + (int)(p0.x * 7.0));
            }
         }

         for (Vec3 nn : new Vec3[]{n1, n2}) {
            for (int k = 0; k < K; k++) {
               double t0 = (double)k / (double)K;
               double t1 = (double)(k + 1) / (double)K;
               double w0 = (double)width * Math.pow(Math.sin(Math.PI * (0.03 + 0.94 * t0)), endTaper);
               double w1 = (double)width * Math.pow(Math.sin(Math.PI * (0.03 + 0.94 * t1)), endTaper);
               Vec3 a = p0.add(axis.scale(t0));
               Vec3 b = p0.add(axis.scale(t1));
               float A = 0.7F * env;
               quad(vc, m, n, cam, a.add(nn.scale(w0)), b.add(nn.scale(w1)), b, a, body, 0.0F, 0.0F, A, A);
               quad(vc, m, n, cam, a, b, b.subtract(nn.scale(w1)), a.subtract(nn.scale(w0)), body, A, A, 0.0F, 0.0F);
               double cw0 = w0 * 0.12;
               double cw1 = w1 * 0.12;
               quad(
                  vc,
                  m,
                  n,
                  cam,
                  a.add(nn.scale(cw0)),
                  b.add(nn.scale(cw1)),
                  b.subtract(nn.scale(cw1)),
                  a.subtract(nn.scale(cw0)),
                  rim,
                  0.95F * env,
                  0.95F * env,
                  0.95F * env,
                  0.95F * env
               );
               // glow: hot core and bloom
               float[] hc = hot(pal);
               double gc0 = w0 * 0.16, gc1 = w1 * 0.16, gb0 = w0 * 0.75, gb1 = w1 * 0.75;
               gquad(m, cam, a.add(nn.scale(gc0)), b.add(nn.scale(gc1)), b.subtract(nn.scale(gc1)), a.subtract(nn.scale(gc0)), hc, 0.8F * env, 0.8F * env, 0.8F * env, 0.8F * env);
               gquad(m, cam, a.add(nn.scale(gc0)), b.add(nn.scale(gc1)), b.add(nn.scale(gb1)), a.add(nn.scale(gb0)), rim, 0.35F * env, 0.35F * env, 0.0F, 0.0F);
               gquad(m, cam, a.subtract(nn.scale(gc0)), b.subtract(nn.scale(gc1)), b.subtract(nn.scale(gb1)), a.subtract(nn.scale(gb0)), rim, 0.35F * env, 0.35F * env, 0.0F, 0.0F);
            }
         }
      }
   }

   private static void book(PoseStack ps, VertexConsumer vc, Vec3 cam, FxRenderer.BookDraw b) {
      ps.pushPose();
      ps.translate(b.pos.x - cam.x, b.pos.y - cam.y, b.pos.z - cam.z);
      ps.mulPose(Axis.YP.rotationDegrees(-b.yaw));
      ps.mulPose(Axis.YP.rotationDegrees(180.0F));
      ps.mulPose(Axis.XP.rotationDegrees(-b.tilt));
      ps.scale(b.scale, b.scale, b.scale);
      Matrix4f m = ps.last().pose();
      Matrix3f n = ps.last().normal();
      float HW = b.hw;
      float HH = b.hh;
      float V = (float)Math.toRadians((double)b.vDeg);
      float al = b.alpha;

      // the book itself is the real 3D model (drawn by GrimoireFloatRenderer); here each grimoire's own aura
      bookAura(vc, m, n, b, HW, HH, V, al);

      if (b.flipCycle >= 0.0F) {
         for (int i = 0; i < 3; i++) {
            float sP = (b.flipCycle - (float)(4 + i * 5)) / 14.0F;
            if (!(sP <= 0.0F) && !(sP >= 1.0F)) {
               float mm = 0.5F - 0.5F * (float)Math.cos(Math.PI * (double)sP);
               double alpha = Math.toRadians((double)(-22.5F - 135.0F * mm));
               float lift = 0.07F + 0.03F * (1.0F - 2.0F * Math.abs(mm - 0.5F));
               PAGES.add(new PageDraw(new Matrix4f(m), new Matrix3f(n), (float)Math.cos(alpha), (float)(-Math.sin(alpha)), lift, HW * 0.95F, HH * 0.92F, b.light));
            }
         }
      }

      ps.popPose();
   }

   /** A flat glowing ribbon in the book's plane (local frame, viewer roughly along +z). */
   private static void lline(Matrix4f m, Vec3 p0, Vec3 p1, float w, int pal, float a0, float a1) {
      Vec3 ax = p1.subtract(p0);
      if (ax.lengthSqr() < 1.0E-8) return;
      Vec3 side = ax.cross(new Vec3(0.0, 0.0, 1.0));
      if (side.lengthSqr() < 1.0E-8) side = new Vec3(1.0, 0.0, 0.0);
      side = side.normalize().scale(w);
      float[] hc = hot(pal), rim = RIM[pal];
      Vec3 c = side.scale(0.35);
      gquad(m, Vec3.ZERO, p0.add(c), p1.add(c), p1.subtract(c), p0.subtract(c), hc, a0, a1, a1, a0);
      gquad(m, Vec3.ZERO, p0.add(c), p1.add(c), p1.add(side), p0.add(side), rim, a0 * 0.55F, a1 * 0.55F, 0.0F, 0.0F);
      gquad(m, Vec3.ZERO, p0.subtract(c), p1.subtract(c), p1.subtract(side), p0.subtract(side), rim, a0 * 0.55F, a1 * 0.55F, 0.0F, 0.0F);
   }

   /** A small four-pointed glint in the book's plane. */
   private static void lstar(Matrix4f m, Vec3 c, float r, int pal, float a) {
      lline(m, c.add(-r, 0.0, 0.0), c.add(r, 0.0, 0.0), r * 0.18F, pal, a, a);
      lline(m, c.add(0.0, -r, 0.0), c.add(0.0, r, 0.0), r * 0.18F, pal, a, a);
   }

   /** Soft elliptical underglow behind the book. */
   private static void underglow(Matrix4f m, float HW, float HH, int pal, float a) {
      float[] glowC = RIM[pal];
      int seg = 24;
      for (int i = 0; i < seg; i++) {
         double a0 = Math.PI * 2.0 * i / seg, a1 = Math.PI * 2.0 * (i + 1) / seg;
         double wob0 = 1.0 + 0.12 * Math.sin(bookTime * 0.3 + i * 1.9), wob1 = 1.0 + 0.12 * Math.sin(bookTime * 0.3 + (i + 1) * 1.9);
         Vec3 q0 = new Vec3(Math.cos(a0) * HW * 1.75 * wob0, Math.sin(a0) * HH * 1.6 * wob0, -0.06);
         Vec3 q1 = new Vec3(Math.cos(a1) * HW * 1.75 * wob1, Math.sin(a1) * HH * 1.6 * wob1, -0.06);
         Vec3 c0 = new Vec3(0.0, 0.0, -0.06);
         gquad(m, Vec3.ZERO, c0, c0, q0, q1, glowC, a, a, 0.0F, 0.0F);
      }
   }

   /**
    * Each grimoire's own aura around the open book:
    *  0 Black  - licking black flame with a red core and a red underglow (like the anime)
    *  1 Dark   - violet-black flame, and three dark crescents circling the book
    *  2 Steel  - six steel shards orbiting in a tilted ring, glinting
    *  3 Four-leaf - green wind streaks spiralling around it, with leaves carried on the wind
    *  4 Three-leaf - a turning golden halo behind it and motes of light rising (hope)
    *  5 Mana Zone Codex - two cyan rune circles turning opposite ways behind it
    *  6 Spirit-bound - three pale spirit wisps orbiting with comet tails
    */
   private static void bookAura(VertexConsumer vc, Matrix4f m, Matrix3f n, FxRenderer.BookDraw b, float HW, float HH, float V, float al) {
      double t = bookTime;
      int pal = b.pal;
      switch (b.style) {
         case 0 -> {
            underglow(m, HW, HH, pal, 0.42F * al);
            for (int s = -1; s <= 1; s += 2) panelOutline(vc, m, n, s * (float)Math.cos(V), (float)Math.sin(V), -0.03F, HW, HH, al, s, pal);
         }
         case 1 -> {
            underglow(m, HW, HH, pal, 0.3F * al);
            for (int s = -1; s <= 1; s += 2) panelOutline(vc, m, n, s * (float)Math.cos(V), (float)Math.sin(V), -0.03F, HW, HH, al * 0.8F, s, pal);
            for (int k = 0; k < 3; k++) {
               double c0 = t * 0.09 + k * Math.PI * 2.0 / 3.0;
               Vec3 prev = null;
               for (int i = 0; i <= 8; i++) {
                  double a = c0 + i * 0.13;
                  Vec3 q = new Vec3(Math.cos(a) * HW * 1.45, Math.sin(a) * HH * 1.45, 0.04);
                  if (prev != null) {
                     float f = (float)Math.sin(Math.PI * i / 8.0);
                     lline(m, prev, q, 0.035F * f + 0.01F, pal, al * 0.8F * f, al * 0.8F * f);
                     quad(vc, m, n, Vec3.ZERO, prev, q, q.scale(1.08), prev.scale(1.08), SMOKE, 0.7F * al * f, 0.7F * al * f, 0.0F, 0.0F);
                  }
                  prev = q;
               }
            }
         }
         case 2 -> {
            underglow(m, HW, HH, pal, 0.18F * al);
            float[] steel = new float[]{0.62F, 0.68F, 0.76F}, dark = new float[]{0.22F, 0.25F, 0.3F};
            for (int k = 0; k < 6; k++) {
               double a = t * 0.05 + k * Math.PI / 3.0;
               Vec3 c = new Vec3(Math.cos(a) * HW * 1.5, Math.sin(a) * HH * 1.15, 0.12 * Math.sin(a + 0.6));
               Vec3 tan = new Vec3(-Math.sin(a), Math.cos(a), 0.0).scale(0.11);
               Vec3 nrm = new Vec3(Math.cos(a), Math.sin(a), 0.0).scale(0.04);
               Vec3 tip0 = c.add(tan), tip1 = c.subtract(tan);
               quad(vc, m, n, Vec3.ZERO, tip0, c.add(nrm), tip1, c.subtract(nrm), steel, al, al, al, al);
               quad(vc, m, n, Vec3.ZERO, tip0, c.add(nrm).add(0.0, 0.0, -0.01), tip1, c.subtract(nrm).add(0.0, 0.0, -0.01), dark, al, al, al, al);
               float g = 0.5F + 0.5F * (float)Math.sin(t * 0.4 + k * 1.3);
               lline(m, tip0, tip1, 0.012F, pal, al * 0.6F * g, al * 0.6F * g);
               if (g > 0.92F) lstar(m, tip0, 0.06F, pal, al * (g - 0.92F) * 10.0F);
            }
         }
         case 3 -> {
            underglow(m, HW, HH, pal, 0.2F * al);
            for (int k = 0; k < 3; k++) {
               Vec3 prev = null;
               for (int i = 0; i <= 14; i++) {
                  double u = i / 14.0;
                  double a = t * 0.16 + k * 2.1 + u * 4.0;
                  double r = 0.55 + 0.25 * u;
                  Vec3 q = new Vec3(Math.cos(a) * r, -0.35 + u * 0.8 + 0.05 * Math.sin(t * 0.2 + k), 0.15 * Math.sin(a));
                  if (prev != null) lline(m, prev, q, 0.018F, pal, al * (float)(u * 0.7), al * (float)(u * 0.7));
                  prev = q;
               }
            }
            float[] leaf = new float[]{0.3F, 0.85F, 0.4F};
            for (int k = 0; k < 4; k++) {
               double a = t * 0.11 + k * 1.57;
               Vec3 c = new Vec3(Math.cos(a) * 0.7, 0.35 * Math.sin(t * 0.07 + k * 2.0), 0.18 * Math.sin(a));
               double sp = t * 0.3 + k;
               Vec3 l1 = new Vec3(Math.cos(sp) * 0.05, Math.sin(sp) * 0.05, 0.0), l2 = new Vec3(-Math.sin(sp) * 0.025, Math.cos(sp) * 0.025, 0.0);
               quad(vc, m, n, Vec3.ZERO, c.add(l1), c.add(l2), c.subtract(l1), c.subtract(l2), leaf, al * 0.95F, al * 0.95F, al * 0.95F, al * 0.95F);
            }
         }
         case 4 -> {
            underglow(m, HW, HH, pal, 0.3F * al);
            Vec3 prev = null;
            for (int i = 0; i <= 32; i++) {
               double a = Math.PI * 2.0 * i / 32.0;
               Vec3 q = new Vec3(Math.cos(a) * 0.62, Math.sin(a) * 0.62, -0.09);
               if (prev != null) lline(m, prev, q, 0.02F, pal, al * 0.7F, al * 0.7F);
               prev = q;
            }
            for (int k = 0; k < 8; k++) {
               double a = t * 0.03 + k * Math.PI / 4.0;
               lline(m, new Vec3(Math.cos(a) * 0.66, Math.sin(a) * 0.66, -0.09), new Vec3(Math.cos(a) * 0.78, Math.sin(a) * 0.78, -0.09), 0.02F, pal, al * 0.8F, 0.0F);
            }
            for (int k = 0; k < 8; k++) {
               double ph = (t * 0.015 + k / 8.0) % 1.0;
               Vec3 c = new Vec3(Math.sin(k * 2.3) * 0.5, -0.4 + ph * 0.95, 0.1 + 0.05 * Math.cos(k * 1.7));
               lstar(m, c, 0.035F, pal, al * (float)Math.sin(Math.PI * ph));
            }
         }
         case 5 -> {
            underglow(m, HW, HH, pal, 0.22F * al);
            for (int ring = 0; ring < 2; ring++) {
               double r = ring == 0 ? 0.6 : 0.78, rot = (ring == 0 ? 1 : -1) * t * 0.025;
               Vec3 prev = null;
               for (int i = 0; i <= 40; i++) {
                  double a = Math.PI * 2.0 * i / 40.0 + rot;
                  Vec3 q = new Vec3(Math.cos(a) * r, Math.sin(a) * r, -0.09);
                  if (prev != null && (ring == 0 || i % 5 != 0)) lline(m, prev, q, 0.014F, pal, al * 0.75F, al * 0.75F);
                  prev = q;
               }
               for (int k = 0; k < 12; k++) {   // rune ticks
                  double a = Math.PI * 2.0 * k / 12.0 + rot;
                  double l = k % 3 == 0 ? 0.1 : 0.05;
                  lline(m, new Vec3(Math.cos(a) * r, Math.sin(a) * r, -0.09), new Vec3(Math.cos(a) * (r - l), Math.sin(a) * (r - l), -0.09), 0.012F, pal, al * 0.8F, al * 0.4F);
               }
            }
            for (int k = 0; k < 6; k++) {   // a turning hexagram inside
               double a0 = t * 0.02 + k * Math.PI / 3.0, a1 = a0 + Math.PI * 2.0 / 3.0;
               lline(m, new Vec3(Math.cos(a0) * 0.6, Math.sin(a0) * 0.6, -0.1), new Vec3(Math.cos(a1) * 0.6, Math.sin(a1) * 0.6, -0.1), 0.01F, pal, al * 0.45F, al * 0.45F);
            }
         }
         default -> {
            underglow(m, HW, HH, pal, 0.25F * al);
            for (int k = 0; k < 3; k++) {
               Vec3 prev = null;
               for (int i = 0; i <= 10; i++) {
                  double a = t * 0.12 + k * Math.PI * 2.0 / 3.0 - i * 0.12;
                  Vec3 q = new Vec3(Math.cos(a) * HW * 1.5, Math.sin(a) * HH * 1.4 + 0.05 * Math.sin(t * 0.3 + k), 0.15 * Math.sin(a * 2.0));
                  if (prev != null) lline(m, prev, q, 0.03F * (1.0F - i / 11.0F), pal, al * (1.0F - i / 10.0F), al * (1.0F - (i - 1) / 10.0F));
                  else lstar(m, q, 0.06F, pal, al);
                  prev = q;
               }
            }
         }
      }
   }

   /** A turning parchment page with a few lines of ink. */
   private static void page(VertexConsumer vc, Matrix4f m, Matrix3f n, float dx, float dz, float lift, float w, float h, float al) {
      float[] paper = new float[]{0.86F, 0.8F, 0.64F};
      float[] ink = new float[]{0.22F, 0.12F, 0.1F};
      Vec3 z = Vec3.ZERO;
      quad(vc, m, n, z, pt(dx, dz, lift, 0.0F, -h), pt(dx, dz, lift, w, -h), pt(dx, dz, lift, w, h), pt(dx, dz, lift, 0.0F, h), paper, 0.97F * al, 0.97F * al, 0.97F * al, 0.97F * al);
      for (int i = 1; i <= 6; i++) {
         float y = -h + 2.0F * h * (float)i / 7.0F;
         float len = w * (i % 3 == 0 ? 0.5F : 0.78F);
         float l2 = lift + 0.002F;
         quad(vc, m, n, z, pt(dx, dz, l2, w * 0.1F, y - 0.007F), pt(dx, dz, l2, w * 0.1F + len, y - 0.007F), pt(dx, dz, l2, w * 0.1F + len, y + 0.007F), pt(dx, dz, l2, w * 0.1F, y + 0.007F), ink, 0.7F * al, 0.7F * al, 0.7F * al, 0.7F * al);
      }
   }

   private static void panel(VertexConsumer vc, Matrix4f m, Matrix3f n, float dx, float dz, float lift, float w, float h, float al) {
      float[] dark = new float[]{0.02F, 0.0F, 0.04F};
      float[] glow = new float[]{0.55F, 0.06F, 0.16F};
      float[] line = new float[]{0.05F, 0.0F, 0.08F};
      Vec3 a = pt(dx, dz, lift, 0.0F, -h);
      Vec3 b = pt(dx, dz, lift, w, -h);
      Vec3 c = pt(dx, dz, lift, w, h);
      Vec3 d = pt(dx, dz, lift, 0.0F, h);
      Vec3 z = Vec3.ZERO;
      quad(vc, m, n, z, a, b, c, d, dark, 0.16F * al, 0.16F * al, 0.16F * al, 0.16F * al);

      for (int i = 1; i <= 6; i++) {
         float y = -h + 2.0F * h * (float)i / 7.0F;
         float len = w * (i % 3 == 0 ? 0.55F : 0.82F);
         quad(
            vc,
            m,
            n,
            z,
            pt(dx, dz, lift, w * 0.1F, y - 0.006F),
            pt(dx, dz, lift, w * 0.1F + len, y - 0.006F),
            pt(dx, dz, lift, w * 0.1F + len, y + 0.006F),
            pt(dx, dz, lift, w * 0.1F, y + 0.006F),
            line,
            0.35F * al,
            0.35F * al,
            0.35F * al,
            0.35F * al
         );
      }

      float t = 0.028F;
      edge(vc, m, n, a, b, pt(dx, dz, lift, w, -h + t), pt(dx, dz, lift, 0.0F, -h + t), dark, al, glow);
      edge(vc, m, n, d, c, pt(dx, dz, lift, w, h - t), pt(dx, dz, lift, 0.0F, h - t), dark, al, glow);
      edge(vc, m, n, a, d, pt(dx, dz, lift, t, h), pt(dx, dz, lift, t, -h), dark, al, glow);
      edge(vc, m, n, b, c, pt(dx, dz, lift, w - t, h), pt(dx, dz, lift, w - t, -h), dark, al, glow);
   }

   /**
    * Black-flame outline around one open page: a soft dark-crimson halo hugging the outer three edges
    * (the spine edge is skipped), whose width flickers along its length like licking flame. Kept thin and
    * translucent so the book stays readable.
    */
   private static void panelOutline(VertexConsumer vc, Matrix4f m, Matrix3f n, float dx, float dz, float lift, float w, float h, float al, int seed, int pal) {
      float[] core = RIM[pal];
      float[] smoke = new float[]{0.02F, 0.0F, 0.015F};
      // outer three edges as (start u,y) -> (end u,y) with outward normal (nu,ny); top edge gets taller flames
      float[][] edges = new float[][]{
         {0.0F, -h, w, -h, 0.0F, -1.0F, 0.7F},
         {w, -h, w, h, 1.0F, 0.0F, 1.0F},
         {w, h, 0.0F, h, 0.0F, 1.0F, 1.6F}
      };
      int N = 10;
      double t = bookTime * 0.22 + seed * 3.1;
      for (int e = 0; e < edges.length; e++) {
         float[] E = edges[e];
         for (int i = 0; i < N; i++) {
            float f0 = (float)i / N, f1 = (float)(i + 1) / N;
            float u0 = E[0] + (E[2] - E[0]) * f0, y0 = E[1] + (E[3] - E[1]) * f0;
            float u1 = E[0] + (E[2] - E[0]) * f1, y1 = E[1] + (E[3] - E[1]) * f1;
            int k0 = e * N + i, k1 = k0 + 1;
            float x0 = (float)(0.07 + 0.055 * Math.sin(t + k0 * 1.37) + 0.03 * Math.sin(t * 2.3 + k0 * 0.71)) * E[6];
            float x1 = (float)(0.07 + 0.055 * Math.sin(t + k1 * 1.37) + 0.03 * Math.sin(t * 2.3 + k1 * 0.71)) * E[6];
            x0 = Math.max(0.012F, x0);
            x1 = Math.max(0.012F, x1);
            Vec3 a = pt(dx, dz, lift, u0, y0), b = pt(dx, dz, lift, u1, y1);
            Vec3 ao = pt(dx, dz, lift, u0 + E[4] * x0, y0 + E[5] * x0), bo = pt(dx, dz, lift, u1 + E[4] * x1, y1 + E[5] * x1);
            Vec3 ao2 = pt(dx, dz, lift, u0 + E[4] * x0 * 1.9F, y0 + E[5] * x0 * 1.9F), bo2 = pt(dx, dz, lift, u1 + E[4] * x1 * 1.9F, y1 + E[5] * x1 * 1.9F);
            gquad(m, Vec3.ZERO, a, b, bo2, ao2, core, 0.75F * al, 0.75F * al, 0.0F, 0.0F);                    // glowing core of the flame
            quad(vc, m, n, Vec3.ZERO, ao, bo, bo2, ao2, smoke, 0.7F * al, 0.7F * al, 0.0F, 0.0F);            // black flame licking outward
            // a tall flickering tongue every few segments
            if ((k0 + seed) % 3 == 0) {
               float tl = (float)(0.12 + 0.1 * Math.abs(Math.sin(t * 0.8 + k0 * 2.1))) * E[6];
               float um = (u0 + u1) * 0.5F, ym = (y0 + y1) * 0.5F;
               Vec3 tip = pt(dx, dz, lift, um + E[4] * (x0 + tl), ym + E[5] * (x0 + tl) + tl * 0.4F);
               gquad(m, Vec3.ZERO, ao, bo, tip, tip, core, 0.55F * al, 0.55F * al, 0.0F, 0.0F);
               quad(vc, m, n, Vec3.ZERO, ao, bo, tip, tip, smoke, 0.75F * al, 0.75F * al, 0.0F, 0.0F);
            }
         }
      }
   }

   private static void edge(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 o0, Vec3 o1, Vec3 i1, Vec3 i0, float[] dark, float al, float[] glow) {
      quad(vc, m, n, Vec3.ZERO, o0, o1, i1, i0, dark, 0.95F * al, 0.95F * al, 0.95F * al, 0.95F * al);
      quad(
         vc,
         m,
         n,
         Vec3.ZERO,
         i0,
         i1,
         i1.add(i1.subtract(o1).scale(0.25)),
         i0.add(i0.subtract(o0).scale(0.25)),
         glow,
         0.65F * al,
         0.65F * al,
         0.0F,
         0.0F
      );
   }

   private static Vec3 pt(float dx, float dz, float lift, float u, float y) {
      return new Vec3((double)(dx * u), (double)y, (double)(dz * u + lift));
   }

   private static record AuraDraw(Vec3 a, Vec3 b, float width, int pal, float alpha) {
   }

   private static final ResourceLocation PAGE_R = ResourceLocation.fromNamespaceAndPath("arcane_addon", "textures/item/spell_book_models/black_grimoire_page_right.png");
   private static final ResourceLocation PAGE_L = ResourceLocation.fromNamespaceAndPath("arcane_addon", "textures/item/spell_book_models/black_grimoire_page_left.png");
   private static final List<PageDraw> PAGES = new ArrayList<>();

   private static record PageDraw(Matrix4f m, Matrix3f n, float dx, float dz, float lift, float w, float h, int light) {
   }

   /** One side of a turning page: wound so only the side facing you is drawn. */
   private static void pageQuad(VertexConsumer vc, PageDraw p, boolean back) {
      Vec3[] q = {pt(p.dx, p.dz, p.lift, 0.0F, -p.h), pt(p.dx, p.dz, p.lift, p.w, -p.h), pt(p.dx, p.dz, p.lift, p.w, p.h), pt(p.dx, p.dz, p.lift, 0.0F, p.h)};
      float[][] uv = {{0, 1}, {1, 1}, {1, 0}, {0, 0}};
      for (int i = 0; i < 4; i++) {
         int k = back ? 3 - i : i;
         float u = back ? 1.0F - uv[k][0] : uv[k][0];
         vc.vertex(p.m, (float)q[k].x, (float)q[k].y, (float)q[k].z).color(1.0F, 1.0F, 1.0F, 1.0F).uv(u, uv[k][1])
            .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(p.light).normal(p.n, 0.0F, 0.0F, back ? -1.0F : 1.0F).endVertex();
      }
   }

   private static record BookDraw(Vec3 pos, float yaw, float tilt, float scale, float alpha, float flipCycle, float hw, float hh, float vDeg, int pal, int style, int light) {
   }

   private static class Fx {
      FxPacket p;
      double born;
      double killAge = -1.0;
   }
}
