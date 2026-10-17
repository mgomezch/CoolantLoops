package com.gtnewhorizons.coolantloops.engine;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CoolantLoopEngineTest {

    private CoolantLoopEngine engine;

    @BeforeEach
    void setUp() {
        engine = new CoolantLoopEngine(CoolantFluidProperty.WATER);
        // Create a 4-segment square loop (each side 5 meters long, 0.1m diameter pipe)
        for (int i = 0; i < 4; i++) {
            engine.addSegment(
                new LoopSegment(
                    "pipe_" + i,
                    5.0, // 5m length
                    0.1, // 0.1m diameter
                    0.00005, // 0.05mm roughness (commercial steel)
                    0.5, // minor loss for corner elbow
                    100.0, // 100 bar burst limit
                    1200.0, // 1200 C max temperature
                    20.0 // 20 C initial temp
                ));
        }
    }

    @Test
    void testInitialStateAtRest() {
        assertEquals(0.0, engine.getVolumetricFlowRate(), 1e-9);
        assertFalse(engine.isRuptured());
        assertEquals(
            4,
            engine.getSegments()
                .size());
        assertTrue(engine.getTotalFluidMassKg() > 0.0);
    }

    @Test
    void testInertialAccelerationUnderPumpPower() {
        engine.setPumpPowered(true);
        engine.setPumpMechanicalPowerWatts(5000.0); // 5 kW mechanical input

        double initialQ = engine.getVolumetricFlowRate();
        double prevQ = initialQ;
        // Step for 100 ticks (5.0 seconds at 0.05s/tick)
        for (int tick = 0; tick < 100; tick++) {
            engine.step(0.05);
            assertTrue(
                engine.getVolumetricFlowRate() >= prevQ - 1e-6,
                "Flow rate must accelerate or maintain steady-state");
            prevQ = engine.getVolumetricFlowRate();
        }

        assertTrue(
            engine.getVolumetricFlowRate() > initialQ + 0.005,
            "Flow rate should have accelerated significantly");
        assertTrue(engine.getFlowRateLitersPerSecond() > 5.0);
    }

    @Test
    void testInertialDecelerationWhenPumpTurnedOff() {
        // First ramp up
        engine.setPumpPowered(true);
        engine.setPumpMechanicalPowerWatts(5000.0);
        for (int tick = 0; tick < 50; tick++) {
            engine.step(0.05);
        }
        double peakQ = engine.getVolumetricFlowRate();
        assertTrue(peakQ > 0.0);

        // Turn off pump
        engine.setPumpPowered(false);
        engine.step(0.05);
        double coastQ = engine.getVolumetricFlowRate();

        // Must still be moving due to fluid inertia (not instant stop!)
        assertTrue(coastQ > 0.0, "Fluid must possess inertia and coast after pump cutoff");
        assertTrue(coastQ < peakQ, "Viscous drag must begin decelerating the fluid");

        // Coast for 200 ticks
        for (int tick = 0; tick < 200; tick++) {
            engine.step(0.05);
        }
        assertTrue(engine.getVolumetricFlowRate() < coastQ, "Fluid must continue decelerating");
    }

    @Test
    void testThermalAdvectionAroundLoop() {
        // Set segment 0 to 100 C, others at 20 C
        engine.getSegments()
            .get(0)
            .setCurrentTemperatureCelsius(100.0);

        engine.setPumpPowered(true);
        engine.setPumpMechanicalPowerWatts(10000.0);

        // Step simulation
        for (int tick = 0; tick < 100; tick++) {
            engine.step(0.05);
        }

        // Fluid should have circulated heat to downstream segments
        assertTrue(
            engine.getSegments()
                .get(1)
                .getCurrentTemperatureCelsius() > 20.0,
            "Heat must advect to segment 1");
        assertTrue(
            engine.getSegments()
                .get(2)
                .getCurrentTemperatureCelsius() > 20.0,
            "Heat must advect to segment 2");
    }

    @Test
    void testPipeBurstWhenPressureExceedsMaxRating() {
        CoolantLoopEngine weakEngine = new CoolantLoopEngine(CoolantFluidProperty.WATER);
        // Add a segment with an extremely low burst limit (1.5 bar)
        weakEngine.addSegment(
            new LoopSegment(
                "weak_pipe",
                10.0,
                0.05,
                0.0001,
                1.0,
                1.5, // 1.5 bar max pressure
                500.0,
                20.0));
        weakEngine.addSegment(new LoopSegment("return_pipe", 10.0, 0.05, 0.0001, 1.0, 50.0, 500.0, 20.0));

        weakEngine.setPumpPowered(true);
        weakEngine.setPumpMechanicalPowerWatts(50000.0); // High power pushing pressure above 1.5 bar

        weakEngine.step(0.05);

        assertTrue(weakEngine.isRuptured(), "Weak pipe segment must burst under excessive pressure");
        assertNotNull(weakEngine.getFailureReason());
        assertTrue(
            weakEngine.getFailureReason()
                .contains("burst"));
    }

    @Test
    void testPipeThermalFailureWhenExceedingMaxTemp() {
        CoolantLoopEngine hotEngine = new CoolantLoopEngine(CoolantFluidProperty.WATER);
        hotEngine.addSegment(
            new LoopSegment(
                "lead_pipe",
                5.0,
                0.1,
                0.0001,
                0.5,
                100.0,
                150.0, // Low max temp (150 C)
                200.0 // Initial temp exceeds max rating!
            ));

        hotEngine.setPumpPowered(true);
        hotEngine.setPumpMechanicalPowerWatts(100.0);
        hotEngine.step(0.05);

        assertTrue(hotEngine.isRuptured(), "Pipe must fail thermally when exceeding max temperature");
        assertNotNull(hotEngine.getFailureReason());
        assertTrue(
            hotEngine.getFailureReason()
                .contains("melted"));
    }

    @Test
    void testParallelBranchFlowWeightedThermalBlending() {
        CoolantLoopEngine eng = new CoolantLoopEngine(CoolantFluidProperty.WATER);

        // Segment 0: Trunk discharge
        LoopSegment seg0 = new LoopSegment("trunk_discharge", 1.0, 0.1, 0.00005, 0.5, 100.0, 1200.0, 20.0);
        seg0.setFlowFraction(1.0);

        // Segment 1: Branch A (flow fraction 1/3, temp 300 C)
        LoopSegment segA = new LoopSegment("branch_A", 1.0, 0.1, 0.00005, 0.5, 100.0, 1200.0, 300.0);
        segA.setFlowFraction(1.0 / 3.0);

        // Segment 2: Branch B (flow fraction 2/3, temp 600 C)
        LoopSegment segB = new LoopSegment("branch_B", 1.0, 0.1, 0.00005, 0.5, 100.0, 1200.0, 600.0);
        segB.setFlowFraction(2.0 / 3.0);

        // Segment 3: Merger (short segment to be fully swept in one step)
        LoopSegment segMerger = new LoopSegment("merger", 0.01, 0.1, 0.00005, 0.5, 100.0, 1200.0, 20.0);
        segMerger.setFlowFraction(1.0);

        // Wire DAG upstreams:
        segA.addUpstreamSegment(seg0);
        segB.addUpstreamSegment(seg0);
        segMerger.addUpstreamSegment(segA);
        segMerger.addUpstreamSegment(segB);
        seg0.addUpstreamSegment(segMerger);

        eng.addSegment(seg0);
        eng.addSegment(segA);
        eng.addSegment(segB);
        eng.addSegment(segMerger);

        eng.setPumpPowered(true);
        eng.setPumpMechanicalPowerWatts(50000.0);

        // Keep branches at constant hot temperatures (representing heat sources like reactor passages)
        // so steady-state blended stream enters the merger
        for (int i = 0; i < 50; i++) {
            segA.setCurrentTemperatureCelsius(300.0);
            segB.setCurrentTemperatureCelsius(600.0);
            eng.step(0.05);
        }

        // Expected blended incoming temperature:
        // (1/3 * 300 + 2/3 * 600) / (1/3 + 2/3) = (100 + 400) / 1.0 = 500 C
        double mergerTemp = segMerger.getCurrentTemperatureCelsius();
        assertEquals(500.0, mergerTemp, 0.5, "Merger must receive flow-weighted blended temperature of 500 C");
    }

    @Test
    void testParallelBranchFrictionDropNotAdditiveInSeries() {
        CoolantLoopEngine seriesEng = new CoolantLoopEngine(CoolantFluidProperty.WATER);
        LoopSegment s1 = new LoopSegment("pipe_1", 10.0, 0.1, 0.00005, 0.0, 100.0, 1200.0, 20.0);
        LoopSegment s2 = new LoopSegment("pipe_2", 10.0, 0.1, 0.00005, 0.0, 100.0, 1200.0, 20.0);
        seriesEng.addSegment(s1);
        seriesEng.addSegment(s2);

        CoolantLoopEngine parallelEng = new CoolantLoopEngine(CoolantFluidProperty.WATER);
        LoopSegment p1 = new LoopSegment("branch_1", 10.0, 0.1, 0.00005, 0.0, 100.0, 1200.0, 20.0);
        LoopSegment p2 = new LoopSegment("branch_2", 10.0, 0.1, 0.00005, 0.0, 100.0, 1200.0, 20.0);
        p1.setFlowFraction(0.5);
        p2.setFlowFraction(0.5);
        parallelEng.addSegment(p1);
        parallelEng.addSegment(p2);

        double testQ = 0.05; // 50 L/s
        double seriesDrop = s1.computePressureDrop(testQ, CoolantFluidProperty.WATER)
            + s2.computePressureDrop(testQ, CoolantFluidProperty.WATER);

        double p1Drop = p1.computePressureDrop(testQ * p1.getFlowFraction(), CoolantFluidProperty.WATER) * p1.getFlowFraction();
        double p2Drop = p2.computePressureDrop(testQ * p2.getFlowFraction(), CoolantFluidProperty.WATER) * p2.getFlowFraction();
        double parallelDrop = p1Drop + p2Drop;

        // Parallel pressure drop with 2 branches must be significantly lower than series addition
        assertTrue(parallelDrop < seriesDrop * 0.35, "Parallel branches must not add pressure drops in series");
    }
}
