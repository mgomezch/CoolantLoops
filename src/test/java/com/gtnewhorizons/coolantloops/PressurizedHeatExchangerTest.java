package com.gtnewhorizons.coolantloops;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.lang.reflect.Field;

import net.minecraft.init.Bootstrap;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.gtnewhorizons.coolantloops.common.metatileentity.hatch.MTEHatchPressurizedFluid;
import com.gtnewhorizons.coolantloops.common.metatileentity.multi.MTEPressurizedHeatExchanger;
import com.gtnewhorizons.coolantloops.engine.CoolantFluidProperty;
import com.gtnewhorizons.coolantloops.engine.LoopSegment;

import cpw.mods.fml.common.ICrashCallable;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.relauncher.FMLInjectionData;
import cpw.mods.fml.relauncher.FMLRelaunchLog;
import cpw.mods.fml.relauncher.Side;
import gregtech.GTMod;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchEnergy;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.metatileentity.implementations.MTEHatchOutput;
import gregtech.api.util.shutdown.ShutDownReasonRegistry;
import gregtech.common.GTProxy;
import gregtech.common.data.GTPowerfailTracker;

public class PressurizedHeatExchangerTest {

    private GTPowerfailTracker mockTracker;

    @BeforeAll
    static void initEnvironment() {
        Thread.currentThread()
            .setName("Server thread");
        try {
            Loader mockLoader = Mockito.mock(Loader.class);
            Mockito.when(mockLoader.getCallableCrashInformation())
                .thenReturn(Mockito.mock(ICrashCallable.class));
            Field f = Loader.class.getDeclaredField("instance");
            f.setAccessible(true);
            if (f.get(null) == null) {
                f.set(null, mockLoader);
            }
        } catch (Throwable ignored) {}

        try {
            Bootstrap.func_151354_b();
        } catch (Throwable ignored) {}

        try {
            Field mcHome = FMLInjectionData.class.getDeclaredField("minecraftHome");
            mcHome.setAccessible(true);
            mcHome.set(null, new File("."));
        } catch (Throwable ignored) {}

        try {
            Field sideField = FMLRelaunchLog.class.getDeclaredField("side");
            sideField.setAccessible(true);
            sideField.set(null, Side.SERVER);
        } catch (Throwable ignored) {}

        // Ensure fluids are registered for test
        registerFluidIfMissing("water");
        registerFluidIfMissing("ic2distilledwater");
        registerFluidIfMissing("distilledwater");
        registerFluidIfMissing("steam");
        registerFluidIfMissing("ic2superheatedsteam");
        registerFluidIfMissing("supercriticalsteam");
        registerFluidIfMissing("supercriticalSteam");
    }

    private static void registerFluidIfMissing(String name) {
        if (FluidRegistry.getFluid(name) == null) {
            Fluid f = new Fluid(name);
            FluidRegistry.registerFluid(f);
        }
    }

    @BeforeEach
    void setupTracker() {
        if (GTMod.proxy == null) {
            GTMod.proxy = Mockito.mock(GTProxy.class);
        }
        mockTracker = Mockito.mock(GTPowerfailTracker.class);
        GTMod.proxy.powerfailTracker = mockTracker;
    }

    private IGregTechTileEntity createMockBaseMetaTileEntity(boolean[] allowedToWorkHolder) {
        IGregTechTileEntity mockBase = Mockito.mock(IGregTechTileEntity.class);
        Mockito.when(mockBase.isAllowedToWork())
            .thenAnswer(inv -> allowedToWorkHolder[0]);
        Mockito.when(mockBase.isServerSide())
            .thenReturn(true);

        boolean[] shutdownHolder = new boolean[] { false };
        gregtech.api.util.shutdown.ShutDownReason[] reasonHolder = new gregtech.api.util.shutdown.ShutDownReason[] {
            ShutDownReasonRegistry.NONE };

        Mockito.doAnswer(inv -> {
            shutdownHolder[0] = inv.getArgument(0);
            return null;
        })
            .when(mockBase)
            .setShutdownStatus(Mockito.anyBoolean());
        Mockito.when(mockBase.wasShutdown())
            .thenAnswer(inv -> shutdownHolder[0]);

        Mockito.doAnswer(inv -> {
            reasonHolder[0] = inv.getArgument(0);
            return null;
        })
            .when(mockBase)
            .setShutDownReason(Mockito.any());
        Mockito.when(mockBase.getLastShutDownReason())
            .thenAnswer(inv -> reasonHolder[0]);

        Mockito.doAnswer(inv -> {
            allowedToWorkHolder[0] = false;
            return null;
        })
            .when(mockBase)
            .disableWorking();

        Mockito.doAnswer(inv -> {
            allowedToWorkHolder[0] = true;
            shutdownHolder[0] = false;
            reasonHolder[0] = ShutDownReasonRegistry.NONE;
            return null;
        })
            .when(mockBase)
            .enableWorking();

        return mockBase;
    }

    private IGregTechTileEntity createMockHatchBase(gregtech.api.metatileentity.MetaTileEntity metaTileEntity) {
        IGregTechTileEntity mockBase = Mockito.mock(IGregTechTileEntity.class);
        Mockito.when(mockBase.getMetaTileEntity())
            .thenReturn(metaTileEntity);
        Mockito.when(mockBase.isServerSide())
            .thenReturn(true);
        metaTileEntity.setBaseMetaTileEntity(mockBase);
        return mockBase;
    }

    private MTEHatchEnergy createMockEnergyHatch(int tier, long storedEU) {
        MTEHatchEnergy hatch = new MTEHatchEnergy("mock_hatch_" + tier, tier, new String[0], null);
        IGregTechTileEntity baseTE = Mockito.mock(IGregTechTileEntity.class);
        long[] stored = new long[] { storedEU };
        Mockito.when(baseTE.getStoredEU())
            .thenAnswer(inv -> stored[0]);
        Mockito.when(baseTE.decreaseStoredEnergyUnits(Mockito.anyLong(), Mockito.anyBoolean()))
            .thenAnswer(inv -> {
                long toDecrease = inv.getArgument(0);
                if (stored[0] >= toDecrease) {
                    stored[0] -= toDecrease;
                    return true;
                }
                return false;
            });
        hatch.setBaseMetaTileEntity(baseTE);
        return hatch;
    }

    @Test
    public void testEnergyConservingCoolantHeatRemovalAndSteamGeneration() {
        boolean[] allowed = new boolean[] { true };
        MTEPressurizedHeatExchanger phe = new MTEPressurizedHeatExchanger("phe_energy_test");
        IGregTechTileEntity mockBase = createMockBaseMetaTileEntity(allowed);
        phe.setBaseMetaTileEntity(mockBase);

        MTEHatchPressurizedFluid inlet = new MTEHatchPressurizedFluid("inlet", 1, new String[0], null);
        MTEHatchPressurizedFluid outlet = new MTEHatchPressurizedFluid("outlet", 1, new String[0], null);
        phe.addPressurizedHatch(createMockHatchBase(inlet), 11);
        phe.addPressurizedHatch(createMockHatchBase(outlet), 11);

        // Power: EV hatch with 1,000,000 EU
        phe.mEnergyHatches.add(createMockEnergyHatch(4, 1_000_000L));

        // Secondary Input: 10,000 mL distilled water
        MTEHatchInput inputHatch = new MTEHatchInput("input", 1, new String[0], null);
        inputHatch.setBaseMetaTileEntity(createMockHatchBase(inputHatch));
        Fluid distilled = FluidRegistry.getFluid("ic2distilledwater");
        assertNotNull(distilled);
        inputHatch.fill(new FluidStack(distilled, 10_000), true);
        phe.mInputHatches.add(inputHatch);

        // Secondary Output: empty output hatch for steam
        MTEHatchOutput outputHatch = new MTEHatchOutput("output", 4, new String[0], null);
        outputHatch.setBaseMetaTileEntity(createMockHatchBase(outputHatch));
        phe.mOutputHatches.add(outputHatch);

        // Primary coolant segment starting at 300 C
        double initialTemp = 300.0;
        LoopSegment seg = new LoopSegment("phe_seg", 1.0, 0.10, 0.000045, 1.2, 500.0, 2000.0, initialTemp);
        CoolantFluidProperty fluid = CoolantFluidProperty.get("ic2distilledwater");
        assertNotNull(fluid);
        double flowRateM3s = 0.05; // 50 L/s

        // Process thermal exchange
        phe.processThermalExchange(flowRateM3s, 0.05, fluid, seg);

        // Verify water was boiled
        int remainingWater = inputHatch.getFluid().amount;
        int waterBoiled = 10_000 - remainingWater;
        assertTrue(waterBoiled > 0, "Water must be boiled during heat exchange");

        // Verify steam produced in output hatch
        assertNotNull(outputHatch.getFluid());
        assertEquals(
            "ic2superheatedsteam",
            outputHatch.getFluid()
                .getFluid()
                .getName());
        assertEquals(waterBoiled * 160, outputHatch.getFluid().amount, "Steam expansion must be 1:160");

        // Verify energy conservation: Q_actual = waterBoiled * 2260 J
        double actualHeatJoules = waterBoiled * 2260.0;
        double expectedWatts = actualHeatJoules / 0.05;
        assertEquals(expectedWatts, phe.getLastHeatTransferredWatts(), 1e-4);

        // Verify primary coolant cooled down
        double effectiveVolume = Math.max(seg.getVolume(), flowRateM3s * 0.05);
        double fluidHeatCapacity = effectiveVolume * fluid.getDensity() * fluid.getSpecificHeat();
        double expectedTempDrop = actualHeatJoules / fluidHeatCapacity;
        double expectedTemp = initialTemp - expectedTempDrop;

        assertEquals(expectedTemp, phe.getDeviceTemperatureCelsius(), 1e-4);
        assertEquals(expectedTemp, seg.getCurrentTemperatureCelsius(), 1e-4);
        assertEquals(expectedTemp, outlet.getDeviceTemperatureCelsius(), 1e-4);
    }

    @Test
    public void testPlainWaterStrictlyRejectedNoBoilingNoCooling() {
        boolean[] allowed = new boolean[] { true };
        MTEPressurizedHeatExchanger phe = new MTEPressurizedHeatExchanger("phe_plain_water_test");
        IGregTechTileEntity mockBase = createMockBaseMetaTileEntity(allowed);
        phe.setBaseMetaTileEntity(mockBase);

        MTEHatchPressurizedFluid inlet = new MTEHatchPressurizedFluid("inlet", 1, new String[0], null);
        MTEHatchPressurizedFluid outlet = new MTEHatchPressurizedFluid("outlet", 1, new String[0], null);
        phe.addPressurizedHatch(createMockHatchBase(inlet), 11);
        phe.addPressurizedHatch(createMockHatchBase(outlet), 11);
        phe.mEnergyHatches.add(createMockEnergyHatch(4, 1_000_000L));

        // Plain Minecraft water in input hatch (must be REJECTED)
        MTEHatchInput inputHatch = new MTEHatchInput("input", 1, new String[0], null);
        inputHatch.setBaseMetaTileEntity(createMockHatchBase(inputHatch));
        Fluid plainWater = FluidRegistry.getFluid("water");
        assertNotNull(plainWater);
        inputHatch.fill(new FluidStack(plainWater, 10_000), true);
        phe.mInputHatches.add(inputHatch);

        MTEHatchOutput outputHatch = new MTEHatchOutput("output", 4, new String[0], null);
        outputHatch.setBaseMetaTileEntity(createMockHatchBase(outputHatch));
        phe.mOutputHatches.add(outputHatch);

        LoopSegment seg = new LoopSegment("phe_seg", 1.0, 0.10, 0.000045, 1.2, 500.0, 2000.0, 300.0);
        CoolantFluidProperty fluid = CoolantFluidProperty.get("ic2distilledwater");
        phe.processThermalExchange(0.05, 0.05, fluid, seg);

        // Plain water must NOT be drained
        assertEquals(10_000, inputHatch.getFluid().amount, "Plain water must not be consumed");
        assertNull(outputHatch.getFluid(), "No steam must be produced from plain water");
        assertEquals(300.0, phe.getDeviceTemperatureCelsius(), "Primary coolant must not cool down");
        assertEquals(300.0, seg.getCurrentTemperatureCelsius());
        assertEquals(0.0, phe.getLastHeatTransferredWatts());
    }

    @Test
    public void testDrySecondaryNoWaterNoCooling() {
        boolean[] allowed = new boolean[] { true };
        MTEPressurizedHeatExchanger phe = new MTEPressurizedHeatExchanger("phe_dry_test");
        IGregTechTileEntity mockBase = createMockBaseMetaTileEntity(allowed);
        phe.setBaseMetaTileEntity(mockBase);

        MTEHatchPressurizedFluid inlet = new MTEHatchPressurizedFluid("inlet", 1, new String[0], null);
        MTEHatchPressurizedFluid outlet = new MTEHatchPressurizedFluid("outlet", 1, new String[0], null);
        phe.addPressurizedHatch(createMockHatchBase(inlet), 11);
        phe.addPressurizedHatch(createMockHatchBase(outlet), 11);
        phe.mEnergyHatches.add(createMockEnergyHatch(4, 1_000_000L));

        // Empty input hatch
        MTEHatchInput inputHatch = new MTEHatchInput("input", 1, new String[0], null);
        inputHatch.setBaseMetaTileEntity(createMockHatchBase(inputHatch));
        phe.mInputHatches.add(inputHatch);

        LoopSegment seg = new LoopSegment("phe_seg", 1.0, 0.10, 0.000045, 1.2, 500.0, 2000.0, 300.0);
        CoolantFluidProperty fluid = CoolantFluidProperty.get("ic2distilledwater");
        phe.processThermalExchange(0.05, 0.05, fluid, seg);

        assertEquals(300.0, phe.getDeviceTemperatureCelsius(), "Dry PHE must not cool primary coolant");
        assertEquals(300.0, seg.getCurrentTemperatureCelsius());
        assertEquals(0.0, phe.getLastHeatTransferredWatts());
    }

    @Test
    public void testCoolantBelow100CelsiusNoBoiling() {
        boolean[] allowed = new boolean[] { true };
        MTEPressurizedHeatExchanger phe = new MTEPressurizedHeatExchanger("phe_subboiling_test");
        IGregTechTileEntity mockBase = createMockBaseMetaTileEntity(allowed);
        phe.setBaseMetaTileEntity(mockBase);

        MTEHatchPressurizedFluid inlet = new MTEHatchPressurizedFluid("inlet", 1, new String[0], null);
        MTEHatchPressurizedFluid outlet = new MTEHatchPressurizedFluid("outlet", 1, new String[0], null);
        phe.addPressurizedHatch(createMockHatchBase(inlet), 11);
        phe.addPressurizedHatch(createMockHatchBase(outlet), 11);
        phe.mEnergyHatches.add(createMockEnergyHatch(4, 1_000_000L));

        MTEHatchInput inputHatch = new MTEHatchInput("input", 1, new String[0], null);
        inputHatch.setBaseMetaTileEntity(createMockHatchBase(inputHatch));
        Fluid distilled = FluidRegistry.getFluid("ic2distilledwater");
        inputHatch.fill(new FluidStack(distilled, 10_000), true);
        phe.mInputHatches.add(inputHatch);

        // Primary coolant at 90 C (below 100 C boiling point)
        LoopSegment seg = new LoopSegment("phe_seg", 1.0, 0.10, 0.000045, 1.2, 500.0, 2000.0, 90.0);
        CoolantFluidProperty fluid = CoolantFluidProperty.get("ic2distilledwater");
        phe.processThermalExchange(0.05, 0.05, fluid, seg);

        assertEquals(10_000, inputHatch.getFluid().amount, "No water boiled below 100 C");
        assertEquals(90.0, phe.getDeviceTemperatureCelsius(), "Coolant temperature remains unchanged");
        assertEquals(90.0, seg.getCurrentTemperatureCelsius());
        assertEquals(0.0, phe.getLastHeatTransferredWatts());
    }

    @Test
    public void testSteamTemperatureTransitions() {
        // Test standard steam (< 300 C), superheated (300 C - 700 C), and supercritical (>= 700 C)
        boolean[] allowed = new boolean[] { true };
        MTEPressurizedHeatExchanger phe = new MTEPressurizedHeatExchanger("phe_transitions_test");
        IGregTechTileEntity mockBase = createMockBaseMetaTileEntity(allowed);
        phe.setBaseMetaTileEntity(mockBase);

        MTEHatchPressurizedFluid inlet = new MTEHatchPressurizedFluid("inlet", 1, new String[0], null);
        MTEHatchPressurizedFluid outlet = new MTEHatchPressurizedFluid("outlet", 1, new String[0], null);
        phe.addPressurizedHatch(createMockHatchBase(inlet), 11);
        phe.addPressurizedHatch(createMockHatchBase(outlet), 11);
        phe.mEnergyHatches.add(createMockEnergyHatch(4, 1_000_000L));

        MTEHatchInput inputHatch = new MTEHatchInput("input", 1, new String[0], null);
        inputHatch.setBaseMetaTileEntity(createMockHatchBase(inputHatch));
        Fluid distilled = FluidRegistry.getFluid("ic2distilledwater");
        inputHatch.fill(new FluidStack(distilled, 50_000), true);
        phe.mInputHatches.add(inputHatch);

        MTEHatchOutput outputHatch = new MTEHatchOutput("output", 4, new String[0], null);
        outputHatch.setBaseMetaTileEntity(createMockHatchBase(outputHatch));
        phe.mOutputHatches.add(outputHatch);

        // Case 1: Coolant at 200 C -> standard steam
        LoopSegment seg = new LoopSegment("phe_seg", 1.0, 0.10, 0.000045, 1.2, 500.0, 2000.0, 200.0);
        CoolantFluidProperty fluid = CoolantFluidProperty.get("ic2distilledwater");
        phe.processThermalExchange(0.05, 0.05, fluid, seg);
        assertEquals(
            "steam",
            outputHatch.getFluid()
                .getFluid()
                .getName());

        // Case 2: Coolant at 800 C -> supercritical steam
        outputHatch.drain(outputHatch.getFluid().amount, true);
        seg.setCurrentTemperatureCelsius(800.0);
        phe.processThermalExchange(0.05, 0.05, fluid, seg);
        assertTrue(
            outputHatch.getFluid()
                .getFluid()
                .getName()
                .equalsIgnoreCase("supercriticalsteam"),
            "Expected supercritical steam at 800 C, got: " + outputHatch.getFluid()
                .getFluid()
                .getName());
    }

    @Test
    public void testCryogenicLiquidNitrogenBoilingAndSubzeroCooling() {
        registerFluidIfMissing("liquidnitrogen");
        registerFluidIfMissing("nitrogen");

        boolean[] allowed = new boolean[] { true };
        MTEPressurizedHeatExchanger phe = new MTEPressurizedHeatExchanger("phe_liquid_nitrogen_test");
        IGregTechTileEntity mockBase = createMockBaseMetaTileEntity(allowed);
        phe.setBaseMetaTileEntity(mockBase);

        MTEHatchPressurizedFluid inlet = new MTEHatchPressurizedFluid("inlet", 1, new String[0], null);
        MTEHatchPressurizedFluid outlet = new MTEHatchPressurizedFluid("outlet", 1, new String[0], null);
        phe.addPressurizedHatch(createMockHatchBase(inlet), 11);
        phe.addPressurizedHatch(createMockHatchBase(outlet), 11);
        phe.mEnergyHatches.add(createMockEnergyHatch(4, 1_000_000L));

        MTEHatchInput inputHatch = new MTEHatchInput("input", 1, new String[0], null);
        inputHatch.setBaseMetaTileEntity(createMockHatchBase(inputHatch));
        Fluid liquidN2 = FluidRegistry.getFluid("liquidnitrogen");
        assertNotNull(liquidN2);
        inputHatch.fill(new FluidStack(liquidN2, 10_000), true);
        phe.mInputHatches.add(inputHatch);

        MTEHatchOutput outputHatch = new MTEHatchOutput("output", 8, new String[0], null);
        outputHatch.setBaseMetaTileEntity(createMockHatchBase(outputHatch));
        phe.mOutputHatches.add(outputHatch);

        // IC2 Coolant loop starting at 20 C
        double initialTemp = 20.0;
        LoopSegment seg = new LoopSegment("phe_seg", 1.0, 0.10, 0.000045, 1.2, 500.0, 2000.0, initialTemp);
        CoolantFluidProperty fluid = CoolantFluidProperty.get("ic2coolant");
        assertNotNull(fluid);

        // Secondary boiling of liquid nitrogen (T_boil = -196 C, deltaT = 20 - (-196) = 216 C)
        phe.processThermalExchange(0.05, 0.05, fluid, seg);

        int remainingN2 = (inputHatch.getFluid() != null) ? inputHatch.getFluid().amount : 0;
        int n2Boiled = 10_000 - remainingN2;
        assertTrue(n2Boiled > 0, "Liquid nitrogen must boil when coolant is warmer than -196 C");

        // Gaseous nitrogen output at 1:160 expansion
        assertNotNull(outputHatch.getFluid());
        assertEquals("nitrogen", outputHatch.getFluid().getFluid().getName());
        assertEquals(n2Boiled * 160, outputHatch.getFluid().amount);

        // Coolant temperature dropped below initial 20 C toward -196 C
        assertTrue(phe.getDeviceTemperatureCelsius() < initialTemp);
        assertTrue(seg.getCurrentTemperatureCelsius() < initialTemp);
        assertTrue(phe.getLastHeatTransferredWatts() > 0.0);
    }

    @Test
    public void testCryogenicLiquidHeliumBoiling() {
        registerFluidIfMissing("liquid_helium");
        registerFluidIfMissing("helium");

        boolean[] allowed = new boolean[] { true };
        MTEPressurizedHeatExchanger phe = new MTEPressurizedHeatExchanger("phe_liquid_helium_test");
        IGregTechTileEntity mockBase = createMockBaseMetaTileEntity(allowed);
        phe.setBaseMetaTileEntity(mockBase);

        MTEHatchPressurizedFluid inlet = new MTEHatchPressurizedFluid("inlet", 1, new String[0], null);
        MTEHatchPressurizedFluid outlet = new MTEHatchPressurizedFluid("outlet", 1, new String[0], null);
        phe.addPressurizedHatch(createMockHatchBase(inlet), 11);
        phe.addPressurizedHatch(createMockHatchBase(outlet), 11);
        phe.mEnergyHatches.add(createMockEnergyHatch(4, 1_000_000L));

        MTEHatchInput inputHatch = new MTEHatchInput("input", 1, new String[0], null);
        inputHatch.setBaseMetaTileEntity(createMockHatchBase(inputHatch));
        Fluid liquidHe = FluidRegistry.getFluid("liquid_helium");
        assertNotNull(liquidHe);
        inputHatch.fill(new FluidStack(liquidHe, 10_000), true);
        phe.mInputHatches.add(inputHatch);

        MTEHatchOutput outputHatch = new MTEHatchOutput("output", 8, new String[0], null);
        outputHatch.setBaseMetaTileEntity(createMockHatchBase(outputHatch));
        phe.mOutputHatches.add(outputHatch);

        // IC2 Coolant loop at -100 C (well above Helium boiling point -269 C)
        LoopSegment seg = new LoopSegment("phe_seg", 1.0, 0.10, 0.000045, 1.2, 500.0, 2000.0, -100.0);
        CoolantFluidProperty fluid = CoolantFluidProperty.get("ic2coolant");

        phe.processThermalExchange(0.05, 0.05, fluid, seg);

        int remainingHe = (inputHatch.getFluid() != null) ? inputHatch.getFluid().amount : 0;
        int heBoiled = 10_000 - remainingHe;
        assertTrue(heBoiled > 0, "Liquid helium must boil when coolant is warmer than -269 C");

        assertNotNull(outputHatch.getFluid());
        assertEquals("helium", outputHatch.getFluid().getFluid().getName());
        assertEquals(heBoiled * 160, outputHatch.getFluid().amount);
        assertTrue(phe.getDeviceTemperatureCelsius() < -100.0);
    }

}
