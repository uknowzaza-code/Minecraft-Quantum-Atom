package com.quantumatom.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.quantumatom.QuantumAtomMod;
import com.quantumatom.dimension.QuantumRealmDimension;
import com.quantumatom.dimension.QuantumRealmDimension.ScaleLayer;
import com.quantumatom.entity.AtomEntity;
import com.quantumatom.entity.AtomEntity.RenderMode;
import com.quantumatom.portal.QuantumPortalManager;
import com.quantumatom.scale.ScaleManager;
import com.quantumatom.science.AtomElement;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class AtomCommand {
	private static final SuggestionProvider<CommandSourceStack> ELEMENT_SUGGESTIONS = (ctx, builder) ->
			SharedSuggestionProvider.suggest(
					Arrays.stream(AtomElement.values())
							.map(e -> e.name().toLowerCase(Locale.ROOT)),
					builder
			);

	private AtomCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("atom")
				.then(Commands.literal("shrinker")
						.executes(ctx -> giveShrinker(ctx.getSource()))
				)
				.then(Commands.literal("dive")
						.executes(ctx -> diveDeeper(ctx.getSource()))
				)
				.then(Commands.literal("ascend")
						.executes(ctx -> ascendUp(ctx.getSource()))
				)
				.then(Commands.literal("spawn")
						.then(Commands.argument("element", StringArgumentType.word())
								.suggests(ELEMENT_SUGGESTIONS)
								.executes(ctx -> spawnAtom(
										ctx.getSource(),
										StringArgumentType.getString(ctx, "element"),
										1.5f
								))
								.then(Commands.argument("size", FloatArgumentType.floatArg(0.25f, 4.0f))
										.executes(ctx -> spawnAtom(
												ctx.getSource(),
												StringArgumentType.getString(ctx, "element"),
												FloatArgumentType.getFloat(ctx, "size")
										))
								)
						)
				)
				.then(Commands.literal("scale")
						.then(Commands.argument("value", DoubleArgumentType.doubleArg(ScaleManager.MIN_SCALE, ScaleManager.MAX_SCALE))
								.executes(ctx -> setPlayerScale(
										ctx.getSource(),
										DoubleArgumentType.getDouble(ctx, "value")
								))
						)
				)
				.then(Commands.literal("clear")
						.executes(ctx -> clearAtoms(ctx.getSource()))
				)
		);
	}

	private static int diveDeeper(CommandSourceStack source) {
		if (!(source.getEntity() instanceof ServerPlayer player)) {
			source.sendFailure(Component.literal("This command must be run by a player."));
			return 0;
		}
		if (QuantumPortalManager.isTransitioning(player.getUUID())) {
			source.sendFailure(Component.literal("Quantum zoom transition already in progress."));
			return 0;
		}
		boolean started = QuantumPortalManager.startDiveTransition(player);
		if (!started) {
			return 0;
		}
		source.sendSuccess(() -> Component.literal("Initiated seamless shrink dive to deeper subatomic layer."), false);
		return 1;
	}

	private static int ascendUp(CommandSourceStack source) {
		if (!(source.getEntity() instanceof ServerPlayer player)) {
			source.sendFailure(Component.literal("This command must be run by a player."));
			return 0;
		}
		if (QuantumPortalManager.isTransitioning(player.getUUID())) {
			source.sendFailure(Component.literal("Quantum zoom transition already in progress."));
			return 0;
		}
		boolean started = QuantumPortalManager.startAscendTransition(player);
		if (!started) {
			return 0;
		}
		source.sendSuccess(() -> Component.literal("Initiated seamless grow-out ascent to shallower scale layer."), false);
		return 1;
	}

	private static int spawnAtom(CommandSourceStack source, String elementName, float size) {
		Optional<AtomElement> elementOpt = AtomElement.fromName(elementName);
		if (elementOpt.isEmpty()) {
			String available = String.join(", ",
					Arrays.stream(AtomElement.values())
							.map(e -> e.name().toLowerCase(Locale.ROOT))
							.toList());
			source.sendFailure(Component.literal("Unknown element: " + elementName + ". Available: " + available));
			return 0;
		}

		AtomElement element = elementOpt.get();
		ScaleLayer layer = QuantumRealmDimension.getLayer(source.getLevel());
		RenderMode renderMode = switch (layer) {
			case NUCLEAR -> RenderMode.NUCLEUS_ONLY;
			case QUARK -> RenderMode.QUARK_INTERIOR;
			case OVERWORLD, ATOMIC -> RenderMode.ATOMIC_CLOUD;
		};

		Vec3 spawnPos = source.getPosition().add(0.0, 14.0 * size, 0.0);
		if (source.getEntity() instanceof ServerPlayer player) {
			Vec3 look = player.getLookAngle();
			double forwardDist = renderMode == RenderMode.QUARK_INTERIOR ? 0.0 : 18.0 * size;
			spawnPos = player.getEyePosition().add(look.scale(forwardDist));
		}

		AtomEntity atom = new AtomEntity(QuantumAtomMod.ATOM_ENTITY, source.getLevel());
		atom.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
		atom.setElement(element);
		atom.setAtomScale(size);
		atom.setRenderMode(renderMode);
		atom.setSourceLabel(element.isotopeName());
		source.getLevel().addFreshEntity(atom);

		source.sendSuccess(() -> Component.literal(
				String.format("Spawned %s (%s) [%s | %dp+, %dn0, %s, scale=%.2f] at (%.1f, %.1f, %.1f)",
						element.isotopeName(), element.symbol(), renderMode.name(),
						element.protons(), element.neutrons(), element.electronConfig(),
						size, atom.getX(), atom.getY(), atom.getZ())
		), true);
		return 1;
	}

	private static int clearAtoms(CommandSourceStack source) {
		Vec3 pos = source.getPosition();
		AABB box = AABB.ofSize(pos, 320.0, 320.0, 320.0);
		List<AtomEntity> atoms = source.getLevel().getEntitiesOfClass(AtomEntity.class, box);
		atoms.forEach(AtomEntity::discard);
		int count = atoms.size();
		source.sendSuccess(() -> Component.literal("Removed " + count + " subatomic entity/entities."), true);
		return count;
	}

	private static int setPlayerScale(CommandSourceStack source, double scale) {
		if (!(source.getEntity() instanceof ServerPlayer player)) {
			source.sendFailure(Component.literal("This command must be run by a player."));
			return 0;
		}
		if (QuantumPortalManager.isTransitioning(player.getUUID())) {
			source.sendFailure(Component.literal("Cannot change scale mid-transition."));
			return 0;
		}

		ScaleLayer layer = QuantumRealmDimension.getLayer(player.level());
		if (scale < ScaleManager.SHRINK_DIVE_THRESHOLD && layer != ScaleLayer.QUARK) {
			ScaleManager.setScale(player, scale);
			source.sendSuccess(() -> Component.literal(
					String.format("Shrinking to %.4fx — crossing %.2fx will dive seamlessly into %s!",
							scale, ScaleManager.SHRINK_DIVE_THRESHOLD, layer.deeper().displayName())
			), false);
			return 1;
		}
		if (scale > ScaleManager.GROW_ASCEND_THRESHOLD && layer != ScaleLayer.OVERWORLD) {
			ScaleManager.setScale(player, scale);
			source.sendSuccess(() -> Component.literal(
					String.format("Growing to %.4fx — crossing %.2fx will ascend seamlessly to %s!",
							scale, ScaleManager.GROW_ASCEND_THRESHOLD, layer.shallower().displayName())
			), false);
			return 1;
		}

		if (layer == ScaleLayer.OVERWORLD && Math.abs(scale - 1.0) < 0.01) {
			ScaleManager.resetToMacroscopic(player);
		} else {
			ScaleManager.setScale(player, scale);
		}
		source.sendSuccess(() -> Component.literal(
				String.format("Set scale to %.4fx (%s)", scale, ScaleManager.describeScale(scale))
		), false);
		return 1;
	}

	private static int giveShrinker(CommandSourceStack source) {
		if (!(source.getEntity() instanceof ServerPlayer player)) {
			source.sendFailure(Component.literal("This command must be run by a player."));
			return 0;
		}
		player.getInventory().add(new ItemStack(QuantumAtomMod.QUANTUM_SHRINKER));
		source.sendSuccess(() -> Component.literal(
				"Given Quantum Shrinker! Right-Click to shrink deeper (Overworld -> Atomic -> Nuclear -> Quark); Shift+Right-Click to grow back up."
		), false);
		return 1;
	}
}
