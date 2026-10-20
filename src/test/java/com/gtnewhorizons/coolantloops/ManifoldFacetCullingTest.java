package com.gtnewhorizons.coolantloops;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.block.Block;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.util.ForgeDirection;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.gtnewhorizons.coolantloops.client.renderer.RenderBlockManifold;
import com.gtnewhorizons.coolantloops.common.block.BlockManifold;
import com.gtnewhorizons.coolantloops.common.tileentity.TileEntityManifold;

/**
 * Unit tests verifying that manifold model facets facing towards adjacent full blocks
 * are not incorrectly culled when they are recessed/non-boundary faces.
 */
public class ManifoldFacetCullingTest {

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

    @AfterEach
    void tearDown() {
        RenderBlockManifold.CURRENT_BOUNDS.remove();
    }

    @Test
    public void testRecessedFacetsNeverCulledAdjacentToOpaqueBlock() {
        BlockManifold manifold = new BlockManifold();
        IBlockAccess world = Mockito.mock(IBlockAccess.class);
        Block opaqueBlock = Mockito.mock(Block.class);
        Mockito.when(opaqueBlock.isOpaqueCube())
            .thenReturn(true);
        Mockito.when(world.getBlock(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt()))
            .thenReturn(opaqueBlock);

        // Recessed central plate / nozzle box entirely within (0.0, 1.0)
        AxisAlignedBB recessedBox = AxisAlignedBB.getBoundingBox(0.25, 0.25, 0.3125, 0.75, 0.75, 0.6875);
        RenderBlockManifold.CURRENT_BOUNDS.set(recessedBox);

        // All 6 faces of this recessed box face adjacent blocks, but none are at the block boundary.
        // None of them should be culled even though the neighbor in that direction is a full opaque cube.
        for (int side = 0; side < 6; side++) {
            assertTrue(
                manifold.shouldSideBeRendered(world, 10, 20, 30, side),
                "Recessed facet for side " + side + " must not be culled against opaque block");
        }
    }

    @Test
    public void testBoundaryFacetCulledAdjacentToOpaqueBlock() {
        BlockManifold manifold = new BlockManifold();
        IBlockAccess world = Mockito.mock(IBlockAccess.class);
        Block opaqueBlock = Mockito.mock(Block.class);
        Mockito.when(opaqueBlock.isOpaqueCube())
            .thenReturn(true);
        Mockito.when(world.getBlock(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt()))
            .thenReturn(opaqueBlock);

        // Box touching WEST boundary at minX = 0.0, but recessed on all other sides
        AxisAlignedBB westBoundaryBox = AxisAlignedBB.getBoundingBox(0.0, 0.25, 0.25, 0.75, 0.75, 0.75);
        RenderBlockManifold.CURRENT_BOUNDS.set(westBoundaryBox);

        // West face (side 4) touches boundary and neighbor is opaque -> culled to prevent z-fighting
        assertFalse(
            manifold.shouldSideBeRendered(world, 9, 20, 30, 4),
            "Boundary facet at minX=0.0 against opaque block must be culled");

        // Other faces are recessed -> must not be culled
        assertTrue(
            manifold.shouldSideBeRendered(world, 11, 20, 30, 5),
            "Recessed East facet (maxX=0.75) must be rendered");
        assertTrue(
            manifold.shouldSideBeRendered(world, 10, 19, 30, 0),
            "Recessed Down facet (minY=0.25) must be rendered");
        assertTrue(
            manifold.shouldSideBeRendered(world, 10, 21, 30, 1),
            "Recessed Up facet (maxY=0.75) must be rendered");
        assertTrue(
            manifold.shouldSideBeRendered(world, 10, 20, 29, 2),
            "Recessed North facet (minZ=0.25) must be rendered");
        assertTrue(
            manifold.shouldSideBeRendered(world, 10, 20, 31, 3),
            "Recessed South facet (maxZ=0.75) must be rendered");
    }

    @Test
    public void testBoundaryFacetRenderedAdjacentToTransparentOrAir() {
        BlockManifold manifold = new BlockManifold();
        IBlockAccess world = Mockito.mock(IBlockAccess.class);
        Block transparentBlock = Mockito.mock(Block.class);
        Mockito.when(transparentBlock.isOpaqueCube())
            .thenReturn(false);
        Mockito.when(world.getBlock(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt()))
            .thenReturn(transparentBlock);

        AxisAlignedBB westBoundaryBox = AxisAlignedBB.getBoundingBox(0.0, 0.25, 0.25, 0.75, 0.75, 0.75);
        RenderBlockManifold.CURRENT_BOUNDS.set(westBoundaryBox);

        // West face (side 4) touches boundary, but neighbor is transparent/air -> rendered
        assertTrue(
            manifold.shouldSideBeRendered(world, 9, 20, 30, 4),
            "Boundary facet against non-opaque block must be rendered");
    }

    @Test
    public void testBoundaryFacetCulledWhenConnectingToNeighborManifold() {
        BlockManifold manifold = new BlockManifold();
        IBlockAccess world = Mockito.mock(IBlockAccess.class);
        TileEntityManifold neighborManifold = Mockito.mock(TileEntityManifold.class);

        Mockito.when(world.getBlock(9, 20, 30))
            .thenReturn(manifold);
        Mockito.when(world.getTileEntity(9, 20, 30))
            .thenReturn(neighborManifold);

        // When testing side 4 (WEST), the opposite direction on the neighbor is EAST
        Mockito.when(neighborManifold.isConnected(ForgeDirection.EAST))
            .thenReturn(true);

        AxisAlignedBB westBoundaryBox = AxisAlignedBB.getBoundingBox(0.0, 0.25, 0.25, 0.75, 0.75, 0.75);
        RenderBlockManifold.CURRENT_BOUNDS.set(westBoundaryBox);

        assertFalse(
            manifold.shouldSideBeRendered(world, 9, 20, 30, 4),
            "Boundary facet connecting to another connected manifold should be culled for a seamless joint");

        // If neighbor is NOT connected back towards us, the boundary facet must be rendered as an end cap
        Mockito.when(neighborManifold.isConnected(ForgeDirection.EAST))
            .thenReturn(false);
        assertTrue(
            manifold.shouldSideBeRendered(world, 9, 20, 30, 4),
            "Boundary facet facing disconnected neighbor manifold must be rendered");
    }
}
