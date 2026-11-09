package com.example.arcaneaddon.mixin.compat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ConcurrentModificationException;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Compatibility fix for Essential (1.5.x): while drawing a player it reads the skin "textures" property of the
 * player's profile, and another thread can be updating that property at the same moment - Essential then crashes
 * the game with a ConcurrentModificationException (GameProfileManager.handleGameProfile).
 *
 * Here that one read is made safe: if the property is being changed right then, Essential is told there is nothing
 * to apply THIS frame (the same answer it gives for a profile with no skin yet) and simply tries again next frame.
 * Nothing else about Essential changes. Without Essential installed this does nothing (@Pseudo, optional config).
 */
@Pseudo
@Mixin(targets = "gg.essential.handlers.GameProfileManager", remap = false)
public abstract class EssentialProfileFix {
    @Redirect(method = "handleGameProfile(Lcom/mojang/authlib/GameProfile;Lgg/essential/mod/Skin;Ljava/lang/String;)Lcom/mojang/authlib/GameProfile;",
            at = @At(value = "INVOKE", target = "Ljava/util/stream/Stream;findFirst()Ljava/util/Optional;", remap = false),
            remap = false, require = 0)
    private static Optional<?> arcane_addon$safeFindFirst(Stream<?> stream) {
        try {
            return stream.findFirst();
        } catch (ConcurrentModificationException e) {
            return Optional.empty();
        }
    }
}
