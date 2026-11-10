package com.ironspellgrimoire.client;

import com.ironspellgrimoire.registry.AddonItemRegistry;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.redspace.ironsspellbooks.api.util.Utils;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent.Stage;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * The grimoire of every addon book: small and closed on your hip; when you cast it floats open beside you as the
 * real 3D book with its own aura. It is anchored to your body (not the camera) and trails a little behind your
 * movement and turning, so it feels like it is floating after you. In first person it hovers low at your left.
 * Summoning Asta's swords draws the blade up out of the open pages, hilt first, then into your hand.
 */
@EventBusSubscriber(modid = "iron_spell_grimoire", value = {Dist.CLIENT})
public class GrimoireFloatRenderer {
   private static final float HIP_SCALE = 0.4F;
   private static final float OPEN_SCALE = 0.6F;
   private static final float FP_SCALE = 0.36F;
   /** How quickly the floating book catches up with where it should be (per tick). Lower = more lag. */
   private static final double FOLLOW = 0.3;
   private static final float TURN = 0.14F;

   private static final Map<Integer, ItemStack> BOOK_STACK = new HashMap<>();
   private static final Set<Integer> BURSTED = new HashSet<>();
   private static final Map<Integer, Long> ANIMS = new HashMap<>();
   private static final Map<Integer, Long> OPEN_UNTIL = new HashMap<>();
   private static final Map<Integer, float[]> PROGRESS = new HashMap<>();
   private static final Map<Integer, float[]> SMOOTH_YAW = new HashMap<>();
   private static final Map<Integer, Vec3[]> SMOOTH_POS = new HashMap<>();
   private static final Set<Integer> HOLDERS = new HashSet<>();
   private static final Set<Integer> OFFHAND_ANIMS = new HashSet<>();

   public static void startAnim(int entityId, boolean offhand) {
      ClientLevel level = Minecraft.getInstance().level;
      if (level != null) {
         if (offhand) OFFHAND_ANIMS.add(entityId);
         else OFFHAND_ANIMS.remove(entityId);
         ANIMS.put(entityId, level.getGameTime());
         openBook(entityId);
      }
   }

   /** player id -> game time of their last cast: the grimoire flips quickly through its pages to the spell. */
   private static final Map<Integer, Long> FLIP = new HashMap<>();
   private static final int FLIP_TICKS = 14;

   public static void castFlip(int entityId) {
      ClientLevel level = Minecraft.getInstance().level;
      if (level == null) return;
      FLIP.put(entityId, level.getGameTime());
      openBook(entityId);
   }

   /** The page-turn cycle while a cast flips the book: three pages riffle over in quick succession (else -1). */
   private static float castCycle(int id, double time) {
      Long t = FLIP.get(id);
      if (t == null) return -1.0F;
      double since = time - t;
      if (since < 0 || since > FLIP_TICKS) return -1.0F;
      return (float) (4.0 + since * 2.3);   // FxBooks flips page i over cycle 4+5i .. 18+5i
   }

   /** The book stays open this long (10 s) after the last hit given, hit taken or spell cast. */
   public static final long COMBAT_TICKS = 200L;

   public static void openBook(int entityId) {
      ClientLevel level = Minecraft.getInstance().level;
      if (level != null) OPEN_UNTIL.put(entityId, level.getGameTime() + COMBAT_TICKS);   // combat: open for 10 s after the last hit / cast
   }

   /** Every grimoire of this addon floats open while you cast (Black, Dark, Steel and the themed ones). */
   public static boolean isAddonGrimoire(ItemStack s) {
      return s != null && !s.isEmpty() && "iron_spell_grimoire".equals(BuiltInRegistries.ITEM.getKey(s.getItem()).getNamespace());
   }

   /** {palette, aura style} per grimoire - see FxRenderer.bookAura. */
   private static int[] aura(Item item) {
      if (item == AddonItemRegistry.BLACK_GRIMOIRE.get()) return new int[]{0, 0};
      if (item == AddonItemRegistry.DARK_GRIMOIRE.get()) return new int[]{1, 1};
      if (item == AddonItemRegistry.STEEL_GRIMOIRE.get()) return new int[]{7, 2};
      if (item == AddonItemRegistry.WIND_GRIMOIRE.get()) return new int[]{5, 3};
      if (item == AddonItemRegistry.MERCURY_GRIMOIRE.get()) return new int[]{11, 2};
      if (item == AddonItemRegistry.FIRE_GRIMOIRE.get()) return new int[]{2, 4};
      if (item == AddonItemRegistry.LIGHTNING_GRIMOIRE.get()) return new int[]{4, 4};
      if (item == AddonItemRegistry.HELLFIRE_GRIMOIRE.get()) return new int[]{14, 0};   // crimson hellfire, like her flames
      if (item == AddonItemRegistry.BONE_GRIMOIRE.get()) return new int[]{15, 2};
      if (item == AddonItemRegistry.THREAD_GRIMOIRE.get()) return new int[]{16, 3};
      if (item == AddonItemRegistry.SPACE_GRIMOIRE.get()) return new int[]{17, 3};
      if (item == AddonItemRegistry.WATER_GRIMOIRE.get()) return new int[]{19, 3};
      if (item == AddonItemRegistry.MIRROR_GRIMOIRE.get()) return new int[]{20, 2};
      if (item == AddonItemRegistry.RIFT_GRIMOIRE.get()) return new int[]{21, 4};
      if (item == AddonItemRegistry.FLAME_SPIRIT_GRIMOIRE.get()) return new int[]{22, 4};
      if (item == AddonItemRegistry.LION_FLAME_GRIMOIRE.get()) return new int[]{23, 0};
      if (item == AddonItemRegistry.WORLD_TREE_GRIMOIRE.get()) return new int[]{24, 3};
      if (item == AddonItemRegistry.SLASH_GRIMOIRE.get()) return new int[]{25, 2};
      if (item == AddonItemRegistry.EARTH_GRIMOIRE.get()) return new int[]{26, 2};
      if (item == AddonItemRegistry.BEAST_GRIMOIRE.get()) return new int[]{27, 3};
      if (item == AddonItemRegistry.COTTON_GRIMOIRE.get()) return new int[]{28, 4};
      if (item == AddonItemRegistry.TIME_GRIMOIRE.get()) return new int[]{8, 3};
      String id = BuiltInRegistries.ITEM.getKey(item).getPath();
      return switch (id) {
         default -> new int[]{8, 4};
      };
   }

   /**
    * The open 3D book, centred, in the frame FxRenderer.book draws its aura in: local +z (the open model's page
    * side) faces you.
    */
   private static void renderOpen(Minecraft mc, PoseStack ps, BufferSource buf, ItemStack book, int seed, int light) {
      mc.getItemRenderer().renderStatic(book, ItemDisplayContext.HEAD, light, OverlayTexture.NO_OVERLAY, ps, buf, mc.level, seed);
   }

   private static float ease(float x) {
      x = Mth.clamp(x, 0.0F, 1.0F);
      return x * x * (3.0F - 2.0F * x);
   }

   private static Vec3 base(Player p, float pt) {
      return new Vec3(Mth.lerp((double)pt, p.xo, p.getX()), Mth.lerp((double)pt, p.yo, p.getY()), Mth.lerp((double)pt, p.zo, p.getZ()));
   }

   private static Vec3 fwdOf(float yaw) {
      double r = Math.toRadians((double)yaw);
      return new Vec3(-Math.sin(r), 0.0, Math.cos(r));
   }

   private static Vec3 leftOf(float yaw) {
      double r = Math.toRadians((double)yaw);
      return new Vec3(Math.cos(r), 0.0, Math.sin(r));
   }

   private static boolean firstPerson(Minecraft mc, Player p) {
      return p == mc.player && mc.options.getCameraType().isFirstPerson();
   }

   /** Where the open book wants to be: beside you in third person, low at your left in first person. Follows your body, not your pitch. */
   private static Vec3 openTarget(Player p, float yaw, boolean fp, double time) {
      Vec3 f = fwdOf(yaw), l = leftOf(yaw);
      double bob = Math.sin(time * 0.08 + (double)p.getId()) * (fp ? 0.015 : 0.05);
      if (fp) return p.position().add(0.0, p.getEyeHeight() - 0.5 + bob, 0.0).add(f.scale(0.85)).add(l.scale(0.5));
      return p.position().add(0.0, (double)p.getBbHeight() * 0.7 + bob, 0.0).add(f.scale(1.7)).add(l.scale(1.35));
   }

   private static Vec3 hipPosition(Player p, float pt, float bodyYaw) {
      return base(p, pt).add(0.0, (double)p.getBbHeight() * 0.45, 0.0).add(leftOf(bodyYaw).scale(0.36)).add(fwdOf(bodyYaw).scale(-0.05));
   }

   // ------------------------------------------------------------------ the summoned sword's flight
   /** Matches SummonQueue.DELAY_TICKS: the real sword is put in the hand the tick the flying one arrives. */
   private static final int SWORD_TICKS = 16;
   /** rise out of the pages | hang and turn to the hand | fly | caught */
   private static final float RISE = 0.36F, HANG = 0.54F, FLY = 0.86F;
   /** The book takes this long to flip open; the sword starts rising once it is open. */
   private static final int OPEN_TICKS = 4;

   /** 0..1 through the sword's flight, from when the book is open to the catch (can be below 0 / above 1). */
   private static float swordT(double elapsed) { return (float) ((elapsed - OPEN_TICKS) / (SWORD_TICKS - OPEN_TICKS)); }
   /** The sword item model's blade axis (hilt to tip) in item space. */
   private static final org.joml.Vector3f BLADE = new org.joml.Vector3f(0.70710677F, 0.70710677F, 0.0F);

   private record Pose(Vec3 pos, Vec3 tip, org.joml.Quaternionf rot, float scale) {}

   private static boolean holding(Player p, boolean off) {
      return off ? p.getOffhandItem().is(AddonItemRegistry.DEMON_DWELLER_SWORD.get()) : p.getMainHandItem().is(AddonItemRegistry.DEMON_SLAYER_SWORD.get());
   }

   /** Where the hand that takes the sword is: reaching out in front (third person), low at the side of the view (first). */
   private static Vec3 handPos(Player p, float pt, boolean off) {
      Minecraft mc = Minecraft.getInstance();
      float yaw = p.getViewYRot(pt);
      Vec3 left = leftOf(yaw);
      if (firstPerson(mc, p)) {
         Vec3 eye = p.getEyePosition(pt), look = p.getViewVector(pt);
         return eye.add(look.scale(0.7)).add(left.scale(off ? 0.38 : -0.38)).add(0.0, -0.34, 0.0);
      }
      return base(p, pt).add(0.0, p.getEyeHeight() * 0.8, 0.0).add(fwdOf(yaw).scale(0.55)).add(left.scale(off ? 0.4 : -0.4));
   }

   private static org.joml.Quaternionf aim(Vec3 tip, float spin) {
      org.joml.Vector3f d = new org.joml.Vector3f((float) tip.x, (float) tip.y, (float) tip.z).normalize();
      org.joml.Quaternionf q = new org.joml.Quaternionf().rotationTo(BLADE, d);
      if (spin != 0.0F) q.premul(new org.joml.Quaternionf().rotationAxis(spin, d.x, d.y, d.z));
      return q;
   }

   private static Vec3 slerpDir(Vec3 a, Vec3 b, float k) {
      Vec3 v = a.scale(1.0F - k).add(b.scale(k));
      return v.lengthSqr() < 1.0E-4 ? b : v.normalize();
   }

   /**
   * The sword at time t (0..1):
   *   rise  - it grows up out of the open pages HILT FIRST, tip still on the pages, turning slowly as it comes
   *   hang  - it hangs for a beat above the book, drifts back a little (wind-up) and swings its hilt toward your hand
   *   fly   - it shoots to the hand, hilt leading, faster and faster along a short arc
   *   catch - the hand closes on the grip; the blade swings up into the held angle with a little recoil
   */
   private static Pose swordPose(Player p, float pt, Vec3 book, boolean off, float t) {
      float half = off ? 1.05F : 1.45F;
      Vec3 up = new Vec3(0.0, 1.0, 0.0), down = new Vec3(0.0, -1.0, 0.0);
      Vec3 hand = handPos(p, pt, off);
      Vec3 top = book.add(0.0, half + 0.12, 0.0);
      Vec3 toHand = hand.subtract(top);
      Vec3 dirHand = toHand.lengthSqr() < 1.0E-4 ? fwdOf(p.getViewYRot(pt)) : toHand.normalize();
      Vec3 held = up.scale(0.8).add(fwdOf(p.getViewYRot(pt)).scale(0.6)).normalize();   // tip up and forward, as held
      if (t < RISE) {
         float u = t / RISE, e = ease(u);
         float sc = 0.3F + 0.7F * (1.0F - (1.0F - u) * (1.0F - u));   // grows fast, settles
         Vec3 pos = book.add(up.scale(half * sc * 0.92F + 0.06F + 0.12F * e));
         return new Pose(pos, down, aim(down, (1.0F - e) * 3.6F), sc);
      }
      if (t < HANG) {
         float u = (t - RISE) / (HANG - RISE), e = ease(u);
         Vec3 pos = top.add(0.0, Math.sin(u * Math.PI) * 0.06, 0.0).subtract(dirHand.scale(0.18 * e));   // pulled back: the wind-up
         Vec3 tip = slerpDir(down, dirHand.scale(-1.0), e);                                              // hilt turns to the hand
         return new Pose(pos, tip, aim(tip, 0.0F), 1.0F);
      }
      Vec3 from = top.subtract(dirHand.scale(0.18));
      if (t < FLY) {
         float u = (t - HANG) / (FLY - HANG);
         float m = u * u * (1.6F - 0.6F * u);                         // accelerates into the hand
         Vec3 ctrl = from.add(hand).scale(0.5).add(0.0, 0.3, 0.0);
         float im = 1.0F - m;
         Vec3 pos = from.scale(im * im).add(ctrl.scale(2.0F * im * m)).add(hand.scale(m * m));
         Vec3 vel = ctrl.subtract(from).scale(2.0F * im).add(hand.subtract(ctrl).scale(2.0F * m));
         Vec3 tip = vel.lengthSqr() < 1.0E-4 ? dirHand.scale(-1.0) : vel.normalize().scale(-1.0);    // hilt first
         return new Pose(pos, tip, aim(tip, 0.0F), 1.0F);
      }
      float u = (t - FLY) / (1.0F - FLY), e = ease(Math.min(1.0F, u * 1.4F));
      Vec3 arrive = hand.subtract(ctrlDir(from, hand)).normalize();
      double recoil = Math.sin(Math.min(1.0F, u) * Math.PI) * 0.09;   // the hand gives a little as it catches
      Vec3 tip = slerpDir(arrive.scale(-1.0), held, e);
      return new Pose(hand.add(arrive.scale(recoil)), tip, aim(tip, 0.0F), 1.0F);
   }

   private static Vec3 ctrlDir(Vec3 from, Vec3 hand) { return from.add(hand).scale(0.5).add(0.0, 0.3, 0.0); }

   /** Pixel-art smoke, motes and speed streaks around the flying sword, spawned per tick (not per frame). */
   private static void swordFx(Player p, Vec3 book, boolean off, float t, float tPrev) {
      java.util.concurrent.ThreadLocalRandom r = java.util.concurrent.ThreadLocalRandom.current();
      Pose now = swordPose(p, 1.0F, book, off, Math.min(1.0F, t));
      float half = (off ? 1.05F : 1.45F) * now.scale;
      if (t < RISE) {
         // anti-magic smoke curling up the blade as it leaves the pages; motes sucked in from around the book
         for (int i = 0; i < 2; i++) {
            Vec3 at = now.pos.add(now.tip.scale(r.nextDouble(-half, half) * 0.8)).add(r.nextDouble(-0.1, 0.1), 0, r.nextDouble(-0.1, 0.1));
            DarkSmoke.smoke(at, new Vec3(r.nextDouble(-0.01, 0.01), 0.025, r.nextDouble(-0.01, 0.01)), 0.16F, 12);
         }
         double a = r.nextDouble(Math.PI * 2);
         Vec3 from = book.add(Math.cos(a) * 0.7, r.nextDouble(-0.1, 0.3), Math.sin(a) * 0.7);
         DarkSmoke.mote(from, book.subtract(from).scale(0.12), 0.07F, 8);
      } else if (t < HANG) {
         if (r.nextInt(2) == 0) DarkSmoke.smoke(now.pos.add(r.nextDouble(-0.15, 0.15), r.nextDouble(-half, half) * 0.6, r.nextDouble(-0.15, 0.15)), new Vec3(0, 0.015, 0), 0.13F, 10);
      } else if (t < FLY + 0.05F) {
         // speed streaks and a smoke trail left along the path
         Pose was = swordPose(p, 1.0F, book, off, Math.min(1.0F, Math.max(HANG, tPrev)));
         Vec3 step = now.pos.subtract(was.pos);
         for (int i = 0; i < 4; i++) {
            Vec3 at = was.pos.add(step.scale(r.nextDouble())).add(r.nextDouble(-0.12, 0.12), r.nextDouble(-0.12, 0.12), r.nextDouble(-0.12, 0.12));
            DarkSmoke.streakTinted(at, step.scale(0.25), 0.07F, 5, 0.85F, 0.85F, 0.9F);
            DarkSmoke.smoke(at, step.scale(-0.05), 0.12F, 9);
         }
      }
      if (tPrev < FLY && t >= FLY) {
         // the catch: a quick burst of black motes from the grip
         for (int i = 0; i < 14; i++) {
            double a = i * Math.PI * 2 / 14;
            Vec3 v = new Vec3(Math.cos(a), r.nextDouble(-0.4, 0.6), Math.sin(a)).scale(0.09);
            DarkSmoke.mote(now.pos, v, 0.08F, 8);
         }
         DarkSmoke.smoke(now.pos, Vec3.ZERO, 0.3F, 10);
      }
   }

   @SubscribeEvent
   public static void onClientTick(ClientTickEvent e) {
      if (e.phase != Phase.END) return;
      Minecraft mc = Minecraft.getInstance();
      HOLDERS.clear();
      if (mc.level == null) {
         ANIMS.clear();
         Swing.clear();
         OPEN_UNTIL.clear();
         PROGRESS.clear();
         SMOOTH_POS.clear();
         return;
      }
      if (mc.isPaused()) return;
      long now = mc.level.getGameTime();
      RandomSource rnd = mc.level.random;
      for (Player p : mc.level.players()) {
         ItemStack equipped = Utils.getPlayerSpellbookStack(p);
         boolean has = isAddonGrimoire(equipped);
         if (has) BOOK_STACK.put(p.getId(), equipped.copyWithCount(1));
         float[] pr = PROGRESS.computeIfAbsent(p.getId(), k -> new float[]{0.0F, 0.0F});
         pr[0] = pr[1];
         boolean open = has && (OPEN_UNTIL.getOrDefault(p.getId(), 0L) > now || ANIMS.containsKey(p.getId()));
         boolean quick = ANIMS.containsKey(p.getId()) || (FLIP.containsKey(p.getId()) && now - FLIP.get(p.getId()) < FLIP_TICKS);
         pr[1] = Mth.clamp(pr[1] + (open ? (quick ? 0.3F : 0.12F) : -0.08F), 0.0F, 1.0F);   // a cast flips it open fast   // a summon flips it open fast
         if (has) { HOLDERS.add(p.getId()); Swing.tick(p, p.yBodyRot); }

         // the book turns after you and floats after you, a little behind
         float tgt = p.getYRot();
         float[] sy = SMOOTH_YAW.computeIfAbsent(p.getId(), k -> new float[]{tgt, tgt});
         sy[0] = sy[1];
         sy[1] += Mth.wrapDegrees(tgt - sy[1]) * TURN;
         Vec3 target = openTarget(p, sy[1], firstPerson(mc, p), (double)now);
         Vec3[] sp = SMOOTH_POS.computeIfAbsent(p.getId(), k -> new Vec3[]{target, target});
         sp[0] = sp[1];
         sp[1] = sp[1].distanceToSqr(target) > 16.0 || pr[0] <= 0.0F ? target : sp[1].add(target.subtract(sp[1]).scale(FOLLOW));

         if (has && !p.isSpectator() && !p.isInvisible() && pr[1] > 0.6F) {
            Vec3 c = sp[1];
            if (pr[0] <= 0.6F) {
               mc.level.playLocalSound(c.x, c.y, c.z, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.6F, 0.7F, false);
               mc.level.playLocalSound(c.x, c.y, c.z, SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8F, 0.8F, false);
            }
            long cyc = (now + (long)p.getId() * 13L) % 50L;
            if (pr[1] >= 1.0F && (cyc == 4L || cyc == 9L || cyc == 14L)) {
               mc.level.playLocalSound(c.x, c.y, c.z, SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.5F, 0.9F + rnd.nextFloat() * 0.3F, false);
            }
         }
         Long fl = FLIP.get(p.getId());
         if (has && fl != null && !p.isSpectator() && (now - fl == 2L || now - fl == 4L || now - fl == 6L)) {   // the riffle of pages
            Vec3 c = sp[1];
            mc.level.playLocalSound(c.x, c.y, c.z, SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.55F, 1.1F + rnd.nextFloat() * 0.3F, false);
         }
         Long st = ANIMS.get(p.getId());
         if (has && st != null && !p.isInvisible()) {
            boolean off = OFFHAND_ANIMS.contains(p.getId());
            float t = swordT(now - st), tPrev = swordT(now - 1 - st);
            if (t >= 0.0F && t <= 1.05F && !(t >= 1.0F && holding(p, off))) swordFx(p, sp[1], off, t, Math.max(0.0F, tPrev));
         }
         if (has && st != null && now - st <= 1L && BURSTED.add(p.getId())) {
            Vec3 cx = sp[1].add(0.0, 0.1, 0.0);
            mc.level.playLocalSound(cx.x, cx.y, cx.z, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.7F, 0.5F, false);
         }
      }
      BURSTED.removeIf(id -> !ANIMS.containsKey(id));
      FLIP.values().removeIf(t -> now - t > FLIP_TICKS + 2L);
      ANIMS.values().removeIf(s -> now - s > SWORD_TICKS + 6L);   // a few ticks of grace for the real sword to arrive
      PROGRESS.keySet().removeIf(id -> !HOLDERS.contains(id) && PROGRESS.get(id)[1] <= 0.0F);
      SMOOTH_YAW.keySet().retainAll(PROGRESS.keySet());
      SMOOTH_POS.keySet().retainAll(PROGRESS.keySet());
      BOOK_STACK.keySet().retainAll(HOLDERS);
      Swing.retain(HOLDERS);
   }

   @SubscribeEvent
   public static void onRender(RenderLevelStageEvent e) {
      if (e.getStage() != Stage.AFTER_ENTITIES) return;
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null || HOLDERS.isEmpty()) return;
      float pt = e.getPartialTick();
      PoseStack ps = e.getPoseStack();
      Vec3 cam = e.getCamera().getPosition();
      BufferSource buf = mc.renderBuffers().bufferSource();
      ItemStack fallback = new ItemStack((ItemLike)AddonItemRegistry.BLACK_GRIMOIRE.get());
      ItemStack sword = new ItemStack((ItemLike)AddonItemRegistry.DEMON_SLAYER_SWORD.get());
      ItemStack twin = new ItemStack((ItemLike)AddonItemRegistry.DEMON_DWELLER_SWORD.get());
      double time = (double)mc.level.getGameTime() + pt;

      for (Player p : mc.level.players()) {
         if (!HOLDERS.contains(p.getId()) || p.isSpectator() || p.isInvisible()) continue;
         float[] pr = PROGRESS.get(p.getId());
         Vec3[] sp = SMOOTH_POS.get(p.getId());
         if (pr == null || sp == null) continue;
         ItemStack book = BOOK_STACK.getOrDefault(p.getId(), fallback);
         float prog = ease(Mth.lerp(pt, pr[0], pr[1]));
         boolean fp = firstPerson(mc, p);
         float yaw = p.getViewYRot(pt);
         float[] sy = SMOOTH_YAW.get(p.getId());
         float yawS = sy == null ? yaw : sy[0] + Mth.wrapDegrees(sy[1] - sy[0]) * pt;
         float bodyYaw = Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot);
         Vec3 open = sp[0].add(sp[1].subtract(sp[0]).scale((double)pt));
         // where it comes from: the hip (third person) or from below the view (first person)
         Vec3 from = fp ? base(p, pt).add(0.0, p.getEyeHeight() - 1.1, 0.0).add(fwdOf(yawS).scale(0.4)).add(leftOf(yawS).scale(0.4))
                        : hipPosition(p, pt, bodyYaw);
         Vec3 bookPos = from.add(open.subtract(from).scale((double)prog));
         int wl = LevelRenderer.getLightColor(mc.level, BlockPos.containing(bookPos));
         int light = LightTexture.pack(Math.max(LightTexture.block(wl), 5), Math.max(LightTexture.sky(wl), 2));
         int[] au = aura(book.getItem());

         ps.pushPose();
         ps.translate(bookPos.x - cam.x, bookPos.y - cam.y, bookPos.z - cam.z);
         if (!fp && prog < 0.5F) {
            // closed on the hip, small enough to sit against the leg; it swings as you walk
            float s = HIP_SCALE * (1.0F + 0.25F * prog);
            ps.mulPose(Axis.YP.rotationDegrees(-bodyYaw));
            float sway = (float)Math.sin(time * 0.05 + (double)p.getId()) * 0.8F + Swing.angle(p.getId(), pt);   // Moonlight pendulum
            ps.mulPose(Axis.ZP.rotationDegrees(sway));
            ps.mulPose(Axis.XP.rotationDegrees(90.0F));
            ps.mulPose(Axis.YP.rotationDegrees(prog * 120.0F));
            ps.scale(s, s, s);
            ps.translate(-0.0625, 0.21875, 0.0);
            mc.getItemRenderer().renderStatic(book, ItemDisplayContext.NONE, light, OverlayTexture.NO_OVERLAY, ps, buf, mc.level, p.getId());
         } else if (prog > 0.02F) {
            // open, floating: the solid 3D book plus its aura
            float k = ease(fp ? prog : (prog - 0.5F) * 2.0F);
            float kb = k - 1.0F;
            float back = 1.0F + 2.70158F * kb * kb * kb + 1.70158F * kb * kb;
            float s = (fp ? FP_SCALE : OPEN_SCALE) * (0.5F + 0.5F * back);
            float tilt = (fp ? 42.0F : 35.0F) * k;
            float turn = fp ? 18.0F : 0.0F;   // in first person it angles in toward you a little
            ps.mulPose(Axis.YP.rotationDegrees(-(yawS - turn)));
            ps.mulPose(Axis.YP.rotationDegrees(180.0F));
            ps.mulPose(Axis.XP.rotationDegrees(-tilt));
            ps.scale(s, s, s);
            renderOpen(mc, ps, buf, book, p.getId(), light);
            float cast = castCycle(p.getId(), time);
            float cyc = k > 0.8F ? (cast >= 0.0F ? cast : (float)((mc.level.getGameTime() + (long)p.getId() * 13L) % 50L) + pt) : -1.0F;
            boolean mastered = MasteryClient.enabled() && com.ironspellgrimoire.server.GrimoireMastery.bookOf(book) != null
                  && com.ironspellgrimoire.server.GrimoireMastery.level(book) >= com.ironspellgrimoire.server.GrimoireMastery.MAX_LEVEL;
            if (book.is(AddonItemRegistry.HELLFIRE_GRIMOIRE.get()) && java.util.concurrent.ThreadLocalRandom.current().nextFloat() < 0.25F) {
               // crimson flames licking up off the open pages
               java.util.concurrent.ThreadLocalRandom fr = java.util.concurrent.ThreadLocalRandom.current();
               Vec3 fp0 = bookPos.add(fr.nextDouble(-0.25, 0.25), 0.05, fr.nextDouble(-0.25, 0.25));
               DarkSmoke.crimsonFlame(fp0, new Vec3(0, fr.nextDouble(0.03, 0.07), 0), (float) fr.nextDouble(0.08, 0.16), fr.nextInt(8, 14));
            }
            FxRenderer.queueBook(bookPos, yawS - turn, tilt, s, k * (mastered ? 1.5F : 1.0F), cyc, 0.46F, 0.375F, 22.5F, au[0], au[1], light);
            if (book.is(AddonItemRegistry.TIME_GRIMOIRE.get())) TimeFxClient.bookFan(bookPos, k);   // Julius: a little fan of pages turning round it
         }
         ps.popPose();

         // Asta's sword rising out of the open pages, hilt first, then flying hilt-first into the hand
         Long start = ANIMS.get(p.getId());
         if (start != null && prog >= 0.9F) {
            float t = swordT(mc.level.getGameTime() - start + pt);
            if (t < 0.0F) continue;
            boolean off = OFFHAND_ANIMS.contains(p.getId());
            if (t >= 1.0F && holding(p, off)) continue;   // the real sword is in the hand: the flying one is gone
            Pose sw = swordPose(p, pt, bookPos, off, Math.min(t, 1.0F));
            ps.pushPose();
            ps.translate(sw.pos.x - cam.x, sw.pos.y - cam.y, sw.pos.z - cam.z);
            ps.mulPose(sw.rot);
            ps.scale(0.9F * sw.scale, 0.9F * sw.scale, 0.9F * sw.scale);
            ps.translate(-0.582F, -0.582F, 0.0F);
            mc.getItemRenderer().renderStatic(off ? twin : sword, ItemDisplayContext.NONE, 15728880, OverlayTexture.NO_OVERLAY, ps, buf, mc.level, p.getId());
            ps.popPose();
         }
      }
      buf.endBatch();
   }
}
