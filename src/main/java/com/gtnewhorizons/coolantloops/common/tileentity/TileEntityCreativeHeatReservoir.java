package com.gtnewhorizons.coolantloops.common.tileentity;

import java.util.function.DoubleConsumer;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidHandler;

import com.gtnewhorizons.coolantloops.common.config.CoolantLoopDataRegistry;
import com.gtnewhorizons.coolantloops.common.util.CoolantLocalization;
import com.gtnewhorizons.coolantloops.engine.ConvectiveHeatTransferModel;
import com.gtnewhorizons.coolantloops.engine.CoolantFluidProperty;
import com.gtnewhorizons.coolantloops.engine.ICoolantLoopDevice;
import com.gtnewhorizons.coolantloops.engine.LoopSegment;
import com.gtnewhorizons.modularui.api.drawable.Text;
import com.gtnewhorizons.modularui.api.math.Alignment;
import com.gtnewhorizons.modularui.api.math.Color;
import com.gtnewhorizons.modularui.api.screen.ITileWithModularUI;
import com.gtnewhorizons.modularui.api.screen.ModularWindow;
import com.gtnewhorizons.modularui.api.screen.UIBuildContext;
import com.gtnewhorizons.modularui.api.widget.Interactable;
import com.gtnewhorizons.modularui.api.widget.Widget;
import com.gtnewhorizons.modularui.common.widget.ButtonWidget;
import com.gtnewhorizons.modularui.common.widget.FakeSyncWidget;
import com.gtnewhorizons.modularui.common.widget.TextWidget;
import com.gtnewhorizons.modularui.common.widget.textfield.NumericWidget;

import gregtech.api.gui.modularui.GTUITextures;

/**
 * Creative Heat Reservoir tile entity.
 * Provides a constant-temperature thermal boundary condition in a coolant loop,
 * exchanging heat according to Newton's law of cooling.
 */
public class TileEntityCreativeHeatReservoir extends TileEntity
    implements ICoolantLoopDevice, ITileWithModularUI, IFluidHandler {

    public static final double MIN_TEMPERATURE_CELSIUS = -273.15;
    public static final double MIN_HEAT_TRANSFER_AREA = 0.1;

    private double targetTemperatureCelsius = 300.0; // Default 300 deg C
    private double heatTransferArea = 2.0; // 2.0 m^2 effective exchange area
    private double minorLossK = 0.5; // Flow resistance coefficient
    private ConvectiveHeatTransferModel convectiveModel = ConvectiveHeatTransferModel.DEFAULT;

    public TileEntityCreativeHeatReservoir() {
        if (CoolantLoopDataRegistry.getDeviceData() != null) {
            this.heatTransferArea = CoolantLoopDataRegistry.getDeviceData()
                .getReservoir().exchangeArea;
        }
        if (CoolantLoopDataRegistry.getConvectiveModel() != null) {
            this.convectiveModel = CoolantLoopDataRegistry.getConvectiveModel();
        }
    }

    private String cachedDeviceId;

    @Override
    public boolean canUpdate() {
        return false;
    }

    @Override
    public String getDeviceId() {
        if (cachedDeviceId == null) {
            cachedDeviceId = "reservoir_" + xCoord + "_" + yCoord + "_" + zCoord;
        }
        return cachedDeviceId;
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
        this.targetTemperatureCelsius = Math.max(MIN_TEMPERATURE_CELSIUS, temp);
        markDirty();
    }

    public double getHeatTransferArea() {
        return heatTransferArea;
    }

    public void setHeatTransferArea(double area) {
        this.heatTransferArea = Math.max(MIN_HEAT_TRANSFER_AREA, area);
        markDirty();
    }

    public void adjustTargetTemperature(double delta) {
        double next = Math.round((this.targetTemperatureCelsius + delta) * 100.0) / 100.0;
        setTargetTemperatureCelsius(next);
    }

    public void adjustHeatTransferArea(double delta) {
        double next = Math.round((this.heatTransferArea + delta) * 100.0) / 100.0;
        setHeatTransferArea(next);
    }

    private static double getTemperatureStep(Widget.ClickData clickData) {
        if (clickData.ctrl) return 1000.0;
        if (clickData.shift) return 100.0;
        if (clickData.mouseButton == 1) return 1.0;
        return 10.0;
    }

    private static double getAreaStep(Widget.ClickData clickData) {
        if (clickData.ctrl) return 100.0;
        if (clickData.shift) return 10.0;
        if (clickData.mouseButton == 1) return 0.1;
        return 1.0;
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

    // --- ModularUI Window Implementation ---

    public static class ClearableNumericWidget extends NumericWidget {

        private final double clearDefaultValue;
        private DoubleConsumer customSetter;

        public ClearableNumericWidget(double clearDefaultValue) {
            super();
            this.clearDefaultValue = clearDefaultValue;
        }

        @Override
        public NumericWidget setSetter(DoubleConsumer setter) {
            this.customSetter = setter;
            return super.setSetter(setter);
        }

        @Override
        public Interactable.ClickResult onClick(int button, boolean doubleClick) {
            if (!isRightBelowMouse()) {
                return Interactable.ClickResult.IGNORE;
            }
            if (button == 1) {
                this.handler.getText()
                    .clear();
                forceFocus();
                return Interactable.ClickResult.SUCCESS;
            }
            return super.onClick(button, doubleClick);
        }

        @Override
        public void onRemoveFocus() {
            if (this.handler.getText()
                .isEmpty()
                || this.handler.getText()
                    .get(0)
                    .trim()
                    .isEmpty()) {
                setValue(this.clearDefaultValue);
                if (this.customSetter != null) {
                    this.customSetter.accept(getValue());
                }
                if (syncsToServer()) {
                    syncToServer(1, buf -> buf.writeDouble(getValue()));
                }
                return;
            }
            super.onRemoveFocus();
        }
    }

    @Override
    public ModularWindow createWindow(UIBuildContext buildContext) {
        ModularWindow.Builder builder = ModularWindow.builder(176, 115);
        builder.setBackground(GTUITextures.BACKGROUND_SINGLEBLOCK_DEFAULT);

        // Title
        builder.widget(
            new TextWidget(Text.localised("coolantloops.gui.creative_reservoir.title")).setPos(10, 8)
                .setSize(156, 12));

        // Row 1: Target Temperature
        builder.widget(
            new TextWidget(Text.localised("coolantloops.gui.creative_reservoir.temperature")).setPos(10, 22)
                .setSize(156, 10));

        // Row 1: Minus button
        builder.widget(
            new ButtonWidget()
                .setOnClick((clickData, widget) -> adjustTargetTemperature(-getTemperatureStep(clickData)))
                .setPlayClickSound(true)
                .setBackground(GTUITextures.BUTTON_STANDARD, GTUITextures.OVERLAY_BUTTON_MINUS_LARGE)
                .addTooltips(
                    CoolantLocalization.getTooltipLines("coolantloops.gui.creative_reservoir.btn.decrease_temp"))
                .setPos(10, 34)
                .setSize(18, 18));

        // Row 1: Numeric input
        NumericWidget tempInput = new ClearableNumericWidget(0.0);
        tempInput.setGetter(this::getDeviceTemperatureCelsius)
            .setSetter(this::setTargetTemperatureCelsius)
            .setValidator(val -> Math.max(MIN_TEMPERATURE_CELSIUS, val))
            .setMinValue(MIN_TEMPERATURE_CELSIUS)
            .setMaxValue(100000.0)
            .setDefaultValue(300.0)
            .setScrollValues(10.0, 1.0, 100.0)
            .setIntegerOnly(false)
            .setTextAlignment(Alignment.Center)
            .setTextColor(Color.WHITE.normal)
            .setBackground(GTUITextures.BACKGROUND_TEXT_FIELD.withOffset(-1, -1, 2, 2))
            .addTooltips(CoolantLocalization.getTooltipLines("coolantloops.gui.creative_reservoir.input.temp_tooltip"))
            .setPos(32, 34)
            .setSize(112, 18);
        builder.widget(tempInput);

        // Row 1: Plus button
        builder.widget(
            new ButtonWidget().setOnClick((clickData, widget) -> adjustTargetTemperature(getTemperatureStep(clickData)))
                .setPlayClickSound(true)
                .setBackground(GTUITextures.BUTTON_STANDARD, GTUITextures.OVERLAY_BUTTON_PLUS_LARGE)
                .addTooltips(
                    CoolantLocalization.getTooltipLines("coolantloops.gui.creative_reservoir.btn.increase_temp"))
                .setPos(148, 34)
                .setSize(18, 18));

        // Row 2: Heat-Transfer Area
        builder.widget(
            new TextWidget(Text.localised("coolantloops.gui.creative_reservoir.area")).setPos(10, 56)
                .setSize(156, 10));

        // Row 2: Minus button
        builder.widget(
            new ButtonWidget().setOnClick((clickData, widget) -> adjustHeatTransferArea(-getAreaStep(clickData)))
                .setPlayClickSound(true)
                .setBackground(GTUITextures.BUTTON_STANDARD, GTUITextures.OVERLAY_BUTTON_MINUS_LARGE)
                .addTooltips(
                    CoolantLocalization.getTooltipLines("coolantloops.gui.creative_reservoir.btn.decrease_area"))
                .setPos(10, 68)
                .setSize(18, 18));

        // Row 2: Numeric input
        NumericWidget areaInput = new ClearableNumericWidget(MIN_HEAT_TRANSFER_AREA);
        areaInput.setGetter(this::getHeatTransferArea)
            .setSetter(this::setHeatTransferArea)
            .setValidator(val -> Math.max(MIN_HEAT_TRANSFER_AREA, val))
            .setMinValue(MIN_HEAT_TRANSFER_AREA)
            .setMaxValue(10000.0)
            .setDefaultValue(2.0)
            .setScrollValues(1.0, 0.1, 10.0)
            .setIntegerOnly(false)
            .setTextAlignment(Alignment.Center)
            .setTextColor(Color.WHITE.normal)
            .setBackground(GTUITextures.BACKGROUND_TEXT_FIELD.withOffset(-1, -1, 2, 2))
            .addTooltips(CoolantLocalization.getTooltipLines("coolantloops.gui.creative_reservoir.input.area_tooltip"))
            .setPos(32, 68)
            .setSize(112, 18);
        builder.widget(areaInput);

        // Row 2: Plus button
        builder.widget(
            new ButtonWidget().setOnClick((clickData, widget) -> adjustHeatTransferArea(getAreaStep(clickData)))
                .setPlayClickSound(true)
                .setBackground(GTUITextures.BUTTON_STANDARD, GTUITextures.OVERLAY_BUTTON_PLUS_LARGE)
                .addTooltips(
                    CoolantLocalization.getTooltipLines("coolantloops.gui.creative_reservoir.btn.increase_area"))
                .setPos(148, 68)
                .setSize(18, 18));

        // Row 3: Status / Telemetry readout
        builder.widget(
            new TextWidget().setStringSupplier(
                () -> CoolantLocalization
                    .format("coolantloops.gui.creative_reservoir.status", targetTemperatureCelsius, heatTransferArea))
                .setDefaultColor(Color.rgb(160, 210, 255))
                .setPos(10, 94)
                .setSize(156, 12));

        // Syncers to ensure client tile entity mirrors server state for real-time string supplier
        builder.widget(
            new FakeSyncWidget.DoubleSyncer(
                () -> targetTemperatureCelsius,
                val -> this.targetTemperatureCelsius = val));
        builder.widget(new FakeSyncWidget.DoubleSyncer(() -> heatTransferArea, val -> this.heatTransferArea = val));

        return builder.build();
    }

    // --- NBT Serialization ---

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        if (nbt.hasKey("targetTemp")) {
            this.targetTemperatureCelsius = Math.max(MIN_TEMPERATURE_CELSIUS, nbt.getDouble("targetTemp"));
        }
        if (nbt.hasKey("area")) {
            this.heatTransferArea = Math.max(MIN_HEAT_TRANSFER_AREA, nbt.getDouble("area"));
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

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound nbt = new NBTTagCompound();
        writeToNBT(nbt);
        return new S35PacketUpdateTileEntity(this.xCoord, this.yCoord, this.zCoord, 1, nbt);
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
