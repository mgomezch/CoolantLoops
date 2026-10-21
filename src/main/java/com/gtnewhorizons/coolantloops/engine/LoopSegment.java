package com.gtnewhorizons.coolantloops.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.util.ChunkCoordinates;

/**
 * Represents a discrete physical pipe segment or in-line device passage
 * within a closed coolant loop.
 */
public class LoopSegment {

    private final String id;
    private final double length; // Meters (m)
    private final double diameter; // Inner diameter (m)
    private final double area; // Cross-sectional area (m^2)
    private final double roughness; // Absolute pipe wall roughness (m)
    private double minorLossK; // Loss coefficient K (dimensionless)
    private final double maxPressureBar; // Bursting limit in bar
    private final double maxTemperatureCelsius;// Maximum safe operating temperature in deg C

    private double currentTemperatureCelsius; // Current temperature of coolant in this segment
    private double flowFraction = 1.0; // Fraction of loop volumetric flow rate traversing this segment
    private final List<LoopSegment> upstreamSegments = new ArrayList<>();

    public LoopSegment(String id, double length, double diameter, double roughness, double minorLossK,
        double maxPressureBar, double maxTemperatureCelsius, double initialTempCelsius) {
        this(
            id,
            Integer.MIN_VALUE,
            Integer.MIN_VALUE,
            Integer.MIN_VALUE,
            length,
            diameter,
            roughness,
            minorLossK,
            maxPressureBar,
            maxTemperatureCelsius,
            initialTempCelsius);
    }

    public LoopSegment(String id, int blockX, int blockY, int blockZ, double length, double diameter, double roughness,
        double minorLossK, double maxPressureBar, double maxTemperatureCelsius, double initialTempCelsius) {
        if (length <= 0 || diameter <= 0) {
            throw new IllegalArgumentException("Length and diameter must be positive");
        }
        this.id = id;
        this.blockX = blockX;
        this.blockY = blockY;
        this.blockZ = blockZ;
        this.length = length;
        this.diameter = diameter;
        this.area = Math.PI * Math.pow(diameter / 2.0, 2);
        this.roughness = roughness;
        this.minorLossK = minorLossK;
        this.maxPressureBar = maxPressureBar;
        this.maxTemperatureCelsius = maxTemperatureCelsius;
        this.currentTemperatureCelsius = initialTempCelsius;
    }

    public LoopSegment(String id, ChunkCoordinates coords, double length, double diameter, double roughness,
        double minorLossK, double maxPressureBar, double maxTemperatureCelsius, double initialTempCelsius) {
        this(
            id,
            coords != null ? coords.posX : Integer.MIN_VALUE,
            coords != null ? coords.posY : Integer.MIN_VALUE,
            coords != null ? coords.posZ : Integer.MIN_VALUE,
            length,
            diameter,
            roughness,
            minorLossK,
            maxPressureBar,
            maxTemperatureCelsius,
            initialTempCelsius);
    }

    public String getId() {
        return id;
    }

    public double getLength() {
        return length;
    }

    public double getDiameter() {
        return diameter;
    }

    public double getArea() {
        return area;
    }

    private double customVolumeM3 = -1.0;

    /**
     * Volume of fluid contained within this segment in m^3.
     */
    public double getVolume() {
        if (customVolumeM3 >= 0.0) {
            return customVolumeM3;
        }
        return area * length;
    }

    public void setCustomVolumeM3(double customVolumeM3) {
        this.customVolumeM3 = customVolumeM3;
    }

    /**
     * Volume in Liters (1 m^3 = 1000 L).
     */
    public double getVolumeLiters() {
        return getVolume() * 1000.0;
    }

    private long capacityLiters = 0L;
    private int blockX = Integer.MIN_VALUE;
    private int blockY = Integer.MIN_VALUE;
    private int blockZ = Integer.MIN_VALUE;

    public void setPosition(int x, int y, int z) {
        this.blockX = x;
        this.blockY = y;
        this.blockZ = z;
    }

    public void setPosition(ChunkCoordinates coords) {
        if (coords != null) {
            setPosition(coords.posX, coords.posY, coords.posZ);
        } else {
            this.blockX = Integer.MIN_VALUE;
            this.blockY = Integer.MIN_VALUE;
            this.blockZ = Integer.MIN_VALUE;
        }
    }

    public boolean hasPosition() {
        return blockX != Integer.MIN_VALUE;
    }

    public int getBlockX() {
        if (!hasPosition()) return 0;
        return blockX;
    }

    public int getBlockY() {
        if (!hasPosition()) return 0;
        return blockY;
    }

    public int getBlockZ() {
        if (!hasPosition()) return 0;
        return blockZ;
    }

    public ChunkCoordinates getCoordinates() {
        if (!hasPosition()) return null;
        return new ChunkCoordinates(blockX, blockY, blockZ);
    }

    public long getCapacityLiters() {
        if (capacityLiters > 0) {
            return capacityLiters;
        }
        return Math.max(1L, (long) Math.ceil(getVolumeLiters()));
    }

    public void setCapacityLiters(long capacityLiters) {
        this.capacityLiters = capacityLiters;
    }

    public double getRoughness() {
        return roughness;
    }

    public double getMinorLossK() {
        return minorLossK;
    }

    public void setMinorLossK(double minorLossK) {
        this.minorLossK = Math.max(0.0, minorLossK);
    }

    public double getMaxPressureBar() {
        return maxPressureBar;
    }

    public double getMaxTemperatureCelsius() {
        return maxTemperatureCelsius;
    }

    public double getCurrentTemperatureCelsius() {
        return currentTemperatureCelsius;
    }

    public void setCurrentTemperatureCelsius(double temp) {
        this.currentTemperatureCelsius = temp;
    }

    public double getFlowFraction() {
        return flowFraction;
    }

    public void setFlowFraction(double flowFraction) {
        this.flowFraction = Math.max(0.0, flowFraction);
    }

    private double flowSizeWeight = 1.0;

    public double getFlowSizeWeight() {
        return flowSizeWeight;
    }

    public void setFlowSizeWeight(double weight) {
        this.flowSizeWeight = Math.max(0.001, weight);
    }

    public List<LoopSegment> getUpstreamSegments() {
        return upstreamSegments;
    }

    public void addUpstreamSegment(LoopSegment seg) {
        if (seg != null && !upstreamSegments.contains(seg)) {
            upstreamSegments.add(seg);
        }
    }

    public void clearUpstreamSegments() {
        upstreamSegments.clear();
    }

    private final Map<String, Long> dissolvedGases = new LinkedHashMap<>();
    private double fallbackGasFraction = 0.0;

    public Map<String, Long> getDissolvedGases() {
        return Collections.unmodifiableMap(dissolvedGases);
    }

    public long getDissolvedGasAmount(String gasName) {
        if (gasName == null) return 0L;
        for (Map.Entry<String, Long> entry : dissolvedGases.entrySet()) {
            if (entry.getKey()
                .equalsIgnoreCase(gasName)) {
                return entry.getValue();
            }
        }
        return 0L;
    }

    public long getTotalDissolvedGasLiters() {
        long sum = 0L;
        for (long v : dissolvedGases.values()) {
            sum += v;
        }
        return sum;
    }

    public void addDissolvedGas(String gasName, long liters) {
        if (gasName == null || liters <= 0L) return;
        dissolvedGases.merge(gasName, liters, Long::sum);
    }

    public long extractDissolvedGas(String gasName, long maxLiters) {
        if (gasName == null || maxLiters <= 0L) return 0L;
        String matchedKey = null;
        for (String k : dissolvedGases.keySet()) {
            if (k.equalsIgnoreCase(gasName)) {
                matchedKey = k;
                break;
            }
        }
        if (matchedKey == null) return 0L;
        long current = dissolvedGases.getOrDefault(matchedKey, 0L);
        long toExtract = Math.min(current, maxLiters);
        if (toExtract > 0L) {
            long remaining = current - toExtract;
            if (remaining > 0L) {
                dissolvedGases.put(matchedKey, remaining);
            } else {
                dissolvedGases.remove(matchedKey);
            }
        }
        return toExtract;
    }

    public void setDissolvedGases(Map<String, Long> gases, long totalLoopLiters) {
        if (gases == null || gases.isEmpty() || totalLoopLiters <= 0L) {
            if (!this.dissolvedGases.isEmpty()) {
                this.dissolvedGases.clear();
            }
            return;
        }
        this.dissolvedGases.clear();
        long cap = getCapacityLiters();
        double ratio = (double) cap / (double) totalLoopLiters;
        for (Map.Entry<String, Long> entry : gases.entrySet()) {
            if (entry.getValue() != null && entry.getValue() > 0L) {
                long segLiters = Math.round(entry.getValue() * ratio);
                if (segLiters > 0L) {
                    this.dissolvedGases.put(entry.getKey(), segLiters);
                }
            }
        }
    }

    public void setDissolvedGasesDirect(Map<String, Long> gases) {
        this.dissolvedGases.clear();
        if (gases != null) {
            for (Map.Entry<String, Long> entry : gases.entrySet()) {
                if (entry.getValue() != null && entry.getValue() > 0L) {
                    this.dissolvedGases.put(entry.getKey(), entry.getValue());
                }
            }
        }
    }

    /**
     * Converts absolute dissolved gas amounts into a volume fraction (0.0 to 1.0)
     * for display purposes and cavitation damage calculations.
     */
    public double getDissolvedGasFraction() {
        long totalGas = getTotalDissolvedGasLiters();
        if (totalGas > 0L) {
            long cap = getCapacityLiters();
            if (cap > 0L) {
                return Math.min(1.0, (double) totalGas / (double) cap);
            }
        }
        return fallbackGasFraction;
    }

    /**
     * Converts volume fraction into percentage (0.0% to 100.0%) for display purposes.
     */
    public double getDissolvedGasPercentage() {
        return getDissolvedGasFraction() * 100.0;
    }

    public void setDissolvedGasFraction(double dissolvedGasFraction) {
        this.fallbackGasFraction = dissolvedGasFraction;
    }

    /**
     * Computes the Darcy friction factor f using the continuous Churchill correlation,
     * which smoothly handles laminar, transition, and fully rough turbulent flow.
     *
     * @param reynolds Reynolds number Re = rho * v * D / mu
     * @return Darcy friction factor f
     */
    public double computeDarcyFrictionFactor(double reynolds) {
        if (reynolds < 1.0) {
            return 64.0; // Clamped creeping flow
        }
        if (reynolds < 2100.0) {
            return 64.0 / reynolds; // Exact Hagen-Poiseuille laminar
        }

        // Churchill correlation for transitional and turbulent regimes:
        double relRoughness = roughness / diameter;
        double a = Math.pow(-2.457 * Math.log(Math.pow(7.0 / reynolds, 0.9) + 0.27 * relRoughness), 16.0);
        double b = Math.pow(37530.0 / reynolds, 16.0);
        return 8.0 * Math.pow(Math.pow(8.0 / reynolds, 12.0) + 1.0 / Math.pow(a + b, 1.5), 1.0 / 12.0);
    }

    /**
     * Computes total head loss delta_P (Pascals) across this segment for a given flow rate Q.
     *
     * @param volumetricFlowRate Volumetric flow rate Q in m^3/s
     * @param fluid              Fluid properties
     * @return Pressure drop delta_P in Pascals
     */
    public double computePressureDrop(double volumetricFlowRate, CoolantFluidProperty fluid) {
        if (Math.abs(volumetricFlowRate) <= 1e-9) {
            return 0.0;
        }
        double velocity = Math.abs(volumetricFlowRate) / area;
        double reynolds = (fluid.getDensity() * velocity * diameter) / fluid.getDynamicViscosity();
        double f = computeDarcyFrictionFactor(reynolds);

        // Major Darcy friction head loss: Delta P_major = f * (L / D) * (rho * v^2 / 2)
        double deltaPMajor = f * (length / diameter) * (fluid.getDensity() * velocity * velocity / 2.0);

        // Minor loss: Delta P_minor = K * (rho * v^2 / 2)
        double deltaPMinor = minorLossK * (fluid.getDensity() * velocity * velocity / 2.0);

        return deltaPMajor + deltaPMinor;
    }

    /**
     * Computes the Reynolds number Re = (rho * v * D) / mu for a given volumetric flow rate.
     *
     * @param volumetricFlowRate Volumetric flow rate Q in m^3/s
     * @param fluid              Fluid properties
     * @return Reynolds number (dimensionless)
     */
    public double computeReynoldsNumber(double volumetricFlowRate, CoolantFluidProperty fluid) {
        if (Math.abs(volumetricFlowRate) <= 1e-9 || fluid == null || area <= 0.0) {
            return 0.0;
        }
        double velocity = Math.abs(volumetricFlowRate) / area;
        return (fluid.getDensity() * velocity * diameter) / fluid.getDynamicViscosity();
    }
}
