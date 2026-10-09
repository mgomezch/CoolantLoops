package com.gtnewhorizons.coolantloops.engine;

/**
 * Interface implemented by in-line blocks and MetaTileEntities that interface
 * with a pressurized coolant loop (Pumps, Exchangers, Reservoirs, Manifolds, Instruments).
 */
public interface ICoolantLoopDevice {

    /**
     * Unique identifier or coordinate label of this device.
     */
    String getDeviceId();

    /**
     * Minor loss coefficient K contributed by fluid flowing through this device.
     */
    double getMinorLossK();

    /**
     * Operating temperature of the device in deg C.
     */
    double getDeviceTemperatureCelsius();

    /**
     * Performs thermal energy exchange between the device and the fluid passing through it.
     *
     * @param coolantFlowRateM3s Volumetric flow rate Q in m^3/s
     * @param dt                 Time step in seconds
     * @param fluid              Fluid thermodynamic properties
     * @param segment            Local loop segment containing fluid entering this device
     */
    void processThermalExchange(double coolantFlowRateM3s, double dt, CoolantFluidProperty fluid, LoopSegment segment);

    /**
     * Associates the managing coolant loop pump with this device.
     */
    default void setLoopPump(ICoolantLoopPump pump) {}
}
