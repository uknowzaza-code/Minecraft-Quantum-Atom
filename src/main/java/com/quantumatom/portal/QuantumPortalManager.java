package com.quantumatom.portal;

import com.quantumatom.QuantumAtomMod;
import com.quantumatom.dimension.QuantumRealmDimension;
import com.quantumatom.dimension.QuantumRealmDimension.ScaleLayer;
import com.quantumatom.entity.AtomEntity;
import com.quantumatom.network.ZoomEffectPayload;
import com.quantumatom.scale.ScaleManager;
import com.quantumatom.science.AtomElement;
import com.quantumatom.science.BlockChemistryMapper;
import com.quantumatom.science.BlockChemistryMapper.SyncedBlockContext;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import qouteall.imm_ptl.core.ScaleUtils;
import qouteall.imm_ptl.core.api.PortalAPI;
import qouteall.imm_ptl.core.chunk_loading.ChunkLoader;
import qouteall.imm_ptl.core.teleportation.ServerTeleportationManager;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Orchestrates seamless, zero-loading-screen multi-dimension transitions driven directly by
 * shrinking and growing across the 4-tier subatomic scale hierarchy:
 * {@code Overworld (10^0 m) <-> Atomic Realm (10^-10 m) <-> Nuclear Realm (10^-15 m) <-> Quark Realm (10^-18 m)}.
 * <p>
 * Uses Immersive Portals v6.0.6's {@link PortalAPI#addChunkLoaderForPlayer(ServerPlayer, ChunkLoader)}
 * to stream destination chunks and entities to the client during the cinematic windup ticks,
 * followed by {@link ServerTeleportationManager#forceTeleportPlayer(ServerPlayer, ResourceKey, Vec3)}
 * at the transition apex (tick 11 of 18) for a completely seamless dimension swap with zero loading screen.
 */
public final class QuantumPortalManager {
	/** Total duration of each cinematic shrink/grow dimension transition (0.9 seconds). */
	public static final int TRANSITION_DURATION_TICKS = 18;

	/** Tick at which the destination layer is populated so entities stream to the client pre-swap. */
	public static final int POPULATE_TICK = 8;

	/** Tick at which the seamless Immersive Portals dimension teleport fires (apex of FOV warp & flash). */
	public static final int SWAP_TICK = 11;

	/** Default vertical anchor inside all three void subatomic dimensions (min_y = -64, height = 384). */
	public static final double SUBATOMIC_ANCHOR_Y = 128.0;

	/** Chunk radius streamed to the client via Immersive Portals' ChunkLoader ahead of the swap. */
	private static final int PRELOAD_CHUNK_RADIUS = 3;

	/**
	 * Per-player journey stack preserving exact return coordinates, gaze angles, synchronized
	 * Overworld block chemistry, selected element, and selected nucleon type across all 4 tiers.
	 */
	public static final class QuantumJourneyState {
		public ResourceKey<Level> macroDimension = Level.OVERWORLD;
		public Vec3 macroReturnPos;
		public float macroYRot;
		public float macroXRot;
		public BlockPos anchorBlockPos;
		public String syncedBlockName = "";
		public String syncedChemicalFormula = "";
		public AtomElement primaryMacroElement = AtomElement.CARBON;

		public Vec3 atomicReturnPos;
		public float atomicYRot;
		public float atomicXRot;
		public AtomElement selectedElement = AtomElement.CARBON;

		public Vec3 nuclearReturnPos;
		public float nuclearYRot;
		public float nuclearXRot;
		public boolean selectedIsProton = true;
	}

	private static final class ShrinkTransition {
		final boolean shrinkingIn;
		final ScaleLayer fromLayer;
		final ScaleLayer targetLayer;
		final ResourceKey<Level> destDimension;
		final Vec3 destPos;
		final float destYRot;
		final float destXRot;
		final double startScale;
		final ChunkLoader preloader;

		// Context used to populate destination dimension right before the seamless swap
		final ResourceKey<Level> macroSourceDim;
		final BlockPos anchorBlockPos;
		final AtomElement targetElement;
		final boolean targetIsProton;
		final String sourceLabel;
		final Vec3 lookForward;

		int elapsedTicks = 0;
		boolean populated = false;
		boolean swapped = false;

		ShrinkTransition(
				boolean shrinkingIn,
				ScaleLayer fromLayer,
				ScaleLayer targetLayer,
				ResourceKey<Level> destDimension,
				Vec3 destPos,
				float destYRot,
				float destXRot,
				double startScale,
				ChunkLoader preloader,
				ResourceKey<Level> macroSourceDim,
				BlockPos anchorBlockPos,
				AtomElement targetElement,
				boolean targetIsProton,
				String sourceLabel,
				Vec3 lookForward
		) {
			this.shrinkingIn = shrinkingIn;
			this.fromLayer = fromLayer;
			this.targetLayer = targetLayer;
			this.destDimension = destDimension;
			this.destPos = destPos;
			this.destYRot = destYRot;
			this.destXRot = destXRot;
			this.startScale = startScale;
			this.preloader = preloader;
			this.macroSourceDim = macroSourceDim;
			this.anchorBlockPos = anchorBlockPos;
			this.targetElement = targetElement;
			this.targetIsProton = targetIsProton;
			this.sourceLabel = sourceLabel;
			this.lookForward = lookForward;
		}
	}

	private static final Map<UUID, QuantumJourneyState> JOURNEY_STATES = new ConcurrentHashMap<>();
	private static final Map<UUID, ShrinkTransition> ACTIVE_TRANSITIONS = new ConcurrentHashMap<>();

	private QuantumPortalManager() {
	}

	public static boolean isTransitioning(UUID playerId) {
		return playerId != null && ACTIVE_TRANSITIONS.containsKey(playerId);
	}

	public static QuantumJourneyState getJourneyState(UUID playerId) {
		return JOURNEY_STATES.computeIfAbsent(playerId, id -> new QuantumJourneyState());
	}

	/**
	 * Starts a cinematic 18-tick shrinking dive into the next deeper subatomic dimension:
	 * {@code OVERWORLD -> ATOMIC -> NUCLEAR -> QUARK}.
	 */
	public static boolean startDiveTransition(ServerPlayer player) {
		MinecraftServer server = player.getServer();
		if (server == null) {
			return false;
		}
		UUID playerId = player.getUUID();
		if (ACTIVE_TRANSITIONS.containsKey(playerId)) {
			return false;
		}

		ServerLevel sourceLevel = player.serverLevel();
		ScaleLayer currentLayer = QuantumRealmDimension.getLayer(sourceLevel);
		ScaleLayer targetLayer = currentLayer.deeper();
		if (currentLayer == ScaleLayer.QUARK || targetLayer == null || targetLayer == currentLayer) {
			player.displayClientMessage(
					Component.literal("Fundamental Limit Reached — Quarks & Gluons are elementary particles (Standard Model, 10^-18 m)!"),
					true
			);
			return false;
		}

		ServerLevel destLevel = QuantumRealmDimension.getLevelForLayer(server, targetLayer);
		if (destLevel == null) {
			QuantumAtomMod.LOGGER.error("Destination dimension for layer {} is not loaded!", targetLayer);
			player.displayClientMessage(
					Component.literal("Subatomic dimension " + targetLayer.displayName() + " is not loaded!"),
					false
			);
			return false;
		}

		QuantumJourneyState state = getJourneyState(playerId);
		Vec3 lookForward = getHorizontalForward(player);
		Vec3 destPos = new Vec3(
				Math.floor(player.getX()) + 0.5,
				SUBATOMIC_ANCHOR_Y,
				Math.floor(player.getZ()) + 0.5
		);

		ResourceKey<Level> macroSourceDim = state.macroDimension;
		BlockPos anchorBlockPos = state.anchorBlockPos;
		AtomElement targetElement = state.selectedElement;
		boolean targetIsProton = state.selectedIsProton;
		String sourceLabel;

		switch (currentLayer) {
			case OVERWORLD -> {
				anchorBlockPos = findAnchorBlock(sourceLevel, player);
				SyncedBlockContext preview = BlockChemistryMapper.mapNeighborhood(sourceLevel, anchorBlockPos);

				state.macroDimension = sourceLevel.dimension();
				state.macroReturnPos = player.position();
				state.macroYRot = player.getYRot();
				state.macroXRot = player.getXRot();
				state.anchorBlockPos = anchorBlockPos;
				state.syncedBlockName = preview.blockDisplayName();
				state.syncedChemicalFormula = preview.chemicalFormula();
				state.primaryMacroElement = preview.primaryElement();
				state.selectedElement = preview.primaryElement();

				macroSourceDim = sourceLevel.dimension();
				targetElement = preview.primaryElement();
				sourceLabel = formatSourceLabel(state.syncedBlockName, state.syncedChemicalFormula);
			}
			case ATOMIC -> {
				AtomEntity targetAtom = findBestTargetAtom(sourceLevel, player);
				targetElement = targetAtom != null
						? targetAtom.getElement()
						: (state.selectedElement != null ? state.selectedElement : AtomElement.CARBON);

				state.atomicReturnPos = player.position();
				state.atomicYRot = player.getYRot();
				state.atomicXRot = player.getXRot();
				state.selectedElement = targetElement;

				if (targetAtom != null && !targetAtom.getSourceLabel().isEmpty()) {
					sourceLabel = targetAtom.getSourceLabel();
				} else if (!state.syncedBlockName.isEmpty()) {
					sourceLabel = formatSourceLabel(state.syncedBlockName, state.syncedChemicalFormula);
				} else {
					sourceLabel = targetElement.isotopeName();
					state.syncedBlockName = targetElement.isotopeName();
					state.syncedChemicalFormula = targetElement.electronConfig();
				}
			}
			case NUCLEAR -> {
				AtomEntity targetAtom = findBestTargetAtom(sourceLevel, player);
				targetElement = targetAtom != null
						? targetAtom.getElement()
						: (state.selectedElement != null ? state.selectedElement : AtomElement.CARBON);
				targetIsProton = targetAtom == null || targetAtom.isNearestNucleonProton(player.getEyePosition());

				state.nuclearReturnPos = player.position();
				state.nuclearYRot = player.getYRot();
				state.nuclearXRot = player.getXRot();
				state.selectedElement = targetElement;
				state.selectedIsProton = targetIsProton;

				if (targetAtom != null && !targetAtom.getSourceLabel().isEmpty()) {
					sourceLabel = targetAtom.getSourceLabel();
				} else if (!state.syncedBlockName.isEmpty()) {
					sourceLabel = formatSourceLabel(state.syncedBlockName, state.syncedChemicalFormula);
				} else {
					sourceLabel = targetElement.isotopeName();
					state.syncedBlockName = targetElement.isotopeName();
					state.syncedChemicalFormula = targetElement.protons() + "p+ / " + targetElement.neutrons() + "n0";
				}
			}
			default -> {
				return false;
			}
		}

		// Clear any manual scale interpolation target and ensure iportal:scaling is clean 1.0x
		ScaleManager.cancelManualInterpolation(playerId);
		ScaleUtils.setIPortalScaling(player, 1.0);

		double startScale = Math.max(0.06, Math.min(2.0, ScaleManager.getBaseScale(player)));
		ChunkLoader preloader = attachPreloader(player, destLevel, destPos);

		ShrinkTransition transition = new ShrinkTransition(
				true,
				currentLayer,
				targetLayer,
				destLevel.dimension(),
				destPos,
				player.getYRot(),
				player.getXRot(),
				startScale,
				preloader,
				macroSourceDim,
				anchorBlockPos,
				targetElement,
				targetIsProton,
				sourceLabel,
				lookForward
		);
		ACTIVE_TRANSITIONS.put(playerId, transition);

		// Immediately trigger client FOV warp, radial speed-lines, quantum flash, and layered woosh sound
		ServerPlayNetworking.send(player, new ZoomEffectPayload(
				true,
				targetLayer.ordinal(),
				TRANSITION_DURATION_TICKS,
				SWAP_TICK,
				state.syncedBlockName,
				state.syncedChemicalFormula
		));

		player.displayClientMessage(
				Component.literal(buildDiveStartMessage(targetLayer, state, targetElement, targetIsProton)),
				true
		);
		return true;
	}

	/**
	 * Starts a cinematic 18-tick grow-up transition ascending one scale layer back toward the Overworld:
	 * {@code QUARK -> NUCLEAR -> ATOMIC -> OVERWORLD}.
	 */
	public static boolean startAscendTransition(ServerPlayer player) {
		MinecraftServer server = player.getServer();
		if (server == null) {
			return false;
		}
		UUID playerId = player.getUUID();
		if (ACTIVE_TRANSITIONS.containsKey(playerId)) {
			return false;
		}

		ServerLevel sourceLevel = player.serverLevel();
		ScaleLayer currentLayer = QuantumRealmDimension.getLayer(sourceLevel);
		ScaleLayer targetLayer = currentLayer.shallower();
		if (currentLayer == ScaleLayer.OVERWORLD || targetLayer == null || targetLayer == currentLayer) {
			ScaleManager.resetToMacroscopic(player);
			player.displayClientMessage(
					Component.literal("Already in Macroscopic World (10^0 m — 1.0x)"),
					true
			);
			return false;
		}

		QuantumJourneyState state = getJourneyState(playerId);
		ServerLevel destLevel;
		Vec3 destPos;
		float destYRot = player.getYRot();
		float destXRot = player.getXRot();

		if (targetLayer == ScaleLayer.OVERWORLD) {
			destLevel = state.macroDimension != null ? server.getLevel(state.macroDimension) : null;
			if (destLevel == null || QuantumRealmDimension.isSubatomicDimension(destLevel)) {
				destLevel = server.overworld();
			}
			if (state.macroReturnPos != null) {
				destPos = state.macroReturnPos;
				destYRot = state.macroYRot;
				destXRot = state.macroXRot;
			} else {
				destPos = computeSafeOverworldReturnPos(destLevel, player.position());
			}
		} else {
			destLevel = QuantumRealmDimension.getLevelForLayer(server, targetLayer);
			if (destLevel == null) {
				QuantumAtomMod.LOGGER.error("Destination dimension for layer {} is not loaded!", targetLayer);
				return false;
			}
			if (targetLayer == ScaleLayer.NUCLEAR && state.nuclearReturnPos != null) {
				destPos = state.nuclearReturnPos;
				destYRot = state.nuclearYRot;
				destXRot = state.nuclearXRot;
			} else if (targetLayer == ScaleLayer.ATOMIC && state.atomicReturnPos != null) {
				destPos = state.atomicReturnPos;
				destYRot = state.atomicYRot;
				destXRot = state.atomicXRot;
			} else {
				destPos = new Vec3(
						Math.floor(player.getX()) + 0.5,
						SUBATOMIC_ANCHOR_Y,
						Math.floor(player.getZ()) + 0.5
				);
			}
		}

		ScaleManager.cancelManualInterpolation(playerId);
		ScaleUtils.setIPortalScaling(player, 1.0);

		double startScale = Math.max(0.5, Math.min(2.8, ScaleManager.getBaseScale(player)));
		ChunkLoader preloader = attachPreloader(player, destLevel, destPos);

		String sourceLabel = formatSourceLabel(state.syncedBlockName, state.syncedChemicalFormula);
		ShrinkTransition transition = new ShrinkTransition(
				false,
				currentLayer,
				targetLayer,
				destLevel.dimension(),
				destPos,
				destYRot,
				destXRot,
				startScale,
				preloader,
				state.macroDimension,
				state.anchorBlockPos,
				state.selectedElement != null ? state.selectedElement : AtomElement.CARBON,
				state.selectedIsProton,
				sourceLabel,
				getHorizontalForward(player)
		);
		ACTIVE_TRANSITIONS.put(playerId, transition);

		ServerPlayNetworking.send(player, new ZoomEffectPayload(
				false,
				targetLayer.ordinal(),
				TRANSITION_DURATION_TICKS,
				SWAP_TICK,
				state.syncedBlockName,
				state.syncedChemicalFormula
		));

		player.displayClientMessage(
				Component.literal("Ascending to " + targetLayer.displayName() + " (" + targetLayer.scaleNotation() + ")..."),
				true
		);
		return true;
	}

	/**
	 * Advances all active 18-tick shrink/grow dimension transitions each server tick.
	 */
	public static void tickServer(MinecraftServer server) {
		if (ACTIVE_TRANSITIONS.isEmpty()) {
			return;
		}

		Iterator<Map.Entry<UUID, ShrinkTransition>> it = ACTIVE_TRANSITIONS.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, ShrinkTransition> entry = it.next();
			UUID playerId = entry.getKey();
			ShrinkTransition tr = entry.getValue();
			ServerPlayer player = server.getPlayerList().getPlayer(playerId);

			if (player == null || player.isRemoved()) {
				it.remove();
				continue;
			}

			tr.elapsedTicks++;
			int tick = tr.elapsedTicks;
			player.resetFallDistance();

			// Step 1: Pre-populate destination layer ahead of the seamless swap so ImmPtl streams entities to client
			if (!tr.populated && tick >= POPULATE_TICK) {
				populateDestinationForTransition(server, tr);
				tr.populated = true;
			}

			// Step 2: Drive smooth pre-swap scale curve, apex seamless teleport, and post-swap arrival ease-out
			if (tick < SWAP_TICK) {
				double progress = tick / (double) SWAP_TICK;
				double curved = progress * progress; // accelerate rapidly into the warp apex
				double targetApexScale = tr.shrinkingIn ? 0.05 : 3.0;
				double nextScale = tr.startScale * Math.pow(targetApexScale / tr.startScale, curved);
				ScaleUtils.setBaseScale(player, clampTransitionScale(nextScale));
			} else if (!tr.swapped) {
				if (!tr.populated) {
					populateDestinationForTransition(server, tr);
					tr.populated = true;
				}
				executeSeamlessDimensionSwap(server, player, tr);
				tr.swapped = true;
			} else if (tick < TRANSITION_DURATION_TICKS) {
				double postProgress = (tick - SWAP_TICK) / (double) (TRANSITION_DURATION_TICKS - SWAP_TICK);
				double easeOut = 1.0 - Math.pow(1.0 - postProgress, 3.0);
				double arrivalStartScale = tr.shrinkingIn ? 2.2 : 0.15;
				double nextScale = arrivalStartScale * Math.pow(1.0 / arrivalStartScale, easeOut);
				ScaleUtils.setBaseScale(player, clampTransitionScale(nextScale));
			} else {
				// Finalize transition at crisp 1.0x scale inside the new layer
				ScaleUtils.setIPortalScaling(player, 1.0);
				ScaleUtils.setBaseScale(player, 1.0);
				detachPreloader(player, tr.preloader);

				if (tr.targetLayer == ScaleLayer.OVERWORLD) {
					ScaleManager.restoreMacroscopicFlight(player);
				} else {
					QuantumRealmDimension.tickSubatomicPlayer(player);
				}

				QuantumJourneyState state = JOURNEY_STATES.get(playerId);
				player.displayClientMessage(
						Component.literal(buildArrivalMessage(tr, state)),
						true
				);
				it.remove();
			}
		}
	}

	public static void onPlayerDisconnect(ServerPlayer player) {
		if (player == null) {
			return;
		}
		UUID playerId = player.getUUID();
		ShrinkTransition active = ACTIVE_TRANSITIONS.remove(playerId);
		if (active != null) {
			detachPreloader(player, active.preloader);
			ScaleUtils.setIPortalScaling(player, 1.0);
			ScaleUtils.setBaseScale(player, 1.0);
		}
		JOURNEY_STATES.remove(playerId);
	}

	private static void populateDestinationForTransition(MinecraftServer server, ShrinkTransition tr) {
		ServerLevel destLevel = server.getLevel(tr.destDimension);
		if (destLevel == null) {
			return;
		}

		preloadServerChunks(destLevel, tr.destPos);

		if (tr.shrinkingIn) {
			switch (tr.targetLayer) {
				case ATOMIC -> {
					ServerLevel macroLevel = tr.macroSourceDim != null ? server.getLevel(tr.macroSourceDim) : server.overworld();
					if (macroLevel == null) {
						macroLevel = server.overworld();
					}
					BlockPos anchor = tr.anchorBlockPos != null ? tr.anchorBlockPos : BlockPos.containing(tr.destPos);
					QuantumRealmDimension.populateAtomicRealmFromOverworld(destLevel, macroLevel, anchor, tr.destPos);
				}
				case NUCLEAR -> QuantumRealmDimension.populateNuclearRealm(
						destLevel,
						tr.destPos,
						tr.lookForward,
						tr.targetElement,
						tr.sourceLabel
				);
				case QUARK -> QuantumRealmDimension.populateQuarkRealm(
						destLevel,
						tr.destPos,
						tr.targetIsProton,
						tr.targetElement,
						tr.sourceLabel
				);
				case OVERWORLD -> {
					// No population needed when diving (Overworld is the outermost layer)
				}
			}
		} else {
			// When ascending back into a subatomic layer, ensure atoms/nucleus exist if chunks were empty
			if (tr.targetLayer == ScaleLayer.NUCLEAR && !hasNearbyAtom(destLevel, tr.destPos, 96.0)) {
				QuantumRealmDimension.populateNuclearRealm(
						destLevel,
						tr.destPos,
						tr.lookForward,
						tr.targetElement,
						tr.sourceLabel
				);
			} else if (tr.targetLayer == ScaleLayer.ATOMIC && !hasNearbyAtom(destLevel, tr.destPos, 110.0)) {
				ServerLevel macroLevel = tr.macroSourceDim != null ? server.getLevel(tr.macroSourceDim) : server.overworld();
				if (macroLevel != null && tr.anchorBlockPos != null) {
					QuantumRealmDimension.populateAtomicRealmFromOverworld(destLevel, macroLevel, tr.anchorBlockPos, tr.destPos);
				}
			}
		}
	}

	private static void executeSeamlessDimensionSwap(MinecraftServer server, ServerPlayer player, ShrinkTransition tr) {
		ServerLevel destLevel = server.getLevel(tr.destDimension);
		if (destLevel == null) {
			return;
		}

		// Set arrival punch-in scale & destination gaze BEFORE forceTeleportPlayer so Immersive Portals
		// computes the exact arrival eye offset, bounding box, and yaw/pitch in one seamless frame.
		double arrivalStartScale = tr.shrinkingIn ? 2.2 : 0.15;
		ScaleUtils.setIPortalScaling(player, 1.0);
		ScaleUtils.setBaseScale(player, arrivalStartScale);
		player.setYRot(tr.destYRot);
		player.setXRot(tr.destXRot);
		player.setYHeadRot(tr.destYRot);

		// Zero-loading-screen seamless cross-dimension teleport via Immersive Portals v6.0.6
		ServerTeleportationManager.of(server).forceTeleportPlayer(player, tr.destDimension, tr.destPos);

		player.resetFallDistance();
		player.setDeltaMovement(Vec3.ZERO);
		player.hurtMarked = true;

		if (tr.targetLayer != ScaleLayer.OVERWORLD) {
			// Enable smooth 3D zero-G flight immediately on arrival tick so there is zero gravity drop
			QuantumRealmDimension.tickSubatomicPlayer(player);
		}
	}

	private static ChunkLoader attachPreloader(ServerPlayer player, ServerLevel destLevel, Vec3 destPos) {
		int cx = ((int) Math.floor(destPos.x)) >> 4;
		int cz = ((int) Math.floor(destPos.z)) >> 4;
		ChunkLoader loader = new ChunkLoader(destLevel.dimension(), cx, cz, PRELOAD_CHUNK_RADIUS);
		PortalAPI.addChunkLoaderForPlayer(player, loader);
		return loader;
	}

	private static void detachPreloader(ServerPlayer player, ChunkLoader preloader) {
		if (preloader != null && player != null) {
			PortalAPI.removeChunkLoaderForPlayer(player, preloader);
		}
	}

	private static void preloadServerChunks(ServerLevel level, Vec3 center) {
		int cx = ((int) Math.floor(center.x)) >> 4;
		int cz = ((int) Math.floor(center.z)) >> 4;
		for (int dx = -PRELOAD_CHUNK_RADIUS; dx <= PRELOAD_CHUNK_RADIUS; dx++) {
			for (int dz = -PRELOAD_CHUNK_RADIUS; dz <= PRELOAD_CHUNK_RADIUS; dz++) {
				level.getChunk(cx + dx, cz + dz);
			}
		}
	}

	private static boolean hasNearbyAtom(ServerLevel level, Vec3 center, double radius) {
		AABB box = AABB.ofSize(center, radius * 2.0, radius * 2.0, radius * 2.0);
		return !level.getEntitiesOfClass(AtomEntity.class, box).isEmpty();
	}

	/**
	 * Finds the Overworld block the player is aiming at (including fluids like Water), or falls back
	 * to the block at/under their feet or nearest non-air block in the immediate 3x3x3 neighborhood.
	 */
	private static BlockPos findAnchorBlock(ServerLevel level, ServerPlayer player) {
		Vec3 eyePos = player.getEyePosition();
		Vec3 reachEnd = eyePos.add(player.getLookAngle().scale(10.0));
		BlockHitResult hit = level.clip(new ClipContext(
				eyePos,
				reachEnd,
				ClipContext.Block.OUTLINE,
				ClipContext.Fluid.ANY,
				player
		));
		if (hit.getType() == HitResult.Type.BLOCK) {
			BlockPos hitPos = hit.getBlockPos();
			if (!level.getBlockState(hitPos).isAir()) {
				return hitPos;
			}
		}

		BlockPos feetPos = player.blockPosition();
		if (!level.getBlockState(feetPos).isAir()) {
			return feetPos;
		}
		BlockPos belowPos = feetPos.below();
		if (!level.getBlockState(belowPos).isAir()) {
			return belowPos;
		}

		// Search immediate 3x3x3 neighborhood for nearest solid/fluid block
		BlockPos closest = null;
		double closestDistSqr = Double.MAX_VALUE;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				for (int dz = -1; dz <= 1; dz++) {
					BlockPos candidate = feetPos.offset(dx, dy, dz);
					BlockState state = level.getBlockState(candidate);
					if (!state.isAir()) {
						double d2 = candidate.distToCenterSqr(eyePos);
						if (d2 < closestDistSqr) {
							closestDistSqr = d2;
							closest = candidate;
						}
					}
				}
			}
		}
		return closest != null ? closest : feetPos;
	}

	/**
	 * Selects the best {@link AtomEntity} target in a subatomic dimension, prioritizing atoms
	 * the player is either very close to / inside OR looking directly toward.
	 */
	private static AtomEntity findBestTargetAtom(ServerLevel level, ServerPlayer player) {
		Vec3 eyePos = player.getEyePosition();
		Vec3 look = player.getLookAngle().normalize();
		AABB searchBox = AABB.ofSize(eyePos, 256.0, 256.0, 256.0);
		List<AtomEntity> atoms = level.getEntitiesOfClass(AtomEntity.class, searchBox);
		if (atoms.isEmpty()) {
			return null;
		}

		AtomEntity best = null;
		double bestScore = Double.MAX_VALUE;
		for (AtomEntity atom : atoms) {
			Vec3 toAtom = atom.position().subtract(eyePos);
			double dist = toAtom.length();
			if (dist < 1.0E-4) {
				return atom;
			}
			// Lower score is better: close atoms and aimed-at atoms win strongly
			double dot = look.dot(toAtom.scale(1.0 / dist));
			double aimMultiplier = dot > 0.6 ? (1.0 - 0.55 * (dot - 0.6) / 0.4) : 1.15;
			double score = dist * aimMultiplier;
			if (score < bestScore) {
				bestScore = score;
				best = atom;
			}
		}
		return best;
	}

	private static Vec3 getHorizontalForward(ServerPlayer player) {
		Vec3 look = player.getLookAngle();
		Vec3 horizontal = new Vec3(look.x, 0.0, look.z);
		if (horizontal.lengthSqr() < 1.0E-4) {
			horizontal = Vec3.directionFromRotation(0.0f, player.getYRot());
		}
		return horizontal.normalize();
	}

	private static Vec3 computeSafeOverworldReturnPos(ServerLevel overworld, Vec3 subatomicPos) {
		int bx = (int) Math.floor(subatomicPos.x);
		int bz = (int) Math.floor(subatomicPos.z);
		overworld.getChunk(bx >> 4, bz >> 4);
		int surfaceY = overworld.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
		if (surfaceY <= overworld.getMinBuildHeight() + 2) {
			BlockPos spawn = overworld.getSharedSpawnPos();
			return new Vec3(spawn.getX() + 0.5, spawn.getY() + 1.0, spawn.getZ() + 0.5);
		}
		return new Vec3(bx + 0.5, surfaceY + 1.0, bz + 0.5);
	}

	private static double clampTransitionScale(double value) {
		return Math.max(ScaleManager.MIN_SCALE, Math.min(ScaleManager.MAX_SCALE, value));
	}

	private static String formatSourceLabel(String blockName, String formula) {
		if (blockName == null || blockName.isEmpty()) {
			return formula != null ? formula : "";
		}
		if (formula == null || formula.isEmpty()) {
			return blockName;
		}
		return blockName + " — " + formula;
	}

	private static String buildDiveStartMessage(
			ScaleLayer targetLayer,
			QuantumJourneyState state,
			AtomElement element,
			boolean isProton
	) {
		return switch (targetLayer) {
			case ATOMIC -> "Shrinking into " + state.syncedBlockName + " [" + state.syncedChemicalFormula + "] — 10^-10 m...";
			case NUCLEAR -> "Diving into " + element.isotopeName() + " Nucleus (" + element.protons() + "p+ / " + element.neutrons() + "n0) — 10^-15 m...";
			case QUARK -> "Entering " + (isProton ? "PROTON (uud)" : "NEUTRON (udd)") + " of " + element.isotopeName() + " — 10^-18 m...";
			case OVERWORLD -> "Transitioning to Overworld...";
		};
	}

	private static String buildArrivalMessage(ShrinkTransition tr, QuantumJourneyState state) {
		return switch (tr.targetLayer) {
			case OVERWORLD -> "Macroscopic World (10^0 m — 1.0x)";
			case ATOMIC -> {
				String chem = state != null && !state.syncedBlockName.isEmpty()
						? " | " + state.syncedBlockName + " (" + state.syncedChemicalFormula + ")"
						: "";
				yield "Atomic Realm (10^-10 m)" + chem;
			}
			case NUCLEAR -> "Nuclear Realm (10^-15 m) | " + tr.targetElement.isotopeName()
					+ " (" + tr.targetElement.protons() + "p+ / " + tr.targetElement.neutrons() + "n0)";
			case QUARK -> "Quark Realm (10^-18 m) | Inside "
					+ (tr.targetIsProton ? "PROTON (uud, +1e)" : "NEUTRON (udd, 0e)")
					+ " — QCD Confinement Cavity";
		};
	}
}
