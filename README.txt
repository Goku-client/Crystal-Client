FAST CRYSTAL (Fabric, Minecraft Java 1.21.11, client-side)

BUILD
1. Install JDK 21.
2. Easiest: generate a blank project at https://fabricmc.net/develop (1.21.11), then copy
   src/ and gradle.properties from this folder over it (keep the generated gradle wrapper).
   Or, if you have Gradle installed: run `gradle wrapper` here, then `./gradlew build`.
3. Jar appears in build/libs/ (use the one WITHOUT -sources). Put it in .minecraft/mods with Fabric API.

CONTROLS (rebindable in Options > Controls > Fast Crystal)
- Hold Right-Click with End Crystal: places one per tick on obsidian/bedrock.
- R: place obsidian on the block you look at, then an End Crystal on top (needs both in hotbar).
      If you are already looking at obsidian/bedrock, it just places the crystal.
- G: toggle fast placement on/off.

If versions in gradle.properties don't resolve, copy the current ones from fabricmc.net/develop.
- V: Safe Anchor. First press: places a Respawn Anchor where you look, a Glowstone shield block on the
     side facing you, and charges it. Second press: detonates it (refuses if health is under 7 hearts).
     Needs Respawn Anchor + 2 Glowstone in hotbar. Overworld/End only (anchors don't explode in the Nether).

GUI
- Right Shift opens the GOKU menu (rebindable). Toggle each feature, click a key button then press a
  key to rebind it (Backspace unbinds, Esc cancels), pick a theme colour with the RGB sliders or presets,
  and switch the rainbow title on/off. Settings save to config/fastcrystal.json.

AUTO AIM / AUTO ATTACK (Z / X, or the GUI)
- Auto Aim smoothly turns toward the nearest player within ~35 degrees of your crosshair (range 6).
- Auto Attack hits the player under your crosshair once the weapon cooldown is full.
- Both are off by default and run in singleplayer. For your own server, switch on "Use on Private Server" in the GUI.
- Only use that on a server you own or where everyone agrees. If the server runs an anticheat plugin, exempt yourself or it may kick you.
- Right Shift opens the GUI in-game, and closes it again.

BUILD ON A PHONE (no PC)
1. Make a free GitHub account, create a new repository, upload ALL files from this zip (keep the .github folder).
2. Open the Actions tab > "Build mod" > Run workflow. Wait ~3 minutes.
3. Open the finished run > Artifacts > download fastcrystal-jar (zip). Extract it; use the jar WITHOUT -sources.
4. Copy the jar and Fabric API (1.21.11) into your launcher's mods folder.
