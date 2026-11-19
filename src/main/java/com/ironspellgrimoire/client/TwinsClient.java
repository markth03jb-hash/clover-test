package com.ironspellgrimoire.client;

import com.ironspellgrimoire.network.AddonNetwork;
import com.ironspellgrimoire.network.TwinsPackets;
import com.ironspellgrimoire.server.TwinsFx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import static com.ironspellgrimoire.server.TwinsFx.*;

/** The Masked Twins on this side: their synced state (server/TwinsFx.sync, counted down between syncs), the HUD, and
 *  the item property that picks each mask's model. */
@Mod.EventBusSubscriber(modid = "iron_spell_grimoire", value = Dist.CLIENT)
public final class TwinsClient {
    private TwinsClient() {}

    public static int[] v = fresh();
    static int[] fresh() { int[] a = new int[SIZE]; a[V_LV] = 1; a[V_LV + 1] = 1; a[V_LOAD] = 0; a[V_LOAD + 1] = a[V_LOAD + 2] = a[V_LOAD + 3] = -1;
        a[V_LOAD + 4] = 0; a[V_LOAD + 5] = a[V_LOAD + 6] = a[V_LOAD + 7] = -1; a[V_TITLE] = -1; return a; }

    public static void sync(TwinsPackets.Sync p) {
        if (p.v.length == SIZE) v = p.v;
        Minecraft mc = Minecraft.getInstance();
        if (p.open) mc.setScreen(new TwinsScreen());
    }

    static boolean equipped() { return v[V_EQ] == 1; }
    static int active() { return v[V_ACTIVE] == NYX ? NYX : SOL; }
    static int lv(int twin) { return Math.max(1, v[V_LV + twin]); }
    /** The art in a wheel slot (-1 empty; anything out of range counts as empty). */
    static int load(int twin, int s) { int a = v[V_LOAD + twin * 4 + s]; return a >= 0 && a < 8 ? a : -1; }
    static int cd(int twin, int art) { return v[V_CD + twin * 8 + art]; }
    static boolean knows(int twin, int art) { return art >= 0 && art < 8 && lv(twin) >= ART_LV[art]; }
    static boolean eclipse() { return v[V_ECLIPSE] > 0; }
    static boolean socket(int twin, int s) { return (v[V_SOCK + twin] & (1 << s)) != 0; }
    static int uses(int twin, int art) { return v[V_USES + twin * 8 + art]; }
    static int cut(int twin, int s) { return v[V_CUT + twin * 3 + s]; }
    static int maskForm(int twin) { return (twin == SOL ? 0 : 3) + TwinsFx.stage(v[V_SOCK + twin]) - 1; }

    public static void send(int action, int arg) { AddonNetwork.CHANNEL.sendToServer(new TwinsPackets.Act(action, arg)); }

    /** The timers count down here between syncs, so the wheel and HUD stay live. */
    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END || Minecraft.getInstance().isPaused()) return;
        for (int i = 0; i < 16; i++) if (v[V_CD + i] > 0) v[V_CD + i]--;
        for (int i : new int[]{V_SWAP, V_RUSH, V_ECLIPSE, V_CORONA, V_NOON, V_EVENTIDE}) if (v[i] > 0) v[i]--;
    }

    // ------------------------------------------------------------------ the model of each mask
    @Mod.EventBusSubscriber(modid = "iron_spell_grimoire", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent
        public static void setup(FMLClientSetupEvent e) {
            e.enqueueWork(() -> net.minecraft.client.renderer.item.ItemProperties.register(com.ironspellgrimoire.registry.AddonItemRegistry.TWIN_MASK.get(),
                    ResourceLocation.fromNamespaceAndPath("iron_spell_grimoire", "form"),
                    (stack, level, entity, seed) -> com.ironspellgrimoire.item.TwinMaskItem.form(stack) / 16F));
        }
    }

    // ------------------------------------------------------------------ the HUD: the worn twin, the resting twin's Resonance, the swap
    static final int GOLD = 0xFFFFD36E, SILVER = 0xFFD6E2FF;
    private static float shown;

    @SubscribeEvent
    public static void hud(RenderGuiEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || !equipped() || ParasiteClient.wheelKind(mc) != 5) return;
        draw(e.getGuiGraphics(), mc, mc.level.getGameTime() + e.getPartialTick());
    }

    static void draw(GuiGraphics g, Minecraft mc, double t) {
        int a = active(), rest = 1 - a;
        int x = 6, y = g.guiHeight() - 24, w = 92;
        shown += (v[V_RES] - shown) * 0.2F;
        boolean ecl = eclipse();
        String head = ecl ? "ECLIPSE UNION " + (v[V_ECLIPSE] + 19) / 20 + "s" : v[V_NOON] > 0 ? "HIGH NOON " + (v[V_NOON] + 19) / 20 + "s"
                : v[V_EVENTIDE] > 0 ? "EVENTIDE " + (v[V_EVENTIDE] + 19) / 20 + "s" : TWIN[a] + "  Lv " + lv(a);
        int col = ecl ? (((int) (t / 4)) % 2 == 0 ? GOLD : SILVER) : v[V_NOON] > 0 ? (((int) (t / 4)) % 2 == 0 ? 0xFFFFFFFF : GOLD) : a == SOL ? GOLD : SILVER;
        g.drawString(mc.font, head, x, y - 10, col, true);
        // the resting twin's Resonance
        g.fill(x - 1, y - 1, x + w + 1, y + 7, 0xE0080812);
        int fw = Math.round(w * Mth.clamp(shown / 100F, 0, 1));
        boolean full = v[V_RES] >= 100 && ((int) (t / 6)) % 2 == 0;
        int c0 = rest == SOL ? 0xFFB07818 : 0xFF5A6AA8, c1 = rest == SOL ? 0xFFFFD36E : 0xFFD6E2FF;
        g.fillGradient(x, y, x + fw, y + 6, full ? 0xFFFFFFFF : c1, c0);
        if (!ecl && v[V_NOON] == 0 && v[V_EVENTIDE] == 0) g.drawString(mc.font, TWIN[rest] + " " + v[V_RES] + "%", x + 64, y - 10, rest == SOL ? GOLD : SILVER, true);   // above the bar, clear of the hotbar
        String swap = v[V_SWAP] > 0 ? "Swap " + (v[V_SWAP] + 19) / 20 + "s" : "Swap ready (G)";
        g.drawString(mc.font, swap, x, y + 9, v[V_SWAP] > 0 ? 0xFF9098B0 : 0xFFE8E8F0, true);
        if (v[V_RUSH] > 0) g.drawString(mc.font, "RUSH", x + mc.font.width(swap) + 8, y + 9, a == SOL ? 0xFFFF9040 : 0xFF80E0FF, true);
    }
}
