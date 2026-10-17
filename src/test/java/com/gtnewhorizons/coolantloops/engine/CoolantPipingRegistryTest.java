package com.gtnewhorizons.coolantloops.engine;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class CoolantPipingRegistryTest {

    @Test
    void testPipeDiameterResolution() {
        assertEquals(0.05, CoolantPipingRegistry.getDiameterForThickness(0.25f), 1e-4);
        assertEquals(0.075, CoolantPipingRegistry.getDiameterForThickness(0.375f), 1e-4);
        assertEquals(0.10, CoolantPipingRegistry.getDiameterForThickness(0.50f), 1e-4);
        assertEquals(0.15, CoolantPipingRegistry.getDiameterForThickness(0.75f), 1e-4);
        assertEquals(0.20, CoolantPipingRegistry.getDiameterForThickness(0.875f), 1e-4);
    }

    @Test
    void testMaxPressureByMaterial() {
        assertTrue(
            CoolantPipingRegistry.getMaxPressureBar("CastIron") < CoolantPipingRegistry.getMaxPressureBar("Steel"));
        assertTrue(
            CoolantPipingRegistry.getMaxPressureBar("Steel")
                < CoolantPipingRegistry.getMaxPressureBar("StainlessSteel"));
        assertTrue(
            CoolantPipingRegistry.getMaxPressureBar("StainlessSteel")
                < CoolantPipingRegistry.getMaxPressureBar("Titanium"));
        assertTrue(
            CoolantPipingRegistry.getMaxPressureBar("Titanium")
                < CoolantPipingRegistry.getMaxPressureBar("TungstenSteel"));
        assertTrue(
            CoolantPipingRegistry.getMaxPressureBar("TungstenSteel")
                < CoolantPipingRegistry.getMaxPressureBar("Osmium"));
        assertTrue(
            CoolantPipingRegistry.getMaxPressureBar("Osmium") < CoolantPipingRegistry.getMaxPressureBar("Neutronium"));

        assertEquals(16.0, CoolantPipingRegistry.getMaxPressureBar("CastIron"), 0.1);
        assertEquals(35.0, CoolantPipingRegistry.getMaxPressureBar("Steel"), 0.1);
        assertEquals(70.0, CoolantPipingRegistry.getMaxPressureBar("StainlessSteel"), 0.1);
        assertEquals(140.0, CoolantPipingRegistry.getMaxPressureBar("Titanium"), 0.1);
        assertEquals(280.0, CoolantPipingRegistry.getMaxPressureBar("TungstenSteel"), 0.1);
        assertEquals(600.0, CoolantPipingRegistry.getMaxPressureBar("Osmium"), 0.1);
        assertEquals(3000.0, CoolantPipingRegistry.getMaxPressureBar("Neutronium"), 0.1);
    }
}
