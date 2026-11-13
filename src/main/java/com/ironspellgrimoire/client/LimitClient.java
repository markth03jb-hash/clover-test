package com.ironspellgrimoire.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Surpassing Limits aura (FxPacket shape 35): a white-hot shell in the grimoire's colour round the body
 * (drawn by AuraOutlineLayer) and that grimoire's own magic rising off you - kept light so you can still see.
 * Book index: 0 Asta, 1 Yami, 2 Yuno, 3 Nozel, 4 Magna, 5 Luck, 6 Mereoleona, 7 Zenon, 8 Klaus.
 */
@EventBusSubscriber(modid = "iron_spell_grimoire", value = {Dist.CLIENT})
public final class LimitClient {
    private LimitClient() {}

    /** player id -> {start, end, book index} */
    static final Map<Integer, double[]> ACTIVE = new HashMap<>();

    static final float[][] COLORS = {
            {1.0F, 0.12F, 0.06F},   // Asta: devil red
            {0.6F, 0.25F, 1.0F},    // Yami: violet
            {0.55F, 1.0F, 0.75F},   // Yuno: mint wind
            {0.88F, 0.92F, 1.0F},   // Nozel: silver
            {1.0F, 0.55F, 0.15F},   // Magna: orange flame
            {0.45F, 0.75F, 1.0F},   // Luck: blue lightning
            {1.0F, 0.18F, 0.1F},    // Mereoleona: crimson
            {0.95F, 0.9F, 0.8F},    // Zenon: ivory
            {0.78F, 0.84F, 0.92F},  // Klaus: steel
            {1.0F, 0.55F, 0.78F},   // Vanessa: thread pink
            {0.62F, 0.45F, 1.0F},   // Finral: space violet
            {0.3F, 0.7F, 1.0F},     // Noelle: water
            {0.9F, 0.9F, 1.0F},     // Gauche: mirror
            {0.9F, 0.25F, 0.85F},   // Langris: rift
            {1.0F, 0.38F, 0.1F},    // Fuegoleon: vermilion
            {1.0F, 0.72F, 0.18F},   // Leopold: gold-orange
            {0.36F, 1.0F, 0.48F},   // Jack: green
            {0.86F, 0.64F, 0.36F},  // Sol: ochre
            {0.3F, 0.86F, 1.0F},    // Julius: cyan
            {1.0F, 0.9F, 0.5F},     // Patolli: light
            {1.0F, 0.12F, 0.18F},   // Blood
            {0.62F, 0.38F, 1.0F},   // Gravity: violet
            {0.4F, 1.0F, 0.82F},    // Sound: sea green
    };

    public static void set(int id, int book, int life) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        double now = mc.level.getGameTime();
        if (life <= 0) { ACTIVE.remove(id); return; }
        double[] f = ACTIVE.get(id);
        if (f == null || now > f[1]) ACTIVE.put(id, new double[]{now, now + life, book});
        else { f[1] = now + life; f[2] = book; }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { ACTIVE.clear(); return; }
        double now = mc.level.getGameTime();
        ACTIVE.values().removeIf(f -> now > f[1] + 10);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (Map.Entry<Integer, double[]> en : ACTIVE.entrySet()) {
            Entity ent = mc.level.getEntity(en.getKey());
            double[] f = en.getValue();
            if (ent == null || now > f[1]) continue;
            boolean self = ent == mc.player && mc.options.getCameraType().isFirstPerson();
            if (self && r.nextInt(3) != 0) continue;   // much lighter in your own view
            int book = (int) f[2];
            double a = r.nextDouble(Math.PI * 2), y = r.nextDouble(0.1, ent.getBbHeight());
            Vec3 at = ent.position().add(Math.cos(a) * 0.45, y, Math.sin(a) * 0.45);
            Vec3 upv = new Vec3(Math.cos(a) * 0.02, r.nextDouble(0.04, 0.09), Math.sin(a) * 0.02);
            switch (book) {
                case 0 -> { DarkSmoke.smoke(at, upv, 0.4F, r.nextInt(10, 16)); if (r.nextInt(2) == 0) DarkSmoke.emberSpark(at, upv.scale(2), 0.05F, r.nextInt(6, 10)); }
                case 1 -> { DarkSmoke.smoke(at, upv.add(new Vec3(-Math.sin(a), 0, Math.cos(a)).scale(0.03)), 0.42F, r.nextInt(12, 18)); if (r.nextInt(3) == 0) DarkSmoke.mote(at, upv, 0.05F, 14); }
                case 2 -> DarkSmoke.streak(at, new Vec3(-Math.sin(a), 0.12, Math.cos(a)).scale(0.25), 0.05F, r.nextInt(6, 10));
                case 3 -> DarkSmoke.bead(at, upv, 0.06F, r.nextInt(10, 16));
                case 4 -> DarkSmoke.flame(at, upv, 0.18F, r.nextInt(8, 12));
                case 5 -> DarkSmoke.blueSpark(at, new Vec3(r.nextDouble(-0.08, 0.08), r.nextDouble(-0.02, 0.1), r.nextDouble(-0.08, 0.08)), 0.05F, r.nextInt(3, 6));
                case 6 -> DarkSmoke.crimsonFlame(at, upv, 0.2F, r.nextInt(8, 12));
                case 7 -> DarkSmoke.dust(at, upv.scale(0.6), 0.25F, r.nextInt(12, 18));
                default -> DarkSmoke.spark(at, upv.scale(1.5), 0.05F, r.nextInt(5, 8));
            }
        }
    }
}
