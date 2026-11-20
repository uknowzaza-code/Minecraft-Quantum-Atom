package com.quantumatom.client.mixin;

import com.quantumatom.client.QuantumZoomEffect;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Applies smooth hyperspace FOV stretching during seamless multi-dimension
 * quantum shrink/grow transitions driven by {@link QuantumZoomEffect}.
 */
@Mixin(GameRenderer.class)
public class GameRendererFovMixin {
	@Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
	private void quantumatom$applyQuantumZoomFov(
			Camera camera,
			float partialTick,
			boolean useFovSetting,
			CallbackInfoReturnable<Double> cir
	) {
		if (!useFovSetting) {
			return;
		}
		double baseFov = cir.getReturnValueD();
		double modifiedFov = QuantumZoomEffect.modifyFov(baseFov, partialTick);
		if (modifiedFov != baseFov) {
			cir.setReturnValue(modifiedFov);
		}
	}
}
