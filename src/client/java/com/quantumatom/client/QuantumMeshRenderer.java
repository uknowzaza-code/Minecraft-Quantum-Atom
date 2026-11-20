package com.quantumatom.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.quantumatom.science.AtomElement.OrbitalType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Procedural 3D mesh renderer across all three subatomic scale realms:
 * <ul>
 *   <li>Atomic Realm ({@code 10^-10 m}): 1s/2s/3s shells, 2p/3p dumbbells, 3d cloverleaf lobes, de Broglie rings, relativistic electrons</li>
 *   <li>Nuclear Realm ({@code 10^-15 m}): Colossal subsurface-glowing Protons &amp; Neutrons + Strong Nuclear Force meson filaments</li>
 *   <li>Quark Realm ({@code 10^-18 m}): Colossal interior QCD Confinement Cavity, giant Valence Quarks, crackling Gluon Flux Tubes, vacuum sea pairs</li>
 * </ul>
 * Every method emits strictly into the caller-provided {@link VertexConsumer} without touching
 * {@code MultiBufferSource}, guaranteeing zero {@code BufferBuilder} interleaving crashes.
 */
public final class QuantumMeshRenderer {
	private QuantumMeshRenderer() {
	}

	/**
	 * Renders a 3D UV sphere with per-vertex Lambertian shading and Fresnel rim glow.
	 *
	 * @param poseStack   current pose stack
	 * @param consumer    POSITION_COLOR quad vertex consumer
	 * @param center      local center offset relative to entity origin
	 * @param radius      sphere radius
	 * @param latSegments latitude segments
	 * @param lonSegments longitude segments
	 * @param rgb         base 24-bit RGB color
	 * @param baseAlpha   interior alpha (0..1)
	 * @param rimAlpha    Fresnel grazing-edge alpha (0..1)
	 * @param cameraLocal camera position in local coordinates
	 * @param invertCull  true to wind faces inward (for interior cavity walls)
	 */
	public static void renderFresnelSphere(
			PoseStack poseStack,
			VertexConsumer consumer,
			Vec3 center,
			float radius,
			int latSegments,
			int lonSegments,
			int rgb,
			float baseAlpha,
			float rimAlpha,
			Vec3 cameraLocal,
			boolean invertCull
	) {
		if ((baseAlpha <= 0.002f && rimAlpha <= 0.002f) || radius <= 1.0e-4f) {
			return;
		}
		Matrix4f matrix = poseStack.last().pose();
		float baseR = ((rgb >> 16) & 0xFF) / 255.0f;
		float baseG = ((rgb >> 8) & 0xFF) / 255.0f;
		float baseB = (rgb & 0xFF) / 255.0f;

		for (int i = 0; i < latSegments; i++) {
			float theta0 = (float) (Math.PI * i / latSegments);
			float theta1 = (float) (Math.PI * (i + 1) / latSegments);
			float sinT0 = Mth.sin(theta0);
			float cosT0 = Mth.cos(theta0);
			float sinT1 = Mth.sin(theta1);
			float cosT1 = Mth.cos(theta1);

			for (int j = 0; j < lonSegments; j++) {
				float phi0 = (float) (2.0 * Math.PI * j / lonSegments);
				float phi1 = (float) (2.0 * Math.PI * (j + 1) / lonSegments);
				float cosP0 = Mth.cos(phi0);
				float sinP0 = Mth.sin(phi0);
				float cosP1 = Mth.cos(phi1);
				float sinP1 = Mth.sin(phi1);

				Vec3 n00 = new Vec3(sinT0 * cosP0, cosT0, sinT0 * sinP0);
				Vec3 n10 = new Vec3(sinT1 * cosP0, cosT1, sinT1 * sinP0);
				Vec3 n11 = new Vec3(sinT1 * cosP1, cosT1, sinT1 * sinP1);
				Vec3 n01 = new Vec3(sinT0 * cosP1, cosT0, sinT0 * sinP1);

				if (invertCull) {
					// Inward-facing CCW winding
					emitShadedVertex(matrix, consumer, center, n00, radius, baseR, baseG, baseB, baseAlpha, rimAlpha, cameraLocal);
					emitShadedVertex(matrix, consumer, center, n10, radius, baseR, baseG, baseB, baseAlpha, rimAlpha, cameraLocal);
					emitShadedVertex(matrix, consumer, center, n11, radius, baseR, baseG, baseB, baseAlpha, rimAlpha, cameraLocal);
					emitShadedVertex(matrix, consumer, center, n01, radius, baseR, baseG, baseB, baseAlpha, rimAlpha, cameraLocal);
				} else {
					// Outward-facing CCW winding
					emitShadedVertex(matrix, consumer, center, n00, radius, baseR, baseG, baseB, baseAlpha, rimAlpha, cameraLocal);
					emitShadedVertex(matrix, consumer, center, n01, radius, baseR, baseG, baseB, baseAlpha, rimAlpha, cameraLocal);
					emitShadedVertex(matrix, consumer, center, n11, radius, baseR, baseG, baseB, baseAlpha, rimAlpha, cameraLocal);
					emitShadedVertex(matrix, consumer, center, n10, radius, baseR, baseG, baseB, baseAlpha, rimAlpha, cameraLocal);
				}
			}
		}
	}

	/**
	 * Renders a 3D dumbbell-shaped p quantum orbital surface (2px, 2py, 2pz, or 3p)
	 * with positive/negative wave-function phase tinting across opposite lobes.
	 * Designed for double-sided translucent RenderTypes ({@code debugStructureQuads}).
	 */
	public static void renderPOrbitalLobes(
			PoseStack poseStack,
			VertexConsumer consumer,
			OrbitalType type,
			float maxLobeRadius,
			int latSegments,
			int lonSegments,
			float alphaScale,
			Vec3 cameraLocal
	) {
		if (alphaScale <= 0.005f || maxLobeRadius <= 0.05f) {
			return;
		}
		Matrix4f matrix = poseStack.last().pose();
		int rgb = type.rgbColor();
		float baseR = ((rgb >> 16) & 0xFF) / 255.0f;
		float baseG = ((rgb >> 8) & 0xFF) / 255.0f;
		float baseB = (rgb & 0xFF) / 255.0f;

		// Opposite quantum wave-function phase (+/- psi) subtle color contrast
		float posR = Mth.clamp(baseR * 1.12f, 0.0f, 1.0f);
		float posG = Mth.clamp(baseG * 1.04f, 0.0f, 1.0f);
		float posB = Mth.clamp(baseB * 0.96f, 0.0f, 1.0f);

		float negR = Mth.clamp(baseR * 0.86f, 0.0f, 1.0f);
		float negG = Mth.clamp(baseG * 1.02f, 0.0f, 1.0f);
		float negB = Mth.clamp(baseB * 1.18f, 0.0f, 1.0f);

		float baseA = 0.042f * alphaScale;
		float rimA = 0.21f * alphaScale;

		for (int i = 0; i < latSegments; i++) {
			float theta0 = (float) (Math.PI * i / latSegments);
			float theta1 = (float) (Math.PI * (i + 1) / latSegments);

			for (int j = 0; j < lonSegments; j++) {
				float phi0 = (float) (2.0 * Math.PI * j / lonSegments);
				float phi1 = (float) (2.0 * Math.PI * (j + 1) / lonSegments);

				Vec3 d00 = unitDir(theta0, phi0);
				Vec3 d10 = unitDir(theta1, phi0);
				Vec3 d11 = unitDir(theta1, phi1);
				Vec3 d01 = unitDir(theta0, phi1);

				float r00 = pLobeRadius(type, d00, maxLobeRadius);
				float r10 = pLobeRadius(type, d10, maxLobeRadius);
				float r11 = pLobeRadius(type, d11, maxLobeRadius);
				float r01 = pLobeRadius(type, d01, maxLobeRadius);

				if (r00 < 0.05f && r10 < 0.05f && r11 < 0.05f && r01 < 0.05f) {
					continue;
				}

				double avgAxis = (axisProjection(type, d00) + axisProjection(type, d10)
						+ axisProjection(type, d11) + axisProjection(type, d01)) * 0.25;
				float r = avgAxis >= 0.0 ? posR : negR;
				float g = avgAxis >= 0.0 ? posG : negG;
				float b = avgAxis >= 0.0 ? posB : negB;

				emitShadedVertex(matrix, consumer, Vec3.ZERO, d00, r00, r, g, b, baseA, rimA, cameraLocal);
				emitShadedVertex(matrix, consumer, Vec3.ZERO, d01, r01, r, g, b, baseA, rimA, cameraLocal);
				emitShadedVertex(matrix, consumer, Vec3.ZERO, d11, r11, r, g, b, baseA, rimA, cameraLocal);
				emitShadedVertex(matrix, consumer, Vec3.ZERO, d10, r10, r, g, b, baseA, rimA, cameraLocal);
			}
		}
	}

	/**
	 * Renders a 3D four-leaf cloverleaf {@code 3d} quantum orbital surface ({@code d_xy} &amp; {@code d_z2} hybrid)
	 * for transition metals (Iron, Copper, Gold) with alternating +/- wave-function phase lobes.
	 */
	public static void render3dOrbitalLobes(
			PoseStack poseStack,
			VertexConsumer consumer,
			int rgb,
			float maxLobeRadius,
			int latSegments,
			int lonSegments,
			float alphaScale,
			Vec3 cameraLocal
	) {
		if (alphaScale <= 0.005f || maxLobeRadius <= 0.05f) {
			return;
		}
		Matrix4f matrix = poseStack.last().pose();
		float baseR = ((rgb >> 16) & 0xFF) / 255.0f;
		float baseG = ((rgb >> 8) & 0xFF) / 255.0f;
		float baseB = (rgb & 0xFF) / 255.0f;

		float posR = Mth.clamp(baseR * 1.15f, 0.0f, 1.0f);
		float posG = Mth.clamp(baseG * 1.05f, 0.0f, 1.0f);
		float posB = Mth.clamp(baseB * 0.90f, 0.0f, 1.0f);

		float negR = Mth.clamp(baseR * 0.85f, 0.0f, 1.0f);
		float negG = Mth.clamp(baseG * 0.98f, 0.0f, 1.0f);
		float negB = Mth.clamp(baseB * 1.18f, 0.0f, 1.0f);

		float baseA = 0.038f * alphaScale;
		float rimA = 0.20f * alphaScale;

		for (int i = 0; i < latSegments; i++) {
			float theta0 = (float) (Math.PI * i / latSegments);
			float theta1 = (float) (Math.PI * (i + 1) / latSegments);

			for (int j = 0; j < lonSegments; j++) {
				float phi0 = (float) (2.0 * Math.PI * j / lonSegments);
				float phi1 = (float) (2.0 * Math.PI * (j + 1) / lonSegments);

				Vec3 d00 = unitDir(theta0, phi0);
				Vec3 d10 = unitDir(theta1, phi0);
				Vec3 d11 = unitDir(theta1, phi1);
				Vec3 d01 = unitDir(theta0, phi1);

				double y00 = dCloverHarmonic(d00);
				double y10 = dCloverHarmonic(d10);
				double y11 = dCloverHarmonic(d11);
				double y01 = dCloverHarmonic(d01);

				float r00 = dLobeRadius(y00, maxLobeRadius);
				float r10 = dLobeRadius(y10, maxLobeRadius);
				float r11 = dLobeRadius(y11, maxLobeRadius);
				float r01 = dLobeRadius(y01, maxLobeRadius);

				if (r00 < 0.05f && r10 < 0.05f && r11 < 0.05f && r01 < 0.05f) {
					continue;
				}

				double avgSign = (y00 + y10 + y11 + y01) * 0.25;
				float r = avgSign >= 0.0 ? posR : negR;
				float g = avgSign >= 0.0 ? posG : negG;
				float b = avgSign >= 0.0 ? posB : negB;

				emitShadedVertex(matrix, consumer, Vec3.ZERO, d00, r00, r, g, b, baseA, rimA, cameraLocal);
				emitShadedVertex(matrix, consumer, Vec3.ZERO, d01, r01, r, g, b, baseA, rimA, cameraLocal);
				emitShadedVertex(matrix, consumer, Vec3.ZERO, d11, r11, r, g, b, baseA, rimA, cameraLocal);
				emitShadedVertex(matrix, consumer, Vec3.ZERO, d10, r10, r, g, b, baseA, rimA, cameraLocal);
			}
		}
	}

	/**
	 * Renders a luminous 3D de Broglie standing-wave ring along a tilted orbital plane.
	 * Built as a closed 4-sided tubular loop so it remains visible from every 3D angle
	 * under back-face-culled additive {@code RenderType.lightning()}.
	 */
	public static void renderOrbitalWaveRing(
			PoseStack poseStack,
			VertexConsumer consumer,
			float radius,
			float tubeRadius,
			int waveNodes,
			float waveAmpRatio,
			float tiltXRad,
			float tiltZRad,
			float time,
			float phaseOffset,
			int rgb,
			float alpha
	) {
		if (alpha <= 0.004f || radius <= 0.05f || tubeRadius <= 1.0e-4f) {
			return;
		}
		Matrix4f matrix = poseStack.last().pose();
		float baseR = ((rgb >> 16) & 0xFF) / 255.0f;
		float baseG = ((rgb >> 8) & 0xFF) / 255.0f;
		float baseB = (rgb & 0xFF) / 255.0f;

		float cosX = Mth.cos(tiltXRad);
		float sinX = Mth.sin(tiltXRad);
		float cosZ = Mth.cos(tiltZRad);
		float sinZ = Mth.sin(tiltZRad);

		Vec3 basisU = new Vec3(cosZ, sinZ * cosX, sinZ * sinX).normalize();
		Vec3 basisN = new Vec3(-sinZ, cosZ * cosX, cosZ * sinX).normalize();
		Vec3 basisV = basisN.cross(basisU).normalize();

		int segments = 40;
		int radialSides = 4;

		for (int s = 0; s < segments; s++) {
			float a0 = (float) (2.0 * Math.PI * s / segments);
			float a1 = (float) (2.0 * Math.PI * (s + 1) / segments);

			Vec3 c0 = waveRingPoint(basisU, basisV, basisN, radius, waveNodes, waveAmpRatio, a0, time, phaseOffset);
			Vec3 c1 = waveRingPoint(basisU, basisV, basisN, radius, waveNodes, waveAmpRatio, a1, time, phaseOffset);

			Vec3 segAxis = c1.subtract(c0);
			double segLen = segAxis.length();
			if (segLen < 1.0e-5) {
				continue;
			}
			Vec3 forward = segAxis.scale(1.0 / segLen);
			Vec3 radialOut = c0.normalize();
			Vec3 sideA = forward.cross(radialOut);
			if (sideA.lengthSqr() < 1.0e-5) {
				sideA = basisN;
			} else {
				sideA = sideA.normalize();
			}
			Vec3 sideB = sideA.cross(forward).normalize();

			float pulse0 = 0.72f + 0.28f * Mth.sin(a0 * waveNodes + time * 0.08f + phaseOffset);
			float pulse1 = 0.72f + 0.28f * Mth.sin(a1 * waveNodes + time * 0.08f + phaseOffset);
			float aFade0 = Mth.clamp(alpha * pulse0, 0.0f, 1.0f);
			float aFade1 = Mth.clamp(alpha * pulse1, 0.0f, 1.0f);

			for (int k = 0; k < radialSides; k++) {
				float ra0 = (float) (2.0 * Math.PI * k / radialSides);
				float ra1 = (float) (2.0 * Math.PI * (k + 1) / radialSides);

				Vec3 rDir0 = sideA.scale(Mth.cos(ra0)).add(sideB.scale(Mth.sin(ra0)));
				Vec3 rDir1 = sideA.scale(Mth.cos(ra1)).add(sideB.scale(Mth.sin(ra1)));

				Vec3 p00 = c0.add(rDir0.scale(tubeRadius));
				Vec3 p10 = c1.add(rDir0.scale(tubeRadius));
				Vec3 p11 = c1.add(rDir1.scale(tubeRadius));
				Vec3 p01 = c0.add(rDir1.scale(tubeRadius));

				consumer.addVertex(matrix, (float) p00.x, (float) p00.y, (float) p00.z).setColor(baseR, baseG, baseB, aFade0);
				consumer.addVertex(matrix, (float) p10.x, (float) p10.y, (float) p10.z).setColor(baseR, baseG, baseB, aFade1);
				consumer.addVertex(matrix, (float) p11.x, (float) p11.y, (float) p11.z).setColor(baseR, baseG, baseB, aFade1);
				consumer.addVertex(matrix, (float) p01.x, (float) p01.y, (float) p01.z).setColor(baseR, baseG, baseB, aFade0);
			}
		}
	}

	/**
	 * Renders an undulating 3D cylindrical gluon flux tube (or Strong Nuclear Force meson exchange filament)
	 * between two 3D points with traveling plasma pulses.
	 */
	public static void renderGluonFluxTube(
			PoseStack poseStack,
			VertexConsumer consumer,
			Vec3 start,
			Vec3 end,
			float tubeRadius,
			float time,
			int seed,
			int rgb,
			float alpha,
			int lengthSteps
	) {
		if (alpha <= 0.005f || tubeRadius <= 1.0e-4f) {
			return;
		}
		Vec3 axis = end.subtract(start);
		double len = axis.length();
		if (len < 1.0e-4) {
			return;
		}
		Vec3 forward = axis.scale(1.0 / len);
		Vec3 upRef = Math.abs(forward.y) < 0.9 ? new Vec3(0.0, 1.0, 0.0) : new Vec3(1.0, 0.0, 0.0);
		Vec3 right = forward.cross(upRef).normalize();
		Vec3 up = right.cross(forward).normalize();

		Matrix4f matrix = poseStack.last().pose();
		float baseR = ((rgb >> 16) & 0xFF) / 255.0f;
		float baseG = ((rgb >> 8) & 0xFF) / 255.0f;
		float baseB = (rgb & 0xFF) / 255.0f;

		int steps = Math.max(4, lengthSteps);
		int radialSides = 6;
		double waveAmp = tubeRadius * 1.25;

		for (int s = 0; s < steps; s++) {
			float t0 = (float) s / steps;
			float t1 = (float) (s + 1) / steps;

			Vec3 c0 = tubeCenterPoint(start, end, right, up, t0, time, seed, waveAmp);
			Vec3 c1 = tubeCenterPoint(start, end, right, up, t1, time, seed, waveAmp);

			// Traveling gluon/meson energy pulses along the tube
			float pulse0 = 0.72f + 0.28f * Mth.sin(time * 0.40f - t0 * 9.42f + seed);
			float pulse1 = 0.72f + 0.28f * Mth.sin(time * 0.40f - t1 * 9.42f + seed);
			float r0 = tubeRadius * pulse0;
			float r1 = tubeRadius * pulse1;

			float bright0 = 0.82f + 0.25f * pulse0;
			float bright1 = 0.82f + 0.25f * pulse1;
			float cr0 = Mth.clamp(baseR * bright0, 0.0f, 1.0f);
			float cg0 = Mth.clamp(baseG * bright0, 0.0f, 1.0f);
			float cb0 = Mth.clamp(baseB * bright0, 0.0f, 1.0f);
			float cr1 = Mth.clamp(baseR * bright1, 0.0f, 1.0f);
			float cg1 = Mth.clamp(baseG * bright1, 0.0f, 1.0f);
			float cb1 = Mth.clamp(baseB * bright1, 0.0f, 1.0f);

			for (int k = 0; k < radialSides; k++) {
				float a0 = (float) (2.0 * Math.PI * k / radialSides);
				float a1 = (float) (2.0 * Math.PI * (k + 1) / radialSides);

				Vec3 rad0 = right.scale(Mth.cos(a0)).add(up.scale(Mth.sin(a0)));
				Vec3 rad1 = right.scale(Mth.cos(a1)).add(up.scale(Mth.sin(a1)));

				Vec3 p00 = c0.add(rad0.scale(r0));
				Vec3 p10 = c1.add(rad0.scale(r1));
				Vec3 p11 = c1.add(rad1.scale(r1));
				Vec3 p01 = c0.add(rad1.scale(r0));

				consumer.addVertex(matrix, (float) p00.x, (float) p00.y, (float) p00.z).setColor(cr0, cg0, cb0, alpha);
				consumer.addVertex(matrix, (float) p10.x, (float) p10.y, (float) p10.z).setColor(cr1, cg1, cb1, alpha);
				consumer.addVertex(matrix, (float) p11.x, (float) p11.y, (float) p11.z).setColor(cr1, cg1, cb1, alpha);
				consumer.addVertex(matrix, (float) p01.x, (float) p01.y, (float) p01.z).setColor(cr0, cg0, cb0, alpha);
			}
		}
	}

	/**
	 * Renders a relativistic 3D electron wave-packet (glowing core + halo + sleek tapered 3D motion trail)
	 * directly into the caller's additive {@code RenderType.lightning()} consumer.
	 */
	public static void renderElectronWavePacket(
			PoseStack poseStack,
			VertexConsumer lightningConsumer,
			OrbitalType orbital,
			int electronIndex,
			float cloudRadius,
			float time,
			float alphaScale,
			Vec3 cameraLocal
	) {
		if (alphaScale <= 0.01f) {
			return;
		}
		int rgb = orbital.rgbColor();
		Vec3 currentPos = electronPosition(orbital, electronIndex, cloudRadius, time);

		float coreRadius = Math.max(0.08f, cloudRadius * 0.013f);
		renderFresnelSphere(
				poseStack, lightningConsumer, currentPos, coreRadius,
				8, 10, 0xFFFFFF, 0.90f * alphaScale, 1.0f * alphaScale, cameraLocal, false
		);
		renderFresnelSphere(
				poseStack, lightningConsumer, currentPos, coreRadius * 2.6f,
				8, 10, rgb, 0.25f * alphaScale, 0.55f * alphaScale, cameraLocal, false
		);

		int trailSteps = 12;
		Vec3 prev = currentPos;
		float prevFade = alphaScale;
		for (int step = 1; step <= trailSteps; step++) {
			float pastTime = time - step * 0.30f;
			Vec3 next = electronPosition(orbital, electronIndex, cloudRadius, pastTime);
			float nextFade = (1.0f - (float) step / (trailSteps + 1)) * alphaScale;
			renderTaperedTrailSegment(
					poseStack,
					lightningConsumer,
					prev,
					next,
					coreRadius * 0.62f * prevFade,
					coreRadius * 0.62f * nextFade,
					rgb,
					0.55f * prevFade,
					0.55f * nextFade
			);
			prev = next;
			prevFade = nextFade;
		}
	}

	/**
	 * Renders a tiny luminous 3D diamond/octahedron quantum vacuum fluctuation sparkle.
	 */
	public static void renderQuantumSparkle(
			PoseStack poseStack,
			VertexConsumer consumer,
			Vec3 center,
			float radius,
			int rgb,
			float alpha
	) {
		if (alpha <= 0.01f || radius <= 1.0e-4f) {
			return;
		}
		Matrix4f matrix = poseStack.last().pose();
		float r = ((rgb >> 16) & 0xFF) / 255.0f;
		float g = ((rgb >> 8) & 0xFF) / 255.0f;
		float b = (rgb & 0xFF) / 255.0f;

		float cx = (float) center.x;
		float cy = (float) center.y;
		float cz = (float) center.z;
		float s = radius;

		emitDiamondQuad(matrix, consumer, cx, cy, cz, s, 0, 0, 0, s, 0, r, g, b, alpha);
		emitDiamondQuad(matrix, consumer, cx, cy, cz, 0, s, 0, 0, 0, s, r, g, b, alpha);
		emitDiamondQuad(matrix, consumer, cx, cy, cz, 0, 0, s, s, 0, 0, r, g, b, alpha);
	}

	/**
	 * Renders subtle glowing geodesic energy field rings hugging the interior of the
	 * colossal {@code QUARK_INTERIOR} QCD confinement dome.
	 */
	public static void renderConfinementFieldHoops(
			PoseStack poseStack,
			VertexConsumer lightningConsumer,
			float cavityRadius,
			float time,
			int rgb,
			float alpha
	) {
		float r = cavityRadius * 0.96f;
		float tube = Math.max(0.08f, cavityRadius * 0.0045f);
		renderOrbitalWaveRing(poseStack, lightningConsumer, r, tube, 6, 0.014f, 0.0f, 0.0f, time * 0.5f, 0.0f, rgb, alpha);
		renderOrbitalWaveRing(poseStack, lightningConsumer, r, tube, 6, 0.014f, (float) (Math.PI / 3.0), 0.35f, time * 0.5f, 2.09f, rgb, alpha * 0.85f);
		renderOrbitalWaveRing(poseStack, lightningConsumer, r, tube, 6, 0.014f, (float) (-Math.PI / 3.0), -0.35f, time * 0.5f, 4.18f, rgb, alpha * 0.85f);
	}

	private static void emitDiamondQuad(
			Matrix4f matrix,
			VertexConsumer consumer,
			float cx, float cy, float cz,
			float ux, float uy, float uz,
			float vx, float vy, float vz,
			float r, float g, float b, float alpha
	) {
		consumer.addVertex(matrix, cx + ux, cy + uy, cz + uz).setColor(r, g, b, alpha);
		consumer.addVertex(matrix, cx + vx, cy + vy, cz + vz).setColor(r, g, b, alpha);
		consumer.addVertex(matrix, cx - ux, cy - uy, cz - uz).setColor(r, g, b, alpha);
		consumer.addVertex(matrix, cx - vx, cy - vy, cz - vz).setColor(r, g, b, alpha);

		consumer.addVertex(matrix, cx + ux, cy + uy, cz + uz).setColor(r, g, b, alpha);
		consumer.addVertex(matrix, cx - vx, cy - vy, cz - vz).setColor(r, g, b, alpha);
		consumer.addVertex(matrix, cx - ux, cy - uy, cz - uz).setColor(r, g, b, alpha);
		consumer.addVertex(matrix, cx + vx, cy + vy, cz + vz).setColor(r, g, b, alpha);
	}

	private static void renderTaperedTrailSegment(
			PoseStack poseStack,
			VertexConsumer consumer,
			Vec3 start,
			Vec3 end,
			float rStart,
			float rEnd,
			int rgb,
			float alphaStart,
			float alphaEnd
	) {
		if (alphaStart <= 0.004f && alphaEnd <= 0.004f) {
			return;
		}
		Vec3 axis = end.subtract(start);
		double len = axis.length();
		if (len < 1.0e-4) {
			return;
		}
		Vec3 forward = axis.scale(1.0 / len);
		Vec3 upRef = Math.abs(forward.y) < 0.9 ? new Vec3(0.0, 1.0, 0.0) : new Vec3(1.0, 0.0, 0.0);
		Vec3 right = forward.cross(upRef).normalize();
		Vec3 up = right.cross(forward).normalize();

		Matrix4f matrix = poseStack.last().pose();
		float baseR = ((rgb >> 16) & 0xFF) / 255.0f;
		float baseG = ((rgb >> 8) & 0xFF) / 255.0f;
		float baseB = (rgb & 0xFF) / 255.0f;

		int radialSides = 5;
		for (int k = 0; k < radialSides; k++) {
			float a0 = (float) (2.0 * Math.PI * k / radialSides);
			float a1 = (float) (2.0 * Math.PI * (k + 1) / radialSides);

			Vec3 rad0 = right.scale(Mth.cos(a0)).add(up.scale(Mth.sin(a0)));
			Vec3 rad1 = right.scale(Mth.cos(a1)).add(up.scale(Mth.sin(a1)));

			Vec3 p00 = start.add(rad0.scale(rStart));
			Vec3 p10 = end.add(rad0.scale(rEnd));
			Vec3 p11 = end.add(rad1.scale(rEnd));
			Vec3 p01 = start.add(rad1.scale(rStart));

			consumer.addVertex(matrix, (float) p00.x, (float) p00.y, (float) p00.z).setColor(baseR, baseG, baseB, alphaStart);
			consumer.addVertex(matrix, (float) p10.x, (float) p10.y, (float) p10.z).setColor(baseR, baseG, baseB, alphaEnd);
			consumer.addVertex(matrix, (float) p11.x, (float) p11.y, (float) p11.z).setColor(baseR, baseG, baseB, alphaEnd);
			consumer.addVertex(matrix, (float) p01.x, (float) p01.y, (float) p01.z).setColor(baseR, baseG, baseB, alphaStart);
		}
	}

	private static Vec3 waveRingPoint(
			Vec3 basisU,
			Vec3 basisV,
			Vec3 basisN,
			float radius,
			int waveNodes,
			float waveAmpRatio,
			float angle,
			float time,
			float phaseOffset
	) {
		float radialWave = 1.0f + waveAmpRatio * Mth.sin(waveNodes * angle + time * 0.055f + phaseOffset);
		float normalWave = radius * waveAmpRatio * 0.65f * Mth.cos(waveNodes * angle - time * 0.04f + phaseOffset);
		double r = radius * radialWave;
		return basisU.scale(r * Mth.cos(angle))
				.add(basisV.scale(r * Mth.sin(angle)))
				.add(basisN.scale(normalWave));
	}

	private static Vec3 electronPosition(OrbitalType orbital, int index, float cloudRadius, float time) {
		float speed = switch (orbital) {
			case S1 -> 0.22f;
			case S2, PX2, PY2, PZ2 -> 0.145f;
			case S3, P3, D3 -> 0.115f;
		};
		float t = time * speed + index * 2.094f;
		float rBase = switch (orbital) {
			case S1 -> cloudRadius * 0.25f;
			case S2 -> cloudRadius * 0.56f;
			case PX2, PY2, PZ2 -> cloudRadius * 0.72f;
			case S3 -> cloudRadius * 0.84f;
			case P3 -> cloudRadius * 0.90f;
			case D3 -> cloudRadius * 0.88f;
		};

		return switch (orbital) {
			case S1, S2, S3 -> {
				float r = rBase * (0.86f + 0.14f * Mth.sin(t * 1.7f));
				float incl = index * 1.15f + Mth.cos(t * 0.55f) * 0.85f;
				double x = r * Math.cos(t) * Math.cos(incl);
				double y = r * Math.sin(t * 1.3f) * Math.sin(incl);
				double z = r * Math.sin(t);
				yield new Vec3(x, y, z);
			}
			case PX2 -> {
				double lobeX = rBase * Math.sin(t);
				double perp = rBase * 0.28 * Math.sin(2.0 * t);
				yield new Vec3(lobeX, perp * Math.cos(index), perp * Math.sin(index));
			}
			case PY2 -> {
				double lobeY = rBase * Math.sin(t);
				double perp = rBase * 0.28 * Math.sin(2.0 * t);
				yield new Vec3(perp * Math.cos(index), lobeY, perp * Math.sin(index));
			}
			case PZ2 -> {
				double lobeZ = rBase * Math.sin(t);
				double perp = rBase * 0.28 * Math.sin(2.0 * t);
				yield new Vec3(perp * Math.cos(index), perp * Math.sin(index), lobeZ);
			}
			case P3 -> {
				// 3-axis alternating outer 3p figure-8 orbit
				double lobe = rBase * Math.sin(t);
				double perp = rBase * 0.26 * Math.sin(2.0 * t);
				yield switch (index % 3) {
					case 0 -> new Vec3(lobe, perp * Math.cos(index), perp * Math.sin(index));
					case 1 -> new Vec3(perp * Math.cos(index), lobe, perp * Math.sin(index));
					default -> new Vec3(perp * Math.cos(index), perp * Math.sin(index), lobe);
				};
			}
			case D3 -> {
				// 4-lobe rose curve trajectory representing 3d transition-metal orbitals
				double roseR = rBase * (0.28 + 0.72 * Math.abs(Math.sin(2.0 * t)));
				double tilt = (index % 3) * 0.95;
				double x = roseR * Math.cos(t);
				double y = roseR * Math.sin(t) * Math.cos(tilt);
				double z = roseR * Math.sin(t) * Math.sin(tilt);
				yield new Vec3(x, y, z);
			}
		};
	}

	private static Vec3 tubeCenterPoint(
			Vec3 start,
			Vec3 end,
			Vec3 right,
			Vec3 up,
			float t,
			float time,
			int seed,
			double waveAmp
	) {
		Vec3 base = start.lerp(end, t);
		double envelope = Math.sin(Math.PI * t);
		double phase = time * 0.45 + t * Math.PI * 3.0 + seed * 1.9;
		double offsetR = Math.sin(phase) * waveAmp * envelope;
		double offsetU = Math.cos(phase * 1.3) * waveAmp * envelope;
		return base.add(right.scale(offsetR)).add(up.scale(offsetU));
	}

	private static Vec3 unitDir(float theta, float phi) {
		float sinT = Mth.sin(theta);
		return new Vec3(
				sinT * Mth.cos(phi),
				Mth.cos(theta),
				sinT * Mth.sin(phi)
		);
	}

	private static double axisProjection(OrbitalType type, Vec3 unitDir) {
		return switch (type) {
			case PX2 -> unitDir.x;
			case PY2, P3 -> unitDir.y;
			case PZ2 -> unitDir.z;
			default -> 1.0;
		};
	}

	private static float pLobeRadius(OrbitalType type, Vec3 unitDir, float maxRadius) {
		double cosAngle = Math.abs(axisProjection(type, unitDir));
		if (cosAngle < 0.14) {
			return 0.0f;
		}
		return (float) (maxRadius * Math.pow(cosAngle, 1.28));
	}

	private static double dCloverHarmonic(Vec3 dir) {
		// Real spherical harmonic d_xy + d_yz blend producing luminous 4-leaf clover lobes
		return 2.0 * (dir.x * dir.y + 0.45 * dir.z * (dir.x - dir.y));
	}

	private static float dLobeRadius(double harmonicValue, float maxRadius) {
		double mag = Math.min(1.0, Math.abs(harmonicValue));
		if (mag < 0.12) {
			return 0.0f;
		}
		return (float) (maxRadius * Math.pow(mag, 1.18));
	}

	private static void emitShadedVertex(
			Matrix4f matrix,
			VertexConsumer consumer,
			Vec3 center,
			Vec3 normal,
			float radius,
			float r,
			float g,
			float b,
			float baseAlpha,
			float rimAlpha,
			Vec3 cameraLocal
	) {
		Vec3 pos = center.add(normal.scale(radius));
		Vec3 toCam = cameraLocal.subtract(pos);
		double dist = toCam.length();
		float ndotv = 0.5f;
		if (dist > 1.0e-4) {
			ndotv = (float) Math.abs(normal.dot(toCam.scale(1.0 / dist)));
		}

		// Fresnel term: luminous at grazing angles, crystal-translucent toward center
		float fresnel = (float) Math.pow(1.0f - Mth.clamp(ndotv, 0.0f, 1.0f), 1.75);
		float alpha = Mth.clamp(Mth.lerp(fresnel, baseAlpha, rimAlpha), 0.0f, 1.0f);

		// Soft cosmic directional highlight + rim energy boost
		float light = (float) Mth.clamp(0.68 + 0.32 * (normal.x * 0.4 + normal.y * 0.8 + normal.z * 0.3), 0.48, 1.16);
		float finalR = Mth.clamp(r * light + fresnel * 0.24f, 0.0f, 1.0f);
		float finalG = Mth.clamp(g * light + fresnel * 0.24f, 0.0f, 1.0f);
		float finalB = Mth.clamp(b * light + fresnel * 0.28f, 0.0f, 1.0f);

		consumer.addVertex(matrix, (float) pos.x, (float) pos.y, (float) pos.z)
				.setColor(finalR, finalG, finalB, alpha);
	}
}
