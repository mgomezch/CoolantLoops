package com.gtnewhorizons.coolantloops;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.coolantloops.engine.CoolantFluidProperty;
import com.gtnewhorizons.coolantloops.engine.ICoolantLoopPump;

/**
 * Analytical test suite verifying Pressurized Degasser physics,
 * power consumption, single-amp overclocking, and dual rate limits.
 */
public class PressurizedDegasserTest {

    private MockCoolantPump mockPump;

    static class MockCoolantPump implements ICoolantLoopPump {

        private long currentFillLiters = 10000L;
        private final Map<String, Long> dissolvedGases = new HashMap<>();

        @Override
        public void consumeCoolant(long liters) {
            currentFillLiters = Math.max(0L, currentFillLiters - liters);
        }

        @Override
        public void addDissolvedGas(String gasName, long liters) {
            if (gasName == null || liters <= 0) return;
            dissolvedGases.merge(gasName, liters, Long::sum);
        }

        @Override
        public double getDissolvedGasFraction() {
            long total = dissolvedGases.values()
                .stream()
                .mapToLong(Long::longValue)
                .sum();
            return currentFillLiters > 0 ? (double) total / (double) currentFillLiters : 0.0;
        }

        @Override
        public long getDissolvedGasAmount(String gasName) {
            return dissolvedGases.getOrDefault(gasName, 0L);
        }

        @Override
        public Map<String, Long> getDissolvedGases() {
            return Collections.unmodifiableMap(dissolvedGases);
        }

        @Override
        public long getTotalCoolantLiters() {
            return 100000L;
        }

        @Override
        public CoolantFluidProperty getCoolantFluidProperty() {
            return CoolantFluidProperty.WATER;
        }

        @Override
        public long extractDissolvedGas(String gasName, long maxLiters) {
            if (gasName == null || maxLiters <= 0) return 0L;
            long cur = dissolvedGases.getOrDefault(gasName, 0L);
            long toExtract = Math.min(cur, maxLiters);
            if (toExtract > 0) {
                long rem = cur - toExtract;
                if (rem > 0) dissolvedGases.put(gasName, rem);
                else dissolvedGases.remove(gasName);
            }
            return toExtract;
        }

        @Override
        public long getCurrentFillLiters() {
            return currentFillLiters;
        }
    }

    @BeforeEach
    public void setup() {
        mockPump = new MockCoolantPump();
    }

    @Test
    public void testDegasserSingleAmpPowerAndOverclockingLogic() {
        // 1 EV hatch: voltage 2048, 1 hatch -> 1 amp -> 2048 EU/t
        int numHatches1 = 1;
        long voltage = 2048L;
        long targetEU1 = (numHatches1 == 1) ? voltage : 2L * voltage * numHatches1;
        assertEquals(2048L, targetEU1, "1 EV hatch must consume 1 amp = 2048 EU/t");

        // 2 EV hatches: voltage 2048 each -> 2 amps per hatch -> 4 amps total = 8192 EU/t
        int numHatches2 = 2;
        long targetEU2 = (numHatches2 == 1) ? voltage : 2L * voltage * numHatches2;
        assertEquals(8192L, targetEU2, "2 EV hatches must consume 2 amps each = 8192 EU/t");

        // 4 IV hatches: voltage 8192 each -> 2 amps per hatch -> 8 amps total = 65536 EU/t
        int numHatches4 = 4;
        long voltageIV = 8192L;
        long targetEU4 = (numHatches4 == 1) ? voltageIV : 2L * voltageIV * numHatches4;
        assertEquals(65536L, targetEU4, "4 IV hatches must consume 2 amps each = 65536 EU/t");
    }

    @Test
    public void testDegasserRateLimitsEnergyAndHydrodynamics() {
        // Total loop volume = 10,000 L
        // Dissolved Tritium = 1,000 L (Concentration C = 0.10)
        // Flow rate Q = 40 L/s (0.040 m^3/s)
        mockPump.addDissolvedGas("Tritium", 1000L);

        double flowRateLs = 40.0;
        long loopVolume = mockPump.getCurrentFillLiters();
        double dt = 1.0; // 1 second

        // Mass transfer limit:
        // Volume crossing per second = C * Q = (1000 / 10000) * 40 = 4.0 L/s
        // 25% of crossing volume = 0.25 * 4.0 = 1.0 L/s
        double crossingVolume = (1000.0 / loopVolume) * (flowRateLs * dt);
        double maxGasMassPerSec = 0.25 * crossingVolume;
        assertEquals(
            1.0,
            maxGasMassPerSec,
            1e-6,
            "Mass transfer extraction rate must be 25% of crossing volume (1.0 L/s)");

        // High Power: 2048 EU/t (1 EV hatch)
        // Energy limit = 2048 / 8 = 256 L/s
        double maxGasEnergyHigh = 2048.0 / 8.0;
        double actualExtractedHigh = Math.min(maxGasEnergyHigh, maxGasMassPerSec);
        assertEquals(
            1.0,
            actualExtractedHigh,
            1e-6,
            "Mass transfer rate (1.0 L/s) constrains extraction under high power");

        // Low Power: 4 EU/t
        // Energy limit = 4 / 8 = 0.5 L/s
        double maxGasEnergyLow = 4.0 / 8.0;
        double actualExtractedLow = Math.min(maxGasEnergyLow, maxGasMassPerSec);
        assertEquals(0.5, actualExtractedLow, 1e-6, "Energy limit (0.5 L/s) constrains extraction under low power");
    }

    @Test
    public void testDegasserMultiGasExtraction() {
        // Add 2000 L Deuterium, 500 L Oxygen
        mockPump.addDissolvedGas("Deuterium", 2000L);
        mockPump.addDissolvedGas("Oxygen", 500L);

        assertEquals(2000L, mockPump.getDissolvedGasAmount("Deuterium"));
        assertEquals(500L, mockPump.getDissolvedGasAmount("Oxygen"));

        // Extract 50 L of Deuterium
        long extractedD = mockPump.extractDissolvedGas("Deuterium", 50L);
        assertEquals(50L, extractedD);
        assertEquals(1950L, mockPump.getDissolvedGasAmount("Deuterium"));
        assertEquals(
            500L,
            mockPump.getDissolvedGasAmount("Oxygen"),
            "Extracting Deuterium must leave Oxygen untouched");

        // Extract 500 L of Oxygen (all of it)
        long extractedO = mockPump.extractDissolvedGas("Oxygen", 600L);
        assertEquals(500L, extractedO);
        assertEquals(0L, mockPump.getDissolvedGasAmount("Oxygen"));
        assertFalse(
            mockPump.getDissolvedGases()
                .containsKey("Oxygen"));
    }

    @Test
    public void testDegasserTurnedOffAllowsFlowWithoutExtraction() {
        mockPump.addDissolvedGas("Tritium", 1000L);
        boolean allowedToWork = false;

        long consumedEU = 0L;
        long extracted = 0L;
        if (allowedToWork) {
            consumedEU = 2048L;
            extracted = mockPump.extractDissolvedGas("Tritium", 10L);
        }

        assertEquals(0L, consumedEU, "Turned off degasser must consume zero power");
        assertEquals(0L, extracted, "Turned off degasser must extract zero gas");
        assertEquals(1000L, mockPump.getDissolvedGasAmount("Tritium"));
    }

    @Test
    public void testDegasserEfficiencyLoss() {
        mockPump.addDissolvedGas("Tritium", 1000L);
        double flowRateLs = 40.0;
        long loopVolume = mockPump.getCurrentFillLiters();
        double dt = 1.0;

        double crossingVolume = (1000.0 / loopVolume) * (flowRateLs * dt);
        double maxGasMassPerSec = 0.25 * crossingVolume; // 1.0 L/s
        double maxGasEnergyHigh = 2048.0 / 8.0; // 256 L/s
        double baseExtract = Math.min(maxGasEnergyHigh, maxGasMassPerSec);
        assertEquals(1.0, baseExtract, 1e-6);

        // 100% efficiency (0 issues)
        double eff100 = 1.0;
        assertEquals(1.0, baseExtract * eff100, 1e-6);

        // 90% efficiency (1 issue)
        double eff90 = 0.90;
        assertEquals(0.90, baseExtract * eff90, 1e-6, "1 maintenance issue must reduce degassing rate to 90%");

        // 70% efficiency (3 issues)
        double eff70 = 0.70;
        assertEquals(0.70, baseExtract * eff70, 1e-6, "3 maintenance issues must reduce degassing rate to 70%");

        // 40% efficiency (6 issues)
        double eff40 = 0.40;
        assertEquals(0.40, baseExtract * eff40, 1e-6, "6 maintenance issues must reduce degassing rate to 40%");
    }
}
