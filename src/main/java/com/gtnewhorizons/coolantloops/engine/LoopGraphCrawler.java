package com.gtnewhorizons.coolantloops.engine;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizons.coolantloops.common.metatileentity.hatch.MTEHatchPressurizedFluid;
import com.gtnewhorizons.coolantloops.common.metatileentity.multi.MTECoolantBath;
import com.gtnewhorizons.coolantloops.common.metatileentity.multi.MTECoolantPump;
import com.gtnewhorizons.coolantloops.common.metatileentity.multi.MTEPressurizedDegasser;
import com.gtnewhorizons.coolantloops.common.metatileentity.multi.MTEPressurizedHeatExchanger;
import com.gtnewhorizons.coolantloops.common.metatileentity.pipe.MTECoolantPipe;
import com.gtnewhorizons.coolantloops.common.tileentity.TileEntityManifold;
import com.gtnewhorizons.coolantloops.common.util.CoolantLocalization;

import gregtech.api.enums.Materials;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.BaseMetaPipeEntity;
import gregtech.api.metatileentity.GregTechTileClientEvents;
import gregtech.api.metatileentity.implementations.MTEFluidPipe;

/**
 * Traverses physical Minecraft block connections from a pump's discharge port
 * around a closed coolant circuit back to the suction port.
 *
 * Enforces the core physical invariants:
 * 1. Continuous closed loop without dead-ends.
 * 2. All GregTech pipes in the loop must be strictly empty (zero Forge fluids).
 * 3. Exactly one pump per loop (no multi-pump loops).
 * 4. All pipes in the loop and the Railcraft tank must use the same material
 * (restricted to materials having both GT fluid pipes and Railcraft tanks).
 * 5. All pipes in each segment between devices must use the same pipe size.
 * 6. Maximum volumetric flow rate is capped by the fluid capacity of the pipes connected to the pump.
 */
public class LoopGraphCrawler {

    /**
     * Represents the flow configuration of a single pipe block along the crawled loop.
     * Sets "input disabled" on the forward side (towards coolant flowing out) and
     * "input allowed" on all other sides to render directional flow arrows on the pipe.
     */
    public static class PipeFlowConfig {

        public final BaseMetaPipeEntity bmpe;
        public final MTEFluidPipe pipe;
        public final ForgeDirection forwardDir;

        public PipeFlowConfig(BaseMetaPipeEntity bmpe, MTEFluidPipe pipe, ForgeDirection forwardDir) {
            this.bmpe = bmpe;
            this.pipe = pipe;
            this.forwardDir = forwardDir;
        }

        public void apply() {
            if (pipe == null || forwardDir == null || forwardDir == ForgeDirection.UNKNOWN) return;
            byte targetMask = (byte) (1 << forwardDir.ordinal());
            if (pipe.mDisableInput != targetMask) {
                pipe.mDisableInput = targetMask;
                if (bmpe != null) {
                    try {
                        bmpe.sendBlockEvent(GregTechTileClientEvents.CHANGE_CUSTOM_DATA, targetMask);
                        bmpe.issueTextureUpdate();
                        bmpe.markDirty();
                    } catch (Throwable ignored) {}
                }
            }
        }
    }

    public static class CrawlResult {

        public final boolean isSuccess;
        public final String failureReason;
        public final List<LoopSegment> segments;
        public final List<ICoolantLoopDevice> devices;
        public final Set<BlockPosCoord> pipePositions;
        public final double maxPumpPipeCapacityLPerSec;
        public final Materials loopMaterial;
        public final List<PipeFlowConfig> pipeConfigs;
        public final Map<String, FlowNode> graphNodes;
        public final Map<String, List<String>> graphEdges;

        public CrawlResult(boolean isSuccess, String failureReason, List<LoopSegment> segments,
            List<ICoolantLoopDevice> devices, Set<BlockPosCoord> pipePositions) {
            this(isSuccess, failureReason, segments, devices, pipePositions, Double.MAX_VALUE, null, null, null, null);
        }

        public CrawlResult(boolean isSuccess, String failureReason, List<LoopSegment> segments,
            List<ICoolantLoopDevice> devices, Set<BlockPosCoord> pipePositions, double maxPumpPipeCapacityLPerSec,
            Materials loopMaterial) {
            this(
                isSuccess,
                failureReason,
                segments,
                devices,
                pipePositions,
                maxPumpPipeCapacityLPerSec,
                loopMaterial,
                null,
                null,
                null);
        }

        public CrawlResult(boolean isSuccess, String failureReason, List<LoopSegment> segments,
            List<ICoolantLoopDevice> devices, Set<BlockPosCoord> pipePositions, double maxPumpPipeCapacityLPerSec,
            Materials loopMaterial, List<PipeFlowConfig> pipeConfigs) {
            this(
                isSuccess,
                failureReason,
                segments,
                devices,
                pipePositions,
                maxPumpPipeCapacityLPerSec,
                loopMaterial,
                pipeConfigs,
                null,
                null);
        }

        public CrawlResult(boolean isSuccess, String failureReason, List<LoopSegment> segments,
            List<ICoolantLoopDevice> devices, Set<BlockPosCoord> pipePositions, double maxPumpPipeCapacityLPerSec,
            Materials loopMaterial, List<PipeFlowConfig> pipeConfigs, Map<String, FlowNode> graphNodes,
            Map<String, List<String>> graphEdges) {
            this.isSuccess = isSuccess;
            this.failureReason = failureReason;
            this.segments = segments != null ? segments : new ArrayList<>();
            this.devices = devices != null ? devices : new ArrayList<>();
            this.pipePositions = pipePositions != null ? pipePositions : new HashSet<>();
            this.maxPumpPipeCapacityLPerSec = maxPumpPipeCapacityLPerSec;
            this.loopMaterial = loopMaterial;
            this.pipeConfigs = pipeConfigs != null ? pipeConfigs : new ArrayList<>();
            this.graphNodes = graphNodes != null ? new LinkedHashMap<>(graphNodes) : new LinkedHashMap<>();
            this.graphEdges = graphEdges != null ? new LinkedHashMap<>(graphEdges) : new LinkedHashMap<>();
        }

        public static CrawlResult fail(String reason) {
            return new CrawlResult(false, reason, null, null, null, 0.0, null, null, null, null);
        }

        public static CrawlResult success(List<LoopSegment> segments, List<ICoolantLoopDevice> devices,
            Set<BlockPosCoord> pipePositions) {
            return new CrawlResult(
                true,
                null,
                segments,
                devices,
                pipePositions,
                Double.MAX_VALUE,
                null,
                null,
                null,
                null);
        }

        public static CrawlResult success(List<LoopSegment> segments, List<ICoolantLoopDevice> devices,
            Set<BlockPosCoord> pipePositions, double maxPumpPipeCapacityLPerSec, Materials loopMaterial) {
            return new CrawlResult(
                true,
                null,
                segments,
                devices,
                pipePositions,
                maxPumpPipeCapacityLPerSec,
                loopMaterial,
                null,
                null,
                null);
        }

        public static CrawlResult success(List<LoopSegment> segments, List<ICoolantLoopDevice> devices,
            Set<BlockPosCoord> pipePositions, double maxPumpPipeCapacityLPerSec, Materials loopMaterial,
            List<PipeFlowConfig> pipeConfigs) {
            return new CrawlResult(
                true,
                null,
                segments,
                devices,
                pipePositions,
                maxPumpPipeCapacityLPerSec,
                loopMaterial,
                pipeConfigs,
                null,
                null);
        }

        public static CrawlResult success(List<LoopSegment> segments, List<ICoolantLoopDevice> devices,
            Set<BlockPosCoord> pipePositions, double maxPumpPipeCapacityLPerSec, Materials loopMaterial,
            List<PipeFlowConfig> pipeConfigs, Map<String, FlowNode> graphNodes, Map<String, List<String>> graphEdges) {
            return new CrawlResult(
                true,
                null,
                segments,
                devices,
                pipePositions,
                maxPumpPipeCapacityLPerSec,
                loopMaterial,
                pipeConfigs,
                graphNodes,
                graphEdges);
        }
    }

    public static class BlockPosCoord {

        public final int x, y, z;

        public BlockPosCoord(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof BlockPosCoord)) return false;
            BlockPosCoord that = (BlockPosCoord) o;
            return x == that.x && y == that.y && z == that.z;
        }

        @Override
        public int hashCode() {
            return 31 * (31 * x + y) + z;
        }
    }

    public static final int STEPS_PER_TICK = 1000;
    public static final int MAX_CRAWL_TICKS = 20;
    public static final int DEFAULT_MAX_STEPS = STEPS_PER_TICK * MAX_CRAWL_TICKS; // 20000

    public static double getPipeSizeWeight(float thickness) {
        if (thickness <= 0.251f) return 1.0 / 6.0;
        if (thickness <= 0.376f) return 1.0 / 3.0;
        if (thickness <= 0.501f) return 1.0;
        if (thickness <= 0.751f) return 2.0;
        return 4.0;
    }

    private final World world;
    private final int maxDepth;

    public LoopGraphCrawler(World world, int maxDepth) {
        this.world = world;
        this.maxDepth = maxDepth > 0 ? maxDepth : DEFAULT_MAX_STEPS;
    }

    public LoopGraphCrawler(World world) {
        this(world, DEFAULT_MAX_STEPS);
    }

    public static boolean isAllowedCoolantPipeMaterial(Materials mat) {
        return CoolantPipingRegistry.isAllowedCoolantPipeMaterial(mat);
    }

    public static boolean areMaterialsEqual(Materials m1, Materials m2) {
        return CoolantPipingRegistry.areMaterialsEqual(m1, m2);
    }

    public static boolean isIron(Materials m) {
        return CoolantPipingRegistry.isIron(m);
    }

    /**
     * Crawls the loop from a starting port (discharge) until returning to the target port (suction).
     */
    public CrawlResult crawl(int startX, int startY, int startZ, ForgeDirection startDir, int targetX, int targetY,
        int targetZ, ForgeDirection targetSuctionSide, String pumpId) {
        return crawl(
            startX,
            startY,
            startZ,
            startDir,
            targetX,
            targetY,
            targetZ,
            targetSuctionSide,
            pumpId,
            null,
            null);
    }

    public CrawlSession startSession(int startX, int startY, int startZ, ForgeDirection startDir, int targetX,
        int targetY, int targetZ, ForgeDirection targetSuctionSide, String pumpId) {
        return startSession(
            startX,
            startY,
            startZ,
            startDir,
            targetX,
            targetY,
            targetZ,
            targetSuctionSide,
            pumpId,
            null,
            null);
    }

    /**
     * Crawls the loop from a starting port (discharge) until returning to the target port (suction),
     * providing the active pump reference to multi-block passages.
     */
    public CrawlResult crawl(int startX, int startY, int startZ, ForgeDirection startDir, int targetX, int targetY,
        int targetZ, ForgeDirection targetSuctionSide, String pumpId, ICoolantLoopPump pump) {
        return crawl(
            startX,
            startY,
            startZ,
            startDir,
            targetX,
            targetY,
            targetZ,
            targetSuctionSide,
            pumpId,
            pump,
            null);
    }

    public CrawlSession startSession(int startX, int startY, int startZ, ForgeDirection startDir, int targetX,
        int targetY, int targetZ, ForgeDirection targetSuctionSide, String pumpId, ICoolantLoopPump pump) {
        return startSession(
            startX,
            startY,
            startZ,
            startDir,
            targetX,
            targetY,
            targetZ,
            targetSuctionSide,
            pumpId,
            pump,
            null);
    }

    public CrawlSession startSession(int startX, int startY, int startZ, ForgeDirection startDir, int targetX,
        int targetY, int targetZ, ForgeDirection targetSuctionSide, String pumpId, ICoolantLoopPump pump,
        Materials expectedTankMaterial) {
        return new CrawlSession(
            this.world,
            startX,
            startY,
            startZ,
            startDir,
            targetX,
            targetY,
            targetZ,
            targetSuctionSide,
            pumpId,
            pump,
            expectedTankMaterial,
            this.maxDepth,
            MAX_CRAWL_TICKS);
    }

    /**
     * Crawls the loop from a starting port (discharge) until returning to the target port (suction),
     * enforcing matching tank material, segment pipe size consistency, and connected pipe capacity limits.
     */
    public enum NodeType {
        SOURCE,
        PIPE,
        DEVICE,
        MANIFOLD_GROUP,
        SINK
    }

    public static class FlowNode {

        public final String id;
        public final NodeType type;
        public final Object data;

        public FlowNode(String id, NodeType type, Object data) {
            this.id = id;
            this.type = type;
            this.data = data;
        }
    }

    public static class ManifoldGroup {

        public final Set<BlockPosCoord> blockCoords = new LinkedHashSet<>();
        public final List<TileEntityManifold> tiles = new ArrayList<>();
        public ForgeDirection inputSide = ForgeDirection.UNKNOWN;
        public ForgeDirection outputSide = ForgeDirection.UNKNOWN;
        public final List<BlockPosCoord> inputPorts = new ArrayList<>();
        public final List<BlockPosCoord> outputPorts = new ArrayList<>();

        public String getGroupId() {
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
            for (BlockPosCoord c : blockCoords) {
                if (c.x < minX || (c.x == minX && (c.y < minY || (c.y == minY && c.z < minZ)))) {
                    minX = c.x;
                    minY = c.y;
                    minZ = c.z;
                }
            }
            return String.format("manifold_group_%d_%d_%d", minX, minY, minZ);
        }
    }

    public static class FrontierStep {

        public final String fromNodeId;
        public final int x, y, z;
        public final ForgeDirection entrySide;
        public final Float segmentThickness;
        public final Integer segmentPipeAmount;

        public FrontierStep(String fromNodeId, int x, int y, int z, ForgeDirection entrySide, Float segmentThickness,
            Integer segmentPipeAmount) {
            this.fromNodeId = fromNodeId;
            this.x = x;
            this.y = y;
            this.z = z;
            this.entrySide = entrySide;
            this.segmentThickness = segmentThickness;
            this.segmentPipeAmount = segmentPipeAmount;
        }
    }

    public static boolean isPumpActive(World world, String pumpId) {
        if (world == null || pumpId == null) return false;
        String[] parts = pumpId.contains(":") ? pumpId.split(":") : pumpId.split("_");
        if (parts.length >= 4 && "pump".equals(parts[0])) {
            try {
                int px = Integer.parseInt(parts[1]);
                int py = Integer.parseInt(parts[2]);
                int pz = Integer.parseInt(parts[3]);
                TileEntity te = world.getTileEntity(px, py, pz);
                if (te instanceof IGregTechTileEntity) {
                    IMetaTileEntity mte = ((IGregTechTileEntity) te).getMetaTileEntity();
                    if (mte instanceof MTECoolantPump) {
                        MTECoolantPump pump = (MTECoolantPump) mte;
                        return pump.isLoopFormed() && !te.isInvalid() && pump.getBaseMetaTileEntity() != null;
                    }
                }
            } catch (Throwable ignored) {}
        }
        return false;
    }

    public static boolean isExternalBlockConnectedTo(World world, int nx, int ny, int nz,
        ForgeDirection sideFacingManifold, int targetX, int targetY, int targetZ, ForgeDirection targetSuctionSide,
        int startX, int startY, int startZ, ForgeDirection startDir) {
        if (world == null) return false;
        if (nx == targetX && ny == targetY && nz == targetZ) {
            return targetSuctionSide == sideFacingManifold;
        }
        if (nx == startX && ny == startY && nz == startZ) {
            return startDir == sideFacingManifold;
        }
        TileEntity te = world.getTileEntity(nx, ny, nz);
        if (te == null) return false;
        if (te instanceof TileEntityManifold) {
            TileEntityManifold mf = (TileEntityManifold) te;
            return mf.isConnected(sideFacingManifold);
        }
        if (te instanceof BaseMetaPipeEntity) {
            BaseMetaPipeEntity bmpe = (BaseMetaPipeEntity) te;
            if (bmpe.getMetaTileEntity() instanceof MTECoolantPipe) {
                MTECoolantPipe pipe = (MTECoolantPipe) bmpe.getMetaTileEntity();
                return isPipeConnectedAtSide(bmpe, pipe, sideFacingManifold);
            }
        }
        if (te instanceof IGregTechTileEntity) {
            IGregTechTileEntity gte = (IGregTechTileEntity) te;
            IMetaTileEntity mte = gte.getMetaTileEntity();
            if (mte instanceof MTEHatchPressurizedFluid) {
                return gte.getFrontFacing() == sideFacingManifold;
            }
            if (mte instanceof ICoolantLoopDevice) {
                return true;
            }
        }
        if (te instanceof ICoolantLoopDevice) {
            return true;
        }
        return false;
    }

    public static void addGraphNode(Map<String, FlowNode> graphNodes, String id, NodeType type, Object data) {
        if (!graphNodes.containsKey(id)) {
            graphNodes.put(id, new FlowNode(id, type, data));
        }
    }

    public static void addGraphEdge(Map<String, List<String>> graphEdges, Map<String, List<String>> reverseGraphEdges,
        String from, String to) {
        List<String> fList = graphEdges.computeIfAbsent(from, k -> new ArrayList<>());
        if (!fList.contains(to)) {
            fList.add(to);
        }
        List<String> rList = reverseGraphEdges.computeIfAbsent(to, k -> new ArrayList<>());
        if (!rList.contains(from)) {
            rList.add(from);
        }
    }

    public static boolean dfsCheckCycle(String u, Map<String, List<String>> graphEdges, Map<String, Integer> colors,
        Map<String, String> parents, List<String> cycle) {
        colors.put(u, 1); // GRAY
        List<String> neighbors = graphEdges.get(u);
        if (neighbors != null) {
            for (String v : neighbors) {
                int col = colors.getOrDefault(v, 0);
                if (col == 1) { // Cycle detected!
                    cycle.add(v);
                    String curr = u;
                    while (curr != null && !curr.equals(v)) {
                        cycle.add(0, curr);
                        curr = parents.get(curr);
                    }
                    cycle.add(0, v);
                    return true;
                } else if (col == 0) {
                    parents.put(v, u);
                    if (dfsCheckCycle(v, graphEdges, colors, parents, cycle)) {
                        return true;
                    }
                }
            }
        }
        colors.put(u, 2); // BLACK
        return false;
    }

    public static class CrawlSession {

        private final World world;
        private final int startX, startY, startZ;
        private final ForgeDirection startDir;
        private final int targetX, targetY, targetZ;
        private final ForgeDirection targetSuctionSide;
        private final String pumpId;
        private final ICoolantLoopPump pump;
        private final Materials expectedTankMaterial;
        private final int maxSteps;
        private final int maxTicks;

        private final List<LoopSegment> segments = new ArrayList<>();
        private final List<ICoolantLoopDevice> devices = new ArrayList<>();
        private final Set<BlockPosCoord> visitedPipeAndDevicePositions = new HashSet<>();
        private final List<PipeFlowConfig> pipeConfigs = new ArrayList<>();
        private final Map<BlockPosCoord, ManifoldGroup> coordToManifoldGroup = new HashMap<>();
        private final Set<ManifoldGroup> allManifoldGroups = new LinkedHashSet<>();

        private final Map<String, FlowNode> graphNodes = new LinkedHashMap<>();
        private final Map<String, List<String>> graphEdges = new LinkedHashMap<>();
        private final Map<String, List<String>> reverseGraphEdges = new LinkedHashMap<>();

        private Materials detectedLoopMaterial;
        private double dischargePipeCapacityLPerSec = -1.0;
        private double suctionPipeCapacityLPerSec = -1.0;
        private MTEFluidPipe lastPipe = null;

        private final String sourceNodeId;
        private final String sinkNodeId;
        private final Queue<FrontierStep> frontier = new ArrayDeque<>();

        private int stepCount = 0;
        private int ticksElapsed = 0;
        private boolean isDone = false;
        private CrawlResult result = null;

        public CrawlSession(World world, int startX, int startY, int startZ, ForgeDirection startDir, int targetX,
            int targetY, int targetZ, ForgeDirection targetSuctionSide, String pumpId, ICoolantLoopPump pump,
            Materials expectedTankMaterial, int maxSteps, int maxTicks) {
            this.world = world;
            this.startX = startX;
            this.startY = startY;
            this.startZ = startZ;
            this.startDir = startDir;
            this.targetX = targetX;
            this.targetY = targetY;
            this.targetZ = targetZ;
            this.targetSuctionSide = targetSuctionSide;
            this.pumpId = pumpId;
            this.pump = pump;
            this.expectedTankMaterial = expectedTankMaterial;
            this.maxSteps = maxSteps > 0 ? maxSteps : DEFAULT_MAX_STEPS;
            this.maxTicks = maxTicks > 0 ? maxTicks : MAX_CRAWL_TICKS;

            this.detectedLoopMaterial = expectedTankMaterial != null ? expectedTankMaterial
                : (pump != null ? pump.getTankMaterial() : null);
            this.sourceNodeId = String.format("pump_disc_%d_%d_%d", startX, startY, startZ);
            this.sinkNodeId = String.format("pump_suc_%d_%d_%d", targetX, targetY, targetZ);
            addGraphNode(graphNodes, sourceNodeId, NodeType.SOURCE, null);

            int firstX = startX + startDir.offsetX;
            int firstY = startY + startDir.offsetY;
            int firstZ = startZ + startDir.offsetZ;
            ForgeDirection firstEntrySide = startDir.getOpposite();

            frontier.add(new FrontierStep(sourceNodeId, firstX, firstY, firstZ, firstEntrySide, null, null));
        }

        public boolean isDone() {
            return isDone;
        }

        public CrawlResult getResult() {
            return result;
        }

        public int getStepCount() {
            return stepCount;
        }

        public int getTicksElapsed() {
            return ticksElapsed;
        }

        private void finishWithFailure(String reason) {
            this.result = CrawlResult.fail(reason);
            this.isDone = true;
        }

        public boolean step(int stepsLimit) {
            if (isDone) {
                return true;
            }

            ticksElapsed++;
            if (ticksElapsed > maxTicks) {
                finishWithFailure(CoolantLocalization.get("coolantloops.crawler.loop_too_large"));
                return true;
            }

            int stepsThisSlice = 0;
            while (!frontier.isEmpty() && stepsThisSlice < stepsLimit) {
                if (stepCount >= maxSteps) {
                    finishWithFailure(CoolantLocalization.get("coolantloops.crawler.loop_too_large"));
                    return true;
                }

                FrontierStep step = frontier.poll();
                stepsThisSlice++;
                stepCount++;

                // Check if we arrived at the pump suction port
                if (step.x == targetX && step.y == targetY && step.z == targetZ) {
                    addGraphNode(graphNodes, sinkNodeId, NodeType.SINK, null);
                    addGraphEdge(graphEdges, reverseGraphEdges, step.fromNodeId, sinkNodeId);
                    if (lastPipe != null && suctionPipeCapacityLPerSec < 0.0) {
                        suctionPipeCapacityLPerSec = lastPipe.getCapacity();
                    }
                    continue;
                }

                TileEntity te = world.getTileEntity(step.x, step.y, step.z);
                if (te == null) {
                    finishWithFailure(
                        CoolantLocalization.format("coolantloops.crawler.no_tile", step.x, step.y, step.z));
                    return true;
                }

                // 1. Manifold Group Structure Handling
                if (te instanceof TileEntityManifold) {
                    TileEntityManifold mf = (TileEntityManifold) te;

                    // Entry side check: must enter perpendicularly to the manifold plane (along normal axis)
                    if (!mf.isNormalDirection(step.entrySide)) {
                        finishWithFailure(
                            CoolantLocalization.format(
                                "coolantloops.crawler.manifold_parallel",
                                step.x,
                                step.y,
                                step.z,
                                step.entrySide,
                                mf.getNormalAxisName()));
                        return true;
                    }
                    if (!mf.isConnected(step.entrySide)) {
                        finishWithFailure(
                            CoolantLocalization.format(
                                "coolantloops.crawler.manifold_disabled",
                                step.x,
                                step.y,
                                step.z,
                                step.entrySide));
                        return true;
                    }

                    BlockPosCoord mfCoord = new BlockPosCoord(step.x, step.y, step.z);
                    ManifoldGroup mg = coordToManifoldGroup.get(mfCoord);

                    if (mg == null) {
                        Set<BlockPosCoord> groupCoords = new LinkedHashSet<>();
                        List<TileEntityManifold> groupTiles = new ArrayList<>();
                        Queue<BlockPosCoord> mfQueue = new ArrayDeque<>();

                        groupCoords.add(mfCoord);
                        groupTiles.add(mf);
                        mfQueue.add(mfCoord);

                        while (!mfQueue.isEmpty()) {
                            BlockPosCoord p = mfQueue.poll();
                            TileEntity pte = world.getTileEntity(p.x, p.y, p.z);
                            TileEntityManifold pmf = (pte instanceof TileEntityManifold) ? (TileEntityManifold) pte
                                : null;
                            if (pmf == null) continue;

                            for (ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
                                if (!pmf.isInPlane(dir) || !pmf.isConnected(dir)) {
                                    continue;
                                }
                                int nx = p.x + dir.offsetX;
                                int ny = p.y + dir.offsetY;
                                int nz = p.z + dir.offsetZ;
                                BlockPosCoord np = new BlockPosCoord(nx, ny, nz);
                                if (!groupCoords.contains(np)) {
                                    TileEntity nte = world.getTileEntity(nx, ny, nz);
                                    if (nte instanceof TileEntityManifold) {
                                        TileEntityManifold nmf = (TileEntityManifold) nte;
                                        if (pmf.hasSamePlaneOrientation(nmf) && nmf.isConnected(dir.getOpposite())) {
                                            groupCoords.add(np);
                                            groupTiles.add(nmf);
                                            mfQueue.add(np);
                                        }
                                    }
                                }
                            }
                        }

                        // Check for conflicting loop ownership on all manifold blocks in the group
                        for (TileEntityManifold tile : groupTiles) {
                            String otherPump = tile.getActivePumpId();
                            if (otherPump != null && !otherPump.equals(pumpId) && isPumpActive(world, otherPump)) {
                                finishWithFailure(
                                    CoolantLocalization.format(
                                        "coolantloops.crawler.manifold_other_loop",
                                        tile.xCoord,
                                        tile.yCoord,
                                        tile.zCoord,
                                        otherPump));
                                return true;
                            }
                        }

                        ForgeDirection groupInputSide = step.entrySide;
                        ForgeDirection groupOutputSide = step.entrySide.getOpposite();

                        mg = new ManifoldGroup();
                        mg.blockCoords.addAll(groupCoords);
                        mg.tiles.addAll(groupTiles);
                        mg.inputSide = groupInputSide;
                        mg.outputSide = groupOutputSide;

                        // Validate external connections across every block in the manifold group
                        for (BlockPosCoord mPos : groupCoords) {
                            TileEntity mteRaw = world.getTileEntity(mPos.x, mPos.y, mPos.z);
                            TileEntityManifold mTile = (mteRaw instanceof TileEntityManifold)
                                ? (TileEntityManifold) mteRaw
                                : null;

                            for (ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
                                int nx = mPos.x + dir.offsetX;
                                int ny = mPos.y + dir.offsetY;
                                int nz = mPos.z + dir.offsetZ;
                                BlockPosCoord neighborPos = new BlockPosCoord(nx, ny, nz);

                                if (groupCoords.contains(neighborPos)) {
                                    continue;
                                }

                                if (mTile != null && !mTile.isConnected(dir)) {
                                    continue;
                                }

                                boolean isConnected = isExternalBlockConnectedTo(
                                    world,
                                    nx,
                                    ny,
                                    nz,
                                    dir.getOpposite(),
                                    targetX,
                                    targetY,
                                    targetZ,
                                    targetSuctionSide,
                                    startX,
                                    startY,
                                    startZ,
                                    startDir);
                                if (!isConnected) {
                                    continue;
                                }

                                if (dir != groupInputSide && dir != groupOutputSide) {
                                    finishWithFailure(
                                        CoolantLocalization.format(
                                            "coolantloops.crawler.manifold_unauthorized_dir",
                                            mPos.x,
                                            mPos.y,
                                            mPos.z,
                                            dir,
                                            groupInputSide,
                                            groupOutputSide));
                                    return true;
                                }

                                if (dir == groupInputSide) {
                                    if (!mg.inputPorts.contains(neighborPos)) {
                                        mg.inputPorts.add(neighborPos);
                                    }
                                } else if (dir == groupOutputSide) {
                                    if (!mg.outputPorts.contains(neighborPos)) {
                                        mg.outputPorts.add(neighborPos);
                                    }
                                }
                            }
                        }

                        if (mg.outputPorts.isEmpty()) {
                            finishWithFailure(
                                CoolantLocalization.format(
                                    "coolantloops.crawler.manifold_group_no_output",
                                    step.x,
                                    step.y,
                                    step.z,
                                    groupOutputSide));
                            return true;
                        }
                        if (mg.inputPorts.isEmpty()) {
                            finishWithFailure(
                                CoolantLocalization.format(
                                    "coolantloops.crawler.manifold_group_no_input",
                                    step.x,
                                    step.y,
                                    step.z,
                                    groupInputSide));
                            return true;
                        }

                        for (BlockPosCoord c : groupCoords) {
                            coordToManifoldGroup.put(c, mg);
                            visitedPipeAndDevicePositions.add(c);
                        }
                        for (TileEntityManifold tile : groupTiles) {
                            tile.setInputSide(groupInputSide);
                            tile.setOutputSide(groupOutputSide);
                            if (!devices.contains(tile)) {
                                devices.add(tile);
                            }
                            LoopSegment manSeg = new LoopSegment(
                                tile.getDeviceId(),
                                1.0,
                                0.10,
                                0.000045,
                                tile.getMinorLossK(),
                                500.0,
                                2000.0,
                                tile.getDeviceTemperatureCelsius());
                            manSeg.setPosition(tile.xCoord, tile.yCoord, tile.zCoord);
                            segments.add(manSeg);
                        }
                        allManifoldGroups.add(mg);

                        addGraphNode(graphNodes, mg.getGroupId(), NodeType.MANIFOLD_GROUP, mg);
                        addGraphEdge(graphEdges, reverseGraphEdges, step.fromNodeId, mg.getGroupId());

                        for (BlockPosCoord outPort : mg.outputPorts) {
                            frontier.add(
                                new FrontierStep(
                                    mg.getGroupId(),
                                    outPort.x,
                                    outPort.y,
                                    outPort.z,
                                    groupOutputSide.getOpposite(),
                                    null,
                                    null));
                        }
                    } else {
                        if (step.entrySide != mg.inputSide) {
                            finishWithFailure(
                                CoolantLocalization.format(
                                    "coolantloops.crawler.manifold_group_wrong_side",
                                    step.x,
                                    step.y,
                                    step.z,
                                    step.entrySide,
                                    mg.inputSide));
                            return true;
                        }
                        if (!mf.isConnected(step.entrySide)) {
                            finishWithFailure(
                                CoolantLocalization.format(
                                    "coolantloops.crawler.manifold_disabled",
                                    step.x,
                                    step.y,
                                    step.z,
                                    step.entrySide));
                            return true;
                        }
                        addGraphEdge(graphEdges, reverseGraphEdges, step.fromNodeId, mg.getGroupId());
                    }
                    continue;
                }

                // 2. Insulated Coolant Pipe Handling
                if (te instanceof BaseMetaPipeEntity) {
                    BaseMetaPipeEntity bmpe = (BaseMetaPipeEntity) te;
                    if (!(bmpe.getMetaTileEntity() instanceof MTECoolantPipe)) {
                        finishWithFailure(
                            CoolantLocalization
                                .format("coolantloops.crawler.not_coolant_pipe", step.x, step.y, step.z));
                        return true;
                    }
                    MTECoolantPipe pipe = (MTECoolantPipe) bmpe.getMetaTileEntity();
                    lastPipe = pipe;

                    BlockPosCoord pCoord = new BlockPosCoord(step.x, step.y, step.z);
                    if (visitedPipeAndDevicePositions.contains(pCoord)) {
                        finishWithFailure(
                            CoolantLocalization
                                .format("coolantloops.crawler.pipe_merge_needs_manifold", step.x, step.y, step.z));
                        return true;
                    }
                    visitedPipeAndDevicePositions.add(pCoord);

                    Materials pipeMat = pipe.mMaterial;
                    if (!isAllowedCoolantPipeMaterial(pipeMat)) {
                        finishWithFailure(
                            CoolantLocalization.format(
                                "coolantloops.crawler.pipe_mat_not_allowed",
                                pipeMat != null ? pipeMat.mDefaultLocalName : "unknown",
                                step.x,
                                step.y,
                                step.z));
                        return true;
                    }

                    if (detectedLoopMaterial == null) {
                        detectedLoopMaterial = pipeMat;
                    } else if (!areMaterialsEqual(pipeMat, detectedLoopMaterial)) {
                        if (expectedTankMaterial != null
                            && areMaterialsEqual(detectedLoopMaterial, expectedTankMaterial)) {
                            finishWithFailure(
                                CoolantLocalization.format(
                                    "coolantloops.crawler.pipe_mat_tank_mismatch",
                                    step.x,
                                    step.y,
                                    step.z,
                                    pipeMat != null ? pipeMat.mDefaultLocalName : "unknown",
                                    expectedTankMaterial.mDefaultLocalName));
                        } else {
                            finishWithFailure(
                                CoolantLocalization.format(
                                    "coolantloops.crawler.pipe_mat_loop_mismatch",
                                    step.x,
                                    step.y,
                                    step.z,
                                    pipeMat != null ? pipeMat.mDefaultLocalName : "unknown",
                                    detectedLoopMaterial.mDefaultLocalName));
                        }
                        return true;
                    }

                    Float currThickness = step.segmentThickness;
                    Integer currAmount = step.segmentPipeAmount;
                    if (currThickness == null) {
                        currThickness = pipe.mThickNess;
                        currAmount = pipe.mPipeAmount;
                    } else {
                        if (Math.abs(pipe.mThickNess - currThickness) > 1e-4 || pipe.mPipeAmount != currAmount) {
                            finishWithFailure(
                                CoolantLocalization.format(
                                    "coolantloops.crawler.pipe_size_mismatch",
                                    step.x,
                                    step.y,
                                    step.z,
                                    currThickness,
                                    currAmount,
                                    pipe.mThickNess,
                                    pipe.mPipeAmount));
                            return true;
                        }
                    }

                    if (dischargePipeCapacityLPerSec < 0.0 && step.fromNodeId.startsWith("pump_disc_")) {
                        dischargePipeCapacityLPerSec = pipe.getCapacity();
                    }

                    if (pipe.mFluids != null) {
                        for (FluidStack fs : pipe.mFluids) {
                            if (fs != null && fs.amount > 0) {
                                finishWithFailure(
                                    CoolantLocalization.format(
                                        "coolantloops.crawler.pipe_forge_fluid",
                                        step.x,
                                        step.y,
                                        step.z,
                                        fs.getFluid()
                                            .getName(),
                                        fs.amount));
                                return true;
                            }
                        }
                    }

                    if (!isPipeConnectedAtSide(
                        world,
                        step.x,
                        step.y,
                        step.z,
                        bmpe,
                        pipe,
                        step.entrySide,
                        targetX,
                        targetY,
                        targetZ)) {
                        finishWithFailure(
                            CoolantLocalization.format(
                                "coolantloops.crawler.pipe_not_connected_back",
                                step.x,
                                step.y,
                                step.z,
                                step.entrySide));
                        return true;
                    }

                    CoolantPipingRegistry.PipeProperties props = CoolantPipingRegistry.getProperties(pipe);
                    LoopSegment seg = new LoopSegment(
                        String.format("pipe_%d_%d_%d", step.x, step.y, step.z),
                        1.0,
                        props.diameter,
                        props.roughness,
                        0.05,
                        props.maxPressureBar,
                        props.maxTemperatureCelsius,
                        20.0);
                    if (pipe.mCapacity > 0) {
                        seg.setCapacityLiters(pipe.mCapacity);
                    }
                    seg.setFlowSizeWeight(getPipeSizeWeight(pipe.mThickNess) * Math.max(1, pipe.mPipeAmount));
                    seg.setPosition(step.x, step.y, step.z);
                    segments.add(seg);

                    String pipeNodeId = String.format("pipe_%d_%d_%d", step.x, step.y, step.z);
                    addGraphNode(graphNodes, pipeNodeId, NodeType.PIPE, pCoord);
                    addGraphEdge(graphEdges, reverseGraphEdges, step.fromNodeId, pipeNodeId);

                    List<ForgeDirection> forwardDirs = new ArrayList<>();
                    for (ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
                        if (dir == step.entrySide) continue;
                        if (isPipeConnectedAtSide(
                            world,
                            step.x,
                            step.y,
                            step.z,
                            bmpe,
                            pipe,
                            dir,
                            targetX,
                            targetY,
                            targetZ)) {
                            int nx = step.x + dir.offsetX;
                            int ny = step.y + dir.offsetY;
                            int nz = step.z + dir.offsetZ;
                            if (isValidLoopBlock(world, nx, ny, nz, targetX, targetY, targetZ)) {
                                forwardDirs.add(dir);
                            }
                        }
                    }

                    if (forwardDirs.isEmpty()) {
                        finishWithFailure(
                            CoolantLocalization
                                .format("coolantloops.crawler.pipe_dead_end", step.x, step.y, step.z, step.entrySide));
                        return true;
                    }
                    if (forwardDirs.size() > 1) {
                        finishWithFailure(
                            CoolantLocalization
                                .format("coolantloops.crawler.pipe_split_needs_manifold", step.x, step.y, step.z));
                        return true;
                    }

                    ForgeDirection nextDir = forwardDirs.get(0);
                    pipeConfigs.add(new PipeFlowConfig(bmpe, pipe, nextDir));

                    frontier.add(
                        new FrontierStep(
                            pipeNodeId,
                            step.x + nextDir.offsetX,
                            step.y + nextDir.offsetY,
                            step.z + nextDir.offsetZ,
                            nextDir.getOpposite(),
                            currThickness,
                            currAmount));
                    continue;
                }

                // 3. GregTech Multiblock Hatches (PHE, Degasser, Bath, Core Passage)
                if (te instanceof IGregTechTileEntity) {
                    IMetaTileEntity mte = ((IGregTechTileEntity) te).getMetaTileEntity();
                    if (mte instanceof MTEHatchPressurizedFluid) {
                        MTEHatchPressurizedFluid hatch = (MTEHatchPressurizedFluid) mte;
                        MTEPressurizedHeatExchanger phe = findAdjacentPHE(world, step.x, step.y, step.z);
                        MTEPressurizedDegasser degasser = (phe == null)
                            ? findAdjacentDegasser(world, step.x, step.y, step.z)
                            : null;
                        MTECoolantBath bath = (phe == null && degasser == null)
                            ? findAdjacentCoolantBath(world, step.x, step.y, step.z)
                            : null;

                        if (phe != null) {
                            if (phe.getPrimaryOutlet() == null && phe.getBaseMetaTileEntity() != null) {
                                phe.checkMachine(phe.getBaseMetaTileEntity(), null);
                            }
                            if (!devices.contains(phe)) {
                                devices.add(phe);
                            }
                            LoopSegment devSeg = new LoopSegment(
                                phe.getDeviceId(),
                                2.0,
                                0.12,
                                0.000045,
                                phe.getMinorLossK(),
                                500.0,
                                2000.0,
                                phe.getDeviceTemperatureCelsius());
                            devSeg.setPosition(step.x, step.y, step.z);
                            segments.add(devSeg);

                            MTEHatchPressurizedFluid outlet = phe.getPrimaryOutlet();
                            if (outlet != null && outlet != hatch) {
                                IGregTechTileEntity outTE = outlet.getBaseMetaTileEntity();
                                visitedPipeAndDevicePositions
                                    .add(new BlockPosCoord(outTE.getXCoord(), outTE.getYCoord(), outTE.getZCoord()));
                                ForgeDirection outFacing = outTE.getFrontFacing();

                                addGraphNode(graphNodes, phe.getDeviceId(), NodeType.DEVICE, phe);
                                addGraphEdge(graphEdges, reverseGraphEdges, step.fromNodeId, phe.getDeviceId());

                                frontier.add(
                                    new FrontierStep(
                                        phe.getDeviceId(),
                                        outTE.getXCoord() + outFacing.offsetX,
                                        outTE.getYCoord() + outFacing.offsetY,
                                        outTE.getZCoord() + outFacing.offsetZ,
                                        outFacing.getOpposite(),
                                        null,
                                        null));
                                continue;
                            } else {
                                finishWithFailure(
                                    CoolantLocalization
                                        .format("coolantloops.crawler.phe_unformed", step.x, step.y, step.z));
                                return true;
                            }
                        } else if (degasser != null) {
                            if (degasser.getPrimaryOutlet() == null && degasser.getBaseMetaTileEntity() != null) {
                                degasser.checkMachine(degasser.getBaseMetaTileEntity(), null);
                            }
                            if (pump != null) {
                                degasser.setConnectedPump(pump);
                            }
                            if (!devices.contains(degasser)) {
                                devices.add(degasser);
                            }
                            LoopSegment devSeg = new LoopSegment(
                                degasser.getDeviceId(),
                                2.0,
                                0.12,
                                0.000045,
                                degasser.getMinorLossK(),
                                500.0,
                                2000.0,
                                degasser.getDeviceTemperatureCelsius());
                            devSeg.setPosition(step.x, step.y, step.z);
                            segments.add(devSeg);

                            MTEHatchPressurizedFluid outlet = degasser.getPrimaryOutlet();
                            if (outlet != null && outlet != hatch) {
                                IGregTechTileEntity outTE = outlet.getBaseMetaTileEntity();
                                visitedPipeAndDevicePositions
                                    .add(new BlockPosCoord(outTE.getXCoord(), outTE.getYCoord(), outTE.getZCoord()));
                                ForgeDirection outFacing = outTE.getFrontFacing();

                                addGraphNode(graphNodes, degasser.getDeviceId(), NodeType.DEVICE, degasser);
                                addGraphEdge(graphEdges, reverseGraphEdges, step.fromNodeId, degasser.getDeviceId());

                                frontier.add(
                                    new FrontierStep(
                                        degasser.getDeviceId(),
                                        outTE.getXCoord() + outFacing.offsetX,
                                        outTE.getYCoord() + outFacing.offsetY,
                                        outTE.getZCoord() + outFacing.offsetZ,
                                        outFacing.getOpposite(),
                                        null,
                                        null));
                                continue;
                            } else {
                                finishWithFailure(
                                    CoolantLocalization
                                        .format("coolantloops.crawler.degasser_unformed", step.x, step.y, step.z));
                                return true;
                            }
                        } else if (bath != null) {
                            if (bath.getPrimaryOutlet() == null && bath.getBaseMetaTileEntity() != null) {
                                bath.checkMachine(bath.getBaseMetaTileEntity(), null);
                            }
                            if (pump != null) {
                                bath.setLoopPump(pump);
                            }
                            if (!devices.contains(bath)) {
                                devices.add(bath);
                            }
                            LoopSegment devSeg = new LoopSegment(
                                bath.getDeviceId(),
                                3.0,
                                0.20,
                                0.000045,
                                bath.getMinorLossK(),
                                1000.0,
                                3000.0,
                                bath.getDeviceTemperatureCelsius());
                            devSeg.setCapacityLiters(bath.getFillVolumeLiters());
                            devSeg.setCustomVolumeM3(bath.getFillVolumeLiters() / 1000.0);
                            devSeg.setPosition(step.x, step.y, step.z);
                            segments.add(devSeg);

                            MTEHatchPressurizedFluid outlet = bath.getPrimaryOutlet();
                            if (outlet != null && outlet != hatch) {
                                IGregTechTileEntity outTE = outlet.getBaseMetaTileEntity();
                                visitedPipeAndDevicePositions
                                    .add(new BlockPosCoord(outTE.getXCoord(), outTE.getYCoord(), outTE.getZCoord()));
                                ForgeDirection outFacing = outTE.getFrontFacing();

                                addGraphNode(graphNodes, bath.getDeviceId(), NodeType.DEVICE, bath);
                                addGraphEdge(graphEdges, reverseGraphEdges, step.fromNodeId, bath.getDeviceId());

                                frontier.add(
                                    new FrontierStep(
                                        bath.getDeviceId(),
                                        outTE.getXCoord() + outFacing.offsetX,
                                        outTE.getYCoord() + outFacing.offsetY,
                                        outTE.getZCoord() + outFacing.offsetZ,
                                        outFacing.getOpposite(),
                                        null,
                                        null));
                                continue;
                            } else {
                                finishWithFailure(
                                    CoolantLocalization
                                        .format("coolantloops.crawler.bath_unformed", step.x, step.y, step.z));
                                return true;
                            }
                        }
                    } else if (mte instanceof ICoolantPassageHatch) {
                        ICoolantPassageHatch passage = (ICoolantPassageHatch) mte;
                        if (!passage.isPassageInlet()) {
                            finishWithFailure(
                                CoolantLocalization
                                    .format("coolantloops.crawler.passage_outlet", step.x, step.y, step.z));
                            return true;
                        }
                        IGregTechTileEntity outTE = passage.getOppositeHatchTile();
                        if (outTE == null) {
                            finishWithFailure(
                                CoolantLocalization
                                    .format("coolantloops.crawler.passage_no_pair", step.x, step.y, step.z));
                            return true;
                        }
                        if (pump != null) {
                            passage.setConnectedPump(pump);
                        }
                        if (!devices.contains(passage)) {
                            devices.add(passage);
                        }
                        LoopSegment devSeg = new LoopSegment(
                            passage.getDeviceId(),
                            5.0,
                            0.10,
                            0.000045,
                            passage.getMinorLossK(),
                            500.0,
                            3200.0,
                            passage.getDeviceTemperatureCelsius());
                        devSeg.setPosition(step.x, step.y, step.z);
                        segments.add(devSeg);

                        visitedPipeAndDevicePositions
                            .add(new BlockPosCoord(outTE.getXCoord(), outTE.getYCoord(), outTE.getZCoord()));
                        ForgeDirection outFacing = outTE.getFrontFacing();

                        addGraphNode(graphNodes, passage.getDeviceId(), NodeType.DEVICE, passage);
                        addGraphEdge(graphEdges, reverseGraphEdges, step.fromNodeId, passage.getDeviceId());

                        frontier.add(
                            new FrontierStep(
                                passage.getDeviceId(),
                                outTE.getXCoord() + outFacing.offsetX,
                                outTE.getYCoord() + outFacing.offsetY,
                                outTE.getZCoord() + outFacing.offsetZ,
                                outFacing.getOpposite(),
                                null,
                                null));
                        continue;
                    }
                }

                // 4. In-Line Coolant Loop Devices (Instruments, Reservoir)
                ICoolantLoopDevice device = null;
                if (te instanceof ICoolantLoopDevice) {
                    device = (ICoolantLoopDevice) te;
                } else if (te instanceof IGregTechTileEntity) {
                    IMetaTileEntity mte = ((IGregTechTileEntity) te).getMetaTileEntity();
                    if (mte instanceof ICoolantLoopDevice) {
                        device = (ICoolantLoopDevice) mte;
                    }
                }

                if (device != null) {
                    BlockPosCoord devCoord = new BlockPosCoord(step.x, step.y, step.z);
                    if (visitedPipeAndDevicePositions.contains(devCoord)) {
                        finishWithFailure(
                            CoolantLocalization
                                .format("coolantloops.crawler.device_merge_needs_manifold", step.x, step.y, step.z));
                        return true;
                    }
                    visitedPipeAndDevicePositions.add(devCoord);

                    if (pump != null) {
                        device.setLoopPump(pump);
                    }
                    if (!devices.contains(device)) {
                        devices.add(device);
                    }

                    LoopSegment devSeg = new LoopSegment(
                        device.getDeviceId(),
                        1.0,
                        0.10,
                        0.000045,
                        device.getMinorLossK(),
                        500.0,
                        2000.0,
                        device.getDeviceTemperatureCelsius());
                    devSeg.setPosition(step.x, step.y, step.z);
                    segments.add(devSeg);

                    addGraphNode(graphNodes, device.getDeviceId(), NodeType.DEVICE, device);
                    addGraphEdge(graphEdges, reverseGraphEdges, step.fromNodeId, device.getDeviceId());

                    ForgeDirection nextDir = findNextDirection(
                        world,
                        step.entrySide,
                        step.x,
                        step.y,
                        step.z,
                        targetX,
                        targetY,
                        targetZ,
                        visitedPipeAndDevicePositions);
                    if (nextDir == ForgeDirection.UNKNOWN) {
                        nextDir = step.entrySide.getOpposite();
                    }

                    frontier.add(
                        new FrontierStep(
                            device.getDeviceId(),
                            step.x + nextDir.offsetX,
                            step.y + nextDir.offsetY,
                            step.z + nextDir.offsetZ,
                            nextDir.getOpposite(),
                            null,
                            null));
                    continue;
                }

                finishWithFailure(
                    CoolantLocalization.format(
                        "coolantloops.crawler.unknown_block",
                        step.x,
                        step.y,
                        step.z,
                        te.getClass()
                            .getSimpleName()));
                return true;
            }

            if (frontier.isEmpty()) {
                finishCrawl();
                return true;
            }

            return false;
        }

        private List<LoopSegment> getSegmentsForNode(String nodeId, Map<String, LoopSegment> segmentMap) {
            List<LoopSegment> list = new ArrayList<>();
            FlowNode node = graphNodes.get(nodeId);
            if (node != null && node.type == NodeType.MANIFOLD_GROUP && node.data instanceof ManifoldGroup) {
                ManifoldGroup mg = (ManifoldGroup) node.data;
                for (TileEntityManifold tile : mg.tiles) {
                    LoopSegment s = segmentMap.get(tile.getDeviceId());
                    if (s != null) {
                        list.add(s);
                    }
                }
            } else {
                LoopSegment s = segmentMap.get(nodeId);
                if (s != null) {
                    list.add(s);
                }
            }
            return list;
        }

        private double getNodeFlowSizeWeight(String nodeId, Map<String, LoopSegment> segmentMap) {
            List<LoopSegment> segs = getSegmentsForNode(nodeId, segmentMap);
            if (segs.isEmpty()) return 1.0;
            return segs.get(0)
                .getFlowSizeWeight();
        }

        private void distributeFlowAndWireUpstreams() {
            Map<String, LoopSegment> segmentMap = new HashMap<>();
            for (LoopSegment seg : segments) {
                segmentMap.put(seg.getId(), seg);
            }

            Map<String, Integer> inDegree = new HashMap<>();
            for (String nodeId : graphNodes.keySet()) {
                inDegree.put(nodeId, 0);
            }
            for (Map.Entry<String, List<String>> entry : graphEdges.entrySet()) {
                for (String target : entry.getValue()) {
                    if (inDegree.containsKey(target)) {
                        inDegree.put(target, inDegree.get(target) + 1);
                    }
                }
            }

            Queue<String> topoQueue = new ArrayDeque<>();
            Map<String, Double> nodeFlow = new HashMap<>();

            for (String nodeId : graphNodes.keySet()) {
                if (inDegree.get(nodeId) == 0) {
                    topoQueue.add(nodeId);
                }
            }
            nodeFlow.put(sourceNodeId, 1.0);

            while (!topoQueue.isEmpty()) {
                String u = topoQueue.poll();
                double flowU = nodeFlow.getOrDefault(u, 0.0);

                List<String> succs = graphEdges.get(u);
                if (succs != null && !succs.isEmpty()) {
                    if (succs.size() == 1) {
                        String v = succs.get(0);
                        nodeFlow.put(v, nodeFlow.getOrDefault(v, 0.0) + flowU);
                        int deg = inDegree.get(v) - 1;
                        inDegree.put(v, deg);
                        if (deg == 0) topoQueue.add(v);
                    } else {
                        double totalWeight = 0.0;
                        double[] weights = new double[succs.size()];
                        for (int i = 0; i < succs.size(); i++) {
                            weights[i] = getNodeFlowSizeWeight(succs.get(i), segmentMap);
                            totalWeight += weights[i];
                        }
                        for (int i = 0; i < succs.size(); i++) {
                            String v = succs.get(i);
                            double fraction = (totalWeight > 0.0) ? (weights[i] / totalWeight) : (1.0 / succs.size());
                            double branchFlow = flowU * fraction;
                            nodeFlow.put(v, nodeFlow.getOrDefault(v, 0.0) + branchFlow);
                            int deg = inDegree.get(v) - 1;
                            inDegree.put(v, deg);
                            if (deg == 0) topoQueue.add(v);
                        }
                    }
                }
            }

            for (String nodeId : graphNodes.keySet()) {
                double flow = nodeFlow.getOrDefault(nodeId, 1.0);
                List<LoopSegment> segs = getSegmentsForNode(nodeId, segmentMap);
                if (segs.isEmpty()) continue;
                FlowNode fn = graphNodes.get(nodeId);
                if (fn != null && fn.type == NodeType.MANIFOLD_GROUP && segs.size() > 1) {
                    double perTileFlow = flow / segs.size();
                    for (LoopSegment s : segs) {
                        s.setFlowFraction(perTileFlow);
                    }
                } else {
                    for (LoopSegment s : segs) {
                        s.setFlowFraction(flow);
                    }
                }
            }

            for (LoopSegment seg : segments) {
                seg.clearUpstreamSegments();
            }

            for (Map.Entry<String, List<String>> entry : graphEdges.entrySet()) {
                String u = entry.getKey();
                for (String v : entry.getValue()) {
                    if (u.equals(sourceNodeId) || v.equals(sinkNodeId)) {
                        continue;
                    }
                    List<LoopSegment> upSegs = getSegmentsForNode(u, segmentMap);
                    List<LoopSegment> downSegs = getSegmentsForNode(v, segmentMap);
                    for (LoopSegment down : downSegs) {
                        for (LoopSegment up : upSegs) {
                            down.addUpstreamSegment(up);
                        }
                    }
                }
            }

            List<String> suctionPreds = reverseGraphEdges.get(sinkNodeId);
            List<String> dischargeSuccs = graphEdges.get(sourceNodeId);
            if (suctionPreds != null && dischargeSuccs != null) {
                for (String sucId : suctionPreds) {
                    List<LoopSegment> sucSegs = getSegmentsForNode(sucId, segmentMap);
                    for (String discId : dischargeSuccs) {
                        List<LoopSegment> discSegs = getSegmentsForNode(discId, segmentMap);
                        for (LoopSegment discSeg : discSegs) {
                            for (LoopSegment sucSeg : sucSegs) {
                                discSeg.addUpstreamSegment(sucSeg);
                            }
                        }
                    }
                }
            }
        }

        private void finishCrawl() {
            // 1. Verify that the Sink was reached
            if (!graphNodes.containsKey(sinkNodeId)) {
                finishWithFailure(CoolantLocalization.get("coolantloops.crawler.no_suction_path"));
                return;
            }

            // 2. Reverse reachability from Sink
            Set<String> canReachSink = new HashSet<>();
            Queue<String> revQueue = new ArrayDeque<>();
            revQueue.add(sinkNodeId);
            canReachSink.add(sinkNodeId);

            while (!revQueue.isEmpty()) {
                String u = revQueue.poll();
                List<String> preds = reverseGraphEdges.get(u);
                if (preds != null) {
                    for (String p : preds) {
                        if (canReachSink.add(p)) {
                            revQueue.add(p);
                        }
                    }
                }
            }

            for (String nodeId : graphNodes.keySet()) {
                if (!canReachSink.contains(nodeId)) {
                    finishWithFailure(
                        CoolantLocalization.format("coolantloops.crawler.branch_not_reaching_suction", nodeId));
                    return;
                }
            }

            // 3. Cycle Detection
            Map<String, Integer> colors = new HashMap<>();
            Map<String, String> parents = new HashMap<>();
            List<String> cycle = new ArrayList<>();

            if (dfsCheckCycle(sourceNodeId, graphEdges, colors, parents, cycle)) {
                boolean involvesManifold = false;
                for (String cId : cycle) {
                    FlowNode fn = graphNodes.get(cId);
                    if (fn != null && fn.type == NodeType.MANIFOLD_GROUP) {
                        involvesManifold = true;
                        break;
                    }
                }
                if (involvesManifold) {
                    finishWithFailure(
                        CoolantLocalization.format("coolantloops.crawler.cycle_detected", String.join(" -> ", cycle)));
                } else {
                    finishWithFailure(
                        CoolantLocalization.format("coolantloops.crawler.cycle_generic", String.join(" -> ", cycle)));
                }
                return;
            }

            // 4. Update pump ownership on all claimed manifold blocks
            for (ManifoldGroup mg : allManifoldGroups) {
                for (TileEntityManifold tile : mg.tiles) {
                    tile.setActivePumpId(pumpId);
                }
            }

            // 5. Apply directional flow configurations to pipes
            for (PipeFlowConfig pfc : pipeConfigs) {
                pfc.apply();
            }

            // 6. Calculate topological flow fraction distribution and upstream segment connections
            distributeFlowAndWireUpstreams();

            // 7. Max pump pipe capacity
            double maxPumpPipeCap = (dischargePipeCapacityLPerSec > 0 && suctionPipeCapacityLPerSec > 0)
                ? Math.min(dischargePipeCapacityLPerSec, suctionPipeCapacityLPerSec)
                : Math.max(dischargePipeCapacityLPerSec, suctionPipeCapacityLPerSec);
            if (maxPumpPipeCap <= 0) {
                maxPumpPipeCap = Double.MAX_VALUE;
            }

            this.result = CrawlResult.success(
                segments,
                devices,
                visitedPipeAndDevicePositions,
                maxPumpPipeCap,
                detectedLoopMaterial,
                pipeConfigs,
                graphNodes,
                graphEdges);
            this.isDone = true;
        }
    }

    /**
     * Crawls the loop from a starting port (discharge) until returning to the target port (suction),
     * enforcing DAG topology with single source and sink, manifold group discovery, and unidirectional alignment.
     */
    public CrawlResult crawl(int startX, int startY, int startZ, ForgeDirection startDir, int targetX, int targetY,
        int targetZ, ForgeDirection targetSuctionSide, String pumpId, ICoolantLoopPump pump,
        Materials expectedTankMaterial) {

        CrawlSession session = startSession(
            startX,
            startY,
            startZ,
            startDir,
            targetX,
            targetY,
            targetZ,
            targetSuctionSide,
            pumpId,
            pump,
            expectedTankMaterial);

        while (!session.isDone()) {
            session.step(STEPS_PER_TICK);
        }

        return session.getResult();
    }

    public static MTEPressurizedHeatExchanger findAdjacentPHE(World world, int x, int y, int z) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                TileEntity te = world.getTileEntity(x + dx, y, z + dz);
                if (te instanceof IGregTechTileEntity) {
                    IMetaTileEntity mte = ((IGregTechTileEntity) te).getMetaTileEntity();
                    if (mte instanceof MTEPressurizedHeatExchanger) {
                        return (MTEPressurizedHeatExchanger) mte;
                    }
                }
            }
        }
        return null;
    }

    public static MTEPressurizedDegasser findAdjacentDegasser(World world, int x, int y, int z) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                TileEntity te = world.getTileEntity(x + dx, y, z + dz);
                if (te instanceof IGregTechTileEntity) {
                    IMetaTileEntity mte = ((IGregTechTileEntity) te).getMetaTileEntity();
                    if (mte instanceof MTEPressurizedDegasser) {
                        return (MTEPressurizedDegasser) mte;
                    }
                }
            }
        }
        return null;
    }

    public static MTECoolantBath findAdjacentCoolantBath(World world, int x, int y, int z) {
        for (int dy = -2; dy <= 1; dy++) {
            for (int dx = -5; dx <= 5; dx++) {
                for (int dz = -12; dz <= 12; dz++) {
                    TileEntity te = world.getTileEntity(x + dx, y + dy, z + dz);
                    if (te instanceof IGregTechTileEntity) {
                        IMetaTileEntity mte = ((IGregTechTileEntity) te).getMetaTileEntity();
                        if (mte instanceof MTECoolantBath) {
                            return (MTECoolantBath) mte;
                        }
                    }
                }
            }
        }
        return null;
    }

    public static boolean isValidLoopBlock(World world, int x, int y, int z, int targetX, int targetY, int targetZ) {
        if (x == targetX && y == targetY && z == targetZ) return true;
        TileEntity te = world.getTileEntity(x, y, z);
        if (te == null) return false;
        if (te instanceof BaseMetaPipeEntity) return true;
        if (te instanceof ICoolantLoopDevice) return true;
        if (te instanceof IGregTechTileEntity) {
            IMetaTileEntity mte = ((IGregTechTileEntity) te).getMetaTileEntity();
            return mte instanceof ICoolantLoopDevice || mte instanceof MTEHatchPressurizedFluid;
        }
        return false;
    }

    public static boolean isPipeConnectedAtSide(BaseMetaPipeEntity bmpe, MTEFluidPipe pipe, ForgeDirection side) {
        if (side == null || side == ForgeDirection.UNKNOWN) return false;
        int flag = 1 << side.ordinal();
        if (pipe != null) {
            if ((pipe.mConnections & flag) != 0) return true;
            try {
                if (pipe.isConnectedAtSide(side)) return true;
            } catch (Throwable ignored) {}
        }
        if (bmpe != null) {
            if ((bmpe.mConnections & flag) != 0) return true;
        }
        return false;
    }

    public static boolean isPipeConnectedAtSide(World world, int x, int y, int z, BaseMetaPipeEntity bmpe,
        MTEFluidPipe pipe, ForgeDirection side, int targetX, int targetY, int targetZ) {
        if (isPipeConnectedAtSide(bmpe, pipe, side)) return true;
        if (world != null && side != null && side != ForgeDirection.UNKNOWN) {
            int nx = x + side.offsetX;
            int ny = y + side.offsetY;
            int nz = z + side.offsetZ;
            if (nx == targetX && ny == targetY && nz == targetZ) {
                return true;
            }
        }
        return false;
    }

    public static ForgeDirection findNextPipeDirection(World world, BaseMetaPipeEntity bmpe, MTEFluidPipe pipe,
        ForgeDirection incomingSide, int x, int y, int z, int targetX, int targetY, int targetZ,
        Set<BlockPosCoord> visited) {

        // 1. Check if the target suction port is adjacent in any connected direction!
        for (ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
            if (dir == incomingSide) continue;
            if (!isPipeConnectedAtSide(world, x, y, z, bmpe, pipe, dir, targetX, targetY, targetZ)) continue;
            int nx = x + dir.offsetX;
            int ny = y + dir.offsetY;
            int nz = z + dir.offsetZ;
            if (nx == targetX && ny == targetY && nz == targetZ) {
                return dir;
            }
        }

        // 2. Check straight ahead if connected and UNVISITED
        ForgeDirection straight = incomingSide.getOpposite();
        if (isPipeConnectedAtSide(world, x, y, z, bmpe, pipe, straight, targetX, targetY, targetZ)) {
            int sx = x + straight.offsetX;
            int sy = y + straight.offsetY;
            int sz = z + straight.offsetZ;
            if (!visited.contains(new BlockPosCoord(sx, sy, sz))
                && isValidLoopBlock(world, sx, sy, sz, targetX, targetY, targetZ)) {
                return straight;
            }
        }

        // 3. Check other forward directions that are connected and UNVISITED
        for (ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
            if (dir == incomingSide || dir == straight) continue;
            if (!isPipeConnectedAtSide(world, x, y, z, bmpe, pipe, dir, targetX, targetY, targetZ)) continue;
            int nx = x + dir.offsetX;
            int ny = y + dir.offsetY;
            int nz = z + dir.offsetZ;
            if (!visited.contains(new BlockPosCoord(nx, ny, nz))
                && isValidLoopBlock(world, nx, ny, nz, targetX, targetY, targetZ)) {
                return dir;
            }
        }

        return ForgeDirection.UNKNOWN;
    }

    public static ForgeDirection findNextDirection(World world, ForgeDirection incomingSide, int x, int y, int z,
        int targetX, int targetY, int targetZ, Set<BlockPosCoord> visited) {
        // 1. Check if the target suction port is adjacent in any valid forward direction!
        for (ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
            if (dir == incomingSide) continue;
            int nx = x + dir.offsetX;
            int ny = y + dir.offsetY;
            int nz = z + dir.offsetZ;
            if (nx == targetX && ny == targetY && nz == targetZ) {
                return dir;
            }
        }

        // 2. Check straight ahead if valid and UNVISITED
        ForgeDirection straight = incomingSide.getOpposite();
        int sx = x + straight.offsetX;
        int sy = y + straight.offsetY;
        int sz = z + straight.offsetZ;
        if (!visited.contains(new BlockPosCoord(sx, sy, sz))
            && isValidLoopBlock(world, sx, sy, sz, targetX, targetY, targetZ)) {
            return straight;
        }

        // 3. Check other forward directions that are UNVISITED
        for (ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
            if (dir == incomingSide || dir == straight) continue;
            int nx = x + dir.offsetX;
            int ny = y + dir.offsetY;
            int nz = z + dir.offsetZ;
            if (!visited.contains(new BlockPosCoord(nx, ny, nz))
                && isValidLoopBlock(world, nx, ny, nz, targetX, targetY, targetZ)) {
                return dir;
            }
        }

        return ForgeDirection.UNKNOWN;
    }
}
