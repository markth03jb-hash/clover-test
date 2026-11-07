package com.example.arcaneaddon.client;

import com.example.arcaneaddon.ArcaneAddon;
import com.example.arcaneaddon.item.BlackGrimoireItem;
import com.example.arcaneaddon.registry.AddonItemRegistry;
import com.example.arcaneaddon.server.SummonQueue;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Black Grimoire presentation.
 *  - Idle: the closed, strapped tome hangs on the owner's left hip.
 *  - Casting: it lifts off the hip, flies to the front-left, opens (tilted like a lectern) and trails crimson smoke.
 *  - Summon Greatsword: the sword rises out of the open book HANDLE FIRST, turns sideways and flies, handle leading, to the right hand.
 * After the last cast the book stays open a few seconds, then returns to the hip.
 */
@Mod.EventBusSubscriber(modid = ArcaneAddon.MODID, value = Dist.CLIENT)
public class GrimoireFloatRenderer {
    // ---- tuning knobs ----
    private static final double FORWARD = 1.7;       // open book: blocks in front of the player
    private static final double LEFT = 1.35;         // open book: blocks to the left
    private static final double HEIGHT = 0.70;       // open book: fraction of body height
    private static final float BOOK_SCALE = 0.8f;    // open book size
    private static final float TILT = 35f;           // open book lean-back (degrees)
    private static final int OPEN_TICKS = 60;        // stays open this long after the last cast
    private static final double HIP_SIDE = 0.40, HIP_BACK = 0.05, HIP_HEIGHT = 0.45;  // hip position
    private static final float HIP_SCALE = 0.7f;     // closed book size
    private static final float DENSITY = 1.0f;     // particle density multiplier: lower it (0.5) if the effects hurt your FPS
    private static final int FLIP_CYCLE = 50;       // ticks between page-flip bursts
    private static final float SWORD_SCALE = 0.9f;
    private static final float SWORD_CENTER = 0.278f; // sword model centre offset from its origin

    private static final DustParticleOptions CRIMSON = new DustParticleOptions(new Vector3f(0.75f, 0.04f, 0.10f), 1.4f);
    private static final DustParticleOptions DARK_RED = new DustParticleOptions(new Vector3f(0.25f, 0.0f, 0.04f), 1.9f);

    private static final DustColorTransitionOptions EMBER = new DustColorTransitionOptions(new Vector3f(1.0f, 0.15f, 0.1f), new Vector3f(0.1f, 0.0f, 0.05f), 1.3f);
    private static final Set<Integer> BURSTED = new HashSet<>();
    private static long swordFxTick = -1;

    private static int n(int base) { return Math.max(1, Math.round(base * DENSITY)); }

    private static final Map<Integer, Long> ANIMS = new HashMap<>();       // sword animation start times
    private static final Map<Integer, Long> OPEN_UNTIL = new HashMap<>();  // book stays open until this game time
    private static final Map<Integer, float[]> PROGRESS = new HashMap<>(); // [previous, current] 0 = on hip, 1 = open
    private static final Map<Integer, float[]> SMOOTH_YAW = new HashMap<>(); // [previous, current] lagging view yaw -> floaty follow
    private static final Set<Integer> HOLDERS = new HashSet<>();

    private static final Set<Integer> OFFHAND_ANIMS = new HashSet<>();   // animations that end in the LEFT hand (Demon Dweller Sword)

    public static void startAnim(int entityId, boolean offhand) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        if (offhand) OFFHAND_ANIMS.add(entityId); else OFFHAND_ANIMS.remove(entityId);
        ANIMS.put(entityId, level.getGameTime());
        openBook(entityId);
    }

    public static void openBook(int entityId) {
        var level = Minecraft.getInstance().level;
        if (level != null) OPEN_UNTIL.put(entityId, level.getGameTime() + OPEN_TICKS);
    }

    private static float ease(float x) { x = Mth.clamp(x, 0f, 1f); return x * x * (3 - 2 * x); }

    private static Vec3 base(Player p, float pt) {
        return new Vec3(Mth.lerp(pt, p.xo, p.getX()), Mth.lerp(pt, p.yo, p.getY()), Mth.lerp(pt, p.zo, p.getZ()));
    }

    private static Vec3 floatPosition(Player p, float pt, float yaw) {
        double r = Math.toRadians(yaw);
        Vec3 fwd = new Vec3(-Math.sin(r), 0, Math.cos(r)), left = new Vec3(Math.cos(r), 0, Math.sin(r));
        double bob = Math.sin((p.level().getGameTime() + pt) * 0.08 + p.getId()) * 0.05;
        return base(p, pt).add(0, p.getBbHeight() * HEIGHT + bob, 0).add(fwd.scale(FORWARD)).add(left.scale(LEFT));
    }

    private static Vec3 hipPosition(Player p, float pt, float bodyYaw) {
        double r = Math.toRadians(bodyYaw);
        Vec3 fwd = new Vec3(-Math.sin(r), 0, Math.cos(r)), left = new Vec3(Math.cos(r), 0, Math.sin(r));
        return base(p, pt).add(0, p.getBbHeight() * HIP_HEIGHT, 0).add(left.scale(HIP_SIDE)).add(fwd.scale(-HIP_BACK));
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        HOLDERS.clear();
        if (mc.level == null) { ANIMS.clear(); OPEN_UNTIL.clear(); PROGRESS.clear(); return; }
        if (mc.isPaused()) return;
        long now = mc.level.getGameTime();
        var rnd = mc.level.random;
        for (Player p : mc.level.players()) {
            ItemStack equipped = Utils.getPlayerSpellbookStack(p);   // can be null (e.g. right after joining a world)
            boolean has = equipped != null && equipped.getItem() instanceof BlackGrimoireItem;
            float[] pr = PROGRESS.computeIfAbsent(p.getId(), k -> new float[]{0f, 0f});
            pr[0] = pr[1];
            boolean open = has && (OPEN_UNTIL.getOrDefault(p.getId(), 0L) > now || ANIMS.containsKey(p.getId()));
            pr[1] = Mth.clamp(pr[1] + (open ? 0.12f : -0.08f), 0f, 1f);
            if (has) HOLDERS.add(p.getId());
            float tgt = p.getViewYRot(1f);
            float[] sy = SMOOTH_YAW.computeIfAbsent(p.getId(), k -> new float[]{tgt, tgt});
            sy[0] = sy[1];
            sy[1] += Mth.wrapDegrees(tgt - sy[1]) * 0.18f;   // the book glides after your view instead of snapping

            if (has && !p.isSpectator() && !p.isInvisible()) {
                float yaw = sy[1];
                Vec3 mv = p.getDeltaMovement();
                if (pr[1] > 0.6f) {
                    Vec3 c = floatPosition(p, 1f, yaw);
                    double t = now * 0.22;
                    // --- opening burst: shockwave rings + flash + sound ---
                    if (pr[0] <= 0.6f) {
                        for (int ring = 0; ring < 3; ring++) {
                            for (int i = 0; i < n(36); i++) {
                                double a = i * Math.PI * 2 / n(36);
                                double sp = 0.12 + ring * 0.09;
                                mc.level.addParticle(ring == 1 ? DARK_RED : EMBER, c.x, c.y, c.z,
                                        mv.x + Math.cos(a) * sp, mv.y + (ring - 1) * 0.04, mv.z + Math.sin(a) * sp);
                            }
                        }
                        mc.level.addParticle(ParticleTypes.FLASH, c.x, c.y, c.z, 0, 0, 0);
                        mc.level.playLocalSound(c.x, c.y, c.z, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.9f, 0.7f, false);
                        mc.level.playLocalSound(c.x, c.y, c.z, SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8f, 0.8f, false);
                    }
                    // --- two counter-rotating helices climbing off the book ---
                    for (int hx = 0; hx < 2; hx++) {
                        for (int i = 0; i < n(2); i++) {
                            double ph = (now * 0.045 + hx * 0.5 + i * 0.5) % 1.0;
                            double a = t * 1.6 * (hx == 0 ? 1 : -1) + hx * Math.PI + ph * 9;
                            double rad = 0.25 + ph * 0.35;
                            mc.level.addParticle(EMBER, c.x + Math.cos(a) * rad, c.y - 0.3 + ph * 1.5, c.z + Math.sin(a) * rad,
                                    mv.x, mv.y + 0.01, mv.z);
                        }
                    }
                    // --- two tilted orbit rings of dust ---
                    for (int ring = 0; ring < 2; ring++) {
                        for (int i = 0; i < n(4); i++) {
                            double a = t * (ring == 0 ? 1 : -1.3) + i * Math.PI * 2 / n(4);
                            double rad = ring == 0 ? 0.7 : 0.5, tilt = ring == 0 ? 0.35 : -0.5;
                            mc.level.addParticle(ring == 0 ? CRIMSON : DARK_RED, c.x + Math.cos(a) * rad, c.y + Math.cos(a) * tilt, c.z + Math.sin(a) * rad,
                                    mv.x - Math.sin(a) * 0.03, mv.y, mv.z + Math.cos(a) * 0.03);
                        }
                    }
                    // --- wisps, spores, glyphs, sparkles, smoke ---
                    for (int i = 0; i < n(3); i++) {
                        mc.level.addParticle(CRIMSON, c.x + (rnd.nextDouble() - 0.5) * 0.8, c.y - 0.2, c.z + (rnd.nextDouble() - 0.5) * 0.8,
                                mv.x, mv.y + 0.03 + rnd.nextDouble() * 0.03, mv.z);
                    }
                    for (int i = 0; i < n(2); i++) {
                        mc.level.addParticle(ParticleTypes.CRIMSON_SPORE, c.x + (rnd.nextDouble() - 0.5) * 1.0, c.y + (rnd.nextDouble() - 0.5) * 0.8,
                                c.z + (rnd.nextDouble() - 0.5) * 1.0, mv.x, mv.y - 0.01, mv.z);
                    }
                    for (int i = 0; i < n(2); i++) {
                        mc.level.addParticle(ParticleTypes.ENCHANT, c.x, c.y + 0.1, c.z,
                                (rnd.nextDouble() - 0.5) * 1.6, 0.8 + rnd.nextDouble() * 0.7, (rnd.nextDouble() - 0.5) * 1.6);
                    }
                    mc.level.addParticle(ParticleTypes.END_ROD, c.x + (rnd.nextDouble() - 0.5) * 0.9, c.y + (rnd.nextDouble() - 0.5) * 0.6,
                            c.z + (rnd.nextDouble() - 0.5) * 0.9, mv.x * 0.9, mv.y + 0.01, mv.z * 0.9);
                    if (now % 3 == 0) mc.level.addParticle(ParticleTypes.LARGE_SMOKE, c.x + (rnd.nextDouble() - 0.5) * 0.6,
                            c.y - 0.3, c.z + (rnd.nextDouble() - 0.5) * 0.6, mv.x, mv.y + 0.02, mv.z);
                    if (now % 5 == 0) mc.level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, c.x + (rnd.nextDouble() - 0.5) * 0.6,
                            c.y - 0.2, c.z + (rnd.nextDouble() - 0.5) * 0.6, mv.x, mv.y + 0.03, mv.z);
                    // --- rustle of pages each time a sheet starts to flip ---
                    long cyc = (now + p.getId() * 13L) % FLIP_CYCLE;
                    if (pr[1] >= 1f && (cyc == 4 || cyc == 9 || cyc == 14)) {
                        mc.level.playLocalSound(c.x, c.y, c.z, SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.5f, 0.9f + rnd.nextFloat() * 0.3f, false);
                    }
                } else if (pr[1] <= 0f && now % 8 == 0) {
                    // embers leaking from the closed book on the hip
                    Vec3 h = hipPosition(p, 1f, p.yBodyRot);
                    mc.level.addParticle(ParticleTypes.CRIMSON_SPORE, h.x, h.y, h.z, mv.x, mv.y + 0.01, mv.z);
                    if (now % 24 == 0) mc.level.addParticle(EMBER, h.x, h.y, h.z, mv.x, mv.y + 0.03, mv.z);
                }
            }

            // --- sword aura: embers and sparks around the Demon Slayer Sword while you hold it (more while dashing) ---
            if (!p.isSpectator() && !p.isInvisible() && p.getMainHandItem().is(AddonItemRegistry.DEMON_SLAYER_SWORD.get())) {
                double yr = Math.toRadians(p.getViewYRot(1f));
                Vec3 fw = new Vec3(-Math.sin(yr), 0, Math.cos(yr)), lf = new Vec3(Math.cos(yr), 0, Math.sin(yr));
                Vec3 hand = new Vec3(p.getX(), p.getY() + p.getEyeHeight() * 0.7, p.getZ()).add(fw.scale(0.6)).add(lf.scale(-0.45));
                boolean dash = p.isAutoSpinAttack();
                for (int i = 0; i < n(dash ? 8 : 2); i++) {
                    mc.level.addParticle(i % 2 == 0 ? EMBER : DARK_RED, hand.x + (rnd.nextDouble() - 0.5) * 0.7, hand.y + (rnd.nextDouble() - 0.5) * 0.9,
                            hand.z + (rnd.nextDouble() - 0.5) * 0.7, 0, 0.02 + rnd.nextDouble() * 0.02, 0);
                }
                if (dash) mc.level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, hand.x, hand.y, hand.z, 0, 0.01, 0);
                else if (now % 6 == 0) mc.level.addParticle(ParticleTypes.CRIMSON_SPORE, hand.x, hand.y, hand.z, 0, 0.02, 0);
            }

            // --- summon burst: when the sword is called, flash + shockwave at the book ---
            Long st = ANIMS.get(p.getId());
            if (has && st != null && now - st <= 1 && BURSTED.add(p.getId())) {
                Vec3 c = floatPosition(p, 1f, sy[1]).add(0, 0.2, 0);
                for (int i = 0; i < n(48); i++) {
                    double a = i * Math.PI * 2 / n(48);
                    mc.level.addParticle(EMBER, c.x, c.y, c.z, Math.cos(a) * 0.25, 0.02, Math.sin(a) * 0.25);
                }
                mc.level.addParticle(ParticleTypes.FLASH, c.x, c.y, c.z, 0, 0, 0);
                mc.level.playLocalSound(c.x, c.y, c.z, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0f, 0.5f, false);
            }
        }
        BURSTED.removeIf(id -> !ANIMS.containsKey(id));
        ANIMS.values().removeIf(s -> now - s > SummonQueue.DELAY_TICKS + 2);
        PROGRESS.keySet().removeIf(id -> !HOLDERS.contains(id) && PROGRESS.get(id)[1] <= 0f);
        SMOOTH_YAW.keySet().retainAll(PROGRESS.keySet());
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || HOLDERS.isEmpty()) return;

        float pt = e.getPartialTick();
        PoseStack ps = e.getPoseStack();
        Vec3 cam = e.getCamera().getPosition();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        ItemStack book = new ItemStack(AddonItemRegistry.BLACK_GRIMOIRE.get());
        ItemStack sword = new ItemStack(AddonItemRegistry.DEMON_SLAYER_SWORD.get());
        ItemStack twin = new ItemStack(AddonItemRegistry.DEMON_DWELLER_SWORD.get());
        ItemStack page = new ItemStack(AddonItemRegistry.GRIMOIRE_PAGE.get());

        for (Player p : mc.level.players()) {
            if (!HOLDERS.contains(p.getId()) || p.isSpectator() || p.isInvisible()) continue;
            float[] pr = PROGRESS.get(p.getId());
            if (pr == null) continue;
            float prog = ease(Mth.lerp(pt, pr[0], pr[1]));

            float yaw = p.getViewYRot(pt);
            float[] sy = SMOOTH_YAW.get(p.getId());
            float yawS = sy == null ? yaw : sy[0] + Mth.wrapDegrees(sy[1] - sy[0]) * pt;   // smoothed yaw for the floating book
            float bodyYaw = Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot);
            double yawR = Math.toRadians(yaw);
            Vec3 fwd = new Vec3(-Math.sin(yawR), 0, Math.cos(yawR)), left = new Vec3(Math.cos(yawR), 0, Math.sin(yawR));
            Vec3 hip = hipPosition(p, pt, bodyYaw), open = floatPosition(p, pt, yawS);
            Vec3 bookPos = hip.add(open.subtract(hip).scale(prog));

            ps.pushPose();
            ps.translate(bookPos.x - cam.x, bookPos.y - cam.y, bookPos.z - cam.z);
            if (prog < 0.5f) {
                // ---- closed tome, strapped to the hip (and lifting off it) ----
                float s = HIP_SCALE * (1f + 0.15f * prog);
                ps.mulPose(Axis.YP.rotationDegrees(-bodyYaw));
                float sway = (float) Math.sin((mc.level.getGameTime() + pt) * 0.12 + p.getId()) * 2f
                        + (float) Math.min(p.getDeltaMovement().horizontalDistance() * 40.0, 14.0);   // swings a little when you move
                ps.mulPose(Axis.ZP.rotationDegrees(sway));
                ps.mulPose(Axis.XP.rotationDegrees(90f));          // hang the book upright, spine at the top
                ps.mulPose(Axis.YP.rotationDegrees(prog * 120f));  // turns toward you as it lifts
                ps.scale(s, s, s);
                ps.translate(-0.0625, 0.21875, 0);                  // pivot on the book's centre
                mc.getItemRenderer().renderStatic(book, ItemDisplayContext.NONE, LightTexture.FULL_BRIGHT,
                        OverlayTexture.NO_OVERLAY, ps, buf, mc.level, p.getId());
            } else {
                // ---- open tome, floating ----
                float k = ease((prog - 0.5f) * 2f);
                float kb = k - 1f;
                float back = 1f + 2.70158f * kb * kb * kb + 1.70158f * kb * kb;   // ease-out-back: small overshoot, then settles
                float s = BOOK_SCALE * (0.5f + 0.5f * back);
                ps.mulPose(Axis.YP.rotationDegrees(-yawS));
                ps.mulPose(Axis.YP.rotationDegrees(180f));          // pages face the player
                ps.mulPose(Axis.XP.rotationDegrees(-TILT * k));     // lean back like a lectern
                ps.scale(s, s, s);
                mc.getItemRenderer().renderStatic(book, ItemDisplayContext.HEAD, LightTexture.FULL_BRIGHT,
                        OverlayTexture.NO_OVERLAY, ps, buf, mc.level, p.getId());

                // ---- pages riffling over from right to left, like thumbing through a real book ----
                if (k > 0.95f) {
                    float cyc = ((mc.level.getGameTime() + p.getId() * 13L) % FLIP_CYCLE) + pt;
                    for (int i = 0; i < 3; i++) {
                        float sP = (cyc - (4 + i * 5)) / 14f;          // 3 sheets, staggered 5 ticks, 14 ticks each
                        if (sP <= 0f || sP >= 1f) continue;
                        float m = 0.5f - 0.5f * (float) Math.cos(Math.PI * sP);   // sine ease in/out
                        float alpha = -22.5f - 135f * m;               // right-hand rest -> straight up -> left-hand rest
                        float lift = 1.5f * (1f - 2f * m);             // thickness offset so it lands on the left stack
                        ps.pushPose();
                        ps.translate(0, 0, -0.03125);                  // hinge = the spine (model z = 7.5)
                        ps.mulPose(Axis.YP.rotationDegrees(alpha));
                        ps.translate(0, 0, lift / 16f);
                        ps.translate(0, 0, 0.03125);
                        mc.getItemRenderer().renderStatic(page, ItemDisplayContext.NONE, LightTexture.FULL_BRIGHT,
                                OverlayTexture.NO_OVERLAY, ps, buf, mc.level, p.getId());
                        ps.popPose();
                    }
                }
            }
            ps.popPose();

            // ---- sword: handle rises out of the open book, then flies sideways (handle first) to the right hand ----
            Long start = ANIMS.get(p.getId());
            if (start != null && prog >= 0.99f) {
                float t = Mth.clamp((mc.level.getGameTime() - start + pt) / SummonQueue.DELAY_TICKS, 0f, 1f);
                boolean off = OFFHAND_ANIMS.contains(p.getId());
                Vec3 hand = base(p, pt).add(0, p.getEyeHeight() * 0.8, 0).add(fwd.scale(0.55)).add(left.scale(off ? 0.4 : -0.4));
                Vec3 lowStart = open.add(0, -0.85, 0);   // blade hangs inside the book, only the handle pokes out
                Vec3 top = open.add(0, 0.55, 0);
                Vec3 pos; float scale, roll;             // roll 225 = blade down / handle up, 315 = blade to the left / handle to the right
                final float RISE = 0.45f;
                if (t < RISE) {
                    float u = ease(t / RISE);
                    pos = lowStart.add(top.subtract(lowStart).scale(u));
                    scale = 0.5f + 0.5f * u; roll = 225f;
                } else {
                    float u = (t - RISE) / (1 - RISE), m = ease(u);
                    Vec3 ctrl = top.add(hand).scale(0.5).add(0, 0.35, 0).add(fwd.scale(0.35));   // swings out toward you
                    pos = top.scale((1 - m) * (1 - m)).add(ctrl.scale(2 * (1 - m) * m)).add(hand.scale(m * m));
                    scale = 1f; roll = 225f + (off ? -90f : 90f) * ease(Mth.clamp(u * 3f, 0f, 1f));
                }
                long gtNow = mc.level.getGameTime();
                if (swordFxTick != gtNow) {   // once per tick, not once per frame
                    swordFxTick = gtNow;
                    for (int i = 0; i < n(6); i++) {
                        double a = gtNow * 0.9 + i * Math.PI * 2 / n(6);
                        mc.level.addParticle(i % 2 == 0 ? EMBER : CRIMSON, pos.x + Math.cos(a) * 0.32, pos.y + Math.sin(a * 0.5) * 0.12,
                                pos.z + Math.sin(a) * 0.32, 0, 0.01, 0);
                    }
                    mc.level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, pos.x, pos.y, pos.z, 0, 0.01, 0);
                    mc.level.addParticle(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 0, 0, 0);
                }
                ps.pushPose();
                ps.translate(pos.x - cam.x, pos.y - cam.y, pos.z - cam.z);
                ps.mulPose(Axis.YP.rotationDegrees(-yaw));
                ps.mulPose(Axis.ZP.rotationDegrees(roll));
                ps.scale(SWORD_SCALE * scale, SWORD_SCALE * scale, SWORD_SCALE * scale);
                ps.translate(-SWORD_CENTER, -SWORD_CENTER, 0);   // pivot on the sword's own centre
                mc.getItemRenderer().renderStatic(off ? twin : sword, ItemDisplayContext.NONE, LightTexture.FULL_BRIGHT,
                        OverlayTexture.NO_OVERLAY, ps, buf, mc.level, p.getId());
                ps.popPose();
            }
        }
        buf.endBatch();
    }
}
