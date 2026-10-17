package com.gtnewhorizons.coolantloops.common.config.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Data-driven configuration container for loop devices (PHE, Degasser, Creative Reservoir).
 */
public class CoolantDeviceData {

    public static class PHEData {

        public double exchangeArea = 24.0; // 24 m^2 internal tube bundle area
        public double minorLossK = 1.2; // Heat exchanger core resistance
        public double latentHeatJoulePerLiter = 2260000.0; // Latent heat of vaporization for water
        public int steamExpansionRatio = 160; // 1 L water -> 160 L steam
        public double superheatedTempThreshold = 300.0; // deg C for superheated steam
        public double supercriticalTempThreshold = 700.0; // deg C for supercritical steam
        public double maxTempDropPerTick = 40.0; // Maximum primary temperature drop per tick
        public double coolingFactor = 0.5; // Primary temperature relaxation factor
        public List<String> allowedSecondaryFluids = new ArrayList<>(
            Arrays.asList("ic2distilledwater", "distilledwater", "distilled_water", "water.distilled"));

        public boolean isAllowedSecondaryFluid(String fluidName) {
            if (fluidName == null) return false;
            String clean = fluidName.trim().toLowerCase();
            for (String allowed : allowedSecondaryFluids) {
                if (allowed != null && clean.equals(allowed.trim().toLowerCase())) {
                    return true;
                }
            }
            return false;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("exchangeArea", exchangeArea);
            map.put("minorLossK", minorLossK);
            map.put("latentHeatJoulePerLiter", latentHeatJoulePerLiter);
            map.put("steamExpansionRatio", steamExpansionRatio);
            map.put("superheatedTempThreshold", superheatedTempThreshold);
            map.put("supercriticalTempThreshold", supercriticalTempThreshold);
            map.put("maxTempDropPerTick", maxTempDropPerTick);
            map.put("coolingFactor", coolingFactor);
            map.put("allowedSecondaryFluids", allowedSecondaryFluids);
            return map;
        }

        public static PHEData fromMap(Map<String, Object> map) {
            PHEData d = new PHEData();
            if (map == null) return d;
            if (map.containsKey("exchangeArea")) d.exchangeArea = ((Number) map.get("exchangeArea")).doubleValue();
            if (map.containsKey("minorLossK")) d.minorLossK = ((Number) map.get("minorLossK")).doubleValue();
            if (map.containsKey("latentHeatJoulePerLiter"))
                d.latentHeatJoulePerLiter = ((Number) map.get("latentHeatJoulePerLiter")).doubleValue();
            if (map.containsKey("steamExpansionRatio"))
                d.steamExpansionRatio = ((Number) map.get("steamExpansionRatio")).intValue();
            if (map.containsKey("superheatedTempThreshold"))
                d.superheatedTempThreshold = ((Number) map.get("superheatedTempThreshold")).doubleValue();
            if (map.containsKey("supercriticalTempThreshold"))
                d.supercriticalTempThreshold = ((Number) map.get("supercriticalTempThreshold")).doubleValue();
            if (map.containsKey("maxTempDropPerTick"))
                d.maxTempDropPerTick = ((Number) map.get("maxTempDropPerTick")).doubleValue();
            if (map.containsKey("coolingFactor")) d.coolingFactor = ((Number) map.get("coolingFactor")).doubleValue();
            if (map.containsKey("allowedSecondaryFluids") && map.get("allowedSecondaryFluids") instanceof List) {
                d.allowedSecondaryFluids = new ArrayList<>();
                for (Object item : (List<?>) map.get("allowedSecondaryFluids")) {
                    if (item != null) {
                        d.allowedSecondaryFluids.add(item.toString());
                    }
                }
            }
            return d;
        }
    }

    public static class DegasserData {

        public double minorLossK = 1.5; // Column pack resistance
        public double gasExtractionEfficiency = 0.25; // Extraction fraction
        public double gasExtractionPowerEuPerLiter = 8.0; // EU cost per liter extracted

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("minorLossK", minorLossK);
            map.put("gasExtractionEfficiency", gasExtractionEfficiency);
            map.put("gasExtractionPowerEuPerLiter", gasExtractionPowerEuPerLiter);
            return map;
        }

        public static DegasserData fromMap(Map<String, Object> map) {
            DegasserData d = new DegasserData();
            if (map == null) return d;
            if (map.containsKey("minorLossK")) d.minorLossK = ((Number) map.get("minorLossK")).doubleValue();
            if (map.containsKey("gasExtractionEfficiency"))
                d.gasExtractionEfficiency = ((Number) map.get("gasExtractionEfficiency")).doubleValue();
            if (map.containsKey("gasExtractionPowerEuPerLiter"))
                d.gasExtractionPowerEuPerLiter = ((Number) map.get("gasExtractionPowerEuPerLiter")).doubleValue();
            return d;
        }
    }

    public static class ReservoirData {

        public double exchangeArea = 2.0; // Heat exchange surface area in m^2
        public List<Double> temperaturePresets = new ArrayList<>(Arrays.asList(20.0, 100.0, 300.0, 600.0, 1000.0));

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("exchangeArea", exchangeArea);
            map.put("temperaturePresets", temperaturePresets);
            return map;
        }

        public static ReservoirData fromMap(Map<String, Object> map) {
            ReservoirData d = new ReservoirData();
            if (map == null) return d;
            if (map.containsKey("exchangeArea")) d.exchangeArea = ((Number) map.get("exchangeArea")).doubleValue();
            if (map.containsKey("temperaturePresets") && map.get("temperaturePresets") instanceof List) {
                d.temperaturePresets.clear();
                for (Object o : (List<?>) map.get("temperaturePresets")) {
                    if (o instanceof Number) d.temperaturePresets.add(((Number) o).doubleValue());
                }
            }
            return d;
        }
    }

    private PHEData phe = new PHEData();
    private DegasserData degasser = new DegasserData();
    private ReservoirData reservoir = new ReservoirData();

    public PHEData getPhe() {
        return phe;
    }

    public void setPhe(PHEData phe) {
        this.phe = phe;
    }

    public DegasserData getDegasser() {
        return degasser;
    }

    public void setDegasser(DegasserData degasser) {
        this.degasser = degasser;
    }

    public ReservoirData getReservoir() {
        return reservoir;
    }

    public void setReservoir(ReservoirData reservoir) {
        this.reservoir = reservoir;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("phe", phe.toMap());
        map.put("degasser", degasser.toMap());
        map.put("reservoir", reservoir.toMap());
        return map;
    }

    @SuppressWarnings("unchecked")
    public static CoolantDeviceData fromMap(Map<String, Object> map) {
        CoolantDeviceData data = new CoolantDeviceData();
        if (map == null) return data;
        if (map.containsKey("phe") && map.get("phe") instanceof Map) {
            data.phe = PHEData.fromMap((Map<String, Object>) map.get("phe"));
        }
        if (map.containsKey("degasser") && map.get("degasser") instanceof Map) {
            data.degasser = DegasserData.fromMap((Map<String, Object>) map.get("degasser"));
        }
        if (map.containsKey("reservoir") && map.get("reservoir") instanceof Map) {
            data.reservoir = ReservoirData.fromMap((Map<String, Object>) map.get("reservoir"));
        }
        return data;
    }
}
