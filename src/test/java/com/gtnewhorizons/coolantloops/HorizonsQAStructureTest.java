package com.gtnewhorizons.coolantloops;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Validates HorizonsQA multiblock structure definitions for CoolantLoops.
 */
public class HorizonsQAStructureTest {

    private JsonObject loadStructure(String path) {
        InputStream is = getClass().getResourceAsStream(path);
        assertNotNull(is, "HorizonsQA structure resource not found: " + path);
        try (InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            return new JsonParser().parse(reader)
                .getAsJsonObject();
        } catch (Exception e) {
            fail("Failed to parse JSON for " + path + ": " + e.getMessage());
            return null;
        }
    }

    private void validateStructure(JsonObject json, int expectedX, int expectedY, int expectedZ) {
        assertEquals(
            2,
            json.get("format_version")
                .getAsInt(),
            "format_version must be 2");
        JsonArray size = json.getAsJsonArray("size");
        assertNotNull(size);
        assertEquals(3, size.size());
        assertEquals(
            expectedX,
            size.get(0)
                .getAsInt(),
            "X size mismatch");
        assertEquals(
            expectedY,
            size.get(1)
                .getAsInt(),
            "Y size mismatch");
        assertEquals(
            expectedZ,
            size.get(2)
                .getAsInt(),
            "Z size mismatch");

        JsonObject palette = json.getAsJsonObject("palette");
        assertNotNull(palette, "palette object required");

        JsonArray layers = json.getAsJsonArray("layers");
        assertNotNull(layers, "layers array required");
        assertEquals(expectedY, layers.size(), "Layer count must match Y size");

        for (int y = 0; y < expectedY; y++) {
            JsonArray layer = layers.get(y)
                .getAsJsonArray();
            assertEquals(expectedZ, layer.size(), "Layer " + y + " row count must match Z size");
            for (int z = 0; z < expectedZ; z++) {
                String row = layer.get(z)
                    .getAsString();
                assertEquals(expectedX, row.length(), "Row length at y=" + y + ", z=" + z + " must match X size");
                for (int x = 0; x < expectedX; x++) {
                    char c = row.charAt(x);
                    if (c != '.' && c != ' ' && c != '-') {
                        assertTrue(
                            palette.has(String.valueOf(c)),
                            "Char '" + c + "' at (" + x + "," + y + "," + z + ") missing in palette");
                    }
                }
            }
        }
    }

    @Test
    public void testCoolantPumpStructure() {
        JsonObject json = loadStructure("/assets/coolantloops/horizonqastructures/multiblock/coolant_pump/valid.json");
        validateStructure(json, 3, 3, 3);

        JsonObject annotations = json.getAsJsonObject("annotations");
        assertNotNull(annotations);
        JsonObject labels = annotations.getAsJsonObject("labels");
        assertNotNull(labels);
        assertTrue(labels.has("controller"));
        assertTrue(labels.has("suction_hatch"));
        assertTrue(labels.has("discharge_hatch"));
    }

    @Test
    public void testPressurizedHeatExchangerStructure() {
        JsonObject json = loadStructure(
            "/assets/coolantloops/horizonqastructures/multiblock/pressurized_heat_exchanger/valid.json");
        validateStructure(json, 3, 4, 3);

        JsonObject annotations = json.getAsJsonObject("annotations");
        assertNotNull(annotations);
        JsonObject labels = annotations.getAsJsonObject("labels");
        assertNotNull(labels);
        assertTrue(labels.has("controller"));
        assertTrue(labels.has("primary_inlet"));
        assertTrue(labels.has("primary_outlet"));
        assertTrue(labels.has("secondary_input"));
        assertTrue(labels.has("secondary_output"));
    }
}
