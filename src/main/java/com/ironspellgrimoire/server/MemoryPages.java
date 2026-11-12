package com.ironspellgrimoire.server;

import com.ironspellgrimoire.IronSpellGrimoire;
import com.ironspellgrimoire.entity.MemoryGuardian;
import com.ironspellgrimoire.network.AddonNetwork;
import com.ironspellgrimoire.network.FxPacket;
import com.ironspellgrimoire.registry.AddonEntities;
import com.ironspellgrimoire.registry.AddonItemRegistry;
import io.redspace.ironsspellbooks.api.util.Utils;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * Lost Memory Pages: the story of the mage who first bound the Grimoire Tower (server/MemoryStory), torn into pages.
 * <ul>
 *   <li>Every boss has a chance (50%) to drop a torn Memory Page for each mage who helped bring it down - one page of
 *       the chapter you're on, and only one page per kind of boss in each chapter: a chapter's five pages come from
 *       five different bosses. Read a page (use it) and it's written into your grimoire's Memories tab, in ink.</li>
 *   <li>Read all five and the chapter is complete: your Tower Compass comes back, pointing to a hidden place. There the
 *       chapter's Memory Guardian waits (entity/MemoryGuardian, server/MemoryGuardianAI).</li>
 *   <li>Beat it: the chapter's last page, a unique grimoire upgrade (Binder's Quill, Warden's Key, Binder's Pen) and
 *       merit stars. The third and final guardian also gives the Binder's Aura, a cosmetic aura round your grimoire.</li>
 * </ul>
 * Kept on the player: pages read ("isg_mem_read"), pages handed out ("isg_mem_given"), bosses already drawn from per
 * chapter ("isg_mem_b0".."2"), chapters done ("isg_mem_done"), the hidden place ("isg_mem_site") and the aura.
 */
@Mod.EventBusSubscriber(modid = IronSpellGrimoire.MODID)
public final class MemoryPages {
    private MemoryPages() {}

    public static final int SHAPE = 77;
    private static final String READ = "isg_mem_read", GIVEN = "isg_mem_given", DONE = "isg_mem_done", SITE = "isg_mem_site", AURA = "isg_mem_aura";
    private static final String[] ROMAN = {"I", "II", "III"};

    private static List<EntityType<?>> bosses() {
        return List.of(AddonEntities.ZENON.get(), AddonEntities.BEAST_OF_DESPAIR.get(), AddonEntities.RHYA.get(), AddonEntities.LICHT.get(),
                AddonEntities.REVENANT.get(), AddonEntities.CURSE_WITCH.get(), AddonEntities.REFLECTION.get(), AddonEntities.CINDER_SOVEREIGN.get());
    }

    static int get(ServerPlayer p, String k) { return p.getPersistentData().getInt(k); }
    static void set(ServerPlayer p, String k, int v) { p.getPersistentData().putInt(k, v); }

    /** The chapter you're on: the first whose guardian you haven't beaten (3 once the story is done). */
    public static int chapter(ServerPlayer p) {
        int done = get(p, DONE);
        for (int c = 0; c < MemoryStory.CH; c++) if ((done & (1 << c)) == 0) return c;
        return MemoryStory.CH;
    }

    public static ItemStack page(int ch, int page) {
        ItemStack s = new ItemStack(AddonItemRegistry.MEMORY_PAGE.get());
        s.getOrCreateTag().putInt("Chapter", ch);
        s.getOrCreateTag().putInt("Page", page);
        return s;
    }

    // ------------------------------------------------------------------ pages from bosses
    @SubscribeEvent
    public static void onBossDeath(LivingDeathEvent e) {
        LivingEntity b = e.getEntity();
        if (!(b.level() instanceof ServerLevel sl) || Rifts.isShade(b)) return;   // a rift's shade drops no page
        if (b instanceof MemoryGuardian g) { guardianFell(sl, g); return; }
        int idx = bosses().indexOf(b.getType());
        if (idx < 0) return;
        for (ServerPlayer p : sl.players()) {
            if (p.distanceToSqr(b) > 40 * 40 || p.isSpectator()) continue;
            if (p != e.getSource().getEntity() && b.getLastHurtByMob() != p && !(b instanceof com.ironspellgrimoire.entity.AddonBoss ab && p.getUUID().equals(ab.focus))
                    && p.distanceToSqr(b) > 24 * 24) continue;   // you had a hand in it, or were right there
            tryDrop(p, b, idx);
        }
    }

    private static void tryDrop(ServerPlayer p, LivingEntity b, int idx) {
        int ch = chapter(p);
        if (ch >= MemoryStory.CH) return;
        String bk = "isg_mem_b" + ch;
        int used = get(p, bk), given = get(p, GIVEN);
        int next = -1;
        for (int i = 0; i < MemoryStory.BOSS_PAGES; i++) if ((given & MemoryStory.bit(ch, i)) == 0) { next = i; break; }
        if (next >= 0 && (used & (1 << idx)) != 0) return;   // this kind of boss already gave its page of this chapter
        if (next < 0) {   // all five handed out: a page you lost unread can turn up again, from any boss
            int r = get(p, READ);
            for (int i = 0; i < MemoryStory.BOSS_PAGES; i++) if ((r & MemoryStory.bit(ch, i)) == 0) { next = i; break; }
            if (next < 0 || ThreadLocalRandom.current().nextFloat() >= 0.35f) return;
            give(p, page(ch, next));
            p.displayClientMessage(Component.literal("A lost Memory Page turns up again.").withStyle(ChatFormatting.GOLD), true);
            return;
        }
        if (ThreadLocalRandom.current().nextFloat() >= 0.5f) return;
        set(p, bk, used | (1 << idx));
        set(p, GIVEN, given | MemoryStory.bit(ch, next));
        give(p, page(ch, next));
        ServerLevel sl = p.serverLevel();
        Vec3 c = b.position().add(0, 1.5, 0);
        FxSend.beam(sl, Fx.GOLD | FxPacket.KEEP_SHAPE, c, p.position().add(0, 1.2, 0), 0.08f, 14);
        FxSend.ring(sl, Fx.SILVER | FxPacket.KEEP_SHAPE, p.position().add(0, 1.2, 0), 0.2f, 1.3f, 0.04f, 12);
        sl.playSound(null, p.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.2f, 0.7f);
        p.sendSystemMessage(Component.literal("A TORN MEMORY PAGE  ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.literal("drifts down from " + b.getDisplayName().getString() + " - Chapter " + ROMAN[ch] + ", page " + (next + 1) + ". Use it to read it into your grimoire.").withStyle(ChatFormatting.YELLOW)));
    }

    private static void give(ServerPlayer p, ItemStack s) { if (!p.getInventory().add(s)) p.drop(s, false); }

    // ------------------------------------------------------------------ reading
    /** Reading a page (item/MemoryPageItem): it's written into the Memories tab. Returns whether it was new. */
    public static boolean read(ServerPlayer p, int ch, int page) {
        if (ch < 0 || ch >= MemoryStory.CH || page < 0 || page >= MemoryStory.PER) return false;
        int r = get(p, READ), bit = MemoryStory.bit(ch, page);
        ServerLevel sl = p.serverLevel();
        if ((r & bit) != 0) {
            Bounties.addStars(p, 2);
            p.displayClientMessage(Component.literal("You already remember this page. It fades - +2 merit stars.").withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        set(p, READ, r | bit);
        set(p, GIVEN, get(p, GIVEN) | bit);
        sync(p);
        FxSend.ring(sl, Fx.GOLD | FxPacket.KEEP_SHAPE, p.position().add(0, 1.1, 0), 0.2f, 1.6f, 0.05f, 14);
        sl.playSound(null, p.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1f, 1.0f);
        sl.playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8f, 0.7f);
        p.displayClientMessage(Component.literal("Chapter " + ROMAN[ch] + ", page " + (page + 1) + " is written into your grimoire's Memories.").withStyle(ChatFormatting.GOLD), true);
        p.sendSystemMessage(Component.literal("\"" + firstWords(MemoryStory.PAGES[ch][page]) + "...\"").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        p.sendSystemMessage(Component.literal("  (Sneak + use your grimoire, then the Memories ribbon, to read it all.)").withStyle(ChatFormatting.DARK_GRAY));
        checkChapter(p);
        return true;
    }

    private static String firstWords(String s) { int i = s.indexOf(' ', 60); return i < 0 ? s : s.substring(0, i); }

    private static boolean chapterRead(ServerPlayer p, int ch) {
        int r = get(p, READ);
        for (int i = 0; i < MemoryStory.BOSS_PAGES; i++) if ((r & MemoryStory.bit(ch, i)) == 0) return false;
        return true;
    }

    /** Five pages read: the compass comes back, pointing to where the chapter's guardian waits. */
    private static void checkChapter(ServerPlayer p) {
        int ch = chapter(p);
        if (ch >= MemoryStory.CH || !chapterRead(p, ch) || p.getPersistentData().contains(SITE)) return;
        ServerLevel ow = p.server.getLevel(Level.OVERWORLD);
        if (ow == null) return;
        Vec3 from = p.level().dimension().equals(Level.OVERWORLD) ? p.position() : Vec3.atCenterOf(ow.getSharedSpawnPos());
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double a = r.nextDouble(Math.PI * 2), d = r.nextDouble(350, 600) + ch * 100;
        CompoundTag site = new CompoundTag();
        site.putInt("X", (int) (from.x + Math.cos(a) * d));
        site.putInt("Z", (int) (from.z + Math.sin(a) * d));
        site.putInt("Ch", ch);
        p.getPersistentData().put(SITE, site);
        giveCompass(p, site);
        ServerLevel sl = p.serverLevel();
        FxSend.beam(sl, Fx.GOLD | FxPacket.KEEP_SHAPE, p.position(), p.position().add(0, 20, 0), 0.5f, 24);
        FxSend.impactFrame(sl, p.position().add(0, 1.2, 0), 3, FxSend.FRAME_GOLD, 20);
        sl.playSound(null, p.blockPosition(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 1.2f, 0.8f);
        double dx = site.getInt("X") - p.getX(), dz = site.getInt("Z") - p.getZ();
        p.sendSystemMessage(Component.literal("CHAPTER " + ROMAN[ch] + " REMEMBERED  ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.literal("Your Tower Compass returns, its needle turned to somewhere hidden - about " + (int) Math.hypot(dx, dz) + " blocks "
                        + Bounties.direction(dx, dz) + ". A memory is waiting there.").withStyle(ChatFormatting.YELLOW)));
    }

    private static void giveCompass(ServerPlayer p, CompoundTag site) {
        ItemStack c = new ItemStack(AddonItemRegistry.TOWER_COMPASS.get());
        c.getOrCreateTag().putInt("TowerX", site.getInt("X"));
        c.getOrCreateTag().putInt("TowerZ", site.getInt("Z"));
        c.getOrCreateTag().putInt("Memory", site.getInt("Ch") + 1);
        c.setHoverName(Component.literal("Tower Compass - Memory " + ROMAN[site.getInt("Ch")]).withStyle(ChatFormatting.GOLD));
        give(p, c);
    }

    private static boolean hasCompass(ServerPlayer p) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).is(AddonItemRegistry.TOWER_COMPASS.get()) && inv.getItem(i).hasTag() && inv.getItem(i).getTag().contains("Memory")) return true;
        return false;
    }

    private static void dropCompasses(ServerPlayer p) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).is(AddonItemRegistry.TOWER_COMPASS.get()) && inv.getItem(i).hasTag() && inv.getItem(i).getTag().contains("Memory")) inv.setItem(i, ItemStack.EMPTY);
    }

    // ------------------------------------------------------------------ the hidden place, and its guardian
    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % 20 != 13) return;
        if (p.getPersistentData().getBoolean(AURA)) {   // the Binder's Aura goes with whatever grimoire you carry
            ItemStack book = Utils.getPlayerSpellbookStack(p);
            if (book != null && GrimoireMastery.bookOf(book) != null && !book.getOrCreateTag().getBoolean("MemoryAura")) book.getOrCreateTag().putBoolean("MemoryAura", true);
        }
        if (!p.getPersistentData().contains(SITE) || !p.level().dimension().equals(Level.OVERWORLD) || p.isSpectator()) return;
        CompoundTag site = p.getPersistentData().getCompound(SITE);
        double dx = site.getInt("X") + 0.5 - p.getX(), dz = site.getInt("Z") + 0.5 - p.getZ();
        if (dx * dx + dz * dz > 28 * 28) return;
        ServerLevel sl = p.serverLevel();
        for (MemoryGuardian g : sl.getEntitiesOfClass(MemoryGuardian.class, p.getBoundingBox().inflate(80))) if (p.getUUID().equals(g.owner())) return;
        summon(p, sl, site);
    }

    private static void summon(ServerPlayer p, ServerLevel sl, CompoundTag site) {
        int ch = site.getInt("Ch");
        MemoryGuardian g = AddonEntities.MEMORY_GUARDIAN.get().create(sl);
        if (g == null) return;
        Vec3 at = ZenonAI.ground(sl, new Vec3(site.getInt("X") + 0.5, p.getY() + 4, site.getInt("Z") + 0.5));
        if (at.distanceToSqr(p.position()) < 6 * 6) {   // don't appear on top of you
            Vec3 away = FxUtil.flat(at.subtract(p.position()));
            at = ZenonAI.ground(sl, p.position().add((away.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : away.normalize()).scale(9)).add(0, 3, 0));
        }
        g.setup(ch, p.getUUID());
        g.moveTo(at.x, at.y, at.z, (float) Math.toDegrees(Math.atan2(-(p.getX() - at.x), p.getZ() - at.z)), 0);
        // its arrival: pages whirl up out of the ground in a narrowing spiral, and the memory takes shape in the middle
        g.setInvisible(true);
        g.setTransforming(50);
        sl.addFreshEntity(g);
        int pal = MemoryGuardianAI.palette(ch);
        final Vec3 c = at;
        p.displayClientMessage(Component.literal("Pages whirl up out of the ground...").withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC), true);
        sl.playSound(null, at.x, at.y, at.z, SoundEvents.BOOK_PAGE_TURN, SoundSource.HOSTILE, 1.6f, 0.5f);
        for (int s = 0; s < 40; s += 2) {
            final int ss = s;
            AstaFx.later(s, () -> {
                double k = ss / 40.0;
                for (int j = 0; j < 3; j++) {
                    double a = ss * 0.45 + j * Math.PI * 2 / 3, r = 5.5 * (1 - k) + 0.6;
                    Vec3 q = c.add(Math.cos(a) * r, 0.3 + k * 2.6 + j * 0.3, Math.sin(a) * r);
                    FxSend.trail(sl, (j == 0 ? Fx.SILVER : pal) | FxPacket.KEEP_SHAPE, q, q.add(-Math.sin(a) * 0.5, 0.15, Math.cos(a) * 0.5), 0.18f, 8);
                }
                if (ss % 8 == 0) { FxSend.ring(sl, pal | FxPacket.KEEP_SHAPE, c.add(0, 0.1, 0), 5.5f * (float) (1 - k) + 0.5f, 5.5f * (float) (1 - k), 0.06f, 8); sl.playSound(null, c.x, c.y, c.z, SoundEvents.BOOK_PAGE_TURN, SoundSource.HOSTILE, 0.8f, 0.6f + (float) k); }
            });
        }
        AstaFx.later(42, () -> {
            if (!g.isAlive()) return;
            g.setInvisible(false);
            for (int i = 0; i < 3; i++) FxSend.ring(sl, pal | FxPacket.KEEP_SHAPE, c.add(0, 0.1 + i * 0.9, 0), 0.4f, 3.5f - i, 0.08f, 24);
            FxSend.beam(sl, pal | FxPacket.KEEP_SHAPE, c, c.add(0, 22, 0), 0.9f, 26);
            FxSend.impactFrame(sl, c.add(0, 1.2, 0), 3, FxSend.FRAME_GOLD, 30);
            sl.playSound(null, c.x, c.y, c.z, SoundEvents.BELL_RESONATE, SoundSource.HOSTILE, 1.2f, 0.6f);
        });
    }

    /** A guardian falls: its owner is rewarded (whoever struck the last blow). */
    private static void guardianFell(ServerLevel sl, MemoryGuardian g) {
        if (g.owner() == null || !(sl.getPlayerByUUID(g.owner()) instanceof ServerPlayer p)) return;
        int ch = g.chapter();
        if ((get(p, DONE) & (1 << ch)) != 0) return;
        set(p, DONE, get(p, DONE) | (1 << ch));
        set(p, GIVEN, get(p, GIVEN) | MemoryStory.bit(ch, MemoryStory.PER - 1));
        p.getPersistentData().remove(SITE);
        dropCompasses(p);
        give(p, page(ch, MemoryStory.PER - 1));
        give(p, new ItemStack(switch (ch) { case 0 -> AddonItemRegistry.BINDERS_QUILL.get(); case 1 -> AddonItemRegistry.WARDENS_KEY.get(); default -> AddonItemRegistry.BINDERS_PEN.get(); }));
        Bounties.addStars(p, 15 + ch * 10);
        p.giveExperiencePoints(300 + ch * 200);
        p.sendSystemMessage(Component.literal("MEMORY RECOVERED  ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.literal(MemoryStory.GUARDIANS[ch] + " fades into a last page. +" + (15 + ch * 10) + " merit stars.").withStyle(ChatFormatting.YELLOW)));
        if (ch == MemoryStory.CH - 1) {
            p.getPersistentData().putBoolean(AURA, true);
            p.sendSystemMessage(Component.literal("THE BINDER'S AURA  ").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD)
                    .append(Component.literal("Ink and gold now turn round your grimoire, whichever you carry. The story is complete.").withStyle(ChatFormatting.GOLD)));
        } else p.sendSystemMessage(Component.literal("  Chapter " + ROMAN[ch + 1] + " begins: its pages are with the great foes again.").withStyle(ChatFormatting.GRAY));
        sync(p);
    }

    // ------------------------------------------------------------------ fallen to a guardian: a way back
    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !p.getPersistentData().contains(SITE)) return;
        CompoundTag site = p.getPersistentData().getCompound(SITE);
        double dx = site.getInt("X") - p.getX(), dz = site.getInt("Z") - p.getZ();
        if (dx * dx + dz * dz < 100 * 100) p.getPersistentData().putLong("isg_mem_retry", p.serverLevel().getGameTime());
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || e.isEndConquered()) return;
        long at = p.getPersistentData().getLong("isg_mem_retry");
        if (at == 0 || p.serverLevel().getGameTime() - at > 6000) return;
        p.sendSystemMessage(Component.literal("The memory still waits. ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal("[Return to it]").withStyle(s -> s.withColor(ChatFormatting.YELLOW).withUnderlined(true)
                        .withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, "/memory return"))))
                .append(Component.literal("  (within five minutes)").withStyle(ChatFormatting.GRAY)));
    }

    // ------------------------------------------------------------------ the client's copy, and /memory
    public static void sync(ServerPlayer p) {
        FxPacket k = new FxPacket();
        k.shape = SHAPE; k.id = p.getId(); k.sweep = get(p, READ);
        k.h = get(p, DONE);
        k.a0 = chapter(p);
        AddonNetwork.send(PacketDistributor.PLAYER.with(() -> p), k);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) { if (e.getEntity() instanceof ServerPlayer p) sync(p); }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("memory").executes(c -> {
            ServerPlayer p = c.getSource().getPlayerOrException();
            int ch = chapter(p);
            if (ch >= MemoryStory.CH) { p.sendSystemMessage(Component.literal("Every memory is recovered.").withStyle(ChatFormatting.GOLD)); return 1; }
            int r = get(p, READ), n = 0;
            for (int i = 0; i < MemoryStory.BOSS_PAGES; i++) if ((r & MemoryStory.bit(ch, i)) != 0) n++;
            p.sendSystemMessage(Component.literal("Chapter " + MemoryStory.CHAPTERS[ch] + ": " + n + "/" + MemoryStory.BOSS_PAGES + " pages read.").withStyle(ChatFormatting.GOLD));
            if (p.getPersistentData().contains(SITE)) {
                CompoundTag site = p.getPersistentData().getCompound(SITE);
                if (!hasCompass(p)) { giveCompass(p, site); p.sendSystemMessage(Component.literal("  Your Tower Compass finds its way back to you.").withStyle(ChatFormatting.GRAY)); }
                else p.sendSystemMessage(Component.literal("  " + MemoryStory.GUARDIANS[ch] + " waits where your compass points.").withStyle(ChatFormatting.GRAY));
            } else {
                int used = get(p, "isg_mem_b" + ch);
                StringBuilder left = new StringBuilder();
                List<EntityType<?>> bs = bosses();
                for (int i = 0; i < bs.size(); i++) if ((used & (1 << i)) == 0) left.append(left.length() > 0 ? ", " : "").append(bs.get(i).getDescription().getString());
                p.sendSystemMessage(Component.literal("  Bosses still holding a page of it: " + left).withStyle(ChatFormatting.GRAY));
            }
            return 1;
        }).then(Commands.literal("return").executes(c -> {   // back to the guardian's place, once, within five minutes of falling there
            ServerPlayer p = c.getSource().getPlayerOrException();
            long at = p.getPersistentData().getLong("isg_mem_retry");
            if (at == 0 || p.serverLevel().getGameTime() - at > 6000 || !p.getPersistentData().contains(SITE)) {
                c.getSource().sendFailure(Component.literal("There's no memory to return to now."));
                return 0;
            }
            p.getPersistentData().remove("isg_mem_retry");
            CompoundTag site = p.getPersistentData().getCompound(SITE);
            ServerLevel ow = p.server.getLevel(Level.OVERWORLD);
            if (ow == null) return 0;
            ow.getChunk(site.getInt("X") >> 4, site.getInt("Z") >> 4);
            Vec3 to = ZenonAI.ground(ow, new Vec3(site.getInt("X") + 12.5, ow.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, site.getInt("X") + 12, site.getInt("Z")) + 2, site.getInt("Z") + 0.5));
            p.teleportTo(ow, to.x, to.y, to.z, p.getYRot(), p.getXRot());
            FxSend.ring(ow, Fx.GOLD | FxPacket.KEEP_SHAPE, to.add(0, 0.1, 0), 0.3f, 2.5f, 0.08f, 16);
            return 1;
        })).then(Commands.literal("give").requires(s -> s.hasPermission(2)).then(Commands.argument("chapter", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 3))
                .executes(c -> {   // ops, for testing: the chapter's five boss pages
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    int ch = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "chapter") - 1;
                    for (int i = 0; i < MemoryStory.BOSS_PAGES; i++) give(p, page(ch, i));
                    return 1;
                }))));
    }
}
