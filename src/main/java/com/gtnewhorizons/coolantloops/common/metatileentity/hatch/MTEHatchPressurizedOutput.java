package com.gtnewhorizons.coolantloops.common.metatileentity.hatch;

import com.gtnewhorizons.coolantloops.common.util.CoolantLocalization;

import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;

/**
 * Pressurized output hatch.
 * Hermetic port discharging coolant from a multiblock machine into a closed coolant loop.
 */
public class MTEHatchPressurizedOutput extends MTEHatchPressurizedFluid {

    public MTEHatchPressurizedOutput(int aID, String aName, String aNameRegional, int aTier) {
        super(
            aID,
            aName,
            aNameRegional,
            aTier,
            new String[] { CoolantLocalization.get("gt.hatch.pressurized_output.desc1"),
                CoolantLocalization.get("gt.hatch.pressurized_output.desc2") });
        super.setMode(HatchMode.DISCHARGE);
    }

    public MTEHatchPressurizedOutput(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, aTier, aDescription, aTextures);
        super.setMode(HatchMode.DISCHARGE);
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEHatchPressurizedOutput(mName, mTier, mDescriptionArray, mTextures);
    }

    @Override
    public void setMode(HatchMode mode) {
        super.setMode(HatchMode.DISCHARGE);
    }
}
