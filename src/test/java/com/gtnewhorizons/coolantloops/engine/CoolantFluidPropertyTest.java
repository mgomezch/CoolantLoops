package com.gtnewhorizons.coolantloops.engine;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class CoolantFluidPropertyTest {

    @Test
    void testStandardFluidsRegistered() {
        assertNotNull(CoolantFluidProperty.get("water"));
        assertNotNull(CoolantFluidProperty.get("ic2distilledwater"));
        assertNotNull(CoolantFluidProperty.get("heavywater"));
        assertNotNull(CoolantFluidProperty.get("ic2coolant"));
        assertNotNull(CoolantFluidProperty.get("sodium"));
    }

    @Test
    void testWaterProperties() {
        CoolantFluidProperty water = CoolantFluidProperty.WATER;
        assertEquals(1000.0, water.getDensity(), 0.01);
        assertEquals(0.001, water.getDynamicViscosity(), 1e-5);
        assertEquals(4184.0, water.getSpecificHeat(), 0.1);
        assertEquals(4184000.0, water.getVolumetricHeatCapacity(), 10.0);
        assertEquals(100.0, water.getBoilingPointCelsius(), 0.1);
    }

    @Test
    void testHeavyWaterProperties() {
        CoolantFluidProperty heavyWater = CoolantFluidProperty.HEAVY_WATER;
        assertTrue(heavyWater.getDensity() > CoolantFluidProperty.WATER.getDensity());
        assertEquals(101.4, heavyWater.getBoilingPointCelsius(), 0.1);
    }

    @Test
    void testUnknownFluidDefaultsToWater() {
        CoolantFluidProperty unknown = CoolantFluidProperty.get("non_existent_fluid_xyz");
        assertNotNull(unknown);
        assertEquals("water", unknown.getFluidName());
    }
}
