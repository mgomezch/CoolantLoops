package com.gtnewhorizons.coolantloops.engine;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.coolantloops.common.tileentity.TileEntityLoopInstrument;

class LoopInstrumentTest {

    @Test
    void testThermometerRedstoneScaling() {
        TileEntityLoopInstrument thermometer = new TileEntityLoopInstrument();
        thermometer.setTrackedMetric(TileEntityLoopInstrument.TrackedMetric.TEMPERATURE);

        LoopSegment coldSeg = new LoopSegment("cold", 1.0, 0.1, 0.000045, 0.05, 50.0, 1000.0, 20.0);
        thermometer.processThermalExchange(0.01, 1.0, CoolantFluidProperty.WATER, coldSeg);
        assertEquals(0, thermometer.getRedstoneOutput(), "20 C should produce 0 Redstone");

        LoopSegment warmSeg = new LoopSegment("warm", 1.0, 0.1, 0.000045, 0.05, 50.0, 1000.0, 500.0);
        thermometer.processThermalExchange(0.01, 1.0, CoolantFluidProperty.WATER, warmSeg);
        assertEquals(7, thermometer.getRedstoneOutput(), "500 C should produce ~7 Redstone (mid-scale)");

        LoopSegment hotSeg = new LoopSegment("hot", 1.0, 0.1, 0.000045, 0.05, 50.0, 1000.0, 1000.0);
        thermometer.processThermalExchange(0.01, 1.0, CoolantFluidProperty.WATER, hotSeg);
        assertEquals(15, thermometer.getRedstoneOutput(), "1000 C should produce 15 Redstone (max-scale)");
    }

    @Test
    void testFlowMeterRedstoneScaling() {
        TileEntityLoopInstrument flowMeter = new TileEntityLoopInstrument();
        flowMeter.setTrackedMetric(TileEntityLoopInstrument.TrackedMetric.FLOW_RATE);

        // Pipe area = pi * 0.05^2 ~= 0.007854 m^2
        LoopSegment seg = new LoopSegment("pipe", 1.0, 0.1, 0.000045, 0.05, 50.0, 1000.0, 20.0);

        // 0 flow
        flowMeter.processThermalExchange(0.0, 1.0, CoolantFluidProperty.WATER, seg);
        assertEquals(0, flowMeter.getRedstoneOutput());

        // 5 m/s velocity -> Q = 5 * 0.007854 ~= 0.03927 m^3/s
        double v5Flow = 5.0 * seg.getArea();
        flowMeter.processThermalExchange(v5Flow, 1.0, CoolantFluidProperty.WATER, seg);
        assertEquals(7, flowMeter.getRedstoneOutput(), "5 m/s (50% max) should produce ~7 Redstone");

        // 10 m/s velocity -> Q = 10 * 0.007854 ~= 0.07854 m^3/s
        double v10Flow = 10.0 * seg.getArea();
        flowMeter.processThermalExchange(v10Flow, 1.0, CoolantFluidProperty.WATER, seg);
        assertEquals(15, flowMeter.getRedstoneOutput(), "10 m/s (100% max) should produce 15 Redstone");
    }
}
