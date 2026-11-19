package com.ironspellgrimoire.client;

import com.ironspellgrimoire.network.ElitePackets;
import com.ironspellgrimoire.server.EliteMobs;
import com.ironspellgrimoire.server.EliteTraits;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * What the client knows about elite mobs (server/EliteMobs) and how they look: a name and trait icons over an elite's
 * head, its threat rank as a skull with pips, a ghost's pale tint and a Mirror Twin's black-and-blue, a Splitter's
 * copies at 60% size, and the models some traits wear - Bulwark's shield, Ironhide's plates, Reflector's mirror
 * shards, Tether's chain.
 */
@Mod.EventBusSubscriber(modid = "iron_spell_grimoire", value = Dist.CLIENT)
public final class EliteClient {
    private EliteClient() {}

    public record Info(int rank, int traits, int flags, int plates, int phase, int chainTo, long chainUntil) {
        public boolean child() { return (flags & 1) != 0; }
        public boolean ghost() { return (flags & 2) != 0; }
        public boolean shadow() { return (flags & 4) != 0; }
        public boolean has(int t) { return (traits & (1 << t)) != 0; }
    }

    static final Map<Integer, Info> DATA = new HashMap<>();
    private static int seenMask, myThreat;

    public static void set(ElitePackets.Sync m) {
        Info old = DATA.put(m.id, new Info(m.rank, m.traits, m.flags, m.plates, m.phase, m.chainTo, m.chainUntil));
        var mc = Minecraft.getInstance();
        if (mc.level != null && (old == null || old.child() != ((m.flags & 1) != 0))) {
            Entity e = mc.level.getEntity(m.id);
            if (e != null) e.refreshDimensions();
        }
    }
    public static void seen(int mask, int threat) { seenMask = mask; myThreat = threat; }
    public static int seenMask() { return seenMask; }
    public static int threat() { return myThreat; }
    public static Info info(Entity e) { return e == null ? null : DATA.get(e.getId()); }

    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent e) { if (e.getLevel().isClientSide()) DATA.remove(e.getEntity().getId()); }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) { DATA.clear(); seenMask = 0; myThreat = 0; }

    /** A Splitter's copies are 60% the size (their hitbox too). */
    @SubscribeEvent
    public static void onSize(EntityEvent.Size e) {
        Entity en = e.getEntity();
        if (en.level() == null || !en.level().isClientSide()) return;
        Info i = DATA.get(en.getId());
        if (i != null && i.child()) e.setNewSize(e.getNewSize().scale(0.6F), true);
    }

    // ------------------------------------------------------------------ models
    static ResourceLocation model(String n) { return ResourceLocation.fromNamespaceAndPath("iron_spell_grimoire", "item/elite_" + n); }
    static final ResourceLocation SHIELD = model("shield"), PLATE = model("plate"), SHARD = model("shard"), LINK = model("chain_link"),
            CHEST = model("chest"), CHEST_LID = model("chest_lid"), RIFT = model("rift");
    static final ResourceLocation ICONS = ResourceLocation.fromNamespaceAndPath("iron_spell_grimoire", "textures/gui/elite_icons.png");

    @Mod.EventBusSubscriber(modid = "iron_spell_grimoire", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Register {
        @SubscribeEvent
        public static void models(ModelEvent.RegisterAdditional e) {
            for (ResourceLocation r : new ResourceLocation[]{SHIELD, PLATE, SHARD, LINK, CHEST, CHEST_LID, RIFT}) e.register(r);
        }
    }

    // ------------------------------------------------------------------ drawing
    private static boolean nested;
    private static final Set<Integer> SCALED = new HashSet<>();

    @SubscribeEvent(priority = EventPriority.LOW)
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void onPre(RenderLivingEvent.Pre e) {
        if (nested) return;
        LivingEntity en = e.getEntity();
        Info i = DATA.get(en.getId());
        if (i == null) return;
        PoseStack ps = e.getPoseStack();
        if (i.ghost() || i.shadow()) {   // the same mob, drawn again in a ghost's or a shadow's colours
            e.setCanceled(true);
            nested = true;
            try {
                float yaw = Mth.lerp(e.getPartialTick(), en.yRotO, en.getYRot());
                MultiBufferSource tint = i.ghost() ? new ShadowSoldierRenderer.Tint(e.getMultiBufferSource(), 0.75F, 0.88F, 1.0F, 0)
                        : new ShadowSoldierRenderer.Tint(e.getMultiBufferSource(), 0.16F, 0.2F, 0.42F, 0);
                ((net.minecraft.client.renderer.entity.LivingEntityRenderer) e.getRenderer()).render(en, yaw, e.getPartialTick(), ps, tint,
                        i.ghost() ? LightTexture.FULL_BRIGHT : e.getPackedLight());
            } finally { nested = false; }
            if (i.shadow()) overhead(en, i, ps, e.getMultiBufferSource(), e.getPackedLight(), 1F);
            return;
        }
        if (i.child()) { ps.pushPose(); ps.scale(0.6F, 0.6F, 0.6F); SCALED.add(en.getId()); }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onPost(RenderLivingEvent.Post e) {
        if (nested) return;
        LivingEntity en = e.getEntity();
        Info i = DATA.get(en.getId());
        if (i == null) return;
        PoseStack ps = e.getPoseStack();
        if (SCALED.remove(en.getId())) ps.popPose();
        float pt = e.getPartialTick();
        MultiBufferSource buf = e.getMultiBufferSource();
        int light = e.getPackedLight();
        float sc = i.child() ? 0.6F : 1F;
        if (i.traits() != 0) gear(en, i, ps, buf, light, pt);
        overhead(en, i, ps, buf, light, sc);
    }

    /** The models a trait wears, turned with the body. */
    static void gear(LivingEntity en, Info i, PoseStack ps, MultiBufferSource buf, int light, float pt) {
        float h = en.getBbHeight(), w = en.getBbWidth();
        float body = Mth.rotLerp(pt, en.yBodyRotO, en.yBodyRot);
        float t = en.tickCount + pt;
        if (i.has(EliteMobs.BULWARK)) {
            ps.pushPose();
            ps.mulPose(Axis.YP.rotationDegrees(-body));
            ps.translate(0.18F, h * 0.42F, w * 0.5F + 0.22F);
            ps.mulPose(Axis.YP.rotationDegrees(180));
            float s = Math.max(0.7F, h / 1.9F);
            ps.scale(s, s, s);
            ParasiteArms.drawPiece(ps, buf, light, SHIELD);
            ps.popPose();
        }
        if (i.has(EliteMobs.IRONHIDE)) {   // three plates: chest, then the shoulders; one cracks off a hit
            float[][] at = {{0, 0.62F, 1}, {-1, 0.74F, 0.25F}, {1, 0.74F, 0.25F}};
            for (int k = i.plates(); k < EliteTraits.IRON_PLATES; k++) {
                ps.pushPose();
                ps.mulPose(Axis.YP.rotationDegrees(-body));
                float[] a = at[k];
                ps.translate(a[0] * (w * 0.5F + 0.04F), h * a[1], a[2] * (w * 0.5F + 0.04F));
                ps.mulPose(Axis.YP.rotationDegrees(a[0] == 0 ? 0 : a[0] * 90));
                float s = Math.max(0.7F, h / 1.9F);
                ps.scale(s, s, s);
                ParasiteArms.drawPiece(ps, buf, light, PLATE);
                ps.popPose();
            }
        }
        if (i.has(EliteMobs.REFLECTOR) && en.level() != null) {   // mirror shards circle it while it reflects (2 s of every 7)
            long g = en.level().getGameTime();
            float in = (g + i.phase()) % EliteTraits.SHELL_CYCLE + pt;
            float up = in < EliteTraits.SHELL_UP ? Math.min(1F, Math.min(in, EliteTraits.SHELL_UP - in) / 5F) : 0F;
            if (up > 0) for (int k = 0; k < 5; k++) {
                ps.pushPose();
                float a = t * 6F + k * 72F;
                ps.translate(0, h * 0.5F + 0.25F * Mth.sin((t + k * 9) * 0.12F), 0);
                ps.mulPose(Axis.YP.rotationDegrees(a));
                ps.translate(0, 0, w * 0.5F + 0.55F);
                ps.mulPose(Axis.ZP.rotationDegrees(15F * Mth.sin(t * 0.2F + k)));
                ps.scale(up, up, up);
                ParasiteArms.drawPiece(ps, buf, LightTexture.FULL_BRIGHT, SHARD);
                ps.popPose();
            }
        }
        if (i.has(EliteMobs.TETHER) && i.chainTo() >= 0 && en.level() != null && en.level().getGameTime() <= i.chainUntil()) {
            Entity to = en.level().getEntity(i.chainTo());
            if (to != null) chain(en, to, ps, buf, light, pt);
        }
    }

    /** Tether: links from its hand to whoever it hooked. */
    static void chain(LivingEntity en, Entity to, PoseStack ps, MultiBufferSource buf, int light, float pt) {
        Vec3 a = en.getPosition(pt).add(0, en.getBbHeight() * 0.6, 0), b = to.getPosition(pt).add(0, to.getBbHeight() * 0.55, 0);
        Vec3 d = b.subtract(a);
        double len = d.length();
        if (len < 0.5) return;
        Vec3 base = en.getPosition(pt);
        float yaw = (float) Math.toDegrees(Math.atan2(d.x, d.z)), pitch = (float) Math.toDegrees(-Math.asin(d.y / len));
        int n = (int) (len / 0.32);
        for (int k = 0; k < n; k++) {
            Vec3 p = a.add(d.scale((k + 0.5) / n)).subtract(base);
            ps.pushPose();
            ps.translate(p.x, p.y, p.z);
            ps.mulPose(Axis.YP.rotationDegrees(yaw));
            ps.mulPose(Axis.XP.rotationDegrees(pitch));
            ps.mulPose(Axis.ZP.rotationDegrees(k % 2 == 0 ? 0 : 90));
            ParasiteArms.drawPiece(ps, buf, light, LINK);
            ps.popPose();
        }
    }

    // ------------------------------------------------------------------ over the head
    /** Icon atlas: 8 x 4 cells of 16 px - the traits 0-19 first, then the six rank skulls (rank 0-5) from cell 24. */
    static void icon(VertexConsumer vc, Matrix4f m, float x, float y, float s, int cell, int light, float a) {
        float u0 = (cell % 8) / 8F, v0 = (cell / 8) / 4F, u1 = u0 + 1 / 8F, v1 = v0 + 1 / 4F;
        vc.vertex(m, x, y + s, 0).color(1F, 1F, 1F, a).uv(u0, v1).uv2(light).endVertex();
        vc.vertex(m, x + s, y + s, 0).color(1F, 1F, 1F, a).uv(u1, v1).uv2(light).endVertex();
        vc.vertex(m, x + s, y, 0).color(1F, 1F, 1F, a).uv(u1, v0).uv2(light).endVertex();
        vc.vertex(m, x, y, 0).color(1F, 1F, 1F, a).uv(u0, v0).uv2(light).endVertex();
    }

    public static String title(LivingEntity en, Info i) {
        String kind = en.getType().getDescription().getString();
        if (i.shadow()) return "Shadow " + kind;
        if ((i.traits() & EliteMobs.CHAMPION) != 0) return "Champion " + kind;
        return i.traits() != 0 ? "Elite " + kind : null;
    }

    static void overhead(LivingEntity en, Info i, PoseStack ps, MultiBufferSource buf, int light, float sc) {
        Minecraft mc = Minecraft.getInstance();
        if (i.ghost() || en.isInvisible() || mc.player == null || en.distanceToSqr(mc.player) > 24 * 24) return;
        String name = title(en, i);
        int traits = Integer.bitCount(i.traits());
        if (name == null && i.rank() == 0) return;
        ps.pushPose();
        ps.translate(0, en.getBbHeight() * sc + (en.shouldShowName() ? 0.75F : 0.45F), 0);
        ps.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        ps.scale(-0.025F, -0.025F, 0.025F);
        Matrix4f m = ps.last().pose();
        Font f = mc.font;
        int full = LightTexture.FULL_BRIGHT;
        float y = 0;
        if (name != null) {
            int col = i.shadow() ? 0x6A8CFF : (i.traits() & EliteMobs.CHAMPION) != 0 ? 0xE070FF : 0xFFC040;
            Component c = Component.literal(name);
            float x = -f.width(c) / 2F;
            f.drawInBatch(c, x, y, col, false, m, buf, Font.DisplayMode.NORMAL, 0x60000000, full);
            y += 10;
        }
        VertexConsumer vc = buf.getBuffer(RenderType.text(ICONS));
        int n = traits + (i.rank() > 0 ? 1 : 0);
        float s = 9, gap = 1, x = -(n * s + (n - 1) * gap) / 2F;
        if (i.rank() > 0) { icon(vc, m, x, y, s, 24 + Math.min(5, i.rank()), full, 1F); x += s + gap; }
        for (int k = 0; k < EliteMobs.COUNT; k++) if (i.has(k)) { icon(vc, m, x, y, s, k, full, 1F); x += s + gap; }
        ps.popPose();
    }
}
