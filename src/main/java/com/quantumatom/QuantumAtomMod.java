package com.quantumatom;

import com.quantumatom.command.AtomCommand;
import com.quantumatom.dimension.QuantumRealmDimension;
import com.quantumatom.entity.AtomEntity;
import com.quantumatom.item.QuantumShrinkerItem;
import com.quantumatom.network.ZoomEffectPayload;
import com.quantumatom.portal.QuantumPortalManager;
import com.quantumatom.scale.ScaleManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QuantumAtomMod implements ModInitializer {
	public static final String MOD_ID = "quantumatom";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static final EntityType<AtomEntity> ATOM_ENTITY = Registry.register(
			BuiltInRegistries.ENTITY_TYPE,
			id("atom"),
			EntityType.Builder.<AtomEntity>of(AtomEntity::new, MobCategory.MISC)
					.sized(0.5f, 0.5f)
					.clientTrackingRange(16)
					.updateInterval(10)
					.build()
	);

	public static final Item QUANTUM_SHRINKER = Registry.register(
			BuiltInRegistries.ITEM,
			id("quantum_shrinker"),
			new QuantumShrinkerItem(new Item.Properties().stacksTo(1))
	);

	@Override
	public void onInitialize() {
		PayloadTypeRegistry.playS2C().register(ZoomEffectPayload.TYPE, ZoomEffectPayload.STREAM_CODEC);

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				AtomCommand.register(dispatcher)
		);

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			QuantumPortalManager.tickServer(server);
			ScaleManager.tickServer(server);
			QuantumRealmDimension.tickServer(server);
		});

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			QuantumPortalManager.onPlayerDisconnect(handler.player);
			ScaleManager.onPlayerDisconnect(handler.player);
			QuantumRealmDimension.onPlayerDisconnect(handler.player);
		});

		ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(entries ->
				entries.accept(QUANTUM_SHRINKER)
		);

		LOGGER.info("Quantum Atom Explorer initialized (4-Tier Seamless Subatomic Zoom: Overworld <-> Atomic <-> Nuclear <-> Quark).");
	}

	public static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
	}
}
