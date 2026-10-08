package com.gtnewhorizons.coolantloops.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 1D Closed-Loop Hydrodynamic and Thermodynamic Simulation Engine.
 *
 * Implements:
 * 1. Hydraulic Inertia: I * dQ/dt = Delta P_pump - Delta P_friction
 * where I = sum(rho * L_i / A_i) is the total acoustic/inertial fluid impedance.
 * 2. Viscous and Minor Form Drag: Darcy-Weisbach loss per segment + unpowered impeller braking.
 * 3. Pressure Field: Computes local pressure along the loop from pump discharge to suction.
 * 4. 1D Advective Thermal Transport: Advects heat between adjacent segments with Newton's cooling law.
 * 5. Rupture and Thermal Failure: Evaluates hoop stress and max operating temperatures.
 */
public class CoolantLoopEngine {

    private CoolantFluidProperty fluid;
    private final List<LoopSegment> segments = new ArrayList<>();

    // State variables
    private double volumetricFlowRate = 0.0; // m^3/s
    private double maxVolumetricFlowRate = Double.MAX_VALUE; // m^3/s (capped by pump connected pipe capacity)
    private double pumpMechanicalPowerWatts = 0.0; // Mechanical power output from motor/rotor (Watts)
    private boolean isPumpPowered = false;
    private double unpoweredImpellerK = 8.0; // High minor loss coefficient when motor is disengaged
    private double maxPumpHeadPressurePa = 25.0 * 1e5; // Maximum stall head (e.g. 25 bar)

    // Simulation status
    private boolean isRuptured = false;
    private String failureReason = null;
    private double peakLoopPressureBar = 0.0;
    private double peakLoopTempCelsius = 20.0;
    private double dissolvedGasFraction = 0.0;
    private boolean isBraking = false;

    public boolean isBraking() {
        return isBraking;
    }

    public void setBraking(boolean braking) {
        this.isBraking = braking;
    }

    public double getDissolvedGasFraction() {
        return dissolvedGasFraction;
    }

    public void setDissolvedGasFraction(double dissolvedGasFraction) {
        this.dissolvedGasFraction = Math.max(0.0, Math.min(1.0, dissolvedGasFraction));
    }

    public CoolantLoopEngine(CoolantFluidProperty fluid) {
        this.fluid = fluid != null ? fluid : CoolantFluidProperty.WATER;
    }

    public void addSegment(LoopSegment segment) {
        if (segment == null) {
            throw new IllegalArgumentException("Segment cannot be null");
        }
        segments.add(segment);
    }

    public void clearSegments() {
        segments.clear();
        volumetricFlowRate = 0.0;
        maxVolumetricFlowRate = Double.MAX_VALUE;
        isRuptured = false;
        failureReason = null;
    }

    public List<LoopSegment> getSegments() {
        return Collections.unmodifiableList(segments);
    }

    public CoolantFluidProperty getFluid() {
        return fluid;
    }

    public void setFluid(CoolantFluidProperty fluid) {
        if (fluid != null) {
            this.fluid = fluid;
        }
    }

    public double getVolumetricFlowRate() {
        return volumetricFlowRate;
    }

    /**
     * Volumetric flow rate in Liters per second (1 m^3/s = 1000 L/s).
     */
    public double getFlowRateLitersPerSecond() {
        return volumetricFlowRate * 1000.0;
    }

    /**
     * Volumetric flow rate in Liters per Minecraft tick (20 ticks/sec).
     */
    public double getFlowRateLitersPerTick() {
        return getFlowRateLitersPerSecond() / 20.0;
    }

    public double getMaxVolumetricFlowRate() {
        return maxVolumetricFlowRate;
    }

    public void setMaxVolumetricFlowRate(double maxFlowRate) {
        this.maxVolumetricFlowRate = Math.max(0.0, maxFlowRate);
        if (this.volumetricFlowRate > this.maxVolumetricFlowRate) {
            this.volumetricFlowRate = this.maxVolumetricFlowRate;
        }
    }

    public double getMaxFlowRateLitersPerSecond() {
        return maxVolumetricFlowRate == Double.MAX_VALUE ? Double.MAX_VALUE : maxVolumetricFlowRate * 1000.0;
    }

    public double getMaxFlowRateLitersPerTick() {
        return getMaxFlowRateLitersPerSecond() / 20.0;
    }

    public void setVolumetricFlowRate(double q) {
        this.volumetricFlowRate = Math.min(maxVolumetricFlowRate, Math.max(0.0, q));
    }

    public double getPumpMechanicalPowerWatts() {
        return pumpMechanicalPowerWatts;
    }

    public void setPumpMechanicalPowerWatts(double power) {
        this.pumpMechanicalPowerWatts = Math.max(0.0, power);
    }

    public boolean isPumpPowered() {
        return isPumpPowered;
    }

    public void setPumpPowered(boolean powered) {
        this.isPumpPowered = powered;
    }

    public boolean isRuptured() {
        return isRuptured;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public double getPeakLoopPressureBar() {
        return peakLoopPressureBar;
    }

    public double getPeakLoopTempCelsius() {
        return peakLoopTempCelsius;
    }

    /**
     * Total fluid mass contained in all loop segments in kg.
     */
    public double getTotalFluidMassKg() {
        double totalVolume = 0.0;
        for (LoopSegment s : segments) {
            totalVolume += s.getVolume();
        }
        return totalVolume * fluid.getDensity();
    }

    /**
     * Total hydraulic inertia impedance I = sum(rho * L_i / A_i) in kg / m^4.
     */
    public double computeHydraulicInertia() {
        double inertia = 0.0;
        double rho = fluid.getDensity();
        for (LoopSegment s : segments) {
            inertia += rho * s.getLength() / s.getArea();
        }
        return Math.max(inertia, 1.0); // Prevent division by zero
    }

    /**
     * Advances the simulation by dt seconds (typically dt = 0.05s for 1 tick).
     *
     * @param dt Time step in seconds
     */
    public void step(double dt) {
        if (isRuptured || segments.isEmpty()) {
            return;
        }

        // 1. Compute driving pump pressure: Delta P_pump
        double deltaPPump = 0.0;
        if (isPumpPowered && pumpMechanicalPowerWatts > 0) {
            // Characteristic pump curve: Delta P = P_mech / (Q + Q_rated)
            // Models electric motor stall torque limits and impeller slip at zero/low flow
            double qRated = Math.max(0.005, 0.02 * Math.sqrt(pumpMechanicalPowerWatts / 5000.0));
            double pHead = Math.min(pumpMechanicalPowerWatts / (volumetricFlowRate + qRated), maxPumpHeadPressurePa);
            if (isBraking) {
                // Active motor braking: pump applies mechanical power against fluid momentum
                deltaPPump = -pHead;
            } else {
                deltaPPump = pHead;
            }
        }

        // 2. Compute total circuit friction pressure drop: Delta P_friction(Q)
        double deltaPFriction = 0.0;
        for (LoopSegment segment : segments) {
            deltaPFriction += segment.computePressureDrop(volumetricFlowRate, fluid);
        }

        // Add unpowered impeller drag if the pump is switched off while fluid is moving
        if (!isPumpPowered && volumetricFlowRate > 1e-6) {
            // Reference velocity at first segment
            double refArea = segments.get(0)
                .getArea();
            double vRef = volumetricFlowRate / refArea;
            deltaPFriction += unpoweredImpellerK * (fluid.getDensity() * vRef * vRef / 2.0);
        }

        // 3. Momentum integration using semi-implicit damping:
        // I * dQ/dt = Delta P_pump - Delta P_friction
        double inertia = computeHydraulicInertia();
        double netDeltaP = deltaPPump - deltaPFriction;

        // Derivative of friction with respect to Q (tangent damping)
        double frictionDerivative = (volumetricFlowRate > 1e-6) ? (2.0 * deltaPFriction / volumetricFlowRate) : 0.0;
        double denominator = inertia + frictionDerivative * dt;
        double deltaQ = (netDeltaP * dt) / denominator;

        volumetricFlowRate = Math.min(maxVolumetricFlowRate, Math.max(0.0, volumetricFlowRate + deltaQ));

        // 4. Pressure distribution and safety verification
        evaluatePressureFieldAndSafety(deltaPPump);

        // 5. Thermal advection step
        advectTemperature(dt);
    }

    /**
     * Evaluates pressure gradient along segments and checks for hoop stress bursting.
     */
    private void evaluatePressureFieldAndSafety(double pumpDischargeHeadPa) {
        // Base suction pressure (atmospheric / tank head) ~ 1.0 bar (1e5 Pa)
        double currentPressurePa = 1.0e5 + Math.max(0.0, pumpDischargeHeadPa);
        peakLoopPressureBar = currentPressurePa / 1e5;

        for (LoopSegment segment : segments) {
            double segDrop = segment.computePressureDrop(volumetricFlowRate, fluid);
            currentPressurePa = Math.max(1.0e5, currentPressurePa - segDrop);

            double pressureBar = currentPressurePa / 1e5;
            if (pressureBar > peakLoopPressureBar) {
                peakLoopPressureBar = pressureBar;
            }

            // Burst limit check
            if (pressureBar > segment.getMaxPressureBar()) {
                isRuptured = true;
                failureReason = String.format(
                    "Pipe segment %s burst! Pressure %.2f bar exceeded maximum rating %.2f bar",
                    segment.getId(),
                    pressureBar,
                    segment.getMaxPressureBar());
                return;
            }

            // Thermal limit check
            if (segment.getCurrentTemperatureCelsius() > segment.getMaxTemperatureCelsius()) {
                isRuptured = true;
                failureReason = String.format(
                    "Pipe segment %s melted! Temperature %.1f C exceeded maximum rating %.1f C",
                    segment.getId(),
                    segment.getCurrentTemperatureCelsius(),
                    segment.getMaxTemperatureCelsius());
                return;
            }
        }
    }

    /**
     * 1D finite-volume advective heat transfer step.
     */
    private void advectTemperature(double dt) {
        if (volumetricFlowRate <= 1e-7 || segments.size() < 2) {
            return;
        }

        double sweptVolume = volumetricFlowRate * dt;
        double[] newTemps = new double[segments.size()];
        double maxTemp = -Double.MAX_VALUE;

        for (int i = 0; i < segments.size(); i++) {
            LoopSegment curr = segments.get(i);
            LoopSegment prev = (i == 0) ? segments.get(segments.size() - 1) : segments.get(i - 1);

            double segVolume = curr.getVolume();
            // Fraction of segment volume replaced by fluid from upstream segment
            double replaceFraction = Math.min(1.0, sweptVolume / segVolume);

            double blendedTemp = curr.getCurrentTemperatureCelsius() * (1.0 - replaceFraction)
                + prev.getCurrentTemperatureCelsius() * replaceFraction;

            newTemps[i] = blendedTemp;
            if (blendedTemp > maxTemp) {
                maxTemp = blendedTemp;
            }
        }

        for (int i = 0; i < segments.size(); i++) {
            segments.get(i)
                .setCurrentTemperatureCelsius(newTemps[i]);
        }
        peakLoopTempCelsius = maxTemp;
    }
}
