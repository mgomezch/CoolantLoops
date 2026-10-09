package com.gtnewhorizons.coolantloops.common.opencomputers;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizons.coolantloops.common.tileentity.TileEntityLoopInstrument;
import com.gtnewhorizons.coolantloops.common.util.RotorThermalHelper;
import com.gtnewhorizons.coolantloops.engine.CoolantFluidProperty;
import com.gtnewhorizons.coolantloops.engine.ICoolantLoopPump;

import li.cil.oc.api.Network;
import li.cil.oc.api.driver.NamedBlock;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.Visibility;
import li.cil.oc.api.prefab.ManagedEnvironment;

/**
 * OpenComputers ManagedEnvironment component for the Pressurized Instrumentation Computer.
 */
public class LoopInstrumentEnvironment extends ManagedEnvironment implements NamedBlock {

    private final TileEntityLoopInstrument instrument;

    public LoopInstrumentEnvironment(TileEntityLoopInstrument instrument) {
        this.instrument = instrument;
        try {
            var builder = Network.newNode(this, Visibility.Network);
            if (builder != null) {
                setNode(
                    builder.withComponent("pressurized_instrumentation_computer")
                        .create());
            }
        } catch (Throwable ignored) {}
    }

    @Override
    public String preferredName() {
        return "pressurized_instrumentation_computer";
    }

    @Override
    public int priority() {
        return 10;
    }

    @Callback(
        doc = "function():table -- Returns full telemetry data of the coolant loop, pump, impeller, and instrumentation computer.",
        direct = true)
    public Object[] getLoopTelemetry(Context context, Arguments args) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("flowRateLPerSec", instrument.getCurrentFlowRateLitersPerSecond());
        data.put("temperatureCelsius", instrument.getDeviceTemperatureCelsius());
        data.put("pressureBar", instrument.getCurrentPressureBar());
        data.put("dissolvedGasFraction", instrument.getCurrentGasFraction());
        data.put("velocityMs", instrument.getCurrentVelocityMs());
        data.put(
            "trackedMetric",
            instrument.getTrackedMetric()
                .name());
        data.put("trackedMetricDisplayName", instrument.getTrackedMetric().displayName);
        data.put("redstoneOutput", instrument.getRedstoneOutput());
        data.put("fineOutputStrength", instrument.getFineOutputStrength());
        data.put("bundledMode", instrument.getBundledMode());
        data.put("bundledModeName", TileEntityLoopInstrument.getBundledModeName(instrument.getBundledMode()));
        data.put(
            "facing",
            instrument.getFacing()
                .name());
        data.put("impellerWearPercent", instrument.getCurrentImpellerWearPercent());
        data.put("fillPercent", instrument.getCurrentFillPercent());
        data.put("reservoirLiters", instrument.getCurrentReservoirLiters());

        ICoolantLoopPump pump = instrument.getLoopPump();
        if (pump != null) {
            data.put("loopConnected", true);
            data.put("loopFormed", pump.isLoopFormed());
            data.put("loopState", pump.getLoopStateName());
            data.put("loopStatus", pump.getLoopStatus());
            data.put("dischargeHatchDisabled", pump.isDischargeHatchDisabled());
            data.put("tankMaterial", pump.getTankMaterial() != null ? pump.getTankMaterial().mDefaultLocalName : "");
            CoolantFluidProperty prop = pump.getCoolantFluidProperty();
            data.put("fluidName", prop != null ? prop.getFluidName() : "");
            data.put("currentFillLiters", pump.getCurrentFillLiters());
            data.put("requiredFillLiters", pump.getRequiredFillLiters());
            data.put("fillFraction", pump.getFillFraction());
            data.put("totalCoolantLiters", pump.getTotalCoolantLiters());
            data.put("reservoirCapacityLiters", pump.getReservoirCapacityLiters());
            data.put("maxFlowRateLPerSec", pump.getMaxFlowRateLitersPerSecond());
            data.put("peakPressureBar", pump.getPeakLoopPressureBar());
            data.put("peakTempCelsius", pump.getPeakLoopTempCelsius());
            data.put("powerConsumptionEU", pump.getPowerConsumptionEU());
            data.put("powerEU", pump.getPowerConsumptionEU());

            // Impeller info
            data.put("hasImpeller", pump.hasRotor());
            data.put("impellerEfficiency", pump.getRotorEfficiency());
            data.put("impellerDamage", pump.getRotorDamage());
            data.put("impellerMaxDamage", pump.getRotorMaxDamage());
            data.put("impellerDurabilityPercent", pump.getRotorDurabilityPercent());
            data.put("impellerDamageAccumulator", pump.getRotorDamageAccumulator());
            data.put(
                "impellerMaterial",
                pump.getRotorMaterial() != null ? RotorThermalHelper.getMaterialLocalizedName(pump.getRotorMaterial())
                    : "");
            data.put("impellerMeltingPointCelsius", pump.getRotorMeltingPointCelsius());
            data.put("impellerSofteningTempCelsius", pump.getRotorSofteningCelsius());
            data.put("homologousTemperature", pump.getHomologousTemperature());
            data.put("thermalWearMultiplier", pump.getThermalWearMultiplier());
            data.put("thermalSofteningActive", pump.getHomologousTemperature() >= 0.5);

            // Dissolved gases map
            data.put("dissolvedGases", new LinkedHashMap<>(pump.getDissolvedGases()));
        } else {
            data.put("loopConnected", false);
            data.put("loopFormed", false);
            data.put("loopState", "STOPPED");
            data.put("loopStatus", "No coolant loop pump connected");
            data.put("dischargeHatchDisabled", false);
            data.put("tankMaterial", "");
            data.put("fluidName", "");
            data.put("currentFillLiters", 0L);
            data.put("requiredFillLiters", 0L);
            data.put("fillFraction", 0.0);
            data.put("totalCoolantLiters", instrument.getCurrentReservoirLiters());
            data.put("reservoirCapacityLiters", 0L);
            data.put("maxFlowRateLPerSec", 0.0);
            data.put("peakPressureBar", instrument.getCurrentPressureBar());
            data.put("peakTempCelsius", instrument.getDeviceTemperatureCelsius());
            data.put("powerConsumptionEU", 0L);
            data.put("powerEU", 0L);
            data.put("hasImpeller", false);
            data.put("impellerEfficiency", 0.0);
            data.put("impellerDamage", 0);
            data.put("impellerMaxDamage", 0);
            data.put("impellerDurabilityPercent", 0.0);
            data.put("impellerDamageAccumulator", 0.0);
            data.put("impellerMaterial", "");
            data.put("impellerMeltingPointCelsius", 0.0);
            data.put("impellerSofteningTempCelsius", 0.0);
            data.put("homologousTemperature", 0.0);
            data.put("thermalWearMultiplier", 1.0);
            data.put("thermalSofteningActive", false);
            data.put("dissolvedGases", Collections.emptyMap());
        }

        return new Object[] { data };
    }

    @Callback(
        doc = "function():table -- Returns pump status, operating state, power, and material info.",
        direct = true)
    public Object[] getPumpStatus(Context context, Arguments args) {
        Map<String, Object> status = new LinkedHashMap<>();
        ICoolantLoopPump pump = instrument.getLoopPump();
        if (pump != null) {
            status.put("connected", true);
            status.put("formed", pump.isLoopFormed());
            status.put("state", pump.getLoopStateName());
            status.put("status", pump.getLoopStatus());
            status.put("powerEU", pump.getPowerConsumptionEU());
            status.put("dischargeDisabled", pump.isDischargeHatchDisabled());
            status.put("tankMaterial", pump.getTankMaterial() != null ? pump.getTankMaterial().mDefaultLocalName : "");
            CoolantFluidProperty prop = pump.getCoolantFluidProperty();
            status.put("fluidName", prop != null ? prop.getFluidName() : "");
            status.put("peakPressureBar", pump.getPeakLoopPressureBar());
            status.put("peakTempCelsius", pump.getPeakLoopTempCelsius());
            status.put("maxFlowRateLPerSec", pump.getMaxFlowRateLitersPerSecond());
        } else {
            status.put("connected", false);
            status.put("formed", false);
            status.put("state", "STOPPED");
            status.put("status", "No coolant loop pump connected");
            status.put("powerEU", 0L);
            status.put("dischargeDisabled", false);
            status.put("tankMaterial", "");
            status.put("fluidName", "");
            status.put("peakPressureBar", instrument.getCurrentPressureBar());
            status.put("peakTempCelsius", instrument.getDeviceTemperatureCelsius());
            status.put("maxFlowRateLPerSec", 0.0);
        }
        return new Object[] { status };
    }

    @Callback(
        doc = "function():table -- Returns impeller / rotor presence, wear, durability, and efficiency.",
        direct = true)
    public Object[] getImpellerStatus(Context context, Arguments args) {
        Map<String, Object> status = new LinkedHashMap<>();
        ICoolantLoopPump pump = instrument.getLoopPump();
        if (pump != null) {
            status.put("hasImpeller", pump.hasRotor());
            status.put("efficiency", pump.getRotorEfficiency());
            status.put("damage", pump.getRotorDamage());
            status.put("maxDamage", pump.getRotorMaxDamage());
            status.put("wearPercent", instrument.getCurrentImpellerWearPercent());
            status.put("durabilityPercent", pump.getRotorDurabilityPercent());
            status.put("damageAccumulator", pump.getRotorDamageAccumulator());
            status.put(
                "material",
                pump.getRotorMaterial() != null ? RotorThermalHelper.getMaterialLocalizedName(pump.getRotorMaterial())
                    : "");
            status.put("meltingPointCelsius", pump.getRotorMeltingPointCelsius());
            status.put("softeningTempCelsius", pump.getRotorSofteningCelsius());
            status.put("homologousTemperature", pump.getHomologousTemperature());
            status.put("thermalWearMultiplier", pump.getThermalWearMultiplier());
            status.put("thermalSofteningActive", pump.getHomologousTemperature() >= 0.5);
        } else {
            status.put("hasImpeller", false);
            status.put("efficiency", 0.0);
            status.put("damage", 0);
            status.put("maxDamage", 0);
            status.put("wearPercent", instrument.getCurrentImpellerWearPercent());
            status.put("durabilityPercent", 0.0);
            status.put("damageAccumulator", 0.0);
            status.put("material", "");
            status.put("meltingPointCelsius", 0.0);
            status.put("softeningTempCelsius", 0.0);
            status.put("homologousTemperature", 0.0);
            status.put("thermalWearMultiplier", 1.0);
            status.put("thermalSofteningActive", false);
        }
        return new Object[] { status };
    }

    @Callback(doc = "function():table -- Returns coolant fluid inventory in loop and reservoir.", direct = true)
    public Object[] getFluidInventory(Context context, Arguments args) {
        Map<String, Object> inv = new LinkedHashMap<>();
        ICoolantLoopPump pump = instrument.getLoopPump();
        if (pump != null) {
            CoolantFluidProperty prop = pump.getCoolantFluidProperty();
            inv.put("fluidName", prop != null ? prop.getFluidName() : "");
            inv.put("currentFillLiters", pump.getCurrentFillLiters());
            inv.put("requiredFillLiters", pump.getRequiredFillLiters());
            inv.put("fillFraction", pump.getFillFraction());
            inv.put("fillPercent", instrument.getCurrentFillPercent());
            inv.put("totalCoolantLiters", pump.getTotalCoolantLiters());
            inv.put("reservoirCapacityLiters", pump.getReservoirCapacityLiters());
        } else {
            inv.put("fluidName", "");
            inv.put("currentFillLiters", 0L);
            inv.put("requiredFillLiters", 0L);
            inv.put("fillFraction", 0.0);
            inv.put("fillPercent", instrument.getCurrentFillPercent());
            inv.put("totalCoolantLiters", instrument.getCurrentReservoirLiters());
            inv.put("reservoirCapacityLiters", 0L);
        }
        return new Object[] { inv };
    }

    @Callback(
        doc = "function():table, number -- Returns table of dissolved gas species (in liters) and total volume fraction.",
        direct = true)
    public Object[] getDissolvedGases(Context context, Arguments args) {
        ICoolantLoopPump pump = instrument.getLoopPump();
        Map<String, Long> gases = pump != null ? new LinkedHashMap<>(pump.getDissolvedGases()) : Collections.emptyMap();
        double fraction = instrument.getCurrentGasFraction();
        return new Object[] { gases, fraction };
    }

    @Callback(doc = "function():number -- Returns flow rate in L/s.", direct = true)
    public Object[] getFlowRate(Context context, Arguments args) {
        return new Object[] { instrument.getCurrentFlowRateLitersPerSecond() };
    }

    @Callback(doc = "function():number -- Returns measured temperature in Celsius.", direct = true)
    public Object[] getTemperature(Context context, Arguments args) {
        return new Object[] { instrument.getDeviceTemperatureCelsius() };
    }

    @Callback(doc = "function():number -- Returns measured pressure in bar.", direct = true)
    public Object[] getPressure(Context context, Arguments args) {
        return new Object[] { instrument.getCurrentPressureBar() };
    }

    @Callback(doc = "function():number -- Returns dissolved gas fraction (0.0 to 1.0).", direct = true)
    public Object[] getDissolvedGasFraction(Context context, Arguments args) {
        return new Object[] { instrument.getCurrentGasFraction() };
    }

    @Callback(doc = "function():number -- Returns impeller wear percentage (0.0 to 100.0%).", direct = true)
    public Object[] getImpellerWear(Context context, Arguments args) {
        return new Object[] { instrument.getCurrentImpellerWearPercent() };
    }

    @Callback(doc = "function():number -- Returns loop fill percentage (0.0 to 100.0%).", direct = true)
    public Object[] getFillLevel(Context context, Arguments args) {
        return new Object[] { instrument.getCurrentFillPercent() };
    }

    @Callback(doc = "function():number -- Returns remaining reservoir fluid volume in liters.", direct = true)
    public Object[] getReservoirLevel(Context context, Arguments args) {
        return new Object[] { instrument.getCurrentReservoirLiters() };
    }

    @Callback(doc = "function():string -- Returns currently tracked loop metric name.", direct = true)
    public Object[] getTrackedMetric(Context context, Arguments args) {
        return new Object[] { instrument.getTrackedMetric()
            .name() };
    }

    @Callback(doc = "function(metric:string|number):boolean -- Sets the tracked loop metric.", direct = true)
    public Object[] setTrackedMetric(Context context, Arguments args) {
        if (args.isString(0)) {
            String name = args.checkString(0);
            for (TileEntityLoopInstrument.TrackedMetric m : TileEntityLoopInstrument.TrackedMetric.values()) {
                if (m.name()
                    .equalsIgnoreCase(name) || m.displayName.equalsIgnoreCase(name)) {
                    instrument.setTrackedMetric(m);
                    return new Object[] { true };
                }
            }
            return new Object[] { false, "Unknown metric name: " + name };
        } else if (args.isInteger(0)) {
            int idx = args.checkInteger(0);
            TileEntityLoopInstrument.TrackedMetric[] values = TileEntityLoopInstrument.TrackedMetric.values();
            if (idx >= 0 && idx < values.length) {
                instrument.setTrackedMetric(values[idx]);
                return new Object[] { true };
            }
            return new Object[] { false, "Metric index out of bounds: " + idx };
        }
        return new Object[] { false, "Expected string or integer argument" };
    }

    @Callback(doc = "function():string -- Returns block facing direction.", direct = true)
    public Object[] getFacing(Context context, Arguments args) {
        return new Object[] { instrument.getFacing()
            .name() };
    }

    @Callback(doc = "function(facing:string|number):boolean -- Sets block facing direction.", direct = true)
    public Object[] setFacing(Context context, Arguments args) {
        if (args.isString(0)) {
            String dirStr = args.checkString(0);
            try {
                ForgeDirection dir = ForgeDirection.valueOf(dirStr.toUpperCase());
                if (dir != ForgeDirection.UNKNOWN) {
                    instrument.setFacing(dir);
                    return new Object[] { true };
                }
            } catch (IllegalArgumentException e) {
                return new Object[] { false, "Invalid direction: " + dirStr };
            }
        } else if (args.isInteger(0)) {
            int dirInt = args.checkInteger(0);
            if (dirInt >= 0 && dirInt < 6) {
                instrument.setFacing(ForgeDirection.getOrientation(dirInt));
                return new Object[] { true };
            }
            return new Object[] { false, "Direction ordinal out of bounds (0-5)" };
        }
        return new Object[] { false, "Expected string or integer argument" };
    }

    @Callback(doc = "function():number -- Returns vanilla redstone output level (0 to 15).", direct = true)
    public Object[] getRedstoneOutput(Context context, Arguments args) {
        return new Object[] { instrument.getRedstoneOutput() };
    }

    @Callback(doc = "function():number -- Returns high-precision 8-bit output level (0 to 255).", direct = true)
    public Object[] getFineOutputStrength(Context context, Arguments args) {
        return new Object[] { instrument.getFineOutputStrength() };
    }

    @Callback(
        doc = "function():number, string -- Returns ProjectRed bundled channel mode index and name.",
        direct = true)
    public Object[] getBundledMode(Context context, Arguments args) {
        return new Object[] { instrument.getBundledMode(),
            TileEntityLoopInstrument.getBundledModeName(instrument.getBundledMode()) };
    }

    @Callback(
        doc = "function(mode:number):boolean -- Sets ProjectRed bundled channel mode (0: Broadcast, 1: Multi, 2-17: Color 0-15).",
        direct = true)
    public Object[] setBundledMode(Context context, Arguments args) {
        int mode = args.checkInteger(0);
        if (mode >= 0 && mode < TileEntityLoopInstrument.BUNDLED_MODE_COUNT) {
            instrument.setBundledMode(mode);
            return new Object[] { true };
        }
        return new Object[] { false, "Bundled mode out of bounds (0 to 17)" };
    }
}
