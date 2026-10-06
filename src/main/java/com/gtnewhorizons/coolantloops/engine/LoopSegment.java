package com.gtnewhorizons.coolantloops.engine;

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
    private final double minorLossK; // Loss coefficient K (dimensionless)
    private final double maxPressureBar; // Bursting limit in bar
    private final double maxTemperatureCelsius;// Maximum safe operating temperature in deg C

    private double currentTemperatureCelsius; // Current temperature of coolant in this segment

    public LoopSegment(String id, double length, double diameter, double roughness, double minorLossK,
        double maxPressureBar, double maxTemperatureCelsius, double initialTempCelsius) {
        if (length <= 0 || diameter <= 0) {
            throw new IllegalArgumentException("Length and diameter must be positive");
        }
        this.id = id;
        this.length = length;
        this.diameter = diameter;
        this.area = Math.PI * Math.pow(diameter / 2.0, 2);
        this.roughness = roughness;
        this.minorLossK = minorLossK;
        this.maxPressureBar = maxPressureBar;
        this.maxTemperatureCelsius = maxTemperatureCelsius;
        this.currentTemperatureCelsius = initialTempCelsius;
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

    /**
     * Volume of fluid contained within this segment in m^3.
     */
    public double getVolume() {
        return area * length;
    }

    /**
     * Volume in Liters (1 m^3 = 1000 L).
     */
    public double getVolumeLiters() {
        return getVolume() * 1000.0;
    }

    public double getRoughness() {
        return roughness;
    }

    public double getMinorLossK() {
        return minorLossK;
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
}
