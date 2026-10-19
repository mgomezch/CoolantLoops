# Provenance

| Asset | Generator |
| :--- | :--- |
| `textures/blocks/instrument_side.png` | `GenerateTextures.java` |
| `textures/blocks/instrument_top.png`<br>`textures/blocks/instrument_top.png.mcmeta` | `GenerateTextures.java` |
| `textures/blocks/instrument_front.png` | `GenerateTextures.java` |
| `textures/blocks/creative_heat_reservoir.png` | `GenerateTextures.java` |
| `textures/blocks/pipe_insulation.png` | `GenerateTextures.java` |
| `textures/blocks/overlay_pressurized_in.png` | `GenerateTextures.java` |
| `textures/blocks/overlay_pressurized_out.png` | `GenerateTextures.java` |

Manual regeneration:

```bash
./gradlew generateProceduralTextures
```

Or run the generator directly:
```
java tools/textures/GenerateTextures.java
```
