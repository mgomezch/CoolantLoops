package com.gtnewhorizons.coolantloops;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.gtnewhorizons.coolantloops.common.block.BlockLoopInstrument;
import com.gtnewhorizons.coolantloops.common.opencomputers.DriverLoopInstrument;
import com.gtnewhorizons.coolantloops.common.opencomputers.LoopInstrumentEnvironment;
import com.gtnewhorizons.coolantloops.common.tileentity.TileEntityLoopInstrument;
import com.gtnewhorizons.coolantloops.common.tileentity.TileEntityLoopInstrument.TrackedMetric;
import com.gtnewhorizons.coolantloops.engine.CoolantFluidProperty;
import com.gtnewhorizons.coolantloops.engine.LoopSegment;
import com.gtnewhorizons.modularui.api.screen.ModularWindow;
import com.gtnewhorizons.modularui.api.screen.UIBuildContext;

import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.ManagedEnvironment;

class PressurizedInstrumentationComputerTest {

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
            TileEntity.addMapping(TileEntityLoopInstrument.class, "coolantloops.loop_instrument");
        } catch (Throwable ignored) {}
    }

    @Test
    void testBlockNamingAndTexturesExist() {
        BlockLoopInstrument block = new BlockLoopInstrument();
        assertEquals(
            "coolantloops.instrument",
            block.getUnlocalizedName()
                .replace("tile.", ""));

        // Composed textures must exist on disk
        File texDir = new File("src/main/resources/assets/coolantloops/textures/blocks");
        File sideTex = new File(texDir, "instrument_side.png");
        File topTex = new File(texDir, "instrument_top.png");
        File topMeta = new File(texDir, "instrument_top.png.mcmeta");
        File frontTex = new File(texDir, "instrument_front.png");

        assertTrue(sideTex.exists(), "instrument_side.png (Steel Reinforced Block) must exist");
        assertTrue(topTex.exists(), "instrument_top.png (Monitor Cover overlay) must exist");
        assertTrue(topMeta.exists(), "instrument_top.png.mcmeta must exist");
        assertTrue(frontTex.exists(), "instrument_front.png (Red dot center overlay) must exist");
    }

    @Test
    void testFacingIsolatedRedstoneOutput() {
        BlockLoopInstrument block = new BlockLoopInstrument();
        World mockWorld = Mockito.mock(World.class);
        TileEntityLoopInstrument te = new TileEntityLoopInstrument();
        te.setFacing(ForgeDirection.SOUTH);

        Mockito.when(mockWorld.getTileEntity(10, 20, 30))
            .thenReturn(te);

        LoopSegment seg = new LoopSegment("seg", 1.0, 0.1, 0.000045, 0.05, 50.0, 1000.0, 1000.0);
        te.setTrackedMetric(TrackedMetric.TEMPERATURE);
        te.processThermalExchange(0.01, 1.0, CoolantFluidProperty.WATER, seg);

        assertEquals(15, te.getRedstoneOutput());
        assertEquals(255, te.getFineOutputStrength());

        // In Minecraft: neighbor at SOUTH (ordinal 3) queries side=NORTH (ordinal 2)
        // because neighbor to the south is querying power from its north side.
        assertEquals(
            15,
            block.isProvidingWeakPower(mockWorld, 10, 20, 30, ForgeDirection.NORTH.ordinal()),
            "Facing side (SOUTH) must provide redstone power");
        assertEquals(
            15,
            block.isProvidingStrongPower(mockWorld, 10, 20, 30, ForgeDirection.NORTH.ordinal()),
            "Facing side (SOUTH) must provide strong redstone power");

        // All other 5 sides MUST return 0
        for (ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
            if (dir != ForgeDirection.NORTH) {
                assertEquals(
                    0,
                    block.isProvidingWeakPower(mockWorld, 10, 20, 30, dir.ordinal()),
                    "Non-facing side (" + dir.getOpposite() + ") must NOT provide power");
                assertEquals(
                    0,
                    block.isProvidingStrongPower(mockWorld, 10, 20, 30, dir.ordinal()),
                    "Non-facing side (" + dir.getOpposite() + ") must NOT provide strong power");
            }
        }
    }

    @Test
    void testTrackedMetricCyclingWithScrewdriver() {
        TileEntityLoopInstrument te = new TileEntityLoopInstrument();
        te.setTrackedMetric(TrackedMetric.FLOW_RATE);

        assertEquals(TrackedMetric.FLOW_RATE, te.getTrackedMetric());

        te.cycleMetric(1);
        assertEquals(TrackedMetric.TEMPERATURE, te.getTrackedMetric());

        te.cycleMetric(1);
        assertEquals(TrackedMetric.PRESSURE, te.getTrackedMetric());

        te.cycleMetric(1);
        assertEquals(TrackedMetric.DISSOLVED_GAS, te.getTrackedMetric());

        te.cycleMetric(1);
        assertEquals(TrackedMetric.FLOW_RATE, te.getTrackedMetric(), "Should wrap back to FLOW_RATE");

        te.cycleMetric(-1);
        assertEquals(TrackedMetric.DISSOLVED_GAS, te.getTrackedMetric(), "Shift-cycle should reverse");
    }

    @Test
    void testModularUIWindowCreation() {
        TileEntityLoopInstrument te = new TileEntityLoopInstrument();
        EntityPlayer mockPlayer = Mockito.mock(EntityPlayer.class);
        UIBuildContext ctx = new UIBuildContext(mockPlayer);
        ModularWindow window = te.createWindow(ctx);

        assertNotNull(window, "ModularWindow must not be null");
        assertEquals(176, window.getSize().width);
        assertEquals(105, window.getSize().height);
        assertFalse(
            window.getChildren()
                .isEmpty(),
            "Window must contain UI widgets");
    }

    @Test
    void testProjectRed8BitSignalScalingAndModes() {
        TileEntityLoopInstrument te = new TileEntityLoopInstrument();
        te.setFacing(ForgeDirection.EAST);

        // 1. Connection check
        assertTrue(te.canConnectBundled(ForgeDirection.EAST.ordinal()), "Must connect on facing side (EAST)");
        for (ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
            if (dir != ForgeDirection.EAST) {
                assertFalse(te.canConnectBundled(dir.ordinal()), "Must NOT connect on non-facing side (" + dir + ")");
                assertNull(te.getBundledSignal(dir.ordinal()), "Signal on non-facing side must be null");
            }
        }

        // 2. Broadcast mode (mode 0)
        te.setBundledMode(TileEntityLoopInstrument.BUNDLED_MODE_BROADCAST);
        te.setTrackedMetric(TrackedMetric.TEMPERATURE);
        LoopSegment seg = new LoopSegment("seg", 1.0, 0.1, 0.000045, 0.05, 50.0, 1000.0, 500.0);
        te.processThermalExchange(0.01, 1.0, CoolantFluidProperty.WATER, seg);

        assertEquals(7, te.getRedstoneOutput());
        assertEquals(127, te.getFineOutputStrength()); // 500/1000 * 255 = 127.5 -> 127

        byte[] bundled = te.getBundledSignal(ForgeDirection.EAST.ordinal());
        assertNotNull(bundled);
        assertEquals(16, bundled.length);
        for (int i = 0; i < 16; i++) {
            assertEquals((byte) 127, bundled[i], "Channel " + i + " must have 8-bit broadcast signal 127");
        }

        // 3. Multi mode (mode 1)
        te.setBundledMode(TileEntityLoopInstrument.BUNDLED_MODE_MULTI);
        seg.setDissolvedGasFraction(0.40);
        te.processThermalExchange(5.0 * seg.getArea(), 1.0, CoolantFluidProperty.WATER, seg); // 5 m/s velocity
        te.setCurrentPressureBar(50.0);

        byte[] multi = te.getBundledSignal(ForgeDirection.EAST.ordinal());
        assertNotNull(multi);
        // Ch0: Flow (5 m/s / 10 m/s = 0.5 * 255 = 127.5 -> 127/128)
        assertEquals((byte) 128, multi[0]);
        // Ch1: Temp (500 / 1000 = 0.5 * 255 = 127.5 -> 127/128)
        assertEquals((byte) 128, multi[1]);
        // Ch2: Pressure (50 bar / 100 bar = 0.5 * 255 = 127.5 -> 127/128)
        assertEquals((byte) 128, multi[2]);
        // Ch3: Gas (0.40 * 255 = 102)
        assertEquals((byte) 102, multi[3]);

        // 4. Specific dye channel mode (e.g. Red = channel 14 -> mode 16)
        te.setBundledMode(16); // Channel 14
        byte[] redCh = te.getBundledSignal(ForgeDirection.EAST.ordinal());
        assertEquals((byte) 0, redCh[0]);
        assertEquals((byte) te.getFineOutputStrength(), redCh[14]);
    }

    @Test
    void testOpenComputersDriverAndCallbacks() {
        DriverLoopInstrument driver = new DriverLoopInstrument();
        assertEquals(TileEntityLoopInstrument.class, driver.getTileEntityClass());

        World mockWorld = Mockito.mock(World.class);
        TileEntityLoopInstrument te = new TileEntityLoopInstrument();
        te.setFacing(ForgeDirection.NORTH);
        te.setTrackedMetric(TrackedMetric.FLOW_RATE);
        te.setCurrentPressureBar(25.5);

        Mockito.when(mockWorld.getTileEntity(5, 6, 7))
            .thenReturn(te);

        assertTrue(driver.worksWith(mockWorld, 5, 6, 7, ForgeDirection.UP));

        ManagedEnvironment env = driver.createEnvironment(mockWorld, 5, 6, 7, ForgeDirection.UP);
        assertNotNull(env);
        assertTrue(env instanceof LoopInstrumentEnvironment);

        LoopInstrumentEnvironment ocEnv = (LoopInstrumentEnvironment) env;
        assertEquals("pressurized_instrumentation_computer", ocEnv.preferredName());

        Context mockContext = Mockito.mock(Context.class);
        Arguments mockArgs = Mockito.mock(Arguments.class);

        // Test getLoopTelemetry
        Object[] telemetryRes = ocEnv.getLoopTelemetry(mockContext, mockArgs);
        assertNotNull(telemetryRes);
        assertTrue(telemetryRes[0] instanceof Map);
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) telemetryRes[0];
        assertEquals("FLOW_RATE", map.get("trackedMetric"));
        assertEquals("NORTH", map.get("facing"));
        assertEquals(25.5, (Double) map.get("pressureBar"), 1e-4);

        // Test setTrackedMetric string
        Mockito.when(mockArgs.isString(0))
            .thenReturn(true);
        Mockito.when(mockArgs.checkString(0))
            .thenReturn("TEMPERATURE");
        Object[] setRes = ocEnv.setTrackedMetric(mockContext, mockArgs);
        assertEquals(Boolean.TRUE, setRes[0]);
        assertEquals(TrackedMetric.TEMPERATURE, te.getTrackedMetric());

        // Test setFacing
        Mockito.when(mockArgs.checkString(0))
            .thenReturn("WEST");
        Object[] faceRes = ocEnv.setFacing(mockContext, mockArgs);
        assertEquals(Boolean.TRUE, faceRes[0]);
        assertEquals(ForgeDirection.WEST, te.getFacing());
    }

    @Test
    void testNBTSerializationAndLegacyCompatibility() {
        TileEntityLoopInstrument te = new TileEntityLoopInstrument();
        te.setFacing(ForgeDirection.WEST);
        te.setTrackedMetric(TrackedMetric.PRESSURE);
        te.setBundledMode(TileEntityLoopInstrument.BUNDLED_MODE_MULTI);
        te.setCurrentPressureBar(42.5);

        NBTTagCompound nbt = new NBTTagCompound();
        te.writeToNBT(nbt);

        TileEntityLoopInstrument restored = new TileEntityLoopInstrument();
        restored.readFromNBT(nbt);

        assertEquals(ForgeDirection.WEST, restored.getFacing());
        assertEquals(TrackedMetric.PRESSURE, restored.getTrackedMetric());
        assertEquals(TileEntityLoopInstrument.BUNDLED_MODE_MULTI, restored.getBundledMode());
        assertEquals(42.5, restored.getCurrentPressureBar(), 1e-4);

        // Legacy compatibility test: "type" key
        NBTTagCompound legacyNbt = new NBTTagCompound();
        legacyNbt.setInteger("type", 1); // Old THERMOMETER
        TileEntityLoopInstrument legacyRestored = new TileEntityLoopInstrument();
        legacyRestored.readFromNBT(legacyNbt);
        assertEquals(TrackedMetric.TEMPERATURE, legacyRestored.getTrackedMetric());
    }

    @Test
    void testNetworkPacketSyncing() {
        TileEntityLoopInstrument te = new TileEntityLoopInstrument();
        te.setFacing(ForgeDirection.SOUTH);
        te.setTrackedMetric(TrackedMetric.TEMPERATURE);

        net.minecraft.network.Packet pkt = te.getDescriptionPacket();
        assertNotNull(pkt, "Description packet must not be null");
        assertTrue(pkt instanceof net.minecraft.network.play.server.S35PacketUpdateTileEntity);

        net.minecraft.network.play.server.S35PacketUpdateTileEntity s35 = (net.minecraft.network.play.server.S35PacketUpdateTileEntity) pkt;
        assertNotNull(s35.func_148857_g(), "Packet NBT tag must not be null");

        TileEntityLoopInstrument clientTe = new TileEntityLoopInstrument();
        clientTe.onDataPacket(null, s35);

        assertEquals(ForgeDirection.SOUTH, clientTe.getFacing(), "Client facing must sync from server packet");
        assertEquals(
            TrackedMetric.TEMPERATURE,
            clientTe.getTrackedMetric(),
            "Client metric must sync from server packet");
    }

    @Test
    void testFacingSideDisallowsFluidConnection() {
        TileEntityLoopInstrument te = new TileEntityLoopInstrument();
        te.setFacing(ForgeDirection.SOUTH);

        // SOUTH face is reserved for redstone output
        assertFalse(te.canFill(ForgeDirection.SOUTH, null), "Facing side (SOUTH) must NOT accept fluid");
        assertFalse(te.canDrain(ForgeDirection.SOUTH, null), "Facing side (SOUTH) must NOT drain fluid");
        assertEquals(
            0,
            te.fill(
                ForgeDirection.SOUTH,
                new net.minecraftforge.fluids.FluidStack(net.minecraftforge.fluids.FluidRegistry.WATER, 1000),
                true));
        assertEquals(0, te.getTankInfo(ForgeDirection.SOUTH).length);

        // Other 5 sides must accept fluid
        for (ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
            if (dir != ForgeDirection.SOUTH) {
                assertTrue(te.canFill(dir, null), "Non-facing side (" + dir + ") must accept fluid");
                assertTrue(te.canDrain(dir, null), "Non-facing side (" + dir + ") must drain fluid");
                assertTrue(te.getTankInfo(dir).length > 0);
            }
        }
    }

    @Test
    void testWrenchRotationAndBlockRotation() {
        BlockLoopInstrument block = new BlockLoopInstrument();
        World mockWorld = Mockito.mock(World.class);
        TileEntityLoopInstrument te = new TileEntityLoopInstrument();
        te.setFacing(ForgeDirection.NORTH);

        Mockito.when(mockWorld.getTileEntity(0, 0, 0))
            .thenReturn(te);

        // rotateBlock rotates around Y (UP)
        assertTrue(block.rotateBlock(mockWorld, 0, 0, 0, ForgeDirection.UP));
        assertEquals(ForgeDirection.EAST, te.getFacing());

        assertTrue(block.rotateBlock(mockWorld, 0, 0, 0, ForgeDirection.UP));
        assertEquals(ForgeDirection.SOUTH, te.getFacing());
    }
}
