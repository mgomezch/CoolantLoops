package com.gtnewhorizons.coolantloops.common.config.data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Global hydrodynamic and thermodynamic physics parameters for Coolant Loops.
 */
public class CoolantPhysicsData {

    // Convection parameters
    private double vTurbulent = 0.5; // Velocity (m/s) at which turbulence begins
    private double vSaturation = 3.0; // Velocity (m/s) at which boundary layer saturation begins
    private double hLaminarSlope = 120.0; // Laminar regime slope W/(m^2*K) / (m/s)
    private double hTurbulentFactor = 1500.0; // Scaling factor for turbulent transition
    private double hMax = 5000.0; // Maximum asymptotic saturation coefficient W/(m^2*K)

    // Pump and hydraulic parameters
    private double unpoweredImpellerK = 8.0; // Drag loss coefficient when pump motor is disengaged
    private double maxPumpHeadPressurePa = Double.POSITIVE_INFINITY; // Maximum stall head pressure (Pa), uncapped by
                                                                     // default
    private double pumpMechanicalPowerPerEU = 80.0; // Mechanical power output (Watts) per EU drawn
    private long fillRateLitersPerTick = 1000L; // Loop filling rate from reservoir tank in L/tick
    private double defaultAmbientTemp = 20.0; // Fallback ambient temperature (deg C)

    // Geometry defaults
    private double standardPassageLength = 5.0; // Length of standard core passage (m)
    private double standardPassageDiameter = 0.10; // Inner diameter of core passage (m)
    private double defaultPipeRoughness = 0.000045; // Commercial metal standard roughness (m)

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("vTurbulent", vTurbulent);
        map.put("vSaturation", vSaturation);
        map.put("hLaminarSlope", hLaminarSlope);
        map.put("hTurbulentFactor", hTurbulentFactor);
        map.put("hMax", hMax);

        map.put("unpoweredImpellerK", unpoweredImpellerK);
        map.put("maxPumpHeadPressurePa", maxPumpHeadPressurePa);
        map.put("pumpMechanicalPowerPerEU", pumpMechanicalPowerPerEU);
        map.put("fillRateLitersPerTick", fillRateLitersPerTick);
        map.put("defaultAmbientTemp", defaultAmbientTemp);

        map.put("standardPassageLength", standardPassageLength);
        map.put("standardPassageDiameter", standardPassageDiameter);
        map.put("defaultPipeRoughness", defaultPipeRoughness);
        return map;
    }

    public static CoolantPhysicsData fromMap(Map<String, Object> map) {
        CoolantPhysicsData data = new CoolantPhysicsData();
        if (map == null) return data;

        if (map.containsKey("vTurbulent")) data.vTurbulent = ((Number) map.get("vTurbulent")).doubleValue();
        if (map.containsKey("vSaturation")) data.vSaturation = ((Number) map.get("vSaturation")).doubleValue();
        if (map.containsKey("hLaminarSlope")) data.hLaminarSlope = ((Number) map.get("hLaminarSlope")).doubleValue();
        if (map.containsKey("hTurbulentFactor"))
            data.hTurbulentFactor = ((Number) map.get("hTurbulentFactor")).doubleValue();
        if (map.containsKey("hMax")) data.hMax = ((Number) map.get("hMax")).doubleValue();

        if (map.containsKey("unpoweredImpellerK"))
            data.unpoweredImpellerK = ((Number) map.get("unpoweredImpellerK")).doubleValue();
        if (map.containsKey("maxPumpHeadPressurePa"))
            data.maxPumpHeadPressurePa = ((Number) map.get("maxPumpHeadPressurePa")).doubleValue();
        if (map.containsKey("pumpMechanicalPowerPerEU"))
            data.pumpMechanicalPowerPerEU = ((Number) map.get("pumpMechanicalPowerPerEU")).doubleValue();
        if (map.containsKey("fillRateLitersPerTick"))
            data.fillRateLitersPerTick = ((Number) map.get("fillRateLitersPerTick")).longValue();
        if (map.containsKey("defaultAmbientTemp"))
            data.defaultAmbientTemp = ((Number) map.get("defaultAmbientTemp")).doubleValue();

        if (map.containsKey("standardPassageLength"))
            data.standardPassageLength = ((Number) map.get("standardPassageLength")).doubleValue();
        if (map.containsKey("standardPassageDiameter"))
            data.standardPassageDiameter = ((Number) map.get("standardPassageDiameter")).doubleValue();
        if (map.containsKey("defaultPipeRoughness"))
            data.defaultPipeRoughness = ((Number) map.get("defaultPipeRoughness")).doubleValue();

        return data;
    }

    public double getVTurbulent() {
        return vTurbulent;
    }

    public void setVTurbulent(double vTurbulent) {
        this.vTurbulent = vTurbulent;
    }

    public double getVSaturation() {
        return vSaturation;
    }

    public void setVSaturation(double vSaturation) {
        this.vSaturation = vSaturation;
    }

    public double getHLaminarSlope() {
        return hLaminarSlope;
    }

    public void setHLaminarSlope(double hLaminarSlope) {
        this.hLaminarSlope = hLaminarSlope;
    }

    public double getHTurbulentFactor() {
        return hTurbulentFactor;
    }

    public void setHTurbulentFactor(double hTurbulentFactor) {
        this.hTurbulentFactor = hTurbulentFactor;
    }

    public double getHMax() {
        return hMax;
    }

    public void setHMax(double hMax) {
        this.hMax = hMax;
    }

    public double getUnpoweredImpellerK() {
        return unpoweredImpellerK;
    }

    public void setUnpoweredImpellerK(double unpoweredImpellerK) {
        this.unpoweredImpellerK = unpoweredImpellerK;
    }

    public double getMaxPumpHeadPressurePa() {
        return maxPumpHeadPressurePa;
    }

    public void setMaxPumpHeadPressurePa(double maxPumpHeadPressurePa) {
        this.maxPumpHeadPressurePa = maxPumpHeadPressurePa;
    }

    public double getPumpMechanicalPowerPerEU() {
        return pumpMechanicalPowerPerEU;
    }

    public void setPumpMechanicalPowerPerEU(double pumpMechanicalPowerPerEU) {
        this.pumpMechanicalPowerPerEU = pumpMechanicalPowerPerEU;
    }

    public long getFillRateLitersPerTick() {
        return fillRateLitersPerTick;
    }

    public void setFillRateLitersPerTick(long fillRateLitersPerTick) {
        this.fillRateLitersPerTick = fillRateLitersPerTick;
    }

    public double getDefaultAmbientTemp() {
        return defaultAmbientTemp;
    }

    public void setDefaultAmbientTemp(double defaultAmbientTemp) {
        this.defaultAmbientTemp = defaultAmbientTemp;
    }

    public double getStandardPassageLength() {
        return standardPassageLength;
    }

    public void setStandardPassageLength(double standardPassageLength) {
        this.standardPassageLength = standardPassageLength;
    }

    public double getStandardPassageDiameter() {
        return standardPassageDiameter;
    }

    public void setStandardPassageDiameter(double standardPassageDiameter) {
        this.standardPassageDiameter = standardPassageDiameter;
    }

    public double getDefaultPipeRoughness() {
        return defaultPipeRoughness;
    }

    public void setDefaultPipeRoughness(double defaultPipeRoughness) {
        this.defaultPipeRoughness = defaultPipeRoughness;
    }
}
