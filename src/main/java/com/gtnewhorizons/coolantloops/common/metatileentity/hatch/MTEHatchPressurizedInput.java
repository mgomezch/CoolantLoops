package com.gtnewhorizons.coolantloops.common.metatileentity.hatch;

import com.gtnewhorizons.coolantloops.common.util.CoolantLocalization;

import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;

/**
 * Pressurized input hatch.
 * Hermetic port accepting coolant return into a multiblock machine from a closed coolant loop.
 */
public class MTEHatchPressurizedInput extends MTEHatchPressurizedFluid {

    public MTEHatchPressurizedInput(int aID, String aName, String aNameRegional, int aTier) {
        super(
            aID,
            aName,
            aNameRegional,
            aTier,
            new String[] { CoolantLocalization.get("gt.hatch.pressurized_input.desc1"),
                CoolantLocalization.get("gt.hatch.pressurized_input.desc2") });
        super.setMode(HatchMode.SUCTION);
    }

    public MTEHatchPressurizedInput(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, aTier, aDescription, aTextures);
        super.setMode(HatchMode.SUCTION);
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEHatchPressurizedInput(mName, mTier, mDescriptionArray, mTextures);
    }

    @Override
    public void setMode(HatchMode mode) {
        super.setMode(HatchMode.SUCTION);
    }
}
