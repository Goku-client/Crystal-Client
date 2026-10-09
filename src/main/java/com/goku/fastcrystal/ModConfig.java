package com.goku.fastcrystal;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public class ModConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("fastcrystal.json");
	private static ModConfig instance = new ModConfig();

	public boolean fastPlace = true;
	public boolean combo = true;
	public boolean anchor = true;
	public boolean rainbow = true;
	public boolean aim = false;
	public boolean attack = false;
	public boolean allowServers = false;
	public int r = 255, g = 90, b = 40;

	public static ModConfig get() { return instance; }

	public int accent() { return 0xFF000000 | (r << 16) | (g << 8) | b; }

	public static void load() {
		try {
			if (Files.exists(FILE)) {
				ModConfig c = GSON.fromJson(Files.readString(FILE), ModConfig.class);
				if (c != null) instance = c;
			}
		} catch (Exception e) {
			System.err.println("[FastCrystal] Could not read config: " + e);
		}
	}

	public static void save() {
		try {
			Files.writeString(FILE, GSON.toJson(instance));
		} catch (Exception e) {
			System.err.println("[FastCrystal] Could not save config: " + e);
		}
	}
}
