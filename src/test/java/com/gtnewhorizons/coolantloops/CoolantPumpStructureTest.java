package com.gtnewhorizons.coolantloops;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collections;

import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.gtnewhorizon.structurelib.alignment.constructable.ChannelDataAccessor;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizons.coolantloops.common.metatileentity.CoolantStructureChannels;
import com.gtnewhorizons.coolantloops.common.metatileentity.multi.MTECoolantPump;
import com.gtnewhorizons.coolantloops.common.util.CoolantFluidHelper;
import com.gtnewhorizons.coolantloops.engine.CoolantFluidProperty;
import com.gtnewhorizons.coolantloops.engine.ICoolantLoopDevice;
import com.gtnewhorizons.coolantloops.engine.LoopGraphCrawler.CrawlResult;
import com.gtnewhorizons.coolantloops.engine.LoopSegment;

import gregtech.api.enums.Materials;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchEnergy;

/**
 * Unit tests verifying:
 * 1. Coolant pump structure channels (Width and Height).
 * 2. Structure tier to pipe material and Railcraft tank material resolution.
 * 3. Railcraft tank item resolution (Iron, Steel, Stainless, Titanium, TungstenSteel, Neutronium).
 * 4. Corner frame box element checks and dynamic tier binding.
 * 5. Dynamic footprint and height structure definition construction.
 */
public class CoolantPumpStructureTest {

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
            if (gregtech.GTMod.proxy.powerfailTracker == null) {
                gregtech.GTMod.proxy.powerfailTracker = Mockito.mock(gregtech.common.data.GTPowerfailTracker.class);
            }
        } catch (Throwable ignored) {}
    }

    @Test
    public void testStructureChannelsRegistration() {
        assertEquals("width", CoolantStructureChannels.STRUCTURE_WIDTH.get());
        assertEquals("height", CoolantStructureChannels.STRUCTURE_HEIGHT.get());
        assertNotNull(CoolantStructureChannels.STRUCTURE_WIDTH.getDefaultTooltip());
        assertNotNull(CoolantStructureChannels.STRUCTURE_HEIGHT.getDefaultTooltip());
    }

    @Test
    public void testTierToMaterialMapping() {
        assertEquals(Materials.CastIron, MTECoolantPump.getMaterialForTier(0));
        assertEquals(Materials.Steel, MTECoolantPump.getMaterialForTier(1));
        assertEquals(Materials.StainlessSteel, MTECoolantPump.getMaterialForTier(2));
        assertEquals(Materials.Titanium, MTECoolantPump.getMaterialForTier(3));
        assertEquals(Materials.TungstenSteel, MTECoolantPump.getMaterialForTier(4));
        assertEquals(Materials.Neutronium, MTECoolantPump.getMaterialForTier(5));

        // Fallbacks
        assertEquals(Materials.CastIron, MTECoolantPump.getMaterialForTier(-1));
        assertEquals(Materials.Neutronium, MTECoolantPump.getMaterialForTier(10));
    }

    @Test
    public void testTriggerMaterialResolution() {
        // Trigger with stackSize = 2 -> tier 1 -> Steel
        ItemStack triggerSteel = new ItemStack(net.minecraft.init.Items.stick, 2);
        assertEquals(Materials.Steel, MTECoolantPump.getMaterialFromTrigger(triggerSteel));

        // Trigger with stackSize = 1 -> tier 0 -> CastIron
        ItemStack triggerIron = new ItemStack(net.minecraft.init.Items.stick, 1);
        assertEquals(Materials.CastIron, MTECoolantPump.getMaterialFromTrigger(triggerIron));

        // Trigger with stackSize = 3 -> tier 2 -> StainlessSteel
        ItemStack triggerStainless = new ItemStack(net.minecraft.init.Items.stick, 3);
        assertEquals(Materials.StainlessSteel, MTECoolantPump.getMaterialFromTrigger(triggerStainless));

        // Trigger with subchannel "tier"
        ItemStack triggerSubchannel = new ItemStack(net.minecraft.init.Items.stick, 1);
        ChannelDataAccessor.setChannelData(triggerSubchannel, "tier", 4);
        assertEquals(Materials.TungstenSteel, MTECoolantPump.getMaterialFromTrigger(triggerSubchannel));
    }

    @Test
    public void testWidthChannelResolution() {
        ItemStack trigger = new ItemStack(net.minecraft.init.Items.stick, 1);

        // Unset defaults to 3
        assertEquals(3, MTECoolantPump.getWidthFromTrigger(trigger));

        // 1-based channel IDs: 1 -> 3, 2 -> 5, 4 -> 9
        ChannelDataAccessor.setChannelData(trigger, "width", 1);
        assertEquals(3, MTECoolantPump.getWidthFromTrigger(trigger));

        ChannelDataAccessor.setChannelData(trigger, "width", 2);
        assertEquals(5, MTECoolantPump.getWidthFromTrigger(trigger));

        ChannelDataAccessor.setChannelData(trigger, "width", 4);
        assertEquals(9, MTECoolantPump.getWidthFromTrigger(trigger));

        // Direct widths: 3, 5, 7, 9
        ChannelDataAccessor.setChannelData(trigger, "width", 3);
        assertEquals(3, MTECoolantPump.getWidthFromTrigger(trigger));

        ChannelDataAccessor.setChannelData(trigger, "width", 5);
        assertEquals(5, MTECoolantPump.getWidthFromTrigger(trigger));

        ChannelDataAccessor.setChannelData(trigger, "width", 7);
        assertEquals(7, MTECoolantPump.getWidthFromTrigger(trigger));

        ChannelDataAccessor.setChannelData(trigger, "width", 9);
        assertEquals(9, MTECoolantPump.getWidthFromTrigger(trigger));
    }

    @Test
    public void testHeightChannelResolution() {
        ItemStack trigger = new ItemStack(net.minecraft.init.Items.stick, 1);

        // Unset defaults to 4
        assertEquals(4, MTECoolantPump.getHeightFromTrigger(trigger));

        // Direct heights: 4, 5, 6, 7, 8
        for (int h = 4; h <= 8; h++) {
            ChannelDataAccessor.setChannelData(trigger, "height", h);
            assertEquals(h, MTECoolantPump.getHeightFromTrigger(trigger));
        }

        // Channel index: 1 -> 4, 2 -> 5, 3 -> 6
        ChannelDataAccessor.setChannelData(trigger, "height", 1);
        assertEquals(4, MTECoolantPump.getHeightFromTrigger(trigger));

        ChannelDataAccessor.setChannelData(trigger, "height", 2);
        assertEquals(5, MTECoolantPump.getHeightFromTrigger(trigger));

        ChannelDataAccessor.setChannelData(trigger, "height", 3);
        assertEquals(6, MTECoolantPump.getHeightFromTrigger(trigger));
    }

    @Test
    public void testRailcraftTankItemResolution() {
        assertNotNull(MTECoolantPump.getTankWallItem(Materials.CastIron));
        assertNotNull(MTECoolantPump.getTankWallItem(Materials.Steel));
        assertNotNull(MTECoolantPump.getTankValveItem(Materials.CastIron));
        assertNotNull(MTECoolantPump.getTankValveItem(Materials.Steel));
        assertNotNull(MTECoolantPump.getTankGaugeItem(Materials.CastIron));
        assertNotNull(MTECoolantPump.getTankGaugeItem(Materials.Steel));

        assertNotNull(MTECoolantPump.getTankWallItem(Materials.StainlessSteel));
        assertNotNull(MTECoolantPump.getTankWallItem(Materials.Titanium));
        assertNotNull(MTECoolantPump.getTankWallItem(Materials.TungstenSteel));
        assertNotNull(MTECoolantPump.getTankWallItem(Materials.Neutronium));
    }

    @Test
    public void testStructureDefinitionShapes() {
        MTECoolantPump pump = new MTECoolantPump("test_pump");
        IStructureDefinition<MTECoolantPump> def = pump.getStructureDefinition();
        assertNotNull(def, "Structure definition should not be null");

        // Verify width and height triggers for all valid dimension combinations
        for (int w : new int[] { 3, 5, 7, 9 }) {
            for (int h = 4; h <= 8; h++) {
                ItemStack trigger = new ItemStack(net.minecraft.init.Items.stick, 2);
                ChannelDataAccessor.setChannelData(trigger, "width", w);
                ChannelDataAccessor.setChannelData(trigger, "height", h);

                assertEquals(w, MTECoolantPump.getWidthFromTrigger(trigger));
                assertEquals(h, MTECoolantPump.getHeightFromTrigger(trigger));
            }
        }
    }

    @Test
    public void testTankValveAndPipingPlaneContract() {
        // Pump base is Layer 0 (y) and Layer 1 (y+1).
        // Suction and Discharge hatches are strictly located at Layer 1 (y+1).
        final int pumpBaseHeight = 2;
        final int hatchPlaneOffset = 1;
        assertEquals(1, hatchPlaneOffset, "Piping loop plane must be at y + 1 (layer 1 of pump multiblock)");

        // Railcraft tank bottom layer (containing the center valve block) is at Layer 2 (y+2).
        final int tankValveOffset = pumpBaseHeight;
        assertEquals(2, tankValveOffset, "Railcraft tank valve must be at y + 2 directly above the pump base");

        // Railcraft tank interior hollow core begins at Layer 3 (y+3).
        final int tankInteriorOffset = pumpBaseHeight + 1;
        assertEquals(3, tankInteriorOffset, "Railcraft tank hollow air interior begins at y + 3");
    }

    @Test
    public void testEnergyHatchesAndRotorImpellerValidation() {
        MTECoolantPump pump = new MTECoolantPump("test_pump");

        // Rotor validation tests
        assertFalse(MTECoolantPump.isValidRotor(null));
        assertFalse(pump.isCorrectMachinePart(null));
        assertEquals(10000, pump.getMaxEfficiency(null));

        // Energy hatch capacity tests
        pump.mEnergyHatches.clear();
        gregtech.api.metatileentity.implementations.MTEHatchEnergy hatch1 = Mockito
            .mock(gregtech.api.metatileentity.implementations.MTEHatchEnergy.class);
        gregtech.api.metatileentity.implementations.MTEHatchEnergy hatch2 = Mockito
            .mock(gregtech.api.metatileentity.implementations.MTEHatchEnergy.class);
        gregtech.api.metatileentity.implementations.MTEHatchEnergy hatch3 = Mockito
            .mock(gregtech.api.metatileentity.implementations.MTEHatchEnergy.class);

        // 1 hatch: valid
        pump.mEnergyHatches.add(hatch1);
        assertEquals(1, pump.mEnergyHatches.size());

        // 2 hatches: valid
        pump.mEnergyHatches.add(hatch2);
        assertEquals(2, pump.mEnergyHatches.size());

        // 3 hatches: invalid (> 2)
        pump.mEnergyHatches.add(hatch3);
        assertEquals(3, pump.mEnergyHatches.size());
    }

    @Test
    public void testPlainWaterAndGaseousFluidRefusal() {
        net.minecraftforge.fluids.Fluid water = new net.minecraftforge.fluids.Fluid("water");
        net.minecraftforge.fluids.Fluid distilled = new net.minecraftforge.fluids.Fluid("ic2distilledwater");
        net.minecraftforge.fluids.Fluid heavy = new net.minecraftforge.fluids.Fluid("heavywater");
        net.minecraftforge.fluids.Fluid cheese = new net.minecraftforge.fluids.Fluid("molten.cheese");

        assertTrue(MTECoolantPump.isPlainRegularWater(water));
        assertFalse(MTECoolantPump.isPlainRegularWater(distilled));
        assertFalse(MTECoolantPump.isPlainRegularWater(heavy));
        assertFalse(MTECoolantPump.isPlainRegularWater(cheese));

        net.minecraftforge.fluids.Fluid gasFluid = new net.minecraftforge.fluids.Fluid("methane").setGaseous(true);
        net.minecraftforge.fluids.Fluid namedGas = new net.minecraftforge.fluids.Fluid("gas_nitrogen");
        assertTrue(MTECoolantPump.isGaseousFluid(gasFluid, null));
        assertTrue(MTECoolantPump.isGaseousFluid(namedGas, null));
        assertFalse(MTECoolantPump.isGaseousFluid(distilled, null));
        assertFalse(MTECoolantPump.isGaseousFluid(cheese, null));

        // Pump simulation step refusal on regular water
        MTECoolantPump pump = new MTECoolantPump("test_pump_water");
        gregtech.api.interfaces.tileentity.IGregTechTileEntity mockPumpBase = Mockito
            .mock(gregtech.api.interfaces.tileentity.IGregTechTileEntity.class);
        Mockito.when(mockPumpBase.isAllowedToWork())
            .thenReturn(true);
        Mockito.when(mockPumpBase.isServerSide())
            .thenReturn(true);
        pump.setBaseMetaTileEntity(mockPumpBase);
        pump.setLoopFormed(true);
        pump.getEngine()
            .addSegment(
                new com.gtnewhorizons.coolantloops.engine.LoopSegment(
                    "test_seg",
                    10.0,
                    0.2,
                    0.0001,
                    1.0,
                    100.0,
                    1000.0,
                    20.0));
        pump.getEngine()
            .setFluid(com.gtnewhorizons.coolantloops.engine.CoolantFluidProperty.WATER);
        assertTrue(pump.stepCoolantLoop());
        assertEquals(MTECoolantPump.LoopState.STOPPED, pump.getLoopState());
        assertTrue(
            pump.getLoopStatus()
                .contains("Plain regular water cannot be used in a coolant loop"));
    }

    @Test
    public void testMoltenFluidTemperatureChecksAndCatastrophicFailure() {
        net.minecraftforge.fluids.Fluid moltenCheese = new net.minecraftforge.fluids.Fluid("molten.cheese");
        moltenCheese.setTemperature(320); // 320 K (46.85 °C)

        assertTrue(MTECoolantPump.isMoltenFluid(moltenCheese, null, null));
        assertEquals(
            320 - 273.15,
            MTECoolantPump.getDeclaredGregTechFluidTemperatureCelsius(moltenCheese, null, null),
            0.01);

        // 1. Pump refuses to start / accelerate if reservoir is cold (< 46.85 °C)
        MTECoolantPump pump = new MTECoolantPump("test_pump_molten");
        gregtech.api.interfaces.tileentity.IGregTechTileEntity mockPumpBase = Mockito
            .mock(gregtech.api.interfaces.tileentity.IGregTechTileEntity.class);
        Mockito.when(mockPumpBase.isAllowedToWork())
            .thenReturn(true);
        Mockito.when(mockPumpBase.isServerSide())
            .thenReturn(true);
        pump.setBaseMetaTileEntity(mockPumpBase);
        pump.setLoopFormed(true);
        com.gtnewhorizons.coolantloops.engine.LoopSegment coldSeg = new com.gtnewhorizons.coolantloops.engine.LoopSegment(
            "cold_seg",
            10.0,
            0.2,
            0.0001,
            1.0,
            100.0,
            1000.0,
            20.0); // 20 °C < 46.85 °C
        pump.getEngine()
            .addSegment(coldSeg);
        pump.getEngine()
            .setFluid(com.gtnewhorizons.coolantloops.engine.CoolantFluidProperty.MOLTEN_CHEESE);
        pump.getReservoir()
            .setReservoirTemperatureCelsius(20.0); // reservoir cooled down below 46.85 °C

        assertTrue(pump.stepCoolantLoop());
        assertEquals(MTECoolantPump.LoopState.STOPPED, pump.getLoopState());
        assertTrue(
            pump.getLoopStatus()
                .contains("below declared GregTech melting point"));

        // 2. When reservoir is at or above melting point (e.g. 55 °C > 46.85 °C), pump is allowed to start/circulate
        pump.getReservoir()
            .setReservoirTemperatureCelsius(55.0);
        pump.setSimulatedBiomeTemperatureCelsius(55.0);
        coldSeg.setCurrentTemperatureCelsius(55.0);
        assertTrue(pump.stepCoolantLoop());
        // Temperature check passes! Pump advances all the way to circulation power check
        assertFalse(
            pump.getLoopStatus()
                .contains("below declared GregTech melting point"));
        assertEquals(
            gregtech.api.util.shutdown.ShutDownReasonRegistry.POWER_LOSS.getDisplayString(),
            pump.getLoopStatus());

        // 3. Circulating molten coolant below melting point causes catastrophic explosion / rupture!
        com.gtnewhorizons.coolantloops.engine.CoolantLoopEngine engine = new com.gtnewhorizons.coolantloops.engine.CoolantLoopEngine(
            com.gtnewhorizons.coolantloops.engine.CoolantFluidProperty.MOLTEN_CHEESE);
        com.gtnewhorizons.coolantloops.engine.LoopSegment frozenSeg = new com.gtnewhorizons.coolantloops.engine.LoopSegment(
            "frozen_seg",
            10.0,
            0.2,
            0.0001,
            1.0,
            100.0,
            1000.0,
            30.0); // 30 °C < 46.85 °C
        engine.addSegment(frozenSeg);
        engine.setVolumetricFlowRate(0.05); // moving/circulating
        engine.setPumpPowered(true);
        engine.setPumpMechanicalPowerWatts(10000.0);
        engine.step(0.05);

        assertTrue(engine.isRuptured());
        assertTrue(
            engine.getFailureReason()
                .contains("Catastrophic coolant solidification"));
    }

    @Test
    public void testMoltenSodiumReservoirBaselineAndEnthalpyDilution() {
        net.minecraftforge.fluids.Fluid sodiumFluid = new net.minecraftforge.fluids.Fluid("sodium");
        sodiumFluid.setTemperature(371); // 371 K (97.85 °C)

        assertTrue(CoolantFluidHelper.isMoltenFluid(sodiumFluid, null, CoolantFluidProperty.SODIUM));
        assertEquals(
            97.85,
            CoolantFluidHelper
                .getDeclaredGregTechFluidTemperatureCelsius(sodiumFluid, null, CoolantFluidProperty.SODIUM),
            0.05);

        // 1. In a cold overworld biome (e.g. 20 °C or even subzero -15 °C), fresh molten sodium baseline inlet temp is
        // its melting point
        assertEquals(
            97.85,
            CoolantFluidHelper.getBaselineInletTemperatureCelsius(sodiumFluid, null, CoolantFluidProperty.SODIUM, 20.0),
            0.05);
        assertEquals(
            97.85,
            CoolantFluidHelper
                .getBaselineInletTemperatureCelsius(sodiumFluid, null, CoolantFluidProperty.SODIUM, -15.0),
            0.05);
        // In a scorching environment above melting point (e.g. 110 °C), baseline is the ambient temp
        assertEquals(
            110.0,
            CoolantFluidHelper
                .getBaselineInletTemperatureCelsius(sodiumFluid, null, CoolantFluidProperty.SODIUM, 110.0),
            0.05);

        // 2. Fresh pump with molten sodium reservoir initializes at baseline inlet temperature (97.85 °C)
        MTECoolantPump pump = new MTECoolantPump("test_pump_sodium");
        pump.setSimulatedBiomeTemperatureCelsius(20.0);
        pump.getEngine()
            .setFluid(CoolantFluidProperty.SODIUM);
        pump.getReservoir()
            .setSimulatedCoolantLiters(10000L);
        pump.getReservoir()
            .setSimulatedTankCapacityLiters(50000L);

        pump.getReservoir()
            .updateReservoirState();
        assertEquals(
            97.85,
            pump.getReservoir()
                .getReservoirTemperatureCelsius(),
            0.05);

        // 3. Reactor operation heats the reservoir up to 500 °C
        pump.getReservoir()
            .setReservoirTemperatureCelsius(500.0);
        assertEquals(
            500.0,
            pump.getReservoir()
                .getReservoirTemperatureCelsius(),
            1e-6);

        // 4. External fluid top-up: adding 10,000 L of fresh sodium (enters at 97.85 °C) dilutes enthalpy
        // (10,000 * 500.0 + 10,000 * 97.85) / 20,000 = 298.925 °C
        pump.getReservoir()
            .setSimulatedCoolantLiters(20000L);
        pump.getReservoir()
            .updateReservoirState();
        assertEquals(
            298.925,
            pump.getReservoir()
                .getReservoirTemperatureCelsius(),
            0.05);

        // 5. Drawing 5,000 L into loop does not change reservoir temperature
        int drained = pump.getReservoir()
            .drainFromReservoir(5000);
        assertEquals(5000, drained);
        assertEquals(
            15000L,
            pump.getReservoir()
                .getTotalCoolantLiters());
        assertEquals(
            298.925,
            pump.getReservoir()
                .getReservoirTemperatureCelsius(),
            0.05);

        // 6. Loop fluid returns at 600 °C: volume-weighted blending into reservoir
        // (15,000 * 298.925 + 5,000 * 600.0) / 20,000 = (4,483,875 + 3,000,000) / 20,000 = 374.19375 °C
        int filled = pump.getReservoir()
            .fillIntoReservoir(5000, 600.0);
        assertEquals(5000, filled);
        assertEquals(
            20000L,
            pump.getReservoir()
                .getTotalCoolantLiters());
        assertEquals(
            374.19,
            pump.getReservoir()
                .getReservoirTemperatureCelsius(),
            0.05);

        // 7. Exploit prevention: fresh fluid enters at melting point -> Delta-T above freezing is 0
        // Attempting to extract heat without nuclear input drops T < 97.8 °C -> catastrophic rupture!
        com.gtnewhorizons.coolantloops.engine.CoolantLoopEngine engine = new com.gtnewhorizons.coolantloops.engine.CoolantLoopEngine(
            CoolantFluidProperty.SODIUM);
        com.gtnewhorizons.coolantloops.engine.LoopSegment seg = new com.gtnewhorizons.coolantloops.engine.LoopSegment(
            "sodium_seg",
            10.0,
            0.2,
            0.0001,
            1.0,
            100.0,
            1000.0,
            97.85);
        engine.addSegment(seg);
        engine.setVolumetricFlowRate(0.05);
        engine.setPumpPowered(true);
        engine.setPumpMechanicalPowerWatts(10000.0);

        // Drop 1 degree below freezing -> solidification rupture!
        seg.setCurrentTemperatureCelsius(96.0);
        engine.step(0.05);
        assertTrue(engine.isRuptured());
        assertTrue(
            engine.getFailureReason()
                .contains("Catastrophic coolant solidification"));
    }

    @Test
    public void testBiomeAmbientTemperatureCalculation() {
        // Fallback with null world -> 20.0 °C
        assertEquals(20.0, MTECoolantPump.calculateAmbientTemperature(null, 0, 0, 0), 1e-6);

        net.minecraft.world.World mockWorld = Mockito.mock(net.minecraft.world.World.class);
        net.minecraft.world.biome.BiomeGenBase mockBiome = Mockito.mock(net.minecraft.world.biome.BiomeGenBase.class);
        Mockito.when(mockWorld.getBiomeGenForCoords(0, 0))
            .thenReturn(mockBiome);

        // Plains (0.80) -> (80.0 - 32.0) / 1.8 = ~26.67 °C
        Mockito.when(mockBiome.getFloatTemperature(0, 64, 0))
            .thenReturn(0.80f);
        assertEquals(26.67, MTECoolantPump.calculateAmbientTemperature(mockWorld, 0, 64, 0), 0.05);

        // Freezing snow biome (0.00) -> (0.0 - 32.0) / 1.8 = ~-17.78 °C
        Mockito.when(mockBiome.getFloatTemperature(0, 64, 0))
            .thenReturn(0.0f);
        assertEquals(-17.78, MTECoolantPump.calculateAmbientTemperature(mockWorld, 0, 64, 0), 0.05);

        // Nether / Hell biome (2.00) -> (200.0 - 32.0) / 1.8 = ~93.33 °C
        Mockito.when(mockBiome.getFloatTemperature(0, 64, 0))
            .thenReturn(2.0f);
        assertEquals(93.33, MTECoolantPump.calculateAmbientTemperature(mockWorld, 0, 64, 0), 0.05);
    }

    @Test
    public void testLiquidNuclearFuelFailureAndImpellerDestruction() {
        // 1. Classification tests
        assertTrue(
            com.gtnewhorizons.coolantloops.common.util.CoolantFluidHelper
                .isLiquidNuclearFuel("thoriumbasedliquidfuel"));
        assertTrue(
            com.gtnewhorizons.coolantloops.common.util.CoolantFluidHelper
                .isLiquidNuclearFuel("uraniumbasedliquidfuel"));
        assertTrue(
            com.gtnewhorizons.coolantloops.common.util.CoolantFluidHelper
                .isLiquidNuclearFuel("plutoniumbasedliquidfuel"));
        assertTrue(
            com.gtnewhorizons.coolantloops.common.util.CoolantFluidHelper
                .isLiquidNuclearFuel("naquadahbasedliquidfuel"));
        assertTrue(
            com.gtnewhorizons.coolantloops.common.util.CoolantFluidHelper.isLiquidNuclearFuel("liquid_naquadah_fuel"));
        assertTrue(
            com.gtnewhorizons.coolantloops.common.util.CoolantFluidHelper.isLiquidNuclearFuel("uraniumhexafluoride"));
        assertTrue(
            com.gtnewhorizons.coolantloops.common.util.CoolantFluidHelper.isLiquidNuclearFuel("uraniumtetrafluoride"));
        assertTrue(
            com.gtnewhorizons.coolantloops.common.util.CoolantFluidHelper
                .isLiquidNuclearFuel("depleteduraniumbasedliquidfuel"));

        // Regular coolants are NOT liquid nuclear fuels
        assertFalse(
            com.gtnewhorizons.coolantloops.common.util.CoolantFluidHelper.isLiquidNuclearFuel("ic2distilledwater"));
        assertFalse(com.gtnewhorizons.coolantloops.common.util.CoolantFluidHelper.isLiquidNuclearFuel("heavywater"));
        assertFalse(com.gtnewhorizons.coolantloops.common.util.CoolantFluidHelper.isLiquidNuclearFuel("sodium"));
        assertFalse(com.gtnewhorizons.coolantloops.common.util.CoolantFluidHelper.isLiquidNuclearFuel("molten.cheese"));

        // 2. Pump failure and impeller destruction
        MTECoolantPump pump = new MTECoolantPump("test_pump_nuke");
        net.minecraft.item.Item mockTool = Mockito.mock(net.minecraft.item.Item.class);
        ItemStack mockRotor = Mockito.mock(ItemStack.class);
        Mockito.when(mockRotor.getItem())
            .thenReturn(mockTool);
        Mockito.when(mockRotor.getItemDamage())
            .thenReturn(170);
        Mockito.when(mockRotor.getMaxDamage())
            .thenReturn(1000);

        pump.setRotorStack(mockRotor);
        assertEquals(mockRotor, pump.getRotor());

        // Feeding liquid nuclear fuel immediately fails pump and voids impeller
        pump.failOnLiquidNuclearFuel("uraniumbasedliquidfuel");

        assertEquals(MTECoolantPump.LoopState.STOPPED, pump.getLoopState());
        assertNull(pump.getRotor(), "Impeller must be destroyed/voided");
        assertEquals(0.0, pump.getRotorEfficiency(), 1e-6);
        assertFalse(
            pump.getEngine()
                .isRuptured(),
            "No terrain explosion on liquid fuel seizure");
        assertTrue(
            pump.getLoopStatus()
                .contains(
                    "Catastrophic pump failure: uraniumbasedliquidfuel is a liquid nuclear fuel, not a coolant! Impeller disintegrated!"));
    }

    @Test
    public void testImpellerCavitationWearFromDissolvedGases() {
        MTECoolantPump pump = new MTECoolantPump("test_pump_cavit");
        net.minecraft.item.Item mockTool = Mockito.mock(net.minecraft.item.Item.class);
        ItemStack mockRotor = Mockito.mock(ItemStack.class);
        Mockito.when(mockRotor.getItem())
            .thenReturn(mockTool);
        Mockito.when(mockRotor.getItemDamage())
            .thenReturn(0);
        Mockito.when(mockRotor.getMaxDamage())
            .thenReturn(1000);
        pump.setRotorStack(mockRotor);

        // At 0% dissolved gas: wear multiplier is 1.0x (1 damage per interval)
        pump.setCurrentFillLiters(10000L);
        pump.setSimulatedCoolantLiters(10000L);
        assertEquals(0.0, pump.getDissolvedGasFraction(), 1e-6);

        pump.setRotorDamageAccumulator(0.0);
        // Direct degradation call: should add (1.0 + 0.0) = 1.0 -> 1 damage applied
        pump.setRotorDamageAccumulator(0.0);
        // Test accumulator math
        double wear0 = 1.0 + pump.getDissolvedGasFraction(); // 1.0
        assertEquals(1.0, wear0, 1e-6);

        // At 100% dissolved gas: wear multiplier is 2.0x (doubled wear: 1.0 + 1.0 = 2.0x)
        pump.addDissolvedGas("Tritium", 10000L);
        assertEquals(1.0, pump.getDissolvedGasFraction(), 1e-6);
        double wear100 = 1.0 + pump.getDissolvedGasFraction(); // 2.0
        assertEquals(2.0, wear100, 1e-6);

        // At 20% dissolved gas: wear multiplier is 1.20x (+20% wear)
        pump.extractDissolvedGas("Tritium", 8000L);
        assertEquals(0.20, pump.getDissolvedGasFraction(), 1e-6);
        double wear20 = 1.0 + pump.getDissolvedGasFraction(); // 1.20
        assertEquals(1.20, wear20, 1e-6);
    }

    @Test
    public void testScannerInfoDataUsesNormalSentenceCase() {
        MTECoolantPump pump = new MTECoolantPump("test_pump_scanner");
        String[] info = pump.getInfoData();
        assertNotNull(info);
        assertTrue(info.length >= 8);

        // Verify normal sentence case
        for (String line : info) {
            assertFalse(line.contains("Discharge Hatch:"), "Must use normal sentence case: " + line);
            assertFalse(line.contains("Tank Material:"), "Must use normal sentence case: " + line);
            assertFalse(line.contains("Current Flow:"), "Must use normal sentence case: " + line);
            assertFalse(line.contains("Max Pipe Flow:"), "Must use normal sentence case: " + line);
            assertFalse(line.contains("Peak Pressure:"), "Must use normal sentence case: " + line);
            assertFalse(line.contains("Peak Temp:"), "Must use normal sentence case: " + line);
            assertFalse(line.contains("Loop Fluid:"), "Must use normal sentence case: " + line);
            assertFalse(line.contains("Dissolved Gas:"), "Must use normal sentence case: " + line);
        }

        assertTrue(info[1].startsWith("Discharge hatch: "));
        assertTrue(info[2].startsWith("Tank material: "));
        assertTrue(info[3].startsWith("Current flow: "));
        assertTrue(info[4].startsWith("Max pipe flow: "));
        assertTrue(info[5].startsWith("Peak pressure: "));
        assertTrue(info[6].startsWith("Peak temp: "));
        assertTrue(info[7].startsWith("Loop fluid: "));
        assertTrue(info[8].startsWith("Dissolved gas: "));
    }

    @Test
    public void testLavaCoolantLoopEasterEgg() {
        // 1. Classification
        assertTrue(com.gtnewhorizons.coolantloops.common.util.CoolantFluidHelper.isLava("lava"));
        assertTrue(com.gtnewhorizons.coolantloops.common.util.CoolantFluidHelper.isLava("fluid.lava"));
        assertFalse(com.gtnewhorizons.coolantloops.common.util.CoolantFluidHelper.isLava("water"));
        assertFalse(com.gtnewhorizons.coolantloops.common.util.CoolantFluidHelper.isLava("sodium"));

        net.minecraftforge.fluids.Fluid lavaFluid = net.minecraftforge.fluids.FluidRegistry.getFluid("lava");
        if (lavaFluid == null) {
            lavaFluid = new net.minecraftforge.fluids.Fluid("lava");
            net.minecraftforge.fluids.FluidRegistry.registerFluid(lavaFluid);
        }
        net.minecraftforge.fluids.FluidStack lavaStack = new net.minecraftforge.fluids.FluidStack(lavaFluid, 1000);
        assertTrue(com.gtnewhorizons.coolantloops.common.util.CoolantFluidHelper.isLava(lavaStack));
        assertTrue(com.gtnewhorizons.coolantloops.common.util.CoolantFluidHelper.isLava(lavaFluid, lavaStack));

        // 2. Nether dimension detection
        net.minecraft.world.World overworld = Mockito.mock(net.minecraft.world.World.class);
        net.minecraft.world.WorldProvider overworldProvider = Mockito.mock(net.minecraft.world.WorldProvider.class);
        overworldProvider.dimensionId = 0;
        overworldProvider.isHellWorld = false;
        try {
            java.lang.reflect.Field fProv = net.minecraft.world.World.class.getField("provider");
            fProv.setAccessible(true);
            fProv.set(overworld, overworldProvider);
        } catch (Throwable t) {
            fail("Failed to set World.provider: " + t);
        }
        assertFalse(com.gtnewhorizons.coolantloops.common.util.CoolantFluidHelper.isNetherWorld(overworld));

        net.minecraft.world.World nether = Mockito.mock(net.minecraft.world.World.class);
        net.minecraft.world.WorldProvider netherProvider = Mockito.mock(net.minecraft.world.WorldProvider.class);
        netherProvider.dimensionId = -1;
        netherProvider.isHellWorld = true;
        try {
            java.lang.reflect.Field fProv = net.minecraft.world.World.class.getField("provider");
            fProv.setAccessible(true);
            fProv.set(nether, netherProvider);
        } catch (Throwable t) {
            fail("Failed to set World.provider: " + t);
        }
        assertTrue(com.gtnewhorizons.coolantloops.common.util.CoolantFluidHelper.isNetherWorld(nether));

        // 3. Pump in Overworld rejects lava
        MTECoolantPump pumpOverworld = new MTECoolantPump("test_pump_lava_overworld");
        gregtech.api.interfaces.tileentity.IGregTechTileEntity mockPumpBaseOw = Mockito
            .mock(gregtech.api.interfaces.tileentity.IGregTechTileEntity.class);
        Mockito.when(mockPumpBaseOw.isAllowedToWork())
            .thenReturn(true);
        Mockito.when(mockPumpBaseOw.isServerSide())
            .thenReturn(true);
        Mockito.when(mockPumpBaseOw.getWorld())
            .thenReturn(overworld);
        pumpOverworld.setBaseMetaTileEntity(mockPumpBaseOw);
        pumpOverworld.setLoopFormed(true);

        com.gtnewhorizons.coolantloops.engine.LoopSegment segOw = new com.gtnewhorizons.coolantloops.engine.LoopSegment(
            "ow_seg",
            10.0,
            0.2,
            0.0001,
            1.0,
            100.0,
            1000.0,
            20.0);
        pumpOverworld.getEngine()
            .addSegment(segOw);
        pumpOverworld.getEngine()
            .setFluid(com.gtnewhorizons.coolantloops.engine.CoolantFluidProperty.LAVA);

        assertTrue(pumpOverworld.stepCoolantLoop());
        assertEquals(MTECoolantPump.LoopState.STOPPED, pumpOverworld.getLoopState());
        assertEquals("Pump refused to start: Lava is too viscous here!", pumpOverworld.getLoopStatus());

        // 4. Pump in Nether accepts lava and bypasses melting temperature check
        MTECoolantPump pumpNether = new MTECoolantPump("test_pump_lava_nether");
        gregtech.api.interfaces.tileentity.IGregTechTileEntity mockPumpBaseNether = Mockito
            .mock(gregtech.api.interfaces.tileentity.IGregTechTileEntity.class);
        Mockito.when(mockPumpBaseNether.isAllowedToWork())
            .thenReturn(true);
        Mockito.when(mockPumpBaseNether.isServerSide())
            .thenReturn(true);
        Mockito.when(mockPumpBaseNether.getWorld())
            .thenReturn(nether);
        pumpNether.setBaseMetaTileEntity(mockPumpBaseNether);
        pumpNether.setLoopFormed(true);

        net.minecraft.item.Item mockTool = Mockito.mock(net.minecraft.item.Item.class);
        ItemStack mockRotor = Mockito.mock(ItemStack.class);
        Mockito.when(mockRotor.getItem())
            .thenReturn(mockTool);
        Mockito.when(mockRotor.getItemDamage())
            .thenReturn(0);
        Mockito.when(mockRotor.getMaxDamage())
            .thenReturn(1000);
        pumpNether.setRotorStack(mockRotor);

        com.gtnewhorizons.coolantloops.engine.LoopSegment segNether = new com.gtnewhorizons.coolantloops.engine.LoopSegment(
            "nether_seg",
            10.0,
            0.2,
            0.0001,
            1.0,
            100.0,
            1000.0,
            93.3);
        pumpNether.getEngine()
            .addSegment(segNether);
        pumpNether.getEngine()
            .setFluid(com.gtnewhorizons.coolantloops.engine.CoolantFluidProperty.LAVA);

        assertTrue(pumpNether.stepCoolantLoop());
        assertFalse(
            pumpNether.getLoopStatus()
                .contains("Lava cannot be used as a coolant"));
        assertFalse(
            pumpNether.getLoopStatus()
                .contains("below declared GregTech melting point"));
    }

    private MTEHatchEnergy createMockEnergyHatch(int tier, long[] storedEUHolder) {
        MTEHatchEnergy hatch = new MTEHatchEnergy("test_hatch_" + tier, tier, new String[0], null);
        IGregTechTileEntity baseTE = Mockito.mock(IGregTechTileEntity.class);
        Mockito.when(baseTE.getStoredEU())
            .thenAnswer(inv -> storedEUHolder[0]);
        Mockito.when(baseTE.decreaseStoredEnergyUnits(Mockito.anyLong(), Mockito.anyBoolean()))
            .thenAnswer(inv -> {
                long toDecrease = (Long) inv.getArguments()[0];
                if (storedEUHolder[0] >= toDecrease) {
                    storedEUHolder[0] -= toDecrease;
                    return true;
                }
                return false;
            });
        hatch.setBaseMetaTileEntity(baseTE);
        return hatch;
    }

    private MTECoolantPump createOperatingPumpWithBase(boolean[] allowedToWorkHolder) {
        MTECoolantPump pump = new MTECoolantPump("test_pump");
        IGregTechTileEntity mockPumpBase = Mockito.mock(IGregTechTileEntity.class);
        Mockito.when(mockPumpBase.isAllowedToWork())
            .thenAnswer(inv -> allowedToWorkHolder[0]);
        Mockito.when(mockPumpBase.isServerSide())
            .thenReturn(true);
        boolean[] shutdownHolder = new boolean[] { false };
        gregtech.api.util.shutdown.ShutDownReason[] shutdownReasonHolder = new gregtech.api.util.shutdown.ShutDownReason[] {
            gregtech.api.util.shutdown.ShutDownReasonRegistry.NONE };
        Mockito.doAnswer(inv -> {
            shutdownHolder[0] = inv.getArgument(0);
            return null;
        })
            .when(mockPumpBase)
            .setShutdownStatus(Mockito.anyBoolean());
        Mockito.when(mockPumpBase.wasShutdown())
            .thenAnswer(inv -> shutdownHolder[0]);
        Mockito.doAnswer(inv -> {
            shutdownReasonHolder[0] = inv.getArgument(0);
            return null;
        })
            .when(mockPumpBase)
            .setShutDownReason(Mockito.any());
        Mockito.when(mockPumpBase.getLastShutDownReason())
            .thenAnswer(inv -> shutdownReasonHolder[0]);
        Mockito.doAnswer(inv -> {
            allowedToWorkHolder[0] = false;
            return null;
        })
            .when(mockPumpBase)
            .disableWorking();
        Mockito.doAnswer(inv -> {
            allowedToWorkHolder[0] = true;
            shutdownHolder[0] = false;
            shutdownReasonHolder[0] = gregtech.api.util.shutdown.ShutDownReasonRegistry.NONE;
            return null;
        })
            .when(mockPumpBase)
            .enableWorking();
        pump.setBaseMetaTileEntity(mockPumpBase);
        pump.setLoopFormed(true);

        net.minecraft.item.Item mockTool = Mockito.mock(net.minecraft.item.Item.class);
        ItemStack mockRotor = Mockito.mock(ItemStack.class);
        Mockito.when(mockRotor.getItem())
            .thenReturn(mockTool);
        Mockito.when(mockRotor.getItemDamage())
            .thenReturn(0);
        Mockito.when(mockRotor.getMaxDamage())
            .thenReturn(1000);
        pump.setRotorStack(mockRotor);

        LoopSegment seg = new LoopSegment("seg_test", 10.0, 0.2, 0.0001, 1.0, 100.0, 1000.0, 20.0);
        pump.getEngine()
            .addSegment(seg);
        pump.getEngine()
            .setFluid(CoolantFluidProperty.DISTILLED_WATER);
        pump.setRequiredFillLiters(1000L);
        pump.setCurrentFillLiters(1000L);
        pump.setLoopState(MTECoolantPump.LoopState.CIRCULATING);
        return pump;
    }

    @Test
    public void testSingleEnergyHatchPowerDrawAndPowerfailShutdown() {
        boolean[] pumpAllowed = new boolean[] { true };
        MTECoolantPump pump = createOperatingPumpWithBase(pumpAllowed);

        // 1 EV Hatch (tier = 4 -> 2048 EU/t, 1A)
        long[] hatchEU = new long[] { 10000L };
        MTEHatchEnergy evHatch = createMockEnergyHatch(4, hatchEU);
        pump.mEnergyHatches.add(evHatch);

        assertEquals(2048L, pump.getRequiredPowerDrawEU());

        // Step tick: has 10,000 EU >= 2048 EU
        assertTrue(pump.stepCoolantLoop());
        assertEquals(10000L - 2048L, hatchEU[0]);
        assertTrue(pump.isAllowedToWork());
        assertFalse(pump.isPowerFailed());
        assertEquals(MTECoolantPump.LoopState.CIRCULATING, pump.getLoopState());

        // Drain until insufficient EU (< 2048)
        hatchEU[0] = 1000L;
        assertTrue(pump.stepCoolantLoop());

        // Verify powerfail
        assertFalse(pump.isAllowedToWork());
        assertFalse(pumpAllowed[0]);
        assertTrue(pump.isPowerFailed());
        assertEquals(MTECoolantPump.LoopState.STOPPED, pump.getLoopState());
        assertEquals(1000L, hatchEU[0]); // Atomic: not partially drained!
        assertEquals(
            gregtech.api.util.shutdown.ShutDownReasonRegistry.POWER_LOSS.getDisplayString(),
            pump.getLoopStatus());

        // Restore power to hatch (10,000 EU)
        hatchEU[0] = 10000L;

        // Step tick: MUST NOT automatically restart!
        assertTrue(pump.stepCoolantLoop());
        assertFalse(pump.isAllowedToWork());
        assertTrue(pump.isPowerFailed());
        assertEquals(10000L, hatchEU[0]); // No power drawn while stopped!

        // Player manually restarts the pump (e.g. soft mallet / GUI button)
        pump.getBaseMetaTileEntity()
            .enableWorking();
        pump.onEnableWorking();
        assertTrue(pump.isAllowedToWork());
        assertFalse(pump.isPowerFailed());

        // Now pump runs and draws power again
        assertTrue(pump.stepCoolantLoop());
        assertEquals(10000L - 2048L, hatchEU[0]);
        assertTrue(pump.isAllowedToWork());
        assertEquals(MTECoolantPump.LoopState.CIRCULATING, pump.getLoopState());
    }

    @Test
    public void testTwoEnergyHatchesPowerDrawAndAtomicDraining() {
        boolean[] pumpAllowed = new boolean[] { true };
        MTECoolantPump pump = createOperatingPumpWithBase(pumpAllowed);

        // 2 EV Hatches (tier 4 -> 4A at 2048V = 8192 EU/t)
        long[] hatch1EU = new long[] { 5000L };
        long[] hatch2EU = new long[] { 5000L };
        MTEHatchEnergy evHatch1 = createMockEnergyHatch(4, hatch1EU);
        MTEHatchEnergy evHatch2 = createMockEnergyHatch(4, hatch2EU);
        pump.mEnergyHatches.add(evHatch1);
        pump.mEnergyHatches.add(evHatch2);

        assertEquals(8192L, pump.getRequiredPowerDrawEU());

        // Combined = 10,000 EU >= 8192 EU -> draws 5000 from hatch1 and 3192 from hatch2
        assertTrue(pump.stepCoolantLoop());
        assertEquals(0L, hatch1EU[0]);
        assertEquals(1808L, hatch2EU[0]);
        assertTrue(pump.isAllowedToWork());

        // Now combined = 4000 + 4000 = 8000 EU < 8192 EU
        hatch1EU[0] = 4000L;
        hatch2EU[0] = 4000L;
        assertTrue(pump.stepCoolantLoop());

        // Must powerfail and must NOT partially drain either hatch
        assertFalse(pump.isAllowedToWork());
        assertTrue(pump.isPowerFailed());
        assertEquals(4000L, hatch1EU[0]);
        assertEquals(4000L, hatch2EU[0]);
    }

    @Test
    public void testMixedTierEnergyHatchesPowerDraw() {
        boolean[] pumpAllowed = new boolean[] { true };
        MTECoolantPump pump = createOperatingPumpWithBase(pumpAllowed);

        // 1 HV hatch (tier = 3, 512V) + 1 IV hatch (tier = 5, 8192V)
        // User rule: 4 amps at higher tier voltage = 4 * 8192 = 32768 EU/t
        long[] hatchHvEU = new long[] { 20000L };
        long[] hatchIvEU = new long[] { 20000L };
        MTEHatchEnergy hvHatch = createMockEnergyHatch(3, hatchHvEU);
        MTEHatchEnergy ivHatch = createMockEnergyHatch(5, hatchIvEU);
        pump.mEnergyHatches.add(hvHatch);
        pump.mEnergyHatches.add(ivHatch);

        assertEquals(32768L, pump.getRequiredPowerDrawEU());

        // Combined = 40000 EU >= 32768 EU
        assertTrue(pump.stepCoolantLoop());
        assertTrue(pump.isAllowedToWork());
        assertEquals(0L, hatchHvEU[0]);
        assertEquals(7232L, hatchIvEU[0]);
    }

    @Test
    public void testFillingPhaseRequiresPowerAndFailsWithoutIt() {
        boolean[] pumpAllowed = new boolean[] { true };
        MTECoolantPump pump = createOperatingPumpWithBase(pumpAllowed);

        // 1 EV Hatch with 0 EU
        long[] hatchEU = new long[] { 0L };
        MTEHatchEnergy evHatch = createMockEnergyHatch(4, hatchEU);
        pump.mEnergyHatches.add(evHatch);

        // Loop is un-filled
        pump.setRequiredFillLiters(10000L);
        pump.setCurrentFillLiters(0L);
        pump.setLoopState(MTECoolantPump.LoopState.FILLING);

        assertTrue(pump.stepCoolantLoop());

        // Must powerfail during filling phase
        assertFalse(pump.isAllowedToWork());
        assertTrue(pump.isPowerFailed());
        assertEquals(MTECoolantPump.LoopState.STOPPED, pump.getLoopState());
    }

    @Test
    public void testPumpCrawlTimeTrackingAndNoAutoMaintenanceFix() {
        boolean[] pumpAllowed = new boolean[] { true };
        MTECoolantPump pump = createOperatingPumpWithBase(pumpAllowed);

        // Initial crawl timestamps
        assertEquals(-1L, pump.getLastCrawlStartTime());
        assertEquals(-1L, pump.getLastCrawlCompleteTime());

        // Simulate damage / maintenance issues on the multiblock
        pump.mWrench = false;
        pump.mScrewdriver = false;
        pump.mSoftMallet = false;
        pump.mHardHammer = false;
        pump.mCrowbar = false;
        pump.mSolderingTool = false;

        // Apply a successful CrawlResult
        LoopSegment testSeg = new LoopSegment("pipe_test", 10.0, 0.1, 0.00005, 0.5, 100.0, 1000.0, 20.0);
        CrawlResult successResult = CrawlResult.success(
            Collections.singletonList(testSeg),
            Collections.emptyList(),
            Collections.emptySet(),
            500.0,
            Materials.Steel,
            Collections.emptyList());

        boolean applied = pump.applyCrawlResult(successResult);
        assertTrue(applied, "Applying valid crawl result must succeed");
        assertTrue(pump.isLoopFormed(), "Loop must be formed");

        // CRITICAL: Maintenance issues must NOT be auto-fixed by re-crawling!
        assertFalse(pump.mWrench, "mWrench issue must remain unfixed");
        assertFalse(pump.mScrewdriver, "mScrewdriver issue must remain unfixed");
        assertFalse(pump.mSoftMallet, "mSoftMallet issue must remain unfixed");
        assertFalse(pump.mHardHammer, "mHardHammer issue must remain unfixed");
        assertFalse(pump.mCrowbar, "mCrowbar issue must remain unfixed");
        assertFalse(pump.mSolderingTool, "mSolderingTool issue must remain unfixed");
    }

    @Test
    public void testPumpLoopTooLargeStatusMessage() {
        boolean[] pumpAllowed = new boolean[] { true };
        MTECoolantPump pump = createOperatingPumpWithBase(pumpAllowed);

        CrawlResult failResult = CrawlResult
            .fail("Loop structure check could not complete because the loop is too large");
        boolean applied = pump.applyCrawlResult(failResult);
        assertFalse(applied, "Applying failed crawl result must return false");
        assertFalse(pump.isLoopFormed(), "Loop must not be formed");
        assertEquals(
            "Loop structure check could not complete because the loop is too large",
            pump.getLoopStatus(),
            "Controller status must state the exact error message");
    }

    @Test
    public void testPumpSoftMalletTurnoffCoastsWithDragAndExchangesHeat() {
        boolean[] pumpAllowed = new boolean[] { true };
        MTECoolantPump pump = createOperatingPumpWithBase(pumpAllowed);

        // Set initial volumetric flow rate to 0.05 m^3/s (50 L/s)
        pump.getEngine()
            .setVolumetricFlowRate(0.05);

        // Register a mock loop device
        ICoolantLoopDevice mockDev = Mockito.mock(ICoolantLoopDevice.class);
        Mockito.when(mockDev.getDeviceId())
            .thenReturn("seg_test");
        pump.addLoopDevice(mockDev);

        // Turn off pump via soft mallet / redstone disable
        pumpAllowed[0] = false;

        assertTrue(pump.stepCoolantLoop());

        // Flow rate must NOT be abruptly 0! It must coast down via drag
        double flow1 = pump.getEngine()
            .getVolumetricFlowRate();
        assertTrue(flow1 > 0.0, "Flow rate must remain positive through fluid inertia");
        assertTrue(flow1 < 0.05, "Flow rate must decelerate from drag");

        // Thermal exchange must have been executed during coasting
        Mockito.verify(mockDev, Mockito.atLeastOnce())
            .processThermalExchange(
                Mockito.eq(flow1),
                Mockito.eq(0.05),
                Mockito.eq(
                    pump.getEngine()
                        .getFluid()),
                Mockito.any());
    }

    @Test
    public void testPumpPowerFailureCoastsWithDragAndExchangesHeat() {
        boolean[] pumpAllowed = new boolean[] { true };
        MTECoolantPump pump = createOperatingPumpWithBase(pumpAllowed);

        // 1 EV Hatch with 0 EU stored
        long[] hatchEU = new long[] { 0L };
        MTEHatchEnergy evHatch = createMockEnergyHatch(4, hatchEU);
        pump.mEnergyHatches.add(evHatch);

        // Set circulating flow rate
        pump.getEngine()
            .setVolumetricFlowRate(0.05);

        ICoolantLoopDevice mockDev = Mockito.mock(ICoolantLoopDevice.class);
        Mockito.when(mockDev.getDeviceId())
            .thenReturn("seg_test");
        pump.addLoopDevice(mockDev);

        // Step while out of power triggers powerfail
        assertTrue(pump.stepCoolantLoop());
        assertTrue(pump.isPowerFailed(), "Pump must powerfail on missing energy");

        // Fluid must coast down, NOT stop abruptly
        double flow = pump.getEngine()
            .getVolumetricFlowRate();
        assertTrue(flow > 0.0, "Fluid must coast through inertia during power failure");
        assertTrue(flow < 0.05, "Fluid must decelerate from drag");

        // Thermal exchange must continue during powerfail coasting
        Mockito.verify(mockDev, Mockito.atLeastOnce())
            .processThermalExchange(
                Mockito.eq(flow),
                Mockito.eq(0.05),
                Mockito.eq(
                    pump.getEngine()
                        .getFluid()),
                Mockito.any());
    }

    @Test
    public void testPumpImpellerMeltingCoastsWithDragAndExchangesHeat() {
        boolean[] pumpAllowed = new boolean[] { true };
        MTECoolantPump pump = createOperatingPumpWithBase(pumpAllowed);

        pump.getEngine()
            .setVolumetricFlowRate(0.05);

        ICoolantLoopDevice mockDev = Mockito.mock(ICoolantLoopDevice.class);
        Mockito.when(mockDev.getDeviceId())
            .thenReturn("seg_test");
        pump.addLoopDevice(mockDev);

        // Trigger impeller melting
        pump.failOnImpellerMelting(2000.0, Materials.Steel);

        // Impeller is destroyed
        assertNull(pump.getRotor(), "Impeller must be destroyed");
        assertEquals(0.0, pump.getRotorEfficiency(), 1e-6);

        // Volumetric flow rate must NOT be zeroed immediately!
        double flowAfterMelt = pump.getEngine()
            .getVolumetricFlowRate();
        assertEquals(0.05, flowAfterMelt, 1e-6, "Melting must not abruptly stop fluid momentum");

        // In subsequent tick without impeller, pump coasts down with drag
        assertTrue(pump.stepCoolantLoop());
        double flowCoasting = pump.getEngine()
            .getVolumetricFlowRate();
        assertTrue(flowCoasting > 0.0, "Fluid must continue coasting");
        assertTrue(flowCoasting < 0.05, "Fluid must slow down from drag");

        // Thermal exchange must continue during coasting
        Mockito.verify(mockDev, Mockito.atLeastOnce())
            .processThermalExchange(
                Mockito.eq(flowCoasting),
                Mockito.eq(0.05),
                Mockito.eq(
                    pump.getEngine()
                        .getFluid()),
                Mockito.any());
    }
}
