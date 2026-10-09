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
