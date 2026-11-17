package com.ironspellgrimoire.client;

import com.ironspellgrimoire.network.AddonNetwork;
import com.ironspellgrimoire.network.ParasitePackets;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * The Parasite Grimoire on your side (server/ParasiteFx): what the strain is (sent by the server), the biomass and
 * frenzy meter, the G key (the wheel - ParasiteWheel; with the System Grimoire it commands your shadows instead), and
 * the movement, which has to be done where the player moves - here: the high sprint-jump, running up walls, gliding,
 * and the Meteor Fall slam. And the Spine Launcher fires on the attack button.
 */
@Mod.EventBusSubscriber(modid = "iron_spell_grimoire", value = Dist.CLIENT)
public final class ParasiteClient {
    private ParasiteClient() {}

    public static final KeyMapping KEY = new KeyMapping("key.iron_spell_grimoire.parasite_wheel", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, "key.categories.iron_spell_grimoire");

    // ------------------------------------------------------------------ the strain, as the server last sent it
    public static boolean equipped, frenzied, bulwark, hide;
    public static float biomass, frenzy;
    public static int ep, spent, devoured, form, strain;
    public static int[] ranks = new int[13];

    public static void sync(ParasitePackets.Sync p) {
        equipped = p.equipped; biomass = p.biomass; frenzy = p.frenzy; frenzied = p.frenzied; ep = p.ep; spent = p.spent; devoured = p.devoured;
        form = p.form; bulwark = p.bulwark; hide = p.hide; strain = p.strain; ranks = p.ranks.length >= 13 ? p.ranks : java.util.Arrays.copyOf(p.ranks, 13);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) ParasiteArms.look(new ParasitePackets.Look(mc.player.getId(), equipped ? form : 0, equipped && bulwark, equipped && hide, frenzied));
        if (p.open) EvolutionScreen.open();
        else if (mc.screen instanceof EvolutionScreen s) s.refresh();
    }

    static int rank(int node) { return node < ranks.length ? ranks[node] : 0; }

    public static void send(int action, int arg) { AddonNetwork.CHANNEL.sendToServer(new ParasitePackets.Act(action, arg)); }

    /** Which wheel G opens: 1 the Parasite forms, 2 the shadow commands, 0 none. */
    static int wheelKind(Minecraft mc) {
        if (mc.player == null) return 0;
        var b = io.redspace.ironsspellbooks.api.util.Utils.getPlayerSpellbookStack(mc.player);
        if (b == null) return 0;
        if (b.is(com.ironspellgrimoire.registry.AddonItemRegistry.PARASITE_GRIMOIRE.get())) return 1;
        if (b.is(com.ironspellgrimoire.registry.AddonItemRegistry.SHADOW_GRIMOIRE.get())) return 2;
        return 0;
    }

    // ------------------------------------------------------------------ the key
    @Mod.EventBusSubscriber(modid = "iron_spell_grimoire", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Keys {
        @SubscribeEvent public static void register(RegisterKeyMappingsEvent e) { e.register(KEY); }
    }

    // ------------------------------------------------------------------ movement
    private static int wallTicks, spineTicks;
    private static boolean wallUsed, gliding, shiftWas;

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer pl = mc.player;
        if (pl == null || mc.level == null) return;
        while (KEY.consumeClick()) {
            if (mc.screen != null) continue;
            int kind = wheelKind(mc);
            if (kind == 0) pl.displayClientMessage(net.minecraft.network.chat.Component.literal("Only the Parasite and System grimoires have a wheel")
                    .withStyle(net.minecraft.ChatFormatting.GRAY), true);
            else mc.setScreen(new ParasiteWheel(kind));
        }
        boolean on = equipped && wheelKind(mc) == 1 && !pl.isSpectator() && !pl.getAbilities().flying && !pl.isPassenger();
        if (!on) { stopGlide(); return; }
        boolean ground = pl.onGround() || pl.isInWater() || pl.onClimbable();
        Vec3 v = pl.getDeltaMovement();
        if (ground) { wallTicks = 0; wallUsed = false; stopGlide(); }
        // Wall Runner: forward into a wall in mid-air runs up it
        int wr = rank(1);
        boolean walling = false;
        if (!ground && wr > 0 && !wallUsed && pl.horizontalCollision && mc.options.keyUp.isDown()) {
            if (wallTicks < 20 * wr) {
                pl.setDeltaMovement(v.x, Math.max(v.y, 0.32), v.z);
                pl.fallDistance = 0;
                wallTicks++;
                walling = true;
            } else wallUsed = true;
        }
        // Glider: hold jump while falling
        int gl = rank(2);
        if (!ground && !walling && gl > 0 && mc.options.keyJump.isDown() && v.y < -0.05 && !pl.isFallFlying()) {
            Vec3 look = pl.getLookAngle().multiply(1, 0, 1);
            look = look.lengthSqr() < 1e-4 ? Vec3.ZERO : look.normalize();
            double speed = Math.max(v.horizontalDistance(), 0.30 + 0.06 * gl);
            pl.setDeltaMovement(look.x * speed, Math.max(v.y, gl >= 2 ? -0.07 : -0.11), look.z * speed);
            pl.fallDistance = 0;
            if (!gliding) { gliding = true; send(6, 1); }
        } else if (gliding && (ground || !mc.options.keyJump.isDown())) stopGlide();
        // Meteor Fall: sneak in mid-air, high enough
        boolean shift = mc.options.keyShift.isDown();
        if (shift && !shiftWas && !ground && mc.screen == null) {
            double h = heightAboveGround(pl);
            if (h >= 3) {
                pl.setDeltaMovement(v.x * 0.2, -2.2, v.z * 0.2);
                send(5, (int) Math.min(2000, h * 10));
                stopGlide();
            }
        }
        shiftWas = shift;
        // the Spine Launcher keeps firing while the attack button is held
        if (form == 3 && pl.getMainHandItem().isEmpty() && mc.screen == null && mc.options.keyAttack.isDown()) {
            if (spineTicks-- <= 0) { send(4, 0); spineTicks = frenzied ? 5 : 8; }
        } else spineTicks = 0;
    }

    private static void stopGlide() { if (gliding) { gliding = false; send(6, 0); } }

    private static double heightAboveGround(LocalPlayer pl) {
        BlockPos at = pl.blockPosition();
        for (int dy = 0; dy <= 40; dy++) {
            BlockPos b = at.below(dy);
            if (!pl.level().getBlockState(b).getCollisionShape(pl.level(), b).isEmpty()) return pl.getY() - (b.getY() + 1);
        }
        return 40;
    }

    /** Leap: sprint-jumps leap high (and a little further). */
    @SubscribeEvent
    public static void jump(LivingEvent.LivingJumpEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (e.getEntity() != mc.player || !equipped || wheelKind(mc) != 1 || !mc.player.isSprinting()) return;
        LocalPlayer pl = mc.player;
        Vec3 v = pl.getDeltaMovement();
        Vec3 look = pl.getLookAngle().multiply(1, 0, 1);
        look = look.lengthSqr() < 1e-4 ? Vec3.ZERO : look.normalize();
        double up = 0.2 + 0.07 * rank(0);
        pl.setDeltaMovement(v.x + look.x * 0.15, v.y + up, v.z + look.z * 0.15);
    }

    /** A click with the Spine Launcher fires at once. */
    @SubscribeEvent
    public static void click(InputEvent.InteractionKeyMappingTriggered e) {
        Minecraft mc = Minecraft.getInstance();
        if (!e.isAttack() || mc.player == null || !equipped || form != 3 || !mc.player.getMainHandItem().isEmpty()) return;
        if (spineTicks <= 0) { send(4, 0); spineTicks = frenzied ? 5 : 8; }
    }

    // ------------------------------------------------------------------ the meter
    private static float shownBio, shownFrenzy;

    @SubscribeEvent
    public static void hud(RenderGuiEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || !equipped || wheelKind(mc) != 1) return;
        GuiGraphics g = e.getGuiGraphics();
        shownBio += (biomass - shownBio) * 0.2F;
        shownFrenzy += (frenzy - shownFrenzy) * 0.2F;
        int x = 6, y = g.guiHeight() - 30, w = 92;
        double t = mc.level.getGameTime() + e.getPartialTick();
        // biomass: a fleshy red bar with a pulse running through it
        g.fill(x - 1, y - 1, x + w + 1, y + 7, 0xE0100306);
        int fw = Math.round(w * Mth.clamp(shownBio / 100F, 0, 1));
        g.fill(x, y, x + fw, y + 6, 0xFFB01828);
        g.fill(x, y, x + fw, y + 2, 0xFFE04050);
        int pulse = (int) ((t * 1.5) % (w + 20)) - 10;
        if (pulse > 0 && pulse < fw) g.fill(x + pulse, y, Math.min(x + fw, x + pulse + 6), y + 6, 0x60FFD0D0);
        for (int k = 1; k < 4; k++) g.fill(x + w * k / 4, y, x + w * k / 4 + 1, y + 6, 0x80100306);   // quarters
        g.drawString(mc.font, "Biomass " + Math.round(biomass), x, y - 10, 0xFFE8A0A8, true);
        // frenzy: a thin bar under it, flashing when frenzied
        int fy = y + 9;
        g.fill(x - 1, fy - 1, x + w + 1, fy + 3, 0xE0100306);
        int frw = Math.round(w * Mth.clamp(shownFrenzy / 100F, 0, 1));
        boolean flash = frenzied && ((int) (t / 4)) % 2 == 0;
        g.fill(x, fy, x + frw, fy + 2, flash ? 0xFFFFFFFF : 0xFFFF6A00);
        String tag = frenzied ? "FRENZIED" : form > 0 ? com.ironspellgrimoire.server.ParasiteFx.FORMS[Math.min(form, 5)] : "";
        if (!tag.isEmpty()) g.drawString(mc.font, tag, x + w + 6, y - 1, frenzied ? 0xFFFF5040 : 0xFFD8C8C8, true);
        if (ep > 0) g.drawString(mc.font, ep + " EP", x + w + 6, fy - 2, 0xFFFFC060, true);
    }
}
