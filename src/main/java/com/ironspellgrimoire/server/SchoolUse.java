package com.ironspellgrimoire.server;

import com.ironspellgrimoire.IronSpellGrimoire;
import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Which school of magic a player casts most (persistent data "isg_school_use"): the rank 4-5 elites that learn your
 * magic resist it (EliteMobs.adapt). Each cast counts one; every 50 casts the counts halve, so it follows how you play now.
 */
@Mod.EventBusSubscriber(modid = IronSpellGrimoire.MODID)
public final class SchoolUse {
    private SchoolUse() {}
    static final String TAG = "isg_school_use";

    @SubscribeEvent
    public static void onCast(SpellOnCastEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || e.getSchoolType() == null) return;
        count(p, e.getSchoolType().getId().getPath());
    }

    static void count(ServerPlayer p, String school) {
        CompoundTag t = p.getPersistentData().getCompound(TAG);
        t.putInt(school, t.getInt(school) + 2);
        int total = t.getInt("#") + 1;
        if (total >= 50) { total = 0; for (String k : t.getAllKeys()) if (!k.equals("#")) t.putInt(k, t.getInt(k) / 2); }
        t.putInt("#", total);
        p.getPersistentData().put(TAG, t);
    }

    /** The school this player casts most, or null if they hardly cast at all. */
    public static String top(ServerPlayer p) {
        CompoundTag t = p.getPersistentData().getCompound(TAG);
        String best = null;
        int bn = 3;
        for (String k : t.getAllKeys()) if (!k.equals("#") && t.getInt(k) > bn) { bn = t.getInt(k); best = k; }
        return best;
    }

    public static void testCount(ServerPlayer p, String school, int n) { for (int i = 0; i < n; i++) count(p, school); }
    public static void testReset(ServerPlayer p) { p.getPersistentData().remove(TAG); }
}
