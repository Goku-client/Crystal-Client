package com.goku.fastcrystal;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.LinkedHashMap;
import java.util.Map;

public class GokuScreen extends Screen {

	private static final int W = 320, H = 378;
	private static final int[][] PRESETS = {
		{255, 60, 60}, {255, 150, 30}, {60, 220, 90}, {40, 200, 255}, {170, 80, 255}, {255, 90, 200}
	};

	private final Screen parent;
	private final Map<KeyBinding, ButtonWidget> keyButtons = new LinkedHashMap<>();
	private KeyBinding listening;
	private int px, py;

	public GokuScreen(Screen parent) {
		super(Text.literal("GOKU"));
		this.parent = parent;
	}

	private static Text colored(String s, int rgb) {
		return Text.literal(s).styled(st -> st.withColor(rgb & 0xFFFFFF));
	}

	@Override
	protected void init() {
		ModConfig cfg = ModConfig.get();
		px = (width - W) / 2;
		py = (height - H) / 2;
		keyButtons.clear();

		int y = py + 62;
		addFeatureRow(y, cfg.fastPlace, v -> cfg.fastPlace = v, FastCrystalClient.toggleKey); y += 24;
		addFeatureRow(y, cfg.combo, v -> cfg.combo = v, FastCrystalClient.comboKey); y += 24;
		addFeatureRow(y, cfg.anchor, v -> cfg.anchor = v, AnchorHandler.anchorKey); y += 24;
		addFeatureRow(y, cfg.aim, v -> cfg.aim = v, AimHandler.aimKey); y += 24;
		addFeatureRow(y, cfg.attack, v -> cfg.attack = v, AimHandler.attackKey); y += 24;
		addDrawableChild(ButtonWidget.builder(stateText(cfg.allowServers), b -> {
			cfg.allowServers = !cfg.allowServers;
			b.setMessage(stateText(cfg.allowServers));
		}).dimensions(px + 150, y, 50, 20).build());
		y += 24;
		addKeyButton(px + 206, y, 100, FastCrystalClient.menuKey); // menu key row
		y += 34;

		// Theme: RGB sliders
		addDrawableChild(new ChannelSlider(px + 18, y, 284, 18, "Red", cfg.r, v -> cfg.r = v)); y += 21;
		addDrawableChild(new ChannelSlider(px + 18, y, 284, 18, "Green", cfg.g, v -> cfg.g = v)); y += 21;
		addDrawableChild(new ChannelSlider(px + 18, y, 284, 18, "Blue", cfg.b, v -> cfg.b = v)); y += 24;

		// Presets
		for (int i = 0; i < PRESETS.length; i++) {
			int[] c = PRESETS[i];
			int rgb = (c[0] << 16) | (c[1] << 8) | c[2];
			addDrawableChild(ButtonWidget.builder(colored("\u25A0\u25A0\u25A0", rgb), b -> {
				cfg.r = c[0]; cfg.g = c[1]; cfg.b = c[2];
				clearAndInit();
			}).dimensions(px + 18 + i * 48, y, 44, 20).build());
		}
		y += 24;

		addDrawableChild(ButtonWidget.builder(rainbowText(cfg.rainbow), b -> {
			cfg.rainbow = !cfg.rainbow;
			b.setMessage(rainbowText(cfg.rainbow));
		}).dimensions(px + 18, y, 140, 20).build());

		addDrawableChild(ButtonWidget.builder(colored("Done", cfg.accent()), b -> close())
			.dimensions(px + 166, y, 136, 20).build());

		refreshKeyButtons();
	}

	private Text rainbowText(boolean on) {
		return colored("Rainbow: " + (on ? "ON" : "OFF"), on ? 0x55FF55 : 0xFF5555);
	}

	private void addFeatureRow(int y, boolean state, java.util.function.Consumer<Boolean> setter, KeyBinding key) {
		boolean[] holder = {state};
		addDrawableChild(ButtonWidget.builder(stateText(state), b -> {
			holder[0] = !holder[0];
			setter.accept(holder[0]);
			b.setMessage(stateText(holder[0]));
		}).dimensions(px + 150, y, 50, 20).build());
		addKeyButton(px + 206, y, 100, key);
	}

	private Text stateText(boolean on) {
		return colored(on ? "ON" : "OFF", on ? 0x55FF55 : 0xFF5555);
	}

	private void addKeyButton(int x, int y, int w, KeyBinding key) {
		ButtonWidget b = ButtonWidget.builder(Text.empty(), btn -> {
			listening = key;
			refreshKeyButtons();
		}).dimensions(x, y, w, 20).build();
		keyButtons.put(key, b);
		addDrawableChild(b);
	}

	private void refreshKeyButtons() {
		int accent = ModConfig.get().accent();
		for (var e : keyButtons.entrySet()) {
			if (e.getKey() == listening) {
				e.getValue().setMessage(colored("> press a key <", 0xFFFF55));
			} else {
				e.getValue().setMessage(colored("[ ", accent).copy()
					.append(e.getKey().getBoundKeyLocalizedText()).append(colored(" ]", accent)));
			}
		}
	}

	@Override
	public boolean keyPressed(KeyInput input) {
		if (listening != null) {
			int code = input.key();
			if (code != GLFW.GLFW_KEY_ESCAPE) {
				InputUtil.Key key = (code == GLFW.GLFW_KEY_BACKSPACE || code == GLFW.GLFW_KEY_DELETE)
					? InputUtil.UNKNOWN_KEY
					: InputUtil.Type.KEYSYM.createFromCode(code);
				listening.setBoundKey(key);
				KeyBinding.updateKeysByCode();
				client.options.write();
			}
			listening = null;
			refreshKeyButtons();
			return true;
		}
		if (InputUtil.Type.KEYSYM.createFromCode(input.key()).getTranslationKey()
			.equals(FastCrystalClient.menuKey.getBoundKeyTranslationKey())) {
			close();
			return true;
		}
		return super.keyPressed(input);
	}

	@Override
	public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
		super.renderBackground(ctx, mouseX, mouseY, delta);
		ModConfig cfg = ModConfig.get();
		int accent = cfg.accent();
		float time = (System.currentTimeMillis() % 6000L) / 6000f;

		// Panel body: dark gradient tinted by the accent colour
		int top = 0xF0000000 | tint(cfg, 0.28f);
		int bottom = 0xF0000000 | tint(cfg, 0.07f);
		ctx.fillGradient(px, py, px + W, py + H, top, bottom);

		// Border
		ctx.fill(px - 2, py - 2, px + W + 2, py, accent);
		ctx.fill(px - 2, py + H, px + W + 2, py + H + 2, accent);
		ctx.fill(px - 2, py, px, py + H, accent);
		ctx.fill(px + W, py, px + W + 2, py + H, accent);

		// Title "GOKU", big, one colour per letter
		String title = "GOKU";
		int scale = 4;
		int tw = textRenderer.getWidth(title);
		int startX = px + (W - tw * scale) / 2;
		ctx.getMatrices().pushMatrix();
		ctx.getMatrices().translate(startX, py + 8);
		ctx.getMatrices().scale(scale, scale);
		int cx = 0;
		for (int i = 0; i < title.length(); i++) {
			String ch = String.valueOf(title.charAt(i));
			int col = cfg.rainbow
				? 0xFF000000 | MathHelper.hsvToRgb((time + i * 0.12f) % 1f, 0.75f, 1f)
				: accent;
			ctx.drawText(textRenderer, ch, cx, 0, col, true);
			cx += textRenderer.getWidth(ch);
		}
		ctx.getMatrices().popMatrix();

		// Colourful stripe under the title
		for (int x = 0; x < W; x += 2) {
			int col = cfg.rainbow
				? 0xFF000000 | MathHelper.hsvToRgb(((float) x / W + time) % 1f, 0.85f, 1f)
				: accent;
			ctx.fill(px + x, py + 46, px + x + 2, py + 49, col);
		}

		// Section labels
		ctx.drawText(textRenderer, "FEATURES", px + 18, py + 53, accent, true);
		ctx.drawText(textRenderer, "ON/OFF", px + 154, py + 53, 0xFFAAAAAA, false);
		ctx.drawText(textRenderer, "KEYBIND", px + 222, py + 53, 0xFFAAAAAA, false);

		int rowY = py + 62;
		String[] names = {"Fast Crystal Place", "Obsidian + Crystal", "Safe Anchor", "Auto Aim", "Auto Attack", "Use on Private Server", "Open This Menu"};
		for (int i = 0; i < names.length; i++) {
			ctx.drawText(textRenderer, names[i], px + 18, rowY + 6 + i * 24, 0xFFFFFFFF, true);
		}
		ctx.drawText(textRenderer, "THEME COLOR", px + 18, rowY + 7 * 24 + 4, accent, true);
		ctx.fill(px + 18, rowY + 7 * 24 + 14, px + 302, rowY + 7 * 24 + 15, accent);
	}

	private static int tint(ModConfig c, float f) {
		return ((int) (c.r * f) << 16) | ((int) (c.g * f) << 8) | (int) (c.b * f);
	}

	@Override
	public void removed() {
		ModConfig.save();
	}

	@Override
	public void close() {
		client.setScreen(parent);
	}

	private static class ChannelSlider extends SliderWidget {
		private final String label;
		private final java.util.function.IntConsumer setter;

		ChannelSlider(int x, int y, int w, int h, String label, int value, java.util.function.IntConsumer setter) {
			super(x, y, w, h, Text.empty(), value / 255.0);
			this.label = label;
			this.setter = setter;
			updateMessage();
		}

		@Override
		protected void updateMessage() {
			setMessage(Text.literal(label + ": " + (int) Math.round(value * 255)));
		}

		@Override
		protected void applyValue() {
			setter.accept((int) Math.round(value * 255));
		}
	}
}
