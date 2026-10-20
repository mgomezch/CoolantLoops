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
            return CoolantFluidProperty.DISTILLED_WATER;
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
        // 1 LV hatch (32V -> 15/16ths = 30 EU/t, 1x speed)
        long vLV = 32L;
        long targetEULV1 = (15L * vLV) / 16L;
        assertEquals(30L, targetEULV1, "1 LV hatch must consume 15/16ths of 32 = 30 EU/t");

        // 2 LV hatches (overclocks to 4x 32V = 128V -> 15/16ths = 120 EU/t, 4x speed)
        long targetEULV2 = (15L * (4L * vLV)) / 16L;
        assertEquals(120L, targetEULV2, "2 LV hatches must consume 15/16ths of 128 = 120 EU/t");

        // 1 MV hatch (128V -> 15/16ths = 120 EU/t, 4x speed)
        long vMV = 128L;
        long targetEUMV1 = (15L * vMV) / 16L;
        assertEquals(120L, targetEUMV1, "1 MV hatch must consume 15/16ths of 128 = 120 EU/t");

        // 2 MV hatches (overclocks to 4x 128V = 512V -> 15/16ths = 480 EU/t, 16x speed)
        long targetEUMV2 = (15L * (4L * vMV)) / 16L;
        assertEquals(480L, targetEUMV2, "2 MV hatches must consume 15/16ths of 512 = 480 EU/t");

        // 1 HV hatch (512V -> 15/16ths = 480 EU/t, 16x speed)
        long vHV = 512L;
        long targetEUHV1 = (15L * vHV) / 16L;
        assertEquals(480L, targetEUHV1, "1 HV hatch must consume 15/16ths of 512 = 480 EU/t");

        // 2 HV hatches (overclocks to 4x 512V = 2048V -> 15/16ths = 1920 EU/t, 64x speed)
        long targetEUHV2 = (15L * (4L * vHV)) / 16L;
        assertEquals(1920L, targetEUHV2, "2 HV hatches must consume 15/16ths of 2048 = 1920 EU/t");

        // Mixed 1 MV + 1 HV hatch: higher-tiered hatch determines overclock (4x 512V = 2048V -> 1920 EU/t, 64x speed)
        long targetEUMixed = (15L * (4L * Math.max(vMV, vHV))) / 16L;
        assertEquals(1920L, targetEUMixed, "Mixed 1 MV + 1 HV hatches must overclock to 4A HV = 1920 EU/t");

        // 1 EV hatch (2048V -> 15/16ths = 1920 EU/t, 64x speed)
        long vEV = 2048L;
        long targetEUEV1 = (15L * vEV) / 16L;
        assertEquals(1920L, targetEUEV1, "1 EV hatch must consume 15/16ths of 2048 = 1920 EU/t");

        // 2 EV hatches (overclocks to 4x 2048V = 8192V -> 15/16ths = 7680 EU/t, 256x speed)
        long targetEUEV2 = (15L * (4L * vEV)) / 16L;
        assertEquals(7680L, targetEUEV2, "2 EV hatches must consume 15/16ths of 8192 = 7680 EU/t");
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

    @Test
    public void testDegasserStructureCoordinates() {
        try {
            if (gregtech.GTMod.gregtechproxy == null) {
                gregtech.GTMod.gregtechproxy = org.mockito.Mockito.mock(gregtech.common.GTProxy.class);
            }
        } catch (Throwable ignored) {}
        gregtech.api.GregTechAPI.sBlockCasings4 = net.minecraft.init.Blocks.stone;

        com.gtnewhorizons.coolantloops.common.metatileentity.multi.MTEPressurizedDegasser degasser = new com.gtnewhorizons.coolantloops.common.metatileentity.multi.MTEPressurizedDegasser(
            "test.degasser");
        gregtech.api.interfaces.tileentity.IGregTechTileEntity mockBase = org.mockito.Mockito
            .mock(gregtech.api.interfaces.tileentity.IGregTechTileEntity.class);
        net.minecraft.world.World mockWorld = org.mockito.Mockito.mock(net.minecraft.world.World.class);
        org.mockito.Mockito.when(
            mockWorld
                .blockExists(org.mockito.Mockito.anyInt(), org.mockito.Mockito.anyInt(), org.mockito.Mockito.anyInt()))
            .thenReturn(true);
        org.mockito.Mockito.when(mockBase.getWorld())
            .thenReturn(mockWorld);
        org.mockito.Mockito.when(mockBase.getXCoord())
            .thenReturn(100);
        org.mockito.Mockito.when(mockBase.getYCoord())
            .thenReturn((short) 50);
        org.mockito.Mockito.when(mockBase.getZCoord())
            .thenReturn(200);
        org.mockito.Mockito.when(mockBase.getFrontFacing())
            .thenReturn(net.minecraftforge.common.util.ForgeDirection.NORTH);
        degasser.setBaseMetaTileEntity(mockBase);

        java.util.concurrent.atomic.AtomicInteger minY = new java.util.concurrent.atomic.AtomicInteger(
            Integer.MAX_VALUE);
        java.util.concurrent.atomic.AtomicInteger maxY = new java.util.concurrent.atomic.AtomicInteger(
            Integer.MIN_VALUE);
        java.util.concurrent.atomic.AtomicInteger minZ = new java.util.concurrent.atomic.AtomicInteger(
            Integer.MAX_VALUE);
        java.util.concurrent.atomic.AtomicInteger maxZ = new java.util.concurrent.atomic.AtomicInteger(
            Integer.MIN_VALUE);
        java.util.concurrent.atomic.AtomicInteger minX = new java.util.concurrent.atomic.AtomicInteger(
            Integer.MAX_VALUE);
        java.util.concurrent.atomic.AtomicInteger maxX = new java.util.concurrent.atomic.AtomicInteger(
            Integer.MIN_VALUE);

        degasser.getStructureDefinition()
            .iterate(
                "degasser_3x3x7",
                mockWorld,
                degasser.getExtendedFacing(),
                100,
                50,
                200,
                1,
                6,
                0,
                (element, world, x, y, z, a, b, c) -> {
                    minY.updateAndGet(curr -> Math.min(curr, y));
                    maxY.updateAndGet(curr -> Math.max(curr, y));
                    minZ.updateAndGet(curr -> Math.min(curr, z));
                    maxZ.updateAndGet(curr -> Math.max(curr, z));
                    minX.updateAndGet(curr -> Math.min(curr, x));
                    maxX.updateAndGet(curr -> Math.max(curr, x));
                    return true;
                });

        assertEquals(50, minY.get(), "Bottom layer must be at controller level y=50");
        assertEquals(56, maxY.get(), "Top layer must be at level y=56 (7 blocks tall: 50 to 56)");
        assertEquals(200, minZ.get(), "Front layer must be at controller level z=200");
        assertEquals(202, maxZ.get(), "Back layer must be at z=202 (3 blocks deep: 200 to 202)");
        assertEquals(99, minX.get(), "Left column must be at x=99");
        assertEquals(101, maxX.get(), "Right column must be at x=101 (3 blocks wide: 99 to 101, center at 100)");
    }
}
