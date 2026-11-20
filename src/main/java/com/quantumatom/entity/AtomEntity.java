package com.quantumatom.entity;

import com.quantumatom.dimension.QuantumRealmDimension;
import com.quantumatom.dimension.QuantumRealmDimension.ScaleLayer;
import com.quantumatom.science.AtomElement;
import com.quantumatom.science.OrbitalSampler;
import com.quantumatom.science.OrbitalSampler.NucleonInfo;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;
import java.util.Locale;

/**
 * Anchor entity for a scale-isolated 3D Quantum Mechanical structure across the 4-tier
 * subatomic hierarchy:
 * <ul>
 *   <li>{@link RenderMode#ATOMIC_CLOUD} ({@code 10^-10 m}): Electron orbitals + pinpoint star nucleus</li>
 *   <li>{@link RenderMode#NUCLEUS_ONLY} ({@code 10^-15 m}): Colossal vibrating Proton/Neutron cluster + meson filaments</li>
 *   <li>{@link RenderMode#QUARK_INTERIOR} ({@code 10^-18 m}): Colossal interior Proton/Neutron cavity with 3 valence quarks &amp; gluon flux tubes</li>
 * </ul>
 */
public class AtomEntity extends Entity {
	public enum RenderMode {
		ATOMIC_CLOUD,
		NUCLEUS_ONLY,
		QUARK_INTERIOR;

		public static RenderMode fromName(String raw) {
			if (raw == null || raw.isBlank()) {
				return ATOMIC_CLOUD;
			}
			String normalized = raw.trim().toUpperCase(Locale.ROOT);
			for (RenderMode mode : values()) {
				if (mode.name().equals(normalized)) {
					return mode;
				}
			}
			return ATOMIC_CLOUD;
		}
	}

	private static final EntityDataAccessor<String> DATA_ELEMENT =
			SynchedEntityData.defineId(AtomEntity.class, EntityDataSerializers.STRING);
	private static final EntityDataAccessor<Float> DATA_SCALE =
			SynchedEntityData.defineId(AtomEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<String> DATA_RENDER_MODE =
			SynchedEntityData.defineId(AtomEntity.class, EntityDataSerializers.STRING);
	private static final EntityDataAccessor<Boolean> DATA_PROTON_INTERIOR =
			SynchedEntityData.defineId(AtomEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<String> DATA_SOURCE_LABEL =
			SynchedEntityData.defineId(AtomEntity.class, EntityDataSerializers.STRING);

	public record RegionInspection(String title, String detail, int accentRgb) {
	}

	public AtomEntity(EntityType<?> entityType, Level level) {
		super(entityType, level);
		this.noPhysics = true;
		this.setNoGravity(true);
		this.noCulling = true;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_ELEMENT, AtomElement.CARBON.name());
		builder.define(DATA_SCALE, 1.0f);
		builder.define(DATA_RENDER_MODE, RenderMode.ATOMIC_CLOUD.name());
		builder.define(DATA_PROTON_INTERIOR, true);
		builder.define(DATA_SOURCE_LABEL, "");
	}

	public ScaleLayer getScaleLayer() {
		return QuantumRealmDimension.getLayer(this.level());
	}

	public boolean isInSubatomicDimension() {
		return QuantumRealmDimension.isSubatomicDimension(this.level());
	}

	public AtomElement getElement() {
		return AtomElement.fromName(this.entityData.get(DATA_ELEMENT)).orElse(AtomElement.CARBON);
	}

	public void setElement(AtomElement element) {
		this.entityData.set(DATA_ELEMENT, (element != null ? element : AtomElement.CARBON).name());
	}

	public String getIsotopeName() {
		return getElement().isotopeName();
	}

	public float getAtomScale() {
		return Math.max(0.25f, Math.min(6.0f, this.entityData.get(DATA_SCALE)));
	}

	public void setAtomScale(float scale) {
		this.entityData.set(DATA_SCALE, Math.max(0.25f, Math.min(6.0f, scale)));
	}

	public RenderMode getRenderMode() {
		return RenderMode.fromName(this.entityData.get(DATA_RENDER_MODE));
	}

	public void setRenderMode(RenderMode mode) {
		this.entityData.set(DATA_RENDER_MODE, (mode != null ? mode : RenderMode.ATOMIC_CLOUD).name());
	}

	public boolean isProtonInterior() {
		return this.entityData.get(DATA_PROTON_INTERIOR);
	}

	public void setProtonInterior(boolean protonInterior) {
		this.entityData.set(DATA_PROTON_INTERIOR, protonInterior);
	}

	public String getSourceLabel() {
		return this.entityData.get(DATA_SOURCE_LABEL);
	}

	public void setSourceLabel(String sourceLabel) {
		this.entityData.set(DATA_SOURCE_LABEL, sourceLabel != null ? sourceLabel : "");
	}

	/**
	 * Effective bounding radius for the currently active scale render mode.
	 */
	public double getCloudRadius() {
		return switch (getRenderMode()) {
			case ATOMIC_CLOUD -> (isInSubatomicDimension() ? 16.5 : 14.0) * getAtomScale();
			case NUCLEUS_ONLY -> Math.max(24.0 * getAtomScale(), getNucleusOuterRadius() * 1.85);
			case QUARK_INTERIOR -> getQuarkCavityRadius();
		};
	}

	/**
	 * Individual nucleon radius for the active render mode.
	 * In {@link RenderMode#NUCLEUS_ONLY}, nucleons are colossal (~2.4 * atomScale) so the player
	 * can comfortably fly around the nucleus and target a specific Proton or Neutron.
	 */
	public double getNucleonRadius() {
		return switch (getRenderMode()) {
			case ATOMIC_CLOUD -> 0.55 * getAtomScale();
			case NUCLEUS_ONLY -> 2.4 * getAtomScale();
			case QUARK_INTERIOR -> getQuarkCavityRadius();
		};
	}

	/**
	 * Outer radius encompassing the packed nucleon cluster in {@link RenderMode#NUCLEUS_ONLY}.
	 */
	public double getNucleusOuterRadius() {
		double nr = getNucleonRadius();
		return OrbitalSampler.getNucleusClusterRadius(getElement(), nr) + nr;
	}

	/**
	 * Interior QCD confinement cavity dome radius for {@link RenderMode#QUARK_INTERIOR}
	 * (~43 blocks at the default Quark Realm scale of {@code 2.0f}).
	 */
	public double getQuarkCavityRadius() {
		return Math.max(26.0, Math.min(56.0, 21.5 * getAtomScale()));
	}

	private double getPhaseSeed() {
		return this.getX() * 0.13 + this.getY() * 0.29 + this.getZ() * 0.17;
	}

	/**
	 * Gentle 3D harmonic zero-gravity drift offset shared by renderer and HUD/nucleon queries.
	 * Zero in {@link RenderMode#QUARK_INTERIOR} so the enclosing cavity dome remains steady around the player.
	 */
	public Vec3 getVisualDriftOffset(double timeTicks) {
		RenderMode mode = getRenderMode();
		if (mode == RenderMode.QUARK_INTERIOR) {
			return Vec3.ZERO;
		}
		double amp = switch (mode) {
			case ATOMIC_CLOUD -> (isInSubatomicDimension() ? 0.40 : 0.10) * getAtomScale();
			case NUCLEUS_ONLY -> 0.18 * getAtomScale();
			case QUARK_INTERIOR -> 0.0;
		};
		double seed = getPhaseSeed();
		double dx = Math.sin(timeTicks * 0.012 + seed) * amp;
		double dy = Math.sin(timeTicks * 0.017 + seed * 1.618) * amp * 1.15;
		double dz = Math.cos(timeTicks * 0.010 + seed * 2.414) * amp;
		return new Vec3(dx, dy, dz);
	}

	/**
	 * Slow 3D cosmic tumble rotation shared by renderer and HUD/nucleon queries.
	 * Identity in {@link RenderMode#QUARK_INTERIOR} while quarks orbit dramatically inside.
	 */
	public Quaternionf getVisualRotation(double timeTicks) {
		RenderMode mode = getRenderMode();
		if (mode == RenderMode.QUARK_INTERIOR) {
			return new Quaternionf();
		}
		double seed = getPhaseSeed();
		double yawSpeed = (mode == RenderMode.NUCLEUS_ONLY)
				? 0.0011
				: (isInSubatomicDimension() ? 0.0024 : 0.0018);
		float yaw = (float) (timeTicks * yawSpeed + seed);
		float pitch = (float) (Math.sin(timeTicks * 0.0014 + seed * 1.3) * (mode == RenderMode.NUCLEUS_ONLY ? 0.10 : 0.18));
		float roll = (float) (Math.cos(timeTicks * 0.0010 + seed * 0.7) * (mode == RenderMode.NUCLEUS_ONLY ? 0.06 : 0.10));
		return new Quaternionf().rotateY(yaw).rotateX(pitch).rotateZ(roll);
	}

	/**
	 * Transforms a world-space point into this atom's drifted and rotated local coordinate frame.
	 */
	public Vec3 worldToAtomLocal(Vec3 worldPos, double timeTicks) {
		return worldToAtomLocal(worldPos, this.position(), timeTicks);
	}

	/**
	 * Transforms a world-space point into this atom's drifted and rotated local coordinate frame
	 * relative to the given interpolated anchor position.
	 */
	public Vec3 worldToAtomLocal(Vec3 worldPos, Vec3 anchorPos, double timeTicks) {
		Vec3 rel = worldPos.subtract(anchorPos).subtract(getVisualDriftOffset(timeTicks));
		Quaternionf inv = getVisualRotation(timeTicks).invert();
		Vector3f local = new Vector3f((float) rel.x, (float) rel.y, (float) rel.z).rotate(inv);
		return new Vec3(local.x, local.y, local.z);
	}

	/**
	 * Returns the 3 dramatic orbiting valence quark positions inside the colossal
	 * {@link RenderMode#QUARK_INTERIOR} confinement cavity (shared by renderer and HUD inspection).
	 */
	public Vec3[] getQuarkInteriorPositions(double timeTicks) {
		double cavityR = getQuarkCavityRadius();
		double orbitR = cavityR * 0.42;
		double baseAngle = timeTicks * 0.022 + (isProtonInterior() ? 0.0 : 1.05);
		Vec3[] positions = new Vec3[3];
		for (int q = 0; q < 3; q++) {
			double angle = baseAngle + q * (Math.PI * 2.0 / 3.0);
			double radialPulse = 1.0 + 0.14 * Math.sin(timeTicks * 0.055 + q * 2.094);
			double r = orbitR * radialPulse;
			double x = Math.cos(angle) * r;
			double y = Math.sin(angle * 1.35 + q * 1.1) * orbitR * 0.48;
			double z = Math.sin(angle) * r;
			positions[q] = new Vec3(x, y, z);
		}
		return positions;
	}

	/**
	 * Returns {@code true} if the nucleon closest to {@code worldPos} is a Proton (or {@link #isProtonInterior()}
	 * when already in {@link RenderMode#QUARK_INTERIOR}). Used when diving from Nuclear Realm into Quark Realm.
	 */
	public boolean isNearestNucleonProton(Vec3 worldPos) {
		if (getRenderMode() == RenderMode.QUARK_INTERIOR) {
			return isProtonInterior();
		}
		NucleonInfo nearest = findNearestNucleon(worldPos, this.tickCount);
		return nearest == null || nearest.isProton();
	}

	/**
	 * Finds the animated {@link NucleonInfo} closest to {@code worldPos} at {@code timeTicks}.
	 */
	public NucleonInfo findNearestNucleon(Vec3 worldPos, double timeTicks) {
		double nucleonRadius = getNucleonRadius();
		List<NucleonInfo> nucleons = OrbitalSampler.getNucleonLayout(getElement(), nucleonRadius);
		if (nucleons.isEmpty()) {
			return null;
		}
		Vec3 localPos = worldToAtomLocal(worldPos, timeTicks);
		NucleonInfo best = nucleons.get(0);
		double bestDistSqr = Double.MAX_VALUE;
		for (NucleonInfo nucleon : nucleons) {
			Vec3 nCenter = OrbitalSampler.getAnimatedNucleonOffset(nucleon, timeTicks, nucleonRadius);
			double d2 = localPos.distanceToSqr(nCenter);
			if (d2 < bestDistSqr) {
				bestDistSqr = d2;
				best = nucleon;
			}
		}
		return best;
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distanceSqr) {
		double maxDist = (isInSubatomicDimension() ? 420.0 : 180.0) * getAtomScale();
		return distanceSqr < maxDist * maxDist;
	}

	@Override
	public AABB getBoundingBoxForCulling() {
		double r = Math.max(getCloudRadius() * 1.65, 64.0 * getAtomScale());
		return new AABB(
				this.getX() - r, this.getY() - r, this.getZ() - r,
				this.getX() + r, this.getY() + r, this.getZ() + r
		);
	}

	/**
	 * Determines which subatomic region the given world-space position is inside
	 * for the client Quantum Inspector HUD.
	 */
	public RegionInspection inspectPosition(Vec3 pos) {
		return inspectPosition(pos, this.tickCount);
	}

	/**
	 * Determines which subatomic region the given world-space position is inside
	 * at the specified sub-tick animation time, tailored to the active {@link RenderMode}.
	 */
	public RegionInspection inspectPosition(Vec3 pos, double timeTicks) {
		AtomElement element = getElement();
		RenderMode mode = getRenderMode();
		Vec3 localPos = worldToAtomLocal(pos, timeTicks);

		return switch (mode) {
			case ATOMIC_CLOUD -> inspectAtomicCloud(element, localPos);
			case NUCLEUS_ONLY -> inspectNucleusOnly(element, localPos, timeTicks);
			case QUARK_INTERIOR -> inspectQuarkInterior(localPos, timeTicks);
		};
	}

	private RegionInspection inspectAtomicCloud(AtomElement element, Vec3 localPos) {
		double dist = localPos.length();
		double cloudR = getCloudRadius();
		double pinpointR = Math.max(2.2 * getAtomScale(), cloudR * 0.12);

		if (dist <= pinpointR) {
			return new RegionInspection(
					"Pinpoint Nucleus (" + getIsotopeName() + ")",
					"~100,000x smaller than Electron Cloud | Right-Click to dive into the Nucleus!",
					0xFBBF24
			);
		}
		if (dist <= cloudR) {
			return new RegionInspection(
					"Electron Cloud (" + element.electronConfig() + ")",
					"3D Quantum Wave Function |psi|^2 | " + element.protons() + "e- orbiting at 10^-10 m",
					0xC084FC
			);
		}
		return new RegionInspection(
				getIsotopeName() + " Atom (" + element.symbol() + ")",
				"Mass: " + element.atomicMassU() + " u | Covalent Radius: " + element.covalentRadiusPm() + " pm",
				element.accentRgb()
		);
	}

	private RegionInspection inspectNucleusOnly(AtomElement element, Vec3 localPos, double timeTicks) {
		double nucleonRadius = getNucleonRadius();
		List<NucleonInfo> nucleons = OrbitalSampler.getNucleonLayout(element, nucleonRadius);
		NucleonInfo closest = null;
		double bestDist = Double.MAX_VALUE;

		for (NucleonInfo nucleon : nucleons) {
			Vec3 nCenter = OrbitalSampler.getAnimatedNucleonOffset(nucleon, timeTicks, nucleonRadius);
			double d = localPos.distanceTo(nCenter);
			if (d < bestDist) {
				bestDist = d;
				closest = nucleon;
			}
		}

		if (closest != null && bestDist <= nucleonRadius * 2.6) {
			if (closest.isProton()) {
				return new RegionInspection(
						"Targeting PROTON (p+, +1e)",
						"Baryon (uud) | Right-Click to dive inside this Proton's Quark Realm!",
						0xF87171
				);
			} else {
				return new RegionInspection(
						"Targeting NEUTRON (n0, 0e)",
						"Baryon (udd) | Right-Click to dive inside this Neutron's Quark Realm!",
						0x60A5FA
				);
			}
		}

		return new RegionInspection(
				"Atomic Nucleus (" + getIsotopeName() + ")",
				element.protons() + "p+ / " + element.neutrons() + "n0 | Bound by Strong Nuclear Meson Exchange",
				0xFBBF24
		);
	}

	private RegionInspection inspectQuarkInterior(Vec3 localPos, double timeTicks) {
		Vec3[] quarks = getQuarkInteriorPositions(timeTicks);
		double cavityR = getQuarkCavityRadius();
		boolean proton = isProtonInterior();

		for (int q = 0; q < 3; q++) {
			if (localPos.distanceTo(quarks[q]) <= cavityR * 0.24) {
				boolean isUp = proton ? (q < 2) : (q == 0);
				if (isUp) {
					return new RegionInspection(
							"Near UP QUARK (u, +2/3e)",
							"Elementary Fermion | Bound by QCD Color Charge & Gluon Flux Tubes",
							0xFDE047
					);
				} else {
					return new RegionInspection(
							"Near DOWN QUARK (d, -1/3e)",
							"Elementary Fermion | Bound by QCD Color Charge & Gluon Flux Tubes",
							0x38BDF8
					);
				}
			}
		}

		if (proton) {
			return new RegionInspection(
					"Inside PROTON Cavity (uud)",
					"2 Up (+2/3e) + 1 Down (-1/3e) = +1e | Fundamental Standard Model Limit (10^-18 m)",
					0xF87171
			);
		}
		return new RegionInspection(
				"Inside NEUTRON Cavity (udd)",
				"1 Up (+2/3e) + 2 Down (-1/3e) = 0e | Fundamental Standard Model Limit (10^-18 m)",
				0x60A5FA
		);
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
		if (tag.contains("Element")) {
			AtomElement.fromName(tag.getString("Element")).ifPresent(this::setElement);
		}
		if (tag.contains("AtomScale")) {
			setAtomScale(tag.getFloat("AtomScale"));
		}
		if (tag.contains("RenderMode")) {
			setRenderMode(RenderMode.fromName(tag.getString("RenderMode")));
		}
		if (tag.contains("ProtonInterior")) {
			setProtonInterior(tag.getBoolean("ProtonInterior"));
		}
		if (tag.contains("SourceLabel")) {
			setSourceLabel(tag.getString("SourceLabel"));
		}
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
		tag.putString("Element", getElement().name());
		tag.putFloat("AtomScale", getAtomScale());
		tag.putString("RenderMode", getRenderMode().name());
		tag.putBoolean("ProtonInterior", isProtonInterior());
		tag.putString("SourceLabel", getSourceLabel());
	}
}
