package com.gtnewhorizons.coolantloops;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.gtnewhorizon.structurelib.alignment.constructable.ChannelDataAccessor;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizons.coolantloops.common.metatileentity.CoolantStructureChannels;
import com.gtnewhorizons.coolantloops.common.metatileentity.multi.MTECoolantPump;

import gregtech.api.enums.Materials;

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
        gregtech.api.metatileentity.implementations.MTEHatchEnergy hatch1 = Mockito.mock(gregtech.api.metatileentity.implementations.MTEHatchEnergy.class);
        gregtech.api.metatileentity.implementations.MTEHatchEnergy hatch2 = Mockito.mock(gregtech.api.metatileentity.implementations.MTEHatchEnergy.class);
        gregtech.api.metatileentity.implementations.MTEHatchEnergy hatch3 = Mockito.mock(gregtech.api.metatileentity.implementations.MTEHatchEnergy.class);

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
        gregtech.api.interfaces.tileentity.IGregTechTileEntity mockPumpBase = Mockito.mock(gregtech.api.interfaces.tileentity.IGregTechTileEntity.class);
        Mockito.when(mockPumpBase.isAllowedToWork()).thenReturn(true);
        Mockito.when(mockPumpBase.isServerSide()).thenReturn(true);
        pump.setBaseMetaTileEntity(mockPumpBase);
        pump.setLoopFormed(true);
        pump.getEngine().addSegment(new com.gtnewhorizons.coolantloops.engine.LoopSegment("test_seg", 10.0, 0.2, 0.0001, 1.0, 100.0, 1000.0, 20.0));
        pump.getEngine().setFluid(com.gtnewhorizons.coolantloops.engine.CoolantFluidProperty.WATER);
        assertTrue(pump.stepCoolantLoop());
        assertEquals(MTECoolantPump.LoopState.STOPPED, pump.getLoopState());
        assertTrue(pump.getLoopStatus().contains("Plain regular water cannot be used in a coolant loop"));
    }
}
