package com.gtnewhorizons.coolantloops.engine;

/**
 * Calculates convective heat transfer coefficient h(v) in W/(m^2 * K)
 * as a function of local coolant flow velocity v (m/s).
 *
 * Implements the narrative specification:
 * - Effectively zero heat transfer at zero flow.
 * - Linear scaling in laminar regime up to critical velocity v_turb.
 * - Steep power-law increase during turbulent transition.
 * - Smooth asymptotic saturation at high velocity.
 */
public class ConvectiveHeatTransferModel {

    private final double vTurbulent; // Velocity at which turbulence begins (m/s), e.g. 0.5 m/s
    private final double vSaturation; // Velocity at which boundary layer saturation begins (m/s), e.g. 3.0 m/s
    private final double hLaminarSlope; // Slope in laminar regime W/(m^2*K) / (m/s)
    private final double hTurbulentFactor; // Scaling factor for turbulent jump
    private final double hMax; // Maximum asymptotic saturation coefficient W/(m^2*K)

    public static final ConvectiveHeatTransferModel DEFAULT = new ConvectiveHeatTransferModel(
        0.5, // vTurbulent
        3.0, // vSaturation
        120.0, // hLaminarSlope -> at v=0.5, h = 60 W/m^2*K
        1500.0, // hTurbulentFactor
        5000.0 // hMax W/m^2*K
    );

    public ConvectiveHeatTransferModel(double vTurbulent, double vSaturation, double hLaminarSlope,
        double hTurbulentFactor, double hMax) {
        if (vTurbulent <= 0 || vSaturation <= vTurbulent) {
            throw new IllegalArgumentException("vTurbulent must be positive and less than vSaturation");
        }
        this.vTurbulent = vTurbulent;
        this.vSaturation = vSaturation;
        this.hLaminarSlope = hLaminarSlope;
        this.hTurbulentFactor = hTurbulentFactor;
        this.hMax = hMax;
    }

    /**
     * Computes local convective heat transfer coefficient h in W/(m^2 * K).
     *
     * @param velocity Coolant flow velocity in m/s (must be non-negative)
     * @return h in W/(m^2 * K)
     */
    public double computeH(double velocity) {
        if (velocity <= 1e-6) {
            return 0.0;
        }

        // 1. Laminar regime (0 < v < vTurbulent)
        if (velocity < vTurbulent) {
            return hLaminarSlope * velocity;
        }

        double hAtTurb = hLaminarSlope * vTurbulent;

        // 2. Turbulent transition regime (vTurbulent <= v < vSaturation)
        if (velocity < vSaturation) {
            double normalizedV = (velocity - vTurbulent) / (vSaturation - vTurbulent);
            // Non-linear power-law growth (1.5 exponent for turbulent vortex mixing)
            return hAtTurb + hTurbulentFactor * Math.pow(normalizedV, 1.5);
        }

        // 3. Boundary layer saturation regime (v >= vSaturation)
        double hAtSat = hAtTurb + hTurbulentFactor;
        double remainingHeadroom = hMax - hAtSat;
        if (remainingHeadroom <= 0) {
            return hMax;
        }
        double decayRate = 0.5; // per (m/s)
        return hMax - remainingHeadroom * Math.exp(-decayRate * (velocity - vSaturation));
    }

    /**
     * Computes heat transfer rate Q_dot in Watts (J/s) according to Newton's law of cooling:
     * Q_dot = h(v) * A * (T_device - T_coolant)
     *
     * @param velocity    Coolant velocity in m/s
     * @param surfaceArea Effective heat transfer surface area in m^2
     * @param deviceTemp  Temperature of device in deg C or K
     * @param coolantTemp Temperature of coolant entering device in deg C or K
     * @return Heat transfer rate in Watts (positive means heat enters coolant; negative means heat leaves coolant)
     */
    public double computeHeatTransferRate(double velocity, double surfaceArea, double deviceTemp, double coolantTemp) {
        double h = computeH(velocity);
        return h * surfaceArea * (deviceTemp - coolantTemp);
    }

    public double getVTurbulent() {
        return vTurbulent;
    }

    public double getVSaturation() {
        return vSaturation;
    }

    public double getHMax() {
        return hMax;
    }
}
