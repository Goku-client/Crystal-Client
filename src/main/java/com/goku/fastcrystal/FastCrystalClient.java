package com.goku.fastcrystal;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

public class FastCrystalClient implements ClientModInitializer {

	private enum Stage { IDLE, PLACE_OBSIDIAN, PLACE_CRYSTAL, RESTORE }

	public static KeyBinding comboKey;
	public static KeyBinding toggleKey;
	public static KeyBinding menuKey;

	private Stage stage = Stage.IDLE;
	private BlockPos obsidianPos;
	private BlockHitResult obsidianHit;
	private int previousSlot;
	private int waitTicks;

	@Override
	public void onInitializeClient() {
		ModConfig.load();
		KeyBinding.Category category = KeyBinding.Category.create(Identifier.of("fastcrystal", "main"));

		comboKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"key.fastcrystal.combo", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R, category));
		toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"key.fastcrystal.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, category));

		menuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"key.fastcrystal.menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, category));
		new AnchorHandler(category);
		new AimHandler(category);
		ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
	}

	private void onTick(MinecraftClient mc) {
		ClientPlayerEntity player = mc.player;
		if (player == null || mc.world == null || mc.interactionManager == null) {
			stage = Stage.IDLE;
			return;
		}

		while (menuKey.wasPressed()) {
			mc.setScreen(new GokuScreen(null));
			return;
		}

		while (toggleKey.wasPressed()) {
			ModConfig.get().fastPlace = !ModConfig.get().fastPlace;
			ModConfig.save();
			player.sendMessage(Text.literal("Fast Crystal Place: " + (ModConfig.get().fastPlace ? "ON" : "OFF")), true);
		}

		// Combo state machine runs first so it is never interrupted.
		if (stage != Stage.IDLE) {
			runCombo(mc, player);
			return;
		}

		while (comboKey.wasPressed()) {
			if (ModConfig.get().combo) startCombo(mc, player);
		}

		// Fast place: hold right-click with a crystal, places every tick instead of every 4.
		if (ModConfig.get().fastPlace
			&& mc.options.useKey.isPressed()
			&& mc.currentScreen == null
			&& player.getMainHandStack().isOf(Items.END_CRYSTAL)
			&& mc.crosshairTarget instanceof BlockHitResult hit
			&& hit.getType() == HitResult.Type.BLOCK) {

			var state = mc.world.getBlockState(hit.getBlockPos());
			if (state.isOf(Blocks.OBSIDIAN) || state.isOf(Blocks.BEDROCK)) {
				mc.interactionManager.interactBlock(player, Hand.MAIN_HAND, hit);
				player.swingHand(Hand.MAIN_HAND);
			}
		}
	}

	private void startCombo(MinecraftClient mc, ClientPlayerEntity player) {
		if (!(mc.crosshairTarget instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
			player.sendMessage(Text.literal("Look at a block first."), true);
			return;
		}
		if (findHotbar(player, Items.OBSIDIAN) < 0 || findHotbar(player, Items.END_CRYSTAL) < 0) {
			player.sendMessage(Text.literal("Need Obsidian and End Crystal in your hotbar."), true);
			return;
		}

		// If already looking at obsidian/bedrock, just drop the crystal on it.
		var targetState = mc.world.getBlockState(hit.getBlockPos());
		if (targetState.isOf(Blocks.OBSIDIAN) || targetState.isOf(Blocks.BEDROCK)) {
			obsidianPos = hit.getBlockPos();
			obsidianHit = new BlockHitResult(
				Vec3d.ofCenter(obsidianPos).add(0, 0.5, 0), Direction.UP, obsidianPos, false);
			previousSlot = player.getInventory().getSelectedSlot();
			stage = Stage.PLACE_CRYSTAL;
			waitTicks = 0;
			return;
		}

		obsidianPos = hit.getBlockPos().offset(hit.getSide());
		obsidianHit = new BlockHitResult(
			Vec3d.ofCenter(obsidianPos).add(0, 0.5, 0), Direction.UP, obsidianPos, false);
		previousSlot = player.getInventory().getSelectedSlot();
		stage = Stage.PLACE_OBSIDIAN;
		waitTicks = 0;
	}

	private void runCombo(MinecraftClient mc, ClientPlayerEntity player) {
		switch (stage) {
			case PLACE_OBSIDIAN -> {
				int slot = findHotbar(player, Items.OBSIDIAN);
				if (slot < 0 || !(mc.crosshairTarget instanceof BlockHitResult hit)) { stage = Stage.RESTORE; return; }
				player.getInventory().setSelectedSlot(slot);
				mc.interactionManager.interactBlock(player, Hand.MAIN_HAND, hit);
				player.swingHand(Hand.MAIN_HAND);
				stage = Stage.PLACE_CRYSTAL;
				waitTicks = 1; // let the block register
			}
			case PLACE_CRYSTAL -> {
				if (waitTicks-- > 0) return;
				int slot = findHotbar(player, Items.END_CRYSTAL);
				if (slot < 0) { stage = Stage.RESTORE; return; }
				if (mc.world.getBlockState(obsidianPos).isOf(Blocks.OBSIDIAN)
					|| mc.world.getBlockState(obsidianPos).isOf(Blocks.BEDROCK)) {
					player.getInventory().setSelectedSlot(slot);
					mc.interactionManager.interactBlock(player, Hand.MAIN_HAND, obsidianHit);
					player.swingHand(Hand.MAIN_HAND);
				}
				stage = Stage.RESTORE;
			}
			case RESTORE -> {
				player.getInventory().setSelectedSlot(previousSlot);
				stage = Stage.IDLE;
			}
			default -> stage = Stage.IDLE;
		}
	}

	private static int findHotbar(ClientPlayerEntity player, net.minecraft.item.Item item) {
		for (int i = 0; i < 9; i++) {
			if (player.getInventory().getStack(i).isOf(item)) return i;
		}
		return -1;
	}
}
