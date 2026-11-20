package com.quantumatom.item;

import com.quantumatom.portal.QuantumPortalManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Handheld Ant-Man style Quantum Shrinker driving seamless multi-dimension zoom transitions
 * across the 4 subatomic scale tiers:
 * {@code Overworld (10^0 m) <-> Atomic Realm (10^-10 m) <-> Nuclear Realm (10^-15 m) <-> Quark Realm (10^-18 m)}.
 * <ul>
 *   <li><b>Right-Click:</b> Shrink deeper one scale layer with cinematic FOV warp, radial speed-lines,
 *       layered woosh audio, and zero-loading-screen Immersive Portals dimension swap.</li>
 *   <li><b>Shift + Right-Click:</b> Grow back up one scale layer toward the macroscopic Overworld.</li>
 * </ul>
 */
public class QuantumShrinkerItem extends Item {
	public QuantumShrinkerItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
			boolean started = serverPlayer.isShiftKeyDown()
					? QuantumPortalManager.startAscendTransition(serverPlayer)
					: QuantumPortalManager.startDiveTransition(serverPlayer);

			if (started) {
				// Cooldown covers the 18-tick cinematic zoom transition to prevent accidental double-triggers
				serverPlayer.getCooldowns().addCooldown(this, QuantumPortalManager.TRANSITION_DURATION_TICKS + 2);
			} else {
				serverPlayer.getCooldowns().addCooldown(this, 8);
			}
		}
		return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
	}
}
