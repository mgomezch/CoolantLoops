package com.gtnewhorizons.coolantloops;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.lang.reflect.Field;
import java.util.List;

import net.minecraft.init.Bootstrap;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.gtnewhorizons.coolantloops.common.gui.graph.CoolantLoopGraphData;
import com.gtnewhorizons.coolantloops.common.gui.graph.CoolantLoopGraphData.GraphEdge;
import com.gtnewhorizons.coolantloops.common.gui.graph.CoolantLoopGraphData.GraphNode;
import com.gtnewhorizons.coolantloops.common.gui.graph.SugiyamaLayout;
import com.gtnewhorizons.coolantloops.common.gui.graph.SugiyamaLayout.LayoutEdge;
import com.gtnewhorizons.coolantloops.common.gui.graph.SugiyamaLayout.LayoutNode;
import com.gtnewhorizons.coolantloops.common.metatileentity.multi.MTECoolantPump;

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
}
