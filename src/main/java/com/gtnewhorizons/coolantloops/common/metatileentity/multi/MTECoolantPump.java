package com.gtnewhorizons.coolantloops.common.metatileentity.multi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidHandler;

import com.gtnewhorizon.structurelib.StructureLibAPI;
import com.gtnewhorizon.structurelib.alignment.constructable.ChannelDataAccessor;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.AutoPlaceEnvironment;
import com.gtnewhorizon.structurelib.structure.IItemSource;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.gtnewhorizon.structurelib.structure.IStructureElement.BlocksToPlace;
import com.gtnewhorizon.structurelib.structure.IStructureElement.PlaceResult;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureUtility;
import com.gtnewhorizons.coolantloops.common.metatileentity.CoolantStructureChannels;
import com.gtnewhorizons.coolantloops.common.metatileentity.hatch.MTEHatchPressurizedFluid;
import com.gtnewhorizons.coolantloops.common.tileentity.TileEntityLoopInstrument;
import com.gtnewhorizons.coolantloops.common.tileentity.TileEntityManifold;
import com.gtnewhorizons.coolantloops.engine.CoolantFluidProperty;
import com.gtnewhorizons.coolantloops.engine.CoolantLoopEngine;
import com.gtnewhorizons.coolantloops.engine.CoolantPipingRegistry;
import com.gtnewhorizons.coolantloops.engine.ICoolantLoopDevice;
import com.gtnewhorizons.coolantloops.engine.ICoolantLoopPump;
import com.gtnewhorizons.coolantloops.engine.ICoolantPassageHatch;
import com.gtnewhorizons.coolantloops.engine.LoopGraphCrawler;
import com.gtnewhorizons.coolantloops.engine.LoopSegment;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.Materials;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.items.MetaGeneratedTool;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.api.metatileentity.implementations.MTEHatchEnergy;
import gregtech.api.metatileentity.implementations.MTEHatchMaintenance;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTStructureUtility;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.api.util.TurbineStatCalculator;
import gregtech.common.blocks.BlockFrameBox;
import mods.railcraft.common.blocks.machine.MultiBlockPattern;
import mods.railcraft.common.blocks.machine.TileMultiBlock;
import mods.railcraft.common.blocks.machine.beta.EnumMachineBeta;
import mods.railcraft.common.blocks.machine.beta.TileTankBase;
import mods.railcraft.common.fluids.tanks.StandardTank;
import mods.railcraft.common.modules.ModuleAdvancedTanks;

/**
 * Multiblock Coolant Loop Pump.
 *
 * Positions directly underneath a Railcraft multiblock tank.
 * Solves the closed loop hydrodynamics and advective heat transport.
 */
public class MTECoolantPump extends MTEEnhancedMultiBlockBase<MTECoolantPump>
    implements ICoolantLoopPump, ISurvivalConstructable {

    // Multiblock dimensions (dynamic footprint W x L, fixed height 2)
    protected int mWidth = 3;
    protected int mLength = 3;
    protected final int mHeight = 2;

    // Hatches
    public MTEHatchPressurizedFluid mDischargeHatch = null;
    public MTEHatchPressurizedFluid mSuctionHatch = null;

    // Simulation Engine
    protected CoolantLoopEngine mEngine = new CoolantLoopEngine(CoolantFluidProperty.DISTILLED_WATER);
    protected List<ICoolantLoopDevice> mLoopDevices = new ArrayList<>();
    protected boolean mLoopFormed = false;
    protected String mLoopStatus = "Waiting for loop formation...";
    protected Materials mTankMaterial = Materials.Steel;

    // Impeller slot
    protected ItemStack mRotorStack = null;
    protected double mRotorEfficiency = 0.85;

    public enum LoopState {
        EMPTY,
        FILLING,
        FILLED,
        CIRCULATING,
        DECELERATING,
        DRAINING,
        STOPPED,
        FAILED
    }

    // Loop State & Initial Filling Phase
    protected LoopState mLoopState = LoopState.EMPTY;
    protected long mCurrentFillLiters = 0L;
    protected long mRequiredFillLiters = 0L;
    protected long mFillRateLitersPerTick = 1000L;

    // Byproduct gases and coolant reservoir state
    protected final Map<String, Long> mDissolvedGases = new LinkedHashMap<>();
    protected long mSimulatedCoolantLiters = 100000L;
    protected long mSimulatedTankCapacityLiters = 200000L;
    protected boolean mHasEverBeenEnabled = false;

    public boolean isDischargeHatchDisabled() {
        if (mDischargeHatch == null) return false;
        return !mDischargeHatch.isAllowedToWork();
    }

    public long getSimulatedTankCapacityLiters() {
        return mSimulatedTankCapacityLiters;
    }

    public void setSimulatedTankCapacityLiters(long capacity) {
        this.mSimulatedTankCapacityLiters = capacity;
    }

    public long getSimulatedCoolantLiters() {
        return mSimulatedCoolantLiters;
    }

    public MTECoolantPump(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public MTECoolantPump(String aName) {
        super(aName);
    }

    @Override
    public boolean shouldCheckMaintenance() {
        return true;
    }

    public double getEfficiencyFactor() {
        int eff = getCurrentEfficiency(null);
        if (eff < 0) eff = 0;
        if (eff > 10000) eff = 10000;
        return eff / 10000.0;
    }

    @Override
    protected void onStructureCheckFinished(IGregTechTileEntity aBaseMetaTileEntity) {
        super.onStructureCheckFinished(aBaseMetaTileEntity);
        if (mMachine) {
            if (aBaseMetaTileEntity != null && !aBaseMetaTileEntity.isAllowedToWork() && !mHasEverBeenEnabled) {
                aBaseMetaTileEntity.enableWorking();
            }
            if (mDischargeHatch != null && !mHasEverBeenEnabled) {
                IGregTechTileEntity bte = mDischargeHatch.getBaseMetaTileEntity();
                if (bte != null && !bte.isAllowedToWork()) {
                    bte.enableWorking();
                }
            }
            mHasEverBeenEnabled = true;
        }
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTECoolantPump(mName);
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection facing,
        int aColorIndex, boolean aActive, boolean aRedstone) {
        if (side == facing) {
            return new ITexture[] { TextureFactory.of(GregTechAPI.sBlockCasings4, 2),
                TextureFactory.of(
                    aActive ? Textures.BlockIcons.OVERLAY_FRONT_DIESEL_ENGINE_ACTIVE
                        : Textures.BlockIcons.OVERLAY_FRONT_DIESEL_ENGINE) };
        }
        return new ITexture[] { TextureFactory.of(GregTechAPI.sBlockCasings4, 2) };
    }

    @Override
    public boolean checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack) {
        mDischargeHatch = null;
        mSuctionHatch = null;
        mEnergyHatches.clear();
        mMaintenanceHatches.clear();

        // Check Railcraft tank presence above pump
        boolean hasValidTank = verifyRailcraftTankAbove(aBaseMetaTileEntity);
        if (!hasValidTank) {
            if (mLoopStatus == null || mLoopStatus.isEmpty()
                || mLoopStatus.equals("Pump structure formed successfully")) {
                mLoopStatus = "Missing or mismatched Railcraft tank above pump!";
            }
            return false;
        }

        int x = aBaseMetaTileEntity.getXCoord();
        int y = aBaseMetaTileEntity.getYCoord();
        int z = aBaseMetaTileEntity.getZCoord();
        World world = aBaseMetaTileEntity.getWorld();

        if (world != null) {
            ForgeDirection front = aBaseMetaTileEntity.getFrontFacing();
            ForgeDirection back = front != null && front != ForgeDirection.UNKNOWN ? front.getOpposite()
                : ForgeDirection.SOUTH;
            ForgeDirection right = front != null && front != ForgeDirection.UNKNOWN
                ? front.getRotation(ForgeDirection.UP)
                : ForgeDirection.WEST;
            int R = mWidth / 2;

            // 1. Verify four corners on both layer 0 and layer 1 are Frame Boxes of mTankMaterial
            int[][] cornerOffsets = { { -R, 0 }, { R, 0 }, { -R, mWidth - 1 }, { R, mWidth - 1 } };
            for (int[] corner : cornerOffsets) {
                int cx = x + right.offsetX * corner[0] + back.offsetX * corner[1];
                int cz = z + right.offsetZ * corner[0] + back.offsetZ * corner[1];
                for (int ly = 0; ly < 2; ly++) {
                    int cy = y + ly;
                    Block block = world.getBlock(cx, cy, cz);
                    if (!(block instanceof BlockFrameBox)) {
                        mLoopStatus = String.format("Corner block at (%d, %d, %d) must be a Frame Box!", cx, cy, cz);
                        return false;
                    }
                    int meta = world.getBlockMetadata(cx, cy, cz);
                    Materials frameMat = BlockFrameBox.getMaterial(meta);
                    if (!CoolantPipingRegistry.areMaterialsEqual(frameMat, mTankMaterial)) {
                        mLoopStatus = String.format(
                            "Corner block at (%d, %d, %d) is %s Frame Box, but must match tank material %s!",
                            cx,
                            cy,
                            cz,
                            frameMat != null ? frameMat.mDefaultLocalName : "unknown",
                            mTankMaterial.mDefaultLocalName);
                        return false;
                    }
                }
            }

            // 2. Scan footprint on both layers for hatches and casings
            List<MTEHatchPressurizedFluid> pressHatches = new ArrayList<>();
            for (int localX = -R; localX <= R; localX++) {
                for (int localZ = 0; localZ < mWidth; localZ++) {
                    // Skip corners (already verified)
                    if ((localX == -R || localX == R) && (localZ == 0 || localZ == mWidth - 1)) {
                        continue;
                    }
                    int bx = x + right.offsetX * localX + back.offsetX * localZ;
                    int bz = z + right.offsetZ * localX + back.offsetZ * localZ;
                    for (int ly = 0; ly < 2; ly++) {
                        // Skip controller position
                        if (localX == 0 && localZ == 0 && ly == 0) {
                            continue;
                        }
                        int by = y + ly;
                        TileEntity te = world.getTileEntity(bx, by, bz);
                        if (te instanceof IGregTechTileEntity) {
                            IGregTechTileEntity gte = (IGregTechTileEntity) te;
                            IMetaTileEntity mte = gte.getMetaTileEntity();
                            if (mte instanceof MTEHatchPressurizedFluid) {
                                MTEHatchPressurizedFluid ph = (MTEHatchPressurizedFluid) mte;
                                if (!pressHatches.contains(ph)) {
                                    pressHatches.add(ph);
                                }
                            } else if (mte instanceof MTEHatchMaintenance) {
                                if (!mMaintenanceHatches.contains(mte)) {
                                    mMaintenanceHatches.add((MTEHatchMaintenance) mte);
                                }
                            } else if (mte instanceof MTEHatchEnergy) {
                                if (!mEnergyHatches.contains(mte)) {
                                    mEnergyHatches.add((MTEHatchEnergy) mte);
                                }
                            }
                        }
                    }
                }
            }

            for (MTEHatchPressurizedFluid ph : pressHatches) {
                if (ph.getMode() == MTEHatchPressurizedFluid.HatchMode.DISCHARGE && mDischargeHatch == null) {
                    mDischargeHatch = ph;
                } else if (ph.getMode() == MTEHatchPressurizedFluid.HatchMode.SUCTION && mSuctionHatch == null) {
                    mSuctionHatch = ph;
                }
            }
            if (mDischargeHatch == null || mSuctionHatch == null) {
                for (MTEHatchPressurizedFluid ph : pressHatches) {
                    IGregTechTileEntity bte = ph.getBaseMetaTileEntity();
                    if (bte.getXCoord() > x && mDischargeHatch == null) {
                        mDischargeHatch = ph;
                        ph.setMode(MTEHatchPressurizedFluid.HatchMode.DISCHARGE);
                    } else if (bte.getXCoord() < x && mSuctionHatch == null) {
                        mSuctionHatch = ph;
                        ph.setMode(MTEHatchPressurizedFluid.HatchMode.SUCTION);
                    }
                }
            }
            if (mDischargeHatch == null && !pressHatches.isEmpty()) {
                mDischargeHatch = pressHatches.get(0);
                mDischargeHatch.setMode(MTEHatchPressurizedFluid.HatchMode.DISCHARGE);
            }
            if (mSuctionHatch == null && pressHatches.size() > 1) {
                mSuctionHatch = pressHatches.get(1);
                mSuctionHatch.setMode(MTEHatchPressurizedFluid.HatchMode.SUCTION);
            }

            if (mDischargeHatch == null || mSuctionHatch == null) {
                mLoopStatus = "Requires 1 Discharge and 1 Suction Pressurized Hatch";
                return false;
            }

            if (mMaintenanceHatches.size() != 1) {
                mLoopStatus = "Requires 1 Maintenance Hatch";
                return false;
            }

            if (mEnergyHatches.isEmpty() || mEnergyHatches.size() > 2) {
                mLoopStatus = "Requires 1 or 2 Energy Hatches";
                return false;
            }

            for (MTEHatchPressurizedFluid ph : pressHatches) {
                ph.updateTexture(48 + 2);
            }
            for (MTEHatchMaintenance mh : mMaintenanceHatches) {
                mh.updateTexture(48 + 2);
            }
            for (MTEHatchEnergy eh : mEnergyHatches) {
                eh.updateTexture(48 + 2);
            }
            if (mDischargeHatch != null) {
                mDischargeHatch.updateTexture(48 + 2);
            }
            if (mSuctionHatch != null) {
                mSuctionHatch.updateTexture(48 + 2);
            }
            if (getBaseMetaTileEntity() != null) {
                ItemStack icon = getMachineCraftingIcon();
                for (MTEHatchPressurizedFluid ph : pressHatches) {
                    ph.updateCraftingIcon(icon);
                }
                for (MTEHatchMaintenance mh : mMaintenanceHatches) {
                    mh.updateCraftingIcon(icon);
                }
                for (MTEHatchEnergy eh : mEnergyHatches) {
                    eh.updateCraftingIcon(icon);
                }
                if (mDischargeHatch != null) {
                    mDischargeHatch.updateCraftingIcon(icon);
                }
                if (mSuctionHatch != null) {
                    mSuctionHatch.updateCraftingIcon(icon);
                }
            }
        }

        checkMaintenance();
        mLoopStatus = "Pump structure formed successfully";
        boolean loopFormed = crawlLoopGraph();
        if (!loopFormed) {
            return false;
        }
        return true;
    }

    /**
     * Checks if a Railcraft multiblock tank sits directly above this pump structure,
     * fully formed with matching footprint and containing coolant fluid.
     */
    public boolean verifyRailcraftTankAbove(IGregTechTileEntity aBaseMetaTileEntity) {
        if (aBaseMetaTileEntity == null) return false;
        World world = aBaseMetaTileEntity.getWorld();
        if (world == null) {
            // Standalone test/mock environment fallback
            return mSimulatedCoolantLiters > 0;
        }

        ForgeDirection front = aBaseMetaTileEntity.getFrontFacing();
        ForgeDirection back = front != null && front != ForgeDirection.UNKNOWN ? front.getOpposite()
            : ForgeDirection.SOUTH;
        ForgeDirection right = front != null && front != ForgeDirection.UNKNOWN ? front.getRotation(ForgeDirection.UP)
            : ForgeDirection.WEST;

        int x0 = aBaseMetaTileEntity.getXCoord();
        int y0 = aBaseMetaTileEntity.getYCoord();
        int z0 = aBaseMetaTileEntity.getZCoord();
        int y = y0 + 2; // Immediately above top pump layer (height 2)

        TileMultiBlock masterBlock = null;

        // Attempt instant pattern check on block behind controller
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

        // If not found directly behind, search the first row behind controller
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
            mLoopStatus = "Missing Railcraft tank above pump!";
            return false;
        }

        // Determine Railcraft tank material and verify it has matching GregTech fluid pipes
        Materials tankMat = getRailcraftTankMaterial(masterBlock);
        if (tankMat == null) {
            mLoopStatus = "Unrecognized Railcraft tank material above pump!";
            return false;
        }
        if (!LoopGraphCrawler.isAllowedCoolantPipeMaterial(tankMat)) {
            mLoopStatus = String.format(
                "Railcraft tank material %s has no matching GregTech fluid pipe! Allowed materials: Iron, Steel, Stainless Steel, Titanium, Tungstensteel, Neutronium.",
                tankMat.mDefaultLocalName);
            return false;
        }
        mTankMaterial = tankMat;

        // Determine dimensions from pattern
        MultiBlockPattern pattern = masterBlock.getPattern();
        if (pattern == null) {
            mLoopStatus = "Railcraft tank has invalid pattern!";
            return false;
        }
        int tankWidthX = pattern.getPatternWidthX();
        int tankWidthZ = pattern.getPatternWidthZ();
        // Railcraft pattern arrays include a 1-block outer border of 'O' (other/air),
        // so a 3x3 tank has pattern width 5 (5x5). Account for either raw or bordered dimension.
        int effX = (tankWidthX > 2) ? (tankWidthX - 2) : tankWidthX;
        int effZ = (tankWidthZ > 2) ? (tankWidthZ - 2) : tankWidthZ;

        int detectedWidth = effX;
        if (detectedWidth != 3 && detectedWidth != 5 && detectedWidth != 7 && detectedWidth != 9) {
            mLoopStatus = String.format("Unsupported Railcraft tank width %d (must be 3, 5, 7, or 9)!", detectedWidth);
            return false;
        }
        if (effX != effZ) {
            mLoopStatus = String.format("Railcraft tank footprint must be square (%dx%d detected)!", effX, effZ);
            return false;
        }

        mWidth = detectedWidth;
        mLength = detectedWidth;
        int R = mWidth / 2;

        // 1. Inspect every block across the footprint
        for (int localX = -R; localX <= R; localX++) {
            for (int localZ = 0; localZ < mWidth; localZ++) {
                int bx = x0 + right.offsetX * localX + back.offsetX * localZ;
                int bz = z0 + right.offsetZ * localX + back.offsetZ * localZ;
                TileEntity te = world.getTileEntity(bx, y, bz);
                if (te == null) {
                    mLoopStatus = String.format("Missing Railcraft tank block at (%d, %d, %d)", bx, y, bz);
                    return false;
                }
                if (!(te instanceof TileMultiBlock)) {
                    mLoopStatus = String.format(
                        "Block at (%d, %d, %d) is %s, not a Railcraft tank block",
                        bx,
                        y,
                        bz,
                        te.getClass()
                            .getSimpleName());
                    return false;
                }
                TileMultiBlock tmb = (TileMultiBlock) te;
                if (!tmb.isStructureValid()) {
                    mLoopStatus = "Railcraft tank above pump is not fully formed/structurally valid!";
                    return false;
                }
                if (tmb.getMasterBlock() != masterBlock) {
                    mLoopStatus = "Mismatched Railcraft tank: multiple distinct tank multiblocks detected above pump!";
                    return false;
                }
            }
        }

        // 2. Validate tank contains coolant fluid initially
        FluidStack availableFluid = getReservoirFluid();
        if (availableFluid == null || availableFluid.amount <= 0 || availableFluid.getFluid() == null) {
            mLoopStatus = "Railcraft tank is empty! Coolant fluid required before pump can operate.";
            return false;
        }

        if (isPlainRegularWater(availableFluid)) {
            mLoopStatus = "Pump refused to start: Plain regular water cannot be used in a coolant loop! Use Distilled Water.";
            return false;
        }

        if (isGaseousFluid(availableFluid)) {
            mLoopStatus = "Pump refused to start: Gaseous fluid (" + availableFluid.getFluid().getName() + ") cannot be used as a coolant!";
            return false;
        }

        CoolantFluidProperty prop = CoolantFluidProperty.get(
            availableFluid.getFluid()
                .getName());
        if (prop == null || prop.isPlainWater()) {
            mLoopStatus = "Pump refused to start: Plain regular water cannot be used in a coolant loop! Use Distilled Water.";
            return false;
        }

        if (isMoltenFluid(availableFluid.getFluid(), availableFluid, prop)) {
            double declaredMelting = getDeclaredGregTechFluidTemperatureCelsius(availableFluid.getFluid(), availableFluid, prop);
            double biomeTemp = getBiomeTemperatureCelsius();
            if (biomeTemp < declaredMelting) {
                mLoopStatus = String.format(
                    "Pump refused to start: Biome ambient temperature (%.1f °C) at pump is below declared GregTech melting point (%.1f °C) for %s! Fluid would solidify in this biome.",
                    biomeTemp,
                    declaredMelting,
                    prop.getFluidName());
                return false;
            }
        }
        mEngine.setFluid(prop);
        return true;
    }

    public static boolean isPlainRegularWater(net.minecraftforge.fluids.Fluid fluid) {
        if (fluid == null) return false;
        String name = fluid.getName().trim().toLowerCase();
        if (name.contains("distill") || name.contains("heavy")) {
            return false;
        }
        if (fluid == FluidRegistry.WATER) {
            return true;
        }
        return name.equals("water") || name.equals("minecraft:water") || name.equals("fluid.water");
    }

    public static boolean isPlainRegularWater(FluidStack stack) {
        if (stack == null || stack.getFluid() == null) return false;
        return isPlainRegularWater(stack.getFluid());
    }

    public static boolean isGaseousFluid(net.minecraftforge.fluids.Fluid fluid, FluidStack stack) {
        if (fluid == null) return false;
        if (fluid.isGaseous() || (stack != null && fluid.isGaseous(stack))) {
            return true;
        }
        String name = fluid.getName().trim().toLowerCase();
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

    public static boolean isMoltenFluid(net.minecraftforge.fluids.Fluid fluid, FluidStack stack, CoolantFluidProperty prop) {
        if (prop != null && prop.isMolten()) {
            return true;
        }
        if (stack != null && stack.getFluid() != null && stack.getFluid().getName() != null
            && stack.getFluid().getName().toLowerCase().contains("molten")) {
            return true;
        }
        if (fluid != null && fluid.getName() != null
            && fluid.getName().toLowerCase().contains("molten")) {
            return true;
        }
        return false;
    }

    public static boolean isMoltenFluid(FluidStack stack) {
        if (stack == null || stack.getFluid() == null) return false;
        return isMoltenFluid(stack.getFluid(), stack, CoolantFluidProperty.get(stack.getFluid().getName()));
    }

    public static double getDeclaredGregTechFluidTemperatureCelsius(net.minecraftforge.fluids.Fluid fluid, FluidStack stack, CoolantFluidProperty prop) {
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
        int tempK = stack.getFluid().getTemperature(stack);
        return tempK - 273.15;
    }

    protected double mSimulatedBiomeTempCelsius = 20.0;

    public void setSimulatedBiomeTemperatureCelsius(double temp) {
        this.mSimulatedBiomeTempCelsius = temp;
    }

    public double getBiomeTemperatureCelsius() {
        IGregTechTileEntity te = getBaseMetaTileEntity();
        if (te != null && te.getWorld() != null) {
            int x = te.getXCoord();
            int y = te.getYCoord();
            int z = te.getZCoord();
            net.minecraft.world.biome.BiomeGenBase biome = te.getBiome(x, z);
            if (biome != null) {
                float fTemp = biome.getFloatTemperature(x, y, z);
                // Standard Minecraft biome float temperature to Celsius:
                // 0.15F is freezing (0°C), 0.8F is temperate/plains (20°C), 2.0F is desert/nether (56.9°C)
                return (fTemp - 0.15) * (20.0 / 0.65);
            }
        }
        return mSimulatedBiomeTempCelsius;
    }

    public double getCoolantTemperatureCelsius() {
        if (mEngine != null && mEngine.getSegments() != null && !mEngine.getSegments().isEmpty()) {
            if (mLoopState == LoopState.CIRCULATING || mEngine.getVolumetricFlowRate() > 1e-5) {
                return mEngine.getMinLoopTempCelsius();
            }
        }
        return getBiomeTemperatureCelsius();
    }

    public FluidStack getReservoirFluid() {
        IGregTechTileEntity te = getBaseMetaTileEntity();
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
            int y = y0 + 2;
            int R = mWidth / 2;

            for (int localX = -R; localX <= R; localX++) {
                for (int localZ = 0; localZ < mWidth; localZ++) {
                    int bx = x0 + right.offsetX * localX + back.offsetX * localZ;
                    int bz = z0 + right.offsetZ * localX + back.offsetZ * localZ;
                    TileEntity tile = world.getTileEntity(bx, y, bz);
                    if (tile instanceof TileTankBase) {
                        TileTankBase master = (TileTankBase) ((TileTankBase) tile).getMasterBlock();
                        if (master != null && master.getTank() != null && master.getTank().getFluid() != null) {
                            return master.getTank().getFluid();
                        }
                    } else if (tile instanceof IFluidHandler) {
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

    /**
     * Re-crawls the connected piping circuit from the discharge hatch back to suction.
     */
    public boolean crawlLoopGraph() {
        IGregTechTileEntity te = getBaseMetaTileEntity();
        if (te == null || te.getWorld() == null) {
            return false;
        }

        if (mDischargeHatch == null || mSuctionHatch == null) {
            mLoopFormed = false;
            mLoopState = LoopState.EMPTY;
            mCurrentFillLiters = 0L;
            mLoopStatus = "Requires 1 Discharge and 1 Suction Pressurized Hatch";
            return false;
        }

        IGregTechTileEntity discTE = mDischargeHatch.getBaseMetaTileEntity();
        IGregTechTileEntity sucTE = mSuctionHatch.getBaseMetaTileEntity();

        LoopGraphCrawler crawler = new LoopGraphCrawler(te.getWorld(), 1000);
        LoopGraphCrawler.CrawlResult result = crawler.crawl(
            discTE.getXCoord(),
            discTE.getYCoord(),
            discTE.getZCoord(),
            discTE.getFrontFacing(),
            sucTE.getXCoord(),
            sucTE.getYCoord(),
            sucTE.getZCoord(),
            sucTE.getFrontFacing(),
            "pump_" + te.getXCoord() + "_" + te.getYCoord() + "_" + te.getZCoord(),
            this,
            mTankMaterial);

        if (!result.isSuccess) {
            mLoopFormed = false;
            mLoopState = LoopState.EMPTY;
            mCurrentFillLiters = 0L;
            mLoopStatus = "Loop disconnect: " + result.failureReason;
            return false;
        }

        mEngine.clearSegments();
        if (result.maxPumpPipeCapacityLPerSec > 0 && result.maxPumpPipeCapacityLPerSec != Double.MAX_VALUE) {
            mEngine.setMaxVolumetricFlowRate(result.maxPumpPipeCapacityLPerSec / 1000.0);
        }
        long requiredFill = 0L;
        for (LoopSegment s : result.segments) {
            mEngine.addSegment(s);
            if (s.getId()
                .startsWith("pipe_")) {
                requiredFill += s.getCapacityLiters();
            }
        }
        mLoopDevices = result.devices;
        for (ICoolantLoopDevice dev : mLoopDevices) {
            if (dev instanceof MTEPressurizedHeatExchanger || dev instanceof ICoolantPassageHatch
                || dev instanceof MTEPressurizedDegasser) {
                requiredFill += 1000L;
            } else if (dev instanceof TileEntityLoopInstrument) {
                requiredFill += 10L;
            } else if (dev instanceof TileEntityManifold) {
                requiredFill += 1000L;
            } else {
                requiredFill += 1000L;
            }
        }
        mRequiredFillLiters = requiredFill;
        mLoopFormed = true;
        fixAllIssues();
        mHasEverBeenEnabled = true;

        if (getBaseMetaTileEntity() != null && !getBaseMetaTileEntity().isAllowedToWork()) {
            getBaseMetaTileEntity().enableWorking();
        }
        if (mDischargeHatch != null) {
            IGregTechTileEntity bte = mDischargeHatch.getBaseMetaTileEntity();
            if (bte != null && !bte.isAllowedToWork()) {
                bte.enableWorking();
            }
        }

        if (mCurrentFillLiters < mRequiredFillLiters) {
            mLoopState = LoopState.FILLING;
            mLoopStatus = String.format(
                "Filling loop: %d / %d L (%.1f%%)",
                mCurrentFillLiters,
                mRequiredFillLiters,
                mRequiredFillLiters > 0 ? (mCurrentFillLiters * 100.0 / mRequiredFillLiters) : 100.0);
        } else {
            mLoopState = LoopState.CIRCULATING;
            mLoopStatus = String.format(
                "Loop active: %d pipe segments, %d devices (Filled %d L)",
                result.segments.size(),
                result.devices.size(),
                mCurrentFillLiters);
        }
        return true;
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);
        if (aBaseMetaTileEntity != null && aBaseMetaTileEntity.isServerSide()) {
            stepCoolantLoop();
        }
    }

    public boolean drainEnergy(long aEU) {
        if (drainEnergyInput(aEU)) return true;
        if (getBaseMetaTileEntity() != null && getBaseMetaTileEntity().getStoredEU() >= aEU) {
            return getBaseMetaTileEntity().decreaseStoredEnergyUnits(aEU, false);
        }
        return false;
    }

    public boolean stepCoolantLoop() {
        if (!mLoopFormed || mEngine.getSegments()
            .isEmpty()) {
            if (mLoopState != LoopState.EMPTY && mLoopState != LoopState.STOPPED && mLoopState != LoopState.FAILED) {
                mLoopState = LoopState.STOPPED;
            }
            return true;
        }

        IGregTechTileEntity baseTE = getBaseMetaTileEntity();
        if (baseTE == null) {
            return true;
        }

        // Draw electrical power if allowed to work (soft hammer / redstone control)
        boolean isWorking = isAllowedToWork();
        if (!isWorking) {
            mEngine.setPumpPowered(false);
            mEngine.setPumpMechanicalPowerWatts(0.0);
            mEngine.setBraking(false);
            if (mLoopState == LoopState.FILLING) {
                mLoopStatus = String
                    .format("Filling paused (Pump disabled): %d / %d L", mCurrentFillLiters, mRequiredFillLiters);
            } else if (mLoopState == LoopState.DECELERATING || mLoopState == LoopState.DRAINING) {
                mLoopStatus = String.format(
                    "Stopping paused (Pump disabled): %d / %d L in loop",
                    mCurrentFillLiters,
                    mRequiredFillLiters);
            } else {
                mLoopStatus = String
                    .format("Pump disabled (Coasting down): %.1f L/s", mEngine.getVolumetricFlowRate() * 1000.0);
            }
            mEngine.step(0.05); // Fluid inertia coast-down
            return true; // Pauses/stops cleanly without explosion!
        }

        // Validate fluid in reservoir / loop: plain regular water and gases refuse to start pump
        FluidStack reservoirFluid = getReservoirFluid();
        if (reservoirFluid != null && reservoirFluid.getFluid() != null) {
            if (isPlainRegularWater(reservoirFluid)) {
                mLoopState = LoopState.STOPPED;
                mEngine.setPumpPowered(false);
                mEngine.setPumpMechanicalPowerWatts(0.0);
                mEngine.setBraking(false);
                mLoopStatus = "Pump refused to start: Plain regular water cannot be used in a coolant loop! Use Distilled Water.";
                mEngine.step(0.05);
                return true;
            }
            if (isGaseousFluid(reservoirFluid)) {
                mLoopState = LoopState.STOPPED;
                mEngine.setPumpPowered(false);
                mEngine.setPumpMechanicalPowerWatts(0.0);
                mEngine.setBraking(false);
                mLoopStatus = "Pump refused to start: Gaseous fluid (" + reservoirFluid.getFluid().getName() + ") cannot be used as a coolant!";
                mEngine.step(0.05);
                return true;
            }
        } else if (mEngine.getFluid() != null && mEngine.getFluid().isPlainWater()) {
            mLoopState = LoopState.STOPPED;
            mEngine.setPumpPowered(false);
            mEngine.setPumpMechanicalPowerWatts(0.0);
            mEngine.setBraking(false);
            mLoopStatus = "Pump refused to start: Plain regular water cannot be used in a coolant loop! Use Distilled Water.";
            mEngine.step(0.05);
            return true;
        }

        // Validate molten fluid temperature
        CoolantFluidProperty currentProp = mEngine.getFluid();
        if (currentProp == null && reservoirFluid != null) {
            currentProp = CoolantFluidProperty.get(reservoirFluid.getFluid().getName());
        }
        boolean isMolten = isMoltenFluid(reservoirFluid != null ? reservoirFluid.getFluid() : null, reservoirFluid, currentProp);

        if (isMolten) {
            double declaredMelting = getDeclaredGregTechFluidTemperatureCelsius(
                reservoirFluid != null ? reservoirFluid.getFluid() : null,
                reservoirFluid,
                currentProp);

            // Active circulation check: if circulating, dropping below melting point causes catastrophic solidification explosion!
            if (mLoopState == LoopState.CIRCULATING || mEngine.getVolumetricFlowRate() > 1e-5) {
                double minLoopTemp = mEngine.getMinLoopTempCelsius();
                if (minLoopTemp < declaredMelting) {
                    mEngine.setRuptured(true);
                    mEngine.setFailureReason(String.format(
                        "Catastrophic coolant solidification: Coolant temperature (%.1f °C) dropped below declared GregTech melting point (%.1f °C) for %s! Solidified plug clogged circulating loop.",
                        minLoopTemp,
                        declaredMelting,
                        currentProp != null ? currentProp.getFluidName() : "molten fluid"));
                    triggerCatastrophicExplosion(mEngine.getFailureReason());
                    return false;
                }
            } else {
                // Not circulating (stopped, decelerating, filling, or about to accelerate): refuse to fill or accelerate!
                double biomeTemp = getBiomeTemperatureCelsius();
                if (biomeTemp < declaredMelting) {
                    mLoopState = LoopState.STOPPED;
                    mEngine.setPumpPowered(false);
                    mEngine.setPumpMechanicalPowerWatts(0.0);
                    mEngine.setBraking(false);
                    mLoopStatus = String.format(
                        "Pump refused to start: Biome ambient temperature (%.1f °C) at pump is below declared GregTech melting point (%.1f °C) for %s! Fluid would solidify in this biome.",
                        biomeTemp,
                        declaredMelting,
                        currentProp != null ? currentProp.getFluidName() : "molten fluid");
                    mEngine.step(0.05);
                    return true;
                }
            }
        }

        // Require valid turbine rotor impeller in controller slot
        if (!updateRotorEfficiency()) {
            mLoopState = LoopState.STOPPED;
            mEngine.setPumpPowered(false);
            mEngine.setPumpMechanicalPowerWatts(0.0);
            mEngine.setBraking(false);
            mLoopStatus = "Missing turbine rotor impeller in controller slot!";
            mEngine.step(0.05); // Fluid inertia coast-down
            return true;
        }

        // Check if high-pressure output hatch is disabled by machine controller cover
        boolean dischargeDisabled = isDischargeHatchDisabled();
        if (dischargeDisabled) {
            checkMaintenance();
            double effFactor = getEfficiencyFactor();
            long availableEU = 0;
            if (mEnergyHatches != null && !mEnergyHatches.isEmpty()) {
                long voltage = getMaxInputVoltage();
                long amps = mEnergyHatches.size() >= 2 ? 4 : 2;
                availableEU = voltage * amps;
            }
            if (availableEU == 0) availableEU = 512;
            boolean powered = drainEnergy(availableEU);
            double mechanicalWatts = powered ? (availableEU * 80.0 * mRotorEfficiency * effFactor) : 0.0;
            if (powered) {
                degradeRotor(availableEU);
            }

            double flowRate = mEngine.getVolumetricFlowRate();
            if (flowRate > 1e-5) {
                // Phase A: Active deceleration down to zero
                mLoopState = LoopState.DECELERATING;
                mEngine.setPumpPowered(powered);
                mEngine.setPumpMechanicalPowerWatts(mechanicalWatts);
                mEngine.setBraking(true);

                // Advance fluid mechanics with active motor braking
                mEngine.step(0.05);

                if (mEngine.getVolumetricFlowRate() <= 1e-5) {
                    mEngine.setVolumetricFlowRate(0.0);
                    mEngine.setBraking(false);
                }

                mLoopStatus = String.format(
                    "Actively decelerating flow: %.1f L/s -> 0.0 L/s (Power: %.1f kW)",
                    mEngine.getFlowRateLitersPerSecond(),
                    mechanicalWatts / 1000.0);

                // Thermal exchange during deceleration
                for (ICoolantLoopDevice dev : mLoopDevices) {
                    LoopSegment seg = null;
                    for (LoopSegment s : mEngine.getSegments()) {
                        if (s.getId()
                            .equals(dev.getDeviceId())) {
                            seg = s;
                            break;
                        }
                    }
                    dev.processThermalExchange(mEngine.getVolumetricFlowRate(), 0.05, mEngine.getFluid(), seg);
                }
                return true;
            } else {
                // Phase B: Flow is zero, return fluid in loop back to reservoir
                mEngine.setVolumetricFlowRate(0.0);
                mEngine.setBraking(false);
                mEngine.setPumpPowered(false);
                mEngine.setPumpMechanicalPowerWatts(0.0);

                if (mCurrentFillLiters > 0) {
                    if (isReservoirFull()) {
                        mLoopState = LoopState.STOPPED;
                        mLoopStatus = String.format(
                            "Flow stopped. Draining paused: Reservoir full (%d L remaining in loop)",
                            mCurrentFillLiters);
                    } else {
                        mLoopState = LoopState.DRAINING;
                        long neededReturn = mCurrentFillLiters;
                        int toReturn = (int) Math.min(mFillRateLitersPerTick, neededReturn);
                        int returned = fillIntoReservoir(toReturn);
                        if (returned > 0) {
                            mCurrentFillLiters -= returned;
                        }

                        if (mCurrentFillLiters <= 0) {
                            mCurrentFillLiters = 0;
                            mLoopState = LoopState.EMPTY;
                            mLoopStatus = "Flow stopped. Loop drained completely back to reservoir.";
                        } else if (isReservoirFull()) {
                            mLoopState = LoopState.STOPPED;
                            mLoopStatus = String.format(
                                "Flow stopped. Draining paused: Reservoir full (%d L remaining in loop)",
                                mCurrentFillLiters);
                        } else {
                            mLoopStatus = String.format(
                                "Draining loop to reservoir: %d / %d L remaining",
                                mCurrentFillLiters,
                                mRequiredFillLiters);
                        }
                    }
                } else {
                    mLoopState = LoopState.EMPTY;
                    mLoopStatus = "Flow stopped. Loop empty.";
                }
                return true;
            }
        }

        mEngine.setBraking(false);

        // Initial Loop Filling Phase (when hatch is enabled)
        if (mLoopState == LoopState.EMPTY || mLoopState == LoopState.DRAINING
            || mCurrentFillLiters < mRequiredFillLiters) {
            mLoopState = LoopState.FILLING;
            // No flow acceleration, power or convective heat transfer during filling
            mEngine.setPumpPowered(false);
            mEngine.setPumpMechanicalPowerWatts(0.0);

            long needed = mRequiredFillLiters - mCurrentFillLiters;
            int toDraw = (int) Math.min(mFillRateLitersPerTick, needed);
            int drained = drainFromReservoir(toDraw);

            if (drained > 0) {
                mCurrentFillLiters += drained;
                double biomeTemp = getBiomeTemperatureCelsius();
                for (LoopSegment seg : mEngine.getSegments()) {
                    if (seg.getCurrentTemperatureCelsius() < biomeTemp) {
                        seg.setCurrentTemperatureCelsius(biomeTemp);
                    }
                }
                if (mCurrentFillLiters >= mRequiredFillLiters) {
                    mCurrentFillLiters = mRequiredFillLiters;
                    mLoopState = LoopState.CIRCULATING;
                    mLoopStatus = String.format("Loop filled (%d L). Beginning circulation.", mCurrentFillLiters);
                } else {
                    mLoopStatus = String.format(
                        "Filling loop: %d / %d L (%.1f%%)",
                        mCurrentFillLiters,
                        mRequiredFillLiters,
                        mRequiredFillLiters > 0 ? (mCurrentFillLiters * 100.0 / mRequiredFillLiters) : 100.0);
                }
            } else {
                // Reservoir ran out of fluid before loop filled!
                // Safe fail condition: stop cleanly WITHOUT EXPLOSION
                mLoopState = LoopState.FAILED;
                mLoopStatus = String.format(
                    "Filling failed: Reservoir dry! (%d / %d L filled)",
                    mCurrentFillLiters,
                    mRequiredFillLiters);
            }
            return true;
        }

        // Active Circulation Phase
        checkMaintenance();
        double effFactor = getEfficiencyFactor();
        long availableEU = 0;
        if (mEnergyHatches != null && !mEnergyHatches.isEmpty()) {
            long voltage = getMaxInputVoltage();
            long amps = mEnergyHatches.size() >= 2 ? 4 : 2;
            availableEU = voltage * amps;
        }
        if (availableEU == 0) availableEU = 512;
        boolean hasPower = drainEnergy(availableEU);
        if (hasPower) {
            // Refuse to accelerate flow if molten fluid is below declared GregTech melting point
            if (isMolten) {
                double minLoopTemp = mEngine.getMinLoopTempCelsius();
                double declaredMelting = getDeclaredGregTechFluidTemperatureCelsius(
                    reservoirFluid != null ? reservoirFluid.getFluid() : null,
                    reservoirFluid,
                    currentProp);
                if (minLoopTemp < declaredMelting) {
                    if (mEngine.getVolumetricFlowRate() > 1e-5) {
                        mEngine.setRuptured(true);
                        mEngine.setFailureReason(String.format(
                            "Catastrophic coolant solidification: Coolant temperature (%.1f °C) dropped below declared GregTech melting point (%.1f °C) for %s! Solidified plug clogged circulating loop.",
                            minLoopTemp,
                            declaredMelting,
                            currentProp != null ? currentProp.getFluidName() : "molten fluid"));
                        triggerCatastrophicExplosion(mEngine.getFailureReason());
                        return false;
                    } else {
                        double biomeTemp = getBiomeTemperatureCelsius();
                        mLoopState = LoopState.STOPPED;
                        mEngine.setPumpPowered(false);
                        mEngine.setPumpMechanicalPowerWatts(0.0);
                        if (biomeTemp < declaredMelting) {
                            mLoopStatus = String.format(
                                "Pump refused to start: Biome ambient temperature (%.1f °C) at pump is below declared GregTech melting point (%.1f °C) for %s! Fluid would solidify in this biome.",
                                biomeTemp,
                                declaredMelting,
                                currentProp != null ? currentProp.getFluidName() : "molten fluid");
                        } else {
                            mLoopStatus = String.format(
                                "Pump refused to start: Coolant temperature (%.1f °C) is below declared GregTech melting point (%.1f °C) for %s! Fluid would solidify.",
                                minLoopTemp,
                                declaredMelting,
                                currentProp != null ? currentProp.getFluidName() : "molten fluid");
                        }
                        return true;
                    }
                }
            }

            mLoopState = LoopState.CIRCULATING;
            mEngine.setPumpPowered(true);
            double mechanicalWatts = availableEU * 80.0 * mRotorEfficiency * effFactor;
            mEngine.setPumpMechanicalPowerWatts(mechanicalWatts);
            degradeRotor(availableEU);
        } else {
            mLoopState = LoopState.STOPPED;
            mEngine.setPumpPowered(false);
            mEngine.setPumpMechanicalPowerWatts(0.0);
            mLoopStatus = "Pump unpowered (No EU in Energy Hatch)";
        }

        // Synchronize dissolved gas fraction across engine and all segments
        double gasFrac = getDissolvedGasFraction();
        mEngine.setDissolvedGasFraction(gasFrac);
        for (LoopSegment seg : mEngine.getSegments()) {
            seg.setDissolvedGasFraction(gasFrac);
        }

        // Advance 1 tick (0.05 seconds) of fluid dynamics
        mEngine.step(0.05);

        // Process thermal exchange across in-line devices matching segment ID
        double flowRate = mEngine.getVolumetricFlowRate();
        for (ICoolantLoopDevice dev : mLoopDevices) {
            LoopSegment seg = null;
            for (LoopSegment s : mEngine.getSegments()) {
                if (s.getId()
                    .equals(dev.getDeviceId())) {
                    seg = s;
                    break;
                }
            }
            dev.processThermalExchange(flowRate, 0.05, mEngine.getFluid(), seg);
        }

        // Check for catastrophic rupture (only during active circulation under catastrophic overpressure)
        if (mEngine.isRuptured()) {
            triggerCatastrophicExplosion(mEngine.getFailureReason());
            return false;
        }

        return true;
    }

    @Override
    public boolean onRunningTick(ItemStack aStack) {
        return stepCoolantLoop();
    }

    /**
     * Trigger catastrophic pump explosion and drain 80% of fluid from the Railcraft tank.
     */
    protected void triggerCatastrophicExplosion(String reason) {
        IGregTechTileEntity te = getBaseMetaTileEntity();
        if (te != null && !te.getWorld().isRemote) {
            World world = te.getWorld();
            int x = te.getXCoord();
            int y = te.getYCoord();
            int z = te.getZCoord();

            // Drain 80% of fluid from tank above
            drainFromReservoir((int) (getTotalCoolantLiters() * 0.80));

            // Explosion scaled to peak loop pressure
            float explosionStrength = Math.min(25.0f, (float) (4.0f + mEngine.getPeakLoopPressureBar() / 10.0));
            world.createExplosion(null, x + 0.5, y + 1.5, z + 0.5, explosionStrength, true);
            te.doExplosion((long) (explosionStrength * 1000));
        }
    }

    public static boolean isValidRotor(ItemStack aStack) {
        if (aStack == null) return false;
        if (aStack.getItem() instanceof MetaGeneratedTool) {
            int damage = aStack.getItemDamage();
            return damage >= 170 && damage <= 179;
        }
        return false;
    }

    public ItemStack getRotor() {
        ItemStack controller = getControllerSlot();
        if (isValidRotor(controller)) {
            return controller;
        }
        if (mRotorStack != null && isValidRotor(mRotorStack)) {
            return mRotorStack;
        }
        return null;
    }

    public boolean updateRotorEfficiency() {
        ItemStack rotor = getRotor();
        if (rotor != null && rotor.getItem() instanceof MetaGeneratedTool) {
            TurbineStatCalculator calc = new TurbineStatCalculator((MetaGeneratedTool) rotor.getItem(), rotor);
            mRotorEfficiency = calc.getBaseEfficiency();
            mRotorStack = rotor;
            return true;
        }
        // Headless mock or test environment fallback
        if (getBaseMetaTileEntity() == null || getBaseMetaTileEntity().getWorld() == null) {
            return true;
        }
        mRotorStack = null;
        return false;
    }

    public void setRotorStack(ItemStack stack) {
        this.mRotorStack = stack;
        if (stack != null && stack.getItem() instanceof MetaGeneratedTool) {
            TurbineStatCalculator calc = new TurbineStatCalculator((MetaGeneratedTool) stack.getItem(), stack);
            this.mRotorEfficiency = calc.getBaseEfficiency();
        }
    }

    public ItemStack getRotorStack() {
        return getRotor();
    }

    public double getRotorEfficiency() {
        return mRotorEfficiency;
    }

    @Override
    public boolean isCorrectMachinePart(ItemStack aStack) {
        return isValidRotor(aStack);
    }

    @Override
    public int getDamageToComponent(ItemStack aStack) {
        return 1;
    }

    @Override
    public int getMaxEfficiency(ItemStack aStack) {
        if (aStack == null) return 10000;
        if (isValidRotor(aStack)) return 10000;
        return 0;
    }

    protected void degradeRotor(long euPerTick) {
        ItemStack rotor = getRotor();
        if (rotor != null && rotor.getItemDamage() < rotor.getMaxDamage()) {
            if (getBaseMetaTileEntity() != null && getBaseMetaTileEntity().getTimer() % 100 == 0) {
                rotor.setItemDamage(rotor.getItemDamage() + 1);
                if (rotor.getItemDamage() >= rotor.getMaxDamage()) {
                    if (getControllerSlot() == rotor) {
                        setInventorySlotContents(getControllerSlotIndex(), null);
                    }
                    mRotorStack = null; // Rotor broken!
                }
            }
        }
    }

    public CoolantLoopEngine getEngine() {
        return mEngine;
    }

    public boolean isLoopFormed() {
        return mLoopFormed;
    }

    public void setLoopFormed(boolean formed) {
        this.mLoopFormed = formed;
    }

    public String getLoopStatus() {
        return mLoopStatus;
    }

    public LoopState getLoopState() {
        return mLoopState;
    }

    public void setLoopState(LoopState state) {
        this.mLoopState = state;
    }

    public long getCurrentFillLiters() {
        return mCurrentFillLiters;
    }

    public void setCurrentFillLiters(long liters) {
        this.mCurrentFillLiters = liters;
    }

    public long getRequiredFillLiters() {
        return mRequiredFillLiters;
    }

    public void setRequiredFillLiters(long liters) {
        this.mRequiredFillLiters = liters;
    }

    public void setSimulatedCoolantLiters(long liters) {
        this.mSimulatedCoolantLiters = liters;
    }

    public void setFillRateLitersPerTick(long litersPerTick) {
        this.mFillRateLitersPerTick = litersPerTick;
    }

    @Override
    public boolean checkRecipe(ItemStack aStack) {
        return true; // Continuously operates in onRunningTick
    }

    /**
     * Drains fluid from the Railcraft tank multiblock reservoir directly above the pump.
     */
    public int drainFromReservoir(int liters) {
        if (liters <= 0) return 0;
        IGregTechTileEntity te = getBaseMetaTileEntity();
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
            int R = mWidth / 2;

            for (int localX = -R; localX <= R; localX++) {
                for (int localZ = 0; localZ < mWidth; localZ++) {
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

        if (mSimulatedCoolantLiters > 0) {
            int drained = (int) Math.min(liters, mSimulatedCoolantLiters);
            mSimulatedCoolantLiters -= drained;
            return drained;
        }
        return 0;
    }

    /**
     * Returns fluid from the coolant loop back into the Railcraft tank reservoir directly above the pump.
     */
    public int fillIntoReservoir(int liters) {
        if (liters <= 0) return 0;
        IGregTechTileEntity te = getBaseMetaTileEntity();
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
            int R = mWidth / 2;

            for (int localX = -R; localX <= R; localX++) {
                for (int localZ = 0; localZ < mWidth; localZ++) {
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
                                    return 0; // Tank is full
                                }
                                int toFill = Math.min(liters, space);
                                String fluidName = mEngine.getFluid() != null ? mEngine.getFluid()
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
                            String fluidName = mEngine.getFluid() != null ? mEngine.getFluid()
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

        if (mSimulatedTankCapacityLiters > 0) {
            long space = Math.max(0, mSimulatedTankCapacityLiters - mSimulatedCoolantLiters);
            int filled = (int) Math.min(liters, space);
            mSimulatedCoolantLiters += filled;
            return filled;
        } else {
            mSimulatedCoolantLiters += liters;
            return liters;
        }
    }

    /**
     * Checks if the Railcraft tank reservoir directly above the pump is full.
     */
    public boolean isReservoirFull() {
        IGregTechTileEntity te = getBaseMetaTileEntity();
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
            int R = mWidth / 2;

            for (int localX = -R; localX <= R; localX++) {
                for (int localZ = 0; localZ < mWidth; localZ++) {
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

        if (mSimulatedTankCapacityLiters > 0) {
            return mSimulatedCoolantLiters >= mSimulatedTankCapacityLiters;
        }
        return false;
    }

    @Override
    public void consumeCoolant(long liters) {
        if (liters <= 0) return;
        drainFromReservoir((int) Math.min(Integer.MAX_VALUE, liters));
    }

    @Override
    public void addDissolvedGas(String gasName, long liters) {
        if (gasName == null || liters <= 0) return;
        mDissolvedGases.merge(gasName, liters, Long::sum);
    }

    @Override
    public double getDissolvedGasFraction() {
        long totalGas = 0;
        for (long val : mDissolvedGases.values()) {
            totalGas += val;
        }
        long totalLiters = getTotalCoolantLiters();
        if (totalLiters <= 0) return 0.0;
        double frac = (double) totalGas / (double) totalLiters;
        return Math.min(1.0, Math.max(0.0, frac));
    }

    @Override
    public long getDissolvedGasAmount(String gasName) {
        if (gasName == null) return 0L;
        return mDissolvedGases.getOrDefault(gasName, 0L);
    }

    @Override
    public Map<String, Long> getDissolvedGases() {
        return Collections.unmodifiableMap(mDissolvedGases);
    }

    @Override
    public synchronized long extractDissolvedGas(String gasName, long maxLiters) {
        if (gasName == null || maxLiters <= 0) return 0L;
        String matchedKey = null;
        for (String k : mDissolvedGases.keySet()) {
            if (k.equalsIgnoreCase(gasName)) {
                matchedKey = k;
                break;
            }
        }
        if (matchedKey == null) return 0L;
        long current = mDissolvedGases.getOrDefault(matchedKey, 0L);
        long toExtract = Math.min(current, maxLiters);
        if (toExtract > 0) {
            long remaining = current - toExtract;
            if (remaining > 0) {
                mDissolvedGases.put(matchedKey, remaining);
            } else {
                mDissolvedGases.remove(matchedKey);
            }
        }
        return toExtract;
    }

    @Override
    public long getTotalCoolantLiters() {
        IGregTechTileEntity te = getBaseMetaTileEntity();
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
            int R = mWidth / 2;

            for (int localX = -R; localX <= R; localX++) {
                for (int localZ = 0; localZ < mWidth; localZ++) {
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
        return mSimulatedCoolantLiters;
    }

    @Override
    public CoolantFluidProperty getCoolantFluidProperty() {
        return mEngine != null ? mEngine.getFluid() : CoolantFluidProperty.DISTILLED_WATER;
    }

    @Override
    public Materials getTankMaterial() {
        return mTankMaterial;
    }

    public void setTankMaterial(Materials mat) {
        this.mTankMaterial = mat;
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

    @Override
    public String[] getInfoData() {
        String tankMatName = mTankMaterial != null ? mTankMaterial.mDefaultLocalName : "Unknown";
        double maxFlowRateLPerSec = mEngine.getMaxFlowRateLitersPerSecond();
        String maxFlowStr = (maxFlowRateLPerSec == Double.MAX_VALUE) ? "Unlimited"
            : String.format("%.1f L/s", maxFlowRateLPerSec);
        String hatchState = isDischargeHatchDisabled() ? "Disabled (Stopping/Draining)" : "Enabled";
        return new String[] { "State: " + mLoopState.name(), "Discharge Hatch: " + hatchState,
            "Tank Material: " + tankMatName,
            "Current Flow: " + String.format("%.2f L/s", mEngine.getFlowRateLitersPerSecond()),
            "Max Pipe Flow: " + maxFlowStr,
            "Peak Pressure: " + String.format("%.2f bar", mEngine.getPeakLoopPressureBar()),
            "Peak Temp: " + String.format("%.1f C", mEngine.getPeakLoopTempCelsius()),
            "Loop Fluid: " + String.format("%d / %d L", mCurrentFillLiters, mRequiredFillLiters),
            "Dissolved Gas: " + String.format("%.2f%%", getDissolvedGasFraction() * 100.0) };
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        NBTTagCompound gasesTag = new NBTTagCompound();
        for (Map.Entry<String, Long> entry : mDissolvedGases.entrySet()) {
            gasesTag.setLong(entry.getKey(), entry.getValue());
        }
        aNBT.setTag("mDissolvedGases", gasesTag);
        aNBT.setLong("mSimulatedCoolantLiters", mSimulatedCoolantLiters);
        aNBT.setLong("mSimulatedTankCapacityLiters", mSimulatedTankCapacityLiters);
        aNBT.setString("mLoopState", mLoopState.name());
        aNBT.setLong("mCurrentFillLiters", mCurrentFillLiters);
        aNBT.setLong("mRequiredFillLiters", mRequiredFillLiters);
        if (mTankMaterial != null && mTankMaterial.mName != null) {
            aNBT.setString("mTankMaterial", mTankMaterial.mName);
        }
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        mDissolvedGases.clear();
        if (aNBT.hasKey("mDissolvedGases")) {
            NBTTagCompound gasesTag = aNBT.getCompoundTag("mDissolvedGases");
            for (Object keyObj : gasesTag.func_150296_c()) {
                String key = (String) keyObj;
                mDissolvedGases.put(key, gasesTag.getLong(key));
            }
        }
        if (aNBT.hasKey("mSimulatedCoolantLiters")) {
            mSimulatedCoolantLiters = aNBT.getLong("mSimulatedCoolantLiters");
        }
        if (aNBT.hasKey("mSimulatedTankCapacityLiters")) {
            mSimulatedTankCapacityLiters = aNBT.getLong("mSimulatedTankCapacityLiters");
        }
        if (aNBT.hasKey("mLoopState")) {
            try {
                mLoopState = LoopState.valueOf(aNBT.getString("mLoopState"));
            } catch (Exception ignored) {}
        }
        if (aNBT.hasKey("mCurrentFillLiters")) {
            mCurrentFillLiters = aNBT.getLong("mCurrentFillLiters");
        }
        if (aNBT.hasKey("mRequiredFillLiters")) {
            mRequiredFillLiters = aNBT.getLong("mRequiredFillLiters");
        }
        if (aNBT.hasKey("mTankMaterial")) {
            Materials mat = Materials.get(aNBT.getString("mTankMaterial"));
            if (mat != null) {
                mTankMaterial = mat;
            }
        }
    }

    public boolean addBottomHatch(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        if (aTileEntity == null) return false;
        IMetaTileEntity aMetaTileEntity = aTileEntity.getMetaTileEntity();
        if (aMetaTileEntity == null) return false;
        if (aMetaTileEntity instanceof MTEHatchPressurizedFluid) {
            MTEHatchPressurizedFluid ph = (MTEHatchPressurizedFluid) aMetaTileEntity;
            ph.updateTexture(aBaseCasingIndex);
            if (getBaseMetaTileEntity() != null) {
                ph.updateCraftingIcon(getMachineCraftingIcon());
            }
            if (mDischargeHatch == null && ph.getMode() == MTEHatchPressurizedFluid.HatchMode.DISCHARGE) {
                mDischargeHatch = ph;
                return true;
            } else if (mSuctionHatch == null && ph.getMode() == MTEHatchPressurizedFluid.HatchMode.SUCTION) {
                mSuctionHatch = ph;
                return true;
            } else if (mDischargeHatch == null) {
                mDischargeHatch = ph;
                ph.setMode(MTEHatchPressurizedFluid.HatchMode.DISCHARGE);
                return true;
            } else if (mSuctionHatch == null) {
                mSuctionHatch = ph;
                ph.setMode(MTEHatchPressurizedFluid.HatchMode.SUCTION);
                return true;
            }
            return true;
        }
        if (aMetaTileEntity instanceof MTEHatchMaintenance) {
            return addMaintenanceToMachineList(aTileEntity, aBaseCasingIndex);
        }
        if (aMetaTileEntity instanceof MTEHatchEnergy) {
            if (aMetaTileEntity.getClass()
                .getName()
                .contains("Multi")) {
                return false;
            }
            return addEnergyInputToMachineList(aTileEntity, aBaseCasingIndex);
        }
        return false;
    }

    public static Materials getMaterialForTier(int tier) {
        switch (tier) {
            case 0:
                return Materials.CastIron;
            case 1:
                return Materials.Steel;
            case 2:
                return Materials.StainlessSteel;
            case 3:
                return Materials.Titanium;
            case 4:
                return Materials.TungstenSteel;
            case 5:
                return Materials.Neutronium;
            default:
                if (tier < 0) return Materials.CastIron;
                return Materials.Neutronium;
        }
    }

    public static Materials getMaterialFromTrigger(ItemStack trigger) {
        if (trigger == null) return Materials.Steel;
        int tier;
        try {
            if (ChannelDataAccessor.hasSubChannel(trigger, "tier")) {
                tier = ChannelDataAccessor.getChannelData(trigger, "tier");
            } else {
                tier = trigger.stackSize - 1;
            }
        } catch (Throwable t) {
            tier = trigger.stackSize - 1;
        }
        return getMaterialForTier(tier);
    }

    public static int getWidthFromTrigger(ItemStack trigger) {
        if (trigger == null) return 3;
        int val = CoolantStructureChannels.STRUCTURE_WIDTH.getValueClamped(trigger, 0, 9);
        switch (val) {
            case 0:
            case 1:
            case 3:
                return 3;
            case 2:
            case 5:
                return 5;
            case 7:
                return 7;
            case 4:
            case 9:
                return 9;
            default:
                if (val <= 0) return 3;
                if (val == 6) return 7;
                if (val >= 8) return 9;
                return 3;
        }
    }

    public static int getHeightFromTrigger(ItemStack trigger) {
        if (trigger == null) return 4;
        int val = CoolantStructureChannels.STRUCTURE_HEIGHT.getValueClamped(trigger, 0, 8);
        switch (val) {
            case 0:
            case 1:
            case 4:
                return 4;
            case 2:
            case 5:
                return 5;
            case 3:
            case 6:
                return 6;
            case 7:
                return 7;
            case 8:
                return 8;
            default:
                if (val < 4) return 4;
                if (val > 8) return 8;
                return val;
        }
    }

    public static ItemStack getTankWallItem(Materials mat) {
        try {
            if (mat == Materials.CastIron && EnumMachineBeta.TANK_IRON_WALL != null) {
                return EnumMachineBeta.TANK_IRON_WALL.getItem();
            }
            if (mat == Materials.Steel && EnumMachineBeta.TANK_STEEL_WALL != null) {
                return EnumMachineBeta.TANK_STEEL_WALL.getItem();
            }
            if (mat == Materials.StainlessSteel && ModuleAdvancedTanks.STAINLESS != null
                && ModuleAdvancedTanks.STAINLESS.TANK_WALL != null) {
                return ModuleAdvancedTanks.STAINLESS.TANK_WALL.getItem();
            }
            if (mat == Materials.Titanium && ModuleAdvancedTanks.TITANIUM != null
                && ModuleAdvancedTanks.TITANIUM.TANK_WALL != null) {
                return ModuleAdvancedTanks.TITANIUM.TANK_WALL.getItem();
            }
            if (mat == Materials.TungstenSteel && ModuleAdvancedTanks.TUNGSTENSTEEL != null
                && ModuleAdvancedTanks.TUNGSTENSTEEL.TANK_WALL != null) {
                return ModuleAdvancedTanks.TUNGSTENSTEEL.TANK_WALL.getItem();
            }
            if (mat == Materials.Neutronium && ModuleAdvancedTanks.NEUTRONIUM != null
                && ModuleAdvancedTanks.NEUTRONIUM.TANK_WALL != null) {
                return ModuleAdvancedTanks.NEUTRONIUM.TANK_WALL.getItem();
            }
            if (mat == Materials.Aluminium && ModuleAdvancedTanks.ALUMINIUM != null
                && ModuleAdvancedTanks.ALUMINIUM.TANK_WALL != null) {
                return ModuleAdvancedTanks.ALUMINIUM.TANK_WALL.getItem();
            }
            if (EnumMachineBeta.TANK_STEEL_WALL != null) {
                return EnumMachineBeta.TANK_STEEL_WALL.getItem();
            }
            return new ItemStack(net.minecraft.init.Items.stick, 1);
        } catch (Throwable t) {
            return new ItemStack(net.minecraft.init.Items.stick, 1);
        }
    }

    public static ItemStack getTankValveItem(Materials mat) {
        try {
            if (mat == Materials.CastIron && EnumMachineBeta.TANK_IRON_VALVE != null) {
                return EnumMachineBeta.TANK_IRON_VALVE.getItem();
            }
            if (mat == Materials.Steel && EnumMachineBeta.TANK_STEEL_VALVE != null) {
                return EnumMachineBeta.TANK_STEEL_VALVE.getItem();
            }
            if (mat == Materials.StainlessSteel && ModuleAdvancedTanks.STAINLESS != null
                && ModuleAdvancedTanks.STAINLESS.TANK_VALVE != null) {
                return ModuleAdvancedTanks.STAINLESS.TANK_VALVE.getItem();
            }
            if (mat == Materials.Titanium && ModuleAdvancedTanks.TITANIUM != null
                && ModuleAdvancedTanks.TITANIUM.TANK_VALVE != null) {
                return ModuleAdvancedTanks.TITANIUM.TANK_VALVE.getItem();
            }
            if (mat == Materials.TungstenSteel && ModuleAdvancedTanks.TUNGSTENSTEEL != null
                && ModuleAdvancedTanks.TUNGSTENSTEEL.TANK_VALVE != null) {
                return ModuleAdvancedTanks.TUNGSTENSTEEL.TANK_VALVE.getItem();
            }
            if (mat == Materials.Neutronium && ModuleAdvancedTanks.NEUTRONIUM != null
                && ModuleAdvancedTanks.NEUTRONIUM.TANK_VALVE != null) {
                return ModuleAdvancedTanks.NEUTRONIUM.TANK_VALVE.getItem();
            }
            if (mat == Materials.Aluminium && ModuleAdvancedTanks.ALUMINIUM != null
                && ModuleAdvancedTanks.ALUMINIUM.TANK_VALVE != null) {
                return ModuleAdvancedTanks.ALUMINIUM.TANK_VALVE.getItem();
            }
            if (EnumMachineBeta.TANK_STEEL_VALVE != null) {
                return EnumMachineBeta.TANK_STEEL_VALVE.getItem();
            }
            return new ItemStack(net.minecraft.init.Items.stick, 1);
        } catch (Throwable t) {
            return new ItemStack(net.minecraft.init.Items.stick, 1);
        }
    }

    public static ItemStack getTankGaugeItem(Materials mat) {
        try {
            if (mat == Materials.CastIron && EnumMachineBeta.TANK_IRON_GAUGE != null) {
                return EnumMachineBeta.TANK_IRON_GAUGE.getItem();
            }
            if (mat == Materials.Steel && EnumMachineBeta.TANK_STEEL_GAUGE != null) {
                return EnumMachineBeta.TANK_STEEL_GAUGE.getItem();
            }
            if (mat == Materials.StainlessSteel && ModuleAdvancedTanks.STAINLESS != null
                && ModuleAdvancedTanks.STAINLESS.TANK_GAUGE != null) {
                return ModuleAdvancedTanks.STAINLESS.TANK_GAUGE.getItem();
            }
            if (mat == Materials.Titanium && ModuleAdvancedTanks.TITANIUM != null
                && ModuleAdvancedTanks.TITANIUM.TANK_GAUGE != null) {
                return ModuleAdvancedTanks.TITANIUM.TANK_GAUGE.getItem();
            }
            if (mat == Materials.TungstenSteel && ModuleAdvancedTanks.TUNGSTENSTEEL != null
                && ModuleAdvancedTanks.TUNGSTENSTEEL.TANK_GAUGE != null) {
                return ModuleAdvancedTanks.TUNGSTENSTEEL.TANK_GAUGE.getItem();
            }
            if (mat == Materials.Neutronium && ModuleAdvancedTanks.NEUTRONIUM != null
                && ModuleAdvancedTanks.NEUTRONIUM.TANK_GAUGE != null) {
                return ModuleAdvancedTanks.NEUTRONIUM.TANK_GAUGE.getItem();
            }
            if (mat == Materials.Aluminium && ModuleAdvancedTanks.ALUMINIUM != null
                && ModuleAdvancedTanks.ALUMINIUM.TANK_GAUGE != null) {
                return ModuleAdvancedTanks.ALUMINIUM.TANK_GAUGE.getItem();
            }
            if (EnumMachineBeta.TANK_STEEL_GAUGE != null) {
                return EnumMachineBeta.TANK_STEEL_GAUGE.getItem();
            }
            return new ItemStack(net.minecraft.init.Items.stick, 1);
        } catch (Throwable t) {
            return new ItemStack(net.minecraft.init.Items.stick, 1);
        }
    }

    public static IStructureElement<MTECoolantPump> ofCornerFrame() {
        return new IStructureElement<MTECoolantPump>() {

            @Override
            public boolean check(MTECoolantPump t, World world, int x, int y, int z) {
                Block block = world.getBlock(x, y, z);
                if (block instanceof BlockFrameBox) {
                    Materials mat = BlockFrameBox.getMaterial(world.getBlockMetadata(x, y, z));
                    if (t.mTankMaterial != null) {
                        return CoolantPipingRegistry.areMaterialsEqual(mat, t.mTankMaterial);
                    }
                    return LoopGraphCrawler.isAllowedCoolantPipeMaterial(mat);
                }
                return false;
            }

            @Override
            public boolean spawnHint(MTECoolantPump t, World world, int x, int y, int z, ItemStack trigger) {
                return GTStructureUtility.<MTECoolantPump>ofFrame(getMaterialFromTrigger(trigger))
                    .spawnHint(t, world, x, y, z, trigger);
            }

            @Override
            public boolean placeBlock(MTECoolantPump t, World world, int x, int y, int z, ItemStack trigger) {
                return GTStructureUtility.<MTECoolantPump>ofFrame(getMaterialFromTrigger(trigger))
                    .placeBlock(t, world, x, y, z, trigger);
            }

            @Override
            public PlaceResult survivalPlaceBlock(MTECoolantPump t, World world, int x, int y, int z, ItemStack trigger,
                IItemSource s, EntityPlayerMP actor, Consumer<IChatComponent> chatter) {
                return GTStructureUtility.<MTECoolantPump>ofFrame(getMaterialFromTrigger(trigger))
                    .survivalPlaceBlock(t, world, x, y, z, trigger, s, actor, chatter);
            }

            @Override
            public PlaceResult survivalPlaceBlock(MTECoolantPump t, World world, int x, int y, int z, ItemStack trigger,
                AutoPlaceEnvironment env) {
                return GTStructureUtility.<MTECoolantPump>ofFrame(getMaterialFromTrigger(trigger))
                    .survivalPlaceBlock(t, world, x, y, z, trigger, env);
            }

            @Override
            public BlocksToPlace getBlocksToPlace(MTECoolantPump t, World world, int x, int y, int z, ItemStack trigger,
                AutoPlaceEnvironment env) {
                return GTStructureUtility.<MTECoolantPump>ofFrame(getMaterialFromTrigger(trigger))
                    .getBlocksToPlace(t, world, x, y, z, trigger, env);
            }
        };
    }

    public static IStructureElement<MTECoolantPump> ofTankWall() {
        return new IStructureElement<MTECoolantPump>() {

            private IStructureElement<MTECoolantPump> getElement(ItemStack trigger) {
                Materials mat = getMaterialFromTrigger(trigger);
                ItemStack stack = getTankWallItem(mat);
                if (stack == null || stack.getItem() == null) {
                    stack = EnumMachineBeta.TANK_STEEL_WALL != null ? EnumMachineBeta.TANK_STEEL_WALL.getItem() : null;
                }
                Block block = (stack != null && stack.getItem() != null) ? Block.getBlockFromItem(stack.getItem())
                    : null;
                if (block == null) {
                    block = Block.getBlockFromName("stone");
                }
                int meta = stack != null ? stack.getItemDamage() : 0;
                return StructureUtility.ofBlock(block, meta);
            }

            @Override
            public boolean check(MTECoolantPump t, World world, int x, int y, int z) {
                TileEntity te = world.getTileEntity(x, y, z);
                if (te instanceof TileMultiBlock) {
                    TileMultiBlock tmb = (TileMultiBlock) te;
                    Materials mat = getRailcraftTankMaterial(tmb);
                    if (t.mTankMaterial != null) {
                        return CoolantPipingRegistry.areMaterialsEqual(mat, t.mTankMaterial);
                    }
                    return LoopGraphCrawler.isAllowedCoolantPipeMaterial(mat);
                }
                return false;
            }

            @Override
            public boolean spawnHint(MTECoolantPump t, World world, int x, int y, int z, ItemStack trigger) {
                return getElement(trigger).spawnHint(t, world, x, y, z, trigger);
            }

            @Override
            public boolean placeBlock(MTECoolantPump t, World world, int x, int y, int z, ItemStack trigger) {
                return getElement(trigger).placeBlock(t, world, x, y, z, trigger);
            }

            @Override
            public PlaceResult survivalPlaceBlock(MTECoolantPump t, World world, int x, int y, int z, ItemStack trigger,
                IItemSource s, EntityPlayerMP actor, Consumer<IChatComponent> chatter) {
                return getElement(trigger).survivalPlaceBlock(t, world, x, y, z, trigger, s, actor, chatter);
            }

            @Override
            public PlaceResult survivalPlaceBlock(MTECoolantPump t, World world, int x, int y, int z, ItemStack trigger,
                AutoPlaceEnvironment env) {
                return getElement(trigger).survivalPlaceBlock(t, world, x, y, z, trigger, env);
            }

            @Override
            public BlocksToPlace getBlocksToPlace(MTECoolantPump t, World world, int x, int y, int z, ItemStack trigger,
                AutoPlaceEnvironment env) {
                return getElement(trigger).getBlocksToPlace(t, world, x, y, z, trigger, env);
            }
        };
    }

    private static String[] makePumpBaseShape0(int width) {
        String[] rows = new String[width];
        for (int r = 0; r < width; r++) {
            char[] row = new char[width];
            for (int c = 0; c < width; c++) {
                if ((r == 0 || r == width - 1) && (c == 0 || c == width - 1)) {
                    row[c] = 'F';
                } else if (r == 0 && c == width / 2) {
                    row[c] = '~';
                } else {
                    row[c] = 'C';
                }
            }
            rows[r] = new String(row);
        }
        return rows;
    }

    private static String[] makePumpBaseShape1(int width) {
        String[] rows = new String[width];
        for (int r = 0; r < width; r++) {
            char[] row = new char[width];
            for (int c = 0; c < width; c++) {
                if ((r == 0 || r == width - 1) && (c == 0 || c == width - 1)) {
                    row[c] = 'F';
                } else {
                    row[c] = 'C';
                }
            }
            rows[r] = new String(row);
        }
        return rows;
    }

    private static String[] makeTankBottomShape(int width) {
        String[] rows = new String[width];
        for (int r = 0; r < width; r++) {
            char[] row = new char[width];
            for (int c = 0; c < width; c++) {
                row[c] = 'w';
            }
            rows[r] = new String(row);
        }
        return rows;
    }

    private static String[] makeTankMidShape(int width) {
        String[] rows = new String[width];
        for (int r = 0; r < width; r++) {
            char[] row = new char[width];
            for (int c = 0; c < width; c++) {
                if (r == 0 || r == width - 1 || c == 0 || c == width - 1) {
                    row[c] = 'w';
                } else {
                    row[c] = ' ';
                }
            }
            rows[r] = new String(row);
        }
        return rows;
    }

    private static String[] makeTankTopShape(int width) {
        return makeTankBottomShape(width);
    }

    private static IStructureDefinition<MTECoolantPump> STRUCTURE_DEFINITION = null;

    @Override
    public IStructureDefinition<MTECoolantPump> getStructureDefinition() {
        if (STRUCTURE_DEFINITION == null) {
            StructureDefinition.Builder<MTECoolantPump> b = StructureDefinition.<MTECoolantPump>builder();
            for (int w : new int[] { 3, 5, 7, 9 }) {
                b.addShape(
                    "pump_" + w + "_base_0",
                    StructureUtility.transpose(new String[][] { makePumpBaseShape0(w) }))
                    .addShape(
                        "pump_" + w + "_base_1",
                        StructureUtility.transpose(new String[][] { makePumpBaseShape1(w) }))
                    .addShape(
                        "tank_" + w + "_bottom",
                        StructureUtility.transpose(new String[][] { makeTankBottomShape(w) }))
                    .addShape("tank_" + w + "_mid", StructureUtility.transpose(new String[][] { makeTankMidShape(w) }))
                    .addShape("tank_" + w + "_top", StructureUtility.transpose(new String[][] { makeTankTopShape(w) }));
            }
            Block casing = GregTechAPI.sBlockCasings4 != null ? GregTechAPI.sBlockCasings4
                : Block.getBlockFromName("stone");
            Block hintBlock = StructureLibAPI.getBlockHint() != null ? StructureLibAPI.getBlockHint()
                : Block.getBlockFromName("stone");
            b.addShape(
                "pump_3x3",
                StructureUtility.transpose(new String[][] { makePumpBaseShape0(3), makePumpBaseShape1(3) }))
                .addElement('F', ofCornerFrame())
                .addElement('w', ofTankWall())
                .addElement(
                    'C',
                    StructureUtility.ofChain(
                        GTStructureUtility.ofHatchAdder(MTECoolantPump::addBottomHatch, 48 + 2, hintBlock, 0),
                        StructureUtility.ofBlock(casing, 2)));
            STRUCTURE_DEFINITION = b.build();
        }
        return STRUCTURE_DEFINITION;
    }

    @Override
    public void construct(ItemStack stackSize, boolean hintsOnly) {
        int width = getWidthFromTrigger(stackSize);
        int height = getHeightFromTrigger(stackSize);
        int hOffset = width / 2;
        int dOffset = 0;

        buildPiece("pump_" + width + "_base_0", stackSize, hintsOnly, hOffset, 0, dOffset);
        buildPiece("pump_" + width + "_base_1", stackSize, hintsOnly, hOffset, 1, dOffset);
        buildPiece("tank_" + width + "_bottom", stackSize, hintsOnly, hOffset, 2, dOffset);
        for (int i = 1; i < height - 1; i++) {
            buildPiece("tank_" + width + "_mid", stackSize, hintsOnly, hOffset, 2 + i, dOffset);
        }
        buildPiece("tank_" + width + "_top", stackSize, hintsOnly, hOffset, 2 + height - 1, dOffset);
    }

    @Override
    public int survivalConstruct(ItemStack stackSize, int elementBudget, ISurvivalBuildEnvironment env) {
        if (mMachine) return -1;
        int width = getWidthFromTrigger(stackSize);
        int height = getHeightFromTrigger(stackSize);
        int hOffset = width / 2;
        int dOffset = 0;

        int built;
        built = survivalBuildPiece(
            "pump_" + width + "_base_0",
            stackSize,
            hOffset,
            0,
            dOffset,
            elementBudget,
            env,
            false,
            true);
        if (built >= 0) return built;
        built = survivalBuildPiece(
            "pump_" + width + "_base_1",
            stackSize,
            hOffset,
            1,
            dOffset,
            elementBudget,
            env,
            false,
            true);
        if (built >= 0) return built;
        built = survivalBuildPiece(
            "tank_" + width + "_bottom",
            stackSize,
            hOffset,
            2,
            dOffset,
            elementBudget,
            env,
            false,
            true);
        if (built >= 0) return built;
        for (int i = 1; i < height - 1; i++) {
            built = survivalBuildPiece(
                "tank_" + width + "_mid",
                stackSize,
                hOffset,
                2 + i,
                dOffset,
                elementBudget,
                env,
                false,
                true);
            if (built >= 0) return built;
        }
        return survivalBuildPiece(
            "tank_" + width + "_top",
            stackSize,
            hOffset,
            2 + height - 1,
            dOffset,
            elementBudget,
            env,
            false,
            true);
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(StatCollector.translateToLocal("gt.multiblock.coolant_pump.machine_type"))
            .addInfo(StatCollector.translateToLocal("gt.multiblock.coolant_pump.desc1"))
            .addInfo(StatCollector.translateToLocal("gt.multiblock.coolant_pump.desc2"))
            .addInfo(StatCollector.translateToLocal("gt.multiblock.coolant_pump.desc3"))
            .addInfo(StatCollector.translateToLocal("gt.multiblock.coolant_pump.desc4"))
            .addPerfectOCInfo()
            .addSeparator()
            .beginStructureBlock(3, 2, 3, false)
            .addController(StatCollector.translateToLocal("gt.multiblock.coolant_pump.structure.controller"))
            .addCasingInfoMin(StatCollector.translateToLocal("gt.multiblock.coolant_pump.structure.casings"), 5, false)
            .addOtherStructurePart(
                StatCollector.translateToLocal("gt.multiblock.coolant_pump.structure.frames"),
                StatCollector.translateToLocal("gt.multiblock.coolant_pump.structure.frames_pos"))
            .addEnergyHatch(StatCollector.translateToLocal("gt.multiblock.coolant_pump.structure.energy"), 1)
            .addMaintenanceHatch(StatCollector.translateToLocal("gt.multiblock.coolant_pump.structure.maintenance"), 1)
            .addOtherStructurePart(
                StatCollector.translateToLocal("gt.multiblock.coolant_pump.structure.hatch_discharge"),
                StatCollector.translateToLocal("gt.multiblock.coolant_pump.structure.hatch_discharge_pos"))
            .addOtherStructurePart(
                StatCollector.translateToLocal("gt.multiblock.coolant_pump.structure.hatch_suction"),
                StatCollector.translateToLocal("gt.multiblock.coolant_pump.structure.hatch_suction_pos"))
            .addOtherStructurePart(
                StatCollector.translateToLocal("gt.multiblock.coolant_pump.structure.rc_tank"),
                StatCollector.translateToLocal("gt.multiblock.coolant_pump.structure.rc_tank_pos"))
            .addSubChannel(CoolantStructureChannels.STRUCTURE_WIDTH)
            .addSubChannel(CoolantStructureChannels.STRUCTURE_HEIGHT)
            .toolTipFinisher(StatCollector.translateToLocal("gt.multiblock.coolantloops.finisher"));
        return tt;
    }
}
