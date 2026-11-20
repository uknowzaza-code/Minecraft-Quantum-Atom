package com.quantumatom.client;

import com.quantumatom.QuantumAtomMod;
import com.quantumatom.dimension.QuantumRealmDimension;
import com.quantumatom.dimension.QuantumRealmDimension.ScaleLayer;
import com.quantumatom.entity.AtomEntity;
import com.quantumatom.entity.AtomEntity.RegionInspection;
import com.quantumatom.scale.ScaleManager;
import com.quantumatom.science.AtomElement;
import com.quantumatom.science.BlockChemistryMapper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Renders the hyperspace zoom warp overlay ({@link QuantumZoomEffect}) and the multi-tier
 * scientific HUD panel across all 4 scale dimensions:
 * {@code Overworld (10^0 m) <-> Atomic Realm (10^-10 m) <-> Nuclear Realm (10^-15 m) <-> Quark Realm (10^-18 m)}.
 */
public class QuantumHudOverlay implements HudRenderCallback {
	private static final String DIVE_GROW_CONTROLS =
			"Right-Click: Shrink Deeper  |  Shift+Right-Click: Grow Back Up";
	private static final String QUARK_LIMIT_CONTROLS =
			"Fundamental Standard Model Limit  |  Shift+Right-Click: Grow Back Up";

	private record HudLine(String text, int argbColor) {
	}

	@Override
	public void onHudRender(GuiGraphics graphics, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		Player player = mc.player;
		if (player == null) {
			return;
		}

		// Always draw the hyperspace radial speed-lines & transition apex flash first (flushes cleanly)
		QuantumZoomEffect.renderOverlay(graphics, deltaTracker);

		if (mc.options.hideGui) {
			return;
		}

		ScaleLayer layer = QuantumRealmDimension.getLayer(player.level());
		double scale = ScaleManager.getScale(player);
		boolean holdingShrinker = player.getMainHandItem().is(QuantumAtomMod.QUANTUM_SHRINKER)
				|| player.getOffhandItem().is(QuantumAtomMod.QUANTUM_SHRINKER);
		Font font = mc.font;

		if (layer == ScaleLayer.OVERWORLD) {
			renderOverworldHud(graphics, font, mc, player, deltaTracker, scale, holdingShrinker);
		} else {
			renderSubatomicHud(graphics, font, player, deltaTracker, layer, scale, holdingShrinker);
		}
	}

	private void renderOverworldHud(
			GuiGraphics graphics,
			Font font,
			Minecraft mc,
			Player player,
			DeltaTracker deltaTracker,
			double scale,
			boolean holdingShrinker
	) {
		if (!holdingShrinker && scale >= 0.95) {
			return;
		}

		List<HudLine> lines = new ArrayList<>(5);
		int accent = 0x38BDF8;

		String banner = String.format(
				"OVERWORLD (%s)  |  Scale: %.3fx",
				ScaleLayer.OVERWORLD.scaleNotation(),
				scale
		);
		lines.add(new HudLine(banner, 0xFF67E8F9));

		// Live Overworld block chemistry preview when holding the Quantum Shrinker
		if (holdingShrinker) {
			BlockState targetState = resolveTargetedBlock(mc, player);
			String blockName = targetState.isAir() ? "Air" : targetState.getBlock().getName().getString();
			String formula = BlockChemistryMapper.getFormulaForBlock(targetState);
			lines.add(new HudLine("Target Matter: " + blockName + "  —  " + formula, 0xFFE2E8F0));
		}

		// If manually shrunk in the Overworld, also inspect any nearby manually spawned AtomEntity
		if (scale < 0.95) {
			Vec3 eyePos = player.getEyePosition();
			AtomEntity nearest = findNearestAtom(player, eyePos, 64.0);
			if (nearest != null && eyePos.distanceTo(nearest.position()) <= nearest.getCloudRadius() * 2.2) {
				AtomElement el = nearest.getElement();
				float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
				RegionInspection info = nearest.inspectPosition(eyePos, nearest.tickCount + partialTick);
				accent = info.accentRgb();
				lines.add(new HudLine(
						String.format("%s (%s) | Orbitals: %s | %dp+ %dn0",
								nearest.getIsotopeName(), el.symbol(), el.electronConfig(),
								el.protons(), el.neutrons()),
						0xFF94A3B8
				));
				lines.add(new HudLine(info.title() + " — " + info.detail(), 0xFF000000 | accent));
			}
		}

		if (holdingShrinker) {
			lines.add(new HudLine(DIVE_GROW_CONTROLS, 0xFF94A3B8));
		}

		drawHudPanel(graphics, font, lines, accent);
	}

	private void renderSubatomicHud(
			GuiGraphics graphics,
			Font font,
			Player player,
			DeltaTracker deltaTracker,
			ScaleLayer layer,
			double scale,
			boolean holdingShrinker
	) {
		Vec3 eyePos = player.getEyePosition();
		AtomEntity nearest = findNearestAtom(player, eyePos, 160.0);
		float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);

		int accent = layerAccentColor(layer);
		List<HudLine> lines = new ArrayList<>(5);

		// 1. Scale Layer Header Banner
		String banner = String.format(
				"%s (%s)  |  %s",
				layer.displayName().toUpperCase(Locale.ROOT),
				layer.scaleNotation(),
				layer.scaleDescription()
		);
		lines.add(new HudLine(banner, 0xFF000000 | layerHeaderColor(layer)));

		// 2. Synchronized Overworld Block & Chemical Formula
		String sourceMatter = buildSourceMatterLine(nearest);
		if (!sourceMatter.isEmpty()) {
			lines.add(new HudLine(sourceMatter, 0xFFE2E8F0));
		}

		// 3. Nearest Element / Nucleus / Quark Inspection
		if (nearest != null) {
			AtomElement el = nearest.getElement();
			double dist = eyePos.distanceTo(nearest.position());
			RegionInspection info = nearest.inspectPosition(eyePos, nearest.tickCount + partialTick);
			accent = info.accentRgb();

			String summary = switch (layer) {
				case ATOMIC -> String.format(
						"Nearest Atom: %s (%s) | Orbitals: %s | %de- | %.1fm",
						nearest.getIsotopeName(), el.symbol(), el.electronConfig(), el.protons(), dist
				);
				case NUCLEAR -> String.format(
						"Nucleus: %s (%s) | %d Protons (p+) + %d Neutrons (n0) | Scale: %.2fx",
						nearest.getIsotopeName(), el.symbol(), el.protons(), el.neutrons(), scale
				);
				case QUARK -> String.format(
						"Baryon Interior: %s of %s (%s) | Scale: %.2fx",
						nearest.isProtonInterior() ? "PROTON (uud)" : "NEUTRON (udd)",
						nearest.getIsotopeName(), el.symbol(), scale
				);
				case OVERWORLD -> "";
			};
			if (!summary.isEmpty()) {
				lines.add(new HudLine(summary, 0xFFCBD5E1));
			}
			lines.add(new HudLine(info.title() + " — " + info.detail(), 0xFF000000 | accent));
		} else {
			lines.add(new HudLine("Scanning subatomic void...", 0xFF94A3B8));
		}

		// 4. Shrink / Grow Controls
		if (holdingShrinker || layer != ScaleLayer.OVERWORLD) {
			String controls = (layer == ScaleLayer.QUARK) ? QUARK_LIMIT_CONTROLS : DIVE_GROW_CONTROLS;
			lines.add(new HudLine(controls, 0xFF94A3B8));
		}

		drawHudPanel(graphics, font, lines, accent);
	}

	private static String buildSourceMatterLine(AtomEntity nearest) {
		String blockLabel = QuantumZoomEffect.getSyncedBlockLabel();
		String formula = QuantumZoomEffect.getSyncedChemicalFormula();
		if (!blockLabel.isBlank() && !formula.isBlank()) {
			return "Synced Block: " + blockLabel + "  —  " + formula;
		}
		if (!formula.isBlank()) {
			return "Synced Matter: " + formula;
		}
		if (!blockLabel.isBlank()) {
			return "Synced Block: " + blockLabel;
		}
		if (nearest != null && !nearest.getSourceLabel().isBlank()) {
			return "Synced Matter: " + nearest.getSourceLabel();
		}
		return "";
	}

	private static BlockState resolveTargetedBlock(Minecraft mc, Player player) {
		HitResult hit = mc.hitResult;
		if (hit instanceof BlockHitResult bhr && hit.getType() == HitResult.Type.BLOCK) {
			return player.level().getBlockState(bhr.getBlockPos());
		}
		// Fallback to fluid/block at feet or directly underneath
		BlockState atPos = player.level().getBlockState(player.blockPosition());
		if (!atPos.isAir()) {
			return atPos;
		}
		return player.level().getBlockState(player.blockPosition().below());
	}

	private static int layerHeaderColor(ScaleLayer layer) {
		return switch (layer) {
			case OVERWORLD -> 0x67E8F9;
			case ATOMIC -> 0x38BDF8;
			case NUCLEAR -> 0xF472B6;
			case QUARK -> 0xE879F9;
		};
	}

	private static int layerAccentColor(ScaleLayer layer) {
		return switch (layer) {
			case OVERWORLD -> 0x38BDF8;
			case ATOMIC -> 0x38BDF8;
			case NUCLEAR -> 0xFBBF24;
			case QUARK -> 0xE879F9;
		};
	}

	private static AtomEntity findNearestAtom(Player player, Vec3 eyePos, double radius) {
		AABB searchBox = new AABB(
				eyePos.x - radius, eyePos.y - radius, eyePos.z - radius,
				eyePos.x + radius, eyePos.y + radius, eyePos.z + radius
		);
		List<AtomEntity> nearby = player.level().getEntitiesOfClass(AtomEntity.class, searchBox);
		return nearby.stream()
				.min(Comparator.comparingDouble(a -> a.position().distanceToSqr(eyePos)))
				.orElse(null);
	}

	private static void drawHudPanel(GuiGraphics graphics, Font font, List<HudLine> lines, int accentRgb) {
		if (lines.isEmpty()) {
			return;
		}
		int maxWidth = 0;
		for (HudLine line : lines) {
			maxWidth = Math.max(maxWidth, font.width(line.text()));
		}

		int x = 8;
		int y = 8;
		int lineHeight = 11;
		int boxHeight = 6 + lines.size() * lineHeight;

		graphics.fill(x - 4, y - 4, x + maxWidth + 8, y + boxHeight, 0xB8070B14);
		graphics.fill(x - 4, y - 4, x - 2, y + boxHeight, 0xFF000000 | accentRgb);
		graphics.fill(x - 2, y - 4, x + maxWidth + 8, y - 3, 0x66000000 | accentRgb);

		int rowY = y;
		for (HudLine line : lines) {
			graphics.drawString(font, line.text(), x + 1, rowY, line.argbColor(), true);
			rowY += lineHeight;
		}
	}
}
