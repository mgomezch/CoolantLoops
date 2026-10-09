package com.gtnewhorizons.coolantloops.common.metatileentity.multi;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizons.coolantloops.common.metatileentity.CoolantStructureChannels;
import com.gtnewhorizons.coolantloops.common.metatileentity.hatch.MTEHatchPressurizedFluid;
import com.gtnewhorizons.coolantloops.common.metatileentity.multi.structure.CoolantPumpStructure;
import com.gtnewhorizons.coolantloops.common.metatileentity.multi.util.CoolantPumpReservoir;
import com.gtnewhorizons.coolantloops.common.tileentity.TileEntityLoopInstrument;
import com.gtnewhorizons.coolantloops.common.tileentity.TileEntityManifold;
import com.gtnewhorizons.coolantloops.common.util.CoolantFluidHelper;
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
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.api.util.TurbineStatCalculator;
import gregtech.common.blocks.BlockFrameBox;

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

    // Reservoir management
    protected final CoolantPumpReservoir mReservoir = new CoolantPumpReservoir(this);
    protected boolean mHasEverBeenEnabled = false;

    public boolean isMachineFormed() {
        return mMachine;
    }

    public int getWidth() {
        return mWidth;
    }

    public void setWidth(int width) {
        this.mWidth = width;
    }

    public int getLength() {
        return mLength;
    }

    public void setLength(int length) {
        this.mLength = length;
    }

    public void setLoopStatus(String loopStatus) {
        this.mLoopStatus = loopStatus;
    }

    public CoolantPumpReservoir getReservoir() {
        return mReservoir;
    }

    public boolean isDischargeHatchDisabled() {
        if (mDischargeHatch == null) return false;
        return !mDischargeHatch.isAllowedToWork();
    }

    public long getSimulatedTankCapacityLiters() {
        return mReservoir.getSimulatedTankCapacityLiters();
    }

    public void setSimulatedTankCapacityLiters(long capacity) {
        mReservoir.setSimulatedTankCapacityLiters(capacity);
    }

    public long getSimulatedCoolantLiters() {
        return mReservoir.getSimulatedCoolantLiters();
    }

    public void setSimulatedCoolantLiters(long liters) {
        mReservoir.setSimulatedCoolantLiters(liters);
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
        return mReservoir.verifyRailcraftTankAbove(aBaseMetaTileEntity);
    }

    public static boolean isPlainRegularWater(net.minecraftforge.fluids.Fluid fluid) {
        return CoolantFluidHelper.isPlainRegularWater(fluid);
    }

    public static boolean isPlainRegularWater(FluidStack stack) {
        return CoolantFluidHelper.isPlainRegularWater(stack);
    }

    public static boolean isLiquidNuclearFuel(FluidStack stack) {
        return CoolantFluidHelper.isLiquidNuclearFuel(stack);
    }

    public static boolean isLiquidNuclearFuel(String name) {
        return CoolantFluidHelper.isLiquidNuclearFuel(name);
    }

    public static boolean isGaseousFluid(net.minecraftforge.fluids.Fluid fluid, FluidStack stack) {
        return CoolantFluidHelper.isGaseousFluid(fluid, stack);
    }

    public static boolean isGaseousFluid(FluidStack stack) {
        return CoolantFluidHelper.isGaseousFluid(stack);
    }

    public static boolean isMoltenFluid(net.minecraftforge.fluids.Fluid fluid, FluidStack stack,
        CoolantFluidProperty prop) {
        return CoolantFluidHelper.isMoltenFluid(fluid, stack, prop);
    }

    public static boolean isMoltenFluid(FluidStack stack) {
        return CoolantFluidHelper.isMoltenFluid(stack);
    }

    public static double getDeclaredGregTechFluidTemperatureCelsius(net.minecraftforge.fluids.Fluid fluid,
        FluidStack stack, CoolantFluidProperty prop) {
        return CoolantFluidHelper.getDeclaredGregTechFluidTemperatureCelsius(fluid, stack, prop);
    }

    public static double getFluidStackTemperatureCelsius(FluidStack stack) {
        return CoolantFluidHelper.getFluidStackTemperatureCelsius(stack);
    }

    protected double mSimulatedBiomeTempCelsius = 20.0;

    public void setSimulatedBiomeTemperatureCelsius(double temp) {
        this.mSimulatedBiomeTempCelsius = temp;
    }

    public static double calculateAmbientTemperature(World world, int x, int y, int z) {
        return CoolantFluidHelper.calculateAmbientTemperature(world, x, y, z);
    }

    public double getBiomeTemperatureCelsius() {
        IGregTechTileEntity te = getBaseMetaTileEntity();
        if (te != null && te.getWorld() != null) {
            return calculateAmbientTemperature(te.getWorld(), te.getXCoord(), te.getYCoord(), te.getZCoord());
        }
        return mSimulatedBiomeTempCelsius;
    }

    public double getCoolantTemperatureCelsius() {
        if (mEngine != null && mEngine.getSegments() != null
            && !mEngine.getSegments()
                .isEmpty()) {
            if (mLoopState == LoopState.CIRCULATING || mEngine.getVolumetricFlowRate() > 1e-5) {
                return mEngine.getMinLoopTempCelsius();
            }
        }
        return getBiomeTemperatureCelsius();
    }

    public FluidStack getReservoirFluid() {
        return mReservoir.getReservoirFluid();
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

        // Validate fluid in reservoir / loop: liquid nuclear fuel fails pump and destroys impeller immediately!
        FluidStack reservoirFluid = getReservoirFluid();
        if (reservoirFluid != null && reservoirFluid.getFluid() != null) {
            if (isLiquidNuclearFuel(reservoirFluid)) {
                failOnLiquidNuclearFuel(
                    reservoirFluid.getFluid()
                        .getName());
                return true;
            }
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
                mLoopStatus = "Pump refused to start: Gaseous fluid (" + reservoirFluid.getFluid()
                    .getName() + ") cannot be used as a coolant!";
                mEngine.step(0.05);
                return true;
            }
        } else if (mEngine.getFluid() != null) {
            if (isLiquidNuclearFuel(
                mEngine.getFluid()
                    .getFluidName())) {
                failOnLiquidNuclearFuel(
                    mEngine.getFluid()
                        .getFluidName());
                return true;
            }
            if (mEngine.getFluid()
                .isPlainWater()) {
                mLoopState = LoopState.STOPPED;
                mEngine.setPumpPowered(false);
                mEngine.setPumpMechanicalPowerWatts(0.0);
                mEngine.setBraking(false);
                mLoopStatus = "Pump refused to start: Plain regular water cannot be used in a coolant loop! Use Distilled Water.";
                mEngine.step(0.05);
                return true;
            }
        }

        // Validate molten fluid temperature
        CoolantFluidProperty currentProp = mEngine.getFluid();
        if (currentProp == null && reservoirFluid != null) {
            currentProp = CoolantFluidProperty.get(
                reservoirFluid.getFluid()
                    .getName());
        }
        boolean isMolten = isMoltenFluid(
            reservoirFluid != null ? reservoirFluid.getFluid() : null,
            reservoirFluid,
            currentProp);

        if (isMolten) {
            double declaredMelting = getDeclaredGregTechFluidTemperatureCelsius(
                reservoirFluid != null ? reservoirFluid.getFluid() : null,
                reservoirFluid,
                currentProp);

            // Active circulation check: if circulating, dropping below melting point causes catastrophic solidification
            // explosion!
            if (mLoopState == LoopState.CIRCULATING || mEngine.getVolumetricFlowRate() > 1e-5) {
                double minLoopTemp = mEngine.getMinLoopTempCelsius();
                if (minLoopTemp < declaredMelting) {
                    mEngine.setRuptured(true);
                    mEngine.setFailureReason(
                        String.format(
                            "Catastrophic coolant solidification: Coolant temperature (%.1f °C) dropped below declared GregTech melting point (%.1f °C) for %s! Solidified plug clogged circulating loop.",
                            minLoopTemp,
                            declaredMelting,
                            currentProp != null ? currentProp.getFluidName() : "molten fluid"));
                    triggerCatastrophicExplosion(mEngine.getFailureReason());
                    return false;
                }
            } else {
                // Not circulating (stopped, decelerating, filling, or about to accelerate): refuse to fill or
                // accelerate!
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
                        mEngine.setFailureReason(
                            String.format(
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

    public static boolean isValidRotor(ItemStack aStack) {
        if (aStack == null || aStack.getItem() == null) return false;
        if (isMetaGeneratedTool(aStack.getItem())) {
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
        if (mRotorStack != null) {
            if (isValidRotor(mRotorStack)) {
                return mRotorStack;
            }
            if (getBaseMetaTileEntity() == null || getBaseMetaTileEntity().getWorld() == null) {
                return mRotorStack;
            }
        }
        return null;
    }

    public boolean updateRotorEfficiency() {
        ItemStack rotor = getRotor();
        if (rotor != null && isMetaGeneratedTool(rotor.getItem())) {
            try {
                TurbineStatCalculator calc = new TurbineStatCalculator((MetaGeneratedTool) rotor.getItem(), rotor);
                mRotorEfficiency = calc.getBaseEfficiency();
                mRotorStack = rotor;
                return true;
            } catch (Throwable ignored) {}
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
        if (stack != null && isMetaGeneratedTool(stack.getItem())) {
            try {
                TurbineStatCalculator calc = new TurbineStatCalculator((MetaGeneratedTool) stack.getItem(), stack);
                this.mRotorEfficiency = calc.getBaseEfficiency();
            } catch (Throwable ignored) {}
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

    public void failOnLiquidNuclearFuel(String fluidName) {
        destroyImpeller();
        mLoopState = LoopState.STOPPED;
        mEngine.setPumpPowered(false);
        mEngine.setPumpMechanicalPowerWatts(0.0);
        mEngine.setBraking(false);
        mEngine.setVolumetricFlowRate(0.0);
        String name = fluidName != null ? fluidName : "liquid nuclear fuel";
        mLoopStatus = String.format(
            "Catastrophic pump failure: %s is a liquid nuclear fuel, not a coolant! Impeller disintegrated!",
            name);
    }

    public void destroyImpeller() {
        ItemStack controller = getControllerSlot();
        if (isValidRotor(controller)) {
            setInventorySlotContents(getControllerSlotIndex(), null);
        }
        mRotorStack = null;
        mRotorEfficiency = 0.0;
        IGregTechTileEntity te = getBaseMetaTileEntity();
        if (te != null && te.getWorld() != null && !te.getWorld().isRemote) {
            te.getWorld()
                .playSoundEffect(
                    te.getXCoord() + 0.5,
                    te.getYCoord() + 1.5,
                    te.getZCoord() + 0.5,
                    "random.break",
                    1.0f,
                    0.8f);
        }
    }

    private double mRotorDamageAccumulator = 0.0;

    public double getRotorDamageAccumulator() {
        return mRotorDamageAccumulator;
    }

    public void setRotorDamageAccumulator(double accumulator) {
        this.mRotorDamageAccumulator = accumulator;
    }

    protected void degradeRotor(long euPerTick) {
        ItemStack rotor = getRotor();
        if (rotor != null && rotor.getItemDamage() < rotor.getMaxDamage()) {
            boolean shouldTick = (getBaseMetaTileEntity() == null) || (getBaseMetaTileEntity().getTimer() % 100 == 0);
            if (shouldTick) {
                double gasFrac = getDissolvedGasFraction();
                double wearMultiplier = 1.0 + gasFrac; // k_cavit = 1.0
                mRotorDamageAccumulator += wearMultiplier;
                int dmg = (int) mRotorDamageAccumulator;
                if (dmg > 0) {
                    mRotorDamageAccumulator -= dmg;
                    rotor.setItemDamage(rotor.getItemDamage() + dmg);
                    if (rotor.getItemDamage() >= rotor.getMaxDamage()) {
                        if (getControllerSlot() == rotor) {
                            setInventorySlotContents(getControllerSlotIndex(), null);
                        }
                        mRotorStack = null; // Rotor broken!
                        mRotorEfficiency = 0.0;
                    }
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
        return mReservoir.drainFromReservoir(liters);
    }

    /**
     * Returns fluid from the coolant loop back into the Railcraft tank reservoir directly above the pump.
     */
    public int fillIntoReservoir(int liters) {
        return mReservoir.fillIntoReservoir(liters);
    }

    /**
     * Checks if the Railcraft tank reservoir directly above the pump is full.
     */
    public boolean isReservoirFull() {
        return mReservoir.isReservoirFull();
    }

    @Override
    public void consumeCoolant(long liters) {
        if (liters <= 0) return;
        drainFromReservoir((int) Math.min(Integer.MAX_VALUE, liters));
    }

    @Override
    public void addDissolvedGas(String gasName, long liters) {
        mReservoir.addDissolvedGas(gasName, liters);
    }

    @Override
    public double getDissolvedGasFraction() {
        return mReservoir.getDissolvedGasFraction();
    }

    @Override
    public long getDissolvedGasAmount(String gasName) {
        return mReservoir.getDissolvedGasAmount(gasName);
    }

    @Override
    public Map<String, Long> getDissolvedGases() {
        return mReservoir.getDissolvedGases();
    }

    @Override
    public synchronized long extractDissolvedGas(String gasName, long maxLiters) {
        return mReservoir.extractDissolvedGas(gasName, maxLiters);
    }

    @Override
    public long getTotalCoolantLiters() {
        return mReservoir.getTotalCoolantLiters();
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
        return CoolantPumpReservoir.getRailcraftTankMaterial(te);
    }

    @Override
    public String[] getInfoData() {
        String tankMatName = mTankMaterial != null ? mTankMaterial.mDefaultLocalName : "Unknown";
        double maxFlowRateLPerSec = mEngine.getMaxFlowRateLitersPerSecond();
        String maxFlowStr = (maxFlowRateLPerSec == Double.MAX_VALUE) ? "Unlimited"
            : String.format("%.1f L/s", maxFlowRateLPerSec);
        String hatchState = isDischargeHatchDisabled() ? "Disabled (stopping/draining)" : "Enabled";
        return new String[] { "State: " + mLoopState.name(), "Discharge hatch: " + hatchState,
            "Tank material: " + tankMatName,
            "Current flow: " + String.format("%.2f L/s", mEngine.getFlowRateLitersPerSecond()),
            "Max pipe flow: " + maxFlowStr,
            "Peak pressure: " + String.format("%.2f bar", mEngine.getPeakLoopPressureBar()),
            "Peak temp: " + String.format("%.1f C", mEngine.getPeakLoopTempCelsius()),
            "Loop fluid: " + String.format("%d / %d L", mCurrentFillLiters, mRequiredFillLiters),
            "Dissolved gas: " + String.format("%.2f%%", getDissolvedGasFraction() * 100.0) };
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        mReservoir.saveNBTData(aNBT);
        aNBT.setString("mLoopState", mLoopState.name());
        aNBT.setLong("mCurrentFillLiters", mCurrentFillLiters);
        aNBT.setLong("mRequiredFillLiters", mRequiredFillLiters);
        aNBT.setDouble("mRotorDamageAccumulator", mRotorDamageAccumulator);
        if (mTankMaterial != null && mTankMaterial.mName != null) {
            aNBT.setString("mTankMaterial", mTankMaterial.mName);
        }
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        mReservoir.loadNBTData(aNBT);
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
        if (aNBT.hasKey("mRotorDamageAccumulator")) {
            mRotorDamageAccumulator = aNBT.getDouble("mRotorDamageAccumulator");
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
        return CoolantPumpStructure.getMaterialForTier(tier);
    }

    public static Materials getMaterialFromTrigger(ItemStack trigger) {
        return CoolantPumpStructure.getMaterialFromTrigger(trigger);
    }

    public static int getWidthFromTrigger(ItemStack trigger) {
        return CoolantPumpStructure.getWidthFromTrigger(trigger);
    }

    public static int getHeightFromTrigger(ItemStack trigger) {
        return CoolantPumpStructure.getHeightFromTrigger(trigger);
    }

    public static ItemStack getTankWallItem(Materials mat) {
        return CoolantPumpStructure.getTankWallItem(mat);
    }

    public static ItemStack getTankValveItem(Materials mat) {
        return CoolantPumpStructure.getTankValveItem(mat);
    }

    public static ItemStack getTankGaugeItem(Materials mat) {
        return CoolantPumpStructure.getTankGaugeItem(mat);
    }

    public static IStructureElement<MTECoolantPump> ofCornerFrame() {
        return CoolantPumpStructure.ofCornerFrame();
    }

    public static IStructureElement<MTECoolantPump> ofTankWall() {
        return CoolantPumpStructure.ofTankWall();
    }

    public static String[] makePumpBaseShape0(int width) {
        return CoolantPumpStructure.makePumpBaseShape0(width);
    }

    public static String[] makePumpBaseShape1(int width) {
        return CoolantPumpStructure.makePumpBaseShape1(width);
    }

    public static String[] makeTankBottomShape(int width) {
        return CoolantPumpStructure.makeTankBottomShape(width);
    }

    public static String[] makeTankMidShape(int width) {
        return CoolantPumpStructure.makeTankMidShape(width);
    }

    public static String[] makeTankTopShape(int width) {
        return CoolantPumpStructure.makeTankTopShape(width);
    }

    @Override
    public IStructureDefinition<MTECoolantPump> getStructureDefinition() {
        return CoolantPumpStructure.getStructureDefinition();
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
