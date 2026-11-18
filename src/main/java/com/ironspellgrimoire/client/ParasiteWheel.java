package com.ironspellgrimoire.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;

/**
 * The G wheel: hold G, point at a slice, let go (or click). With the Parasite Grimoire its slices are the arm forms,
 * the Bulwark, the Chitin Hide and Retract; with the System Grimoire they command your shadows. Raw, ragged red flesh
 * for the parasite; black with blue fire for the shadows.
 */
public class ParasiteWheel extends Screen {
    /** 1 parasite forms, 2 shadow commands */
    final int kind;
    private int hover = -1;
    private float open;
    private long lastNs;

    /** The parasite's slices: 0-6 the arm forms (form = slice + 1), then the Bulwark, the Hide, the strain's art, the
     *  Final Form and Retract. Short labels on the wheel; the full name under it. */
    private static final String[] P_NAMES = {"Reaper", "Talons", "Spines", "Titan", "Lance", "Whip", "Ram", "Shield", "Hide", "Art", "Final", "Retract"};
    private static final String[] P_FULL = {"Reaper Edge", "Rending Talons", "Spine Launcher", "Titan Fist", "Bone Lance", "Whip-Spear", "Battering Ram",
            "Bulwark", "Chitin Hide", "Strain Art", "Final Form", "Retract"};
    private static final String[] P_DESC = {
            "A great hooked blade: heavy cuts that sweep all in front.",
            "Fast hooked claws: every cut bleeds.",
            "A bone barrel: attack to fire spines.",
            "A huge armoured fist: slow, crushing, a shockwave.",
            "A long bone spike: long reach, it runs through.",
            "A long hooking spine: drags foes in.",
            "A blunt ram: guards you, and sprinting rams them.",
            "Your other arm: a spined shield.",
            "Armour plates over your body (eats biomass).",
            "Your awakened strain's signature move.",
            "30 s as the strain itself (100 biomass).",
            "Your arms go back to normal."};
    private static final String[] S_NAMES = {"Hunt", "Rally", "Stand Down", "Sink / Rise", "Army", "Status", "Market", "Loadout"};
    private static final String[] S_DESC = {
            "Every shadow at your side goes for what you look at.",
            "Your shadows come back to your side at once.",
            "Your shadows forget what they were fighting.",
            "Your shadows sink into your shadow, or rise again.",
            "Open the Shadow Army.",
            "Open your Status: level, stats, the Daily Trial, Job Change.",
            "Open the Market.",
            "Swap to your next saved army loadout (save them in the Army menu)."};

    public ParasiteWheel(int kind) { super(Component.literal(kind == 1 || kind == 4 ? "Parasite" : kind == 3 ? "Hunger Blade" : "Shadows")); this.kind = kind; }

    private String[] names() {
        if (kind == 4) {   // the quick ring: the four pinned forms
            String[] n = new String[4];
            for (int i = 0; i < 4; i++) { int f = ParasiteClient.pins[i]; n[i] = f > 0 ? com.ironspellgrimoire.server.ParasiteFx.FORMS[f] : "Empty"; }
            return n;
        }
        if (kind == 3) {   // the Hunger Blade: the four swords you carry
            String[] n = new String[4];
            for (int i = 0; i < 4; i++) { int f = HungerClient.load[i]; n[i] = HungerClient.has(f) ? com.ironspellgrimoire.server.HungerFx.NAMES[f] : "Empty"; }
            return n;
        }
        return kind == 1 ? P_NAMES : S_NAMES;
    }

    /** Whether a slice can be used now (and lit as such). */
    /** Whether a form (1-7) is evolved, as far as this side knows (the server decides). */
    static boolean formReady(int f) {
        return switch (f) {
            case 3 -> ParasiteClient.rank(4) > 0;
            case 5 -> ParasiteClient.rank(5) > 0;
            case 6 -> ParasiteClient.rank(13) > 0;
            case 7 -> ParasiteClient.rank(14) > 0;
            default -> f > 0;
        };
    }

    private boolean usable(int i) {
        if (kind == 4) return formReady(ParasiteClient.pins[i]);
        if (kind == 3) return HungerClient.has(HungerClient.load[i]);
        if (kind != 1) return true;
        return switch (i) {
            case 2 -> ParasiteClient.rank(4) > 0;
            case 4 -> ParasiteClient.rank(5) > 0;
            case 5 -> ParasiteClient.rank(13) > 0;
            case 6 -> ParasiteClient.rank(14) > 0;
            case 7 -> ParasiteClient.rank(6) > 0;
            case 8 -> ParasiteClient.rank(7) > 0;
            case 9 -> ParasiteClient.awakened && ParasiteClient.artCd == 0;
            case 10 -> ParasiteClient.awakened && ParasiteClient.finalCd == 0 && ParasiteClient.biomass >= 100;
            default -> true;
        };
    }

    private boolean active(int i) {
        if (kind == 4) return ParasiteClient.form == ParasiteClient.pins[i] && ParasiteClient.pins[i] > 0;
        if (kind == 3) return HungerClient.sel == i;
        if (kind != 1) return false;
        if (i < 7) return ParasiteClient.form == i + 1;
        if (i == 7) return ParasiteClient.bulwark;
        if (i == 8) return ParasiteClient.hide;
        if (i == 9) return ParasiteClient.artLeft > 0;
        if (i == 10) return ParasiteClient.finalLeft > 0;
        return false;
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        long ns = System.nanoTime();
        float dt = lastNs == 0 ? 0 : Math.min(0.1F, (ns - lastNs) / 1e9F);
        lastNs = ns;
        open = Math.min(1F, open + dt * 7F);
        float e = 1 - (1 - open) * (1 - open);
        String[] nm = names();
        int n = nm.length, cx = width / 2, cy = height / 2;
        double R = Math.min(kind == 1 ? 112 : 96, height / 2.0 - 34);
        double r0 = 34 * e, r1 = R * e;
        double dx = mx - cx, dy = my - cy;
        double ang = Math.atan2(dx, -dy);
        if (ang < 0) ang += Math.PI * 2;
        double dist = Math.sqrt(dx * dx + dy * dy);
        hover = dist > 20 ? (int) Math.floor((ang + Math.PI / n) / (Math.PI * 2 / n)) % n : -1;
        if (forceHover >= 0) { hover = forceHover % n; open = 1F; e = 1F; r0 = 34; r1 = R; }

        boolean shadow = kind == 2;
        float[] base = shadow ? new float[]{0.03F, 0.04F, 0.09F} : new float[]{0.16F, 0.03F, 0.05F};
        float[] lit = shadow ? new float[]{0.18F, 0.45F, 1.0F} : new float[]{0.85F, 0.12F, 0.16F};
        float[] rim = shadow ? new float[]{0.35F, 0.7F, 1.0F} : new float[]{0.62F, 0.62F, 0.66F};

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Matrix4f m = g.pose().last().pose();
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        double step = Math.PI * 2 / n, gap = 0.035;
        for (int i = 0; i < n; i++) {
            double a0 = i * step - step / 2 + gap, a1 = (i + 1) * step - step / 2 - gap;
            boolean h = i == hover, ok = usable(i), on = active(i);
            double out = r1 + (h ? 8 : 0);
            float k = ok ? 1F : 0.45F;
            float[] c = h && ok ? lit : base;
            ring(bb, m, cx, cy, r0, out, a0, a1, c[0] * 0.6F * k, c[1] * 0.6F * k, c[2] * 0.6F * k, 0.92F, c[0] * k, c[1] * k, c[2] * k, 0.92F, 10);
            ring(bb, m, cx, cy, out - 3, out, a0, a1, rim[0] * k, rim[1] * k, rim[2] * k, 0.95F, rim[0] * k, rim[1] * k, rim[2] * k, 0.95F, 10);
            if (on) ring(bb, m, cx, cy, r0, r0 + 4, a0, a1, lit[0], lit[1], lit[2], 1F, 1F, 0.85F, 0.85F, 1F, 10);
            if (!shadow) {   // a few bone barbs along the rim
                for (int b = 1; b <= 2; b++) {
                    double ba = a0 + (a1 - a0) * b / 3.0;
                    tri(bb, m, cx, cy, ba - 0.04, out, ba + 0.04, out, ba, out + 7 * e, rim[0] * k, rim[1] * k, rim[2] * k, 0.9F);
                }
            }
        }
        ring(bb, m, cx, cy, 0, r0 - 3, 0, Math.PI * 2, base[0] * 0.4F, base[1] * 0.4F, base[2] * 0.4F, 0.95F, base[0], base[1], base[2], 0.95F, 32);
        BufferUploader.drawWithShader(bb.end());
        RenderSystem.disableBlend();

        for (int i = 0; i < n; i++) {
            double a = i * step;
            double rr = r0 + (r1 - r0) * 0.6 + (i == hover ? 4 : 0);
            int tx = (int) (cx + Math.sin(a) * rr), ty = (int) (cy - Math.cos(a) * rr);
            String s = nm[i];
            int col = !usable(i) ? 0xFF7A6A6A : active(i) ? 0xFFFFE070 : 0xFFF2E6E6;
            if (kind == 3 && usable(i)) {   // the sword itself over its name
                g.renderItem(com.ironspellgrimoire.item.HungerBladeItem.of(com.ironspellgrimoire.registry.AddonItemRegistry.HUNGER_BLADE.get(), HungerClient.load[i]), tx - 8, ty - 28);
            }
            if (font.width(s) > 50 && s.contains(" ")) {
                String[] w = s.split(" ", 2);
                g.drawCenteredString(font, w[0], tx, ty - 9, col);
                g.drawCenteredString(font, w[1], tx, ty + 1, col);
            } else g.drawCenteredString(font, s, tx, ty - 4, col);
        }
        // the middle and the line under the wheel
        if (kind == 3) {   // the blade's mood in the middle; the pointed-at sword under the wheel
            int md = com.ironspellgrimoire.server.HungerFx.mood(HungerClient.hunger);
            g.drawCenteredString(font, Math.round(HungerClient.hunger) + "", cx, cy - 8, 0xFFFF7080);
            g.drawCenteredString(font, HungerClient.MOODS[md], cx, cy + 2, HungerClient.MOOD_COL[md]);
            if (hover >= 0) {
                int f = HungerClient.load[hover], y = cy + (int) (r1 + 14);
                g.drawCenteredString(font, HungerClient.has(f) ? com.ironspellgrimoire.server.HungerFx.NAMES[f] : "Empty slot", cx, y, 0xFFFFFFFF);
                g.drawCenteredString(font, HungerClient.has(f) ? com.ironspellgrimoire.server.HungerFx.PASSIVE[f] : "Carry a sword here from The Blade page.", cx, y + 11, 0xFFC8B8B8);
            }
            return;
        }
        boolean par = kind == 1 || kind == 4;
        String title = par ? (ParasiteClient.frenzied ? "FRENZY" : Math.round(ParasiteClient.biomass) + "") : "Shadows";
        g.drawCenteredString(font, title, cx, cy - 8, par ? 0xFFFF7080 : 0xFF8FC8FF);
        if (kind == 1) g.drawCenteredString(font, "biomass", cx, cy + 2, 0xFFB08890);
        if (hover >= 0) {
            String d = kind == 4 ? (ParasiteClient.pins[hover] > 0 ? P_DESC[ParasiteClient.pins[hover] - 1] + "  (sneak + G: the full wheel)" : "Pin a form here in Evolution (Guide tab).")
                    : (kind == 1 ? P_DESC : S_DESC)[hover];
            if (!usable(hover)) d = kind == 1 && hover == 9 ? (ParasiteClient.awakened ? "Ready in " + ParasiteClient.artCd + " s." : "Evolve every node to awaken your strain.")
                    : kind == 1 && hover == 10 ? (!ParasiteClient.awakened ? "Needs an awakened strain." : ParasiteClient.finalCd > 0 ? "Ready in " + ParasiteClient.finalCd + " s." : "Needs 100 biomass.")
                    : "Locked - evolve it on the Evolution page.";
            int y = cy + (int) (r1 + 14);
            g.drawCenteredString(font, (kind == 1 ? P_FULL : nm)[hover], cx, y, 0xFFFFFFFF);
            g.drawCenteredString(font, d, cx, y + 11, 0xFFC8B8B8);
        }
    }

    private void pick() {
        int h = hover;
        onClose();
        if (h < 0) return;
        if (kind == 3) { if (usable(h)) HungerClient.send(2, h); return; }
        if (kind == 4) { if (usable(h)) ParasiteClient.send(1, ParasiteClient.pins[h]); return; }
        if (kind == 1) {
            if (h < 7) ParasiteClient.send(1, h + 1);
            else if (h == 7) ParasiteClient.send(2, 0);
            else if (h == 8) ParasiteClient.send(3, 0);
            else if (h == 9) ParasiteClient.send(11, 0);
            else if (h == 10) ParasiteClient.send(12, 0);
            else ParasiteClient.send(1, 0);
        } else ParasiteClient.send(h == 7 ? 40 : 20 + h, 0);
    }

    @Override
    public boolean keyReleased(int key, int scan, int mods) {
        if (ParasiteClient.KEY.matches(key, scan)) { pick(); return true; }
        return super.keyReleased(key, scan, mods);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) { pick(); return true; }
        if (button == 1) { onClose(); return true; }
        return super.mouseClicked(mx, my, button);
    }

    // ------------------------------------------------------------------ drawing helpers
    private static float px(int cx, double a, double r) { return (float) (cx + Math.sin(a) * r); }
    private static float py(int cy, double a, double r) { return (float) (cy - Math.cos(a) * r); }
    private static void v(BufferBuilder bb, Matrix4f m, float x, float y, float r, float g, float b, float a) { bb.vertex(m, x, y, 0).color(r, g, b, a).endVertex(); }

    private static void ring(BufferBuilder bb, Matrix4f m, int cx, int cy, double r0, double r1, double a0, double a1,
                             float ir, float ig, float ib, float ia, float or, float og, float ob, float oa, int seg) {
        for (int s = 0; s < seg; s++) {
            double b0 = a0 + (a1 - a0) * s / seg, b1 = a0 + (a1 - a0) * (s + 1) / seg;
            float x00 = px(cx, b0, r0), y00 = py(cy, b0, r0), x01 = px(cx, b0, r1), y01 = py(cy, b0, r1);
            float x10 = px(cx, b1, r0), y10 = py(cy, b1, r0), x11 = px(cx, b1, r1), y11 = py(cy, b1, r1);
            v(bb, m, x00, y00, ir, ig, ib, ia); v(bb, m, x11, y11, or, og, ob, oa); v(bb, m, x01, y01, or, og, ob, oa);
            v(bb, m, x00, y00, ir, ig, ib, ia); v(bb, m, x10, y10, ir, ig, ib, ia); v(bb, m, x11, y11, or, og, ob, oa);
        }
    }

    private static void tri(BufferBuilder bb, Matrix4f m, int cx, int cy, double a0, double r0, double a1, double r1, double a2, double r2, float r, float g, float b, float al) {
        v(bb, m, px(cx, a0, r0), py(cy, a0, r0), r, g, b, al);
        v(bb, m, px(cx, a2, r2), py(cy, a2, r2), r, g, b, al);
        v(bb, m, px(cx, a1, r1), py(cy, a1, r1), r, g, b, al);
    }

    /** For the screenshots: the slice shown as pointed at (-1: follow the mouse). */
    public static int forceHover = -1;
}
