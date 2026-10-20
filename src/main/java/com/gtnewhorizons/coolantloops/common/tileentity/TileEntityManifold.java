package com.gtnewhorizons.coolantloops.common.tileentity;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidHandler;

import com.gtnewhorizons.coolantloops.engine.CoolantFluidProperty;
import com.gtnewhorizons.coolantloops.engine.ICoolantLoopDevice;
import com.gtnewhorizons.coolantloops.engine.LoopGraphCrawler;
import com.gtnewhorizons.coolantloops.engine.LoopSegment;

import gregtech.api.interfaces.metatileentity.IConnectable;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * Manifold TileEntity.
 * A 3D pipe-like junction object with an orientable "connect-to-other-manifold-blocks" plane
 * and two "connect-to-input-or-output" normal directions.
 * Adjacent manifold blocks must share the same plane orientation and explicit mutual connection
 * to form a single manifold group.
 */
public class TileEntityManifold extends TileEntity implements ICoolantLoopDevice, IFluidHandler, IConnectable {

    private ForgeDirection facing = ForgeDirection.NORTH; // Normal facing defining the plane
    private byte connections = (byte) 0x3F; // Bitmask for 6 directions (bits 0..5), default all connected
    private double currentTemperatureCelsius = 20.0;
    private double minorLossK = 0.3; // Manifold flow splitting/merging resistance
    private double totalInputFlowRate = 0.0;

    private LoopGraphCrawler.BlockPosCoord activePumpPos = null;
    private String activePumpId = null;
    private ForgeDirection inputSide = ForgeDirection.UNKNOWN;
    private ForgeDirection outputSide = ForgeDirection.UNKNOWN;

    public TileEntityManifold() {}

    // --- Plane Orientation & Geometry ---

    public ForgeDirection getFacing() {
        return facing;
    }

    public void setFacing(ForgeDirection facing) {
        if (facing != null && facing != ForgeDirection.UNKNOWN) {
            this.facing = facing;
            validateConnectionsWithNeighbors();
            markDirty();
            if (worldObj != null) {
                worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
            }
        }
    }

    /**
     * Resolves the normal axis for the given direction:
     * 0 = X axis (WEST / EAST)
     * 1 = Y axis (DOWN / UP)
     * 2 = Z axis (NORTH / SOUTH)
     */
    public static int getAxisForDirection(ForgeDirection dir) {
        if (dir == null) return 2;
        return switch (dir) {
            case WEST, EAST -> 0;
            case DOWN, UP -> 1;
            case NORTH, SOUTH -> 2;
            default -> 2;
        };
    }

    /**
     * Resolves the normal axis of this manifold's plane.
     */
    public int getPlaneAxis() {
        return getAxisForDirection(facing);
    }

    /**
     * Returns true if the direction lies within the manifold's header plane
     * (i.e. is perpendicular to the normal axis).
     */
    public boolean isInPlane(ForgeDirection dir) {
        if (dir == null || dir == ForgeDirection.UNKNOWN) return false;
        return getAxisForDirection(dir) != getPlaneAxis();
    }

    /**
     * Returns true if the direction is along the manifold's normal axis
     * (i.e. is one of the two input/output directions).
     */
    public boolean isNormalDirection(ForgeDirection dir) {
        if (dir == null || dir == ForgeDirection.UNKNOWN) return false;
        return getAxisForDirection(dir) == getPlaneAxis();
    }

    /**
     * Checks if another manifold block shares the exact same plane orientation.
     */
    public boolean hasSamePlaneOrientation(TileEntityManifold other) {
        if (other == null) return false;
        return this.getPlaneAxis() == other.getPlaneAxis();
    }

    public String getPlaneName() {
        return switch (getPlaneAxis()) {
            case 0 -> com.gtnewhorizons.coolantloops.common.util.CoolantLocalization
                .get("coolantloops.manifold.plane.yz");
            case 1 -> com.gtnewhorizons.coolantloops.common.util.CoolantLocalization
                .get("coolantloops.manifold.plane.xz");
            case 2 -> com.gtnewhorizons.coolantloops.common.util.CoolantLocalization
                .get("coolantloops.manifold.plane.xy");
            default -> "XY";
        };
    }

    public String getNormalAxisName() {
        return switch (getPlaneAxis()) {
            case 0 -> "X";
            case 1 -> "Y";
            case 2 -> "Z";
            default -> "Z";
        };
    }

    public ForgeDirection getNextFacing() {
        return switch (facing) {
            case NORTH -> ForgeDirection.EAST;
            case EAST -> ForgeDirection.UP;
            case UP -> ForgeDirection.SOUTH;
            case SOUTH -> ForgeDirection.WEST;
            case WEST -> ForgeDirection.DOWN;
            case DOWN -> ForgeDirection.NORTH;
            default -> ForgeDirection.NORTH;
        };
    }

    // --- Explicit Toggleable Connections (Like GregTech Pipes) ---

    public byte getConnectionsMask() {
        return connections;
    }

    public void setConnectionsMask(byte mask) {
        this.connections = mask;
        markDirty();
        if (worldObj != null) {
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
    }

    public boolean isConnected(ForgeDirection dir) {
        if (dir == null || dir == ForgeDirection.UNKNOWN) return false;
        return (connections & (1 << dir.ordinal())) != 0;
    }

    public void setConnected(ForgeDirection dir, boolean connected) {
        if (dir == null || dir == ForgeDirection.UNKNOWN) return;
        if (connected) {
            connections |= (1 << dir.ordinal());
        } else {
            connections &= ~(1 << dir.ordinal());
        }
        markDirty();
        if (worldObj != null) {
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
    }

    public boolean toggleConnection(ForgeDirection dir) {
        boolean newState = !isConnected(dir);
        setConnected(dir, newState);
        return newState;
    }

    /**
     * Checks whether connection to an adjacent block in the given direction is allowed.
     * Adjacent manifolds MUST share the exact same main-plane orientation.
     */
    public boolean isConnectionAllowed(ForgeDirection dir) {
        if (dir == null || dir == ForgeDirection.UNKNOWN) return false;
        if (worldObj != null) {
            TileEntity neighbor = worldObj
                .getTileEntity(xCoord + dir.offsetX, yCoord + dir.offsetY, zCoord + dir.offsetZ);
            if (neighbor instanceof TileEntityManifold neighborManifold) {
                if (!hasSamePlaneOrientation(neighborManifold)) {
                    return false;
                }
            }
        }
        return true;
    }

    public void setConnections(byte mask) {
        setConnectionsMask(mask);
    }

    /**
     * Inspects all 6 neighbors. If an adjacent manifold has an incompatible main-plane orientation,
     * automatically disconnects the mutual connection. If an adjacent manifold has a compatible
     * main-plane orientation and is in-plane, connects the mutual connection.
     */
    public boolean validateConnectionsWithNeighbors() {
        if (worldObj == null) return false;
        boolean changed = false;
        for (ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
            TileEntity neighbor = worldObj
                .getTileEntity(xCoord + dir.offsetX, yCoord + dir.offsetY, zCoord + dir.offsetZ);
            if (neighbor instanceof TileEntityManifold neighborManifold) {
                if (!hasSamePlaneOrientation(neighborManifold)) {
                    if (isConnected(dir)) {
                        setConnected(dir, false);
                        changed = true;
                    }
                    if (neighborManifold.isConnected(dir.getOpposite())) {
                        neighborManifold.setConnected(dir.getOpposite(), false);
                        changed = true;
                    }
                } else if (isInPlane(dir) && neighborManifold.isInPlane(dir.getOpposite())) {
                    if (!isConnected(dir)) {
                        setConnected(dir, true);
                        changed = true;
                    }
                    if (!neighborManifold.isConnected(dir.getOpposite())) {
                        neighborManifold.setConnected(dir.getOpposite(), true);
                        changed = true;
                    }
                }
            }
        }
        if (changed) {
            markDirty();
            if (!worldObj.isRemote) {
                worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
            }
        }
        return changed;
    }

    /**
     * Toggles connection on the given side and, if an adjacent manifold or connectable pipe is present,
     * updates the reciprocal connection on that neighbor as well.
     */
    public boolean toggleConnectionWithNeighbor(ForgeDirection dir) {
        if (dir == null || dir == ForgeDirection.UNKNOWN) return false;
        if (!isConnectionAllowed(dir)) {
            setConnected(dir, false);
            if (worldObj != null) {
                TileEntity neighbor = worldObj
                    .getTileEntity(xCoord + dir.offsetX, yCoord + dir.offsetY, zCoord + dir.offsetZ);
                if (neighbor instanceof TileEntityManifold neighborManifold) {
                    neighborManifold.setConnected(dir.getOpposite(), false);
                }
            }
            return false;
        }

        boolean newState = toggleConnection(dir);
        if (worldObj != null) {
            TileEntity neighbor = worldObj
                .getTileEntity(xCoord + dir.offsetX, yCoord + dir.offsetY, zCoord + dir.offsetZ);
            ForgeDirection opp = dir.getOpposite();
            if (neighbor instanceof IConnectable connectable) {
                if (newState) {
                    connectable.connect(opp);
                } else {
                    connectable.disconnect(opp);
                }
            } else if (neighbor instanceof IGregTechTileEntity gte
                && gte.getMetaTileEntity() instanceof IConnectable connectable) {
                    if (newState) {
                        connectable.connect(opp);
                    } else {
                        connectable.disconnect(opp);
                    }
                }
        }
        return newState;
    }

    // --- IConnectable Implementation ---

    @Override
    public int connect(ForgeDirection side) {
        if (!isConnectionAllowed(side)) {
            setConnected(side, false);
            return 0;
        }
        setConnected(side, true);
        return 1;
    }

    @Override
    public void disconnect(ForgeDirection side) {
        setConnected(side, false);
    }

    @Override
    public boolean isConnectedAtSide(ForgeDirection side) {
        return isConnected(side);
    }

    // --- 3D Bounding Boxes for Selection and Collisions ---

    public List<AxisAlignedBB> getComponentBoundingBoxes(int x, int y, int z) {
        List<AxisAlignedBB> list = new ArrayList<>();
        int axis = getPlaneAxis();

        float cMin = 0.25F;
        float cMax = 0.75F;
        float tMin = 0.3125F;
        float tMax = 0.6875F;

        // 1. Central Core Plate
        if (axis == 2) { // Z normal (XY plane)
            list.add(AxisAlignedBB.getBoundingBox(x + cMin, y + cMin, z + tMin, x + cMax, y + cMax, z + tMax));
        } else if (axis == 1) { // Y normal (XZ plane)
            list.add(AxisAlignedBB.getBoundingBox(x + cMin, y + tMin, z + cMin, x + cMax, y + tMax, z + cMax));
        } else { // X normal (YZ plane)
            list.add(AxisAlignedBB.getBoundingBox(x + tMin, y + cMin, z + cMin, x + tMax, y + cMax, z + cMax));
        }

        // 2. In-plane manifold flanges extending to adjacent block boundaries
        if (axis == 2) { // Z normal (in-plane: WEST, EAST, DOWN, UP)
            if (isConnected(ForgeDirection.WEST)) {
                list.add(AxisAlignedBB.getBoundingBox(x + 0.0F, y + cMin, z + tMin, x + cMin, y + cMax, z + tMax));
            }
            if (isConnected(ForgeDirection.EAST)) {
                list.add(AxisAlignedBB.getBoundingBox(x + cMax, y + cMin, z + tMin, x + 1.0F, y + cMax, z + tMax));
            }
            if (isConnected(ForgeDirection.DOWN)) {
                list.add(AxisAlignedBB.getBoundingBox(x + cMin, y + 0.0F, z + tMin, x + cMax, y + cMin, z + tMax));
            }
            if (isConnected(ForgeDirection.UP)) {
                list.add(AxisAlignedBB.getBoundingBox(x + cMin, y + cMax, z + tMin, x + cMax, y + 1.0F, z + tMax));
            }
        } else if (axis == 1) { // Y normal (in-plane: WEST, EAST, NORTH, SOUTH)
            if (isConnected(ForgeDirection.WEST)) {
                list.add(AxisAlignedBB.getBoundingBox(x + 0.0F, y + tMin, z + cMin, x + cMin, y + tMax, z + cMax));
            }
            if (isConnected(ForgeDirection.EAST)) {
                list.add(AxisAlignedBB.getBoundingBox(x + cMax, y + tMin, z + cMin, x + 1.0F, y + tMax, z + cMax));
            }
            if (isConnected(ForgeDirection.NORTH)) {
                list.add(AxisAlignedBB.getBoundingBox(x + cMin, y + tMin, z + 0.0F, x + cMax, y + tMax, z + cMin));
            }
            if (isConnected(ForgeDirection.SOUTH)) {
                list.add(AxisAlignedBB.getBoundingBox(x + cMin, y + tMin, z + cMax, x + cMax, y + tMax, z + 1.0F));
            }
        } else { // X normal (in-plane: DOWN, UP, NORTH, SOUTH)
            if (isConnected(ForgeDirection.DOWN)) {
                list.add(AxisAlignedBB.getBoundingBox(x + tMin, y + 0.0F, z + cMin, x + tMax, y + cMin, z + cMax));
            }
            if (isConnected(ForgeDirection.UP)) {
                list.add(AxisAlignedBB.getBoundingBox(x + tMin, y + cMax, z + cMin, x + tMax, y + 1.0F, z + cMax));
            }
            if (isConnected(ForgeDirection.NORTH)) {
                list.add(AxisAlignedBB.getBoundingBox(x + tMin, y + cMin, z + 0.0F, x + tMax, y + cMax, z + cMin));
            }
            if (isConnected(ForgeDirection.SOUTH)) {
                list.add(AxisAlignedBB.getBoundingBox(x + tMin, y + cMin, z + cMax, x + tMax, y + cMax, z + 1.0F));
            }
        }

        // 3. Normal pipe connection nozzles/collars extending along the normal axis
        if (axis == 2) {
            if (isConnected(ForgeDirection.NORTH)) {
                list.add(AxisAlignedBB.getBoundingBox(x + cMin, y + cMin, z + 0.0F, x + cMax, y + cMax, z + tMin));
            }
            if (isConnected(ForgeDirection.SOUTH)) {
                list.add(AxisAlignedBB.getBoundingBox(x + cMin, y + cMin, z + tMax, x + cMax, y + cMax, z + 1.0F));
            }
        } else if (axis == 1) {
            if (isConnected(ForgeDirection.DOWN)) {
                list.add(AxisAlignedBB.getBoundingBox(x + cMin, y + 0.0F, z + cMin, x + cMax, y + tMin, z + cMax));
            }
            if (isConnected(ForgeDirection.UP)) {
                list.add(AxisAlignedBB.getBoundingBox(x + cMin, y + tMax, z + cMin, x + cMax, y + 1.0F, z + cMax));
            }
        } else {
            if (isConnected(ForgeDirection.WEST)) {
                list.add(AxisAlignedBB.getBoundingBox(x + 0.0F, y + cMin, z + cMin, x + tMin, y + cMax, z + cMax));
            }
            if (isConnected(ForgeDirection.EAST)) {
                list.add(AxisAlignedBB.getBoundingBox(x + tMax, y + cMin, z + cMin, x + 1.0F, y + cMax, z + cMax));
            }
        }
        return list;
    }

    public AxisAlignedBB getUnionBoundingBox() {
        double minX = 0.25, minY = 0.25, minZ = 0.25;
        double maxX = 0.75, maxY = 0.75, maxZ = 0.75;
        int axis = getPlaneAxis();

        if (axis == 2) {
            minZ = 0.3125;
            maxZ = 0.6875;
        } else if (axis == 1) {
            minY = 0.3125;
            maxY = 0.6875;
        } else {
            minX = 0.3125;
            maxX = 0.6875;
        }

        if (isConnected(ForgeDirection.WEST)) minX = 0.0;
        if (isConnected(ForgeDirection.EAST)) maxX = 1.0;
        if (isConnected(ForgeDirection.DOWN)) minY = 0.0;
        if (isConnected(ForgeDirection.UP)) maxY = 1.0;
        if (isConnected(ForgeDirection.NORTH)) minZ = 0.0;
        if (isConnected(ForgeDirection.SOUTH)) maxZ = 1.0;

        return AxisAlignedBB.getBoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
    }

    // --- Manifold Group Discovery ---

    /**
     * Resolves all contiguous adjacent manifold blocks that share the same plane orientation
     * AND have explicit mutual connections enabled between them.
     */
    public List<TileEntityManifold> findContiguousGroup() {
        List<TileEntityManifold> group = new ArrayList<>();
        if (worldObj == null) {
            group.add(this);
            return group;
        }

        Set<TileEntityManifold> visited = new HashSet<>();
        Queue<TileEntityManifold> queue = new ArrayDeque<>();

        visited.add(this);
        queue.add(this);

        while (!queue.isEmpty()) {
            TileEntityManifold curr = queue.poll();
            group.add(curr);

            for (ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
                // Must be an in-plane direction!
                if (!curr.isInPlane(dir)) {
                    continue;
                }
                // Must have connection enabled on this block
                if (!curr.isConnected(dir)) {
                    continue;
                }

                TileEntity te = curr.worldObj
                    .getTileEntity(curr.xCoord + dir.offsetX, curr.yCoord + dir.offsetY, curr.zCoord + dir.offsetZ);
                if (te instanceof TileEntityManifold) {
                    TileEntityManifold adj = (TileEntityManifold) te;
                    // Neighbor must have identical plane orientation and mutual connection enabled
                    if (curr.hasSamePlaneOrientation(adj) && adj.isConnected(dir.getOpposite())) {
                        if (visited.add(adj)) {
                            queue.add(adj);
                        }
                    }
                }
            }
        }
        return group;
    }

    public List<TileEntityManifold> findLinearGroup() {
        return findContiguousGroup();
    }

    // --- ICoolantLoopDevice Implementation ---

    @Override
    public String getDeviceId() {
        return String.format("manifold_%d_%d_%d", xCoord, yCoord, zCoord);
    }

    @Override
    public double getMinorLossK() {
        return minorLossK;
    }

    @Override
    public double getDeviceTemperatureCelsius() {
        return currentTemperatureCelsius;
    }

    public double getTotalInputFlowRate() {
        return totalInputFlowRate;
    }

    public LoopGraphCrawler.BlockPosCoord getActivePumpPos() {
        return activePumpPos;
    }

    public void setActivePumpPos(LoopGraphCrawler.BlockPosCoord activePumpPos) {
        this.activePumpPos = activePumpPos;
        this.activePumpId = activePumpPos != null ? activePumpPos.toString() : null;
        markDirty();
    }

    public String getActivePumpId() {
        if (activePumpId != null) return activePumpId;
        if (activePumpPos != null) return activePumpPos.toString();
        return null;
    }

    public void setActivePumpId(String activePumpId) {
        this.activePumpId = activePumpId;
        this.activePumpPos = LoopGraphCrawler.BlockPosCoord.parse(activePumpId);
        markDirty();
    }

    public ForgeDirection getInputSide() {
        return inputSide;
    }

    public void setInputSide(ForgeDirection inputSide) {
        this.inputSide = inputSide;
        markDirty();
    }

    public ForgeDirection getOutputSide() {
        return outputSide;
    }

    public void setOutputSide(ForgeDirection outputSide) {
        this.outputSide = outputSide;
        markDirty();
    }

    @Override
    public void processThermalExchange(double coolantFlowRateM3s, double dt, CoolantFluidProperty fluid,
        LoopSegment segment) {
        if (segment != null) {
            this.currentTemperatureCelsius = segment.getCurrentTemperatureCelsius();
            this.totalInputFlowRate = coolantFlowRateM3s;
        }
    }

    // --- NBT and Network Synchronization ---

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        if (nbt.hasKey("facing")) {
            this.facing = ForgeDirection.getOrientation(nbt.getInteger("facing"));
        }
        if (nbt.hasKey("connections")) {
            this.connections = nbt.getByte("connections");
        } else {
            this.connections = (byte) 0x3F;
        }
        if (nbt.hasKey("temp")) {
            this.currentTemperatureCelsius = nbt.getDouble("temp");
        }
        if (nbt.hasKey("pumpX")) {
            this.activePumpPos = new LoopGraphCrawler.BlockPosCoord(
                nbt.getInteger("pumpX"),
                nbt.getInteger("pumpY"),
                nbt.getInteger("pumpZ"));
            this.activePumpId = activePumpPos.toString();
        } else if (nbt.hasKey("pumpId")) {
            this.activePumpId = nbt.getString("pumpId");
            this.activePumpPos = LoopGraphCrawler.BlockPosCoord.parse(this.activePumpId);
        } else {
            this.activePumpPos = null;
            this.activePumpId = null;
        }
        if (nbt.hasKey("inputSide")) {
            this.inputSide = ForgeDirection.getOrientation(nbt.getInteger("inputSide"));
        }
        if (nbt.hasKey("outputSide")) {
            this.outputSide = ForgeDirection.getOrientation(nbt.getInteger("outputSide"));
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setInteger("facing", facing.ordinal());
        nbt.setByte("connections", connections);
        nbt.setDouble("temp", currentTemperatureCelsius);
        if (activePumpPos != null) {
            nbt.setInteger("pumpX", activePumpPos.x);
            nbt.setInteger("pumpY", activePumpPos.y);
            nbt.setInteger("pumpZ", activePumpPos.z);
        }
        if (activePumpId != null) {
            nbt.setString("pumpId", activePumpId);
        }
        if (inputSide != null && inputSide != ForgeDirection.UNKNOWN) {
            nbt.setInteger("inputSide", inputSide.ordinal());
        }
        if (outputSide != null && outputSide != ForgeDirection.UNKNOWN) {
            nbt.setInteger("outputSide", outputSide.ordinal());
        }
    }

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound nbt = new NBTTagCompound();
        writeToNBT(nbt);
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 1, nbt);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity pkt) {
        if (pkt != null && pkt.func_148857_g() != null) {
            readFromNBT(pkt.func_148857_g());
            if (worldObj != null) {
                worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
            }
        }
    }

    // --- IFluidHandler Implementation for GT Fluid Pipe Connectivity ---

    @Override
    public int fill(ForgeDirection from, FluidStack resource, boolean doFill) {
        return (isConnected(from) && resource != null) ? resource.amount : 0;
    }

    @Override
    public FluidStack drain(ForgeDirection from, FluidStack resource, boolean doDrain) {
        return null;
    }

    @Override
    public FluidStack drain(ForgeDirection from, int maxDrain, boolean doDrain) {
        return null;
    }

    @Override
    public boolean canFill(ForgeDirection from, Fluid fluid) {
        return isConnected(from);
    }

    @Override
    public boolean canDrain(ForgeDirection from, Fluid fluid) {
        return isConnected(from);
    }

    @Override
    public FluidTankInfo[] getTankInfo(ForgeDirection from) {
        if (isConnected(from)) {
            return new FluidTankInfo[] { new FluidTankInfo(null, 1000) };
        }
        return new FluidTankInfo[0];
    }
}
