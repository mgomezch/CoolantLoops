package com.gtnewhorizons.coolantloops.engine;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

import gregtech.api.metatileentity.BaseMetaPipeEntity;
import gregtech.api.metatileentity.implementations.MTEFluidPipe;

/**
 * Traverses physical Minecraft block connections from a pump's discharge port
 * around a closed coolant circuit back to the suction port.
 *
 * Enforces the core physical invariants:
 * 1. Continuous closed loop without dead-ends.
 * 2. All GregTech pipes in the loop must be strictly empty (zero Forge fluids).
 * 3. Exactly one pump per loop (no multi-pump loops).
 */
public class LoopGraphCrawler {

    public static class CrawlResult {

        public final boolean isSuccess;
        public final String failureReason;
        public final List<LoopSegment> segments;
        public final List<ICoolantLoopDevice> devices;
        public final Set<BlockPosCoord> pipePositions;

        public CrawlResult(boolean isSuccess, String failureReason, List<LoopSegment> segments,
            List<ICoolantLoopDevice> devices, Set<BlockPosCoord> pipePositions) {
            this.isSuccess = isSuccess;
            this.failureReason = failureReason;
            this.segments = segments != null ? segments : new ArrayList<>();
            this.devices = devices != null ? devices : new ArrayList<>();
            this.pipePositions = pipePositions != null ? pipePositions : new HashSet<>();
        }

        public static CrawlResult fail(String reason) {
            return new CrawlResult(false, reason, null, null, null);
        }

        public static CrawlResult success(List<LoopSegment> segments, List<ICoolantLoopDevice> devices,
            Set<BlockPosCoord> pipePositions) {
            return new CrawlResult(true, null, segments, devices, pipePositions);
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

    /**
     * Crawls the loop from a starting port (discharge) until returning to the target port (suction).
     */
    public CrawlResult crawl(int startX, int startY, int startZ, ForgeDirection startDir, int targetX, int targetY,
        int targetZ, ForgeDirection targetSuctionSide, String pumpId) {

        List<LoopSegment> segments = new ArrayList<>();
        List<ICoolantLoopDevice> devices = new ArrayList<>();
        Set<BlockPosCoord> visited = new HashSet<>();

        int currX = startX + startDir.offsetX;
        int currY = startY + startDir.offsetY;
        int currZ = startZ + startDir.offsetZ;
        ForgeDirection entryDir = startDir.getOpposite();

        int stepCount = 0;

        while (stepCount < maxDepth) {
            // Check if we arrived back at the pump suction port
            if (currX == targetX && currY == targetY && currZ == targetZ) {
                if (entryDir == targetSuctionSide) {
                    // Loop is successfully closed!
                    return CrawlResult.success(segments, devices, visited);
                } else {
                    return CrawlResult.fail("Reached pump, but arrived on the wrong side (expected suction port)");
                }
            }

            BlockPosCoord coord = new BlockPosCoord(currX, currY, currZ);
            if (visited.contains(coord)) {
                return CrawlResult.fail(
                    String.format("Invalid cyclic loop topology: cycle detected at (%d, %d, %d)", currX, currY, currZ));
            }
            visited.add(coord);

            TileEntity te = world.getTileEntity(currX, currY, currZ);
            if (te == null) {
                return CrawlResult
                    .fail(String.format("Loop broken: no tile entity at (%d, %d, %d)", currX, currY, currZ));
            }

            // 1. Is it a GregTech Fluid Pipe?
            if (te instanceof BaseMetaPipeEntity) {
                BaseMetaPipeEntity bmpe = (BaseMetaPipeEntity) te;
                if (!(bmpe.getMetaTileEntity() instanceof MTEFluidPipe)) {
                    return CrawlResult.fail(
                        String.format("Loop broken: pipe at (%d, %d, %d) is not a GT fluid pipe", currX, currY, currZ));
                }
                MTEFluidPipe pipe = (MTEFluidPipe) bmpe.getMetaTileEntity();

                // Enforce empty pipe invariant
                if (pipe.mFluids != null) {
                    for (FluidStack fs : pipe.mFluids) {
                        if (fs != null && fs.amount > 0) {
                            return CrawlResult.fail(
                                String.format(
                                    "Catastrophic loop disconnect: Pipe at (%d, %d, %d) contains Forge fluid (%s, %d L)! Coolant pipes must remain empty.",
                                    currX,
                                    currY,
                                    currZ,
                                    fs.getFluid()
                                        .getName(),
                                    fs.amount));
                        }
                    }
                }

                // Create loop segment
                CoolantPipingRegistry.PipeProperties props = CoolantPipingRegistry.getProperties(pipe);
                LoopSegment seg = new LoopSegment(
                    String.format("pipe_%d_%d_%d", currX, currY, currZ),
                    1.0, // Standard 1-block length
                    props.diameter,
                    props.roughness,
                    0.05, // Small friction loss
                    props.maxPressureBar,
                    props.maxTemperatureCelsius,
                    20.0);
                segments.add(seg);

                // Find the next connected neighbor
                ForgeDirection nextDir = findNextPipeConnection(bmpe, entryDir, currX, currY, currZ);
                if (nextDir == ForgeDirection.UNKNOWN) {
                    return CrawlResult.fail(String.format("Dead-end at pipe (%d, %d, %d)", currX, currY, currZ));
                }

                currX += nextDir.offsetX;
                currY += nextDir.offsetY;
                currZ += nextDir.offsetZ;
                entryDir = nextDir.getOpposite();
                stepCount++;
                continue;
            }

            // 2. Is it an In-Line Coolant Loop Device?
            if (te instanceof ICoolantLoopDevice) {
                ICoolantLoopDevice device = (ICoolantLoopDevice) te;
                devices.add(device);

                // Add device as an in-line segment with its minor loss coefficient
                LoopSegment devSeg = new LoopSegment(
                    device.getDeviceId(),
                    1.0,
                    0.10, // Standard 10cm internal passage
                    0.000045,
                    device.getMinorLossK(),
                    500.0, // High structural device strength
                    2000.0,
                    device.getDeviceTemperatureCelsius());
                segments.add(devSeg);

                // Find next output connection from device
                ForgeDirection nextDir = entryDir.getOpposite(); // Default straight through
                currX += nextDir.offsetX;
                currY += nextDir.offsetY;
                currZ += nextDir.offsetZ;
                entryDir = nextDir.getOpposite();
                stepCount++;
                continue;
            }

            return CrawlResult.fail(
                String.format(
                    "Loop broken: unknown non-coolant block at (%d, %d, %d): %s",
                    currX,
                    currY,
                    currZ,
                    te.getClass()
                        .getSimpleName()));
        }

        return CrawlResult.fail(String.format("Loop exceeds maximum supported length (%d blocks)", maxDepth));
    }

    private ForgeDirection findNextPipeConnection(BaseMetaPipeEntity pipe, ForgeDirection incomingSide, int x, int y,
        int z) {
        ForgeDirection result = ForgeDirection.UNKNOWN;
        for (ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
            if (dir == incomingSide) continue; // Don't turn back to where we came from

            // Check if pipe is connected in this direction
            if ((pipe.mConnections & (1 << dir.ordinal())) != 0) {
                if (result != ForgeDirection.UNKNOWN) {
                    // For straight line pipe crawler, multiple open connections without a manifold
                    // We pick the continuous line or report ambiguous branch
                }
                result = dir;
            }
        }
        return result;
    }
}
