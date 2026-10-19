package com.gtnewhorizons.coolantloops.engine;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.gtnewhorizons.coolantloops.common.tileentity.TileEntityCreativeHeatReservoir;
import com.gtnewhorizons.modularui.api.screen.ModularWindow;
import com.gtnewhorizons.modularui.api.screen.UIBuildContext;

import cpw.mods.fml.common.Loader;

class CreativeHeatReservoirTest {

    @BeforeAll
    static void initEnvironment() {
        Thread.currentThread().setName("Server thread");
        try {
            Loader mockLoader = Mockito.mock(Loader.class);
            Mockito.when(mockLoader.getCallableCrashInformation())
                .thenReturn(Mockito.mock(cpw.mods.fml.common.ICrashCallable.class));
            java.lang.reflect.Field f = Loader.class.getDeclaredField("instance");
            f.setAccessible(true);
            f.set(null, mockLoader);

            try {
                java.lang.reflect.Field nm = Loader.class.getDeclaredField("namedMods");
                nm.setAccessible(true);
                nm.set(mockLoader, new java.util.HashMap<>());
            } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}

        try {
            if (net.minecraft.launchwrapper.Launch.blackboard == null) {
                net.minecraft.launchwrapper.Launch.blackboard = new java.util.HashMap<>();
            }
            net.minecraft.launchwrapper.Launch.blackboard.put("fml.deobfuscatedEnvironment", Boolean.TRUE);
        } catch (Throwable ignored) {}

        try {
            net.minecraft.init.Bootstrap.func_151354_b();
        } catch (Throwable ignored) {}

        try {
            TileEntity.addMapping(TileEntityCreativeHeatReservoir.class, "coolantloops.creative_heat_reservoir");
        } catch (Throwable ignored) {}
    }

    @Test
    void testHeatTransferToCoolant() {
        TileEntityCreativeHeatReservoir reservoir = new TileEntityCreativeHeatReservoir();
        reservoir.setTargetTemperatureCelsius(500.0);
        reservoir.setHeatTransferArea(4.0);

        LoopSegment seg = new LoopSegment("seg_0", 1.0, 0.1, 0.000045, 0.5, 100.0, 1000.0, 20.0);

        // Process heat transfer at 0.02 m^3/s for 1 second
        reservoir.processThermalExchange(0.02, 1.0, CoolantFluidProperty.WATER, seg);

        assertTrue(
            seg.getCurrentTemperatureCelsius() > 20.0,
            "Coolant temperature must rise when passing through a 500 C reservoir");
        assertTrue(
            seg.getCurrentTemperatureCelsius() < 500.0,
            "Coolant temperature cannot exceed device temperature in a single pass");
    }

    @Test
    void testCoolingHotCoolant() {
        TileEntityCreativeHeatReservoir coldSink = new TileEntityCreativeHeatReservoir();
        coldSink.setTargetTemperatureCelsius(15.0); // Cold sink at 15 C

        LoopSegment hotSeg = new LoopSegment("hot_seg", 1.0, 0.1, 0.000045, 0.5, 100.0, 1000.0, 90.0);

        coldSink.processThermalExchange(0.02, 1.0, CoolantFluidProperty.WATER, hotSeg);

        assertTrue(
            hotSeg.getCurrentTemperatureCelsius() < 90.0,
            "Coolant temperature must drop when passing through a 15 C heat sink");
        assertTrue(hotSeg.getCurrentTemperatureCelsius() > 15.0);
    }

    @Test
    void testAbsoluteZeroTemperatureClamping() {
        TileEntityCreativeHeatReservoir reservoir = new TileEntityCreativeHeatReservoir();

        // Below absolute zero
        reservoir.setTargetTemperatureCelsius(-300.0);
        assertEquals(TileEntityCreativeHeatReservoir.MIN_TEMPERATURE_CELSIUS, reservoir.getDeviceTemperatureCelsius());

        reservoir.setTargetTemperatureCelsius(-1000.0);
        assertEquals(TileEntityCreativeHeatReservoir.MIN_TEMPERATURE_CELSIUS, reservoir.getDeviceTemperatureCelsius());

        // Exactly absolute zero
        reservoir.setTargetTemperatureCelsius(-273.15);
        assertEquals(-273.15, reservoir.getDeviceTemperatureCelsius());

        // Above absolute zero
        reservoir.setTargetTemperatureCelsius(100.0);
        assertEquals(100.0, reservoir.getDeviceTemperatureCelsius());

        // NBT loading below absolute zero
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setDouble("targetTemp", -500.0);
        reservoir.readFromNBT(nbt);
        assertEquals(TileEntityCreativeHeatReservoir.MIN_TEMPERATURE_CELSIUS, reservoir.getDeviceTemperatureCelsius());
    }

    @Test
    void testHeatTransferAreaClamping() {
        TileEntityCreativeHeatReservoir reservoir = new TileEntityCreativeHeatReservoir();

        // Below minimum area
        reservoir.setHeatTransferArea(-5.0);
        assertEquals(TileEntityCreativeHeatReservoir.MIN_HEAT_TRANSFER_AREA, reservoir.getHeatTransferArea());

        reservoir.setHeatTransferArea(0.0);
        assertEquals(TileEntityCreativeHeatReservoir.MIN_HEAT_TRANSFER_AREA, reservoir.getHeatTransferArea());

        reservoir.setHeatTransferArea(0.05);
        assertEquals(TileEntityCreativeHeatReservoir.MIN_HEAT_TRANSFER_AREA, reservoir.getHeatTransferArea());

        // Valid area
        reservoir.setHeatTransferArea(4.5);
        assertEquals(4.5, reservoir.getHeatTransferArea());

        // NBT loading below minimum area
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setDouble("area", -1.0);
        reservoir.readFromNBT(nbt);
        assertEquals(TileEntityCreativeHeatReservoir.MIN_HEAT_TRANSFER_AREA, reservoir.getHeatTransferArea());
    }

    @Test
    void testAdjustTargetTemperatureAndArea() {
        TileEntityCreativeHeatReservoir reservoir = new TileEntityCreativeHeatReservoir();
        reservoir.setTargetTemperatureCelsius(300.0);
        reservoir.setHeatTransferArea(2.0);

        reservoir.adjustTargetTemperature(-10.0);
        assertEquals(290.0, reservoir.getDeviceTemperatureCelsius());

        reservoir.adjustTargetTemperature(100.0);
        assertEquals(390.0, reservoir.getDeviceTemperatureCelsius());

        // Adjust below absolute zero
        reservoir.adjustTargetTemperature(-1000.0);
        assertEquals(TileEntityCreativeHeatReservoir.MIN_TEMPERATURE_CELSIUS, reservoir.getDeviceTemperatureCelsius());

        reservoir.adjustHeatTransferArea(-1.0);
        assertEquals(1.0, reservoir.getHeatTransferArea());

        reservoir.adjustHeatTransferArea(5.0);
        assertEquals(6.0, reservoir.getHeatTransferArea());

        // Adjust below minimum area
        reservoir.adjustHeatTransferArea(-20.0);
        assertEquals(TileEntityCreativeHeatReservoir.MIN_HEAT_TRANSFER_AREA, reservoir.getHeatTransferArea());
    }

    @Test
    void testNbtRoundTrip() {
        TileEntityCreativeHeatReservoir original = new TileEntityCreativeHeatReservoir();
        original.setTargetTemperatureCelsius(450.5);
        original.setHeatTransferArea(7.25);

        NBTTagCompound tag = new NBTTagCompound();
        original.writeToNBT(tag);

        TileEntityCreativeHeatReservoir loaded = new TileEntityCreativeHeatReservoir();
        loaded.readFromNBT(tag);

        assertEquals(450.5, loaded.getDeviceTemperatureCelsius());
        assertEquals(7.25, loaded.getHeatTransferArea());
        assertEquals(original.getMinorLossK(), loaded.getMinorLossK());
    }

    @Test
    void testModularUIWindowCreation() {
        TileEntityCreativeHeatReservoir te = new TileEntityCreativeHeatReservoir();
        EntityPlayer mockPlayer = Mockito.mock(EntityPlayer.class);
        UIBuildContext ctx = new UIBuildContext(mockPlayer);
        ModularWindow window = te.createWindow(ctx);

        assertNotNull(window, "ModularWindow must not be null");
        assertEquals(176, window.getSize().width);
        assertEquals(115, window.getSize().height);
        assertFalse(window.getChildren().isEmpty(), "Window must contain UI widgets");
    }
}
