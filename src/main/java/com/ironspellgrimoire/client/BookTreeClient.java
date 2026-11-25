package com.ironspellgrimoire.client;

import com.ironspellgrimoire.network.AddonNetwork;
import com.ironspellgrimoire.network.BookTreePackets;
import com.ironspellgrimoire.server.BookTree;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** The Vector, Quiet Fuse and Foreseen trees as the server last sent them (server/BookTree), and each book's colours. */
@Mod.EventBusSubscriber(modid = "iron_spell_grimoire", value = Dist.CLIENT)
public final class BookTreeClient {
    private BookTreeClient() {}

    public static int book = -1, points, respec;
    public static float xp;
    public static int[] cd = new int[4], extra = new int[0];
    public static final Map<String, Integer> RANKS = new HashMap<>();

    /** Each book's colours: background, panel, line, accent, bright, dim text. */
    public static final int[][] THEME = {
            {0xFF0B1118, 0xEE111B24, 0xFF2E4656, 0xFF6FDFFF, 0xFFE6F8FF, 0xFF7E98A8},   // Vector: white and cyan on slate
            {0xFF180A13, 0xEE22101B, 0xFF5C3248, 0xFFFF7FB4, 0xFFFFE6C8, 0xFFA88496},   // Quiet Fuse: rose on charcoal plum
            {0xFF060A1C, 0xEE0C1230, 0xFF2C3A6C, 0xFF9DB9FF, 0xFFEAF0FF, 0xFF8090C0}};  // Foreseen: ice-white on deep navy

    public static void sync(BookTreePackets.Sync m) {
        book = m.book; xp = m.xp; points = m.points; cd = m.cd; extra = m.extra; respec = m.respec;
        RANKS.clear();
        for (String s : m.ranks.split(",")) {
            int c = s.indexOf(':');
            if (c > 0) RANKS.put(s.substring(0, c), Integer.parseInt(s.substring(c + 1)));
        }
        Minecraft mc = Minecraft.getInstance();
        if (m.open && book >= 0 && !(mc.screen instanceof BookTreeScreen)) mc.setScreen(new BookTreeScreen());
    }

    public static int rank(String id) { return "root".equals(id) ? 1 : RANKS.getOrDefault(id, 0); }
    public static int level() { return BookTree.level(xp); }
    public static int spent() {
        if (book < 0) return 0;
        int s = 0;
        for (BookTree.Node n : BookTree.TREES.get(book)) if (!"root".equals(n.id())) s += rank(n.id()) * n.cost();
        return s;
    }
    public static int free() { return points - spent(); }

    /** Why a node can't take a rank now (the client's copy of BookTree.blocked), or null. */
    public static String blocked(BookTree.Node x) {
        int r = rank(x.id());
        if (x.kind() == BookTree.ROOT) return "Always yours";
        if (r >= x.ranks()) return x.ranks() > 1 ? "All ranks learned" : "Learned";
        boolean ok = x.req().length == 0;
        for (String q : x.req()) if (rank(q) > 0) { ok = true; break; }
        if (!ok) {
            StringBuilder s = new StringBuilder("Needs ");
            for (int i = 0; i < x.req().length; i++) { BookTree.Node q = BookTree.node(book, x.req()[i]); s.append(i > 0 ? " or " : "").append(q == null ? x.req()[i] : q.name()); }
            return s.toString();
        }
        if (x.excl() != null && rank(x.excl()) > 0) return "You took " + BookTree.node(book, x.excl()).name();
        if (free() < x.cost()) return "Needs " + x.cost() + (x.cost() == 1 ? " point" : " points");
        return null;
    }

    public static boolean wheelOpen(int slot) { return book >= 0 && (slot == 0 || rank(BookTree.WHEEL_IDS[book][slot]) > 0); }

    public static void send(int action, int arg, String id) { AddonNetwork.CHANNEL.sendToServer(new BookTreePackets.Act(action, arg, id == null ? "" : id)); }

    @SubscribeEvent public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) { book = -1; RANKS.clear(); }
}
