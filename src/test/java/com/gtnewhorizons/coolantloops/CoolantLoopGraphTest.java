package com.gtnewhorizons.coolantloops;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.lang.reflect.Field;
import java.util.List;

import net.minecraft.init.Bootstrap;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.World;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.gtnewhorizons.coolantloops.common.gui.graph.CoolantLoopGraphData;
import com.gtnewhorizons.coolantloops.common.gui.graph.CoolantLoopGraphData.GraphEdge;
import com.gtnewhorizons.coolantloops.common.gui.graph.CoolantLoopGraphData.GraphNode;
import com.gtnewhorizons.coolantloops.common.gui.graph.CoolantLoopGraphWidget;
import com.gtnewhorizons.coolantloops.common.gui.graph.SugiyamaLayout;
import com.gtnewhorizons.coolantloops.common.gui.graph.SugiyamaLayout.LayoutEdge;
import com.gtnewhorizons.coolantloops.common.gui.graph.SugiyamaLayout.LayoutNode;
import com.gtnewhorizons.coolantloops.common.metatileentity.multi.MTECoolantPump;
import com.gtnewhorizons.coolantloops.common.tileentity.TileEntityManifold;
import com.gtnewhorizons.coolantloops.engine.LoopGraphCrawler;

import cpw.mods.fml.common.ICrashCallable;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.relauncher.FMLInjectionData;
import cpw.mods.fml.relauncher.FMLRelaunchLog;
import cpw.mods.fml.relauncher.Side;
import gregtech.GTMod;
import gregtech.common.GTProxy;

/**
 * Unit tests for the Coolant Loop Graph visualization:
 * - Sugiyama / DOT hierarchical DAG layout
 * - Multi-span edge dummy routing
 * - Point hit testing for nodes and thick polyline edges
 * - Graph data JSON serialization round-trip
 * - Computer Monitor cover detection on MTECoolantPump
 */
public class CoolantLoopGraphTest {

    @BeforeAll
    static void initEnvironment() {
        Thread.currentThread()
            .setName("Server thread");
        try {
            Loader mockLoader = Mockito.mock(Loader.class);
            Mockito.when(mockLoader.getCallableCrashInformation())
                .thenReturn(Mockito.mock(ICrashCallable.class));
            Field f = Loader.class.getDeclaredField("instance");
            f.setAccessible(true);
            if (f.get(null) == null) {
                f.set(null, mockLoader);
            }
        } catch (Throwable ignored) {}

        try {
            Bootstrap.func_151354_b();
        } catch (Throwable ignored) {}

        try {
            Field mcHome = FMLInjectionData.class.getDeclaredField("minecraftHome");
            mcHome.setAccessible(true);
            mcHome.set(null, new File("."));
        } catch (Throwable ignored) {}

        try {
            Field sideField = FMLRelaunchLog.class.getDeclaredField("side");
            sideField.setAccessible(true);
            sideField.set(null, Side.SERVER);
        } catch (Throwable ignored) {}

        if (GTMod.proxy == null) {
            GTMod.proxy = Mockito.mock(GTProxy.class);
        }
    }

    @Test
    public void testSugiyamaLinearChainLayout() {
        CoolantLoopGraphData data = new CoolantLoopGraphData();
        data.isFormed = true;

        GraphNode src = new GraphNode("src", "Pump Discharge", "coolant_pump", "SOURCE", 0, 0, 0);
        GraphNode dev1 = new GraphNode("dev1", "PHE 1", "heat_exchanger", "DEVICE", 10, 0, 0);
        GraphNode dev2 = new GraphNode("dev2", "Degasser", "degasser", "DEVICE", 20, 0, 0);
        GraphNode sink = new GraphNode("sink", "Pump Suction", "coolant_pump", "SINK", 30, 0, 0);

        data.nodes.add(src);
        data.nodes.add(dev1);
        data.nodes.add(dev2);
        data.nodes.add(sink);

        data.edges.add(new GraphEdge("e1", "src", "dev1"));
        data.edges.add(new GraphEdge("e2", "dev1", "dev2"));
        data.edges.add(new GraphEdge("e3", "dev2", "sink"));

        SugiyamaLayout layout = new SugiyamaLayout();
        layout.computeLayout(data);

        assertEquals(
            4,
            layout.getNodes()
                .size());
        assertEquals(
            3,
            layout.getEdges()
                .size());

        LayoutNode lSrc = layout.getNode("src");
        LayoutNode lDev1 = layout.getNode("dev1");
        LayoutNode lDev2 = layout.getNode("dev2");
        LayoutNode lSink = layout.getNode("sink");

        assertNotNull(lSrc);
        assertNotNull(lDev1);
        assertNotNull(lDev2);
        assertNotNull(lSink);

        // Strict layer ranking
        assertEquals(0, lSrc.layer);
        assertEquals(1, lDev1.layer);
        assertEquals(2, lDev2.layer);
        assertEquals(3, lSink.layer);

        // Strictly monotonic horizontal progression (left-to-right)
        assertTrue(lSrc.x < lDev1.x, "src.x < dev1.x");
        assertTrue(lDev1.x < lDev2.x, "dev1.x < dev2.x");
        assertTrue(lDev2.x < lSink.x, "dev2.x < sink.x");

        // Edge routing coordinates
        for (LayoutEdge le : layout.getEdges()) {
            assertNotNull(le.waypoints);
            assertTrue(le.waypoints.size() >= 2, "Polyline must have at least start and end points");
            assertTrue(
                le.waypoints.get(0).x < le.waypoints.get(le.waypoints.size() - 1).x,
                "Flow must route from left to right");
        }
    }

    @Test
    public void testSugiyamaParallelSplitAndMergeLayout() {
        CoolantLoopGraphData data = new CoolantLoopGraphData();
        data.isFormed = true;

        GraphNode src = new GraphNode("src", "Discharge", "coolant_pump", "SOURCE", 0, 0, 0);
        GraphNode split = new GraphNode("split", "Splitter Manifold", "manifold", "MANIFOLD_GROUP", 5, 0, 0);
        GraphNode branchA = new GraphNode("branchA", "PHE A", "heat_exchanger", "DEVICE", 10, 0, 0);
        GraphNode branchB = new GraphNode("branchB", "PHE B", "heat_exchanger", "DEVICE", 10, 5, 0);
        GraphNode merge = new GraphNode("merge", "Combiner Manifold", "manifold", "MANIFOLD_GROUP", 15, 0, 0);
        GraphNode sink = new GraphNode("sink", "Suction", "coolant_pump", "SINK", 20, 0, 0);

        data.nodes.addAll(List.of(src, split, branchA, branchB, merge, sink));

        data.edges.add(new GraphEdge("e0", "src", "split"));
        data.edges.add(new GraphEdge("e1", "split", "branchA"));
        data.edges.add(new GraphEdge("e2", "split", "branchB"));
        data.edges.add(new GraphEdge("e3", "branchA", "merge"));
        data.edges.add(new GraphEdge("e4", "branchB", "merge"));
        data.edges.add(new GraphEdge("e5", "merge", "sink"));

        SugiyamaLayout layout = new SugiyamaLayout();
        layout.computeLayout(data);

        assertEquals(
            6,
            layout.getNodes()
                .size());
        assertEquals(
            6,
            layout.getEdges()
                .size());

        LayoutNode lSplit = layout.getNode("split");
        LayoutNode lA = layout.getNode("branchA");
        LayoutNode lB = layout.getNode("branchB");
        LayoutNode lMerge = layout.getNode("merge");

        assertEquals(1, lSplit.layer);
        assertEquals(2, lA.layer);
        assertEquals(2, lB.layer);
        assertEquals(3, lMerge.layer);

        // Both parallel branches share layer 2, but must have distinct vertical tracks (no overlap)
        assertEquals(lA.x, lB.x, 1e-3, "Parallel branches in same layer share x position");
        assertNotEquals(lA.y, lB.y, "Parallel branches must be vertically separated");
        assertTrue(
            Math.abs(lA.y - lB.y) >= SugiyamaLayout.DEFAULT_NODE_HEIGHT,
            "Vertical distance must accommodate node height");
    }

    @Test
    public void testSugiyamaMultiSpanEdgeWaypoints() {
        CoolantLoopGraphData data = new CoolantLoopGraphData();
        data.isFormed = true;

        GraphNode src = new GraphNode("src", "Discharge", "coolant_pump", "SOURCE", 0, 0, 0);
        GraphNode mid1 = new GraphNode("mid1", "Device 1", "heat_exchanger", "DEVICE", 5, 0, 0);
        GraphNode mid2 = new GraphNode("mid2", "Device 2", "degasser", "DEVICE", 10, 0, 0);
        GraphNode sink = new GraphNode("sink", "Suction", "coolant_pump", "SINK", 15, 0, 0);

        data.nodes.addAll(List.of(src, mid1, mid2, sink));

        // Normal chain through mid1 and mid2
        data.edges.add(new GraphEdge("e1", "src", "mid1"));
        data.edges.add(new GraphEdge("e2", "mid1", "mid2"));
        data.edges.add(new GraphEdge("e3", "mid2", "sink"));

        // Bypass multi-span edge: spans 3 layers directly from src (layer 0) to sink (layer 3)
        GraphEdge bypass = new GraphEdge("bypass", "src", "sink");
        data.edges.add(bypass);

        SugiyamaLayout layout = new SugiyamaLayout();
        layout.computeLayout(data);

        LayoutEdge lBypass = null;
        for (LayoutEdge le : layout.getEdges()) {
            if (le.data == bypass) {
                lBypass = le;
                break;
            }
        }

        assertNotNull(lBypass);
        // Multi-span edge across 3 layers must have dummy intermediate waypoints
        assertTrue(
            lBypass.waypoints.size() > 2,
            "Multi-span edge must route through intermediate layer waypoints (points: " + lBypass.waypoints.size()
                + ")");
    }

    @Test
    public void testSugiyamaHitTesting() {
        CoolantLoopGraphData data = new CoolantLoopGraphData();
        data.isFormed = true;

        GraphNode n1 = new GraphNode("n1", "Node 1", "coolant_pump", "SOURCE", 0, 0, 0);
        GraphNode n2 = new GraphNode("n2", "Node 2", "coolant_pump", "SINK", 10, 0, 0);
        data.nodes.addAll(List.of(n1, n2));

        GraphEdge edge = new GraphEdge("e1", "n1", "n2");
        data.edges.add(edge);

        SugiyamaLayout layout = new SugiyamaLayout();
        layout.computeLayout(data);

        LayoutNode ln1 = layout.getNode("n1");
        assertNotNull(ln1);

        // Center hit test on node
        LayoutNode hitNode = layout.findNodeAt(ln1.x + ln1.width / 2.0, ln1.y + ln1.height / 2.0);
        assertSame(ln1, hitNode);

        // Far away miss test
        assertNull(layout.findNodeAt(ln1.x + 5000.0, ln1.y + 5000.0));

        // Edge hit test near midpoint between n1 and n2
        LayoutEdge le = layout.getEdges()
            .get(0);
        double midX = (le.waypoints.get(0).x + le.waypoints.get(le.waypoints.size() - 1).x) / 2.0;
        double midY = (le.waypoints.get(0).y + le.waypoints.get(le.waypoints.size() - 1).y) / 2.0;

        LayoutEdge hitEdge = layout.findEdgeAt(midX, midY, 6.0);
        assertNotNull(hitEdge, "Point directly on segment midpoint must hit edge within tolerance");

        // Edge miss test far vertically
        LayoutEdge missEdge = layout.findEdgeAt(midX, midY + 100.0, 6.0);
        assertNull(missEdge);
    }

    @Test
    public void testCoolantLoopGraphDataJsonRoundTrip() {
        CoolantLoopGraphData data = new CoolantLoopGraphData();
        data.isFormed = true;
        data.loopState = "CIRCULATING";
        data.coolantName = "Supercritical Carbon Dioxide";
        data.totalFlowRateLPerSec = 1250.5;
        data.peakPressureBar = 45.2;

        GraphNode gn = new GraphNode("p1", "Main Pump", "coolant_pump", "SOURCE", 100, 64, 200);
        gn.temperature = 185.4;
        gn.flowRateLPerSec = 1250.5;
        gn.pressureBar = 45.2;
        gn.status = "Normal";
        gn.extraStats.put("Rotor Material", "TungstenCarbide");
        gn.extraStats.put("Rotor Efficiency", "92%");
        data.nodes.add(gn);

        GraphEdge ge = new GraphEdge("e1", "p1", "sink");
        ge.pipeMaterial = "StainlessSteel";
        ge.pipeSize = "Large";
        ge.lengthM = 14.5;
        ge.diameterM = 0.076;
        ge.flowRateLPerSec = 1250.5;
        ge.flowRateLPerTick = 62.525;
        ge.flowFraction = 1.0;
        ge.velocityMPerSec = 27.5;
        ge.temperatureCelsius = 185.0;
        ge.maxTemperatureCelsius = 900.0;
        ge.pressureBar = 44.8;
        ge.maxPressureBar = 64.0;
        ge.pressureDropMbar = 125.0;
        ge.reynolds = 150000.0;
        ge.flowRegime = "Turbulent";
        data.edges.add(ge);

        String json = data.toJson();
        assertNotNull(json);
        assertTrue(json.contains("Supercritical Carbon Dioxide"));
        assertTrue(json.contains("TungstenCarbide"));

        CoolantLoopGraphData restored = CoolantLoopGraphData.fromJson(json);
        assertNotNull(restored);
        assertTrue(restored.isFormed);
        assertEquals("CIRCULATING", restored.loopState);
        assertEquals("Supercritical Carbon Dioxide", restored.coolantName);
        assertEquals(1250.5, restored.totalFlowRateLPerSec, 1e-4);
        assertEquals(45.2, restored.peakPressureBar, 1e-4);

        assertEquals(1, restored.nodes.size());
        GraphNode restoredNode = restored.nodes.get(0);
        assertEquals("p1", restoredNode.id);
        assertEquals("Main Pump", restoredNode.label);
        assertEquals("coolant_pump", restoredNode.itemType);
        assertEquals(185.4, restoredNode.temperature, 1e-4);
        assertEquals("TungstenCarbide", restoredNode.extraStats.get("Rotor Material"));

        assertEquals(1, restored.edges.size());
        GraphEdge restoredEdge = restored.edges.get(0);
        assertEquals("e1", restoredEdge.id);
        assertEquals("StainlessSteel", restoredEdge.pipeMaterial);
        assertEquals("Turbulent", restoredEdge.flowRegime);
        assertEquals(150000.0, restoredEdge.reynolds, 1e-4);
    }

    @Test
    public void testCoolantPumpMonitorCoverDetection() {
        MTECoolantPump pump = new MTECoolantPump("test_monitor_pump");

        // By default without cover or tile entity base, returns false
        assertFalse(pump.hasComputerMonitorCover());

        // Simulated cover flag enables monitor cover for headless testing / GUI inspection
        pump.setSimulatedComputerMonitorCover(true);
        assertTrue(pump.hasComputerMonitorCover());

        pump.setSimulatedComputerMonitorCover(false);
        assertFalse(pump.hasComputerMonitorCover());
    }

    @Test
    public void testEdgeKeyBasics() {
        CoolantLoopGraphData.EdgeKey key1 = new CoolantLoopGraphData.EdgeKey("hubA", "hubB", "seg1");
        CoolantLoopGraphData.EdgeKey key2 = new CoolantLoopGraphData.EdgeKey("hubA", "hubB", "seg1");
        CoolantLoopGraphData.EdgeKey key3 = new CoolantLoopGraphData.EdgeKey("hubA", "hubB", "seg2");
        CoolantLoopGraphData.EdgeKey key4 = new CoolantLoopGraphData.EdgeKey("hubA", "hubC", "seg1");

        assertEquals(key1, key2);
        assertEquals(key1.hashCode(), key2.hashCode());
        assertNotEquals(key1, key3);
        assertNotEquals(key1, key4);
        assertNotEquals(key1, null);
        assertNotEquals(key1, "string");

        assertEquals("hubA", key1.fromHubId);
        assertEquals("hubB", key1.toHubId);
        assertEquals("seg1", key1.pathId);
        assertEquals("hubA->hubB:seg1", key1.toString());

        // Null safety
        CoolantLoopGraphData.EdgeKey nullKey = new CoolantLoopGraphData.EdgeKey(null, null, null);
        assertEquals("", nullKey.fromHubId);
        assertEquals("", nullKey.toHubId);
        assertEquals("", nullKey.pathId);
        assertEquals("->:", nullKey.toString());
    }

    @Test
    public void testBlockPosCoordChunkCoordinatesConversion() {
        ChunkCoordinates cc = new ChunkCoordinates(15, 64, -25);
        LoopGraphCrawler.BlockPosCoord bpc = new LoopGraphCrawler.BlockPosCoord(cc);
        assertEquals(15, bpc.x);
        assertEquals(64, bpc.y);
        assertEquals(-25, bpc.z);

        ChunkCoordinates convertedBack = bpc.toChunkCoordinates();
        assertEquals(15, convertedBack.posX);
        assertEquals(64, convertedBack.posY);
        assertEquals(-25, convertedBack.posZ);

        LoopGraphCrawler.BlockPosCoord nullBpc = new LoopGraphCrawler.BlockPosCoord((ChunkCoordinates) null);
        assertEquals(0, nullBpc.x);
        assertEquals(0, nullBpc.y);
        assertEquals(0, nullBpc.z);
    }

    @Test
    public void testTileEntityManifoldCoordinateAccessors() {
        TileEntityManifold mf = new TileEntityManifold();
        assertNull(mf.getActivePumpPos());
        assertNull(mf.getActivePumpCoordinates());
        assertNull(mf.getActivePumpId());

        mf.setActivePumpPos(10, 20, 30);
        assertNotNull(mf.getActivePumpPos());
        assertEquals(10, mf.getActivePumpPos().x);
        assertEquals(20, mf.getActivePumpPos().y);
        assertEquals(30, mf.getActivePumpPos().z);
        assertEquals(new ChunkCoordinates(10, 20, 30), mf.getActivePumpCoordinates());
        assertEquals("pump:10:20:30", mf.getActivePumpId());

        mf.setActivePumpPos(new ChunkCoordinates(40, 50, 60));
        assertEquals(40, mf.getActivePumpPos().x);
        assertEquals(50, mf.getActivePumpPos().y);
        assertEquals(60, mf.getActivePumpPos().z);
        assertEquals(new ChunkCoordinates(40, 50, 60), mf.getActivePumpCoordinates());

        mf.setActivePumpPos((ChunkCoordinates) null);
        assertNull(mf.getActivePumpPos());
        assertNull(mf.getActivePumpCoordinates());
        assertNull(mf.getActivePumpId());
    }

    @Test
    public void testIsPumpActiveCoordinates() {
        assertFalse(LoopGraphCrawler.isPumpActive(null, 10, 20, 30));
        assertFalse(LoopGraphCrawler.isPumpActive(null, new ChunkCoordinates(10, 20, 30)));
        assertFalse(LoopGraphCrawler.isPumpActive(null, new LoopGraphCrawler.BlockPosCoord(10, 20, 30)));
        assertFalse(LoopGraphCrawler.isPumpActive(null, (String) null));
        assertFalse(LoopGraphCrawler.isPumpActive(null, (ChunkCoordinates) null));
        assertFalse(LoopGraphCrawler.isPumpActive(null, (LoopGraphCrawler.BlockPosCoord) null));

        World mockWorld = Mockito.mock(World.class);
        gregtech.api.metatileentity.BaseMetaTileEntity mockGtte = Mockito
            .mock(gregtech.api.metatileentity.BaseMetaTileEntity.class);
        MTECoolantPump mockPump = Mockito.mock(MTECoolantPump.class);

        Mockito.when(mockWorld.getTileEntity(10, 20, 30))
            .thenReturn((TileEntity) mockGtte);
        Mockito.when(mockGtte.getMetaTileEntity())
            .thenReturn(mockPump);
        Mockito.when(mockGtte.isInvalid())
            .thenReturn(false);
        Mockito.when(mockPump.getBaseMetaTileEntity())
            .thenReturn(mockGtte);
        Mockito.when(mockPump.isLoopFormed())
            .thenReturn(true);

        assertTrue(LoopGraphCrawler.isPumpActive(mockWorld, 10, 20, 30));
        assertTrue(LoopGraphCrawler.isPumpActive(mockWorld, new ChunkCoordinates(10, 20, 30)));
        assertTrue(LoopGraphCrawler.isPumpActive(mockWorld, new LoopGraphCrawler.BlockPosCoord(10, 20, 30)));
        assertTrue(LoopGraphCrawler.isPumpActive(mockWorld, "pump:10:20:30"));

        // If loop is unformed:
        Mockito.when(mockPump.isLoopFormed())
            .thenReturn(false);
        assertFalse(LoopGraphCrawler.isPumpActive(mockWorld, 10, 20, 30));
        assertFalse(LoopGraphCrawler.isPumpActive(mockWorld, new ChunkCoordinates(10, 20, 30)));
        assertFalse(LoopGraphCrawler.isPumpActive(mockWorld, new LoopGraphCrawler.BlockPosCoord(10, 20, 30)));
        assertFalse(LoopGraphCrawler.isPumpActive(mockWorld, "pump:10:20:30"));

        // Other position where tile entity is null:
        assertFalse(LoopGraphCrawler.isPumpActive(mockWorld, 99, 99, 99));
        assertFalse(LoopGraphCrawler.isPumpActive(mockWorld, new ChunkCoordinates(99, 99, 99)));
        assertFalse(LoopGraphCrawler.isPumpActive(mockWorld, new LoopGraphCrawler.BlockPosCoord(99, 99, 99)));
    }

    @Test
    public void testSugiyamaIncrementalDataUpdate() {
        SugiyamaLayout layout = new SugiyamaLayout();
        CoolantLoopGraphData data = new CoolantLoopGraphData();
        data.isFormed = true;

        GraphNode n1 = new GraphNode("n1", "Pump", "coolant_pump", "SOURCE", 0, 0, 0);
        n1.temperature = 25.0;
        GraphNode n2 = new GraphNode("n2", "Sink", "coolant_pump", "SINK", 10, 0, 0);
        n2.temperature = 40.0;
        data.nodes.add(n1);
        data.nodes.add(n2);

        GraphEdge e1 = new GraphEdge("e1", "n1", "n2");
        e1.flowRateLPerSec = 50.0;
        data.edges.add(e1);

        layout.computeLayout(data);
        assertEquals(
            2,
            layout.getNodes()
                .size());
        assertEquals(
            1,
            layout.getEdges()
                .size());
        double originalN1X = layout.getNode("n1").x;
        double originalN2X = layout.getNode("n2").x;

        // Create updated telemetry with same topology
        CoolantLoopGraphData updated = new CoolantLoopGraphData();
        updated.isFormed = true;
        GraphNode n1Up = new GraphNode("n1", "Pump", "coolant_pump", "SOURCE", 0, 0, 0);
        n1Up.temperature = 95.0;
        GraphNode n2Up = new GraphNode("n2", "Sink", "coolant_pump", "SINK", 10, 0, 0);
        n2Up.temperature = 120.0;
        updated.nodes.add(n1Up);
        updated.nodes.add(n2Up);

        GraphEdge e1Up = new GraphEdge("e1", "n1", "n2");
        e1Up.flowRateLPerSec = 75.0;
        updated.edges.add(e1Up);

        assertTrue(layout.canUpdateDataOnly(updated));
        layout.updateData(updated);

        // Positions should be completely preserved
        assertEquals(originalN1X, layout.getNode("n1").x, 0.001);
        assertEquals(originalN2X, layout.getNode("n2").x, 0.001);
        // Telemetry should be updated in place
        assertEquals(95.0, layout.getNode("n1").data.temperature, 0.001);
        assertEquals(120.0, layout.getNode("n2").data.temperature, 0.001);
        assertEquals(
            75.0,
            layout.getEdges()
                .get(0).data.flowRateLPerSec,
            0.001);

        // Topology change (extra node) should reject data-only update
        CoolantLoopGraphData changed = new CoolantLoopGraphData();
        changed.isFormed = true;
        changed.nodes.add(n1Up);
        changed.nodes.add(n2Up);
        changed.nodes.add(new GraphNode("n3", "Device", "heat_exchanger", "DEVICE", 5, 0, 0));
        changed.edges.add(e1Up);
        assertFalse(layout.canUpdateDataOnly(changed));
    }

    @Test
    public void testGraphWidgetPanZoomStatePreservation() {
        CoolantLoopGraphWidget widget = new CoolantLoopGraphWidget();
        CoolantLoopGraphData data = new CoolantLoopGraphData();
        data.isFormed = true;

        GraphNode n1 = new GraphNode("n1", "Pump", "coolant_pump", "SOURCE", 0, 0, 0);
        GraphNode n2 = new GraphNode("n2", "Sink", "coolant_pump", "SINK", 10, 0, 0);
        data.nodes.add(n1);
        data.nodes.add(n2);
        data.edges.add(new GraphEdge("e1", "n1", "n2"));

        widget.setGraphData(data);

        // User zooms and pans
        widget.resetZoom();
        assertEquals(1.0f, widget.getZoom(), 0.001f);

        widget.zoomIn();
        float zoomedIn = widget.getZoom();
        assertTrue(zoomedIn > 1.0f);

        widget.zoomAt(1.75f, 150.0f, 100.0f);
        assertEquals(1.75f, widget.getZoom(), 0.001f);

        // Simulate incoming periodic telemetry sync packet
        CoolantLoopGraphData syncPacket = new CoolantLoopGraphData();
        syncPacket.isFormed = true;
        GraphNode n1Sync = new GraphNode("n1", "Pump", "coolant_pump", "SOURCE", 0, 0, 0);
        n1Sync.temperature = 300.0;
        GraphNode n2Sync = new GraphNode("n2", "Sink", "coolant_pump", "SINK", 10, 0, 0);
        n2Sync.temperature = 180.0;
        syncPacket.nodes.add(n1Sync);
        syncPacket.nodes.add(n2Sync);
        syncPacket.edges.add(new GraphEdge("e1", "n1", "n2"));

        widget.setGraphData(syncPacket);

        // Zoom must NOT reset to default fit scale upon telemetry sync!
        assertEquals(1.75f, widget.getZoom(), 0.001f);
        assertEquals(
            300.0,
            widget.getLayout()
                .getNode("n1").data.temperature,
            0.001);

        // Manual fitToView can be called explicitly
        widget.fitToView();
        // Zoom changed due to explicit fit request
        assertNotNull(widget.getGraphData());
    }

    @Test
    public void testUnformedGraphDataAndFailureReason() {
        CoolantLoopGraphData data = new CoolantLoopGraphData();
        data.isFormed = false;
        data.failureReason = "Loop disconnect: Dead-end coolant pipe at (10, 65, 20) entering from EAST";

        String json = data.toJson();
        assertNotNull(json);

        CoolantLoopGraphData restored = CoolantLoopGraphData.fromJson(json);
        assertFalse(restored.isFormed);
        assertEquals(data.failureReason, restored.failureReason);

        CoolantLoopGraphWidget widget = new CoolantLoopGraphWidget();
        widget.setGraphData(restored);
        assertNotNull(widget.getGraphData());
        assertFalse(widget.getGraphData().isFormed);
        assertEquals(data.failureReason, widget.getGraphData().failureReason);
    }

    @Test
    public void testNodeBoundsAndAutoFitCalculation() {
        CoolantLoopGraphWidget widget = new CoolantLoopGraphWidget();
        CoolantLoopGraphData data = new CoolantLoopGraphData();
        data.isFormed = true;

        GraphNode n1 = new GraphNode("n1", "Pump", "coolant_pump", "SOURCE", 0, 0, 0);
        GraphNode n2 = new GraphNode("n2", "Sink", "coolant_pump", "SINK", 10, 0, 0);
        data.nodes.add(n1);
        data.nodes.add(n2);
        data.edges.add(new GraphEdge("e1", "n1", "n2"));

        widget.setGraphData(data);
        SugiyamaLayout layout = widget.getLayout();

        double minX = layout.getNodeMinX();
        double maxX = layout.getNodeMaxX();
        double minY = layout.getNodeMinY();
        double maxY = layout.getNodeMaxY();

        assertTrue(maxX > minX, "maxX should be greater than minX");
        assertTrue(maxY > minY, "maxY should be greater than minY");
        assertEquals(maxX - minX, layout.getNodeBoundsWidth(), 0.001);
        assertEquals(maxY - minY, layout.getNodeBoundsHeight(), 0.001);
        assertEquals((minX + maxX) / 2.0, layout.getNodeCenterX(), 0.001);
        assertEquals((minY + maxY) / 2.0, layout.getNodeCenterY(), 0.001);

        // Verify bounds tightly enclose real node cards
        for (LayoutNode node : layout.getNodes()) {
            if (!node.isDummy) {
                assertTrue(node.x >= minX, "node x should be >= minX");
                assertTrue(node.x + node.width <= maxX, "node right edge should be <= maxX");
                assertTrue(node.y >= minY, "node y should be >= minY");
                assertTrue(node.y + node.height <= maxY, "node bottom edge should be <= maxY");
            }
        }
    }

    @Test
    public void testZoomRangeExpandedAndLockToggle() {
        CoolantLoopGraphWidget widget = new CoolantLoopGraphWidget();
        assertEquals(0.001f, CoolantLoopGraphWidget.MIN_ZOOM, 0.0001f);
        assertEquals(1000.0f, CoolantLoopGraphWidget.MAX_ZOOM, 0.0001f);

        // Clamping to expanded zoom limits
        widget.zoomAt(0.00001f, 100, 100);
        assertEquals(CoolantLoopGraphWidget.MIN_ZOOM, widget.getZoom(), 0.0001f);
        assertEquals("0.10%", widget.getZoomString());

        widget.zoomAt(1500.0f, 100, 100);
        assertEquals(CoolantLoopGraphWidget.MAX_ZOOM, widget.getZoom(), 0.0001f);
        assertEquals("100000%", widget.getZoomString());

        widget.zoomAt(1.0f, 100, 100);
        assertEquals("100%", widget.getZoomString());

        widget.zoomAt(0.05f, 100, 100);
        assertEquals("5.0%", widget.getZoomString());

        // Lock toggle state
        assertFalse(widget.isAutoFitLocked());
        widget.toggleAutoFitLocked();
        assertTrue(widget.isAutoFitLocked());
        widget.toggleAutoFitLocked();
        assertFalse(widget.isAutoFitLocked());
    }
}
