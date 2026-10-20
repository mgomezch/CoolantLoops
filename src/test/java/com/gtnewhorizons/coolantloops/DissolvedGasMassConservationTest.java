package com.gtnewhorizons.coolantloops;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.coolantloops.common.metatileentity.multi.MTECoolantPump;
import com.gtnewhorizons.coolantloops.engine.CoolantFluidProperty;
import com.gtnewhorizons.coolantloops.engine.CoolantLoopEngine;
import com.gtnewhorizons.coolantloops.engine.LoopSegment;

/**
 * Verifies that coolant loops strictly track absolute dissolved gas amounts (in liters),
 * conserving gas mass, and only convert to percentages on demand for display purposes
 * and cavitation wear calculations.
 */
public class DissolvedGasMassConservationTest {

    @Test
    public void testAbsoluteDissolvedGasTrackingAndMassConservation() {
        MTECoolantPump pump = new MTECoolantPump("test_conservation_pump");
        pump.setCurrentFillLiters(10000L);
        pump.setSimulatedCoolantLiters(10000L);

        // 1. Initial pristine state
        assertEquals(0L, pump.getTotalDissolvedGasLiters(), "Initial total dissolved gas must be 0 L");
        assertEquals(0.0, pump.getDissolvedGasFraction(), 1e-6);
        assertEquals(0.0, pump.getDissolvedGasPercentage(), 1e-6);
        assertEquals(1.0, 1.0 + pump.getDissolvedGasFraction(), 1e-6, "Cavitation factor at 0% gas must be 1.0x");

        // 2. Add absolute gas amounts (e.g. from reactor radiolysis)
        pump.addDissolvedGas("Tritium", 400L);
        pump.addDissolvedGas("Oxygen", 200L);

        // Absolute amounts must be exact integers
        assertEquals(400L, pump.getDissolvedGasAmount("Tritium"));
        assertEquals(200L, pump.getDissolvedGasAmount("Oxygen"));
        assertEquals(600L, pump.getTotalDissolvedGasLiters());

        Map<String, Long> gases = pump.getDissolvedGases();
        assertEquals(2, gases.size());
        assertEquals(400L, gases.get("Tritium"));
        assertEquals(200L, gases.get("Oxygen"));

        // Percentages are derived on demand: 600 L / 10000 L = 6.0%
        assertEquals(0.06, pump.getDissolvedGasFraction(), 1e-6);
        assertEquals(6.0, pump.getDissolvedGasPercentage(), 1e-6);

        // Cavitation factor is computed from the fraction: 1.0 + 0.06 = 1.06x
        double cavitationFactor = 1.0 + pump.getDissolvedGasFraction();
        assertEquals(1.06, cavitationFactor, 1e-6);

        // 3. Degassing extraction: extract 150 L Tritium and 50 L Oxygen
        long extractedTritium = pump.extractDissolvedGas("Tritium", 150L);
        long extractedOxygen = pump.extractDissolvedGas("Oxygen", 50L);
        assertEquals(150L, extractedTritium);
        assertEquals(50L, extractedOxygen);

        // Mass conservation check:
        // Initial gas (600 L) = Extracted gas (200 L) + Remaining gas (400 L)
        long remainingTritium = pump.getDissolvedGasAmount("Tritium");
        long remainingOxygen = pump.getDissolvedGasAmount("Oxygen");
        assertEquals(250L, remainingTritium);
        assertEquals(150L, remainingOxygen);
        assertEquals(400L, pump.getTotalDissolvedGasLiters());

        long totalConserved = extractedTritium + extractedOxygen + remainingTritium + remainingOxygen;
        assertEquals(600L, totalConserved, "Dissolved gas mass must be strictly conserved!");

        // New derived percentage: 400 L / 10000 L = 4.0%
        assertEquals(0.04, pump.getDissolvedGasFraction(), 1e-6);
        assertEquals(4.0, pump.getDissolvedGasPercentage(), 1e-6);
        assertEquals(1.04, 1.0 + pump.getDissolvedGasFraction(), 1e-6);
    }

    @Test
    public void testEngineAndSegmentAbsoluteGasSynchronization() {
        CoolantLoopEngine engine = new CoolantLoopEngine(CoolantFluidProperty.HEAVY_WATER);

        // Segment 1: 6,000 L capacity (60% of loop)
        LoopSegment seg1 = new LoopSegment("seg_1", 10.0, 0.874, 0.000045, 0.5, 100.0, 1000.0, 20.0);
        seg1.setCapacityLiters(6000L);
        seg1.setCustomVolumeM3(6.0);

        // Segment 2: 4,000 L capacity (40% of loop)
        LoopSegment seg2 = new LoopSegment("seg_2", 10.0, 0.713, 0.000045, 0.5, 100.0, 1000.0, 20.0);
        seg2.setCapacityLiters(4000L);
        seg2.setCustomVolumeM3(4.0);

        engine.addSegment(seg1);
        engine.addSegment(seg2);
        assertEquals(10000L, engine.getTotalLoopVolumeLiters());

        // Add absolute dissolved gas directly to engine
        engine.addDissolvedGas("Deuterium", 1000L);
        assertEquals(1000L, engine.getDissolvedGasAmount("Deuterium"));
        assertEquals(1000L, engine.getTotalDissolvedGasLiters());

        // Engine-level fraction and percentage
        assertEquals(0.10, engine.getDissolvedGasFraction(), 1e-6);
        assertEquals(10.0, engine.getDissolvedGasPercentage(), 1e-6);

        // Synchronize segments with loop-level gas inventory
        seg1.setDissolvedGases(engine.getDissolvedGases(), engine.getTotalLoopVolumeLiters());
        seg2.setDissolvedGases(engine.getDissolvedGases(), engine.getTotalLoopVolumeLiters());

        // Segment 1 holds 60% of gas = 600 L
        assertEquals(600L, seg1.getDissolvedGasAmount("Deuterium"));
        assertEquals(600L, seg1.getTotalDissolvedGasLiters());
        assertEquals(0.10, seg1.getDissolvedGasFraction(), 1e-6);
        assertEquals(10.0, seg1.getDissolvedGasPercentage(), 1e-6);

        // Segment 2 holds 40% of gas = 400 L
        assertEquals(400L, seg2.getDissolvedGasAmount("Deuterium"));
        assertEquals(400L, seg2.getTotalDissolvedGasLiters());
        assertEquals(0.10, seg2.getDissolvedGasFraction(), 1e-6);
        assertEquals(10.0, seg2.getDissolvedGasPercentage(), 1e-6);

        // Segment gas sum must equal engine total gas
        assertEquals(
            engine.getTotalDissolvedGasLiters(),
            seg1.getTotalDissolvedGasLiters() + seg2.getTotalDissolvedGasLiters());

        // Extraction from engine
        long extracted = engine.extractDissolvedGas("Deuterium", 300L);
        assertEquals(300L, extracted);
        assertEquals(700L, engine.getDissolvedGasAmount("Deuterium"));
        assertEquals(700L, engine.getTotalDissolvedGasLiters());
        assertEquals(0.07, engine.getDissolvedGasFraction(), 1e-6);
        assertEquals(7.0, engine.getDissolvedGasPercentage(), 1e-6);
    }

    @Test
    public void testCaseInsensitiveGasLookup() {
        MTECoolantPump pump = new MTECoolantPump("test_case_pump");
        pump.setCurrentFillLiters(10000L);
        pump.setSimulatedCoolantLiters(10000L);

        pump.addDissolvedGas("Tritium", 500L);

        assertEquals(500L, pump.getDissolvedGasAmount("Tritium"));
        assertEquals(500L, pump.getDissolvedGasAmount("tritium"));
        assertEquals(500L, pump.getDissolvedGasAmount("TRITIUM"));

        long extracted = pump.extractDissolvedGas("tritium", 200L);
        assertEquals(200L, extracted);
        assertEquals(300L, pump.getDissolvedGasAmount("TRITIUM"));
        assertEquals(300L, pump.getDissolvedGasAmount("Tritium"));
    }
}
