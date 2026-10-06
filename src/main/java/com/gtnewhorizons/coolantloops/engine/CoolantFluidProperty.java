package com.gtnewhorizons.coolantloops.engine;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Thermodynamic and transport properties of coolant fluids used in closed loops.
 * All units are standard SI (kg, m, s, J, K, Pa).
 */
public class CoolantFluidProperty {

    private final String fluidName;
    private final double density; // kg/m^3
    private final double dynamicViscosity; // Pa*s
    private final double specificHeat; // J/(kg*K)
    private final double thermalConductivity; // W/(m*K)
    private final double boilingPointCelsius; // deg C at 1 atm
    private final double freezingPointCelsius; // deg C at 1 atm

    private static final Map<String, CoolantFluidProperty> REGISTRY = new HashMap<>();

    public CoolantFluidProperty(String fluidName, double density, double dynamicViscosity, double specificHeat,
        double thermalConductivity, double boilingPointCelsius, double freezingPointCelsius) {
        this.fluidName = fluidName;
        this.density = density;
        this.dynamicViscosity = dynamicViscosity;
        this.specificHeat = specificHeat;
        this.thermalConductivity = thermalConductivity;
        this.boilingPointCelsius = boilingPointCelsius;
        this.freezingPointCelsius = freezingPointCelsius;
    }

    public static void register(CoolantFluidProperty property) {
        REGISTRY.put(
            property.getFluidName()
                .toLowerCase(),
            property);
    }

    public static CoolantFluidProperty get(String name) {
        if (name == null) {
            return WATER;
        }
        return REGISTRY.getOrDefault(name.toLowerCase(), WATER);
    }

    public static Map<String, CoolantFluidProperty> getRegistry() {
        return Collections.unmodifiableMap(REGISTRY);
    }

    // Default registered fluids
    public static final CoolantFluidProperty WATER = new CoolantFluidProperty(
        "water",
        1000.0, // 1000 kg/m^3
        0.001, // 1.0 mPa*s
        4184.0, // 4.184 kJ/kg*K
        0.606, // 0.606 W/m*K
        100.0,
        0.0);

    public static final CoolantFluidProperty DISTILLED_WATER = new CoolantFluidProperty(
        "ic2distilledwater",
        998.0,
        0.00095,
        4186.0,
        0.610,
        100.0,
        0.0);

    public static final CoolantFluidProperty HEAVY_WATER = new CoolantFluidProperty(
        "heavywater",
        1105.0, // 1.105 g/cm^3
        0.00125,
        4210.0,
        0.595,
        101.4,
        3.8);

    public static final CoolantFluidProperty IC2_COOLANT = new CoolantFluidProperty(
        "ic2coolant",
        1080.0,
        0.0025,
        3800.0,
        0.520,
        140.0,
        -25.0);

    public static final CoolantFluidProperty SODIUM = new CoolantFluidProperty(
        "sodium",
        927.0,
        0.0007,
        1260.0,
        86.0, // High liquid metal conductivity
        883.0,
        97.8);

    static {
        register(WATER);
        register(DISTILLED_WATER);
        register(HEAVY_WATER);
        register(IC2_COOLANT);
        register(SODIUM);
    }

    public String getFluidName() {
        return fluidName;
    }

    public double getDensity() {
        return density;
    }

    public double getDynamicViscosity() {
        return dynamicViscosity;
    }

    public double getSpecificHeat() {
        return specificHeat;
    }

    public double getThermalConductivity() {
        return thermalConductivity;
    }

    public double getBoilingPointCelsius() {
        return boilingPointCelsius;
    }

    public double getFreezingPointCelsius() {
        return freezingPointCelsius;
    }

    /**
     * Volumetric heat capacity in J/(m^3 * K).
     */
    public double getVolumetricHeatCapacity() {
        return density * specificHeat;
    }

    /**
     * Kinematic viscosity in m^2/s: nu = mu / rho.
     */
    public double getKinematicViscosity() {
        return dynamicViscosity / density;
    }
}
