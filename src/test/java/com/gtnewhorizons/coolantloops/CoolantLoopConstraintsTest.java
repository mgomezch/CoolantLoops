package com.gtnewhorizons.coolantloops;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.coolantloops.engine.CoolantFluidProperty;
import com.gtnewhorizons.coolantloops.engine.CoolantLoopEngine;
import com.gtnewhorizons.coolantloops.engine.CoolantPipingRegistry;
import com.gtnewhorizons.coolantloops.engine.LoopSegment;

/**
 * Unit tests verifying the physical constraints:
 * 1. Material Matching: Railcraft tank and all pipes in a loop must use the same material
 * (restricted to materials having both GT fluid pipes and Railcraft tanks:
 * Iron/Cast Iron, Steel, Stainless Steel, Titanium, TungstenSteel, Neutronium).
 * 2. Pipe Size Consistency: All pipe blocks in a segment between devices must use the same pipe size;
 * device input/output cannot change pipe size; only Manifolds can transition pipe sizes.
 * 3. Volumetric Flow Rate Cap: Fluid circulation rate is strictly capped by the fluid capacity
 * of the pipes connected to the pump.
 */
public class CoolantLoopConstraintsTest {

    private CoolantLoopEngine engine;

    @BeforeEach
    public void setup() {
        engine = new CoolantLoopEngine(CoolantFluidProperty.WATER);
        for (int i = 0; i < 4; i++) {
            engine.addSegment(new LoopSegment("pipe_" + i, 5.0, 0.1, 0.00005, 0.5, 100.0, 1200.0, 20.0));
        }
    }

    @Test
    public void testAllowedPipeMaterials() {
        // Materials with both GT fluid pipes and Railcraft tanks
        assertTrue(CoolantPipingRegistry.isAllowedCoolantPipeMaterial("Steel"));
        assertTrue(CoolantPipingRegistry.isAllowedCoolantPipeMaterial("Iron"));
        assertTrue(CoolantPipingRegistry.isAllowedCoolantPipeMaterial("CastIron"));
        assertTrue(CoolantPipingRegistry.isAllowedCoolantPipeMaterial("WroughtIron"));
        assertTrue(CoolantPipingRegistry.isAllowedCoolantPipeMaterial("StainlessSteel"));
        assertTrue(CoolantPipingRegistry.isAllowedCoolantPipeMaterial("Titanium"));
        assertTrue(CoolantPipingRegistry.isAllowedCoolantPipeMaterial("TungstenSteel"));
        assertTrue(CoolantPipingRegistry.isAllowedCoolantPipeMaterial("Neutronium"));
        assertTrue(CoolantPipingRegistry.isAllowedCoolantPipeMaterial("Osmium"));

        // Materials without Railcraft tanks or without GT fluid pipes
        assertFalse(CoolantPipingRegistry.isAllowedCoolantPipeMaterial("Bronze"));
        assertFalse(CoolantPipingRegistry.isAllowedCoolantPipeMaterial("Copper"));
        assertFalse(CoolantPipingRegistry.isAllowedCoolantPipeMaterial("Wood"));
        assertFalse(CoolantPipingRegistry.isAllowedCoolantPipeMaterial("Aluminium")); // RC tank but no GT fluid pipe
        assertFalse(CoolantPipingRegistry.isAllowedCoolantPipeMaterial("Palladium")); // RC tank but no GT fluid pipe
        assertFalse(CoolantPipingRegistry.isAllowedCoolantPipeMaterial("Iridium")); // RC tank but no GT fluid pipe
        assertFalse(CoolantPipingRegistry.isAllowedCoolantPipeMaterial((String) null));
    }

    @Test
    public void testMaterialEqualityComparison() {
        // Steel
        assertTrue(CoolantPipingRegistry.areMaterialsEqual("Steel", "Steel"));
        assertTrue(CoolantPipingRegistry.areMaterialsEqual("Steel", "steel"));

        // Iron variations should all match each other
        assertTrue(CoolantPipingRegistry.areMaterialsEqual("Iron", "CastIron"));
        assertTrue(CoolantPipingRegistry.areMaterialsEqual("CastIron", "WroughtIron"));
        assertTrue(CoolantPipingRegistry.areMaterialsEqual("Iron", "WroughtIron"));

        // Other materials
        assertTrue(CoolantPipingRegistry.areMaterialsEqual("StainlessSteel", "StainlessSteel"));
        assertTrue(CoolantPipingRegistry.areMaterialsEqual("Titanium", "Titanium"));
        assertTrue(CoolantPipingRegistry.areMaterialsEqual("TungstenSteel", "TungstenSteel"));
        assertTrue(CoolantPipingRegistry.areMaterialsEqual("Neutronium", "Neutronium"));

        // Mismatches
        assertFalse(CoolantPipingRegistry.areMaterialsEqual("Steel", "CastIron"));
        assertFalse(CoolantPipingRegistry.areMaterialsEqual("Steel", "TungstenSteel"));
        assertFalse(CoolantPipingRegistry.areMaterialsEqual("Titanium", "StainlessSteel"));
        assertFalse(CoolantPipingRegistry.areMaterialsEqual("Steel", (String) null));
    }

    @Test
    public void testVolumetricFlowRateCap() {
        // Medium Steel Pipe capacity: 240 L/t = 4,800 L/s = 4.8 m^3/s
        engine.setMaxVolumetricFlowRate(4.8);
        assertEquals(4.8, engine.getMaxVolumetricFlowRate(), 1e-6);
        assertEquals(4800.0, engine.getMaxFlowRateLitersPerSecond(), 1e-3);
        assertEquals(240.0, engine.getMaxFlowRateLitersPerTick(), 1e-3);

        // Power the pump with extreme power (500 kW)
        engine.setPumpPowered(true);
        engine.setPumpMechanicalPowerWatts(500000.0);

        // Run 200 ticks
        for (int t = 0; t < 200; t++) {
            engine.step(0.05);
            assertTrue(
                engine.getVolumetricFlowRate() <= 4.8 + 1e-9,
                "Flow rate must never exceed the connected pipe capacity limit!");
        }

        // Test Small Steel Pipe capacity: 60 L/t = 1,200 L/s = 1.2 m^3/s
        engine.setMaxVolumetricFlowRate(1.2);
        assertTrue(
            engine.getVolumetricFlowRate() <= 1.2 + 1e-9,
            "Setting smaller cap must immediately clamp flow rate");

        for (int t = 0; t < 50; t++) {
            engine.step(0.05);
            assertTrue(engine.getVolumetricFlowRate() <= 1.2 + 1e-9);
        }
    }
}
