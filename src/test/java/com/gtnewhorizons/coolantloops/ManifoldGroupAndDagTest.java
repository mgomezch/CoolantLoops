package com.gtnewhorizons.coolantloops;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.gtnewhorizons.coolantloops.common.metatileentity.multi.MTECoolantPump;
import com.gtnewhorizons.coolantloops.common.metatileentity.pipe.MTECoolantPipe;
import com.gtnewhorizons.coolantloops.common.tileentity.TileEntityManifold;
import com.gtnewhorizons.coolantloops.engine.LoopGraphCrawler;
import com.gtnewhorizons.coolantloops.engine.LoopGraphCrawler.CrawlResult;

import gregtech.api.enums.Materials;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.BaseMetaPipeEntity;

public class ManifoldGroupAndDagTest {

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

    private static class WorldTileMap {

        final World world = Mockito.mock(World.class);
        final java.util.Map<String, TileEntity> tiles = new java.util.HashMap<>();

        public WorldTileMap() {
            Mockito.when(world.getTileEntity(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt()))
                .thenAnswer(inv -> {
                    int x = inv.getArgument(0);
                    int y = inv.getArgument(1);
                    int z = inv.getArgument(2);
                    return tiles.get(x + "," + y + "," + z);
                });
            Mockito.when(world.getBlock(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt()))
                .thenAnswer(inv -> {
                    int x = inv.getArgument(0);
                    int y = inv.getArgument(1);
                    int z = inv.getArgument(2);
                    TileEntity te = tiles.get(x + "," + y + "," + z);
                    if (te instanceof TileEntityManifold) {
                        return com.gtnewhorizons.coolantloops.common.block.ModBlocks.manifold;
                    }
                    if (te instanceof BaseMetaPipeEntity) {
                        return gregtech.api.GregTechAPI.sBlockMachines;
                    }
                    if (te instanceof IGregTechTileEntity) {
                        return gregtech.api.GregTechAPI.sBlockMachines;
                    }
                    return net.minecraft.init.Blocks.air;
                });
        }

        public void put(int x, int y, int z, TileEntity te) {
            te.xCoord = x;
            te.yCoord = y;
            te.zCoord = z;
            te.setWorldObj(world);
            tiles.put(x + "," + y + "," + z, te);
        }

        public TileEntityManifold putManifold(int x, int y, int z) {
            TileEntityManifold mf = new TileEntityManifold();
            mf.setFacing(ForgeDirection.EAST);
            put(x, y, z, mf);
            return mf;
        }

        public BaseMetaPipeEntity putPipe(int x, int y, int z, ForgeDirection... connectedSides) {
            MTECoolantPipe pipe = new MTECoolantPipe(
                "pipe_" + x + "_" + y + "_" + z,
                0.5f,
                Materials.Steel,
                1000,
                1000,
                true);
            byte conns = 0;
            for (ForgeDirection dir : connectedSides) {
                conns |= (1 << dir.ordinal());
            }
            pipe.mConnections = conns;

            BaseMetaPipeEntity bmpe = Mockito.mock(BaseMetaPipeEntity.class);
            Mockito.when(bmpe.getMetaTileEntity())
                .thenReturn(pipe);
            bmpe.mConnections = conns;
            put(x, y, z, bmpe);
            return bmpe;
        }
    }

    @Test
    public void testContiguousManifoldGroupDiscovery3D() {
        WorldTileMap map = new WorldTileMap();

        // 3 contiguous manifolds in an L-shape in 3D: (10, 10, 10), (10, 10, 11), (10, 11, 11)
        TileEntityManifold mf1 = map.putManifold(10, 10, 10);
        TileEntityManifold mf2 = map.putManifold(10, 10, 11);
        TileEntityManifold mf3 = map.putManifold(10, 11, 11);

        // An isolated manifold at (20, 20, 20)
        TileEntityManifold mfIsolated = map.putManifold(20, 20, 20);

        List<TileEntityManifold> group = mf1.findContiguousGroup();
        assertEquals(3, group.size(), "Contiguous group must find all 3 connected manifolds in 3D");
        assertTrue(group.contains(mf1));
        assertTrue(group.contains(mf2));
        assertTrue(group.contains(mf3));
        assertFalse(group.contains(mfIsolated), "Isolated manifold must not be in group");
    }

    @Test
    public void testManifoldPlaneOrientationMismatchRejectsGrouping() {
        WorldTileMap map = new WorldTileMap();

        TileEntityManifold mf1 = map.putManifold(10, 10, 10);
        mf1.setFacing(ForgeDirection.EAST); // Normal axis X (YZ plane)

        TileEntityManifold mf2 = map.putManifold(10, 10, 11);
        mf2.setFacing(ForgeDirection.NORTH); // Normal axis Z (XY plane) - plane mismatch!

        List<TileEntityManifold> group = mf1.findContiguousGroup();
        assertEquals(1, group.size(), "Manifolds with different plane orientations must not group together");
        assertTrue(group.contains(mf1));
        assertFalse(group.contains(mf2));
    }

    @Test
    public void testManifoldConnectionTogglePreventsGrouping() {
        WorldTileMap map = new WorldTileMap();

        TileEntityManifold mf1 = map.putManifold(10, 10, 10);
        mf1.setFacing(ForgeDirection.EAST);

        TileEntityManifold mf2 = map.putManifold(10, 10, 11);
        mf2.setFacing(ForgeDirection.EAST);

        // Explicitly disable connection from mf1 towards mf2 (SOUTH)
        mf1.setConnected(ForgeDirection.SOUTH, false);

        List<TileEntityManifold> group = mf1.findContiguousGroup();
        assertEquals(1, group.size(), "Manifold with toggled-off connection must not group with neighbor");
        assertTrue(group.contains(mf1));
        assertFalse(group.contains(mf2));
    }

    @Test
    public void testManifoldRejectsNonPerpendicularBranchEntry() {
        WorldTileMap map = new WorldTileMap();

        // Pump discharge at (10, 10, 10) facing EAST
        // Suction at (10, 10, 8) facing NORTH
        // Pipe 1 at (11, 10, 10) connects WEST and EAST
        map.putPipe(11, 10, 10, ForgeDirection.WEST, ForgeDirection.EAST);

        // Manifold at (12, 10, 10) with NORTH facing (normal axis Z, XY plane)
        // Entry from WEST is parallel to XY plane (not perpendicular!)
        TileEntityManifold mf = map.putManifold(12, 10, 10);
        mf.setFacing(ForgeDirection.NORTH);

        LoopGraphCrawler crawler = new LoopGraphCrawler(map.world, 100);
        CrawlResult result = crawler
            .crawl(10, 10, 10, ForgeDirection.EAST, 10, 10, 8, ForgeDirection.NORTH, "pump:10:10:10");

        assertFalse(result.isSuccess, "Crawl must fail when branch enters manifold parallel to its plane");
        assertTrue(
            result.failureReason.contains("parallel to its plane")
                || result.failureReason.contains("perpendicularly to their plane"),
            "Error message should mention perpendicularity: " + result.failureReason);
    }

    @Test
    public void testStrictAxisAlignmentLateralRejection() {
        WorldTileMap map = new WorldTileMap();

        // Pump discharge at (10, 10, 10) facing EAST
        // Suction at (10, 10, 8) facing NORTH
        // Pipe 1 at (11, 10, 10) connects WEST (from pump) and EAST (to manifold)
        map.putPipe(11, 10, 10, ForgeDirection.WEST, ForgeDirection.EAST);

        // Manifold at (12, 10, 10): flow axis is EAST-WEST (input from WEST, output to EAST)
        map.putManifold(12, 10, 10);

        // Illegal lateral pipe connected to manifold from NORTH at (12, 10, 9)
        map.putPipe(12, 10, 9, ForgeDirection.SOUTH, ForgeDirection.NORTH);

        // Valid output pipe at (13, 10, 10)
        map.putPipe(13, 10, 10, ForgeDirection.WEST, ForgeDirection.NORTH);

        LoopGraphCrawler crawler = new LoopGraphCrawler(map.world, 100);
        CrawlResult result = crawler
            .crawl(10, 10, 10, ForgeDirection.EAST, 10, 10, 8, ForgeDirection.NORTH, "pump:10:10:10");

        assertFalse(result.isSuccess, "Crawl must fail when manifold has lateral connections");
        assertTrue(
            result.failureReason.contains("unauthorized direction") || result.failureReason.contains("lateral"),
            "Error message should mention unauthorized direction: " + result.failureReason);
    }

    @Test
    public void testManifoldConflictingLoopOwnershipRejection() {
        WorldTileMap map = new WorldTileMap();

        // Foreign active pump at (50, 50, 50)
        gregtech.api.metatileentity.BaseMetaTileEntity foreignBase = Mockito
            .mock(gregtech.api.metatileentity.BaseMetaTileEntity.class);
        MTECoolantPump foreignPump = Mockito.mock(MTECoolantPump.class);
        Mockito.when(foreignPump.isLoopFormed())
            .thenReturn(true);
        Mockito.when(foreignPump.getBaseMetaTileEntity())
            .thenReturn(foreignBase);
        Mockito.when(foreignBase.getMetaTileEntity())
            .thenReturn(foreignPump);
        map.put(50, 50, 50, foreignBase);

        // Pipe from discharge
        map.putPipe(11, 10, 10, ForgeDirection.WEST, ForgeDirection.EAST);

        // Manifold at (12, 10, 10) already claimed by active foreign pump
        TileEntityManifold mf = map.putManifold(12, 10, 10);
        mf.setActivePumpId("pump:50:50:50");

        // Output pipe at (13, 10, 10) leaving EAST to (13, 10, 10)
        map.putPipe(13, 10, 10, ForgeDirection.WEST, ForgeDirection.NORTH);
        map.putPipe(13, 10, 9, ForgeDirection.SOUTH, ForgeDirection.WEST);
        map.putPipe(12, 10, 9, ForgeDirection.EAST, ForgeDirection.WEST);
        map.putPipe(11, 10, 9, ForgeDirection.EAST, ForgeDirection.WEST);
        map.putPipe(10, 10, 9, ForgeDirection.EAST, ForgeDirection.NORTH); // target suction at (10, 10, 8) facing NORTH

        LoopGraphCrawler crawler = new LoopGraphCrawler(map.world, 100);
        CrawlResult result = crawler
            .crawl(10, 10, 10, ForgeDirection.EAST, 10, 10, 8, ForgeDirection.NORTH, "pump:10:10:10");

        assertFalse(result.isSuccess, "Crawl must fail when manifold is claimed by another active pump");
        assertTrue(
            result.failureReason.contains("already part of another active coolant loop"),
            "Error message should mention already part of another active loop: " + result.failureReason);
    }

    @Test
    public void testManifoldCycleDetectionRejection() {
        WorldTileMap map = new WorldTileMap();

        // Pump discharge at (10, 10, 10) facing EAST
        // Suction at (10, 10, 8) facing NORTH
        map.putPipe(11, 10, 10, ForgeDirection.WEST, ForgeDirection.EAST);

        // Manifold Group: (12, 10, 10) and (12, 10, 11)
        // Flow axis: Input WEST, Output EAST
        map.putManifold(12, 10, 10);
        map.putManifold(12, 10, 11);

        // Output from (12, 10, 11) leaves EAST to (13, 10, 11)
        // Then loops around: (13, 10, 11) -> (13, 10, 12) -> (11, 10, 12) -> (11, 10, 11)
        // and feeds back into the input side (WEST) of manifold (12, 10, 11)!
        map.putPipe(13, 10, 11, ForgeDirection.WEST, ForgeDirection.SOUTH);
        map.putPipe(13, 10, 12, ForgeDirection.NORTH, ForgeDirection.WEST);
        map.putPipe(12, 10, 12, ForgeDirection.EAST, ForgeDirection.WEST);
        map.putPipe(11, 10, 12, ForgeDirection.EAST, ForgeDirection.NORTH);
        map.putPipe(11, 10, 11, ForgeDirection.SOUTH, ForgeDirection.EAST);

        // Output from (12, 10, 10) leaves EAST to (13, 10, 10) and heads toward suction
        map.putPipe(13, 10, 10, ForgeDirection.WEST, ForgeDirection.NORTH);
        map.putPipe(13, 10, 9, ForgeDirection.SOUTH, ForgeDirection.WEST);
        map.putPipe(12, 10, 9, ForgeDirection.EAST, ForgeDirection.WEST);
        map.putPipe(11, 10, 9, ForgeDirection.EAST, ForgeDirection.WEST);
        map.putPipe(10, 10, 9, ForgeDirection.EAST, ForgeDirection.NORTH); // targetSuctionSide=NORTH at (10, 10, 8)

        LoopGraphCrawler crawler = new LoopGraphCrawler(map.world, 100);
        CrawlResult result = crawler
            .crawl(10, 10, 10, ForgeDirection.EAST, 10, 10, 8, ForgeDirection.NORTH, "pump:10:10:10");

        assertFalse(result.isSuccess, "Crawl must fail when manifold output loops back to its input without pump");
        assertTrue(
            result.failureReason.contains("Invalid cyclic loop topology")
                && result.failureReason.contains("path from manifold output loops back to its input"),
            "Error message must specify manifold cycle: " + result.failureReason);
    }

    @Test
    public void testValidParallelBranchSplitAndMergeAsDAG() {
        WorldTileMap map = new WorldTileMap();

        // 1. Pump discharge at (10, 10, 10) facing EAST
        // Suction at (10, 10, 8) facing NORTH
        map.putPipe(11, 10, 10, ForgeDirection.WEST, ForgeDirection.EAST);

        // 2. Splitter Manifold Group: (12, 10, 10) and (12, 10, 11)
        // Axis: WEST -> EAST
        TileEntityManifold split1 = map.putManifold(12, 10, 10);
        TileEntityManifold split2 = map.putManifold(12, 10, 11);

        // 3. Branch A (from 12, 10, 10):
        // Pipe at (13, 10, 10) WEST-EAST
        map.putPipe(13, 10, 10, ForgeDirection.WEST, ForgeDirection.EAST);
        // Pipe at (14, 10, 10) WEST-EAST
        map.putPipe(14, 10, 10, ForgeDirection.WEST, ForgeDirection.EAST);

        // 4. Branch B (from 12, 10, 11):
        // Pipe at (13, 10, 11) WEST-EAST
        map.putPipe(13, 10, 11, ForgeDirection.WEST, ForgeDirection.EAST);
        // Pipe at (14, 10, 11) WEST-EAST
        map.putPipe(14, 10, 11, ForgeDirection.WEST, ForgeDirection.EAST);

        // 5. Merger Manifold Group: (15, 10, 10) and (15, 10, 11)
        // Axis: WEST -> EAST
        // Receives Branch A at (15, 10, 10) from WEST, and Branch B at (15, 10, 11) from WEST
        TileEntityManifold merge1 = map.putManifold(15, 10, 10);
        TileEntityManifold merge2 = map.putManifold(15, 10, 11);

        // 6. Return path from (15, 10, 10) leaving EAST to (16, 10, 10)
        map.putPipe(16, 10, 10, ForgeDirection.WEST, ForgeDirection.NORTH);
        map.putPipe(16, 10, 9, ForgeDirection.SOUTH, ForgeDirection.WEST);
        map.putPipe(15, 10, 9, ForgeDirection.EAST, ForgeDirection.WEST);
        map.putPipe(14, 10, 9, ForgeDirection.EAST, ForgeDirection.WEST);
        map.putPipe(13, 10, 9, ForgeDirection.EAST, ForgeDirection.WEST);
        map.putPipe(12, 10, 9, ForgeDirection.EAST, ForgeDirection.WEST);
        map.putPipe(11, 10, 9, ForgeDirection.EAST, ForgeDirection.WEST);
        map.putPipe(10, 10, 9, ForgeDirection.EAST, ForgeDirection.NORTH); // target suction at (10, 10, 8) facing NORTH

        LoopGraphCrawler crawler = new LoopGraphCrawler(map.world, 100);
        CrawlResult result = crawler
            .crawl(10, 10, 10, ForgeDirection.EAST, 10, 10, 8, ForgeDirection.NORTH, "pump:10:10:10");

        assertTrue(
            result.isSuccess,
            "Parallel split and merge through manifold groups must succeed as a valid DAG: " + result.failureReason);

        // Verify active pump ID set on all manifold blocks across both groups
        assertEquals("pump:10:10:10", split1.getActivePumpId());
        assertEquals("pump:10:10:10", split2.getActivePumpId());
        assertEquals("pump:10:10:10", merge1.getActivePumpId());
        assertEquals("pump:10:10:10", merge2.getActivePumpId());

        // Verify axes set correctly
        assertEquals(ForgeDirection.WEST, split1.getInputSide());
        assertEquals(ForgeDirection.EAST, split1.getOutputSide());
        assertEquals(ForgeDirection.WEST, merge1.getInputSide());
        assertEquals(ForgeDirection.EAST, merge1.getOutputSide());
    }
}
