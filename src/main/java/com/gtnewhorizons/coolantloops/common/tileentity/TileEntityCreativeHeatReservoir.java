package com.gtnewhorizons.coolantloops.common.tileentity;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidHandler;

import com.gtnewhorizons.coolantloops.engine.ConvectiveHeatTransferModel;
import com.gtnewhorizons.coolantloops.engine.CoolantFluidProperty;
import com.gtnewhorizons.coolantloops.engine.ICoolantLoopDevice;
import com.gtnewhorizons.coolantloops.engine.LoopSegment;

/**
 * Creative Heat Reservoir tile entity.
 * Provides a constant-temperature thermal boundary condition in a coolant loop,
 * exchanging heat according to Newton's law of cooling.
 */
public class TileEntityCreativeHeatReservoir extends TileEntity implements ICoolantLoopDevice, IFluidHandler {

    private double targetTemperatureCelsius = 300.0; // Default 300 deg C
    private double heatTransferArea = 2.0; // 2.0 m^2 effective exchange area
    private double minorLossK = 0.5; // Flow resistance coefficient
    private ConvectiveHeatTransferModel convectiveModel = ConvectiveHeatTransferModel.DEFAULT;

    public TileEntityCreativeHeatReservoir() {}

    @Override
    public String getDeviceId() {
        return String.format("reservoir_%d_%d_%d", xCoord, yCoord, zCoord);
    }

    @Override
    public double getMinorLossK() {
        return minorLossK;
    }

    @Override
    public double getDeviceTemperatureCelsius() {
        return targetTemperatureCelsius;
    }

    public void setTargetTemperatureCelsius(double temp) {
        this.targetTemperatureCelsius = temp;
        markDirty();
    }

    public double getHeatTransferArea() {
        return heatTransferArea;
    }

    public void setHeatTransferArea(double area) {
        this.heatTransferArea = Math.max(0.1, area);
        markDirty();
    }

    @Override
    public void processThermalExchange(double coolantFlowRateM3s, double dt, CoolantFluidProperty fluid,
        LoopSegment segment) {
        if (segment == null || fluid == null) {
            return;
        }

        double velocity = coolantFlowRateM3s / segment.getArea();
        double currentCoolantTemp = segment.getCurrentTemperatureCelsius();

        // Newton's law of cooling: Q_dot = h(v) * A * (T_device - T_coolant)
        double qDotWatts = convectiveModel
            .computeHeatTransferRate(velocity, heatTransferArea, targetTemperatureCelsius, currentCoolantTemp);

        // Delta E = Q_dot * dt (Joules)
        double deltaEnergyJoules = qDotWatts * dt;

        // Delta T = Delta E / (m * C_p) = Delta E / (V * rho * C_p)
        double segmentMassKg = segment.getVolume() * fluid.getDensity();
        double heatCapacity = segmentMassKg * fluid.getSpecificHeat();

        if (heatCapacity > 0) {
            double deltaTemp = deltaEnergyJoules / heatCapacity;
            segment.setCurrentTemperatureCelsius(currentCoolantTemp + deltaTemp);
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        if (nbt.hasKey("targetTemp")) {
            this.targetTemperatureCelsius = nbt.getDouble("targetTemp");
        }
        if (nbt.hasKey("area")) {
            this.heatTransferArea = nbt.getDouble("area");
        }
        if (nbt.hasKey("minorLossK")) {
            this.minorLossK = nbt.getDouble("minorLossK");
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setDouble("targetTemp", targetTemperatureCelsius);
        nbt.setDouble("area", heatTransferArea);
        nbt.setDouble("minorLossK", minorLossK);
    }

    // --- IFluidHandler Implementation for GT Fluid Pipe Connectivity ---

    @Override
    public int fill(ForgeDirection from, FluidStack resource, boolean doFill) {
        return resource != null ? resource.amount : 0;
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
        return true;
    }

    @Override
    public boolean canDrain(ForgeDirection from, Fluid fluid) {
        return true;
    }

    @Override
    public FluidTankInfo[] getTankInfo(ForgeDirection from) {
        return new FluidTankInfo[] { new FluidTankInfo(null, 1000) };
    }
}
