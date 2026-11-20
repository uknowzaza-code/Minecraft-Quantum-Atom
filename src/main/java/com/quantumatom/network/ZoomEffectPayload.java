package com.quantumatom.network;

import com.quantumatom.QuantumAtomMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * S2C payload triggered when a player begins a seamless multi-dimension quantum zoom transition
 * (shrinking deeper or growing back up). The client uses this payload to drive the smooth
 * pre-swap windup (FOV stretch + radial speed-lines + woosh), transition apex flash at
 * {@code swapTick}, post-swap arrival ease-out through {@code durationTicks}, and HUD synced
 * Overworld block/chemistry context.
 */
public record ZoomEffectPayload(
		boolean shrinkingIn,
		int targetLayerOrdinal,
		int durationTicks,
		int swapTick,
		String blockLabel,
		String chemicalFormula
) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<ZoomEffectPayload> TYPE =
			new CustomPacketPayload.Type<>(QuantumAtomMod.id("zoom_effect"));

	public static final StreamCodec<RegistryFriendlyByteBuf, ZoomEffectPayload> STREAM_CODEC =
			CustomPacketPayload.codec(ZoomEffectPayload::write, ZoomEffectPayload::read);

	public ZoomEffectPayload {
		blockLabel = blockLabel != null ? blockLabel : "";
		chemicalFormula = chemicalFormula != null ? chemicalFormula : "";
	}

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeBoolean(this.shrinkingIn);
		buf.writeVarInt(this.targetLayerOrdinal);
		buf.writeVarInt(this.durationTicks);
		buf.writeVarInt(this.swapTick);
		buf.writeUtf(this.blockLabel, 256);
		buf.writeUtf(this.chemicalFormula, 256);
	}

	private static ZoomEffectPayload read(RegistryFriendlyByteBuf buf) {
		boolean shrinkingIn = buf.readBoolean();
		int targetLayerOrdinal = buf.readVarInt();
		int durationTicks = buf.readVarInt();
		int swapTick = buf.readVarInt();
		String blockLabel = buf.readUtf(256);
		String chemicalFormula = buf.readUtf(256);
		return new ZoomEffectPayload(
				shrinkingIn,
				targetLayerOrdinal,
				durationTicks,
				swapTick,
				blockLabel,
				chemicalFormula
		);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
