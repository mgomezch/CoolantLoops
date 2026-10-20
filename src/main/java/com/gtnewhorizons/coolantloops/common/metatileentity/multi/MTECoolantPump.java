package com.gtnewhorizons.coolantloops.common.metatileentity.multi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizons.coolantloops.common.config.CoolantLoopDataRegistry;
import com.gtnewhorizons.coolantloops.common.gui.CoolantLoopMachineGui;
import com.gtnewhorizons.coolantloops.common.gui.graph.CoolantLoopGraphData;
import com.gtnewhorizons.coolantloops.common.metatileentity.CoolantStructureChannels;
import com.gtnewhorizons.coolantloops.common.metatileentity.hatch.MTEHatchPressurizedFluid;
import com.gtnewhorizons.coolantloops.common.metatileentity.multi.structure.CoolantPumpStructure;
import com.gtnewhorizons.coolantloops.common.metatileentity.multi.util.CoolantPumpReservoir;
import com.gtnewhorizons.coolantloops.common.util.CoolantFluidHelper;
import com.gtnewhorizons.coolantloops.common.util.CoolantLocalization;
import com.gtnewhorizons.coolantloops.common.util.RotorThermalHelper;
import com.gtnewhorizons.coolantloops.engine.CoolantFluidProperty;
import com.gtnewhorizons.coolantloops.engine.CoolantLoopEngine;
import com.gtnewhorizons.coolantloops.engine.CoolantPipingRegistry;
import com.gtnewhorizons.coolantloops.engine.ICoolantLoopDevice;
import com.gtnewhorizons.coolantloops.engine.ICoolantLoopPump;
import com.gtnewhorizons.coolantloops.engine.LoopGraphCrawler;
import com.gtnewhorizons.coolantloops.engine.LoopSegment;
import com.gtnewhorizons.modularui.api.widget.IWidgetBuilder;
import com.gtnewhorizons.modularui.common.widget.ButtonWidget;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.HatchElement;
import gregtech.api.enums.ItemList;
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
import gregtech.api.structure.error.PositionedStructureError;
import gregtech.api.structure.error.StructureError;
import gregtech.api.structure.error.StructureErrors;
import gregtech.api.util.GTUtility;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.api.util.TurbineStatCalculator;
import gregtech.api.util.shutdown.ShutDownReasonRegistry;
import gregtech.common.blocks.BlockFrameBox;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;

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
    protected String mLoopStatus = CoolantLocalization.get("coolantloops.pump.status.waiting");
    protected Materials mTankMaterial = Materials.Steel;

    // Impeller slot
    protected ItemStack mRotorStack = null;
    protected double mRotorEfficiency = 0.85;
    protected Materials mRotorMaterial = null;
    protected Double mSimulatedFluidTempCelsius = null;

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
    protected long mFillRateLitersPerTick = (CoolantLoopDataRegistry.getPhysicsData() != null)
        ? CoolantLoopDataRegistry.getPhysicsData()
            .getFillRateLitersPerTick()
        : 1000L;

    // Reservoir management
    protected final CoolantPumpReservoir mReservoir = new CoolantPumpReservoir(this);
    protected boolean mHasEverBeenEnabled = false;
    protected boolean mPowerFailed = false;

    // Multi-tick loop crawling state
    protected long mLastCrawlStartTime = -1L;
    protected long mLastCrawlCompleteTime = -1L;
    protected LoopGraphCrawler.CrawlSession mActiveCrawl = null;

    public long getLastCrawlStartTime() {
        return mLastCrawlStartTime;
    }

    public long getLastCrawlCompleteTime() {
        return mLastCrawlCompleteTime;
    }

    public LoopGraphCrawler.CrawlSession getActiveCrawl() {
        return mActiveCrawl;
    }

    // Loop Graph Visualization & Cover Detection
    protected LoopGraphCrawler.CrawlResult mLastCrawlResult = null;
    protected boolean mSimulateComputerMonitorCover = false;

    public LoopGraphCrawler.CrawlResult getLastCrawlResult() {
        return mLastCrawlResult;
    }

    public void setSimulatedComputerMonitorCover(boolean simulate) {
        this.mSimulateComputerMonitorCover = simulate;
    }

    public boolean hasComputerMonitorCover() {
        if (mSimulateComputerMonitorCover) return true;
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base == null) return false;
        if (!ItemList.Cover_Screen.hasBeenSet()) return false;
        ItemStack screenStack = ItemList.Cover_Screen.get(1L);
        if (screenStack == null) return false;
        for (ForgeDirection side : ForgeDirection.VALID_DIRECTIONS) {
            ItemStack cover = base.getCoverItemAtSide(side);
            if (cover != null && GTUtility.areStacksEqual(cover, screenStack, true)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean allowCoverOnSide(ForgeDirection side, ItemStack coverItem) {
        if (ItemList.Cover_Screen.hasBeenSet()
            && GTUtility.areStacksEqual(coverItem, ItemList.Cover_Screen.get(1L), true)) {
            return true;
        }
        return super.allowCoverOnSide(side, coverItem);
    }

    public String getLoopGraphDataJson() {
        return CoolantLoopGraphData.buildFrom(this)
            .toJson();
    }

    public boolean isPowerFailed() {
        if (getBaseMetaTileEntity() != null && getBaseMetaTileEntity().wasShutdown()) {
            return getBaseMetaTileEntity().getLastShutDownReason() == ShutDownReasonRegistry.POWER_LOSS;
        }
        return mPowerFailed;
    }

    @Override
    public void onEnableWorking() {
        super.onEnableWorking();
        mPowerFailed = false;
    }

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

    public void addLoopDevice(ICoolantLoopDevice dev) {
        if (dev != null && !mLoopDevices.contains(dev)) {
            mLoopDevices.add(dev);
        }
    }

    public List<ICoolantLoopDevice> getLoopDevices() {
        return mLoopDevices;
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
            return new ITexture[] { TextureFactory.of(GregTechAPI.sBlockCasings2, 15),
                TextureFactory.of(
                    aActive ? Textures.BlockIcons.OVERLAY_FRONT_DIESEL_ENGINE_ACTIVE
                        : Textures.BlockIcons.OVERLAY_FRONT_DIESEL_ENGINE) };
        }
        return new ITexture[] { TextureFactory.of(GregTechAPI.sBlockCasings2, 15) };
    }

    @Override
    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack, List<StructureError> aErrors) {
        mDischargeHatch = null;
        mSuctionHatch = null;
        mEnergyHatches.clear();
        mMaintenanceHatches.clear();

        // Check Railcraft tank presence above pump
        boolean hasValidTank = verifyRailcraftTankAbove(aBaseMetaTileEntity);
        if (!hasValidTank) {
            if (mLoopStatus == null || mLoopStatus.isEmpty()
                || mLoopStatus.equals(CoolantLocalization.get("coolantloops.pump.status.formed"))) {
                mLoopStatus = CoolantLocalization.get("coolantloops.pump.status.missing_or_mismatched_tank");
            }
            aErrors.add(StructureErrors.of("coolantloops.structure.error.pump.missing_or_mismatched_tank"));
            return;
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
                    String matName = mTankMaterial != null ? mTankMaterial.mDefaultLocalName : "Matching";
                    if (!(block instanceof BlockFrameBox)) {
                        mLoopStatus = CoolantLocalization.format("coolantloops.pump.status.corner_frame", cx, cy, cz);
                        aErrors.add(
                            new PositionedStructureError(
                                cx,
                                cy,
                                cz,
                                Collections.singletonList(
                                    StatCollector.translateToLocalFormatted(
                                        "coolantloops.structure.error.pump.corner_frame",
                                        matName))));
                        return;
                    }
                    int meta = world.getBlockMetadata(cx, cy, cz);
                    Materials frameMat = BlockFrameBox.getMaterial(meta);
                    if (!CoolantPipingRegistry.areMaterialsEqual(frameMat, mTankMaterial)) {
                        mLoopStatus = CoolantLocalization.format(
                            "coolantloops.pump.status.corner_frame_mat",
                            cx,
                            cy,
                            cz,
                            frameMat != null ? frameMat.mDefaultLocalName : "unknown",
                            matName);
                        aErrors.add(
                            new PositionedStructureError(
                                cx,
                                cy,
                                cz,
                                Collections.singletonList(
                                    StatCollector.translateToLocalFormatted(
                                        "coolantloops.structure.error.pump.corner_frame",
                                        matName))));
                        return;
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
                            } else {
                                mLoopStatus = CoolantLocalization
                                    .format("coolantloops.pump.status.invalid_hatch", bx, by, bz);
                                aErrors.add(
                                    new PositionedStructureError(
                                        bx,
                                        by,
                                        bz,
                                        Collections.singletonList(
                                            StatCollector
                                                .translateToLocal("coolantloops.structure.error.pump.invalid_hatch"))));
                                return;
                            }
                        } else if (GregTechAPI.sBlockCasings2 != null) {
                            Block block = world.getBlock(bx, by, bz);
                            int meta = world.getBlockMetadata(bx, by, bz);
                            if (block != GregTechAPI.sBlockCasings2 || meta != 15) {
                                mLoopStatus = CoolantLocalization
                                    .format("coolantloops.pump.status.casing_req", bx, by, bz);
                                aErrors.add(
                                    new PositionedStructureError(
                                        bx,
                                        by,
                                        bz,
                                        Collections.singletonList(
                                            StatCollector
                                                .translateToLocal("coolantloops.structure.error.pump.casing_req"))));
                                return;
                            }
                        }
                    }
                }
            }

            for (MTEHatchPressurizedFluid ph : pressHatches) {
                if (ph.isOutput()) {
                    if (mDischargeHatch == null) {
                        mDischargeHatch = ph;
                    } else {
                        mLoopStatus = CoolantLocalization.get("coolantloops.pump.status.req_hatches");
                        aErrors.add(StructureErrors.of("coolantloops.structure.error.duplicate_pump_hatches"));
                        return;
                    }
                } else if (ph.isInput()) {
                    if (mSuctionHatch == null) {
                        mSuctionHatch = ph;
                    } else {
                        mLoopStatus = CoolantLocalization.get("coolantloops.pump.status.req_hatches");
                        aErrors.add(StructureErrors.of("coolantloops.structure.error.duplicate_pump_hatches"));
                        return;
                    }
                }
            }

            if (mDischargeHatch == null) {
                mLoopStatus = CoolantLocalization.get("coolantloops.pump.status.req_hatches");
                aErrors.add(StructureErrors.of("coolantloops.structure.error.missing_discharge_hatch"));
            }
            if (mSuctionHatch == null) {
                mLoopStatus = CoolantLocalization.get("coolantloops.pump.status.req_hatches");
                aErrors.add(StructureErrors.of("coolantloops.structure.error.missing_suction_hatch"));
            }

            checkOneMaintenanceHatch(aErrors);

            if (mEnergyHatches.isEmpty()) {
                checkHasEnergyHatch(aErrors);
            } else if (mEnergyHatches.size() > 2) {
                checkHatchMax(aErrors, HatchElement.Energy, 2);
            }

            if (!aErrors.isEmpty()) {
                return;
            }

            final int casingIndex = 16 + 15;
            for (MTEHatchPressurizedFluid ph : pressHatches) {
                ph.updateTexture(casingIndex);
            }
            for (MTEHatchMaintenance mh : mMaintenanceHatches) {
                mh.updateTexture(casingIndex);
            }
            for (MTEHatchEnergy eh : mEnergyHatches) {
                eh.updateTexture(casingIndex);
            }
            if (mDischargeHatch != null) {
                mDischargeHatch.updateTexture(casingIndex);
            }
            if (mSuctionHatch != null) {
                mSuctionHatch.updateTexture(casingIndex);
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
        if (aBaseMetaTileEntity != null && !aBaseMetaTileEntity.isAllowedToWork()) {
            aBaseMetaTileEntity.enableWorking();
        }
        mLoopStatus = CoolantLocalization.get("coolantloops.pump.status.formed");
        if (mActiveCrawl != null && !mActiveCrawl.isDone()) {
            return;
        }
        crawlLoopGraph();
    }

    @Override
    public boolean checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack) {
        return checkMachine_TT(aBaseMetaTileEntity);
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

    public static boolean isLava(String name) {
        return CoolantFluidHelper.isLava(name);
    }

    public static boolean isLava(net.minecraftforge.fluids.Fluid fluid, FluidStack stack) {
        return CoolantFluidHelper.isLava(fluid, stack);
    }

    public static boolean isLava(FluidStack stack) {
        return CoolantFluidHelper.isLava(stack);
    }

    public static boolean isNetherWorld(World world) {
        return CoolantFluidHelper.isNetherWorld(world);
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
        if (mReservoir != null) {
            double resTemp = mReservoir.getReservoirTemperatureCelsius();
            if (!Double.isNaN(resTemp)) {
                return resTemp;
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
    /**
     * Re-crawls the connected piping circuit from the discharge hatch back to suction.
     */
    public boolean crawlLoopGraph() {
        IGregTechTileEntity te = getBaseMetaTileEntity();
        if (te == null || te.getWorld() == null) {
            return false;
        }

        if (mDischargeHatch == null || mSuctionHatch == null) {
            mActiveCrawl = null;
            mLoopFormed = false;
            mLoopState = LoopState.EMPTY;
            mCurrentFillLiters = 0L;
            mLoopStatus = CoolantLocalization.get("coolantloops.pump.status.req_hatches");
            return false;
        }

        IGregTechTileEntity discTE = mDischargeHatch.getBaseMetaTileEntity();
        IGregTechTileEntity sucTE = mSuctionHatch.getBaseMetaTileEntity();

        if (discTE == null || sucTE == null) {
            mActiveCrawl = null;
            mLoopFormed = false;
            return false;
        }

        mLastCrawlStartTime = (te != null) ? te.getTimer() : System.currentTimeMillis();
        LoopGraphCrawler crawler = new LoopGraphCrawler(te.getWorld(), LoopGraphCrawler.DEFAULT_MAX_STEPS);
        mActiveCrawl = crawler.startSession(
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

        mActiveCrawl.step(LoopGraphCrawler.STEPS_PER_TICK);

        if (mActiveCrawl.isDone()) {
            mLastCrawlCompleteTime = (te != null) ? te.getTimer() : System.currentTimeMillis();
            LoopGraphCrawler.CrawlResult result = mActiveCrawl.getResult();
            mActiveCrawl = null;
            return applyCrawlResult(result);
        } else {
            mLoopFormed = false;
            mLoopStatus = CoolantLocalization.format(
                "coolantloops.pump.status.crawling",
                mActiveCrawl.getStepCount(),
                mActiveCrawl.getTicksElapsed());
            return false;
        }
    }

    public void stepActiveCrawl() {
        if (mActiveCrawl == null) return;
        mActiveCrawl.step(LoopGraphCrawler.STEPS_PER_TICK);
        if (mActiveCrawl.isDone()) {
            IGregTechTileEntity te = getBaseMetaTileEntity();
            mLastCrawlCompleteTime = (te != null) ? te.getTimer() : System.currentTimeMillis();
            LoopGraphCrawler.CrawlResult result = mActiveCrawl.getResult();
            mActiveCrawl = null;
            applyCrawlResult(result);
        } else {
            mLoopStatus = CoolantLocalization.format(
                "coolantloops.pump.status.crawling",
                mActiveCrawl.getStepCount(),
                mActiveCrawl.getTicksElapsed());
        }
    }

    public boolean applyCrawlResult(LoopGraphCrawler.CrawlResult result) {
        if (result == null || !result.isSuccess) {
            mLastCrawlResult = null;
            mLoopFormed = false;
            mLoopState = LoopState.EMPTY;
            mCurrentFillLiters = 0L;
            String reason = (result != null && result.failureReason != null) ? result.failureReason
                : CoolantLocalization.get("coolantloops.crawler.no_suction_path");
            if (reason.equals(CoolantLocalization.get("coolantloops.crawler.loop_too_large"))) {
                mLoopStatus = reason;
            } else {
                mLoopStatus = CoolantLocalization.format("coolantloops.pump.status.loop_disconnect", reason);
            }
            return false;
        }

        mLastCrawlResult = result;
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
            dev.setLoopPump(this);
            requiredFill += dev.getFillVolumeLiters();
        }
        mRequiredFillLiters = requiredFill;
        mLoopFormed = true;
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
            mLoopStatus = CoolantLocalization.format(
                "coolantloops.pump.status.filling_loop",
                mCurrentFillLiters,
                mRequiredFillLiters,
                mRequiredFillLiters > 0 ? (mCurrentFillLiters * 100.0 / mRequiredFillLiters) : 100.0);
        } else {
            mLoopState = LoopState.CIRCULATING;
            mLoopStatus = CoolantLocalization.format(
                "coolantloops.pump.status.loop_active",
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
            if (mActiveCrawl != null) {
                stepActiveCrawl();
            }
            stepCoolantLoop();
        }
    }

    public List<MTEHatchEnergy> getValidEnergyHatches() {
        List<MTEHatchEnergy> list = new ArrayList<>();
        if (mEnergyHatches != null) {
            for (MTEHatchEnergy hatch : mEnergyHatches) {
                if (hatch != null && (hatch.isValid() || hatch.getBaseMetaTileEntity() != null)) {
                    list.add(hatch);
                }
            }
        }
        return list;
    }

    public long getRequiredPowerDrawEU() {
        List<MTEHatchEnergy> hatches = getValidEnergyHatches();
        if (hatches.isEmpty()) {
            return 512L;
        }
        if (hatches.size() == 1) {
            int tier = Math.max(0, Math.min(hatches.get(0).mTier, GTValues.V.length - 1));
            return GTValues.V[tier];
        }
        int maxTier = 0;
        for (MTEHatchEnergy h : hatches) {
            if (h.mTier > maxTier) {
                maxTier = h.mTier;
            }
        }
        maxTier = Math.max(0, Math.min(maxTier, GTValues.V.length - 1));
        return 4L * GTValues.V[maxTier];
    }

    public boolean triggerPowerfail() {
        mPowerFailed = true;
        stopMachine(ShutDownReasonRegistry.POWER_LOSS);
        if (getBaseMetaTileEntity() != null) {
            getBaseMetaTileEntity().setActive(false);
            getBaseMetaTileEntity().setShutDownReason(ShutDownReasonRegistry.POWER_LOSS);
        }
        mLoopState = LoopState.STOPPED;
        mEngine.setPumpPowered(false);
        mEngine.setPumpMechanicalPowerWatts(0.0);
        mEngine.setBraking(false);
        mLoopStatus = ShutDownReasonRegistry.POWER_LOSS.getDisplayString();
        return stepCoastingAndCheckRupture();
    }

    protected boolean stepCoastingAndCheckRupture() {
        mEngine.step(0.05);
        processCoastingThermalExchange();
        if (mEngine.isRuptured()) {
            triggerCatastrophicExplosion(mEngine.getFailureReason(), mEngine.getRupturedSegment());
            return false;
        }
        return true;
    }

    /**
     * Executes thermal exchange across in-line devices while fluid is coasting down through inertia.
     */
    public void processCoastingThermalExchange() {
        double flowRate = mEngine.getVolumetricFlowRate();
        if (flowRate > 1e-6) {
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
        }
    }

    public boolean drainEnergy(long aEU) {
        if (aEU <= 0) {
            mPowerFailed = false;
            return true;
        }

        List<MTEHatchEnergy> hatches = getValidEnergyHatches();
        if (!hatches.isEmpty()) {
            long totalStored = 0L;
            for (MTEHatchEnergy h : hatches) {
                IGregTechTileEntity bte = h.getBaseMetaTileEntity();
                if (bte != null) {
                    totalStored += bte.getStoredEU();
                }
            }

            if (totalStored < aEU) {
                return false;
            }

            long remaining = aEU;
            for (MTEHatchEnergy h : hatches) {
                IGregTechTileEntity bte = h.getBaseMetaTileEntity();
                if (bte != null) {
                    long stored = bte.getStoredEU();
                    if (stored > 0) {
                        long toDrain = Math.min(stored, remaining);
                        bte.decreaseStoredEnergyUnits(toDrain, false);
                        remaining -= toDrain;
                        if (remaining <= 0) {
                            mPowerFailed = false;
                            return true;
                        }
                    }
                }
            }
            if (remaining <= 0) {
                mPowerFailed = false;
                return true;
            }
            return false;
        }

        if (getBaseMetaTileEntity() != null && getBaseMetaTileEntity().getStoredEU() >= aEU) {
            boolean drained = getBaseMetaTileEntity().decreaseStoredEnergyUnits(aEU, false);
            if (drained) {
                mPowerFailed = false;
            }
            return drained;
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

        if (mEngine.isRuptured()) {
            triggerCatastrophicExplosion(mEngine.getFailureReason(), mEngine.getRupturedSegment());
            return false;
        }

        IGregTechTileEntity baseTE = getBaseMetaTileEntity();
        if (baseTE == null) {
            return true;
        }

        for (ICoolantLoopDevice dev : mLoopDevices) {
            dev.setLoopPump(this);
        }

        mReservoir.updateReservoirState();

        // Draw electrical power if allowed to work (soft hammer / redstone control)
        boolean isWorking = isAllowedToWork();
        if (!isWorking) {
            if (baseTE != null) {
                baseTE.setActive(false);
            }
            mEngine.setPumpPowered(false);
            mEngine.setPumpMechanicalPowerWatts(0.0);
            mEngine.setBraking(false);
            if (isPowerFailed()) {
                mLoopStatus = ShutDownReasonRegistry.POWER_LOSS.getDisplayString();
            } else if (baseTE != null && baseTE.wasShutdown() && baseTE.getLastShutDownReason() != null) {
                mLoopStatus = baseTE.getLastShutDownReason()
                    .getDisplayString();
            } else if (mLoopState == LoopState.FILLING) {
                mLoopStatus = CoolantLocalization
                    .format("coolantloops.pump.status.filling_paused", mCurrentFillLiters, mRequiredFillLiters);
            } else if (mLoopState == LoopState.DECELERATING || mLoopState == LoopState.DRAINING) {
                mLoopStatus = CoolantLocalization
                    .format("coolantloops.pump.status.stopping_paused", mCurrentFillLiters, mRequiredFillLiters);
            } else {
                mLoopStatus = CoolantLocalization
                    .format("coolantloops.pump.status.disabled_coasting", mEngine.getVolumetricFlowRate() * 1000.0);
            }
            return stepCoastingAndCheckRupture();
        }

        // Validate fluid in reservoir / loop: liquid nuclear fuel fails pump and destroys impeller immediately!
        FluidStack reservoirFluid = getReservoirFluid();
        if (reservoirFluid != null && reservoirFluid.getFluid() != null) {
            if (isLiquidNuclearFuel(reservoirFluid)) {
                failOnLiquidNuclearFuel(
                    reservoirFluid.getFluid()
                        .getName());
                return stepCoastingAndCheckRupture();
            }
            if (isPlainRegularWater(reservoirFluid)) {
                mLoopState = LoopState.STOPPED;
                mEngine.setPumpPowered(false);
                mEngine.setPumpMechanicalPowerWatts(0.0);
                mEngine.setBraking(false);
                mLoopStatus = CoolantLocalization.get("coolantloops.pump.status.refuse_water");
                return true;
            }
            if (isGaseousFluid(reservoirFluid)) {
                mLoopState = LoopState.STOPPED;
                mEngine.setPumpPowered(false);
                mEngine.setPumpMechanicalPowerWatts(0.0);
                mEngine.setBraking(false);
                mLoopStatus = CoolantLocalization.format(
                    "coolantloops.pump.status.refuse_gas",
                    reservoirFluid.getFluid()
                        .getName());
                return true;
            }
            if (isLava(reservoirFluid)) {
                World world = getBaseMetaTileEntity() != null ? getBaseMetaTileEntity().getWorld() : null;
                if (!isNetherWorld(world)) {
                    mLoopState = LoopState.STOPPED;
                    mEngine.setPumpPowered(false);
                    mEngine.setPumpMechanicalPowerWatts(0.0);
                    mEngine.setBraking(false);
                    mLoopStatus = CoolantLocalization.get("coolantloops.pump.status.refuse_lava");
                    return true;
                }
            }
        } else if (mEngine.getFluid() != null) {
            if (isLiquidNuclearFuel(
                mEngine.getFluid()
                    .getFluidName())) {
                failOnLiquidNuclearFuel(
                    mEngine.getFluid()
                        .getFluidName());
                return stepCoastingAndCheckRupture();
            }
            if (mEngine.getFluid()
                .isPlainWater()) {
                mLoopState = LoopState.STOPPED;
                mEngine.setPumpPowered(false);
                mEngine.setPumpMechanicalPowerWatts(0.0);
                mEngine.setBraking(false);
                mLoopStatus = CoolantLocalization.get("coolantloops.pump.status.refuse_water");
                return true;
            }
            if (mEngine.getFluid()
                .isLava()) {
                World world = getBaseMetaTileEntity() != null ? getBaseMetaTileEntity().getWorld() : null;
                if (!isNetherWorld(world)) {
                    mLoopState = LoopState.STOPPED;
                    mEngine.setPumpPowered(false);
                    mEngine.setPumpMechanicalPowerWatts(0.0);
                    mEngine.setBraking(false);
                    mLoopStatus = CoolantLocalization.get("coolantloops.pump.status.refuse_lava");
                    return true;
                }
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

        World loopWorld = getBaseMetaTileEntity() != null ? getBaseMetaTileEntity().getWorld() : null;
        boolean isLavaNether = ((reservoirFluid != null && isLava(reservoirFluid))
            || (currentProp != null && currentProp.isLava())) && isNetherWorld(loopWorld);

        if (isMolten && !isLavaNether) {
            double declaredMelting = getDeclaredGregTechFluidTemperatureCelsius(
                reservoirFluid != null ? reservoirFluid.getFluid() : null,
                reservoirFluid,
                currentProp);

            // If the loop contains fluid and temperature drops below melting point, catastrophic solidification
            // rupture!
            if (mCurrentFillLiters > 0 && !mEngine.getSegments()
                .isEmpty()) {
                double minLoopTemp = mEngine.getMinLoopTempCelsius();
                if (minLoopTemp < declaredMelting) {
                    mEngine.setRuptured(true);
                    mEngine.setFailureReason(
                        CoolantLocalization.format(
                            "coolantloops.pump.status.catastrophic_solidification",
                            minLoopTemp,
                            declaredMelting,
                            currentProp != null ? currentProp.getFluidName() : "molten fluid"));
                    triggerCatastrophicExplosion(mEngine.getFailureReason(), mEngine.getRupturedSegment());
                    return false;
                }
            } else {
                // Loop is empty (before initial fill): refuse to fill if reservoir or segments are below melting point
                double resTemp = mReservoir.getReservoirTemperatureCelsius();
                if (Double.isNaN(resTemp)) {
                    resTemp = CoolantFluidHelper.getBaselineInletTemperatureCelsius(
                        reservoirFluid != null ? reservoirFluid.getFluid() : null,
                        reservoirFluid,
                        currentProp,
                        getBiomeTemperatureCelsius());
                }
                if (resTemp < declaredMelting) {
                    mLoopState = LoopState.STOPPED;
                    mEngine.setPumpPowered(false);
                    mEngine.setPumpMechanicalPowerWatts(0.0);
                    mEngine.setBraking(false);
                    mLoopStatus = CoolantLocalization.format(
                        "coolantloops.pump.status.refuse_solidify",
                        resTemp,
                        declaredMelting,
                        currentProp != null ? currentProp.getFluidName() : "molten fluid");
                    return true;
                } else if (!mEngine.getSegments()
                    .isEmpty()) {
                        double minLoopTemp = mEngine.getMinLoopTempCelsius();
                        if (minLoopTemp < declaredMelting) {
                            mLoopState = LoopState.STOPPED;
                            mEngine.setPumpPowered(false);
                            mEngine.setPumpMechanicalPowerWatts(0.0);
                            mEngine.setBraking(false);
                            mLoopStatus = CoolantLocalization.format(
                                "coolantloops.pump.status.refuse_solidify_coolant",
                                minLoopTemp,
                                declaredMelting,
                                currentProp != null ? currentProp.getFluidName() : "molten fluid");
                            return true;
                        }
                    }
            }
        }

        // Require valid turbine rotor impeller in controller slot
        if (!updateRotorEfficiency()) {
            mLoopState = LoopState.STOPPED;
            mEngine.setPumpPowered(false);
            mEngine.setPumpMechanicalPowerWatts(0.0);
            mEngine.setBraking(false);
            mLoopStatus = CoolantLocalization.get("coolantloops.pump.status.missing_rotor");
            return stepCoastingAndCheckRupture();
        }

        // Impeller melting check: if fluid temperature exceeds impeller melting point, melt catastrophically!
        double pumpFluidTemp = getPumpFluidTemperatureCelsius();
        Materials rotorMat = getRotorMaterial();
        double th = RotorThermalHelper.calculateHomologousTemperature(pumpFluidTemp, rotorMat);
        if (RotorThermalHelper.isMelted(th)) {
            failOnImpellerMelting(pumpFluidTemp, rotorMat);
            return stepCoastingAndCheckRupture();
        }

        // Check if high-pressure output hatch is disabled by machine controller cover
        boolean dischargeDisabled = isDischargeHatchDisabled();
        if (dischargeDisabled) {
            double flowRate = mEngine.getVolumetricFlowRate();
            if (flowRate > 1e-5) {
                // Phase A: Active deceleration down to zero
                checkMaintenance();
                double effFactor = getEfficiencyFactor();
                long requiredEU = getRequiredPowerDrawEU();
                boolean powered = drainEnergy(requiredEU);
                if (!powered) {
                    return triggerPowerfail();
                }
                if (baseTE != null) {
                    baseTE.setActive(true);
                }
                double wattsPerEu = (CoolantLoopDataRegistry.getPhysicsData() != null)
                    ? CoolantLoopDataRegistry.getPhysicsData()
                        .getPumpMechanicalPowerPerEU()
                    : 80.0;
                double mechanicalWatts = requiredEU * wattsPerEu * mRotorEfficiency * effFactor;
                degradeRotor(requiredEU);

                mLoopState = LoopState.DECELERATING;
                mEngine.setPumpPowered(true);
                mEngine.setPumpMechanicalPowerWatts(mechanicalWatts);
                mEngine.setBraking(true);

                // Advance fluid mechanics with active motor braking
                mEngine.step(0.05);

                if (mEngine.isRuptured()) {
                    triggerCatastrophicExplosion(mEngine.getFailureReason(), mEngine.getRupturedSegment());
                    return false;
                }

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
                if (baseTE != null) {
                    baseTE.setActive(false);
                }
                mEngine.setVolumetricFlowRate(0.0);
                mEngine.setBraking(false);
                mEngine.setPumpPowered(false);
                mEngine.setPumpMechanicalPowerWatts(0.0);

                if (mCurrentFillLiters > 0) {
                    if (isReservoirFull()) {
                        mLoopState = LoopState.STOPPED;
                        mLoopStatus = CoolantLocalization
                            .format("coolantloops.pump.status.draining_paused", mCurrentFillLiters);
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
                            mLoopStatus = CoolantLocalization.get("coolantloops.pump.status.drained_complete");
                        } else if (isReservoirFull()) {
                            mLoopState = LoopState.STOPPED;
                            mLoopStatus = CoolantLocalization
                                .format("coolantloops.pump.status.draining_paused", mCurrentFillLiters);
                        } else {
                            mLoopStatus = CoolantLocalization.format(
                                "coolantloops.pump.status.draining_loop",
                                mCurrentFillLiters,
                                mRequiredFillLiters);
                        }
                    }
                } else {
                    mLoopState = LoopState.EMPTY;
                    mLoopStatus = CoolantLocalization.get("coolantloops.pump.status.flow_stopped_empty");
                }
                return true;
            }
        }

        mEngine.setBraking(false);

        // Initial Loop Filling Phase (when hatch is enabled)
        if (mLoopState == LoopState.EMPTY || mLoopState == LoopState.DRAINING
            || mCurrentFillLiters < mRequiredFillLiters) {
            long requiredEU = getRequiredPowerDrawEU();
            if (!drainEnergy(requiredEU)) {
                return triggerPowerfail();
            }
            if (baseTE != null) {
                baseTE.setActive(true);
            }
            degradeRotor(requiredEU);

            mLoopState = LoopState.FILLING;
            // No flow acceleration, power or convective heat transfer during filling
            mEngine.setPumpPowered(false);
            mEngine.setPumpMechanicalPowerWatts(0.0);

            long needed = mRequiredFillLiters - mCurrentFillLiters;
            int toDraw = (int) Math.min(mFillRateLitersPerTick, needed);
            int drained = drainFromReservoir(toDraw);

            if (drained > 0) {
                mCurrentFillLiters += drained;
                double fillTemp = mReservoir.getReservoirTemperatureCelsius();
                if (Double.isNaN(fillTemp)) {
                    fillTemp = CoolantFluidHelper.getBaselineInletTemperatureCelsius(
                        reservoirFluid != null ? reservoirFluid.getFluid() : null,
                        reservoirFluid,
                        currentProp,
                        getBiomeTemperatureCelsius());
                }
                for (LoopSegment seg : mEngine.getSegments()) {
                    if (seg.getCurrentTemperatureCelsius() < fillTemp) {
                        seg.setCurrentTemperatureCelsius(fillTemp);
                    }
                }
                if (mCurrentFillLiters >= mRequiredFillLiters) {
                    mCurrentFillLiters = mRequiredFillLiters;
                    mLoopState = LoopState.CIRCULATING;
                    mLoopStatus = CoolantLocalization
                        .format("coolantloops.pump.status.filled_circulating", mCurrentFillLiters);
                } else {
                    mLoopStatus = CoolantLocalization.format(
                        "coolantloops.pump.status.filling_loop",
                        mCurrentFillLiters,
                        mRequiredFillLiters,
                        mRequiredFillLiters > 0 ? (mCurrentFillLiters * 100.0 / mRequiredFillLiters) : 100.0);
                }
            } else {
                // Reservoir ran out of fluid before loop filled!
                // Safe fail condition: stop cleanly WITHOUT EXPLOSION
                mLoopState = LoopState.FAILED;
                mLoopStatus = CoolantLocalization
                    .format("coolantloops.pump.status.filling_failed", mCurrentFillLiters, mRequiredFillLiters);
            }
            return true;
        }

        // Active Circulation Phase
        checkMaintenance();
        double effFactor = getEfficiencyFactor();
        long requiredEU = getRequiredPowerDrawEU();
        boolean hasPower = drainEnergy(requiredEU);
        if (!hasPower) {
            return triggerPowerfail();
        }
        if (baseTE != null) {
            baseTE.setActive(true);
        }

        // Refuse to accelerate flow if molten fluid is below declared GregTech melting point
        if (isMolten) {
            double minLoopTemp = mEngine.getMinLoopTempCelsius();
            double declaredMelting = getDeclaredGregTechFluidTemperatureCelsius(
                reservoirFluid != null ? reservoirFluid.getFluid() : null,
                reservoirFluid,
                currentProp);
            if (minLoopTemp < declaredMelting) {
                mEngine.setRuptured(true);
                mEngine.setFailureReason(
                    CoolantLocalization.format(
                        "coolantloops.pump.status.catastrophic_solidification",
                        minLoopTemp,
                        declaredMelting,
                        currentProp != null ? currentProp.getFluidName() : "molten fluid"));
                triggerCatastrophicExplosion(mEngine.getFailureReason(), mEngine.getRupturedSegment());
                return false;
            }
        }

        mLoopState = LoopState.CIRCULATING;
        mEngine.setPumpPowered(true);
        double wattsPerEu = (CoolantLoopDataRegistry.getPhysicsData() != null)
            ? CoolantLoopDataRegistry.getPhysicsData()
                .getPumpMechanicalPowerPerEU()
            : 80.0;
        double mechanicalWatts = requiredEU * wattsPerEu * mRotorEfficiency * effFactor;
        mEngine.setPumpMechanicalPowerWatts(mechanicalWatts);
        degradeRotor(requiredEU);

        // Synchronize absolute dissolved gases across engine and all segments
        if (mEngine != null) {
            Map<String, Long> gases = mReservoir.getDissolvedGases();
            mEngine.setDissolvedGases(gases);
            double gasFrac = getDissolvedGasFraction();
            mEngine.setDissolvedGasFraction(gasFrac);
            long loopLiters = mEngine.getTotalLoopVolumeLiters();
            for (LoopSegment seg : mEngine.getSegments()) {
                seg.setDissolvedGases(gases, loopLiters);
                seg.setDissolvedGasFraction(gasFrac);
            }
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

        // Check for catastrophic rupture
        if (mEngine.isRuptured()) {
            triggerCatastrophicExplosion(mEngine.getFailureReason(), mEngine.getRupturedSegment());
            return false;
        }

        return true;
    }

    @Override
    public boolean onRunningTick(ItemStack aStack) {
        return true;
    }

    /**
     * Trigger catastrophic pump explosion and drain 80% of fluid from the Railcraft tank.
     */
    protected void triggerCatastrophicExplosion(String reason) {
        triggerCatastrophicExplosion(reason, mEngine != null ? mEngine.getRupturedSegment() : null);
    }

    protected void triggerCatastrophicExplosion(String reason, LoopSegment rupturedSegment) {
        // Drain 80% of fluid from tank above
        drainFromReservoir((int) (getTotalCoolantLiters() * 0.80));

        IGregTechTileEntity te = getBaseMetaTileEntity();
        if (te != null && te.getWorld() != null && !te.getWorld().isRemote) {
            World world = te.getWorld();
            int x = te.getXCoord();
            int y = te.getYCoord();
            int z = te.getZCoord();

            // Explosion scaled to peak loop pressure
            float explosionStrength = Math.min(25.0f, (float) (4.0f + mEngine.getPeakLoopPressureBar() / 10.0));
            if (GregTechAPI.sMachineExplosions) {
                world.createExplosion(null, x + 0.5, y + 1.5, z + 0.5, explosionStrength, true);
                te.doExplosion((long) (explosionStrength * 1000));
            }

            spillCoolantFluid(world, rupturedSegment);
        }

        mLoopState = LoopState.FAILED;
        mEngine.setPumpPowered(false);
        mEngine.setPumpMechanicalPowerWatts(0.0);
        mEngine.setBraking(false);
        mLoopStatus = reason;
    }

    protected void spillCoolantFluid(World world, LoopSegment rupturedSegment) {
        if (world == null || world.isRemote) {
            return;
        }

        CoolantFluidProperty prop = mEngine.getFluid();
        FluidStack reservoirFluid = getReservoirFluid();
        if (prop == null && reservoirFluid != null && reservoirFluid.getFluid() != null) {
            prop = CoolantFluidProperty.get(
                reservoirFluid.getFluid()
                    .getName());
        }

        // Check per-fluid configuration
        if (prop != null && !prop.isSpillOnRupture()) {
            return;
        }

        Fluid forgeFluid = null;
        if (reservoirFluid != null && reservoirFluid.getFluid() != null) {
            forgeFluid = reservoirFluid.getFluid();
        } else if (prop != null) {
            forgeFluid = FluidRegistry.getFluid(prop.getFluidName());
        }

        if (forgeFluid == null || !forgeFluid.canBePlacedInWorld()) {
            return;
        }

        Block fluidBlock = forgeFluid.getBlock();
        if (fluidBlock == null) {
            return;
        }

        List<LoopGraphCrawler.BlockPosCoord> candidateOrigins = new ArrayList<>();
        if (rupturedSegment != null && rupturedSegment.hasPosition()) {
            candidateOrigins.add(
                new LoopGraphCrawler.BlockPosCoord(
                    rupturedSegment.getBlockX(),
                    rupturedSegment.getBlockY(),
                    rupturedSegment.getBlockZ()));
        }

        IGregTechTileEntity te = getBaseMetaTileEntity();
        if (te != null) {
            candidateOrigins.add(new LoopGraphCrawler.BlockPosCoord(te.getXCoord(), te.getYCoord(), te.getZCoord()));
        }

        List<LoopGraphCrawler.BlockPosCoord> validSpillPositions = new ArrayList<>();
        Set<LoopGraphCrawler.BlockPosCoord> seen = new HashSet<>();

        for (LoopGraphCrawler.BlockPosCoord origin : candidateOrigins) {
            for (ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
                int cx = origin.x + dir.offsetX;
                int cy = origin.y + dir.offsetY;
                int cz = origin.z + dir.offsetZ;
                LoopGraphCrawler.BlockPosCoord candidate = new LoopGraphCrawler.BlockPosCoord(cx, cy, cz);
                if (seen.add(candidate)) {
                    if (world.isAirBlock(cx, cy, cz)
                        || (world.getBlock(cx, cy, cz) != null && world.getBlock(cx, cy, cz)
                            .isReplaceable(world, cx, cy, cz))) {
                        validSpillPositions.add(candidate);
                    }
                }
            }
        }

        if (validSpillPositions.isEmpty()) {
            return;
        }

        Collections.shuffle(validSpillPositions);
        int randOffset = (world.rand != null) ? world.rand.nextInt(2) : 0;
        int spillCount = Math.min(validSpillPositions.size(), 1 + randOffset);

        for (int i = 0; i < spillCount; i++) {
            LoopGraphCrawler.BlockPosCoord pos = validSpillPositions.get(i);
            world.setBlock(pos.x, pos.y, pos.z, fluidBlock, 0, 3);
            world.notifyBlocksOfNeighborChange(pos.x, pos.y, pos.z, fluidBlock);
            world.markBlockForUpdate(pos.x, pos.y, pos.z);
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
                double eff = calc.getBaseEfficiency();
                mRotorEfficiency = eff > 0.0 ? eff : 1.0;
                mRotorStack = rotor;
                Materials mat = RotorThermalHelper.getRotorMaterial(rotor);
                mRotorMaterial = (mat != null && mat != Materials._NULL) ? mat : Materials.Steel;
                return true;
            } catch (Throwable ignored) {
                mRotorEfficiency = 1.0;
                mRotorStack = rotor;
                mRotorMaterial = Materials.Steel;
                return true;
            }
        }
        // Headless mock or test environment fallback
        if (getBaseMetaTileEntity() == null || getBaseMetaTileEntity().getWorld() == null) {
            return true;
        }
        mRotorStack = null;
        mRotorMaterial = null;
        return false;
    }

    public void setRotorStack(ItemStack stack) {
        this.mRotorStack = stack;
        this.mRotorMaterial = RotorThermalHelper.getRotorMaterial(stack);
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
    public Materials getRotorMaterial() {
        if (mRotorMaterial != null) return mRotorMaterial;
        ItemStack r = getRotor();
        if (r != null) {
            mRotorMaterial = RotorThermalHelper.getRotorMaterial(r);
        }
        return mRotorMaterial;
    }

    public void setRotorMaterial(Materials mat) {
        this.mRotorMaterial = mat;
    }

    public void setSimulatedFluidTemperatureCelsius(Double temp) {
        this.mSimulatedFluidTempCelsius = temp;
    }

    public Double getSimulatedFluidTemperatureCelsius() {
        return this.mSimulatedFluidTempCelsius;
    }

    @Override
    public double getPumpFluidTemperatureCelsius() {
        if (mSimulatedFluidTempCelsius != null) {
            return mSimulatedFluidTempCelsius;
        }
        if (mEngine != null && mEngine.getSegments() != null
            && !mEngine.getSegments()
                .isEmpty()) {
            List<LoopSegment> segs = mEngine.getSegments();
            LoopSegment suctionSeg = segs.get(segs.size() - 1);
            if (suctionSeg != null) {
                return suctionSeg.getCurrentTemperatureCelsius();
            }
        }
        return getBiomeTemperatureCelsius();
    }

    @Override
    public double getHomologousTemperature() {
        return RotorThermalHelper.calculateHomologousTemperature(getPumpFluidTemperatureCelsius(), getRotorMaterial());
    }

    @Override
    public double getThermalWearMultiplier() {
        return RotorThermalHelper.calculateThermalWearMultiplier(getHomologousTemperature());
    }

    @Override
    public double getRotorMeltingPointCelsius() {
        return RotorThermalHelper.getMeltingPointCelsius(getRotorMaterial());
    }

    @Override
    public double getRotorSofteningCelsius() {
        return RotorThermalHelper.getThermalSofteningCelsius(getRotorMaterial());
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
        // Do NOT abruptly zero flow rate! Fluid continues circulating through inertia and slowing with drag.
        String name = fluidName != null ? fluidName : "liquid nuclear fuel";
        mLoopStatus = CoolantLocalization.format("coolantloops.pump.status.catastrophic_fuel", name);
    }

    public void failOnImpellerMelting(double fluidTemp, Materials mat) {
        destroyImpeller();
        mLoopState = LoopState.STOPPED;
        mEngine.setPumpPowered(false);
        mEngine.setPumpMechanicalPowerWatts(0.0);
        mEngine.setBraking(false);
        // Do NOT abruptly zero flow rate! Fluid continues circulating through inertia and slowing with drag.
        String matName = mat != null ? RotorThermalHelper.getMaterialLocalizedName(mat) : "impeller";
        double meltC = RotorThermalHelper.getMeltingPointCelsius(mat);
        mLoopStatus = CoolantLocalization
            .format("coolantloops.pump.status.catastrophic_melt", fluidTemp, meltC, matName);
    }

    public void destroyImpeller() {
        ItemStack controller = getControllerSlot();
        if (isValidRotor(controller)) {
            setInventorySlotContents(getControllerSlotIndex(), null);
        }
        mRotorStack = null;
        mRotorEfficiency = 0.0;
        mRotorMaterial = null;
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

    public void degradeRotor(long euPerTick) {
        ItemStack rotor = getRotor();
        if (rotor == null) {
            return;
        }

        double fluidTempC = getPumpFluidTemperatureCelsius();
        Materials mat = getRotorMaterial();
        double th = RotorThermalHelper.calculateHomologousTemperature(fluidTempC, mat);
        if (RotorThermalHelper.isMelted(th)) {
            failOnImpellerMelting(fluidTempC, mat);
            return;
        }

        boolean shouldTick = (getBaseMetaTileEntity() == null) || (getBaseMetaTileEntity().getTimer() % 100 == 0);
        if (shouldTick) {
            double gasFrac = getDissolvedGasFraction();
            double cavitationFactor = 1.0 + gasFrac; // k_cavit = 1.0
            double thermalFactor = RotorThermalHelper.calculateThermalWearMultiplier(th);
            double totalWearMultiplier = cavitationFactor * thermalFactor;
            mRotorDamageAccumulator += totalWearMultiplier;
            int dmg = (int) mRotorDamageAccumulator;
            if (dmg > 0) {
                mRotorDamageAccumulator -= dmg;
                if (rotor.getItem() instanceof MetaGeneratedTool) {
                    MetaGeneratedTool tool = (MetaGeneratedTool) rotor.getItem();
                    tool.doDamage(rotor, dmg);
                    if (rotor.stackSize == 0
                        || MetaGeneratedTool.getToolDamage(rotor) >= MetaGeneratedTool.getToolMaxDamage(rotor)) {
                        if (getControllerSlot() == rotor
                            || (getControllerSlot() != null && getControllerSlot().stackSize == 0)) {
                            setInventorySlotContents(getControllerSlotIndex(), null);
                        }
                        mRotorStack = null; // Rotor broken!
                        mRotorEfficiency = 0.0;
                        mRotorMaterial = null;
                    }
                } else {
                    rotor.setItemDamage(rotor.getItemDamage() + dmg);
                    if (rotor.getMaxDamage() > 0 && rotor.getItemDamage() >= rotor.getMaxDamage()) {
                        if (getControllerSlot() == rotor) {
                            setInventorySlotContents(getControllerSlotIndex(), null);
                        }
                        mRotorStack = null; // Rotor broken!
                        mRotorEfficiency = 0.0;
                        mRotorMaterial = null;
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
    public double getFillFraction() {
        if (mRequiredFillLiters > 0) {
            return Math.min(1.0, (double) mCurrentFillLiters / mRequiredFillLiters);
        }
        return mLoopFormed ? 1.0 : 0.0;
    }

    @Override
    public String getLoopStateName() {
        return mLoopState != null ? mLoopState.name() : "STOPPED";
    }

    @Override
    public double getMaxFlowRateLitersPerSecond() {
        return mEngine != null ? mEngine.getMaxFlowRateLitersPerSecond() : 0.0;
    }

    @Override
    public double getPeakLoopPressureBar() {
        return mEngine != null ? mEngine.getPeakLoopPressureBar() : 1.0;
    }

    @Override
    public double getPeakLoopTempCelsius() {
        return mEngine != null ? mEngine.getPeakLoopTempCelsius() : 20.0;
    }

    @Override
    public boolean hasRotor() {
        return getRotor() != null;
    }

    @Override
    public int getRotorDamage() {
        ItemStack rotor = getRotor();
        if (rotor == null) return 0;
        if (rotor.getItem() instanceof MetaGeneratedTool) {
            return (int) Math.min(Integer.MAX_VALUE, MetaGeneratedTool.getToolDamage(rotor));
        }
        return rotor.getItemDamage();
    }

    @Override
    public int getRotorMaxDamage() {
        ItemStack rotor = getRotor();
        if (rotor == null) return 0;
        if (rotor.getItem() instanceof MetaGeneratedTool) {
            return (int) Math.min(Integer.MAX_VALUE, MetaGeneratedTool.getToolMaxDamage(rotor));
        }
        return rotor.getMaxDamage();
    }

    @Override
    public double getRotorDurabilityPercent() {
        ItemStack rotor = getRotor();
        if (rotor != null) {
            if (rotor.getItem() instanceof MetaGeneratedTool) {
                long max = MetaGeneratedTool.getToolMaxDamage(rotor);
                if (max > 0) {
                    long dmg = MetaGeneratedTool.getToolDamage(rotor);
                    return Math.max(0.0, 100.0 * (1.0 - (double) dmg / max));
                }
            } else if (rotor.getMaxDamage() > 0) {
                return Math.max(0.0, 100.0 * (1.0 - (double) rotor.getItemDamage() / rotor.getMaxDamage()));
            }
        }
        return 0.0;
    }

    @Override
    public long getPowerConsumptionEU() {
        if (mPowerFailed || !isAllowedToWork()) {
            return 0L;
        }
        if (mLoopState == LoopState.CIRCULATING || mLoopState == LoopState.FILLING) {
            return getRequiredPowerDrawEU();
        }
        if (mLoopState == LoopState.DECELERATING && (mEngine == null || mEngine.getVolumetricFlowRate() > 1e-5)) {
            return getRequiredPowerDrawEU();
        }
        return 0L;
    }

    @Override
    public double getPumpMechanicalPowerWatts() {
        return mEngine != null ? mEngine.getPumpMechanicalPowerWatts() : 0.0;
    }

    @Override
    public long getReservoirCapacityLiters() {
        return mReservoir != null ? mReservoir.getTotalTankCapacityLiters() : 0L;
    }

    @Override
    public boolean checkRecipe(ItemStack aStack) {
        return true; // Continuously operates in onRunningTick
    }

    @Override
    protected MTEMultiBlockBaseGui<?> getGui() {
        return new CoolantLoopMachineGui<>(this);
    }

    @Override
    public boolean supportsSingleRecipeLocking() {
        return false;
    }

    @Override
    public boolean supportsVoidProtection() {
        return false;
    }

    @Override
    public boolean supportsInputSeparation() {
        return false;
    }

    @Override
    public boolean supportsBatchMode() {
        return false;
    }

    @Override
    public ButtonWidget createLockToSingleRecipeButton(IWidgetBuilder<?> builder) {
        return (ButtonWidget) new ButtonWidget().setSize(0, 0)
            .setEnabled(false);
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
    public double getDissolvedGasPercentage() {
        return mReservoir.getDissolvedGasPercentage();
    }

    @Override
    public long getTotalDissolvedGasLiters() {
        return mReservoir.getTotalDissolvedGasLiters();
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
        String maxFlowStr = (maxFlowRateLPerSec == Double.MAX_VALUE)
            ? com.gtnewhorizons.coolantloops.common.util.CoolantLocalization
                .get("gt.scanner.coolant_pump.max_pipe_flow.unlimited")
            : String.format("%.1f L/s", maxFlowRateLPerSec);
        String hatchState = isDischargeHatchDisabled()
            ? com.gtnewhorizons.coolantloops.common.util.CoolantLocalization
                .get("gt.scanner.coolant_pump.discharge_hatch.disabled")
            : com.gtnewhorizons.coolantloops.common.util.CoolantLocalization
                .get("gt.scanner.coolant_pump.discharge_hatch.enabled");
        Materials rMat = getRotorMaterial();
        String impellerStr;
        String thermalWearStr;
        if (rMat != null || hasRotor()) {
            String matName = rMat != null ? RotorThermalHelper.getMaterialLocalizedName(rMat) : "Unknown";
            double meltC = RotorThermalHelper.getMeltingPointCelsius(rMat);
            impellerStr = com.gtnewhorizons.coolantloops.common.util.CoolantLocalization
                .format("gt.scanner.coolant_pump.impeller", matName, mRotorEfficiency * 100.0, meltC);
            double th = getHomologousTemperature();
            double thermalWear = getThermalWearMultiplier();
            String softeningActive = th >= 0.5
                ? com.gtnewhorizons.coolantloops.common.util.CoolantLocalization
                    .get("gt.scanner.coolant_pump.impeller_thermal.softening")
                : "";
            thermalWearStr = com.gtnewhorizons.coolantloops.common.util.CoolantLocalization
                .format("gt.scanner.coolant_pump.impeller_thermal_wear", thermalWear, th, softeningActive);
        } else {
            impellerStr = com.gtnewhorizons.coolantloops.common.util.CoolantLocalization
                .get("gt.scanner.coolant_pump.impeller.none");
            thermalWearStr = com.gtnewhorizons.coolantloops.common.util.CoolantLocalization
                .format("gt.scanner.coolant_pump.impeller_thermal_wear", 1.0, 0.0, "");
        }

        return new String[] {
            com.gtnewhorizons.coolantloops.common.util.CoolantLocalization
                .format("gt.scanner.coolant_pump.state", mLoopState.name()),
            com.gtnewhorizons.coolantloops.common.util.CoolantLocalization
                .format("gt.scanner.coolant_pump.discharge_hatch", hatchState),
            com.gtnewhorizons.coolantloops.common.util.CoolantLocalization
                .format("gt.scanner.coolant_pump.tank_material", tankMatName),
            com.gtnewhorizons.coolantloops.common.util.CoolantLocalization
                .format("gt.scanner.coolant_pump.current_flow", mEngine.getFlowRateLitersPerSecond()),
            com.gtnewhorizons.coolantloops.common.util.CoolantLocalization
                .format("gt.scanner.coolant_pump.max_pipe_flow", maxFlowStr),
            com.gtnewhorizons.coolantloops.common.util.CoolantLocalization
                .format("gt.scanner.coolant_pump.peak_pressure", mEngine.getPeakLoopPressureBar()),
            com.gtnewhorizons.coolantloops.common.util.CoolantLocalization
                .format("gt.scanner.coolant_pump.peak_temp", mEngine.getPeakLoopTempCelsius()),
            com.gtnewhorizons.coolantloops.common.util.CoolantLocalization
                .format("gt.scanner.coolant_pump.loop_fluid", mCurrentFillLiters, mRequiredFillLiters),
            com.gtnewhorizons.coolantloops.common.util.CoolantLocalization
                .format("gt.scanner.coolant_pump.dissolved_gas", getDissolvedGasFraction() * 100.0),
            impellerStr, thermalWearStr };
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        mReservoir.saveNBTData(aNBT);
        aNBT.setString("mLoopState", mLoopState.name());
        aNBT.setLong("mCurrentFillLiters", mCurrentFillLiters);
        aNBT.setLong("mRequiredFillLiters", mRequiredFillLiters);
        aNBT.setDouble("mRotorDamageAccumulator", mRotorDamageAccumulator);
        aNBT.setBoolean("mPowerFailed", mPowerFailed);
        if (mTankMaterial != null && mTankMaterial.mName != null) {
            aNBT.setString("mTankMaterial", mTankMaterial.mName);
        }
        if (mRotorMaterial != null && mRotorMaterial.mName != null) {
            aNBT.setString("mRotorMaterial", mRotorMaterial.mName);
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
        if (aNBT.hasKey("mPowerFailed")) {
            mPowerFailed = aNBT.getBoolean("mPowerFailed");
        }
        if (aNBT.hasKey("mTankMaterial")) {
            Materials mat = Materials.get(aNBT.getString("mTankMaterial"));
            if (mat != null) {
                mTankMaterial = mat;
            }
        }
        if (aNBT.hasKey("mRotorMaterial")) {
            Materials mat = Materials.get(aNBT.getString("mRotorMaterial"));
            if (mat != null) {
                mRotorMaterial = mat;
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
            if (ph.isOutput()) {
                if (mDischargeHatch == null) {
                    mDischargeHatch = ph;
                    return true;
                }
                return false;
            } else if (ph.isInput()) {
                if (mSuctionHatch == null) {
                    mSuctionHatch = ph;
                    return true;
                }
                return false;
            }
            return false;
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
    public MultiblockTooltipBuilder createTooltip() {
        MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(StatCollector.translateToLocal("gt.multiblock.coolant_pump.machine_type"))
            .addInfo(StatCollector.translateToLocal("gt.multiblock.coolant_pump.desc1"))
            .addInfo(StatCollector.translateToLocal("gt.multiblock.coolant_pump.desc2"))
            .addInfo(StatCollector.translateToLocal("gt.multiblock.coolant_pump.desc3"))
            .addInfo(StatCollector.translateToLocal("gt.multiblock.coolant_pump.desc4"))
            .addInfo(StatCollector.translateToLocal("gt.multiblock.coolant_pump.desc5"))
            .addInfo(StatCollector.translateToLocal("gt.multiblock.coolant_pump.desc6"))
            .addPerfectOCInfo()
            .addSeparator()
            .beginVariableStructureBlock(3, 9, 6, 10, 3, 9, false)
            .addController(StatCollector.translateToLocal("gt.multiblock.coolant_pump.structure.controller"))
            .addCasingInfo(
                StatCollector.translateToLocal("gt.multiblock.coolant_pump.structure.casings"),
                StatCollector.translateToLocal("gt.multiblock.coolant_pump.structure.casings_pos"),
                false)
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
            .addSubChannel(CoolantStructureChannels.TANK_MATERIAL)
            .toolTipFinisher(StatCollector.translateToLocal("gt.multiblock.coolantloops.finisher"));
        return tt;
    }
}
