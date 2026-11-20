package com.quantumatom.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.quantumatom.dimension.QuantumRealmDimension;
import com.quantumatom.entity.AtomEntity;
import com.quantumatom.entity.AtomEntity.RenderMode;
import com.quantumatom.scale.ScaleManager;
import com.quantumatom.science.AtomElement;
import com.quantumatom.science.AtomElement.OrbitalType;
import com.quantumatom.science.OrbitalSampler;
import com.quantumatom.science.OrbitalSampler.NucleonInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Scale-isolated 3D procedural renderer for {@link AtomEntity} across all 3 subatomic dimensions:
 * <ul>
 *   <li>{@link RenderMode#ATOMIC_CLOUD} ({@code 10^-10 m}): Electron orbitals, wave rings, relativistic electrons, and pinpoint star nucleus only</li>
 *   <li>{@link RenderMode#NUCLEUS_ONLY} ({@code 10^-15 m}): Colossal vibrating Protons &amp; Neutrons bound by Strong Force meson filaments (no exposed quarks)</li>
 *   <li>{@link RenderMode#QUARK_INTERIOR} ({@code 10^-18 m}): Colossal interior QCD Confinement Cavity enclosing 3 giant Valence Quarks, QCD color auras, crackling Gluon Flux Tubes, and virtual sea pairs</li>
 * </ul>
 * Every render pass strictly acquires and releases a single {@link VertexConsumer} before transitioning
 * to another {@link RenderType} or {@link Font#drawInBatch}, eliminating {@code BufferBuilder: Not building!} crashes.
 */
public class AtomEntityRenderer extends EntityRenderer<AtomEntity> {
	private static final int PROTON_CORE_RGB = 0xDC2626;
	private static final int PROTON_AURA_RGB = 0xFB923C;
	private static final int NEUTRON_CORE_RGB = 0x2563EB;
	private static final int NEUTRON_AURA_RGB = 0x38BDF8;

	private static final int MESON_GOLD_RGB = 0xFBBF24;
	private static final int MESON_ROSE_RGB = 0xF472B6;

	private static final int PROTON_CAVITY_RGB = 0xEA580C;
	private static final int PROTON_CAVITY_RIM_RGB = 0xF87171;
	private static final int NEUTRON_CAVITY_RGB = 0x1D4ED8;
	private static final int NEUTRON_CAVITY_RIM_RGB = 0x60A5FA;

	private static final int UP_QUARK_RGB = 0xFDE047;
	private static final int DOWN_QUARK_RGB = 0x38BDF8;
	private static final int GLUON_CORE_RGB = 0xF0FDFA;
	private static final int GLUON_PLASMA_RGB = 0xA7F3D0;
	private static final int[] QCD_COLOR_CHARGES = {0xFF3B30, 0x34C759, 0x0A84FF};

	private static final int MAX_VISUAL_ELECTRONS = 10;
	private static final int MAX_MESON_LINKS = 72;

	private record PreparedNucleon(
			NucleonInfo info,
			Vec3 center,
			double distToCamera
	) {
	}

	private record MesonLink(Vec3 a, Vec3 b, int seed, int rgb) {
	}

	public AtomEntityRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public boolean shouldRender(AtomEntity entity, Frustum frustum, double x, double y, double z) {
		boolean inSubatomic = QuantumRealmDimension.isSubatomicDimension(entity.level());
		Minecraft mc = Minecraft.getInstance();

		if (inSubatomic) {
			double maxDist = 420.0 * entity.getAtomScale();
			double maxDistSqr = maxDist * maxDist;
			if (entity.distanceToSqr(x, y, z) < maxDistSqr) {
				return true;
			}
			if (this.entityRenderDispatcher.camera != null) {
				Vec3 dispCamPos = this.entityRenderDispatcher.camera.getPosition();
				if (entity.position().distanceToSqr(dispCamPos) < maxDistSqr) {
					return true;
				}
			}
			Vec3 mainCamPos = mc.gameRenderer.getMainCamera().getPosition();
			return entity.position().distanceToSqr(mainCamPos) < maxDistSqr;
		}

		// Overworld manually spawned atoms stay hidden at normal macroscopic scale (>= 0.95x)
		if (mc.player == null || ScaleManager.getScale(mc.player) >= 0.95) {
			return false;
		}
		double maxDist = 180.0 * entity.getAtomScale();
		return entity.distanceToSqr(x, y, z) < maxDist * maxDist;
	}

	@Override
	public void render(
			AtomEntity entity,
			float entityYaw,
			float partialTick,
			PoseStack poseStack,
			MultiBufferSource bufferSource,
			int packedLight
	) {
		Minecraft mc = Minecraft.getInstance();
		boolean inSubatomic = QuantumRealmDimension.isSubatomicDimension(entity.level());
		double playerScale = (mc.player != null) ? ScaleManager.getScale(mc.player) : 1.0;

		float visibility;
		if (inSubatomic) {
			visibility = 1.0f;
		} else {
			if (mc.player == null || playerScale >= 0.95) {
				return;
			}
			visibility = Mth.clamp((float) ((0.95 - playerScale) / 0.65), 0.0f, 1.0f);
		}

		float time = entity.tickCount + partialTick;
		Vec3 camWorld = (this.entityRenderDispatcher.camera != null)
				? this.entityRenderDispatcher.camera.getPosition()
				: mc.gameRenderer.getMainCamera().getPosition();
		Vec3 anchorPos = entity.getPosition(partialTick);
		Vec3 driftOffset = entity.getVisualDriftOffset(time);
		Quaternionf atomRotation = entity.getVisualRotation(time);
		Vec3 cameraLocal = entity.worldToAtomLocal(camWorld, anchorPos, time);

		poseStack.pushPose();
		poseStack.translate(driftOffset.x, driftOffset.y, driftOffset.z);
		poseStack.mulPose(atomRotation);

		switch (entity.getRenderMode()) {
			case ATOMIC_CLOUD -> renderAtomicCloudMode(
					entity, poseStack, bufferSource, time, visibility, inSubatomic, cameraLocal, atomRotation, packedLight
			);
			case NUCLEUS_ONLY -> renderNucleusOnlyMode(
					entity, poseStack, bufferSource, time, visibility, cameraLocal, atomRotation, packedLight
			);
			case QUARK_INTERIOR -> renderQuarkInteriorMode(
					entity, poseStack, bufferSource, time, visibility, cameraLocal, packedLight
			);
		}

		poseStack.popPose();
	}

	// =========================================================================
	// MODE 1: ATOMIC_CLOUD (10^-10 m — Electron Orbitals + Pinpoint Star Nucleus)
	// =========================================================================

	private void renderAtomicCloudMode(
			AtomEntity entity,
			PoseStack poseStack,
			MultiBufferSource bufferSource,
			float time,
			float visibility,
			boolean inSubatomic,
			Vec3 cameraLocal,
			Quaternionf atomRotation,
			int packedLight
	) {
		AtomElement element = entity.getElement();
		float cloudRadius = (float) entity.getCloudRadius();
		double camDist = cameraLocal.length();

		// PASS 1 (RenderType.lightning): Pinpoint star nucleus + de Broglie rings + relativistic electrons + sparkles
		renderAtomicCloudEnergyPass(poseStack, bufferSource, element, cloudRadius, time, visibility, inSubatomic, cameraLocal, camDist);

		// PASS 2 (RenderType.debugStructureQuads): Volumetric 1s/2s/2p/3s/3p/3d orbital shells (no depth write)
		renderAtomicCloudShellsPass(poseStack, bufferSource, element, cloudRadius, visibility, inSubatomic, cameraLocal, camDist);

		// PASS 3 (Font.drawInBatch): Clean isotope identifier when approaching an atom in the synced lattice
		if (inSubatomic && camDist < cloudRadius * 1.65 && camDist > cloudRadius * 0.22) {
			Quaternionf invRot = new Quaternionf(atomRotation).invert();
			float labelScale = Math.max(0.020f, cloudRadius * 0.0018f);
			String label = element.isotopeName() + " (" + element.symbol() + ")";
			renderBillboardLabel3D(
					poseStack, bufferSource,
					new Vec3(0.0, cloudRadius * 0.16, 0.0),
					invRot, label, element.accentRgb(), labelScale, packedLight
			);
		}
	}

	private void renderAtomicCloudEnergyPass(
			PoseStack poseStack,
			MultiBufferSource bufferSource,
			AtomElement element,
			float cloudRadius,
			float time,
			float visibility,
			boolean inSubatomic,
			Vec3 cameraLocal,
			double camDist
	) {
		VertexConsumer lightning = bufferSource.getBuffer(RenderType.lightning());
		List<OrbitalType> orbitals = element.orbitals();

		// 1. Tiny brilliant pinpoint nuclear star-core (representing the 100,000x smaller nucleus — NO giant nucleons!)
		float pinpointCoreR = Math.max(0.20f, cloudRadius * 0.017f);
		float starPulse = 0.90f + 0.10f * Mth.sin(time * 0.14f);
		QuantumMeshRenderer.renderFresnelSphere(
				poseStack, lightning, Vec3.ZERO, pinpointCoreR * starPulse,
				10, 12, 0xFFFFFF, 0.92f * visibility, 1.0f * visibility, cameraLocal, false
		);
		QuantumMeshRenderer.renderFresnelSphere(
				poseStack, lightning, Vec3.ZERO, pinpointCoreR * 2.6f * starPulse,
				10, 14, 0xFEF08A, 0.22f * visibility, 0.62f * visibility, cameraLocal, false
		);
		QuantumMeshRenderer.renderQuantumSparkle(
				poseStack, lightning, Vec3.ZERO, pinpointCoreR * 4.8f * starPulse, 0xFFFBEB, 0.65f * visibility
		);

		// 2. Luminous 3D de Broglie standing-wave orbital rings
		float ringAlpha = (inSubatomic ? 1.0f : 0.75f) * visibility;
		float tubeBase = Math.max(0.032f, cloudRadius * 0.0042f);
		QuantumMeshRenderer.renderOrbitalWaveRing(
				poseStack, lightning, cloudRadius * 0.28f, tubeBase * 0.85f,
				3, 0.034f, 0.52f, 0.35f, time, 0.0f,
				OrbitalType.S1.rgbColor(), 0.26f * ringAlpha
		);
		if (orbitals.contains(OrbitalType.S2)) {
			QuantumMeshRenderer.renderOrbitalWaveRing(
					poseStack, lightning, cloudRadius * 0.62f, tubeBase,
					4, 0.032f, -0.45f, 0.78f, time, 1.57f,
					OrbitalType.S2.rgbColor(), 0.22f * ringAlpha
			);
		}
		if (orbitals.contains(OrbitalType.PX2)) {
			QuantumMeshRenderer.renderOrbitalWaveRing(
					poseStack, lightning, cloudRadius * 0.78f, tubeBase * 0.95f,
					5, 0.028f, 1.12f, -0.55f, time, 3.14f,
					OrbitalType.PX2.rgbColor(), 0.19f * ringAlpha
			);
		}
		if (orbitals.contains(OrbitalType.D3)) {
			QuantumMeshRenderer.renderOrbitalWaveRing(
					poseStack, lightning, cloudRadius * 0.90f, tubeBase * 1.05f,
					6, 0.026f, 0.88f, 1.05f, time, 4.71f,
					OrbitalType.D3.rgbColor(), 0.22f * ringAlpha
			);
		} else if (orbitals.contains(OrbitalType.S3) || orbitals.contains(OrbitalType.P3)) {
			OrbitalType outer = orbitals.contains(OrbitalType.P3) ? OrbitalType.P3 : OrbitalType.S3;
			QuantumMeshRenderer.renderOrbitalWaveRing(
					poseStack, lightning, cloudRadius * 0.90f, tubeBase,
					5, 0.026f, -0.82f, -0.62f, time, 4.10f,
					outer.rgbColor(), 0.20f * ringAlpha
			);
		}

		// 3. Relativistic 3D Electron Wave-Packets with sleek tapered trails (capped cleanly for heavy elements like Au-197)
		int electronVisualCount = Math.min(element.protons(), MAX_VISUAL_ELECTRONS);
		for (int e = 0; e < electronVisualCount; e++) {
			OrbitalType assignedOrbital = assignElectronToOrbital(e, electronVisualCount, orbitals);
			QuantumMeshRenderer.renderElectronWavePacket(
					poseStack, lightning, assignedOrbital, e,
					cloudRadius, time, visibility, cameraLocal
			);
		}

		// 4. Subtle quantum vacuum energy glints drifting inside the electron cloud
		if (inSubatomic && camDist < cloudRadius * 3.6) {
			for (int i = 0; i < 8; i++) {
				double theta = time * (0.018 + (i % 3) * 0.006) + i * 2.39996;
				double r = cloudRadius * (0.34 + 0.58 * ((i + 0.5) / 8.0));
				double yOff = Math.sin(theta * 1.4 + i) * r * 0.55;
				Vec3 pos = new Vec3(Math.cos(theta) * r, yOff, Math.sin(theta) * r);
				float twinkle = 0.20f + 0.80f * Math.max(0.0f, Mth.sin(time * 0.18f + i * 1.7f));
				int sparkColor = orbitals.get(i % orbitals.size()).rgbColor();
				QuantumMeshRenderer.renderQuantumSparkle(
						poseStack, lightning, pos,
						cloudRadius * 0.0085f * twinkle, sparkColor, 0.40f * twinkle * visibility
				);
			}
		}
	}

	private void renderAtomicCloudShellsPass(
			PoseStack poseStack,
			MultiBufferSource bufferSource,
			AtomElement element,
			float cloudRadius,
			float visibility,
			boolean inSubatomic,
			Vec3 cameraLocal,
			double camDist
	) {
		VertexConsumer translucent = bufferSource.getBuffer(RenderType.debugStructureQuads());
		float orbitalVis = Mth.clamp(visibility * (inSubatomic ? 1.20f : 1.0f), 0.0f, 1.0f);
		boolean farAtom = camDist > cloudRadius * 2.8;

		int s1Lat = farAtom ? 12 : 18;
		int s1Lon = farAtom ? 16 : 22;
		int s2Lat = farAtom ? 14 : 20;
		int s2Lon = farAtom ? 18 : 24;
		int pLat = farAtom ? 12 : 18;
		int pLon = farAtom ? 16 : 22;

		for (OrbitalType orbital : element.orbitals()) {
			int rgb = orbital.rgbColor();
			switch (orbital) {
				case S1 -> {
					float r1sInner = cloudRadius * 0.15f;
					float r1sOuter = cloudRadius * 0.28f;
					QuantumMeshRenderer.renderFresnelSphere(
							poseStack, translucent, Vec3.ZERO, r1sInner,
							Math.max(10, s1Lat - 4), Math.max(12, s1Lon - 4), rgb,
							0.042f * orbitalVis, 0.16f * orbitalVis, cameraLocal, camDist < r1sInner
					);
					QuantumMeshRenderer.renderFresnelSphere(
							poseStack, translucent, Vec3.ZERO, r1sOuter,
							s1Lat, s1Lon, rgb,
							0.032f * orbitalVis, 0.18f * orbitalVis, cameraLocal, camDist < r1sOuter
					);
				}
				case S2 -> {
					float rNode = cloudRadius * 0.42f;
					QuantumMeshRenderer.renderFresnelSphere(
							poseStack, translucent, Vec3.ZERO, rNode,
							s1Lat, s1Lon, rgb,
							0.022f * orbitalVis, 0.11f * orbitalVis, cameraLocal, camDist < rNode
					);
					float r2s = cloudRadius * 0.64f;
					QuantumMeshRenderer.renderFresnelSphere(
							poseStack, translucent, Vec3.ZERO, r2s,
							s2Lat, s2Lon, rgb,
							0.026f * orbitalVis, 0.15f * orbitalVis, cameraLocal, camDist < r2s
					);
				}
				case PX2, PY2, PZ2 -> QuantumMeshRenderer.renderPOrbitalLobes(
						poseStack, translucent, orbital, cloudRadius * 0.84f,
						pLat, pLon, orbitalVis, cameraLocal
				);
				case S3 -> {
					float r3s = cloudRadius * 0.88f;
					QuantumMeshRenderer.renderFresnelSphere(
							poseStack, translucent, Vec3.ZERO, r3s,
							s2Lat, s2Lon, rgb,
							0.020f * orbitalVis, 0.13f * orbitalVis, cameraLocal, camDist < r3s
					);
				}
				case P3 -> QuantumMeshRenderer.renderPOrbitalLobes(
						poseStack, translucent, orbital, cloudRadius * 0.96f,
						pLat, pLon, orbitalVis * 0.92f, cameraLocal
				);
				case D3 -> QuantumMeshRenderer.render3dOrbitalLobes(
						poseStack, translucent, rgb, cloudRadius * 0.95f,
						pLat, pLon, orbitalVis * 0.95f, cameraLocal
				);
			}
		}
	}

	// =========================================================================
	// MODE 2: NUCLEUS_ONLY (10^-15 m — Colossal Baryon Cluster + Meson Filaments)
	// =========================================================================

	private void renderNucleusOnlyMode(
			AtomEntity entity,
			PoseStack poseStack,
			MultiBufferSource bufferSource,
			float time,
			float visibility,
			Vec3 cameraLocal,
			Quaternionf atomRotation,
			int packedLight
	) {
		AtomElement element = entity.getElement();
		float nucleonRadius = (float) entity.getNucleonRadius();
		float nucleusOuterR = (float) entity.getNucleusOuterRadius();

		List<NucleonInfo> layout = OrbitalSampler.getNucleonLayout(element, nucleonRadius);
		List<PreparedNucleon> nucleons = new ArrayList<>(layout.size());
		for (NucleonInfo info : layout) {
			Vec3 center = OrbitalSampler.getAnimatedNucleonOffset(info, time, nucleonRadius);
			double dist = cameraLocal.distanceTo(center);
			nucleons.add(new PreparedNucleon(info, center, dist));
		}
		// Back-to-front ordering relative to active camera
		nucleons.sort(Comparator.comparingDouble(PreparedNucleon::distToCamera).reversed());

		List<MesonLink> mesonLinks = buildMesonExchangeLinks(nucleons, nucleonRadius);

		// PASS 1 (RenderType.debugQuads): Solid subsurface-glowing 3D Protons & Neutrons (NO exposed quarks!)
		renderNucleusBaryonsPass(poseStack, bufferSource, nucleons, nucleonRadius, time, visibility, cameraLocal);

		// PASS 2 (RenderType.lightning): Strong Nuclear Force meson filaments + baryon aura rims + distant electron horizon
		renderNucleusStrongForcePass(poseStack, bufferSource, nucleons, mesonLinks, nucleonRadius, nucleusOuterR, time, visibility, cameraLocal);

		// PASS 3 (Font.drawInBatch): Clean "p+" / "n0" labels on closest nucleons so player can choose Proton or Neutron
		renderNucleusLabelsPass(poseStack, bufferSource, nucleons, nucleonRadius, atomRotation, packedLight);
	}

	private List<MesonLink> buildMesonExchangeLinks(List<PreparedNucleon> nucleons, float nucleonRadius) {
		int count = nucleons.size();
		if (count <= 1) {
			return List.of();
		}
		double maxLinkDistSqr = (nucleonRadius * 3.15) * (nucleonRadius * 3.15);
		List<MesonLink> links = new ArrayList<>(Math.min(MAX_MESON_LINKS, count * 2));

		// Prioritize closest nucleons first (iterating from back of sorted list = closest to camera)
		for (int idxA = count - 1; idxA >= 0 && links.size() < MAX_MESON_LINKS; idxA--) {
			PreparedNucleon a = nucleons.get(idxA);
			int linkedForA = 0;
			for (int idxB = idxA - 1; idxB >= 0 && linkedForA < 2 && links.size() < MAX_MESON_LINKS; idxB--) {
				PreparedNucleon b = nucleons.get(idxB);
				double d2 = a.center().distanceToSqr(b.center());
				if (d2 <= maxLinkDistSqr && d2 > 1.0e-3) {
					boolean protonNeutronPair = a.info().isProton() != b.info().isProton();
					int rgb = protonNeutronPair ? MESON_GOLD_RGB : MESON_ROSE_RGB;
					links.add(new MesonLink(a.center(), b.center(), a.info().index() * 13 + b.info().index(), rgb));
					linkedForA++;
				}
			}
		}
		return links;
	}

	private void renderNucleusBaryonsPass(
			PoseStack poseStack,
			MultiBufferSource bufferSource,
			List<PreparedNucleon> nucleons,
			float nucleonRadius,
			float time,
			float visibility,
			Vec3 cameraLocal
	) {
		VertexConsumer solid = bufferSource.getBuffer(RenderType.debugQuads());
		int total = nucleons.size();
		boolean heavyNucleus = total > 36;

		for (int i = 0; i < total; i++) {
			PreparedNucleon nucleon = nucleons.get(i);
			boolean isClose = (total - i) <= 18 || nucleon.distToCamera() < nucleonRadius * 8.5;
			int lat = isClose ? 14 : (heavyNucleus ? 8 : 11);
			int lon = isClose ? 18 : (heavyNucleus ? 10 : 14);

			boolean isProton = nucleon.info().isProton();
			int coreRgb = isProton ? PROTON_CORE_RGB : NEUTRON_CORE_RGB;
			// Subtle strong-force nuclear breathing pulsation
			float pulse = 1.0f + 0.028f * Mth.sin(time * 0.16f + nucleon.info().index() * 1.9f);
			QuantumMeshRenderer.renderFresnelSphere(
					poseStack, solid, nucleon.center(), nucleonRadius * pulse,
					lat, lon, coreRgb, 0.93f * visibility, 1.0f * visibility, cameraLocal, false
			);
		}
	}

	private void renderNucleusStrongForcePass(
			PoseStack poseStack,
			MultiBufferSource bufferSource,
			List<PreparedNucleon> nucleons,
			List<MesonLink> mesonLinks,
			float nucleonRadius,
			float nucleusOuterR,
			float time,
			float visibility,
			Vec3 cameraLocal
	) {
		VertexConsumer lightning = bufferSource.getBuffer(RenderType.lightning());

		// 1. Pulsing Strong Nuclear Force meson/pion exchange filaments between neighboring nucleons
		float mesonTubeR = nucleonRadius * 0.095f;
		for (MesonLink link : mesonLinks) {
			float linkPulse = 0.55f + 0.45f * Mth.sin(time * 0.24f + link.seed() * 0.7f);
			QuantumMeshRenderer.renderGluonFluxTube(
					poseStack, lightning, link.a(), link.b(),
					mesonTubeR * (0.75f + 0.35f * linkPulse), time * 1.15f, link.seed(),
					link.rgb(), 0.72f * linkPulse * visibility, 8
			);
		}

		// 2. Subsurface baryon energy rim glow on the closest 24 nucleons
		int total = nucleons.size();
		int startIdx = Math.max(0, total - 24);
		for (int i = startIdx; i < total; i++) {
			PreparedNucleon nucleon = nucleons.get(i);
			boolean isProton = nucleon.info().isProton();
			int auraRgb = isProton ? PROTON_AURA_RGB : NEUTRON_AURA_RGB;
			QuantumMeshRenderer.renderFresnelSphere(
					poseStack, lightning, nucleon.center(), nucleonRadius * 1.09f,
					10, 14, auraRgb, 0.06f * visibility, 0.46f * visibility, cameraLocal, false
			);
		}

		// 3. Distant cosmic horizon rings representing the unimaginably far-away electron cloud
		float horizonR = Math.max(92.0f, nucleusOuterR * 4.2f);
		float horizonTube = Math.max(0.18f, horizonR * 0.0032f);
		QuantumMeshRenderer.renderOrbitalWaveRing(
				poseStack, lightning, horizonR, horizonTube,
				6, 0.02f, 0.35f, 0.20f, time * 0.25f, 0.0f, 0x818CF8, 0.14f * visibility
		);
		QuantumMeshRenderer.renderOrbitalWaveRing(
				poseStack, lightning, horizonR * 1.12f, horizonTube,
				8, 0.02f, -0.58f, 0.75f, time * 0.20f, 2.4f, 0x38BDF8, 0.11f * visibility
		);
	}

	private void renderNucleusLabelsPass(
			PoseStack poseStack,
			MultiBufferSource bufferSource,
			List<PreparedNucleon> nucleons,
			float nucleonRadius,
			Quaternionf atomRotation,
			int packedLight
	) {
		if (nucleons.isEmpty()) {
			return;
		}
		Quaternionf invRot = new Quaternionf(atomRotation).invert();
		float textScale = 0.011f * nucleonRadius;
		int maxLabels = 6;
		int shown = 0;

		// Iterate from closest nucleon outward
		for (int i = nucleons.size() - 1; i >= 0 && shown < maxLabels; i--) {
			PreparedNucleon nucleon = nucleons.get(i);
			if (nucleon.distToCamera() > nucleonRadius * 6.5) {
				break;
			}
			boolean isProton = nucleon.info().isProton();
			String tag = isProton ? "p+" : "n0";
			int rgb = isProton ? 0xFCA5A5 : 0x93C5FD;
			renderBillboardLabel3D(
					poseStack, bufferSource,
					nucleon.center().add(0.0, nucleonRadius * 1.15, 0.0),
					invRot, tag, rgb, textScale, packedLight
			);
			shown++;
		}
	}

	// =========================================================================
	// MODE 3: QUARK_INTERIOR (10^-18 m — Interior Proton/Neutron QCD Cavity)
	// =========================================================================

	private void renderQuarkInteriorMode(
			AtomEntity entity,
			PoseStack poseStack,
			MultiBufferSource bufferSource,
			float time,
			float visibility,
			Vec3 cameraLocal,
			int packedLight
	) {
		float cavityR = (float) entity.getQuarkCavityRadius();
		boolean isProton = entity.isProtonInterior();
		Vec3[] qPos = entity.getQuarkInteriorPositions(time);
		Vec3 yJunction = new Vec3(
				(qPos[0].x + qPos[1].x + qPos[2].x) / 3.0,
				(qPos[0].y + qPos[1].y + qPos[2].y) / 3.0,
				(qPos[0].z + qPos[1].z + qPos[2].z) / 3.0
		);
		float quarkCoreR = Math.max(3.2f, cavityR * 0.084f);

		// PASS 1 (RenderType.debugQuads): 3 gigantic solid 3D shaded Valence Quarks
		renderQuarkInteriorCoresPass(poseStack, bufferSource, qPos, quarkCoreR, isProton, visibility, cameraLocal);

		// PASS 2 (RenderType.lightning): QCD RGB auras + massive crackling Gluon Flux Tubes + virtual sea pairs + dome field hoops
		renderQuarkInteriorQcdPass(poseStack, bufferSource, qPos, yJunction, cavityR, quarkCoreR, isProton, time, visibility, cameraLocal);

		// PASS 3 (RenderType.debugStructureQuads): Colossal inward-facing QCD Confinement Cavity Dome (no depth write)
		renderQuarkInteriorCavityDomePass(poseStack, bufferSource, cavityR, isProton, visibility, cameraLocal);

		// PASS 4 (Font.drawInBatch): Clear floating 3D labels above the 3 valence quarks & Y-junction
		renderQuarkInteriorLabelsPass(poseStack, bufferSource, qPos, yJunction, quarkCoreR, isProton, packedLight);
	}

	private void renderQuarkInteriorCoresPass(
			PoseStack poseStack,
			MultiBufferSource bufferSource,
			Vec3[] qPos,
			float quarkCoreR,
			boolean isProton,
			float visibility,
			Vec3 cameraLocal
	) {
		VertexConsumer solid = bufferSource.getBuffer(RenderType.debugQuads());
		for (int q = 0; q < 3; q++) {
			boolean isUp = isProton ? (q < 2) : (q == 0);
			int coreRgb = isUp ? UP_QUARK_RGB : DOWN_QUARK_RGB;
			QuantumMeshRenderer.renderFresnelSphere(
					poseStack, solid, qPos[q], quarkCoreR,
					20, 24, coreRgb, 0.94f * visibility, 1.0f * visibility, cameraLocal, false
			);
		}
	}

	private void renderQuarkInteriorQcdPass(
			PoseStack poseStack,
			MultiBufferSource bufferSource,
			Vec3[] qPos,
			Vec3 yJunction,
			float cavityR,
			float quarkCoreR,
			boolean isProton,
			float time,
			float visibility,
			Vec3 cameraLocal
	) {
		VertexConsumer lightning = bufferSource.getBuffer(RenderType.lightning());

		float tubeCoreR = Math.max(0.55f, cavityR * 0.015f);
		float tubeSheathR = tubeCoreR * 2.15f;

		// 1. Massive crackling perimeter Gluon Flux Tubes connecting all 3 Valence Quarks (core + plasma sheath)
		for (int edge = 0; edge < 3; edge++) {
			Vec3 a = qPos[edge];
			Vec3 b = qPos[(edge + 1) % 3];
			int seed = 100 + edge * 17;
			QuantumMeshRenderer.renderGluonFluxTube(poseStack, lightning, a, b, tubeCoreR, time * 1.25f, seed, GLUON_CORE_RGB, 0.78f * visibility, 16);
			QuantumMeshRenderer.renderGluonFluxTube(poseStack, lightning, a, b, tubeSheathR, time * 0.95f, seed + 5, GLUON_PLASMA_RGB, 0.42f * visibility, 14);
		}

		// 2. Dynamic Red/Green/Blue QCD Color Charge Auras + Y-junction flux tubes
		int colorShift = (int) ((time * 0.08f) % 3);
		for (int q = 0; q < 3; q++) {
			int qcdColor = QCD_COLOR_CHARGES[(q + colorShift) % 3];
			int seed = 200 + q * 19;

			// Dual-layer color-charged flux tube into the central baryon Y-junction
			QuantumMeshRenderer.renderGluonFluxTube(poseStack, lightning, qPos[q], yJunction, tubeCoreR * 1.15f, time * 1.3f, seed, qcdColor, 0.85f * visibility, 16);
			QuantumMeshRenderer.renderGluonFluxTube(poseStack, lightning, qPos[q], yJunction, tubeSheathR * 1.15f, time * 0.9f, seed + 7, qcdColor, 0.38f * visibility, 12);

			// Dual concentric QCD color-charge aura halos around each gigantic quark
			QuantumMeshRenderer.renderFresnelSphere(
					poseStack, lightning, qPos[q], quarkCoreR * 1.48f,
					16, 20, qcdColor, 0.18f * visibility, 0.62f * visibility, cameraLocal, false
			);
			QuantumMeshRenderer.renderFresnelSphere(
					poseStack, lightning, qPos[q], quarkCoreR * 2.08f,
					14, 18, qcdColor, 0.05f * visibility, 0.34f * visibility, cameraLocal, false
			);
		}

		// 3. Blazing central QCD baryon Y-junction nexus
		float knotPulse = 0.88f + 0.16f * Mth.sin(time * 0.22f);
		float knotR = quarkCoreR * 0.62f * knotPulse;
		QuantumMeshRenderer.renderFresnelSphere(
				poseStack, lightning, yJunction, knotR,
				12, 16, 0xFFFFFF, 0.55f * visibility, 0.92f * visibility, cameraLocal, false
		);
		QuantumMeshRenderer.renderFresnelSphere(
				poseStack, lightning, yJunction, knotR * 1.85f,
				12, 16, 0xE879F9, 0.14f * visibility, 0.45f * visibility, cameraLocal, false
		);

		// 4. Shimmering virtual sea quark-antiquark (q - q_bar) vacuum fluctuation pairs inside the cavity
		int seaPairCount = 12;
		for (int i = 0; i < seaPairCount; i++) {
			float lifePhase = Mth.sin(time * (0.09f + (i % 4) * 0.02f) + i * 1.83f);
			if (lifePhase <= 0.05f) {
				continue;
			}
			double theta = i * 2.39996 + time * 0.014;
			double phi = ((i % 5) - 2.0) * 0.48 + Math.sin(time * 0.02 + i) * 0.25;
			double distR = cavityR * (0.34 + 0.46 * ((i % 7) / 6.0));
			Vec3 pairCenter = new Vec3(
					Math.cos(theta) * Math.cos(phi) * distR,
					Math.sin(phi) * distR,
					Math.sin(theta) * Math.cos(phi) * distR
			);

			double sep = cavityR * 0.036 * lifePhase;
			Vec3 sepVec = new Vec3(Math.cos(theta + i), Math.sin(i * 1.7), Math.sin(theta + i)).normalize().scale(sep);
			Vec3 qSea = pairCenter.add(sepVec);
			Vec3 antiQSea = pairCenter.subtract(sepVec);

			int seaColor = QCD_COLOR_CHARGES[i % 3];
			int antiColor = QCD_COLOR_CHARGES[(i + 1) % 3];
			float seaR = Math.max(0.32f, cavityR * 0.011f) * lifePhase;

			QuantumMeshRenderer.renderFresnelSphere(poseStack, lightning, qSea, seaR, 6, 8, seaColor, 0.55f * lifePhase * visibility, 0.85f * lifePhase * visibility, cameraLocal, false);
			QuantumMeshRenderer.renderFresnelSphere(poseStack, lightning, antiQSea, seaR, 6, 8, antiColor, 0.55f * lifePhase * visibility, 0.85f * lifePhase * visibility, cameraLocal, false);
			QuantumMeshRenderer.renderGluonFluxTube(poseStack, lightning, qSea, antiQSea, seaR * 0.35f, time * 1.5f, 300 + i, 0xF0ABFC, 0.52f * lifePhase * visibility, 5);
		}

		// 5. Ambient QCD vacuum sparkles throughout the cavity
		for (int s = 0; s < 18; s++) {
			double ang = time * 0.022 + s * 2.39996;
			double r = cavityR * (0.20 + 0.68 * ((s + 0.5) / 18.0));
			double y = Math.sin(ang * 1.3 + s) * r * 0.62;
			Vec3 sparkPos = new Vec3(Math.cos(ang) * r, y, Math.sin(ang) * r);
			float twinkle = 0.25f + 0.75f * Math.max(0.0f, Mth.sin(time * 0.22f + s * 1.9f));
			int sparkRgb = QCD_COLOR_CHARGES[s % 3];
			QuantumMeshRenderer.renderQuantumSparkle(
					poseStack, lightning, sparkPos,
					cavityR * 0.012f * twinkle, sparkRgb, 0.50f * twinkle * visibility
			);
		}

		// 6. Geodesic interior confinement field hoops hugging the dome wall
		int hoopRgb = isProton ? PROTON_CAVITY_RIM_RGB : NEUTRON_CAVITY_RIM_RGB;
		QuantumMeshRenderer.renderConfinementFieldHoops(poseStack, lightning, cavityR, time, hoopRgb, 0.24f * visibility);
	}

	private void renderQuarkInteriorCavityDomePass(
			PoseStack poseStack,
			MultiBufferSource bufferSource,
			float cavityR,
			boolean isProton,
			float visibility,
			Vec3 cameraLocal
	) {
		VertexConsumer translucent = bufferSource.getBuffer(RenderType.debugStructureQuads());
		int innerRgb = isProton ? PROTON_CAVITY_RGB : NEUTRON_CAVITY_RGB;
		int outerRgb = isProton ? PROTON_CAVITY_RIM_RGB : NEUTRON_CAVITY_RIM_RGB;

		// Primary colossal inward-facing QCD Confinement Cavity membrane enclosing the player
		QuantumMeshRenderer.renderFresnelSphere(
				poseStack, translucent, Vec3.ZERO, cavityR,
				26, 34, innerRgb, 0.14f * visibility, 0.46f * visibility, cameraLocal, true
		);
		// Secondary outer confinement energy skin for luminous spherical depth
		QuantumMeshRenderer.renderFresnelSphere(
				poseStack, translucent, Vec3.ZERO, cavityR * 1.035f,
				20, 26, outerRgb, 0.06f * visibility, 0.26f * visibility, cameraLocal, true
		);
	}

	private void renderQuarkInteriorLabelsPass(
			PoseStack poseStack,
			MultiBufferSource bufferSource,
			Vec3[] qPos,
			Vec3 yJunction,
			float quarkCoreR,
			boolean isProton,
			int packedLight
	) {
		Quaternionf identity = new Quaternionf();
		float textScale = 0.028f * (quarkCoreR / 3.5f);

		for (int q = 0; q < 3; q++) {
			boolean isUp = isProton ? (q < 2) : (q == 0);
			int labelRgb = isUp ? UP_QUARK_RGB : DOWN_QUARK_RGB;
			String label = isUp ? "Up Quark (+2/3e)" : "Down Quark (-1/3e)";
			renderBillboardLabel3D(
					poseStack, bufferSource,
					qPos[q].add(0.0, quarkCoreR * 1.95, 0.0),
					identity, label, labelRgb, textScale, packedLight
			);
		}

		renderBillboardLabel3D(
				poseStack, bufferSource,
				yJunction.add(0.0, quarkCoreR * 1.25, 0.0),
				identity, "QCD Gluon Flux Junction", GLUON_PLASMA_RGB, textScale * 0.78f, packedLight
		);
	}

	// =========================================================================
	// Shared Helpers
	// =========================================================================

	private void renderBillboardLabel3D(
			PoseStack poseStack,
			MultiBufferSource bufferSource,
			Vec3 localPos,
			Quaternionf inverseAtomRot,
			String text,
			int rgb,
			float textScale,
			int packedLight
	) {
		poseStack.pushPose();
		poseStack.translate(localPos.x, localPos.y, localPos.z);
		poseStack.mulPose(inverseAtomRot);
		poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
		poseStack.scale(textScale, -textScale, textScale);

		Font font = this.getFont();
		float xOffset = -font.width(text) / 2.0f;
		Matrix4f matrix = poseStack.last().pose();
		font.drawInBatch(
				text, xOffset, 0.0f, 0xFF000000 | rgb, false,
				matrix, bufferSource, Font.DisplayMode.SEE_THROUGH, 0x55000000, packedLight
		);
		poseStack.popPose();
	}

	private static OrbitalType assignElectronToOrbital(int electronIndex, int totalVisual, List<OrbitalType> orbitals) {
		if (orbitals.isEmpty()) {
			return OrbitalType.S1;
		}
		if (electronIndex < 2) {
			return OrbitalType.S1;
		}
		// Spread remaining visual wave-packets evenly across all occupied shells (2s, 2p, 3s, 3p, 3d)
		int nonInnerCount = orbitals.size() - 1;
		if (nonInnerCount <= 0) {
			return orbitals.get(0);
		}
		int idx = 1 + ((electronIndex - 2) % nonInnerCount);
		return orbitals.get(idx);
	}

	@Override
	public ResourceLocation getTextureLocation(AtomEntity entity) {
		return ResourceLocation.withDefaultNamespace("textures/misc/white.png");
	}
}
