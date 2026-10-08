package com.gtnewhorizons.coolantloops;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import net.minecraft.util.AxisAlignedBB;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.gtnewhorizons.coolantloops.common.metatileentity.pipe.MTECoolantPipe;

import gregtech.api.enums.Materials;
import gregtech.api.interfaces.ITexture;

public class CoolantPipeVisualTest {

    @BeforeAll
    static void initEnvironment() {
        Thread.currentThread().setName("Server thread");
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
    public void testBezelSizeScaling() {
        // Test bezel sizes across all 5 standard pipe thicknesses
        float[] thicknesses = { 0.25F, 0.375F, 0.50F, 0.75F, 0.875F };
        for (float t : thicknesses) {
            float b = MTECoolantPipe.getBezelSize(t);
            assertTrue(b > 0.0F, "Bezel size must be positive for thickness " + t);
            assertTrue(b < t / 2.0F, "Bezel size must be less than half thickness: " + b + " vs " + (t / 2.0F));
            assertTrue(b >= 0.02F && b <= 0.0625F, "Bezel size must stay within [0.02, 0.0625]: " + b);
        }

        // Medium pipe (0.50) must have exactly 1/16 block (1 pixel = 0.0625) bezel
        assertEquals(0.0625F, MTECoolantPipe.getBezelSize(0.50F), 1e-6);
    }

    @Test
    public void testBeveledCrossSectionVolumeAndArea() {
        float thickness = 0.50F;
        float pipeMin = (1.0F - thickness) / 2.0F; // 0.25
        float pipeMax = 1.0F - pipeMin;            // 0.75
        float b = MTECoolantPipe.getBezelSize(thickness); // 0.0625
        float length = 1.0F;

        List<AxisAlignedBB> boxes = MTECoolantPipe.getBeveledSubBoxesX(0.0F, length, pipeMin, pipeMax, b);
        assertEquals(3, boxes.size(), "Must consist of 3 sub-boxes forming the beveled cross-section");

        double totalVolume = 0.0;
        for (AxisAlignedBB box : boxes) {
            double v = (box.maxX - box.minX) * (box.maxY - box.minY) * (box.maxZ - box.minZ);
            totalVolume += v;
        }

        double standardVolume = length * (thickness * thickness); // 1.0 * 0.25 = 0.25
        double cornerCutoutsVolume = 4.0 * (b * b) * length;     // 4 * 0.0625^2 * 1.0 = 0.015625
        double expectedBeveledVolume = standardVolume - cornerCutoutsVolume; // 0.234375

        assertEquals(expectedBeveledVolume, totalVolume, 1e-6,
            "Beveled pipe volume must match standard box minus 4 corner cut-outs");
        assertTrue(totalVolume < standardVolume, "Beveled pipe volume must be strictly less than un-beveled box");
    }

    @Test
    public void testBeveledSubBoxesDisjoint() {
        float thickness = 0.50F;
        float pipeMin = 0.25F;
        float pipeMax = 0.75F;
        float b = 0.0625F;

        List<AxisAlignedBB> boxes = MTECoolantPipe.getBeveledSubBoxesX(0.0F, 1.0F, pipeMin, pipeMax, b);
        // Verify no overlapping volume between any pair of boxes
        for (int i = 0; i < boxes.size(); i++) {
            for (int j = i + 1; j < boxes.size(); j++) {
                AxisAlignedBB b1 = boxes.get(i);
                AxisAlignedBB b2 = boxes.get(j);
                double overlapX = Math.max(0.0, Math.min(b1.maxX, b2.maxX) - Math.max(b1.minX, b2.minX));
                double overlapY = Math.max(0.0, Math.min(b1.maxY, b2.maxY) - Math.max(b1.minY, b2.minY));
                double overlapZ = Math.max(0.0, Math.min(b1.maxZ, b2.maxZ) - Math.max(b1.minZ, b2.minZ));
                double intersectionVolume = overlapX * overlapY * overlapZ;
                assertEquals(0.0, intersectionVolume, 1e-6, "Sub-boxes must have zero intersection volume (no z-fighting)");
            }
        }
    }

    @Test
    public void testPipeDescriptionAndNaming() {
        MTECoolantPipe pipe = new MTECoolantPipe("test.coolantpipe", 0.50F, Materials.Steel, 240, 2500, true);
        assertEquals("Medium Steel Coolant Pipe", pipe.getLocalizedName());
        String[] desc = pipe.getDescription();
        assertTrue(desc.length >= 4, "Must contain insulated description and specs");
        assertTrue(desc[0].contains("Thermally Insulated Coolant Loop Pipe"));
    }

    @Test
    public void testDistinctiveTextureOverlay() {
        MTECoolantPipe pipe = new MTECoolantPipe("test.coolantpipe", 0.50F, Materials.Steel, 240, 2500, true);
        ITexture[] textures = pipe.getTexture(null, net.minecraftforge.common.util.ForgeDirection.NORTH, 0, -1, false, false);
        assertNotNull(textures, "Pipe textures must not be null");
        assertTrue(textures.length >= 1, "Must contain base and optional insulation textures");
    }

    static class DummyDeviceTile extends net.minecraft.tileentity.TileEntity implements com.gtnewhorizons.coolantloops.engine.ICoolantLoopDevice {
        @Override public String getDeviceId() { return "dummy"; }
        @Override public double getMinorLossK() { return 0.2; }
        @Override public double getDeviceTemperatureCelsius() { return 20.0; }
        @Override public void processThermalExchange(double flow, double dt, com.gtnewhorizons.coolantloops.engine.CoolantFluidProperty fluid, com.gtnewhorizons.coolantloops.engine.LoopSegment seg) {}
    }

    @Test
    public void testCoolantPipeCanConnectToPressurizedHatch() {
        MTECoolantPipe pipe = new MTECoolantPipe("test.coolantpipe", 0.50F, Materials.Steel, 240, 2500, true);
        gregtech.api.metatileentity.BaseMetaTileEntity mockGte = Mockito.mock(gregtech.api.metatileentity.BaseMetaTileEntity.class);
        com.gtnewhorizons.coolantloops.common.metatileentity.hatch.MTEHatchPressurizedFluid hatch =
            new com.gtnewhorizons.coolantloops.common.metatileentity.hatch.MTEHatchPressurizedFluid("hatch", 4, new String[0], new gregtech.api.interfaces.ITexture[0][0][0]);
        Mockito.when(mockGte.getMetaTileEntity()).thenReturn(hatch);

        assertTrue(pipe.canConnect(net.minecraftforge.common.util.ForgeDirection.WEST, mockGte),
            "MTECoolantPipe must allow connection to MTEHatchPressurizedFluid");
    }

    @Test
    public void testCoolantPipeCanConnectToManifoldAndDevices() {
        MTECoolantPipe pipe = new MTECoolantPipe("test.coolantpipe", 0.50F, Materials.Steel, 240, 2500, true);
        com.gtnewhorizons.coolantloops.common.tileentity.TileEntityManifold mockManifold =
            Mockito.mock(com.gtnewhorizons.coolantloops.common.tileentity.TileEntityManifold.class);
        assertTrue(pipe.canConnect(net.minecraftforge.common.util.ForgeDirection.EAST, mockManifold),
            "MTECoolantPipe must allow connection to TileEntityManifold");

        DummyDeviceTile deviceTile = new DummyDeviceTile();
        assertTrue(pipe.canConnect(net.minecraftforge.common.util.ForgeDirection.NORTH, deviceTile),
            "MTECoolantPipe must allow connection to ICoolantLoopDevice");
    }

    @Test
    public void testPressurizedHatchReturnsNonEmptyTankInfo() {
        com.gtnewhorizons.coolantloops.common.metatileentity.hatch.MTEHatchPressurizedFluid hatch =
            new com.gtnewhorizons.coolantloops.common.metatileentity.hatch.MTEHatchPressurizedFluid("hatch", 4, new String[0], new gregtech.api.interfaces.ITexture[0][0][0]);
        net.minecraftforge.fluids.FluidTankInfo[] infos = hatch.getTankInfo(net.minecraftforge.common.util.ForgeDirection.UNKNOWN);
        assertNotNull(infos, "Tank info array must not be null");
        assertTrue(infos.length > 0, "Tank info array must not be empty (prevents GT pipe auto-disconnect)");
        assertTrue(infos[0].capacity > 0, "Tank capacity must be positive so pipe canConnect succeeds");
    }
}
