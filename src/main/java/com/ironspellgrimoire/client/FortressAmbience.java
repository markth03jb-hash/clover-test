package com.ironspellgrimoire.client;

import com.ironspellgrimoire.AddonClientConfig;
import com.ironspellgrimoire.registry.AddonSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The fortress's own sound while you stand inside your walls in Siege Mode: layers that join as the fortress grows -
 * wind and creaking timber (from the start), hammers and chisels (book level 4), the forge (6), a distant bell (8).
 * Each layer is a seamless loop (tools/sfx_architect2.py) that fades in and out; its loudness is the client config's
 * fortress_ambience (also on the Fortress menu's Spells tab), 0 = off.
 */
@Mod.EventBusSubscriber(modid = "iron_spell_grimoire", value = Dist.CLIENT)
public final class FortressAmbience {
    private FortressAmbience() {}

    static final String[] LAYER = {"fort_amb_wind", "fort_amb_works", "fort_amb_forge", "fort_amb_bell"};
    static final int[] FROM_LEVEL = {1, 4, 6, 8};
    static final float[] WEIGHT = {0.55F, 0.45F, 0.4F, 0.5F};
    private static final Loop[] playing = new Loop[LAYER.length];

    /** How many layers a fortress of this level has. */
    public static int layers(int level) { int n = 0; for (int l : FROM_LEVEL) if (level >= l) n++; return n; }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        boolean in = mc.player != null && mc.level != null && FortressClient.equipped() && FortressClient.insideWalls(mc.player);
        float vol = AddonClientConfig.fortressAmbience();
        int lv = FortressClient.level();
        for (int i = 0; i < LAYER.length; i++) {
            float target = in && vol > 0 && lv >= FROM_LEVEL[i] ? vol * WEIGHT[i] : 0F;
            Loop l = playing[i];
            if (target > 0 && (l == null || l.isStopped())) {
                l = new Loop(i);
                playing[i] = l;
                mc.getSoundManager().play(l);
            }
            if (l != null) l.target = target;
            if (l != null && l.isStopped()) playing[i] = null;
        }
    }

    /** One looping layer, gliding toward its target loudness (about two seconds from silence to full); stops at silence. */
    static final class Loop extends AbstractTickableSoundInstance {
        float target;

        Loop(int layer) {
            super(AddonSounds.get(LAYER[layer]), SoundSource.AMBIENT, RandomSource.create());
            this.looping = true;
            this.delay = 0;
            this.volume = 0.001F;
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
        }

        @Override public void tick() {
            float step = 0.012F;
            if (volume < target) volume = Math.min(target, volume + step);
            else if (volume > target) volume = Math.max(target, volume - step);
            if (target <= 0 && volume <= 0.002F) stop();
        }

        @Override public boolean canStartSilent() { return true; }
    }
}
