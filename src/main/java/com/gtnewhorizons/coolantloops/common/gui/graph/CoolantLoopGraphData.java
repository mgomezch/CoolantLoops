package com.gtnewhorizons.coolantloops.common.gui.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import net.minecraft.item.ItemStack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.gtnewhorizons.coolantloops.common.block.ModBlocks;
import com.gtnewhorizons.coolantloops.common.metatileentity.ModMetaTileEntities;
import com.gtnewhorizons.coolantloops.common.metatileentity.hatch.MTEHatchPressurizedFluid;
import com.gtnewhorizons.coolantloops.common.metatileentity.multi.MTECoolantBath;
import com.gtnewhorizons.coolantloops.common.metatileentity.multi.MTECoolantPump;
import com.gtnewhorizons.coolantloops.common.metatileentity.multi.MTEPressurizedDegasser;
import com.gtnewhorizons.coolantloops.common.metatileentity.multi.MTEPressurizedHeatExchanger;
import com.gtnewhorizons.coolantloops.common.tileentity.TileEntityCreativeHeatReservoir;
import com.gtnewhorizons.coolantloops.common.tileentity.TileEntityLoopInstrument;
import com.gtnewhorizons.coolantloops.engine.CoolantFluidProperty;
import com.gtnewhorizons.coolantloops.engine.CoolantLoopEngine;
import com.gtnewhorizons.coolantloops.engine.ICoolantLoopDevice;
import com.gtnewhorizons.coolantloops.engine.LoopGraphCrawler;
import com.gtnewhorizons.coolantloops.engine.LoopGraphCrawler.BlockPosCoord;
import com.gtnewhorizons.coolantloops.engine.LoopGraphCrawler.CrawlResult;
import com.gtnewhorizons.coolantloops.engine.LoopGraphCrawler.FlowNode;
import com.gtnewhorizons.coolantloops.engine.LoopGraphCrawler.NodeType;
import com.gtnewhorizons.coolantloops.engine.LoopSegment;

import gregtech.api.enums.Materials;

/**
 * Serialized representation of the coolant loop's topological flow graph and telemetry.
 * Serves as the data transport between server simulation and client ModularUI graph renderer.
 */
public class CoolantLoopGraphData {

    private static final Gson GSON = new GsonBuilder().create();

    public boolean isFormed;
    public String failureReason;
    public String loopState = "EMPTY";
    public String coolantName = "None";
    public double totalFlowRateLPerSec = 0.0;
    public double peakPressureBar = 1.0;
    public List<GraphNode> nodes = new ArrayList<>();
    public List<GraphEdge> edges = new ArrayList<>();

    public CoolantLoopGraphData() {}

    public static class GraphNode {

        public String id;
        public String label;
        public String itemType;
        public String nodeType; // SOURCE, SINK, DEVICE, MANIFOLD
        public int xCoord, yCoord, zCoord;
        public String status = "Normal";
        public double temperature = 20.0;
        public double flowRateLPerSec = 0.0;
        public double pressureBar = 1.0;
        public Map<String, String> extraStats = new LinkedHashMap<>();

        public GraphNode() {}

        public GraphNode(String id, String label, String itemType, String nodeType, int x, int y, int z) {
            this.id = id;
            this.label = label;
            this.itemType = itemType;
            this.nodeType = nodeType;
            this.xCoord = x;
            this.yCoord = y;
            this.zCoord = z;
        }

        public ItemStack getItemStack() {
            return resolveItemType(this.itemType);
        }
    }

    public static class GraphEdge {

        public String id;
        public String fromNodeId;
        public String toNodeId;
        public String pipeMaterial = "StainlessSteel";
        public String pipeSize = "Medium";
        public double diameterM = 0.05;
        public double lengthM = 1.0;
        public double flowRateLPerSec = 0.0;
        public double flowRateLPerTick = 0.0;
        public double flowFraction = 1.0;
        public double velocityMPerSec = 0.0;
        public double temperatureCelsius = 20.0;
        public double maxTemperatureCelsius = 3000.0;
        public double pressureBar = 1.0;
        public double maxPressureBar = 64.0;
        public double pressureDropMbar = 0.0;
        public double reynolds = 0.0;
        public String flowRegime = "Laminar";

        public GraphEdge() {}

        public GraphEdge(String id, String fromNodeId, String toNodeId) {
            this.id = id;
            this.fromNodeId = fromNodeId;
            this.toNodeId = toNodeId;
        }
    }

    public static ItemStack resolveItemType(String itemType) {
        if (itemType == null) return null;
        return switch (itemType) {
            case "coolant_pump" -> ModMetaTileEntities.coolantPump != null
                ? ModMetaTileEntities.coolantPump.getStackForm(1L)
                : null;
            case "heat_exchanger" -> ModMetaTileEntities.pressurizedHeatExchanger != null
                ? ModMetaTileEntities.pressurizedHeatExchanger.getStackForm(1L)
                : null;
            case "degasser" -> ModMetaTileEntities.pressurizedDegasser != null
                ? ModMetaTileEntities.pressurizedDegasser.getStackForm(1L)
                : null;
            case "coolant_bath" -> ModMetaTileEntities.coolantBath != null
                ? ModMetaTileEntities.coolantBath.getStackForm(1L)
                : null;
            case "manifold" -> ModBlocks.manifold != null ? new ItemStack(ModBlocks.manifold) : null;
            case "instrument" -> ModBlocks.loopInstrument != null ? new ItemStack(ModBlocks.loopInstrument) : null;
            case "reservoir" -> ModBlocks.creativeHeatReservoir != null ? new ItemStack(ModBlocks.creativeHeatReservoir)
                : null;
            default -> ModMetaTileEntities.pressurizedFluidHatch != null
                ? ModMetaTileEntities.pressurizedFluidHatch.getStackForm(1L)
                : null;
        };
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    public static CoolantLoopGraphData fromJson(String json) {
        if (json == null || json.trim()
            .isEmpty()) {
            return new CoolantLoopGraphData();
        }
        try {
            return GSON.fromJson(json, CoolantLoopGraphData.class);
        } catch (Throwable t) {
            CoolantLoopGraphData data = new CoolantLoopGraphData();
            data.isFormed = false;
            data.failureReason = "Deserialization error: " + t.getMessage();
            return data;
        }
    }

    /**
     * Builds a comprehensive topological graph data snapshot from the active pump.
     */
    public static CoolantLoopGraphData buildFrom(MTECoolantPump pump) {
        CoolantLoopGraphData data = new CoolantLoopGraphData();
        if (pump == null) {
            data.isFormed = false;
            data.failureReason = "No pump controller";
            return data;
        }

        data.isFormed = pump.isLoopFormed();
        data.loopState = pump.getLoopState() != null ? pump.getLoopState()
            .name() : "EMPTY";
        data.failureReason = pump.getLoopStatus();

        CoolantLoopEngine engine = pump.getEngine();
        if (engine == null || !pump.isLoopFormed()) {
            return data;
        }

        CoolantFluidProperty fluid = pump.getCoolantFluidProperty();
        data.coolantName = fluid != null ? fluid.getFluidName() : "None";
        data.totalFlowRateLPerSec = engine.getFlowRateLitersPerSecond();
        data.peakPressureBar = engine.getPeakLoopPressureBar();

        CrawlResult crawl = pump.getLastCrawlResult();
        Map<String, FlowNode> graphNodes = (crawl != null && crawl.graphNodes != null) ? crawl.graphNodes
            : Collections.emptyMap();
        Map<String, List<String>> graphEdges = (crawl != null && crawl.graphEdges != null) ? crawl.graphEdges
            : Collections.emptyMap();

        Map<String, LoopSegment> segmentMap = new HashMap<>();
        for (LoopSegment seg : engine.getSegments()) {
            if (seg != null && seg.getId() != null) {
                segmentMap.put(seg.getId(), seg);
            }
        }

        // If crawler topology is empty, construct a fallback linear graph
        if (graphNodes.isEmpty()) {
            buildLinearFallbackGraph(pump, engine, data);
            return data;
        }

        // 1. Identify all Significant (Hub) Nodes (Source, Sink, Devices, Manifold Groups)
        Map<String, GraphNode> hubNodeMap = new LinkedHashMap<>();
        for (Map.Entry<String, FlowNode> entry : graphNodes.entrySet()) {
            String nodeId = entry.getKey();
            FlowNode fn = entry.getValue();
            if (fn.type == NodeType.PIPE) {
                continue; // Pipe blocks become edges
            }
            GraphNode gn = createHubNode(pump, engine, nodeId, fn);
            if (gn != null) {
                hubNodeMap.put(nodeId, gn);
                data.nodes.add(gn);
            }
        }

        // 2. Trace directed paths between Significant Nodes through pipe chains
        int edgeCounter = 0;
        Set<String> visitedBranchSignatures = new HashSet<>();

        for (String fromHubId : hubNodeMap.keySet()) {
            List<String> rawOuts = graphEdges.get(fromHubId);
            if (rawOuts == null) continue;

            for (String firstOut : rawOuts) {
                // Trace until reaching next hub node
                List<LoopSegment> pathSegments = new ArrayList<>();
                String curr = firstOut;
                String toHubId = null;
                Set<String> visitedInTrace = new HashSet<>();

                while (curr != null && visitedInTrace.add(curr)) {
                    if (hubNodeMap.containsKey(curr)) {
                        toHubId = curr;
                        break;
                    }
                    FlowNode currFn = graphNodes.get(curr);
                    if (currFn != null && currFn.type == NodeType.PIPE) {
                        LoopSegment seg = segmentMap.get(curr);
                        if (seg != null) {
                            pathSegments.add(seg);
                        }
                    }
                    List<String> nexts = graphEdges.get(curr);
                    if (nexts != null && !nexts.isEmpty()) {
                        curr = nexts.get(0);
                    } else {
                        break;
                    }
                }

                if (toHubId != null && !toHubId.equals(fromHubId)) {
                    String branchSig = fromHubId + "->"
                        + toHubId
                        + ":"
                        + (pathSegments.isEmpty() ? "direct"
                            : pathSegments.get(0)
                                .getId());
                    if (visitedBranchSignatures.add(branchSig)) {
                        GraphEdge edge = createGraphEdge(
                            "edge_" + (++edgeCounter),
                            fromHubId,
                            toHubId,
                            pathSegments,
                            engine,
                            crawl != null ? crawl.loopMaterial : null);
                        data.edges.add(edge);
                    }
                }
            }
        }

        return data;
    }

    private static GraphNode createHubNode(MTECoolantPump pump, CoolantLoopEngine engine, String nodeId, FlowNode fn) {
        String label = nodeId;
        String itemType = "default_device";
        String nodeType = fn.type.name();
        int x = 0, y = 0, z = 0;
        double temp = 20.0;
        double flow = engine.getFlowRateLitersPerSecond();
        double pressure = 1.0;
        String status = "Normal";
        Map<String, String> extra = new LinkedHashMap<>();

        if (fn.type == NodeType.SOURCE) {
            label = "Pump Discharge";
            itemType = "coolant_pump";
            if (pump.getBaseMetaTileEntity() != null) {
                x = pump.getBaseMetaTileEntity()
                    .getXCoord();
                y = pump.getBaseMetaTileEntity()
                    .getYCoord();
                z = pump.getBaseMetaTileEntity()
                    .getZCoord();
            }
            temp = pump.getReservoir() != null ? pump.getReservoir()
                .getReservoirTemperatureCelsius() : 20.0;
            pressure = engine.getPeakLoopPressureBar();
            status = pump.getLoopState()
                .name();
            extra.put(
                "Reservoir Level",
                String
                    .format(Locale.US, "%,d / %,d L", pump.getCurrentFillLiters(), pump.getReservoirCapacityLiters()));
            extra.put("Head Pressure", String.format(Locale.US, "%.2f bar", pressure));
            extra.put("Pump Power", String.format(Locale.US, "%,d EU/t", pump.getPowerConsumptionEU()));
            if (pump.getCoolantFluidProperty() != null) {
                extra.put(
                    "Coolant",
                    pump.getCoolantFluidProperty()
                        .getFluidName());
            }
        } else if (fn.type == NodeType.SINK) {
            label = "Pump Suction";
            itemType = "coolant_pump";
            if (pump.getBaseMetaTileEntity() != null) {
                x = pump.getBaseMetaTileEntity()
                    .getXCoord();
                y = pump.getBaseMetaTileEntity()
                    .getYCoord();
                z = pump.getBaseMetaTileEntity()
                    .getZCoord();
            }
            List<LoopSegment> segs = engine.getSegments();
            if (!segs.isEmpty()) {
                temp = segs.get(segs.size() - 1)
                    .getCurrentTemperatureCelsius();
            }
            pressure = 1.0;
            extra.put("Suction Pressure", "1.00 bar (tank head)");
            extra.put("Return Temperature", String.format(Locale.US, "%.1f °C", temp));
        } else if (fn.type == NodeType.MANIFOLD_GROUP) {
            label = "Manifold Group";
            itemType = "manifold";
            if (fn.data instanceof LoopGraphCrawler.ManifoldGroup mg) {
                if (!mg.blockCoords.isEmpty()) {
                    BlockPosCoord first = mg.blockCoords.iterator()
                        .next();
                    x = first.x;
                    y = first.y;
                    z = first.z;
                }
                int ins = mg.inputPorts.size();
                int outs = mg.outputPorts.size();
                if (ins < outs) {
                    label = "Manifold (splitter)";
                } else if (ins > outs) {
                    label = "Manifold (combiner)";
                }
                extra.put("Blocks", String.valueOf(mg.blockCoords.size()));
                extra.put("Ports", String.format("%d in / %d out", ins, outs));
            }

        } else if (fn.type == NodeType.DEVICE) {
            Object devObj = fn.data;
            if (devObj instanceof MTEPressurizedHeatExchanger phe) {
                label = "High-pressure heat exchanger";
                itemType = "heat_exchanger";
                if (phe.getBaseMetaTileEntity() != null) {
                    x = phe.getBaseMetaTileEntity()
                        .getXCoord();
                    y = phe.getBaseMetaTileEntity()
                        .getYCoord();
                    z = phe.getBaseMetaTileEntity()
                        .getZCoord();
                }
                status = phe.getBaseMetaTileEntity() != null && phe.getBaseMetaTileEntity()
                    .isActive() ? "Active" : "Idle";
                extra.put("Operating Mode", "High-Pressure Convective");
            } else if (devObj instanceof MTEPressurizedDegasser degasser) {
                label = "High-pressure degasser";
                itemType = "degasser";
                if (degasser.getBaseMetaTileEntity() != null) {
                    x = degasser.getBaseMetaTileEntity()
                        .getXCoord();
                    y = degasser.getBaseMetaTileEntity()
                        .getYCoord();
                    z = degasser.getBaseMetaTileEntity()
                        .getZCoord();
                }
                status = degasser.getBaseMetaTileEntity() != null && degasser.getBaseMetaTileEntity()
                    .isActive() ? "Active" : "Idle";
                extra.put("Purge State", "Circulating");
            } else if (devObj instanceof MTECoolantBath bath) {
                label = "Coolant Bath";
                itemType = "coolant_bath";
                if (bath.getBaseMetaTileEntity() != null) {
                    x = bath.getBaseMetaTileEntity()
                        .getXCoord();
                    y = bath.getBaseMetaTileEntity()
                        .getYCoord();
                    z = bath.getBaseMetaTileEntity()
                        .getZCoord();
                }
                status = bath.getBaseMetaTileEntity() != null && bath.getBaseMetaTileEntity()
                    .isActive() ? "Active" : "Idle";
                extra.put("Capacity", String.format(Locale.US, "%,d L", bath.getFillVolumeLiters()));
            } else if (devObj instanceof TileEntityLoopInstrument inst) {
                label = "Loop Instrument";
                itemType = "instrument";
                x = inst.xCoord;
                y = inst.yCoord;
                z = inst.zCoord;
                status = "Monitoring";
            } else if (devObj instanceof TileEntityCreativeHeatReservoir res) {
                label = "Creative Heat Reservoir";
                itemType = "reservoir";
                x = res.xCoord;
                y = res.yCoord;
                z = res.zCoord;
                status = "Active";
            } else if (devObj instanceof MTEHatchPressurizedFluid hatch) {
                label = "Passage Hatch";
                itemType = "passage_hatch";
                if (hatch.getBaseMetaTileEntity() != null) {
                    x = hatch.getBaseMetaTileEntity()
                        .getXCoord();
                    y = hatch.getBaseMetaTileEntity()
                        .getYCoord();
                    z = hatch.getBaseMetaTileEntity()
                        .getZCoord();
                }
            }
        }

        GraphNode node = new GraphNode(nodeId, label, itemType, nodeType, x, y, z);
        node.status = status;
        node.temperature = temp;
        node.flowRateLPerSec = flow;
        node.pressureBar = pressure;
        node.extraStats = extra;
        return node;
    }

    private static GraphEdge createGraphEdge(String edgeId, String fromId, String toId, List<LoopSegment> pathSegments,
        CoolantLoopEngine engine, Materials defaultMat) {
        GraphEdge edge = new GraphEdge(edgeId, fromId, toId);

        if (defaultMat != null) {
            edge.pipeMaterial = defaultMat.mDefaultLocalName != null ? defaultMat.mDefaultLocalName : defaultMat.name();
        }

        if (pathSegments.isEmpty()) {
            edge.lengthM = 1.0;
            edge.diameterM = 0.05;
            edge.flowFraction = 1.0;
            edge.flowRateLPerSec = engine.getFlowRateLitersPerSecond();
            edge.flowRateLPerTick = edge.flowRateLPerSec / 20.0;
            edge.velocityMPerSec = 0.0;
            edge.temperatureCelsius = 20.0;
            edge.maxTemperatureCelsius = 3000.0;
            edge.pressureBar = engine.getPeakLoopPressureBar();
            edge.maxPressureBar = 64.0;
            edge.pressureDropMbar = 0.0;
            edge.reynolds = 0.0;
            edge.flowRegime = "Laminar";
            return edge;
        }

        double totalLen = 0.0;
        double maxPress = 0.0;
        double maxTempLimit = Double.MAX_VALUE;
        double curTemp = 0.0;
        double totalDeltaP = 0.0;
        double flowFrac = pathSegments.get(0)
            .getFlowFraction();
        double diam = pathSegments.get(0)
            .getDiameter();
        double area = pathSegments.get(0)
            .getArea();
        double burstBar = pathSegments.get(0)
            .getMaxPressureBar();

        for (LoopSegment s : pathSegments) {
            totalLen += s.getLength();
            curTemp = s.getCurrentTemperatureCelsius();
            maxTempLimit = Math.min(maxTempLimit, s.getMaxTemperatureCelsius());
            maxPress = Math.max(maxPress, s.getMaxPressureBar());
            double localQ = (engine.getFlowRateLitersPerSecond() / 1000.0) * s.getFlowFraction();
            totalDeltaP += s.computePressureDrop(localQ, engine.getFluid());
        }

        edge.lengthM = Math.max(1.0, totalLen);
        edge.diameterM = diam > 0 ? diam : 0.05;
        edge.flowFraction = flowFrac;
        edge.flowRateLPerSec = engine.getFlowRateLitersPerSecond() * flowFrac;
        edge.flowRateLPerTick = edge.flowRateLPerSec / 20.0;
        edge.velocityMPerSec = area > 0 ? (edge.flowRateLPerSec / 1000.0) / area : 0.0;
        edge.temperatureCelsius = curTemp;
        edge.maxTemperatureCelsius = maxTempLimit != Double.MAX_VALUE ? maxTempLimit : 3000.0;
        edge.pressureBar = engine.getPeakLoopPressureBar();
        edge.maxPressureBar = burstBar > 0 ? burstBar : 64.0;
        edge.pressureDropMbar = totalDeltaP / 100.0; // Pa to mbar

        double localFlowM3 = edge.flowRateLPerSec / 1000.0;
        edge.reynolds = pathSegments.get(0)
            .computeReynoldsNumber(localFlowM3, engine.getFluid());
        edge.flowRegime = edge.reynolds < 2300.0 ? "Laminar" : (edge.reynolds < 4000.0 ? "Transitional" : "Turbulent");

        if (diam <= 0.025) edge.pipeSize = "Tiny";
        else if (diam <= 0.038) edge.pipeSize = "Small";
        else if (diam <= 0.051) edge.pipeSize = "Medium";
        else if (diam <= 0.076) edge.pipeSize = "Large";
        else edge.pipeSize = "Huge";

        return edge;
    }

    private static void buildLinearFallbackGraph(MTECoolantPump pump, CoolantLoopEngine engine,
        CoolantLoopGraphData data) {
        GraphNode source = new GraphNode("source", "Pump Discharge", "coolant_pump", "SOURCE", 0, 0, 0);
        source.temperature = pump.getReservoir() != null ? pump.getReservoir()
            .getReservoirTemperatureCelsius() : 20.0;
        source.flowRateLPerSec = engine.getFlowRateLitersPerSecond();
        source.pressureBar = engine.getPeakLoopPressureBar();
        data.nodes.add(source);

        String prevId = "source";
        int devIdx = 0;
        for (ICoolantLoopDevice dev : pump.getLoopDevices()) {
            String devId = "dev_" + (++devIdx);
            String itemType = "default_device";
            String label = "Device #" + devIdx;
            if (dev instanceof MTEPressurizedHeatExchanger) {
                itemType = "heat_exchanger";
                label = "High-pressure heat exchanger";
            } else if (dev instanceof MTEPressurizedDegasser) {
                itemType = "degasser";
                label = "High-pressure degasser";
            } else if (dev instanceof MTECoolantBath) {
                itemType = "coolant_bath";
                label = "Coolant Bath";
            }
            GraphNode devNode = new GraphNode(devId, label, itemType, "DEVICE", 0, 0, 0);
            devNode.temperature = engine.getAvgLoopTempCelsius();
            devNode.flowRateLPerSec = engine.getFlowRateLitersPerSecond();
            data.nodes.add(devNode);

            data.edges.add(new GraphEdge("edge_" + devIdx, prevId, devId));
            prevId = devId;
        }

        GraphNode sink = new GraphNode("sink", "Pump Suction", "coolant_pump", "SINK", 0, 0, 0);
        sink.temperature = engine.getAvgLoopTempCelsius();
        sink.flowRateLPerSec = engine.getFlowRateLitersPerSecond();
        data.nodes.add(sink);
        data.edges.add(new GraphEdge("edge_sink", prevId, "sink"));
    }
}
