package com.quantumatom.scale;

import com.quantumatom.dimension.QuantumRealmDimension;
import com.quantumatom.dimension.QuantumRealmDimension.ScaleLayer;
import com.quantumatom.portal.QuantumPortalManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import qouteall.imm_ptl.core.ScaleUtils;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages smooth logarithmic player scale interpolation and automatically triggers seamless
 * multi-dimension transitions via {@link QuantumPortalManager} whenever manual scaling crosses
 * the shrink-dive threshold ({@code < 0.12}) or grow-ascend threshold ({@code > 2.5}).
 */
public final class ScaleManager {
	public static final double MIN_SCALE = 0.005;
	public static final double MAX_SCALE = 16.0;

	/** Crossing below this scale via {@code /atom scale} automatically dives one subatomic layer deeper. */
	public static final double SHRINK_DIVE_THRESHOLD = 0.12;

	/** Crossing above this scale via {@code /atom scale} automatically ascends one layer shallower. */
	public static final double GROW_ASCEND_THRESHOLD = 2.5;

	private static final Map<UUID, Double> TARGET_BASE_SCALES = new ConcurrentHashMap<>();

	private ScaleManager() {
	}

	/**
	 * Returns the player's effective scale ({@code Attributes.SCALE} total value).
	 */
	public static double getScale(Player player) {
		AttributeInstance attr = player.getAttribute(Attributes.SCALE);
		return attr != null ? attr.getValue() : 1.0;
	}

	/**
	 * Returns the player's base {@code Attributes.SCALE} value.
	 */
	public static double getBaseScale(Player player) {
		AttributeInstance attr = player.getAttribute(Attributes.SCALE);
		return attr != null ? attr.getBaseValue() : 1.0;
	}

	/**
	 * Returns the active Immersive Portals {@code iportal:scaling} multiplier on {@code player}.
	 */
	public static double getIPortalScale(Player player) {
		return ScaleUtils.getIPortalScaling(player);
	}

	/**
	 * Smoothly interpolates the player's scale toward {@code targetBaseScale}.
	 * If a player shrinks below {@link #SHRINK_DIVE_THRESHOLD} or expands above
	 * {@link #GROW_ASCEND_THRESHOLD}, {@link #tickServer(MinecraftServer)} automatically
	 * triggers a seamless cross-dimension zoom transition.
	 */
	public static void setScale(Player player, double targetBaseScale) {
		if (QuantumPortalManager.isTransitioning(player.getUUID())) {
			return;
		}
		double clamped = clampScale(targetBaseScale);
		TARGET_BASE_SCALES.put(player.getUUID(), clamped);
		applyMacroFlightState(player, clamped);
	}

	/**
	 * Immediately sets the player's base scale to {@code targetBaseScale}.
	 */
	public static void setScaleImmediate(Player player, double targetBaseScale) {
		double clamped = clampScale(targetBaseScale);
		TARGET_BASE_SCALES.remove(player.getUUID());
		ScaleUtils.setBaseScale(player, clamped);
		applyMacroFlightState(player, clamped);
	}

	/**
	 * Smoothly resets the player to macroscopic {@code 1.0x} scale, clearing any residual
	 * Immersive Portals scaling modifier.
	 */
	public static void resetToMacroscopic(Player player) {
		ScaleUtils.setIPortalScaling(player, 1.0);
		setScale(player, 1.0);
	}

	/**
	 * Cancels any pending manual scale interpolation (invoked when a cinematic dimension transition takes over).
	 */
	public static void cancelManualInterpolation(UUID playerId) {
		if (playerId != null) {
			TARGET_BASE_SCALES.remove(playerId);
		}
	}

	/**
	 * Restores normal Overworld survival flight rules after ascending back to the macroscopic world.
	 */
	public static void restoreMacroscopicFlight(ServerPlayer player) {
		player.resetFallDistance();
		if (!player.isCreative() && !player.isSpectator()) {
			if (player.getAbilities().mayfly || player.getAbilities().flying) {
				player.getAbilities().mayfly = false;
				player.getAbilities().flying = false;
				player.onUpdateAbilities();
			}
		}
	}

	/**
	 * Smoothly interpolates manual player scale targets logarithmically each server tick, and
	 * triggers automatic seamless dimension dives/ascents when crossing scale thresholds.
	 */
	public static void tickServer(MinecraftServer server) {
		if (TARGET_BASE_SCALES.isEmpty()) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			UUID playerId = player.getUUID();
			if (QuantumPortalManager.isTransitioning(playerId)) {
				TARGET_BASE_SCALES.remove(playerId);
				continue;
			}

			Double targetBase = TARGET_BASE_SCALES.get(playerId);
			if (targetBase == null) {
				continue;
			}

			AttributeInstance attr = player.getAttribute(Attributes.SCALE);
			if (attr == null) {
				TARGET_BASE_SCALES.remove(playerId);
				continue;
			}

			double currentBase = attr.getBaseValue();
			double ratio = targetBase / Math.max(MIN_SCALE, currentBase);
			double nextBase;
			if (Math.abs(ratio - 1.0) < 0.015) {
				nextBase = targetBase;
				TARGET_BASE_SCALES.remove(playerId);
				ScaleUtils.setBaseScale(player, nextBase);
				applyMacroFlightState(player, nextBase);
			} else {
				nextBase = clampScale(currentBase * Math.pow(ratio, 0.26));
				ScaleUtils.setBaseScale(player, nextBase);
			}

			// Automatic threshold-driven seamless dimension transition!
			ScaleLayer layer = QuantumRealmDimension.getLayer(player.level());
			if (nextBase < SHRINK_DIVE_THRESHOLD && layer != ScaleLayer.QUARK) {
				TARGET_BASE_SCALES.remove(playerId);
				QuantumPortalManager.startDiveTransition(player);
			} else if (nextBase > GROW_ASCEND_THRESHOLD && layer != ScaleLayer.OVERWORLD) {
				TARGET_BASE_SCALES.remove(playerId);
				QuantumPortalManager.startAscendTransition(player);
			}
		}
	}

	public static void onPlayerDisconnect(ServerPlayer player) {
		if (player != null) {
			TARGET_BASE_SCALES.remove(player.getUUID());
		}
	}

	public static String describeScale(double scale) {
		if (scale < SHRINK_DIVE_THRESHOLD) {
			return "Quantum Dive Threshold (< 0.12x)";
		} else if (scale < 0.95) {
			return "Shrinking (" + String.format("%.2fx", scale) + ")";
		} else if (scale > GROW_ASCEND_THRESHOLD) {
			return "Ascend Threshold (> 2.5x)";
		} else if (scale > 1.05) {
			return "Expanding (" + String.format("%.2fx", scale) + ")";
		}
		return "Standard Scale (1.0x)";
	}

	private static void applyMacroFlightState(Player player, double targetBaseScale) {
		if (QuantumRealmDimension.isSubatomicDimension(player.level())) {
			return;
		}
		if (!player.isCreative() && !player.isSpectator()) {
			boolean shrunk = targetBaseScale < 0.95;
			if (player.getAbilities().mayfly != shrunk || (!shrunk && player.getAbilities().flying)) {
				player.getAbilities().mayfly = shrunk;
				player.getAbilities().flying = shrunk;
				player.onUpdateAbilities();
			}
		}
	}

	private static double clampScale(double value) {
		return Math.max(MIN_SCALE, Math.min(MAX_SCALE, value));
	}
}
