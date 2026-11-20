package com.quantumatom.science;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Wikipedia-verified chemical elements covering biological CHON matter, atmospheric gases,
 * silicates, calcium minerals, halides, volcanic minerals, and metallic Minecraft blocks.
 */
public enum AtomElement {
	HYDROGEN(
			"H",
			"Hydrogen",
			1,
			0,
			1.008,
			31,
			"1s¹",
			0x38BDF8,
			List.of(OrbitalType.S1)
	),
	CARBON(
			"C",
			"Carbon",
			6,
			6,
			12.011,
			76,
			"1s² 2s² 2p²",
			0x94A3B8,
			List.of(OrbitalType.S1, OrbitalType.S2, OrbitalType.PX2, OrbitalType.PY2)
	),
	NITROGEN(
			"N",
			"Nitrogen",
			7,
			7,
			14.007,
			71,
			"1s² 2s² 2p³",
			0x60A5FA,
			List.of(OrbitalType.S1, OrbitalType.S2, OrbitalType.PX2, OrbitalType.PY2, OrbitalType.PZ2)
	),
	OXYGEN(
			"O",
			"Oxygen",
			8,
			8,
			15.999,
			66,
			"1s² 2s² 2p⁴",
			0xF87171,
			List.of(OrbitalType.S1, OrbitalType.S2, OrbitalType.PX2, OrbitalType.PY2, OrbitalType.PZ2)
	),
	SODIUM(
			"Na",
			"Sodium",
			11,
			12,
			22.990,
			166,
			"[Ne] 3s¹",
			0xA78BFA,
			List.of(OrbitalType.S1, OrbitalType.S2, OrbitalType.PX2, OrbitalType.PY2, OrbitalType.PZ2, OrbitalType.S3)
	),
	SILICON(
			"Si",
			"Silicon",
			14,
			14,
			28.085,
			111,
			"[Ne] 3s² 3p²",
			0x34D399,
			List.of(OrbitalType.S1, OrbitalType.S2, OrbitalType.PX2, OrbitalType.PY2, OrbitalType.PZ2, OrbitalType.S3, OrbitalType.P3)
	),
	PHOSPHORUS(
			"P",
			"Phosphorus",
			15,
			16,
			30.974,
			107,
			"[Ne] 3s² 3p³",
			0xFB923C,
			List.of(OrbitalType.S1, OrbitalType.S2, OrbitalType.PX2, OrbitalType.PY2, OrbitalType.PZ2, OrbitalType.S3, OrbitalType.P3)
	),
	SULFUR(
			"S",
			"Sulfur",
			16,
			16,
			32.06,
			105,
			"[Ne] 3s² 3p⁴",
			0xFACC15,
			List.of(OrbitalType.S1, OrbitalType.S2, OrbitalType.PX2, OrbitalType.PY2, OrbitalType.PZ2, OrbitalType.S3, OrbitalType.P3)
	),
	CHLORINE(
			"Cl",
			"Chlorine",
			17,
			18,
			35.45,
			102,
			"[Ne] 3s² 3p⁵",
			0x4ADE80,
			List.of(OrbitalType.S1, OrbitalType.S2, OrbitalType.PX2, OrbitalType.PY2, OrbitalType.PZ2, OrbitalType.S3, OrbitalType.P3)
	),
	CALCIUM(
			"Ca",
			"Calcium",
			20,
			20,
			40.078,
			176,
			"[Ar] 4s²",
			0xE2E8F0,
			List.of(OrbitalType.S1, OrbitalType.S2, OrbitalType.PX2, OrbitalType.PY2, OrbitalType.PZ2, OrbitalType.S3, OrbitalType.P3)
	),
	IRON(
			"Fe",
			"Iron",
			26,
			30,
			55.845,
			132,
			"[Ar] 3d⁶ 4s²",
			0xF97316,
			List.of(OrbitalType.S1, OrbitalType.S2, OrbitalType.PX2, OrbitalType.PY2, OrbitalType.PZ2, OrbitalType.S3, OrbitalType.P3, OrbitalType.D3)
	),
	COPPER(
			"Cu",
			"Copper",
			29,
			34,
			63.546,
			132,
			"[Ar] 3d¹⁰ 4s¹",
			0xEA580C,
			List.of(OrbitalType.S1, OrbitalType.S2, OrbitalType.PX2, OrbitalType.PY2, OrbitalType.PZ2, OrbitalType.S3, OrbitalType.P3, OrbitalType.D3)
	),
	GOLD(
			"Au",
			"Gold",
			79,
			118,
			196.967,
			136,
			"[Xe] 4f¹⁴ 5d¹⁰ 6s¹",
			0xFBBF24,
			List.of(OrbitalType.S1, OrbitalType.S2, OrbitalType.PX2, OrbitalType.PY2, OrbitalType.PZ2, OrbitalType.S3, OrbitalType.P3, OrbitalType.D3)
	);

	public enum OrbitalType {
		S1("1s", 0x38BDF8),
		S2("2s", 0x60A5FA),
		PX2("2px", 0xC084FC),
		PY2("2py", 0xE879F9),
		PZ2("2pz", 0xF472B6),
		S3("3s", 0x22D3EE),
		P3("3p", 0xA855F7),
		D3("3d", 0xF59E0B);

		private final String label;
		private final int rgbColor;

		OrbitalType(String label, int rgbColor) {
			this.label = label;
			this.rgbColor = rgbColor;
		}

		public String label() {
			return label;
		}

		public int rgbColor() {
			return rgbColor;
		}
	}

	private final String symbol;
	private final String displayName;
	private final int protons;
	private final int neutrons;
	private final double atomicMassU;
	private final int covalentRadiusPm;
	private final String electronConfig;
	private final int accentRgb;
	private final List<OrbitalType> orbitals;

	AtomElement(
			String symbol,
			String displayName,
			int protons,
			int neutrons,
			double atomicMassU,
			int covalentRadiusPm,
			String electronConfig,
			int accentRgb,
			List<OrbitalType> orbitals
	) {
		this.symbol = symbol;
		this.displayName = displayName;
		this.protons = protons;
		this.neutrons = neutrons;
		this.atomicMassU = atomicMassU;
		this.covalentRadiusPm = covalentRadiusPm;
		this.electronConfig = electronConfig;
		this.accentRgb = accentRgb;
		this.orbitals = orbitals;
	}

	public String symbol() {
		return symbol;
	}

	public String displayName() {
		return displayName;
	}

	public int protons() {
		return protons;
	}

	public int neutrons() {
		return neutrons;
	}

	public int totalNucleons() {
		return protons + neutrons;
	}

	public String isotopeName() {
		return displayName + "-" + totalNucleons();
	}

	public double atomicMassU() {
		return atomicMassU;
	}

	public int covalentRadiusPm() {
		return covalentRadiusPm;
	}

	public String electronConfig() {
		return electronConfig;
	}

	public int accentRgb() {
		return accentRgb;
	}

	public List<OrbitalType> orbitals() {
		return orbitals;
	}

	public static Optional<AtomElement> fromName(String input) {
		if (input == null) {
			return Optional.empty();
		}
		String normalized = input.trim().toUpperCase(Locale.ROOT);
		for (AtomElement element : values()) {
			if (element.name().equals(normalized)
					|| element.symbol.toUpperCase(Locale.ROOT).equals(normalized)
					|| element.displayName.toUpperCase(Locale.ROOT).equals(normalized)) {
				return Optional.of(element);
			}
		}
		return Optional.empty();
	}
}
