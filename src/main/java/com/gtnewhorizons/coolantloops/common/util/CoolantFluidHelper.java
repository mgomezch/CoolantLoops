package com.gtnewhorizons.coolantloops.common.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.world.World;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizons.coolantloops.common.config.CoolantLoopDataRegistry;
import com.gtnewhorizons.coolantloops.engine.CoolantFluidProperty;

import gregtech.api.enums.Materials;

public final class CoolantFluidHelper {

    private static final Map<Fluid, Boolean> sWaterCache = new ConcurrentHashMap<>();
    private static final Map<Fluid, Boolean> sLavaCache = new ConcurrentHashMap<>();
    private static final Map<Fluid, Boolean> sGasCache = new ConcurrentHashMap<>();
    private static final Map<Fluid, Boolean> sNuclearFuelFluidCache = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> sNuclearFuelNameCache = new ConcurrentHashMap<>();

    public static void clearCaches() {
        sWaterCache.clear();
        sLavaCache.clear();
        sGasCache.clear();
        sNuclearFuelFluidCache.clear();
        sNuclearFuelNameCache.clear();
    }

    private CoolantFluidHelper() {}

    public static boolean isPlainRegularWater(Fluid fluid) {
        if (fluid == null) return false;
        Boolean cached = sWaterCache.get(fluid);
        if (cached != null) return cached;
        String name = fluid.getName()
            .trim()
            .toLowerCase();
        boolean res = name.equals("water") || name.equals("minecraft:water") || name.equals("fluid.water");
        sWaterCache.put(fluid, res);
        return res;
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
        Boolean cached = sLavaCache.get(fluid);
        if (cached != null) return cached;
        boolean res = isLava(fluid.getName());
        sLavaCache.put(fluid, res);
        return res;
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
        Boolean cached = sNuclearFuelNameCache.get(name);
        if (cached != null) return cached;
        boolean res = computeIsLiquidNuclearFuel(name);
        sNuclearFuelNameCache.put(name, res);
        return res;
    }

    private static boolean computeIsLiquidNuclearFuel(String name) {
        if (CoolantLoopDataRegistry.getFluidData() != null) {
            return CoolantLoopDataRegistry.getFluidData()
                .isLiquidNuclearFuel(name);
        }
        String clean = name.trim()
            .toLowerCase();
        return clean.equals("liquidfuel") || clean.equals("liquid_fuel")
            || clean.equals("nuclearfuel")
            || clean.equals("uraniumhexafluoride")
            || clean.equals("uraniumtetrafluoride")
            || clean.equals("thoriumbasedliquidfuel")
            || clean.equals("uraniumbasedliquidfuel")
            || clean.equals("plutoniumbasedliquidfuel")
            || clean.equals("naquadahbasedliquidfuel")
            || clean.equals("liquid_naquadah_fuel")
            || clean.equals("depletedthoriumbasedliquidfuel")
            || clean.equals("depleteduraniumbasedliquidfuel")
            || clean.equals("depletedplutoniumbasedliquidfuel")
            || clean.equals("depletednaquadahbasedliquidfuel");
    }

    public static boolean isLiquidNuclearFuel(Fluid fluid, FluidStack stack) {
        if (fluid == null && stack != null) {
            fluid = stack.getFluid();
        }
        if (fluid == null) return false;
        Boolean cached = sNuclearFuelFluidCache.get(fluid);
        if (cached != null) return cached;
        boolean res = isLiquidNuclearFuel(fluid.getName());
        if (!res) {
            try {
                Materials mat = Materials.FLUID_MAP.get(fluid);
                if (mat != null && mat.mName != null && isLiquidNuclearFuel(mat.mName)) {
                    res = true;
                }
            } catch (Throwable ignored) {}
        }
        sNuclearFuelFluidCache.put(fluid, res);
        return res;
    }

    public static boolean isLiquidNuclearFuel(FluidStack stack) {
        if (stack == null || stack.getFluid() == null) return false;
        return isLiquidNuclearFuel(stack.getFluid(), stack);
    }

    public static boolean isGaseousFluid(Fluid fluid, FluidStack stack) {
        if (fluid == null && stack != null) {
            fluid = stack.getFluid();
        }
        if (fluid == null) return false;
        Boolean cached = sGasCache.get(fluid);
        if (cached != null) return cached;
        boolean res = fluid.isGaseous() || (stack != null && fluid.isGaseous(stack));
        if (!res) {
            String name = fluid.getName()
                .trim()
                .toLowerCase();
            if (name.startsWith("gas_") || name.endsWith("_gas") || name.endsWith(".gas")) {
                res = true;
            } else {
                try {
                    Materials mat = Materials.FLUID_MAP.get(fluid);
                    if (mat != null && mat.mGas != null && mat.mGas == fluid) {
                        res = true;
                    }
                } catch (Throwable ignored) {}
            }
        }
        sGasCache.put(fluid, res);
        return res;
    }

    public static boolean isGaseousFluid(FluidStack stack) {
        if (stack == null || stack.getFluid() == null) return false;
        return isGaseousFluid(stack.getFluid(), stack);
    }

    public static boolean isMoltenFluid(Fluid fluid, FluidStack stack, CoolantFluidProperty prop) {
        if (isLava(fluid, stack)) {
            return true;
        }
        if (prop != null) {
            return prop.isMolten();
        }
        if (fluid != null) {
            CoolantFluidProperty p = CoolantFluidProperty.get(fluid.getName());
            if (p != null) {
                return p.isMolten();
            }
        }
        if (stack != null && stack.getFluid() != null) {
            CoolantFluidProperty p = CoolantFluidProperty.get(
                stack.getFluid()
                    .getName());
            if (p != null) {
                return p.isMolten();
            }
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

    public static double getBaselineInletTemperatureCelsius(Fluid fluid, FluidStack stack, CoolantFluidProperty prop,
        double ambientTemp) {
        if (isMoltenFluid(fluid, stack, prop)) {
            double declaredMelting = getDeclaredGregTechFluidTemperatureCelsius(fluid, stack, prop);
            return Math.max(ambientTemp, declaredMelting);
        }
        return ambientTemp;
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
