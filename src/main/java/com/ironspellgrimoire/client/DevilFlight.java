package com.ironspellgrimoire.client;

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
 * Devil Union wings - controlled flight, no elytra needed:
 *  - In the air, press JUMP to spread the wings. You hover in place (slowly sinking) - nothing happens on its own.
 *  - W flies forward where you look (look down to dive, up to climb), S brakes / backs up, A and D drift sideways.
 *    Speed builds up while you hold W and eases off when you let go.
 *  - Hold JUMP to rise, hold SNEAK to sink faster. Land to fold the wings. No fall damage in the union.
 * Runs on your own client (movement is client-side), so it responds instantly.
 */
@EventBusSubscriber(modid = "iron_spell_grimoire", value = {Dist.CLIENT})
public final class DevilFlight {
    private DevilFlight() {}

    private static final double MAX_SPEED = 1.3, DIVE_BONUS = 0.5;
    private static boolean flying, wasJump;
    private static int flyTicks, beat;
    private static Vec3 vel = Vec3.ZERO;

    public static boolean flying() { return flying; }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || mc.isPaused()) return;
        boolean jump = mc.options.keyJump.isDown();
        boolean union = (FxRenderer.inDevilForm(p.getId(), mc.level.getGameTime()) || FxRenderer.inSpiritDive(p.getId(), mc.level.getGameTime())) && !Cutscene.running();
        boolean airborne = !p.onGround() && !p.isInWater() && !p.isInLava() && !p.isPassenger() && !p.getAbilities().flying && !p.isFallFlying();
        if (!union || !airborne) {
            flying = false;
            wasJump = jump;
            return;
        }
        if (!flying) {
            if (!(jump && !wasJump)) { wasJump = jump; return; }   // a fresh jump press in the air spreads the wings
            flying = true;
            flyTicks = 0;
            vel = new Vec3(p.getDeltaMovement().x * 0.5, 0.15, p.getDeltaMovement().z * 0.5);   // catch yourself, small lift
            mc.level.playLocalSound(p.getX(), p.getY(), p.getZ(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 0.8F, 1.1F, false);
        }
        flyTicks++;
        float fwd = p.input.forwardImpulse, side = p.input.leftImpulse;
        Vec3 look = p.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : flat.normalize();
        Vec3 left = new Vec3(flat.z, 0, -flat.x);
        // where the controls want you to go
        double speed = MAX_SPEED + Math.max(0, -look.y) * DIVE_BONUS;
        Vec3 target = Vec3.ZERO;
        if (fwd > 0) target = look.scale(speed * fwd);                 // forward: along your view, dives and climbs too
        else if (fwd < 0) target = flat.scale(0.35 * fwd);             // back: brake and drift backward
        target = target.add(left.scale(0.45 * side));
        double vy = target.y;
        if (jump) vy = Math.max(vy, 0.0) + 0.38;                      // rise
        else if (p.isShiftKeyDown()) vy = Math.min(vy, 0.0) - 0.5;    // sink
        else if (fwd <= 0) vy = -0.05;                                 // hovering: a slow sink
        target = new Vec3(target.x, vy, target.z);
        // ease toward it: smooth starts, stops and turns
        double ease = fwd > 0 ? 0.14 : 0.2;
        vel = vel.add(target.subtract(vel).scale(ease));
        if (p.horizontalCollision) vel = new Vec3(vel.x * 0.3, vel.y, vel.z * 0.3);
        Vec3 nv = vel;
        // a moment of sinking every 3 s, so servers that don't allow flight never mistake this for flying
        if (flyTicks % 60 == 0) nv = new Vec3(nv.x, Math.min(nv.y, -0.06), nv.z);
        p.setDeltaMovement(nv);
        p.fallDistance = 0.0F;
        if ((jump || fwd > 0) && ++beat % 14 == 0) {
            mc.level.playLocalSound(p.getX(), p.getY(), p.getZ(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 0.45F, 1.2F, false);
        }
        wasJump = jump;
    }
}
