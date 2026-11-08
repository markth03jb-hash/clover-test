package com.example.arcaneaddon.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * Mereoleona's Mana Zone: the mana in the air holds her up. While her zone is up, pressing Jump in the air steps off
 * the air itself - a burst of fire under your feet and a jump toward where you're moving - twice before you land.
 */
@EventBusSubscriber(modid = "arcane_addon", value = {Dist.CLIENT})
public final class ZoneStep {
    private ZoneStep() {}

    private static boolean wasJump;
    private static int steps;

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || mc.isPaused()) return;
        boolean jump = mc.options.keyJump.isDown();
        if (p.onGround() || p.isInWater()) steps = 0;
        boolean zone = FxRenderer.zonePalette(p.getId(), mc.level.getGameTime()) == 2;
        if (zone && jump && !wasJump && !p.onGround() && !p.isInWater() && !p.getAbilities().flying && steps < 2) {
            steps++;
            Vec3 look = p.getLookAngle();
            Vec3 flat = new Vec3(look.x, 0, look.z);
            flat = flat.lengthSqr() < 1.0E-4 ? Vec3.ZERO : flat.normalize();
            float fwd = p.input.forwardImpulse, side = p.input.leftImpulse;
            Vec3 left = new Vec3(flat.z, 0, -flat.x);
            Vec3 push = flat.scale(0.55 * fwd).add(left.scale(0.45 * side));
            p.setDeltaMovement(p.getDeltaMovement().x * 0.4 + push.x, 0.62, p.getDeltaMovement().z * 0.4 + push.z);
            p.fallDistance = 0;
            // fire under your feet where you stepped
            for (int k = 0; k < 10; k++) {
                double a = Math.PI * 2 * k / 10;
                DarkSmoke.flame(p.position().add(Math.cos(a) * 0.4, 0, Math.sin(a) * 0.4), new Vec3(Math.cos(a) * 0.06, -0.02, Math.sin(a) * 0.06), 0.25F, 10);
            }
            mc.level.playLocalSound(p.getX(), p.getY(), p.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.5F, 1.6F, false);
        }
        wasJump = jump;
    }
}
