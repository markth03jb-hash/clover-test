package com.ironspellgrimoire.server;

import static com.ironspellgrimoire.server.FxUtil.*;

import com.ironspellgrimoire.network.FxPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * The Parasite Grimoire's guidance and capstone ({@link ParasiteFx} holds the strain itself):
 * <ul>
 *   <li>Lessons: the parasite explains each control once, the first time it becomes possible (the Guide tab in
 *       Evolution lists them all).</li>
 *   <li>The quick ring: four forms pinned for a tap of G (the full wheel with sneak + G).</li>
 *   <li>Each form's feel: its own burst as it grows, its own mark on every hit.</li>
 *   <li>The Apex Mutation, chosen once after the Sovereign rank: Hydra Heart (cheat death once every 10 minutes),
 *       Living Armory (the last form moves to your other arm when you take a new one - both drawn, both count) or
 *       Devourer's Maw (every devour steals a small strength from what was eaten).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = "iron_spell_grimoire")
public final class ParasiteExtras {
    private ParasiteExtras() {}

    public static final String[] APEX = {"", "Hydra Heart", "Living Armory", "Devourer's Maw"};
    public static final String[] APEX_DESC = {"",
            "Cheat death: once every 10 minutes a killing blow leaves you at 40% health.",
            "Two forms at once: taking a new form moves the last one to your other arm - both drawn, half its strength added.",
            "Every devour steals a small strength from the prey's kind for 30 s."};
    static final UUID STOLEN = UUID.fromString("e5f3b2c1-7d40-4a3b-8e62-8f1d50000080");

    // ------------------------------------------------------------------ lessons
    static void teach(ServerPlayer p, int bit, String line, String how) {
        CompoundTag r = ParasiteFx.root(p);
        if ((r.getInt("taught") & (1 << bit)) != 0) return;
        r.putInt("taught", r.getInt("taught") | (1 << bit));
        p.sendSystemMessage(Component.literal("The parasite: ").withStyle(ChatFormatting.DARK_RED)
                .append(Component.literal(line).withStyle(ChatFormatting.RED, ChatFormatting.ITALIC))
                .append(Component.literal("  [" + how + "]").withStyle(ChatFormatting.GRAY)));
    }

    /** What's newly possible, taught once (every two seconds while the book is worn). */
    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % 40 != 0 || !ParasiteFx.equipped(p)) return;
        teach(p, 0, "We are one body now.", "G: the form wheel (sneak + G: all slices; tap G: your 4 pinned forms) - Evolution page: spend EP");
        if (ParasiteFx.root(p).getInt("devoured") > 0 || ParasiteFx.rank(p, 0) > 0) teach(p, 1, "Eat. Grow.", "kills feed biomass; Consume devours a weakened foe whole for EP");
        if (ParasiteFx.rank(p, ParasiteFx.LEAP) > 0 || ParasiteFx.rank(p, ParasiteFx.WALL) > 0) teach(p, 2, "Move like we hunt.", "Wall Runner: forward into a wall mid-air - Glider: hold jump falling - double-tap a direction mid-air: Air Dash - sneak high up: Meteor Fall");
        if (ParasiteFx.rank(p, ParasiteFx.F_BULWARK) > 0 || ParasiteFx.rank(p, 14) > 0) teach(p, 3, "A shield of bone.", "with the Bulwark or Battering Ram out, right-click just before a blow lands to parry it");
        if (ParasiteFx.rank(p, 18) > 0) teach(p, 4, "Reach out.", "Z: Tendril Grapple");
        if (ParasiteFx.rank(p, 19) > 0) teach(p, 5, "Under the ground.", "sneak + Z on the ground: Burrow; Z again to erupt");
        if (ParasiteFx.rank(p, 20) > 0) teach(p, 6, "Hold on.", "sneak against a wall mid-air: cling; jump: pounce where you look");
        if (ParasiteFx.rank(p, 22) > 0) teach(p, 7, "I can smell them through the walls.", "B: Predator Sight");
        if (ParasiteFx.rank(p, 23) > 0) teach(p, 8, "Old skin.", "Molting: below 30% health you shed a husk that draws foes off you");
        if (ParasiteFx.strain(p) != 0) teach(p, 9, "We have a nature now.", "Strain tab: organs, a nest, a second strain");
        if (ParasiteFx.awakened(p)) teach(p, 10, "Awake.", "the wheel's Strain Art and Final Form slices");
        if (ParasiteFx.evoRank(p) >= ParasiteFx.RANKS.length - 1 && apex(p) == 0) teach(p, 11, "Nothing left to become... but one thing.", "Strain tab: choose your Apex Mutation, for good");
    }

    // ------------------------------------------------------------------ the quick ring
    public static int[] pins(ServerPlayer p) {
        int[] a = ParasiteFx.root(p).getIntArray("pins");
        return a.length == 4 ? a : new int[]{1, 2, 3, 4};
    }

    /** Pin a form (0 to clear) in one of the quick ring's four places. */
    static void pin(ServerPlayer p, int slot, int form) {
        if (slot < 0 || slot > 3 || form < 0 || form > ParasiteFx.LAST_FORM) return;
        int[] a = pins(p).clone();
        for (int i = 0; i < 4; i++) if (a[i] == form && form != 0) a[i] = a[slot];
        a[slot] = form;
        ParasiteFx.root(p).putIntArray("pins", a);
    }

    // ------------------------------------------------------------------ each form's feel
    static final int RED = Fx.CRIMSON;

    /** The arm becomes a form: its own burst. */
    static void transformFx(ServerPlayer p, int f) {
        ServerLevel sl = p.serverLevel();
        Vec3 h = ParasiteFx.handPos(p), d = flat(p.getLookAngle());
        switch (f) {
            case ParasiteFx.REAPER -> FxSend.crescent(sl, RED | FxPacket.KEEP_SHAPE, h, d, new Vec3(0, 1, 0), 0.5f, 1.1f, 6, 0.6f);
            case ParasiteFx.TALONS -> { for (int k = -1; k <= 1; k++) FxSend.trail(sl, RED | FxPacket.KEEP_SHAPE, h.add(rightOf(d).scale(0.15 * k)), h.add(d.scale(0.9)).add(rightOf(d).scale(0.25 * k)), 0.06f, 6); }
            case ParasiteFx.SPINE -> { for (int k = 0; k < 6; k++) FxSend.beam(sl, Fx.SILVER | FxPacket.KEEP_SHAPE, h, h.add(rotY(d, k * Math.PI / 3).scale(0.8).add(0, 0.2, 0)), 0.05f, 6); }
            case ParasiteFx.TITAN -> { FxSend.ring(sl, RED | FxPacket.KEEP_SHAPE, p.position().add(0, 0.05, 0), 0.3f, 2.0f, 0.1f, 8); sound(sl, p.position(), SoundEvents.IRON_GOLEM_STEP, 1f, 0.6f); }
            case ParasiteFx.LANCE -> FxSend.beam(sl, Fx.SILVER | FxPacket.KEEP_SHAPE, h, h.add(d.scale(2.2)), 0.12f, 6);
            case ParasiteFx.WHIP -> FxSend.trail(sl, RED | FxPacket.KEEP_SHAPE, h, h.add(d.scale(2.5)).add(0, 0.6, 0), 0.1f, 7);
            case ParasiteFx.RAM -> { FxSend.ring(sl, Fx.SILVER | FxPacket.KEEP_SHAPE, h, 0.1f, 1.0f, 0.1f, 6); sound(sl, p.position(), SoundEvents.ANVIL_PLACE, 0.4f, 0.6f); }
            default -> FxSend.ring(sl, RED | FxPacket.KEEP_SHAPE, h, 0.1f, 0.7f, 0.06f, 6);
        }
    }

    /** Every blow with a form leaves that form's mark. */
    @SubscribeEvent
    public static void onHit(LivingHurtEvent e) {
        if (!(e.getSource().getEntity() instanceof ServerPlayer p) || e.getSource().getDirectEntity() != p || !e.getSource().is(DamageTypes.PLAYER_ATTACK)
                || !ParasiteFx.equipped(p) || !p.getMainHandItem().isEmpty()) return;
        int f = ParasiteFx.form(p);
        if (f == ParasiteFx.NONE) return;
        LivingEntity t = e.getEntity();
        ServerLevel sl = p.serverLevel();
        Vec3 c = mid(t), d = flat(c.subtract(p.position()));
        switch (f) {
            case ParasiteFx.REAPER -> FxSend.crescent(sl, RED | FxPacket.KEEP_SHAPE, c, d, new Vec3(0, 1, 0), 0.9f, 1.3f, 5, 0.6f);
            case ParasiteFx.TALONS -> { for (int k = -1; k <= 1; k++) FxSend.slash(sl, RED | FxPacket.KEEP_SHAPE, c.add(rightOf(d).scale(0.18 * k)), d, new Vec3(0.3, 1, 0), 0.4f, 0.6f, 4, 0.7f); }
            case ParasiteFx.TITAN -> { FxSend.ring(sl, RED | FxPacket.KEEP_SHAPE, t.position().add(0, 0.05, 0), 0.2f, 1.8f, 0.1f, 6); Fx.impact(sl, RED, c, 1.2f); }
            case ParasiteFx.LANCE -> FxSend.beam(sl, Fx.SILVER | FxPacket.KEEP_SHAPE, c.subtract(d.scale(0.6)), c.add(d.scale(1.4)), 0.07f, 5);
            case ParasiteFx.WHIP -> FxSend.trail(sl, RED | FxPacket.KEEP_SHAPE, ParasiteFx.handPos(p), c, 0.08f, 6);
            case ParasiteFx.RAM -> { FxSend.ring(sl, Fx.SILVER | FxPacket.KEEP_SHAPE, c, 0.1f, 1.2f, 0.12f, 5); Fx.impact(sl, Fx.SILVER, c, 1.0f); }
            default -> Fx.spark(sl, RED, c, 0.8f);
        }
    }

    // ------------------------------------------------------------------ the Apex Mutation
    public static int apex(ServerPlayer p) { return ParasiteFx.root(p).getInt("apex"); }

    static void chooseApex(ServerPlayer p, int which) {
        if (which < 1 || which > 3 || apex(p) != 0) return;
        if (ParasiteFx.evoRank(p) < ParasiteFx.RANKS.length - 1) { p.displayClientMessage(Component.literal("Reach the Sovereign rank first").withStyle(ChatFormatting.GRAY), true); return; }
        ParasiteFx.root(p).putInt("apex", which);
        ServerLevel sl = p.serverLevel();
        FxSend.ring(sl, RED | FxPacket.KEEP_SHAPE, p.position().add(0, 0.05, 0), 0.4f, 6f, 0.3f, 18);
        FxSend.beam(sl, RED | FxPacket.KEEP_SHAPE, p.position(), p.position().add(0, 7, 0), 0.7f, 16);
        sound(sl, p.position(), SoundEvents.WARDEN_ROAR, 0.6f, 1.3f);
        p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(5, 50, 15));
        p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(Component.literal(APEX[which].toUpperCase()).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)));
        p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(Component.literal("Apex Mutation").withStyle(ChatFormatting.GRAY)));
    }

    /** Hydra Heart: a killing blow leaves you at 40% instead - once every 10 minutes. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onOwnDeath(LivingDeathEvent e) {
        if (e.isCanceled() || !(e.getEntity() instanceof ServerPlayer p) || !ParasiteFx.equipped(p) || apex(p) != 1) return;
        CompoundTag r = ParasiteFx.root(p);
        long now = p.serverLevel().getGameTime();
        if (now < r.getLong("hydraCd")) return;
        e.setCanceled(true);
        r.putLong("hydraCd", now + 12000);
        p.setHealth(p.getMaxHealth() * 0.4f);
        ServerLevel sl = p.serverLevel();
        for (int k = 0; k < 3; k++) FxSend.ring(sl, RED | FxPacket.KEEP_SHAPE, p.position().add(0, 0.5 + k * 0.6, 0), 0.3f, 2.5f + k, 0.12f, 14);
        sound(sl, p.position(), SoundEvents.TOTEM_USE, 0.7f, 0.5f);
        p.displayClientMessage(Component.literal("Hydra Heart - another heart takes the blow").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), true);
    }

    /** Devourer's Maw: what was eaten lends a strength of its kind for 30 s. */
    static void onDevour(ServerPlayer p, LivingEntity t) {
        if (apex(p) != 3) return;
        MobType mt = t.getMobType();
        int kind = mt == MobType.UNDEAD ? 0 : mt == MobType.ARTHROPOD ? 1 : mt == MobType.ILLAGER ? 2 : mt == MobType.WATER ? 3 : 4;
        String[] what = {"undead hide: +4 armour", "skittering legs: +12% speed", "a raider's arm: +12% damage", "gills: +12% attack speed", "a beast's heart: +4 max health"};
        switch (kind) {
            case 0 -> TimedModifiers.apply(p, Attributes.ARMOR, STOLEN, "Devourer's Maw", 4, AttributeModifier.Operation.ADDITION, 600);
            case 1 -> TimedModifiers.apply(p, Attributes.MOVEMENT_SPEED, STOLEN, "Devourer's Maw", 0.12, AttributeModifier.Operation.MULTIPLY_TOTAL, 600);
            case 2 -> TimedModifiers.apply(p, Attributes.ATTACK_DAMAGE, STOLEN, "Devourer's Maw", 0.12, AttributeModifier.Operation.MULTIPLY_TOTAL, 600);
            case 3 -> TimedModifiers.apply(p, Attributes.ATTACK_SPEED, STOLEN, "Devourer's Maw", 0.12, AttributeModifier.Operation.MULTIPLY_TOTAL, 600);
            default -> TimedModifiers.apply(p, Attributes.MAX_HEALTH, STOLEN, "Devourer's Maw", 4, AttributeModifier.Operation.ADDITION, 600);
        }
        p.displayClientMessage(Component.literal("Devourer's Maw - stolen: " + what[kind]).withStyle(ChatFormatting.RED), true);
        Fx.spark(p.serverLevel(), RED, p.getEyePosition(), 1f);
    }

    /** Living Armory: the form on the other arm (0 none). */
    public static int form2(ServerPlayer p) { return apex(p) == 2 ? ParasiteFx.root(p).getInt("form2") : 0; }

    // ------------------------------------------------------------------ tests and screenshots
    public static void testApex(ServerPlayer p, int which) { ParasiteFx.root(p).putInt("apex", which); }
    public static void testForm2(ServerPlayer p, int f) { ParasiteFx.root(p).putInt("form2", f); }
    public static int testTaught(ServerPlayer p) { return ParasiteFx.root(p).getInt("taught"); }
}
