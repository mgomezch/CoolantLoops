# Texture provenance

| Asset path | Generator | Approach |
| :--- | :--- | :--- |
| `textures/blocks/instrument_side.png` | `GenerateTextures.java` | 16×16 telemetry equipment chassis: brushed-metal streaks `(x*7 + y*13)%9 - 4`, 2px chiseled bevel border, 4 corner hex bolts, and recessed ventilation slots. |
| `textures/blocks/instrument_top.png`<br>`textures/blocks/instrument_top.png.mcmeta` | `GenerateTextures.java` | 16×64 (4 frames) animated CRT/LCD telemetry monitor: metallic chassis frame with phosphor oscilloscope sine wave $y = 7 + 1.8\sin((x-4+2f)\pi/3)$ scrolling across frames with phosphor bloom and status pulse. |
| `textures/blocks/instrument_front.png` | `GenerateTextures.java` | 16×16 telemetry panel: metallic front face, circular dial gauge with needle pointer, gold contact pins, and dual status LEDs (cyan link spark `#00E5FF` and ruby redstone trigger `#FF2525`). |
| `textures/blocks/creative_heat_reservoir.png` | `GenerateTextures.java` | 16×16 high-temperature ceramic containment block: dark tungsten-carbide casing, heavy containment grill crossbars, and incandescent radiant heat core with radial gradient ($r \le 1.5$: `#FFF5B4` white-hot $\to$ `#FF8C0F` orange $\to$ `#C82808` crimson). |
| `textures/blocks/pipe_insulation.png` | `GenerateTextures.java` | 16×16 seamless periodic weave noise `sin(x*π/8)*cos(y*π/8)*8 + sin(x*π/4)*sin(y*π/4)*5` with 4-pixel orthogonal weave ribs, semi-transparent alpha channel (`a=190`). |
| `textures/blocks/overlay_pressurized_in.png` | `GenerateTextures.java` | 16×16 pressurized input hatch overlay: 4 directional chevron arrowheads in royal purple (`#9333EA`) pointing inward toward center `(7.5, 7.5)`. |
| `textures/blocks/overlay_pressurized_out.png` | `GenerateTextures.java` | 16×16 pressurized output hatch overlay: 4 directional chevron arrowheads in royal purple (`#9333EA`) pointing outward away from center `(7.5, 7.5)`. |

Manual regeneration:

```bash
./gradlew generateProceduralTextures
```

Or run the generator directly:
```
java tools/textures/GenerateTextures.java
```
