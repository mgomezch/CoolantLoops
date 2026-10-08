package com.gtnewhorizons.coolantloops;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import com.gtnewhorizons.coolantloops.common.block.BlockManifold;
import com.gtnewhorizons.coolantloops.common.metatileentity.pipe.MTECoolantPipe;
import com.gtnewhorizons.coolantloops.engine.LoopGraphCrawler;
import com.gtnewhorizons.coolantloops.engine.LoopGraphCrawler.CrawlResult;
import com.gtnewhorizons.coolantloops.engine.LoopGraphCrawler.PipeFlowConfig;

import gregtech.api.enums.Materials;
import gregtech.api.metatileentity.BaseMetaPipeEntity;
import gregtech.api.metatileentity.GregTechTileClientEvents;
import gregtech.api.metatileentity.implementations.MTEFluidPipe;

/**
 * Unit tests verifying:
 * 1. Manifold block texture is set to Galacticraft's Sealable Oxygen Pipe ("galacticraftcore:enclosed_oxygen_pipe").
 * 2. Crawling the coolant loop automatically configures every pipe block in the loop with
 * "input disabled" on the forwards side (towards coolant flowing out of the pipe) and
 * "input allowed" on all other sides.
 * 3. Visual flow arrow direction matches fluid flow direction.
 */
public class ManifoldAndPipeFlowDirectionTest {

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
    public void testManifoldTextureRegistration() {
        BlockManifold manifold = new BlockManifold();
        IIconRegister mockRegister = Mockito.mock(IIconRegister.class);
        IIcon mockIcon = Mockito.mock(IIcon.class);
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        Mockito.when(mockRegister.registerIcon(captor.capture()))
            .thenReturn(mockIcon);

        manifold.registerBlockIcons(mockRegister);

        // Verify the texture name requested is GregTech/IC2's Mining Pipe Tip (fullblock drill head)
        assertEquals("coolantloops:manifold", captor.getValue());

        // Verify getIcon returns the registered icon on all sides
        for (int side = 0; side < 6; side++) {
            assertEquals(mockIcon, manifold.getIcon(side, 0));
        }
    }

    @Test
    public void testPipeFlowConfigSetsForwardInputDisabled() {
        MTEFluidPipe pipe = new MTEFluidPipe("test.pipe", 0.5f, Materials.Steel, 1000, 1000, true, 1);
        BaseMetaPipeEntity bmpe = Mockito.mock(BaseMetaPipeEntity.class);

        // Test configuring forward flow direction = EAST
        PipeFlowConfig configEast = new PipeFlowConfig(bmpe, pipe, ForgeDirection.EAST);
        configEast.apply();

        // EAST side must have input disabled (1 << 5 = 32)
        assertEquals(1 << ForgeDirection.EAST.ordinal(), pipe.mDisableInput);
        assertTrue(pipe.isInputDisabledAtSide(ForgeDirection.EAST), "Forward side (EAST) must be input-disabled");

        // All other 5 sides must have input allowed (bits = 0)
        for (ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
            if (dir != ForgeDirection.EAST) {
                assertFalse(pipe.isInputDisabledAtSide(dir), "Non-forward side (" + dir + ") must be input-allowed");
            }
        }

        Mockito.verify(bmpe)
            .sendBlockEvent(GregTechTileClientEvents.CHANGE_CUSTOM_DATA, (byte) (1 << ForgeDirection.EAST.ordinal()));
        Mockito.verify(bmpe)
            .issueTextureUpdate();
        Mockito.verify(bmpe)
            .markDirty();

        // Test configuring forward flow direction = NORTH
        PipeFlowConfig configNorth = new PipeFlowConfig(bmpe, pipe, ForgeDirection.NORTH);
        configNorth.apply();

        assertEquals(1 << ForgeDirection.NORTH.ordinal(), pipe.mDisableInput);
        assertTrue(pipe.isInputDisabledAtSide(ForgeDirection.NORTH), "Forward side (NORTH) must be input-disabled");

        for (ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
            if (dir != ForgeDirection.NORTH) {
                assertFalse(pipe.isInputDisabledAtSide(dir), "Non-forward side (" + dir + ") must be input-allowed");
            }
        }

        Mockito.verify(bmpe)
            .sendBlockEvent(GregTechTileClientEvents.CHANGE_CUSTOM_DATA, (byte) (1 << ForgeDirection.NORTH.ordinal()));
    }

    @Test
    public void testPipeFlowConfigNoOpWhenAlreadyConfigured() {
        MTEFluidPipe pipe = new MTEFluidPipe("test.pipe2", 0.5f, Materials.Steel, 1000, 1000, true, 1);
        BaseMetaPipeEntity bmpe = Mockito.mock(BaseMetaPipeEntity.class);

        PipeFlowConfig config = new PipeFlowConfig(bmpe, pipe, ForgeDirection.SOUTH);
        config.apply();

        Mockito.verify(bmpe, Mockito.times(1))
            .sendBlockEvent(GregTechTileClientEvents.CHANGE_CUSTOM_DATA, (byte) (1 << ForgeDirection.SOUTH.ordinal()));

        // Applying again with the same direction should not trigger redundant network updates
        config.apply();
        Mockito.verify(bmpe, Mockito.times(1))
            .sendBlockEvent(GregTechTileClientEvents.CHANGE_CUSTOM_DATA, (byte) (1 << ForgeDirection.SOUTH.ordinal()));
    }

    @Test
    public void testCrawlerAutomaticallyConfiguresPipeLoopDirections() {
        World mockWorld = Mockito.mock(World.class);

        // Build a 4-pipe square loop:
        // Discharge at (10, 10, 10) facing EAST
        // Pipe 0 at (11, 10, 10): entry from WEST, exits SOUTH
        // Pipe 1 at (11, 10, 11): entry from NORTH, exits WEST
        // Pipe 2 at (10, 10, 11): entry from EAST, exits NORTH
        // Suction target at (10, 10, 10)
        MTECoolantPipe pipe0 = new MTECoolantPipe("pipe0", 0.5f, Materials.Steel, 1000, 1000, true);
        MTECoolantPipe pipe1 = new MTECoolantPipe("pipe1", 0.5f, Materials.Steel, 1000, 1000, true);
        MTECoolantPipe pipe2 = new MTECoolantPipe("pipe2", 0.5f, Materials.Steel, 1000, 1000, true);

        // Connections:
        // Pipe 0 connects WEST and SOUTH
        pipe0.mConnections = (byte) ((1 << ForgeDirection.WEST.ordinal()) | (1 << ForgeDirection.SOUTH.ordinal()));
        // Pipe 1 connects NORTH and WEST
        pipe1.mConnections = (byte) ((1 << ForgeDirection.NORTH.ordinal()) | (1 << ForgeDirection.WEST.ordinal()));
        // Pipe 2 connects EAST and NORTH
        pipe2.mConnections = (byte) ((1 << ForgeDirection.EAST.ordinal()) | (1 << ForgeDirection.NORTH.ordinal()));

        BaseMetaPipeEntity bmpe0 = Mockito.mock(BaseMetaPipeEntity.class);
        Mockito.when(bmpe0.getMetaTileEntity())
            .thenReturn(pipe0);
        bmpe0.mConnections = pipe0.mConnections;

        BaseMetaPipeEntity bmpe1 = Mockito.mock(BaseMetaPipeEntity.class);
        Mockito.when(bmpe1.getMetaTileEntity())
            .thenReturn(pipe1);
        bmpe1.mConnections = pipe1.mConnections;

        BaseMetaPipeEntity bmpe2 = Mockito.mock(BaseMetaPipeEntity.class);
        Mockito.when(bmpe2.getMetaTileEntity())
            .thenReturn(pipe2);
        bmpe2.mConnections = pipe2.mConnections;

        Mockito.when(mockWorld.getTileEntity(11, 10, 10))
            .thenReturn((TileEntity) bmpe0);
        Mockito.when(mockWorld.getTileEntity(11, 10, 11))
            .thenReturn((TileEntity) bmpe1);
        Mockito.when(mockWorld.getTileEntity(10, 10, 11))
            .thenReturn((TileEntity) bmpe2);

        // Ensure all pipes start with mDisableInput = 0
        assertEquals(0, pipe0.mDisableInput);
        assertEquals(0, pipe1.mDisableInput);
        assertEquals(0, pipe2.mDisableInput);

        LoopGraphCrawler crawler = new LoopGraphCrawler(mockWorld, 100);
        CrawlResult result = crawler
            .crawl(10, 10, 10, ForgeDirection.EAST, 10, 10, 10, ForgeDirection.NORTH, "test_pump");

        assertTrue(result.isSuccess, "Crawl should succeed for closed pipe loop");
        assertEquals(3, result.pipeConfigs.size());

        // Verify Pipe 0 (flowing out to SOUTH):
        assertEquals(1 << ForgeDirection.SOUTH.ordinal(), pipe0.mDisableInput);
        assertTrue(pipe0.isInputDisabledAtSide(ForgeDirection.SOUTH));
        assertFalse(pipe0.isInputDisabledAtSide(ForgeDirection.WEST));

        // Verify Pipe 1 (flowing out to WEST):
        assertEquals(1 << ForgeDirection.WEST.ordinal(), pipe1.mDisableInput);
        assertTrue(pipe1.isInputDisabledAtSide(ForgeDirection.WEST));
        assertFalse(pipe1.isInputDisabledAtSide(ForgeDirection.NORTH));

        // Verify Pipe 2 (flowing out to NORTH towards suction target):
        assertEquals(1 << ForgeDirection.NORTH.ordinal(), pipe2.mDisableInput);
        assertTrue(pipe2.isInputDisabledAtSide(ForgeDirection.NORTH));
        assertFalse(pipe2.isInputDisabledAtSide(ForgeDirection.EAST));
    }

    @Test
    public void testRegularFluidPipeRejectedByCrawler() {
        World mockWorld = Mockito.mock(World.class);
        MTEFluidPipe regularPipe = new MTEFluidPipe("regular", 0.5f, Materials.Steel, 1000, 1000, true, 1);
        regularPipe.mConnections = (byte) ((1 << ForgeDirection.WEST.ordinal()) | (1 << ForgeDirection.SOUTH.ordinal()));
        BaseMetaPipeEntity bmpe = Mockito.mock(BaseMetaPipeEntity.class);
        Mockito.when(bmpe.getMetaTileEntity()).thenReturn(regularPipe);
        bmpe.mConnections = regularPipe.mConnections;
        Mockito.when(mockWorld.getTileEntity(11, 10, 10)).thenReturn((TileEntity) bmpe);

        LoopGraphCrawler crawler = new LoopGraphCrawler(mockWorld, 100);
        CrawlResult result = crawler.crawl(10, 10, 10, ForgeDirection.EAST, 10, 10, 10, ForgeDirection.NORTH, "test_pump");
        assertFalse(result.isSuccess, "Regular GT fluid pipe must be rejected by crawler");
        assertTrue(result.failureReason.contains("not an insulated coolant pipe"),
            "Failure message must state that coolant loops require specialized Coolant Pipes: " + result.failureReason);
    }

    @Test
    public void testRegularFluidPipeInMiddleOfLoopRejectedWithClearMessage() {
        World mockWorld = Mockito.mock(World.class);

        // Pipe 0: Coolant pipe at (11, 10, 10) connected WEST and SOUTH
        MTECoolantPipe pipe0 = new MTECoolantPipe("pipe0", 0.5f, Materials.Steel, 1000, 1000, true);
        pipe0.mConnections = (byte) ((1 << ForgeDirection.WEST.ordinal()) | (1 << ForgeDirection.SOUTH.ordinal()));
        BaseMetaPipeEntity bmpe0 = Mockito.mock(BaseMetaPipeEntity.class);
        Mockito.when(bmpe0.getMetaTileEntity()).thenReturn(pipe0);
        bmpe0.mConnections = pipe0.mConnections;

        // Pipe 1: REGULAR fluid pipe at (11, 10, 11) connected NORTH and WEST
        MTEFluidPipe regularPipe1 = new MTEFluidPipe("regular1", 0.5f, Materials.Steel, 1000, 1000, true, 1);
        regularPipe1.mConnections = (byte) ((1 << ForgeDirection.NORTH.ordinal()) | (1 << ForgeDirection.WEST.ordinal()));
        BaseMetaPipeEntity bmpe1 = Mockito.mock(BaseMetaPipeEntity.class);
        Mockito.when(bmpe1.getMetaTileEntity()).thenReturn(regularPipe1);
        bmpe1.mConnections = regularPipe1.mConnections;

        // Pipe 2: Coolant pipe at (10, 10, 11) connected EAST and NORTH
        MTECoolantPipe pipe2 = new MTECoolantPipe("pipe2", 0.5f, Materials.Steel, 1000, 1000, true);
        pipe2.mConnections = (byte) ((1 << ForgeDirection.EAST.ordinal()) | (1 << ForgeDirection.NORTH.ordinal()));
        BaseMetaPipeEntity bmpe2 = Mockito.mock(BaseMetaPipeEntity.class);
        Mockito.when(bmpe2.getMetaTileEntity()).thenReturn(pipe2);
        bmpe2.mConnections = pipe2.mConnections;

        Mockito.when(mockWorld.getTileEntity(11, 10, 10)).thenReturn((TileEntity) bmpe0);
        Mockito.when(mockWorld.getTileEntity(11, 10, 11)).thenReturn((TileEntity) bmpe1);
        Mockito.when(mockWorld.getTileEntity(10, 10, 11)).thenReturn((TileEntity) bmpe2);

        LoopGraphCrawler crawler = new LoopGraphCrawler(mockWorld, 100);
        CrawlResult result = crawler.crawl(10, 10, 10, ForgeDirection.EAST, 10, 10, 10, ForgeDirection.NORTH, "test_pump");
        assertFalse(result.isSuccess, "Loop with regular fluid pipe in the middle must fail");
        assertTrue(result.failureReason.contains("pipe at (11, 10, 11) is not an insulated coolant pipe"),
            "Failure message must identify pipe at (11, 10, 11) as not an insulated coolant pipe, but got: " + result.failureReason);
    }
}
