package com.gtnewhorizons.coolantloops.common.metatileentity.multi;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureUtility;
import com.gtnewhorizons.coolantloops.common.config.CoolantLoopDataRegistry;
import com.gtnewhorizons.coolantloops.common.config.data.CoolantDeviceData;
import com.gtnewhorizons.coolantloops.common.gui.CoolantLoopMachineGui;
import com.gtnewhorizons.coolantloops.common.metatileentity.hatch.MTEHatchPressurizedFluid;
import com.gtnewhorizons.coolantloops.engine.ConvectiveHeatTransferModel;
import com.gtnewhorizons.coolantloops.engine.CoolantFluidProperty;
import com.gtnewhorizons.coolantloops.engine.ICoolantLoopDevice;
import com.gtnewhorizons.coolantloops.engine.LoopSegment;
import com.gtnewhorizons.modularui.api.widget.IWidgetBuilder;
import com.gtnewhorizons.modularui.common.widget.ButtonWidget;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.Materials;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.api.metatileentity.implementations.MTEHatchEnergy;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.metatileentity.implementations.MTEHatchMaintenance;
import gregtech.api.metatileentity.implementations.MTEHatchOutput;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTStructureUtility;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.api.util.shutdown.ShutDownReasonRegistry;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;

/**
 * Pressurized Heat Exchanger Multiblock.
 * 3x3x5 Dimensions (Width 3, Height 3, Length 5).
 *
 * Transfers thermal energy from primary coolant loop to secondary fluid
 * (e.g. Distilled Water -> Steam at standard 1:160 expansion ratio).
 */
public class MTEPressurizedHeatExchanger extends MTEEnhancedMultiBlockBase<MTEPressurizedHeatExchanger>
    implements ICoolantLoopDevice, ISurvivalConstructable {

    public static final int CASING_INDEX_HEAT_PROOF = 11;

    protected MTEHatchPressurizedFluid mPrimaryInlet = null;
    protected MTEHatchPressurizedFluid mPrimaryOutlet = null;

    protected double mCurrentCoolantTemp = 20.0;
    protected double mExchangeArea = 24.0; // 24 m^2 internal tube bundle area
    protected double mMinorLossK = 1.2; // Heat exchanger core resistance
    protected double mLastHeatTransferredWatts = 0.0;
    protected ConvectiveHeatTransferModel mConvectiveModel = ConvectiveHeatTransferModel.DEFAULT;
    protected LoopSegment mBoundSegment = null;
    protected CoolantFluidProperty mLoopFluid = null;
    protected double mLastCoolantFlowRateM3s = 0.0;
    protected long mLastStepTick = -1;

    public MTEPressurizedHeatExchanger(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
        initFromConfig();
    }

    public MTEPressurizedHeatExchanger(String aName) {
        super(aName);
        initFromConfig();
    }

    private void initFromConfig() {
        if (CoolantLoopDataRegistry.getDeviceData() != null) {
            mExchangeArea = CoolantLoopDataRegistry.getDeviceData()
                .getPhe().exchangeArea;
            mMinorLossK = CoolantLoopDataRegistry.getDeviceData()
                .getPhe().minorLossK;
        }
        if (CoolantLoopDataRegistry.getConvectiveModel() != null) {
            mConvectiveModel = CoolantLoopDataRegistry.getConvectiveModel();
        }
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
            if (aBaseMetaTileEntity != null && !aBaseMetaTileEntity.isAllowedToWork()) {
                aBaseMetaTileEntity.enableWorking();
            }
        }
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEPressurizedHeatExchanger(mName);
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection facing,
        int aColorIndex, boolean aActive, boolean aRedstone) {
        if (side == facing) {
            return new ITexture[] { TextureFactory.of(GregTechAPI.sBlockCasings1, 11),
                TextureFactory.of(
                    aActive ? Textures.BlockIcons.OVERLAY_FRONT_HEAT_EXCHANGER_ACTIVE
                        : Textures.BlockIcons.OVERLAY_FRONT_HEAT_EXCHANGER) };
        }
        return new ITexture[] { TextureFactory.of(GregTechAPI.sBlockCasings1, 11) };
    }

    public MTEHatchPressurizedFluid getPrimaryInlet() {
        return mPrimaryInlet;
    }

    public MTEHatchPressurizedFluid getPrimaryOutlet() {
        return mPrimaryOutlet;
    }

    @Override
    public boolean checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack) {
        mPrimaryInlet = null;
        mPrimaryOutlet = null;
        mInputHatches.clear();
        mOutputHatches.clear();
        mMaintenanceHatches.clear();
        mEnergyHatches.clear();

        java.util.List<gregtech.api.structure.error.StructureError> errors = new java.util.ArrayList<>();
        if (!checkPiece("exchanger_3x5x3", 1, 1, 0, errors)) {
            return false;
        }

        if (mMaintenanceHatches.size() != 1) {
            return false;
        }
        if (mEnergyHatches.size() < 1 || mEnergyHatches.size() > 2) {
            return false;
        }
        if (mPrimaryInlet == null || mPrimaryOutlet == null) {
            return false;
        }
        if (mInputHatches.size() != 1 || mOutputHatches.size() != 1) {
            return false;
        }

        if (mPrimaryInlet != null) {
            mPrimaryInlet.updateTexture(CASING_INDEX_HEAT_PROOF);
            mPrimaryInlet.setMode(MTEHatchPressurizedFluid.HatchMode.SUCTION);
        }
        if (mPrimaryOutlet != null) {
            mPrimaryOutlet.updateTexture(CASING_INDEX_HEAT_PROOF);
            mPrimaryOutlet.setMode(MTEHatchPressurizedFluid.HatchMode.DISCHARGE);
        }
        for (MTEHatchInput ih : mInputHatches) {
            ih.updateTexture(CASING_INDEX_HEAT_PROOF);
        }
        for (MTEHatchOutput oh : mOutputHatches) {
            oh.updateTexture(CASING_INDEX_HEAT_PROOF);
        }
        for (MTEHatchMaintenance mh : mMaintenanceHatches) {
            mh.updateTexture(CASING_INDEX_HEAT_PROOF);
        }
        for (MTEHatchEnergy eh : mEnergyHatches) {
            eh.updateTexture(CASING_INDEX_HEAT_PROOF);
        }

        if (getBaseMetaTileEntity() != null) {
            ItemStack icon = getMachineCraftingIcon();
            if (mPrimaryInlet != null) mPrimaryInlet.updateCraftingIcon(icon);
            if (mPrimaryOutlet != null) mPrimaryOutlet.updateCraftingIcon(icon);
            for (MTEHatchInput ih : mInputHatches) ih.updateCraftingIcon(icon);
            for (MTEHatchOutput oh : mOutputHatches) oh.updateCraftingIcon(icon);
            for (MTEHatchMaintenance mh : mMaintenanceHatches) mh.updateCraftingIcon(icon);
            for (MTEHatchEnergy eh : mEnergyHatches) eh.updateCraftingIcon(icon);
        }

        checkMaintenance();
        if (aBaseMetaTileEntity != null && !aBaseMetaTileEntity.isAllowedToWork()) {
            aBaseMetaTileEntity.enableWorking();
        }

        return true;
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);
        if (aBaseMetaTileEntity != null && aBaseMetaTileEntity.isServerSide()) {
            stepHeatExchange();
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
        stopMachine(ShutDownReasonRegistry.POWER_LOSS);
        if (getBaseMetaTileEntity() != null) {
            getBaseMetaTileEntity().setActive(false);
        }
        mLastHeatTransferredWatts = 0.0;
        return false;
    }

    public boolean drainEnergy(long aEU) {
        if (aEU <= 0) return true;

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
                            return true;
                        }
                    }
                }
            }
            return remaining <= 0;
        }

        if (getBaseMetaTileEntity() != null && getBaseMetaTileEntity().getStoredEU() >= aEU) {
            return getBaseMetaTileEntity().decreaseStoredEnergyUnits(aEU, false);
        }
        return false;
    }

    public boolean stepHeatExchange() {
        IGregTechTileEntity te = getBaseMetaTileEntity();
        long currentTick = (te != null) ? te.getTimer() : -1;
        if (te != null && te.getWorld() != null && currentTick >= 0) {
            if (mLastStepTick == currentTick) {
                return true;
            }
            mLastStepTick = currentTick;
        }

        checkMaintenance();
        if (!isAllowedToWork() || mPrimaryInlet == null || mPrimaryOutlet == null) {
            mLastHeatTransferredWatts = 0.0;
            if (getBaseMetaTileEntity() != null) {
                getBaseMetaTileEntity().setActive(false);
            }
            return true;
        }

        double effFactor = getEfficiencyFactor();
        if (effFactor <= 0.0) {
            mLastHeatTransferredWatts = 0.0;
            return true;
        }

        long requiredEU = getRequiredPowerDrawEU();
        if (!drainEnergy(requiredEU)) {
            return triggerPowerfail();
        }
        if (getBaseMetaTileEntity() != null) {
            getBaseMetaTileEntity().setActive(true);
        }

        // Check if allowed secondary boiling fluid (e.g. distilled water, liquid nitrogen, liquid helium) is available
        ArrayList<FluidStack> inputFluids = getStoredFluids();
        FluidStack secondaryInput = null;
        CoolantDeviceData.PHEData.SecondaryCoolantEntry matchedCoolant = null;
        CoolantDeviceData.PHEData pheConfig = (CoolantLoopDataRegistry.getDeviceData() != null)
            ? CoolantLoopDataRegistry.getDeviceData()
                .getPhe()
            : new CoolantDeviceData.PHEData();
        for (FluidStack fs : inputFluids) {
            if (fs != null && fs.getFluid() != null && fs.amount > 0) {
                String fluidName = fs.getFluid()
                    .getName();
                CoolantDeviceData.PHEData.SecondaryCoolantEntry entry = pheConfig.findSecondaryCoolant(fluidName);
                if (entry != null) {
                    secondaryInput = fs;
                    matchedCoolant = entry;
                    break;
                }
            }
        }

        if (secondaryInput == null || matchedCoolant == null
            || mCurrentCoolantTemp <= matchedCoolant.boilingPointCelsius) {
            mLastHeatTransferredWatts = 0.0;
            return true;
        }

        // Secondary boiling calculation if primary coolant > secondary boiling point
        double boilingTemp = matchedCoolant.boilingPointCelsius;
        // Velocity across internal tube bundle
        double velocity = 2.0;
        if (mBoundSegment != null && mBoundSegment.getArea() > 0) {
            velocity = Math.abs(mLastCoolantFlowRateM3s) / mBoundSegment.getArea();
        }

        // Q_dot = h(v) * A * deltaT * effFactor (Watts)
        double qWatts = mConvectiveModel
            .computeHeatTransferRate(velocity, mExchangeArea, mCurrentCoolantTemp, boilingTemp) * effFactor;

        // Effective fluid mass and heat capacity passing through the exchanger this tick
        double dt = 0.05; // 1 tick = 0.05 seconds
        double sweptVolume = Math.abs(mLastCoolantFlowRateM3s) * dt;
        double segVolume = (mBoundSegment != null) ? mBoundSegment.getVolume() : 0.007854;
        double effectiveVolume = Math.max(segVolume, sweptVolume);

        double fluidDensity = (mLoopFluid != null) ? mLoopFluid.getDensity() : 1000.0;
        double fluidSpecificHeat = (mLoopFluid != null) ? mLoopFluid.getSpecificHeat() : 4184.0;
        double fluidHeatCapacity = effectiveVolume * fluidDensity * fluidSpecificHeat; // Joules / K

        // Maximum sensible heat removable before reaching secondary boiling temperature
        double maxSensibleHeatJoules = (fluidHeatCapacity > 0)
            ? fluidHeatCapacity * Math.max(0.0, mCurrentCoolantTemp - boilingTemp)
            : 0.0;

        // Convective heat transfer limit in this tick
        double convHeatJoules = qWatts * dt;

        // Available heat this tick
        double transferHeatLimitJoules = Math.min(convHeatJoules, maxSensibleHeatJoules);

        // Latent heat of vaporization for secondary coolant (Joules / mL)
        double latentHeatJoulePerMl = matchedCoolant.latentHeatJoulePerLiter / 1000.0;

        int maxFluidToBoil = (latentHeatJoulePerMl > 0) ? (int) (transferHeatLimitJoules / latentHeatJoulePerMl) : 0;

        int fluidToBoil = Math.min(secondaryInput.amount, maxFluidToBoil);

        if (fluidToBoil > 0) {
            // Drain allowed secondary coolant
            int actualFluidDrained = 0;
            for (MTEHatchInput hatch : mInputHatches) {
                if (hatch != null && hatch.getFluid() != null
                    && hatch.getFluid()
                        .getFluid() == secondaryInput.getFluid()) {
                    int needed = fluidToBoil - actualFluidDrained;
                    FluidStack drained = hatch.drain(needed, true);
                    if (drained != null && drained.amount > 0) {
                        actualFluidDrained += drained.amount;
                        if (actualFluidDrained >= fluidToBoil) {
                            break;
                        }
                    }
                }
            }
            fluidToBoil = actualFluidDrained;
        }

        if (fluidToBoil > 0) {
            int expansion = matchedCoolant.expansionRatio > 0 ? matchedCoolant.expansionRatio : 160;
            int gasProduced = fluidToBoil * expansion;

            if (matchedCoolant.isSteam) {
                double supercritThresh = (CoolantLoopDataRegistry.getDeviceData() != null)
                    ? CoolantLoopDataRegistry.getDeviceData()
                        .getPhe().supercriticalTempThreshold
                    : 700.0;
                double superheatedThresh = (CoolantLoopDataRegistry.getDeviceData() != null)
                    ? CoolantLoopDataRegistry.getDeviceData()
                        .getPhe().superheatedTempThreshold
                    : 300.0;

                // 1 L Water -> steam depending on coolant temperature
                String steamName = "steam";
                if (mCurrentCoolantTemp >= supercritThresh) {
                    steamName = "supercriticalsteam";
                } else if (mCurrentCoolantTemp >= superheatedThresh) {
                    steamName = "ic2superheatedsteam";
                }
                Fluid steamFluid = FluidRegistry.getFluid(steamName);
                if (steamFluid == null && steamName.equals("supercriticalsteam")) {
                    steamFluid = FluidRegistry.getFluid("supercriticalSteam");
                }
                if (steamFluid == null) {
                    steamFluid = FluidRegistry.getFluid("steam");
                }
                if (steamFluid != null) {
                    FluidStack steamStack = new FluidStack(steamFluid, gasProduced);
                    addOutput(steamStack);
                }
            } else {
                // Non-steam cryogenic gas output (e.g. nitrogen, oxygen, air, helium)
                String gasName = matchedCoolant.outputGasName;
                Fluid gasFluid = FluidRegistry.getFluid(gasName);
                if (gasFluid == null) {
                    gasFluid = FluidRegistry.getFluid(gasName.toLowerCase());
                }
                if (gasFluid == null) {
                    gasFluid = FluidRegistry.getFluid(gasName.toUpperCase());
                }
                if (gasFluid != null) {
                    FluidStack gasStack = new FluidStack(gasFluid, gasProduced);
                    addOutput(gasStack);
                }
            }

            // Energy-conserving cooling of primary coolant
            double actualHeatRemovedJoules = fluidToBoil * latentHeatJoulePerMl;
            mLastHeatTransferredWatts = actualHeatRemovedJoules / dt;

            if (fluidHeatCapacity > 0) {
                double tempDrop = actualHeatRemovedJoules / fluidHeatCapacity;
                double newTemp = Math.max(boilingTemp, mCurrentCoolantTemp - tempDrop);
                mCurrentCoolantTemp = newTemp;
                if (mBoundSegment != null) {
                    mBoundSegment.setCurrentTemperatureCelsius(newTemp);
                }
                if (mPrimaryOutlet != null) {
                    mPrimaryOutlet.setCurrentTemperatureCelsius(newTemp);
                }
            }
        } else {
            mLastHeatTransferredWatts = 0.0;
        }

        return true;
    }

    @Override
    public boolean onRunningTick(ItemStack aStack) {
        return stepHeatExchange();
    }

    // --- ICoolantLoopDevice Implementation ---

    @Override
    public String getDeviceId() {
        IGregTechTileEntity te = getBaseMetaTileEntity();
        if (te != null) {
            return String.format("phe_%d_%d_%d", te.getXCoord(), te.getYCoord(), te.getZCoord());
        }
        return "phe_unbound";
    }

    @Override
    public double getMinorLossK() {
        return mMinorLossK;
    }

    @Override
    public double getDeviceTemperatureCelsius() {
        return mCurrentCoolantTemp;
    }

    @Override
    public void processThermalExchange(double coolantFlowRateM3s, double dt, CoolantFluidProperty fluid,
        LoopSegment segment) {
        this.mBoundSegment = segment;
        this.mLoopFluid = fluid;
        this.mLastCoolantFlowRateM3s = coolantFlowRateM3s;
        if (segment != null) {
            this.mCurrentCoolantTemp = segment.getCurrentTemperatureCelsius();
        }
        stepHeatExchange();
    }

    public LoopSegment getBoundSegment() {
        return mBoundSegment;
    }

    public void setBoundSegment(LoopSegment segment) {
        this.mBoundSegment = segment;
        if (segment != null) {
            this.mCurrentCoolantTemp = segment.getCurrentTemperatureCelsius();
        }
    }

    public CoolantFluidProperty getLoopFluid() {
        return mLoopFluid;
    }

    public void setLoopFluid(CoolantFluidProperty fluid) {
        this.mLoopFluid = fluid;
    }

    public double getLastCoolantFlowRateM3s() {
        return mLastCoolantFlowRateM3s;
    }

    public void setLastCoolantFlowRateM3s(double flowRate) {
        this.mLastCoolantFlowRateM3s = flowRate;
    }

    public double getLastHeatTransferredWatts() {
        return mLastHeatTransferredWatts;
    }

    public void setCurrentCoolantTemp(double temp) {
        this.mCurrentCoolantTemp = temp;
        if (mBoundSegment != null) {
            mBoundSegment.setCurrentTemperatureCelsius(temp);
        }
    }

    @Override
    public boolean checkRecipe(ItemStack aStack) {
        return true;
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
    public ButtonWidget createLockToSingleRecipeButton(IWidgetBuilder<?> builder) {
        return (ButtonWidget) new ButtonWidget().setSize(0, 0)
            .setEnabled(false);
    }

    public boolean addMaintenanceOrEnergyHatch(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        if (aTileEntity == null) return false;
        IMetaTileEntity aMetaTileEntity = aTileEntity.getMetaTileEntity();
        if (aMetaTileEntity == null) return false;
        if (aMetaTileEntity instanceof MTEHatchMaintenance) {
            MTEHatchMaintenance mh = (MTEHatchMaintenance) aMetaTileEntity;
            mh.updateTexture(aBaseCasingIndex);
            if (getBaseMetaTileEntity() != null) {
                mh.updateCraftingIcon(getMachineCraftingIcon());
            }
            return mMaintenanceHatches.add(mh);
        }
        if (aMetaTileEntity instanceof MTEHatchEnergy) {
            MTEHatchEnergy eh = (MTEHatchEnergy) aMetaTileEntity;
            eh.updateTexture(aBaseCasingIndex);
            if (getBaseMetaTileEntity() != null) {
                eh.updateCraftingIcon(getMachineCraftingIcon());
            }
            return mEnergyHatches.add(eh);
        }
        return false;
    }

    public boolean addPressurizedHatch(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        if (aTileEntity == null) return false;
        IMetaTileEntity aMetaTileEntity = aTileEntity.getMetaTileEntity();
        if (aMetaTileEntity == null) return false;
        if (aMetaTileEntity instanceof MTEHatchPressurizedFluid) {
            MTEHatchPressurizedFluid ph = (MTEHatchPressurizedFluid) aMetaTileEntity;
            ph.updateTexture(aBaseCasingIndex);
            if (getBaseMetaTileEntity() != null) {
                ph.updateCraftingIcon(getMachineCraftingIcon());
            }
            if (mPrimaryInlet == null) {
                mPrimaryInlet = ph;
                ph.setMode(MTEHatchPressurizedFluid.HatchMode.SUCTION);
                return true;
            } else if (mPrimaryOutlet == null) {
                mPrimaryOutlet = ph;
                ph.setMode(MTEHatchPressurizedFluid.HatchMode.DISCHARGE);
                return true;
            }
            return true;
        }
        return false;
    }

    public boolean addSecondaryHatch(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        if (aTileEntity == null) return false;
        IMetaTileEntity aMetaTileEntity = aTileEntity.getMetaTileEntity();
        if (aMetaTileEntity == null) return false;
        if (aMetaTileEntity instanceof MTEHatchInput) {
            MTEHatchInput ih = (MTEHatchInput) aMetaTileEntity;
            ih.updateTexture(aBaseCasingIndex);
            if (getBaseMetaTileEntity() != null) {
                ih.updateCraftingIcon(getMachineCraftingIcon());
            }
            return mInputHatches.add(ih);
        }
        if (aMetaTileEntity instanceof MTEHatchOutput) {
            MTEHatchOutput oh = (MTEHatchOutput) aMetaTileEntity;
            oh.updateTexture(aBaseCasingIndex);
            if (getBaseMetaTileEntity() != null) {
                oh.updateCraftingIcon(getMachineCraftingIcon());
            }
            return mOutputHatches.add(oh);
        }
        return false;
    }

    public boolean addBottomHatch(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        return addPressurizedHatch(aTileEntity, aBaseCasingIndex);
    }

    private static IStructureDefinition<MTEPressurizedHeatExchanger> STRUCTURE_DEFINITION = null;

    @Override
    public IStructureDefinition<MTEPressurizedHeatExchanger> getStructureDefinition() {
        if (STRUCTURE_DEFINITION == null) {
            STRUCTURE_DEFINITION = StructureDefinition.<MTEPressurizedHeatExchanger>builder()
                .addShape(
                    "exchanger_3x5x3",
                    StructureUtility.transpose(
                        new String[][] { { "xxx", "lxh", "lxh", "lxh", "xxx" }, { "L~H", "lch", "lch", "lch", "LxH" },
                            { "xxx", "lxh", "lxh", "lxh", "xxx" } }))
                .addElement(
                    'x',
                    StructureUtility.ofChain(
                        GTStructureUtility.ofHatchAdder(
                            MTEPressurizedHeatExchanger::addMaintenanceOrEnergyHatch,
                            CASING_INDEX_HEAT_PROOF,
                            1),
                        StructureUtility.ofBlock(GregTechAPI.sBlockCasings1, 11)))
                .addElement('h', StructureUtility.ofBlock(GregTechAPI.sBlockCasings10, 10))
                .addElement('l', StructureUtility.ofBlock(GregTechAPI.sBlockCasings10, 9))
                .addElement('c', GTStructureUtility.ofSheetMetal(Materials.Copper))
                .addElement(
                    'H',
                    GTStructureUtility
                        .ofHatchAdder(MTEPressurizedHeatExchanger::addPressurizedHatch, CASING_INDEX_HEAT_PROOF, 2))
                .addElement(
                    'L',
                    GTStructureUtility
                        .ofHatchAdder(MTEPressurizedHeatExchanger::addSecondaryHatch, CASING_INDEX_HEAT_PROOF, 3))
                .build();
        }
        return STRUCTURE_DEFINITION;
    }

    @Override
    public void construct(ItemStack stackSize, boolean hintsOnly) {
        buildPiece("exchanger_3x5x3", stackSize, hintsOnly, 1, 1, 0);
    }

    @Override
    public int survivalConstruct(ItemStack stackSize, int elementBudget, ISurvivalBuildEnvironment env) {
        if (mMachine) return -1;
        return survivalBuildPiece("exchanger_3x5x3", stackSize, 1, 1, 0, elementBudget, env, false, true);
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(StatCollector.translateToLocal("gt.multiblock.pressurized_heat_exchanger.machine_type"))
            .addInfo(StatCollector.translateToLocal("gt.multiblock.pressurized_heat_exchanger.desc1"))
            .addInfo(StatCollector.translateToLocal("gt.multiblock.pressurized_heat_exchanger.desc2"))
            .addSeparator()
            .beginStructureBlock(3, 3, 5, false)
            .addController(
                StatCollector.translateToLocal("gt.multiblock.pressurized_heat_exchanger.structure.controller"))
            .addCasingInfoMin(
                StatCollector.translateToLocal("gt.multiblock.pressurized_heat_exchanger.structure.casings"),
                15,
                false)
            .addOtherStructurePart(
                StatCollector.translateToLocal("gt.multiblock.pressurized_heat_exchanger.structure.cooling_duct"),
                StatCollector.translateToLocal("gt.multiblock.pressurized_heat_exchanger.structure.cooling_duct_pos"))
            .addOtherStructurePart(
                StatCollector.translateToLocal("gt.multiblock.pressurized_heat_exchanger.structure.heating_duct"),
                StatCollector.translateToLocal("gt.multiblock.pressurized_heat_exchanger.structure.heating_duct_pos"))
            .addOtherStructurePart(
                StatCollector.translateToLocal("gt.multiblock.pressurized_heat_exchanger.structure.copper_sheetmetal"),
                StatCollector
                    .translateToLocal("gt.multiblock.pressurized_heat_exchanger.structure.copper_sheetmetal_pos"))
            .addOtherStructurePart(
                StatCollector.translateToLocal("gt.multiblock.pressurized_heat_exchanger.structure.hatch_inlet"),
                StatCollector.translateToLocal("gt.multiblock.pressurized_heat_exchanger.structure.hatch_inlet_pos"))
            .addOtherStructurePart(
                StatCollector.translateToLocal("gt.multiblock.pressurized_heat_exchanger.structure.hatch_outlet"),
                StatCollector.translateToLocal("gt.multiblock.pressurized_heat_exchanger.structure.hatch_outlet_pos"))
            .addInputHatch(
                StatCollector.translateToLocal("gt.multiblock.pressurized_heat_exchanger.structure.input_hatch"),
                1)
            .addOutputHatch(
                StatCollector.translateToLocal("gt.multiblock.pressurized_heat_exchanger.structure.output_hatch"),
                1)
            .addMaintenanceHatch(
                StatCollector.translateToLocal("gt.multiblock.pressurized_heat_exchanger.structure.maintenance"),
                1)
            .addEnergyHatch(
                StatCollector.translateToLocal("gt.multiblock.pressurized_heat_exchanger.structure.energy"),
                1)
            .toolTipFinisher(StatCollector.translateToLocal("gt.multiblock.coolantloops.finisher"));
        return tt;
    }
}
