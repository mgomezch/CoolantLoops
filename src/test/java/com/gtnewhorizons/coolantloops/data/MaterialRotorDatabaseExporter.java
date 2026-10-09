package com.gtnewhorizons.coolantloops.data;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.gtnewhorizons.coolantloops.engine.CoolantFluidProperty;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.relauncher.Side;
import gregtech.api.enums.Materials;
import gregtech.api.enums.SubTag;
import gregtech.api.util.TurbineStatCalculator;
import gregtech.common.items.IDMetaTool01;
import gregtech.common.items.MetaGeneratedTool01;

/**
 * Extracts GTNH material, turbine rotor, and coolant fluid properties into an SQLite database.
 * Grounded strictly in GT5, CoolantLoops, and ModularNuclear mod code and constants.
 */
public class MaterialRotorDatabaseExporter {

    public static final String DEFAULT_DB_PATH = "/home/mgomezch/stuff/dev/nh-dev/data/gtnh_materials_rotors.db";

    public static final int[] ROTOR_TOOL_IDS = { IDMetaTool01.TURBINE_SMALL.ID, IDMetaTool01.TURBINE.ID,
        IDMetaTool01.TURBINE_LARGE.ID, IDMetaTool01.TURBINE_HUGE.ID };

    public static final String[] ROTOR_SIZE_NAMES = { "Small", "Normal", "Large", "Huge" };

    @BeforeAll
    public static void initEnvironment() {
        Thread.currentThread()
            .setName("Server thread");
        try {
            java.lang.reflect.Field f = Loader.class.getDeclaredField("instance");
            f.setAccessible(true);
            Loader loader = Mockito.mock(Loader.class);
            Mockito.when(loader.getCallableCrashInformation())
                .thenReturn(Mockito.mock(cpw.mods.fml.common.ICrashCallable.class));
            f.set(null, loader);

            try {
                java.lang.reflect.Field nm = Loader.class.getDeclaredField("namedMods");
                nm.setAccessible(true);
                nm.set(loader, new HashMap<>());
            } catch (Throwable ignored) {}

            try {
                java.lang.reflect.Field mc = Loader.class.getDeclaredField("modController");
                mc.setAccessible(true);
                mc.set(loader, Mockito.mock(cpw.mods.fml.common.LoadController.class));
            } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}

        try {
            java.lang.reflect.Field f = cpw.mods.fml.relauncher.FMLRelaunchLog.class.getDeclaredField("side");
            f.setAccessible(true);
            f.set(null, Side.SERVER);
        } catch (Throwable ignored) {}

        try {
            net.minecraft.init.Bootstrap.func_151354_b();
        } catch (Throwable ignored) {}

        try {
            if (MetaGeneratedTool01.INSTANCE == null) {
                new MetaGeneratedTool01();
            }
        } catch (Throwable ignored) {}
    }

    public static void main(String[] args) throws Exception {
        initEnvironment();
        String dbPath = args.length > 0 ? args[0] : System.getProperty("gtnh.rotor.db", DEFAULT_DB_PATH);
        exportToDatabase(new File(dbPath));
    }

    @Test
    public void exportDatabaseTest() throws Exception {
        String dbPath = System.getProperty("gtnh.rotor.db", DEFAULT_DB_PATH);
        File dbFile = new File(dbPath);
        exportToDatabase(dbFile);
    }

    public static void exportToDatabase(File dbFile) throws Exception {
        if (dbFile.getParentFile() != null && !dbFile.getParentFile()
            .exists()) {
            dbFile.getParentFile()
                .mkdirs();
        }

        System.out.println("Exporting GTNH material and turbine rotor database to: " + dbFile.getAbsolutePath());
        Class.forName("org.sqlite.JDBC");

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath())) {
            conn.setAutoCommit(false);

            createTables(conn);
            int materialCount = exportMaterialsAndRotors(conn);
            int coolantCount = exportCoolantFluids(conn);
            int thermalCount = exportThermalLimits(conn);

            conn.commit();
            System.out.println("Export completed successfully:");
            System.out.println(" - Materials processed: " + materialCount);
            System.out.println(" - Coolant fluids exported: " + coolantCount);
            System.out.println(" - Thermal limit records: " + thermalCount);
        }
    }

    private static void createTables(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS material_thermal_limits;");
            stmt.execute("DROP TABLE IF EXISTS turbine_rotors;");
            stmt.execute("DROP TABLE IF EXISTS coolant_fluids;");
            stmt.execute("DROP TABLE IF EXISTS materials;");

            stmt.execute(
                "CREATE TABLE materials (" + "name TEXT PRIMARY KEY, "
                    + "local_name TEXT NOT NULL, "
                    + "chemical_formula TEXT, "
                    + "tier INTEGER NOT NULL, "
                    + "durability INTEGER NOT NULL, "
                    + "tool_speed REAL NOT NULL, "
                    + "blast_furnace_required INTEGER NOT NULL, "
                    + "blast_furnace_temp_k INTEGER NOT NULL, "
                    + "blast_furnace_temp_c REAL, "
                    + "melting_point_k INTEGER NOT NULL, "
                    + "melting_point_c REAL, "
                    + "gas_temp_k INTEGER NOT NULL, "
                    + "gas_temp_c REAL, "
                    + "density_gt INTEGER NOT NULL, "
                    + "has_molten_fluid INTEGER NOT NULL, "
                    + "molten_fluid_name TEXT, "
                    + "molten_temp_k INTEGER, "
                    + "molten_temp_c REAL, "
                    + "has_gas_fluid INTEGER NOT NULL, "
                    + "gas_fluid_name TEXT, "
                    + "gas_temp_fluid_k INTEGER, "
                    + "gas_temp_fluid_c REAL, "
                    + "has_plasma_fluid INTEGER NOT NULL, "
                    + "plasma_fluid_name TEXT, "
                    + "plasma_temp_k INTEGER, "
                    + "plasma_temp_c REAL, "
                    + "is_turbine_rotor_material INTEGER NOT NULL, "
                    + "is_loop_coolant INTEGER NOT NULL"
                    + ");");

            stmt.execute(
                "CREATE TABLE turbine_rotors (" + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "material_name TEXT NOT NULL REFERENCES materials(name), "
                    + "size TEXT NOT NULL, "
                    + "tool_id INTEGER NOT NULL, "
                    + "durability INTEGER NOT NULL, "
                    + "base_efficiency REAL NOT NULL, "
                    + "overflow_tier INTEGER NOT NULL, "
                    + "overflow_efficiency INTEGER NOT NULL, "
                    + "steam_efficiency_tight REAL NOT NULL, "
                    + "steam_efficiency_loose REAL NOT NULL, "
                    + "optimal_steam_flow_tight REAL NOT NULL, "
                    + "optimal_steam_flow_loose REAL NOT NULL, "
                    + "optimal_steam_eu_t_tight REAL NOT NULL, "
                    + "optimal_steam_eu_t_loose REAL NOT NULL, "
                    + "gas_efficiency_tight REAL NOT NULL, "
                    + "gas_efficiency_loose REAL NOT NULL, "
                    + "optimal_gas_flow_tight REAL NOT NULL, "
                    + "optimal_gas_flow_loose REAL NOT NULL, "
                    + "optimal_gas_eu_t_tight REAL NOT NULL, "
                    + "optimal_gas_eu_t_loose REAL NOT NULL, "
                    + "plasma_efficiency_tight REAL NOT NULL, "
                    + "plasma_efficiency_loose REAL NOT NULL, "
                    + "optimal_plasma_flow_tight REAL NOT NULL, "
                    + "optimal_plasma_flow_loose REAL NOT NULL, "
                    + "optimal_plasma_eu_t_tight REAL NOT NULL, "
                    + "optimal_plasma_eu_t_loose REAL NOT NULL"
                    + ");");

            stmt.execute(
                "CREATE TABLE coolant_fluids (" + "fluid_name TEXT PRIMARY KEY, "
                    + "display_name TEXT NOT NULL, "
                    + "density_kg_m3 REAL NOT NULL, "
                    + "dynamic_viscosity_pa_s REAL NOT NULL, "
                    + "kinematic_viscosity_m2_s REAL NOT NULL, "
                    + "specific_heat_j_kg_k REAL NOT NULL, "
                    + "volumetric_heat_capacity_j_m3_k REAL NOT NULL, "
                    + "thermal_conductivity_w_m_k REAL NOT NULL, "
                    + "prandtl_number REAL NOT NULL, "
                    + "freezing_point_c REAL NOT NULL, "
                    + "boiling_point_c REAL, "
                    + "can_boil INTEGER NOT NULL, "
                    + "is_molten INTEGER NOT NULL, "
                    + "is_plain_water INTEGER NOT NULL, "
                    + "declared_temperature_c REAL NOT NULL, "
                    + "mptr_byproduct_gas TEXT, "
                    + "corresponding_gt_material TEXT"
                    + ");");

            stmt.execute(
                "CREATE TABLE material_thermal_limits (" + "material_name TEXT PRIMARY KEY REFERENCES materials(name), "
                    + "local_name TEXT NOT NULL, "
                    + "tier INTEGER NOT NULL, "
                    + "normal_rotor_durability INTEGER NOT NULL, "
                    + "normal_base_efficiency REAL NOT NULL, "
                    + "melting_point_c REAL, "
                    + "molten_temp_c REAL, "
                    + "blast_furnace_temp_c REAL, "
                    + "effective_thermal_limit_c REAL NOT NULL, "
                    + "thermal_source TEXT NOT NULL"
                    + ");");

            stmt.execute("CREATE INDEX idx_rotors_mat ON turbine_rotors(material_name);");
            stmt.execute("CREATE INDEX idx_rotors_size ON turbine_rotors(size);");
            stmt.execute("CREATE INDEX idx_rotors_eff ON turbine_rotors(base_efficiency);");
            stmt.execute("CREATE INDEX idx_mat_tier ON materials(tier);");
            stmt.execute("CREATE INDEX idx_thermal_limit ON material_thermal_limits(effective_thermal_limit_c);");
        }
    }

    private static int exportMaterialsAndRotors(Connection conn) throws SQLException {
        String matSql = "INSERT INTO materials VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";
        String rotorSql = "INSERT INTO turbine_rotors (material_name, size, tool_id, durability, base_efficiency, "
            + "overflow_tier, overflow_efficiency, steam_efficiency_tight, steam_efficiency_loose, "
            + "optimal_steam_flow_tight, optimal_steam_flow_loose, optimal_steam_eu_t_tight, optimal_steam_eu_t_loose, "
            + "gas_efficiency_tight, gas_efficiency_loose, optimal_gas_flow_tight, optimal_gas_flow_loose, "
            + "optimal_gas_eu_t_tight, optimal_gas_eu_t_loose, plasma_efficiency_tight, plasma_efficiency_loose, "
            + "optimal_plasma_flow_tight, optimal_plasma_flow_loose, optimal_plasma_eu_t_tight, optimal_plasma_eu_t_loose) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";

        Set<String> knownCoolantNames = new HashSet<>();
        knownCoolantNames.add("Water");
        knownCoolantNames.add("DistilledWater");
        knownCoolantNames.add("HeavyWater");
        knownCoolantNames.add("Sodium");
        knownCoolantNames.add("Lead");
        knownCoolantNames.add("LeadBismuth");
        knownCoolantNames.add("Tin");
        knownCoolantNames.add("Cheese");

        List<Materials> allMaterials = new ArrayList<>(
            Materials.getMaterialsMap()
                .values());
        allMaterials.sort(Comparator.comparing(m -> m.mName));

        int materialsExported = 0;
        int rotorsExported = 0;

        try (PreparedStatement psMat = conn.prepareStatement(matSql);
            PreparedStatement psRotor = conn.prepareStatement(rotorSql)) {

            for (Materials mat : allMaterials) {
                if (mat == null || mat.mName == null || mat.mName.isEmpty()) continue;

                // Check turbine rotor eligibility
                boolean canBeRotor = false;
                List<TurbineStatCalculator> calculators = new ArrayList<>(4);

                if (mat.mDurability > 0 && mat.mToolSpeed > 0) {
                    for (int i = 0; i < ROTOR_TOOL_IDS.length; i++) {
                        int toolId = ROTOR_TOOL_IDS[i];
                        ItemStack stack = MetaGeneratedTool01.INSTANCE.getToolWithStats(toolId, 1, mat, mat, null);
                        if (stack != null) {
                            try {
                                TurbineStatCalculator calc = new TurbineStatCalculator(
                                    MetaGeneratedTool01.INSTANCE,
                                    stack);
                                if (calc.getMaxDurability() > 0) {
                                    calculators.add(calc);
                                } else {
                                    calculators.add(null);
                                }
                            } catch (Throwable t) {
                                calculators.add(null);
                            }
                        } else {
                            calculators.add(null);
                        }
                    }
                    if (calculators.size() == 4 && calculators.get(1) != null) {
                        canBeRotor = true;
                    }
                }

                boolean isCoolant = knownCoolantNames.contains(mat.mName) || "Water".equalsIgnoreCase(mat.mName);

                // Smelting / Molten Fluid state
                boolean hasSmeltToFluid = mat.contains(SubTag.SMELTING_TO_FLUID) || mat.mStandardMoltenFluid != null
                    || (mat.mFluid != null && mat.mFluid.getName()
                        .contains("molten"));

                String moltenFluidName = null;
                Integer moltenTempK = null;
                Double moltenTempC = null;

                if (hasSmeltToFluid) {
                    if (mat.mStandardMoltenFluid != null) {
                        moltenFluidName = mat.mStandardMoltenFluid.getName();
                        moltenTempK = mat.mStandardMoltenFluid.getTemperature();
                    } else if (mat.mFluid != null && mat.mFluid.getName()
                        .contains("molten")) {
                            moltenFluidName = mat.mFluid.getName();
                            moltenTempK = mat.mFluid.getTemperature();
                        } else {
                            moltenFluidName = "molten." + mat.mName.toLowerCase(Locale.ENGLISH);
                            moltenTempK = mat.mMeltingPoint > 0 ? mat.mMeltingPoint : 1000;
                        }
                    if (moltenTempK != null && moltenTempK > 0) {
                        moltenTempC = moltenTempK - 273.15;
                    }
                }

                // Gas state
                boolean hasGas = mat.mGas != null || mat.mGasTemp > 0;
                String gasFluidName = mat.mGas != null ? mat.mGas.getName() : null;
                Integer gasTempK = null;
                if (mat.mGas != null) {
                    gasTempK = mat.mGas.getTemperature();
                } else if (mat.mGasTemp > 0) {
                    gasTempK = mat.mGasTemp;
                }
                Double gasTempC = gasTempK != null ? (gasTempK - 273.15) : null;

                // Plasma state
                boolean hasPlasma = mat.mPlasma != null;
                String plasmaFluidName = mat.mPlasma != null ? mat.mPlasma.getName() : null;
                Integer plasmaTempK = (mat.mPlasma != null) ? Integer.valueOf(mat.mPlasma.getTemperature()) : null;
                Double plasmaTempC = plasmaTempK != null ? (plasmaTempK - 273.15) : null;

                // Temperatures in Celsius
                Double blastFurnaceTempC = mat.mBlastFurnaceTemp > 0 ? (mat.mBlastFurnaceTemp - 273.15) : null;
                Double meltingPointC = mat.mMeltingPoint > 0 ? (mat.mMeltingPoint - 273.15) : null;
                Double gtGasTempC = mat.mGasTemp > 0 ? (mat.mGasTemp - 273.15) : null;

                // Bind Material statement
                psMat.setString(1, mat.mName);
                psMat.setString(2, mat.mDefaultLocalName != null ? mat.mDefaultLocalName : mat.mName);
                psMat.setString(3, mat.getChemicalFormula());
                psMat.setInt(4, mat.mToolQuality);
                psMat.setInt(5, mat.mDurability);
                psMat.setDouble(6, mat.mToolSpeed);
                psMat.setInt(7, mat.mBlastFurnaceRequired ? 1 : 0);
                psMat.setInt(8, mat.mBlastFurnaceTemp);
                if (blastFurnaceTempC != null) psMat.setDouble(9, blastFurnaceTempC);
                else psMat.setNull(9, java.sql.Types.REAL);
                psMat.setInt(10, mat.mMeltingPoint);
                if (meltingPointC != null) psMat.setDouble(11, meltingPointC);
                else psMat.setNull(11, java.sql.Types.REAL);
                psMat.setInt(12, mat.mGasTemp);
                if (gtGasTempC != null) psMat.setDouble(13, gtGasTempC);
                else psMat.setNull(13, java.sql.Types.REAL);
                psMat.setLong(14, mat.mDensity);
                psMat.setInt(15, hasSmeltToFluid ? 1 : 0);
                psMat.setString(16, moltenFluidName);
                if (moltenTempK != null) psMat.setInt(17, moltenTempK);
                else psMat.setNull(17, java.sql.Types.INTEGER);
                if (moltenTempC != null) psMat.setDouble(18, moltenTempC);
                else psMat.setNull(18, java.sql.Types.REAL);
                psMat.setInt(19, hasGas ? 1 : 0);
                psMat.setString(20, gasFluidName);
                if (gasTempK != null) psMat.setInt(21, gasTempK);
                else psMat.setNull(21, java.sql.Types.INTEGER);
                if (gasTempC != null) psMat.setDouble(22, gasTempC);
                else psMat.setNull(22, java.sql.Types.REAL);
                psMat.setInt(23, hasPlasma ? 1 : 0);
                psMat.setString(24, plasmaFluidName);
                if (plasmaTempK != null) psMat.setInt(25, plasmaTempK);
                else psMat.setNull(25, java.sql.Types.INTEGER);
                if (plasmaTempC != null) psMat.setDouble(26, plasmaTempC);
                else psMat.setNull(26, java.sql.Types.REAL);
                psMat.setInt(27, canBeRotor ? 1 : 0);
                psMat.setInt(28, isCoolant ? 1 : 0);
                psMat.executeUpdate();
                materialsExported++;

                // Export rotor entries if valid
                if (canBeRotor) {
                    int overflowTier = 1 + (int) Math.min(2.0, mat.mToolQuality / 3.0);
                    for (int i = 0; i < ROTOR_SIZE_NAMES.length; i++) {
                        TurbineStatCalculator calc = calculators.get(i);
                        if (calc == null) continue;

                        psRotor.setString(1, mat.mName);
                        psRotor.setString(2, ROTOR_SIZE_NAMES[i]);
                        psRotor.setInt(3, ROTOR_TOOL_IDS[i]);
                        psRotor.setInt(4, (int) calc.getMaxDurability());
                        psRotor.setDouble(5, calc.getBaseEfficiency());
                        psRotor.setInt(6, overflowTier);
                        psRotor.setInt(7, calc.getOverflowEfficiency());

                        psRotor.setDouble(8, calc.getSteamEfficiency());
                        psRotor.setDouble(9, calc.getLooseSteamEfficiency());
                        psRotor.setDouble(10, calc.getOptimalSteamFlow());
                        psRotor.setDouble(11, calc.getOptimalLooseSteamFlow());
                        psRotor.setDouble(12, calc.getOptimalSteamEUt());
                        psRotor.setDouble(13, calc.getOptimalLooseSteamEUt());

                        psRotor.setDouble(14, calc.getGasEfficiency());
                        psRotor.setDouble(15, calc.getLooseGasEfficiency());
                        psRotor.setDouble(16, calc.getOptimalGasFlow());
                        psRotor.setDouble(17, calc.getOptimalLooseGasFlow());
                        psRotor.setDouble(18, calc.getOptimalGasEUt());
                        psRotor.setDouble(19, calc.getOptimalLooseGasEUt());

                        psRotor.setDouble(20, calc.getPlasmaEfficiency());
                        psRotor.setDouble(21, calc.getLoosePlasmaEfficiency());
                        psRotor.setDouble(22, calc.getOptimalPlasmaFlow());
                        psRotor.setDouble(23, calc.getOptimalLoosePlasmaFlow());
                        psRotor.setDouble(24, calc.getOptimalPlasmaEUt());
                        psRotor.setDouble(25, calc.getOptimalLoosePlasmaEUt());

                        psRotor.executeUpdate();
                        rotorsExported++;
                    }
                }
            }
        }
        System.out
            .println("Exported " + rotorsExported + " turbine rotor entries for " + materialsExported + " materials.");
        return materialsExported;
    }

    private static int exportCoolantFluids(Connection conn) throws SQLException {
        String sql = "INSERT INTO coolant_fluids VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";

        Map<String, String> displayNames = new HashMap<>();
        displayNames.put("water", "Water");
        displayNames.put("ic2distilledwater", "Distilled water");
        displayNames.put("heavywater", "Heavy water");
        displayNames.put("ic2coolant", "IC2 coolant");
        displayNames.put("sodium", "Sodium");
        displayNames.put("molten.cheese", "Molten cheese");

        Map<String, String> byproductGases = new HashMap<>();
        byproductGases.put("water", "Deuterium");
        byproductGases.put("ic2distilledwater", "Deuterium");
        byproductGases.put("heavywater", "Tritium");
        byproductGases.put("ic2coolant", "None");
        byproductGases.put("sodium", "None");
        byproductGases.put("molten.cheese", "None");

        Map<String, String> correspondingMats = new HashMap<>();
        correspondingMats.put("water", "Water");
        correspondingMats.put("ic2distilledwater", "Water");
        correspondingMats.put("heavywater", "HeavyWater");
        correspondingMats.put("ic2coolant", null);
        correspondingMats.put("sodium", "Sodium");
        correspondingMats.put("molten.cheese", "Cheese");

        List<CoolantFluidProperty> fluidList = new ArrayList<>();
        fluidList.add(CoolantFluidProperty.WATER);
        fluidList.add(CoolantFluidProperty.DISTILLED_WATER);
        fluidList.add(CoolantFluidProperty.HEAVY_WATER);
        fluidList.add(CoolantFluidProperty.IC2_COOLANT);
        fluidList.add(CoolantFluidProperty.SODIUM);
        fluidList.add(CoolantFluidProperty.MOLTEN_CHEESE);

        int count = 0;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (CoolantFluidProperty p : fluidList) {
                String fname = p.getFluidName();
                String dname = displayNames.getOrDefault(fname, fname);
                double prandtl = p.getDynamicViscosity() * p.getSpecificHeat() / p.getThermalConductivity();
                Double boilingPoint = Double.isInfinite(p.getBoilingPointCelsius()) ? null : p.getBoilingPointCelsius();

                ps.setString(1, fname);
                ps.setString(2, dname);
                ps.setDouble(3, p.getDensity());
                ps.setDouble(4, p.getDynamicViscosity());
                ps.setDouble(5, p.getKinematicViscosity());
                ps.setDouble(6, p.getSpecificHeat());
                ps.setDouble(7, p.getVolumetricHeatCapacity());
                ps.setDouble(8, p.getThermalConductivity());
                ps.setDouble(9, prandtl);
                ps.setDouble(10, p.getFreezingPointCelsius());
                if (boilingPoint != null) ps.setDouble(11, boilingPoint);
                else ps.setNull(11, java.sql.Types.REAL);
                ps.setInt(12, p.canBoil() ? 1 : 0);
                ps.setInt(13, p.isMolten() ? 1 : 0);
                ps.setInt(14, p.isPlainWater() ? 1 : 0);
                ps.setDouble(15, p.getDeclaredTemperatureCelsius());
                ps.setString(16, byproductGases.getOrDefault(fname, "None"));
                ps.setString(17, correspondingMats.get(fname));

                ps.executeUpdate();
                count++;
            }
        }
        return count;
    }

    private static int exportThermalLimits(Connection conn) throws SQLException {
        String querySql = "SELECT m.name, m.local_name, m.tier, m.melting_point_c, m.molten_temp_c, m.blast_furnace_temp_c, "
            + "r.durability, r.base_efficiency "
            + "FROM materials m "
            + "JOIN turbine_rotors r ON m.name = r.material_name AND r.size = 'Normal' "
            + "ORDER BY m.tier ASC, r.durability ASC;";

        String insertSql = "INSERT INTO material_thermal_limits VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";

        int count = 0;
        try (Statement qStmt = conn.createStatement();
            var rs = qStmt.executeQuery(querySql);
            PreparedStatement insStmt = conn.prepareStatement(insertSql)) {

            while (rs.next()) {
                String name = rs.getString(1);
                String localName = rs.getString(2);
                int tier = rs.getInt(3);
                Double meltingPointC = rs.getObject(4) != null ? rs.getDouble(4) : null;
                Double moltenTempC = rs.getObject(5) != null ? rs.getDouble(5) : null;
                Double blastFurnaceTempC = rs.getObject(6) != null ? rs.getDouble(6) : null;
                int durability = rs.getInt(7);
                double baseEfficiency = rs.getDouble(8);

                // Determine effective thermal limit
                double effectiveLimit;
                String source;

                if (moltenTempC != null && moltenTempC > 0) {
                    effectiveLimit = moltenTempC;
                    source = "Molten fluid temperature";
                } else if (meltingPointC != null && meltingPointC > 0) {
                    effectiveLimit = meltingPointC;
                    source = "Melting point";
                } else if (blastFurnaceTempC != null && blastFurnaceTempC > 0) {
                    effectiveLimit = blastFurnaceTempC;
                    source = "Blast furnace temperature";
                } else {
                    // Fallback based on tier: 1000 K (726.85 C) standard GT default
                    effectiveLimit = 726.85;
                    source = "Default GregTech baseline";
                }

                insStmt.setString(1, name);
                insStmt.setString(2, localName);
                insStmt.setInt(3, tier);
                insStmt.setInt(4, durability);
                insStmt.setDouble(5, baseEfficiency);
                if (meltingPointC != null) insStmt.setDouble(6, meltingPointC);
                else insStmt.setNull(6, java.sql.Types.REAL);
                if (moltenTempC != null) insStmt.setDouble(7, moltenTempC);
                else insStmt.setNull(7, java.sql.Types.REAL);
                if (blastFurnaceTempC != null) insStmt.setDouble(8, blastFurnaceTempC);
                else insStmt.setNull(8, java.sql.Types.REAL);
                insStmt.setDouble(9, effectiveLimit);
                insStmt.setString(10, source);

                insStmt.executeUpdate();
                count++;
            }
        }
        return count;
    }
}
