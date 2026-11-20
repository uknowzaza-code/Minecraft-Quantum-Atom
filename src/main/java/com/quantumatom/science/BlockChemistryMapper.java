package com.quantumatom.science;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Maps real Minecraft Overworld blocks and their surrounding {@code 3x3x3} spatial neighborhood
 * to Wikipedia-verified chemical formulas and spatially synchronized 3D atomic/molecular clusters
 * for the {@code quantumatom:atomic_realm} dimension.
 */
public final class BlockChemistryMapper {
	/** Spacing in Atomic Realm blocks corresponding to a 1-block step in the Overworld 3x3x3 grid. */
	private static final double NEIGHBOR_BLOCK_SPACING = 46.0;
	private static final double NEIGHBOR_VERTICAL_SPACING = 42.0;

	/** Maximum total atoms spawned for a synchronized 3x3x3 neighborhood (keeps FPS smooth). */
	private static final int MAX_SYNCED_ATOMS = 18;

	/** Minimum clearance from the player spawn center (0,0,0) and between non-bonded clusters. */
	private static final double MIN_SPAWN_CLEARANCE_SQR = 14.0 * 14.0;
	private static final double MIN_CLUSTER_SEPARATION_SQR = 26.0 * 26.0;

	/** Exact water H-O-H bond angle (104.5 degrees -> half angle 52.25 degrees). */
	private static final double H2O_HALF_ANGLE_RAD = Math.toRadians(52.25);

	public record PlacedAtomSpec(Vec3 relativeOffset, AtomElement element, float scale, String label) {
	}

	public record SyncedBlockContext(
			String blockDisplayName,
			String chemicalFormula,
			AtomElement primaryElement,
			List<PlacedAtomSpec> atoms
	) {
	}

	private enum ChemistryKind {
		WATER("H2O (Water)", AtomElement.OXYGEN),
		ORGANIC_CHON("Organic CHON (Cellulose / Biomolecules)", AtomElement.CARBON),
		PURE_CARBON("Pure Carbon (C-12 Lattice)", AtomElement.CARBON),
		SILICATE("SiO2 (Silicate / Silicon Dioxide)", AtomElement.SILICON),
		IRON_METAL("Fe (Metallic Iron Lattice)", AtomElement.IRON),
		COPPER_METAL("Cu (Metallic Copper Lattice)", AtomElement.COPPER),
		GOLD_METAL("Au (Metallic Gold Lattice)", AtomElement.GOLD),
		CALCIUM_MINERAL("CaCO3 / Ca3(PO4)2 (Calcium Mineral)", AtomElement.CALCIUM),
		MARINE_HALITE("NaCl + SiO2 (Marine Halite / Silicate)", AtomElement.SODIUM),
		LUMINESCENT_MINERAL("SiO2 + P/S (Luminescent Mineral)", AtomElement.PHOSPHORUS),
		VOLCANIC_SILICATE("SiO2 + Fe/S (Volcanic Silicate)", AtomElement.SILICON),
		ATMOSPHERE("Earth Atmosphere (78% N2, 21% O2)", AtomElement.NITROGEN),
		DEFAULT_MINERAL("SiO2 + Fe/Ca (Mineral Composite)", AtomElement.SILICON);

		final String formula;
		final AtomElement primaryElement;

		ChemistryKind(String formula, AtomElement primaryElement) {
			this.formula = formula;
			this.primaryElement = primaryElement;
		}
	}

	private record NeighborOffset(int dx, int dy, int dz) {
		int manhattan() {
			return Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
		}
	}

	/** All 26 surrounding 3x3x3 offsets ordered face-adjacent first, then edge, then corner. */
	private static final List<NeighborOffset> NEIGHBOR_OFFSETS = buildOrderedNeighborOffsets();

	private BlockChemistryMapper() {
	}

	/**
	 * Inspects {@code anchorBlockPos} and its surrounding {@code 3x3x3} block neighborhood in
	 * {@code overworld}, producing a spatially synchronized list of floating 3D atoms whose
	 * chemistry and relative positions mirror the real blocks around the player.
	 */
	public static SyncedBlockContext mapNeighborhood(ServerLevel overworld, BlockPos anchorBlockPos) {
		BlockState anchorState = overworld.getBlockState(anchorBlockPos);
		ChemistryKind anchorKind = classifyBlock(overworld, anchorBlockPos, anchorState);
		String blockName = resolveDisplayName(anchorState);
		String anchorLabel = formatAtomLabel(blockName, anchorKind.formula);

		List<PlacedAtomSpec> placed = new ArrayList<>(MAX_SYNCED_ATOMS);
		List<Vec3> clusterCenters = new ArrayList<>();

		// 1. Place the anchor block's signature molecular/lattice cluster prominently near the arrival point
		Vec3 primaryCenter = new Vec3(0.0, 2.5, -22.0);
		addPrimaryCluster(placed, anchorKind, primaryCenter, anchorLabel);
		clusterCenters.add(primaryCenter);

		// 2. Scan the 3x3x3 neighborhood: prioritize real solid/fluid neighbors first, then atmospheric air
		List<NeighborOffset> nonAirNeighbors = new ArrayList<>();
		List<NeighborOffset> airNeighbors = new ArrayList<>();
		for (NeighborOffset off : NEIGHBOR_OFFSETS) {
			BlockPos pos = anchorBlockPos.offset(off.dx(), off.dy(), off.dz());
			BlockState state = overworld.getBlockState(pos);
			if (state.isAir() && state.getFluidState().isEmpty()) {
				airNeighbors.add(off);
			} else {
				nonAirNeighbors.add(off);
			}
		}

		// Place spatially mirrored atoms for real surrounding blocks
		for (NeighborOffset off : nonAirNeighbors) {
			if (placed.size() >= MAX_SYNCED_ATOMS) {
				break;
			}
			BlockPos pos = anchorBlockPos.offset(off.dx(), off.dy(), off.dz());
			BlockState state = overworld.getBlockState(pos);
			ChemistryKind kind = classifyBlock(overworld, pos, state);
			String label = formatAtomLabel(resolveDisplayName(state), kind.formula);
			placeNeighborCluster(placed, clusterCenters, off, pos, kind, label);
		}

		// Fill remaining spatial directions (e.g. open sky above ground blocks) with atmospheric N2/O2 pairs
		int airAdded = 0;
		for (NeighborOffset off : airNeighbors) {
			if (placed.size() >= MAX_SYNCED_ATOMS || airAdded >= 4) {
				break;
			}
			BlockPos pos = anchorBlockPos.offset(off.dx(), off.dy(), off.dz());
			String label = formatAtomLabel("Air", ChemistryKind.ATMOSPHERE.formula);
			if (placeNeighborCluster(placed, clusterCenters, off, pos, ChemistryKind.ATMOSPHERE, label)) {
				airAdded++;
			}
		}

		return new SyncedBlockContext(blockName, anchorKind.formula, anchorKind.primaryElement, List.copyOf(placed));
	}

	/**
	 * Maps a single {@link BlockState} (without world neighborhood context) to its signature molecular cluster.
	 */
	public static SyncedBlockContext mapSingleBlock(BlockState state) {
		ChemistryKind kind = classifyBlockStateOnly(state);
		String blockName = resolveDisplayName(state);
		String label = formatAtomLabel(blockName, kind.formula);
		List<PlacedAtomSpec> placed = new ArrayList<>();
		addPrimaryCluster(placed, kind, new Vec3(0.0, 2.5, -22.0), label);
		return new SyncedBlockContext(blockName, kind.formula, kind.primaryElement, List.copyOf(placed));
	}

	/**
	 * Returns the Wikipedia-verified chemical formula label for a given {@link BlockState}.
	 */
	public static String getFormulaForBlock(BlockState state) {
		return classifyBlockStateOnly(state).formula;
	}

	private static void addPrimaryCluster(
			List<PlacedAtomSpec> out,
			ChemistryKind kind,
			Vec3 center,
			String label
	) {
		switch (kind) {
			case WATER -> {
				// Iconic bent 104.5-degree H2O molecule (Oxygen vertex + 2 bonded Hydrogens)
				double bondDist = 17.5;
				double sinHalf = Math.sin(H2O_HALF_ANGLE_RAD);
				double cosHalf = Math.cos(H2O_HALF_ANGLE_RAD);
				addIfRoom(out, new PlacedAtomSpec(center, AtomElement.OXYGEN, 1.40f, label));
				addIfRoom(out, new PlacedAtomSpec(
						center.add(-bondDist * sinHalf, -bondDist * cosHalf * 0.72, bondDist * 0.25),
						AtomElement.HYDROGEN, 0.95f, label));
				addIfRoom(out, new PlacedAtomSpec(
						center.add(bondDist * sinHalf, -bondDist * cosHalf * 0.72, bondDist * 0.25),
						AtomElement.HYDROGEN, 0.95f, label));
			}
			case ORGANIC_CHON -> {
				// Cellulose / Biomolecule CHON cluster (Carbon core surrounded by O, H, N)
				addIfRoom(out, new PlacedAtomSpec(center, AtomElement.CARBON, 1.35f, label));
				addIfRoom(out, new PlacedAtomSpec(center.add(18.5, 6.0, -5.0), AtomElement.OXYGEN, 1.15f, label));
				addIfRoom(out, new PlacedAtomSpec(center.add(-17.0, 8.5, 4.0), AtomElement.HYDROGEN, 0.92f, label));
				addIfRoom(out, new PlacedAtomSpec(center.add(-4.0, -16.5, -6.0), AtomElement.NITROGEN, 1.18f, label));
			}
			case PURE_CARBON -> {
				// Tetrahedral Carbon-12 crystal lattice (diamond / graphite)
				addIfRoom(out, new PlacedAtomSpec(center, AtomElement.CARBON, 1.40f, label));
				addIfRoom(out, new PlacedAtomSpec(center.add(16.0, 12.0, -10.0), AtomElement.CARBON, 1.18f, label));
				addIfRoom(out, new PlacedAtomSpec(center.add(-16.0, 12.0, 10.0), AtomElement.CARBON, 1.18f, label));
				addIfRoom(out, new PlacedAtomSpec(center.add(0.0, -16.5, -12.0), AtomElement.CARBON, 1.18f, label));
			}
			case SILICATE -> {
				// SiO2 tetrahedral silicate unit (Silicon center + 2 bonded Oxygens)
				addIfRoom(out, new PlacedAtomSpec(center, AtomElement.SILICON, 1.42f, label));
				addIfRoom(out, new PlacedAtomSpec(center.add(-18.0, 10.0, -4.0), AtomElement.OXYGEN, 1.12f, label));
				addIfRoom(out, new PlacedAtomSpec(center.add(18.0, -9.5, -4.0), AtomElement.OXYGEN, 1.12f, label));
			}
			case IRON_METAL -> addMetalLattice(out, AtomElement.IRON, center, label);
			case COPPER_METAL -> addMetalLattice(out, AtomElement.COPPER, center, label);
			case GOLD_METAL -> addMetalLattice(out, AtomElement.GOLD, center, label);
			case CALCIUM_MINERAL -> {
				// CaCO3 / Ca3(PO4)2 mineral cluster
				addIfRoom(out, new PlacedAtomSpec(center, AtomElement.CALCIUM, 1.40f, label));
				addIfRoom(out, new PlacedAtomSpec(center.add(18.5, 7.0, -6.0), AtomElement.CARBON, 1.10f, label));
				addIfRoom(out, new PlacedAtomSpec(center.add(-18.0, 8.0, 4.0), AtomElement.OXYGEN, 1.12f, label));
				addIfRoom(out, new PlacedAtomSpec(center.add(2.0, -17.5, -8.0), AtomElement.PHOSPHORUS, 1.20f, label));
			}
			case MARINE_HALITE -> {
				// NaCl ionic cube + SiO2 coastal sand
				addIfRoom(out, new PlacedAtomSpec(center, AtomElement.SODIUM, 1.32f, label));
				addIfRoom(out, new PlacedAtomSpec(center.add(18.0, 2.0, -6.0), AtomElement.CHLORINE, 1.28f, label));
				addIfRoom(out, new PlacedAtomSpec(center.add(-18.5, -9.0, -4.0), AtomElement.SILICON, 1.22f, label));
				addIfRoom(out, new PlacedAtomSpec(center.add(-4.0, 17.5, -8.0), AtomElement.OXYGEN, 1.05f, label));
			}
			case LUMINESCENT_MINERAL -> {
				addIfRoom(out, new PlacedAtomSpec(center, AtomElement.PHOSPHORUS, 1.38f, label));
				addIfRoom(out, new PlacedAtomSpec(center.add(18.5, 7.0, -5.0), AtomElement.SULFUR, 1.25f, label));
				addIfRoom(out, new PlacedAtomSpec(center.add(-18.0, -8.0, -5.0), AtomElement.SILICON, 1.22f, label));
				addIfRoom(out, new PlacedAtomSpec(center.add(0.0, 17.5, -8.0), AtomElement.OXYGEN, 1.08f, label));
			}
			case VOLCANIC_SILICATE -> {
				addIfRoom(out, new PlacedAtomSpec(center, AtomElement.SILICON, 1.38f, label));
				addIfRoom(out, new PlacedAtomSpec(center.add(18.0, 9.0, -5.0), AtomElement.OXYGEN, 1.12f, label));
				addIfRoom(out, new PlacedAtomSpec(center.add(-18.5, 6.0, -4.0), AtomElement.IRON, 1.26f, label));
				addIfRoom(out, new PlacedAtomSpec(center.add(2.0, -17.5, -7.0), AtomElement.SULFUR, 1.18f, label));
			}
			case ATMOSPHERE -> {
				// Earth Atmosphere: 78% N2, 21% O2 — diatomic N2 and O2 pairs
				// Primary N2 diatomic pair right ahead
				addIfRoom(out, new PlacedAtomSpec(center.add(-7.5, 1.0, 0.0), AtomElement.NITROGEN, 1.22f, label));
				addIfRoom(out, new PlacedAtomSpec(center.add(7.5, -1.0, 0.0), AtomElement.NITROGEN, 1.22f, label));
				// Secondary O2 diatomic pair flanking to the right-upper quadrant
				Vec3 o2Center = new Vec3(34.0, 14.0, -12.0);
				addIfRoom(out, new PlacedAtomSpec(o2Center.add(-7.0, 2.0, 0.0), AtomElement.OXYGEN, 1.18f, label));
				addIfRoom(out, new PlacedAtomSpec(o2Center.add(7.0, -2.0, 0.0), AtomElement.OXYGEN, 1.18f, label));
			}
			case DEFAULT_MINERAL -> {
				addIfRoom(out, new PlacedAtomSpec(center, AtomElement.SILICON, 1.35f, label));
				addIfRoom(out, new PlacedAtomSpec(center.add(18.0, 8.0, -4.0), AtomElement.OXYGEN, 1.10f, label));
				addIfRoom(out, new PlacedAtomSpec(center.add(-17.5, -8.0, -4.0), AtomElement.IRON, 1.22f, label));
				addIfRoom(out, new PlacedAtomSpec(center.add(0.0, 17.5, -8.0), AtomElement.CALCIUM, 1.18f, label));
			}
		}
	}

	private static void addMetalLattice(
			List<PlacedAtomSpec> out,
			AtomElement metal,
			Vec3 center,
			String label
	) {
		addIfRoom(out, new PlacedAtomSpec(center, metal, 1.42f, label));
		addIfRoom(out, new PlacedAtomSpec(center.add(20.0, 9.0, -8.0), metal, 1.24f, label));
		addIfRoom(out, new PlacedAtomSpec(center.add(-20.0, 9.0, -8.0), metal, 1.24f, label));
		addIfRoom(out, new PlacedAtomSpec(center.add(0.0, -19.0, -10.0), metal, 1.24f, label));
	}

	private static boolean placeNeighborCluster(
			List<PlacedAtomSpec> out,
			List<Vec3> clusterCenters,
			NeighborOffset off,
			BlockPos worldPos,
			ChemistryKind kind,
			String label
	) {
		long hash = worldPos.asLong() * 0x9E3779B97F4A7C15L;
		double jx = (((hash) & 0xFF) / 255.0 - 0.5) * 6.0;
		double jy = (((hash >> 8) & 0xFF) / 255.0 - 0.5) * 5.0;
		double jz = (((hash >> 16) & 0xFF) / 255.0 - 0.5) * 6.0;

		Vec3 base = new Vec3(
				off.dx() * NEIGHBOR_BLOCK_SPACING + jx,
				off.dy() * NEIGHBOR_VERTICAL_SPACING + jy,
				off.dz() * NEIGHBOR_BLOCK_SPACING + jz
		);

		if (base.lengthSqr() < MIN_SPAWN_CLEARANCE_SQR) {
			return false;
		}
		for (Vec3 existing : clusterCenters) {
			if (base.distanceToSqr(existing) < MIN_CLUSTER_SEPARATION_SQR) {
				return false;
			}
		}

		clusterCenters.add(base);

		// Place 1-2 representative atoms for each neighbor block matching its exact chemistry
		switch (kind) {
			case WATER -> {
				addIfRoom(out, new PlacedAtomSpec(base, AtomElement.OXYGEN, 1.15f, label));
				if (out.size() < MAX_SYNCED_ATOMS) {
					double bond = 13.5;
					addIfRoom(out, new PlacedAtomSpec(
							base.add(bond * Math.sin(H2O_HALF_ANGLE_RAD), -bond * Math.cos(H2O_HALF_ANGLE_RAD), 0.0),
							AtomElement.HYDROGEN, 0.85f, label));
				}
			}
			case ATMOSPHERE -> {
				// ~75% N2, ~25% O2 across atmospheric neighbor slots
				boolean isOxygen = ((Math.abs(off.dx() * 3 + off.dy() * 5 + off.dz() * 7)) % 4) == 0;
				AtomElement gas = isOxygen ? AtomElement.OXYGEN : AtomElement.NITROGEN;
				addIfRoom(out, new PlacedAtomSpec(base.add(-6.0, 0.0, 0.0), gas, 1.05f, label));
				addIfRoom(out, new PlacedAtomSpec(base.add(6.0, 0.0, 0.0), gas, 1.05f, label));
			}
			case SILICATE -> {
				addIfRoom(out, new PlacedAtomSpec(base, AtomElement.SILICON, 1.18f, label));
				if (out.size() < MAX_SYNCED_ATOMS && off.manhattan() == 1) {
					addIfRoom(out, new PlacedAtomSpec(base.add(13.0, 6.0, 0.0), AtomElement.OXYGEN, 0.95f, label));
				}
			}
			case ORGANIC_CHON -> {
				AtomElement[] chon = {AtomElement.CARBON, AtomElement.OXYGEN, AtomElement.HYDROGEN, AtomElement.NITROGEN};
				AtomElement chosen = chon[Math.floorMod(off.dx() + off.dy() * 2 + off.dz() * 3, chon.length)];
				addIfRoom(out, new PlacedAtomSpec(base, chosen, 1.12f, label));
			}
			case CALCIUM_MINERAL -> {
				AtomElement[] elems = {AtomElement.CALCIUM, AtomElement.CARBON, AtomElement.OXYGEN, AtomElement.PHOSPHORUS};
				AtomElement chosen = elems[Math.floorMod(off.dx() + off.dy() * 3 + off.dz(), elems.length)];
				addIfRoom(out, new PlacedAtomSpec(base, chosen, 1.15f, label));
			}
			case MARINE_HALITE -> {
				boolean isCl = Math.floorMod(off.dx() + off.dy() + off.dz(), 2) == 0;
				addIfRoom(out, new PlacedAtomSpec(base, isCl ? AtomElement.CHLORINE : AtomElement.SODIUM, 1.12f, label));
			}
			case LUMINESCENT_MINERAL -> {
				boolean isS = Math.floorMod(off.dx() + off.dz(), 2) == 0;
				addIfRoom(out, new PlacedAtomSpec(base, isS ? AtomElement.SULFUR : AtomElement.PHOSPHORUS, 1.15f, label));
			}
			case VOLCANIC_SILICATE -> {
				AtomElement[] elems = {AtomElement.SILICON, AtomElement.IRON, AtomElement.SULFUR, AtomElement.OXYGEN};
				AtomElement chosen = elems[Math.floorMod(off.dx() * 2 + off.dy() + off.dz(), elems.length)];
				addIfRoom(out, new PlacedAtomSpec(base, chosen, 1.16f, label));
			}
			default -> addIfRoom(out, new PlacedAtomSpec(base, kind.primaryElement, 1.18f, label));
		}
		return true;
	}

	private static void addIfRoom(List<PlacedAtomSpec> list, PlacedAtomSpec spec) {
		if (list.size() < MAX_SYNCED_ATOMS) {
			list.add(spec);
		}
	}

	private static ChemistryKind classifyBlock(ServerLevel overworld, BlockPos pos, BlockState state) {
		// Sand directly bordering water exhibits coastal halite (NaCl + SiO2) chemistry
		if (state.is(BlockTags.SAND) && isAdjacentToWater(overworld, pos)) {
			return ChemistryKind.MARINE_HALITE;
		}
		return classifyBlockStateOnly(state);
	}

	private static boolean isAdjacentToWater(ServerLevel overworld, BlockPos pos) {
		for (Direction dir : Direction.values()) {
			if (overworld.getFluidState(pos.relative(dir)).is(FluidTags.WATER)) {
				return true;
			}
		}
		return false;
	}

	private static ChemistryKind classifyBlockStateOnly(BlockState state) {
		if (state == null || (state.isAir() && state.getFluidState().isEmpty())) {
			return ChemistryKind.ATMOSPHERE;
		}

		Block block = state.getBlock();
		String path = BuiltInRegistries.BLOCK.getKey(block).getPath().toLowerCase(Locale.ROOT);

		// 1. Water / Ice / Snow -> H2O
		if (state.getFluidState().is(FluidTags.WATER)
				|| state.is(BlockTags.ICE)
				|| state.is(BlockTags.SNOW)
				|| block == Blocks.WATER
				|| block == Blocks.BUBBLE_COLUMN
				|| path.contains("water")
				|| path.contains("ice")
				|| path.contains("snow")) {
			return ChemistryKind.WATER;
		}

		// 2. Pure Carbon (Coal / Diamond)
		if (state.is(BlockTags.COAL_ORES)
				|| state.is(BlockTags.DIAMOND_ORES)
				|| block == Blocks.COAL_BLOCK
				|| block == Blocks.DIAMOND_BLOCK
				|| path.contains("coal")
				|| path.contains("diamond")) {
			return ChemistryKind.PURE_CARBON;
		}

		// 3. Metals (Iron, Copper, Gold)
		if (state.is(BlockTags.IRON_ORES)
				|| block == Blocks.IRON_BLOCK
				|| block == Blocks.RAW_IRON_BLOCK
				|| block == Blocks.ANVIL
				|| block == Blocks.CHIPPED_ANVIL
				|| block == Blocks.DAMAGED_ANVIL
				|| block == Blocks.CAULDRON
				|| block == Blocks.HOPPER
				|| block == Blocks.CHAIN
				|| block == Blocks.BLAST_FURNACE
				|| path.contains("iron")
				|| path.contains("anvil")
				|| path.contains("cauldron")
				|| path.contains("rail")) {
			return ChemistryKind.IRON_METAL;
		}
		if (state.is(BlockTags.COPPER_ORES)
				|| block == Blocks.COPPER_BLOCK
				|| block == Blocks.RAW_COPPER_BLOCK
				|| block == Blocks.LIGHTNING_ROD
				|| path.contains("copper")) {
			return ChemistryKind.COPPER_METAL;
		}
		if (state.is(BlockTags.GOLD_ORES)
				|| block == Blocks.GOLD_BLOCK
				|| block == Blocks.RAW_GOLD_BLOCK
				|| block == Blocks.GILDED_BLACKSTONE
				|| block == Blocks.BELL
				|| path.contains("gold")) {
			return ChemistryKind.GOLD_METAL;
		}

		// 4. Calcium minerals (Bone / Calcite / Dripstone / Coral)
		if (block == Blocks.BONE_BLOCK
				|| block == Blocks.CALCITE
				|| block == Blocks.DRIPSTONE_BLOCK
				|| block == Blocks.POINTED_DRIPSTONE
				|| state.is(BlockTags.CORALS)
				|| state.is(BlockTags.WALL_CORALS)
				|| path.contains("bone")
				|| path.contains("calcite")
				|| path.contains("dripstone")
				|| path.contains("coral")) {
			return ChemistryKind.CALCIUM_MINERAL;
		}

		// 5. Marine Halite (Prismarine / Sponge)
		if (path.contains("prismarine") || path.contains("sponge")) {
			return ChemistryKind.MARINE_HALITE;
		}

		// 6. Luminescent minerals (Redstone / Glowstone / Lanterns)
		if (state.is(BlockTags.REDSTONE_ORES)
				|| block == Blocks.REDSTONE_BLOCK
				|| block == Blocks.REDSTONE_WIRE
				|| block == Blocks.GLOWSTONE
				|| block == Blocks.SEA_LANTERN
				|| block == Blocks.SHROOMLIGHT
				|| path.contains("redstone")
				|| path.contains("glowstone")
				|| path.contains("froglight")) {
			return ChemistryKind.LUMINESCENT_MINERAL;
		}

		// 7. Organic CHON (Wood / Leaves / Grass / Plants / Wool / Crops)
		if (state.is(BlockTags.LOGS)
				|| state.is(BlockTags.PLANKS)
				|| state.is(BlockTags.LEAVES)
				|| state.is(BlockTags.FLOWERS)
				|| state.is(BlockTags.SAPLINGS)
				|| state.is(BlockTags.WOOL)
				|| state.is(BlockTags.WOOL_CARPETS)
				|| state.is(BlockTags.CROPS)
				|| block == Blocks.GRASS_BLOCK
				|| block == Blocks.SHORT_GRASS
				|| block == Blocks.TALL_GRASS
				|| block == Blocks.FERN
				|| block == Blocks.LARGE_FERN
				|| block == Blocks.MOSS_BLOCK
				|| block == Blocks.MOSS_CARPET
				|| block == Blocks.VINE
				|| block == Blocks.HAY_BLOCK
				|| block == Blocks.BAMBOO
				|| block == Blocks.CACTUS
				|| block == Blocks.SUGAR_CANE
				|| block == Blocks.PUMPKIN
				|| block == Blocks.MELON
				|| block == Blocks.KELP
				|| block == Blocks.KELP_PLANT
				|| block == Blocks.SEAGRASS
				|| block == Blocks.TALL_SEAGRASS
				|| path.contains("log")
				|| path.contains("wood")
				|| path.contains("plank")
				|| path.contains("leaves")
				|| path.contains("grass")
				|| path.contains("moss")
				|| path.contains("vine")
				|| path.contains("flower")
				|| path.contains("fern")
				|| path.contains("sapling")
				|| path.contains("wool")
				|| path.contains("carpet")
				|| path.contains("bamboo")
				|| path.contains("cactus")
				|| path.contains("mushroom")
				|| path.contains("fungus")
				|| path.contains("stem")
				|| path.contains("hyphae")
				|| path.contains("pumpkin")
				|| path.contains("melon")
				|| path.contains("hay")
				|| path.contains("chest")
				|| path.contains("barrel")
				|| path.contains("bookshelf")
				|| path.contains("crafting_table")) {
			return ChemistryKind.ORGANIC_CHON;
		}

		// 8. Volcanic / Nether silicates (Obsidian / Netherrack / Basalt / Blackstone / Magma)
		if (block == Blocks.OBSIDIAN
				|| block == Blocks.CRYING_OBSIDIAN
				|| block == Blocks.BASALT
				|| block == Blocks.POLISHED_BASALT
				|| block == Blocks.SMOOTH_BASALT
				|| block == Blocks.BLACKSTONE
				|| block == Blocks.NETHERRACK
				|| block == Blocks.MAGMA_BLOCK
				|| block == Blocks.SOUL_SAND
				|| block == Blocks.SOUL_SOIL
				|| state.is(BlockTags.BASE_STONE_NETHER)
				|| path.contains("obsidian")
				|| path.contains("basalt")
				|| path.contains("blackstone")
				|| path.contains("nether")
				|| path.contains("magma")
				|| path.contains("soul_")) {
			return ChemistryKind.VOLCANIC_SILICATE;
		}

		// 9. Silicates (Stone / Cobblestone / Deepslate / Sand / Glass / Quartz / Dirt / Clay)
		if (state.is(BlockTags.BASE_STONE_OVERWORLD)
				|| state.is(BlockTags.STONE_BRICKS)
				|| state.is(BlockTags.SAND)
				|| state.is(BlockTags.DIRT)
				|| state.is(BlockTags.TERRACOTTA)
				|| state.is(BlockTags.IMPERMEABLE)
				|| block == Blocks.COBBLESTONE
				|| block == Blocks.MOSSY_COBBLESTONE
				|| block == Blocks.SMOOTH_STONE
				|| block == Blocks.GRAVEL
				|| block == Blocks.CLAY
				|| block == Blocks.MUD
				|| block == Blocks.SANDSTONE
				|| block == Blocks.RED_SANDSTONE
				|| block == Blocks.QUARTZ_BLOCK
				|| path.contains("stone")
				|| path.contains("deepslate")
				|| path.contains("granite")
				|| path.contains("diorite")
				|| path.contains("andesite")
				|| path.contains("tuff")
				|| path.contains("sand")
				|| path.contains("glass")
				|| path.contains("quartz")
				|| path.contains("gravel")
				|| path.contains("dirt")
				|| path.contains("mud")
				|| path.contains("clay")
				|| path.contains("terracotta")
				|| path.contains("concrete")
				|| path.contains("brick")
				|| path.contains("cobble")) {
			return ChemistryKind.SILICATE;
		}

		return ChemistryKind.DEFAULT_MINERAL;
	}

	private static String resolveDisplayName(BlockState state) {
		if (state == null || (state.isAir() && state.getFluidState().isEmpty())) {
			return "Air";
		}
		if (state.getFluidState().is(FluidTags.WATER) && state.isAir()) {
			return "Water";
		}
		String translated = state.getBlock().getName().getString();
		if (translated != null && !translated.isBlank() && !translated.startsWith("block.")) {
			return translated;
		}
		// Clean fallback formatting from registry path (e.g., "oak_log" -> "Oak Log")
		String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
		String[] parts = path.split("_");
		StringBuilder sb = new StringBuilder();
		for (String part : parts) {
			if (part.isEmpty()) {
				continue;
			}
			if (!sb.isEmpty()) {
				sb.append(' ');
			}
			sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
		}
		return sb.isEmpty() ? "Block" : sb.toString();
	}

	private static String formatAtomLabel(String blockName, String formula) {
		return blockName + " — " + formula;
	}

	private static List<NeighborOffset> buildOrderedNeighborOffsets() {
		List<NeighborOffset> offsets = new ArrayList<>(26);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				for (int dz = -1; dz <= 1; dz++) {
					if (dx == 0 && dy == 0 && dz == 0) {
						continue;
					}
					offsets.add(new NeighborOffset(dx, dy, dz));
				}
			}
		}
		offsets.sort(Comparator.comparingInt(NeighborOffset::manhattan)
				.thenComparingInt(o -> Math.abs(o.dy()))
				.thenComparingInt(NeighborOffset::dx)
				.thenComparingInt(NeighborOffset::dz));
		return List.copyOf(offsets);
	}
}
