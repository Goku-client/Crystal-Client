package com.goku.fastcrystal;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.lwjgl.glfw.GLFW;

/**
 * Safe Anchor: press once to place Respawn Anchor + Glowstone shield + charge it.
 * Press again to detonate (only if your health is high enough).
 * Works in Overworld/End only (anchors do not explode in the Nether).
 */
public class AnchorHandler {

	private enum Stage { IDLE, PLACE_ANCHOR, PLACE_SHIELD, CHARGE, ARMED, DETONATE, RESTORE }

	private static final float MIN_HEALTH = 14.0f;

	public static KeyBinding anchorKey;
	private Stage stage = Stage.IDLE;
	private BlockPos anchorPos;
	private BlockPos shieldPos;
	private int previousSlot;
	private int wait;

	public AnchorHandler(KeyBinding.Category category) {
		anchorKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"key.fastcrystal.anchor", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V, category));
		ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
	}

	private void onTick(MinecraftClient mc) {
		ClientPlayerEntity p = mc.player;
		if (p == null || mc.world == null || mc.interactionManager == null) { stage = Stage.IDLE; return; }

		if (wait > 0 && stage != Stage.IDLE && stage != Stage.ARMED) { wait--; return; }

		boolean pressed = false;
		while (anchorKey.wasPressed()) pressed = true;

		switch (stage) {
			case IDLE -> { if (pressed && ModConfig.get().anchor) start(mc, p); }
			case ARMED -> {
				if (!pressed) return;
				if (!mc.world.getBlockState(anchorPos).isOf(Blocks.RESPAWN_ANCHOR)) { stage = Stage.IDLE; return; }
				if (p.getHealth() + p.getAbsorptionAmount() < MIN_HEALTH) {
					p.sendMessage(Text.literal("Health too low to detonate. Heal first, sir."), true);
					return;
				}
				stage = Stage.DETONATE;
			}
			case PLACE_ANCHOR -> {
				int slot = find(p, Items.RESPAWN_ANCHOR);
				if (slot < 0) { abort(p, "Lost the anchor item."); return; }
				p.getInventory().setSelectedSlot(slot);
				if (mc.crosshairTarget instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
					mc.interactionManager.interactBlock(p, Hand.MAIN_HAND, hit);
					p.swingHand(Hand.MAIN_HAND);
				}
				stage = Stage.PLACE_SHIELD; wait = 1;
			}
			case PLACE_SHIELD -> {
				if (!mc.world.getBlockState(anchorPos).isOf(Blocks.RESPAWN_ANCHOR)) { abort(p, "Anchor was not placed."); return; }
				int slot = find(p, Items.GLOWSTONE);
				if (slot < 0) { abort(p, "Lost the glowstone."); return; }
				p.getInventory().setSelectedSlot(slot);
				BlockPos below = shieldPos.down();
				if (!mc.world.getBlockState(shieldPos).isAir()) {
					// Something already blocks that side, which shields us anyway.
				} else if (mc.world.getBlockState(below).isSolidBlock(mc.world, below)) {
					BlockHitResult h = new BlockHitResult(
						Vec3d.ofCenter(below).add(0, 0.5, 0), Direction.UP, below, false);
					mc.interactionManager.interactBlock(p, Hand.MAIN_HAND, h);
					p.swingHand(Hand.MAIN_HAND);
				} else {
					abort(p, "No ground to place the shield on. Pick a flatter spot."); return;
				}
				stage = Stage.CHARGE; wait = 1;
			}
			case CHARGE -> {
				int slot = find(p, Items.GLOWSTONE);
				if (slot < 0) { abort(p, "No glowstone left to charge."); return; }
				p.getInventory().setSelectedSlot(slot);
				BlockHitResult h = new BlockHitResult(
					Vec3d.ofCenter(anchorPos).add(0, 0.5, 0), Direction.UP, anchorPos, false);
				mc.interactionManager.interactBlock(p, Hand.MAIN_HAND, h);
				p.swingHand(Hand.MAIN_HAND);
				p.getInventory().setSelectedSlot(previousSlot);
				p.sendMessage(Text.literal("Anchor armed. Take cover, then press the key again."), true);
				stage = Stage.ARMED;
			}
			case DETONATE -> {
				int safe = findNonGlowstone(p);
				p.getInventory().setSelectedSlot(safe);
				BlockHitResult h = new BlockHitResult(
					Vec3d.ofCenter(anchorPos).add(0, 0.5, 0), Direction.UP, anchorPos, false);
				mc.interactionManager.interactBlock(p, Hand.MAIN_HAND, h);
				p.swingHand(Hand.MAIN_HAND);
				stage = Stage.RESTORE; wait = 1;
			}
			case RESTORE -> {
				p.getInventory().setSelectedSlot(previousSlot);
				stage = Stage.IDLE;
			}
		}
	}

	private void start(MinecraftClient mc, ClientPlayerEntity p) {
		if (mc.world.getRegistryKey() == World.NETHER) {
			p.sendMessage(Text.literal("Anchors do not explode in the Nether."), true); return;
		}
		if (!(mc.crosshairTarget instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
			p.sendMessage(Text.literal("Look at a block first."), true); return;
		}
		int anchorSlot = find(p, Items.RESPAWN_ANCHOR);
		int glowSlot = find(p, Items.GLOWSTONE);
		if (anchorSlot < 0 || glowSlot < 0 || p.getInventory().getStack(glowSlot).getCount() < 2) {
			p.sendMessage(Text.literal("Need a Respawn Anchor and at least 2 Glowstone in your hotbar."), true); return;
		}
		anchorPos = hit.getBlockPos().offset(hit.getSide());
		Vec3d toPlayer = p.getEntityPos().subtract(Vec3d.ofCenter(anchorPos));
		Direction side = Direction.getFacing(toPlayer.x, 0, toPlayer.z);
		shieldPos = anchorPos.offset(side);
		previousSlot = p.getInventory().getSelectedSlot();
		stage = Stage.PLACE_ANCHOR;
		wait = 0;
	}

	private void abort(ClientPlayerEntity p, String msg) {
		p.getInventory().setSelectedSlot(previousSlot);
		p.sendMessage(Text.literal(msg), true);
		stage = Stage.IDLE;
	}

	private static int find(ClientPlayerEntity p, Item item) {
		for (int i = 0; i < 9; i++) if (p.getInventory().getStack(i).isOf(item)) return i;
		return -1;
	}

	private static int findNonGlowstone(ClientPlayerEntity p) {
		int cur = p.getInventory().getSelectedSlot();
		if (!p.getInventory().getStack(cur).isOf(Items.GLOWSTONE)) return cur;
		for (int i = 0; i < 9; i++) if (!p.getInventory().getStack(i).isOf(Items.GLOWSTONE)) return i;
		return cur;
	}
}
