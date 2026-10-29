package de.yxnnick9.studio.mixin;

import de.yxnnick9.studio.SkyRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks of the vanilla sky renderer (1.21.11, intermediary names). Same injection points that
 * "Customization Studio" by Marv (MIT) uses - see NOTICE. The mixin is not required: if the names ever change,
 * it is skipped and the vanilla sky stays.
 */
@Pseudo
@Mixin(targets = "net.minecraft.class_9975", remap = false)
public abstract class SkyMixin {
    @Inject(method = "method_62302(I)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void yxn$starSky(int stars, CallbackInfo ci) {
        if (SkyRenderer.draw()) ci.cancel();
    }

    @Inject(method = {
            "method_62307(Lnet/minecraft/class_4587;FFFLnet/minecraft/class_12131;FF)V",
            "method_62306(Lnet/minecraft/class_4587;FI)V",
            "method_62305()V"
    }, at = @At("HEAD"), cancellable = true, remap = false)
    private void yxn$customSkyOnly(CallbackInfo ci) {
        if (SkyRenderer.shouldHide()) ci.cancel();
    }
}
