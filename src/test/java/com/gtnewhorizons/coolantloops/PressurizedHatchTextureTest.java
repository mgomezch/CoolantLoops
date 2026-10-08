package com.gtnewhorizons.coolantloops;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraftforge.common.util.ForgeDirection;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.gtnewhorizons.coolantloops.common.metatileentity.hatch.MTEHatchPressurizedFluid;
import com.gtnewhorizons.coolantloops.common.metatileentity.multi.MTECoolantPump;
import com.gtnewhorizons.coolantloops.common.metatileentity.multi.MTEPressurizedDegasser;
import com.gtnewhorizons.coolantloops.common.metatileentity.multi.MTEPressurizedHeatExchanger;

import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * Unit tests verifying that Pressurized Fluid Hatches correctly take on the base casing texture
 * of the multiblock they belong to when formed via StructureDefinition / addBottomHatch or checkMachine.
 */
public class PressurizedHatchTextureTest {

    private static final int CASING_INDEX_ROBUST_TUNGSTENSTEEL = 48 + 2; // Page 0, Index 50
    private static final int CASING_INDEX_HEAT_PROOF = 11;

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

        try {
            java.lang.reflect.Field sideField = cpw.mods.fml.relauncher.FMLRelaunchLog.class.getDeclaredField("side");
            sideField.setAccessible(true);
            sideField.set(null, cpw.mods.fml.relauncher.Side.SERVER);
        } catch (Throwable ignored) {}

        ITexture dummyCasing50 = Mockito.mock(ITexture.class);
        Textures.BlockIcons.setCasingTextureForId(CASING_INDEX_ROBUST_TUNGSTENSTEEL, dummyCasing50);
        ITexture dummyCasing11 = Mockito.mock(ITexture.class);
        Textures.BlockIcons.setCasingTextureForId(CASING_INDEX_HEAT_PROOF, dummyCasing11);
    }

    @Test
    public void testPressurizedHatchTextureInheritance() {
        MTEHatchPressurizedFluid hatch = new MTEHatchPressurizedFluid("test_hatch", 1, new String[0], null);
        IGregTechTileEntity mockTile = Mockito.mock(IGregTechTileEntity.class);
        Mockito.when(mockTile.isServerSide())
            .thenReturn(true);
        hatch.setBaseMetaTileEntity(mockTile);

        // Before multiblock formation, casing texture is null (falls back to tier machine casing)
        assertNull(hatch.getCasingTexture(), "Casing texture should be null before multiblock formation");

        // When multiblock forms and calls updateTexture with casing index 50
        hatch.updateTexture(CASING_INDEX_ROBUST_TUNGSTENSTEEL);

        // Casing texture must now match the multiblock casing (Robust Tungstensteel Machine Casing)
        ITexture casingTex = hatch.getCasingTexture();
        assertNotNull(casingTex, "Casing texture must not be null after updateTexture");
        assertEquals(
            Textures.BlockIcons.casingTexturePages[0][50],
            casingTex,
            "Casing texture must match Robust Tungstensteel Machine Casing (page 0, index 50)");

        // Non-facing side must render the multiblock casing base texture
        ITexture[] nonFacingTextures = hatch
            .getTexture(mockTile, ForgeDirection.UP, ForgeDirection.SOUTH, 0, false, false);
        assertEquals(1, nonFacingTextures.length);
        assertEquals(casingTex, nonFacingTextures[0], "Non-facing side must render the base casing texture");

        // Facing side in DISCHARGE mode must render base casing texture + OVERLAY_PIPE_OUT
        hatch.setMode(MTEHatchPressurizedFluid.HatchMode.DISCHARGE);
        ITexture[] dischargeFacing = hatch
            .getTexture(mockTile, ForgeDirection.SOUTH, ForgeDirection.SOUTH, 0, false, false);
        assertEquals(2, dischargeFacing.length);
        assertEquals(casingTex, dischargeFacing[0], "Facing side base texture must match casing");

        // Facing side in SUCTION mode must render base casing texture + OVERLAY_PIPE_IN
        hatch.setMode(MTEHatchPressurizedFluid.HatchMode.SUCTION);
        ITexture[] suctionFacing = hatch
            .getTexture(mockTile, ForgeDirection.SOUTH, ForgeDirection.SOUTH, 0, false, false);
        assertEquals(2, suctionFacing.length);
        assertEquals(casingTex, suctionFacing[0], "Facing side base texture must match casing");
    }

    @Test
    public void testCoolantPumpHatchTextureOnForm() {
        MTECoolantPump pump = new MTECoolantPump("test_pump");
        MTEHatchPressurizedFluid dischargeHatch = new MTEHatchPressurizedFluid("discharge", 1, new String[0], null);
        dischargeHatch.setMode(MTEHatchPressurizedFluid.HatchMode.DISCHARGE);

        IGregTechTileEntity mockTile = Mockito.mock(IGregTechTileEntity.class);
        Mockito.when(mockTile.getMetaTileEntity())
            .thenReturn(dischargeHatch);
        Mockito.when(mockTile.isServerSide())
            .thenReturn(true);
        dischargeHatch.setBaseMetaTileEntity(mockTile);

        // addBottomHatch in StructureDefinition
        boolean added = pump.addBottomHatch(mockTile, CASING_INDEX_ROBUST_TUNGSTENSTEEL);
        assertTrue(added, "addBottomHatch should accept Pressurized Fluid Hatch");
        assertEquals(
            Textures.BlockIcons.casingTexturePages[0][50],
            dischargeHatch.getCasingTexture(),
            "Discharge hatch must inherit pump casing texture (index 50) on structure add");
    }

    @Test
    public void testHeatExchangerHatchTextureOnForm() {
        MTEPressurizedHeatExchanger phe = new MTEPressurizedHeatExchanger("test_phe");
        MTEHatchPressurizedFluid inletHatch = new MTEHatchPressurizedFluid("inlet", 1, new String[0], null);
        inletHatch.setMode(MTEHatchPressurizedFluid.HatchMode.SUCTION);

        IGregTechTileEntity mockTile = Mockito.mock(IGregTechTileEntity.class);
        Mockito.when(mockTile.getMetaTileEntity())
            .thenReturn(inletHatch);
        Mockito.when(mockTile.isServerSide())
            .thenReturn(true);
        inletHatch.setBaseMetaTileEntity(mockTile);

        // addPressurizedHatch (or addBottomHatch) in StructureDefinition
        boolean added = phe.addPressurizedHatch(mockTile, CASING_INDEX_HEAT_PROOF);
        assertTrue(added, "addPressurizedHatch should accept Pressurized Fluid Hatch");
        assertEquals(
            Textures.BlockIcons.casingTexturePages[0][CASING_INDEX_HEAT_PROOF],
            inletHatch.getCasingTexture(),
            "Inlet hatch must inherit PHE casing texture (index 11) on structure add");
    }

    @Test
    public void testDegasserHatchTextureOnForm() {
        MTEPressurizedDegasser degasser = new MTEPressurizedDegasser("test_degasser");
        MTEHatchPressurizedFluid inletHatch = new MTEHatchPressurizedFluid("inlet", 1, new String[0], null);

        IGregTechTileEntity mockTile = Mockito.mock(IGregTechTileEntity.class);
        Mockito.when(mockTile.getMetaTileEntity())
            .thenReturn(inletHatch);
        Mockito.when(mockTile.isServerSide())
            .thenReturn(true);
        inletHatch.setBaseMetaTileEntity(mockTile);

        // addBottomHatch in StructureDefinition
        boolean added = degasser.addBottomHatch(mockTile, CASING_INDEX_ROBUST_TUNGSTENSTEEL);
        assertTrue(added, "addBottomHatch should accept Pressurized Fluid Hatch");
        assertEquals(
            Textures.BlockIcons.casingTexturePages[0][50],
            inletHatch.getCasingTexture(),
            "Inlet hatch must inherit Degasser casing texture (index 50) on structure add");
    }
}
