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
import com.gtnewhorizons.coolantloops.common.metatileentity.multi.MTECoolantPump;
import com.gtnewhorizons.coolantloops.common.metatileentity.multi.MTEPressurizedDegasser;
import com.gtnewhorizons.coolantloops.common.metatileentity.multi.MTEPressurizedHeatExchanger;
import com.gtnewhorizons.coolantloops.common.metatileentity.pipe.MTECoolantPipe;
import com.gtnewhorizons.coolantloops.common.tileentity.TileEntityManifold;

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

        public CrawlResult(boolean isSuccess, String failureReason, List<LoopSegment> segments,
            List<ICoolantLoopDevice> devices, Set<BlockPosCoord> pipePositions) {
            this(isSuccess, failureReason, segments, devices, pipePositions, Double.MAX_VALUE, null, null);
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
                null);
        }

        public CrawlResult(boolean isSuccess, String failureReason, List<LoopSegment> segments,
            List<ICoolantLoopDevice> devices, Set<BlockPosCoord> pipePositions, double maxPumpPipeCapacityLPerSec,
            Materials loopMaterial, List<PipeFlowConfig> pipeConfigs) {
            this.isSuccess = isSuccess;
            this.failureReason = failureReason;
            this.segments = segments != null ? segments : new ArrayList<>();
            this.devices = devices != null ? devices : new ArrayList<>();
            this.pipePositions = pipePositions != null ? pipePositions : new HashSet<>();
            this.maxPumpPipeCapacityLPerSec = maxPumpPipeCapacityLPerSec;
            this.loopMaterial = loopMaterial;
            this.pipeConfigs = pipeConfigs != null ? pipeConfigs : new ArrayList<>();
        }

        public static CrawlResult fail(String reason) {
            return new CrawlResult(false, reason, null, null, null, 0.0, null, null);
        }

        public static CrawlResult success(List<LoopSegment> segments, List<ICoolantLoopDevice> devices,
            Set<BlockPosCoord> pipePositions) {
            return new CrawlResult(true, null, segments, devices, pipePositions, Double.MAX_VALUE, null, null);
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
                pipeConfigs);
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

    private final World world;
    private final int maxDepth;

    public LoopGraphCrawler(World world, int maxDepth) {
        this.world = world;
        this.maxDepth = maxDepth > 0 ? maxDepth : 1000;
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

    private void addGraphNode(Map<String, FlowNode> graphNodes, String id, NodeType type, Object data) {
        if (!graphNodes.containsKey(id)) {
            graphNodes.put(id, new FlowNode(id, type, data));
        }
    }

    private void addGraphEdge(Map<String, List<String>> graphEdges, Map<String, List<String>> reverseGraphEdges,
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

    private boolean dfsCheckCycle(String u, Map<String, List<String>> graphEdges, Map<String, Integer> colors,
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

    /**
     * Crawls the loop from a starting port (discharge) until returning to the target port (suction),
     * enforcing DAG topology with single source and sink, manifold group discovery, and unidirectional alignment.
     */
    public CrawlResult crawl(int startX, int startY, int startZ, ForgeDirection startDir, int targetX, int targetY,
        int targetZ, ForgeDirection targetSuctionSide, String pumpId, ICoolantLoopPump pump,
        Materials expectedTankMaterial) {

        List<LoopSegment> segments = new ArrayList<>();
        List<ICoolantLoopDevice> devices = new ArrayList<>();
        Set<BlockPosCoord> visitedPipeAndDevicePositions = new HashSet<>();
        List<PipeFlowConfig> pipeConfigs = new ArrayList<>();
        Map<BlockPosCoord, ManifoldGroup> coordToManifoldGroup = new HashMap<>();
        Set<ManifoldGroup> allManifoldGroups = new LinkedHashSet<>();

        Map<String, FlowNode> graphNodes = new LinkedHashMap<>();
        Map<String, List<String>> graphEdges = new LinkedHashMap<>();
        Map<String, List<String>> reverseGraphEdges = new LinkedHashMap<>();

        Materials detectedLoopMaterial = expectedTankMaterial != null ? expectedTankMaterial
            : (pump != null ? pump.getTankMaterial() : null);
        double dischargePipeCapacityLPerSec = -1.0;
        double suctionPipeCapacityLPerSec = -1.0;
        MTEFluidPipe lastPipe = null;

        String sourceNodeId = String.format("pump_disc_%d_%d_%d", startX, startY, startZ);
        String sinkNodeId = String.format("pump_suc_%d_%d_%d", targetX, targetY, targetZ);
        addGraphNode(graphNodes, sourceNodeId, NodeType.SOURCE, null);

        int firstX = startX + startDir.offsetX;
        int firstY = startY + startDir.offsetY;
        int firstZ = startZ + startDir.offsetZ;
        ForgeDirection firstEntrySide = startDir.getOpposite();

        Queue<FrontierStep> frontier = new ArrayDeque<>();
        frontier.add(new FrontierStep(sourceNodeId, firstX, firstY, firstZ, firstEntrySide, null, null));

        int stepCount = 0;

        while (!frontier.isEmpty()) {
            if (stepCount >= maxDepth) {
                return CrawlResult.fail(String.format("Loop exceeds maximum supported length (%d steps)", maxDepth));
            }

            FrontierStep step = frontier.poll();

            // Check if we arrived at the pump suction port
            if (step.x == targetX && step.y == targetY && step.z == targetZ) {
                addGraphNode(graphNodes, sinkNodeId, NodeType.SINK, null);
                addGraphEdge(graphEdges, reverseGraphEdges, step.fromNodeId, sinkNodeId);
                if (lastPipe != null && suctionPipeCapacityLPerSec < 0.0) {
                    suctionPipeCapacityLPerSec = lastPipe.getCapacity();
                }
                stepCount++;
                continue;
            }

            TileEntity te = world.getTileEntity(step.x, step.y, step.z);
            if (te == null) {
                return CrawlResult
                    .fail(String.format("Loop broken: no tile entity at (%d, %d, %d)", step.x, step.y, step.z));
            }

            // 1. Manifold Group Structure Handling
            if (te instanceof TileEntityManifold) {
                TileEntityManifold mf = (TileEntityManifold) te;

                // Entry side check: must enter perpendicularly to the manifold plane (along normal axis)
                if (!mf.isNormalDirection(step.entrySide)) {
                    return CrawlResult.fail(
                        String.format(
                            "Invalid manifold connection: branch entered manifold at (%d, %d, %d) from side %s which is parallel to its plane (normal axis %s). Manifolds can only be entered and exited perpendicularly to their plane.",
                            step.x,
                            step.y,
                            step.z,
                            step.entrySide,
                            mf.getNormalAxisName()));
                }
                if (!mf.isConnected(step.entrySide)) {
                    return CrawlResult.fail(
                        String.format(
                            "Invalid manifold connection: manifold at (%d, %d, %d) has connection disabled on entry side %s!",
                            step.x,
                            step.y,
                            step.z,
                            step.entrySide));
                }

                BlockPosCoord mfCoord = new BlockPosCoord(step.x, step.y, step.z);
                ManifoldGroup mg = coordToManifoldGroup.get(mfCoord);

                if (mg == null) {
                    // Discover all contiguous adjacent in-plane manifold blocks with matching plane orientation and
                    // mutual connection
                    Set<BlockPosCoord> groupCoords = new LinkedHashSet<>();
                    List<TileEntityManifold> groupTiles = new ArrayList<>();
                    Queue<BlockPosCoord> mfQueue = new ArrayDeque<>();

                    groupCoords.add(mfCoord);
                    groupTiles.add(mf);
                    mfQueue.add(mfCoord);

                    while (!mfQueue.isEmpty()) {
                        BlockPosCoord p = mfQueue.poll();
                        TileEntity pte = world.getTileEntity(p.x, p.y, p.z);
                        TileEntityManifold pmf = (pte instanceof TileEntityManifold) ? (TileEntityManifold) pte : null;
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
                            return CrawlResult.fail(
                                String.format(
                                    "Manifold at (%d, %d, %d) is already part of another active coolant loop (%s)!",
                                    tile.xCoord,
                                    tile.yCoord,
                                    tile.zCoord,
                                    otherPump));
                        }
                    }

                    // All valid inputs must come from step.entrySide; valid outputs must leave opposite
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
                        TileEntityManifold mTile = (mteRaw instanceof TileEntityManifold) ? (TileEntityManifold) mteRaw
                            : null;

                        for (ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
                            int nx = mPos.x + dir.offsetX;
                            int ny = mPos.y + dir.offsetY;
                            int nz = mPos.z + dir.offsetZ;
                            BlockPosCoord neighborPos = new BlockPosCoord(nx, ny, nz);

                            if (groupCoords.contains(neighborPos)) {
                                continue; // Internal contiguous manifold adjacency
                            }

                            if (mTile != null && !mTile.isConnected(dir)) {
                                continue; // Connection disabled on this face
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

                            // Disallow connections in any direction other than input or output axis
                            if (dir != groupInputSide && dir != groupOutputSide) {
                                return CrawlResult.fail(
                                    String.format(
                                        "Invalid manifold connection: manifold at (%d, %d, %d) has connection in unauthorized direction %s! All inputs must be from %s and all outputs to %s.",
                                        mPos.x,
                                        mPos.y,
                                        mPos.z,
                                        dir,
                                        groupInputSide,
                                        groupOutputSide));
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
                        return CrawlResult.fail(
                            String.format(
                                "Manifold group at (%d, %d, %d) has no output connections in direction %s!",
                                step.x,
                                step.y,
                                step.z,
                                groupOutputSide));
                    }
                    if (mg.inputPorts.isEmpty()) {
                        return CrawlResult.fail(
                            String.format(
                                "Manifold group at (%d, %d, %d) has no input connections in direction %s!",
                                step.x,
                                step.y,
                                step.z,
                                groupInputSide));
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
                        segments.add(
                            new LoopSegment(
                                tile.getDeviceId(),
                                1.0,
                                0.10,
                                0.000045,
                                tile.getMinorLossK(),
                                500.0,
                                2000.0,
                                tile.getDeviceTemperatureCelsius()));
                    }
                    allManifoldGroups.add(mg);

                    addGraphNode(graphNodes, mg.getGroupId(), NodeType.MANIFOLD_GROUP, mg);
                    addGraphEdge(graphEdges, reverseGraphEdges, step.fromNodeId, mg.getGroupId());

                    // Enqueue each output port as an independent downstream branch
                    for (BlockPosCoord outPort : mg.outputPorts) {
                        frontier.add(
                            new FrontierStep(
                                mg.getGroupId(),
                                outPort.x,
                                outPort.y,
                                outPort.z,
                                groupOutputSide.getOpposite(),
                                null, // Manifolds permit pipe diameter/amount transitions
                                null));
                    }
                } else {
                    // Manifold group already discovered by another incoming branch
                    if (step.entrySide != mg.inputSide) {
                        return CrawlResult.fail(
                            String.format(
                                "Invalid manifold connection: branch entered manifold group at (%d, %d, %d) from side %s, but group input side is %s!",
                                step.x,
                                step.y,
                                step.z,
                                step.entrySide,
                                mg.inputSide));
                    }
                    if (!mf.isConnected(step.entrySide)) {
                        return CrawlResult.fail(
                            String.format(
                                "Invalid manifold connection: manifold at (%d, %d, %d) has connection disabled on entry side %s!",
                                step.x,
                                step.y,
                                step.z,
                                step.entrySide));
                    }
                    addGraphEdge(graphEdges, reverseGraphEdges, step.fromNodeId, mg.getGroupId());
                }
                stepCount++;
                continue;
            }

            // 2. Insulated Coolant Pipe Handling
            if (te instanceof BaseMetaPipeEntity) {
                BaseMetaPipeEntity bmpe = (BaseMetaPipeEntity) te;
                if (!(bmpe.getMetaTileEntity() instanceof MTECoolantPipe)) {
                    return CrawlResult.fail(
                        String.format(
                            "Loop broken: pipe at (%d, %d, %d) is not an insulated coolant pipe! Coolant loops require specialized Coolant Pipes.",
                            step.x,
                            step.y,
                            step.z));
                }
                MTECoolantPipe pipe = (MTECoolantPipe) bmpe.getMetaTileEntity();
                lastPipe = pipe;

                BlockPosCoord pCoord = new BlockPosCoord(step.x, step.y, step.z);
                if (visitedPipeAndDevicePositions.contains(pCoord)) {
                    return CrawlResult.fail(
                        String.format(
                            "Invalid pipe junction at (%d, %d, %d): flow merging requires a Manifold!",
                            step.x,
                            step.y,
                            step.z));
                }
                visitedPipeAndDevicePositions.add(pCoord);

                // Pipe material validation
                Materials pipeMat = pipe.mMaterial;
                if (!isAllowedCoolantPipeMaterial(pipeMat)) {
                    return CrawlResult.fail(
                        String.format(
                            "Pipe material %s at (%d, %d, %d) is not allowed for coolant loops: only materials with both GT fluid pipes and Railcraft tanks are permitted (Iron/Cast Iron, Steel, Stainless Steel, Titanium, TungstenSteel, Osmium, Neutronium).",
                            pipeMat != null ? pipeMat.mDefaultLocalName : "unknown",
                            step.x,
                            step.y,
                            step.z));
                }

                if (detectedLoopMaterial == null) {
                    detectedLoopMaterial = pipeMat;
                } else if (!areMaterialsEqual(pipeMat, detectedLoopMaterial)) {
                    if (expectedTankMaterial != null && areMaterialsEqual(detectedLoopMaterial, expectedTankMaterial)) {
                        return CrawlResult.fail(
                            String.format(
                                "Pipe material mismatch at (%d, %d, %d): Pipe is %s but Railcraft tank requires %s!",
                                step.x,
                                step.y,
                                step.z,
                                pipeMat != null ? pipeMat.mDefaultLocalName : "unknown",
                                expectedTankMaterial.mDefaultLocalName));
                    } else {
                        return CrawlResult.fail(
                            String.format(
                                "Pipe material mismatch at (%d, %d, %d): Pipe is %s but loop requires %s!",
                                step.x,
                                step.y,
                                step.z,
                                pipeMat != null ? pipeMat.mDefaultLocalName : "unknown",
                                detectedLoopMaterial.mDefaultLocalName));
                    }
                }

                // Pipe size consistency within current continuous segment
                Float currThickness = step.segmentThickness;
                Integer currAmount = step.segmentPipeAmount;
                if (currThickness == null) {
                    currThickness = pipe.mThickNess;
                    currAmount = pipe.mPipeAmount;
                } else {
                    if (Math.abs(pipe.mThickNess - currThickness) > 1e-4 || pipe.mPipeAmount != currAmount) {
                        return CrawlResult.fail(
                            String.format(
                                "Pipe size mismatch at (%d, %d, %d): Expected pipe thickness %.3f (amount %d) but found %.3f (amount %d) in segment between devices. Pipe size transitions require a device or manifold!",
                                step.x,
                                step.y,
                                step.z,
                                currThickness,
                                currAmount,
                                pipe.mThickNess,
                                pipe.mPipeAmount));
                    }
                }

                if (dischargePipeCapacityLPerSec < 0.0 && step.fromNodeId.startsWith("pump_disc_")) {
                    dischargePipeCapacityLPerSec = pipe.getCapacity();
                }

                // Invariant: empty pipes (zero Forge fluids)
                if (pipe.mFluids != null) {
                    for (FluidStack fs : pipe.mFluids) {
                        if (fs != null && fs.amount > 0) {
                            return CrawlResult.fail(
                                String.format(
                                    "Catastrophic loop disconnect: Pipe at (%d, %d, %d) contains Forge fluid (%s, %d L)! Coolant pipes must remain empty.",
                                    step.x,
                                    step.y,
                                    step.z,
                                    fs.getFluid()
                                        .getName(),
                                    fs.amount));
                        }
                    }
                }

                // Verify backward connection to incoming side
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
                    return CrawlResult.fail(
                        String.format(
                            "Pipe at (%d, %d, %d) is not connected backwards to incoming side %s",
                            step.x,
                            step.y,
                            step.z,
                            step.entrySide));
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
                segments.add(seg);

                String pipeNodeId = String.format("pipe_%d_%d_%d", step.x, step.y, step.z);
                addGraphNode(graphNodes, pipeNodeId, NodeType.PIPE, pCoord);
                addGraphEdge(graphEdges, reverseGraphEdges, step.fromNodeId, pipeNodeId);

                // Find forward connections
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
                    return CrawlResult.fail(
                        String.format(
                            "Dead-end or missing forward connection at pipe (%d, %d, %d) [incoming=%s]",
                            step.x,
                            step.y,
                            step.z,
                            step.entrySide));
                }
                if (forwardDirs.size() > 1) {
                    return CrawlResult.fail(
                        String.format(
                            "Ambiguous pipe branching at (%d, %d, %d): flow splitting requires a Manifold!",
                            step.x,
                            step.y,
                            step.z));
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
                stepCount++;
                continue;
            }

            // 3. GregTech Multiblock Hatches (PHE, Degasser, Core Passage)
            if (te instanceof IGregTechTileEntity) {
                IMetaTileEntity mte = ((IGregTechTileEntity) te).getMetaTileEntity();
                if (mte instanceof MTEHatchPressurizedFluid) {
                    MTEHatchPressurizedFluid hatch = (MTEHatchPressurizedFluid) mte;
                    MTEPressurizedHeatExchanger phe = findAdjacentPHE(world, step.x, step.y, step.z);
                    MTEPressurizedDegasser degasser = (phe == null)
                        ? findAdjacentDegasser(world, step.x, step.y, step.z)
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
                            stepCount++;
                            continue;
                        } else {
                            return CrawlResult.fail(
                                "PHE at (" + step.x
                                    + ", "
                                    + step.y
                                    + ", "
                                    + step.z
                                    + ") is unformed or missing a valid outlet hatch");
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
                            stepCount++;
                            continue;
                        } else {
                            return CrawlResult.fail(
                                "Pressurized Degasser at (" + step.x
                                    + ", "
                                    + step.y
                                    + ", "
                                    + step.z
                                    + ") is unformed or missing a valid outlet hatch");
                        }
                    }
                } else if (mte instanceof ICoolantPassageHatch) {
                    ICoolantPassageHatch passage = (ICoolantPassageHatch) mte;
                    if (!passage.isPassageInlet()) {
                        return CrawlResult.fail(
                            String
                                .format("Loop enters passage through outlet at (%d, %d, %d)", step.x, step.y, step.z));
                    }
                    IGregTechTileEntity outTE = passage.getOppositeHatchTile();
                    if (outTE == null) {
                        return CrawlResult.fail(
                            String.format(
                                "Passage hatch at (%d, %d, %d) has no paired opposite hatch",
                                step.x,
                                step.y,
                                step.z));
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
                    stepCount++;
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
                    return CrawlResult.fail(
                        String.format(
                            "Invalid device junction at (%d, %d, %d): flow merging requires a Manifold!",
                            step.x,
                            step.y,
                            step.z));
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
                stepCount++;
                continue;
            }

            return CrawlResult.fail(
                String.format(
                    "Loop broken: unknown non-coolant block at (%d, %d, %d): %s",
                    step.x,
                    step.y,
                    step.z,
                    te.getClass()
                        .getSimpleName()));
        }

        // --- DAG Graph Verification: Single Source, Single Sink, and Acyclicity ---

        // 1. Verify that the Sink was reached
        if (!graphNodes.containsKey(sinkNodeId)) {
            return CrawlResult.fail("Loop broken: no path reached the pump suction port!");
        }

        // 2. Reverse reachability from Sink (Every node in the graph must have a path to the suction port)
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
                return CrawlResult
                    .fail("Invalid loop topology: branch at " + nodeId + " does not lead to pump suction port!");
            }
        }

        // 3. Cycle Detection: DFS 3-color traversal starting from sourceNodeId
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
                return CrawlResult.fail(
                    "Invalid cyclic loop topology: path from manifold output loops back to its input without passing through the pump: "
                        + String.join(" -> ", cycle));
            } else {
                return CrawlResult.fail("Invalid cyclic loop topology: cycle detected: " + String.join(" -> ", cycle));
            }
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

        double maxPumpPipeCap = (dischargePipeCapacityLPerSec > 0 && suctionPipeCapacityLPerSec > 0)
            ? Math.min(dischargePipeCapacityLPerSec, suctionPipeCapacityLPerSec)
            : Math.max(dischargePipeCapacityLPerSec, suctionPipeCapacityLPerSec);
        if (maxPumpPipeCap <= 0) {
            maxPumpPipeCap = Double.MAX_VALUE;
        }

        return CrawlResult.success(
            segments,
            devices,
            visitedPipeAndDevicePositions,
            maxPumpPipeCap,
            detectedLoopMaterial,
            pipeConfigs);
    }

    private MTEPressurizedHeatExchanger findAdjacentPHE(World world, int x, int y, int z) {
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

    private MTEPressurizedDegasser findAdjacentDegasser(World world, int x, int y, int z) {
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

    private boolean isValidLoopBlock(World world, int x, int y, int z, int targetX, int targetY, int targetZ) {
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

    private ForgeDirection findNextPipeDirection(World world, BaseMetaPipeEntity bmpe, MTEFluidPipe pipe,
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

    private ForgeDirection findNextDirection(World world, ForgeDirection incomingSide, int x, int y, int z, int targetX,
        int targetY, int targetZ, Set<BlockPosCoord> visited) {
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
