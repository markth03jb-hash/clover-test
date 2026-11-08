package com.example.arcaneaddon.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * Devil Union wings - smooth flight, no elytra needed:
 *  - In the air, PRESS JUMP once to spread the wings (with a push to get going). You keep flying - no need to hold it.
 *  - Steer by looking. Your speed eases toward where you look, so turns are smooth arcs instead of snaps.
 *    Diving builds speed, climbing trades it away, level flight holds it.
 *  - HOLD JUMP to beat your wings: steady thrust forward and up (climb as long as you like).
 *  - SNEAK to fold the wings and drop. Landing or touching water also folds them. No fall damage in the union.
 * Runs on your own client (movement is client-side), so it is smooth even on a server.
 */
@EventBusSubscriber(modid = "arcane_addon", value = {Dist.CLIENT})
public final class DevilFlight {
    private DevilFlight() {}

    private static boolean flying, wasJump;
    private static int flyTicks, beatSound;
    /** Our own flight state - the game's air drag and gravity would otherwise eat the speed every tick. */
    private static double flySpeed;
    private static Vec3 flyDir = new Vec3(0, 0, 1);

    public static boolean flying() { return flying; }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || mc.isPaused()) return;
        boolean jump = mc.options.keyJump.isDown();
        boolean union = FxRenderer.inDevilForm(p.getId(), mc.level.getGameTime()) && !Cutscene.running();
        boolean airborne = !p.onGround() && !p.isInWater() && !p.isInLava() && !p.isPassenger() && !p.getAbilities().flying && !p.isFallFlying();
        if (!union || !airborne || p.isShiftKeyDown()) {
            if (flying && p.isShiftKeyDown()) mc.level.playLocalSound(p.getX(), p.getY(), p.getZ(), SoundEvents.ARMOR_EQUIP_ELYTRA, SoundSource.PLAYERS, 0.6F, 0.7F, false);
            flying = false;
            wasJump = jump;
            return;
        }
        Vec3 look = p.getLookAngle();
        Vec3 v = p.getDeltaMovement();
        if (!flying) {
            if (!(jump && !wasJump)) { wasJump = jump; return; }   // a fresh jump press in the air takes off
            flying = true;
            flyTicks = 0;
            Vec3 push = v.add(look.scale(0.6)).add(0.0, 0.45, 0.0);
            flySpeed = Math.max(0.8, push.length());
            flyDir = push.normalize();
            mc.level.playLocalSound(p.getX(), p.getY(), p.getZ(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 0.9F, 1.1F, false);
        }
        flyTicks++;
        if (p.horizontalCollision) flySpeed *= 0.5;      // bumping into a wall slows you right down
        double speed = flySpeed;
        speed += -look.y * 0.045;                        // dive to gain speed, climb to spend it
        if (jump) speed += 0.035;                        // wing beats push you along
        speed -= 0.006;                                  // a little drag
        speed = Mth.clamp(speed, 0.45, 2.0);
        flySpeed = speed;
        // ease the flight direction toward where you look: smooth turns
        Vec3 dir = flyDir.add(look.subtract(flyDir).scale(0.12)).normalize();
        flyDir = dir;
        Vec3 nv = dir.scale(speed);
        if (jump) nv = nv.add(0.0, 0.05, 0.0);           // beating wings give lift
        else nv = nv.add(0.0, -0.035, 0.0);              // gliding sinks gently
        // a moment of sinking every 3 s, so servers that don't allow flight never mistake this for flying
        if (flyTicks % 60 == 0) nv = new Vec3(nv.x, Math.min(nv.y, -0.06), nv.z);
        p.setDeltaMovement(nv);
        p.fallDistance = 0.0F;
        if (jump && ++beatSound % 12 == 0) {
            mc.level.playLocalSound(p.getX(), p.getY(), p.getZ(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 0.5F, 1.2F, false);
        }
        wasJump = jump;
    }
}
