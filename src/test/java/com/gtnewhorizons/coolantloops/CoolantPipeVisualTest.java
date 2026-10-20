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
        float pipeMax = 1.0F - pipeMin; // 0.75
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
        double cornerCutoutsVolume = 4.0 * (b * b) * length; // 4 * 0.0625^2 * 1.0 = 0.015625
        double expectedBeveledVolume = standardVolume - cornerCutoutsVolume; // 0.234375

        assertEquals(
            expectedBeveledVolume,
            totalVolume,
            1e-6,
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
                assertEquals(
                    0.0,
                    intersectionVolume,
                    1e-6,
                    "Sub-boxes must have zero intersection volume (no z-fighting)");
            }
        }
    }

    @Test
    public void testPipeDescriptionAndNaming() {
        MTECoolantPipe pipe = new MTECoolantPipe("test.coolantpipe", 0.50F, Materials.Steel, 240, 2500, true);
        assertEquals("Medium Steel Coolant Pipe", pipe.getLocalizedName());
        String[] desc = pipe.getDescription();
        assertTrue(desc.length >= 4, "Must contain insulated description and specs");
        assertTrue(
            desc[0]
                .contains(com.gtnewhorizons.coolantloops.common.util.CoolantLocalization.get("gt.pipe.coolant.desc")));
    }

    @Test
    public void testDistinctiveTextureOverlay() {
        MTECoolantPipe pipe = new MTECoolantPipe("test.coolantpipe", 0.50F, Materials.Steel, 240, 2500, true);
        ITexture[] textures = pipe
            .getTexture(null, net.minecraftforge.common.util.ForgeDirection.NORTH, 0, -1, false, false);
        assertNotNull(textures, "Pipe textures must not be null");
        assertTrue(textures.length >= 1, "Must contain base and optional insulation textures");
    }

    static class DummyDeviceTile extends net.minecraft.tileentity.TileEntity
        implements com.gtnewhorizons.coolantloops.engine.ICoolantLoopDevice {

        @Override
        public String getDeviceId() {
            return "dummy";
        }

        @Override
        public double getMinorLossK() {
            return 0.2;
        }

        @Override
        public double getDeviceTemperatureCelsius() {
            return 20.0;
        }

        @Override
        public void processThermalExchange(double flow, double dt,
            com.gtnewhorizons.coolantloops.engine.CoolantFluidProperty fluid,
            com.gtnewhorizons.coolantloops.engine.LoopSegment seg) {}
    }

    @Test
    public void testCoolantPipeCanConnectToPressurizedHatch() {
        MTECoolantPipe pipe = new MTECoolantPipe("test.coolantpipe", 0.50F, Materials.Steel, 240, 2500, true);
        gregtech.api.metatileentity.BaseMetaTileEntity mockGte = Mockito
            .mock(gregtech.api.metatileentity.BaseMetaTileEntity.class);
        com.gtnewhorizons.coolantloops.common.metatileentity.hatch.MTEHatchPressurizedFluid hatch = new com.gtnewhorizons.coolantloops.common.metatileentity.hatch.MTEHatchPressurizedFluid(
            "hatch",
            4,
            new String[0],
            new gregtech.api.interfaces.ITexture[0][0][0]);
        Mockito.when(mockGte.getMetaTileEntity())
            .thenReturn(hatch);

        assertTrue(
            pipe.canConnect(net.minecraftforge.common.util.ForgeDirection.WEST, mockGte),
            "MTECoolantPipe must allow connection to MTEHatchPressurizedFluid");
    }

    @Test
    public void testCoolantPipeCanConnectToManifoldAndDevices() {
        MTECoolantPipe pipe = new MTECoolantPipe("test.coolantpipe", 0.50F, Materials.Steel, 240, 2500, true);
        com.gtnewhorizons.coolantloops.common.tileentity.TileEntityManifold mockManifold = Mockito
            .mock(com.gtnewhorizons.coolantloops.common.tileentity.TileEntityManifold.class);
        assertTrue(
            pipe.canConnect(net.minecraftforge.common.util.ForgeDirection.EAST, mockManifold),
            "MTECoolantPipe must allow connection to TileEntityManifold");

        DummyDeviceTile deviceTile = new DummyDeviceTile();
        assertTrue(
            pipe.canConnect(net.minecraftforge.common.util.ForgeDirection.NORTH, deviceTile),
            "MTECoolantPipe must allow connection to ICoolantLoopDevice");
    }

    @Test
    public void testPressurizedHatchReturnsNonEmptyTankInfo() {
        com.gtnewhorizons.coolantloops.common.metatileentity.hatch.MTEHatchPressurizedFluid hatch = new com.gtnewhorizons.coolantloops.common.metatileentity.hatch.MTEHatchPressurizedFluid(
            "hatch",
            4,
            new String[0],
            new gregtech.api.interfaces.ITexture[0][0][0]);
        net.minecraftforge.fluids.FluidTankInfo[] infos = hatch
            .getTankInfo(net.minecraftforge.common.util.ForgeDirection.UNKNOWN);
        assertNotNull(infos, "Tank info array must not be null");
        assertTrue(infos.length > 0, "Tank info array must not be empty (prevents GT pipe auto-disconnect)");
        assertTrue(infos[0].capacity > 0, "Tank capacity must be positive so pipe canConnect succeeds");
    }

    @Test
    public void testCoolantPipeRejectsInstrumentFacingConnection() {
        MTECoolantPipe pipe = new MTECoolantPipe("test.coolantpipe", 0.50F, Materials.Steel, 240, 2500, true);
        com.gtnewhorizons.coolantloops.common.tileentity.TileEntityLoopInstrument inst = new com.gtnewhorizons.coolantloops.common.tileentity.TileEntityLoopInstrument();
        inst.setFacing(net.minecraftforge.common.util.ForgeDirection.SOUTH);

        // Pipe is to the SOUTH of instrument, querying towards NORTH (oppositeSide is SOUTH, which matches facing)
        assertFalse(
            pipe.canConnect(net.minecraftforge.common.util.ForgeDirection.NORTH, inst),
            "Pipe must NOT connect to instrument's facing side (SOUTH)");

        // Pipe is to the NORTH of instrument, querying towards SOUTH (oppositeSide is NORTH, not facing)
        assertTrue(
            pipe.canConnect(net.minecraftforge.common.util.ForgeDirection.SOUTH, inst),
            "Pipe must connect to instrument's non-facing side");
    }

    @Test
    public void testSolderingIronThinBezel() {
        // When holding a soldering iron, pipe thickness is 0.0625F (1/16th block)
        float b = MTECoolantPipe.getBezelSize(0.0625F);
        assertEquals(0.0F, b, 1e-6, "Bezel size must be 0 for thin wire/soldering iron mode");
    }

    @Test
    public void testManifoldIConnectableImplementation() {
        com.gtnewhorizons.coolantloops.common.tileentity.TileEntityManifold manifold = new com.gtnewhorizons.coolantloops.common.tileentity.TileEntityManifold();

        assertTrue(
            manifold instanceof gregtech.api.interfaces.metatileentity.IConnectable,
            "TileEntityManifold must implement IConnectable");

        gregtech.api.interfaces.metatileentity.IConnectable connectable = manifold;
        connectable.disconnect(net.minecraftforge.common.util.ForgeDirection.WEST);
        assertFalse(connectable.isConnectedAtSide(net.minecraftforge.common.util.ForgeDirection.WEST));
        assertFalse(manifold.isConnected(net.minecraftforge.common.util.ForgeDirection.WEST));

        int result = connectable.connect(net.minecraftforge.common.util.ForgeDirection.WEST);
        assertTrue(result > 0);
        assertTrue(connectable.isConnectedAtSide(net.minecraftforge.common.util.ForgeDirection.WEST));
        assertTrue(manifold.isConnected(net.minecraftforge.common.util.ForgeDirection.WEST));
    }

    @Test
    public void testManifoldReciprocalNeighborConnection() {
        net.minecraft.world.World mockWorld = Mockito.mock(net.minecraft.world.World.class);

        com.gtnewhorizons.coolantloops.common.tileentity.TileEntityManifold m1 = new com.gtnewhorizons.coolantloops.common.tileentity.TileEntityManifold();
        m1.xCoord = 10;
        m1.yCoord = 64;
        m1.zCoord = 10;
        m1.setWorldObj(mockWorld);

        com.gtnewhorizons.coolantloops.common.tileentity.TileEntityManifold m2 = new com.gtnewhorizons.coolantloops.common.tileentity.TileEntityManifold();
        m2.xCoord = 11;
        m2.yCoord = 64;
        m2.zCoord = 10;
        m2.setWorldObj(mockWorld);

        Mockito.when(mockWorld.getTileEntity(11, 64, 10))
            .thenReturn(m2);
        Mockito.when(mockWorld.getTileEntity(10, 64, 10))
            .thenReturn(m1);

        // Initially both connected
        assertTrue(m1.isConnected(net.minecraftforge.common.util.ForgeDirection.EAST));
        assertTrue(m2.isConnected(net.minecraftforge.common.util.ForgeDirection.WEST));

        // Toggle EAST on m1: both m1 EAST and m2 WEST should disconnect
        boolean newState = m1.toggleConnectionWithNeighbor(net.minecraftforge.common.util.ForgeDirection.EAST);
        assertFalse(newState);
        assertFalse(m1.isConnected(net.minecraftforge.common.util.ForgeDirection.EAST));
        assertFalse(m2.isConnected(net.minecraftforge.common.util.ForgeDirection.WEST));

        // Toggle again: both should reconnect
        newState = m1.toggleConnectionWithNeighbor(net.minecraftforge.common.util.ForgeDirection.EAST);
        assertTrue(newState);
        assertTrue(m1.isConnected(net.minecraftforge.common.util.ForgeDirection.EAST));
        assertTrue(m2.isConnected(net.minecraftforge.common.util.ForgeDirection.WEST));
    }

    @Test
    public void testBlockManifoldHitboxExpansion() {
        com.gtnewhorizons.coolantloops.common.block.BlockManifold block = new com.gtnewhorizons.coolantloops.common.block.BlockManifold() {

            @Override
            public boolean shouldForceFullBlockBB() {
                return true;
            }
        };

        net.minecraft.world.World mockWorld = Mockito.mock(net.minecraft.world.World.class);
        AxisAlignedBB box = block.getSelectedBoundingBoxFromPool(mockWorld, 10, 20, 30);
        assertEquals(10.0, box.minX, 1e-6);
        assertEquals(20.0, box.minY, 1e-6);
        assertEquals(30.0, box.minZ, 1e-6);
        assertEquals(11.0, box.maxX, 1e-6);
        assertEquals(21.0, box.maxY, 1e-6);
        assertEquals(31.0, box.maxZ, 1e-6);
    }

    @Test
    public void testManifoldDisallowsDirectNormalConnectionToAnotherManifold() {
        net.minecraft.world.World mockWorld = Mockito.mock(net.minecraft.world.World.class);

        com.gtnewhorizons.coolantloops.common.tileentity.TileEntityManifold m1 = new com.gtnewhorizons.coolantloops.common.tileentity.TileEntityManifold();
        m1.xCoord = 10;
        m1.yCoord = 64;
        m1.zCoord = 10;
        m1.setFacing(net.minecraftforge.common.util.ForgeDirection.NORTH); // Z normal
        m1.setWorldObj(mockWorld);

        com.gtnewhorizons.coolantloops.common.tileentity.TileEntityManifold m2 = new com.gtnewhorizons.coolantloops.common.tileentity.TileEntityManifold();
        m2.xCoord = 10;
        m2.yCoord = 64;
        m2.zCoord = 9; // NORTH of m1 (along normal axis)
        m2.setFacing(net.minecraftforge.common.util.ForgeDirection.NORTH);
        m2.setWorldObj(mockWorld);

        Mockito.when(mockWorld.getTileEntity(10, 64, 10))
            .thenReturn(m1);
        Mockito.when(mockWorld.getTileEntity(10, 64, 9))
            .thenReturn(m2);

        // NORTH is normal direction: valid connection target MUST NOT be another manifold
        assertFalse(m1.isValidConnectionTarget(net.minecraftforge.common.util.ForgeDirection.NORTH));
        assertFalse(m1.isConnectionAllowed(net.minecraftforge.common.util.ForgeDirection.NORTH));
        assertFalse(m2.isValidConnectionTarget(net.minecraftforge.common.util.ForgeDirection.SOUTH));
        assertFalse(m2.isConnectionAllowed(net.minecraftforge.common.util.ForgeDirection.SOUTH));

        // Attempting to toggle connection with wrench must fail and stay disconnected
        assertFalse(m1.toggleConnectionWithNeighbor(net.minecraftforge.common.util.ForgeDirection.NORTH));
        assertFalse(m1.isConnected(net.minecraftforge.common.util.ForgeDirection.NORTH));
        assertFalse(m2.isConnected(net.minecraftforge.common.util.ForgeDirection.SOUTH));

        // Force connection bits on both
        m1.setConnected(net.minecraftforge.common.util.ForgeDirection.NORTH, true);
        m2.setConnected(net.minecraftforge.common.util.ForgeDirection.SOUTH, true);
        assertTrue(m1.isConnected(net.minecraftforge.common.util.ForgeDirection.NORTH));
        assertTrue(m2.isConnected(net.minecraftforge.common.util.ForgeDirection.SOUTH));

        // validateConnectionsWithNeighbors must automatically retract normal connection between manifolds
        m1.validateConnectionsWithNeighbors();
        assertFalse(
            m1.isConnected(net.minecraftforge.common.util.ForgeDirection.NORTH),
            "m1 must automatically retract normal connection to m2");
        assertFalse(
            m2.isConnected(net.minecraftforge.common.util.ForgeDirection.SOUTH),
            "m2 must automatically retract normal connection to m1");
    }

    @Test
    public void testManifoldDisallowsConnectionToAirOrInvalidDevice() {
        net.minecraft.world.World mockWorld = Mockito.mock(net.minecraft.world.World.class);

        com.gtnewhorizons.coolantloops.common.tileentity.TileEntityManifold m = new com.gtnewhorizons.coolantloops.common.tileentity.TileEntityManifold();
        m.xCoord = 10;
        m.yCoord = 64;
        m.zCoord = 10;
        m.setFacing(net.minecraftforge.common.util.ForgeDirection.NORTH); // Z normal, XY in-plane
        m.setWorldObj(mockWorld);

        // In empty world, getTileEntity returns null (air)
        Mockito.when(mockWorld.getTileEntity(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt()))
            .thenReturn(null);

        // All 6 directions have no valid device to connect to
        for (net.minecraftforge.common.util.ForgeDirection dir : net.minecraftforge.common.util.ForgeDirection.VALID_DIRECTIONS) {
            assertFalse(m.isValidConnectionTarget(dir), "Air must not be valid connection target on side " + dir);
            assertFalse(m.isConnectionAllowed(dir), "Connection must not be allowed to air on side " + dir);
            assertFalse(m.toggleConnectionWithNeighbor(dir), "Wrench must refuse to extend to air on side " + dir);
            assertFalse(m.isConnected(dir));
        }

        // Force connection bits
        m.setConnections((byte) 0x3F);
        m.validateConnectionsWithNeighbors();
        assertEquals(0, m.getConnectionsMask(), "All connections must automatically retract when facing air");
    }

    @Test
    public void testManifoldAllowsNormalConnectionToCoolantPipeOnly() {
        net.minecraft.world.World mockWorld = Mockito.mock(net.minecraft.world.World.class);

        com.gtnewhorizons.coolantloops.common.tileentity.TileEntityManifold m = new com.gtnewhorizons.coolantloops.common.tileentity.TileEntityManifold();
        m.xCoord = 10;
        m.yCoord = 64;
        m.zCoord = 10;
        m.setFacing(net.minecraftforge.common.util.ForgeDirection.NORTH); // Z normal: NORTH and SOUTH are normal faces
        m.setWorldObj(mockWorld);

        // Put coolant pipe at (10, 64, 9) (NORTH)
        MTECoolantPipe pipe = new MTECoolantPipe("coolantpipe.steel", 0.5f, Materials.Steel, 240, 2500, true);
        gregtech.api.metatileentity.BaseMetaPipeEntity mockPipeTile = Mockito
            .mock(gregtech.api.metatileentity.BaseMetaPipeEntity.class);
        Mockito.when(mockPipeTile.getMetaTileEntity())
            .thenReturn(pipe);
        Mockito.when(mockWorld.getTileEntity(10, 64, 9))
            .thenReturn(mockPipeTile);

        // Put regular GT machine (not MTECoolantPipe) at (10, 64, 11) (SOUTH)
        gregtech.api.metatileentity.BaseMetaTileEntity mockMachineTile = Mockito
            .mock(gregtech.api.metatileentity.BaseMetaTileEntity.class);
        gregtech.api.metatileentity.implementations.MTEBasicMachine mockMachine = Mockito
            .mock(gregtech.api.metatileentity.implementations.MTEBasicMachine.class);
        Mockito.when(mockMachineTile.getMetaTileEntity())
            .thenReturn(mockMachine);
        Mockito.when(mockWorld.getTileEntity(10, 64, 11))
            .thenReturn(mockMachineTile);

        // NORTH (coolant pipe) must be valid
        assertTrue(m.isValidConnectionTarget(net.minecraftforge.common.util.ForgeDirection.NORTH));
        assertTrue(m.isConnectionAllowed(net.minecraftforge.common.util.ForgeDirection.NORTH));

        // SOUTH (regular machine) must be invalid
        assertFalse(m.isValidConnectionTarget(net.minecraftforge.common.util.ForgeDirection.SOUTH));
        assertFalse(m.isConnectionAllowed(net.minecraftforge.common.util.ForgeDirection.SOUTH));

        // validateConnectionsWithNeighbors must keep/enable NORTH and retract SOUTH
        m.setConnected(net.minecraftforge.common.util.ForgeDirection.SOUTH, true);
        m.validateConnectionsWithNeighbors();
        assertTrue(m.isConnected(net.minecraftforge.common.util.ForgeDirection.NORTH));
        assertFalse(m.isConnected(net.minecraftforge.common.util.ForgeDirection.SOUTH));
    }

    @Test
    public void testManifoldAdoptsCoolantPipeColor() {
        net.minecraft.world.World mockWorld = Mockito.mock(net.minecraft.world.World.class);

        com.gtnewhorizons.coolantloops.common.tileentity.TileEntityManifold m = new com.gtnewhorizons.coolantloops.common.tileentity.TileEntityManifold();
        m.xCoord = 10;
        m.yCoord = 64;
        m.zCoord = 10;
        m.setFacing(net.minecraftforge.common.util.ForgeDirection.NORTH);
        m.setWorldObj(mockWorld);

        // Initially no pipe: color multiplier is default 0xFFFFFF
        assertEquals(0xFFFFFF, m.getColorMultiplier());

        // Connect Steel pipe to NORTH
        MTECoolantPipe steelPipe = new MTECoolantPipe("coolantpipe.steel", 0.5f, Materials.Steel, 240, 2500, true);
        gregtech.api.metatileentity.BaseMetaPipeEntity mockSteelTile = Mockito
            .mock(gregtech.api.metatileentity.BaseMetaPipeEntity.class);
        Mockito.when(mockSteelTile.getMetaTileEntity())
            .thenReturn(steelPipe);
        Mockito.when(mockWorld.getTileEntity(10, 64, 9))
            .thenReturn(mockSteelTile);

        m.validateConnectionsWithNeighbors();
        assertEquals(Materials.Steel, m.getActiveMaterial());
        short[] steelRgba = Materials.Steel.mRGBa;
        int expectedSteelColor = ((steelRgba[0] & 0xFF) << 16) | ((steelRgba[1] & 0xFF) << 8) | (steelRgba[2] & 0xFF);
        assertEquals(expectedSteelColor, m.getColorMultiplier());

        // Replace with Titanium pipe
        MTECoolantPipe titaniumPipe = new MTECoolantPipe(
            "coolantpipe.titanium",
            0.5f,
            Materials.Titanium,
            480,
            5000,
            true);
        gregtech.api.metatileentity.BaseMetaPipeEntity mockTitaniumTile = Mockito
            .mock(gregtech.api.metatileentity.BaseMetaPipeEntity.class);
        Mockito.when(mockTitaniumTile.getMetaTileEntity())
            .thenReturn(titaniumPipe);
        Mockito.when(mockWorld.getTileEntity(10, 64, 9))
            .thenReturn(mockTitaniumTile);

        m.validateConnectionsWithNeighbors();
        assertEquals(Materials.Titanium, m.getActiveMaterial());
        short[] tiRgba = Materials.Titanium.mRGBa;
        int expectedTiColor = ((tiRgba[0] & 0xFF) << 16) | ((tiRgba[1] & 0xFF) << 8) | (tiRgba[2] & 0xFF);
        assertEquals(expectedTiColor, m.getColorMultiplier());

        // Disconnect pipe (air at NORTH)
        Mockito.when(mockWorld.getTileEntity(10, 64, 9))
            .thenReturn(null);
        m.validateConnectionsWithNeighbors();
        assertNull(m.getActiveMaterial());
        assertEquals(0xFFFFFF, m.getColorMultiplier());
    }

    @Test
    public void testManifoldGroupColorPropagation() {
        net.minecraft.world.World mockWorld = Mockito.mock(net.minecraft.world.World.class);

        // 1x2 in-plane manifold group (Z normal, EAST/WEST in-plane)
        com.gtnewhorizons.coolantloops.common.tileentity.TileEntityManifold m1 = new com.gtnewhorizons.coolantloops.common.tileentity.TileEntityManifold();
        m1.xCoord = 10;
        m1.yCoord = 64;
        m1.zCoord = 10;
        m1.setFacing(net.minecraftforge.common.util.ForgeDirection.NORTH);
        m1.setWorldObj(mockWorld);

        com.gtnewhorizons.coolantloops.common.tileentity.TileEntityManifold m2 = new com.gtnewhorizons.coolantloops.common.tileentity.TileEntityManifold();
        m2.xCoord = 11;
        m2.yCoord = 64;
        m2.zCoord = 10;
        m2.setFacing(net.minecraftforge.common.util.ForgeDirection.NORTH);
        m2.setWorldObj(mockWorld);

        Mockito.when(mockWorld.getTileEntity(10, 64, 10))
            .thenReturn(m1);
        Mockito.when(mockWorld.getTileEntity(11, 64, 10))
            .thenReturn(m2);

        // Steel pipe connected ONLY to m1 at NORTH (10, 64, 9)
        MTECoolantPipe pipe = new MTECoolantPipe("coolantpipe.steel", 0.5f, Materials.Steel, 240, 2500, true);
        gregtech.api.metatileentity.BaseMetaPipeEntity mockPipeTile = Mockito
            .mock(gregtech.api.metatileentity.BaseMetaPipeEntity.class);
        Mockito.when(mockPipeTile.getMetaTileEntity())
            .thenReturn(pipe);
        Mockito.when(mockWorld.getTileEntity(10, 64, 9))
            .thenReturn(mockPipeTile);

        // Validate connections
        m1.validateConnectionsWithNeighbors();
        m2.validateConnectionsWithNeighbors();

        // Both manifolds in the group must assume Steel color
        assertEquals(Materials.Steel, m1.getActiveMaterial());
        assertEquals(Materials.Steel, m2.getActiveMaterial());
        short[] steelRgba = Materials.Steel.mRGBa;
        int expectedColor = ((steelRgba[0] & 0xFF) << 16) | ((steelRgba[1] & 0xFF) << 8) | (steelRgba[2] & 0xFF);
        assertEquals(expectedColor, m1.getColorMultiplier());
        assertEquals(expectedColor, m2.getColorMultiplier());
    }
}
