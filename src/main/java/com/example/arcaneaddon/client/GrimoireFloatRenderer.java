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
    private static final float BOOK_SCALE = 0.42f;   // open book size (outline hologram)
    private static final float TILT = 35f;           // open book lean-back (degrees)
    private static final int OPEN_TICKS = 60;        // stays open this long after the last cast
    private static final double HIP_SIDE = 0.40, HIP_BACK = 0.05, HIP_HEIGHT = 0.45;  // hip position
    private static final float HIP_SCALE = 0.7f;     // closed book size
    private static final int FLIP_CYCLE = 50;       // ticks between page-flip bursts
    private static final float SWORD_SCALE = 0.9f;
    private static final float SWORD_CENTER = 0.582f; // sword model centre offset from its origin

    private static final Set<Integer> BURSTED = new HashSet<>();

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
                if (pr[1] > 0.6f) {
                    Vec3 c = floatPosition(p, 1f, sy[1]);
                    // opening: just a sound, the look is the 3D outline book (no particles on the book)
                    if (pr[0] <= 0.6f) {
                        mc.level.playLocalSound(c.x, c.y, c.z, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.6f, 0.7f, false);
                        mc.level.playLocalSound(c.x, c.y, c.z, SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8f, 0.8f, false);
                    }
                    long cyc = (now + p.getId() * 13L) % FLIP_CYCLE;
                    if (pr[1] >= 1f && (cyc == 4 || cyc == 9 || cyc == 14)) {
                        mc.level.playLocalSound(c.x, c.y, c.z, SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.5f, 0.9f + rnd.nextFloat() * 0.3f, false);
                    }
                }
            }

            // --- summon: a dark ring flares where the sword is called from ---
            Long st = ANIMS.get(p.getId());
            if (has && st != null && now - st <= 1 && BURSTED.add(p.getId())) {
                Vec3 c = floatPosition(p, 1f, sy[1]).add(0, 0.1, 0);
                FxRenderer.localRing(c, OFFHAND_ANIMS.contains(p.getId()) ? 1 : 0, 0.3f, 2.4f, 0.35f, 9);
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
            boolean fp = p == mc.player && mc.options.getCameraType().isFirstPerson();   // the book is never drawn in first person

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
            if (fp) {
                // nothing: first person sees no book
            } else if (prog < 0.5f) {
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
                // ---- open tome, floating: a see-through dark outline so it never blocks the view ----
                float k = ease((prog - 0.5f) * 2f);
                float kb = k - 1f;
                float back = 1f + 2.70158f * kb * kb * kb + 1.70158f * kb * kb;   // ease-out-back: small overshoot, then settles
                float s = BOOK_SCALE * (0.5f + 0.5f * back);
                float cyc = k > 0.95f ? ((mc.level.getGameTime() + p.getId() * 13L) % FLIP_CYCLE) + pt : -1f;
                FxRenderer.queueBook(bookPos, yawS, TILT * k, s, k, cyc);
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
                {   // dark aura hugging the flying blade
                    double ang = Math.toRadians(45f + roll), yr = Math.toRadians(yaw);
                    Vec3 axis = new Vec3(Math.cos(ang) * Math.cos(yr), Math.sin(ang), Math.cos(ang) * Math.sin(yr));
                    float half = (off ? 1.05f : 1.5f) * scale;
                    FxRenderer.queueAura(pos.subtract(axis.scale(half)), pos.add(axis.scale(half)), (off ? 0.26f : 0.36f) * scale, off ? 1 : 0, 1f);
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
