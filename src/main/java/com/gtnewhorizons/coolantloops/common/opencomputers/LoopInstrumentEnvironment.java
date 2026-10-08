package com.gtnewhorizons.coolantloops.common.opencomputers;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizons.coolantloops.common.tileentity.TileEntityLoopInstrument;

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
        doc = "function():table -- Returns full telemetry data of the coolant loop and instrumentation computer.",
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
        return new Object[] { data };
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
