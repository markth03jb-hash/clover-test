package com.ironspellgrimoire.client;

import com.ironspellgrimoire.network.AddonNetwork;
import com.ironspellgrimoire.network.FortressPackets;
import com.ironspellgrimoire.server.FortressFx;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** The Architect of Ruin's state on the client (server/FortressFx sends it whole): read by the menu and the tracker. */
@Mod.EventBusSubscriber(modid = "iron_spell_grimoire", value = Dist.CLIENT)
public final class FortressClient {
    private FortressClient() {}

    static CompoundTag tag = new CompoundTag();
    private static long since;   // client ticks since the last sync (the cooldowns count down here between syncs)

    public static void sync(CompoundTag t, boolean open) {
        tag = t;
        since = 0;
        Minecraft mc = Minecraft.getInstance();
        if (open && !(mc.screen instanceof FortressScreen)) mc.setScreen(new FortressScreen());
    }

    /** Test hook (UiShots): a made-up state, then the menu. */
    public static void force(CompoundTag t) { tag = t; since = 0; }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase == TickEvent.Phase.END && !Minecraft.getInstance().isPaused()) since++;
    }

    static boolean equipped() { return tag.getBoolean("eq"); }
    static int level() { return Math.max(1, tag.getInt("lv")); }
    static int stone() { return tag.getInt("stone"); }
    static float xp() { return tag.getFloat("xp"); }
    static int gridSize() { return FortressFx.gridSize(level()); }
    static int[] grid() { int[] g = tag.getIntArray("grid"); return g.length == 25 ? g : blank(); }
    private static int[] blank() { int[] g = new int[25]; java.util.Arrays.fill(g, -1); return g; }
    static int[] slots() { int[] s = tag.getIntArray("slots"); return s.length == 4 ? s : new int[]{-1, -1, -1, -1}; }
    static boolean unlocked(int room) { return FortressFx.unlocked(room, tag.getInt("bp"), stars()); }
    static int stars() { return tag.getInt("stars"); }
    static int shape() { return Math.max(0, Math.min(FortressFx.HORSESHOE, tag.getInt("shape"))); }
    static boolean prestigeOk() { return tag.getBoolean("prestigeOk"); }
    /** The garrison's order (FortressSiege.FREE..FALL_BACK) and how many defenders are out. */
    static int order() { return tag.getInt("order"); }
    static int defendersOut() { return tag.getInt("defOut"); }
    /** Ticks left on the breach alarm (0: none) and which side it's on. */
    static int breachLeft() { return (int) Math.max(0, tag.getLong("breachUntil") - (tag.getLong("now") + since)); }
    static String breachSide() { return tag.getString("breachSide"); }
    /** Is the player inside their own walls while a siege is up? (the ambience) */
    static boolean insideWalls(net.minecraft.world.entity.player.Player p) {
        if (tag.getLong("siegeUntil") <= tag.getLong("now") + since || !tag.contains("fcx")) return false;
        double dx = p.getX() - tag.getDouble("fcx"), dz = p.getZ() - tag.getDouble("fcz"), dy = p.getY() - tag.getDouble("fcy");
        int hw = tag.getInt("fhw");
        if (Math.abs(dy) > 12) return false;
        if (tag.getInt("fshape") == FortressFx.LINE) return dx * dx + dz * dz < 9 * 9;
        return Math.max(Math.abs(dx), Math.abs(dz)) < hw + 0.5;
    }
    static int roomLevel(int room) { for (int c : grid()) if (c >= 0 && c / 10 == room) return c % 10; return 0; }
    static int cellOf(int room) { int[] g = grid(); for (int i = 0; i < 25; i++) if (g[i] >= 0 && g[i] / 10 == room) return i; return -1; }
    static int builtCount() { int n = 0; for (int c : grid()) if (c >= 0) n++; return n; }
    static boolean paired(int a, int b) {
        int ca = cellOf(a), cb = cellOf(b);
        return ca >= 0 && cb >= 0 && Math.abs(ca % 5 - cb % 5) + Math.abs(ca / 5 - cb / 5) == 1;
    }
    static boolean toured() { return tag.getBoolean("toured"); }
    static ListTag garrison() { return tag.getList("garrison", Tag.TAG_COMPOUND); }
    static ListTag banners() { return tag.getList("banners", Tag.TAG_STRING); }
    static int cap() { return Math.max(4, tag.getInt("cap")); }
    static boolean citadelOk() { return tag.getBoolean("citadelOk"); }
    static boolean siegeUp() { return tag.getBoolean("siegeUp"); }
    /** Ticks left on a cooldown: room 0-23, -1 Rampart, -2 Siege, -3 Citadel. */
    static int cd(int key) {
        long[] c = tag.getLongArray("cds");
        int i = key >= 0 ? key : FortressFx.ROOMS - 1 - key;
        if (c.length != FortressFx.ROOMS + 3) return 0;
        return (int) Math.max(0, c[i] - (tag.getLong("now") + since));
    }

    public static void send(int action, int arg) { AddonNetwork.CHANNEL.sendToServer(new FortressPackets.Act(action, arg)); }

    /** The room's key for its icon: "Hall of Banners" -> hall_of_banners. */
    static String key(int room) { return FortressFx.ROOM[room].name().toLowerCase().replace("'", "").replace(' ', '_'); }
}
