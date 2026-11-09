package com.ironspellgrimoire.server;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModFileInfo;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Finds the "impact" tick of a player animation on the server - the tick where the arms and body move fastest,
 * i.e. where the swing comes through - by reading the animation files straight out of the mod jars (the addon's
 * own and Iron's). The server tells clients this tick (so both sides agree) and uses it to land damage on the
 * swing (CastSync).
 */
public final class AnimImpact {
    private AnimImpact() {}

    private static Map<String, Integer> impacts;

    /** Impact tick of an animation id such as "iron_spell_grimoire:asta_zetten_fast", or 0 if unknown / not a swing. */
    public static synchronized int of(String id) {
        if (impacts == null) load();
        Integer v = impacts.get(id);
        return v == null ? 0 : v;
    }

    private static void load() {
        impacts = new HashMap<>();
        for (String ns : new String[]{"iron_spell_grimoire", "irons_spellbooks"}) {
            try {
                IModFileInfo info = ModList.get().getModFileById(ns);
                if (info == null) continue;
                Path dir = info.getFile().findResource("assets", ns, "player_animation");
                if (!Files.isDirectory(dir)) continue;
                try (Stream<Path> files = Files.list(dir)) {
                    for (Path f : (Iterable<Path>) files::iterator) {
                        if (!f.toString().endsWith(".json")) continue;
                        try (Reader r = Files.newBufferedReader(f)) {
                            JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
                            if (!root.has("animations")) continue;
                            for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("animations").entrySet()) {
                                if (e.getValue().isJsonObject()) impacts.put(ns + ":" + e.getKey(), compute(e.getValue().getAsJsonObject()));
                            }
                        } catch (Exception ignored) {}
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    private static final String[][] PARTS = {{"right_arm", "rightArm"}, {"left_arm", "leftArm"}, {"body"}, {"torso"}, {"rightItem", "right_item"}};
    private static final float[] WEIGHT = {1.0F, 0.8F, 1.5F, 1.2F, 0.6F};

    private static int compute(JsonObject anim) {
        if (!anim.has("bones")) return 0;
        JsonObject bones = anim.getAsJsonObject("bones");
        int end = 40;
        if (anim.has("animation_length")) end = (int) Math.min(60, Math.max(1, Math.round(anim.get("animation_length").getAsDouble() * 20)));
        float[] speed = new float[end + 1];
        for (int k = 0; k < PARTS.length; k++) {
            JsonObject bone = null;
            for (String n : PARTS[k]) if (bones.has(n) && bones.get(n).isJsonObject()) { bone = bones.getAsJsonObject(n); break; }
            if (bone == null || !bone.has("rotation") || !bone.get("rotation").isJsonObject()) continue;
            JsonObject rot = bone.getAsJsonObject("rotation");
            if (rot.has("vector")) continue;   // constant: never moves
            java.util.TreeMap<Double, double[]> keys = new java.util.TreeMap<>();
            for (Map.Entry<String, JsonElement> e : rot.entrySet()) {
                try {
                    double tick = Double.parseDouble(e.getKey()) * 20.0;
                    JsonElement v = e.getValue();
                    if (v.isJsonObject()) v = v.getAsJsonObject().has("vector") ? v.getAsJsonObject().get("vector") : v.getAsJsonObject().get("post");
                    if (v == null || !v.isJsonArray()) continue;
                    double[] xyz = new double[3];
                    for (int i = 0; i < 3 && i < v.getAsJsonArray().size(); i++) {
                        try { xyz[i] = Math.toRadians(v.getAsJsonArray().get(i).getAsDouble()); } catch (Exception ignored) {}
                    }
                    keys.put(tick, xyz);
                } catch (NumberFormatException ignored) {}
            }
            if (keys.size() < 2) continue;
            for (int t = 0; t < end; t++) {
                double[] a = at(keys, t), b = at(keys, t + 1);
                speed[t] += WEIGHT[k] * (float) (Math.abs(b[0] - a[0]) + Math.abs(b[1] - a[1]) + Math.abs(b[2] - a[2]));
            }
        }
        int best = 0;
        for (int t = 1; t < end; t++) if (speed[t] > speed[best]) best = t;
        return speed[best] < 0.06F ? 0 : Math.min(best + 1, 24);
    }

    private static double[] at(java.util.TreeMap<Double, double[]> keys, double t) {
        Map.Entry<Double, double[]> lo = keys.floorEntry(t), hi = keys.ceilingEntry(t);
        if (lo == null) return hi.getValue();
        if (hi == null || hi.getKey().equals(lo.getKey())) return lo.getValue();
        double f = (t - lo.getKey()) / (hi.getKey() - lo.getKey());
        double[] a = lo.getValue(), b = hi.getValue();
        return new double[]{a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f, a[2] + (b[2] - a[2]) * f};
    }
}
