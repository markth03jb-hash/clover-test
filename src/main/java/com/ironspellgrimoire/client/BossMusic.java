package com.ironspellgrimoire.client;

import com.ironspellgrimoire.entity.Zenon;
import com.ironspellgrimoire.registry.AddonSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The bosses' music (Zenon, Vetto): while a living boss is within 48 blocks, his theme for the phase he is in plays (looping,
 * on the Music volume) and the game's own music is held off. A phase change cross-fades into the next track; when he
 * dies or you leave, it fades out.
 */
@Mod.EventBusSubscriber(modid = "iron_spell_grimoire", value = Dist.CLIENT)
public final class BossMusic {
    private BossMusic() {}

    private static Track playing;
    private static int playingPhase;

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) { stop(); return; }
        net.minecraft.world.entity.LivingEntity boss = null;
        int bossPhase = 1;
        java.util.List<net.minecraftforge.registries.RegistryObject<SoundEvent>> tracks = AddonSounds.BOSS_MUSIC;
        double best = 48 * 48;
        for (net.minecraft.world.entity.LivingEntity o : mc.level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, mc.player.getBoundingBox().inflate(48),
                x -> x instanceof Zenon || x instanceof com.ironspellgrimoire.entity.Vetto)) {
            if (!o.isAlive()) continue;
            double d = o.distanceToSqr(mc.player);
            if (d < best) {
                best = d; boss = o;
                if (o instanceof Zenon os) { bossPhase = os.phase(); tracks = AddonSounds.BOSS_MUSIC; }
                else if (o instanceof com.ironspellgrimoire.entity.Vetto v) { bossPhase = v.phase(); tracks = AddonSounds.VETTO_MUSIC; }
            }
        }
        if (boss == null) { stop(); return; }
        mc.getMusicManager().stopPlaying();   // hold the game's own music off
        int phase = Math.max(1, Math.min(3, bossPhase)) + (tracks == AddonSounds.VETTO_MUSIC ? 10 : 0);
        if (playing == null || playingPhase != phase || playing.isStopped()) {
            if (playing != null) playing.fadeOut();
            SoundEvent ev = tracks.get(phase % 10 - 1).get();
            playing = new Track(ev);
            playingPhase = phase;
            mc.getSoundManager().play(playing);
        }
    }

    private static void stop() {
        if (playing != null) { playing.fadeOut(); playing = null; }
    }

    /** One looping track that fades in, and fades out (then stops) when asked. */
    static final class Track extends AbstractTickableSoundInstance {
        private float target = 0.9F;
        private boolean leaving;

        Track(SoundEvent ev) {
            super(ev, SoundSource.MUSIC, RandomSource.create());
            this.looping = true;
            this.delay = 0;
            this.volume = 0.01F;
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
        }

        void fadeOut() { leaving = true; }

        @Override public void tick() {
            if (leaving) {
                this.volume -= 0.03F;
                if (this.volume <= 0.01F) this.stop();
            } else if (this.volume < target) this.volume = Math.min(target, this.volume + 0.03F);
        }

        @Override public boolean canStartSilent() { return true; }
    }
}
