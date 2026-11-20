package com.quantumatom.mixin;

import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Relaxes the minimum bound of {@code attribute.name.generic.scale} from 0.0625 down to 0.005
 * so players can shrink small enough to enter nucleons and inspect quarks.
 */
@Mixin(RangedAttribute.class)
public class RangedAttributeMixin {
	@Shadow
	@Final
	@Mutable
	private double minValue;

	@Inject(method = "<init>", at = @At("RETURN"))
	private void quantumatom$relaxScaleMin(String descriptionId, double defaultValue, double min, double max, CallbackInfo ci) {
		if ("attribute.name.generic.scale".equals(descriptionId)) {
			this.minValue = 0.005;
		}
	}
}
