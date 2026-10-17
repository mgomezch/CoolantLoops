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
    private final boolean isMolten;
    private final double radiolysisMultiplier;

    private static final Map<String, CoolantFluidProperty> REGISTRY = new HashMap<>();

    public CoolantFluidProperty(String fluidName, double density, double dynamicViscosity, double specificHeat,
        double thermalConductivity, double boilingPointCelsius, double freezingPointCelsius) {
        this(
            fluidName,
            density,
            dynamicViscosity,
            specificHeat,
            thermalConductivity,
            boilingPointCelsius,
            freezingPointCelsius,
            "lava".equalsIgnoreCase(fluidName) || "molten.cheese".equalsIgnoreCase(fluidName),
            1.0);
    }

    public CoolantFluidProperty(String fluidName, double density, double dynamicViscosity, double specificHeat,
        double thermalConductivity, double boilingPointCelsius, double freezingPointCelsius, boolean isMolten,
        double radiolysisMultiplier) {
        this.fluidName = fluidName;
        this.density = density;
        this.dynamicViscosity = dynamicViscosity;
        this.specificHeat = specificHeat;
        this.thermalConductivity = thermalConductivity;
        this.boilingPointCelsius = boilingPointCelsius;
        this.freezingPointCelsius = freezingPointCelsius;
        this.isMolten = isMolten;
        this.radiolysisMultiplier = radiolysisMultiplier;
    }

    public static void register(CoolantFluidProperty property) {
        if (property != null && property.getFluidName() != null) {
            REGISTRY.put(
                property.getFluidName()
                    .toLowerCase(),
                property);
        }
    }

    public static void registerAlias(String alias, CoolantFluidProperty property) {
        if (alias != null && property != null) {
            REGISTRY.put(alias.toLowerCase(), property);
        }
    }

    public static CoolantFluidProperty get(String name) {
        if (name == null) {
            return null;
        }
        String clean = name.trim()
            .toLowerCase();
        return REGISTRY.get(clean);
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
        Double.POSITIVE_INFINITY, // Non-boiling closed coolant
        -25.0);

    public static final CoolantFluidProperty SODIUM = new CoolantFluidProperty(
        "sodium",
        927.0,
        0.0007,
        1260.0,
        86.0, // High liquid metal conductivity
        Double.POSITIVE_INFINITY, // Handled as non-boiling liquid metal
        97.8);

    public static final CoolantFluidProperty MOLTEN_CHEESE = new CoolantFluidProperty(
        "molten.cheese",
        1120.0, // 1.12 g/cm^3
        0.557, // 557 cP = 0.557 Pa*s
        3000.0, // 3 kJ/(kg*K)
        0.481, // 0.481 W/(m*K)
        Double.POSITIVE_INFINITY, // Non-boiling
        46.85); // GregTech melting point: 320 K - 273.15 = 46.85°C

    /**
     * Nether lava easter egg.
     * Flow rate in the nether matches water, giving it water-like viscosity.
     * Density is 3000 kg/m^3 based on:
     * "Hot stuff (2021), A. Yeo, H. Routledge, J. Lewis, D. Meggi, Journal of Physics Special Topics, University of
     * Leicester"
     * https://journals.le.ac.uk/index.php/pst/article/view/3956/3425
     */
    public static final CoolantFluidProperty LAVA = new CoolantFluidProperty(
        "lava",
        3000.0, // 3000 kg/m^3 (Yeo et al., 2021)
        0.001, // 1.0 mPa*s, same dynamic viscosity as water in the nether
        1250.0, // 1250 J/(kg*K) silicate melt specific heat
        1.5, // 1.5 W/(m*K) thermal conductivity
        Double.POSITIVE_INFINITY, // Non-boiling closed liquid
        1000.0); // Lava melting point: ~1000°C

    public static void resetDefaults() {
        REGISTRY.clear();
        register(WATER);
        register(DISTILLED_WATER);
        register(HEAVY_WATER);
        register(IC2_COOLANT);
        register(SODIUM);
        register(MOLTEN_CHEESE);
        register(LAVA);
        REGISTRY.put("minecraft:water", WATER);
        REGISTRY.put("fluid.water", WATER);
        REGISTRY.put("cheese", MOLTEN_CHEESE);
        REGISTRY.put("moltencheese", MOLTEN_CHEESE);
        REGISTRY.put("distilledwater", DISTILLED_WATER);
        REGISTRY.put("distilled_water", DISTILLED_WATER);
        REGISTRY.put("water.distilled", DISTILLED_WATER);
        REGISTRY.put("heavywater", HEAVY_WATER);
        REGISTRY.put("heavy_water", HEAVY_WATER);
        REGISTRY.put("water.heavy", HEAVY_WATER);
        REGISTRY.put("coolant", IC2_COOLANT);
        REGISTRY.put("fluid.sodium", SODIUM);
        REGISTRY.put("liquid.sodium", SODIUM);
        REGISTRY.put("lava", LAVA);
        REGISTRY.put("fluid.lava", LAVA);
        REGISTRY.put("minecraft:lava", LAVA);
    }

    public static CoolantFluidProperty getCanonical(String name) {
        if (name == null) return null;
        String clean = name.trim()
            .toLowerCase()
            .replace(" ", "")
            .replace("_", "");
        if (clean.equals("water") || clean.equals("minecraft:water")) return WATER;
        if (clean.equals("ic2distilledwater") || clean.equals("distilledwater") || clean.equals("water.distilled"))
            return DISTILLED_WATER;
        if (clean.equals("heavywater") || clean.equals("water.heavy")) return HEAVY_WATER;
        if (clean.equals("ic2coolant") || clean.equals("coolant")) return IC2_COOLANT;
        if (clean.equals("sodium") || clean.equals("fluid.sodium") || clean.equals("liquid.sodium")) return SODIUM;
        if (clean.equals("molten.cheese") || clean.equals("cheese") || clean.equals("moltencheese"))
            return MOLTEN_CHEESE;
        if (clean.equals("lava") || clean.equals("fluid.lava") || clean.equals("minecraft:lava")) return LAVA;
        return null;
    }

    static {
        resetDefaults();
    }

    public boolean isPlainWater() {
        return this == WATER || "water".equalsIgnoreCase(this.fluidName);
    }

    public boolean isLava() {
        return this == LAVA || "lava".equalsIgnoreCase(this.fluidName);
    }

    public boolean isMolten() {
        return isMolten;
    }

    public double getRadiolysisMultiplier() {
        return radiolysisMultiplier;
    }

    public double getDeclaredTemperatureCelsius() {
        try {
            net.minecraftforge.fluids.Fluid f = net.minecraftforge.fluids.FluidRegistry.getFluid(fluidName);
            if (f != null) {
                int tempK = f.getTemperature();
                if (tempK > 0) {
                    return tempK - 273.15;
                }
            }
        } catch (Throwable ignored) {}
        return freezingPointCelsius;
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

    public boolean canBoil() {
        return !Double.isInfinite(boilingPointCelsius);
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
