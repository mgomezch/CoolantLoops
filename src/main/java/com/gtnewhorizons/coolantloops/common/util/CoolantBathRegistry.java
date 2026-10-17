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

    private static final Map<String, HotColdPair> HOT_TO_COLD_INGOTS = new HashMap<>();
    private static final Map<String, HotColdPair> COLD_TO_HOT_INGOTS = new HashMap<>();
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
                            HOT_TO_COLD_INGOTS.put(getStackKey(in), pair);
                            COLD_TO_HOT_INGOTS.put(getStackKey(out), pair);
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
                    String hotKey = getStackKey(hot);
                    String coldKey = getStackKey(cold);
                    if (!HOT_TO_COLD_INGOTS.containsKey(hotKey)) {
                        HOT_TO_COLD_INGOTS.put(hotKey, pair);
                    }
                    if (!COLD_TO_HOT_INGOTS.containsKey(coldKey)) {
                        COLD_TO_HOT_INGOTS.put(coldKey, pair);
                    }
                }
            }
        } catch (Throwable ignored) {}
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

    public static String getStackKey(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return "";
        return stack.getItem().delegate.name() + ":" + stack.getItemDamage();
    }

    /**
     * Checks whether an item stack can be processed in the Coolant Bath.
     */
    public static boolean isCompatibleItem(ItemStack stack) {
        if (stack == null) return false;
        ensureInitialized();

        if (isCoolantCell(stack)) {
            return true;
        }

        ItemData data = GTOreDictUnificator.getAssociation(stack);
        if (data != null) {
            if (data.mPrefix == OrePrefixes.ingotHot) {
                return true;
            }
            if (data.mPrefix == OrePrefixes.ingot) {
                return COLD_TO_HOT_INGOTS.containsKey(getStackKey(stack))
                    || GTOreDictUnificator.get(OrePrefixes.ingotHot, data.mMaterial.mMaterial, 1L) != null;
            }
        }

        String key = getStackKey(stack);
        return HOT_TO_COLD_INGOTS.containsKey(key) || COLD_TO_HOT_INGOTS.containsKey(key);
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

        ItemData data = GTOreDictUnificator.getAssociation(stack);
        Materials mat = data != null && data.mMaterial != null ? data.mMaterial.mMaterial : null;

        // Check Hot Ingot cooling down
        if ((data != null && data.mPrefix == OrePrefixes.ingotHot)
            || HOT_TO_COLD_INGOTS.containsKey(getStackKey(stack))) {
            HotColdPair pair = HOT_TO_COLD_INGOTS.get(getStackKey(stack));
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
        if ((data != null && data.mPrefix == OrePrefixes.ingot) || COLD_TO_HOT_INGOTS.containsKey(getStackKey(stack))) {
            HotColdPair pair = COLD_TO_HOT_INGOTS.get(getStackKey(stack));
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
