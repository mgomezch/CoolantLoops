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

        public static class SecondaryCoolantEntry {

            public String inputFluidName;
            public double boilingPointCelsius;
            public String outputGasName;
            public double latentHeatJoulePerLiter;
            public int expansionRatio;
            public boolean isSteam;

            public SecondaryCoolantEntry() {}

            public SecondaryCoolantEntry(String inputFluidName, double boilingPointCelsius, String outputGasName,
                double latentHeatJoulePerLiter, int expansionRatio, boolean isSteam) {
                this.inputFluidName = inputFluidName;
                this.boilingPointCelsius = boilingPointCelsius;
                this.outputGasName = outputGasName;
                this.latentHeatJoulePerLiter = latentHeatJoulePerLiter;
                this.expansionRatio = expansionRatio;
                this.isSteam = isSteam;
            }

            public Map<String, Object> toMap() {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("inputFluidName", inputFluidName);
                map.put("boilingPointCelsius", boilingPointCelsius);
                map.put("outputGasName", outputGasName);
                map.put("latentHeatJoulePerLiter", latentHeatJoulePerLiter);
                map.put("expansionRatio", expansionRatio);
                map.put("isSteam", isSteam);
                return map;
            }

            public static SecondaryCoolantEntry fromMap(Map<String, Object> map) {
                SecondaryCoolantEntry e = new SecondaryCoolantEntry();
                if (map == null) return e;
                if (map.containsKey("inputFluidName")) e.inputFluidName = String.valueOf(map.get("inputFluidName"));
                if (map.containsKey("boilingPointCelsius"))
                    e.boilingPointCelsius = ((Number) map.get("boilingPointCelsius")).doubleValue();
                if (map.containsKey("outputGasName")) e.outputGasName = String.valueOf(map.get("outputGasName"));
                if (map.containsKey("latentHeatJoulePerLiter"))
                    e.latentHeatJoulePerLiter = ((Number) map.get("latentHeatJoulePerLiter")).doubleValue();
                if (map.containsKey("expansionRatio"))
                    e.expansionRatio = ((Number) map.get("expansionRatio")).intValue();
                if (map.containsKey("isSteam")) e.isSteam = Boolean.parseBoolean(String.valueOf(map.get("isSteam")));
                return e;
            }
        }

        public double exchangeArea = 24.0; // 24 m^2 internal tube bundle area
        public double minorLossK = 1.2; // Heat exchanger core resistance
        public double latentHeatJoulePerLiter = 2260000.0; // Latent heat of vaporization for water
        public int steamExpansionRatio = 160; // 1 L water -> 160 L steam
        public double superheatedTempThreshold = 300.0; // deg C for superheated steam
        public double supercriticalTempThreshold = 700.0; // deg C for supercritical steam
        public double maxTempDropPerTick = 40.0; // Maximum primary temperature drop per tick
        public double coolingFactor = 0.5; // Primary temperature relaxation factor
        public double baseMaxHeatPerBatchJoules = 2260000.0; // Base heat transfer cap per 20-tick batch at 1A LV (32
                                                             // EU/t) (equivalent to 1 L distilled water)
        public int batchTicks = 20; // Batch duration (20 ticks = 1 second)
        public List<String> allowedSecondaryFluids = new ArrayList<>(
            Arrays.asList(
                "ic2distilledwater",
                "distilledwater",
                "distilled_water",
                "water.distilled",
                "liquidnitrogen",
                "liquid_nitrogen",
                "liquidoxygen",
                "liquid_oxygen",
                "liquidair",
                "liquid_air",
                "liquid_helium",
                "liquidhelium",
                "liquid helium",
                "bwm.liquidhelium"));
        public List<SecondaryCoolantEntry> secondaryCoolants = new ArrayList<>(
            Arrays.asList(
                new SecondaryCoolantEntry("ic2distilledwater", 100.0, "steam", 2260000.0, 160, true),
                new SecondaryCoolantEntry("distilledwater", 100.0, "steam", 2260000.0, 160, true),
                new SecondaryCoolantEntry("distilled_water", 100.0, "steam", 2260000.0, 160, true),
                new SecondaryCoolantEntry("water.distilled", 100.0, "steam", 2260000.0, 160, true),
                new SecondaryCoolantEntry("liquidnitrogen", -196.0, "nitrogen", 160000.0, 1, false),
                new SecondaryCoolantEntry("liquid_nitrogen", -196.0, "nitrogen", 160000.0, 1, false),
                new SecondaryCoolantEntry("liquidoxygen", -183.0, "oxygen", 243000.0, 1, false),
                new SecondaryCoolantEntry("liquid_oxygen", -183.0, "oxygen", 243000.0, 1, false),
                new SecondaryCoolantEntry("liquidair", -194.0, "air", 174000.0, 1, false),
                new SecondaryCoolantEntry("liquid_air", -194.0, "air", 174000.0, 1, false),
                new SecondaryCoolantEntry("liquid_helium", -269.0, "helium", 20700.0, 1, false),
                new SecondaryCoolantEntry("liquidhelium", -269.0, "helium", 20700.0, 1, false),
                new SecondaryCoolantEntry("liquid helium", -269.0, "helium", 20700.0, 1, false),
                new SecondaryCoolantEntry("bwm.liquidhelium", -269.0, "helium", 20700.0, 1, false)));

        public boolean isAllowedSecondaryFluid(String fluidName) {
            if (fluidName == null) return false;
            String clean = fluidName.trim()
                .toLowerCase();
            for (String allowed : allowedSecondaryFluids) {
                if (allowed != null && clean.equals(
                    allowed.trim()
                        .toLowerCase())) {
                    return true;
                }
            }
            return findSecondaryCoolant(fluidName) != null;
        }

        public SecondaryCoolantEntry findSecondaryCoolant(String fluidName) {
            if (fluidName == null) return null;
            String clean = fluidName.trim()
                .toLowerCase();
            String norm = clean.replace("_", "")
                .replace(".", "")
                .replace(" ", "");

            // If explicitly configured in allowedSecondaryFluids, allow even plain water
            boolean inAllowedList = false;
            for (String allowed : allowedSecondaryFluids) {
                if (allowed != null && clean.equals(
                    allowed.trim()
                        .toLowerCase())) {
                    inAllowedList = true;
                    break;
                }
            }

            if ((norm.equals("water") || norm.equals("minecraft:water") || norm.equals("fluid.water"))
                && !inAllowedList) {
                return null; // Plain water is prohibited by default unless explicitly configured by admin
            }

            for (SecondaryCoolantEntry entry : secondaryCoolants) {
                if (entry != null && entry.inputFluidName != null) {
                    String entryClean = entry.inputFluidName.trim()
                        .toLowerCase();
                    String entryNorm = entryClean.replace("_", "")
                        .replace(".", "")
                        .replace(" ", "");
                    if (clean.equals(entryClean) || norm.equals(entryNorm)) {
                        return entry;
                    }
                }
            }
            // Fallback for distilled water or configured plain water
            if (norm.equals("ic2distilledwater") || norm.equals("distilledwater")
                || norm.equals("waterdistilled")
                || (inAllowedList
                    && (norm.equals("water") || norm.equals("minecraft:water") || norm.equals("fluid.water")))) {
                return new SecondaryCoolantEntry(
                    clean,
                    100.0,
                    "steam",
                    latentHeatJoulePerLiter,
                    steamExpansionRatio,
                    true);
            }
            if (norm.equals("liquidnitrogen") || norm.equals("nitrogenliquid")) {
                return new SecondaryCoolantEntry("liquidnitrogen", -196.0, "nitrogen", 160000.0, 160, false);
            }
            if (norm.equals("liquidoxygen") || norm.equals("oxygenliquid")) {
                return new SecondaryCoolantEntry("liquidoxygen", -183.0, "oxygen", 243000.0, 160, false);
            }
            if (norm.equals("liquidair") || norm.equals("airliquid")) {
                return new SecondaryCoolantEntry("liquidair", -194.0, "air", 174000.0, 160, false);
            }
            if (norm.equals("liquidhelium") || norm.equals("heliumliquid") || norm.equals("bwmliquidhelium")) {
                return new SecondaryCoolantEntry("liquid_helium", -269.0, "helium", 20700.0, 160, false);
            }
            if (inAllowedList) {
                // Admin added an arbitrary allowed secondary fluid, treat as steam generator at 100 C if unknown
                return new SecondaryCoolantEntry(
                    clean,
                    100.0,
                    "steam",
                    latentHeatJoulePerLiter,
                    steamExpansionRatio,
                    true);
            }
            return null;
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
            List<Map<String, Object>> coolantMaps = new ArrayList<>();
            for (SecondaryCoolantEntry e : secondaryCoolants) {
                if (e != null) coolantMaps.add(e.toMap());
            }
            map.put("secondaryCoolants", coolantMaps);
            return map;
        }

        @SuppressWarnings("unchecked")
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
            if (map.containsKey("secondaryCoolants") && map.get("secondaryCoolants") instanceof List) {
                d.secondaryCoolants = new ArrayList<>();
                for (Object item : (List<?>) map.get("secondaryCoolants")) {
                    if (item instanceof Map) {
                        d.secondaryCoolants.add(SecondaryCoolantEntry.fromMap((Map<String, Object>) item));
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

    public static class CoolantBathData {

        public long fillVolumePerSlotLiters = 1000L; // Coolant fill volume per slot in Liters
        public double baseMinorLossK = 1.0; // Base minor loss coefficient of empty bath
        public double itemMinorLossK = 1.0; // Additional minor loss K per active item in bath

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("fillVolumePerSlotLiters", fillVolumePerSlotLiters);
            map.put("baseMinorLossK", baseMinorLossK);
            map.put("itemMinorLossK", itemMinorLossK);
            return map;
        }

        public static CoolantBathData fromMap(Map<String, Object> map) {
            CoolantBathData d = new CoolantBathData();
            if (map == null) return d;
            if (map.containsKey("fillVolumePerSlotLiters"))
                d.fillVolumePerSlotLiters = ((Number) map.get("fillVolumePerSlotLiters")).longValue();
            if (map.containsKey("baseMinorLossK"))
                d.baseMinorLossK = ((Number) map.get("baseMinorLossK")).doubleValue();
            if (map.containsKey("itemMinorLossK"))
                d.itemMinorLossK = ((Number) map.get("itemMinorLossK")).doubleValue();
            return d;
        }
    }

    private PHEData phe = new PHEData();
    private DegasserData degasser = new DegasserData();
    private ReservoirData reservoir = new ReservoirData();
    private CoolantBathData bath = new CoolantBathData();

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

    public CoolantBathData getBath() {
        return bath;
    }

    public void setBath(CoolantBathData bath) {
        this.bath = bath;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("phe", phe.toMap());
        map.put("degasser", degasser.toMap());
        map.put("bath", bath.toMap());
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
        if (map.containsKey("bath") && map.get("bath") instanceof Map) {
            data.bath = CoolantBathData.fromMap((Map<String, Object>) map.get("bath"));
        }
        if (map.containsKey("reservoir") && map.get("reservoir") instanceof Map) {
            data.reservoir = ReservoirData.fromMap((Map<String, Object>) map.get("reservoir"));
        }
        return data;
    }
}
