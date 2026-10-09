package com.gtnewhorizons.coolantloops.common.util;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.minecraftforge.fluids.FluidStack;

import gregtech.api.enums.Materials;
import gregtech.api.items.MetaGeneratedTool;

public final class RotorThermalHelper {

    /**
     * Alpha parameter for thermal softening and creep wear:
     * k_temp = 1.0 + ALPHA * ((Th - 0.5) / 0.5)^2
     * Near the melting point (Th -> 1.0), wear rate is (1.0 + ALPHA) * baseline wear = 5x.
     */
    public static final double ALPHA = 4.0;

    private RotorThermalHelper() {}

    public static boolean isMetaGeneratedTool(Item item) {
        if (item == null) return false;
        try {
            Class<?> clazz = item.getClass();
            while (clazz != null && clazz != Object.class) {
                if (clazz.getName()
                    .endsWith("MetaGeneratedTool")) {
                    return true;
                }
                clazz = clazz.getSuperclass();
            }
        } catch (Throwable ignored) {}
        return false;
    }

    public static boolean isTurbineRotor(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return false;
        if (isMetaGeneratedTool(stack.getItem())) {
            int damage = stack.getItemDamage();
            return damage >= 170 && damage <= 179;
        }
        return false;
    }

    public static Materials getRotorMaterial(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return null;
        try {
            if (stack.getItem() instanceof MetaGeneratedTool) {
                return ((MetaGeneratedTool) stack.getItem()).getPrimaryMaterial(stack);
            }
        } catch (Throwable ignored) {}
        return null;
    }

    public static double getMeltingPointCelsius(Materials mat) {
        if (mat == null) {
            return 726.85; // 1000 K standard GT default
        }
        try {
            FluidStack molten = mat.getMolten(1);
            if (molten != null && molten.getFluid() != null
                && molten.getFluid()
                    .getTemperature() > 0) {
                return molten.getFluid()
                    .getTemperature() - 273.15;
            }
        } catch (Throwable ignored) {}
        if (mat.mMeltingPoint > 0) {
            return mat.mMeltingPoint - 273.15;
        }
        if (mat.mBlastFurnaceTemp > 0) {
            return mat.mBlastFurnaceTemp - 273.15;
        }
        return 726.85;
    }

    public static double getMeltingPointKelvin(Materials mat) {
        return getMeltingPointCelsius(mat) + 273.15;
    }

    public static double getThermalSofteningCelsius(Materials mat) {
        return (getMeltingPointKelvin(mat) * 0.5) - 273.15;
    }

    public static double calculateHomologousTemperature(double fluidTempCelsius, Materials mat) {
        double tFluidK = fluidTempCelsius + 273.15;
        double tMeltK = getMeltingPointKelvin(mat);
        if (tMeltK <= 0) return 0.0;
        return tFluidK / tMeltK;
    }

    public static double calculateThermalWearMultiplier(double homologousTemp) {
        if (homologousTemp < 0.5) {
            return 1.0;
        }
        if (homologousTemp >= 1.0) {
            return 1.0 + ALPHA;
        }
        double norm = (homologousTemp - 0.5) / 0.5;
        return 1.0 + ALPHA * (norm * norm);
    }

    public static boolean isMelted(double homologousTemp) {
        return homologousTemp >= 1.0;
    }

    public static String getMaterialLocalizedName(Materials mat) {
        if (mat == null) return "Unknown";
        if (mat.mDefaultLocalName != null && !mat.mDefaultLocalName.isEmpty()) {
            try {
                String key = mat.getLocalizedNameKey();
                if (StatCollector.canTranslate(key)) {
                    return StatCollector.translateToLocal(key);
                }
            } catch (Throwable ignored) {}
            return mat.mDefaultLocalName;
        }
        return mat.getLocalizedName();
    }
}
