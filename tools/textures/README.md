# CoolantLoops — Texture Provenance & Asset Manifest

In strict compliance with GTNH policies against AI-generated art assets, all visual media in **CoolantLoops** are either extracted directly from existing open-source Minecraft mods (GregTech 5 Unofficial, IndustrialCraft 2) or procedurally composited/synthesized using deterministic code.

## Asset Provenance Manifest

| Asset Path | Origin / Generator | Provenance / Technical Pipeline | Upstream Source & License |
| :--- | :--- | :--- | :--- |
| `textures/blocks/instrument_side.png` | `build.gradle.kts` (`composeInstrumentTextures`) | Direct extraction of `BLOCK_STEELPREIN.png` (Reinforced Steel Casing texture). | GregTech 5 Unofficial (LGPL-3.0) |
| `textures/blocks/instrument_top.png`<br>`textures/blocks/instrument_top.png.mcmeta` | `build.gradle.kts` (`composeInstrumentTextures`) | Automated 2D composite: repeats `BLOCK_STEELPREIN.png` vertically across 4 frames and overlays `OVERLAY_SCREEN.png` animated monitor telemetry. Copies `.mcmeta` animation config. | GregTech 5 Unofficial (LGPL-3.0) |
| `textures/blocks/instrument_front.png` | `build.gradle.kts` (`composeInstrumentTextures`) | Automated 2D composite: overlays `OVERLAY_REDSTONE_TRANSMITTER.png` onto `BLOCK_STEELPREIN.png` and draws a 3-layer anti-aliased red/amber status LED indicator (`#A00000`, `#FF1E1E`, `#FFB4B4`). | GregTech 5 Unofficial (LGPL-3.0) + Procedural Code |
| `textures/blocks/manifold.png` | `build.gradle.kts` (`composeInstrumentTextures`) | Direct extraction of `assets/ic2/textures/blocks/machine/blockMiningTip.png` (Mining Pipe Tip texture) from the IndustrialCraft 2 jar on `compileClasspath`. | IndustrialCraft 2 (Open Source / Modpack Permissions) |
| `textures/blocks/creative_heat_reservoir.png` | `build.gradle.kts` (`composeInstrumentTextures`) | Automated 2D composite: composites `MACHINE_HEATPROOFCASING.png` base casing with `OVERLAY_FRONT_HEAT_EXCHANGER.png` icon overlay. | GregTech 5 Unofficial (LGPL-3.0) |
| `textures/blocks/pipe_insulation.png` | `build.gradle.kts` (`composeInstrumentTextures`) | Pure procedural synthesis: 16×16 seamless periodic trigonometric weave noise `sin(x*π/8)*cos(y*π/8)*8 + sin(x*π/4)*sin(y*π/4)*5` with 4-pixel orthogonal weave ribs, semi-transparent alpha channel (`a=190`). | Procedural Code (MIT / Project Author) |

## Build Target Automation

Asset composition is executed automatically during Gradle builds:
- `tasks.processResources` depends on `composeInstrumentTextures`.
- The task dynamically accesses GT5 and IC2 jars from Gradle's dependency cache (`compileClasspath`) or local dev paths, extracts baseline assets, performs image composition using standard Java `BufferedImage` / `Graphics2D` / `ImageIO`, and writes the assets directly into `src/main/resources/assets/coolantloops/textures/blocks/`.

### Manual Regeneration Command
```bash
# Inside mods/CoolantLoops:
./gradlew composeInstrumentTextures
```
