package com.gtnewhorizons.coolantloops.common.tileentity;

import java.util.Arrays;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidHandler;

import com.gtnewhorizons.coolantloops.engine.CoolantFluidProperty;
import com.gtnewhorizons.coolantloops.engine.ICoolantLoopDevice;
import com.gtnewhorizons.coolantloops.engine.LoopSegment;
import com.gtnewhorizons.modularui.api.drawable.IDrawable;
import com.gtnewhorizons.modularui.api.drawable.Text;
import com.gtnewhorizons.modularui.api.math.Alignment;
import com.gtnewhorizons.modularui.api.math.Color;
import com.gtnewhorizons.modularui.api.screen.ITileWithModularUI;
import com.gtnewhorizons.modularui.api.screen.ModularWindow;
import com.gtnewhorizons.modularui.api.screen.UIBuildContext;
import com.gtnewhorizons.modularui.common.widget.ButtonWidget;
import com.gtnewhorizons.modularui.common.widget.DrawableWidget;
import com.gtnewhorizons.modularui.common.widget.FakeSyncWidget;
import com.gtnewhorizons.modularui.common.widget.TextWidget;

import cpw.mods.fml.common.Optional;
import gregtech.api.gui.modularui.GTUITextures;
import mrtjp.projectred.api.IBundledTile;

/**
 * Pressurized Instrumentation Computer.
 * Monitors fluid state variables (flow rate, temperature, pressure, dissolved gas)
 * and produces a proportional Redstone output (0 to 15) and 8-bit ProjectRed bundled output (0 to 255)
 * strictly from its facing direction.
 */
@Optional.Interface(iface = "mrtjp.projectred.api.IBundledTile", modid = "ProjRed|Transmission")
public class TileEntityLoopInstrument extends TileEntity
    implements ICoolantLoopDevice, ITileWithModularUI, IBundledTile, IFluidHandler {

    public enum TrackedMetric {

        FLOW_RATE("Flow Rate", "L/s"),
        TEMPERATURE("Temperature", "°C"),
        PRESSURE("Pressure", "bar"),
        DISSOLVED_GAS("Dissolved Gas", "%");

        public final String displayName;
        public final String unit;

        TrackedMetric(String displayName, String unit) {
            this.displayName = displayName;
            this.unit = unit;
        }
    }

    public static final String[] DYE_NAMES = new String[] { "White", "Orange", "Magenta", "Light Blue", "Yellow",
        "Lime", "Pink", "Gray", "Light Gray", "Cyan", "Purple", "Blue", "Brown", "Green", "Red", "Black" };

    public static final int BUNDLED_MODE_BROADCAST = 0;
    public static final int BUNDLED_MODE_MULTI = 1;
    public static final int BUNDLED_MODE_COUNT = 18;

    private ForgeDirection facing = ForgeDirection.NORTH;
    private TrackedMetric trackedMetric = TrackedMetric.FLOW_RATE;
    private int redstoneOutput = 0;
    private int fineOutputStrength = 0;
    private int bundledMode = BUNDLED_MODE_BROADCAST;
    private final byte[] bundledSignal = new byte[16];

    private double currentTemperatureCelsius = 20.0;
    private double currentFlowRateM3s = 0.0;
    private double currentPressureBar = 1.0;
    private double currentGasFraction = 0.0;
    private double currentVelocityMs = 0.0;
    private double minorLossK = 0.05;

    public TileEntityLoopInstrument() {}

    public ForgeDirection getFacing() {
        return facing;
    }

    public void setFacing(ForgeDirection facing) {
        if (facing != null && facing != ForgeDirection.UNKNOWN) {
            this.facing = facing;
            markDirty();
            if (worldObj != null) {
                worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
                worldObj.notifyBlocksOfNeighborChange(xCoord, yCoord, zCoord, getBlockType());
            }
        }
    }

    public TrackedMetric getTrackedMetric() {
        return trackedMetric;
    }

    public void setTrackedMetric(TrackedMetric metric) {
        if (metric != null) {
            this.trackedMetric = metric;
            updateOutputs();
            markDirty();
        }
    }

    public void cycleMetric(int dir) {
        TrackedMetric[] values = TrackedMetric.values();
        int next = (trackedMetric.ordinal() + dir) % values.length;
        if (next < 0) next += values.length;
        setTrackedMetric(values[next]);
    }

    public int getBundledMode() {
        return bundledMode;
    }

    public void setBundledMode(int mode) {
        if (mode < 0) {
            mode = (mode % BUNDLED_MODE_COUNT + BUNDLED_MODE_COUNT) % BUNDLED_MODE_COUNT;
        } else {
            mode = mode % BUNDLED_MODE_COUNT;
        }
        this.bundledMode = mode;
        updateOutputs();
        markDirty();
    }

    public void cycleBundledMode(int dir) {
        setBundledMode(bundledMode + dir);
    }

    public static String getBundledModeName(int mode) {
        if (mode == BUNDLED_MODE_BROADCAST) return "Broadcast (All Channels)";
        if (mode == BUNDLED_MODE_MULTI) return "Multi (Ch0:Flow, Ch1:Temp, Ch2:Pres, Ch3:Gas)";
        int dyeIdx = mode - 2;
        if (dyeIdx >= 0 && dyeIdx < DYE_NAMES.length) {
            return "Channel " + dyeIdx + " (" + DYE_NAMES[dyeIdx] + ")";
        }
        return "Unknown";
    }

    public int getRedstoneOutput() {
        return redstoneOutput;
    }

    public int getFineOutputStrength() {
        return fineOutputStrength;
    }

    public double getCurrentFlowRateLitersPerSecond() {
        return currentFlowRateM3s * 1000.0;
    }

    public double getCurrentPressureBar() {
        return currentPressureBar;
    }

    public void setCurrentPressureBar(double bar) {
        this.currentPressureBar = Math.max(0.0, bar);
        updateOutputs();
    }

    public double getCurrentGasFraction() {
        return currentGasFraction;
    }

    public double getCurrentVelocityMs() {
        return currentVelocityMs;
    }

    public double getCurrentValue() {
        return switch (trackedMetric) {
            case FLOW_RATE -> getCurrentFlowRateLitersPerSecond();
            case TEMPERATURE -> currentTemperatureCelsius;
            case PRESSURE -> currentPressureBar;
            case DISSOLVED_GAS -> currentGasFraction * 100.0;
        };
    }

    @Override
    public String getDeviceId() {
        return String.format(
            "instrument_%s_%d_%d_%d",
            trackedMetric.name()
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
            this.currentGasFraction = segment.getDissolvedGasFraction();
            this.currentVelocityMs = segment.getArea() > 0 ? (coolantFlowRateM3s / segment.getArea()) : 0.0;
            if (fluid != null) {
                double dropPa = segment.computePressureDrop(coolantFlowRateM3s, fluid);
                this.currentPressureBar = Math.max(1.0, 1.0 + (dropPa / 1e5));
            }
        }
        updateOutputs();
    }

    public void updateOutputs() {
        int oldRedstone = this.redstoneOutput;
        int coarse = 0;
        int fine = 0;

        switch (trackedMetric) {
            case FLOW_RATE:
                // Velocity 0 to 10 m/s, or flow rate 0 to 1000 L/s
                double normFlow = (currentVelocityMs > 0) ? Math.max(0.0, Math.min(1.0, currentVelocityMs / 10.0))
                    : Math.max(0.0, Math.min(1.0, getCurrentFlowRateLitersPerSecond() / 1000.0));
                coarse = normFlow > 0.0 ? Math.max(1, (int) Math.min(15.0, Math.max(0.0, normFlow * 15.0))) : 0;
                fine = (int) Math.min(255.0, Math.max(0.0, normFlow * 255.0));
                break;
            case TEMPERATURE:
                // Temperature 0 to 1000 C
                double normTemp = Math.max(0.0, Math.min(1.0, currentTemperatureCelsius / 1000.0));
                coarse = (int) Math.min(15.0, Math.max(0.0, normTemp * 15.0));
                fine = (int) Math.min(255.0, Math.max(0.0, normTemp * 255.0));
                break;
            case PRESSURE:
                // Pressure 0 to 100 bar
                double normPres = Math.max(0.0, Math.min(1.0, currentPressureBar / 100.0));
                coarse = (int) Math.min(15.0, Math.max(0.0, normPres * 15.0));
                fine = (int) Math.min(255.0, Math.max(0.0, normPres * 255.0));
                break;
            case DISSOLVED_GAS:
                // Gas fraction 0.0 to 1.0 (0% to 100%)
                double normGas = Math.max(0.0, Math.min(1.0, currentGasFraction));
                coarse = (int) Math.min(15.0, Math.max(0.0, normGas * 15.0));
                fine = (int) Math.min(255.0, Math.max(0.0, normGas * 255.0));
                break;
        }

        this.redstoneOutput = coarse;
        this.fineOutputStrength = fine;

        updateBundledSignal();

        if (oldRedstone != coarse && worldObj != null) {
            worldObj.notifyBlocksOfNeighborChange(xCoord, yCoord, zCoord, getBlockType());
        }
    }

    private void updateBundledSignal() {
        byte fineByte = (byte) (fineOutputStrength & 0xFF);
        Arrays.fill(bundledSignal, (byte) 0);
        if (bundledMode == BUNDLED_MODE_BROADCAST) {
            Arrays.fill(bundledSignal, fineByte);
        } else if (bundledMode == BUNDLED_MODE_MULTI) {
            double normFlow = (currentVelocityMs > 0) ? Math.max(0.0, Math.min(1.0, currentVelocityMs / 10.0))
                : Math.max(0.0, Math.min(1.0, getCurrentFlowRateLitersPerSecond() / 1000.0));
            bundledSignal[0] = (byte) ((int) Math.round(normFlow * 255.0) & 0xFF);
            bundledSignal[1] = (byte) ((int) Math
                .round(Math.max(0.0, Math.min(1.0, currentTemperatureCelsius / 1000.0)) * 255.0) & 0xFF);
            bundledSignal[2] = (byte) ((int) Math
                .round(Math.max(0.0, Math.min(1.0, currentPressureBar / 100.0)) * 255.0) & 0xFF);
            bundledSignal[3] = (byte) ((int) Math.round(Math.max(0.0, Math.min(1.0, currentGasFraction)) * 255.0)
                & 0xFF);
        } else {
            int ch = bundledMode - 2;
            if (ch >= 0 && ch < 16) {
                bundledSignal[ch] = fineByte;
            }
        }
    }

    // --- ProjectRed IBundledTile Implementation ---

    @Override
    @Optional.Method(modid = "ProjRed|Transmission")
    public boolean canConnectBundled(int side) {
        return side == facing.ordinal();
    }

    @Override
    @Optional.Method(modid = "ProjRed|Transmission")
    public byte[] getBundledSignal(int side) {
        if (side == facing.ordinal()) {
            return bundledSignal;
        }
        return null;
    }

    public byte[] getBundledSignalDirect() {
        return bundledSignal;
    }

    // --- ModularUI ITileWithModularUI Implementation ---

    @Override
    public ModularWindow createWindow(UIBuildContext buildContext) {
        ModularWindow.Builder builder = ModularWindow.builder(176, 105);
        builder.setBackground(GTUITextures.BACKGROUND_SINGLEBLOCK_DEFAULT);

        // Terminal Screen background
        builder.widget(
            new DrawableWidget().setDrawable(GTUITextures.PICTURE_SCREEN_BLACK)
                .setPos(7, 10)
                .setSize(162, 85));

        // Title
        builder.widget(
            new TextWidget("Pressurized Instrumentation Computer").setDefaultColor(Color.rgb(0, 255, 128))
                .setPos(12, 14));

        // Row 1: Tracked Metric Selector
        builder.widget(
            new ButtonWidget().setOnClick((clickData, widget) -> cycleMetric(-1))
                .setBackground(() -> new IDrawable[] { GTUITextures.BUTTON_STANDARD })
                .addTooltip("Previous metric")
                .setPos(12, 26)
                .setSize(12, 12));
        builder.widget(
            new TextWidget(Text.localised("<")).setTextAlignment(Alignment.Center)
                .setPos(12, 28)
                .setSize(12, 10));
        builder.widget(
            new TextWidget().setStringSupplier(() -> "Metric: " + trackedMetric.displayName)
                .setDefaultColor(Color.rgb(100, 200, 255))
                .setPos(28, 28));
        builder.widget(
            new ButtonWidget().setOnClick((clickData, widget) -> cycleMetric(1))
                .setBackground(() -> new IDrawable[] { GTUITextures.BUTTON_STANDARD })
                .addTooltip("Next metric")
                .setPos(153, 26)
                .setSize(12, 12));
        builder.widget(
            new TextWidget(Text.localised(">")).setTextAlignment(Alignment.Center)
                .setPos(153, 28)
                .setSize(12, 10));

        // Row 2: Value Display
        builder.widget(
            new TextWidget()
                .setStringSupplier(() -> String.format("Measured: %.2f %s", getCurrentValue(), trackedMetric.unit))
                .setDefaultColor(Color.rgb(255, 220, 100))
                .setPos(12, 42));

        // Row 3: Output Signal
        builder.widget(
            new TextWidget()
                .setStringSupplier(
                    () -> String.format("Output: %d / 15  (Fine: %d / 255)", redstoneOutput, fineOutputStrength))
                .setDefaultColor(Color.rgb(255, 80, 80))
                .setPos(12, 56));

        // Row 4: ProjectRed Bundled Cable Mode
        builder.widget(
            new ButtonWidget().setOnClick((clickData, widget) -> cycleBundledMode(-1))
                .setBackground(() -> new IDrawable[] { GTUITextures.BUTTON_STANDARD })
                .addTooltip("Previous bundled mode")
                .setPos(12, 70)
                .setSize(12, 12));
        builder.widget(
            new TextWidget(Text.localised("<")).setTextAlignment(Alignment.Center)
                .setPos(12, 72)
                .setSize(12, 10));
        builder.widget(
            new TextWidget().setStringSupplier(() -> "PR: " + getBundledModeName(bundledMode))
                .setDefaultColor(Color.rgb(200, 160, 255))
                .setPos(28, 72));
        builder.widget(
            new ButtonWidget().setOnClick((clickData, widget) -> cycleBundledMode(1))
                .setBackground(() -> new IDrawable[] { GTUITextures.BUTTON_STANDARD })
                .addTooltip("Next bundled mode")
                .setPos(153, 70)
                .setSize(12, 12));
        builder.widget(
            new TextWidget(Text.localised(">")).setTextAlignment(Alignment.Center)
                .setPos(153, 72)
                .setSize(12, 10));

        // Syncers
        builder.widget(new FakeSyncWidget.IntegerSyncer(() -> trackedMetric.ordinal(), idx -> {
            TrackedMetric[] vals = TrackedMetric.values();
            if (idx >= 0 && idx < vals.length) this.trackedMetric = vals[idx];
        }));
        builder.widget(new FakeSyncWidget.IntegerSyncer(() -> redstoneOutput, val -> this.redstoneOutput = val));
        builder
            .widget(new FakeSyncWidget.IntegerSyncer(() -> fineOutputStrength, val -> this.fineOutputStrength = val));
        builder.widget(new FakeSyncWidget.IntegerSyncer(() -> bundledMode, val -> this.bundledMode = val));
        builder.widget(
            new FakeSyncWidget.IntegerSyncer(
                () -> facing.ordinal(),
                val -> this.facing = ForgeDirection.getOrientation(val)));

        return builder.build();
    }

    // --- NBT Serialization ---

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        if (nbt.hasKey("facing")) {
            int f = nbt.getInteger("facing");
            if (f >= 0 && f < 6) {
                this.facing = ForgeDirection.getOrientation(f);
            }
        }
        if (nbt.hasKey("metric")) {
            int m = nbt.getInteger("metric");
            if (m >= 0 && m < TrackedMetric.values().length) {
                this.trackedMetric = TrackedMetric.values()[m];
            }
        } else if (nbt.hasKey("type")) {
            int t = nbt.getInteger("type");
            this.trackedMetric = switch (t) {
                case 0 -> TrackedMetric.FLOW_RATE;
                case 1 -> TrackedMetric.TEMPERATURE;
                case 2 -> TrackedMetric.DISSOLVED_GAS;
                default -> TrackedMetric.FLOW_RATE;
            };
        }
        if (nbt.hasKey("redstone")) {
            this.redstoneOutput = nbt.getInteger("redstone");
        }
        if (nbt.hasKey("fineRedstone")) {
            this.fineOutputStrength = nbt.getInteger("fineRedstone");
        }
        if (nbt.hasKey("bundledMode")) {
            this.bundledMode = nbt.getInteger("bundledMode");
        }
        if (nbt.hasKey("temp")) {
            this.currentTemperatureCelsius = nbt.getDouble("temp");
        }
        if (nbt.hasKey("flow")) {
            this.currentFlowRateM3s = nbt.getDouble("flow");
        }
        if (nbt.hasKey("pressure")) {
            this.currentPressureBar = nbt.getDouble("pressure");
        }
        if (nbt.hasKey("gas")) {
            this.currentGasFraction = nbt.getDouble("gas");
        }
        updateOutputs();
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setInteger("facing", facing.ordinal());
        nbt.setInteger("metric", trackedMetric.ordinal());
        nbt.setInteger("redstone", redstoneOutput);
        nbt.setInteger("fineRedstone", fineOutputStrength);
        nbt.setInteger("bundledMode", bundledMode);
        nbt.setDouble("temp", currentTemperatureCelsius);
        nbt.setDouble("flow", currentFlowRateM3s);
        nbt.setDouble("pressure", currentPressureBar);
        nbt.setDouble("gas", currentGasFraction);
    }

    // --- Network Synchronization ---

    @Override
    public net.minecraft.network.Packet getDescriptionPacket() {
        NBTTagCompound nbt = new NBTTagCompound();
        writeToNBT(nbt);
        return new net.minecraft.network.play.server.S35PacketUpdateTileEntity(
            this.xCoord,
            this.yCoord,
            this.zCoord,
            1,
            nbt);
    }

    @Override
    public void onDataPacket(net.minecraft.network.NetworkManager net,
        net.minecraft.network.play.server.S35PacketUpdateTileEntity pkt) {
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
        if (from == facing) return 0;
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
        return from != facing;
    }

    @Override
    public boolean canDrain(ForgeDirection from, Fluid fluid) {
        return from != facing;
    }

    @Override
    public FluidTankInfo[] getTankInfo(ForgeDirection from) {
        if (from == facing) {
            return new FluidTankInfo[0];
        }
        return new FluidTankInfo[] { new FluidTankInfo(null, 1000) };
    }
}
