package com.gtnewhorizons.coolantloops.engine;

import java.util.Map;

/**
 * Interface representing the pump controller managing a closed coolant loop.
 * Tracks loop circulation, fluid inventory in the attached reservoir, and dissolved radiolytic gases.
 */
public interface ICoolantLoopPump {

    /**
     * Consumes / drains coolant from the reservoir due to transmutation or phase change.
     *
     * @param liters Number of liters of liquid coolant consumed
     */
    void consumeCoolant(long liters);

    /**
     * Adds dissolved byproduct gas (e.g. Tritium, Oxygen, Deuterium) to the loop state.
     *
     * @param gasName Name of the gas species (e.g. "Tritium", "Oxygen")
     * @param liters  Amount of gas generated in liters
     */
    void addDissolvedGas(String gasName, long liters);

    /**
     * Gets the volume fraction of dissolved gases relative to total coolant volume (0.0 to 1.0).
     */
    double getDissolvedGasFraction();

    /**
     * Gets the accumulated liters of a specific dissolved gas species.
     */
    long getDissolvedGasAmount(String gasName);

    /**
     * Gets an unmodifiable map of all dissolved gas species and their accumulated liters.
     */
    Map<String, Long> getDissolvedGases();

    /**
     * Gets the total remaining coolant in liters in the attached reservoir.
     */
    long getTotalCoolantLiters();

    /**
     * Extracts / removes up to maxLiters of a specific dissolved gas species from the loop.
     *
     * @param gasName   Name of the gas species (e.g. "Deuterium", "Tritium", "Oxygen")
     * @param maxLiters Maximum liters to extract
     * @return Number of liters actually extracted
     */
    long extractDissolvedGas(String gasName, long maxLiters);

    /**
     * Gets the current circulating coolant volume in the loop (in liters).
     */
    long getCurrentFillLiters();

    /**
     * Gets the thermodynamic and transport property of the circulating coolant fluid.
     */
    CoolantFluidProperty getCoolantFluidProperty();

    /**
     * Gets the material of the Railcraft tank multiblock sitting above the pump,
     * which must match all pipes in the coolant loop circuit.
     */
    default gregtech.api.enums.Materials getTankMaterial() {
        return null;
    }

    /**
     * Gets the required coolant fill volume in liters needed to completely fill all pipes and devices.
     */
    default long getRequiredFillLiters() {
        return 0L;
    }

    /**
     * Gets the current loop fill fraction (0.0 to 1.0).
     */
    default double getFillFraction() {
        return getRequiredFillLiters() > 0 ? (double) getCurrentFillLiters() / getRequiredFillLiters() : 1.0;
    }

    /**
     * Gets the human-readable status message of the loop and pump.
     */
    default String getLoopStatus() {
        return "";
    }

    /**
     * Gets the current operating state name (e.g. "CIRCULATING", "FILLING", "STOPPED").
     */
    default String getLoopStateName() {
        return "STOPPED";
    }

    /**
     * Returns true if a closed loop circuit is formed between discharge and suction hatches.
     */
    default boolean isLoopFormed() {
        return false;
    }

    /**
     * Returns true if the discharge hatch is disabled via machine controller cover.
     */
    default boolean isDischargeHatchDisabled() {
        return false;
    }

    /**
     * Gets the maximum allowable flow rate in liters per second capped by pipe capacity.
     */
    default double getMaxFlowRateLitersPerSecond() {
        return 0.0;
    }

    /**
     * Gets the peak pressure experienced across the loop in bar.
     */
    default double getPeakLoopPressureBar() {
        return 1.0;
    }

    /**
     * Gets the peak temperature across all segments in the loop in deg C.
     */
    default double getPeakLoopTempCelsius() {
        return 20.0;
    }

    /**
     * Returns true if a valid turbine rotor impeller is installed.
     */
    default boolean hasRotor() {
        return false;
    }

    /**
     * Gets the base mechanical efficiency of the impeller (0.0 to 2.0).
     */
    default double getRotorEfficiency() {
        return 0.0;
    }

    /**
     * Gets current durability damage of the impeller.
     */
    default int getRotorDamage() {
        return 0;
    }

    /**
     * Gets maximum durability damage of the impeller.
     */
    default int getRotorMaxDamage() {
        return 0;
    }

    /**
     * Gets the accumulated fractional wear on the impeller.
     */
    default double getRotorDamageAccumulator() {
        return 0.0;
    }

    /**
     * Gets the remaining durability percentage of the impeller (100.0% to 0.0%).
     */
    default double getRotorDurabilityPercent() {
        return 100.0;
    }

    /**
     * Gets the active electrical power consumption drawn by the pump in EU/t.
     */
    default long getPowerConsumptionEU() {
        return 0L;
    }

    /**
     * Gets the total fluid capacity in liters of the attached reservoir tank.
     */
    default long getReservoirCapacityLiters() {
        return 0L;
    }

    /**
     * Gets the primary material of the turbine rotor impeller.
     */
    default gregtech.api.enums.Materials getRotorMaterial() {
        return null;
    }

    /**
     * Gets the fluid temperature at the pump suction in degrees Celsius.
     */
    default double getPumpFluidTemperatureCelsius() {
        return 20.0;
    }

    /**
     * Gets the homologous temperature Th = T_fluid,K / T_melt,K of the impeller.
     */
    default double getHomologousTemperature() {
        return 0.0;
    }

    /**
     * Gets the thermal softening / creep wear multiplier k_temp on the impeller.
     */
    default double getThermalWearMultiplier() {
        return 1.0;
    }

    /**
     * Gets the effective melting point of the rotor material in degrees Celsius.
     */
    default double getRotorMeltingPointCelsius() {
        return 726.85;
    }

    /**
     * Gets the temperature at which thermal softening begins (0.5 * T_melt in Kelvin) in degrees Celsius.
     */
    default double getRotorSofteningCelsius() {
        return 226.85;
    }
}
