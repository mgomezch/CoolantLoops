package com.gtnewhorizons.coolantloops.common.metatileentity.multi;

import java.util.ArrayList;

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
import com.gtnewhorizons.coolantloops.common.metatileentity.hatch.MTEHatchPressurizedFluid;
import com.gtnewhorizons.coolantloops.engine.ConvectiveHeatTransferModel;
import com.gtnewhorizons.coolantloops.engine.CoolantFluidProperty;
import com.gtnewhorizons.coolantloops.engine.ICoolantLoopDevice;
import com.gtnewhorizons.coolantloops.engine.LoopSegment;

import gregtech.api.GregTechAPI;
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

    public MTEPressurizedHeatExchanger(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public MTEPressurizedHeatExchanger(String aName) {
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

    public boolean stepHeatExchange() {
        checkMaintenance();
        if (!isAllowedToWork() || mPrimaryInlet == null || mPrimaryOutlet == null) {
            mLastHeatTransferredWatts = 0.0;
            return true;
        }

        double effFactor = getEfficiencyFactor();
        if (effFactor <= 0.0) {
            mLastHeatTransferredWatts = 0.0;
            return true;
        }

        // Check if secondary water is available
        ArrayList<FluidStack> inputFluids = getStoredFluids();
        FluidStack waterInput = null;
        for (FluidStack fs : inputFluids) {
            if (fs != null && (fs.getFluid() == FluidRegistry.WATER || fs.getFluid()
                .getName()
                .toLowerCase()
                .contains("water"))) {
                waterInput = fs;
                break;
            }
        }

        if (waterInput == null || waterInput.amount <= 0) {
            mLastHeatTransferredWatts = 0.0;
            return true;
        }

        // Secondary boiling calculation if primary coolant >= 100 C
        double ambientTemp = 20.0;
        if (mCurrentCoolantTemp > 100.0) {
            // Delta T available above boiling point
            double deltaT = mCurrentCoolantTemp - 100.0;
            // Q_dot = h(v) * A * deltaT * effFactor
            double qWatts = mConvectiveModel.computeHeatTransferRate(2.0, mExchangeArea, mCurrentCoolantTemp, 100.0)
                * effFactor;
            mLastHeatTransferredWatts = qWatts;

            // 1 Liter of water requires ~2.26 MJ of latent heat to vaporize
            // Q_Joules_per_tick = qWatts * 0.05s
            double joulesPerTick = qWatts * 0.05;
            // 2,260,000 J / L -> 2260 J / mL
            int waterToBoil = (int) Math.min(waterInput.amount, joulesPerTick / 2260.0);

            if (waterToBoil > 0) {
                // Drain water
                for (MTEHatchInput hatch : mInputHatches) {
                    FluidStack drained = hatch.drain(waterToBoil, true);
                    if (drained != null && drained.amount > 0) {
                        waterToBoil = drained.amount;
                        break;
                    }
                }

                // 1 L Water -> 160 L Steam (Regular, Superheated, or Supercritical depending on coolant temperature)
                String steamName = "steam";
                if (mCurrentCoolantTemp >= 700.0) {
                    steamName = "supercriticalsteam";
                } else if (mCurrentCoolantTemp >= 300.0) {
                    steamName = "ic2superheatedsteam";
                }
                int steamProduced = waterToBoil * 160;
                Fluid steamFluid = FluidRegistry.getFluid(steamName);
                if (steamFluid == null && steamName.equals("supercriticalsteam")) {
                    steamFluid = FluidRegistry.getFluid("supercriticalSteam");
                }
                if (steamFluid == null) {
                    steamFluid = FluidRegistry.getFluid("steam");
                }
                if (steamFluid != null) {
                    FluidStack steamStack = new FluidStack(steamFluid, steamProduced);
                    addOutput(steamStack);
                    for (MTEHatchOutput outHatch : mOutputHatches) {
                        outHatch.fill(steamStack, true);
                    }
                }

                // Cool primary coolant
                double tempDrop = Math.min(deltaT * 0.5, 40.0);
                mCurrentCoolantTemp = Math.max(ambientTemp, mCurrentCoolantTemp - tempDrop);
                mPrimaryOutlet.setCurrentTemperatureCelsius(mCurrentCoolantTemp);
            }
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
        if (segment != null) {
            this.mCurrentCoolantTemp = segment.getCurrentTemperatureCelsius();
        }
    }

    @Override
    public boolean checkRecipe(ItemStack aStack) {
        return true;
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
