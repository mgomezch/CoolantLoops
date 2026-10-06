package com.gtnewhorizons.coolantloops.common.metatileentity.multi;

import java.util.ArrayList;

import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureUtility;
import com.gtnewhorizons.coolantloops.common.metatileentity.hatch.MTEHatchPressurizedFluid;
import com.gtnewhorizons.coolantloops.engine.ConvectiveHeatTransferModel;
import com.gtnewhorizons.coolantloops.engine.CoolantFluidProperty;
import com.gtnewhorizons.coolantloops.engine.ICoolantLoopDevice;
import com.gtnewhorizons.coolantloops.engine.LoopSegment;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.MultiblockTooltipBuilder;

/**
 * Pressurized Heat Exchanger Multiblock.
 * 3x3 Base, Height 4.
 *
 * Transfers thermal energy from primary coolant loop to secondary fluid
 * (e.g. Distilled Water -> Steam at standard 1:160 expansion ratio).
 */
public class MTEPressurizedHeatExchanger extends MTEEnhancedMultiBlockBase<MTEPressurizedHeatExchanger>
    implements ICoolantLoopDevice {

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
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEPressurizedHeatExchanger(mName);
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection facing,
        int aColorIndex, boolean aActive, boolean aRedstone) {
        if (side == facing) {
            return new ITexture[] { TextureFactory.of(GregTechAPI.sBlockCasings4, 2),
                TextureFactory.of(
                    aActive ? Textures.BlockIcons.OVERLAY_FRONT_HEAT_EXCHANGER_ACTIVE
                        : Textures.BlockIcons.OVERLAY_FRONT_HEAT_EXCHANGER) };
        }
        return new ITexture[] { TextureFactory.of(GregTechAPI.sBlockCasings4, 2) };
    }

    @Override
    public boolean checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack) {
        mPrimaryInlet = null;
        mPrimaryOutlet = null;
        mInputHatches.clear();
        mOutputHatches.clear();
        mMaintenanceHatches.clear();
        return checkPiece("exchanger_3x3x4", 1, 0, 1);
    }

    @Override
    public boolean onRunningTick(ItemStack aStack) {
        if (!isAllowedToWork() || mPrimaryInlet == null || mPrimaryOutlet == null) {
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
            // Q_dot = h(v) * A * deltaT
            double qWatts = mConvectiveModel.computeHeatTransferRate(2.0, mExchangeArea, mCurrentCoolantTemp, 100.0);
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

                // 1 L Water -> 160 L Steam
                int steamProduced = waterToBoil * 160;
                FluidStack steamStack = FluidRegistry.getFluidStack("steam", steamProduced);
                if (steamStack != null) {
                    addOutput(steamStack);
                }

                // Cool primary coolant
                double tempDrop = Math.min(deltaT * 0.5, 40.0);
                mCurrentCoolantTemp = Math.max(ambientTemp, mCurrentCoolantTemp - tempDrop);
                mPrimaryOutlet.setCurrentTemperatureCelsius(mCurrentCoolantTemp);
            }
        }

        return true;
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

    private static IStructureDefinition<MTEPressurizedHeatExchanger> STRUCTURE_DEFINITION = null;

    @Override
    public IStructureDefinition<MTEPressurizedHeatExchanger> getStructureDefinition() {
        if (STRUCTURE_DEFINITION == null) {
            STRUCTURE_DEFINITION = StructureDefinition.<MTEPressurizedHeatExchanger>builder()
                .addShape(
                    "exchanger_3x3x4",
                    StructureUtility.transpose(
                        new String[][] { { "CCC", "C~C", "CCC" }, { "CCC", "C C", "CCC" }, { "CCC", "C C", "CCC" },
                            { "CCC", "CCC", "CCC" } }))
                .addElement('C', StructureUtility.ofBlock(GregTechAPI.sBlockCasings4, 2))
                .build();
        }
        return STRUCTURE_DEFINITION;
    }

    @Override
    public void construct(ItemStack stackSize, boolean hintsOnly) {
        buildPiece("exchanger_3x3x4", stackSize, hintsOnly, 1, 0, 1);
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType("Pressurized Heat Exchanger")
            .addInfo("Extracts thermal energy from closed coolant loops")
            .addInfo("Converts secondary Distilled Water -> Steam at 1:160 expansion")
            .addSeparator()
            .beginStructureBlock(3, 4, 3, false)
            .addController("Center of bottom front layer")
            .addCasingInfoMin("Reinforced Machine Casings", 24, false)
            .addOtherStructurePart("Pressurized Fluid Hatch (Inlet)", "1x")
            .addOtherStructurePart("Pressurized Fluid Hatch (Outlet)", "1x")
            .addInputHatch("1x Secondary Fluid Input (Distilled Water)", 1)
            .addOutputHatch("1x Secondary Fluid Output (Steam)", 1)
            .addMaintenanceHatch("1x Maintenance Hatch", 1)
            .toolTipFinisher("Coolant Loops");
        return tt;
    }
}
