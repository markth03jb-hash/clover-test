package com.example.arcaneaddon.client;

import com.example.arcaneaddon.AddonClientConfig;
import com.example.arcaneaddon.server.GrimoireMastery;
import com.example.arcaneaddon.spells.AnimNames;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The grimoire, opened (sneak + use while holding it). One spread per spell, in the book's order:
 *   left page   the spell's name, a moving preview - you, in the spell's own cast animation, with its icon and the
 *               grimoire's colours turning behind - and the mastery level it unlocks at
 *   right page  what it does, then everything about it: base damage and the spell's own numbers, mana cost,
 *               cooldown, cast type
 * A spell the grimoire has not given you yet (its mastery level not reached) is just an empty spread.
 * Turn pages with the arrows, the arrow keys or the mouse wheel. The effect quality can be switched at the top.
 */
public class GrimoireBookScreen extends Screen {
    private static final int BW = 300, BH = 190;   // the open book

    private final ItemStack stack;
    private final int book;
    private final List<String> ids = new ArrayList<>();
    private final List<Integer> unlockAt = new ArrayList<>();
    private int page = 0;
    private double flipAt = -100;   // when the last page turn started (screen ticks)
    private int flipDir = 1;
    private int ticks = 0;
    private long openedAt;

    // the moving preview: a copy of you, playing the spell's cast animation
    private RemotePlayer model;
    private ModifierLayer<IAnimation> layer;
    private int animTick = 0;

    public GrimoireBookScreen(ItemStack stack) {
        super(stack.getHoverName());
        this.stack = stack.copy();
        GrimoireMastery.Book b = GrimoireMastery.bookOf(stack);
        this.book = b == null ? 0 : Math.max(0, GrimoireMastery.BOOKS.indexOf(b));
        if (b != null) for (Map.Entry<String, Integer> e : b.unlocks().entrySet()) { ids.add(e.getKey()); unlockAt.add(e.getValue()); }
    }

    public static void open(ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        if (GrimoireMastery.bookOf(stack) == null) return;
        mc.setScreen(new GrimoireBookScreen(stack));
        if (mc.player != null) mc.player.playSound(SoundEvents.BOOK_PAGE_TURN, 1.0F, 0.8F);
    }

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        openedAt = mc.level == null ? 0 : mc.level.getGameTime();
        try {
            model = new RemotePlayer(mc.level, mc.player.getGameProfile());
            model.setItemSlot(EquipmentSlot.MAINHAND, mc.player.getMainHandItem().copy());
            model.setItemSlot(EquipmentSlot.OFFHAND, mc.player.getOffhandItem().copy());
            layer = new ModifierLayer<>();
            PlayerAnimationAccess.getPlayerAnimLayer(model).addAnimLayer(1000, layer);
        } catch (Throwable t) { model = null; layer = null; }
        restartPreview();
    }

    @Override public boolean isPauseScreen() { return false; }

    private boolean unlocked(int i) {
        if (!MasteryClient.enabled()) return true;
        return GrimoireMastery.level(stack) >= unlockAt.get(i);
    }

    private AbstractSpell spell(int i) {
        try { return SpellRegistry.getSpell(ResourceLocation.fromNamespaceAndPath("arcane_addon", ids.get(i))); } catch (Throwable t) { return null; }
    }

    private void restartPreview() {
        animTick = 0;
        if (layer == null || ids.isEmpty() || !unlocked(page)) { if (layer != null) layer.setAnimation(null); return; }
        try {
            AbstractSpell sp = spell(page);
            String name = sp == null ? null : AnimNames.of(sp.getCastStartAnimation());
            KeyframeAnimation anim = name == null ? null : PlayerAnimationRegistry.getAnimation(ResourceLocation.tryParse(name));
            layer.setAnimation(anim == null ? null : new KeyframeAnimationPlayer(anim));
        } catch (Throwable t) { layer.setAnimation(null); }
    }

    private void turn(int dir) {
        if (ids.isEmpty()) return;
        int np = Mth.clamp(page + dir, 0, ids.size() - 1);
        if (np == page) return;
        page = np; flipAt = ticks; flipDir = dir;
        Minecraft.getInstance().player.playSound(SoundEvents.BOOK_PAGE_TURN, 1.0F, 0.9F + 0.2F * (float) Math.random());
        restartPreview();
    }

    @Override
    public void tick() {
        ticks++;
        if (model != null) {
            try { model.tickCount++; PlayerAnimationAccess.getPlayerAnimLayer(model).tick(); } catch (Throwable ignored) {}
            animTick++;
            if (layer != null && (layer.getAnimation() == null || !layer.getAnimation().isActive()) && animTick > 30) restartPreview();   // loop
        }
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_D) { turn(1); return true; }
        if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_A) { turn(-1); return true; }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        turn(delta < 0 ? 1 : -1);
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int x0 = (width - BW) / 2, y0 = (height - BH) / 2;
        if (inside(mx, my, x0 + 8, y0 + BH - 18, 18, 12)) { turn(-1); return true; }
        if (inside(mx, my, x0 + BW - 26, y0 + BH - 18, 18, 12)) { turn(1); return true; }
        if (inside(mx, my, x0 + BW - 92, y0 - 14, 92, 12)) {   // effect quality
            AddonClientConfig.Quality[] q = AddonClientConfig.Quality.values();
            AddonClientConfig.setQuality(q[(AddonClientConfig.quality().ordinal() + 1) % q.length]);
            Minecraft.getInstance().player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.6F, 1.0F);
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) { return mx >= x && mx < x + w && my >= y && my < y + h; }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        Minecraft mc = Minecraft.getInstance();
        Font f = font;
        int[] c = GrimoireWheel.themeColors(book);   // pageIn, pageOut, trim, accent, panel, title
        int x0 = (width - BW) / 2, y0 = (height - BH) / 2, mid = x0 + BW / 2;
        double time = (mc.level == null ? 0 : mc.level.getGameTime() - openedAt) + pt;
        float flip = (float) Mth.clamp(1.0 - (ticks + pt - flipAt) / 5.0, 0.0, 1.0);   // a page takes 5 ticks to turn

        // the cover and the two pages
        g.fill(x0 - 6, y0 - 6, x0 + BW + 6, y0 + BH + 6, 0xFF000000 | dark(c[2], 0.35F));
        g.fill(x0 - 4, y0 - 4, x0 + BW + 4, y0 + BH + 4, 0xFF000000 | c[4]);
        page(g, x0, y0, BW / 2 - 2, BH, c, true);
        page(g, mid + 2, y0, BW / 2 - 2, BH, c, false);
        g.fill(mid - 2, y0, mid + 2, y0 + BH, 0xFF000000 | dark(c[2], 0.5F));   // the spine
        // trim lines round each page
        int trim = 0xFF000000 | c[2];
        frame(g, x0 + 4, y0 + 4, BW / 2 - 10, BH - 8, trim);
        frame(g, mid + 6, y0 + 4, BW / 2 - 10, BH - 8, trim);

        // effect quality switch, above the book
        String q = "Effects: " + AddonClientConfig.quality().name();
        boolean hq = inside(mx, my, x0 + BW - 92, y0 - 14, 92, 12);
        g.drawString(f, q, x0 + BW - f.width(q), y0 - 13, hq ? 0xFFFFFFFF : 0xFF000000 | c[5], true);

        if (ids.isEmpty()) return;
        int title = 0xFF000000 | c[5], ink = 0xFF000000 | ink(c), accent = 0xFF000000 | c[3];
        // page arrows and number
        String num = (page + 1) + " / " + ids.size();
        g.drawString(f, num, mid - f.width(num) / 2, y0 + BH - 15, ink, false);
        if (page > 0) g.drawString(f, "<", x0 + 12, y0 + BH - 16, inside(mx, my, x0 + 8, y0 + BH - 18, 18, 12) ? 0xFFFFFFFF : title, true);
        if (page < ids.size() - 1) g.drawString(f, ">", x0 + BW - 18, y0 + BH - 16, inside(mx, my, x0 + BW - 26, y0 + BH - 18, 18, 12) ? 0xFFFFFFFF : title, true);

        if (unlocked(page)) {
            AbstractSpell sp = spell(page);
            if (sp != null) {
                drawLeft(g, f, sp, x0 + 10, y0 + 10, BW / 2 - 22, BH - 32, c, title, ink, accent, time, pt);
                drawRight(g, f, sp, page, mid + 12, y0 + 12, BW / 2 - 24, BH - 34, title, ink, accent);
            }
        }
        // a page turning over: a sheet sweeping across the spread
        if (flip > 0F) {
            float k = 1F - flip;
            int half = BW / 2;
            int w = (int) (half * Math.abs(Mth.cos(k * Mth.PI)));
            boolean firstHalf = k < 0.5F;
            int px = flipDir > 0 ? (firstHalf ? mid + 2 : mid - 2 - w) : (firstHalf ? mid - 2 - w : mid + 2);
            g.fill(px, y0, px + w, y0 + BH, 0xFF000000 | (firstHalf ? c[0] : c[1]));
            g.fill(px, y0, px + Math.max(1, w), y0 + 1, trim);
        }
    }

    /** Left page: name, the moving preview, the mastery level. */
    private void drawLeft(GuiGraphics g, Font f, AbstractSpell sp, int x, int y, int w, int h, int[] c, int title, int ink, int accent, double time, float pt) {
        Minecraft mc = Minecraft.getInstance();
        Component name = sp.getDisplayName(mc.player);
        g.drawCenteredString(f, name, x + w / 2, y + 2, title);
        // the preview window
        int px = x + 6, py = y + 16, pw = w - 12, ph = h - 40;
        g.fill(px - 1, py - 1, px + pw + 1, py + ph + 1, 0xFF000000 | c[2]);
        g.fill(px, py, px + pw, py + ph, 0xFF000000 | dark(c[4], 1.0F));
        g.enableScissor(px, py, px + pw, py + ph);
        int cx = px + pw / 2, cy = py + ph / 2;
        // the grimoire's colours turning behind: rays and a slow ring of motes
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI / 6 + time * 0.01;
            int lx = cx + (int) (Math.cos(a) * pw), ly = cy + (int) (Math.sin(a) * pw);
            line(g, cx, cy, lx, ly, (0x18 << 24) | c[3]);
        }
        for (int i = 0; i < 16; i++) {
            double a = i * Math.PI / 8 + time * 0.03;
            double r = pw * 0.36 + Math.sin(time * 0.1 + i) * 3;
            int mx = cx + (int) (Math.cos(a) * r), my = cy + (int) (Math.sin(a) * r * 0.5) + ph / 5;
            int s = (i % 3 == 0) ? 2 : 1;
            g.fill(mx, my, mx + s, my + s, (0xB0 << 24) | c[3]);
        }
        // the spell's icon, pulsing behind the figure
        try {
            ResourceLocation icon = sp.getSpellIconResource();
            float pulse = 1F + 0.06F * (float) Math.sin(time * 0.2);
            g.pose().pushPose();
            g.pose().translate(cx, py + ph * 0.32F, 0);
            g.pose().scale(2.2F * pulse, 2.2F * pulse, 1F);
            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            g.setColor(1F, 1F, 1F, 0.45F);
            g.blit(icon, -8, -8, 0, 0, 16, 16, 16, 16);
            g.setColor(1F, 1F, 1F, 1F);
            g.pose().popPose();
        } catch (Throwable ignored) {}
        // you, casting it
        if (model != null) {
            try {
                float sway = (float) Math.sin(time * 0.03) * 40F;
                InventoryScreen.renderEntityInInventoryFollowsMouse(g, cx, py + ph - 8, (int) (ph * 0.36F), -sway, -ph * 0.08F, model);
            } catch (Throwable ignored) {}
        }
        g.disableScissor();
        // unlocks at
        int lvl = unlockAt.get(page);
        String u = "Mastery Lv " + lvl;
        g.drawCenteredString(f, u, x + w / 2, y + h - 18, accent);
    }

    /** Right page: description and every number. */
    private void drawRight(GuiGraphics g, Font f, AbstractSpell sp, int i, int x, int y, int w, int h, int title, int ink, int accent) {
        Minecraft mc = Minecraft.getInstance();
        int lvl = 1;
        try { lvl = sp.getLevelFor(1, mc.player); } catch (Throwable ignored) {}
        int yy = y;
        String key = "spell.arcane_addon." + ids.get(i) + ".guide";
        if (I18n.exists(key)) {
            List<FormattedCharSequence> lines = f.split(Component.translatable(key), w);
            for (FormattedCharSequence l : lines) {
                if (yy > y + h * 0.55) break;
                g.drawString(f, l, x, yy, ink, false);
                yy += 9;
            }
        }
        yy += 5;
        g.fill(x, yy - 3, x + w, yy - 2, (0x80 << 24) | (accent & 0xFFFFFF));
        List<Component> info = new ArrayList<>();
        try {
            List<? extends Component> u = sp.getUniqueInfo(lvl, mc.player);
            if (u.isEmpty()) info.add(Component.translatable("ui.irons_spellbooks.damage", String.format("%.1f", sp.getSpellPower(lvl, mc.player))));
            else info.addAll(u);
        } catch (Throwable ignored) {}
        try { info.add(Component.translatable("ui.irons_spellbooks.mana_cost", sp.getManaCost(lvl))); } catch (Throwable ignored) {}
        try { info.add(Component.translatable("tooltip.irons_spellbooks.cooldown_length_seconds", Utils.timeFromTicks(sp.getSpellCooldown(), 1))); } catch (Throwable ignored) {}
        try { info.add(Component.literal("Cast: " + pretty(sp.getCastType().name()))); } catch (Throwable ignored) {}
        for (Component c : info) {
            for (FormattedCharSequence l : f.split(c, w)) {
                if (yy > y + h - 4) return;
                g.drawString(f, l, x, yy, title, false);
                yy += 10;
            }
        }
    }

    private static String pretty(String s) {
        s = s.toLowerCase().replace('_', ' ');
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static void page(GuiGraphics g, int x, int y, int w, int h, int[] c, boolean left) {
        // a gradient from the edge colour at the outside to the inside colour at the spine
        int steps = 12;
        for (int i = 0; i < steps; i++) {
            float t = i / (float) (steps - 1);
            int col = mixc(c[1], c[0], left ? t : 1 - t);
            int a = x + w * i / steps, b = x + w * (i + 1) / steps;
            g.fill(a, y, b, y + h, 0xFF000000 | col);
        }
    }

    private static void frame(GuiGraphics g, int x, int y, int w, int h, int col) {
        g.fill(x, y, x + w, y + 1, col); g.fill(x, y + h - 1, x + w, y + h, col);
        g.fill(x, y, x + 1, y + h, col); g.fill(x + w - 1, y, x + w, y + h, col);
    }

    private static void line(GuiGraphics g, int x0, int y0, int x1, int y1, int col) {
        int n = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0)) / 2;
        for (int i = 0; i <= n; i++) {
            int x = x0 + (x1 - x0) * i / Math.max(1, n), y = y0 + (y1 - y0) * i / Math.max(1, n);
            g.fill(x, y, x + 2, y + 1, col);
        }
    }

    /** Readable ink on the page: light pages get dark ink, dark pages get light ink. */
    private static int ink(int[] c) {
        int p = c[0];
        float lum = ((p >> 16 & 255) * 0.3F + (p >> 8 & 255) * 0.59F + (p & 255) * 0.11F) / 255F;
        return lum > 0.5F ? 0x221A14 : 0xD8D0C4;
    }

    private static int dark(int rgb, float k) {
        return ((int) ((rgb >> 16 & 255) * k) << 16) | ((int) ((rgb >> 8 & 255) * k) << 8) | (int) ((rgb & 255) * k);
    }

    private static int mixc(int a, int b, float t) {
        int r = (int) Mth.lerp(t, a >> 16 & 255, b >> 16 & 255), gg = (int) Mth.lerp(t, a >> 8 & 255, b >> 8 & 255), bl = (int) Mth.lerp(t, a & 255, b & 255);
        return r << 16 | gg << 8 | bl;
    }
}
