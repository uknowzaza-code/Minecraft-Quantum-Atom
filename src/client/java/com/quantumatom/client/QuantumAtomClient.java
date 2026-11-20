package com.quantumatom.client;

import com.quantumatom.QuantumAtomMod;
import com.quantumatom.network.ZoomEffectPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

public class QuantumAtomClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityRendererRegistry.register(QuantumAtomMod.ATOM_ENTITY, AtomEntityRenderer::new);
		HudRenderCallback.EVENT.register(new QuantumHudOverlay());

		ClientPlayNetworking.registerGlobalReceiver(ZoomEffectPayload.TYPE, (payload, context) ->
				context.client().execute(() -> QuantumZoomEffect.onPayloadReceived(payload))
		);

		ClientTickEvents.END_CLIENT_TICK.register(QuantumZoomEffect::tickClient);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> QuantumZoomEffect.reset());
	}
}
