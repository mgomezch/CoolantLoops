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
        assertNotNull(CoolantFluidProperty.get("molten.cheese"));
        assertNotNull(CoolantFluidProperty.get("cheese"));
        assertNotNull(CoolantFluidProperty.get("moltencheese"));
    }

    @Test
    void testWaterProperties() {
        CoolantFluidProperty water = CoolantFluidProperty.WATER;
        assertEquals(1000.0, water.getDensity(), 0.01);
        assertEquals(0.001, water.getDynamicViscosity(), 1e-5);
        assertEquals(4184.0, water.getSpecificHeat(), 0.1);
        assertEquals(4184000.0, water.getVolumetricHeatCapacity(), 10.0);
        assertEquals(100.0, water.getBoilingPointCelsius(), 0.1);
        assertTrue(water.canBoil());
    }

    @Test
    void testHeavyWaterProperties() {
        CoolantFluidProperty heavyWater = CoolantFluidProperty.HEAVY_WATER;
        assertTrue(heavyWater.getDensity() > CoolantFluidProperty.WATER.getDensity());
        assertEquals(101.4, heavyWater.getBoilingPointCelsius(), 0.1);
        assertTrue(heavyWater.canBoil());
    }

    @Test
    void testMoltenCheeseProperties() {
        CoolantFluidProperty cheese = CoolantFluidProperty.MOLTEN_CHEESE;
        assertEquals(1120.0, cheese.getDensity(), 0.01);
        assertEquals(0.557, cheese.getDynamicViscosity(), 1e-5);
        assertEquals(3000.0, cheese.getSpecificHeat(), 0.1);
        assertEquals(0.481, cheese.getThermalConductivity(), 1e-5);
        assertEquals(46.85, cheese.getFreezingPointCelsius(), 0.01);
        assertTrue(Double.isInfinite(cheese.getBoilingPointCelsius()));
        assertFalse(cheese.canBoil());
        assertSame(cheese, CoolantFluidProperty.get("molten.cheese"));
        assertSame(cheese, CoolantFluidProperty.get("cheese"));
        assertSame(cheese, CoolantFluidProperty.get("moltencheese"));
    }

    @Test
    void testNonBoilingFluids() {
        assertFalse(CoolantFluidProperty.IC2_COOLANT.canBoil());
        assertTrue(Double.isInfinite(CoolantFluidProperty.IC2_COOLANT.getBoilingPointCelsius()));
        assertFalse(CoolantFluidProperty.SODIUM.canBoil());
        assertTrue(Double.isInfinite(CoolantFluidProperty.SODIUM.getBoilingPointCelsius()));
    }

    @Test
    void testUnknownFluidReturnsNull() {
        assertNull(CoolantFluidProperty.get("non_existent_fluid_xyz"));
        assertNull(CoolantFluidProperty.get(null));
    }

    @Test
    void testIsPlainWater() {
        assertTrue(CoolantFluidProperty.WATER.isPlainWater());
        assertFalse(CoolantFluidProperty.DISTILLED_WATER.isPlainWater());
        assertFalse(CoolantFluidProperty.HEAVY_WATER.isPlainWater());
        assertFalse(CoolantFluidProperty.MOLTEN_CHEESE.isPlainWater());
        assertFalse(CoolantFluidProperty.IC2_COOLANT.isPlainWater());
        assertFalse(CoolantFluidProperty.SODIUM.isPlainWater());
        assertFalse(CoolantFluidProperty.LAVA.isPlainWater());
    }

    @Test
    void testLavaProperties() {
        CoolantFluidProperty lava = CoolantFluidProperty.LAVA;
        assertEquals(3000.0, lava.getDensity(), 0.01);
        assertEquals(0.001, lava.getDynamicViscosity(), 1e-5);
        assertEquals(1250.0, lava.getSpecificHeat(), 0.1);
        assertEquals(1.5, lava.getThermalConductivity(), 1e-5);
        assertEquals(1000.0, lava.getFreezingPointCelsius(), 0.01);
        assertTrue(Double.isInfinite(lava.getBoilingPointCelsius()));
        assertFalse(lava.canBoil());
        assertTrue(lava.isLava());
        assertTrue(lava.isMolten());
        assertFalse(lava.isPlainWater());
        assertSame(lava, CoolantFluidProperty.get("lava"));
        assertSame(lava, CoolantFluidProperty.get("fluid.lava"));
    }
}
