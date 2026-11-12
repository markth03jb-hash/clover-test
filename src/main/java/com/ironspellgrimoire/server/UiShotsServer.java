package com.ironspellgrimoire.server;

import com.ironspellgrimoire.registry.AddonItemRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * The server half of the automatic screenshot run (client/UiShots): sets the test player up with a grimoire and some
 * progress, and opens each of the mod's menus for real, so the screens get real data. Only ever called by that run.
 */
public final class UiShotsServer {
    private UiShotsServer() {}

    /** A grimoire equipped (the one with the most spells), merit stars, Mana Cores, a few past hunts and quests. */
    public static void prepare(ServerPlayer p) {
        GrimoireMastery.Book best = null;
        for (GrimoireMastery.Book b : GrimoireMastery.BOOKS) if (best == null || b.unlocks().size() > best.unlocks().size()) best = b;
        if (best != null) {
            ItemStack book = new ItemStack(best.item().get());
            CuriosApi.getCuriosInventory(p).ifPresent(h -> h.setEquippedCurio("spellbook", 0, book));
            p.getInventory().add(book.copy());
        }
        Bounties.addStars(p, 37);
        p.getInventory().add(new ItemStack(AddonItemRegistry.MANA_CORE.get(), 3));
        p.getInventory().add(new ItemStack(AddonItemRegistry.DEVIL_SHARD.get(), 2));
        Boards.log(p, Boards.BOUNTY, Boards.DONE, Bounties.NAMES[0], 7);
        Boards.log(p, Boards.BOUNTY, Boards.GAVE_UP, Bounties.NAMES[1], 0);
        Boards.log(p, Boards.BOUNTY, Boards.EXPIRED, Bounties.NAMES[Math.min(2, Bounties.NAMES.length - 1)], 0);
        Boards.log(p, Boards.QUEST, Boards.DONE, "Daily: " + Quests.TITLES[0], 2);
        Boards.log(p, Boards.QUEST, Boards.DONE, Quests.TITLES[1], 1);
    }

    public static void board(ServerPlayer p, int kind) { Boards.open(p, kind, p.blockPosition()); }
    /** Takes the first note on a board (so the HUD has a hunt and a quest to track). */
    public static void take(ServerPlayer p, int kind) { Boards.open(p, kind, p.blockPosition()); Boards.pick(p, kind, 0); }
    public static void forge(ServerPlayer p) { StarForge.open(p); }
    public static void altar(ServerPlayer p) { AltarMenu.open(p, p.blockPosition()); }
    public static void library(ServerPlayer p) { GrimoireLibrary.open(p); }
    public static void waypoints(ServerPlayer p) { Waypoints.open(p); }
    public static ItemStack book(ServerPlayer p) {
        for (ItemStack s : p.getInventory().items) if (GrimoireMastery.bookOf(s) != null) return s;
        return ItemStack.EMPTY;
    }
    public static BlockPos at(ServerPlayer p) { return p.blockPosition(); }

    /** Dante, standing four blocks in front of the camera (facing it, or turned away), for a look at his cloak. */
    public static void dante(ServerPlayer p, boolean back, boolean flare) {
        for (var e : p.serverLevel().getEntitiesOfClass(com.ironspellgrimoire.entity.Dante.class, p.getBoundingBox().inflate(30))) e.discard();
        var d = com.ironspellgrimoire.registry.AddonEntities.DANTE.get().create(p.serverLevel());
        if (d == null) return;
        var look = p.getLookAngle().multiply(1, 0, 1).normalize();
        var at = p.position().add(look.scale(4.2));
        float face = (float) Math.toDegrees(Math.atan2(look.x, -look.z)) + (back ? 180 : 0);
        d.moveTo(at.x, p.getY(), at.z, face, 0);
        d.setYHeadRot(face); d.yBodyRot = face;
        d.setNoAi(true);
        p.serverLevel().addFreshEntity(d);
        if (flare) d.setPose(BossKit.P_RAISE);
    }
}
