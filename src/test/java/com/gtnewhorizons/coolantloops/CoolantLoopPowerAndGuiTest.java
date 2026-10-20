package com.gtnewhorizons.coolantloops;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import net.minecraft.init.Bootstrap;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.gtnewhorizons.coolantloops.common.gui.CoolantLoopMachineGui;
import com.gtnewhorizons.coolantloops.common.metatileentity.hatch.MTEHatchPressurizedFluid;
import com.gtnewhorizons.coolantloops.common.metatileentity.hatch.MTEHatchPressurizedInput;
import com.gtnewhorizons.coolantloops.common.metatileentity.hatch.MTEHatchPressurizedOutput;
import com.gtnewhorizons.coolantloops.common.metatileentity.multi.MTECoolantPump;
import com.gtnewhorizons.coolantloops.common.metatileentity.multi.MTEPressurizedDegasser;
import com.gtnewhorizons.coolantloops.common.metatileentity.multi.MTEPressurizedHeatExchanger;
import com.gtnewhorizons.coolantloops.engine.CoolantFluidProperty;
import com.gtnewhorizons.coolantloops.engine.ICoolantLoopPump;
import com.gtnewhorizons.coolantloops.engine.LoopSegment;

import cpw.mods.fml.common.ICrashCallable;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.relauncher.FMLInjectionData;
import cpw.mods.fml.relauncher.FMLRelaunchLog;
import cpw.mods.fml.relauncher.Side;
import gregtech.GTMod;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchEnergy;
import gregtech.api.util.shutdown.ShutDownReasonRegistry;
import gregtech.common.GTProxy;
import gregtech.common.data.GTPowerfailTracker;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;

/**
 * Verifies standard GT5U powerfail behavior, power draw calculations,
 * atomic draining, and GUI uniformity (recipe lock hidden, power panel enabled)
 * across MTECoolantPump, MTEPressurizedHeatExchanger, and MTEPressurizedDegasser.
 */
public class CoolantLoopPowerAndGuiTest {

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
    }

    @BeforeEach
    void setupTracker() {
        if (GTMod.proxy == null) {
            GTMod.proxy = Mockito.mock(GTProxy.class);
        }
        mockTracker = Mockito.mock(GTPowerfailTracker.class);
        GTMod.proxy.powerfailTracker = mockTracker;
    }

    private MTEHatchEnergy createMockEnergyHatch(int tier, long[] storedEUHolder) {
        MTEHatchEnergy hatch = new MTEHatchEnergy("mock_hatch_" + tier, tier, new String[0], null);
        IGregTechTileEntity baseTE = Mockito.mock(IGregTechTileEntity.class);
        Mockito.when(baseTE.getStoredEU())
            .thenAnswer(inv -> storedEUHolder[0]);
        Mockito.when(baseTE.decreaseStoredEnergyUnits(Mockito.anyLong(), Mockito.anyBoolean()))
            .thenAnswer(inv -> {
                long toDecrease = inv.getArgument(0);
                if (storedEUHolder[0] >= toDecrease) {
                    storedEUHolder[0] -= toDecrease;
                    return true;
                }
                return false;
            });
        hatch.setBaseMetaTileEntity(baseTE);
        return hatch;
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

    private boolean invokeShouldDisplayRecipeLock(MTEMultiBlockBaseGui<?> gui) throws Exception {
        Method m = MTEMultiBlockBaseGui.class.getDeclaredMethod("shouldDisplayRecipeLock");
        m.setAccessible(true);
        return (boolean) m.invoke(gui);
    }

    private boolean invokeShouldDisplayVoidExcess(MTEMultiBlockBaseGui<?> gui) throws Exception {
        Method m = MTEMultiBlockBaseGui.class.getDeclaredMethod("shouldDisplayVoidExcess");
        m.setAccessible(true);
        return (boolean) m.invoke(gui);
    }

    private boolean invokeShouldDisplayInputSeparation(MTEMultiBlockBaseGui<?> gui) throws Exception {
        Method m = MTEMultiBlockBaseGui.class.getDeclaredMethod("shouldDisplayInputSeparation");
        m.setAccessible(true);
        return (boolean) m.invoke(gui);
    }

    private boolean invokeShouldDisplayBatchMode(MTEMultiBlockBaseGui<?> gui) throws Exception {
        Method m = MTEMultiBlockBaseGui.class.getDeclaredMethod("shouldDisplayBatchMode");
        m.setAccessible(true);
        return (boolean) m.invoke(gui);
    }

    private MTEMultiBlockBaseGui<?> getGuiViaReflection(Object multiblock) throws Exception {
        Method m = multiblock.getClass()
            .getDeclaredMethod("getGui");
        m.setAccessible(true);
        return (MTEMultiBlockBaseGui<?>) m.invoke(multiblock);
    }

    // =========================================================================
    // 1. Coolant Loop Machine GUI Uniformity & Recipe Lock Hiding
    // =========================================================================

    @Test
    public void testGuiUniformityAndRecipeLockHiddenAcrossMultiblocks() throws Exception {
        MTECoolantPump pump = new MTECoolantPump("pump_gui_test");
        MTEPressurizedHeatExchanger phe = new MTEPressurizedHeatExchanger("phe_gui_test");
        MTEPressurizedDegasser degasser = new MTEPressurizedDegasser("degasser_gui_test");

        // Verify getGui returns CoolantLoopMachineGui
        MTEMultiBlockBaseGui<?> pumpGui = getGuiViaReflection(pump);
        MTEMultiBlockBaseGui<?> pheGui = getGuiViaReflection(phe);
        MTEMultiBlockBaseGui<?> degasserGui = getGuiViaReflection(degasser);

        assertInstanceOf(CoolantLoopMachineGui.class, pumpGui);
        assertInstanceOf(CoolantLoopMachineGui.class, pheGui);
        assertInstanceOf(CoolantLoopMachineGui.class, degasserGui);

        // Verify shouldDisplayRecipeLock returns false for all three
        assertFalse(invokeShouldDisplayRecipeLock(pumpGui), "Pump GUI must hide lock recipe button");
        assertFalse(invokeShouldDisplayRecipeLock(pheGui), "Heat Exchanger GUI must hide lock recipe button");
        assertFalse(invokeShouldDisplayRecipeLock(degasserGui), "Degasser GUI must hide lock recipe button");

        // Verify void excess, input separation, and batch mode buttons are hidden
        assertFalse(invokeShouldDisplayVoidExcess(pumpGui), "Pump GUI must hide void excess button");
        assertFalse(invokeShouldDisplayVoidExcess(pheGui), "Heat Exchanger GUI must hide void excess button");
        assertFalse(invokeShouldDisplayVoidExcess(degasserGui), "Degasser GUI must hide void excess button");

        assertFalse(invokeShouldDisplayInputSeparation(pumpGui), "Pump GUI must hide input separation button");
        assertFalse(invokeShouldDisplayInputSeparation(pheGui), "Heat Exchanger GUI must hide input separation button");
        assertFalse(invokeShouldDisplayInputSeparation(degasserGui), "Degasser GUI must hide input separation button");

        assertFalse(invokeShouldDisplayBatchMode(pumpGui), "Pump GUI must hide batch mode button");
        assertFalse(invokeShouldDisplayBatchMode(pheGui), "Heat Exchanger GUI must hide batch mode button");
        assertFalse(invokeShouldDisplayBatchMode(degasserGui), "Degasser GUI must hide batch mode button");

        // Verify supportsSingleRecipeLocking, supportsVoidProtection, supportsInputSeparation, supportsBatchMode return
        // false
        assertFalse(pump.supportsSingleRecipeLocking());
        assertFalse(phe.supportsSingleRecipeLocking());
        assertFalse(degasser.supportsSingleRecipeLocking());

        assertFalse(pump.supportsVoidProtection());
        assertFalse(pump.supportsInputSeparation());
        assertFalse(pump.supportsBatchMode());

        // Verify supportsPowerPanel returns true
        assertTrue(pump.supportsPowerPanel(), "Pump must support power dialog");
        assertTrue(phe.supportsPowerPanel(), "Heat Exchanger must support power dialog");
        assertTrue(degasser.supportsPowerPanel(), "Degasser must support power dialog");
    }

    // =========================================================================
    // 2. Coolant Pump Powerfail Event & Standard Shutdown
    // =========================================================================

    @Test
    public void testCoolantPumpStandardPowerfailEventCreation() {
        boolean[] allowed = new boolean[] { true };
        MTECoolantPump pump = new MTECoolantPump("pump_powerfail");
        IGregTechTileEntity mockBase = createMockBaseMetaTileEntity(allowed);
        pump.setBaseMetaTileEntity(mockBase);
        pump.setLoopFormed(true);

        long[] hatchEU = new long[] { 500L }; // Insufficient (< 2048 for EV)
        pump.mEnergyHatches.add(createMockEnergyHatch(4, hatchEU));

        ItemStack mockRotor = Mockito.mock(ItemStack.class);
        Mockito.when(mockRotor.getItem())
            .thenReturn(Mockito.mock(net.minecraft.item.Item.class));
        pump.setRotorStack(mockRotor);
        pump.getEngine()
            .addSegment(new LoopSegment("seg1", 10.0, 0.2, 0.0001, 1.0, 100.0, 1000.0, 20.0));
        pump.getEngine()
            .setFluid(CoolantFluidProperty.DISTILLED_WATER);
        pump.setRequiredFillLiters(1000L);
        pump.setCurrentFillLiters(1000L);
        pump.setLoopState(MTECoolantPump.LoopState.CIRCULATING);

        // makePowerfailEvents is true by default
        assertTrue(pump.makesPowerfailEvents());
        pump.stepCoolantLoop();

        // Standard shutdown verification
        assertFalse(pump.isAllowedToWork());
        assertTrue(pump.isPowerFailed());
        assertEquals(MTECoolantPump.LoopState.STOPPED, pump.getLoopState());
        assertEquals(ShutDownReasonRegistry.POWER_LOSS.getDisplayString(), pump.getLoopStatus());
        assertEquals(500L, hatchEU[0], "Atomic drain: buffer must not be modified");

        // Verify GTPowerfailTracker received event
        Mockito.verify(mockTracker, Mockito.times(1))
            .createPowerfailEvent(mockBase);

        // Reset and test with makePowerfailEvents = false (toggled from Power Dialog)
        pump.setPowerfailEventCreationStatus(false);
        assertFalse(pump.makesPowerfailEvents());
        allowed[0] = true;
        mockBase.enableWorking();
        pump.onEnableWorking();

        pump.stepCoolantLoop();
        assertFalse(pump.isAllowedToWork());
        // Verify no additional powerfail event was created
        Mockito.verify(mockTracker, Mockito.times(1))
            .createPowerfailEvent(mockBase);
    }

    // =========================================================================
    // 3. Pressurized Heat Exchanger Power Draw & Powerfail
    // =========================================================================

    @Test
    public void testHeatExchangerPowerDrawAndAtomicPowerfail() {
        boolean[] allowed = new boolean[] { true };
        MTEPressurizedHeatExchanger phe = new MTEPressurizedHeatExchanger("phe_power");
        IGregTechTileEntity mockBase = createMockBaseMetaTileEntity(allowed);
        phe.setBaseMetaTileEntity(mockBase);

        MTEHatchPressurizedInput inlet = new MTEHatchPressurizedInput("inlet", 1, new String[0], null);
        MTEHatchPressurizedOutput outlet = new MTEHatchPressurizedOutput("outlet", 1, new String[0], null);
        phe.addPressurizedHatch(createMockHatchBase(inlet), 11);
        phe.addPressurizedHatch(createMockHatchBase(outlet), 11);

        // 1 EV hatch (tier 4 -> 15/16ths of 2048V = 1920 EU/t)
        long[] hatch1EU = new long[] { 10000L };
        MTEHatchEnergy evHatch = createMockEnergyHatch(4, hatch1EU);
        phe.mEnergyHatches.add(evHatch);
        assertEquals(1920L, phe.getRequiredPowerDrawEU());

        // Step heat exchange with sufficient power
        assertTrue(phe.stepHeatExchange());
        assertEquals(10000L - 1920L, hatch1EU[0]);
        assertTrue(phe.isAllowedToWork());

        // Add 2nd EV hatch -> overclocks to 4A EV = 8192V -> 15/16ths = 7680 EU/t
        long[] hatch2EU = new long[] { 5000L };
        MTEHatchEnergy evHatch2 = createMockEnergyHatch(4, hatch2EU);
        phe.mEnergyHatches.add(evHatch2);
        assertEquals(7680L, phe.getRequiredPowerDrawEU());

        // Combined available = (10000 - 1920) + 5000 = 13080 >= 7680
        assertTrue(phe.stepHeatExchange());
        assertEquals(10000L - 1920L - 7680L, hatch1EU[0]); // 400L
        assertEquals(5000L, hatch2EU[0]);

        // Now drop power below 7680 EU total: hatch1 = 0, hatch2 = 5000
        hatch1EU[0] = 0L;
        hatch2EU[0] = 5000L;
        assertFalse(phe.stepHeatExchange());

        // Verify atomic behavior and standard GT5U powerfail
        assertFalse(phe.isAllowedToWork());
        assertEquals(5000L, hatch2EU[0], "Atomic drain: buffer must not be modified");
        Mockito.verify(mockTracker, Mockito.times(1))
            .createPowerfailEvent(mockBase);
    }

    // =========================================================================
    // 4. Pressurized Degasser Power Draw & Powerfail
    // =========================================================================

    @Test
    public void testDegasserPowerDrawAndAtomicPowerfail() {
        boolean[] allowed = new boolean[] { true };
        MTEPressurizedDegasser degasser = new MTEPressurizedDegasser("degasser_power");
        IGregTechTileEntity mockBase = createMockBaseMetaTileEntity(allowed);
        degasser.setBaseMetaTileEntity(mockBase);

        MTEHatchPressurizedInput inlet = new MTEHatchPressurizedInput("inlet", 1, new String[0], null);
        MTEHatchPressurizedOutput outlet = new MTEHatchPressurizedOutput("outlet", 1, new String[0], null);
        degasser.addBottomHatch(createMockHatchBase(inlet), 2);
        degasser.addBottomHatch(createMockHatchBase(outlet), 2);

        ICoolantLoopPump mockPump = Mockito.mock(ICoolantLoopPump.class);
        degasser.setConnectedPump(mockPump);

        // 1 EV hatch (tier 4 -> 15/16ths of 2048V = 1920 EU/t)
        long[] hatch1EU = new long[] { 5000L };
        MTEHatchEnergy evHatch = createMockEnergyHatch(4, hatch1EU);
        degasser.mEnergyHatches.add(evHatch);
        assertEquals(1920L, degasser.getRequiredPowerDrawEU());

        // Step degassing with sufficient power
        assertTrue(degasser.stepDegassing(0.04, 0.05));
        assertEquals(5000L - 1920L, hatch1EU[0]);
        assertEquals(1920L, degasser.getLastDrainedEU());
        assertTrue(degasser.isAllowedToWork());

        // Add 2nd EV hatch -> overclocks to 4A EV = 8192V -> 15/16ths = 7680 EU/t
        long[] hatch2EU = new long[] { 10000L };
        MTEHatchEnergy evHatch2 = createMockEnergyHatch(4, hatch2EU);
        degasser.mEnergyHatches.add(evHatch2);
        assertEquals(7680L, degasser.getRequiredPowerDrawEU());

        // Step degassing with 2 hatches
        assertTrue(degasser.stepDegassing(0.04, 0.05));
        assertEquals(0L, hatch1EU[0]);
        assertEquals(10000L - (7680L - 3080L), hatch2EU[0]); // 10000 - 4600 = 5400
        assertEquals(7680L, degasser.getLastDrainedEU());

        // Now drop power below 7680 EU total: hatch1 = 0, hatch2 = 2000
        hatch2EU[0] = 2000L;
        assertFalse(degasser.stepDegassing(0.04, 0.05));

        // Verify atomic behavior and standard GT5U powerfail
        assertFalse(degasser.isAllowedToWork());
        assertEquals(2000L, hatch2EU[0], "Atomic drain: buffer must not be modified");
        assertEquals(0L, degasser.getLastDrainedEU());
        Mockito.verify(mockTracker, Mockito.times(1))
            .createPowerfailEvent(mockBase);
    }

    // =========================================================================
    // 5. Pump Electrical Consumption and Mechanical Power Reporting
    // =========================================================================

    @Test
    public void testPumpElectricalAndMechanicalPowerReporting() {
        boolean[] allowed = new boolean[] { true };
        MTECoolantPump pump = new MTECoolantPump("pump_power_reporting");
        IGregTechTileEntity mockBase = createMockBaseMetaTileEntity(allowed);
        pump.setBaseMetaTileEntity(mockBase);

        // 1 EV hatch (tier 4 -> 2048 EU/t, 1 amp)
        long[] hatchEU = new long[] { 50000L };
        pump.mEnergyHatches.add(createMockEnergyHatch(4, hatchEU));
        assertEquals(2048L, pump.getRequiredPowerDrawEU());

        // 1. Stopped / Empty
        pump.setLoopState(MTECoolantPump.LoopState.STOPPED);
        assertEquals(0L, pump.getPowerConsumptionEU(), "Stopped pump must report 0 EU/t");
        assertEquals(0.0, pump.getPumpMechanicalPowerWatts(), 1e-4, "Stopped pump must report 0 mechanical watts");

        // 2. Filling state
        pump.setLoopState(MTECoolantPump.LoopState.FILLING);
        assertEquals(2048L, pump.getPowerConsumptionEU(), "Filling pump must report full power draw (2048 EU/t)");
        assertEquals(0.0, pump.getPumpMechanicalPowerWatts(), 1e-4, "Filling pump has 0 mechanical watts");

        // 3. Circulating state
        pump.setLoopState(MTECoolantPump.LoopState.CIRCULATING);
        assertEquals(2048L, pump.getPowerConsumptionEU(), "Circulating pump must report 2048 EU/t");
        pump.getEngine()
            .setPumpMechanicalPowerWatts(139264.0);
        assertEquals(
            139264.0,
            pump.getPumpMechanicalPowerWatts(),
            1e-4,
            "Pump must report engine produced mechanical watts");

        // 4. Decelerating with flow > 0 (Phase A active braking)
        pump.setLoopState(MTECoolantPump.LoopState.DECELERATING);
        pump.getEngine()
            .setVolumetricFlowRate(0.05); // 50 L/s > 1e-5
        assertEquals(2048L, pump.getPowerConsumptionEU(), "Active deceleration with flow must report 2048 EU/t");

        // 5. Decelerating with flow == 0 (Phase B fluid draining to reservoir)
        pump.getEngine()
            .setVolumetricFlowRate(0.0);
        assertEquals(0L, pump.getPowerConsumptionEU(), "Deceleration phase B without flow must report 0 EU/t");

        // 6. Overclocked pump with 2 EV hatches (4 amps -> 8192 EU/t)
        long[] hatch2EU = new long[] { 50000L };
        pump.mEnergyHatches.add(createMockEnergyHatch(4, hatch2EU));
        assertEquals(8192L, pump.getRequiredPowerDrawEU());

        pump.setLoopState(MTECoolantPump.LoopState.CIRCULATING);
        assertEquals(8192L, pump.getPowerConsumptionEU(), "Circulating pump with 2 hatches must report 8192 EU/t");

        pump.setLoopState(MTECoolantPump.LoopState.FILLING);
        assertEquals(8192L, pump.getPowerConsumptionEU(), "Filling pump with 2 hatches must report 8192 EU/t");

        // 7. Allowed to work = false disables power consumption
        allowed[0] = false;
        assertEquals(0L, pump.getPowerConsumptionEU(), "Disabled pump must report 0 EU/t");
    }

    private IGregTechTileEntity createMockHatchBase(MTEHatchPressurizedFluid hatch) {
        IGregTechTileEntity mockBase = Mockito.mock(IGregTechTileEntity.class);
        Mockito.when(mockBase.getMetaTileEntity())
            .thenReturn(hatch);
        hatch.setBaseMetaTileEntity(mockBase);
        return mockBase;
    }
}
