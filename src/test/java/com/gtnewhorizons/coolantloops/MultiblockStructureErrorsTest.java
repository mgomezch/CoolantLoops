package com.gtnewhorizons.coolantloops;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.init.Bootstrap;
import net.minecraft.world.World;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.gtnewhorizon.structurelib.StructureLib;
import com.gtnewhorizons.coolantloops.common.metatileentity.multi.MTECoolantBath;
import com.gtnewhorizons.coolantloops.common.metatileentity.multi.MTECoolantPump;
import com.gtnewhorizons.coolantloops.common.metatileentity.multi.MTEPressurizedDegasser;
import com.gtnewhorizons.coolantloops.common.metatileentity.multi.MTEPressurizedHeatExchanger;

import cpw.mods.fml.common.ICrashCallable;
import cpw.mods.fml.common.Loader;
import gregtech.GTMod;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.structure.error.StructureError;
import gregtech.common.GTProxy;
import gregtech.common.data.GTPowerfailTracker;

public class MultiblockStructureErrorsTest {

    @BeforeAll
    static void initEnvironment() {
        Thread.currentThread()
            .setName("Server thread");
        try {
            Loader mockLoader = Mockito.mock(Loader.class);
            Mockito.when(mockLoader.getCallableCrashInformation())
                .thenReturn(Mockito.mock(ICrashCallable.class));
            new java.io.File("build/tmp/config").mkdirs();
            Mockito.when(mockLoader.getConfigDir())
                .thenReturn(new java.io.File("build/tmp/config"));
            Field f = Loader.class.getDeclaredField("instance");
            f.setAccessible(true);
            if (f.get(null) == null) {
                f.set(null, mockLoader);
            }
        } catch (Throwable ignored) {}

        try {
            Field mcHome = cpw.mods.fml.relauncher.FMLInjectionData.class.getDeclaredField("minecraftHome");
            mcHome.setAccessible(true);
            mcHome.set(null, new java.io.File("."));
        } catch (Throwable ignored) {}

        try {
            Bootstrap.func_151354_b();
        } catch (Throwable ignored) {}

        gregtech.api.GregTechAPI.sBlockCasings1 = net.minecraft.init.Blocks.stone;
        gregtech.api.GregTechAPI.sBlockCasings2 = net.minecraft.init.Blocks.stone;
        gregtech.api.GregTechAPI.sBlockCasings4 = net.minecraft.init.Blocks.stone;
        gregtech.api.GregTechAPI.sBlockCasings10 = net.minecraft.init.Blocks.stone;
        gregtech.api.GregTechAPI.sBlockSheetmetalGT = net.minecraft.init.Blocks.stone;

        try {
            Field fBlockHint = StructureLib.class.getDeclaredField("blockHint");
            fBlockHint.setAccessible(true);
            if (fBlockHint.get(null) == null) {
                fBlockHint.set(null, net.minecraft.init.Blocks.stone);
            }
        } catch (Throwable ignored) {}

        try {
            Field fGlassList = gregtech.api.util.GlassTier.class.getDeclaredField("glassList");
            fGlassList.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<org.apache.commons.lang3.tuple.Pair<net.minecraft.block.Block, Integer>> list = (List<org.apache.commons.lang3.tuple.Pair<net.minecraft.block.Block, Integer>>) fGlassList
                .get(null);
            if (list != null && list.isEmpty()) {
                list.add(org.apache.commons.lang3.tuple.Pair.of(net.minecraft.init.Blocks.glass, 0));
            }
        } catch (Throwable ignored) {}

        try {
            if (GTMod.proxy == null) {
                GTMod.proxy = Mockito.mock(GTProxy.class);
            }
            if (GTMod.proxy.powerfailTracker == null) {
                GTMod.proxy.powerfailTracker = Mockito.mock(GTPowerfailTracker.class);
            }
        } catch (Throwable ignored) {}
    }

    @Test
    void testPressurizedHeatExchangerStructureErrors() {
        MTEPressurizedHeatExchanger phe = new MTEPressurizedHeatExchanger("test_phe");
        List<StructureError> errors = new ArrayList<>();
        IGregTechTileEntity mockBase = Mockito.mock(IGregTechTileEntity.class);
        World mockWorld = Mockito.mock(World.class);
        Mockito.when(mockBase.getWorld())
            .thenReturn(mockWorld);
        Mockito.when(mockWorld.blockExists(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt()))
            .thenReturn(true);
        phe.setBaseMetaTileEntity(mockBase);

        phe.checkMachine(mockBase, null, errors);
        assertFalse(errors.isEmpty(), "PHE should report structure errors when unformed");
    }

    @Test
    void testPressurizedDegasserStructureErrors() {
        MTEPressurizedDegasser degasser = new MTEPressurizedDegasser("test_degasser");
        List<StructureError> errors = new ArrayList<>();
        IGregTechTileEntity mockBase = Mockito.mock(IGregTechTileEntity.class);
        World mockWorld = Mockito.mock(World.class);
        Mockito.when(mockBase.getWorld())
            .thenReturn(mockWorld);
        Mockito.when(mockWorld.blockExists(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt()))
            .thenReturn(true);
        degasser.setBaseMetaTileEntity(mockBase);

        degasser.checkMachine(mockBase, null, errors);
        assertFalse(errors.isEmpty(), "Degasser should report structure errors when unformed");
    }

    @Test
    void testCoolantBathStructureErrors() {
        MTECoolantBath bath = new MTECoolantBath("test_bath");
        List<StructureError> errors = new ArrayList<>();
        IGregTechTileEntity mockBase = Mockito.mock(IGregTechTileEntity.class);
        World mockWorld = Mockito.mock(World.class);
        Mockito.when(mockBase.getWorld())
            .thenReturn(mockWorld);
        Mockito.when(mockWorld.blockExists(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt()))
            .thenReturn(true);
        bath.setBaseMetaTileEntity(mockBase);

        bath.checkMachine(mockBase, null, errors);
        assertFalse(errors.isEmpty(), "Coolant Bath should report structure errors when unformed");
    }

    @Test
    void testCoolantPumpStructureErrors() {
        MTECoolantPump pump = new MTECoolantPump("test_pump");
        List<StructureError> errors = new ArrayList<>();
        IGregTechTileEntity mockBase = Mockito.mock(IGregTechTileEntity.class);
        World mockWorld = Mockito.mock(World.class);
        Mockito.when(mockBase.getWorld())
            .thenReturn(mockWorld);
        Mockito.when(mockWorld.blockExists(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt()))
            .thenReturn(true);
        pump.setBaseMetaTileEntity(mockBase);

        pump.checkMachine(mockBase, null, errors);
        assertFalse(errors.isEmpty(), "Coolant Pump should report structure errors when unformed (missing tank)");
        boolean hasTankError = errors.stream()
            .anyMatch(
                e -> e.getDisplayString() != null && (e.getDisplayString()
                    .contains("coolantloops.structure.error.pump.missing_or_mismatched_tank")
                    || e.getDisplayString()
                        .toLowerCase()
                        .contains("railcraft tank")));
        assertTrue(hasTankError, "Pump structure errors should include missing or mismatched tank");
    }

    @Test
    void testStructureErrorLocalizationKeysExist() {
        String[] keys = new String[] { "coolantloops.structure.error.missing_primary_inlet",
            "coolantloops.structure.error.missing_primary_outlet",
            "coolantloops.structure.error.single_amp_energy_required",
            "coolantloops.structure.error.pump.missing_or_mismatched_tank",
            "coolantloops.structure.error.pump.corner_frame", "coolantloops.structure.error.pump.casing_req",
            "coolantloops.structure.error.pump.invalid_hatch", "coolantloops.structure.error.missing_suction_hatch",
            "coolantloops.structure.error.missing_discharge_hatch",
            "coolantloops.structure.error.duplicate_pump_hatches" };
        for (String key : keys) {
            String val = com.gtnewhorizons.coolantloops.common.util.CoolantLocalization.get(key);
            assertNotNull(val, "Key should exist in lang file: " + key);
            assertNotEquals(key, val, "Key should be translated: " + key);
        }
    }
}
