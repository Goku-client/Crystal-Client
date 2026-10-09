package com.goku.fastcrystal;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

/**
 * Auto Aim: gently turns toward the player you are already looking near.
 * Auto Attack: swings at the player under your crosshair once the cooldown is full.
 * Runs in singleplayer by default. Enable "Use on Private Server" in the menu for your own server.
 */
public class AimHandler {

	private static final double RANGE = 6.0;
	private static final double MAX_ANGLE = 35.0;   // degrees from your crosshair
	private static final double SMOOTHING = 0.35;   // fraction of the gap closed each tick
	private static final double MAX_STEP = 25.0;    // max degrees per tick

	public static KeyBinding aimKey;
	public static KeyBinding attackKey;

	public AimHandler(KeyBinding.Category category) {
		aimKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"key.fastcrystal.aim", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_Z, category));
		attackKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"key.fastcrystal.attack", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_X, category));
		ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
	}

	private void onTick(MinecraftClient mc) {
		ClientPlayerEntity p = mc.player;
		if (p == null || mc.world == null) return;
		ModConfig cfg = ModConfig.get();

		while (aimKey.wasPressed()) {
			cfg.aim = !cfg.aim; ModConfig.save();
			p.sendMessage(Text.literal("Auto Aim: " + (cfg.aim ? "ON" : "OFF")), true);
		}
		while (attackKey.wasPressed()) {
			cfg.attack = !cfg.attack; ModConfig.save();
			p.sendMessage(Text.literal("Auto Attack: " + (cfg.attack ? "ON" : "OFF")), true);
		}

		if (mc.currentScreen != null || mc.interactionManager == null) return;
		if (!mc.isInSingleplayer() && !cfg.allowServers) return; // multiplayer only if you enabled it in the menu
		if (!p.isAlive() || p.isSpectator()) return;

		if (cfg.aim) {
			PlayerEntity target = findTarget(mc, p);
			if (target != null) {
				Vec3d eye = p.getEyePos();
				Vec3d to = target.getBoundingBox().getCenter().subtract(eye);
				double flat = Math.sqrt(to.x * to.x + to.z * to.z);
				float wantYaw = (float) (Math.toDegrees(Math.atan2(to.z, to.x)) - 90.0);
				float wantPitch = (float) -Math.toDegrees(Math.atan2(to.y, flat));

				float dYaw = MathHelper.wrapDegrees(wantYaw - p.getYaw());
				float dPitch = wantPitch - p.getPitch();
				float stepYaw = (float) MathHelper.clamp(dYaw * SMOOTHING, -MAX_STEP, MAX_STEP);
				float stepPitch = (float) MathHelper.clamp(dPitch * SMOOTHING, -MAX_STEP, MAX_STEP);

				p.setYaw(p.getYaw() + stepYaw);
				p.setPitch(MathHelper.clamp(p.getPitch() + stepPitch, -90f, 90f));
			}
		}

		if (cfg.attack
			&& mc.crosshairTarget instanceof EntityHitResult hit
			&& hit.getEntity() instanceof PlayerEntity victim
			&& victim != p && victim.isAlive() && !victim.isSpectator()
			&& p.getAttackCooldownProgress(0.5f) >= 1.0f) {
			mc.interactionManager.attackEntity(p, victim);
			p.swingHand(Hand.MAIN_HAND);
		}
	}

	private PlayerEntity findTarget(MinecraftClient mc, ClientPlayerEntity p) {
		Vec3d eye = p.getEyePos();
		Vec3d look = p.getRotationVec(1.0f);
		PlayerEntity best = null;
		double bestAngle = MAX_ANGLE;
		for (PlayerEntity other : mc.world.getPlayers()) {
			if (other == p || !other.isAlive() || other.isSpectator()) continue;
			Vec3d to = other.getBoundingBox().getCenter().subtract(eye);
			if (to.lengthSquared() > RANGE * RANGE) continue;
			double angle = Math.toDegrees(Math.acos(MathHelper.clamp(look.dotProduct(to.normalize()), -1.0, 1.0)));
			if (angle < bestAngle) { bestAngle = angle; best = other; }
		}
		return best;
	}
}
