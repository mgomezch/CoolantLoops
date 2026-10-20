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

    @Test
    public void testLateralFacesOfFlangeRenderedAdjacentToNeighborWithoutBranch() {
        // Reproduces the exact bug where adjacent manifold A (extending UP and EAST)
        // and neighbor B at EAST (extending only WEST) had a see-through missing model face on A's UP flange.
        TileEntityManifold manifoldA = new TileEntityManifold();
        manifoldA.xCoord = 10;
        manifoldA.yCoord = 20;
        manifoldA.zCoord = 30;
        manifoldA.setFacing(ForgeDirection.NORTH); // Z normal, XY plane
        manifoldA.setConnected(ForgeDirection.EAST, true);
        manifoldA.setConnected(ForgeDirection.UP, true);

        TileEntityManifold manifoldB = new TileEntityManifold();
        manifoldB.xCoord = 11;
        manifoldB.yCoord = 20;
        manifoldB.zCoord = 30;
        manifoldB.setFacing(ForgeDirection.NORTH); // Z normal, XY plane
        manifoldB.setConnected(ForgeDirection.WEST, true);
        // manifoldB is NOT connected UP

        BlockManifold block = new BlockManifold();
        IBlockAccess world = Mockito.mock(IBlockAccess.class);
        Mockito.when(world.getBlock(11, 20, 30))
            .thenReturn(block);
        Mockito.when(world.getTileEntity(11, 20, 30))
            .thenReturn(manifoldB);
        Mockito.when(world.getTileEntity(10, 20, 30))
            .thenReturn(manifoldA);

        java.util.List<AxisAlignedBB> boxes = manifoldA.getComponentBoundingBoxes(0, 0, 0);
        // Box for UP flange: y extends from 0.75 to 1.0, x from 0.25 to 0.75
        AxisAlignedBB upFlangeBox = null;
        AxisAlignedBB eastFlangeBox = null;
        for (AxisAlignedBB box : boxes) {
            if (box.maxY >= 0.99 && box.minY >= 0.74) {
                upFlangeBox = box;
            }
            if (box.maxX >= 0.99 && box.minX >= 0.74) {
                eastFlangeBox = box;
            }
        }
        assertNotNull(upFlangeBox, "UP flange bounding box must exist");
        assertNotNull(eastFlangeBox, "EAST flange bounding box must exist");

        // When rendering the UP flange: side 5 (EAST) is lateral (maxX = 0.75 < 1.0)
        RenderBlockManifold.CURRENT_BOUNDS.set(upFlangeBox);
        assertTrue(
            block.shouldSideBeRendered(world, 11, 20, 30, 5),
            "Lateral EAST face of UP flange must be rendered even if EAST neighbor is a connected manifold");

        // When rendering the EAST flange: side 5 (EAST) touches boundary (maxX = 1.0) and B connects WEST
        RenderBlockManifold.CURRENT_BOUNDS.set(eastFlangeBox);
        assertFalse(
            block.shouldSideBeRendered(world, 11, 20, 30, 5),
            "Boundary EAST face connecting to neighbor WEST flange should be culled for a seamless joint");
    }

    @Test
    public void testIncompatiblePlaneOrientationsNeverCullBoundaryFacets() {
        TileEntityManifold manifoldA = new TileEntityManifold();
        manifoldA.xCoord = 10;
        manifoldA.yCoord = 20;
        manifoldA.zCoord = 30;
        manifoldA.setFacing(ForgeDirection.NORTH); // Z normal, XY plane
        manifoldA.setConnected(ForgeDirection.EAST, true);

        TileEntityManifold manifoldB = new TileEntityManifold();
        manifoldB.xCoord = 11;
        manifoldB.yCoord = 20;
        manifoldB.zCoord = 30;
        manifoldB.setFacing(ForgeDirection.UP); // Y normal, XZ plane (INCOMPATIBLE)
        manifoldB.setConnected(ForgeDirection.WEST, true);

        BlockManifold block = new BlockManifold();
        IBlockAccess world = Mockito.mock(IBlockAccess.class);
        Mockito.when(world.getBlock(11, 20, 30))
            .thenReturn(block);
        Mockito.when(world.getTileEntity(11, 20, 30))
            .thenReturn(manifoldB);
        Mockito.when(world.getTileEntity(10, 20, 30))
            .thenReturn(manifoldA);

        AxisAlignedBB eastBoundaryBox = AxisAlignedBB.getBoundingBox(0.75, 0.25, 0.3125, 1.0, 0.75, 0.6875);
        RenderBlockManifold.CURRENT_BOUNDS.set(eastBoundaryBox);

        assertTrue(
            block.shouldSideBeRendered(world, 11, 20, 30, 5),
            "Boundary facet facing incompatible orientation manifold must NEVER be culled");
    }

    @Test
    public void testIncompatibleNeighborOrientationDisallowsConnection() {
        net.minecraft.world.World world = Mockito.mock(net.minecraft.world.World.class);
        TileEntityManifold manifoldA = new TileEntityManifold();
        manifoldA.xCoord = 10;
        manifoldA.yCoord = 20;
        manifoldA.zCoord = 30;
        manifoldA.setWorldObj(world);
        manifoldA.setFacing(ForgeDirection.NORTH); // Z normal (XY plane)

        TileEntityManifold manifoldB = new TileEntityManifold();
        manifoldB.xCoord = 11;
        manifoldB.yCoord = 20;
        manifoldB.zCoord = 30;
        manifoldB.setWorldObj(world);
        manifoldB.setFacing(ForgeDirection.UP); // Y normal (XZ plane)

        Mockito.when(world.getTileEntity(10, 20, 30))
            .thenReturn(manifoldA);
        Mockito.when(world.getTileEntity(11, 20, 30))
            .thenReturn(manifoldB);

        // Connection should be disallowed in both directions
        assertFalse(manifoldA.isConnectionAllowed(ForgeDirection.EAST));
        assertFalse(manifoldB.isConnectionAllowed(ForgeDirection.WEST));

        // Attempting to toggle connection with wrench must fail and return false
        assertFalse(manifoldA.toggleConnectionWithNeighbor(ForgeDirection.EAST));
        assertFalse(manifoldA.isConnected(ForgeDirection.EAST));
        assertFalse(manifoldB.isConnected(ForgeDirection.WEST));

        // Connect method should return 0 and keep disconnected
        assertEquals(0, manifoldA.connect(ForgeDirection.EAST));
        assertFalse(manifoldA.isConnected(ForgeDirection.EAST));
    }

    @Test
    public void testOrientationChangeAutomaticallyDisconnectsIncompatibleNeighbors() {
        net.minecraft.world.World world = Mockito.mock(net.minecraft.world.World.class);
        TileEntityManifold manifoldA = new TileEntityManifold();
        manifoldA.xCoord = 10;
        manifoldA.yCoord = 20;
        manifoldA.zCoord = 30;
        manifoldA.setWorldObj(world);
        manifoldA.setFacing(ForgeDirection.NORTH); // Z normal (XY plane)

        TileEntityManifold manifoldB = new TileEntityManifold();
        manifoldB.xCoord = 11;
        manifoldB.yCoord = 20;
        manifoldB.zCoord = 30;
        manifoldB.setWorldObj(world);
        manifoldB.setFacing(ForgeDirection.NORTH); // Z normal (XY plane)

        Mockito.when(world.getTileEntity(10, 20, 30))
            .thenReturn(manifoldA);
        Mockito.when(world.getTileEntity(11, 20, 30))
            .thenReturn(manifoldB);

        // Initially compatible and connected
        manifoldA.setConnected(ForgeDirection.EAST, true);
        manifoldB.setConnected(ForgeDirection.WEST, true);
        assertTrue(manifoldA.isConnected(ForgeDirection.EAST));
        assertTrue(manifoldB.isConnected(ForgeDirection.WEST));

        // Manifold B changes orientation to UP (Y normal, XZ plane)
        manifoldB.setFacing(ForgeDirection.UP);

        // Both manifolds should now be disconnected
        assertFalse(
            manifoldB.isConnected(ForgeDirection.WEST),
            "B must be disconnected from A after orientation change");
        assertFalse(
            manifoldA.isConnected(ForgeDirection.EAST),
            "A must be disconnected from B after orientation change");
    }

    @Test
    public void testNeighborBlockChangeDisconnectsIncompatibleNeighbors() {
        net.minecraft.world.World world = Mockito.mock(net.minecraft.world.World.class);
        TileEntityManifold manifoldA = new TileEntityManifold();
        manifoldA.xCoord = 10;
        manifoldA.yCoord = 20;
        manifoldA.zCoord = 30;
        manifoldA.setWorldObj(world);
        manifoldA.setFacing(ForgeDirection.NORTH); // Z normal

        TileEntityManifold manifoldB = new TileEntityManifold();
        manifoldB.xCoord = 11;
        manifoldB.yCoord = 20;
        manifoldB.zCoord = 30;
        manifoldB.setWorldObj(world);
        manifoldB.setFacing(ForgeDirection.UP); // Y normal (incompatible)

        Mockito.when(world.getTileEntity(10, 20, 30))
            .thenReturn(manifoldA);
        Mockito.when(world.getTileEntity(11, 20, 30))
            .thenReturn(manifoldB);

        // Force connection flags
        manifoldA.setConnected(ForgeDirection.EAST, true);
        manifoldB.setConnected(ForgeDirection.WEST, true);

        BlockManifold block = new BlockManifold();
        block.onNeighborBlockChange(world, 10, 20, 30, block);

        assertFalse(manifoldA.isConnected(ForgeDirection.EAST));
        assertFalse(manifoldB.isConnected(ForgeDirection.WEST));
    }

    @Test
    public void testNonWrenchActivationReturnsFalseAndNoChat() {
        net.minecraft.world.World world = Mockito.mock(net.minecraft.world.World.class);
        TileEntityManifold manifold = new TileEntityManifold();
        manifold.xCoord = 10;
        manifold.yCoord = 20;
        manifold.zCoord = 30;
        manifold.setWorldObj(world);

        Mockito.when(world.getTileEntity(10, 20, 30))
            .thenReturn(manifold);

        BlockManifold block = new BlockManifold();
        net.minecraft.entity.player.EntityPlayer player = Mockito.mock(net.minecraft.entity.player.EntityPlayer.class);
        Mockito.when(player.getHeldItem())
            .thenReturn(null);

        boolean activated = block.onBlockActivated(world, 10, 20, 30, player, 0, 0.5f, 0.5f, 0.5f);
        assertFalse(activated, "Right-clicking without wrench must return false so blocks can be placed");
        Mockito.verify(player, Mockito.never())
            .addChatMessage(Mockito.any());
    }
}
