package com.gtnewhorizons.coolantloops.common.metatileentity.multi.util;

import java.util.Collections;
import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidHandler;

import com.gtnewhorizons.coolantloops.common.metatileentity.multi.MTECoolantPump;
import com.gtnewhorizons.coolantloops.common.util.CoolantFluidHelper;
import com.gtnewhorizons.coolantloops.engine.CoolantFluidProperty;
import com.gtnewhorizons.coolantloops.engine.LoopGraphCrawler;

import gregtech.api.enums.Materials;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import mods.railcraft.common.blocks.machine.MultiBlockPattern;
import mods.railcraft.common.blocks.machine.TileMultiBlock;
import mods.railcraft.common.blocks.machine.beta.TileTankBase;
import mods.railcraft.common.fluids.tanks.StandardTank;

public class CoolantPumpReservoir {

    private final MTECoolantPump pump;
    private final Map<String, Long> dissolvedGases = new java.util.LinkedHashMap<>();
    private long simulatedCoolantLiters = 100000L;
    private long simulatedTankCapacityLiters = 0L;

    public CoolantPumpReservoir(MTECoolantPump pump) {
        this.pump = pump;
    }

    public long getSimulatedCoolantLiters() {
        return simulatedCoolantLiters;
    }

    public void setSimulatedCoolantLiters(long simulatedCoolantLiters) {
        this.simulatedCoolantLiters = simulatedCoolantLiters;
    }

    public long getSimulatedTankCapacityLiters() {
        return simulatedTankCapacityLiters;
    }

    public void setSimulatedTankCapacityLiters(long simulatedTankCapacityLiters) {
        this.simulatedTankCapacityLiters = simulatedTankCapacityLiters;
    }

    public long getTotalTankCapacityLiters() {
        if (simulatedTankCapacityLiters > 0) {
            return simulatedTankCapacityLiters;
        }
        IGregTechTileEntity baseTE = pump.getBaseMetaTileEntity();
        if (baseTE != null && baseTE.getWorld() != null) {
            World world = baseTE.getWorld();
            int x0 = baseTE.getXCoord();
            int y0 = baseTE.getYCoord();
            int z0 = baseTE.getZCoord();
            ForgeDirection front = baseTE.getFrontFacing();
            ForgeDirection back = front != null && front != ForgeDirection.UNKNOWN ? front.getOpposite()
                : ForgeDirection.SOUTH;
            ForgeDirection right = front != null && front != ForgeDirection.UNKNOWN
                ? front.getRotation(ForgeDirection.UP)
                : ForgeDirection.WEST;
            for (int localX = 0; localX < pump.getWidth(); localX++) {
                for (int localZ = 0; localZ < pump.getWidth(); localZ++) {
                    int bx = x0 + right.offsetX * localX + back.offsetX * localZ;
                    int bz = z0 + right.offsetZ * localX + back.offsetZ * localZ;
                    for (int yOffset = 2; yOffset <= 3; yOffset++) {
                        int y = y0 + yOffset;
                        TileEntity tile = world.getTileEntity(bx, y, bz);
                        if (tile instanceof TileTankBase) {
                            TileTankBase tankTile = (TileTankBase) tile;
                            TileTankBase master = (TileTankBase) tankTile.getMasterBlock();
                            if (master != null && master.getTank() != null) {
                                return master.getTank()
                                    .getCapacity();
                            }
                        }
                        if (tile instanceof IFluidHandler) {
                            FluidTankInfo[] infos = ((IFluidHandler) tile).getTankInfo(ForgeDirection.DOWN);
                            if (infos != null && infos.length > 0 && infos[0] != null) {
                                return infos[0].capacity;
                            }
                        }
                    }
                }
            }
        }
        return 0L;
    }

    public static Materials getRailcraftTankMaterial(TileEntity te) {
        if (te == null) return null;
        if (te instanceof TileTankBase) {
            TileTankBase tankBase = (TileTankBase) te;
            try {
                mods.railcraft.common.blocks.machine.beta.MetalTank tankType = tankBase.getTankType();
                if (tankType instanceof mods.railcraft.common.blocks.machine.beta.SteelTank) {
                    return Materials.Steel;
                }
                if (tankType instanceof mods.railcraft.common.blocks.machine.beta.IronTank) {
                    return Materials.CastIron;
                }
                if (tankType instanceof mods.railcraft.common.blocks.machine.tank.GenericMultiTankBase) {
                    String mat = ((mods.railcraft.common.blocks.machine.tank.GenericMultiTankBase) tankType).tankMaterial;
                    if (mat != null) {
                        mat = mat.toLowerCase();
                        switch (mat) {
                            case "steel":
                                return Materials.Steel;
                            case "iron":
                                return Materials.CastIron;
                            case "stainless":
                                return Materials.StainlessSteel;
                            case "titanium":
                                return Materials.Titanium;
                            case "tungstensteel":
                                return Materials.TungstenSteel;
                            case "neutronium":
                                return Materials.Neutronium;
                            case "aluminium":
                            case "aluminum":
                                return Materials.Aluminium;
                            case "palladium":
                                return Materials.Palladium;
                            case "iridium":
                                return Materials.Iridium;
                            case "osmium":
                                return Materials.Osmium;
                            default:
                                return null;
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }
        String name = te.getClass()
            .getSimpleName()
            .toLowerCase();
        if (name.contains("steel") && !name.contains("stainless") && !name.contains("tungsten")) {
            return Materials.Steel;
        }
        if (name.contains("iron")) {
            return Materials.CastIron;
        }
        if (name.contains("stainless")) {
            return Materials.StainlessSteel;
        }
        if (name.contains("titanium")) {
            return Materials.Titanium;
        }
        if (name.contains("tungstensteel")) {
            return Materials.TungstenSteel;
        }
        if (name.contains("neutronium")) {
            return Materials.Neutronium;
        }
        if (name.contains("alumin")) {
            return Materials.Aluminium;
        }
        if (name.contains("palladium")) {
            return Materials.Palladium;
        }
        if (name.contains("iridium")) {
            return Materials.Iridium;
        }
        if (name.contains("osmium")) {
            return Materials.Osmium;
        }
        return null;
    }

    public boolean verifyRailcraftTankAbove(IGregTechTileEntity aBaseMetaTileEntity) {
        if (aBaseMetaTileEntity == null) return false;
        World world = aBaseMetaTileEntity.getWorld();
        if (world == null) {
            return simulatedCoolantLiters > 0;
        }

        ForgeDirection front = aBaseMetaTileEntity.getFrontFacing();
        ForgeDirection back = front != null && front != ForgeDirection.UNKNOWN ? front.getOpposite()
            : ForgeDirection.SOUTH;
        ForgeDirection right = front != null && front != ForgeDirection.UNKNOWN ? front.getRotation(ForgeDirection.UP)
            : ForgeDirection.WEST;

        int x0 = aBaseMetaTileEntity.getXCoord();
        int y0 = aBaseMetaTileEntity.getYCoord();
        int z0 = aBaseMetaTileEntity.getZCoord();
        int y = y0 + 2;

        TileMultiBlock masterBlock = null;

        int probeX = x0 + back.offsetX;
        int probeZ = z0 + back.offsetZ;
        TileEntity centerTe = world.getTileEntity(probeX, y, probeZ);
        if (centerTe instanceof TileMultiBlock) {
            TileMultiBlock tmbCenter = (TileMultiBlock) centerTe;
            if (!tmbCenter.isStructureValid()) {
                try {
                    java.lang.reflect.Method mTest = TileMultiBlock.class.getDeclaredMethod("testIfMasterBlock");
                    mTest.setAccessible(true);
                    mTest.invoke(tmbCenter);
                } catch (Throwable ignored) {}
            }
            masterBlock = tmbCenter.getMasterBlock();
        }

        if (masterBlock == null) {
            for (int dx = -4; dx <= 4; dx++) {
                TileEntity te = world
                    .getTileEntity(x0 + right.offsetX * dx + back.offsetX, y, z0 + right.offsetZ * dx + back.offsetZ);
                if (te instanceof TileMultiBlock) {
                    TileMultiBlock tmb = (TileMultiBlock) te;
                    if (!tmb.isStructureValid()) {
                        try {
                            java.lang.reflect.Method mTest = TileMultiBlock.class
                                .getDeclaredMethod("testIfMasterBlock");
                            mTest.setAccessible(true);
                            mTest.invoke(tmb);
                        } catch (Throwable ignored) {}
                    }
                    masterBlock = tmb.getMasterBlock();
                    if (masterBlock != null) break;
                }
            }
        }

        if (masterBlock == null) {
            pump.setLoopStatus("Missing Railcraft tank above pump!");
            return false;
        }

        Materials tankMat = getRailcraftTankMaterial(masterBlock);
        if (tankMat == null) {
            pump.setLoopStatus("Unrecognized Railcraft tank material above pump!");
            return false;
        }
        if (!LoopGraphCrawler.isAllowedCoolantPipeMaterial(tankMat)) {
            pump.setLoopStatus(
                String.format(
                    "Railcraft tank material %s has no matching GregTech fluid pipe! Allowed materials: Iron, Steel, Stainless Steel, Titanium, Tungstensteel, Neutronium.",
                    tankMat.mDefaultLocalName));
            return false;
        }
        pump.setTankMaterial(tankMat);

        MultiBlockPattern pattern = masterBlock.getPattern();
        if (pattern == null) {
            pump.setLoopStatus("Railcraft tank has invalid pattern!");
            return false;
        }
        int tankWidthX = pattern.getPatternWidthX();
        int tankWidthZ = pattern.getPatternWidthZ();
        int effX = (tankWidthX > 2) ? (tankWidthX - 2) : tankWidthX;
        int effZ = (tankWidthZ > 2) ? (tankWidthZ - 2) : tankWidthZ;

        int detectedWidth = effX;
        if (detectedWidth != 3 && detectedWidth != 5 && detectedWidth != 7 && detectedWidth != 9) {
            pump.setLoopStatus(
                String.format("Unsupported Railcraft tank width %d (must be 3, 5, 7, or 9)!", detectedWidth));
            return false;
        }
        if (effX != effZ) {
            pump.setLoopStatus(String.format("Railcraft tank footprint must be square (%dx%d detected)!", effX, effZ));
            return false;
        }

        pump.setWidth(detectedWidth);
        pump.setLength(detectedWidth);
        int R = detectedWidth / 2;

        for (int localX = -R; localX <= R; localX++) {
            for (int localZ = 0; localZ < detectedWidth; localZ++) {
                int bx = x0 + right.offsetX * localX + back.offsetX * localZ;
                int bz = z0 + right.offsetZ * localX + back.offsetZ * localZ;
                TileEntity te = world.getTileEntity(bx, y, bz);
                if (te == null) {
                    pump.setLoopStatus(String.format("Missing Railcraft tank block at (%d, %d, %d)", bx, y, bz));
                    return false;
                }
                if (!(te instanceof TileMultiBlock)) {
                    pump.setLoopStatus(
                        String.format(
                            "Block at (%d, %d, %d) is %s, not a Railcraft tank block",
                            bx,
                            y,
                            bz,
                            te.getClass()
                                .getSimpleName()));
                    return false;
                }
                TileMultiBlock tmb = (TileMultiBlock) te;
                if (!tmb.isStructureValid()) {
                    pump.setLoopStatus("Railcraft tank above pump is not fully formed/structurally valid!");
                    return false;
                }
                if (tmb.getMasterBlock() != masterBlock) {
                    pump.setLoopStatus(
                        "Mismatched Railcraft tank: multiple distinct tank multiblocks detected above pump!");
                    return false;
                }
            }
        }

        FluidStack availableFluid = getReservoirFluid();
        if (availableFluid == null || availableFluid.amount <= 0 || availableFluid.getFluid() == null) {
            pump.setLoopStatus("Railcraft tank is empty! Coolant fluid required before pump can operate.");
            return false;
        }

        if (CoolantFluidHelper.isLiquidNuclearFuel(availableFluid)) {
            pump.failOnLiquidNuclearFuel(
                availableFluid.getFluid()
                    .getName());
            return false;
        }

        if (CoolantFluidHelper.isPlainRegularWater(availableFluid)) {
            pump.setLoopStatus(
                "Pump refused to start: Plain regular water cannot be used in a coolant loop! Use Distilled Water.");
            return false;
        }

        if (CoolantFluidHelper.isGaseousFluid(availableFluid)) {
            pump.setLoopStatus(
                "Pump refused to start: Gaseous fluid (" + availableFluid.getFluid()
                    .getName() + ") cannot be used as a coolant!");
            return false;
        }

        if (CoolantFluidHelper.isLava(availableFluid)) {
            if (!CoolantFluidHelper.isNetherWorld(world)) {
                pump.setLoopStatus("Pump refused to start: Lava is too viscous here!");
                return false;
            }
        }

        CoolantFluidProperty prop = CoolantFluidProperty.get(
            availableFluid.getFluid()
                .getName());
        if (prop == null || prop.isPlainWater()) {
            pump.setLoopStatus(
                "Pump refused to start: Plain regular water cannot be used in a coolant loop! Use Distilled Water.");
            return false;
        }

        if (CoolantFluidHelper.isMoltenFluid(availableFluid.getFluid(), availableFluid, prop)) {
            if (!(CoolantFluidHelper.isLava(availableFluid) && CoolantFluidHelper.isNetherWorld(world))) {
                double declaredMelting = CoolantFluidHelper
                    .getDeclaredGregTechFluidTemperatureCelsius(availableFluid.getFluid(), availableFluid, prop);
                double biomeTemp = pump.getBiomeTemperatureCelsius();
                if (biomeTemp < declaredMelting) {
                    pump.setLoopStatus(
                        String.format(
                            "Pump refused to start: Biome ambient temperature (%.1f °C) at pump is below declared GregTech melting point (%.1f °C) for %s! Fluid would solidify in this biome.",
                            biomeTemp,
                            declaredMelting,
                            prop.getFluidName()));
                    return false;
                }
            }
        }
        pump.getEngine()
            .setFluid(prop);
        return true;
    }

    public FluidStack getReservoirFluid() {
        IGregTechTileEntity te = pump.getBaseMetaTileEntity();
        if (te != null && te.getWorld() != null) {
            World world = te.getWorld();
            ForgeDirection front = te.getFrontFacing();
            ForgeDirection back = front != null && front != ForgeDirection.UNKNOWN ? front.getOpposite()
                : ForgeDirection.SOUTH;
            ForgeDirection right = front != null && front != ForgeDirection.UNKNOWN
                ? front.getRotation(ForgeDirection.UP)
                : ForgeDirection.WEST;
            int x0 = te.getXCoord();
            int y0 = te.getYCoord();
            int z0 = te.getZCoord();
            int R = pump.getWidth() / 2;

            for (int localX = -R; localX <= R; localX++) {
                for (int localZ = 0; localZ < pump.getWidth(); localZ++) {
                    int bx = x0 + right.offsetX * localX + back.offsetX * localZ;
                    int bz = z0 + right.offsetZ * localX + back.offsetZ * localZ;
                    TileEntity tile = world.getTileEntity(bx, y0 + 2, bz);
                    if (tile instanceof TileTankBase) {
                        TileTankBase tankTile = (TileTankBase) tile;
                        TileTankBase master = (TileTankBase) tankTile.getMasterBlock();
                        if (master != null && master.getTank() != null
                            && master.getTank()
                                .getFluid() != null) {
                            return master.getTank()
                                .getFluid();
                        }
                    }
                    if (tile instanceof IFluidHandler) {
                        FluidStack drained = ((IFluidHandler) tile).drain(ForgeDirection.DOWN, 1, false);
                        if (drained != null && drained.amount > 0 && drained.getFluid() != null) {
                            return drained;
                        }
                    }
                }
            }
        }
        return null;
    }

    public int drainFromReservoir(int liters) {
        if (liters <= 0) return 0;
        IGregTechTileEntity te = pump.getBaseMetaTileEntity();
        if (te != null && te.getWorld() != null) {
            World world = te.getWorld();
            ForgeDirection front = te.getFrontFacing();
            ForgeDirection back = front != null && front != ForgeDirection.UNKNOWN ? front.getOpposite()
                : ForgeDirection.SOUTH;
            ForgeDirection right = front != null && front != ForgeDirection.UNKNOWN
                ? front.getRotation(ForgeDirection.UP)
                : ForgeDirection.WEST;
            int x0 = te.getXCoord();
            int y0 = te.getYCoord();
            int z0 = te.getZCoord();
            int R = pump.getWidth() / 2;

            for (int localX = -R; localX <= R; localX++) {
                for (int localZ = 0; localZ < pump.getWidth(); localZ++) {
                    int bx = x0 + right.offsetX * localX + back.offsetX * localZ;
                    int bz = z0 + right.offsetZ * localX + back.offsetZ * localZ;
                    for (int yOffset = 2; yOffset <= 3; yOffset++) {
                        int y = y0 + yOffset;
                        TileEntity tile = world.getTileEntity(bx, y, bz);
                        if (tile instanceof TileTankBase) {
                            TileTankBase tankTile = (TileTankBase) tile;
                            TileTankBase master = (TileTankBase) tankTile.getMasterBlock();
                            if (master != null && master.getTank() != null) {
                                FluidStack drained = master.getTank()
                                    .drain(liters, true);
                                if (drained != null && drained.amount > 0) {
                                    return drained.amount;
                                }
                            }
                        }
                        if (tile instanceof IFluidHandler) {
                            FluidStack drained = ((IFluidHandler) tile).drain(ForgeDirection.DOWN, liters, true);
                            if (drained != null && drained.amount > 0) {
                                return drained.amount;
                            }
                        }
                    }
                }
            }
        }

        if (simulatedCoolantLiters > 0) {
            int drained = (int) Math.min(liters, simulatedCoolantLiters);
            simulatedCoolantLiters -= drained;
            return drained;
        }
        return 0;
    }

    public int fillIntoReservoir(int liters) {
        if (liters <= 0) return 0;
        IGregTechTileEntity te = pump.getBaseMetaTileEntity();
        if (te != null && te.getWorld() != null) {
            World world = te.getWorld();
            ForgeDirection front = te.getFrontFacing();
            ForgeDirection back = front != null && front != ForgeDirection.UNKNOWN ? front.getOpposite()
                : ForgeDirection.SOUTH;
            ForgeDirection right = front != null && front != ForgeDirection.UNKNOWN
                ? front.getRotation(ForgeDirection.UP)
                : ForgeDirection.WEST;
            int x0 = te.getXCoord();
            int y0 = te.getYCoord();
            int z0 = te.getZCoord();
            int R = pump.getWidth() / 2;

            for (int localX = -R; localX <= R; localX++) {
                for (int localZ = 0; localZ < pump.getWidth(); localZ++) {
                    int bx = x0 + right.offsetX * localX + back.offsetX * localZ;
                    int bz = z0 + right.offsetZ * localX + back.offsetZ * localZ;
                    for (int yOffset = 2; yOffset <= 3; yOffset++) {
                        int y = y0 + yOffset;
                        TileEntity tile = world.getTileEntity(bx, y, bz);
                        if (tile instanceof TileTankBase) {
                            TileTankBase tankTile = (TileTankBase) tile;
                            TileTankBase master = (TileTankBase) tankTile.getMasterBlock();
                            if (master != null && master.getTank() != null) {
                                StandardTank stdTank = master.getTank();
                                int space = stdTank.getCapacity() - stdTank.getFluidAmount();
                                if (space <= 0) {
                                    return 0;
                                }
                                int toFill = Math.min(liters, space);
                                String fluidName = pump.getEngine()
                                    .getFluid() != null ? pump.getEngine()
                                        .getFluid()
                                        .getFluidName() : "ic2distilledwater";
                                net.minecraftforge.fluids.Fluid f = FluidRegistry.getFluid(fluidName);
                                if (f != null) {
                                    FluidStack stack = new FluidStack(f, toFill);
                                    int filled = stdTank.fill(stack, true);
                                    if (filled > 0) return filled;
                                }
                            }
                        }
                        if (tile instanceof IFluidHandler) {
                            String fluidName = pump.getEngine()
                                .getFluid() != null ? pump.getEngine()
                                    .getFluid()
                                    .getFluidName() : "ic2distilledwater";
                            net.minecraftforge.fluids.Fluid f = FluidRegistry.getFluid(fluidName);
                            if (f != null) {
                                FluidStack stack = new FluidStack(f, liters);
                                int filled = ((IFluidHandler) tile).fill(ForgeDirection.DOWN, stack, true);
                                if (filled > 0) return filled;
                            }
                        }
                    }
                }
            }
        }

        if (simulatedTankCapacityLiters > 0) {
            long space = Math.max(0, simulatedTankCapacityLiters - simulatedCoolantLiters);
            int filled = (int) Math.min(liters, space);
            simulatedCoolantLiters += filled;
            return filled;
        } else {
            simulatedCoolantLiters += liters;
            return liters;
        }
    }

    public boolean isReservoirFull() {
        IGregTechTileEntity te = pump.getBaseMetaTileEntity();
        if (te != null && te.getWorld() != null) {
            World world = te.getWorld();
            ForgeDirection front = te.getFrontFacing();
            ForgeDirection back = front != null && front != ForgeDirection.UNKNOWN ? front.getOpposite()
                : ForgeDirection.SOUTH;
            ForgeDirection right = front != null && front != ForgeDirection.UNKNOWN
                ? front.getRotation(ForgeDirection.UP)
                : ForgeDirection.WEST;
            int x0 = te.getXCoord();
            int y0 = te.getYCoord();
            int z0 = te.getZCoord();
            int R = pump.getWidth() / 2;

            for (int localX = -R; localX <= R; localX++) {
                for (int localZ = 0; localZ < pump.getWidth(); localZ++) {
                    int bx = x0 + right.offsetX * localX + back.offsetX * localZ;
                    int bz = z0 + right.offsetZ * localX + back.offsetZ * localZ;
                    for (int yOffset = 2; yOffset <= 3; yOffset++) {
                        int y = y0 + yOffset;
                        TileEntity tile = world.getTileEntity(bx, y, bz);
                        if (tile instanceof TileTankBase) {
                            TileTankBase tankTile = (TileTankBase) tile;
                            TileTankBase master = (TileTankBase) tankTile.getMasterBlock();
                            if (master != null && master.getTank() != null) {
                                return master.getTank()
                                    .getFluidAmount()
                                    >= master.getTank()
                                        .getCapacity();
                            }
                        }
                        if (tile instanceof IFluidHandler) {
                            FluidTankInfo[] infos = ((IFluidHandler) tile).getTankInfo(ForgeDirection.DOWN);
                            if (infos != null && infos.length > 0 && infos[0] != null) {
                                int amt = infos[0].fluid != null ? infos[0].fluid.amount : 0;
                                return amt >= infos[0].capacity;
                            }
                        }
                    }
                }
            }
        }

        if (simulatedTankCapacityLiters > 0) {
            return simulatedCoolantLiters >= simulatedTankCapacityLiters;
        }
        return false;
    }

    public void addDissolvedGas(String gasName, long liters) {
        if (gasName == null || liters <= 0) return;
        dissolvedGases.merge(gasName, liters, Long::sum);
    }

    public double getDissolvedGasFraction() {
        long totalGas = 0;
        for (long val : dissolvedGases.values()) {
            totalGas += val;
        }
        long totalLiters = getTotalCoolantLiters();
        if (totalLiters <= 0) return 0.0;
        double frac = (double) totalGas / (double) totalLiters;
        return Math.min(1.0, Math.max(0.0, frac));
    }

    public long getDissolvedGasAmount(String gasName) {
        if (gasName == null) return 0L;
        return dissolvedGases.getOrDefault(gasName, 0L);
    }

    public Map<String, Long> getDissolvedGases() {
        return Collections.unmodifiableMap(dissolvedGases);
    }

    public synchronized long extractDissolvedGas(String gasName, long maxLiters) {
        if (gasName == null || maxLiters <= 0) return 0L;
        String matchedKey = null;
        for (String k : dissolvedGases.keySet()) {
            if (k.equalsIgnoreCase(gasName)) {
                matchedKey = k;
                break;
            }
        }
        if (matchedKey == null) return 0L;
        long current = dissolvedGases.getOrDefault(matchedKey, 0L);
        long toExtract = Math.min(current, maxLiters);
        if (toExtract > 0) {
            long remaining = current - toExtract;
            if (remaining > 0) {
                dissolvedGases.put(matchedKey, remaining);
            } else {
                dissolvedGases.remove(matchedKey);
            }
        }
        return toExtract;
    }

    public long getTotalCoolantLiters() {
        IGregTechTileEntity te = pump.getBaseMetaTileEntity();
        if (te != null && te.getWorld() != null) {
            World world = te.getWorld();
            ForgeDirection front = te.getFrontFacing();
            ForgeDirection back = front != null && front != ForgeDirection.UNKNOWN ? front.getOpposite()
                : ForgeDirection.SOUTH;
            ForgeDirection right = front != null && front != ForgeDirection.UNKNOWN
                ? front.getRotation(ForgeDirection.UP)
                : ForgeDirection.WEST;
            int x0 = te.getXCoord();
            int y0 = te.getYCoord();
            int z0 = te.getZCoord();
            int R = pump.getWidth() / 2;

            for (int localX = -R; localX <= R; localX++) {
                for (int localZ = 0; localZ < pump.getWidth(); localZ++) {
                    int bx = x0 + right.offsetX * localX + back.offsetX * localZ;
                    int bz = z0 + right.offsetZ * localX + back.offsetZ * localZ;
                    for (int yOffset = 2; yOffset <= 3; yOffset++) {
                        int y = y0 + yOffset;
                        TileEntity tile = world.getTileEntity(bx, y, bz);
                        if (tile instanceof TileTankBase) {
                            TileTankBase master = (TileTankBase) ((TileTankBase) tile).getMasterBlock();
                            if (master != null && master.getTank() != null) {
                                return master.getTank()
                                    .getFluidAmount();
                            }
                        }
                        if (tile instanceof IFluidHandler) {
                            FluidTankInfo[] infos = ((IFluidHandler) tile).getTankInfo(ForgeDirection.DOWN);
                            if (infos != null) {
                                long sum = 0;
                                for (FluidTankInfo info : infos) {
                                    if (info != null && info.fluid != null) {
                                        sum += info.fluid.amount;
                                    }
                                }
                                if (sum > 0) return sum;
                            }
                        }
                    }
                }
            }
        }
        return simulatedCoolantLiters;
    }

    public void saveNBTData(NBTTagCompound aNBT) {
        NBTTagCompound gasesTag = new NBTTagCompound();
        for (Map.Entry<String, Long> entry : dissolvedGases.entrySet()) {
            gasesTag.setLong(entry.getKey(), entry.getValue());
        }
        aNBT.setTag("mDissolvedGases", gasesTag);
        aNBT.setLong("mSimulatedCoolantLiters", simulatedCoolantLiters);
        aNBT.setLong("mSimulatedTankCapacityLiters", simulatedTankCapacityLiters);
    }

    public void loadNBTData(NBTTagCompound aNBT) {
        dissolvedGases.clear();
        if (aNBT.hasKey("mDissolvedGases")) {
            NBTTagCompound gasesTag = aNBT.getCompoundTag("mDissolvedGases");
            for (Object keyObj : gasesTag.func_150296_c()) {
                String key = (String) keyObj;
                dissolvedGases.put(key, gasesTag.getLong(key));
            }
        }
        if (aNBT.hasKey("mSimulatedCoolantLiters")) {
            simulatedCoolantLiters = aNBT.getLong("mSimulatedCoolantLiters");
        }
        if (aNBT.hasKey("mSimulatedTankCapacityLiters")) {
            simulatedTankCapacityLiters = aNBT.getLong("mSimulatedTankCapacityLiters");
        }
    }
}
