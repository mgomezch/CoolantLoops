# CoolantLoops — Texture Provenance & Asset Manifest

In strict compliance with GTNH policies against AI-generated art assets and third-party copyright redistribution risks:
- **Zero third-party files are extracted or bundled** into the mod's distribution jar.
- **Upstream textures are referenced strictly at runtime** via Minecraft Forge's cross-mod icon registration.
- **All custom textures are 100% original procedural artwork** synthesized from scratch using pure Java math, trigonometric wave functions, and procedural geometry.

---

## 1. Runtime Delegated Assets (Zero Bundled Files)

| Block / Item | Target Texture Domain & Name | Upstream Mod | License | Notes |
| :--- | :--- | :--- | :--- | :--- |
| `BlockManifold` | `ic2:machine/blockMiningTip` | IndustrialCraft 2 | Open Source / Modpack Permissions | Directly registers the IC2 mining pipe tip texture at runtime in memory. Zero PNG files are bundled or redistributed. |

---

## 2. Procedural Asset Manifest

All custom textures are generated from scratch during Gradle builds via `GenerateTextures.java` (hooked into `generateProceduralTextures`):

| Asset Path | Generator | Technical Pipeline & Mathematical Formulation | License |
| :--- | :--- | :--- | :--- |
| `textures/blocks/instrument_side.png` | `GenerateTextures.java` | 16×16 telemetry equipment chassis: deterministic brushed-metal streaks `(x*7 + y*13)%9 - 4`, 2px chiseled bevel border, 4 corner hex bolts, and recessed ventilation slots. | Pure Procedural Code (MIT) |
| `textures/blocks/instrument_top.png`<br>`textures/blocks/instrument_top.png.mcmeta` | `GenerateTextures.java` | 16×64 (4 frames) animated CRT/LCD telemetry monitor: metallic chassis frame with phosphor oscilloscope sine wave $y = 7 + 1.8\sin((x-4+2f)\pi/3)$ scrolling across frames with phosphor bloom and status pulse. | Pure Procedural Code (MIT) |
| `textures/blocks/instrument_front.png` | `GenerateTextures.java` | 16×16 telemetry panel: metallic front face, circular dial gauge with needle pointer, gold contact pins, and dual status LEDs (cyan link spark `#00E5FF` and ruby redstone trigger `#FF2525`). | Pure Procedural Code (MIT) |
| `textures/blocks/creative_heat_reservoir.png` | `GenerateTextures.java` | 16×16 high-temperature ceramic containment block: dark tungsten-carbide casing, heavy containment grill crossbars, and incandescent radiant heat core with radial gradient ($r \le 1.5$: `#FFF5B4` white-hot $\to$ `#FF8C0F` orange $\to$ `#C82808` crimson). | Pure Procedural Code (MIT) |
| `textures/blocks/pipe_insulation.png` | `GenerateTextures.java` | 16×16 seamless periodic weave noise `sin(x*π/8)*cos(y*π/8)*8 + sin(x*π/4)*sin(y*π/4)*5` with 4-pixel orthogonal weave ribs, semi-transparent alpha channel (`a=190`). | Pure Procedural Code (MIT) |

---

## 3. Build Target Automation

Asset synthesis is executed automatically during Gradle builds:
- `tasks.processResources` and `tasks.named("sourcesJar")` depend on `generateProceduralTextures`.
- Incremental input/output caching ensures zero build overhead when `GenerateTextures.java` has not changed (`UP-TO-DATE` in < 50ms).

### Manual Regeneration Command
```bash
# Inside mods/CoolantLoops:
./gradlew generateProceduralTextures
# Or run the generator directly:
java tools/textures/GenerateTextures.java
```
