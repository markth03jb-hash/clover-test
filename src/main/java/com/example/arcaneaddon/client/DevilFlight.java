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
 * Devil Union wings: elytra-style flight without an elytra.
 *  - In the air, HOLD JUMP to spread the wings and glide where you look. Diving builds speed, pulling up bleeds it.
 *  - Each new PRESS of jump while gliding is a wing beat: a boost up and forward (every half second), so you can keep
 *    your height for as long as you like.
 *  - Let go of jump to drop; there is no fall damage while the union lasts (server side).
 * Runs on your own client (movement is client-side), so it is smooth. Between wing beats you always sink a little,
 * which keeps the server's anti-flight check happy.
 */
@EventBusSubscriber(modid = "arcane_addon", value = {Dist.CLIENT})
public final class DevilFlight {
    private DevilFlight() {}

    private static boolean gliding, wasJump;
    private static int flapCooldown, rising;

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || mc.isPaused()) return;
        if (flapCooldown > 0) flapCooldown--;
        if (rising > 0) rising--;
        boolean jump = mc.options.keyJump.isDown();
        boolean canFly = FxRenderer.inDevilForm(p.getId(), mc.level.getGameTime()) && !Cutscene.running()
                && !p.onGround() && !p.isInWater() && !p.isInLava() && !p.isPassenger() && !p.getAbilities().flying && !p.isFallFlying();
        if (!canFly || !jump) {
            gliding = false;
            wasJump = jump;
            return;
        }
        Vec3 look = p.getLookAngle();
        Vec3 v = p.getDeltaMovement();
        double speed = v.length();
        if (!gliding) {                                   // spreading the wings gives a small push to get going
            speed = Math.max(speed, 0.85);
            mc.level.playLocalSound(p.getX(), p.getY(), p.getZ(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 0.6F, 1.3F, false);
        }
        gliding = true;
        speed += -look.y * 0.06 - 0.004;                  // diving gains speed, climbing loses it
        speed = Mth.clamp(speed, 0.55, 2.2);
        Vec3 dir = v.lengthSqr() > 1.0E-4 ? v.normalize().scale(0.82).add(look.scale(0.18)).normalize() : look;
        Vec3 nv = dir.scale(speed);
        if (jump && !wasJump && flapCooldown <= 0) {      // a wing beat
            nv = nv.add(look.scale(0.35)).add(0.0, 0.7, 0.0);
            flapCooldown = 10;
            rising = 5;
            mc.level.playLocalSound(p.getX(), p.getY(), p.getZ(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 0.9F, 0.9F, false);
        }
        if (rising <= 0) nv = new Vec3(nv.x, Math.min(nv.y, -0.05), nv.z);   // between beats you always sink a little
        p.setDeltaMovement(nv);
        p.fallDistance = 0.0F;
        wasJump = jump;
    }
}
