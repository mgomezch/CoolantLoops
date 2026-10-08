package com.gtnewhorizons.coolantloops.common.metatileentity.pipe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.util.EnumChatFormatting;

import com.gtnewhorizons.coolantloops.engine.CoolantPipingRegistry;

import gregtech.api.enums.Materials;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEFluidPipe;

/**
 * Thermally Insulated Coolant Pipe for closed coolant loops.
 * Available in all standard sizes and materials (including Osmium).
 * Crafts from original GregTech pipes and tier-progressed heat insulation materials.
 */
public class MTECoolantPipe extends MTEFluidPipe {

    public MTECoolantPipe(int aID, String aName, String aPrefixKey, float aThickNess, Materials aMaterial,
        int aCapacity, int aHeatResistance, boolean aGasProof) {
        super(aID, aName, aPrefixKey, aThickNess, aMaterial, aCapacity, aHeatResistance, aGasProof, 1);
    }

    public MTECoolantPipe(String aName, float aThickNess, Materials aMaterial, int aCapacity, int aHeatResistance,
        boolean aGasProof) {
        super(aName, aThickNess, aMaterial, aCapacity, aHeatResistance, aGasProof, 1);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTECoolantPipe(mName, mThickNess, mMaterial, mCapacity, mHeatResistance, mGasProof);
    }

    public String getSizeName() {
        if (mThickNess <= 0.251f) {
            return "Tiny";
        } else if (mThickNess <= 0.376f) {
            return "Small";
        } else if (mThickNess <= 0.501f) {
            return "Medium";
        } else if (mThickNess <= 0.751f) {
            return "Large";
        } else {
            return "Huge";
        }
    }

    @Override
    public String getLocalizedName() {
        String matName = mMaterial != null ? mMaterial.mDefaultLocalName : "Unknown";
        return String.format("%s %s Coolant Pipe", getSizeName(), matName);
    }

    @Override
    public String[] getDescription() {
        List<String> list = new ArrayList<>();
        list.add(EnumChatFormatting.AQUA + "Thermally Insulated Coolant Loop Pipe");
        String[] superDesc = super.getDescription();
        if (superDesc != null) {
            list.addAll(Arrays.asList(superDesc));
        }
        double maxP = CoolantPipingRegistry.getMaxPressureBar(mMaterial);
        double maxT = CoolantPipingRegistry.getMaxTemperatureCelsius(this);
        double diamCm = CoolantPipingRegistry.getDiameterForThickness(mThickNess) * 100.0;
        list.add(EnumChatFormatting.GRAY + String.format("Inner Diameter: %.1f cm", diamCm));
        list.add(EnumChatFormatting.GRAY + String.format("Max Safe Pressure: %.1f bar", maxP));
        list.add(EnumChatFormatting.GRAY + String.format("Max Safe Temperature: %.1f \u00B0C", maxT));
        return list.toArray(new String[0]);
    }
}
