package com.gtnewhorizons.coolantloops;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.gtnewhorizons.coolantloops.common.metatileentity.multi.MTECoolantBath;
import com.gtnewhorizons.coolantloops.common.metatileentity.multi.util.CoolantBathSlot;
import com.gtnewhorizons.coolantloops.engine.ConvectiveHeatTransferModel;
import com.gtnewhorizons.coolantloops.engine.CoolantFluidProperty;
import com.gtnewhorizons.coolantloops.engine.LoopSegment;

/**
 * Test suite verifying:
 * 1. Coolant Bath slot auto-ejection rules for coolant cells and ingots.
 * 2. Convective thermal transfer physics (Newton's cooling & heating law).
 * 3. Flow resistance (minor loss K-factor) dynamic scaling: K = 1.0 + 0.1 * N_active.
 * 4. Structure dimensions and slot counts across glass tiers (EV, IV, LuV).
 * 5. NBT serialization and state restoration.
 */
public class CoolantBathTest {

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
        } catch (Throwable ignored) {}

        try {
            net.minecraft.init.Bootstrap.func_151354_b();
        } catch (Throwable ignored) {}

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
    }

    @Test
    public void testCoolantCellAutoEjectionRules() {
        ItemStack cellItem = new ItemStack(Items.iron_ingot); // Mock item representing cell
        int maxDmg = 10000;

        // Case 1: Fully-cold insertion (initialDamage = 0 or 1).
        // Must NOT eject immediately at insertion.
        // Must ONLY eject when reaching fully-hot (maxDamage).
        cellItem.setItemDamage(0);
        CoolantBathSlot coldInserted = new CoolantBathSlot(
            cellItem.copy(),
            20.0,
            1000.0,
            0,
            maxDmg,
            10000.0,
            0.25,
            true,
            false,
            false,
            null);

        assertFalse(coldInserted.isAutoEjectReady(), "Cold inserted cell must not eject at insertion state");

        // Intermediate damage: should NOT eject
        coldInserted.itemStack.setItemDamage(5000);
        assertFalse(coldInserted.isAutoEjectReady(), "Cold inserted cell must not eject at intermediate damage");

        // Fully hot: MUST eject
        coldInserted.itemStack.setItemDamage(maxDmg);
        assertTrue(coldInserted.isAutoEjectReady(), "Cold inserted cell must eject when reaching maximum heat");

        // Case 2: Fully-hot insertion (initialDamage = 10000).
        // Must NOT eject immediately at insertion.
        // Must ONLY eject when reaching fully-cold (<= 1 damage).
        cellItem.setItemDamage(maxDmg);
        CoolantBathSlot hotInserted = new CoolantBathSlot(
            cellItem.copy(),
            1000.0,
            20.0,
            maxDmg,
            maxDmg,
            10000.0,
            0.25,
            true,
            false,
            true,
            null);

        assertFalse(hotInserted.isAutoEjectReady(), "Hot inserted cell must not eject at insertion state");

        // Intermediate damage: should NOT eject
        hotInserted.itemStack.setItemDamage(4000);
        assertFalse(hotInserted.isAutoEjectReady(), "Hot inserted cell must not eject at intermediate damage");

        // Fully cold: MUST eject
        hotInserted.itemStack.setItemDamage(1);
        assertTrue(hotInserted.isAutoEjectReady(), "Hot inserted cell must eject when reaching fully cold state");

        // Case 3: Intermediate insertion (initialDamage = 5000).
        // Ejects at either extreme (cold or hot), but NOT at initial state.
        cellItem.setItemDamage(5000);
        CoolantBathSlot intermediateInserted = new CoolantBathSlot(
            cellItem.copy(),
            500.0,
            20.0,
            5000,
            maxDmg,
            10000.0,
            0.25,
            true,
            false,
            true,
            null);

        assertFalse(intermediateInserted.isAutoEjectReady(), "Intermediate cell must not eject at insertion state");

        intermediateInserted.itemStack.setItemDamage(maxDmg);
        assertTrue(intermediateInserted.isAutoEjectReady(), "Intermediate cell must eject when fully hot");

        intermediateInserted.itemStack.setItemDamage(1);
        assertTrue(intermediateInserted.isAutoEjectReady(), "Intermediate cell must eject when fully cold");
    }

    @Test
    public void testIngotCoolingPhysicsAndAutoEjection() {
        ItemStack hotIngot = new ItemStack(Items.gold_ingot);
        ItemStack coldIngot = new ItemStack(Items.iron_ingot);

        double initialHotTemp = 1500.0;
        double targetColdTemp = 20.0;
        double heatCapacity = 1000.0; // J/K
        double surfaceArea = 0.15; // m^2

        CoolantBathSlot slot = new CoolantBathSlot(
            hotIngot.copy(),
            initialHotTemp,
            targetColdTemp,
            0,
            1000,
            heatCapacity,
            surfaceArea,
            false,
            true,
            true,
            coldIngot.copy());

        assertEquals(0.0, slot.getProgressFraction(), 1e-4, "Progress should initially be 0.0");
        assertFalse(slot.isAutoEjectReady(), "Should not auto-eject when hot");

        // Simulate 10 seconds of cooling in 20 C coolant at 2 m/s velocity
        double coolantTemp = 20.0;
        double velocity = 2.0;
        ConvectiveHeatTransferModel conv = ConvectiveHeatTransferModel.DEFAULT;

        for (int tick = 0; tick < 200; tick++) {
            double dt = 0.05; // 1 tick = 0.05s
            double qDotWatts = conv.computeHeatTransferRate(velocity, slot.surfaceArea, slot.currentTemp, coolantTemp);
            double deltaEJoules = qDotWatts * dt;
            slot.currentTemp -= deltaEJoules / slot.heatCapacity;
            slot.updateItemDamage();
        }

        assertTrue(slot.currentTemp < initialHotTemp, "Temperature must have decreased");
        assertTrue(slot.getProgressFraction() > 0.0, "Progress must have increased");

        // Force cool to room temperature (<= 22 C)
        slot.currentTemp = 21.0;
        slot.updateItemDamage();
        assertTrue(slot.isAutoEjectReady(), "Must be ready to auto-eject once cooled down to target temperature");
        assertEquals(
            coldIngot.getItem(),
            slot.getCompletedOutput()
                .getItem(),
            "Must output the cooled ingot");
    }

    @Test
    public void testIngotHeatingPhysicsAndAutoEjection() {
        ItemStack coldIngot = new ItemStack(Items.iron_ingot);
        ItemStack hotIngot = new ItemStack(Items.gold_ingot);

        double initialColdTemp = 20.0;
        double targetHotTemp = 1200.0;
        double heatCapacity = 800.0; // J/K
        double surfaceArea = 0.15; // m^2

        CoolantBathSlot slot = new CoolantBathSlot(
            coldIngot.copy(),
            initialColdTemp,
            targetHotTemp,
            0,
            1000,
            heatCapacity,
            surfaceArea,
            false,
            true,
            false,
            hotIngot.copy());

        assertEquals(0.0, slot.getProgressFraction(), 1e-4);
        assertFalse(slot.isAutoEjectReady());

        // Heat up to near target
        slot.currentTemp = targetHotTemp - 1.0;
        slot.updateItemDamage();
        assertTrue(slot.isAutoEjectReady(), "Must auto-eject once heated up to target blast furnace temperature");
        assertEquals(
            hotIngot.getItem(),
            slot.getCompletedOutput()
                .getItem(),
            "Must output the heated hot ingot");
    }

    @Test
    public void testSlotNBTSerialization() {
        ItemStack stack = new ItemStack(Items.diamond, 1, 5);
        ItemStack outStack = new ItemStack(Items.emerald, 1, 0);

        CoolantBathSlot original = new CoolantBathSlot(
            stack,
            850.5,
            20.0,
            10,
            1000,
            1250.0,
            0.2,
            false,
            true,
            true,
            outStack);

        NBTTagCompound tag = original.writeToNBT();
        assertNotNull(tag);

        CoolantBathSlot restored = CoolantBathSlot.readFromNBT(tag);
        assertNotNull(restored);
        assertEquals(original.currentTemp, restored.currentTemp, 1e-4);
        assertEquals(original.targetTemp, restored.targetTemp, 1e-4);
        assertEquals(original.startTemp, restored.startTemp, 1e-4);
        assertEquals(original.initialDamage, restored.initialDamage);
        assertEquals(original.maxDamage, restored.maxDamage);
        assertEquals(original.heatCapacity, restored.heatCapacity, 1e-4);
        assertEquals(original.surfaceArea, restored.surfaceArea, 1e-4);
        assertEquals(original.isCoolantCell, restored.isCoolantCell);
        assertEquals(original.isIngot, restored.isIngot);
        assertEquals(original.cooling, restored.cooling);
        assertNotNull(restored.transformedOutput);
        assertEquals(Items.emerald, restored.transformedOutput.getItem());
    }

    @Test
    public void testDynamicMinorLossKFactor() {
        MTECoolantBath bath = new MTECoolantBath("test.bath");
        bath.mSlots = new CoolantBathSlot[15];

        // 0 active items -> base K = 1.0
        assertEquals(1.0, bath.getMinorLossK(), 1e-4, "Base K with 0 active items must be 1.0");

        // Add 3 active items -> K = 1.0 + 0.1 * 3 = 1.3
        bath.mSlots[0] = new CoolantBathSlot(
            new ItemStack(Items.iron_ingot),
            1000,
            20,
            0,
            1000,
            500,
            0.1,
            false,
            true,
            true,
            null);
        bath.mSlots[1] = new CoolantBathSlot(
            new ItemStack(Items.iron_ingot),
            1000,
            20,
            0,
            1000,
            500,
            0.1,
            false,
            true,
            true,
            null);
        bath.mSlots[2] = new CoolantBathSlot(
            new ItemStack(Items.iron_ingot),
            1000,
            20,
            0,
            1000,
            500,
            0.1,
            false,
            true,
            true,
            null);

        assertEquals(3, bath.getActiveItemCount());
        assertEquals(4.0, bath.getMinorLossK(), 1e-4, "K with 3 active items must be 4.0 (1.0 + 1.0 * 3)");

        // Add 12 more active items -> 15 active items -> K = 1.0 + 1.0 * 15 = 16.0
        for (int i = 3; i < 15; i++) {
            bath.mSlots[i] = new CoolantBathSlot(
                new ItemStack(Items.iron_ingot),
                1000,
                20,
                0,
                1000,
                500,
                0.1,
                false,
                true,
                true,
                null);
        }
        assertEquals(15, bath.getActiveItemCount());
        assertEquals(16.0, bath.getMinorLossK(), 1e-4, "K with 15 active items must be 16.0 (1.0 + 1.0 * 15)");
    }

    @Test
    public void testStructureDimensionsAndSlotCountsByTier() {
        MTECoolantBath bath = new MTECoolantBath("test.bath");

        // EV Glass (Tier 4 / structure tier 1): 5x7x3 footprint -> 3x5 interior = 15 slots
        bath.mStructureTier = 1;
        bath.mInteriorWidth = 3;
        bath.mInteriorLength = 5;
        assertEquals(15, bath.getTotalSlotCount(), "EV glass tier must provide 15 virtual slots (3x5)");
        assertEquals(15000L, bath.getFillVolumeLiters(), "15 slots must have 15,000 L fill volume (1000 L/slot)");

        // IV Glass (Tier 5 / structure tier 2): 7x9x3 footprint -> 5x7 interior = 35 slots
        bath.mStructureTier = 2;
        bath.mInteriorWidth = 5;
        bath.mInteriorLength = 7;
        assertEquals(35, bath.getTotalSlotCount(), "IV glass tier must provide 35 virtual slots (5x7)");
        assertEquals(35000L, bath.getFillVolumeLiters(), "35 slots must have 35,000 L fill volume (1000 L/slot)");

        // LuV Glass (Tier 6 / structure tier 3): 9x11x3 footprint -> 7x9 interior = 63 slots
        bath.mStructureTier = 3;
        bath.mInteriorWidth = 7;
        bath.mInteriorLength = 9;
        assertEquals(63, bath.getTotalSlotCount(), "LuV glass tier must provide 63 virtual slots (7x9)");
        assertEquals(63000L, bath.getFillVolumeLiters(), "63 slots must have 63,000 L fill volume (1000 L/slot)");

        // Custom slot array (e.g. 12 slots)
        bath.mSlots = new CoolantBathSlot[12];
        assertEquals(12, bath.getTotalSlotCount(), "Explicit 12-slot array must report 12 slots");
        assertEquals(12000L, bath.getFillVolumeLiters(), "12 slots must have 12,000 L fill volume");
    }

    @Test
    public void testLoopSegmentCoupling() {
        MTECoolantBath bath = new MTECoolantBath("test.bath");
        bath.mSlots = new CoolantBathSlot[15];

        LoopSegment segment = new LoopSegment("test_bath_seg", 1.0, 0.05, 10.0, 0.0, 20.0, 100.0, 100.0);
        segment.setCurrentTemperatureCelsius(25.0);

        // Process thermal exchange with empty bath
        bath.processThermalExchange(0.01, 1.0, CoolantFluidProperty.DISTILLED_WATER, segment);
        assertEquals(1.0, segment.getMinorLossK(), 1e-4, "Empty bath must set segment minor loss K to 1.0");

        // Add 10 active items to bath -> K = 1.0 + 1.0 * 10 = 11.0
        for (int i = 0; i < 10; i++) {
            bath.mSlots[i] = new CoolantBathSlot(
                new ItemStack(Items.iron_ingot),
                1000,
                20,
                0,
                1000,
                500,
                0.1,
                false,
                true,
                true,
                null);
        }

        bath.processThermalExchange(0.01, 1.0, CoolantFluidProperty.DISTILLED_WATER, segment);
        assertEquals(11.0, segment.getMinorLossK(), 1e-4, "10 active items must set segment minor loss K to 11.0");
    }

    @Test
    public void testCoolantBathYAMLConfiguration() {
        com.gtnewhorizons.coolantloops.common.config.data.CoolantDeviceData devData = new com.gtnewhorizons.coolantloops.common.config.data.CoolantDeviceData();
        devData.getBath().fillVolumePerSlotLiters = 500L;
        devData.getBath().baseMinorLossK = 2.5;
        devData.getBath().itemMinorLossK = 0.5;

        // Verify serialization / deserialization round-trip
        java.util.Map<String, Object> map = devData.toMap();
        com.gtnewhorizons.coolantloops.common.config.data.CoolantDeviceData loaded = com.gtnewhorizons.coolantloops.common.config.data.CoolantDeviceData
            .fromMap(map);
        assertEquals(500L, loaded.getBath().fillVolumePerSlotLiters);
        assertEquals(2.5, loaded.getBath().baseMinorLossK, 1e-4);
        assertEquals(0.5, loaded.getBath().itemMinorLossK, 1e-4);

        // Set as active device data
        com.gtnewhorizons.coolantloops.common.config.CoolantLoopDataRegistry.setDeviceData(loaded);
        try {
            MTECoolantBath bath = new MTECoolantBath("test.bath");
            bath.mSlots = new CoolantBathSlot[12];
            assertEquals(
                12 * 500L,
                bath.getFillVolumeLiters(),
                "12 slots with 500 L/slot must have 6,000 L fill volume");

            // Empty bath
            assertEquals(2.5, bath.getMinorLossK(), 1e-4, "Base K must be 2.5 from config");

            // 3 active items -> 2.5 + 0.5 * 3 = 4.0
            bath.mSlots[0] = new CoolantBathSlot(
                new ItemStack(Items.iron_ingot),
                1000,
                20,
                0,
                1000,
                500,
                0.1,
                false,
                true,
                true,
                null);
            bath.mSlots[1] = new CoolantBathSlot(
                new ItemStack(Items.iron_ingot),
                1000,
                20,
                0,
                1000,
                500,
                0.1,
                false,
                true,
                true,
                null);
            bath.mSlots[2] = new CoolantBathSlot(
                new ItemStack(Items.iron_ingot),
                1000,
                20,
                0,
                1000,
                500,
                0.1,
                false,
                true,
                true,
                null);
            assertEquals(4.0, bath.getMinorLossK(), 1e-4, "3 active items must give K = 2.5 + 0.5 * 3 = 4.0");
        } finally {
            // Restore default
            com.gtnewhorizons.coolantloops.common.config.CoolantLoopDataRegistry
                .setDeviceData(new com.gtnewhorizons.coolantloops.common.config.data.CoolantDeviceData());
        }
    }

    @Test
    public void testCryogenicCellLatentHeatCoolingAndAutoEjection() {
        ItemStack liquidNitrogenCell = new ItemStack(Items.apple);
        ItemStack nitrogenGasCell = new ItemStack(Items.baked_potato);

        double boilingPoint = -196.0;
        double latentHeat = 160000.0; // 160 kJ
        double surfaceArea = 0.25;

        CoolantBathSlot cryoSlot = new CoolantBathSlot(
            liquidNitrogenCell.copy(),
            boilingPoint,
            boilingPoint,
            0,
            1000,
            latentHeat,
            surfaceArea,
            false,
            false,
            false,
            nitrogenGasCell.copy(),
            true);

        assertTrue(cryoSlot.isCryoCell);
        assertFalse(cryoSlot.isAutoEjectReady(), "Cryo cell must not be ready to eject initially");
        assertEquals(0.0, cryoSlot.getProgressFraction(), 1e-4);

        // Put into bath
        MTECoolantBath bath = new MTECoolantBath("test.bath");
        bath.mSlots = new CoolantBathSlot[15];
        bath.mSlots[0] = cryoSlot;

        // Loop segment with IC2 coolant at 20 C
        LoopSegment segment = new LoopSegment("cryo_seg", 1.0, 0.05, 0.00005, 0.0, 100.0, 1200.0, 20.0);
        double flowRate = 0.02; // 20 L/s

        // Process thermal exchange for several ticks
        double prevCoolantTemp = segment.getCurrentTemperatureCelsius();
        bath.processThermalExchange(flowRate, 1.0, CoolantFluidProperty.IC2_COOLANT, segment);

        assertTrue(
            segment.getCurrentTemperatureCelsius() < prevCoolantTemp,
            "Coolant temperature must decrease when exchanging heat with -196 C cryo cell");
        assertTrue(cryoSlot.absorbedHeatJoules > 0.0, "Cryo cell must absorb latent heat");
        assertEquals(
            boilingPoint,
            cryoSlot.currentTemp,
            1e-4,
            "Cryo cell temperature remains at boiling point during phase change");

        // Manually absorb full latent heat (160 kJ)
        cryoSlot.absorbedHeatJoules = latentHeat;
        cryoSlot.updateItemDamage();

        assertTrue(
            cryoSlot.isAutoEjectReady(),
            "Cryo cell must be ready to auto-eject once 100% latent heat is absorbed");
        assertEquals(1.0, cryoSlot.getProgressFraction(), 1e-4);
        assertNotNull(cryoSlot.getCompletedOutput());
        assertEquals(
            nitrogenGasCell.getItem(),
            cryoSlot.getCompletedOutput()
                .getItem());
    }

    @Test
    public void testCryoSlotNBTSerialization() {
        ItemStack inCell = new ItemStack(Items.arrow);
        ItemStack outCell = new ItemStack(Items.feather);

        CoolantBathSlot original = new CoolantBathSlot(
            inCell,
            -196.0,
            -196.0,
            0,
            1000,
            160000.0,
            0.25,
            false,
            false,
            false,
            outCell,
            true);
        original.absorbedHeatJoules = 80000.0;
        original.updateItemDamage();

        NBTTagCompound tag = original.writeToNBT();
        assertNotNull(tag);

        CoolantBathSlot restored = CoolantBathSlot.readFromNBT(tag);
        assertNotNull(restored);
        assertTrue(restored.isCryoCell);
        assertEquals(80000.0, restored.absorbedHeatJoules, 1e-4);
        assertEquals(original.currentTemp, restored.currentTemp, 1e-4);
        assertEquals(original.targetTemp, restored.targetTemp, 1e-4);
        assertEquals(original.heatCapacity, restored.heatCapacity, 1e-4);
        assertNotNull(restored.transformedOutput);
        assertEquals(Items.feather, restored.transformedOutput.getItem());
        assertEquals(0.5, restored.getProgressFraction(), 1e-2);
    }
}
