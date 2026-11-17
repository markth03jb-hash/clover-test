package com.ironspellgrimoire.client;

import com.ironspellgrimoire.network.AddonNetwork;
import com.ironspellgrimoire.network.HungerPackets;
import com.ironspellgrimoire.server.HungerFx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** The Hunger Blade on this side: its synced state (server/HungerFx), the hunger meter, and the item property that
 *  picks each sword's model. */
@Mod.EventBusSubscriber(modid = "iron_spell_grimoire", value = Dist.CLIENT)
public final class HungerClient {
    private HungerClient() {}

    public static boolean equipped, drawn;
    public static int growth, unlocked = 1, sel, bond, fed, pact;
    public static int[] load = {0, -1, -1, -1};
    public static float hunger = 60;

    public static void sync(HungerPackets.Sync p) {
        equipped = p.equipped; growth = p.growth; unlocked = p.unlocked; load = p.load.length == 4 ? p.load : load; sel = p.sel; hunger = p.hunger;
        bond = p.bond; drawn = p.drawn; fed = p.fed; pact = p.pact;
        Minecraft mc = Minecraft.getInstance();
        if (p.open) mc.setScreen(new BladeScreen());
        else if (mc.screen instanceof BladeScreen s) s.refresh();
    }

    static boolean has(int f) { return f >= 0 && f < HungerFx.FORMS && (unlocked & (1 << f)) != 0; }
    static int bondLevel() { int l = 0; while (l < 5 && bond >= HungerFx.BOND_AT[l + 1]) l++; return l; }
    static final String[] MOODS = {"Starved", "Hungry", "Content", "Well fed"};
    static final int[] MOOD_COL = {0xFFFF3030, 0xFFFF9040, 0xFFE8D8D0, 0xFF90E080};

    public static void send(int action, int arg) { AddonNetwork.CHANNEL.sendToServer(new HungerPackets.Act(action, arg)); }

    // ------------------------------------------------------------------ the model of each sword
    @Mod.EventBusSubscriber(modid = "iron_spell_grimoire", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent
        public static void setup(FMLClientSetupEvent e) {
            e.enqueueWork(() -> net.minecraft.client.renderer.item.ItemProperties.register(com.ironspellgrimoire.registry.AddonItemRegistry.HUNGER_BLADE.get(),
                    ResourceLocation.fromNamespaceAndPath("iron_spell_grimoire", "form"),
                    (stack, level, entity, seed) -> com.ironspellgrimoire.item.HungerBladeItem.form(stack) / 32F));
        }
    }

    // ------------------------------------------------------------------ the meter
    private static float shown = 60;

    @SubscribeEvent
    public static void hud(RenderGuiEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || !equipped || ParasiteClient.wheelKind(mc) != 3) return;
        GuiGraphics g = e.getGuiGraphics();
        shown += (hunger - shown) * 0.2F;
        int m = HungerFx.mood(hunger);
        int x = 6, y = g.guiHeight() - 24, w = 92;
        double t = mc.level.getGameTime() + e.getPartialTick();
        g.fill(x - 1, y - 1, x + w + 1, y + 7, 0xE0100306);
        int fw = Math.round(w * Mth.clamp(shown / 100F, 0, 1));
        boolean beat = m <= 1 && ((int) (t / (m == 0 ? 5 : 10))) % 2 == 0;
        g.fill(x, y, x + fw, y + 6, beat ? 0xFFFF4050 : 0xFFA01424);
        g.fill(x, y, x + fw, y + 2, 0xFFE04858);
        for (int k : new int[]{10, 30, 70}) g.fill(x + w * k / 100, y, x + w * k / 100 + 1, y + 6, 0x90100306);   // the mood marks
        int cur = load[Math.max(0, Math.min(3, sel))];
        String name = HungerFx.NAMES[has(cur) ? cur : 0];
        g.drawString(mc.font, name, x, y - 10, 0xFFFF8080, true);
        g.drawString(mc.font, MOODS[m], x + w + 6, y - 1, MOOD_COL[m], true);
        String b = "Bond " + bondLevel() + "  " + growth + " Growth";
        g.drawString(mc.font, b, x, y + 9, 0xFFC8A8A8, true);
        if (pact > 0) g.drawString(mc.font, "AWAKENED PACT " + pact + "s", x, y - 20, ((int) (t / 4)) % 2 == 0 ? 0xFFFFE060 : 0xFFFF8040, true);
    }
}
