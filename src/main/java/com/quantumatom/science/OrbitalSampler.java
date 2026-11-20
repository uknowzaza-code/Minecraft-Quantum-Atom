package com.quantumatom.science;

import com.quantumatom.science.AtomElement.OrbitalType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Quantum-mechanical orbital sampler and subatomic nucleon/quark geometry generator.
 * <p>
 * Electron positions are sampled from hydrogen-like wave-function probability densities |psi(n,l,m)|^2
 * across {@code 1s, 2s, 2p, 3s, 3p, 3d} orbitals, and nucleon clusters use concentric Fibonacci
 * spherical shell packing scaling cleanly from Hydrogen-1 (1 nucleon) to Gold-197 (197 nucleons).
 */
public final class OrbitalSampler {
	private static final double GOLDEN_ANGLE = Math.PI * (3.0 - Math.sqrt(5.0));

	/** Capacities of concentric spherical packing shells (shell 0 center, shells 1..4 outer layers). */
	private static final int[] SHELL_CAPACITIES = {1, 12, 42, 92, 162};
	private static final double SHELL_RADIUS_STEP_FACTOR = 2.28;

	private OrbitalSampler() {
	}

	public record NucleonInfo(Vec3 offset, boolean isProton, int index) {
	}

	/**
	 * Applies subtle zero-point nuclear quantum vibration to a nucleon's rest offset.
	 */
	public static Vec3 getAnimatedNucleonOffset(NucleonInfo nucleon, double timeTicks, double nucleonRadius) {
		if (nucleon.offset().lengthSqr() < 1.0e-6) {
			return Vec3.ZERO;
		}
		double amp = nucleonRadius * 0.065;
		double phase = timeTicks * 0.18 + nucleon.index() * 2.39996;
		double dx = Math.sin(phase) * amp;
		double dy = Math.cos(phase * 1.31) * amp;
		double dz = Math.sin(phase * 0.87 + 1.2) * amp;
		return nucleon.offset().add(dx, dy, dz);
	}

	/**
	 * Samples a 3D point from the probability density |psi|^2 of the given orbital.
	 */
	public static Vec3 sampleOrbital(OrbitalType type, RandomSource random, double cloudRadius) {
		double maxRadiusA0 = switch (type) {
			case S1 -> 4.5;
			case S2, PX2, PY2, PZ2 -> 9.5;
			case S3, P3 -> 15.0;
			case D3 -> 14.0;
		};
		double scaleToWorld = cloudRadius / maxRadiusA0;

		for (int attempt = 0; attempt < 36; attempt++) {
			double x = (random.nextDouble() * 2.0 - 1.0) * maxRadiusA0;
			double y = (random.nextDouble() * 2.0 - 1.0) * maxRadiusA0;
			double z = (random.nextDouble() * 2.0 - 1.0) * maxRadiusA0;
			double r = Math.sqrt(x * x + y * y + z * z);
			if (r > maxRadiusA0 || r < 0.15) {
				continue;
			}

			double prob = probabilityDensity(type, x, y, z, r);
			if (random.nextDouble() < prob) {
				return new Vec3(x * scaleToWorld, y * scaleToWorld, z * scaleToWorld);
			}
		}

		// Fallback to a mild radial point if rejection sampling didn't hit
		double theta = random.nextDouble() * Math.PI * 2.0;
		double phi = Math.acos(2.0 * random.nextDouble() - 1.0);
		double r = cloudRadius * 0.40;
		return new Vec3(
				r * Math.sin(phi) * Math.cos(theta),
				r * Math.cos(phi),
				r * Math.sin(phi) * Math.sin(theta)
		);
	}

	private static double probabilityDensity(OrbitalType type, double x, double y, double z, double r) {
		return switch (type) {
			case S1 -> {
				double psi = Math.exp(-r);
				yield Math.min(1.0, psi * psi * 1.4);
			}
			case S2 -> {
				double psi = (2.0 - r) * Math.exp(-r * 0.5);
				yield Math.min(1.0, psi * psi * 0.65);
			}
			case PX2 -> dumbbellDensity(x, r);
			case PY2 -> dumbbellDensity(y, r);
			case PZ2 -> dumbbellDensity(z, r);
			case S3 -> {
				// |psi_3s|^2 ~ ((27 - 18r + 2r^2) * exp(-r/3))^2 with two radial nodes
				double poly = (27.0 - 18.0 * r + 2.0 * r * r) / 27.0;
				double psi = poly * Math.exp(-r / 3.0);
				yield Math.min(1.0, psi * psi * 5.0);
			}
			case P3 -> {
				// 3p radial-node dumbbell combining outer nodal lobes
				double axis = Math.max(Math.abs(x), Math.max(Math.abs(y), Math.abs(z)));
				double psi = (6.0 - r) * (axis / 6.0) * Math.exp(-r / 3.0);
				yield Math.min(1.0, psi * psi * 2.2);
			}
			case D3 -> {
				// 3d_xy / 3d_yz / 3d_zx cloverleaf probability lobes
				double clover = Math.max(Math.abs(x * y), Math.max(Math.abs(y * z), Math.abs(z * x)));
				double psi = (clover / 12.0) * Math.exp(-r / 3.0);
				yield Math.min(1.0, psi * psi * 3.8);
			}
		};
	}

	private static double dumbbellDensity(double axisCoord, double r) {
		double psi = axisCoord * Math.exp(-r * 0.5);
		return Math.min(1.0, psi * psi * 1.8);
	}

	/**
	 * Returns the outer bounding radius (including nucleon sphere radius) of the packed nucleus
	 * for the given element and nucleon radius.
	 */
	public static double getNucleusClusterRadius(AtomElement element, double nucleonRadius) {
		int total = element.totalNucleons();
		if (total <= 1) {
			return nucleonRadius * 1.35;
		}
		if (total < 13) {
			double r = Math.max(nucleonRadius * 1.65, nucleonRadius * 2.15 * Math.cbrt(total) * 0.58);
			return r + nucleonRadius;
		}
		int remaining = total;
		int shellIdx = 0;
		double maxShellRadius = 0.0;
		while (remaining > 0) {
			int cap = shellCapacity(shellIdx);
			if (shellIdx > 0) {
				maxShellRadius = nucleonRadius * SHELL_RADIUS_STEP_FACTOR * shellIdx;
			}
			remaining -= Math.min(remaining, cap);
			shellIdx++;
		}
		return maxShellRadius + nucleonRadius;
	}

	/**
	 * Generates deterministic 3D positions for protons and neutrons packed inside the nucleus.
	 * Uses concentric Fibonacci shells so every nucleus from H-1 up to Au-197 packs cleanly
	 * with zero overlapping nucleons and an even surface distribution of protons &amp; neutrons.
	 */
	public static List<NucleonInfo> getNucleonLayout(AtomElement element, double nucleonRadius) {
		int total = element.totalNucleons();
		List<NucleonInfo> layout = new ArrayList<>(total);

		if (total == 1) {
			layout.add(new NucleonInfo(Vec3.ZERO, true, 0));
			return layout;
		}

		double shellStep = nucleonRadius * SHELL_RADIUS_STEP_FACTOR;
		int protonsTarget = element.protons();
		int protonsPlaced = 0;
		int neutronsPlaced = 0;
		int globalIndex = 0;

		int remaining = total;
		int shellIdx = (total >= 13) ? 0 : 1;

		while (remaining > 0) {
			int cap = shellCapacity(shellIdx);
			int count = Math.min(remaining, cap);

			double shellRadius;
			if (shellIdx == 0) {
				shellRadius = 0.0;
			} else if (shellIdx == 1 && total < 13) {
				shellRadius = Math.max(nucleonRadius * 1.65, nucleonRadius * 2.15 * Math.cbrt(total) * 0.58);
			} else {
				shellRadius = shellStep * shellIdx;
			}

			for (int i = 0; i < count; i++) {
				Vec3 pos;
				if (shellIdx == 0) {
					pos = Vec3.ZERO;
				} else {
					double t = (i + 0.5) / count;
					double y = 1.0 - 2.0 * t;
					double radiusAtY = Math.sqrt(Math.max(0.0, 1.0 - y * y));
					double theta = GOLDEN_ANGLE * i + shellIdx * 1.13;
					pos = new Vec3(
							Math.cos(theta) * radiusAtY * shellRadius,
							y * shellRadius,
							Math.sin(theta) * radiusAtY * shellRadius
					);
				}

				// Interleave protons & neutrons proportionally across every concentric shell
				boolean isProton;
				if (protonsPlaced < protonsTarget && neutronsPlaced < element.neutrons()) {
					isProton = (long) (protonsPlaced + 1) * total <= (long) protonsTarget * (globalIndex + 1);
				} else {
					isProton = protonsPlaced < protonsTarget;
				}

				if (isProton) {
					protonsPlaced++;
				} else {
					neutronsPlaced++;
				}

				layout.add(new NucleonInfo(pos, isProton, globalIndex));
				globalIndex++;
			}

			remaining -= count;
			shellIdx++;
		}

		return layout;
	}

	private static int shellCapacity(int shellIdx) {
		if (shellIdx < SHELL_CAPACITIES.length) {
			return SHELL_CAPACITIES[shellIdx];
		}
		double r = shellIdx * SHELL_RADIUS_STEP_FACTOR;
		return Math.max(12, (int) Math.floor(4.0 * Math.PI * r * r / 4.2));
	}

	/**
	 * Returns the 3 quark offsets (uud for Proton, udd for Neutron) oscillating inside a nucleon.
	 */
	public static Vec3[] getQuarkOffsets(int nucleonIndex, double timeTicks, double nucleonRadius) {
		double orbitR = nucleonRadius * 0.42;
		double phase = nucleonIndex * 1.7 + timeTicks * 0.08;
		double tilt = (nucleonIndex % 3) * 0.7;

		Vec3[] quarks = new Vec3[3];
		for (int q = 0; q < 3; q++) {
			double angle = phase + q * (Math.PI * 2.0 / 3.0);
			double x = Math.cos(angle) * orbitR;
			double y = Math.sin(angle + tilt) * orbitR * 0.65;
			double z = Math.sin(angle) * orbitR;
			quarks[q] = new Vec3(x, y, z);
		}
		return quarks;
	}
}
