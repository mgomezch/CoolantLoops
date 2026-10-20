package com.gtnewhorizons.coolantloops.common.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import com.gtnewhorizons.coolantloops.common.config.data.CoolantDeviceData;
import com.gtnewhorizons.coolantloops.common.config.data.CoolantFluidData;
import com.gtnewhorizons.coolantloops.common.config.data.CoolantPhysicsData;
import com.gtnewhorizons.coolantloops.common.config.data.CoolantPipeData;
import com.gtnewhorizons.coolantloops.engine.ConvectiveHeatTransferModel;
import com.gtnewhorizons.coolantloops.engine.CoolantFluidProperty;

/**
 * Central data registry for CoolantLoops, providing structured YAML-backed
 * configuration of hydrodynamic constants, fluid thermodynamic properties,
 * piping limits, and multiblock device parameters.
 */
public class CoolantLoopDataRegistry {

    private static final Logger LOG = LogManager.getLogger("CoolantLoopsDataRegistry");

    private static CoolantPhysicsData physicsData = new CoolantPhysicsData();
    private static CoolantFluidData fluidData = new CoolantFluidData();
    private static CoolantPipeData pipeData = new CoolantPipeData();
    private static CoolantDeviceData deviceData = new CoolantDeviceData();

    private static ConvectiveHeatTransferModel convectiveModel = ConvectiveHeatTransferModel.DEFAULT;

    private static File configFolder;
    private static boolean initialized = false;

    public static synchronized void init(File configDirectory) {
        configFolder = new File(configDirectory, "coolantloops");
        if (!configFolder.exists()) {
            configFolder.mkdirs();
        }

        loadPhysics();
        loadFluids();
        loadPipes();
        loadDevices();

        // Instantiate convective model from configured physics
        convectiveModel = new ConvectiveHeatTransferModel(
            physicsData.getVTurbulent(),
            physicsData.getVSaturation(),
            physicsData.getHLaminarSlope(),
            physicsData.getHTurbulentFactor(),
            physicsData.getHMax());

        // Sync loaded fluids into CoolantFluidProperty runtime registry
        applyFluidRegistry();

        initialized = true;
        LOG.info("CoolantLoops YAML Data Registry initialized successfully.");
    }

    private static Yaml createYaml() {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setIndent(2);
        return new Yaml(options);
    }

    private static void loadPhysics() {
        File file = new File(configFolder, "physics.yaml");
        if (!file.exists()) {
            writeDefaultPhysics(file);
        }
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            Yaml yaml = createYaml();
            Map<String, Object> map = yaml.load(reader);
            if (map != null) {
                physicsData = CoolantPhysicsData.fromMap(map);
            }
        } catch (Exception e) {
            LOG.error("Failed to load physics.yaml, keeping defaults", e);
        }
    }

    private static void writeDefaultPhysics(File file) {
        String yaml = "# ==========================================================================\n"
            + "# Coolant Loops: Hydrodynamic & Convective Physics Configuration\n"
            + "# ==========================================================================\n"
            + "\n"
            + "# Velocity at which turbulence begins in m/s (default 0.5 m/s)\n"
            + "vTurbulent: 0.5\n"
            + "\n"
            + "# Velocity at which boundary layer saturation begins in m/s (default 3.0 m/s)\n"
            + "vSaturation: 3.0\n"
            + "\n"
            + "# Convection slope in laminar regime in W/(m^2*K) / (m/s) (default 120.0)\n"
            + "hLaminarSlope: 120.0\n"
            + "\n"
            + "# Scaling factor for turbulent jump in W/(m^2*K) (default 1500.0)\n"
            + "hTurbulentFactor: 1500.0\n"
            + "\n"
            + "# Maximum asymptotic saturation coefficient in W/(m^2*K) (default 5000.0)\n"
            + "hMax: 5000.0\n"
            + "\n"
            + "# Minor loss drag coefficient for unpowered impeller coast-down (default 8.0)\n"
            + "unpoweredImpellerK: 8.0\n"
            + "\n"
            + "# Maximum stall head pressure in Pascals (default .inf / uncapped; physically governed by motor power)\n"
            + "maxPumpHeadPressurePa: .inf\n"
            + "\n"
            + "# Mechanical power output (Watts) delivered per EU drawn by pump (default 80.0)\n"
            + "pumpMechanicalPowerPerEU: 80.0\n"
            + "\n"
            + "# Primary loop fill rate from Railcraft tank in L/tick (default 1000)\n"
            + "fillRateLitersPerTick: 1000\n"
            + "\n"
            + "# Fallback ambient temperature in degrees Celsius (default 20.0)\n"
            + "defaultAmbientTemp: 20.0\n"
            + "\n"
            + "# Standard reactor passage length in meters (default 5.0)\n"
            + "standardPassageLength: 5.0\n"
            + "\n"
            + "# Standard reactor passage inner diameter in meters (default 0.10)\n"
            + "standardPassageDiameter: 0.10\n"
            + "\n"
            + "# Commercial metal pipe standard roughness in meters (default 0.000045)\n"
            + "defaultPipeRoughness: 0.000045\n";

        writeFile(file, yaml);
    }

    private static void loadFluids() {
        File file = new File(configFolder, "fluids.yaml");
        if (!file.exists()) {
            File alt = new File(configFolder, "coolants.yaml");
            if (alt.exists()) {
                file = alt;
            } else {
                writeDefaultFluids(file);
            }
        }
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            Yaml yaml = createYaml();
            Map<String, Object> map = yaml.load(reader);
            if (map != null) {
                fluidData = CoolantFluidData.fromMap(map);
            }
        } catch (Exception e) {
            LOG.error("Failed to load fluids.yaml, keeping defaults", e);
        }
    }

    private static void writeDefaultFluids(File file) {
        String yaml = "# ==========================================================================\n"
            + "# Coolant Loops: Thermodynamic Fluid Properties\n"
            + "# All units are standard SI (kg/m^3, Pa*s, J/(kg*K), W/(m*K), deg C)\n"
            + "# ==========================================================================\n"
            + "\n"
            + "fluids:\n"
            + "  water:\n"
            + "    fluidName: \"water\"\n"
            + "    density: 1000.0\n"
            + "    dynamicViscosity: 0.001\n"
            + "    specificHeat: 4184.0\n"
            + "    thermalConductivity: 0.606\n"
            + "    boilingPointCelsius: 100.0\n"
            + "    freezingPointCelsius: 0.0\n"
            + "    isMolten: false\n"
            + "    radiolysisMultiplier: 1.0\n"
            + "    spillOnRupture: true\n"
            + "    aliases:\n"
            + "      - \"minecraft:water\"\n"
            + "\n"
            + "  ic2distilledwater:\n"
            + "    fluidName: \"ic2distilledwater\"\n"
            + "    density: 998.0\n"
            + "    dynamicViscosity: 0.00095\n"
            + "    specificHeat: 4186.0\n"
            + "    thermalConductivity: 0.610\n"
            + "    boilingPointCelsius: 100.0\n"
            + "    freezingPointCelsius: 0.0\n"
            + "    isMolten: false\n"
            + "    radiolysisMultiplier: 1.0\n"
            + "    spillOnRupture: true\n"
            + "    aliases:\n"
            + "      - \"distilledwater\"\n"
            + "      - \"distilled_water\"\n"
            + "      - \"water.distilled\"\n"
            + "\n"
            + "  heavywater:\n"
            + "    fluidName: \"heavywater\"\n"
            + "    density: 1105.0\n"
            + "    dynamicViscosity: 0.00125\n"
            + "    specificHeat: 4210.0\n"
            + "    thermalConductivity: 0.595\n"
            + "    boilingPointCelsius: 101.4\n"
            + "    freezingPointCelsius: 3.8\n"
            + "    isMolten: false\n"
            + "    radiolysisMultiplier: 1.0\n"
            + "    spillOnRupture: true\n"
            + "    aliases:\n"
            + "      - \"heavy_water\"\n"
            + "      - \"water.heavy\"\n"
            + "\n"
            + "  ic2coolant:\n"
            + "    fluidName: \"ic2coolant\"\n"
            + "    density: 1080.0\n"
            + "    dynamicViscosity: 0.0025\n"
            + "    specificHeat: 3800.0\n"
            + "    thermalConductivity: 0.520\n"
            + "    boilingPointCelsius: Infinity\n"
            + "    freezingPointCelsius: -Infinity\n"
            + "    isMolten: false\n"
            + "    radiolysisMultiplier: 1.0\n"
            + "    spillOnRupture: true\n"
            + "    aliases:\n"
            + "      - \"coolant\"\n"
            + "\n"
            + "  sodium:\n"
            + "    fluidName: \"sodium\"\n"
            + "    density: 927.0\n"
            + "    dynamicViscosity: 0.0007\n"
            + "    specificHeat: 1260.0\n"
            + "    thermalConductivity: 86.0\n"
            + "    boilingPointCelsius: Infinity\n"
            + "    freezingPointCelsius: 97.8\n"
            + "    isMolten: false\n"
            + "    radiolysisMultiplier: 1.0\n"
            + "    spillOnRupture: true\n"
            + "    aliases:\n"
            + "      - \"fluid.sodium\"\n"
            + "      - \"liquid.sodium\"\n"
            + "\n"
            + "  molten.cheese:\n"
            + "    fluidName: \"molten.cheese\"\n"
            + "    density: 1120.0\n"
            + "    dynamicViscosity: 0.557\n"
            + "    specificHeat: 3000.0\n"
            + "    thermalConductivity: 0.481\n"
            + "    boilingPointCelsius: Infinity\n"
            + "    freezingPointCelsius: 46.85\n"
            + "    isMolten: true\n"
            + "    radiolysisMultiplier: 1.0\n"
            + "    spillOnRupture: true\n"
            + "    aliases:\n"
            + "      - \"cheese\"\n"
            + "      - \"moltencheese\"\n"
            + "\n"
            + "  lava:\n"
            + "    fluidName: \"lava\"\n"
            + "    density: 3000.0\n"
            + "    dynamicViscosity: 0.001\n"
            + "    specificHeat: 1250.0\n"
            + "    thermalConductivity: 1.5\n"
            + "    boilingPointCelsius: Infinity\n"
            + "    freezingPointCelsius: 1000.0\n"
            + "    isMolten: true\n"
            + "    radiolysisMultiplier: 1.0\n"
            + "    spillOnRupture: true\n"
            + "    aliases:\n"
            + "      - \"fluid.lava\"\n"
            + "      - \"minecraft:lava\"\n"
            + "\n"
            + "# Liquid nuclear fuels that destroy impellers and fail closed-loop pumps\n"
            + "liquidNuclearFuels:\n"
            + "  - \"liquidfuel\"\n"
            + "  - \"liquid_fuel\"\n"
            + "  - \"nuclearfuel\"\n"
            + "  - \"uraniumhexafluoride\"\n"
            + "  - \"uraniumtetrafluoride\"\n"
            + "  - \"thoriumbasedliquidfuel\"\n"
            + "  - \"uraniumbasedliquidfuel\"\n"
            + "  - \"plutoniumbasedliquidfuel\"\n"
            + "  - \"naquadahbasedliquidfuel\"\n"
            + "  - \"liquid_naquadah_fuel\"\n"
            + "  - \"depletedthoriumbasedliquidfuel\"\n"
            + "  - \"depleteduraniumbasedliquidfuel\"\n"
            + "  - \"depletedplutoniumbasedliquidfuel\"\n"
            + "  - \"depletednaquadahbasedliquidfuel\"\n";

        writeFile(file, yaml);
    }

    private static void loadPipes() {
        File file = new File(configFolder, "pipes.yaml");
        if (!file.exists()) {
            writeDefaultPipes(file);
        }
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            Yaml yaml = createYaml();
            Map<String, Object> map = yaml.load(reader);
            if (map != null) {
                pipeData = CoolantPipeData.fromMap(map);
            }
        } catch (Exception e) {
            LOG.error("Failed to load pipes.yaml, keeping defaults", e);
        }
    }

    private static void writeDefaultPipes(File file) {
        String yaml = "# ==========================================================================\n"
            + "# Coolant Loops: Piping Hydrodynamics & Burst Limits\n"
            + "# ==========================================================================\n"
            + "\n"
            + "# Maximum burst pressure ratings in bar for pipe materials\n"
            + "materialPressureRatingsBar:\n"
            + "  castiron: 16.0\n"
            + "  iron: 16.0\n"
            + "  wroughtiron: 15.0\n"
            + "  steel: 35.0\n"
            + "  stainlesssteel: 70.0\n"
            + "  titanium: 140.0\n"
            + "  tungstensteel: 280.0\n"
            + "  osmium: 600.0\n"
            + "  neutronium: 3000.0\n"
            + "\n"
            + "# Maximum operating temperature limits in degrees Celsius\n"
            + "materialTemperatureLimitsCelsius:\n"
            + "  castiron: 1538.0\n"
            + "  iron: 1538.0\n"
            + "  wroughtiron: 1500.0\n"
            + "  steel: 1500.0\n"
            + "  stainlesssteel: 1700.0\n"
            + "  titanium: 1668.0\n"
            + "  tungstensteel: 3000.0\n"
            + "  osmium: 3033.0\n"
            + "  neutronium: 10000.0\n"
            + "\n"
            + "# Inner flow diameter (meters) by GregTech pipe thickness\n"
            + "thicknessDiameters:\n"
            + "  tiny: 0.05\n"
            + "  small: 0.075\n"
            + "  normal: 0.10\n"
            + "  large: 0.15\n"
            + "  huge: 0.20\n"
            + "\n"
            + "# Materials permitted for closed coolant loops (must match Railcraft tank compatibility)\n"
            + "allowedMaterials:\n"
            + "  - \"steel\"\n"
            + "  - \"iron\"\n"
            + "  - \"castiron\"\n"
            + "  - \"wroughtiron\"\n"
            + "  - \"stainlesssteel\"\n"
            + "  - \"titanium\"\n"
            + "  - \"tungstensteel\"\n"
            + "  - \"osmium\"\n"
            + "  - \"neutronium\"\n"
            + "\n"
            + "# Material equivalence families for loop continuity validation\n"
            + "materialFamilies:\n"
            + "  iron:\n"
            + "    - \"iron\"\n"
            + "    - \"castiron\"\n"
            + "    - \"wroughtiron\"\n"
            + "\n"
            + "# Standard commercial metal pipe surface roughness (meters)\n"
            + "defaultRoughness: 0.000045\n";

        writeFile(file, yaml);
    }

    private static void loadDevices() {
        File file = new File(configFolder, "devices.yaml");
        if (!file.exists()) {
            writeDefaultDevices(file);
        }
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            Yaml yaml = createYaml();
            Map<String, Object> map = yaml.load(reader);
            if (map != null) {
                deviceData = CoolantDeviceData.fromMap(map);
            }
        } catch (Exception e) {
            LOG.error("Failed to load devices.yaml, keeping defaults", e);
        }
    }

    private static void writeDefaultDevices(File file) {
        String yaml = "# ==========================================================================\n"
            + "# Coolant Loops: Multiblock Device Parameters (PHE, Degasser, Reservoir)\n"
            + "# ==========================================================================\n"
            + "\n"
            + "# Pressurized Heat Exchanger\n"
            + "phe:\n"
            + "  exchangeArea: 24.0                # Internal tube bundle surface area in m^2\n"
            + "  minorLossK: 1.2                  # Minor loss coefficient across bundle\n"
            + "  latentHeatJoulePerLiter: 2260000.0 # Latent heat of vaporization for water (J/L)\n"
            + "  steamExpansionRatio: 160          # 1 Liter water -> 160 Liters steam\n"
            + "  superheatedTempThreshold: 300.0   # Coolant temp for superheated steam (deg C)\n"
            + "  supercriticalTempThreshold: 700.0 # Coolant temp for supercritical steam (deg C)\n"
            + "  maxTempDropPerTick: 40.0          # Maximum primary coolant temp drop per tick\n"
            + "  coolingFactor: 0.5               # Relaxation factor towards boiling point\n"
            + "  allowedSecondaryFluids:\n"
            + "    - \"ic2distilledwater\"\n"
            + "    - \"distilledwater\"\n"
            + "    - \"distilled_water\"\n"
            + "    - \"water.distilled\"\n"
            + "\n"
            + "# Pressurized Degasser\n"
            + "degasser:\n"
            + "  minorLossK: 1.5                  # Column packing minor loss coefficient\n"
            + "  gasExtractionEfficiency: 0.25     # Maximum fraction of dissolved gas extracted per crossing\n"
            + "  gasExtractionPowerEuPerLiter: 8.0 # EU consumed per liter of gas separated\n"
            + "\n"
            + "# Coolant Bath\n"
            + "bath:\n"
            + "  fillVolumePerSlotLiters: 1000   # Coolant fill volume per item slot in Liters\n"
            + "  baseMinorLossK: 1.0            # Base minor loss coefficient of empty bath\n"
            + "  itemMinorLossK: 1.0            # Additional minor loss K per active item in bath\n"
            + "\n"
            + "# Creative Heat Reservoir\n"
            + "reservoir:\n"
            + "  exchangeArea: 2.0                # Heat exchange surface area in m^2\n"
            + "  temperaturePresets:\n"
            + "    - 20.0\n"
            + "    - 100.0\n"
            + "    - 300.0\n"
            + "    - 600.0\n"
            + "    - 1000.0\n";

        writeFile(file, yaml);
    }

    private static void writeFile(File file, String content) {
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            writer.write(content);
        } catch (Exception e) {
            LOG.error("Failed to write default YAML file " + file.getName(), e);
        }
    }

    /**
     * Applies configured fluid properties from YAML into the runtime registry of CoolantFluidProperty.
     */
    private static void applyFluidRegistry() {
        for (Map.Entry<String, CoolantFluidData.FluidPropertyEntry> entry : fluidData.getFluids()
            .entrySet()) {
            CoolantFluidData.FluidPropertyEntry prop = entry.getValue();
            CoolantFluidProperty canonical = CoolantFluidProperty.getCanonical(prop.getFluidName());
            if (canonical == null) {
                canonical = CoolantFluidProperty.getCanonical(entry.getKey());
            }

            CoolantFluidProperty runtimeProp;
            if (canonical != null && isClose(canonical.getDensity(), prop.getDensity())
                && isClose(canonical.getDynamicViscosity(), prop.getDynamicViscosity())
                && isClose(canonical.getSpecificHeat(), prop.getSpecificHeat())
                && isClose(canonical.getThermalConductivity(), prop.getThermalConductivity())
                && isClose(canonical.getBoilingPointCelsius(), prop.getBoilingPointCelsius())
                && isClose(canonical.getFreezingPointCelsius(), prop.getFreezingPointCelsius())
                && isClose(canonical.getRadiolysisMultiplier(), prop.getRadiolysisMultiplier())
                && canonical.isMolten() == prop.isMolten()
                && canonical.isSpillOnRupture() == prop.isSpillOnRupture()) {
                runtimeProp = canonical;
            } else {
                runtimeProp = new CoolantFluidProperty(
                    prop.getFluidName(),
                    prop.getDensity(),
                    prop.getDynamicViscosity(),
                    prop.getSpecificHeat(),
                    prop.getThermalConductivity(),
                    prop.getBoilingPointCelsius(),
                    prop.getFreezingPointCelsius(),
                    prop.isMolten(),
                    prop.getRadiolysisMultiplier(),
                    prop.isSpillOnRupture());
            }

            // Register primary name
            CoolantFluidProperty.register(runtimeProp);

            // Register key and aliases
            String key = entry.getKey()
                .toLowerCase();
            CoolantFluidProperty.registerAlias(key, runtimeProp);

            if (prop.getAliases() != null) {
                for (String alias : prop.getAliases()) {
                    if (alias != null && !alias.isEmpty()) {
                        CoolantFluidProperty.registerAlias(alias.toLowerCase(), runtimeProp);
                    }
                }
            }
        }
    }

    private static boolean isClose(double a, double b) {
        if (Double.isInfinite(a) || Double.isInfinite(b)) return a == b;
        if (Double.isNaN(a) && Double.isNaN(b)) return true;
        return Math.abs(a - b) < 1e-4;
    }

    public static synchronized void resetToDefaults() {
        physicsData = new CoolantPhysicsData();
        fluidData = new CoolantFluidData();
        pipeData = new CoolantPipeData();
        deviceData = new CoolantDeviceData();
        convectiveModel = ConvectiveHeatTransferModel.DEFAULT;
        CoolantFluidProperty.resetDefaults();
    }

    // Lookup APIs

    public static ConvectiveHeatTransferModel getConvectiveModel() {
        return convectiveModel;
    }

    public static CoolantPhysicsData getPhysicsData() {
        return physicsData;
    }

    public static CoolantFluidData getFluidData() {
        return fluidData;
    }

    public static CoolantPipeData getPipeData() {
        return pipeData;
    }

    public static CoolantDeviceData getDeviceData() {
        return deviceData;
    }

    public static void setDeviceData(CoolantDeviceData data) {
        deviceData = (data != null) ? data : new CoolantDeviceData();
    }

    public static double getPipePressureRating(String materialName) {
        if (materialName == null) return 10.0;
        String clean = materialName.toLowerCase()
            .replace(" ", "")
            .replace("_", "")
            .replace("-", "");
        Double rating = pipeData.getMaterialPressureRatingsBar()
            .get(clean);
        return rating != null ? rating : 15.0;
    }

    public static double getPipeTemperatureLimit(String materialName) {
        if (materialName == null) return 200.0;
        String clean = materialName.toLowerCase()
            .replace(" ", "")
            .replace("_", "")
            .replace("-", "");
        Double limit = pipeData.getMaterialTemperatureLimitsCelsius()
            .get(clean);
        return limit != null ? limit : 200.0;
    }

    public static double getDiameterForThickness(float thickness) {
        if (thickness <= 0.251f) {
            return pipeData.getThicknessDiameters()
                .getOrDefault("tiny", 0.05);
        } else if (thickness <= 0.376f) {
            return pipeData.getThicknessDiameters()
                .getOrDefault("small", 0.075);
        } else if (thickness <= 0.501f) {
            return pipeData.getThicknessDiameters()
                .getOrDefault("normal", 0.10);
        } else if (thickness <= 0.751f) {
            return pipeData.getThicknessDiameters()
                .getOrDefault("large", 0.15);
        } else {
            return pipeData.getThicknessDiameters()
                .getOrDefault("huge", 0.20);
        }
    }

    public static boolean isAllowedCoolantPipeMaterial(String materialName) {
        if (materialName == null) return false;
        String clean = materialName.toLowerCase()
            .replace(" ", "")
            .replace("_", "")
            .replace("-", "");
        return pipeData.getAllowedMaterials()
            .contains(clean);
    }
}
