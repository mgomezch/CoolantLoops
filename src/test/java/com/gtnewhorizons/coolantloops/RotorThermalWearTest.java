package com.gtnewhorizons.coolantloops;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.coolantloops.client.tooltip.RotorTooltipEventHandler;
import com.gtnewhorizons.coolantloops.common.metatileentity.multi.MTECoolantPump;
import com.gtnewhorizons.coolantloops.common.opencomputers.LoopInstrumentEnvironment;
import com.gtnewhorizons.coolantloops.common.tileentity.TileEntityLoopInstrument;
import com.gtnewhorizons.coolantloops.common.util.CoolantLocalization;
import com.gtnewhorizons.coolantloops.common.util.RotorThermalHelper;

import gregtech.api.enums.Materials;
import gregtech.common.items.IDMetaTool01;
import gregtech.common.items.MetaGeneratedTool01;

public class RotorThermalWearTest {

    @BeforeAll
    public static void setUp() {
        CoolantLocalization.loadLangFile("/assets/coolantloops/lang/en_US.lang");
        com.gtnewhorizons.coolantloops.data.MaterialRotorDatabaseExporter.initEnvironment();
    }

    @Test
    public void testThermalHelperFormulasAndAlpha4() {
        // Test homologous temperature
        // Titanium: melting point in GT is around 1668 C = 1941.15 K
        double meltC = RotorThermalHelper.getMeltingPointCelsius(Materials.Titanium);
        assertTrue(meltC > 1500.0, "Titanium melting point should be > 1500 C");

        double tFluidC = 20.0;
        double thCold = RotorThermalHelper.calculateHomologousTemperature(tFluidC, Materials.Titanium);
        assertTrue(thCold < 0.5, "Cold titanium Th should be well below 0.5");
        assertEquals(1.0, RotorThermalHelper.calculateThermalWearMultiplier(thCold), 1e-6);

        // Alpha = 4.0 wear tests at exact homologous temperatures
        assertEquals(1.0, RotorThermalHelper.calculateThermalWearMultiplier(0.40), 1e-6);
        assertEquals(1.0, RotorThermalHelper.calculateThermalWearMultiplier(0.50), 1e-6);

        // Th = 0.75: norm = (0.75 - 0.5)/0.5 = 0.5; norm^2 = 0.25; k_temp = 1.0 + 4.0 * 0.25 = 2.0
        assertEquals(2.0, RotorThermalHelper.calculateThermalWearMultiplier(0.75), 1e-6);

        // Th = 1.0: norm = 1.0; k_temp = 1.0 + 4.0 * 1.0 = 5.0
        assertEquals(5.0, RotorThermalHelper.calculateThermalWearMultiplier(1.0), 1e-6);

        // Melting checks
        assertFalse(RotorThermalHelper.isMelted(0.999));
        assertTrue(RotorThermalHelper.isMelted(1.000));
        assertTrue(RotorThermalHelper.isMelted(1.200));

        // Softening temperature in Celsius: (T_melt_K * 0.5) - 273.15
        double meltK = RotorThermalHelper.getMeltingPointKelvin(Materials.Titanium);
        double expectedSoftenC = (meltK * 0.5) - 273.15;
        assertEquals(expectedSoftenC, RotorThermalHelper.getThermalSofteningCelsius(Materials.Titanium), 1e-6);
    }

    @Test
    public void testPumpThermalWearAccumulation() {
        MTECoolantPump pump = new MTECoolantPump("thermal_wear_test_pump");
        pump.setRotorMaterial(Materials.Magnalium);
        // Magnalium melting point in GT is approx 650-660 C (around 924-933 K)
        double meltK = RotorThermalHelper.getMeltingPointKelvin(Materials.Magnalium);
        double meltC = RotorThermalHelper.getMeltingPointCelsius(Materials.Magnalium);

        // Case 1: Cold fluid (20 C) -> Th < 0.5 -> thermal factor = 1.0
        pump.setSimulatedFluidTemperatureCelsius(20.0);
        assertTrue(pump.getHomologousTemperature() < 0.5);
        assertEquals(1.0, pump.getThermalWearMultiplier(), 1e-6);

        // Create a dummy rotor stack
        ItemStack rotorStack = MetaGeneratedTool01.INSTANCE
            .getToolWithStats(IDMetaTool01.TURBINE.ID, 1, Materials.Magnalium, Materials.Magnalium, null);
        pump.setRotorStack(rotorStack);
        pump.setRotorDamageAccumulator(0.0);

        // Run degradation tick at 20 C: wear factor should be 1.0 (no gas, cold)
        long d0 = pump.getRotorDamage();
        pump.degradeRotor(512L);
        assertEquals(d0 + 1, pump.getRotorDamage(), "Wear multiplier 1.0 must inflict 1 damage to rotor");
        assertEquals(0.0, pump.getRotorDamageAccumulator(), 1e-6, "Integer damage must flush from accumulator");

        // Case 2: Fluid at Th = 0.75:
        // T_fluid_K = 0.75 * meltK => T_fluid_C = 0.75 * meltK - 273.15
        double t75C = 0.75 * meltK - 273.15;
        pump.setSimulatedFluidTemperatureCelsius(t75C);
        assertEquals(0.75, pump.getHomologousTemperature(), 1e-4);
        assertEquals(2.0, pump.getThermalWearMultiplier(), 1e-4);

        // Wear factor should now be 2.0
        long d1 = pump.getRotorDamage();
        pump.degradeRotor(512L);
        assertEquals(d1 + 2, pump.getRotorDamage(), "Wear multiplier 2.0 must inflict 2 damage to rotor");
        assertEquals(0.0, pump.getRotorDamageAccumulator(), 1e-6, "Integer damage must flush from accumulator");

        // Case 3: Combined Cavitation + Thermal Softening:
        // 50% dissolved gas (cavitation = 1.5) and Th = 0.75 (thermal = 2.0) => 3.0x wear!
        pump.extractDissolvedGas("Tritium", 100000L); // Clear
        pump.addDissolvedGas("Tritium", 50000L); // 50% of 100,000 L
        assertEquals(0.5, pump.getDissolvedGasFraction(), 1e-6);

        long d2 = pump.getRotorDamage();
        pump.degradeRotor(512L);
        assertEquals(d2 + 3, pump.getRotorDamage(), "Combined wear multiplier 3.0 must inflict 3 damage to rotor");
        assertEquals(0.0, pump.getRotorDamageAccumulator(), 1e-6, "Integer damage must flush from accumulator");

        // Case 4: Fractional wear accumulation
        pump.setRotorDamageAccumulator(0.4);
        pump.setSimulatedFluidTemperatureCelsius(20.0);
        pump.extractDissolvedGas("Tritium", 100000L); // 0% gas, cold => totalWearMultiplier = 1.0
        long d3 = pump.getRotorDamage();
        pump.degradeRotor(512L);
        // 0.4 + 1.0 = 1.4 => dmg = 1, remaining accumulator = 0.4
        assertEquals(d3 + 1, pump.getRotorDamage(), "Must inflict 1 damage");
        assertEquals(0.4, pump.getRotorDamageAccumulator(), 1e-4, "Accumulator must retain fractional wear");
    }

    @Test
    public void testCatastrophicMeltingFailure() {
        MTECoolantPump pump = new MTECoolantPump("melting_test_pump");
        pump.setRotorMaterial(Materials.Magnalium);
        double meltC = RotorThermalHelper.getMeltingPointCelsius(Materials.Magnalium);

        ItemStack rotorStack = MetaGeneratedTool01.INSTANCE
            .getToolWithStats(IDMetaTool01.TURBINE.ID, 1, Materials.Magnalium, Materials.Magnalium, null);
        pump.setRotorStack(rotorStack);
        assertTrue(pump.hasRotor());

        // Fluid reaches or exceeds melting point!
        pump.setSimulatedFluidTemperatureCelsius(meltC + 5.0);
        assertTrue(pump.getHomologousTemperature() >= 1.0);

        // Run degradation: should fail catastrophically and void rotor
        pump.degradeRotor(512L);

        assertFalse(pump.hasRotor(), "Impeller must be destroyed upon reaching melting point");
        assertNull(pump.getRotorStack(), "Rotor stack must be voided");
        assertEquals(0.0, pump.getRotorEfficiency(), 1e-6);

        // Check error message format and normal sentence case
        String status = pump.getLoopStatus();
        assertNotNull(status);
        assertTrue(status.contains("Catastrophic pump failure:"), "Status must announce catastrophic failure");
        assertTrue(status.contains("exceeded impeller melting point"), "Status must cite melting point");
        assertTrue(status.contains("Impeller melted!"), "Status must declare impeller melted");
        assertFalse(status.contains("Impeller Melted!"), "Must strictly use normal sentence case");
    }

    @Test
    public void testRotorTooltipEventHandler() {
        RotorTooltipEventHandler handler = new RotorTooltipEventHandler();
        ItemStack rotorStack = MetaGeneratedTool01.INSTANCE
            .getToolWithStats(IDMetaTool01.TURBINE.ID, 1, Materials.Titanium, Materials.Titanium, null);

        List<String> tooltipLines = new ArrayList<>();
        ItemTooltipEvent event = new ItemTooltipEvent(rotorStack, (EntityPlayer) null, tooltipLines, false);

        handler.onItemTooltip(event);

        assertFalse(tooltipLines.isEmpty(), "Tooltip handler should add lines for turbine rotor");
        boolean foundHeader = false;
        boolean foundSoftening = false;
        boolean foundMelting = false;

        for (String line : tooltipLines) {
            if (line.contains("Coolant loop impeller limits:")) foundHeader = true;
            if (line.contains("Thermal softening:")) foundSoftening = true;
            if (line.contains("Melting point:")) foundMelting = true;
        }

        assertTrue(foundHeader, "Tooltip must include impeller limits header");
        assertTrue(foundSoftening, "Tooltip must specify thermal softening temperature");
        assertTrue(foundMelting, "Tooltip must specify melting point");
    }

    @Test
    public void testScannerInfoDataIncludesThermalTelemtry() {
        MTECoolantPump pump = new MTECoolantPump("scanner_thermal_pump");
        ItemStack rotorStack = MetaGeneratedTool01.INSTANCE
            .getToolWithStats(IDMetaTool01.TURBINE.ID, 1, Materials.Titanium, Materials.Titanium, null);
        pump.setRotorStack(rotorStack);
        pump.setSimulatedFluidTemperatureCelsius(20.0);

        String[] info = pump.getInfoData();
        assertNotNull(info);
        assertTrue(info.length >= 10);

        boolean foundImpeller = false;
        boolean foundThermalWear = false;
        for (String line : info) {
            if (line.contains("Impeller: Titanium")) foundImpeller = true;
            if (line.contains("Impeller thermal wear:")) foundThermalWear = true;
            assertFalse(line.contains("Impeller Thermal Wear:"), "Must use normal sentence case: " + line);
        }

        assertTrue(foundImpeller, "Scanner info must display impeller material and specs");
        assertTrue(foundThermalWear, "Scanner info must display thermal wear multiplier and Th");
    }

    @Test
    public void testOpenComputersThermalTelemetry() {
        TileEntityLoopInstrument te = new TileEntityLoopInstrument();
        MTECoolantPump pump = new MTECoolantPump("oc_thermal_pump");
        ItemStack rotorStack = MetaGeneratedTool01.INSTANCE
            .getToolWithStats(IDMetaTool01.TURBINE.ID, 1, Materials.Titanium, Materials.Titanium, null);
        pump.setRotorStack(rotorStack);
        pump.setSimulatedFluidTemperatureCelsius(100.0);
        te.setLoopPump(pump);

        LoopInstrumentEnvironment env = new LoopInstrumentEnvironment(te);

        // Test getImpellerStatus callback
        Object[] res = env.getImpellerStatus(null, null);
        assertNotNull(res);
        assertTrue(res.length > 0);
        @SuppressWarnings("unchecked")
        Map<String, Object> status = (Map<String, Object>) res[0];

        assertTrue((Boolean) status.get("hasImpeller"));
        assertEquals("Titanium", status.get("material"));
        assertTrue((Double) status.get("meltingPointCelsius") > 1500.0);
        assertTrue((Double) status.get("softeningTempCelsius") > 600.0);
        assertTrue(status.containsKey("homologousTemperature"));
        assertEquals(1.0, (Double) status.get("thermalWearMultiplier"), 1e-4);
        assertFalse((Boolean) status.get("thermalSofteningActive"));
    }
}
