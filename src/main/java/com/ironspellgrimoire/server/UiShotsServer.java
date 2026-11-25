package com.ironspellgrimoire.server;

import com.ironspellgrimoire.registry.AddonItemRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
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
    /** The boss ladder for the pictures: a mage two tiers up with the Gate on offer, a streak, a few kills and best times. */
    public static void ladder(ServerPlayer p, String what) {
        if (what.equals("setup")) {
            BossLadder.testReset(p);
            BossLadder.testKill(p, 4, 2460, 2); BossLadder.testKill(p, 4, 1980, 2); BossLadder.testKill(p, 4, 1700, 1);
            BossLadder.testKill(p, 5, 3300, 3); BossLadder.testKill(p, 0, 4100, 1);
            BossLadder.data(p).putInt("streak", 3);
            BossLadder.fell(p, 0);
            p.getPersistentData().remove(Boards.KEY[Boards.BOUNTY]);
            p.getPersistentData().remove("isg_bounty"); p.getPersistentData().remove("isg_bounty_next");
            BossLadder.sync(p);
        } else if (what.equals("board")) {
            Boards.open(p, Boards.BOUNTY, p.blockPosition());
        } else {
            BossLadder.testReset(p);
            p.getPersistentData().remove(Boards.KEY[Boards.BOUNTY]);
            BossLadder.sync(p);
        }
    }
    public static void altar(ServerPlayer p) { AltarMenu.open(p, p.blockPosition()); }
    public static void library(ServerPlayer p) { GrimoireLibrary.open(p); }
    public static void waypoints(ServerPlayer p) { Waypoints.open(p); }
    public static ItemStack book(ServerPlayer p) {
        for (ItemStack s : p.getInventory().items) if (GrimoireMastery.bookOf(s) != null) return s;
        return ItemStack.EMPTY;
    }
    public static BlockPos at(ServerPlayer p) { return p.blockPosition(); }

    /** The Blood Grimoire's Scarlet Moon, on the test player (for the third-person pictures). */
    public static void scarletMoon(ServerPlayer p) {
        ItemStack book = new ItemStack(AddonItemRegistry.BLOOD_GRIMOIRE.get());
        CuriosApi.getCuriosInventory(p).ifPresent(h -> h.setEquippedCurio("spellbook", 0, book));
        BloodFx.showGauge(p, BloodFx.MAX);
        BloodFx.scarletMoon(p, 10f);
    }
    /** The Gravity or Sound Grimoire equipped, and one of its spells cast where the player looks ("target" puts a still husk 6 ahead). */
    /** The shadow shots face the player the way the camera looks (+Z), so what the spells raise lands in view. */
    private static net.minecraft.world.phys.Vec3 beatenAt;

    /** For the screenshots: the exclusive shadow is not yet won, so it can be challenged again. */
    private static void freshChallenge(ServerPlayer p, int i) {
        ShadowArmy.list(p).removeIf(t -> ShadowArmy.EXCLUSIVES[i].equals(((net.minecraft.nbt.CompoundTag) t).getString("type")));
        ShadowArmy.root(p).remove("isg_shadow_beaten_" + i);
        ShadowArmy.recallAll(p);
    }

    private static void faceForward(ServerPlayer p) {
        p.setYRot(0); p.setYHeadRot(0); p.setYBodyRot(0); p.setXRot(0);
        p.connection.teleport(p.getX(), p.getY(), p.getZ(), 0, 0);
    }

    /** Elite mobs for the screenshots: a row of them in front of the player (no AI, so they hold still), a cursed chest
     *  and a rift; "clear" takes them away. */
    /** The wild families in a row, five at a time (a = the Mirefolk and Stonefolk's first two, b = the rest), facing the camera. */
    /** The Ironbound Twins side by side, facing the camera, the chain drawn taut between them. */
    public static void twins(ServerPlayer p) {
        folk(p, "none");
        ServerLevel sl = p.serverLevel();
        var h = AddonEntities.TWIN_HALDRIC.get().create(sl); var o = AddonEntities.TWIN_OSWIN.get().create(sl);
        if (h == null || o == null) return;
        h.moveTo(p.getX() - 5, p.getY(), p.getZ() + 9, 180, 0); o.moveTo(p.getX() + 5, p.getY(), p.getZ() + 9, 180, 0);
        for (var t : new com.ironspellgrimoire.entity.IronboundTwin[]{h, o}) { t.setYBodyRot(180); t.setYHeadRot(180); t.setNoAi(true); }
        h.partner = o.getUUID(); o.partner = h.getUUID();
        sl.addFreshEntity(h); sl.addFreshEntity(o);
        for (int i = 0; i < 6; i++) AstaFx.later(1 + i * 4, () -> BossFx.beam(sl, Fx.STEEL, h.position().add(0, 1.6, 0), o.position().add(0, 1.6, 0), 0.25f, 6));
    }

    /** A star coming down in front of the camera; it lands and its guardians gather. */
    public static void star(ServerPlayer p) {
        folk(p, "none");
        ServerLevel sl = p.serverLevel();
        var s = FallingStars.callDown(sl, p.position().add(0, 0, 10), false);
        if (s != null) s.setPos(s.getX(), s.groundY + 10, s.getZ());
    }

    /** The Cinder Sovereign in its second form's last stage (the Ashen Crown), standing still, a way off. */
    public static void ashen(ServerPlayer p) {
        folk(p, "none");
        ServerLevel sl = p.serverLevel();
        var b = AddonEntities.CINDER_SOVEREIGN.get().create(sl);
        if (b == null) return;
        b.setAshen(true);
        b.setPhase(4);
        b.moveTo(p.getX(), p.getY(), p.getZ() + 30, 180, 0);
        b.setYBodyRot(180); b.setYHeadRot(180);
        b.setNoAi(true);
        sl.addFreshEntity(b);
    }

    public static void folk(ServerPlayer p, String what) {
        ServerLevel sl = p.serverLevel();
        for (var e : sl.getEntitiesOfClass(com.ironspellgrimoire.entity.WildFolk.class, p.getBoundingBox().inflate(24))) e.discard();
        for (var e : sl.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, p.getBoundingBox().inflate(48),
                x -> x instanceof com.ironspellgrimoire.entity.IronboundTwin || x instanceof com.ironspellgrimoire.entity.FallingStar || x instanceof com.ironspellgrimoire.entity.CinderSovereign
                        || x.getPersistentData().contains(EliteMobs.TAG))) e.discard();
        if (what.equals("clear")) return;
        p.server.setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);
        faceForward(p);
        var kinds = com.ironspellgrimoire.entity.WildFolk.Kind.values();
        int from = what.equals("a") ? 0 : what.equals("b") ? 5 : what.equals("c") ? 10 : 15;
        for (int i = 0; i < 5 && from + i < kinds.length; i++) {
            var k = kinds[from + i];
            var m = WildFolkAI.type(k).create(sl);
            if (m == null) continue;
            m.getPersistentData().putBoolean("isg_folk_band", true);
            m.moveTo(p.getX() + (i - 2) * 2.2, p.getY(), p.getZ() + 7, 180, 0);
            m.setYBodyRot(180); m.setYHeadRot(180);
            m.setNoAi(true);
            sl.addFreshEntity(m);
        }
    }

    public static void elites(ServerPlayer p, String what) {
        ServerLevel sl = p.serverLevel();
        var box = p.getBoundingBox().inflate(16);
        for (var e : sl.getEntities((net.minecraft.world.entity.Entity) null, box, e -> EliteMobs.marked(e) || e instanceof com.ironspellgrimoire.entity.CursedChest || e instanceof com.ironspellgrimoire.entity.EliteRift)) e.discard();
        if (what.equals("clear")) return;
        p.server.setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);   // Peaceful would delete them at once
        faceForward(p);
        p.getPersistentData().putInt(EliteMobs.SEEN, (1 << EliteMobs.COUNT) - 1);
        EliteMobs.testThreat(p, 4);
        EliteMobs.sendSeen(p);
        Object[][] row = {
                {net.minecraft.world.entity.EntityType.VINDICATOR, 0.0, 1, 1 << EliteMobs.BULWARK | 1 << EliteMobs.IRONHIDE},
                {net.minecraft.world.entity.EntityType.SKELETON, -3.0, 2, 1 << EliteMobs.REFLECTOR | 1 << EliteMobs.VOLLEY},
                {net.minecraft.world.entity.EntityType.PILLAGER, 3.0, 5, 1 << EliteMobs.MIRROR_TWIN | 1 << EliteMobs.LEECH},
                {net.minecraft.world.entity.EntityType.VINDICATOR, 6.5, 3, 1 << EliteMobs.TETHER | 1 << EliteMobs.HUNTER | 1 << EliteMobs.SPRINTER}};
        for (Object[] r : row) {
            @SuppressWarnings("unchecked") var type = (net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.Mob>) r[0];
            var m = type.create(sl);
            if (m == null) continue;
            m.moveTo(p.getX() + (double) r[1], p.getY(), p.getZ() + 6, 180, 0);
            m.setYBodyRot(180); m.setYHeadRot(180);
            m.setNoAi(true);
            if (type == net.minecraft.world.entity.EntityType.SKELETON) m.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(net.minecraft.world.item.Items.BOW));
            sl.addFreshEntity(m);
            EliteMobs.testMake(m, (int) r[2], (int) r[3]);
            if (EliteMobs.has(m, EliteMobs.REFLECTOR)) {
                EliteMobs.data(m).putInt("phase", (int) ((EliteTraits.SHELL_CYCLE - sl.getGameTime() % EliteTraits.SHELL_CYCLE - 5 + EliteTraits.SHELL_CYCLE) % EliteTraits.SHELL_CYCLE));
                EliteMobs.sync(m);
            }
            if (EliteMobs.has(m, EliteMobs.TETHER)) EliteTraits.testChain(m, p, 200);
            if (EliteMobs.has(m, EliteMobs.MIRROR_TWIN) && EliteMobs.data(m).hasUUID("tw") && sl.getEntity(EliteMobs.data(m).getUUID("tw")) instanceof net.minecraft.world.entity.Mob sh) {
                sh.setNoAi(true);
                sh.moveTo(p.getX() + 4.8, p.getY(), p.getZ() + 8, 180, 0);
                sh.setYBodyRot(180); sh.setYHeadRot(180);
            }
        }
        var half = net.minecraft.world.entity.EntityType.VINDICATOR.create(sl);   // a Splitter's copy
        if (half != null) {
            half.moveTo(p.getX() - 1.8, p.getY(), p.getZ() + 3.5, 160, 0); half.setYBodyRot(160); half.setNoAi(true);
            net.minecraft.nbt.CompoundTag d = new net.minecraft.nbt.CompoundTag(); d.putInt("r", 2); d.putBoolean("child", true);
            half.getPersistentData().put(EliteMobs.TAG, d);
            half.refreshDimensions();
            sl.addFreshEntity(half);
            EliteMobs.testMake(half, 2, 0);
            EliteMobs.data(half).putBoolean("child", true);
            half.refreshDimensions();
            EliteMobs.sync(half);
        }
        var chest = com.ironspellgrimoire.registry.AddonEntities.CURSED_CHEST.get().create(sl);
        if (chest != null) { chest.setup(java.util.List.of(), 2); chest.moveTo(p.getX() + 1, p.getY(), p.getZ() + 3, 200, 0); sl.addFreshEntity(chest); }
        var rift = com.ironspellgrimoire.registry.AddonEntities.ELITE_RIFT.get().create(sl);
        if (rift != null) { rift.setup("minecraft:zombie", 0); rift.moveTo(p.getX() + 3.5, p.getY(), p.getZ() + 3.5, 0, 0); sl.addFreshEntity(rift); }
    }

    /** The Meridian Grimoire's pictures: Dayreaver held at noon, the star and a flare, Zenith, the night (and back to noon). */
    static void meridian(ServerPlayer p, String w) {
        ServerLevel sl = p.serverLevel();
        switch (w) {
            case "noon" -> {
                for (var m : sl.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, p.getBoundingBox().inflate(40))) m.discard();   // what earlier pictures left
                sl.setDayTime(6000); MeridianFx.testReset(p); MeridianFx.callDayreaver(p, 10);
            }
            case "star" -> MeridianFx.hearthStar(p, 10);
            case "flare" -> MeridianFx.haughtyFlare(p, 26);
            case "zenith" -> MeridianFx.zenithUnbound(p, 0);
            case "night" -> { sl.setDayTime(18000); MeridianFx.lamplight(p, 7); MeridianFx.lastCoal(p, 0); }
            default -> { sl.setDayTime(6000); MeridianFx.testReset(p); p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY); }
        }
        MeridianFx.sync(p);
    }

    /** The Grimoire of Ill Omen's pictures: marks over a zombie and a villager that pursue, the Afterimage walking off,
     *  The Law Itself coming down on both. */
    static void omen(ServerPlayer p, String w) {
        ServerLevel sl = p.serverLevel();
        switch (w) {
            case "marks" -> {
                for (var m : sl.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, p.getBoundingBox().inflate(40))) m.discard();
                sl.setDayTime(6000); OmenFx.testReset(p);
                var look = p.getLookAngle().multiply(1, 0, 1).normalize();
                var side = new net.minecraft.world.phys.Vec3(-look.z, 0, look.x);
                var z = net.minecraft.world.entity.EntityType.HUSK.create(sl);   // a husk: a zombie would burn in the noon sun
                var v = net.minecraft.world.entity.EntityType.VILLAGER.create(sl);
                int i = 0;
                for (var m : new net.minecraft.world.entity.Mob[]{z, v}) {
                    if (m == null) continue;
                    var at = p.position().add(look.scale(6)).add(side.scale(i++ == 0 ? -1.6 : 1.6));
                    m.moveTo(at.x, p.getY(), at.z, p.getYRot() + 180, 0); m.setNoAi(true); m.setYHeadRot(p.getYRot() + 180);
                    m.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000); m.setHealth(1000);
                    sl.addFreshEntity(m);
                }
                if (z != null) { z.setTarget(p); OmenFx.testSet(z, 45, 0); }
                if (v != null) { OmenFx.testStruck(v, p); OmenFx.testSet(v, 88, 4); }   // a villager that struck you is a pursuer too
            }
            case "after" -> {
                OmenFx.afterimage(p, 0);
                var look = p.getLookAngle().multiply(1, 0, 1).normalize();
                for (var a : sl.getEntitiesOfClass(com.ironspellgrimoire.entity.OmenAfterimage.class, p.getBoundingBox().inflate(4))) a.heading = new net.minecraft.world.phys.Vec3(-look.z, 0, look.x);
            }
            case "law" -> OmenFx.theLawItself(p, 0);
            default -> {
                for (var m : sl.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, p.getBoundingBox().inflate(40))) m.discard();
                OmenFx.testReset(p);
            }
        }
    }

    /** The Vector, Quiet Fuse and Foreseen pictures: each tree part-learned and open, the wheel, the False Future double. */
    static void trio(ServerPlayer p, String w) {
        ItemStack b = BookTree.book(p);
        int k = BookTree.kind(b);
        switch (w) {
            case "tree" -> {
                BookTree.testXp(b, 1500);
                String[][] picks = {{"r1:3", "r2:1", "r4:1", "m1:2", "m3:1", "f1:1", "f2:1", "w1:1", "p1:1"},
                        {"p1:2", "p2:1", "p3:1", "s1:1", "s4:1", "a1:2", "a2:1", "b1:1", "w1:1", "w3:1"},
                        {"s1:2", "s3:1", "e1:2", "e2:1", "e3:1", "c1:1", "c2:1", "t1:1", "w2:1"}};
                for (String s : picks[Math.max(0, k)]) { String[] q = s.split(":"); BookTree.testSet(b, q[0], Integer.parseInt(q[1])); }
                BookTree.sync(p, true);
            }
            case "sync" -> BookTree.sync(p, false);
            case "double" -> {
                for (var m : p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, p.getBoundingBox().inflate(40))) m.discard();
                p.serverLevel().setDayTime(6000);
                var look = p.getLookAngle().multiply(1, 0, 1).normalize();
                ForeseenFx.spawnDouble(p, p.position().add(look.scale(3)).add(-look.z * 2.2, 0, look.x * 2.2), 200);   // to one side, where the camera sees it
            }
            default -> {
                for (var m : p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, p.getBoundingBox().inflate(40))) m.discard();
                BookTree.testClearCd(p);
            }
        }
    }

    /** The Grimoire of the Bound Demon's pictures: the Bond Tree part-learned, a takeover (the eye burning), the branched
     *  wing, the black pair. */
    static void bound(ServerPlayer p, String w) {
        ItemStack b = BookTree.book(p);
        ServerLevel sl = p.serverLevel();
        switch (w) {
            case "tree" -> {
                BookTree.testXp(b, 1500);
                for (String s : new String[]{"d1", "d3", "d5", "k1", "k2", "k4", "b1", "b2", "s1", "s2", "w1"}) BookTree.testSet(b, s, 1);
                BookTree.sync(p, true);
            }
            case "takeover" -> {
                for (var m : sl.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, p.getBoundingBox().inflate(40))) m.discard();
                sl.setDayTime(6000);
                BoundFx.testReset(p);
                var look = p.getLookAngle().multiply(1, 0, 1).normalize();
                var h = net.minecraft.world.entity.EntityType.HUSK.create(sl);
                if (h != null) { var at = p.position().add(look.scale(5)); h.moveTo(at.x, p.getY(), at.z, p.getYRot() + 180, 0); h.setNoAi(true);
                    h.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(140); h.setHealth(140); sl.addFreshEntity(h); }
                BoundFx.testFeed(p, 100f);
                BookTree.sync(p, false);
            }
            case "awake" -> {
                for (var m : sl.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, p.getBoundingBox().inflate(40))) m.discard();
                BoundFx.testReset(p);
                BookTree.testSet(b, "K4", 0);
                BoundFx.draw(p, false);
                BoundFx.awaken(p, false);
                BookTree.sync(p, false);
            }
            case "black" -> {
                BoundFx.endAwake(p);
                BookTree.testSet(b, "K4", 1);
                BoundFx.awaken(p, false);
                BookTree.sync(p, false);
            }
            default -> {
                for (var m : sl.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, p.getBoundingBox().inflate(40))) m.discard();
                BoundFx.testReset(p);
                BookTree.testSet(b, "K4", 0);
                BoundFx.broadcast(p);
                BookTree.testClearCd(p);
            }
        }
    }

    public static void element(ServerPlayer p, String which) {
        Moves.ends(p, BloodFx.MODE);
        String w = which.substring(2);
        ItemStack book = new ItemStack(switch (which.charAt(0)) {
            case 'g' -> AddonItemRegistry.GRAVITY_GRIMOIRE.get();
            case 'i' -> AddonItemRegistry.INK_GRIMOIRE.get();
            case 'e' -> AddonItemRegistry.ERROR_GRIMOIRE.get();
            case 't' -> AddonItemRegistry.TREASURE_GRIMOIRE.get();
            case 'c' -> AddonItemRegistry.CHAIN_GRIMOIRE.get();
            case 'h' -> AddonItemRegistry.HUNTER_GRIMOIRE.get();
            case 'v' -> AddonItemRegistry.VOID_GRIMOIRE.get();
            case 'b' -> AddonItemRegistry.GAMBLER_GRIMOIRE.get();
            case 'r' -> AddonItemRegistry.TAROT_GRIMOIRE.get();
            case 'a' -> AddonItemRegistry.ABYSS_GRIMOIRE.get();
            case 'm' -> AddonItemRegistry.MECH_GRIMOIRE.get();
            case 'w' -> AddonItemRegistry.WEATHER_GRIMOIRE.get();
            case 'o' -> AddonItemRegistry.OVERDRIVE_GRIMOIRE.get();
            case 'd' -> AddonItemRegistry.DREAM_GRIMOIRE.get();
            case 's' -> AddonItemRegistry.SHADOW_GRIMOIRE.get();
            case 'p' -> AddonItemRegistry.PARASITE_GRIMOIRE.get();
            case 'H' -> AddonItemRegistry.HUNGER_GRIMOIRE.get();
            case 'T' -> AddonItemRegistry.TWINS_GRIMOIRE.get();
            case 'A' -> AddonItemRegistry.ARCHITECT_GRIMOIRE.get();
            case 'M' -> AddonItemRegistry.MERIDIAN_GRIMOIRE.get();
            case 'O' -> AddonItemRegistry.OMEN_GRIMOIRE.get();
            case 'X' -> AddonItemRegistry.VECTOR_GRIMOIRE.get();
            case 'Q' -> AddonItemRegistry.FUSE_GRIMOIRE.get();
            case 'F' -> AddonItemRegistry.FORESEEN_GRIMOIRE.get();
            case 'B' -> AddonItemRegistry.BOUND_GRIMOIRE.get();
            default -> AddonItemRegistry.SOUND_GRIMOIRE.get();
        });
        net.minecraft.nbt.CompoundTag mt = new net.minecraft.nbt.CompoundTag(); mt.putInt("xp", 10_000_000);
        book.getOrCreateTag().put(GrimoireMastery.masteryTag(), mt);
        ItemStack cur = io.redspace.ironsspellbooks.api.util.Utils.getPlayerSpellbookStack(p);
        if (cur == null || !cur.is(book.getItem())) CuriosApi.getCuriosInventory(p).ifPresent(h -> h.setEquippedCurio("spellbook", 0, book));
        io.redspace.ironsspellbooks.api.magic.MagicData.getPlayerMagicData(p).setMana(2000);
        if (which.charAt(0) == 'p') { parasite(p, w); return; }
        if (which.charAt(0) == 'H') { hunger(p, w); return; }
        if (which.charAt(0) == 'T') { twins(p, w); return; }
        if (which.charAt(0) == 'A') { architect(p, w); return; }
        if (which.charAt(0) == 'M') { meridian(p, w); return; }
        if (which.charAt(0) == 'O') { omen(p, w); return; }
        if ("XQF".indexOf(which.charAt(0)) >= 0) { trio(p, w); return; }
        if (which.charAt(0) == 'B') { bound(p, w); return; }
        switch (w) {
            case "target" -> {
                for (var old : p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.monster.Husk.class, p.getBoundingBox().inflate(40))) old.discard();
                var look = p.getLookAngle().multiply(1, 0, 1).normalize();
                var z = net.minecraft.world.entity.EntityType.HUSK.create(p.serverLevel());
                if (z != null) {
                    var at = p.position().add(look.scale(7));
                    z.moveTo(at.x, p.getY(), at.z, 0, 0); z.setNoAi(true);
                    z.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000); z.setHealth(1000);
                    p.serverLevel().addFreshEntity(z);
                }
            }
            case "gravity_bullet" -> GravityFx.gravityBullet(p, 10f);
            case "crushing_field" -> GravityFx.crushingField(p, 10f);
            case "gravity_well" -> GravityFx.gravityWell(p, 10f);
            case "gravity_lift" -> GravityFx.gravityLift(p, 10f);
            case "orbiting_mass" -> GravityFx.orbitingMass(p, 10f);
            case "repulse" -> GravityFx.repulse(p, 10f);
            case "mass_domain" -> GravityFx.massDomain(p, 10f);
            case "starfall_collapse" -> GravityFx.starfallCollapse(p, 10f);
            case "sonic_pulse" -> SoundFx.sonicPulse(p, 10f);
            case "sonic_lance" -> SoundFx.sonicLance(p, 10f);
            case "sound_barrier" -> SoundFx.soundBarrier(p, 10f);
            case "silence_field" -> SoundFx.silenceField(p, 10f);
            case "echo_chamber" -> SoundFx.echoChamber(p, 10f);
            case "tuning_strike" -> SoundFx.tuningStrike(p, 10f);
            case "thunderclap" -> SoundFx.thunderclap(p, 10f);
            case "crescendo" -> SoundFx.crescendoMode(p, 10f);
            case "final_chord" -> SoundFx.finalChord(p, 10f);
            case "brush_stroke" -> InkFx.brushStroke(p, 10f);
            case "ink_splash" -> InkFx.inkSplash(p, 10f);
            case "ink_serpent" -> InkFx.inkSerpent(p, 10f);
            case "seal_script" -> InkFx.sealScript(p, 10f);
            case "ink_beasts" -> InkFx.inkBeasts(p, 10f);
            case "ink_wave" -> InkFx.inkWave(p, 10f);
            case "masterpiece" -> InkFx.masterpieceMode(p, 10f);
            case "grand_calligraphy" -> InkFx.grandCalligraphy(p, 10f);
            case "glitch_bolt" -> ErrorFx.glitchBolt(p, 10f);
            case "corrupted_threads" -> ErrorFx.corruptedThreads(p, 10f);
            case "lag_spike" -> ErrorFx.lagSpike(p, 10f);
            case "crash_zone" -> ErrorFx.crashZone(p, 10f);
            case "error_windows" -> ErrorFx.errorWindows(p, 10f);
            case "blue_screen" -> ErrorFx.blueScreen(p, 10f);
            case "fatal_error" -> ErrorFx.fatalError(p, 10f);
            case "system_crash" -> ErrorFx.systemCrash(p, 10f);
            case "vault_volley" -> TreasureFx.vaultVolley(p, 10f);
            case "gilded_spear" -> TreasureFx.gildedSpear(p, 10f);
            case "vault_chains" -> TreasureFx.vaultChains(p, 10f);
            case "blade_wall" -> TreasureFx.bladeWall(p, 10f);
            case "blade_rain" -> TreasureFx.bladeRain(p, 10f);
            case "treasure_barrage" -> TreasureFx.treasureBarrage(p, 10f);
            case "kings_vault" -> TreasureFx.kingsVault(p, 10f);
            case "vault_breaker" -> TreasureFx.vaultBreaker(p, 10f);
            case "chain_lash" -> ChainFx.chainLash(p, 10f);
            case "binding_chain" -> ChainFx.bindingChain(p, 10f);
            case "dowsing_chain" -> ChainFx.dowsingChain(p, 10f);
            case "healing_chain" -> ChainFx.healingChain(p, 10f);
            case "judgment_chain" -> ChainFx.judgmentChain(p, 10f);
            case "stealing_chain" -> ChainFx.stealingChain(p, 10f);
            case "chain_net" -> ChainFx.chainNet(p, 10f);
            case "anchor_chain" -> ChainFx.anchorChain(p, 10f);
            case "restriction" -> ChainFx.restriction(p, 10f);
            case "thousand_chains" -> ChainFx.thousandChains(p, 10f);
            case "saw_cleave" -> HunterFx.sawCleave(p, 10f);
            case "trick_switch" -> HunterFx.trickSwitch(p, 10f);
            case "silver_shot" -> HunterFx.silverShot(p, 10f);
            case "quickstep" -> HunterFx.quickstep(p, 10f);
            case "visceral_strike" -> HunterFx.visceralStrike(p, 10f);
            case "hunters_mark" -> HunterFx.huntersMark(p, 10f);
            case "rending_spin" -> HunterFx.rendingSpin(p, 10f);
            case "blood_volley" -> HunterFx.bloodVolley(p, 10f);
            case "blood_moon_hunt" -> HunterFx.bloodMoonHunt(p, 10f);
            case "nightmare_cleave" -> HunterFx.nightmareCleave(p, 10f);
            case "erase" -> VoidFx.erase(p, 10f);
            case "null_step" -> VoidFx.nullStep(p, 10f);
            case "unmake" -> VoidFx.unmakeSpell(p, 10f);
            case "nullify" -> VoidFx.nullify(p, 10f);
            case "seam" -> VoidFx.seamSpell(p, 10f);
            case "absence" -> VoidFx.absenceSpell(p, 10f);
            case "void_lance" -> VoidFx.voidLance(p, 10f);
            case "delete" -> VoidFx.deleteSpell(p, 10f);
            case "emptiness" -> VoidFx.emptinessMode(p, 10f);
            case "origin_zero" -> VoidFx.originZero(p, 10f);
            case "coin_toss" -> GamblerFx.coinToss(p, 10f);
            case "double_or_nothing" -> GamblerFx.doubleOrNothing(p, 10f);
            case "roulette" -> GamblerFx.roulette(p, 10f);
            case "chip_shot" -> GamblerFx.chipShot(p, 10f);
            case "lucky_seven" -> GamblerFx.luckySeven(p, 10f);
            case "slot_machine" -> GamblerFx.slotMachine(p, 10f);
            case "loaded_dice" -> GamblerFx.loadedDice(p, 10f);
            case "insurance" -> GamblerFx.insurance(p, 10f);
            case "all_in" -> GamblerFx.allInMode(p, 10f);
            case "jackpot" -> GamblerFx.jackpot(p, 10f);
            case "draw_card" -> TarotFx.drawCard(p, 10f);
            case "the_magician" -> TarotFx.magicianCard(p, 10f);
            case "the_tower" -> TarotFx.towerCard(p, 10f);
            case "the_star" -> TarotFx.starCard(p, 10f);
            case "death_card" -> TarotFx.deathCard(p, 10f);
            case "wheel_of_fortune" -> TarotFx.wheelCard(p, 10f);
            case "the_hanged_man" -> TarotFx.hangedCard(p, 10f);
            case "three_card_spread" -> TarotFx.threeCardSpread(p, 10f);
            case "the_world" -> TarotFx.worldMode(p, 10f);
            case "judgement" -> TarotFx.judgementCard(p, 10f);
            case "crushing_pressure" -> AbyssFx.crushingPressure(p, 10f);
            case "anglers_lure" -> AbyssFx.anglersLure(p, 10f);
            case "abyssal_grasp" -> AbyssFx.abyssalGrasp(p, 10f);
            case "undertow" -> AbyssFx.undertowSpell(p, 10f);
            case "abyssal_dive" -> AbyssFx.abyssalDive(p, 10f);
            case "depth_charge" -> AbyssFx.depthCharge(p, 10f);
            case "leviathan" -> AbyssFx.leviathanSpell(p, 10f);
            case "hadal_weight" -> AbyssFx.hadalWeight(p, 10f);
            case "bioluminescence" -> AbyssFx.bioluminescence(p, 10f);
            case "the_trench" -> AbyssFx.theTrench(p, 10f);
            case "rivet_barrage" -> MechFx.rivetBarrage(p, 10f);
            case "thruster_dash" -> MechFx.thrusterDash(p, 10f);
            case "rocket_fist" -> MechFx.rocketFist(p, 10f);
            case "piston_stomp" -> MechFx.pistonStomp(p, 10f);
            case "bulwark_plating" -> MechFx.bulwarkPlating(p, 10f);
            case "missile_swarm" -> MechFx.missileSwarm(p, 10f);
            case "overclock" -> MechFx.overclockSpell(p, 10f);
            case "rail_cannon" -> MechFx.railCannon(p, 10f);
            case "pilot" -> MechFx.pilotMode(p, 10f);
            case "titan_buster" -> MechFx.titanBuster(p, 10f);
            case "sunbeam" -> WeatherFx.sunbeamSpell(p, 10f);
            case "downpour" -> WeatherFx.downpour(p, 10f);
            case "thunder_strike" -> WeatherFx.thunderclap(p, 10f);
            case "hailstorm" -> WeatherFx.hailstorm(p, 10f);
            case "gale" -> WeatherFx.galeSpell(p, 10f);
            case "fog_bank" -> WeatherFx.fogBank(p, 10f);
            case "cyclone" -> WeatherFx.cycloneSpell(p, 10f);
            case "rainbow" -> WeatherFx.rainbowSpell(p, 10f);
            case "eye_of_the_storm" -> WeatherFx.eyeOfTheStorm(p, 10f);
            case "cataclysm" -> WeatherFx.cataclysmSpell(p, 10f);
            case "draw_heartline" -> OverdriveFx.drawHeartline(p, 10f);
            case "pulse_cut" -> OverdriveFx.thunderDraw(p, 10f);
            case "adrenaline_rush" -> OverdriveFx.adrenalineRush(p, 10f);
            case "heartbeat" -> OverdriveFx.readPulse(p, 10f);
            case "flatline" -> OverdriveFx.flatlineThrust(p, 10f);
            case "tachycardia" -> OverdriveFx.tachycardia(p, 10f);
            case "second_wind" -> OverdriveFx.secondWind(p, 10f);
            case "ribbon_bind" -> OverdriveFx.ribbonBind(p, 10f);
            case "redline" -> OverdriveFx.lastMinute(p, 10f);
            case "final_beat" -> OverdriveFx.finalBeat(p, 10f);
            case "dream_mote" -> DreamFx.lullabyMote(p, 10f);
            case "counting_sheep" -> DreamFx.countingSheep(p, 10f);
            case "pillow_fortress" -> DreamFx.pillowFortress(p, 10f);
            case "sleepwalk" -> DreamFx.sleepwalk(p, 10f);
            case "dream_clock" -> DreamFx.dreamClock(p, 10f);
            case "toy_parade" -> DreamFx.toyParade(p, 10f);
            case "nightmare_shard" -> DreamFx.nightmareShard(p, 10f);
            case "rude_awakening" -> DreamFx.rudeAwakening(p, 10f);
            case "half_asleep" -> DreamFx.halfAsleep(p, 10f);
            case "dream_world" -> DreamFx.dreamWorld(p, 10f);
            case "remnant" -> { faceForward(p); ShadowFx.testRemnant(p, p.position().add(0.6, 0, 4.5)); }   // in front of the camera
            case "rise" -> { faceForward(p); ShadowFx.rise(p, 10f); }
            case "umbral_step" -> { faceForward(p); ShadowFx.umbralStep(p, 10f); }
            case "unseen_hand" -> { faceForward(p); ShadowFx.unseenHand(p, 10f); }
            case "command_hunt" -> { faceForward(p); ShadowFx.commandHunt(p, 10f); }
            case "veil" -> ShadowFx.veil(p, 10f);
            case "challenge_knight" -> { faceForward(p); freshChallenge(p, 0); ShadowArmy.challenge(p, 0); }
            case "challenge_ravener" -> {   // the knight (now in the army) steps aside so the Ravener stands alone
                faceForward(p); freshChallenge(p, 1);
                for (var t : ShadowArmy.list(p)) ((net.minecraft.nbt.CompoundTag) t).putBoolean("eq", false);
                ShadowArmy.recallAll(p);
                ShadowArmy.challenge(p, 1);
            }
            case "beat" -> {   // the living form falls (as if beaten) - its remnant waits
                for (var s : p.serverLevel().getEntitiesOfClass(com.ironspellgrimoire.entity.ShadowSoldier.class, p.getBoundingBox().inflate(32), com.ironspellgrimoire.entity.ShadowSoldier::living)) {
                    beatenAt = s.position();
                    s.kill();
                }
            }
            case "arise" -> {   // look at its remnant, then Rise
                if (beatenAt != null) {
                    double dx = beatenAt.x - p.getX(), dz = beatenAt.z - p.getZ(), dy = beatenAt.y + 0.6 - p.getEyeY();
                    float yaw = (float) (Math.atan2(-dx, dz) * 180 / Math.PI), pitch = (float) (-Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * 180 / Math.PI);
                    p.setYRot(yaw); p.setYHeadRot(yaw); p.setXRot(pitch);
                    p.connection.teleport(p.getX(), p.getY(), p.getZ(), yaw, pitch);
                }
                ShadowFx.rise(p, 10f);
            }
            case "ranks_of_shade" -> { for (var t : ShadowArmy.list(p)) ((net.minecraft.nbt.CompoundTag) t).putBoolean("eq", true); ShadowArmy.keep(p); AstaFx.later(30, () -> ShadowFx.ranksOfShade(p, 10f)); }
            case "rally_of_the_fallen" -> ShadowFx.rallyOfTheFallen(p, 10f);
            case "status" -> { HunterAscension.testGrant(p, 6, 950); HunterAscension.openStatus(p, 0); }
            case "status_adv" -> { HunterAscension.testGrant(p, Math.max(0, HunterAscension.ADV_LEVEL - HunterAscension.level(p)), 0); HunterAscension.testJob(p, 1, false); HunterAscension.openStatus(p, 0); }
            case "status_advanced" -> { HunterAscension.testJob(p, 1, true); HunterAscension.testTitles(p, 0b10111011, 4); HunterAscension.openStatus(p, 0); }
            case "status_reset_job" -> { HunterAscension.testJob(p, 0, false); HunterAscension.testTitles(p, 0, -1); }
            case "status_job" -> { HunterAscension.testGrant(p, Math.max(0, HunterAscension.JOB_LEVEL - HunterAscension.level(p)), 0); HunterAscension.openStatus(p, 0); }   // the Job Change button
            case "job_choice" -> { HunterAscension.testJobReady(p); }   // the trial passed: the choice
            case "job_trial" -> {   // the trial begins (then ends, so it doesn't crowd the later shots)
                faceForward(p);
                HunterAscension.testJobReset(p);
                HunterAscension.handle(p, 14, 0);
                AstaFx.later(40, () -> HunterAscension.testEndReckoning(p, false));
            }
            case "market" -> HunterAscension.openMarket(p, 0);
            case "sovereigns_grip" -> { faceForward(p); ShadowFx.sovereignsGrip(p, 10f); }
            case "dread_roar" -> ShadowFx.dreadRoar(p, 10f);
            case "mantle_of_shades" -> { for (var t : ShadowArmy.list(p)) ((net.minecraft.nbt.CompoundTag) t).putBoolean("eq", true); ShadowArmy.keep(p); AstaFx.later(30, () -> ShadowFx.mantleOfShades(p, 10f)); }
            case "thousand_cuts" -> {
                faceForward(p);
                p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(com.ironspellgrimoire.registry.AddonItemRegistry.NIGHTFANG.get()));
                ShadowFx.thousandCuts(p);
            }
            case "shadow_step" -> { faceForward(p); ShadowFx.shadowStep(p, 10f); }
            case "knives" -> {   // the four knives laid out in the hotbar, the Duskglass Kris in hand with its ghost knives out
                var inv = p.getInventory();
                inv.setItem(1, new net.minecraft.world.item.ItemStack(com.ironspellgrimoire.registry.AddonItemRegistry.NIGHTFANG.get()));
                inv.setItem(2, new net.minecraft.world.item.ItemStack(com.ironspellgrimoire.registry.AddonItemRegistry.DRAGON_FANG.get()));
                p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(com.ironspellgrimoire.registry.AddonItemRegistry.DUSKGLASS_KRIS.get()));
                ShadowFx.phantomEdges(p);
            }
            case "dragon_fang" -> {   // the Dragon Fang in hand, then its lunge
                faceForward(p);
                p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(com.ironspellgrimoire.registry.AddonItemRegistry.DRAGON_FANG.get()));
            }
            case "wyrmfang_lunge" -> { faceForward(p); ShadowFx.wyrmfangLunge(p); }
            case "vanilla_sword" -> {   // a plain diamond sword in hand: the reference the new blades are held to match
                faceForward(p);
                p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD));
            }
            case "gilded_thorn" -> {   // the Gilded Thorn in hand (first person), in the hotbar beside the others
                faceForward(p);
                var inv = p.getInventory();
                inv.setItem(1, new net.minecraft.world.item.ItemStack(com.ironspellgrimoire.registry.AddonItemRegistry.NIGHTFANG.get()));
                inv.setItem(2, new net.minecraft.world.item.ItemStack(com.ironspellgrimoire.registry.AddonItemRegistry.DRAGON_FANG.get()));
                inv.setItem(3, new net.minecraft.world.item.ItemStack(com.ironspellgrimoire.registry.AddonItemRegistry.GILDED_THORN.get()));
                p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(com.ironspellgrimoire.registry.AddonItemRegistry.GILDED_THORN.get()));
            }
            case "thornbite" -> { faceForward(p); ShadowFx.thornbite(p); }
            case "bloodrend" -> {   // Bloodrend in hand (first person), in the hotbar beside the others
                faceForward(p);
                p.getInventory().setItem(4, new net.minecraft.world.item.ItemStack(com.ironspellgrimoire.registry.AddonItemRegistry.BLOODREND.get()));
                p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(com.ironspellgrimoire.registry.AddonItemRegistry.BLOODREND.get()));
            }
            case "rosewrath" -> {   // Rosewrath in hand (first person), in the hotbar beside the others
                faceForward(p);
                p.getInventory().setItem(5, new net.minecraft.world.item.ItemStack(com.ironspellgrimoire.registry.AddonItemRegistry.ROSEWRATH.get()));
                p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(com.ironspellgrimoire.registry.AddonItemRegistry.ROSEWRATH.get()));
            }
            case "frostwing" -> {   // the Frostwing greatsword in hand (first person), in the hotbar beside the others
                faceForward(p);
                p.getInventory().setItem(6, new net.minecraft.world.item.ItemStack(com.ironspellgrimoire.registry.AddonItemRegistry.FROSTWING.get()));
                p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(com.ironspellgrimoire.registry.AddonItemRegistry.FROSTWING.get()));
            }
            case "sovereigns_verdict" -> { faceForward(p); ShadowFx.sovereignsVerdict(p); }
            case "crimson_fracture" -> { faceForward(p); ShadowFx.crimsonFracture(p); }
            case "crimson_rend" -> { faceForward(p); p.setHealth(p.getMaxHealth() * 0.3f); ShadowFx.crimsonRend(p); }
            case "sovereigns_domain" -> ShadowFx.sovereignsDomain(p, 10f);
            case "night_legion" -> { faceForward(p); ShadowFx.nightLegion(p, 10f); }
            case "gate_of_shade" -> ShadowFx.gateOfShade(p, 10f);
            case "end" -> { Moves.ends(p, GravityFx.MODE); Moves.ends(p, SoundFx.MODE); Moves.ends(p, SoundFx.BARRIER); Moves.ends(p, InkFx.MODE); Moves.ends(p, ErrorFx.MODE); Moves.ends(p, TreasureFx.MODE); Moves.ends(p, ChainFx.MODE); Moves.ends(p, HunterFx.MODE); Moves.ends(p, VoidFx.MODE); Moves.ends(p, VoidFx.ABSENCE); Moves.ends(p, GamblerFx.MODE); Moves.ends(p, GamblerFx.INSURED); Moves.ends(p, TarotFx.MODE); Moves.ends(p, TarotFx.READ); Moves.ends(p, AbyssFx.MODE); Moves.ends(p, MechFx.MODE); Moves.ends(p, MechFx.PLATING); Moves.ends(p, WeatherFx.MODE); Moves.ends(p, WeatherFx.FOG_BANK); Moves.ends(p, OverdriveFx.MODE); Moves.ends(p, DreamFx.MODE); Moves.ends(p, ShadowFx.MODE); ShadowArmy.recallAll(p); }
            default -> {}
        }
    }

    /** The Hunger Blade Grimoire's shots: every sword held (first and third person), its moves, the wheel and The Blade page. */
    private static void hunger(ServerPlayer p, String w) {
        if (w.startsWith("hold")) {   // hold<form>: that sword in the hand
            faceForward(p);
            int f = Integer.parseInt(w.substring(4));
            p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            HungerFx.testHold(p, f);
            return;
        }
        switch (w) {
            case "setup" -> {   // every sword grown, four carried, a target ahead
                faceForward(p);
                HungerFx.testReset(p);
                HungerFx.testUnlockAll(p);
                HungerFx.testGrowth(p, 260);
                HungerFx.testLoad(p, 7, 10, 25, 28);
                HungerFx.testHunger(p, 82);
                HungerFx.testBond(p, 140);
                HungerFx.testCalm(p);
                p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                p.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, ItemStack.EMPTY);
                for (var old : p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.monster.Husk.class, p.getBoundingBox().inflate(40))) old.discard();
                for (var old : p.serverLevel().getEntitiesOfClass(com.ironspellgrimoire.entity.BladeSpirit.class, p.getBoundingBox().inflate(40))) old.discard();
                for (var old : p.serverLevel().getEntitiesOfClass(com.ironspellgrimoire.entity.ThrownHungerBlade.class, p.getBoundingBox().inflate(40))) old.discard();
                for (int k = -1; k <= 1; k++) {
                    var z = net.minecraft.world.entity.EntityType.HUSK.create(p.serverLevel());
                    if (z == null) continue;
                    var at = p.position().add(k * 2.2, 0, 5 + Math.abs(k));
                    z.moveTo(at.x, p.getY(), at.z, 180, 0); z.setNoAi(true);
                    z.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000); z.setHealth(1000);
                    p.serverLevel().addFreshEntity(z);
                }
                HungerFx.sync(p, false);
            }
            case "draw0", "draw1", "draw2", "draw3", "draw4" -> {   // each path's way of coming out
                faceForward(p);
                int f = new int[]{2, 5, 10, 15, 20}[w.charAt(4) - '0'];
                p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                HungerFx.testLoad(p, f, 7, 10, 25); HungerFx.testSelect(p, 0);
                HungerFx.draw(p, 10f);
            }
            case "lesson" -> { HungerFx.testReset(p); HungerFx.testUnlockAll(p); p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY); HungerFx.draw(p, 10f); HungerFx.testHunger(p, 20); }
            case "draw" -> { faceForward(p); p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY); HungerFx.draw(p, 10f); }
            case "gnash" -> { faceForward(p); HungerFx.gnash(p, 10f); }
            case "throw" -> { faceForward(p); HungerFx.testBond(p, 140); p.setShiftKeyDown(true); HungerFx.gnash(p, 10f); p.setShiftKeyDown(false); }
            case "feast" -> { faceForward(p); HungerFx.feast(p, 10f); }
            case "unleash" -> { faceForward(p); HungerFx.unleash(p, 10f); }
            case "pact" -> { faceForward(p); HungerFx.testBond(p, 230); HungerFx.unleash(p, 10f); }
            case "hungry" -> { HungerFx.testHunger(p, 20); HungerFx.sync(p, false); }
            case "starved" -> { HungerFx.testHunger(p, 4); HungerFx.sync(p, false); }
            case "fed" -> { HungerFx.testHunger(p, 82); HungerFx.sync(p, false); }
            case "page" -> { HungerFx.testHunger(p, 55); HungerFx.theBlade(p, 10f); }
            case "titles" -> { HungerFx.testBond(p, 1000); HungerFx.testDevours(p, 50); HungerFx.testTitles(p); HungerFx.handle(p, 4, 0); HungerFx.theBlade(p, 10f); }
            case "devour" -> {   // a husk near death in front, sneak-struck
                faceForward(p);
                var hs = p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.monster.Husk.class, p.getBoundingBox().inflate(8));
                if (!hs.isEmpty()) {
                    var z = hs.stream().min(java.util.Comparator.comparingDouble(x -> x.distanceToSqr(p))).get();
                    z.setHealth(z.getMaxHealth() * 0.1f);
                    p.setShiftKeyDown(true);
                    z.hurt(p.damageSources().playerAttack(p), 2f);
                    p.setShiftKeyDown(false);
                }
            }
            case "parry" -> {   // a husk strikes as the jaw guard catches it
                faceForward(p);
                HungerFx.testParry(p);
                var hs = p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.monster.Husk.class, p.getBoundingBox().inflate(8));
                if (!hs.isEmpty()) p.hurt(p.damageSources().mobAttack(hs.get(0)), 4f);
            }
            case "charge" -> { faceForward(p); HungerFx.testCharge(p); }
            case "twin" -> { HungerFx.testBond(p, 140); }
            case "sulk" -> { p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY); HungerFx.testHunger(p, 3); HungerFx.testStarvedFor(p, 70); HungerFx.draw(p, 10f); HungerFx.sync(p, false); }
            case "spirit" -> { faceForward(p); HungerFx.testBond(p, 230); HungerFx.unleash(p, 10f); }
            case "sheath" -> { p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY); p.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, ItemStack.EMPTY); }
            case "domain" -> { faceForward(p); HungerFx.hungerDomain(p, 10f); }
            case "ready" -> { HungerFx.testReset(p); root3(p); HungerFx.testGrowth(p, 50); HungerFx.sync(p, false); }
            case "page_new" -> { HungerFx.testReset(p); HungerFx.testGrowth(p, 45); root3(p); HungerFx.theBlade(p, 10f); }
            case "end" -> { p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY); HungerFx.testReset(p); }
            default -> {}
        }
    }

    /** The Masked Twins Grimoire's shots: the masks worn (third person), the swap, the arts, the wheel, the menu, the trial. */
    private static void twins(ServerPlayer p, String w) {
        if ((w.startsWith("sol") || w.startsWith("nyx")) && w.length() == 4 && Character.isDigit(w.charAt(3))) {   // sol<n> / nyx<n>: that twin worn, its art n cast at the husks ahead
            faceForward(p);
            int twin = w.startsWith("sol") ? TwinsFx.SOL : TwinsFx.NYX, art = w.charAt(3) - '0';
            TwinsFx.testCooldowns(p); TwinsFx.testActive(p, twin); TwinsFx.testLoad(p, twin, art, -1, -1, -1);
            TwinsArts.cast(p, twin, art);
            return;
        }
        switch (w) {
            case "setup" -> {   // a pair well on its way: Sol 32 (two stones), Nyx 27 (one), a target ahead
                faceForward(p);
                TwinsFx.testReset(p);
                TwinsFx.testLevels(p, 32, 27);
                TwinsFx.testLoad(p, TwinsFx.SOL, 0, 2, 3, 5); TwinsFx.testLoad(p, TwinsFx.NYX, 0, 1, 2, 5);
                TwinsFx.testStones(p, 2, 1); TwinsFx.testSockets(p, 3, 1); TwinsFx.testTitles(p, 1 | 2); TwinsFx.testRes(p, 64);
                TwinsFx.testXp(p, TwinsFx.SOL, 60);
                TwinsFx.testUses(p, TwinsFx.SOL, 0, 120); TwinsFx.testUses(p, TwinsFx.SOL, 2, 260); TwinsFx.testUses(p, TwinsFx.SOL, 3, 45);
                TwinsFx.testUses(p, TwinsFx.NYX, 1, 260); TwinsFx.testUses(p, TwinsFx.NYX, 2, 70);
                TwinsFx.root(p).putIntArray("cut0", new int[]{0, 2, 0}); TwinsFx.root(p).putIntArray("cut1", new int[]{1, 0, 0});
                p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                for (var old : p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.monster.Husk.class, p.getBoundingBox().inflate(40))) old.discard();
                for (int k = -1; k <= 1; k++) {
                    var z = net.minecraft.world.entity.EntityType.HUSK.create(p.serverLevel());
                    if (z == null) continue;
                    var at = p.position().add(k * 2.2, 0, 6 + Math.abs(k));
                    z.moveTo(at.x, p.getY(), at.z, 180, 0); z.setNoAi(true);
                    z.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000); z.setHealth(1000);
                    p.serverLevel().addFreshEntity(z);
                }
                TwinsFx.root(p).putLong("combat", p.serverLevel().getGameTime());
                TwinsFx.sync(p, false);
            }
            case "swap" -> { faceForward(p); TwinsFx.testCooldowns(p); TwinsFx.testRes(p, 100); TwinsFx.swap(p); }
            case "full" -> { TwinsFx.testSockets(p, 7, 7); TwinsFx.testLevels(p, 50, 50); TwinsFx.testActive(p, TwinsFx.SOL); TwinsFx.sync(p, false); }
            case "wearnyx" -> { TwinsFx.testActive(p, TwinsFx.NYX); TwinsFx.testCooldowns(p); TwinsFx.sync(p, false); }
            case "strike" -> { faceForward(p); TwinsArts.twinStrike(p, 10f); }
            case "eclipse" -> { faceForward(p); TwinsFx.testLevels(p, 50, 50); TwinsFx.testTrialWon(p); TwinsFx.testSockets(p, 7, 7); TwinsArts.eclipseUnion(p, 10f); }
            case "noeclipse" -> { TwinsFx.testEndEclipse(p); TwinsFx.sync(p, false); }
            case "trial" -> { faceForward(p); TwinsFx.testLevels(p, 40, 40); TwinsFx.handle(p, 6, 0); }
            case "page" -> TwinsFx.theMasks(p, 10f);
            case "echo" -> { faceForward(p); TwinsFx.testCombat(p); TwinsFx.testEcho(p); }
            case "noon" -> { faceForward(p); TwinsFx.testLevels(p, 50, 50); TwinsFx.testActive(p, TwinsFx.SOL); TwinsFx.highNoon(p, 10f); }
            case "eventide" -> { faceForward(p); TwinsFx.testLevels(p, 50, 50); TwinsFx.testActive(p, TwinsFx.NYX); TwinsFx.eventide(p, 10f); }
            case "end" -> TwinsFx.testReset(p);
            default -> {}
        }
    }

    /** A young blade: the three base stages grown. */
    private static void root3(ServerPlayer p) { HungerFx.testGrow(p, 1); HungerFx.testGrow(p, 2); HungerFx.testGrow(p, 3); }

    /** The Parasite Grimoire's shots: its arm forms (first and third person), its moves and its pages. */
    private static void parasite(ServerPlayer p, String w) {
        switch (w) {
            case "setup" -> {   // everything evolved, a full gut, empty hands, and a target ahead
                faceForward(p);
                ParasiteFx.testReset(p);
                ParasiteFx.testFill(p);
                ParasiteFx.testUnlockAll(p);
                p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                p.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, ItemStack.EMPTY);
                for (var old : p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.monster.Husk.class, p.getBoundingBox().inflate(40))) old.discard();
                var z = net.minecraft.world.entity.EntityType.HUSK.create(p.serverLevel());
                if (z != null) {
                    var at = p.position().add(0, 0, 6);
                    z.moveTo(at.x, p.getY(), at.z, 180, 0); z.setNoAi(true);
                    z.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000); z.setHealth(1000);
                    p.serverLevel().addFreshEntity(z);
                }
            }
            case "form1", "form2", "form3", "form4", "form5", "form6", "form7" -> { faceForward(p); if (ParasiteFx.bulwark(p)) ParasiteFx.toggleBulwark(p); ParasiteFx.setForm(p, w.charAt(4) - '0', true); }
            case "bulwark" -> { if (ParasiteFx.form(p) != 0) ParasiteFx.setForm(p, 0, true); if (!ParasiteFx.bulwark(p)) ParasiteFx.toggleBulwark(p); }
            case "hide" -> { if (!ParasiteFx.hide(p)) ParasiteFx.toggleHide(p); ParasiteFx.setForm(p, ParasiteFx.TITAN, true); }
            case "swing" -> p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
            case "consume" -> { faceForward(p); ParasiteFx.consume(p, 10f); }
            case "marrow_barrage" -> { faceForward(p); ParasiteFx.testFill(p); ParasiteFx.marrowBarrage(p, 10f); }
            case "grasping_field" -> { faceForward(p); ParasiteFx.testFill(p); ParasiteFx.graspingField(p, 10f); }
            case "spine_storm" -> { faceForward(p); ParasiteFx.testFill(p); ParasiteFx.spineStorm(p, 10f); }
            case "spine_fire" -> { faceForward(p); ParasiteFx.setForm(p, ParasiteFx.SPINE, true); if (ParasiteFx.form(p) != ParasiteFx.SPINE) ParasiteFx.setForm(p, ParasiteFx.SPINE, true); ParasiteFx.handle(p, 4, 0); }
            case "slam" -> { faceForward(p); p.connection.teleport(p.getX(), p.getY() + 6, p.getZ(), 0, 0); AstaFx.later(2, () -> { ParasiteFx.handle(p, 5, 60); p.setDeltaMovement(0, -2, 0); p.hurtMarked = true; }); }
            case "evolution" -> { ParasiteFx.testSpent(p, 6); ParasiteFx.testStrain(p, 0); ParasiteFx.evolution(p, 0); }
            case "evolution_strain" -> { ParasiteFx.testStrain(p, ParasiteFx.HIVE); ParasiteFx.evolution(p, 0); }
            case "frenzy" -> ParasiteFx.testFrenzy(p);
            case "awaken" -> {   // everything evolved, every mutation found (three worn), the Brute strain awakened
                ParasiteFx.testUnlockAll(p); ParasiteFx.testMutations(p); ParasiteFx.testStrain(p, ParasiteFx.BRUTE); ParasiteFx.testFill(p); ParasiteFx.testCooldowns(p);
                for (int m : new int[]{ParasiteFx.TWIN, ParasiteFx.BARBED, ParasiteFx.KEEN}) if (!ParasiteFx.wears(p, m)) ParasiteFx.handle(p, 10, m);
            }
                        case "colossus" -> { ParasiteFx.testStrain(p, ParasiteFx.BRUTE); ParasiteFx.testCooldowns(p); ParasiteMoves.strainArt(p); }
            case "swarm" -> { ParasiteFx.testStrain(p, ParasiteFx.HIVE); ParasiteFx.testCooldowns(p); ParasiteMoves.strainArt(p); }
            case "apex" -> { faceForward(p); ParasiteFx.testStrain(p, ParasiteFx.HUNTER); ParasiteFx.testCooldowns(p); ParasiteMoves.strainArt(p); }
            case "final" -> { faceForward(p); ParasiteFx.testFill(p); ParasiteFx.testCooldowns(p); ParasiteMoves.finalFormStart(p); }
            case "tempest", "impaler" -> { faceForward(p); ParasiteFx.testPath(p, ParasiteFx.U_STORM, w.equals("tempest") ? 1 : 2); ParasiteFx.testFill(p); ParasiteFx.spineStorm(p, 10f); }
            case "maw", "vortex" -> { faceForward(p); ParasiteFx.testPath(p, ParasiteFx.U_FIELD, w.equals("maw") ? 1 : 2); ParasiteFx.testFill(p); ParasiteFx.graspingField(p, 10f); }
            case "seeker", "bomb" -> { faceForward(p); ParasiteFx.testPath(p, ParasiteFx.U_BARRAGE, w.equals("seeker") ? 1 : 2); ParasiteFx.testFill(p); ParasiteFx.marrowBarrage(p, 10f); }
            case "starve" -> ParasiteFx.testStarve(p);
            case "grapple" -> { faceForward(p); ParasiteMoves.grapple(p); }
            case "burrow" -> ParasiteMoves.burrow(p);
            case "collapse" -> { faceForward(p); ParasiteFx.testFill(p); ParasiteMoves.collapseCore(p, 10f); }
            case "pack" -> {}
            case "pack0", "pack1", "pack2", "pack3" -> {
                faceForward(p); ParasiteFx.testFill(p); ParasiteFx.testStrain(p, w.charAt(4) - '0'); ParasiteMoves.packCall(p, 0f);
                for (int k : new int[]{1, 10, 20}) AstaFx.later(k, () -> com.mojang.logging.LogUtils.getLogger().info("UISHOTS pack: {} brutes near after {} ticks (biomass {})",
                        p.serverLevel().getEntitiesOfClass(com.ironspellgrimoire.entity.InfectedBrute.class, p.getBoundingBox().inflate(16)).size(), k, ParasiteFx.biomass(p)));
            }
            case "sight" -> { ParasiteFx.testUnlockAll(p); ParasiteFx.testFill(p); if (!ParasiteFx.sight(p)) ParasiteFx.handle(p, 27, 0); }
            case "sight_off" -> { if (ParasiteFx.sight(p)) ParasiteFx.handle(p, 27, 0); }
            case "molt" -> { faceForward(p); ParasiteFx.testUnlockAll(p); p.setHealth(p.getMaxHealth()); p.invulnerableTime = 0; p.hurt(p.damageSources().magic(), p.getMaxHealth() * 0.8f); }
            case "network" -> {   // two nests: one a little way off, one here - then the travel menu
                ParasiteFx.testUnlockAll(p); ParasiteFx.testFill(p);
                p.connection.teleport(p.getX() + 6, p.getY(), p.getZ(), p.getYRot(), p.getXRot());
                AstaFx.later(4, () -> { ParasiteFx.handle(p, 19, 0); p.connection.teleport(p.getX() - 6, p.getY(), p.getZ(), p.getYRot(), p.getXRot());
                    AstaFx.later(4, () -> { ParasiteFx.testFill(p); ParasiteFx.handle(p, 19, 0); AstaFx.later(4, () -> ParasiteNest.sendNests(p)); }); });
            }
            case "look1", "look2", "look3" -> { ParasiteFx.testUnlockAll(p); ParasiteFx.testFill(p); if (!ParasiteFx.hide(p)) ParasiteFx.toggleHide(p); ParasiteFx.handle(p, 29, w.charAt(4) - '0'); }
            case "nest" -> { faceForward(p); ParasiteFx.testFill(p); ParasiteFx.handle(p, 19, 0); p.connection.teleport(p.getX() - 3, p.getY(), p.getZ() - 3, -45, 20); }
            case "worn" -> { ParasiteFx.testUnlockAll(p); if (!ParasiteFx.bulwark(p)) ParasiteFx.toggleBulwark(p); ParasiteFx.root(p).putFloat("limb", 18f); ParasiteFx.testEaten(p, 1600); }
            case "end" -> { ParasiteFx.testReset(p); }
            default -> {}
        }
    }

    /** One Blood spell, cast where the player looks. */
    public static void bloodSpell(ServerPlayer p, String which) {
        switch (which) {
            case "thorned_veins" -> BloodFx.thornedVeins(p, 10f);
            case "red_tide" -> BloodFx.redTide(p, 10f);
            case "blood_pact" -> BloodFx.bloodPact(p, 10f);
            case "gauge" -> {
                ItemStack book = new ItemStack(AddonItemRegistry.BLOOD_GRIMOIRE.get());
                CuriosApi.getCuriosInventory(p).ifPresent(h -> h.setEquippedCurio("spellbook", 0, book));
                BloodFx.showGauge(p, 68f);
            }
            case "moonfall" -> { p.setXRot(5); BloodFx.scarletMoon(p, 10f); }
            case "overflow" -> BloodFx.showGauge(p, 132f);
            case "throne" -> IceFx.iceThrone(p, 10f);
            case "hemorrhage" -> BloodFx.hemorrhage(p, 10f);
            case "sanguine_volley" -> BloodFx.sanguineVolley(p, 10f);
            case "crimson_bulwark" -> BloodFx.crimsonBulwark(p, 10f);
            case "prison" -> {   // a still zombie straight ahead to hold
                var look = p.getLookAngle().multiply(1, 0, 1).normalize();
                var z = net.minecraft.world.entity.EntityType.HUSK.create(p.serverLevel());
                if (z != null) {
                    var at = p.position().add(look.scale(6));
                    z.moveTo(at.x, p.getY(), at.z, 0, 0); z.setNoAi(true);
                    p.serverLevel().addFreshEntity(z);
                    p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, z.position().add(0, 1, 0));
                }
                BloodFx.transfusion(p, 10f);
            }
            case "master" -> {   // the equipped Blood Grimoire mastered, with a path or two chosen (the book pictures)
                ItemStack bk = io.redspace.ironsspellbooks.api.util.Utils.getPlayerSpellbookStack(p);
                if (bk == null) return;
                net.minecraft.nbt.CompoundTag mt = new net.minecraft.nbt.CompoundTag(); mt.putInt("xp", 10_000_000);
                bk.getOrCreateTag().put(GrimoireMastery.masteryTag(), mt);
                BloodPaths.choose(p, "crimson_edge", 1);
                BloodFx.setMoonCount(p, 25);   // and an awakened Moon for the mode pictures
            }
            case "exsanguinate" -> {   // two still zombies to draw from
                p.server.setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);
                var look = p.getLookAngle().multiply(1, 0, 1).normalize();
                var right = new net.minecraft.world.phys.Vec3(-look.z, 0, look.x);
                for (int i = -1; i <= 1; i += 2) {
                    var z = net.minecraft.world.entity.EntityType.ZOMBIE.create(p.serverLevel());
                    if (z == null) continue;
                    var at = p.position().add(look.scale(3.5)).add(right.scale(i * 1.6));
                    z.moveTo(at.x, p.getY(), at.z, 0, 0);
                    z.setNoAi(true);
                    z.setHealth(z.getMaxHealth());
                    p.serverLevel().addFreshEntity(z);
                }
                BloodFx.exsanguinate(p, 4f);
            }
            default -> BloodFx.crimsonEdge(p, 10f);
        }
    }

    /** Dante, standing four blocks in front of the camera (facing it, or turned away), for a look at his cloak. */
    public static void dante(ServerPlayer p, boolean back, boolean flare) {
        p.server.setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);   // Peaceful would delete him at once
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

    /**
     * The rivals' pictures: all five (and a Legionnaire) in a row in front of the camera - facing it, turned away, or
     * mid-fight (Seren's parry, Brannoch's horn, Alder kneeling as he yields, Wren casting, Mira foreclosing, the
     * standard-bearer) - or cleared away.
     */
    public static void rivals(ServerPlayer p, String what) {
        p.server.setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);   // Peaceful would delete them at once
        var sl = p.serverLevel();
        for (var e : sl.getEntitiesOfClass(com.ironspellgrimoire.entity.Rival.class, p.getBoundingBox().inflate(40))) e.discard();
        for (var e : sl.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, p.getBoundingBox().inflate(40))) e.discard();   // and whatever earlier pictures left
        if (what.equals("clear")) return;
        boolean back = what.equals("back"), act = what.equals("action");
        java.util.List<net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.Mob>> types = java.util.List.of(
                com.ironspellgrimoire.registry.AddonEntities.SEREN_VALE.get(), com.ironspellgrimoire.registry.AddonEntities.BRANNOCH_TOR.get(),
                com.ironspellgrimoire.registry.AddonEntities.TOR_LEGIONNAIRE.get(), com.ironspellgrimoire.registry.AddonEntities.ALDER_BELLMAR.get(),
                com.ironspellgrimoire.registry.AddonEntities.WREN_BELLMAR.get(), com.ironspellgrimoire.registry.AddonEntities.MIRA_HOLLOWELL.get());
        var look = p.getLookAngle().multiply(1, 0, 1).normalize();
        var side = new net.minecraft.world.phys.Vec3(-look.z, 0, look.x);
        float face = (float) Math.toDegrees(Math.atan2(look.x, -look.z)) + (back ? 180 : 0);
        for (int i = 0; i < types.size(); i++) {
            var m = types.get(i).create(sl);
            if (m == null) continue;
            var at = p.position().add(look.scale(6.5)).add(side.scale((i - 2.5) * 2.1));
            m.moveTo(at.x, p.getY(), at.z, face, 0);
            m.setYHeadRot(face); m.yBodyRot = face; m.yBodyRotO = face; m.yHeadRotO = face;
            m.setNoAi(true);
            sl.addFreshEntity(m);
            if (!act) continue;
            if (m instanceof com.ironspellgrimoire.entity.SerenVale r) r.setFlag(SerenAI.PARRY);
            if (m instanceof com.ironspellgrimoire.entity.BrannochTor r) r.setPose(BossKit.P_RAISE);
            if (m instanceof com.ironspellgrimoire.entity.TorLegionnaire l) l.setBearer(true);
            if (m instanceof com.ironspellgrimoire.entity.AlderBellmar r) r.setFlag(com.ironspellgrimoire.entity.Rival.YIELDED);
            if (m instanceof com.ironspellgrimoire.entity.WrenBellmar r) r.setPose(BossKit.P_CAST);
            if (m instanceof com.ironspellgrimoire.entity.MiraHollowell r) r.setFlag(MiraAI.FORECLOSE);
        }
    }

    /** The Architect of Ruin's pictures: a fortress well on its way, Siege Mode, the Citadel. */
    static void architect(ServerPlayer p, String w) {
        switch (w) {
            case "setup" -> {   // book level 6 (4x4 plan), eleven rooms with three pairs, a garrison of four, two banners waiting
                faceForward(p);
                FortressFx.testReset(p);
                FortressFx.testLevel(p, 6);
                FortressFx.testUnlockAll(p);
                FortressFx.testStone(p, 100000);
                int[][] plan = {{FortressFx.BARRACKS, 0}, {FortressFx.CHAPEL, 1}, {FortressFx.WATCHTOWER, 2}, {FortressFx.OBSERVATORY, 3},
                        {FortressFx.ARMOURY, 5}, {FortressFx.FORGE, 6}, {FortressFx.ARCANUM, 7}, {FortressFx.GATEHOUSE, 10}, {FortressFx.WAR_ROOM, 11},
                        {FortressFx.WORKSHOP, 12}, {FortressFx.LIBRARY, 15}};
                for (int[] r : plan) FortressFx.handle(p, 1, r[0] * 100 + r[1]);
                for (int k = 0; k < 3; k++) FortressFx.handle(p, 1, FortressFx.BARRACKS * 100);
                for (int k = 0; k < 2; k++) FortressFx.handle(p, 1, FortressFx.FORGE * 100);
                FortressFx.handle(p, 1, FortressFx.WATCHTOWER * 100);
                FortressFx.handle(p, 3, 0 * 100 + FortressFx.FORGE + 1); FortressFx.handle(p, 3, 1 * 100 + FortressFx.ARMOURY + 1);
                FortressFx.handle(p, 3, 2 * 100 + FortressFx.ARCANUM + 1); FortressFx.handle(p, 3, 3 * 100 + FortressFx.CHAPEL + 1);
                for (String b : new String[]{"minecraft:zombie", "minecraft:skeleton", "minecraft:witch", "minecraft:ravager"}) { FortressSiege.testBanner(p, b); FortressFx.handle(p, 4, 0); }
                FortressSiege.testKills(p, 1, 90); FortressSiege.testKills(p, 2, 30); FortressSiege.testKills(p, 4, 210);
                FortressFx.handle(p, 6, 1 * 100 + FortressFx.FORGE + 1);
                FortressSiege.testBanner(p, "minecraft:pillager"); FortressSiege.testBanner(p, "minecraft:spider");
                FortressFx.testRoot(p).putInt("stone", 236);
                FortressFx.testRoot(p).putFloat("xp", FortressFx.need(6) * 0.6f);
                FortressFx.testRoot(p).putBoolean("toured", true);
                FortressFx.testClearCds(p);
                FortressFx.sync(p, false);
            }
            case "siege" -> { FortressFx.testClearCds(p); FortressFx.siegePage(p, 10f); }
            case "breach" -> {   // Hold the Gate, then a wall on the left broken by a foe
                FortressFx.handle(p, 12, FortressSiege.HOLD);
                var wall = FortressSiege.testWallList(p).stream().filter(x -> x.isAlive() && x.kind() == com.ironspellgrimoire.entity.FortressWall.WALL && x.getX() > p.getX() + 3).findFirst().orElse(null);
                var foe = net.minecraft.world.entity.EntityType.ZOMBIE.create(p.serverLevel());
                if (wall != null && foe != null) { foe.moveTo(wall.getX(), wall.getY(), wall.getZ()); wall.hurt(foe.damageSources().mobAttack(foe), 100000); }
            }
            case "line", "horseshoe" -> {
                FortressSiege.endAll(p, false);
                faceForward(p);
                FortressFx.handle(p, 10, w.equals("line") ? FortressFx.LINE : FortressFx.HORSESHOE);
                FortressFx.testClearCds(p);
                FortressFx.siegePage(p, 10f);
            }
            case "citadel" -> { FortressSiege.endAll(p, false); FortressFx.handle(p, 10, FortressFx.RING); FortressFx.testLevel(p, 10); FortressFx.testClearCds(p); FortressFx.citadelPage(p, 10f); }
            case "clear" -> { FortressFx.testReset(p); FortressFx.sync(p, false); }
            case "page" -> FortressFx.sync(p, true);
            default -> {}
        }
    }
}
