package com.gtnewhorizons.coolantloops.common.tileentity;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizons.coolantloops.engine.CoolantFluidProperty;
import com.gtnewhorizons.coolantloops.engine.ICoolantLoopDevice;
import com.gtnewhorizons.coolantloops.engine.LoopSegment;

/**
 * Manifold TileEntity.
 * Works together with up to 8 adjacent inline manifold blocks facing the same direction.
 * Merges incoming flows preserving thermal energy, and distributes output flow
 * proportionally to connected pipe cross-sectional areas.
 */
public class TileEntityManifold extends TileEntity implements ICoolantLoopDevice {

    private ForgeDirection facing = ForgeDirection.NORTH;
    private double currentTemperatureCelsius = 20.0;
    private double minorLossK = 0.3; // Manifold flow splitting/merging resistance
    private double totalInputFlowRate = 0.0;

    public TileEntityManifold() {}

    public ForgeDirection getFacing() {
        return facing;
    }

    public void setFacing(ForgeDirection facing) {
        if (facing != null && facing != ForgeDirection.UNKNOWN) {
            this.facing = facing;
            markDirty();
        }
    }

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

    /**
     * Resolves all contiguous adjacent manifold blocks in the linear group (max 8).
     */
    public List<TileEntityManifold> findLinearGroup() {
        List<TileEntityManifold> group = new ArrayList<>();
        if (worldObj == null) {
            group.add(this);
            return group;
        }

        // Determine axis perpendicular to facing (e.g. if facing NORTH/SOUTH, line along EAST/WEST)
        ForgeDirection searchAxis = (facing == ForgeDirection.NORTH || facing == ForgeDirection.SOUTH)
            ? ForgeDirection.EAST
            : ForgeDirection.NORTH;

        // Scan negative direction
        for (int i = 1; i <= 8; i++) {
            TileEntity te = worldObj.getTileEntity(
                xCoord - searchAxis.offsetX * i,
                yCoord - searchAxis.offsetY * i,
                zCoord - searchAxis.offsetZ * i);
            if (te instanceof TileEntityManifold) {
                TileEntityManifold other = (TileEntityManifold) te;
                if (other.getFacing() == this.facing) {
                    group.add(0, other);
                    continue;
                }
            }
            break;
        }

        group.add(this);

        // Scan positive direction
        for (int i = 1; i <= 8; i++) {
            if (group.size() >= 8) break;
            TileEntity te = worldObj.getTileEntity(
                xCoord + searchAxis.offsetX * i,
                yCoord + searchAxis.offsetY * i,
                zCoord + searchAxis.offsetZ * i);
            if (te instanceof TileEntityManifold) {
                TileEntityManifold other = (TileEntityManifold) te;
                if (other.getFacing() == this.facing) {
                    group.add(other);
                    continue;
                }
            }
            break;
        }

        return group;
    }

    @Override
    public void processThermalExchange(double coolantFlowRateM3s, double dt, CoolantFluidProperty fluid,
        LoopSegment segment) {
        if (segment != null) {
            this.currentTemperatureCelsius = segment.getCurrentTemperatureCelsius();
            this.totalInputFlowRate = coolantFlowRateM3s;
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        if (nbt.hasKey("facing")) {
            this.facing = ForgeDirection.getOrientation(nbt.getInteger("facing"));
        }
        if (nbt.hasKey("temp")) {
            this.currentTemperatureCelsius = nbt.getDouble("temp");
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setInteger("facing", facing.ordinal());
        nbt.setDouble("temp", currentTemperatureCelsius);
    }
}
