package com.gtnewhorizons.coolantloops.common.util;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.item.ItemStack;

import com.gtnewhorizons.coolantloops.common.metatileentity.multi.util.CoolantBathSlot;

import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.objects.ItemData;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTRecipe;

/**
 * Registry and physics helper for Coolant Bath compatible items.
 * Discovers and caches:
 * 1. Hot Ingot <-> Regular Ingot pairings from Vacuum Freezer recipes and Materials blast furnace temperatures.
 * 2. Coolant Cell heat capacities, damage scales, and temperature mappings.
 */
public class CoolantBathRegistry {

    private static final Map<ItemDamageKey, HotColdPair> HOT_TO_COLD_INGOTS = new HashMap<>();
    private static final Map<ItemDamageKey, HotColdPair> COLD_TO_HOT_INGOTS = new HashMap<>();
    private static boolean initialized = false;

    public static class HotColdPair {

        public final ItemStack hotStack;
        public final ItemStack coldStack;
        public final double blastFurnaceTempCelsius;
        public final Materials material;

        public HotColdPair(ItemStack hotStack, ItemStack coldStack, double blastFurnaceTempCelsius,
            Materials material) {
            this.hotStack = hotStack;
            this.coldStack = coldStack;
            this.blastFurnaceTempCelsius = blastFurnaceTempCelsius;
            this.material = material;
        }
    }

    public static class CryoCellPair {

        public final String name;
        public final ItemStack liquidCell;
        public final ItemStack gasCell;
        public final double boilingPointCelsius;
        public final double latentHeatJoules;

        public CryoCellPair(String name, ItemStack liquidCell, ItemStack gasCell, double boilingPointCelsius,
            double latentHeatJoules) {
            this.name = name;
            this.liquidCell = liquidCell;
            this.gasCell = gasCell;
            this.boilingPointCelsius = boilingPointCelsius;
            this.latentHeatJoules = latentHeatJoules;
        }
    }

    private static final Map<ItemDamageKey, CryoCellPair> CRYO_CELLS = new HashMap<>();

    public static synchronized void ensureInitialized() {
        if (initialized) return;
        initialized = true;

        // 1. Scan Vacuum Freezer recipes for hot ingot -> cold ingot conversions
        try {
            if (RecipeMaps.vacuumFreezerRecipes != null) {
                for (GTRecipe recipe : RecipeMaps.vacuumFreezerRecipes.getAllRecipes()) {
                    if (recipe.mInputs != null && recipe.mInputs.length > 0
                        && recipe.mOutputs != null
                        && recipe.mOutputs.length > 0) {
                        ItemStack in = recipe.mInputs[0];
                        ItemStack out = recipe.mOutputs[0];
                        if (in == null || out == null) continue;

                        ItemData inData = GTOreDictUnificator.getAssociation(in);
                        ItemData outData = GTOreDictUnificator.getAssociation(out);
                        if (inData != null && inData.mPrefix == OrePrefixes.ingotHot
                            && outData != null
                            && outData.mPrefix == OrePrefixes.ingot) {
                            Materials mat = inData.mMaterial != null ? inData.mMaterial.mMaterial : null;
                            double bfTemp = getMaterialBlastFurnaceTemp(mat);
                            HotColdPair pair = new HotColdPair(in.copy(), out.copy(), bfTemp, mat);
                            ItemDamageKey inKey = ItemDamageKey.of(in);
                            ItemDamageKey outKey = ItemDamageKey.of(out);
                            if (inKey != null) HOT_TO_COLD_INGOTS.put(inKey, pair);
                            if (outKey != null) COLD_TO_HOT_INGOTS.put(outKey, pair);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        // 2. Scan all GregTech materials for ingotHot <-> ingot pairs
        try {
            for (Materials mat : Materials.values()) {
                if (mat == null) continue;
                ItemStack hot = GTOreDictUnificator.get(OrePrefixes.ingotHot, mat, 1L);
                ItemStack cold = GTOreDictUnificator.get(OrePrefixes.ingot, mat, 1L);
                if (hot != null && cold != null) {
                    double bfTemp = getMaterialBlastFurnaceTemp(mat);
                    HotColdPair pair = new HotColdPair(hot, cold, bfTemp, mat);
                    ItemDamageKey hotKey = ItemDamageKey.of(hot);
                    ItemDamageKey coldKey = ItemDamageKey.of(cold);
                    if (hotKey != null && !HOT_TO_COLD_INGOTS.containsKey(hotKey)) {
                        HOT_TO_COLD_INGOTS.put(hotKey, pair);
                    }
                    if (coldKey != null && !COLD_TO_HOT_INGOTS.containsKey(coldKey)) {
                        COLD_TO_HOT_INGOTS.put(coldKey, pair);
                    }
                }
            }
        } catch (Throwable ignored) {}

        // 3. Register cryogenic cells
        try {
            registerCryoCellPair(
                "liquidnitrogen",
                GTOreDictUnificator.get(OrePrefixes.cell, Materials.LiquidNitrogen, 1L),
                GTOreDictUnificator.get(OrePrefixes.cell, Materials.Nitrogen, 1L),
                -196.0,
                160000.0);
            registerCryoCellPair(
                "liquidoxygen",
                GTOreDictUnificator.get(OrePrefixes.cell, Materials.LiquidOxygen, 1L),
                GTOreDictUnificator.get(OrePrefixes.cell, Materials.Oxygen, 1L),
                -183.0,
                243000.0);
            ItemStack airCell = null;
            try {
                airCell = gregtech.api.util.GTModHandler.getIC2Item("airCell", 1L);
            } catch (Throwable ignored) {}
            if (airCell == null) {
                airCell = GTOreDictUnificator.get(OrePrefixes.cell, Materials.Air, 1L);
            }
            registerCryoCellPair(
                "liquidair",
                GTOreDictUnificator.get(OrePrefixes.cell, Materials.LiquidAir, 1L),
                airCell,
                -194.0,
                174000.0);
            ItemStack heliumLiquidCell = null;
            try {
                heliumLiquidCell = bartworks.system.material.WerkstoffLoader.LiquidHelium.get(OrePrefixes.cell);
            } catch (Throwable ignored) {}
            if (heliumLiquidCell == null) {
                heliumLiquidCell = GTOreDictUnificator.get(OrePrefixes.cell, Materials.Helium, 1L);
            }
            registerCryoCellPair(
                "liquidhelium",
                heliumLiquidCell,
                GTOreDictUnificator.get(OrePrefixes.cell, Materials.Helium, 1L),
                -269.0,
                20700.0);
        } catch (Throwable ignored) {}
    }

    public static void registerCryoCellPair(String name, ItemStack liquidCell, ItemStack gasCell,
        double boilingPointCelsius, double latentHeatJoules) {
        if (liquidCell == null || gasCell == null) return;
        CryoCellPair pair = new CryoCellPair(
            name,
            liquidCell.copy(),
            gasCell.copy(),
            boilingPointCelsius,
            latentHeatJoules);
        ItemDamageKey key = ItemDamageKey.of(liquidCell);
        if (key != null) {
            CRYO_CELLS.put(key, pair);
        }
    }

    public static CryoCellPair getCryoCellPair(ItemStack stack) {
        if (stack == null) return null;
        ensureInitialized();
        ItemDamageKey key = ItemDamageKey.of(stack);
        if (key != null && CRYO_CELLS.containsKey(key)) {
            return CRYO_CELLS.get(key);
        }
        // Fallback by checking item data associations or unlocalized names
        ItemData data = GTOreDictUnificator.getAssociation(stack);
        if (data != null && data.mPrefix == OrePrefixes.cell
            && data.mMaterial != null
            && data.mMaterial.mMaterial != null) {
            String matName = data.mMaterial.mMaterial.mName.toLowerCase();
            if (matName.contains("liquidnitrogen")) {
                return new CryoCellPair(
                    "liquidnitrogen",
                    stack,
                    GTOreDictUnificator.get(OrePrefixes.cell, Materials.Nitrogen, 1L),
                    -196.0,
                    160000.0);
            }
            if (matName.contains("liquidoxygen")) {
                return new CryoCellPair(
                    "liquidoxygen",
                    stack,
                    GTOreDictUnificator.get(OrePrefixes.cell, Materials.Oxygen, 1L),
                    -183.0,
                    243000.0);
            }
            if (matName.contains("liquidair")) {
                ItemStack airOut = null;
                try {
                    airOut = gregtech.api.util.GTModHandler.getIC2Item("airCell", 1L);
                } catch (Throwable ignored) {}
                if (airOut == null) airOut = GTOreDictUnificator.get(OrePrefixes.cell, Materials.Air, 1L);
                return new CryoCellPair("liquidair", stack, airOut, -194.0, 174000.0);
            }
            if (matName.contains("liquidhelium")) {
                return new CryoCellPair(
                    "liquidhelium",
                    stack,
                    GTOreDictUnificator.get(OrePrefixes.cell, Materials.Helium, 1L),
                    -269.0,
                    20700.0);
            }
        }
        String unloc = stack.getUnlocalizedName();
        if (unloc != null) {
            String lower = unloc.toLowerCase();
            if (lower.contains("liquidnitrogen") || lower.contains("liquid_nitrogen")) {
                return new CryoCellPair(
                    "liquidnitrogen",
                    stack,
                    GTOreDictUnificator.get(OrePrefixes.cell, Materials.Nitrogen, 1L),
                    -196.0,
                    160000.0);
            }
            if (lower.contains("liquidoxygen") || lower.contains("liquid_oxygen")) {
                return new CryoCellPair(
                    "liquidoxygen",
                    stack,
                    GTOreDictUnificator.get(OrePrefixes.cell, Materials.Oxygen, 1L),
                    -183.0,
                    243000.0);
            }
            if (lower.contains("liquidair") || lower.contains("liquid_air")) {
                ItemStack airOut = null;
                try {
                    airOut = gregtech.api.util.GTModHandler.getIC2Item("airCell", 1L);
                } catch (Throwable ignored) {}
                if (airOut == null) airOut = GTOreDictUnificator.get(OrePrefixes.cell, Materials.Air, 1L);
                return new CryoCellPair("liquidair", stack, airOut, -194.0, 174000.0);
            }
            if (lower.contains("liquidhelium") || lower.contains("liquid_helium")) {
                return new CryoCellPair(
                    "liquidhelium",
                    stack,
                    GTOreDictUnificator.get(OrePrefixes.cell, Materials.Helium, 1L),
                    -269.0,
                    20700.0);
            }
        }
        return null;
    }

    public static boolean isCryoCell(ItemStack stack) {
        return getCryoCellPair(stack) != null;
    }

    public static double getMaterialBlastFurnaceTemp(Materials mat) {
        if (mat == null) return 1200.0;
        if (mat.mBlastFurnaceTemp > 0) {
            // GT blast furnace temp is stored in Kelvin, convert to Celsius
            double celsius = mat.mBlastFurnaceTemp - 273.15;
            return Math.max(200.0, celsius);
        }
        return 1200.0; // Default fallback for high-temperature metals
    }

    @Deprecated
    public static String getStackKey(ItemStack stack) {
        ItemDamageKey key = ItemDamageKey.of(stack);
        return key != null ? key.toString() : "";
    }

    /**
     * Checks whether an item stack can be processed in the Coolant Bath.
     */
    public static boolean isCompatibleItem(ItemStack stack) {
        if (stack == null) return false;
        ensureInitialized();

        if (isCryoCell(stack)) {
            return true;
        }

        if (isCoolantCell(stack)) {
            return true;
        }

        ItemDamageKey key = ItemDamageKey.of(stack);
        ItemData data = GTOreDictUnificator.getAssociation(stack);
        if (data != null) {
            if (data.mPrefix == OrePrefixes.ingotHot) {
                return true;
            }
            if (data.mPrefix == OrePrefixes.ingot) {
                return (key != null && COLD_TO_HOT_INGOTS.containsKey(key))
                    || GTOreDictUnificator.get(OrePrefixes.ingotHot, data.mMaterial.mMaterial, 1L) != null;
            }
        }

        return key != null && (HOT_TO_COLD_INGOTS.containsKey(key) || COLD_TO_HOT_INGOTS.containsKey(key));
    }

    /**
     * Checks if the stack is an IC2 or GregTech coolant cell.
     */
    public static boolean isCoolantCell(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return false;

        String className = stack.getItem()
            .getClass()
            .getName();
        if (className.contains("CoolantCell") || className.contains("ItemReactorHeatStorage")
            || className.contains("ItemReactorCondensator")) {
            return true;
        }

        String unloc = stack.getUnlocalizedName();
        if (unloc != null) {
            String lower = unloc.toLowerCase();
            if (lower.contains("coolantcell") || lower.contains("reactorcoolant")
                || lower.contains("heatcapacitor")
                || lower.contains("heatstorage")) {
                return true;
            }
        }

        return false;
    }

    /**
     * Creates a new CoolantBathSlot for an accepted item stack based on current loop coolant temperature.
     */
    public static CoolantBathSlot createSlot(ItemStack inputStack, double currentCoolantTemp) {
        if (inputStack == null) return null;
        ensureInitialized();

        ItemStack stack = inputStack.copy();
        stack.stackSize = 1;

        CryoCellPair cryo = getCryoCellPair(stack);
        if (cryo != null) {
            double cellTemp = cryo.boilingPointCelsius;
            double targetTemp = cryo.boilingPointCelsius;
            int maxDmg = 1000;
            int initialDmg = 0;
            double heatCap = Math.max(1000.0, cryo.latentHeatJoules); // Latent heat of vaporization in Joules
            double area = 0.25; // m^2 surface area
            ItemStack gasOutput = cryo.gasCell != null ? cryo.gasCell.copy() : null;

            return new CoolantBathSlot(
                stack,
                cellTemp,
                targetTemp,
                initialDmg,
                maxDmg,
                heatCap,
                area,
                false,
                false,
                false, // cooling = false: item absorbs heat / boils
                gasOutput,
                true);
        }

        if (isCoolantCell(stack)) {
            int maxDmg = stack.getMaxDamage() > 0 ? stack.getMaxDamage() : 10000;
            int curDmg = stack.getItemDamage();
            // Temperature mapping: damage 1 -> 20 C, maxDamage -> 1000 C
            double cellTemp = 20.0 + ((double) Math.max(0, curDmg - 1) / Math.max(1, maxDmg - 1)) * 980.0;

            boolean cooling = cellTemp > currentCoolantTemp;
            double targetTemp = cooling ? 20.0 : 1000.0;
            double heatCap = maxDmg * 1.0; // 1 HU per damage point, ~1000-60000 J/K
            double area = 0.25; // m^2 surface area

            return new CoolantBathSlot(
                stack,
                cellTemp,
                targetTemp,
                curDmg,
                maxDmg,
                heatCap,
                area,
                true,
                false,
                cooling,
                null);
        }

        ItemDamageKey key = ItemDamageKey.of(stack);
        ItemData data = GTOreDictUnificator.getAssociation(stack);
        Materials mat = data != null && data.mMaterial != null ? data.mMaterial.mMaterial : null;

        // Check Hot Ingot cooling down
        if ((data != null && data.mPrefix == OrePrefixes.ingotHot)
            || (key != null && HOT_TO_COLD_INGOTS.containsKey(key))) {
            HotColdPair pair = key != null ? HOT_TO_COLD_INGOTS.get(key) : null;
            double hotTemp = pair != null ? pair.blastFurnaceTempCelsius : getMaterialBlastFurnaceTemp(mat);
            ItemStack coldOutput = pair != null ? pair.coldStack.copy()
                : (mat != null ? GTOreDictUnificator.get(OrePrefixes.ingot, mat, 1L) : null);

            double initialTemp = hotTemp;
            double targetTemp = 20.0;
            int maxDmg = 1000;
            int initialDmg = 0;
            double mass = mat != null ? Math.max(1.0, mat.getMass()) : 10.0;
            double heatCap = mass * 50.0; // e.g. 500 - 5000 J/K
            double area = 0.15; // m^2

            return new CoolantBathSlot(
                stack,
                initialTemp,
                targetTemp,
                initialDmg,
                maxDmg,
                heatCap,
                area,
                false,
                true,
                true,
                coldOutput);
        }

        // Check Cold Ingot heating up
        if ((data != null && data.mPrefix == OrePrefixes.ingot)
            || (key != null && COLD_TO_HOT_INGOTS.containsKey(key))) {
            HotColdPair pair = key != null ? COLD_TO_HOT_INGOTS.get(key) : null;
            double hotTemp = pair != null ? pair.blastFurnaceTempCelsius : getMaterialBlastFurnaceTemp(mat);
            ItemStack hotOutput = pair != null ? pair.hotStack.copy()
                : (mat != null ? GTOreDictUnificator.get(OrePrefixes.ingotHot, mat, 1L) : null);

            double initialTemp = 20.0;
            double targetTemp = hotTemp;
            int maxDmg = 1000;
            int initialDmg = 0;
            double mass = mat != null ? Math.max(1.0, mat.getMass()) : 10.0;
            double heatCap = mass * 50.0;
            double area = 0.15;

            return new CoolantBathSlot(
                stack,
                initialTemp,
                targetTemp,
                initialDmg,
                maxDmg,
                heatCap,
                area,
                false,
                true,
                false,
                hotOutput);
        }

        return null;
    }
}
