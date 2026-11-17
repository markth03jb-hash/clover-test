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
    /** Z: the Tendril Grapple (sneaking on the ground: Burrow). */
    /** B: Predator Sight. */
    public static final KeyMapping SIGHT = new KeyMapping("key.iron_spell_grimoire.parasite_sight", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, "key.categories.iron_spell_grimoire");
    public static final KeyMapping TENDRIL = new KeyMapping("key.iron_spell_grimoire.parasite_tendril", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, "key.categories.iron_spell_grimoire");

    // ------------------------------------------------------------------ the strain, as the server last sent it
    public static boolean equipped, frenzied, bulwark, hide;
    public static float biomass, frenzy;
    public static int ep, spent, devoured, form, strain;
    public static int[] ranks = new int[18];
    public static int muts, artCd, finalCd, finalLeft, sated, cap = 100, artLeft;
    public static int[] slots = {-1, -1, -1};
    public static boolean awakened, starving;
    public static int strain2, burrowLeft, evoRank, eaten, limb, nestStored = -1;
    public static boolean limbGone, sight;
    public static int look, nestCount, nestMax = 1;
    public static int[] organs = new int[3], adapt = new int[5];

    static boolean wears(int m) { for (int s : slots) if (s == m) return true; return false; }

    public static void sync(ParasitePackets.Sync p) {
        equipped = p.equipped; biomass = p.biomass; frenzy = p.frenzy; frenzied = p.frenzied; ep = p.ep; spent = p.spent; devoured = p.devoured;
        form = p.form; bulwark = p.bulwark; hide = p.hide; strain = p.strain; ranks = p.ranks.length >= 18 ? p.ranks : java.util.Arrays.copyOf(p.ranks, 18);
        int[] x = p.extra.length >= 12 ? p.extra : new int[]{0, -1, -1, -1, 0, 0, 0, 0, 0, 0, 100, 0};
        muts = x[0]; slots = new int[]{x[1], x[2], x[3]}; awakened = x[4] == 1; artCd = x[5]; finalCd = x[6]; finalLeft = x[7];
        sated = x[8]; starving = x[9] == 1; cap = Math.max(1, x[10]); artLeft = x[11];
        if (x.length >= 22) {
            strain2 = x[12]; organs = new int[]{x[13], x[14], x[15]}; adapt = new int[]{x[16], x[17], x[18], x[19], x[20]}; burrowLeft = x[21] ;
        }
        if (x.length >= 27) { evoRank = x[22]; eaten = x[23]; limb = x[24]; limbGone = x[25] == 1; nestStored = x[26]; }
        if (x.length >= 31) { sight = x[27] == 1; look = x[28]; nestCount = x[29]; nestMax = x[30]; }
        Minecraft mc = Minecraft.getInstance();
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
        @SubscribeEvent public static void register(RegisterKeyMappingsEvent e) { e.register(KEY); e.register(TENDRIL); e.register(SIGHT); }
    }

    // ------------------------------------------------------------------ movement
    private static int wallTicks, spineTicks;
    private static boolean wallUsed, gliding, shiftWas, clinging, jumpWas;

    /** A wall right beside the player (for Wall Cling). */
    private static boolean wallBeside(LocalPlayer pl) {
        var box = pl.getBoundingBox();
        for (var d : new double[][]{{0.2, 0}, {-0.2, 0}, {0, 0.2}, {0, -0.2}})
            if (!pl.level().noCollision(pl, box.move(d[0], 0, d[1]).deflate(0, 0.1, 0))) return true;
        return false;
    }

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
        while (SIGHT.consumeClick()) if (on && mc.screen == null) send(27, 0);
        while (TENDRIL.consumeClick()) {
            if (!on || mc.screen != null) continue;
            if (pl.isShiftKeyDown() && pl.onGround()) { if (rank(19) > 0) send(14, 0); }
            else if (rank(18) > 0) send(13, 0);
            else if (burrowLeft > 0) send(14, 0);
        }
        if (!on) { stopGlide(); clinging = false; return; }
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
        // Wall Cling: sneak in mid-air against a wall to stick; jump to pounce where you look
        boolean shift = mc.options.keyShift.isDown(), jump = mc.options.keyJump.isDown();
        if (rank(20) > 0 && !ground && shift && (clinging || wallBeside(pl))) {
            if (!clinging) { clinging = true; jumpWas = jump; }
            pl.setDeltaMovement(0, 0, 0);
            pl.fallDistance = 0;
            if (jump && !jumpWas) {   // pounce
                Vec3 look = pl.getLookAngle();
                pl.setDeltaMovement(look.x * 1.4, look.y * 1.0 + 0.35, look.z * 1.4);
                clinging = false;
                send(15, 0);
            }
            jumpWas = jump;
            shiftWas = shift;
            return;
        }
        clinging = false;
        jumpWas = jump;
        // Meteor Fall: sneak in mid-air, high enough
        if (shift && !shiftWas && !ground && mc.screen == null) {
            double h = heightAboveGround(pl);
            if (h >= 3) {
                pl.setDeltaMovement(v.x * 0.2, -2.2, v.z * 0.2);
                send(5, (int) Math.min(2000, h * 10));
                stopGlide();
            }
        }
        shiftWas = shift;
        // the Spine Launcher keeps firing while the attack button is held (and the Marrow Forge's lance throws)
        if ((form == 3 || (form == 5 && organs[2] == 2)) && pl.getMainHandItem().isEmpty() && mc.screen == null && mc.options.keyAttack.isDown()) {
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
        double up = (0.2 + 0.07 * rank(0)) * (wears(3) ? 1.5 : 1.0);   // Hollow Bones: higher still
        pl.setDeltaMovement(v.x + look.x * 0.15, v.y + up, v.z + look.z * 0.15);
    }

    /** A click with the Spine Launcher fires at once; use, with the Bulwark or the Battering Ram out, parries. */
    @SubscribeEvent
    public static void click(InputEvent.InteractionKeyMappingTriggered e) {
        Minecraft mc = Minecraft.getInstance();
        if (e.isUseItem() && mc.player != null && equipped && (bulwark || form == 7) && mc.player.getMainHandItem().isEmpty()
                && (mc.hitResult == null || mc.hitResult.getType() != net.minecraft.world.phys.HitResult.Type.ENTITY)) {
            send(18, 0);
            mc.player.swing(net.minecraft.world.InteractionHand.OFF_HAND);
            return;
        }
        if (!e.isAttack() || mc.player == null || !equipped || !(form == 3 || (form == 5 && organs[2] == 2)) || !mc.player.getMainHandItem().isEmpty()) return;
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
        int fw = Math.round(w * Mth.clamp(shownBio / cap, 0, 1));
        g.fill(x, y, x + fw, y + 6, 0xFFB01828);
        g.fill(x, y, x + fw, y + 2, 0xFFE04050);
        int pulse = (int) ((t * 1.5) % (w + 20)) - 10;
        if (pulse > 0 && pulse < fw) g.fill(x + pulse, y, Math.min(x + fw, x + pulse + 6), y + 6, 0x60FFD0D0);
        for (int k = 1; k < 4; k++) g.fill(x + w * k / 4, y, x + w * k / 4 + 1, y + 6, 0x80100306);   // quarters
        g.drawString(mc.font, "Biomass " + Math.round(biomass) + (cap != 100 ? "/" + cap : ""), x, y - 10, 0xFFE8A0A8, true);
        String hunger = starving ? "STARVING" : sated > 0 ? "Sated +" + sated + "%" : "";
        if (!hunger.isEmpty()) g.drawString(mc.font, hunger, x, y - 20, starving ? 0xFFFF5040 : 0xFF9AE08A, true);
        // frenzy: a thin bar under it, flashing when frenzied
        int fy = y + 9;
        g.fill(x - 1, fy - 1, x + w + 1, fy + 3, 0xE0100306);
        int frw = Math.round(w * Mth.clamp(shownFrenzy / 100F, 0, 1));
        boolean flash = frenzied && ((int) (t / 4)) % 2 == 0;
        g.fill(x, fy, x + frw, fy + 2, flash ? 0xFFFFFFFF : 0xFFFF6A00);
        String tag = finalLeft > 0 ? "FINAL FORM " + finalLeft + "s" : frenzied ? "FRENZIED"
                : form > 0 ? com.ironspellgrimoire.server.ParasiteFx.FORMS[Math.min(form, com.ironspellgrimoire.server.ParasiteFx.LAST_FORM)] : "";
        if (!tag.isEmpty()) g.drawString(mc.font, tag, x + w + 6, y - 1, frenzied ? 0xFFFF5040 : 0xFFD8C8C8, true);
        if (ep > 0) g.drawString(mc.font, ep + " EP", x + w + 6, fy - 2, 0xFFFFC060, true);
        if (limb > 0 || limbGone) {   // the guarding arm's wear (Regenerating Limbs)
            int lx = x + w + 6, ly = fy + 8;
            g.fill(lx - 1, ly - 1, lx + 41, ly + 3, 0xE0100306);
            g.fill(lx, ly, lx + (limbGone ? 0 : Math.round(40 * Math.min(1f, limb / 30f))), ly + 2, 0xFFD8D8DC);
            if (limbGone) g.drawString(mc.font, "regrowing", lx + 44, ly - 3, 0xFFB0A0A0, true);
        }
        if (artLeft > 0) g.drawString(mc.font, com.ironspellgrimoire.server.ParasiteFx.ARTS[Math.min(3, strain)] + " " + artLeft + "s", x, y - 30, 0xFFFFA060, true);
    }
}
