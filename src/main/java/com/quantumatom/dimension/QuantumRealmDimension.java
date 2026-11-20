package com.quantumatom.dimension;

import com.quantumatom.QuantumAtomMod;
import com.quantumatom.entity.AtomEntity;
import com.quantumatom.science.AtomElement;
import com.quantumatom.science.BlockChemistryMapper;
import com.quantumatom.science.BlockChemistryMapper.PlacedAtomSpec;
import com.quantumatom.science.BlockChemistryMapper.SyncedBlockContext;
import com.quantumatom.science.OrbitalSampler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Multi-dimension 4-tier subatomic scale hierarchy and realm population manager:
 * <ol>
 *   <li>{@link ScaleLayer#OVERWORLD} ({@code 10^0 m}) — Macroscopic Minecraft world</li>
 *   <li>{@link ScaleLayer#ATOMIC} ({@code quantumatom:atomic_realm}, {@code 10^-10 m}) — Electron clouds synchronized to Overworld blocks</li>
 *   <li>{@link ScaleLayer#NUCLEAR} ({@code quantumatom:nuclear_realm}, {@code 10^-15 m}) — Colossal atomic nucleus (Protons &amp; Neutrons)</li>
 *   <li>{@link ScaleLayer#QUARK} ({@code quantumatom:quark_realm}, {@code 10^-18 m}) — Interior of a Proton ({@code uud}) or Neutron ({@code udd})</li>
 * </ol>
 */
public final class QuantumRealmDimension {
	public enum ScaleLayer {
		OVERWORLD("Overworld", "10^0 m", "Macroscopic World"),
		ATOMIC("Atomic Realm", "10^-10 m", "Picometer — Electron Wave Clouds"),
		NUCLEAR("Nuclear Realm", "10^-15 m", "Femtometer — Protons & Neutrons"),
		QUARK("Quark Realm", "10^-18 m", "Attometer — Valence Quarks & Gluons");

		private final String displayName;
		private final String scaleNotation;
		private final String scaleDescription;

		ScaleLayer(String displayName, String scaleNotation, String scaleDescription) {
			this.displayName = displayName;
			this.scaleNotation = scaleNotation;
			this.scaleDescription = scaleDescription;
		}

		public String displayName() {
			return displayName;
		}

		public String scaleNotation() {
			return scaleNotation;
		}

		public String scaleDescription() {
			return scaleDescription;
		}

		public ScaleLayer deeper() {
			return switch (this) {
				case OVERWORLD -> ATOMIC;
				case ATOMIC -> NUCLEAR;
				case NUCLEAR -> QUARK;
				case QUARK -> null;
			};
		}

		public ScaleLayer shallower() {
			return switch (this) {
				case OVERWORLD -> null;
				case ATOMIC -> OVERWORLD;
				case NUCLEAR -> ATOMIC;
				case QUARK -> NUCLEAR;
			};
		}

		public static ScaleLayer fromOrdinal(int ordinal) {
			ScaleLayer[] values = values();
			if (ordinal < 0 || ordinal >= values.length) {
				return OVERWORLD;
			}
			return values[ordinal];
		}
	}

	public static final ResourceKey<Level> ATOMIC_REALM_KEY =
			ResourceKey.create(Registries.DIMENSION, QuantumAtomMod.id("atomic_realm"));
	public static final ResourceKey<Level> NUCLEAR_REALM_KEY =
			ResourceKey.create(Registries.DIMENSION, QuantumAtomMod.id("nuclear_realm"));
	public static final ResourceKey<Level> QUARK_REALM_KEY =
			ResourceKey.create(Registries.DIMENSION, QuantumAtomMod.id("quark_realm"));

	public static final ResourceKey<DimensionType> ATOMIC_REALM_TYPE_KEY =
			ResourceKey.create(Registries.DIMENSION_TYPE, QuantumAtomMod.id("atomic_realm"));
	public static final ResourceKey<DimensionType> NUCLEAR_REALM_TYPE_KEY =
			ResourceKey.create(Registries.DIMENSION_TYPE, QuantumAtomMod.id("nuclear_realm"));
	public static final ResourceKey<DimensionType> QUARK_REALM_TYPE_KEY =
			ResourceKey.create(Registries.DIMENSION_TYPE, QuantumAtomMod.id("quark_realm"));

	private static final double STALE_CLEAR_RADIUS = 140.0;
	private static final double ATOM_VERTICAL_MARGIN = 18.0;
	private static final double PLAYER_VERTICAL_MARGIN = 8.0;
	private static final double NUCLEAR_BASE_NUCLEON_RADIUS = 2.4;

	private static final float DEFAULT_FLYING_SPEED = 0.05f;
	private static final float ATOMIC_FLYING_SPEED = 0.058f;

	private static final Set<UUID> PLAYERS_IN_SUBATOMIC = ConcurrentHashMap.newKeySet();

	private QuantumRealmDimension() {
	}

	/**
	 * Returns the {@link ScaleLayer} corresponding to the given level.
	 */
	public static ScaleLayer getLayer(Level level) {
		return level != null ? getLayer(level.dimension()) : ScaleLayer.OVERWORLD;
	}

	/**
	 * Returns the {@link ScaleLayer} corresponding to the given dimension key.
	 */
	public static ScaleLayer getLayer(ResourceKey<Level> dimensionKey) {
		if (dimensionKey == null) {
			return ScaleLayer.OVERWORLD;
		}
		if (dimensionKey.equals(ATOMIC_REALM_KEY)) {
			return ScaleLayer.ATOMIC;
		}
		if (dimensionKey.equals(NUCLEAR_REALM_KEY)) {
			return ScaleLayer.NUCLEAR;
		}
		if (dimensionKey.equals(QUARK_REALM_KEY)) {
			return ScaleLayer.QUARK;
		}
		return ScaleLayer.OVERWORLD;
	}

	/**
	 * Returns {@code true} if {@code level} is any of the 3 dedicated subatomic dimensions
	 * ({@code atomic_realm}, {@code nuclear_realm}, or {@code quark_realm}).
	 */
	public static boolean isSubatomicDimension(Level level) {
		return getLayer(level) != ScaleLayer.OVERWORLD;
	}

	/**
	 * Resolves the {@link ServerLevel} for the requested {@link ScaleLayer}, or {@code null} if unavailable.
	 */
	public static ServerLevel getLevelForLayer(MinecraftServer server, ScaleLayer layer) {
		if (server == null || layer == null) {
			return null;
		}
		return switch (layer) {
			case OVERWORLD -> server.overworld();
			case ATOMIC -> server.getLevel(ATOMIC_REALM_KEY);
			case NUCLEAR -> server.getLevel(NUCLEAR_REALM_KEY);
			case QUARK -> server.getLevel(QUARK_REALM_KEY);
		};
	}

	/**
	 * Clears stale atoms around {@code destCenter} in {@code atomicLevel} and spawns a spatially
	 * synchronized 3D constellation of floating {@link AtomEntity} instances in
	 * {@link AtomEntity.RenderMode#ATOMIC_CLOUD} mirroring the exact Minecraft blocks in the
	 * {@code 3x3x3} Overworld neighborhood around {@code anchorBlockPos}.
	 */
	public static SyncedBlockContext populateAtomicRealmFromOverworld(
			ServerLevel atomicLevel,
			ServerLevel overworld,
			BlockPos anchorBlockPos,
			Vec3 destCenter
	) {
		if (atomicLevel == null || overworld == null || anchorBlockPos == null || destCenter == null) {
			return new SyncedBlockContext("Air", "Earth Atmosphere (78% N2, 21% O2)", AtomElement.NITROGEN, List.of());
		}

		clearNearbyAtoms(atomicLevel, destCenter, STALE_CLEAR_RADIUS);
		SyncedBlockContext context = BlockChemistryMapper.mapNeighborhood(overworld, anchorBlockPos);

		for (PlacedAtomSpec spec : context.atoms()) {
			Vec3 offset = spec.relativeOffset();
			double x = destCenter.x + offset.x;
			double y = clampAtomY(atomicLevel, destCenter.y + offset.y);
			double z = destCenter.z + offset.z;

			AtomEntity atom = new AtomEntity(QuantumAtomMod.ATOM_ENTITY, atomicLevel);
			atom.setPos(x, y, z);
			atom.setElement(spec.element());
			atom.setAtomScale(spec.scale());
			atom.setRenderMode(AtomEntity.RenderMode.ATOMIC_CLOUD);
			atom.setProtonInterior(false);
			atom.setSourceLabel(spec.label());
			atomicLevel.addFreshEntity(atom);
		}

		return context;
	}

	/**
	 * Clears stale entities near {@code destCenter} in {@code nuclearLevel} and spawns a colossal
	 * {@link AtomEntity} in {@link AtomEntity.RenderMode#NUCLEUS_ONLY} directly in front of
	 * {@code destCenter} (along {@code -Z}).
	 */
	public static AtomEntity populateNuclearRealm(
			ServerLevel nuclearLevel,
			Vec3 destCenter,
			AtomElement element,
			String sourceLabel
	) {
		return populateNuclearRealm(nuclearLevel, destCenter, new Vec3(0.0, 0.0, -1.0), element, sourceLabel);
	}

	/**
	 * Clears stale entities near {@code destCenter} in {@code nuclearLevel} and spawns a colossal
	 * {@link AtomEntity} in {@link AtomEntity.RenderMode#NUCLEUS_ONLY} framed cleanly ahead of
	 * the player along {@code forwardDir}.
	 */
	public static AtomEntity populateNuclearRealm(
			ServerLevel nuclearLevel,
			Vec3 destCenter,
			Vec3 forwardDir,
			AtomElement element,
			String sourceLabel
	) {
		if (nuclearLevel == null || destCenter == null) {
			return null;
		}

		AtomElement targetElement = element != null ? element : AtomElement.CARBON;
		clearNearbyAtoms(nuclearLevel, destCenter, STALE_CLEAR_RADIUS);

		float atomScale = computeNuclearScale(targetElement);
		double nucleonRadius = NUCLEAR_BASE_NUCLEON_RADIUS * atomScale;
		double clusterRadius = OrbitalSampler.getNucleusClusterRadius(targetElement, nucleonRadius);
		double forwardDist = Math.max(20.0, Math.min(46.0, clusterRadius + 16.0));

		Vec3 dir = (forwardDir != null && forwardDir.lengthSqr() > 1.0e-4)
				? forwardDir.normalize()
				: new Vec3(0.0, 0.0, -1.0);

		double spawnX = destCenter.x + dir.x * forwardDist;
		double spawnY = clampAtomY(nuclearLevel, destCenter.y + dir.y * forwardDist + 1.5);
		double spawnZ = destCenter.z + dir.z * forwardDist;

		String resolvedLabel = (sourceLabel != null && !sourceLabel.isBlank())
				? sourceLabel
				: targetElement.isotopeName() + " Nucleus";

		AtomEntity nucleus = new AtomEntity(QuantumAtomMod.ATOM_ENTITY, nuclearLevel);
		nucleus.setPos(spawnX, spawnY, spawnZ);
		nucleus.setElement(targetElement);
		nucleus.setAtomScale(atomScale);
		nucleus.setRenderMode(AtomEntity.RenderMode.NUCLEUS_ONLY);
		nucleus.setProtonInterior(true);
		nucleus.setSourceLabel(resolvedLabel);
		nuclearLevel.addFreshEntity(nucleus);
		return nucleus;
	}

	/**
	 * Clears stale entities near {@code destCenter} in {@code quarkLevel} and spawns a colossal
	 * {@link AtomEntity} in {@link AtomEntity.RenderMode#QUARK_INTERIOR} centered directly at
	 * {@code destCenter} so the player arrives inside the glowing Proton ({@code uud}) or
	 * Neutron ({@code udd}) QCD confinement cavity.
	 */
	public static AtomEntity populateQuarkRealm(
			ServerLevel quarkLevel,
			Vec3 destCenter,
			boolean isProton,
			AtomElement parentElement,
			String sourceLabel
	) {
		if (quarkLevel == null || destCenter == null) {
			return null;
		}

		AtomElement element = parentElement != null ? parentElement : AtomElement.CARBON;
		clearNearbyAtoms(quarkLevel, destCenter, STALE_CLEAR_RADIUS);

		double spawnY = clampAtomY(quarkLevel, destCenter.y);
		String resolvedLabel = (sourceLabel != null && !sourceLabel.isBlank())
				? sourceLabel
				: (isProton ? "PROTON (uud)" : "NEUTRON (udd)") + " — " + element.isotopeName();

		AtomEntity cavity = new AtomEntity(QuantumAtomMod.ATOM_ENTITY, quarkLevel);
		cavity.setPos(destCenter.x, spawnY, destCenter.z);
		cavity.setElement(element);
		cavity.setAtomScale(2.0f);
		cavity.setRenderMode(AtomEntity.RenderMode.QUARK_INTERIOR);
		cavity.setProtonInterior(isProton);
		cavity.setSourceLabel(resolvedLabel);
		quarkLevel.addFreshEntity(cavity);
		return cavity;
	}

	/**
	 * Convenience server-tick entry point ticking all online players through
	 * {@link #tickSubatomicPlayer(ServerPlayer)}.
	 */
	public static void tickServer(MinecraftServer server) {
		if (server == null) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			tickSubatomicPlayer(player);
		}
	}

	/**
	 * Grants smooth 3D zero-gravity flight ({@code mayfly}, {@code flying}, invisible slow fall,
	 * and zero fall damage) plus vertical void-bounds buoyancy while {@code player} is inside any
	 * subatomic dimension ({@code atomic_realm}, {@code nuclear_realm}, {@code quark_realm}).
	 * Cleanly restores flight state upon returning to the Overworld.
	 */
	public static void tickSubatomicPlayer(ServerPlayer player) {
		if (player == null || player.isRemoved()) {
			return;
		}

		UUID playerId = player.getUUID();
		ScaleLayer layer = getLayer(player.level());
		if (layer == ScaleLayer.OVERWORLD) {
			handleSubatomicExit(player, playerId);
			return;
		}

		boolean justEntered = PLAYERS_IN_SUBATOMIC.add(playerId);
		Abilities abilities = player.getAbilities();
		boolean abilitiesDirty = false;

		if (!player.isSpectator()) {
			if (!abilities.mayfly) {
				abilities.mayfly = true;
				abilitiesDirty = true;
			}
			if (!abilities.flying) {
				abilities.flying = true;
				abilitiesDirty = true;
			}

			float targetFlySpeed = (layer == ScaleLayer.ATOMIC) ? ATOMIC_FLYING_SPEED : DEFAULT_FLYING_SPEED;
			if (Math.abs(abilities.getFlyingSpeed() - targetFlySpeed) > 0.002f) {
				abilities.setFlyingSpeed(targetFlySpeed);
				abilitiesDirty = true;
			}
		}

		if (abilitiesDirty || justEntered) {
			player.onUpdateAbilities();
		}

		// Zero fall damage & subtle invisible slow-fall cushion
		player.resetFallDistance();
		MobEffectInstance slowFall = player.getEffect(MobEffects.SLOW_FALLING);
		if (slowFall == null || slowFall.getDuration() < 40) {
			player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 80, 0, true, false, false));
		}

		// Soft vertical buoyancy so players cannot drift into void damage below min_y or above max_y
		ServerLevel subatomicLevel = player.serverLevel();
		double lowerBound = subatomicLevel.getMinBuildHeight() + PLAYER_VERTICAL_MARGIN;
		double upperBound = subatomicLevel.getMaxBuildHeight() - PLAYER_VERTICAL_MARGIN;
		if (player.getY() < lowerBound) {
			Vec3 velocity = player.getDeltaMovement();
			player.setDeltaMovement(velocity.x, Math.max(velocity.y, 0.35), velocity.z);
			player.hurtMarked = true;
		} else if (player.getY() > upperBound) {
			Vec3 velocity = player.getDeltaMovement();
			player.setDeltaMovement(velocity.x, Math.min(velocity.y, -0.25), velocity.z);
			player.hurtMarked = true;
		}
	}

	/**
	 * Cleans up per-player tracking when a player disconnects.
	 */
	public static void onPlayerDisconnect(ServerPlayer player) {
		if (player != null) {
			PLAYERS_IN_SUBATOMIC.remove(player.getUUID());
		}
	}

	private static void handleSubatomicExit(ServerPlayer player, UUID playerId) {
		if (!PLAYERS_IN_SUBATOMIC.remove(playerId)) {
			return;
		}
		player.resetFallDistance();

		Abilities abilities = player.getAbilities();
		boolean dirty = false;
		if (Math.abs(abilities.getFlyingSpeed() - DEFAULT_FLYING_SPEED) > 0.001f) {
			abilities.setFlyingSpeed(DEFAULT_FLYING_SPEED);
			dirty = true;
		}

		if (!player.isCreative() && !player.isSpectator() && getEffectiveScale(player) >= 0.95) {
			if (abilities.mayfly || abilities.flying) {
				abilities.mayfly = false;
				abilities.flying = false;
				dirty = true;
			}
		}

		if (dirty) {
			player.onUpdateAbilities();
		}
	}

	private static void clearNearbyAtoms(ServerLevel level, Vec3 center, double radius) {
		AABB box = AABB.ofSize(center, radius * 2.0, radius * 2.0, radius * 2.0);
		List<AtomEntity> stale = level.getEntitiesOfClass(AtomEntity.class, box);
		for (AtomEntity atom : stale) {
			atom.discard();
		}
	}

	private static float computeNuclearScale(AtomElement element) {
		int nucleons = element.totalNucleons();
		if (nucleons >= 140) {
			return 1.10f;
		}
		if (nucleons >= 48) {
			return 1.35f;
		}
		return 1.65f;
	}

	private static double clampAtomY(ServerLevel level, double y) {
		double minY = level.getMinBuildHeight() + ATOM_VERTICAL_MARGIN;
		double maxY = level.getMaxBuildHeight() - ATOM_VERTICAL_MARGIN;
		return Math.max(minY, Math.min(maxY, y));
	}

	private static double getEffectiveScale(ServerPlayer player) {
		AttributeInstance attr = player.getAttribute(Attributes.SCALE);
		return attr != null ? attr.getValue() : 1.0;
	}
}
