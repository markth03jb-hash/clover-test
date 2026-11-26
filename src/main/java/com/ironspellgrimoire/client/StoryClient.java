package com.ironspellgrimoire.client;

import com.ironspellgrimoire.network.AddonNetwork;
import com.ironspellgrimoire.network.StoryPackets;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.math.Axis;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

/**
 * ASHFALL on the client: what the server last told us (the Journal's state), the keys - X lets the Brand burn, K
 * opens the Journal - and the story's tracker on the right, under the others: the chapter, what's next, how far and
 * which way, and the Brand. A pillar of ember light stands over where the story points.
 */
@Mod.EventBusSubscriber(modid = "iron_spell_grimoire", value = Dist.CLIENT)
public final class StoryClient {
    private StoryClient() {}

    public static final KeyMapping BRAND = new KeyMapping("key.iron_spell_grimoire.brand", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, "key.categories.iron_spell_grimoire");
    public static final KeyMapping JOURNAL = new KeyMapping("key.iron_spell_grimoire.journal", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, "key.categories.iron_spell_grimoire");
    static final int EMBER = 0xFFFF9A3C;

    public static CompoundTag state = new CompoundTag();
    static long syncedAt;

    public static void sync(CompoundTag t) {
        Minecraft mc = Minecraft.getInstance();
        state = t;
        syncedAt = mc.level == null ? 0 : mc.level.getGameTime();
        String open = t.getString("open");
        if (open.equals("journal")) mc.setScreen(new JournalScreen(-1));
        else if (open.startsWith("page:")) { try { mc.setScreen(new JournalScreen(Integer.parseInt(open.substring(5)))); } catch (NumberFormatException ignored) {} }
        else if (mc.screen instanceof JournalScreen j) j.refresh();
    }

    public static void talk(CompoundTag t) {
        Minecraft mc = Minecraft.getInstance();
        if (t.contains("lines")) { mc.setScreen(new DialogueScreen(t)); return; }
        if (mc.screen instanceof DialogueScreen d && d.id.equals(t.getString("id"))) d.update(t);
    }

    static void act(CompoundTag t) { AddonNetwork.CHANNEL.sendToServer(new StoryPackets.Act(t)); }
    static void act(String a) { CompoundTag t = new CompoundTag(); t.putString("a", a); act(t); }

    // ------------------------------------------------------------------ the tracker
    static boolean begun() { return state.getBoolean("has") && state.getInt("ch") >= 0 && state.getInt("ch") < 5; }
    public static boolean trackerShown() { return begun() || state.getInt("brand") > 0; }
    static ListTag goals() { return state.getList("goals", Tag.TAG_COMPOUND); }
    /** The goals still open (the tracker shows those). */
    static java.util.List<CompoundTag> open() {
        java.util.List<CompoundTag> out = new java.util.ArrayList<>();
        for (Tag x : goals()) { CompoundTag g = (CompoundTag) x; if (g.getInt("h") < Math.max(1, g.getInt("n"))) out.add(g); }
        return out;
    }
    static boolean targetHere() {
        Minecraft mc = Minecraft.getInstance();
        if (!state.contains("target") || mc.level == null) return false;
        return state.getCompound("target").getString("dim").equals(mc.level.dimension().location().toString());
    }
    public static int trackerH() {
        int lines = begun() ? Math.min(3, Math.max(1, open().size())) : 0;
        return 16 + lines * 10 + (begun() && targetHere() ? 11 : 0) + (state.getInt("brand") > 0 ? 11 : 0) + 2;
    }

    static int left(String k) { Minecraft mc = Minecraft.getInstance(); long now = mc.level == null ? 0 : mc.level.getGameTime(); return (int) Math.max(0, state.getInt(k) - (now - syncedAt)); }

    static String brandLine() {
        int b = state.getInt("brand");
        String name = "Brand " + new String[]{"0", "I", "II", "III", "IV"}[Math.min(4, b)];
        int on = left("brandOn"), cd = left("brandCd");
        String key = BRAND.getTranslatedKeyMessage().getString();
        return on > 0 ? name + ": burning " + (on + 19) / 20 + " s" : cd > 0 ? name + ": " + (cd + 19) / 20 + " s" : name + ": ready [" + key + "]";
    }

    @SubscribeEvent
    public static void onGui(RenderGuiEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui || TrackerMode.hidden() || !trackerShown()) return;
        if (mc.screen instanceof DialogueScreen) return;
        GuiGraphics g = e.getGuiGraphics();
        Font f = mc.font;
        long now = mc.level.getGameTime();
        float yaw = mc.player.getViewYRot(e.getPartialTick());
        int y = TrackerMode.storyY();
        java.util.List<CompoundTag> open = open();
        double dx = 0, dz = 0;
        boolean arrow = begun() && targetHere();
        if (arrow) { CompoundTag t = state.getCompound("target"); dx = t.getDouble("x") - mc.player.getX(); dz = t.getDouble("z") - mc.player.getZ(); }
        if (TrackerMode.compact()) {
            String t = begun() ? (open.isEmpty() ? state.getString("chTitle") : open.get(0).getString("t")) : brandLine();
            if (arrow) t = (int) Math.sqrt(dx * dx + dz * dz) + " m  " + t;
            TrackerMode.line(g, f, y, EMBER, "STORY", t, dx, dz, arrow, now, yaw);
            return;
        }
        int w = 168, x = g.guiWidth() - w - 4, h = trackerH();
        g.fill(x, y, x + w, y + h, 0xA0100806);
        g.renderOutline(x, y, w, h, 0xC0B0602A);
        g.fill(x, y, x + 3, y + h, EMBER);
        g.drawString(f, "ASHFALL", x + 8, y + 4, EMBER, false);
        if (begun()) {
            String ch = "Ch. " + (state.getInt("ch") + 1);
            g.drawString(f, ch, x + w - 6 - f.width(ch), y + 4, 0xFFFFFFFF, false);
        }
        int ty = y + 16;
        if (begun()) {
            int shown = 0;
            for (CompoundTag gl : open) {
                if (shown++ >= 3) break;
                String t = gl.getString("t");
                int n = gl.getInt("n");
                String prog = n > 1 ? "  " + gl.getInt("h") + "/" + n : "";
                int room = w - 14 - f.width(prog);
                if (f.width(t) > room) t = f.plainSubstrByWidth(t, room - 6) + "..";
                g.drawString(f, t, x + 8, ty, 0xFFE8D8C8, false);
                if (!prog.isEmpty()) g.drawString(f, prog.trim(), x + w - 6 - f.width(prog.trim()), ty, 0xFFFFFFFF, false);
                ty += 10;
            }
            if (open.isEmpty()) { g.drawString(f, state.getString("chTitle"), x + 8, ty, 0xFFE8D8C8, false); ty += 10; }
            if (arrow) {
                String d = (int) Math.sqrt(dx * dx + dz * dz) + " m " + com.ironspellgrimoire.server.Bounties.direction(dx, dz);
                g.drawString(f, d, x + 22, ty + 1, 0xFFFFFFFF, false);
                float rel = Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(-dx, dz)) - yaw);
                g.pose().pushPose();
                g.pose().translate(x + 12, ty + 4.5, 0);
                g.pose().mulPose(Axis.ZP.rotationDegrees(rel));
                g.fill(-1, -2, 1, 4, EMBER);
                for (int i = 0; i < 4; i++) g.fill(-i, -5 + i, i + 1, -4 + i, EMBER);
                g.pose().popPose();
                ty += 11;
            }
        }
        if (state.getInt("brand") > 0) g.drawString(f, brandLine(), x + 8, ty, left("brandOn") > 0 ? EMBER : 0xFFB0A090, false);
    }

    /** The pillar of ember light over where the story points (drawn from FxRenderer's glow pass). */
    static void drawBeacon(Matrix4f m, Vec3 cam, double now) {
        Minecraft mc = Minecraft.getInstance();
        if (!begun() || !targetHere() || mc.player == null || mc.level == null) return;
        CompoundTag t = state.getCompound("target");
        double tx = t.getDouble("x"), tz = t.getDouble("z");
        double dx = tx - cam.x, dz = tz - cam.z, d = Math.sqrt(dx * dx + dz * dz);
        if (d > 600 || d < 24) return;   // close by, the tracker's arrow is enough
        double ground = t.getDouble("y");
        int bx = Mth.floor(tx), bz = Mth.floor(tz);
        if (ground < 0) ground = mc.level.hasChunk(bx >> 4, bz >> 4) ? mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz) : mc.player.getY();
        float w = (float) Math.max(0.5, d * 0.01);
        float a = (float) (0.55 + 0.25 * Math.sin(now * 0.15)) * (float) Mth.clamp((d - 6) / 20.0, 0, 1);
        Vec3 bot = new Vec3(tx, ground, tz), top = new Vec3(tx, ground + 120, tz);
        FxBase.gline(m, cam, bot, top, w, 2, a);
        FxBase.gline(m, cam, bot, bot.add(0, 6, 0), w * 2.2F, 2, a * 0.6F);
    }

    // ------------------------------------------------------------------ the keys
    @Mod.EventBusSubscriber(modid = "iron_spell_grimoire", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Keys {
        @SubscribeEvent public static void register(RegisterKeyMappingsEvent e) { e.register(BRAND); e.register(JOURNAL); }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        while (BRAND.consumeClick()) if (mc.screen == null) act("brand");
        while (JOURNAL.consumeClick()) if (mc.screen == null) act("journal");
    }
}
