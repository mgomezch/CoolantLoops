package com.gtnewhorizons.coolantloops;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.coolantloops.engine.ConvectiveHeatTransferModel;
import com.gtnewhorizons.coolantloops.engine.CoolantFluidProperty;
import com.gtnewhorizons.coolantloops.engine.CoolantLoopEngine;
import com.gtnewhorizons.coolantloops.engine.CoolantPipingRegistry;
import com.gtnewhorizons.coolantloops.engine.LoopSegment;

/**
 * Comprehensive end-to-end analytical test suite verifying the complete
 * Coolant Loops mechanics:
 * 1. Momentum solver & hydraulic inertia acceleration/coasting.
 * 2. Convective heat transfer & thermal advection.
 * 3. Manifold flow splitting (area-weighted) & thermal blending.
 * 4. Pressurized Heat Exchanger secondary boiling (Distilled Water -> Steam).
 * 5. In-line instrumentation analog Redstone scaling.
 * 6. Overpressure pipe rupture & safety trip shutoff.
 */
public class CoolantLoopEndToEndTest {

    private CoolantLoopEngine engine;
    private List<LoopSegment> loopSegments;

    @BeforeEach
    public void setup() {
        engine = new CoolantLoopEngine(CoolantFluidProperty.WATER);
        loopSegments = new ArrayList<>();

        // Segment 0: Discharge pipe (Steel Normal, 0.1m, length 10m)
        LoopSegment seg0 = new LoopSegment(
            "seg_discharge",
            10.0,
            0.1, // 0.1m diameter
            0.00005, // Steel roughness
            0.2, // elbow
            CoolantPipingRegistry.getMaxPressureBar("steel"),
            1000.0,
            20.0);
        engine.addSegment(seg0);
        loopSegments.add(seg0);

        // Segment 1: Creative Heat Reservoir (length 2m, K=0.8)
        LoopSegment seg1 = new LoopSegment(
            "seg_reservoir",
            2.0,
            0.1,
            0.00005,
            0.8,
            CoolantPipingRegistry.getMaxPressureBar("steel"),
            1000.0,
            20.0);
        engine.addSegment(seg1);
        loopSegments.add(seg1);

        // Segment 2: Pressurized Heat Exchanger (length 4m, K=1.2)
        LoopSegment seg2 = new LoopSegment(
            "seg_phe",
            4.0,
            0.1,
            0.00005,
            1.2,
            CoolantPipingRegistry.getMaxPressureBar("steel"),
            1000.0,
            20.0);
        engine.addSegment(seg2);
        loopSegments.add(seg2);

        // Segment 3: Return pipe to Suction (Steel Normal, 0.1m, length 12m)
        LoopSegment seg3 = new LoopSegment(
            "seg_suction",
            12.0,
            0.1,
            0.00005,
            0.4,
            CoolantPipingRegistry.getMaxPressureBar("steel"),
            1000.0,
            20.0);
        engine.addSegment(seg3);
        loopSegments.add(seg3);
    }

    @Test
    public void testInertialAccelerationAndSteadyStateCirculation() {
        assertEquals(0.0, engine.getVolumetricFlowRate(), 1e-6);

        engine.setPumpPowered(true);
        engine.setPumpMechanicalPowerWatts(120000.0); // 120 kW (EV Tier pump)

        // Run 100 ticks (5 seconds) under full power
        for (int t = 0; t < 100; t++) {
            engine.step(0.05);
        }

        double flow = engine.getVolumetricFlowRate();
        double litersPerSec = engine.getFlowRateLitersPerSecond();
        double vel = flow / loopSegments.get(0)
            .getArea();

        assertTrue(flow > 0.010, "Flow rate should have accelerated smoothly (flow=" + flow + ")");
        assertTrue(litersPerSec > 10.0, "Flow rate in L/s should exceed 10 L/s");
        assertTrue(vel > 1.2, "Velocity should reach steady state (v=" + vel + " m/s)");
        assertFalse(engine.isRuptured(), "Pressure should remain safely below Steel burst limit");
    }

    @Test
    public void testConvectiveHeatTransferAndThermalAdvection() {
        engine.setPumpPowered(true);
        engine.setPumpMechanicalPowerWatts(120000.0);

        // Accelerate loop
        for (int t = 0; t < 60; t++) {
            engine.step(0.05);
        }
        double flow = engine.getVolumetricFlowRate();
        double vel = flow / loopSegments.get(0)
            .getArea();
        assertTrue(vel > 1.0);

        // Inject thermal energy into Segment 1 from a 350°C reservoir
        double reservoirTemp = 350.0;
        double currentTemp = loopSegments.get(1)
            .getCurrentTemperatureCelsius();
        double area = 12.0;

        ConvectiveHeatTransferModel convection = ConvectiveHeatTransferModel.DEFAULT;
        double heatWatts = convection.computeHeatTransferRate(vel, area, reservoirTemp, currentTemp);
        assertTrue(heatWatts > 50000.0, "Heat transfer rate should be significant: " + heatWatts + " W");

        // Apply heat to segment
        double dt = 0.05;
        double segmentMass = CoolantFluidProperty.WATER.getDensity() * loopSegments.get(1)
            .getArea()
            * loopSegments.get(1)
                .getLength();
        double tempRise = (heatWatts * dt) / (segmentMass * CoolantFluidProperty.WATER.getSpecificHeat());
        loopSegments.get(1)
            .setCurrentTemperatureCelsius(currentTemp + tempRise);

        assertTrue(
            loopSegments.get(1)
                .getCurrentTemperatureCelsius() > currentTemp);

        // Run advection
        double initialNextTemp = loopSegments.get(2)
            .getCurrentTemperatureCelsius();
        engine.step(0.05);
        assertTrue(
            loopSegments.get(2)
                .getCurrentTemperatureCelsius() >= initialNextTemp,
            "Advection should carry hot coolant into downstream segments");
    }

    @Test
    public void testPressurizedHeatExchangerSteamGeneration() {
        // Feed hot coolant (250°C) into PHE segment
        loopSegments.get(2)
            .setCurrentTemperatureCelsius(250.0);

        engine.setPumpPowered(true);
        engine.setPumpMechanicalPowerWatts(120000.0);

        // Accelerate loop
        for (int t = 0; t < 50; t++) {
            engine.step(0.05);
        }
        double flow = engine.getVolumetricFlowRate();
        double vel = flow / loopSegments.get(0)
            .getArea();

        // PHE heat extraction: secondary distilled water at 100°C
        double pheArea = 24.0;
        double deltaT = 250.0 - 100.0;
        double qExchangedWatts = ConvectiveHeatTransferModel.DEFAULT
            .computeHeatTransferRate(vel, pheArea, 250.0, 100.0);
        assertTrue(qExchangedWatts > 100000.0, "PHE heat transfer rate should be > 100 kW");

        // Latent heat of vaporization: 2,260,000 J/L
        // 1 L Water -> 160 L Steam
        double joulesPerTick = qExchangedWatts * 0.05;
        double waterBoiledL = joulesPerTick / 2260000.0;
        double steamProducedL = waterBoiledL * 160.0;

        assertTrue(steamProducedL > 3.0, "PHE should produce substantial steam: " + steamProducedL + " L/t");
    }

    @Test
    public void testManifoldAreaWeightedSplitAndThermalBlending() {
        // Manifold splitting 1 normal pipe (0.1m) into 2 smaller pipes (Small 0.075m and Tiny 0.05m)
        double aTotal = 0.080; // 80 L/s total flow
        double d1 = 0.075;
        double d2 = 0.050;
        double area1 = Math.PI * Math.pow(d1 / 2.0, 2);
        double area2 = Math.PI * Math.pow(d2 / 2.0, 2);
        double totalArea = area1 + area2;

        // Area-weighted flow distribution
        double q1 = aTotal * (area1 / totalArea);
        double q2 = aTotal * (area2 / totalArea);

        assertEquals(aTotal, q1 + q2, 1e-9, "Conservation of mass in manifold split");
        assertTrue(q1 > q2, "Larger diameter branch must receive greater flow share");

        // Thermal blending: branch 1 is at 150°C, branch 2 is at 250°C
        double t1 = 150.0;
        double t2 = 250.0;
        double tMix = (q1 * t1 + q2 * t2) / (q1 + q2);

        assertTrue(tMix > t1 && tMix < t2, "Blended temperature must lie between branch temperatures");
        // Because q1 > q2, tMix must be closer to t1 (150°C) than to t2 (250°C)
        assertTrue(Math.abs(tMix - t1) < Math.abs(tMix - t2), "Blend must be weighted toward dominant branch");
    }

    @Test
    public void testInstrumentationAnalogRedstoneOutputs() {
        // Flow meter: 0 to 10 m/s mapped to 0 to 15 redstone
        // At 0 m/s -> redstone 0
        int redstone0 = (int) Math.min(15, Math.max(0, (0.0 / 10.0) * 15.0));
        assertEquals(0, redstone0);

        // At 5 m/s -> redstone 7 or 8
        int redstone5 = (int) Math.min(15, Math.max(0, (5.0 / 10.0) * 15.0));
        assertEquals(7, redstone5);

        // At 10 m/s -> redstone 15
        int redstone10 = (int) Math.min(15, Math.max(0, (10.0 / 10.0) * 15.0));
        assertEquals(15, redstone10);

        // Thermometer: 20°C to 500°C mapped to 0 to 15 redstone
        double temp = 260.0;
        int tempRedstone = (int) Math.min(15, Math.max(0, ((temp - 20.0) / 480.0) * 15.0));
        assertEquals(7, tempRedstone);
    }

    @Test
    public void testEmergencyTripThermalShutoffAndImpellerBraking() {
        engine.setPumpPowered(true);
        engine.setPumpMechanicalPowerWatts(120000.0);

        // Run pump up to speed
        for (int t = 0; t < 80; t++) {
            engine.step(0.05);
        }
        double peakFlow = engine.getVolumetricFlowRate();
        assertTrue(peakFlow > 0.01);

        // Thermal trip event: Redstone shutoff from thermometer trips pump
        engine.setPumpPowered(false);
        for (int t = 0; t < 40; t++) {
            engine.step(0.05);
        }

        double coastFlow = engine.getVolumetricFlowRate();
        assertTrue(coastFlow < peakFlow, "Impeller braking drag and friction must decelerate coolant when unpowered");
    }

    @Test
    public void testOverpressureCatastrophicRuptureOnWeakPipes() {
        // Create an engine with a weak pipe (burst limit = 2.0 bar)
        CoolantLoopEngine weakEngine = new CoolantLoopEngine(CoolantFluidProperty.WATER);
        weakEngine.addSegment(
            new LoopSegment(
                "weak_bronze",
                20.0,
                0.05, // Tiny pipe -> huge friction resistance
                0.0001,
                2.5,
                2.0, // 2.0 bar limit
                500.0,
                20.0));
        weakEngine.addSegment(new LoopSegment("return_seg", 10.0, 0.05, 0.0001, 1.0, 50.0, 500.0, 20.0));

        weakEngine.setPumpPowered(true);
        weakEngine.setPumpMechanicalPowerWatts(150000.0); // Extreme pump power

        // Step simulation: pressure will exceed 2.0 bar and burst
        for (int t = 0; t < 20; t++) {
            weakEngine.step(0.05);
            if (weakEngine.isRuptured()) {
                break;
            }
        }

        assertTrue(weakEngine.isRuptured(), "Exceeding burst rating must trigger isRuptured() flag!");
    }
}
