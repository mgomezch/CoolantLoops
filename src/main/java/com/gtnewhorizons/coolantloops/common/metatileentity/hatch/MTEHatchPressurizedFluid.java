package com.gtnewhorizons.coolantloops.common.metatileentity.hatch;

import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizons.coolantloops.engine.CoolantFluidProperty;
import com.gtnewhorizons.coolantloops.engine.ICoolantLoopDevice;
import com.gtnewhorizons.coolantloops.engine.ICoolantLoopPump;
import com.gtnewhorizons.coolantloops.engine.ICoolantPassageHatch;
import com.gtnewhorizons.coolantloops.engine.LoopSegment;
import com.gtnewhorizons.modularui.api.math.Color;
import com.gtnewhorizons.modularui.api.screen.ModularWindow;
import com.gtnewhorizons.modularui.api.screen.UIBuildContext;
import com.gtnewhorizons.modularui.common.widget.DrawableWidget;
import com.gtnewhorizons.modularui.common.widget.TextWidget;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.Textures;
import gregtech.api.gui.modularui.GTUITextures;
import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.render.TextureFactory;

/**
 * Pressurized fluid hatch.
 * Bridges multiblock machines (coolant pump, heat exchangers, baths, reactor columns)
 * to closed coolant loops.
 */
public class MTEHatchPressurizedFluid extends MTEHatch implements ICoolantLoopDevice, ICoolantPassageHatch {

    public static class CustomHatchIcon implements IIconContainer, Runnable {

        protected IIcon mIcon;
        protected final String mModID;
        protected final String mIconName;

        public CustomHatchIcon(final String aModID, final String aIconName) {
            this.mModID = aModID;
            this.mIconName = aIconName;
            GregTechAPI.sGTBlockIconload.add(this);
        }

        @Override
        public IIcon getIcon() {
            return this.mIcon;
        }

        @Override
        public IIcon getOverlayIcon() {
            return null;
        }

        @Override
        public ResourceLocation getTextureFile() {
            return TextureMap.locationBlocksTexture;
        }

        @Override
        public void run() {
            this.mIcon = GregTechAPI.sBlockIcons.registerIcon(this.mModID + ":" + this.mIconName);
        }
    }

    public static final IIconContainer OVERLAY_PRESSURIZED_IN = new CustomHatchIcon(
        "coolantloops",
        "overlay_pressurized_in");
    public static final IIconContainer OVERLAY_PRESSURIZED_OUT = new CustomHatchIcon(
        "coolantloops",
        "overlay_pressurized_out");

    public enum HatchMode {
        DISCHARGE, // Output from multiblock into coolant loop
        SUCTION // Return from coolant loop into multiblock
    }

    public interface IThermalExchangeListener {

        void processThermalExchange(double volumetricFlowRateM3s, double dtSeconds, CoolantFluidProperty fluid,
            LoopSegment segment);
    }

    private HatchMode mode = HatchMode.DISCHARGE;
    private double currentTemperatureCelsius = 20.0;
    private double minorLossK = 0.2; // Minor fitting loss
    private boolean mWorks = true;

    protected IGregTechTileEntity mOppositeHatch = null;
    protected ICoolantLoopPump mConnectedPump = null;
    protected IThermalExchangeListener mThermalExchangeListener = null;

    public MTEHatchPressurizedFluid(int aID, String aName, String aNameRegional, int aTier) {
        this(
            aID,
            aName,
            aNameRegional,
            aTier,
            new String[] {
                com.gtnewhorizons.coolantloops.common.util.CoolantLocalization.get("gt.hatch.pressurized_fluid.desc1"),
                com.gtnewhorizons.coolantloops.common.util.CoolantLocalization
                    .get("gt.hatch.pressurized_fluid.desc2") });
    }

    public MTEHatchPressurizedFluid(int aID, String aName, String aNameRegional, int aTier, String[] aDescription) {
        super(aID, aName, aNameRegional, aTier, 0, aDescription);
    }

    public MTEHatchPressurizedFluid(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, aTier, 0, aDescription, aTextures);
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEHatchPressurizedFluid(mName, mTier, mDescriptionArray, mTextures);
    }

    public boolean isInput() {
        return mode == HatchMode.SUCTION;
    }

    public boolean isOutput() {
        return mode == HatchMode.DISCHARGE;
    }

    public HatchMode getMode() {
        return mode;
    }

    public void setMode(HatchMode mode) {
        this.mode = mode;
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base != null) {
            if (base.isServerSide()) {
                base.issueTileUpdate();
            } else {
                base.issueTextureUpdate();
            }
        }
    }

    public void setThermalExchangeListener(IThermalExchangeListener listener) {
        this.mThermalExchangeListener = listener;
    }

    public IThermalExchangeListener getThermalExchangeListener() {
        return this.mThermalExchangeListener;
    }

    @Override
    public ITexture[] getTexturesActive(ITexture aBaseTexture) {
        if (isInput()) {
            return new ITexture[] { aBaseTexture, TextureFactory.of(Textures.BlockIcons.OVERLAY_PIPE_IN),
                TextureFactory.of(OVERLAY_PRESSURIZED_IN) };
        } else {
            return new ITexture[] { aBaseTexture, TextureFactory.of(Textures.BlockIcons.OVERLAY_PIPE_OUT),
                TextureFactory.of(OVERLAY_PRESSURIZED_OUT) };
        }
    }

    @Override
    public ITexture[] getTexturesInactive(ITexture aBaseTexture) {
        return getTexturesActive(aBaseTexture);
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection aFacing,
        int colorIndex, boolean aActive, boolean redstoneLevel) {
        ITexture baseTexture = getCasingTexture();
        if (baseTexture == null) {
            baseTexture = Textures.BlockIcons.MACHINE_CASINGS[mTier][colorIndex + 1];
        }
        if (side != aFacing) {
            return new ITexture[] { baseTexture };
        }
        if (isInput()) {
            return new ITexture[] { baseTexture, TextureFactory.of(Textures.BlockIcons.OVERLAY_PIPE_IN),
                TextureFactory.of(OVERLAY_PRESSURIZED_IN) };
        } else {
            return new ITexture[] { baseTexture, TextureFactory.of(Textures.BlockIcons.OVERLAY_PIPE_OUT),
                TextureFactory.of(OVERLAY_PRESSURIZED_OUT) };
        }
    }

    @Override
    public boolean isFacingValid(ForgeDirection facing) {
        return true;
    }

    @Override
    public boolean isAccessAllowed(EntityPlayer aPlayer) {
        return true;
    }

    // --- ICoolantPassageHatch Implementation ---

    @Override
    public IGregTechTileEntity getOppositeHatchTile() {
        return mOppositeHatch;
    }

    public void setOppositeHatch(IGregTechTileEntity opposite) {
        this.mOppositeHatch = opposite;
    }

    @Override
    public boolean isPassageInlet() {
        return isInput();
    }

    @Override
    public void setConnectedPump(ICoolantLoopPump pump) {
        this.mConnectedPump = pump;
        if (mOppositeHatch != null && mOppositeHatch.getMetaTileEntity() instanceof ICoolantPassageHatch other) {
            if (other.getConnectedPump() != pump) {
                other.setConnectedPump(pump);
            }
        }
    }

    @Override
    public ICoolantLoopPump getConnectedPump() {
        return mConnectedPump;
    }

    // --- ICoolantLoopDevice Implementation ---

    private String cachedDeviceId;

    @Override
    public String getDeviceId() {
        if (cachedDeviceId != null) return cachedDeviceId;
        IGregTechTileEntity te = getBaseMetaTileEntity();
        if (te != null) {
            cachedDeviceId = "hatch_" + te.getXCoord() + "_" + te.getYCoord() + "_" + te.getZCoord();
            return cachedDeviceId;
        }
        return "hatch_unbound";
    }

    @Override
    public double getMinorLossK() {
        return mOppositeHatch != null ? 2.5 : minorLossK;
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
        if (mThermalExchangeListener != null) {
            mThermalExchangeListener.processThermalExchange(coolantFlowRateM3s, dt, fluid, segment);
            return;
        }
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
    public net.minecraftforge.fluids.FluidTankInfo[] getTankInfo(ForgeDirection aSide) {
        return new net.minecraftforge.fluids.FluidTankInfo[] {
            new net.minecraftforge.fluids.FluidTankInfo(null, 1000) };
    }

    @Override
    public boolean allowCoverOnSide(ForgeDirection side, ItemStack coverItem) {
        return true;
    }

    public boolean isAllowedToWork() {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base != null) {
            return base.isAllowedToWork();
        }
        return mWorks;
    }

    public void enableWorking() {
        this.mWorks = true;
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base != null) {
            base.enableWorking();
        }
    }

    public void disableWorking() {
        this.mWorks = false;
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base != null) {
            base.disableWorking();
        }
    }

    @Override
    public void onEnableWorking() {
        this.mWorks = true;
    }

    @Override
    public void onDisableWorking() {
        this.mWorks = false;
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setInteger("mode", mode.ordinal());
        aNBT.setDouble("temp", currentTemperatureCelsius);
        aNBT.setBoolean("mWorks", mWorks);
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
        if (aNBT.hasKey("mWorks")) {
            this.mWorks = aNBT.getBoolean("mWorks");
        }
    }

    // --- ModularUI GUI ---

    @Override
    public boolean onRightclick(IGregTechTileEntity aBaseMetaTileEntity, EntityPlayer aPlayer) {
        openGui(aPlayer);
        return true;
    }

    @Override
    public void addUIWidgets(ModularWindow.Builder builder, UIBuildContext buildContext) {
        builder.widget(
            new DrawableWidget().setDrawable(GTUITextures.PICTURE_SCREEN_BLACK)
                .setPos(7, 16)
                .setSize(160, 60))
            .widget(
                new TextWidget()
                    .setStringSupplier(() -> isInput() ? "Pressurized input hatch" : "Pressurized output hatch")
                    .setDefaultColor(Color.rgb(168, 85, 247))
                    .setPos(10, 20))
            .widget(
                new TextWidget()
                    .setStringSupplier(() -> String.format("Temperature: %.1f °C", currentTemperatureCelsius))
                    .setDefaultColor(Color.rgb(255, 200, 0))
                    .setPos(10, 32))
            .widget(
                new TextWidget()
                    .setStringSupplier(
                        () -> String.format("Paired: %s", mOppositeHatch != null ? "Linked passage" : "None"))
                    .setDefaultColor(Color.rgb(150, 255, 150))
                    .setPos(10, 44))
            .widget(
                new TextWidget()
                    .setStringSupplier(
                        () -> String.format("Pump: %s", mConnectedPump != null ? "Connected" : "Disconnected"))
                    .setDefaultColor(Color.rgb(150, 180, 255))
                    .setPos(10, 56));
    }
}
