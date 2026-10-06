package com.gtnewhorizons.coolantloops.engine;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.coolantloops.common.tileentity.TileEntityCreativeHeatReservoir;

class CreativeHeatReservoirTest {

    @Test
    void testHeatTransferToCoolant() {
        TileEntityCreativeHeatReservoir reservoir = new TileEntityCreativeHeatReservoir();
        reservoir.setTargetTemperatureCelsius(500.0);
        reservoir.setHeatTransferArea(4.0);

        LoopSegment seg = new LoopSegment("seg_0", 1.0, 0.1, 0.000045, 0.5, 100.0, 1000.0, 20.0 // Initial cold coolant
                                                                                                // (20 C)
        );

        // Process heat transfer at 0.02 m^3/s for 1 second
        reservoir.processThermalExchange(0.02, 1.0, CoolantFluidProperty.WATER, seg);

        assertTrue(
            seg.getCurrentTemperatureCelsius() > 20.0,
            "Coolant temperature must rise when passing through a 500 C reservoir");
        assertTrue(
            seg.getCurrentTemperatureCelsius() < 500.0,
            "Coolant temperature cannot exceed device temperature in a single pass");
    }

    @Test
    void testCoolingHotCoolant() {
        TileEntityCreativeHeatReservoir coldSink = new TileEntityCreativeHeatReservoir();
        coldSink.setTargetTemperatureCelsius(15.0); // Cold sink at 15 C

        LoopSegment hotSeg = new LoopSegment("hot_seg", 1.0, 0.1, 0.000045, 0.5, 100.0, 1000.0, 90.0 // Hot coolant (90
                                                                                                     // C)
        );

        coldSink.processThermalExchange(0.02, 1.0, CoolantFluidProperty.WATER, hotSeg);

        assertTrue(
            hotSeg.getCurrentTemperatureCelsius() < 90.0,
            "Coolant temperature must drop when passing through a 15 C heat sink");
        assertTrue(hotSeg.getCurrentTemperatureCelsius() > 15.0);
    }
}
