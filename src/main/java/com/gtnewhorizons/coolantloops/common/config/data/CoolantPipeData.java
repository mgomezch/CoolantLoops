package com.gtnewhorizons.coolantloops.common.config.data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Data-driven configuration container for coolant loop piping, burst ratings, and material limits.
 */
public class CoolantPipeData {

    private Map<String, Double> materialPressureRatingsBar = new LinkedHashMap<>();
    private Map<String, Double> materialTemperatureLimitsCelsius = new LinkedHashMap<>();
    private Map<String, Double> thicknessDiameters = new LinkedHashMap<>();
    private List<String> allowedMaterials = new ArrayList<>();
    private Map<String, List<String>> materialFamilies = new LinkedHashMap<>();
    private double defaultRoughness = 0.000045; // Commercial metal pipe roughness in meters

    public CoolantPipeData() {
        populateDefaults();
    }

    private void populateDefaults() {
        // Default burst pressure ratings (bar) strictly for allowed coolant pipe materials
        materialPressureRatingsBar.put("castiron", 16.0);
        materialPressureRatingsBar.put("iron", 16.0);
        materialPressureRatingsBar.put("wroughtiron", 15.0);
        materialPressureRatingsBar.put("steel", 35.0);
        materialPressureRatingsBar.put("stainlesssteel", 70.0);
        materialPressureRatingsBar.put("titanium", 140.0);
        materialPressureRatingsBar.put("tungstensteel", 280.0);
        materialPressureRatingsBar.put("osmium", 600.0);
        materialPressureRatingsBar.put("neutronium", 3000.0);

        // Explicit temperature limits (deg C) overrides strictly for allowed coolant pipe materials
        materialTemperatureLimitsCelsius.put("castiron", 1538.0);
        materialTemperatureLimitsCelsius.put("iron", 1538.0);
        materialTemperatureLimitsCelsius.put("wroughtiron", 1500.0);
        materialTemperatureLimitsCelsius.put("steel", 1500.0);
        materialTemperatureLimitsCelsius.put("stainlesssteel", 1700.0);
        materialTemperatureLimitsCelsius.put("titanium", 1668.0);
        materialTemperatureLimitsCelsius.put("tungstensteel", 3000.0);
        materialTemperatureLimitsCelsius.put("osmium", 3033.0);
        materialTemperatureLimitsCelsius.put("neutronium", 10000.0);

        // Pipe thickness to flow diameter mappings (meters)
        thicknessDiameters.put("tiny", 0.05);
        thicknessDiameters.put("small", 0.075);
        thicknessDiameters.put("normal", 0.10);
        thicknessDiameters.put("large", 0.15);
        thicknessDiameters.put("huge", 0.20);

        // Materials allowed in closed loops (must match Railcraft tank availability)
        allowedMaterials.add("steel");
        allowedMaterials.add("iron");
        allowedMaterials.add("castiron");
        allowedMaterials.add("wroughtiron");
        allowedMaterials.add("stainlesssteel");
        allowedMaterials.add("titanium");
        allowedMaterials.add("tungstensteel");
        allowedMaterials.add("osmium");
        allowedMaterials.add("neutronium");

        // Material compatibility families
        materialFamilies.put("iron", new ArrayList<>(java.util.Arrays.asList("iron", "castiron", "wroughtiron")));
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("materialPressureRatingsBar", materialPressureRatingsBar);
        map.put("materialTemperatureLimitsCelsius", materialTemperatureLimitsCelsius);
        map.put("thicknessDiameters", thicknessDiameters);
        map.put("allowedMaterials", allowedMaterials);
        map.put("materialFamilies", materialFamilies);
        map.put("defaultRoughness", defaultRoughness);
        return map;
    }

    @SuppressWarnings("unchecked")
    public static CoolantPipeData fromMap(Map<String, Object> map) {
        CoolantPipeData data = new CoolantPipeData();
        if (map == null) return data;

        if (map.containsKey("materialPressureRatingsBar") && map.get("materialPressureRatingsBar") instanceof Map) {
            data.materialPressureRatingsBar.clear();
            Map<String, Object> pMap = (Map<String, Object>) map.get("materialPressureRatingsBar");
            for (Map.Entry<String, Object> e : pMap.entrySet()) {
                if (e.getValue() instanceof Number) {
                    data.materialPressureRatingsBar.put(cleanKey(e.getKey()), ((Number) e.getValue()).doubleValue());
                }
            }
        }

        if (map.containsKey("materialTemperatureLimitsCelsius")
            && map.get("materialTemperatureLimitsCelsius") instanceof Map) {
            data.materialTemperatureLimitsCelsius.clear();
            Map<String, Object> tMap = (Map<String, Object>) map.get("materialTemperatureLimitsCelsius");
            for (Map.Entry<String, Object> e : tMap.entrySet()) {
                if (e.getValue() instanceof Number) {
                    data.materialTemperatureLimitsCelsius
                        .put(cleanKey(e.getKey()), ((Number) e.getValue()).doubleValue());
                }
            }
        }

        if (map.containsKey("thicknessDiameters") && map.get("thicknessDiameters") instanceof Map) {
            data.thicknessDiameters.clear();
            Map<String, Object> dMap = (Map<String, Object>) map.get("thicknessDiameters");
            for (Map.Entry<String, Object> e : dMap.entrySet()) {
                if (e.getValue() instanceof Number) {
                    data.thicknessDiameters.put(cleanKey(e.getKey()), ((Number) e.getValue()).doubleValue());
                }
            }
        }

        if (map.containsKey("allowedMaterials") && map.get("allowedMaterials") instanceof List) {
            data.allowedMaterials.clear();
            for (Object o : (List<?>) map.get("allowedMaterials")) {
                if (o != null) data.allowedMaterials.add(cleanKey(o.toString()));
            }
        }

        if (map.containsKey("materialFamilies") && map.get("materialFamilies") instanceof Map) {
            data.materialFamilies.clear();
            Map<String, Object> fMap = (Map<String, Object>) map.get("materialFamilies");
            for (Map.Entry<String, Object> entry : fMap.entrySet()) {
                if (entry.getValue() instanceof List) {
                    List<String> list = new ArrayList<>();
                    for (Object item : (List<?>) entry.getValue()) {
                        if (item != null) list.add(cleanKey(item.toString()));
                    }
                    data.materialFamilies.put(cleanKey(entry.getKey()), list);
                }
            }
        }

        if (map.containsKey("defaultRoughness")) {
            data.defaultRoughness = ((Number) map.get("defaultRoughness")).doubleValue();
        }

        return data;
    }

    public boolean areMaterialsEqual(String m1, String m2) {
        if (m1 == null || m2 == null) return false;
        String n1 = cleanKey(m1);
        String n2 = cleanKey(m2);
        if (n1.equals(n2)) return true;
        for (List<String> family : materialFamilies.values()) {
            if (family != null && family.contains(n1) && family.contains(n2)) {
                return true;
            }
        }
        return false;
    }

    private static String cleanKey(String key) {
        if (key == null) return "";
        return key.toLowerCase()
            .replace(" ", "")
            .replace("_", "")
            .replace("-", "");
    }

    public Map<String, Double> getMaterialPressureRatingsBar() {
        return materialPressureRatingsBar;
    }

    public Map<String, Double> getMaterialTemperatureLimitsCelsius() {
        return materialTemperatureLimitsCelsius;
    }

    public Map<String, Double> getThicknessDiameters() {
        return thicknessDiameters;
    }

    public List<String> getAllowedMaterials() {
        return allowedMaterials;
    }

    public Map<String, List<String>> getMaterialFamilies() {
        return materialFamilies;
    }

    public double getDefaultRoughness() {
        return defaultRoughness;
    }

    public void setDefaultRoughness(double defaultRoughness) {
        this.defaultRoughness = defaultRoughness;
    }
}
