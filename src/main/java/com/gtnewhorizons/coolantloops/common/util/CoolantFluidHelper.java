package com.gtnewhorizons.coolantloops.common.util;

import net.minecraft.world.World;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizons.coolantloops.engine.CoolantFluidProperty;

import gregtech.api.enums.Materials;

public final class CoolantFluidHelper {

    private CoolantFluidHelper() {}

    public static boolean isPlainRegularWater(Fluid fluid) {
        if (fluid == null) return false;
        String name = fluid.getName()
            .trim()
            .toLowerCase();
        if (name.contains("distill") || name.contains("heavy")) {
            return false;
        }
        return name.equals("water") || name.endsWith(".water") || name.equals("fluid.water");
    }

    public static boolean isPlainRegularWater(FluidStack stack) {
        if (stack == null || stack.getFluid() == null) return false;
        return isPlainRegularWater(stack.getFluid());
    }

    public static boolean isLava(String name) {
        if (name == null) return false;
        String clean = name.trim()
            .toLowerCase();
        return clean.equals("lava") || clean.endsWith(".lava") || clean.equals("fluid.lava");
    }

    public static boolean isLava(Fluid fluid, FluidStack stack) {
        if (fluid == null && stack != null) {
            fluid = stack.getFluid();
        }
        if (fluid == null) return false;
        return isLava(fluid.getName());
    }

    public static boolean isLava(FluidStack stack) {
        if (stack == null || stack.getFluid() == null) return false;
        return isLava(stack.getFluid(), stack);
    }

    public static boolean isNetherWorld(World world) {
        if (world == null || world.provider == null) return false;
        return world.provider.dimensionId == -1 || world.provider.isHellWorld;
    }

    public static boolean isLiquidNuclearFuel(String name) {
        if (name == null) return false;
        String clean = name.trim()
            .toLowerCase();
        if (clean.contains("liquidfuel") || clean.contains("liquid_fuel") || clean.contains("nuclearfuel")) {
            return true;
        }
        if (clean.contains("uraniumhexafluoride") || clean.contains("uraniumtetrafluoride")) {
            return true;
        }
        if (clean.contains("thoriumbasedliquidfuel") || (clean.contains("thorium") && clean.contains("liquidfuel"))) {
            return true;
        }
        if (clean.contains("uraniumbasedliquidfuel") || (clean.contains("uranium") && clean.contains("liquidfuel"))) {
            return true;
        }
        if (clean.contains("plutoniumbasedliquidfuel")
            || (clean.contains("plutonium") && clean.contains("liquidfuel"))) {
            return true;
        }
        if (clean.contains("naquadahbasedliquidfuel") || (clean.contains("naquadah") && clean.contains("liquidfuel"))
            || clean.contains("liquid_naquadah_fuel")) {
            return true;
        }
        if (clean.contains("uranium") || clean.contains("plutonium")
            || clean.contains("thorium")
            || clean.contains("naquadah")
            || clean.contains("naquadria")) {
            if (clean.contains("fuel") || clean.contains("plasma")) {
                return true;
            }
        }
        return false;
    }

    public static boolean isLiquidNuclearFuel(Fluid fluid, FluidStack stack) {
        if (fluid == null && stack != null) {
            fluid = stack.getFluid();
        }
        if (fluid == null) return false;
        if (isLiquidNuclearFuel(fluid.getName())) {
            return true;
        }
        try {
            Materials mat = Materials.FLUID_MAP.get(fluid);
            if (mat != null && mat.mName != null && isLiquidNuclearFuel(mat.mName)) {
                return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    public static boolean isLiquidNuclearFuel(FluidStack stack) {
        if (stack == null || stack.getFluid() == null) return false;
        return isLiquidNuclearFuel(stack.getFluid(), stack);
    }

    public static boolean isGaseousFluid(Fluid fluid, FluidStack stack) {
        if (fluid == null) return false;
        if (fluid.isGaseous() || (stack != null && fluid.isGaseous(stack))) {
            return true;
        }
        String name = fluid.getName()
            .trim()
            .toLowerCase();
        if (name.startsWith("gas_") || name.endsWith("_gas") || name.endsWith(".gas")) {
            return true;
        }
        try {
            Materials mat = Materials.FLUID_MAP.get(fluid);
            if (mat != null && mat.mGas != null && mat.mGas == fluid) {
                return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    public static boolean isGaseousFluid(FluidStack stack) {
        if (stack == null || stack.getFluid() == null) return false;
        return isGaseousFluid(stack.getFluid(), stack);
    }

    public static boolean isMoltenFluid(Fluid fluid, FluidStack stack, CoolantFluidProperty prop) {
        if (isLava(fluid, stack)) {
            return true;
        }
        if (prop != null && prop.isMolten()) {
            return true;
        }
        if (stack != null && stack.getFluid() != null
            && stack.getFluid()
                .getName() != null
            && stack.getFluid()
                .getName()
                .toLowerCase()
                .contains("molten")) {
            return true;
        }
        if (fluid != null && fluid.getName() != null
            && fluid.getName()
                .toLowerCase()
                .contains("molten")) {
            return true;
        }
        return false;
    }

    public static boolean isMoltenFluid(FluidStack stack) {
        if (stack == null || stack.getFluid() == null) return false;
        return isMoltenFluid(
            stack.getFluid(),
            stack,
            CoolantFluidProperty.get(
                stack.getFluid()
                    .getName()));
    }

    public static double getDeclaredGregTechFluidTemperatureCelsius(Fluid fluid, FluidStack stack,
        CoolantFluidProperty prop) {
        if (fluid == null && stack != null) {
            fluid = stack.getFluid();
        }
        if (fluid == null && prop != null) {
            try {
                fluid = FluidRegistry.getFluid(prop.getFluidName());
            } catch (Throwable ignored) {}
        }
        if (fluid != null) {
            int tempK = (stack != null) ? fluid.getTemperature(stack) : fluid.getTemperature();
            if (tempK > 0) {
                return tempK - 273.15;
            }
        }
        if (prop != null) {
            return prop.getDeclaredTemperatureCelsius();
        }
        return 20.0;
    }

    public static double getFluidStackTemperatureCelsius(FluidStack stack) {
        if (stack == null || stack.getFluid() == null) return 20.0;
        if (stack.tag != null) {
            if (stack.tag.hasKey("temperature")) {
                int t = stack.tag.getInteger("temperature");
                return t > 200 ? (t - 273.15) : (double) t;
            }
            if (stack.tag.hasKey("Temperature")) {
                int t = stack.tag.getInteger("Temperature");
                return t > 200 ? (t - 273.15) : (double) t;
            }
            if (stack.tag.hasKey("temp")) {
                double t = stack.tag.getDouble("temp");
                return t > 200.0 ? (t - 273.15) : t;
            }
        }
        int tempK = stack.getFluid()
            .getTemperature(stack);
        return tempK - 273.15;
    }

    public static double calculateAmbientTemperature(World world, int x, int y, int z) {
        if (world != null) {
            try {
                float bTemp = world.getBiomeGenForCoords(x, z)
                    .getFloatTemperature(x, y, z);
                return (bTemp * 100.0 - 32.0) / 1.8;
            } catch (Exception ignored) {}
        }
        return 20.0;
    }
}
