package com.ironspellgrimoire.server;

import com.ironspellgrimoire.IronSpellGrimoire;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The Emberbrand (ASHFALL): the shard of the Cinder Sovereign in your arm, woken stage by stage as the story goes on
 * (Story keeps each mage's stage). Press X to let it burn.
 * <ul>
 *   <li><b>Brand I - Ember Surge</b> (Arc I): for 12 s your spells hit 20% harder, you move 10% faster and your
 *       melee blows burn for 3 more. Then it sleeps for 90 s.</li>
 * </ul>
 * The later stages (and what they cost) come with the later arcs.
 */
@Mod.EventBusSubscriber(modid = IronSpellGrimoire.MODID)
public final class Brand {
    private Brand() {}

    public static final String[] NAMES = {"", "Ember Surge", "Ember Surge", "Ember Surge", "Ember Surge"};
    public static final int LASTS = 240, SLEEPS = 1800;
    static final UUID POWER = UUID.fromString("b4a2d000-0000-4000-8000-000000000001"), SPEED = UUID.fromString("b4a2d000-0000-4000-8000-000000000002");
    /** When each mage's Brand burns until, and sleeps until (game time). */
    static final Map<UUID, long[]> STATE = new HashMap<>();

    static long[] st(ServerPlayer p) { return STATE.computeIfAbsent(p.getUUID(), k -> new long[2]); }
    public static boolean burning(ServerPlayer p) { return p.serverLevel().getGameTime() < st(p)[0]; }

    /** [ticks until it can burn again, ticks it still burns] for the HUD. */
    static int[] state(ServerPlayer p) {
        long now = p.serverLevel().getGameTime();
        long[] s = st(p);
        return new int[]{(int) Math.max(0, s[1] - now), (int) Math.max(0, s[0] - now)};
    }

    public static void use(ServerPlayer p) {
        int stage = Story.brand(p);
        if (stage <= 0) { p.displayClientMessage(Component.literal("Your arm aches where the brand is. It isn't awake yet.").withStyle(ChatFormatting.GRAY), true); return; }
        long now = p.serverLevel().getGameTime();
        long[] s = st(p);
        if (now < s[1]) { p.displayClientMessage(Component.literal("The Brand is spent. " + (s[1] - now + 19) / 20 + " s.").withStyle(ChatFormatting.GRAY), true); return; }
        s[0] = now + LASTS; s[1] = now + SLEEPS;
        double power = 0.2 + 0.1 * (stage - 1);
        TimedModifiers.apply(p, AttributeRegistry.SPELL_POWER.get(), POWER, "Ember Surge", power, AttributeModifier.Operation.MULTIPLY_BASE, LASTS);
        TimedModifiers.apply(p, Attributes.MOVEMENT_SPEED, SPEED, "Ember Surge", 0.1, AttributeModifier.Operation.MULTIPLY_TOTAL, LASTS);
        var sl = p.serverLevel();
        FxSend.ring(sl, Fx.FIRE, p.position().add(0, 0.1, 0), 0.4f, 3.2f, 0.1f, 14);
        FxSend.ring(sl, Fx.FUEGO, p.position().add(0, 1.1, 0), 0.2f, 1.4f, 0.06f, 10);
        sl.playSound(null, p.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1f, 0.6f);
        sl.playSound(null, p.blockPosition(), SoundEvents.BLAZE_AMBIENT, SoundSource.PLAYERS, 0.6f, 0.5f);
        p.displayClientMessage(Component.literal("BRAND " + roman(stage) + " - " + NAMES[Math.min(stage, NAMES.length - 1)].toUpperCase()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
        boolean fight = !sl.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(16), m -> m instanceof Enemy && m.isAlive()).isEmpty();
        Story.Party pt = Story.partyOf(p);
        if (pt != null) { StoryArc1.onBrand(p.server, pt, p, fight); pt.dirty = true; }
        Story.sync(p, null);
    }

    public static String roman(int n) { return new String[]{"0", "I", "II", "III", "IV"}[Math.max(0, Math.min(4, n))]; }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % 8 != 0) return;
        long[] s = STATE.get(p.getUUID());
        if (s == null || p.serverLevel().getGameTime() >= s[0]) return;
        Vec3 arm = p.position().add(Vec3.directionFromRotation(0, p.yBodyRot + 90).scale(-0.35)).add(0, 1.05, 0);   // the right arm
        Kit.orb(p.serverLevel(), Fx.FIRE, arm, new Vec3(0, 0.05, 0), 0.08f, 10);
    }

    /** Ember Surge: melee blows burn for 3 more. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onHurt(LivingHurtEvent e) {
        if (!(e.getSource().getEntity() instanceof ServerPlayer p) || e.getEntity() == p || !BookTree.melee(e.getSource()) || !burning(p)) return;
        e.setAmount(e.getAmount() + 3f);
        Kit.orb(p.serverLevel(), Fx.FIRE, e.getEntity().position().add(0, e.getEntity().getBbHeight() * 0.6, 0), new Vec3(0, 0.08, 0), 0.25f, 8);
    }

    @SubscribeEvent public static void onStopping(net.minecraftforge.event.server.ServerStoppingEvent e) { STATE.clear(); }

    static void testReset(ServerPlayer p) { STATE.remove(p.getUUID()); }
}
