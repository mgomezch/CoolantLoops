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
}
