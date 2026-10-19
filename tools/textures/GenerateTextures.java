package tools.textures;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.imageio.ImageIO;

/**
 * 100% Original Procedural Texture Generator for CoolantLoops.
 *
 * Complies strictly with GTNH policy against AI-generated art assets:
 * Synthesizes all custom block textures from scratch using pure Java geometry,
 * trigonometric wave equations, and procedural color palettes.
 *
 * Zero third-party PNG templates or extracted pixels are used.
 */
public class GenerateTextures {

    public static void main(String[] args) throws IOException {
        Path scriptDir = Paths.get(GenerateTextures.class.getProtectionDomain().getCodeSource().getLocation().getPath());
        File outDir;
        if (args.length > 0) {
            outDir = new File(args[0]);
        } else {
            outDir = new File("src/main/resources/assets/coolantloops/textures/blocks");
        }
        outDir.mkdirs();

        System.out.println("================================================================================");
        System.out.println("         CoolantLoops — Procedural Texture Generation Suite                     ");
        System.out.println("================================================================================");
        System.out.println("Target Directory: " + outDir.getAbsolutePath());

        generateInstrumentSide(new File(outDir, "instrument_side.png"));
        generateInstrumentTop(new File(outDir, "instrument_top.png"), new File(outDir, "instrument_top.png.mcmeta"));
        generateInstrumentFront(new File(outDir, "instrument_front.png"));
        generateCreativeHeatReservoir(new File(outDir, "creative_heat_reservoir.png"));
        generatePipeInsulation(new File(outDir, "pipe_insulation.png"));
        generateOverlayPressurizedIn(new File(outDir, "overlay_pressurized_in.png"));
        generateOverlayPressurizedOut(new File(outDir, "overlay_pressurized_out.png"));

        System.out.println("================================================================================");
        System.out.println("✓ All CoolantLoops textures synthesized successfully from pure procedural code!");
        System.out.println("================================================================================");
    }

    /**
     * 1. instrument_side.png: 16x16 metallic telemetry equipment chassis with brushed streaks and hex bolts.
     */
    private static void generateInstrumentSide(File outFile) throws IOException {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                // Brushed metal noise
                int noise = ((x * 7 + y * 13) % 9) - 4;
                int base = 108 + noise;
                int r = base - 2;
                int g = base + 1;
                int b = base + 6;

                // Outer bevel
                if (x == 0 || y == 0) {
                    r = Math.min(255, r + 35);
                    g = Math.min(255, g + 35);
                    b = Math.min(255, b + 38);
                } else if (x == 15 || y == 15) {
                    r = Math.max(0, r - 45);
                    g = Math.max(0, g - 45);
                    b = Math.max(0, b - 40);
                } else if (x == 1 || y == 1) {
                    r = Math.min(255, r + 15);
                    g = Math.min(255, g + 15);
                    b = Math.min(255, b + 18);
                } else if (x == 14 || y == 14) {
                    r = Math.max(0, r - 25);
                    g = Math.max(0, g - 25);
                    b = Math.max(0, b - 20);
                }

                // Inset horizontal ventilation / expansion slot ribs (rows 6-7 and 10-11)
                if ((y == 6 || y == 10) && x >= 4 && x <= 11) {
                    r = 38; g = 40; b = 46;
                } else if ((y == 7 || y == 11) && x >= 4 && x <= 11) {
                    r = 135; g = 142; b = 152;
                }

                img.setRGB(x, y, (0xFF << 24) | (r << 16) | (g << 8) | b);
            }
        }

        // Corner hex bolts at (2,2), (2,13), (13,2), (13,13)
        int[][] bolts = {{2, 2}, {13, 2}, {2, 13}, {13, 13}};
        for (int[] pt : bolts) {
            int bx = pt[0], by = pt[1];
            img.setRGB(bx, by, 0xFFCAD2DC);
            img.setRGB(bx + 1, by + 1, 0xFF353940);
        }

        ImageIO.write(img, "PNG", outFile);
        System.out.println("  [✓] Wrote instrument_side.png");
    }

    /**
     * 2. instrument_top.png: 16x64 (4 frames) animated CRT/LCD telemetry monitor with scrolling green waveform.
     */
    private static void generateInstrumentTop(File outFile, File metaFile) throws IOException {
        int w = 16;
        int h = 64;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        for (int frame = 0; frame < 4; frame++) {
            int yOffset = frame * 16;

            // Render base chassis
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    int noise = ((x * 11 + y * 7) % 7) - 3;
                    int base = 95 + noise;
                    int r = base - 2;
                    int g = base + 1;
                    int b = base + 5;

                    // Bevel
                    if (x == 0 || y == 0) {
                        r += 30; g += 30; b += 32;
                    } else if (x == 15 || y == 15) {
                        r -= 40; g -= 40; b -= 35;
                    }

                    // Screen bezel rim (3..12)
                    if (x >= 3 && x <= 12 && y >= 3 && y <= 12) {
                        if (x == 3 || y == 3) {
                            r = 42; g = 45; b = 50;
                        } else if (x == 12 || y == 12) {
                            r = 110; g = 118; b = 126;
                        } else {
                            // Dark phosphor screen background
                            r = 8; g = 18; b = 12;
                        }
                    }

                    img.setRGB(x, yOffset + y, (0xFF << 24) | (Math.min(255, Math.max(0, r)) << 16) | (Math.min(255, Math.max(0, g)) << 8) | Math.min(255, Math.max(0, b)));
                }
            }

            // Draw glowing phosphor oscilloscope sine waveform inside (4..11, 4..11)
            for (int x = 4; x <= 11; x++) {
                double phase = (x - 4 + frame * 2.0) * Math.PI / 3.0;
                int waveY = 7 + (int) Math.round(1.8 * Math.sin(phase));
                waveY = Math.max(4, Math.min(11, waveY));

                // Bright core
                img.setRGB(x, yOffset + waveY, 0xFF35F555);

                // Phosphor bloom above/below
                if (waveY > 4 && img.getRGB(x, yOffset + waveY - 1) != 0xFF35F555) {
                    img.setRGB(x, yOffset + waveY - 1, 0xFF146028);
                }
                if (waveY < 11 && img.getRGB(x, yOffset + waveY + 1) != 0xFF35F555) {
                    img.setRGB(x, yOffset + waveY + 1, 0xFF146028);
                }
            }

            // Status blip at top-right of screen (flashes with frame)
            int blipColor = (frame % 2 == 0) ? 0xFF00E5FF : 0xFF0A4050;
            img.setRGB(11, yOffset + 4, blipColor);
        }

        ImageIO.write(img, "PNG", outFile);
        System.out.println("  [✓] Wrote instrument_top.png (16x64 4-frame animation)");

        try (FileWriter fw = new FileWriter(metaFile)) {
            fw.write("{\n  \"animation\": {\n    \"frametime\": 4\n  }\n}\n");
        }
        System.out.println("  [✓] Wrote instrument_top.png.mcmeta");
    }

    /**
     * 3. instrument_front.png: 16x16 telemetry sensor panel with round dial gauge and dual LED cluster.
     */
    private static void generateInstrumentFront(File outFile) throws IOException {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int noise = ((x * 5 + y * 11) % 7) - 3;
                int base = 104 + noise;
                int r = base - 2;
                int g = base;
                int b = base + 5;

                // Bevel
                if (x == 0 || y == 0) {
                    r += 32; g += 32; b += 35;
                } else if (x == 15 || y == 15) {
                    r -= 42; g -= 42; b -= 38;
                }
                img.setRGB(x, y, (0xFF << 24) | (Math.min(255, Math.max(0, r)) << 16) | (Math.min(255, Math.max(0, g)) << 8) | Math.min(255, Math.max(0, b)));
            }
        }

        // Circular telemetry dial gauge (center x=7.5, y=5.5, radius=4)
        for (int y = 2; y <= 9; y++) {
            for (int x = 4; x <= 11; x++) {
                double dist = Math.hypot(x - 7.5, y - 5.5);
                if (dist <= 3.8) {
                    // White dial face with subtle gradient
                    int dialVal = (int) (225 - dist * 10);
                    img.setRGB(x, y, (0xFF << 24) | (dialVal << 16) | (dialVal << 8) | dialVal);
                } else if (dist <= 4.5) {
                    // Dark bezel ring
                    img.setRGB(x, y, 0xFF3A3D44);
                }
            }
        }

        // Gauge needle: center pivot to top-right
        img.setRGB(7, 6, 0xFF202020); // Center pivot
        img.setRGB(8, 5, 0xFFE02020); // Red needle
        img.setRGB(9, 4, 0xFFE02020);
        img.setRGB(6, 7, 0xFF151515); // Pivot shadow

        // Corner mounting bolts
        img.setRGB(2, 2, 0xFFCAD2DC);
        img.setRGB(13, 2, 0xFFCAD2DC);

        // Lower status cluster (y=11..13)
        // LED 1 (Left: Link / Power Status - Cyan)
        img.setRGB(4, 12, 0xFF00E5FF);
        img.setRGB(5, 12, 0xFF00B0D0);
        img.setRGB(4, 13, 0xFF0080A0);
        img.setRGB(4, 12, 0xFFE0FFFF); // Specular spark

        // LED 2 (Right: Redstone Signal - Ruby Red)
        img.setRGB(11, 12, 0xFFFF2525);
        img.setRGB(12, 12, 0xFFC01515);
        img.setRGB(11, 13, 0xFF800808);
        img.setRGB(11, 12, 0xFFFFB0B0); // Specular spark

        // Central gold contact terminal pins at (7,12) and (8,12)
        img.setRGB(7, 12, 0xFFCCA535);
        img.setRGB(8, 12, 0xFFE6C045);
        img.setRGB(7, 13, 0xFF7A6018);
        img.setRGB(8, 13, 0xFF7A6018);

        ImageIO.write(img, "PNG", outFile);
        System.out.println("  [✓] Wrote instrument_front.png");
    }

    /**
     * 4. creative_heat_reservoir.png: 16x16 refractory ceramic containment casing with incandescent radiant thermal core.
     */
    private static void generateCreativeHeatReservoir(File outFile) throws IOException {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int noise = ((x * 9 + y * 7) % 7) - 3;
                int base = 52 + noise;
                int r = base + 4;
                int g = base;
                int b = base - 2;

                // Heavy tungsten-carbide bevel
                if (x == 0 || y == 0) {
                    r += 35; g += 32; b += 28;
                } else if (x == 15 || y == 15) {
                    r -= 30; g -= 30; b -= 28;
                }

                // Inner thermal aperture (3..12)
                if (x >= 3 && x <= 12 && y >= 3 && y <= 12) {
                    double dist = Math.hypot(x - 7.5, y - 7.5);
                    if (dist <= 4.2) {
                        // Incandescent radiant heat core: center gold-white -> orange -> crimson
                        if (dist <= 1.5) {
                            r = 255; g = 245; b = 180; // White-hot core
                        } else if (dist <= 2.8) {
                            r = 255; g = 140; b = 15;  // Radiant orange
                        } else {
                            r = 200; g = 40; b = 8;    // Deep crimson boundary
                        }
                    } else {
                        // Refractory brick rim
                        r = 75; g = 50; b = 40;
                    }
                }

                img.setRGB(x, y, (0xFF << 24) | (Math.min(255, Math.max(0, r)) << 16) | (Math.min(255, Math.max(0, g)) << 8) | Math.min(255, Math.max(0, b)));
            }
        }

        // Heavy containment grill crossbars: (7, 8) columns and rows
        for (int i = 3; i <= 12; i++) {
            img.setRGB(i, 7, 0xFF45484E);
            img.setRGB(i, 8, 0xFF2A2C30);
            img.setRGB(7, i, 0xFF45484E);
            img.setRGB(8, i, 0xFF2A2C30);
        }

        // Center junction node
        img.setRGB(7, 7, 0xFF656A74);
        img.setRGB(8, 8, 0xFF1A1C20);

        ImageIO.write(img, "PNG", outFile);
        System.out.println("  [✓] Wrote creative_heat_reservoir.png");
    }

    /**
     * 5. pipe_insulation.png: 16x16 seamless periodic weave noise with subtle thermal cladding ribs.
     */
    private static void generatePipeInsulation(File outFile) throws IOException {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int weaveX = (x % 4 == 0) ? 10 : 0;
                int weaveY = (y % 4 == 0) ? 10 : 0;
                int noise = (int) ((Math.sin(x * Math.PI / 8.0) * Math.cos(y * Math.PI / 8.0) * 8.0) +
                                  (Math.sin(x * Math.PI / 4.0) * Math.sin(y * Math.PI / 4.0) * 5.0));
                int base = 212 + weaveX + weaveY + noise;
                int r = Math.max(0, Math.min(255, base - 6));
                int g = Math.max(0, Math.min(255, base));
                int b = Math.max(0, Math.min(255, base + 8));
                int a = 190; // Semi-transparent overlay letting base pipe metal texture subtly show through
                img.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }
        ImageIO.write(img, "PNG", outFile);
        System.out.println("  [✓] Wrote pipe_insulation.png");
    }

    /**
     * 6. overlay_pressurized_in.png: 16x16 purple directional chevron arrows pointing toward center (input/suction).
     */
    private static void generateOverlayPressurizedIn(File outFile) throws IOException {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        // Rich vibrant royal purple (RGB: 147, 51, 234)
        int purple = (255 << 24) | (147 << 16) | (51 << 8) | 234;

        int[][] coords = {
            // Top chevron pointing down (toward center)
            { 6, 2 }, { 7, 2 }, { 8, 2 }, { 9, 2 },
            { 7, 3 }, { 8, 3 },
            // Left chevron pointing right (toward center)
            { 2, 6 }, { 2, 7 }, { 2, 8 }, { 2, 9 },
            { 3, 7 }, { 3, 8 },
            // Right chevron pointing left (toward center)
            { 13, 6 }, { 13, 7 }, { 13, 8 }, { 13, 9 },
            { 12, 7 }, { 12, 8 },
            // Bottom chevron pointing up (toward center)
            { 7, 12 }, { 8, 12 },
            { 6, 13 }, { 7, 13 }, { 8, 13 }, { 9, 13 }
        };

        for (int[] pt : coords) {
            img.setRGB(pt[0], pt[1], purple);
        }

        ImageIO.write(img, "PNG", outFile);
        System.out.println("  [✓] Wrote overlay_pressurized_in.png (purple inward chevrons)");
    }

    /**
     * 7. overlay_pressurized_out.png: 16x16 purple directional chevron arrows pointing away from center (output/discharge).
     */
    private static void generateOverlayPressurizedOut(File outFile) throws IOException {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        // Rich vibrant royal purple (RGB: 147, 51, 234)
        int purple = (255 << 24) | (147 << 16) | (51 << 8) | 234;

        int[][] coords = {
            // Top chevron pointing up (away from center)
            { 7, 2 }, { 8, 2 },
            { 6, 3 }, { 7, 3 }, { 8, 3 }, { 9, 3 },
            // Left chevron pointing left (away from center)
            { 3, 6 }, { 3, 9 },
            { 2, 7 }, { 2, 8 }, { 3, 7 }, { 3, 8 },
            // Right chevron pointing right (away from center)
            { 12, 6 }, { 12, 9 },
            { 12, 7 }, { 12, 8 }, { 13, 7 }, { 13, 8 },
            // Bottom chevron pointing down (away from center)
            { 6, 12 }, { 7, 12 }, { 8, 12 }, { 9, 12 },
            { 7, 13 }, { 8, 13 }
        };

        for (int[] pt : coords) {
            img.setRGB(pt[0], pt[1], purple);
        }

        ImageIO.write(img, "PNG", outFile);
        System.out.println("  [✓] Wrote overlay_pressurized_out.png (purple outward chevrons)");
    }
}

