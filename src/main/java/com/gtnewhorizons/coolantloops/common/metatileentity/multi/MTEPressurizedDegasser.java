package com.gtnewhorizons.coolantloops.common.metatileentity.multi;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
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
import com.gtnewhorizons.coolantloops.engine.CoolantFluidProperty;
import com.gtnewhorizons.coolantloops.engine.ICoolantLoopDevice;
import com.gtnewhorizons.coolantloops.engine.ICoolantLoopPump;
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
import gregtech.api.metatileentity.implementations.MTEHatchMaintenance;
import gregtech.api.metatileentity.implementations.MTEHatchOutput;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTStructureUtility;
import gregtech.api.util.MultiblockTooltipBuilder;

/**
 * Pressurized Degasser Multiblock.
 * 3x3 Footprint, Height 7.
 *
 * Extracts dissolved radiolytic and byproduct gases (e.g. Deuterium, Tritium, Oxygen, Hydrogen)
 * from circulating closed coolant loops into top-layer output hatches.
 */
public class MTEPressurizedDegasser extends MTEEnhancedMultiBlockBase<MTEPressurizedDegasser>
    implements ICoolantLoopDevice, ISurvivalConstructable {

    protected MTEHatchPressurizedFluid mPrimaryInlet = null;
    protected MTEHatchPressurizedFluid mPrimaryOutlet = null;
    protected ICoolantLoopPump mConnectedPump = null;

    protected double mCurrentCoolantTemp = 20.0;
    protected double mMinorLossK = 1.5; // Internal column pack minor loss coefficient
    protected long mLastDrainedEU = 0L;
    protected long mTotalExtractedLiters = 0L;
    protected final Map<String, Double> mExtractionAccumulators = new HashMap<>();

    public MTEPressurizedDegasser(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public MTEPressurizedDegasser(String aName) {
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
        return new MTEPressurizedDegasser(mName);
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection facing,
        int aColorIndex, boolean aActive, boolean aRedstone) {
        if (side == facing) {
            return new ITexture[] { TextureFactory.of(GregTechAPI.sBlockCasings4, 2),
                TextureFactory.of(
                    aActive ? Textures.BlockIcons.OVERLAY_FRONT_DISTILLATION_TOWER_ACTIVE
                        : Textures.BlockIcons.OVERLAY_FRONT_DISTILLATION_TOWER) };
        }
        return new ITexture[] { TextureFactory.of(GregTechAPI.sBlockCasings4, 2) };
    }

    public MTEHatchPressurizedFluid getPrimaryInlet() {
        return mPrimaryInlet;
    }

    public MTEHatchPressurizedFluid getPrimaryOutlet() {
        return mPrimaryOutlet;
    }

    public void setConnectedPump(ICoolantLoopPump pump) {
        this.mConnectedPump = pump;
    }

    public ICoolantLoopPump getConnectedPump() {
        return mConnectedPump;
    }

    public long getLastDrainedEU() {
        return mLastDrainedEU;
    }

    public long getTotalExtractedLiters() {
        return mTotalExtractedLiters;
    }

    @Override
    public boolean checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack) {
        mPrimaryInlet = null;
        mPrimaryOutlet = null;
        mEnergyHatches.clear();
        mOutputHatches.clear();
        mMaintenanceHatches.clear();

        if (!checkPiece("degasser_3x3x7", 1, 0, 1)) {
            return false;
        }

        if (mPrimaryInlet == null || mPrimaryOutlet == null) {
            return false;
        }

        if (mEnergyHatches.isEmpty() || mOutputHatches.isEmpty() || mMaintenanceHatches.size() != 1) {
            return false;
        }

        checkMaintenance();
        if (aBaseMetaTileEntity != null && !aBaseMetaTileEntity.isAllowedToWork()) {
            aBaseMetaTileEntity.enableWorking();
        }

        return true;
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
            if (mPrimaryInlet == null && ph.getMode() == MTEHatchPressurizedFluid.HatchMode.SUCTION) {
                mPrimaryInlet = ph;
                return true;
            } else if (mPrimaryOutlet == null && ph.getMode() == MTEHatchPressurizedFluid.HatchMode.DISCHARGE) {
                mPrimaryOutlet = ph;
                return true;
            } else if (mPrimaryInlet == null) {
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
        if (aMetaTileEntity instanceof MTEHatchEnergy) {
            if (aMetaTileEntity.getClass()
                .getName()
                .contains("Multi")) {
                return false;
            }
            return addEnergyInputToMachineList(aTileEntity, aBaseCasingIndex);
        }
        if (aMetaTileEntity instanceof MTEHatchMaintenance) {
            return addMaintenanceToMachineList(aTileEntity, aBaseCasingIndex);
        }
        return false;
    }

    public boolean addTopHatch(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        if (aTileEntity == null) return false;
        IMetaTileEntity aMetaTileEntity = aTileEntity.getMetaTileEntity();
        if (aMetaTileEntity == null) return false;
        if (aMetaTileEntity instanceof MTEHatchOutput) {
            return addOutputToMachineList(aTileEntity, aBaseCasingIndex);
        }
        return false;
    }

    private static IStructureDefinition<MTEPressurizedDegasser> STRUCTURE_DEFINITION = null;

    @Override
    public IStructureDefinition<MTEPressurizedDegasser> getStructureDefinition() {
        if (STRUCTURE_DEFINITION == null) {
            STRUCTURE_DEFINITION = StructureDefinition.<MTEPressurizedDegasser>builder()
                .addShape(
                    "degasser_3x3x7",
                    StructureUtility.transpose(
                        new String[][] { { "CCC", "C~C", "CCC" }, { "CCC", "C C", "CCC" }, { "CCC", "C C", "CCC" },
                            { "CCC", "C C", "CCC" }, { "CCC", "C C", "CCC" }, { "CCC", "C C", "CCC" },
                            { "TTT", "TTT", "TTT" } }))
                .addElement(
                    'C',
                    StructureUtility.ofChain(
                        GTStructureUtility.ofHatchAdder(MTEPressurizedDegasser::addBottomHatch, 48 + 2, 1),
                        StructureUtility.ofBlock(GregTechAPI.sBlockCasings4, 2)))
                .addElement(
                    'T',
                    StructureUtility.ofChain(
                        GTStructureUtility.ofHatchAdder(MTEPressurizedDegasser::addTopHatch, 48 + 2, 2),
                        StructureUtility.ofBlock(GregTechAPI.sBlockCasings4, 2)))
                .build();
        }
        return STRUCTURE_DEFINITION;
    }

    @Override
    public void construct(ItemStack stackSize, boolean hintsOnly) {
        buildPiece("degasser_3x3x7", stackSize, hintsOnly, 1, 0, 1);
    }

    @Override
    public int survivalConstruct(ItemStack stackSize, int elementBudget, ISurvivalBuildEnvironment env) {
        if (mMachine) return -1;
        return survivalBuildPiece("degasser_3x3x7", stackSize, 1, 0, 1, elementBudget, env, false, true);
    }

    @Override
    public String getDeviceId() {
        IGregTechTileEntity te = getBaseMetaTileEntity();
        if (te != null) {
            return String.format("degasser_%d_%d_%d", te.getXCoord(), te.getYCoord(), te.getZCoord());
        }
        return "degasser_unbound";
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
        stepDegassing(coolantFlowRateM3s, dt);
    }

    @Override
    public boolean checkRecipe(ItemStack aStack) {
        return true;
    }

    /**
     * Executes one time step of degassing based on power consumption, flow rate, and dissolved gas concentration.
     */
    public boolean stepDegassing(double coolantFlowRateM3s, double dt) {
        checkMaintenance();
        // If turned off, allows flow through without consuming energy or degassing
        if (!isAllowedToWork() || mPrimaryInlet == null || mPrimaryOutlet == null || mConnectedPump == null) {
            mLastDrainedEU = 0L;
            return true;
        }

        double effFactor = getEfficiencyFactor();
        if (effFactor <= 0.0) {
            return true; // No degassing possible with 0 efficiency
        }

        // 1. Calculate target energy based on single-amp overclocking mechanics
        int numEnergyHatches = mEnergyHatches.size();
        if (numEnergyHatches == 0) {
            mLastDrainedEU = 0L;
            return true;
        }

        long targetEU = 0L;
        if (numEnergyHatches == 1) {
            // Single hatch consumes 1 amp
            targetEU = mEnergyHatches.get(0)
                .maxEUInput();
        } else {
            // Multiple hatches consume 2 amps per hatch (usual GT multiblock overclock logic)
            for (MTEHatchEnergy h : mEnergyHatches) {
                if (h != null && h.isValid()) {
                    targetEU += h.maxEUInput() * 2L;
                }
            }
        }

        if (targetEU <= 0L) {
            mLastDrainedEU = 0L;
            return true;
        }

        // 2. Consume energy from hatches
        long availableEU = 0L;
        for (MTEHatchEnergy hatch : mEnergyHatches) {
            if (hatch != null && hatch.isValid()) {
                IGregTechTileEntity bte = hatch.getBaseMetaTileEntity();
                if (bte != null) {
                    availableEU += bte.getStoredEU();
                }
            }
        }

        long toDrain = Math.min(targetEU, availableEU);
        long drainedEU = 0L;
        if (toDrain > 0L) {
            long remaining = toDrain;
            for (MTEHatchEnergy hatch : mEnergyHatches) {
                if (hatch != null && hatch.isValid()) {
                    IGregTechTileEntity bte = hatch.getBaseMetaTileEntity();
                    if (bte != null) {
                        long canDrain = Math.min(bte.getStoredEU(), remaining);
                        if (canDrain > 0L) {
                            bte.decreaseStoredEnergyUnits(canDrain, false);
                            drainedEU += canDrain;
                            remaining -= canDrain;
                            if (remaining <= 0L) break;
                        }
                    }
                }
            }
        }

        mLastDrainedEU = drainedEU;
        if (drainedEU <= 0L) {
            return true; // No energy available to degas
        }

        // 3. Degassing rate limits:
        // A. Energy limit: up to 1 liter of extracted gas per second for every 8 EU/t consumed
        double maxGasEnergyPerSec = (double) drainedEU / 8.0;
        double maxGasEnergyThisTick = maxGasEnergyPerSec * dt;

        // B. Mass transfer limit: 25% of dissolved gas volume that conceptually crosses the degasser this second
        double flowRateLs = coolantFlowRateM3s * 1000.0;
        if (flowRateLs <= 0.0) {
            return true; // No fluid crossing the degasser
        }

        long loopVolume = mConnectedPump.getCurrentFillLiters();
        if (loopVolume <= 0L) {
            loopVolume = 1000L;
        }
        double qTick = flowRateLs * dt; // Liters of coolant crossing in dt

        Map<String, Long> dissolvedGases = mConnectedPump.getDissolvedGases();
        if (dissolvedGases == null || dissolvedGases.isEmpty()) {
            return true;
        }

        // Process each dissolved gas species
        for (Map.Entry<String, Long> entry : dissolvedGases.entrySet()) {
            String gasName = entry.getKey();
            long gasLiters = entry.getValue();
            if (gasLiters <= 0L) continue;

            double cGas = (double) gasLiters / (double) loopVolume;
            double vCrossing = cGas * qTick;
            double maxGasMassThisTick = 0.25 * vCrossing;

            double toExtract = Math.min(maxGasEnergyThisTick, maxGasMassThisTick) * effFactor;
            if (toExtract <= 0.0) continue;

            Fluid fluid = resolveGasFluid(gasName);
            if (fluid == null) continue;

            // Check if at least one output hatch can accept this gas fluid
            if (!canOutputHatchAccept(fluid)) {
                continue; // Hatches full or locked to another fluid
            }

            // Accumulate fractional liters
            double currentAccum = mExtractionAccumulators.getOrDefault(gasName, 0.0) + toExtract;
            if (currentAccum >= 1.0) {
                int wholeLiters = (int) Math.floor(currentAccum);
                long extracted = mConnectedPump.extractDissolvedGas(gasName, wholeLiters);
                if (extracted > 0L) {
                    mExtractionAccumulators.put(gasName, currentAccum - (double) extracted);
                    mTotalExtractedLiters += extracted;
                    dumpFluidToOutputHatches(new FluidStack(fluid, (int) extracted));
                } else {
                    mExtractionAccumulators.put(gasName, currentAccum);
                }
            } else {
                mExtractionAccumulators.put(gasName, currentAccum);
            }
        }

        return true;
    }

    protected boolean canOutputHatchAccept(Fluid fluid) {
        if (fluid == null || mOutputHatches.isEmpty()) return false;
        FluidStack testStack = new FluidStack(fluid, 1);
        for (MTEHatchOutput hatch : mOutputHatches) {
            if (hatch == null || !hatch.isValid()) continue;
            if (hatch.canStoreFluid(testStack) && hatch.fill(testStack, false) > 0) {
                return true;
            }
        }
        return false;
    }

    protected int dumpFluidToOutputHatches(FluidStack stack) {
        if (stack == null || stack.amount <= 0) return 0;
        int totalFilled = 0;
        int remaining = stack.amount;
        for (MTEHatchOutput hatch : mOutputHatches) {
            if (hatch == null || !hatch.isValid()) continue;
            if (!hatch.canStoreFluid(stack)) continue;
            FluidStack toFill = new FluidStack(stack.getFluid(), remaining);
            int filled = hatch.fill(toFill, true);
            if (filled > 0) {
                totalFilled += filled;
                remaining -= filled;
                if (remaining <= 0) break;
            }
        }
        return totalFilled;
    }

    public static Fluid resolveGasFluid(String gasName) {
        if (gasName == null) return null;
        String clean = gasName.trim()
            .toLowerCase();
        Fluid f = FluidRegistry.getFluid(clean);
        if (f != null) return f;

        if ("deuterium".equals(clean)) {
            FluidStack fs = Materials.Deuterium.getGas(1);
            if (fs != null) return fs.getFluid();
        } else if ("tritium".equals(clean)) {
            FluidStack fs = Materials.Tritium.getGas(1);
            if (fs != null) return fs.getFluid();
        } else if ("oxygen".equals(clean)) {
            FluidStack fs = Materials.Oxygen.getGas(1);
            if (fs != null) return fs.getFluid();
        } else if ("hydrogen".equals(clean)) {
            FluidStack fs = Materials.Hydrogen.getGas(1);
            if (fs != null) return fs.getFluid();
        } else if ("methane".equals(clean)) {
            FluidStack fs = Materials.Methane.getGas(1);
            if (fs != null) return fs.getFluid();
        }

        return FluidRegistry.getFluid("gas_" + clean);
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setLong("mLastDrainedEU", mLastDrainedEU);
        aNBT.setLong("mTotalExtractedLiters", mTotalExtractedLiters);
        aNBT.setDouble("mCurrentCoolantTemp", mCurrentCoolantTemp);
        NBTTagCompound accumTag = new NBTTagCompound();
        for (Map.Entry<String, Double> e : mExtractionAccumulators.entrySet()) {
            accumTag.setDouble(e.getKey(), e.getValue());
        }
        aNBT.setTag("mExtractionAccumulators", accumTag);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        mLastDrainedEU = aNBT.getLong("mLastDrainedEU");
        mTotalExtractedLiters = aNBT.getLong("mTotalExtractedLiters");
        if (aNBT.hasKey("mCurrentCoolantTemp")) {
            mCurrentCoolantTemp = aNBT.getDouble("mCurrentCoolantTemp");
        }
        mExtractionAccumulators.clear();
        if (aNBT.hasKey("mExtractionAccumulators")) {
            NBTTagCompound accumTag = aNBT.getCompoundTag("mExtractionAccumulators");
            for (Object k : accumTag.func_150296_c()) {
                String key = (String) k;
                mExtractionAccumulators.put(key, accumTag.getDouble(key));
            }
        }
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(StatCollector.translateToLocal("gt.multiblock.pressurized_degasser.machine_type"))
            .addInfo(StatCollector.translateToLocal("gt.multiblock.pressurized_degasser.desc1"))
            .addInfo(StatCollector.translateToLocal("gt.multiblock.pressurized_degasser.desc2"))
            .addInfo(StatCollector.translateToLocal("gt.multiblock.pressurized_degasser.desc3"))
            .addSeparator()
            .beginStructureBlock(3, 7, 3, false)
            .addController(StatCollector.translateToLocal("gt.multiblock.pressurized_degasser.structure.controller"))
            .addCasingInfoMin(
                StatCollector.translateToLocal("gt.multiblock.pressurized_degasser.structure.casings"),
                48,
                false)
            .addOtherStructurePart(
                StatCollector.translateToLocal("gt.multiblock.pressurized_degasser.structure.hatch_inlet"),
                StatCollector.translateToLocal("gt.multiblock.pressurized_degasser.structure.hatch_inlet_pos"))
            .addOtherStructurePart(
                StatCollector.translateToLocal("gt.multiblock.pressurized_degasser.structure.hatch_outlet"),
                StatCollector.translateToLocal("gt.multiblock.pressurized_degasser.structure.hatch_outlet_pos"))
            .addEnergyHatch(StatCollector.translateToLocal("gt.multiblock.pressurized_degasser.structure.energy"), 1)
            .addMaintenanceHatch(
                StatCollector.translateToLocal("gt.multiblock.pressurized_degasser.structure.maintenance"),
                1)
            .addOutputHatch(
                StatCollector.translateToLocal("gt.multiblock.pressurized_degasser.structure.output_hatch"),
                2)
            .toolTipFinisher(StatCollector.translateToLocal("gt.multiblock.coolantloops.finisher"));
        return tt;
    }
}
