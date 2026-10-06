package com.gtnewhorizons.coolantloops.common.tileentity;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

import com.gtnewhorizons.coolantloops.engine.CoolantFluidProperty;
import com.gtnewhorizons.coolantloops.engine.ICoolantLoopDevice;
import com.gtnewhorizons.coolantloops.engine.LoopSegment;

/**
 * In-Line Coolant Loop Instrument.
 * Monitors fluid state variables (flow rate, temperature, or dissolved gas)
 * and produces a proportional Redstone output (0 to 15).
 */
public class TileEntityLoopInstrument extends TileEntity implements ICoolantLoopDevice {

    public enum InstrumentType {
        FLOW_METER,
        THERMOMETER,
        DISSOLVED_GAS_SENSOR
    }

    private InstrumentType type = InstrumentType.THERMOMETER;
    private int redstoneOutput = 0;
    private double currentTemperatureCelsius = 20.0;
    private double currentFlowRateM3s = 0.0;
    private double currentGasFraction = 0.0;
    private double minorLossK = 0.05; // Very low instrument resistance

    public TileEntityLoopInstrument() {}

    public InstrumentType getType() {
        return type;
    }

    public void setType(InstrumentType type) {
        if (type != null) {
            this.type = type;
            markDirty();
        }
    }

    public int getRedstoneOutput() {
        return redstoneOutput;
    }

    public double getCurrentFlowRateLitersPerSecond() {
        return currentFlowRateM3s * 1000.0;
    }

    public double getCurrentGasFraction() {
        return currentGasFraction;
    }

    @Override
    public String getDeviceId() {
        return String.format(
            "instrument_%s_%d_%d_%d",
            type.name()
                .toLowerCase(),
            xCoord,
            yCoord,
            zCoord);
    }

    @Override
    public double getMinorLossK() {
        return minorLossK;
    }

    @Override
    public double getDeviceTemperatureCelsius() {
        return currentTemperatureCelsius;
    }

    @Override
    public void processThermalExchange(double coolantFlowRateM3s, double dt, CoolantFluidProperty fluid,
        LoopSegment segment) {
        this.currentFlowRateM3s = coolantFlowRateM3s;
        if (segment != null) {
            this.currentTemperatureCelsius = segment.getCurrentTemperatureCelsius();
        }

        int oldRedstone = this.redstoneOutput;
        int newRedstone = 0;

        switch (type) {
            case FLOW_METER:
                // Velocity 0 to 10 m/s maps to 0..15 Redstone
                double velocity = (segment != null && segment.getArea() > 0) ? (coolantFlowRateM3s / segment.getArea())
                    : 0.0;
                newRedstone = (int) Math.min(15.0, Math.max(0.0, (velocity / 10.0) * 15.0));
                break;
            case THERMOMETER:
                // Temperature 0 C to 1000 C maps to 0..15 Redstone
                newRedstone = (int) Math.min(15.0, Math.max(0.0, (currentTemperatureCelsius / 1000.0) * 15.0));
                break;
            case DISSOLVED_GAS_SENSOR:
                // Gas fraction 0.0 to 1.0 maps to 0..15 Redstone
                newRedstone = (int) Math.min(15.0, Math.max(0.0, currentGasFraction * 15.0));
                break;
        }

        this.redstoneOutput = newRedstone;
        if (oldRedstone != newRedstone && worldObj != null) {
            worldObj.notifyBlocksOfNeighborChange(xCoord, yCoord, zCoord, getBlockType());
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        if (nbt.hasKey("type")) {
            int t = nbt.getInteger("type");
            if (t >= 0 && t < InstrumentType.values().length) {
                this.type = InstrumentType.values()[t];
            }
        }
        if (nbt.hasKey("redstone")) {
            this.redstoneOutput = nbt.getInteger("redstone");
        }
        if (nbt.hasKey("temp")) {
            this.currentTemperatureCelsius = nbt.getDouble("temp");
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setInteger("type", type.ordinal());
        nbt.setInteger("redstone", redstoneOutput);
        nbt.setDouble("temp", currentTemperatureCelsius);
    }
}
