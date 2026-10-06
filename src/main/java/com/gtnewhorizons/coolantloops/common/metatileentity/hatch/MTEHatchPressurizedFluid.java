package com.gtnewhorizons.coolantloops.common.metatileentity.hatch;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizons.coolantloops.engine.CoolantFluidProperty;
import com.gtnewhorizons.coolantloops.engine.ICoolantLoopDevice;
import com.gtnewhorizons.coolantloops.engine.LoopSegment;

import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.render.TextureFactory;

/**
 * Pressurized Fluid Hatch.
 * Acts as the hermetic port bridging multiblock machines (Pump, Exchangers)
 * to closed coolant loop pipes.
 */
public class MTEHatchPressurizedFluid extends MTEHatch implements ICoolantLoopDevice {

    public enum HatchMode {
        DISCHARGE, // Output from multiblock into coolant loop
        SUCTION // Return from coolant loop into multiblock
    }

    private HatchMode mode = HatchMode.DISCHARGE;
    private double currentTemperatureCelsius = 20.0;
    private double minorLossK = 0.2; // Minor fitting loss

    public MTEHatchPressurizedFluid(int aID, String aName, String aNameRegional, int aTier) {
        super(aID, aName, aNameRegional, aTier, 0, "Hermetic port for pressurized coolant loops");
    }

    public MTEHatchPressurizedFluid(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, aTier, 0, aDescription, aTextures);
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEHatchPressurizedFluid(mName, mTier, mDescriptionArray, mTextures);
    }

    public HatchMode getMode() {
        return mode;
    }

    public void setMode(HatchMode mode) {
        this.mode = mode;
    }

    @Override
    public ITexture[] getTexturesActive(ITexture aBaseTexture) {
        return new ITexture[] { aBaseTexture, TextureFactory.of(Textures.BlockIcons.OVERLAY_PIPE_OUT) };
    }

    @Override
    public ITexture[] getTexturesInactive(ITexture aBaseTexture) {
        return new ITexture[] { aBaseTexture, TextureFactory.of(Textures.BlockIcons.OVERLAY_PIPE_IN) };
    }

    @Override
    public boolean isFacingValid(ForgeDirection facing) {
        return true;
    }

    @Override
    public boolean isAccessAllowed(EntityPlayer aPlayer) {
        return true;
    }

    // --- ICoolantLoopDevice Implementation ---

    @Override
    public String getDeviceId() {
        IGregTechTileEntity te = getBaseMetaTileEntity();
        if (te != null) {
            return String.format("hatch_%d_%d_%d", te.getXCoord(), te.getYCoord(), te.getZCoord());
        }
        return "hatch_unbound";
    }

    @Override
    public double getMinorLossK() {
        return minorLossK;
    }

    @Override
    public double getDeviceTemperatureCelsius() {
        return currentTemperatureCelsius;
    }

    public void setCurrentTemperatureCelsius(double temp) {
        this.currentTemperatureCelsius = temp;
    }

    @Override
    public void processThermalExchange(double coolantFlowRateM3s, double dt, CoolantFluidProperty fluid,
        LoopSegment segment) {
        if (segment != null) {
            this.currentTemperatureCelsius = segment.getCurrentTemperatureCelsius();
        }
    }

    // Reject raw Forge fluid insertion into pressurized hatches
    @Override
    public boolean isFluidInputAllowed(FluidStack aFluid) {
        return false;
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setInteger("mode", mode.ordinal());
        aNBT.setDouble("temp", currentTemperatureCelsius);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        if (aNBT.hasKey("mode")) {
            int m = aNBT.getInteger("mode");
            if (m >= 0 && m < HatchMode.values().length) {
                this.mode = HatchMode.values()[m];
            }
        }
        if (aNBT.hasKey("temp")) {
            this.currentTemperatureCelsius = aNBT.getDouble("temp");
        }
    }
}
