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
public class FxRenderer extends FxBase {
   static final List<FxRenderer.Fx> FX = new ArrayList<>();
   /** The per-tick copies (life <= 1) in FX, in the same order - all replaceTrack ever needs to look at. */
   static final List<FxRenderer.Fx> TRACK = new ArrayList<>();
   /** Players in Devil Union: entity id -> {start tick, end tick}. Fed by FxPacket shape 6. */
   static final java.util.Map<Integer, double[]> DEVIL = new java.util.HashMap<>();
   /** Black Cocoon shells: entity id -> {start, end, radius, close ticks}. Fed by FxPacket shape 15. */
   static final java.util.Map<Integer, double[]> COCOON = new java.util.HashMap<>();
   /** Devil Union: Beelzebub (Zenon): entity id -> {start, end}. Fed by shape 31. */
   static final java.util.Map<Integer, double[]> BONE_FORM = new java.util.HashMap<>();
   /** Spirit Dive: entity id -> {start, end}. Fed by FxPacket shape 18. */
   static final java.util.Map<Integer, double[]> SPIRIT = new java.util.HashMap<>();

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
   static final java.util.Map<Integer, double[]> ZONES = new java.util.HashMap<>();
   static final java.util.Map<Integer, Double> ZONE_TARGET = new java.util.HashMap<>();
   /**
    * Weapon rings (FxPacket shape 36): {entity id, kind, count, radius, height, angular speed (rad/tick), phase,
    * bob, grow ticks, stagger (ticks extra per piece), born, life}. kind 0 = wind halberds, 1 = mercury swords.
    */
   static final List<double[]> ORBITS = new ArrayList<>();
   /** Bone whips (shape 38): {entity id, born, life, count, reach, lash period, union}. */
   static final List<double[]> WHIPS = new ArrayList<>();
   static final List<FxRenderer.BookDraw> BOOKS = new ArrayList<>();
   static final List<FxRenderer.AuraDraw> AURAS = new ArrayList<>();

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
      if (p.shape == 42) { FxSync.cast(p.id, p.sweep, p.life); return; }   // a move's cast animation started (effect timing)
      if (FxSync.defer(p)) return;   // held back to land on the swing
      PixelFx.onPacket(p);   // forms, auras and held weapons shed pixel sprites too
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
      if (p.shape == 31) {   // Devil Union: Beelzebub - horns and bone wings. id = player, life = ticks left (0 = off)
         Minecraft mc0 = Minecraft.getInstance();
         if (mc0.level != null) {
            double now0 = mc0.level.getGameTime();
            if (p.life > 0) {
               double[] f = BONE_FORM.get(p.id);
               if (f == null || f[1] < now0) BONE_FORM.put(p.id, new double[]{now0, now0 + p.life}); else f[1] = now0 + p.life;
            } else BONE_FORM.remove(p.id);
         }
         return;
      }
      if (p.shape == 41) { PixelFx.spawn(p); return; }   // the pixel-sprite effect style (Sky Splitter test)
      if (p.shape == 40) { Cutscene.cinematic(p.life, p.sweep); return; }   // a short cinematic (Equinox)
      if (p.shape == 39) { DarkSmoke.guardFists(p.id, p.life); return; }   // Burning Fists Guard: id = player, life = ticks
      if (p.shape == 38) {   // Zenon's bone whips: id = player, life, h = count, r = reach, a0 = lash period, palette 1 = Beelzebub
         Minecraft mc0 = Minecraft.getInstance();
         if (mc0.level != null) WHIPS.add(new double[]{p.id, mc0.level.getGameTime(), p.life, p.h, p.r, p.a0, p.palette});
         return;
      }
      if (p.shape == 37) { BoneBladeLayer.set(p.id, p.life, p.r, p.palette == 1); return; }   // Zenon's bone sword in the hand
      if (p.shape == 36) {   // a ring of weapons circling a player, animated here every frame (smooth, one packet)
         Minecraft mc0 = Minecraft.getInstance();
         if (mc0.level != null) ORBITS.add(new double[]{p.id, p.palette, p.h, p.r, p.r2, p.a0, p.a1, p.speed, p.sweep, p.d.x, mc0.level.getGameTime(), p.life});
         return;
      }
      if (p.shape == 35) { LimitClient.set(p.id, p.palette, p.life); return; }   // Surpassing Limits aura: palette = book index
      if (p.shape == 34) { MasteryClient.setEnabled(p.life > 0); return; }   // Grimoire Mastery on/off
      if (p.shape == 32) { ModeHudOverlay.set(p.palette, p.life); return; }   // mode timer on the HUD: palette = mode, life = ticks (0 = off)
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
               ZONE_TARGET.put(p.id, (double) p.r);   // a clash pushes the dome back: it glides to the new radius
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
         Cutscene.impactFrame(p.life, (int) p.palette);
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
            PixelFx.kill(p.id);
            for (FxRenderer.Fx f : FX) {
               if (f.p.id == p.id && f.killAge < 0.0) {
                  f.killAge = (double)mc.level.getGameTime() - f.born;
               }
            }
         } else {
            FxRenderer.Fx fx = new FxRenderer.Fx();
            fx.p = p;
            fx.born = (double)mc.level.getGameTime();
            if (p.life <= 1) { replaceTrack(fx); TRACK.add(fx); }
            FX.add(fx);
         }
      }
   }

   /** {start, end} of this player's Devil Union (the wings are a GeckoLib model: GeoFormLayer), or null. */
   static double[] devilFormTimes(int id) { return DEVIL.get(id); }

   /** {start, end} of this player's Beelzebub form (GeoFormLayer), or null. */
   static double[] boneFormTimes(int id) { return BONE_FORM.get(id); }

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
            FxSync.tick();
            FX.clear();
            TRACK.clear();
            ORBITS.clear();
            WHIPS.clear();
         } else {
            double now = (double)mc.level.getGameTime();
            Iterator<FxRenderer.Fx> it = FX.iterator();
            DarkSmoke.tick();
            PixelFx.tick();
            FxSync.tick();
            for (java.util.Map.Entry<Integer, double[]> ze : ZONES.entrySet()) {
               Double tgt = ZONE_TARGET.get(ze.getKey());
               if (tgt != null) ze.getValue()[2] += (tgt - ze.getValue()[2]) * 0.2;
               net.minecraft.world.entity.Entity ent = mc.level.getEntity(ze.getKey());
               if (ent != null) FxZones.zoneTick(mc, ent, ze.getValue(), now);
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
            ZONES.values().removeIf(zf -> now > zf[1] + FxZones.ZONE_CLOSE + 2.0);
            FxZones.ZONE_H.keySet().removeIf(id -> !ZONES.containsKey(id));
            ZONE_TARGET.keySet().removeIf(id -> !ZONES.containsKey(id));
            if (ZONES.isEmpty() || now - columnTopAge > 100.0) { FxZones.COLUMN_TOP.clear(); FxZones.ZONE_H.clear(); columnTopAge = now; }
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
               if (f.p.shape == 30) FxEmitters.emitBone(f, now - f.born);
               {   // every effect also sheds pixel sprites in its own palette (PixelFx)
                  double pa = now - f.born, pl = f.killAge >= 0.0 ? f.killAge : (double)f.p.life;
                  boolean pTick = f.p.life <= 1 && f.killAge < 0.0;
                  Vec3 pat = (f.p.shape == 7 || f.p.shape == 8 || f.p.shape == 30) ? travelled(f, pa) : f.p.c.add(f.p.v.scale(mv(f.p, pa)));
                  PixelFx.fromFx(f.p, pat, pa, Math.max(pl, pTick ? 1.0 : pl), pTick);
               }
               if (f.p.palette == DARKNESS) FxEmitters.emitDarkness(f, now - f.born);
               else if (f.p.palette == WIND) FxEmitters.emitWind(f, now - f.born);
               else if (f.p.palette == MERCURY) FxEmitters.emitMercury(f, now - f.born);
               else if (f.p.palette == FIRE || f.p.palette == BLUE_FIRE || f.p.palette == CRIMSON) { flamePal = f.p.palette; FxEmitters.emitFire(f, now - f.born); flamePal = FIRE; }
               else if ((f.p.palette == LIGHTNING || f.p.palette == BLUE_LIGHTNING) && f.p.shape != 23) { boltPal = f.p.palette; FxEmitters.emitLightning(f, now - f.born); }
               double life = f.killAge >= 0.0 ? f.killAge + 4.0 : (double)f.p.life;
               if (now - f.born > life + (f.p.life <= 1 && f.killAge < 0.0 ? 1.6 : 1.0)) {   // per-tick copies: room to fade out
                  it.remove();
               }
            }
            TRACK.removeIf(f -> now - f.born > (f.killAge >= 0.0 ? f.killAge + 4.0 : (double)f.p.life) + (f.p.life <= 1 && f.killAge < 0.0 ? 1.6 : 1.0));   // same rule as FX above
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
   /** Packets made on this client while the effect list is being walked; spawned right after. */
   static final List<FxPacket> PENDING = new ArrayList<>();
   /** Thunder God Mode: entity id -> {start, end}; gloves: entity id -> {start, end}. Fed by shapes 24 and 25. */
   static final java.util.Map<Integer, double[]> THUNDER = new java.util.HashMap<>();
   static final java.util.Map<Integer, double[]> GLOVES = new java.util.HashMap<>();

   static void localBolt(Vec3 a, Vec3 b, float w, int life, int branches) {
      FxPacket q = new FxPacket();
      q.shape = 23; q.palette = (byte)boltPal; q.life = life; q.c = a; q.d = b.subtract(a); q.r = w; q.sweep = branches;
      q.id = java.util.concurrent.ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE);
      PENDING.add(q);
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
            OPACITY = com.example.arcaneaddon.AddonClientConfig.effectOpacity();
            ALPHA_NOW = OPACITY;
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
                  // a per-tick copy is normally replaced by the next one at age ~1; if that one is late, this one keeps
                  // gliding along its path and fades out gently instead of freezing and then jumping
                  ALPHA_NOW = perTick && age > 1.2 ? OPACITY * (float)Mth.clamp((2.5 - age) / 1.3, 0.0, 1.0) : OPACITY;
                  // slashes are all drawn like Spirit of Zephyr's - glowing, in their own colour - except Yami's and
                  // Asta's darkness, which stays a black blade with a glowing edge
                  slashGlow = sh == 13 && f.p.palette != 1 && f.p.palette != 10;
                  lightPal = ((sh == 0 || sh == 1 || sh == 2 || sh == 13) && isLight(f.p.palette)) || slashGlow ? f.p.palette : -1;
                  switch (f.p.shape) {
                     case 0:
                        FxShapes.wall(vc, m, n, cam, f.p, age, env);
                        break;
                     case 1:
                        FxShapes.crescentFx(vc, m, n, cam, f.p, age, env);
                        break;
                     case 2:
                        beam(vc, m, n, cam, f.p.c, f.p.c.add(f.p.d), f.p.r * (1.0F - 0.55F * (float)(age / life)), f.p.palette, env, f.p.h > 0.5F ? 0.0 : 0.35);
                        break;
                     case 13:
                        FxShapes.slashFx(vc, m, n, cam, f.p, age, life);
                        break;
                     case 19:
                        FxShapes.mercurySphere(vc, m, n, cam, f.p, age, life);
                        break;
                     case 20:
                        FxShapes.fireballFx(vc, m, n, cam, f.p, age, life);
                        break;
                     case 23:
                        FxShapes.boltFx(m, cam, f.p, age, life);
                        break;
                     case 7:
                        FxShapes.boxFx(vc, m, n, cam, f, age, life);
                        break;
                     case 8:
                        FxShapes.spikeFx(vc, m, n, cam, f, age, life);
                        break;
                     case 28:
                        FxShapes.tornadoFx(m, cam, f.p, age, life);
                        break;
                     case 29:
                        FxShapes.gustFx(m, cam, f.p, age, life);
                        break;
                     case 30:
                        FxShapes.boneFx(vc, m, n, cam, f, age, life);
                        break;
                     case 33:
                        FxShapes.spaceCracks(vc, m, n, cam, f.p, age, life);
                  }
                  lightPal = -1;
                  slashGlow = false;
                  ALPHA_NOW = OPACITY;
               }
            }

            ORBITS.removeIf(o -> now > o[10] + o[11] + o[9] * o[2] + 1.0);
            WHIPS.removeIf(o -> now > o[1] + o[2] + 4.0);
            for (double[] o : WHIPS) FxShapes.whipsFx(vc, m, n, cam, mc, o, now, pt);
            for (double[] o : ORBITS) FxShapes.orbitFx(vc, m, n, cam, mc, o, now, pt);
            float pulse = 0.75F + 0.25F * (float)Math.sin(now * 0.35);

            for (Player p : mc.level.players()) {
               if (!p.isSpectator() && !p.isInvisible() && (p != mc.player || !mc.options.getCameraType().isFirstPerson())) {
                  FxForms.heldAura(vc, m, n, cam, p, pt, pulse, p.getMainHandItem().getItem(), true);
                  FxForms.heldAura(vc, m, n, cam, p, pt, pulse, p.getOffhandItem().getItem(), false);
                  double[] form = DEVIL.get(p.getId());
                  if (form != null) FxForms.devilForm(vc, m, n, cam, p, pt, now, form);
                  double[] sp = SPIRIT.get(p.getId());
                  if (sp != null) FxForms.spiritForm(vc, m, n, cam, p, pt, now, sp);
                  double[] th = THUNDER.get(p.getId()), gl = GLOVES.get(p.getId());
                  boolean thOn = th != null && now <= th[1], glOn = gl != null && now <= gl[1];
                  if (thOn || glOn) FxForms.thunderBody(m, cam, p, pt, now, thOn, glOn);
               }
            }
            DEVIL.values().removeIf(f -> now > f[1] + 20.0);
            nearFade = false;
            for (java.util.Map.Entry<Integer, double[]> ce : COCOON.entrySet()) {
               net.minecraft.world.entity.Entity ent = mc.level.getEntity(ce.getKey());
               if (ent != null) FxForms.cocoon(vc, m, n, cam, ent, pt, now, ce.getValue());
            }
            COCOON.values().removeIf(f -> now > f[1] + 6.0);
            for (java.util.Map.Entry<Integer, double[]> ze : ZONES.entrySet()) {
               net.minecraft.world.entity.Entity ent = mc.level.getEntity(ze.getKey());
               if (ent != null) FxZones.zoneField(vc, m, n, cam, ent, pt, now, ze.getValue());
            }
            nearFade = true;

            for (FxRenderer.AuraDraw a : AURAS) {
               beam(vc, m, n, cam, a.a, a.b, a.width, a.pal, a.alpha * pulse, 0.5);
            }

            AURAS.clear();
            nearFade = false;
            // open grimoires: black-flame aura (smoke in the translucent pass, red underglow in the glow pass)
            for (FxRenderer.BookDraw b : BOOKS) {
               FxBooks.book(ps, vc, cam, b);
            }
            BOOKS.clear();
            buf.endBatch(rt);
            GLOW_SRC.endBatch(GlowRenderType.GLOW);
            gv = null;
            DarkSmoke.render(ps, e.getCamera(), buf, pt);
            PixelFx.render(ps, e.getCamera(), buf, pt);
            if (!FxBooks.PAGES.isEmpty()) {   // turning pages, drawn with the book's own page art (front = right page, back = left page)
               for (int side = 0; side < 2; side++) {
                  RenderType prt = RenderType.entityCutout(side == 0 ? FxBooks.PAGE_R : FxBooks.PAGE_L);
                  VertexConsumer pv = buf.getBuffer(prt);
                  for (FxBooks.PageDraw pd : FxBooks.PAGES) FxBooks.pageQuad(pv, pd, side == 1);
                  buf.endBatch(prt);
               }
               FxBooks.PAGES.clear();
            }
         }
      }
   }

   /** How far a moving solid has travelled: stops when its move time is up, or where it was killed. */
   /**
    * Things re-sent every tick (projectiles, orbiting weapons, the tornado...) are one object to the eye: a new copy
    * replaces the copy it continues (same kind, same size, right where the old one has got to), so there is never a
    * gap (the old copy waits in place until the next arrives) and never a doubled ghost.
    */
   static void replaceTrack(FxRenderer.Fx nf) {
      FxPacket np = nf.p;
      FxRenderer.Fx best = null;
      double bestD = Double.MAX_VALUE;
      for (FxRenderer.Fx f : TRACK) {
         FxPacket p = f.p;
         if (p.life > 1 || f.killAge >= 0.0 || p.shape != np.shape || p.palette != np.palette) continue;
         if (Math.abs(p.r - np.r) > Math.max(0.05, np.r * 0.25) || Math.abs(p.r2 - np.r2) > Math.max(0.05, Math.abs(np.r2) * 0.25)) continue;
         double age = nf.born - f.born;
         if (age < 0.5 || age > 3.0) continue;
         Vec3 at = (p.shape == 7 || p.shape == 8 || p.shape == 30) ? travelled(f, age) : p.c.add(p.v.scale(mv(p, age)));
         double d = at.distanceTo(np.c);
         double tol = 0.6 + np.v.length() * 0.6;
         if (d < tol && d < bestD) { bestD = d; best = f; }
      }
      if (best != null) {   // dropped: the new copy takes over
         best.killAge = -2.0;
         FX.remove(best);
         TRACK.remove(best);
      }
   }

   static record AuraDraw(Vec3 a, Vec3 b, float width, int pal, float alpha) {
   }

   static record BookDraw(Vec3 pos, float yaw, float tilt, float scale, float alpha, float flipCycle, float hw, float hh, float vDeg, int pal, int style, int light) {
   }

   static class Fx {
      FxPacket p;
      double born;
      double killAge = -1.0;
   }
}
