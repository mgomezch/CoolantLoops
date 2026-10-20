package com.gtnewhorizons.coolantloops;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.gtnewhorizons.coolantloops.common.config.CoolantLoopDataRegistry;
import com.gtnewhorizons.coolantloops.common.config.data.CoolantDeviceData;
import com.gtnewhorizons.coolantloops.common.config.data.CoolantFluidData;
import com.gtnewhorizons.coolantloops.common.config.data.CoolantPhysicsData;
import com.gtnewhorizons.coolantloops.common.config.data.CoolantPipeData;
import com.gtnewhorizons.coolantloops.engine.ConvectiveHeatTransferModel;
import com.gtnewhorizons.coolantloops.engine.CoolantFluidProperty;
import com.gtnewhorizons.coolantloops.engine.CoolantPipingRegistry;

public class CoolantLoopDataRegistryTest {

    @BeforeEach
    public void setUp() {
        CoolantLoopDataRegistry.resetToDefaults();
    }

    @AfterEach
    public void tearDown() {
        CoolantLoopDataRegistry.resetToDefaults();
    }

    @Test
    public void testDefaultYamlGenerationAndRoundtrip(@TempDir Path tempDir) {
        File configDir = tempDir.toFile();
        CoolantLoopDataRegistry.init(configDir);

        File loopDir = new File(configDir, "coolantloops");
        assertTrue(loopDir.exists(), "coolantloops config directory should be created");

        File physicsFile = new File(loopDir, "physics.yaml");
        File fluidsFile = new File(loopDir, "fluids.yaml");
        File pipesFile = new File(loopDir, "pipes.yaml");
        File devicesFile = new File(loopDir, "devices.yaml");

        assertTrue(physicsFile.exists(), "physics.yaml should be automatically generated");
        assertTrue(fluidsFile.exists(), "fluids.yaml should be automatically generated");
        assertTrue(pipesFile.exists(), "pipes.yaml should be automatically generated");
        assertTrue(devicesFile.exists(), "devices.yaml should be automatically generated");

        // Verify default physics values
        CoolantPhysicsData phys = CoolantLoopDataRegistry.getPhysicsData();
        assertNotNull(phys);
        assertEquals(0.5, phys.getVTurbulent(), 1e-4);
        assertEquals(3.0, phys.getVSaturation(), 1e-4);
        assertEquals(120.0, phys.getHLaminarSlope(), 1e-4);
        assertEquals(1500.0, phys.getHTurbulentFactor(), 1e-4);
        assertEquals(5000.0, phys.getHMax(), 1e-4);
        assertEquals(8.0, phys.getUnpoweredImpellerK(), 1e-4);
        assertTrue(Double.isInfinite(phys.getMaxPumpHeadPressurePa()), "Default pump stall head must be uncapped");
        assertEquals(80.0, phys.getPumpMechanicalPowerPerEU(), 1e-4);
        assertEquals(1000L, phys.getFillRateLitersPerTick());

        // Verify default fluids
        CoolantFluidData fluids = CoolantLoopDataRegistry.getFluidData();
        assertNotNull(fluids);
        assertTrue(
            fluids.getFluids()
                .containsKey("water"));
        assertTrue(
            fluids.getFluids()
                .containsKey("ic2distilledwater"));
        assertTrue(
            fluids.getFluids()
                .containsKey("heavywater"));
        assertTrue(
            fluids.getFluids()
                .containsKey("sodium"));
        assertTrue(
            fluids.getFluids()
                .containsKey("ic2coolant"));
        assertTrue(
            fluids.getFluids()
                .containsKey("molten.cheese"));
        assertTrue(
            fluids.getFluids()
                .containsKey("lava"));

        // Verify default pipes: strictly the chosen 7 coolant pipe materials (with iron/cast/wrought equivalents)
        CoolantPipeData pipes = CoolantLoopDataRegistry.getPipeData();
        assertNotNull(pipes);
        assertEquals(
            35.0,
            pipes.getMaterialPressureRatingsBar()
                .get("steel"),
            1e-4);
        assertEquals(
            140.0,
            pipes.getMaterialPressureRatingsBar()
                .get("titanium"),
            1e-4);
        assertEquals(
            3000.0,
            pipes.getMaterialPressureRatingsBar()
                .get("neutronium"),
            1e-4);
        assertTrue(
            pipes.getAllowedMaterials()
                .contains("steel"));
        assertTrue(
            pipes.getAllowedMaterials()
                .contains("stainlesssteel"));
        assertTrue(
            pipes.getAllowedMaterials()
                .contains("titanium"));
        assertFalse(
            pipes.getMaterialPressureRatingsBar()
                .containsKey("wood"),
            "Non-coolant pipe materials must not exist");
        assertFalse(
            pipes.getMaterialPressureRatingsBar()
                .containsKey("copper"),
            "Non-coolant pipe materials must not exist");
        assertFalse(
            pipes.getMaterialPressureRatingsBar()
                .containsKey("bronze"),
            "Non-coolant pipe materials must not exist");
        assertFalse(
            pipes.getMaterialPressureRatingsBar()
                .containsKey("naquadah"),
            "Non-coolant pipe materials must not exist");

        // Verify default devices
        CoolantDeviceData devices = CoolantLoopDataRegistry.getDeviceData();
        assertNotNull(devices);
        assertEquals(24.0, devices.getPhe().exchangeArea, 1e-4);
        assertEquals(1.2, devices.getPhe().minorLossK, 1e-4);
        assertEquals(160, devices.getPhe().steamExpansionRatio);
        assertEquals(1.5, devices.getDegasser().minorLossK, 1e-4);
        assertEquals(0.25, devices.getDegasser().gasExtractionEfficiency, 1e-4);
        assertEquals(8.0, devices.getDegasser().gasExtractionPowerEuPerLiter, 1e-4);
    }

    @Test
    public void testCustomFluidAndPhysicsYamlConfiguration(@TempDir Path tempDir) throws Exception {
        File configDir = tempDir.toFile();
        File loopDir = new File(configDir, "coolantloops");
        loopDir.mkdirs();

        // Write custom physics.yaml
        String customPhysics = "vTurbulent: 0.8\n" + "vSaturation: 4.5\n"
            + "hLaminarSlope: 150.0\n"
            + "hTurbulentFactor: 2000.0\n"
            + "hMax: 8000.0\n"
            + "unpoweredImpellerK: 12.5\n"
            + "maxPumpHeadPressurePa: 4000000.0\n"
            + "pumpMechanicalPowerPerEU: 95.0\n"
            + "fillRateLitersPerTick: 2500\n"
            + "defaultAmbientTemp: 25.0\n"
            + "standardPassageLength: 6.0\n"
            + "standardPassageDiameter: 0.12\n"
            + "defaultPipeRoughness: 0.00005\n";
        Files.write(new File(loopDir, "physics.yaml").toPath(), customPhysics.getBytes());

        // Write custom fluids.yaml with a new fluid: dowtherm_a
        String customFluids = "fluids:\n" + "  dowtherm_a:\n"
            + "    fluidName: \"dowtherm_a\"\n"
            + "    density: 1060.0\n"
            + "    dynamicViscosity: 0.0035\n"
            + "    specificHeat: 2170.0\n"
            + "    thermalConductivity: 0.138\n"
            + "    boilingPointCelsius: 257.0\n"
            + "    freezingPointCelsius: 12.0\n"
            + "    isMolten: false\n"
            + "    radiolysisMultiplier: 0.5\n"
            + "    aliases:\n"
            + "      - \"synthetic_heat_transfer_fluid\"\n"
            + "      - \"fluid.dowtherm\"\n"
            + "  custom_liquid_metal:\n"
            + "    fluidName: \"custom_liquid_metal\"\n"
            + "    density: 10500.0\n"
            + "    dynamicViscosity: 0.0018\n"
            + "    specificHeat: 145.0\n"
            + "    thermalConductivity: 12.5\n"
            + "    boilingPointCelsius: Infinity\n"
            + "    freezingPointCelsius: 125.0\n"
            + "    isMolten: true\n"
            + "    radiolysisMultiplier: 0.0\n"
            + "    aliases:\n"
            + "      - \"lead_bismuth_eutectic\"\n";
        Files.write(new File(loopDir, "fluids.yaml").toPath(), customFluids.getBytes());

        // Write custom pipes.yaml with custom alloy inconel_718
        String customPipes = "materialPressureRatingsBar:\n" + "  steel: 45.0\n"
            + "  inconel_718: 500.0\n"
            + "materialTemperatureLimitsCelsius:\n"
            + "  steel: 1200.0\n"
            + "  inconel_718: 1400.0\n"
            + "thicknessDiameters:\n"
            + "  normal: 0.12\n"
            + "allowedMaterials:\n"
            + "  - \"steel\"\n"
            + "  - \"inconel_718\"\n"
            + "defaultRoughness: 0.00003\n";
        Files.write(new File(loopDir, "pipes.yaml").toPath(), customPipes.getBytes());

        // Write custom devices.yaml
        String customDevices = "phe:\n" + "  exchangeArea: 36.0\n"
            + "  minorLossK: 0.9\n"
            + "  latentHeatJoulePerLiter: 2000000.0\n"
            + "  steamExpansionRatio: 200\n"
            + "  superheatedTempThreshold: 280.0\n"
            + "  supercriticalTempThreshold: 650.0\n"
            + "degasser:\n"
            + "  minorLossK: 1.1\n"
            + "  gasExtractionEfficiency: 0.40\n"
            + "  gasExtractionPowerEuPerLiter: 12.0\n"
            + "bath:\n"
            + "  fillVolumePerSlotLiters: 1500\n"
            + "  baseMinorLossK: 2.0\n"
            + "  itemMinorLossK: 0.8\n"
            + "reservoir:\n"
            + "  exchangeArea: 5.0\n"
            + "  temperaturePresets:\n"
            + "    - 50.0\n"
            + "    - 250.0\n"
            + "    - 500.0\n";
        Files.write(new File(loopDir, "devices.yaml").toPath(), customDevices.getBytes());

        // Re-initialize registry with the custom directory
        CoolantLoopDataRegistry.init(configDir);

        // 1. Verify custom physics loaded
        CoolantPhysicsData phys = CoolantLoopDataRegistry.getPhysicsData();
        assertEquals(0.8, phys.getVTurbulent(), 1e-4);
        assertEquals(4.5, phys.getVSaturation(), 1e-4);
        assertEquals(150.0, phys.getHLaminarSlope(), 1e-4);
        assertEquals(2000.0, phys.getHTurbulentFactor(), 1e-4);
        assertEquals(8000.0, phys.getHMax(), 1e-4);
        assertEquals(12.5, phys.getUnpoweredImpellerK(), 1e-4);
        assertEquals(40.0 * 1e5, phys.getMaxPumpHeadPressurePa(), 1e-4);
        assertEquals(95.0, phys.getPumpMechanicalPowerPerEU(), 1e-4);
        assertEquals(2500L, phys.getFillRateLitersPerTick());
        assertEquals(25.0, phys.getDefaultAmbientTemp(), 1e-4);

        // 2. Verify convective model regenerated with new parameters
        ConvectiveHeatTransferModel model = CoolantLoopDataRegistry.getConvectiveModel();
        assertEquals(0.8, model.getVTurbulent(), 1e-4);
        assertEquals(4.5, model.getVSaturation(), 1e-4);
        assertEquals(8000.0, model.getHMax(), 1e-4);

        // 3. Verify custom fluids registered and searchable by primary name and alias
        CoolantFluidProperty dowtherm = CoolantFluidProperty.get("dowtherm_a");
        assertNotNull(dowtherm, "dowtherm_a must be registered via YAML");
        assertEquals(1060.0, dowtherm.getDensity(), 1e-4);
        assertEquals(0.0035, dowtherm.getDynamicViscosity(), 1e-4);
        assertEquals(2170.0, dowtherm.getSpecificHeat(), 1e-4);
        assertEquals(257.0, dowtherm.getBoilingPointCelsius(), 1e-4);
        assertFalse(dowtherm.isMolten());

        CoolantFluidProperty aliasLookup = CoolantFluidProperty.get("synthetic_heat_transfer_fluid");
        assertNotNull(aliasLookup, "fluid must be resolved by YAML alias");
        assertEquals("dowtherm_a", aliasLookup.getFluidName());

        CoolantFluidProperty lbe = CoolantFluidProperty.get("lead_bismuth_eutectic");
        assertNotNull(lbe, "custom liquid metal must be resolved by alias");
        assertEquals(10500.0, lbe.getDensity(), 1e-4);
        assertTrue(lbe.isMolten());

        // 4. Verify custom pipes
        assertEquals(45.0, CoolantPipingRegistry.getMaxPressureBar("steel"), 1e-4);
        assertEquals(500.0, CoolantPipingRegistry.getMaxPressureBar("inconel_718"), 1e-4);
        assertTrue(CoolantPipingRegistry.isAllowedCoolantPipeMaterial("inconel_718"));
        assertFalse(CoolantPipingRegistry.isAllowedCoolantPipeMaterial("copper"));

        // 5. Verify custom devices
        CoolantDeviceData devices = CoolantLoopDataRegistry.getDeviceData();
        assertEquals(36.0, devices.getPhe().exchangeArea, 1e-4);
        assertEquals(200, devices.getPhe().steamExpansionRatio);
        assertEquals(0.40, devices.getDegasser().gasExtractionEfficiency, 1e-4);
        assertEquals(12.0, devices.getDegasser().gasExtractionPowerEuPerLiter, 1e-4);
        assertEquals(1500L, devices.getBath().fillVolumePerSlotLiters);
        assertEquals(2.0, devices.getBath().baseMinorLossK, 1e-4);
        assertEquals(0.8, devices.getBath().itemMinorLossK, 1e-4);
        assertEquals(5.0, devices.getReservoir().exchangeArea, 1e-4);
        assertEquals(3, devices.getReservoir().temperaturePresets.size());
    }

    @Test
    public void testPHEAllowedSecondaryFluidsConfiguration(@TempDir Path tempDir) throws Exception {
        File configDir = tempDir.toFile();
        CoolantLoopDataRegistry.init(configDir);

        CoolantDeviceData.PHEData phe = CoolantLoopDataRegistry.getDeviceData()
            .getPhe();
        assertNotNull(phe);

        // By default, distilled water variants are allowed
        assertTrue(phe.isAllowedSecondaryFluid("ic2distilledwater"));
        assertTrue(phe.isAllowedSecondaryFluid("distilledwater"));
        assertTrue(phe.isAllowedSecondaryFluid("distilled_water"));
        assertTrue(phe.isAllowedSecondaryFluid("water.distilled"));

        // Plain regular water is NOT allowed by default in PHE
        assertFalse(phe.isAllowedSecondaryFluid("water"));
        assertFalse(phe.isAllowedSecondaryFluid("minecraft:water"));

        // Now customize devices.yaml to allow plain water as well
        File loopDir = new File(configDir, "coolantloops");
        String customDevices = "phe:\n" + "  allowedSecondaryFluids:\n"
            + "    - \"ic2distilledwater\"\n"
            + "    - \"water\"\n"
            + "    - \"minecraft:water\"\n";
        Files.write(new File(loopDir, "devices.yaml").toPath(), customDevices.getBytes());

        // Re-init from directory
        CoolantLoopDataRegistry.init(configDir);
        CoolantDeviceData.PHEData customPhe = CoolantLoopDataRegistry.getDeviceData()
            .getPhe();
        assertTrue(
            customPhe.isAllowedSecondaryFluid("water"),
            "Admin configured water must now be allowed without code changes");
        assertTrue(customPhe.isAllowedSecondaryFluid("minecraft:water"));
    }

    @Test
    public void testZeroSubstringMatchingForFluidsAndMaterials() {
        // CoolantFluidProperty must strictly match exact names and configured aliases, NOT arbitrary substrings
        assertNull(CoolantFluidProperty.get("sparkling_water"), "Arbitrary water substring must not match");
        assertNull(CoolantFluidProperty.get("cheese_sauce"), "Arbitrary cheese substring must not match");
        assertNull(CoolantFluidProperty.get("sodium_bicarbonate"), "Arbitrary sodium substring must not match");
        assertNull(CoolantFluidProperty.get("hot_coolant_blend"), "Arbitrary coolant substring must not match");
        assertNull(CoolantFluidProperty.get("super_heavy_water"), "Arbitrary heavy substring must not match");

        // Exact registered names and aliases must resolve
        assertNotNull(CoolantFluidProperty.get("water"));
        assertNotNull(CoolantFluidProperty.get("minecraft:water"));
        assertNotNull(CoolantFluidProperty.get("ic2distilledwater"));
        assertNotNull(CoolantFluidProperty.get("distilledwater"));
        assertNotNull(CoolantFluidProperty.get("sodium"));
        assertNotNull(CoolantFluidProperty.get("fluid.sodium"));
        assertNotNull(CoolantFluidProperty.get("molten.cheese"));
        assertNotNull(CoolantFluidProperty.get("cheese"));
        assertNotNull(CoolantFluidProperty.get("lava"));

        // Material compatibility must use configured materialFamilies
        assertTrue(CoolantPipingRegistry.areMaterialsEqual("iron", "castiron"));
        assertTrue(CoolantPipingRegistry.areMaterialsEqual("castiron", "wroughtiron"));
        assertFalse(
            CoolantPipingRegistry.areMaterialsEqual("steel", "stainlesssteel"),
            "Steel and stainless steel are distinct");
        assertFalse(CoolantPipingRegistry.areMaterialsEqual("iron", "steel"), "Iron and steel are distinct");
    }

    @Test
    public void testLiquidNuclearFuelsYAMLConfiguration(@TempDir Path tempDir) throws Exception {
        File configDir = tempDir.toFile();
        CoolantLoopDataRegistry.init(configDir);

        CoolantFluidData fluidData = CoolantLoopDataRegistry.getFluidData();
        assertTrue(fluidData.isLiquidNuclearFuel("uraniumbasedliquidfuel"));
        assertTrue(fluidData.isLiquidNuclearFuel("depleteduraniumbasedliquidfuel"));
        assertTrue(fluidData.isLiquidNuclearFuel("thoriumbasedliquidfuel"));
        assertFalse(
            fluidData.isLiquidNuclearFuel("custom_thorium_blend"),
            "Unregistered fluid is not liquid nuclear fuel");

        // Add custom fuel in fluids.yaml
        File loopDir = new File(configDir, "coolantloops");
        String customFluids = "liquidNuclearFuels:\n" + "  - \"custom_thorium_blend\"\n" + "  - \"liquidfuel\"\n";
        Files.write(new File(loopDir, "fluids.yaml").toPath(), customFluids.getBytes());

        CoolantLoopDataRegistry.init(configDir);
        assertTrue(
            CoolantLoopDataRegistry.getFluidData()
                .isLiquidNuclearFuel("custom_thorium_blend"),
            "Custom liquid fuel added in YAML must be recognized without code changes");
    }

    @Test
    public void testIndependentPhaseChangeOverrides(@TempDir Path tempDir) throws Exception {
        File configDir = tempDir.toFile();
        CoolantLoopDataRegistry.init(configDir);

        File loopDir = new File(configDir, "coolantloops");
        // Override only phase change points for water, without specifying any transport properties
        String customFluids = "fluids:\n" + "  water:\n"
            + "    boilingPointCelsius: 95.0\n"
            + "    freezingPointCelsius: -4.0\n";
        Files.write(new File(loopDir, "fluids.yaml").toPath(), customFluids.getBytes());

        CoolantLoopDataRegistry.init(configDir);

        CoolantFluidProperty water = CoolantFluidProperty.get("water");
        assertNotNull(water);
        // Phase change points should reflect the YAML overrides
        assertEquals(95.0, water.getBoilingPointCelsius(), 1e-4);
        assertEquals(-4.0, water.getFreezingPointCelsius(), 1e-4);
        // Transport properties should retain canonical water baseline
        assertEquals(1000.0, water.getDensity(), 1e-4);
        assertEquals(0.001, water.getDynamicViscosity(), 1e-5);
        assertEquals(4184.0, water.getSpecificHeat(), 1e-4);
        assertEquals(0.606, water.getThermalConductivity(), 1e-4);
        assertFalse(water.isMolten());
    }

    @Test
    public void testIndependentTransportPropertyOverrides(@TempDir Path tempDir) throws Exception {
        File configDir = tempDir.toFile();
        CoolantLoopDataRegistry.init(configDir);

        File loopDir = new File(configDir, "coolantloops");
        // Override only density for heavywater, without specifying phase change points
        String customFluids = "fluids:\n" + "  heavywater:\n" + "    density: 1115.0\n";
        Files.write(new File(loopDir, "fluids.yaml").toPath(), customFluids.getBytes());

        CoolantLoopDataRegistry.init(configDir);

        CoolantFluidProperty heavyWater = CoolantFluidProperty.get("heavywater");
        assertNotNull(heavyWater);
        // Transport override should be applied
        assertEquals(1115.0, heavyWater.getDensity(), 1e-4);
        // Phase change points should retain canonical heavywater baseline
        assertEquals(101.4, heavyWater.getBoilingPointCelsius(), 1e-4);
        assertEquals(3.8, heavyWater.getFreezingPointCelsius(), 1e-4);
        // Other transport properties should retain canonical values
        assertEquals(0.00125, heavyWater.getDynamicViscosity(), 1e-5);
        assertEquals(4210.0, heavyWater.getSpecificHeat(), 1e-4);
        assertEquals(0.595, heavyWater.getThermalConductivity(), 1e-4);
    }

    @Test
    public void testIC2CoolantNeverFreezesEvenWithYamlOverride(@TempDir Path tempDir) throws Exception {
        File configDir = tempDir.toFile();
        CoolantLoopDataRegistry.init(configDir);

        File loopDir = new File(configDir, "coolantloops");
        // Attempt to configure a finite freezing point for ic2coolant
        String customFluids = "fluids:\n" + "  ic2coolant:\n" + "    freezingPointCelsius: -25.0\n";
        Files.write(new File(loopDir, "fluids.yaml").toPath(), customFluids.getBytes());

        CoolantLoopDataRegistry.init(configDir);

        CoolantFluidProperty ic2Coolant = CoolantFluidProperty.get("ic2coolant");
        assertNotNull(ic2Coolant);
        // Must remain negative infinity regardless of configuration
        assertEquals(Double.NEGATIVE_INFINITY, ic2Coolant.getFreezingPointCelsius());
        assertTrue(Double.isInfinite(ic2Coolant.getBoilingPointCelsius()));
    }
}
