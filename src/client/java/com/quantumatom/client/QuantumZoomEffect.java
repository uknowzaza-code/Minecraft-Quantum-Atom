package com.quantumatom.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.quantumatom.network.ZoomEffectPayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * Client-side cinematic fast-move zooming animation and synchronized chemistry HUD state.
 * <p>
 * Triggered by S2C {@link ZoomEffectPayload}, driving:
 * <ul>
 *   <li>Smooth accelerating/decelerating FOV hyperspace warp via {@code GameRendererFovMixin}</li>
 *   <li>60 tapered radial speed-lines streaming outward (shrink-dive) or inward (grow-ascend)</li>
 *   <li>Quantum transition flash vignette timed cleanly to the seamless dimension swap apex</li>
 *   <li>Layered pitch-sweeping WOOSH + quantum resonance client audio</li>
 * </ul>
 */
public final class QuantumZoomEffect {
	private static final int SPEED_LINE_COUNT = 60;

	private static boolean active = false;
	private static boolean shrinkingIn = true;
	private static int targetLayerOrdinal = 1;
	private static int durationTicks = 18;
	private static int swapTick = 11;
	private static int elapsedTicks = 0;
	private static boolean apexSoundPlayed = false;

	private static String syncedBlockLabel = "";
	private static String syncedChemicalFormula = "";

	private QuantumZoomEffect() {
	}

	/**
	 * Handles an incoming S2C {@link ZoomEffectPayload} on the client render/game thread.
	 */
	public static void onPayloadReceived(ZoomEffectPayload payload) {
		active = true;
		shrinkingIn = payload.shrinkingIn();
		targetLayerOrdinal = payload.targetLayerOrdinal();
		durationTicks = Math.max(8, payload.durationTicks());
		swapTick = Mth.clamp(payload.swapTick(), 2, durationTicks - 2);
		elapsedTicks = 0;
		apexSoundPlayed = false;

		if (targetLayerOrdinal == 0) {
			// Returning to the Overworld clears the subatomic synced block banner
			syncedBlockLabel = "";
			syncedChemicalFormula = "";
		} else {
			if (payload.blockLabel() != null && !payload.blockLabel().isBlank()) {
				syncedBlockLabel = payload.blockLabel();
			}
			if (payload.chemicalFormula() != null && !payload.chemicalFormula().isBlank()) {
				syncedChemicalFormula = payload.chemicalFormula();
			}
		}

		playWindupWoosh(shrinkingIn);
	}

	/**
	 * Advances the zoom animation each client tick and fires the transition-apex woosh burst.
	 */
	public static void tickClient(Minecraft client) {
		if (client == null || client.player == null || client.level == null) {
			reset();
			return;
		}
		if (!active) {
			return;
		}
		elapsedTicks++;
		if (elapsedTicks >= swapTick && !apexSoundPlayed) {
			apexSoundPlayed = true;
			playApexWoosh(shrinkingIn);
		}
		if (elapsedTicks >= durationTicks) {
			active = false;
		}
	}

	public static void reset() {
		active = false;
		elapsedTicks = 0;
		apexSoundPlayed = false;
		syncedBlockLabel = "";
		syncedChemicalFormula = "";
	}

	public static boolean isActive() {
		return active;
	}

	public static String getSyncedBlockLabel() {
		return syncedBlockLabel;
	}

	public static String getSyncedChemicalFormula() {
		return syncedChemicalFormula;
	}

	/**
	 * Computes the hyperspace FOV warp applied inside {@code GameRendererFovMixin}.
	 */
	public static double modifyFov(double baseFov, float partialTick) {
		if (!active) {
			return baseFov;
		}
		float t = elapsedTicks + partialTick;
		float intensity = computeWarpIntensity(t);
		if (intensity <= 0.001f) {
			return baseFov;
		}
		// Dramatic outward hyperspace stretch peaking at the dimension swap apex
		double maxBoost = shrinkingIn ? 42.0 : 32.0;
		double boosted = baseFov + maxBoost * intensity;
		return Mth.clamp(boosted, 30.0, 142.0);
	}

	/**
	 * Renders radial hyperspace speed-lines, tunnel vignette, and quantum apex flash over the HUD.
	 */
	public static void renderOverlay(GuiGraphics graphics, DeltaTracker deltaTracker) {
		if (!active) {
			return;
		}
		float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
		float t = elapsedTicks + partialTick;
		if (t >= durationTicks) {
			return;
		}

		float intensity = computeWarpIntensity(t);
		float distFromApex = Math.abs(t - swapTick);
		float apexFlash = (float) Math.exp(-(distFromApex * distFromApex) * 0.42) * 0.72f;
		if (intensity <= 0.01f && apexFlash <= 0.01f) {
			return;
		}

		int screenW = graphics.guiWidth();
		int screenH = graphics.guiHeight();
		float cx = screenW * 0.5f;
		float cy = screenH * 0.5f;
		float maxDiag = (float) Math.hypot(cx, cy) * 1.12f;

		int primaryRgb = primaryColorForLayer(targetLayerOrdinal);
		int secondaryRgb = secondaryColorForLayer(targetLayerOrdinal);

		Matrix4f matrix = graphics.pose().last().pose();
		VertexConsumer gui = graphics.bufferSource().getBuffer(RenderType.guiOverlay());

		// 1. Subtle cosmic tunnel edge vignette framing the hyperspace rush
		if (intensity > 0.02f) {
			int vigSegments = 24;
			float innerVignetteR = maxDiag * (0.46f + 0.20f * (1.0f - intensity));
			float vigR = ((primaryRgb >> 16) & 0xFF) / 255.0f * 0.22f;
			float vigG = ((primaryRgb >> 8) & 0xFF) / 255.0f * 0.22f;
			float vigB = (primaryRgb & 0xFF) / 255.0f * 0.28f;
			float edgeAlpha = 0.48f * intensity;

			for (int s = 0; s < vigSegments; s++) {
				float a0 = (float) (2.0 * Math.PI * s / vigSegments);
				float a1 = (float) (2.0 * Math.PI * (s + 1) / vigSegments);
				float cos0 = Mth.cos(a0);
				float sin0 = Mth.sin(a0);
				float cos1 = Mth.cos(a1);
				float sin1 = Mth.sin(a1);

				float in0x = cx + cos0 * innerVignetteR;
				float in0y = cy + sin0 * innerVignetteR;
				float in1x = cx + cos1 * innerVignetteR;
				float in1y = cy + sin1 * innerVignetteR;
				float out0x = cx + cos0 * maxDiag;
				float out0y = cy + sin0 * maxDiag;
				float out1x = cx + cos1 * maxDiag;
				float out1y = cy + sin1 * maxDiag;

				// Y-down front-facing CCW winding
				gui.addVertex(matrix, in0x, in0y, 0.0f).setColor(vigR, vigG, vigB, 0.0f);
				gui.addVertex(matrix, in1x, in1y, 0.0f).setColor(vigR, vigG, vigB, 0.0f);
				gui.addVertex(matrix, out1x, out1y, 0.0f).setColor(vigR, vigG, vigB, edgeAlpha);
				gui.addVertex(matrix, out0x, out0y, 0.0f).setColor(vigR, vigG, vigB, edgeAlpha);
			}
		}

		// 2. 60 dynamic glowing tapered radial hyperspace speed-lines
		if (intensity > 0.02f) {
			float speedDir = shrinkingIn ? 1.0f : -1.0f;
			for (int i = 0; i < SPEED_LINE_COUNT; i++) {
				float hashA = frac(Math.sin(i * 12.9898) * 43758.5453);
				float hashB = frac(Math.sin(i * 78.233 + 1.7) * 24634.6345);
				float angle = (float) (2.0 * Math.PI * (i + hashA * 0.65) / SPEED_LINE_COUNT);
				float ux = Mth.cos(angle);
				float uy = Mth.sin(angle);
				// 90-degree perpendicular in Y-down screen space
				float vx = -uy;
				float vy = ux;

				// Streaming radial motion phase
				float cycle = frac(hashB + t * (0.14f + 0.09f * hashA) * speedDir);
				float innerR = maxDiag * (0.14f + 0.50f * cycle);
				float streakLen = maxDiag * (0.24f + 0.28f * hashA) * (0.55f + 0.65f * intensity);
				float outerR = Math.min(maxDiag * 1.15f, innerR + streakLen);

				float wIn = 0.45f;
				float wOut = (1.9f + 2.3f * hashA) * (0.6f + 0.55f * intensity);

				int rgb = (i % 5 == 0) ? 0xFFFFFF : ((i % 2 == 0) ? primaryRgb : secondaryRgb);
				float r = ((rgb >> 16) & 0xFF) / 255.0f;
				float g = ((rgb >> 8) & 0xFF) / 255.0f;
				float b = (rgb & 0xFF) / 255.0f;

				float headAlpha = Mth.clamp((0.35f + 0.50f * hashB) * intensity, 0.0f, 0.90f);
				float innerAlpha = shrinkingIn ? 0.0f : headAlpha;
				float outerAlpha = shrinkingIn ? headAlpha : 0.0f;

				float ix = cx + ux * innerR;
				float iy = cy + uy * innerR;
				float ox = cx + ux * outerR;
				float oy = cy + uy * outerR;

				float x0 = ix - vx * wIn;
				float y0 = iy - vy * wIn;
				float x1 = ix + vx * wIn;
				float y1 = iy + vy * wIn;
				float x2 = ox + vx * wOut;
				float y2 = oy + vy * wOut;
				float x3 = ox - vx * wOut;
				float y3 = oy - vy * wOut;

				// Verified Y-down CCW front-facing quad order
				gui.addVertex(matrix, x0, y0, 0.0f).setColor(r, g, b, innerAlpha);
				gui.addVertex(matrix, x1, y1, 0.0f).setColor(r, g, b, innerAlpha);
				gui.addVertex(matrix, x2, y2, 0.0f).setColor(r, g, b, outerAlpha);
				gui.addVertex(matrix, x3, y3, 0.0f).setColor(r, g, b, outerAlpha);
			}
		}

		// 3. Crisp quantum barrier flash peaking at the seamless dimension-swap apex
		if (apexFlash > 0.01f) {
			float fr = Mth.lerp(0.65f, ((primaryRgb >> 16) & 0xFF) / 255.0f, 1.0f);
			float fg = Mth.lerp(0.65f, ((primaryRgb >> 8) & 0xFF) / 255.0f, 1.0f);
			float fb = Mth.lerp(0.65f, (primaryRgb & 0xFF) / 255.0f, 1.0f);

			gui.addVertex(matrix, 0.0f, 0.0f, 0.0f).setColor(fr, fg, fb, apexFlash);
			gui.addVertex(matrix, 0.0f, (float) screenH, 0.0f).setColor(fr, fg, fb, apexFlash);
			gui.addVertex(matrix, (float) screenW, (float) screenH, 0.0f).setColor(fr, fg, fb, apexFlash);
			gui.addVertex(matrix, (float) screenW, 0.0f, 0.0f).setColor(fr, fg, fb, apexFlash);
		}

		// Flush custom guiOverlay batch cleanly before any HUD text/fill calls
		graphics.flush();
	}

	private static float computeWarpIntensity(float t) {
		if (t <= 0.0f || t >= durationTicks) {
			return 0.0f;
		}
		if (t <= swapTick) {
			float u = Mth.clamp(t / Math.max(1.0f, swapTick), 0.0f, 1.0f);
			// Rapid accelerating curve building into the dimension swap apex
			return u * u * (1.8f - 0.8f * u);
		}
		float v = Mth.clamp((t - swapTick) / Math.max(1.0f, durationTicks - swapTick), 0.0f, 1.0f);
		float inv = 1.0f - v;
		// Smooth cubic ease-out on arrival in the new scale dimension
		return inv * inv * inv;
	}

	private static void playWindupWoosh(boolean inward) {
		Minecraft mc = Minecraft.getInstance();
		SoundManager sm = mc.getSoundManager();
		if (inward) {
			sm.play(SimpleSoundInstance.forUI(SoundEvents.BREEZE_INHALE, 1.38f, 0.72f));
			sm.play(SimpleSoundInstance.forUI(SoundEvents.TRIDENT_RIPTIDE_3.value(), 1.62f, 0.58f));
		} else {
			sm.play(SimpleSoundInstance.forUI(SoundEvents.BREEZE_INHALE, 0.74f, 0.68f));
			sm.play(SimpleSoundInstance.forUI(SoundEvents.TRIDENT_RIPTIDE_2.value(), 0.80f, 0.55f));
		}
	}

	private static void playApexWoosh(boolean inward) {
		Minecraft mc = Minecraft.getInstance();
		SoundManager sm = mc.getSoundManager();
		if (inward) {
			sm.play(SimpleSoundInstance.forUI(SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 1.52f, 0.82f));
			sm.play(SimpleSoundInstance.forUI(SoundEvents.ENDERMAN_TELEPORT, 1.48f, 0.44f));
			sm.play(SimpleSoundInstance.forUI(SoundEvents.BEACON_ACTIVATE, 1.85f, 0.34f));
		} else {
			sm.play(SimpleSoundInstance.forUI(SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 0.82f, 0.78f));
			sm.play(SimpleSoundInstance.forUI(SoundEvents.ENDERMAN_TELEPORT, 0.78f, 0.44f));
			sm.play(SimpleSoundInstance.forUI(SoundEvents.BEACON_DEACTIVATE, 1.18f, 0.36f));
		}
	}

	private static int primaryColorForLayer(int layerOrdinal) {
		return switch (layerOrdinal) {
			case 0 -> 0x34D399; // Overworld arrival (emerald-sky)
			case 1 -> 0x38BDF8; // Atomic Realm (electric sapphire-cyan)
			case 2 -> 0xF472B6; // Nuclear Realm (subatomic rose-violet)
			case 3 -> 0xE879F9; // Quark Realm (QCD magenta-gold)
			default -> 0x67E8F9;
		};
	}

	private static int secondaryColorForLayer(int layerOrdinal) {
		return switch (layerOrdinal) {
			case 0 -> 0x7DD3FC;
			case 1 -> 0x818CF8;
			case 2 -> 0xFBBF24;
			case 3 -> 0xFDE047;
			default -> 0xC084FC;
		};
	}

	private static float frac(double v) {
		double f = v - Math.floor(v);
		return (float) f;
	}
}
