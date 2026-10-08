package com.gtnewhorizons.coolantloops.engine;

import java.util.HashMap;
import java.util.Map;

import gregtech.api.enums.Materials;
import gregtech.api.metatileentity.implementations.MTEFluidPipe;

/**
 * Registry mapping GregTech fluid pipe materials and sizes to hydrodynamic
 * simulation properties (diameter, roughness, pressure ratings, and thermal limits).
 */
public class CoolantPipingRegistry {

    public static class PipeProperties {

        public final double diameter; // Inner diameter (m)
        public final double roughness; // Wall roughness (m)
        public final double maxPressureBar; // Bursting pressure limit (bar)
        public final double maxTemperatureCelsius;// Maximum safe operating temperature (deg C)

        public PipeProperties(double diameter, double roughness, double maxPressureBar, double maxTemperatureCelsius) {
            this.diameter = diameter;
            this.roughness = roughness;
            this.maxPressureBar = maxPressureBar;
            this.maxTemperatureCelsius = maxTemperatureCelsius;
        }
    }

    private static final Map<String, Double> MATERIAL_PRESSURE_RATINGS_BAR = new HashMap<>();

    static {
        // Pressure ratings (in bar) for standard GregTech pipe materials
        MATERIAL_PRESSURE_RATINGS_BAR.put("wood", 2.0);
        MATERIAL_PRESSURE_RATINGS_BAR.put("potin", 6.0);
        MATERIAL_PRESSURE_RATINGS_BAR.put("copper", 8.0);
        MATERIAL_PRESSURE_RATINGS_BAR.put("bronze", 12.0);
        MATERIAL_PRESSURE_RATINGS_BAR.put("wroughtiron", 15.0);
        MATERIAL_PRESSURE_RATINGS_BAR.put("iron", 16.0);
        MATERIAL_PRESSURE_RATINGS_BAR.put("steel", 35.0);
        MATERIAL_PRESSURE_RATINGS_BAR.put("stainlesssteel", 70.0);
        MATERIAL_PRESSURE_RATINGS_BAR.put("titanium", 140.0);
        MATERIAL_PRESSURE_RATINGS_BAR.put("tungstensteel", 280.0);
        MATERIAL_PRESSURE_RATINGS_BAR.put("osmium", 600.0);
        MATERIAL_PRESSURE_RATINGS_BAR.put("hsse", 450.0);
        MATERIAL_PRESSURE_RATINGS_BAR.put("hssg", 400.0);
        MATERIAL_PRESSURE_RATINGS_BAR.put("hsss", 550.0);
        MATERIAL_PRESSURE_RATINGS_BAR.put("naquadah", 850.0);
        MATERIAL_PRESSURE_RATINGS_BAR.put("naquadahalloy", 1200.0);
        MATERIAL_PRESSURE_RATINGS_BAR.put("neutronium", 3000.0);
    }

    /**
     * Resolves the inner flow diameter (meters) from GregTech pipe thickness.
     */
    public static double getDiameterForThickness(float thickness) {
        if (thickness <= 0.251f) {
            return 0.05; // Tiny: 5 cm
        } else if (thickness <= 0.376f) {
            return 0.075; // Small: 7.5 cm
        } else if (thickness <= 0.501f) {
            return 0.10; // Normal: 10 cm
        } else if (thickness <= 0.751f) {
            return 0.15; // Large: 15 cm
        } else {
            return 0.20; // Huge: 20 cm
        }
    }

    /**
     * Resolves the maximum burst pressure (bar) for a given GT material name.
     */
    public static double getMaxPressureBar(String materialName) {
        if (materialName == null) {
            return 10.0;
        }
        String name = materialName.toLowerCase()
            .replace(" ", "")
            .replace("_", "")
            .replace("-", "");
        if (MATERIAL_PRESSURE_RATINGS_BAR.containsKey(name)) {
            return MATERIAL_PRESSURE_RATINGS_BAR.get(name);
        }
        return 15.0;
    }

    /**
     * Resolves the maximum burst pressure (bar) for a given GT material.
     */
    public static double getMaxPressureBar(Materials material) {
        if (material == null) {
            return 10.0;
        }
        double rating = getMaxPressureBar(material.mName);
        if (rating > 15.0 || MATERIAL_PRESSURE_RATINGS_BAR.containsKey(material.mName.toLowerCase())) {
            return rating;
        }
        // Fallback: estimate based on blast furnace temperature
        if (material.mBlastFurnaceTemp > 0) {
            return Math.max(20.0, material.mBlastFurnaceTemp / 25.0);
        }
        return rating;
    }

    /**
     * Resolves the maximum safe operating temperature (Celsius) for a given GT pipe.
     */
    public static double getMaxTemperatureCelsius(MTEFluidPipe pipe) {
        if (pipe == null) {
            return 100.0;
        }
        // pipe.mHeatResistance is in Kelvin in GT5
        if (pipe.mHeatResistance > 0) {
            return pipe.mHeatResistance - 273.15;
        }
        if (pipe.mMaterial != null && pipe.mMaterial.mBlastFurnaceTemp > 0) {
            return pipe.mMaterial.mBlastFurnaceTemp - 273.15;
        }
        return 200.0;
    }

    /**
     * Resolves full pipe simulation properties for a GT fluid pipe.
     */
    public static PipeProperties getProperties(MTEFluidPipe pipe) {
        if (pipe == null) {
            return new PipeProperties(0.10, 0.000045, 15.0, 200.0);
        }
        double diameter = getDiameterForThickness(pipe.mThickNess);
        double maxPressure = getMaxPressureBar(pipe.mMaterial);
        double maxTemp = getMaxTemperatureCelsius(pipe);
        double roughness = 0.000045; // Commercial metal pipe standard roughness
        return new PipeProperties(diameter, roughness, maxPressure, maxTemp);
    }

    /**
     * Checks if a material is allowed for coolant loop piping and tanks.
     * Only materials that have BOTH GregTech fluid pipes and Railcraft tanks are allowed:
     * Steel, Iron/Cast Iron/Wrought Iron, Stainless Steel, Titanium, TungstenSteel, Neutronium.
     */
    public static boolean isAllowedCoolantPipeMaterial(String materialName) {
        if (materialName == null) return false;
        String name = materialName.toLowerCase()
            .replace(" ", "")
            .replace("_", "")
            .replace("-", "");
        return name.equals("steel") || name.equals("iron")
            || name.equals("castiron")
            || name.equals("wroughtiron")
            || name.equals("stainlesssteel")
            || name.equals("titanium")
            || name.equals("tungstensteel")
            || name.equals("osmium")
            || name.equals("neutronium");
    }

    public static boolean isAllowedCoolantPipeMaterial(Materials mat) {
        if (mat == null) return false;
        return isAllowedCoolantPipeMaterial(mat.mName);
    }

    /**
     * Checks if two materials are functionally identical for coolant loop material matching.
     * Iron, Cast Iron, and Wrought Iron are treated as equivalent (Iron family).
     */
    public static boolean areMaterialsEqual(String m1, String m2) {
        if (m1 == null || m2 == null) return false;
        String n1 = m1.toLowerCase()
            .replace(" ", "")
            .replace("_", "")
            .replace("-", "");
        String n2 = m2.toLowerCase()
            .replace(" ", "")
            .replace("_", "")
            .replace("-", "");
        if (n1.equals(n2)) return true;
        if (isIron(n1) && isIron(n2)) return true;
        return false;
    }

    public static boolean areMaterialsEqual(Materials m1, Materials m2) {
        if (m1 == null || m2 == null) return false;
        if (m1 == m2) return true;
        return areMaterialsEqual(m1.mName, m2.mName);
    }

    public static boolean isIron(String name) {
        if (name == null) return false;
        String n = name.toLowerCase()
            .replace(" ", "")
            .replace("_", "")
            .replace("-", "");
        return n.equals("iron") || n.equals("castiron") || n.equals("wroughtiron");
    }

    public static boolean isIron(Materials m) {
        if (m == null) return false;
        return isIron(m.mName);
    }
}
