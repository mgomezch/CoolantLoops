package com.gtnewhorizons.coolantloops.common.config.data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Data-driven configuration container for closed-loop coolant fluids.
 */
public class CoolantFluidData {

    public static class FluidPropertyEntry {

        private String fluidName;
        private double density = 1000.0; // kg/m^3
        private double dynamicViscosity = 0.001; // Pa*s
        private double specificHeat = 4184.0; // J/(kg*K)
        private double thermalConductivity = 0.606; // W/(m*K)
        private double boilingPointCelsius = Double.POSITIVE_INFINITY;
        private double freezingPointCelsius = 0.0;
        private boolean isMolten = false;
        private double radiolysisMultiplier = 1.0;
        private List<String> aliases = new ArrayList<>();

        public FluidPropertyEntry() {}

        public FluidPropertyEntry(String fluidName, double density, double dynamicViscosity, double specificHeat,
            double thermalConductivity, double boilingPointCelsius, double freezingPointCelsius, boolean isMolten,
            double radiolysisMultiplier, List<String> aliases) {
            this.fluidName = fluidName;
            this.density = density;
            this.dynamicViscosity = dynamicViscosity;
            this.specificHeat = specificHeat;
            this.thermalConductivity = thermalConductivity;
            this.boilingPointCelsius = boilingPointCelsius;
            this.freezingPointCelsius = freezingPointCelsius;
            this.isMolten = isMolten;
            this.radiolysisMultiplier = radiolysisMultiplier;
            if (aliases != null) {
                this.aliases.addAll(aliases);
            }
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("fluidName", fluidName);
            map.put("density", density);
            map.put("dynamicViscosity", dynamicViscosity);
            map.put("specificHeat", specificHeat);
            map.put("thermalConductivity", thermalConductivity);
            map.put("boilingPointCelsius", Double.isInfinite(boilingPointCelsius) ? "Infinity" : boilingPointCelsius);
            map.put("freezingPointCelsius", freezingPointCelsius);
            map.put("isMolten", isMolten);
            map.put("radiolysisMultiplier", radiolysisMultiplier);
            map.put("aliases", aliases);
            return map;
        }

        @SuppressWarnings("unchecked")
        public static FluidPropertyEntry fromMap(Map<String, Object> map) {
            FluidPropertyEntry entry = new FluidPropertyEntry();
            if (map == null) return entry;

            if (map.containsKey("fluidName")) entry.fluidName = (String) map.get("fluidName");
            if (map.containsKey("density")) entry.density = ((Number) map.get("density")).doubleValue();
            if (map.containsKey("dynamicViscosity"))
                entry.dynamicViscosity = ((Number) map.get("dynamicViscosity")).doubleValue();
            if (map.containsKey("specificHeat")) entry.specificHeat = ((Number) map.get("specificHeat")).doubleValue();
            if (map.containsKey("thermalConductivity"))
                entry.thermalConductivity = ((Number) map.get("thermalConductivity")).doubleValue();

            if (map.containsKey("boilingPointCelsius")) {
                Object b = map.get("boilingPointCelsius");
                if (b instanceof Number) {
                    entry.boilingPointCelsius = ((Number) b).doubleValue();
                } else if (b instanceof String) {
                    String str = ((String) b).trim()
                        .toLowerCase();
                    if (str.contains("inf") || str.equals("none") || str.equals("null")) {
                        entry.boilingPointCelsius = Double.POSITIVE_INFINITY;
                    } else {
                        try {
                            entry.boilingPointCelsius = Double.parseDouble(str);
                        } catch (NumberFormatException e) {
                            entry.boilingPointCelsius = Double.POSITIVE_INFINITY;
                        }
                    }
                }
            }

            if (map.containsKey("freezingPointCelsius"))
                entry.freezingPointCelsius = ((Number) map.get("freezingPointCelsius")).doubleValue();
            if (map.containsKey("isMolten")) entry.isMolten = Boolean.TRUE.equals(map.get("isMolten"));
            if (map.containsKey("radiolysisMultiplier"))
                entry.radiolysisMultiplier = ((Number) map.get("radiolysisMultiplier")).doubleValue();

            if (map.containsKey("aliases") && map.get("aliases") instanceof List) {
                entry.aliases = new ArrayList<>();
                for (Object o : (List<?>) map.get("aliases")) {
                    if (o != null) entry.aliases.add(o.toString());
                }
            }
            return entry;
        }

        public String getFluidName() {
            return fluidName;
        }

        public void setFluidName(String fluidName) {
            this.fluidName = fluidName;
        }

        public double getDensity() {
            return density;
        }

        public void setDensity(double density) {
            this.density = density;
        }

        public double getDynamicViscosity() {
            return dynamicViscosity;
        }

        public void setDynamicViscosity(double dynamicViscosity) {
            this.dynamicViscosity = dynamicViscosity;
        }

        public double getSpecificHeat() {
            return specificHeat;
        }

        public void setSpecificHeat(double specificHeat) {
            this.specificHeat = specificHeat;
        }

        public double getThermalConductivity() {
            return thermalConductivity;
        }

        public void setThermalConductivity(double thermalConductivity) {
            this.thermalConductivity = thermalConductivity;
        }

        public double getBoilingPointCelsius() {
            return boilingPointCelsius;
        }

        public void setBoilingPointCelsius(double boilingPointCelsius) {
            this.boilingPointCelsius = boilingPointCelsius;
        }

        public double getFreezingPointCelsius() {
            return freezingPointCelsius;
        }

        public void setFreezingPointCelsius(double freezingPointCelsius) {
            this.freezingPointCelsius = freezingPointCelsius;
        }

        public boolean isMolten() {
            return isMolten;
        }

        public void setMolten(boolean molten) {
            isMolten = molten;
        }

        public double getRadiolysisMultiplier() {
            return radiolysisMultiplier;
        }

        public void setRadiolysisMultiplier(double radiolysisMultiplier) {
            this.radiolysisMultiplier = radiolysisMultiplier;
        }

        public List<String> getAliases() {
            return aliases;
        }

        public void setAliases(List<String> aliases) {
            this.aliases = aliases;
        }
    }

    private Map<String, FluidPropertyEntry> fluids = new LinkedHashMap<>();
    private List<String> liquidNuclearFuels = new ArrayList<>(java.util.Arrays.asList(
        "liquidfuel",
        "liquid_fuel",
        "nuclearfuel",
        "uraniumhexafluoride",
        "uraniumtetrafluoride",
        "thoriumbasedliquidfuel",
        "uraniumbasedliquidfuel",
        "plutoniumbasedliquidfuel",
        "naquadahbasedliquidfuel",
        "liquid_naquadah_fuel",
        "depletedthoriumbasedliquidfuel",
        "depleteduraniumbasedliquidfuel",
        "depletedplutoniumbasedliquidfuel",
        "depletednaquadahbasedliquidfuel"
    ));

    public Map<String, FluidPropertyEntry> getFluids() {
        return fluids;
    }

    public void setFluids(Map<String, FluidPropertyEntry> fluids) {
        this.fluids = fluids;
    }

    public List<String> getLiquidNuclearFuels() {
        return liquidNuclearFuels;
    }

    public void setLiquidNuclearFuels(List<String> fuels) {
        this.liquidNuclearFuels = fuels;
    }

    public boolean isLiquidNuclearFuel(String name) {
        if (name == null) return false;
        String clean = name.trim().toLowerCase();
        for (String fuel : liquidNuclearFuels) {
            if (fuel != null && clean.equals(fuel.trim().toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        Map<String, Object> fluidMap = new LinkedHashMap<>();
        for (Map.Entry<String, FluidPropertyEntry> e : fluids.entrySet()) {
            fluidMap.put(
                e.getKey(),
                e.getValue()
                    .toMap());
        }
        map.put("fluids", fluidMap);
        map.put("liquidNuclearFuels", liquidNuclearFuels);
        return map;
    }

    @SuppressWarnings("unchecked")
    public static CoolantFluidData fromMap(Map<String, Object> map) {
        CoolantFluidData data = new CoolantFluidData();
        if (map == null) return data;

        if (map.containsKey("fluids") && map.get("fluids") instanceof Map) {
            Map<String, Object> fMap = (Map<String, Object>) map.get("fluids");
            for (Map.Entry<String, Object> entry : fMap.entrySet()) {
                if (entry.getValue() instanceof Map) {
                    FluidPropertyEntry prop = FluidPropertyEntry.fromMap((Map<String, Object>) entry.getValue());
                    if (prop.getFluidName() == null || prop.getFluidName()
                        .isEmpty()) {
                        prop.setFluidName(entry.getKey());
                    }
                    data.fluids.put(entry.getKey(), prop);
                }
            }
        }

        if (map.containsKey("liquidNuclearFuels") && map.get("liquidNuclearFuels") instanceof List) {
            data.liquidNuclearFuels = new ArrayList<>();
            for (Object o : (List<?>) map.get("liquidNuclearFuels")) {
                if (o != null) {
                    data.liquidNuclearFuels.add(o.toString());
                }
            }
        }
        return data;
    }
}
