package com.gtnewhorizons.coolantloops;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.gtnewhorizons.coolantloops.common.metatileentity.hatch.MTEHatchPressurizedFluid;
import com.gtnewhorizons.coolantloops.common.metatileentity.multi.MTECoolantPump;
import com.gtnewhorizons.coolantloops.engine.LoopSegment;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * Unit tests verifying intentional non-destructive flow stopping and reservoir drain-back:
 * 1. Machine controller cover placement support on discharge hatch.
 * 2. Active motor deceleration down to zero when discharge hatch is disabled.
 * 3. Coolant drain-back from the loop to the reservoir once flow is stopped.
 * 4. Reservoir capacity limit enforcement ("at least until it's full").
 * 5. Pump disabled immunity ("if the pump is disabled, the cover's state has no effect").
 * 6. Re-enabling the cover refills the loop from reservoir and resumes circulation.
 */
public class CoolantLoopFlowStopTest {

    private MTECoolantPump pump;
    private MTEHatchPressurizedFluid dischargeHatch;
    private MTEHatchPressurizedFluid suctionHatch;
    private IGregTechTileEntity mockPumpBase;
    private IGregTechTileEntity mockDischargeBase;
    private boolean pumpAllowedToWork = true;
    private boolean dischargeAllowedToWork = true;

    @BeforeAll
    static void initEnvironment() {
        Thread.currentThread()
            .setName("Server thread");
        try {
            cpw.mods.fml.common.Loader mockLoader = Mockito.mock(cpw.mods.fml.common.Loader.class);
            Mockito.when(mockLoader.getCallableCrashInformation())
                .thenReturn(Mockito.mock(cpw.mods.fml.common.ICrashCallable.class));
            java.lang.reflect.Field f = cpw.mods.fml.common.Loader.class.getDeclaredField("instance");
            f.setAccessible(true);
            if (f.get(null) == null) {
                f.set(null, mockLoader);
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }

        try {
            net.minecraft.init.Bootstrap.func_151354_b();
        } catch (Throwable t) {
            t.printStackTrace();
        }

        try {
            java.lang.reflect.Field mcHome = cpw.mods.fml.relauncher.FMLInjectionData.class
                .getDeclaredField("minecraftHome");
            mcHome.setAccessible(true);
            mcHome.set(null, new java.io.File("."));
        } catch (Throwable ignored) {}

        try {
            if (gregtech.GTMod.proxy == null) {
                gregtech.GTMod.proxy = Mockito.mock(gregtech.common.GTProxy.class);
            }
        } catch (Throwable ignored) {}

        try {
            java.lang.reflect.Field sideField = cpw.mods.fml.relauncher.FMLRelaunchLog.class.getDeclaredField("side");
            sideField.setAccessible(true);
            sideField.set(null, cpw.mods.fml.relauncher.Side.SERVER);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    @BeforeEach
    public void setup() {
        pumpAllowedToWork = true;
        dischargeAllowedToWork = true;

        pump = new MTECoolantPump("test_pump");
        mockPumpBase = Mockito.mock(IGregTechTileEntity.class);
        Mockito.when(mockPumpBase.isAllowedToWork())
            .thenAnswer(inv -> pumpAllowedToWork);
        Mockito.when(mockPumpBase.isServerSide())
            .thenReturn(true);
        Mockito.when(mockPumpBase.getStoredEU())
            .thenReturn(1000000L);
        Mockito.when(mockPumpBase.decreaseStoredEnergyUnits(Mockito.anyLong(), Mockito.anyBoolean()))
            .thenReturn(true);
        Mockito.when(mockPumpBase.getXCoord())
            .thenReturn(0);
        Mockito.when(mockPumpBase.getYCoord())
            .thenReturn((short) 10);
        Mockito.when(mockPumpBase.getZCoord())
            .thenReturn(0);
        pump.setBaseMetaTileEntity(mockPumpBase);

        dischargeHatch = new MTEHatchPressurizedFluid("discharge", 1, new String[0], null);
        mockDischargeBase = Mockito.mock(IGregTechTileEntity.class);
        Mockito.when(mockDischargeBase.isAllowedToWork())
            .thenAnswer(inv -> dischargeAllowedToWork);
        dischargeHatch.setBaseMetaTileEntity(mockDischargeBase);

        suctionHatch = new MTEHatchPressurizedFluid("suction", 1, new String[0], null);

        pump.mDischargeHatch = dischargeHatch;
        pump.mSuctionHatch = suctionHatch;

        // Add loop segment and mark loop formed
        LoopSegment seg = new LoopSegment("seg_0", 10.0, 0.1, 0.00005, 0.5, 100.0, 1000.0, 20.0);
        pump.getEngine()
            .addSegment(seg);
        pump.setRequiredFillLiters(5000L);
        pump.setCurrentFillLiters(5000L);
        pump.setFillRateLitersPerTick(1000L);
        pump.setSimulatedCoolantLiters(50000L);
        pump.setSimulatedTankCapacityLiters(100000L);
        pump.setLoopState(MTECoolantPump.LoopState.CIRCULATING);
        setLoopFormed(pump, true);
    }

    private void setLoopFormed(MTECoolantPump pump, boolean formed) {
        try {
            java.lang.reflect.Field f = MTECoolantPump.class.getDeclaredField("mLoopFormed");
            f.setAccessible(true);
            f.set(pump, formed);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testCoverSupportOnPressurizedHatch() {
        // Must allow covers (including machine controller) on all sides
        for (ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
            assertTrue(dischargeHatch.allowCoverOnSide(dir, null));
        }

        // Test working state toggling
        dischargeAllowedToWork = false;
        assertFalse(dischargeHatch.isAllowedToWork());
        assertTrue(pump.isDischargeHatchDisabled());

        dischargeAllowedToWork = true;
        assertTrue(dischargeHatch.isAllowedToWork());
        assertFalse(pump.isDischargeHatchDisabled());

        // Test NBT persistence of mWorks
        NBTTagCompound nbt = new NBTTagCompound();
        dischargeHatch.disableWorking();
        dischargeHatch.saveNBTData(nbt);
        assertFalse(nbt.getBoolean("mWorks"));

        MTEHatchPressurizedFluid loaded = new MTEHatchPressurizedFluid("loaded", 1, new String[0], null);
        loaded.loadNBTData(nbt);
        assertFalse(loaded.isAllowedToWork());
    }

    @Test
    public void testActiveDecelerationOnDischargeHatchDisable() {
        // Initial state: steady-state circulation at 0.05 m^3/s (50 L/s)
        pump.getEngine()
            .setVolumetricFlowRate(0.05);
        assertEquals(
            0.05,
            pump.getEngine()
                .getVolumetricFlowRate(),
            1e-6);

        // Machine controller cover on output hatch disables the hatch
        dischargeAllowedToWork = false;
        assertTrue(pump.isDischargeHatchDisabled());

        // Step 1: Pump enters DECELERATING state and applies active motor braking
        pump.stepCoolantLoop();
        assertEquals(MTECoolantPump.LoopState.DECELERATING, pump.getLoopState());
        assertTrue(
            pump.getLoopStatus()
                .contains("Actively decelerating flow"));

        // Step ticks until fluid is brought to rest
        int ticks = 0;
        while (pump.getEngine()
            .getVolumetricFlowRate() > 0.0 && ticks < 20) {
            pump.stepCoolantLoop();
            ticks++;
        }

        // Fluid actively decelerated down to zero
        assertEquals(
            0.0,
            pump.getEngine()
                .getVolumetricFlowRate(),
            1e-6);
        assertFalse(
            pump.getEngine()
                .isBraking());
        assertTrue(ticks <= 5, "Active electric braking must stop flow in 5 ticks or fewer (took " + ticks + ")");
    }

    @Test
    public void testLoopFluidDrainBackToReservoir() {
        // Flow is at zero
        pump.getEngine()
            .setVolumetricFlowRate(0.0);
        dischargeAllowedToWork = false;
        assertEquals(5000L, pump.getCurrentFillLiters());
        assertEquals(50000L, pump.getSimulatedCoolantLiters());

        // Step 1: Transitions to DRAINING and returns fillRate (1000 L) to reservoir
        pump.stepCoolantLoop();
        assertEquals(MTECoolantPump.LoopState.DRAINING, pump.getLoopState());
        assertEquals(4000L, pump.getCurrentFillLiters());
        assertEquals(51000L, pump.getSimulatedCoolantLiters());

        // Step 4 more ticks: drains remaining 4000 L
        for (int i = 0; i < 4; i++) {
            pump.stepCoolantLoop();
        }

        // Loop is now completely empty, all 5000 L returned to reservoir
        assertEquals(0L, pump.getCurrentFillLiters());
        assertEquals(MTECoolantPump.LoopState.EMPTY, pump.getLoopState());
        assertEquals(55000L, pump.getSimulatedCoolantLiters());
        assertTrue(
            pump.getLoopStatus()
                .contains("Loop drained completely back to reservoir"));
    }

    @Test
    public void testReservoirFullPausesDraining() {
        // Reservoir has capacity of 52,000 L, starting with 50,000 L (only 2,000 L space)
        pump.setSimulatedTankCapacityLiters(52000L);
        pump.setSimulatedCoolantLiters(50000L);
        pump.setCurrentFillLiters(5000L);
        pump.getEngine()
            .setVolumetricFlowRate(0.0);
        dischargeAllowedToWork = false;

        // Tick 1: Drains 1000 L -> Reservoir: 51,000 L, Loop: 4000 L
        pump.stepCoolantLoop();
        assertEquals(MTECoolantPump.LoopState.DRAINING, pump.getLoopState());
        assertEquals(4000L, pump.getCurrentFillLiters());
        assertEquals(51000L, pump.getSimulatedCoolantLiters());

        // Tick 2: Drains 1000 L -> Reservoir: 52,000 L (FULL!), Loop: 3000 L
        pump.stepCoolantLoop();
        assertEquals(3000L, pump.getCurrentFillLiters());
        assertEquals(52000L, pump.getSimulatedCoolantLiters());
        assertTrue(pump.isReservoirFull());

        // Tick 3: Reservoir is full; draining pauses in STOPPED state without fluid loss
        pump.stepCoolantLoop();
        assertEquals(MTECoolantPump.LoopState.STOPPED, pump.getLoopState());
        assertEquals(3000L, pump.getCurrentFillLiters());
        assertEquals(52000L, pump.getSimulatedCoolantLiters());
        assertTrue(
            pump.getLoopStatus()
                .contains("Reservoir full"));
    }

    @Test
    public void testPumpDisabledHasNoEffect() {
        // When the pump itself is disabled, the cover's state has no effect
        pumpAllowedToWork = false;
        dischargeAllowedToWork = false; // Cover set to disable hatch

        pump.getEngine()
            .setVolumetricFlowRate(0.05);
        pump.setCurrentFillLiters(5000L);
        long initialReservoir = pump.getSimulatedCoolantLiters();

        // Step loop
        pump.stepCoolantLoop();

        // Must NOT actively brake
        assertFalse(
            pump.getEngine()
                .isBraking());
        assertFalse(
            pump.getEngine()
                .isPumpPowered());

        // Must NOT return fluid to reservoir
        assertEquals(5000L, pump.getCurrentFillLiters());
        assertEquals(initialReservoir, pump.getSimulatedCoolantLiters());
        assertTrue(
            pump.getLoopStatus()
                .contains("Pump disabled"));
    }

    @Test
    public void testReEnablingDischargeHatchRefillsAndCirculates() {
        // Start from empty drained state with discharge hatch disabled
        pump.getEngine()
            .setVolumetricFlowRate(0.0);
        pump.setCurrentFillLiters(0L);
        pump.setLoopState(MTECoolantPump.LoopState.EMPTY);
        pump.setSimulatedCoolantLiters(55000L);
        dischargeAllowedToWork = false;

        // Player re-enables discharge hatch via cover or redstone
        dischargeAllowedToWork = true;
        assertFalse(pump.isDischargeHatchDisabled());

        // Tick 1: Transitions to FILLING and draws 1000 L from reservoir
        pump.stepCoolantLoop();
        assertEquals(MTECoolantPump.LoopState.FILLING, pump.getLoopState());
        assertEquals(1000L, pump.getCurrentFillLiters());
        assertEquals(54000L, pump.getSimulatedCoolantLiters());

        // Ticks 2..5: Fill remaining 4000 L
        for (int i = 0; i < 4; i++) {
            pump.stepCoolantLoop();
        }

        // Loop filled, transitions to CIRCULATING and begins active flow
        assertEquals(5000L, pump.getCurrentFillLiters());
        assertEquals(MTECoolantPump.LoopState.CIRCULATING, pump.getLoopState());
        assertEquals(50000L, pump.getSimulatedCoolantLiters());
        assertTrue(
            pump.getLoopStatus()
                .contains("Beginning circulation"));
    }

    @Test
    public void testPumpUnpoweredProducesZeroFlowAndPower() {
        // Cut off power from base TE
        Mockito.when(mockPumpBase.decreaseStoredEnergyUnits(Mockito.anyLong(), Mockito.anyBoolean()))
            .thenReturn(false);
        Mockito.when(mockPumpBase.getStoredEU())
            .thenReturn(0L);

        pump.getEngine()
            .setVolumetricFlowRate(0.0);
        pump.stepCoolantLoop();

        assertFalse(
            pump.getEngine()
                .isPumpPowered(),
            "Unpowered pump must not set isPumpPowered to true");
        assertEquals(
            0.0,
            pump.getEngine()
                .getPumpMechanicalPowerWatts(),
            1e-6,
            "Unpowered pump mechanical power must be 0");
        assertEquals(
            0.0,
            pump.getEngine()
                .getVolumetricFlowRate(),
            1e-6,
            "Flow rate must remain 0 when pump is unpowered");
        assertEquals(MTECoolantPump.LoopState.STOPPED, pump.getLoopState());
        assertTrue(
            pump.getLoopStatus()
                .contains("Pump unpowered"),
            "Status must indicate pump is unpowered");
    }
}
