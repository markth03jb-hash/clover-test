package com.ironspellgrimoire.server;

import static com.ironspellgrimoire.server.FxUtil.*;
import static com.ironspellgrimoire.server.TwinsFx.*;
import static com.ironspellgrimoire.server.TwinsArts.*;
import com.ironspellgrimoire.entity.AddonBoss;
import com.ironspellgrimoire.entity.TwinWarden;
import com.ironspellgrimoire.network.AddonNetwork;
import com.ironspellgrimoire.network.FxPacket;
import com.ironspellgrimoire.network.TwinsPackets;
import com.ironspellgrimoire.registry.AddonItemRegistry;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Split out of TwinsFx (same code, own file): the trial: the Dawn Warden and the Dusk Warden. */
@Mod.EventBusSubscriber(modid = "iron_spell_grimoire")
public final class TwinsTrial {
    private TwinsTrial() {}

    // ------------------------------------------------------------------ the trial: the Dawn Warden and the Dusk Warden
    static void startTrial(ServerPlayer p) {
        if (!bonded(p, BOND_ECLIPSE)) { tell(p, "Both twins must reach level " + BOND_ECLIPSE); return; }
        if (trialRunning(p)) { tell(p, "The trial has already begun"); return; }
        ServerLevel sl = p.serverLevel();
        CompoundTag r = root(p);
        r.putLong("trialEnd", now(p) + TRIAL);
        r.putInt("downs", 0);
        Vec3 d = flat(p.getLookAngle()), side = rightOf(d);
        for (int k = 0; k < 2; k++) {
            TwinWarden w = new TwinWarden(sl, p, k == 1);
            Vec3 at = p.position().add(d.scale(6)).add(side.scale(k == 0 ? -3 : 3)).add(0, 1.5, 0);
            w.moveTo(at.x, at.y, at.z, p.getYRot() + 180, 0);
            sl.addFreshEntity(w);
            Fx.pillar(sl, k == 0 ? PAL_SOL : PAL_NYX, at.add(0, -1.5, 0), 4f, 1.4f, 16);
        }
        OverdriveFx.play(sl, p.position(), "eclipse_toll", 1.2f, 0.8f);
        say(p, NYX, "They are what we were before we were us. Beat them both.");
        p.closeContainer();
        sync(p, false);
    }

    /** A warden falls; both down, the trial is won. */
    public static void wardenDown(TwinWarden w, ServerPlayer p) {
        CompoundTag r = root(p);
        if (!trialRunning(p)) return;
        r.putInt("downs", r.getInt("downs") + 1);
        ServerLevel sl = p.serverLevel();
        Fx.flash(sl, w.position().add(0, 1.2, 0), 1.6f);
        Fx.pillar(sl, w.dusk() ? PAL_NYX : PAL_SOL, w.position(), 5f, 1.4f, 20);
        if (r.getInt("downs") < 2) { say(p, w.dusk() ? NYX : SOL, "One more."); return; }
        r.putLong("trialEnd", 0);
        r.putBoolean("trial", true);
        Adv.grant(p, "two_wardens");
        award(p, 3);
        say(p, SOL, "The eclipse is ours.");
        p.sendSystemMessage(Component.literal("Eclipse Union is open: the Eclipse Union page.").withStyle(ChatFormatting.GOLD));
        OverdriveFx.play(sl, p.position(), "eclipse_toll", 1.4f, 1.1f);
        sync(p, false);
    }

    static void endTrial(ServerPlayer p, String why) {
        root(p).putLong("trialEnd", 0);
        for (TwinWarden w : p.serverLevel().getEntitiesOfClass(TwinWarden.class, p.getBoundingBox().inflate(96), x -> p.getUUID().equals(x.owner()))) w.discard();
        tell(p, why);
        sync(p, false);
    }

    /** A warden's turn: the Dawn Warden's fire, the Dusk Warden's frost and blinks. */
    public static void wardenAttack(TwinWarden w, ServerPlayer p, int turn) {
        ServerLevel sl = (ServerLevel) w.level();
        Vec3 from = w.position().add(0, 1.6, 0), to = p.getEyePosition().add(0, -0.3, 0);
        boolean low = w.getHealth() < w.getMaxHealth() * 0.5f;
        var src = w.damageSources().mobAttack(w);
        if (!w.dusk()) {
            if (low && turn % 3 == 2) {   // Dawnbreaker
                Vec3 c = w.position();
                FxSend.ring(sl, PAL_SOL, c.add(0, 0.1, 0), 0.5f, 7f, 0.8f, 14);
                Fx.pillar(sl, PAL_SOL, c, 4f, 1.6f, 12);
                if (p.distanceTo(w) < 7.5) p.hurt(src, 9f);
                OverdriveFx.play(sl, c, "sun_flare", 1.2f, 0.7f);
            } else if (p.distanceTo(w) < 4.5) {   // a flare all round
                FxSend.ring(sl, PAL_SOL, w.position().add(0, 0.5, 0), 0.4f, 4.5f, 0.4f, 12);
                p.hurt(src, 7f);
                OverdriveFx.play(sl, w.position(), "sun_flare", 1f, 1f);
            } else {   // a bolt of fire
                FxSend.beam(sl, PAL_SOL2, from, to, 0.2f, 6);
                if (w.hasLineOfSight(p)) p.hurt(src, 5f);
                OverdriveFx.play(sl, from, "sun_flare", 0.6f, 1.6f);
            }
        } else {
            if (turn % 3 == 1) {   // a blink to the far side
                Vec3 behind = p.position().add(p.position().subtract(w.position()).multiply(1, 0, 1).normalize().scale(5)).add(0, 1.5, 0);
                Fx.flash(sl, w.position().add(0, 1.2, 0), 0.8f);
                w.teleportTo(behind.x, behind.y, behind.z);
                OverdriveFx.play(sl, behind, "moon_shimmer", 0.8f, 1.3f);
            } else if (low && turn % 3 == 2) {   // a few stars on the player
                Vec3 at = p.position();
                for (int k = 0; k < 3; k++) {
                    final int kk = k;
                    AstaFx.later(k * 6, () -> {
                        if (!p.isAlive() || !w.isAlive()) return;
                        FxSend.spike(sl, PAL_NYX, at.add(kk - 1, 8, 0), new Vec3(0, -1, 0), 1.2f, 0.18f, 6, 0, FxPacket.HOLD, 0, new Vec3(0, -1.6, 0), 5);
                        AstaFx.later(5, () -> { if (p.isAlive() && p.position().distanceTo(at) < 2.2) p.hurt(src, 4f); });
                    });
                }
            } else {   // a frost needle
                FxSend.beam(sl, PAL_NYX2, from, to, 0.12f, 5);
                if (w.hasLineOfSight(p)) { p.hurt(src, 5f); slow(p, -0.25, 40); }
                OverdriveFx.play(sl, from, "moon_shimmer", 0.6f, 1.8f);
            }
        }
    }

}
